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
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central state holder for the Sports hub.
 * Handles settings, follows, events, matches, and standings.
 */
object SportsRepository {

    // ---- Constants ----
    const val DAYS_BACK = 7
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

    /** Every channel the last match ran against, for "watch on…" lookups. */
    val channels = MutableStateFlow<List<com.hikari.app.data.IptvChannel>>(emptyList())

    /** eventId -> the scoring plays, fetched on demand by the game page. */
    private val summaryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<ScoringPlay>>>()

    /**
     * TheSportsDB leagues that arrive with the nuvio-data index, and the games
     * already read from their files. A file is a whole season; re-reading it on
     * every 15-minute refresh would be a lot of traffic for the same fixtures,
     * so idle leagues are kept for half an hour and live ones for four minutes.
     */
    @Volatile private var tsdbIndexAt = 0L
    private val tsdbLeagueCache =
        java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<SportsEvent>>>()

    private val viewing = MutableStateFlow(false)
    private val refreshJob = SupervisorJob()
    private val scope = CoroutineScope(refreshJob + Dispatchers.IO)
    private val mutex = Mutex()
    private var initialized = false
    private var context: Context? = null
    private var lastRefreshAt = 0L

    // ---- Persistence ----
    private lateinit var store: DataStore<Preferences>
    private val SETTINGS_PREF_KEY = stringPreferencesKey(SETTINGS_KEY)
    private val FOLLOWS_PREF_KEY = stringPreferencesKey(FOLLOWS_KEY)

    private val Context.sportsDataStore by preferencesDataStore(name = "sports", corruptionHandler = com.hikari.app.data.ReplaceCorruptPreferences)

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

