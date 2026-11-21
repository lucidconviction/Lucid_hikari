package com.hikari.app.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Local screenshots for the Web Apps grid.
 *
 * mShots (the WordPress thumbnail service) frequently has no render for a
 * freshly added site, so a new Web App used to sit on a placeholder favicon for
 * hours. The in-app WebView sees the real page, so it captures it once the page
 * has fully loaded and keeps the image keyed by host here. The Websites tab
 * prefers this file and falls back to mShots only when a site has never been
 * opened.
 *
 * Keyed by HOST, not the full URL: the saved URL, a redirect target and a
 * deep link inside the same site all share one thumbnail.
 */
object WebAppShots {

    private val versions = mutableStateMapOf<String, Long>()
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "webapp-shots").apply { isDaemon = true }
    }

    /** Lower-cased host without `www.`, the same key the grid looks up. */
    fun hostKey(raw: String): String {
        val host = runCatching { java.net.URI(raw.trim()).host }.getOrNull()
            ?.trim()?.trimEnd('.')?.lowercase()?.removePrefix("www.")
        if (!host.isNullOrBlank()) return host
        return raw.trim().lowercase()
    }

    fun file(context: Context, url: String): File {
        val dir = File(context.filesDir, "webapp_shots").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(hostKey(url).toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) } + ".jpg"
        return File(dir, name)
    }

    /** The captured screenshot for [url]'s host, or null when none was taken. */
    fun cachedFile(context: Context, url: String): File? =
        file(context, url).takeIf { it.isFile && it.length() > 0L }

    /** Bumped whenever a host's screenshot is replaced; read from composition. */
    fun versionOf(url: String): Long = versions[hostKey(url)] ?: 0L

    /**
     * Draws the loaded page into a JPEG for [url]'s host.
     *
     * Must run on the main thread (WebView.draw is not thread-safe) and after
     * the page settled — callers delay past `onPageFinished` so lazy images
     * and webfonts are in place. The JPEG write happens on a worker thread and
     * the version map is bumped back on the main thread, which is what makes
     * the grid swap its mShots placeholder for the real capture.
     */
    fun capture(
        context: Context,
        webView: WebView,
        url: String,
        delayMs: Long = 0L,
        onSaved: ((File?) -> Unit)? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { capture(context, webView, url, delayMs, onSaved) }
            return
        }
        val run = Runnable {
            // A capture queued while the page was up must not draw a WebView
            // that has since been detached (Back, tab switch): drawing a
            // detached view can block the UI thread for seconds on a slow
            // device and produce a blank file.
            if (!webView.isAttachedToWindow || webView.width <= 0 || webView.height <= 0) {
                onSaved?.invoke(null)
                return@Runnable
            }
            // The card is at most a few hundred dp wide; rasterising the full
            // 1080p page costs time and memory for pixels nobody sees. Scale
            // into a 720px-wide bitmap instead — a real page capture, just at
            // thumbnail resolution.
            val scale = minOf(1f, 720f / webView.width.toFloat())
            val outW = (webView.width * scale).toInt().coerceAtLeast(1)
            val outH = (webView.height * scale).toInt().coerceAtLeast(1)
            val bitmap = runCatching {
                Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    canvas.scale(scale, scale)
                    webView.draw(canvas)
                }
            }.getOrNull()
            if (bitmap == null) {
                onSaved?.invoke(null)
                return@Runnable
            }
            val key = hostKey(url)
            val target = file(context, url)
            writer.execute {
                val ok = runCatching {
                    FileOutputStream(target).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                }.isSuccess
                bitmap.recycle()
                val saved = target.takeIf { ok && it.length() > 0L }
                main.post {
                    if (saved != null) versions[key] = System.currentTimeMillis()
                    onSaved?.invoke(saved)
                }
            }
        }
        if (delayMs > 0L) main.postDelayed(run, delayMs) else main.post(run)
    }
}
