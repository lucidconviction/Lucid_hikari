package com.hikari.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hikari.app.cs3.YouTubePlayables
import com.hikari.app.data.StreamSource
import com.hikari.app.data.YouTubeChannel
import com.hikari.app.data.stores.YouTubeChannelStore
import com.hikari.app.net.Http
import com.hikari.app.player.PlayerActivity
import com.hikari.app.ui.openYouTubeVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * YouTube tab — quick URL bar + saved channels/videos list.
 * Supports playing single videos, channels (auto-fetches latest videos),
 * and adding/saving them to the persistent store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(nav: androidx.navigation.NavHostController) {
    val context = LocalContext.current
    val store = remember { YouTubeChannelStore.getInstance(context) }
    val items by store.channelsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    // Channel videos dialog state
    var selectedChannelVideos by remember { mutableStateOf<YouTubeChannelInfo?>(null) }
    var loadingChannelId by remember { mutableStateOf<String?>(null) }

    val videos = items.filter { it.itemType == "video" }
    val channels = items.filter { it.itemType == "channel" }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = Color(0xFFCC0000),
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = "YouTube",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ── Quick-play URL bar ──────────────────────────────────────────
        var urlInput by remember { mutableStateOf("") }
        var urlPlaying by remember { mutableStateOf(false) }

        val handleQuickPlay: () -> Unit = {
            if (urlInput.isNotBlank()) {
                val cleaned = urlInput.trim()
                val videoId = extractVideoId(cleaned)
                if (videoId != null) {
                    urlInput = ""
                    urlPlaying = true
                    playVideo(
                        context = context,
                        videoId = videoId,
                        title = null,
                        onComplete = { urlPlaying = false },
                        onError = { showError = it },
                    )
                } else {
                    val chan = extractChannelIdentifier(cleaned)
                    if (chan != null) {
                        urlInput = ""
                        urlPlaying = true
                        CoroutineScope(Dispatchers.IO).launch {
                            val info = resolveYouTubeChannel(cleaned)
                            withContext(Dispatchers.Main) {
                                if (info != null && info.videos.isNotEmpty()) {
                                    val latest = info.videos.first()
                                    playVideo(
                                        context = context,
                                        videoId = latest.id,
                                        title = latest.title,
                                        onComplete = { urlPlaying = false },
                                        onError = { showError = it },
                                    )
                                } else {
                                    urlPlaying = false
                                    showError = "Could not find videos for this channel"
                                }
                            }
                        }
                    } else {
                        showError = "Not a recognisable YouTube video or channel URL"
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = { Text("Paste a YouTube video or channel URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 44.dp, top = 8.dp, bottom = 8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { handleQuickPlay() }),
            )
            if (urlPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else if (urlInput.isNotBlank()) {
                IconButton(
                    onClick = { handleQuickPlay() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── Error message ───────────────────────────────────────────────
        showError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { showError = null },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Saved items list ────────────────────────────────────────────
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved items yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Paste a URL above to play instantly, or tap + to save a video/channel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Videos section
                if (videos.isNotEmpty()) {
                    item {
                        Text(
                            text = "Videos",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(videos, key = { it.id }) { item ->
                        YouTubeItemRow(
                            item = item,
                            onPlay = {
                                val videoId = extractVideoId(item.url)
                                if (videoId != null) {
                                    playVideo(
                                        context = context,
                                        videoId = videoId,
                                        title = item.name.ifBlank { null },
                                        onError = { showError = it },
                                    )
                                } else {
                                    showError = "Invalid YouTube video URL"
                                }
                            },
                            onDelete = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    store.deleteChannel(item.id)
                                }
                            },
                        )
                    }
                }

                // Channels section
                if (channels.isNotEmpty()) {
                    item {
                        Text(
                            text = "Channels",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(channels, key = { it.id }) { item ->
                        val isResolving = loadingChannelId == item.id
                        YouTubeChannelRow(
                            item = item,
                            isLoading = isResolving,
                            onViewVideos = {
                                loadingChannelId = item.id
                                CoroutineScope(Dispatchers.IO).launch {
                                    val info = resolveYouTubeChannel(item.url)
                                    withContext(Dispatchers.Main) {
                                        loadingChannelId = null
                                        if (info != null && info.videos.isNotEmpty()) {
                                            selectedChannelVideos = info
                                            if (item.name != info.title && info.title.isNotBlank()) {
                                                store.updateChannel(item.copy(name = info.title))
                                            }
                                        } else {
                                            showError = "Could not find videos for this channel"
                                        }
                                    }
                                }
                            },
                            onPlayLatest = {
                                loadingChannelId = item.id
                                CoroutineScope(Dispatchers.IO).launch {
                                    val info = resolveYouTubeChannel(item.url)
                                    withContext(Dispatchers.Main) {
                                        loadingChannelId = null
                                        if (info != null && info.videos.isNotEmpty()) {
                                            val latest = info.videos.first()
                                            playVideo(
                                                context = context,
                                                videoId = latest.id,
                                                title = latest.title,
                                                onError = { showError = it },
                                            )
                                            if (item.name != info.title && info.title.isNotBlank()) {
                                                store.updateChannel(item.copy(name = info.title))
                                            }
                                        } else {
                                            showError = "Could not find videos for this channel"
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    store.deleteChannel(item.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── Channel videos popup dialog ──────────────────────────────────
    selectedChannelVideos?.let { channelInfo ->
        YouTubeChannelVideosDialog(
            channelInfo = channelInfo,
            onDismiss = { selectedChannelVideos = null },
            onPlayVideo = { video ->
                selectedChannelVideos = null
                playVideo(
                    context = context,
                    videoId = video.id,
                    title = video.title,
                    onError = { showError = it },
                )
            },
        )
    }

    // ── Add dialog ────────────────────────────────────────────────────
    if (showAddDialog) {
        YouTubeAddDialog(
            onAdd = { name, url, type ->
                val normalizedUrl = normalizeYouTubeUrl(url, type)
                val defaultName = if (name.isNotBlank()) {
                    name
                } else if (type == "channel") {
                    extractChannelIdentifier(url)?.second ?: url
                } else {
                    ""
                }
                val newItem = YouTubeChannel(
                    name = defaultName,
                    url = normalizedUrl,
                    itemType = type,
                )
                CoroutineScope(Dispatchers.IO).launch {
                    store.addChannel(newItem)
                }
                showAddDialog = false
            },
            onCancel = { showAddDialog = false },
        )
    }
}

// ─── Video item row ─────────────────────────────────────────────────────────

@Composable
fun YouTubeItemRow(
    item: YouTubeChannel,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { item.url },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.name.isNotBlank()) item.url else "Video",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ─── Channel item row ────────────────────────────────────────────────────────

@Composable
fun YouTubeChannelRow(
    item: YouTubeChannel,
    isLoading: Boolean = false,
    onViewVideos: () -> Unit,
    onPlayLatest: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onViewVideos() }
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { item.url },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isLoading) "Loading videos..." else if (item.name.isNotBlank()) item.url else "Channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Play latest video button
            IconButton(onClick = onPlayLatest, enabled = !isLoading) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play Latest",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ─── Channel Videos Dialog ──────────────────────────────────────────────────

@Composable
fun YouTubeChannelVideosDialog(
    channelInfo: YouTubeChannelInfo,
    onDismiss: () -> Unit,
    onPlayVideo: (YouTubeChannelVideo) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = channelInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${channelInfo.videos.size} latest videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        channelInfo.videos.firstOrNull()?.let { onPlayVideo(it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Latest Video")
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(channelInfo.videos, key = { it.id }) { video ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayVideo(video) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 48.dp)
                                        .background(Color.Black, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (video.thumbnailUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = video.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (video.published.isNotBlank()) {
                                        Text(
                                            text = video.published,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

// ─── Add dialog ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAddDialog(
    onAdd: (name: String, url: String, type: String) -> Unit,
    onCancel: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val (detectedType, _) = validateYoutubeUrl(url)
    val isValid = detectedType.isNotEmpty()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add YouTube Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste a YouTube video or channel URL below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("YouTube URL") },
                    singleLine = true,
                    placeholder = {
                        Text(
                            if (detectedType == "channel")
                                "https://www.youtube.com/@username"
                            else
                                "https://www.youtube.com/watch?v=..."
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    isError = url.isNotBlank() && !isValid,
                    supportingText = {
                        if (url.isNotBlank() && !isValid) {
                            Text("Please enter a valid YouTube video or channel URL", color = MaterialTheme.colorScheme.error)
                        } else if (isValid) {
                            Text(
                                "Detected as: ${if (detectedType == "video") "Video" else "Channel"}",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    placeholder = { Text(if (detectedType == "channel") "Channel Name" else "Video Title") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isValid) {
                                onAdd(name.trim(), url.trim(), detectedType)
                            }
                        },
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onAdd(name.trim(), url.trim(), detectedType)
                    }
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (detectedType == "channel") "Add Channel" else if (detectedType == "video") "Add Video" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}

// ─── Data models & helpers ───────────────────────────────────────────────────

data class YouTubeChannelVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String = "",
    val published: String = "",
)

data class YouTubeChannelInfo(
    val channelId: String,
    val title: String,
    val videos: List<YouTubeChannelVideo>,
)

/** Extracts an 11-character video ID from any YouTube URL format. */
fun extractVideoId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // Direct 11-char ID
    if (trimmed.matches(Regex("""^[a-zA-Z0-9_-]{11}$"""))) {
        return trimmed
    }

    // youtu.be/<id>
    Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""").find(trimmed)?.let {
        return it.groupValues[1]
    }

    // [?&]v=<id> (youtube.com/watch?v=...)
    Regex("""[?&]v=([a-zA-Z0-9_-]{11})""").find(trimmed)?.let {
        return it.groupValues[1]
    }

    // /embed/<id>, /v/<id>, /shorts/<id>, /live/<id>
    Regex("""youtube\.com/(?:embed|v|shorts|live)/([a-zA-Z0-9_-]{11})""").find(trimmed)?.let {
        return it.groupValues[1]
    }

    return null
}

/** Extracts channel identifier (channel ID, handle, or custom name). */
fun extractChannelIdentifier(input: String): Pair<String, String>? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // Raw UC ID (24 chars)
    if (trimmed.matches(Regex("""^UC[a-zA-Z0-9_-]{22}$"""))) {
        return Pair("channel_id", trimmed)
    }

    // URL with /channel/UC...
    Regex("""channel/(UC[a-zA-Z0-9_-]{22})""").find(trimmed)?.let {
        return Pair("channel_id", it.groupValues[1])
    }

    // @handle or URL with @handle
    Regex("""(?:youtube\.com/)?@([a-zA-Z0-9._-]+)""").find(trimmed)?.let {
        return Pair("handle", "@" + it.groupValues[1])
    }

    // /c/name or /user/name
    Regex("""youtube\.com/(?:c|user)/([a-zA-Z0-9._-]+)""").find(trimmed)?.let {
        return Pair("custom", it.groupValues[1])
    }

    return null
}

/** Validates a YouTube URL and returns Pair(type, id). type is "video", "channel", or "". */
fun validateYoutubeUrl(input: String): Pair<String, String> {
    val vid = extractVideoId(input)
    if (vid != null) return Pair("video", vid)

    val chan = extractChannelIdentifier(input)
    if (chan != null) return Pair("channel", chan.second)

    return Pair("", "")
}

/** Normalizes a YouTube URL to canonical form. */
fun normalizeYouTubeUrl(url: String, type: String): String {
    val trimmed = url.trim()
    if (type == "video") {
        val videoId = extractVideoId(trimmed)
        return if (videoId != null) "https://www.youtube.com/watch?v=$videoId" else trimmed
    } else {
        val chan = extractChannelIdentifier(trimmed)
        return when (chan?.first) {
            "channel_id" -> "https://www.youtube.com/channel/${chan.second}"
            "handle" -> "https://www.youtube.com/${chan.second}"
            "custom" -> "https://www.youtube.com/c/${chan.second}"
            else -> if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        }
    }
}

/**
 * Resolves a YouTube channel URL to its metadata and latest videos.
 * Multi-stage resolution:
 * 1. Direct UC ID if available.
 * 2. NewPipeExtractor ChannelInfo.
 * 3. HTML scrape of canonical / externalId / browseId / RSS links.
 * 4. YouTube RSS XML feed parsing for channel title and video list.
 */
suspend fun resolveYouTubeChannel(channelUrl: String): YouTubeChannelInfo? = withContext(Dispatchers.IO) {
    try {
        val trimmed = channelUrl.trim()
        val chanIdent = extractChannelIdentifier(trimmed)

        // Step 1: Direct UC ID if present
        var channelId = if (chanIdent?.first == "channel_id") {
            chanIdent.second
        } else {
            Regex("""UC[a-zA-Z0-9_-]{22}""").find(trimmed)?.value
        }

        var channelTitle = ""
        var extractedVideos: List<YouTubeChannelVideo>? = null

        // Step 2: Resolve handle or custom URL to UC ID
        if (channelId == null) {
            val normalizedUrl = if (chanIdent?.first == "handle") {
                "https://www.youtube.com/${chanIdent.second}"
            } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }

            // A) Try NewPipeExtractor
            if (YouTubePlayables.ensureReady()) {
                try {
                    val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(
                        org.schabi.newpipe.extractor.ServiceList.YouTube,
                        normalizedUrl,
                    )
                    if (!info.id.isNullOrBlank() && info.id.startsWith("UC")) {
                        channelId = info.id
                        channelTitle = info.name.orEmpty()
                    }
                } catch (e: Exception) {
                    com.hikari.app.data.Logs.log("YouTube", "NewPipe channel lookup: ${e.message}")
                }
            }

            // B) HTML scrape fallback
            if (channelId == null) {
                channelId = scrapeChannelIdFromHtml(normalizedUrl)
            }
        }

        // Step 3: Fetch RSS feed for the channel ID
        if (channelId != null && channelId.startsWith("UC")) {
            val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            val xml = Http.fetchStringRobust(rssUrl, mapOf("User-Agent" to Http.UA)).getOrNull()
            if (!xml.isNullOrBlank()) {
                val parsed = parseRssFeed(xml)
                if (channelTitle.isBlank() && parsed.first.isNotBlank()) {
                    channelTitle = parsed.first
                }
                if (parsed.second.isNotEmpty()) {
                    extractedVideos = parsed.second
                }
            }
        }

        // Step 4: Return result if videos found
        if (channelId != null && !extractedVideos.isNullOrEmpty()) {
            return@withContext YouTubeChannelInfo(
                channelId = channelId,
                title = channelTitle.ifBlank { chanIdent?.second ?: "Channel" },
                videos = extractedVideos,
            )
        }

        null
    } catch (e: Exception) {
        com.hikari.app.data.Logs.log("YouTube", "Failed to resolve channel: ${e.message}")
        null
    }
}

private suspend fun scrapeChannelIdFromHtml(url: String): String? = withContext(Dispatchers.IO) {
    try {
        val page = Http.fetchStringRobust(url, mapOf("User-Agent" to Http.UA)).getOrNull()
            ?: return@withContext null

        // Canonical channel link
        val canonMatch = Regex("""/channel/(UC[a-zA-Z0-9_-]{22})""").find(page)
        if (canonMatch != null) return@withContext canonMatch.groupValues[1]

        // externalId: "externalId":"UC..."
        val extMatch = Regex("""\"externalId\"\s*:\s*\"(UC[a-zA-Z0-9_-]{22})\"""").find(page)
        if (extMatch != null) return@withContext extMatch.groupValues[1]

        // browseId: "browseId":"UC..."
        val browseMatch = Regex("""\"browseId\"\s*:\s*\"(UC[a-zA-Z0-9_-]{22})\"""").find(page)
        if (browseMatch != null) return@withContext browseMatch.groupValues[1]

        // RSS link in HTML
        val rssMatch = Regex("""feeds/videos\.xml\?channel_id=(UC[a-zA-Z0-9_-]{22})""").find(page)
        if (rssMatch != null) return@withContext rssMatch.groupValues[1]

        // channelId in structured JSON
        val cidMatch = Regex("""\"channelId\"\s*:\s*\"(UC[a-zA-Z0-9_-]{22})\"""").find(page)
        cidMatch?.groupValues?.get(1)
    } catch (e: Exception) {
        null
    }
}

private fun parseRssFeed(xml: String): Pair<String, List<YouTubeChannelVideo>> {
    val channelTitle = Regex("""<author>\s*<name>(.*?)</name>""", RegexOption.DOT_MATCHES_ALL)
        .find(xml)?.groupValues?.get(1)?.trim()
        ?: Regex("""<title>(.*?)</title>""").find(xml)?.groupValues?.get(1)?.trim()
        ?: ""

    val cleanChannelTitle = unescapeXml(channelTitle)

    val entryRegex = Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)
    val videoIdRegex = Regex("""<yt:videoId>(.*?)</yt:videoId>""")
    val titleRegex = Regex("""<title>(.*?)</title>""")
    val publishedRegex = Regex("""<published>(.*?)</published>""")
    val thumbRegex = Regex("""<media:thumbnail[^>]+url=["']([^"']+)["']""")

    val videos = mutableListOf<YouTubeChannelVideo>()
    for (match in entryRegex.findAll(xml)) {
        val entry = match.groupValues[1]
        val vid = videoIdRegex.find(entry)?.groupValues?.get(1) ?: continue
        val title = titleRegex.find(entry)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: "Video"
        val pub = publishedRegex.find(entry)?.groupValues?.get(1) ?: ""
        val thumb = thumbRegex.find(entry)?.groupValues?.get(1) ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"

        val formattedDate = pub.take(10)
        videos.add(
            YouTubeChannelVideo(
                id = vid,
                title = title,
                thumbnailUrl = thumb,
                published = formattedDate,
            ),
        )
    }

    return Pair(cleanChannelTitle, videos)
}

private fun unescapeXml(text: String): String {
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .trim()
}

private fun playVideo(
    context: Context,
    videoId: String,
    title: String? = null,
    onComplete: () -> Unit = {},
    onError: (String) -> Unit,
) {
    CoroutineScope(Dispatchers.IO).launch {
        val sources = YouTubePlayables.resolve(videoId)
        withContext(Dispatchers.Main) {
            onComplete()
            if (sources.isEmpty()) {
                val opened = openYouTubeVideo(context, videoId, title)
                if (!opened) onError("Could not play this YouTube video")
            } else {
                launchPlayer(context, sources, title ?: "YouTube")
            }
        }
    }
}

private fun launchPlayer(context: Context, sources: List<StreamSource>, title: String = "YouTube") {
    if (sources.isEmpty()) return
    val sourcesJson = JSONArray().apply {
        sources.forEach { source ->
            val headersObj = if (source.headers.isNotEmpty()) {
                JSONObject(source.headers)
            } else {
                JSONObject()
            }
            put(JSONObject().apply {
                put("name", source.name)
                put("url", source.url)
                put("headers", headersObj)
                put("isM3u8", source.isM3u8)
                put("isMpd", source.isMpd)
                put("provider", "YouTube")
            })
        }
    }
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", title)
        putExtra("sources", sourcesJson.toString())
        putExtra("askServer", false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
