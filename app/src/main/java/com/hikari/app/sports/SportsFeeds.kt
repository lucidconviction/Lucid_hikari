package com.hikari.app.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Fetches sports data from free public feeds:
 * - ESPN (scores, schedules, teams, standings, summaries)
 * - sporting-events.org (fixtures for guide-only sports)
 * - BKFC, TheSportsDB (wrestling)
 * - footballwebpages/tsdb (football leagues)
 * - HockeyTech (hockey leagues)
 * - MiLB (minor league baseball)
 * - HockeyTech Teams
 */
object SportsFeeds {

    private const val DATA_URL = "https://raw.githubusercontent.com/nuvio-media/sports-feeds/main/"
    private const val WRESTLING_URL = "https://www.thesportsdb.com/api/v1/json/3/eventspastleague.php?id=4420" // WWE (using public key 3)

    private suspend fun get(url: String): String = SportsPlatform.get(url)

    /** Raw scoreboard for a date range (yyyyMMdd). */
    suspend fun scoreboardRaw(league: League, fromDay: String, toDay: String): String {
        val query = if (!league.espnQuery.isNullOrBlank()) "&${league.espnQuery}" else ""
        val dates = if (fromDay == toDay) fromDay else "$fromDay-$toDay"
        return get("https://site.api.espn.com/apis/site/v2/sports/${league.espnPath}/scoreboard?dates=$dates&limit=300$query")
    }

    /** Scoreboard for a single day. */
    suspend fun scoreboardDayRaw(league: League, day: String): String =
        scoreboardRaw(league, day, day)

    /** Default scoreboard (today + next 2 days). */
    suspend fun scoreboardDefaultRaw(league: League): String =
        get("https://site.api.espn.com/apis/site/v2/sports/${league.espnPath}/scoreboard?limit=300${league.espnQuery}")

    /** Teams for a league. */
    suspend fun teamsRaw(league: League): String =
        get("https://site.api.espn.com/apis/site/v2/sports/${league.espnPath}/teams?limit=1000")

    /** Team schedule (fixtures=true for future, false for past). */
    suspend fun teamScheduleRaw(leaguePath: String, teamId: String, fixtures: Boolean): String =
        get("https://site.api.espn.com/apis/site/v2/sports/$leaguePath/teams/$teamId/schedule?${if (fixtures) "season=2026" else "season=2025"}")

    // ---- Parsing helpers (from org.json to our models) ----

    /** Scoring plays for a game. */
    suspend fun summary(league: League, espnId: String): List<ScoringPlay> = parseSummary(summaryJson(league, espnId))

    suspend fun summaryJson(league: League, espnId: String): JSONObject =
        JSONObject(get("https://cdn.espn.com/core/${league.sport.name.lowercase()}/summary?xhr=1&gameId=$espnId"))

    /** Standings table as raw JSON text (returns null when the league doesn't have one). */
    suspend fun standingsRaw(url: String): String? = runCatching { get(url) }.getOrNull()

    private fun parseSummary(o: JSONObject): List<ScoringPlay> {
        val plays = mutableListOf<ScoringPlay>()
        val periodTypes = o.optJSONArray("periods") ?: return plays
        (0 until periodTypes.length()).forEach { i ->
            val period = periodTypes.optJSONObject(i) ?: return@forEach
            val playTypes = period.optJSONArray("plays") ?: return@forEach
            (0 until playTypes.length()).forEach { j ->
                val play = playTypes.optJSONObject(j) ?: return@forEach
                plays.add(ScoringPlay(
                    clock = play.optString("clock").takeIf { it.isNotBlank() } ?: play.optString("time"),
                    teamAbbr = play.optString("teamAbbreviation").takeIf { it.isNotBlank() },
                    text = play.optString("text"),
                    awayScore = play.optString("awayScore").takeIf { it.isNotBlank() },
                    homeScore = play.optString("homeScore").takeIf { it.isNotBlank() }
                ))
            }
        }
        return plays
    }

    /** sporting-events.org fixtures. */
    suspend fun fixturesRaw(slug: String): String = get("https://sporting-events.org/data/$slug.json")

    /** BKFC events. */
    suspend fun bkfcEventsRaw(): String = get("https://api.bkfc.com/events")

    /** TheSportsDB wrestling events. */
    suspend fun wrestlingRaw(): String = get(WRESTLING_URL)

    /** Football index (footballwebpages). */
    suspend fun footballIndexRaw(): String =
        get(DATA_URL + "football/index.json")

    /** Football league data (footballwebpages). */
    suspend fun footballLeagueRaw(tsdbId: String): String {
        return get(DATA_URL + "football/$tsdbId.json")
    }

    /** TheSportsDB game details. */
    suspend fun tsdbGameRaw(tsdbEventId: String): String =
        get(DATA_URL + "sportsdb/games/$tsdbEventId.json")

    /** ESPN shared cache (pre-parsed team/venue data). */
    suspend fun espnSharedRaw(leagueId: String): String? =
        runCatching { get(DATA_URL + "espn/" + safeFileId(leagueId) + ".json") }.getOrNull()

    private fun safeFileId(s: String): String = s.replace('/', '_').replace('.', '_')

    // ---- Caching layer ----

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry<Any>>()
    private const val DEFAULT_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutes

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
        keep("sb:$path:$from:$to", 2 * 60 * 1000) { get("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?dates=$from-$to&limit=300") }

    /** Scoreboard now (today + next 2 days) with caching. */
    suspend fun scoreboardNow(path: String, get: suspend (String) -> String): String =
        keep("sbnow:$path", 3 * 60 * 1000) { get("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?limit=300") }

