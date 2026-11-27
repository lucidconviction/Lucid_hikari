package com.hikari.app.music

import com.hikari.app.music.ytm.YtMusicRepository
import com.hikari.app.music.ytm.innertube.Innertube
import com.hikari.app.music.ytm.model.SearchFilter as YtFilter
import com.hikari.app.music.ytm.model.SearchResult as YtSearchResult
import com.hikari.app.music.ytm.model.Song as YtSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The BitChord / Nuvio One YouTube-Music catalogue, adapted to Lucid's
 * existing [MusicTrack] model and [MusicPlayer].
 *
 * Phase 2a: Home and Search read YouTube Music anonymously (no login), and
 * every track carries its exact video id so playback still runs through the
 * app's one player and its YouTube engine. Account library, artist/album
 * pages, downloads and the BitChord MediaSession follow in phase 2b.
 */
internal object YtMusicCatalog {

    @Volatile
    private var configured = false

    fun warmUp() {
        if (configured) return
        configured = true
        runCatching {
            Innertube.appLanguage = { java.util.Locale.getDefault().toLanguageTag() }
        }
    }

    /** Home shelves as a Deezer-shaped page, so the existing Nuvio Home can
     *  draw them without a second feed abstraction. */
    suspend fun home(): DeezerApi.Home? = withContext(Dispatchers.IO) {
        warmUp()
        val feed = runCatching { YtMusicRepository.home() }.getOrNull()
            ?.getOrNull() ?: return@withContext null
        val sections = LinkedHashMap<String, List<MusicTrack>>()
        for (shelf in feed.shelves) {
            val tracks = shelf.items
                .mapNotNull { item ->
                    val id = item.videoId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    MusicTrack(
                        id = id,
                        title = item.title,
                        artist = item.subtitle.ifBlank { "YouTube Music" },
                        coverUrl = item.thumbnailUrl.orEmpty(),
                        videoId = id,
                    )
                }
                .distinctBy { it.id }
            if (tracks.isNotEmpty() && shelf.title.isNotBlank()) {
                sections.putIfAbsent(shelf.title, tracks.take(20))
            }
        }
        if (sections.isEmpty()) return@withContext null
        DeezerApi.Home(
            sections = sections,
            artists = emptyList(),
            albums = emptyList(),
            playlists = emptyList(),
        )
    }

    /** Search songs from YouTube Music's catalogue. */
    suspend fun search(query: String, filter: MusicFilter): MusicSearchData =
        withContext(Dispatchers.IO) {
            warmUp()
            val ytFilter = when (filter) {
                MusicFilter.ALL, MusicFilter.TRACKS -> YtFilter.ALL
                MusicFilter.ARTISTS -> YtFilter.ARTISTS
                MusicFilter.ALBUMS -> YtFilter.ALBUMS
                MusicFilter.PLAYLISTS -> YtFilter.PLAYLISTS
            }
            val rows = runCatching { YtMusicRepository.search(query, ytFilter) }
                .getOrNull()?.getOrNull().orEmpty()
            val tracks = rows.mapNotNull { row ->
                when (row) {
                    is YtSearchResult.TopTrack -> row.song.toTrack()
                    is YtSearchResult.Track -> row.song.toTrack()
                    is YtSearchResult.Browse -> null
                }
            }.distinctBy { it.id }
            val artists = rows.mapNotNull { row ->
                val item = (row as? YtSearchResult.Browse)?.item ?: return@mapNotNull null
                if (item.type != com.hikari.app.music.ytm.model.BrowseType.ARTIST) return@mapNotNull null
                MusicArtist(
                    id = "yt:${item.browseId}",
                    name = item.title,
                    pictureUrl = item.thumbnailUrl.orEmpty(),
                )
            }.distinctBy { it.id }
            val albums = rows.mapNotNull { row ->
                val item = (row as? YtSearchResult.Browse)?.item ?: return@mapNotNull null
                if (item.type != com.hikari.app.music.ytm.model.BrowseType.ALBUM) return@mapNotNull null
                MusicAlbum(
                    id = "yt:${item.browseId}",
                    title = item.title,
                    artistName = item.subtitle,
                    coverUrl = item.thumbnailUrl.orEmpty(),
                )
            }.distinctBy { it.id }
            val playlists = rows.mapNotNull { row ->
                val item = (row as? YtSearchResult.Browse)?.item ?: return@mapNotNull null
                if (item.type != com.hikari.app.music.ytm.model.BrowseType.PLAYLIST) return@mapNotNull null
                MusicPlaylist(
                    id = "yt:${item.browseId}",
                    title = item.title,
                    creator = item.subtitle,
                    coverUrl = item.thumbnailUrl.orEmpty(),
                )
            }.distinctBy { it.id }
            MusicSearchData(
                tracks = tracks,
                artists = artists,
                albums = albums,
                playlists = playlists,
            )
        }

