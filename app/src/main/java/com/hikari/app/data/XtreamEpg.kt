package com.hikari.app.data

import android.util.Xml
import com.hikari.app.HikariApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The XMLTV guide a saved Xtream panel serves at `xmltv.php`.
 *
 * Full XMLTV files from a large panel run to tens of megabytes, and the app
 * has to stay usable on a 512 MB phone, so this is deliberately a *now/next*
 * reader, not a guide database:
 *
 *  - the document is parsed as a stream, never held as a DOM;
 *  - only programmes that are on now or start next are kept, one per channel;
 *  - the read is capped at [MAX_BYTES] (a truncated tail still leaves the
 *    channels parsed so far usable);
 *  - results live in a short-lived in-memory cache, so moving around the IPTV
 *    tab does not re-download the guide.
 *
 * Panels that do not carry `xmltv.php`, or answer it with HTML/404, simply
 * return null — the IPTV page then keeps its normal layout.
 */
object XtreamEpg {

    /** One guide entry, reduced to what the UI shows. */
    data class Programme(
        val channelId: String,
        val channelName: String,
        val title: String,
        val description: String?,
        val startMs: Long,
        val stopMs: Long,
    ) {
        val progress: Float
            get() {
                val span = stopMs - startMs
                if (span <= 0L) return 0f
                return ((System.currentTimeMillis() - startMs).toFloat() / span).coerceIn(0f, 1f)
            }
    }

    data class Snapshot(
        val fetchedAt: Long,
        val now: Map<String, Programme>,
        val next: Map<String, Programme>,
        val names: Map<String, String>,
        private val nowByName: Map<String, Programme>,
        private val nextByName: Map<String, Programme>,
    ) {
        /** Everything on air right now, in a stable channel-name order. */
        fun onNow(): List<Programme> = now.values.sortedBy { it.channelName.lowercase() }

        /** "Now" and "next" for a playlist channel, matched by tvg-id first and
         *  then by the guide's display name. */
        fun lookup(channel: IptvChannel): Pair<Programme?, Programme?> {
            val id = channel.tvgId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            if (id != null && (now.containsKey(id) || next.containsKey(id))) {
                return now[id] to next[id]
            }
            val key = XtreamEpg.normalizeName(channel.name)
            return nowByName[key] to nextByName[key]
        }
    }

    private const val MAX_BYTES = 12L * 1024L * 1024L
    private const val TTL_MS = 15 * 60 * 1000L

    private val cache = ConcurrentHashMap<String, Snapshot>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(28, TimeUnit.SECONDS)
        .build()

    /** The credentials behind a provider id, when that provider is Xtream. */
    fun credentialsFor(providerId: String): XtreamCredentials? {
        if (providerId.isBlank()) return null
        val app = runCatching { HikariApp.instance }.getOrNull() ?: return null
        val provider = runCatching { app.providers.byId(providerId) }.getOrNull() ?: return null
        return XtreamCredentials.fromUrl(provider.config.url)
    }

    /** Now/next for one playlist channel, or null when its panel has no guide
     *  or the channel is not listed in it. */
    suspend fun lookupForChannel(channel: IptvChannel): Pair<Programme?, Programme?>? {
        val creds = credentialsFor(channel.providerId) ?: return null
        val snapshot = load(creds) ?: return null
        val hit = snapshot.lookup(channel)
        return hit.takeIf { it.first != null || it.second != null }
    }

    fun cached(creds: XtreamCredentials): Snapshot? {
        val hit = cache[key(creds)] ?: return null
        return hit.takeIf { System.currentTimeMillis() - it.fetchedAt < TTL_MS }
    }

