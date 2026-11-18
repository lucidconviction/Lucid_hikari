package com.hikari.app.data

import com.hikari.app.nuvio.TmdbResolver

/**
 * The streaming platforms on Home's "Streaming services" row.
 *
 * The list is VyseTV's: the same TMDB watch-provider ids, in the same order, so
 * the row carries every service that app shows (Netflix, Disney+, Prime Video,
 * HBO Max, Hulu, Apple TV, Paramount+, Crunchyroll, Peacock, Starz, … down to
 * the niche ones: Criterion, MUBI, Kanopy, Hoopla, HiDive, Shudder, BritBox).
 *
 * Names and logos are read from TMDB's own provider catalogue at runtime, so a
 * service that rebrands gets its new mark without an app update; [FALLBACK]
 * only covers the first launch, an offline device or a TMDB failure.
 */
object StreamingPlatforms {

    data class Platform(
        val id: Int,
        val name: String,
        /** TMDB artwork path (`/xxx.png`), null until the catalogue loads. */
        val logoPath: String? = null,
    )

    /**
     * VyseTV's featured order (its `_featuredProviders`). Order matters: the
     * mainstream services are first, so the row is useful without scrolling.
     */
    private val FEATURED_IDS = listOf(
        8, 337, 9, 1899, 15, 350, 2303, 283, 386, 43, 34, 526, 520, 1768, 73, 300,
        207, 538, 257, 2383, 299, 457, 430, 99, 151, 87, 258, 11, 191, 212, 192,
        188, 2, 10, 3, 7,
    )

    /** Offline / first-launch names, in [FEATURED_IDS] order. */
    private val FALLBACK_NAMES = listOf(
        "Netflix", "Disney+", "Prime Video", "Max", "Hulu", "Apple TV+",
        "Paramount+", "Crunchyroll", "Peacock", "Starz", "MGM+", "AMC+",
        "Discovery+", "ESPN+", "Tubi", "Pluto TV", "Roku Channel", "Plex",
        "fuboTV", "Philo", "Sling TV", "ViX", "HiDive", "Shudder", "BritBox",
        "Acorn TV", "Criterion Channel", "MUBI", "Kanopy", "Hoopla", "YouTube",
        "YouTube Premium", "Apple TV Store", "Amazon Video", "Google Play Movies",
        "Fandango at Home",
    )

    /** The row before the catalogue has loaded (and if TMDB cannot answer). */
    fun fallback(): List<Platform> =
        FEATURED_IDS.mapIndexed { i, id ->
            Platform(id, FALLBACK_NAMES.getOrElse(i) { id.toString() })
        }

    /**
     * Every featured service TMDB knows for [region], in VyseTV's order.
     *
     * Movie and TV catalogues differ (fubo and BritBox are TV-only), so both
     * are fetched and merged — the same thing VyseTV does. A service whose
     * logo is missing still appears: the row draws its initial.
     */
    suspend fun catalogue(region: String = "US"): List<Platform> {
        val byId = HashMap<Int, Platform>()
        for (segment in listOf("movie", "tv")) {
            val data = runCatching {
                TmdbResolver.apiGet("/watch/providers/$segment", mapOf("watch_region" to region))
            }.getOrNull() ?: continue
            val arr = data.optJSONArray("results") ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("provider_id", 0)
                if (id <= 0) continue
                val raw = o.optString("provider_name")
                val logo = o.optString("logo_path").takeIf { it.isNotBlank() }
                byId.putIfAbsent(id, Platform(id, displayName(raw), logo))
            }
        }
        val out = FEATURED_IDS.mapNotNull { byId[it] }
        return out.ifEmpty { fallback() }
    }

    /** TMDB's catalogue names, shortened to what a tile can show. */
    private fun displayName(raw: String): String {
        val name = raw.trim()
        return when (name) {
            "Disney Plus" -> "Disney+"
            "Amazon Prime Video" -> "Prime Video"
            "HBO Max" -> "Max"
            "Apple TV" -> "Apple TV+"
            "Paramount Plus Premium", "Paramount Plus" -> "Paramount+"
            "Peacock Premium", "Peacock Premium Plus" -> "Peacock"
            "Discovery +" -> "Discovery+"
            "ESPN Plus" -> "ESPN+"
            "Tubi TV" -> "Tubi"
            "The Roku Channel" -> "Roku Channel"
            "Sling TV Orange and Blue" -> "Sling TV"
            "Fandango At Home" -> "Fandango at Home"
            else -> name
        }
    }

    /** The discover source a tile opens: popular titles on this platform. */
    fun specFor(platform: Platform): TmdbSpec = TmdbSpec(
        type = TmdbSourceType.DISCOVER,
        media = "",
        sort = "popularity.desc",
        providers = platform.id.toString(),
        region = "US",
        title = platform.name,
    )
}
