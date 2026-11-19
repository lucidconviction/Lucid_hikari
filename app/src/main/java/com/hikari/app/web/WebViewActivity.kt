package com.hikari.app.web

import com.hikari.app.i18n.I18n
import android.app.AlertDialog
import android.app.DownloadManager
import android.widget.Button
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ScrollView
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.hikari.app.HikariApp
import com.hikari.app.data.Site
import com.hikari.app.net.AdBlocker
import com.hikari.app.net.Http
import com.hikari.app.net.PromoGuard
import com.hikari.app.player.PlayerActivity
import com.hikari.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Ad-free web view for user-added movie/streaming websites.
 *
 * Everything is tuned to behave like a real Chrome browser so the sites' own
 * HTML5 players work:
 *  - Full cookie support INCLUDING third-party cookies (CDN auth cookies and
 *    Cloudflare cf_clearance are set/read cross-origin), flushed to disk so
 *    they survive activity restarts.
 *  - A current desktop-class Chrome user agent (sites serve their normal
 *    player instead of a mobile/fallback page, and Cloudflare challenges get
 *    the least suspicious fingerprint possible).
 *  - An hls.js polyfill: Android's WebView CANNOT decode HLS (.m3u8) in a
 *    <video> tag, which is exactly why "the site loads but the video buffers
 *    forever". The polyfill injects hls.js and attaches it to any m3u8 video
 *    the page creates (their own hls.js is detected and left alone).
 *  - Ads are HIDDEN (never removed, and never touching <video>), so scripts
 *    that reference ad elements keep working and the player is never torn
 *    down by our cleanup.
 *  - A floating ▶ button hands any REAL detected HLS/DASH/MP4 source straight
 *    to Hikari's ExoPlayer as a fallback when a site's player won't cooperate,
 *    carrying cookies + the page URL as Referer for hotlink-protected CDNs.
 */
class WebViewActivity : ComponentActivity() {

