package com.hikari.app.iptv

import com.hikari.app.data.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * The Live TV port's "Generate Portals": the Cloud Vault Xtream database,
 * verified account by account, then saved as ordinary Hikari IPTV playlists.
 *
 * Ported from PlayTorrio's IptvScraper/IptvController (Cloud Vault + Reddit,
 * including base64 and paste links). The reference keeps a private verified-
 * portal list; Hikari shows those results on its own IPTV page through
 * [PortalStore], and a result behaves exactly like an Xtream portal the user
 * added by hand once its Add button is tapped.
 */
object PortalGenerator {

    private const val CLOUD_PRIMARY =
        "https://pub-38f23eb5f3304328b9774fadfa233a38.r2.dev/xtreamity-plus-db.csv.gz"
    private const val CLOUD_BACKUP =
        "https://s3.us-west-004.backblazeb2.com/Xtream-STBemu/xtreamity-plus-db.csv.gz"
    private const val UA = "VLC/3.0.20 LibVLC/3.0.20"

    data class Source(val url: String, val username: String, val password: String, val source: String)

    data class Generated(
        val baseUrl: String,
        val username: String,
        val password: String,
        val name: String,
        val expiry: String?,
        val maxConnections: String?,
        val m3uUrl: String,
        val source: String,
    )

