package com.hikari.app.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.hikari.app.HikariApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * F4 — Multi-window state store, ported from Nuvio's MultiWindowStore.
 *
 * One [WindowStream] per open slot (0..8). Per-stream volume / pause / audio
 * focus / resize mode are tracked alongside the stream list so the grid UI
 * can render per-cell controls without recomposing the whole pool.
 */
data class WindowStream(
    val id: String,
    val name: String,
    val url: String,
    val slotIndex: Int,
    val logo: String? = null,
    val isPlaying: Boolean = false,
)

const val RESIZE_FILL = 0
const val RESIZE_FIT = 1
const val RESIZE_FIXED_WIDTH = 2
const val RESIZE_FIXED_HEIGHT = 3
const val RESIZE_ZOOM = 4

object MultiWindowStore {
    private const val MAX_SLOTS = 9
    private val streams = mutableStateListOf<WindowStream>()
    private var idCounter = 0L
    private val volumes = mutableStateMapOf<String, Float>()
    private val paused = mutableStateMapOf<String, Boolean>()
    /** Last known playback position per window, so leaving MultiView pauses
     *  the wall without losing where each channel was. */
    private val positions = mutableMapOf<String, Long>()
    private var _audioFocusId: String? = null
    private val playerIndexById = mutableMapOf<String, Int>()
    private val resizeModes = mutableStateMapOf<String, Int>()
    private val _currentLayout = mutableStateOf<MultiViewLayout?>(null)
    private val _layoutLocked = mutableStateOf(false)
    private val slotPlaylists = mutableMapOf<Int, List<WindowStream>>()
    private val slotPlaylistIdx = mutableMapOf<Int, Int>()
    private val _fullscreenStreamId = mutableStateOf<String?>(null)

    // ── Layout persistence ────────────────────────────────────────────────
    /**
     * One saved multi-window configuration: a name, the layout key, and the
     * streams each slot holds (name + url + logo). Tapping a saved layout in
     * the multi-window page restores every slot at once — no re-pasting links.
     */
    @Serializable
    data class SavedLayout(
        val name: String,
        val layoutKey: String,
        val slots: List<SavedSlot> = emptyList(),
    )

    @Serializable
    data class SavedSlot(
        val slotIndex: Int,
        val name: String,
        val url: String,
        val logo: String? = null,
    )

    private const val LAYOUTS_FILE = "multiview_layouts.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _savedLayouts = mutableStateListOf<SavedLayout>()

    val savedLayouts: List<SavedLayout> get() = _savedLayouts

    fun initLayouts(cacheDir: File) {
        _savedLayouts.clear()
        val f = File(cacheDir, LAYOUTS_FILE)
        if (!f.exists()) return
        val raw = try { f.readText() } catch (_: Exception) { "" }
        if (raw.isBlank()) return
        try { _savedLayouts.addAll(json.decodeFromString<List<SavedLayout>>(raw)) } catch (_: Exception) {}
    }

    private fun persistLayouts(cacheDir: File) {
        runCatching { File(cacheDir, LAYOUTS_FILE).writeText(json.encodeToString(_savedLayouts.toList())) }
    }

    /**
     * Saves the CURRENT multi-window configuration under [name]. Overwrites a
     * layout already saved under that name (so "Save Layouts" is idempotent —
     * re-saving a layout you just tweaked updates it in place).
     */
    fun saveLayouts(cacheDir: File, name: String) {
        val key = name.trim().ifBlank { "Layout ${_savedLayouts.size + 1}" }
        val snapshot = streams.map { s ->
            SavedSlot(slotIndex = s.slotIndex, name = s.name, url = s.url, logo = s.logo)
        }.sortedBy { it.slotIndex }
        val layoutKey = _currentLayout.value?.name ?: "GRID_2x3"
        val saved = SavedLayout(name = key, layoutKey = layoutKey, slots = snapshot)
        val idx = _savedLayouts.indexOfFirst { it.name.equals(key, ignoreCase = true) }
        if (idx >= 0) _savedLayouts[idx] = saved else _savedLayouts.add(saved)
        persistLayouts(cacheDir)
    }

    /** Remove a saved layout by name. */
    fun deleteLayout(cacheDir: File, name: String) {
        if (_savedLayouts.removeAll { it.name.equals(name, ignoreCase = true) }) {
            persistLayouts(cacheDir)
        }
    }

