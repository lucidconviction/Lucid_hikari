package com.hikari.app.data

import com.hikari.app.HikariApp
import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The user's own upcoming episodes and film releases, read from the trackers
 * they signed in to.
 *
 * This is the calendar the tracker services already keep for them — Simkl's
 * `/calendar/shows|movies`, Trakt's `/calendars/my/shows|movies` — not a local
 * guess from the watchlist. An entry is therefore "what airs on MY list", which
 * is exactly the question "what is coming up that I am watching".
 *
 * Both services are asked in parallel and merged by date; a service that is not
 * connected (or refuses its token) contributes nothing instead of failing the
 * whole calendar. Every field is parsed defensively: a calendar is a nicety and
 * must never be able to break the Trackers tab.
 */
object TrackerCalendarRepository {

    data class Entry(
        val atMillis: Long,
        /** "Today", "Tomorrow", "Fri 21 Nov". */
        val day: String,
        /** "20:00", or blank when the service gave no time. */
        val time: String,
        val title: String,
        /** "S2E5 · Episode title", or "Film". */
        val subtitle: String,
        val poster: String?,
        val item: MediaItem?,
        val tracker: String,
    )

    suspend fun load(app: HikariApp, days: Int = 14): List<Entry> = withContext(Dispatchers.IO) {
        val accounts = runCatching { app.store.trackers() }.getOrDefault(emptyList())
        val out = ArrayList<Entry>()
        accounts.firstOrNull { it.kind == TrackerKind.SIMKL }?.let { acc ->
            out += runCatching { simkl(app, acc, days) }.getOrDefault(emptyList())
        }
        accounts.firstOrNull { it.kind == TrackerKind.TRAKT }?.let { acc ->
            out += runCatching { trakt(app, acc, days) }.getOrDefault(emptyList())
        }
        out.sortedBy { it.atMillis }
    }

    // ---- Simkl -----------------------------------------------------------

    /**
     * Simkl's own calendar is the CDN-hosted public one
     * (`data.simkl.in/calendar/v2/{tv,anime,movie_release}.json`) joined with
     * the user's synced ids — a per-user `/calendar` API does not exist (it
     * answers 404), which is why the previous attempt showed nothing. The
     * public files cover yesterday + 33 days, so a 14-day window is just a
     * filter, and no user token is needed for them.
     */
    private suspend fun simkl(app: HikariApp, account: TrackerAccount, days: Int): List<Entry> {
        val client = app.store.trackerClients().firstOrNull { it.kind == TrackerKind.SIMKL }
            ?: TrackerClient(TrackerKind.SIMKL)
        if (!client.ready || account.token.isBlank()) return emptyList()
        val auth = mapOf(
            "simkl-api-key" to client.id,
            "Authorization" to "Bearer ${account.token}",
        )
        val body = runCatching {
            Http.get(
                "https://api.simkl.com/sync/all-items/?extended=full" +
                    "&client_id=${client.id}&app-name=Hikari&app-version=1.0",
                auth,
            ).use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull() ?: return emptyList()
        val sync = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()

        fun idsOf(arrayKey: String): Set<Int> {
            val arr = sync.optJSONArray(arrayKey) ?: return emptySet()
            val out = HashSet<Int>(arr.length())
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val media = row.optJSONObject("show") ?: row.optJSONObject("movie") ?: row
                val id = media.optJSONObject("ids")?.optInt("simkl", 0) ?: 0
                if (id > 0) out += id
            }
            return out
        }
        val shows = idsOf("shows") + idsOf("anime")
        val movies = idsOf("movies")

        val now = System.currentTimeMillis()
        val limit = now + days * 24L * 60L * 60L * 1000L
        val out = ArrayList<Entry>()
        val files = listOf(
            Triple("tv", "shows", false),
            Triple("anime", "anime", false),
            Triple("movie_release", "movies", true),
        )
        for ((file, _, isMovie) in files) {
            if (isMovie && movies.isEmpty()) continue
            if (!isMovie && shows.isEmpty()) continue
            val url = "https://data.simkl.in/calendar/v2/$file.json" +
                "?client_id=${client.id}&app-name=Hikari&app-version=1.0"
            val json = runCatching { Http.getStringQuiet(url) }.getOrNull() ?: continue
            val root = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val calendar = root.optJSONArray("calendar") ?: continue
            val metadata = root.optJSONObject("metadata")
            for (i in 0 until calendar.length()) {
                val o = calendar.optJSONObject(i) ?: continue
                val simklId = o.optInt("simkl_id", 0)
                if (simklId <= 0) continue
                if (if (isMovie) simklId !in movies else simklId !in shows) continue
                val at = parseDate(o.optString("date")) ?: continue
                if (at < now - 24L * 60L * 60L * 1000L || at > limit) continue
                val meta = metadata?.optJSONObject(simklId.toString()) ?: continue
                val title = meta.optString("title").trim()
                if (title.isBlank()) continue
                val episode = o.optJSONObject("episode")
                val season = episode?.optInt("season", -1) ?: -1
                val number = episode?.optInt("episode", 0) ?: 0
                val epTitle = episode?.optString("title").orEmpty().trim()
                val subtitle = when {
                    episode == null -> "Film"
                    season > 0 -> "S${season}E$number" + if (epTitle.isNotBlank()) " · $epTitle" else ""
                    else -> "E$number" + if (epTitle.isNotBlank()) " · $epTitle" else ""
                }
                val posterPath = meta.optString("poster").takeIf { it.isNotBlank() }
                val poster = posterPath?.let {
                    "https://wsrv.nl/?url=https://simkl.in/posters/" + it + "_m.webp&q=90"
                }
                val tmdb = meta.optJSONObject("ids")?.optString("tmdb").orEmpty()
                out += entry(
                    at = at,
                    title = title,
                    subtitle = subtitle,
                    poster = poster,
                    tmdbId = tmdb,
                    isSeries = !isMovie,
                    tracker = "Simkl",
                )
            }
        }
        return out
    }

