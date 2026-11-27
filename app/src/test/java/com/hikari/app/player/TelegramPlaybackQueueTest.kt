package com.hikari.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPlaybackQueueTest {

    private fun item(title: String, fileId: Int = 0) =
        TelegramPlaybackQueue.Item(title, """[{"name":"Telegram","url":"hikari-td://file?id=$fileId"}]""", fileId)

    @Test
    fun advancesThroughTheLoadedList() {
        val queue = TelegramPlaybackQueue
        queue.clear()
        queue.set(listOf(item("One"), item("Two"), item("Three")), 0)
        assertEquals("One", queue.current()?.title)
        assertTrue(queue.hasNext())
        assertEquals("Two", queue.next()?.title)
        assertEquals("Two", queue.advance()?.title)
        assertEquals("Three", queue.next()?.title)
        assertEquals("Three", queue.advance()?.title)
        assertFalse(queue.hasNext())
        assertNull(queue.advance())
        queue.clear()
    }

    @Test
    fun lastItemHasNoNext() {
        val queue = TelegramPlaybackQueue
        queue.clear()
        queue.set(listOf(item("Only")), 0)
        assertFalse(queue.hasNext())
        assertNull(queue.next())
        assertNull(queue.advance())
        queue.clear()
    }

    @Test
    fun emptyQueueIsSafe() {
        val queue = TelegramPlaybackQueue
        queue.clear()
        assertNull(queue.current())
        assertNull(queue.next())
        assertFalse(queue.hasNext())
        assertNull(queue.advance())
    }

    @Test
    fun settingAnOutOfRangeIndexClamps() {
        val queue = TelegramPlaybackQueue
        queue.clear()
        queue.set(listOf(item("One"), item("Two")), 99)
        assertEquals("Two", queue.current()?.title)
        queue.set(emptyList(), 0)
        assertNull(queue.current())
    }
}
