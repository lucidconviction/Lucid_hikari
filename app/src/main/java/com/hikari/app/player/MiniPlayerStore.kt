package com.hikari.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The in-app floating player: when the player's "Mini" pill is tapped, the
 * player hands its current source and position here and closes; the app root
 * draws a movable, resizable video window over whatever screen is showing.
 */
object MiniPlayerStore {

    data class Item(
        val title: String,
        val url: String,
        val headers: Map<String, String>,
        val isM3u8: Boolean,
        val isMpd: Boolean,
        val poster: String?,
        val positionMs: Long,
        /** The player's full source list, so expanding restores the server
         *  chooser and all the extras the original launch carried. */
        val sourcesJson: String,
        val currentIndex: Int,
    )

    private val _item = MutableStateFlow<Item?>(null)
    val item: StateFlow<Item?> = _item.asStateFlow()

    fun show(item: Item) {
        _item.value = item
    }

    fun hide() {
        _item.value = null
    }
}
