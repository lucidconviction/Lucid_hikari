package com.hikari.app.player

import com.hikari.app.data.MediaItem

/**
 * The list of titles the user was browsing when they opened the current video.
 *
 * The player only receives the chosen title's stream rows, not the shelf it
 * came from. This store keeps that shelf (Home row, extension catalog, search
 * result list…) for the lifetime of the process so the player can autoplay the
 * next title when the current one ends — the same behaviour YouTube gets from
 * its feed view model.
 */
object PlaybackQueueStore {

    @Volatile
    private var items: List<MediaItem> = emptyList()

    /** Remember the shelf and the title inside it that was just opened. */
    fun set(list: List<MediaItem>, current: MediaItem) {
        items = list.filter { it.rawType != "manga" }
    }

    /** Replace the remembered shelf (a fresh catalog/search result). */
    fun setList(list: List<MediaItem>) {
        items = list.filter { it.rawType != "manga" }
    }

    /** The next video after [mediaId] in the shelf, or null when this title did
     *  not come from that shelf / was the last one. */
    fun nextFor(providerId: String, mediaId: String): MediaItem? {
        val list = items
        if (list.isEmpty() || mediaId.isBlank()) return null
        val index = list.indexOfFirst {
            it.id == mediaId && (providerId.isBlank() || it.providerId == providerId)
        }
        if (index < 0) return null
        return list.drop(index + 1).firstOrNull { it.id.isNotBlank() }
    }

    fun clear() {
        items = emptyList()
    }
}