    data class Progress(
        val status: String,
        val tried: Int = 0,
        val found: Int = 0,
        val target: Int = 5,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Finds up to [target] working panels. Cloud Vault lists thousands of
     * credentials; candidates are shuffled so repeated runs do not hit the
     * same dead hosts, then checked in small parallel batches.
     */
    suspend fun generate(
        target: Int = 5,
        maxTries: Int = 400,
        onProgress: (Progress) -> Unit = {},
    ): List<Generated> = withContext(Dispatchers.IO) {
        onProgress(Progress("Loading Cloud Vault portals…", target = target))
        val cloud = runCatching { loadCloudVault() }.getOrDefault(emptyList())
        onProgress(Progress("Cloud Vault: ${cloud.size} candidates · loading Reddit…", target = target))
        val reddit = runCatching { loadReddit() }.getOrDefault(emptyList())
        android.util.Log.d(
            "Hikari/Portal",
            "sources: Cloud Vault=${cloud.size}, Reddit=${reddit.size}",
        )
        val pool = (cloud + reddit)
            .distinctBy { it.url.lowercase() + "|" + it.username.lowercase() + "|" + it.password }
            .shuffled()
        if (pool.isEmpty()) {
            throw IllegalStateException("Couldn't load portals from Cloud Vault or Reddit.")
        }
        onProgress(
            Progress(
                "Cloud Vault: ${cloud.size} · Reddit: ${reddit.size} · verifying ${pool.size}…",
                target = target,
            ),
        )
        val candidates = pool.take(maxTries)
        val found = LinkedHashMap<String, Generated>()
        var tried = 0
        var batchStart = 0
        while (batchStart < candidates.size && found.size < target) {
            val batch = candidates.subList(batchStart, minOf(batchStart + 8, candidates.size))
            batchStart += batch.size
            val results = coroutineScope {
                batch.map { source -> async { verify(source) } }.awaitAll()
            }
            tried += batch.size
            results.filterNotNull().forEach { generated ->
                val key = generated.baseUrl.lowercase() + "|" +
                    generated.username.lowercase() + "|" + generated.password
                if (found.putIfAbsent(key, generated) == null) {
                    onProgress(
                        Progress(
                            "Found ${found.size} of $target · ${generated.name}",
                            tried,
                            found.size,
                            target,
                        ),
                    )
                }
            }
            if (found.size < target) {
                onProgress(
                    Progress("Verified ${found.size} of $target · tried $tried", tried, found.size, target),
                )
            }
            if (batch.isEmpty()) break
        }
        found.values.take(target)
    }

    /**
     * Saves [portals] as lightweight Portal search results.
     *
     * They are NOT installed as IPTV providers here: each provider downloads
     * and keeps its whole channel list, and a generation run adding five
     * panels at once was what exhausted the 512 MB heap. The IPTV page lists
     * the results and converts one into a provider only when Add is tapped.
     */
    suspend fun save(context: android.content.Context, portals: List<Generated>): Int =
        PortalStore.addAll(context, portals)

    private fun verify(source: Source): Generated? {
        val creds = runCatching { XtreamCredentials(source.url, source.username, source.password) }
            .getOrNull() ?: return null
        val request = Request.Builder()
            .url(creds.apiInfoUrl())
            .header("User-Agent", UA)
            .header("Accept", "application/json,*/*")
            .build()
        val text = runCatching {
            client.newCall(request).execute().use { reply ->
                if (!reply.isSuccessful) return@use null
                reply.body?.string()
            }
        }.getOrNull() ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val user = root.optJSONObject("user_info") ?: root
        val auth = user.optString("auth")
        val status = user.optString("status").lowercase()
        val accepted = auth == "1" || status == "active" || root.has("user_info")
        if (!accepted) return null
        // Auth alone is not enough: some panels answer player_api.php but
        // their get.php returns an error page. Peek the playlist before it is
        // offered, so "Generate portals" never adds a tile that cannot load.
        if (!playlistLooksValid(creds)) return null
        val name = user.optString("username").ifBlank { source.username }
            .ifBlank { source.url.substringAfter("://").substringBefore('/') }
        val expirySec = user.optString("exp_date").toLongOrNull()
        val expiry = when {
            expirySec == null || expirySec <= 0L -> "Unlimited"
            else -> {
                val d = java.util.Date(expirySec * 1000L)
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(d)
            }
        }
        return Generated(
            baseUrl = source.url,
            username = source.username,
            password = source.password,
            name = "Xtream · $name",
            expiry = expiry,
            maxConnections = user.optString("max_connections").takeIf { it.isNotBlank() },
            m3uUrl = creds.m3uUrl(),
            source = source.source,
        )
    }

    /** First few KiB of the panel's M3U: a real playlist starts with #EXTM3U/#EXTINF. */
    private fun playlistLooksValid(creds: XtreamCredentials): Boolean = runCatching {
        val request = Request.Builder()
            .url(creds.m3uUrl())
            .header("User-Agent", UA)
            .header("Range", "bytes=0-4095")
            .build()
        client.newCall(request).execute().use { reply ->
            if (!reply.isSuccessful) return@use false
            val source = reply.body?.source() ?: return@use false
            val buffer = okio.Buffer()
            source.read(buffer, 4096L)
            val text = buffer.readUtf8()
            text.contains("#EXTM3U", ignoreCase = true) ||
                text.contains("#EXTINF", ignoreCase = true)
        }
    }.getOrDefault(false)

    // ── Reddit source ──────────────────────────────────────────────────────

    private val redditClient = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val REDDIT_UA = "Hikari/1.0 (by /u/HikariApp)"
    private const val PASTE_UA =
        "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36"
    private val redditClientIds = listOf("ohXpoqrZYub1kg", "NOe2iKrPPzwscA", "JrPdG8Z6dkWNxA")
    private val redditSubs = listOf("IPTV_ZONENEW", "FreeIPTV", "iptvguru", "IPTVfree")
    private val pasteDomains = listOf(
        "paste.sh", "pastebin.com", "justpaste.it", "controlc.com",
        "pastes.dev", "text.is", "rentry.co",
    )
    private val pasteLink = Regex(
        """https?://(?:paste\.sh|pastebin\.com|justpaste\.it|controlc\.com|pastes\.dev|text\.is|rentry\.co)/[a-zA-Z0-9#_=-]+""",
        RegexOption.IGNORE_CASE,
    )
    private val base64Blob = Regex("""aHR0[a-zA-Z0-9+/=]{10,}""")
    private val junkMarkers = listOf(
        "Array.isArray", "prototype.", "function(", "var ", "const ",
        "let ", "return!", "void ", ".message}", "window.", "document.",
    )
    private val junkCredentials = listOf(
        "type=m3u", "output=ts", "password=", "username=", "password", "username",
    )

    @Volatile private var redditToken: String? = null
    @Volatile private var redditTokenAt = 0L

    private fun redditBearer(): String? {
        val cached = redditToken
        if (!cached.isNullOrBlank() && System.currentTimeMillis() - redditTokenAt < 50 * 60 * 1000L) {
            return cached
        }
        for (clientId in redditClientIds) {
            val basic = android.util.Base64.encodeToString(
                (clientId + ":").toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP,
            )
            val request = Request.Builder()
                .url("https://www.reddit.com/api/v1/access_token")
                .header("User-Agent", REDDIT_UA)
                .header("Authorization", "Basic $basic")
                .post(
                    ("grant_type=https://oauth.reddit.com/grants/installed_client" +
                        "&device_id=DO_NOT_TRACK_THIS_DEVICE")
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType()),
                )
                .build()
            val body = runCatching {
                redditClient.newCall(request).execute().use { reply ->
                    if (reply.isSuccessful) reply.body?.string() else null
                }
            }.getOrNull() ?: continue
            val token = runCatching { JSONObject(body).optString("access_token") }.getOrNull()
            if (!token.isNullOrBlank()) {
                redditToken = token
                redditTokenAt = System.currentTimeMillis()
                return token
            }
        }
        return null
    }

