package com.hikari.app.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.concurrent.Volatile

/**
 * Fetches sports data from free public feeds:
 * - ESPN (scores, schedules, teams, standings, summaries)
 * - ESPN's own feeds for cricket, HockeyTech and Minor League Baseball
 * - sporting-events.org (fixtures for guide-only sports)
 * - BKFC, TheSportsDB (wrestling)
 * - the nuvio-data files (football leagues, racing and the other sports
 *   TheSportsDB covers)
 *
 * The nuvio-data files are built by a daily GitHub job from TheSportsDB, so
 * the app never asks TheSportsDB directly and never needs an API key.
 */
object SportsFeeds {

    private const val DATA_URL = "https://raw.githubusercontent.com/homelessbrian/nuvio-data/main/"
    private const val WRESTLING_URL = DATA_URL + "wrestling.json"

    private suspend fun get(url: String): String = SportsPlatform.get(url)

    /** Raw scoreboard for a date range (yyyyMMdd). */
    suspend fun scoreboardRaw(league: League, fromDay: String, toDay: String): String {
        val path = league.espnPath
        // Cricket, the AHL/ECHL/junior hockey leagues and MiLB have no ESPN
        // scoreboard: their own feeds are shaped into the same event JSON.
        if (path != null && OtherFeeds.handles(path)) {
            return OtherFeeds.scoreboard(path, fromDay, toDay) { get(it) }
        }
        val query = if (!league.espnQuery.isNullOrBlank()) "&${league.espnQuery}" else ""
        val dates = if (fromDay == toDay) fromDay else "$fromDay-$toDay"
        return get("https://site.api.espn.com/apis/site/v2/sports/${league.espnPath}/scoreboard?dates=$dates&limit=300$query")
    }

    /** Scoreboard for a single day. */
    suspend fun scoreboardDayRaw(league: League, day: String): String =
        scoreboardRaw(league, day, day)

    /** Default scoreboard (today + next 2 days). */
    suspend fun scoreboardDefaultRaw(league: League): String {
        val path = league.espnPath
        if (path != null && OtherFeeds.handles(path)) return OtherFeeds.scoreboardNow(path) { get(it) }
        return get("https://site.api.espn.com/apis/site/v2/sports/${league.espnPath}/scoreboard?limit=300${league.espnQuery}")
    }

    /** Teams for a league. */
    suspend fun teamsRaw(league: League): String {
        val path = league.espnPath ?: throw IllegalStateException("no team list for this league")
        if (OtherFeeds.handles(path)) return OtherFeeds.teams(path) { get(it) }
        return get("https://site.api.espn.com/apis/site/v2/sports/${league.espnPath}/teams?limit=1000")
    }

    /** Team schedule (fixtures=true for future, false for past). */
    suspend fun teamScheduleRaw(leaguePath: String, teamId: String, fixtures: Boolean): String {
        if (OtherFeeds.handles(leaguePath)) {
            return if (fixtures) "{}" else OtherFeeds.teamSchedule(leaguePath, teamId) { get(it) }
        }
        return get("https://site.api.espn.com/apis/site/v2/sports/$leaguePath/teams/$teamId/schedule?${if (fixtures) "season=2026" else "season=2025"}")
    }

    // ---- Parsing helpers (from org.json to our models) ----

    /** Scoring plays for a game. */
    suspend fun summary(league: League, espnId: String): List<ScoringPlay> = parseSummary(summaryJson(league, espnId))

    suspend fun summaryJson(league: League, espnId: String): JSONObject {
        val path = league.espnPath ?: throw IllegalStateException("no summary for this league")
        // HockeyTech/MiLB game pages need a different request shape; the game
        // page itself is a later addition, so treat them as "no summary yet"
        // rather than asking ESPN for a league it does not carry.
        if (OtherFeeds.handles(path) && path != "cricket/panel" && !path.startsWith("cricket/")) {
            throw IllegalStateException("no summary for this league")
        }
        return JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/$path/summary?event=$espnId"))
    }

    /** Standings table as raw JSON text (returns null when the league doesn't have one). */
    suspend fun standingsRaw(url: String): String? = runCatching { get(url) }.getOrNull()

