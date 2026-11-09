@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hikari.app.music

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.navigation.NavHostController
import com.hikari.app.ui.navigation.LocalTaskbarInset
import com.hikari.app.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val MusicAccent = Color(0xFF7C5CFF)
internal val MusicAccent2 = Color(0xFF00D2EF)
private val MusicBg = Color(0xFF0B0B10)

internal enum class MusicTab(val label: String) {
    HOME("Home"), SEARCH("Search"), BROWSE("Browse"), RADIO("Radio"), LIBRARY("Library")
}

internal enum class MusicFilter(val label: String) {
    ALL("All"), TRACKS("Tracks"), ARTISTS("Artists"), ALBUMS("Albums"), PLAYLISTS("Playlists")
}

/**
 * The Music tab: Home, Search, Browse, Radio and Library over one persistent
 * player. The page is the Hikari port of the reference music page — same
 * discovery flows and library, rebuilt in Compose and wired into the app's
 * existing theme, navigation and taskbar.
 */
@Composable
fun MusicScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taskbar = LocalTaskbarInset.current
    val player by MusicPlayer.state.collectAsState()
    val liked by MusicLibrary.liked.collectAsState()
    val playlists by MusicLibrary.playlists.collectAsState()
    val recent by MusicLibrary.recent.collectAsState()
    val downloads by MusicLibrary.downloads.collectAsState()
    val downloadStates by MusicLibrary.downloadStates.collectAsState()
    val preferLossless by MusicLibrary.preferLossless.collectAsState()
    val videoMode by MusicLibrary.videoMode.collectAsState()

    LaunchedEffect(Unit) {
        MusicLibrary.init(context)
        MusicPlayer.init(context)
        QobuzResolver.warmUp()
    }

    var tabName by rememberSaveable { mutableStateOf(MusicTab.HOME.name) }
    val tab = MusicTab.entries.firstOrNull { it.name == tabName } ?: MusicTab.HOME
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(MusicFilter.ALL.name) }
    var searchData by remember { mutableStateOf<MusicSearchData?>(null) }
    var searching by remember { mutableStateOf(false) }

    var home by remember { mutableStateOf<DeezerApi.Home?>(null) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeReload by remember { mutableStateOf(0) }

    var menuTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var artistDetail by remember { mutableStateOf<MusicArtistDetails?>(null) }
    var albumDetail by remember { mutableStateOf<MusicAlbumDetails?>(null) }
    var playlistDetail by remember { mutableStateOf<MusicPlaylistDetails?>(null) }
    var userPlaylistDetail by remember { mutableStateOf<UserPlaylist?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var createPlaylistTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var createPlaylistOpen by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun playTrack(track: MusicTrack, queue: List<MusicTrack>? = null) {
        if (!videoMode) {
            MusicPlayer.play(track, queue)
            return
        }
        scope.launch {
            val opened = openMusicVideo(context, track)
            if (!opened) {
                // No matching upload: never leave the tap doing nothing. Drop
                // back to Audio so the next tap plays the song immediately.
                MusicLibrary.setVideoMode(false)
                toast("No music video found — playing the audio instead.")
                MusicPlayer.play(track, queue)
            }
        }
    }

    fun changePlaybackMode(video: Boolean) {
        MusicLibrary.setVideoMode(video)
        if (!video) return
        val current = player.track ?: return
        scope.launch {
            val opened = openMusicVideo(context, current)
            if (!opened) {
                MusicLibrary.setVideoMode(false)
                toast("No music video found for this song.")
            }
        }
    }

    fun openArtist(id: String) {
        if (id.isBlank()) return
        scope.launch {
            detailLoading = true
            artistDetail = runCatching { DeezerApi.artistDetails(id) }.getOrNull()
            detailLoading = false
        }
    }

    fun openAlbum(id: String) {
        if (id.isBlank()) return
        scope.launch {
            detailLoading = true
            albumDetail = runCatching { DeezerApi.albumDetails(id) }.getOrNull()
            detailLoading = false
        }
    }

    fun openPlaylist(id: String) {
        if (id.isBlank()) return
        scope.launch {
            detailLoading = true
            playlistDetail = runCatching { DeezerApi.playlistDetails(id) }.getOrNull()
            detailLoading = false
        }
    }

    // Home shelves load once; the reload counter powers the refresh button.
    LaunchedEffect(homeReload) {
        homeLoading = true
        homeError = null
        try {
            home = DeezerApi.home(force = homeReload > 0)
        } catch (e: Throwable) {
            homeError = e.message ?: "Couldn't reach Deezer."
        } finally {
            homeLoading = false
        }
    }

    // Search is debounced; a cleared box drops back to the prompt.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            appliedQuery = ""
            searchData = null
            searching = false
            return@LaunchedEffect
        }
        delay(350)
        appliedQuery = q
        searching = true
        searchData = runCatching { DeezerApi.search(q) }.getOrNull()
        searching = false
    }

    // A download starting in the player should also refresh the row's state.
    LaunchedEffect(downloadStates) {
        MusicPlayer.refreshDownloadState()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MusicBg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = taskbar + if (player.track != null) 78.dp else 0.dp),
        ) {
            MusicHeader(
                onRefresh = { homeReload++ },
                onSettings = { showSettings = true },
            )
            if (tab == MusicTab.HOME) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (videoMode) "Music videos on" else "Audio only",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    MusicModeToggle(video = videoMode, onChange = ::changePlaybackMode)
                }
            }
            MusicTabRow(
                selected = tab,
                onSelect = { tabName = it.name },
            )
            when (tab) {
                MusicTab.HOME -> HomeTab(
                    home = home,
                    loading = homeLoading,
                    error = homeError,
                    liked = liked,
                    onPlay = ::playTrack,
                    onTrackMenu = { menuTrack = it },
                    onArtist = ::openArtist,
                    onAlbum = ::openAlbum,
                    onPlaylist = ::openPlaylist,
                    onRetry = { homeReload++ },
                )
                MusicTab.SEARCH -> SearchTab(
                    query = query,
                    onQueryChange = { query = it },
                    appliedQuery = appliedQuery,
                    searching = searching,
                    data = searchData,
                    filter = MusicFilter.entries.firstOrNull { it.name == filters } ?: MusicFilter.ALL,
                    onFilter = { filters = it.name },
                    liked = liked,
                    onPlay = ::playTrack,
                    onTrackMenu = { menuTrack = it },
                    onArtist = ::openArtist,
                    onAlbum = ::openAlbum,
                    onPlaylist = ::openPlaylist,
                )
                MusicTab.BROWSE -> BrowseTab(
                    onGenre = { q ->
                        query = q
                        tabName = MusicTab.SEARCH.name
                    },
                )
                MusicTab.RADIO -> RadioTab(
                    onPlayStation = { q ->
                        scope.launch {
                            val tracks = runCatching { DeezerApi.genreTracks(q, 30) }.getOrDefault(emptyList())
                            if (tracks.isEmpty()) toast("No tracks found for this station.")
                            else {
                                playTrack(tracks.first(), tracks)
                                // Radio is continuous: wrap the station's queue.
                                MusicPlayer.setRepeat(MusicPlayer.RepeatMode.ALL)
                            }
                        }
                    },
                )
                MusicTab.LIBRARY -> LibraryTab(
                    liked = liked,
                    playlists = playlists,
                    recent = recent,
                    downloads = downloads,
                    downloadStates = downloadStates,
                    onPlay = ::playTrack,
                    onOpenPlaylist = { userPlaylistDetail = it },
                    onCreatePlaylist = {
                        createPlaylistTrack = null
                        createPlaylistOpen = true
                    },
                    onDeletePlaylist = { MusicLibrary.deletePlaylist(it.id) },
                    onOpenDownloads = { showDownloads = true },
                    onTrackMenu = { menuTrack = it },
                )
            }
        }

        if (player.track != null) {
            MusicMiniPlayer(
                state = player,
                onExpand = { nav.navigate(Routes.MUSIC_PLAYER) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = taskbar + 6.dp),
            )
        }

        if (detailLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MusicAccent)
            }
        }
    }

    menuTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { menuTrack = null },
            title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MenuRow(if (liked.any { it.id == track.id }) "Remove from Liked Songs" else "Add to Liked Songs") {
                        MusicLibrary.toggleLike(track)
                        menuTrack = null
                    }
                    MenuRow("Add to playlist") {
                        menuTrack = null
                        addToPlaylistTrack = track
                    }
                    MenuRow(if (MusicLibrary.downloadedFor(track.id) != null) "Remove download" else "Download") {
                        menuTrack = null
                        if (MusicLibrary.downloadedFor(track.id) != null) MusicLibrary.deleteDownload(track.id)
                        else MusicLibrary.download(track)
                    }
                    if (track.artistId.isNotBlank()) {
                        MenuRow("View ${track.artist}") {
                            menuTrack = null
                            openArtist(track.artistId)
                        }
                    }
                    if (track.albumId.isNotBlank()) {
                        MenuRow("View ${track.album}") {
                            menuTrack = null
                            openAlbum(track.albumId)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuTrack = null }) { Text("Close") }
            },
        )
    }

    artistDetail?.let { detail ->
        ArtistDetailSheet(
            detail = detail,
            liked = liked,
            onDismiss = { artistDetail = null },
            onPlay = ::playTrack,
            onTrackMenu = { artistDetail = null; menuTrack = it },
            onAlbum = { artistDetail = null; openAlbum(it) },
        )
    }
    albumDetail?.let { detail ->
        AlbumDetailSheet(
            detail = detail,
            liked = liked,
            onDismiss = { albumDetail = null },
            onPlay = ::playTrack,
            onTrackMenu = { albumDetail = null; menuTrack = it },
            onArtist = { albumDetail = null; openArtist(it) },
        )
    }
    playlistDetail?.let { detail ->
        PlaylistDetailSheet(
            detail = detail,
            liked = liked,
            onDismiss = { playlistDetail = null },
            onPlay = ::playTrack,
            onTrackMenu = { playlistDetail = null; menuTrack = it },
        )
    }
    userPlaylistDetail?.let { playlist ->
        UserPlaylistSheet(
            playlist = playlist,
            liked = liked,
            onDismiss = { userPlaylistDetail = null },
            onPlay = ::playTrack,
            onTrackMenu = { userPlaylistDetail = null; menuTrack = it },
            onRemove = { track ->
                MusicLibrary.removeTrackFromPlaylist(playlist.id, track.id)
                userPlaylistDetail = MusicLibrary.playlists.value.firstOrNull { it.id == playlist.id }
            },
            onDelete = {
                MusicLibrary.deletePlaylist(playlist.id)
                userPlaylistDetail = null
            },
        )
    }

    if (showSettings) {
        MusicSettingsDialog(
            preferLossless = preferLossless,
            onPreferLossless = { MusicLibrary.setPreferLossless(it) },
            onDismiss = { showSettings = false },
        )
    }
    if (showDownloads) {
        DownloadsDialog(
            downloads = downloads,
            downloadStates = downloadStates,
            onPlay = { track -> playTrack(track, downloads.map { it.track }) },
            onDelete = { MusicLibrary.deleteDownload(it) },
            onDismiss = { showDownloads = false },
        )
    }
    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onAdd = { playlist ->
                MusicLibrary.addTrackToPlaylist(playlist.id, track)
                addToPlaylistTrack = null
            },
            onCreate = {
                addToPlaylistTrack = null
                createPlaylistTrack = track
                createPlaylistOpen = true
            },
            onDismiss = { addToPlaylistTrack = null },
        )
    }
    if (createPlaylistOpen) {
        CreatePlaylistDialog(
            initialTrack = createPlaylistTrack,
            onCreate = { title ->
                val playlist = MusicLibrary.createPlaylist(title)
                createPlaylistTrack?.let { MusicLibrary.addTrackToPlaylist(playlist.id, it) }
                createPlaylistOpen = false
                createPlaylistTrack = null
            },
            onDismiss = {
                createPlaylistOpen = false
                createPlaylistTrack = null
            },
        )
    }

    if (player.error != null) {
        LaunchedEffect(player.error) {
            toast(player.error!!)
            MusicPlayer.clearError()
        }
    }
}

