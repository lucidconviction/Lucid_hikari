package com.hikari.app.sports

import com.hikari.app.data.IptvChannel
import com.hikari.app.data.XtreamEpg
import com.hikari.app.data.XtreamShortEpg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Works out WHICH feed of a network is showing a fixture, by reading that
 * channel's own short guide around kick-off.
 *
 * The sports matcher already answers "this looks like beIN Sports". The problem
 * is that a portal carries `beIN SPORTS 1 AR`, `1 FR`, `2 AR`, `3 EN` — ten
 * near-identical rows — and only one of them has the game. This reads each
 * candidate's `get_short_epg` and scores the programme titles against the two
 * teams, so the card can say "Showing: Arsenal v Chelsea" and float that feed
 * to the top.
 *
 * It is intentionally narrow:
 *
 *  - only Xtream channels (they are the ones with a numeric stream id and a
 *    player API), never a guess from an M3U name;
 *  - only when the fixture page asks (not for every card in the list);
 *  - at most 8 channels, 3 requests at a time, one 6-programme window each;
 *  - every answer, including "nothing found", is cached for ten minutes, so
 *    opening the same game again costs no requests.
 */
object FeedFinder {

    data class Found(
        val programTitle: String,
        val score: Int,
    )

    private data class Cached(val at: Long, val found: Found?)

    private const val TTL_MS = 10 * 60 * 1000L
    private const val MAX_CHANNELS = 8
    private const val CONCURRENCY = 3
    private const val REQUEST_TIMEOUT_MS = 9_000L

    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun find(event: SportsEvent, matches: List<ChannelMatch>): Map<String, Found> {
        val league = SportsCatalog.league(event.leagueId) ?: return emptyMap()
        val sides = sidesFor(event, league)
        if (sides.isEmpty()) return emptyMap()

        val targets = matches.asSequence()
            .mapNotNull { match -> match.channel?.let { match to it } }
            .filter { (_, channel) -> channel.providerId.isNotBlank() }
            .distinctBy { (_, channel) ->
                channel.providerId + "|" + (XtreamShortEpg.streamIdFromUrl(channel.url) ?: channel.url)
            }
            .take(MAX_CHANNELS)
            .toList()
        if (targets.isEmpty()) return emptyMap()

        val out = ConcurrentHashMap<String, Found>()
        val gate = Semaphore(CONCURRENCY)
        coroutineScope {
            targets.map { (_, channel) ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        hitFor(event, channel, sides)?.let { out[channel.url] = it }
                    }
                }
            }.awaitAll()
        }
        return out
    }

    private suspend fun hitFor(
        event: SportsEvent,
        channel: IptvChannel,
        sides: List<List<String>>,
    ): Found? {
        val key = "${event.id}|${channel.url}"
        val now = System.currentTimeMillis()
        cache[key]?.let { if (now - it.at < TTL_MS) return it.found }

        val creds = XtreamEpg.credentialsFor(channel.providerId) ?: return null
        val streamId = XtreamShortEpg.streamIdFromUrl(channel.url) ?: return null
        val programmes = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            XtreamShortEpg.fetch(creds, streamId, limit = 6)
        }.orEmpty()
        val best = bestProgramme(event, programmes, sides)
        cache[key] = Cached(now, best)
        return best
    }

    private fun bestProgramme(
        event: SportsEvent,
        programmes: List<XtreamShortEpg.Programme>,
        sides: List<List<String>>,
    ): Found? {
        // The guide is asked around the fixture, not the whole day: 30 minutes
        // before kick-off covers build-up, 2h30 after covers extra time and
        // post-match shows.
        val from = event.startMs - 30 * 60 * 1000L
        val to = event.startMs + 150 * 60 * 1000L
        var best: XtreamShortEpg.Programme? = null
        var bestScore = 0
        for (programme in programmes) {
            if (programme.stopMs < from || programme.startMs > to) continue
            val text = SportsCatalog.norm(programme.title + " " + programme.description)
            var score = 0
            for (side in sides) {
                if (side.any { token -> SportsCatalog.hasWord(text, token) }) score++
            }
            if (score > bestScore) {
                best = programme
                bestScore = score
            }
        }
        return best?.let { Found(it.title, bestScore) }
    }

    /**
     * The words that identify each side. Team fixtures use the same strong/weak
     * names the matcher already trusts; event fixtures (races, fight cards) use
     * the event and league titles, minus noise words.
     */
    private fun sidesFor(event: SportsEvent, league: League): List<List<String>> {
        val sides = listOfNotNull(event.away, event.home)
        if (sides.isNotEmpty()) {
            return sides.map { side ->
                val (strong, weak) = SportsCatalog.teamNames(side.team, league)
                (strong + weak).asSequence()
                    .map { it.trim() }
                    .filter { it.length >= 3 && it !in GENERIC }
                    .distinct()
                    .toList()
            }.filter { it.isNotEmpty() }
        }
        return listOf(words(event.title + " " + league.name)).filter { it.isNotEmpty() }
    }

    private fun words(text: String): List<String> =
        SportsCatalog.norm(text).trim().split(' ')
            .filter { it.length >= 4 && it !in NOISE }
            .distinct()

    /** Club-name words that appear in half the world's channels and teams. */
    private val GENERIC = setOf(
        "united", "city", "real", "club", "sporting", "athletic", "atletico",
        "inter", "state", "university", "town", "county", "rovers", "wanderers",
        "olympique", "racing", "deportivo", "sport", "sports", "team", "new",
        "saint",
    )

    private val NOISE = setOf(
        "live", "versus", "main", "card", "event", "fight", "night", "race",
        "grand", "prix", "round", "final", "world", "championship", "preview",
        "replay", "highlights", "from",
    )
}
