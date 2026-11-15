package com.hikari.app.net

import android.content.Context
import java.io.File

/**
 * Hosts-file ad blocker for the WebView tab (sites opened from the Extensions
 * screen). NEVER applied to the video player — ExoPlayer does its own network
 * I/O and is deliberately untouched by this engine, so blocking a CDN domain
 * can never break playback.
 *
 * Supports any hosts file in the classic format (StevenBlack, AdAway, yoyo):
 *   0.0.0.0 ad.doubleclick.net
 *   127.0.0.1 example.com        # comment
 *   plain-domain.example.com
 *   *.example.com                 # OISD's wildcard spelling
 * plus plain one-domain-per-line lists. It ALSO accepts Adblock-syntax filter
 * lists (EasyList, EasyPrivacy, AdGuard Base, uBlock filters) and extracts the
 * domain-anchored rules (`||ads.example.com^`, including `$third-party` and
 * other options) into the same domain set. That is the practical half of those
 * lists: it catches rotating ad subdomains and whole ad networks without
 * compiling tens of thousands of URL regexes on a phone.
 *
 * Downloads are cached under filesDir/adblock/ as a normalized one-domain-per-
 * line list, so the blocklist works offline and the next page load parses a
 * compact file instead of the multi-megabyte source. "Update lists" in
 * Settings refreshes them; opening the browser also refreshes a list older
 * than a week.
 */
object AdBlocker {

    /** A remote blocklist. The name is display-only; [url] may be a hosts file
     *  or an Adblock filter list — [parseList] detects both. */
    data class HostList(val name: String, val url: String)

