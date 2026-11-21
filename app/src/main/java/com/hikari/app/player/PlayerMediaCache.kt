package com.hikari.app.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Disk cache for stream bytes.
 *
 * Memory buffering only covers the next few seconds; once the player has to
 * re-open a segment, seek, replay an episode or recover from a dropped socket,
 * the bytes were downloaded again from the origin. A 512 MB LRU cache keeps the
 * recent segments on disk instead, which is what removes the repeat "buffer,
 * then start over" behaviour on a flaky IPTV/aggregator host.
 *
 * The cache lives under `cacheDir`, so Android may reclaim it under storage
 * pressure. A failure to open the cache is never fatal: callers fall back to
 * the direct upstream factory and playback continues exactly as before.
 */
object PlayerMediaCache {

    private const val MAX_BYTES = 512L * 1024L * 1024L
    private const val DIR = "player_media"

    @Volatile private var cache: SimpleCache? = null
    @Volatile private var failed = false

    private fun cache(context: Context): SimpleCache? {
        cache?.let { return it }
        if (failed) return null
        synchronized(this) {
            cache?.let { return it }
            if (failed) return null
            return runCatching {
                val dir = File(context.cacheDir, DIR)
                SimpleCache(dir, LeastRecentlyUsedCacheEvictor(MAX_BYTES))
            }.onSuccess { cache = it }
                .onFailure {
                    failed = true
                    com.hikari.app.data.Logs.log(
                        "Player",
                        "media cache unavailable — using direct upstream: ${it.message}",
                    )
                }
                .getOrNull()
        }
    }

    /** Wraps [upstream] with the shared disk cache. Falls back to [upstream]. */
    fun wrap(context: Context, upstream: DataSource.Factory): DataSource.Factory {
        val c = cache(context) ?: return upstream
        return CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(upstream)
            // A corrupt/partially written span must never stop playback: read
            // through to the origin and keep the session alive.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
