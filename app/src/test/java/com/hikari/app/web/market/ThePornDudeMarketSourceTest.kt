package com.hikari.app.web.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThePornDudeMarketSourceTest {

    @Test
    fun readsTheFirstItemListInRankOrder() {
        val html = """
            <html><head>
              <script type="application/ld+json">{"@type":"BreadcrumbList","itemListElement":[]}</script>
              <script type="application/ld+json">
              {"@type":"ItemList","numberOfItems":3,"itemListElement":[
                {"@type":"ListItem","position":3,"name":"Third","url":"https://theporndude.com/3/third"},
                {"@type":"ListItem","position":1,"name":"First","url":"https://theporndude.com/1/first"},
                {"@type":"ListItem","position":2,"name":"Second","url":"https://theporndude.com/2/second"}
              ]}</script>
            </head></html>
        """.trimIndent()

        val ranked = ThePornDudeMarketSource.parseListing(html)
        assertEquals(listOf("First", "Second", "Third"), ranked.map { it.name })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals("1/first", ranked.first().reviewPath)
    }

    @Test
    fun readsReviewFields() {
        val review = ThePornDudeMarketSource.parseReview(
            """{"display_slug":"https://pornhub.com","data_category_link":"https://www.pornhub.com/","is_deadsite":false}"""
        )
        assertEquals("https://pornhub.com", review?.displayUrl)
        assertEquals("https://www.pornhub.com/", review?.affiliateUrl)
        assertEquals(false, review?.dead)

        val dead = ThePornDudeMarketSource.parseReview("""{"is_deadsite":true}""")
        assertEquals(true, dead?.dead)
    }

    @Test
    fun prefersTheCanonicalDisplayUrl() {
        val url = ThePornDudeMarketSource.canonicalUrl(
            displayUrl = "https://pornhub.com",
            affiliateUrl = "https://pdude.link/pornhub",
        ) { error("the resolver must not be called for a direct site") }
        assertEquals("https://pornhub.com", url)
    }

    @Test
    fun stripsTheOnlyFansReferralSuffix() {
        val url = ThePornDudeMarketSource.canonicalUrl(
            displayUrl = "https://onlyfans.com",
            affiliateUrl = "https://pdude.link/skylar",
        ) { "https://onlyfans.com/skylarfreexo/c89?utm_source=tpd" }
        assertEquals("https://onlyfans.com/skylarfreexo", url)
    }

    @Test
    fun rejectsAnOnlyFansRedirectThatEndsSomewhereElse() {
        val url = ThePornDudeMarketSource.canonicalUrl(
            displayUrl = "https://onlyfans.com",
            affiliateUrl = "https://pdude.link/skylar",
        ) { "https://example.com/landing" }
        assertNull(url)
    }

    @Test
    fun rejectsAffiliateOnlyEntries() {
        assertNull(
            ThePornDudeMarketSource.canonicalUrl(
                displayUrl = "https://pdude.link/x",
                affiliateUrl = "https://pdude.link/x",
            )
        )
        assertTrue(
            ThePornDudeMarketSource.canonicalUrl(
                displayUrl = "",
                affiliateUrl = "https://pdude.link/x",
            ) { "https://example.com/" } == "https://example.com/"
        )
    }
}
