package com.hikari.app.sports

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hikari.app.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central state holder for the Sports hub.
 * Handles settings, follows, events, matches, and standings.
 */
object SportsRepository {

    // ---- Constants ----
    const val DAYS_BACK = 3
    const val DAYS_AHEAD = 7
    private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    private const val SETTINGS_KEY = "sports_settings"
    private const val FOLLOWS_KEY = "sports_follows"
    private const val STANDINGS_KEY_PREFIX = "sports_standings_"

    // ---- State flows ----
    val settings = MutableStateFlow(SportsSettings())
    val follows = MutableStateFlow(SportsFollows())
    val events = MutableStateFlow<List<SportsEvent>>(emptyList())
    val known = MutableStateFlow<List<SportsEvent>>(emptyList())
    val matches = MutableStateFlow<Map<String, List<ChannelMatch>>>(emptyMap())
    val loading = MutableStateFlow(false)
    val matching = MutableStateFlow(false)
    val problem = MutableStateFlow<String?>(null)
    val standings = MutableStateFlow<Map<String, SportsStandings.StandingsView>>(emptyMap())

    private val viewing = MutableStateFlow(false)
    private val refreshJob = SupervisorJob()
    private val scope = CoroutineScope(refreshJob + Dispatchers.IO)
    private val mutex = Mutex()
    private var initialized = false
    private var context: Context? = null

    // ---- Persistence ----
    private lateinit var store: DataStore<Preferences>
    private val SETTINGS_PREF_KEY = stringPreferencesKey(SETTINGS_KEY)
    private val FOLLOWS_PREF_KEY = stringPreferencesKey(FOLLOWS_KEY)

    private val Context.sportsDataStore by preferencesDataStore(name = "sports")

