package com.hikari.app.youtube

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the YouTube browser screen.
 * Manages feed tabs, search, navigation stack, stream resolution, and player integration.
 */
class YouTubeViewModel(
    private val context: Context,
    private val authRepository: YouTubeAuthRepository,
    private val feedRepository: YouTubeFeedRepository,
    private val onPlay: (YtStream) -> Unit,
    private val onClosePlayer: () -> Unit,
) : ViewModel() {

    // ── UI State ──

    data class UiState(
        val currentTab: YtFeedTab = YtFeedTab.HOME,
        val signedIn: Boolean = false,
        val userName: String? = null,
        val userAvatar: String? = null,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val rows: List<YtFeedRow> = emptyList(),
        val grid: List<YtFeedItem> = emptyList(),
        val continuationKey: String? = null,
        val canLoadMore: Boolean = false,
        val subTitle: String? = null,
        val searchQuery: String = "",
        val searchSuggestions: List<YtSearchSuggestion> = emptyList(),
        val showSearch: Boolean = false,
        val showAccountSheet: Boolean = false,
        val upNext: YtFeedItem? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Navigation stack for back handling (channel/playlist/search drill-down)
    private data class StackEntry(
        val state: UiState,
        val continuationKey: String?,
    )
    private val backStack = mutableListOf<StackEntry>()

    // Stream cache and prefetch
    private val streamCache = mutableMapOf<String, YtStream>()
    private var prefetchJob: kotlinx.coroutines.Job? = null

    // Current feed items for up-next
    private var currentFeedItems: List<YtFeedItem> = emptyList()
    private var playedVideoIds = mutableSetOf<String>()

    init {
        observeAuthState()
        loadCurrentTab()
    }

    // ── Auth observation ──

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.observeSignInState().collect { isSignedIn ->
                updateState { it.copy(signedIn = isSignedIn) }
                if (isSignedIn) {
                    fetchUserInfo()
                } else {
                    updateState { it.copy(userName = null, userAvatar = null) }
                    // If we were on a signed-in tab, switch to Home
                    if (_uiState.value.currentTab.needsSignIn) {
                        selectTab(YtFeedTab.HOME)
                    }
                }
            }
        }
    }

    private suspend fun fetchUserInfo() {
        val userInfo = authRepository.getUserInfo()
        updateState { it.copy(userName = userInfo?.name, userAvatar = userInfo?.picture) }
    }

    // ── Helpers ──

    private inline fun updateState(block: (UiState) -> UiState) {
        _uiState.value = block(_uiState.value)
    }

    // ── Tab selection ──

    fun selectTab(tab: YtFeedTab) {
        if (_uiState.value.currentTab == tab && !_uiState.value.loading) return
        
        val needsSignIn = tab.needsSignIn
        if (needsSignIn && !_uiState.value.signedIn) {
            updateState { it.copy(showAccountSheet = true) }
            return
        }

        backStack.clear()
        updateState {
            it.copy(
                currentTab = tab,
                loading = true,
                error = null,
                rows = emptyList(),
                grid = emptyList(),
                continuationKey = null,
                canLoadMore = false,
                subTitle = null,
                searchQuery = "",
                showSearch = false,
                upNext = null,
            )
        }
        playedVideoIds.clear()
        loadCurrentTab()
    }

    private fun loadCurrentTab() {
        viewModelScope.launch {
            val accessToken = authRepository.getAccessToken()
            val result = feedRepository.fetchFeed(_uiState.value.currentTab, accessToken)
            
            updateState { state ->
                result.fold(
                    onSuccess = { feed ->
                        when (feed) {
                            is List<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val rows = feed as List<YtFeedRow>
                                val items = rows.flatMap { it.items }
                                currentFeedItems = items
                                state.copy(loading = false, rows = rows, grid = emptyList(), canLoadMore = false)
                            }
                            is YtFeedGrid -> {
                                currentFeedItems = feed.items
                                state.copy(loading = false, rows = emptyList(), grid = feed.items, continuationKey = feed.continuationKey, canLoadMore = feed.continuationKey != null)
                            }
                            else -> state.copy(loading = false, error = "Unexpected feed type")
                        }
                    },
                    onFailure = { e ->
                        state.copy(loading = false, error = e.message ?: "Failed to load feed")
                    }
                )
            }
            // Prefetch first video
            currentFeedItems.firstOrNull { it.kind == YtItemKind.VIDEO && !it.isLive }?.let { prefetch(it) }
        }
    }

    // ── Search ──

    fun onSearchQueryChange(query: String) {
        updateState { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            getSearchSuggestions(query)
        } else {
            updateState { it.copy(searchSuggestions = emptyList()) }
        }
    }

    fun startSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return
        
        pushCurrentState()
        updateState {
            it.copy(
                loading = true,
                error = null,
                rows = emptyList(),
                grid = emptyList(),
                continuationKey = null,
                canLoadMore = false,
                subTitle = "Results for \"$query\"",
                showSearch = false,
            )
        }
        viewModelScope.launch {
            val accessToken = authRepository.getAccessToken()
            val result = feedRepository.search(query, accessToken)
            updateState { state ->
                result.fold(
                    onSuccess = { feed ->
                        when (feed) {
                            is YtFeedGrid -> {
                                currentFeedItems = feed.items
                                state.copy(loading = false, grid = feed.items, continuationKey = feed.continuationKey, canLoadMore = feed.continuationKey != null)
                            }
                            else -> state.copy(loading = false, error = "Unexpected search result")
                        }
                    },
                    onFailure = { e -> state.copy(loading = false, error = e.message ?: "Search failed") }
                )
            }
        }
    }

    private fun getSearchSuggestions(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val suggestions = feedRepository.getSearchSuggestions(query)
            updateState { it.copy(searchSuggestions = suggestions) }
        }
    }

    fun clearSearch() {
        if (backStack.isNotEmpty()) {
            goBack()
        } else {
            updateState { it.copy(searchQuery = "", searchSuggestions = emptyList(), showSearch = false) }
            loadCurrentTab()
        }
    }

    // ── Navigation (channel/playlist drill-down) ──

    fun openItem(item: YtFeedItem) {
        when (item.kind) {
            YtItemKind.VIDEO -> {
                playItem(item)
            }
            YtItemKind.CHANNEL -> {
                pushCurrentState()
                updateState {
                    it.copy(loading = true, error = null, rows = emptyList(), grid = emptyList(), continuationKey = null, canLoadMore = false, subTitle = item.title)
                }
                viewModelScope.launch {
                    val accessToken = authRepository.getAccessToken()
                    val result = feedRepository.fetchChannel(item.channelId ?: item.videoId ?: "", accessToken)
                    updateState { state ->
                        result.fold(
                            onSuccess = { feed ->
                                when (feed) {
                                    is List<*> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val rows = feed as List<YtFeedRow>
                                        val items = rows.flatMap { it.items }
                                        currentFeedItems = items
                                        state.copy(loading = false, rows = rows, grid = emptyList(), canLoadMore = false)
                                    }
                                    is YtFeedGrid -> {
                                        currentFeedItems = feed.items
                                        state.copy(loading = false, rows = emptyList(), grid = feed.items, continuationKey = feed.continuationKey, canLoadMore = feed.continuationKey != null)
                                    }
                                    else -> state.copy(loading = false, error = "Unexpected channel result")
                                }
                            },
                            onFailure = { e -> state.copy(loading = false, error = e.message ?: "Failed to open channel") }
                        )
                    }
                }
            }
            YtItemKind.PLAYLIST -> {
                pushCurrentState()
                updateState {
                    it.copy(loading = true, error = null, rows = emptyList(), grid = emptyList(), continuationKey = null, canLoadMore = false, subTitle = item.title)
                }
                viewModelScope.launch {
                    val accessToken = authRepository.getAccessToken()
                    val result = feedRepository.fetchPlaylist(item.videoId ?: "", accessToken)
                    updateState { state ->
                        result.fold(
                            onSuccess = { feed ->
                                when (feed) {
                                    is List<*> -> {
                                        @Suppress("UNCHECKED_CAST")
                                        val rows = feed as List<YtFeedRow>
                                        val items = rows.flatMap { it.items }
                                        currentFeedItems = items
                                        state.copy(loading = false, rows = rows, grid = emptyList(), canLoadMore = false)
                                    }
                                    is YtFeedGrid -> {
                                        currentFeedItems = feed.items
                                        state.copy(loading = false, rows = emptyList(), grid = feed.items, continuationKey = feed.continuationKey, canLoadMore = feed.continuationKey != null)
                                    }
                                    else -> state.copy(loading = false, error = "Unexpected playlist result")
                                }
                            },
                            onFailure = { e -> state.copy(loading = false, error = e.message ?: "Failed to open playlist") }
                        )
                    }
                }
            }
        }
    }

    fun goBack(): Boolean {
        val entry = backStack.lastOrNull() ?: return false
        backStack.removeLast()
        currentFeedItems = entry.state.rows.flatMap { it.items } + entry.state.grid
        _uiState.value = entry.state.copy(loading = false)
        return true
    }

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    private fun pushCurrentState() {
        if (!_uiState.value.loading) {
            backStack.add(StackEntry(_uiState.value, _uiState.value.continuationKey))
        }
    }

    // ── Load more ──

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || !state.canLoadMore) return
        val continuationKey = state.continuationKey ?: return
        
        updateState { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val accessToken = authRepository.getAccessToken()
            val result = feedRepository.fetchContinuation(continuationKey, accessToken)
            updateState { current ->
                result.fold(
                    onSuccess = { feed ->
                        when (feed) {
                            is YtFeedGrid -> {
                                val newItems = current.grid + feed.items
                                currentFeedItems += feed.items
                                current.copy(grid = newItems, loadingMore = false, continuationKey = feed.continuationKey, canLoadMore = feed.continuationKey != null)
                            }
                            else -> current.copy(loadingMore = false)
                        }
                    },
                    onFailure = { current.copy(loadingMore = false) }
                )
            }
        }
    }

    // ── Playback ──

    fun playItem(item: YtFeedItem) {
        if (item.kind != YtItemKind.VIDEO || item.videoId == null) return
        
        // Check cache first
        streamCache[item.videoId!!]?.takeIf { it.isFresh }?.let { stream ->
            onPlay(stream)
            prepareNext(item.videoId!!)
            return
        }
        
        updateState { it.copy(error = null) }
        viewModelScope.launch {
            val accessToken = authRepository.getAccessToken()
            val result = feedRepository.resolveStream(item.videoId!!, accessToken)
            result.fold(
                onSuccess = { stream ->
                    onPlay(stream)
                    streamCache[item.videoId!!] = stream
                    prepareNext(item.videoId!!)
                },
                onFailure = { e ->
                    updateState { it.copy(error = e.message ?: "Couldn't play video") }
                }
            )
        }
    }

    /** Prefetches stream for an item (e.g., when focused). */
    fun prefetch(item: YtFeedItem) {
        if (item.kind != YtItemKind.VIDEO || item.videoId == null) return
        if (streamCache[item.videoId!!]?.isFresh == true) return
        
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val accessToken = authRepository.getAccessToken()
            feedRepository.prefetchStream(item.videoId!!, accessToken)
        }
    }

    /** Prepares the next video for up-next card and instant playback. */
    private fun prepareNext(currentVideoId: String) {
        playedVideoIds += currentVideoId
        
        // Find next in current feed
        val next = currentFeedItems
            .dropWhile { it.videoId != currentVideoId }
            .drop(1)
            .firstOrNull { it.kind == YtItemKind.VIDEO && !it.isLive && it.videoId !in playedVideoIds }
        
        next?.let {
            updateState { it.copy(upNext = next) }
            prefetch(next)
        }
    }

    /** Called when current video ends and autoplay should trigger. */
    fun onVideoEnded(currentVideoId: String) {
        playedVideoIds += currentVideoId
        val next = _uiState.value.upNext
            ?.takeIf { it.videoId !in playedVideoIds }
            ?: currentFeedItems
                .dropWhile { it.videoId != currentVideoId }
                .drop(1)
                .firstOrNull { it.kind == YtItemKind.VIDEO && !it.isLive && it.videoId !in playedVideoIds }
        
        next?.let {
            playItem(it)
        } ?: onClosePlayer()
    }

    // ── Account / Sign-in ──

    fun startSignIn() {
        viewModelScope.launch {
            val deviceCodeResponse = authRepository.startSignIn()
            deviceCodeResponse?.let { response ->
                updateState { it.copy(showAccountSheet = false) }
                showDeviceCodeFlow(response)
            }
        }
    }

    private fun showDeviceCodeFlow(response: DeviceCodeResponse) {
        viewModelScope.launch {
            var interval = response.interval
            val expiresAt = System.currentTimeMillis() + response.expires_in * 1000L
            
            while (System.currentTimeMillis() < expiresAt) {
                val success = authRepository.pollForToken(response.device_code, interval)
                if (success) {
                    // Signed in!
                    updateState { it.copy(signedIn = true) }
                    fetchUserInfo()
                    loadCurrentTab() // Reload current tab with auth
                    return@launch
                }
                kotlinx.coroutines.delay(interval * 1000L)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            updateState { it.copy(signedIn = false, userName = null, userAvatar = null) }
            if (_uiState.value.currentTab.needsSignIn) {
                selectTab(YtFeedTab.HOME)
            }
        }
    }

    fun toggleAccountSheet() {
        updateState { it.copy(showAccountSheet = !it.showAccountSheet) }
    }

    override fun onCleared() {
        prefetchJob?.cancel()
        super.onCleared()
    }
}