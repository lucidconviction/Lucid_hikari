package com.hikari.app.sports

import com.hikari.app.HikariApp
import com.hikari.app.data.CatalogRef
import com.hikari.app.data.MediaItem
import com.hikari.app.data.ProviderType
import com.hikari.app.data.StreamSource
import com.hikari.app.providers.ContentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The live sports half of the Sports tab: events published by the Stremio
 * add-ons the user installed.
 *
 * This is the same idea as the reference TV app's sports guide, re-pointed at
 * Hikari's own provider contract. A sports add-on is an ordinary Stremio add-on
 * whose manifest publishes event catalogs ("Live Events", "Today", "NBA", …);
 * its catalog rows carry an id, a title and usually a backdrop, and its
 * `/stream` resource answers for that id. The add-on protocol is already fully
 * implemented by [com.hikari.app.providers.StremioAddon], so nothing here
 * speaks HTTP directly — it reads the provider the user already installed and
 * keeps all of its configuration, headers and playback rules intact.
 *
 * Why this exists next to [SportsRepository] (ESPN/TheSportsDB): the score
 * feeds know the fixture, but they do not know which source carries it. A
 * sports add-on does. The Live tab shows those events and puts their streams
 * one tap away; the existing "Watch on" search still finds the same event in
 * every other extension.
 */
object SportsAddons {

    /** One live/upcoming event as an installed add-on publishes it. */
    data class Event(
        val key: String,
        val providerId: String,
        val providerName: String,
        val catalogName: String,
        /** The catalog row, kept whole so its addon can answer `/stream`. */
        val item: MediaItem,
        val title: String,
        val league: String?,
        val sport: Sport?,
        /** Event start in ms, or null when the add-on did not publish a time. */
        val startMs: Long?,
        /** The add-on says the event is on air now (or its catalog is a live one). */
        val live: Boolean,
        /** When the live flag was observed, so a stale "LIVE" cannot stick forever. */
        val observedAt: Long,
        val artwork: String?,
        val description: String?,
    ) {
        /**
         * A live badge is only trustworthy for fifteen minutes: catalogs refresh
         * on a timer, and an event that ended ten minutes ago must stop claiming
         * to be live before the next refresh lands.
         */
        fun isLiveNow(now: Long = System.currentTimeMillis()): Boolean {
            if (!live || now - observedAt !in -60_000L..15 * 60_000L) return false
            return startMs == null || startMs <= now + 15 * 60_000L
        }

        /** Live events first, then by start time; unknown times after dated rows. */
        fun sortKey(now: Long): Long =
            if (isLiveNow(now)) Long.MIN_VALUE else startMs ?: Long.MAX_VALUE
    }

    /** Add-ons whose event catalogs were read — named so the empty state can say
     *  exactly what was scanned instead of only "nothing found". */
    val scannedAddons = MutableStateFlow<List<String>>(emptyList())
    val events = MutableStateFlow<List<Event>>(emptyList())
    val loading = MutableStateFlow(false)
    val problem = MutableStateFlow<String?>(null)

    private const val FRESH_MS = 3 * 60_000L
    private const val TOTAL_BUDGET_MS = 20_000L
    private const val MANIFEST_TIMEOUT_MS = 5_000L
    private const val CATALOG_TIMEOUT_MS = 6_000L
    private const val STREAM_TIMEOUT_MS = 15_000L
    private const val MAX_CATALOGS_PER_ADDON = 3
    private const val MAX_EVENTS = 400

    private val mutex = Mutex()
    @Volatile private var lastLoadAt = 0L
    /** False when the previous pass had no add-on candidates at all (the
     *  provider list was still loading); the 30-second retry brake must not
     *  cover that case. */
    @Volatile private var lastHadCandidates = false
    /** The installed-provider fingerprint of the last pass. A newly installed
     *  or removed add-on bypasses the short retry brake below. */
    @Volatile private var lastProviderSignature: String? = null

