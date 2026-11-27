package com.hikari.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.VidNutzHubs
import com.hikari.app.data.VidNutzRepository
import com.hikari.app.data.VidNutzSub
import com.hikari.app.data.VidNutzUiState
import com.hikari.app.data.VidNutzVideo
import com.hikari.app.data.VidNutzRecentSearchesStore
import com.hikari.app.data.VidNutzSavedSearchesStore
import com.hikari.app.ui.openYouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DarkSurface = Color(0xFF1A1A1A)
private val MutedText = Color(0xFF888888)
private val LightText = Color(0xFFFFFFFF)

@Composable
fun VidNutzScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf(VidNutzUiState()) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var recentSearches by remember { mutableStateOf<List<String>>(emptyList()) }
    var savedSearches by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        recentSearches = withContext(Dispatchers.Default) {
            VidNutzRecentSearchesStore.load()
        }
        savedSearches = withContext(Dispatchers.Default) {
            VidNutzSavedSearchesStore.load()
        }
        uiState = uiState.copy(selectedHubId = VidNutzHubs.menu.firstOrNull()?.id ?: "trending")
        val hub = VidNutzHubs.byId(uiState.selectedHubId)
        if (hub != null) {
            uiState = uiState.copy(hubLoading = true)
            val sections = VidNutzRepository.getHubSections(hub)
            uiState = uiState.copy(hubSections = sections, hubLoading = false)
        }
    }

    fun playVideo(video: VidNutzVideo) {
        scope.launch {
            openYouTubeVideo(context, video.videoId, video.title)
        }
    }

    fun selectHub(hub: com.hikari.app.data.VidNutzHub) {
        if (hub.id == uiState.selectedHubId && uiState.hubSections.isNotEmpty()) return
        uiState = uiState.copy(
            selectedHubId = hub.id, videos = emptyList(), searchResults = null,
            searchQuery = "", hubSections = emptyMap(), hubLoading = true,
            currentPage = 1, hasMore = true, viewingSubId = null, subVideos = emptyList(),
        )
        scope.launch {
            val sections = VidNutzRepository.getHubSections(hub)
            uiState = uiState.copy(hubSections = sections, hubLoading = false)
        }
    }

    fun addSavedSearch(q: String) {
        if (q.isBlank()) return
        val updated = (listOf(q) + savedSearches).distinct().take(20)
        savedSearches = updated
        scope.launch { VidNutzSavedSearchesStore.save(updated) }
    }

    fun removeSavedSearch(q: String) {
        val updated = savedSearches.filterNot { it.equals(q, ignoreCase = true) }
        savedSearches = updated
        scope.launch { VidNutzSavedSearchesStore.save(updated) }
    }

    fun selectSubForView(sub: VidNutzSub) {
        scope.launch {
            val cached = VidNutzRepository.getSubVideos(sub.id, sub.queries, 28)
            uiState = uiState.copy(
                viewingSubId = sub.id, subVideos = cached, subHasMore = cached.isNotEmpty(), subPage = 1,
            )
            val fresh = VidNutzRepository.forceRefreshSubVideos(sub.id, sub.queries, 28)
            if (fresh.isNotEmpty()) {
                uiState = uiState.copy(subVideos = fresh, subHasMore = fresh.isNotEmpty())
            }
        }
    }

    fun loadMoreSubView() {
        val subId = uiState.viewingSubId ?: return
        val hub = VidNutzHubs.byId(uiState.selectedHubId) ?: return
        val subObj = hub.subs.firstOrNull { it.id == subId } ?: return
        scope.launch {
            uiState = uiState.copy(isLoadingMore = true)
            val page = uiState.subPage + 1
            val more = VidNutzRepository.getMoreSubVideos(subObj.id, subObj.queries, page)
                .filter { v -> uiState.subVideos.none { it.videoId == v.videoId } }
            if (more.isNotEmpty()) {
                uiState = uiState.copy(
                    subVideos = uiState.subVideos + more, subPage = page, subHasMore = true, isLoadingMore = false,
                )
            } else {
                uiState = uiState.copy(subHasMore = false, isLoadingMore = false)
            }
        }
    }

    fun refreshSub(sub: VidNutzSub) {
        if (sub.id in uiState.refreshingSubIds) return
        uiState = uiState.copy(refreshingSubIds = uiState.refreshingSubIds + sub.id)
        scope.launch {
            try {
                val fresh = VidNutzRepository.forceRefreshSubVideos(sub.id, sub.queries, 12)
                val cur = uiState
                uiState = cur.copy(
                    hubSections = cur.hubSections + (sub.id to fresh),
                    refreshingSubIds = cur.refreshingSubIds - sub.id,
                )
            } catch (_: Exception) {
                uiState = uiState.copy(refreshingSubIds = uiState.refreshingSubIds - sub.id)
            }
        }
    }

    fun loadMore() {
        if (uiState.isLoadingMore || !uiState.hasMore) return
        val isSearching = uiState.searchQuery.isNotBlank()
        val nextPage = if (isSearching) uiState.searchCurrentPage + 1 else uiState.currentPage + 1
        scope.launch {
            uiState = uiState.copy(isLoadingMore = true)
            val more = if (isSearching) {
                VidNutzRepository.search(uiState.searchQuery, nextPage)
            } else {
                VidNutzRepository.fetchByCategory(uiState.selectedCategory, nextPage)
            }
            if (more.isNotEmpty()) {
                if (isSearching) {
                    uiState = uiState.copy(
                        searchResults = (uiState.searchResults ?: emptyList()) + more,
                        searchCurrentPage = nextPage, searchHasMore = true, isLoadingMore = false,
                    )
                } else {
                    uiState = uiState.copy(
                        videos = uiState.videos + more, currentPage = nextPage, hasMore = true, isLoadingMore = false,
                    )
                }
            } else {
                uiState = uiState.copy(isLoadingMore = false, hasMore = false, searchHasMore = false)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.background(DarkSurface).fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { q ->
                        uiState = uiState.copy(searchQuery = q, searchCurrentPage = 1, searchHasMore = true)
                        searchJob?.cancel()
                        if (q.isNotBlank()) {
                            searchJob = scope.launch {
                                delay(400)
                                val results = VidNutzRepository.search(q)
                                uiState = uiState.copy(searchResults = results, searchHasMore = results.isNotEmpty(), isLoading = false)
                            }
                        } else {
                            uiState = uiState.copy(searchResults = null)
                        }
                    },
                    placeholder = { Text("Search videos...", color = MutedText, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { uiState = uiState.copy(searchQuery = "", searchResults = null) }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = MutedText)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightText, unfocusedTextColor = LightText,
                        focusedBorderColor = Color(0xFF4A90D9), unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = LightText, focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SavedSearchMenu(
                    savedSearches = savedSearches,
                    currentQuery = uiState.searchQuery,
                    onRun = { t ->
                        uiState = uiState.copy(searchQuery = t, searchResults = null, searchCurrentPage = 1, searchHasMore = true, isLoading = true)
                        scope.launch {
                            val r = VidNutzRepository.search(t)
                            uiState = uiState.copy(searchResults = r, searchHasMore = r.isNotEmpty(), isLoading = false)
                        }
                    },
                    onSave = { addSavedSearch(it) },
                    onDelete = { removeSavedSearch(it) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.viewingSubId != null) {
                    IconButton(onClick = { uiState = uiState.copy(viewingSubId = null, subVideos = emptyList()) }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = MutedText, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    val hub = VidNutzHubs.byId(uiState.selectedHubId)
                    val sub = hub?.subs?.firstOrNull { it.id == uiState.viewingSubId }
                    Text(sub?.name ?: "View all", color = LightText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                } else {
                    val hub = VidNutzHubs.byId(uiState.selectedHubId) ?: VidNutzHubs.menu.first()
                    VidNutzChip(
                        label = hub.displayName,
                        isSelected = hub.id == uiState.selectedHubId,
                        onClick = { searchJob?.cancel(); selectHub(hub) },
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        scope.launch {
                            hub.subs.take(3).forEach { sub ->
                                try {
                                    val fresh = VidNutzRepository.forceRefreshSubVideos(sub.id, sub.queries, 12)
                                    if (fresh.isNotEmpty()) {
                                        val cur = uiState
                                        uiState = cur.copy(hubSections = cur.hubSections + (sub.id to fresh))
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = MutedText, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        val searching = uiState.searchQuery.isNotBlank() || uiState.searchResults != null
        if (searching) {
            val searchVids = uiState.searchResults ?: emptyList()
            if (uiState.isLoading && searchVids.isEmpty()) {
                items(6) { ShimmerVideoCard() }
            } else if (searchVids.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No videos found", color = MutedText, fontSize = 14.sp)
                    }
                }
            } else {
                items(searchVids, key = { it.videoId }) { video ->
                    VidNutzVideoCard(video = video, isLoading = uiState.resolvingVideoId == video.videoId, onPlay = { playVideo(video) })
                }
                item(key = "__load_more__") {
                    val showLoading = uiState.isLoadingMore
                    val showButton = !uiState.isLoadingMore && uiState.searchHasMore && searchVids.isNotEmpty()
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        if (showLoading) {
                            CircularProgressIndicator(color = MutedText.copy(alpha = 0.5f), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else if (showButton) {
                            Button(
                                onClick = { loadMore() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            ) {
                                Text("LOAD MORE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        } else if (uiState.viewingSubId != null) {
            val subVids = uiState.subVideos
            if (subVids.isEmpty() && uiState.isLoading) {
                items(6) { ShimmerVideoCard() }
            } else if (subVids.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No videos found", color = MutedText, fontSize = 14.sp)
                    }
                }
            } else {
                items(subVids, key = { it.videoId }) { video ->
                    VidNutzVideoCard(video = video, isLoading = uiState.resolvingVideoId == video.videoId, onPlay = { playVideo(video) })
                }
                item(key = "__sub_load_more__") {
                    val showLoading = uiState.isLoadingMore
                    val showButton = !uiState.isLoadingMore && uiState.subHasMore && subVids.isNotEmpty()
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        if (showLoading) {
                            CircularProgressIndicator(color = MutedText.copy(alpha = 0.5f), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else if (showButton) {
                            Button(
                                onClick = { loadMoreSubView() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            ) {
                                Text("LOAD MORE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        } else {
            val hub = VidNutzHubs.byId(uiState.selectedHubId) ?: VidNutzHubs.menu.first()
            if (uiState.hubLoading && uiState.hubSections.isEmpty()) {
                items(6) { ShimmerVideoCard() }
            } else {
                hub.subs.forEach { sub ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = sub.id) {
                        val videos = uiState.hubSections[sub.id]
                        if (videos == null) {
                            LaunchedEffect(sub.id) {
                                val v = VidNutzRepository.getSubVideos(sub.id, sub.queries, 12)
                                val cur = uiState
                                uiState = cur.copy(hubSections = cur.hubSections + (sub.id to v))
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(sub.name, color = LightText, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(4.dp))
                                val isRefreshing = sub.id in uiState.refreshingSubIds
                                if (isRefreshing) {
                                    CircularProgressIndicator(color = MutedText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    IconButton(onClick = { refreshSub(sub) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Refresh, "Refresh", tint = MutedText, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { selectSubForView(sub) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.ArrowForward, "View all", tint = MutedText, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                videos == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = Color(0xFF4A90D9), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Loading...", color = MutedText, fontSize = 14.sp)
                                }
                                videos.isEmpty() -> Text("No videos found", color = MutedText, fontSize = 14.sp)
                                else -> LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    items(videos, key = { it.videoId }) { video: VidNutzVideo ->
                                        VidRailCard(video = video, onPlay = { playVideo(video) })
                                    }
                                    item(key = "load_more_${sub.id}") {
                                        Button(
                                            onClick = {
                                                val existing = uiState.hubSections[sub.id].orEmpty()
                                                val page = (existing.size / 28) + 1
                                                scope.launch {
                                                    val more = VidNutzRepository.getMoreSubVideos(sub.id, sub.queries, page)
                                                        .filter { v -> existing.none { it.videoId == v.videoId } }
                                                    val cur = uiState
                                                    uiState = cur.copy(hubSections = cur.hubSections + (sub.id to (existing + more).distinctBy { it.videoId }))
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = LightText),
                                            modifier = Modifier.size(120.dp, 72.dp),
                                        ) {
                                            Text("+ MORE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VidNutzVideoCard(video: VidNutzVideo, isLoading: Boolean, onPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable(enabled = !isLoading) { onPlay() }
            .aspectRatio(16f / 9f),
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (video.durationSeconds > 0) {
            val mins = video.durationSeconds / 60
            val secs = video.durationSeconds % 60
            Text(
                "${mins}:${secs.toString().padStart(2, '0')}",
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (video.isLive) {
            Text(
                "LIVE",
                color = Color.Red,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
            )
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
            )
        }
        Text(
            video.title,
            color = LightText,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun VidRailCard(video: VidNutzVideo, onPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable { onPlay() }
            .aspectRatio(16f / 9f),
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            video.title,
            color = LightText,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun VidNutzChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) Color(0xFF4A90D9) else DarkSurface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (isSelected) Color.Black else LightText,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShimmerVideoCard() {
    Box(
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A)),
    ) {}
}

@Composable
private fun SavedSearchMenu(
    savedSearches: List<String>,
    currentQuery: String,
    onRun: (String) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = !showMenu }) {
            Icon(Icons.Filled.Bookmark, "Saved searches", tint = MutedText)
        }
        if (showMenu && savedSearches.isNotEmpty()) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                savedSearches.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s, maxLines = 1) },
                        onClick = { onRun(s); showMenu = false },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = MutedText) },
                        trailingIcon = {
                            IconButton(onClick = { onDelete(s) }) {
                                Icon(Icons.Filled.Delete, "Delete", tint = Color.Red)
                            }
                        },
                    )
                }
                if (currentQuery.isNotBlank() && !savedSearches.any { it.equals(currentQuery, ignoreCase = true) }) {
                    DropdownMenuItem(
                        text = { Text("Save this search", color = MutedText) },
                        onClick = { onSave(currentQuery); showMenu = false },
                    )
                }
            }
        }
    }
}
