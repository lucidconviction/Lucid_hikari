package com.hikari.app.player

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A loopback HTTP proxy for streams handed from the browser to ExoPlayer.
 *
 * Why it exists: a WebView can play a stream that ExoPlayer cannot open,
 * because the page's requests carry a Referer, the session cookies, the page's
 * User-Agent and often an Origin — and a player that sends only some of them
 * gets a 403 from the CDN. Passing those headers on the media item is not
 * enough for HLS: the player resolves the child playlist and every segment
 * itself, and those requests are what the CDN actually checks. The proxy
 * fetches every hop with the same credentials, so the whole ladder works.
 *
 * It also rewrites HLS manifests so child playlists, keys and segments all come
 * back through the proxy, and it forwards Range requests untouched, which is
 * what makes seeking work.
 *
 * Deliberately NOT a general proxy: it binds to loopback, serves only URLs the
 * app itself registered, and has no listening interface on the network.
 */
object StreamProxy {

    private data class Entry(val url: String, val headers: Map<String, String>)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** `url + headers` → token, so a manifest refresh does not mint a new token
     *  for every segment it repeats. */
    private val tokensByKey = ConcurrentHashMap<String, String>()

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var port: Int = 0

    @Volatile
    private var server: ServerSocket? = null

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "hikari-stream-proxy").apply { isDaemon = true }
    }

    /**
     * Registers [url] with the headers the page used and returns the local URL
     * to hand to the player. Returns the original URL when the proxy cannot
     * start, so a hand-off never fails because of the helper.
     */
    fun register(url: String, headers: Map<String, String>): String {
        val p = ensureStarted() ?: return url
        val key = url + "\n" + headers.toSortedMap().entries.joinToString("\n") { it.key + ":" + it.value }
        val token = tokensByKey[key] ?: run {
            val t = java.util.UUID.randomUUID().toString().replace("-", "")
            // A long live session mints a token per segment. The bound keeps a
            // day-long stream from growing the map without limit; clearing
            // only costs a re-register on the next manifest fetch.
            if (entries.size > 4000) {
                entries.clear()
                tokensByKey.clear()
            }
            entries[t] = Entry(url, headers)
            tokensByKey[key] = t
            t
        }
        return "http://127.0.0.1:$p/s/$token"
    }

    @Synchronized
    private fun ensureStarted(): Int? {
        if (port != 0) return port
        return runCatching {
            val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            server = socket
            port = socket.localPort
            executor.execute {
                while (!socket.isClosed) {
                    val conn = runCatching { socket.accept() }.getOrNull() ?: break
                    executor.execute { runCatching { serve(conn) } }
                }
            }
            port
        }.getOrNull()
    }

    private fun serve(conn: Socket) {
        conn.use { socket ->
            socket.soTimeout = 20_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val path = parts[1]

            val requestHeaders = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) requestHeaders[line.substring(0, i).trim().lowercase()] =
                    line.substring(i + 1).trim()
            }

            val token = path.removePrefix("/s/").substringBefore('?').substringBefore('/')
            val entry = entries[token] ?: run {
                writeEmpty(output, 404)
                return
            }

            val builder = Request.Builder().url(entry.url)
            entry.headers.forEach { (k, v) -> runCatching { builder.header(k, v) } }
            requestHeaders["range"]?.let { builder.header("Range", it) }
            if (method == "HEAD") builder.head()

            val response = runCatching { client.newCall(builder.build()).execute() }.getOrNull()
            if (response == null) {
                writeEmpty(output, 502)
                return
            }
            response.use { r ->
                val contentType = r.header("Content-Type").orEmpty()
                val looksHls = entry.url.contains(".m3u8", true) ||
                    contentType.contains("mpegurl", true)

                if (looksHls && method != "HEAD") {
                    val body = runCatching { r.body?.bytes() }.getOrNull()
                    if (body == null) {
                        writeEmpty(output, 502)
                        return
                    }
                    val text = String(body, Charsets.UTF_8)
                    val rewritten = if (text.startsWith("#EXTM3U")) {
                        rewriteHls(text, entry.url, entry.headers)
                    } else {
                        text
                    }
                    val bytes = rewritten.toByteArray(Charsets.UTF_8)
                    val head = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/vnd.apple.mpegurl\r\n")
                        append("Content-Length: ").append(bytes.size).append("\r\n")
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    output.write(head.toByteArray(Charsets.US_ASCII))
                    output.write(bytes)
                    output.flush()
                    return
                }

                val code = r.code
                val head = buildString {
                    append("HTTP/1.1 ").append(code).append(' ').append(r.message.ifBlank { "OK" })
                        .append("\r\n")
                    r.header("Content-Type")?.let { append("Content-Type: ").append(it).append("\r\n") }
                    r.header("Content-Length")?.let { append("Content-Length: ").append(it).append("\r\n") }
                    r.header("Content-Range")?.let { append("Content-Range: ").append(it).append("\r\n") }
                    r.header("Accept-Ranges")?.let { append("Accept-Ranges: ").append(it).append("\r\n") }
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(head.toByteArray(Charsets.US_ASCII))
                if (method != "HEAD") {
                    r.body?.byteStream()?.use { copy(it, output) }
                }
                output.flush()
            }
        }
    }

    /**
     * Rewrites every URI in an HLS manifest through the proxy: child playlists
     * (`#EXT-X-STREAM-INF` and plain lines), encryption keys, init segments and
     * alternate renditions. A relative URI resolves against the manifest it
     * came from, exactly as the player would have done.
     */
    private fun rewriteHls(
        text: String,
        baseUrl: String,
        headers: Map<String, String>,
    ): String {
        val out = StringBuilder(text.length + 256)
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> out.append('\n')
                trimmed.startsWith("#") -> {
                    out.append(rewriteTagUris(line, baseUrl, headers)).append('\n')
                }
                else -> {
                    out.append(register(resolve(baseUrl, trimmed), headers)).append('\n')
                }
            }
        }
        return out.toString()
    }

    private val uriAttr = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)

    private fun rewriteTagUris(
        line: String,
        baseUrl: String,
        headers: Map<String, String>,
    ): String = uriAttr.replace(line) { m ->
        val original = m.groupValues[1]
        "URI=\"" + register(resolve(baseUrl, original), headers) + "\""
    }

    private fun resolve(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun writeEmpty(out: BufferedOutputStream, code: Int) {
        val reason = if (code == 404) "Not Found" else "Bad Gateway"
        out.write(
            ("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                .toByteArray(Charsets.US_ASCII),
        )
        out.flush()
    }

    private fun copy(input: InputStream, output: BufferedOutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            output.write(buffer, 0, n)
        }
    }
}
