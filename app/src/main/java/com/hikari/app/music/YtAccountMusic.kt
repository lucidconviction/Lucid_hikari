package com.hikari.app.music

import android.content.Context
import com.hikari.app.data.Logs
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The signed-in half of Lucid Music, using **the YouTube page's own account**.
 *
 * The YouTube tab signs in with the TV device-code flow and MediaServiceCore
 * keeps the OAuth session. This repository asks that same service for the
 * account's Music home, Liked Music and playlists, so choosing YouTube Music in
 * Settings needs no second login and no cookie jar. This is the same bridge the
 * Nuvio One music section uses.
 */
object YtAccountMusic {

    private const val TAG = "MusicAccount"
    private const val MAX_PAGES = 300

    data class Library(
        val liked: List<MusicTrack> = emptyList(),
        val playlists: List<MusicPlaylist> = emptyList(),
    )

    @Volatile
    private var context: Context? = null

    /** Playlist cards kept from the last library load, for opening later. */
    private val playlistItems = ConcurrentHashMap<String, MediaItem>()

    @Synchronized
    fun init(appContext: Context) {
        context = appContext.applicationContext
        GlobalPreferences.instance(appContext.applicationContext)
        YouTubeServiceManager.instance()
    }

    fun isSignedIn(): Boolean = runCatching {
        service().signInService.isSigned
    }.getOrDefault(false)

    fun accountName(): String? = runCatching {
        service().signInService.selectedAccount?.name
            ?: service().signInService.selectedAccount?.email
    }.getOrNull()

    /** The account's personalised Music home. Browse cards that cannot be
     *  opened yet are skipped; every song becomes a playable row. */
    fun home(): DeezerApi.Home? = runCatching {
        initIfNeeded()
        if (!isSignedIn()) return null
        val groups = service().contentService.getMusicObserve()
            .timeout(25, TimeUnit.SECONDS)
            .toList()
            .blockingGet()
            .flatten()
        val sections = LinkedHashMap<String, List<MusicTrack>>()
        for (group in groups) {
            val tracks = group.mediaItems.orEmpty()
                .mapNotNull { it.toTrack() }
                .distinctBy { it.id }
            val title = group.title.orEmpty().ifBlank { "Music" }
            if (tracks.isNotEmpty() && title.isNotBlank()) {
                sections.putIfAbsent(title, tracks.take(20))
            }
        }
        if (sections.isEmpty()) return null
        DeezerApi.Home(
            sections = sections,
            artists = emptyList(),
            albums = emptyList(),
            playlists = emptyList(),
        )
    }.onFailure { Logs.log(TAG, "account home failed: ${it.message}") }.getOrNull()

    /** Liked Music and the account's playlists. */
    fun library(): Library = runCatching {
        initIfNeeded()
        if (!isSignedIn()) return Library()
        val groups = service().contentService.getMusicObserve()
            .timeout(25, TimeUnit.SECONDS)
            .toList()
            .blockingGet()
            .flatten()
        val likedGroup = groups.firstOrNull {
            it.title.orEmpty().contains("liked", ignoreCase = true)
        }
        val liked = allItems(likedGroup).mapNotNull { it.toTrack() }.distinctBy { it.id }
        val playlists = runCatching { accountPlaylists() }.getOrDefault(emptyList())
        Library(liked, playlists)
    }.onFailure { Logs.log(TAG, "account library failed: ${it.message}") }.getOrElse { Library() }

    /** Tracks of an account playlist. Pass the id from [accountPlaylists] with
     *  its `account:` prefix. */
    fun playlistTracks(playlistId: String): List<MusicTrack> = runCatching {
        initIfNeeded()
        val id = playlistId.removePrefix("account:")
        val item = playlistItems[id]
        val group = if (item != null) {
            service().contentService.getGroupObserve(item).timeout(20, TimeUnit.SECONDS).blockingFirst()
        } else {
            service().contentService.getGroupObserve("VL$id").timeout(20, TimeUnit.SECONDS).blockingFirst()
        }
        allItems(group).mapNotNull { it.toTrack() }.distinctBy { it.id }
    }.onFailure { Logs.log(TAG, "playlist $playlistId failed: ${it.message}") }.getOrDefault(emptyList())

