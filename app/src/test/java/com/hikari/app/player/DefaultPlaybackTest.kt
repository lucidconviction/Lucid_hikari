package com.hikari.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultPlaybackTest {

    @Test
    fun videoPrefersHighestAtOrBelow() {
        val tracks = listOf(
            DefaultPlayback.VideoTrack(0, 360),
            DefaultPlayback.VideoTrack(1, 720),
            DefaultPlayback.VideoTrack(2, 1080),
            DefaultPlayback.VideoTrack(3, 2160),
        )
        assertEquals(2, DefaultPlayback.chooseVideoIndex(tracks, 1080))
        assertEquals(1, DefaultPlayback.chooseVideoIndex(tracks, 900))
        assertEquals(3, DefaultPlayback.chooseVideoIndex(tracks, 4320))
        assertNull(DefaultPlayback.chooseVideoIndex(tracks, 0))
    }

    @Test
    fun videoFallsBackToNearestAboveWhenNothingFits() {
        val tracks = listOf(
            DefaultPlayback.VideoTrack(0, 720),
            DefaultPlayback.VideoTrack(1, 1080),
        )
        assertEquals(0, DefaultPlayback.chooseVideoIndex(tracks, 480))
    }

    @Test
    fun audioMatchesLanguageTagAndRegion() {
        val tracks = listOf(
            DefaultPlayback.AudioTrack(0, "en-US", "English"),
            DefaultPlayback.AudioTrack(1, "hi-IN", "Hindi"),
            DefaultPlayback.AudioTrack(2, "es-ES", "Spanish"),
        )
        assertEquals(1, DefaultPlayback.chooseAudioIndex(tracks, "hi"))
        assertEquals(0, DefaultPlayback.chooseAudioIndex(tracks, "en-GB"))
        assertNull(DefaultPlayback.chooseAudioIndex(tracks, "ja"))
        assertNull(DefaultPlayback.chooseAudioIndex(tracks, ""))
    }

    @Test
    fun audioFallsBackToDisplayNameInLabel() {
        val tracks = listOf(
            DefaultPlayback.AudioTrack(0, "und", "Hindi"),
            DefaultPlayback.AudioTrack(1, "und", "English"),
        )
        assertEquals(1, DefaultPlayback.chooseAudioIndex(tracks, "en"))
    }
}
