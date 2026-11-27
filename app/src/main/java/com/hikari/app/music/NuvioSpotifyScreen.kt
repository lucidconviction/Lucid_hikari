package com.hikari.app.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Import a public Spotify playlist's track list into a local Lucid playlist. */
@Composable
internal fun NuvioSpotifyScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by MusicSpotifySession.state.collectAsState()
    val sync by MusicSpotifySync.state.collectAsState()
    val loginLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) {
        MusicSpotifySession.init(context)
    }
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SpotifyImport.Imported?>(null) }
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
                    contentDescription = "Close import",
                    tint = NuvioMusicPalette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Spotify",
                    color = NuvioMusicPalette.text,
                    fontFamily = NuvioDisplay,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Account sync or a public playlist link",
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 10.5.sp,
                )
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NuvioMusicPalette.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (session.connected) {
                            "Spotify · ${session.accountName ?: "connected"}"
                        } else {
                            "Spotify account"
                        },
                        color = NuvioMusicPalette.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (session.connected) {
                            "Liked Songs and your playlists sync into Library"
                        } else {
                            "Sign in to copy your Liked Songs and playlists"
                        },
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                if (session.connected) {
                    NuvioPill(
                        "Sync",
                        true,
                        onClick = {
                            MusicSpotifySession.init(context)
                            MusicSpotifySync.sync(context)
                        },
                    )
                } else {
                    NuvioPill(
                        "Connect",
                        true,
                        onClick = {
                            loginLauncher.launch(
                                android.content.Intent(context, SpotifyLoginActivity::class.java),
                            )
                        },
                    )
                }
            }
            if (session.connected) {
                Spacer(Modifier.height(6.dp))
                NuvioPill(
                    "Sign out of Spotify",
                    false,
                    onClick = { MusicSpotifySession.clear(context) },
                )
            }
            if (sync.busy) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = NuvioMusicPalette.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        sync.progress ?: "Syncing…",
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
            }
            sync.result?.let {
                Text(it, color = NuvioMusicPalette.accentTeal, fontSize = 11.sp)
            }
            sync.error?.let {
                Text(it, color = NuvioMusicPalette.error, fontSize = 11.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "PUBLIC PLAYLIST LINK",
                color = NuvioMusicPalette.accentTeal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Playlist link or ID",
                color = NuvioMusicPalette.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = NuvioMusicPalette.text,
                    fontSize = 13.sp,
                    fontFamily = NuvioSans,
                ),
                cursorBrush = SolidColor(NuvioMusicPalette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(NuvioMusicPalette.surface)
                    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(11.dp)),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (url.isBlank()) {
                            Text(
                                "https://open.spotify.com/playlist/…",
                                color = NuvioMusicPalette.textMuted,
                                fontSize = 12.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NuvioPill(
                    "Import",
                    true,
                    onClick = {
                        if (!loading) {
                            loading = true
                            error = null
                            result = null
                            scope.launch {
                                runCatching { SpotifyImport.import(context, url) }
                                    .onSuccess { result = it }
                                    .onFailure { error = it.message ?: "Import failed" }
                                loading = false
                            }
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NuvioMusicPalette.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            error?.let {
                Text(it, color = NuvioMusicPalette.error, fontSize = 12.sp)
            }
            result?.let {
                Text(
                    "\"${it.playlist}\" imported",
                    color = NuvioMusicPalette.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${it.added} of ${it.total} tracks added to your Library playlists.",
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                NuvioPill("Done", true, onClick = onClose)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Only the public track list is read. Songs are matched to Deezer, and anything missing is played from YouTube by title.",
                color = NuvioMusicPalette.textMuted,
                fontSize = 10.5.sp,
            )
        }
    }
}
