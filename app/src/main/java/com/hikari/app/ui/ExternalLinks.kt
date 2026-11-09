package com.hikari.app.ui

import com.hikari.app.i18n.I18n
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hikari.app.web.WebViewActivity

/** Hikari's Telegram group — help, bug reports, title requests and release
 *  news. Single source of truth for every place that links to it. */
const val TELEGRAM_CHANNEL_URL = "https://t.me/CodegeasseHikari"

/** Hikari's subreddit — help, discussion and release news. */
const val REDDIT_COMMUNITY_URL = "https://www.reddit.com/r/HikariApp"

/** Hikari's Discord server — help, discussion and release news. */
const val DISCORD_INVITE_URL = "https://discord.gg/hdwB78Ahq"

/** The developer's tip jar, linked from Home and Settings → About & Updates. */
const val PAYPAL_DONATE_URL = "https://www.paypal.me/Robbdeeze"

/**
 * Opens a YouTube video in the YouTube app rather than Hikari's own WebView.
 *
 * The in-app WebView is the right place for extension/watch pages, but it is
 * the wrong place for YouTube: the site redirects `www.youtube.com` to
 * `m.youtube.com`, and the WebView's redirect protection (Settings → WebView
 * safety) blocks that hop, so the trailer page just sits on a black screen.
 * Handing the id to the YouTube app avoids the redirect entirely.
 *
 * Tried in order, first one that starts wins:
 *  1. the explicit `vnd.youtube:<id>` scheme — the reliable way to force the
 *     YouTube app, and to offer the system chooser when another handler
 *     (ReVanced, NewPipe...) is installed;
 *  2. the plain https watch link — the YouTube app again when its App Links
 *     are verified, else a browser;
 *  3. [WebViewActivity] as a last resort so the tap is never a no-op.
 *
 * Never uses [android.content.pm.PackageManager.resolveActivity] to probe:
 * on API 30+ package visibility would report "not installed" for the YouTube
 * app unless it is declared in `<queries>`, while starting the implicit
 * intent works regardless.
 */
fun openYouTubeVideo(context: Context, videoId: String, title: String? = null): Boolean {
    val id = videoId.trim()
    if (id.isEmpty()) return false
    val watchUrl = "https://www.youtube.com/watch?v=$id"

    if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")))) return true
    if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)))) return true

    val webView = Intent(context, WebViewActivity::class.java).apply {
        putExtra("url", watchUrl)
        if (!title.isNullOrBlank()) putExtra("title", title)
    }
    if (launch(context, webView)) return true

    Toast.makeText(context, I18n.t("Couldn't open YouTube"), Toast.LENGTH_SHORT).show()
    return false
}

/**
 * Opens a Telegram channel/group (a `https://t.me/...` link) in the Telegram
 * app, falling back to the user's browser — deliberately NEVER Hikari's own
 * WebView. Telegram's t.me pages hand off to the app anyway, and the WebView's
 * redirect/popup protection turns that hand-off into a dead end, so the user
 * would just stare at a page they can't log into.
 *
 * The `tg://resolve?domain=<handle>` scheme is tried first because it targets
 * the app directly even on devices where Telegram's App Links for t.me are not
 * verified (where the https link would silently land in a browser).
 */
fun openTelegram(context: Context, url: String = TELEGRAM_CHANNEL_URL): Boolean {
    val handle = telegramHandle(url)
    if (handle != null &&
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$handle")))
    ) {
        return true
    }
    if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) return true

    Toast.makeText(context, I18n.t("Couldn't open Telegram"), Toast.LENGTH_SHORT).show()
    return false
}

/** The `@handle` out of a t.me link (or a `tg://resolve?domain=` link), so the
 *  app scheme can be built from the same URL the user sees. Null when [url] is
 *  not a recognizable Telegram link. */
private fun telegramHandle(url: String): String? {
    val trimmed = url.trim()
    val handle = when {
        trimmed.startsWith("tg://resolve?domain=", ignoreCase = true) ->
            trimmed.substringAfter("domain=").substringBefore('&')

        else -> trimmed.substringAfter("t.me/", "").substringBefore('/').substringBefore('?')
    }
    return handle.trim().ifBlank { null }
}

