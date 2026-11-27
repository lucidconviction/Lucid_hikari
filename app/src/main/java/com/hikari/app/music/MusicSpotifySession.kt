package com.hikari.app.music

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The desktop Spotify session: the `sp_dc` cookie captured from a real sign-in. */
object MusicSpotifySession {
    private const val PREFS = "lucid_music_spotify"
    private const val KEY_COOKIE = "sp_dc"
    private const val KEY_NAME = "account_name"

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(SpotifySessionState())
    val state: StateFlow<SpotifySessionState> = _state.asStateFlow()

    data class SpotifySessionState(
        val connected: Boolean = false,
        val accountName: String? = null,
    )

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        val cookie = store.getString(KEY_COOKIE, null).orEmpty()
        _state.value = SpotifySessionState(
            connected = cookie.isNotBlank(),
            accountName = store.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() },
        )
    }

    fun cookie(context: Context): String {
        init(context)
        return prefs?.getString(KEY_COOKIE, null).orEmpty()
    }

    fun save(context: Context, cookie: String, accountName: String?) {
        init(context)
        val clean = cookie.trim()
        if (clean.isBlank()) return
        val name = accountName?.trim()?.takeIf { it.isNotBlank() }
        prefs?.edit()
            ?.putString(KEY_COOKIE, clean)
            ?.putString(KEY_NAME, name.orEmpty())
            ?.apply()
        _state.value = SpotifySessionState(connected = true, accountName = name)
    }

    fun clear(context: Context) {
        init(context)
        prefs?.edit()?.remove(KEY_COOKIE)?.remove(KEY_NAME)?.apply()
        _state.value = SpotifySessionState()
    }
}
