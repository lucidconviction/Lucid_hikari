package com.hikari.app.music

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Deezer's public API — the metadata backbone of the Music page (charts,
 * search, artist/album/playlist pages). No key and no login. Requests go
 * through [Http]'s quiet client and, when Deezer refuses the region or the
 * network, through the same public CORS proxy the reference app uses.
 */
object DeezerApi {

    private const val BASE = "https://api.deezer.com"
    private const val PROXY = "https://wave-proxy.aymanisthedude1.workers.dev/proxy?url="
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    data class Home(
        val sections: LinkedHashMap<String, List<MusicTrack>>,
        val artists: List<MusicArtist>,
        val albums: List<MusicAlbum>,
        val playlists: List<MusicPlaylist>,
    ) {
        val hero: MusicTrack? get() = sections.values.firstOrNull()?.firstOrNull()
    }

    private val homeMutex = Mutex()
    @Volatile private var homeCache: Home? = null
    @Volatile private var homeAt = 0L
    private const val HOME_TTL_MS = 10 * 60 * 1000L

    /** Home shelves: charts, new releases, curated mixes and genre rows. */
    suspend fun home(force: Boolean = false): Home = withContext(Dispatchers.IO) {
        if (!force && homeCache != null && System.currentTimeMillis() - homeAt < HOME_TTL_MS) {
            return@withContext homeCache!!
        }
        homeMutex.withLock {
            if (!force && homeCache != null && System.currentTimeMillis() - homeAt < HOME_TTL_MS) {
                return@withLock homeCache!!
            }
            val sections = LinkedHashMap<String, List<MusicTrack>>()
            val artists = runCatching { chartArtists() }.getOrDefault(emptyList())
            val albums = runCatching { newReleases() }.getOrDefault(emptyList())
            val playlists = runCatching { chartPlaylists() }.getOrDefault(emptyList())

            runCatching { chartTracks(25) }.getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { sections["Top Global & Trending Hits"] = it }

            val genreQueries = linkedMapOf(
                "Pop Essentials" to "Top Pop Hits",
                "Hip-Hop & Rap Heavyweights" to "Hip Hop Hits",
                "Electronic, Dance & EDM" to "Electronic Dance",
                "Rock Classics & Alternative" to "Rock Essentials",
                "Film & Anime Soundtracks" to "Hans Zimmer Soundtracks",
                "Chill, Lofi & Ambient Beats" to "Lofi Beats Chill",
            )
            // Four at a time: six searches in one burst is fine, but the phone
            // may be on a slow network and the home shelf can paint page by page.
            coroutineScope {
                genreQueries.entries.map { (label, query) ->
                    async {
                        label to runCatching { searchTracks(query, 15) }.getOrDefault(emptyList())
                    }
                }.awaitAll().forEach { (label, tracks) ->
                    if (tracks.isNotEmpty()) sections[label] = tracks
                }
            }

            val data = Home(sections, artists, albums, playlists)
            homeCache = data
            homeAt = System.currentTimeMillis()
            data
        }
    }