    private fun parseSummary(o: JSONObject): List<ScoringPlay> {
        val plays = mutableListOf<ScoringPlay>()

        fun periodLabel(n: Int): String = when (n) {
            1 -> "Q1"; 2 -> "Q2"; 3 -> "Q3"; 4 -> "Q4"; 5 -> "OT"
            else -> if (n > 0) "P$n" else ""
        }

        // Most ESPN sports put the scoring in one flat list.
        o.optJSONArray("scoringPlays")?.let { a ->
            (0 until a.length()).forEach { i ->
                val p = a.optJSONObject(i) ?: return@forEach
                val period = p.optJSONObject("period")?.optInt("number", 0) ?: 0
                val clock = p.optJSONObject("clock")?.optString("displayValue").orEmpty()
                val type = p.optJSONObject("scoringType")?.optString("abbreviation").orEmpty()
                    .ifBlank { p.optJSONObject("type")?.optString("abbreviation").orEmpty() }
                val team = p.optJSONObject("team")?.let {
                    it.optString("abbreviation").ifBlank { it.optString("displayName") }
                }
                plays += ScoringPlay(
                    clock = listOfNotNull(periodLabel(period).takeIf { it.isNotBlank() }, clock.takeIf { it.isNotBlank() }).joinToString(" "),
                    teamAbbr = team?.takeIf { it.isNotBlank() },
                    text = listOfNotNull(type.takeIf { it.isNotBlank() }, p.optString("text").takeIf { it.isNotBlank() }).joinToString(" · "),
                    awayScore = p.opt("awayScore")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                    homeScore = p.opt("homeScore")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }

        // Hockey/baseball/basketball: every play, with the scoring ones flagged.
        if (plays.isEmpty()) o.optJSONArray("plays")?.let { a ->
            (0 until a.length()).forEach { i ->
                val p = a.optJSONObject(i) ?: return@forEach
                val scoring = p.optBoolean("scoringPlay", false)
                if (!scoring) return@forEach
                val clock = p.optJSONObject("clock")?.optString("displayValue").orEmpty()
                val period = p.optJSONObject("period")?.optInt("number", 0) ?: 0
                plays += ScoringPlay(
                    clock = listOfNotNull(periodLabel(period).takeIf { it.isNotBlank() }, clock.takeIf { it.isNotBlank() }).joinToString(" "),
                    teamAbbr = p.optJSONObject("team")?.optString("abbreviation")?.takeIf { it.isNotBlank() },
                    text = p.optString("text"),
                    awayScore = p.opt("awayScore")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                    homeScore = p.opt("homeScore")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }

        // Last resort: the periods' own play lists.
        if (plays.isEmpty()) {
            val periods = o.optJSONArray("periods") ?: return plays
            (0 until periods.length()).forEach { i ->
                val period = periods.optJSONObject(i) ?: return@forEach
                val periodPlays = period.optJSONArray("plays") ?: return@forEach
                (0 until periodPlays.length()).forEach { j ->
                    val play = periodPlays.optJSONObject(j) ?: return@forEach
                    plays += ScoringPlay(
                        clock = play.optString("clock").takeIf { it.isNotBlank() } ?: play.optString("time"),
                        teamAbbr = play.optString("teamAbbreviation").takeIf { it.isNotBlank() },
                        text = play.optString("text"),
                        awayScore = play.optString("awayScore").takeIf { it.isNotBlank() },
                        homeScore = play.optString("homeScore").takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        return plays
    }

    /** sporting-events.org fixtures. */
    suspend fun fixturesRaw(slug: String): String = get("https://sporting-events.org/data/$slug.json")

    /** BKFC events. */
    suspend fun bkfcEventsRaw(): String = get("https://api.bkfc.com/events")

    /** TheSportsDB wrestling events (the nuvio-data copy, no API key). */
    suspend fun wrestlingRaw(): String = get(WRESTLING_URL)

    /**
     * Every sport from TheSportsDB (the nuvio-data job, from its leagues.txt):
     * the leagues, their teams and the extra competitions the built-in catalog
     * does not carry (non-league football, rugby league, racing, netball...).
     * The sportsdb folder; the older football folder while that's all there is.
     */
    suspend fun footballIndexRaw(): String =
        runCatching { get(DATA_URL + "sportsdb/index.json").also { tsdbFolder = "sportsdb/" } }.getOrElse {
            get(DATA_URL + "football/index.json").also { tsdbFolder = "football/" }
        }

    /** One league's games or events (a few weeks back to the end of the season). */
    suspend fun footballLeagueRaw(tsdbId: String): String {
        val first = tsdbFolder
        return runCatching { get(DATA_URL + first + "$tsdbId.json") }.getOrElse { e ->
            val other = if (first == "sportsdb/") "football/" else "sportsdb/"
            runCatching { get(DATA_URL + other + "$tsdbId.json").also { tsdbFolder = other } }
                .getOrElse { throw e }
        }
    }

    @Volatile private var tsdbFolder = "sportsdb/"

    /** TheSportsDB game details. */
    suspend fun tsdbGameRaw(tsdbEventId: String): String =
        get(DATA_URL + "sportsdb/games/$tsdbEventId.json")

    /** ESPN shared cache (pre-parsed team/venue data). */
    suspend fun espnSharedRaw(leagueId: String): String? =
        runCatching { get(DATA_URL + "espn/" + safeFileId(leagueId) + ".json") }.getOrNull()

    private fun safeFileId(s: String): String = s.replace('/', '_').replace('.', '_')

    // ---- TheSportsDB index / league files ----

    /** A league from the nuvio-data files, with its teams (for Pick Your Teams). */
    class TsdbLeague(
        val league: League,
        val teams: List<TeamInfo> = emptyList(),
        val tsdbId: String = league.id.removePrefix("tsdb/"),
        val alias: Boolean = false,
    )

    /** Club colours aren't in the data: a steady colour per club, from its name. */
    private val CLUB_COLORS = longArrayOf(
        0xFFB71C1C, 0xFF0D47A1, 0xFF1B5E20, 0xFF4A148C, 0xFFE65100, 0xFF006064,
        0xFF880E4F, 0xFF263238, 0xFF33691E, 0xFF1A237E, 0xFFBF360C, 0xFF3E2723,
    )

    private fun clubColor(name: String): Long =
        CLUB_COLORS[Math.floorMod(name.lowercase(Locale.ROOT).hashCode(), CLUB_COLORS.size)]

    private fun clubAbbr(name: String): String {
        val words = name.split(' ', '-').filter {
            it.isNotBlank() && it.lowercase(Locale.ROOT) !in
                setOf("fc", "afc", "town", "city", "united", "the", "and", "&")
        }
        return when {
            words.isEmpty() -> name.take(3).uppercase(Locale.ROOT)
            words.size == 1 -> words[0].take(3).uppercase(Locale.ROOT)
            else -> words.take(3).joinToString("") { it.take(1) }.uppercase(Locale.ROOT)
                .padEnd(3, words[0].getOrElse(1) { 'X' }.uppercaseChar())
        }
    }

    fun parseFootballIndex(raw: String): List<TsdbLeague> {
        val arr = JSONObject(raw).optJSONArray("leagues") ?: return emptyList()
        val out = ArrayList<TsdbLeague>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tid = o.str("id") ?: continue
            val name = o.str("name") ?: continue
            // One that fills one of the app's own leagues ("app": "guide/wwe"):
            // that league already has a richer schedule feed, so do not add a
            // second tsdb copy of it.
            val own = o.str("app")?.let { app -> SportsCatalog.leagues.firstOrNull { l -> l.id == app } }
            if (own != null) {
                out += TsdbLeague(own, emptyList(), tid, alias = true)
                continue
            }
            // Any sport (rugby league, cricket, racing...): the sport, where
            // it's watched, how guides name it and how long a game runs.
            val sport = o.str("sport")?.let { s -> Sport.entries.firstOrNull { it.name == s } ?: Sport.OTHER }
                ?: Sport.FOOTBALL
            val words = o.optJSONArray("words")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } }
            }.orEmpty()
            val regions = o.optJSONArray("regions")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } }.toSet()
            }.orEmpty()
            val league = League(
                id = "tsdb/$tid",
                sport = sport,
                name = name,
                short = o.str("short") ?: name.take(6).uppercase(Locale.ROOT),
                kind = if (o.str("kind") == "event") LeagueKind.EVENT else LeagueKind.TEAMS,
                guideWords = (listOf(name) + words).distinct(),
                // The index leaves regions empty for leagues it expects every
                // region to see (the English non-league divisions, MotoGP...).
                // Empty used to mean "never suggested"; ALL_REGIONS keeps them
                // in All Events, which is where Nuvio shows them too.
                regions = regions.ifEmpty { League.ALL_REGIONS },
                minutes = o.optInt("minutes", 115).takeIf { it > 0 } ?: 115,
                color = 0xFF2E3B4E,
            )
            val teams = o.optJSONArray("teams")?.let { ta ->
                (0 until ta.length()).mapNotNull { k ->
                    val t = ta.optJSONObject(k) ?: return@mapNotNull null
                    val id = t.str("id") ?: return@mapNotNull null
                    val n = t.str("name") ?: return@mapNotNull null
                    TeamInfo(
                        leagueId = league.id,
                        id = id,
                        name = n,
                        fullName = n,
                        abbr = clubAbbr(n),
                        location = null,
                        color = clubColor(n),
                        altColor = null,
                        logo = t.str("badge")?.takeIf { it.startsWith("https://") },
                    )
                }
            }.orEmpty()
            out += TsdbLeague(league, teams)
        }
        return out
    }

    /** A league's games or events (races, fight cards, wrestling shows). */
    fun parseFootballLeague(
        league: League,
        raw: String,
        now: Long = System.currentTimeMillis(),
        fromMs: Long = Long.MIN_VALUE,
        toMs: Long = Long.MAX_VALUE,
    ): List<SportsEvent> {
        val arr = JSONObject(raw).optJSONArray("events") ?: return emptyList()
        val out = ArrayList<SportsEvent>()
        val durationMs = league.minutes * 60_000L
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val id = e.str("id") ?: continue
            val hasTime = e.optBoolean("hasTime", true)
            val start = if (hasTime) {
                SportsDates.parseIso(e.str("start")) ?: continue
            } else {
                SportsDates.localDateOf(e.str("start")) ?: continue
            }
            // A season file is mostly games months away from the sports page's
            // window. Building an event object (and its team colours) for every
            // one of them was most of the refresh cost of the non-league
            // divisions; skip them before allocating anything.
            if (start > toMs || start + durationMs < fromMs) continue
            // An event with a name instead of two teams (a race, a fight card,
            // a wrestling show).
            if (e.str("home") == null) {
                val title = e.str("name") ?: continue
                val end = start + league.minutes * 60_000L
                out += SportsEvent(
                    id = "tsdb:${league.id}:$id",
                    leagueId = league.id,
                    startMs = start,
                    hasTime = hasTime,
                    state = when {
                        !hasTime -> GameState.PRE
                        now >= end -> GameState.POST
                        now >= start -> GameState.LIVE
                        else -> GameState.PRE
                    },
                    title = title,
                    subtitle = listOfNotNull(e.str("venue"), e.str("city"), e.str("country"))
                        .joinToString(", ").ifBlank { null },
                    status = null,
                    away = null,
                    home = null,
                    source = EventSource.FIXTURES,
                    endMs = end,
                    note = if (!hasTime) "Time to be confirmed"
                    else listOfNotNull(e.str("venue"), e.str("city")).joinToString(" · ").ifBlank { null },
                    about = e.str("about"),
                    // A race's finishing order (or a fight card's results).
                    standings = e.optJSONArray("results")?.let { r ->
                        (0 until r.length()).mapNotNull { r.optJSONObject(it) }.mapNotNull { x ->
                            val n = x.str("name") ?: return@mapNotNull null
                            Standing(x.optInt("pos", 0).takeIf { it > 0 }?.toString() ?: "", n, x.str("detail"))
                        }
                    }.orEmpty(),
                )
                continue
            }
            val state = when (e.str("state")) {
                "in" -> GameState.LIVE
                "post" -> GameState.POST
                "off" -> GameState.OFF
                else -> GameState.PRE
            }
            fun side(nameKey: String, idKey: String, scoreKey: String, home: Boolean): Side? {
                val n = e.str(nameKey) ?: return null
                val tid = e.str(idKey) ?: n
                val team = TeamInfo(
                    leagueId = league.id,
                    id = tid,
                    name = n,
                    fullName = n,
                    abbr = clubAbbr(n),
                    location = null,
                    color = clubColor(n),
                    altColor = null,
                    logo = null,
                )
                val score = if (state == GameState.LIVE || state == GameState.POST) {
                    e.opt(scoreKey)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                } else {
                    null
                }
                return Side(team = team, score = score, record = null, winner = false, home = home)
            }
            val h = side("home", "homeId", "hs", true) ?: continue
            val a = side("away", "awayId", "as", false) ?: continue
            val hs = h.score?.toIntOrNull()
            val asScore = a.score?.toIntOrNull()
            val done = state == GameState.POST && hs != null && asScore != null
            val round = e.optInt("round", 0).takeIf { it > 0 }
            out += SportsEvent(
                id = "tsdb:${league.id}:$id",
                leagueId = league.id,
                startMs = start,
                hasTime = hasTime,
                state = state,
                title = "${h.team.name} v ${a.team.name}",
                subtitle = listOfNotNull(round?.let { "Round $it" }, e.str("venue"))
                    .joinToString(" · ").ifBlank { null },
                status = when (state) {
                    GameState.LIVE -> e.str("status") ?: "Live"
                    GameState.POST -> "FT"
                    GameState.OFF -> e.str("status") ?: "Postponed"
                    GameState.PRE -> null
                },
                away = if (done) a.copy(winner = asScore > hs) else a,
                home = if (done) h.copy(winner = hs > asScore) else h,
                source = EventSource.FIXTURES,
                endMs = start + league.minutes * 60_000L,
                note = if (!hasTime) "Time to be confirmed" else null,
            )
        }
        return out
    }

