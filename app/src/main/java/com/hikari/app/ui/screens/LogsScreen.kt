package com.hikari.app.ui.screens

import com.hikari.app.ui.components.LocalHideHelp
import com.hikari.app.i18n.tr
import com.hikari.app.i18n.I18n

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hikari.app.BuildConfig
import com.hikari.app.HikariApp
import com.hikari.app.data.DownloadsSaver
import com.hikari.app.data.Logs
import com.hikari.app.ui.components.GlassCard
import com.hikari.app.ui.components.GlassShape
import com.hikari.app.ui.components.SettingsPageHeader
import com.hikari.app.ui.navigation.LocalTaskbarInset
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Logs & diagnostics: the two rolling app logs and the crash log,
 * each with a Share button and a Save-to-storage button, plus a "Share all".
 *
 * The point is that a bug report never needs a screenshot again: the user opens
 * this page, taps Share on whatever the developer asked for, and Android's share
 * sheet hands over the real text.
 */
@Composable
fun LogsPage(app: HikariApp, onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }
    // Live tail: the ring is in memory, so a one-second refresh costs no disk
    // I/O. Pausing freezes the lines that are on screen while the app carries
    // on logging around them.
    var livePaused by remember { mutableStateOf(false) }
    var liveFilter by remember { mutableStateOf("") }
    var liveTick by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(livePaused) {
        while (!livePaused) {
            kotlinx.coroutines.delay(1_000)
            liveTick++
        }
    }
    val liveLines = remember(liveTick, liveFilter) {
        val q = liveFilter.trim()
        Logs.recentLines(300).filter { q.isBlank() || it.contains(q, ignoreCase = true) }
    }
    val tailState = rememberLazyListState()
    LaunchedEffect(liveLines.size, livePaused) {
        if (!livePaused && liveLines.isNotEmpty()) {
            tailState.scrollToItem(liveLines.lastIndex)
        }
    }
    // Log page position held across visits (see ScrollKeeper).
    val listState = com.hikari.app.ui.components.keptListState("logs")

    BackHandler { onBack() }

    val files = remember(refreshTick) { Logs.logFiles(context) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Clear of the floating taskbar (0 when there is no bar): this page
        // replaces the Settings list, so its last row has to clear the bar too.
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = LocalTaskbarInset.current + 16.dp,
        ),
    ) {
        item {
            // The same header as every settings folder page (see
            // SettingsPageHeader): one line, one title size.
            SettingsPageHeader(
                title = tr("Logs & diagnostics"),
                subtitle = tr("Share what the app recorded"),
                onBack = onBack,
            )
        }

        item {
            GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        tr("All logs"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Share or save the app logs and the crash log together."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(
                            icon = Icons.Filled.Share,
                            label = tr("Share all"),
                            primary = true,
                        ) {
                            val existing = Logs.existingFiles(context)
                            if (existing.isEmpty()) {
                                toast("No logs to share yet")
                            } else {
                                shareFiles(context, existing.map { it.file }, "Hikari logs")
                            }
                        }
                        ActionPill(
                            icon = Icons.Filled.Save,
                            label = tr("Save all"),
                            primary = false,
                        ) {
                            val existing = Logs.existingFiles(context)
                            if (existing.isEmpty()) {
                                toast("No logs to save yet")
                            } else {
                                val results = existing.map { saveLog(context, it.file) }
                                val ok = results.count { it.isSuccess }
                                toast(
                                    if (ok == existing.size) {
                                        "Saved $ok file(s) to " +
                                            DownloadsSaver.pathOf("")
                                    } else {
                                        "Could not save: " + failureReason(results)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Live log"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        ActionPill(
                            icon = if (livePaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            label = tr(if (livePaused) "Resume" else "Pause"),
                            primary = !livePaused,
                        ) { livePaused = !livePaused }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("The newest lines this session wrote, refreshed every second."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = liveFilter,
                        onValueChange = { liveFilter = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = {
                            Text(
                                tr("Filter by tag, message or thread"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                    ) {
                        if (liveLines.isEmpty()) {
                            Text(
                                if (liveFilter.isBlank()) tr("Nothing logged yet this session.")
                                else tr("No log lines match that filter."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                            )
                        } else {
                            LazyColumn(
                                state = tailState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                items(liveLines) { line ->
                                    val isError = line.contains("[crash]") ||
                                        line.contains("!:") ||
                                        line.contains("FAILED")
                                    Text(
                                        line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        lineHeight = 12.sp,
                                        color = if (isError) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${liveLines.size} line${if (liveLines.size == 1) "" else "s"} · " +
                                if (livePaused) tr("paused") else tr("live"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(liveLines.joinToString("\n")))
                            toast("Copied")
                        }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(tr("Copy"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
                Text(
                    tr("Log files"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    tr("Tap Share to send a file, or Save to keep it in Downloads."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        for (entry in files) {
            item(key = entry.key) {
                val exists = entry.file.exists() && entry.file.length() > 0L
                GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (entry.isCrash) Icons.Filled.BugReport else Icons.Filled.Description,
                                contentDescription = null,
                                tint = if (entry.isCrash && exists) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    if (exists) {
                                        "${formatSize(entry.file.length())} · updated ${formatTime(entry.file.lastModified())}"
                                    } else {
                                        "Empty — nothing recorded yet"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (exists) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionPill(
                                    icon = Icons.Filled.Share,
                                    label = tr("Share"),
                                    primary = true,
                                ) {
                                    shareFiles(context, listOf(entry.file), "Hikari ${entry.title}")
                                }
                                ActionPill(
                                    icon = Icons.Filled.Save,
                                    label = tr("Save"),
                                    primary = false,
                                ) {
                                    val result = saveLog(context, entry.file)
                                    toast(
                                        if (result.isSuccess) {
                                            "Saved to " + result.getOrNull()
                                        } else {
                                            "Could not save: " +
                                                (result.exceptionOrNull()?.message ?: "unknown reason")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                if (!LocalHideHelp.current) {
                Text(
                    tr("Logs live only on this device and are never uploaded " + "automatically — nothing leaves your phone until you tap ") +
                        "Share or Save.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showClearDialog = true }) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Clear all logs"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(tr("Clear all logs?")) },
            text = { Text(tr("The two app logs and the crash log will be deleted from this device.")) },
            confirmButton = {
                TextButton(onClick = {
                    Logs.clear(context)
                    showClearDialog = false
                    refreshTick++
                    toast("Logs cleared")
                }) { Text(tr("Clear"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(tr("Cancel")) }
            },
        )
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(GlassShape)
            .background(
                if (primary) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Hand one or more files to Android's share sheet (FileProvider URIs only). */
internal fun shareFiles(context: Context, files: List<File>, subject: String) {
    val uris = ArrayList<Uri>()
    files.forEach { file ->
        runCatching {
            uris.add(
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file,
                )
            )
        }
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, I18n.t("Nothing to share"), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(
        if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
    ).apply {
        type = "text/plain"
        if (uris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(
            Intent.EXTRA_TEXT,
            "Hikari diagnostics · ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share logs"))
    }.onFailure {
        Toast.makeText(context, I18n.t("No app available to share"), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Save one log into `Downloads/Hikari` (see [DownloadsSaver]) and record WHY it
 * failed when it does, so the next "could not save" report carries its reason in
 * the app log instead of being a dead end.
 */
private fun saveLog(context: Context, file: File): Result<String> {
    val result = DownloadsSaver.save(context, file, "hikari-" + file.name, "text/plain")
    result.exceptionOrNull()?.let { Logs.logError("Downloads", "save ${file.name} failed", it) }
    return result
}

/** The first real reason among a batch of saves, for the "Save all" message. */
private fun failureReason(results: List<Result<String>>): String =
    results.firstNotNullOfOrNull { it.exceptionOrNull()?.message } ?: "unknown reason"

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(millis: Long): String =
    runCatching { SimpleDateFormat("d MMM, HH:mm", Locale.US).format(Date(millis)) }
        .getOrDefault("—")