    suspend fun search(query: String): MusicSearchData = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext MusicSearchData()
        coroutineScope {
            val tracks = async { runCatching { searchTracks(q, 40) }.getOrDefault(emptyList()) }
            val artists = async { runCatching { searchArtists(q, 20) }.getOrDefault(emptyList()) }
            val albums = async { runCatching { searchAlbums(q, 20) }.getOrDefault(emptyList()) }
            val playlists = async { runCatching { searchPlaylists(q, 20) }.getOrDefault(emptyList()) }
            MusicSearchData(tracks.await(), artists.await(), albums.await(), playlists.await())
        }
    }

    suspend fun artistDetails(artistId: String): MusicArtistDetails? = withContext(Dispatchers.IO) {
        if (artistId.isBlank()) return@withContext null
        val artistObj = json("/artist/$artistId") ?: return@withContext null
        val artist = MusicArtist.fromJson(artistObj)
        val topTracks = runCatching {
            val obj = json("/artist/$artistId/top?limit=50") ?: return@runCatching emptyList()
            parseTrackArray(obj.optJSONArray("data"))
        }.getOrDefault(emptyList())
        val albums = runCatching {
            val obj = json("/artist/$artistId/albums?limit=30") ?: return@runCatching emptyList()
            parseAlbumArray(obj.optJSONArray("data"))
        }.getOrDefault(emptyList())
        MusicArtistDetails(artist, topTracks, albums)
    }

    suspend fun albumDetails(albumId: String): MusicAlbumDetails? = withContext(Dispatchers.IO) {
        if (albumId.isBlank()) return@withContext null
        val obj = json("/album/$albumId") ?: return@withContext null
        val album = MusicAlbum.fromJson(obj)
        val tracks = parseTrackArray(obj.optJSONObject("tracks")?.optJSONArray("data") ?: JSONArray())
        MusicAlbumDetails(album, tracks.ifEmpty { album.tracks })
    }

    suspend fun playlistDetails(playlistId: String): MusicPlaylistDetails? = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext null
        val obj = json("/playlist/$playlistId") ?: return@withContext null
        val playlist = MusicPlaylist.fromJson(obj)
        val tracks = parseTrackArray(obj.optJSONObject("tracks")?.optJSONArray("data") ?: JSONArray())
        MusicPlaylistDetails(playlist, tracks.ifEmpty { playlist.tracks })
    }

    suspend fun genreTracks(query: String, limit: Int = 50): List<MusicTrack> =
        withContext(Dispatchers.IO) { searchTracks(query, limit) }

    // ---- endpoints ---------------------------------------------------------

    private suspend fun chartTracks(limit: Int): List<MusicTrack> = withContext(Dispatchers.IO) {
        parseTrackArray(json("/chart/0/tracks?limit=$limit")?.optJSONArray("data"))
    }

    private suspend fun chartArtists(): List<MusicArtist> = withContext(Dispatchers.IO) {
        parseArtistArray(json("/chart/0/artists?limit=20")?.optJSONArray("data"))
    }

    private suspend fun chartPlaylists(): List<MusicPlaylist> = withContext(Dispatchers.IO) {
        parsePlaylistArray(json("/chart/0/playlists?limit=20")?.optJSONArray("data"))
    }

    private suspend fun newReleases(): List<MusicAlbum> = withContext(Dispatchers.IO) {
        val releases = parseAlbumArray(json("/chart/0/albums?limit=20")?.optJSONArray("data"))
        if (releases.isNotEmpty()) releases else parseAlbumArray(
            json("/editorial/0/releases?limit=20")?.optJSONArray("data")
        )
    }

    suspend fun searchTracks(query: String, limit: Int): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            parseTrackArray(
                json("/search/track?q=${enc(query)}&limit=$limit")?.optJSONArray("data")
            )
        }

    suspend fun searchArtists(query: String, limit: Int): List<MusicArtist> =
        withContext(Dispatchers.IO) {
            parseArtistArray(
                json("/search/artist?q=${enc(query)}&limit=$limit")?.optJSONArray("data")
            )
        }

    suspend fun searchAlbums(query: String, limit: Int): List<MusicAlbum> =
        withContext(Dispatchers.IO) {
            parseAlbumArray(
                json("/search/album?q=${enc(query)}&limit=$limit")?.optJSONArray("data")
            )
        }

    suspend fun searchPlaylists(query: String, limit: Int): List<MusicPlaylist> =
        withContext(Dispatchers.IO) {
            parsePlaylistArray(
                json("/search/playlist?q=${enc(query)}&limit=$limit")?.optJSONArray("data")
            )
        }

    // ---- transport ---------------------------------------------------------

    /** One Deezer JSON object, direct first and through the proxy if refused. */
    private fun json(path: String): JSONObject? {
        val full = BASE + path
        val direct = runCatching {
            Http.getStringQuiet(full, mapOf("Accept" to "application/json", "User-Agent" to UA))
        }.getOrNull()
        parseBody(direct)?.let { return it }
        val proxied = runCatching {
            Http.getStringQuiet(
                PROXY + URLEncoder.encode(full, "UTF-8"),
                mapOf("Accept" to "application/json", "User-Agent" to UA),
            )
        }.getOrNull()
        return parseBody(proxied)
    }

    private fun parseBody(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (obj.has("error") && obj.optJSONObject("error") != null) return null
        return obj
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun parseTrackArray(arr: JSONArray?): List<MusicTrack> {
        if (arr == null) return emptyList()
        val out = ArrayList<MusicTrack>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val track = runCatching { MusicTrack.fromJson(o) }.getOrNull() ?: continue
            if (track.id.isNotBlank()) out += track
        }
        return out
    }

    private fun parseArtistArray(arr: JSONArray?): List<MusicArtist> {
        if (arr == null) return emptyList()
        val out = ArrayList<MusicArtist>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { runCatching { out += MusicArtist.fromJson(it) } }
        }
        return out
    }

    private fun parseAlbumArray(arr: JSONArray?): List<MusicAlbum> {
        if (arr == null) return emptyList()
        val out = ArrayList<MusicAlbum>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { runCatching { out += MusicAlbum.fromJson(it) } }
        }
        return out
    }

    private fun parsePlaylistArray(arr: JSONArray?): List<MusicPlaylist> {
        if (arr == null) return emptyList()
        val out = ArrayList<MusicPlaylist>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { runCatching { out += MusicPlaylist.fromJson(it) } }
        }
        return out
    }
}
