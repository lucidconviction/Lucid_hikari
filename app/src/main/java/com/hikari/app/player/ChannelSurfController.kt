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
    private val channels: List<IptvChannel>,
    private val onChannelChanged: (IptvChannel) -> Unit,
) {
    var currentIndex by mutableStateOf(channels.indexOfFirst { it.url == channelId }.coerceAtLeast(0))
        private set

    val total get() = channels.size

    suspend fun next(): IptvChannel? {
        val next = (currentIndex + 1) % total
        return switch(next)
    }

    suspend fun prev(): IptvChannel? {
        val prev = (currentIndex - 1 + total) % total
        return switch(prev)
    }

    suspend fun switch(index: Int): IptvChannel? {
        if (index < 0 || index >= total) return null
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