    /** Catalogs are expensive to reach (a manifest fetch), so they are kept for
     *  ten minutes per installed add-on. The provider id is stable across
     *  provider-list rebuilds; the URL is part of the key so a replaced add-on
     *  with the same id cannot inherit stale catalogs. */
    private data class CatalogCache(val at: Long, val url: String, val refs: List<CatalogRef>)
    private val catalogCache = ConcurrentHashMap<String, CatalogCache>()

    private val sportWords = listOf(
        "sport", "football", "soccer", "basketball", "tennis", "motorsport",
        "formula", "racing", "rugby", "hockey", "baseball", "boxing", "ufc",
        "mma", "cricket", "golf", "darts", "snooker", "afl", "nfl", "nba",
        "mlb", "nhl", "volleyball", "handball", "cycling", "netball",
    )
    private val liveCatalogWords = Regex("\\b(live|today|on now|right now|current)\\b", RegexOption.IGNORE_CASE)
    private val liveWord = Regex(
        "\\b(live|live now|on air|streaming now|watch live|in play)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val terminalWord = Regex(
        "\\b(replay|re-run|rerun|highlights?|classic|finished|ended|cancelled|canceled|" +
            "postponed|abandoned|full match replay|recap|podcast|preview show)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val excludedCatalog = Regex(
        "\\b(replay|replays|highlights?|network|24/7|24_7|radio|podcast|documentary)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val statusSymbols = Regex("^[^\\p{L}\\p{N}]+")
    private val livePrefix = Regex("^live\\s*:?\\s+", RegexOption.IGNORE_CASE)
    private val shortDate = Regex("""\b(\d{1,2})\s+([A-Za-z]{3})[a-z]*\s+(\d{4})\b""")
    private val clock24 = Regex("""\b(\d{1,2}):(\d{2})\s*(?:UTC|GMT)?\b""", RegexOption.IGNORE_CASE)
    private val months = listOf(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec",
    )
    private val leagues by lazy {
        SportsCatalog.all
            .map { it.name to SportsCatalog.norm(it.name) }
            .filter { it.second.length >= 4 }
            .distinctBy { it.second }
            .sortedByDescending { it.second.length }
    }

    /**
     * Reads every installed sports add-on and publishes its live/upcoming
     * events. Safe to call whenever the Live tab is shown: a result younger
     * than three minutes is reused, a concurrent call is ignored, and a dead
     * add-on can only cost its own timeout.
     */
    suspend fun load(providers: List<ContentProvider>, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val signature = providers.asSequence()
            .filter { it.config.enabled }
            .joinToString("|") { "${it.config.id}@${it.config.url}" }
        val changed = signature != lastProviderSignature
        lastProviderSignature = signature
        if (!force && !changed && events.value.isNotEmpty() && now - lastLoadAt in 0..FRESH_MS) return
        if (!force && !changed && lastHadCandidates && lastLoadAt != 0L && now - lastLoadAt in 0..10_000L) return
        if (!mutex.tryLock()) return
        loading.value = true
        try {
            // Provider catalog reads are chatty, blocking HTTP calls in the
            // Stremio engine. LaunchedEffect runs on the main dispatcher, so
            // without this hop every catalog read fails with
            // NetworkOnMainThreadException — manifest reads happened to work
            // because the engine fetches those on its own IO scope.
            withContext(Dispatchers.IO) { loadLocked(providers) }
            lastLoadAt = System.currentTimeMillis()
        } finally {
            loading.value = false
            mutex.unlock()
        }
    }

    private suspend fun loadLocked(providers: List<ContentProvider>) {
        val candidates = providers.filter { provider ->
            provider.config.enabled && (
                provider.config.type == ProviderType.STREMIO ||
                    sportsNamed(provider)
                )
        }.sortedWith(
            // A plainly-named sports add-on must be reached before the budget
            // is spent on a long list of movie/subtitle add-ons.
            compareByDescending<ContentProvider> { sportsNamed(it) }
                .thenBy { it.config.name.lowercase(Locale.US) },
        )
        if (candidates.isEmpty()) {
            lastHadCandidates = false
            events.value = emptyList()
            scannedAddons.value = emptyList()
            problem.value = null
            return
        }
        lastHadCandidates = true

        val now = System.currentTimeMillis()
        // Four ADD-ONS at a time is enough to feel instant and small enough
        // that a phone with thirty installed add-ons does not open thirty
        // connections at once while the user is only glancing at Sports. A
        // hard total budget still publishes whatever answered: a library full
        // of dead hosts must not leave the Live tab spinning for minutes.
        val gate = Semaphore(4)
        val collected = java.util.Collections.synchronizedList(
            mutableListOf<Pair<String, ProviderResult>>(),
        )
        withTimeoutOrNull(TOTAL_BUDGET_MS) {
            coroutineScope {
                candidates.forEach { provider ->
                    launch {
                        val result = gate.withPermit { loadProvider(provider, now) }
                        collected.add(provider.config.name to result)
                    }
                }
            }
        }
        val loaded = synchronized(collected) { collected.toList() }
        val names = loaded.mapNotNull { (name, result) -> name.takeIf { result.scanned } }
            .distinct()
            .sorted()
        val all = loaded.flatMap { it.second.events }
            .distinctBy { it.key }
            .sortedWith(
                compareBy<Event> { it.startMs ?: Long.MAX_VALUE }
                    .thenBy { it.providerName.lowercase(Locale.US) },
            )
            .take(MAX_EVENTS)

        scannedAddons.value = names
        events.value = all
        problem.value = when {
            names.isNotEmpty() && all.isEmpty() ->
                "Your sports add-ons are installed, but their event lists are empty right now."
            names.isEmpty() && candidates.any { sportsNamed(it) } ->
                "Couldn't read an event list from your sports add-ons. Check your connection, " +
                    "or open Extensions and reload them."
            else -> null
        }
        com.hikari.app.data.Logs.log(
            "Sports",
            "add-on guide: ${all.size} event(s) from ${names.size} add-on(s)" +
                if (names.isEmpty()) " (no sports catalogs found)" else " (${names.joinToString()})",
        )
    }

    private fun sportsNamed(provider: ContentProvider): Boolean {
        val name = " " + provider.config.name.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim() + " "
        return sportWords.any { term ->
            if (term.length <= 3) " $term " in name else name.contains(term)
        }
    }

    private data class ProviderResult(val scanned: Boolean, val events: List<Event>)

    private suspend fun loadProvider(provider: ContentProvider, now: Long): ProviderResult {
        val refs = catalogsFor(provider)
        if (refs.isEmpty()) return ProviderResult(false, emptyList())
        val sports = refs
            .filter { isSportsCatalog(provider, it) }
            .sortedWith(
                compareBy<CatalogRef> { catalogRank(it) }
                    .thenBy { it.name.lowercase(Locale.US) },
            )
            .take(MAX_CATALOGS_PER_ADDON)
        if (sports.isEmpty()) return ProviderResult(false, emptyList())

        val seen = LinkedHashMap<String, Event>()
        for (ref in sports) {
            val rows = withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                runCatching { provider.getCatalog(ref, 1) }.getOrDefault(emptyList())
            }.orEmpty()
            if (rows.isEmpty()) {
                android.util.Log.d(
                    "Hikari/SportsAddons",
                    "${provider.config.name} · ${ref.name}: no rows" +
                        (com.hikari.app.providers.StremioAddon.catalogErrors[provider.config.id]
                            ?.let { " — $it" } ?: ""),
                )
            }
            for (item in rows) {
                val event = toEvent(provider, ref, item, now) ?: continue
                // The same event is commonly present in "Live", "Today" and a
                // sport-specific catalog of the SAME add-on. Keep one card, not
                // three duplicates of one fixture. An undated event keys on its
                // title alone, so two different live matches are both kept.
                val dedupe = "${SportsCatalog.norm(event.title)}|" +
                    (event.startMs?.div(30 * 60_000L)?.toString() ?: "untimed")
                seen.putIfAbsent(dedupe, event)
            }
            if (seen.size >= MAX_EVENTS) break
        }
        return ProviderResult(true, seen.values.toList())
    }

