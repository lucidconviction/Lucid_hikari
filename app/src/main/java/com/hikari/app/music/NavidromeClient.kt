package com.hikari.app.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Navidrome / Subsonic music server support for Music → Servers.
 *
 * Albums are read with `getAlbumList2`, their tracks with `getAlbum`, and the
 * stream/cover URLs are the server's own `rest/stream` and `rest/getCoverArt`
 * endpoints carrying the usual salted Subsonic token.
 */
object NavidromeClient {

    data class Server(val url: String, val user: String, val pass: String)

    data class Album(
        val id: String,
        val name: String,
        val artist: String,
        val coverUrl: String,
        val songCount: Int,
    )

    data class Song(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationSeconds: Int,
        val coverUrl: String,
        val streamUrl: String,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun base(server: Server): String = server.url.trimEnd('/')

    private fun authQuery(server: Server): String {
        val salt = Random.nextInt(100000, 999999).toString()
        val token = md5(server.pass + salt)
        return "u=" + enc(server.user) +
            "&t=" + token +
            "&s=" + salt +
            "&v=1.16.1&c=LucidHikari&f=json"
    }

    fun streamUrl(server: Server, songId: String): String =
        base(server) + "/rest/stream?id=" + enc(songId) + "&" + authQuery(server)

    fun coverUrl(server: Server, coverId: String): String =
        if (coverId.isBlank()) "" else
            base(server) + "/rest/getCoverArt?id=" + enc(coverId) +
                "&size=400&" + authQuery(server)

    suspend fun albums(server: Server, size: Int = 100): List<Album> =
        withContext(Dispatchers.IO) {
            val url = base(server) + "/rest/getAlbumList2?type=newest&size=$size&" +
                authQuery(server)
            val root = get(url) ?: return@withContext emptyList()
            val arr = root.optJSONObject("albumList2")?.optJSONArray("album")
                ?: JSONArray()
            (0 until arr.length()).mapNotNull { index ->
                val album = arr.optJSONObject(index) ?: return@mapNotNull null
                val id = album.optString("id")
                if (id.isBlank()) return@mapNotNull null
                Album(
                    id = id,
                    name = album.optString("name").ifBlank { album.optString("title") }
                        .ifBlank { "Album" },
                    artist = album.optString("artist").ifBlank { "Navidrome" },
                    coverUrl = coverUrl(server, album.optString("coverArt")),
                    songCount = album.optInt("songCount", 0),
                )
            }
        }

    suspend fun albumSongs(server: Server, albumId: String): List<Song> =
        withContext(Dispatchers.IO) {
            val url = base(server) + "/rest/getAlbum?id=" + enc(albumId) + "&" +
                authQuery(server)
            val root = get(url) ?: return@withContext emptyList()
            val album = root.optJSONObject("album") ?: return@withContext emptyList()
            val albumName = album.optString("name").ifBlank { "Navidrome" }
            val arr = album.optJSONArray("song") ?: JSONArray()
            (0 until arr.length()).mapNotNull { index ->
                val song = arr.optJSONObject(index) ?: return@mapNotNull null
                val id = song.optString("id")
                if (id.isBlank()) return@mapNotNull null
                Song(
                    id = id,
                    title = song.optString("title").ifBlank { "Track" },
                    artist = song.optString("artist").ifBlank { album.optString("artist") }
                        .ifBlank { "Navidrome" },
                    album = song.optString("album").ifBlank { albumName },
                    durationSeconds = song.optInt("duration", 0),
                    coverUrl = coverUrl(server, song.optString("coverArt")),
                    streamUrl = streamUrl(server, id),
                )
            }
        }

    private fun get(url: String): JSONObject? = runCatching {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Navidrome ${response.code}")
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val sub = root.optJSONObject("subsonic-response") ?: root
            if (!sub.optString("status").equals("ok", true)) {
                error(sub.optJSONObject("error")?.optString("message") ?: "Navidrome error")
            }
            sub
        }
    }.getOrNull()

    private fun md5(value: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
