package com.hikari.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hikari.app.MainActivity
import com.hikari.app.R

/**
 * Keeps the player's audio alive when the app is put away or the screen locks.
 *
 * The player itself stays in [PlayerActivity] — this is a foreground
 * `mediaPlayback` service, not a second player. Its only jobs are to keep the
 * process at foreground priority, show a media notification with play/pause
 * and stop, and let the notification reach the activity through the small
 * static bridge at the bottom of [PlayerActivity]. When the activity comes back
 * the service is stopped and the notification disappears.
 *
 * A service is what makes "background play" survive the screen locking: the
 * activity is stopped, but an audio-only foreground service is exactly the
 * state Android expects a media app to be in.
 */
class BackgroundPlaybackService : Service() {

    companion object {
        private const val CHANNEL = "hikari_background_playback"
        private const val NOTIF_ID = 0x504C // "PL"
        private const val ACTION_TOGGLE = "com.hikari.app.playback.TOGGLE"
        private const val ACTION_STOP = "com.hikari.app.playback.STOP"

        /** Start (or refresh) the background notification. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BackgroundPlaybackService::class.java)
                        .setAction("com.hikari.app.playback.START"),
                )
            }
        }

        /** Tear the notification down when the player is visible again. */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, BackgroundPlaybackService::class.java))
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                PlayerActivity.serviceToggle()
                handler.postDelayed({ updateNotification() }, 120L)
            }
            ACTION_STOP -> {
                PlayerActivity.servicePause()
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

    /** Poll the activity once a second. The player may pause, resume, or end
     *  while the app is away; a poll keeps the notification honest without
     *  wiring every media3 callback through a service. */
    private fun startTicker() {
        if (ticker != null) return
        val run = object : Runnable {
            override fun run() {
                if (PlayerActivity.serviceHandle() == null || PlayerActivity.serviceEnded()) {
                    stopSelf()
                    return
                }
                updateNotification()
                handler.postDelayed(this, 1_000L)
            }
        }
        ticker = run
        handler.post(run)
    }

    private fun updateNotification() {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val playing = PlayerActivity.serviceIsPlaying()
        val title = PlayerActivity.serviceTitle().ifBlank { "Hikari" }
        val subtitle = PlayerActivity.serviceSubtitle().ifBlank {
            if (playing) "Playing in the background" else "Paused"
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, BackgroundPlaybackService::class.java).setAction(ACTION_TOGGLE),
            flags,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, BackgroundPlaybackService::class.java).setAction(ACTION_STOP),
            flags,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_audio)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                toggle,
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Background playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Audio that keeps playing when the app is in the background"
                setShowBadge(false)
                setSound(null, null)
            }
        )
    }
}
