package com.hikari.app.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTagsTest {

    @Test
    fun `parses number language and quality`() {
        val tags = FeedTagsParser.parse("AR: beIN SPORTS 1 HD")
        assertEquals("1", tags.number)
        assertEquals("AR", tags.lang)
        assertEquals("HD", tags.quality)
    }

    @Test
    fun `a four digit resolution is never a channel number`() {
        val tags = FeedTagsParser.parse("Sky Sports Main Event 1080p")
        assertEquals(null, tags.number)
        assertEquals("FHD", tags.quality)
    }

    @Test
    fun `4k wins over hd`() {
        val tags = FeedTagsParser.parse("beIN SPORTS 2 4K HD")
        assertEquals("2", tags.number)
        assertEquals("4K", tags.quality)
    }

    @Test
    fun `language is optional`() {
        val tags = FeedTagsParser.parse("ESPN 2 FHD")
        assertEquals("2", tags.number)
        assertEquals(null, tags.lang)
        assertEquals("FHD", tags.quality)
    }

    @Test
    fun `f1 is not a channel number`() {
        val tags = FeedTagsParser.parse("F1 Live")
        assertEquals(null, tags.number)
    }

    @Test
    fun `quality rank orders 4k above fhd above hd`() {
        assertTrue(FeedTagsParser.qualityRank("4K") > FeedTagsParser.qualityRank("FHD"))
        assertTrue(FeedTagsParser.qualityRank("FHD") > FeedTagsParser.qualityRank("HD"))
        assertTrue(FeedTagsParser.qualityRank("HD") > FeedTagsParser.qualityRank("SD"))
        assertEquals(0, FeedTagsParser.qualityRank(null))
    }

    @Test
    fun `a filter matches only the chosen dimensions`() {
        val tags = FeedTagsParser.parse("beIN SPORTS 2 AR FHD")
        assertTrue(FeedFilter(number = "2").matches(tags))
        assertTrue(FeedFilter(lang = "AR", quality = "FHD").matches(tags))
        assertFalse(FeedFilter(number = "1").matches(tags))
        assertFalse(FeedFilter(lang = "FR").matches(tags))
        assertTrue(FeedFilter().matches(tags))
    }
}
