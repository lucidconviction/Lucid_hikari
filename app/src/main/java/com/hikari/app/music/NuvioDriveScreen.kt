package com.hikari.app.music

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Google Drive OAuth connection and downloads upload. */
@Composable
internal fun NuvioDriveScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val state by MusicDrive.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { MusicDrive.init(context) }
    Column(
        Modifier
            .fillMaxSize()
            .background(NuvioMusicPalette.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close Drive",
                    tint = NuvioMusicPalette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Google Drive",
                    color = NuvioMusicPalette.text,
                    fontFamily = NuvioDisplay,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.connected) {
                        state.email ?: "Connected"
                    } else {
                        "Upload your downloads with a Google account"
                    },
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 10.5.sp,
                )
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NuvioMusicPalette.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = NuvioMusicPalette.accentTeal,
                    modifier = Modifier.size(34.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.connected) "Drive is connected" else "Connect Google Drive",
                        color = NuvioMusicPalette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (!state.configured) {
                            "Add GOOGLE_DRIVE_CLIENT_ID and GOOGLE_DRIVE_CLIENT_SECRET to gradle.properties first."
                        } else if (state.connected) {
                            "Downloads upload into a \"Lucid Music\" folder using the drive.file scope."
                        } else {
                            "Google shows a code; approve it on your phone or computer."
                        },
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.connected) {
                    NuvioPill(
                        "Upload downloads",
                        true,
                        onClick = { MusicDrive.uploadDownloads(context) },
                    )
                    NuvioPill("Disconnect", false, onClick = { MusicDrive.disconnect() })
                } else {
                    NuvioPill(
                        if (state.busy) "Starting…" else "Connect",
                        true,
                        onClick = { MusicDrive.connect(context) },
                    )
                }
            }
            state.signIn?.let { signIn ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "Enter this code at ${signIn.url}",
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    signIn.code,
                    color = NuvioMusicPalette.accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NuvioPill(
                        "Open Google",
                        true,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(signIn.url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    NuvioPill("Cancel", false, onClick = { MusicDrive.cancel() })
                }
            }
            if (state.busy || state.total > 0) {
                Spacer(Modifier.height(14.dp))
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (state.uploaded.toFloat() / state.total.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = NuvioMusicPalette.accent,
                        trackColor = Color.White.copy(alpha = 0.08f),
                    )
                    Text(
                        "${state.uploaded} / ${state.total}",
                        color = NuvioMusicPalette.textMuted,
                        fontSize = 10.sp,
                    )
                }
            }
            state.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = NuvioMusicPalette.textSecondary, fontSize = 11.sp)
            }
        }
    }
}
