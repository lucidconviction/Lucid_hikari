package com.hikari.app.iptv

import com.hikari.app.HikariApp
import com.hikari.app.data.ProviderConfig
import com.hikari.app.data.ProviderType
import com.hikari.app.data.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * The Live TV port's "Generate Portals": the Cloud Vault Xtream database,
 * verified account by account, then saved as ordinary Hikari IPTV playlists.
 *
 * Ported from PlayTorrio's IptvScraper/IptvController. The reference keeps a
 * private verified-portal list and its own browser; Hikari already has those
 * surfaces (IPTV providers, playlist pages, Channel Wall sources), so a
 * verified panel is saved through [HikariApp.store] and behaves exactly like
 * an Xtream portal the user added by hand.
 */
object PortalGenerator {

    private const val CLOUD_PRIMARY =
        "https://pub-38f23eb5f3304328b9774fadfa233a38.r2.dev/xtreamity-plus-db.csv.gz"
    private const val CLOUD_BACKUP =
        "https://s3.us-west-004.backblazeb2.com/Xtream-STBemu/xtreamity-plus-db.csv.gz"
    private const val UA = "VLC/3.0.20 LibVLC/3.0.20"

    data class Source(val url: String, val username: String, val password: String)

    data class Generated(
        val baseUrl: String,
        val username: String,
        val password: String,
        val name: String,
        val expiry: String?,
        val maxConnections: String?,
        val m3uUrl: String,
    )

