package com.hikari.app.web

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The synchronous "Always block" / "Always unblock" memory for the browser's
 * two kinds of link hand-off:
 *
 *  - a **site redirect** to a host the user chose to block (or never ask about
 *    again), and
 *  - an **external app call** (`intent://`, `market://`, `mailto:`, `tel:`, a
 *    custom scheme) the user chose to block or allow.
 *
 * It is deliberately separate from the ad-block list and from the existing
 * "Allowed redirect links" list:
 *
 *  - "Always block" here means "do not ask me about this redirect/app link",
 *    not "block every request to this site";
 *  - the site allow list stays in AppStore/RedirectAllow because Settings
 *    already edits it;
 *  - app calls need keys (`pkg:com.foo`, `app:mailto`) that are not hosts.
 *
 * SharedPreferences (not DataStore) because the WebView's redirect and app-call
 * gates must answer synchronously, before a page's first request. The Settings
 * screen reads [state] to show the counts and clear them.
 */
object WebViewLinkGuard {

    data class State(
        val blockedRedirects: Set<String> = emptySet(),
        val allowedApps: Set<String> = emptySet(),
        val blockedApps: Set<String> = emptySet(),
    )

    private const val PREFS = "webview_link_guard"
    private const val REDIRECT_BLOCK = "redirect_block"
    private const val APP_ALLOW = "app_allow"
    private const val APP_BLOCK = "app_block"

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _state.value = State(
            blockedRedirects = p.getStringSet(REDIRECT_BLOCK, emptySet()).orEmpty().toSet(),
            allowedApps = p.getStringSet(APP_ALLOW, emptySet()).orEmpty().toSet(),
            blockedApps = p.getStringSet(APP_BLOCK, emptySet()).orEmpty().toSet(),
        )
    }

    /** Hosts are matched without `www.`, a port or a trailing dot. A link with
     *  no host falls back to the whole URL so it can still be remembered. */
    fun redirectKey(host: String?, url: String?): String {
        val h = host?.trim()?.trimEnd('.')?.lowercase()?.removePrefix("www.").orEmpty()
        if (h.isNotBlank()) return h
        return url?.trim()?.lowercase()?.trimEnd('/').orEmpty()
    }

    fun isRedirectBlocked(host: String?, url: String?): Boolean {
        val key = redirectKey(host, url)
        return key.isNotBlank() && key in _state.value.blockedRedirects
    }

    @Synchronized
    fun blockRedirect(host: String?, url: String?) {
        val key = redirectKey(host, url)
        if (key.isBlank()) return
        val next = _state.value.blockedRedirects + key
        _state.value = _state.value.copy(blockedRedirects = next)
        prefs?.edit()?.putStringSet(REDIRECT_BLOCK, next)?.apply()
    }

    @Synchronized
    fun unblockRedirect(host: String?, url: String?) {
        val key = redirectKey(host, url)
        if (key.isBlank()) return
        val next = _state.value.blockedRedirects - key
        _state.value = _state.value.copy(blockedRedirects = next)
        prefs?.edit()?.putStringSet(REDIRECT_BLOCK, next)?.apply()
    }

    fun normalizeAppKey(key: String): String = key.trim().lowercase()

    fun isAppAllowed(key: String): Boolean =
        normalizeAppKey(key) in _state.value.allowedApps

    fun isAppBlocked(key: String): Boolean =
        normalizeAppKey(key) in _state.value.blockedApps

    @Synchronized
    fun allowApp(key: String) {
        val k = normalizeAppKey(key)
        if (k.isBlank()) return
        val allow = _state.value.allowedApps + k
        val block = _state.value.blockedApps - k
        _state.value = _state.value.copy(allowedApps = allow, blockedApps = block)
        prefs?.edit()?.putStringSet(APP_ALLOW, allow)?.putStringSet(APP_BLOCK, block)?.apply()
    }

    @Synchronized
    fun blockApp(key: String) {
        val k = normalizeAppKey(key)
        if (k.isBlank()) return
        val block = _state.value.blockedApps + k
        val allow = _state.value.allowedApps - k
        _state.value = _state.value.copy(allowedApps = allow, blockedApps = block)
        prefs?.edit()?.putStringSet(APP_ALLOW, allow)?.putStringSet(APP_BLOCK, block)?.apply()
    }

    /** Forget one app link decision (both the block and allow side). */
    @Synchronized
    fun forgetApp(key: String) {
        val k = normalizeAppKey(key)
        if (k.isBlank()) return
        val allow = _state.value.allowedApps - k
        val block = _state.value.blockedApps - k
        _state.value = _state.value.copy(allowedApps = allow, blockedApps = block)
        prefs?.edit()?.putStringSet(APP_ALLOW, allow)?.putStringSet(APP_BLOCK, block)?.apply()
    }

    @Synchronized
    fun clearRedirects() {
        _state.value = _state.value.copy(blockedRedirects = emptySet())
        prefs?.edit()?.remove(REDIRECT_BLOCK)?.apply()
    }

    @Synchronized
    fun clearApps() {
        _state.value = _state.value.copy(allowedApps = emptySet(), blockedApps = emptySet())
        prefs?.edit()?.remove(APP_ALLOW)?.remove(APP_BLOCK)?.apply()
    }

    fun clearAll() {
        clearRedirects()
        clearApps()
    }
}