@Composable
private fun MusicHeader(onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Music",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Lossless when available · Powered by Deezer",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh music", tint = Color.White.copy(alpha = 0.8f))
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Music settings", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun MusicTabRow(selected: MusicTab, onSelect: (MusicTab) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(MusicTab.entries) { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        tab.label,
                        fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun HomeTab(
    home: DeezerApi.Home?,
    loading: Boolean,
    error: String?,
    liked: List<MusicTrack>,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onPlaylist: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        home == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MusicAccent)
        }
        home == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "Couldn't load music.", color = Color.White.copy(alpha = 0.7f))
                TextButton(onClick = onRetry) { Text("Try again", color = MusicAccent) }
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            home.hero?.let { hero ->
                item {
                    MusicHero(
                        track = hero,
                        isLiked = liked.any { it.id == hero.id },
                        onPlay = { onPlay(hero, home.sections.values.firstOrNull()) },
                        onLike = { MusicLibrary.toggleLike(hero) },
                        onMenu = { onTrackMenu(hero) },
                    )
                }
            }
            if (home.artists.isNotEmpty()) {
                item {
                    MusicSectionTitle("Trending artists", "The names on everyone's playlist")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(home.artists, key = { it.id }) { artist ->
                            MusicArtistCard(artist) { onArtist(artist.id) }
                        }
                    }
                }
            }
            if (home.albums.isNotEmpty()) {
                item {
                    MusicSectionTitle("New album releases", "Fresh full-lengths")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(home.albums, key = { it.id }) { album ->
                            MusicAlbumCard(album) { onAlbum(album.id) }
                        }
                    }
                }
            }
            if (home.playlists.isNotEmpty()) {
                item {
                    MusicSectionTitle("Curated charts & mixes", "Editorial playlists")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(home.playlists, key = { it.id }) { playlist ->
                            MusicPlaylistCard(playlist) { onPlaylist(playlist.id) }
                        }
                    }
                }
            }
            home.sections.forEach { (title, tracks) ->
                item(key = "section:$title") {
                    MusicSectionTitle(title, null)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(tracks, key = { "$title:${it.id}" }) { track ->
                            MusicTrackCard(
                                track = track,
                                isLiked = liked.any { it.id == track.id },
                                onPlay = { onPlay(track, tracks) },
                                onMenu = { onTrackMenu(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    appliedQuery: String,
    searching: Boolean,
    data: MusicSearchData?,
    filter: MusicFilter,
    onFilter: (MusicFilter) -> Unit,
    liked: List<MusicTrack>,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onPlaylist: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Search songs, artists, albums, playlists…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(MusicFilter.entries) { item ->
                FilterChip(
                    selected = item == filter,
                    onClick = { onFilter(item) },
                    label = { Text(item.label) },
                )
            }
        }
        when {
            query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Find a song, an artist, an album.", color = Color.White.copy(alpha = 0.55f))
                }
            }
            searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MusicAccent)
            }
            data == null || data.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results for \"$appliedQuery\".", color = Color.White.copy(alpha = 0.6f))
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val showTracks = filter == MusicFilter.ALL || filter == MusicFilter.TRACKS
                val showArtists = filter == MusicFilter.ALL || filter == MusicFilter.ARTISTS
                val showAlbums = filter == MusicFilter.ALL || filter == MusicFilter.ALBUMS
                val showPlaylists = filter == MusicFilter.ALL || filter == MusicFilter.PLAYLISTS
                if (showArtists && data.artists.isNotEmpty()) {
                    item { MusicSectionTitle("Artists", null) }
                    items(data.artists, key = { "a:${it.id}" }) { artist ->
                        ArtistRow(artist) { onArtist(artist.id) }
                    }
                }
                if (showTracks && data.tracks.isNotEmpty()) {
                    item { MusicSectionTitle("Tracks", "${data.tracks.size} results") }
                    items(data.tracks, key = { "t:${it.id}" }) { track ->
                        MusicTrackRow(
                            track = track,
                            isLiked = liked.any { it.id == track.id },
                            onPlay = { onPlay(track, data.tracks) },
                            onMenu = { onTrackMenu(track) },
                        )
                    }
                }
                if (showAlbums && data.albums.isNotEmpty()) {
                    item { MusicSectionTitle("Albums", null) }
                    items(data.albums, key = { "al:${it.id}" }) { album ->
                        AlbumRow(album) { onAlbum(album.id) }
                    }
                }
                if (showPlaylists && data.playlists.isNotEmpty()) {
                    item { MusicSectionTitle("Playlists", null) }
                    items(data.playlists, key = { "p:${it.id}" }) { playlist ->
                        PlaylistRow(playlist) { onPlaylist(playlist.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseTab(onGenre: (String) -> Unit) {
    val genres = listOf(
        Triple("Pop Hits", MusicAccent, "Top Pop Hits"),
        Triple("Hip-Hop & Rap", Color(0xFF7850FF), "Hip Hop Hits"),
        Triple("Electronic & EDM", Color(0xFF00D294), "Electronic Dance"),
        Triple("Chill Lofi Beats", MusicAccent2, "Lofi Beats Chill"),
        Triple("Rock Classics", Color(0xFFF99C00), "Rock Essentials"),
        Triple("R&B & Soul", Color(0xFFE12AFB), "R&B Soul"),
        Triple("Soundtracks & Gaming", Color(0xFFFF6568), "Hans Zimmer Soundtracks"),
        Triple("Heavy Metal", Color(0xFFFB2C36), "Heavy Metal"),
        Triple("Jazz & Blues", Color(0xFF625FFF), "Jazz Blues"),
        Triple("Classical Piano", Color(0xFFFAC800), "Classical Piano"),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Browse moods & genres", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Pick a lane; we'll queue a station for it.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }
        items(genres) { (label, color, searchQuery) ->
            Box(
                Modifier
                    .aspectRatio(1.55f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(color.copy(alpha = 0.88f), color.copy(alpha = 0.38f)),
                        ),
                    )
                    .clickable { onGenre(searchQuery) }
                    .padding(16.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RadioTab(onPlayStation: (String) -> Unit) {
    val stations = listOf(
        "Pop Radio" to Color(0xFF7C5CFF),
        "Rap & Hip-Hop" to Color(0xFF7850FF),
        "Rock Mix" to Color(0xFFF99C00),
        "Dance & Electro" to Color(0xFF00D294),
        "R&B Station" to Color(0xFFE12AFB),
        "Lofi & Ambient" to Color(0xFF00D2EF),
        "Heavy Metal Station" to Color(0xFFFB2C36),
        "Jazz Club" to Color(0xFF625FFF),
    )
    val queries = listOf(
        "Pop Radio Hits", "Hip Hop Radio", "Rock Radio", "Electro Radio",
        "R&B Radio", "Lofi Radio", "Metal Radio", "Jazz Radio",
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Radio stations & live streams", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Continuous channels tuned to your mood.", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }
        items(stations.indices.toList()) { index ->
            val (name, color) = stations[index]
            Box(
                Modifier
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(color.copy(alpha = 0.18f))
                    .clickable { onPlayStation(queries[index]) }
                    .padding(16.dp),
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                    Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    liked: List<MusicTrack>,
    playlists: List<UserPlaylist>,
    recent: List<MusicTrack>,
    downloads: List<DownloadedMusic>,
    downloadStates: Map<String, MusicLibrary.DownloadState>,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onOpenPlaylist: (UserPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (UserPlaylist) -> Unit,
    onOpenDownloads: () -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your library", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New playlist", color = MusicAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            LibraryBanner(
                title = "Liked songs",
                subtitle = "${liked.size} favourite tracks",
                colors = listOf(Color(0xFF5B36F5), Color(0xFF8F58FF)),
                icon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) },
                onClick = {
                    if (liked.isNotEmpty()) onPlay(liked.first(), liked)
                },
            )
        }
        item {
            LibraryBanner(
                title = "Downloaded songs",
                subtitle = if (downloads.isEmpty()) "Nothing offline yet" else "${downloads.size} tracks · ${(downloads.sumOf { it.fileSize } / (1024 * 1024.0)).formatOne()} MB",
                colors = listOf(Color(0xFF0083B0), Color(0xFF00B4DB)),
                icon = { Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) },
                onClick = onOpenDownloads,
            )
        }
        val downloadError = downloadStates.values.firstOrNull { it.message != null && !it.running }
        if (downloadError != null) {
            item {
                Text(
                    "Download failed: ${downloadError.message}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (playlists.isNotEmpty()) {
            item { MusicSectionTitle("Your playlists", null) }
            items(playlists, key = { "pl:${it.id}" }) { playlist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onOpenPlaylist(playlist) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MusicAccent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicAccent)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.tracks.size} tracks", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    IconButton(onClick = { onDeletePlaylist(playlist) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { MusicSectionTitle("Recently played", null) }
            items(recent.take(20), key = { "r:${it.id}" }) { track ->
                MusicTrackRow(
                    track = track,
                    isLiked = liked.any { it.id == track.id },
                    onPlay = { onPlay(track, recent) },
                    onMenu = { onTrackMenu(track) },
                    trailing = downloadStates[track.id]?.let { state ->
                        if (state.running) "${(state.progress * 100).toInt()}%" else null
                    },
                )
            }
        }
        if (liked.isEmpty() && playlists.isEmpty() && recent.isEmpty() && downloads.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Like a song or make a playlist to start your library.", color = Color.White.copy(alpha = 0.55f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
private fun LibraryBanner(
    title: String,
    subtitle: String,
    colors: List<Color>,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun MusicHero(
    track: MusicTrack,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onMenu: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF24104F), Color(0xFF4B2A8A), Color(0xFF0B0B10)),
                ),
            ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("FEATURED TRACK", color = MusicAccent2, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(track.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = onPlay, shape = RoundedCornerShape(50), color = Color.White) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF24104F), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Play", color = Color(0xFF24104F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    IconButton(onClick = onLike) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) MusicAccent2 else Color.White.copy(alpha = 0.8f),
                        )
                    }
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MusicSectionTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        subtitle?.let {
            Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun MusicTrackCard(
    track: MusicTrack,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
) {
    Column(Modifier.width(148.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF181820))
                .clickable(onClick = onPlay),
        ) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            if (isLiked) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MusicAccent2,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(15.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
internal fun MusicArtistCard(artist: MusicArtist, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = artist.pictureUrl,
            contentDescription = artist.name,
            modifier = Modifier.size(86.dp).clip(CircleShape).background(Color(0xFF181820)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(artist.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val detail = when {
            artist.albumCount > 0 -> "${artist.albumCount} albums"
            artist.fanCount > 0 -> formatFans(artist.fanCount)
            else -> ""
        }
        if (detail.isNotBlank()) {
            Text(detail, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun MusicAlbumCard(album: MusicAlbum, onClick: () -> Unit) {
    Column(Modifier.width(142.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = album.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(13.dp)).background(Color(0xFF181820)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(5.dp))
        Text(album.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artistName, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MusicPlaylistCard(playlist: MusicPlaylist, onClick: () -> Unit) {
    Column(Modifier.width(142.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = playlist.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(13.dp)).background(Color(0xFF181820)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(5.dp))
        Text(playlist.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(playlist.creator, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MusicTrackRow(
    track: MusicTrack,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    trailing: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF181820)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            trailing ?: track.formattedDuration,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
        )
        IconButton(onClick = onMenu, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ArtistRow(artist: MusicArtist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artist.pictureUrl,
            contentDescription = artist.name,
            modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF181820)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when {
                artist.albumCount > 0 && artist.fanCount > 0 -> "${artist.albumCount} albums · ${formatFans(artist.fanCount)}"
                artist.albumCount > 0 -> "${artist.albumCount} albums"
                artist.fanCount > 0 -> formatFans(artist.fanCount)
                else -> "Artist"
            }
            Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
internal fun AlbumRow(album: MusicAlbum, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = album.title,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF181820)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(album.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artistName, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        val detail = if (album.trackCount > 0) "${album.trackCount} tracks" else album.releaseDate.take(4)
        if (detail.isNotBlank()) {
            Text(detail, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlaylistRow(playlist: MusicPlaylist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = playlist.title,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF181820)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlist.creator, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        Text("${playlist.trackCount} tracks", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    )
}

internal fun Double.formatOne(): String = String.format(java.util.Locale.US, "%.1f", this)

internal fun formatFans(count: Long): String = when {
    count >= 1_000_000 -> "${(count / 100_000) / 10.0}M fans"
    count >= 1_000 -> "${count / 1_000}K fans"
    else -> "$count fans"
}
