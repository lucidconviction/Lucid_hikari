package com.hikari.app.youtube

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The YouTube player's controls, matching the reference TV client one-for-one.
 *
 * Top row: Back, title, Play/Pause.
 * Bottom row: Last, Subscribe, Watch later, Quality, Info, More, Comments, Like,
 * Dislike, Next. The seek bar sits below it.
 *
 * The ring is the part Hikari's YouTube player was missing: it used three
 * floating pills on the right and relied on the generic player bar for the
 * rest. Everything here talks to the same engine the YouTube tab uses (through
 * [YouTubeSession.feed]), so likes, subscriptions, playlists, comments,
 * suggestions and quality are the account's own state, not a local imitation.
 */
private enum class YtPlayerPanel { INFO, MORE, COMMENTS, SAVE, QUALITY }

private const val YT_SEEK_STEP_MS = 10_000L

// The reference remote's ring, in its own numbering: Back and Play on top, ten
// controls below them, the seek bar last.
private const val YT_BACK_CONTROL = 0
private const val YT_PLAY_CONTROL = 1
private const val YT_LAST_VIDEO_CONTROL = 2
private const val YT_SUBSCRIBE_CONTROL = 3
private const val YT_WATCH_LATER_CONTROL = 4
private const val YT_QUALITY_CONTROL = 5
private const val YT_INFO_CONTROL = 6
private const val YT_MORE_CONTROL = 7
private const val YT_COMMENTS_CONTROL = 8
private const val YT_LIKE_CONTROL = 9
private const val YT_DISLIKE_CONTROL = 10
private const val YT_NEXT_VIDEO_CONTROL = 11
private const val YT_ROTATE_CONTROL = 12
private const val YT_BACKGROUND_CONTROL = 13
private const val YT_SEEK_CONTROL = 14

/** An open side panel owns the remote: the ring behind it must not take focus. */
private val LocalYtPanelOpen = compositionLocalOf { false }

