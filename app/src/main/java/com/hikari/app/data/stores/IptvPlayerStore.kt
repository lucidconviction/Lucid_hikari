package com.hikari.app.data.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope

private val Context.iptvPlayerDataStore by preferencesDataStore(name = "iptv_player")

/**
 * DataStore-backed store for coordinating IPTV player interactions.
 * Used to trigger "Add Source" dialog from the player screen.
 */
class IptvPlayerStore(private val ctx: Context) {

    private val store: DataStore<Preferences> get() = ctx.iptvPlayerDataStore

    private val KEY_SHOW_ADD_SOURCE = booleanPreferencesKey("show_add_source")

    /** Flow indicating whether to show the add source dialog */
    val showAddSourceFlow: Flow<Boolean> = store.data
        .map { prefs -> prefs[KEY_SHOW_ADD_SOURCE] ?: false }
        .distinctUntilChanged()

    /** Trigger the add source dialog */
    suspend fun showAddSourceDialog() {
        store.edit { prefs ->
            prefs[KEY_SHOW_ADD_SOURCE] = true
        }
    }

    /** Clear the add source trigger */
    suspend fun clearAddSource() {
        store.edit { prefs ->
            prefs[KEY_SHOW_ADD_SOURCE] = false
        }
    }

    companion object {
        @Volatile private var INSTANCE: IptvPlayerStore? = null

        fun getInstance(context: Context): IptvPlayerStore {
            return INSTANCE ?: synchronized(this) {
                val instance = IptvPlayerStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

fun CoroutineScope.launchIptvPlayerStore(context: Context, action: suspend IptvPlayerStore.() -> Unit) {
    launch(Dispatchers.IO) {
        IptvPlayerStore.getInstance(context.applicationContext).action()
    }
}
