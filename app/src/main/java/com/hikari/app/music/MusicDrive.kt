package com.hikari.app.music

import android.content.Context
import android.util.Base64
import com.hikari.app.BuildConfig
import com.hikari.app.data.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Google Drive OAuth (TV / limited-input device flow) and upload of the
 * Music tab's downloaded files into a "Lucid Music" Drive folder.
 *
 * The OAuth client is the app developer's: set GOOGLE_DRIVE_CLIENT_ID and
 * GOOGLE_DRIVE_CLIENT_SECRET in gradle.properties. Only the `drive.file` scope
 * is requested, so the app sees only files it created.
 */
object MusicDrive {
    private const val PREFS = "lucid_music_drive"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EMAIL = "email"
    private const val KEY_FOLDER = "folder_id"
    private const val KEY_UPLOADED = "uploaded"
    private const val SCOPE = "openid email https://www.googleapis.com/auth/drive.file"
    private const val FOLDER = "Lucid Music"

    data class SignIn(val code: String, val url: String)
    data class State(
        val configured: Boolean = BuildConfig.GOOGLE_DRIVE_CLIENT_ID.isNotBlank() &&
            BuildConfig.GOOGLE_DRIVE_CLIENT_SECRET.isNotBlank(),
        val connected: Boolean = false,
        val email: String? = null,
        val busy: Boolean = false,
        val message: String? = null,
        val signIn: SignIn? = null,
        val uploaded: Int = 0,
        val total: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private var appContext: Context? = null
    private var accessToken: String? = null
    private var accessExpiresAt = 0L
    private var signInJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val prefs = prefs(app)
        _state.value = _state.value.copy(
            connected = prefs.getString(KEY_REFRESH, null).orEmpty().isNotBlank(),
            email = prefs.getString(KEY_EMAIL, null),
        )
    }

