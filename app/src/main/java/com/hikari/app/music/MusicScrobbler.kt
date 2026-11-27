package com.hikari.app.music

import android.content.Context
import android.content.SharedPreferences
import com.hikari.app.BuildConfig
import com.hikari.app.data.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class LastFmConfig(
    val username: String = "",
    val sessionKey: String = "",
    val enabled: Boolean = false,
) {
    val configured: Boolean get() = sessionKey.isNotBlank()
    val available: Boolean
        get() = BuildConfig.LASTFM_API_KEY.isNotBlank() &&
            BuildConfig.LASTFM_SECRET.isNotBlank()
}

/**
 * Last.fm scrobbling for the Music section. Credentials are the app's own API
 * key/secret from gradle.properties; the user only signs in with their Last.fm
 * username and password, and only the session key is stored.
 */
object MusicScrobbler {
    private const val TAG = "MusicScrobbler"
    private const val PREFS = "lucid_music_lastfm"
    private const val KEY_USERNAME = "username"
    private const val KEY_SESSION = "session_key"
    private const val KEY_ENABLED = "enabled"
    private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

    private val _config = MutableStateFlow(LastFmConfig())
    val config: StateFlow<LastFmConfig> = _config.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var prefs: SharedPreferences? = null
    private var started = false
    private var announcedId: String? = null
    private var scrobbledId: String? = null
    private var lastTrackId: String? = null
    private var lastPositionMs = 0L

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _config.value = LastFmConfig(
            username = store.getString(KEY_USERNAME, "").orEmpty(),
            sessionKey = store.getString(KEY_SESSION, "").orEmpty(),
            enabled = store.getBoolean(KEY_ENABLED, false),
        )
        if (!started) {
            started = true
            observePlayback()
        }
    }

    /** Signs in with Last.fm; returns the account name on success. */
    suspend fun login(
        context: Context,
        username: String,
        password: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.LASTFM_API_KEY.trim()
        val secret = BuildConfig.LASTFM_SECRET.trim()
        if (apiKey.isBlank() || secret.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Last.fm isn't configured. Add LASTFM_API_KEY and LASTFM_SECRET to gradle.properties.",
                ),
            )
        }
        val name = username.trim()
        if (name.isBlank() || password.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter your Last.fm username and password."),
            )
        }
        runCatching {
            val body = execute(
                params = linkedMapOf(
                    "method" to "auth.getMobileSession",
                    "api_key" to apiKey,
                    "username" to name,
                    "password" to password,
                ),
                secret = secret,
            )
            val session = JSONObject(body).getJSONObject("session")
            val account = session.optString("name").takeIf { it.isNotBlank() } ?: name
            val key = session.getString("key")
            saveSession(context, account, key, enabled = true)
            account
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        init(context)
        if (enabled && !_config.value.configured) return
        _config.value = _config.value.copy(enabled = enabled)
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        announcedId = null
        scrobbledId = null
    }

    fun logout(context: Context) {
        init(context)
        _config.value = LastFmConfig()
        prefs?.edit()?.clear()?.apply()
        announcedId = null
        scrobbledId = null
    }

    private fun saveSession(
        context: Context,
        username: String,
        sessionKey: String,
        enabled: Boolean,
    ) {
        init(context)
        _config.value = LastFmConfig(username, sessionKey, enabled)
        prefs?.edit()
            ?.putString(KEY_USERNAME, username)
            ?.putString(KEY_SESSION, sessionKey)
            ?.putBoolean(KEY_ENABLED, enabled)
            ?.apply()
        announcedId = null
        scrobbledId = null
    }

    private fun observePlayback() {
        scope.launch {
            MusicPlayer.state.collect { state ->
                val config = _config.value
                if (!config.enabled || !config.configured) return@collect
                val track = state.track ?: return@collect
                if (track.id != lastTrackId) {
                    lastTrackId = track.id
                    lastPositionMs = 0L
                    announcedId = null
                    scrobbledId = null
                }
                if (state.positionMs + 5_000L < lastPositionMs) {
                    announcedId = null
                    scrobbledId = null
                }
                lastPositionMs = state.positionMs
                if (state.playing && announcedId != track.id) {
                    announcedId = track.id
                    scrobbledId = null
                    updateNowPlaying(config, track)
                }
                if (scrobbledId == track.id || !state.playing) return@collect
                val duration = state.durationMs
                if (duration < 30_000L) return@collect
                val threshold = min(duration / 2L, 4 * 60_000L)
                if (state.positionMs >= threshold) {
                    scrobbledId = track.id
                    scrobble(config, track, duration)
                }
            }
        }
    }

    private fun updateNowPlaying(config: LastFmConfig, track: MusicTrack) {
        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "api_key" to BuildConfig.LASTFM_API_KEY,
            "sk" to config.sessionKey,
            "artist" to track.artist,
            "track" to track.title,
        )
        track.album.takeIf { it.isNotBlank() && it != "Single" }?.let { params["album"] = it }
        track.trackNumber?.let { params["trackNumber"] = it.toString() }
        post(params)
    }

    private fun scrobble(config: LastFmConfig, track: MusicTrack, durationMs: Long) {
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "api_key" to BuildConfig.LASTFM_API_KEY,
            "sk" to config.sessionKey,
            "artist" to track.artist,
            "track" to track.title,
            "timestamp" to (System.currentTimeMillis() / 1000L).toString(),
            "duration" to (durationMs / 1000L).coerceAtLeast(1L).toString(),
        )
        track.album.takeIf { it.isNotBlank() && it != "Single" }?.let { params["album"] = it }
        track.trackNumber?.let { params["trackNumber"] = it.toString() }
        post(params)
    }

    private fun post(params: Map<String, String>) {
        val secret = BuildConfig.LASTFM_SECRET
        if (secret.isBlank()) return
        runCatching { execute(params, secret) }
            .onFailure { Logs.log(TAG, "${params["method"]} failed: ${it.message}") }
    }

    private fun execute(params: Map<String, String>, secret: String): String {
        val signed = params.toMutableMap()
        signed["api_sig"] = signature(params, secret)
        val form = FormBody.Builder().apply {
            signed.forEach { (key, value) -> add(key, value) }
            add("format", "json")
        }.build()
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(form)
            .header("User-Agent", "LucidHikari/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.contains("\"error\"")) {
                val error = runCatching { JSONObject(body) }.getOrNull()
                val message = error?.optString("message").orEmpty()
                    .ifBlank { "Last.fm rejected the request." }
                throw IOException(message)
            }
            if (!response.isSuccessful) {
                throw IOException("Last.fm returned HTTP ${response.code}.")
            }
            return body
        }
    }

    internal fun signature(params: Map<String, String>, secret: String): String {
        val raw = params.toSortedMap().entries.joinToString("") { (key, value) -> key + value } +
            secret
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