    // ---- Caching layer ----

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry<Any>>()

    private data class CacheEntry<T>(val value: T, val expiresAt: Long)

    private suspend fun <T : Any> keep(key: String, maxAgeMs: Long, load: suspend () -> T): T = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cache[key]?.let { if (it.expiresAt > now) @Suppress("UNCHECKED_CAST") return@withContext it.value as T }
        val fresh = load()
        cache[key] = CacheEntry(fresh, now + maxAgeMs)
        fresh
    }

    // ---- Public cached API ----

    /** Scoreboard with caching. */
    suspend fun scoreboard(path: String, from: String, to: String, get: suspend (String) -> String): String =
        keep("sb:$path:$from:$to", 2 * 60 * 1000) {
            if (OtherFeeds.handles(path)) OtherFeeds.scoreboard(path, from, to) { get(it) }
            else get("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?dates=$from-$to&limit=300")
        }

    /** Scoreboard now (today + next 2 days) with caching. */
    suspend fun scoreboardNow(path: String, get: suspend (String) -> String): String =
        keep("sbnow:$path", 3 * 60 * 1000) {
            if (OtherFeeds.handles(path)) OtherFeeds.scoreboardNow(path) { get(it) }
            else get("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?limit=300")
        }

    /** Teams with caching. */
    suspend fun teams(path: String, get: suspend (String) -> String): String =
        keep("teams:$path", 60 * 60 * 1000) {
            if (OtherFeeds.handles(path)) OtherFeeds.teams(path) { get(it) }
            else get("https://site.api.espn.com/apis/site/v2/sports/$path/teams?limit=1000")
        }

    /** Team schedule with caching. */
    suspend fun teamSchedule(path: String, teamId: String, get: suspend (String) -> String): String =
        keep("sched:$path:$teamId", 15 * 60 * 1000) {
            if (OtherFeeds.handles(path)) OtherFeeds.teamSchedule(path, teamId) { get(it) }
            else get("https://site.api.espn.com/apis/site/v2/sports/$path/teams/$teamId/schedule?season=2026")
        }

    // ---- Summaries / Standings (ESPN-shaped game pages) ----

    suspend fun summary(path: String, eventId: String, get: suspend (String) -> String): JSONObject = when {
        path.startsWith("football/") -> JSONObject(get("https://cdn.espn.com/core/football/summary?xhr=1&gameId=$eventId"))
        path.startsWith("basketball/") -> JSONObject(get("https://cdn.espn.com/core/basketball/summary?xhr=1&gameId=$eventId"))
        path.startsWith("baseball/") -> JSONObject(get("https://cdn.espn.com/core/baseball/summary?xhr=1&gameId=$eventId"))
        path.startsWith("hockey/") -> JSONObject(get("https://cdn.espn.com/core/hockey/summary?xhr=1&gameId=$eventId"))
        else -> JSONObject(get("https://cdn.espn.com/core/${path.split('/')[1]}/summary?xhr=1&gameId=$eventId"))
    }

    suspend fun standings(path: String, get: suspend (String) -> String): String? = when {
        path.startsWith("football/") -> runCatching { get("https://site.api.espn.com/apis/site/v2/sports/$path/standings") }.getOrNull()
        path.startsWith("basketball/") -> runCatching { get("https://site.api.espn.com/apis/site/v2/sports/$path/standings") }.getOrNull()
        path.startsWith("baseball/") -> runCatching { get("https://site.api.espn.com/apis/site/v2/sports/$path/standings") }.getOrNull()
        path.startsWith("hockey/") -> runCatching { get("https://site.api.espn.com/apis/site/v2/sports/$path/standings") }.getOrNull()
        else -> null
    }
}

