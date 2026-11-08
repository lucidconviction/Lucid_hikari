package com.hikari.app.sports

import android.app.Activity
import android.content.Context
import android.content.Intent
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
 * Live/upcoming "Watch on" links found INSIDE the app:
 *  - the IPTV channels already matched by [SportsMatcher] (shown directly), and
 *  - titles/streams returned when every installed extension/addon (and the
 *    IPTV playlists) are searched for this event's name.
 *
 * Only a fixture that is on now or about to start asks for this: a finished
 * game has no live server to find, and searching every extension on every old
 * game would make the page slow for nothing.
 */
object SportsWatch {

    data class Playable(
        val key: String,
        val title: String,
        val provider: String,
        val poster: String?,
        val sources: List<StreamSource>,
    )

    private const val SEARCH_BUDGET_MS = 14_000L
    private const val RESOLVE_BUDGET_MS = 8_000L
    private const val MAX_ITEMS = 8

    suspend fun search(event: SportsEvent): List<Playable> = withContext(Dispatchers.IO) {
        val app = runCatching { HikariApp.instance }.getOrNull() ?: return@withContext emptyList()
        val query = event.title.trim().takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val repo = ContentRepository(app.providers)

        val items = LinkedHashMap<String, MediaItem>()
        withTimeoutOrNull(SEARCH_BUDGET_MS) {
            repo.searchStreaming(query).collect { page ->
                page.forEach { item -> items.putIfAbsent(item.uniqueId, item) }
            }
        }

        val relevant = items.values
            .filter { matchesEvent(it.title, event) }
            .take(MAX_ITEMS)
        if (relevant.isEmpty()) return@withContext emptyList()

        // Resolve several titles at once, but not all installed providers'
        // answers in one burst: originStreams is a blocking extension call.
        val gate = Semaphore(4)
        val resolved = coroutineScope {
            relevant.map { item ->
                async {
                    gate.withPermit {
                        val sources = withTimeoutOrNull(RESOLVE_BUDGET_MS + 4_000L) {
                            repo.originStreams(item, null, RESOLVE_BUDGET_MS)
                        }.orEmpty()
                            .filter { it.url.isNotBlank() && !it.externalUrl }
                        item to sources
                    }
                }
            }.awaitAll()
        }

        val seenUrls = HashSet<String>()
        val out = mutableListOf<Playable>()
        for ((item, sources) in resolved) {
            val unique = sources.filter { seenUrls.add(it.url) }
            if (unique.isEmpty()) continue
            out += Playable(
                key = item.uniqueId,
                title = item.title,
                provider = app.providers.byId(item.providerId)?.config?.name
                    ?: item.providerId.substringAfter('|', item.providerId),
                poster = item.posterUrl,
                sources = unique,
            )
            if (out.size >= MAX_ITEMS) break
        }
        out
    }

    /**
     * Whether a search result is about THIS fixture. A team event needs both
     * sides named (one name alone is every other game that team plays); an
     * event without sides (a fight card) needs two distinctive title words.
     */
    private fun matchesEvent(candidate: String, event: SportsEvent): Boolean {
        val text = SportsCatalog.norm(candidate)
        if (text.isBlank()) return false
        val sides = listOfNotNull(event.home, event.away).map { side ->
            listOf(
                side.team.name,
                side.team.fullName,
                side.team.abbr,
            ).map { SportsCatalog.norm(it) }.filter { it.length >= 3 }.toSet()
        }
        if (sides.isNotEmpty()) {
            return sides.all { names -> names.any { text.contains(it) } }
        }
        val tokens = SportsCatalog.norm(event.title)
            .split(' ')
            .filter { it.length >= 4 }
            .distinct()
        // A weekly show has only one distinctive word ("SmackDown") and must
        // still match; a fight card has several and needs two of them.
        return tokens.isNotEmpty() && tokens.count { text.contains(it) } >= minOf(2, tokens.size)
    }

    /** Opens [playable]'s streams in the player; the first is started directly. */
    fun play(context: Context, playable: Playable) {
        if (playable.sources.isEmpty()) return
        val payload = JSONArray().apply {
            playable.sources.forEach { source ->
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
            putExtra("title", playable.title)
            putExtra("sources", payload)
            putExtra("askServer", false)
            putExtra("histProviderId", playable.sources.first().providerId)
            putExtra("histTitle", playable.title)
            playable.poster?.takeIf { it.isNotBlank() }?.let { putExtra("histPoster", it) }
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
