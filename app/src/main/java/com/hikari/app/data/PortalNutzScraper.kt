@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.hikari.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class PortalEntry(
    val url: String,
    val username: String,
    val password: String,
    val name: String,
    val domain: String = "",
    val expDate: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    val status: String? = null,
    val channelCount: Int = 0,
    val isTrial: Boolean? = null,
    val verifiedAt: Long = System.currentTimeMillis(),
)

/**
 * IPTV Portal Scraper — Port of /Users/robbdeeze/Documents/projects/INTEL/iptv-scraper
 *
 * Scans Reddit subreddits, extracts portal credentials, verifies them against
 * the Xtream Codes API, and fetches channel/VOD/series counts.
 */
object PortalNutzScraper {

    private const val FETCH_TIMEOUT_MS = 8_000L
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    private const val MAX_PARALLEL = 6

    private val REDDIT_OAUTH_CLIENT_IDS = listOf(
        "ohXpoqrZYub1kg" to "Nuvio IPTV",
        "NOe2iKrPPzwscA" to "Hikari IPTV",
        "JrPdG8Z6dkWNxA" to "Default IPTV",
    )
    private val REDDIT_OAUTH_UA = "Hikari/1.0"
    private val UA = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36"

    private val CATALOG_SUBS = listOf("IPTV_ZONENEW", "FreeIPTV", "iptvguru", "IPTVpremium")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // In-memory cache
    private val cache = mutableMapOf<String, PortalEntry>()
    private val seenPastes = mutableSetOf<String>()
    private val processedPosts = mutableSetOf<String>()

    /** Discover verified IPTV portals from Reddit catalogs. */
    suspend fun discoverPortals(): List<PortalEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Return fresh cached portals if we have enough
        val fresh = cache.values.filter { now - it.verifiedAt < CACHE_TTL_MS }
            .distinctBy { "${it.url}|${it.username}" }
        if (fresh.size >= 3) return@withContext fresh.sortedByDescending { p ->
            if (p.expDate != null && p.expDate > now) p.expDate else 0L
        }

        val allPortals = verifyCachedPortals().toMutableList()
        if (allPortals.isNotEmpty()) return@withContext allPortals

        // Fetch from Reddit
        val redditPortals = fetchFromReddit()
        allPortals.addAll(redditPortals)

