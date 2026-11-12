package com.hikari.app.data

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

/**
 * What every preferences DataStore in the app recovers with when its file is
 * unreadable (truncated write, torn flush, a full disk).
 *
 * Without a handler DataStore throws `CorruptionException` on the first read,
 * and because many stores are read during startup that took the whole app down
 * rather than one settings screen. Replacing the damaged file with empty
 * preferences loses the settings it held — the only thing that can be done
 * with an unparseable file — and lets the app open so the user can set them
 * again.
 */
val ReplaceCorruptPreferences: ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { emptyPreferences() }
