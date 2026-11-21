package com.hikari.app.data.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hikari.app.data.YouTubeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.youTubeChannelDataStore by preferencesDataStore(name = "youtube_channels", corruptionHandler = com.hikari.app.data.ReplaceCorruptPreferences)

class YouTubeChannelStore(private val ctx: Context) {

    private val store: DataStore<Preferences> get() = ctx.youTubeChannelDataStore

    private val KEY_CHANNELS = stringPreferencesKey("channels")

    private val json = Json { ignoreUnknownKeys = true }

    /** Flow of all YouTube channels */
    val channelsFlow: Flow<List<YouTubeChannel>> = store.data
        .map { prefs ->
            val jsonStr = prefs[KEY_CHANNELS] ?: "[]"
            runCatching { json.decodeFromString<List<YouTubeChannel>>(jsonStr) }.getOrDefault(emptyList())
        }
        .distinctUntilChanged()

    /** Get current channels */
    suspend fun getChannels(): List<YouTubeChannel> = channelsFlow.first()

    /** Add a channel */
    suspend fun addChannel(channel: YouTubeChannel) {
        store.edit { prefs ->
            val current = prefs[KEY_CHANNELS] ?: "[]"
            val currentList = runCatching { json.decodeFromString<List<YouTubeChannel>>(current) }.getOrDefault(emptyList())
            prefs[KEY_CHANNELS] = json.encodeToString(currentList + channel)
        }
    }

    /** Update a channel */
    suspend fun updateChannel(channel: YouTubeChannel) {
        store.edit { prefs ->
            val current = prefs[KEY_CHANNELS] ?: "[]"
            val currentList = runCatching { json.decodeFromString<List<YouTubeChannel>>(current) }.getOrDefault(emptyList())
            val updated = currentList.map { if (it.id == channel.id) channel else it }
            prefs[KEY_CHANNELS] = json.encodeToString(updated)
        }
    }

    /** Delete a channel by ID */
    suspend fun deleteChannel(channelId: String) {
        store.edit { prefs ->
            val current = prefs[KEY_CHANNELS] ?: "[]"
            val currentList = runCatching { json.decodeFromString<List<YouTubeChannel>>(current) }.getOrDefault(emptyList())
            prefs[KEY_CHANNELS] = json.encodeToString(currentList.filter { it.id != channelId })
        }
    }

    companion object {
        @Volatile private var INSTANCE: YouTubeChannelStore? = null

        fun getInstance(context: Context): YouTubeChannelStore {
            return INSTANCE ?: synchronized(this) {
                val instance = YouTubeChannelStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

fun CoroutineScope.launchYouTubeChannelStore(context: Context, action: suspend YouTubeChannelStore.() -> Unit) {
    launch(Dispatchers.IO) {
        YouTubeChannelStore.getInstance(context.applicationContext).action()
    }
}
