package com.hikari.app.player

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.hikari.app.MainActivity
import com.hikari.app.data.StreamSource
import com.hikari.app.i18n.tr
import com.hikari.app.net.Http
import com.hikari.app.net.IptvPlaybackForklift
import com.hikari.app.net.PlayerHttp
import com.hikari.app.net.StreamProbe
import com.hikari.app.tv.tvPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * "PIP Mode/Ch Preview": off by default. When on, picking a channel, movie,
 * series or web stream shows it in a floating preview instead of opening the
 * full player. Remembered on this device.
 */
object PipModeSettings {
    private const val PREFS = "pip_mode_ch_preview"
    private const val KEY = "pip_mode_ch_preview_enabled"

    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs?.getBoolean(KEY, false) ?: false
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        init(context)
        _enabled.value = enabled
        prefs?.edit()?.putBoolean(KEY, enabled)?.apply()
    }

    fun toggle(context: Context) = setEnabled(context, !_enabled.value)

    val isOn: Boolean get() = _enabled.value
}

/** One playable server from a PlayerActivity `sources` JSON payload. */
internal data class PipSource(
    val name: String,
    val url: String,
    val headers: Map<String, String>,
    val isM3u8: Boolean,
    val isMpd: Boolean,
    val isTorrent: Boolean,
    val infoHash: String?,
    val fileIdx: Int?,
    val trackers: List<String>,
    val isLive: Boolean,
    val local: Boolean,
    val hasDrm: Boolean,
    val thumbnail: String?,
    val providerId: String = "",
)

/** Remote-driven bounds adjustment (a television has no drag gesture). */
private enum class PipAdjustMode { NONE, MOVE, RESIZE }

/** One floating preview window and where it sits on screen. */
internal data class PipPane(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val logo: String?,
    val artwork: String?,
    val sources: List<PipSource>,
    val sourceIndex: Int = 0,
    val extras: Bundle,
    val intentFlags: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val locked: Boolean = false,
    val muted: Boolean = false,
    val manual: Boolean = false,
    val resumePositionMs: Long = 0L,
    val resumeToken: Long = 0L,
)

/**
 * Every floating preview, held above navigation so they float over every
 * screen. A normal tap reuses the newest pane; a window can also be opened
 * alongside the others (up to 4 upright / 9 on a tablet).
 */
internal object PipWindowStore {
    private val _panes = MutableStateFlow<List<PipPane>>(emptyList())
    val panes: StateFlow<List<PipPane>> = _panes.asStateFlow()

    /** Windows folded away into the floating bubble, with their last timestamp. */
    private val _minimized = MutableStateFlow<List<PipPane>>(emptyList())
    val minimized: StateFlow<List<PipPane>> = _minimized.asStateFlow()

    /** The television's remote-control sheet (MENU/PIP pill). */
    private val _remoteMenu = MutableStateFlow(false)
    val remoteMenu: StateFlow<Boolean> = _remoteMenu.asStateFlow()

    fun openRemoteMenu() {
        if (_panes.value.isNotEmpty()) _remoteMenu.value = true
    }

    fun closeRemoteMenu() {
        _remoteMenu.value = false
    }

    private var nextId = 1L

    /** Normal play with PIP on: reuse the newest window instead of stacking one per tap. */
    fun show(pane: PipPane) {
        val list = _panes.value
        if (list.isEmpty()) {
            add(pane)
            return
        }
        val newest = list.last()
        _panes.value = list.dropLast(1).map { it.copy(muted = true) } +
            pane.copy(
                id = newest.id,
                x = newest.x,
                y = newest.y,
                width = newest.width,
                height = newest.height,
                manual = newest.manual,
                muted = false,
                locked = newest.locked,
                resumePositionMs = 0L,
                resumeToken = newest.resumeToken + 1,
            )
    }

    fun add(pane: PipPane) {
        val same = _panes.value.firstOrNull { it.title == pane.title && it.sources.firstOrNull()?.url == pane.sources.firstOrNull()?.url }
        if (same != null) {
            _panes.value = _panes.value.map { it.copy(muted = it.id != same.id) }
            bringToFront(same.id)
            return
        }
        val created = pane.copy(id = nextId++)
        _panes.value = _panes.value.map { it.copy(muted = true) } + created
    }

    /** Folds a window into the circular bubble, remembering where playback stopped. */
    fun minimize(id: Long, positionMs: Long) {
        val pane = _panes.value.firstOrNull { it.id == id } ?: return
        _panes.value = _panes.value.filterNot { it.id == id }
        _minimized.value = _minimized.value.filterNot { it.id == id } +
            pane.copy(
                resumePositionMs = positionMs.coerceAtLeast(0L),
                resumeToken = pane.resumeToken + 1,
                muted = true,
                locked = false,
            )
    }

    /** Reopens a minimized window at its old position/size and playback timestamp. */
    fun restore(id: Long) {
        val pane = _minimized.value.firstOrNull { it.id == id } ?: return
        _minimized.value = _minimized.value.filterNot { it.id == id }
        val existing = _panes.value.filterNot { it.id == id }
        _panes.value = existing.map { it.copy(muted = true) } +
            pane.copy(
                muted = false,
                locked = false,
                manual = true,
                resumeToken = pane.resumeToken + 1,
            )
    }

    fun remove(id: Long) {
        _panes.value = _panes.value.filterNot { it.id == id }
        _minimized.value = _minimized.value.filterNot { it.id == id }
    }

    fun removeLast() {
        _panes.value.lastOrNull()?.let { remove(it.id) }
    }

    /** The touched window moves to the top, so overlapping PiPs stack naturally. */
    fun bringToFront(id: Long) {
        val list = _panes.value
        if (list.lastOrNull()?.id == id) return
        val pane = list.firstOrNull { it.id == id } ?: return
        _panes.value = list.filterNot { it.id == id } + pane
    }

    fun setBounds(id: Long, x: Float, y: Float, width: Float, height: Float, manual: Boolean) {
        _panes.value = _panes.value.map { pane ->
            if (pane.id == id) {
                pane.copy(x = x, y = y, width = width, height = height, manual = manual || pane.manual)
            } else {
                pane
            }
        }
    }

    fun setLocked(id: Long, locked: Boolean) {
        _panes.value = _panes.value.map { if (it.id == id) it.copy(locked = locked) else it }
    }

    fun setMuted(id: Long, muted: Boolean) {
        _panes.value = _panes.value.map { if (it.id == id) it.copy(muted = muted) else it }
    }

    fun nextSource(id: Long) {
        _panes.value = _panes.value.map { pane ->
            if (pane.id != id) return@map pane
            val current = pane.sourceIndex
            if (current + 1 >= pane.sources.size) return@map pane
            // Skip servers the app already knows are dead. If nothing is known
            // (or only dead links remain), the next one in order is still
            // better than staying on a broken window.
            val nextHealthy = (current + 1 until pane.sources.size).firstOrNull { i ->
                val s = pane.sources[i]
                !s.isTorrent && !s.hasDrm && s.url.isNotBlank() && !StreamProbe.knownBad(s.url)
            }
            val next = nextHealthy ?: (current + 1)
            pane.copy(sourceIndex = next, resumePositionMs = 0L, resumeToken = pane.resumeToken + 1)
        }
    }