    val PRESETS = listOf(
        // Modern, compact, actively maintained. OISD is the one to recommend:
        // it is curated to "block. don't break." and is much smaller than the
        // legacy mega-hosts files.
        HostList("OISD Small", "https://small.oisd.nl/domainswild"),
        HostList("EasyList", "https://easylist.to/easylist/easylist.txt"),
        HostList("EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        HostList("AdGuard Base", "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_2_Base/filter.txt"),
        HostList("StevenBlack", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        HostList("AdAway", "https://adaway.org/hosts.txt"),
    )

    /** Built-in defaults — base protection even with no lists configured. */
    val BUILTIN = listOf(
        // Google / DoubleClick / YouTube monetisation
        "doubleclick.net", "googleadservices.com", "googlesyndication.com", "googletagmanager.com",
        "googletagservices.com", "google-analytics.com", "adservice.google.com", "2mdn.net",
        "imasdk.googleapis.com", "pagead2.googlesyndication.com", "partner.googleadservices.com",
        // Programmatic exchanges / SSPs
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com",
        "smartadserver.com", "sovrn.com", "lijit.com", "yieldmo.com", "media.net", "adform.net",
        "adsafeprotected.com", "moatads.com", "doubleverify.com", "adsrvr.org", "bluekai.com",
        "demdex.net", "krxd.net", "rlcdn.com", "tapad.com", "crwdcntrl.net", "agkn.com",
        "mathtag.com", "exelator.com", "contextweb.com", "sharethrough.com", "teads.tv",
        "spotxchange.com", "tremorhub.com", "3lift.com", "indexww.com", "33across.com",
        "gumgum.com", "districtm.io", "smaato.net", "yieldlab.net", "adsafety.net",
        "adkernel.com", "adhigh.net", "adotmob.com", "smartclip.net", "vidazoo.com",
        "aniview.com", "primis.tech", "unruly.io", "connatix.com", "adpushup.com",
        // Popunders / redirect gates / adult ad networks common on streaming sites
        "popads.net", "popcash.net", "popunder.net", "popunder.ru", "onclickads.net",
        "adsterra.com", "propellerads.com", "propeller-tracking.com", "adcash.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "trafficstars.com",
        "tsyndicate.com", "magsrv.com", "realsrv.com", "adspyglass.com", "hilltopads.net",
        "clickadu.com", "bidvertiser.com", "adxpansion.com",
        // Native / content recommendation widgets
        "taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "zergnet.com",
        "plista.com", "content.ad", "dable.io", "genieejapan.com",
        // Mobile ad SDKs (pages embedding app-install units)
        "adcolony.com", "inmobi.com", "admarvel.com", "flurry.com", "startappservice.com",
        "smartyads.com", "admob.com", "applovin.com", "vungle.com", "chartboost.com",
        "mopub.com", "supersonicads.com", "fyber.com", "unityads.unity3d.com",
        // Analytics that only exist to profile ad clicks
        "quantserve.com", "scorecardresearch.com", "amazon-adsystem.com", "adroll.com",
        "criteo.com", "criteo.net", "snigelweb.com", "ezoic.net", "mediavine.com",
        "trafficjunky.net", "adnium.com", "adnami.io", "adtelligent.com", "loopme.me",
        "smaato.com", "vervegroupinc.com", "yieldlove.com", "stroeer.com", "xandr.com",
    )

    fun cacheDir(context: Context): File =
        File(context.filesDir, "adblock").apply { mkdirs() }

    fun cacheFile(context: Context, url: String): File =
        File(cacheDir(context), url.hashCode().toLong().toString(16) + ".txt")

    /**
     * Parses one list into a set of lowercase domains. [parseList] decides
     * whether the text is a hosts file, an Adblock filter list or both; a normal
     * caller should use that rather than [parseHosts] directly.
     */
    fun parseHosts(text: String): Set<String> {
        val out = HashSet<String>()
        for (line in text.lineSequence()) {
            val clean = line.substringBefore('#').trim()
            if (clean.isEmpty() || clean.startsWith("!") || clean.startsWith("[")) continue
            val parts = clean.split(WHITESPACE)
            val candidate = when {
                parts.size >= 2 && isIp(parts[0]) -> parts[1]
                parts.size == 1 -> parts[0]
                else -> continue
            }
            if (!addDomain(out, candidate)) continue
        }
        return out
    }

    /**
     * Parses either a classic hosts/domain list or an Adblock filter list.
     *
     * Filter lists are reduced to their DOMAIN-ANCHORED rules. That is
     * deliberately conservative: `||ads.example.com^$script,third-party` and
     * `||ads.example.com^` both mean "this host serves ads", while
     * `/ads/banner.js`, cosmetic rules (`##.ad`) and path-only rules stay out
     * — the built-in URL patterns already cover the same-host cases, and
     * turning every EasyList regex into a Kotlin regex on a phone would cost
     * more memory than the video player itself.
     */
    fun parseList(text: String): Set<String> {
        val looksAdblock = text.lineSequence().take(80).any { line ->
            val t = line.trim()
            t.startsWith("[Adblock") || t.startsWith("||")
        }
        if (!looksAdblock) return parseHosts(text)
        val out = HashSet<String>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("!") || t.startsWith("[") ||
                t.startsWith("#") || t.startsWith("@@")
            ) {
                continue
            }
            if (!t.startsWith("||")) {
                // Some lists mix hosts lines into an Adblock file; keep those.
                if (!t.contains("##") && !t.contains("#@#")) {
                    val parts = t.split(WHITESPACE)
                    if (parts.size == 1) addDomain(out, parts[0])
                }
                continue
            }
            var body = t.removePrefix("||")
            val stop = body.indexOfFirst {
                it == '^' || it == '$' || it == '/' || it == '?' || it == '#' || it == '|'
            }
            if (stop >= 0) body = body.substring(0, stop)
            body = body.trim().trimEnd('.').trimStart('.')
            if (body.isEmpty() || '*' in body || ':' in body) continue
            addDomain(out, body)
        }
        // A few hosts-format lines may sit under the Adblock header too.
        out += parseHosts(text)
        return out
    }

    /** Normalizes and adds one host; false when it is not a usable domain. */
    private fun addDomain(out: MutableSet<String>, raw: String): Boolean {
        var host = raw.trim()
            .removePrefix("0.0.0.0").removePrefix("127.0.0.1")
            .trim()
            .removePrefix("*.")
            .removePrefix("||")
            .substringBefore('^').substringBefore('$').substringBefore('/')
            .substringBefore(':')
            .trim().trimEnd('.').lowercase()
        if (host.startsWith(".")) host = host.trimStart('.')
        if (host.isEmpty() || host.length > 253 || !host.contains('.')) return false
        if ('*' in host || ' ' in host || '\t' in host) return false
        if (host.startsWith("localhost") || host.endsWith(".local") ||
            host.endsWith(".localdomain") || host.endsWith(".lan")
        ) {
            return false
        }
        if (host in LOCAL_NAMES) return false
        val tld = host.substringAfterLast('.')
        if (tld.length < 2 || tld.all { it.isDigit() }) return false
        if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return false
        if (".." in host) return false
        return out.add(host)
    }

