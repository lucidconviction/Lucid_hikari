package com.hikari.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.PortalEntry
import com.hikari.app.data.PortalNutzScraper
import com.hikari.app.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IptvNutZScreen(
    nav: androidx.navigation.NavHostController,
    onBack: () -> Unit,
    onAddPortal: (PortalEntry) -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var portals by remember { mutableStateOf<List<PortalEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        portals = PortalNutzScraper.loadCached()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).padding(16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onBack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Text("IPTV NutZ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                isLoading = true
                error = null
                scope.launch {
                    try {
                        val discovered = PortalNutzScraper.discoverPortals()
                        portals = discovered
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        isLoading = false
                    }
                }
            }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = if (isLoading) Color.Gray else Color.White)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (error != null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Warning, "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "Unknown error", color = Color.Red, fontSize = 14.sp)
                }
            }
        } else if (portals.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No portals found. Tap refresh to discover.", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            Text("${portals.size} portal(s) discovered", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(portals) { portal ->
                    PortalCard(
                        portal = portal,
                        onPlay = {
                            // Build M3U URL from portal
                            val m3uUrl = "${portal.url}/playlist.m3u8?username=${portal.username}&password=${portal.password}"
                            onAddPortal(portal)
                        },
                        onAdd = { onAddPortal(portal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalCard(portal: PortalEntry, onPlay: () -> Unit, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(portal.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                if (portal.isTrial == true) {
                    Text("Trial", color = Color(0xFFFF9800), fontSize = 11.sp)
                }
                if (portal.status == "Active") {
                    Icon(Icons.Filled.CheckCircle, "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                }
            }
            Text(portal.domain, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row {
                Text("Channels: ${portal.channelCount}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.width(16.dp))
                portal.expDate?.let { expDate ->
                    val daysLeft = (expDate - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)
                    Text("Expires in ${daysLeft.toInt()}d", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onAdd() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add to Playlist", fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onPlay() }) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White)
                }
            }
        }
    }
}
