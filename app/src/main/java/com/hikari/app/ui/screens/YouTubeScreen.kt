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
import com.hikari.app.youtube.youTubePlayerIntent

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
        // An in-player "More Videos" switch changes the current video without
        // leaving the player; the result names the video that actually played.
        val playedVideoId = data.getStringExtra("ytVideoId")
            ?.takeIf { it.isNotBlank() } ?: req.videoId
        if (position >= 0L && duration > 0L) {
            YouTubeProgress.record(
                context,
                YouTubeProgress.ID_PREFIX + playedVideoId,
                position,
                duration,
            )
        }
        // Same behaviour as the old app: when autoplay is on and the video ran
        // to the end, ask the feed what comes next and start it.
        if (data.getBooleanExtra("ytEnded", false) && YouTubeSettings.autoplayNext(context)) {
            YouTubeSession.feed?.playNext(
                playedVideoId,
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
        player.launch(youTubePlayerIntent(context, req))
        followUp = null
    }

    CompositionLocalProvider(
        LocalYouTubeBottomPadding provides bottomInset,
        LocalScreenActive provides true,
    ) {
        EngineYouTubeScreen(
            onPlay = { request ->
                lastRequest = request
                player.launch(youTubePlayerIntent(context, request))
            },
        )
    }
}
