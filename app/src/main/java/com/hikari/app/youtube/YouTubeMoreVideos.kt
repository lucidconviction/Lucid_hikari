package com.hikari.app.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.hikari.app.youtube.YouTubePlayRequest

/**
 * The player's "More Videos" button and the list the current video was picked from.
 *
 * The button sits at the middle of the right edge. Tapping it slides a vertically scrolling
 * overlay in from the right; each entry is a thumbnail a quarter of the player's width, with the
 * title underneath. Picking one resolves and plays it in place of the current video.
 */
@Composable
fun YouTubeMoreVideos(
    videoId: String,
    buttonVisible: Boolean = true,
    initialOpen: Boolean = false,
    autoHideMs: Long = 5_000L,
    onOpenChange: (Boolean) -> Unit = {},
    onPlay: (YouTubePlayRequest) -> Unit,
) {
    val vm = YouTubeSession.feed ?: return
    val queue by vm.queue.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(initialOpen) }
    var showButton by remember { mutableStateOf(buttonVisible && !initialOpen) }
    var loadingVideoId by remember { mutableStateOf<String?>(null) }

    fun setOpen(value: Boolean) {
        open = value
        if (value) showButton = false
        onOpenChange(value)
    }

    // A new video is playing: clear any spinner left over from the previous one.
    // The button is offered for a few seconds after the video starts, then
    // times out of the way (the user can reopen it by tapping again when the
    // player is recreated).
    LaunchedEffect(videoId, buttonVisible) {
        loadingVideoId = null
        showButton = buttonVisible && !open
        if (buttonVisible) {
            delay(autoHideMs)
            if (!open) showButton = false
        }
    }
    BackHandler(enabled = open) { setOpen(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The requested size: each thumbnail fills a quarter of the player's width. Item and list
        // padding is added around it so the thumbnail itself lands on that quarter.
        val thumbnailWidth = (maxWidth / 4).coerceIn(150.dp, 360.dp)
        val panelWidth = (thumbnailWidth + 28.dp).coerceAtMost(maxWidth * 0.6f)

        AnimatedVisibility(
            visible = showButton && !open,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3000000))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable { setOpen(true) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "More Videos",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AnimatedVisibility(
            visible = open,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { setOpen(false) },
            )
        }

        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .background(Color(0xF5111114)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "More Videos",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { setOpen(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                if (queue.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No videos in this list.", color = Color(0xFFB0B0B0))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = queue,
                            key = { index, item -> item.videoId ?: "slot-$index" },
                        ) { _, item ->
                            MoreVideoRow(
                                item = item,
                                current = item.videoId == videoId,
                                loading = loadingVideoId == item.videoId,
                                enabled = loadingVideoId == null,
                                onClick = {
                                    val id = item.videoId ?: return@MoreVideoRow
                                    loadingVideoId = id
                                    vm.activate(
                                        item = item,
                                        from = queue,
                                        onReady = { playable ->
                                            loadingVideoId = null
                                            onPlay(
                                                YouTubePlayRequest(
                                                    url = playable.url,
                                                    title = playable.title,
                                                    poster = playable.poster,
                                                    videoId = playable.videoId,
                                                    startPositionMs = playable.startPositionMs,
                                                    headers = playable.headers,
                                                ),
                                            )
                                        },
                                        onError = { loadingVideoId = null },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreVideoRow(
    item: YtItem,
    current: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (current) Color(0xFF242428) else Color.Transparent)
            .clickable(enabled = enabled && !current, onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.thumbnailOrFallback(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            item.badge?.let { badge ->
                Text(
                    text = badge,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )

                current -> Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Now playing",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.title,
            color = if (current) Color(0xFFE53935) else Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.author?.takeIf { it.isNotBlank() }?.let { author ->
            Text(
                text = author,
                color = Color(0xFF9A9A9A),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
