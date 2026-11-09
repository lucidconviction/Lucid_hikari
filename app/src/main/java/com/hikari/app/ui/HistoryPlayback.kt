package com.hikari.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.navigation.NavHostController
import com.hikari.app.cs3.YouTubePlayables
import com.hikari.app.data.HistoryEntry
import com.hikari.app.ui.navigation.Routes
import com.hikari.app.youtube.YouTubePlayRequest
import com.hikari.app.youtube.YouTubeProgress
import com.hikari.app.youtube.youTubePlayerIntent

/**
 * Opens one watch-history row.
 *
 * YouTube entries are not provider titles: there is no "youtube" extension for
 * DetailScreen to open, which is why a YouTube video tapped from Continue
 * Watching / History used to land on an empty detail page. Resolve it through
 * the same engine the YouTube tab uses and hand it straight to the player,
 * with the saved position so it resumes where it left off.
 */
suspend fun openHistoryEntry(context: Context, nav: NavHostController, entry: HistoryEntry) {
    val isYouTube = entry.providerId.equals("youtube", ignoreCase = true) ||
        YouTubeProgress.isYouTubeId(entry.mediaId)
    if (isYouTube) {
        val videoId = entry.mediaId
            .removePrefix(YouTubeProgress.ID_PREFIX)
            .takeIf { it.isNotBlank() }
            ?: return
        val sources = runCatching { YouTubePlayables.resolve(videoId) }
            .getOrDefault(emptyList())
            .filter { it.url.isNotBlank() }
        val source = sources.firstOrNull()
        if (source == null) {
            Toast.makeText(
                context,
                "Couldn't play this YouTube video — try it from the YouTube tab.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val intent = youTubePlayerIntent(
            context,
            YouTubePlayRequest(
                url = source.url,
                title = entry.title,
                poster = entry.posterUrl,
                videoId = videoId,
                startPositionMs = entry.positionMs,
                headers = source.headers,
            ),
        )
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "Couldn't open the YouTube player.", Toast.LENGTH_SHORT).show()
        }
        return
    }

    Routes.safeNavigate(
        nav,
        Routes.detail(
            providerId = entry.providerId,
            type = entry.type,
            mediaId = entry.mediaId,
            title = entry.title,
            posterUrl = entry.posterUrl,
            episodeId = entry.episodeId,
            startPositionMs = entry.positionMs,
        ),
    )
}
