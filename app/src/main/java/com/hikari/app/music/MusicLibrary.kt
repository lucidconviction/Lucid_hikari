package com.hikari.app.music

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hikari.app.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.musicDataStore: DataStore<Preferences> by preferencesDataStore(name = "music_library", corruptionHandler = com.hikari.app.data.ReplaceCorruptPreferences)

/**
 * The Music page's own store: liked songs, user playlists, recently played,
 * the chosen audio source, and downloaded songs. Kept out of the app-wide
 * settings store — it changes often and can grow to a few hundred tracks.
 */
object MusicLibrary {

    private val K_LIKED = stringPreferencesKey("liked")
    private val K_PLAYLISTS = stringPreferencesKey("playlists")
    private val K_RECENT = stringPreferencesKey("recent")
    private val K_DOWNLOADS = stringPreferencesKey("downloads")
    private val K_SOURCE = stringPreferencesKey("audio_source")
    private val K_VIDEO_MODE = booleanPreferencesKey("video_mode")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var store: DataStore<Preferences>? = null
    private var appContext: Context? = null
    @Volatile private var loaded = false

    private val _liked = MutableStateFlow<List<MusicTrack>>(emptyList())
    val liked: StateFlow<List<MusicTrack>> = _liked.asStateFlow()

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _recent = MutableStateFlow<List<MusicTrack>>(emptyList())
    val recent: StateFlow<List<MusicTrack>> = _recent.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadedMusic>>(emptyList())
    val downloads: StateFlow<List<DownloadedMusic>> = _downloads.asStateFlow()

    // YouTube first by default: it is the engine that always works. Lossless
    // (Qobuz Eclipse) is an opt-in in the music settings sheet.
    private val _preferLossless = MutableStateFlow(false)
    val preferLossless: StateFlow<Boolean> = _preferLossless.asStateFlow()

    /** Whether the page plays songs as music videos (YouTube) instead of audio. */
    private val _videoMode = MutableStateFlow(false)
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()

    data class DownloadState(val progress: Float, val running: Boolean, val message: String? = null)
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun init(context: Context) {
        if (store != null) return
        synchronized(this) {
            if (store != null) return
            appContext = context.applicationContext
            store = context.applicationContext.musicDataStore
            scope.launch { load() }
        }
    }

    private suspend fun load() {
        if (loaded) return
        val prefs = store?.data?.first() ?: return
        _liked.value = parseTracks(prefs[K_LIKED])
        _playlists.value = parsePlaylists(prefs[K_PLAYLISTS])
        _recent.value = parseTracks(prefs[K_RECENT])
        val downloads = parseDownloads(prefs[K_DOWNLOADS])
        _downloads.value = downloads.filter { downloadFile(it)?.isFile == true }
        _preferLossless.value = (prefs[K_SOURCE] ?: "youtube") == "flac"
        _videoMode.value = prefs[K_VIDEO_MODE] ?: false
        loaded = true
    }

    fun isLiked(trackId: String): Boolean = _liked.value.any { it.id == trackId }

    fun toggleLike(track: MusicTrack) {
        val list = _liked.value.toMutableList()
        val index = list.indexOfFirst { it.id == track.id }
        if (index >= 0) list.removeAt(index) else list.add(0, track)
        _liked.value = list
        persist(K_LIKED, tracksJson(list))
    }

    fun addRecent(track: MusicTrack) {
        val list = _recent.value.toMutableList()
        list.removeAll { it.id == track.id }
        list.add(0, track)
        if (list.size > 50) list.subList(50, list.size).clear()
        _recent.value = list
        persist(K_RECENT, tracksJson(list))
    }

    fun createPlaylist(title: String): UserPlaylist {
        val playlist = UserPlaylist(
            id = "custom_${System.currentTimeMillis()}",
            title = title.trim().ifBlank { "My Playlist" },
            createdAt = System.currentTimeMillis().toString(),
        )
        _playlists.value = listOf(playlist) + _playlists.value
        persistPlaylists()
        return playlist
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        persistPlaylists()
    }