    /** Loads (or returns the cached) guide for [creds]. Null when the panel
     *  has no usable XMLTV endpoint. */
    suspend fun load(creds: XtreamCredentials, force: Boolean = false): Snapshot? {
        if (!creds.isXtream()) return null
        val k = key(creds)
        cached(creds)?.takeIf { !force }?.let { return it }
        val lock = locks.getOrPut(k) { Mutex() }
        return lock.withLock {
            cached(creds)?.takeIf { !force }?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) { fetch(creds) }
            if (loaded != null) cache[k] = loaded
            loaded
        }
    }

    private fun key(creds: XtreamCredentials): String =
        "${creds.root}|${creds.username}".lowercase()

    private fun fetch(creds: XtreamCredentials): Snapshot? {
        val url = creds.root + "/xmltv.php?username=" + enc(creds.username) +
            "&password=" + enc(creds.password)
        val response = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute()
        }.getOrNull() ?: return null
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val raw = BufferedInputStream(body.byteStream(), 64 * 1024)
            val bounded = object : FilterInputStream(raw) {
                private var read = 0L
                override fun read(): Int {
                    if (read >= MAX_BYTES) return -1
                    val b = super.read()
                    if (b >= 0) read++
                    return b
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (read >= MAX_BYTES) return -1
                    val wanted = minOf(len.toLong(), MAX_BYTES - read).toInt()
                    val n = super.read(b, off, wanted)
                    if (n > 0) read += n
                    return n
                }
            }
            return parse(bounded)
        }
    }

    private fun parse(stream: java.io.InputStream): Snapshot? {
        val nowMs = System.currentTimeMillis()
        val names = HashMap<String, String>()
        val now = HashMap<String, Programme>()
        val next = HashMap<String, Programme>()
        var sawProgramme = false
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)
            var event = parser.eventType
            var channelId: String? = null
            var displayName: String? = null
            var title: String? = null
            var description: String? = null
            var startMs = 0L
            var stopMs = 0L
            var inProgramme = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                        "channel" -> {
                            channelId = parser.getAttributeValue(null, "id")
                            displayName = null
                        }
                        "display-name" -> {
                            val id = channelId
                            if (id != null) displayName = parser.nextText().trim()
                        }
                        "programme" -> {
                            inProgramme = true
                            channelId = parser.getAttributeValue(null, "channel")
                            startMs = parseTime(parser.getAttributeValue(null, "start"))
                            stopMs = parseTime(parser.getAttributeValue(null, "stop"))
                            title = null
                            description = null
                        }
                        "title" -> if (inProgramme) {
                            title = parser.nextText().trim()
                        }
                        "desc" -> if (inProgramme) {
                            description = parser.nextText().trim().takeIf { it.isNotBlank() }
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name.lowercase(Locale.US)) {
                        "channel" -> {
                            val id = channelId
                            val name = displayName
                            if (!id.isNullOrBlank() && !name.isNullOrBlank()) names[id] = name
                            channelId = null
                            displayName = null
                        }
                        "programme" -> {
                            inProgramme = false
                            val id = channelId
                            val t = title
                            if (!id.isNullOrBlank() && !t.isNullOrBlank() && stopMs > startMs) {
                                sawProgramme = true
                                val name = names[id] ?: id
                                val programme = Programme(
                                    channelId = id,
                                    channelName = name,
                                    title = t,
                                    description = description,
                                    startMs = startMs,
                                    stopMs = stopMs,
                                )
                                if (startMs <= nowMs && stopMs > nowMs) {
                                    val existing = now[id]
                                    // Overlapping entries are common in XMLTV;
                                    // the one that started most recently wins.
                                    if (existing == null || startMs >= existing.startMs) now[id] = programme
                                } else if (startMs > nowMs) {
                                    val existing = next[id]
                                    if (existing == null || startMs < existing.startMs) next[id] = programme
                                }
                            }
                            channelId = null
                            title = null
                            description = null
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Throwable) {
            // A truncated guide (over the byte cap, a dropped connection) has
            // still yielded whole programmes above; keep what was parsed.
            if (!sawProgramme) return null
        }
        if (!sawProgramme) return null
        val nowByName = HashMap<String, Programme>()
        val nextByName = HashMap<String, Programme>()
        for ((id, p) in now) nowByName.putIfAbsent(normalizeName(p.channelName), p)
        for ((id, p) in next) {
            val name = names[id]
            if (name != null) nextByName.putIfAbsent(normalizeName(name), p)
            else nextByName.putIfAbsent(normalizeName(p.channelName), p)
        }
        return Snapshot(
            fetchedAt = System.currentTimeMillis(),
            now = now,
            next = next,
            names = names,
            nowByName = nowByName,
            nextByName = nextByName,
        )
    }

    /** XMLTV `yyyyMMddHHmmss ±zzzz`, or a local timestamp without an offset. */
    private fun parseTime(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return 0L
        for (pattern in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmssZ", "yyyyMMddHHmmss")) {
            runCatching {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                return format.parse(value)?.time ?: 0L
            }
        }
        return 0L
    }

    /** Loose channel-name key: "Sky Sports F1 HD" and "Sky Sports F1" meet. */
    private fun normalizeName(name: String): String {
        var s = name.lowercase(Locale.US)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        s = s.replace(Regex("\\b(fhd|uhd|hd|sd|4k|hevc|h264)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return s
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
