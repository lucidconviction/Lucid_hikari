package com.hikari.app.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hikari.app.MainActivity
import com.hikari.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Puts the Music tab in the notification shade, on the lock screen and on
 * headset/Bluetooth buttons while music plays in the background.
 *
 * Unlike [com.hikari.app.player.BackgroundPlaybackService], this service does
 * not own a player: [MusicPlayer] is the process-wide singleton. The service
 * keeps the process at foreground priority, publishes a MediaSessionCompat so
 * Android treats it as a real media app, and draws a MediaStyle notification
 * with previous / play-pause / next / close.
 *
 * Started from [MusicPlayer] the first time a track actually begins playing.
 * It stays while the user is away, and goes when playback has been paused for
 * a while or the user closes it from the notification.
 */
class MusicPlaybackService : Service() {

    companion object {
        private const val CHANNEL = "hikari_music_playback"
        private const val NOTIF_ID = 0x4D55 // "MU"
        private const val ACTION_TOGGLE = "com.hikari.app.music.TOGGLE"
        private const val ACTION_PREVIOUS = "com.hikari.app.music.PREVIOUS"
        private const val ACTION_NEXT = "com.hikari.app.music.NEXT"
        private const val ACTION_CLOSE = "com.hikari.app.music.CLOSE"

        /** How long a paused track keeps its notification before the service
         *  stands down. Resuming from the Music page simply starts it again. */
        private const val PAUSED_IDLE_MS = 5 * 60_000L

        private var running = false

        fun start(context: Context) {
            if (running) return
            running = true
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MusicPlaybackService::class.java)
                        .setAction("com.hikari.app.music.START"),
                )
            }.onFailure { running = false }
        }

        fun stop(context: Context) {
            running = false
            runCatching {
                context.stopService(Intent(context, MusicPlaybackService::class.java))
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ticker: Runnable? = null
    private var pausedSince = 0L
    private lateinit var session: MediaSessionCompat
    private var artwork: Bitmap? = null
    private var artworkUrl: String? = null
    private var lastMetadataKey: String? = null
    private var lastWidgetKey: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "LucidMusic").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (!MusicPlayer.state.value.playing) MusicPlayer.toggle()
                    handler.postDelayed({ updateNotification() }, 120L)
                }

                override fun onPause() {
                    if (MusicPlayer.state.value.playing) MusicPlayer.toggle()
                    handler.postDelayed({ updateNotification() }, 120L)
                }

                override fun onSkipToNext() {
                    MusicPlayer.next()
                    handler.postDelayed({ updateNotification() }, 120L)
                }

                override fun onSkipToPrevious() {
                    MusicPlayer.previous()
                    handler.postDelayed({ updateNotification() }, 120L)
                }

                override fun onSeekTo(pos: Long) {
                    MusicPlayer.seekTo(pos)
                    handler.postDelayed({ updateNotification() }, 120L)
                }

                override fun onStop() {
                    MusicPlayer.pause()
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                MusicPlayer.toggle()
                handler.postDelayed({ updateNotification() }, 120L)
            }
            ACTION_PREVIOUS -> {
                MusicPlayer.previous()
                handler.postDelayed({ updateNotification() }, 120L)
            }
            ACTION_NEXT -> {
                MusicPlayer.next()
                handler.postDelayed({ updateNotification() }, 120L)
            }
            ACTION_CLOSE -> {
                MusicPlayer.pause()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startInForeground()
        startTicker()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        running = false
        runCatching { session.isActive = false }
        runCatching { session.release() }
        super.onDestroy()
    }

    private fun startInForeground() {
        val notif = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
        }
    }

    /** Poll once a second: keeps the metadata, play/pause state and progress
     *  honest without routing every ExoPlayer callback through a service. */
    private fun startTicker() {
        if (ticker != null) return
        val run = object : Runnable {
            override fun run() {
                val state = MusicPlayer.state.value
                if (state.track == null) {
                    stopSelf()
                    return
                }
                if (state.playing) {
                    pausedSince = 0L
                } else {
                    if (pausedSince == 0L) pausedSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - pausedSince > PAUSED_IDLE_MS) {
                        stopSelf()
                        return
                    }
                }
                updateSession()
                updateNotification()
                val widgetKey = "${state.track?.id}|${state.playing}|${state.track?.coverUrl}"
                if (widgetKey != lastWidgetKey) {
                    lastWidgetKey = widgetKey
                    MusicWidgetProvider.refresh(this@MusicPlaybackService)
                }
                handler.postDelayed(this, 1_000L)
            }
        }
        ticker = run
        handler.post(run)
    }

    private fun updateSession() {
        val state = MusicPlayer.state.value
        val track = state.track ?: return
        refreshArtwork(track.coverUrl)
        val key = "${track.id}|${track.title}|${track.artist}|${state.durationMs}"
        if (key != lastMetadataKey) {
            lastMetadataKey = key
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.id)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
                .apply { artwork?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
            session.setMetadata(metadata)
        }
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (state.playing) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    state.positionMs.coerceAtLeast(0L),
                    if (state.playing) 1f else 0f,
                )
                .build(),
        )
    }

    private fun refreshArtwork(url: String?) {
        val wanted = url?.takeIf { it.isNotBlank() }
        if (wanted == artworkUrl) return
        artworkUrl = wanted
        artwork = null
        if (wanted == null) return
        scope.launch {
            val bitmap = runCatching {
                val result = coil.Coil.imageLoader(this@MusicPlaybackService)
                    .execute(
                        coil.request.ImageRequest.Builder(this@MusicPlaybackService)
                            .data(wanted)
                            .size(512, 512)
                            .build(),
                    )
                (result.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
            if (bitmap != null && artworkUrl == wanted) {
                artwork = bitmap
                lastMetadataKey = null
                handler.post { updateSession(); updateNotification() }
            }
        }
    }

    private fun updateNotification() {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val state = MusicPlayer.state.value
        val track = state.track
        val playing = state.playing
        val title = track?.title?.ifBlank { null } ?: "Hikari Music"
        val subtitle = track?.artist?.ifBlank { null }
            ?: if (playing) "Playing" else "Paused"
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
        fun action(action: String, request: Int) = PendingIntent.getService(
            this,
            request,
            Intent(this, MusicPlaybackService::class.java).setAction(action),
            flags,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_audio)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                action(ACTION_PREVIOUS, 1),
            )
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                action(ACTION_TOGGLE, 2),
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                action(ACTION_NEXT, 3),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Close",
                action(ACTION_CLOSE, 4),
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        artwork?.let { builder.setLargeIcon(it) }
        if (state.durationMs > 0L) {
            val pct = ((state.positionMs.toFloat() / state.durationMs) * 100f)
                .toInt().coerceIn(0, 100)
            builder.setProgress(100, pct, false)
        } else {
            builder.setProgress(0, 0, false)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Music playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Music controls while Lucid Hikari is in the background"
                setShowBadge(false)
                setSound(null, null)
            }
        )
    }
}
