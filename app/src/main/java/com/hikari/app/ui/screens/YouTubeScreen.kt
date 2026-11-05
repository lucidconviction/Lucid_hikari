package com.hikari.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hikari.app.player.PlayerActivity
import com.hikari.app.youtube.YouTubeAuthRepository
import com.hikari.app.youtube.YouTubeFeedRepository
import com.hikari.app.youtube.YouTubeSignInKeepAliveService
import com.hikari.app.youtube.YouTubeViewModel
import com.hikari.app.youtube.YtFeedItem
import com.hikari.app.youtube.YtFeedRow
import com.hikari.app.youtube.YtFeedTab
import com.hikari.app.youtube.YtStream
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(nav: NavHostController) {
    val context = LocalContext.current

    val authRepository = remember { YouTubeAuthRepository(context) }
    val feedRepository = remember { YouTubeFeedRepository(context, authRepository) }

    val viewModel: YouTubeViewModel = viewModel(
        factory = YouTubeViewModelFactory(
            context = context,
            authRepository = authRepository,
            feedRepository = feedRepository,
            onPlay = { stream -> launchPlayer(context, stream) },
            onClosePlayer = {},
        )
    )

    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = viewModel.canGoBack) {
        viewModel.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        YouTubeTopAppBar(uiState = uiState, viewModel = viewModel)

        if (uiState.showSearch) {
            YouTubeSearchOverlay(
                uiState = uiState,
                viewModel = viewModel,
                onClose = { viewModel.onSearchQueryChange("") },
            )
        }

        if (uiState.signInActive && uiState.signInCode != null) {
            YouTubeSignInDialog(
                code = uiState.signInCode!!,
                activateLink = uiState.signInActivateLink ?: "https://youtube.com/activate",
                onCancel = { viewModel.cancelSignIn() },
            )
        }

        YouTubeFeedTabs(
            currentTab = uiState.currentTab,
            signedIn = uiState.signedIn,
            onTabClick = { tab -> viewModel.selectTab(tab) },
        )

        uiState.error?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.currentTab.isGrid) {
                YouTubeGridFeed(
                    items = uiState.grid,
                    onItemClick = { item -> viewModel.openItem(item) },
                    onItemFocus = { item -> viewModel.prefetch(item) },
                    loadMore = { viewModel.loadMore() },
                    loadingMore = uiState.loadingMore,
                    canLoadMore = uiState.canLoadMore,
                )
            } else {
                YouTubeRowFeed(
                    rows = uiState.rows,
                    onItemClick = { item -> viewModel.openItem(item) },
                    onItemFocus = { item -> viewModel.prefetch(item) },
                )
            }
        }
    }
}

