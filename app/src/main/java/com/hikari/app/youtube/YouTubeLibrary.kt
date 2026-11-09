package com.hikari.app.youtube

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * This device's own copies of the YouTube lists the signed-out engine cannot
 * read: Watch later and Liked videos.
 *
 * YouTube exposes both lists only to an authorised account, and the MediaService
 * engine bundled with the app has no endpoint for either, so the page keeps them
 * itself: the ⋮ menu on any video card writes here, and the You tab reads them
 * back. They survive restarts, are capped (a feed is not a library), and are
 * deliberately local — nothing is sent anywhere.
 */
object YouTubeLibrary {

    enum class Shelf { WATCH_LATER, LIKED }

    data class Entry(
        val videoId: String,
        val title: String,
        val author: String?,
        val thumbnail: String?,
        val channelId: String?,
        val savedAt: Long,
    )

    private const val PREFS = "youtube_library"
    private const val KEY_WATCH_LATER = "watch_later"
    private const val KEY_LIKED = "liked"
    private const val MAX_ENTRIES = 500

    /** In-memory copy of both shelves, so a card's ⋮ menu does not parse JSON on every recomposition. */
    private val cache = HashMap<Shelf, List<Entry>>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, shelf: Shelf): List<Entry> = synchronized(cache) {
        cache[shelf]?.let { return it }
        val raw = prefs(context).getString(
            if (shelf == Shelf.WATCH_LATER) KEY_WATCH_LATER else KEY_LIKED,
            null,
        ) ?: return emptyList<Entry>().also { cache[shelf] = it }
        val arr = runCatching { JSONArray(raw) }.getOrNull()
            ?: return emptyList<Entry>().also { cache[shelf] = it }
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("videoId")
            if (id.isBlank()) continue
            out += Entry(
                videoId = id,
                title = o.optString("title"),
                author = o.optString("author").takeIf { it.isNotBlank() },
                thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
                channelId = o.optString("channelId").takeIf { it.isNotBlank() },
                savedAt = o.optLong("savedAt", 0L),
            )
        }
        cache[shelf] = out
        out
    }

    @Synchronized
    private fun write(context: Context, shelf: Shelf, entries: List<Entry>) {
        cache[shelf] = entries
        val arr = JSONArray()
        entries.take(MAX_ENTRIES).forEach { e ->
            arr.put(
                JSONObject()
                    .put("videoId", e.videoId)
                    .put("title", e.title)
                    .put("author", e.author.orEmpty())
                    .put("thumbnail", e.thumbnail.orEmpty())
                    .put("channelId", e.channelId.orEmpty())
                    .put("savedAt", e.savedAt),
            )
        }
        prefs(context).edit()
            .putString(if (shelf == Shelf.WATCH_LATER) KEY_WATCH_LATER else KEY_LIKED, arr.toString())
            .apply()
    }

    fun entries(context: Context, shelf: Shelf): List<Entry> =
        read(context, shelf).sortedByDescending { it.savedAt }

    fun isSaved(context: Context, shelf: Shelf, videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) return false
        return read(context, shelf).any { it.videoId == videoId }
    }

    /** Adds or removes [item] from [shelf]; returns true when it is now saved. */
    fun toggle(context: Context, shelf: Shelf, item: YtItem): Boolean {
        val videoId = item.videoId?.takeIf { it.isNotBlank() } ?: return false
        val current = read(context, shelf)
        val existing = current.firstOrNull { it.videoId == videoId }
        val nowSaved = if (existing != null) {
            write(context, shelf, current.filterNot { it.videoId == videoId })
            false
        } else {
            write(
                context,
                shelf,
                (current + Entry(
                    videoId = videoId,
                    title = item.title,
                    author = item.author,
                    thumbnail = item.thumbnail,
                    channelId = item.channelId,
                    savedAt = System.currentTimeMillis(),
                )).takeLast(MAX_ENTRIES),
            )
            true
        }
        // The You tab is on screen and has just changed under the menu.
        YouTubeSession.feed?.refreshYou()
        return nowSaved
    }

    fun remove(context: Context, shelf: Shelf, videoId: String) {
        write(context, shelf, read(context, shelf).filterNot { it.videoId == videoId })
        YouTubeSession.feed?.refreshYou()
    }
}
