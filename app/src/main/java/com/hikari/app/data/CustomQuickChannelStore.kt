package com.hikari.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists custom quick channels to a JSON file in the app's cache directory.
 */
object CustomQuickChannelStore {
    private const val FILE = "quick_custom_channels.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(cacheDir: File): File = File(cacheDir, FILE)

    fun loadChannels(cacheDir: File): List<QuickChannel> {
        val f = file(cacheDir)
        if (!f.exists()) return emptyList()
        val raw = try { f.readText() } catch (_: Exception) { return emptyList() }
        if (raw.isBlank()) return emptyList()
        return try { json.decodeFromString<List<QuickChannel>>(raw) } catch (_: Exception) { emptyList() }
    }

    fun saveChannels(cacheDir: File, channels: List<QuickChannel>) {
        runCatching { file(cacheDir).writeText(json.encodeToString(channels)) }
    }

    fun addChannel(cacheDir: File, channel: QuickChannel) {
        val updated = loadChannels(cacheDir).toMutableList()
        updated.removeAll { it.displayName.equals(channel.displayName, ignoreCase = true) }
        updated.add(channel)
        saveChannels(cacheDir, updated)
    }

    fun removeChannel(cacheDir: File, displayName: String) {
        val updated = loadChannels(cacheDir).filterNot { it.displayName.equals(displayName, ignoreCase = true) }
        saveChannels(cacheDir, updated)
    }
}