    private suspend fun catalogsFor(provider: ContentProvider): List<CatalogRef> {
        val url = provider.config.url
        val cached = catalogCache[provider.config.id]
        if (cached != null && cached.url == url && System.currentTimeMillis() - cached.at in 0..10 * 60_000L) {
            return cached.refs
        }
        val refs = withTimeoutOrNull(MANIFEST_TIMEOUT_MS) {
            runCatching { provider.catalogs() }.getOrDefault(emptyList())
        }.orEmpty()
        catalogCache[provider.config.id] = CatalogCache(
            // An empty answer may be a cold serverless container: let it be
            // retried after a minute instead of being frozen for ten.
            at = if (refs.isEmpty()) System.currentTimeMillis() - 9 * 60_000L
            else System.currentTimeMillis(),
            url = url,
            refs = refs,
        )
        return refs
    }

    private fun catalogRank(ref: CatalogRef): Int {
        val text = "${ref.id} ${ref.name}".lowercase(Locale.US)
        return when {
            "live" in text -> 0
            "today" in text -> 1
            "upcoming" in text -> 2
            "all" in text -> 3
            else -> 4
        }
    }

    private fun isSportsCatalog(provider: ContentProvider, ref: CatalogRef): Boolean {
        val text = "${provider.config.name} ${ref.name} ${ref.id} ${ref.rawType}".lowercase(Locale.US)
        if (excludedCatalog.containsMatchIn(text)) return false
        val words = " " + text.replace(Regex("[^a-z0-9]+"), " ").trim() + " "
        if (sportWords.any { term ->
                if (term.length <= 3) " $term " in words else text.contains(term)
            }
        ) {
            return true
        }
        // A plainly-named sports add-on may publish a generic "Live Events"
        // catalog whose name carries no sport word. Accept that only when the
        // add-on itself is sports-named — a general live-TV add-on's "Live"
        // catalog is not a sports guide.
        return sportWords.any { provider.config.name.lowercase(Locale.US).contains(it) } &&
            liveCatalogWords.containsMatchIn(text)
    }

