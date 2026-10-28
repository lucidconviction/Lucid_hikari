package com.hikari.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.hikari.app.HikariApp

private val Context.hubDataStore by preferencesDataStore("hikari_hub")
private val HUB_CATALOG_KEY = stringPreferencesKey("vid_nutz_hub_catalog")

private val Context.searchDataStore by preferencesDataStore("hikari_search")
private val RECENT_SEARCHES_KEY = stringPreferencesKey("vid_nutz_recent_searches")
private val SAVED_SEARCHES_KEY = stringPreferencesKey("vid_nutz_saved_searches")

object VidNutzCacheStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val ctx: Context get() = HikariApp.instance.applicationContext

    suspend fun saveCache(cache: VidNutzCache) {
        withContext(Dispatchers.IO) {
            try {
                ctx.hubDataStore.edit { prefs ->
                    prefs[HUB_CATALOG_KEY] = json.encodeToString(cache)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun loadCache(): VidNutzCache? {
        return withContext(Dispatchers.IO) {
            try {
                val data = ctx.hubDataStore.data.first()
                val str = data[HUB_CATALOG_KEY] ?: return@withContext null
                json.decodeFromString<VidNutzCache>(str)
            } catch (_: Exception) {
                null
            }
        }
    }
}

object VidNutzRecentSearchesStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val ctx: Context get() = HikariApp.instance.applicationContext

    suspend fun load(): List<String> {
        return try {
            val data = ctx.searchDataStore.data.first()
            val str = data[RECENT_SEARCHES_KEY] ?: return emptyList()
            json.decodeFromString<List<String>>(str)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(searches: List<String>) {
        try {
            ctx.searchDataStore.edit { prefs ->
                prefs[RECENT_SEARCHES_KEY] = json.encodeToString(searches)
            }
        } catch (_: Exception) {}
    }
}

object VidNutzSavedSearchesStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val ctx: Context get() = HikariApp.instance.applicationContext

    suspend fun load(): List<String> {
        return try {
            val data = ctx.searchDataStore.data.first()
            val str = data[SAVED_SEARCHES_KEY] ?: return emptyList()
            json.decodeFromString<List<String>>(str)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(searches: List<String>) {
        try {
            ctx.searchDataStore.edit { prefs ->
                prefs[SAVED_SEARCHES_KEY] = json.encodeToString(searches)
            }
        } catch (_: Exception) {}
    }
}
