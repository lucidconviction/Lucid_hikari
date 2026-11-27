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

    /** A user-made YouTube playlist, kept on this device like Watch later. */
    data class Playlist(
        val id: String,
        val title: String,
        val videos: List<Entry>,
    )

    private const val PREFS = "youtube_library"
    private const val KEY_WATCH_LATER = "watch_later"
    private const val KEY_LIKED = "liked"
    private const val KEY_PLAYLISTS = "playlists"
    private const val MAX_ENTRIES = 500

    /** In-memory copy of both shelves, so a card's ⋮ menu does not parse JSON on every recomposition. */
    private val cache = HashMap<Shelf, List<Entry>>()
    private var playlistCache: List<Playlist>? = null

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

    // ---- user playlists -----------------------------------------------------

    /** Every playlist the user made on this device, newest first. */
    fun playlists(context: Context): List<Playlist> = synchronized(cache) {
        playlistCache?.let { return it }
        val raw = prefs(context).getString(KEY_PLAYLISTS, null)
        val arr = raw?.let { runCatching { JSONArray(it) }.getOrNull() }
        val out = ArrayList<Playlist>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val vids = o.optJSONArray("videos") ?: JSONArray()
                val videos = ArrayList<Entry>()
                for (j in 0 until vids.length()) {
                    vids.optJSONObject(j)?.let { e -> entryFromJson(e)?.let { videos += it } }
                }
                out += Playlist(id, o.optString("title").ifBlank { "Playlist" }, videos)
            }
        }
        playlistCache = out
        out
    }

    fun createPlaylist(context: Context, title: String): Playlist = synchronized(cache) {
        val playlist = Playlist(
            id = "ytpl_${System.currentTimeMillis()}",
            title = title.trim().ifBlank { "New playlist" },
            videos = emptyList(),
        )
        writePlaylists(context, listOf(playlist) + playlists(context))
        playlist
    }

    fun playlistContains(context: Context, playlistId: String, videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) return false
        return playlists(context).firstOrNull { it.id == playlistId }
            ?.videos?.any { it.videoId == videoId } == true
    }

    /** Adds or removes [item] from [playlistId]; returns true when now included. */
    fun toggleInPlaylist(context: Context, playlistId: String, item: YtItem): Boolean {
        val entry = item.toEntry() ?: return false
        var nowSaved = false
        val next = playlists(context).map { playlist ->
            if (playlist.id != playlistId) return@map playlist
            val existing = playlist.videos.any { it.videoId == entry.videoId }
            nowSaved = !existing
            playlist.copy(
                videos = if (existing) playlist.videos.filterNot { it.videoId == entry.videoId }
                else (playlist.videos + entry).takeLast(MAX_ENTRIES),
            )
        }
        writePlaylists(context, next)
        YouTubeSession.feed?.refreshYou()
        return nowSaved
    }

    private fun YtItem.toEntry(): Entry? {
        val id = videoId?.takeIf { it.isNotBlank() } ?: return null
        return Entry(
            videoId = id,
            title = title,
            author = author,
            thumbnail = thumbnail,
            channelId = channelId,
            savedAt = System.currentTimeMillis(),
        )
    }

    private fun entryFromJson(o: JSONObject): Entry? {
        val id = o.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        return Entry(
            videoId = id,
            title = o.optString("title"),
            author = o.optString("author").takeIf { it.isNotBlank() },
            thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
            channelId = o.optString("channelId").takeIf { it.isNotBlank() },
            savedAt = o.optLong("savedAt", 0L),
        )
    }

    private fun writePlaylists(context: Context, list: List<Playlist>) {
        playlistCache = list
        val arr = JSONArray()
        list.forEach { playlist ->
            val videos = JSONArray()
            playlist.videos.forEach { e ->
                videos.put(
                    JSONObject()
                        .put("videoId", e.videoId)
                        .put("title", e.title)
                        .put("author", e.author.orEmpty())
                        .put("thumbnail", e.thumbnail.orEmpty())
                        .put("channelId", e.channelId.orEmpty())
                        .put("savedAt", e.savedAt),
                )
            }
            arr.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("title", playlist.title)
                    .put("videos", videos),
            )
        }
        prefs(context).edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }
}
