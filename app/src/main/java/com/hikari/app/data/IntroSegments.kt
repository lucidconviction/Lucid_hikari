package com.hikari.app.data

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Intro, recap, preview and credits timings from TheIntroDB
 * (https://theintrodb.org), a free community database. No key, no account.
 *
 * This is what turns the player's "Skip intro" pill from a blind +85 seconds
 * into the actual segment: the same request also feeds the marks above the seek
 * bar. Only titles whose provider is TMDB can be asked (the endpoint is keyed by
 * TMDB id); everything else keeps the old skip behaviour.
 *
 * Failures are an empty list, never an exception, and every answer — including
 * "this title isn't in the database" — is cached for the session, so opening a
 * film twice costs one request.
 */
object IntroSegments {

    enum class Kind(val label: String, val color: Int) {
        INTRO("Skip intro", 0xFFFFC857.toInt()),
        RECAP("Skip recap", 0xFF7C5CFF.toInt()),
        PREVIEW("Skip preview", 0xFF00D2EF.toInt()),
        CREDITS("Skip credits", 0xFF8A8FA3.toInt()),
    }

    data class Segment(
        val kind: Kind,
        val startMs: Long,
        /** Null when the segment runs to the end of the video (credits often do). */
        val endMs: Long?,
    )

    private data class Cached(val at: Long, val list: List<Segment>)

    private const val TTL_MS = 30 * 60 * 1000L
    private const val TIMEOUT_MS = 8_000L

    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun fetch(
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        force: Boolean = false,
    ): List<Segment> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext emptyList()
        val key = "$tmdbId|${season ?: 0}|${episode ?: 0}"
        val now = System.currentTimeMillis()
        cache[key]?.let { if (!force && now - it.at < TTL_MS) return@withContext it.list }

        val url = buildString {
            append("https://api.theintrodb.org/v4/media?tmdb_id=")
            append(tmdbId)
            if (season != null && episode != null) {
                append("&season=").append(season)
                append("&episode=").append(episode)
            }
        }
        val body = withTimeoutOrNull(TIMEOUT_MS) {
            Http.getStringQuiet(
                url,
                mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to "LucidHikari",
                ),
            )
        }.orEmpty()
        val parsed = parse(body)
        cache[key] = Cached(now, parsed)
        parsed
    }

    private fun parse(body: String): List<Segment> {
        if (body.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val out = ArrayList<Segment>()
            for (kind in Kind.entries) {
                val array = root.optJSONArray(kind.name.lowercase()) ?: continue
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val start = entry.optLong("start_ms", -1L)
                    if (start < 0L) continue
                    val end = entry.optLong("end_ms", -1L).takeIf { it > start }
                    out += Segment(kind, start, end)
                }
            }
            out.sortedBy { it.startMs }
        }.getOrElse { emptyList() }
    }
}
