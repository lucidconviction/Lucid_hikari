package com.hikari.app.web

import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Brings a userscript in from a URL instead of making the user paste its source.
 *
 * Two shapes are accepted, because those are the two a user actually has:
 *
 *  - a direct script (`https://…/foo.user.js`, a raw gist, a repo blob's raw
 *    URL) — fetched and returned as-is;
 *  - a Greasy Fork / OpenUserJS script PAGE. Those pages do not serve the
 *    script; they link to it ("Install this script"). The link is found and
 *    followed, and the result must still look like a userscript, so a wrong
 *    page fails with a sentence instead of saving HTML as code.
 *
 * The caller puts the result in the editor rather than saving it silently: an
 * import is a convenience, not a reason to trust something unseen.
 */
object UserscriptImport {

    private const val MAX_PAGE_BYTES = 1_500_000
    private const val MAX_SCRIPT_BYTES = 2_500_000

    suspend fun fetch(rawUrl: String): String = withContext(Dispatchers.IO) {
        var target = rawUrl.trim()
        if (target.isEmpty()) throw IllegalArgumentException("Enter a script URL")
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://$target"
        }
        val body = Http.fetchStringCapped(target, maxBytes = MAX_PAGE_BYTES)
            .getOrElse { throw IllegalStateException(it.message ?: "Could not download the script") }
        if (looksLikeUserscript(body)) return@withContext body

        val href = firstScriptHref(body)
            ?: throw IllegalStateException(
                "That page does not link to a .user.js script — open the script's own page and try again.",
            )
        val absolute = runCatching {
            URI(target).resolve(href.replace("&amp;", "&")).toString()
        }.getOrElse { href }
        val script = Http.fetchStringCapped(absolute, maxBytes = MAX_SCRIPT_BYTES)
            .getOrElse { throw IllegalStateException(it.message ?: "Could not download the script") }
        if (!looksLikeUserscript(script)) {
            throw IllegalStateException("The link at that address is not a userscript.")
        }
        script
    }

    private fun looksLikeUserscript(text: String): Boolean =
        text.contains("==UserScript==") ||
            (text.contains("@name") && text.contains("@match", ignoreCase = true)) ||
            (text.contains("@name") && text.contains("@include", ignoreCase = true))

    /** The "Install this script" link on a Greasy Fork / OpenUserJS page. */
    private fun firstScriptHref(html: String): String? {
        // Greasy Fork's own install button first, then any .user.js href.
        val patterns = listOf(
            Regex("""href="([^"]*/code/[^"]*\.user\.js[^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""href="([^"]+\.user\.js[^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""(https?://[^\s"'<>]+\.user\.js)""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val m = p.find(html) ?: continue
            return m.groupValues[1]
        }
        return null
    }
}
