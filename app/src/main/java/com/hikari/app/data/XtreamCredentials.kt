package com.hikari.app.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** F3 — Xtream panel credentials for IPTV providers. */
data class XtreamCredentials(
  val baseUrl: String,
  val username: String,
  val password: String,
) {
  /**
   * The panel ROOT this credential points at: `scheme://host[:port][/path]`,
   * never a script name. [m3uUrl], [apiInfoUrl] and [apiUrl] append the scripts
   * to it, so a saved `get.php?…` link round-trips through [fromUrl] unchanged:
   * the same URL the user added is the one re-fetched on refresh (no
   * `…/get.php/get.php?…` doubling).
   */
  val root: String
    get(): String {
      val b = baseUrl.trimEnd('/')
      return b.substringBeforeLast("/get.php").substringBeforeLast("/player_api.php")
    }

  /** True when this is an Xtream credential (a panel root, not a bare path). */
  fun isXtream(): Boolean =
    baseUrl.startsWith("http://") || baseUrl.startsWith("https://")

  /** The m3u_plus export URL for the panel — credentials URL-encoded once. */
  fun m3uUrl(): String =
    root + "/get.php?username=" + enc(username) +
      "&password=" + enc(password) + "&type=m3u_plus&output=ts"

  /** The panel's server-info endpoint: `player_api.php?username=…&password=…`. */
  fun apiUrl(): String =
    root + "/player_api.php?username=" + enc(username) + "&password=" + enc(password)

  /** Alias kept for callers that spell the auth check as `apiInfoUrl`. */
  fun apiInfoUrl(): String = apiUrl()

  /**
   * Null when the credentials are well-formed, else the problem — shown in the
   * Add dialog BEFORE any network request is made. Enforces the basic rule the
   * UI asks for: a non-empty host that looks like a URL, plus a username.
   */
  fun validate(): String? {
    if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
      return "Fill in the panel URL, username and password."
    }
    val u = baseUrl.trim()
    if (!u.startsWith("http://") && !u.startsWith("https://")) {
      return "The panel URL must start with http:// or https://."
    }
    val uri = try { java.net.URI(u) } catch (_: Exception) {
      return "That is not a valid URL."
    }
    if (uri.host.isNullOrBlank()) return "That URL has no host."
    return null
  }

  private fun enc(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8.name())

  companion object {
    /**
     * Parse Xtream credentials out of a stored panel URL (the full
     * `…/get.php?username=…&password=…&type=m3u_plus` form the app saves).
     * Null when the URL is not an Xtream login link. Username/password are
     * URL-*decoded* so [m3uUrl] re-encodes them exactly once on refresh.
     */
    fun fromUrl(url: String): XtreamCredentials? {
      val u = url.trim()
      if (!u.startsWith("http")) return null
      val uri = try { java.net.URI(u) } catch (_: Exception) { return null }
      val query = uri.query ?: return null
      val username = query.takeIf { it.contains("username=") }
        ?.substringAfter("username=")?.substringBefore("&")?.ifBlank { null }
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
      val password = query.takeIf { it.contains("password=") }
        ?.substringAfter("password=")?.substringBefore("&")?.ifBlank { null }
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
      if (username == null || password == null) return null
      // Strip the script name so the root round-trips through m3uUrl()/apiUrl()
      // without doubling it (…/get.php/get.php?…).
      val root = u.substringBefore("?").trimEnd('/')
        .substringBeforeLast("/get.php").substringBeforeLast("/player_api.php")
      return XtreamCredentials(root, username, password)
    }
  }
}
