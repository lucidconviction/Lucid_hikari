package com.hikari.app.music

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Party mode: a LAN page guests can use to see the queue and add songs. */
@Composable
internal fun NuvioPartyScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val running by MusicPartyServer.running.collectAsState()
    val player by MusicPlayer.state.collectAsState()
    var url by remember { mutableStateOf(MusicPartyServer.url()) }
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
                    contentDescription = "Close party",
                    tint = NuvioMusicPalette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Party mode",
                    color = NuvioMusicPalette.text,
                    fontFamily = NuvioDisplay,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Guests on the same Wi-Fi can queue songs",
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
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = NuvioMusicPalette.accentTeal,
                    modifier = Modifier.size(34.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (running) "Party is live" else "Start a party",
                        color = NuvioMusicPalette.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (running) {
                            url ?: "Open this device's Wi-Fi address in a browser"
                        } else {
                            "The phone becomes a small web server. Guests search and add songs from their own phone."
                        },
                        color = NuvioMusicPalette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NuvioPill(
                    if (running) "Stop party" else "Start party",
                    true,
                    onClick = {
                        if (running) {
                            MusicPartyServer.stop()
                            url = null
                        } else {
                            scope.launch {
                                url = kotlinx.coroutines.withContext(
                                    kotlinx.coroutines.Dispatchers.IO,
                                ) {
                                    MusicPartyServer.start()
                                }
                            }
                        }
                    },
                )
                if (running && url != null) {
                    NuvioPill("Refresh link", false, onClick = { url = MusicPartyServer.url() })
                }
            }
            if (running && url != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    url.orEmpty(),
                    color = NuvioMusicPalette.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Scan the code or type the address on any phone or tablet on the same network.",
                    color = NuvioMusicPalette.textMuted,
                    fontSize = 10.5.sp,
                )
                remember(url) { qrBitmap(url) }?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Party join QR code",
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(190.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(10.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "NOW PLAYING",
                color = NuvioMusicPalette.accentTeal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                player.track?.title ?: "Nothing playing",
                color = NuvioMusicPalette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                player.track?.artist ?: "Start a song from Music first",
                color = NuvioMusicPalette.textSecondary,
                fontSize = 12.sp,
            )
            val upNext = player.queue.drop(player.index + 1).take(20)
            if (upNext.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "UP NEXT",
                    color = NuvioMusicPalette.accentTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                upNext.forEachIndexed { index, track ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            color = NuvioMusicPalette.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = NuvioMusicPalette.text,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist,
                                color = NuvioMusicPalette.textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun qrBitmap(content: String?): Bitmap? {
    if (content.isNullOrBlank()) return null
    return runCatching {
        val matrix = com.google.zxing.MultiFormatWriter().encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            640,
            640,
        )
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] =
                    if (matrix.get(x, y)) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
            }
        }
        Bitmap.createBitmap(
            pixels,
            matrix.width,
            matrix.height,
            Bitmap.Config.ARGB_8888,
        )
    }.getOrNull()
}