/**
 * Opens a community link (Reddit, Discord, …) in the matching app when it is
 * installed, else in whatever the device uses for links. Community links are
 * app hand-offs, not pages to browse in Hikari, so they deliberately bypass
 * [openInAppBrowser].
 */
fun openCommunity(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) return true
    Toast.makeText(context, I18n.t("Couldn't open the link"), Toast.LENGTH_SHORT).show()
    return false
}

/**
 * Opens a web page in Hikari's own WebView.
 *
 * Every "open page"/"read more"/"visit site" action inside the app lands here:
 * the in-app browser keeps the user in Hikari, shares the app's cookies and
 * ad-blocking rules, and cannot dump them into a different browser's UI for a
 * page that was only meant to be a quick look. Only a launch that throws falls
 * back to the system browser, and even that is reported honestly.
 */
fun openInAppBrowser(context: Context, url: String, title: String? = null): Boolean {
    if (url.isBlank()) return false
    val web = Intent(context, WebViewActivity::class.java).apply {
        putExtra("url", url)
        if (!title.isNullOrBlank()) putExtra("title", title)
    }
    if (launch(context, web)) return true
    if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) return true
    Toast.makeText(context, I18n.t("Couldn't open the link"), Toast.LENGTH_SHORT).show()
    return false
}

/**
 * Opens an ordinary https page in the in-app browser.
 *
 * Kept as the short name used by the tracker/account dialogs. Those flows used
 * to demand a real browser; the in-app WebView keeps its own cookie jar, so a
 * login made there survives and the app's redirect/popup protection stays
 * active for the page.
 *
 * Never silent: false means "nothing was opened" and every caller must fall
 * back or say so — a tap that dies inside here is the whole class of "dead
 * link" reports.
 */
fun openUrl(context: Context, url: String): Boolean = openInAppBrowser(context, url)

/**
 * Hosts that must keep their native-app hand-off instead of opening in the
 * WebView: YouTube (its site redirects to m.youtube, which WebView safety
 * blocks), Telegram and the community apps, plus sign-in/payment providers
 * that refuse to run inside an embedded browser.
 */
private val EXTERNAL_HANDOFF_HOSTS = listOf(
    "youtube.com", "youtu.be", "t.me", "telegram.me", "telegram.dog",
    "reddit.com", "redd.it", "discord.gg", "discord.com",
    "accounts.google.com", "paypal.me", "paypal.com",
)

/**
 * Rewrites an implicit `ACTION_VIEW http(s)` intent into Hikari's WebView.
 *
 * This is the app-wide safety net behind [openInAppBrowser]: extensions,
 * settings dialogs and any future "visit site" action that starts a plain
 * browser intent still end up in the in-app browser. Explicit intents (a
 * component or package is set), non-web schemes and the hand-off hosts above
 * are returned untouched, so app links, the installer and WebViewActivity's
 * own "Open in browser" menu keep working.
 */
fun routeWebIntent(context: Context, intent: Intent): Intent {
    if (intent.action != Intent.ACTION_VIEW) return intent
    if (intent.component != null || intent.`package` != null) return intent
    val data = intent.data ?: return intent
    val scheme = data.scheme?.lowercase() ?: return intent
    if (scheme != "http" && scheme != "https") return intent
    val host = data.host?.lowercase()?.trimEnd('.') ?: return intent
    if (EXTERNAL_HANDOFF_HOSTS.any { host == it || host.endsWith(".$it") }) return intent
    return Intent(context, WebViewActivity::class.java).apply {
        putExtra("url", data.toString())
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Starts [intent], tagging it with NEW_TASK when [context] is not an Activity
 *  (e.g. an application context) so Android doesn't reject the launch. Returns
 *  false when nothing can handle it — the caller falls through to the next
 *  option.
 *
 *  Stamped via [com.hikari.app.net.ExtensionUiGuard.runTrusted]: this is our
 *  own UI opening a browser/app the user asked for, so the extension
 *  auto-open guard lets it through without stack inference (which convicted
 *  a library frame and silently killed tracker login taps). */
private fun launch(context: Context, intent: Intent): Boolean {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        com.hikari.app.net.ExtensionUiGuard.runTrusted { context.startActivity(intent) }
    }.isSuccess
}