    /**
     * Reddit is not one source: posts carry credentials directly, as base64,
     * and as links to paste sites (sometimes an encrypted paste.sh). The
     * PlayTorrio scraper follows all three, which is why it finds panels our
     * first pass missed.
     */
    private fun loadReddit(): List<Source> {
        val out = ArrayList<Source>()
        val seen = HashSet<String>()
        val pastes = LinkedHashSet<String>()
        val bearer = redditBearer()
        for (sub in redditSubs) {
            var answered = false
            if (bearer != null) {
                val text = httpText(
                    "https://oauth.reddit.com/r/$sub/new?limit=100&raw_json=1",
                    ua = REDDIT_UA,
                    token = bearer,
                )
                if (text != null) {
                    answered = true
                    val children = runCatching {
                        JSONObject(text).optJSONObject("data")?.optJSONArray("children")
                    }.getOrNull()
                    if (children != null) {
                        for (i in 0 until children.length()) {
                            val post = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                            val body = buildString {
                                append(post.optString("title")).append('\n')
                                append(post.optString("selftext")).append('\n')
                                append(post.optString("url")).append('\n')
                                append(post.optString("url_overridden_by_dest"))
                            }
                            collectReddit(body, out, seen, pastes)
                        }
                    }
                }
            }
            if (!answered) {
                val rss = httpText(
                    "https://www.reddit.com/r/$sub/new/.rss?limit=60",
                    ua = REDDIT_UA,
                )
                if (rss != null) {
                    Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
                        .findAll(rss)
                        .forEach { collectReddit(it.groupValues[1], out, seen, pastes) }
                }
            }
        }
        // Only the first few paste links per run: they are third-party hosts
        // with their own timeouts, and the generator still has panels to verify.
        for (link in pastes.take(12)) {
            val text = fetchPaste(link) ?: continue
            collectReddit(text, out, seen, null)
        }
        return out
    }

