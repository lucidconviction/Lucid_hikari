package com.hikari.app.data.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hikari.app.data.IptvChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * DataStore-backed store for quick/pinned channels.
 * Channels are stored as JSON-serialized IptvChannel objects in a JSON string.
 */
private val Context.quickChannelDataStore by preferencesDataStore(name = "quick_channels")

class QuickChannelStore(private val ctx: Context) {

    private val store: DataStore<Preferences> get() = ctx.quickChannelDataStore

    private val KEY_PINNED_CHANNELS = stringPreferencesKey("pinned_channels")

    private val json = Json { ignoreUnknownKeys = true }

    /** Flow of all pinned channels */
    val pinnedChannelsFlow: Flow<List<IptvChannel>> = store.data
        .map { prefs ->
            val jsonStr = prefs[KEY_PINNED_CHANNELS] ?: "[]"
            runCatching { json.decodeFromString<List<IptvChannel>>(jsonStr) }.getOrDefault(emptyList())
        }
        .distinctUntilChanged()

    /** Get current pinned channels */
    suspend fun getPinnedChannels(): List<IptvChannel> = pinnedChannelsFlow.first()

    /** Check if a channel is pinned */
    suspend fun isPinned(channel: IptvChannel): Boolean = getPinnedChannels().any { it.url == channel.url }

    /** Pin a channel */
    suspend fun pinChannel(channel: IptvChannel) {
        store.edit { prefs ->
            val current = prefs[KEY_PINNED_CHANNELS] ?: "[]"
            val currentList = runCatching { json.decodeFromString<List<IptvChannel>>(current) }.getOrDefault(emptyList())
            val updated = currentList + channel
            prefs[KEY_PINNED_CHANNELS] = json.encodeToString(updated)
        }
    }

    /** Unpin a channel */
    suspend fun unpinChannel(channel: IptvChannel) {
        store.edit { prefs ->
            val current = prefs[KEY_PINNED_CHANNELS] ?: "[]"
            val currentList = runCatching { json.decodeFromString<List<IptvChannel>>(current) }.getOrDefault(emptyList())
            val updated = currentList.filterNot { it.url == channel.url }
            prefs[KEY_PINNED_CHANNELS] = json.encodeToString(updated)
        }
    }

    /** Toggle pin state */
    suspend fun togglePin(channel: IptvChannel) {
        if (isPinned(channel)) unpinChannel(channel) else pinChannel(channel)
    }

    /** Clear all pinned channels */
    suspend fun clearAll() {
        store.edit { prefs ->
            prefs[KEY_PINNED_CHANNELS] = "[]"
        }
    }

    companion object {
        @Volatile private var INSTANCE: QuickChannelStore? = null

        fun getInstance(context: Context): QuickChannelStore {
            return INSTANCE ?: synchronized(this) {
                val instance = QuickChannelStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

fun CoroutineScope.launchQuickChannelStore(context: Context, action: suspend QuickChannelStore.() -> Unit) {
    launch(Dispatchers.IO) {
        QuickChannelStore.getInstance(context.applicationContext).action()
    }
}
