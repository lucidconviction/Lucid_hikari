package com.hikari.app.ui.screens

import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavHostController
import com.hikari.app.HikariApp
import com.hikari.app.R
import com.hikari.app.data.IptvChannel
import com.hikari.app.data.MultiViewSession
import com.hikari.app.data.QuickChannel
import com.hikari.app.data.QuickChannelList
import com.hikari.app.data.StreamValidationStore
import com.hikari.app.player.PlayerPool
import com.hikari.app.providers.IptvProvider
import com.hikari.app.ui.navigation.Routes
import com.hikari.app.ui.screens.IptvNutZScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * F4 — multi-view grid port of Nuvio's MultiWindowGrid.
 *
 * Uses Lucid's PlayerPool (ExoPlayer) and MultiWindowStore for per-stream
 * volume, pause, audio focus, resize mode and slot management. Full cell
 * options panel (volume presets, scaling, swap, quick channels, refresh) and
 * QuickChannelOverlay are included.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiViewScreen(
    sessions: List<MultiViewSession>,
    layout: MultiViewLayout = MultiViewLayout.V6_2x3,
    onBack: () -> Unit,
    nav: NavHostController? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val pool = remember(context) { PlayerPool(context) }

    // Init stream validation store from cache dir
    val cacheDir = app.cacheDir
    LaunchedEffect(Unit) { StreamValidationStore.ensureLoaded(cacheDir) }

    LaunchedEffect(sessions) {
        if (MultiWindowStore.allStreams.isEmpty() && sessions.isNotEmpty()) {
            MultiWindowStore.clear()
            val count = sessions.size.coerceIn(1, 9)
            for (i in sessions.indices) {
                val s = sessions[i]
                MultiWindowStore.addToSlot(s.name, s.url, slotIndex = i)
            }
        }
        MultiWindowStore.setLayout(layout)
    }

    DisposableEffect(Unit) {
        onDispose {
            pool.releaseAll(); MultiWindowStore.clear()
            StreamValidationStore.save(cacheDir)
        }
    }

    MultiViewContent(
        cacheDir = cacheDir,
        onBack = onBack,
        onBrowseIptv = { nav?.navigate(Routes.IPTV) },
        onBrowseQuickChannels = { /* handled by QuickChannelSheet */ },
        onFullscreen = { stream -> openChannelFullscreen(context, stream.url) },
        onPauseAll = { togglePauseAll(pool) },
        onMuteAll = { toggleMuteAll(pool) },
        onRefreshAll = { refreshAll(pool) },
        onCloseAll = { pool.releaseAll(); MultiWindowStore.clear() },
        pool = pool,
        nav = nav,
    )
}

private fun openChannelFullscreen(context: android.content.Context, url: String) {
    // Mark the stream as fullscreen so the grid can pause siblings
    val stream = MultiWindowStore.allStreams.find { it.url == url }
    if (stream != null) MultiWindowStore.setFullscreenStream(stream.id)

    val isM3u8 = url.endsWith(".m3u8", ignoreCase = true)
    val name = url.substringAfterLast('/')
    val sources = org.json.JSONArray().apply {
        put(org.json.JSONObject()
            .put("name", name)
            .put("url", url)
            .put("isM3u8", isM3u8)
            .put("provider", "")
            .put("providerId", "")
            .put("providerName", "")
            .put("details", "")
            .put("ytId", "")
            .put("isTorrent", false)
            .put("isMpd", false)
        )
    }
    val intent = android.content.Intent(context, com.hikari.app.player.PlayerActivity::class.java).apply {
        putExtra("title", url)
        putExtra("sources", sources.toString())
    }
    context.startActivity(intent)
}

private fun togglePauseAll(pool: PlayerPool) {
    val allPaused = MultiWindowStore.allStreams.all { MultiWindowStore.isPaused(it.id) }
    for (stream in MultiWindowStore.allStreams) {
        val player = pool.get(stream.slotIndex)
        if (allPaused) {
            player.playWhenReady = true
            MultiWindowStore.setPaused(stream.id, false)
        } else {
            player.pause()
            MultiWindowStore.setPaused(stream.id, true)
        }
    }
}

private fun toggleMuteAll(pool: PlayerPool) {
    val allMuted = MultiWindowStore.allStreams.all { MultiWindowStore.getVolume(it.id) == 0f }
    for (stream in MultiWindowStore.allStreams) {
        val vol = if (allMuted) 1f else 0f
        pool.get(stream.slotIndex).volume = vol
        MultiWindowStore.setVolume(stream.id, vol)
        if (vol > 0f) MultiWindowStore.setAudioFocus(stream.id)
    }
}

private fun refreshAll(pool: PlayerPool) {
    for (stream in MultiWindowStore.allStreams) {
        val player = pool.get(stream.slotIndex)
        player.setMediaItem(MediaItem.fromUri(stream.url))
        player.volume = MultiWindowStore.getVolume(stream.id)
        player.playWhenReady = !MultiWindowStore.isPaused(stream.id)
        player.prepare()
    }
}