/** `JSONObject` convenience: the first of [names] with a real value. */
private fun JSONObject.str(vararg names: String): String? {
    for (n in names) {
        val v = optString(n, "").trim()
        if (v.isNotEmpty() && v != "null") return v
    }
    return null
}

/** One scoring play in a game summary. */
data class ScoringPlay(
    val clock: String,
    val teamAbbr: String?,
    val text: String,
    val awayScore: String?,
    val homeScore: String?
)

/**
 * The feeds ESPN does not carry: cricket (ESPN's cricket API), HockeyTech (the
 * AHL, ECHL, PWHL and the Canadian junior leagues) andMinor League Baseball.
 *
 * Each is turned into the same event JSON ESPN's scoreboard returns, so the
 * existing parser, layouts and game cards work unchanged.
 */
private object OtherFeeds {

    private const val ESPN = "https://site.api.espn.com/apis/site/v2/sports/"
    private const val CRICKET_PANEL = "https://site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates="
    private const val HOCKEYTECH = "https://lscluster.hockeytech.com/feed/index.php?feed=modulekit&fmt=json&lang=en"
    private const val MLB = "https://statsapi.mlb.com/api/v1/"
    private val CRICKET_FORMATS = setOf("Test", "ODI", "T20I")
    private const val DAY = 86_400_000L