    /** A YouTube Music artist page: top songs plus its release shelves. */
    suspend fun artistDetails(browseId: String): MusicArtistDetails? =
        withContext(Dispatchers.IO) {
            val page = runCatching { YtMusicRepository.artistPage(browseId) }
                .getOrNull()?.getOrNull() ?: return@withContext null
            val songs = page.songs.map { it.toTrack() }
            val albums = page.sections.flatMap { shelf ->
                shelf.items.mapNotNull { item ->
                    val id = item.browseId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    MusicAlbum(
                        id = id,
                        title = item.title,
                        artistName = item.subtitle,
                        coverUrl = item.thumbnailUrl.orEmpty(),
                    )
                }
            }.distinctBy { it.id }
            if (songs.isEmpty() && albums.isEmpty()) return@withContext null
            MusicArtistDetails(
                artist = MusicArtist(
                    id = browseId,
                    name = page.name.orEmpty().ifBlank { "Artist" },
                    pictureUrl = page.thumbnailUrl.orEmpty(),
                ),
                topTracks = songs,
                albums = albums,
            )
        }

    data class YtMood(
        val title: String,
        val browseId: String,
        val params: String?,
        val imageUrl: String?,
    )

    data class YtMoodSection(val title: String, val items: List<YtMood>)

    /** YouTube Music's own Explore categories, merged into Lucid's Explore. */
    suspend fun moodSections(): List<YtMoodSection> = withContext(Dispatchers.IO) {
        warmUp()
        val sections = runCatching { YtMusicRepository.moodAndGenres() }
            .getOrNull()?.getOrNull().orEmpty()
        sections.mapNotNull { section ->
            val items = section.items.map {
                YtMood(
                    title = it.title,
                    browseId = it.browseId,
                    params = it.params,
                    imageUrl = it.thumbnailUrl,
                )
            }
            if (items.isEmpty()) null else YtMoodSection(section.title, items)
        }
    }

    /** The songs behind one Explore category/mood. */
    suspend fun moodTracks(mood: YtMood): List<MusicTrack> = withContext(Dispatchers.IO) {
        warmUp()
        val shelves = runCatching {
            YtMusicRepository.moodGenreShelves(mood.browseId, mood.params)
        }.getOrNull()?.getOrNull().orEmpty()
        shelves.flatMap { shelf ->
            shelf.items.mapNotNull { item ->
                val id = item.videoId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MusicTrack(
                    id = id,
                    title = item.title,
                    artist = item.subtitle.ifBlank { "YouTube Music" },
                    coverUrl = item.thumbnailUrl.orEmpty(),
                    videoId = id,
                )
            }
        }.distinctBy { it.id }.take(50)
    }

    /** A YouTube Music album page, shaped like the Deezer detail sheet. */
    suspend fun albumDetails(browseId: String): MusicAlbumDetails? =
        withContext(Dispatchers.IO) {
            val page = runCatching { YtMusicRepository.browseSongs(browseId) }
                .getOrNull()?.getOrNull() ?: return@withContext null
            val header = page.header
            val tracks = page.songs.map { it.toTrack() }
            if (tracks.isEmpty()) return@withContext null
            MusicAlbumDetails(
                album = MusicAlbum(
                    id = browseId,
                    title = header?.title.orEmpty().ifBlank { "Album" },
                    artistName = header?.subtitle.orEmpty(),
                    coverUrl = header?.thumbnailUrl.orEmpty(),
                    trackCount = tracks.size,
                    tracks = tracks,
                ),
                tracks = tracks,
            )
        }

    /** A YouTube Music playlist page (editorial, mix or account playlist). */
    suspend fun playlistDetails(browseId: String): MusicPlaylistDetails? =
        withContext(Dispatchers.IO) {
            val page = runCatching { YtMusicRepository.browseSongs(browseId) }
                .getOrNull()?.getOrNull() ?: return@withContext null
            val header = page.header
            val tracks = page.songs.map { it.toTrack() }
            if (tracks.isEmpty()) return@withContext null
            MusicPlaylistDetails(
                playlist = MusicPlaylist(
                    id = browseId,
                    title = header?.title.orEmpty().ifBlank { "Playlist" },
                    creator = header?.subtitle.orEmpty().ifBlank { "YouTube Music" },
                    coverUrl = header?.thumbnailUrl.orEmpty(),
                    trackCount = tracks.size,
                    tracks = tracks,
                ),
                tracks = tracks,
            )
        }

    private fun YtSong.toTrack(): MusicTrack {
        val seconds = durationText?.let { text ->
            val parts = text.trim().split(':')
            when (parts.size) {
                2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                3 -> (parts[0].toIntOrNull() ?: 0) * 3600 +
                    (parts[1].toIntOrNull() ?: 0) * 60 +
                    (parts[2].toIntOrNull() ?: 0)
                else -> 0
            }
        } ?: 0
        return MusicTrack(
            id = videoId,
            title = title,
            artist = artist.ifBlank { "YouTube Music" },
            artistId = artistId.orEmpty(),
            album = albumName.orEmpty().ifBlank { "YouTube Music" },
            albumId = albumId.orEmpty(),
            coverUrl = thumbnailUrl.orEmpty(),
            durationSeconds = seconds,
            explicit = isExplicit == true,
            videoId = videoId,
        )
    }
}
