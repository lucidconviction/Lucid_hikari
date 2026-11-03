package com.hikari.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Switch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.hikari.app.data.ProviderType
import com.hikari.app.data.Collection
import com.hikari.app.providers.ContentProvider
import com.hikari.app.data.Profiles
import com.hikari.app.i18n.tr

/**
 * The television top-right cluster, in the shape of the CloudStream desktop
 * reference: a provider pill (icon + name, opens the provider box) and a
 * profile pill (avatar + name, opens the profile menu). Row is right-aligned
 * so it hugs the screen's top-right corner above the feed.
 */
@Composable
fun TvHomeTopBar(
    providerName: String?,
    providerIconUrl: String?,
    profileName: String?,
    onProviderClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvProviderPill(
            name = providerName,
            iconUrl = providerIconUrl,
            onClick = onProviderClick,
        )
        Spacer(Modifier.width(10.dp))
        TvProfilePill(
            name = profileName,
            onClick = onProfileClick,
        )
    }
}

@Composable
fun TvProviderPill(
    name: String?,
    iconUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = TvDesktop.SurfaceCard,
        modifier = modifier
            .tvPress(previewPass = true, onClick = onClick)
            .border(1.dp, TvDesktop.GlassEdge, RoundedCornerShape(50)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvProviderIcon(iconUrl = iconUrl, name = name, size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                name ?: tr("All providers"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TvDesktop.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = TvDesktop.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun TvProfilePill(
    name: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = TvDesktop.SurfaceCard,
        modifier = modifier
            .tvPress(previewPass = true, onClick = onClick)
            .border(1.dp, TvDesktop.GlassEdge, RoundedCornerShape(50)),
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initial = name?.trim()?.firstOrNull()?.uppercase() ?: "M"
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Cs3.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                name ?: tr("Main"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TvDesktop.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = TvDesktop.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The television home header as ONE line, CloudStream-desktop-style: the
 * title and tagline on the left, then the mouse, search, translate and
 * site buttons, then the provider pill and the profile pill — everything in
 * a single row, so the pills row and the button row can never stack into two.
 *
 * [selected] is the header's extension pick (null on All/collections): it
 * gates the translate and site buttons exactly like the phone header, and
 * [onSettings] is non-null only while the Settings tab is hidden from the
 * taskbar.
 */
@Composable
fun TvHomeHeaderBar(
    providerName: String?,
    providerIconUrl: String?,
    profileName: String?,
    onProviderClick: () -> Unit,
    onProfileClick: () -> Unit,
    selected: String?,
    onSearch: () -> Unit,
    onTranslate: () -> Unit,
    onVerify: () -> Unit,
    onMouse: () -> Unit,
    mouseOn: Boolean,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val accent = Cs3.accent
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tr("Lucid Hikari"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tr("Every stream, one place."),
                style = MaterialTheme.typography.bodyMedium,
                color = TvDesktop.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMouse) {
            Icon(
                Icons.Filled.Mouse,
                contentDescription = tr("Mouse cursor"),
                tint = if (mouseOn) accent else accent.copy(alpha = 0.7f),
                modifier = Modifier.size(if (mouseOn) 26.dp else 24.dp),
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = tr("Search"), tint = accent)
        }
        selected?.let { pid ->
            val translateOn = com.hikari.app.data.Translator.isOn(pid)
            IconButton(onClick = onTranslate) {
                Text(
                    tr("A\u3042"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (translateOn) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (selected != null) {
            IconButton(onClick = onVerify) {
                Icon(
                    Icons.Filled.Public,
                    contentDescription = tr("Open this extension's site in a web view"),
                    tint = accent,
                )
            }
        }
        onSettings?.let { open ->
            IconButton(onClick = open) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = tr("Settings"),
                    tint = accent,
                )
            }
        }
        TvProviderPill(
            name = providerName,
            iconUrl = providerIconUrl,
            onClick = onProviderClick,
        )
        Spacer(Modifier.width(10.dp))
        TvProfilePill(
            name = profileName,
            onClick = onProfileClick,
        )
    }
}

/** A provider's icon, or its initial letter when it declares none. */
@Composable
fun TvProviderIcon(
    iconUrl: String?,
    name: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (!iconUrl.isNullOrBlank()) {
        AsyncImage(
            model = iconUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White),
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Cs3.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name?.trim()?.firstOrNull()?.uppercase() ?: "•",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Cs3.accent,
            )
        }
    }
}

/**
 * The "Select Provider" box, in the reference shape: title on the left with
 * the "All Plugins (Global)" pill at its right, the filter full-width below,
 * and one card per installed provider in a grid. A tap chooses and closes; a
 * hold toggles that provider into the multi-pick without closing, so the old
 * strip's hold-to-multi lives on here. The grid collapses to its content
 * (bounded above) instead of holding a fixed tall box, so the dialog's bottom
 * is just room for the buttons, never a slab of empty panel.
 */
@Composable
fun TvSelectProviderDialog(
    providers: List<ContentProvider>,
    selection: List<String>,
    onPickAll: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onToggleMulti: ((String) -> Unit)? = null,
    onManage: (() -> Unit)? = null,
    /** The user's own collections (search callers pass theirs; Home passes
     *  none). Rendered as full-width cards above the provider grid, picked
     *  by their provider key like anything else. */
    collections: List<Collection> = emptyList(),
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(providers, query) {
        val q = query.trim()
        if (q.isBlank()) providers
        else providers.filter { it.config.name.contains(q, ignoreCase = true) }
    }
    val shownCollections = remember(collections, query) {
        val q = query.trim()
        if (q.isBlank()) collections
        else collections.filter { it.name.contains(q, ignoreCase = true) }
    }
    // Wide reference card (desktop Select Provider): the platform-default
    // AlertDialog width is what truncated the title to "Select ..." and
    // clipped the All Plugins pill on TV.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(TvDesktop.CornerLarge),
            color = TvDesktop.SurfaceElevated,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 760.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Select Provider"),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = TvDesktop.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    val allSelected = selection.isEmpty()
                    Surface(
                        onClick = onPickAll,
                        shape = RoundedCornerShape(10.dp),
                        color = if (allSelected) Cs3.accent.copy(alpha = 0.28f)
                        else TvDesktop.SurfaceCard,
                        modifier = Modifier.tvPress(previewPass = true, onClick = onPickAll)
                            .border(
                                1.dp,
                                if (allSelected) Cs3.accent
                                else TvDesktop.GlassEdge,
                                RoundedCornerShape(10.dp),
                            ),
                    ) {
                        // Reference pill: the label alone, no icons.
                        Text(
                            tr("All Plugins (Global)"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allSelected) Cs3.accent
                            else TvDesktop.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Text(
                    tr("Choose a dedicated provider or search across all installed plugins"),
                    style = MaterialTheme.typography.bodySmall,
                    color = TvDesktop.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            tr("Filter providers…"),
                            color = TvDesktop.TextMuted,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = TvDesktop.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = tr("Clear"),
                                    tint = TvDesktop.TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                ) {
                    items(shownCollections, key = { "tv-col-" + it.id }, span = { GridItemSpan(maxLineSpan) }) { col ->
                    val key = com.hikari.app.ui.navigation.Routes.COLLECTION_PROVIDER_PREFIX + col.id
                    TvProviderCard(
                        name = col.name,
                        supporting = tr("Collection"),
                        selected = key in selection,
                        icon = {
                            TvProviderIcon(
                                iconUrl = null,
                                name = col.name,
                                size = 34.dp,
                            )
                        },
                        onClick = { onPick(key) },
                        onHold = onToggleMulti?.let { toggle -> { toggle(key) } },
                    )
                }
                items(shown, key = { "tv-sel-" + it.config.id }) { p ->
                        val id = p.config.id
                        val dupTag = if (shown.count { q -> q.config.name == p.config.name } > 1) {
                            p.config.id.split("|").drop(1).firstOrNull()?.trim()
                                ?.takeIf { it.isNotEmpty() }?.let { "($it)" }
                        } else null
                        TvProviderCard(
                            name = p.config.name,
                            supporting = tvProviderSubtitle(p),
                            tag = dupTag,
                            selected = id in selection,
                            icon = {
                                TvProviderIcon(
                                    iconUrl = p.config.iconUrl,
                                    name = p.config.name,
                                    size = 28.dp,
                                )
                            },
                            onClick = { onPick(id) },
                            onHold = onToggleMulti?.let { toggle -> { toggle(id) } },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onManage != null) {
                        TextButton(onClick = onManage) { Text(tr("Manage feed")) }
                    }
                    TextButton(onClick = onDismiss) { Text(tr("Close")) }
                }
            }
        }
    }
}

/**
 * "Manage Home Screen Feed", in the reference shape: an "Enable
 * Multi-Provider Feed" switch, type chips (Movies/Series/Anime/Docs/Live),
 * and either a single-provider grid (switch off — a tap picks and closes) or
 * the Active/Available two-pane feed mixer (switch on — reorder with arrows,
 * add/remove, Save & Close applies the mix).
 */
@Composable
fun TvManageFeedDialog(
    providers: List<ContentProvider>,
    selection: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val working = remember(selection) { mutableStateOf(ArrayList(selection)) }
    var isAdvancedMode by remember { mutableStateOf(selection.size > 1) }
    var typeFilter by remember { mutableStateOf(setOf<String>()) }
    // Per-provider catalog counts for the "N catalogs" subtitles (reference
    // rows name the count, not the engine). Loaded concurrently off the tap
    // path; a provider that does not answer in time keeps its type subtitle.
    val catalogCounts = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(providers) {
        providers.forEach { p ->
            launch {
                val n = withTimeoutOrNull(8_000L) {
                    runCatching { p.catalogs().size }.getOrDefault(0)
                }
                if (n != null) catalogCounts[p.config.id] = n
            }
        }
    }
    fun matches(p: ContentProvider): Boolean {
        if (typeFilter.isEmpty()) return true
        return tvFeedCats(p.config.type).any { it in typeFilter }
    }
    val feedCats = listOf("Movies", "Series", "Anime", "Docs", "Live")
    Dialog(
        onDismissRequest = {
            // Reference guard: closing the single screen while several are
            // active keeps the top one rather than the multi set.
            if (!isAdvancedMode && working.value.size > 1) {
                val top = working.value.firstOrNull() ?: providers.firstOrNull()?.config?.id
                if (top != null) onSave(listOf(top)) else onDismiss()
            } else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(TvDesktop.CornerLarge),
            color = TvDesktop.SurfaceElevated,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isAdvancedMode) tr("Manage Home Screen Feed") else tr("Select Provider"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TvDesktop.TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (isAdvancedMode) tr("Mix, reorder, and customize multiple providers.")
                            else tr("Choose a provider to display on your Home Screen."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TvDesktop.TextMuted,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Enable Multi-Provider Feed"),
                            style = MaterialTheme.typography.labelLarge,
                            color = TvDesktop.TextPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isAdvancedMode,
                            onCheckedChange = { enabled ->
                                isAdvancedMode = enabled
                                if (!enabled) {
                                    val top = working.value.firstOrNull()
                                        ?: providers.firstOrNull()?.config?.id
                                    working.value = if (top != null) ArrayList(listOf(top)) else ArrayList()
                                }
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                // TYPE CHIPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    feedCats.forEach { c ->
                        val on = c in typeFilter
                        Surface(
                            onClick = { typeFilter = if (on) typeFilter - c else typeFilter + c },
                            shape = RoundedCornerShape(50),
                            color = if (on) Cs3.accent.copy(alpha = 0.28f) else TvDesktop.SurfaceCard,
                            border = BorderStroke(
                                1.dp,
                                if (on) Cs3.accent else TvDesktop.GlassEdge,
                            ),
                        ) {
                            Text(
                                c,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (on) TvDesktop.TextPrimary else TvDesktop.TextMuted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // CONTENT
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!isAdvancedMode) {
                        // Single-provider grid: a tap picks and closes.
                        val filtered = providers.filter { matches(it) }.sortedBy { it.config.name }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 250.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .background(TvDesktop.SurfaceCard.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(filtered, key = { "tv-feed-single-" + it.config.id }) { p ->
                                val active = p.config.id in working.value
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) Cs3.accent.copy(alpha = 0.22f) else TvDesktop.SurfaceElevated)
                                        .clickable {
                                            working.value = ArrayList(listOf(p.config.id))
                                            onSave(listOf(p.config.id))
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TvProviderIcon(iconUrl = p.config.iconUrl, name = p.config.name, size = 32.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            p.config.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = TvDesktop.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        // Reference names the catalog count, not the engine.
                                        val singleCount = catalogCounts[p.config.id]
                                        Text(
                                            if (singleCount != null) "$singleCount catalogs"
                                            else tvProviderSubtitle(p),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TvDesktop.TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Active/Available two-pane mixer.
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Active feed.
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Text(tr("Active Providers Feed"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TvDesktop.TextPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                if (working.value.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(TvDesktop.SurfaceCard, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(tr("No active providers"), color = TvDesktop.TextMuted)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize().background(TvDesktop.SurfaceCard, RoundedCornerShape(12.dp)).padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        itemsIndexed(working.value.toList(), key = { _, id -> "tv-feed-active-$id" }) { index, id ->
                                            val p = providers.firstOrNull { it.config.id == id } ?: return@itemsIndexed
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = TvDesktop.SurfaceElevated,
                                                border = BorderStroke(0.5.dp, TvDesktop.GlassEdge),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        (index + 1).toString() + ".",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TvDesktop.TextMuted,
                                                        modifier = Modifier.padding(end = 10.dp, start = 4.dp),
                                                    )
                                                    TvProviderIcon(iconUrl = p.config.iconUrl, name = p.config.name, size = 40.dp)
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(p.config.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TvDesktop.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        val activeCount = catalogCounts[id]
                                                        Text(
                                                            if (activeCount != null) "Catalogs: $activeCount active"
                                                            else tvProviderSubtitle(p),
                                                            style = MaterialTheme.typography.bodySmall, color = TvDesktop.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.background(TvDesktop.SurfaceCard.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 4.dp),
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                val cur = ArrayList(working.value)
                                                                if (index > 0) {
                                                                    val tmp = cur[index - 1]
                                                                    cur[index - 1] = cur[index]
                                                                    cur[index] = tmp
                                                                    working.value = cur
                                                                }
                                                            },
                                                            enabled = index > 0,
                                                            modifier = Modifier.size(28.dp),
                                                        ) {
                                                            Icon(Icons.Filled.ArrowUpward, contentDescription = tr("Move Up"), tint = TvDesktop.TextMuted, modifier = Modifier.size(16.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val cur = ArrayList(working.value)
                                                                if (index < cur.lastIndex) {
                                                                    val tmp = cur[index + 1]
                                                                    cur[index + 1] = cur[index]
                                                                    cur[index] = tmp
                                                                    working.value = cur
                                                                }
                                                            },
                                                            enabled = index < working.value.lastIndex,
                                                            modifier = Modifier.size(28.dp),
                                                        ) {
                                                            Icon(Icons.Filled.ArrowDownward, contentDescription = tr("Move Down"), tint = TvDesktop.TextMuted, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    IconButton(
                                                        onClick = { working.value = ArrayList(working.value - id) },
                                                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(50)),
                                                    ) {
                                                        Icon(Icons.Filled.Close, contentDescription = tr("Remove"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            // Available plugins.
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Text(tr("Available Plugins"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TvDesktop.TextPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                val inactive = providers.filter { it.config.id !in working.value && matches(it) }.sortedBy { it.config.name }
                                if (inactive.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(TvDesktop.SurfaceCard.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(tr("All plugins are active"), color = TvDesktop.TextMuted)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize().background(TvDesktop.SurfaceCard.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(inactive, key = { "tv-feed-avail-" + it.config.id }) { p ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(TvDesktop.SurfaceElevated).padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TvProviderIcon(iconUrl = p.config.iconUrl, name = p.config.name, size = 32.dp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(p.config.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TvDesktop.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    val availCount = catalogCounts[p.config.id]
                                                    Text(
                                                        if (availCount != null) "$availCount catalogs available"
                                                        else tvProviderSubtitle(p),
                                                        style = MaterialTheme.typography.bodySmall, color = TvDesktop.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                FilledTonalIconButton(onClick = { working.value = ArrayList((working.value + p.config.id).distinct()) }) {
                                                    Icon(Icons.Filled.Add, contentDescription = tr("Add"))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                // BOTTOM ACTION BAR
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onSave(working.value.toList()) }) {
                        Text(tr("Save & Close"))
                    }
                }
            }
        }
    }
}

/** Reference feed categories for a provider kind (the Manage dialog chips). */
private fun tvFeedCats(type: ProviderType): Set<String> = when (type) {
    ProviderType.ANIYOMI, ProviderType.FORK_VIDEO, ProviderType.ANYMEX, ProviderType.SORA -> setOf("Anime")
    ProviderType.IPTV -> setOf("Live")
    ProviderType.MANGA, ProviderType.ANYMEX_MANGA -> emptySet()
    else -> setOf("Movies", "Series", "Docs")
}

/** Reference Select-Provider card subtitle: "Types • domain". */
private fun tvProviderSubtitle(p: ContentProvider): String {
    val t = when (p.config.type) {
        ProviderType.ANIYOMI, ProviderType.FORK_VIDEO, ProviderType.ANYMEX, ProviderType.SORA -> "Anime"
        ProviderType.IPTV -> "Live"
        else -> "Movies • Series"
    }
    val d = runCatching {
        val u = p.config.url.trim()
        if (u.isBlank()) "" else java.net.URI(u).host?.removePrefix("www.").orEmpty()
    }.getOrDefault("")
    return if (d.isBlank()) t else "$t • $d"
}

@Composable
fun TvProviderCard(
    name: String,
    supporting: String?,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onHold: (() -> Unit)? = null,
    /** Duplicate-name tag ("(repo)") — the reference disambiguates twins. */
    tag: String? = null,
    /** Phone picker actions (pin, pack caret) — null on TV, which has none. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Cs3.accent.copy(alpha = 0.28f)
                else TvDesktop.SurfaceCard.copy(alpha = 0.45f)
            )
            .border(
                1.dp,
                if (selected) Cs3.accent
                else TvDesktop.GlassEdge,
                shape,
            )
            .clickable(onClick = onClick)
            .tvPress(previewPass = true, onClick = onClick, onHold = onHold)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = TvDesktop.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!tag.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = Cs3.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!supporting.isNullOrBlank()) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = TvDesktop.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            trailing()
        }
    }
}

/**
 * The profile menu: the active profile first, every other profile to switch
 * into, an inline Add row, and Manage (which opens Settings, where the full
 * profile management lives). A locked target refuses the switch — the caller
 * reports it (see [onLockedTarget]).
 */
@Composable
fun TvProfileMenuDialog(
    profiles: List<Profiles.Profile>,
    activeId: String?,
    onSwitch: (String) -> Unit,
    onAdd: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(TvDesktop.CornerLarge),
        containerColor = TvDesktop.SurfaceElevated,
        title = { Text(tr("Profiles"), fontWeight = FontWeight.Bold, color = TvDesktop.TextPrimary) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    tr("Active Profile"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Cs3.accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                profiles.forEach { p ->
                    val active = p.id == activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TvDesktop.CornerCard))
                            .background(
                                if (active) Cs3.accent.copy(alpha = 0.16f)
                                else Color.Transparent
                            )
                            .clickable(enabled = !active) { onSwitch(p.id) }
                            .tvPress(previewPass = true, onClick = { if (!active) onSwitch(p.id) })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val initial = p.name.trim().firstOrNull()?.uppercase() ?: "•"
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) Cs3.accent
                                    else TvDesktop.SurfaceElevated
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                initial,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                else TvDesktop.TextMuted,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            p.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (active) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Cs3.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (adding) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = { Text(tr("Profile name…")) },
                        shape = RoundedCornerShape(TvDesktop.CornerCard),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            adding = false
                            newName = ""
                        }) { Text(tr("Cancel")) }
                        TextButton(
                            onClick = {
                                val name = newName.trim()
                                if (name.isNotEmpty()) {
                                    adding = false
                                    newName = ""
                                    onAdd(name)
                                }
                            }
                        ) { Text(tr("Create")) }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TvDesktop.CornerCard))
                            .clickable { adding = true }
                            .tvPress(previewPass = true, onClick = { adding = true })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Cs3.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr("Add Profile"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(TvDesktop.CornerCard))
                        .clickable(onClick = onManage)
                        .tvPress(previewPass = true, onClick = onManage)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = TvDesktop.TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr("Manage Profiles"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Close")) }
        },
    )
}
