package com.hikari.app.data

import com.hikari.app.HikariApp
import com.hikari.app.net.Http
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * AniList-backed anime catalogues.
 *
 * Two consumers:
 *  - the Anime tab / provider-catalog fallback, which wants the classic
 *    five shelves ([homeRows]);
 *  - Home's "Anime spotlight" rail and its Show All grid, which want a small
 *    rich set (banner, description, genres, next-airing episode) so one card
 *    can carry the whole pitch.
 *
 * Both share one GraphQL reader and one parser, so a field added for the
 * spotlight is parsed the same way everywhere. Nothing here throws: a dead
 * AniList (offline, rate limit, maintenance) returns the last spotlight cache
 * or an empty list, and the caller shows a retry state.
 */
object AnimeCatalogRepository {
    private const val ENDPOINT = "https://graphql.anilist.co"

    /** Six hours: trending moves slowly enough that a re-open must not cost a
     *  request, and a stale row is better than an empty one. */
    private const val SPOTLIGHT_TTL_MS = 6 * 60 * 60 * 1000L
    private const val SPOTLIGHT_FILE = "anime-spotlight.json"
    private const val SPOTLIGHT_LIMIT = 22

    /**
     * One spotlight card: the [MediaItem] the normal detail page opens plus
     * the next episode number AniList knows (its date rides on the item, see
     * [MediaItem.nextEpisodeDate]) — the "EP 7 · Wed" chip.
     */
    data class SpotlightEntry(
        val item: MediaItem,
        val nextEpisode: Int? = null,
    )

    @Volatile
    private var spotlightMem: Pair<Long, List<SpotlightEntry>>? = null

    /** Show-All pages, per sort+page. Small and process-lifetime only: the
     *  screen keeps its own list, this just makes a tab switch instant. */
    private val catalogMem = ConcurrentHashMap<String, List<MediaItem>>()

    // ------------------------------------------------------------------
    // The classic shelves (Anime tab / no-provider fallback)
    // ------------------------------------------------------------------

    suspend fun homeRows(): List<CatalogRow> = withContext(Dispatchers.IO) {
        val sections = listOf("Trending Anime" to "TRENDING_DESC", "Popular Anime" to "POPULARITY_DESC", "Top Rated Anime" to "SCORE_DESC", "Currently Airing" to "START_DATE_DESC", "New Anime" to "START_DATE_DESC")
        sections.mapNotNull { (title, sort) ->
            val items = query(sort, title == "Currently Airing")
            if (items.isEmpty()) null else CatalogRow("anilist-catalog", "AniList", title, items, "anilist-catalog|$sort|$title", sort, MediaType.SERIES, "anime")
        }
    }

    private suspend fun query(sort: String, airing: Boolean): List<MediaItem> {
        val media = fetchMedia(sort, airing, page = 1, perPage = 30, rich = false)
        return parseEntries(media, rich = false).map { it.item }
    }

    // ------------------------------------------------------------------
    // Home spotlight
    // ------------------------------------------------------------------

    /** Whatever is cached right now (memory, then disk), regardless of age —
     *  the row paints this on the first frame and refreshes underneath. */
    suspend fun cachedSpotlight(): List<SpotlightEntry> = withContext(Dispatchers.IO) {
        spotlightMem?.takeIf { it.second.isNotEmpty() }?.let { return@withContext it.second }
        val disk = readSpotlight()?.takeIf { it.second.isNotEmpty() }
        if (disk != null) {
            spotlightMem = disk
            return@withContext disk.second
        }
        emptyList()
    }