    fun connect(context: Context) {
        init(context)
        val app = appContext ?: return
        if (_state.value.busy) return
        if (!_state.value.configured) {
            _state.value = _state.value.copy(
                message = "Drive OAuth isn't configured. Add GOOGLE_DRIVE_CLIENT_ID and GOOGLE_DRIVE_CLIENT_SECRET to gradle.properties.",
            )
            return
        }
        signInJob?.cancel()
        signInJob = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null, signIn = null)
            val codes = runCatching {
                postForm(
                    "https://oauth2.googleapis.com/device/code",
                    mapOf(
                        "client_id" to BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                        "scope" to SCOPE,
                    ),
                )
            }.getOrElse {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Couldn't reach Google: ${it.message}",
                )
                return@launch
            }
            val deviceCode = codes.optString("device_code")
            val userCode = codes.optString("user_code")
            if (deviceCode.isBlank() || userCode.isBlank()) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = codes.optString("error_description")
                        .ifBlank { "Google didn't start the sign-in." },
                )
                return@launch
            }
            val verification = codes.optString("verification_url")
                .ifBlank { "https://www.google.com/device" }
            _state.value = _state.value.copy(
                busy = false,
                signIn = SignIn(userCode, verification),
            )
            var interval = codes.optLong("interval", 5L).coerceAtLeast(2L)
            val expiresAt = System.currentTimeMillis() +
                codes.optLong("expires_in", 1800L) * 1000L
            while (System.currentTimeMillis() < expiresAt) {
                delay(interval * 1000L)
                val token = runCatching {
                    postForm(
                        "https://oauth2.googleapis.com/token",
                        mapOf(
                            "client_id" to BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                            "client_secret" to BuildConfig.GOOGLE_DRIVE_CLIENT_SECRET,
                            "device_code" to deviceCode,
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        ),
                    )
                }.getOrNull() ?: continue
                when (token.optString("error")) {
                    "" -> {
                        val refresh = token.optString("refresh_token").takeIf { it.isNotBlank() }
                        if (refresh == null) {
                            _state.value = _state.value.copy(
                                busy = false,
                                signIn = null,
                                message = "Google didn't return a refresh token; try again.",
                            )
                            return@launch
                        }
                        accessToken = token.optString("access_token")
                        accessExpiresAt = System.currentTimeMillis() +
                            token.optLong("expires_in", 3600L) * 1000L
                        val email = emailFrom(token.optString("id_token"))
                        prefs(app).edit()
                            .putString(KEY_REFRESH, refresh)
                            .putString(KEY_EMAIL, email)
                            .apply()
                        _state.value = _state.value.copy(
                            connected = true,
                            email = email,
                            busy = false,
                            signIn = null,
                            message = "Connected to Google Drive.",
                        )
                        return@launch
                    }
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 2L
                    "access_denied" -> {
                        _state.value = _state.value.copy(
                            busy = false,
                            signIn = null,
                            message = "Google sign-in was declined.",
                        )
                        return@launch
                    }
                    "expired_token" -> {
                        _state.value = _state.value.copy(
                            busy = false,
                            signIn = null,
                            message = "The Google code expired. Try again.",
                        )
                        return@launch
                    }
                    else -> {
                        _state.value = _state.value.copy(
                            busy = false,
                            signIn = null,
                            message = token.optString("error_description")
                                .ifBlank { "Google sign-in failed." },
                        )
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(
                busy = false,
                signIn = null,
                message = "The Google code expired. Try again.",
            )
        }
    }

    fun cancel() {
        signInJob?.cancel()
        signInJob = null
        _state.value = _state.value.copy(busy = false, signIn = null)
    }

    fun disconnect() {
        signInJob?.cancel()
        signInJob = null
        val app = appContext ?: return
        prefs(app).edit().clear().apply()
        accessToken = null
        accessExpiresAt = 0L
        _state.value = _state.value.copy(
            connected = false,
            email = null,
            signIn = null,
            uploaded = 0,
            total = 0,
            message = "Disconnected from Google Drive.",
        )
    }

    fun uploadDownloads(context: Context) {
        init(context)
        val app = appContext ?: return
        if (_state.value.busy) return
        if (!_state.value.connected) {
            _state.value = _state.value.copy(message = "Connect Google Drive first.")
            return
        }
        val downloads = MusicLibrary.downloads.value
        if (downloads.isEmpty()) {
            _state.value = _state.value.copy(message = "Nothing downloaded to upload.")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(
                busy = true,
                message = null,
                uploaded = 0,
                total = downloads.size,
            )
            val token = token(app)
            if (token == null) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Google Drive access expired. Reconnect.",
                )
                return@launch
            }
            val folderId = runCatching { ensureFolder(token) }.getOrElse {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Couldn't create the Drive folder: ${it.message}",
                )
                return@launch
            }
            val uploadedSet = prefs(app)
                .getStringSet(KEY_UPLOADED, emptySet())
                .orEmpty()
                .toMutableSet()
            var done = 0
            var failed = 0
            for (download in downloads) {
                val file = MusicLibrary.downloadFile(download) ?: continue
                if (!file.isFile) continue
                val key = download.track.id + ":" + file.length()
                if (key in uploadedSet) {
                    done++
                    _state.value = _state.value.copy(uploaded = done)
                    continue
                }
                val ok = runCatching {
                    uploadFile(token, folderId, file, download)
                    uploadedSet += key
                    prefs(app).edit().putStringSet(KEY_UPLOADED, uploadedSet).apply()
                }.onFailure {
                    Logs.log("MusicDrive", "upload failed: ${it.message}")
                }.isSuccess
                if (ok) done++ else failed++
                _state.value = _state.value.copy(uploaded = done)
            }
            _state.value = _state.value.copy(
                busy = false,
                message = if (failed > 0) {
                    "Uploaded $done of ${downloads.size}; $failed failed."
                } else {
                    "Uploaded $done songs to Google Drive."
                },
            )
        }
    }

    private suspend fun token(app: Context): String? {
        accessToken?.let {
            if (System.currentTimeMillis() < accessExpiresAt - 60_000) return it
        }
        val refresh = prefs(app).getString(KEY_REFRESH, null) ?: return null
        val result = runCatching {
            postForm(
                "https://oauth2.googleapis.com/token",
                mapOf(
                    "client_id" to BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                    "client_secret" to BuildConfig.GOOGLE_DRIVE_CLIENT_SECRET,
                    "refresh_token" to refresh,
                    "grant_type" to "refresh_token",
                ),
            )
        }.getOrNull() ?: return null
        if (result.optString("error") == "invalid_grant") {
            prefs(app).edit().remove(KEY_REFRESH).apply()
            _state.value = _state.value.copy(connected = false, email = null)
            return null
        }
        val access = result.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        accessToken = access
        accessExpiresAt = System.currentTimeMillis() +
            result.optLong("expires_in", 3600L) * 1000L
        return access
    }

    private fun ensureFolder(token: String): String {
        val app = appContext ?: error("not initialized")
        prefs(app).getString(KEY_FOLDER, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val metadata = JSONObject()
            .put("name", FOLDER)
            .put("mimeType", "application/vnd.google-apps.folder")
        val body = metadata.toString().toRequestBody(
            "application/json; charset=UTF-8".toMediaType(),
        )
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        val response = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) error("Drive folder ${r.code}")
            r.body?.string().orEmpty()
        }
        val id = JSONObject(response).optString("id")
        if (id.isBlank()) error("Drive returned no folder id")
        prefs(app).edit().putString(KEY_FOLDER, id).apply()
        return id
    }

    private fun uploadFile(
        token: String,
        folderId: String,
        file: File,
        download: DownloadedMusic,
    ) {
        val mime = when (download.format.lowercase()) {
            "flac" -> "audio/flac"
            "m4a", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            else -> "audio/mpeg"
        }
        val metadata = JSONObject()
            .put("name", download.fileName)
            .put("parents", org.json.JSONArray().put(folderId))
            .toString()
        val boundary = "lucid-" + System.nanoTime()
        val body = MultipartRelated(metadata, file, mime, boundary)
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Drive upload ${response.code}: ${response.body?.string().orEmpty().take(200)}")
            }
        }
    }

    private class MultipartRelated(
        private val metadata: String,
        private val file: File,
        private val mime: String,
        private val boundary: String,
    ) : RequestBody() {
        override fun contentType(): MediaType =
            "multipart/related; boundary=$boundary".toMediaType()

        override fun contentLength(): Long {
            val head = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadata + "\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n")
                .toByteArray()
            val tail = "\r\n--$boundary--\r\n".toByteArray()
            return head.size + file.length() + tail.size
        }

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8("--$boundary\r\n")
            sink.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            sink.writeUtf8(metadata)
            sink.writeUtf8("\r\n--$boundary\r\n")
            sink.writeUtf8("Content-Type: $mime\r\n\r\n")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    sink.write(buffer, 0, read)
                }
            }
            sink.writeUtf8("\r\n--$boundary--\r\n")
        }
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder().url(url).post(body).build()
        return http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            JSONObject(text)
        }
    }

    private fun emailFrom(idToken: String): String? = runCatching {
        val payload = idToken.split('.').getOrNull(1) ?: return null
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING))
        JSONObject(json).optString("email").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val JSON = "application/json; charset=UTF-8"
}