    /**
     * The detail page keeps searching after the first batch lands (its live
     * [com.hikari.app.player.StreamsLive] session). Fold those new servers into
     * the window: while the preview is still tuning/on a torrent it switches to
     * the first direct mirror; once it is actually playing it only grows the
     * failover list, so a later batch can never restart the picture.
     */
    fun mergeSources(id: Long, incoming: List<PipSource>, canSwitch: Boolean) {
        if (incoming.isEmpty()) return
        _panes.value = _panes.value.map { pane ->
            if (pane.id != id) return@map pane
            val merged = (pane.sources + incoming).distinctBy {
                if (it.isTorrent) "t:${it.infoHash ?: it.url}#${it.fileIdx ?: -1}" else "u:${it.url}"
            }
            if (merged.size == pane.sources.size) return@map pane
            val current = pane.sources.getOrNull(pane.sourceIndex)
            val currentKey = current?.let { if (it.isTorrent) "t:${it.infoHash ?: it.url}#${it.fileIdx ?: -1}" else "u:${it.url}" }
            val switch = canSwitch && (current == null || current.isTorrent || current.hasDrm)
            if (!switch) return@map pane.copy(sources = merged)
            val nextIndex = bestDirectPipIndex(merged)
            if (nextIndex < 0) return@map pane.copy(sources = merged)
            val next = merged.getOrNull(nextIndex)
            val nextKey = next?.let { if (it.isTorrent) "t:${it.infoHash ?: it.url}#${it.fileIdx ?: -1}" else "u:${it.url}" }
            if (nextKey == null || nextKey == currentKey) {
                pane.copy(sources = merged)
            } else {
                pane.copy(
                    sources = merged,
                    sourceIndex = nextIndex,
                    resumePositionMs = 0L,
                    resumeToken = pane.resumeToken + 1,
                )
            }
        }
    }

    fun trimTo(maxPanes: Int) {
        val list = _panes.value
        if (list.size > maxPanes) _panes.value = list.takeLast(maxPanes)
    }

    fun hide() {
        _panes.value = emptyList()
        _minimized.value = emptyList()
    }
}

/** Parses a PlayerActivity sources payload into the fields PIP needs. */
private fun parsePipSources(json: String): List<PipSource> {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    val out = ArrayList<PipSource>(array.length().coerceAtMost(24))
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        val url = o.optString("url").trim()
        val infoHash = o.optString("infoHash").takeIf { it.isNotBlank() && it != "null" }
        val isTorrent = o.optBoolean("isTorrent", false) ||
            url.startsWith("magnet:", ignoreCase = true) ||
            url.startsWith("torrent:", ignoreCase = true)
        if (url.isBlank() && infoHash == null) continue
        val headersObj = o.optJSONObject("headers")
        val headers = if (headersObj == null) emptyMap() else buildMap {
            val keys = headersObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = headersObj.optString(k)
                if (v.isNotBlank()) put(k, v)
            }
        }
        val trackersArray = o.optJSONArray("trackers")
        val trackers = if (trackersArray == null) emptyList() else buildList {
            for (t in 0 until trackersArray.length()) {
                trackersArray.optString(t).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
        val fileIdx = o.optInt("fileIdx", -1).takeIf { it >= 0 }
        out += PipSource(
            name = o.optString("name").ifBlank { "Stream" },
            url = url,
            headers = headers,
            isM3u8 = o.optBoolean("isM3u8", false) || url.substringBefore('?').contains(".m3u8", true),
            isMpd = o.optBoolean("isMpd", false) || url.substringBefore('?').contains(".mpd", true),
            isTorrent = isTorrent,
            infoHash = infoHash,
            fileIdx = fileIdx,
            trackers = trackers,
            isLive = o.optBoolean("isLive", false),
            local = o.optBoolean("local", false) || url.startsWith("file:") || url.startsWith("content:"),
            hasDrm = o.has("drm") && !o.isNull("drm"),
            thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
            providerId = o.optString("providerId"),
        )
    }
    return out
}

/** The detail page's live search hands its servers over as `StreamSource`s. */
private fun StreamSource.toPipSource(): PipSource = PipSource(
    name = name,
    url = url,
    headers = headers,
    isM3u8 = isM3u8 || url.substringBefore('?').contains(".m3u8", true),
    isMpd = isMpd || url.substringBefore('?').contains(".mpd", true),
    isTorrent = isTorrent,
    infoHash = infoHash,
    fileIdx = fileIdx,
    trackers = trackers,
    isLive = isLive,
    local = url.startsWith("file:") || url.startsWith("content:"),
    hasDrm = drm != null,
    thumbnail = thumbnail,
    providerId = providerId,
)

/** True when the URL itself is the media file (or a manifest ExoPlayer
 *  understands), so it can be played without walking a wrapper page. */
private fun isDirectMediaUrl(source: PipSource): Boolean {
    if (source.isM3u8 || source.isMpd) return true
    val path = source.url.substringBefore('?').substringBefore('#').lowercase()
    return listOf(
        ".mp4", ".mkv", ".webm", ".ts", ".mov", ".m4v", ".m3u8", ".mpd",
        ".avi", ".mp3", ".aac", ".m4a", ".flac",
    ).any { path.endsWith(it) }
}

/** A mixed server list should not open on a magnet just because the torrent
 *  row came first: prefer a mirror the app already resolved and has not marked
 *  dead, then any direct mirror, then a torrent. */
private fun bestDirectPipIndex(sources: List<PipSource>): Int {
    val verified = sources.indexOfFirst { s ->
        !s.isTorrent && !s.hasDrm && s.url.isNotBlank() &&
            !s.url.startsWith("hikari-td://", ignoreCase = true) &&
            !StreamProbe.knownBad(s.url) && StreamProbe.cached(s.url) != null
    }
    if (verified >= 0) return verified
    return sources.indexOfFirst { s ->
        !s.isTorrent && !s.hasDrm && s.url.isNotBlank() &&
            !s.url.startsWith("hikari-td://", ignoreCase = true) &&
            !StreamProbe.knownBad(s.url)
    }
}

private fun bestPipSourceIndex(sources: List<PipSource>): Int =
    bestDirectPipIndex(sources).takeIf { it >= 0 } ?: 0

/**
 * The one place a player launch is intercepted. Returns true when the intent
 * became a floating PIP; callers then must not start PlayerActivity.
 */
object PipLauncher {
    fun intercept(context: Context, intent: Intent): Boolean =
        route(context, intent, reuseNewest = true)

    /**
     * Adds a launch to a BRAND NEW floating window instead of reusing the
     * newest one. This is the long-press "send to a new PIP" action on a
     * stream: holding a second channel while one is already playing gives the
     * viewer both, tiled side by side.
     */
    fun addNew(context: Context, intent: Intent): Boolean =
        route(context, intent, reuseNewest = false)