    private val IPV4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
    private val WHITESPACE = Regex("\\s+")
    private val LOCAL_NAMES = setOf(
        "localhost", "broadcasthost", "ip6-localhost", "ip6-loopback",
        "ip6-allnodes", "ip6-allrouters", "0.0.0.0", "::1", "::", "255.255.255.255",
    )

    private fun isIp(s: String): Boolean =
        s in LOCAL_NAMES || IPV4.matches(s) || (':' in s && s.count { it == ':' } >= 2)

    /**
     * Downloads [url], caches the PARSED domain list and returns the domains.
     *
     * The cache is written as one domain per line rather than the multi-megabyte
     * source: EasyList is ~5 MB and AdGuard Base ~8 MB, and re-parsing either on
     * every first page load would stall the WebView thread that asks for them.
     * A failed download leaves the previous cache untouched.
     */
    suspend fun download(url: String, context: Context): Set<String> {
        val text = Http.fetchStringCapped(
            url = url,
            maxBytes = 12 * 1024 * 1024,
            readTimeoutSec = 60,
        ).getOrDefault("")
        if (text.isBlank()) return emptySet()
        val domains = parseList(text)
        if (domains.isNotEmpty()) {
            runCatching {
                cacheFile(context, url).writeText(domains.sorted().joinToString("\n"))
            }
        }
        return domains
    }

    /** Reads the cached domains for [url] without touching the network. */
    fun loadCached(url: String, context: Context): Set<String> {
        val f = cacheFile(context, url)
        if (!f.exists()) return emptySet()
        return runCatching { parseList(f.readText()) }.getOrDefault(emptySet())
    }

    /** (re)downloads every configured list, returning the combined domain set.
     *  A single failing list is skipped, never fatal. */
    suspend fun refreshAll(lists: List<HostList>, context: Context): Set<String> {
        val all = HashSet<String>()
        for (l in lists) {
            runCatching { download(l.url, context) }.getOrDefault(emptySet()).let { all.addAll(it) }
        }
        return all
    }

