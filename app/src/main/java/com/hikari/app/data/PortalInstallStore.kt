package com.hikari.app.data

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tracks which portal keys (url|username) have been installed via the portalnutz scraper UI.
 * Used to show "Install" chips for uninstalled portals and "ADDED" badges for installed ones.
 *
 * Storage is a JSON file in the app's cache directory — not persisted across app updates
 * (same lifetime as the channel cache), but survives process death and app close.
 */
@Serializable
private data class InstalledPortalsPayload(val keys: Set<String> = emptySet())

object PortalInstallStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val CACHE_FILE = "installed_portals.json"

    private val _installed = MutableStateFlow<Set<String>>(emptySet())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    private var cachedCacheDir: File? = null

    fun init(cacheDir: File) {
        cachedCacheDir = cacheDir
        val raw = try { cacheDir.resolve(CACHE_FILE).takeIf { it.exists() }?.readText() } catch (_: Exception) { null }
        val keys = if (!raw.isNullOrBlank()) {
            try { json.decodeFromString<InstalledPortalsPayload>(raw).keys } catch (_: Exception) { emptySet() }
        } else emptySet()
        _installed.value = keys
    }

    private fun persist() {
        val dir = cachedCacheDir ?: runCatching {
            (Class.forName("android.app.AppGlobals").getMethod("getInitialApplication").invoke(null) as Application).cacheDir
        }.getOrNull() ?: return
        scope.launch {
            try {
                dir.resolve(CACHE_FILE).writeText(json.encodeToString(InstalledPortalsPayload(_installed.value)))
            } catch (_: Exception) {}
        }
    }

    /** Key form: `url|username` */
    fun keyOf(url: String, username: String): String = "$url|$username"

    fun isInstalled(url: String, username: String): Boolean =
        keyOf(url, username) in _installed.value

    fun markInstalled(url: String, username: String) {
        val k = keyOf(url, username)
        if (k in _installed.value) return
        _installed.value = _installed.value + k
        persist()
    }

    fun markUninstalled(url: String, username: String) {
        val k = keyOf(url, username)
        if (k !in _installed.value) return
        _installed.value = _installed.value - k
        persist()
    }

    fun clear() {
        _installed.value = emptySet()
        persist()
    }
}