    /** HockeyTech's league codes and the public keys their own sites use. */
    private val HOCKEYTECH_KEYS = mapOf(
        "ahl" to "50c2cd9b5e18e390", "echl" to "2c2b89ea7345cae8", "pwhl" to "446521baf8c38984",
        "ohl" to "f1aa699db3d81487", "whl" to "f1aa699db3d81487", "lhjmq" to "f1aa699db3d81487",
        "ushl" to "e828f89b243dc43f",
    )

    /** These feeds have no team colours: a steady one per team, from its name. */
    private val COLOURS = listOf(
        "B71C1C", "0D47A1", "1B5E20", "4A148C", "E65100", "006064",
        "880E4F", "263238", "33691E", "1A237E", "BF360C", "3E2723",
    )

    private fun colourOf(name: String) =
        COLOURS[Math.floorMod(name.lowercase(Locale.ROOT).hashCode(), COLOURS.size)]

    fun handles(path: String?): Boolean = path != null &&
        (path.startsWith("cricket/") || path.startsWith("hockeytech/") || path.startsWith("milb/"))

    /** Whether a game page can be had (international cricket has none). */
    fun espnExtras(path: String?): Boolean = path != null && path != "cricket/panel"

    // ------------------------------------------------------------------ days

    /** "20261026" (a day) or "202610" (a month) -> first and last day. */
    private fun span(from: String, to: String): Pair<String, String> =
        (if (from.length == 6) from + "01" else from) to (if (to.length == 6) to + "31" else to)

    private fun isoMs(s: String?): Long? = SportsDates.parseIso(s)

    private fun inSpan(ms: Long?, span: Pair<String, String>): Boolean {
        val d = SportsDates.dayKey(ms ?: return false)
        return d >= span.first && d <= span.second
    }

    private fun eventsJson(list: List<JSONObject>): String =
        JSONObject().put("events", JSONArray(list)).toString()

    /** The scoreboard for days [from]..[to], in ESPN's shape. */
    suspend fun scoreboard(
        path: String,
        from: String,
        to: String,
        get: suspend (String) -> String,
    ): String {
        val span = span(from, to)
        return when {
            path == "cricket/panel" -> eventsJson(cricketPanel(span, get))
            path.startsWith("cricket/") -> eventsJson(
                cricketSeason(path.removePrefix("cricket/"), span, get)
                    .filter { inSpan(isoMs(it.optString("date")), span) },
            )
            path.startsWith("hockeytech/") -> eventsJson(hockeyTech(path.removePrefix("hockeytech/"), span, get))
            else -> eventsJson(milb(path.removePrefix("milb/"), span, get))
        }
    }

    /** Today and the days either side (the feed's own pick). */
    suspend fun scoreboardNow(path: String, get: suspend (String) -> String): String {
        val now = SportsDates.dayOf(LiveTvClock.nowMs())
        return scoreboard(path, SportsDates.dayKey(now - DAY), SportsDates.dayKey(now + DAY), get)
    }

    /** A league's teams, in the shape of ESPN's teams list. */
    suspend fun teams(path: String, get: suspend (String) -> String): String {
        val teams: List<JSONObject> = when {
            // International cricket: the countries playing in the last and
            // next fortnight.
            path == "cricket/panel" -> panelDays(-14, 14, get)
                .flatMap { ev -> competitorsOf(ev).mapNotNull { it.optJSONObject("team") } }
                .distinctBy { it.optString("id") }
            path.startsWith("cricket/") -> {
                val now = LiveTvClock.nowMs()
                val year = SportsDates.dayKey(now).take(4).toInt()
                val events = cricketSeason(path.removePrefix("cricket/"), "${year - 1}0101" to "${year}1231", get)
                events.flatMap { ev -> competitorsOf(ev).mapNotNull { it.optJSONObject("team") } }
                    .distinctBy { it.optString("id") }
            }
            path.startsWith("hockeytech/") -> hockeyTechTeams(path.removePrefix("hockeytech/"), get)
            else -> milbTeams(path.removePrefix("milb/"), get)
        }
        val wrapped = JSONArray(teams.map { JSONObject().put("team", it) })
        return JSONObject()
            .put(
                "sports",
                JSONArray().put(JSONObject().put("leagues", JSONArray().put(JSONObject().put("teams", wrapped)))),
            )
            .toString()
    }

    /** A team's games from a few weeks back to a couple of months ahead. */
    suspend fun teamSchedule(path: String, teamId: String, get: suspend (String) -> String): String {
        val now = LiveTvClock.nowMs()
        val span = SportsDates.dayKey(now - 21 * DAY) to SportsDates.dayKey(now + 75 * DAY)
        val all: List<JSONObject> = when {
            path == "cricket/panel" -> panelDays(-3, 21, get)
            path.startsWith("cricket/") ->
                cricketSeason(path.removePrefix("cricket/"), span, get)
                    .filter { inSpan(isoMs(it.optString("date")), span) }
            path.startsWith("hockeytech/") -> hockeyTech(path.removePrefix("hockeytech/"), span, get)
            else -> milb(path.removePrefix("milb/"), span, get, teamId)
        }
        return eventsJson(
            all.filter { ev -> competitorsOf(ev).any { it.optJSONObject("team")?.optString("id") == teamId } },
        )
    }

