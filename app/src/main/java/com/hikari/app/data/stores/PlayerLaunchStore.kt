package com.hikari.app.data.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A pending player launch — what to play, passed across screens via the store.
 */
@Serializable
data class PlayerLaunch(
    @SerialName("providerId") val providerId: String = "",
    @SerialName("mediaId") val mediaId: String = "",
    @SerialName("episodeId") val episodeId: String = "",
    @SerialName("title") val title: String = "",
)

private val Context.playerLaunchDataStore by preferencesDataStore(name = "player_launch")

/**
 * DataStore-backed store for coordinating player launches across screens.
 * Used to launch the player from any screen (IPTV, VOD, History, etc.)
 * with the correct stream/source data.
 */
class PlayerLaunchStore(private val ctx: Context) {

    private val store: DataStore<Preferences> get() = ctx.playerLaunchDataStore

    private val KEY_PENDING_LAUNCH = stringPreferencesKey("pending_launch_json")

    private val json = Json { ignoreUnknownKeys = true }

    /** Flow of pending player launch (null if none) */
    val pendingLaunchFlow: Flow<PlayerLaunch?> = store.data
        .map { prefs ->
            val jsonStr = prefs[KEY_PENDING_LAUNCH]
            if (jsonStr.isNullOrBlank()) null
            else runCatching { json.decodeFromString<PlayerLaunch>(jsonStr) }.getOrNull()
        }
        .distinctUntilChanged()

    /** Get current pending launch (if any) */
    suspend fun getPendingLaunch(): PlayerLaunch? = pendingLaunchFlow.first()

    /** Queue a player launch */
    suspend fun setPendingLaunch(launch: PlayerLaunch) {
        store.edit { prefs ->
            prefs[KEY_PENDING_LAUNCH] = json.encodeToString(launch)
        }
    }

    /** Clear pending launch */
    suspend fun clearPendingLaunch() {
        store.edit { prefs ->
            prefs[KEY_PENDING_LAUNCH] = ""
        }
    }

    companion object {
        @Volatile private var INSTANCE: PlayerLaunchStore? = null

        fun getInstance(context: Context): PlayerLaunchStore {
            return INSTANCE ?: synchronized(this) {
                val instance = PlayerLaunchStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

fun CoroutineScope.launchPlayerLaunchStore(context: Context, action: suspend PlayerLaunchStore.() -> Unit) {
    launch(Dispatchers.IO) {
        PlayerLaunchStore.getInstance(context.applicationContext).action()
    }
}
