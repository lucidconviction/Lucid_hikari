package com.hikari.app.music

import com.hikari.app.data.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Party mode: a tiny LAN web page where guests can see what is playing and
 * add songs to the host's queue. No account, no cloud service — the phone is
 * the server.
 */
object MusicPartyServer {

    private const val START_PORT = 8765
    private const val MAX_PORT = 8785

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var server: ServerSocket? = null
    private var thread: Thread? = null
    private val pool = Executors.newCachedThreadPool()
    private var boundPort = 0

    @Synchronized
    fun start(): String? {
        if (_running.value) return url()
        val socket = runCatching {
            var candidate: ServerSocket? = null
            for (port in START_PORT..MAX_PORT) {
                val s = runCatching { ServerSocket(port) }.getOrNull()
                if (s != null) {
                    candidate = s
                    boundPort = port
                    break
                }
            }
            candidate ?: error("No free party port")
        }.onFailure {
            Logs.log("MusicParty", "could not open a port: ${it.message}")
        }.getOrNull() ?: return null
        server = socket
        _running.value = true
        thread = Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                pool.execute { handle(client) }
            }
        }, "LucidMusicParty").apply {
            isDaemon = true
            start()
        }
        return url()
    }

    @Synchronized
    fun stop() {
        _running.value = false
        runCatching { server?.close() }
        server = null
        thread = null
    }

    fun url(): String? {
        if (!_running.value) return null
        val ip = localIp() ?: return null
        return "http://$ip:$boundPort/"
    }

    private fun handle(client: Socket) {
        runCatching {
            client.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val request = reader.readLine().orEmpty()
                if (request.isBlank()) return@use
                // Drain headers.
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val target = request.split(' ').getOrNull(1).orEmpty()
                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))
                val (status, contentType, body) = when {
                    path == "/state.json" -> Triple(200, "application/json", stateJson())
                    path == "/search" -> Triple(
                        200,
                        "text/html; charset=utf-8",
                        page(search = query["q"].orEmpty()),
                    )
                    path == "/queue" -> {
                        queueFromGuest(query)
                        Triple(303, "text/plain", "")
                    }
                    else -> Triple(200, "text/html; charset=utf-8", page(search = null))
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                writer.write("HTTP/1.1 $status ${if (status == 303) "See Other" else "OK"}\r\n")
                writer.write("Content-Type: $contentType\r\n")
                if (status == 303) writer.write("Location: /\r\n")
                writer.write("Content-Length: ${bytes.size}\r\n")
                writer.write("Connection: close\r\n\r\n")
                writer.flush()
                socket.getOutputStream().write(bytes)
                socket.getOutputStream().flush()
            }
        }.onFailure {
            Logs.log("MusicParty", "request failed: ${it.message}")
        }
    }

    private fun queueFromGuest(query: Map<String, String>) {
        val title = query["title"].orEmpty().trim()
        if (title.isBlank()) return
        val artist = query["artist"].orEmpty().trim().ifBlank { "Party request" }
        val id = query["id"].takeUnless { it.isNullOrBlank() }
            ?: "party:${System.currentTimeMillis()}"
        val cover = query["cover"].orEmpty()
        MusicPlayer.enqueue(
            MusicTrack(
                id = id,
                title = title,
                artist = artist,
                coverUrl = cover,
                album = "Party queue",
            ),
        )
    }

    private fun stateJson(): String {
        val state = MusicPlayer.state.value
        val track = state.track
        return buildString {
            append("{")
            append("\"playing\":").append(state.playing).append(',')
            append("\"title\":\"").append(jsonEscape(track?.title.orEmpty())).append("\",")
            append("\"artist\":\"").append(jsonEscape(track?.artist.orEmpty())).append("\",")
            append("\"queue\":[")
            state.queue.drop(state.index + 1).take(20).forEachIndexed { index, song ->
                if (index > 0) append(',')
                append("{\"title\":\"").append(jsonEscape(song.title))
                    .append("\",\"artist\":\"").append(jsonEscape(song.artist)).append("\"}")
            }
            append("]}")
        }
    }

    private fun page(search: String?): String {
        val state = MusicPlayer.state.value
        val track = state.track
        val results = if (search != null) {
            runBlocking { runCatching { DeezerApi.searchTracks(search, 12) }.getOrDefault(emptyList()) }
        } else emptyList()
        return buildString {
            append(
                """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lucid Music Party</title>
<style>
:root{color-scheme:dark}
body{margin:0;background:#08070b;color:#f8f6ff;font-family:system-ui,sans-serif}
main{max-width:720px;margin:0 auto;padding:24px 18px 60px}
h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:26px 0 10px;color:#a78bfa}
.now{background:#14111c;border:1px solid #2a2436;border-radius:18px;padding:16px;margin-top:16px}
.title{font-size:20px;font-weight:700}.sub{color:#a9a4b8;font-size:13px}
form.search{display:flex;gap:8px;margin-top:18px}
input[type=search]{flex:1;background:#14111c;border:1px solid #2a2436;color:#fff;border-radius:12px;padding:12px}
button{background:#a78bfa;color:#100f14;border:0;border-radius:12px;padding:12px 16px;font-weight:700}
.row{display:flex;align-items:center;gap:12px;background:#14111c;border:1px solid #2a2436;border-radius:14px;padding:10px 12px;margin-top:8px}
.row .meta{flex:1;min-width:0}.row .meta div{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.small{font-size:12px;color:#a9a4b8}.foot{margin-top:32px;font-size:11px;color:#6d677e}
</style></head><body><main>
<h1>Lucid Music Party</h1><div class="sub">Add songs from this page — the host's phone plays them.</div>
<div class="now"><div class="small">NOW PLAYING</div>
<div class="title">""",
            )
            append(escape(track?.title ?: "Nothing playing"))
            append("</div><div class=\"sub\">")
            append(escape(track?.artist ?: "Search below to add a song"))
            append("</div><div class=\"small\">")
            append(if (state.playing) "Playing" else if (track != null) "Paused" else "")
            append("</div></div>")
            if (search != null) {
                append("<h2>Results for “").append(escape(search)).append("”</h2>")
                if (results.isEmpty()) {
                    append("<div class=\"sub\">No songs found.</div>")
                } else {
                    results.forEach { song ->
                        append("<div class=\"row\"><div class=\"meta\"><div class=\"title\" style=\"font-size:14px\">")
                        append(escape(song.title))
                        append("</div><div class=\"small\">")
                        append(escape(song.artist))
                        append("</div></div><form method=\"get\" action=\"/queue\">")
                        append("<input type=\"hidden\" name=\"id\" value=\"").append(escape(song.id)).append("\">")
                        append("<input type=\"hidden\" name=\"title\" value=\"").append(escape(song.title)).append("\">")
                        append("<input type=\"hidden\" name=\"artist\" value=\"").append(escape(song.artist)).append("\">")
                        append("<input type=\"hidden\" name=\"cover\" value=\"").append(escape(song.coverUrl)).append("\">")
                        append("<button>Add</button></form></div>")
                    }
                }
            }
            append("<form class=\"search\" method=\"get\" action=\"/search\">")
            append("<input type=\"search\" name=\"q\" placeholder=\"Search songs…\" value=\"")
            append(escape(search.orEmpty()))
            append("\"><button>Search</button></form>")
            val upNext = state.queue.drop(state.index + 1).take(20)
            if (upNext.isNotEmpty()) {
                append("<h2>Up next</h2>")
                upNext.forEach { song ->
                    append("<div class=\"row\"><div class=\"meta\"><div>")
                    append(escape(song.title))
                    append("</div><div class=\"small\">")
                    append(escape(song.artist))
                    append("</div></div></div>")
                }
            }
            append("<div class=\"foot\">Lucid Music Party · same Wi-Fi only</div>")
            append("</main></body></html>")
        }
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            key to value
        }.toMap()

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    }.getOrNull()
}
