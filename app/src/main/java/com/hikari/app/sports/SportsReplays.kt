package com.hikari.app.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Fight replays from the sport-video-archive API (the archive behind
 * watchprowrestling.store). It carries UFC, MMA and boxing cards with a poster
 * and the file-host links for each show.
 *
 * The list is deliberately date-sorted, newest first: a replay page is for
 * catching up on something that already happened.
 */
object SportsReplays {

    private const val BASE = "https://bn32423.us.ci"
    private const val TTL_MS = 30 * 60 * 1000L
    private val SECTIONS = listOf("ufc", "mma", "boxing")

    data class Stream(val label: String, val url: String, val provider: String?)

    data class Replay(
        val id: String,
        val title: String,
        val dateMs: Long,
        val section: String,
        val thumb: String?,
        val pageUrl: String,
        val streams: List<Stream>,
    )

    val items = MutableStateFlow<List<Replay>>(emptyList())
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
                    val found = LinkedHashMap<String, Replay>()
                    SECTIONS.forEach { section ->
                        runCatching { fetchSection(section) }.getOrDefault(emptyList()).forEach { replay ->
                            found.putIfAbsent(replay.id, replay)
                        }
                    }
                    found.values.sortedByDescending { it.dateMs }
                }
                items.value = all
                loadedAt = System.currentTimeMillis()
                if (all.isEmpty()) problem.value = "No replays came back from the archive."
            } catch (e: Exception) {
                problem.value = e.message ?: "Couldn't load replays."
            } finally {
                loading.value = false
            }
        }
    }

    private fun fetchSection(section: String): List<Replay> {
        val raw = com.hikari.app.net.Http.getStringQuiet(
            "$BASE/api/videos?section=$section&limit=60",
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
            val effectiveSection = if (section == "mma" && title.contains("UFC", ignoreCase = true)) "ufc" else section
            out += Replay(
                id = v.optString("id").ifBlank { page.ifBlank { title } },
                title = title,
                dateMs = dateMs,
                section = effectiveSection,
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
