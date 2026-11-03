package com.hikari.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The user's own picture as the app background (Settings → App Layout →
 * "Custom background").
 *
 * The picture is copied into the app's private storage (so it keeps working
 * after the gallery picture is moved or deleted) and drawn behind every screen
 * by [CustomBackgroundLayer], which AppRoot places right after the theme's own
 * backdrop. A "darkness" overlay in the theme's background colour keeps text
 * readable over any picture. Without a picture nothing changes: the normal
 * theme backdrop is what shows.
 */
object CustomBackground {
    private const val PREFS = "hikari_custom_bg"
    private const val KEY_DIM = "dim"
    private const val DEFAULT_DIM = 0.45f
    private const val MAX_DIM = 0.9f

    /** The longest side the saved picture is scaled down to (keeps memory sane). */
    private const val MAX_SIDE = 2048

    /** The picture being shown, or null for the normal theme background. */
    var image: ImageBitmap? by mutableStateOf(null)
        private set

    /** How strongly the theme colour is laid over the picture, 0 (none) .. 0.9. */
    var dim: Float by mutableFloatStateOf(DEFAULT_DIM)
        private set

    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "custom_bg/background.jpg")

    /** Reads the saved picture and darkness once per process. */
    suspend fun load(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        val result = withContext(Dispatchers.IO) {
            val f = file(app)
            val bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val savedDim = prefs.getFloat(KEY_DIM, DEFAULT_DIM)
            Pair(bmp?.asImageBitmap(), savedDim)
        }
        image = result.first
        dim = result.second.coerceIn(0f, MAX_DIM)
    }

    /** Saves the picture at [uri] as the background. False when it can't be read. */
    suspend fun set(context: Context, uri: Uri): Boolean {
        val app = context.applicationContext
        val bmp = withContext(Dispatchers.IO) {
            val decoded = decodeScaled(app, uri) ?: return@withContext null
            val f = file(app)
            f.parentFile?.mkdirs()
            val ok = runCatching {
                f.outputStream().use { decoded.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }.getOrDefault(false)
            if (ok) decoded else null
        } ?: return false
        loaded = true
        image = bmp.asImageBitmap()
        return true
    }

    /** Goes back to the normal theme background. */
    suspend fun clear(context: Context) {
        image = null
        runCatching { file(context.applicationContext).delete() }
    }

    /** Sets the darkness overlay (0 = full picture, 0.9 = nearly solid). */
    fun setDim(context: Context, value: Float) {
        dim = value.coerceIn(0f, MAX_DIM)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_DIM, dim).apply()
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val app = context.applicationContext
        // First pass: bounds only, to work out the downsample factor.
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null
        val maxSide = boundsOpts.outWidth.coerceAtLeast(boundsOpts.outHeight)
        val sample = if (maxSide <= MAX_SIDE) 1
        else maxOf(1, (maxSide.toFloat() / MAX_SIDE).roundToInt())
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return app.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }
}

/**
 * A composable that, when placed inside the root Box of AppRoot, draws the
 * custom background image (or does nothing when no image is set).
 */
@Composable
fun CustomBackgroundLayer(modifier: Modifier = Modifier) {
    val img = CustomBackground.image
    if (img == null) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                drawImage(img, topLeft = Offset.Zero)
            }
    )
}