// ── Color tokens ────────────────────────────────────────────────────────────
private val Primary = Color(0xFFadc6ff)
private val PrimaryContainer = Color(0xFF4b8eff)
private val SurfaceContainer = Color(0xFF201f1f)
private val SurfaceContainerLow = Color(0xFF1c1b1b)
private val SurfaceContainerHighest = Color(0xFF353534)
private val OnSurface = Color(0xFFe5e2e1)
private val OnSurfaceVariant = Color(0xFFc1c6d7)
private val OutlineVariant = Color(0xFF414755)
private val ErrorColor = Color(0xFFffb4ab)
private val ErrorContainerAlpha = Color(0x33ffb4ab)
private val ErrorBorderAlpha = Color(0x4dffb4ab)
private val GlassBg = Color(0x991E1E1E)
private val SurfaceCard = Color(0xFF2A2A2A)
private val SurfaceLow = Color(0xFF1C1C1C)
private val Accent = Color(0xFF4b8eff)
private val GreenLive = Color(0xFF4CAF50)

private val scaleLabels = listOf("Fill", "Fit", "16:9", "4:3", "Zoom")
private val scaleModes = listOf(RESIZE_FILL, RESIZE_FIT, RESIZE_FIXED_WIDTH, RESIZE_FIXED_HEIGHT, RESIZE_ZOOM)

private val volumeSteps = listOf(
    "Mute" to 0f, "15" to 0.15f, "30" to 0.30f, "45" to 0.45f,
    "70" to 0.70f, "85" to 0.85f, "Max" to 1.0f,
)

private val qcTabs = listOf("All", "US", "UK", "CA", "Premium", "Sports", "News", "Custom")

