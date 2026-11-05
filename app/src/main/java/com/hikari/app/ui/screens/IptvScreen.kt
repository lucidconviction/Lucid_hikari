package com.hikari.app.ui.screens

import com.hikari.app.ui.components.LocalHideHelp

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hikari.app.HikariApp
import com.hikari.app.data.IptvPlaylist
import com.hikari.app.data.MultiViewSession
import com.hikari.app.data.QuickChannel
import com.hikari.app.data.CustomRegion
import com.hikari.app.data.QuickChannelList
import com.hikari.app.data.IptvChannel
import com.hikari.app.data.MediaItem
import com.hikari.app.data.MediaType
import com.hikari.app.data.NetworkStream
import com.hikari.app.data.Profiles
import com.hikari.app.data.ProviderConfig
import com.hikari.app.data.ProviderType
import com.hikari.app.data.TileShapes
import com.hikari.app.i18n.I18n
import com.hikari.app.i18n.tr
import com.hikari.app.providers.IptvProvider
import com.hikari.app.providers.gatherIptvChannels
import com.hikari.app.ui.IptvArt
import com.hikari.app.ui.navigation.LocalTaskbarInset
import com.hikari.app.ui.navigation.Routes
import com.hikari.app.ui.theme.rememberGlassTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import com.hikari.app.tv.tvTextFieldKeys

