package com.hikari.app.music

import com.hikari.app.HikariApp
import com.hikari.app.cs3.YouTubePlayables
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Turns a Deezer track into something ExoPlayer can play:
 *  1. Qobuz FLAC/Hi-Res (when the user picked lossless),
 *  2. the YouTube tab's own engine (a search by title + artist, then the
 *     adaptive manifest the engine already knows how to resolve),
 *  3. Deezer's 30-second preview as the last resort.
 *
 * The YouTube path deliberately reuses [YouTubePlayables.resolve], so music
 * playback shares the same engine (and the same working stream formats) as
 * the YouTube tab instead of a second extractor.
 */
object MusicSourceResolver {

    private val ytVideoIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun resolve(track: MusicTrack, preferLossless: Boolean): MusicStream? = coroutineScope {
        // Start both engines together. A dead Qobuz worker must not make every
        // tap wait half a minute before the perfectly good YouTube fallback.
        val lossless = async(Dispatchers.IO) {
            runCatching { QobuzResolver.resolve(track) }.getOrNull()
        }
        val youtubeJob = async(Dispatchers.IO) {
            runCatching { youtube(track) }.getOrNull()
        }

        val firstChoice: MusicStream? = if (preferLossless) {
            withTimeoutOrNull(7_000L) { lossless.await() }
                ?: withTimeoutOrNull(12_000L) { youtubeJob.await() }
                ?: withTimeoutOrNull(4_000L) { lossless.await() }
        } else {
            withTimeoutOrNull(10_000L) { youtubeJob.await() }
                ?: withTimeoutOrNull(7_000L) { lossless.await() }
        }
        if (firstChoice != null) {
            if (firstChoice.lossless) youtubeJob.cancel()
            else lossless.cancel()
            return@coroutineScope firstChoice
        }
        val preview = track.previewUrl
        if (!preview.isNullOrBlank()) {
            MusicStream(
                url = preview,
                headers = mapOf("User-Agent" to "Mozilla/5.0"),
                label = "Preview · 30s",
                lossless = false,
            )
        } else null
    }

    /** The YouTube video id chosen for [track], when one was found. */
    fun youtubeIdFor(trackId: String): String? = ytVideoIds[trackId]

    /**
     * A direct file URL for offline saving. Qobuz is already a file; YouTube
     * only offers one through the muxed MP4 formats (its adaptive audio is
     * segmented DASH, not a single file). Falls back to Deezer's preview.
     */
    suspend fun resolveForDownload(track: MusicTrack): MusicStream? = withContext(Dispatchers.IO) {
        runCatching { QobuzResolver.resolve(track) }.getOrNull()
            ?.takeIf { !it.isDash && !it.isHls }
            ?.let { return@withContext it }
        runCatching { youtubeDirect(track) }.getOrNull()?.let { return@withContext it }
        track.previewUrl?.takeIf { it.isNotBlank() }?.let {
            return@withContext MusicStream(
                url = it,
                headers = mapOf("User-Agent" to "Mozilla/5.0"),
                label = "Preview · 30s",
            )
        }
        null
    }

    private suspend fun youtubeDirect(track: MusicTrack): MusicStream? = withContext(Dispatchers.IO) {
        val context = runCatching { HikariApp.instance }.getOrNull() ?: return@withContext null
        val service = runCatching {
            GlobalPreferences.instance(context)
            com.hikari.app.youtube.YouTubeContentRegion.apply(context)
            YouTubeServiceManager.instance()
        }.getOrNull() ?: return@withContext null
        val videoId = ytVideoIds[track.id] ?: run {
            val groups = runCatching {
                service.contentService.getSearchObserve("${track.title} ${track.artist}".trim()).blockingFirst()
            }.getOrNull().orEmpty()
            groups.asSequence()
                .flatMap { it.mediaItems.orEmpty().asSequence() }
                .firstOrNull {
                    !it.videoId.isNullOrBlank() && !it.isLive &&
                        it.type != MediaItem.TYPE_CHANNEL && it.type != MediaItem.TYPE_PLAYLIST
                }
                ?.videoId
        } ?: return@withContext null
        ytVideoIds[track.id] = videoId
        val info = runCatching {
            service.mediaItemService.getFormatInfoObserve(videoId).blockingFirst()
        }.getOrNull() ?: return@withContext null
        val ua = info.clientInfo?.userAgent?.takeIf { it.isNotBlank() }
        val direct = info.urlFormats.orEmpty()
            .filter { it.url?.isNotBlank() == true && it.mimeType?.startsWith("video/mp4") == true }
            .maxByOrNull { it.bitrate?.toLongOrNull() ?: 0L }
            ?: return@withContext null
        MusicStream(
            url = direct.url,
            headers = if (ua != null) mapOf("User-Agent" to ua) else emptyMap(),
            label = "YouTube download",
        )
    }

    private suspend fun youtube(track: MusicTrack): MusicStream? = withContext(Dispatchers.IO) {
        val context = runCatching { HikariApp.instance }.getOrNull() ?: return@withContext null
        val service = runCatching {
            GlobalPreferences.instance(context)
            com.hikari.app.youtube.YouTubeContentRegion.apply(context)
            YouTubeServiceManager.instance()
        }.getOrNull() ?: return@withContext null

        val query = "${track.title} ${track.artist}".trim()
        val groups = runCatching {
            service.contentService.getSearchObserve(query).blockingFirst()
        }.getOrNull().orEmpty()

        val candidates = ArrayList<MediaItem>()
        for (group in groups) {
            for (item in group.mediaItems.orEmpty()) {
                if (item.type == MediaItem.TYPE_CHANNEL || item.type == MediaItem.TYPE_PLAYLIST) continue
                if (item.videoId.isNullOrBlank() || item.isLive || item.isUpcoming) continue
                candidates += item
            }
        }
        if (candidates.isEmpty()) return@withContext null

        // Prefer a result whose length is close to the track's (music videos
        // and lyric uploads usually are); break ties toward TYPE_MUSIC.
        val target = track.durationSeconds * 1000L
        val best = candidates.maxByOrNull { item ->
            var score = 0.0
            if (item.type == MediaItem.TYPE_MUSIC) score += 1000
            if (target > 0 && item.durationMs > 0) {
                val diff = abs(item.durationMs - target)
                if (diff <= 3000) score += 500
                else if (diff <= 10_000) score += 250
                else if (diff <= 30_000) score += 100
            }
            score
        } ?: return@withContext null

        val videoId = best.videoId ?: return@withContext null
        ytVideoIds[track.id] = videoId
        val sources = runCatching { YouTubePlayables.resolve(videoId) }
            .getOrDefault(emptyList())
            .filter { it.url.isNotBlank() }
        val source = sources.firstOrNull() ?: return@withContext null
        MusicStream(
            url = source.url,
            headers = source.headers,
            label = when {
                source.isMpd -> "YouTube HD"
                source.isM3u8 -> "YouTube Live"
                else -> "YouTube HQ"
            },
            lossless = false,
            isDash = source.isMpd,
            isHls = source.isM3u8,
        )
    }
}
