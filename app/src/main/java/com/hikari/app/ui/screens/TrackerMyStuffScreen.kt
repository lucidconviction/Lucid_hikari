package com.hikari.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.hikari.app.HikariApp
import com.hikari.app.data.TrackerKind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.hikari.app.data.MediaType
import com.hikari.app.data.TrackerCalendarRepository
import com.hikari.app.data.TrackerLibraryRepository
import com.hikari.app.i18n.tr
import com.hikari.app.ui.components.GlassCard
import com.hikari.app.ui.effects.rememberShelfSnap
import com.hikari.app.ui.layout.rememberLayoutTokens
import com.hikari.app.ui.navigation.LocalTaskbarInset
import com.hikari.app.ui.navigation.Routes

/**
 * My Stuff → Trackers.
 *
 * The watchlists, histories and personal lists from the services the user has
 * signed in to, in one place beside the library they saved themselves. Before
 * this section existed the tracker catalogue was a MODE of the Library screen
 * (Settings → a single "tracker library source" pick), which meant seeing your
 * AniList list replaced your saved titles, and a second tracker could not be
 * shown at all.
 *
 * Every connected service is loaded separately and shown under its own header,
 * so AniList, MAL, Simkl and Trakt can all be read side by side. Each request
 * goes through [TrackerLibraryRepository] — the same parser Home's tracker
 * shelves use — so a fix there fixes this page too.
 *
 * A failure on one service is shown on that service's header and does not stop
 * the others: a dead Trakt token must not hide a working AniList.
 */
