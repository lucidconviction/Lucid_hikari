@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hikari.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.HikariApp
import com.hikari.app.iptv.PortalGenerator
import kotlinx.coroutines.launch

/**
 * "Generate Portals" from the PlayTorrio Live TV port: pulls the Cloud Vault
 * Xtream database, verifies accounts, and saves the working ones as normal
 * Hikari IPTV playlists (so they appear in the IPTV tab, the Channel Wall and
 * the sports source search at once).
 */
@Composable
internal fun PortalGeneratorDialog(app: HikariApp, onDismiss: () -> Unit) {
    var runKey by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Starting…") }
    var tried by remember { mutableIntStateOf(0) }
    var found by remember { mutableIntStateOf(0) }
    var added by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<PortalGenerator.Generated>>(emptyList()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(runKey) {
        running = true
        error = null
        results = emptyList()
        found = 0
        tried = 0
        added = 0
        try {
            val list = PortalGenerator.generate(target = 5) { p ->
                status = p.status
                tried = p.tried
                found = p.found
            }
            results = list
            if (list.isEmpty()) {
                status = "No working portals found this round. Try again — the pool is shuffled."
                running = false
                return@LaunchedEffect
            }
            status = "Saving ${list.size} portal${if (list.size == 1) "" else "s"} to IPTV…"
            added = PortalGenerator.save(app, list)
            status = "Added $added portal${if (added == 1) "" else "s"} to IPTV and the Channel Wall."
        } catch (e: Throwable) {
            error = e.message ?: "Couldn't generate portals."
            status = ""
        } finally {
            running = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Generate Xtream portals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Cloud Vault · verifies live Xtream accounts and adds the working ones to your IPTV playlists.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        if (running) status else status.ifBlank { "Done." },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (running && (tried > 0 || found > 0)) {
                    Text(
                        "Tried $tried · verified $found of 5",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (results.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().height(190.dp)) {
                        items(results, key = { it.m3uUrl }) { p ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(
                                    p.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        p.baseUrl.substringAfter("://").substringBefore('/'),
                                        p.expiry,
                                        p.maxConnections?.let { "$it conn" },
                                    ).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text("Close") }
        },
        dismissButton = {
            if (!running && (error != null || added == 0)) {
                TextButton(onClick = { runKey++ }) { Text("Try again") }
            }
        },
    )
}