@Composable
fun YouTubeTopAppBar(
    uiState: YouTubeViewModel.UiState,
    viewModel: YouTubeViewModel,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.subTitle != null || uiState.showSearch) {
                IconButton(
                    onClick = { if (uiState.subTitle != null) viewModel.goBack() else viewModel.onSearchQueryChange("") },
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (uiState.subTitle == null && !uiState.showSearch) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = Color(0xFFCC0000),
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "YouTube",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (uiState.subTitle != null) {
                Text(
                    text = uiState.subTitle!!,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
                if (!uiState.signedIn) {
                    TextButton(
                        onClick = { viewModel.startSignIn() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("Sign In", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { viewModel.toggleAccountSheet() }) {
                        uiState.userAvatar?.let { avatar ->
                            AsyncImage(
                                model = avatar,
                                contentDescription = "",
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                            )
                        } ?: Icon(Icons.Filled.AccountCircle, contentDescription = "Account", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeSearchOverlay(
    uiState: YouTubeViewModel.UiState,
    viewModel: YouTubeViewModel,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onClose() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(top = 16.dp),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search YouTube") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { viewModel.startSearch() }),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
            )

            if (uiState.searchSuggestions.isNotEmpty()) {
                Text(
                    text = "Suggestions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                )
                uiState.searchSuggestions.forEach { suggestion ->
                    Text(
                        text = suggestion.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                            .clickable {
                                viewModel.onSearchQueryChange(suggestion.text)
                                viewModel.startSearch()
                            }
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }
    }
}

@Composable
fun YouTubeFeedTabs(
    currentTab: YtFeedTab,
    signedIn: Boolean,
    onTabClick: (YtFeedTab) -> Unit,
) {
    val tabs = remember { YtFeedTab.values() }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.filter { if (it.needsSignIn) signedIn else true }.forEach { tab ->
            val selected = currentTab == tab
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onTabClick(tab) },
            )
        }
    }
}

@Composable
fun YouTubeRowFeed(
    rows: List<YtFeedRow>,
    onItemClick: (YtFeedItem) -> Unit,
    onItemFocus: (YtFeedItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(rows) { row ->
            if (row.items.isNotEmpty()) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.items.forEach { item ->
                        YouTubeVideoCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onFocus = { onItemFocus(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeGridFeed(
    items: List<YtFeedItem>,
    onItemClick: (YtFeedItem) -> Unit,
    onItemFocus: (YtFeedItem) -> Unit,
    loadMore: () -> Unit,
    loadingMore: Boolean,
    canLoadMore: Boolean,
) {
    val gridState = rememberLazyGridState()
    val columns = if (items.firstOrNull()?.isShorts == true) 2 else 3

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        state = gridState,
    ) {
        items(items) { item ->
            YouTubeGridCard(
                item = item,
                onClick = { onItemClick(item) },
                onFocus = { onItemFocus(item) },
                isShorts = item.isShorts,
            )
        }

        if (loadingMore) {
            item(span = { GridItemSpan(columns) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) {
        val lastIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (canLoadMore && !loadingMore && lastIndex >= items.size - 6) {
            loadMore()
        }
    }
}

@Composable
fun YouTubeVideoCard(
    item: YtFeedItem,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    val cardWidth = 280.dp
    val aspectRatio = 16f / 9f
    val cardHeight = (cardWidth.value / aspectRatio).dp

    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            ) {
                item.thumbnail?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (item.isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .background(Color(0xFFCC0000), RoundedCornerShape(4.dp)),
                        )
                    }
                }

                if (item.percentWatched > 0 && item.percentWatched < 100) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((item.percentWatched / 100f))
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.author != null) {
                    Text(
                        text = item.author!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.badge?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun YouTubeGridCard(
    item: YtFeedItem,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    isShorts: Boolean = false,
) {
    val aspectRatio = if (isShorts) 9f / 16f else 16f / 9f
    val cardWidth = if (isShorts) 160.dp else 180.dp

    Card(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(aspectRatio)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            item.thumbnail?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            }

            if (item.isLive) {
                Box(
                    modifier = Modifier.padding(6.dp).align(Alignment.TopStart),
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .background(Color(0xFFCC0000), RoundedCornerShape(4.dp)),
                    )
                }
            }

            if (item.percentWatched > 0 && item.percentWatched < 100) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.percentWatched / 100f)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    )
                }
            }
        }
    }
}

private fun launchPlayer(context: Context, stream: YtStream) {
    val sources = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "YouTube")
            put("url", stream.url)
            put("isM3u8", stream.url.contains(".m3u8") || stream.url.contains("m3u8"))
            stream.headers.forEach { (k, v) -> put(k, v) }
        })
    }
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", stream.title)
        putExtra("sources", sources.toString())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/** Sign-in dialog showing the device code and a button to open the activation page. */
@Composable
fun YouTubeSignInDialog(
    code: String,
    activateLink: String,
    onCancel: () -> Unit,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { androidx.compose.material3.Text("Sign in to YouTube") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Text("Open the link below on your phone or computer and enter this code:")
                androidx.compose.material3.Text(
                    text = code,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(activateLink))
                context.startActivity(intent)
            }) {
                androidx.compose.material3.Text("Open youtube.com/activate")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                androidx.compose.material3.Text("Cancel")
            }
        },
    )
}

/** Account bottom sheet — sign in when not authenticated, show profile + sign out when signed in. */
@Composable
fun YouTubeAccountSheet(
    signedIn: Boolean,
    userName: String? = null,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            androidx.compose.material3.Text(
                text = "Account",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            if (signedIn) {
                userName?.let { name ->
                    androidx.compose.material3.Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text("Sign Out")
                }
            } else {
                androidx.compose.material3.Text(
                    text = "Sign in to access Subscriptions, History, Playlists, and Channels.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text("Sign In with YouTube")
                }
            }
        }
    }
}

class YouTubeViewModelFactory(
    private val context: Context,
    private val authRepository: YouTubeAuthRepository,
    private val feedRepository: YouTubeFeedRepository,
    private val onPlay: (YtStream) -> Unit,
    private val onClosePlayer: () -> Unit,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return YouTubeViewModel(
            context = context,
            authRepository = authRepository,
            feedRepository = feedRepository,
            onPlay = onPlay,
            onClosePlayer = onClosePlayer,
        ) as T
    }
}