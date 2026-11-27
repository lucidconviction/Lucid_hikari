package com.hikari.app.data

import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import kotlin.concurrent.Volatile

/**
 * Lightweight stream validation store — port of Nuvio's StreamValidationStore
 * adapted to Lucid_hikari's simpler data model (no sourceId, no IptvRepository).
 *
 * Tracks whether each known URL is alive/dead via a file-persisted map.
 * Called from MultiViewScreen's QuickChannelOverlay to show a green dot
 * for working streams and dim dead ones.
 */
object StreamValidationStore {
    private const val FILE_NAME = "stream_validation_cache.json"
    private const val DEAD_COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes
    private const val TTL_MS = 5 * 60 * 1000L           // 5 minutes

    data class Record(val url: String, val alive: Boolean, val at: Long)

    @Volatile
    private var _cache: Map<String, Record> = emptyMap()

    val snapshot: Map<String, Boolean> get() = _cache.mapValues { it.value.alive }

    private var _lock = Any()

    fun ensureLoaded(file: File) {
        synchronized(_lock) {
            if (_cache.isNotEmpty()) return
            val f = File(file.parentFile, FILE_NAME)
            if (!f.exists()) return
            val raw = try { f.readText() } catch (_: Exception) { return }
            if (raw.isBlank()) return
            try {
                val list = com.google.gson.Gson().fromJson(raw, Array<Record>::class.java)?.toList() ?: return
                _cache = list.associate { it.url to it }.toMap()
            } catch (_: Exception) {}
        }
    }

    fun save(file: File) {
        synchronized(_lock) {
            if (_cache.isEmpty()) return
            val raw = com.google.gson.Gson().toJson(_cache.values.toList())
            runCatching { File(file.parentFile, FILE_NAME).writeText(raw) }
        }
    }

    fun isKnownDeadSync(url: String): Boolean {
        return _cache[url]?.let { !it.alive } ?: false
    }

    fun isKnownAliveSync(url: String): Boolean {
        val r = _cache[url] ?: return false
        return r.alive && (System.currentTimeMillis() - r.at) <= TTL_MS
    }

    fun needsRecheck(url: String): Boolean {
        val r = _cache[url] ?: return true
        val age = System.currentTimeMillis() - r.at
        return age >= if (r.alive) TTL_MS else DEAD_COOLDOWN_MS
    }

    fun remember(results: Map<String, Boolean>) {
        synchronized(_lock) {
            val now = System.currentTimeMillis()
            results.forEach { (url, alive) ->
                _cache = _cache.toMutableMap().also { it[url] = Record(url, alive, now) }
            }
        }
    }

    fun getAliveMap(): Map<String, Boolean> {
        synchronized(_lock) { return _cache.mapValues { it.value.alive }.toMap() }
    }

    fun getDeadUrls(): Set<String> = _cache.values.filter { !it.alive }.map { it.url }.toSet()

    fun markDead(urls: Set<String>) {
        remember(urls.associateWith { false })
    }

    fun markAlive(url: String) {
        remember(mapOf(url to true))
    }

    fun clearAll(file: File) {
        synchronized(_lock) {
            _cache = emptyMap()
            runCatching { File(file.parentFile, FILE_NAME).delete() }
        }
    }
}