private object QuickChannelMatchCache {
    val matches = mutableMapOf<String, Pair<List<IptvChannel>, Map<String, Boolean>>>()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiViewContent(
    cacheDir: java.io.File,
    onBack: () -> Unit,
    onBrowseIptv: () -> Unit,
    onBrowseQuickChannels: () -> Unit,
    onFullscreen: (WindowStream) -> Unit,
    onPauseAll: () -> Unit,
    onMuteAll: () -> Unit,
    onRefreshAll: () -> Unit,
    onCloseAll: () -> Unit,
    pool: PlayerPool,
    nav: NavHostController? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val streams = MultiWindowStore.allStreams

    // State for cell options sheet
    var cellOptionsStream by remember { mutableStateOf<WindowStream?>(null) }

    // State for quick channel sheet (from Browse IPTV empty state)
    var quickChannelSheetOpen by remember { mutableStateOf(false) }

    if (streams.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("MW", color = OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text("No streams added", color = OnSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = { quickChannelSheetOpen = true },
                    shape = RoundedCornerShape(50),
                    color = PrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Browse IPTV Channels", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onBrowseIptv,
                    shape = RoundedCornerShape(50),
                    color = SurfaceContainer,
                    modifier = Modifier.padding(top = 8.dp)
                        .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(50)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("IPTV Browser", color = OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(9999.dp)).background(SurfaceContainer)
                        .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text("★ Saved Layouts", color = OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }

        // Quick channel sheet for "Browse IPTV Channels"
        if (quickChannelSheetOpen) {
            QuickChannelsSheet(
                currentSlotIndex = -1,
                onAddStream = { channel, slotIndex ->
                    MultiWindowStore.addToSlot(channel.name, channel.url, channel.logo, slotIndex)
                },
                onDismiss = { quickChannelSheetOpen = false },
            )
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isPortrait = maxWidth < maxHeight
        val isTablet = maxWidth >= 600.dp
        val maxSlots = if (isTablet) 9 else 6
        val count = streams.size.coerceAtMost(maxSlots)
        val layout = MultiWindowStore.resolveLayout(count, isPortrait, isTablet)
        val slots = layout.calculateSlots(count)
        val totalRows = (slots.maxOfOrNull { it.row + it.rowSpan } ?: 1).coerceAtLeast(1)
        val totalCols = (slots.maxOfOrNull { it.col + it.colSpan } ?: 1).coerceAtLeast(1)

        // Fullscreen coordination: when entering fullscreen, pause all other
        // grid players so only the fullscreen stream has audio.  When returning
        // (the composable recomposes), resume them.
        val fullscreenStreamId = MultiWindowStore.fullscreenStreamId
        LaunchedEffect(fullscreenStreamId) {
            if (fullscreenStreamId != null) {
                // Pause every player except the one going fullscreen
                for (s in MultiWindowStore.allStreams) {
                    if (s.id != fullscreenStreamId) {
                        val p = pool.get(s.slotIndex)
                        p.pause()
                        MultiWindowStore.setPaused(s.id, true)
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                // Returning from fullscreen — resume all paused players
                for (s in MultiWindowStore.allStreams) {
                    if (MultiWindowStore.isPaused(s.id)) {
                        val p = pool.get(s.slotIndex)
                        p.playWhenReady = true
                        MultiWindowStore.setPaused(s.id, false)
                    }
                }
                MultiWindowStore.clearFullscreen()
            }
        }

        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MultiNutz", color = Primary, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = -0.3.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(9999.dp)).background(ErrorContainerAlpha)
                            .border(0.5.dp, ErrorBorderAlpha, RoundedCornerShape(9999.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(ErrorColor))
                            Text("${streams.size} ACTIVE", color = ErrorColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // PortalNutz button — open the portal discovery feature
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(SurfaceContainer)
                            .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                            .clickable {
                nav?.navigate(Routes.IPTV_NUTZ) ?: run {
                    val intent = android.content.Intent(context, android.content.Intent::class.java).apply {
                        action = "com.hikari.app.iptv.nutz"
                        putExtra("action", "open_nutz")
                    }
                    context.startActivity(intent)
                }
            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("★ PortalNutz", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
            }

            // Pill row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val validLayouts = getValidLayouts(count, isPortrait, isTablet)
                val isAuto = !MultiWindowStore.isLayoutLocked()
                Box(
                    Modifier.clip(RoundedCornerShape(9999.dp))
                        .background(if (isAuto) PrimaryContainer else SurfaceContainer)
                        .border(if (isAuto) 0.dp else 0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                        .clickable { MultiWindowStore.setAutoLayout() }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("Auto", color = if (isAuto) Color.White else OnSurfaceVariant,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                validLayouts.forEach { l ->
                    val isSelected = MultiWindowStore.currentLayout() == l || (isAuto && defaultLayout(count, isPortrait, isTablet) == l)
                    Box(
                        Modifier.clip(RoundedCornerShape(9999.dp))
                            .background(if (isSelected) PrimaryContainer else SurfaceContainer)
                            .border(if (isSelected) 0.dp else 0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                            .clickable { MultiWindowStore.setLayout(l) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(l.label, color = if (isSelected) Color.White else OnSurfaceVariant,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
                Spacer(Modifier.width(4.dp))
                val allMuted = streams.all { MultiWindowStore.getVolume(it.id) == 0f }
                Box(
                    Modifier.clip(RoundedCornerShape(9999.dp))
                        .background(if (allMuted) SurfaceContainer else PrimaryContainer)
                        .border(if (!allMuted) 0.dp else 0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                        .clickable { onMuteAll() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(if (allMuted) "Sound All" else "Mute All",
                        color = if (allMuted) OnSurfaceVariant else Color.White,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                val allPaused = streams.all { MultiWindowStore.isPaused(it.id) }
                Box(
                    Modifier.clip(RoundedCornerShape(9999.dp))
                        .background(if (allPaused) SurfaceContainer else PrimaryContainer)
                        .border(if (!allPaused) 0.dp else 0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                        .clickable { onPauseAll() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(if (allPaused) "Play All" else "Pause All",
                        color = if (allPaused) OnSurfaceVariant else Color.White,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(9999.dp)).background(SurfaceContainer)
                        .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                        .clickable { onRefreshAll() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("Refresh All", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(9999.dp)).background(SurfaceContainer)
                        .border(0.5.dp, ErrorBorderAlpha, RoundedCornerShape(9999.dp))
                        .clickable { onCloseAll() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("Close All", color = ErrorColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }

            // Grid
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                var gridPos = 0
                for (row in 0 until totalRows) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0 until totalCols) {
                            val slot = slots.find { it.row == row && it.col == col }
                            if (slot != null) {
                                val stream = streams.getOrNull(slot.index)
                                if (stream != null) {
                                    gridPos++
                                    val isAudioFocused = MultiWindowStore.isAudioFocused(stream.id)
                                    key(stream.id) {
                                        val occupiedSlots = streams.map { it.slotIndex }.sorted()
                                        val curSlot = stream.slotIndex
                                        val curIdx = occupiedSlots.indexOf(curSlot)
                                        val prevSlot = if (curIdx > 0) occupiedSlots[curIdx - 1] else null
                                        val nextSlot = if (curIdx >= 0 && curIdx < occupiedSlots.size - 1) occupiedSlots[curIdx + 1] else null
                                        VideoCell(
                                            stream = stream,
                                            gridPosition = gridPos,
                                            isAudioFocused = isAudioFocused,
                                            onRemove = { MultiWindowStore.remove(stream.id) },
                                            onFullscreen = { onFullscreen(stream) },
                                            onCompletion = {
                                                val next = MultiWindowStore.nextInSlot(stream.slotIndex)
                                                if (next != null) {
                                                    MultiWindowStore.remove(stream.id)
                                                    MultiWindowStore.addToSlot(next.name, next.url, slotIndex = stream.slotIndex)
                                                } else {
                                                    MultiWindowStore.remove(stream.id)
                                                }
                                            },
                                            onSwap = { targetSlot -> MultiWindowStore.swapSlots(curSlot, targetSlot) },
                                            onOptionsClick = { cellOptionsStream = stream },
                                            pool = pool,
                                            modifier = Modifier.weight((slot.colSpan * totalRows).toFloat()).padding(4.dp),
                                        )
                                    }
                                } else {
                                    Box(
                                        Modifier.weight((slot.colSpan * totalRows).toFloat()).padding(4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceContainerLow.copy(alpha = 0.5f))
                                            .border(1.dp, OutlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Cell options bottom sheet
    cellOptionsStream?.let { stream ->
        MultiWindowCellOptions(
            stream = stream,
            pool = pool,
            volume = MultiWindowStore.getVolume(stream.id),
            onVolumeChange = { vol ->
                pool.get(stream.slotIndex).volume = vol
                MultiWindowStore.setVolume(stream.id, vol)
                if (vol > 0f) MultiWindowStore.setAudioFocus(stream.id)
            },
            onSwap = { targetSlot -> MultiWindowStore.swapSlots(stream.slotIndex, targetSlot) },
            onRefresh = {
                val player = pool.get(stream.slotIndex)
                player.setMediaItem(MediaItem.fromUri(stream.url))
                player.volume = MultiWindowStore.getVolume(stream.id)
                player.playWhenReady = !MultiWindowStore.isPaused(stream.id)
                player.prepare()
            },
            onFullscreen = { onFullscreen(stream) },
            onClose = { MultiWindowStore.remove(stream.id) },
            onDismiss = { cellOptionsStream = null },
        )
    }

    // Quick channel sheet (opened from Browse IPTV button)
    if (quickChannelSheetOpen) {
        QuickChannelsSheet(
            currentSlotIndex = MultiWindowStore.nextAvailableSlot() ?: 0,
            onAddStream = { channel, slotIndex ->
                MultiWindowStore.addToSlot(channel.name, channel.url, channel.logo, slotIndex)
            },
            onDismiss = { quickChannelSheetOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoCell(
    stream: WindowStream,
    gridPosition: Int,
    isAudioFocused: Boolean,
    onRemove: () -> Unit,
    onFullscreen: () -> Unit,
    onCompletion: () -> Unit,
    onSwap: (Int) -> Unit,
    onOptionsClick: () -> Unit,
    pool: PlayerPool,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val player: ExoPlayer = remember(stream.id) {
        pool.get(stream.slotIndex).also {
            it.setMediaItem(MediaItem.fromUri(stream.url))
            it.volume = MultiWindowStore.getVolume(stream.id)
            it.playWhenReady = !MultiWindowStore.isPaused(stream.id)
            it.videoScalingMode = if (MultiWindowStore.getResizeMode(stream.id) == RESIZE_ZOOM)
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING else C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            it.prepare()
        }
    }

    // Track resize mode changes - store current value in a mutableState
    var resizeMode by remember { mutableStateOf(MultiWindowStore.getResizeMode(stream.id)) }
    LaunchedEffect(stream.id) {
        snapshotFlow { MultiWindowStore.getResizeMode(stream.id) }.collect { newMode ->
            resizeMode = newMode
            player.videoScalingMode = if (newMode == RESIZE_ZOOM)
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING else C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    DisposableEffect(stream.id) { onDispose { /* player stays in pool */ } }

    var isAudioActive by remember { mutableStateOf(MultiWindowStore.getVolume(stream.id) > 0f) }
    var isPlaying by remember(stream.id) { mutableStateOf(!MultiWindowStore.isPaused(stream.id)) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var seekProgress by remember { mutableStateOf(0f) }

    val borderModifier = if (isAudioFocused) {
        Modifier.border(2.dp, Primary, RoundedCornerShape(12.dp))
    } else {
        Modifier.border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
            seekProgress = (elapsedSeconds % 3600).toFloat() / 3600f
        }
    }

    fun onInteraction() {
        MultiWindowStore.setAudioFocus(stream.id)
    }

    val isStreamAlive = !StreamValidationStore.isKnownDeadSync(stream.url)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .then(borderModifier),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = lucidResizeModeToPlayerView(MultiWindowStore.getResizeMode(stream.id))
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pv -> pv.resizeMode = lucidResizeModeToPlayerView(resizeMode) },
        )

        // Overlay with always-visible, always-tappable controls.
        // NOTE: no .graphicsLayer alpha fade — fading the container makes the
        // buttons untouchable.  Controls stay visible and functional at all times.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(GlassBg.copy(alpha = 0.35f)))

            // Top-left badge with stream validation green dot
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp)
                    .clip(RoundedCornerShape(4.dp)).background(PrimaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isStreamAlive) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(GreenLive))
                    }
                    Text("$gridPosition | ${stream.name}", color = Color.White,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Top-right fullscreen
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp)
                    .size(28.dp).clip(CircleShape)
                    .background(SurfaceContainerHighest.copy(alpha = 0.85f))
                    .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), CircleShape)
                    .clickable { onFullscreen(); onInteraction() },
                contentAlignment = Alignment.Center,
            ) {
                Text("⛶", color = OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Center play/pause
            Box(
                Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape)
                    .background(PrimaryContainer.copy(alpha = 0.2f))
                    .border(0.5.dp, Primary.copy(alpha = 0.4f), CircleShape)
                    .clickable {
                        isPlaying = !isPlaying
                        if (isPlaying) {
                            player.play()
                            MultiWindowStore.setPaused(stream.id, false)
                        } else {
                            player.pause()
                            MultiWindowStore.setPaused(stream.id, true)
                        }
                        onInteraction()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause", tint = Primary, modifier = Modifier.size(18.dp))
            }

            // Bottom row: options (⋮) + swap arrows (← →)
            val occupiedSlots = MultiWindowStore.allStreams.map { it.slotIndex }.sorted()
            val curSlot = stream.slotIndex
            val curIdx = occupiedSlots.indexOf(curSlot)
            val prevSlot = if (curIdx > 0) occupiedSlots[curIdx - 1] else null
            val nextSlot = if (curIdx >= 0 && curIdx < occupiedSlots.size - 1) occupiedSlots[curIdx + 1] else null

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Options (⋮)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceContainerHighest.copy(alpha = 0.85f))
                        .clickable(onClick = { onOptionsClick(); onInteraction() })
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋮", color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Swap left arrow
                if (prevSlot != null) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHighest.copy(alpha = 0.85f))
                            .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), CircleShape)
                            .clickable { onSwap(prevSlot); onInteraction() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("←", color = OnSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Swap right arrow
                if (nextSlot != null) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHighest.copy(alpha = 0.85f))
                            .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), CircleShape)
                            .clickable { onSwap(nextSlot); onInteraction() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("→", color = OnSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Bottom-right volume chip
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(9999.dp)).background(SurfaceContainerHighest.copy(alpha = 0.85f))
                    .border(0.5.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(9999.dp))
                    .clickable {
                        isAudioActive = !isAudioActive
                        val vol = if (isAudioActive) 1f else 0f
                        player.volume = vol
                        MultiWindowStore.setVolume(stream.id, vol)
                        if (isAudioActive) {
                            MultiWindowStore.setAudioFocus(stream.id)
                        }
                        onInteraction()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(if (isAudioActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Audio", tint = if (isAudioActive) Primary else OnSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                    Text("${(MultiWindowStore.getVolume(stream.id) * 100).toInt()}%",
                        color = if (isAudioActive) OnSurface else OnSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Progress bar
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                    .background(SurfaceContainerHighest.copy(alpha = 0.6f)),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(seekProgress.coerceIn(0f, 1f))
                        .background(PrimaryContainer),
                )
                if (isPlaying) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).size(6.dp).clip(CircleShape)
                            .background(PrimaryContainer),
                    )
                }
            }
        }

    }
}

/** Maps Lucid resize mode constants to PlayerView/AspectRatioFrameLayout values. */
private fun lucidResizeModeToPlayerView(mode: Int): Int = when (mode) {
    RESIZE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    RESIZE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    RESIZE_FIXED_WIDTH -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    RESIZE_FIXED_HEIGHT -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
    RESIZE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

/**
 * Full cell options bottom sheet — port of Nuvio's MultiWindowCellOptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiWindowCellOptions(
    stream: WindowStream,
    pool: PlayerPool,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onSwap: ((Int) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onFullscreen: (() -> Unit)? = null,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var overlayMode by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        when (overlayMode) {
            "swap" -> SwapPositionOverlay(
                currentSlotIndex = stream.slotIndex,
                onSelect = { targetSlot ->
                    onSwap?.invoke(targetSlot)
                    overlayMode = null
                    onDismiss()
                },
                onBack = { overlayMode = null },
            )
            "quick" -> QuickChannelOverlay(
                currentSlotIndex = stream.slotIndex,
                onSelect = { channel ->
                    // Update the stream's URL
                    MultiWindowStore.remove(stream.id)
                    MultiWindowStore.addToSlot(channel.name, channel.url, channel.logo, stream.slotIndex)
                    overlayMode = null
                    onDismiss()
                },
                onBack = { overlayMode = null },
            )
            else -> {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stream.name, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Slot ${stream.slotIndex + 1}", color = OnSurfaceVariant, fontSize = 13.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceCard).clickable { overlayMode = "quick" }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                            Text("Quick Channels", color = OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (onSwap != null) {
                            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceCard).clickable { overlayMode = "swap" }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                                Text("Swap", color = OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = OnSurfaceVariant.copy(alpha = 0.15f))

                    Text("Volume", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        volumeSteps.forEach { (label, level) ->
                            val isActive = volume == level
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (isActive) PrimaryContainer else SurfaceCard)
                                    .clickable { onVolumeChange(level) }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = if (isActive) Color.White else OnSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = OnSurfaceVariant.copy(alpha = 0.15f))

                    Text("Scaling", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        scaleLabels.zip(scaleModes).forEach { (label, mode) ->
                            val currentMode = MultiWindowStore.getResizeMode(stream.id)
                            val isActive = currentMode == mode
                            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (isActive) PrimaryContainer else SurfaceCard).clickable {
                                MultiWindowStore.setResizeMode(stream.id, mode)
                            }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(label, color = if (isActive) Color.White else OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = OnSurfaceVariant.copy(alpha = 0.15f))

                    if (onRefresh != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceCard).clickable(onClick = onRefresh).padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Refresh Stream", color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("↻", color = Primary, fontSize = 16.sp)
                        }
                    }

                    if (onFullscreen != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceCard).clickable(onClick = onFullscreen).padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Fullscreen", color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("⛶", color = Primary, fontSize = 16.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceCard).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Close Channel", color = ErrorColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("✕", color = ErrorColor, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Swap position overlay — shows 9 slots, current slot is dimmed, occupied are selectable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapPositionOverlay(
    currentSlotIndex: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val maxSlots = 9
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceCard).clickable(onClick = onBack).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("← Back", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text("Swap Slot", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Select target slot to swap with:", color = OnSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (slot in 0 until maxSlots) {
                if (slot == currentSlotIndex) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SurfaceCard).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${slot + 1}", color = OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    val hasStream = MultiWindowStore.getStreamsForSlot(slot) != null
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (hasStream) Primary else SurfaceLow).clickable(enabled = hasStream) { onSelect(slot) }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${slot + 1}", color = if (hasStream) Color.White else OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Quick channel overlay with category tabs and channel matching.
 * Uses IptvProvider.channels() to scan for matches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickChannelOverlay(
    currentSlotIndex: Int,
    onSelect: (IptvChannel) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val scope = rememberCoroutineScope()
    var qcFilter by remember { mutableStateOf("All") }
    var selectedQc by remember { mutableStateOf<QuickChannel?>(null) }
    var loadState by remember { mutableStateOf<QcLoadState>(QcLoadState.Idle) }
    var aliveByUrl by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var qcMatches by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }

    val filtered = remember(qcFilter) {
        when (qcFilter) {
            "All" -> QuickChannelList.all
            "US" -> QuickChannelList.getChannelsForCategory("us")
            "UK" -> QuickChannelList.getChannelsForCategory("uk")
            "CA" -> QuickChannelList.getChannelsForCategory("ca")
            "Premium" -> QuickChannelList.getChannelsForCategory("premium")
            "Sports" -> QuickChannelList.getChannelsForCategory("sports")
            "News" -> QuickChannelList.getChannelsForCategory("news")
            "Custom" -> QuickChannelList.getCustomChannels(app.cacheDir, emptyList())
            else -> QuickChannelList.all
        }
    }

    LaunchedEffect(selectedQc) {
        val qc = selectedQc ?: return@LaunchedEffect
        val cached = QuickChannelMatchCache.matches[qc.displayName]
        if (cached != null) {
            aliveByUrl = cached.second
            qcMatches = cached.first
            loadState = QcLoadState.Success(cached.first)
            return@LaunchedEffect
        }
        loadState = QcLoadState.Loading
        loadState = try {
            val matches = withContext(Dispatchers.IO) {
                val allChannels = mutableListOf<IptvChannel>()
                for (p in app.providers.providers.value.filterIsInstance<IptvProvider>()) {
                    runCatching { allChannels.addAll(p.channels(force = true)) }.getOrNull()
                }
                allChannels.filter { ch -> QuickChannelList.matches(qc, ch) }
            }
            if (matches.isEmpty()) {
                QcLoadState.Error("No sources found for \"${qc.displayName}\"")
            } else {
                // Probe each URL with a short HTTP HEAD/GET — use StreamValidationStore
                // so results persist across sessions.
                val probeResults = withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val toProbe = matches.map { it.url }.filter { url ->
                        !StreamValidationStore.isKnownAliveSync(url) &&
                            !StreamValidationStore.isKnownDeadSync(url)
                    }.take(30) // cap to avoid flooding
                    val results = mutableMapOf<String, Boolean>()
                    for (url in toProbe) {
                        val ok = try {
                            com.hikari.app.net.Http.get(url, mapOf("User-Agent" to com.hikari.app.net.Http.IPTV_UA)).use { resp ->
                                resp.isSuccessful || resp.code == 301 || resp.code == 302
                            }
                        } catch (_: Exception) { false }
                        results[url] = ok
                        if (ok) StreamValidationStore.markAlive(url) else StreamValidationStore.markDead(setOf(url))
                    }
                    // Merge with cached results for all matched channels
                    matches.associate { ch ->
                        ch.url to (results[ch.url]
                            ?: StreamValidationStore.isKnownAliveSync(ch.url))
                    }
                }
                aliveByUrl = probeResults
                QuickChannelMatchCache.matches[qc.displayName] = matches to probeResults
                QcLoadState.Success(matches)
            }
        } catch (e: Exception) {
            QcLoadState.Error(e.message ?: "Failed to load sources")
        }
    }

    val onRetry: () -> Unit = {
        selectedQc?.let { qc -> QuickChannelMatchCache.matches.remove(qc.displayName) }
        val current = selectedQc
        selectedQc = null
        selectedQc = current
    }

    when (val state = loadState) {
        is QcLoadState.Loading -> {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.align(Alignment.Start).clip(RoundedCornerShape(8.dp)).background(SurfaceCard).clickable(onClick = { selectedQc = null; loadState = QcLoadState.Idle }).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("← Back", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(48.dp))
                Text("Resolving ${selectedQc?.displayName}...", color = OnSurfaceVariant, fontSize = 14.sp)
            }
        }
        is QcLoadState.Error -> {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.align(Alignment.Start).clip(RoundedCornerShape(8.dp)).background(SurfaceCard).clickable(onClick = { selectedQc = null; loadState = QcLoadState.Idle }).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("← Back", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(48.dp))
                Text("⚠", color = ErrorColor, fontSize = 36.sp)
                Spacer(Modifier.height(12.dp))
                Text(state.message, color = OnSurface, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(16.dp))
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(PrimaryContainer).clickable(onClick = onRetry).padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Text("Retry", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        is QcLoadState.Success -> {
            val qc = selectedQc ?: return
            QuickChannelMatchOverlay(
                quickChannel = qc,
                matchedChannels = state.channels,
                aliveByUrl = aliveByUrl,
                currentSlotIndex = currentSlotIndex,
                onSelect = { ch ->
                    if (currentSlotIndex >= 0) {
                        MultiWindowStore.addToSlot(ch.name, ch.url, ch.logo, currentSlotIndex)
                    } else {
                        onSelect(ch)
                    }
                },
                onBack = { selectedQc = null; loadState = QcLoadState.Idle },
            )
        }
        QcLoadState.Idle -> {
            var showAddCustom by remember { mutableStateOf(false) }
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(480.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceCard).clickable(onClick = onBack).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text("← Back", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Quick Channels", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    // Plus sign to add a custom quick channel
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .clickable { showAddCustom = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${filtered.size}", color = OnSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(qcTabs) { tab ->
                        val isActive = qcFilter == tab
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (isActive) PrimaryContainer else SurfaceCard).clickable { qcFilter = tab }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(tab, color = if (isActive) Color.White else OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.displayName }) { qc ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLow).clickable { selectedQc = qc }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(qc.displayName, color = OnSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("▸", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text("No quick channels match filter", color = OnSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                        }
                    }
                }
                // Show the add-custom-channel sheet when the + button is tapped
                if (showAddCustom) {
                    AddCustomQuickChannelSheet(
                        cacheDir = app.cacheDir,
                        onDismiss = { showAddCustom = false },
                        onAdded = { /* refresh filter */ },
                    )
                }
            }
        }
    }
}

private sealed class QcLoadState {
    data object Idle : QcLoadState()
    data object Loading : QcLoadState()
    data class Success(val channels: List<IptvChannel>) : QcLoadState()
    data class Error(val message: String) : QcLoadState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickChannelMatchOverlay(
    quickChannel: QuickChannel,
    matchedChannels: List<IptvChannel>,
    aliveByUrl: Map<String, Boolean>,
    currentSlotIndex: Int,
    onSelect: (IptvChannel) -> Unit,
    onBack: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    // Hide definitively dead channels; put alive ones at top, unknown after.
    val sortedChannels = remember(matchedChannels, aliveByUrl) {
        val alive = mutableListOf<IptvChannel>()
        val unknown = mutableListOf<IptvChannel>()
        for (ch in matchedChannels) {
            val isAlive = aliveByUrl[ch.url] == true || StreamValidationStore.isKnownAliveSync(ch.url)
            val isDead = aliveByUrl[ch.url] == false || StreamValidationStore.isKnownDeadSync(ch.url)
            if (isDead) continue
            if (isAlive) alive.add(ch) else unknown.add(ch)
        }
        alive + unknown
    }

    val filtered = remember(sortedChannels, searchQuery) {
        var result = sortedChannels
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter { it.name.lowercase().contains(q) }
        }
        result
    }

    val workingCount = sortedChannels.count { aliveByUrl[it.url] == true || StreamValidationStore.isKnownAliveSync(it.url) }
    val checkingCount = sortedChannels.count { aliveByUrl[it.url] == null && !StreamValidationStore.isKnownAliveSync(it.url) }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(480.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceCard).clickable(onClick = onBack).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("← Back", color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(quickChannel.displayName, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("$workingCount working${if (checkingCount > 0) " · checking $checkingCount" else ""}", color = OnSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        androidx.compose.material3.OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search channels...", color = OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 13.sp) },
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = OnSurface),
        )

        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.url }) { ch ->
                val isAlive = aliveByUrl[ch.url] == true || StreamValidationStore.isKnownAliveSync(ch.url)
                val isDead = aliveByUrl[ch.url] == false || StreamValidationStore.isKnownDeadSync(ch.url)
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceLow).clickable { if (!isDead) onSelect(ch) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ch.name, color = if (isDead) OnSurfaceVariant.copy(alpha = 0.4f) else OnSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ch.group.isNotBlank()) {
                            Text(ch.group, color = OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isDead) {
                        // Dead channels shown dimmed with ✗
                        Text("✗", color = ErrorColor.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else if (isAlive) {
                        Text("✓", color = GreenLive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(OnSurfaceVariant.copy(alpha = 0.4f)))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Slot ${currentSlotIndex + 1}", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (checkingCount > 0) "Checking ${quickChannel.displayName} streams..." else "No channels match \"${quickChannel.displayName}\"",
                        color = OnSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Add-custom quick channel bottom sheet — opened from the "+" button in
 * QuickChannelOverlay. Lets the user create a named search pattern that is
 * persisted alongside the built-in list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomQuickChannelSheet(
    cacheDir: java.io.File,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("US") }
    var tag by remember { mutableStateOf("entertainment") }
    var saved by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!saved) onDismiss() },
        sheetState = sheetState,
        containerColor = SurfaceContainer.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Add Quick Channel", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))

            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Channel name", color = OnSurfaceVariant) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = OnSurface),
            )
            Spacer(Modifier.height(8.dp))

            androidx.compose.material3.OutlinedTextField(
                value = aliases, onValueChange = { aliases = it },
                label = { Text("Search aliases (comma-separated)", color = OnSurfaceVariant) },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = OnSurface),
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Region:", color = OnSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("US", "UK", "CA", "EU", "AU", "LA", "IN", "PK", "Custom")) { r ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (region == r) PrimaryContainer else SurfaceCard)
                                .clickable { region = r }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(r, color = if (region == r) Color.White else OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Tag:", color = OnSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("entertainment", "news", "sports", "premium", "kids", "broadcast")) { t ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (tag == t) PrimaryContainer else SurfaceCard)
                                .clickable { tag = t }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(t, color = if (tag == t) Color.White else OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (name.isNotBlank() && aliases.isNotBlank()) Primary else Primary.copy(alpha = 0.3f))
                    .clickable(enabled = name.isNotBlank() && aliases.isNotBlank()) {
                        val aliasList = aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val qc = QuickChannel(
                            displayName = name.trim(),
                            aliases = aliasList,
                            regions = if (region == "Custom") emptyList() else listOf(region),
                            tags = listOf(tag),
                        )
                        QuickChannelList.addCustomChannel(cacheDir, qc)
                        saved = true
                        onAdded()
                        onDismiss()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Save Channel", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Quick channels sheet — for the "Browse IPTV Channels" button from empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickChannelsSheet(
    currentSlotIndex: Int,
    onAddStream: (IptvChannel, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        QuickChannelOverlay(
            currentSlotIndex = currentSlotIndex,
            onSelect = { channel ->
                val slot = MultiWindowStore.nextAvailableSlot() ?: 0
                onAddStream(channel, slot)
                onDismiss()
            },
            onBack = onDismiss,
        )
    }
}

/**
 * Slot-picker bottom sheet — port of Nuvio's MultiWindowPositionPicker.
 * Shows a grid of available slots (0..8) — occupied ones show their current
 * stream, empty ones are selectable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotPickerBottomSheet(
    name: String,
    url: String,
    logo: String? = null,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSlots = if (LocalConfiguration.current.screenWidthDp >= 600) 9 else 6
    var selectedSlot by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer.copy(alpha = 0.97f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Add to MultiView", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(name, color = OnSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                (0 until maxSlots).forEach { slotIndex ->
                    val occupied = MultiWindowStore.getStreamsForSlot(slotIndex)
                    val isSelected = selectedSlot == slotIndex
                    val modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> PrimaryContainer
                                occupied != null -> SurfaceContainerHighest
                                else -> SurfaceContainerLow
                            }
                        )
                        .then(
                            if (isSelected) Modifier.border(2.dp, Primary, RoundedCornerShape(12.dp))
                            else if (occupied != null) Modifier.border(0.5.dp, OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable {
                            selectedSlot = if (isSelected) null else slotIndex
                        }
                        .padding(4.dp)
                    Box(modifier = modifier, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (occupied != null) {
                                Text(
                                    occupied.name.take(10),
                                    color = OnSurface,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                                Text(
                                    "Slot ${slotIndex + 1}",
                                    color = OnSurfaceVariant,
                                    fontSize = 7.sp,
                                )
                            } else {
                                Text(
                                    "Slot ${slotIndex + 1}",
                                    color = OnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Empty",
                                    color = OnSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 8.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedSlot != null) Primary else Primary.copy(alpha = 0.3f))
                        .clickable(enabled = selectedSlot != null) {
                            selectedSlot?.let { onSlotSelected(it) }
                            selectedSlot = null
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Add to Slot",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedSlot != null) Primary.copy(alpha = 0.7f) else Primary.copy(alpha = 0.15f))
                        .clickable(enabled = selectedSlot != null) {
                            selectedSlot?.let { onSlotSelected(it) }
                            selectedSlot = null
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Add & Go",
                        color = Color.White.copy(alpha = if (selectedSlot != null) 1f else 0.4f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
