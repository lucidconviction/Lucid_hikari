package com.hikari.app.data

/**
 * A saved Xtream Codes panel account.
 *
 * Stored as JSON in AppStore under [AppStore.PREF_XTREAM_ACCOUNTS]. Each account
 * is probed on save to capture channel/movie/series counts so the UI can show
 * them without re-hitting the panel on every screen load.
 *
 * @property name      User-chosen display name (defaults to the panel host).
 * @property baseUrl   The panel root URL (e.g. "http://example.com:8080").
 * @property username  Xtream username.
 * @property password  Xtream password (plain — the panel API needs it).
 * @property channels  Number of live channels reported by the panel (0 = unknown).
 * @property movies    Number of VOD movies reported by the panel.
 * @property series    Number of series reported by the panel.
 * @property status    "Active" | "Expired" | "Disabled" | "Unknown".
 * @property expDate   Expiration epoch-ms reported by the panel (null = unknown).
 */
data class XtreamAccount(
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val channels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val status: String = "Unknown",
    val expDate: Long? = null,
) {
    /** Display label — falls back to the base URL host if no custom name was set. */
    val label: String get() = name.ifBlank { baseUrl }

    /** True when the panel reported an active status during the last probe. */
    val isWorking: Boolean get() = status == "Active"
}