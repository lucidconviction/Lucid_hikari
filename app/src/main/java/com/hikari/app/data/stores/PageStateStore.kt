package com.hikari.app.data.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * UI state for a single screen/page — scroll position, search query, etc.
 * Serialized to JSON and stored in DataStore so the user's place in each
 * screen is restored across restarts.
 */
@Serializable
data class PageState(
    @SerialName("scrollOffset") val scrollOffset: Int = 0,
    @SerialName("searchQuery") val searchQuery: String = "",
    @SerialName("selectedTabIndex") val selectedTabIndex: Int = 0,
)

private val Context.pageStateDataStore by preferencesDataStore(name = "page_states")

/**
 * DataStore-backed store for page/screen UI state persistence.
 * Saves scroll position, search query, selected tabs, etc. per screen.
 */
class PageStateStore(private val ctx: Context) {

    private val store: DataStore<Preferences> get() = ctx.pageStateDataStore

    private val KEY_PAGE_STATES = stringPreferencesKey("page_states_json")

    private val json = Json { ignoreUnknownKeys = true }

    /** Get saved state for a screen */
    suspend fun getState(screenKey: String): PageState? = store.data
        .map { prefs ->
            val jsonStr = prefs[KEY_PAGE_STATES] ?: "{}"
            runCatching { json.decodeFromString<Map<String, PageState>>(jsonStr) }
                .getOrDefault(emptyMap())[screenKey]
        }
        .first()

    /** Save state for a screen */
    suspend fun saveState(screenKey: String, state: PageState) {
        store.edit { prefs ->
            val currentMap = readPageStates(prefs) + (screenKey to state)
            prefs[KEY_PAGE_STATES] = Json.encodeToString(currentMap)
        }
    }

    /** Clear state for a screen */
    suspend fun clearState(screenKey: String) {
        store.edit { prefs ->
            val currentMap = readPageStates(prefs) - screenKey
            prefs[KEY_PAGE_STATES] = Json.encodeToString(currentMap)
        }
    }

    private fun readPageStates(prefs: Preferences): Map<String, PageState> {
        val jsonStr = prefs[KEY_PAGE_STATES] ?: "{}"
        return runCatching { json.decodeFromString<Map<String, PageState>>(jsonStr) }
            .getOrDefault(emptyMap())
    }

    companion object {
        @Volatile private var INSTANCE: PageStateStore? = null

        fun getInstance(context: Context): PageStateStore {
            return INSTANCE ?: synchronized(this) {
                val instance = PageStateStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

fun CoroutineScope.launchPageStateStore(context: Context, action: suspend PageStateStore.() -> Unit) {
    launch(Dispatchers.IO) {
        PageStateStore.getInstance(context.applicationContext).action()
    }
}
