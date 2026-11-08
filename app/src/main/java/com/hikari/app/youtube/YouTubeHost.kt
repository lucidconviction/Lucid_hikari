package com.hikari.app.youtube

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/** Whether the YouTube tab is currently on screen — video previews only play while it is. */
val LocalScreenActive = staticCompositionLocalOf { true }

/** Room under the page for the app's floating bottom navigation. */
val LocalYouTubeBottomPadding = staticCompositionLocalOf { 0.dp }
