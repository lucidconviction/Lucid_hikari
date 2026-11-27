package com.hikari.app.data

import kotlinx.serialization.Serializable

@Serializable
data class VidNutzVideo(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val durationSeconds: Int,
    val viewCount: Long = 0,
    val uploadDate: String = "",
    val isLive: Boolean = false,
)

@Serializable
enum class VidNutzCategory(val displayName: String) {
    TRENDING("Trending"),
    LIVE("Live"),
    POLITICS("Politics"),
    NEWS("News"),
    MUSIC("Music"),
    SPORTS("Sports"),
    DOCUMENTARY("Documentary"),
    TECHNOLOGY("Technology"),
    ENTERTAINMENT("Entertainment"),
    COMEDY("Comedy"),
    SCIENCE("Science"),
    TRUE_CRIME("True Crime"),
    FOOD_DRINK("Food & Drink"),
}

@Serializable
data class VidNutzSub(
    val id: String,
    val name: String,
    val count: Int = 0,
    val queries: List<String> = emptyList(),
)

@Serializable
data class VidNutzHub(
    val id: String,
    val displayName: String,
    val subs: List<VidNutzSub>,
)

@Serializable
data class VidNutzUiState(
    val selectedCategory: VidNutzCategory = VidNutzCategory.TRENDING,
    val videos: List<VidNutzVideo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val searchQuery: String = "",
    val searchResults: List<VidNutzVideo>? = null,
    val searchCurrentPage: Int = 1,
    val searchHasMore: Boolean = true,
    val resolvingVideoId: String? = null,
    val recentSearches: List<String> = emptyList(),
    val selectedHubId: String = "staff_picks",
    val hubSections: Map<String, List<VidNutzVideo>> = emptyMap(),
    val hubLoading: Boolean = false,
    val refreshingSubIds: Set<String> = emptySet(),
    val viewingSubId: String? = null,
    val subVideos: List<VidNutzVideo> = emptyList(),
    val subHasMore: Boolean = true,
    val subPage: Int = 1,
)

@Serializable
data class CachedVideo(
    val videos: List<VidNutzVideo>,
    val timestamp: Long,
    val page: Int = 1,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() - timestamp > 30 * 60 * 1000L
}

@Serializable
data class VidNutzCache(
    val categories: Map<String, CachedVideo> = emptyMap(),
    val searches: Map<String, CachedVideo> = emptyMap(),
    val hubs: Map<String, Map<String, CachedVideo>> = emptyMap(),
)