    private fun collectReddit(
        raw: String,
        out: MutableList<Source>,
        seen: MutableSet<String>,
        pastes: MutableSet<String>?,
    ) {
        if (raw.isBlank()) return
        // Base64 hides both paste links and whole credential blocks.
        for (match in base64Blob.findAll(raw)) {
            val decoded = decodeBase64(match.value) ?: continue
            if (decoded.startsWith("http") && isPasteSite(decoded)) {
                pastes?.add(decoded)
            } else if (decoded.length >= 10) {
                addPortals(extractPortals(decodeEntities(decoded)), out, seen)
            }
        }
        if (pastes != null) {
            for (match in pasteLink.findAll(raw)) pastes.add(decodeEntities(match.value))
        }
        val text = normalizeUnicode(
            decodeEntities(raw)
                .replace(Regex("<(?:p|br|div|li|h\\d)[^>]*>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), " "),
        )
        if (isJunk(text)) return
        addPortals(extractPortals(text), out, seen)
    }

    private fun addPortals(found: List<Source>, out: MutableList<Source>, seen: MutableSet<String>) {
        for (source in found) {
            val key = source.url.lowercase() + "|" + source.username.lowercase() + "|" + source.password
            if (seen.add(key)) out += source
        }
    }

    // ── Paste sites ────────────────────────────────────────────────────────

    private fun isPasteSite(url: String): Boolean =
        pasteDomains.any { url.contains(it, ignoreCase = true) }

    private fun fetchPaste(url: String): String? {
        if (url.contains("paste.sh/", ignoreCase = true) && url.contains('#')) {
            return decryptPasteSh(url)
        }
        val target = when {
            url.contains("pastebin.com/", ignoreCase = true) && !url.contains("/raw/", ignoreCase = true) ->
                "https://pastebin.com/raw/" + lastSegment(url)
            url.contains("pastes.dev/", ignoreCase = true) ->
                "https://api.pastes.dev/" + lastSegment(url)
            url.contains("rentry.co/", ignoreCase = true) && !url.contains("/raw", ignoreCase = true) ->
                "https://rentry.co/" + lastSegment(url) + "/raw"
            url.contains("paste.sh/", ignoreCase = true) && !url.endsWith(".txt") -> "$url.txt"
            else -> url
        }
        return httpText(target, ua = PASTE_UA)
    }

    private fun lastSegment(url: String): String {
        var s = url
        val hash = s.indexOf('#')
        if (hash >= 0) s = s.substring(0, hash)
        val query = s.indexOf('?')
        if (query >= 0) s = s.substring(0, query)
        return s.substringAfterLast('/')
    }

    /**
     * paste.sh stores `Salted__` + salt + AES-256-CBC ciphertext, base64-encoded,
     * with the key derived from the URL id, the server key on the first line and
     * the client key in the fragment. Modern pastes use one PBKDF2-HMAC-SHA512
     * round; older ones use OpenSSL EVP_BytesToKey (MD5).
     */
    private fun decryptPasteSh(urlWithHash: String): String? {
        val hashAt = urlWithHash.indexOf('#')
        if (hashAt <= 0) return null
        val baseUrl = urlWithHash.substring(0, hashAt)
        val clientKey = urlWithHash.substring(hashAt + 1)
        val id = baseUrl.substringAfterLast('/')
        val raw = httpText("$baseUrl.txt", ua = PASTE_UA) ?: return null
        val lines = raw.split('\n')
        if (lines.size < 2) return null
        val serverKey = lines.first().trim()
        val b64 = lines.drop(1).joinToString("").trim()
        if (b64.isEmpty()) return null
        val bytes = runCatching {
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        }.getOrNull() ?: return null
        if (bytes.size < 17) return null
        val salt = bytes.copyOfRange(8, 16)
        val cipherText = bytes.copyOfRange(16, bytes.size)
        val password = (id + serverKey + clientKey + "https://paste.sh").toByteArray(Charsets.UTF_8)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            runCatching {
                val keyIv = pbkdf2Sha512(password, salt, 48)
                aesCbcDecrypt(cipherText, keyIv.copyOfRange(0, 32), keyIv.copyOfRange(32, 48))
            }.getOrNull()?.let { if (it.isNotBlank()) return it }
        }
        return runCatching {
            val (key, iv) = evpBytesToKey(password, salt, 32, 16)
            aesCbcDecrypt(cipherText, key, iv)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun pbkdf2Sha512(password: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val chars = CharArray(password.size) { (password[it].toInt() and 0xFF).toChar() }
        val spec = javax.crypto.spec.PBEKeySpec(chars, salt, 1, length * 8)
        return javax.crypto.SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec)
            .encoded
    }

    private fun evpBytesToKey(
        password: ByteArray,
        salt: ByteArray,
        keyLength: Int,
        ivLength: Int,
    ): Pair<ByteArray, ByteArray> {
        val out = java.io.ByteArrayOutputStream()
        var previous = ByteArray(0)
        val md5 = java.security.MessageDigest.getInstance("MD5")
        while (out.size() < keyLength + ivLength) {
            md5.reset()
            md5.update(previous)
            md5.update(password)
            md5.update(salt)
            previous = md5.digest()
            out.write(previous)
        }
        val all = out.toByteArray()
        return all.copyOfRange(0, keyLength) to all.copyOfRange(keyLength, keyLength + ivLength)
    }

    private fun aesCbcDecrypt(cipherText: ByteArray, key: ByteArray, iv: ByteArray): String? =
        runCatching {
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(key, "AES"),
                javax.crypto.spec.IvParameterSpec(iv),
            )
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()

    private fun decodeBase64(value: String): String? = runCatching {
        String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8)
    }.getOrNull()

    private fun httpText(url: String, ua: String, token: String? = null): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/json,application/atom+xml,text/plain,*/*")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return runCatching {
            redditClient.newCall(request).execute().use { reply ->
                if (reply.isSuccessful) reply.body?.string() else null
            }
        }.getOrNull()
    }

    // ── Credential extraction ──────────────────────────────────────────────

    private val urlUserFirst = Regex(
        """(https?://[^?\s"'<]+)\?(?:[^\s"'<]*?&)?(?:username|user|usr|login|account)=([^&\s"'<]+)\s*&(?:[^\s"'<]*?&)?(?:password|pass|pwd)=([^&\s"'<]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val urlPassFirst = Regex(
        """(https?://[^?\s"'<]+)\?(?:[^\s"'<]*?&)?(?:password|pass|pwd)=([^&\s"'<]+)\s*&(?:[^\s"'<]*?&)?(?:username|user|usr|login|account)=([^&\s"'<]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val labeledPortal = Regex(
        """(?:Portal(?:-URL)?|Host(?:\s*URL)?|Panel|Server|Real-URL|URL|M3U(?:-Link)?|Link|Servidor|H[ôo]te|Сервер|🌐|🌍|🔗|📡)\s*[:=➤\-•*|]?\s*(https?://[^\s<"'\n]+)[\s\S]{1,600}?(?:Username|User|Usr|Usu[áa]rio|Usuario|Utilisateur|Login|Account|Compte|Логин|👤|🗣️)\s*[:=➤\-•*|]?\s*([^\s|<"'\n]+)[\s\S]{1,300}?(?:Password|Pass|Pwd|Senha|Contrase[ñn]a|Clave|Mot\s*de\s*passe|Пароль|🔑|🔒|🔐)\s*[:=➤\-•*|]?\s*([^\s|<"'\n]+)(?:[\s\S]{1,200}?(?:Port|Porta|Puerto|Порт)\s*[:=➤\-•*|]?\s*(\d+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val streamPortal = Regex(
        """(https?://[^\s/]+(?::\d+)?)/(?:live|movie|series)/([^/\s]+)/([^/\s]+)/\d+""",
        RegexOption.IGNORE_CASE,
    )
    private val comboPortal = Regex(
        """(?:^|\n)\s*(https?://[a-zA-Z0-9.\-_]+(?::\d+)?(?:/[^\s:|,\t\n]*)?)\s*[:|,\t ]\s*([a-zA-Z0-9_\-.@]+)\s*[:|,\t ]\s*([a-zA-Z0-9_\-.@!#$%^&*+=]+)""",
        RegexOption.MULTILINE,
    )

    private fun extractPortals(text: String): List<Source> {
        if (text.length < 10 || isJunk(text)) return emptyList()
        val out = LinkedHashMap<String, Source>()

        fun put(rawUrl: String, rawUser: String, rawPass: String, rawPort: String? = null) {
            var url = cleanPortalUrl(rawUrl)
            if (!rawPort.isNullOrBlank() && !url.matches(Regex(""":\d+$"""))) {
                rawPort.toIntOrNull()?.takeIf { it in 1..65535 }?.let { url = "$url:$it" }
            }
            val user = cleanCred(rawUser)
            val pass = cleanCred(rawPass)
            if (url.length < 10 || user.length < 3 || pass.length < 3) return
            if (user.contains("http", ignoreCase = true) || pass.contains("http", ignoreCase = true)) return
            val lowerUser = user.lowercase()
            val lowerPass = pass.lowercase()
            if (lowerPass.endsWith(".php") || lowerPass.endsWith(".ts") ||
                lowerPass.endsWith(".m3u") || lowerPass.endsWith(".m3u8") ||
                lowerPass.endsWith(".mp4") || lowerUser.endsWith(".php") || lowerUser == "play"
            ) return
            if (junkCredentials.any { lowerUser.contains(it) || lowerPass.contains(it) }) return
            val key = url.lowercase() + "|" + user.lowercase() + "|" + pass
            out.putIfAbsent(key, Source(url, user, pass, "Reddit"))
        }

        urlUserFirst.findAll(text).forEach {
            put(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        urlPassFirst.findAll(text).forEach {
            put(it.groupValues[1], it.groupValues[3], it.groupValues[2])
        }
        labeledPortal.findAll(text).forEach {
            put(it.groupValues[1], it.groupValues[2], it.groupValues[3], it.groupValues[4])
        }
        streamPortal.findAll(text).forEach {
            put(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        comboPortal.findAll(text).forEach {
            put(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        return out.values.toList()
    }

    private fun isJunk(text: String): Boolean {
        var hits = 0
        for (marker in junkMarkers) {
            if (text.contains(marker)) hits++
            if (hits >= 2) return true
        }
        return false
    }

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#32;", " ")

    /** Posters hide credentials behind mathematical/fullwidth Unicode. */
    private fun normalizeUnicode(input: String): String {
        val out = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val ch = input.codePointAt(index)
            index += Character.charCount(ch)
            val mapped = when {
                (ch in 0x1D400..0x1D419) || (ch in 0x1D434..0x1D44D) ||
                    (ch in 0x1D468..0x1D481) || (ch in 0x1D5A0..0x1D5B9) ||
                    (ch in 0x1D5D4..0x1D5ED) || (ch in 0x1D608..0x1D621) ||
                    (ch in 0x1D670..0x1D689) -> ('A'.code + ((ch - 0x1D400) % 26)).toChar()
                (ch in 0x1D41A..0x1D433) || (ch in 0x1D44E..0x1D467) ||
                    (ch in 0x1D482..0x1D49B) || (ch in 0x1D5BA..0x1D5D3) ||
                    (ch in 0x1D5EE..0x1D607) || (ch in 0x1D622..0x1D63B) ||
                    (ch in 0x1D68A..0x1D6A3) -> ('a'.code + ((ch - 0x1D41A) % 26)).toChar()
                (ch in 0x1D7CE..0x1D7D7) || (ch in 0x1D7E2..0x1D7EB) ||
                    (ch in 0x1D7EC..0x1D7F5) || (ch in 0x1D7F6..0x1D7FF) ->
                    ('0'.code + ((ch - 0x1D7CE) % 10)).toChar()
                ch in 0xFF01..0xFF5E -> (ch - 0xFEE0).toChar()
                else -> null
            }
            if (mapped != null) {
                out.append(mapped)
            } else {
                val smallCaps = when (ch) {
                    0x1D00 -> 'A'; 0x0299 -> 'B'; 0x1D04 -> 'C'; 0x1D05 -> 'D'
                    0x1D07 -> 'E'; 0x0262 -> 'G'; 0x029C -> 'H'; 0x026A -> 'I'
                    0x1D0A -> 'J'; 0x1D0B -> 'K'; 0x029F -> 'L'; 0x1D0D -> 'M'
                    0x0274 -> 'N'; 0x1D0F -> 'O'; 0x1D18 -> 'P'; 0x0280 -> 'R'
                    0x1D1B -> 'T'; 0x1D1C -> 'U'; 0x1D20 -> 'V'; 0x1D21 -> 'W'
                    0x028F -> 'Y'; 0x1D22 -> 'Z'
                    else -> null
                }
                if (smallCaps != null) out.append(smallCaps) else out.appendCodePoint(ch)
            }
        }
        return out.toString()
    }

