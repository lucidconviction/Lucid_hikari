package com.hikari.app.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.hikari.app.HikariApp
import com.hikari.app.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * The Music page's player. One ExoPlayer for the whole app, audio-only (the
 * video track is disabled even when the YouTube engine hands us a DASH
 * manifest — the visual is the page's artwork, not the music video).
 *
 * The queue is resolved lazily: just the current track is handed to ExoPlayer
 * and the next one is resolved when it is actually needed, so a 50-track
 * playlist does not fetch 50 manifests up front.
 */
object MusicPlayer {

    enum class RepeatMode { OFF, ALL, ONE }

    data class State(
        val track: MusicTrack? = null,
        val queue: List<MusicTrack> = emptyList(),
        val index: Int = 0,
        val playing: Boolean = false,
        val loading: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val shuffle: Boolean = false,
        val repeat: RepeatMode = RepeatMode.OFF,
        val quality: String = "",
        val error: String? = null,
        val lyrics: MusicLyrics = MusicLyrics.EMPTY,
        val lyricsLoading: Boolean = false,
        val downloadState: MusicLibrary.DownloadState? = null,
    ) {
        val hasNext: Boolean
            get() = queue.isNotEmpty() && (index < queue.lastIndex || repeat == RepeatMode.ALL)
        val hasPrevious: Boolean
            get() = queue.isNotEmpty() && (index > 0 || positionMs > 3000L)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var player: ExoPlayer? = null
    private var originalQueue: List<MusicTrack> = emptyList()
    private var loadJob: Job? = null
    private var playToken = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        MusicLibrary.init(context)
        if (player != null) return
        main.post {
            if (player == null) createPlayer()
        }
    }