    private fun toEvent(
        provider: ContentProvider,
        ref: CatalogRef,
        item: MediaItem,
        now: Long,
    ): Event? {
        val rawTitle = item.title.trim()
        if (rawTitle.isBlank()) return null
        val releaseInfo = item.releaseInfo.orEmpty()
        val title = rawTitle
            .replace(statusSymbols, "")
            .replace(livePrefix, "")
            .trim()
        if (title.isBlank()) return null

        val overview = item.overview.orEmpty()
        val searchText = listOf(
            rawTitle,
            releaseInfo,
            overview,
            item.genres.joinToString(" "),
            ref.name,
            provider.config.name,
        ).joinToString(" ")
        val catalogLive = liveCatalogWords.containsMatchIn("${ref.name} ${ref.id}") ||
            liveCatalogWords.containsMatchIn(provider.config.name)
        val live = liveWord.containsMatchIn("$rawTitle $releaseInfo") ||
            (catalogLive && item.released.isNullOrBlank() && releaseInfo.isBlank())
        val start = parseStart(item, releaseInfo, overview)
        val terminated = terminalWord.containsMatchIn("$title $releaseInfo") ||
            overview.lineSequence().any { line ->
                terminalWord.containsMatchIn(line.trim()) && !line.contains("live", true)
            }
        if (terminated && !live) {
            return null
        }

        if (start != null) {
            // Keep a finished event for a short tail (a final score/card the
            // user may still want), and reject anything outside the sports
            // window the rest of the tab uses.
            if (start < now - 8 * 3_600_000L || start > now + 72 * 3_600_000L) {
                if (!(live && start <= now + 3_600_000L)) {
                    return null
                }
            }
        } else if (!live && !catalogLive && !isEventType(item.rawType)) {
            // An undated movie/series row in a sports catalog is VOD, not an
            // event. Without any time or live cue there is nothing to put on
            // the guide.
            return null
        }

        return Event(
            key = "sportsaddon|${provider.config.id}|${item.rawType}|${item.id}",
            providerId = provider.config.id,
            providerName = provider.config.name,
            catalogName = ref.name,
            item = item,
            title = title,
            league = leagueOf(searchText),
            sport = sportOf(searchText),
            startMs = start,
            live = live,
            observedAt = now,
            artwork = safeImage(item.backdropUrl) ?: safeImage(item.posterUrl),
            description = com.hikari.app.net.PromoGuard.cleanText(overview).take(320).ifBlank { null },
        )
    }

