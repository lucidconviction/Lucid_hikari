package com.hikari.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.hikari.app.player.PlayerActivity
import com.hikari.app.ui.navigation.LocalTaskbarInset
import com.hikari.app.youtube.LocalScreenActive
import com.hikari.app.youtube.LocalYouTubeBottomPadding
import com.hikari.app.youtube.YouTubePlayRequest
import com.hikari.app.youtube.YouTubeProgress
import com.hikari.app.youtube.YouTubeScreen as EngineYouTubeScreen
import com.hikari.app.youtube.YouTubeSession
import com.hikari.app.youtube.YouTubeSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The YouTube tab, running SmartTube's engine through the ported feed UI and
 * playing each video in Hikari's own player (the same [PlayerActivity] every
 * other stream uses — so orientation, controls and gestures are the app's).
 */
@Composable
fun YouTubeScreen(nav: NavHostController) {
    val context = LocalContext.current
    val bottomInset = LocalTaskbarInset.current
    var lastRequest by remember { mutableStateOf<YouTubePlayRequest?>(null) }
    var followUp by remember { mutableStateOf<YouTubePlayRequest?>(null) }

    val player = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val req = lastRequest ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val position = data.getLongExtra("ytPosition", -1L)
        val duration = data.getLongExtra("ytDuration", -1L)
        if (position >= 0L && duration > 0L) {
            YouTubeProgress.record(
                context,
                YouTubeProgress.ID_PREFIX + req.videoId,
                position,
                duration,
            )
        }
        // Same behaviour as the old app: when autoplay is on and the video ran
        // to the end, ask the feed what comes next and start it.
        if (data.getBooleanExtra("ytEnded", false) && YouTubeSettings.autoplayNext(context)) {
            YouTubeSession.feed?.playNext(
                req.videoId,
                { p ->
                    followUp = YouTubePlayRequest(
                        p.url, p.title, p.poster, p.videoId, 0L, p.headers,
                    )
                },
                {},
            )
        }
    }

    LaunchedEffect(followUp) {
        val req = followUp ?: return@LaunchedEffect
        lastRequest = req
        player.launch(playerIntent(context, req))
        followUp = null
    }

    CompositionLocalProvider(
        LocalYouTubeBottomPadding provides bottomInset,
        LocalScreenActive provides true,
    ) {
        EngineYouTubeScreen(
            onPlay = { request ->
                lastRequest = request
                player.launch(playerIntent(context, request))
            },
        )
    }
}

/** The PlayerActivity handoff: the engine's local DASH manifest plus the User-Agent it needs. */
private fun playerIntent(context: Context, request: YouTubePlayRequest): Intent {
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
        // The player's own "YouTube" origin, so its back button returns here.
        putExtra("histProviderId", "youtube")
        putExtra("histMediaId", YouTubeProgress.ID_PREFIX + request.videoId)
        putExtra("histType", "movie")
        putExtra("histTitle", request.title)
        putExtra("histPoster", request.poster.orEmpty())
        putExtra("ytPlayback", true)
    }
}