    fun addTrackToPlaylist(playlistId: String, track: MusicTrack) {
        val list = _playlists.value.map { playlist ->
            if (playlist.id != playlistId) playlist
            else playlist.copy(tracks = playlist.tracks.toMutableList().also { tracks ->
                if (tracks.none { it.id == track.id }) tracks.add(track)
            })
        }
        _playlists.value = list
        persistPlaylists()
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id != playlistId) playlist
            else playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId }.toMutableList())
        }
        persistPlaylists()
    }

    fun setPreferLossless(value: Boolean) {
        _preferLossless.value = value
        persist(K_SOURCE, if (value) "flac" else "youtube")
    }

    /** Switches the whole page between audio-only and YouTube music videos. */
    fun setVideoMode(value: Boolean) {
        if (_videoMode.value == value) return
        _videoMode.value = value
        val dataStore = store ?: return
        scope.launch { runCatching { dataStore.edit { it[K_VIDEO_MODE] = value } } }
    }

    // ---- downloads ---------------------------------------------------------

    fun downloadedFor(trackId: String): DownloadedMusic? = _downloads.value.firstOrNull { it.track.id == trackId }

    fun downloadFile(download: DownloadedMusic): File? {
        val context = appContext ?: return null
        return File(File(context.filesDir, "music"), download.fileName)
    }

    fun totalDownloadedBytes(): Long = _downloads.value.sumOf { it.fileSize }

    fun download(track: MusicTrack) {
        if (_downloadStates.value[track.id]?.running == true) return
        if (downloadedFor(track.id) != null) return
        setDownloadState(track.id, 0f, true, null)
        scope.launch {
            try {
                val stream = MusicSourceResolver.resolveForDownload(track)
                    ?: throw IllegalStateException("No downloadable audio stream found")
                if (stream.isDash || stream.isHls) {
                    throw IllegalStateException("Downloads need a direct audio source; this track only has a video manifest")
                }
                val response = Http.getQuiet(stream.url, stream.headers)
                if (!response.isSuccessful) throw IllegalStateException("Download failed (HTTP ${response.code})")
                val context = appContext ?: throw IllegalStateException("No app context")
                val dir = File(context.filesDir, "music").apply { mkdirs() }
                val name = safeName(track) + extensionFor(stream)
                val target = File(dir, name)
                val total = response.body?.contentLength() ?: -1L
                response.use { reply ->
                    reply.body?.byteStream()?.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (total > 0) {
                                    setDownloadState(track.id, (copied.toFloat() / total).coerceIn(0f, 1f), true, null)
                                }
                            }
                        }
                    }
                }
                if (!target.isFile || target.length() < 1024) {
                    target.delete()
                    throw IllegalStateException("Downloaded file was empty")
                }
                val entry = DownloadedMusic(
                    track = track,
                    fileName = name,
                    format = if (stream.lossless) "flac" else "m4a",
                    quality = stream.label,
                    fileSize = target.length(),
                    downloadedAt = System.currentTimeMillis(),
                )
                _downloads.value = listOf(entry) + _downloads.value.filterNot { it.track.id == track.id }
                persistDownloads()
                setDownloadState(track.id, 1f, false, null)
            } catch (e: Throwable) {
                setDownloadState(track.id, 0f, false, e.message ?: "Download failed")
            }
        }
    }

    fun deleteDownload(trackId: String) {
        _downloads.value.firstOrNull { it.track.id == trackId }?.let { downloadFile(it)?.delete() }
        _downloads.value = _downloads.value.filterNot { it.track.id == trackId }
        persistDownloads()
    }

    private fun setDownloadState(id: String, progress: Float, running: Boolean, message: String?) {
        _downloadStates.value = _downloadStates.value + (id to DownloadState(progress, running, message))
    }

    private fun extensionFor(stream: MusicStream): String = when {
        stream.lossless -> ".flac"
        stream.url.contains(".mp3", true) -> ".mp3"
        stream.url.contains(".mp4", true) -> ".mp4"
        else -> ".m4a"
    }

    private fun safeName(track: MusicTrack): String =
        "${track.artist} - ${track.title}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(90)
            .ifBlank { track.id }

    // ---- persistence -------------------------------------------------------

    private fun persist(key: Preferences.Key<String>, value: String) {
        val dataStore = store ?: return
        scope.launch { runCatching { dataStore.edit { it[key] = value } } }
    }

    private fun persistPlaylists() {
        val arr = JSONArray()
        _playlists.value.forEach { arr.put(it.toJson()) }
        persist(K_PLAYLISTS, arr.toString())
    }

    private fun persistDownloads() {
        val arr = JSONArray()
        _downloads.value.forEach { d ->
            arr.put(
                JSONObject()
                    .put("track", d.track.toJson())
                    .put("fileName", d.fileName)
                    .put("format", d.format)
                    .put("quality", d.quality)
                    .put("fileSize", d.fileSize)
                    .put("downloadedAt", d.downloadedAt),
            )
        }
        persist(K_DOWNLOADS, arr.toString())
    }

    private fun tracksJson(tracks: List<MusicTrack>): String =
        JSONArray().apply { tracks.forEach { put(it.toJson()) } }.toString()

    private fun parseTracks(raw: String?): List<MusicTrack> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<MusicTrack>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { runCatching { out += MusicTrack.fromJson(it) } }
        }
        return out
    }

    private fun parsePlaylists(raw: String?): List<UserPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<UserPlaylist>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { runCatching { out += UserPlaylist.fromJson(it) } }
        }
        return out
    }

    private fun parseDownloads(raw: String?): List<DownloadedMusic> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<DownloadedMusic>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val track = o.optJSONObject("track")?.let { MusicTrack.fromJson(it) } ?: continue
            out += DownloadedMusic(
                track = track,
                fileName = o.optString("fileName"),
                format = o.optString("format", "m4a"),
                quality = o.optString("quality", "HQ Audio"),
                fileSize = o.optLong("fileSize"),
                downloadedAt = o.optLong("downloadedAt"),
            )
        }
        return out
    }
}