    private fun isEventType(rawType: String): Boolean {
        val type = rawType.lowercase(Locale.US)
        return type in setOf(
            "tv", "channel", "channels", "live", "sports", "sport", "event",
            "events", "match", "matches", "game", "games",
        ) || type.startsWith("sport")
    }

    private fun parseStart(item: MediaItem, releaseInfo: String, overview: String): Long? {
        SportsDates.parseIso(item.released)?.let { return it }
        SportsDates.parseIso(releaseInfo)?.let { return it }
        SportsDates.parseIso(overview)?.let { return it }

        // "14 Nov 2026 · 19:30 UTC" — the shape most sports add-ons print in
        // releaseInfo/description. The time is read from the same string only
        // when a date is present, so a stray "19:30" in prose cannot invent one.
        val date = shortDate.find("$releaseInfo\n$overview") ?: return null
        val month = months.indexOf(date.groupValues[2].take(3).lowercase(Locale.US)) + 1
        if (month == 0) return null
        val tail = "$releaseInfo\n$overview".substring(date.range.last + 1).take(80)
        val clock = clock24.find(tail)
        val hour = clock?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minute = clock?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val day = date.groupValues[1].toIntOrNull() ?: return null
        val year = date.groupValues[3].toIntOrNull() ?: return null
        if (day !in 1..31 || hour > 23 || minute > 59 || year !in 2000..2100) return null
        return CivilTime.toEpochMs(
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute,
            second = 0,
            zone = LiveTvTimeZones.UTC,
        )
    }

    private fun leagueOf(text: String): String? {
        val norm = " ${SportsCatalog.norm(text)} "
        return leagues.firstOrNull { " ${it.second} " in norm }?.first
    }

    private fun sportOf(text: String): Sport? {
        val words = " " + text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim() + " "
        fun has(vararg terms: String): Boolean = terms.any { " $it " in words }
        return when {
            has("american football", "nfl", "ncaa football") -> Sport.AMERICAN_FOOTBALL
            has("basketball", "nba", "wnba", "euroleague") -> Sport.BASKETBALL
            has("ice hockey", "hockey", "nhl") -> Sport.HOCKEY
            has("baseball", "mlb", "minor league baseball") -> Sport.BASEBALL
            has("football", "soccer", "premier league", "champions league", "la liga") -> Sport.FOOTBALL
            has("rugby", "six nations") -> Sport.RUGBY
            has("ufc", "mma", "boxing", "fight", "combat") -> Sport.FIGHTING
            has("formula 1", "formula one", "f1", "motogp", "nascar", "racing", "motorsport") -> Sport.RACING
            has("golf", "pga", "lpga") -> Sport.GOLF
            has("tennis", "atp", "wta", "wimbledon") -> Sport.TENNIS
            has("cricket", "t20", "ipl") -> Sport.CRICKET
            has("darts", "pdc") -> Sport.DARTS
            has("snooker", "billiards", "pool") -> Sport.SNOOKER
            has("wrestling", "wwe", "aew") -> Sport.WRESTLING
            has("afl", "aussie rules", "australian football") -> Sport.AUSSIE_RULES
            has("volleyball") -> Sport.VOLLEYBALL
            has("lacrosse") -> Sport.LACROSSE
            has("gaelic", "hurling") -> Sport.GAELIC
            has("netball") -> Sport.NETBALL
            else -> null
        }
    }

