@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hikari.app.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.hikari.app.music.audio.MusicBitPerfectUsb
import kotlinx.coroutines.delay
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
    /** When set, a music-note button returns to the Music tab. The Music tab's
     *  own copy passes null (it is already there). */
    onOpenMusicTab: (() -> Unit)? = null,
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
                if (onOpenMusicTab != null) {
                    IconButton(onClick = onOpenMusicTab, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MusicNote, contentDescription = "Go to Music", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(21.dp))
                    }
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
    val state by MusicPlayer.state.collectAsState()
    val liked by MusicLibrary.liked.collectAsState()
    val preferLossless by MusicLibrary.preferLossless.collectAsState()
    val videoMode by MusicLibrary.videoMode.collectAsState()
    val catalog by MusicLibrary.catalog.collectAsState()
    var settings by remember { mutableStateOf(false) }

    fun changePlaybackMode(video: Boolean) {
        MusicLibrary.setVideoMode(video)
        MusicPlayer.setVideoMode(video)
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
            catalog = catalog,
            onCatalog = { MusicLibrary.setCatalog(it) },
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Transport chrome is a video-player overlay now: it fades away while the
    // music plays and comes back on a tap, so a music video can be watched
    // without a wall of buttons. Paused or reading lyrics keeps it up.
    var controlsVisible by remember { mutableStateOf(true) }
    var videoFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, state.playing, showLyrics, state.track?.id) {
        if (controlsVisible && state.playing && !showLyrics) {
            delay(5_000L)
            controlsVisible = false
        }
    }
    LaunchedEffect(state.track?.id) { controlsVisible = true }
    LaunchedEffect(state.videoPlaying) {
        if (!state.videoPlaying) videoFullscreen = false
    }
    // Back leaves the full-screen video before it leaves the Now Playing page.
    androidx.activity.compose.BackHandler(enabled = videoFullscreen) {
        videoFullscreen = false
    }

    // Full-screen music video: the page's chrome is gone and the picture fills
    // the window. Tap the picture for the overlay controls, Back (or the ⌄
    // button) returns to the normal Now Playing page.
    if (videoFullscreen && state.videoPlaying) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view -> view.player = MusicPlayer.exoPlayer },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { controlsVisible = !controlsVisible },
            )
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC000000), Color.Transparent),
                            ),
                        )
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { videoFullscreen = false }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Exit full screen", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6000000)),
                            ),
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    val duration = state.durationMs.coerceAtLeast(0L)
                    Slider(
                        value = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { value -> MusicPlayer.seekTo((value * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { MusicPlayer.previous() }) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.width(18.dp))
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { MusicPlayer.toggle() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.playing) "Pause" else "Play",
                                tint = Color(0xFF24104F),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        IconButton(onClick = { MusicPlayer.next() }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(state.positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(formatTime(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
        return
    }

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
                        if (videoMode) "Music video · plays here" else "Audio · lossless when available",
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
                    if (state.videoPlaying) {
                        // The YouTube music video, drawn inline in the Music
                        // player. The shared MusicPlayer ExoPlayer is attached
                        // to a plain PlayerView; its own controls stay hidden
                        // because the page's controls are right below.
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = { view -> view.player = MusicPlayer.exoPlayer },
                            onRelease = { view -> view.player = null },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black),
                        )
                        // Tap the picture to bring the transport back once it
                        // has timed out.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { controlsVisible = !controlsVisible },
                        )
                        // Full screen keeps the music video in this page;
                        // PiP hands it to Hikari's full player, where the
                        // system picture-in-picture button lives.
                        Column(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Surface(
                                onClick = {
                                    videoFullscreen = true
                                    controlsVisible = true
                                },
                                shape = RoundedCornerShape(50),
                                color = Color(0x99000000),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Fullscreen,
                                        contentDescription = "Full screen video",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "Full screen",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Surface(
                                onClick = {
                                    scope.launch {
                                        openMusicVideo(context, track, state.positionMs)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                color = Color(0x99000000),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Fullscreen,
                                        contentDescription = "Picture in picture",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        "PiP",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    } else if (showLyrics) {
                        LyricsPanel(state, Modifier.fillMaxSize())
                    } else {
                        AsyncImage(
                            model = track.coverUrl,
                            contentDescription = track.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF181820))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { controlsVisible = !controlsVisible },
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

                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    Column {
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
    catalog: String,
    onCatalog: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MusicSettings.init(context) }
    val quality by MusicSettings.audioQuality.collectAsState()
    val autoplay by MusicSettings.autoplay.collectAsState()
    val skipSilence by MusicSettings.skipSilence.collectAsState()
    val speed by MusicSettings.playbackSpeed.collectAsState()
    val fade by MusicSettings.fadeSeconds.collectAsState()
    val equalizer by MusicSettings.equalizerPreset.collectAsState()
    val downloadQuality by MusicSettings.downloadQuality.collectAsState()
    val wifiOnly by MusicSettings.wifiOnlyDownloads.collectAsState()
    val usbDevices = remember(context) { UsbAudioOutput.devices(context) }
    val bitPerfectUsb by MusicSettings.bitPerfectUsb.collectAsState()
    val usbState by com.hikari.app.music.audio.MusicBitPerfectUsb.state.collectAsState()
    val lastFm by MusicScrobbler.config.collectAsState()
    var lastFmUser by remember { mutableStateOf("") }
    var lastFmPass by remember { mutableStateOf("") }
    var lastFmError by remember { mutableStateOf<String?>(null) }
    var lastFmBusy by remember { mutableStateOf(false) }
    val lastFmScope = rememberCoroutineScope()
    val notificationsOn = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        android.os.Build.VERSION.SDK_INT < 33

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Music settings") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SettingsHeading("CATALOGUE")
                SettingsSourceRow(
                    title = "Deezer + YouTube Music (merged)",
                    subtitle = "One Home and Search: Deezer charts plus YouTube Music shelves. Duplicate songs collapse to one row.",
                    selected = catalog != "deezer" && catalog != "ytmusic",
                    onClick = { onCatalog("both") },
                )
                SettingsSourceRow(
                    title = "Deezer only",
                    subtitle = "The default charts, artists and editorial playlists.",
                    selected = catalog == "deezer",
                    onClick = { onCatalog("deezer") },
                )
                SettingsSourceRow(
                    title = "YouTube Music only",
                    subtitle = "Personalised account shelves and exact video playback.",
                    selected = catalog == "ytmusic",
                    onClick = { onCatalog("ytmusic") },
                )

                SettingsHeading("PLAYBACK")
                SettingsChoiceLine(
                    label = "Audio quality",
                    options = MusicAudioQuality.entries.map { it.label to it },
                    selected = quality,
                    onSelect = { MusicSettings.setAudioQuality(it) },
                )
                SettingsToggleLine(
                    title = "Autoplay next track",
                    subtitle = "Continue through the queue when a song ends.",
                    checked = autoplay,
                    onChange = { MusicSettings.setAutoplay(it) },
                )
                SettingsToggleLine(
                    title = "Skip silence",
                    subtitle = "Jump over silent gaps inside a track.",
                    checked = skipSilence,
                    onChange = { MusicSettings.setSkipSilence(it) },
                )
                SettingsChoiceLine(
                    label = "Speed",
                    options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                        .map { speedLabel(it) to it },
                    selected = speed,
                    onSelect = { MusicSettings.setPlaybackSpeed(it) },
                )
                SettingsChoiceLine(
                    label = "Crossfade",
                    options = (0..4).map {
                        if (it == 0) "Gapless" to it else "${it}s" to it
                    },
                    selected = fade,
                    onSelect = { MusicSettings.setFadeSeconds(it) },
                )
                SettingsChoiceLine(
                    label = "Equalizer",
                    options = MusicEqualizerPreset.entries.map { it.label to it },
                    selected = equalizer,
                    onSelect = { MusicSettings.setEqualizerPreset(it) },
                )

                SettingsHeading("DOWNLOADS")
                SettingsChoiceLine(
                    label = "Download quality",
                    options = MusicAudioQuality.entries.map { it.label to it },
                    selected = downloadQuality,
                    onSelect = { MusicSettings.setDownloadQuality(it) },
                )
                SettingsToggleLine(
                    title = "Wi-Fi only",
                    subtitle = "Do not start a download on mobile data.",
                    checked = wifiOnly,
                    onChange = { MusicSettings.setWifiOnlyDownloads(it) },
                )
                Text(
                    "Downloads save the direct file when the source offers one (Qobuz FLAC or YouTube's muxed MP4); otherwise the track only streams.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.5.sp,
                )

                SettingsHeading("NOTIFICATIONS")
                SettingsSourceRow(
                    title = if (notificationsOn) {
                        "Background controls enabled"
                    } else {
                        "Background controls are off"
                    },
                    subtitle = if (notificationsOn) {
                        "Play, pause, previous and next appear in the shade and on the lock screen."
                    } else {
                        "Tap to allow notifications so the media controls can appear."
                    },
                    selected = notificationsOn,
                    onClick = {
                        val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                        }
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                )

                SettingsHeading("LAST.FM")
                if (!lastFm.available) {
                    Text(
                        "Add LASTFM_API_KEY and LASTFM_SECRET to gradle.properties, rebuild, then sign in here.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.5.sp,
                    )
                } else if (lastFm.configured) {
                    SettingsToggleLine(
                        title = "Scrobble to Last.fm",
                        subtitle = "Signed in as ${lastFm.username.ifBlank { "Last.fm" }}",
                        checked = lastFm.enabled,
                        onChange = { MusicScrobbler.setEnabled(context, it) },
                    )
                    TextButton(onClick = { MusicScrobbler.logout(context) }) {
                        Text("Sign out of Last.fm", color = Color.White.copy(alpha = 0.65f))
                    }
                } else {
                    SettingsInputLine(
                        label = "Last.fm username",
                        value = lastFmUser,
                        onValueChange = { lastFmUser = it },
                    )
                    Spacer(Modifier.height(5.dp))
                    SettingsInputLine(
                        label = "Password",
                        value = lastFmPass,
                        onValueChange = { lastFmPass = it },
                        password = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NuvioPill(
                            if (lastFmBusy) "Signing in…" else "Sign in",
                            true,
                            onClick = {
                                if (!lastFmBusy) {
                                    lastFmBusy = true
                                    lastFmError = null
                                    lastFmScope.launch {
                                        MusicScrobbler.login(context, lastFmUser, lastFmPass)
                                            .onSuccess { lastFmPass = "" }
                                            .onFailure {
                                                lastFmError = it.message ?: "Last.fm sign-in failed"
                                            }
                                        lastFmBusy = false
                                    }
                                }
                            },
                        )
                    }
                    lastFmError?.let {
                        Text(it, color = NuvioMusicPalette.error, fontSize = 10.5.sp)
                    }
                }

                SettingsHeading("USB AUDIO")
                SettingsToggleLine(
                    title = "Bit-perfect USB output",
                    subtitle = if (usbDevices.isEmpty()) {
                        "Connect a USB DAC, then enable direct output."
                    } else {
                        "Exclusive LastWave driver · " + usbDevices.joinToString { it.name }
                    },
                    checked = bitPerfectUsb,
                    onChange = { on ->
                        if (!on) {
                            MusicBitPerfectUsb.setEnabled(false)
                            MusicSettings.setBitPerfectUsb(false)
                        } else {
                            MusicBitPerfectUsb.setEnabled(true) { granted, message ->
                                MusicSettings.setBitPerfectUsb(granted)
                                if (!granted) {
                                    android.widget.Toast.makeText(
                                        context,
                                        message ?: "USB output was not enabled.",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                )
                Text(
                    when {
                        usbState.bitPerfect -> "Bit-perfect: PCM is going straight to the USB DAC."
                        usbState.active -> "USB direct output active for supported PCM."
                        !usbState.message.isNullOrBlank() -> usbState.message.orEmpty()
                        else ->
                            "Only supported lossless PCM is sent to the DAC; other formats keep Android's normal route."
                    },
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.5.sp,
                )

                SettingsHeading("AUDIO SOURCE")
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
                    "Music section design: Nuvio One M / BitChord (GPLv3). Playback engine: Lucid Hikari.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingsHeading(title: String) {
    Text(
        title,
        color = MusicAccent2,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

@Composable
private fun <T> SettingsChoiceLine(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (text, value) ->
                val active = value == selected
                Text(
                    text,
                    color = if (active) Color(0xFF101218) else Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) MusicAccent else Color.White.copy(alpha = 0.07f))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsInputLine(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    Column {
        Text(
            label,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 12.5.sp,
            ),
            cursorBrush = SolidColor(MusicAccent),
            visualTransformation = if (password) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

@Composable
private fun SettingsToggleLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 10.5.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
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
