package com.hikari.app.data

/**
 * F4 — One window of a multi-view session (persisted in AppStore).
 */
data class MultiViewSession(
    val name: String,
    val url: String,
) {
    companion object {
        fun parse(raw: String?): List<MultiViewSession> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val url = o.optString("url").trim()
                        if (url.isEmpty()) continue
                        add(MultiViewSession(o.optString("name").trim(), url))
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun encode(sessions: List<MultiViewSession>): String {
            val arr = org.json.JSONArray()
            sessions.forEach { s ->
                arr.put(org.json.JSONObject().put("name", s.name).put("url", s.url))
            }
            return arr.toString()
        }
    }
}
