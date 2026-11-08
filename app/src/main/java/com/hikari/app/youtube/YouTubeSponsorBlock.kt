package com.hikari.app.youtube

import com.hikari.app.net.Http
import org.json.JSONArray
import java.net.URLEncoder

/**
 * SponsorBlock: the parts of a video the community has marked (sponsor reads,
 * intros, outros…), fetched once per video and skipped by the player.
 */
object YouTubeSponsorBlock {

    data class Segment(val startMs: Long, val endMs: Long, val category: String)

    /** The segments for [videoId] in the selected [categories]; empty on any failure. */
    fun fetch(videoId: String, categories: Set<String>): List<Segment> {
        if (videoId.isBlank() || categories.isEmpty()) return emptyList()
        return runCatching {
            val cats = URLEncoder.encode(JSONArray(categories.toList()).toString(), "UTF-8")
            val raw = Http.getStringQuiet(
                "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$cats",
            ) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val seg = o.optJSONArray("segment") ?: return@mapNotNull null
                val start = (seg.optDouble(0, -1.0) * 1000).toLong()
                val end = (seg.optDouble(1, -1.0) * 1000).toLong()
                if (start < 0 || end <= start) return@mapNotNull null
                Segment(start, end, o.optString("category"))
            }
        }.getOrDefault(emptyList())
    }
}
