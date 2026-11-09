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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * The Audio / Video switch shared by the Music home page and the full player.
 *
 * Audio is the page's own lossless/stream player; Video opens the same song as
 * a YouTube music video in Hikari's player, where picture-in-picture and the
 * usual video controls already live.
 */
@Composable
internal fun MusicModeToggle(
    video: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicModeChip("Audio", Icons.Filled.MusicNote, !video) { onChange(false) }
        MusicModeChip("Video", Icons.Filled.PlayArrow, video) { onChange(true) }
    }
}

@Composable
private fun MusicModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MusicAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** The always-visible control strip above the taskbar. */
@Composable
internal fun MusicMiniPlayer(
    state: MusicPlayer.State,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onExpand),
        color = Color(0xFF181820),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MusicAccent,
                trackColor = Color.White.copy(alpha = 0.08f),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF22222C)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { MusicPlayer.previous() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = { MusicPlayer.toggle() }, modifier = Modifier.size(38.dp)) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = MusicAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                IconButton(onClick = { MusicPlayer.next() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Open player", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Full-screen Now Playing page. A route, not a dialog, so the taskbar is
 *  genuinely out of the way and Back returns to the Music page. */
@Composable
internal fun MusicPlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by MusicPlayer.state.collectAsState()
    val liked by MusicLibrary.liked.collectAsState()
    val preferLossless by MusicLibrary.preferLossless.collectAsState()
    val videoMode by MusicLibrary.videoMode.collectAsState()
    var settings by remember { mutableStateOf(false) }

    fun changePlaybackMode(video: Boolean) {
        MusicLibrary.setVideoMode(video)
        if (!video) return
        val track = state.track ?: return
        scope.launch {
            if (!openMusicVideo(context, track)) {
                MusicLibrary.setVideoMode(false)
                Toast.makeText(
                    context,
                    "No music video found for this song.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    MusicNowPlaying(
        state = state,
        liked = liked.any { it.id == state.track?.id },
        videoMode = videoMode,
        onVideoMode = ::changePlaybackMode,
        onDismiss = onBack,
        onLike = { state.track?.let { MusicLibrary.toggleLike(it) } },
        onDownload = { state.track?.let { MusicLibrary.download(it) } },
        onOpenSettings = { settings = true },
    )
    if (settings) {
        MusicSettingsDialog(
            preferLossless = preferLossless,
            onPreferLossless = { MusicLibrary.setPreferLossless(it) },
            onDismiss = { settings = false },
        )
    }
}

@Composable
internal fun MusicNowPlaying(
    state: MusicPlayer.State,
    liked: Boolean,
    videoMode: Boolean,
    onVideoMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onDownload: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val track = state.track ?: return
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val downloaded = MusicLibrary.downloadedFor(track.id) != null
    Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2A155C), Color(0xFF0B0B10), Color(0xFF000000)),
                    ),
                ),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player", tint = Color.White)
                    }
                    Text("Now playing", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Music settings", tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (videoMode) "Music video · opens in the player" else "Audio · lossless when available",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                    MusicModeToggle(video = videoMode, onChange = onVideoMode)
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showLyrics) {
                        LyricsPanel(state, Modifier.fillMaxSize())
                    } else {
                        AsyncImage(
                            model = track.coverUrl,
                            contentDescription = track.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF181820)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onLike) {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (liked) MusicAccent2 else Color.White.copy(alpha = 0.75f),
                        )
                    }
                    IconButton(onClick = onDownload) {
                        if (downloaded) Icon(Icons.Filled.Check, contentDescription = "Downloaded", tint = MusicAccent2)
                        else Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White.copy(alpha = 0.75f))
                    }
                }

                val duration = state.durationMs.coerceAtLeast(0L)
                Slider(
                    value = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { value -> MusicPlayer.seekTo((value * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.positionMs), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { MusicPlayer.setShuffle(!state.shuffle) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffle) MusicAccent2 else Color.White.copy(alpha = 0.55f),
                        )
                    }
                    IconButton(onClick = { MusicPlayer.previous() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { MusicPlayer.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFF24104F), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.playing) "Pause" else "Play",
                                tint = Color(0xFF24104F),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    IconButton(onClick = { MusicPlayer.next() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { MusicPlayer.cycleRepeat() }) {
                        Icon(
                            if (state.repeat == MusicPlayer.RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            tint = if (state.repeat != MusicPlayer.RepeatMode.OFF) MusicAccent2 else Color.White.copy(alpha = 0.55f),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(Icons.Filled.Subtitles, contentDescription = null, tint = if (showLyrics) MusicAccent2 else Color.White.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (showLyrics) "Artwork" else "Lyrics", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                    Text(
                        state.quality.ifBlank { if (state.loading) "Resolving…" else "Streaming" },
                        color = MusicAccent2.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = { showQueue = true }) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Queue", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                }
            }

            if (showQueue) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xF20B0B10))
                        .padding(WindowInsets.statusBars.asPaddingValues()),
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Up next", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showQueue = false }) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close queue", tint = Color.White)
                            }
                        }
                        if (state.queue.isEmpty()) {
                            Text("The queue is empty.", color = Color.White.copy(alpha = 0.55f))
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                itemsIndexed(state.queue) { index, queueTrack ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                MusicPlayer.playAt(index)
                                                showQueue = false
                                            }
                                            .padding(vertical = 7.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            color = if (index == state.index) MusicAccent2 else Color.White.copy(alpha = 0.4f),
                                            fontSize = 11.sp,
                                            modifier = Modifier.width(24.dp),
                                        )
                                        AsyncImage(
                                            model = queueTrack.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF181820)),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(queueTrack.title, color = if (index == state.index) MusicAccent2 else Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(queueTrack.artist, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(queueTrack.formattedDuration, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun LyricsPanel(state: MusicPlayer.State, modifier: Modifier = Modifier) {
    val lyrics = state.lyrics
    if (lyrics.isEmpty) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Subtitles, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (state.lyricsLoading) "Looking for lyrics…" else "No lyrics found for this track.",
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
        return
    }
    if (!lyrics.synced || lyrics.lines.isEmpty()) {
        LazyColumn(modifier.fillMaxSize()) {
            item {
                Text(
                    lyrics.plain,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        return
    }
    val active = MusicPlayer.activeLyricIndex(lyrics, state.positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) {
            runCatching { listState.animateScrollToItem((active - 2).coerceAtLeast(0)) }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            Text(
                line.text,
                color = if (index == active) Color.White else Color.White.copy(alpha = 0.38f),
                fontSize = if (index == active) 21.sp else 17.sp,
                fontWeight = if (index == active) FontWeight.Black else FontWeight.Medium,
                lineHeight = 26.sp,
            )
        }
    }
}

@Composable
internal fun MusicSettingsDialog(
    preferLossless: Boolean,
    onPreferLossless: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Music audio source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSourceRow(
                    title = "Lossless first (Qobuz)",
                    subtitle = "FLAC/Hi-Res when the Eclipse service has it; falls back to YouTube.",
                    selected = preferLossless,
                    onClick = { onPreferLossless(true) },
                )
                SettingsSourceRow(
                    title = "YouTube first",
                    subtitle = "Use the YouTube tab's engine, the same one used for video playback.",
                    selected = !preferLossless,
                    onClick = { onPreferLossless(false) },
                )
                Text(
                    "Downloads save the direct file when the source offers one (Qobuz FLAC or YouTube's muxed MP4); otherwise the track only streams.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingsSourceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MusicAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MusicAccent2)
    }
}

@Composable
internal fun ArtistDetailSheet(
    detail: MusicArtistDetails,
    liked: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onAlbum: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = detail.artist.pictureUrl,
                        contentDescription = detail.artist.name,
                        modifier = Modifier.size(86.dp).clip(CircleShape).background(Color(0xFF181820)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(detail.artist.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        val detailLine = when {
                            detail.artist.albumCount > 0 && detail.artist.fanCount > 0 ->
                                "${detail.artist.albumCount} albums · ${formatFans(detail.artist.fanCount)}"
                            detail.artist.albumCount > 0 -> "${detail.artist.albumCount} albums"
                            detail.artist.fanCount > 0 -> formatFans(detail.artist.fanCount)
                            else -> "Artist"
                        }
                        Text(detailLine, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                    if (detail.topTracks.isNotEmpty()) {
                        IconButton(onClick = { onPlay(detail.topTracks.first(), detail.topTracks) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play artist", tint = MusicAccent2, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            item { MusicSectionTitle("Top tracks", null) }
            items(detail.topTracks) { track ->
                MusicTrackRow(
                    track = track,
                    isLiked = liked.any { it.id == track.id },
                    onPlay = { onPlay(track, detail.topTracks) },
                    onMenu = { onTrackMenu(track) },
                )
            }
            if (detail.albums.isNotEmpty()) {
                item { MusicSectionTitle("Albums", null) }
                items(detail.albums) { album ->
                    AlbumRow(album) { onAlbum(album.id) }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
internal fun AlbumDetailSheet(
    detail: MusicAlbumDetails,
    liked: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onArtist: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = detail.album.coverUrl,
                        contentDescription = detail.album.title,
                        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF181820)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(detail.album.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            detail.album.artistName,
                            color = MusicAccent2,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onArtist(detail.album.artistId) },
                        )
                        Text("${detail.tracks.size} tracks", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    if (detail.tracks.isNotEmpty()) {
                        IconButton(onClick = { onPlay(detail.tracks.first(), detail.tracks) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play album", tint = MusicAccent2, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            items(detail.tracks) { track ->
                MusicTrackRow(
                    track = track,
                    isLiked = liked.any { it.id == track.id },
                    onPlay = { onPlay(track, detail.tracks) },
                    onMenu = { onTrackMenu(track) },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
internal fun PlaylistDetailSheet(
    detail: MusicPlaylistDetails,
    liked: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = detail.playlist.coverUrl,
                        contentDescription = detail.playlist.title,
                        modifier = Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF181820)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(detail.playlist.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(detail.playlist.creator, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        Text("${detail.tracks.size} tracks", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    if (detail.tracks.isNotEmpty()) {
                        IconButton(onClick = { onPlay(detail.tracks.first(), detail.tracks) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist", tint = MusicAccent2, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            items(detail.tracks) { track ->
                MusicTrackRow(
                    track = track,
                    isLiked = liked.any { it.id == track.id },
                    onPlay = { onPlay(track, detail.tracks) },
                    onMenu = { onTrackMenu(track) },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
internal fun UserPlaylistSheet(
    playlist: UserPlaylist,
    liked: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    onRemove: (MusicTrack) -> Unit,
    onDelete: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(86.dp).clip(RoundedCornerShape(14.dp)).background(MusicAccent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(34.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Text("${playlist.tracks.size} tracks", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                    if (playlist.tracks.isNotEmpty()) {
                        IconButton(onClick = { onPlay(playlist.tracks.first(), playlist.tracks) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist", tint = MusicAccent2, modifier = Modifier.size(32.dp))
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = Color.White.copy(alpha = 0.55f))
                    }
                }
            }
            if (playlist.tracks.isEmpty()) {
                item {
                    Text("This playlist is empty. Add songs from any three-dot menu.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            items(playlist.tracks) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        MusicTrackRow(
                            track = track,
                            isLiked = liked.any { it.id == track.id },
                            onPlay = { onPlay(track, playlist.tracks) },
                            onMenu = { onTrackMenu(track) },
                        )
                    }
                    IconButton(onClick = { onRemove(track) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(17.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
internal fun AddToPlaylistDialog(
    track: MusicTrack,
    playlists: List<UserPlaylist>,
    onAdd: (UserPlaylist) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (playlists.isEmpty()) {
                    Text("No playlists yet.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(vertical = 8.dp))
                }
                playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onAdd(playlist) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(playlist.title, color = Color.White, modifier = Modifier.weight(1f))
                        Text("${playlist.tracks.size}", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onCreate, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MusicAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New playlist", color = MusicAccent)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun CreatePlaylistDialog(
    initialTrack: MusicTrack?,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                initialTrack?.let {
                    Text("Starts with \"${it.title}\"", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DownloadsDialog(
    downloads: List<DownloadedMusic>,
    downloadStates: Map<String, MusicLibrary.DownloadState>,
    onPlay: (MusicTrack) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Downloaded songs") },
        text = {
            if (downloads.isEmpty() && downloadStates.isEmpty()) {
                Text("No downloads yet. Use the download button on a track.", color = Color.White.copy(alpha = 0.55f))
            } else {
                LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    downloadStates.entries.filter { it.value.running }.forEach { (id, state) ->
                        item(key = "run:$id") {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = MusicAccent, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Downloading… ${(state.progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }
                    }
                    downloadStates.entries.filter { it.value.message != null && !it.value.running }.forEach { (id, state) ->
                        item(key = "err:$id") {
                            Text(
                                "Download failed: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(downloads, key = { it.track.id }) { download ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                MusicTrackRow(
                                    track = download.track,
                                    isLiked = false,
                                    onPlay = { onPlay(download.track) },
                                    onMenu = {},
                                    trailing = "${(download.fileSize / (1024 * 1024.0)).formatOne()} MB",
                                )
                            }
                            IconButton(onClick = { onDelete(download.track.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete download", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
