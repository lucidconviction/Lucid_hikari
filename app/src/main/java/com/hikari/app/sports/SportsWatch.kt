package com.hikari.app.sports

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.hikari.app.HikariApp
import com.hikari.app.data.ContentRepository
import com.hikari.app.data.MediaItem
import com.hikari.app.data.StreamSource
import com.hikari.app.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Watch on" streams found INSIDE the app.
 *
 * The reference TV app wires this as: your own channels first, then every
 * sports addon's streams, each one a clickable row. Hikari has no separate
 * sports-addon layer — an addon, a CloudStream/Hikari extension and an IPTV
 * playlist are all [ContentProvider]s — so this searches ALL of them through
 * the normal provider contract:
 *
 *  1. the IPTV channels already matched by [SportsMatcher] (the caller draws
 *     those rows directly);
 *  2. every installed extension / Stremio addon / IPTV playlist, searched
 *     with the fixture's own words;
 *  3. every stream the matching titles return, flattened into one clickable
 *     row per stream (not one row per title).
 *
 * A finished game is searched too: the queries switch to highlights, replays
 * and full-game words, which is where the archive addons answer.
 */
object SportsWatch {

    /**
     * One clickable stream row. [sources] is the whole server list of the
     * title the stream belongs to; [selected] is the one this row represents.
     * Playing passes the whole list with the tapped stream first, so the
     * player can still fall back to the title's other servers.
     */
    data class StreamLink(
        val key: String,
        val title: String,
        val provider: String,
        val poster: String?,
        val sourceLabel: String,
        val sources: List<StreamSource>,
        val selected: StreamSource,
        val score: Int,
    )

    private const val SEARCH_BUDGET_MS = 20_000L
    private const val RESOLVE_BUDGET_MS = 7_000L
    private const val MAX_TITLES = 16
    private const val MAX_LINKS = 40