    private fun safeImage(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > 2048) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        return value
    }

    /**
     * The streams this add-on returns for [event], with stale/placeholder rows
     * removed and the add-on's own source metadata carried along. Resolved on
     * demand (when the event card is opened), never during the catalog scan:
     * one `/stream` request per visible event would turn a fast guide into a
     * wall of timeouts.
     */
    suspend fun streams(event: Event): List<StreamSource> = withContext(Dispatchers.IO) {
        val app = runCatching { HikariApp.instance }.getOrNull() ?: return@withContext emptyList()
        val provider = app.providers.byId(event.providerId) ?: return@withContext emptyList()
        val sources: List<StreamSource> = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
            runCatching { provider.getStreams(event.item, null) }.getOrDefault(emptyList<StreamSource>())
        }.orEmpty()
        sources
            .filter { source ->
                source.url.startsWith("http://") || source.url.startsWith("https://") ||
                    (source.isTorrent && !source.infoHash.isNullOrBlank())
            }
            // A page link is not a video file: the player cannot open it, and
            // an add-on uses these as "watch on the site" rows. The event card
            // still says which add-on carries it; only real streams are listed.
            .filterNot { it.externalUrl }
            .distinctBy { it.identityKey() }
            .sortedWith(
                compareBy<StreamSource> { if (sourceLooksLive(it)) 0 else 1 }
                    .thenByDescending { qualityRank(it.name) },
            )
    }

    private fun sourceLooksLive(source: StreamSource): Boolean {
        val text = "${source.name} ${source.details}".lowercase(Locale.US)
        return "live" in text || source.isM3u8
    }

    /**
     * The user's IPTV channels that look like they carry [event]. Add-on events
     * have no broadcaster list, so this matches the two participants (when the
     * title names them), the league and the sport's channel group. Deliberately
     * conservative: one team name alone is not enough unless the channel is
     * also a league/sport channel, so "Arsenal TV" is offered for an Arsenal
     * match but a random entertainment channel is not.
     */
    fun channelsFor(
        event: Event,
        channels: List<com.hikari.app.data.IptvChannel>,
    ): List<com.hikari.app.data.IptvChannel> {
        if (channels.isEmpty()) return emptyList()
        val title = SportsCatalog.norm(event.title)
        val sides = title
            .split(Regex("\\s+(?:vs?\\.?|versus|at)\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
        val league = event.league?.let { SportsCatalog.norm(it) }?.takeIf { it.length >= 3 }
        val sport = event.sport?.label?.let { SportsCatalog.norm(it) }?.takeIf { it.length >= 3 }
        val scored = channels.mapNotNull { channel ->
            val name = SportsCatalog.norm(channel.name)
            val group = SportsCatalog.norm(channel.group)
            val both = if (sides.size >= 2) {
                name.contains(sides[0]) && name.contains(sides[1])
            } else false
            val one = if (sides.size >= 2 && !both) {
                name.contains(sides[0]) || name.contains(sides[1])
            } else false
            val leagueHit = league != null && (name.contains(league) || group.contains(league))
            val sportHit = sport != null && (group.contains(sport) || name.contains(sport))
            val score = when {
                both -> 100
                one && leagueHit -> 70
                leagueHit && sides.size < 2 -> 65
                leagueHit -> 45
                one && sportHit -> 35
                else -> 0
            }
            if (score <= 0) null else channel to score
        }.sortedWith(
            compareByDescending<Pair<com.hikari.app.data.IptvChannel, Int>> { it.second }
                .thenBy { it.first.name.lowercase(Locale.US) },
        )
        return scored.take(6).map { it.first }
    }

    private fun qualityRank(name: String): Int = when {
        Regex("\\b(4k|2160p|uhd)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 4
        Regex("\\b(1080p|fhd)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 3
        Regex("\\b(720p|hd)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 2
        else -> 1
    }
}
