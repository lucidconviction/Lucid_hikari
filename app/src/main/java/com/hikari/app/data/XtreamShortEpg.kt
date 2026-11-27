package com.hikari.app.data

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import android.util.Base64

/**
 * The per-channel short guide an Xtream panel serves:
 * `player_api.php?action=get_short_epg&stream_id=…&limit=…`.
 *
 * This is deliberately separate from [XtreamEpg]:
 *
 *  - XtreamEpg reads the panel's whole XMLTV file and keeps only now/next,
 *    which is right for the "On now" strip but cannot answer "what is this
 *    channel showing at kick-off tomorrow";
 *  - this asks for ONE channel's next few programmes, which is exactly the
 *    question the sports feed finder asks, and costs one small JSON request
 *    instead of a 12 MB guide download.
 *
 * Titles and descriptions arrive base64-encoded (the same as the full guide);
 * both timestamps and datetime strings are accepted. Results are cached for
 * ten minutes per panel + stream id, and failures are an empty list, never an
 * exception.
 */
object XtreamShortEpg {

    data class Programme(
        val title: String,
        val description: String,
        val startMs: Long,
        val stopMs: Long,
    )

    private data class Cached(val at: Long, val list: List<Programme>)

    private const val TTL_MS = 10 * 60 * 1000L
    private const val REQUEST_TIMEOUT_MS = 8_000L

    private val cache = ConcurrentHashMap<String, Cached>()

    private val STREAM_ID = Regex(""".*/(\d+)(?:\.[A-Za-z0-9]+)?(?:[?#].*)?$""")

    /**
     * The numeric stream id from an Xtream playback URL:
     * `…/live/user/pass/12345.ts` → `12345`. Null for anything that is not one.
     */
    fun streamIdFromUrl(url: String): String? =
        STREAM_ID.matchEntire(url.trim())?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    suspend fun fetch(
        creds: XtreamCredentials,
        streamId: String,
        limit: Int = 6,
        force: Boolean = false,
    ): List<Programme> = withContext(Dispatchers.IO) {
        if (!creds.isXtream() || streamId.isBlank()) return@withContext emptyList()
        val key = "${creds.root}|${creds.username}|$streamId".lowercase()
        val hit = cache[key]
        val now = System.currentTimeMillis()
        if (!force && hit != null && now - hit.at < TTL_MS) return@withContext hit.list

        val url = creds.root +
            "/player_api.php?username=" + enc(creds.username) +
            "&password=" + enc(creds.password) +
            "&action=get_short_epg&stream_id=" + enc(streamId) +
            "&limit=" + limit.coerceIn(2, 8)

        val body = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            Http.getStringQuiet(url, mapOf("Accept" to "application/json,*/*"))
        }.orEmpty()
        val parsed = parse(body)
        cache[key] = Cached(now, parsed)
        parsed
    }

    private fun parse(body: String): List<Programme> {
        if (body.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val array = root.optJSONArray("epg_listings") ?: return@runCatching emptyList()
            val out = ArrayList<Programme>(array.length())
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val start = timeOf(entry, "start_timestamp", "start") ?: continue
                val stop = timeOf(entry, "stop_timestamp", "end") ?: continue
                if (stop <= start) continue
                out += Programme(
                    title = decode(entry.optString("title")),
                    description = decode(entry.optString("description")),
                    startMs = start,
                    stopMs = stop,
                )
            }
            out.sortedBy { it.startMs }
        }.getOrElse { emptyList() }
    }

    private fun timeOf(entry: JSONObject, vararg keys: String): Long? {
        for (key in keys) {
            val raw = entry.opt(key) ?: continue
            val millis = toMillis(raw)
            if (millis != null) return millis
        }
        return null
    }

    private fun toMillis(value: Any): Long? {
        if (value is Number) {
            val n = value.toLong()
            return if (n > 10_000_000_000L) n else n * 1000L
        }
        val text = value.toString().trim().trim('"')
        val n = text.toLongOrNull()
        if (n != null) return if (n > 10_000_000_000L) n else n * 1000L
        for (pattern in PATTERNS) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                    isLenient = true
                }.parse(text)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private val PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm",
        "dd/MM/yyyy HH:mm:ss",
    )

    /**
     * Panels base64-encode the title/description. A panel that sends plain text
     * instead is kept as-is: "News" is itself valid base64, and decoding it
     * would produce binary noise. The check is on the RESULT — anything with
     * control characters or replacement marks is treated as a failed decode.
     */
    private fun decode(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val decoded = runCatching {
            String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8).trim()
        }.getOrNull()
        if (decoded.isNullOrBlank()) return text
        val bad = decoded.count {
            it == '\uFFFD' || (it.code < 32 && it != '\n' && it != '\r' && it != '\t')
        }
        return if (bad > 0) text else decoded
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