    fun init(ctx: Context) {
        if (initialized) return
        context = ctx.applicationContext
        store = ctx.applicationContext.sportsDataStore
        initialized = true
        
        scope.launch {
            // Load settings and follows first
            val sRaw = store.data.map { it[SETTINGS_PREF_KEY] }.first()
            settings.value = SportsSettings.fromJson(sRaw)
            val fRaw = store.data.map { it[FOLLOWS_PREF_KEY] }.first()
            follows.value = SportsFollows.fromJson(fRaw)
            
            android.util.Log.d("Hikari/Sports", "Initialized with region: ${settings.value.region.ifBlank { "default" }}")
            
            // Initial load
            refreshNow()
            
            // Periodic refresh
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                if (viewing.value) refreshNow()
            }
        }
    }

    private fun loadSettings() { /* redundant now */ }
    private fun loadFollows() { /* redundant now */ }

    suspend fun saveSettings() {
        store.edit { it[SETTINGS_PREF_KEY] = settings.value.toJson() }
    }

    suspend fun saveFollows() {
        store.edit { it[FOLLOWS_PREF_KEY] = follows.value.toJson() }
    }

    // ---- Public API ----

    /** Start a full refresh (events + matches + standings). */
    fun refreshNow() {
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                loading.value = true
                problem.value = null
                val (newEvents, newKnown) = coroutineScope {
                    val evs = async { loadEvents() }
                    val kn = async { loadKnown() }
                    evs.await() to kn.await()
                }
                events.value = newEvents
                known.value = newKnown
                // Match to channels (will be called with current IPTV channels from UI)
                // loadMatches() is called from UI when channels are available
                // Load standings for followed leagues
                loadStandings()
            } catch (e: Exception) {
                problem.value = "Failed to load: ${e.message}"
            } finally {
                loading.value = false
                mutex.unlock()
            }
        }
    }

    /** Set viewing state (for auto-refresh). */
    fun setViewing(v: Boolean) {
        viewing.value = v
        if (v) refreshNow()
    }

    /** Match current events to the given IPTV channels. */
    fun matchChannels(channels: List<com.hikari.app.data.IptvChannel>) {
        scope.launch {
            matching.value = true
            try {
                val m = SportsMatcher.match(events.value, channels)
                matches.value = m
            } finally {
                matching.value = false
            }
        }
    }

    /** Toggle follow for a team. */
    suspend fun toggleTeam(team: FollowedTeam) {
        val current = follows.value
        val exists = current.teams.any { it.leagueId == team.leagueId && it.teamId == team.teamId }
        val newTeams = if (exists) current.teams.filter { !(it.leagueId == team.leagueId && it.teamId == team.teamId) }
        else current.teams + team
        follows.value = current.copy(teams = newTeams)
        saveFollows()
    }

    /** Toggle follow for a league. */
    suspend fun toggleLeague(leagueId: String) {
        val current = follows.value
        val newLeagues = if (leagueId in current.leagues) current.leagues - leagueId else current.leagues + leagueId
        follows.value = current.copy(leagues = newLeagues)
        saveFollows()
    }

    /** Toggle follow for a word. */
    suspend fun toggleWord(word: String) {
        val current = follows.value
        val trimmed = word.trim()
        if (trimmed.isBlank()) return
        val newWords = if (trimmed in current.words) current.words.filter { it != trimmed } else current.words + trimmed
        follows.value = current.copy(words = newWords)
        saveFollows()
    }

    /** Pin/unpin a league to the top bar. */
    suspend fun togglePin(leagueId: String) {
        val current = follows.value
        val newPins = if (leagueId in current.pins) current.pins.filter { it != leagueId } else current.pins + leagueId
        follows.value = current.copy(pins = newPins)
        saveFollows()
    }

    /** Unpin a league (remove from top bar). */
    suspend fun unpin(leagueId: String) {
        val current = follows.value
        val newUnpinned = current.unpinned + leagueId
        val newPins = current.pins.filter { it != leagueId }
        follows.value = current.copy(pins = newPins, unpinned = newUnpinned)
        saveFollows()
    }

    /** Reorder My Sports rows. */
    suspend fun reorder(order: List<String>) {
        follows.value = follows.value.copy(order = order)
        saveFollows()
    }

    /** Toggle a reminder for an event. */
    suspend fun toggleRemind(event: SportsEvent, autoSwitch: Boolean? = null) {
        val current = follows.value
        val existing = current.reminders.firstOrNull { it.eventId == event.id }
        val newReminders = if (existing != null) {
            current.reminders.filter { it.eventId != event.id }
        } else {
            current.reminders + SportsReminder(
                eventId = event.id,
                title = event.title,
                startMs = event.startMs,
                autoSwitch = autoSwitch ?: settings.value.autoSwitch
            )
        }
        follows.value = current.copy(reminders = newReminders)
        saveFollows()
    }

    /** Check if a reminder is set for an event. */
    fun isReminded(eventId: String): Boolean =
        follows.value.reminders.any { it.eventId == eventId }

    /** Update settings. */
    suspend fun updateSettings(block: SportsSettings.() -> SportsSettings) {
        settings.value = settings.value.block()
        saveSettings()
    }

    // ---- Private loading methods ----

    /** Load all events from active leagues. */
    private suspend fun loadEvents(): List<SportsEvent> {
        val activeLeagues = activeLeagues(settings.value, follows.value)
        android.util.Log.d("Hikari/Sports", "Loading events for ${activeLeagues.size} active leagues: ${activeLeagues.joinToString { it.id }}")
        val allEvents = mutableListOf<SportsEvent>()

        // Load scoreboards for each active league in parallel
        coroutineScope {
            activeLeagues.map { league ->
                async {
                    try {
                        val evs = loadLeagueEvents(league)
                        android.util.Log.d("Hikari/Sports", "Loaded ${evs.size} events for ${league.id}")
                        evs
                    } catch (e: Exception) {
                        android.util.Log.e("Hikari/Sports", "Failed to load events for ${league.id}", e)
                        emptyList()
                    }
                }
            }.awaitAll().forEach { allEvents.addAll(it) }
        }

        // Filter to the window [now - 3 days, now + 7 days]
        val now = LiveTvClock.nowMs()
        val windowStart = SportsDates.dayStart(-DAYS_BACK, now)
        val windowEnd = SportsDates.dayStart(DAYS_AHEAD + 1, now)

        val filtered = allEvents
            .filter { ev ->
                // Event overlaps with our window
                (ev.endMs ?: (ev.startMs + 1)) >= windowStart && ev.startMs < windowEnd
            }
            .distinctBy { it.id }
            .sortedBy { it.startMs }
        
        android.util.Log.d("Hikari/Sports", "Total events after filtering: ${filtered.size}")
        return filtered
    }

    /** Load events for a single league. */
    private suspend fun loadLeagueEvents(league: League): List<SportsEvent> {
        if (league.guideOnly) return loadGuideOnlyEvents(league)
        if (league.espnPath == null) return emptyList()
        
        // Fetch Yesterday, Today, and Tomorrow individually to avoid HTTP 400 on ranges
        val days = listOf(-1, 0, 1).map { SportsDates.dayKey(SportsDates.dayStart(it)) }
        val out = mutableListOf<SportsEvent>()
        
        coroutineScope {
            days.map { day ->
                async {
                    try {
                        val raw = SportsFeeds.scoreboardDayRaw(league, day)
                        parseScoreboard(league, raw)
                    } catch (e: Exception) {
                        android.util.Log.e("Hikari/Sports", "Error loading events for ${league.id} on $day", e)
                        emptyList()
                    }
                }
            }.awaitAll().forEach { out.addAll(it) }
        }
        
        return out.distinctBy { it.id }
    }

    /** Load guide-only events (darts, snooker, wrestling) from sporting-events.org / TheSportsDB. */
    private suspend fun loadGuideOnlyEvents(league: League): List<SportsEvent> {
        return when (league.fixtureSlug) {
            "darts" -> loadDartsEvents(league)
            "snooker" -> loadSnookerEvents(league)
            else -> loadWrestlingEvents(league)
        }
    }

    /** Load darts events from sporting-events.org. */
    private suspend fun loadDartsEvents(league: League): List<SportsEvent> {
        val raw = SportsFeeds.fixturesRaw(league.fixtureSlug!!)
        val o = JSONObject(raw)
        val events = o.optJSONArray("events") ?: return emptyList()
        val out = mutableListOf<SportsEvent>()
        (0 until events.length()).forEach { i ->
            val e = events.optJSONObject(i) ?: return@forEach
            val startMs = SportsDates.parseIso(e.optString("start")) ?: return@forEach
            val title = e.optString("title")
            val state = when {
                startMs > LiveTvClock.nowMs() -> GameState.PRE
                e.optBoolean("live", false) -> GameState.LIVE
                else -> GameState.POST
            }
            out += SportsEvent(
                id = "darts-${e.optString("id")}",
                leagueId = league.id,
                startMs = startMs,
                hasTime = true,
                state = state,
                title = title,
                subtitle = null,
                status = if (state == GameState.LIVE) "Live" else if (state == GameState.POST) "Final" else "Upcoming",
                away = null,
                home = null,
                source = EventSource.FIXTURES,
                espnId = null
            )
        }
        return out
    }

    /** Load snooker events from sporting-events.org. */
    private suspend fun loadSnookerEvents(league: League): List<SportsEvent> {
        val raw = SportsFeeds.fixturesRaw(league.fixtureSlug!!)
        val o = JSONObject(raw)
        val events = o.optJSONArray("events") ?: return emptyList()
        val out = mutableListOf<SportsEvent>()
        (0 until events.length()).forEach { i ->
            val e = events.optJSONObject(i) ?: return@forEach
            val startMs = SportsDates.parseIso(e.optString("start")) ?: return@forEach
            val title = e.optString("title")
            val state = when {
                startMs > LiveTvClock.nowMs() -> GameState.PRE
                e.optBoolean("live", false) -> GameState.LIVE
                else -> GameState.POST
            }
            out += SportsEvent(
                id = "snooker-${e.optString("id")}",
                leagueId = league.id,
                startMs = startMs,
                hasTime = true,
                state = state,
                title = title,
                subtitle = null,
                status = if (state == GameState.LIVE) "Live" else if (state == GameState.POST) "Final" else "Upcoming",
                away = null,
                home = null,
                source = EventSource.FIXTURES,
                espnId = null
            )
        }
        return out
    }

    /** Load wrestling events from TheSportsDB. */
    private suspend fun loadWrestlingEvents(league: League): List<SportsEvent> {
        val raw = SportsFeeds.wrestlingRaw()
        val o = JSONObject(raw)
        val events = o.optJSONArray("events") ?: return emptyList()
        val out = mutableListOf<SportsEvent>()
        (0 until events.length()).forEach { i ->
            val e = events.optJSONObject(i) ?: return@forEach
            val title = e.optString("strEvent")
            val dateStr = e.optString("dateEvent")
            val timeStr = e.optString("strTime")
            val startMs = SportsDates.parseIso("$dateStr${if (timeStr.isNotBlank()) "T$timeStr" else "T00:00"}Z") ?: return@forEach
            val state = when {
                startMs > LiveTvClock.nowMs() -> GameState.PRE
                e.optString("strStatus") == "Live" -> GameState.LIVE
                else -> GameState.POST
            }
            out += SportsEvent(
                id = "wrestling-${e.optString("idEvent")}",
                leagueId = league.id,
                startMs = startMs,
                hasTime = timeStr.isNotBlank(),
                state = state,
                title = SportsCatalog.wrestlingHeadline(title),
                subtitle = null,
                status = if (state == GameState.LIVE) "Live" else if (state == GameState.POST) "Final" else "Upcoming",
                away = null,
                home = null,
                source = EventSource.FIXTURES,
                espnId = null,
                about = e.optString("strDescriptionEN").takeIf { it.isNotBlank() }
            )
        }
        return out
    }

    /** Parse ESPN scoreboard JSON into SportsEvent list. */
    private fun parseScoreboard(league: League, raw: String): List<SportsEvent> {
        if (raw.isBlank()) return emptyList()
        val o = JSONObject(raw)
        val events = o.optJSONArray("events") ?: return emptyList()
        val out = mutableListOf<SportsEvent>()
        val now = LiveTvClock.nowMs()
        
        android.util.Log.d("Hikari/Sports", "Parsing ${events.length()} events for ${league.id}")
        
        (0 until events.length()).forEach { i ->
            val e = events.optJSONObject(i) ?: run {
                android.util.Log.w("Hikari/Sports", "Event $i is null")
                return@forEach
            }
            val id = e.optString("id")
            val competitions = e.optJSONArray("competitions")
            if (competitions == null || competitions.length() == 0) {
                android.util.Log.w("Hikari/Sports", "Event $id has no competitions")
                return@forEach
            }
            val competition = competitions.optJSONObject(0) ?: run {
                android.util.Log.w("Hikari/Sports", "Event $id has null competition[0]")
                return@forEach
            }
            
            // Start time
            val dateStr = e.optString("date")
            val startMs = SportsDates.parseIso(dateStr) ?: run {
                android.util.Log.w("Hikari/Sports", "Event $id has invalid date: $dateStr")
                return@forEach
            }
            val hasTime = dateStr.contains('T')
            
            // State
            val status = competition.optJSONObject("status") ?: run {
                android.util.Log.w("Hikari/Sports", "Event $id has null status")
                return@forEach
            }
            val statusType = status.optJSONObject("type")
            val stateName = statusType?.optString("name")
            val state = when (stateName?.lowercase()) {
                "status.scheduled", "status.time_scheduled", "status_scheduled", "status.time.scheduled" -> GameState.PRE
                "status.in_progress", "status.halftime", "status.overtime", "status.in_progress", "status.inprogress", "status.in_progress", "status_live", "status.live" -> GameState.LIVE
                "status.final", "status.completed", "status_full_time", "status_final", "status.final", "status.completed" -> GameState.POST
                else -> GameState.OFF
            }
            
            // Competitors
            val competitors = competition.optJSONArray("competitors")
            if (competitors == null || competitors.length() != 2) {
                android.util.Log.w("Hikari/Sports", "Event $id has ${competitors?.length() ?: 0} competitors")
                return@forEach
            }
            
            val away = parseSide(competitors.optJSONObject(0), league, true, competition)
            val home = parseSide(competitors.optJSONObject(1), league, false, competition)
            
            if (away == null || home == null) {
                android.util.Log.w("Hikari/Sports", "Event $id skipped: away=$away, home=$home")
                return@forEach
            }
            
            // Broadcasts
            val broadcasts = mutableListOf<String>()
            competition.optJSONArray("broadcasts")?.let { bc ->
                (0 until bc.length()).forEach { j ->
                    bc.optJSONObject(j)?.let { b ->
                        b.optJSONObject("media")?.optString("shortName")?.let { broadcasts += it }
                    }
                }
            }
            
            // Title
            val shortName = e.optString("shortName")
            val title = if (shortName.isNotBlank()) shortName else "${away.team.name} @ ${home.team.name}"
            val statusText = statusType?.optString("shortDetail")?.takeIf { it.isNotBlank() }
                ?: statusType?.optString("detail")?.takeIf { it.isNotBlank() }
            
            out += SportsEvent(
                id = "espn-$id",
                leagueId = league.id,
                startMs = startMs,
                hasTime = hasTime,
                state = state,
                title = title,
                subtitle = e.optString("name").takeIf { it.isNotBlank() },
                status = statusText,
                away = away,
                home = home,
                broadcasts = broadcasts,
                source = EventSource.ESPN,
                espnId = id.takeIf { it.isNotBlank() },
                note = e.optString("notes").takeIf { it.isNotBlank() }
            )
            android.util.Log.d("Hikari/Sports", "Added event: $id ($title)")
        }
        return out
    }

    /** Parse one side (team) from ESPN competition. */
    private fun parseSide(o: JSONObject?, league: League, isAway: Boolean, competition: JSONObject): Side? {
        if (o == null) {
            android.util.Log.w("Hikari/Sports", "parseSide: o is null")
            return null
        }
        val teamObj = o.optJSONObject("team") ?: run {
            android.util.Log.w("Hikari/Sports", "parseSide: team object is missing in ${o.keys()}")
            return null
        }
        val teamInfoObj = teamObj.optJSONObject("team") ?: teamObj
        val id = teamInfoObj.optString("id")
        if (id.isBlank()) {
            android.util.Log.w("Hikari/Sports", "parseSide: team id is missing")
            return null
        }
        val name = teamInfoObj.optString("shortDisplayName").takeIf { it.isNotBlank() } ?: teamInfoObj.optString("name")
        val fullName = teamInfoObj.optString("displayName")
        val abbr = teamInfoObj.optString("abbreviation")
        val location = teamInfoObj.optString("location").takeIf { it.isNotBlank() }
        val color = teamInfoObj.optString("color").let { if (it.isNotBlank()) it.toLongOrNull(16) ?: 0xFF2A2F3A else null }
        val altColor = teamInfoObj.optString("alternateColor").let { if (it.isNotBlank()) it.toLongOrNull(16) else null }
        val logo = teamInfoObj.optJSONArray("logos")?.optJSONObject(0)?.optString("href").takeIf { it != null && it.isNotBlank() }
        val record = o.optString("records").let { parseRecord(it) }
        val score = o.optString("score").takeIf { it.isNotBlank() }
        val winner = o.optBoolean("winner") == true
        
        val team = TeamInfo(league.id, id, name, fullName, abbr, location, color, altColor, logo, record)
        return Side(team, score, record, winner, !isAway)
    }

    private fun parseRecord(raw: String): String? {
        if (raw.isBlank() || raw == "{}") return null
        val o = JSONObject(raw)
        val items = o.optJSONArray("items") ?: return null
        val parts = mutableListOf<String>()
        (0 until items.length()).forEach { i ->
            val r = items.optJSONObject(i) ?: return@forEach
            val type = r.optString("type")
            val summary = r.optString("summary")
            if (summary.isNotBlank()) parts += "$type: $summary"
        }
        return parts.joinToString(", ").takeIf { it.isNotBlank() }
    }

    /** Load known events (all time, for My Sports historical view). */
    private suspend fun loadKnown(): List<SportsEvent> {
        // For now, just return current events. A full implementation would cache more.
        return events.value
    }

    /** Load standings for followed leagues that have tables. */
    private fun loadStandings() {
        scope.launch {
            val active = activeLeagues(settings.value, follows.value).filter { SportsCatalog.mayHaveTable(it) }
            val results = active.map { league ->
                scope.async { loadLeagueStandings(league) }
            }.awaitAll()
            val map = results.filterNotNull().associateBy({ it.leagueId }, { it })
            standings.value = map
        }
    }

    /** Load standings for a single league. */
    private suspend fun loadLeagueStandings(league: League): SportsStandings.StandingsView? {
        val path = league.espnPath ?: return null
        val url = "https://site.api.espn.com/apis/site/v2/sports/$path/standings"
        val raw = runCatching { Http.getStringQuiet(url) }.getOrNull() ?: return null
        val parsed = parseStandings(league, raw) ?: return null
        return SportsStandings.StandingsView(
            leagueId = league.id,
            kind = SportsStandings.StandingsKind.REGULAR,
            tables = listOf(parsed),
            asOf = SportsDates.localIsoDate()
        )
    }

    /** Parse ESPN standings JSON. */
    private fun parseStandings(league: League, raw: String): SportsStandings.StandingsTable? {
        val o = JSONObject(raw)
        val children = o.optJSONArray("children") ?: return null
        val rows = mutableListOf<SportsStandings.StandingsRow>()
        
        (0 until children.length()).forEach { i ->
            val group = children.optJSONObject(i) ?: return@forEach
            val standings = group.optJSONObject("standings")?.optJSONArray("entries") ?: return@forEach
            (0 until standings.length()).forEach { j ->
                val entry = standings.optJSONObject(j) ?: return@forEach
                val teamObj = entry.optJSONObject("team")?.optJSONObject("team") ?: entry.optJSONObject("team")
                val teamInfo = parseTeamInfo(teamObj, league)
                val stats = entry.optJSONArray("stats") ?: return@forEach
                var played = 0; var won = 0; var lost = 0; var tied = 0; var otl = 0; var pts = 0; var pct = 0f; var gf = 0; var ga = 0; var gd = 0
                (0 until stats.length()).forEach { k ->
                    val stat = stats.optJSONObject(k) ?: return@forEach
                    val name = stat.optString("name").lowercase()
                    val v = stat.optString("value")
                    when (name) {
                        "gamesplayed" -> played = v.toIntOrNull() ?: 0
                        "wins" -> won = v.toIntOrNull() ?: 0
                        "losses" -> lost = v.toIntOrNull() ?: 0
                        "ties" -> tied = v.toIntOrNull() ?: 0
                        "otlosses" -> otl = v.toIntOrNull() ?: 0
                        "points" -> pts = v.toIntOrNull() ?: 0
                        "winpercent" -> pct = v.toFloatOrNull() ?: 0f
                        "pointsfor", "runsfor" -> gf = v.toIntOrNull() ?: 0
                        "pointsagainst", "runsagainst" -> ga = v.toIntOrNull() ?: 0
                        "pointdifferential", "rundifferential" -> gd = v.toIntOrNull() ?: 0
                    }
                }
                rows += SportsStandings.StandingsRow(
                    team = teamInfo, played = played, won = won, lost = lost, tied = tied, otl = otl,
                    gf = gf, ga = ga, gd = gd, pts = pts, pct = pct
                )
            }
        }
        return if (rows.isNotEmpty()) SportsStandings.StandingsTable(name = "", rows = rows) else null
    }

    /** Parse TeamInfo from ESPN team object. */
    private fun parseTeamInfo(o: JSONObject?, league: League): TeamInfo {
        val id = o?.optString("id") ?: ""
        val name = o?.optString("shortDisplayName")?.takeIf { it.isNotBlank() } ?: o?.optString("name") ?: ""
        val fullName = o?.optString("displayName") ?: name
        val abbr = o?.optString("abbreviation") ?: name.take(3).uppercase()
        val location = o?.optString("location")?.takeIf { it.isNotBlank() }
        val color = o?.optString("color")?.let { it.toLongOrNull() }
        val altColor = o?.optString("alternateColor")?.let { it.toLongOrNull() }
        val logo = o?.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotBlank() }
        return TeamInfo(league.id, id, name, fullName, abbr, location, color, altColor, logo)
    }

    // ---- Team picker (load teams for a league, cached) ----

    private val teamCache = java.util.concurrent.ConcurrentHashMap<String, List<TeamInfo>>()

    /** Load (or fetch) the teams for a league. Returns empty list when the league has no ESPN path. */
    suspend fun teamsForLeague(league: League): List<TeamInfo> {
        teamCache[league.id]?.let { return it }
        val path = league.espnPath ?: return emptyList()
        return try {
            val raw = Http.getStringQuiet("https://site.api.espn.com/apis/site/v2/sports/$path/teams?limit=1000") ?: return emptyList()
            val parsed = parseTeams(league, raw)
            teamCache[league.id] = parsed
            parsed
        } catch (e: Exception) {
            android.util.Log.e("Hikari/Sports", "Failed to load teams for ${league.id}", e)
            emptyList()
        }
    }

    private fun parseTeams(league: League, raw: String): List<TeamInfo> {
        if (raw.isBlank()) return emptyList()
        val o = JSONObject(raw)
        val teamsArr = o.optJSONArray("teams") ?: return emptyList()
        val out = mutableListOf<TeamInfo>()
        (0 until teamsArr.length()).forEach { i ->
            val t = teamsArr.optJSONObject(i) ?: return@forEach
            val id = t.optString("id")
            if (id.isBlank()) return@forEach
            val name = t.optString("shortDisplayName").takeIf { it.isNotBlank() } ?: t.optString("name")
            val fullName = t.optString("displayName")
            val abbr = t.optString("abbreviation")
            val location = t.optString("location").takeIf { it.isNotBlank() }
            val color = t.optString("color").let { if (it.isNotBlank()) it.toLongOrNull(16) else null }
            val altColor = t.optString("alternateColor").let { if (it.isNotBlank()) it.toLongOrNull(16) else null }
            val logo = t.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.isNotBlank() }
            out += TeamInfo(league.id, id, name, fullName, abbr, location, color, altColor, logo)
        }
        return out
    }

    // ---- Active leagues computation ----

    /** Active leagues based on settings and follows. */
    fun activeLeagues(settings: SportsSettings, follows: SportsFollows): List<League> {
        val base = SportsCatalog.ordered(settings)
        val region = settings.region.ifBlank { SportsPlatform.countryCode() }.uppercase()
        val followedLeagues = follows.leagues + follows.teams.map { SportsCatalog.teamFamily(it.leagueId) }.distinct()
        val pinned = follows.pins
        val unpinned = follows.unpinned
        
        return base.filter { league ->
            !settings.hiddenLeagues.contains(league.id) &&
            (league.id in followedLeagues || league.id in pinned || league.regions.contains(region) || league.sport in SportsCatalog.sportsOrder(settings)) &&
            league.id !in unpinned
        }
    }

    /** Days back from today that the user can scroll. */
    suspend fun wantPast() {
        // No-op for now - would extend DAYS_BACK
    }

    /** Start of day (device time) for a day offset. */
    fun dayStart(offset: Int): Long = SportsDates.dayStart(offset)
}