    /** Mirror a like to the account's Liked Music. Best effort: the local
     *  library updates immediately either way. */
    fun syncLike(track: MusicTrack, liked: Boolean) {
        if (track.videoId.isBlank()) return
        runCatching {
            initIfNeeded()
            if (!isSignedIn()) return
            val item = com.liskovsoft.youtubeapi.service.data.YouTubeMediaItem.fromIds(
                track.videoId,
                track.artistId.takeIf { it.isNotBlank() }.orEmpty(),
            )
            val call = if (liked) {
                service().mediaItemService.setLikeObserve(item)
            } else {
                service().mediaItemService.removeLikeObserve(item)
            }
            call.ignoreElements().blockingAwait()
        }.onFailure { Logs.log(TAG, "like sync failed: ${it.message}") }
    }

    fun invalidate() {
        runCatching { service().invalidateCache() }
    }

    private fun initIfNeeded() {
        val ctx = context ?: com.hikari.app.HikariApp.instance.applicationContext
        if (context == null) init(ctx)
    }

    private fun service() = YouTubeServiceManager.instance()

    private fun accountPlaylists(): List<MusicPlaylist> {
        val items = mutableListOf<MediaItem>()
        var group: MediaGroup? = service().contentService.getPlaylistsObserve()
            .timeout(20, TimeUnit.SECONDS)
            .blockingFirst()
        var pages = 0
        while (group != null) {
            items += group.mediaItems.orEmpty()
            if (group.nextPageKey.isNullOrBlank() || pages++ >= MAX_PAGES) break
            group = runCatching { service().contentService.continueGroup(group) }.getOrNull()
        }
        return items.mapNotNull { item ->
            val id = item.accountPlaylistId() ?: return@mapNotNull null
            if (item.title.orEmpty().contains("liked", ignoreCase = true)) return@mapNotNull null
            playlistItems[id] = item
            MusicPlaylist(
                id = "account:$id",
                title = item.title.orEmpty().ifBlank { "Playlist" },
                creator = accountName().orEmpty().ifBlank { "YouTube Music" },
                coverUrl = item.cardImageUrl ?: item.backgroundImageUrl ?: "",
            )
        }.distinctBy { it.id }
    }

    private fun MediaItem.accountPlaylistId(): String? {
        val direct = playlistId?.takeIf { it.isNotBlank() }
        if (direct != null) return normalize(direct)
        val key = reloadPageKey?.takeIf { it.isNotBlank() }
            ?: channelId?.takeIf { it.isNotBlank() }
        return key?.takeIf { it.startsWith("VL") }?.removePrefix("VL")
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalize(id: String): String =
        if (id.startsWith("VL") && id.length > 2) id.removePrefix("VL") else id

    private fun allItems(first: MediaGroup?): List<MediaItem> {
        if (first == null) return emptyList()
        val out = mutableListOf<MediaItem>()
        var group: MediaGroup? = first
        var pages = 0
        while (group != null) {
            out += group.mediaItems.orEmpty()
            if (group.nextPageKey.isNullOrBlank() || pages++ >= MAX_PAGES) break
            group = runCatching { service().contentService.continueGroup(group) }.getOrNull()
        }
        return out
    }

    private fun MediaItem.toTrack(): MusicTrack? {
        val id = videoId?.takeIf { it.isNotBlank() } ?: return null
        val label = title?.takeIf { it.isNotBlank() } ?: return null
        val byline = author?.takeIf { it.isNotBlank() }
            ?: secondTitle?.toString()?.substringBefore(" • ")?.trim().orEmpty()
        return MusicTrack(
            id = id,
            title = label,
            artist = byline.ifBlank { "YouTube Music" },
            artistId = channelId.orEmpty(),
            album = "YouTube Music",
            coverUrl = (cardImageUrl ?: backgroundImageUrl).orEmpty(),
            durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(0),
            videoId = id,
        )
    }
}