    /** Builds a direct channel launch and sends it to a new PIP window. */
    fun addChannel(
        context: Context,
        name: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        logo: String? = null,
        providerId: String? = null,
    ): Boolean {
        if (!PipModeSettings.isOn || url.isBlank()) return false
        val lower = url.substringBefore('?').lowercase()
        val headersJson = JSONObject().apply { headers.forEach { (k, v) -> put(k, v) } }
        val source = JSONObject()
            .put("name", name)
            .put("url", url)
            .put("headers", headersJson)
            .put("isM3u8", lower.endsWith(".m3u8") || lower.endsWith(".m3u"))
            .put("isMpd", lower.contains(".mpd"))
            .put("isLive", true)
            .put("isTorrent", false)
            .put("provider", "IPTV")
            .put("providerId", providerId.orEmpty())
            .put("providerName", "")
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("title", name)
            putExtra("sources", JSONArray().put(source).toString())
            putExtra("askServer", false)
            if (!providerId.isNullOrBlank()) putExtra("histProviderId", providerId)
            putExtra("histTitle", name)
            logo?.takeIf { it.isNotBlank() }?.let {
                putExtra("titleLogo", it)
                putExtra("bannerBackdrop", it)
                putExtra("histPoster", it)
            }
        }
        return addNew(context, intent)
    }

    private fun route(context: Context, intent: Intent, reuseNewest: Boolean): Boolean {
        if (!PipModeSettings.isOn) return false
        val extras = intent.extras ?: return false
        val sourcesJson = extras.getString("sources") ?: return false
        val sources = parsePipSources(sourcesJson)
        // No resolved servers yet (the detail page opens the player empty and
        // lets it search): keep the normal launch so nothing is lost.
        if (sources.isEmpty()) return false
        // TDLib files are served by a data source the PIP player does not own;
        // those keep opening in the full player instead of failing silently.
        if (sources.all { it.url.startsWith("hikari-td://", ignoreCase = true) }) return false
        val intro = sources.first()
        val title = extras.getString("title")?.takeIf { it.isNotBlank() }
            ?: extras.getString("histTitle")?.takeIf { it.isNotBlank() }
            ?: intro.name
        val poster = extras.getString("titleLogo")?.takeIf { it.isNotBlank() }
            ?: extras.getString("histPoster")?.takeIf { it.isNotBlank() }
            ?: extras.getString("bannerBackdrop")?.takeIf { it.isNotBlank() }
            ?: extras.getString("serverArt")?.takeIf { it.isNotBlank() }
            ?: intro.thumbnail
        val subtitle = extras.getString("histEpisodeName")?.takeIf { it.isNotBlank() }
            ?: intro.name.takeIf { it.isNotBlank() && it != title }
        val startIndex = bestPipSourceIndex(sources)
        val pane = PipPane(
            id = 0L,
            title = title,
            subtitle = subtitle,
            logo = poster,
            artwork = poster,
            sources = sources,
            sourceIndex = startIndex,
            extras = Bundle(extras),
            intentFlags = intent.flags,
        )
        if (reuseNewest) PipWindowStore.show(pane) else PipWindowStore.add(pane)
        bringMainActivityForward(context)
        return true
    }

    private fun bringMainActivityForward(context: Context) {
        if (context is MainActivity) return
        val forward = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(forward) }
    }

    /** Sends a PIP back to the full player at the position it reached. */
    internal fun expand(context: Context, pane: PipPane, positionMs: Long) {
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtras(pane.extras)
            putExtra("startPosition", positionMs.coerceAtLeast(0L))
            addFlags(pane.intentFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
        PipWindowStore.remove(pane.id)
    }
}

