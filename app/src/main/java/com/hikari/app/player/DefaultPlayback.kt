package com.hikari.app.player

import java.util.Locale

/**
 * Pure resolution of the viewer's default video quality and audio language.
 *
 * Kept out of PlayerActivity so the rules are testable without a player:
 * quality means "the best rendition at or below the preferred height, else the
 * nearest one above"; audio means an exact language tag, then the language's
 * English display name in the track label.
 */
object DefaultPlayback {

    data class VideoTrack(
        val index: Int,
        val height: Int,
        val bitrate: Long = 0L,
    )

    data class AudioTrack(
        val index: Int,
        val language: String?,
        val label: String? = null,
    )

    /** [preferredHeight] 0 = Auto (no preference). */
    fun chooseVideoIndex(tracks: List<VideoTrack>, preferredHeight: Int): Int? {
        if (preferredHeight <= 0) return null
        val withHeight = tracks.filter { it.height > 0 }
        if (withHeight.isEmpty()) return null
        val atOrBelow = withHeight
            .filter { it.height <= preferredHeight }
            .maxWithOrNull(compareBy<VideoTrack> { it.height }.thenBy { it.bitrate })
        if (atOrBelow != null) return atOrBelow.index
        // Nothing at or below the preference: the nearest above is still a
        // better answer than silently ignoring the setting.
        return withHeight
            .minWithOrNull(compareBy<VideoTrack> { it.height }.thenByDescending { it.bitrate })
            ?.index
    }

    /** [preferredLanguage] blank = Auto (no preference). */
    fun chooseAudioIndex(tracks: List<AudioTrack>, preferredLanguage: String): Int? {
        val want = normalizeLanguage(preferredLanguage)
        if (want.isBlank()) return null
        tracks.firstOrNull { normalizeLanguage(it.language) == want }?.let { return it.index }
        val display = displayName(want)
        if (display.isBlank()) return null
        return tracks.firstOrNull { track ->
            val label = track.label.orEmpty().lowercase(Locale.ROOT)
            label == display || label.contains(display) ||
                label.contains("($want)") || label.contains("[$want]")
        }?.index
    }

    fun normalizeLanguage(raw: String?): String =
        raw.orEmpty().trim().lowercase(Locale.ROOT)
            .replace('_', '-')
            .substringBefore('-')

    private fun displayName(code: String): String =
        runCatching {
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
                .lowercase(Locale.ROOT)
                .takeIf { it.isNotBlank() && it != "und" }
        }.getOrNull().orEmpty()
}
