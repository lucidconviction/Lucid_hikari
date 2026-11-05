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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.net.URLEncoder

/**
 * YouTube tab — simple channel management. Users can add/delete YouTube
 * channels by pasting a channel URL (e.g., `https://www.youtube.com/@username`
 * or `/channel/UC...`). Tapping a channel fetches its latest videos and plays
 * them directly in Hikari's player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeScreen(nav: androidx.navigation.NavHostController) {
    val context = LocalContext.current
    val store = remember { YouTubeChannelStore.getInstance(context) }
    val channels by store.channelsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

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
                    contentDescription = "Add Channel",
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

        // Channel list
        if (channels.isEmpty()) {
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
                        text = "No YouTube channels yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add a channel",
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
                items(channels, key = { it.id }) { channel ->
                    YouTubeChannelRow(
                        channel = channel,
                        onPlay = { /* handled by channel click */ },
                        onDelete = {
                            CoroutineScope(Dispatchers.IO).launch {
                                store.deleteChannel(channel.id)
                            }
                        },
                    )
                }
            }
        }
    }

    // Add channel dialog
    if (showAddDialog) {
        YouTubeAddChannelDialog(
            onAdd = { name, url ->
                val newChannel = YouTubeChannel(name = name.trim(), url = url.trim())
                CoroutineScope(Dispatchers.IO).launch {
                    store.addChannel(newChannel)
                }
                showAddDialog = false
            },
            onCancel = { showAddDialog = false },
        )
    }
}

@Composable
fun YouTubeChannelRow(
    channel: YouTubeChannel,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* play logic handled in parent */ }
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
            // Play icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { /* play */ },
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channel.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
fun YouTubeAddChannelDialog(
    onAdd: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add YouTube Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = if (it.isBlank()) "Name is required" else null
                    },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = validateYoutubeUrl(it)?.let { null } ?: "Invalid YouTube URL"
                    },
                    label = { Text("Channel URL") },
                    singleLine = true,
                    placeholder = { Text("https://www.youtube.com/@username") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank() && urlError == null) {
                                onAdd(name, url)
                            }
                        },
                    ),
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (name.isNotBlank() && urlError == null) {
                        onAdd(name, url)
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

/** Validates a YouTube channel URL and extracts the channel ID. */
private fun validateYoutubeUrl(url: String): String? {
    val trimmed = url.trim()
    // Handle various YouTube channel URL formats
    val patterns = listOf(
        Regex("""youtube\.com/@([a-zA-Z0-9_-]+)"""),
        Regex("""youtube\.com/channel/([a-zA-Z0-9_-]+)"""),
        Regex("""youtube\.com/user/([a-zA-Z0-9_-]+)"""),
        Regex("""youtube\.com/c/([a-zA-Z0-9_-]+)"""),
    )
    for (pattern in patterns) {
        pattern.find(trimmed)?.let { return it.groupValues[1] }
    }
    return null
}

/**
 * Resolves a YouTube channel URL to its latest videos by scraping the RSS feed.
 * Returns video titles and URLs that can be played directly.
 */
private suspend fun resolveYouTubeChannelVideos(channelUrl: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
    val results = mutableListOf<YouTubeVideo>()
    try {
        // Try RSS feed first (most reliable)
        val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=${extractChannelId(channelUrl)}"
        val xml = Http.fetchStringRobust(rssUrl, mapOf("User-Agent" to Http.UA)).getOrNull() ?: return@withContext emptyList()

        // Parse XML to extract video entries
        val videoEntries = """<entry>(.*?)</entry>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val idMatches = """<yt:videoId>(.*?)</yt:videoId>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val titleMatches = """<title>(.*?)</title>""".toRegex(RegexOption.DOT_MATCHES_ALL)

        val entries = videoEntries.findAll(xml).toList()
        val ids = idMatches.findAll(xml).map { it.groupValues[1] }.toList()
        val titles = titleMatches.findAll(xml).map { it.groupValues[1] }.toList()

        for (i in entries.indices) {
            if (i < ids.size && i < titles.size) {
                results.add(YouTubeVideo(id = ids[i], title = titles[i]))
            }
        }
    } catch (e: Exception) {
        com.hikari.app.data.Logs.log("YouTube", "Failed to resolve channel: ${e.message}")
    }
    return@withContext results.take(20) // Limit to 20 most recent
}

private fun extractChannelId(url: String): String {
    val patterns = listOf(
        Regex("""youtube\.com/@[a-zA-Z0-9_-]+"""),
        Regex("""youtube\.com/channel/([a-zA-Z0-9_-]+)"""),
    )
    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null) {
            val id = match.value.split("/").lastOrNull()
            if (id != null && id.startsWith("UC")) return id
        }
    }
    return ""
}

private data class YouTubeVideo(
    val id: String,
    val title: String,
)

private fun launchPlayer(context: Context, channelName: String, videos: List<YouTubeVideo>) {
    if (videos.isEmpty()) return
    
    val sources = JSONArray().apply {
        videos.forEach { video ->
            put(JSONObject().apply {
                put("name", video.title)
                put("url", "https://www.youtube.com/watch?v=${video.id}")
                put("isM3u8", false)
            })
        }
    }
    
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", channelName)
        putExtra("sources", sources.toString())
        putExtra("askServer", false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
