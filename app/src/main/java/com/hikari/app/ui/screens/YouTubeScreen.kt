package com.hikari.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.hikari.app.net.Http
import com.hikari.app.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request

private val JSON = Json { ignoreUnknownKeys = true }

/** YouTube video ID: exactly 11 base64-like characters. */
private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

/** Patterns pulled from the YouTube watch page. */
private val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
private val QUALITY_LABEL_REGEX = Regex("(\\d{2,4})p")

/**
 * YouTube tab. The user pastes a YouTube URL (watch, youtu.be, /shorts/, etc.)
 * and Hikari resolves it to a direct stream URL via YouTube's innerTube API —
 * the same technique Nuvio uses internally. The resolved URL is then launched
 * in [PlayerActivity] so it plays inside Hikari's player.
 */
@Composable
fun YouTubeScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var resolutionState by remember { mutableStateOf<ResolutionState>(ResolutionState.Idle) }

    fun resolveAndPlay(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return
        resolutionState = ResolutionState.Loading
        val videoId = extractVideoId(trimmed)
        if (videoId == null) {
            resolutionState = ResolutionState.Error("Invalid YouTube URL")
            return
        }

        scope.launch(Dispatchers.IO) {
            val result = try {
                resolveYouTubeUrl(trimmed, videoId)
            } catch (e: Exception) {
                ResolutionState.Error("Failed to resolve: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                when (result) {
                    is ResolutionState.Success -> {
                        resolutionState = result
                        launchPlayer(context, result.url, "YouTube: $videoId")
                    }
                    else -> resolutionState = result
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Icon(
            imageVector = Icons.Filled.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Color(0xFFCC0000),
        )
        Text(
            text = "YouTube",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "Paste a YouTube URL to play in Hikari",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
            textAlign = TextAlign.Center,
        )

        // URL input
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(
                onSend = { resolveAndPlay(urlInput) },
            ),
            shape = RoundedCornerShape(12.dp),
        )

        TextButton(
            onClick = { resolveAndPlay(urlInput) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = resolutionState !is ResolutionState.Loading,
        ) {
            if (resolutionState is ResolutionState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(" Play", modifier = Modifier.padding(start = 4.dp))
            }
        }

        // Status
        when (val state = resolutionState) {
            is ResolutionState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            is ResolutionState.Success -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Opening in player…",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            else -> {}
        }

        Spacer(modifier = Modifier.size(24.dp))

        // Hint
        Text(
            text = "Supports: youtube.com/watch, youtu.be, /shorts/, /embed/, /live/",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── YouTube extraction helpers (adapted from Nuvio) ─────────────────────────

private sealed interface ResolutionState {
    data object Idle : ResolutionState
    data object Loading : ResolutionState
    data class Success(val url: String) : ResolutionState
    data class Error(val message: String) : ResolutionState
}

private suspend fun resolveYouTubeUrl(rawUrl: String, videoId: String): ResolutionState {
    val watchUrl = "https://www.youtube.com/watch?v=$videoId&hl=en"

    // Use a dedicated OkHttp client (no Cloudflare interceptor — YouTube
    // does not need it and the interceptor adds latency).
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val watchResponse = runCatching {
        httpClient.newCall(
            Request.Builder().url(watchUrl)
                .header("User-Agent", Http.UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    if (watchResponse.isFailure) return ResolutionState.Error("Failed to load YouTube page")

    val html = watchResponse.getOrNull() ?: return ResolutionState.Error("Empty watch page")
    val apiKey = API_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)
    val visitorData = VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)

    if (apiKey.isNullOrBlank()) return ResolutionState.Error("Could not extract YouTube API key")

    val payload = buildJsonObject {
        put("videoId", videoId)
        put("contentCheckOk", true)
        put("racyCheckOk", true)
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", "ANDROID")
                put("clientVersion", "20.10.35")
                put("osName", "Android")
                put("osVersion", "14")
                put("platform", "MOBILE")
                put("androidSdkVersion", 34)
                put("hl", "en")
                put("gl", "US")
            }
        }
        putJsonObject("playbackContext") {
            putJsonObject("contentPlaybackContext") {
                put("html5Preference", "HTML5_PREF_WANTS")
            }
        }
    }

    val playerResponse = runCatching {
        httpClient.newCall(
            Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=${URLEncoder.encode(apiKey, "UTF-8")}")
                .header("User-Agent", Http.UA)
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .header("X-Youtube-Client-Name", "3")
                .header("X-Youtube-Client-Version", "20.10.35")
                .apply { visitorData?.let { vd -> header("X-Goog-Visitor-Id", vd) } }
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    if (playerResponse.isFailure) return ResolutionState.Error("Player API request failed")

    val streamingUrl = withContext(Dispatchers.Default) {
        extractStreamUrl(playerResponse.getOrNull() ?: "", httpClient)
    }

    return streamingUrl?.let { ResolutionState.Success(it) }
        ?: ResolutionState.Error("No playable stream found for this video")
}

private fun extractVideoId(input: String): String? {
    val trimmed = input.trim()
    if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed

    val parsed = parseUrl(trimmed) ?: return null
    if (parsed.host.endsWith("youtu.be")) {
        parsed.pathSegments.firstOrNull()?.let { id ->
            if (VIDEO_ID_REGEX.matches(id)) return id
        }
    }
    parsed.query["v"]?.firstOrNull()?.let { id ->
        if (VIDEO_ID_REGEX.matches(id)) return id
    }
    if (parsed.pathSegments.size >= 2) {
        val (first, second) = parsed.pathSegments[0] to parsed.pathSegments[1]
        if ((first == "embed" || first == "shorts" || first == "live") && VIDEO_ID_REGEX.matches(second)) {
            return second
        }
    }
    return null
}

private data class ParsedUrl(
    val host: String,
    val pathSegments: List<String>,
    val query: Map<String, List<String>>,
)

private fun parseUrl(input: String): ParsedUrl? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    val withoutFragment = normalized.substringBefore('#')
    val withoutScheme = withoutFragment.substringAfter("://", withoutFragment)
    val host = withoutScheme.substringBefore('/').substringBefore('?').lowercase()
    if (host.isBlank()) return null
    val pathAndQuery = withoutScheme.removePrefix(host)
    val path = when {
        pathAndQuery.startsWith("/") -> pathAndQuery.substringBefore('?')
        pathAndQuery.startsWith("?") || pathAndQuery.isBlank() -> "/"
        else -> "/${pathAndQuery.substringBefore('?')}"
    }
    val queryString = withoutFragment.substringAfter('?', "")
    val query = mutableMapOf<String, MutableList<String>>()
    queryString.split('&').filter { it.isNotBlank() }.forEach { pair ->
        val key = pair.substringBefore('=').trim()
        if (key.isBlank()) return@forEach
        query.getOrPut(key) { mutableListOf() }.add(pair.substringAfter('=', ""))
    }
    return ParsedUrl(
        host = host,
        pathSegments = path.trim('/').split('/').filter { it.isNotBlank() },
        query = query,
    )
}

/**
 * Extracts a single directly playable URL from the player API response.
 * Prefers HLS master playlist (carries audio as a rendition), then falls back
 * to the best progressive format.
 */
private fun extractStreamUrl(playerJson: String, httpClient: OkHttpClient): String? {
    val root = runCatching { JSON.parseToJsonElement(playerJson) }.getOrNull()?.jsonObject ?: return null
    val streaming = root["streamingData"]?.jsonObject ?: return null

    // Try HLS manifest first.
    streaming["hlsManifestUrl"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?.let { manifestUrl ->
            val manifestBody = runCatching {
                httpClient.newCall(Request.Builder().url(manifestUrl).header("User-Agent", Http.UA).build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching null
                        resp.body?.string()
                    }
            }.getOrNull()
            if (manifestBody?.contains("#EXTM3U") == true) return manifestUrl
        }

    // Fall back to best progressive format.
    val best = streaming["formats"]?.jsonArray
        ?.mapNotNull { it.jsonObject }
        ?.filter { fmt -> fmt["mimeType"]?.jsonPrimitive?.content?.contains("video/") == true }
        ?.maxByOrNull { fmt ->
            val height = fmt["height"]?.jsonPrimitive?.intOrNull
                ?: parseQualityLabel(fmt["qualityLabel"]?.jsonPrimitive?.content)
                ?: 0
            val fps = fmt["fps"]?.jsonPrimitive?.intOrNull ?: 0
            val bitrate = fmt["bitrate"]?.jsonPrimitive?.longOrNull
                ?: fmt["averageBitrate"]?.jsonPrimitive?.longOrNull ?: 0L
            height * 1_000_000_000L + fps * 1_000_000L + bitrate
        }
    return best?.let { it["url"]?.jsonPrimitive?.content?.takeIf { u -> u.isNotBlank() } }
}

private fun parseQualityLabel(label: String?): Int? {
    if (label.isNullOrBlank()) return null
    return QUALITY_LABEL_REGEX.find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

// ── Player launch ────────────────────────────────────────────────────────────

private fun launchPlayer(context: android.content.Context, url: String, title: String) {
    val sources = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "YouTube")
            put("url", url)
            put("isM3u8", url.contains(".m3u8"))
        })
    }
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", title)
        putExtra("sources", sources.toString())
    }
    context.startActivity(intent)
}
