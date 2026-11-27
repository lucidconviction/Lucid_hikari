package com.hikari.app.web.market

import com.hikari.app.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI

/**
 * ThePornDude's side of the market.
 *
 * Each category page embeds a Schema.org `ItemList` with the ranked sites and
 * their internal review paths. The review's JSON endpoint is the useful part:
 * it carries `display_slug` (the real site), `data_category_link` (sometimes an
 * affiliate redirect) and `is_deadsite`. That is far more stable than scraping
 * the visual ranking, and it lets us skip dead entries and never store a
 * `pdude.link` redirect.
 *
 * `free-onlyfans-accounts` is the one category whose `display_slug` is only the
 * platform root (`onlyfans.com`). For those entries we follow the redirect once
 * and keep the profile segment (`/name/c89` -> `/name`), so the user gets the
 * creator's page rather than an affiliate landing page.
 */
object ThePornDudeMarketSource {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val blockedHosts = setOf("pdude.link", "theporndude.com", "www.theporndude.com")

    data class Ranked(val name: String, val reviewPath: String, val rank: Int)

    data class Review(val displayUrl: String, val affiliateUrl: String, val dead: Boolean)

    /** Reads the first ItemList JSON-LD on a category page. */
    fun parseListing(html: String): List<Ranked> {
        val doc = Jsoup.parse(html)
        val root = doc.select("script[type=application/ld+json]").asSequence()
            .mapNotNull { script ->
                runCatching { json.parseToJsonElement(script.data()).jsonObject }.getOrNull()
            }
            .firstOrNull { obj ->
                obj["@type"]?.jsonPrimitive?.contentOrNull == "ItemList" ||
                    (
                        obj["@type"] == null &&
                            obj["itemListElement"]?.let { runCatching { it.jsonArray.isNotEmpty() }.getOrDefault(false) } == true
                        )
            } ?: return emptyList()

        val items = root["itemListElement"]?.jsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val rank = obj["position"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (name.isBlank() || !url.contains("theporndude.com/")) return@mapNotNull null
            val path = url.substringAfter("theporndude.com/").trim('/')
            if (path.isBlank()) return@mapNotNull null
            Ranked(name, path, rank)
        }.sortedBy { it.rank }
    }

    /** Reads the review endpoint's useful fields. */
    fun parseReview(text: String): Review? = runCatching {
        val obj = json.parseToJsonElement(text).jsonObject
        Review(
            displayUrl = obj["display_slug"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            affiliateUrl = obj["data_category_link"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            dead = obj["is_deadsite"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }.getOrNull()

    /**
     * Turns a review into the URL the market will open.
     *
     * [resolve] follows an affiliate redirect (the real implementation uses
     * OkHttp; tests pass a lambda). It is only consulted for a platform root —
     * normal sites use [displayUrl] directly, which is the canonical link and
     * avoids the affiliate wrapper entirely.
     */
    fun canonicalUrl(
        displayUrl: String,
        affiliateUrl: String,
        resolve: (String) -> String? = ::resolveRedirect,
    ): String? {
        val display = displayUrl.trim()
        val displayUri = runCatching { URI(display) }.getOrNull()
        val displayHost = displayUri?.host?.removePrefix("www.")?.lowercase().orEmpty()

        if (displayHost.isBlank()) {
            val finalUrl = resolve(affiliateUrl) ?: return null
            return usableDirectUrl(finalUrl)
        }
        if (displayHost in blockedHosts) return null

        val isOnlyFansRoot = displayHost == "onlyfans.com" &&
            displayUri?.path.orEmpty().trim('/').isBlank()
        if (!isOnlyFansRoot) return usableDirectUrl(display)

        val finalUrl = resolve(affiliateUrl) ?: return null
        val finalUri = runCatching { URI(finalUrl) }.getOrNull() ?: return null
        val finalHost = finalUri.host?.removePrefix("www.")?.lowercase().orEmpty()
        if (!finalHost.endsWith("onlyfans.com")) return null
        val profile = finalUri.path.orEmpty()
            .trim('/')
            .split('/')
            .firstOrNull { it.isNotBlank() }
            ?: return null
        return "https://onlyfans.com/$profile"
    }

    private fun usableDirectUrl(raw: String): String? {
        val value = raw.trim()
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) return null
        val host = runCatching { URI(value).host }.getOrNull()
            ?.removePrefix("www.")
            ?.lowercase()
            .orEmpty()
        if (host.isBlank() || host in blockedHosts) return null
        return value
    }

    /**
     * Fetches one adult category. Returns an empty list on any source failure so
     * the repository can keep the previous category on screen.
     */
    fun fetchCategory(category: MarketCategory): List<MarketApp> {
        val html = Http.getStringQuiet(category.sourceUrl, headers()) ?: return emptyList()
        val ranked = parseListing(html)
        if (ranked.isEmpty()) return emptyList()

        val apps = mutableListOf<MarketApp>()
        // A few extra entries: the top of the list occasionally contains a dead
        // site, and the next one should fill the tab instead of leaving a hole.
        for (item in ranked.take(category.cap + 5)) {
            if (apps.size >= category.cap) break
            val reviewText = Http.getStringQuiet(
                "https://theporndude.com/json/${item.reviewPath}",
                headers(),
            ) ?: continue
            val review = parseReview(reviewText) ?: continue
            if (review.dead) continue
            val url = canonicalUrl(review.displayUrl, review.affiliateUrl) ?: continue
            apps += MarketApp(
                name = item.name,
                url = url,
                categoryId = category.id,
                source = MarketSource.TPD.key,
                rank = apps.size + 1,
                adult = true,
            )
        }
        return apps
    }

    private fun headers() = mapOf(
        "Accept" to "text/html,application/xhtml+xml",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private fun resolveRedirect(url: String): String? {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        return runCatching {
            Http.getQuiet(url, headers()).use { response ->
                if (response.isSuccessful) response.request.url.toString() else null
            }
        }.getOrNull()
    }
}
