package com.hikari.app.data.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.multiWindowStore: DataStore<Preferences> by preferencesDataStore(name = "multi_window")

private val KEY_SLOTS = stringPreferencesKey("slots")
private val KEY_STREAMS = stringPreferencesKey("streams")
private val KEY_UPCOMING = stringPreferencesKey("upcoming")

/**
 * In-memory entity for a multi-window slot (persisted as JSON).
 */
@Serializable
data class MultiWindowSlotEntity(
    val slot: Int,
    val isOccupied: Boolean = false,
    val streamInfoId: String? = null,
    val isPaused: Boolean = false,
    val isAudioFocused: Boolean = false,
    val upcomingIndex: Int = 0,
)

/**
 * In-memory entity for a multi-window stream (persisted as JSON).
 */
@Serializable
data class MultiWindowStreamEntity(
    val id: String,
    val title: String?,
    val url: String,
    val provider: String?,
    val headersJson: String? = null,
    val drmJson: String? = null,
)

/**
 * In-memory entity for an upcoming playlist item (persisted as JSON).
 */
@Serializable
data class UpcomingPlaylistItemEntity(
    val slot: Int,
    val title: String,
    val url: String,
    val position: Int,
)

/**
 * Stream info used when populating a multi-window slot from a player stream.
 */
@Serializable
data class StreamInfo(
    val title: String?,
    val url: String,
    val provider: String?,
    val headers: Map<String, String> = emptyMap(),
    val drm: com.hikari.app.data.DrmSpec? = null,
)

/**
 * MultiWindowStore — DataStore-backed persistence for multi-window state.
 */
class MultiWindowStore(private val ctx: Context) {

    private val store = ctx.multiWindowStore
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ---- helpers ----

    private suspend fun readSlots(): List<MultiWindowSlotEntity> {
        val jsonStr = store.data.map { it[KEY_SLOTS] ?: "[]" }.first()
        return runCatching { json.decodeFromString<List<MultiWindowSlotEntity>>(jsonStr) }
            .getOrDefault(emptyList())
    }

    private suspend fun writeSlots(slots: List<MultiWindowSlotEntity>) {
        store.edit { prefs ->
            prefs[KEY_SLOTS] = json.encodeToString(slots)
        }
    }

    private suspend fun readStreams(): Map<String, MultiWindowStreamEntity> {
        val jsonStr = store.data.map { it[KEY_STREAMS] ?: "{}" }.first()
        return runCatching {
            json.decodeFromString<Map<String, MultiWindowStreamEntity>>(jsonStr)
        }.getOrDefault(emptyMap())
    }

    private suspend fun writeStreams(streams: Map<String, MultiWindowStreamEntity>) {
        store.edit { prefs ->
            prefs[KEY_STREAMS] = json.encodeToString(streams)
        }
    }

    private suspend fun readUpcoming(): Map<Int, List<UpcomingPlaylistItemEntity>> {
        val jsonStr = store.data.map { it[KEY_UPCOMING] ?: "{}" }.first()
        return runCatching {
            json.decodeFromString<Map<Int, List<UpcomingPlaylistItemEntity>>>(jsonStr)
        }.getOrDefault(emptyMap())
    }

    private suspend fun writeUpcoming(upcoming: Map<Int, List<UpcomingPlaylistItemEntity>>) {
        store.edit { prefs ->
            prefs[KEY_UPCOMING] = json.encodeToString(upcoming)
        }
    }

    // ---- slots ----

    val slots: Flow<List<MultiWindowSlotEntity>> = store.data
        .map { readSlots() }
        .map { it.sortedBy { s -> s.slot } }

    suspend fun getSlot(slot: Int): MultiWindowSlotEntity? =
        readSlots().firstOrNull { it.slot == slot }

    suspend fun occupySlot(slot: Int, streamInfo: StreamInfo) {
        val streamId = "stream_${System.currentTimeMillis()}"
        val stream = MultiWindowStreamEntity(
            id = streamId,
            title = streamInfo.title,
            url = streamInfo.url,
            provider = streamInfo.provider,
            headersJson = json.encodeToString(streamInfo.headers),
            drmJson = streamInfo.drm?.let { json.encodeToString(it) },
        )
        val streams = readStreams().toMutableMap()
        streams[streamId] = stream
        writeStreams(streams)

        val slots = readSlots().toMutableList()
        val existing = slots.indexOfFirst { it.slot == slot }
        if (existing >= 0) {
            slots[existing] = slots[existing].copy(
                isOccupied = true,
                streamInfoId = streamId,
            )
        } else {
            slots.add(MultiWindowSlotEntity(slot = slot, isOccupied = true, streamInfoId = streamId))
        }
        writeSlots(slots)
    }