    data class Progress(
        val status: String,
        val tried: Int = 0,
        val found: Int = 0,
        val target: Int = 5,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Finds up to [target] working panels. Cloud Vault lists thousands of
     * credentials; candidates are shuffled so repeated runs do not hit the
     * same dead hosts, then checked in small parallel batches.
     */
    suspend fun generate(
        target: Int = 5,
        maxTries: Int = 400,
        onProgress: (Progress) -> Unit = {},
    ): List<Generated> = withContext(Dispatchers.IO) {
        onProgress(Progress("Downloading the Cloud Vault portal list…", target = target))
        val pool = loadCloudVault()
        if (pool.isEmpty()) {
            throw IllegalStateException("Couldn't download the Cloud Vault portal list.")
        }
        val candidates = pool.shuffled().take(maxTries)
        val found = LinkedHashMap<String, Generated>()
        var tried = 0
        var batchStart = 0
        while (batchStart < candidates.size && found.size < target) {
            val batch = candidates.subList(batchStart, minOf(batchStart + 8, candidates.size))
            batchStart += batch.size
            val results = coroutineScope {
                batch.map { source -> async { verify(source) } }.awaitAll()
            }
            tried += batch.size
            results.filterNotNull().forEach { generated ->
                val key = generated.baseUrl.lowercase() + "|" +
                    generated.username.lowercase() + "|" + generated.password
                if (found.putIfAbsent(key, generated) == null) {
                    onProgress(
                        Progress(
                            "Found ${found.size} of $target · ${generated.name}",
                            tried,
                            found.size,
                            target,
                        ),
                    )
                }
            }
            if (found.size < target) {
                onProgress(
                    Progress("Verified ${found.size} of $target · tried $tried", tried, found.size, target),
                )
            }
            if (batch.isEmpty()) break
        }
        found.values.take(target)
    }

    /**
     * Saves [portals] as IPTV providers. Deliberately does not download each
     * M3U here: the provider loads it lazily on the playlist page/wall, and a
     * generation run can add five panels without five full playlist downloads.
     */
    suspend fun save(app: HikariApp, portals: List<Generated>): Int {
        var added = 0
        for (portal in portals) {
            val m3u = portal.m3uUrl
            if (m3u.isBlank()) continue
            app.store.addProvider(
                ProviderConfig(
                    id = "iptv|" + m3u.hashCode(),
                    name = portal.name.ifBlank { "Xtream portal" },
                    type = ProviderType.IPTV,
                    url = m3u,
                ),
            )
            added++
        }
        if (added > 0) app.providers.refresh()
        return added
    }

    private fun verify(source: Source): Generated? {
        val creds = runCatching { XtreamCredentials(source.url, source.username, source.password) }
            .getOrNull() ?: return null
        val request = Request.Builder()
            .url(creds.apiInfoUrl())
            .header("User-Agent", UA)
            .header("Accept", "application/json,*/*")
            .build()
        val text = runCatching {
            client.newCall(request).execute().use { reply ->
                if (!reply.isSuccessful) return@use null
                reply.body?.string()
            }
        }.getOrNull() ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val user = root.optJSONObject("user_info") ?: root
        val auth = user.optString("auth")
        val status = user.optString("status").lowercase()
        val accepted = auth == "1" || status == "active" || root.has("user_info")
        if (!accepted) return null
        // Auth alone is not enough: some panels answer player_api.php but
        // their get.php returns an error page. Peek the playlist before it is
        // offered, so "Generate portals" never adds a tile that cannot load.
        if (!playlistLooksValid(creds)) return null
        val name = user.optString("username").ifBlank { source.username }
            .ifBlank { source.url.substringAfter("://").substringBefore('/') }
        val expirySec = user.optString("exp_date").toLongOrNull()
        val expiry = when {
            expirySec == null || expirySec <= 0L -> "Unlimited"
            else -> {
                val d = java.util.Date(expirySec * 1000L)
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(d)
            }
        }
        return Generated(
            baseUrl = source.url,
            username = source.username,
            password = source.password,
            name = "Xtream · $name",
            expiry = expiry,
            maxConnections = user.optString("max_connections").takeIf { it.isNotBlank() },
            m3uUrl = creds.m3uUrl(),
        )
    }

    /** First few KiB of the panel's M3U: a real playlist starts with #EXTM3U/#EXTINF. */
    private fun playlistLooksValid(creds: XtreamCredentials): Boolean = runCatching {
        val request = Request.Builder()
            .url(creds.m3uUrl())
            .header("User-Agent", UA)
            .header("Range", "bytes=0-4095")
            .build()
        client.newCall(request).execute().use { reply ->
            if (!reply.isSuccessful) return@use false
            val source = reply.body?.source() ?: return@use false
            val buffer = okio.Buffer()
            source.read(buffer, 4096L)
            val text = buffer.readUtf8()
            text.contains("#EXTM3U", ignoreCase = true) ||
                text.contains("#EXTINF", ignoreCase = true)
        }
    }.getOrDefault(false)

    private fun loadCloudVault(): List<Source> {
        var bytes: ByteArray? = null
        for (url in listOf(CLOUD_PRIMARY, CLOUD_BACKUP)) {
            val request = Request.Builder().url(url).header("User-Agent", UA).build()
            val body = runCatching {
                client.newCall(request).execute().use { reply ->
                    if (reply.isSuccessful) reply.body?.bytes() else null
                }
            }.getOrNull()
            if (body != null && body.isNotEmpty()) {
                bytes = body
                break
            }
        }
        val compressed = bytes ?: return emptyList()

        val raw = runCatching {
            if (compressed.size >= 2 && compressed[0] == 0x1f.toByte() && compressed[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(compressed)).readBytes()
            } else compressed
        }.getOrNull() ?: return emptyList()
        val text = runCatching { String(raw, Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Source>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size < 3) continue
            val host = parts[0].trim().trimEnd('/')
            val user = parts[1].trim()
            val pass = parts[2].trim()
            if (!host.startsWith("http://") && !host.startsWith("https://")) continue
            if (user.isBlank() || pass.isBlank()) continue
            val key = host.lowercase() + "|" + user.lowercase() + "|" + pass
            if (seen.add(key)) out += Source(host, user, pass)
        }
        return out
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    out += sb.toString().trim()
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        out += sb.toString().trim()
        return out
    }
}
