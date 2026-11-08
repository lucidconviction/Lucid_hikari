package com.hikari.app.music

import org.json.JSONArray
import org.json.JSONObject

/** One song. Mirrors the fields the Music page needs from Deezer. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val album: String = "Single",
    val albumId: String = "",
    val coverUrl: String = "",
    val durationSeconds: Int = 0,
    val explicit: Boolean = false,
    val previewUrl: String? = null,
    val trackNumber: Int? = null,
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            return "%d:%02d".format(durationSeconds / 60, durationSeconds % 60)
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("artistId", artistId)
        put("album", album)
        put("albumId", albumId)
        put("coverUrl", coverUrl)
        put("durationSeconds", durationSeconds)
        put("explicit", explicit)
        previewUrl?.let { put("previewUrl", it) }
        trackNumber?.let { put("trackNumber", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): MusicTrack {
            fun optString(vararg keys: String): String {
                for (k in keys) {
                    val v = o.optString(k)
                    if (v.isNotBlank() && v != "null") return v
                }
                return ""
            }

            fun optInt(vararg keys: String): Int {
                for (k in keys) {
                    val v = o.optInt(k, Int.MIN_VALUE)
                    if (v != Int.MIN_VALUE) return v
                }
                return 0
            }

            val albumObj = o.optJSONObject("album") ?: JSONObject()
            val artistObj = o.optJSONObject("artist") ?: JSONObject()
            var cover = if (albumObj.length() > 0) {
                listOf("cover_xl", "cover_big", "cover_medium", "cover_small")
                    .firstNotNullOfOrNull { albumObj.optString(it).takeIf { v -> v.isNotBlank() } }
                    .orEmpty()
            } else {
                listOf("cover_xl", "cover_big", "cover_medium", "cover_small", "cover", "coverUrl")
                    .firstNotNullOfOrNull { o.optString(it).takeIf { v -> v.isNotBlank() } }
                    .orEmpty()
            }
            if (cover.startsWith("http://")) cover = "https://" + cover.removePrefix("http://")
            val explicitValue = if (o.has("explicit_lyrics")) o.optBoolean("explicit_lyrics") else o.optBoolean("explicit")
            return MusicTrack(
                id = optString("id"),
                title = optString("title", "title_short").ifBlank { "Unknown Track" },
                artist = artistObj.optString("name").ifBlank { optString("artist") }.ifBlank { "Unknown Artist" },
                artistId = artistObj.optString("id").ifBlank { optString("artistId") },
                album = albumObj.optString("title").ifBlank { optString("album") }.ifBlank { "Single" },
                albumId = albumObj.optString("id").ifBlank { optString("albumId") },
                coverUrl = cover,
                durationSeconds = optInt("duration", "durationSeconds"),
                explicit = explicitValue,
                previewUrl = o.optString("preview").ifBlank { o.optString("previewUrl") }.takeIf { it.isNotBlank() },
                trackNumber = optInt("track_position", "trackNumber").takeIf { it > 0 },
            )
        }
    }
}

data class MusicArtist(
    val id: String,
    val name: String,
    val pictureUrl: String = "",
    val fanCount: Long = 0,
    val albumCount: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("pictureUrl", pictureUrl)
        put("fanCount", fanCount)
        put("albumCount", albumCount)
    }

    companion object {
        fun fromJson(o: JSONObject): MusicArtist {
            var pic = ""
            for (k in listOf("picture_xl", "picture_big", "picture_medium", "picture_small", "picture", "pictureUrl")) {
                val v = o.optString(k)
                if (v.isNotBlank()) { pic = v; break }
            }
            if (pic.startsWith("http://")) pic = "https://" + pic.removePrefix("http://")
            return MusicArtist(
                id = o.optString("id"),
                name = o.optString("name").ifBlank { "Artist" },
                pictureUrl = pic,
                fanCount = o.optLong("nb_fan", o.optLong("fanCount")),
                albumCount = o.optInt("nb_album", o.optInt("albumCount")),
            )
        }
    }
}

data class MusicAlbum(
    val id: String,
    val title: String,
    val artistName: String = "",
    val artistId: String = "",
    val coverUrl: String = "",
    val releaseDate: String = "",
    val trackCount: Int = 0,
    val tracks: List<MusicTrack> = emptyList(),
) {
    companion object {
        fun fromJson(o: JSONObject): MusicAlbum {
            val artistObj = o.optJSONObject("artist") ?: JSONObject()
            var cover = ""
            for (k in listOf("cover_xl", "cover_big", "cover_medium", "cover_small", "cover")) {
                val v = o.optString(k)
                if (v.isNotBlank()) { cover = v; break }
            }
            if (cover.startsWith("http://")) cover = "https://" + cover.removePrefix("http://")
            val tracksArr = o.optJSONObject("tracks")?.optJSONArray("data") ?: o.optJSONArray("tracks")
            val tracks = mutableListOf<MusicTrack>()
            if (tracksArr != null) {
                for (i in 0 until tracksArr.length()) {
                    tracksArr.optJSONObject(i)?.let { tracks += MusicTrack.fromJson(it) }
                }
            }
            return MusicAlbum(
                id = o.optString("id"),
                title = o.optString("title").ifBlank { "Album" },
                artistName = artistObj.optString("name").ifBlank { o.optString("artistName") },
                artistId = artistObj.optString("id").ifBlank { o.optString("artistId") },
                coverUrl = cover,
                releaseDate = o.optString("release_date"),
                trackCount = o.optInt("nb_tracks", tracks.size),
                tracks = tracks,
            )
        }
    }
}

data class MusicPlaylist(
    val id: String,
    val title: String,
    val creator: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val tracks: List<MusicTrack> = emptyList(),
) {
    companion object {
        fun fromJson(o: JSONObject): MusicPlaylist {
            val creatorObj = o.optJSONObject("creator") ?: JSONObject()
            var cover = ""
            for (k in listOf("picture_xl", "picture_big", "picture_medium", "picture_small", "picture", "cover")) {
                val v = o.optString(k)
                if (v.isNotBlank()) { cover = v; break }
            }
            if (cover.startsWith("http://")) cover = "https://" + cover.removePrefix("http://")
            val tracksArr = o.optJSONObject("tracks")?.optJSONArray("data") ?: o.optJSONArray("tracks")
            val tracks = mutableListOf<MusicTrack>()
            if (tracksArr != null) {
                for (i in 0 until tracksArr.length()) {
                    tracksArr.optJSONObject(i)?.let { tracks += MusicTrack.fromJson(it) }
                }
            }
            return MusicPlaylist(
                id = o.optString("id"),
                title = o.optString("title").ifBlank { "Playlist" },
                creator = creatorObj.optString("name").ifBlank { o.optString("creator") },
                coverUrl = cover,
                trackCount = o.optInt("nb_tracks", tracks.size),
                tracks = tracks,
            )
        }
    }
}

/** A playlist the user made in Hikari. */
data class UserPlaylist(
    val id: String,
    val title: String,
    val createdAt: String = "",
    val tracks: MutableList<MusicTrack> = mutableListOf(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("tracks", JSONArray().apply { tracks.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): UserPlaylist {
            val arr = o.optJSONArray("tracks") ?: JSONArray()
            val tracks = mutableListOf<MusicTrack>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { tracks += MusicTrack.fromJson(it) }
            }
            return UserPlaylist(
                id = o.optString("id"),
                title = o.optString("title").ifBlank { "My Playlist" },
                createdAt = o.optString("createdAt"),
                tracks = tracks,
            )
        }
    }
}

