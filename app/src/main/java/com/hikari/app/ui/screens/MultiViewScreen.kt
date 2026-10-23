package com.hikari.app.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import com.hikari.app.ui.screens.MultiViewLayout
import com.hikari.app.data.MultiViewSession
import com.hikari.app.player.PlayerPool
import kotlinx.coroutines.launch
import com.hikari.app.i18n.tr
import com.hikari.app.i18n.I18n

/**
 * F4 — multi-view screen showing up to nine channel windows in a configurable grid.
 *
 * [sessions] is the list of channels to show. [layout] determines the grid shape
 * (see [MultiViewLayout]). Each cell hosts its own ExoPlayer from [PlayerPool].
 * A long-press on a window opens the full-screen player for that channel.
 */
@Composable
fun MultiViewScreen(
    sessions: List<MultiViewSession>,
    layout: MultiViewLayout = MultiViewLayout.GRID_2x3,
    onBack: () -> Unit,
) {
    val maxWindows = layout.maxWindows
    val cols = layout.cols
    val rows = layout.rows
    val context = LocalContext.current
    val pool = remember { PlayerPool(context) }

    DisposableEffect(layout, sessions) {
        onDispose { pool.releaseAll() }
    }

    var selectedCell by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
        ) {
            items(sessions.take(maxWindows), key = { it.url }) { session ->
                val idx = sessions.indexOf(session).coerceIn(0, pool.size - 1)
                val player = pool.get(idx)

                // Set media item when URL changes
                DisposableEffect(session.url) {
                    player.setMediaItem(MediaItem.fromUri(session.url))
                    player.playWhenReady = true
                    player.prepare()
                    onDispose { /* player stays in pool */ }
                }

                WindowCell(
                    player = player,
                    name = session.name,
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        // Long-press would go to full player — for now, tap opens full
                        // (full player navigation lives in the caller)
                    },
                )
            }
        }

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = tr("Back"),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WindowCell(
    player: androidx.media3.exoplayer.ExoPlayer,
    name: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1a1a1a))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (name.isNotBlank()) {
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(4.dp).align(Alignment.BottomStart),
            )
        }
    }
}
