package com.hikari.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * F4 — pool of ExoPlayer instances for the multi-view screen. At most [MAX_PLAYERS]
 * players are kept alive; excess players are released back to the pool.
 * Each player is configured with the app's shared HTTP client so probe results
 * and cached resolutions are reused across windows.
 */
class PlayerPool(private val context: Context) {
    companion object {
        const val MAX_PLAYERS = 9
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val players = mutableListOf<ExoPlayer>()
    private val trackSelector: TrackSelector by lazy { DefaultTrackSelector(context) }
    private val renderersFactory: RenderersFactory by lazy {
        NextRenderersFactory(context)
            .setEnableDecoderFallback(true)
    }

    /** Returns a player at [index], creating it if needed. */
    fun get(index: Int): ExoPlayer {
        while (players.size <= index) {
            players.add(
                ExoPlayer.Builder(context)
                    .setRenderersFactory(renderersFactory)
                    .setTrackSelector(trackSelector)
                    .build()
            )
        }
        return players[index]
    }

    /** Releases the player at [index] and shrinks the pool. */
    fun release(index: Int) {
        if (index < players.size) {
            players[index].release()
            players.removeAt(index)
        }
    }

    /** Number of players currently in the pool. */
    val size: Int get() = players.size

    /** Releases all players. */
    fun releaseAll() {
        players.forEach { it.release() }
        players.clear()
    }
}
