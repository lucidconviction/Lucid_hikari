package com.hikari.app.iptv

import com.hikari.app.data.IptvChannel

/**
 * What a channel name says about which feed it is.
 *
 * A portal commonly carries the same network many times:
 *
 *     beIN SPORTS 1 AR HD
 *     beIN SPORTS 1 FR FHD
 *     beIN SPORTS 2 AR 4K
 *
 * They are one network, not three channels. Splitting the name into a number,
 * a language/region and a quality is what lets a wall or a Quick Channel sheet
 * offer "1 / 2 / 3", "AR / FR / EN" and "4K / FHD / HD" instead of a long list
 * of near-duplicates.
 *
 * Parsing is deliberately conservative: a number is only a 1-2 digit token (so
 * a year or a resolution never becomes a channel number), a language only
 * comes from the fixed set below, and quality follows a precedence rather than
 * whichever token appears first.
 */
data class FeedTags(
    val number: String?,
    val lang: String?,
    val quality: String?,
)

/**
 * The chosen dimensions. A null field means "all"; [matches] is the filter.
 */
data class FeedFilter(
    val number: String? = null,
    val lang: String? = null,
    val quality: String? = null,
) {
    val active: Boolean get() = number != null || lang != null || quality != null

    fun matches(tags: FeedTags): Boolean =
        (number == null || tags.number == number) &&
            (lang == null || tags.lang == lang) &&
            (quality == null || tags.quality == quality)
}

object FeedTagsParser {
    /**
     * The tokens treated as a language or region. Kept identical to the set the
     * Live TV port has always used, so a feed labelled "AR" or "MENA" is
     * grouped the same way it was before filters existed.
     */
    private val LANGS = setOf(
        "AR", "EN", "FR", "TR", "ES", "DE", "IT", "PT", "NL", "PL", "RU", "UK",
        "US", "CA", "MENA", "FA", "UR", "HI", "IN", "BR", "MX", "GR", "RO", "AL",
        "SE", "NO", "DK", "FI", "KU", "HE", "ID", "MY", "PH", "AF", "BE", "CH",
        "AT", "KR", "JP",
    )

    private val SEPARATORS = Regex("[^A-Z0-9+]+")

    fun parse(name: String): FeedTags {
        val tokens = name.uppercase().split(SEPARATORS).filter { it.isNotEmpty() }
        val number = tokens.firstOrNull { token ->
            token.length in 1..2 && token.all { it.isDigit() }
        }
        val language = tokens.firstOrNull { it in LANGS }
        val quality = when {
            tokens.any { it == "4K" || it == "UHD" } -> "4K"
            tokens.any { it == "FHD" || it == "1080" || it == "1080P" } -> "FHD"
            tokens.contains("HD") -> "HD"
            tokens.contains("SD") -> "SD"
            else -> null
        }
        return FeedTags(number, language, quality)
    }

    /** Higher wins when sorting equal feeds. */
    fun qualityRank(quality: String?): Int = when (quality) {
        "4K" -> 4
        "FHD" -> 3
        "HD" -> 2
        "SD" -> 1
        else -> 0
    }
}

/**
 * The order a source list should be read in:
 *
 *  1. sources the user pinned,
 *  2. sources whose EPG says they are showing the game (sports),
 *  3. sources the health check says are alive,
 *  4. the best quality,
 *  5. name.
 *
 * All arguments are by URL, so the same channel on two portals stays two rows.
 */
fun sortFeedChannels(
    channels: List<IptvChannel>,
    alive: Map<String, Boolean> = emptyMap(),
    pinned: Set<String> = emptySet(),
    matched: Set<String> = emptySet(),
): List<IptvChannel> = channels.sortedWith(
    compareByDescending<IptvChannel> { it.url in pinned }
        .thenByDescending { it.url in matched }
        .thenByDescending { alive[it.url] == true }
        .thenByDescending { FeedTagsParser.qualityRank(FeedTagsParser.parse(it.name).quality) }
        .thenBy { it.name.lowercase() },
)

