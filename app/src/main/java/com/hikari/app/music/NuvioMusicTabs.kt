package com.hikari.app.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Section shell
// ---------------------------------------------------------------------------

@Composable
internal fun NuvioMusicTopBar(
    selected: MusicTab,
    onSelect: (MusicTab) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 720.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NuvioBrand(compact = true)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = NuvioMusicPalette.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Music settings",
                            tint = NuvioMusicPalette.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                NuvioNavPill(selected, onSelect, scrollable = true)
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 22.dp),
            ) {
                NuvioBrand(Modifier.align(Alignment.CenterStart))
                NuvioNavPill(
                    selected = selected,
                    onSelect = onSelect,
                    modifier = Modifier.align(Alignment.Center),
                )
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = NuvioMusicPalette.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Music settings",
                            tint = NuvioMusicPalette.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NuvioBrand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(if (compact) 30.dp else 34.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(NuvioMusicPalette.accentPurple, NuvioMusicPalette.accentTeal),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 17.dp else 19.dp),
            )
        }
        Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
        Text(
            "Lucid Music",
            color = NuvioMusicPalette.text,
            fontFamily = NuvioDisplay,
            fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun NuvioNavPill(
    selected: MusicTab,
    onSelect: (MusicTab) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val items: @Composable () -> Unit = {
        MusicTab.entries.forEach { tab ->
            NuvioNavItem(
                label = tab.label,
                selected = selected == tab,
                onClick = { onSelect(tab) },
            )
        }
    }
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(NuvioMusicPalette.surface.copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (scrollable) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items()
            }
        } else {
            items()
        }
    }
}

