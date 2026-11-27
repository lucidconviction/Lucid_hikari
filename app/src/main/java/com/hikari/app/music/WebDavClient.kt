package com.hikari.app.music

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** A minimal WebDAV browser for Music → Servers. */
object WebDavClient {

    data class Entry(
        val name: String,
        val url: String,
        val isDir: Boolean,
        val size: Long = 0L,
        val contentType: String = "",
    ) {
        val isAudio: Boolean
            get() = !isDir && (
                contentType.startsWith("audio/", true) ||
                    name.substringAfterLast('.', "").lowercase() in setOf(
                        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "ape",
                    )
                )
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val BODY = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getcontenttype/>
  </d:prop>
</d:propfind>"""

    private fun auth(user: String, pass: String): String? =
        if (user.isBlank() && pass.isBlank()) null else Credentials.basic(user, pass)

    suspend fun list(
        server: MusicServerStore.WebDav,
        directory: String = server.url,
    ): List<Entry> = withContext(Dispatchers.IO) {
        val base = directory.takeIf { it.isNotBlank() } ?: server.url
        require(base.startsWith("http")) { "WebDAV URL must start with http:// or https://" }
        val request = Request.Builder()
            .url(base)
            .method("PROPFIND", BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
            .apply { auth(server.user, server.pass)?.let { header("Authorization", it) } }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("WebDAV ${response.code} ${response.message}")
            }
            val body = response.body?.string().orEmpty()
            parse(body, base)
        }
    }

    fun streamHeaders(server: MusicServerStore.WebDav): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        auth(server.user, server.pass)?.let { headers["Authorization"] = it }
        return headers
    }

    private fun parse(xml: String, baseUrl: String): List<Entry> {
        val out = ArrayList<Entry>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var href = ""
        var name = ""
        var size = 0L
        var contentType = ""
        var isDir = false
        var inResponse = false
        var inResourceType = false
        var currentTag = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':').orEmpty()
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = tag
                    when (tag) {
                    "response" -> {
                        inResponse = true
                        href = ""
                        name = ""
                        size = 0L
                        contentType = ""
                        isDir = false
                    }
                    "resourcetype" -> inResourceType = true
                    "collection" -> if (inResourceType) isDir = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (!inResponse) {
                        event = parser.next()
                        continue
                    }
                    val text = parser.text?.trim().orEmpty()
                    if (text.isBlank()) {
                        event = parser.next()
                        continue
                    }
                    when (currentTag) {
                        "href" -> href = text
                        "displayname" -> name = text
                        "getcontentlength" -> size = text.toLongOrNull() ?: 0L
                        "getcontenttype" -> contentType = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                    "resourcetype" -> inResourceType = false
                    "response" -> {
                        inResponse = false
                        if (href.isNotBlank()) {
                            val resolved = resolve(baseUrl, href)
                            val self = trimSlashes(resolved) == trimSlashes(baseUrl)
                            if (!self) {
                                val shown = name.ifBlank {
                                    URLDecoder.decode(href.trimEnd('/').substringAfterLast('/'), "UTF-8")
                                }
                                out += Entry(shown, resolved, isDir, size, contentType)
                            }
                        }
                    }
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }
        return out.sortedWith(
            compareByDescending<Entry> { it.isDir }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
    }

    private fun resolve(baseUrl: String, href: String): String {
        val absolute = if (href.startsWith("http://") || href.startsWith("https://")) {
            href
        } else {
            runCatching { java.net.URI(baseUrl).resolve(href).toString() }.getOrDefault(href)
        }
        return if (href.endsWith("/") && !absolute.endsWith("/")) "$absolute/" else absolute
    }

    private fun trimSlashes(value: String): String = value.trimEnd('/')
}
