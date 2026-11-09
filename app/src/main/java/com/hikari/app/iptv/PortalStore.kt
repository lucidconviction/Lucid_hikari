package com.hikari.app.iptv

import android.content.Context
import com.hikari.app.HikariApp
import com.hikari.app.data.ProviderConfig
import com.hikari.app.data.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portals found by the generator, kept OUTSIDE the IPTV provider list until the
 * user chooses one.
 *
 * Adding five full Xtream exports as IPTV providers at once was what made
 * Hikari run out of heap on a 512 MB device: every provider parses and keeps
 * its whole channel list. The search results therefore live here as light
 * account records, are shown on the IPTV page, and each one becomes a normal
 * playlist only when "Add" is tapped.
 */
object PortalStore {

    private const val PREFS = "portal_search"
    private const val KEY = "portals"

    /** Written into [ProviderConfig.extra] when the user taps Add. It keeps the
     *  migration below from treating a portal the user re-added as a legacy
     *  auto-added one on the next launch. */
    const val MARKER = "portal-store"

    data class Saved(
        val baseUrl: String,
        val username: String,
        val password: String,
        val name: String,
        val expiry: String?,
        val maxConnections: String?,
        val source: String = "Portal search",
        val added: Boolean = false,
    ) {
        val key: String get() = baseUrl.lowercase() + "|" + username.lowercase()
        val m3uUrl: String
            get() = com.hikari.app.data.XtreamCredentials(baseUrl, username, password).m3uUrl()
        val host: String get() = baseUrl.substringAfter("://").substringBefore('/')
    }

    private val _portals = MutableStateFlow<List<Saved>>(emptyList())
    val portals: StateFlow<List<Saved>> = _portals.asStateFlow()

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (_portals.value.isNotEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _portals.value = parse(prefs.getString(KEY, null))
    }

    fun addAll(context: Context, found: List<PortalGenerator.Generated>): Int {
        init(context)
        if (found.isEmpty()) return 0
        val current = _portals.value.toMutableList()
        val known = current.map { it.key }.toHashSet()
        var added = 0
        for (p in found) {
            val saved = Saved(
                baseUrl = p.baseUrl,
                username = p.username,
                password = p.password,
                name = p.name,
                expiry = p.expiry,
                maxConnections = p.maxConnections,
                source = p.source,
            )
            if (known.add(saved.key)) {
                current.add(saved)
                added++
            }
        }
        _portals.value = current
        persist(context, current)
        return added
    }

    fun remove(context: Context, portal: Saved) {
        init(context)
        _portals.value = _portals.value.filterNot { it.key == portal.key }
        persist(context, _portals.value)
    }

    /**
     * Moves the Xtream providers that older builds added automatically into
     * this lightweight store.
     *
     * The first versions of "Generate portals" saved every found panel as a
     * full IPTV provider. Each provider downloads and keeps its whole channel
     * list, so ten panels were enough to exhaust a 512 MB heap during search or
     * while drawing the IPTV tab. Those rows are recognisable by the generator's
     * own "Xtream · " name prefix (manual Xtream adds use the server name); they
     * are removed from the installed list and kept here instead, where the user
     * can add back only the ones they actually want.
     */
    suspend fun migrateLegacyProviders(app: HikariApp): Int {
        init(app)
        val all = app.store.providers()
        val legacy = all.filter { config ->
            config.type == ProviderType.IPTV &&
                config.name.startsWith("Xtream · ") &&
                config.extra != MARKER
        }
        if (legacy.isEmpty()) return 0
        val existing = _portals.value.toMutableList()
        val known = existing.map { it.key }.toHashSet()
        var moved = 0
        for (config in legacy) {
            val creds = com.hikari.app.data.XtreamCredentials.fromUrl(config.url) ?: continue
            val saved = Saved(
                baseUrl = creds.baseUrl,
                username = creds.username,
                password = creds.password,
                name = config.name,
                expiry = null,
                maxConnections = null,
                source = "Earlier portal search",
                // They WERE in the playlist list, but they are no longer providers.
                // Marking them un-added makes the row offer the Add button again.
                added = false,
            )
            if (known.add(saved.key)) {
                existing += saved
                moved++
            }
        }
        if (moved == 0) return 0
        _portals.value = existing
        persist(app, existing)
        app.store.saveProviders(all - legacy.toSet())
        app.providers.refresh()
        return moved
    }

    /** Converts one generated portal into an ordinary IPTV provider (lazy M3U load). */
    suspend fun addAsProvider(context: Context, portal: Saved): Boolean {
        val app = context.applicationContext as? HikariApp ?: return false
        val m3u = portal.m3uUrl
        val config = ProviderConfig(
            id = "iptv|" + m3u.hashCode(),
            name = portal.name.ifBlank { "Xtream portal" },
            type = ProviderType.IPTV,
            url = m3u,
            extra = MARKER,
        )
        app.store.addProvider(config)
        app.providers.refresh()
        // Confirm the write landed before claiming success: a coalesced refresh
        // once made this row look added while the provider list stayed empty.
        val saved = runCatching { app.store.providers().any { it.id == config.id } }
            .getOrDefault(false)
        if (!saved) return false
        init(context)
        _portals.value = _portals.value.map { if (it.key == portal.key) it.copy(added = true) else it }
        persist(context, _portals.value)
        return true
    }

    private fun persist(context: Context, list: List<Saved>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("baseUrl", p.baseUrl)
                    .put("username", p.username)
                    .put("password", p.password)
                    .put("name", p.name)
                    .put("expiry", p.expiry.orEmpty())
                    .put("maxConnections", p.maxConnections.orEmpty())
                    .put("source", p.source)
                    .put("added", p.added),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    private fun parse(raw: String?): List<Saved> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Saved>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val base = o.optString("baseUrl")
            val user = o.optString("username")
            val pass = o.optString("password")
            if (base.isBlank() || user.isBlank() || pass.isBlank()) continue
            out += Saved(
                baseUrl = base,
                username = user,
                password = pass,
                name = o.optString("name").ifBlank { "Xtream portal" },
                expiry = o.optString("expiry").takeIf { it.isNotBlank() },
                maxConnections = o.optString("maxConnections").takeIf { it.isNotBlank() },
                source = o.optString("source").ifBlank { "Portal search" },
                added = o.optBoolean("added", false),
            )
        }
        return out
    }
}