    private fun competitorsOf(ev: JSONObject): List<JSONObject> {
        val a = ev.optJSONArray("competitions")?.optJSONObject(0)?.optJSONArray("competitors")
            ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    // ------------------------------------------------------------------ cricket

    /** Cricket's short names are codes ("KKR", "MR"): cards use the full name. */
    private fun withFullNames(ev: JSONObject): JSONObject {
        for (c in competitorsOf(ev)) {
            val t = c.optJSONObject("team") ?: continue
            val full = t.optString("displayName").ifBlank { t.optString("name") }
            val short = t.optString("shortDisplayName")
            if (full.isNotBlank() &&
                (short.isBlank() || short.length <= 4 || short == t.optString("abbreviation"))
            ) {
                t.put("shortDisplayName", full)
            }
        }
        return ev
    }

    /** A franchise league's seasons that touch [span]. */
    private suspend fun cricketSeason(
        id: String,
        span: Pair<String, String>,
        get: suspend (String) -> String,
    ): List<JSONObject> {
        val default = runCatching {
            JSONObject(get("${ESPN}cricket/$id/scoreboard"))
        }.getOrNull()
        val years = sortedSetOf(span.first.take(4).toInt(), span.second.take(4).toInt())
        default?.optJSONArray("leagues")?.optJSONObject(0)?.let { l ->
            l.optJSONObject("season")?.optInt("year", 0)?.takeIf { it > 2000 }?.let { years += it }
            l.optJSONArray("links")?.let { links ->
                for (i in 0 until links.length()) {
                    Regex("""/season/(\d{4})(?:/|$)""")
                        .find(links.optJSONObject(i)?.optString("href").orEmpty())
                        ?.let { years += it.groupValues[1].toInt() }
                }
            }
        }
        val out = LinkedHashMap<String, JSONObject>()
        fun add(root: JSONObject?) {
            val evs = root?.optJSONArray("events") ?: return
            for (i in 0 until evs.length()) {
                val e = evs.optJSONObject(i) ?: continue
                val eid = e.optString("id")
                if (eid.isNotBlank()) out[eid] = withFullNames(e)
            }
        }
        add(default)
        for (y in years) {
            add(
                runCatching {
                    JSONObject(get("${ESPN}cricket/$id/scoreboard?season=$y"))
                }.getOrNull(),
            )
        }
        return out.values.toList()
    }

    /** International matches from [fromDay] to [toDay] days from today. */
    private suspend fun panelDays(
        fromDay: Int,
        toDay: Int,
        get: suspend (String) -> String,
    ): List<JSONObject> {
        val now = LiveTvClock.nowMs()
        val out = LinkedHashMap<String, JSONObject>()
        for (d in fromDay..toDay) {
            val key = SportsDates.dayKey(now + d * DAY)
            cricketPanel(key to key, get).forEach { out[it.optString("id")] = it }
        }
        return out.values.toList()
    }

    /** International matches on the days of [span] (the panel is one day a request). */
    private suspend fun cricketPanel(
        span: Pair<String, String>,
        get: suspend (String) -> String,
    ): List<JSONObject> {
        val first = SportsDates.dayKeyStart(span.first) ?: return emptyList()
        val last = SportsDates.dayKeyStart(span.second) ?: return emptyList()
        // Guard against an accidental year-long range turning into hundreds of
        // requests; the sports window is at most a fortnight.
        if (last - first > 31 * DAY) return emptyList()
        val out = LinkedHashMap<String, JSONObject>()
        var t = first
        while (t <= last) {
            val day = SportsDates.dayKey(t)
            t += DAY
            val root = runCatching { JSONObject(get(CRICKET_PANEL + day)) }.getOrNull() ?: continue
            val blocks = root.optJSONArray("scores") ?: continue
            for (b in 0 until blocks.length()) {
                val evs = blocks.optJSONObject(b)?.optJSONArray("events") ?: continue
                for (i in 0 until evs.length()) {
                    val e = evs.optJSONObject(i) ?: continue
                    val card = e.optJSONArray("competitions")?.optJSONObject(0)
                        ?.optJSONObject("class")?.optString("generalClassCard")?.trim()
                    if (card !in CRICKET_FORMATS) continue
                    val eid = e.optString("id")
                    if (eid.isNotBlank()) out[eid] = withFullNames(e)
                }
            }
        }
        return out.values.toList()
    }

    // ------------------------------------------------------------------ HockeyTech

    /** "(...)" or "callback(...)" around JSON (some HockeyTech views). */
    private fun unwrap(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("{") || t.startsWith("[")) return t
        val open = t.indexOf('(')
        val close = t.lastIndexOf(')')
        return if (open >= 0 && close > open) t.substring(open + 1, close).trim() else t
    }

    private fun JSONObject.any(vararg names: String): String? {
        for (n in names) optString(n, "").trim().takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
        return null
    }

    private suspend fun ht(
        code: String,
        view: String,
        extra: String,
        get: suspend (String) -> String,
    ): JSONObject? {
        val key = HOCKEYTECH_KEYS[code] ?: return null
        val raw = get("$HOCKEYTECH&view=$view&client_code=$code&key=$key$extra")
        return runCatching { JSONObject(unwrap(raw)).optJSONObject("SiteKit") }.getOrNull()
    }

    /** Each team's logo as the league's own score bar gives it. */
    private val htLogos = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The score bar: games of the last and next week, live scores for today's. */
    private suspend fun htBar(code: String, get: suspend (String) -> String): JSONArray {
        val bar = runCatching {
            ht(code, "scorebar", "&numberofdaysback=7&numberofdaysahead=7", get)
                ?.optJSONArray("Scorebar") ?: JSONArray()
        }.getOrNull() ?: JSONArray()
        for (i in 0 until bar.length()) {
            val g = bar.optJSONObject(i) ?: continue
            for (side in listOf("Home", "Visitor")) {
                val tid = g.any("${side}ID") ?: continue
                val logo = g.any("${side}Logo")?.takeIf { it.startsWith("http") } ?: continue
                htLogos["$code:$tid"] = logo.replace("http://", "https://")
            }
        }
        return bar
    }

