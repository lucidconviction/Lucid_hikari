package com.hikari.app.sports

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SportsPlatform provides the platform capabilities the Sports hub needs:
 * - HTTP GET for JSON feeds
 * - Device locale (country, language)
 * - Background thread sleep
 * - Accent folding for platforms without java.text.Normalizer
 */
internal object SportsPlatform {

    /** GET [url] as text (JSON). Throws with a plain message on failure ("HTTP 404", "timed out"). */
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        Http.getStringQuiet(url) ?: throw Exception("HTTP ${Http.getStringStrict(url).exceptionOrNull()?.message ?: "failed"}")
    }

    /** The device's country ("US", "GB"), upper case; "" when unknown. */
    fun countryCode(): String {
        try {
            val locale = java.util.Locale.getDefault()
            val country = locale.country.uppercase()
            return if (country.isNotEmpty()) country else ""
        } catch (e: Exception) {
            return ""
        }
    }

    /** The device's language ("en", "es"), lower case. */
    fun languageCode(): String {
        try {
            val locale = java.util.Locale.getDefault()
            val lang = locale.language.lowercase()
            return if (lang.isNotEmpty()) lang else "en"
        } catch (e: Exception) {
            return "en"
        }
    }

    /** Sleeps the calling (background) thread for [ms]. */
    fun pause(ms: Long) {
        Thread.sleep(ms)
    }

    /** Strips accents the way java.text.Normalizer NFD + mark removal does. */
    fun foldAccents(text: String): String {
        if (text.all { it.code < 128 }) return text
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder()
        val nonSpacingMark = java.lang.Character.NON_SPACING_MARK.toLong()
        for (ch in normalized) {
            if (java.lang.Character.getType(ch).toLong() != nonSpacingMark) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}