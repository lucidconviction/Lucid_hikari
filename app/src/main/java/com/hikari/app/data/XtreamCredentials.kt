package com.hikari.app.data

/** F3 — Xtream panel credentials for IPTV providers. */
data class XtreamCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    /** True when [baseUrl] looks like an Xtream panel URL. */
    fun isXtream(): Boolean = baseUrl.contains("get.php") || baseUrl.contains("player_api.php")

    /** Build the m3u_plus URL for the panel. */
    fun m3uUrl(): String = "$baseUrl/get.php?username=$username&password=$password&type=m3u_plus&output=ts"

    companion object {
        fun fromUrl(url: String): XtreamCredentials? {
            val u = url.trim()
            if (!u.startsWith("http")) return null
            val uri = try { java.net.URI(u) } catch (_: Exception) { return null }
            val username = uri.query?.takeIf { it.contains("username=") }
                ?.substringAfter("username=").substringBefore("&").ifBlank { null }
            val password = uri.query?.takeIf { it.contains("password=") }
                ?.substringAfter("password=").substringBefore("&").ifBlank { null }
            if (username == null || password == null) return null
            return XtreamCredentials(u.substringBefore("?"), username, password)
        }
    }
}
