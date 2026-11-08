package com.hikari.app.music

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Qobuz lossless resolution through the same Eclipse service the reference
 * music page uses. A short-lived session is generated on first use; each
 * track is matched by title/artist/duration, then handed back as a direct
 * FLAC/Hi-Res URL. Any failure is silent: the caller falls back to YouTube.
 */
object QobuzResolver {

    private const val GENERATE = "https://qobuz-tidal-eclipse.cyrusna29.workers.dev/generate"
    private const val DEFAULT_ENDPOINT =
        "https://qobuz-tidal-eclipse.cyrusna29.workers.dev/u/4opn823jmxs6yee60au24sse15kp"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

    private data class QTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Int,
    )

    private val sessionMutex = Mutex()
    @Volatile private var endpoint: String = DEFAULT_ENDPOINT
    @Volatile private var sessionAt = 0L
    private val cache = java.util.concurrent.ConcurrentHashMap<String, MusicStream>()

    fun isProbablyAvailable(): Boolean = true

    /** Generate the Eclipse session while the user is browsing, not on Play. */
    suspend fun warmUp() = withContext(Dispatchers.IO) { ensureSession() }

    suspend fun resolve(track: MusicTrack): MusicStream? = withContext(Dispatchers.IO) {
        if (track.title.isBlank()) return@withContext null
        cache[track.id]?.let { return@withContext it }
        ensureSession()
        val base = endpoint
        if (base.isBlank()) return@withContext null

        val query = "${track.title} ${track.artist}".trim()
        var results = runCatching { search(base, query) }.getOrDefault(emptyList())
        if (results.isEmpty()) {
            val clean = cleanTitle(track.title)
            if (clean != track.title) {
                results = runCatching { search(base, "$clean ${track.artist}".trim()) }
                    .getOrDefault(emptyList())
            }
        }
        if (results.isEmpty()) return@withContext null
        val match = pickBest(track, results) ?: return@withContext null
        val stream = runCatching { streamUrl(base, match.id) }.getOrNull() ?: return@withContext null
        val result = MusicStream(
            url = stream.first,
            headers = mapOf("User-Agent" to UA),
            label = stream.third.ifBlank { "FLAC Hi-Res" },
            lossless = stream.second.equals("flac", true) || stream.third.contains("FLAC", true),
            isDash = false,
            isHls = false,
        )
        cache[track.id] = result
        result
    }

    /** Drops a cached URL (a signed stream can expire while the app sits open). */
    fun forget(trackId: String) {
        cache.remove(trackId)
    }

    private suspend fun ensureSession() {
        if (endpoint.isNotBlank() && System.currentTimeMillis() - sessionAt < 30 * 60 * 1000L) return
        sessionMutex.withLock {
            if (endpoint.isNotBlank() && System.currentTimeMillis() - sessionAt < 30 * 60 * 1000L) return
            val raw = runCatching {
                Http.postString(
                    GENERATE,
                    "{}",
                    mapOf(
                        "Content-Type" to "application/json",
                        "Origin" to "https://qobuz-tidal-eclipse.cyrusna29.workers.dev",
                        "Referer" to "https://qobuz-tidal-eclipse.cyrusna29.workers.dev/",
                        "User-Agent" to UA,
                        "Accept" to "*/*",
                    ),
                )
            }.getOrNull()
            val obj = runCatching { JSONObject(raw.orEmpty()) }.getOrNull() ?: return
            val manifest = obj.optString("manifestUrl")
            if (manifest.isNotBlank()) {
                endpoint = manifest.replace("/manifest.json", "")
                sessionAt = System.currentTimeMillis()
            } else {
                // Keep the known endpoint as the fallback; refresh next time.
                endpoint = DEFAULT_ENDPOINT
                sessionAt = System.currentTimeMillis() - 25 * 60 * 1000L
            }
        }
    }

    private fun search(base: String, query: String): List<QTrack> {
        val raw = Http.getStringQuiet(
            "$base/search?q=${URLEncoder.encode(query, "UTF-8")}",
            mapOf("Accept" to "application/json", "User-Agent" to UA),
        ) ?: return emptyList()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val arr = obj.optJSONArray("tracks") ?: return emptyList()
        val out = ArrayList<QTrack>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val artistObj = o.optJSONObject("artist") ?: JSONObject()
            val albumObj = o.optJSONObject("album") ?: JSONObject()
            out += QTrack(
                id = id,
                title = o.optString("title"),
                artist = artistObj.optString("name").ifBlank { o.optString("artist") },
                album = albumObj.optString("title"),
                duration = o.optInt("duration"),
            )
        }
        return out
    }

    /** @return (url, format, quality) or null. */
    private fun streamUrl(base: String, id: String): Triple<String, String, String>? {
        val raw = Http.getStringQuiet(
            "$base/stream/$id",
            mapOf("Accept" to "application/json", "User-Agent" to UA),
        ) ?: return null
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val url = obj.optString("url")
        if (url.isBlank()) return null
        return Triple(url, obj.optString("format"), obj.optString("quality"))
    }

    private fun pickBest(track: MusicTrack, candidates: List<QTrack>): QTrack? {
        if (candidates.isEmpty()) return null
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        var best: QTrack? = null
        var bestScore = -1
        for (c in candidates) {
            var score = 0
            val ct = normalize(c.title)
            val ca = normalize(c.artist)
            if (ct == title) score += 50
            else if (ct.contains(title) || title.contains(ct)) score += 35
            if (artist.isNotBlank()) {
                if (ca == artist) score += 40
                else if (ca.contains(artist) || artist.contains(ca)) score += 25
            } else score += 20
            if (track.durationSeconds > 0 && c.duration > 0) {
                val diff = abs(track.durationSeconds - c.duration)
                when {
                    diff <= 2 -> score += 20
                    diff <= 5 -> score += 10
                    diff <= 10 -> score += 5
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best ?: candidates.firstOrNull()
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    private fun cleanTitle(raw: String): String =
        raw.replace(Regex("\\(feat\\.[^)]+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(ft\\.[^)]+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[feat\\.[^]]+]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[ft\\.[^]]+]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Radio Edit\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Remastered[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Remastered[^]]*]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
}
