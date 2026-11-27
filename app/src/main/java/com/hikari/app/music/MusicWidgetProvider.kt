package com.hikari.app.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import com.hikari.app.MainActivity
import com.hikari.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.request.ImageRequest

/** The Music home-screen widget: artwork, title/artist and transport. */
class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.hikari.app.music.WIDGET_TOGGLE"
        private const val ACTION_PREVIOUS = "com.hikari.app.music.WIDGET_PREVIOUS"
        private const val ACTION_NEXT = "com.hikari.app.music.WIDGET_NEXT"
        private const val PREFS = "lucid_music_widget"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_COVER = "cover"
        private const val KEY_PLAYING = "playing"
        private const val KEY_TRACK = "track"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MusicWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val state = MusicPlayer.state.value
            val track = state.track
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = track?.title ?: prefs.getString(KEY_TITLE, null) ?: "Lucid Music"
            val artist = track?.artist ?: prefs.getString(KEY_ARTIST, null) ?: "Nothing playing"
            val cover = track?.coverUrl ?: prefs.getString(KEY_COVER, null).orEmpty()
            val playing = track?.let { state.playing } ?: prefs.getBoolean(KEY_PLAYING, false)
            prefs.edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putString(KEY_COVER, cover)
                .putString(KEY_TRACK, track?.id ?: prefs.getString(KEY_TRACK, null))
                .putBoolean(KEY_PLAYING, playing)
                .apply()

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.music_widget)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist)
                views.setImageViewResource(
                    R.id.widget_toggle,
                    if (playing) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play,
                )
                views.setOnClickPendingIntent(R.id.widget_previous, broadcast(context, ACTION_PREVIOUS, 1))
                views.setOnClickPendingIntent(R.id.widget_toggle, broadcast(context, ACTION_TOGGLE, 2))
                views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 3))
                val open = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
                if (cover.isNotBlank()) {
                    scope.launch {
                        val bitmap = loadCover(context, cover)
                        if (bitmap != null) {
                            val withArt = RemoteViews(context.packageName, R.layout.music_widget)
                            withArt.setTextViewText(R.id.widget_title, title)
                            withArt.setTextViewText(R.id.widget_artist, artist)
                            withArt.setImageViewResource(
                                R.id.widget_toggle,
                                if (playing) android.R.drawable.ic_media_pause
                                else android.R.drawable.ic_media_play,
                            )
                            withArt.setImageViewBitmap(R.id.widget_art, bitmap)
                            withArt.setOnClickPendingIntent(R.id.widget_previous, broadcast(context, ACTION_PREVIOUS, 1))
                            withArt.setOnClickPendingIntent(R.id.widget_toggle, broadcast(context, ACTION_TOGGLE, 2))
                            withArt.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 3))
                            withArt.setOnClickPendingIntent(R.id.widget_root, open)
                            manager.updateAppWidget(id, withArt)
                        }
                    }
                }
            }
        }

        private fun broadcast(context: Context, action: String, request: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                request,
                Intent(context, MusicWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private suspend fun loadCover(context: Context, url: String): Bitmap? = runCatching {
            val result = coil.Coil.imageLoader(context).execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(256, 256)
                    .build(),
            )
            (result.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                MusicPlayer.init(context)
                MusicPlayer.toggle()
            }
            ACTION_PREVIOUS -> {
                MusicPlayer.init(context)
                MusicPlayer.previous()
            }
            ACTION_NEXT -> {
                MusicPlayer.init(context)
                MusicPlayer.next()
            }
            else -> super.onReceive(context, intent)
        }
        refresh(context)
    }
}