data class MusicSearchData(
    val tracks: List<MusicTrack> = emptyList(),
    val artists: List<MusicArtist> = emptyList(),
    val albums: List<MusicAlbum> = emptyList(),
    val playlists: List<MusicPlaylist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

data class MusicArtistDetails(
    val artist: MusicArtist,
    val topTracks: List<MusicTrack> = emptyList(),
    val albums: List<MusicAlbum> = emptyList(),
)

data class MusicAlbumDetails(
    val album: MusicAlbum,
    val tracks: List<MusicTrack> = emptyList(),
)

data class MusicPlaylistDetails(
    val playlist: MusicPlaylist,
    val tracks: List<MusicTrack> = emptyList(),
)

data class LyricsLine(val timeMs: Long, val text: String)

data class MusicLyrics(
    val trackId: String = "",
    val synced: Boolean = false,
    val plain: String = "",
    val lines: List<LyricsLine> = emptyList(),
) {
    val isEmpty: Boolean get() = plain.isBlank() && lines.isEmpty()

    companion object {
        val EMPTY = MusicLyrics()
    }
}

/** One playable URL for a track, with whatever headers the host needs. */
data class MusicStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val label: String = "Audio",
    val lossless: Boolean = false,
    val isDash: Boolean = false,
    val isHls: Boolean = false,
)

data class DownloadedMusic(
    val track: MusicTrack,
    val fileName: String,
    val format: String,
    val quality: String,
    val fileSize: Long,
    val downloadedAt: Long,
)
