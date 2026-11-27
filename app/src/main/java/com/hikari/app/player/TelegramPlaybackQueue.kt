package com.hikari.app.player

/**
 * The Telegram page's video queue.
 *
 * A channel/chat feed is a list of videos, but the player only receives the
 * one the user tapped. This process-wide store carries the rest of that loaded
 * list so `PlayerActivity` can:
 *
 *  - start the next video muted in the corner before the current one ends, and
 *  - hand off to it in place, without resolving a provider, an episode or a
 *    shelf item.
 *
 * It mirrors [PlaybackQueueStore] (a process-lifetime hand-off, not persisted)
 * but carries ready-made source JSON, because a Telegram video has no
 * provider/catalog item to resolve later. TDLib file references can expire;
 * every source URI carries its chat/message ids so `TdFileDataSource` can
 * refresh them when it opens.
 */
object TelegramPlaybackQueue {

    data class Item(
        val title: String,
        /** The same JSON shape as the player's `sources` intent extra. */
        val sourcesJson: String,
        /** TDLib file id for prewarming the corner preview; 0 for public CDN videos. */
        val fileId: Int = 0,
    )

    @Volatile
    private var items: List<Item> = emptyList()

    @Volatile
    private var index: Int = -1

    @Synchronized
    fun set(items: List<Item>, at: Int) {
        this.items = items
        this.index = if (items.isEmpty()) -1 else at.coerceIn(0, items.lastIndex)
    }

    fun current(): Item? = items.getOrNull(index)

    fun next(): Item? = items.getOrNull(index + 1)

    fun hasNext(): Boolean = index >= 0 && index + 1 in items.indices

    /** Move to the next item and return it; null at the end of the queue. */
    @Synchronized
    fun advance(): Item? {
        if (!hasNext()) return null
        index += 1
        return current()
    }

    @Synchronized
    fun clear() {
        items = emptyList()
        index = -1
    }
}
