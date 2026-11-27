package com.hikari.app.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device music for the Music tab's Local page.
 *
 * Reads MediaStore's audio table (the same library every player sees), maps
 * rows to [MusicTrack] with a `content://` URI, and lets [MusicPlayer] play the
 * file directly, without a Qobuz/YouTube lookup.
 */
object LocalMusicRepository {

    const val PERMISSION_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
    const val PERMISSION_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) PERMISSION_MEDIA_AUDIO else PERMISSION_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            context.checkSelfPermission(requiredPermission()) == PackageManager.PERMISSION_GRANTED

    suspend fun scan(context: Context): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val out = ArrayList<MusicTrack>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val durationMs = cursor.getLong(durationCol)
                    if (durationMs in 1..4_000L) continue
                    val albumId = cursor.getLong(albumIdCol)
                    val art = if (albumId > 0) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId,
                        ).toString()
                    } else ""
                    out += MusicTrack(
                        id = "local:$id",
                        title = cursor.getString(titleCol)
                            ?.takeIf { it.isNotBlank() } ?: "Unknown Track",
                        artist = cursor.getString(artistCol)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                            ?: "Unknown Artist",
                        album = cursor.getString(albumCol)
                            ?.takeIf { it.isNotBlank() } ?: "Unknown Album",
                        coverUrl = art,
                        durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(0),
                        trackNumber = cursor.getInt(trackCol).takeIf { it > 0 },
                        localUri = ContentUris.withAppendedId(collection, id).toString(),
                    )
                }
            }
        }
        out
    }
}