    private fun htLogo(code: String, tid: String) =
        htLogos["$code:$tid"] ?: "https://assets.leaguestat.com/$code/logos/$tid.png"

    private suspend fun hockeyTech(
        code: String,
        span: Pair<String, String>,
        get: suspend (String) -> String,
    ): List<JSONObject> {
        // The score bar first (it says where each team's logo is), then the
        // season (the whole schedule, filtered to the requested days), with the
        // score bar's games on top for their live scores.
        val bar = htBar(code, get)
        val season = runCatching {
            ht(code, "schedule", "", get)?.optJSONArray("Schedule") ?: JSONArray()
        }.getOrNull() ?: JSONArray()
        val games = LinkedHashMap<String, JSONObject>()
        for (i in 0 until season.length()) {
            val g = season.optJSONObject(i) ?: continue
            val ev = htEvent(code, g) ?: continue
            if (inSpan(isoMs(ev.optString("date")), span)) games[ev.optString("id")] = ev
        }
        for (i in 0 until bar.length()) {
            val ev = htEvent(code, bar.optJSONObject(i) ?: continue) ?: continue
            if (inSpan(isoMs(ev.optString("date")), span)) games[ev.optString("id")] = ev
        }
        return games.values.toList()
    }

    /** One HockeyTech game (the season list and the score bar name things differently). */
    private fun htEvent(code: String, g: JSONObject): JSONObject? {
        val id = g.any("game_id", "ID", "id") ?: return null
        val start = isoMs(g.any("GameDateISO8601")) ?: return null
        val raw = g.any("game_status", "GameStatusStringLong", "GameStatusString").orEmpty()
        val low = raw.lowercase(Locale.ROOT)
        val statusCode = g.any("GameStatus", "status")
        val off = low == "ppd" || low.contains("postpon") || low.contains("cancel")
        val state = when {
            low.startsWith("final") || statusCode == "4" -> "post"
            off -> "pre"
            Regex("""\b(1st|2nd|3rd|ot|so|intermission)\b|\d+:\d{2}\s*(1st|2nd|3rd|ot)""")
                .containsMatchIn(low) && !Regex("""\d{1,2}:\d{2}\s*(am|pm)""").containsMatchIn(low) -> "in"
            statusCode == "2" || statusCode == "3" -> "in"
            else -> "pre"
        }
        fun team(side: String, alt: String): JSONObject? {
            val tid = g.any("${side}_team", "${alt}ID") ?: return null
            val city = g.any("${side}_team_city", "${alt}City").orEmpty()
            val nick = g.any("${side}_team_nickname", "${alt}Nickname").orEmpty()
            val name = listOf(city, nick).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { g.any("${side}_team_name", "${alt}LongName") ?: return null }
            return JSONObject().put("id", tid).put("displayName", name)
                .put("shortDisplayName", nick.ifBlank { name })
                .put("abbreviation", g.any("${side}_team_code", "${alt}Code") ?: name.take(3).uppercase(Locale.ROOT))
                .put("location", city.ifBlank { null })
                .put("logo", htLogo(code, tid)).put("color", colourOf(name))
        }
        val home = team("home", "Home") ?: return null
        val away = team("visiting", "Visitor") ?: return null
        val hs = g.any("home_goal_count", "HomeGoals")
        val vs = g.any("visiting_goal_count", "VisitorGoals")
        val done = state == "post"
        val hN = hs?.toIntOrNull()
        val vN = vs?.toIntOrNull()
        val type = JSONObject()
            .put("state", state)
            .put(
                "name",
                when {
                    off -> "STATUS_POSTPONED"
                    state == "post" -> "STATUS_FINAL"
                    state == "in" -> "STATUS_IN_PROGRESS"
                    else -> "STATUS_SCHEDULED"
                },
            )
            .put("description", if (off) "Postponed" else raw)
            .put("shortDetail", if (state == "pre") "" else raw.ifBlank { if (done) "Final" else "Live" })
        val status = JSONObject().put("type", type)
        fun comp(t: JSONObject, home: Boolean, score: String?, win: Boolean, order: Int) =
            JSONObject().put("homeAway", if (home) "home" else "away")
                .put("team", t).put("score", score ?: "").put("winner", win).put("order", order)
        val competition = JSONObject().put("status", status)
            .put(
                "competitors",
                JSONArray()
                    .put(comp(away, false, vs, done && hN != null && vN != null && vN > hN, 0))
                    .put(comp(home, true, hs, done && hN != null && vN != null && hN > vN, 1)),
            )
        g.any("venue_name", "venue_location")?.let { competition.put("venue", JSONObject().put("fullName", it)) }
        return JSONObject().put("id", "$code-$id").put("date", SportsDates.isoOut(start)).put("status", status)
            .put("name", "${away.optString("displayName")} at ${home.optString("displayName")}")
            .put("competitions", JSONArray().put(competition))
    }