    /**
     * Final blocked set for the WebView: cached list domains + built-ins +
     * manual blocklist, minus anything on the whitelist. Returns
     * (blockedDomains, whitelistDomains); check the whitelist FIRST.
     */
    fun resolve(
        context: Context,
        lists: List<HostList>,
        manualBlock: List<String>,
        manualWhite: List<String>,
    ): Pair<Set<String>, Set<String>> {
        val blocked = HashSet<String>()
        for (l in lists) blocked.addAll(loadCached(l.url, context))
        blocked.addAll(BUILTIN)
        for (d in manualBlock) {
            val c = d.trim().trimEnd('.').lowercase()
            if (c.isNotBlank()) blocked.add(c)
        }
        val white = manualWhite
            .map { it.trim().trimEnd('.').lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        for (d in white) blocked.remove(d)
        return blocked to white
    }

    /** True if [host] equals a blocked domain or is a subdomain of one. */
    fun matches(host: String, domains: Set<String>): Boolean {
        if (domains.isEmpty()) return false
        var h = host.trim().trimEnd('.').lowercase()
        if (domains.contains(h)) return true
        while (true) {
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (h.isEmpty()) return false
            if (domains.contains(h)) return true
        }
    }

    /** Blocked set built purely from the on-disk cache + built-ins — no store,
     *  no network, no suspension. For WebViews that can't suspend (e.g. the
     *  hidden Cloudflare solver): covers whatever lists the user configured,
     *  since downloading a list caches it under filesDir/adblock/. */
    fun cachedResolve(context: Context): Set<String> {
        val blocked = HashSet<String>()
        val dir = cacheDir(context)
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".txt")) {
                runCatching { blocked.addAll(parseList(f.readText())) }
            }
        }
        blocked.addAll(BUILTIN)
        return blocked
    }

    /** True when a request looks like actual media (never ad-block these —
     *  blocklists routinely contain tube-site CDN domains that serve the
     *  real video). */
    fun isMediaLike(request: android.webkit.WebResourceRequest): Boolean {        val h = request.requestHeaders
        if (h?.containsKey("Range") == true) return true
        val accept = h?.get("Accept")?.lowercase().orEmpty()
        if (accept.contains("video/") || accept.contains("application/octet-stream")) return true
        val u = request.url.toString().lowercase()
        return u.contains(".mp4") || u.contains(".m3u8") || u.contains(".mpd") ||
            u.contains(".ts?") || u.contains("videoplayback") || u.contains("/videos/") ||
            u.contains("/media/") || u.contains("phncdn") || u.contains("streamable")
    }

    fun normalizeDomain(raw: String): String =
        raw.trim()
            .removePrefix("http://").removePrefix("https://")
            .removePrefix("*.").removePrefix("||")
            .substringBefore('^').substringBefore('/').substringBefore('?')
            .substringBefore('#').trim().trimEnd('.').lowercase()

    // ------------------------------------------------ URL-pattern blocking
    //
    // The uBO-style half a hosts file cannot do. Hosts only match whole
    // DOMAINS, so everything below sailed straight through even with lists
    // added and enabled (the user's own report): ads served from the site's
    // OWN domain (`site.com/ads/banner.js`, `site.com/pop.js`), from ad
    // subdomains no list names yet (`xyz.ads-network.foo`), and rotating
    // popunder/popup hosts. These EasyList-subset network rules match the
    // URL itself, and run in the WebView's request interceptor right after
    // the hosts check (whitelist and the media exemption both still win —
    // see the call site).
    //
    // Supported syntax (the practical core of EasyList/uBO network rules):
    //   ||domain^path*   domain-anchored (any subdomain, any scheme)
    //   |http…|          start/end anchored
    //   plain substring  (with * wildcards)
    //   ^               a "separator" (end of host, /, ?, &, =, : …)
    //   @@…             exception — never block, wins over everything
    //   $third-party / $~third-party, $domain=a|~b, $script,stylesheet,image,
    //   font,media,xmlhttprequest,subdocument,other, $match-case

    data class PatternRule(
        val raw: String,
        val isException: Boolean,
        val regex: Regex,
        /** null = either; true = third-party requests only. */
        val thirdParty: Boolean?,
        /** null = any page; otherwise page hosts this rule applies to. */
        val domains: List<DomainOpt>?,
        /** null = any resource kind. */
        val types: Set<String>?,
        val matchCase: Boolean,
    )

    data class DomainOpt(val domain: String, val negated: Boolean)

    /** Curated built-ins: the ad networks video/streaming sites actually use
     *  (popunders, redirect gates, in-page widgets), as URL patterns so
     *  same-host servings and rotating subdomains are caught too. */
    val BUILTIN_PATTERNS = listOf(
        // Popunder / popup delivery.
        "||popads.net^", "||popcash.net^", "||popunder.net^", "||onclickads.net^",
        "||adsterra.com^", "||propellerads.com^", "||propeller-tracking.com^",
        "||adcash.com^", "||exoclick.com^", "||trafficjunky.net^",
        "||juicyads.com^", "||adxpansion.com^", "||hilltopads.net^",
        "||clickadu.com^", "||mgid.com^", "||revcontent.com^",
        "||outbrain.com^", "||taboola.com^", "||criteo.com^",
        "||adnxs.com^", "||rubiconproject.com^", "||pubmatic.com^",
        "||doubleclick.net^", "||googlesyndication.com^",
        "||googleadservices.com^", "||amazon-adsystem.com^",
        "||imasdk.googleapis.com^", "||adform.net^", "||smartadserver.com^",
        "||adspyglass.com^", "||tsyndicate.com^", "||magsrv.com^",
        "||realsrv.com^", "||adnium.com^", "||adnami.io^",
        "||doubleverify.com^", "||adsafeprotected.com^", "||moatads.com^",
        "||quantserve.com^", "||scorecardresearch.com^", "||adsrvr.org^",
        "/popunder", "/pop-under", "/popup-ad", "/popad.", "/pop.js",
        "/popunder.js", "/popup.js", "/popcash.js", "/pst.js",
        "/push-notification.js", "/pushcrew.js",
        // Same-host ad scripts and redirect gates (hosts files never see these).
        "/ads/banner", "/ads/pop", "/ads.js", "/ads.min.js", "/ad.js",
        "/adframe.", "/adserver/", "/advertising/", "/advert/",
        "/promo-redirect", "/redirect-ads", "/out.php?",
        // Video-player ad injection.
        "||vast.com^", "/vast.xml", "/vmap", "imasdk",
        "/jwplayer-ads", "/video-ads/", "/preroll", "/midroll", "/adbreak",
        "/videojs-contrib-ads", "/google.ima", "/ima3.js",
        // Prebid / header-bidding wrappers used to fill ad slots.
        "/prebid", "/gpt.js", "/googletag", "/pagead/", "/adsbygoogle",
        // Trackers that exist only to profile ad clicks.
        "||moatads.com^", "||scorecardresearch.com^", "||quantserve.com^",
        // Never break logins / payments / the video itself.
        "@@||accounts.google.com^", "@@||paypal.com^", "@@||stripe.com^",
        "@@||paypalobjects.com^", "@@||stripe.network^",
        "@@||cloudflare.com^", "@@||challenges.cloudflare.com^",
        "@@||gstatic.com^", "@@||www.gstatic.com^",
        "@@||jsdelivr.net^", "@@||unpkg.com^",
    )

    private val compiledPatterns: List<PatternRule> by lazy {
        BUILTIN_PATTERNS.mapNotNull { parsePattern(it) }
    }

    private val optionsRe = Regex("^[a-z~\\-]+(=[^,]*)?(,[a-z~\\-]+(=[^,]*)?)*$")

    /** Parses one EasyList-subset rule, or null when it is not a network rule. */
    fun parsePattern(raw: String): PatternRule? {
        var s = raw.trim()
        if (s.isEmpty() || s.startsWith("!") || s.startsWith("[")) return null
        if (s.contains("##") || s.contains("#@#") || s.contains("#?#")) return null
        val isException = s.startsWith("@@")
        if (isException) s = s.substringAfter("@@")
        var thirdParty: Boolean? = null
        var domains: List<DomainOpt>? = null
        var types: Set<String>? = null
        var matchCase = false
        val optIdx = s.lastIndexOf('$')
        if (optIdx > 0) {
            val opts = s.substring(optIdx + 1)
            // A `$` inside a real pattern (rare) is not options: options look
            // like `name` or `name=value` lists, nothing else.
            if (optionsRe.matches(opts)) {
                s = s.substring(0, optIdx)
                for (o in opts.split(',')) {
                    val name = o.substringBefore('=')
                    val value = o.substringAfter('=', "")
                    when (name) {
                        "third-party" -> thirdParty = true
                        "~third-party" -> thirdParty = false
                        "domain" -> domains = value.split('|')
                            .filter { it.isNotBlank() }
                            .map { DomainOpt(it.trimStart('~').lowercase(), it.startsWith("~")) }
                            .takeIf { it.isNotEmpty() }
                        "match-case" -> matchCase = true
                        "script", "stylesheet", "image", "font", "media",
                        "xmlhttprequest", "subdocument", "other", "object",
                        "ping", "websocket" -> types = (types ?: emptySet()) + name
                        "~script", "~stylesheet", "~image", "~font", "~media",
                        "~xmlhttprequest", "~subdocument", "~other" -> {
                            // Negated types are rare in practice; treat the
                            // rule as untyped rather than misread it.
                        }
                        "document", "elemhide", "generichide", "important",
                        "redirect", "csp", "removeparam", "cookie" -> {
                            // Page-level / cosmetic / header options: not
                            // network-block semantics — ignore the option.
                        }
                        else -> {
                            // Unknown option: safest to keep the rule but
                            // untyped would MISREAD it, so drop the rule.
                            if (name.isNotBlank()) return null
                        }
                    }
                }
            }
        }
        if (s.isEmpty()) return null
        val startAnchor = s.startsWith("|") && !s.startsWith("||")
        val domainAnchor = s.startsWith("||")
        var endAnchor = false
        var core = when {
            domainAnchor -> s.substring(2)
            startAnchor -> s.substring(1)
            else -> s
        }
        if (core.endsWith("|") && !core.endsWith("\\|")) {
            endAnchor = true
            core = core.dropLast(1)
        }
        val body = buildString {
            for (c in core) {
                when (c) {
                    '*' -> append(".*")
                    '^' -> append("(?:[^a-zA-Z0-9_.%-]|$)")
                    else -> append(Regex.escape(c.toString()))
                }
            }
        }
        val pattern = when {
            domainAnchor -> "^[a-zA-Z][a-zA-Z0-9+.-]*://([^/]*\\.)?" + body
            startAnchor -> "^" + body
            else -> body
        } + if (endAnchor) "$" else ""
        val regex = runCatching {
            Regex(pattern, if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        }.getOrNull() ?: return null
        return PatternRule(raw, isException, regex, thirdParty, domains, types, matchCase)
    }

    /** The request's coarse kind, inferred from its URL and headers (the
     *  WebView never tells us the type directly). Unknown → "other". */
    fun requestType(request: android.webkit.WebResourceRequest): String {
        val u = request.url.toString().lowercase()
        val path = u.substringBefore('?')
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> "script"
            path.endsWith(".css") -> "stylesheet"
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") ||
                path.endsWith(".ico") || path.endsWith(".avif") || path.endsWith(".bmp") -> "image"
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                path.endsWith(".otf") || path.endsWith(".eot") -> "font"
            path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".mpd") ||
                path.endsWith(".ts") || path.endsWith(".webm") || path.endsWith(".m4s") -> "media"
            else -> {
                val accept = request.requestHeaders?.get("Accept")?.lowercase().orEmpty()
                when {
                    accept.contains("javascript") || accept.contains("ecmascript") -> "script"
                    accept.contains("css") -> "stylesheet"
                    accept.startsWith("image/") -> "image"
                    accept.contains("font") -> "font"
                    accept.contains("video/") || accept.contains("audio/") ||
                        accept.contains("mpegurl") || accept.contains("mpd") -> "media"
                    request.method.equals("POST", ignoreCase = true) -> "xmlhttprequest"
                    else -> "other"
                }
            }
        }
    }

    private fun sameParty(reqHost: String, pageHost: String): Boolean {
        val r = reqHost.lowercase()
        val p = pageHost.trim().trimEnd('.').lowercase()
        return r == p || r.endsWith(".$p") || p.endsWith(".$r")
    }

    private fun ruleApplies(
        rule: PatternRule,
        url: String,
        reqHost: String,
        pageHost: String?,
        type: String,
    ): Boolean {
        if (!rule.regex.containsMatchIn(url)) return false
        if (rule.thirdParty != null && pageHost != null) {
            val third = !sameParty(reqHost, pageHost)
            if (rule.thirdParty != third) return false
        }
        rule.domains?.let { opts ->
            val ph = (pageHost ?: "").lowercase()
            var included = opts.none { !it.negated }
            for (o in opts) {
                if (ph == o.domain || ph.endsWith("." + o.domain)) {
                    if (o.negated) return false
                    included = true
                }
            }
            if (!included) return false
        }
        rule.types?.let { if (type !in it) return false }
        return true
    }

    /**
     * True when a URL is blocked by pattern rules (exceptions win). The
     * caller still checks the whitelist first and never blocks media —
     * this answers only "does a rule match".
     */
    fun matchesPatternUrl(url: String, pageHost: String?, type: String = "other"): Boolean {
        val reqHost = runCatching {
            java.net.URI(url).host
        }.getOrNull()?.trim()?.trimEnd('.')?.lowercase().orEmpty()
        if (reqHost.isEmpty()) return false
        var blocked = false
        for (rule in compiledPatterns) {
            if (!ruleApplies(rule, url, reqHost, pageHost, type)) continue
            if (rule.isException) return false
            blocked = true
        }
        return blocked
    }

    /** Same as [matchesPatternUrl] for a live WebView request (type inferred). */
    fun matchesPattern(request: android.webkit.WebResourceRequest, pageHost: String?): Boolean =
        matchesPatternUrl(request.url.toString(), pageHost, requestType(request))
}
