package com.hikari.app.music

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Synced lyrics from lrclib.net, the same keyless source the reference app uses. */
object MusicLyricsService {

    private const val UA = "Hikari/1.0 (https://github.com/codegeasse1)"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, MusicLyrics>()

    suspend fun forTrack(track: MusicTrack): MusicLyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }
        val result = fetchExact(track) ?: fetchSearch(track) ?: MusicLyrics.EMPTY
        if (!result.isEmpty) cache[track.id] = result
        result
    }

    private fun fetchExact(track: MusicTrack): MusicLyrics? {
        val query = buildString {
            append("artist_name=").append(enc(track.artist))
            append("&track_name=").append(enc(track.title))
            if (track.album.isNotBlank() && track.album != "Single") {
                append("&album_name=").append(enc(track.album))
            }
            if (track.durationSeconds > 0) append("&duration=").append(track.durationSeconds)
        }
        val raw = Http.getStringQuiet(
            "https://lrclib.net/api/get?$query",
            mapOf("Accept" to "application/json", "User-Agent" to UA),
        ) ?: return null
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return parse(track, obj)
    }

    private fun fetchSearch(track: MusicTrack): MusicLyrics? {
        val q = enc("${track.artist} ${track.title}")
        val raw = Http.getStringQuiet(
            "https://lrclib.net/api/search?q=$q",
            mapOf("Accept" to "application/json", "User-Agent" to UA),
        ) ?: return null
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val parsed = parse(track, obj)
            if (!parsed.isEmpty) return parsed
        }
        return null
    }

    private fun parse(track: MusicTrack, obj: JSONObject): MusicLyrics {
        val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() }
        if (synced != null) {
            return MusicLyrics(
                trackId = track.id,
                synced = true,
                plain = plain ?: synced,
                lines = parseLrc(synced),
            )
        }
        if (plain != null) {
            return MusicLyrics(trackId = track.id, synced = false, plain = plain)
        }
        return MusicLyrics.EMPTY
    }

    private fun parseLrc(raw: String): List<LyricsLine> {
        val out = ArrayList<LyricsLine>()
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        for (line in raw.lines()) {
            val match = regex.find(line) ?: continue
            val minutes = match.groupValues[1].toLongOrNull() ?: continue
            val seconds = match.groupValues[2].toLongOrNull() ?: continue
            val fraction = match.groupValues[3]
            val millis = when (fraction.length) {
                1 -> fraction.toLongOrNull()?.times(100) ?: 0
                2 -> fraction.toLongOrNull()?.times(10) ?: 0
                3 -> fraction.toLongOrNull() ?: 0
                else -> 0
            }
            val text = line.substring(match.range.last + 1).trim()
            if (text.isNotBlank()) {
                out += LyricsLine((minutes * 60 + seconds) * 1000 + millis, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