@Composable
fun YouTubePlayerOverlay(
    videoId: String,
    title: String,
    playerProvider: () -> Player?,
    onBack: () -> Unit,
    onSwitchVideo: (YouTubePlayRequest) -> Unit,
    onQuality: (Int) -> Unit,
    onSwipeStart: (Float) -> Unit = {},
    onSwipe: (Float) -> Unit = {},
    onSwipeEnd: () -> Unit = {},
    onRotate: () -> Unit = {},
    backgroundPlay: Boolean = false,
    onBackgroundPlay: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feed = YouTubeSession.feed

    var showControls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<YtPlayerPanel?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var qualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var quality by remember { mutableIntStateOf(0) }

    var info by remember(videoId) { mutableStateOf<YtVideoInfo?>(null) }
    var likeStatus by remember(videoId) { mutableIntStateOf(0) }
    var subscribed by remember(videoId) { mutableStateOf(false) }
    var channelId by remember(videoId) { mutableStateOf<String?>(null) }

    var playlists by remember { mutableStateOf<List<YtPlaylist>>(emptyList()) }
    var saveLoading by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var nextBusy by remember { mutableStateOf(false) }
    var lastBusy by remember { mutableStateOf(false) }
    var controlIndex by remember { mutableIntStateOf(YT_INFO_CONTROL) }
    var controlsTick by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { rootFocus.requestFocus() }
    }

    // Controls time out five seconds after the last key, exactly like the
    // reference. The root keeps focus while they are gone so the next press
    // reaches the ring handler instead of being lost on the video surface.
    LaunchedEffect(showControls, controlsTick, panel) {
        if (panel != null) return@LaunchedEffect
        if (showControls) {
            delay(5_000L)
            showControls = false
        } else {
            delay(30L)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(showControls, panel, videoId) {
        if (panel == null) {
            delay(40L)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_500L)
            notice = null
        }
    }

    // On a remote, Back closes the open panel first and leaves the player on
    // the next press, which is what the reference does.
    BackHandler(enabled = panel != null) { panel = null }

    // The player is media3's; poll it rather than trying to observe it through
    // Compose. 250ms is enough for a smooth seek bar and a live play icon.
    LaunchedEffect(playerProvider) {
        while (true) {
            val p = playerProvider()
            position = p?.currentPosition ?: 0L
            duration = p?.duration?.takeIf { it > 0L } ?: 0L
            playing = p?.isPlaying == true
            qualities = qualityHeights(p)
            delay(250L)
        }
    }

    LaunchedEffect(videoId) {
        panel = null
        showControls = true
        controlIndex = YT_INFO_CONTROL
        val loaded = runCatching { YouTubeVideoDetails.info(context, videoId) }.getOrNull()
        info = loaded
        likeStatus = loaded?.likeStatus ?: 0
        subscribed = loaded?.isSubscribed ?: false
        channelId = loaded?.channelId
    }

    // The controls time out; a panel keeps them on screen.
    fun poke() {
        showControls = true
        controlsTick++
    }

    fun seekBy(delta: Long) {
        val p = playerProvider() ?: return
        val total = duration.takeIf { it > 0L } ?: p.duration.coerceAtLeast(0L)
        val target = (p.currentPosition + delta).coerceAtLeast(0L)
            .let { if (total > 0L) it.coerceAtMost(total) else it }
        p.seekTo(target)
        position = target
        poke()
        controlIndex = YT_SEEK_CONTROL
    }

    fun switchTo(playable: YtPlayable) {
        onSwitchVideo(
            YouTubePlayRequest(
                url = playable.url,
                title = playable.title,
                poster = playable.poster,
                videoId = playable.videoId,
                startPositionMs = playable.startPositionMs,
                headers = playable.headers,
                isLive = playable.isLive,
            )
        )
    }

    fun react(target: Int) {
        val vm = feed
        if (vm == null || !vm.isSignedIn()) {
            notice = "Sign in to YouTube to like or dislike videos."
            return
        }
        val previous = likeStatus
        val next = if (previous == target) 0 else target
        likeStatus = next
        scope.launch {
            vm.setReaction(videoId, previous, next).onFailure {
                likeStatus = previous
                notice = it.message ?: "Couldn't update the reaction."
            }
        }
    }

    fun toggleSubscribe() {
        val vm = feed
        val channel = channelId ?: info?.channelId
        if (vm == null || !vm.isSignedIn()) {
            notice = "Sign in to YouTube to subscribe."
            return
        }
        if (channel.isNullOrBlank()) {
            notice = "This video doesn't name its channel."
            return
        }
        val next = !subscribed
        subscribed = next
        scope.launch {
            vm.setSubscribed(channel, next).onFailure {
                subscribed = !next
                notice = it.message ?: "Couldn't change the subscription."
            }
        }
    }

    fun openSave() {
        panel = YtPlayerPanel.SAVE
        poke()
        if (playlists.isEmpty() && !saveLoading) {
            val vm = feed
            if (vm == null) {
                notice = "Open this video from the YouTube tab to use playlists."
                return
            }
            saveLoading = true
            scope.launch {
                playlists = vm.playlists(videoId).getOrDefault(emptyList())
                saveLoading = false
            }
        }
    }

    fun goLast() {
        val vm = feed ?: return
        val queue = vm.queue.value
        val index = queue.indexOfFirst { it.videoId == videoId }
        val previous = if (index > 0) queue[index - 1] else null
        if (previous == null) {
            notice = "This is the first video in the list."
            return
        }
        lastBusy = true
        vm.activate(
            previous,
            queue,
            onError = {
                lastBusy = false
                notice = it
            },
            onReady = {
                lastBusy = false
                switchTo(it)
            },
        )
    }

    fun goNext() {
        val vm = feed ?: return
        nextBusy = true
        vm.playNext(
            videoId,
            onReady = {
                nextBusy = false
                switchTo(it)
            },
            onNone = {
                nextBusy = false
                notice = "No next video was found."
            },
        )
    }

    fun pickQuality(height: Int) {
        quality = height
        onQuality(height)
        notice = if (height <= 0) "Quality: Auto" else "Quality: ${height}p"
    }

    fun saveTo(playlist: YtPlaylist) {
        val vm = feed ?: return
        scope.launch {
            vm.addToPlaylist(playlist.id, videoId)
                .onSuccess {
                    notice = "Saved to ${playlist.title}"
                    panel = null
                }
                .onFailure { notice = it.message ?: "Couldn't save this video." }
        }
    }

    fun createPlaylist() {
        val vm = feed ?: return
        val name = newPlaylistName.trim()
        if (name.isEmpty()) return
        scope.launch {
            vm.createPlaylist(name, videoId)
                .onSuccess {
                    notice = "Saved to $name"
                    newPlaylistName = ""
                    panel = null
                }
                .onFailure { notice = it.message ?: "Couldn't create the playlist." }
        }
    }

    fun activateControl(index: Int) {
        poke()
        when (index) {
            YT_BACK_CONTROL -> onBack()
            YT_PLAY_CONTROL -> playerProvider()?.let { if (it.isPlaying) it.pause() else it.play() }
            YT_LAST_VIDEO_CONTROL -> goLast()
            YT_SUBSCRIBE_CONTROL -> toggleSubscribe()
            YT_WATCH_LATER_CONTROL -> {
                if (panel == YtPlayerPanel.SAVE) panel = null else openSave()
            }
            YT_QUALITY_CONTROL ->
                panel = if (panel == YtPlayerPanel.QUALITY) null else YtPlayerPanel.QUALITY
            YT_INFO_CONTROL ->
                panel = if (panel == YtPlayerPanel.INFO) null else YtPlayerPanel.INFO
            YT_MORE_CONTROL ->
                panel = if (panel == YtPlayerPanel.MORE) null else YtPlayerPanel.MORE
            YT_COMMENTS_CONTROL ->
                panel = if (panel == YtPlayerPanel.COMMENTS) null else YtPlayerPanel.COMMENTS
            YT_LIKE_CONTROL -> react(1)
            YT_DISLIKE_CONTROL -> react(2)
            YT_NEXT_VIDEO_CONTROL -> goNext()
            YT_ROTATE_CONTROL -> onRotate()
            YT_BACKGROUND_CONTROL -> onBackgroundPlay(!backgroundPlay)
            YT_SEEK_CONTROL -> playerProvider()?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The video is a native surface behind this Box; this layer only catches
        // taps that miss a control, which is what toggles the chrome.
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable(enabled = panel == null)
                .onPreviewKeyEvent { event ->
                    if (panel != null) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        val code = event.nativeKeyEvent.keyCode
                        // The remote's rewind/fast-forward buttons scrub ten
                        // seconds even while the chrome is hidden.
                        if (code == AndroidKeyEvent.KEYCODE_MEDIA_REWIND ||
                            code == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                        ) {
                            seekBy(
                                if (code == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                                    YT_SEEK_STEP_MS else -YT_SEEK_STEP_MS
                            )
                            return@onPreviewKeyEvent true
                        }
                        controlsTick++
                        if (!showControls) {
                            when (event.key) {
                                Key.DirectionLeft -> seekBy(-YT_SEEK_STEP_MS)
                                Key.DirectionRight -> seekBy(YT_SEEK_STEP_MS)
                                else -> {
                                    showControls = true
                                    controlIndex = YT_INFO_CONTROL
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                            code == AndroidKeyEvent.KEYCODE_ENTER ||
                            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        ) {
                            activateControl(controlIndex)
                            return@onPreviewKeyEvent true
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                when {
                                    controlIndex == YT_SEEK_CONTROL -> seekBy(-YT_SEEK_STEP_MS)
                                    controlIndex == YT_PLAY_CONTROL -> controlIndex = YT_BACK_CONTROL
                                    controlIndex in YT_LAST_VIDEO_CONTROL..YT_BACKGROUND_CONTROL ->
                                        controlIndex =
                                            (controlIndex - 1).coerceAtLeast(YT_LAST_VIDEO_CONTROL)
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionRight -> {
                                when {
                                    controlIndex == YT_SEEK_CONTROL -> seekBy(YT_SEEK_STEP_MS)
                                    controlIndex == YT_BACK_CONTROL -> controlIndex = YT_PLAY_CONTROL
                                    controlIndex in YT_LAST_VIDEO_CONTROL..YT_BACKGROUND_CONTROL ->
                                        controlIndex =
                                            (controlIndex + 1).coerceAtMost(YT_BACKGROUND_CONTROL)
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionUp -> {
                                when {
                                    controlIndex == YT_SEEK_CONTROL -> controlIndex = YT_INFO_CONTROL
                                    controlIndex in YT_LAST_VIDEO_CONTROL..YT_INFO_CONTROL ->
                                        controlIndex = YT_BACK_CONTROL
                                    controlIndex in YT_MORE_CONTROL..YT_BACKGROUND_CONTROL ->
                                        controlIndex = YT_PLAY_CONTROL
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionDown -> {
                                when {
                                    controlIndex == YT_BACK_CONTROL ->
                                        controlIndex = YT_LAST_VIDEO_CONTROL
                                    controlIndex == YT_PLAY_CONTROL ->
                                        controlIndex = YT_NEXT_VIDEO_CONTROL
                                    controlIndex in YT_LAST_VIDEO_CONTROL..YT_BACKGROUND_CONTROL ->
                                        controlIndex = YT_SEEK_CONTROL
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                activateControl(controlIndex)
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                    }
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    controlsTick++
                    if (event.key == Key.MediaPlayPause) {
                        playerProvider()?.let { if (it.isPlaying) it.pause() else it.play() }
                        true
                    } else {
                        false
                    }
                }
                .pointerInput(panel) {
                    detectTapGestures {
                        if (panel == null) {
                            if (showControls) showControls = false else poke()
                        }
                    }
                }
                // Brightness / volume: the generic player's vertical swipes sit
                // on the native surface, which this Compose chrome sits over.
                // Hand the drag back to the activity so both players share one
                // HUD, one gain curve and one settings switch.
                .pointerInput(panel) {
                    var total = 0f
                    var active = false
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (panel == null) {
                                active = true
                                total = 0f
                                onSwipeStart(offset.x)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (active) {
                                total += dragAmount
                                onSwipe(total)
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (active) {
                                active = false
                                onSwipeEnd()
                            }
                        },
                        onDragCancel = {
                            if (active) {
                                active = false
                                onSwipeEnd()
                            }
                        },
                    )
                }
        )

        if (showControls) {
            CompositionLocalProvider(LocalYtPanelOpen provides (panel != null)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.78f),
                                )
                            )
                        )
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 28.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        YtControl(
                            label = "Back",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            focused = controlIndex == YT_BACK_CONTROL,
                            onClick = { activateControl(YT_BACK_CONTROL) },
                        )
                        Text(
                            title.ifBlank { info?.title.orEmpty() },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        YtControl(
                            label = null,
                            icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            focused = controlIndex == YT_PLAY_CONTROL,
                            modifier = Modifier.size(50.dp),
                            onClick = { activateControl(YT_PLAY_CONTROL) },
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    notice?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        YtControl(
                            label = "Last",
                            icon = Icons.Filled.SkipPrevious,
                            focused = controlIndex == YT_LAST_VIDEO_CONTROL,
                            busy = lastBusy,
                            onClick = { activateControl(YT_LAST_VIDEO_CONTROL) },
                        )
                        YtControl(
                            label = if (subscribed) "Subscribed" else "Subscribe",
                            icon = if (subscribed) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                            focused = controlIndex == YT_SUBSCRIBE_CONTROL,
                            active = subscribed,
                            onClick = { activateControl(YT_SUBSCRIBE_CONTROL) },
                        )
                        YtControl(
                            label = "Watch later",
                            icon = Icons.Filled.WatchLater,
                            focused = controlIndex == YT_WATCH_LATER_CONTROL,
                            selected = panel == YtPlayerPanel.SAVE,
                            onClick = { activateControl(YT_WATCH_LATER_CONTROL) },
                        )
                        YtControl(
                            label = if (quality <= 0) "Auto" else "${quality}p",
                            icon = Icons.Filled.Tune,
                            focused = controlIndex == YT_QUALITY_CONTROL,
                            selected = panel == YtPlayerPanel.QUALITY,
                            active = quality > 0,
                            onClick = { activateControl(YT_QUALITY_CONTROL) },
                        )
                        YtControl(
                            label = "Info",
                            icon = Icons.Filled.Info,
                            focused = controlIndex == YT_INFO_CONTROL,
                            selected = panel == YtPlayerPanel.INFO,
                            onClick = { activateControl(YT_INFO_CONTROL) },
                        )
                        YtControl(
                            label = "More",
                            icon = Icons.Filled.VideoLibrary,
                            focused = controlIndex == YT_MORE_CONTROL,
                            selected = panel == YtPlayerPanel.MORE,
                            onClick = { activateControl(YT_MORE_CONTROL) },
                        )
                        YtControl(
                            label = "Comments",
                            icon = Icons.Filled.Comment,
                            focused = controlIndex == YT_COMMENTS_CONTROL,
                            selected = panel == YtPlayerPanel.COMMENTS,
                            onClick = { activateControl(YT_COMMENTS_CONTROL) },
                        )
                        YtControl(
                            label = null,
                            icon = if (likeStatus == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            focused = controlIndex == YT_LIKE_CONTROL,
                            modifier = Modifier.size(36.dp),
                            active = likeStatus == 1,
                            onClick = { activateControl(YT_LIKE_CONTROL) },
                        )
                        YtControl(
                            label = null,
                            icon = if (likeStatus == 2) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            focused = controlIndex == YT_DISLIKE_CONTROL,
                            modifier = Modifier.size(36.dp),
                            active = likeStatus == 2,
                            onClick = { activateControl(YT_DISLIKE_CONTROL) },
                        )
                        YtControl(
                            label = "Next",
                            icon = Icons.Filled.SkipNext,
                            focused = controlIndex == YT_NEXT_VIDEO_CONTROL,
                            busy = nextBusy,
                            onClick = { activateControl(YT_NEXT_VIDEO_CONTROL) },
                        )
                        YtControl(
                            label = "Rotate",
                            icon = Icons.Outlined.ScreenRotation,
                            focused = controlIndex == YT_ROTATE_CONTROL,
                            onClick = { activateControl(YT_ROTATE_CONTROL) },
                        )
                        YtControl(
                            label = "Background",
                            icon = Icons.Filled.Headphones,
                            focused = controlIndex == YT_BACKGROUND_CONTROL,
                            active = backgroundPlay,
                            onClick = { activateControl(YT_BACKGROUND_CONTROL) },
                        )
                    }

                    if (duration > 0L) {
                        YtSeekBar(
                            position = position,
                            duration = duration,
                            focused = controlIndex == YT_SEEK_CONTROL,
                            onSeek = { target ->
                                playerProvider()?.seekTo(target)
                                position = target
                                poke()
                                controlIndex = YT_SEEK_CONTROL
                            },
                        )
                        // A focused seek bar grows by 8dp and shows the 10s hints;
                        // a television's bottom overscan would clip the timestamp
                        // without this extra room.
                        Spacer(Modifier.height(if (com.hikari.app.tv.TvMode.isTv) 12.dp else 0.dp))
                    }
                }
            }
        }

        when (panel) {
            YtPlayerPanel.INFO -> YouTubeVideoPanel(
                videoId = videoId,
                mode = YtPanelMode.INFO,
                onClose = { panel = null },
            )
            YtPlayerPanel.COMMENTS -> YouTubeVideoPanel(
                videoId = videoId,
                mode = YtPanelMode.COMMENTS,
                onClose = { panel = null },
            )
            YtPlayerPanel.MORE -> YouTubeMoreVideos(
                videoId = videoId,
                buttonVisible = false,
                initialOpen = true,
                onOpenChange = { open -> if (!open) panel = null },
                onPlay = { request ->
                    panel = null
                    onSwitchVideo(request)
                },
            )
            YtPlayerPanel.SAVE -> PanelShell(
                title = "Watch later",
                onClose = { panel = null },
            ) {
                if (saveLoading) {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                } else {
                    playlists.forEach { playlist ->
                        PanelRow(
                            label = playlist.title,
                            icon = if (playlist.selected) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            onClick = { saveTo(playlist) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("New playlist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { createPlaylist() },
                        enabled = newPlaylistName.isNotBlank(),
                    ) { Text("Create and save") }
                }
            }
            YtPlayerPanel.QUALITY -> PanelShell(
                title = "Quality",
                onClose = { panel = null },
            ) {
                PanelRow(
                    label = "Auto",
                    icon = if (quality <= 0) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    onClick = { pickQuality(0) },
                )
                qualities.forEach { height ->
                    PanelRow(
                        label = "${height}p",
                        icon = if (quality == height) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        onClick = { pickQuality(height) },
                    )
                }
                if (qualities.isEmpty()) {
                    Text(
                        "Quality appears once the video's tracks are ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun YtControl(
    label: String?,
    icon: ImageVector,
    focused: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    active: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val highlight = focused || selected
    val panelOpen = LocalYtPanelOpen.current
    val background = if (highlight) Color.White else Color.Black.copy(alpha = 0.82f)
    val content = when {
        highlight -> Color.Black
        active -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .focusProperties { canFocus = !panelOpen }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            }
        } else {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp,
                        color = content,
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, color = content, maxLines = 1)
            }
        }
    }
}

@Composable
private fun YtSeekBar(
    position: Long,
    duration: Long,
    focused: Boolean,
    onSeek: (Long) -> Unit,
) {
    var width by remember { mutableIntStateOf(1) }
    val fraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 10.dp else 6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = if (focused) 0.35f else 0.25f))
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(50),
                )
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0L) {
                            onSeek((offset.x / width * duration).toLong().coerceIn(0L, duration))
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(if (focused) 8.dp else 4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatTime(position)} / ${formatTime(duration)}",
                fontSize = 12.sp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.weight(1f))
            if (focused) {
                Text(
                    "◀  10s      ▶  10s",
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PanelShell(
    title: String,
    onClose: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.94f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .padding(end = 24.dp)
                .then(
                    if (com.hikari.app.tv.TvMode.isTv) {
                        Modifier.fillMaxWidth(0.30f)
                    } else {
                        Modifier.widthIn(max = 420.dp)
                    }
                )
                .fillMaxHeight(if (com.hikari.app.tv.TvMode.isTv) 1f else 0.86f),
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun PanelRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

private fun qualityHeights(player: Player?): List<Int> {
    val groups = player?.currentTracks?.groups ?: return emptyList()
    return groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            (0 until group.length).mapNotNull { index ->
                group.getTrackFormat(index).height.takeIf { height -> height > 0 }
            }
        }
        .distinct()
        .sortedDescending()
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