@Composable
private fun NuvioNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(32.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Color.White
                    focused -> NuvioMusicPalette.surfaceFocused
                    else -> Color.Transparent
                },
            )
            .border(
                2.dp,
                when {
                    focused && selected -> NuvioMusicPalette.accent
                    focused -> NuvioMusicPalette.accent
                    else -> Color.Transparent
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = when {
                selected -> NuvioMusicPalette.background
                else -> NuvioMusicPalette.textSecondary
            },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

@Composable
internal fun NuvioHomeTab(
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
    modifier: Modifier = Modifier,
) {
    when {
        home == null && loading -> NuvioLoading(modifier)
        home == null -> NuvioErrorState(error ?: "Couldn't load music.", onRetry, modifier)
        else -> {
            val hero = home.hero
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                hero?.let { track ->
                    item(key = "hero:${track.id}") {
                        NuvioHero(
                            track = track,
                            onPlay = { onPlay(track, home.sections.values.firstOrNull()) },
                            onArtist = { onArtist(track.artistId) },
                        )
                    }
                }
                if (home.artists.isNotEmpty()) {
                    item {
                        NuvioShelfRow(
                            shelf = NuvioShelf(
                                title = "Trending artists",
                                subtitle = "The names on everyone's playlist",
                                items = home.artists.map {
                                    NuvioShelfItem(
                                        title = it.name,
                                        subtitle = it.albumCount.takeIf { n -> n > 0 }
                                            ?.let { n -> "$n albums" } ?: "Artist",
                                        imageUrl = it.pictureUrl,
                                        kind = NuvioItemKind.ARTIST,
                                        artistId = it.id,
                                    )
                                },
                            ),
                            circleCards = true,
                            onItemClick = { item, _ -> onArtist(item.artistId) },
                        )
                    }
                }
                if (home.albums.isNotEmpty()) {
                    item {
                        NuvioShelfRow(
                            shelf = NuvioShelf(
                                title = "New releases",
                                subtitle = "Fresh full-lengths",
                                items = home.albums.map {
                                    NuvioShelfItem(
                                        title = it.title,
                                        subtitle = it.artistName.ifBlank { "Album" },
                                        imageUrl = it.coverUrl,
                                        kind = NuvioItemKind.ALBUM,
                                        albumId = it.id,
                                    )
                                },
                            ),
                            onItemClick = { item, _ -> onAlbum(item.albumId) },
                        )
                    }
                }
                if (home.playlists.isNotEmpty()) {
                    item {
                        NuvioShelfRow(
                            shelf = NuvioShelf(
                                title = "Curated charts & mixes",
                                subtitle = "Editorial playlists",
                                items = home.playlists.map {
                                    NuvioShelfItem(
                                        title = it.title,
                                        subtitle = it.creator.ifBlank { "Playlist" },
                                        imageUrl = it.coverUrl,
                                        kind = NuvioItemKind.PLAYLIST,
                                        playlistId = it.id,
                                    )
                                },
                            ),
                            onItemClick = { item, _ -> onPlaylist(item.playlistId) },
                        )
                    }
                }
                home.sections.forEach { (title, tracks) ->
                    item(key = "section:$title") {
                        NuvioShelfRow(
                            shelf = NuvioShelf(
                                title = title,
                                items = tracks.map { it.asShelfItem() },
                            ),
                            onItemClick = { item, _ ->
                                item.track?.let { onPlay(it, tracks) }
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

private fun MusicTrack.asShelfItem(): NuvioShelfItem = NuvioShelfItem(
    title = title,
    subtitle = artist,
    imageUrl = coverUrl,
    kind = NuvioItemKind.TRACK,
    track = this,
)

// ---------------------------------------------------------------------------
// Explore
// ---------------------------------------------------------------------------

private data class NuvioGenre(val label: String, val color: Color, val query: String)

private val nuvioGenres = listOf(
    NuvioGenre("Pop Hits", Color(0xFF7C5CFF), "Top Pop Hits"),
    NuvioGenre("Hip-Hop & Rap", Color(0xFF7850FF), "Hip Hop Hits"),
    NuvioGenre("Electronic & EDM", Color(0xFF00D294), "Electronic Dance"),
    NuvioGenre("Chill Lofi Beats", Color(0xFF00D2EF), "Lofi Beats Chill"),
    NuvioGenre("Rock Classics", Color(0xFFF99C00), "Rock Essentials"),
    NuvioGenre("R&B & Soul", Color(0xFFE12AFB), "R&B Soul"),
    NuvioGenre("Soundtracks & Gaming", Color(0xFFFF6568), "Hans Zimmer Soundtracks"),
    NuvioGenre("Heavy Metal", Color(0xFFFB2C36), "Heavy Metal"),
    NuvioGenre("Jazz & Blues", Color(0xFF625FFF), "Jazz Blues"),
    NuvioGenre("Classical Piano", Color(0xFFFAC800), "Classical Piano"),
)

private data class NuvioStation(val name: String, val color: Color, val query: String)

private val nuvioStations = listOf(
    NuvioStation("Pop Radio", Color(0xFF7C5CFF), "Pop Radio Hits"),
    NuvioStation("Rap & Hip-Hop", Color(0xFF7850FF), "Hip Hop Radio"),
    NuvioStation("Rock Mix", Color(0xFFF99C00), "Rock Radio"),
    NuvioStation("Dance & Electro", Color(0xFF00D294), "Electro Radio"),
    NuvioStation("R&B Station", Color(0xFFE12AFB), "R&B Radio"),
    NuvioStation("Lofi & Ambient", Color(0xFF00D2EF), "Lofi Radio"),
    NuvioStation("Heavy Metal", Color(0xFFFB2C36), "Metal Radio"),
    NuvioStation("Jazz Club", Color(0xFF625FFF), "Jazz Radio"),
)

private val nuvioMoodColors = listOf(
    Color(0xFF7C5CFF),
    Color(0xFF00D294),
    Color(0xFFE12AFB),
    Color(0xFFF99C00),
    Color(0xFF00D2EF),
    Color(0xFFFB2C36),
    Color(0xFF625FFF),
    Color(0xFFFAC800),
)

@Composable
internal fun NuvioExploreTab(
    onStation: (String) -> Unit,
    modifier: Modifier = Modifier,
    ytMoods: List<YtMusicCatalog.YtMoodSection> = emptyList(),
    onYtMood: (YtMusicCatalog.YtMood) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "Explore",
                    color = NuvioMusicPalette.text,
                    fontFamily = NuvioDisplay,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Moods, genres and continuous stations.",
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            NuvioSectionHeader("Moods & genres", "Pick a lane; we'll queue a station for it.")
        }
        items(nuvioGenres) { genre ->
            NuvioGenreTile(
                label = genre.label,
                color = genre.color,
                onClick = { onStation(genre.query) },
            )
        }
        ytMoods.forEach { section ->
            item(key = "yt-mood-header:${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 10.dp)) {
                    NuvioSectionHeader(section.title, "From YouTube Music")
                }
            }
            itemsIndexed(
                items = section.items,
                key = { index, mood -> "yt-mood:${mood.browseId}:$index" },
            ) { index, mood ->
                NuvioGenreTile(
                    label = mood.title,
                    color = nuvioMoodColors[index % nuvioMoodColors.size],
                    onClick = { onYtMood(mood) },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 10.dp)) {
                NuvioSectionHeader("Radio", "Continuous channels tuned to a mood.")
            }
        }
        items(nuvioStations) { station ->
            NuvioStationTile(
                name = station.name,
                color = station.color,
                onClick = { onStation(station.query) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------

@Composable
internal fun NuvioLibraryTab(
    liked: List<MusicTrack>,
    playlists: List<UserPlaylist>,
    recent: List<MusicTrack>,
    downloads: List<DownloadedMusic>,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onOpenPlaylist: (UserPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenDownloads: () -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    accountName: String? = null,
    accountSignedIn: Boolean = false,
    accountChecked: Boolean = true,
    onAccount: () -> Unit = {},
    onSignIn: () -> Unit = {},
    accountPlaylists: List<MusicPlaylist> = emptyList(),
    onOpenAccountPlaylist: (MusicPlaylist) -> Unit = {},
    onOpenServers: () -> Unit = {},
    onOpenSpotify: () -> Unit = {},
    onOpenParty: () -> Unit = {},
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            NuvioSectionHeader("Library", "Everything you saved, downloaded and played")
        }
        item {
            NuvioYouTubeAccountRow(
                name = accountName,
                signedIn = accountSignedIn,
                checked = accountChecked,
                onAccount = onAccount,
                onSignIn = onSignIn,
            )
        }
        item {
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NuvioMusicPalette.surface)
                    .border(
                        2.dp,
                        if (focused) NuvioMusicPalette.accent else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(16.dp),
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onOpenServers)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Album,
                    contentDescription = null,
                    tint = NuvioMusicPalette.accentTeal,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "WebDAV & Navidrome",
                        color = NuvioMusicPalette.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Browse and stream your own music servers",
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = NuvioMusicPalette.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        item {
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NuvioMusicPalette.surface)
                    .border(
                        2.dp,
                        if (focused) NuvioMusicPalette.accent else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(16.dp),
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onOpenSpotify)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = NuvioMusicPalette.accentCoral,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Import Spotify playlist",
                        color = NuvioMusicPalette.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Public link · no Spotify login",
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = NuvioMusicPalette.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        item {
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NuvioMusicPalette.surface)
                    .border(
                        2.dp,
                        if (focused) NuvioMusicPalette.accent else Color.White.copy(alpha = 0.06f),
                        RoundedCornerShape(16.dp),
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onOpenParty)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = NuvioMusicPalette.accentTeal,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Party mode",
                        color = NuvioMusicPalette.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Let guests queue songs from their phones",
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = NuvioMusicPalette.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NuvioLibraryTile(
                    title = "Liked",
                    subtitle = "${liked.size} songs",
                    icon = Icons.Filled.Favorite,
                    color = NuvioMusicPalette.accentCoral,
                    onClick = {
                        liked.firstOrNull()?.let { onPlay(it, liked) }
                    },
                    modifier = Modifier.weight(1f),
                )
                NuvioLibraryTile(
                    title = "Downloads",
                    subtitle = "${downloads.size} offline",
                    icon = Icons.Filled.Download,
                    color = NuvioMusicPalette.accentTeal,
                    onClick = onOpenDownloads,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NuvioLibraryTile(
                    title = "New playlist",
                    subtitle = "Collect songs",
                    icon = Icons.Filled.Add,
                    color = NuvioMusicPalette.accent,
                    onClick = onCreatePlaylist,
                    modifier = Modifier.weight(1f),
                )
                NuvioLibraryTile(
                    title = "Play recent",
                    subtitle = "${recent.size} played",
                    icon = Icons.Filled.PlayArrow,
                    color = NuvioMusicPalette.accentTeal,
                    onClick = {
                        recent.firstOrNull()?.let { onPlay(it, recent) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (liked.isNotEmpty()) {
            item {
                Column {
                    NuvioSectionHeader("Liked songs", "${liked.size} tracks", actionLabel = "Play all") {
                        onPlay(liked.first(), liked)
                    }
                    Spacer(Modifier.height(6.dp))
                    liked.take(8).forEachIndexed { index, track ->
                        NuvioTrackRow(
                            track = track,
                            index = index,
                            isCurrent = false,
                            isPlaying = false,
                            onClick = { onPlay(track, liked) },
                            onMenu = { onTrackMenu(track) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            item {
                NuvioShelfRow(
                    shelf = NuvioShelf(
                        title = "Your playlists",
                        subtitle = "${playlists.size} playlists",
                        items = playlists.map {
                            NuvioShelfItem(
                                title = it.title,
                                subtitle = "${it.tracks.size} tracks",
                                imageUrl = it.tracks.firstOrNull()?.coverUrl,
                                kind = NuvioItemKind.PLAYLIST,
                                playlistId = it.id,
                            )
                        },
                    ),
                    onItemClick = { item, _ ->
                        playlists.firstOrNull { it.id == item.playlistId }?.let(onOpenPlaylist)
                    },
                )
            }
        }
        if (accountPlaylists.isNotEmpty()) {
            item {
                NuvioShelfRow(
                    shelf = NuvioShelf(
                        title = "YouTube Music playlists",
                        subtitle = "Saved on your account",
                        items = accountPlaylists.map {
                            NuvioShelfItem(
                                title = it.title,
                                subtitle = it.creator.ifBlank { "Playlist" },
                                imageUrl = it.coverUrl,
                                kind = NuvioItemKind.PLAYLIST,
                                playlistId = it.id,
                            )
                        },
                    ),
                    onItemClick = { item, _ ->
                        accountPlaylists.firstOrNull { it.id == item.playlistId }
                            ?.let(onOpenAccountPlaylist)
                    },
                )
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Column {
                    NuvioSectionHeader("Recently played", "Pick up where you left off")
                    Spacer(Modifier.height(6.dp))
                    recent.take(8).forEachIndexed { index, track ->
                        NuvioTrackRow(
                            track = track,
                            index = index,
                            isCurrent = false,
                            isPlaying = false,
                            onClick = { onPlay(track, recent) },
                            onMenu = { onTrackMenu(track) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
        if (liked.isEmpty() && recent.isEmpty() && playlists.isEmpty()) {
            item {
                NuvioEmptyState(
                    title = "Your library is empty",
                    message = "Like a song, start a playlist or download an album and it will appear here.",
                )
            }
        }
    }
}

@Composable
internal fun NuvioNotificationBanner(onEnable: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NuvioMusicPalette.accentSoft)
            .border(
                2.dp,
                if (focused) NuvioMusicPalette.accent else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onEnable)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = NuvioMusicPalette.accent,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Background controls are off",
                color = NuvioMusicPalette.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Allow notifications for music controls in the shade and on the lock screen.",
                color = NuvioMusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Allow",
            color = NuvioMusicPalette.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NuvioYouTubeAccountRow(
    name: String?,
    signedIn: Boolean,
    checked: Boolean,
    onAccount: () -> Unit,
    onSignIn: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NuvioMusicPalette.surface)
            .border(
                2.dp,
                if (focused) NuvioMusicPalette.accent else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(16.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { if (signedIn) onAccount() else if (checked) onSignIn() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFCC0000)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    signedIn -> name?.takeIf { it.isNotBlank() } ?: "YouTube Music"
                    !checked -> "Checking YouTube account…"
                    else -> "Sign in with YouTube"
                },
                color = NuvioMusicPalette.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    signedIn -> "Same account as the YouTube tab · tap to switch"
                    !checked -> "Reading the YouTube session"
                    else -> "Uses the same login as the YouTube page"
                },
                color = NuvioMusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = NuvioMusicPalette.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NuvioLibraryTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NuvioMusicPalette.surface)
            .border(
                2.dp,
                if (focused) NuvioMusicPalette.accent else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(16.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Column {
            Text(
                title,
                color = NuvioMusicPalette.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = NuvioMusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Local (MediaStore)
// ---------------------------------------------------------------------------

@Composable
internal fun NuvioLocalTab(
    tracks: List<MusicTrack>,
    loading: Boolean,
    permissionGranted: Boolean,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(tracks, filter) {
        val q = filter.trim()
        if (q.isBlank()) tracks else tracks.filter {
            it.title.contains(q, true) ||
                it.artist.contains(q, true) ||
                it.album.contains(q, true)
        }
    }
    if (!permissionGranted) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp),
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = NuvioMusicPalette.accent,
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your local music",
                    color = NuvioMusicPalette.text,
                    fontFamily = NuvioDisplay,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Allow access to this device's audio library to list and play your own files.",
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                NuvioPill("Allow media access", selected = true, onClick = onGrant)
            }
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Local",
                            color = NuvioMusicPalette.text,
                            fontFamily = NuvioDisplay,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${tracks.size} songs on this device",
                            color = NuvioMusicPalette.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    NuvioPill("Refresh", selected = false, onClick = onRefresh)
                }
                Spacer(Modifier.height(10.dp))
                NuvioSearchField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = "Search this device…",
                )
            }
        }
        if (loading) {
            item { NuvioLoading(Modifier.fillMaxWidth().height(180.dp)) }
        } else if (shown.isEmpty()) {
            item {
                NuvioEmptyState(
                    title = "No local songs",
                    message = if (tracks.isEmpty()) {
                        "MediaStore returned no audio files."
                    } else {
                        "Nothing matches \"$filter\"."
                    },
                )
            }
        } else {
            itemsIndexed(shown, key = { i, t -> "local:${t.id}:$i" }) { index, track ->
                NuvioTrackRow(
                    track = track,
                    index = index,
                    isCurrent = false,
                    isPlaying = false,
                    onClick = { onPlay(track, shown) },
                    onMenu = { onTrackMenu(track) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

@Composable
internal fun NuvioSearchTab(
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
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        NuvioSearchField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MusicFilter.entries.forEach { entry ->
                NuvioPill(
                    text = entry.label,
                    selected = filter == entry,
                    onClick = { onFilter(entry) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            query.isBlank() -> NuvioEmptyState(
                title = "Search music",
                message = "Songs, artists, albums and playlists — one search across Deezer.",
            )
            searching && data == null -> NuvioLoading()
            data == null -> NuvioEmptyState(
                title = "Nothing yet",
                message = "No results for \"$appliedQuery\".",
            )
            else -> {
                val tracks = data.tracks
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 26.dp),
                ) {
                    if (filter == MusicFilter.ALL || filter == MusicFilter.TRACKS) {
                        if (tracks.isNotEmpty()) {
                            item {
                                NuvioSectionHeader("Songs", "${tracks.size} results")
                            }
                            itemsIndexed(tracks, key = { i, t -> "track:${t.id}:$i" }) { index, track ->
                                NuvioTrackRow(
                                    track = track,
                                    index = index,
                                    isCurrent = false,
                                    isPlaying = false,
                                    onClick = { onPlay(track, tracks) },
                                    onMenu = { onTrackMenu(track) },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                    if ((filter == MusicFilter.ALL || filter == MusicFilter.ARTISTS) && data.artists.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(10.dp))
                            NuvioSectionHeader("Artists", "${data.artists.size} results")
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(data.artists, key = { i, a -> "artist:${a.id}:$i" }) { _, artist ->
                                    NuvioCard(
                                        item = NuvioShelfItem(
                                            title = artist.name,
                                            subtitle = "${artist.albumCount} albums",
                                            imageUrl = artist.pictureUrl,
                                            kind = NuvioItemKind.ARTIST,
                                            artistId = artist.id,
                                        ),
                                        circle = true,
                                        onClick = { onArtist(artist.id) },
                                    )
                                }
                            }
                        }
                    }
                    if ((filter == MusicFilter.ALL || filter == MusicFilter.ALBUMS) && data.albums.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            NuvioSectionHeader("Albums", "${data.albums.size} results")
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(data.albums, key = { i, a -> "album:${a.id}:$i" }) { _, album ->
                                    NuvioCard(
                                        item = NuvioShelfItem(
                                            title = album.title,
                                            subtitle = album.artistName.ifBlank { "Album" },
                                            imageUrl = album.coverUrl,
                                            kind = NuvioItemKind.ALBUM,
                                            albumId = album.id,
                                        ),
                                        onClick = { onAlbum(album.id) },
                                    )
                                }
                            }
                        }
                    }
                    if ((filter == MusicFilter.ALL || filter == MusicFilter.PLAYLISTS) && data.playlists.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            NuvioSectionHeader("Playlists", "${data.playlists.size} results")
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(data.playlists, key = { i, p -> "playlist:${p.id}:$i" }) { _, playlist ->
                                    NuvioCard(
                                        item = NuvioShelfItem(
                                            title = playlist.title,
                                            subtitle = playlist.creator.ifBlank { "Playlist" },
                                            imageUrl = playlist.coverUrl,
                                            kind = NuvioItemKind.PLAYLIST,
                                            playlistId = playlist.id,
                                        ),
                                        onClick = { onPlaylist(playlist.id) },
                                    )
                                }
                            }
                        }
                    }
                    if (filter == MusicFilter.TRACKS && tracks.isEmpty()) {
                        item {
                            NuvioEmptyState(
                                title = "No songs found",
                                message = "Try a different spelling or another filter.",
                            )
                        }
                    }
                }
            }
        }
    }
}