    suspend fun releaseSlot(slot: Int) {
        val slots = readSlots().toMutableList()
        val idx = slots.indexOfFirst { it.slot == slot }
        if (idx >= 0) {
            val removed = slots.removeAt(idx)
            if (removed.streamInfoId != null) {
                val streams = readStreams().toMutableMap()
                streams.remove(removed.streamInfoId)
                writeStreams(streams)
            }
            slots.add(MultiWindowSlotEntity(slot = slot, isOccupied = false, streamInfoId = null))
            writeSlots(slots)
        }
        val upcoming = readUpcoming().toMutableMap()
        upcoming.remove(slot)
        writeUpcoming(upcoming)
    }

    suspend fun setPaused(slot: Int, paused: Boolean) {
        val slots = readSlots().toMutableList()
        val idx = slots.indexOfFirst { it.slot == slot }
        if (idx >= 0) {
            slots[idx] = slots[idx].copy(isPaused = paused)
            writeSlots(slots)
        }
    }

    suspend fun setAudioFocus(slot: Int, focused: Boolean) {
        val slots = readSlots().toMutableList()
        val idx = slots.indexOfFirst { it.slot == slot }
        if (idx >= 0) {
            slots[idx] = slots[idx].copy(isAudioFocused = focused)
            // clear focus from other slots
            for (i in slots.indices) {
                if (i != idx && slots[i].isAudioFocused) {
                    slots[i] = slots[i].copy(isAudioFocused = false)
                }
            }
        } else {
            slots.add(MultiWindowSlotEntity(slot = slot, isAudioFocused = focused))
        }
        writeSlots(slots)
    }

    suspend fun setAudioFocused(slot: Int) = setAudioFocus(slot, true)
    suspend fun clearAudioFocus(slot: Int) = setAudioFocus(slot, false)
    suspend fun isAudioFocused(slot: Int): Boolean = getSlot(slot)?.isAudioFocused ?: false
    suspend fun isPaused(slot: Int): Boolean = getSlot(slot)?.isPaused ?: false

    // ---- upcoming index ----

    suspend fun setUpcomingIndex(slot: Int, index: Int) {
        val slots = readSlots().toMutableList()
        val idx = slots.indexOfFirst { it.slot == slot }
        if (idx >= 0) {
            slots[idx] = slots[idx].copy(upcomingIndex = index)
            writeSlots(slots)
        }
    }

    suspend fun getUpcomingIndex(slot: Int): Int = getSlot(slot)?.upcomingIndex ?: 0

    // ---- upcoming playlist ----

    suspend fun setUpcomingPlaylist(slot: Int, items: List<Pair<String, String>>) {
        val upcoming = readUpcoming().toMutableMap()
        upcoming[slot] = items.mapIndexed { i, (title, url) ->
            UpcomingPlaylistItemEntity(slot = slot, title = title, url = url, position = i)
        }
        writeUpcoming(upcoming)
        setUpcomingIndex(slot, 0)
    }

    suspend fun getUpcomingPlaylist(slot: Int): List<Pair<String, String>> {
        return readUpcoming()[slot]?.map { it.title to it.url } ?: emptyList()
    }

    suspend fun getUpcomingCount(slot: Int): Int = readUpcoming()[slot]?.size ?: 0

    suspend fun clearUpcoming(slot: Int) {
        val upcoming = readUpcoming().toMutableMap()
        upcoming.remove(slot)
        writeUpcoming(upcoming)
    }

    companion object {
        @Volatile private var INSTANCE: MultiWindowStore? = null

        fun getInstance(context: Context): MultiWindowStore {
            return INSTANCE ?: synchronized(this) {
                val instance = MultiWindowStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

fun CoroutineScope.launchMultiWindowStore(context: Context, action: suspend MultiWindowStore.() -> Unit) {
    launch(Dispatchers.IO) {
        MultiWindowStore.getInstance(context.applicationContext).action()
    }
}
