package com.hikari.app.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.hikari.app.HikariApp
import com.hikari.app.data.NsfwGate
import com.hikari.app.i18n.tr
import com.hikari.app.ui.layout.DeviceClass
import com.hikari.app.ui.layout.rememberLayoutTokens
import com.hikari.app.ui.navigation.LocalTaskbarInset
import com.hikari.app.ui.navigation.Routes
import com.hikari.app.web.WebViewActivity
import com.hikari.app.web.market.MarketApp
import com.hikari.app.web.market.MarketCategories
import com.hikari.app.web.market.MarketGroup
import com.hikari.app.web.market.WebAppMarketRepo
import kotlinx.coroutines.launch

/**
 * The Web App Market: a curated catalogue of sites, grouped by what they are,
 * with one-tap save into the existing Web Apps shelf.
 *
 * The screen is a sub-route of Web Apps (see `Routes.tabBaseOf`), so it keeps
 * the phone taskbar / tablet dock and highlights the Web Apps tab. It does not
 * auto-save anything: the bookmark control writes the same `Site` the Web Apps
 * tab stores, and tapping a card opens the existing ad-free browser.
 *
 * Adult categories are not built into the screen: they are filtered out
 * completely when NSFW is off, and their data is only ever fetched while it is
 * on (see WebAppMarketRepo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppMarketScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val layout = rememberLayoutTokens()
    val scope = rememberCoroutineScope()

    val state by WebAppMarketRepo.state.collectAsState()
    val savedSites by app.store.sitesFlow().collectAsState(initial = emptyList())
    val nsfw by app.store.nsfwEnabledFlow().collectAsState(initial = NsfwGate.enabled)

    var query by rememberSaveable { mutableStateOf("") }
    var groupId by rememberSaveable {
        mutableStateOf(MarketCategories.groups.first().id)
    }
    var categoryId by rememberSaveable {
        mutableStateOf(MarketCategories.groups.first().categories.first().id)
    }

    val groups: List<MarketGroup> = remember(nsfw) {
        MarketCategories.groups.filter { group -> group.categories.any { !it.adult } || nsfw }
    }
    val group = groups.firstOrNull { it.id == groupId } ?: groups.first()
    val category = group.categories.firstOrNull { it.id == categoryId }
        ?: group.categories.first()
    val queryActive = query.isNotBlank()
    val allApps = state.catalog?.apps.orEmpty()
    val visibleApps = remember(allApps, query, category.id, nsfw) {
        if (queryActive) {
            allApps.filter { (!it.adult || nsfw) && MarketCategories.matches(query, it) }
        } else {
            allApps.filter { it.categoryId == category.id }
        }
    }
    val savedUrls = remember(savedSites) { savedSites.map { it.url }.toSet() }
    val visibleTotal = allApps.count { !it.adult || nsfw }

    LaunchedEffect(Unit) {
        WebAppMarketRepo.ensureLoaded(context)
    }
    // A seed or cache older than a week refreshes itself once; a fresh seed is
    // used without touching the network on the first open.
    LaunchedEffect(state.catalog) {
        val catalog = state.catalog ?: return@LaunchedEffect
        if (WebAppMarketRepo.isStale(catalog) && !state.refreshing) {
            WebAppMarketRepo.refresh(
                context = context,
                includeFmhy = true,
                includeAdult = nsfw,
            )
        }
    }
    // The adult catalogue is runtime-only, so opening the Adult group for the
    // first time is what fetches it. No request is made while NSFW is off.
    LaunchedEffect(group.id, nsfw) {
        if (!nsfw || group.categories.none { it.adult }) return@LaunchedEffect
        val hasAdult = allApps.any { it.adult }
        if (!hasAdult && !state.refreshing) {
            WebAppMarketRepo.refreshAdult(context)
        }
    }
    LaunchedEffect(nsfw, groups) {
        val selected = groups.firstOrNull { it.id == groupId } ?: groups.first()
        if (selected.id != groupId) {
            groupId = selected.id
            categoryId = selected.categories.first().id
        } else if (selected.categories.none { it.id == categoryId }) {
            categoryId = selected.categories.first().id
        }
    }

    fun openSite(site: MarketApp) {
        context.startActivity(
            Intent(context, WebViewActivity::class.java).apply {
                putExtra("url", site.url)
                putExtra("title", site.name)
            }
        )
    }

    fun toggleSaved(site: MarketApp) {
        if (site.url in savedUrls) {
            scope.launch { app.store.removeSite(site.url) }
            android.widget.Toast.makeText(
                context,
                com.hikari.app.i18n.I18n.t("Removed"),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } else {
            scope.launch { app.store.addSite(com.hikari.app.data.Site(site.name, site.url)) }
            android.widget.Toast.makeText(
                context,
                com.hikari.app.i18n.I18n.t("Saved to Web Apps"),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val refresh: () -> Unit = {
        if (!state.refreshing) {
            WebAppMarketRepo.refresh(
                context = context,
                includeFmhy = true,
                includeAdult = nsfw,
                force = true,
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tr("Web App Market") + " · " + visibleTotal,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!nav.popBackStack()) {
                            nav.navigate(Routes.WEBSITES) { launchSingleTop = true }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
                actions = {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = tr("Refresh the market"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        if (state.catalog == null && state.loading) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.pageGutter, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = tr("Clear search"))
                        }
                    }
                },
                placeholder = {
                    Text(
                        tr("Search every category"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = layout.pageGutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.id }) { item ->
                    MarketChip(
                        label = tr(item.label),
                        selected = item.id == group.id,
                        onClick = {
                            groupId = item.id
                            categoryId = item.categories.first().id
                            query = ""
                        },
                    )
                }
            }

            if (group.categories.size > 1) {
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = layout.pageGutter),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(group.categories, key = { it.id }) { item ->
                        val count = allApps.count { it.categoryId == item.id }
                        MarketChip(
                            label = tr(item.label) + if (count > 0) " · $count" else "",
                            selected = item.id == category.id,
                            compact = true,
                            onClick = {
                                categoryId = item.id
                                query = ""
                            },
                        )
                    }
                }
            }

            if (queryActive) {
                Text(
                    tr("Searching every category"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = layout.pageGutter, vertical = 6.dp),
                )
            } else if (category.blurb.isNotBlank()) {
                Text(
                    tr(category.blurb),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = layout.pageGutter, vertical = 6.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (layout.device == DeviceClass.TABLET) 180.dp else 150.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = layout.pageGutter,
                    end = layout.pageGutter,
                    top = 4.dp,
                    bottom = LocalTaskbarInset.current + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MarketBanner(
                            text = tr("Couldn't refresh — showing the last catalogue."),
                            action = tr("Retry"),
                            onAction = refresh,
                        )
                    }
                }

                if (visibleApps.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyMarket(
                            searching = queryActive,
                            loading = state.refreshing,
                            adult = category.adult,
                            onRefresh = refresh,
                        )
                    }
                } else {
                    items(visibleApps, key = { "${it.categoryId}|${it.url}" }) { site ->
                        MarketAppCard(
                            app = site,
                            saved = site.url in savedUrls,
                            onOpen = { openSite(site) },
                            onToggleSaved = { toggleSaved(site) },
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    MarketFooter(catalogFetchedAt = state.catalog?.fetchedAt ?: 0L)
                }
            }
        }
        }
    }
}

@Composable
private fun MarketChip(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp,
            ),
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MarketAppCard(
    app: MarketApp,
    saved: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: () -> Unit,
) {
    val host = remember(app.url) { MarketCategories.hostOf(app.url) }
    // Full-bleed icons need real pixels: icon.horse returns a 256px PNG (often
    // a site's apple-touch-icon, which is sharper than the 32/64px .ico), with
    // Google's 256px favicon as the fallback. The letter card is the last
    // resort when neither service has anything.
    var iconSource by remember(app.url) { mutableIntStateOf(0) }
    val iconUrl = remember(host, iconSource) {
        if (iconSource == 0) {
            "https://icon.horse/icon/$host"
        } else {
            "https://www.google.com/s2/favicons?domain=$host&sz=256"
        }
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        ),
        modifier = Modifier.fillMaxWidth().height(168.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The icon IS the thumbnail now: full bleed under the name, host,
            // rank and bookmark. The gradient underneath keeps transparent or
            // failed favicons from turning into a black square.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f),
                                MaterialTheme.colorScheme.surfaceContainer,
                            )
                        )
                    ),
            )
            SubcomposeAsyncImage(
                model = iconUrl,
                contentDescription = app.name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    if (iconSource == 0) iconSource = 1
                },
                loading = { MarketFaviconBackdrop(app.name) },
                error = { MarketFaviconBackdrop(app.name) },
                success = { SubcomposeAsyncImageContent() },
            )
            // The scrim: a little darkness under the corner buttons, a strong
            // falloff at the bottom for the two lines of text.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.30f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.92f),
                        )
                    ),
            )
            if (app.rank > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Text(
                        "#${app.rank}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            IconButton(
                onClick = onToggleSaved,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (saved) {
                        tr("Remove from Web Apps")
                    } else {
                        tr("Save to Web Apps")
                    },
                    tint = if (saved) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    app.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    host,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The full-bleed fallback while an icon is loading or if it cannot be loaded. */
@Composable
private fun MarketFaviconBackdrop(name: String) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MarketBanner(text: String, action: String, onAction: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onAction,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            ) {
                Text(
                    action,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyMarket(
    searching: Boolean,
    loading: Boolean,
    adult: Boolean,
    onRefresh: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    searching -> tr("No apps match that search.")
                    loading && adult -> tr("Loading adult categories…")
                    loading -> tr("Loading the market…")
                    adult -> tr("No adult apps yet — refresh the market.")
                    else -> tr("No apps in this category yet — refresh the market.")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            if (!loading) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        tr("Refresh"),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketFooter(catalogFetchedAt: Long) {
    val updated = if (catalogFetchedAt > 0L) {
        DateUtils.getRelativeTimeSpanString(
            catalogFetchedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } else {
        ""
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            tr("Links only — nothing is hosted here."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
        if (updated.isNotBlank()) {
            Text(
                tr("Updated {}").replace("{}", updated),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
        Text(
            tr("Adult categories follow Settings → Content → NSFW."),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 10.sp,
        )
    }
}
