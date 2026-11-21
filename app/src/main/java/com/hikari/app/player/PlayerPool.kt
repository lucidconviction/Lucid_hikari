package com.hikari.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * F4 — pool of ExoPlayer instances for the multi-view screen.
 *
 * Keyed by SLOT, not by list position: swapping two tiles or removing one used
 * to shift the list and hand a cell the player that was already playing a
 * different stream, which showed up as the same video looping. A removed slot
 * now leaves the other players exactly where they were.
 */
class PlayerPool(private val context: Context) {
    companion object {
        const val MAX_PLAYERS = 9
    }

    private val players = HashMap<String, ExoPlayer>()
    private val trackSelector: DefaultTrackSelector by lazy {
        DefaultTrackSelector(context).apply {
            val params = buildUponParameters()
                .setPreferredAudioLanguage("en")
            // Four tiles on a 2 GB television cannot all pull 1080p at once.
            if (com.hikari.app.tv.TvMode.isTv) {
                params.setMaxVideoSize(1280, 720)
                    .setMaxVideoBitrate(4_000_000)
            }
            setParameters(params)
        }
    }
    private val renderersFactory: RenderersFactory by lazy {
        NextRenderersFactory(context)
            .setEnableDecoderFallback(true)
    }

    /** Returns the player for [key] (a stable stream id), creating it if needed. */
    fun get(key: String): ExoPlayer = players.getOrPut(key) {
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            // Each tile owns its own volume; requesting audio focus here would
            // make the platform pause a different window every time one starts.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .build()
    }

    /** Releases only [key]'s player; every other stream keeps playing. */
    fun release(key: String) {
        players.remove(key)?.release()
    }

    /** Number of players currently in the pool. */
    val size: Int get() = players.size

    /** Releases all players. */
    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
    }
}