    /**
     * Trending titles for the Home rail. A fresh cache answers without a
     * request; an expired one is re-fetched, and a failed fetch falls back to
     * the stale list (the row is "always on", so it must never go blank just
     * because AniList hiccupped).
     */
    suspend fun spotlight(force: Boolean = false): List<SpotlightEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val mem = spotlightMem?.takeIf { it.second.isNotEmpty() }
        if (!force && mem != null && now - mem.first < SPOTLIGHT_TTL_MS) {
            return@withContext mem.second
        }
        val disk = if (mem == null) readSpotlight()?.takeIf { it.second.isNotEmpty() } else null
        val cached = mem ?: disk
        if (!force && cached != null && now - cached.first < SPOTLIGHT_TTL_MS) {
            spotlightMem = cached
            return@withContext cached.second
        }
        val fetched = runCatching { fetchSpotlight() }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            spotlightMem = now to fetched
            writeSpotlight(now, fetched)
            return@withContext fetched
        }
        if (cached != null) {
            spotlightMem = cached
            return@withContext cached.second
        }
        emptyList()
    }

    private suspend fun fetchSpotlight(): List<SpotlightEntry> {
        val media = fetchMedia("TRENDING_DESC", airing = false, page = 1, perPage = SPOTLIGHT_LIMIT, rich = true)
        // Home is a public surface: AniList's `isAdult` titles never ride this
        // rail, whatever the app's adult-content switch says. (The switch is
        // about what a user may BROWSE, not what the launcher suggests.)
        return parseEntries(media, rich = true).filter { !it.item.nsfw }
    }

    // ------------------------------------------------------------------
    // Show All grid
    // ------------------------------------------------------------------

    /**
     * One page of the Show All grid. [kind] is "trending" (AniList
     * TRENDING_DESC) or "popular" (POPULARITY_DESC); [page] starts at 1.
     * Empty means "no more pages".
     */
    suspend fun catalog(kind: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val key = "$kind|$page"
        catalogMem[key]?.let { return@withContext it }
        val sort = if (kind == "popular") "POPULARITY_DESC" else "TRENDING_DESC"
        val media = runCatching {
            fetchMedia(sort, airing = false, page = page.coerceAtLeast(1), perPage = 30, rich = true)
        }.getOrDefault(JSONArray())
        val items = parseEntries(media, rich = true)
            .map { it.item }
            .filter { !it.nsfw }
        if (items.isNotEmpty()) catalogMem[key] = items
        items
    }

    // ------------------------------------------------------------------
    // AniList reader
    // ------------------------------------------------------------------

    private suspend fun fetchMedia(
        sort: String,
        airing: Boolean,
        page: Int,
        perPage: Int,
        rich: Boolean,
    ): JSONArray {
        val status = if (airing) ", status: RELEASING" else ""
        val extras = if (rich) {
            " bannerImage description(asHtml: false) genres isAdult"
        } else ""
        val query = "query { Page(page: " + page.coerceAtLeast(1) + ", perPage: " +
            perPage.coerceIn(1, 50) + ") { media(type: ANIME$status, sort: [$sort]) { " +
            "id format title { userPreferred english romaji } " +
            "coverImage { large extraLarge } startDate { year } averageScore " +
            "nextAiringEpisode { airingAt episode }$extras } } }"
        val raw = Http.postStringQuiet(ENDPOINT, JSONObject().put("query", query).toString())
            ?: return JSONArray()
        return runCatching {
            JSONObject(raw).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
        }.getOrNull() ?: JSONArray()
    }

    private fun parseEntries(media: JSONArray, rich: Boolean): List<SpotlightEntry> {
        if (media.length() == 0) return emptyList()
        val out = ArrayList<SpotlightEntry>(media.length())
        for (i in 0 until media.length()) {
            val o = media.optJSONObject(i) ?: continue
            val id = o.optInt("id", 0)
            if (id <= 0) continue
            val t = o.optJSONObject("title")
            val preferred = t?.optString("userPreferred")?.trim().orEmpty()
            val english = t?.optString("english")?.trim().orEmpty()
            val romaji = t?.optString("romaji")?.trim().orEmpty()
            val title = preferred.ifBlank { english }.ifBlank { romaji }.ifBlank { "Untitled" }
            // The English name rides along as the SEARCH key: TMDB and every
            // extension index that name, while the display title stays the
            // user-preferred one (see MediaItem.searchTitle).
            val englishKey = english.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }.orEmpty()
            val cover = o.optJSONObject("coverImage")
            val poster = cover?.optString("extraLarge")?.takeIf { it.isNotBlank() }
                ?: cover?.optString("large")?.takeIf { it.isNotBlank() }
            val next = o.optJSONObject("nextAiringEpisode")
            val nextAt = next?.optLong("airingAt", 0L)?.takeIf { it > 0L }
            val overview = if (rich) {
                o.optString("description").trim().takeIf { it.isNotBlank() && it != "null" }
            } else null
            val genres = if (rich) {
                o.optJSONArray("genres")?.let { arr ->
                    buildList {
                        for (g in 0 until arr.length()) {
                            arr.optString(g).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }.orEmpty()
            } else emptyList()
            val item = MediaItem(
                providerId = "anilist",
                id = id.toString(),
                title = title,
                type = if (o.optString("format").equals("MOVIE", true)) MediaType.MOVIE else MediaType.SERIES,
                posterUrl = poster,
                year = o.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 },
                overview = overview,
                genres = genres,
                backdropUrl = if (rich) {
                    o.optString("bannerImage").trim().takeIf { it.isNotBlank() && it != "null" }
                } else null,
                rawType = "anime",
                rating = o.optDouble("averageScore", 0.0).takeIf { it > 0.0 }?.div(10.0),
                originalTitle = englishKey,
                nsfw = rich && o.optBoolean("isAdult", false),
                nextEpisodeDate = nextAt?.let { java.time.Instant.ofEpochSecond(it).toString() },
                metadataSource = "AniList",
            )
            out.add(SpotlightEntry(item, next?.optInt("episode", 0)?.takeIf { it > 0 }))
        }
        return out
    }

    // ------------------------------------------------------------------
    // Cache on disk
    // ------------------------------------------------------------------

    private fun spotlightFile(): File = File(HikariApp.instance.filesDir, SPOTLIGHT_FILE)

    private fun readSpotlight(): Pair<Long, List<SpotlightEntry>>? = runCatching {
        val f = spotlightFile()
        if (!f.exists()) return@runCatching null
        val root = JSONObject(f.readText())
        val at = root.optLong("at", 0L)
        val arr = root.optJSONArray("entries") ?: return@runCatching null
        val list = ArrayList<SpotlightEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            val genres = o.optJSONArray("genres")?.let { g ->
                buildList {
                    for (j in 0 until g.length()) {
                        g.optString(j).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }.orEmpty()
            val item = MediaItem(
                providerId = "anilist",
                id = id,
                title = o.optString("title").ifBlank { "Untitled" },
                type = if (o.optString("type") == MediaType.MOVIE.name) MediaType.MOVIE else MediaType.SERIES,
                posterUrl = o.optString("poster").takeIf { it.isNotBlank() && it != "null" },
                year = o.optInt("year", 0).takeIf { it > 0 },
                overview = o.optString("overview").takeIf { it.isNotBlank() && it != "null" },
                genres = genres,
                backdropUrl = o.optString("backdrop").takeIf { it.isNotBlank() && it != "null" },
                rawType = "anime",
                rating = o.optDouble("rating", 0.0).takeIf { it > 0.0 },
                originalTitle = o.optString("original"),
                nsfw = o.optBoolean("nsfw", false),
                nextEpisodeDate = o.optString("next").takeIf { it.isNotBlank() && it != "null" },
                metadataSource = "AniList",
            )
            list.add(SpotlightEntry(item, o.optInt("episode", 0).takeIf { it > 0 }))
        }
        at to list
    }.getOrNull()

    private fun writeSpotlight(at: Long, entries: List<SpotlightEntry>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { e ->
                val item = e.item
                arr.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("type", item.type.name)
                        .put("poster", item.posterUrl.orEmpty())
                        .put("backdrop", item.backdropUrl.orEmpty())
                        .put("year", item.year ?: 0)
                        .put("overview", item.overview.orEmpty())
                        .put("genres", JSONArray(item.genres))
                        .put("rating", item.rating ?: 0.0)
                        .put("original", item.originalTitle)
                        .put("nsfw", item.nsfw)
                        .put("next", item.nextEpisodeDate.orEmpty())
                        .put("episode", e.nextEpisode ?: 0),
                )
            }
            val root = JSONObject().put("at", at).put("entries", arr)
            val f = spotlightFile()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(root.toString())
                tmp.delete()
            }
        }
    }
}