    /**
     * Replace the saved-layouts list in place (used by the rename path so the
     * sheet's snapshot is the source of truth). Persists to disk.
     */
    fun replaceLayouts(cacheDir: File, list: List<SavedLayout>) {
        _savedLayouts.clear()
        _savedLayouts.addAll(list)
        persistLayouts(cacheDir)
    }

    /** Overload without a cache dir — in-memory only (rename preview). */
    fun replaceLayouts(list: List<SavedLayout>) {
        _savedLayouts.clear()
        _savedLayouts.addAll(list)
    }

    /**
     * Apply a saved layout: clear the current grid, then re-add every slot's
     * stream in slot order. The layout lock follows the saved layout key so the
     * pill strip shows the same shape the user saved.
     */
    fun applyLayout(cacheDir: File, name: String): Boolean {
        val saved = _savedLayouts.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        clear()
        val layout = runCatching { MultiViewLayout.fromKey(saved.layoutKey) }.getOrNull()
        if (layout != null) setLayout(layout) else setAutoLayout()
        saved.slots.forEach { s ->
            addToSlot(s.name, s.url, s.logo, s.slotIndex)
        }
        return true
    }

    fun setSlotPlaylist(slotIndex: Int, channels: List<WindowStream>) {
        slotPlaylists[slotIndex] = channels
        slotPlaylistIdx[slotIndex] = -1
    }

    fun nextInSlot(slotIndex: Int): WindowStream? {
        val list = slotPlaylists[slotIndex] ?: return null
        val next = (slotPlaylistIdx[slotIndex] ?: -1) + 1
        if (next >= list.size) {
            slotPlaylists.remove(slotIndex)
            slotPlaylistIdx.remove(slotIndex)
            return null
        }
        slotPlaylistIdx[slotIndex] = next
        return list[next]
    }

    fun markSlotCurrent(slotIndex: Int, url: String) {
        val list = slotPlaylists[slotIndex] ?: return
        val idx = list.indexOfFirst { it.url == url }
        if (idx >= 0) slotPlaylistIdx[slotIndex] = idx
    }

    val allStreams: List<WindowStream> get() = streams

