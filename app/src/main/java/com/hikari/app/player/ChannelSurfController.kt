package com.hikari.app.player

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hikari.app.data.AppStore
import com.hikari.app.data.IptvChannel

/**
 * F1 — Channel-surf controller for IPTV playback.
 *
 * Tracks the current index inside a playlist and exposes [next] / [prev] /
 * [switch] which the player calls to load a different channel from the same
 * list. The controller is created when a channel belonging to a playlist is
 * launched and released when playback ends.
 */
class ChannelSurfController(
    private val store: AppStore,
    private val channelId: String,
    internal val channels: List<IptvChannel>,
    private val onChannelChanged: (IptvChannel) -> Unit,
) {
    var currentIndex by mutableStateOf(channels.indexOfFirst { it.url == channelId }.coerceAtLeast(0))
        private set

    /** The channel we were on before the last switch, for the remote's 0 key. */
    private var lastIndex: Int = currentIndex

    val total get() = channels.size

    /**
     * Channels the player has proved dead. Stepping skips them, so CH+ / CH-
     * and Auto-Next no longer land on a stream the player would have to fail
     * on. The picker marks them as it validates; the set lives with the
     * controller, i.e. for this playback only.
     */
    internal val deadUrls: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    fun markDead(url: String) {
        if (url.isNotBlank()) deadUrls.add(url)
    }

    fun isDead(url: String): Boolean = url in deadUrls

    /** Next/previous index that is not known dead, or the current one. */
    private fun step(direction: Int): Int {
        if (total <= 0) return currentIndex
        var index = currentIndex
        repeat(total) {
            index = (index + direction + total) % total
            if (channels[index].url !in deadUrls) return index
        }
        return currentIndex
    }

    suspend fun next(): IptvChannel? = switch(step(1))

    suspend fun prev(): IptvChannel? = switch(step(-1))

    /** The remote's 0 key: return to the channel we were on before this one. */
    suspend fun last(): IptvChannel? = switch(lastIndex)

    suspend fun switch(index: Int): IptvChannel? {
        if (index < 0 || index >= total) return null
        if (index == currentIndex) return channels.getOrNull(index)
        lastIndex = currentIndex
        currentIndex = index
        val ch = channels[index]
        onChannelChanged(ch)
        if (store.channelSurfToast()) {
            Toast.makeText(
                com.hikari.app.HikariApp.instance,
                "⏺ Channel ${index + 1}/$total — ${ch.name}",
                Toast.LENGTH_SHORT,
            ).show()
        }
        return ch
    }
}
