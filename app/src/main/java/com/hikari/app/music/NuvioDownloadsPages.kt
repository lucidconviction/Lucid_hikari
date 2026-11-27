package com.hikari.app.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The full Downloads page behind Library → Downloads. */
@Composable
internal fun NuvioDownloadsPage(
    downloads: List<DownloadedMusic>,
    onClose: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onDelete: (DownloadedMusic) -> Unit,
    onUpload: (() -> Unit)? = null,
) {
    val tracks = downloads.map { it.track }
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
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NuvioMusicPalette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Downloads",
                    color = NuvioMusicPalette.text,
                    fontFamily = NuvioDisplay,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${downloads.size} songs · ${humanBytes(downloads.sumOf { it.fileSize })}",
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 10.5.sp,
                )
            }
            if (tracks.isNotEmpty()) {
                if (onUpload != null) {
                    NuvioPill("Drive", false, onClick = onUpload)
                    Spacer(Modifier.width(6.dp))
                }
                NuvioPill("Play all", true, onClick = { onPlay(tracks.first(), tracks) })
            }
        }
        if (downloads.isEmpty()) {
            NuvioEmptyState(
                title = "Nothing downloaded",
                message = "Use a song's ⋮ menu → Download and it will be saved here for offline playback.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 26.dp),
            ) {
                itemsIndexed(downloads, key = { i, d -> "dl:${d.track.id}:$i" }) { index, download ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(download.track, tracks) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NuvioArtwork(
                            url = download.track.coverUrl,
                            modifier = Modifier.size(50.dp),
                            cornerRadius = 9,
                            icon = Icons.Filled.Download,
                            iconSize = 22,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                download.track.title,
                                color = NuvioMusicPalette.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${download.track.artist} · ${download.quality}",
                                color = NuvioMusicPalette.textSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                humanBytes(download.fileSize),
                                color = NuvioMusicPalette.textMuted,
                                fontSize = 9.5.sp,
                            )
                        }
                        IconButton(
                            onClick = { onDelete(download) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete download",
                                tint = NuvioMusicPalette.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A user playlist opened from Library, as a full page instead of a sheet. */
@Composable
internal fun NuvioUserPlaylistPage(
    playlist: UserPlaylist,
    onClose: () -> Unit,
    onPlay: (MusicTrack, List<MusicTrack>?) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: (MusicTrack) -> Unit,
) {
    val tracks = playlist.tracks.toList()
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
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NuvioMusicPalette.text,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDeletePlaylist, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete playlist",
                    tint = NuvioMusicPalette.error.copy(alpha = 0.85f),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NuvioArtwork(
                url = tracks.firstOrNull()?.coverUrl,
                modifier = Modifier.size(92.dp),
                cornerRadius = 14,
                iconSize = 32,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.title,
                    color = NuvioMusicPalette.text,
                    fontFamily = NuvioDisplay,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${tracks.size} tracks",
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (tracks.isNotEmpty()) {
                    NuvioPill("Play", true, onClick = { onPlay(tracks.first(), tracks) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tracks.isEmpty()) {
            NuvioEmptyState(
                title = "Empty playlist",
                message = "Add songs from a track's ⋮ menu → Add to playlist.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 26.dp),
            ) {
                itemsIndexed(tracks, key = { i, t -> "pl:${t.id}:$i" }) { index, track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            NuvioTrackRow(
                                track = track,
                                index = index,
                                isCurrent = false,
                                isPlaying = false,
                                onClick = { onPlay(track, tracks) },
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        IconButton(
                            onClick = { onRemoveTrack(track) },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove from playlist",
                                tint = NuvioMusicPalette.textMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun humanBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
