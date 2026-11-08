package com.hikari.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hikari.app.player.MiniPlayerStore
import com.hikari.app.player.PlayerActivity
import kotlin.math.roundToInt

/**
 * The app's in-app picture-in-picture window. The player's "Mini" pill puts its
 * source and position in [MiniPlayerStore] and closes; this draws the video
 * over whatever screen is showing. Drag to move, pinch to resize, tap the
 * expand button to return to the full player, and × to stop it.
 */
@Composable
fun MiniPlayerHost() {
    val context = LocalContext.current
    val item by MiniPlayerStore.item.collectAsState()
    val current = item ?: return
    val density = LocalDensity.current

    val exo = remember(current.url) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Hikari")
        if (current.headers.isNotEmpty()) {
            runCatching { http.setDefaultRequestProperties(current.headers) }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(current.url))
                seekTo(current.positionMs)
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(current.url) {
        onDispose { exo.release() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = maxWidth.value
        val maxH = maxHeight.value
        var resultWidth by remember(current.url) {
            mutableFloatStateOf((maxW * 0.46f).coerceIn(180f, 420f))
        }
        val aspect = 16f / 9f
        val heightDp = resultWidth / aspect
        var offsetX by remember(current.url) { mutableFloatStateOf(-1f) }
        var offsetY by remember(current.url) { mutableFloatStateOf(-1f) }
        val minWidth = 140f
        val maxWidthDp = (maxW - 24f).coerceAtLeast(minWidth)
        LaunchedEffect(current.url, maxW, maxH) {
            if (offsetX < 0f) {
                offsetX = (maxW - resultWidth - 16f).coerceAtLeast(8f)
                offsetY = (maxH - heightDp - 150f).coerceAtLeast(80f)
            }
        }

        Box(
            Modifier
                .offset(offsetX.coerceAtLeast(0f).dp, offsetY.coerceAtLeast(0f).dp)
                .width(resultWidth.dp)
                .pointerInput(current.url) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        resultWidth = (resultWidth * zoom).coerceIn(minWidth, maxWidthDp)
                        val dx = pan.x / density.density
                        val dy = pan.y / density.density
                        val maxX = (maxW - resultWidth - 8f).coerceAtLeast(8f)
                        val maxY = (maxH - (resultWidth / aspect) - 8f).coerceAtLeast(8f)
                        offsetX = (offsetX + dx).coerceIn(8f, maxX)
                        offsetY = (offsetY + dy).coerceIn(8f, maxY)
                    }
                },
        ) {
            Surface(
                color = Color(0xFF0E0E12),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(heightDp.dp),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    player = exo
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x99000000))
                                    .clickable {
                                        val sources = current.sourcesJson.ifBlank {
                                            org.json.JSONArray().put(
                                                org.json.JSONObject()
                                                    .put("name", "Mini")
                                                    .put("url", current.url)
                                                    .put("headers", org.json.JSONObject(current.headers as Map<*, *>))
                                                    .put("isM3u8", current.isM3u8)
                                                    .put("isMpd", current.isMpd),
                                            ).toString()
                                        }
                                        val intent = Intent(context, PlayerActivity::class.java).apply {
                                            putExtra("title", current.title)
                                            putExtra("sources", sources)
                                            putExtra("startPosition", exo.currentPosition)
                                            putExtra("askServer", false)
                                        }
                                        context.startActivity(intent)
                                        MiniPlayerStore.hide()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = "Expand", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x99000000))
                                    .clickable { MiniPlayerStore.hide() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close mini player", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        // Corner grip: drag horizontally to resize (16:9 kept).
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(30.dp)
                                .pointerInput(current.url) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        resultWidth = (resultWidth + drag.x / density.density)
                                            .coerceIn(minWidth, maxWidthDp)
                                        val maxX = (maxW - resultWidth - 8f).coerceAtLeast(8f)
                                        val maxY = (maxH - (resultWidth / aspect) - 8f).coerceAtLeast(8f)
                                        offsetX = offsetX.coerceIn(8f, maxX)
                                        offsetY = offsetY.coerceIn(8f, maxY)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⤡", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xEE0E0E12))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            // The title bar is the drag handle: the video surface
                            // belongs to PlayerView and consumes its own touches.
                            .pointerInput(current.url) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val dx = drag.x / density.density
                                    val dy = drag.y / density.density
                                    val maxX = (maxW - resultWidth - 8f).coerceAtLeast(8f)
                                    val maxY = (maxH - (resultWidth / aspect) - 8f).coerceAtLeast(8f)
                                    offsetX = (offsetX + dx).coerceIn(8f, maxX)
                                    offsetY = (offsetY + dy).coerceIn(8f, maxY)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                current.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Mini player · drag to move · pinch to resize",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