/**
 * The IPTV tab: the user's playlists as tiles, and the channels inside them.
 *
 * The app already plays IPTV — a playlist is a provider, so its channels appear
 * in Home's source picker, in the global search and in the player's server list.
 * What it had no home for was *browsing* a playlist: the only place a playlist
 * was listed at all was a row in Extensions, and its groups were buried in
 * Home's catalog picker. This is the IPTV-shaped surface the user asked for
 * ("add in taskbar IPTV button … all added iptv link it shows here as Poster box
 * like in personal catalog creator, and clicking it open that iptv link
 * catalog").
 *
 * The hierarchy is the playlist's own:
 *   IPTV tab → one tile per playlist → one tile per group ("Sports", "India",
 *   "Movies 24/7") → the group's channels, as a paged poster grid
 *   ([CatalogScreen], which every IPTV group already answers through the
 *   provider contract).
 *
 * Nothing here reaches TMDB: a live channel has no entry there, and the artwork
 * lookup knows it (see [com.hikari.app.data.IptvMark]). A playlist's tile wears
 * the first real channel logo it has, and every channel without a logo gets a
 * tile drawn from its own name ([IptvArt]).
 *
 * The tab's button is OFF by default — see Settings → Taskbar buttons, where it
 * can be switched on like any other tab (see [com.hikari.app.data.AppStore.iptvTabFlow]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvScreen(nav: NavHostController) {
    val app = LocalContext.current.applicationContext as HikariApp
    val all by app.providers.providers.collectAsState()
    val playlists = remember(all) { all.filterIsInstance<IptvProvider>() }
    // One tile shape per section — each of the three pages remembers its own
    // look (the playlist page keeps the original key).
    val playlistShapeFlow = remember { app.store.iptvShapeFlow() }
    val playlistShape by playlistShapeFlow.collectAsState(initial = TileShapes.POSTER)
    val torShapeFlow = remember { app.store.iptvTorShapeFlow() }
    val torShape by torShapeFlow.collectAsState(initial = TileShapes.POSTER)
    val streamShapeFlow = remember { app.store.iptvStreamShapeFlow() }
    val streamShape by streamShapeFlow.collectAsState(initial = TileShapes.POSTER)
    val scope = rememberCoroutineScope()
    val uiContext = LocalContext.current

    // "+" opens the add-playlist dialog right here, with the link field already
    // in front of the user — the tab used to bounce to Extensions and leave them
    // to find "Add IPTV playlist" themselves ("make clicking it directly open
    // m3u8 entering link so user can add directly from there too").
    var showAdd by remember { mutableStateOf(false) }
    var addLink by remember { mutableStateOf("") }
    var addName by remember { mutableStateOf("") }
    // Whether the dialog is adding a PLAYLIST or a single NETWORK STREAM. Both
    // are IPTV providers and both end up as a tile in this tab; the difference
    // is what the link IS (a list of channels, or one file on some host that has
    // to be resolved before it plays) — see [NetworkStream].
    var addStream by remember { mutableStateOf(false) }
    var addXtream by remember { mutableStateOf(false) }
    var addXtreamUrl by remember { mutableStateOf("") }
    var addXtreamUser by remember { mutableStateOf("") }
    var addXtreamPass by remember { mutableStateOf("") }
    // F3 — errors from validating the Xtream credentials: shown inline in the Add
    // dialog instead of letting a broken source be saved as a silent empty tile.
    var xtreamError by remember { mutableStateOf("") }
    var xtreamTestResult by remember { mutableStateOf("") }
    var xtreamTesting by remember { mutableStateOf(false) }
    var addTorrent by remember { mutableStateOf(false) }
    // F3 — the add dialog's mode selector is a DROPDOWN on a phone: four pills
    // (Playlist / Network stream / Torrent / Xtream panel) in a row pushed the
    // link field and the help text off the bottom of a narrow screen, and the
    // chosen mode's own label was the only thing visible. A dropdown collapses
    // the four options into one field that shows the current mode, so the rest
    // of the form (link, name, Add) stays on screen. The selected mode is still
    // the same four-state machine below — only the affordance changes.
    var addModeExpanded by remember { mutableStateOf(false) }
    val addModeLabel = when {
        addTorrent -> tr("Torrent")
        addStream -> tr("Network stream")
        addXtream -> tr("Xtream panel")
        else -> tr("Playlist")
    }
    val addModeIndex = when {
        addTorrent -> 2
        addStream -> 1
        addXtream -> 3
        else -> 0
    }
    fun selectAddMode(index: Int) {
        when (index) {
            0 -> { addStream = false; addTorrent = false; addXtream = false }
            1 -> { addStream = true; addTorrent = false; addXtream = false }
            2 -> { addStream = true; addTorrent = true; addXtream = false }
            3 -> {
                addXtream = true
                addStream = false
                addTorrent = false
                // The panel fields are the input here; the plain link field
                // below is for the other modes and only clutters Xtream.
                addLink = ""
                xtreamError = ""
                xtreamTestResult = ""
            }
        }
    }
    var addFileLabel by remember { mutableStateOf("") }
    var addFilePath by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = copyPlaylistInto(uiContext, uri)
                if (picked == null) {
                    Toast.makeText(uiContext, I18n.t("Could not read that file"), Toast.LENGTH_LONG).show()
                } else {
                    addFileLabel = picked.first
                    addFilePath = picked.second
                }
            }
        }
    }

    fun addNow(playAfter: Boolean = false) {
        val link = addLink.trim()
        // Re-derived through the same normalization the save uses, so a pasted
        // bare info hash (saved magnetized) is found after Add saves it.
        val normalized = NetworkStream.normalizePastedLink(link)
        val local = addFilePath.takeIf { it.isNotBlank() }
        val name = addName
        val wasStream = addStream
        val wantTorrent = addTorrent
        val wasXtream = addXtream
        adding = true
        scope.launch {
            // Keep preview in scope for account saving after the if-else
            var preview: com.hikari.app.providers.XtreamPreview? = null
            val result = if (wasXtream) {
                // F3 — Xtream panel: validate the credentials (URL shape, then the
                // panel's own player_api.php) BEFORE anything is saved, so a dead
                // host or a wrong password is reported here instead of becoming
                // an empty tile that only shows up later as "Didn't load".
                val creds = com.hikari.app.data.XtreamCredentials(
                    baseUrl = addXtreamUrl.trim(),
                    username = addXtreamUser.trim(),
                    password = addXtreamPass.trim(),
                )
                val credError = creds.validate()
                if (credError != null) {
                    adding = false
                    Toast.makeText(uiContext, credError, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val probe = IptvProvider.previewXtream(creds)
                preview = probe.getOrNull()
                if (preview == null) {
                    adding = false
                    xtreamError = probe.exceptionOrNull()?.message
                        ?: I18n.t("Could not reach that panel")
                    Toast.makeText(
                        uiContext,
                        xtreamError,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                addIptvPlaylist(
                    app,
                    preview.m3uUrl,
                    local,
                    name.ifBlank { preview.serverName },
                )
            } else if (wasStream) {
                addNetworkStream(app, link, name)
            } else {
                addIptvPlaylist(app, link, local, name)
            }
            adding = false
            result.fold(
                onSuccess = { n ->
                    app.providers.refresh()
                    val msg = when {
                        wantTorrent -> I18n.t("Added torrent stream")
                        wasStream -> I18n.t("Added network stream")
                        else -> I18n.t("Added IPTV playlist (%s channels)").replace("%s", n.toString())
                    }
                    Toast.makeText(uiContext, msg, Toast.LENGTH_LONG).show()
                    showAdd = false
addStream = false
                    addTorrent = false
                    addXtream = false
                    addLink = ""
                    addName = ""
                    addFileLabel = ""
                    addFilePath = ""
                    addXtreamUrl = ""
                    addXtreamUser = ""
                    addXtreamPass = ""
                    xtreamError = ""
                    xtreamTestResult = ""
                    xtreamTesting = false
                    if (playAfter && wasStream) {
                        val saved = app.store.providers().firstOrNull { p ->
                            p.type == com.hikari.app.data.ProviderType.IPTV &&
                                (p.url == link || p.extra == link)
                        }
                        if (saved != null) {
                            // Play means the detail page now, not the folder list: a
                            // network stream is one file and opens straight on Play.
                            if (NetworkStream.isTorrentLink(saved.url)) {
                                Routes.safeNavigate(
                                    nav,
                                    Routes.catalog(
                                        providerId = saved.id,
                                        catalogId = IptvProvider.CATALOG_ALL,
                                        title = saved.name,
                                        providerName = saved.name,
                                        type = MediaType.MOVIE,
                                        rawType = "torrent",
                                    ),
                                )
                            } else {
                                Routes.safeNavigate(
                                    nav,
                                    Routes.detail(
                                        providerId = saved.id,
                                        type = MediaType.MOVIE,
                                        mediaId = saved.url,
                                        title = saved.name,
                                        posterUrl = null,
                                        rawType = "stream",
                                    ),
                                )
                            }
                        }
                    }
                },
                onFailure = { t ->
                    Toast.makeText(
                        uiContext,
                        t.message ?: I18n.t("Could not read that playlist"),
                        Toast.LENGTH_LONG,
                    ).show()
addStream = false
                    addTorrent = false
                    addXtream = false
                    addLink = ""
                    addName = ""
                    addFileLabel = ""
                    addFilePath = ""
                    addXtreamUrl = ""
                    addXtreamUser = ""
                    addXtreamPass = ""
                    xtreamError = ""
                    xtreamTestResult = ""
                    xtreamTesting = false
                },
            )
            // Save the Xtream account separately so it shows in the quick-access row
            if (wasXtream && preview != null) {
                val previewData = preview as? com.hikari.app.providers.XtreamPreview
                    ?: run { adding = false; return@launch }
                val credsForAcc = com.hikari.app.data.XtreamCredentials(
                    baseUrl = addXtreamUrl.trim(),
                    username = addXtreamUser.trim(),
                    password = addXtreamPass.trim(),
                )
                val acc = com.hikari.app.data.XtreamAccount(
                    name = previewData.serverName,
                    baseUrl = credsForAcc.root,
                    username = credsForAcc.username,
                    password = credsForAcc.password,
                    channels = previewData.channelCount,
                    movies = 0,
                    series = 0,
                    status = previewData.status,
                    expDate = previewData.expDate,
                )
                val currentAccs = app.store.xtreamAccounts().toMutableList()
                val key = "${acc.baseUrl}|${acc.username}"
                val idx = currentAccs.indexOfFirst { "${it.baseUrl}|${it.username}" == key }
                if (idx >= 0) currentAccs[idx] = acc else currentAccs.add(acc)
                app.store.setXtreamAccounts(currentAccs)
            }
        }
    }

    // Removes one playlist from the tab. The provider is dropped first, so the
    // tile goes away immediately (the grid is keyed on the provider list), and
    // then the app's own copy of the file is deleted — but only when it really
    // is the app's copy (a playlist picked from storage, under filesDir/iptv/)
    // and nothing else still lists it: a linked playlist has nothing local, and
    // a file another profile (or another provider) still uses must stay (the
    // same rule the Extensions screen's remove follows).
    fun removePlaylist(card: IptvCard) {
        scope.launch {
            val target = app.store.providers().firstOrNull { it.id == card.id }
            app.store.removeProvider(card.id)
            app.providers.refresh()
            // Forget the cached read: this playlist's channel count and any
            // error belong to a row that no longer exists. Adding the same link
            // again reuses its id (see addIptvPlaylist), and must not inherit a
            // stale count or a stale "didn't load" badge.
            IptvProvider.forget(card.id)
            val path = target?.url.orEmpty()
            val base = app.filesDir.absolutePath + "/iptv/"
            if (path.startsWith(base) &&
                app.store.providers().none { it.url == path } &&
                !Profiles.otherProfilesReference(app, path)
            ) {
                withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
            }
        }
    }

    var cards by remember { mutableStateOf<List<IptvCard>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    // The playlist whose tile was tapped on its trash button. Removal is
    // confirmed in a dialog first: it takes the playlist's channels out of
    // Home, search and the player's server list, and deletes the app's own
    // copy when the playlist was imported from storage.
    var removeTarget by remember { mutableStateOf<IptvCard?>(null) }
    // One re-read of every playlist, keyed on which playlists exist: adding or
    // removing one is what has to re-run this, not a recomposition.
    val ids = remember(playlists) { playlists.map { it.config.id } }
    LaunchedEffect(ids) {
        if (playlists.isEmpty()) {
            cards = emptyList()
            return@LaunchedEffect
        }
        reading = true
        val out = ArrayList<IptvCard>(playlists.size)
        // Three at a time: a playlist is a whole M3U download, and a user with a
        // dozen of them should not fire a dozen at once.
        playlists.chunked(3).forEach { chunk ->
            out += withContext(Dispatchers.IO) { chunk.map { readCard(it) } }
            cards = out.toList()
        }
        reading = false
    }

    // ---- Three swipeable sections like My Stuff (IPTV | Tor | Network stream)
    // The pill strip and the pager share one `section` state in both directions
    // (tap animates over, swipe settles back); TV draws the section directly
    // with pills as the only switcher, like MyStuffScreen does.
    var section by rememberSaveable { mutableStateOf(IptvSection.PLAYLIST) }
    val tvNow = com.hikari.app.tv.TvMode.current()
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { 2 },
    )
    var pagingProgrammatically by remember { mutableStateOf(false) }
    fun pageIndex(s: String): Int = when (s) {
        IptvSection.LINKS -> 1
        else -> 0
    }
    fun sectionAt(page: Int): String = when (page) {
        1 -> IptvSection.LINKS
        else -> IptvSection.PLAYLIST
    }
    LaunchedEffect(section) {
        val target = pageIndex(section)
        if (target == pager.settledPage) return@LaunchedEffect
        if (target == pager.currentPage && !pager.isScrollInProgress) return@LaunchedEffect
        pagingProgrammatically = true
        try {
            if (tvNow) pager.scrollToPage(target)
            else pager.animateScrollToPage(target)
        } finally {
            pagingProgrammatically = false
        }
    }
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page ->
            if (pagingProgrammatically) return@collect
            val shown = sectionAt(page)
            if (shown != section) section = shown
        }
    }

    val playlistCards = remember(cards) { cards.filter { !it.torrent && !it.stream } }
    val torCards = remember(cards) { cards.filter { it.torrent } }
    val streamCards = remember(cards) { cards.filter { it.stream && !it.torrent } }

    // Opens the add dialog preset to one section's kind: Tor takes a magnet /
    // .torrent URL / info hash, Network stream a direct link, IPTV a playlist
    // link or file. The dialog's own pills can still switch after.
    fun presetAdd(kind: String) {
        when (kind) {
            IptvSection.LINKS -> {
                addStream = true
                addTorrent = false  // Start with network stream, can toggle
            }
            else -> {
                addStream = false
                addTorrent = false
            }
        }
        addLink = ""
        addName = ""
        addFileLabel = ""
        addFilePath = ""
        // Drop any Xtream entry left over from a previous dialog session so the
        // + button lands on a clean playlist link, not a stale panel form.
        addXtream = false
        xtreamError = ""
        xtreamTestResult = ""
        showAdd = true
    }

    /** F2 — the header's Validate button. Probes every installed playlist's
     *  channels in parallel (3 at a time, same as the card read) and caches
     *  the results, so the health dots on each playlist's tile update
     *  without re-reading the whole M3U. Declared before [SectionPage] so
     *  the composable can call it without a forward-reference issue. */
    fun validateAllPlaylists() {
        val ps = playlists
        if (ps.isEmpty()) return
        scope.launch {
            ps.chunked(3).forEach { chunk ->
                withContext(Dispatchers.IO) {
                    chunk.forEach { p ->
                        val chs = runCatching { p.channels() }.getOrNull().orEmpty()
                        chs.filter { it.url.startsWith("http") }.forEach { c ->
                            IptvProbeCache.put(c.url, com.hikari.app.net.StreamProbe.probe(c.url))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SectionPage(kind: String) {
        IptvSectionPage(
            kind = kind,
            playlistCards = playlistCards,
            torCards = torCards,
            streamCards = streamCards,
            playlistsEmpty = playlists.isEmpty(),
            reading = reading,
            playlistShape = playlistShape,
            torShape = torShape,
            streamShape = streamShape,
            nav = nav,
            onCyclePlaylist = { scope.launch { app.store.setIptvShape(nextShape(playlistShape)) } },
            onCycleTor = { scope.launch { app.store.setIptvTorShape(nextShape(torShape)) } },
            onCycleStream = { scope.launch { app.store.setIptvStreamShape(nextShape(streamShape)) } },
            onAddPreset = { presetAdd(it) },
            onRemove = { removeTarget = it },
            onValidateAll = { validateAllPlaylists() },
            onRefresh = { scope.launch { app.providers.refresh() } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Quick Channels section at the TOP of IPTV tab (no separate tab)
        QuickChannelsSection(nav, playlists)

        // Xtream accounts quick access row
        XtreamAccountRow(app = app)

        Spacer(Modifier.height(8.dp))
        IptvSectionStrip(
            sections = listOf(IptvSection.PLAYLIST, IptvSection.LINKS),
            current = section,
            onPick = { section = it },
        )
        if (tvNow) {
            Box(Modifier.fillMaxSize()) { SectionPage(section) }
        } else {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                SectionPage(sectionAt(page))
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = {
                if (!adding) {
                    showAdd = false
                    addStream = false; addTorrent = false; addXtream = false
                    addLink = ""; addName = ""; addFileLabel = ""; addFilePath = ""
                    addXtreamUrl = ""; addXtreamUser = ""; addXtreamPass = ""
                    xtreamError = ""; xtreamTestResult = ""; xtreamTesting = false
                }
            },
            title = { Text(if (addTorrent) tr("Add torrent") else if (addStream) tr("Add network stream") else tr("Add IPTV playlist")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Playlist or single link. Kept in front of the fields
                    // because it changes what they mean: a playlist link is
                    // read and its channels listed, a stream link is kept as it
                    // is and worked out when it is played. On a phone the four
                    // modes are a DROPDOWN: four pills in a row pushed the link
                    // field and the help text off the bottom of a narrow
                    // screen, and the chosen mode's own label was the only
                    // thing visible. On a television (or tablet in landscape)
                    // the pills stay — a row of four reads fine where a
                    // dropdown would only save vertical space the form already
                    // has.
                    val addModeIsPortrait = LocalConfiguration.current.orientation ==
                        android.content.res.Configuration.ORIENTATION_PORTRAIT
                    if (addModeIsPortrait) {
                        ExposedDropdownMenuBox(
                            expanded = addModeExpanded,
                            onExpandedChange = { addModeExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = addModeLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(tr("Mode")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = addModeExpanded,
                                    )
                                },
                            )
                            ExposedDropdownMenu(
                                expanded = addModeExpanded,
                                onDismissRequest = { addModeExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(tr("Playlist")) },
                                    onClick = { selectAddMode(0); addModeExpanded = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Network stream")) },
                                    onClick = { selectAddMode(1); addModeExpanded = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Torrent")) },
                                    onClick = { selectAddMode(2); addModeExpanded = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Xtream panel")) },
                                    onClick = { selectAddMode(3); addModeExpanded = false },
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AddModePill(
                                label = tr("Playlist"),
                                selected = !addStream,
                                onClick = { addStream = false; addTorrent = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            AddModePill(
                                label = tr("Network stream"),
                                selected = addStream && !addTorrent,
                                onClick = { addStream = true; addTorrent = false },
                            )
                            Spacer(Modifier.width(8.dp))
                            AddModePill(
                                label = tr("Torrent"),
                                selected = addTorrent,
                                onClick = { addStream = true; addTorrent = true },
                            )
                            Spacer(Modifier.width(8.dp))
                            AddModePill(
                                label = tr("Xtream panel"),
                                selected = addXtream,
                                onClick = {
                                    addXtream = !addXtream
                                    if (addXtream) {
                                        addStream = false
                                        addTorrent = false
                                        addLink = ""
                                        xtreamError = ""
                                        xtreamTestResult = ""
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!LocalHideHelp.current) {
                    Text(
                        if (addTorrent) {
                            tr(
                                "Paste a magnet link, a .torrent URL, or a bare info hash. Add saves it for later; " +
                                    "Play starts it now through the torrent engine."
                            )
                        } else if (addStream) {
                            tr(
                                "Paste a link to a stream: an m3u8 or mp4, a Terabox/Telebox " +
                                    "share, an MDisk link, or a download page — anything an " +
                                    "installed extension can open. It is resolved when you " +
                                    "press play."
                            )
                        } else if (addXtream) {
                            tr(
                                "Enter your Xtream panel's host URL plus the username and " +
                                    "password the panel gave you. The app logs in to the " +
                                    "panel (player_api.php) before saving, so a wrong " +
                                    "password or a dead host is reported here instead of " +
                                    "becoming an empty tile."
                            )
                        } else {
                            tr(
                                "Paste an M3U playlist link — an Xtream panel's " +
                                    "get.php?username=…&password=…&type=m3u_plus link works — " +
                                    "or a JSON playlist link (a raw .json channel list, " +
                                    "plain or encrypted). " +
                                    "Or pick a playlist file from storage. A single video " +
                                    "link (m3u8, mp4, and friends) is not a playlist — add it with " +
                                    "Network stream instead so it plays directly. A Stremio " +
                                    "manifest.json link is not a playlist either — add that in " +
                                    "Extensions → Add Stremio."
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    }
                    Spacer(Modifier.height(8.dp))
                    // The plain link field is for playlist / network stream /
                    // torrent modes; the Xtream fields below take over when that
                    // pill is selected, so hide this one to avoid two URL inputs.
                    if (!addXtream) {
                        OutlinedTextField(
                            value = addLink,
                            onValueChange = { addLink = it },
                            placeholder = {
                                Text(
                                    if (addStream) tr("https://…/m3u8, terabox, mdisk…")
                                    else tr("https://…/playlist.m3u or .json")
                                )
                            },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().tvTextFieldKeys(addLink),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // F3 — Xtream panel inputs (URL, username, password) shown when the
                    // panel pill is selected instead of a raw playlist link.
                    if (addXtream) {
                        OutlinedTextField(
                            value = addXtreamUrl,
                            onValueChange = { addXtreamUrl = it; xtreamError = "" },
                            label = { Text(tr("Panel URL")) },
                            singleLine = true,
                            isError = xtreamError.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().tvTextFieldKeys(addXtreamUrl),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = addXtreamUser,
                            onValueChange = { addXtreamUser = it; xtreamError = "" },
                            label = { Text(tr("Username")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().tvTextFieldKeys(addXtreamUser),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = addXtreamPass,
                            onValueChange = { addXtreamPass = it; xtreamError = "" },
                            label = { Text(tr("Password")) },
                            singleLine = true,
                            isError = xtreamError.isNotBlank(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().tvTextFieldKeys(addXtreamPass),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                enabled = addXtreamUrl.isNotBlank() &&
                                    addXtreamUser.isNotBlank() &&
                                    addXtreamPass.isNotBlank() && !xtreamTesting,
                                onClick = {
                                    val creds = com.hikari.app.data.XtreamCredentials(
                                        baseUrl = addXtreamUrl.trim(),
                                        username = addXtreamUser.trim(),
                                        password = addXtreamPass.trim(),
                                    )
                                    val credError = creds.validate()
                                    if (credError != null) {
                                        xtreamError = credError
                                    } else {
                                        xtreamError = ""
                                        xtreamTesting = true
                                        scope.launch {
                                            IptvProvider.validateXtream(creds).fold(
                                                onSuccess = {
                                                    addLink = creds.m3uUrl()
                                                    xtreamTestResult = I18n.t("Login OK — the panel is reachable")
                                                    xtreamTesting = false
                                                },
                                                onFailure = { t ->
                                                    xtreamError = t.message
                                                        ?: I18n.t("Could not reach that panel")
                                                    xtreamTesting = false
                                                },
                                            )
                                        }
                                    }
                                },
                            ) {
                                if (xtreamTesting) {
                                    CircularProgressIndicator(
                                        Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(tr("Test / Fill"))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (xtreamTesting) {
                                Text(
                                    tr("Signing in…"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (xtreamTestResult.isNotBlank()) {
                                Text(
                                    xtreamTestResult,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (xtreamError.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                xtreamError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!addStream && !addXtream) Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Pick a file"))
                        }
                        if (addFileLabel.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                addFileLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = {
                            Text(if (addStream) tr("Name") else tr("Name (optional)"))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().tvTextFieldKeys(addName),
                    )
                    if (adding) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (addStream) tr("Adding…") else tr("Reading playlist…"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (addStream && addLink.isNotBlank()) {
                        TextButton(
                            enabled = !adding,
                            onClick = {
                                // Play now: save then navigate into the stream card
                                addNow(playAfter = true)
                            },
                        ) { Text(tr("Play")) }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        enabled = !adding && (
                            addLink.isNotBlank() ||
                            addFilePath.isNotBlank() ||
                            (addXtream && addXtreamUrl.isNotBlank() &&
                                addXtreamUser.isNotBlank() && addXtreamPass.isNotBlank())
                        ),
                        onClick = { addNow(playAfter = false) },
                    ) { Text(tr("Add")) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    addStream = false; addTorrent = false; addXtream = false
                    addLink = ""; addName = ""; addFileLabel = ""; addFilePath = ""
                    addXtreamUrl = ""; addXtreamUser = ""; addXtreamPass = ""
                    xtreamError = ""; xtreamTestResult = ""; xtreamTesting = false
                    showAdd = false
                }) { Text(tr("Cancel")) }
            },
        )
    }

    // Removing a playlist is destructive in two ways (its channels leave Home
    // and search, and a stored copy is deleted), so it is confirmed here rather
    // than done on the tap.
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(I18n.t("Remove %s?").replace("%s", target.name)) },
            text = {
                Column {
                    Text(
                        tr(
                            "Its channels disappear from Home, search and the player's " +
                                "server list."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (target.local) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr("The playlist file stored in the app is deleted too."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    removePlaylist(target)
                    removeTarget = null
                }) {
                    Text(tr("Remove"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(tr("Cancel")) }
            },
        )
    }
}

/**
 * Copies a picked playlist file into the app's own storage, because a SAF Uri
 * is not readable after a restart and a playlist the user chose should keep
 * working. Returns (display name, stored path), or null when the file could not
 * be read — the same rule the Extensions screen's picker follows, kept here so
 * the IPTV tab can add a playlist without leaving the tab.
 */
private suspend fun copyPlaylistInto(
    context: android.content.Context,
    uri: Uri,
): Pair<String, String>? = withContext(Dispatchers.IO) {
    runCatching {
        val raw = runCatching {
            context.contentResolver
                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
        val name = raw?.trim().orEmpty().ifBlank { "playlist.m3u" }
        val safe = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").takeLast(80)
        val dir = File(context.filesDir, "iptv").apply { mkdirs() }
        val file = File(dir, safe)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        } ?: throw Exception("Could not read that file")
        if (file.length() == 0L) throw Exception("That file is empty")
        name to file.absolutePath
    }.getOrNull()
}

/**
 * Adds (or updates) an IPTV playlist from the tab's own + button. [link] is the
 * pasted M3U/M3U8/Xtream URL and [localPath] the stored copy of a picked file —
 * one of the two. The playlist is READ before it is saved, so a dead link or a
 * file with no channels is reported instead of becoming an empty tile, and the
 * id is derived from the source so adding the same link twice updates the one
 * entry (exactly what the Extensions screen does, see its addIptvPlaylist).
 */
private suspend fun addIptvPlaylist(
    app: HikariApp,
    link: String,
    localPath: String?,
    name: String,
): Result<Int> = withContext(Dispatchers.IO) {
    val url = when {
        !localPath.isNullOrBlank() -> localPath
        // A bare info hash is a torrent, not a host: keep it verbatim so the
        // torrent guard below sees it instead of an "https://" lookalike.
        NetworkStream.isBareInfoHash(link.trim()) -> link.trim()
        else -> link.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else if (it.isBlank()) "" else "https://$it"
        }
    }
    if (url.isBlank()) {
        return@withContext Result.failure(
            Exception(I18n.t("Paste an M3U/M3U8 link, or pick a playlist file")),
        )
    }
    // A torrent is not a playlist: saving one here lists it as an IPTV folder
    // that plays as live TV. Tor mode is the path that engines it.
    if (localPath.isNullOrBlank() && NetworkStream.isTorrentLink(url)) {
        return@withContext Result.failure(
            Exception(I18n.t("That looks like a torrent, not a playlist — add it with Tor instead")),
        )
    }
    // A single video/stream address is not a playlist: downloading it here would fetch
    // video bytes as playlist text, and saving it would list it as a one-channel
    // IPTV folder (All channels into Ungrouped) that plays as live TV. Network stream
    // mode is the path that resolves such a link at play time and plays it as a file.
    if (localPath.isNullOrBlank() && NetworkStream.isDirectMediaLink(url)) {
        return@withContext Result.failure(
            Exception(I18n.t("That looks like a single video link, not a playlist — add it with Network stream instead")),
        )
    }
    val count = IptvProvider.preview(url).getOrElse {
        // A playlist CONFIG (TMDB key, no channels): save its key instead of
        // listing an empty tile.
        val (cfgKey, cfgLabel) = IptvProvider.readPlaylistConfigKey(url)
        if (cfgKey != null) {
            if (runCatching { app.store.tmdbApiKey() }.getOrDefault("").isBlank()) {
                runCatching { app.store.setTmdbApiKey(cfgKey) }
            }
            return@withContext Result.failure(
                Exception(
                    "“${cfgLabel ?: "That file"}” is a playlist config, not channels — " +
                        "its TMDB key was saved to Settings. It holds nothing to list."
                ),
            )
        }
        return@withContext Result.failure(
            Exception(it.message ?: I18n.t("Could not read that playlist")),
        )
    }
    val display = name.trim().ifBlank {
        if (url.startsWith("http")) {
            url.substringAfter("://").substringBefore('/').ifBlank { "IPTV" }
        } else {
            File(url).name.substringBeforeLast('.').ifBlank { "IPTV" }
        }
    }
    app.store.addProvider(
        ProviderConfig(
            id = "iptv|" + url.hashCode(),
            name = display,
            type = ProviderType.IPTV,
            url = url,
        )
    )
    app.providers.refresh()
    Result.success(count)
}

/**
 * Adds a NETWORK STREAM: one pasted link, kept exactly as the user wrote it and
 * resolved when it is played (see [NetworkStream]).
 *
 * Deliberately NOT read before it is saved, unlike [addIptvPlaylist]. There is
 * nothing to count — a Terabox share or an m3u8 is not a channel list — and
 * fetching it here would both make adding slow and fail honest links that the
 * app can only work out at play time (the host needs the player's own headers,
 * or asks for a session the resolver sets up then). The entry is saved with
 * [NetworkStream.MARKER] on its config, which is what makes the tab, the tile
 * and the play path treat it as a stream rather than a one-channel playlist.
 */
private suspend fun addNetworkStream(
    app: HikariApp,
    link: String,
    name: String,
): Result<Int> = withContext(Dispatchers.IO) {
    val trimmed = link.trim()
    // A magnet link is complete as-is: prefixing "https://" (as before) mangles
    // it into an unplayable https URL that then lists and plays like an IPTV
    // channel instead of going to the torrent engine. A bare info hash is a
    // magnet with no scheme yet (see [NetworkStream.magnetize]).
    val url = if (trimmed.startsWith("magnet:", ignoreCase = true)) {
        trimmed
    } else if (NetworkStream.isBareInfoHash(trimmed)) {
        NetworkStream.magnetize(trimmed)
    } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else if (trimmed.isBlank()) {
        ""
    } else {
        "https://$trimmed"
    }
    if (url.isBlank()) {
        return@withContext Result.failure(Exception(I18n.t("Paste a link to the stream")))
    }
    if (!NetworkStream.isStreamLink(url)) {
        return@withContext Result.failure(
            Exception(I18n.t("That is not a link — paste a full http(s) URL or magnet link")),
        )
    }
    // A stream's name cannot come from the link (a share key is not a name), so
    // the host stands in when the user did not type one.
    val display = name.trim().ifBlank {
        when {
            url.startsWith("magnet:", ignoreCase = true) -> torrentDisplayName(url)
            NetworkStream.isTorrentFile(url) ->
                url.substringBefore('?').trimEnd('/').substringAfterLast('/')
                    .substringBeforeLast('.').ifBlank { "Torrent" }
            else -> NetworkStream.hostOf(url).removePrefix("www.").substringBefore('.').ifBlank { "Stream" }
        }
    }
    app.store.addProvider(
        ProviderConfig(
            id = "iptv|" + url.hashCode(),
            name = display,
            type = ProviderType.IPTV,
            url = url,
            extra = NetworkStream.MARKER,
        )
    )
    app.providers.refresh()
    Result.success(1)
}

/** Display name for a pasted torrent: the magnet's own dn= name, the
 *  .torrent file's name, or Torrent + short hash — never a URL shard like
 *  "magnet:". */
private fun torrentDisplayName(link: String): String {
    if (NetworkStream.isBareInfoHash(link)) {
        return "Torrent · " + link.take(8).uppercase()
    }
    Regex("[?&]dn=([^&]+)").find(link)?.let { m ->
        runCatching { java.net.URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull()
            ?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    if (!link.startsWith("magnet:", ignoreCase = true)) {
        link.substringBefore('?').trimEnd('/').substringAfterLast('/')
            .substringBeforeLast('.').ifBlank { null }?.let { return it }
    }
    val hash = Regex("btih:([a-zA-Z0-9]+)").find(link)?.groupValues?.getOrNull(1)
    return if (hash.isNullOrBlank()) "Torrent" else "Torrent · " + hash.take(8).uppercase()
}

/** One of the add dialog's two modes: Playlist, or a single Network stream. */
@Composable
private fun AddModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    // Television pills sit black with a hairline edge (desktop language).
    val tvPill = com.hikari.app.tv.TvMode.current()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else if (tvPill) com.hikari.app.tv.TvDesktop.SurfaceCard
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (!selected && tvPill) androidx.compose.foundation.BorderStroke(
            1.dp,
            com.hikari.app.tv.TvDesktop.GlassEdge,
        ) else null,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else if (tvPill) com.hikari.app.tv.TvDesktop.TextMuted
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * One playlist's own page: its groups as tiles, exactly the way a personal
 * catalog shows its folders. A tap opens that group's channels in the paged
 * catalog grid (which is how every other catalog in the app is browsed).
 */
@Composable
fun IptvPlaylistScreen(nav: NavHostController, providerId: String) {
    val app = LocalContext.current.applicationContext as HikariApp
    val all by app.providers.providers.collectAsState()
    val playlist = remember(all, providerId) {
        all.filterIsInstance<IptvProvider>().firstOrNull { it.config.id == providerId }
    }
    val shapeFlow = remember { app.store.iptvShapeFlow() }
    val shape by shapeFlow.collectAsState(initial = TileShapes.POSTER)
    val groupModeFlow = remember { app.store.iptvGroupModeFlow() }
    val groupMode by groupModeFlow.collectAsState(initial = "groups")
    val scope = rememberCoroutineScope()

    var groups by remember(providerId) { mutableStateOf<List<IptvGroupTile>?>(null) }
    /** F2 — loaded channel list, used for validation and health-dot computation. */
    var channels by remember(providerId) { mutableStateOf<List<com.hikari.app.data.IptvChannel>?>(null) }
    /** F2 — true while a Validate probe is in flight. */
    var validating by remember { mutableStateOf(false) }
    /** F2 — a version counter that recomposes the screen when validation completes. */
    var validateVersion by remember { mutableStateOf(0) }
    LaunchedEffect(providerId, playlist, groupMode) {
        if (playlist == null) {
            groups = emptyList()
            channels = emptyList()
            return@LaunchedEffect
        }
        groups = withContext(Dispatchers.IO) { readGroups(playlist, groupMode) }
        channels = withContext(Dispatchers.IO) { playlist.channels() }
    }
    /** F2 — probe every channel URL in the current playlist and cache results. */
    fun validate() {
        val chs = channels
        if (chs == null || chs.isEmpty()) return
        validating = true
        scope.launch {
            val sem = Semaphore(8)
            try {
                coroutineScope {
                    chs.map { c ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                if (c.url.startsWith("http")) {
                                    IptvProbeCache.put(c.url, com.hikari.app.net.StreamProbe.probe(c.url))
                                }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                validating = false
                validateVersion++
            }
        }
    }
    val isTorrent = playlist != null && NetworkStream.isTorrentLink(playlist.config.url)

    // ---- In-folder search -------------------------------------------------
    //
    // The header's search icon used to jump to the global Search tab scoped to
    // this playlist — leaving the folder to search it. It now searches right
    // here: the field filters this playlist's channels in place (same backend
    // as the scoped search, `IptvProvider.search`), and a tap plays from the
    // results without ever leaving the folder.
    var folderSearchOpen by remember(providerId) { mutableStateOf(false) }
    var folderQuery by remember(providerId) { mutableStateOf("") }
    var folderResults by remember(providerId) { mutableStateOf<List<MediaItem>?>(null) }
    var folderSearching by remember(providerId) { mutableStateOf(false) }
    var folderPage by remember(providerId) { mutableStateOf(1) }
    var folderHasMore by remember(providerId) { mutableStateOf(false) }
    LaunchedEffect(providerId, playlist, folderQuery) {
        val p = playlist
        val q = folderQuery.trim()
        folderPage = 1
        if (p == null || q.length < 2) {
            folderResults = null
            folderSearching = false
            folderHasMore = false
            return@LaunchedEffect
        }
        folderSearching = true
        delay(400)
        val first = withContext(Dispatchers.IO) {
            runCatching { p.search(q, 1) }.getOrDefault(emptyList())
        }
        // A newer keystroke superseded this run (query changed mid-flight).
        if (folderQuery.trim() != q) return@LaunchedEffect
        folderResults = first
        folderHasMore = first.size >= 240
        folderSearching = false
    }
    fun folderMore() {
        val p = playlist ?: return
        val q = folderQuery.trim()
        if (q.length < 2 || folderSearching) return
        val next = folderPage + 1
        folderSearching = true
        scope.launch {
            val more = withContext(Dispatchers.IO) {
                runCatching { p.search(q, next) }.getOrDefault(emptyList())
            }
            if (folderQuery.trim() != q) {
                folderSearching = false
                return@launch
            }
            folderResults = (folderResults.orEmpty() + more).distinctBy { it.id }
            folderPage = next
            folderHasMore = more.size >= 240
            folderSearching = false
        }
    }

    val loaded = groups
    Column(Modifier.fillMaxSize()) {
        IptvHeader(
            title = playlist?.displayName ?: tr("IPTV"),
            subtitle = when {
                loaded == null -> tr("Reading the playlist…")
                else -> I18n.t("%s channels in %s %s")
                    .replaceFirst("%s", loaded.sumOf { it.count }.toString())
                    .replaceFirst("%s", loaded.size.toString())
                    .replaceFirst("%s", when (IptvPlaylist.normalizeGroupMode(groupMode)) {
                        "language" -> tr("languages")
                        "category" -> tr("categories")
                        "country" -> tr("countries")
                        else -> tr("groups")
                    })
            },
            onBack = { nav.popBackStack() },
            shape = shape,
            onCycleShape = { scope.launch { app.store.setIptvShape(nextShape(shape)) } },
            onSearch = playlist?.let {
                {
                    folderSearchOpen = !folderSearchOpen
                    if (!folderSearchOpen) folderQuery = ""
                }
            },
            onAdd = null,
            onValidate = { validate() },
            validating = validating,
        )
        if (folderSearchOpen && !isTorrent) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = folderQuery,
                    onValueChange = { folderQuery = it },
                    label = { Text(tr("Search channels")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).tvTextFieldKeys(folderQuery),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    folderSearchOpen = false
                    folderQuery = ""
                }) { Text(tr("Close")) }
            }
        }
        if (!isTorrent) {
            com.hikari.app.ui.components.ChoiceRow(
                value = tr("Grouped by: %s").replace("%s", when (IptvPlaylist.normalizeGroupMode(groupMode)) {
                    "language" -> tr("Language")
                    "category" -> tr("Category")
                    "country" -> tr("Country")
                    else -> tr("Groups")
                }),
                supporting = tr("Group this playlist by its sections, language, category or country"),
                leadingIcon = Icons.Filled.FolderOpen,
                onClick = {
                    val next = when (IptvPlaylist.normalizeGroupMode(groupMode)) {
                        "groups" -> "language"
                        "language" -> "category"
                        "category" -> "country"
                        else -> "groups"
                    }
                    scope.launch { app.store.setIptvGroupMode(next) }
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (loaded == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        // In-folder search results replace the group grid while a query is
        // typed — the folder is never left to search it.
        if (folderSearchOpen && !isTorrent && folderQuery.trim().length >= 2) {
            val res = folderResults
            LazyColumn(
                // In-folder search position held per query (see ScrollKeeper).
                state = com.hikari.app.ui.components.keptListState("iptvSearch:$providerId:$folderQuery"),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = LocalTaskbarInset.current + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (res == null || (folderSearching && res.isEmpty())) {
                    item(key = "iptv-search-loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (!folderSearching && res != null && res.isEmpty()) {
                    item(key = "iptv-search-empty") {
                        Text(
                            tr("No channels match that search."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(res.orEmpty(), key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                Routes.safeNavigate(
                                    nav,
                                    Routes.detail(
                                        providerId,
                                        item.type,
                                        item.id,
                                        item.title,
                                        item.posterUrl,
                                        item.rawType,
                                    ),
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.overview?.isNotBlank() == true) {
                                Text(
                                    item.overview.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (folderHasMore) {
                    item(key = "iptv-search-more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(
                                onClick = { folderMore() },
                                enabled = !folderSearching,
                            ) {
                                if (folderSearching) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(tr("Show more"))
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }
        if (isTorrent && playlist != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = tileMinFor(shape)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = LocalTaskbarInset.current + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    IptvTile(
                        cover = null,
                        name = playlist.displayName,
                        subtitle = tr("Torrent — plays directly"),
                        shape = shape,
                    ) {
                        Routes.safeNavigate(
                            nav,
                            Routes.catalog(
                                providerId = providerId,
                                catalogId = IptvProvider.CATALOG_ALL,
                                title = playlist.displayName,
                                providerName = playlist.displayName,
                                type = MediaType.MOVIE,
                                rawType = "torrent",
                            ),
                        )
                    }
                }
            }
            return@Column
        }
        val name = playlist?.displayName ?: providerId
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = tileMinFor(shape)),
            // Group grid position held per playlist (see ScrollKeeper): Back
            // from a channel lands where the grid was.
            state = com.hikari.app.ui.components.keptGridState(
                "iptvGroups:$providerId",
                itemKeys = remember(loaded) { loaded.map { it.catalogId } },
            ),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = LocalTaskbarInset.current + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(loaded, key = { it.catalogId }) { g ->
                // Drawn off the main thread: a group has no artwork of its own,
                // so its tile is generated from its name (once — see IptvArt).
                val art by produceState<String?>(initialValue = null, providerId, g.catalogId) {
                    value = withContext(Dispatchers.IO) {
                        IptvArt.tile(
                            MediaItem(
                                providerId = providerId,
                                id = g.catalogId,
                                title = g.name,
                                type = MediaType.MOVIE,
                                overview = name,
                            )
                        )
                    }
                }
                // F2 — compute health from the current channels
                val groupChannels = remember(channels, validateVersion, g.catalogId) {
                    channels.orEmpty().filter { IptvPlaylist.groupOf(it) == g.name }
                }
                val health = remember(groupChannels, validateVersion) {
                    healthOf(groupChannels)
                }
                IptvTile(
                    cover = null,
                    art = art,
                    name = g.name,
                    subtitle = I18n.t("%s channels").replace("%s", g.count.toString()),
                    shape = shape,
                    health = health,
                ) {
                    Routes.safeNavigate(
                        nav,
                        Routes.catalog(
                            providerId = providerId,
                            catalogId = g.catalogId,
                            title = g.name,
                            providerName = name,
                            type = MediaType.MOVIE,
                            rawType = "channel",
                        ),
                    )
                }
            }
        }
    }
}

/** One tile in the merged "View All Playlists" grid: a group from one playlist. */
private data class AllPlaylistTile(
    val provider: IptvProvider,
    val name: String,
    val catalogId: String,
    val count: Int,
    val key: String,
)

/**
 * F4 — "View All Playlists": one grouped grid that merges EVERY IPTV
 * playlist's contents. Each tile is a (playlist, group) pair; group tiles
 * route to that playlist's own catalog, so the paged channel grid and its
 * search/validation stay exactly the same code path Home uses.
 */
@Composable
fun IptvAllPlaylistsScreen(nav: NavHostController) {
    val app = LocalContext.current.applicationContext as HikariApp
    val all by app.providers.providers.collectAsState()
    val playlists = remember(all) { all.filterIsInstance<IptvProvider>() }
    val shapeFlow = remember { app.store.iptvShapeFlow() }
    val shape by shapeFlow.collectAsState(initial = TileShapes.POSTER)
    val groupModeFlow = remember { app.store.iptvGroupModeFlow() }
    val groupMode by groupModeFlow.collectAsState(initial = "groups")
    val scope = rememberCoroutineScope()

    var tiles by remember { mutableStateOf<List<AllPlaylistTile>?>(null) }
    LaunchedEffect(playlists, groupMode) {
        val m = IptvPlaylist.normalizeGroupMode(groupMode)
        tiles = withContext(Dispatchers.IO) {
            val out = ArrayList<AllPlaylistTile>()
            playlists.chunked(3).forEach { chunk ->
                chunk.forEach { p ->
                    val pid = p.config.id
                    val channels = runCatching {
                        withTimeoutOrNull(60_000L) { p.channels() }
                    }.getOrNull().orEmpty()
                    if (channels.isEmpty()) return@forEach
                    out += AllPlaylistTile(
                        provider = p,
                        name = I18n.t("All channels"),
                        catalogId = IptvProvider.CATALOG_ALL,
                        count = channels.size,
                        key = "$pid|all",
                    )
                    channels.groupBy { IptvPlaylist.groupKey(it, m) }
                        .entries
                        .sortedWith(
                            compareByDescending<Map.Entry<String, List<com.hikari.app.data.IptvChannel>>> { it.value.size }
                                .thenBy { it.key.lowercase() },
                        )
                        .forEach { (group, chs) ->
                            out += AllPlaylistTile(
                                provider = p,
                                name = group,
                                catalogId = IptvProvider.catalogIdForGroup(group),
                                count = chs.size,
                                key = "$pid|$group",
                            )
                        }
                }
            }
            out
        }
    }

    val totalChannels = remember(tiles) { tiles.orEmpty().sumOf { it.count } }
    Column(Modifier.fillMaxSize()) {
        IptvHeader(
            title = tr("View All Playlists"),
            subtitle = when {
                tiles == null -> tr("Reading the playlist…")
                playlists.isEmpty() -> tr("No playlists yet")
                else -> I18n.t("%s playlists · %s channels")
                    .replace("%s", playlists.size.toString())
                    .replace("%s", totalChannels.toString())
            },
            onBack = { nav.popBackStack() },
            shape = shape,
            onCycleShape = { scope.launch { app.store.setIptvShape(nextShape(shape)) } },
            onSearch = null,
            onAdd = null,
        )
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    tr("Add an IPTV playlist and its channels appear here, grouped with every other playlist's."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
            return@Column
        }
        val list = tiles ?: return@Column
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = tileMinFor(shape)),
            state = com.hikari.app.ui.components.keptGridState(
                "iptvAllPlaylists:$groupMode",
                itemKeys = remember(list) { list.map { it.key } },
            ),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = LocalTaskbarInset.current + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.key }) { tile ->
                IptvTile(
                    cover = null,
                    name = tile.name,
                    subtitle = I18n.t("%s · %s channels")
                        .replace("%s", tile.provider.displayName)
                        .replace("%s", tile.count.toString()),
                    shape = shape,
                ) {
                    Routes.safeNavigate(
                        nav,
                        Routes.catalog(
                            providerId = tile.provider.config.id,
                            catalogId = tile.catalogId,
                            title = tile.name,
                            providerName = tile.provider.displayName,
                            type = MediaType.MOVIE,
                            rawType = "channel",
                        ),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ building --

/**
 * F2 — cache of probe results per channel URL, with a 5-minute TTL so re-opening
 * the app or re-tapping Validate doesn't hammer the provider panel. Stored
 * outside IptvChannel (which is immutable once parsed) so results survive
 * recompositions and channel-list re-reads.
 */
internal object IptvProbeCache {
    private val cache = mutableMapOf<String, com.hikari.app.net.StreamProbe.ProbeResult?>()
    private val timestamps = mutableMapOf<String, Long>()
    private const val TTL_MS = 5 * 60 * 1000L

    fun put(url: String, result: com.hikari.app.net.StreamProbe.ProbeResult?) {
        cache[url] = result
        timestamps[url] = System.currentTimeMillis()
    }

    fun get(url: String): com.hikari.app.net.StreamProbe.ProbeResult? {
        val ts = timestamps[url] ?: return null
        if (System.currentTimeMillis() - ts > TTL_MS) {
            cache.remove(url)
            timestamps.remove(url)
            return null
        }
        return cache[url]
    }

    fun clear() {
        cache.clear()
        timestamps.clear()
    }
}

/** F2 — aggregate health of a group's channels, drawn as a colored dot. */
internal enum class HealthDot { ALL_ALIVE, MIXED, ALL_DEAD, NONE }

/**
 * Computes the aggregate health of [channels] from the probe cache: green when
 * every probed channel answered, red when every probed channel failed, yellow
 * when results are mixed. NONE means nothing has been probed yet.
 */
private fun healthOf(channels: List<com.hikari.app.data.IptvChannel>): HealthDot {
    if (channels.isEmpty()) return HealthDot.NONE
    val results = channels.mapNotNull { IptvProbeCache.get(it.url) }
    if (results.isEmpty()) return HealthDot.NONE
    val alive = results.count { it.isAlive }
    val dead = results.size - alive
    return when {
        dead == 0 -> HealthDot.ALL_ALIVE
        alive == 0 -> HealthDot.ALL_DEAD
        else -> HealthDot.MIXED
    }
}

/** A playlist as the tab draws it. */
private data class IptvCard(
    val id: String,
    val name: String,
    /** The playlist/stream link itself: a network stream opens straight on its
     *  detail page, which plays this URL (no folders in between). */
    val url: String = "",
    val channels: Int,
    val groups: Int,
    val cover: String?,
    val error: String?,
    /** True for a link the user added as a single NETWORK STREAM rather than a
     *  playlist: one channel, and no groups to count. */
    val stream: Boolean = false,
    /** True when that single stream is a torrent (magnet/.torrent): shown and
     *  played as a torrent, never grouped or badged like IPTV. */
    val torrent: Boolean = false,
    /** True when the playlist is the app's own copy of a file the user picked
     *  from storage (under `filesDir/iptv/`) rather than a pasted link — the one
     *  case where removing the tile also deletes something. */
    val local: Boolean = false,
)

/** One group inside a playlist. */
private data class IptvGroupTile(
    val name: String,
    val catalogId: String,
    val count: Int,
)

/** Reads one playlist (its channel count, its groups, and a logo to wear). */
private suspend fun readCard(p: IptvProvider): IptvCard {
    val list = runCatching {
        withTimeoutOrNull(60_000L) { p.channels() }
    }.getOrNull().orEmpty()
    return IptvCard(
        id = p.config.id,
        name = p.displayName,
        url = p.config.url,
        channels = list.size,
        groups = list.map { IptvPlaylist.groupOf(it) }.distinct().size,
        // The first channel that really has a logo: a playlist tile wearing one
        // of its own channel logos reads as "this is what is inside" far better
        // than a generic glyph.
        cover = list.firstOrNull { !it.logo.isNullOrBlank() }?.logo,
        error = IptvProvider.iptvErrors[p.config.id],
        stream = NetworkStream.isStream(p.config),
        torrent = NetworkStream.isTorrentLink(p.config.url),
        local = !p.config.url.startsWith("http"),
    )
}

/**
 * A playlist's groups, biggest first, with "All channels" as the first tile.
 *
 * The ids are the provider's own catalog ids ([IptvProvider.CATALOG_ALL] and
 * [IptvProvider.catalogIdForGroup]), so a tile links straight to the same paged
 * catalog the provider would hand Home — no second code path to keep in step.
 */
private suspend fun readGroups(p: IptvProvider, mode: String): List<IptvGroupTile> {
    val list = runCatching {
        withTimeoutOrNull(60_000L) { p.channels() }
    }.getOrNull().orEmpty()
    if (list.isEmpty()) return emptyList()
    val m = IptvPlaylist.normalizeGroupMode(mode)
    val out = ArrayList<IptvGroupTile>()
    if (m == "groups") {
        out += IptvGroupTile(
            name = I18n.t("All channels"),
            catalogId = IptvProvider.CATALOG_ALL,
            count = list.size,
        )
    }
    list.groupBy { IptvPlaylist.groupKey(it, m) }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<com.hikari.app.data.IptvChannel>>> { it.value.size }
                .thenBy { it.key.lowercase() },
        )
        .forEach { (group, channels) ->
            out += IptvGroupTile(
                name = group,
                catalogId = when (m) {
                    "language" -> IptvProvider.catalogIdForLanguage(group)
                    "category" -> IptvProvider.catalogIdForCategory(group)
                    "country" -> IptvProvider.catalogIdForCountry(group)
                    else -> IptvProvider.catalogIdForGroup(group)
                },
                count = channels.size,
            )
        }
    return out
}

// -------------------------------------------------------------------- pieces --

/** The tab's own title bar (no back button on the root tab). */
/** The three swipeable pages of the IPTV tab — the My Stuff pattern:
 *  pills up top, pages below, one shared state in both directions. */
private object IptvSection {
    const val PLAYLIST = "playlist"
    const val LINKS = "links"  // Combined torrent + network stream
}

/** The pill strip above the pages (IPTV | Tor | Network stream). */
@Composable
private fun IptvSectionStrip(sections: List<String>, current: String, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sections.forEach { section ->
            IptvSectionPill(
                label = when (section) {
                    IptvSection.LINKS -> tr("Links")
                    else -> tr("IPTV")
                },
                icon = when (section) {
                    IptvSection.LINKS -> Icons.Filled.Link
                    else -> Icons.Filled.LiveTv
                },
                selected = current == section,
                modifier = Modifier.weight(1f),
            ) { onPick(section) }
        }
    }
}

/** One section button of the strip (selected = primary pill, else surface). */
@Composable
private fun IptvSectionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Television pills sit black with a hairline edge (see [AddModePill]).
    val tvPill = com.hikari.app.tv.TvMode.current()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else if (tvPill) com.hikari.app.tv.TvDesktop.SurfaceCard
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (!selected && tvPill) androidx.compose.foundation.BorderStroke(
            1.dp,
            com.hikari.app.tv.TvDesktop.GlassEdge,
        ) else null,
        modifier = modifier.height(38.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else if (tvPill) com.hikari.app.tv.TvDesktop.TextMuted
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else if (tvPill) com.hikari.app.tv.TvDesktop.TextMuted
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One IPTV section page: its own header (own title/counts, own tile-shape
 * button, own + button opening the add dialog preset to this kind) above its
 * own grid. Each page keeps its own shape and its own scroll position.
 */
@Composable
private fun IptvSectionPage(
    kind: String,
    playlistCards: List<IptvCard>,
    torCards: List<IptvCard>,
    streamCards: List<IptvCard>,
    playlistsEmpty: Boolean,
    reading: Boolean,
    playlistShape: String,
    torShape: String,
    streamShape: String,
    nav: NavHostController,
    onCyclePlaylist: () -> Unit,
    onCycleTor: () -> Unit,
    onCycleStream: () -> Unit,
    onAddPreset: (String) -> Unit,
    onRemove: (IptvCard) -> Unit,
    onValidateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    // Combine tor and stream cards for the LINKS section
    val allLinkCards = remember(torCards, streamCards) { torCards + streamCards }
    val pageCards = when (kind) {
        IptvSection.LINKS -> allLinkCards
        else -> playlistCards
    }
    val pageShape = when (kind) {
        IptvSection.LINKS -> torShape
        else -> playlistShape
    }
    Column(Modifier.fillMaxSize()) {
        IptvHeader(
            title = when (kind) {
                IptvSection.LINKS -> tr("Links")
                else -> tr("IPTV")
            },
            subtitle = when (kind) {
                IptvSection.LINKS -> if (allLinkCards.isEmpty()) tr("No links yet")
                else I18n.t("%s links").replace("%s", allLinkCards.size.toString())
                else -> if (playlistsEmpty) tr("No playlists yet")
                else I18n.t("%s playlists · %s channels")
                    .replace("%s", playlistCards.size.toString())
                    .replace("%s", playlistCards.sumOf { it.channels }.toString())
            },
            onBack = null,
            shape = pageShape,
            onCycleShape = when (kind) {
                IptvSection.LINKS -> onCycleTor
                else -> onCyclePlaylist
            },
            // A magnifier in the IPTV header jumps to the global Search tab,
            // scoped to IPTV — the same affordance Home's "Search this
            // extension" uses. Without it the only search on this page was
            // buried inside one playlist's own group list.
            onSearch = if (kind == IptvSection.PLAYLIST) {
                { Routes.safeNavigate(nav, Routes.iptvScopedSearchQuery()) }
            } else null,
            onAdd = { onAddPreset(kind) },
            // F2 — the Validate button. It only ever makes sense on the
            // playlist page (probing a single stream's URL is pointless), so
            // the other two pages pass null and the header skips the button.
            onValidate = if (kind == IptvSection.PLAYLIST) onValidateAll else null,
            validating = false,
            onRefresh = onRefresh,
        )
        IptvKindGrid(
            kind = kind,
            cards = pageCards,
            reading = reading,
            shape = pageShape,
            keeperKey = when (kind) {
                IptvSection.LINKS -> "iptv_links"
                else -> "iptv"
            },
            showOnboarding = kind == IptvSection.PLAYLIST && playlistsEmpty,
            emptyTitle = when (kind) {
                IptvSection.LINKS -> tr("No links yet")
                else -> tr("No IPTV playlist yet")
            },
            emptyBody = when (kind) {
                IptvSection.LINKS -> tr("Paste a magnet link, .torrent URL, or direct stream link (m3u8, mp4, Terabox, MDisk…) and it appears here as a tile.")
                else -> tr("Paste an M3U/M3U8 link (or pick a playlist file) and it appears here as a tile.")
            },
            emptyAction = when (kind) {
                IptvSection.LINKS -> tr("Add link")
                else -> tr("Add playlist")
            },
            onEmptyAdd = { onAddPreset(kind) },
            nav = nav,
            onRemove = onRemove,
        )
    }
}

/**
 * The body under one section's header: the onboarding card (playlist page with
 * nothing added yet), a spinner while the first read lands, an empty card, or
 * the tile grid. Tile taps route by kind exactly like the old single list.
 */
@Composable
private fun IptvKindGrid(
    kind: String,
    cards: List<IptvCard>,
    reading: Boolean,
    shape: String,
    keeperKey: String,
    showOnboarding: Boolean,
    emptyTitle: String,
    emptyBody: String,
    emptyAction: String,
    onEmptyAdd: () -> Unit,
    nav: NavHostController,
    onRemove: (IptvCard) -> Unit,
) {
    when {
        showOnboarding || (cards.isEmpty() && !reading) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    emptyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    emptyBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                )
                Surface(
                    onClick = onEmptyAdd,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(emptyAction)
                    }
                }
            }
        }

        cards.isEmpty() && reading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = tileMinFor(shape)),
            // Each page holds its own scroll position across visits (see
            // ScrollKeeper).
            state = com.hikari.app.ui.components.keptGridState(
                keeperKey,
                itemKeys = remember(cards) {
                    val allKey = if (kind == IptvSection.PLAYLIST && cards.size > 1) listOf("iptv-all-playlists") else emptyList()
                    allKey + cards.map { it.id }
                },
            ),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = LocalTaskbarInset.current + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // "View All Playlists" — a grouped tile that opens every IPTV
            // playlist's contents at once. Only when there is more than one
            // playlist to group; a single playlist's tile is already that.
            if (kind == IptvSection.PLAYLIST && cards.size > 1) {
                item(key = "iptv-all-playlists") {
                    val totalChannels = remember(cards) { cards.sumOf { it.channels } }
                    val totalGroups = remember(cards) { cards.sumOf { it.groups } }
                    IptvTile(
                        cover = null,
                        name = tr("View All Playlists"),
                        subtitle = I18n.t("%s playlists · %s channels · %s groups")
                            .replace("%s", cards.size.toString())
                            .replace("%s", totalChannels.toString())
                            .replace("%s", totalGroups.toString()),
                        shape = shape,
                        badge = null,
                        onRemove = null,
                    ) {
                        Routes.safeNavigate(nav, Routes.iptvAllPlaylists())
                    }
                }
            }
            items(cards, key = { it.id }) { card ->
                IptvTile(
                    cover = card.cover,
                    name = card.name,
                    subtitle = when {
                        card.error != null -> card.error
                        card.torrent -> tr("Torrent")
                        card.stream -> tr("Network stream")
                        card.channels == 0 -> tr("Empty playlist")
                        else -> I18n.t("%s channels · %s groups")
                            .replace("%s", card.channels.toString())
                            .replace("%s", card.groups.toString())
                    },
                    shape = shape,
                    badge = if (card.error != null) tr("Didn't load") else null,
                    onRemove = { onRemove(card) },
                ) {
                    if (card.torrent) {
                        Routes.safeNavigate(
                            nav,
                            Routes.catalog(
                                providerId = card.id,
                                catalogId = IptvProvider.CATALOG_ALL,
                                title = card.name,
                                providerName = card.name,
                                type = MediaType.MOVIE,
                                rawType = "torrent",
                            ),
                        )
                    } else if (card.stream && !card.torrent) {
                        // A network stream is one file, not a channel list: it opens
                        // straight on its detail page (Play), with no All-channels /
                        // Ungrouped folders in between — and plays as VOD, not live.
                        Routes.safeNavigate(
                            nav,
                            Routes.detail(
                                providerId = card.id,
                                type = MediaType.MOVIE,
                                mediaId = card.url,
                                title = card.name,
                                posterUrl = card.cover,
                                rawType = "stream",
                            ),
                        )
                    } else {
                        Routes.safeNavigate(nav, Routes.iptvPlaylist(card.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun IptvHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    shape: String,
    onCycleShape: () -> Unit,
    onSearch: (() -> Unit)?,
    onAdd: (() -> Unit)?,
    /** F2 — Validate button callback for probing this playlist's channels. */
    onValidate: (() -> Unit)? = null,
    /** F2 — true while a validation probe is in flight. */
    validating: Boolean = false,
    /** Refresh all IPTV playlists. */
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (com.hikari.app.tv.TvMode.current()) 20.dp else 6.dp,
                end = if (com.hikari.app.tv.TvMode.current()) 20.dp else 6.dp,
                top = if (com.hikari.app.tv.TvMode.current()) 12.dp else 6.dp,
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = tr("Back"),
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                tr(title),
                style = if (com.hikari.app.tv.TvMode.current()) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Shape: poster → square → wide → poster. One button, three looks, and
        // the choice is remembered (Settings-free: it belongs to this tab).
        IconButton(onClick = onCycleShape) {
            Icon(
                Icons.Filled.GridView,
                contentDescription = I18n.t("Tile shape: %s").replace("%s", shapeLabel(shape)),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = tr("Search these channels"))
            }
        }
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = tr("Add playlist"))
            }
        }
        if (onValidate != null) {
            IconButton(
                onClick = onValidate,
                enabled = !validating,
            ) {
                if (validating) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = tr("Validate channels"),
                    )
                }
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = tr("Refresh playlists"))
            }
        }
    }
}

/**
 * One tile of the IPTV tab: a cover (a real logo, a drawn channel tile, or the
 * playlist's own generated one) with the name under it, shaped by [shape].
 */
@Composable
private fun IptvTile(
    name: String,
    subtitle: String,
    shape: String,
    cover: String? = null,
    /** A `file://` URL of a locally drawn tile (see [IptvArt]) — used when there
     *  is no real artwork at all, which is the common case for a group. */
    art: String? = null,
    badge: String? = null,
    /** Shown as a trash button on the cover's top-left when non-null (the
     *  badge already owns the top-right). Only the tab's own tiles pass it —
     *  a group inside a playlist has nothing to delete. */
    onRemove: (() -> Unit)? = null,
    /** F2 — health dot drawn at the bottom-right of the cover: green all alive,
     *  yellow mixed, red all dead. NONE draws no dot. */
    health: HealthDot = HealthDot.NONE,
    onClick: () -> Unit,
) {
    val tokens = rememberGlassTokens()
    val model = cover ?: art
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.fillTop)
            .border(1.dp, tokens.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(7.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(TileShapes.aspect(shape))
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LiveTv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(26.dp),
            )
            AsyncImage(
                model = model,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            if (health != HealthDot.NONE) {
                val dotColor = when (health) {
                    HealthDot.ALL_ALIVE -> Color(0xFF4CAF50)
                    HealthDot.MIXED -> Color(0xFFFFC107)
                    HealthDot.ALL_DEAD -> Color(0xFFF44336)
                    HealthDot.NONE -> Color.Transparent
                }
                Surface(
                    color = dotColor,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(10.dp),
                ) {}
            }
            if (onRemove != null) {
                Surface(
                    onClick = onRemove,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = I18n.t("Remove %s").replace("%s", name),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(4.dp).size(15.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

/** The smallest a tile may be for [shape] — the grid's own column width. */
private fun tileMinFor(shape: String): Dp = when (TileShapes.normalize(shape)) {
    TileShapes.WIDE -> 168.dp
    TileShapes.SQUARE -> 118.dp
    else -> 104.dp
}

// -------------------------------------------------------------------- Quick Channels --

/**
 * Xtream saved accounts — shown at the top of the IPTV tab so the user can
 * jump to configured panels without hunting through Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XtreamAccountRow(app: HikariApp) {
    val scope = rememberCoroutineScope()
    val xtreamAccounts by app.store.xtreamAccountsFlow().collectAsState(initial = emptyList())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Saved Xtream accounts
        if (xtreamAccounts.isNotEmpty()) {
            Text(
                "Saved Xtream:  ",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(xtreamAccounts) { acc ->
                    XtreamAccountPill(
                        account = acc,
                        onClick = { /* TODO: open panel detail */ },
                    )
                }
            }
        }
    }
}

@Composable
private fun XtreamAccountPill(account: com.hikari.app.data.XtreamAccount, onClick: () -> Unit) {
    val expText = if (account.expDate != null && account.expDate > 0L) {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(account.expDate))
        "Exp: $d"
    } else null
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (account.isWorking) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.label.ifBlank { account.baseUrl },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (expText != null) {
                    Text(
                        expText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QuickChannelsSection(nav: NavHostController, playlists: List<IptvProvider>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheDir = remember { (ctx.applicationContext as? HikariApp)?.cacheDir ?: ctx.cacheDir }

    // Quick channel region tabs
    val qcTabs = listOf("All", "US", "UK", "CA", "Premium", "Sports", "News", "Custom")
    var selectedQcRegion by remember { mutableStateOf("All") }

    // Custom regions and channels state
    var customRegions by remember { mutableStateOf(emptyList<CustomRegion>()) }
    var customChannels by remember { mutableStateOf(emptyList<QuickChannel>()) }

    fun reloadCustomData() {
        customRegions = QuickChannelList.getCustomRegions(cacheDir)
        customChannels = QuickChannelList.getCustomChannels(cacheDir, emptyList())
    }

    LaunchedEffect(cacheDir) {
        reloadCustomData()
    }
    var showAddRegion by remember { mutableStateOf(false) }
    var showAddChannel by remember { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf<CustomRegion?>(null) }
    var showDeleteRegion by remember { mutableStateOf<CustomRegion?>(null) }
    var editingChannel by remember { mutableStateOf<QuickChannel?>(null) }
    var showDeleteChannel by remember { mutableStateOf<QuickChannel?>(null) }

    // Modal bottom sheet state
    var showSheet by remember { mutableStateOf(false) }
    var selectedQc by remember { mutableStateOf<QuickChannel?>(null) }
    var qcMatches by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var aliveByUrl by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var qcLoading by remember { mutableStateOf(false) }
    var qcValidating by remember { mutableStateOf(false) }
    var hideDead by remember { mutableStateOf(true) }
    var sheetExpanded by remember { mutableStateOf(false) }
    // Full playlist channels — fetched once, reused for playChannel
    var allChannels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }

    fun runQcValidation() {
        if (selectedQc == null || qcValidating) return
        scope.launch(Dispatchers.IO) {
            qcValidating = true
            val currentMatches = qcMatches.ifEmpty {
                com.hikari.app.providers.gatherIptvChannels(playlists).filter { ch ->
                    QuickChannelList.matches(selectedQc!!, ch)
                }
            }
            qcMatches = currentMatches
            validateChannelStatuses(currentMatches) { url, isAlive ->
                aliveByUrl = aliveByUrl + (url to isAlive)
            }
            qcValidating = false
        }
    }

    // Filtered quick channels — reactive to selected region, custom regions, and custom channels
    val filtered = remember(selectedQcRegion, customRegions, customChannels) {
        when (selectedQcRegion) {
            "All" -> (QuickChannelList.default + customChannels).distinctBy { it.displayName }
            "US" -> QuickChannelList.default.filter { "US" in it.regions } + customChannels.filter { c -> c.regions.any { it.equals("us", ignoreCase = true) } }
            "UK" -> QuickChannelList.default.filter { "UK" in it.regions } + customChannels.filter { c -> c.regions.any { it.equals("uk", ignoreCase = true) } }
            "CA" -> QuickChannelList.default.filter { "CA" in it.regions } + customChannels.filter { c -> c.regions.any { it.equals("ca", ignoreCase = true) } }
            "Premium" -> QuickChannelList.default.filter { "premium" in it.tags } + customChannels.filter { c -> c.tags.any { it.equals("premium", ignoreCase = true) } }
            "Sports" -> QuickChannelList.default.filter { "sports" in it.tags } + customChannels.filter { c -> c.tags.any { it.equals("sports", ignoreCase = true) } }
            "News" -> QuickChannelList.default.filter { "news" in it.tags } + customChannels.filter { c -> c.tags.any { it.equals("news", ignoreCase = true) } }
            "Custom" -> customChannels
            else -> {
                val reg = customRegions.firstOrNull { it.name == selectedQcRegion }
                val targetTag = reg?.tag ?: selectedQcRegion
                val customMatches = customChannels.filter { c -> c.regions.any { it.equals(targetTag, ignoreCase = true) || it.equals(selectedQcRegion, ignoreCase = true) } }
                val defaultMatches = QuickChannelList.default.filter { c -> c.regions.any { it.equals(targetTag, ignoreCase = true) || it.equals(selectedQcRegion, ignoreCase = true) } }
                (customMatches + defaultMatches).distinctBy { it.displayName }
            }
        }
    }
    LaunchedEffect(selectedQcRegion, customRegions) {
        reloadCustomData()
    }

    // Show sheet when a quick channel is selected
    LaunchedEffect(selectedQc) {
        val qc = selectedQc ?: return@LaunchedEffect
        showSheet = true
        qcLoading = true
        qcMatches = emptyList()
        aliveByUrl = emptyMap()

        scope.launch(Dispatchers.IO) {
            val fetched = com.hikari.app.providers.gatherIptvChannels(playlists)
            allChannels = fetched
            val matches = fetched.filter { ch ->
                QuickChannelList.matches(qc, ch)
            }
            qcMatches = matches
            qcLoading = false

            // Real network stream probe — progressive live updates
            validateChannelStatuses(matches) { url, isAlive ->
                aliveByUrl = aliveByUrl + (url to isAlive)
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // Section header — top-right: "+ Region" and "+ CH" buttons to add
        // custom region tabs and custom quick channels.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Quick Channels",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showAddRegion = true },
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White.copy(alpha = 0.85f),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Region", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { showAddChannel = true },
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White.copy(alpha = 0.85f),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CH", fontSize = 12.sp)
                }
                // Validate button — only shown when a quick channel is selected
                if (selectedQc != null) {
                    OutlinedButton(
                        onClick = { runQcValidation() },
                        enabled = !qcValidating,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.85f),
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        if (qcValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Validate", fontSize = 12.sp)
                    }
                }
            }
        }

        // Region tabs (horizontal scroll)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val allTabs = qcTabs + customRegions.map { it.name }
                items(allTabs) { tab ->
                    val isCustomRegion = customRegions.any { it.name == tab }
                    val isActive = selectedQcRegion == tab
                    val regionForTab = customRegions.firstOrNull { it.name == tab }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .then(
                                if (isCustomRegion) {
                                    Modifier.combinedClickable(
                                        onClick = { selectedQcRegion = tab },
                                        onLongClick = {
                                            regionForTab?.let {
                                                editingRegion = it
                                                showAddRegion = true
                                            }
                                        },
                                    )
                                } else {
                                    Modifier.clickable { selectedQcRegion = tab }
                                },
                            )
                            .padding(start = 12.dp, end = if (isCustomRegion) 8.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Text(
                            tab,
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isCustomRegion) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable { regionForTab?.let { showDeleteRegion = it } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete region",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick channel cards (bigger size)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered, key = { "${it.displayName}_${it.aliases.joinToString()}" }) { qc ->
                QuickChannelCardBig(
                    qc = qc,
                    isActive = selectedQc == qc,
                    onClick = { selectedQc = qc },
                    onLongClick = {
                        editingChannel = qc
                        showAddChannel = true
                    },
                )
            }
        }
    }

    // ModalBottomSheet popup with stream validation
    if (showSheet && selectedQc != null) {
        ModalBottomSheet(
            onDismissRequest = { 
                showSheet = false
                selectedQc = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selectedQc!!.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { runQcValidation() },
                        enabled = !qcValidating,
                    ) {
                        if (qcValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Validate",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    // Close button
                    IconButton(onClick = { showSheet = false; selectedQc = null }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))

                // Validation status
                val workingCount = aliveByUrl.values.count { it }
                val probedCount = aliveByUrl.size
                val totalCount = qcMatches.size

                if (qcLoading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Checking channels...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (qcMatches.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No matching channels found",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    // Status summary & hide offline toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$workingCount/$totalCount working" + if (probedCount < totalCount) " (probing $probedCount/$totalCount…)" else "",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (workingCount > 0) {
                                Text(
                                    "✓ $workingCount live",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            // Toggle to hide dead channels. Sized to content (no
                            // fixed height) with generous vertical padding so the
                            // pill is never clipped, and the left summary shrinks
                            // via weight(1f) instead of pushing the toggle off-screen.
                            Surface(
                                onClick = { hideDead = !hideDead },
                                shape = RoundedCornerShape(12.dp),
                                color = if (hideDead) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = if (hideDead) "Offline hidden" else "Show offline",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Channel list — working/live channels float to the top, most relevant first.
                    val sortedMatches = remember(qcMatches, aliveByUrl, hideDead, selectedQc) {
                        qcMatches
                            .filter { ch ->
                                if (!hideDead) true
                                else aliveByUrl[ch.url] != false
                            }
                            .sortedWith(compareByDescending<IptvChannel> { aliveByUrl[it.url] == true }
                                .thenByDescending { aliveByUrl[it.url] == null }
                                .thenByDescending { selectedQc?.let { qc -> relevanceScore(qc, it) } ?: 0 })
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sortedMatches) { ch ->
                            val isWorking = aliveByUrl[ch.url]
                            ChannelListItem(
                                channel = ch,
                                isAlive = isWorking,
                                onClick = {
                                    playChannel(ctx, ch, nav, allChannels)
                                    showSheet = false
                                    selectedQc = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Region dialog — ADD or EDIT, depending on whether [editingRegion] is set.
    if (showAddRegion || editingRegion != null) {
        val isEdit = editingRegion != null
        val seed = editingRegion ?: CustomRegion("", "")
        var regionName by remember(editingRegion, showAddRegion) { mutableStateOf(seed.name) }
        var regionTag by remember(editingRegion, showAddRegion) { mutableStateOf(seed.tag) }
        AlertDialog(
            onDismissRequest = { showAddRegion = false; editingRegion = null },
            title = { Text(if (isEdit) tr("Edit region") else tr("Add region")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = regionName,
                        onValueChange = { regionName = it },
                        label = { Text(tr("Region name")) },
                        placeholder = { Text(tr("e.g. French, Sports Extra")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = regionTag,
                        onValueChange = { regionTag = it },
                        label = { Text(tr("Tag")) },
                        placeholder = { Text(tr("e.g. french, sports2")) },
                        enabled = !isEdit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = regionName.isNotBlank() && (isEdit || regionTag.isNotBlank()),
                    onClick = {
                        val finalTag = if (isEdit) seed.tag else regionTag.trim().lowercase()
                        QuickChannelList.addCustomRegion(cacheDir, CustomRegion(regionName.trim(), finalTag))
                        reloadCustomData()
                        showAddRegion = false
                        editingRegion = null
                    },
                ) {
                    Text(if (isEdit) tr("Save") else tr("Add"))
                }
            },
            dismissButton = {
                Row {
                    if (isEdit) {
                        TextButton(onClick = {
                            val toDelete = editingRegion
                            editingRegion = null
                            showAddRegion = false
                            showDeleteRegion = toDelete
                        }) {
                            Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showAddRegion = false; editingRegion = null }) {
                        Text(tr("Cancel"))
                    }
                }
            },
        )
    }

    // Delete a custom region tab — takes the whole tab with it, including any
    // custom channels tagged for it (they still show up under "Custom").
    if (showDeleteRegion != null) {
        val region = showDeleteRegion!!
        AlertDialog(
            onDismissRequest = { showDeleteRegion = null },
            title = { Text(tr("Delete region")) },
            text = { Text(tr("Remove the \"${region.name}\" tab from the quick channel strip?")) },
            confirmButton = {
                TextButton(onClick = {
                    QuickChannelList.removeCustomRegion(cacheDir, region.tag)
                    reloadCustomData()
                    if (selectedQcRegion == region.name) selectedQcRegion = "All"
                    showDeleteRegion = null
                }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRegion = null }) {
                    Text(tr("Cancel"))
                }
            },
        )
    }
    
    // Channel dialog — ADD or EDIT, depending on whether [editingChannel] is set.
    if (showAddChannel || editingChannel != null) {
        val isEdit = editingChannel != null
        val seed = editingChannel ?: QuickChannel("", emptyList(), emptyList(), emptyList())
        var channelName by remember(editingChannel, showAddChannel) { mutableStateOf(seed.displayName) }
        var channelAlias by remember(editingChannel, showAddChannel) { mutableStateOf(seed.aliases.joinToString(", ")) }
        var channelRegion by remember(editingChannel, showAddChannel) { mutableStateOf(selectedQcRegion) }
        var regionDropdownExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddChannel = false; editingChannel = null },
            title = { Text(if (isEdit) tr("Edit channel") else tr("Add channel")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        label = { Text(tr("Display name")) },
                        placeholder = { Text(tr("e.g. My Channel")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = channelAlias,
                        onValueChange = { channelAlias = it },
                        label = { Text(tr("Search aliases")) },
                        placeholder = { Text(tr("e.g. CNN, ESPN")) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    // Region picker — ExposedDropdownMenu so the user can pick
                    // an existing preset or custom region when adding a channel.
                    val allRegions = buildList {
                        add("All")
                        add("US")
                        add("UK")
                        add("CA")
                        add("Premium")
                        add("Sports")
                        add("News")
                        add("Custom")
                        addAll(customRegions.map { it.name })
                    }
                    val currentRegionLabel = runCatching {
                        customRegions.first { it.name == channelRegion }.name
                    }.getOrElse { channelRegion }
                    ExposedDropdownMenuBox(
                        expanded = regionDropdownExpanded,
                        onExpandedChange = { regionDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = currentRegionLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(tr("Region")) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionDropdownExpanded)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = regionDropdownExpanded,
                            onDismissRequest = { regionDropdownExpanded = false },
                        ) {
                            allRegions.forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(region) },
                                    onClick = {
                                        channelRegion = region
                                        regionDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = channelName.isNotBlank(),
                    onClick = {
                        val parsedAliases = channelAlias.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val aliasList = if (parsedAliases.isEmpty()) listOf(channelName.trim()) else parsedAliases
                        val targetRegions = if (isEdit) {
                            seed.regions
                        } else {
                            when (channelRegion) {
                                "All", "Custom" -> listOf("custom")
                                else -> {
                                    val reg = customRegions.firstOrNull { it.name == channelRegion }
                                    listOf(reg?.tag ?: channelRegion.lowercase())
                                }
                            }
                        }
                        val qc = QuickChannel(
                            displayName = channelName.trim(),
                            aliases = aliasList,
                            regions = targetRegions,
                            tags = seed.tags,
                        )
                        QuickChannelList.addCustomChannel(cacheDir, qc)
                        reloadCustomData()
                        showAddChannel = false
                        editingChannel = null
                    },
                ) {
                    Text(if (isEdit) tr("Save") else tr("Add"))
                }
            },
            dismissButton = {
                Row {
                    if (isEdit) {
                        TextButton(onClick = {
                            val toDelete = editingChannel
                            editingChannel = null
                            showAddChannel = false
                            showDeleteChannel = toDelete
                        }) {
                            Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showAddChannel = false; editingChannel = null }) {
                        Text(tr("Cancel"))
                    }
                }
            },
        )
    }

    // Delete a custom quick channel.
    if (showDeleteChannel != null) {
        val qc = showDeleteChannel!!
        AlertDialog(
            onDismissRequest = { showDeleteChannel = null },
            title = { Text(tr("Delete channel")) },
            text = { Text(tr("Remove \"${qc.displayName}\" from your quick channels?")) },
            confirmButton = {
                TextButton(onClick = {
                    QuickChannelList.removeCustomChannel(cacheDir, qc.displayName)
                    reloadCustomData()
                    showDeleteChannel = null
                }) {
                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChannel = null }) {
                    Text(tr("Cancel"))
                }
            },
        )
    }
}

/**
 * Big Quick Channel card (160dp width).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickChannelCardBig(
    qc: QuickChannel,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                qc.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Channel list item with working status indicator.
 */
@Composable
private fun ChannelListItem(
    channel: IptvChannel,
    isAlive: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channel.group != null && channel.group.isNotEmpty()) {
                Text(
                    channel.group,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Working status indicator
        when (isAlive) {
            true -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Live",
                    color = Color(0xFF4CAF50),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            false -> {
                Text(
                    "Offline",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                )
            }
            null -> {
                Text(
                    "Play",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Probes streams in parallel (5 concurrent) and returns a map of URL -> alive flag.
 * Uses the app's own StreamProbe so the same connection pool and DNS cache are shared.
 * Calls [onProgress] as each URL finishes so the UI can update live.
 */
private suspend fun validateChannelStatuses(
    channels: List<IptvChannel>,
    concurrent: Int = 5,
    onProgress: (String, Boolean) -> Unit = { _, _ -> },
): Map<String, Boolean> {
    if (channels.isEmpty()) return emptyMap()
    val out = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    val semaphore = Semaphore(concurrent)
    coroutineScope {
        channels.map { ch ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = com.hikari.app.net.StreamProbe.probe(ch.url)
                    val isAlive = result?.isAlive ?: false
                    out[ch.url] = isAlive
                    com.hikari.app.ui.screens.IptvProbeCache.put(ch.url, result)
                    onProgress(ch.url, isAlive)
                }
            }
        }.awaitAll()
    }
    return out
}

/**
 * Play a channel using the app's player.
 * [allChannels] is the full channel list from the relevant playlist(s) — passed
 * by the caller after computing it (channels() is suspend so it must be
 * pre-fetched off the main thread). This is what the player's "Channels"
 * button shows and what CH+/CH- steps through.
 * The providerId is extracted from the channel itself (set by IptvProvider.channels()).
 */
private fun playChannel(
    ctx: android.content.Context,
    channel: IptvChannel,
    nav: NavHostController,
    allChannels: List<IptvChannel>,
) {
    val providerId = channel.providerId
    val isM3u8 = channel.url.endsWith(".m3u8", ignoreCase = true)
    val isMpd = channel.url.substringBefore('?').contains(".mpd", ignoreCase = true)
    val sources = org.json.JSONArray().apply {
        put(org.json.JSONObject()
            .put("name", channel.name)
            .put("url", channel.url)
            .put("isM3u8", isM3u8)
            .put("isMpd", isMpd)
            .put("provider", "IPTV")
            .put("providerId", providerId)
            .put("providerName", "")
            .put("details", "")
            .put("ytId", "")
            .put("isTorrent", false)
        )
    }
    val intent = android.content.Intent(ctx, com.hikari.app.player.PlayerActivity::class.java).apply {
        putExtra("title", channel.name)
        putExtra("sources", sources.toString())
        // Pass providerId as histProviderId so PlayerActivity.originProviderId is set
        // This allows fetchChannelStreams() to find the provider and resolve HLS streams
        putExtra("histProviderId", providerId)
        if (allChannels.isNotEmpty()) {
            val arr = org.json.JSONArray()
            allChannels.forEach { ch ->
                arr.put(
                    org.json.JSONObject()
                        .put("name", ch.name)
                        .put("url", ch.url)
                        .put("logo", ch.logo ?: "")
                        .put("group", ch.group)
                        .put("providerId", ch.providerId)
                )
            }
            putExtra("channelSurfChannels", arr.toString())
        }
    }
    ctx.startActivity(intent)
}

/** poster → square → wide → poster. */
private fun nextShape(shape: String): String = when (TileShapes.normalize(shape)) {
    TileShapes.POSTER -> TileShapes.SQUARE
    TileShapes.SQUARE -> TileShapes.WIDE
    else -> TileShapes.POSTER
}

private fun shapeLabel(shape: String): String = when (TileShapes.normalize(shape)) {
    TileShapes.WIDE -> I18n.t("Wide")
    TileShapes.SQUARE -> I18n.t("Square")
    else -> I18n.t("Poster")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerChannelListDialog(
    playerActivity: android.content.Context,
    controller: com.hikari.app.player.ChannelSurfController?,
    onDismiss: () -> Unit,
    onSwitch: (IptvChannel) -> Unit,
) {
    val app = LocalContext.current.applicationContext as HikariApp
    val allProviders by app.providers.providers.collectAsState()
    val playlists = remember(allProviders) { allProviders.filterIsInstance<IptvProvider>() }

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 600.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header & Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "IPTV Channels",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Current List") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("All Playlists") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Quick Channels") }
                    )
                }
                Spacer(Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // Current Playlist Channels
                        val channels = controller?.channels.orEmpty()
                        val currentIndex = controller?.currentIndex ?: -1
                        if (channels.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text("No active channel list", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(channels) { i, ch ->
                                    val isCurrent = i == currentIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable {
                                                if (controller != null) {
                                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                        controller.switch(i)
                                                    }
                                                }
                                                onSwitch(ch)
                                                onDismiss()
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (isCurrent) Color(0xFF4CAF50) else Color.Gray)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "${i + 1}. ${ch.name}",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // All Playlists
                        var allChannels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
                        var loading by remember { mutableStateOf(true) }
                        LaunchedEffect(playlists) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val list = mutableListOf<IptvChannel>()
                                for (p in playlists) {
                                    runCatching { list.addAll(p.channels()) }.getOrNull()
                                }
                                allChannels = list
                                loading = false
                            }
                        }
                        
                        androidx.compose.material3.OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search all channels…") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))

                        val filtered = remember(allChannels, searchQuery) {
                            if (searchQuery.isBlank()) allChannels
                            else allChannels.filter { it.name.contains(searchQuery, ignoreCase = true) || (it.group != null && it.group.contains(searchQuery, ignoreCase = true)) }
                        }

                        if (loading) {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (filtered.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text("No channels found", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filtered.take(200)) { ch ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable {
                                                onSwitch(ch)
                                                onDismiss()
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                ch.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (!ch.group.isNullOrBlank()) {
                                                Text(
                                                    ch.group,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Quick Channels embedded view
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            QuickChannelsSectionEmbedded(playlists = playlists, onChannelSelected = { ch, _ ->
                                onSwitch(ch)
                                onDismiss()
                            })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickChannelsSectionEmbedded(playlists: List<IptvProvider>, onChannelSelected: (IptvChannel, List<IptvChannel>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheDir = remember { (ctx.applicationContext as? HikariApp)?.cacheDir ?: ctx.cacheDir }

    val qcTabs = listOf("All", "US", "UK", "CA", "Premium", "Sports", "News", "Custom")
    var selectedQcRegion by remember { mutableStateOf("All") }

    var customRegions by remember { mutableStateOf(emptyList<CustomRegion>()) }
    var customChannels by remember { mutableStateOf(emptyList<QuickChannel>()) }

    fun reloadCustomData() {
        customRegions = QuickChannelList.getCustomRegions(cacheDir)
        customChannels = QuickChannelList.getCustomChannels(cacheDir, emptyList())
    }

    LaunchedEffect(cacheDir) {
        reloadCustomData()
    }

    var showSheet by remember { mutableStateOf(false) }
    var selectedQc by remember { mutableStateOf<QuickChannel?>(null) }
    var qcMatches by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var aliveByUrl by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var qcLoading by remember { mutableStateOf(false) }
    var qcValidating by remember { mutableStateOf(false) }
    var hideDead by remember { mutableStateOf(true) }
    
    // Cache: QuickChannel.displayName -> filtered channels (persisted across selections)
    val qcChannelCache = remember { mutableMapOf<String, List<IptvChannel>>() }
    // Full playlist channels (all playlists) — fetched once, reused for playChannel
    var allChannels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }

    fun runQcValidation() {
        val qc = selectedQc
        if (qc == null || qcValidating) return
        scope.launch(Dispatchers.IO) {
            qcValidating = true
            var currentMatches = qcMatches
            if (currentMatches.isEmpty()) {
                currentMatches = if (allChannels.isNotEmpty()) {
                    allChannels.filter { ch -> QuickChannelList.matches(qc, ch) }
                } else {
                    gatherIptvChannels(playlists).filter { ch -> QuickChannelList.matches(qc, ch) }
                }
                qcChannelCache[qc.displayName] = currentMatches
            }
            qcMatches = currentMatches
            validateChannelStatusesEmbedded(currentMatches) { url, isAlive ->
                aliveByUrl = aliveByUrl + (url to isAlive)
            }
            qcValidating = false
        }
    }

    val filtered = remember(selectedQcRegion, customRegions, customChannels) {
        when (selectedQcRegion) {
            "All" -> (QuickChannelList.default + customChannels).distinctBy { it.displayName }
            "US" -> QuickChannelList.default.filter { "US" in it.regions } + customChannels.filter { c -> c.regions.any { it.equals("us", ignoreCase = true) } }
            "UK" -> QuickChannelList.default.filter { "UK" in it.regions } + customChannels.filter { c -> c.regions.any { it.equals("uk", ignoreCase = true) } }
            "CA" -> QuickChannelList.default.filter { "CA" in it.regions } + customChannels.filter { c -> c.regions.any { it.equals("ca", ignoreCase = true) } }
            "Premium" -> QuickChannelList.default.filter { "premium" in it.tags } + customChannels.filter { c -> c.tags.any { it.equals("premium", ignoreCase = true) } }
            "Sports" -> QuickChannelList.default.filter { "sports" in it.tags } + customChannels.filter { c -> c.tags.any { it.equals("sports", ignoreCase = true) } }
            "News" -> QuickChannelList.default.filter { "news" in it.tags } + customChannels.filter { c -> c.tags.any { it.equals("news", ignoreCase = true) } }
            "Custom" -> customChannels
            else -> {
                val reg = customRegions.firstOrNull { it.name == selectedQcRegion }
                val targetTag = reg?.tag ?: selectedQcRegion
                val customMatches = customChannels.filter { c -> c.regions.any { it.equals(targetTag, ignoreCase = true) || it.equals(selectedQcRegion, ignoreCase = true) } }
                val defaultMatches = QuickChannelList.default.filter { c -> c.regions.any { it.equals(targetTag, ignoreCase = true) || it.equals(selectedQcRegion, ignoreCase = true) } }
                (customMatches + defaultMatches).distinctBy { it.displayName }
            }
        }
    }

    LaunchedEffect(selectedQc) {
        val qc = selectedQc
        if (qc != null) {
            showSheet = true
            
            // Fast path: serve from cache if available, don't clear UI
            val cached = qcChannelCache[qc.displayName]
            if (cached != null && cached.isNotEmpty()) {
                qcMatches = cached
                qcLoading = false
                // Refresh validation in background (allChannels already available)
                scope.launch(Dispatchers.IO) {
                    validateChannelStatusesEmbedded(cached) { url, isAlive ->
                        aliveByUrl = aliveByUrl + (url to isAlive)
                    }
                }
                return@LaunchedEffect
            }
            
            // Cold fetch: show loading, fetch in background
            qcLoading = true
            qcMatches = emptyList()
            aliveByUrl = emptyMap()
            
            scope.launch(Dispatchers.IO) {
                val fetched = gatherIptvChannels(playlists)
                allChannels = fetched
                val matches = fetched.filter { ch -> QuickChannelList.matches(qc, ch) }
                qcChannelCache[qc.displayName] = matches
                qcMatches = matches
                qcLoading = false
                validateChannelStatusesEmbedded(matches) { url, isAlive ->
                    aliveByUrl = aliveByUrl + (url to isAlive)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val allTabs = qcTabs + customRegions.map { it.name }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(allTabs) { tab ->
                val active = selectedQcRegion == tab
                Surface(
                    onClick = { selectedQcRegion = tab },
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = tab,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No quick channels in this tab", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered) { qc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { selectedQc = qc }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            qc.displayName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showSheet && selectedQc != null) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                selectedQc = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selectedQc!!.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { runQcValidation() }, enabled = !qcValidating) {
                        if (qcValidating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Validate", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showSheet = false; selectedQc = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))

                val workingCount = aliveByUrl.values.count { it }
                val totalCount = qcMatches.size

                if (qcLoading) {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (qcMatches.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("No matching channels found", color = Color.White.copy(alpha = 0.5f))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$workingCount/$totalCount working", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Surface(
                            onClick = { hideDead = !hideDead },
                            shape = RoundedCornerShape(12.dp),
                            color = if (hideDead) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(if (hideDead) "Offline hidden" else "Show offline", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    val sortedMatches = remember(qcMatches, aliveByUrl, hideDead, selectedQc) {
                        qcMatches
                            .filter { ch -> if (!hideDead) true else aliveByUrl[ch.url] != false }
                            .sortedWith(compareByDescending<IptvChannel> { aliveByUrl[it.url] == true }
                                .thenByDescending { aliveByUrl[it.url] == null }
                                .thenByDescending { selectedQc?.let { qc -> relevanceScore(qc, it) } ?: 0 })
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sortedMatches) { ch ->
                            val isWorking = aliveByUrl[ch.url]
                            ChannelListItem(channel = ch, isAlive = isWorking, onClick = {
                                onChannelSelected(ch, allChannels)
                                showSheet = false
                                selectedQc = null
                            })
                        }
                    }
                }
            }
        }
    }
}

private suspend fun validateChannelStatusesEmbedded(
    channels: List<IptvChannel>,
    concurrent: Int = 5,
    onProgress: (String, Boolean) -> Unit = { _, _ -> },
): Map<String, Boolean> {
    if (channels.isEmpty()) return emptyMap()
    val out = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    val semaphore = Semaphore(concurrent)
    coroutineScope {
        channels.map { ch ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = com.hikari.app.net.StreamProbe.probe(ch.url)
                    val isAlive = result?.isAlive ?: false
                    out[ch.url] = isAlive
                    onProgress(ch.url, isAlive)
                }
            }
        }.awaitAll()
    }
    return out
}

/**
 * Score how well [channel] matches [qc] by counting keyword hits in the name.
 * Higher = more relevant. Used as a secondary sort after alive status so the
 * top results show the channels whose names most closely match the selection.
 */
private fun relevanceScore(qc: com.hikari.app.data.QuickChannel, channel: IptvChannel): Int {
    val nameLower = channel.name.lowercase()
    val keywords = (qc.aliases + qc.displayName.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }).toSet()
    var score = 0
    for (kw in keywords) {
        val k = kw.replace("*", "").trim()
        if (k.isEmpty()) continue
        if (nameLower.contains(k)) score++
    }
    return score
}
