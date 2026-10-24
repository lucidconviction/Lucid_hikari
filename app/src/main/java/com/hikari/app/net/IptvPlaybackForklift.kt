package com.hikari.app.net

/** Helpers that decide whether an incoming playback URL looks like an IPTV
 *  channel, and which User-Agent to hand it. The goal is narrow: only when
 *  the source did NOT declare its own UA (i.e. it fell through to our default
 *  browser UA) do we swap in a VLC fingerprint, because many live-channel CDNs
 *  and signed HLS endpoints gate their manifests on a media-player UA and
 *  return 403 to a plain desktop Chrome string. */
object IptvPlaybackForklift {

    /** Heuristic: a URL is treated as an IPTV channel when it carries one of
     *  the markers that channel lists and live gateways use in practice
     *  (.m3u8 / .ts / .mpd manifests, or a path/query segment that screams
     *  "live channel" rather than VOD). */
    fun shouldTryIptvUa(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".m3u8") ||
            lower.endsWith(".ts") ||
            lower.endsWith(".mpd") ||
            lower.contains("/live/") ||
            lower.contains("?list=") ||
            lower.contains("channel=")
    }

    /** Pick the UA for a request: if the extractor already declared one, keep
     *  it; otherwise, for URLs that look like IPTV channels, use the VLC-style
     *  UA from [Http.IPTV_UA] rather than the app's desktop Chrome UA — that is
     *  the only case where the fingerprint swap is wanted. */
    fun pickUa(declared: String?, url: String): String {
        if (!shouldTryIptvUa(url)) return declared ?: Http.UA
        // Only switch when the declared UA is absent (or blank); a real
        // extractor-provided UA is preserved so signed-CDN parity is not
        // broken by an unconditional swap.
        return declared?.takeIf { it.isNotBlank() } ?: Http.IPTV_UA
    }
}
