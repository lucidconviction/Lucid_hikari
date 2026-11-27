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
import com.hikari.app.ui.components.HikariSectionHeader
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val MusicAccent = Color(0xFF7C5CFF)
internal val MusicAccent2 = Color(0xFF00D2EF)
private val MusicBg = Color(0xFF0B0B10)

internal enum class MusicTab(val label: String) {
    HOME("Home"), EXPLORE("Explore"), LIBRARY("Library"), LOCAL("Local"), SEARCH("Search")
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
    // The YouTube page's own account view model: same sign-in, same accounts,
    // same device-code flow. Music only reads the session it already has.
    val accountsVm: com.hikari.app.youtube.YouTubeAccountViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel {
            com.hikari.app.youtube.YouTubeAccountViewModel(context)
        }
    val accountState by accountsVm.state.collectAsState()
    // Background music controls need POST_NOTIFICATIONS on Android 13+. The
    // YouTube tab asks once at startup; if that was denied, Music explains
    // why the shade is empty and offers the system switch.
    val notifPrefs = remember { context.getSharedPreferences("music_notifications", 0) }
    val notificationGranted = remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted.value = granted
        if (granted) runCatching { MusicPlaybackService.start(context) }
    }
    val localPermission = remember {
        mutableStateOf(LocalMusicRepository.hasPermission(context))
    }
    val localPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        localPermission.value = granted ||
            LocalMusicRepository.hasPermission(context)
    }
    // Google Drive upload through the system document provider: the user
    // picks a folder in Drive, and the downloads are copied into it.
    val driveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        if (tree != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val files = MusicLibrary.downloads.value
                if (files.isEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "Nothing downloaded to upload.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Uploading ${files.size} songs to Drive…",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    tree,
                    android.provider.DocumentsContract.getTreeDocumentId(tree),
                )
                var copied = 0
                for (download in files) {
                    val file = MusicLibrary.downloadFile(download) ?: continue
                    if (!file.isFile) continue
                    val mime = when (download.format.lowercase()) {
                        "flac" -> "audio/flac"
                        "m4a", "mp4" -> "audio/mp4"
                        "ogg" -> "audio/ogg"
                        else -> "audio/mpeg"
                    }
                    val doc = runCatching {
                        android.provider.DocumentsContract.createDocument(
                            context.contentResolver,
                            parent,
                            mime,
                            download.fileName,
                        )
                    }.getOrNull() ?: continue
                    val ok = runCatching {
                        context.contentResolver.openOutputStream(doc)?.use { output ->
                            file.inputStream().use { input -> input.copyTo(output) }
                        } != null
                    }.getOrDefault(false)
                    if (ok) copied++ else runCatching {
                        android.provider.DocumentsContract.deleteDocument(
                            context.contentResolver,
                            doc,
                        )
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        if (copied == files.size) {
                            "Uploaded $copied songs to Drive."
                        } else {
                            "Uploaded $copied of ${files.size} songs to Drive."
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
    val activity = context as? android.app.Activity
    fun openNotificationSettings() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
    fun askNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val rationale = activity?.let {
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
        } == true
        val asked = notifPrefs.getBoolean("asked", false)
        if (rationale || !asked) {
            notifPrefs.edit().putBoolean("asked", true).apply()
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationGranted.value = android.os.Build.VERSION.SDK_INT < 33 ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val player by MusicPlayer.state.collectAsState()
    val liked by MusicLibrary.liked.collectAsState()
    val playlists by MusicLibrary.playlists.collectAsState()
    val recent by MusicLibrary.recent.collectAsState()
    val downloads by MusicLibrary.downloads.collectAsState()
    val downloadStates by MusicLibrary.downloadStates.collectAsState()
    val preferLossless by MusicLibrary.preferLossless.collectAsState()
    val videoMode by MusicLibrary.videoMode.collectAsState()
    val catalog by MusicLibrary.catalog.collectAsState()

    LaunchedEffect(Unit) {
        MusicLibrary.init(context)
        MusicPlayer.init(context)
        QobuzResolver.warmUp()
        YtMusicCatalog.warmUp()
        YtAccountMusic.init(context)
        accountsVm.refresh()
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
    var ytAccountLiked by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var ytAccountPlaylists by remember { mutableStateOf<List<MusicPlaylist>>(emptyList()) }
    var ytMoodSections by remember {
        mutableStateOf<List<YtMusicCatalog.YtMoodSection>>(emptyList())
    }
    var showAccounts by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }
    var showSpotify by remember { mutableStateOf(false) }
    var showParty by remember { mutableStateOf(false) }
    var showDrive by remember { mutableStateOf(false) }
    var localTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var localLoading by remember { mutableStateOf(false) }
    var localReload by remember { mutableStateOf(0) }

    LaunchedEffect(tab, localPermission.value, localReload) {
        if (tab == MusicTab.LOCAL && localPermission.value) {
            localLoading = true
            localTracks = LocalMusicRepository.scan(context)
            localLoading = false
        }
    }

    // YouTube Music's Explore buttons merged into the Explore page.
    LaunchedEffect(catalog, homeReload) {
        if (catalog == "deezer") {
            ytMoodSections = emptyList()
        } else {
            ytMoodSections = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { YtMusicCatalog.moodSections() }.getOrDefault(emptyList())
            }
        }
    }

    // The account's own liked songs and playlists, loaded from the SAME
    // MediaServiceCore session the YouTube tab signed in with.
    LaunchedEffect(catalog, accountState.signedIn, accountState.accountName) {
        if (catalog == "ytmusic" && accountState.signedIn) {
            val lib = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                YtAccountMusic.library()
            }
            ytAccountLiked = lib.liked
            ytAccountPlaylists = lib.playlists
        } else {
            ytAccountLiked = emptyList()
            ytAccountPlaylists = emptyList()
        }
    }

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
        // The first play is the natural moment to ask for the notification
        // permission the background controls need. A permanent "don't ask
        // again" is handled by the banner's settings route instead.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            !notificationGranted.value &&
            !notifPrefs.getBoolean("asked", false)
        ) {
            askNotifications()
        }
        MusicPlayer.play(track, queue)
        // Video mode draws its picture on the Now Playing page, so a track
        // started from any Music shelf goes straight there; auto-next keeps
        // the same page and simply swaps the video/song.
        if (videoMode) nav.navigate(Routes.MUSIC_PLAYER)
    }

    fun changePlaybackMode(video: Boolean) {
        MusicLibrary.setVideoMode(video)
        MusicPlayer.setVideoMode(video)
        if (video && player.track != null) nav.navigate(Routes.MUSIC_PLAYER)
    }

    fun openArtist(id: String) {
        if (id.isBlank()) return
        scope.launch {
            detailLoading = true
            artistDetail = runCatching {
                when {
                    id.startsWith("yt:") -> YtMusicCatalog.artistDetails(id.removePrefix("yt:"))
                    id.startsWith("dz:") -> DeezerApi.artistDetails(id.removePrefix("dz:"))
                    id.startsWith("UC") -> YtMusicCatalog.artistDetails(id)
                    else -> DeezerApi.artistDetails(id)
                }
            }.getOrNull()
            detailLoading = false
        }
    }

    fun openAlbum(id: String) {
        if (id.isBlank()) return
        scope.launch {
            detailLoading = true
            albumDetail = runCatching {
                when {
                    id.startsWith("yt:") -> YtMusicCatalog.albumDetails(id.removePrefix("yt:"))
                    id.startsWith("dz:") -> DeezerApi.albumDetails(id.removePrefix("dz:"))
                    id.startsWith("MPRE") -> YtMusicCatalog.albumDetails(id)
                    else -> DeezerApi.albumDetails(id)
                }
            }.getOrNull()
            detailLoading = false
        }
    }

    fun openPlaylist(id: String) {
        if (id.isBlank()) return
        scope.launch {
            detailLoading = true
            playlistDetail = runCatching {
                val ytId = id.removePrefix("yt:")
                val dzId = id.removePrefix("dz:")
                when {
                    id.startsWith("account:") -> {
                        val tracks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            YtAccountMusic.playlistTracks(id)
                        }
                        if (tracks.isEmpty()) null else MusicPlaylistDetails(
                            playlist = ytAccountPlaylists.firstOrNull { it.id == id }
                                ?: MusicPlaylist(id = id, title = "YouTube Music"),
                            tracks = tracks,
                        )
                    }
                    id.startsWith("yt:") -> YtMusicCatalog.playlistDetails(ytId)
                    id.startsWith("dz:") -> DeezerApi.playlistDetails(dzId)
                    id.startsWith("VL") || id.startsWith("OLAK") || id.startsWith("PL") ->
                        YtMusicCatalog.playlistDetails(id)
                    else -> DeezerApi.playlistDetails(id)
                }
            }.getOrNull()
            detailLoading = false
        }
    }

    // Home shelves load once; the reload counter powers the refresh button.
    // Switching the catalogue (Settings → Music → Catalogue) reloads the page
    // from the other service.
    LaunchedEffect(homeReload, catalog, accountState.signedIn) {
        homeLoading = true
        homeError = null
        var completed = false
        try {
            val wantsDeezer = catalog != "ytmusic"
            val wantsYouTube = catalog != "deezer"
            // Deezer and YouTube Music are independent services. Waiting for
            // one before asking the other made the first Music visit the sum
            // of both latencies; running them together halves that.
            val (deezerHome, ytHome, accountHome) = kotlinx.coroutines.coroutineScope {
                val deezer = if (wantsDeezer) {
                    async {
                        kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                            runCatching { DeezerApi.home(force = homeReload > 0) }.getOrNull()
                        }
                    }
                } else null
                val youtube = if (wantsYouTube) {
                    async {
                        kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                            runCatching { YtMusicCatalog.home() }.getOrNull()
                        }
                    }
                } else null
                val account = if (wantsYouTube && accountState.signedIn) {
                    async {
                        kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { YtAccountMusic.home() }.getOrNull()
                            }
                        }
                    }
                } else null
                Triple(
                    deezer?.await(),
                    youtube?.await(),
                    account?.await(),
                )
            }
            home = mergeMusicHomes(deezerHome, ytHome, accountHome)
            if (home == null) {
                homeError = when (catalog) {
                    "ytmusic" -> "YouTube Music didn't answer. Check the connection and try again."
                    "deezer" -> "Couldn't reach Deezer."
                    else -> "Neither Deezer nor YouTube Music answered."
                }
            }
            completed = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Leaving the Music page cancels this effect. That is not a music
            // error, and its message ("the coroutine scope left the
            // composition") must never become the page's error state.
            throw e
        } catch (e: Throwable) {
            homeError = e.message ?: "Couldn't load music."
            completed = true
        } finally {
            // A replaced loader (account sign-in refresh, catalogue switch)
            // must not publish `loading = false` while the replacement is
            // already fetching: that one frame is the error flash.
            if (completed) homeLoading = false
        }
    }

    // Search is debounced; a cleared box drops back to the prompt.
    LaunchedEffect(query, catalog, filters) {
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
        val effectiveFilter = MusicFilter.entries.firstOrNull { it.name == filters }
            ?: MusicFilter.ALL
        val yt = if (catalog != "deezer") {
            runCatching { YtMusicCatalog.search(q, effectiveFilter) }.getOrNull()
        } else null
        val deezer = if (catalog != "ytmusic") {
            runCatching { DeezerApi.search(q) }.getOrNull()
        } else null
        searchData = mergeMusicSearch(yt, deezer)
        searching = false
    }

    // A download starting in the player should also refresh the row's state.
    LaunchedEffect(downloadStates) {
        MusicPlayer.refreshDownloadState()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NuvioMusicPalette.background),
    ) {
        NuvioAuroraBackground(seed = home?.hero?.coverUrl) {}
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = taskbar + if (player.track != null) 78.dp else 0.dp),
        ) {
            NuvioMusicTopBar(
                selected = tab,
                onSelect = { tabName = it.name },
                onRefresh = { homeReload++ },
                onSettings = { showSettings = true },
            )
            if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationGranted.value) {
                NuvioNotificationBanner(onEnable = ::askNotifications)
            }
            if (tab == MusicTab.HOME) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            videoMode -> "Music videos on"
                            catalog == "ytmusic" -> "YouTube Music catalogue"
                            catalog == "deezer" -> "Deezer catalogue"
                            else -> "Deezer + YouTube Music"
                        },
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    MusicModeToggle(video = videoMode, onChange = ::changePlaybackMode)
                }
            }
            when (tab) {
                MusicTab.HOME -> NuvioHomeTab(
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
                MusicTab.EXPLORE -> NuvioExploreTab(
                    onStation = { q ->
                        scope.launch {
                            val tracks = runCatching { DeezerApi.genreTracks(q, 30) }
                                .getOrDefault(emptyList())
                            if (tracks.isEmpty()) {
                                toast("No tracks found for this station.")
                            } else {
                                playTrack(tracks.first(), tracks)
                                MusicPlayer.setRepeat(MusicPlayer.RepeatMode.ALL)
                            }
                        }
                    },
                    ytMoods = ytMoodSections,
                    onYtMood = { mood ->
                        scope.launch {
                            val tracks = runCatching { YtMusicCatalog.moodTracks(mood) }
                                .getOrDefault(emptyList())
                            if (tracks.isEmpty()) {
                                toast("YouTube Music has no songs for ${mood.title}.")
                            } else {
                                playTrack(tracks.first(), tracks)
                                MusicPlayer.setRepeat(MusicPlayer.RepeatMode.ALL)
                            }
                        }
                    },
                )
                MusicTab.LIBRARY -> NuvioLibraryTab(
                    // Signed in, the account's Liked Music joins the local
                    // likes; both are the same songs, keyed by video/track id.
                    liked = if (catalog == "ytmusic" && ytAccountLiked.isNotEmpty()) {
                        (ytAccountLiked + liked).distinctBy { it.id }
                    } else liked,
                    playlists = playlists,
                    recent = recent,
                    downloads = downloads,
                    onPlay = ::playTrack,
                    onOpenPlaylist = { userPlaylistDetail = it },
                    onCreatePlaylist = {
                        createPlaylistTrack = null
                        createPlaylistOpen = true
                    },
                    onOpenDownloads = { showDownloads = true },
                    onTrackMenu = { menuTrack = it },
                    accountName = accountState.accountName,
                    accountSignedIn = accountState.signedIn,
                    accountChecked = accountState.checked,
                    onAccount = { showAccounts = true },
                    onSignIn = { accountsVm.startSignIn() },
                    accountPlaylists = ytAccountPlaylists,
                    onOpenAccountPlaylist = { openPlaylist(it.id) },
                    onOpenServers = { showServers = true },
                    onOpenSpotify = { showSpotify = true },
                    onOpenParty = { showParty = true },
                )
                MusicTab.LOCAL -> NuvioLocalTab(
                    tracks = localTracks,
                    loading = localLoading,
                    permissionGranted = localPermission.value,
                    onGrant = {
                        runCatching {
                            localPermissionLauncher.launch(LocalMusicRepository.requiredPermission())
                        }
                    },
                    onRefresh = { localReload++ },
                    onPlay = ::playTrack,
                    onTrackMenu = { menuTrack = it },
                )
                MusicTab.SEARCH -> NuvioSearchTab(
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
            }
        }

        if (player.track != null) {
            MusicMiniPlayer(
                state = player,
                onExpand = { nav.navigate(Routes.MUSIC_PLAYER) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        // Clear the television's left rail; on a phone the
                        // taskbar inset already does the same job at the bottom.
                        start = if (com.hikari.app.tv.TvMode.current()) 96.dp else 0.dp,
                        bottom = taskbar + 6.dp,
                    ),
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
        NuvioUserPlaylistPage(
            playlist = playlist,
            onClose = { userPlaylistDetail = null },
            onPlay = ::playTrack,
            onDeletePlaylist = {
                MusicLibrary.deletePlaylist(playlist.id)
                userPlaylistDetail = null
            },
            onRemoveTrack = { track ->
                MusicLibrary.removeTrackFromPlaylist(playlist.id, track.id)
                userPlaylistDetail = MusicLibrary.playlists.value
                    .firstOrNull { it.id == playlist.id }
            },
        )
    }

    if (showSettings) {
        MusicSettingsDialog(
            preferLossless = preferLossless,
            onPreferLossless = { MusicLibrary.setPreferLossless(it) },
            catalog = catalog,
            onCatalog = { MusicLibrary.setCatalog(it) },
            onDismiss = { showSettings = false },
        )
    }
    if (showServers) {
        NuvioServersScreen(
            onClose = { showServers = false },
            onPlay = { track, queue -> playTrack(track, queue) },
        )
    }
    if (showSpotify) {
        NuvioSpotifyScreen(onClose = { showSpotify = false })
    }
    if (showParty) {
        NuvioPartyScreen(onClose = { showParty = false })
    }
    if (showDrive) {
        NuvioDriveScreen(onClose = { showDrive = false })
    }
    // The same device-code dialog the YouTube tab uses: entering the code on
    // youtube.com/activate signs Music in to the identical account.
    com.hikari.app.youtube.YouTubeSignInDialog(accountsVm, accountState)
    if (showAccounts) {
        com.hikari.app.youtube.YouTubeAccountsDialog(
            account = accountState,
            onSelect = { id -> accountsVm.selectAccount(id) },
            onAdd = { accountsVm.startSignIn() },
            onSignOut = { accountsVm.signOut() },
            onDismiss = { showAccounts = false },
        )
    }
    if (showDownloads) {
        NuvioDownloadsPage(
            downloads = downloads,
            onClose = { showDownloads = false },
            onPlay = { track, queue -> playTrack(track, queue) },
            onDelete = { MusicLibrary.deleteDownload(it.track.id) },
            onUpload = { showDrive = true },
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
    HikariSectionHeader(title = title, subtitle = subtitle)
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

// ---------------------------------------------------------------------------
// Merged Deezer + YouTube Music catalogue
// ---------------------------------------------------------------------------

/** The merged home feed: account shelves, then Deezer, then public YouTube
 *  Music. Duplicate tracks inside one shelf are removed by title + artist. */
private fun mergeMusicHomes(
    deezer: DeezerApi.Home?,
    youTube: DeezerApi.Home?,
    account: DeezerApi.Home?,
): DeezerApi.Home? {
    fun key(track: MusicTrack): String =
        track.title.trim().lowercase() + "\u0000" + track.artist.trim().lowercase()

    val sections = LinkedHashMap<String, List<MusicTrack>>()
    fun add(all: DeezerApi.Home?) {
        all?.sections?.forEach { (title, tracks) ->
            val existing = sections[title].orEmpty()
            val seen = existing.mapTo(HashSet(), ::key)
            sections[title] = existing + tracks.filter { seen.add(key(it)) }
        }
    }
    add(account)
    add(deezer)
    add(youTube)

    val artists = (
        deezer?.artists.orEmpty().map { it.copy(id = "dz:${it.id}") } +
            youTube?.artists.orEmpty() +
            account?.artists.orEmpty()
        ).distinctBy { it.id }
    val albums = (
        deezer?.albums.orEmpty().map { it.copy(id = "dz:${it.id}") } +
            youTube?.albums.orEmpty() +
            account?.albums.orEmpty()
        ).distinctBy { it.id }
    val playlists = (
        deezer?.playlists.orEmpty().map { it.copy(id = "dz:${it.id}") } +
            youTube?.playlists.orEmpty() +
            account?.playlists.orEmpty()
        ).distinctBy { it.id }

    if (sections.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()) {
        return null
    }
    return DeezerApi.Home(sections, artists, albums, playlists)
}

/** The merged search page. YouTube songs come first (they carry an exact
 *  video id), then Deezer songs that are not the same recording. */
private fun mergeMusicSearch(
    youTube: MusicSearchData?,
    deezer: MusicSearchData?,
): MusicSearchData? {
    if (youTube == null && deezer == null) return null
    fun key(track: MusicTrack): String =
        track.title.trim().lowercase() + "\u0000" + track.artist.trim().lowercase()

    val seen = HashSet<String>()
    val tracks = (youTube?.tracks.orEmpty() + deezer?.tracks.orEmpty())
        .filter { seen.add(key(it)) }
    val artists = (
        youTube?.artists.orEmpty() +
            deezer?.artists.orEmpty().map { it.copy(id = "dz:${it.id}") }
        ).distinctBy { it.id }
    val albums = (
        youTube?.albums.orEmpty() +
            deezer?.albums.orEmpty().map { it.copy(id = "dz:${it.id}") }
        ).distinctBy { it.id }
    val playlists = (
        youTube?.playlists.orEmpty() +
            deezer?.playlists.orEmpty().map { it.copy(id = "dz:${it.id}") }
        ).distinctBy { it.id }
    return MusicSearchData(
        tracks = tracks,
        artists = artists,
        albums = albums,
        playlists = playlists,
    )
}
