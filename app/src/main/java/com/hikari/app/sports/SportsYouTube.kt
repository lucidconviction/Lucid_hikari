package com.hikari.app.sports

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.hikari.app.HikariApp
import com.hikari.app.cs3.YouTubePlayables
import com.hikari.app.player.PlayerActivity
import com.hikari.app.youtube.YouTubeProgress
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * YouTube videos for a sports game page — the promotional clips before an
 * event (weigh-ins, pressers, previews) and the post-event package (highlights,
 * interviews, analysis).
 *
 * Deliberately uses the SAME SmartTube/MediaServiceCore engine the YouTube tab
 * uses, so a sports clip starts at HD, gets the full adaptive manifest and
 * carries the right User-Agent — the same engine hand-off as a trailer from a
 * detail page. Discovery is a plain engine search scoped by the event's own
 * title; no feed state, no watch progress, no "More Videos" queue is involved
 * (the player intent does not set `ytPlayback`), so sports clips stay separate
 * from what the YouTube tab is doing.
 */
object SportsYouTube {

    data class Clip(
        val videoId: String,
        val title: String,
        val author: String?,
        val thumbnail: String?,
    )

    private const val MAX_CLIPS = 12

    /** True for fight cards, where the pre-event material is weigh-ins/pressers. */
    private fun isFighting(event: SportsEvent): Boolean =
        SportsCatalog.league(event.leagueId)?.sport == Sport.FIGHTING ||
            listOf("ufc", "mma", "bkfc", "boxing", "pfl").any {
                event.leagueId.contains(it, ignoreCase = true)
            }

    private fun queriesFor(event: SportsEvent, postEvent: Boolean): List<String> {
        val title = event.title.takeIf { it.isNotBlank() } ?: return emptyList()
        return if (postEvent) {
            listOf("$title highlights", "$title interview")
        } else if (isFighting(event)) {
            listOf("$title weigh in", "$title press conference")
        } else {
            listOf("$title preview", "$title analysis")
        }
    }

    /**
     * Whether a search result is about THIS fixture. A team event needs one
     * of its teams named (never just the city — "Dallas Stars" must not accept
     * a Dallas Cowboys video); an event without sides (a fight card) needs two
     * distinctive words of its title.
     */
    private fun isRelevant(title: String, event: SportsEvent): Boolean {
        val text = SportsCatalog.norm(title)
        if (text.isBlank()) return false
        val teams = listOfNotNull(event.away, event.home)
            .flatMap { listOf(it.team.name, it.team.fullName) }
            .map { SportsCatalog.norm(it) }
            .filter { it.length >= 3 }
            .distinct()
        if (teams.isNotEmpty()) return teams.any { text.contains(it) }
        val tokens = SportsCatalog.norm(event.title)
            .split(' ')
            .filter { it.length >= 4 }
            .distinct()
        return tokens.isNotEmpty() && tokens.count { text.contains(it) } >= minOf(2, tokens.size)
    }

    /**
     * Searches the YouTube engine for [event]'s clips. Post-event events get
     * highlight/interview material; everything else gets the build-up.
     * Blocking network work — always call from a coroutine.
     */
    suspend fun clipsFor(event: SportsEvent, postEvent: Boolean): List<Clip> =
        withContext(Dispatchers.IO) {
            val queries = queriesFor(event, postEvent)
            if (queries.isEmpty()) return@withContext emptyList()
            val context = runCatching { HikariApp.instance }.getOrNull()
                ?: return@withContext emptyList()
            val service = runCatching {
                GlobalPreferences.instance(context)
                com.hikari.app.youtube.YouTubeContentRegion.apply(context)
                YouTubeServiceManager.instance()
            }.getOrNull() ?: return@withContext emptyList()

            val found = LinkedHashMap<String, Clip>()
            for (query in queries) {
                if (found.size >= MAX_CLIPS) break
                val groups = runCatching {
                    service.contentService.getSearchObserve(query).blockingFirst()
                }.getOrNull().orEmpty()
                for (group in groups) {
                    for (item in group.mediaItems.orEmpty()) {
                        if (found.size >= MAX_CLIPS) break
                        if (item.type == MediaItem.TYPE_CHANNEL ||
                            item.type == MediaItem.TYPE_PLAYLIST
                        ) continue
                        val id = item.videoId?.takeIf { it.isNotBlank() } ?: continue
                        // YouTube happily answers "Dallas Stars highlights"
                        // with a Dallas COWBOYS clip. Keep only results that
                        // name one of this event's teams (or, for a fight
                        // card, two distinctive words of its title).
                        if (!isRelevant(item.title.orEmpty(), event)) continue
                        found.putIfAbsent(
                            id,
                            Clip(
                                videoId = id,
                                title = item.title.orEmpty().ifBlank { "YouTube video" },
                                author = item.author,
                                thumbnail = item.cardImageUrl,
                            ),
                        )
                    }
                }
            }
            found.values.toList()
        }

    /**
     * Resolves [clip] through the YouTube tab's engine and opens the player.
     * Returns an error message when nothing playable came back (the caller
     * shows it), or null on success.
     */
    suspend fun play(context: Context, clip: Clip): String? {
        val sources = runCatching { YouTubePlayables.resolve(clip.videoId) }
            .getOrDefault(emptyList())
            .filter { it.url.isNotBlank() }
        if (sources.isEmpty()) return "Couldn't play this YouTube video."
        val payload = runCatching {
            JSONArray().apply {
                sources.forEach { source ->
                    put(
                        JSONObject()
                            .put("name", source.name)
                            .put("url", source.url)
                            .put("headers", JSONObject(source.headers))
                            .put("isM3u8", source.isM3u8)
                            .put("isMpd", source.isMpd)
                            .put("isTorrent", false)
                            .put("provider", source.provider)
                            .put("providerId", source.providerId)
                            .put("providerName", source.providerName)
                            .put("details", source.details),
                    )
                }
            }.toString()
        }.getOrNull() ?: return "Couldn't prepare this YouTube video."

        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("title", clip.title)
            putExtra("sources", payload)
            putExtra("askServer", false)
            putExtra("histProviderId", "youtube")
            putExtra("histMediaId", YouTubeProgress.ID_PREFIX + clip.videoId)
            putExtra("histType", "movie")
            putExtra("histTitle", clip.title)
            putExtra("histPoster", clip.thumbnail.orEmpty())
            // No `ytPlayback`: a sports clip should not inherit the YouTube
            // feed's More Videos queue or its resume state.
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.fold(
            onSuccess = { null },
            onFailure = {
                Toast.makeText(context, "Couldn't open the player", Toast.LENGTH_SHORT).show()
                "Couldn't open the player."
            },
        )
    }
}
