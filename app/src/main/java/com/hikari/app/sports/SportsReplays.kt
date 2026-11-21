package com.hikari.app.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Replays from the sport-video-archive API (the archive behind
 * watchprowrestling.store and sport-video.org.ua).
 *
 * The archive's `/api/sections` page lists 60+ sections: every sport plus F1
 * seasons, racing series and wrestling promotions. This client pulls the
 * SPORT sections — football, basketball, hockey, racing, wrestling and the
 * fight cards — so the Replays tab is a full catch-up page rather than an
 * MMA-only list. Duplicate videos (a card carried by two sources) collapse by
 * id; the list is date-sorted, newest first.
 */
object SportsReplays {

    private const val BASE = "https://bn32423.us.ci"
    private const val TTL_MS = 30 * 60 * 1000L
    private const val PER_SECTION = 18

    data class Section(val key: String, val label: String)

    /**
     * The sport sections of `/api/sections`, in the order the chips should be
     * read: fighting first (the page's original purpose), then the big team
     * sports, racing, wrestling and the catch-all.
     */
    private val SECTIONS = listOf(
        Section("ufc", "UFC"),
        Section("mma", "MMA"),
        Section("boxing", "Boxing"),
        Section("football", "Football"),
        Section("basketball", "Basketball"),
        Section("americanfootball", "American Football"),
        Section("baseball", "Baseball"),
        Section("icehockey", "Hockey"),
        Section("rugby", "Rugby / AFL / GAA"),
        Section("nba", "NBA"),
        Section("ncaa", "NCAA"),
        Section("euroleague", "Euroleague"),
        Section("wnba", "WNBA"),
        Section("nhl-2026-27", "NHL"),
        Section("f1-2026", "F1"),
        Section("f1-archive", "F1 Archive"),
        Section("nascar", "NASCAR"),
        Section("indycar", "IndyCar"),
        Section("wsbk", "WSBK"),
        Section("wrc", "WRC"),
        Section("formula-2", "Formula 2"),
        Section("formula-3", "Formula 3"),
        Section("formula-e", "Formula E"),
        Section("f1-academy", "F1 Academy"),
        Section("dtm", "DTM"),
        Section("wwe", "WWE"),
        Section("aew", "AEW"),
        Section("njpw", "NJPW"),
        Section("puroresu", "Puroresu"),
        Section("impact-wrestling", "TNA / Impact"),
        Section("roh", "ROH"),
        Section("gcw", "GCW"),
        Section("nwa", "NWA"),
        Section("lucha-wrestling", "Lucha Libre"),
        Section("highlights", "Highlights"),
        Section("other", "Other Sport"),
    )

    data class Stream(val label: String, val url: String, val provider: String?)

    data class Replay(
        val id: String,
        val title: String,
        val dateMs: Long,
        /** Section KEY (for filtering). */
        val section: String,
        /** Human label for the card badge / chip. */
        val sectionLabel: String,
        val thumb: String?,
        val pageUrl: String,
        val streams: List<Stream>,
    )

    val items = MutableStateFlow<List<Replay>>(emptyList())

    /** Sections that actually returned videos, in display order (for the chips). */
    val sections = MutableStateFlow<List<Section>>(emptyList())
    val loading = MutableStateFlow(false)
    val problem = MutableStateFlow<String?>(null)

    private val mutex = Mutex()
    private var loadedAt = 0L

