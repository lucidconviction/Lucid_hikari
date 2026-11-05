package com.hikari.app.youtube

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Simple 4-tuple for destructuring multiple values. */
data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** YouTube feed tab identifiers. */
enum class YtFeedTab(
    val title: String,
    val needsSignIn: Boolean = false,
    val isGrid: Boolean = false,
) {
    HOME("Home"),
    SUBSCRIPTIONS("Subscriptions", needsSignIn = true, isGrid = true),
    HISTORY("History", needsSignIn = true, isGrid = true),
    PLAYLISTS("Playlists", needsSignIn = true, isGrid = true),
    LIVE("Live"),
    MUSIC("Music"),
    GAMING("Gaming"),
    NEWS("News"),
    SPORTS("Sports"),
    SHORTS("Shorts", isGrid = true),
    CHANNELS("Channels", needsSignIn = true, isGrid = true),
}

/** A single item in a YouTube feed (video, channel, or playlist). */
data class YtFeedItem(
    val kind: YtItemKind,
    val videoId: String?,
    val title: String,
    val author: String?,
    val thumbnail: String?,
    val badge: String?,
    val isLive: Boolean,
    val isShorts: Boolean,
    val channelId: String?,
    val percentWatched: Int,
    val continuationKey: String?,
)

enum class YtItemKind { VIDEO, CHANNEL, PLAYLIST }

/** A row of items (for row-based feeds like Home). */
data class YtFeedRow(
    val title: String,
    val items: List<YtFeedItem>,
    val continuationKey: String?,
)

/** A grid feed (Subscriptions, History, etc.) with a single continuation. */
data class YtFeedGrid(
    val items: List<YtFeedItem>,
    val continuationKey: String?,
)

/** Search suggestion. */
data class YtSearchSuggestion(
    val text: String,
    val isRecent: Boolean = false,
)

/** Resolved stream info for playback. */
data class YtStream(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val videoId: String,
    val headers: Map<String, String>,
    val startPositionMs: Long = 0L,
    val lookedUpAt: Long = System.currentTimeMillis(),
) {
    val isFresh: Boolean get() = System.currentTimeMillis() - lookedUpAt < 20 * 60 * 1000L
}