    /**
     * Loads the fixture when the sports screen is opened if the startup fetch
     * never landed (the app starts it at launch, but that request can fail while
     * the network is still coming up). Without this the tab stayed on "No games"
     * until the user restarted Hikari or pressed Refresh.
     */
    fun ensureFresh(maxAgeMs: Long = 5 * 60_000L) {
        if (loading.value) return
        val now = LiveTvClock.nowMs()
        if (events.value.isEmpty() || lastRefreshAt == 0L || now - lastRefreshAt > maxAgeMs) {
            refreshNow()
        }
    }

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
                lastRefreshAt = LiveTvClock.nowMs()
                channels.value.takeIf { it.isNotEmpty() }?.let { chans ->
                    matches.value = SportsMatcher.match(newEvents, chans)
                }
                loadStandings()
            } catch (e: Exception) {
                problem.value = "Failed to load: ${e.message}"
            } finally {
                loading.value = false
                mutex.unlock()
            }
        }
    }

    /**
     * Gather the user's channels (IPTV playlists plus the ones imported from
     * Extensions/addons) and match them to the events. The Sports tab can be
     * opened before the IPTV tab has ever loaded, so it asks for them itself.
     */
    fun ensureChannels(ctx: Context, force: Boolean = false) {
        if (!force && channels.value.isNotEmpty()) return
        scope.launch {
            val app = ctx.applicationContext as? com.hikari.app.HikariApp ?: return@launch
            val playlists = app.providers.providers.value
                .filterIsInstance<com.hikari.app.providers.IptvProvider>()
            if (playlists.isEmpty()) return@launch
            val list = com.hikari.app.providers.gatherIptvChannels(playlists, force)
            if (list.isEmpty()) return@launch
            channels.value = list
            matching.value = true
            try {
                matches.value = SportsMatcher.match(events.value, list)
            } finally {
                matching.value = false
            }
        }
    }

    /** Set viewing state (for auto-refresh). */
    fun setViewing(v: Boolean) {
        viewing.value = v
        // Opening the tab right after start-up must not repeat the refresh the
        // init already ran; anything older than a couple of minutes does.
        if (v && (events.value.isEmpty() || LiveTvClock.nowMs() - lastRefreshAt > 2 * 60_000L)) {
            refreshNow()
        }
    }

    /** Match current events to the given IPTV channels. */
    fun matchChannels(channels: List<com.hikari.app.data.IptvChannel>) {
        if (channels.isEmpty()) return
        this.channels.value = channels
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

    /**
     * The scoring plays for [event], newest page first. Cached for a few
     * minutes while a game is live, much longer once it is over.
     */
    suspend fun summaryFor(event: SportsEvent): List<ScoringPlay> {
        val league = SportsCatalog.league(event.leagueId) ?: return emptyList()
        val espnId = event.espnId ?: return emptyList()
        val cached = summaryCache[event.id]
        val ttl = if (event.state == GameState.LIVE) 30_000L else 10 * 60_000L
        if (cached != null && LiveTvClock.nowMs() - cached.first < ttl) return cached.second
        val plays = runCatching { SportsFeeds.summary(league, espnId) }.getOrDefault(emptyList())
        summaryCache[event.id] = LiveTvClock.nowMs() to plays
        return plays
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
        // The extra competitions (non-league football, rugby league, racing,
        // netball…) come from the nuvio-data index. Load it before choosing the
        // active leagues, otherwise they are not in the catalog yet and their
        // games never appear.
        ensureTsdbIndex()
        val activeLeagues = activeLeagues(settings.value, follows.value)
        android.util.Log.d("Hikari/Sports", "Loading events for ${activeLeagues.size} active leagues: ${activeLeagues.joinToString { it.id }}")
        val allEvents = mutableListOf<SportsEvent>()

        // Load scoreboards for each active league in parallel, but not all at
        // once: ESPN starts refusing requests when a whole region's leagues are
        // fired simultaneously.
        val gate = kotlinx.coroutines.sync.Semaphore(6)
        coroutineScope {
            activeLeagues.map { league ->
                async {
                    gate.withPermit {
                        try {
                            val evs = loadLeagueEvents(league)
                            android.util.Log.d("Hikari/Sports", "Loaded ${evs.size} events for ${league.id}")
                            evs
                        } catch (e: Exception) {
                            android.util.Log.e("Hikari/Sports", "Failed to load events for ${league.id}", e)
                            emptyList()
                        }
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

    /**
     * Reads the nuvio-data index (twice a day) and adds its leagues to the
     * catalog. That file is what brings in the competitions the built-in list
     * does not carry: English non-league football, Scottish League One/Two,
     * rugby league's Super League, MotoGP/Formula E/WEC and friends.
     */
    private suspend fun ensureTsdbIndex() {
        val now = LiveTvClock.nowMs()
        val twelveHours = 12 * 60 * 60_000L
        if (tsdbIndexAt != 0L && now - tsdbIndexAt < twelveHours) return
        val raw = runCatching { SportsFeeds.footballIndexRaw() }.getOrNull()
        if (raw != null) {
            val parsed = runCatching { SportsFeeds.parseFootballIndex(raw) }.getOrNull().orEmpty()
            if (parsed.isNotEmpty()) {
                // Aliases already have a richer schedule feed (WWE/AEW/boxing):
                // adding a second tsdb copy would duplicate every card.
                SportsCatalog.setExtra(parsed.filterNot { it.alias }.map { it.league })
                tsdbIndexAt = now
                android.util.Log.d(
                    "Hikari/Sports",
                    "TheSportsDB index: ${parsed.count { !it.alias }} extra leagues",
                )
                return
            }
        }
        // Offline or the file is briefly unavailable: retry in half an hour
        // instead of on every 15-minute refresh.
        tsdbIndexAt = now - twelveHours + 30 * 60_000L
    }

    /**
     * One TheSportsDB league's fixtures. The file is cached in memory with a
     * short TTL while its games are live or about to start, and a long one
     * otherwise.
     */
    private suspend fun loadTsdbLeagueEvents(league: League): List<SportsEvent> {
        val now = LiveTvClock.nowMs()
        val cached = tsdbLeagueCache[league.id]
        val busy = cached?.second?.any { ev ->
            ev.state == GameState.LIVE ||
                (ev.state == GameState.PRE &&
                    ev.startMs in (now - 20 * 60_000L)..(now + 15 * 60_000L))
        } == true
        val maxAge = if (busy) 4 * 60_000L else 30 * 60_000L
        if (cached != null && now - cached.first < maxAge) return cached.second
        val raw = runCatching {
            SportsFeeds.footballLeagueRaw(league.id.removePrefix("tsdb/"))
        }.getOrNull() ?: return cached?.second.orEmpty()
        // Only build the games the page can show: a whole-season file is
        // hundreds of events, and the other 90% were discarded two lines later
        // by the repository's own window filter.
        val from = SportsDates.dayStart(-DAYS_BACK - 1, now)
        val to = SportsDates.dayStart(DAYS_AHEAD + 1, now)
        val events = runCatching { SportsFeeds.parseFootballLeague(league, raw, now, from, to) }
            .getOrDefault(emptyList())
        tsdbLeagueCache[league.id] = now to events
        return events
    }

    /** Load events for a single league. */
    private suspend fun loadLeagueEvents(league: League): List<SportsEvent> {
        if (league.guideOnly) return loadGuideOnlyEvents(league)
        if (league.id.startsWith("tsdb/")) return loadTsdbLeagueEvents(league)
        if (league.espnPath == null) return emptyList()

        // One range request covers the whole sports window. Some ESPN paths
        // answer a long range with an error, so a failed/empty range falls back
        // to the three days around today.
        val from = SportsDates.dayKey(SportsDates.dayStart(-DAYS_BACK))
        val to = SportsDates.dayKey(SportsDates.dayStart(DAYS_AHEAD))
        val range = runCatching { SportsFeeds.scoreboardRaw(league, from, to) }.getOrNull()
        val ranged = range?.let { parseScoreboard(league, it) }.orEmpty()
        if (ranged.isNotEmpty()) return ranged

        val days = (-1..3).map { SportsDates.dayKey(SportsDates.dayStart(it)) }
        val out = mutableListOf<SportsEvent>()
        coroutineScope {
            days.map { day ->
                async {
                    try {
                        val raw = SportsFeeds.scoreboardDayRaw(league, day)
                        parseScoreboard(league, raw)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().forEach { out.addAll(it) }
        }
        return out.distinctBy { it.id }
    }

    /** Load guide-only events (BKFC, darts, snooker, racing, wrestling) from their feeds. */
    private suspend fun loadGuideOnlyEvents(league: League): List<SportsEvent> {
        return when {
            league.id == "guide/bkfc" -> loadBkfcEvents(league)
            // The nuvio-data wrestling file carries boxing's cards too (it is
            // built from TheSportsDB's boxing league); the older sporting-
            // events file is only a fallback for when that feed is unavailable.
            league.id == "guide/boxing" -> loadWrestlingEvents(league).ifEmpty { loadFixtureEvents(league) }
            league.id.startsWith("guide/wwe") || league.id.startsWith("guide/aew") ||
                league.id == "guide/wrestling" -> loadWrestlingEvents(league)
            league.fixtureSlug != null -> loadFixtureEvents(league)
            else -> emptyList()
        }
    }

    /**
     * BKFC's own calendar (api.bkfc.com/events). It carries the poster, venue
     * and platform for each card, which is everything the sports page needs.
     */
    private suspend fun loadBkfcEvents(league: League): List<SportsEvent> {
        val raw = runCatching { SportsFeeds.bkfcEventsRaw() }.getOrNull() ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val now = LiveTvClock.nowMs()
        val out = mutableListOf<SportsEvent>()
        (0 until arr.length()).forEach { i ->
            val e = arr.optJSONObject(i) ?: return@forEach
            val startMs = SportsDates.parseIso(
                e.optString("main_card_start_time").ifBlank { e.optString("event_date") },
            ) ?: return@forEach
            val title = e.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "BKFC event"
            val state = when {
                now < startMs -> GameState.PRE
                now in startMs..(startMs + 5 * 60 * 60_000L) -> GameState.LIVE
                else -> GameState.POST
            }
            out += SportsEvent(
                id = "bkfc-" + e.optString("id").ifBlank { startMs.toString() },
                leagueId = league.id,
                startMs = startMs,
                hasTime = true,
                state = state,
                title = title,
                subtitle = e.optString("location").ifBlank { e.optString("venue_name") }
                    .takeIf { it.isNotBlank() && it != "null" },
                status = when (state) {
                    GameState.LIVE -> "Live"
                    GameState.POST -> "Final"
                    else -> "Upcoming"
                },
                away = null,
                home = null,
                source = EventSource.FIXTURES,
                espnId = null,
                note = e.optString("platform").takeIf { it.isNotBlank() && it != "null" },
                about = e.optString("description").takeIf { it.isNotBlank() && it != "null" },
                image = e.optString("main_image_url").ifBlank { e.optString("hero_image_url") }
                    .takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    /** sporting-events.org fixtures: title, start and (when given) a live flag. */
    private suspend fun loadFixtureEvents(league: League): List<SportsEvent> {
        val raw = runCatching { SportsFeeds.fixturesRaw(league.fixtureSlug!!) }.getOrNull() ?: return emptyList()
        val o = JSONObject(raw)
        val events = o.optJSONArray("events") ?: return emptyList()
        val out = mutableListOf<SportsEvent>()
        (0 until events.length()).forEach { i ->
            val e = events.optJSONObject(i) ?: return@forEach
            val startMs = SportsDates.parseIso(e.optString("start")) ?: return@forEach
            val title = e.optString("title").takeIf { it.isNotBlank() } ?: return@forEach
            val state = when {
                startMs > LiveTvClock.nowMs() -> GameState.PRE
                e.optBoolean("live", false) -> GameState.LIVE
                else -> GameState.POST
            }
            out += SportsEvent(
                id = "fixture-${league.id}-${e.optString("id").ifBlank { startMs.toString() }}",
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
                espnId = null,
                about = e.optString("description").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    /** Load wrestling events from TheSportsDB (through the nuvio-data copy). */
    private suspend fun loadWrestlingEvents(league: League): List<SportsEvent> {
        val raw = SportsFeeds.wrestlingRaw()
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val events = o.optJSONArray("events") ?: return emptyList()
        val out = mutableListOf<SportsEvent>()
        (0 until events.length()).forEach { i ->
            val e = events.optJSONObject(i) ?: return@forEach
            // Two shapes are supported: the raw TheSportsDB fields (strEvent…)
            // and the compact nuvio-data file (league/name/start).
            val compact = e.has("league") && e.has("start")
            val title = if (compact) e.optString("name") else e.optString("strEvent")
            if (title.isBlank()) return@forEach
            val leagueId = if (compact) {
                when (e.optString("league").lowercase()) {
                    "wwe" -> "guide/wwe"
                    "aew" -> "guide/aew"
                    "tna" -> "guide/wrestling"
                    "boxing" -> "guide/boxing"
                    else -> "guide/wrestling"
                }
            } else {
                val lower = title.lowercase()
                when {
                    listOf(
                        "wwe", "smackdown", "raw", "nxt", "wrestlemania",
                        "royal rumble", "summerslam", "survivor series",
                    ).any { it in lower } -> "guide/wwe"
                    "aew" in lower || "all elite" in lower -> "guide/aew"
                    else -> "guide/wrestling"
                }
            }
            if (leagueId != league.id) return@forEach
            val startMs = if (compact) {
                val hasTime = e.optBoolean("hasTime", true)
                if (hasTime) SportsDates.parseIso(e.optString("start"))
                else SportsDates.localDateOf(e.optString("start"))
            } else {
                val dateStr = e.optString("dateEvent")
                val timeStr = e.optString("strTime")
                SportsDates.parseIso("$dateStr${if (timeStr.isNotBlank()) "T$timeStr" else "T00:00"}Z")
            } ?: return@forEach
            val hasTime = if (compact) e.optBoolean("hasTime", true) else e.optString("strTime").isNotBlank()
            val end = startMs + league.minutes * 60_000L
            val state = when {
                !hasTime -> GameState.PRE
                LiveTvClock.nowMs() >= end -> GameState.POST
                compact && e.optString("strStatus") == "Live" -> GameState.LIVE
                !compact && e.optString("strStatus") == "Live" -> GameState.LIVE
                LiveTvClock.nowMs() >= startMs -> GameState.LIVE
                else -> GameState.PRE
            }
            out += SportsEvent(
                id = if (compact) "tsdb-${e.optString("id")}" else "wrestling-${e.optString("idEvent")}",
                leagueId = league.id,
                startMs = startMs,
                hasTime = hasTime,
                state = state,
                title = wrestlingHeadline(title),
                subtitle = if (compact) {
                    listOf(
                        e.optString("venue"),
                        e.optString("city"),
                        e.optString("country"),
                    ).filter { it.isNotBlank() && it != "null" }.joinToString(", ").ifBlank { null }
                } else {
                    null
                },
                status = if (state == GameState.LIVE) "Live"
                else if (state == GameState.POST) "Final" else "Upcoming",
                away = null,
                home = null,
                source = EventSource.FIXTURES,
                espnId = null,
                about = (if (compact) e.optString("about") else e.optString("strDescriptionEN"))
                    .takeIf { it.isNotBlank() && it != "null" },
                endMs = end,
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

        (0 until events.length()).forEach { i ->
            val e = events.optJSONObject(i) ?: return@forEach
            val eventId = e.optString("id")
            val comps = e.optJSONArray("competitions") ?: return@forEach
            if (comps.length() == 0) return@forEach
            // A team sport's event is one game (competition[0]). A fight card
            // (UFC, PFL, boxing) is one event with one competition per bout, so
            // every bout gets its own row on the Sports page.
            val count = if (league.kind == LeagueKind.EVENT) comps.length() else 1
            for (ci in 0 until count) {
                val comp = comps.optJSONObject(ci) ?: continue
                parseCompetition(league, e, comp, eventId, ci)?.let { out += it }
            }
        }
        return out
    }

    /** One competition (a game, or one fight on a card) -> a SportsEvent. */
    private fun parseCompetition(
        league: League,
        event: JSONObject,
        competition: JSONObject,
        eventId: String,
        index: Int,
    ): SportsEvent? {
        val dateStr = competition.optString("date").ifBlank { event.optString("date") }
        val startMs = SportsDates.parseIso(dateStr) ?: return null
        val hasTime = dateStr.contains('T')

        val status = competition.optJSONObject("status") ?: event.optJSONObject("status") ?: return null
        val statusType = status.optJSONObject("type")
        val explicitState = statusType?.optString("state")?.lowercase().orEmpty()
        val stateName = statusType?.optString("name")?.lowercase().orEmpty()
        val state = when {
            // ESPN's cricket (and some other) feeds only carry the short
            // "pre"/"in"/"post" state, with no name at all. Without this every
            // cricket match was classified OFF and vanished from the page.
            explicitState == "pre" -> GameState.PRE
            explicitState == "in" -> GameState.LIVE
            explicitState == "post" -> GameState.POST
            stateName.contains("scheduled") || stateName.contains("pre") || stateName.contains("tbd") -> GameState.PRE
            stateName.contains("progress") || stateName.contains("live") || stateName.contains("halftime") ||
                stateName.contains("overtime") || stateName.contains("shootout") || stateName.contains("delayed") -> GameState.LIVE
            stateName.contains("final") || stateName.contains("full_time") || stateName.contains("full-time") ||
                stateName.contains("completed") || stateName.contains("fulltime") -> GameState.POST
            stateName.contains("postponed") || stateName.contains("cancel") || stateName.contains("suspend") -> GameState.OFF
            else -> GameState.OFF
        }

        val competitors = competition.optJSONArray("competitors") ?: return null
        val ordered = (0 until competitors.length()).mapNotNull { competitors.optJSONObject(it) }
            .sortedBy { it.optInt("order", 0) }
        if (ordered.size < 2) return null
        val away = parseSide(ordered[0], league, true, competition) ?: return null
        val home = parseSide(ordered[1], league, false, competition) ?: return null

        val broadcasts = mutableListOf<String>()
        competition.optJSONArray("broadcasts")?.let { bc ->
            (0 until bc.length()).forEach { j ->
                bc.optJSONObject(j)?.let { b ->
                    b.optJSONObject("media")?.optString("shortName")?.takeIf { it.isNotBlank() }?.let { broadcasts += it }
                    b.optJSONArray("names")?.let { names ->
                        (0 until names.length()).forEach { k ->
                            names.optString(k).takeIf { it.isNotBlank() }?.let { broadcasts += it }
                        }
                    }
                }
            }
        }

        val weight = competition.optJSONObject("type")?.optString("abbreviation").orEmpty()
            .takeIf { it.isNotBlank() && it.length <= 30 }
        val eventName = event.optString("name").takeIf { it.isNotBlank() }
        val shortName = event.optString("shortName").takeIf { it.isNotBlank() }
        val title = when {
            league.kind == LeagueKind.EVENT && away.team.name.isNotBlank() ->
                "${away.team.name} vs ${home.team.name}"
            shortName != null -> shortName
            else -> "${away.team.name} @ ${home.team.name}"
        }
        val subtitle = when {
            league.kind == LeagueKind.EVENT -> listOfNotNull(weight, eventName).joinToString(" · ").takeIf { it.isNotBlank() }
            else -> eventName
        }
        val id = if (league.kind == LeagueKind.EVENT) {
            "espn-$eventId-" + competition.optString("id").ifBlank { index.toString() }
        } else {
            "espn-$eventId"
        }
        val statusText = statusType?.optString("shortDetail")?.takeIf { it.isNotBlank() }
            ?: statusType?.optString("detail")?.takeIf { it.isNotBlank() }

        return SportsEvent(
            id = id,
            leagueId = league.id,
            startMs = startMs,
            hasTime = hasTime,
            state = state,
            title = title,
            subtitle = subtitle,
            status = statusText,
            away = away,
            home = home,
            broadcasts = broadcasts,
            source = EventSource.ESPN,
            espnId = eventId.takeIf { it.isNotBlank() },
            note = weight?.takeIf { league.kind != LeagueKind.EVENT },
        )
    }

    /** Parse one side (a team, or a fighter/athlete) from ESPN competition. */
    private fun parseSide(o: JSONObject?, league: League, isAway: Boolean, competition: JSONObject): Side? {
        if (o == null) return null
        val teamObj = o.optJSONObject("team")?.let { it.optJSONObject("team") ?: it }
        val athlete = o.optJSONObject("athlete")
        val info = teamObj ?: athlete ?: return null
        val isAthlete = teamObj == null && athlete != null

        val id = info.optString("id").ifBlank { o.optString("id") }
        if (id.isBlank()) return null
        val name = info.optString("shortDisplayName").ifBlank { info.optString("displayName") }
            .ifBlank { info.optString("shortName") }
            .ifBlank { info.optString("name") }
            .ifBlank { info.optString("fullName") }
        val fullName = info.optString("displayName").ifBlank { info.optString("fullName") }
            .ifBlank { name }
        val abbr = info.optString("abbreviation").ifBlank {
            if (isAthlete) {
                name.split(' ').lastOrNull()?.take(3)?.uppercase().orEmpty()
            } else {
                name.take(3).uppercase()
            }
        }
        val location = info.optString("location").takeIf { it.isNotBlank() }
        val color = info.optString("color").let { if (it.isNotBlank()) it.toLongOrNull(16) ?: 0xFF2A2F3A else null }
        val altColor = info.optString("alternateColor").let { if (it.isNotBlank()) it.toLongOrNull(16) else null }
        val logo = info.optJSONArray("logos")?.optJSONObject(0)?.optString("href")
            ?.takeIf { it.isNotBlank() }
            ?: info.optString("logo").takeIf { it.isNotBlank() && it != "null" }
        val record = recordOf(o)
        val score = o.optString("score").takeIf { it.isNotBlank() }
        val winner = o.optBoolean("winner") == true
        val periods = runCatching {
            val arr = o.optJSONArray("linescores") ?: return@runCatching emptyList<String>()
            (0 until arr.length()).mapNotNull { i ->
                val v = arr.optJSONObject(i)?.optString("displayValue").orEmpty()
                v.ifBlank { arr.optJSONObject(i)?.optString("value")?.takeIf { it.isNotBlank() } }
            }
        }.getOrDefault(emptyList())

        val team = TeamInfo(league.id, id, name, fullName, abbr, location, color, altColor, logo, record)
        return Side(team, score, record, winner, !isAway, periods)
    }

    /** A team's or fighter's record, from either of ESPN's shapes. */
    private fun recordOf(o: JSONObject): String? {
        o.optJSONArray("records")?.let { arr ->
            (0 until arr.length()).forEach { i ->
                val r = arr.optJSONObject(i) ?: return@forEach
                r.optString("summary").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        o.optJSONObject("records")?.let { return parseRecord(it.toString()) }
        return null
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

    /** Load known events: the current window plus each followed team's own schedule. */
    private suspend fun loadKnown(): List<SportsEvent> {
        val out = events.value.toMutableList()
        val followedTeams = follows.value.teams
        if (followedTeams.isEmpty()) return out
        coroutineScope {
            followedTeams.map { ft ->
                async {
                    runCatching { loadTeamSchedule(ft) }.getOrDefault(emptyList())
                }
            }.awaitAll().forEach { list ->
                val ids = out.mapTo(HashSet()) { it.id }
                list.forEach { if (ids.add(it.id)) out += it }
            }
        }
        return out.sortedBy { it.startMs }
    }

    /** A followed team's fixture list from ESPN (past and upcoming). */
    private suspend fun loadTeamSchedule(ft: FollowedTeam): List<SportsEvent> {
        val league = SportsCatalog.league(ft.leagueId) ?: return emptyList()
        val path = league.espnPath ?: return emptyList()
        val raw = runCatching { SportsFeeds.teamScheduleRaw(path, ft.teamId, fixtures = true) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { SportsFeeds.teamScheduleRaw(path, ft.teamId, fixtures = false) }
                .getOrNull()
            ?: return emptyList()
        return runCatching { parseScoreboard(league, raw) }.getOrDefault(emptyList())
    }

    /** Load standings for followed leagues that have tables. */
    private fun loadStandings() {
        scope.launch {
            val followed = (follows.value.leagues + follows.value.pins).distinct().toSet()
            val active = activeLeagues(settings.value, follows.value)
                .filter { SportsCatalog.mayHaveTable(it) }
                .filter { it.id in followed }
                .ifEmpty {
                    activeLeagues(settings.value, follows.value)
                        .filter { SportsCatalog.mayHaveTable(it) }
                        .take(4)
                }
            val results = active.map { league ->
                scope.async { loadLeagueStandings(league) }
            }.awaitAll()
            val map = results.filterNotNull().associateBy({ it.leagueId }, { it })
            standings.value = standings.value + map
        }
    }

    /** Load one league's table on demand (the league page). */
    suspend fun ensureStandings(leagueId: String) {
        if (standings.value.containsKey(leagueId)) return
        val league = SportsCatalog.league(leagueId) ?: return
        if (!SportsCatalog.mayHaveTable(league)) return
        val view = runCatching { withContext(Dispatchers.IO) { loadLeagueStandings(league) } }.getOrNull() ?: return
        standings.value = standings.value + (leagueId to view)
    }

    /** Load standings for a single league. */
    private suspend fun loadLeagueStandings(league: League): SportsStandings.StandingsView? {
        val path = league.espnPath ?: return null
        val divisions = path in setOf("football/nfl", "basketball/nba", "hockey/nhl", "baseball/mlb")
        val url = "https://site.api.espn.com/apis/v2/sports/$path/standings" + if (divisions) "?level=3" else ""
        val raw = runCatching { SportsPlatform.get(url) }.getOrNull() ?: return null
        val tables = parseStandingsTables(league, raw)
        if (tables.isEmpty()) return null
        return SportsStandings.StandingsView(
            leagueId = league.id,
            kind = SportsStandings.StandingsKind.REGULAR,
            tables = tables,
            asOf = SportsDates.localIsoDate()
        )
    }

    /** Parse ESPN standings JSON into all the tables it carries (conferences, divisions, groups). */
    private fun parseStandingsTables(league: League, raw: String): List<SportsStandings.StandingsTable> {
        val o = JSONObject(raw)
        val tables = mutableListOf<SportsStandings.StandingsTable>()

        fun parseEntries(group: JSONObject): List<SportsStandings.StandingsRow> {
            val entries = group.optJSONObject("standings")?.optJSONArray("entries") ?: return emptyList()
            return (0 until entries.length()).mapNotNull { j ->
                val entry = entries.optJSONObject(j) ?: return@mapNotNull null
                val teamObj = entry.optJSONObject("team")?.optJSONObject("team") ?: entry.optJSONObject("team")
                val teamInfo = parseTeamInfo(teamObj, league)
                if (teamInfo.id.isBlank()) return@mapNotNull null
                val stats = entry.optJSONArray("stats")
                fun stat(vararg names: String): Int {
                    if (stats == null) return 0
                    for (k in 0 until stats.length()) {
                        val stat = stats.optJSONObject(k) ?: continue
                        val name = stat.optString("name").lowercase()
                        if (name in names) {
                            return stat.optString("value").toDoubleOrNull()?.toInt()
                                ?: stat.optString("displayValue").toIntOrNull() ?: 0
                        }
                    }
                    return 0
                }
                SportsStandings.StandingsRow(
                    team = teamInfo,
                    played = stat("gamesplayed", "games", "gp"),
                    won = stat("wins", "w"),
                    lost = stat("losses", "l"),
                    tied = stat("ties", "t", "draws", "d"),
                    otl = stat("otlosses", "otl"),
                    gf = stat("pointsfor", "runsfor", "goalsfor", "pf", "gf"),
                    ga = stat("pointsagainst", "runsagainst", "goalsagainst", "pa", "ga"),
                    gd = stat("pointdifferential", "rundifferential", "goaldifferential", "gd"),
                    pts = stat("points", "pts"),
                    pct = stat("winpercent", "pct").toFloat(),
                    streak = null,
                )
            }
        }

        val children = o.optJSONArray("children")
        if (children != null && children.length() > 0) {
            fun walk(node: JSONObject) {
                val rows = parseEntries(node)
                if (rows.isNotEmpty()) {
                    tables += SportsStandings.StandingsTable(
                        name = node.optString("name").takeIf { it.isNotBlank() } ?: "",
                        rows = rows,
                    )
                    return
                }
                val kids = node.optJSONArray("children") ?: return
                for (i in 0 until kids.length()) kids.optJSONObject(i)?.let { walk(it) }
            }
            for (i in 0 until children.length()) children.optJSONObject(i)?.let { walk(it) }
        }
        if (tables.isEmpty()) {
            val rows = parseEntries(o)
            if (rows.isNotEmpty()) tables += SportsStandings.StandingsTable(name = "", rows = rows)
        }
        return tables
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
            val raw = SportsPlatform.get("https://site.api.espn.com/apis/site/v2/sports/$path/teams?limit=1000")
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
        val teamsArr = o.optJSONArray("teams")
            ?: o.optJSONArray("sports")?.optJSONObject(0)
                ?.optJSONArray("leagues")?.optJSONObject(0)
                ?.optJSONArray("teams")
            ?: return emptyList()
        val out = mutableListOf<TeamInfo>()
        (0 until teamsArr.length()).forEach { i ->
            val row = teamsArr.optJSONObject(i) ?: return@forEach
            // ESPN nests the team in a "team" object in this endpoint.
            val t = row.optJSONObject("team") ?: row
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
        val followedLeagues = (
            follows.leagues +
                follows.teams.map { SportsCatalog.teamFamily(it.leagueId) } +
                follows.leagues.flatMap { SportsCatalog.relatedLeagues(it) } +
                follows.teams.flatMap { SportsCatalog.relatedLeagues(it.leagueId) }
            ).distinct()
        val pinned = follows.pins
        val unpinned = follows.unpinned
        return SportsCatalog.ordered(settings).filter { league ->
            if (league.id in unpinned) return@filter false
            val mine = league.id in followedLeagues || league.id in pinned
            mine || SportsCatalog.shown(league, settings)
        }
    }

    /** Days back from today that the user can scroll. */
    suspend fun wantPast() {
        // No-op for now - would extend DAYS_BACK
    }

    /** Start of day (device time) for a day offset. */
    fun dayStart(offset: Int): Long = SportsDates.dayStart(offset)
}