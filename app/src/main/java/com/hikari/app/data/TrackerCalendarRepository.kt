package com.hikari.app.data

import com.hikari.app.HikariApp
import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private const val DAY_MS = 24L * 60L * 60L * 1000L

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
        val animeIds = HashSet<Int>()

        fun posterOf(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let {
            "https://wsrv.nl/?url=https://simkl.in/posters/" + it + "_m.webp&q=90"
        }

        // Anime and film releases are small CDN files (250 KB / 1.2 MB) and
        // parse safely on a phone. The TV file is tens of megabytes — parsing
        // it into a JSON object was enough to take the app down — so TV airings
        // come from the per-day endpoint instead (about 22 KB a day).
        val files = listOf(
            Triple("anime", false, true),
            Triple("movie_release", true, false),
        )
        for ((file, isMovie, _) in files) {
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
                if (!isMovie) animeIds += simklId
                val at = parseDate(o.optString("date")) ?: continue
                if (at < now - DAY_MS || at > limit) continue
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
                out += entry(
                    at = at,
                    title = title,
                    subtitle = subtitle,
                    poster = posterOf(meta.optString("poster")),
                    tmdbId = meta.optJSONObject("ids")?.optString("tmdb").orEmpty(),
                    isSeries = !isMovie,
                    tracker = "Simkl",
                )
            }
        }

        // TV shows: one small response per day. Two Simkl quirks matter here:
        //
        //  * `date` must be DD-MM-YYYY (see [dayStringSimkl]); and
        //  * the returned `date` field carries the air TIME but today's calendar
        //    date, whatever day was asked for. Taking it at face value filed
        //    every episode under today — so the rail is built from the requested
        //    day and only the clock is read from the payload.
        if (shows.isNotEmpty()) {
            coroutineScope {
                val days = (0 until days.coerceIn(1, 21)).map { offset ->
                    async {
                        offset to runCatching {
                            Http.getStringQuiet(
                                "https://api.simkl.com/tv/airing?date=" + dayStringSimkl(offset) +
                                    "&client_id=${client.id}&app-name=Hikari&app-version=1.0",
                                auth,
                            )
                        }.getOrNull()
                    }
                }
                for ((offset, body) in days.awaitAll()) {
                    val arr = body?.let { runCatching { JSONArray(it) }.getOrNull() } ?: continue
                    val base = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, offset)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val simklId = o.optJSONObject("ids")?.optInt("simkl_id", 0) ?: 0
                        if (simklId <= 0 || simklId !in shows || simklId in animeIds) continue
                        val title = o.optString("title").trim()
                        if (title.isBlank()) continue
                        // "2026-11-18T22:30:00-05:00" -> "22:30"; blank when the
                        // catalogue has no air time on file.
                        val rawTime = o.optString("date")
                            .substringAfter('T', "")
                            .take(5)
                            .takeIf { it.length == 5 && it[2] == ':' && it[0].isDigit() }
                        val minutes = rawTime?.let {
                            (it.take(2).toIntOrNull() ?: 0) * 60 + (it.drop(3).toIntOrNull() ?: 0)
                        }
                        // Unknown time sits at noon so it cannot drift into the
                        // previous day; it displays with no clock at all.
                        val at = base.timeInMillis + (minutes ?: 720) * 60_000L
                        if (at < now - DAY_MS || at > limit) continue
                        val episode = o.optJSONObject("episode")
                        val season = episode?.optInt("season", 0) ?: 0
                        val number = episode?.optInt("episode", 0) ?: 0
                        out += entry(
                            at = at,
                            title = title,
                            subtitle = if (season > 0) "S${season}E$number" else "E$number",
                            poster = posterOf(o.optString("poster")),
                            tmdbId = "",
                            isSeries = true,
                            tracker = "Simkl",
                            timeOverride = rawTime?.let(::twelveHour).orEmpty(),
                        )
                    }
                }
            }
        }
        // Two sources can describe the same airing; keep one row per airing.
        return out.distinctBy { Triple(it.atMillis, it.title, it.subtitle) }
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
            // Without `extended=images` Trakt sends no artwork at all, and
            // `show.poster` is not a field it defines — hence the nested read
            // below. The calendar should look like the library, not a column
            // of blank tiles.
            val url = "https://api.trakt.tv/calendars/my/$type/$from/$days?extended=full,images"
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
                    poster = show?.optJSONObject("images")
                        ?.optJSONObject("poster")?.optString("full")
                        ?.takeIf { it.startsWith("http") }
                        ?: show?.optString("poster")?.takeIf { it.startsWith("http") },
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
        /** The service's own air time, when it must not be converted. */
        timeOverride: String? = null,
    ): Entry {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        val now = Calendar.getInstance()
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val day = when {
            sameDay(cal, now) -> "Today"
            sameDay(cal, tomorrow) -> "Tomorrow"
            else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(at))
        }
        // 12-hour everywhere, matching the rest of the app. The previous
        // "HH:mm" read as 19:30 — correct, but not what a phone user expects.
        val time = timeOverride
            ?: SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))
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

    /**
     * `/tv/airing` and `/anime/airing` document `DD-MM-YYYY`; a bogus date is
     * silently replaced with *today*, so an ISO string here made every day of
     * the window return today's single episode. Trakt keeps [dayString].
     */
    private fun dayStringSimkl(offsetDays: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return SimpleDateFormat("dd-MM-yyyy", Locale.US).format(cal.time)
    }

    /** "19:30" -> "7:30 PM", without any timezone conversion. */
    private fun twelveHour(hhmm: String): String {
        val h = hhmm.take(2).toIntOrNull() ?: return ""
        val m = hhmm.drop(3).take(2)
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return "$h12:$m $suffix"
    }

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