    suspend fun search(event: SportsEvent): List<StreamLink> = withContext(Dispatchers.IO) {
        val app = runCatching { HikariApp.instance }.getOrNull() ?: return@withContext emptyList()
        val queries = queriesFor(event)
        if (queries.isEmpty()) return@withContext emptyList()
        val repo = ContentRepository(app.providers)

        // A shared deadline, not one budget per query: four searches must
        // still leave the game page responsive on a phone with many providers.
        val deadline = SystemClock.elapsedRealtime() + SEARCH_BUDGET_MS
        val items = LinkedHashMap<String, MediaItem>()
        for (query in queries) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0L || items.size >= 80) break
            withTimeoutOrNull(left) {
                repo.searchStreaming(query).collect { page ->
                    page.forEach { item -> items.putIfAbsent(item.uniqueId, item) }
                }
            }
        }

        val ranked = items.values
            .map { item -> item to relevance(item.title, event) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_TITLES)
        if (ranked.isEmpty()) return@withContext emptyList()

        // Resolve several titles at once, but not all installed providers'
        // answers in one burst: originStreams is a blocking extension call.
        val gate = Semaphore(5)
        val resolved = coroutineScope {
            ranked.map { (item, score) ->
                async {
                    gate.withPermit {
                        val sources = withTimeoutOrNull(RESOLVE_BUDGET_MS + 4_000L) {
                            repo.originStreams(item, null, RESOLVE_BUDGET_MS)
                        }.orEmpty()
                            .filter { it.url.isNotBlank() && !it.externalUrl }
                        Triple(item, score, sources)
                    }
                }
            }.awaitAll()
        }

        val seenUrls = HashSet<String>()
        val out = mutableListOf<StreamLink>()
        for ((item, score, sources) in resolved) {
            val unique = sources.filter { seenUrls.add(it.url) }
            if (unique.isEmpty()) continue
            val provider = app.providers.byId(item.providerId)?.config?.name
                ?: item.providerId.substringAfter('|', item.providerId)
            for (source in unique) {
                out += StreamLink(
                    key = "${item.uniqueId}|${source.url}",
                    title = item.title,
                    provider = provider,
                    poster = item.posterUrl,
                    sourceLabel = source.name.ifBlank { "Stream" },
                    sources = unique,
                    selected = source,
                    score = score,
                )
                if (out.size >= MAX_LINKS) break
            }
            if (out.size >= MAX_LINKS) break
        }
        out.sortedWith(compareByDescending<StreamLink> { it.score }.thenBy { it.title })
    }

    /** The event's own words, plus the words each state's streams use. */
    private fun queriesFor(event: SportsEvent): List<String> {
        val base = event.title.trim().takeIf { it.isNotBlank() } ?: return emptyList()
        val names = listOfNotNull(event.home, event.away)
            .flatMap { listOf(it.team.name, it.team.fullName) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val league = SportsCatalog.league(event.leagueId)?.name?.takeIf { it.isNotBlank() }
        val out = LinkedHashSet<String>()
        out += base
        if (names.isNotEmpty()) out += names.joinToString(" ")
        if (league != null) out += "$league $base"
        if (event.state == GameState.POST) {
            out += "$base highlights"
            out += "$base replay"
            out += "$base full game"
        } else {
            out += "$base live stream"
        }
        return out.toList()
    }

    /**
     * How much a search result looks like THIS fixture. Both teams named is the
     * strongest signal; one team plus the league or an action word is enough
     * to list it; an event without sides needs two distinctive title words.
     */
    private fun relevance(candidate: String, event: SportsEvent): Int {
        val text = SportsCatalog.norm(candidate)
        if (text.isBlank()) return 0
        val sides = listOfNotNull(event.home, event.away).map { side ->
            listOf(side.team.name, side.team.fullName, side.team.abbr)
                .map { SportsCatalog.norm(it) }
                .filter { it.length >= 3 }
                .toSet()
        }
        if (sides.isNotEmpty()) {
            val hits = sides.count { names -> names.any { text.contains(it) } }
            val both = sides.size >= 2 && hits >= 2
            val league = SportsCatalog.league(event.leagueId)?.name
                ?.let { SportsCatalog.norm(it) }?.takeIf { it.isNotBlank() }
                ?.let { text.contains(it) } == true
            val action = listOf(
                "live", "stream", " vs ", "vs.", "highlights", "highlight",
                "replay", "full game", "watch",
            ).any { text.contains(it) }
            return when {
                both -> 120
                hits == 1 && (league || action) -> 55
                hits == 1 -> 35
                else -> 0
            }
        }
        val tokens = SportsCatalog.norm(event.title)
            .split(' ')
            .filter { it.length >= 4 }
            .distinct()
        if (tokens.isEmpty()) return 0
        val hits = tokens.count { text.contains(it) }
        return if (hits >= minOf(2, tokens.size)) 40 + hits * 10 else 0
    }

    /**
     * Opens [link]'s title in the player with the tapped stream first. The
     * title's other servers ride along, so a dead row still has the player's
     * usual fallbacks.
     */
    fun play(context: Context, link: StreamLink) {
        val ordered = listOf(link.selected) +
            link.sources.filterNot { it.url == link.selected.url }
        if (ordered.isEmpty()) return
        val payload = JSONArray().apply {
            ordered.forEach { source ->
                put(
                    JSONObject()
                        .put("name", source.name)
                        .put("url", source.url)
                        .put("headers", JSONObject(source.headers))
                        .put("isM3u8", source.isM3u8)
                        .put("isMpd", source.isMpd)
                        .put("isTorrent", source.isTorrent)
                        .put("provider", source.provider)
                        .put("providerId", source.providerId)
                        .put("providerName", source.providerName)
                        .put("details", source.details),
                )
            }
        }.toString()
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("title", link.title)
            putExtra("sources", payload)
            putExtra("askServer", false)
            putExtra("histProviderId", link.selected.providerId)
            putExtra("histTitle", link.title)
            link.poster?.takeIf { it.isNotBlank() }?.let { putExtra("histPoster", it) }
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