    fun addToSlot(name: String, url: String, logo: String? = null, slotIndex: Int) {
        val limit = maxSlots()
        val existingAtRequested = streams.indexOfFirst { it.slotIndex == slotIndex }
        // Portrait holds four windows comfortably; past that each cell is too
        // small to watch and the player pool starts competing for memory. A
        // request for slot 5+ in portrait is redirected to the first free slot
        // under the cap, and refused (with a reason) when all four are taken.
        val effectiveSlot = if (slotIndex < limit) {
            slotIndex
        } else {
            (0 until limit).firstOrNull { isSlotAvailable(it) } ?: run {
                if (existingAtRequested < 0) {
                    runCatching {
                        Toast.makeText(
                            HikariApp.instance,
                            "Phone portrait MultiView is limited to $limit windows. A tablet can use up to nine.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return
                }
                slotIndex
            }
        }
        val id = "mw_${++idCounter}"
        val hasFocus = _audioFocusId == null
        volumes[id] = if (hasFocus) 1f else 0f
        if (hasFocus) _audioFocusId = id
        val existingAtSlot = streams.indexOfFirst { it.slotIndex == effectiveSlot }
        val existingUrl = streams.indexOfFirst { it.url == url }
        if (existingUrl >= 0 && existingUrl != existingAtSlot) {
            streams.removeAt(existingUrl)
            val adjusted = streams.indexOfFirst { it.slotIndex == effectiveSlot }
            if (adjusted >= 0) {
                val oldId = streams[adjusted].id
                streams[adjusted] = WindowStream(id = id, name = name, url = url, slotIndex = effectiveSlot, logo = logo)
                volumes.remove(oldId); playerIndexById.remove(oldId); paused.remove(oldId); resizeModes.remove(oldId)
            } else {
                streams.add(WindowStream(id = id, name = name, url = url, slotIndex = effectiveSlot, logo = logo))
            }
        } else if (existingAtSlot >= 0) {
            val oldId = streams[existingAtSlot].id
            streams[existingAtSlot] = WindowStream(id = id, name = name, url = url, slotIndex = effectiveSlot, logo = logo)
            volumes.remove(oldId); playerIndexById.remove(oldId); paused.remove(oldId); resizeModes.remove(oldId)
        } else {
            streams.add(WindowStream(id = id, name = name, url = url, slotIndex = effectiveSlot, logo = logo))
        }
        if (_currentLayout.value == null) _layoutLocked.value = false
    }

    /** How many windows the current device and orientation allow. */
    fun maxSlots(): Int = runCatching {
        val app = HikariApp.instance
        val config = app.resources.configuration
        val portrait = config.orientation == Configuration.ORIENTATION_PORTRAIT
        // A phone in portrait gets four; a tablet (smallest width 600dp) can
        // use the full nine even when held upright.
        val tablet = config.smallestScreenWidthDp >= 600
        if (portrait && !tablet) 4 else MAX_SLOTS
    }.getOrDefault(MAX_SLOTS)

    fun removeSlot(slotIndex: Int) {
        streams.removeAll { it.slotIndex == slotIndex }
        slotPlaylists.remove(slotIndex); slotPlaylistIdx.remove(slotIndex)
    }

    fun remove(id: String) {
        streams.removeAll { it.id == id }
        volumes.remove(id); paused.remove(id); playerIndexById.remove(id); resizeModes.remove(id)
        if (_audioFocusId == id) _audioFocusId = null
    }

    fun getStreamsForSlot(slotIndex: Int): WindowStream? = streams.find { it.slotIndex == slotIndex }
    fun getOccupiedSlots(): List<Int> = streams.map { it.slotIndex }
    fun isSlotAvailable(slotIndex: Int): Boolean = streams.none { it.slotIndex == slotIndex }
    fun nextAvailableSlot(): Int? = (0 until maxSlots()).firstOrNull { isSlotAvailable(it) }

    fun setVolume(streamId: String, volume: Float) { volumes[streamId] = volume.coerceIn(0f, 1f) }
    fun getVolume(streamId: String): Float = volumes[streamId] ?: 0f
    fun setPaused(streamId: String, isPaused: Boolean) { paused[streamId] = isPaused }
    fun isPaused(streamId: String): Boolean = paused[streamId] ?: false

    /** Saves where a window was when the page closed, so the next visit resumes
     *  the channel instead of starting it from zero. */
    fun setPosition(streamId: String, positionMs: Long) {
        if (positionMs > 0L) positions[streamId] = positionMs
    }

    fun position(streamId: String): Long = positions[streamId] ?: 0L
    fun setAudioFocus(streamId: String) { _audioFocusId = streamId }
    fun isAudioFocused(streamId: String): Boolean = _audioFocusId == streamId
    fun storePlayerIndex(streamId: String, index: Int) { playerIndexById[streamId] = index }
    fun getPlayerIndex(streamId: String): Int? = playerIndexById[streamId]
    fun setResizeMode(streamId: String, mode: Int) { resizeModes[streamId] = mode }
    fun getResizeMode(streamId: String): Int = resizeModes[streamId] ?: RESIZE_FIT

    fun currentLayout(): MultiViewLayout? = _currentLayout.value
    fun isLayoutLocked(): Boolean = _layoutLocked.value
    fun setLayout(layout: MultiViewLayout) { _currentLayout.value = layout; _layoutLocked.value = true }
    fun setAutoLayout() { _currentLayout.value = null; _layoutLocked.value = false }

    /** The stream currently in fullscreen mode, or null when back in the grid. */
    val fullscreenStreamId: String? get() = _fullscreenStreamId.value
    fun setFullscreenStream(id: String?) { _fullscreenStreamId.value = id }
    fun clearFullscreen() { _fullscreenStreamId.value = null }
    fun resolveLayout(count: Int, isPortrait: Boolean, isTablet: Boolean = false): MultiViewLayout =
        _currentLayout.value ?: defaultLayout(count, isPortrait, isTablet)

    fun swapSlots(slotA: Int, slotB: Int) {
        val idxA = streams.indexOfFirst { it.slotIndex == slotA }
        val idxB = streams.indexOfFirst { it.slotIndex == slotB }
        if (idxA < 0 || idxB < 0) return
        val a = streams[idxA]
        val b = streams[idxB]
        streams[idxA] = b.copy(slotIndex = slotA)
        streams[idxB] = a.copy(slotIndex = slotB)
    }

    fun clear() {
        streams.clear(); volumes.clear(); paused.clear(); positions.clear(); playerIndexById.clear(); resizeModes.clear()
        slotPlaylists.clear(); slotPlaylistIdx.clear()
        _audioFocusId = null; _currentLayout.value = null; _layoutLocked.value = false
    }
}