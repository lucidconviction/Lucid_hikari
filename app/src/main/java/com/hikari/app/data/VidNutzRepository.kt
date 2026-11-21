package com.hikari.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object VidNutzRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private const val REQUEST_TIMEOUT_MS = 8_000L
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val okHttpClient = client

    // Invidious instances
    private val INVIDIOUS_INSTANCES = listOf(
        "https://inv.nadeko.net",
        "https://invidious.fdn.fr",
        "https://iv.ggtyler.dev",
        "https://yewtu.be",
    )

    // Piped instances
    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.r4fo.com",
        "https://api.piped.projectsegfau.lt",
    )

    private var instanceIndex = 0
    private var pipedIndex = 0

    private fun rotatedInvidious() = INVIDIOUS_INSTANCES
    private fun rotatedPiped() = PIPED_INSTANCES

    private fun encodeUrl(q: String) = URLEncoder.encode(q, "UTF-8")

    suspend fun fetchTrending(page: Int = 1): List<VidNutzVideo> {
        if (page == 1) {
            try {
                val qs = listOf("popular", "trending", "viral")
                val results = mutableListOf<VidNutzVideo>()
                for (q in qs) {
                    val r = search(q, 1)
                    results.addAll(r.filter { it.durationSeconds in 30..1800 })
                    if (results.size >= 28) break
                }
                if (results.isNotEmpty()) return results.take(28).distinctBy { it.videoId }
            } catch (_: Exception) {}
        }

        for (instance in rotatedPiped()) {
            try {
                val url = "$instance/feed/trending"
                val response = withTimeout(REQUEST_TIMEOUT_MS) { httpGet(url) }
                val parsed = json.decodeFromString<PipedTrendingResponse>(response)
                if (parsed.items.isNotEmpty()) {
                    return parsed.items.filter { it.duration in 30L..1800L }.take(28)
                        .map { it.toVidNutz() }.distinctBy { it.videoId }
                }
            } catch (_: Exception) {}
        }

        for (instance in rotatedInvidious()) {
            try {
                val url = "$instance/api/v1/trending?type=video&page=$page"
                val response = withTimeout(REQUEST_TIMEOUT_MS) { httpGet(url) }
                val raw = json.decodeFromString<List<InvidiousVideo>>(response)
                if (raw.isNotEmpty()) {
                    return raw.filter { it.lengthSeconds in 30..1800 }.take(28)
                        .map { it.toVidNutz() }.distinctBy { it.videoId }
                }
            } catch (_: Exception) {}
        }

        return emptyList()
    }

    suspend fun search(query: String, page: Int = 1): List<VidNutzVideo> {
        if (query.isBlank()) return emptyList()
        val encodedQuery = encodeUrl(query)

        for (instance in rotatedPiped()) {
            try {
                val url = "$instance/search?q=${encodedQuery}&filter=videos&page=$page"
                val response = withTimeout(REQUEST_TIMEOUT_MS) { httpGet(url) }
                val parsed = json.decodeFromString<PipedSearchResponse>(response)
                if (parsed.items.isNotEmpty()) {
                    return parsed.items.filter { it.duration in 30L..1800L }.take(28)
                        .map { it.toVidNutz() }.distinctBy { it.videoId }
                }
            } catch (_: Exception) {}
        }

        for (instance in rotatedInvidious()) {
            try {
                val url = "$instance/api/v1/search?q=${encodedQuery}&type=video&sort=date&page=$page"
                val response = withTimeout(REQUEST_TIMEOUT_MS) { httpGet(url) }
                val raw = json.decodeFromString<List<InvidiousVideo>>(response)
                if (raw.isNotEmpty()) {
                    return raw.filter { it.lengthSeconds in 30..1800 }.take(28)
                        .map { it.toVidNutz() }.distinctBy { it.videoId }
                }
            } catch (_: Exception) {}
        }

        return emptyList()
    }

    suspend fun fetchLive(page: Int = 1): List<VidNutzVideo> {
        if (page > 1) return emptyList()
        val queries = listOf("live stream", "livestream", "live now")
        for (instance in rotatedInvidious()) {
            try {
                for (q in queries) {
                    val url = "$instance/api/v1/search?q=${encodeUrl(q)}&type=video&sort=relevance&date=all"
                    val response = withTimeout(REQUEST_TIMEOUT_MS) { httpGet(url) }
                    val raw = json.decodeFromString<List<InvidiousVideo>>(response)
                    val filtered = raw.filter {
                        it.lengthSeconds in 0..60 || it.publishedText.contains("streaming", ignoreCase = true)
                    }.take(28).map { it.toVidNutz(isLive = true) }.distinctBy { it.videoId }
                    if (filtered.isNotEmpty()) return filtered
                }
            } catch (_: Exception) {}
        }
        for (instance in rotatedPiped()) {
            try {
                val url = "$instance/search?q=${encodeUrl("live stream")}&filter=videos&page=1"
                val response = withTimeout(REQUEST_TIMEOUT_MS) { httpGet(url) }
                val parsed = json.decodeFromString<PipedSearchResponse>(response)
                if (parsed.items.isNotEmpty()) {
                    return parsed.items.filter { it.duration in 0L..300L }.take(28)
                        .map { it.toVidNutz(isLive = true) }.distinctBy { it.videoId }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private val categoryToQuery = mapOf(
        VidNutzCategory.TRENDING to "Trending",
        VidNutzCategory.LIVE to "Live",
        VidNutzCategory.POLITICS to "Politics",
        VidNutzCategory.NEWS to "News",
        VidNutzCategory.MUSIC to "Music",
        VidNutzCategory.SPORTS to "Sports",
        VidNutzCategory.DOCUMENTARY to "Documentary",
        VidNutzCategory.TECHNOLOGY to "Technology",
        VidNutzCategory.ENTERTAINMENT to "Entertainment",
        VidNutzCategory.COMEDY to "Comedy",
        VidNutzCategory.SCIENCE to "Science",
        VidNutzCategory.TRUE_CRIME to "True Crime",
        VidNutzCategory.FOOD_DRINK to "Food",
    )

    suspend fun fetchByCategory(category: VidNutzCategory, page: Int = 1): List<VidNutzVideo> {
        return when (category) {
            VidNutzCategory.TRENDING -> fetchTrending(page)
            VidNutzCategory.LIVE -> fetchLive(page)
            else -> {
                val query = category.displayName
                search(query, page)
            }
        }.distinctBy { it.videoId }
    }

    suspend fun resolveStreamUrl(videoId: String): String? {
        // Try Invidious first for direct stream URL
        for (instance in rotatedInvidious()) {
            try {
                val url = "$instance/api/v1/videos/$videoId"
                val response = withTimeout(REQUEST_TIMEOUT_MS) { httpGet(url) }
                val video = json.decodeFromString<InvidiousVideoResponse>(response)
                if (video.youtubeUrl.isNotEmpty()) {
                    return "https://www.youtube.com/watch?v=$videoId"
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ── Hub helpers ──

    suspend fun getSubVideos(subId: String, queries: List<String>, count: Int = 12): List<VidNutzVideo> {
        val base = queries.firstOrNull()?.takeIf { it.isNotBlank() } ?: "videos"
        return search(base, 1).distinctBy { it.videoId }.take(count)
    }

    suspend fun forceRefreshSubVideos(subId: String, queries: List<String>, count: Int = 12): List<VidNutzVideo> {
        return getSubVideos(subId, queries, count)
    }

    fun getCachedHub(hub: VidNutzHub): Map<String, List<VidNutzVideo>> {
        return emptyMap()
    }

    suspend fun getHubSections(hub: VidNutzHub): Map<String, List<VidNutzVideo>> {
        return withContext(Dispatchers.IO) {
            val sections = mutableMapOf<String, List<VidNutzVideo>>()
            hub.subs.take(6).forEach { sub ->
                val vids = getSubVideos(sub.id, sub.queries, 12)
                if (vids.isNotEmpty()) sections[sub.id] = vids
            }
            sections
        }
    }

    suspend fun getMoreSubVideos(subId: String, queries: List<String>, page: Int): List<VidNutzVideo> {
        val base = queries.firstOrNull()?.takeIf { it.isNotBlank() } ?: "videos"
        return search(base, page).distinctBy { it.videoId }.take(28)
    }

    // ── HTTP ──

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36")
            .build()
        okHttpClient.newCall(request).execute().use { resp ->
            resp.body?.string() ?: ""
        }
    }

    // ── Data classes ──

    data class InvidiousVideo(
        val title: String = "",
        val videoId: String = "",
        val author: String = "",
        val lengthSeconds: Int = 0,
        val viewCount: Long = 0,
        val publishedText: String = "",
    )

    data class InvidiousVideoResponse(
        val youtubeUrl: String = "",
        val description: String = "",
    )

    data class PipedSearchItem(
        val url: String = "",
        val title: String = "",
        val uploader: String = "",
        val duration: Long = 0,
        val views: Long = 0,
        val uploadedDate: String = "",
    )

    data class PipedSearchResponse(
        val items: List<PipedSearchItem> = emptyList(),
    )

    data class PipedTrendingResponse(
        val items: List<PipedSearchItem> = emptyList(),
    )

    private fun PipedSearchItem.toVidNutz(isLive: Boolean = false): VidNutzVideo {
        val videoId = url.removePrefix("/watch?v=")
        return VidNutzVideo(
            videoId = videoId,
            title = title,
            thumbnail = "https://img.youtube.com/vi/$videoId/mqdefault.jpg",
            channelName = uploader,
            durationSeconds = duration.toInt(),
            viewCount = views,
            uploadDate = uploadedDate,
            isLive = isLive || (duration in 0L..5L) || uploadedDate.contains("streaming", ignoreCase = true),
        )
    }

    private fun InvidiousVideo.toVidNutz(isLive: Boolean = false) = VidNutzVideo(
        videoId = videoId,
        title = title,
        thumbnail = "https://img.youtube.com/vi/$videoId/mqdefault.jpg",
        channelName = author,
        durationSeconds = lengthSeconds,
        viewCount = viewCount,
        uploadDate = publishedText,
        isLive = isLive || (lengthSeconds in 0..5) || publishedText.contains("streaming", ignoreCase = true),
    )
}
