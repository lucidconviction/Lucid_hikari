package com.hikari.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.hikari.app.data.AnimeCatalogRepository
import com.hikari.app.data.MediaItem
import com.hikari.app.i18n.tr
import com.hikari.app.ui.rememberPosterStyle
import com.hikari.app.ui.rememberVisibleItems
import com.hikari.app.ui.components.PosterCard
import com.hikari.app.ui.navigation.Routes
import com.hikari.app.tv.TvMode
import com.hikari.app.tv.TvScreenHeader
import com.hikari.app.tv.TvUi

private const val TAB_TRENDING = 0
private const val TAB_POPULAR = 1

/**
 * AniList's Trending / Popular catalogues as a full grid — the "Show All"
 * destination of Home's Anime spotlight.
 *
 * Paged: AniList answers 30 titles a page, and scrolling near the end asks for
 * the next one. The two tabs are two different sorts of the same query, so
 * switching is one request (cached after the first visit).
 */
@Composable
fun AnimeSpotlightScreen(
    nav: NavHostController,
    initialTab: Int = TAB_TRENDING,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(TAB_TRENDING, TAB_POPULAR)) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()
    val style = rememberPosterStyle()
    val shown = rememberVisibleItems(items)

    fun kind() = if (tab == TAB_POPULAR) "popular" else "trending"

    // First page of the selected tab (and every retry).
    LaunchedEffect(tab, attempt) {
        items = emptyList()
        page = 0
        done = false
        loading = true
        val first = runCatching { AnimeCatalogRepository.catalog(kind(), 1) }
            .getOrDefault(emptyList())
        items = first
        page = 1
        done = first.isEmpty()
        loading = false
    }

    // Fill while the end of the grid is in sight. Keyed on the state that
    // changes after each page, so the next request starts only when the last
    // one has finished.
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, tab, done, loading, page) {
        if (!nearEnd || done || loading || page <= 0) return@LaunchedEffect
        loading = true
        val next = runCatching { AnimeCatalogRepository.catalog(kind(), page + 1) }
            .getOrDefault(emptyList())
        val known = items.map { it.uniqueId }.toSet()
        items = items + next.filter { it.uniqueId !in known }
        page += 1
        done = next.isEmpty()
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        if (TvMode.current()) {
            TvScreenHeader(
                title = tr("Anime spotlight"),
                subtitle = tr("Trending & popular on AniList"),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Anime spotlight"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        tr("Trending & popular on AniList"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tab == TAB_TRENDING,
                onClick = { tab = TAB_TRENDING },
                label = { Text(tr("Trending")) },
            )
            FilterChip(
                selected = tab == TAB_POPULAR,
                onClick = { tab = TAB_POPULAR },
                label = { Text(tr("Popular")) },
            )
        }

        when {
            items.isEmpty() && loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            items.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    tr("Couldn't reach AniList right now."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { attempt++ }) { Text(tr("Retry")) }
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = TvUi.gridMinFor(100)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(shown, key = { it.uniqueId }) { item ->
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        PosterCard(
                            item = item,
                            style = style,
                            onClick = {
                                com.hikari.app.player.PlaybackQueueStore.set(shown, item)
                                Routes.safeNavigate(
                                    nav,
                                    Routes.detail(
                                        item.providerId, item.type, item.id,
                                        item.title, item.posterUrl, item.rawType,
                                    ),
                                )
                            },
                        )
                    }
                }
                if (loading && !done) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
