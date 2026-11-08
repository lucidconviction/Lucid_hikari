package com.hikari.app.youtube

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hikari.app.player.PlayerActivity
import org.json.JSONArray
import org.json.JSONObject

/** What the YouTube page hands back when you pick a video. */
data class YouTubePlayRequest(
    val url: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val startPositionMs: Long = 0L,
    /** Stream headers (the User-Agent the URL was resolved with). */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * The PlayerActivity handoff used by both the YouTube tab and the player's
 * "More Videos" panel: the engine's local DASH manifest plus the User-Agent it
 * needs, the progress id, and the marker that turns on the YouTube extras.
 */
fun youTubePlayerIntent(context: Context, request: YouTubePlayRequest): Intent {
    val lower = request.url.lowercase()
    val isMpd = lower.contains(".mpd") || lower.startsWith("http://127.0.0.1") ||
        lower.startsWith("http://localhost")
    val isM3u8 = lower.contains(".m3u8")
    val headers = JSONObject().apply {
        request.headers.forEach { (k, v) -> put(k, v) }
    }
    val sources = JSONArray().put(
        JSONObject().apply {
            put("name", "YouTube")
            put("url", request.url)
            put("headers", headers)
            put("isMpd", isMpd)
            put("isM3u8", isM3u8)
            put("provider", "YouTube")
        },
    )
    return Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", request.title)
        putExtra("sources", sources.toString())
        putExtra("startPosition", request.startPositionMs)
        putExtra("askServer", false)
        putExtra("histProviderId", "youtube")
        putExtra("histMediaId", YouTubeProgress.ID_PREFIX + request.videoId)
        putExtra("histType", "movie")
        putExtra("histTitle", request.title)
        putExtra("histPoster", request.poster.orEmpty())
        putExtra("ytPlayback", true)
    }
}

/** Whether the YouTube tab is currently on screen — video previews only play while it is. */
val LocalScreenActive = staticCompositionLocalOf { true }

/** Room under the page for the app's floating bottom navigation. */
val LocalYouTubeBottomPadding = staticCompositionLocalOf { 0.dp }