    private suspend fun hockeyTechTeams(code: String, get: suspend (String) -> String): List<JSONObject> {
        htBar(code, get)
        val list = runCatching {
            ht(code, "teamsbyseason", "", get)?.optJSONArray("Teamsbyseason") ?: JSONArray()
        }.getOrNull() ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val t = list.optJSONObject(i) ?: return@mapNotNull null
            val tid = t.any("id", "team_id") ?: return@mapNotNull null
            val city = t.any("city").orEmpty()
            val nick = t.any("nickname").orEmpty()
            val name = listOf(city, nick).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { t.any("name") ?: return@mapNotNull null }
            JSONObject().put("id", tid).put("displayName", name)
                .put("shortDisplayName", nick.ifBlank { name })
                .put("abbreviation", t.any("code") ?: name.take(3).uppercase(Locale.ROOT))
                .put("location", city.ifBlank { null })
                .put("logo", htLogo(code, tid)).put("color", colourOf(name))
        }
    }

    // ------------------------------------------------------------------ Minor League Baseball

    private suspend fun milb(
        sportId: String,
        span: Pair<String, String>,
        get: suspend (String) -> String,
        teamId: String? = null,
    ): List<JSONObject> {
        fun d(k: String) = "${k.take(4)}-${k.substring(4, 6)}-${k.substring(6, 8)}"
        val url = "${MLB}schedule?sportId=$sportId&startDate=${d(span.first)}&endDate=${d(span.second)}" +
            "&hydrate=team,venue,linescore,broadcasts(all)" +
            (teamId?.let { "&teamId=$it" } ?: "")
        val root = runCatching { JSONObject(get(url)) }.getOrNull() ?: return emptyList()
        val out = ArrayList<JSONObject>()
        val dates = root.optJSONArray("dates") ?: return out
        for (i in 0 until dates.length()) {
            val games = dates.optJSONObject(i)?.optJSONArray("games") ?: continue
            for (j in 0 until games.length()) milbEvent(games.optJSONObject(j) ?: continue)?.let { out += it }
        }
        return out
    }

    private fun milbTeamJson(t: JSONObject?): JSONObject? {
        t ?: return null
        val id = t.optInt("id", 0).takeIf { it > 0 }?.toString() ?: return null
        val name = t.optString("name").ifBlank { return null }
        return JSONObject().put("id", id).put("displayName", name)
            .put("shortDisplayName", t.optString("teamName").ifBlank { name })
            .put("abbreviation", t.optString("abbreviation").ifBlank { name.take(3).uppercase(Locale.ROOT) })
            .put("location", t.optString("locationName").ifBlank { null })
            .put("logo", "https://www.mlbstatic.com/team-logos/$id.svg").put("color", colourOf(name))
    }

    private fun milbEvent(g: JSONObject): JSONObject? {
        val id = g.optLong("gamePk", 0L).takeIf { it > 0 } ?: return null
        val start = isoMs(g.optString("gameDate")) ?: return null
        val st = g.optJSONObject("status")
        val abstract = st?.optString("abstractGameState").orEmpty()
        val detailed = st?.optString("detailedState").orEmpty()
        val off = Regex("Postponed|Cancelled|Canceled|Suspended", RegexOption.IGNORE_CASE)
            .containsMatchIn(detailed)
        val state = when {
            off -> "pre"
            abstract == "Final" -> "post"
            abstract == "Live" -> "in"
            else -> "pre"
        }
        val line = g.optJSONObject("linescore")
        val inning = line?.let { l ->
            listOf(
                l.optString("inningState").ifBlank { l.optString("inningHalf") },
                l.optString("currentInningOrdinal"),
            ).filter { it.isNotBlank() }.joinToString(" ")
        }.orEmpty()
        val type = JSONObject()
            .put("state", state)
            .put(
                "name",
                when {
                    off -> "STATUS_POSTPONED"
                    state == "post" -> "STATUS_FINAL"
                    state == "in" -> "STATUS_IN_PROGRESS"
                    else -> "STATUS_SCHEDULED"
                },
            )
            .put("description", if (off) detailed else "")
            .put(
                "shortDetail",
                when (state) {
                    "in" -> inning.ifBlank { "Live" }
                    "post" -> "Final"
                    else -> ""
                },
            )
        val status = JSONObject().put("type", type)
        val teams = g.optJSONObject("teams") ?: return null
        val innings = line?.optJSONArray("innings")
        fun comp(side: String, order: Int): JSONObject? {
            val s = teams.optJSONObject(side) ?: return null
            val t = milbTeamJson(s.optJSONObject("team")) ?: return null
            val score = if (s.has("score")) s.optInt("score").toString() else ""
            val c = JSONObject().put("homeAway", side).put("team", t).put("score", score)
                .put("winner", s.optBoolean("isWinner", false)).put("order", order)
            if (innings != null && innings.length() > 0 && state != "pre") {
                c.put(
                    "linescores",
                    JSONArray(
                        (0 until innings.length()).map { i ->
                            val r = innings.optJSONObject(i)?.optJSONObject(side)?.opt("runs")
                            JSONObject().put("displayValue", r?.toString() ?: "-")
                        },
                    ),
                )
            }
            return c
        }
        val home = comp("home", 1) ?: return null
        val away = comp("away", 0) ?: return null
        val competition = JSONObject().put("status", status)
            .put("competitors", JSONArray().put(away).put(home))
        // TV (and streaming) it's on.
        val broadcasts = g.optJSONArray("broadcasts") ?: JSONArray()
        val tv = (0 until broadcasts.length()).mapNotNull { broadcasts.optJSONObject(it) }
            .filter { it.optString("type").equals("TV", true) || it.optString("type").isBlank() }
            .map { it.optString("name") }.filter { it.isNotBlank() }.distinct()
        if (tv.isNotEmpty()) {
            competition.put("broadcasts", JSONArray().put(JSONObject().put("names", JSONArray(tv))))
        }
        g.optJSONObject("venue")?.optString("name")?.takeIf { it.isNotBlank() }
            ?.let { competition.put("venue", JSONObject().put("fullName", it)) }
        return JSONObject().put("id", "milb-$id").put("date", SportsDates.isoOut(start)).put("status", status)
            .put("name", "${away.optJSONObject("team")?.optString("displayName")} at ${home.optJSONObject("team")?.optString("displayName")}")
            .put("competitions", JSONArray().put(competition))
    }

    private suspend fun milbTeams(sportId: String, get: suspend (String) -> String): List<JSONObject> {
        val root = runCatching { JSONObject(get("${MLB}teams?sportId=$sportId")) }.getOrNull()
            ?: return emptyList()
        val a = root.optJSONArray("teams") ?: return emptyList()
        return (0 until a.length()).mapNotNull { milbTeamJson(a.optJSONObject(it)) }
    }
}
