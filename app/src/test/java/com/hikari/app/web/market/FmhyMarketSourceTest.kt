package com.hikari.app.web.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FmhyMarketSourceTest {

    @Test
    fun readsTheSectionListAndSkipsMirrorsAndChatLinks() {
        val html = """
            <main>
              <h2>Streaming Sites</h2>
              <h3 id="stream-aggregators">Stream Aggregators</h3>
              <ul>
                <li><strong><a href="https://one.example/">One</a></strong>, <a href="https://mirror.example/">2</a> - 4K</li>
                <li><a href="https://discord.gg/chat">Discord</a> <a href="https://two.example/">Two</a></li>
                <li><a href="/internal">Internal</a></li>
                <li><a href="https://one.example/">One again</a></li>
                <li><a href="https://three.example/">Three</a></li>
              </ul>
              <h3 id="p-stream-forks">P-Stream Forks</h3>
              <ul>
                <li><a href="https://fork.example/">Fork</a></li>
              </ul>
              <h3 id="unknown-section">Unknown</h3>
              <ul><li><a href="https://ignored.example/">Ignored</a></li></ul>
            </main>
        """.trimIndent()

        val apps = FmhyMarketSource.parse(html)
        val aggregators = apps.filter { it.categoryId == "stream-aggregators" }
        assertEquals(listOf("One", "Two", "Three"), aggregators.map { it.name })
        assertEquals(listOf("https://one.example/", "https://two.example/", "https://three.example/"), aggregators.map { it.url })
        assertEquals(listOf(1, 2, 3), aggregators.map { it.rank })
        assertEquals(1, apps.count { it.categoryId == "p-stream-forks" })
        assertTrue(apps.none { it.categoryId == "unknown-section" })
    }

    @Test
    fun capsAtFortyPerSection() {
        val items = (1..44).joinToString("\n") {
            """<li><a href="https://site$it.example/">Site $it</a></li>"""
        }
        val html = """
            <main>
              <h3 id="live-tv">Live TV</h3>
              <ul>$items</ul>
            </main>
        """.trimIndent()

        val apps = FmhyMarketSource.parse(html).filter { it.categoryId == "live-tv" }
        assertEquals(40, apps.size)
        assertEquals(1, apps.first().rank)
        assertEquals(40, apps.last().rank)
    }

    @Test
    fun cleansZeroWidthCharactersAndNewBadges() {
        // A raw string does not process \u escapes, so interpolate the real
        // code point: the parser sees what FMHY's HTML actually carries.
        val zeroWidth = "\u2060"
        val html = """
            <main>
              <h3 id="stream-aggregators">Stream Aggregators</h3>
              <ul>
                <li><a href="https://clean.example/"><strong>Clean$zeroWidth</strong> <span class="new-badge">New</span></a></li>
              </ul>
            </main>
        """.trimIndent()

        val app = FmhyMarketSource.parse(html).first()
        assertEquals("Clean", app.name)
    }
}