    private fun cleanPortalUrl(raw: String): String {
        var clean = raw.trim().replace(Regex("""\s+"""), "")
        val query = clean.indexOf('?')
        if (query >= 0) clean = clean.substring(0, query)
        clean = clean.trim()
        if (clean.contains("@")) {
            clean = "http://" + clean.substring(clean.lastIndexOf('@') + 1)
        }
        clean = clean.replace(
            Regex(
                """/(?:get|live|movie|series|portal|c|index|playlist|player_api|xmltv|index\.php|portal\.php)\.php$""",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        clean = clean.trimEnd('/')
        if (!clean.startsWith("http", ignoreCase = true)) clean = "http://$clean"
        return clean
    }

    private fun cleanCred(raw: String): String {
        var s = raw.trim().trimStart('=', ':', '➤', '•').trim()
        s = s.split(Regex("""[\s\n&?|<>]""")).firstOrNull().orEmpty()
        return s.replace(Regex("""[,;]+$"""), "")
    }

    @Volatile private var cloudCache: List<Source>? = null

    private fun loadCloudVault(): List<Source> {
        cloudCache?.takeIf { it.isNotEmpty() }?.let { return it }
        for (url in listOf(CLOUD_PRIMARY, CLOUD_BACKUP)) {
            val request = Request.Builder().url(url).header("User-Agent", UA).build()
            val list = runCatching {
                client.newCall(request).execute().use { reply ->
                    if (!reply.isSuccessful) return@use null
                    val body = reply.body ?: return@use null
                    val seen = HashSet<String>()
                    val out = ArrayList<Source>()
                    // Parse the gzip stream line by line: the database is tens
                    // of megabytes uncompressed, and keeping the compressed
                    // bytes, the inflated bytes and a String at once is an
                    // avoidable memory spike on a 512 MB device.
                    GZIPInputStream(body.byteStream()).bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (line.isBlank()) continue
                            val parts = parseCsvLine(line)
                            if (parts.size < 3) continue
                            val host = parts[0].trim().trimEnd('/')
                            val user = parts[1].trim()
                            val pass = parts[2].trim()
                            if (!host.startsWith("http://") && !host.startsWith("https://")) continue
                            if (user.isBlank() || pass.isBlank()) continue
                            val key = host.lowercase() + "|" + user.lowercase() + "|" + pass
                            if (seen.add(key)) out += Source(host, user, pass, "Cloud Vault")
                        }
                    }
                    out
                }
            }.getOrNull()
            if (!list.isNullOrEmpty()) {
                cloudCache = list
                return list
            }
        }
        return emptyList()
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    out += sb.toString().trim()
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        out += sb.toString().trim()
        return out
    }
}
