package com.hikari.app.ui.screens

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf

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
    private var _audioFocusId: String? = null
    private val playerIndexById = mutableMapOf<String, Int>()
    private val resizeModes = mutableStateMapOf<String, Int>()
    private val _currentLayout = mutableStateOf<MultiViewLayout?>(null)
    private val _layoutLocked = mutableStateOf(false)
    private val slotPlaylists = mutableMapOf<Int, List<WindowStream>>()
    private val slotPlaylistIdx = mutableMapOf<Int, Int>()
    private val _fullscreenStreamId = mutableStateOf<String?>(null)

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
        val id = "mw_${++idCounter}"
        val hasFocus = _audioFocusId == null
        volumes[id] = if (hasFocus) 1f else 0f
        if (hasFocus) _audioFocusId = id
        val existingAtSlot = streams.indexOfFirst { it.slotIndex == slotIndex }
        val existingUrl = streams.indexOfFirst { it.url == url }
        if (existingUrl >= 0 && existingUrl != existingAtSlot) {
            streams.removeAt(existingUrl)
            val adjusted = streams.indexOfFirst { it.slotIndex == slotIndex }
            if (adjusted >= 0) {
                val oldId = streams[adjusted].id
                streams[adjusted] = WindowStream(id = id, name = name, url = url, slotIndex = slotIndex, logo = logo)
                volumes.remove(oldId); playerIndexById.remove(oldId); paused.remove(oldId); resizeModes.remove(oldId)
            } else {
                streams.add(WindowStream(id = id, name = name, url = url, slotIndex = slotIndex, logo = logo))
            }
        } else if (existingAtSlot >= 0) {
            val oldId = streams[existingAtSlot].id
            streams[existingAtSlot] = WindowStream(id = id, name = name, url = url, slotIndex = slotIndex, logo = logo)
            volumes.remove(oldId); playerIndexById.remove(oldId); paused.remove(oldId); resizeModes.remove(oldId)
        } else {
            streams.add(WindowStream(id = id, name = name, url = url, slotIndex = slotIndex, logo = logo))
        }
        if (_currentLayout.value == null) _layoutLocked.value = false
    }

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
    fun nextAvailableSlot(): Int? = (0 until MAX_SLOTS).firstOrNull { isSlotAvailable(it) }

    fun setVolume(streamId: String, volume: Float) { volumes[streamId] = volume.coerceIn(0f, 1f) }
    fun getVolume(streamId: String): Float = volumes[streamId] ?: 0f
    fun setPaused(streamId: String, isPaused: Boolean) { paused[streamId] = isPaused }
    fun isPaused(streamId: String): Boolean = paused[streamId] ?: false
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
        streams.clear(); volumes.clear(); paused.clear(); playerIndexById.clear(); resizeModes.clear()
        slotPlaylists.clear(); slotPlaylistIdx.clear()
        _audioFocusId = null; _currentLayout.value = null; _layoutLocked.value = false
    }
}