    // ---- Trakt -----------------------------------------------------------

    private suspend fun trakt(app: HikariApp, account: TrackerAccount, days: Int): List<Entry> {
        val client = app.store.trackerClients().firstOrNull { it.kind == TrackerKind.TRAKT }
            ?: TrackerClient(TrackerKind.TRAKT)
        if (!client.ready || account.token.isBlank()) return emptyList()
        val headers = mapOf(
            "trakt-api-version" to "2",
            "trakt-api-key" to client.id,
            "Authorization" to "Bearer ${account.token}",
        )
        val from = dayString(0)
        val out = ArrayList<Entry>()
        for (type in listOf("shows", "movies")) {
            val url = "https://api.trakt.tv/calendars/my/$type/$from/$days"
            val body = runCatching {
                Http.get(url, headers).use { r ->
                    if (!r.isSuccessful) null else r.body?.string()
                }
            }.getOrNull() ?: continue
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val date = o.optString("first_aired").ifBlank { o.optString("released") }
                val at = parseDate(date) ?: continue
                val episode = o.optJSONObject("episode")
                val show = o.optJSONObject("show") ?: o.optJSONObject("movie")
                val title = show?.optString("title")?.trim().orEmpty()
                if (title.isBlank()) continue
                val season = episode?.optInt("season", 0) ?: 0
                val number = episode?.optInt("number", 0) ?: 0
                val epTitle = episode?.optString("title").orEmpty().trim()
                val subtitle = if (episode != null) {
                    "S${season}E$number" + if (epTitle.isNotBlank()) " · $epTitle" else ""
                } else {
                    "Film"
                }
                out += entry(
                    at = at,
                    title = title,
                    subtitle = subtitle,
                    poster = show?.optString("poster")?.takeIf { it.startsWith("http") },
                    tmdbId = show?.optJSONObject("ids")?.optString("tmdb").orEmpty(),
                    isSeries = episode != null,
                    tracker = "Trakt",
                )
            }
        }
        return out
    }

    // ---- shared ----------------------------------------------------------

    private fun entry(
        at: Long,
        title: String,
        subtitle: String,
        poster: String?,
        tmdbId: String,
        isSeries: Boolean,
        tracker: String,
    ): Entry {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        val now = Calendar.getInstance()
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val day = when {
            sameDay(cal, now) -> "Today"
            sameDay(cal, tomorrow) -> "Tomorrow"
            else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(at))
        }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
        val item = if (tmdbId.isNotBlank() && tmdbId != "null") {
            MediaItem(
                providerId = "tmdb",
                id = tmdbId,
                title = title,
                type = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
                posterUrl = poster,
                rawType = "tmdb",
            )
        } else {
            null
        }
        return Entry(at, day, time, title, subtitle, poster, item, tracker)
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun dayString(offsetDays: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    /** ISO-8601 from Simkl/Trakt, with and without milliseconds/Z. */
    private fun parseDate(raw: String): Long? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.US).apply {
                    timeZone = if (p.endsWith("'Z'")) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
                }.parse(text)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }
}
