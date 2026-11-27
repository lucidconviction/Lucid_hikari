package com.hikari.app.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.launch

/**
 * Music → Servers: a WebDAV file browser and a Navidrome/Subsonic album
 * browser, both playing through the same MusicPlayer.
 */
@Composable
internal fun NuvioServersScreen(
    onClose: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
) {
    var mode by remember { mutableStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(NuvioMusicPalette.background),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close servers",
                        tint = NuvioMusicPalette.text,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Music servers",
                        color = NuvioMusicPalette.text,
                        fontFamily = NuvioDisplay,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "WebDAV files or a Navidrome / Subsonic library",
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NuvioPill("WebDAV", mode == 0, { mode = 0 })
                NuvioPill("Navidrome", mode == 1, { mode = 1 })
            }
            Spacer(Modifier.height(10.dp))
            if (mode == 0) {
                WebDavPane(onPlay)
            } else {
                NavidromePane(onPlay)
            }
        }
    }
}

@Composable
private fun WebDavPane(onPlay: (MusicTrack, List<MusicTrack>?) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(MusicServerStore.webDav().url) }
    var user by remember { mutableStateOf(MusicServerStore.webDav().user) }
    var pass by remember { mutableStateOf(MusicServerStore.webDav().pass) }
    var entries by remember { mutableStateOf<List<WebDavClient.Entry>>(emptyList()) }
    var directory by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }

    fun load(target: String) {
        val server = MusicServerStore.WebDav(
            url = url.trim(),
            user = user.trim(),
            pass = pass,
        )
        if (server.url.isBlank()) {
            error = "Enter the WebDAV URL first."
            return
        }
        MusicServerStore.saveWebDav(server)
        loading = true
        error = null
        scope.launch {
            runCatching { WebDavClient.list(server, target) }
                .onSuccess {
                    entries = it
                    directory = target
                    connected = true
                }
                .onFailure { error = it.message ?: "WebDAV connection failed" }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ServerField("Server URL", url, { url = it }, placeholder = "https://host/dav/music")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ServerField("Username", user, { user = it })
                }
                Box(Modifier.weight(1f)) {
                    ServerField("Password", pass, { pass = it }, password = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NuvioPill(
                    if (connected) "Reload" else "Connect",
                    true,
                    onClick = { load(if (directory.isBlank()) url.trim() else directory) },
                )
                Spacer(Modifier.weight(1f))
                if (connected) {
                    NuvioPill("Root", false, onClick = { load(url.trim()) })
                }
            }
            if (connected && directory.isNotBlank()) {
                Text(
                    directory,
                    color = NuvioMusicPalette.textMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            error?.let {
                Text(
                    it,
                    color = NuvioMusicPalette.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NuvioMusicPalette.accent)
            }
            entries.isEmpty() -> NuvioEmptyState(
                title = if (connected) "This folder is empty" else "Connect a WebDAV server",
                message = if (connected) {
                    "No playable audio files or subfolders here."
                } else {
                    "Enter the server URL and credentials, then tap Connect."
                },
            )
            else -> {
                val playable = entries.filter { !it.isDir && it.isAudio }
                val headers = WebDavClient.streamHeaders(
                    MusicServerStore.WebDav(url.trim(), user.trim(), pass),
                )
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (directory.isNotBlank()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val parent = directory.trimEnd('/').substringBeforeLast('/')
                                        load(if (parent.startsWith("http")) parent else url.trim())
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = NuvioMusicPalette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Up one folder",
                                    color = NuvioMusicPalette.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                    }
                    items(entries, key = { it.url }) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDir) {
                                        load(entry.url)
                                    } else if (entry.isAudio) {
                                        val track = webDavTrack(entry, headers)
                                        val queue = playable.map { webDavTrack(it, headers) }
                                        onPlay(track, queue)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                if (entry.isDir) Icons.Filled.Folder else Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = if (entry.isDir) NuvioMusicPalette.accent
                                else NuvioMusicPalette.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    color = NuvioMusicPalette.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (entry.isDir) "Folder" else humanSize(entry.size),
                                    color = NuvioMusicPalette.textMuted,
                                    fontSize = 10.sp,
                                )
                            }
                            if (!entry.isDir && entry.isAudio) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = NuvioMusicPalette.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun webDavTrack(
    entry: WebDavClient.Entry,
    headers: Map<String, String>,
): MusicTrack = MusicTrack(
    id = "webdav:${entry.url}",
    title = entry.name.substringBeforeLast('.').ifBlank { entry.name },
    artist = "WebDAV",
    album = "WebDAV",
    streamUrl = entry.url,
    streamHeaders = headers,
)

@Composable
private fun NavidromePane(onPlay: (MusicTrack, List<MusicTrack>?) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(MusicServerStore.navidrome().url) }
    var user by remember { mutableStateOf(MusicServerStore.navidrome().user) }
    var pass by remember { mutableStateOf(MusicServerStore.navidrome().pass) }
    var albums by remember { mutableStateOf<List<NavidromeClient.Album>>(emptyList()) }
    var openAlbum by remember { mutableStateOf<NavidromeClient.Album?>(null) }
    var songs by remember { mutableStateOf<List<NavidromeClient.Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }

    fun server() = NavidromeClient.Server(url.trim(), user.trim(), pass)

    fun connect(album: NavidromeClient.Album? = null) {
        if (url.isBlank()) {
            error = "Enter the Navidrome URL first."
            return
        }
        MusicServerStore.saveNavidrome(
            MusicServerStore.Navidrome(url.trim(), user.trim(), pass),
        )
        loading = true
        error = null
        scope.launch {
            if (album == null) {
                runCatching { NavidromeClient.albums(server()) }
                    .onSuccess {
                        albums = it
                        connected = true
                        openAlbum = null
                    }
                    .onFailure { error = it.message ?: "Navidrome connection failed" }
            } else {
                runCatching { NavidromeClient.albumSongs(server(), album.id) }
                    .onSuccess {
                        songs = it
                        openAlbum = album
                        connected = true
                    }
                    .onFailure { error = it.message ?: "Could not open the album" }
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (openAlbum == null) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ServerField("Server URL", url, { url = it }, placeholder = "https://music.example.com")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ServerField("Username", user, { user = it })
                    }
                    Box(Modifier.weight(1f)) {
                        ServerField("Password", pass, { pass = it }, password = true)
                    }
                }
                Spacer(Modifier.height(8.dp))
                NuvioPill(
                    if (connected) "Reload albums" else "Connect",
                    true,
                    onClick = { connect() },
                )
                error?.let {
                    Text(
                        it,
                        color = NuvioMusicPalette.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { openAlbum = null }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back to albums",
                        tint = NuvioMusicPalette.text,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        openAlbum?.name.orEmpty(),
                        color = NuvioMusicPalette.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        openAlbum?.artist.orEmpty(),
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { connect(openAlbum) }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh album",
                        tint = NuvioMusicPalette.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NuvioMusicPalette.accent)
            }
            openAlbum == null && albums.isEmpty() -> NuvioEmptyState(
                title = if (connected) "No albums returned" else "Connect Navidrome",
                message = if (connected) {
                    "The server has no newest albums to show."
                } else {
                    "Enter the URL and Subsonic credentials, then tap Connect."
                },
            )
            openAlbum == null -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { connect(album) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NuvioArtwork(
                            url = album.coverUrl,
                            modifier = Modifier.size(52.dp),
                            cornerRadius = 8,
                            icon = Icons.Filled.Album,
                            iconSize = 22,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                album.name,
                                color = NuvioMusicPalette.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${album.artist} · ${album.songCount} tracks",
                                color = NuvioMusicPalette.textSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = NuvioMusicPalette.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            else -> {
                val queue = songs.map { it.toTrack() }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(songs, key = { i, s -> "nav:${s.id}:$i" }) { index, song ->
                        NuvioTrackRow(
                            track = song.toTrack(),
                            index = index,
                            isCurrent = false,
                            isPlaying = false,
                            onClick = {
                                val track = song.toTrack()
                                onPlay(track, queue)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun NavidromeClient.Song.toTrack(): MusicTrack = MusicTrack(
    id = "navidrome:$id",
    title = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationSeconds = durationSeconds,
    streamUrl = streamUrl,
)

@Composable
private fun ServerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false,
) {
    Column {
        Text(
            label,
            color = NuvioMusicPalette.textMuted,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = NuvioMusicPalette.text,
                fontSize = 13.sp,
                fontFamily = NuvioSans,
            ),
            cursorBrush = SolidColor(NuvioMusicPalette.accent),
            visualTransformation = if (password) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NuvioMusicPalette.surface)
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(10.dp)),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank() && placeholder.isNotBlank()) {
                        Text(placeholder, color = NuvioMusicPalette.textMuted, fontSize = 12.sp)
                    }
                    inner()
                }
            },
        )
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
