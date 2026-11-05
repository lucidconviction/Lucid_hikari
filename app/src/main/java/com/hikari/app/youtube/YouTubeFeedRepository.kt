package com.hikari.app.youtube

import android.content.Context
import android.util.Base64
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
            val (payload, endpoint) = when (tab) {
                YtFeedTab.HOME -> buildBrowsePayload("home", accessToken)
                YtFeedTab.SUBSCRIPTIONS -> buildBrowsePayload("subscriptions", accessToken)
                YtFeedTab.HISTORY -> buildBrowsePayload("history", accessToken)
                YtFeedTab.PLAYLISTS -> buildBrowsePayload("playlists", accessToken)
                YtFeedTab.LIVE -> buildBrowsePayload("live", accessToken)
                YtFeedTab.MUSIC -> buildBrowsePayload("music", accessToken)
                YtFeedTab.GAMING -> buildBrowsePayload("gaming", accessToken)
                YtFeedTab.NEWS -> buildBrowsePayload("news", accessToken)
                YtFeedTab.SPORTS -> buildBrowsePayload("sports", accessToken)
                YtFeedTab.SHORTS -> buildBrowsePayload("shorts", accessToken)
                YtFeedTab.CHANNELS -> buildBrowsePayload("channels", accessToken)
            }
            val response = postJson(endpoint, payload, accessToken)
            parseFeedResponse(response, tab)
        }
    }

    /** Loads the next page of a feed using a continuation key. */
    suspend fun fetchContinuation(continuationKey: String, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val payload = buildContinuationPayload(continuationKey, accessToken)
            val response = postJson(NEXT_URL, payload, accessToken)
            parseContinuationResponse(response)
        }
    }

    /** Searches YouTube. */
    suspend fun search(query: String, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val payload = buildSearchPayload(query, accessToken)
            val response = postJson(SEARCH_URL, payload, accessToken)
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
            val (payload, url) = buildBrowsePayload("channel/$channelId", accessToken)
            val response = postJson(url, payload, accessToken)
            parseFeedResponse(response, YtFeedTab.HOME)
        }
    }

    /** Opens a playlist. */
    suspend fun fetchPlaylist(playlistId: String, accessToken: String?): Result<Any> {
        return withContext(Dispatchers.IO) {
            ensureApiKey()
            val (payload, url) = buildBrowsePayload("playlist/$playlistId", accessToken)
            val response = postJson(url, payload, accessToken)
            parseFeedResponse(response, YtFeedTab.HOME)
        }
    }

    /** Resolves a video to a playable stream. */
    suspend fun resolveStream(videoId: String, accessToken: String?): Result<YtStream> {
        return withContext(Dispatchers.IO) {
            // Check cache first
            streamCache[videoId]?.takeIf { it.isFresh }?.let { return@withContext Result.success(it) }

            ensureApiKey()
            var attempts = 0
            var lastError: String? = null

            while (attempts++ < MAX_CLIENT_SWITCHES) {
                val payload = buildPlayerPayload(videoId, accessToken)
                val response = postJson(PLAYER_URL, payload, accessToken)
                val stream = extractStreamUrl(response, videoId)
                
                if (stream != null) {
                    streamCache[videoId] = stream
                    if (streamCache.size >= MAX_CACHED_STREAMS) streamCache.clear()
                    return@withContext Result.success(stream)
                }
                
                // Switch client and retry
                lastError = "No playable stream with current client"
            }
            Result.failure(IllegalStateException(lastError ?: "No playable stream found"))
        }
    }

    /** Prefetches a stream in the background. */
    suspend fun prefetchStream(videoId: String, accessToken: String?) {
        if (streamCache[videoId]?.isFresh == true) return
        resolveStream(videoId, accessToken).getOrNull()
    }

    // ── Payload builders ──

    private fun buildBrowsePayload(browseId: String, accessToken: String?): Pair<String, String> {
        val payload = JSONObject().apply {
            put("browseId", browseId)
            put("context", buildClientContext(accessToken))
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

    private fun buildPlayerPayload(videoId: String, accessToken: String?): String {
        return JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", buildClientContext(accessToken))
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("html5Preference", "HTML5_PREF_WANTS")
                })
            })
        }.toString()
    }

    private fun buildClientContext(accessToken: String?): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "20.10.35")
                put("osName", "Android")
                put("osVersion", "14")
                put("platform", "MOBILE")
                put("androidSdkVersion", 34)
                put("hl", "en")
                put("gl", "US")
                put("userInterfaceTheme", "USER_INTERFACE_THEME_DARK")
            })
            accessToken?.let { put("auth", it) }
            visitorDataCache?.let { put("visitorData", it) }
        }
    }

    // ── HTTP ──

    private fun postJson(url: String, payload: String, accessToken: String?): String {
        val requestBuilder = Request.Builder()
            .url("$url?key=${apiKeyCache ?: ""}")
            .header("User-Agent", Http.UA)
            .header("Content-Type", "application/json")
            .header("Origin", "https://www.youtube.com")
            .header("X-Youtube-Client-Name", "3")
            .header("X-Youtube-Client-Version", "20.10.35")
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
            val streaming = root.optJSONObject("streamingData") ?: return null

            // Try HLS manifest first (for live)
            val hlsManifestUrl = streaming.optString("hlsManifestUrl", "")
            if (hlsManifestUrl.isNotBlank()) {
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
                } catch (e: Exception) {
                    // Ignore, fall through
                }
            }

            // Fall back to best progressive format
            val formats = streaming.optJSONArray("formats")
            var bestFormat: JSONObject? = null
            var bestScore = 0L
            
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i)
                    val mimeType = fmt?.optString("mimeType", "") ?: ""
                    if (!mimeType.startsWith("video/")) continue
                    
                    val height = fmt?.optInt("height", 0) ?: 0
                    val qualityLabel = fmt?.optString("qualityLabel", "") ?: ""
                    val parsedHeight = if (height > 0) height else parseQualityLabel(qualityLabel)
                    val fps = fmt?.optInt("fps", 0) ?: 0
                    val bitrate = fmt?.optLong("bitrate", 0L) ?: fmt?.optLong("averageBitrate", 0L) ?: 0L
                    
                    val score = parsedHeight.toLong() * 1_000_000_000L + fps.toLong() * 1_000_000L + bitrate
                    if (score > bestScore) {
                        bestScore = score
                        bestFormat = fmt
                    }
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
                YtStream(
                    url = it,
                    title = title,
                    thumbnail = thumbnail,
                    videoId = videoId,
                    headers = userAgent.takeIf { it.isNotBlank() }?.let { mapOf("User-Agent" to it) } ?: mapOf("User-Agent" to Http.UA),
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseQualityLabel(label: String?): Int {
        if (label.isNullOrBlank()) return 0
        return "\"(\\d{2,4})p\"".toRegex().find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}

private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")