/** The Home header's PIP Mode/Ch Preview pill, next to the provider chip. */
@Composable
fun PipModeChip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled by PipModeSettings.enabled.collectAsState()
    val panes by PipWindowStore.panes.collectAsState()
    val minimized by PipWindowStore.minimized.collectAsState()
    val count = panes.size + minimized.size
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = {
            if (com.hikari.app.tv.TvMode.isTv && count > 0) {
                PipWindowStore.openRemoteMenu()
            } else {
                PipModeSettings.toggle(context)
            }
        },
        shape = RoundedCornerShape(50),
        color = if (enabled) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        ),
        modifier = modifier.height(34.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Filled.PictureInPictureAlt,
                contentDescription = tr("PIP Mode/Ch Preview"),
                tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                tr("PIP Mode"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                if (enabled) tr("On") else tr("Off"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (enabled && count > 0) {
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        count.toString(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * Hosts every floating preview above navigation. The first opens as the large
 * card; adding another tiles them, up to 4 upright and 9 on a tablet. Each
 * window can be moved, resized and locked on its own, and minimized into the
 * draggable circular folder.
 */
@Composable
fun PipHost() {
    val panes by PipWindowStore.panes.collectAsState()
    val minimized by PipWindowStore.minimized.collectAsState()
    val remoteMenu by PipWindowStore.remoteMenu.collectAsState()
    val context = LocalContext.current
    if (panes.isEmpty() && minimized.isEmpty()) return
    var folderOpen by remember { mutableStateOf(false) }
    LaunchedEffect(minimized.isEmpty()) {
        if (minimized.isEmpty()) folderOpen = false
    }
    // Back closes the minimized-folder sheet only. Closing an active PIP by
    // Back made a sheet/dialog dismiss take the floating window with it; the
    // window has its own × and is never dismissed by an unrelated Back.
    BackHandler(enabled = folderOpen) { folderOpen = false }

    BoxWithConstraints(Modifier.fillMaxSize().zIndex(50f)) {
        val density = LocalDensity.current
        val marginPx = with(density) { 8.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val topPx = with(density) { 58.dp.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val maxHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val minWidthPx = minOf(with(density) { 150.dp.toPx() }, maxWidthPx)
        val minHeightPx = minOf(with(density) { 100.dp.toPx() }, maxHeightPx)
        val tablet = maxWidth >= 600.dp
        val tvUi = com.hikari.app.tv.TvMode.isTv
        // The television's navigation rail is drawn over the left edge; the
        // old x=8dp placement put the whole PIP underneath it.
        val railPx = if (tvUi) with(density) { 104.dp.toPx() } else 0f
        val leftPx = marginPx + railPx
        val contentWidthPx = (maxWidthPx - leftPx - marginPx).coerceAtLeast(minWidthPx)
        // The default floating-window size, not a second television: about a
        // third of the content width, capped at 360dp, in the corner.
        val tvSingleWidth = minOf(
            contentWidthPx * 0.34f,
            with(density) { 360.dp.toPx() },
        ).coerceIn(minWidthPx, contentWidthPx)
        val tvSingleHeight = (
            tvSingleWidth * 9f / 16f + with(density) { 90.dp.toPx() }
            ).coerceIn(minHeightPx, (maxHeightPx - topPx - marginPx).coerceAtLeast(minHeightPx))
        // One decoder on a television: a second floating stream is what made
        // the whole page (and the player) sluggish on a 2 GB box.
        val maxPanes = if (tvUi) 1 else if (tablet) 9 else 4
        val bubbleRadiusPx = with(density) { 27.dp.toPx() }
        var folderX by remember {
            mutableFloatStateOf(maxWidthPx - bubbleRadiusPx - with(density) { 16.dp.toPx() })
        }
        var folderY by remember {
            mutableFloatStateOf(maxHeightPx - bubbleRadiusPx - with(density) { 130.dp.toPx() })
        }
        LaunchedEffect(maxWidthPx, maxHeightPx) {
            folderX = folderX.coerceIn(bubbleRadiusPx, (maxWidthPx - bubbleRadiusPx).coerceAtLeast(bubbleRadiusPx))
            folderY = folderY.coerceIn(bubbleRadiusPx, (maxHeightPx - bubbleRadiusPx).coerceAtLeast(bubbleRadiusPx))
        }

        LaunchedEffect(panes.size, maxWidthPx, maxHeightPx, tablet) {
            PipWindowStore.trimTo(maxPanes)
            val list = PipWindowStore.panes.value
            if (list.isEmpty()) return@LaunchedEffect
            val columns = if (tvUi) 3 else if (tablet) 3 else 2
            val count = list.size
            val grid = count > 1
            val rows = ((count + columns - 1) / columns).coerceAtLeast(1)
            val largeWidth = if (tvUi) {
                tvSingleWidth
            } else {
                (maxWidthPx - marginPx * 2f).coerceIn(minWidthPx, maxWidthPx)
            }
            val largeHeight = if (tvUi) {
                tvSingleHeight
            } else {
                with(density) { 240.dp.toPx() }
                    .coerceIn(minHeightPx, (maxHeightPx - topPx - marginPx).coerceAtLeast(minHeightPx))
            }
            val gridWidth = if (tvUi) contentWidthPx else maxWidthPx - marginPx * 2f
            val cellWidth = (
                (gridWidth - gapPx * (columns - 1)) / columns
                ).coerceIn(minWidthPx, maxWidthPx)
            val cellHeight = if (tvUi) {
                (cellWidth * 9f / 16f + with(density) { 90.dp.toPx() })
                    .coerceIn(minHeightPx, (maxHeightPx - topPx - marginPx).coerceAtLeast(minHeightPx))
            } else {
                ((maxHeightPx * 0.58f - gapPx * (rows - 1)) / rows)
                    .coerceIn(minHeightPx, with(density) { 220.dp.toPx() }.coerceAtLeast(minHeightPx))
            }
            var slot = 0
            list.forEach { pane ->
                if (pane.manual) {
                    val width = pane.width.coerceIn(minWidthPx, maxWidthPx)
                    val height = pane.height.coerceIn(minHeightPx, maxHeightPx)
                    PipWindowStore.setBounds(
                        id = pane.id,
                        x = pane.x.coerceIn(0f, (maxWidthPx - width).coerceAtLeast(0f)),
                        y = pane.y.coerceIn(0f, (maxHeightPx - height).coerceAtLeast(0f)),
                        width = width,
                        height = height,
                        manual = true,
                    )
                    return@forEach
                }
                if (!grid) {
                    PipWindowStore.setBounds(
                        id = pane.id,
                        x = when {
                            tvUi -> (maxWidthPx - largeWidth - marginPx * 2f).coerceAtLeast(leftPx)
                            tablet -> marginPx
                            else -> ((maxWidthPx - largeWidth) / 2f).coerceAtLeast(0f)
                        },
                        y = topPx,
                        width = largeWidth,
                        height = largeHeight,
                        manual = false,
                    )
                } else {
                    val column = slot % columns
                    val row = slot / columns
                    slot++
                    PipWindowStore.setBounds(
                        id = pane.id,
                        x = (if (tvUi) leftPx else marginPx) + column * (cellWidth + gapPx),
                        y = topPx + row * (cellHeight + gapPx),
                        width = cellWidth,
                        height = cellHeight,
                        manual = false,
                    )
                }
            }
        }

        panes.forEach { pane ->
            if (pane.width <= 0f || pane.height <= 0f) return@forEach
            key(pane.id) {
                PipWindow(
                    pane = pane,
                    onToggleLock = { PipWindowStore.setLocked(pane.id, !pane.locked) },
                    onExpand = { positionMs ->
                        if (!pane.locked) PipLauncher.expand(context, pane, positionMs)
                    },
                    onClose = { PipWindowStore.remove(pane.id) },
                    onMinimize = { positionMs -> PipWindowStore.minimize(pane.id, positionMs) },
                    onMoveBy = { dx, dy ->
                        if (!pane.locked) {
                            PipWindowStore.setBounds(
                                id = pane.id,
                                x = (pane.x + dx).coerceIn(0f, (maxWidthPx - pane.width).coerceAtLeast(0f)),
                                y = (pane.y + dy).coerceIn(0f, (maxHeightPx - pane.height).coerceAtLeast(0f)),
                                width = pane.width,
                                height = pane.height,
                                manual = true,
                            )
                        }
                    },
                    onResizeBy = { dx, dy ->
                        if (!pane.locked) {
                            val nextWidth = (pane.width + dx).coerceIn(minWidthPx, maxWidthPx)
                            val nextHeight = (pane.height + dy).coerceIn(minHeightPx, maxHeightPx)
                            PipWindowStore.setBounds(
                                id = pane.id,
                                x = pane.x.coerceIn(0f, (maxWidthPx - nextWidth).coerceAtLeast(0f)),
                                y = pane.y.coerceIn(0f, (maxHeightPx - nextHeight).coerceAtLeast(0f)),
                                width = nextWidth,
                                height = nextHeight,
                                manual = true,
                            )
                        }
                    },
                    onMuteChange = { PipWindowStore.setMuted(pane.id, it) },
                    onSourceFailed = { PipWindowStore.nextSource(pane.id) },
                    modifier = Modifier
                        .offset { IntOffset(pane.x.roundToInt(), pane.y.roundToInt()) }
                        .width(with(density) { pane.width.toDp() })
                        .height(with(density) { pane.height.toDp() }),
                )
            }
        }

        if (folderOpen) {
            Dialog(
                onDismissRequest = { folderOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xF21A1A1A))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            tr("Minimized PiPs"),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        minimized.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { pane ->
                                    MinimizedPaneButton(pane) {
                                        folderOpen = false
                                        PipWindowStore.restore(pane.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (remoteMenu) {
            PipRemoteMenu { PipWindowStore.closeRemoteMenu() }
        }

        if (minimized.isNotEmpty()) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (folderX - bubbleRadiusPx).roundToInt(),
                            (folderY - bubbleRadiusPx).roundToInt(),
                        )
                    }
                    .size(if (tvUi) 62.dp else 54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.84f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    .then(
                        if (tvUi) Modifier.tvPress(previewPass = true, onClick = { folderOpen = !folderOpen })
                        else Modifier.clickable { folderOpen = !folderOpen },
                    )
                    .pointerInput(maxWidthPx, maxHeightPx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            folderX = (folderX + drag.x).coerceIn(
                                bubbleRadiusPx,
                                (maxWidthPx - bubbleRadiusPx).coerceAtLeast(bubbleRadiusPx),
                            )
                            folderY = (folderY + drag.y).coerceIn(
                                bubbleRadiusPx,
                                (maxHeightPx - bubbleRadiusPx).coerceAtLeast(bubbleRadiusPx),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = tr("Minimized PiPs"),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        minimized.size.toString(),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Television remote sheet for the active floating window. Reached from the
 * Home PIP pill (its count opens this instead of toggling the mode) and offers
 * every touch feature without a finger: move by arrows, resize by arrows, lock,
 * minimize, full player and close.
 */
@Composable
private fun PipRemoteMenu(onDismiss: () -> Unit) {
    val panes by PipWindowStore.panes.collectAsState()
    val context = LocalContext.current
    val pane = panes.lastOrNull() ?: return
    var mode by remember { mutableStateOf(PipAdjustMode.NONE) }
    val firstFocus = remember { FocusRequester() }
    val adjustFocus = remember { FocusRequester() }
    LaunchedEffect(mode) {
        // Let the remote's OK that OPENED this sheet go by before a row takes
        // focus, or that same press would immediately run Move.
        delay(if (mode == PipAdjustMode.NONE) 450L else 250L)
        if (mode == PipAdjustMode.NONE) {
            runCatching { firstFocus.requestFocus() }
        } else {
            runCatching { adjustFocus.requestFocus() }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val step = with(density) { 24.dp.toPx() }
            val maxW = with(density) { maxWidth.toPx() }
            val maxH = with(density) { maxHeight.toPx() }
            val minW = with(density) { 150.dp.toPx() }
            val minH = with(density) { 100.dp.toPx() }

            fun moveBy(dx: Float, dy: Float) {
                PipWindowStore.setBounds(
                    id = pane.id,
                    x = (pane.x + dx).coerceIn(0f, (maxW - pane.width).coerceAtLeast(0f)),
                    y = (pane.y + dy).coerceIn(0f, (maxH - pane.height).coerceAtLeast(0f)),
                    width = pane.width,
                    height = pane.height,
                    manual = true,
                )
            }

            fun resizeBy(dx: Float, dy: Float) {
                val w = (pane.width + dx).coerceIn(minW, maxW)
                val h = (pane.height + dy).coerceIn(minH, maxH)
                PipWindowStore.setBounds(
                    id = pane.id,
                    x = pane.x.coerceIn(0f, (maxW - w).coerceAtLeast(0f)),
                    y = pane.y.coerceIn(0f, (maxH - h).coerceAtLeast(0f)),
                    width = w,
                    height = h,
                    manual = true,
                )
            }

            Column(
                Modifier
                    .widthIn(max = 620.dp)
                    // A full list of seven rows is taller than a 1080p screen;
                    // keep the card on screen and let the focused row scroll
                    // itself into view.
                    .heightIn(max = maxHeight * 0.84f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF21A1A1A))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    tr("PIP controls"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    pane.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mode == PipAdjustMode.NONE) {
                    PipMenuRow(
                        label = tr("Move window with arrows"),
                        focus = firstFocus,
                    ) {
                        PipWindowStore.setLocked(pane.id, false)
                        mode = PipAdjustMode.MOVE
                    }
                    PipMenuRow(tr("Resize window with arrows")) {
                        PipWindowStore.setLocked(pane.id, false)
                        mode = PipAdjustMode.RESIZE
                    }
                    PipMenuRow(if (pane.locked) tr("Unlock position") else tr("Lock position")) {
                        PipWindowStore.setLocked(pane.id, !pane.locked)
                    }
                    PipMenuRow(tr("Minimize to folder")) {
                        PipWindowStore.minimize(pane.id, 0L)
                        onDismiss()
                    }
                    PipMenuRow(tr("Open full player")) {
                        PipLauncher.expand(context, pane, 0L)
                        onDismiss()
                    }
                    PipMenuRow(tr("Close window")) {
                        PipWindowStore.remove(pane.id)
                        onDismiss()
                    }
                    PipMenuRow(tr("Turn PIP Mode off")) {
                        PipModeSettings.setEnabled(context, false)
                        PipWindowStore.hide()
                        onDismiss()
                    }
                    TextButton(onClick = onDismiss) {
                        Text(tr("Done"), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .focusRequester(adjustFocus)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (mode == PipAdjustMode.MOVE) moveBy(-step, 0f)
                                        else resizeBy(-step, 0f)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        if (mode == PipAdjustMode.MOVE) moveBy(step, 0f)
                                        else resizeBy(step, 0f)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (mode == PipAdjustMode.MOVE) moveBy(0f, -step)
                                        else resizeBy(0f, -step)
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        if (mode == PipAdjustMode.MOVE) moveBy(0f, step)
                                        else resizeBy(0f, step)
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape -> {
                                        mode = PipAdjustMode.NONE
                                        true
                                    }
                                    else -> false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (mode == PipAdjustMode.MOVE) {
                                tr("Move: arrows · OK to finish")
                            } else {
                                tr("Resize: arrows · OK to finish")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    TextButton(onClick = { mode = PipAdjustMode.NONE }) {
                        Text(tr("Done"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PipMenuRow(
    label: String,
    focus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .tvPress(previewPass = true, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MinimizedPaneButton(pane: PipPane, onClick: () -> Unit) {
    val image = pane.artwork ?: pane.logo
    val tv = com.hikari.app.tv.TvMode.isTv
    Box(
        Modifier
            .size(if (tv) 64.dp else 48.dp)
            .clip(CircleShape)
            .background(Color(0xFF2A2A2A))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .then(
                if (tv) Modifier.tvPress(previewPass = true, onClick = onClick)
                else Modifier.clickable(onClick = onClick),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = pane.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )
        } else {
            Text(
                pane.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PipWindow(
    pane: PipPane,
    onToggleLock: () -> Unit,
    onExpand: (positionMs: Long) -> Unit,
    onClose: () -> Unit,
    onMinimize: (positionMs: Long) -> Unit,
    onMoveBy: (dxPx: Float, dyPx: Float) -> Unit,
    onResizeBy: (dxPx: Float, dyPx: Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onSourceFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val source = pane.sources.getOrNull(pane.sourceIndex) ?: pane.sources.firstOrNull() ?: return
    // The detail page's live session keeps growing after the first batch; the
    // window folds those servers in instead of freezing on a torrent-only
    // first answer.
    val liveSessionId = pane.extras.getString("streamsLiveId")?.takeIf { it.isNotBlank() }
    // A PIP is part of this screen, not a background music player: opening the
    // full player (or leaving the app) pauses it, and coming back resumes it.
    var appActive by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> appActive = true
                Lifecycle.Event.ON_STOP -> appActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A reused window (a newer, better server list arriving from the detail
    // search) keeps the same pane id, so the token is part of every playback
    // key; otherwise the old player would stay on screen.
    val playbackKey = Triple(pane.id, pane.sourceIndex, pane.resumeToken)
    var error by remember(playbackKey) { mutableStateOf<String?>(null) }
    var loadingLabel by remember(playbackKey) { mutableStateOf<String?>(null) }
    var resolvedUrl by remember(playbackKey) { mutableStateOf<String?>(null) }
    var resolvedMime by remember(playbackKey) { mutableStateOf<String?>(null) }
    var player by remember(playbackKey, resolvedUrl) { mutableStateOf<ExoPlayer?>(null) }
    var positionMs by remember(playbackKey) { mutableLongStateOf(pane.resumePositionMs) }
    var durationMs by remember(playbackKey) { mutableLongStateOf(0L) }
    var isPlaying by remember(playbackKey) { mutableStateOf(false) }
    var hasStarted by remember(playbackKey) { mutableStateOf(pane.resumePositionMs > 0L) }
    // A first RENDERED frame, not merely ExoPlayer "playing": a misdetected
    // wrapper page or an audio-only rendition can report READY/isPlaying and
    // used to freeze the window on the wrong server.
    var hasFrame by remember(playbackKey) { mutableStateOf(false) }
    var muted by remember(pane.id) { mutableStateOf(pane.muted) }
    var touchCount by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var volume by remember(pane.id) { mutableFloatStateOf(1f) }
    val locked = pane.locked
    val started = positionMs > 0L || isPlaying
    val tvInput = com.hikari.app.tv.TvMode.isTv
    // A television has no tap to bring the top bar back, so its controls stay
    // composed (and focusable) for the remote; a phone still gets the clean
    // auto-hiding strip.
    val showControls = !locked && (controlsVisible || tvInput)
    // The transport row is the one thing that must never vanish under a
    // finger. It stays put while the window is unlocked; only the title bar
    // times out, and a tap brings it back.
    val showBottomControls = !locked

    fun interacted() {
        touchCount++
        PipWindowStore.bringToFront(pane.id)
    }

    LaunchedEffect(pane.muted) {
        muted = pane.muted
        player?.volume = if (muted) 0f else volume
    }
    LaunchedEffect(touchCount) {
        controlsVisible = true
        delay(8_000L)
        controlsVisible = false
    }

    // Resolve the source: direct URLs play at once, torrents ask TorrServer for
    // a local stream first (the same engine the full player uses).
    val frameNow by rememberUpdatedState(hasFrame)
    LaunchedEffect(playbackKey, liveSessionId) {
        val id = liveSessionId ?: return@LaunchedEffect
        StreamsLive.flow(id).collect { live ->
            if (live.isEmpty()) return@collect
            PipWindowStore.mergeSources(
                id = pane.id,
                incoming = live.map { it.toPipSource() },
                canSwitch = !frameNow,
            )
        }
    }

    LaunchedEffect(playbackKey) {
        error = null
        resolvedUrl = null
        resolvedMime = null
        player = null
        loadingLabel = null
        if (source.hasDrm) {
            error = "DRM-protected stream. Tap to open the full player."
            return@LaunchedEffect
        }
        if (!source.isTorrent && source.url.isNotBlank()) {
            // The search has usually probed wrapper links already; use that
            // resolved media URL when it is still fresh instead of asking the
            // page to resolve again.
            val cached = StreamProbe.cached(source.url)
            if (cached != null) {
                resolvedMime = cached.mime
                resolvedUrl = cached.url
            } else if (isDirectMediaUrl(source) || source.isLive) {
                // IPTV channels are often bare gateway paths with no file
                // extension (`/user/pass/12345`) but they are still the live
                // media URL; walking a wrapper page here only added a 14 s
                // "Finding stream…" wait before playback.
                resolvedUrl = source.url
            } else {
                // A provider link that is not a media file itself (a Plavio /
                // hubcloud / pixeldrain page) has to be walked exactly like the
                // full player does before ExoPlayer can see a picture.
                loadingLabel = com.hikari.app.i18n.I18n.t("Finding stream…")
                val resolved = withTimeoutOrNull(14_000L) {
                    runCatching { StreamProbe.resolve(source.url, source.headers) }.getOrNull()
                }
                loadingLabel = null
                resolvedMime = resolved?.mime
                resolvedUrl = resolved?.url?.takeIf { it.isNotBlank() } ?: source.url
            }
            return@LaunchedEffect
        }
        if (source.isTorrent || source.infoHash != null || source.url.startsWith("magnet:", true)) {
            loadingLabel = "Starting torrent engine…"
            val url = withTimeoutOrNull(45_000L) {
                withContext(Dispatchers.IO) { runCatching { resolveTorrentUrl(source) }.getOrNull() }
            }
            loadingLabel = null
            if (url != null) {
                resolvedUrl = url
            } else {
                error = "This torrent didn't start in the preview. Tap to open the full player."
            }
            return@LaunchedEffect
        }
        error = "No preview stream. Tap to open the full player."
    }

    // Build and release the player. A new source/URL rebuilds it; minimizing
    // disposes it entirely, so the folder holds no decoder while folded away.
    LaunchedEffect(playbackKey, resolvedUrl) {
        val url = resolvedUrl ?: return@LaunchedEffect
        val built = runCatching {
            buildPipPlayer(context, source, url, resolvedMime, muted, pane.resumePositionMs)
        }.getOrNull()
        if (built == null) {
            error = "This stream didn't open. Tap to open the full player."
            return@LaunchedEffect
        }
        player = built
    }
    DisposableEffect(player) {
        val current = player
        onDispose { runCatching { current?.release() } }
    }
    LaunchedEffect(player, appActive) {
        player?.playWhenReady = appActive
    }

    DisposableEffect(player, playbackKey) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasFrame = true
            }

            override fun onPlayerError(e: PlaybackException) {
                com.hikari.app.data.Logs.log(
                    "PIP",
                    "playback error code=${e.errorCode} (${e.errorCodeName}) msg=${e.message} cause=${e.cause}",
                )
                // Before the first frame a failed server is a bad server: try
                // the next one. After the picture has started, switching a VOD
                // source would restart the film from zero — let the player
                // recover, and only surface the error if it cannot.
                val canFailover = !hasFrame || source.isLive
                if (canFailover && pane.sourceIndex + 1 < pane.sources.size) onSourceFailed()
                else error = pipErrorMessage(e)
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        val stallLimitMs = if (source.isLive) 30_000L else 20_000L
        var bufferingSince = 0L
        var stalled = false
        while (true) {
            positionMs = p.currentPosition.coerceAtLeast(0L)
            durationMs = p.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            isPlaying = p.isPlaying
            if (p.isPlaying) hasStarted = true
            // A server that accepts the connection but never sends a frame
            // would otherwise sit on "Tuning…" forever. Try the next server
            // after a realistic buffer window, exactly like the full player's
            // failover; only stop with an explanation on the last one.
            if (!stalled && p.playWhenReady && p.playbackState == Player.STATE_BUFFERING) {
                val now = SystemClock.elapsedRealtime()
                if (bufferingSince == 0L) bufferingSince = now
                if (now - bufferingSince >= stallLimitMs) {
                    // A VOD that has already drawn a frame must be allowed to
                    // ride out a buffer dip all the way to the end; only a
                    // live feed or a never-started window treats a long stall
                    // as a bad server.
                    if (hasFrame && !source.isLive) {
                        bufferingSince = now
                    } else {
                        stalled = true
                        if (pane.sourceIndex + 1 < pane.sources.size) {
                            onSourceFailed()
                            return@LaunchedEffect
                        }
                        p.pause()
                        error = "This server is not responding. Tap to open the full player."
                    }
                }
            } else {
                bufferingSince = 0L
            }
            delay(500L)
        }
    }

    val moveByNow by rememberUpdatedState(onMoveBy)
    val resizeByNow by rememberUpdatedState(onResizeBy)
    var adjustMode by remember(pane.id) { mutableStateOf(PipAdjustMode.NONE) }
    val adjustStepPx = with(LocalDensity.current) { 24.dp.toPx() }
    val moveFocus = remember { FocusRequester() }
    // Deliberately NO auto-focus. On a television that trapped the remote in
    // the floating window, so the page could not be browsed or another title
    // started while a PIP was playing. The PIP pill's controls sheet is the
    // explicit way in; the window's own buttons stay focusable when the viewer
    // navigates to them.

    Box(modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            // Any touch inside the window belongs to the window. Without this
            // a tap in the black strip between two controls fell through to the
            // page underneath (tapping "Links" behind the PIP's transport bar).
            // Child buttons and the video/title gestures still win first.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { interacted() },
    ) {
        // Fixed strip above the video: when controls time out it stays black,
        // so showing them again never resizes or shifts the video.
        Box(
            Modifier.fillMaxWidth()
                .height(if (tvInput) 52.dp else 42.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (locked) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr("Locked"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    PipButton(Icons.Rounded.Lock, tr("Unlock preview")) {
                        interacted()
                        onToggleLock()
                    }
                }
            } else if (showControls) {
                PipTopBar(
                    title = pane.title,
                    subtitle = pane.subtitle,
                    onTouch = { interacted() },
                    onMoveBy = onMoveBy,
                    onToggleLock = onToggleLock,
                    onExpand = { interacted(); onExpand(positionMs) },
                    onClose = onClose,
                    onMinimizeClick = { interacted(); onMinimize(positionMs) },
                    tv = tvInput,
                    moveFocus = moveFocus,
                    onMoveMode = { interacted(); adjustMode = PipAdjustMode.MOVE },
                    onResizeMode = { interacted(); adjustMode = PipAdjustMode.RESIZE },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
        ) {
            if (pane.artwork != null) {
                AsyncImage(
                    model = pane.artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.25f),
                                0.55f to Color.Black.copy(alpha = 0.55f),
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                )
            }

            player?.let { p ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = p
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.player = p
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    },
                )
            }

            if (!locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { interacted() }
                        .pointerInput(locked) {
                            val resizeCornerPx = 56.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val longPress = awaitLongPressOrCancellation(down.id)
                                if (longPress != null) {
                                    longPress.consume()
                                    interacted()
                                    val resizing = down.position.x >= size.width - resizeCornerPx &&
                                        down.position.y >= size.height - resizeCornerPx
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.positionChanged()) {
                                            val delta = change.positionChange()
                                            change.consume()
                                            if (resizing) resizeByNow(delta.x, delta.y) else moveByNow(delta.x, delta.y)
                                        }
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                    }
                                } else if (currentEvent.changes.any { it.id == down.id && it.pressed }) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.positionChanged()) change.consume()
                                        if (!change.pressed) break
                                    }
                                }
                            }
                        },
                )
            } else {
                // Locked: the video/overlay takes every touch so nothing lands
                // on the list behind the window.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { },
                )
            }

            if (showControls) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = tr("Hold and drag to resize"),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            if (!started && error == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (pane.logo != null) {
                            AsyncImage(
                                model = pane.logo,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(width = 96.dp, height = 54.dp),
                            )
                        } else {
                            Text(
                                pane.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            loadingLabel ?: tr("Tuning…"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }

            error?.let { message ->
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .clickable { onExpand(positionMs) }
                        .padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tr("Tap to open"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Fixed strip below the video, for the same reason as the top one.
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (tvInput) 58.dp else 48.dp)
                .background(Color.Black)
                .then(
                    if (locked) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (showBottomControls) {
                PipBottomBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    muted = muted,
                    onTouch = { interacted() },
                    onPlayPause = {
                        interacted()
                        player?.let { p -> if (p.isPlaying) p.pause() else p.play() }
                    },
                    onSeek = { fraction ->
                        interacted()
                        if (durationMs > 0L) player?.seekTo((durationMs * fraction).toLong())
                    },
                    onMute = {
                        interacted()
                        muted = !muted
                        // Volume-down can reach 0; unmuting from there must
                        // restore an audible level instead of staying silent.
                        if (!muted && volume <= 0f) volume = 1f
                        player?.volume = if (muted) 0f else volume
                        onMuteChange(muted)
                    },
                    onVolumeDown = {
                        interacted()
                        volume = (volume - 0.1f).coerceIn(0f, 1f)
                        muted = volume <= 0f
                        player?.volume = if (muted) 0f else volume
                        onMuteChange(muted)
                    },
                    onVolumeUp = {
                        interacted()
                        volume = (volume + 0.1f).coerceIn(0f, 1f)
                        muted = volume <= 0f
                        player?.volume = if (muted) 0f else volume
                        onMuteChange(muted)
                    },
                    onResizeBy = onResizeBy,
                )
            }
        }
    }

    // Remote move/resize mode. A television has no drag, so the two title-bar
    // buttons above put the window into a bounds mode: arrows nudge it, OK or
    // Back finishes. The overlay takes focus so the page's own D-pad handling
    // cannot consume the arrows.
    if (adjustMode != PipAdjustMode.NONE) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(adjustMode) {
            runCatching { focus.requestFocus() }
        }
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (adjustMode == PipAdjustMode.MOVE) moveByNow(-adjustStepPx, 0f)
                            else resizeByNow(-adjustStepPx, 0f)
                            true
                        }
                        Key.DirectionRight -> {
                            if (adjustMode == PipAdjustMode.MOVE) moveByNow(adjustStepPx, 0f)
                            else resizeByNow(adjustStepPx, 0f)
                            true
                        }
                        Key.DirectionUp -> {
                            if (adjustMode == PipAdjustMode.MOVE) moveByNow(0f, -adjustStepPx)
                            else resizeByNow(0f, -adjustStepPx)
                            true
                        }
                        Key.DirectionDown -> {
                            if (adjustMode == PipAdjustMode.MOVE) moveByNow(0f, adjustStepPx)
                            else resizeByNow(0f, adjustStepPx)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape -> {
                            adjustMode = PipAdjustMode.NONE
                            true
                        }
                        else -> false
                    }
                }
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                if (adjustMode == PipAdjustMode.MOVE) {
                    tr("Move: arrows · OK to finish")
                } else {
                    tr("Resize: arrows · OK to finish")
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    }
}

/** Builds the ExoPlayer used inside one PIP window. */
private fun buildPipPlayer(
    context: Context,
    source: PipSource,
    url: String,
    mime: String?,
    muted: Boolean,
    resumePositionMs: Long,
): ExoPlayer {
    val isLoopback = isPipLoopback(source.url)
    val isCs3 = source.providerId.startsWith("cs3|")
    var client = when {
        isCs3 -> runCatching { com.hikari.app.cs3.Cs3MainApiProvider.cs3BaseClient() }
            .getOrNull() ?: PlayerHttp.client
        isLoopback -> PlayerHttp.loopbackClient
        else -> PlayerHttp.client
    }
    // CS3 providers may install a per-source interceptor (signed CDN tokens,
    // required Referer); the full player attaches it, so the PIP must too.
    if (isCs3) {
        runCatching { com.hikari.app.cs3.Cs3MainApiProvider.videoInterceptorFor(source.url) }
            .getOrNull()
            ?.let { client = client.newBuilder().addInterceptor(it).build() }
    }
    val clean = source.headers.filter { (_, v) -> v.all { c -> c.code in 32..126 } }
    val headers = buildMap {
        // An extension's loopback proxy carries its upstream's Referer/UA in
        // its own query; explicit video headers still win.
        if (isLoopback) putAll(pipLoopbackHeaders(source.url))
        putAll(clean)
        if (none { it.key.equals("Referer", ignoreCase = true) } &&
            source.url.contains("fastream", ignoreCase = true)
        ) {
            put("Referer", "https://fastream.to")
        }
    }
    val ua = IptvPlaybackForklift.pickUa(headers["User-Agent"], source.url)
    val network = OkHttpDataSource.Factory(client)
        .setUserAgent(ua)
        .setDefaultRequestProperties(headers)
    val dataSource = DefaultDataSource.Factory(context, network)
    // VOD benefits from the shared disk cache on re-open/seek; live segments
    // are never replayed and only add writing load.
    val playbackSource = if (source.isLive || source.local) dataSource else {
        runCatching { PlayerMediaCache.wrap(context, dataSource) }.getOrDefault(dataSource)
    }
    val mediaSourceFactory = DefaultMediaSourceFactory(playbackSource)
        .setLoadErrorHandlingPolicy(RetryFriendlyLoadErrorPolicy())
    val lowTv = isLowMemoryTv(context)
    val trackSelector = DefaultTrackSelector(context).apply {
        val params = buildUponParameters().setPreferredAudioLanguage("en")
        if (lowTv) {
            params.setMaxVideoSize(1920, 1080).setMaxVideoBitrate(8_000_000)
        }
        setParameters(params)
    }
    val player = ExoPlayer.Builder(context)
        // The full player's decoder stack: nextlib registers FFmpeg software
        // decoders for the audio/video tracks this TV's MediaCodec cannot
        // handle (E-AC-3, some HEVC profiles), and decoder fallback turns a
        // choking hardware codec into a software one instead of the "use the
        // full player" error. The bare builder here was why many channels
        // showed that message in PIP.
        .setRenderersFactory(
            NextRenderersFactory(context).setEnableDecoderFallback(true),
        )
        .setTrackSelector(trackSelector)
        .setLoadControl(pipLoadControl(lowTv = lowTv, live = source.isLive))
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ false,
        )
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
    val mimeLower = mime?.lowercase()
    val media = MediaItem.Builder().apply {
        setUri(url)
        when {
            source.isM3u8 || mimeLower?.contains("mpegurl") == true ->
                setMimeType(MimeTypes.APPLICATION_M3U8)
            source.isMpd || mimeLower?.contains("dash") == true ->
                setMimeType(MimeTypes.APPLICATION_MPD)
            !mime.isNullOrBlank() -> setMimeType(mime)
        }
    }.build()
    player.setMediaItem(media)
    player.volume = if (muted) 0f else 1f
    player.playWhenReady = true
    player.prepare()
    if (resumePositionMs > 0L) player.seekTo(resumePositionMs)
    return player
}

private fun isPipLoopback(url: String): Boolean =
    runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
        ?.let { it == "localhost" || it == "127.0.0.1" || it == "::1" || it == "[::1]" } == true

/** 2 GB television boxes (the onn 4K class) cannot decode 4K and hold a deep
 *  buffer at once. Cap them at the panel's 1080p and a sane bitrate instead of
 *  dropping frames while the network fills a bank they will never watch. */
private fun isLowMemoryTv(context: Context): Boolean {
    if (!com.hikari.app.tv.TvMode.isTv) return false
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
    val info = ActivityManager.MemoryInfo()
    runCatching { am.getMemoryInfo(info) }
    return info.totalMem in 1L..(3L * 1024 * 1024 * 1024)
}

/** PiP's own buffer policy: enough to ride a CDN dip, far short of the full
 *  player's three-minute VOD bank. A television has one decoder budget shared
 *  by every floating window, so this is deliberately leaner than the phone
 *  path as well. */
private fun pipLoadControl(lowTv: Boolean, live: Boolean): DefaultLoadControl {
    val minMs = when {
        live && lowTv -> 10_000
        live -> 15_000
        lowTv -> 15_000
        else -> 20_000
    }
    val maxMs = when {
        live && lowTv -> 30_000
        live -> 45_000
        lowTv -> 60_000
        else -> 90_000
    }
    val startMs = if (live) 2_000 else if (lowTv) 1_500 else 1_000
    val rebufferMs = if (lowTv) 4_000 else 3_000
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minMs, maxMs, startMs, rebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(
            if (lowTv) 24 * 1024 * 1024 else 40 * 1024 * 1024,
        )
        .build()
}

private fun pipLoopbackHeaders(proxyUrl: String): Map<String, String> {
    val query = runCatching { java.net.URI(proxyUrl).query }.getOrNull().orEmpty()
    if (query.isBlank()) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (part in query.split('&')) {
        val k = part.substringBefore('=').trim().lowercase()
        val v = runCatching {
            java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }.getOrDefault("").trim()
        if (v.isBlank()) continue
        when (k) {
            "referer", "referrer" -> out.putIfAbsent("Referer", v)
            "useragent", "user-agent" -> out.putIfAbsent("User-Agent", v)
        }
    }
    return out
}

/** Asks TorrServer (the same engine the full player uses) for a local URL. */
private suspend fun resolveTorrentUrl(source: PipSource): String? {
    val magnet = buildMagnet(source) ?: return null
    val link = com.lagradost.cloudstream3.utils.newExtractorLink(
        source = "Torrent",
        name = source.name,
        url = magnet,
    )
    val (playable, _) = com.lagradost.cloudstream3.ui.player.Torrent.transformLink(link)
    return playable.url.takeIf { it.isNotBlank() }
}

private fun buildMagnet(source: PipSource): String? {
    if (source.url.startsWith("magnet:", true)) {
        return if (source.fileIdx != null && !source.url.contains("index=")) {
            source.url + (if (source.url.contains("?")) "&" else "?") + "index=" + source.fileIdx
        } else {
            source.url
        }
    }
    val hash = source.infoHash ?: return null
    val sb = StringBuilder("magnet:?xt=urn:btih:").append(hash)
    if (source.name.isNotBlank()) {
        sb.append("&dn=").append(java.net.URLEncoder.encode(source.name, "UTF-8"))
    }
    val trackers = (source.trackers + PIP_TRACKERS).distinct()
    trackers.forEach { t ->
        val clean = t.removePrefix("tracker:")
        if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("udp://")) {
            sb.append("&tr=").append(java.net.URLEncoder.encode(clean, "UTF-8"))
        }
    }
    source.fileIdx?.let { sb.append("&index=").append(it) }
    return sb.toString()
}

private val PIP_TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://tracker.openbittorrent.com:6969/announce",
)

private fun pipErrorMessage(error: PlaybackException): String =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "Stream unavailable in the preview. Tap to open the full player."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This format needs the full player. Tap to open it."
        else -> "This stream didn't open. Tap to open the full player."
    }

@Composable
private fun PipTopBar(
    title: String,
    subtitle: String?,
    onTouch: () -> Unit,
    onMoveBy: (dxPx: Float, dyPx: Float) -> Unit,
    onToggleLock: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onMinimizeClick: () -> Unit,
    tv: Boolean = false,
    moveFocus: FocusRequester? = null,
    onMoveMode: () -> Unit = {},
    onResizeMode: () -> Unit = {},
) {
    val moveByNow by rememberUpdatedState(onMoveBy)
    val touchNow by rememberUpdatedState(onTouch)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Minimize lives on the far left, away from Close on the right, so it
        // cannot be hit by mistake when reaching for the close button.
        PipButton(Icons.Rounded.Minimize, tr("Minimize preview")) {
            onTouch(); onMinimizeClick()
        }
        // The title area is this window's move handle. It claims the touch on
        // the way down, so the scrollable list behind cannot steal the drag.
        Row(
            Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        touchNow()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.positionChanged()) {
                                val delta = change.positionChange()
                                change.consume()
                                moveByNow(delta.x, delta.y)
                            }
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = tr("Drag to move"),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (tv) {
            PipButton(
                Icons.Filled.OpenWith,
                tr("Move window with arrows"),
                modifier = if (moveFocus != null) Modifier.focusRequester(moveFocus) else Modifier,
            ) {
                onTouch(); onMoveMode()
            }
            PipButton(Icons.Filled.AspectRatio, tr("Resize window with arrows")) {
                onTouch(); onResizeMode()
            }
        }
        PipButton(Icons.Rounded.LockOpen, tr("Lock position")) {
            onTouch(); onToggleLock()
        }
        PipButton(Icons.Rounded.Fullscreen, tr("Open full player")) {
            onTouch(); onExpand()
        }
        PipButton(Icons.Filled.Close, tr("Close preview")) {
            onClose()
        }
    }
}

@Composable
private fun PipBottomBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    muted: Boolean,
    onTouch: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onMute: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onResizeBy: (dxPx: Float, dyPx: Float) -> Unit,
) {
    val resizeByNow by rememberUpdatedState(onResizeBy)
    val touchNow by rememberUpdatedState(onTouch)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 200.dp
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            if (durationMs > 0L) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                onSeek((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth((positionMs.toFloat() / durationMs).coerceIn(0f, 1f))
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Text(
                        "LIVE",
                        fontSize = 8.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PipButton(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) tr("Pause") else tr("Play"),
                    onClick = onPlayPause,
                )
                Text(
                    if (durationMs > 0L) formatPipTime(positionMs) else "LIVE",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                PipButton(
                    if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    if (muted) tr("Unmute") else tr("Mute"),
                    onClick = onMute,
                )
                if (!compact) {
                    PipButton(Icons.Rounded.VolumeDown, tr("Volume down"), onClick = onVolumeDown)
                    PipButton(Icons.Rounded.VolumeUp, tr("Volume up"), onClick = onVolumeUp)
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                touchNow()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.positionChanged()) {
                                        val delta = change.positionChange()
                                        change.consume()
                                        resizeByNow(delta.x, delta.y)
                                    }
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = tr("Drag to resize"),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tv = com.hikari.app.tv.TvMode.isTv
    Box(
        modifier
            .size(if (tv) 42.dp else 34.dp)
            .clip(CircleShape)
            .then(
                if (tv) Modifier.tvPress(previewPass = true, onClick = onClick)
                else Modifier.clickable(onClick = onClick),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(if (tv) 20.dp else 17.dp),
        )
    }
}

private fun formatPipTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
