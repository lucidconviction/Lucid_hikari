package com.hikari.app.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The byte classification behind the Live TV health dot. These are the cases
 * that used to be indistinguishable: every one of them answered HTTP 200, so
 * before this the "green" dot meant "a web server exists", not "this plays".
 */
class StreamProbeClassifyTest {

    private fun classify(
        bytes: ByteArray,
        code: Int = 200,
        contentType: String = "video/mp2t",
        url: String = "http://host/live/user/pass/1.ts",
        contentLength: Long = -1L,
        ended: Boolean = false,
    ): Verdict = StreamProbe.classifyHead(
        code = code,
        contentType = contentType,
        url = url,
        contentLength = contentLength,
        bytes = bytes,
        length = bytes.size,
        ended = ended,
    )

    @Test
    fun `mpeg ts with sync bytes is live`() {
        val bytes = ByteArray(188 * 5)
        for (index in bytes.indices step 188) bytes[index] = 0x47
        assertEquals(Verdict.LIVE, classify(bytes))
    }

    @Test
    fun `a 200 html error page is not video`() {
        val bytes = "<html><body>Account expired</body></html>".toByteArray()
        assertEquals(
            Verdict.NOT_VIDEO,
            classify(bytes, contentType = "text/html; charset=utf-8"),
        )
    }

    @Test
    fun `an hls playlist is live`() {
        val bytes = "#EXTM3U\n#EXT-X-VERSION:3\n".toByteArray()
        assertEquals(
            Verdict.LIVE,
            classify(bytes, contentType = "application/vnd.apple.mpegurl", url = "http://host/x.m3u8"),
        )
    }

    @Test
    fun `an hls url that serves a web page is not video`() {
        val bytes = "<html>nope</html>".toByteArray()
        assertEquals(
            Verdict.NOT_VIDEO,
            classify(bytes, contentType = "application/x-mpegurl", url = "http://host/x.m3u8"),
        )
    }

    @Test
    fun `an mp4 ftyp box is live`() {
        val bytes = ByteArray(64)
        "ftyp".toByteArray().copyInto(bytes, 4)
        assertEquals(Verdict.LIVE, classify(bytes, contentType = "video/mp4"))
    }

    @Test
    fun `a short unknown body is dead`() {
        val bytes = ByteArray(1024) { 0x11 }
        assertEquals(
            Verdict.DEAD,
            classify(bytes, contentType = "application/octet-stream", ended = true),
        )
    }

    @Test
    fun `a large unknown body is live`() {
        val bytes = ByteArray(40 * 1024) { 0x11 }
        assertEquals(Verdict.LIVE, classify(bytes, contentType = "application/octet-stream"))
    }

    @Test
    fun `an error status is dead`() {
        assertEquals(Verdict.DEAD, classify(ByteArray(1024), code = 404))
    }

    @Test
    fun `a placeholder content length is not video`() {
        // Enough bytes to pass the short-body rule, but the host declares a
        // 2 KB file: that is a placeholder/manifest masquerading as media.
        val bytes = ByteArray(32 * 1024) { 0x11 }
        assertEquals(
            Verdict.NOT_VIDEO,
            classify(bytes, contentLength = 2048L),
        )
    }
}
