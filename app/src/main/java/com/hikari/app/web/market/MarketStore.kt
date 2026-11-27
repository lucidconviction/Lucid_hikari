package com.hikari.app.web.market

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The market's disk layer: a small JSON cache plus the SFW seed shipped in the
 * APK. The cache is written to a temp file and renamed, so a killed process
 * cannot leave a half-written catalogue that the next open would read.
 *
 * Adult entries are only ever added by a runtime refresh; the seed is SFW-only
 * on purpose, so the repository does not ship a porn directory.
 */
object MarketStore {

    private const val DIR = "webapp_market"
    private const val CACHE = "catalog.json"
    private const val SEED = "webapp_market_seed.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun load(context: Context): MarketCatalog = withContext(Dispatchers.IO) {
        readCache(context) ?: readSeed(context) ?: MarketCatalog()
    }

    suspend fun save(context: Context, catalog: MarketCatalog) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val temp = File(dir, "$CACHE.tmp")
            temp.writeText(json.encodeToString(MarketCatalog.serializer(), catalog))
            val target = File(dir, CACHE)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
    }

    private fun readCache(context: Context): MarketCatalog? = runCatching {
        val file = File(File(context.filesDir, DIR), CACHE)
        if (!file.isFile || file.length() == 0L) return@runCatching null
        val parsed = json.decodeFromString(MarketCatalog.serializer(), file.readText())
        if (parsed.schema != MarketCatalog.SCHEMA || parsed.apps.isEmpty()) return@runCatching null
        parsed
    }.getOrNull()

    private fun readSeed(context: Context): MarketCatalog? = runCatching {
        val text = context.assets.open(SEED).bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString(MarketCatalog.serializer(), text)
        if (parsed.schema != MarketCatalog.SCHEMA) return@runCatching null
        parsed
    }.getOrNull()
}