    /** Teams with caching. */
    suspend fun teams(path: String, get: suspend (String) -> String): String =
        keep("teams:$path", 60 * 60 * 1000) { get("https://site.api.espn.com/apis/site/v2/sports/$path/teams?limit=1000") }

    /** Team schedule with caching. */
    suspend fun teamSchedule(path: String, teamId: String, get: suspend (String) -> String): String =
        keep("sched:$path:$teamId", 15 * 60 * 1000) { get("https://site.api.espn.com/apis/site/v2/sports/$path/teams/$teamId/schedule?season=2026") }

    // ---- Cricket ----

    private suspend fun cricketSeason(id: String, span: Pair<String, String>, get: suspend (String) -> String): List<JSONObject> {
        val raw = get("https://hs-consumer-api.espncricinfo.com/v1/pages/series/schedule?lang=en&seriesId=$id")
        val o = JSONObject(raw)
        val matches = o.optJSONArray("matches") ?: return emptyList()
        return (0 until matches.length()).mapNotNull { i ->
            matches.optJSONObject(i)?.let { JSONObject(it.toString()) }
        }
    }

    // ---- Panel Days (HockeyTech) ----

    private suspend fun panelDays(fromDay: Int, toDay: Int, get: suspend (String) -> String): List<JSONObject> {
        val days = (fromDay..toDay).map { SportsDates.dayStart(it) }
        return days.map { day ->
            val key = SportsDates.dayKey(day)
            val raw = get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=schedule&key=$key")
            JSONObject(raw)
        }.filter { it.has("SiteKit") }
    }

    private suspend fun cricketPanel(span: Pair<String, String>, get: suspend (String) -> String): List<JSONObject> {
        val (from, to) = span
        val raw = get("https://hs-consumer-api.espncricinfo.com/v1/pages/series/schedule?lang=en&seriesId=$from")
        return listOf(JSONObject(raw))
    }

    // ---- HockeyTech ----

    private suspend fun ht(code: String, view: String, extra: String, get: suspend (String) -> String): JSONObject? {
        val raw = get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=$view&key=$code$extra")
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private suspend fun htBar(code: String, get: suspend (String) -> String): org.json.JSONArray {
        val raw = get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=standings&key=$code")
        return org.json.JSONArray(raw)
    }

    private suspend fun hockeyTech(code: String, span: Pair<String, String>, get: suspend (String) -> String): List<JSONObject> {
        val (from, to) = span
        val startMs = SportsDates.dayKeyStart(from) ?: 0L
        val endMs = SportsDates.dayKeyStart(to) ?: Long.MAX_VALUE
        val days = mutableListOf<Long>()
        var d = startMs
        while (d <= endMs) {
            days.add(d)
            d += 86_400_000L
        }
        return days.map { day ->
            val key = SportsDates.dayKey(day)
            val raw = get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=schedule&key=$key&team=$code")
            runCatching { JSONObject(raw) }.getOrNull()
        }.filterNotNull()
    }

    private suspend fun hockeyTechTeams(code: String, get: suspend (String) -> String): List<JSONObject> {
        val raw = get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=team&key=$code")
        return listOf(runCatching { JSONObject(raw) }.getOrNull()).filterNotNull()
    }

    // ---- MiLB (Minor League Baseball) ----

    private suspend fun milb(sportId: String, span: Pair<String, String>, get: suspend (String) -> String, teamId: String? = null): List<JSONObject> {
        val (from, to) = span
        val url = "https://statsapi.mlb.com/api/v1/schedule?sportId=$sportId&startDate=$from&endDate=$to${teamId?.let { "&teamId=$it" } ?: ""}"
        val raw = get(url)
        val o = JSONObject(raw)
        val dates = o.optJSONArray("dates") ?: return emptyList()
        return (0 until dates.length()).flatMap { i ->
            dates.optJSONObject(i)?.optJSONArray("games")?.let { games ->
                (0 until games.length()).mapNotNull { j -> games.optJSONObject(j)?.let { JSONObject(it.toString()) } }
            } ?: emptyList()
        }
    }

    private suspend fun milbTeams(sportId: String, get: suspend (String) -> String): List<JSONObject> {
        val raw = get("https://statsapi.mlb.com/api/v1/teams?sportId=$sportId")
        val o = JSONObject(raw)
        val teams = o.optJSONArray("teams") ?: return emptyList()
        return (0 until teams.length()).mapNotNull { i -> teams.optJSONObject(i)?.let { JSONObject(it.toString()) } }
    }

    // ---- Summaries / Standings parsers (returning raw for now, parsing happens in SportsRepository) ----

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

    private suspend fun htSeason(code: String, get: suspend (String) -> String): String? =
        runCatching { get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=standings&key=$code") }.getOrNull()

    private suspend fun htSummary(code: String, eventId: String, get: suspend (String) -> String): JSONObject {
        val raw = get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=game&key=$code&game_id=$eventId")
        return JSONObject(raw)
    }

    private suspend fun htStandings(code: String, get: suspend (String) -> String): String? =
        runCatching { get("https://lscluster.hockeytech.com/feed/?feed=modulekit&view=standings&key=$code") }.getOrNull()

    private suspend fun milbSummary(eventId: String, get: suspend (String) -> String): JSONObject {
        val raw = get("https://statsapi.mlb.com/api/v1.1/game/$eventId/feed/live")
        return JSONObject(raw)
    }

    private suspend fun milbStandings(sportId: String, get: suspend (String) -> String): String? =
        runCatching { get("https://statsapi.mlb.com/api/v1/standings?leagueId=103,104&sportId=$sportId") }.getOrNull()
}

/** One scoring play in a game summary. */
data class ScoringPlay(
    val clock: String,
    val teamAbbr: String?,
    val text: String,
    val awayScore: String?,
    val homeScore: String?
)