    suspend fun ensureLoaded(force: Boolean = false) {
        if (!force && items.value.isNotEmpty() && System.currentTimeMillis() - loadedAt < TTL_MS) return
        mutex.withLock {
            if (!force && items.value.isNotEmpty() && System.currentTimeMillis() - loadedAt < TTL_MS) return
            loading.value = true
            problem.value = null
            try {
                val all = withContext(Dispatchers.IO) {
                    // Ten at a time: the archive answers each section in well
                    // under a second, and this keeps 36 requests from firing at
                    // once (which the origin throttles) while still loading
                    // the whole page in a few seconds.
                    val gate = Semaphore(10)
                    val fetched = coroutineScope {
                        SECTIONS.map { section ->
                            async {
                                gate.withPermit {
                                    section to runCatching { fetchSection(section) }
                                        .getOrDefault(emptyList())
                                }
                            }
                        }.awaitAll()
                    }
                    val found = LinkedHashMap<String, Replay>()
                    fetched.forEach { (_, list) ->
                        list.forEach { replay -> found.putIfAbsent(replay.id, replay) }
                    }
                    found.values.sortedByDescending { it.dateMs }
                }
                items.value = all
                val present = all.map { it.section }.toSet()
                val ordered = buildList {
                    SECTIONS.filter { it.key in present }.forEach { add(it) }
                    // A card can be remapped (an MMA entry naming UFC moves to
                    // the UFC section); keep a chip for any key not in the list.
                    present.filter { key -> SECTIONS.none { it.key == key } }
                        .sorted()
                        .forEach { add(Section(it, it.uppercase())) }
                }
                sections.value = ordered
                loadedAt = System.currentTimeMillis()
                if (all.isEmpty()) problem.value = "No replays came back from the archive."
            } catch (e: Exception) {
                problem.value = e.message ?: "Couldn't load replays."
            } finally {
                loading.value = false
            }
        }
    }

    private fun fetchSection(section: Section): List<Replay> {
        val raw = com.hikari.app.net.Http.getStringQuiet(
            "$BASE/api/videos?section=${section.key}&limit=$PER_SECTION",
        ) ?: return emptyList()
        val root = org.json.JSONObject(raw)
        val videos = root.optJSONArray("videos") ?: return emptyList()
        val out = mutableListOf<Replay>()
        for (i in 0 until videos.length()) {
            val v = videos.optJSONObject(i) ?: continue
            val title = cleanTitle(v.optString("title"))
            if (title.isBlank()) continue
            val page = v.optString("pageUrl")
            val streams = mutableListOf<Stream>()
            v.optJSONArray("streams")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val s = arr.optJSONObject(j) ?: continue
                    val url = s.optString("url")
                    if (url.isNotBlank()) {
                        streams += Stream(
                            label = s.optString("label").ifBlank { s.optString("provider", "Stream") },
                            url = url,
                            provider = s.optString("provider").takeIf { it.isNotBlank() },
                        )
                    }
                }
            }
            val dateMs = SportsDates.parseIso(v.optString("date") + "T00:00:00Z") ?: 0L
            // The archive carries some UFC cards in both `mma` and `ufc`;
            // filing the UFC-titled ones under the UFC chip matches the list
            // users see on the site (and dedupes the two feeds by id).
            val ufc = section.key == "mma" && title.contains("UFC", ignoreCase = true)
            val effectiveSection = if (ufc) "ufc" else section.key
            val effectiveLabel = if (ufc) "UFC" else section.label
            out += Replay(
                id = v.optString("id").ifBlank { page.ifBlank { title } },
                title = title,
                dateMs = dateMs,
                section = effectiveSection,
                sectionLabel = effectiveLabel,
                thumb = cardImage(v.optString("thumb").takeIf { it.isNotBlank() }),
                pageUrl = page,
                streams = streams,
            )
        }
        return out
    }

    private fun cleanTitle(raw: String): String {
        var t = raw.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").trim()
        t = t.removePrefix("Watch ").trim()
        t = t.removeSuffix(" Live Stream").removeSuffix(" Live").trim()
        return t
    }

    /** wsrv.nl resizes/crops any poster for a phone card. */
    private fun cardImage(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.contains("wsrv.nl")) return url
        return runCatching {
            "https://wsrv.nl/?url=${URLEncoder.encode(url, "UTF-8")}&w=640&h=360&fit=cover&output=webp&q=75"
        }.getOrNull() ?: url
    }
}