    private fun createPlayer() {
        val context = appContext ?: runCatching { HikariApp.instance }.getOrNull() ?: return
        MusicLibrary.init(context)
        val exo = ExoPlayer.Builder(context).build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        exo.setHandleAudioBecomingNoisy(true)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(playing = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> _state.value = _state.value.copy(loading = true)
                    Player.STATE_READY -> _state.value = _state.value.copy(
                        loading = false,
                        durationMs = exo.duration.takeIf { it > 0 } ?: _state.value.durationMs,
                    )
                    Player.STATE_ENDED -> onEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    loading = false,
                    playing = false,
                    error = "Couldn't play this track (${error.errorCodeName})",
                )
            }
        })
        player = exo
        scope.launch {
            while (isActive) {
                val p = player
                val current = _state.value.track
                if (p != null && current != null) {
                    val pos = runCatching { p.currentPosition }.getOrDefault(0L)
                    val dur = runCatching { p.duration }.getOrDefault(0L)
                    _state.value = _state.value.copy(
                        positionMs = pos.coerceAtLeast(0L),
                        durationMs = if (dur > 0) dur else _state.value.durationMs,
                    )
                }
                delay(500L)
            }
        }
    }

    /** Plays [track] with [queue] as the list around it. */
    fun play(track: MusicTrack, queue: List<MusicTrack>? = null) {
        val context = appContext ?: runCatching { HikariApp.instance }.getOrNull() ?: return
        init(context)
        main.post {
            if (player == null) createPlayer()
            val sourceQueue = (queue ?: listOf(track)).ifEmpty { listOf(track) }
            originalQueue = sourceQueue
            val ordered = if (_state.value.shuffle) {
                listOf(track) + sourceQueue.filterNot { it.id == track.id }.shuffled()
            } else sourceQueue
            val index = ordered.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
            _state.value = _state.value.copy(
                queue = ordered,
                index = index,
                track = track,
                positionMs = 0L,
                durationMs = track.durationSeconds * 1000L,
                error = null,
            )
            loadCurrent(track)
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) {
                _state.value.track?.let { loadCurrent(it) }
            } else {
                p.play()
            }
        }
    }

    fun pause() {
        player?.pause()
    }

    fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val nextIndex = when {
            s.shuffle -> {
                if (s.queue.size <= 1) s.index
                else {
                    var candidate = s.index
                    while (candidate == s.index) candidate = (0 until s.queue.size).random()
                    candidate
                }
            }
            s.index < s.queue.lastIndex -> s.index + 1
            s.repeat == RepeatMode.ALL -> 0
            else -> return
        }
        playAt(nextIndex)
    }

    fun previous() {
        val p = player
        val s = _state.value
        if (p != null && p.currentPosition > 3000L) {
            p.seekTo(0L)
            return
        }
        if (s.queue.isEmpty()) return
        val prevIndex = when {
            s.index > 0 -> s.index - 1
            s.repeat == RepeatMode.ALL -> s.queue.lastIndex
            else -> return
        }
        playAt(prevIndex)
    }

    fun playAt(index: Int) {
        val s = _state.value
        val track = s.queue.getOrNull(index) ?: return
        _state.value = s.copy(index = index, track = track, positionMs = 0L, durationMs = track.durationSeconds * 1000L)
        loadCurrent(track)
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        _state.value = _state.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    fun setShuffle(on: Boolean) {
        val s = _state.value
        if (s.shuffle == on) return
        if (on) {
            originalQueue = s.queue
            val current = s.track
            val rest = s.queue.filterNot { it.id == current?.id }.shuffled()
            val queue = listOfNotNull(current) + rest
            _state.value = s.copy(shuffle = true, queue = queue, index = 0)
        } else {
            val current = s.track
            val restored = originalQueue.ifEmpty { s.queue }
            val index = restored.indexOfFirst { it.id == current?.id }.takeIf { it >= 0 } ?: 0
            _state.value = s.copy(shuffle = false, queue = restored, index = index)
        }
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeat = next)
    }

    fun setRepeat(mode: RepeatMode) {
        _state.value = _state.value.copy(repeat = mode)
    }

    private fun onEnded() {
        val s = _state.value
        if (s.repeat == RepeatMode.ONE) {
            player?.seekTo(0L)
            player?.play()
            return
        }
        if (s.hasNext) next()
        else {
            player?.pause()
            player?.seekTo(0L)
            _state.value = s.copy(playing = false, positionMs = 0L)
        }
    }

    private fun loadCurrent(track: MusicTrack) {
        val token = ++playToken
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                lyrics = MusicLyrics.EMPTY,
                lyricsLoading = true,
                quality = "",
            )
            launch {
                val lyrics = MusicLyricsService.forTrack(track)
                if (token == playToken) {
                    _state.value = _state.value.copy(lyrics = lyrics, lyricsLoading = false)
                }
            }
            MusicLibrary.addRecent(track)
            val downloaded = MusicLibrary.downloadedFor(track.id)?.let { MusicLibrary.downloadFile(it) }
            val localFile = downloaded?.takeIf { it.isFile && it.length() > 1024L }
            val stream = if (localFile != null) null else withContext(Dispatchers.IO) {
                MusicSourceResolver.resolve(track, MusicLibrary.preferLossless.value)
            }
            if (token != playToken) return@launch
            if (localFile == null && stream == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    playing = false,
                    error = "No playable audio source for this track.",
                )
                return@launch
            }
            if (player == null) createPlayer()
            val exo = player ?: return@launch

            val uri = if (localFile != null) Uri.fromFile(localFile) else Uri.parse(stream!!.url)
            val headers = stream?.headers.orEmpty()
            val userAgent = headers["User-Agent"] ?: Http.UA
            val dataSource = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
            val mediaItem = ExoMediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build(),
                )
                .build()
            val mediaSource = when {
                localFile != null -> ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem)
                stream!!.isDash || uri.toString().contains(".mpd", true) ->
                    DashMediaSource.Factory(dataSource).createMediaSource(mediaItem)
                stream.isHls || uri.toString().contains(".m3u8", true) ->
                    HlsMediaSource.Factory(dataSource).createMediaSource(mediaItem)
                else -> ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem)
            }
            exo.setMediaSource(mediaSource)
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            exo.prepare()
            exo.play()
            _state.value = _state.value.copy(
                loading = false,
                quality = when {
                    localFile != null -> MusicLibrary.downloadedFor(track.id)?.quality ?: "Offline"
                    stream != null -> stream.label
                    else -> ""
                },
            )
        }
    }

    /** A rough "this track is playing near [positionMs]" helper for the lyrics view. */
    fun activeLyricIndex(lyrics: MusicLyrics, positionMs: Long): Int {
        if (!lyrics.synced || lyrics.lines.isEmpty()) return -1
        var result = -1
        for ((i, line) in lyrics.lines.withIndex()) {
            if (line.timeMs <= positionMs + 250) result = i else break
        }
        return result
    }

    /** Finds the track whose display duration most closely matches [seconds]. */
    internal fun closest(tracks: List<MusicTrack>, seconds: Int): MusicTrack? {
        if (tracks.isEmpty()) return null
        if (seconds <= 0) return tracks.first()
        return tracks.minByOrNull { abs(it.durationSeconds - seconds) }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun refreshDownloadState() {
        val track = _state.value.track ?: return
        _state.value = _state.value.copy(
            downloadState = MusicLibrary.downloadStates.value[track.id],
        )
    }

    fun openDownloadState() {
        // The screen observes MusicLibrary.downloadStates directly; keeping this
        // function lets callers refresh after starting a download.
        refreshDownloadState()
    }

    @Suppress("unused")
    private fun fileOf(track: MusicTrack): File? =
        MusicLibrary.downloadedFor(track.id)?.let { MusicLibrary.downloadFile(it) }
}
