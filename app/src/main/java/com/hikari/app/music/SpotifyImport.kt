package com.hikari.app.music

import android.text.Html
import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a public Spotify playlist from its embed page — no Spotify login
 * and no API key.
 *
 * The embed page renders one track row per song with the title in an `h3` and
 * the byline in an `h4`. Each row is matched to Deezer once; when Deezer has no
 * match the row is still kept, and the player resolves it by title on YouTube.
 */
object SpotifyImport {

    data class Row(val title: String, val artist: String)
    data class Imported(val playlist: String, val added: Int, val total: Int)

    suspend fun import(context: android.content.Context, input: String): Imported =
        withContext(Dispatchers.IO) {
            val id = playlistId(input) ?: error("Paste a Spotify playlist link or ID")
            val url = "https://open.spotify.com/embed/playlist/$id"
            val html = Http.getStringQuiet(url)
                ?: error("Spotify didn't answer. Check the link and the connection.")
            val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
                .find(html)?.groupValues?.getOrNull(1)
                ?.let(::decode)
                ?.takeIf { it.isNotBlank() }
                ?: "Spotify import"
            val rowRegex = Regex(
                "TracklistRow_title[^>]*>(.*?)</h3>.*?TracklistRow_subtitle[^>]*>(.*?)</h4>",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
            val rows = rowRegex.findAll(html).mapNotNull { match ->
                val title = decode(match.groupValues[1]).trim()
                val artist = decode(match.groupValues[2]).trim()
                if (title.isBlank()) null else Row(title, artist)
            }.distinctBy { it.title.lowercase() + "\u0000" + it.artist.lowercase() }
                .toList()
            if (rows.isEmpty()) error("No tracks were found in that Spotify playlist")

            val playlist = MusicLibrary.createPlaylist(name)
            val tracks = rows.mapIndexed { index, row ->
                val match = runCatching {
                    DeezerApi.searchTracks("${row.title} ${row.artist}".trim(), 1)
                        .firstOrNull()
                }.getOrNull()
                match ?: MusicTrack(
                    id = "spotify:${id}:$index",
                    title = row.title,
                    artist = row.artist.ifBlank { "Spotify" },
                    album = "Spotify import",
                )
            }
            MusicLibrary.addTracksToPlaylist(playlist.id, tracks)
            Imported(name, tracks.size, rows.size)
        }

    /** Accepts an open.spotify.com URL, a spotify: URI or a bare playlist id. */
    fun playlistId(input: String): String? {
        val value = input.trim()
        if (value.isBlank()) return null
        val fromUri = runCatching {
            when {
                value.startsWith("spotify:playlist:") -> value.removePrefix("spotify:playlist:")
                value.contains("/playlist/") -> value.substringAfter("/playlist/")
                    .substringBefore('?').substringBefore('/')
                else -> null
            }
        }.getOrNull()
        val id = (fromUri ?: value).trim()
        return id.takeIf { it.matches(Regex("[A-Za-z0-9]{16,40}")) }
    }

    private fun decode(value: String): String =
        runCatching {
            Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrDefault(value)
}