    /** In-app UI scale: keeps this View-based screen on the same interface size
     *  as the rest of the app (and off the phone's own font/display size when
     *  the in-app scale is enabled). */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.hikari.app.ui.UiScale.wrap(newBase))
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var videoChip: TextView
    private lateinit var urlField: EditText
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    /** `og:image` (or the first large image) of the page, for the media sheet's
     *  preview — the PlayBridge-style "what am I about to play" card. */
    @Volatile
    private var pagePreviewUrl: String? = null
    @Volatile
    private var pagePreviewTitle: String = ""
    private lateinit var rootView: LinearLayout
    /** Horizontal strip of saved pages at the bottom of the browser. */
    private lateinit var bookmarkBar: LinearLayout
    private var contentFrame: FrameLayout? = null
    // Main-frame load failure panel. A blank black page with no explanation was
    // the whole "it didn't load" experience: a dead host, a dropped connection
    // and a rejected certificate all looked identical. The panel says what
    // happened and offers a retry without leaving the page.
    private var errorPanel: LinearLayout? = null
    private var errorDetail: TextView? = null
    private var lastFailedUrl: String? = null
    private var loadFailed = false
    // Remote-mouse cursor (Home header toggle): a D-pad-driven ring over the
    // page. Arrows move it, OK taps the page AT the ring by dispatching a real
    // touch to the WebView — so a Cloudflare "I'm not a robot" checkbox and
    // any other target can be pressed from the sofa.
    private var mouseMode = false
    private var cursorView: View? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private val detectedVideos = LinkedHashSet<String>()
    private var pageUrl: String? = null
    private var pageTitle: String = ""
    private var startUrl: String = ""

    // Web Apps thumbnails: a page capture is taken a moment AFTER onPageFinished
    // so lazy images/webfonts are in place. Each navigation bumps the token so a
    // pending capture can never write the previous page under the new host.
    private val shotHandler = Handler(Looper.getMainLooper())
    private var shotToken = 0

    // Provider this WebView was opened for (null = a plain site/URL). Used by
    // the per-extension auto-translate feature to decide whether the page's
    // text should be translated to English.
    private var providerId: String? = null

    // Whether to translate this page's text into English. True when the intent
    // asked for it explicitly ("Just this time") or the provider has "Always
    // translate" turned on in the store.
    @Volatile
    private var translateEnabled = false

    // "Verify for Cloudflare" mode (opened from the Home tab): the site is
    // shown so the user can complete a CF challenge; once a challenge was seen
    // and the page turns into real content, the activity finishes by itself so
    // the caller can reload the extension catalog with the now-valid cookies.
    private var autoCloseWhenCloudflarePassed = false
    private var challengeSeen = false
    private var verifyDone = false
    // Consecutive polls where the verify page looked like a hard WAF block
    // ("you have been blocked") rather than a solvable challenge — a block can
    // never mint a clearance, so after a few ticks the view closes itself
    // instead of lingering on top of the player forever.
    private var blockedCount = 0
    // Consecutive polls where the verify page showed neither a challenge nor a
    // hard block — i.e. it loaded as ordinary content and has nothing to
    // verify. After a few ticks the view closes itself instead of lingering.
    private var noChallengePolls = 0
    // Set when this view was opened to pass a Cloudflare challenge for a host
    // (intent extra "verifyHost"). CloudflareVerifier is NOT the launcher — it
    // never opens a visible view (see its class doc); the host is passed so the
    // verifier can wake its waiters the moment this view closes and retry
    // immediately.
    private var verifyHost: String? = null
    // Renderer-crash recoveries already spent by this view (carried across the
    // relaunch via the intent, see onRenderProcessGone).
    private var renderRestarts = 0
    // Open tabs — each tab remembers its URL, a display title, and a short-
    // lived favicon URL (the Google favicons proxy, which works for any domain
    // without needing to load the page). Tabs persist across relaunches so the
    // user always sees the same set when they come back to this activity.
    private val tabsPrefs by lazy {
        getSharedPreferences("webview_tabs", MODE_PRIVATE)
    }
    private val openTabs = mutableListOf<Tab>()
    // Which tab in [openTabs] is the currently visible one (index into the list).
    private var activeTabIndex = 0
    // Video-verification mode (set alongside verifyHost): this view is open
    // solely to pass a CF challenge for a STREAMING site, and
    // the site's redirect to the real video page is legitimate — so main-frame
    // redirects are NOT blocked here (the browsing view's redirect protection
    // cancels exactly those, which is what kept the movie page from opening).
    // Ad hosts are still blocked and popups are still dropped, so it can't
    // spiral into ad sites.
    @Volatile
    private var verifyAllowRedirects = false
    // Set when this view is an addon's OWN configuration page (the page a
    // Stremio addon publishes, see StremioAddon.configurationPage): the link
    // that page hands back to install the configured addon is captured and
    // returned to the caller instead of being loaded. Nothing else about the
    // page is special-cased.
    @Volatile
    private var addonConfig = false
    private val verifyHandler = Handler(Looper.getMainLooper())
    private var verifyRunnable: Runnable? = null

    // Persisted element-block selectors (WebView menu → Element blocker). Loaded
    // from the store on launch, kept in sync by the bridge, and handed to the
    // page's ELEMENT_BLOCK_JS on every load so blocked elements stay hidden.
    private val blockedSelectors = java.util.LinkedHashSet<String>()

    // Ad blocking (WebView requests only) — resolved from the user's lists on
    // launch, and never applied to the ExoPlayer's own network I/O.
    @Volatile
    private var blockedDomains: Set<String> = emptySet()
    @Volatile
    private var whitelistDomains: Set<String> = emptySet()

    // WebView safety toggles (Settings → Privacy & Browsing → WebView safety). Default ON:
    //  - redirectProtection: the main frame can only navigate within the site
    //    it was opened for — ad-hijack redirects (ad.twinrdengine.com & co)
    //    are cancelled before they load.
    //  - popupProtection: window.open() popups are only relayed into the view
    //    when they belong to the same site; a popup the user's own tap opened
    //    to a foreign site asks (Allow once / Always / Cancel) instead of
    //    being dropped, and automatic popunders are still dropped unasked.
    @Volatile
    private var redirectProtection = true
    @Volatile
    private var popupProtection = true
    @Volatile private var blockedToastShown = false
    // Hosts the user explicitly allowed redirects to (Settings → Privacy & Browsing → WebView safety
    // → Allowed redirect links). Navigations to these are never blocked.
    //
    // Seeded from the in-memory mirror rather than empty (see RedirectAllow):
    // the store read below is asynchronous, and a page's first redirect can
    // easily beat it — which is how an allowed link (net77.cc) was still
    // "Blocked redirect to net77.cc" in the verification view.
    @Volatile
    private var allowedRedirectHosts: Set<String> = com.hikari.app.data.RedirectAllow.now()

    /**
     * The main-frame navigation the redirect protection refused LAST, so the ⋯
     * menu can offer to allow it and open it in one tap (see
     * [allowBlockedRedirect]). Cleared on every new page ([onPageStarted]), so
     * the offer can never point at a link from a page the user has left.
     */
    /**
     * When the user last put a finger on the page.
     *
     * WebView credits a real tap to `request.hasGesture()` / `onCreateWindow`'s
     * `isUserGesture` only when the navigation comes straight from the touch
     * handler. Streaming sites almost never do that: the tap runs a script,
     * which sets `location.href` from a cross-origin iframe or calls
     * `window.open` a tick later — and both arrive with the gesture flag
     * FALSE. Treating that as an automatic redirect is what made a tapped
     * episode do nothing ("secondary screens don't load") and a tapped player
     * popup get dropped.
     *
     * "A finger was here in the last couple of seconds" is the reliable
     * signal. It only ever RELAXES the redirect/popup gates — the ad host and
     * ad pattern checks still run first, and a redirect with no touch behind
     * it stays cancelled exactly as before.
     */
    @Volatile
    private var lastUserTouchAt = 0L

    private fun recentUserGesture(): Boolean =
        android.os.SystemClock.uptimeMillis() - lastUserTouchAt < 2_500L

    /** Debug-build breadcrumb for a page that renders blank: what loaded,
     *  what was cancelled, and which gate cancelled it. Release builds log
     *  nothing. */
    private fun dbg(msg: String) {
        if (com.hikari.app.BuildConfig.DEBUG) android.util.Log.d("HikariWeb", msg)
    }

    @Volatile
    private var lastBlockedRedirectUrl: String? = null
    @Volatile
    private var lastBlockedRedirectHost: String? = null

    /** The user's allowed redirect hosts, mirror included. */
    private fun allowedNow(): Set<String> =
        allowedRedirectHosts + com.hikari.app.data.RedirectAllow.now()

    /**
     * True when a navigation to [url] is one the USER allowed (Settings →
     * Privacy & Browsing → WebView safety → "Allowed redirect links", or the
     * WebView's own ⋯ menu). Both forms the list accepts are honoured: a host
     * ("net77.cc", subdomains included) and a bare word ("filester", any link
     * that contains it — see [com.hikari.app.data.RedirectAllow]).
     *
     * The activity's own copy of the list is consulted as well as the in-memory
     * mirror, because the activity reads the store itself on launch (see
     * [allowedNow]): whichever of the two holds the entry, the redirect is the
     * user's own decision and must not be cancelled.
     */
    private fun isRedirectAllowed(url: String?): Boolean =
        com.hikari.app.data.RedirectAllow.allowsIn(url, allowedNow())

    /** Records a refused main-frame navigation so the ⋯ menu can allow it (see
     *  [allowBlockedRedirect]). */
    private fun rememberBlockedRedirect(url: String) {
        lastBlockedRedirectUrl = url
        lastBlockedRedirectHost =
            runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
    }

    /**
     * The one-line toast for a refused redirect: which host was cancelled, and
     * where the way out is. The ⋯ menu carries "Allow redirect to <host>" for
     * exactly this link (see [allowBlockedRedirect]), which is what makes an
     * extension's file host a one-tap decision instead of a trip to Settings.
     */
    private fun blockedRedirectToast(url: Uri): String =
        "Blocked redirect to " + (url.host ?: "unknown") + " — open the menu to allow it"

    // Guards the auto hand-off to the external player: a page that genuinely
    // can't start its own <video> gets handed to Hikari's ExoPlayer ONCE (reset
    // on every navigation), so the user never stares at an infinite spinner.
    private var autoLaunched = false

    /**
     * A URL the user just approved with "Allow once" (a popup or a redirect
     * protection dialog). Both navigation gates honour it for a short window:
     * without this, the dialog's own `loadUrl` was immediately re-cancelled by
     * the other gate — the exact "I tapped Allow once and nothing opened"
     * behaviour. It never overrides the donation guard.
     */
    @Volatile private var allowedOnceUrl: String? = null
    @Volatile private var allowedOnceAt = 0L
    private fun allowOnce(url: String) {
        allowedOnceUrl = url
        allowedOnceAt = SystemClock.elapsedRealtime()
    }
    private fun isOneShotAllowed(url: String?): Boolean {
        val target = allowedOnceUrl ?: return false
        if (url == null || url != target || SystemClock.elapsedRealtime() - allowedOnceAt > 20_000L) {
            if (SystemClock.elapsedRealtime() - allowedOnceAt > 20_000L) allowedOnceUrl = null
            return false
        }
        return true
    }

    /** Host of the page currently shown, cached so the per-request ad checks do
     *  not parse a URI for every image, script and segment. */
    @Volatile private var pageHostCache: String? = null

    /** Per-session "Request desktop site" choice. The default UA comes from
     *  Settings; this is the one-tap override browsers put in their menu. */
    @Volatile private var desktopMode = false

    // True while any <video> on the page is actively playing (kept fresh by the
    // periodic scan). Popups opened while a video is playing are ads — players
    // pop them on click — so they're dropped instead of replacing the video.
    @Volatile
    private var videoPlaying = false


    // Fullscreen HTML5 video support
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // File uploads (sites with <input type=file>)
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // Periodic re-scan for SPA players that add <video> without a navigation
    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null

    // Temporary child WebView that relays window.open() popups into the main
    // view. Android CRASHES with "Parent WebView cannot host its own popup
    // window" if a popup's transport targets the parent WebView itself, so the
    // popup URL is captured in a throwaway child and loaded in the main view.
    private var popupChild: WebView? = null

    /**
     * Popup hosts the user chose "Always allow" for (see [askPopupPermission]):
     * a blocked popup from one of these opens without asking again. Kept in
     * this view's own preferences (not the store): it is a per-device browsing
     * choice, exactly like a browser's pop-up exceptions list.
     */
    private val popupAllowPrefs by lazy {
        getSharedPreferences("webview_popup_allow", MODE_PRIVATE)
    }

    /** Popup allow/block hosts for this view (Settings → Privacy & Browsing → WebView safety). */
    @Volatile
    private var popupAllowHosts: Set<String> = emptySet()
    @Volatile
    private var popupBlockHosts: Set<String> = emptySet()

    // ---- Tab management ---------------------------------------------------

    /** One browsing session tab — a URL with a display title and a favicon URL. */
    data class Tab(
        val url: String,
        val title: String,
        /** A Google favicons proxy URL: works for any domain without loading the page. */
        val faviconUrl: String = "https://www.google.com/s2/favicons?domain=${Uri.parse(url).host ?: url}&sz=64",
    )

    /** Loads persisted tabs from SharedPreferences and restores [openTabs] + [activeTabIndex]. */
    private fun loadTabs() {
        val json = tabsPrefs.getString("tabs_json", null) ?: return
        runCatching {
            val arr = JSONArray(json)
            openTabs.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                openTabs.add(Tab(obj.optString("url"), obj.optString("title")))
            }
            activeTabIndex = tabsPrefs.getInt("active_tab", 0).coerceIn(0, openTabs.size - 1)
        }
    }

    /** Persists [openTabs] + [activeTabIndex] to SharedPreferences. */
    private fun saveTabs() {
        val arr = JSONArray()
        for (t in openTabs) arr.put(JSONObject().apply {
            put("url", t.url)
            put("title", t.title)
        })
        tabsPrefs.edit().apply {
            putString("tabs_json", arr.toString())
            putInt("active_tab", activeTabIndex)
            apply()
        }
    }

    /** Opens a new tab with the given URL/title and makes it the active one. */
    private fun openTab(url: String, title: String) {
        val normalized = if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        ) url else "https://$url"
        openTabs.add(Tab(normalized, title.ifBlank { normalized }))
        activeTabIndex = openTabs.size - 1
        saveTabs()
    }

    /** Closes the tab at the given index; picks the nearest neighbour as active. */
    private fun closeTabAt(index: Int) {
        if (openTabs.size <= 1) { finish(); return }
        openTabs.removeAt(index)
        activeTabIndex = when {
            activeTabIndex >= openTabs.size -> openTabs.size - 1
            activeTabIndex < 0 -> 0
            else -> activeTabIndex
        }
        saveTabs()
        switchToActiveTab()
    }

    /** Jumps to the active tab without changing the list. */
    private fun switchToActiveTab() {
        if (openTabs.isEmpty()) return
        val t = openTabs[activeTabIndex]
        pageUrl = t.url
        pageTitle = t.title
        webView.loadUrl(t.url)
    }

    /** Called when this activity is first created — loads the active tab's URL. */
    private fun restoreActiveTab(recreated: Boolean = false) {
        // Temporary views (Cloudflare verification, an add-on's configuration
        // page) are not browsing sessions: adding them to the tab list put a
        // "just a moment" page in the user's Web Apps tab strip forever.
        if (verifyHost != null || autoCloseWhenCloudflarePassed || addonConfig) {
            loadTabs()
            return
        }
        loadTabs()
        if (openTabs.isEmpty()) {
            openTabs.add(Tab(startUrl, pageTitle))
            activeTabIndex = 0
            saveTabs()
            pageUrl = startUrl
            return
        }
        // If the start URL from the intent is different from any saved tab, push
        // the intent URL as a new tab so the saved session is not lost. On a
        // recreation (rotation/process restore) the active tab has already moved
        // beyond the original start URL, so adding it again would duplicate the
        // tab the user was reading.
        val exact = openTabs.indexOfFirst { it.url == startUrl }
        if (exact >= 0) {
            activeTabIndex = exact
        } else if (!recreated) {
            openTabs.add(Tab(startUrl, pageTitle))
            activeTabIndex = openTabs.size - 1
            saveTabs()
        } else {
            activeTabIndex = activeTabIndex.coerceIn(0, openTabs.lastIndex)
        }
        val t = openTabs[activeTabIndex]
        pageUrl = t.url
        pageTitle = t.title
    }

    /**
     * Keeps the active tab in step with the page the user is actually on: its
     * URL and title are what the tab grid shows and what the next launch
     * restores. Temporary verification/config views are excluded.
     */
    private fun noteVisited(url: String?, title: String? = null) {
        if (verifyHost != null || autoCloseWhenCloudflarePassed || addonConfig) return
        val index = activeTabIndex
        if (index !in openTabs.indices) return
        val old = openTabs[index]
        val newUrl = url?.takeIf {
            it.startsWith("http://", true) || it.startsWith("https://", true)
        } ?: old.url
        val newTitle = title?.takeIf { it.isNotBlank() } ?: old.title
        if (newUrl == old.url && newTitle == old.title) return
        openTabs[index] = old.copy(url = newUrl, title = newTitle)
        if (newTitle != pageTitle) pageTitle = newTitle
        saveTabs()
    }

    // ---- Save current page as a Website -----------------------------------

    /**
     * Captures the loaded page for the Web Apps grid once it has fully settled.
     *
     * onPageFinished fires when the document is done, not when its lazy images
     * and webfonts are painted, so the capture waits a moment. The token makes
     * this a no-op if the user has navigated somewhere else in between — a
     * screenshot of page A must never be filed under page B's host.
     */
    private fun scheduleScreenshot(view: WebView?, url: String?) {
        if (view == null || url.isNullOrBlank()) return
        // Only ordinary browsing gets thumbnailed: an extension's verification
        // / login / watch page has no Web Apps card, and saving screenshots of
        // those would be both useless and a privacy leak.
        if (providerId != null) return
        val token = ++shotToken
        shotHandler.removeCallbacksAndMessages(null)
        shotHandler.postDelayed({
            if (token != shotToken) return@postDelayed
            val current = webView?.url?.takeIf { it.isNotBlank() } ?: url
            WebAppShots.capture(this, view, current)
        }, 2_200L)
    }

    /** Adds the currently-viewed page to the user's saved Websites (as seen in
     *  the Websites tab). Shows a small dialog for the user to confirm or rename
     *  the site before saving. */
    private fun saveCurrentSite() {
        val url = pageUrl ?: webView.url ?: return
        val host = currentPageHost()?.trim()?.trimEnd('.')?.lowercase().orEmpty()
        val defaultName = host.ifBlank { pageTitle }
        val builder = AlertDialog.Builder(this)
        builder.setTitle(I18n.t("Save this website"))
        val input = android.widget.EditText(this).apply {
            setText(defaultName)
            setHint(I18n.t("Website name"))
        }
        builder.setView(input)
        builder.setPositiveButton(I18n.t("Save")) { _, _ ->
            val name = input.text?.toString()?.trim()?.ifEmpty { defaultName } ?: defaultName
            // Take the thumbnail only once the page reports 100% — a site
            // saved while still streaming in its hero images would otherwise
            // get a half-painted screenshot. A page not done yet is captured
            // by the scheduled post-load shot instead.
            if (webView.progress >= 100) {
                WebAppShots.capture(this, webView, webView.url ?: url)
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val app = applicationContext as HikariApp
                app.store.addSite(Site(name, url))
            }
            Toast.makeText(this, I18n.t("Website saved"), Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton(I18n.t("Cancel"), null)
        builder.show()
    }

    /** Last popup URL/host the page tried to open (for the ⋯ "block this ad" offer). */
    @Volatile
    private var lastPopupUrl: String? = null
    @Volatile
    private var lastPopupHost: String? = null

    private fun isPopupHostAllowed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        if (popupAllowHosts.contains(h)) return true
        return popupAllowPrefs.getStringSet("hosts", emptySet()).orEmpty()
            .contains(h)
    }

    private fun isPopupHostBlocked(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return popupBlockHosts.contains(host.lowercase())
    }

    private fun rememberPopupHost(host: String) {
        val norm = host.lowercase()
        val allow = popupAllowPrefs.getStringSet("hosts", emptySet()).orEmpty()
            .toMutableSet()
        if (allow.add(norm)) {
            popupAllowPrefs.edit().putStringSet("hosts", allow).apply()
        }
        popupAllowHosts = popupAllowHosts + norm
        popupBlockHosts = popupBlockHosts - norm
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            runCatching {
                val cur = app.store.webviewPopupAllow().toMutableSet()
                if (cur.add(norm)) app.store.setWebviewPopupAllow(cur.toList())
                val blk = app.store.webviewPopupBlock().toMutableSet()
                if (blk.remove(norm)) app.store.setWebviewPopupBlock(blk.toList())
            }
        }
    }

    /** "Block always": popups from this host are dropped silently, never asked again. */
    private fun rememberPopupBlocked(host: String) {
        val norm = host.lowercase()
        popupBlockHosts = popupBlockHosts + norm
        popupAllowHosts = popupAllowHosts - norm
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            runCatching {
                val cur = app.store.webviewPopupBlock().toMutableSet()
                if (cur.add(norm)) app.store.setWebviewPopupBlock(cur.toList())
                val allow = app.store.webviewPopupAllow().toMutableSet()
                if (allow.remove(norm)) app.store.setWebviewPopupAllow(allow.toList())
            }
        }
        val prefsAllow = popupAllowPrefs.getStringSet("hosts", emptySet()).orEmpty().toMutableSet()
        if (prefsAllow.remove(norm)) popupAllowPrefs.edit().putStringSet("hosts", prefsAllow).apply()
    }

    /**
     * A popup the user's own tap opened, but protection would drop: instead of
     * a "Blocked popup" toast and a dead end, the tap gets a choice — Allow
     * once, Always allow this site, Block always, or Cancel (which just drops
     * it, like before). Only ever called for popups a real tap produced:
     * automatic popunders never reach here, so they can never nag. A host on
     * the Block-always list never reaches here either — it is dropped
     * silently in the relay below.
     *
     * The choice is built as explicit buttons, not a list adapter: on some
     * themes the list rows rendered with no visible text, which is exactly
     * the reported "no option" dialog.
     */
    private fun askPopupPermission(url: String, host: String?, onAllow: (String) -> Unit) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val label = host ?: url
            val dlg = AlertDialog.Builder(this@WebViewActivity)
                .setTitle(I18n.t("Open this popup?"))
                .setMessage(
                    I18n.t("This page tried to open a popup:") + "\n" + label
                )
                .create()
            dlg.setView(choiceBox(
                I18n.t("Allow once") to { onAllow(url) },
                (I18n.t("Always allow") + (if (!host.isNullOrBlank()) " $host" else "")) to {
                    if (!host.isNullOrBlank()) {
                        rememberPopupHost(host)
                        Toast.makeText(
                            this@WebViewActivity,
                            "Popups from $host will always open — change it in Settings → Privacy & Browsing",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    onAllow(url)
                },
                (I18n.t("Block always") + (if (!host.isNullOrBlank()) " $host" else "")) to {
                    if (!host.isNullOrBlank()) {
                        rememberPopupBlocked(host)
                        Toast.makeText(
                            this@WebViewActivity,
                            "Popups from $host will always be blocked — change it in Settings → Privacy & Browsing",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                I18n.t("Cancel") to { },
            ) { dlg.dismiss() })
            dlg.show()
        }
    }

    /**
     * A vertical stack of full-width buttons for a choice dialog (see
     * [askPopupPermission]): every option is always visible and tappable,
     * whatever the theme does to list rows. [onPick] runs after the dialog
     * is dismissed, around the option's own action.
     */
    private fun choiceBox(vararg options: Pair<String, () -> Unit>, onPick: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, dp(4), pad, pad)
        }
        for ((text, action) in options) {
            box.addView(Button(this).apply {
                this.text = text
                setOnClickListener { onPick(); action() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        return box
    }

    /** When the last choice dialog (popup or redirect) was shown for a URL, by
     *  URL: asking twice for one tap — once from shouldOverrideUrlLoading and
     *  once from the request backup — would stack two dialogs. */
    @Volatile
    private var lastChoiceAt = 0L
    @Volatile
    private var lastChoiceUrl: String? = null

    private fun choiceDue(url: String): Boolean {
        val now = System.currentTimeMillis()
        if (url == lastChoiceUrl && now - lastChoiceAt < 5_000L) return false
        lastChoiceUrl = url
        lastChoiceAt = now
        return true
    }

    /**
     * A main-frame navigation the user's own tap opened toward a foreign host
     * that redirect protection would refuse: instead of a bare "Blocked
     * redirect" toast and a trip to the ⋯ menu, the tap gets the same choice
     * popups get — Allow once, Always allow this host, Block always, Cancel.
     * Automatic redirects (no gesture) keep the silent block exactly as
     * before, so ad-hijack redirects can never nag.
     */
    private fun askRedirectPermission(url: String, host: String?) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (!choiceDue(url)) return@runOnUiThread
            rememberBlockedRedirect(url)
            val label = host ?: url
            val dlg = AlertDialog.Builder(this@WebViewActivity)
                .setTitle(I18n.t("Open this link?"))
                .setMessage(
                    I18n.t("This page tried to open a link:") + "\n" + label
                )
                .create()
            dlg.setView(choiceBox(
                I18n.t("Allow once") to {
                    allowOnce(url)
                    runCatching { webView.loadUrl(url) }
                },
                (I18n.t("Always allow") + (if (!host.isNullOrBlank()) " $host" else "")) to {
                    allowRedirectTo(url, host)
                },
                (I18n.t("Block always") + (if (!host.isNullOrBlank()) " $host" else "")) to {
                    if (!host.isNullOrBlank()) blockHostForever(host)
                },
                I18n.t("Cancel") to { },
            ) { dlg.dismiss() })
            dlg.show()
        }
    }

    /**
     * Adds [host] to the user's "Allowed redirect links" (the SAME list
     * Settings edits) and loads [url] straight away — the ⋯ menu's "Allow
     * redirect" generalized for the tap-choice dialog (see
     * [askRedirectPermission] and [allowBlockedRedirect]).
     */
    private fun allowRedirectTo(url: String, host: String?) {
        val entry = host?.takeIf { it.isNotBlank() } ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            val current = runCatching { app.store.webviewRedirectAllow() }.getOrDefault(emptyList())
            val next = (current + entry).distinct()
            runCatching { app.store.setWebviewRedirectAllow(next) }
            runOnUiThread {
                allowedRedirectHosts = allowedRedirectHosts + entry
                Toast.makeText(
                    this@WebViewActivity,
                    "Allowed redirect to $entry",
                    Toast.LENGTH_SHORT,
                ).show()
                runCatching { webView.loadUrl(url) }
            }
        }
    }

    /**
     * A host the user chose "Block always" for on a tapped link (see
     * [askRedirectPermission]): its requests never load again AND its popups
     * never ask again — the same two lists "Block this ad site" writes (see
     * [blockAdSite]), so the entry is visible and revertible in Settings.
     */
    private fun blockHostForever(host: String) {
        val norm = AdBlocker.normalizeDomain(host.trim().trimEnd('.').lowercase())
        if (norm.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            runCatching {
                val cur = app.store.adBlock()
                if (cur.none { AdBlocker.normalizeDomain(it) == norm }) {
                    runCatching { app.store.setAdBlock((cur + norm).distinct()) }
                }
                val blk = app.store.webviewPopupBlock().toMutableSet()
                if (blk.add(norm)) app.store.setWebviewPopupBlock(blk.toList())
                val allow = app.store.webviewPopupAllow().toMutableSet()
                if (allow.remove(norm)) app.store.setWebviewPopupAllow(allow.toList())
            }
            val (blocked, white) = runCatching {
                AdBlocker.resolve(app, app.store.adLists(), app.store.adBlock(), app.store.adWhite())
            }.getOrDefault(blockedDomains to whitelistDomains)
            blockedDomains = blocked
            whitelistDomains = white
            popupBlockHosts = popupBlockHosts + norm
            popupAllowHosts = popupAllowHosts - norm
            runOnUiThread {
                Toast.makeText(
                    this@WebViewActivity,
                    "Blocked $host — ads and popups from it will not load. Revert in Settings → Privacy.",
                    Toast.LENGTH_LONG,
                ).show()
                runCatching { webView.reload() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Debug builds only: expose the WebView to `chrome://inspect` /
        // `adb forward ... webview_devtools_remote_<pid>` so a page that
        // renders blank can be inspected (its real URL, console errors and
        // DOM). Release builds stay closed.
        if (com.hikari.app.BuildConfig.DEBUG) {
            runCatching { WebView.setWebContentsDebuggingEnabled(true) }
        }
        // Never allow a window title bar — the app draws no header anywhere.
        window.requestFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // True fullscreen: no status bar, no nav bar (swipe to reveal).
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val startUrl = intent.getStringExtra("url") ?: "https://www.google.com"
        this.startUrl = startUrl
        pageTitle = intent.getStringExtra("title").orEmpty().ifBlank { startUrl }
        autoCloseWhenCloudflarePassed = intent.getBooleanExtra("autoCloseWhenCloudflarePassed", false)
        verifyHost = intent.getStringExtra("verifyHost")
        verifyAllowRedirects = intent.getBooleanExtra("verifyAllowRedirects", false)
        addonConfig = intent.getBooleanExtra("stremioConfig", false)
        providerId = intent.getStringExtra("providerId")
        renderRestarts = intent.getIntExtra("renderRestarts", 0)
        val forceTranslate = intent.getBooleanExtra("translate", false)
        // Record every open (and why) so a "the site opened by itself" report
        // can be traced in Settings › Logs & diagnostics.
        com.hikari.app.data.Logs.log(
            "WebView",
            "open \"$startUrl\" (title=\"$pageTitle\", verifyHost=${verifyHost ?: "-"}, " +
                "autoClose=$autoCloseWhenCloudflarePassed, provider=${providerId ?: "-"}, " +
                "addonConfig=$addonConfig)"
        )

        // An extension's "support us" link is never opened: it is a funding
        // pitch, not something to watch (see PromoGuard — the same rule the
        // page itself is held to). Refused before the WebView is built, so the
        // user gets a reason instead of a blank page.
        if (PromoGuard.isDonationUrl(startUrl)) {
            com.hikari.app.data.Logs.log("WebView", "refused donation page $startUrl")
            Toast.makeText(this, "Blocked a donation page", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }

        // The media badge lives IN the toolbar, not as a floating pill over the
        // page: its job is to say "this page has N playable sources" and open
        // the list, not to cover the site's own controls.
        videoChip = TextView(this).apply {
            text = "\u25B6 0"
            visibility = View.GONE
            setBackgroundColor(0xFF2A3A6A.toInt())
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
            setPadding(dp(10), dp(5), dp(10), dp(5))
            isClickable = true
            contentDescription = I18n.t("Videos on this page")
            setOnClickListener { showMediaSheet() }
        }

        // A TELEVISION BOX MAY HAVE NO WEBVIEW AT ALL. The WebView is part of
        // the system on a phone, but plenty of cheap Android TV / Fire TV boxes
        // ship without a usable provider — and constructing one there throws
        // (MissingWebViewPackageException from WebViewFactory) instead of
        // failing politely, which used to be an instant crash the moment a
        // title, a Cloudflare check or a site link tried to open a web page.
        // Now it is a message and a closed screen.
        webView = try {
            WebView(this)
        } catch (t: Throwable) {
            com.hikari.app.data.Logs.log(
                "WebView",
                "this device has no usable WebView ($t) — cannot open $startUrl",
            )
            Toast.makeText(
                this,
                I18n.t("This device has no web browser (WebView), so this page can't be opened"),
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return
        }
        // JS → Kotlin bridge for the auto hand-off: when a page's own <video>
        // is stuck buffering forever (common on WebView-hostile tube sites),
        // the page calls HikariBridge.stuckVideo(url) and we hand the real
        // source to Hikari's ExoPlayer so playback actually starts.
        webView.addJavascriptInterface(HikariJsBridge(), "HikariBridge")

        // Built-in host rules apply to the FIRST request, before the async store
        // read below lands. They used to be empty for the first page, which is
        // exactly the page a user notices ads on.
        blockedDomains = AdBlocker.BUILTIN
            .map { AdBlocker.normalizeDomain(it) }
            .filter { it.isNotBlank() }
            .toSet()

        // ---- Browser chrome -------------------------------------------------
        //
        // A real address bar in the PlayBridge mould: back / forward, the URL
        // (tap to edit, spaces become a search), reload, the media badge that
        // opens the source list, and the ⋯ menu. Tabs moved into the menu; a
        // "Tabs" pill next to a URL field was two permanent buttons for one
        // occasional action.
        val TOP_BAR_HEIGHT = dp(52)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                TOP_BAR_HEIGHT,
            )
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xF00B0E16.toInt())
            setPadding(dp(2), 0, dp(2), 0)
        }

        fun barButton(glyph: String, desc: String, onClick: (View) -> Unit): TextView =
            TextView(this@WebViewActivity).apply {
                text = glyph
                textSize = 19f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                contentDescription = desc
                isClickable = true
                setOnClickListener { v -> onClick(v) }
            }

        backButton = barButton("\u2039", I18n.t("Back")) { if (webView.canGoBack()) webView.goBack() }
        forwardButton = barButton("\u203A", I18n.t("Forward")) { if (webView.canGoForward()) webView.goForward() }
        val reloadButton = barButton("\u27F3", I18n.t("Reload")) { webView.reload() }
        val menuButton = barButton("\u22EE", I18n.t("Browser menu")) { showMenu(it) }

        urlField = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8A93AE.toInt())
            hint = I18n.t("Search or enter address")
            textSize = 13f
            includeFontPadding = false
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0x22FFFFFF)
                setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) selectAll() }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    loadFromAddressBar(text.toString())
                    clearFocus()
                    hideKeyboard()
                    true
                } else {
                    false
                }
            }
        }

        topBar.addView(backButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        topBar.addView(forwardButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        topBar.addView(urlField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(videoChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4) })
        topBar.addView(reloadButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        topBar.addView(menuButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val content = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val panel = LinearLayout(this@WebViewActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                visibility = View.GONE
                setPadding(dp(28), dp(28), dp(28), dp(28))
                addView(
                    TextView(this@WebViewActivity).apply {
                        text = I18n.t("This page couldn't load")
                        textSize = 20f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    TextView(this@WebViewActivity).apply {
                        text = I18n.t("Check your connection and try again.")
                        textSize = 14f
                        setTextColor(0xFFB0B0B8.toInt())
                        gravity = Gravity.CENTER
                        setPadding(0, dp(12), 0, dp(18))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    Button(this@WebViewActivity).apply {
                        text = I18n.t("Try again")
                        setOnClickListener { retryLoad() }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER_HORIZONTAL },
                )
                isClickable = true
            }
            addView(panel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
            errorPanel = panel
            errorDetail = (panel.getChildAt(1) as? TextView)
        }

        // Bottom bookmark strip. It is the browser's "tabs" in the only form a
        // television can use: one row of saved pages, reachable with the D-pad
        // without opening a menu, plus a + to save the page you are on.
        bookmarkBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        val bookmarkScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF0D1018.toInt())
            addView(bookmarkBar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))
            addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TOP_BAR_HEIGHT))
            // A little air between the chrome and the page: sites open with a
            // header banner pressed right against the URL bar otherwise, and a
            // fixed site header reads as part of Hikari's own toolbar.
            addView(
                content,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = dp(16)
                },
            )
            addView(
                bookmarkScroll,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)),
            )
        }
        setContentView(rootView)
        // When the system bars are showing (the user swiped them back, or the
        // device ignores the immersive request), reserve their height at the
        // top instead of letting the status bar draw over the address field.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            if (v.paddingTop != top) v.setPadding(0, top, 0, 0)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(rootView)
        contentFrame = rootView.getChildAt(2) as? FrameLayout
        mouseMode = com.hikari.app.tv.MouseMode.enabled
        desktopMode = getSharedPreferences("webview_prefs", MODE_PRIVATE)
            .getBoolean("desktop", false)
        if (mouseMode) showCursor(center = true)

        // Load the user's ad-blocking config (hosts lists are cached on disk;
        // lists the user never downloaded get fetched lazily here). Blocking
        // applies to WebView requests only — the ExoPlayer is untouched.
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            // Safety toggles from Settings.
            redirectProtection = app.store.webviewRedirect()
            popupProtection = app.store.webviewPopup()
            allowedRedirectHosts = app.store.webviewRedirectAllow().toSet() +
                com.hikari.app.data.RedirectAllow.now()
            // Popup exceptions (Settings → Privacy & Browsing → WebView safety).
            // One-time migration: older builds kept "always allow" in this
            // view's own preferences — fold them into the store once, so the
            // Settings list shows them and they can be reverted there.
            popupAllowHosts = runCatching { app.store.webviewPopupAllow() }.getOrDefault(emptyList())
                .map { it.trim().trimEnd('.').lowercase() }.filter { it.isNotBlank() }.toSet()
            popupBlockHosts = runCatching { app.store.webviewPopupBlock() }.getOrDefault(emptyList())
                .map { it.trim().trimEnd('.').lowercase() }.filter { it.isNotBlank() }.toSet()
            runCatching {
                val legacy = getSharedPreferences("webview_popup_allow", MODE_PRIVATE)
                    .getStringSet("hosts", emptySet()).orEmpty()
                    .map { it.trim().trimEnd('.').lowercase() }.filter { it.isNotBlank() }.toSet()
                if (legacy.isNotEmpty() && popupAllowHosts.isEmpty() && popupBlockHosts.isEmpty()) {
                    popupAllowHosts = legacy
                    app.store.setWebviewPopupAllow(legacy.toList())
                } else if (legacy.isNotEmpty()) {
                    val merged = (popupAllowHosts + legacy) - popupBlockHosts
                    if (merged != popupAllowHosts) {
                        popupAllowHosts = merged
                        app.store.setWebviewPopupAllow(merged.toList())
                    }
                }
            }
            val enabled = app.store.adEnabled()
            if (enabled) {
                val lists = app.store.adLists()
                val week = 7 * 24 * 60 * 60 * 1000L
                for (l in lists) {
                    // Lazy first download, and a weekly refresh after it. A
                    // blocklist that is never opened in Settings otherwise
                    // stays frozen at the day it was added — the "ad blocker
                    // is on but the new ad networks still get through" report.
                    val file = AdBlocker.cacheFile(app, l.url)
                    val stale = !file.exists() || file.length() == 0L ||
                        System.currentTimeMillis() - file.lastModified() > week
                    if (stale) {
                        runCatching { AdBlocker.download(l.url, app) }
                    }
                }
                val (blocked, white) = AdBlocker.resolve(app, lists, app.store.adBlock(), app.store.adWhite())
                blockedDomains = blocked
                whitelistDomains = white
            } else {
                blockedDomains = emptySet()
                whitelistDomains = emptySet()
            }
            // Userscripts (Tampermonkey-style, WebView only). Loaded here so
            // they're ready before the first page finishes; if the page already
            // started loading, inject the document-start scripts for it now.
            // pageUrl is read INSTEAD of webView.url: reading WebView state off
            // the main thread crashes (checkThread) — this coroutine runs on IO.
            UserscriptManager.reload(app)
            val startUrlNow = pageUrl
            if (startUrlNow != null) {
                val inject = UserscriptManager.scriptsFor(startUrlNow, atStart = true)
                if (inject.isNotEmpty()) {
                    runOnUiThread {
                        for (js in inject) runCatching { webView.evaluateJavascript(js, null) }
                    }
                }
            }
            // Persisted element-block selectors, applied by ELEMENT_BLOCK_JS on
            // every page load (see injectElementBlocker). The app-level cache
            // (HikariApp.elementBlocks) is already synced from disk at startup,
            // so blocks are ready BEFORE the first page loads — this is what
            // previously made them look "reset" after closing and reopening the
            // WebView (the async store read was racing the first onPageFinished).
            // The store re-read below is a safety net for a cold-start race; once
            // it lands we re-inject so an already-finished page still gets its
            // blocks applied.
            blockedSelectors.clear()
            blockedSelectors += app.elementBlocks
            runCatching { blockedSelectors += app.store.elementBlocks() }
            runOnUiThread { injectElementBlocker(webView) }
            // Per-extension auto-translate: on when the intent asked for it
            // explicitly ("Just this time") or the provider has "Always
            // translate" turned on.
            translateEnabled = forceTranslate ||
                (providerId != null && providerId in app.store.translateProviders())
            if (translateEnabled) {
                runOnUiThread { runCatching { webView.evaluateJavascript(TRANSLATE_JS, null) } }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    openTabs.size > 1 -> {
                        // On the home page of a tab: close this tab and switch to the nearest one.
                        closeTabAt(activeTabIndex)
                    }
                    else -> finish()
                }
            }
        })

        restoreActiveTab(recreated = savedInstanceState != null)
        setupWebView(startUrl)
        refreshBookmarks()
    }

    private fun setupWebView(startUrl: String) {
        val ws = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.setSupportMultipleWindows(true)
        ws.javaScriptCanOpenWindowsAutomatically = true
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        ws.loadWithOverviewMode = true
        ws.useWideViewPort = true
        ws.mediaPlaybackRequiresUserGesture = false
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.offscreenPreRaster = true
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setOnKeyListener { _, _, event -> handleTvRemote(event) }
        if (com.hikari.app.tv.TvMode.isTv) webView.requestFocus()
        // The WebView's own Safe Browsing check (default on modern Android;
        // set explicitly so a vendor build cannot quietly disable it).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { ws.safeBrowsingEnabled = true }
        }
        // UA comes from Settings → Privacy & Browsing → WebView user agent: stock Android default
        // (passes Cloudflare's JS challenge) unless the user overrides with a
        // custom one. See HikariApp.effectiveWebViewUa.
        ws.userAgentString = (application as HikariApp).effectiveWebViewUa()
        if (desktopMode) ws.userAgentString = DESKTOP_UA
        // Download links (a file host's "Download" button, a player's "save
        // video") used to do nothing at all: Android shows no browser chrome,
        // so there was no download manager to catch them. Route them through
        // the system DownloadManager with the page's cookies, referer and UA —
        // the three things hotlink-protected file hosts require.
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.isBlank()) return@setDownloadListener
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(
                    this,
                    I18n.t("This download is generated inside the page and can't be saved"),
                    Toast.LENGTH_SHORT,
                ).show()
                return@setDownloadListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                handleExternalScheme(Uri.parse(url), gesture = true)
                return@setDownloadListener
            }
            val name = runCatching {
                URLUtil.guessFileName(url, contentDisposition, mimetype)
            }.getOrDefault("download")
            runCatching {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(name)
                    setDescription(I18n.t("Downloaded by Lucid Hikari"))
                    mimetype?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                    )
                    addRequestHeader(
                        "User-Agent",
                        userAgent?.takeIf { it.isNotBlank() }
                            ?: (application as HikariApp).effectiveWebViewUa(),
                    )
                    CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                        addRequestHeader("Cookie", it)
                    }
                    pageUrl?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Referer", it) }
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    } else {
                        // API 24-28 would need WRITE_EXTERNAL_STORAGE for the
                        // public folder; the app's own external Download
                        // directory needs no permission and is still reachable
                        // from a file manager.
                        setDestinationInExternalFilesDir(
                            this@WebViewActivity,
                            Environment.DIRECTORY_DOWNLOADS,
                            name,
                        )
                    }
                }
                (getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, I18n.t("Download started") + " · $name", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, I18n.t("Couldn't start the download"), Toast.LENGTH_SHORT).show()
            }
        }

        // Cookies are the backbone of most streaming CDNs (session tokens) and
        // Cloudflare (cf_clearance). Accept them, accept THIRD-party ones (the
        // player page is site A, the CDN is site B), and flush to disk so they
        // survive activity restarts — this is what makes "works in my browser"
        // actually work in the app.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                // If the user tapped a link, they are actively browsing the site —
                // cancel verification auto-close so the view doesn't disappear.
                if (request.isForMainFrame && request.hasGesture()) {
                    autoCloseWhenCloudflarePassed = false
                }
                // A donation / "support the developer" page is never part of
                // watching something (see PromoGuard) — these are exactly the
                // links an extension hangs off its content, and once they load
                // they are a funding pitch with a progress bar and a pay
                // button. Refused here, before a single byte of them arrives.
                if (PromoGuard.isDonationUrl(request.url.toString())) {
                    showBlockedToast("Blocked a donation page")
                    return true
                }
                // An addon's configuration page ENDS by navigating to the
                // configured addon's own manifest — a stremio:// link the
                // WebView cannot follow, or a manifest URL. That navigation IS
                // the user pressing "Install", so it is taken here and handed
                // back to the caller, which installs/replaces the addon.
                if (addonConfig) {
                    val install = addonInstallUrl(request.url.toString())
                    if (install != null) {
                        finishWithManifest(install)
                        return true
                    }
                }
                // A non-web scheme (mailto:, tel:, market:, intent:, a TV
                // provider's custom sign-in scheme) is not a page the WebView
                // can render. Hand it to the system instead of showing
                // "net::ERR_UNKNOWN_URL_SCHEME" over a dead end.
                if (handleExternalScheme(request.url, request.hasGesture())) return true
                // The user already chose "Allow once" for exactly this URL in
                // the redirect/popup dialog. Let it through BOTH gates; the
                // dialog's own loadUrl used to be cancelled by the sibling
                // request check, which made "Allow once" a no-op.
                if (request.isForMainFrame && isOneShotAllowed(request.url.toString())) {
                    return false
                }
                // Video-verification mode (verifyAllowRedirects): the streaming
                // site's redirect to the real video page is the whole point —
                // skip ALL main-frame redirect blocking here. Ad hosts are
                // still blocked below and popups are still dropped, so it
                // can't spiral into ad sites.
                if (!verifyAllowRedirects && !addonConfig) {
                    // Cloudflare-verification mode: ONLY the CF challenge may ever
                    // load (the site + Cloudflare's challenge infra). Any other
                    // redirect target is cancelled before it loads — the view is
                    // open solely to pass the challenge, never to surf.
                    if (autoCloseWhenCloudflarePassed && request.isForMainFrame &&
                        !isVerifyAllowed(request.url.toString())
                    ) {
                        showBlockedToast(blockedRedirectToast(request.url))
                        rememberBlockedRedirect(request.url.toString())
                        return true
                    }
                    // Redirect protection: cancel main-frame navigations away from
                    // the site before they load (ad-hijack redirects). Same-site
                    // pages, subdomains, whitelisted hosts and user-allowed
                    // redirect hosts still work.
                    if (redirectProtection && request.isForMainFrame) {
                        val host = request.url.host
                        val cur = currentPageHost()
                        if (host != null && cur != null && host != cur &&
                            !AdBlocker.matches(host, whitelistDomains) &&
                            !isRedirectAllowed(request.url.toString()) &&
                            !isSameSite(host, cur)
                        ) {
                            // The USER tapped this link (a real gesture): it gets
                            // the Allow / Always / Block-always choice inline
                            // (see askRedirectPermission), not a bare toast and
                            // a trip to the ⋯ menu. Automatic redirects keep the
                            // silent block — they are never the user's tap.
                            if (request.hasGesture() || recentUserGesture()) {
                                // A tapped link: the user asked for this page, so
                                // it loads. Only AUTOMATIC redirects are
                                // cancelled — an ad hijack has no finger behind
                                // it, and the ad host/pattern gates above and
                                // below still apply.
                                return false
                            }
                            dbg("cancel-navigation " + request.url)
                            showBlockedToast(blockedRedirectToast(request.url))
                            rememberBlockedRedirect(request.url.toString())
                            return true
                        }
                    }
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val u = request.url.toString()
                val host = request.url.host ?: ""
                // Cloudflare's own challenge traffic is never ad traffic (see
                // isCloudflareInfra): letting the challenge's scripts, iframes
                // and /cdn-cgi/ endpoints load is what lets it COMPLETE. Block
                // or rewrite them and the page simply reloads itself into the
                // same challenge — the verify page looping instead of passing.
                if (isCloudflareInfra(u)) return null
                // Donation / funding pages are refused at the request level
                // too: a main-frame navigation that reached the network any
                // other way (a meta refresh, a script assignment, a redirect
                // chain) still gets an empty body instead of a donate card.
                if (request.isForMainFrame && PromoGuard.isDonationUrl(u)) {
                    dbg("block-donation " + u)
                    showBlockedToast("Blocked a donation page")
                    return WebResourceResponse(
                        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                    )
                }
                // An explicit "Allow once" outranks the redirect/ad gates (the
                // user just chose it), but never the donation guard above.
                if (request.isForMainFrame && isOneShotAllowed(u)) return null
                // The hls.js polyfill is loaded from these CDNs; a blocklist
                // that happens to name a public CDN must not take the video
                // engine's own dependency down with it.
                if (isAppAssetHost(host)) return null
                if (host.isNotBlank()) {
                    // Whitelist wins first — a site the user unblocked keeps
                    // all its subdomains usable.
                    if (AdBlocker.matches(host, whitelistDomains)) {
                        return null
                    }
                    val firstParty = currentPageHost()?.let { isSameSite(host, it) } == true ||
                        AdBlocker.isAlwaysAllowedHost(host)
                    if (!firstParty && AdBlocker.matches(host, blockedDomains)) {
                        dbg("ad-host " + host + " main=" + request.isForMainFrame)
                        // NEVER block actual media — blocklists routinely contain
                        // tube-site CDN domains (phncdn.com, …) that serve the
                        // actual video. Blocking those turns "video loads" into
                        // "video buffering forever" (the Pornhub-style stall).
                        if (!isMediaRequest(request)) {
                            if (request.isForMainFrame) {
                                // An ad GATE: follow the destination it carries
                                // instead of leaving an empty document.
                                val cur = currentPageHost()
                                val dest = adGateDestination(u)?.takeIf { d ->
                                    val dh = runCatching { java.net.URI(d).host }
                                        .getOrNull().orEmpty()
                                    // The site's own domain is allowed as a gate
                                    // destination even when a list carries it:
                                    // blocklists name streaming domains for
                                    // their ad subresources, not because the
                                    // page itself is an advert.
                                    !isAdHost(d) || (cur != null && isSameSite(dh, cur))
                                }
                                dbg("ad-gate " + host + " -> " + dest)
                                if (dest != null) return adGatePage(dest)
                                // A top-level DOCUMENT is never turned into a
                                // blank page. Rumble itself is in three ad lists
                                // (for its ad subresources), and blanking the
                                // page's own domain is what stopped Rumble from
                                // loading anything past the first page.
                                dbg("ad-gate allow-document " + host)
                                return null
                            }
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    // URL-pattern rules (the uBO-style half): catches what
                    // hosts cannot — ads from the site's OWN domain, ad
                    // subdomains no list names, rotating popunder hosts. Same
                    // guarantees as above: whitelist already won, media never
                    // blocked.
                    if (!request.isForMainFrame && !isMediaRequest(request) &&
                        AdBlocker.matchesPattern(request, currentPageHost())
                    ) {
                        dbg("ad-pattern " + u + " main=" + request.isForMainFrame)
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                // Only count it as a video if it actually IS one: probe the
                // content-type in the background so ad previews, gifs and
                // image placeholders never populate the chip.
                if (VIDEO_URL_RE.containsMatchIn(u)) {
                    maybeAddVideo(u)
                }
                // Video-verification mode: main-frame redirects are allowed
                // (the streaming site's redirect to the video page), but ad
                // subresources are still blocked above.
                if (!verifyAllowRedirects) {
                    // Cloudflare-verification mode: never let a redirect land on a
                    // foreign page while the view is just passing a challenge.
                    if (autoCloseWhenCloudflarePassed && request.isForMainFrame &&
                        !isVerifyAllowed(u)
                    ) {
                        showBlockedToast(blockedRedirectToast(request.url))
                        rememberBlockedRedirect(u)
                        return WebResourceResponse(
                            "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    // Redirect protection: never let an ad hijack navigate the main
                    // frame away to a foreign domain (the twinrdengine.com-class
                    // redirects). Same-site pages, subdomains, whitelisted hosts and
                    // user-allowed redirect hosts still navigate normally; turning
                    // the toggle off disables this entirely.
                    if (redirectProtection && request.isForMainFrame) {
                        val host = request.url.host
                        val cur = currentPageHost()
                        if (host != null && cur != null && host != cur &&
                            !AdBlocker.matches(host, whitelistDomains) &&
                            !isRedirectAllowed(u) &&
                            !isSameSite(host, cur)
                        ) {
                            // Same tap-choice as the override gate above: a
                            // user-gesture navigation asks inline, an
                            // automatic one is dropped silently.
                            if (request.hasGesture() || recentUserGesture()) {
                                // Same rule as the override gate: a finger on
                                // the page means the navigation is the user's,
                                // not an ad hijack's.
                                return null
                            }
                            dbg("cancel-request " + u + " main=" + request.isForMainFrame)
                            showBlockedToast(blockedRedirectToast(request.url))
                            rememberBlockedRedirect(u)
                            return WebResourceResponse(
                                "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }
                }
                return null
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                // Only the page itself, never an ad iframe or a failed poster:
                // those are expected and the page still works around them.
                if (request?.isForMainFrame != true) return
                val description = runCatching { error?.description?.toString() }.getOrNull()
                showLoadError(request.url?.toString(), description)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                dbg("start " + url)
                syncToolbar(url)
                // A full navigation starts a new page: sources detected on the
                // previous one are not this page's. SPA route changes do not
                // fire onPageStarted, so a client-side page keeps its list.
                if (url != null && !url.startsWith("about:")) {
                    detectedVideos.clear()
                    videoScores.clear()
                    videoChip.visibility = View.GONE
                    pagePreviewUrl = null
                    pagePreviewTitle = ""
                }
                pageUrl = url
                pageHostCache = runCatching { url?.let { java.net.URI(it).host?.lowercase() } }.getOrNull()
                loadFailed = false
                errorPanel?.visibility = View.GONE
                // A new navigation invalidates the capture still queued for the
                // page that was on screen (see scheduleScreenshot).
                shotToken++
                shotHandler.removeCallbacksAndMessages(null)
                detectedVideos.clear()
                autoLaunched = false
                scanHandler.removeCallbacksAndMessages(null)
                progressBar.visibility = View.VISIBLE
                blockedToastShown = false
                // The "allow the redirect that was just blocked" offer belongs
                // to the page it was made on (see [lastBlockedRedirectUrl]).
                lastBlockedRedirectUrl = null
                lastBlockedRedirectHost = null
                // Same for "Block this ad site": it must never point at a popup
                // from a page the user has already left.
                lastPopupUrl = null
                lastPopupHost = null
                if (!autoCloseWhenCloudflarePassed && !addonConfig) noteVisited(url)
                // Userscripts declaring @run-at document-start run before the
                // page's own scripts.
                if (UserscriptManager.isLoaded() && url != null) {
                    for (js in UserscriptManager.scriptsFor(url, atStart = true)) {
                        runCatching { view?.evaluateJavascript(js, null) }
                    }
                }
                // Ad containers can be in the first paint; start hiding them
                // now instead of waiting for onPageFinished. POPUP_GUARD_JS is
                // idempotent and is also injected on finish in case the very
                // first document was not ready here.
                if (!autoCloseWhenCloudflarePassed && !addonConfig) {
                    runCatching { view?.evaluateJavascript(COMPAT_JS, null) }
                    runCatching { view?.evaluateJavascript(POPUP_GUARD_JS, null) }
                    runCatching { view?.evaluateJavascript(SNIFFER_JS, null) }
                }
                // A measured, sustained video is the only thing allowed to keep
                // the screen awake; clear the stale flag from the last page.
                setKeepScreenOn(false)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageUrl = url
                pageHostCache = runCatching { url?.let { java.net.URI(it).host?.lowercase() } }.getOrNull()
                progressBar.visibility = View.GONE
                // A failed load also finishes. Keep the retry panel up instead
                // of letting the blank error document hide it.
                if (loadFailed) return
                noteVisited(url)
                refreshBookmarks()
                if (url != null && isOneShotAllowed(url)) allowedOnceUrl = null
                // The popup relay child (if any) served its purpose — free it.
                popupChild?.let { runCatching { it.destroy() } }
                popupChild = null
                if (UserscriptManager.isLoaded() && url != null) {
                    for (js in UserscriptManager.scriptsFor(url, atStart = false)) {
                        runCatching { view?.evaluateJavascript(js, null) }
                    }
                }
                syncToolbar(url)
                loadPagePreview(view)
                view?.evaluateJavascript(AD_CLEAN_JS, null)
                view?.evaluateJavascript(PROMO_CLEAN_JS, null)
                if (!autoCloseWhenCloudflarePassed && !addonConfig) {
                    runCatching { view?.evaluateJavascript(SNIFFER_JS, null) }
                }
                if (!autoCloseWhenCloudflarePassed && !addonConfig) {
                    view?.evaluateJavascript(POPUP_GUARD_JS, null)
                }
                // The verification view exists only to pass a Cloudflare
                // challenge — it must never offer to play or hand off video
                // (the site page would just start its player + ads).
                if (autoCloseWhenCloudflarePassed) {
                    videoChip.visibility = View.GONE
                    if (translateEnabled) view?.evaluateJavascript(TRANSLATE_JS, null)
                    injectElementBlocker(view)
                    pollCloudflarePass()
                    return
                }
                view?.evaluateJavascript(VIDEO_POLYFILL_JS, null)
                view?.evaluateJavascript(STUCK_MONITOR_JS, null)
                if (translateEnabled) view?.evaluateJavascript(TRANSLATE_JS, null)
                injectElementBlocker(view)
                // Some players create <video> from JS without a network URL we
                // can see — scan the DOM for the real element too. The runnable
                // keeps re-scanning so single-page-app players (which swap the
                // player without a full navigation) still light the chip.
                scanRunnable?.let { scanHandler.removeCallbacks(it) }
                val r = object : Runnable {
                    override fun run() {
                        val v = webView
                        if (v == null) return
                        v.evaluateJavascript(STUCK_MONITOR_JS, null)
                        v.evaluateJavascript(VIDEO_SCAN_JS) { res ->
                            for (u in extractUrls(res)) maybeAddVideo(u)
                        }
                        v.evaluateJavascript(VIDEO_PLAYING_JS) { res ->
                            val playing = res?.trim()?.trim('"') == "true"
                            videoPlaying = playing
                            setKeepScreenOn(playing)
                        }
                        scanHandler.postDelayed(this, 2500)
                    }
                }
                scanRunnable = r
                scanHandler.postDelayed(r, 1500)
                scheduleScreenshot(view, url)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // A verification view is never relaunched: it exists only to
                // pass a challenge, and a Cloudflare page that just killed the
                // renderer will kill it again on the next load — relaunching
                // turned that into a loop of verify views opening one after
                // another. Close instead and leave the user in control.
                if (autoCloseWhenCloudflarePassed) {
                    verifyDone = true
                    runCatching { finish() }
                    return true
                }
                // The renderer crashed (often a heavyweight site) — relaunch the
                // activity instead of showing a dead white screen. Bounded: a
                // page that crashes the renderer once will do it every time, so
                // one retry, then close rather than reopened-forever.
                if (renderRestarts >= MAX_RENDER_RESTARTS) {
                    runCatching { finish() }
                    return true
                }
                renderRestarts++
                runCatching {
                    finish()
                    startActivity(Intent(intent).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("renderRestarts", renderRestarts)
                        // Return to the page that crashed, not the one the
                        // activity was first launched with.
                        putExtra("url", pageUrl ?: startUrl)
                        putExtra("title", pageTitle)
                    })
                }
                return true
            }
        })

        webView.webChromeClient = object : WebChromeClient() {
            /**
             * Page dialogs that appear on their own.
             *
             * A video site fires `alert()`/`confirm()` from load scripts and
             * from "are you still watching" timers; each one is a modal over
             * the page that the user did not ask for, and one of them ("This
             * page says: …") can wedge a player that only dismisses on OK. A
             * dialog with no recent touch behind it is answered for the user;
             * one they triggered keeps the platform dialog.
             */
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?,
            ): Boolean {
                if (!recentUserGesture() || videoPlaying) {
                    dbg("suppress-alert " + url)
                    result?.confirm()
                    return true
                }
                return false
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?,
            ): Boolean {
                if (!recentUserGesture() || videoPlaying) {
                    dbg("suppress-confirm " + url)
                    result?.cancel()
                    return true
                }
                return false
            }

            override fun onJsBeforeUnload(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?,
            ): Boolean {
                if (!recentUserGesture() || videoPlaying) {
                    result?.confirm()
                    return true
                }
                return false
            }

            override fun onCloseWindow(window: WebView?) {
                // Prevent ad scripts / popunders from closing the main WebView via window.close()
                if (window === popupChild) {
                    runCatching { popupChild?.destroy() }
                    popupChild = null
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // Cloudflare-verification view: NEVER relay popups. It is only
                // open to pass the challenge, and these streaming pages' players
                // use window.open to pop ads the moment you click the video.
                if (autoCloseWhenCloudflarePassed) return false
                // Popup protection: a window.open that is NOT backed by a user
                // gesture is an auto-popunder — site scripts fire these on page
                // load and on the first stray tap, which is exactly the "popup
                // ad appears after the site loads" report. Only a
                // gesture-driven window.open (a real click, e.g. a player's
                // "open in new window") is relayed.
                //
                // An addon's config page is exempt: it is user-driven setup,
                // not a video site, and its login buttons (Google "Sign in"
                // via GIS, debrid API-key flows) pop their windows WITHOUT a
                // gesture the WebView credits — the script opens them from a
                // cross-origin frame, so isUserGesture arrives false and the
                // page sat on "Waiting for Google..." forever with the popup
                // dropped. Same-site/video gates below still apply to
                // everything that is not a config page.
                if (popupProtection && !isUserGesture && !recentUserGesture() && !addonConfig) {
                    showBlockedToast("Blocked popup")
                    return false
                }
                // While a video is actively playing, a window.open popup is an
                // ad (players pop them on click) — relaying it into the main
                // view would replace the playing video with the ad page.
                if (videoPlaying) {
                    showBlockedToast("Blocked popup during playback")
                    return false
                }
                // Route popups (some players open in window.open) into this
                // same web view so video keeps working. The transport CANNOT
                // target the parent WebView itself — Android throws
                // "Parent WebView cannot host its own popup window". Instead a
                // throwaway child WebView captures the popup's URL and loads it
                // in the main view.
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val child = WebView(this@WebViewActivity).apply {
                    setBackgroundColor(0xFF000000.toInt())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        if (desktopMode) DESKTOP_UA
                        else (application as HikariApp).effectiveWebViewUa()
                    webViewClient = object : WebViewClient() {
                        private var relayed = false

                        private fun relay(url: String?) {
                            if (relayed || url.isNullOrBlank()) return
                            // An about:blank popup is the blank window an ad
                            // script opens before writing its page into it.
                            // Relaying the blank URL replaced the page the user
                            // was watching with a white screen. Leave the popup
                            // child alive: if the SAME window later navigates to
                            // a real URL, that navigation is relayed normally.
                            if (!addonConfig && (url == "about:blank" || url.startsWith("javascript:"))) {
                                return
                            }
                            relayed = true
                            dbg("relay-popup " + url)
                            runCatching {
                                lastPopupUrl = url
                                lastPopupHost = runCatching { java.net.URI(url).host }
                                    .getOrNull()?.trim()?.trimEnd('.')?.lowercase()
                            }
                            // A config page's "install" popup is the install
                            // signal, exactly like a main-frame navigation to it
                            // (see addonInstallUrl).
                            if (addonConfig) {
                                addonInstallUrl(url)?.let { finishWithManifest(it) }
                                return
                            }
                            // A popup to a non-web scheme is a sign-in/app
                            // hand-off, not a page to relay into this view.
                            if (handleExternalScheme(Uri.parse(url), gesture = true)) return
                            // A popup to a donation page is an ad-style popup
                            // with a cause: never relayed into the main view.
                            if (PromoGuard.isDonationUrl(url)) {
                                showBlockedToast("Blocked a donation page")
                                return
                            }
                            // A popup to an ad-pattern URL is an ad popup, not
                            // a choice: dropped outright instead of asking
                            // Allow once / Always for it (the deputize…-class
                            // popunders from the user's own screenshot). Same
                            // guarantees as request blocking — whitelisted
                            // hosts are checked before this is ever reached…
                            // (checked below via the same whitelistDomains).
                            if (popupProtection && !addonConfig) {
                                val ph = runCatching { java.net.URI(url).host }
                                    .getOrNull()?.trim()?.trimEnd('.').orEmpty()
                                if (ph.isNotEmpty() &&
                                    !AdBlocker.matches(ph, whitelistDomains) &&
                                    AdBlocker.matchesPatternUrl(url, currentPageHost())
                                ) {
                                    showBlockedToast("Blocked ad popup")
                                    return
                                }
                            }
                            // Popup protection: only relay popups that belong to
                            // the site (same host/subdomain or whitelisted).
                            // about:blank popunders and foreign ad popups are
                            // dropped entirely.
                            //
                            // An extension's settings page is user-driven
                            // setup, not a video site: its login buttons (a
                            // Google "Sign in", a debrid API-key flow) pop
                            // windows on FOREIGN hosts (accounts.google.com)
                            // that the same-site gate below would drop — and
                            // the page then sits on "Waiting for Google..."
                            // forever, which is exactly the reported hang.
                            // Relay them so the login can finish like in a
                            // real browser (the popup URL loads in this view,
                            // the provider redirects back to the settings
                            // page signed in).
                            //
                            // A tap-opened popup that still fails this gate is
                            // not dropped with a bare toast any more: the tap
                            // gets its choice (Allow once / Always / Cancel —
                            // see askPopupPermission), and a site already
                            // allowed opens straight through.
                            if (popupProtection && !addonConfig) {
                                val host = runCatching { java.net.URI(url).host }.getOrNull()
                                val cur = currentPageHost()
                                // "Block always" wins over everything: dropped
                                // silently, never asked again (revert it in
                                // Settings → Privacy & Browsing → WebView safety).
                                if (!host.isNullOrBlank() && isPopupHostBlocked(host)) {
                                    showBlockedToast("Blocked popup from $host")
                                    return
                                }
                                if (host == null || cur == null ||
                                    (host != cur && !AdBlocker.matches(host, whitelistDomains) && !isSameSite(host, cur))
                                ) {
                                    // A popup opened by the user's own tap is the
                                    // player (Kwik, Dood, Streamtape…) or a
                                    // sign-in hand-off — load it. The dialog was
                                    // an extra step that often never appeared
                                    // because the gesture flag had already been
                                    // lost. Automatic popunders still hit the
                                    // !recentUserGesture() gate above.
                                    if (isPopupHostAllowed(host) || recentUserGesture()) {
                                        allowOnce(url)
                                        runOnUiThread { webView.loadUrl(url) }
                                    } else {
                                        askPopupPermission(url, host) { allowed ->
                                            allowOnce(allowed)
                                            runOnUiThread { webView.loadUrl(allowed) }
                                        }
                                    }
                                    return
                                }
                            }
                            runOnUiThread { webView.loadUrl(url) }
                        }

                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            relay(request?.url?.toString())
                            return true
                        }

                        override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                            relay(url)
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            // The throwaway popup renderer died — reclaim it and
                            // keep the browsing activity (and the app) alive.
                            runCatching { if (popupChild === view) popupChild = null }
                            runCatching { view?.destroy() }
                            return true
                        }
                    }
                }
                popupChild = child
                transport.webView = child
                resultMsg?.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback = filePathCallback
                val intent = runCatching { fileChooserParams?.createIntent() }.getOrNull()
                return if (intent != null) {
                    try {
                        startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                        true
                    } catch (e: Exception) {
                        fileChooserCallback = null
                        false
                    }
                } else {
                    fileChooserCallback = null
                    false
                }
            }

            override fun onShowCustomView(
                view: View?,
                callback: CustomViewCallback?
            ) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                view?.let { v ->
                    rootView.addView(
                        v,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    )
                }
                setKeepScreenOn(true)
                hideSystemBars()
            }

            override fun onHideCustomView() {
                hideCustomView()
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                val clean = title?.trim().orEmpty()
                if (clean.isBlank() || clean.startsWith("about:") || clean.startsWith("data:")) return
                pageTitle = clean
                noteVisited(view?.url ?: pageUrl, clean)
            }
        }

        webView.loadUrl(pageUrl ?: startUrl)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER_REQUEST) return
        val cb = fileChooserCallback ?: return
        fileChooserCallback = null
        if (resultCode == RESULT_OK && data != null) {
            val results = if (data.clipData != null) {
                (0 until data.clipData!!.itemCount).mapNotNull { data.clipData!!.getItemAt(it).uri }
            } else {
                data.data?.let { listOf(it) }.orEmpty()
            }
            cb.onReceiveValue(results.toTypedArray())
        } else {
            cb.onReceiveValue(null)
        }
    }

    private fun hideCustomView() {
        customView?.let { v -> rootView.removeView(v) }
        customView = null
        customViewCallback?.let { runCatching { it.onCustomViewHidden() } }
        customViewCallback = null
        setKeepScreenOn(videoPlaying)
    }

    /**
     * Shows the load-failure panel for a main-frame error and remembers the
     * URL so Retry reloads the page the user actually asked for.
     *
     * Verification views never show it: their only job is to pass a challenge,
     * and a challenge that could not load closes rather than covering itself
     * in app UI the caller cannot dismiss.
     */
    private fun showLoadError(url: String?, description: String? = null) {
        if (autoCloseWhenCloudflarePassed) return
        val failed = url?.takeIf { it.isNotBlank() } ?: pageUrl ?: startUrl
        if (failed.startsWith("about:") || failed.startsWith("data:") ||
            failed.startsWith("javascript:")
        ) return
        lastFailedUrl = failed
        loadFailed = true
        errorDetail?.text = buildString {
            append(I18n.t("Check your connection and try again."))
            if (!description.isNullOrBlank()) append("\n").append(description)
        }
        errorPanel?.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
    }

    /** Retry the page that failed, without leaving the WebView. */
    private fun retryLoad() {
        errorPanel?.visibility = View.GONE
        loadFailed = false
        runCatching {
            webView.loadUrl(lastFailedUrl ?: pageUrl ?: startUrl)
        }
    }

    // The ⋯ pill opens this tiny menu — the app draws NO header bar, so the
    // site's own header/search stay fully visible.
    private fun showMenu(anchor: View) {
        val menu = android.widget.PopupMenu(this, anchor, Gravity.END)
        menu.menu.add(0, 1, 0, "\u2190 Back")
        menu.menu.add(0, 2, 0, "\u2192 Forward")
        menu.menu.add(0, 3, 0, "\u21BB Reload")
        menu.menu.add(0, 4, 0, "\u2302 Go to app home")
        menu.menu.add(0, 5, 0, "\u25B6 Open in player")
        menu.menu.add(0, 6, 0, "Open in browser")
        menu.menu.add(0, 15, 4, if (desktopMode) "\uD83D\uDCF1 Mobile site" else "\uD83D\uDDA5 Desktop site")
        // The way out of a blocked redirect, without a trip to Settings. The
        // host of the main-frame navigation that was just refused (see
        // [rememberBlockedRedirect]) is added to Settings' own "Allowed redirect
        // links" and the page is opened — so a link the redirect protection took
        // for an ad hijack, or an extension's rotating file host, is one tap.
        // Offered only while there is a refused link to offer.
        val blockedHost = lastBlockedRedirectHost
        if (blockedHost != null && blockedHost.isNotBlank()) {
            menu.menu.add(0, 11, 6, "✔ Allow redirect to $blockedHost")
        }
        // "Block this ad site": the ad host behind the last popup or the last
        // blocked redirect — the thing that opened (or tried to open) the ad
        // page. Offered only for a FOREIGN host, never for the site being
        // browsed: blocking the site's own domain would break the site
        // itself (the Cindy-style overlay lives on the page, use the Element
        // blocker for that). One tap adds it to the manual blocklist AND to
        // the popup block-always list, so its requests never load and its
        // popups never ask again. Revert it in Settings.
        val adHost = (lastPopupHost?.takeIf { it.isNotBlank() }
            ?: lastBlockedRedirectHost?.takeIf { it.isNotBlank() })
            ?.trim()?.trimEnd('.')?.lowercase()
        val curHost = currentPageHost()?.trim()?.trimEnd('.')?.lowercase().orEmpty()
        if (!adHost.isNullOrBlank() && adHost != curHost) {
            menu.menu.add(0, 14, 6, "⛔ Block this ad site ($adHost)")
        }
        menu.menu.add(0, 7, 7, "\u2298 Element blocker")
        menu.menu.add(0, 8, 8, "\u21A9 Undo last block")
        menu.menu.add(0, 9, 9, "\u2715 Clear all blocks")
        if (translateEnabled) menu.menu.add(0, 10, 10, "\u2716 Translation off")
        menu.menu.add(0, 12, 12, if (mouseMode) "\u2716 Mouse cursor off" else "\u2316 Mouse cursor on")
        // Save this page as a Website (persisted to the Websites tab).
        menu.menu.add(0, 20, 20, "\u2764\uFE0F Save this site")
        // Open the tab grid so the user can switch / close tabs.
        menu.menu.add(0, 21, 21, "\uD83D\uDD17 Tabs (\u200A${openTabs.size}\u200B)")
        // Per-site adblock switch, instant: a site the blocker breaks goes on
        // the whitelist from here (and back off with the same tap), no trip
        // to Settings. The in-memory set is swapped and the page reloaded, so
        // the new rule applies to the very next load.
        val menuHost = currentPageHost()?.trim()?.trimEnd('.').orEmpty()
        if (menuHost.isNotBlank()) {
            val siteOpen = AdBlocker.matches(menuHost, whitelistDomains)
            menu.menu.add(
                0, 13, 11,
                if (siteOpen) "\u2714 Ads allowed on $menuHost — tap to block"
                else "\u2298 Allow ads on $menuHost",
            )
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> if (webView.canGoBack()) webView.goBack()
                2 -> if (webView.canGoForward()) webView.goForward()
                3 -> webView.reload()
                4 -> {
                    // Leave the site view and jump the app back to its Home tab
                    // (NOT the website's home page — AppRoot watches this).
                    HikariApp.instance.homeTabRequest.value += 1
                    finish()
                }
                5 -> playVideo()
                6 -> runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl ?: webView.url)))
                }
                15 -> {
                    desktopMode = !desktopMode
                    getSharedPreferences("webview_prefs", MODE_PRIVATE).edit()
                        .putBoolean("desktop", desktopMode).apply()
                    webView.settings.userAgentString =
                        if (desktopMode) DESKTOP_UA
                        else (application as HikariApp).effectiveWebViewUa()
                    webView.reload()
                    Toast.makeText(
                        this,
                        if (desktopMode) I18n.t("Desktop site requested")
                        else I18n.t("Mobile site requested"),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                7 -> enableElementBlocker()
                8 -> undoLastBlock()
                9 -> clearAllBlocks()
                10 -> {
                    // Per-extension auto-translate off (menu is only offered
                    // while a translation is active on this page).
                    translateEnabled = false
                    val pid = providerId
                    if (pid != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            runCatching { com.hikari.app.data.Translator.enable(pid, false) }
                        }
                    }
                    Toast.makeText(this, I18n.t("Translation off"), Toast.LENGTH_SHORT).show()
                }
                11 -> allowBlockedRedirect()
                12 -> toggleMouse()
                13 -> toggleSiteAdblock()
                14 -> blockAdSite()
                20 -> saveCurrentSite()
                21 -> showTabGrid()
            }
            true
        }
        menu.show()
    }

    /**
     * Shows a full-screen dialog listing all open tabs as cards with favicon
     * thumbnails. The user can tap a card to switch to it, or tap the X to close
     * a tab. The active tab is highlighted.
     */
    private fun showTabGrid() {
        if (openTabs.isEmpty()) return
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_tabs_grid)
        val list = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.tabs_list)
        val closeBtn = dialog.findViewById<android.widget.ImageView>(R.id.tabs_close)
        closeBtn?.setOnClickListener { dialog.dismiss() }
        if (list != null) {
            val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                inner class TabGridHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
                    val favicon: android.widget.ImageView = view.findViewById(R.id.item_tab_favicon)
                    val title: android.widget.TextView = view.findViewById(R.id.item_tab_title)
                    val close: android.widget.ImageView = view.findViewById(R.id.item_tab_close)
                }

                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                    val v = android.view.LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_tabs_grid, parent, false)
                    return TabGridHolder(v)
                }

                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                    val gridHolder = holder as TabGridHolder
                    val tab = openTabs[position]
                    gridHolder.title.text = tab.title
                    gridHolder.favicon.load(tab.faviconUrl) {
                        crossfade(true)
                    }
                    gridHolder.itemView.setBackgroundColor(
                        if (position == activeTabIndex)
                            0x33FFFFFF.toInt()
                        else
                            0x00000000.toInt()
                    )
                    gridHolder.itemView.setOnClickListener {
                        activeTabIndex = position
                        saveTabs()
                        switchToActiveTab()
                        dialog.dismiss()
                    }
                    gridHolder.close.setOnClickListener {
                        closeTabAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, openTabs.size)
                    }
                    gridHolder.close.contentDescription = I18n.t("Close tab")
                }
                override fun getItemCount() = openTabs.size
            }
            list.adapter = adapter
            val columns = (resources.displayMetrics.widthPixels / dp(240)).coerceIn(2, 4)
            list.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, columns)
        }
        dialog.show()
    }

    /**
     * The ⋯ menu's "Block this ad site" (see [showMenu]): the ad host behind
     * the last popup or blocked redirect goes on the manual blocklist (its
     * requests never load again) AND on the popup block-always list (its
     * popups never ask again). Both lists are the same ones Settings edits,
     * so a mistaken block is reverted there, not here.
     */
    private fun blockAdSite() {
        val host = (lastPopupHost?.takeIf { it.isNotBlank() }
            ?: lastBlockedRedirectHost?.takeIf { it.isNotBlank() })
            ?.trim()?.trimEnd('.')?.lowercase()
        if (host.isNullOrBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            val norm = AdBlocker.normalizeDomain(host)
            runCatching {
                val cur = app.store.adBlock()
                if (cur.none { AdBlocker.normalizeDomain(it) == norm }) {
                    runCatching { app.store.setAdBlock((cur + norm).distinct()) }
                }
                val blk = app.store.webviewPopupBlock().toMutableSet()
                if (blk.add(norm)) app.store.setWebviewPopupBlock(blk.toList())
                val allow = app.store.webviewPopupAllow().toMutableSet()
                if (allow.remove(norm)) app.store.setWebviewPopupAllow(allow.toList())
            }
            val (blocked, white) = runCatching {
                AdBlocker.resolve(app, app.store.adLists(), app.store.adBlock(), app.store.adWhite())
            }.getOrDefault(blockedDomains to whitelistDomains)
            blockedDomains = blocked
            whitelistDomains = white
            popupBlockHosts = popupBlockHosts + norm
            popupAllowHosts = popupAllowHosts - norm
            runOnUiThread {
                Toast.makeText(
                    this@WebViewActivity,
                    "Blocked $host — ads and popups from it will not load. Revert in Settings → Privacy.",
                    Toast.LENGTH_LONG,
                ).show()
                webView.reload()
            }
        }
    }

    /**
     * The ⋯ menu's per-site adblock switch (see [showMenu]): flips the current
     * site on/off the Settings whitelist, swaps the in-memory set this page
     * checks, and reloads — so the change bites on the very next load, not on
     * the next visit. Subdomains ride along (the whitelist matches them too).
     */
    private fun toggleSiteAdblock() {
        val host = currentPageHost()?.trim()?.trimEnd('.')?.lowercase()
        if (host.isNullOrBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            val norm = AdBlocker.normalizeDomain(host)
            val cur = app.store.adWhite()
            val has = cur.any { AdBlocker.normalizeDomain(it) == norm }
            val next = if (has) cur.filterNot { AdBlocker.normalizeDomain(it) == norm }
            else cur + norm
            runCatching { app.store.setAdWhite(next) }
            whitelistDomains =
                next.map { AdBlocker.normalizeDomain(it) }.filter { it.isNotBlank() }.toSet()
            runOnUiThread {
                Toast.makeText(
                    this@WebViewActivity,
                    if (has) "Blocking ads on $host" else "Ads allowed on $host",
                    Toast.LENGTH_SHORT,
                ).show()
                webView.reload()
            }
        }
    }

    /**
     * "Allow redirect to <host>" — the ⋯ menu's own way out of a blocked
     * redirect.
     *
     * The host of the link that was just refused is added to the user's
     * "Allowed redirect links" list — the SAME list Settings edits, so the entry
     * is visible and removable there and covers every later navigation to it
     * (and to its subdomains). The refused page is then loaded straight away, so
     * the link the user clicked finally lands.
     *
     * Saved through [com.hikari.app.data.AppStore.setWebviewRedirectAllow],
     * which writes the in-memory mirror BEFORE the DataStore write, so this very
     * navigation — and any a page makes while it loads — already sees the entry
     * (see RedirectAllow for why that ordering matters).
     */
    private fun toggleMouse() {
        mouseMode = !mouseMode
        com.hikari.app.tv.MouseMode.enabled = mouseMode
        if (mouseMode) {
            showCursor(center = true)
            Toast.makeText(this, I18n.t("Mouse on — arrows move the ring, OK taps the page"), Toast.LENGTH_SHORT).show()
        } else {
            hideCursor()
        }
    }

    private fun cursorStep(): Int = dp(44)

    private fun showCursor(center: Boolean) {
        val frame = contentFrame ?: return
        hideCursor()
        val ring = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setStroke(dp(3), 0xFFFF5252.toInt())
            setColor(android.graphics.Color.TRANSPARENT)
        }
        val size = dp(52)
        val v = View(this).apply { background = ring }
        if (center) {
            cursorX = (frame.width / 2f).takeIf { it > 0 } ?: dp(400).toFloat()
            cursorY = (frame.height / 3f).takeIf { it > 0 } ?: dp(400).toFloat()
        }
        cursorX = cursorX.coerceIn(0f, (frame.width - size).coerceAtLeast(0).toFloat())
        cursorY = cursorY.coerceIn(0f, (frame.height - size).coerceAtLeast(0).toFloat())
        frame.addView(v, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = cursorX.toInt()
            topMargin = cursorY.toInt()
        })
        cursorView = v
    }

    private fun hideCursor() {
        cursorView?.let { runCatching { contentFrame?.removeView(it) } }
        cursorView = null
    }

    private fun moveCursor(dx: Int, dy: Int) {
        val frame = contentFrame ?: return
        val v = cursorView ?: run { showCursor(center = true); return }
        val size = dp(52)
        cursorX = (cursorX + dx * cursorStep()).coerceIn(0f, (frame.width - size).coerceAtLeast(0).toFloat())
        cursorY = (cursorY + dy * cursorStep()).coerceIn(0f, (frame.height - size).coerceAtLeast(0).toFloat())
        (v.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.leftMargin = cursorX.toInt()
            lp.topMargin = cursorY.toInt()
            v.layoutParams = lp
        }
    }

    private fun tapCursor() {
        val v = cursorView ?: return
        val size = dp(52) / 2f
        val x = cursorX + size
        val y = cursorY + size
        val down = android.os.SystemClock.uptimeMillis()
        val downEv = android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val upEv = android.view.MotionEvent.obtain(down, down + 90, android.view.MotionEvent.ACTION_UP, x, y, 0)
        runCatching { webView.dispatchTouchEvent(downEv) }
        runCatching { webView.dispatchTouchEvent(upEv) }
        downEv.recycle()
        upEv.recycle()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN ||
            ev.actionMasked == android.view.MotionEvent.ACTION_UP
        ) {
            lastUserTouchAt = android.os.SystemClock.uptimeMillis()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (mouseMode && event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> { moveCursor(0, -1); return true }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { moveCursor(0, 1); return true }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { moveCursor(-1, 0); return true }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursor(1, 0); return true }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> { tapCursor(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun allowBlockedRedirect() {
        val url = lastBlockedRedirectUrl ?: return
        // The list names HOSTS or words; a link with no host (a data:/blob:
        // navigation, a bare path) has nothing that can be expressed on it.
        val entry = lastBlockedRedirectHost?.takeIf { it.isNotBlank() } ?: return
        lastBlockedRedirectUrl = null
        lastBlockedRedirectHost = null
        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext as HikariApp
            val current = runCatching { app.store.webviewRedirectAllow() }.getOrDefault(emptyList())
            val next = (current + entry).distinct()
            runCatching { app.store.setWebviewRedirectAllow(next) }
            runOnUiThread {
                allowedRedirectHosts = allowedRedirectHosts + entry
                Toast.makeText(
                    this@WebViewActivity,
                    "Allowed redirect to $entry",
                    Toast.LENGTH_SHORT,
                ).show()
                runCatching { webView.loadUrl(url) }
            }
        }
    }

    // ---- Element blocker ----

    private fun enableElementBlocker() {
        // Toggle: ON selects (red highlight), a 2nd tap on the same element
        // blocks it; OFF clears the highlight. Reads the state back so the
        // toast always matches reality.
        webView.evaluateJavascript(
            "(window.__hikariBlockerToggle?window.__hikariBlockerToggle():null);" +
                "(window.__hikariBlockerActive?'true':'false')"
        ) { res ->
            val active = res?.trim()?.trim('"') == "true"
            Toast.makeText(
                this@WebViewActivity,
                if (active) I18n.t("Blocker ON — tap an element to select it, tap it again to block")
                else I18n.t("Element blocker OFF"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun undoLastBlock() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sel = runCatching { (applicationContext as HikariApp).store.removeLastElementBlock() }
                .getOrNull()
            runOnUiThread {
                if (sel == null) {
                    Toast.makeText(this@WebViewActivity, I18n.t("Nothing to undo"), Toast.LENGTH_SHORT).show()
                } else {
                    blockedSelectors.remove(sel)
                    (applicationContext as HikariApp).elementBlocks = blockedSelectors.toList()
                    webView.evaluateJavascript(
                        "window.__hikariRestoreSelector?window.__hikariRestoreSelector(" +
                            jsString(sel) + "):null",
                        null
                    )
                    Toast.makeText(this@WebViewActivity, I18n.t("Last block reverted"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearAllBlocks() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { (applicationContext as HikariApp).store.clearElementBlocks() }
            runOnUiThread {
                blockedSelectors.clear()
                (applicationContext as HikariApp).elementBlocks = emptyList()
                webView.evaluateJavascript(
                    "window.__hikariRestoreAll?window.__hikariRestoreAll():null",
                    null
                )
                Toast.makeText(this@WebViewActivity, I18n.t("All element blocks cleared"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** A JS string literal (for embedding a selector into evaluateJavascript). */
    private fun jsString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /**
     * Translates one string into English via Google's free gtx endpoint (no
     * API key, CORS-free since the app does the request server-side). Returns
     * the original text when nothing could be translated so the page keeps
     * working even if translation is unavailable.
     */
    private fun fetchTranslation(text: String): String {
        if (text.isBlank()) return text
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx" +
            "&sl=auto&tl=en&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8")
        return try {
            val body = Http.get(url).use { it.body?.string() } ?: return text
            JSONArray(body).optJSONArray(0)?.optJSONArray(0)?.optString(0) ?: text
        } catch (e: Exception) {
            text
        }
    }

    /** Injects the blocker script + the persisted selectors into the page. */
    private fun injectElementBlocker(view: WebView?) {
        // Never touch a Cloudflare-verification page. The element blocker is
        // built for ad iframes/overlays, and a challenge's widget lives in an
        // iframe of its own — hiding the wrong node (or the wrapper the whole
        // challenge renders into) leaves Cloudflare's script to re-render and
        // re-request the challenge, which is exactly the "the verification page
        // keeps opening in a loop" report. The verify view has no ads to block
        // anyway: it is one page shown solely to pass the check.
        if (autoCloseWhenCloudflarePassed) return
        view?.evaluateJavascript(ELEMENT_BLOCK_JS, null)
        // In-page ad overlays (see INTERSTITIAL_KILL_JS). Separate from the
        // element blocker because it has nothing to do with the user's saved
        // selectors: it is the "the ad is drawn by the site itself" case.
        view?.evaluateJavascript(INTERSTITIAL_KILL_JS, null)
        val arr = JSONArray().apply { blockedSelectors.forEach { put(it) } }
        view?.evaluateJavascript(
            "window.__hikariBlocks=" + arr.toString() +
                ";window.__hikariHideAll&&window.__hikariHideAll();",
            null
        )
    }

    /** Host of the page currently shown (or being loaded). */
    private fun currentPageHost(): String? {
        pageHostCache?.let { return it }
        return runCatching {
            java.net.URI(pageUrl ?: webView.url).host?.lowercase()
        }.getOrNull()
    }

    /**
     * Same SITE, not merely a subdomain chain.
     *
     * `www.dailymotion.com` and `static.neon-ssr.dailymotion.com` are the same
     * site even though neither is a suffix of the other — the earlier
     * "a.b is a subdomain of a" test called them different, so the site's own
     * static host was treated as a third party and could be ad-blocked. The
     * comparison is now on the registrable domain (eTLD+1, with the common
     * two-level suffixes handled).
     */
    private fun isSameSite(host: String, current: String): Boolean {
        val h = host.trim().trimEnd('.').lowercase()
        val c = current.trim().trimEnd('.').lowercase()
        if (h.isBlank() || c.isBlank()) return false
        if (h == c || h.endsWith("." + c) || c.endsWith("." + h)) return true
        return registrableDomain(h) == registrableDomain(c)
    }

    private fun registrableDomain(host: String): String {
        val parts = host.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) return parts.joinToString(".")
        val tld = parts.last()
        val sld = parts[parts.size - 2]
        val twoLevel = tld.length == 2 &&
            sld in setOf("co", "com", "net", "org", "gov", "ac", "edu", "ne", "or", "go")
        return parts.takeLast(if (twoLevel) 3 else 2).joinToString(".")
    }

    /** Cloudflare's own challenge infrastructure — the scripts, iframes and
     *  /cdn-cgi/ endpoints that mint the cf_clearance cookie. These must NEVER
     *  be treated as ad traffic: an ad host list that carries (or a user
     *  blocklist that happens to carry) a Cloudflare challenge host turns the
     *  challenge's own bootstrap into a blocked request, so the page reloads
     *  itself and asks again — the challenge loops instead of completing. */
    private fun isCloudflareInfra(url: String?): Boolean {
        if (url == null) return false
        if (url.contains("/cdn-cgi/")) return true
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "challenges.cloudflare.com" || host.endsWith(".challenges.cloudflare.com") ||
            host == "cloudflare.com" || host.endsWith(".cloudflare.com") ||
            host == "cloudflareinsights.com" || host.endsWith(".cloudflareinsights.com")
    }

    /** The public CDNs that serve the injected hls.js polyfill. Never ad-block
     *  these: without hls.js an HLS-only site buffers forever even when every
     *  ad was blocked correctly. */
    private fun isAppAssetHost(host: String): Boolean {
        val h = host.trim().trimEnd('.').lowercase()
        return h == "cdn.jsdelivr.net" || h.endsWith(".jsdelivr.net") ||
            h == "unpkg.com" || h.endsWith(".unpkg.com") ||
            h == "cdnjs.cloudflare.com" || h.endsWith(".cdnjs.cloudflare.com")
    }    /** In Cloudflare-verification mode ONLY the challenge may be shown: the
     *  site we started on plus Cloudflare's own challenge infra. Anything else
     *  (ad-hijack redirects, parked/redirect pages, trackers) is blocked —
     *  the auto-opened view must never land on a random site. */
    private fun isVerifyAllowed(url: String?): Boolean {
        if (url == null) return false
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        // A link the user put on the allowed-redirect list is allowed here too:
        // an extension's own site very often answers on a mirror host
        // (net77.cc and friends), and "Verify for Cloudflare" was refusing
        // exactly the redirect the user had already permitted — the reported
        // "I allowed net77.cc and the verification WebView still says Blocked
        // redirect to net77.cc". The allow list only ever carries what the user
        // chose (a host, or a word they typed), so honouring it cannot open the
        // challenge view to ad-hijack redirects.
        if (isRedirectAllowed(url)) return true
        val orig = runCatching { java.net.URI(startUrl).host?.lowercase() }.getOrNull() ?: return true
        if (host == orig) return true
        return host == "challenges.cloudflare.com" || host.endsWith(".challenges.cloudflare.com") ||
            host == "cloudflare.com" || host.endsWith(".cloudflare.com") ||
            host == "cloudflareinsights.com" || host.endsWith(".cloudflareinsights.com")
    }

    /** One toast per page so ad spam doesn't toast-spam the user. Thread-safe:
     *  shouldInterceptRequest runs on a WebView background thread (no Looper),
     *  so the Toast itself must be posted to the main looper. */
    private fun showBlockedToast(msg: String) {
        if (blockedToastShown) return
        blockedToastShown = true
        verifyHandler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    /**
     * The manifest URL inside a link an addon's configuration page hands back,
     * or null when the link is not one.
     *
     * A Stremio addon's config page installs the configured addon by navigating
     * to `stremio://<host>/<path>/manifest.json` (an older spelling is
     * `stremio://<percent-encoded absolute URL>`), and a few pages simply link
     * the manifest itself. Both are normalised to the https manifest URL the
     * addon-install path expects — the path that config produced is the whole
     * point, so it is never discarded.
     */
    private fun addonInstallUrl(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty()) return null
        val lower = url.lowercase()
        if (lower.startsWith("stremio://")) {
            val rest = url.substring("stremio://".length)
            val decoded = runCatching { java.net.URLDecoder.decode(rest, "UTF-8") }
                .getOrDefault(rest)
            val absolute =
                if (decoded.startsWith("http://") || decoded.startsWith("https://")) decoded
                else "https://" + decoded
            return manifestUrlOf(absolute)
        }
        if ((lower.startsWith("http://") || lower.startsWith("https://")) &&
            lower.substringBefore('?').endsWith("/manifest.json")
        ) {
            return url
        }
        return null
    }

    /** A manifest URL for [url], adding `/manifest.json` when it names a base. */
    private fun manifestUrlOf(url: String): String =
        if (url.substringBefore('?').endsWith("/manifest.json")) url
        else url.substringBefore('?').trimEnd('/') + "/manifest.json"

    /**
     * Sends a non-web URL to the system: mailto:, tel:, market:, intent: and
     * the custom schemes OAuth/TV sign-in flows use. Returns true when the
     * navigation was handled here (so the WebView must not attempt it).
     */
    private fun handleExternalScheme(uri: Uri, gesture: Boolean = true): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme in setOf("http", "https", "about", "data", "blob", "javascript", "file", "content")) {
            return false
        }
        if (verifyHost != null || autoCloseWhenCloudflarePassed) {
            // A verification view exists only to show its challenge; opening an
            // external app from it is never part of that job.
            Toast.makeText(this, I18n.t("This link can't be opened here"), Toast.LENGTH_SHORT).show()
            return true
        }
        // Only a real tap may launch another app from ordinary browsing. A
        // script-triggered intent:// is a classic ad/install hijack; extension
        // and add-on setup pages are exempt so their own login flows still
        // work on a delayed redirect.
        if (!gesture && providerId == null && !addonConfig) {
            showBlockedToast(I18n.t("Blocked an automatic app launch"))
            return true
        }
        val opened = runCatching {
            val intent = if (scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            val resolver = runCatching {
                packageManager.resolveActivity(
                    intent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                )
            }.getOrNull()
            if (resolver == null && scheme == "intent") {
                // intent:// carries its own browser fallback; use it instead
                // of dead-ending when the target app is not installed.
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (!fallback.isNullOrBlank()) {
                    webView.loadUrl(fallback)
                    return true
                }
            }
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) {
            Toast.makeText(this, I18n.t("No app can open this link"), Toast.LENGTH_SHORT).show()
        }
        return true
    }

    /** The addon's config page handed back an install URL: give it to the caller
     *  (the Extensions screen installs/replaces the addon) and close. */
    private fun finishWithManifest(url: String) {
        com.hikari.app.data.Logs.log("WebView", "addon config page returned $url")
        setResult(RESULT_OK, Intent().putExtra("manifestUrl", url))
        finish()
    }

    /**
     * "Verify for Cloudflare" mode: every ~1.2s ask the page whether it still
     * looks like a WAF challenge. The moment a challenge we've already seen
     * turns into real content (the user completed the verification and the
     * page reloaded), finish this activity so the caller reloads the extension
     * catalog with the now-valid cf_clearance cookie. If no challenge ever
     * appears the user just closes the view normally.
     */
    private fun pollCloudflarePass() {
        verifyHandler.removeCallbacksAndMessages(null)
        val r = object : Runnable {
            override fun run() {
                if (!autoCloseWhenCloudflarePassed || verifyDone) return
                val v = webView
                if (v == null) return
                v.evaluateJavascript(
                    "(function(){" +
                        "var t=(document.title||'').toLowerCase();" +
                        "var h=location.href.toLowerCase();" +
                        "var b=document.body?document.body.innerText.slice(0,3000).toLowerCase():'';" +
                        "var chal=(t.indexOf('just a moment')>=0||t.indexOf('attention required')>=0||" +
                        "h.indexOf('cdn-cgi/challenge')>=0||h.indexOf('challenge-platform')>=0||" +
                        "b.indexOf('verify you are human')>=0||b.indexOf('performing security verification')>=0||" +
                        "b.indexOf('checking your browser')>=0||b.indexOf('cf-chl')>=0||" +
                        "b.indexOf('turnstile')>=0);" +
                        "var block=(t.indexOf('you have been blocked')>=0||t.indexOf('access denied')>=0||" +
                        "h.indexOf('cf-error')>=0||b.indexOf('you have been blocked')>=0||" +
                        "b.indexOf('access denied')>=0||b.indexOf('request blocked')>=0||" +
                        "b.indexOf('cf-error-details')>=0);" +
                        "return chal?1:(block?2:0);" +
                        "})();"
                ) { res ->
                    when (res?.trim()?.trim('"')) {
                        "1" -> {
                            challengeSeen = true
                            blockedCount = 0
                            noChallengePolls = 0
                        }
                        "2" -> {
                            // Hard WAF block — no challenge to pass. Give the
                            // page a beat (Cloudflare may still be mid-redirect)
                            // then close instead of sitting on the player.
                            blockedCount++
                            noChallengePolls = 0
                            if (blockedCount >= 3) {
                                verifyDone = true
                                finish()
                            }
                        }
                        else -> if (challengeSeen) {
                            // Challenge present → gone = verification complete.
                            verifyDone = true
                            finish()
                        } else {
                            // No challenge ever appeared and no hard block — the
                            // page loaded as ordinary content (e.g. the SVG
                            // namespace page a URL scanner mistook for a stream).
                            // The verify view has nothing to do; close it instead
                            // of lingering on screen forever.
                            noChallengePolls++
                            if (noChallengePolls >= VERIFY_NO_CHALLENGE_POLLS) {
                                verifyDone = true
                                finish()
                            }
                        }
                    }
                    if (!verifyDone && autoCloseWhenCloudflarePassed) {
                        verifyHandler.postDelayed(this, 1200)
                    }
                }
            }
        }
        verifyRunnable = r
        verifyHandler.postDelayed(r, 1200)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Keep the display awake while the page is actually playing a video, and
     *  release it the moment playback stops. */
    private fun setKeepScreenOn(on: Boolean) {
        if (on) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Immersive fullscreen is not sticky — re-hide the bars whenever this
     *  activity is resumed or regains focus (see PlayerActivity.hideSystemUi). */
    override fun onResume() {
        super.onResume()
        hideSystemBars()
        // Pair with onPause's media stop: a WebView left paused never runs its
        // scripts again, which would freeze the page for the rest of the session.
        runCatching { webView.onResume() }
        runCatching { popupChild?.onResume() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * How good a source is, 0 = not a video. Populated by [maybeAddVideo]'s
     * probe and used to order the player's server list.
     *
     * The scale mirrors what makes a source *playable* rather than merely
     * present: an adaptive master (it carries the whole ladder, so the player
     * can pick a rendition and seek) beats a single media playlist, which beats
     * a bare file. An HLS playlist whose only tracks are audio is not a video
     * at all and scores 0.
     */
    private val videoScores = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Request headers the page's own fetch/XHR used for a source, keyed by
     *  URL (reported by SNIFFER_JS). Merged into the player's hand-off so a
     *  CDN sees exactly what its own page sent. */
    private val sniffHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    private fun maybeAddVideo(url: String) {
        // Verification view never offers video.
        if (autoCloseWhenCloudflarePassed) return
        if (detectedVideos.contains(url)) return
        // An ad that happens to be served as video is still an ad.
        if (isAdVideoCandidate(url)) return
        // A fragment of a stream is not a stream. HLS/DASH players fetch
        // `init.mp4`, `.m4s` and audio renditions while the manifest is already
        // in the list, so offering them as separate "videos" makes the sheet a
        // pile of unusable rows (and can win the sort as a bare MP4).
        if (isStreamFragment(url)) return
        Thread {
            val score = probeVideoScore(url)
            if (score > 0) {
                runOnUiThread {
                    if (detectedVideos.add(url)) {
                        videoScores[url] = score
                        videoChip.text = "\u25B6 " + detectedVideos.size
                        videoChip.visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    /**
     * True for requests that carry actual media (a Range request, a video
     * Accept header, or a media-looking URL). The ad-blocker never blocks
     * these: blocklists contain video-CDN domains (tube-site CDNs especially)
     * and blocking the media itself is what makes videos spin forever.
     */
    private fun isMediaRequest(request: WebResourceRequest): Boolean {
        val h = request.requestHeaders
        if (h?.containsKey("Range") == true) return true
        val accept = h?.get("Accept")?.lowercase().orEmpty()
        if (accept.contains("video/") || accept.contains("application/octet-stream")) return true
        val u = request.url.toString().lowercase()
        return u.contains(".mp4") || u.contains(".m3u8") || u.contains(".mpd") ||
            u.contains(".ts?") || u.contains("videoplayback") || u.contains("/videos/") ||
            u.contains("/media/") || u.contains("phncdn") || u.contains("streamable")
    }

    /**
     * Fetches the first couple of kilobytes and scores the source:
     *
     *  - 0    not video (or an audio-only HLS playlist)
     *  - 40   a direct file (mp4/webm)
     *  - 60   an HLS media playlist (one rendition)
     *  - 110  a DASH manifest
     *  - 120  an HLS master playlist (a full quality ladder)
     *
     * A master playlist is the difference between "opens at 480p and cannot
     * seek well" and a normal adaptive stream; it is why this ranks above a
     * single media playlist instead of both being "a video".
     */
    private fun probeVideoScore(url: String): Int {
        // Share the WebView's cookie jar with the probe request — Cloudflare /
        // auth-token CDNs answer with a 403 (or a challenge page) without it.
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        val headers = buildMap {
            put("Range", "bytes=0-2047")
            put("Referer", pageUrl ?: url)
            put("User-Agent", (application as HikariApp).effectiveWebViewUa())
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
        }
        val r = runCatching { Http.get(url, headers) }.getOrNull() ?: return 0
        r.use { resp ->
            if (!resp.isSuccessful) return 0
            val ct = resp.header("Content-Type")?.lowercase().orEmpty()
            val body = resp.body?.bytes() ?: return 0
            val head = String(body, 0, minOf(body.size, 4096), Charsets.ISO_8859_1)
            return when {
                head.startsWith("#EXTM3U") -> {
                    val master = head.contains("#EXT-X-STREAM-INF")
                    val media = head.contains("#EXTINF")
                    val videoCodec = Regex("avc1|hvc1|hev1|vp09|av01|dvh1|dvhe", RegexOption.IGNORE_CASE)
                        .containsMatchIn(head)
                    // An audio-only playlist (demuxed live audio) is not a
                    // playable "video" and must never occupy a server slot.
                    if (!master && media && !videoCodec) 0
                    else if (master) 120
                    else 60
                }
                ct.contains("application/dash+xml") || head.contains("<mpd") -> 110
                ct.contains("video/") -> 40
                ct.contains("application/octet-stream") && looksLikeMp4(body) -> 40
                else -> 0
            }
        }
    }

    /**
     * The real destination carried by an ad GATE.
     *
     * Streaming sites and Rumble wrap a click in a click-tracker on an ad host
     * (`d4.ads.rmbl.ws/c?…&fb=<base64 of the video page>`): the main frame is
     * sent to the tracker, which then 302s to the page. Blocking the tracker
     * outright left the WebView on an empty document — the white page at
     * "level 2". The destination is in the query, so we take it and load the
     * page the click was for; only a gate with nothing usable behind it is
     * cancelled.
     */
    private fun adGateDestination(raw: String): String? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        for (key in listOf("fb", "url", "u", "dest", "destination", "redirect", "redirect_url", "to", "target")) {
            val v = uri.getQueryParameter(key) ?: continue
            for (candidate in listOf(v, decodeBase64(v))) {
                val c = candidate?.trim().orEmpty()
                if (c.startsWith("http://") || c.startsWith("https://")) return c
            }
        }
        return null
    }

    private fun decodeBase64(raw: String): String? = runCatching {
        val t = Uri.decode(raw).trim()
        val bytes = runCatching {
            Base64.decode(t, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }.recoverCatching {
            Base64.decode(t, Base64.DEFAULT)
        }.getOrThrow()
        String(bytes, Charsets.UTF_8)
    }.getOrNull()

    /** True when [url]'s host is one the ad lists block. */
    private fun isAdHost(url: String): Boolean {
        val h = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        return h.isNotBlank() && AdBlocker.matches(h, blockedDomains)
    }

    /**
     * What a main-frame navigation to a blocked ad host renders instead of a
     * blank document. With a destination it forwards there immediately; with
     * none it says what happened and offers the way back, because a white
     * page reads as "the app is broken".
     */
    private fun adGatePage(destination: String?): WebResourceResponse {
        val style = "html,body{background:#000;color:#fff;font:15px sans-serif;margin:0}" +
            ".w{display:flex;height:100vh;flex-direction:column;gap:14px;align-items:center;" +
            "justify-content:center;padding:24px;text-align:center}a{color:#7DD3FC}"
        val html = if (destination != null) {
            val url = destination.replace("\"", "%22")
            "<!doctype html><meta charset=\"utf-8\">" +
                "<meta http-equiv=\"refresh\" content=\"0;url=" + url + "\">" +
                "<style>" + style + "</style><div class=\"w\">Opening…</div>" +
                "<script>location.replace(" + org.json.JSONObject.quote(destination) + ")</script>"
        } else {
            "<!doctype html><meta charset=\"utf-8\"><style>" + style + "</style>" +
                "<div class=\"w\"><div>An ad link was blocked.</div>" +
                "<a href=\"javascript:history.back()\">Go back</a></div>"
        }
        return WebResourceResponse(
            "text/html", "utf-8",
            java.io.ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)),
        )
    }

    /**
     * True for the pieces a manifest hands the player: initialization
     * segments, .m4s fragments, transport-stream chunks and demuxed audio.
     * These are only playable as part of their parent playlist.
     */
    private fun isStreamFragment(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        if (path.contains(".m3u8") || path.contains(".mpd")) return false
        return path.endsWith("/init.mp4") ||
            path.contains("/init.") ||
            path.endsWith(".m4s") || path.endsWith(".cmfv") || path.endsWith(".cmfa") ||
            path.endsWith(".ts") ||
            path.contains("aac_") || path.contains("_audio_") || path.contains("/audio/") ||
            path.contains("h264_sd/") || path.contains("h264_hq/") || path.contains("h264_hd/")
    }

    /** True when [url] is a host/pattern the ad lists already call an ad. */
    private fun isAdVideoCandidate(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
        if (host.isBlank()) return true
        val page = currentPageHost()
        // A source served from the page's own site is not an ad however many
        // lists carry that domain (Rumble is in three of them, for its ad
        // subresources — treating that as "every video is an ad" left the chip
        // empty on every Rumble page).
        if (AdBlocker.isAlwaysAllowedHost(host)) {
            return AdBlocker.matchesPatternUrl(url, page)
        }
        if (page != null && isSameSite(host, page)) {
            return AdBlocker.matchesPatternUrl(url, page)
        }
        if (AdBlocker.matches(host, blockedDomains)) return true
        return AdBlocker.matchesPatternUrl(url, page)
    }

    private fun looksLikeMp4(b: ByteArray): Boolean {
        if (b.size < 12) return false
        // MP4 boxes start with a size then "ftyp" / "moov" / "mdat" / "styp"
        return b.size >= 12 &&
            ((b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() && b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte()) ||
                (b[4] == 'm'.code.toByte() && b[5] == 'o'.code.toByte() && b[6] == 'o'.code.toByte() && b[7] == 'v'.code.toByte()) ||
                (b[4] == 'm'.code.toByte() && b[5] == 'd'.code.toByte() && b[6] == 'a'.code.toByte() && b[7] == 't'.code.toByte()))
    }

    // ---- Browser chrome helpers -----------------------------------------

    /** `https://www.example.com/a` → `example.com/a` for the address field. */
    private fun stripScheme(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.")

    private fun hideKeyboard() {
        runCatching {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(urlField.windowToken, 0)
        }
    }

    /** Keeps the address field and the back/forward affordances in step with
     *  the page. Never overwrites what the user is typing. */
    private fun syncToolbar(url: String?) {
        runCatching {
            if (!url.isNullOrBlank() && !url.startsWith("about:")) {
                if (!urlField.hasFocus()) {
                    urlField.setText(stripScheme(url))
                    urlField.setSelection(urlField.text.length)
                }
            }
            backButton.alpha = if (webView.canGoBack()) 1f else 0.3f
            forwardButton.alpha = if (webView.canGoForward()) 1f else 0.3f
        }
    }

    /** A typed address: a real URL loads, anything else is a search. */
    private fun loadFromAddressBar(raw: String) {
        val t = raw.trim()
        if (t.isBlank()) return
        val target = when {
            t.contains("://") -> t
            t.contains('.') && !t.contains(' ') -> "https://" + t
            else -> "https://www.google.com/search?q=" + Uri.encode(t)
        }
        runCatching { webView.loadUrl(target) }
    }

    /**
     * The page's preview card (`og:image` + title), the PlayBridge idea of
     * showing WHAT is about to be played instead of a bare URL. Fetched once
     * per page; a page without OpenGraph falls back to its <title>.
     */
    private fun loadPagePreview(view: WebView?) {
        view ?: return
        view.evaluateJavascript(
            """(function(){try{
                 var m=document.querySelector('meta[property="og:image"],meta[name="og:image"],meta[name="twitter:image"]');
                 var t=document.querySelector('meta[property="og:title"],meta[name="twitter:title"]');
                 return JSON.stringify({i:m?m.content:'',t:t?t.content:(document.title||'')});
               }catch(e){return '{}';}})();""",
        ) { res ->
            runCatching {
                val raw = org.json.JSONTokener(res).nextValue() as? String ?: return@runCatching
                val o = org.json.JSONObject(raw)
                pagePreviewUrl = o.optString("i").takeIf { it.startsWith("http") }
                pagePreviewTitle = o.optString("t")
            }
        }
    }

    /** Detected sources in the order the player will use them. */
    private fun sortedDetected(): List<String> {
        val page = currentPageHost().orEmpty()
        fun same(u: String): Boolean {
            val h = runCatching { java.net.URI(u).host }.getOrNull()?.lowercase() ?: return false
            if (page.isBlank()) return false
            return h == page || h.endsWith(".$page") || page.endsWith(".$h")
        }
        return detectedVideos.toList().sortedWith(
            compareByDescending<String> { if (same(it)) 1 else 0 }
                .thenByDescending { videoScores[it] ?: 0 },
        )
    }

    private fun mediaTypeLabel(url: String): String = when (videoScores[url] ?: 0) {
        120 -> "HLS \u00B7 adaptive"
        110 -> "DASH"
        60 -> "HLS"
        40 -> if (url.contains(".mp4", true)) "MP4" else I18n.t("Video")
        else -> I18n.t("Video")
    }

    /**
     * The source list, modelled on PlayBridge's cast sheet: a preview of the
     * page, one tap for "play the best", and a row per source with its type,
     * host and a play button. This is the "finding video" surface — the badge
     * only says how many, this says which.
     */
    // ---------------------------------------------------------------- bookmarks

    private fun bookmarkPrefs() = getSharedPreferences("webview_bookmarks", MODE_PRIVATE)

    private fun loadBookmarks(): List<Pair<String, String>> = runCatching {
        val raw = bookmarkPrefs().getString("items", "[]").orEmpty()
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val u = o.optString("u")
            if (!u.startsWith("http")) return@mapNotNull null
            o.optString("t").ifBlank { urlHost(u) } to u
        }
    }.getOrDefault(emptyList())

    private fun saveBookmarks(items: List<Pair<String, String>>) {
        runCatching {
            val arr = org.json.JSONArray()
            items.take(12).forEach { (t, u) ->
                arr.put(org.json.JSONObject().put("t", t).put("u", u))
            }
            bookmarkPrefs().edit().putString("items", arr.toString()).apply()
        }
    }

    /**
     * Redraws the bottom strip. "+" saves the current page; each saved page is
     * a focusable chip (D-pad OK loads it, long-press forgets it). The strip is
     * rebuilt on every page finish so its title/URL are the current ones.
     */
    private fun refreshBookmarks() {
        if (!::bookmarkBar.isInitialized) return
        bookmarkBar.removeAllViews()
        bookmarkBar.addView(
            bookmarkChip("+", accent = true, onClick = { addCurrentBookmark() }, onLongClick = null),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                marginEnd = dp(8)
            },
        )
        for ((title, url) in loadBookmarks()) {
            bookmarkBar.addView(
                bookmarkChip(title, accent = false, onClick = {
                    if (webView.url != url || !webView.canGoBack()) {
                        webView.loadUrl(url)
                    }
                    webView.requestFocus()
                }, onLongClick = {
                    val next = loadBookmarks().filterNot { it.second == url }
                    saveBookmarks(next)
                    refreshBookmarks()
                    Toast.makeText(this, I18n.t("Bookmark removed"), Toast.LENGTH_SHORT).show()
                }),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                    marginEnd = dp(8)
                },
            )
        }
        if (loadBookmarks().isEmpty()) {
            bookmarkBar.addView(
                TextView(this).apply {
                    text = I18n.t("Save a page with +")
                    textSize = 11f
                    setTextColor(0xFF7C86A2.toInt())
                },
            )
        }
    }

    private fun addCurrentBookmark() {
        val url = webView.url ?: return
        if (!url.startsWith("http")) return
        val title = pageTitle.ifBlank { webView.title.orEmpty() }.ifBlank { urlHost(url) }
            .take(40)
        val existing = loadBookmarks()
        if (existing.any { it.second == url }) {
            Toast.makeText(this, I18n.t("Already saved"), Toast.LENGTH_SHORT).show()
            return
        }
        saveBookmarks((listOf(title to url) + existing).take(12))
        refreshBookmarks()
        Toast.makeText(this, I18n.t("Page saved"), Toast.LENGTH_SHORT).show()
    }

    private fun bookmarkChip(
        label: String,
        accent: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
    ): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        maxWidth = dp(170)
        setTextColor(if (accent) Color.WHITE else 0xFFE6EAF5.toInt())
        setPadding(dp(14), 0, dp(14), 0)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(17).toFloat()
            setColor(if (accent) 0xFF3D5AFE.toInt() else 0x33161C2B)
            setStroke(dp(1), if (accent) 0x00000000 else 0x33FFFFFF)
        }
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        setOnClickListener { onClick() }
        if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
    }

    // ------------------------------------------------------------ TV remote

    /**
     * D-pad support for the browser. A WebView is a touch surface: without
     * this, a television remote can focus the URL bar but has no way to scroll
     * the page or press what is on it. Arrows scroll; OK taps the centre (the
     * element a site puts there: the player, a cookie banner's accept, a play
     * button); media keys toggle and seek the page's <video>.
     */
    private fun handleTvRemote(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!com.hikari.app.tv.TvMode.isTv) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                webView.scrollBy(0, dp(110)); true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                webView.scrollBy(0, -dp(110)); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                webView.scrollBy(dp(140), 0); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                webView.scrollBy(-dp(140), 0); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                remoteTap()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                webView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused){v.play()}else{v.pause()}})()",
                    null,
                )
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                webView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.min(v.duration||1e9,v.currentTime+10)})()",
                    null,
                )
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                webView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime-10)})()",
                    null,
                )
                true
            }
            else -> false
        }
    }

    private fun remoteTap() {
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) return
        val x = w / 2f
        val y = h / 2f
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0)
        runCatching {
            webView.dispatchTouchEvent(down)
            webView.dispatchTouchEvent(up)
        }
        down.recycle()
        up.recycle()
    }

    /** A WebView frame cropped to 16:9 and scaled down for the source rows. */
    private fun capturePageFrame(): Bitmap? {
        val v = webView ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        return runCatching {
            val full = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(full))
            val cropH = (v.width * 9f / 16f).toInt().coerceIn(1, v.height)
            val top = ((v.height - cropH) / 2).coerceAtLeast(0)
            val cropped = Bitmap.createBitmap(full, 0, top, v.width, cropH)
            if (cropped !== full) full.recycle()
            val scaled = Bitmap.createScaledBitmap(cropped, 320, 180, true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        }.getOrNull()
    }

    private fun showMediaSheet() {
        val list = sortedDetected()
        if (list.isEmpty()) {
            // Nothing sniffed yet: fall back to the page scan.
            playVideo()
            return
        }
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(22))
            setBackgroundColor(0xFF10131C.toInt())
        }

        val preview = pagePreviewUrl
        if (preview != null) {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF1B2030.toInt())
            }
            root.addView(
                image,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(150),
                ).apply { bottomMargin = dp(12) },
            )
            Thread {
                val bmp = runCatching {
                    Http.get(preview).use { r ->
                        BitmapFactory.decodeStream(r.body?.byteStream())
                    }
                }.getOrNull()
                if (bmp != null) runOnUiThread { image.setImageBitmap(bmp) }
            }.start()
        }

        root.addView(
            TextView(this).apply {
                text = pagePreviewTitle.ifBlank { pageTitle }
                textSize = 16f
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
        )
        val frame = capturePageFrame()
        root.addView(
            TextView(this).apply {
                text = I18n.t("Tap a source to play it in Hikari")
                textSize = 12f
                setTextColor(0xFF9AA3C0.toInt())
                setPadding(0, dp(4), 0, dp(12))
            },
        )
        root.addView(
            TextView(this).apply {
                text = I18n.t("\u25B6 Play best")
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFF3D5AFE.toInt())
                setPadding(0, dp(12), 0, dp(12))
                isClickable = true
                setOnClickListener { dialog.dismiss(); playVideo() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) },
        )

        for (u in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(8), dp(2), dp(8))
                isClickable = true
                isFocusable = com.hikari.app.tv.TvMode.isTv
                setOnClickListener {
                    dialog.dismiss()
                    launchPlayer(listOf(u), pageUrl ?: webView.url, preferred = setOf(u))
                }
            }
            // A frame of what the page is showing, at the same size for every
            // source: the list is a channel guide, and a guide without a picture
            // makes the reader work out which row is which from a URL.
            row.addView(
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFF1B2030.toInt())
                    frame?.let { setImageBitmap(it) }
                },
                LinearLayout.LayoutParams(dp(112), dp(63)).apply { marginEnd = dp(12) },
            )
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(
                TextView(this).apply {
                    text = mediaTypeLabel(u) + "  \u00B7  " + urlHost(u)
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
            )
            texts.addView(
                TextView(this).apply {
                    text = u
                    textSize = 11f
                    setTextColor(0xFF7C86A2.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                },
            )
            row.addView(
                texts,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            root.addView(row)
        }

        dialog.setContentView(ScrollView(this).apply { addView(root) })
        dialog.show()
    }

    private fun playVideo() {
        val ref = pageUrl ?: webView.url
        // The page's own <video> element is the strongest signal there is: it
        // is the source the site itself is playing. Network sniffing also sees
        // the ad clip a click fires, and that is how "server 1" used to be an
        // advert. DOM sources go first, sniffed ones follow as fallbacks.
        webView.evaluateJavascript(VIDEO_SCAN_JS) { res ->
            val dom = extractUrls(res)
            val all = (dom + detectedVideos.toList()).distinct()
            if (all.isEmpty()) {
                Toast.makeText(this, I18n.t("No video found on this page"), Toast.LENGTH_SHORT).show()
            } else {
                launchPlayer(all, ref, preferred = dom.toSet())
            }
        }
    }

    private fun extractUrls(jsonArrayLiteral: String): List<String> {
        val t = jsonArrayLiteral.trim()
        val start = t.indexOf('[')
        val end = t.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            val arr = JSONArray(t.substring(start, end + 1))
            (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }
        }.getOrDefault(emptyList())
    }

    private fun launchPlayer(
        urls: List<String>,
        referer: String?,
        /** Sources that came from the page's own <video> element — always
         *  ordered before anything that was merely sniffed off the wire. */
        preferred: Set<String> = emptySet(),
    ) {
        // Manifests first: a page usually exposes the HLS/DASH master and the
        // individual segments it is currently fetching. The master is the one
        // the player can adapt from (and seek in); a segment URL pins one
        // quality and often expires. The player lists the rest as alternates.
        val pageHost = runCatching { java.net.URI(referer.orEmpty()).host }
            .getOrNull().orEmpty().lowercase()
        fun sameSiteAsPage(u: String): Boolean {
            val h = runCatching { java.net.URI(u).host }.getOrNull()?.lowercase() ?: return false
            if (pageHost.isBlank()) return false
            return h == pageHost || h.endsWith(".$pageHost") || pageHost.endsWith(".$h")
        }
        val unique = urls.distinct()
            .filter { it.startsWith("http") }
            // Drop anything the ad lists recognise outright — the "2 videos"
            // chip must never count an advert as a source.
            .filterNot { isAdVideoCandidate(it) }
            .sortedWith(
                // The episode's own CDN first: it is normally served from the
                // site's domain or a subdomain of it, while an advert comes from
                // an unrelated host. Manifests before fragments after that.
                compareByDescending<String> {
                    // 1. What the page's player is actually using.
                    if (it in preferred) 1 else 0
                }
                    // 2. Its own CDN before an unrelated host.
                    .thenByDescending { if (sameSiteAsPage(it)) 1 else 0 }
                    // 3. The probed source score: a master playlist with a full
                    //    ladder above a single rendition above a bare file.
                    .thenByDescending { videoScores[it] ?: 0 }
            )
        if (unique.isEmpty()) {
            Toast.makeText(this, I18n.t("No playable video found"), Toast.LENGTH_SHORT).show()
            return
        }
        val cookie = runCatching {
            unique.firstNotNullOfOrNull { CookieManager.getInstance().getCookie(it) }
        }.getOrNull()
        // The credentials the PAGE used. A CDN that handed the WebView its
        // media rejects a request missing any of these, and for HLS it checks
        // the child playlist and every segment, not just the first URL — which
        // is why they are handed to the loopback proxy (StreamProxy) rather
        // than only to the first request.
        val headerMap = buildMap {
            put("User-Agent", (application as HikariApp).effectiveWebViewUa())
            referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            referer?.let { ref ->
                runCatching { java.net.URI(ref) }.getOrNull()
                    ?.let { u -> "${u.scheme}://${u.host}" }
                    ?.let { put("Origin", it) }
            }
        }
        val sources = JSONArray()
        unique.forEach { u ->
            // DASH is left direct: its MPD points at relative segment
            // templates the player resolves itself, which the proxy would have
            // to parse and rewrite. HLS is rewritten, so it goes through.
            val effectiveHeaders = HashMap<String, String>()
            sniffHeaders[u]?.let { effectiveHeaders.putAll(it) }
            effectiveHeaders.putAll(headerMap)
            val proxied = if (u.contains(".mpd", true)) {
                null
            } else {
                runCatching {
                    com.hikari.app.player.StreamProxy.register(u, effectiveHeaders)
                }.getOrNull()
            }
            val headers = JSONObject()
            // A proxied source needs no headers of its own (the proxy adds the
            // page's); a direct one still does.
            if (proxied == null) effectiveHeaders.forEach { (k, v) -> headers.put(k, v) }
            sources.put(
                JSONObject()
                    .put("name", "Web · ${urlHost(u)}")
                    .put("url", proxied ?: u)
                    .put("headers", headers)
                    .put("isM3u8", u.contains(".m3u8", true))
                    .put("isMpd", u.contains(".mpd", true))
                    .put("subtitles", JSONArray())
            )
        }
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra("title", pageTitle)
                putExtra("sources", sources.toString())
            }
        )
    }

    private fun urlHost(u: String): String = runCatching {
        java.net.URI(u).host ?: u.take(48)
    }.getOrDefault(u.take(48))

    /**
     * Handed to the page as `window.HikariBridge`. Called by STUCK_MONITOR_JS
     * when a real <video> on the page has been trying to play for ~9s without
     * producing a single frame — the site's player is never going to start on
     * this WebView, so hand the exact source to Hikari's ExoPlayer (with the
     * page as Referer + shared cookies) and let it play there instead.
     */
    private inner class HikariJsBridge {
        /**
         * Every bridge call is routed through here. A `@JavascriptInterface`
         * method is Java code invoked FROM Chromium's native layer, so an
         * exception escaping it is rethrown into native as
         * `JniAndroid$UncaughtException` and reaches our default uncaught
         * handler — which records a crash and kills the process. The user then
         * sees the "app crashed on a previous launch" banner on Home for what
         * was, from their point of view, an ordinary WebView page. Swallowing
         * (and logging) bridge failures keeps a broken userscript or a null
         * WebView from taking the whole app down.
         */
        private inline fun <T> safe(tag: String, block: () -> T): T? =
            runCatching { block() }.onFailure {
                android.util.Log.w("HikariBridge", "$tag failed (swallowed)", it)
            }.getOrNull()

        /**
         * A media URL the page's own JS saw (SNIFFER_JS): `{url, kind,
         * contentType, poster, headers}`. The headers are the ones that page
         * actually sent, which the player hand-off reuses.
         */
        @android.webkit.JavascriptInterface
        fun sniff(payload: String) {
            safe("sniff") {
                val o = org.json.JSONObject(payload)
                val url = o.optString("url")
                if (!url.startsWith("http://") && !url.startsWith("https://")) return@safe
                val headers = HashMap<String, String>()
                o.optJSONObject("headers")?.let { h ->
                    h.keys().forEach { key ->
                        val v = h.optString(key)
                        if (v.isNotBlank() && key.isNotBlank()) headers[key] = v
                    }
                }
                if (headers.isNotEmpty()) sniffHeaders[url] = headers
                val poster = o.optString("poster")
                if (pagePreviewUrl == null && poster.startsWith("http")) pagePreviewUrl = poster
                val contentType = o.optString("contentType").lowercase()
                val looksMedia = VIDEO_URL_RE.containsMatchIn(url) ||
                    contentType.contains("video/") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("dash")
                if (!looksMedia) return@safe
                if (isAdVideoCandidate(url) || isStreamFragment(url)) return@safe
                maybeAddVideo(url)
            }
        }

        @android.webkit.JavascriptInterface
        fun stuckVideo(url: String) {
            safe("stuckVideo") {
                // Verification view exists only to pass the challenge — never
                // hand a video off to the external player from it.
                if (autoCloseWhenCloudflarePassed) return@safe
                if (autoLaunched || url.isBlank()) return@safe
                autoLaunched = true
                runOnUiThread {
                    safe("stuckVideo.ui") {
                        detectedVideos.add(url)
                        videoChip.text = "\u25B6 Opening external player…"
                        videoChip.visibility = View.VISIBLE
                        launchPlayer(listOf(url), pageUrl ?: webView.url)
                    }
                }
            }
        }

        // ---- Element blocker bridge ----
        @android.webkit.JavascriptInterface
        fun blockElement(selector: String) {
            safe("blockElement") {
                // A page can call this bridge directly. Keep the persisted
                // selector list bounded and reject control characters so a
                // hostile page cannot fill storage or inject into the JS that
                // re-applies the list.
                val clean = selector.trim()
                if (clean.isEmpty() || clean.length > 500) return@safe
                if (clean.any { it == '\n' || it == '\r' || it == '\u0000' }) return@safe
                if (blockedSelectors.size >= 300) return@safe
                runOnUiThread {
                    safe("blockElement.ui") {
                        blockedSelectors.add(clean)
                        (applicationContext as HikariApp).elementBlocks = blockedSelectors.toList()
                        Toast.makeText(this@WebViewActivity, I18n.t("Element blocked"), Toast.LENGTH_SHORT)
                            .show()
                        lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                (applicationContext as HikariApp).store.addElementBlock(clean)
                            }
                        }
                    }
                }
            }
        }

        // ---- Userscript GM_* value storage (WebView only) ----
        @android.webkit.JavascriptInterface
        fun userscriptGet(scriptId: String, key: String): String? =
            safe("userscriptGet") {
                UserscriptManager.getValue(applicationContext, scriptId, key)
            }

        @android.webkit.JavascriptInterface
        fun userscriptSet(scriptId: String, key: String, valueJson: String) {
            safe("userscriptSet") {
                UserscriptManager.setValue(applicationContext, scriptId, key, valueJson)
            }
        }

        @android.webkit.JavascriptInterface
        fun userscriptDelete(scriptId: String, key: String) {
            safe("userscriptDelete") {
                UserscriptManager.deleteValue(applicationContext, scriptId, key)
            }
        }

        @android.webkit.JavascriptInterface
        fun userscriptList(scriptId: String): String =
            safe("userscriptList") { UserscriptManager.listValues(applicationContext, scriptId) }
                ?: "{}"

        // ---- Auto-translate bridge ----
        // The page's TRANSLATE_JS calls this with a batch of unique text
        // strings; we fetch English translations (translate.googleapis.com —
        // no key needed) and hand them back via window.__hikariTransResult.
        @android.webkit.JavascriptInterface
        fun translate(id: String, textsJson: String) {
            safe("translate") {
                val arr = runCatching { JSONArray(textsJson) }.getOrNull() ?: return@safe
                val texts = (0 until minOf(arr.length(), 160)).map {
                    arr.optString(it).take(2000)
                }
                if (texts.isEmpty()) return@safe
                lifecycleScope.launch {
                    safe("translate.worker") {
                        val sem = java.util.concurrent.Semaphore(6)
                        val results = arrayOfNulls<String>(texts.size)
                        coroutineScope {
                            for (i in texts.indices) {
                                launch(Dispatchers.IO) {
                                    sem.acquire()
                                    try {
                                        results[i] = fetchTranslation(texts[i])
                                    } catch (e: Exception) {
                                        results[i] = ""
                                    } finally {
                                        sem.release()
                                    }
                                }
                            }
                        }
                        val out = JSONArray()
                        results.forEach { out.put(it ?: "") }
                        val js = "window.__hikariTransResult?window.__hikariTransResult(" +
                            jsString(id) + "," + out.toString() + "):null"
                        runOnUiThread { runCatching { webView.evaluateJavascript(js, null) } }
                    }
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        // Persist cookies/DOM-storage to disk the moment the WebView leaves the
        // foreground so logins and sessions survive even a force-kill.
        runCatching { CookieManager.getInstance().flush() }
        // Stop whatever the page is PLAYING. A WebView plays media in its own
        // process and does not pause just because the activity it lives in went
        // to the background — so a trailer (or any video page) opened here kept
        // its audio running under whatever the user did next, including the
        // player's own video: the reported "the trailer sound is still coming
        // while I watch something". pausing the WebView halts timers/rendering,
        // and the JS is the belt-and-braces for a page that started playback
        // through its own element rather than the WebView's media APIs.
        runCatching { webView.onPause() }
        runCatching { popupChild?.onPause() }
        setKeepScreenOn(false)
        runCatching {
            webView.evaluateJavascript(
                "(function(){try{document.querySelectorAll('video,audio')" +
                    ".forEach(function(m){try{m.pause()}catch(e){}})}catch(e){}})()",
                null,
            )
        }
    }

    override fun onDestroy() {
        verifyHandler.removeCallbacksAndMessages(null)
        scanHandler.removeCallbacksAndMessages(null)
        setKeepScreenOn(false)
        runCatching { popupChild?.destroy() }
        popupChild = null
        runCatching { CookieManager.getInstance().flush() }
        runCatching { webView.destroy() }
        // The verify WebView closed (challenge passed or dismissed) — wake any
        // CloudflareVerifier waiters so their retry runs immediately.
        com.hikari.app.net.CloudflareVerifier.onVerifyViewClosed(verifyHost)
        super.onDestroy()
    }

    companion object {
        private const val FILE_CHOOSER_REQUEST = 4001

        /** A current desktop Chrome UA for the browser menu's "Desktop site".
         *  Kept as a constant rather than built from the device because the
         *  whole point is to stop looking like a phone. */
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

        /** Verify polls (1.2s apart) with no challenge AND no hard block — the
         *  page loaded as normal content (e.g. the SVG-namespace page a URL
         *  scanner mistook for a stream), so the verify view is useless and
         *  should close itself instead of lingering (~14s). */
        private const val VERIFY_NO_CHALLENGE_POLLS = 12

        /** How many times a crashed renderer may be recovered by relaunching
         *  this activity inside one browsing session. */
        private const val MAX_RENDER_RESTARTS = 3

        /** HLS/DASH/MP4 URLs ending the request path (optional query). */
        private val VIDEO_URL_RE =
            Regex("""\.(m3u8|mpd|mp4)(\?[^\s"']*)?$""", RegexOption.IGNORE_CASE)

        /** Hides ad containers. HIDING (not removing) keeps page scripts that
         *  query these elements working, and videos are never touched — the
         *  site's own player stays intact. */
        private val POPUP_GUARD_JS = """
            (function(){
              if(window.__hikariPopupGuard)return;
              var nativeOpen=window.open;
              var lastTrust=0;
              function mark(e){try{if(e&&e.isTrusted)lastTrust=Date.now();}catch(x){}}
              ['pointerdown','mousedown','click','keydown','touchstart'].forEach(function(t){
                try{document.addEventListener(t,mark,true);}catch(x){}
              });
              var AD=/(?:popads|popcash|propellerads|adsterra|onclickads|clickadu|exoclick|juicyads|trafficjunky|trafficstars|adcash|hilltop|bidvertiser|revcontent|mgid|taboola|outbrain|doubleclick|googlesyndication|adnxs|adform|rubicon|pubmatic|openx|tsyndicate|magsrv|realsrv|adspyglass|adf\.ly|linkvertise)/i;
              window.open=function(url,name,features){
                try{
                  var u=String(url||'');
                  if(Date.now()-lastTrust>5000)return null;
                  if(AD.test(u))return null;
                }catch(x){}
                return nativeOpen.apply(window,arguments);
              };
              window.__hikariPopupGuard=true;
            })();
        """.trimIndent()

        private val AD_CLEAN_JS = """
            (function(){
              if(window.__hikariAdClean)return;
              window.__hikariAdClean=true;
              var SEL=[
                '[id^="google_ads"]','.adsbygoogle','ins.adsbygoogle','[class*="adsense"]','[class*="advertisement"]','[class*="advertising"]','[class*="advert-"]','[class*="advert_"]','[class*="adverts"]','.advert','#advert','[id^="advert"]',
                '[class*="ad-banner"]','[id*="ad-banner"]','div[data-ad]','ins[data-ad-client]','[data-ad-slot]','[data-ad-unit]',
                '[class*="sponsored"]','[class*="ad-placeholder"]','[class*="advertorial"]',
                'iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]','iframe[src*="googleads"]',
                'iframe[src*="advertising"]','iframe[src*="adserver"]','iframe[src*="2mdn"]',
                'iframe[id^="google_ads_iframe"]','[id^="div-gpt-ad"]',
                '[class^="ad_"]','[id^="ad_"]','[class*="ad-pop"]','[class*="ad-popup"]','[id*="ad-popup"]',
                '[class*="popunder"]','[id*="popunder"]','[class*="popup-ad"]','[id*="popup-ad"]',
                '[class*="interstitial"]','[class*="overlay-ad"]','[id*="overlay-ad"]',
                '[class*="ad-overlay"]','[id*="ad-overlay"]','[class*="ad_container"]','[id*="ad_container"]',
                '[class*="ad-slot"]','[id*="ad-slot"]','[class*="ads-container"]','[id*="ads-container"]',
                '[class*="close-ad"]','[id*="close-ad"]','[class*="closead"]','[id*="closead"]',
                'iframe[src*="popads"]','iframe[src*="popcash"]','iframe[src*="propellerads"]','iframe[src*="adnxs"]',
                'iframe[src*="exoclick"]','iframe[src*="juicyads"]','iframe[src*="trafficjunky"]',
                '[class*="chat-widget"]','[id*="chat-widget"]','[class*="chat-bubble"]','[class*="message-from"]',
                '[class*="fake-chat"]','[class*="fake-message"]','[class*="dating-widget"]','[class*="meet-widget"]'
              ];
              // Fake chat/date bait overlays (the "1 MESSAGE FROM: CINDY / Haha
              // loved your joke, let's chat more" card): a small fixed box with
              // a photo, a message count and a chat invitation. Never a video.
              var BAIT=/\b\d+\s*messages?\s*from\b|message from\s*:\s*\w+|loved your joke|let'?s chat|chat (with )?me|hot singles|meet (hot |lonely |local )?(singles|girls|women)|verify you are (human|18|over 18)|you'?ve won|claim your (prize|reward)/i;
              function isBaitBox(e){
                try{
                  if(!e||!e.parentNode)return false;
                  if(e.closest('video')||e.querySelector('video'))return false;
                  var cs=getComputedStyle(e);
                  if(cs.position!=='fixed'&&cs.position!=='absolute'&&cs.position!=='sticky')return false;
                  var r=e.getBoundingClientRect();
                  var vw=window.innerWidth||0,vh=window.innerHeight||0;
                  if(vw<=0||vh<=0)return false;
                  // A bait card: bigger than a button, smaller than the page.
                  if(r.width<200||r.height<140)return false;
                  if(r.width>vw*0.92||r.height>vh*0.92)return false;
                  var t=(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim();
                  if(t.length<12||t.length>600)return false;
                  return BAIT.test(t);
                }catch(x){return false;}
              }
              function cleanBait(){
                try{
                  var els=document.querySelectorAll('div,section,aside,dialog');
                  for(var i=0;i<els.length;i++){
                    var e=els[i];
                    if(isBaitBox(e)){
                      e.style.setProperty('display','none','important');
                      e.style.setProperty('pointer-events','none','important');
                    }
                  }
                }catch(x){}
              }
              // "Close Ad" interstitial overlays (the full-page dim + "Close Ad
              // x" pill over video/download pages): the pill itself is tiny, so
              // the pill's overlay ANCESTOR goes — the highest fixed/absolute
              // box above it that still looks like an overlay (smaller than
              // the viewport), or the pill alone when there is none. Never a
              // video: anything holding a <video> is left intact.
              var CLOSEAD=/^\s*close ad\s*[x\u2715\u00d7]?\$/i;
              function hasVideo(e){
                try{
                  if(!e)return false;
                  if(e.tagName==='VIDEO')return true;
                  if(e.closest&&e.closest('video'))return true;
                  if(e.querySelector&&e.querySelector('video'))return true;
                }catch(x){}
                return false;
              }
              function cleanCloseAd(){
                try{
                  var vw=window.innerWidth||0,vh=window.innerHeight||0;
                  var els=document.querySelectorAll('div,span,button,a,section,aside');
                  for(var i=0;i<els.length;i++){
                    var e=els[i];
                    var t=(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim();
                    if(t.length>22||!CLOSEAD.test(t))continue;
                    if(hasVideo(e))continue;
                    var cs=getComputedStyle(e);
                    if(cs.position!=='fixed'&&cs.position!=='absolute'&&cs.position!=='sticky')continue;
                    var target=e,up=0;
                    try{
                      var p=e.parentNode;
                      while(p&&up<4){
                        up++;
                        if(!p.getBoundingClientRect||hasVideo(p))break;
                        var pcs=getComputedStyle(p);
                        if(pcs.position!=='fixed'&&pcs.position!=='absolute')break;
                        var r=p.getBoundingClientRect();
                        if(vw>0&&vh>0&&(r.width>vw*0.95&&r.height>vh*0.95))break;
                        target=p;
                        p=p.parentNode;
                      }
                    }catch(x){}
                    target.style.setProperty('display','none','important');
                    target.style.setProperty('pointer-events','none','important');
                  }
                }catch(x){}
              }
              function clean(){
                for(var i=0;i<SEL.length;i++){
                  try{
                    var els=document.querySelectorAll(SEL[i]);
                    for(var j=0;j<els.length;j++){
                      var e=els[j];
                      if(!e||!e.parentNode)continue;
                      if(e.closest('video')||e.querySelector('video'))continue;
                      e.style.setProperty('display','none','important');
                    }
                  }catch(e){}
                }
                // cleanBait/cleanCloseAd are expensive — only run them once on
                // initial load, not on every DOM mutation.
                if(!window.__hikariAdCleanRun){
                  window.__hikariAdCleanRun=true;
                  cleanBait();
                  cleanCloseAd();
                }
              }
              // Defer initial clean by 50ms so the page can paint first — this
              // avoids layout thrashing blocking the first render.
              if(document.readyState==='loading'){
                document.addEventListener('DOMContentLoaded',function(){setTimeout(clean,50);});
              }else{
                setTimeout(clean,50);
              }
              var adObPending=false;
              function adObLater(){
                if(adObPending)return;
                adObPending=true;
                setTimeout(function(){adObPending=false;clean();},200);
              }
              try{
                new MutationObserver(adObLater).observe(document.documentElement,{childList:true,subtree:true});
              }catch(e){}
            })();
        """.trimIndent()

        /**
         * Hides the extensions' (and the pages') funding pitch — the "goal
         * achieved / send extra love / watch an ad to support" card, the ko-fi
         * and Patreon buttons, the donate strip. Hiding (not removing) keeps
         * page scripts that query those nodes working, and a video is never
         * touched. It looks for two things: the usual class/id/caption of a
         * money box, and the ASK itself — a short element whose text is only a
         * funding line and which contains a link or button — so a card with no
         * telling class still goes, while a real page mentioning donations in a
         * paragraph (a login page's terms, an article) stays.
         */
        private val PROMO_CLEAN_JS = """
            (function(){
              if(window.__hikariPromoClean)return;
              window.__hikariPromoClean=true;
              var SEL=[
                '[class*="donate"]','[id*="donate"]','[class*="donation"]','[id*="donation"]',
                '[class*="buymeacoffee"]','[id*="buymeacoffee"]','[class*="ko-fi"]','[id*="ko-fi"]',
                '[class*="kofi"]','[id*="kofi"]','[class*="patreon"]','[id*="patreon"]',
                '[class*="paypal"]','[id*="paypal"]','[class*="sponsor"]','[id*="sponsor"]',
                '[class*="funding"]','[id*="funding"]','[class*="fundrais"]','[id*="fundrais"]',
                '[class*="support-us"]','[class*="supportus"]','[class*="tip-jar"]','[class*="tipjar"]',
                '[class*="support-banner"]','[id*="support-banner"]','[class*="promo-card"]',
                'a[href*="ko-fi."]','a[href*="kofi."]','a[href*="buymeacoffee"]','a[href*="patreon."]',
                'a[href*="paypal."]','a[href*="opencollective"]','a[href*="liberapay"]',
                'a[href*="trakteer"]','a[href*="saweria."]','a[href*="sociabuzz"]','a[href*="donationalerts"]',
                'a[href*="github.com/sponsors"]','a[href*="gofundme"]','a[href*="kickstarter"]'
              ];
              var PROMO=/buy me a coffee|ko-?fi[.]?com|buymeacoffee|become a patron|become a sponsor|support (us|me|this|our) (on|via|through)|watch an ad to support|send extra love|make a donation|donate (now|today|to us|to this)|goal (achieved|completed)|supporters made this happen|sponsor (this|our) (repo|project|channel|extension)|fund (this|our) (repo|project|extension)|not affiliated with the cloudstream app|help (us )?keep [\w'\u2019 .-]{1,28}?(alive|free|running|online)|keep [\w'\u2019 .-]{1,28}?alive|keep it alive|zero support|extensions? die|no ads,? no (subscription|ads)|goal missed|monthly (goal|target)|active repo maintenance|maintain(ing)? \d+\+? (providers|repos|extensions)|can'?t donate|supporters? (this month|so far)|unlock (premium|ad-?free)|\$\s?\d{1,6}([,.]\d{3})?\s*\/\s*\$\s?\d{1,6}([,.]\d{3})?/i;
              var DISMISS=/^(maybe later|not now|no thanks|no, thanks|later|maybe next time|remind me later|skip|close|dismiss|\u2715|\u00d7|x)$/i;
              var lastDismiss=0;
              var MONEY=/ko-?fi[.]|buymeacoffee|patreon[.]com|paypal[.]|github[.]com\/sponsors|opencollective|liberapay|trakteer|saweria[.]|sociabuzz|donationalerts|gofundme|kickstarter|cash[.]app|venmo[.]com/i;
              function hide(e){
                try{
                  if(!e||!e.parentNode)return;
                  if(e.closest('video')||e.querySelector('video'))return;
                  e.style.setProperty('display','none','important');
                }catch(x){}
              }
              function clean(){
                var i,j,els;
                for(i=0;i<SEL.length;i++){
                  try{
                    els=document.querySelectorAll(SEL[i]);
                    for(j=0;j<els.length;j++)hide(els[j]);
                  }catch(x){}
                }
                try{
                  els=document.querySelectorAll('a,button,[role="button"]');
                  for(i=0;i<els.length;i++){
                    var a=els[i];
                    if(!a||!a.parentNode)continue;
                    var href=a.getAttribute('href')||a.getAttribute('data-url')||'';
                    if(!PROMO.test(a.textContent||'')&&!MONEY.test(href))continue;
                    hide(a);
                    var box=a.parentNode,up=0;
                    while(box&&up<6){
                      up++;
                      var t=(box.textContent||'').replace(/\s+/g,' ').trim();
                      if(t.length>0&&t.length<900&&PROMO.test(t)){hide(box);}
                      box=box.parentNode;
                    }
                  }
                }catch(x){}
                // A funding MODAL — the "Help keep <repo> alive" card a repo
                // overlays its own page with. It is a big fixed/absolute box
                // over the content, so it never looks like one of the links
                // above: it is hidden outright, and its own dismiss control
                // ("Maybe Later", "✕") is clicked, because a merely hidden
                // overlay still eats every tap on the page underneath.
                try{
                  els=document.querySelectorAll('div,section,aside,article,main,dialog');
                  var vw=window.innerWidth||0,vh=window.innerHeight||0;
                  for(i=0;i<els.length;i++){
                    var o=els[i];
                    if(!o||!o.parentNode)continue;
                    if(o.getAttribute('data-hikari-promo')==='1')continue;
                    if(o.closest('video')||o.querySelector('video'))continue;
                    var cs=getComputedStyle(o);
                    if(cs.position!=='fixed'&&cs.position!=='absolute')continue;
                    var r=o.getBoundingClientRect();
                    if(vw<=0||vh<=0)break;
                    if(r.width<vw*0.45||r.height<vh*0.35)continue;
                    var text=(o.textContent||'').replace(/\s+/g,' ').trim();
                    if(text.length===0||text.length>2500)continue;
                    if(!PROMO.test(text))continue;
                    o.setAttribute('data-hikari-promo','1');
                    var now=Date.now();
                    if(now-lastDismiss>1500){
                      var ctrls=o.querySelectorAll('button,a,[role="button"],span,div');
                      for(j=0;j<ctrls.length;j++){
                        var c=ctrls[j];
                        var ct=(c.textContent||'').replace(/\s+/g,' ').trim();
                        if(ct.length===0||ct.length>24)continue;
                        if(!DISMISS.test(ct))continue;
                        if(c.querySelector('button,a'))continue;
                        lastDismiss=now;
                        try{c.click();}catch(x){}
                        break;
                      }
                    }
                    hide(o);
                  }
                }catch(x){}
              }
              // Defer initial clean by 50ms so the page can paint first.
              if(document.readyState==='loading'){
                document.addEventListener('DOMContentLoaded',function(){setTimeout(clean,50);});
              }else{
                setTimeout(clean,50);
              }
              // Throttled: an ad-heavy extension page mutates constantly, and
              // walking every link on every mutation is work the page cannot
              // afford — one pass every 400ms is invisible to the user and
              // cheap enough to never fight the site's own scripts.
              var pending=false;
              function later(){
                if(pending)return;
                pending=true;
                setTimeout(function(){pending=false;clean();},400);
              }
              try{
                new MutationObserver(later).observe(document.documentElement,{childList:true,subtree:true});
              }catch(x){}
            })();
        """.trimIndent()

        /**
         * hls.js polyfill. Android WebView CANNOT decode HLS in a <video>, so
         * sites that feed the player a plain .m3u8 buffer forever. If the page
         * already runs its own hls.js (window.Hls exists) we reuse it; otherwise
         * we load hls.js from CDN and attach it to every m3u8 video, including
         * ones the site creates later. Videos the page already plays via a
         * blob: src (their own hls.js) are skipped so we never double-attach.
         */
        private val VIDEO_POLYFILL_JS = """
            (function(){
              if(window.__hikariHlsInit)return;
              window.__hikariHlsInit=true;
              function attach(v){
                try{
                  // Play inline instead of fullscreen — some players never
                  // start when the WebView tries to promote them.
                  v.setAttribute('playsinline','');
                  v.setAttribute('webkit-playsinline','');
                  if(v.__hikariHlsAttached)return;
                  var src=v.currentSrc||v.src||(v.querySelector('source')&&v.querySelector('source').src)||(v.getAttribute('data-src')||'');
                  if(!src||src.indexOf('.m3u8')<0)return;
                  if(src.indexOf('blob:')===0)return;
                  if(v.canPlayType('application/vnd.apple.mpegurl'))return;
                  if(!window.Hls||!window.Hls.isSupported())return;
                  var hls=new window.Hls({enableWorker:true,lowLatencyMode:true});
                  hls.loadSource(src);
                  hls.attachMedia(v);
                  v.__hikariHlsAttached=hls;
                }catch(e){}
              }
              function scan(){
                try{
                  var vs=document.querySelectorAll('video');
                  for(var i=0;i<vs.length;i++)attach(vs[i]);
                  var ss=document.querySelectorAll('video source');
                  for(var i=0;i<ss.length;i++){
                    var s=ss[i];
                    if(s.src&&s.src.indexOf('.m3u8')>=0&&s.parentElement)attach(s.parentElement);
                  }
                }catch(e){}
              }
              function boot(){
                scan();
                setInterval(scan,2000);
                try{
                  new MutationObserver(scan).observe(document.body||document.documentElement,{childList:true,subtree:true});
                }catch(e){}
              }
              if(window.Hls){boot();}
              else{
                var tried=[];
                function loadLib(src){
                  if(tried.indexOf(src)>=0)return;
                  tried.push(src);
                  var s=document.createElement('script');
                  s.src=src;
                  s.async=true;
                  s.onload=function(){boot();};
                  s.onerror=function(){};
                  document.head.appendChild(s);
                }
                loadLib('https://cdn.jsdelivr.net/npm/hls.js@1.7.4/dist/hls.min.js');
                setTimeout(function(){loadLib('https://unpkg.com/hls.js@1.7.4/dist/hls.min.js');},3500);
                setTimeout(function(){loadLib('https://cdnjs.cloudflare.com/ajax/libs/hls.js/1.7.4/hls.min.js');},7000);
              }
            })();
        """.trimIndent()

        /** Returns the current page's <video>/<source> URLs as a JSON array string. */
        private val VIDEO_SCAN_JS = """
            (function(){
              var out=[];
              try{
                document.querySelectorAll('video').forEach(function(v){
                  var s=v.currentSrc||v.src||(v.querySelector('source')&&v.querySelector('source').src)||(v.getAttribute('data-src')||'');
                  if(s&&s.indexOf('blob:')!==0)out.push(s);
                });
                document.querySelectorAll('video source').forEach(function(s){if(s.src&&s.src.indexOf('blob:')!==0)out.push(s.src);});
              }catch(e){}
              return JSON.stringify(out);
            })();
        """.trimIndent()

        /** True when any <video> on the page is actively playing (used to drop
         *  ad popups that players open on click during playback). */
        private val VIDEO_PLAYING_JS = """
            (function(){
              try{
                var vs=document.querySelectorAll('video');
                for(var i=0;i<vs.length;i++){
                  var v=vs[i];
                  if(v&&!v.paused&&!v.ended&&v.readyState>=2&&v.currentTime>0)return 'true';
                }
              }catch(e){}
              return 'false';
            })();
        """.trimIndent()

        /**
         * Watches every <video> on the page. If one has a real http(s) source
         * but has spent ~9 seconds trying to play without producing a single
         * frame (or failed outright), the site's player is never going to start
         * on this WebView — hand the exact source to the app so it plays in
         * Hikari's ExoPlayer instead of spinning forever.
         */
        private val STUCK_MONITOR_JS = """
            (function(){
              if(window.__hikariStuckMon)return;
              window.__hikariStuckMon=true;
              var state=new Map();
              var FIRE_AFTER=9000;
              function fire(v,src){
                if(v.__hikariStuckFired)return;
                v.__hikariStuckFired=true;
                try{HikariBridge.stuckVideo(src);}catch(e){}
              }
              function tick(){
                try{
                  document.querySelectorAll('video').forEach(function(v){
                    try{
                      var src=v.currentSrc||v.src||(v.querySelector('source')&&v.querySelector('source').src)||'';
                      if(!src||src.indexOf('http')!==0){state.delete(v);return;}
                      var prev=v.__hikariSrc;
                      if(prev!==src){v.__hikariSrc=src;v.__hikariStuckFired=false;state.delete(v);}
                      var stuck=(v.error!==null)||(!v.paused&&v.readyState<2&&!v.videoWidth);
                      if(stuck){
                        var st=state.get(v);
                        if(!st){st={since:Date.now()};state.set(v,st);}
                        if(Date.now()-st.since>=FIRE_AFTER)fire(v,src);
                      }else{
                        state.delete(v);
                      }
                    }catch(e){}
                  });
                }catch(e){}
              }
              setInterval(tick,2000);
              tick();
            })();
        """.trimIndent()

        /**
         * Element blocker. Always injected (guarded by a page-scoped flag) so
         * the menu's "Element blocker" toggle just flips
         * window.__hikariBlockerActive; the next tap on the page picks the
         * tapped element, computes a stable CSS selector, hides it, pushes it
         * to the registry + __hikariBlocks and calls
         * HikariBridge.blockElement(selector) so the app persists it. The
         * toggle switches itself off after one block. Persisted selectors from
         * __hikariBlocks are re-hidden on every load + on DOM mutations (SPAs
         * re-mount nodes), and __hikariRestoreSelector/__hikariRestoreAll undo
         * a block / all blocks without a reload.
         */
                /**
         * Element blocker. Always injected (guarded by a page-scoped flag).
         * Menu → "Element blocker" TOGGLES the mode: when on, the first tap
         * HIGHLIGHTS the element in red (outline + scroll into view) so you see
         * exactly what would be removed; a second tap on the SAME element hides
         * + persists it. Tapping a different element just moves the highlight.
         * Toggle off (or a completed block) clears the highlight.
         * Persisted selectors from __hikariBlocks are re-hidden on every load,
         * on DOM mutations AND on a 500ms interval — sites that re-render ads
         * (rotating GIF banners, SPA sliders) recreate containers with the same
         * selectors, which is what made blocked elements "come back".
         */
        /**
         * Watches the page's OWN network calls (fetch / XHR), its DOM and the
         * performance timeline, and reports every media URL to the bridge.
         *
         * Why it earns its keep over `shouldInterceptRequest`: an SPA can hold
         * a stream in JavaScript (a fetch response it parses, a URL assembled
         * at runtime) without ever issuing a request the WebView exposes as a
         * resource, and it knows the headers it used. That is the PlayBridge /
         * Nuvio `sniffer.js` idea, reduced to what Hikari's hand-off needs.
         */
        private val SNIFFER_JS = """
            (function(){
              if(window.__hikariSniffer)return;
              window.__hikariSniffer=true;
              var seen={};
              function poster(){
                try{
                  var m=document.querySelector('meta[property="og:image"],meta[name="twitter:image"]');
                  return m&&m.content?m.content:'';
                }catch(e){return ''}
              }
              function report(url,kind,headers,contentType){
                try{
                  if(!url||typeof url!=='string'||url.indexOf('http')!==0)return;
                  var key=kind+'|'+url;
                  if(seen[key])return;
                  seen[key]=true;
                  if(window.HikariBridge&&window.HikariBridge.sniff){
                    window.HikariBridge.sniff(JSON.stringify({
                      url:url,kind:kind||'',contentType:contentType||'',
                      poster:poster(),headers:headers||{}
                    }));
                  }
                }catch(e){}
              }
              function extract(text){
                if(!text||typeof text!=='string'||text.length>524288)return;
                try{
                  var cleaned=text.replace(/\\\//g,'/');
                  var re=/https?:\/\/[^\s"'<>]+?\.(?:m3u8|mpd|mp4|webm|mkv|mov|m4v)(?:\?[^\s"'<>]*)?/gi;
                  var m,count=0;
                  while((m=re.exec(cleaned))&&count<25){report(m[0],'page',{},'');count++}
                }catch(e){}
              }
              function scanPage(){
                try{
                  try{
                    if(document.getElementsByTagName('*').length<4000){
                      extract(document.documentElement?document.documentElement.innerHTML:'');
                    }
                  }catch(e){}
                  var links=document.querySelectorAll('video source,video,a[href],link[rel="preload"]');
                  for(var i=0;i<links.length&&i<120;i++){
                    var el=links[i];
                    var u=el.currentSrc||el.src||el.href||el.getAttribute('data-src')||'';
                    if(u)report(u,'dom',{},'');
                  }
                  var metas=document.querySelectorAll('meta[property="og:video"],meta[property="og:video:url"],meta[name="twitter:player:stream"]');
                  for(var j=0;j<metas.length;j++){if(metas[j].content)report(metas[j].content,'dom',{},'')}
                  try{
                    var entries=performance.getEntriesByType('resource')||[];
                    for(var k=0;k<entries.length&&k<400;k++){
                      var n=entries[k].name||'';
                      if(/\.(m3u8|mpd|mp4|webm)(\?|${'$'})/i.test(n))report(n,'perf',{},'');
                    }
                  }catch(e){}
                }catch(e){}
              }
              try{
                var origFetch=window.fetch;
                if(origFetch){
                  window.fetch=function(input,init){
                    var url=typeof input==='string'?input:(input&&input.url)||'';
                    var headers={};
                    try{
                      if(init&&init.headers){
                        if(init.headers.forEach)init.headers.forEach(function(v,k){headers[k]=v});
                        else for(var k in init.headers)headers[k]=init.headers[k];
                      }
                    }catch(e){}
                    report(url,'fetch',headers,'');
                    var promise=origFetch.apply(window,arguments);
                    try{
                      promise.then(function(res){
                        var ct=(res&&res.headers&&res.headers.get('content-type'))||'';
                        report(url,'fetch',headers,ct);
                        if(/text|json|javascript|mpegurl|dash/i.test(ct)){
                          res.clone().text().then(extract).catch(function(){});
                        }
                      }).catch(function(){});
                    }catch(e){}
                    return promise;
                  };
                }
              }catch(e){}
              try{
                var XHR=window.XMLHttpRequest;
                if(XHR){
                  var open=XHR.prototype.open,send=XHR.prototype.send,setH=XHR.prototype.setRequestHeader;
                  XHR.prototype.open=function(method,url){this.__hkUrl=url;this.__hkHeaders={};return open.apply(this,arguments)};
                  XHR.prototype.setRequestHeader=function(k,v){
                    try{this.__hkHeaders=this.__hkHeaders||{};this.__hkHeaders[k]=v}catch(e){}
                    return setH.apply(this,arguments);
                  };
                  XHR.prototype.send=function(body){
                    var xhr=this;
                    try{
                      report(xhr.__hkUrl,'xhr',xhr.__hkHeaders||{},'');
                      xhr.addEventListener('load',function(){
                        try{
                          var ct=xhr.getResponseHeader('content-type')||'';
                          report(xhr.__hkUrl,'xhr',xhr.__hkHeaders||{},ct);
                          if(/text|json|javascript|mpegurl|dash/i.test(ct)){
                            var t=xhr.responseText||'';
                            if(t.length<524288)extract(t);
                          }
                        }catch(e){}
                      });
                    }catch(e){}
                    return send.apply(this,arguments);
                  };
                }
              }catch(e){}
              scanPage();
              try{new MutationObserver(scanPage).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}
              setInterval(scanPage,4000);
            })();
        """

        /**
         * Small document-start compatibility shims.
         *
         * Sites' own players test `window.chrome` (and a non-empty plugin
         * list) as "is this a real browser", and refuse to initialise the
         * player when the object is missing. WebView does not provide
         * `window.chrome`, so a page that plays in Chrome can sit on a black
         * rectangle here. The shims are deliberately inert: no fingerprint
         * spoofing, no automation flags, nothing a normal browser would not
         * answer.
         *
         * Notification prompts gain the same treatment: a page may only ask
         * while the user is actually interacting with it, which is what
         * browsers do and what stops the "allow notifications?" card that
         * appears the instant a video page loads.
         */
        private val COMPAT_JS = """
            (function(){
              try{
                if(!window.chrome)window.chrome={};
                if(!window.chrome.runtime)window.chrome.runtime={};
              }catch(e){}
              try{
                if(window.Notification&&Notification.requestPermission){
                  var original=Notification.requestPermission.bind(Notification);
                  Notification.requestPermission=function(){
                    try{
                      if(!(navigator.userActivation&&navigator.userActivation.isActive)){
                        return Promise.resolve('denied');
                      }
                    }catch(e){}
                    return original.apply(null,arguments);
                  };
                }
              }catch(e){}
            })();
        """

        /**
         * Hides full-screen AD UIs that live INSIDE the page.
         *
         * The request blocker can stop the ad's network traffic, but a site is
         * free to draw its own "click to watch" interstitial over the content
         * with a plain div: nothing to block, and it sits on top of the page
         * until dismissed. Animepahe's episode link is exactly this — the
         * player never appears because an illustration + "Watch!" panel covers
         * the page.
         *
         * The rule is deliberately narrow: an element must be positioned over
         * at least half the viewport, sit above z-index 40, carry an ad-like
         * id/class (or wrap a known ad frame), and must NOT contain a
         * `<video>`/`<audio>` — so a player, a cookie banner with a neutral
         * name, or a site's own dialog is never touched. Anything hidden comes
         * back on reload, which makes a false positive a refresh away.
         */
        private val INTERSTITIAL_KILL_JS = """
            (function(){
              if(window.__hkInterstitialInit)return;
              window.__hkInterstitialInit=true;
              var AD=/(^|[-_ ])(ads?|advert(isement)?|sponsor(ed)?|promo(ted)?|popup|popunder|interstitial|overlay-ad|ad-overlay|adbox|ad-slot|adunit)([-_ ]|${'$'})/i;
              function kill(el){
                try{
                  if(!el||el.__hkKilled)return;
                  var cs=getComputedStyle(el);
                  if(!cs)return;
                  if(cs.position!=='fixed'&&cs.position!=='absolute')return;
                  var r=el.getBoundingClientRect();
                  var vw=innerWidth||1, vh=innerHeight||1;
                  if(r.width<=0||r.height<=0)return;
                  if(r.width*r.height < vw*vh*0.5)return;
                  var z=parseInt(cs.zIndex||'0',10)||0;
                  if(z<40)return;
                  if(el.querySelector('video,audio'))return;
                  var sig=(el.id||'')+' '+(typeof el.className==='string'?el.className:'');
                  var adFrame=el.querySelector('iframe[src*="doubleclick"],iframe[src*="googlesyndication"],iframe[src*="adservice"],iframe[src*="adsystem"],a[href*=".cyou"],a[href*=".cfd"]');
                  if(!AD.test(sig)&&!adFrame)return;
                  el.style.setProperty('display','none','important');
                  el.__hkKilled=1;
                }catch(e){}
              }
              function scan(){
                var els=document.querySelectorAll('div,section,aside,ins');
                for(var i=0;i<els.length;i++)kill(els[i]);
              }
              scan();
              try{new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}
              setInterval(scan,2000);
            })();
        """

        private val ELEMENT_BLOCK_JS = """
            (function(){
              if(window.__hikariBlockerInit)return;
              window.__hikariBlockerInit=true;
              window.__hikariBlockerActive=false;
              window.__hikariBlocks=window.__hikariBlocks||[];
              window.__hikariBlockRegistry=[];
              window.__hikariPendingEl=null;
              function clearPending(){
                if(window.__hikariPendingEl){
                  try{
                    window.__hikariPendingEl.style.removeProperty('outline');
                    window.__hikariPendingEl.style.removeProperty('outline-offset');
                  }catch(e){}
                  window.__hikariPendingEl=null;
                }
              }
              // ---- adaptive relocation -------------------------------------
              // A blocked element is identified by more than its CSS selector.
              // Sites re-render constantly and the selector stops matching; the
              // fingerprint below is the Scrapling idea, reduced to what a
              // browser needs: tag, text, the identifying attributes, the short
              // ancestor path and the parent. When the selector misses, the
              // best candidate above 55% is hidden instead, so a block survives
              // a redesign without ever guessing wildly.
              function hkText(e){
                try{return (e.innerText||e.textContent||'').replace(/\s+/g,' ').trim().slice(0,120)}catch(x){return ''}
              }
              function hkFingerprint(e){
                try{
                  var attrs={};
                  ['class','id','href','src','role','data-testid'].forEach(function(a){
                    var v=e.getAttribute&&e.getAttribute(a);
                    if(v)attrs[a]=String(v).slice(0,120);
                  });
                  var path=[];var n=e;
                  for(var i=0;i<4&&n&&n.nodeType===1;i++){
                    path.unshift(n.tagName.toLowerCase());n=n.parentElement;
                  }
                  return {
                    tag:e.tagName.toLowerCase(),
                    text:hkText(e),
                    attrs:attrs,
                    path:path.join('>'),
                    parent:e.parentElement?e.parentElement.tagName.toLowerCase():'',
                    parentClass:e.parentElement?String(e.parentElement.className||'').slice(0,80):''
                  };
                }catch(x){return null}
              }
              function hkSim(a,b){
                if(!a||!b)return 0;
                var x=String(a).toLowerCase(),y=String(b).toLowerCase();
                if(x===y)return 1;
                if(x.indexOf(y)>=0||y.indexOf(x)>=0)return 0.8;
                var A=x.split(/\s+/),B=y.split(/\s+/),hit=0;
                for(var i=0;i<A.length;i++)if(B.indexOf(A[i])>=0)hit++;
                return A.length?hit/A.length:0;
              }
              function hkScore(fp,e){
                var c=hkFingerprint(e);if(!c)return 0;
                var total=0,checks=0;
                total+=(fp.tag===c.tag?1:0);checks++;
                if(fp.text){total+=hkSim(fp.text,c.text);checks++}
                var keys=Object.keys(fp.attrs||{});
                if(keys.length){
                  var hit=0;
                  for(var i=0;i<keys.length;i++){
                    hit+=hkSim(fp.attrs[keys[i]],(c.attrs||{})[keys[i]]||'');
                  }
                  total+=hit/keys.length;checks++;
                }
                total+=hkSim(fp.path,c.path);checks++;
                if(fp.parent){
                  total+=hkSim(fp.parent,c.parent);checks++;
                  total+=hkSim(fp.parentClass,c.parentClass);checks++;
                }
                return checks?total/checks:0;
              }
              function hkAdaptiveHide(sel){
                var raw=null;
                try{raw=localStorage.getItem('hk_fp_'+sel)}catch(e){}
                if(!raw)return false;
                var fp=null;
                try{fp=JSON.parse(raw)}catch(e){return false}
                if(!fp)return false;
                var best=null,bestScore=0;
                var cands=document.querySelectorAll('div,section,aside,ins,iframe,img,a');
                for(var i=0;i<cands.length&&i<4000;i++){
                  var e=cands[i];
                  if(!e||!e.parentNode)continue;
                  if(e.querySelector&&e.querySelector('video,audio'))continue;
                  var sc=hkScore(fp,e);
                  if(sc>bestScore){bestScore=sc;best=e}
                }
                if(best&&bestScore>=0.55){
                  if(!best.__hikariBlocked){
                    best.__hikariBlocked=true;
                    window.__hikariBlockRegistry.push({el:best,sel:sel});
                  }
                  best.style.setProperty('display','none','important');
                  best.style.setProperty('pointer-events','none','important');
                  return true;
                }
                return false;
              }
              function hideAll(){
                var sels=window.__hikariBlocks||[];
                for(var i=0;i<sels.length;i++){
                  try{
                    var els=document.querySelectorAll(sels[i]);
                    if(!els.length){hkAdaptiveHide(sels[i]);continue}
                    for(var j=0;j<els.length;j++){
                      var e=els[j];
                      if(!e||!e.parentNode)continue;
                      // Re-apply every pass: sites reset inline styles and the
                      // element would otherwise flicker back into view.
                      if(!e.__hikariBlocked){
                        e.__hikariBlocked=true;
                        window.__hikariBlockRegistry.push({el:e,sel:sels[i]});
                        // Remember what this element looked like the first time,
                        // so the fallback has something to score against.
                        try{
                          if(!localStorage.getItem('hk_fp_'+sels[i])){
                            var fp=hkFingerprint(e);
                            if(fp)localStorage.setItem('hk_fp_'+sels[i],JSON.stringify(fp));
                          }
                        }catch(err){}
                      }
                      e.style.setProperty('display','none','important');
                      e.style.setProperty('pointer-events','none','important');
                    }
                  }catch(e){}
                }
              }
              window.__hikariHideAll=hideAll;
              function restoreOne(sel){
                var rem=[];
                for(var i=0;i<window.__hikariBlockRegistry.length;i++){
                  var en=window.__hikariBlockRegistry[i];
                  if(en.sel===sel){
                    try{
                      en.el.__hikariBlocked=false;
                      en.el.style.removeProperty('display');
                      en.el.style.removeProperty('pointer-events');
                    }catch(e){}
                    rem.push(i);
                  }
                }
                for(var k=rem.length-1;k>=0;k--)window.__hikariBlockRegistry.splice(rem[k],1);
                var bi=window.__hikariBlocks.indexOf(sel);
                if(bi>=0)window.__hikariBlocks.splice(bi,1);
              }
              window.__hikariRestoreSelector=restoreOne;
              function restoreAll(){
                for(var i=0;i<window.__hikariBlockRegistry.length;i++){
                  var en=window.__hikariBlockRegistry[i];
                  try{
                    en.el.__hikariBlocked=false;
                    en.el.style.removeProperty('display');
                    en.el.style.removeProperty('pointer-events');
                  }catch(e){}
                }
                window.__hikariBlockRegistry=[];
                window.__hikariBlocks=[];
              }
              window.__hikariRestoreAll=restoreAll;
              window.__hikariBlockerToggle=function(){
                window.__hikariBlockerActive=!window.__hikariBlockerActive;
                if(!window.__hikariBlockerActive)clearPending();
                return window.__hikariBlockerActive;
              };
              function esc(s){return String(s).replace(/[^a-zA-Z0-9_-]/g,function(c){return '\\'+c;});}
              function buildSelector(el){
                if(!el||el===document.documentElement||el===document.body)return '';
                if(el.id){return '#'+esc(el.id);}
                var t=el;
                for(var k=0;k<4;k++){
                  var r=t.getBoundingClientRect?t.getBoundingClientRect():null;
                  if(r&&(r.width>=24&&r.height>=16))break;
                  if(!t.parentElement||t.parentElement===document.body)break;
                  t=t.parentElement;
                }
                var parts=[];
                var cur=t;
                while(cur&&cur!==document.documentElement&&parts.length<3){
                  var tag=(cur.tagName||'').toLowerCase();
                  if(!tag)break;
                  var seg=tag;
                  var cls=[].slice.call(cur.classList).filter(function(c){return c&&c.length>1&&!c.match(/^(css-|sc-|_)/);});
                  if(cls.length)seg+='.'+cls.slice(0,2).map(esc).join('.');
                  var parent=cur.parentElement;
                  if(parent){
                    var sameTag=[].slice.call(parent.children).filter(function(s){return (s.tagName||'').toLowerCase()===tag;});
                    if(sameTag.length>1)seg+=':nth-of-type('+(sameTag.indexOf(cur)+1)+')';
                  }
                  parts.unshift(seg);
                  cur=cur.parentElement;
                }
                return parts.join(' > ');
              }
              function highlight(el){
                clearPending();
                try{
                  el.style.setProperty('outline','3px solid #ff1744','important');
                  el.style.setProperty('outline-offset','2px','important');
                  el.scrollIntoView({block:'center',inline:'center',behavior:'smooth'});
                }catch(e){}
                window.__hikariPendingEl=el;
              }
              function block(el){
                clearPending();
                var sel=buildSelector(el);
                if(!sel){window.__hikariBlockerActive=false;return;}
                el.__hikariBlocked=true;
                el.style.setProperty('display','none','important');
                el.style.setProperty('pointer-events','none','important');
                window.__hikariBlockRegistry.push({el:el,sel:sel});
                window.__hikariBlocks.push(sel);
                window.__hikariBlockerActive=false;
                try{HikariBridge.blockElement(sel);}catch(e){}
              }
              document.addEventListener('pointerdown',function(e){
                if(!window.__hikariBlockerActive)return;
                e.stopImmediatePropagation();
                e.preventDefault();
              },true);
              document.addEventListener('click',function(e){
                if(!window.__hikariBlockerActive)return;
                e.stopImmediatePropagation();
                e.preventDefault();
                var el=e.target&&e.target.nodeType===3?e.target.parentElement:e.target;
                if(window.__hikariPendingEl===el){
                  block(el);
                }else{
                  highlight(el);
                }
              },true);
              try{new MutationObserver(hideAll).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}
              hideAll();
              setInterval(hideAll,500);
            })();
        """.trimIndent()

        /**
         * Auto-translate (per-extension, English only). Walks the page's text
         * nodes, batches the unique strings to `HikariBridge.translate()` (the
         * app fetches Google's gtx endpoint — no CORS issue, no API key) and
         * swaps the translated text back in place. Works for ANY language
         * (CJK, Korean, accented Latin, Cyrillic, …): text that's already
         * English is recognized by its stopwords and left alone, everything
         * else is auto-detected by the translator and turned into English.
         */
        private val TRANSLATE_JS = """
            (function(){
              if(window.__hikariTranslateInit)return;
              window.__hikariTranslateInit=true;
              var cache={};        // original text -> translated text
              var done={};         // original text that can't be translated (skip forever)
              var texts={};        // original text -> [text nodes waiting]
              var pending={};      // request id -> {keys:[], nodes:[[]]}
              var inFlight=false;
              var reqCounter=0;
              var walker=null;
              var applied=new WeakSet();
              // English stopwords: a pure-ASCII text containing any of these is
              // almost certainly already English and is skipped (saves the
              // translation round-trips). Everything else — CJK, Korean,
              // accented Latin, Cyrillic, or ASCII without stopwords — is sent
              // to the translator, which auto-detects the language.
              var EN_STOP=/^(the|and|of|to|in|is|are|was|were|for|with|on|at|by|this|that|these|those|you|your|we|our|they|their|them|it|its|a|an|or|but|as|from|not|be|have|has|had|i|me|my|do|does|did|what|which|who|when|where|why|there|here|can|will|would|should|could|then|than|so|if|up|out|about|just|more|most|all|any|some|also|only|into|over|under|no|yes)$/i;
              function looksEnglish(t){
                var m=t.match(/[A-Za-z]+/g);
                if(!m)return false;
                for(var i=0;i<m.length;i++){if(EN_STOP.test(m[i]))return true;}
                return false;
              }
              function worth(text){
                var t=(text||'').trim();
                if(t.length<2)return false;
                if(!/[A-Za-z\u00C0-\u024F\u0370-\u03FF\u0400-\u04FF\u3040-\u30FF\u3400-\u9FFF\uAC00-\uD7AF\uF900-\uFAFF]/.test(t))return false;
                if(/^[\d\s.,!?%$#@&*()\/\-+='"<>\[\]{}|\\:;_~^`\u00A0]+$/.test(t))return false;
                if(cache[t]||done[t])return false;
                if(/^[\x00-\x7F]+$/.test(t)&&looksEnglish(t))return false;
                return true;
              }
              function shouldSkip(node){
                var p=node.parentElement;
                if(!p)return true;
                var tag=p.tagName;
                if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT'||tag==='TEXTAREA'||tag==='INPUT'||tag==='SELECT'||tag==='OPTION'||tag==='CODE'||tag==='PRE'||tag==='IFRAME')return true;
                if(p.isContentEditable)return true;
                if(tag==='A'){var h=(p.getAttribute('href')||'').trim();if(h&&(node.nodeValue||'').trim()===h)return true;}
                if(applied.has(node))return true;
                return false;
              }
              function process(node){
                var v=node.nodeValue;
                if(!v||!v.trim())return;
                if(cache[v]){
                  if(node.nodeValue===v){node.nodeValue=cache[v];applied.add(node);}
                  return;
                }
                if(!worth(v))return;
                (texts[v]=texts[v]||[]).push(node);
              }
              function flush(){
                if(inFlight)return;
                var keys=Object.keys(texts);
                if(!keys.length)return;
                var batch=[],nodes=[];
                for(var i=0;i<keys.length&&batch.length<40;i++){
                  var k=keys[i];
                  var list=texts[k]||[];
                  var alive=false;
                  for(var j=0;j<list.length;j++){if(list[j]&&list[j].parentNode){alive=true;break;}}
                  if(!alive){delete texts[k];continue;}
                  batch.push(k);
                  nodes.push(list);
                  delete texts[k];
                }
                if(!batch.length)return;
                var id='t'+(reqCounter++);
                pending[id]={keys:batch,nodes:nodes};
                inFlight=true;
                try{window.HikariBridge.translate(id,JSON.stringify(batch));}
                catch(e){inFlight=false;delete pending[id];}
              }
              window.__hikariTransResult=function(id,results){
                var p=pending[id];
                delete pending[id];
                if(!p){inFlight=false;return;}
                for(var i=0;i<p.keys.length;i++){
                  var orig=p.keys[i];
                  var tr=(results&&results[i])||'';
                  if(!tr||!tr.trim()||tr===orig){done[orig]=true;continue;}
                  cache[orig]=tr;
                  var list=p.nodes[i]||[];
                  for(var j=0;j<list.length;j++){
                    var n=list[j];
                    if(n&&n.parentNode&&n.nodeValue===orig){n.nodeValue=tr;applied.add(n);}
                  }
                }
                inFlight=false;
                setTimeout(function(){scan();},50);
              };
              function scan(){
                try{
                  if(!document.body){setTimeout(scan,400);return;}
                  if(walker&&walker.currentNode&&!document.documentElement.contains(walker.currentNode))walker=null;
                  if(!walker)walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null);
                  var n,count=0,exhausted=true;
                  while((n=walker.nextNode())){
                    if(!n.nodeValue||!n.nodeValue.trim())continue;
                    if(shouldSkip(n))continue;
                    process(n);
                    if(++count>=1500){exhausted=false;break;}
                  }
                  if(exhausted)walker=null;
                  flush();
                }catch(e){walker=null;}
              }
              var timer=null;
              function schedule(){if(timer)return;timer=setTimeout(function(){timer=null;scan();},250);}
              try{new MutationObserver(schedule).observe(document.documentElement,{childList:true,subtree:true,characterData:true});}catch(e){}
              setInterval(scan,4000);
              setTimeout(scan,150);
            })();
        """.trimIndent()
    }
}
