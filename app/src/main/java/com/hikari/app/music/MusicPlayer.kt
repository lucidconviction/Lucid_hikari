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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.hikari.app.HikariApp
import com.hikari.app.music.audio.MusicAudioRenderersFactory
import com.hikari.app.music.audio.MusicBitPerfectUsb
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
        /** The Music page's Audio/Video choice for this playback session. */
        val videoMode: Boolean = false,
        /** True while the current media carries a video track and is drawn inline. */
        val videoPlaying: Boolean = false,
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

    private data class PreparedMedia(val source: MediaSource, val hasVideo: Boolean, val label: String)

    /** Crossfade length from Settings → Music → Fade (0 = gapless/off). */
    private fun crossfadeMs(): Long =
        MusicSettings.fadeSeconds.value.coerceIn(0, 4) * 1_000L

    /** Resolve the next track early enough for a slow YouTube lookup. */
    private fun crossfadePrepareMs(): Long = crossfadeMs() + 5_000L

    private var settingsWired = false

    private var fadePlayer: ExoPlayer? = null
    private var fadeJob: Job? = null
    private var fadeToken = 0
    private var fading = false
    private var fadePrepared = false
    private var fadeNextIndex = -1
    private var fadeNextTrack: MusicTrack? = null
    private var fadeHasVideo = false
    private var fadeQuality = ""

    fun init(context: Context) {
        appContext = context.applicationContext
        MusicLibrary.init(context)
        MusicSettings.init(context)
        MusicBitPerfectUsb.init(context)
        MusicScrobbler.init(context)
        wireSettings()
        if (player != null) return
        main.post {
            if (player == null) createPlayer()
        }
    }

    /** Applies the Nuvio/BitChord playback settings live. */
    private fun wireSettings() {
        if (settingsWired) return
        settingsWired = true
        scope.launch {
            MusicSettings.audioQuality.collect {
                val capCurrent = _state.value.quality.startsWith("YouTube")
                player?.let { p ->
                    p.applyAudioPreferences(_state.value.videoPlaying, capCurrent)
                }
                fadePlayer?.let { p ->
                    p.applyAudioPreferences(fadeHasVideo, fadeQuality.startsWith("YouTube"))
                }
            }
        }
        scope.launch {
            MusicSettings.skipSilence.collect { enabled ->
                player?.skipSilenceEnabled = enabled
                fadePlayer?.skipSilenceEnabled = enabled
            }
        }
        scope.launch {
            MusicSettings.playbackSpeed.collect { speed ->
                player?.setPlaybackSpeed(speed)
                fadePlayer?.setPlaybackSpeed(speed)
            }
        }
        scope.launch {
            MusicSettings.equalizerPreset.collect { preset -> MusicEqualizer.apply(preset) }
        }
        scope.launch {
            MusicSettings.fadeSeconds.collect { seconds ->
                if (seconds == 0) cancelCrossfade()
            }
        }
    }

    private fun ExoPlayer.applyAudioPreferences(hasVideo: Boolean, capBitrate: Boolean) {
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !hasVideo)
            // The quality ceiling is a YouTube data-saving choice. A lossless
            // Qobuz/local FLAC can legitimately exceed 320 kbps, so it must not
            // be filtered out by the same cap.
            .setMaxAudioBitrate(
                if (capBitrate) MusicSettings.audioQuality.value.maxKbps * 1_000
                else Int.MAX_VALUE,
            )
            .build()
    }

    private val mainListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(playing = isPlaying)
            // Keep the process in the foreground while music is audible so the
            // notification's previous/play-pause/next stay live when the user
            // leaves the app or locks the screen.
            if (isPlaying) appContext?.let { MusicPlaybackService.start(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.value = _state.value.copy(loading = true)
                Player.STATE_READY -> _state.value = _state.value.copy(
                    loading = false,
                    durationMs = player?.duration?.takeIf { it > 0 } ?: _state.value.durationMs,
                )
                Player.STATE_ENDED -> if (fading) promoteFade() else onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                loading = false,
                playing = false,
                error = "Couldn't play this track (${error.errorCodeName})",
            )
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                MusicEqualizer.attach(audioSessionId)
                MusicEqualizer.apply(MusicSettings.equalizerPreset.value)
            }
        }
    }

    private val fadeListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            abortCrossfade()
        }
    }

    private fun newPlayer(context: Context): ExoPlayer {
        val exo = ExoPlayer.Builder(context)
            // The LastWave USB sink wraps Media3's normal sink and only takes
            // over supported lossless PCM when the user enabled direct output.
            .setRenderersFactory(
                MusicAudioRenderersFactory(context, MusicBitPerfectUsb.audioOutput()),
            )
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        exo.setHandleAudioBecomingNoisy(true)
        exo.skipSilenceEnabled = MusicSettings.skipSilence.value
        exo.setPlaybackSpeed(MusicSettings.playbackSpeed.value)
        // Keep the CPU awake while audio is playing in the background; the
        // foreground service alone does not hold a wake lock.
        exo.setWakeMode(C.WAKE_MODE_NETWORK)
        return exo
    }

    private fun createPlayer() {
        val context = appContext ?: runCatching { HikariApp.instance }.getOrNull() ?: return
        MusicLibrary.init(context)
        val exo = newPlayer(context)
        exo.addListener(mainListener)
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
                    maybeStartCrossfade(p, pos, dur)
                    if (fading) updateCrossfadeVolumes(p, pos, dur)
                }
                delay(if (fading) 100L else 500L)
            }
        }
    }

    private fun createFadePlayer(): ExoPlayer? {
        fadePlayer?.let { return it }
        val context = appContext ?: runCatching { HikariApp.instance }.getOrNull() ?: return null
        val exo = newPlayer(context)
        exo.volume = 0f
        exo.addListener(fadeListener)
        fadePlayer = exo
        return exo
    }

    /** Plays [track] with [queue] as the list around it. */
    fun play(track: MusicTrack, queue: List<MusicTrack>? = null) {
        cancelCrossfade()
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
                videoMode = MusicLibrary.videoMode.value,
                videoPlaying = false,
            )
            loadCurrent(track)
        }
    }

    /** Switches the running player between audio and inline video without
     *  losing the place in the song. */
    fun setVideoMode(on: Boolean) {
        cancelCrossfade()
        val s = _state.value
        if (s.videoMode == on) return
        val at = (runCatching { player?.currentPosition }.getOrNull() ?: s.positionMs)
            .coerceAtLeast(0L)
        _state.value = s.copy(videoMode = on, positionMs = at)
        s.track?.let { loadCurrent(it, startAtMs = at) }
    }

    /** The ExoPlayer behind the page, for the inline video surface. */
    val exoPlayer: ExoPlayer? get() = player

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            fadePlayer?.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE) {
                _state.value.track?.let { loadCurrent(it) }
            } else {
                p.play()
                if (fading && fadePrepared) fadePlayer?.play()
            }
        }
    }

    fun pause() {
        player?.pause()
        fadePlayer?.pause()
    }

    fun next() {
        cancelCrossfade()
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
        cancelCrossfade()
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
        cancelCrossfade()
        val s = _state.value
        val track = s.queue.getOrNull(index) ?: return
        _state.value = s.copy(index = index, track = track, positionMs = 0L, durationMs = track.durationSeconds * 1000L)
        loadCurrent(track)
    }

    /** Adds a track to the party queue. Used by the guest web page. */
    @Synchronized
    fun enqueue(track: MusicTrack, playNext: Boolean = false) {
        val s = _state.value
        if (s.track == null || s.queue.isEmpty()) {
            play(track, listOf(track))
            return
        }
        if (s.queue.any { it.id == track.id }) return
        val queue = s.queue.toMutableList()
        val at = if (playNext) (s.index + 1).coerceIn(0, queue.size) else queue.size
        queue.add(at, track)
        _state.value = s.copy(queue = queue)
    }

    fun seekTo(positionMs: Long) {
        cancelCrossfade()
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

    private fun crossfadeNextIndex(s: State): Int? = when {
        s.repeat == RepeatMode.ONE -> null
        s.queue.size <= 1 -> null
        s.shuffle -> {
            var candidate = s.index
            while (candidate == s.index) candidate = (0 until s.queue.size).random()
            candidate
        }
        s.index < s.queue.lastIndex -> s.index + 1
        s.repeat == RepeatMode.ALL -> 0
        else -> null
    }

    /** Starts loading the next track into the secondary player when the current
     *  one is exactly one crossfade away from its end. */
    private fun maybeStartCrossfade(p: ExoPlayer, positionMs: Long, durationMs: Long) {
        val s = _state.value
        val fadeMs = crossfadeMs()
        if (fadeMs <= 0L) return
        val prepareMs = crossfadePrepareMs()
        if (fading || !p.isPlaying || s.track == null) return
        if (durationMs <= prepareMs + 5_000L) return
        if (positionMs < durationMs - prepareMs) return
        val index = crossfadeNextIndex(s) ?: return
        val track = s.queue.getOrNull(index) ?: return
        if (track.id == s.track.id) return
        val token = ++fadeToken
        fading = true
        fadePrepared = false
        fadeNextIndex = index
        fadeNextTrack = track
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val prepared = runCatching { buildMedia(track) }.getOrNull()
            if (token != fadeToken || !fading) return@launch
            if (prepared == null) {
                abortCrossfade()
                return@launch
            }
            val fp = createFadePlayer() ?: run { abortCrossfade(); return@launch }
            fp.volume = 0f
            fp.setMediaSource(prepared.source)
            fp.applyAudioPreferences(
                prepared.hasVideo,
                prepared.label.startsWith("YouTube"),
            )
            fp.playWhenReady = player?.isPlaying == true
            fp.prepare()
            if (token != fadeToken || !fading) return@launch
            fadeHasVideo = prepared.hasVideo
            fadeQuality = prepared.label
            fadePrepared = true
        }
    }

    private fun updateCrossfadeVolumes(p: ExoPlayer, positionMs: Long, durationMs: Long) {
        val fp = fadePlayer ?: return
        if (!fadePrepared || durationMs <= 0L) return
        val fadeMs = crossfadeMs().coerceAtLeast(1L)
        val start = durationMs - fadeMs
        val progress = ((positionMs - start).toFloat() / fadeMs).coerceIn(0f, 1f)
        p.volume = 1f - progress
        fp.volume = progress
        if (progress >= 0.995f) promoteFade()
    }

    /** The next track's player becomes the main one; the old main is kept as
     *  the reusable crossfade player. */
    private fun promoteFade() {
        val old = player
        val next = fadePlayer
        val track = fadeNextTrack
        val index = fadeNextIndex
        if (!fadePrepared || old == null || next == null || track == null || index < 0) {
            abortCrossfade()
            onEnded()
            return
        }
        fading = false
        fadeToken++
        fadeJob?.cancel()
        fadePrepared = false
        val position = runCatching { next.currentPosition }.getOrDefault(0L)
        val duration = runCatching { next.duration }.getOrDefault(0L)
        runCatching {
            old.pause()
            old.volume = 1f
            old.clearMediaItems()
            old.removeListener(mainListener)
            old.addListener(fadeListener)
            next.removeListener(fadeListener)
            next.addListener(mainListener)
        }
        next.volume = 1f
        player = next
        fadePlayer = old
        _state.value = _state.value.copy(
            track = track,
            index = index,
            positionMs = position.coerceAtLeast(0L),
            durationMs = if (duration > 0L) duration else track.durationSeconds * 1000L,
            playing = true,
            loading = false,
            videoPlaying = fadeHasVideo,
            quality = fadeQuality,
            lyrics = MusicLyrics.EMPTY,
            lyricsLoading = true,
            error = null,
        )
        MusicLibrary.addRecent(track)
        scope.launch {
            val lyrics = MusicLyricsService.forTrack(track)
            if (_state.value.track?.id == track.id) {
                _state.value = _state.value.copy(lyrics = lyrics, lyricsLoading = false)
            }
        }
        fadeNextIndex = -1
        fadeNextTrack = null
    }

    private fun abortCrossfade() {
        fading = false
        fadePrepared = false
        fadeToken++
        fadeJob?.cancel()
        player?.volume = 1f
        fadePlayer?.let {
            it.volume = 0f
            runCatching { it.pause() }
            runCatching { it.clearMediaItems() }
        }
        fadeNextIndex = -1
        fadeNextTrack = null
    }

    private fun cancelCrossfade() {
        if (fading || fadePrepared || fadePlayer?.mediaItemCount != 0) abortCrossfade()
    }

    private fun onEnded() {
        val s = _state.value
        if (s.repeat == RepeatMode.ONE) {
            player?.seekTo(0L)
            player?.play()
            return
        }
        // Autoplay off still honours an explicit Repeat (All/One); with both
        // off the queue stops at the end of the track.
        if (s.hasNext && (MusicSettings.autoplay.value || s.repeat != RepeatMode.OFF)) {
            next()
        } else {
            player?.pause()
            player?.seekTo(0L)
            _state.value = s.copy(playing = false, positionMs = 0L)
        }
    }

    private fun loadCurrent(track: MusicTrack, startAtMs: Long = 0L) {
        cancelCrossfade()
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
            val prepared = buildMedia(track)
            if (token != playToken) return@launch
            if (prepared == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    playing = false,
                    videoPlaying = false,
                    error = "No playable audio source for this track.",
                )
                return@launch
            }
            if (player == null) createPlayer()
            val exo = player ?: return@launch
            exo.setMediaSource(prepared.source)
            // Audio mode disables the picture; Video mode enables it so the
            // PlayerView on the Music player page has something to draw. The
            // same parameters carry the quality ceiling and the equalizer.
            exo.applyAudioPreferences(
                prepared.hasVideo,
                prepared.label.startsWith("YouTube"),
            )
            if (startAtMs > 0L) exo.seekTo(startAtMs)
            exo.prepare()
            exo.play()
            _state.value = _state.value.copy(
                loading = false,
                videoPlaying = prepared.hasVideo,
                quality = prepared.label,
            )
        }
    }

    /** Resolves a track into a ready-to-set MediaSource. Shared by normal
     *  playback and the crossfade's secondary player. */
    private suspend fun buildMedia(track: MusicTrack): PreparedMedia? {
        // Local/MediaStore and server rows carry their own playable URI and
        // must never fall through to a title search on Qobuz/YouTube.
        val directLocal = track.localUri?.takeIf { it.isNotBlank() }
        val directStream = track.streamUrl?.takeIf { it.isNotBlank() }
        val isDirect = directLocal != null || directStream != null
        val videoMode = _state.value.videoMode && !isDirect
        val downloaded = if (videoMode || isDirect) null else {
            MusicLibrary.downloadedFor(track.id)?.let { MusicLibrary.downloadFile(it) }
        }
        val localFile = downloaded?.takeIf { it.isFile && it.length() > 1024L }
        val videoStream = if (videoMode) withContext(Dispatchers.IO) {
            runCatching { MusicSourceResolver.videoStream(track) }.getOrNull()
        } else null
        val stream = when {
            isDirect -> null
            videoStream != null -> videoStream
            localFile != null -> null
            else -> withContext(Dispatchers.IO) {
                MusicSourceResolver.resolve(track, MusicLibrary.preferLossless.value)
            }
        }
        if (!isDirect && localFile == null && stream == null) return null
        val uri = when {
            directLocal != null -> Uri.parse(directLocal)
            directStream != null -> Uri.parse(directStream)
            localFile != null -> Uri.fromFile(localFile)
            else -> Uri.parse(stream!!.url)
        }
        val headers = when {
            directStream != null -> track.streamHeaders
            else -> stream?.headers.orEmpty()
        }
        val userAgent = headers["User-Agent"] ?: Http.UA
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        val context = appContext ?: runCatching { HikariApp.instance }.getOrNull()
        val dataSource = if (context != null) {
            androidx.media3.datasource.DefaultDataSource.Factory(context, http)
        } else http
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
        val urlText = uri.toString()
        val mediaSource = when {
            localFile != null || directLocal != null ->
                ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem)
            stream?.isDash == true || urlText.contains(".mpd", true) ->
                DashMediaSource.Factory(dataSource).createMediaSource(mediaItem)
            stream?.isHls == true || urlText.contains(".m3u8", true) ->
                HlsMediaSource.Factory(dataSource).createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem)
        }
        MusicBitPerfectUsb.setSourceLossless(
            stream?.lossless == true ||
                urlText.contains(".flac", true) ||
                urlText.contains(".wav", true) ||
                urlText.contains(".alac", true) ||
                (localFile != null &&
                    MusicLibrary.downloadedFor(track.id)?.format?.equals("flac", true) == true),
        )
        return PreparedMedia(
            source = mediaSource,
            hasVideo = videoStream != null,
            label = when {
                directLocal != null -> "Local file"
                directStream != null -> track.album.ifBlank { "Server" }
                localFile != null -> MusicLibrary.downloadedFor(track.id)?.quality ?: "Offline"
                videoStream != null -> "YouTube video"
                stream != null -> stream.label
                else -> ""
            },
        )
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