/** Repository for YouTube feed data via innerTube API. */
class YouTubeFeedRepository(
    private val context: Context,
    private val authRepository: YouTubeAuthRepository,
) {

    private companion object {
        const val INNER_TUBE_API_URL = "https://www.youtube.com/youtubei/v1"
        const val BROWSE_URL = "$INNER_TUBE_API_URL/browse"
        const val SEARCH_URL = "$INNER_TUBE_API_URL/search"
        const val NEXT_URL = "$INNER_TUBE_API_URL/next"
        const val PLAYER_URL = "$INNER_TUBE_API_URL/player"
        const val MAX_CACHED_STREAMS = 24
        const val MAX_CLIENT_SWITCHES = 8
        
        // Android client
        const val ANDROID_CLIENT_VERSION = "21.26.364"
        const val ANDROID_USER_AGENT = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
        const val ANDROID_CLIENT_NAME = "1"
        const val ANDROID_INNER_TUBE_NAME = "ANDROID"
        
        // TV client (better for streams, supports auth)
        const val TV_CLIENT_VERSION = "7.20231204.0.0"
        const val TV_USER_AGENT = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
        const val TV_CLIENT_NAME = "85"
        const val TV_INNER_TUBE_NAME = "TVHTML5"
        
        // iOS client (no signature deciphering needed)
        const val IOS_CLIENT_VERSION = "19.10.3"
        const val IOS_USER_AGENT = "com.google.ios.youtube/19.10.3 (iPhone14,3; U; CPU iOS 17_0 like Mac OS X)"
        const val IOS_CLIENT_NAME = "5"
        const val IOS_INNER_TUBE_NAME = "IOS"
        
        // Web embed client (good for restricted content)
        const val WEB_EMBED_CLIENT_VERSION = "2.20240429.00.00"
        const val WEB_EMBED_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        const val WEB_EMBED_CLIENT_NAME = "56"
        const val WEB_EMBED_INNER_TUBE_NAME = "WEB_EMBEDDED_PLAYER"
        
        // Client types for switching
        enum class ClientType {
            ANDROID,
            TV,
            IOS,
            WEB_EMBED
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var apiKeyCache: String? = null
    private var visitorDataCache: String? = null
    private var lastApiKeyFetch = 0L
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, YtStream>()

    /** Fetches the initial feed for a tab. */
    suspend fun fetchFeed(tab: YtFeedTab, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val client = when (tab) {
                YtFeedTab.MUSIC, YtFeedTab.GAMING, YtFeedTab.NEWS, YtFeedTab.SPORTS -> ClientType.TV
                else -> ClientType.ANDROID
            }
            val (payload, endpoint) = when (tab) {
                YtFeedTab.HOME -> buildBrowsePayload("FEwhat_to_watch", accessToken, client)
                YtFeedTab.SUBSCRIPTIONS -> buildBrowsePayload("FEsubscriptions", accessToken, client)
                YtFeedTab.HISTORY -> buildBrowsePayload("FEhistory", accessToken, client)
                YtFeedTab.PLAYLISTS -> buildBrowsePayload("FEplaylist_aggregation", accessToken, client)
                YtFeedTab.LIVE -> buildReelPayload(accessToken, client)
                YtFeedTab.MUSIC -> buildBrowsePayloadTV("FEtopics_music", "6gQJRkVleHBsb3Jl", accessToken, client)
                YtFeedTab.GAMING -> buildBrowsePayloadTV("FEtopics_gaming", "6gQJRkVleHBsb3Jl", accessToken, client)
                YtFeedTab.NEWS -> buildBrowsePayloadTV("FEtopics_news", "6gQJRkVleHBsb3Jl", accessToken, client)
                YtFeedTab.SPORTS -> buildBrowsePayloadTV("FEtopics_sports", "6gQJRkVleHBsb3Jl", accessToken, client)
                YtFeedTab.SHORTS -> buildShortsPayload(accessToken, client)
                YtFeedTab.CHANNELS -> buildBrowsePayload("FEmy_youtube", accessToken, client)
            }
            val response = postJson(endpoint, payload, accessToken, client)
            parseFeedResponse(response, tab)
        }
    }

    /** Loads the next page of a feed using a continuation key. */
    suspend fun fetchContinuation(continuationKey: String, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val payload = buildContinuationPayload(continuationKey, accessToken)
            val response = postJson(NEXT_URL, payload, accessToken, ClientType.ANDROID)
            parseContinuationResponse(response)
        }
    }

    /** Searches YouTube. */
    suspend fun search(query: String, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val payload = buildSearchPayload(query, accessToken)
            val response = postJson(SEARCH_URL, payload, accessToken, ClientType.ANDROID)
            parseSearchResponse(response)
        }
    }

    /** Gets search suggestions. */
    suspend fun getSearchSuggestions(query: String): List<YtSearchSuggestion> {
        return withContext(Dispatchers.IO) {
            // Using YouTube's completion API
            val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=${query.urlEncode()}"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", Http.UA)
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    // Response is like: ["query", [["suggestion1"], ["suggestion2"]]]
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() < 2) return@withContext emptyList()
                    val suggestionsArray = jsonArray.getJSONArray(1)
                    val suggestions = mutableListOf<YtSearchSuggestion>()
                    for (i in 0 until suggestionsArray.length()) {
                        val arr = suggestionsArray.optJSONArray(i)
                        arr?.let { suggestions.add(YtSearchSuggestion(it.getString(0))) }
                    }
                    suggestions
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** Opens a channel page (uploads, playlists, etc.). */
    suspend fun fetchChannel(channelId: String, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val (payload, url) = buildBrowsePayload("channel/$channelId", accessToken, ClientType.ANDROID)
            val response = postJson(url, payload, accessToken, ClientType.ANDROID)
            parseFeedResponse(response, YtFeedTab.HOME)
        }
    }

    /** Opens a playlist. */
    suspend fun fetchPlaylist(playlistId: String, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val (payload, url) = buildBrowsePayload("playlist/$playlistId", accessToken, ClientType.ANDROID)
            val response = postJson(url, payload, accessToken, ClientType.ANDROID)
            parseFeedResponse(response, YtFeedTab.HOME)
        }
    }

    /** Resolves a video to a playable stream. */
    suspend fun resolveStream(videoId: String, accessToken: String?): Result<YtStream> {
        return withContext(Dispatchers.IO) {
            // Check cache first
            streamCache[videoId]?.takeIf { it.isFresh }?.let { 
                Log.d("YouTubeFeedRepo", "Cache hit for $videoId: ${it.url}")
                return@withContext Result.success(it) 
            }

            ensureApiKey()
            
            // Try multiple clients like the reference project does
            val clients = listOf(
                ClientType.ANDROID,
                ClientType.TV,
                ClientType.IOS,
                ClientType.WEB_EMBED
            )
            
            var lastError: String? = null
            
            for (client in clients) {
                val payload = buildPlayerPayload(videoId, accessToken, client)
                val response = postJson(PLAYER_URL, payload, accessToken, client)
                val stream = extractStreamUrl(response, videoId)
                
                if (stream != null) {
                    streamCache[videoId] = stream
                    if (streamCache.size >= MAX_CACHED_STREAMS) streamCache.clear()
                    Log.d("YouTubeFeedRepo", "Resolved stream for $videoId using $client: ${stream.url}")
                    return@withContext Result.success(stream)
                }
                
                lastError = "No playable stream with $client client"
                Log.w("YouTubeFeedRepo", "$lastError for $videoId")
            }
            
            Log.e("YouTubeFeedRepo", "Failed to resolve stream for $videoId after trying all clients: $lastError")
            Result.failure(IllegalStateException(lastError ?: "No playable stream found"))
        }
    }

    /** Prefetches a stream in the background. */
    suspend fun prefetchStream(videoId: String, accessToken: String?) {
        if (streamCache[videoId]?.isFresh == true) return
        resolveStream(videoId, accessToken).getOrNull()
    }

    // ── Payload builders ──

    private fun buildBrowsePayload(browseId: String, accessToken: String?, client: ClientType = ClientType.ANDROID): Pair<String, String> {
        val payload = JSONObject().apply {
            put("browseId", browseId)
            put("context", buildClientContext(accessToken, client))
        }
        return payload.toString() to BROWSE_URL
    }

    /** Builds browse payload for TV client (used for Music, Gaming, News, Sports topics). */
    private fun buildBrowsePayloadTV(browseId: String, params: String, accessToken: String?, client: ClientType = ClientType.TV): Pair<String, String> {
        val payload = JSONObject().apply {
            put("browseId", browseId)
            put("params", params)
            put("context", buildClientContext(accessToken, client))
        }
        return payload.toString() to BROWSE_URL
    }

    private fun buildSearchPayload(query: String, accessToken: String?): String {
        return JSONObject().apply {
            put("query", query)
            put("context", buildClientContext(accessToken))
            put("params", JSONObject().put("type", "video"))
        }.toString()
    }

    private fun buildContinuationPayload(continuation: String, accessToken: String?): String {
        return JSONObject().apply {
            put("continuation", continuation)
            put("context", buildClientContext(accessToken))
        }.toString()
    }

    private fun buildPlayerPayload(videoId: String, accessToken: String?, client: ClientType = ClientType.ANDROID): String {
        return JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", buildClientContext(accessToken, client))
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("html5Preference", "HTML5_PREF_WANTS")
                })
            })
        }.toString()
    }

    /** Builds payload for Shorts feed using REEL_WATCH_INPUT_TYPE_SEEDLESS. */
    private fun buildShortsPayload(accessToken: String?, client: ClientType = ClientType.ANDROID): Pair<String, String> {
        val payload = JSONObject().apply {
            put("disablePlayerResponse", true)
            put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS")
            put("params", "CA8%3D")
            put("context", buildClientContext(accessToken, client))
        }
        return payload.toString() to "$INNER_TUBE_API_URL/browse"
    }

    /** Builds payload for Live feed using reel endpoint. */
    private fun buildReelPayload(accessToken: String?, client: ClientType = ClientType.ANDROID): Pair<String, String> {
        val payload = JSONObject().apply {
            put("disablePlayerResponse", true)
            put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS")
            put("params", "CA8%3D")
            put("context", buildClientContext(accessToken, client))
        }
        return payload.toString() to "$INNER_TUBE_API_URL/reel/reel_watch_sequence"
    }

    private fun buildClientContext(accessToken: String?, client: ClientType = ClientType.ANDROID): JSONObject {
        return JSONObject().apply {
            when (client) {
                ClientType.ANDROID -> {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", ANDROID_CLIENT_VERSION)
                        put("osName", "Android")
                        put("osVersion", "11")
                        put("platform", "MOBILE")
                        put("androidSdkVersion", 30)
                        put("hl", "en")
                        put("gl", "US")
                        put("userInterfaceTheme", "USER_INTERFACE_THEME_DARK")
                    })
                }
                ClientType.TV -> {
                    put("client", JSONObject().apply {
                        put("clientName", "TVHTML5")
                        put("clientVersion", TV_CLIENT_VERSION)
                        put("osName", "Android")
                        put("osVersion", "13")
                        put("platform", "TV")
                        put("hl", "en")
                        put("gl", "US")
                        put("userInterfaceTheme", "USER_INTERFACE_THEME_DARK")
                    })
                }
                ClientType.IOS -> {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", IOS_CLIENT_VERSION)
                        put("osName", "iOS")
                        put("osVersion", "17.0")
                        put("platform", "MOBILE")
                        put("hl", "en")
                        put("gl", "US")
                        put("userInterfaceTheme", "USER_INTERFACE_THEME_DARK")
                    })
                }
                ClientType.WEB_EMBED -> {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_EMBEDDED_PLAYER")
                        put("clientVersion", WEB_EMBED_CLIENT_VERSION)
                        put("osName", "Linux")
                        put("osVersion", "10")
                        put("platform", "DESKTOP")
                        put("hl", "en")
                        put("gl", "US")
                        put("userInterfaceTheme", "USER_INTERFACE_THEME_DARK")
                    })
                }
            }
            accessToken?.let { put("auth", it) }
            visitorDataCache?.let { put("visitorData", it) }
        }
    }

    // ── HTTP ──

    private fun postJson(url: String, payload: String, accessToken: String?, client: ClientType = ClientType.ANDROID): String {
        val clientInfo: Tuple4<String, String, String, String> = when (client) {
            ClientType.ANDROID -> Tuple4(ANDROID_USER_AGENT, ANDROID_CLIENT_NAME, ANDROID_CLIENT_VERSION, ANDROID_INNER_TUBE_NAME)
            ClientType.TV -> Tuple4(TV_USER_AGENT, TV_CLIENT_NAME, TV_CLIENT_VERSION, TV_INNER_TUBE_NAME)
            ClientType.IOS -> Tuple4(IOS_USER_AGENT, IOS_CLIENT_NAME, IOS_CLIENT_VERSION, IOS_INNER_TUBE_NAME)
            ClientType.WEB_EMBED -> Tuple4(WEB_EMBED_USER_AGENT, WEB_EMBED_CLIENT_NAME, WEB_EMBED_CLIENT_VERSION, WEB_EMBED_INNER_TUBE_NAME)
        }

        val userAgent = clientInfo.first
        val clientName = clientInfo.second
        val clientVersion = clientInfo.third
        val innerTubeName = clientInfo.fourth

        val requestBuilder = Request.Builder()
            .url("$url?key=${apiKeyCache ?: ""}")
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/json")
            .header("X-Youtube-Client-Name", clientName)
            .header("X-Youtube-Client-Version", clientVersion)
            .header("X-GOOG-API-FORMAT-VERSION", "2")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

        accessToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        visitorDataCache?.let { requestBuilder.header("X-Goog-Visitor-Id", it) }

        val request = requestBuilder.build()
        return httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IllegalStateException("Empty response")
        }
    }

    private fun ensureApiKey() {
        if (apiKeyCache != null && System.currentTimeMillis() - lastApiKeyFetch < 30 * 60 * 1000) return
        // Fetch watch page to extract API key and visitor data
        val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en"
        val request = Request.Builder()
            .url(watchUrl)
            .header("User-Agent", Http.UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        
        val html = httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use ""
            resp.body?.string() ?: ""
        }

        val apiKeyRegex = "\"INNERTUBE_API_KEY\":\"([^\"]+)\"".toRegex()
        val visitorRegex = "\"VISITOR_DATA\":\"([^\"]+)\"".toRegex()
        
        apiKeyRegex.find(html)?.groupValues?.getOrNull(1)?.let { 
            apiKeyCache = it
            lastApiKeyFetch = System.currentTimeMillis()
        }
        visitorRegex.find(html)?.groupValues?.getOrNull(1)?.let { 
            visitorDataCache = it
        }
    }

    // ── Response parsing ──

    private fun parseFeedResponse(json: String, tab: YtFeedTab): Result<Any> {
        return try {
            val root = JSONObject(json)
            val contents = root.optJSONObject("contents")
            val tabs = contents?.optJSONArray("tabs")
            val primaryTab = tabs?.let { arr ->
                for (i in 0 until arr.length()) {
                    val tabObj = arr.optJSONObject(i)
                    val tabRenderer = tabObj?.optJSONObject("tabRenderer")
                    if (tabRenderer != null) return@let tabObj
                }
                null
            }
            val tabRenderer = primaryTab?.optJSONObject("tabRenderer")
            val tabContent = tabRenderer?.optJSONObject("content")
            val richGrid = tabContent?.optJSONObject("richGridRenderer")
            val sectionList = tabContent?.optJSONObject("sectionListRenderer")

            if (tab.isGrid) {
                // Grid feeds: Subscriptions, History, Playlists, Shorts, Channels
                val grid = richGrid ?: return Result.failure(IllegalStateException("No richGridRenderer"))
                Result.success(parseGrid(grid))
            } else {
                // Row feeds: Home, Live, Music, Gaming, News, Sports
                val sections = sectionList ?: return Result.failure(IllegalStateException("No sectionListRenderer"))
                Result.success(parseRows(sections))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseContinuationResponse(json: String): Result<Any> {
        return try {
            val root = JSONObject(json)
            val response = root.optJSONArray("onResponseReceivedActions")?.let { arr ->
                if (arr.length() > 0) arr.optJSONObject(0) else null
            }
            val appendAction = response?.optJSONObject("appendContinuationItemsAction")
            val items = appendAction?.optJSONArray("continuationItems")
            
            if (items == null || items.length() == 0) {
                return Result.success(YtFeedGrid(emptyList(), null))
            }

            // Check if it's a grid or rows continuation
            val firstItem = items.optJSONObject(0)
            if (firstItem?.optJSONObject("richItemRenderer") != null) {
                // Grid continuation
                val parsedItems = mutableListOf<YtFeedItem>()
                for (i in 0 until items.length()) {
                    parseRichItem(items.optJSONObject(i))?.let { parsedItems.add(it) }
                }
                Result.success(YtFeedGrid(parsedItems, extractContinuation(items)))
            } else {
                // Row continuation - simplified for now
                val parsedItems = mutableListOf<YtFeedItem>()
                for (i in 0 until items.length()) {
                    parseRichItem(items.optJSONObject(i))?.let { parsedItems.add(it) }
                }
                Result.success(YtFeedGrid(parsedItems, extractContinuation(items)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSearchResponse(json: String): Result<Any> {
        return try {
            val root = JSONObject(json)
            val contents = root.optJSONObject("contents")
            val tabs = contents?.optJSONArray("tabs")
            val tabRenderer = tabs?.let { arr ->
                for (i in 0 until arr.length()) {
                    val tabObj = arr.optJSONObject(i)
                    val tr = tabObj?.optJSONObject("tabRenderer")
                    if (tr != null) return@let tr
                }
                null
            }
            val tabContent = tabRenderer?.optJSONObject("content")
            val richGrid = tabContent?.optJSONObject("richGridRenderer")
            
            richGrid?.let { 
                Result.success(parseGrid(it)) 
            } ?: Result.failure(IllegalStateException("No search results"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGrid(grid: JSONObject): YtFeedGrid {
        val contents = grid.optJSONArray("contents") ?: return YtFeedGrid(emptyList(), null)
        val items = mutableListOf<YtFeedItem>()
        for (i in 0 until contents.length()) {
            parseRichItem(contents.optJSONObject(i))?.let { items.add(it) }
        }
        val continuation = extractContinuation(contents)
        return YtFeedGrid(items, continuation)
    }

    private fun parseRows(sectionList: JSONObject): List<YtFeedRow> {
        val contents = sectionList.optJSONArray("contents") ?: return emptyList()
        val rows = mutableListOf<YtFeedRow>()
        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i)
            val sectionRenderer = section?.optJSONObject("itemSectionRenderer")
            val sectionContents = sectionRenderer?.optJSONArray("contents") ?: continue
            val firstContent = sectionContents.optJSONObject(0)
            val shelf = firstContent?.optJSONObject("shelfRenderer")
            val grid = shelf?.optJSONObject("content")?.optJSONObject("richGridRenderer")
            val horizontalList = shelf?.optJSONObject("content")?.optJSONObject("horizontalListRenderer")
            
            val title = shelf?.optJSONObject("title")?.optJSONObject("simpleText")?.optString("text", "")
                ?: firstContent?.optJSONObject("title")?.optJSONObject("simpleText")?.optString("text", "")
                ?: "Section"
            
            var items: List<YtFeedItem> = emptyList()
            var continuation: String? = null
            
            grid?.let {
                val gridContents = it.optJSONArray("contents")
                if (gridContents != null) {
                    val parsedItems = mutableListOf<YtFeedItem>()
                    for (j in 0 until gridContents.length()) {
                        parseRichItem(gridContents.optJSONObject(j))?.let { parsedItems.add(it) }
                    }
                    items = parsedItems
                }
                continuation = extractContinuation(gridContents)
            }
            
            horizontalList?.let {
                val hlItems = it.optJSONArray("items")
                if (hlItems != null) {
                    val parsedItems = mutableListOf<YtFeedItem>()
                    for (j in 0 until hlItems.length()) {
                        parseRichItem(hlItems.optJSONObject(j))?.let { parsedItems.add(it) }
                    }
                    items = parsedItems
                }
            }
            
            if (items.isNotEmpty()) {
                rows.add(YtFeedRow(title, items, continuation))
            }
        }
        return rows
    }

    private fun parseRichItem(item: JSONObject?): YtFeedItem? {
        item?.let { obj ->
            val richItem = obj.optJSONObject("richItemRenderer") ?: return null
            val content = richItem.optJSONObject("content") ?: return null
            
            // Video
            val videoRenderer = content.optJSONObject("videoRenderer")
            if (videoRenderer != null) {
                return parseVideoRenderer(videoRenderer)
            }
            
            // Channel
            val channelRenderer = content.optJSONObject("channelRenderer")
            if (channelRenderer != null) {
                return parseChannelRenderer(channelRenderer)
            }
            
            // Playlist
            val playlistRenderer = content.optJSONObject("playlistRenderer")
            if (playlistRenderer != null) {
                return parsePlaylistRenderer(playlistRenderer)
            }
            
            // Lockup (compact video)
            val lockupContent = content.optJSONObject("lockupViewModel")?.optJSONObject("content")?.optJSONObject("lockupViewModel")
            if (lockupContent != null) {
                val lockupVideo = lockupContent.optJSONObject("videoRenderer")
                if (lockupVideo != null) return parseVideoRenderer(lockupVideo)
            }
        }
        return null
    }

    private fun parseVideoRenderer(r: JSONObject): YtFeedItem {
        val videoId = r.optString("videoId", "")
        val titleObj = r.optJSONObject("title")
        val title = titleObj?.optJSONArray("runs")?.let { arr ->
            arr.optJSONObject(0)?.optString("text", "")
        } ?: titleObj?.optJSONObject("simpleText")?.optString("text", "") ?: "Untitled"
        val author = r.optJSONObject("ownerText")?.optJSONArray("runs")?.let { arr ->
            arr.optJSONObject(0)?.optString("text", "")
        }
        val channelId = r.optJSONObject("ownerText")?.optJSONArray("runs")?.let { arr ->
            arr.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId", "")
        }?.takeIf { it.isNotBlank() }
        val thumbnail = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { arr ->
            arr.optJSONObject(arr.length() - 1)?.optString("url", "")
        }?.takeIf { it.isNotBlank() }
        val badge = r.optJSONArray("badges")?.let { arr ->
            arr.optJSONObject(0)?.optJSONObject("metadataBadgeRenderer")?.optJSONObject("label")?.optString("text", "")
        }?.takeIf { it.isNotBlank() }
        val isLive = r.optJSONArray("badges")?.let { arr ->
            for (i in 0 until arr.length()) {
                val badgeObj = arr.optJSONObject(i)
                val style = badgeObj?.optJSONObject("metadataBadgeRenderer")?.optString("style", "")
                if (style == "BADGE_STYLE_TYPE_LIVE_NOW") return@let true
            }
            false
        } ?: false
        val isShorts = r.optBoolean("isShorts")
        val percentWatched = r.optInt("percentWatched", 0)
        val continuationKey = r.optString("continuationKey", "").takeIf { it.isNotBlank() }
        
        return YtFeedItem(
            kind = YtItemKind.VIDEO,
            videoId = videoId.takeIf { it.isNotBlank() },
            title = title,
            author = author?.takeIf { it.isNotBlank() },
            thumbnail = thumbnail,
            badge = badge,
            isLive = isLive,
            isShorts = isShorts,
            channelId = channelId,
            percentWatched = percentWatched.coerceIn(0, 100),
            continuationKey = continuationKey,
        )
    }

    private fun parseChannelRenderer(r: JSONObject): YtFeedItem {
        val channelId = r.optString("channelId", "")
        val title = r.optJSONObject("title")?.optJSONObject("simpleText")?.optString("text", "") ?: "Channel"
        val thumbnail = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { arr ->
            arr.optJSONObject(arr.length() - 1)?.optString("url", "")
        }?.takeIf { it.isNotBlank() }
        val videoCount = r.optJSONObject("videoCountText")?.optJSONObject("simpleText")?.optString("text", "")
        val badge = videoCount?.let { "$it videos" }
        
        return YtFeedItem(
            kind = YtItemKind.CHANNEL,
            videoId = null,
            title = title,
            author = null,
            thumbnail = thumbnail,
            badge = badge,
            isLive = false,
            isShorts = false,
            channelId = channelId.takeIf { it.isNotBlank() },
            percentWatched = 0,
            continuationKey = null,
        )
    }

    private fun parsePlaylistRenderer(r: JSONObject): YtFeedItem {
        val playlistId = r.optString("playlistId", "")
        val title = r.optJSONObject("title")?.optJSONObject("simpleText")?.optString("text", "") ?: "Playlist"
        val author = r.optJSONObject("ownerText")?.optJSONArray("runs")?.let { arr ->
            arr.optJSONObject(0)?.optString("text", "")
        }?.takeIf { it.isNotBlank() }
        val thumbnail = r.optJSONArray("thumbnails")?.let { arr ->
            arr.optJSONObject(arr.length() - 1)?.optString("url", "")
        }?.takeIf { it.isNotBlank() }
        val videoCount = r.optInt("videoCount", 0)
        val badge = if (videoCount > 0) "$videoCount videos" else null
        
        return YtFeedItem(
            kind = YtItemKind.PLAYLIST,
            videoId = playlistId.takeIf { it.isNotBlank() }, // reuse videoId field for playlist id
            title = title,
            author = author,
            thumbnail = thumbnail,
            badge = badge,
            isLive = false,
            isShorts = false,
            channelId = null,
            percentWatched = 0,
            continuationKey = null,
        )
    }

    private fun extractContinuation(contents: JSONArray?): String? {
        contents?.let { arr ->
            if (arr.length() > 0) {
                val last = arr.optJSONObject(arr.length() - 1)
                val contItem = last?.optJSONObject("continuationItemRenderer")
                return contItem?.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token", "")
                    ?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    // ── Stream extraction ──

    private fun extractStreamUrl(playerJson: String, videoId: String): YtStream? {
        try {
            val root = JSONObject(playerJson)
            
            // Log the raw response for debugging
            if (root.has("playabilityStatus")) {
                val playability = root.optJSONObject("playabilityStatus")
                val status = playability?.optString("status", "UNKNOWN")
                val reason = playability?.optString("reason", "none")
                Log.d("YouTubeFeedRepo", "Playability for $videoId: $status - $reason")
            }
            
            val streaming = root.optJSONObject("streamingData")
            if (streaming == null) {
                Log.w("YouTubeFeedRepo", "No streamingData in response for $videoId")
                // Check if it's a playability error
                val playability = root.optJSONObject("playabilityStatus")
                val reason = playability?.optString("reason", "unknown")
                Log.w("YouTubeFeedRepo", "Playability reason: $reason")
                return null
            }

            // Try HLS manifest first (for live)
            val hlsManifestUrl = streaming.optString("hlsManifestUrl", "")
            if (hlsManifestUrl.isNotBlank()) {
                Log.d("YouTubeFeedRepo", "Found HLS manifest for $videoId: $hlsManifestUrl")
                // Quick HEAD to verify it's valid
                try {
                    val headRequest = Request.Builder().url(hlsManifestUrl).method("HEAD", null).header("User-Agent", Http.UA).build()
                    val headResp = httpClient.newCall(headRequest).execute()
                    if (headResp.isSuccessful) {
                        headResp.close()
                        return YtStream(
                            url = hlsManifestUrl,
                            title = "", // Will be filled by caller
                            thumbnail = null,
                            videoId = videoId,
                            headers = mapOf("User-Agent" to Http.UA),
                        )
                    }
                    headResp.close()
                    Log.w("YouTubeFeedRepo", "HLS manifest HEAD failed for $videoId")
                } catch (e: Exception) {
                    Log.w("YouTubeFeedRepo", "HLS manifest check failed for $videoId: ${e.message}")
                }
            }

            // Fall back to best progressive format
            val formats = streaming.optJSONArray("formats")
            val adaptiveFormats = streaming.optJSONArray("adaptiveFormats")
            var bestFormat: JSONObject? = null
            var bestScore = 0L
            
            val allFormats = mutableListOf<JSONObject>()
            formats?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { allFormats.add(it) }
                }
            }
            adaptiveFormats?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { allFormats.add(it) }
                }
            }
            
            Log.d("YouTubeFeedRepo", "Found ${allFormats.size} formats for $videoId")
            
            for (fmt in allFormats) {
                val mimeType = fmt.optString("mimeType", "")
                if (!mimeType.startsWith("video/")) continue

                val url = fmt.optString("url", "")
                // Skip formats without a direct URL (need signature deciphering or DASH manifest)
                if (url.isBlank()) {
                    Log.d("YouTubeFeedRepo", "Skipping format ${fmt.optInt("itag")} - no direct URL (mime: $mimeType, height: ${fmt.optInt("height")})")
                    continue
                }

                val hasSignature = url.contains("signature=", ignoreCase = true) || url.contains("sig=", ignoreCase = true)
                val hasSParam = url.contains("&s=", ignoreCase = true) || url.contains("?s=", ignoreCase = true)

                if (hasSParam && !hasSignature) {
                    Log.w("YouTubeFeedRepo", "Format has 's' param but no signature (needs deciphering): ${url.take(200)}")
                    // Skip formats that need signature deciphering
                    continue
                }

                val height = fmt.optInt("height", 0)
                val qualityLabel = fmt.optString("qualityLabel", "")
                val parsedHeight = if (height > 0) height else parseQualityLabel(qualityLabel)
                val fps = fmt.optInt("fps", 0)
                val bitrate = fmt.optLong("bitrate", 0L).takeIf { it > 0 } ?: fmt.optLong("averageBitrate", 0L)
                
                val score = parsedHeight.toLong() * 1_000_000_000L + fps.toLong() * 1_000_000L + bitrate
                if (score > bestScore) {
                    bestScore = score
                    bestFormat = fmt
                }
            }
            
            val url = bestFormat?.optString("url", "")?.takeIf { it.isNotBlank() }
            val title = root.optJSONObject("videoDetails")?.optString("title", "") ?: ""
            val thumbnail = root.optJSONObject("videoDetails")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { arr ->
                arr.optJSONObject(arr.length() - 1)?.optString("url", "")
            }?.takeIf { it.isNotBlank() }
            val clientInfo = streaming.optJSONObject("clientInfo")
            val userAgent = clientInfo?.optString("userAgent", "") ?: ""
            
            return url?.let {
                Log.d("YouTubeFeedRepo", "Selected format for $videoId: ${it.take(200)}")
                YtStream(
                    url = it,
                    title = title,
                    thumbnail = thumbnail,
                    videoId = videoId,
                    headers = userAgent.takeIf { it.isNotBlank() }?.let { mapOf("User-Agent" to it) } ?: mapOf("User-Agent" to Http.UA),
                )
            }
        } catch (e: Exception) {
            Log.e("YouTubeFeedRepo", "extractStreamUrl failed for $videoId: ${e.message}")
            return null
        }
    }

    private fun parseQualityLabel(label: String?): Int {
        if (label.isNullOrBlank()) return 0
        return "\"(\\d{2,4})p\"".toRegex().find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}

private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")