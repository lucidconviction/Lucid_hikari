package com.hikari.app.music

import android.content.Context
import android.content.SharedPreferences

/** Credentials for the Music tab's WebDAV and Navidrome servers. */
object MusicServerStore {
    private const val PREFS = "lucid_music_servers"
    private const val KEY_WEBDAV_URL = "webdav_url"
    private const val KEY_WEBDAV_USER = "webdav_user"
    private const val KEY_WEBDAV_PASS = "webdav_pass"
    private const val KEY_NAV_URL = "nav_url"
    private const val KEY_NAV_USER = "nav_user"
    private const val KEY_NAV_PASS = "nav_pass"

    @Volatile private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    data class WebDav(val url: String = "", val user: String = "", val pass: String = "")
    data class Navidrome(val url: String = "", val user: String = "", val pass: String = "")

    fun webDav(): WebDav {
        val p = prefs ?: return WebDav()
        return WebDav(
            p.getString(KEY_WEBDAV_URL, "").orEmpty(),
            p.getString(KEY_WEBDAV_USER, "").orEmpty(),
            p.getString(KEY_WEBDAV_PASS, "").orEmpty(),
        )
    }

    fun saveWebDav(value: WebDav) {
        prefs?.edit()
            ?.putString(KEY_WEBDAV_URL, value.url)
            ?.putString(KEY_WEBDAV_USER, value.user)
            ?.putString(KEY_WEBDAV_PASS, value.pass)
            ?.apply()
    }

    fun navidrome(): Navidrome {
        val p = prefs ?: return Navidrome()
        return Navidrome(
            p.getString(KEY_NAV_URL, "").orEmpty(),
            p.getString(KEY_NAV_USER, "").orEmpty(),
            p.getString(KEY_NAV_PASS, "").orEmpty(),
        )
    }

    fun saveNavidrome(value: Navidrome) {
        prefs?.edit()
            ?.putString(KEY_NAV_URL, value.url)
            ?.putString(KEY_NAV_USER, value.user)
            ?.putString(KEY_NAV_PASS, value.pass)
            ?.apply()
    }
}
