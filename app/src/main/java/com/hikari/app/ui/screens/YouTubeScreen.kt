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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hikari.app.cs3.YouTubePlayables
import com.hikari.app.data.YouTubeChannel
import com.hikari.app.data.stores.YouTubeChannelStore
import com.hikari.app.net.Http
import com.hikari.app.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * YouTube tab — simple channel and video management.
 * Videos are shown first, then channels. Users can add/delete freely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(nav: androidx.navigation.NavHostController) {
    val context = LocalContext.current
    val store = remember { YouTubeChannelStore.getInstance(context) }
    val items by store.channelsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    val videos = items.filter { it.itemType == "video" }
    val channels = items.filter { it.itemType == "channel" }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
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

        // Error message
        showError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Content - videos first, then channels
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
                        text = "No YouTube items yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add a video or channel",
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
                // Videos first
                if (videos.isNotEmpty()) {
                    items(videos, key = { it.id }) { item ->
                        YouTubeItemRow(
                            item = item,
                            isVideo = true,
                            onPlay = {
                                val videoId = extractVideoId(item.url)
                                if (videoId != null) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val sources = YouTubePlayables.resolve(videoId)
                                        withContext(Dispatchers.Main) {
                                            if (sources.isEmpty()) {
                                                showError = "Could not play this YouTube video"
                                            } else {
                                                launchPlayer(context, sources)
                                            }
                                        }
                                    }
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

                // Then channels
                if (channels.isNotEmpty()) {
                    items(channels, key = { it.id }) { item ->
                        YouTubeItemRow(
                            item = item,
                            isVideo = false,
                            onPlay = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val videoIds = resolveYouTubeChannelVideos(item.url)
                                    if (videoIds.isEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            showError = "Could not fetch videos for this channel"
                                        }
                                    } else {
                                        // Resolve all channel videos to actual streams
                                        val allSources = mutableListOf<com.hikari.app.data.StreamSource>()
                                        for (vid in videoIds) {
                                            allSources.addAll(YouTubePlayables.resolve(vid))
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (allSources.isEmpty()) {
                                                showError = "Could not play videos from this channel"
                                            } else {
                                                launchPlayer(context, allSources)
                                            }
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

    // Add dialog
    if (showAddDialog) {
        YouTubeAddDialog(
            onAdd = { name, url, type ->
                val newItem = YouTubeChannel(name = name.trim(), url = url.trim(), itemType = type)
                CoroutineScope(Dispatchers.IO).launch {
                    store.addChannel(newItem)
                }
                showAddDialog = false
            },
            onCancel = { showAddDialog = false },
        )
    }
}

@Composable
fun YouTubeItemRow(
    item: YouTubeChannel,
    isVideo: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(4.dp),
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
            // Type icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isVideo) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Filled.VideoFile else Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = if (isVideo) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Item info - just show URL, no title
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isVideo) "Video" else "Channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAddDialog(
    onAdd: (String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("video") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add YouTube Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type selector with radio buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == "video",
                        onClick = { selectedType = "video" },
                    )
                    Text("Video", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = selectedType == "channel",
                        onClick = { selectedType = "channel" },
                    )
                    Text("Channel", style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = if (it.isBlank()) "Name is required" else null
                    },
                    label = { Text(if (selectedType == "video") "Video Title" else "Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError != null,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        val (type, _) = validateYoutubeUrl(it)
                        urlError = when {
                            type == null -> "Invalid YouTube URL"
                            selectedType == "video" && type == "channel" -> "Please enter a video URL"
                            selectedType == "channel" && type == "video" -> "Please enter a channel URL"
                            else -> null
                        }
                    },
                    label = { Text("YouTube URL") },
                    singleLine = true,
                    placeholder = {
                        Text(
                            if (selectedType == "video")
                                "https://www.youtube.com/watch?v=..."
                            else
                                "https://www.youtube.com/@username"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank() && urlError == null) {
                                onAdd(name, url, selectedType)
                            }
                        },
                    ),
                    isError = urlError != null,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (name.isNotBlank() && urlError == null) {
                        onAdd(name, url, selectedType)
                    }
                },
                enabled = name.isNotBlank() && urlError == null,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}

/** Validates a YouTube URL (channel or video) and returns (type, id). */
private fun validateYoutubeUrl(url: String): Pair<String?, String?> {
    val trimmed = url.trim()
    // Strip query parameters and fragments before matching
    val cleanUrl = trimmed.split("?").first().split("#").first()

    // Video URL patterns
    val videoPatterns = listOf(
        Regex("""youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})"""),
        Regex("""youtube\.com/v/([a-zA-Z0-9_-]{11})"""),
        Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
        Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})"""),
    )
    for (pattern in videoPatterns) {
        pattern.find(cleanUrl)?.let { return Pair("video", it.groupValues[1]) }
    }

    // Channel URL patterns
    val channelPatterns = listOf(
        Regex("""youtube\.com/@([a-zA-Z0-9_-]+)"""),
        Regex("""youtube\.com/channel/([a-zA-Z0-9_-]+)"""),
        Regex("""youtube\.com/user/([a-zA-Z0-9_-]+)"""),
        Regex("""youtube\.com/c/([a-zA-Z0-9_-]+)"""),
    )
    for (pattern in channelPatterns) {
        pattern.find(cleanUrl)?.let { return Pair("channel", it.groupValues[1]) }
    }

    return Pair(null, null)
}

private fun extractVideoId(url: String): String? {
    val (type, id) = validateYoutubeUrl(url)
    return if (type == "video") id else null
}

/**
 * Resolves a YouTube channel URL to its latest video IDs by scraping the RSS feed.
 * Returns video IDs in order from newest to oldest.
 */
private suspend fun resolveYouTubeChannelVideos(channelUrl: String): List<String> = withContext(Dispatchers.IO) {
    val results = mutableListOf<String>()
    try {
        val channelId = extractChannelId(channelUrl)
        if (channelId.isEmpty()) return@withContext emptyList()
        val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val xml = Http.fetchStringRobust(rssUrl, mapOf("User-Agent" to Http.UA)).getOrNull() ?: return@withContext emptyList()

        val idMatches = """<yt:videoId>(.*?)</yt:videoId>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val ids = idMatches.findAll(xml).map { it.groupValues[1] }.toList()

        results.addAll(ids)
    } catch (e: Exception) {
        com.hikari.app.data.Logs.log("YouTube", "Failed to resolve channel: ${e.message}")
    }
    return@withContext results.take(20) // Limit to 20 most recent
}

private fun extractChannelId(url: String): String {
    val cleanUrl = url.trim().split("?").first().split("#").first()
    val pattern = Regex("""youtube\.com/(?:@|channel/|user/|c/)([a-zA-Z0-9_-]+)""")
    return pattern.find(cleanUrl)?.groupValues?.get(1) ?: ""
}

private fun launchPlayer(context: Context, sources: List<com.hikari.app.data.StreamSource>) {
    if (sources.isEmpty()) return
    val sourcesJson = JSONArray().apply {
        sources.forEach { source ->
            put(JSONObject().apply {
                put("name", source.name)
                put("url", source.url)
                put("isM3u8", source.isM3u8)
                put("isMpd", source.isMpd)
                put("provider", "YouTube")
            })
        }
    }
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", "YouTube")
        putExtra("sources", sourcesJson.toString())
        putExtra("askServer", false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
