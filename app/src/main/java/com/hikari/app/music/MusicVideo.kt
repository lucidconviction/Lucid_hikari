package com.hikari.app.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.hikari.app.youtube.youTubePlayerIntent

/**
 * Opens [track] as a YouTube music video in Hikari's own player.
 *
 * This is the one bridge between the Music page and the YouTube engine:
 * [MusicSourceResolver.videoRequest] finds the matching upload with the same
 * search the audio player uses, and [youTubePlayerIntent] hands it to
 * [com.hikari.app.player.PlayerActivity] — which is what gives Video mode the
 * app's normal quality picker, gestures and picture-in-picture for free.
 *
 * The audio player is paused first so the song and its video never play over
 * each other.
 */
internal suspend fun openMusicVideo(
    context: Context,
    track: MusicTrack,
    startPositionMs: Long = 0L,
): Boolean {
    // Stop the song first: the search can take a few seconds, and audio must
    // never keep playing underneath a video that is about to open.
    MusicPlayer.pause()
    val request = runCatching { MusicSourceResolver.videoRequest(track) }.getOrNull() ?: return false
    val intent = youTubePlayerIntent(context, request.copy(startPositionMs = startPositionMs.coerceAtLeast(0L)))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}
