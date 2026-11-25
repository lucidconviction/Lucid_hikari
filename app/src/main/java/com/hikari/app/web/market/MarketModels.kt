package com.hikari.app.web.market

import kotlinx.serialization.Serializable

/**
 * The Web App Market's catalogue.
 *
 * The apps are the data; the groups and categories are code (see
 * [MarketCategories]), so a label or an order can never go missing because a
 * scrape went sideways. A market app is just a saved `Site` the user has not
 * opted into yet: tapping one opens the existing WebView, and its bookmark
 * button writes the same `Site(name, url)` the Web Apps tab already stores.
 */
@Serializable
data class MarketApp(
    val name: String,
    val url: String,
    val categoryId: String,
    /** "fmhy" or "tpd" — kept as a string so the cache stays readable. */
    val source: String,
    val rank: Int = 0,
    /** FMHY's own one-line descriptor. Deliberately empty for adult entries. */
    val note: String = "",
    val adult: Boolean = false,
)

@Serializable
data class MarketCatalog(
    val schema: Int = SCHEMA,
    val fetchedAt: Long = 0L,
    val apps: List<MarketApp> = emptyList(),
) {
    companion object {
        /** Bump when the JSON shape changes; a mismatch falls back to the seed. */
        const val SCHEMA = 1
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

enum class MarketSource(val key: String, val label: String) {
    FMHY("fmhy", "FMHY"),
    TPD("tpd", "ThePornDude"),
}

data class MarketCategory(
    /** Stable id. For FMHY categories it is the page's own `h3` id. */
    val id: String,
    val groupId: String,
    val label: String,
    val source: MarketSource,
    val cap: Int,
    val adult: Boolean,
    val sourceUrl: String,
    val blurb: String = "",
)

data class MarketGroup(
    val id: String,
    val label: String,
    val categories: List<MarketCategory>,
)

/**
 * The market's information architecture: six groups, 21 category tabs.
 *
 * FMHY's 16 streaming sections each contribute up to 10 top sites; the five
 * ThePornDude pages contribute 5 each. Adult categories are only rendered when
 * the app's existing NSFW switch is on, and are never part of the bundled seed.
 */
object MarketCategories {
    const val FMHY_PAGE = "https://fmhy.net/video"
    const val TPD_TUBES = "https://theporndude.com/top-porn-tube-sites"
    const val TPD_CAMS = "https://theporndude.com/top-sex-cam-sites"
    const val TPD_AI = "https://theporndude.com/ai-porn-sites"
    const val TPD_ONLYFANS = "https://theporndude.com/onlyfans-porn-sites"
    const val TPD_FREE_OF = "https://theporndude.com/free-onlyfans-accounts"

    private fun fmhy(id: String, group: String, label: String) = MarketCategory(
        id = id,
        groupId = group,
        label = label,
        source = MarketSource.FMHY,
        cap = 40,
        adult = false,
        sourceUrl = "$FMHY_PAGE#$id",
    )

    private fun tpd(id: String, label: String, url: String) = MarketCategory(
        id = id,
        groupId = GROUP_ADULT,
        label = label,
        source = MarketSource.TPD,
        cap = 20,
        adult = true,
        sourceUrl = url,
    )

    private const val GROUP_MOVIES = "movies"
    private const val GROUP_ANIME = "anime"
    private const val GROUP_LIVE = "live"
    private const val GROUP_SPORTS = "sports"
    private const val GROUP_ARCHIVES = "archives"
    private const val GROUP_ADULT = "adult"

    val all: List<MarketCategory> = listOf(
        // Movies & TV
        fmhy("stream-aggregators", GROUP_MOVIES, "Stream Aggregators"),
        fmhy("p-stream-forks", GROUP_MOVIES, "P-Stream Forks"),
        fmhy("dedicated-server", GROUP_MOVIES, "Dedicated Servers"),
        fmhy("multi-server", GROUP_MOVIES, "Multi-Server"),
        fmhy("stream-lounges", GROUP_MOVIES, "Stream Lounges"),
        fmhy("free-w-ads", GROUP_MOVIES, "Free with Ads"),
        fmhy("video-streaming", GROUP_MOVIES, "Video Streaming"),
        fmhy("tv-streaming", GROUP_MOVIES, "TV Streaming"),
        fmhy("drama-streaming", GROUP_MOVIES, "Drama Streaming"),
        // Anime & Cartoons
        fmhy("anime-streaming", GROUP_ANIME, "Anime Streaming"),
        fmhy("cartoon-streaming", GROUP_ANIME, "Cartoon Streaming"),
        // Live TV
        fmhy("live-tv", GROUP_LIVE, "Live TV"),
        // Sports
        fmhy("live-sports", GROUP_SPORTS, "Live Sports"),
        fmhy("sports-replays", GROUP_SPORTS, "Sports Replays"),
        // Archives
        fmhy("classics-public-domain", GROUP_ARCHIVES, "Classics / Public Domain"),
        fmhy("film-archives", GROUP_ARCHIVES, "Film Archives"),
        // Adult — runtime only, NSFW-gated
        tpd("tpd-tubes", "Tubes", TPD_TUBES),
        tpd("tpd-cams", "Cams", TPD_CAMS),
        tpd("tpd-ai", "AI", TPD_AI),
        tpd("tpd-onlyfans", "OnlyFans", TPD_ONLYFANS),
        tpd("tpd-free-of", "Free OF accounts", TPD_FREE_OF),
    )

    val groups: List<MarketGroup> = listOf(
        MarketGroup(GROUP_MOVIES, "Movies & TV", all.filter { it.groupId == GROUP_MOVIES }),
        MarketGroup(GROUP_ANIME, "Anime & Cartoons", all.filter { it.groupId == GROUP_ANIME }),
        MarketGroup(GROUP_LIVE, "Live TV", all.filter { it.groupId == GROUP_LIVE }),
        MarketGroup(GROUP_SPORTS, "Sports", all.filter { it.groupId == GROUP_SPORTS }),
        MarketGroup(GROUP_ARCHIVES, "Archives", all.filter { it.groupId == GROUP_ARCHIVES }),
        MarketGroup(GROUP_ADULT, "Adult", all.filter { it.groupId == GROUP_ADULT }),
    )

    private val byId = all.associateBy { it.id }

    fun category(id: String): MarketCategory? = byId[id]

    fun group(id: String): MarketGroup? = groups.firstOrNull { it.id == id }

    val fmhy: List<MarketCategory> = all.filter { it.source == MarketSource.FMHY }
    val adult: List<MarketCategory> = all.filter { it.adult }

    /** True when [query] matches an app's name, host or category label. */
    fun matches(query: String, app: MarketApp): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val category = byId[app.categoryId]?.label.orEmpty()
        return app.name.contains(q, ignoreCase = true) ||
            hostOf(app.url).contains(q, ignoreCase = true) ||
            category.contains(q, ignoreCase = true)
    }

    fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()
            ?.removePrefix("www.")
            ?.lowercase()
            .orEmpty()
            .ifBlank { url.substringAfter("://").substringBefore('/') }
}
