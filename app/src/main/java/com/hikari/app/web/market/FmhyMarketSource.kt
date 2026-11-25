package com.hikari.app.web.market

import com.hikari.app.net.Http
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/**
 * FMHY's side of the market.
 *
 * `fmhy.net/video` is a VitePress page: each streaming category is an
 * `<h3 id="…">`, followed by `<ul><li>` entries whose first real external link
 * is the site. Numbered links after it are mirrors, and the icon links beside
 * it are Discord/Telegram/status pages; neither is a market app. The section id
 * is the stable key, so a change to the visible heading does not move a tab.
 *
 * Parsing is separated from fetching so the parser can be unit-tested against a
 * fixture without a network or Android runtime.
 */
object FmhyMarketSource {

    /** Chat/community links that FMHY puts beside its entries. YouTube, Twitch,
     *  GitHub and the actual sites are all legitimate destinations. */
    private val blockedHosts = setOf(
        "fmhy.net",
        "discord.com",
        "discord.gg",
        "t.me",
        "telegram.me",
        "reddit.com",
        "www.reddit.com",
    )

    private val control = Regex("[\\p{Cc}]")
    private val spaces = Regex("\\s+")
    private val mirrorLabel = Regex("^\\d+$")

    data class Site(val name: String, val url: String)

    /** Parses the whole page into apps in category order. */
    fun parse(html: String): List<MarketApp> {
        val doc = Jsoup.parse(html, MarketCategories.FMHY_PAGE)
        val out = mutableListOf<MarketApp>()

        for (heading in doc.select("main h3[id]")) {
            val category = MarketCategories.category(heading.id()) ?: continue
            if (category.source != MarketSource.FMHY || category.adult) continue

            runCatching {
                val items = heading.nextElementSiblings()
                    .takeWhile { it.tagName() != "h2" && it.tagName() != "h3" }
                    .flatMap { it.select("li") }
                val seen = HashSet<String>()
                var rank = 0
                for (li in items) {
                    val site = firstSite(li) ?: continue
                    if (!seen.add(keyOf(site.url))) continue
                    rank += 1
                    out += MarketApp(
                        name = site.name,
                        url = site.url,
                        categoryId = category.id,
                        source = MarketSource.FMHY.key,
                        rank = rank,
                        adult = false,
                    )
                    if (rank >= category.cap) break
                }
            }
        }
        return out
    }

    /** Fetches and parses FMHY. An empty list means "leave the cache alone". */
    fun fetch(): List<MarketApp> {
        val html = Http.getStringQuiet(
            MarketCategories.FMHY_PAGE,
            mapOf("Accept" to "text/html,application/xhtml+xml"),
        ) ?: return emptyList()
        return parse(html)
    }

    /**
     * The first external anchor that is not a numbered mirror or a chat link.
     * FMHY marks the featured entry with a star icon before the link, so the
     * anchor itself is the right place to read the name from.
     */
    internal fun firstSite(li: Element): Site? {
        for (anchor in li.select("a[href]")) {
            val raw = anchor.attr("href").trim()
            if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) continue
            val host = hostOf(raw)
            if (host.isBlank() || host in blockedHosts) continue
            var name = clean(anchor.text())
            if (mirrorLabel.matches(name)) continue
            if (name.isBlank()) {
                name = clean(li.selectFirst("strong")?.text().orEmpty())
            }
            if (name.isBlank()) name = host
            if (anchor.selectFirst(".new-badge, .new") != null ||
                li.selectFirst(".new-badge, .new") != null
            ) {
                name = name.removeSuffix("New").trim()
            }
            if (name.isBlank()) continue
            return Site(clean(name), raw)
        }
        return null
    }

    private fun clean(value: String): String = value
        .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
        .replace(control, " ")
        .replace(spaces, " ")
        .trim()

    internal fun hostOf(url: String): String =
        runCatching { URI(url).host }.getOrNull()
            ?.removePrefix("www.")
            ?.lowercase()
            .orEmpty()

    private fun keyOf(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url.lowercase()
        val host = uri.host?.removePrefix("www.")?.lowercase().orEmpty()
        val path = uri.path.orEmpty().trimEnd('/')
        return "$host$path".ifBlank { url.lowercase() }
    }
}
