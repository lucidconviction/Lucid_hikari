package com.hikari.app.web.market

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarketCatalogTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun seed(): MarketCatalog {
        val candidates = listOf(
            File("src/main/assets/webapp_market_seed.json"),
            File("app/src/main/assets/webapp_market_seed.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("webapp_market_seed.json not found from ${File(".").absolutePath}")
        return json.decodeFromString(MarketCatalog.serializer(), file.readText())
    }

    @Test
    fun seedIsTheCurrentSchemaAndSfwOnly() {
        val catalog = seed()
        assertEquals(MarketCatalog.SCHEMA, catalog.schema)
        assertTrue("seed is empty", catalog.apps.isNotEmpty())
        assertTrue("seed has adult entries", catalog.apps.none { it.adult })
        assertTrue("seed has a non-FMHY entry", catalog.apps.all { it.source == MarketSource.FMHY.key })
    }

    @Test
    fun seedUsesKnownCategoriesAndRespectsCaps() {
        val catalog = seed()
        val known = MarketCategories.all.associateBy { it.id }
        for (app in catalog.apps) {
            assertTrue("unknown category ${app.categoryId}", app.categoryId in known)
            val category = known.getValue(app.categoryId)
            assertTrue("too many apps in ${category.id}", catalog.apps.count { it.categoryId == category.id } <= category.cap)
            assertTrue("bad url ${app.url}", app.url.startsWith("http://") || app.url.startsWith("https://"))
            assertTrue("bad rank ${app.rank}", app.rank in 1..category.cap)
        }
    }

    @Test
    fun seedHasNoDuplicateEntriesInACategory() {
        val catalog = seed()
        for (category in MarketCategories.fmhy) {
            val apps = catalog.apps.filter { it.categoryId == category.id }
            assertEquals(
                "duplicate in ${category.id}",
                apps.size,
                apps.map { it.url }.distinct().size,
            )
        }
    }

    @Test
    fun seedHasTheExpandedSfwCatalogue() {
        val catalog = seed()
        assertEquals(414, catalog.apps.size)
    }

    @Test
    fun theTwoShortSectionsKeepWhatTheSourceHas() {
        val catalog = seed()
        assertEquals(6, catalog.apps.count { it.categoryId == "p-stream-forks" })
        assertEquals(10, catalog.apps.count { it.categoryId == "cartoon-streaming" })
    }

    @Test
    fun searchMatchesNameHostAndCategory() {
        val app = MarketApp(
            name = "ArrowTV",
            url = "https://arrowtv.net/",
            categoryId = "stream-aggregators",
            source = "fmhy",
            rank = 1,
        )
        assertTrue(MarketCategories.matches("arrow", app))
        assertTrue(MarketCategories.matches("arrowtv.net", app))
        assertTrue(MarketCategories.matches("aggregator", app))
        assertTrue(MarketCategories.matches("", app))
        assertTrue(!MarketCategories.matches("anime", app))
    }
}