@Composable
fun TrackerMyStuffScreen(nav: NavHostController, embedded: Boolean = false) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as HikariApp
    val layout = rememberLayoutTokens()
    val flow = remember { app.store.trackersFlow() }
    val accounts by flow.collectAsState(initial = emptyList())
    val supported = remember(accounts) {
        accounts.filter { it.kind in TRACKER_LIBRARY_KINDS }
    }

    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var shelves by remember {
        mutableStateOf<Map<String, List<TrackerLibraryRepository.Shelf>>>(emptyMap())
    }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var calendar by remember { mutableStateOf<List<TrackerCalendarRepository.Entry>>(emptyList()) }

    // Keyed on the connected identities, not on the list instance: the flow
    // re-emits the same accounts on unrelated writes, and reloading ten HTTP
    // requests for that would make the page flicker.
    val identityKey = supported.joinToString(",") { it.kind.key + "|" + it.user }
    LaunchedEffect(identityKey, reload) {
        if (supported.isEmpty()) {
            shelves = emptyMap()
            errors = emptyMap()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val loaded = LinkedHashMap<String, List<TrackerLibraryRepository.Shelf>>()
        val failed = LinkedHashMap<String, String>()
        // Sequential on purpose: each repository call already fans its own
        // endpoints out concurrently, and six services at once would be a
        // thundering herd on the user's connection.
        supported.forEach { account ->
            val result = TrackerLibraryRepository.load(app, account.kind.key)
            result.onSuccess { if (it.isNotEmpty()) loaded[account.kind.key] = it }
            result.onFailure { failed[account.kind.key] = it.message ?: com.hikari.app.i18n.I18n.t("Could not load") }
        }
        shelves = loaded
        errors = failed
        loading = false
        calendar = runCatching { TrackerCalendarRepository.load(app) }
            .getOrDefault(emptyList())
    }

    val openItem: (com.hikari.app.data.MediaItem) -> Unit = { item ->
        if (item.rawType == "manga") {
            Routes.safeNavigate(nav, Routes.mangaSearch(item.title))
        } else {
            Routes.safeNavigate(
                nav,
                Routes.detail(
                    providerId = item.providerId,
                    type = item.type,
                    mediaId = item.id,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    rawType = item.rawType,
                ),
            )
        }
    }
    val series = remember(shelves) {
        shelves.values.flatten().flatMap { it.items }
            .filter { it.type == MediaType.SERIES }
            .distinctBy { it.uniqueId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = layout.pageGutter,
            end = layout.pageGutter,
            top = 14.dp,
            bottom = LocalTaskbarInset.current + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
    ) {
        item(key = "tracker-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (!embedded) {
                        Text(
                            tr("Trackers"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (supported.isEmpty()) {
                            tr("Watchlists and lists from the trackers you sign in to")
                        } else {
                            tr("Your watchlists and lists, straight from your trackers")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (supported.isNotEmpty()) {
                    IconButton(onClick = { reload++ }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = tr("Refresh"),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (calendar.isNotEmpty()) {
            item(key = "calendar-header") {
                Text(
                    tr("Upcoming"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            calendar.groupBy { it.day }.forEach { (day, entries) ->
                item(key = "calendar-day-" + day) {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                entries.forEach { entry ->
                    item(key = "calendar-" + entry.atMillis + "-" + entry.title) {
                        TrackerCalendarRow(entry) {
                            entry.item?.let(openItem)
                        }
                    }
                }
            }
        }

        if (supported.isEmpty()) {
            item(key = "tracker-connect") {
                GlassCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr("No tracker connected"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(
                                "Sign in to AniList, MyAnimeList, Kitsu, Simkl, Shikimori or Trakt " +
                                    "in Settings → Trackers, and your watchlists appear here."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { Routes.navigateTab(nav, Routes.SETTINGS) }) {
                            Text(tr("Open Settings"))
                        }
                    }
                }
            }
            return@LazyColumn
        }

        if (loading && shelves.isEmpty()) {
            item(key = "tracker-loading") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        tr("Loading your trackers…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        supported.forEach { account ->
            item(key = "tracker-${account.kind.key}") {
                TrackerSectionHeader(
                    label = account.kind.label,
                    user = account.user,
                    error = errors[account.kind.key],
                    // An empty-but-successful load explains itself through the
                    // repository's own diagnosis ("connected, but the list is
                    // empty"), which is more useful than a generic line.
                    note = TrackerLibraryRepository.errors["tracker:${account.kind.key}"],
                )
            }
            val list = shelves[account.kind.key].orEmpty()
            list.forEachIndexed { index, shelf ->
                item(key = "tracker-shelf-${account.kind.key}-$index") {
                    TrackerShelfRow(
                        title = shelf.title,
                        items = shelf.items,
                        onOpen = { item ->
                            if (item.rawType == "manga") {
                                Routes.safeNavigate(nav, Routes.mangaSearch(item.title))
                            } else {
                                Routes.safeNavigate(
                                    nav,
                                    Routes.detail(
                                        providerId = item.providerId,
                                        type = item.type,
                                        mediaId = item.id,
                                        title = item.title,
                                        posterUrl = item.posterUrl,
                                        rawType = item.rawType,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
        // The cross-tracker Series row comes AFTER the sources: each tracker's
        // own categories stay grouped under its name, which is the order that
        // reads as "Simkl → its lists" rather than a nameless row on top.
        if (series.isNotEmpty()) {
            item(key = "tracker-series") {
                TrackerShelfRow(
                    title = tr("Series"),
                    items = series,
                    onOpen = openItem,
                )
            }
        }
    }
}

/** The trackers whose lists [TrackerLibraryRepository] can read. */
private val TRACKER_LIBRARY_KINDS = setOf(
    TrackerKind.ANILIST,
    TrackerKind.MAL,
    TrackerKind.KITSU,
    TrackerKind.SIMKL,
    TrackerKind.SHIKIMORI,
    TrackerKind.TRAKT,
)

@Composable
private fun TrackerSectionHeader(
    label: String,
    user: String,
    error: String?,
    note: String?,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (user.isNotBlank()) {
                Text(
                    "  ·  " + user,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val line = error ?: note
        if (!line.isNullOrBlank()) {
            Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackerCalendarRow(
    entry: TrackerCalendarRepository.Entry,
    onClick: () -> Unit,
) {
    val clickable = entry.item != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 38.dp, height = 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val poster = entry.poster
            if (!poster.isNullOrBlank()) {
                AsyncImage(
                    model = poster,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                entry.time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                entry.tracker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackerShelfRow(
    title: String,
    items: List<com.hikari.app.data.MediaItem>,
    onOpen: (com.hikari.app.data.MediaItem) -> Unit,
) {
    val state = rememberLazyListState()
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberShelfSnap(state),
        ) {
            items(items, key = { it.uniqueId }) { item ->
                TrackerLibraryCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}
