package com.hikari.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Persists a list of saved Xtream Codes panel accounts in AppStore
 * under [AppStore.K.IPTV_XTREAM_ACCOUNTS].
 *
 * Each account is probed on save to capture channel/movie/series counts so
 * the UI can display them without re-hitting the panel on every screen load.
 */
object XtreamAccountStore {
    private val _accounts = MutableStateFlow<List<XtreamAccount>>(emptyList())
    val accounts: StateFlow<List<XtreamAccount>> = _accounts.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun loadFromStore(raw: String?) {
        val list = if (raw.isNullOrBlank()) emptyList() else runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                XtreamAccount(
                    name = o.optString("name", ""),
                    baseUrl = o.optString("baseUrl", ""),
                    username = o.optString("username", ""),
                    password = o.optString("password", ""),
                    channels = o.optInt("channels", 0),
                    movies = o.optInt("movies", 0),
                    series = o.optInt("series", 0),
                    status = o.optString("status", "Unknown"),
                    expDate = if (o.has("expDate") && o.optLong("expDate", 0L) > 0) o.optLong("expDate") else null,
                ).takeIf { it.name.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
        _accounts.value = list
    }

    fun toJson(accounts: List<XtreamAccount>): String {
        val arr = org.json.JSONArray()
        for (acc in accounts) {
            val o = org.json.JSONObject()
            o.put("name", acc.name)
            o.put("baseUrl", acc.baseUrl)
            o.put("username", acc.username)
            o.put("password", acc.password)
            o.put("channels", acc.channels)
            o.put("movies", acc.movies)
            o.put("series", acc.series)
            o.put("status", acc.status)
            if (acc.expDate != null) o.put("expDate", acc.expDate)
            arr.put(o)
        }
        return arr.toString()
    }

    suspend fun addAccount(creds: XtreamCredentials, store: AppStore): XtreamAccount =
        withContext(Dispatchers.IO) {
            val api = creds.apiUrl()
            val resp = try {
                client.newCall(Request.Builder().url(api).build()).execute()
            } catch (e: Exception) {
                throw e
            }
            if (resp.code != 200) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: ""
            val doc = JSONObject(body)
            val username = doc.optString("username", creds.username)
            val expDate = doc.optLong("exp_date", 0L)
            val isActive = doc.optInt("is_active", 0) == 1
            val maxConns = doc.optInt("max_connections", 0)
            val status = if (isActive) "Active" else "Expired"
            val channelCount = doc.optInt("num_streams", 0)

            var movieCount = 0
            var seriesCount = 0
            try {
                val catsUrl = "$api/get_vod_categories?username=${creds.username}&password=${creds.password}"
                val catResp = client.newCall(Request.Builder().url(catsUrl).build()).execute()
                if (catResp.code == 200) {
                    val catJson = org.json.JSONArray(catResp.body?.string() ?: "")
                    movieCount = catJson.length()
                }
            } catch (_: Exception) {}
            try {
                val seriesUrl = "$api/get_series?username=${creds.username}&password=${creds.password}"
                val seriesResp = client.newCall(Request.Builder().url(seriesUrl).build()).execute()
                if (seriesResp.code == 200) {
                    seriesCount = seriesResp.body?.string()?.lines()?.count { it.isNotBlank() } ?: 0
                }
            } catch (_: Exception) {}

            val displayName = doc.optString("server_product", "")
                .ifBlank { creds.root.split("/").getOrNull(2)?.split(":")?.firstOrNull() ?: "Xtream Panel" }

            val account = XtreamAccount(
                name = displayName,
                baseUrl = creds.root,
                username = creds.username,
                password = creds.password,
                channels = channelCount,
                movies = movieCount,
                series = seriesCount,
                status = status,
                expDate = if (expDate > 0) expDate * 1000L else null,
            )
            val current = _accounts.value.toMutableList()
            val key = "${account.baseUrl}|${account.username}"
            val idx = current.indexOfFirst { "${it.baseUrl}|${it.username}" == key }
            if (idx >= 0) current[idx] = account else current.add(account)
            _accounts.value = current
            account
        }

    fun removeAccount(baseUrl: String, username: String) {
        _accounts.value = _accounts.value.filterNot { "${it.baseUrl}|${it.username}" == "$baseUrl|$username" }
    }

    fun clear() {
        _accounts.value = emptyList()
    }
}
