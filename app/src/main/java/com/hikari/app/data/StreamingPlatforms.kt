package com.hikari.app.data

import com.hikari.app.nuvio.TmdbResolver

/**
 * The streaming platforms on Home's "Streaming services" row.
 *
 * The row is VyseTV's: real services (Netflix, Hulu, Disney+…) rather than the
 * app's own extensions, because a viewer thinks in services, not in scrapers.
 * Each tile opens a TMDB discover grid filtered by that platform's watch
 * provider, which is exactly what VyseTV's provider shelf does.
 *
 * [providerId] is TMDB's watch-provider id for the US catalogue (the ids the
 * service listing publishes); [domain] is only a fallback for the logo when
 * TMDB has no artwork.
 */
object StreamingPlatforms {

    data class Platform(
        val key: String,
        val name: String,
        val domain: String,
        val providerId: Int,
    )

    val ALL: List<Platform> = listOf(
        Platform("netflix", "Netflix", "netflix.com", 8),
        Platform("prime_video", "Prime Video", "primevideo.com", 9),
        Platform("disney_plus", "Disney+", "disneyplus.com", 337),
        Platform("hulu", "Hulu", "hulu.com", 15),
        Platform("max", "Max", "max.com", 1899),
        Platform("apple_tv", "Apple TV+", "tv.apple.com", 350),
        Platform("paramount", "Paramount+", "paramountplus.com", 531),
        Platform("peacock", "Peacock", "peacocktv.com", 386),
        Platform("crunchyroll", "Crunchyroll", "crunchyroll.com", 283),
        Platform("youtube", "YouTube", "youtube.com", 192),
    )

    /**
     * provider id → TMDB logo path for [region], merged from the movie and TV
     * catalogues (a service can appear in one and not the other).
     *
     * One request pair per session: the row asks once and every tile renders
     * from the map. A failure just means the tiles fall back to their domain
     * favicon, never an empty square.
     */
    suspend fun logos(region: String = "US"): Map<Int, String> {
        val out = HashMap<Int, String>()
        for (segment in listOf("movie", "tv")) {
            val data = runCatching {
                TmdbResolver.apiGet("/watch/providers/$segment", mapOf("watch_region" to region))
            }.getOrNull() ?: continue
            val arr = data.optJSONArray("results") ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("provider_id", 0)
                val logo = o.optString("logo_path")
                if (id > 0 && logo.isNotBlank()) out.putIfAbsent(id, logo)
            }
        }
        return out
    }

    /** The discover source a tile opens: popular titles on this platform. */
    fun specFor(platform: Platform): TmdbSpec = TmdbSpec(
        type = TmdbSourceType.DISCOVER,
        media = "",
        sort = "popularity.desc",
        providers = platform.providerId.toString(),
        region = "US",
        title = platform.name,
    )
}