        // Verify all found
        val verified = verifyPortals(allPortals.map { 
            PortalParsed(url = it.url, domain = it.domain, username = it.username, password = it.password) 
        })
        cache.putAll(verified.associateBy { "${it.url}|${it.username}" })
        verified.sortedByDescending { p ->
            if (p.expDate != null && p.expDate > now) p.expDate else 0L
        }
    }

    /** Verify cached portals that need refresh. */
    private suspend fun verifyCachedPortals(): List<PortalEntry> = coroutineScope {
        val now = System.currentTimeMillis()
        val toVerify = cache.entries
            .filter { (_, entry) -> now - entry.verifiedAt > 10 * 60 * 1000L }
            .map { (key, _) -> key.split("|") }
            .filter { it.size >= 2 }
            .map { (url, user) -> PortalParsed(url.trim(), "", user.trim(), "") }

        if (toVerify.isEmpty()) return@coroutineScope emptyList()

        toVerify.map { portal ->
            async {
                try {
                    val parsed = parseXtreamUrl(portal.url) ?: return@async null
                    verifySinglePortal(
                        entry = PortalEntry(url = portal.url, username = portal.username, password = "", name = "", domain = parsed.domain),
                        username = parsed.username,
                        password = parsed.password,
                    )
                } catch (_: Exception) { null }
            }
        }.awaitAll().filterNotNull()
    }

    /** Fetch from Reddit subreddits. */
    private suspend fun fetchFromReddit(): List<PortalEntry> = coroutineScope {
        val portals = mutableListOf<PortalParsed>()
        CATALOG_SUBS.map { sub ->
            async { fetchFromRedditSubreddit(sub) }
        }.awaitAll().flatten().forEach { parsed ->
            val key = "${parsed.url}|${parsed.username}"
            if (!seenPastes.contains(key) && parsed.username.isNotBlank() && parsed.password.isNotBlank()) {
                portals.add(parsed)
                seenPastes.add(key)
            }
        }
        // Verify fetched portals
        verifyPortals(portals)
    }

    /** Fetch portals from a single Reddit subreddit. */
    private suspend fun fetchFromRedditSubreddit(sub: String): List<PortalParsed> = withContext(Dispatchers.IO) {
        val portals = mutableListOf<PortalParsed>()
        val oauthUrl = "https://www.reddit.com/r/$sub/new.json?limit=100&sort=new"

        // Try OAuth2 first
        val oauthToken = getRedditOAuthToken()
        if (oauthToken != null) {
            try {
                val req = Request.Builder()
                    .url(oauthUrl)
                    .header("User-Agent", REDDIT_OAUTH_UA)
                    .header("Authorization", "Bearer $oauthToken")
                    .build()
                val resp = withTimeout(FETCH_TIMEOUT_MS * 3) { client.newCall(req).execute() }
                if (resp.code == 200) {
                    portals.addAll(parseRedditJson(resp.body?.string()))
                }
            } catch (_: Exception) {}
        }

        if (portals.isNotEmpty()) return@withContext portals

        // Fallback: no-auth
        try {
            val req = Request.Builder()
                .url("$oauthUrl&t=week")
                .header("User-Agent", UA)
                .build()
            val resp = withTimeout(FETCH_TIMEOUT_MS * 3) { client.newCall(req).execute() }
            if (resp.code == 200) {
                portals.addAll(parseRedditJson(resp.body?.string()))
            }
        } catch (_: Exception) {}

        portals
    }

    /** Parse Reddit JSON and extract portal credentials. */
    private fun parseRedditJson(json: String?): List<PortalParsed> {
        val portals = mutableListOf<PortalParsed>()
        if (json.isNullOrBlank()) return portals

        try {
            val doc = org.json.JSONObject(json)
            val children = doc.optJSONObject("data")?.optJSONArray("children") ?: return portals

            for (i in 0 until children.length()) {
                val post = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                val id = post.optString("id", "")
                if (processedPosts.contains(id)) continue
                processedPosts.add(id)

                val text = "${post.optString("title", "")} ${post.optString("selftext", "")}"
                portals.addAll(extractPortals(text))
            }
        } catch (_: Exception) {}

        return portals.distinctBy { "${it.url}|${it.username}" }
    }

    /** Extract portal credentials from text. */
    private fun extractPortals(text: String): List<PortalParsed> {
        val results = mutableListOf<PortalParsed>()

        // Pattern 1: http://server:port/playlist?user=xxx&pass=xxx
        val p1 = Regex("""(https?://[^\s&"]+:\d+/playlist\?user=[^&]+&pass=[^&]+)""")
        p1.findAll(text).forEach {
            val url = it.value
            val creds = parseXtreamUrl(url)
            if (creds != null) results.add(PortalParsed(creds.url, creds.domain, creds.username, creds.password))
        }

        // Pattern 2: Portal URL with separate User/Pass lines
        val p2 = Regex(
            """(?:Portal|Host|Panel|URL)\W*?(https?://[^<\s'"<>]+)[\s\S]{1,200}?(?:Username|User)\W*?([^\s|<"'\n]+)[\s\S]{1,50}?(?:Password|Pass)\W*?([^\s|<"'\n]+)""",
            RegexOption.IGNORE_CASE
        )
        p2.findAll(text).forEach {
            val url = cleanUrl(it.groupValues[1])
            val user = it.groupValues[2].trim()
            val pass = it.groupValues[3].trim()
            if (url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                results.add(PortalParsed(url, "", user, pass))
            }
        }

        // Pattern 3: Base64-encoded HTTP links
        val b64 = Regex("""aHR0c[A-Za-z0-9+/=]{10,}""")
        b64.findAll(text).forEach {
            try {
                val decoded = Base64.Default.decode(it.value).decodeToString()
                if (decoded.contains("http://") || decoded.contains("https://")) {
                    val url = if (decoded.contains("/playlist?")) decoded.split("?")[0].trim() else decoded
                    val creds = parseXtreamUrl(url)
                    if (creds != null) results.add(PortalParsed(creds.url, creds.domain, creds.username, creds.password))
                }
            } catch (_: Exception) {}
        }

        return results
    }

    /** Clean URL from junk. */
    private fun cleanUrl(raw: String): String {
        var c = raw.replace("&amp;", "&").replace("&quot;", "\"")
        val q = c.indexOf("?")
        if (q >= 0) c = c.substring(0, q)
        
        val junkPatterns = listOf("/get.php", "/get_m3u.php", "/index.php", "/live.php", 
            "/c.php", "/portal.php", "/player_api.php", "/portal/api.php")
        for (pattern in junkPatterns) {
            if (c.endsWith(pattern, ignoreCase = true)) c = c.removeSuffix(pattern)
        }
        
        c = c.trim('/')
        if (!c.lowercase().startsWith("http://") && !c.lowercase().startsWith("https://")) c = "http://$c"
        
        // Strip port
        c = Regex("""\:\d{2,5}""").replace(c, "")
        return c
    }

    /** Parse URL into base URL + credentials. */
    private fun parseXtreamUrl(url: String): PortalParsed? {
        return try {
            val pattern = Regex("""https?://([^/:]+):(\d+)/playlist\?user=([^&]+)&pass=([^&]+)""")
            val match = pattern.find(url) ?: return null
            PortalParsed(
                url = "http://${match.groupValues[1]}",
                domain = match.groupValues[1],
                username = match.groupValues[3],
                password = match.groupValues[4],
            )
        } catch (_: Exception) { null }
    }

    /** Verify a single portal. */
    private suspend fun verifySinglePortal(
        entry: PortalEntry,
        username: String,
        password: String,
    ): PortalEntry? = withContext(Dispatchers.IO) {
        try {
            val apiBase = "${entry.url}/player_api.php"
            val verifyUrl = "$apiBase?username=$username&password=$password"
            
            val req = Request.Builder()
                .url(verifyUrl)
                .header("User-Agent", UA)
                .build()
            
            val resp = withTimeout(FETCH_TIMEOUT_MS) { client.newCall(req).execute() }
            if (resp.code != 200) return@withContext null
            
            val body = resp.body?.string() ?: return@withContext null
            val doc = org.json.JSONObject(body)
            val userInfo = doc.optJSONObject("user_info") ?: doc
            
            val expTime = userInfo.optLong("exp_date", 0)
            val maxConns = userInfo.optInt("max_connections", 0)
            val isActive = userInfo.optInt("is_active", 0) == 1
            val status = when {
                isActive -> "Active"
                expTime > System.currentTimeMillis() -> "Expired"
                else -> "Unknown"
            }
            
            // Fetch channel count
            var channelCount = 0
            try {
                val chReq = Request.Builder()
                    .url("$apiBase?username=$username&password=$password&action=get_live_streams")
                    .header("User-Agent", UA)
                    .build()
                val chResp = withTimeout(FETCH_TIMEOUT_MS) { client.newCall(chReq).execute() }
                if (chResp.code == 200) {
                    val chDoc = org.json.JSONObject(chResp.body?.string() ?: "[]")
                    channelCount = if (chDoc.has("data")) chDoc.optJSONArray("data")?.length() ?: 0 else 0
                }
            } catch (_: Exception) {}
            
            return@withContext PortalEntry(
                url = entry.url,
                username = username,
                password = password,
                name = entry.name,
                domain = entry.domain,
                expDate = if (expTime > 0) expTime * 1000L else null,
                maxConnections = maxConns.takeIf { it > 0 },
                activeConnections = if (isActive) 1 else 0,
                status = status,
                channelCount = channelCount,
                isTrial = maxConns <= 1,
                verifiedAt = System.currentTimeMillis(),
            )
        } catch (_: Exception) { null }
    }

    /** Verify a list of portals. */
    private suspend fun verifyPortals(urls: List<PortalParsed>): List<PortalEntry> = coroutineScope {
        urls.distinctBy { "${it.url}|${it.username}" }
            .map { portal ->
                async {
                    try {
                        val parsed = if (portal.password.isEmpty()) parseXtreamUrl(portal.url) else null
                        val username = parsed?.username ?: portal.username
                        val password = parsed?.password ?: portal.password
                        if (username.isBlank() || password.isBlank()) return@async null
                        
                        val entry = PortalEntry(
                            url = portal.url,
                            username = "",
                            password = "",
                            name = "",
                            domain = parsed?.domain ?: portal.domain,
                        )
                        verifySinglePortal(entry, username, password)
                    } catch (_: Exception) { null }
                }
            }.awaitAll().filterNotNull()
    }

    /** Get Reddit OAuth token (rotates through client IDs). */
    private suspend fun getRedditOAuthToken(): String? = withContext(Dispatchers.IO) {
        for ((clientId, _) in REDDIT_OAUTH_CLIENT_IDS) {
            try {
                val auth = Base64.Default.encode("${clientId}:".toByteArray(StandardCharsets.UTF_8))
                val body = "grant_type=client_credentials".toRequestBody("application/x-www-form-urlencoded".toMediaType())
                
                val req = Request.Builder()
                    .url("https://www.reddit.com/api/v1/access_token")
                    .header("User-Agent", REDDIT_OAUTH_UA)
                    .header("Authorization", "Basic $auth")
                    .post(body)
                    .build()
                
                val resp = withTimeout(5_000L) { client.newCall(req).execute() }
                if (resp.code == 200) {
                    val json = resp.body?.string() ?: continue
                    val doc = org.json.JSONObject(json)
                    return@withContext doc.optString("access_token", null)
                }
            } catch (_: Exception) {}
        }
        null
    }

    /** Load cached portals. */
    fun loadCached(): List<PortalEntry> {
        val now = System.currentTimeMillis()
        return cache.values
            .filter { now - it.verifiedAt < CACHE_TTL_MS }
            .sortedByDescending { p ->
                if (p.expDate != null && p.expDate > now) p.expDate else 0L
            }
    }

    /** Clear cache. */
    fun clearCache() {
        cache.clear()
        seenPastes.clear()
        processedPosts.clear()
    }

    private data class PortalParsed(
        val url: String,
        val domain: String = "",
        val username: String,
        val password: String,
    )
}
