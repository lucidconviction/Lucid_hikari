package com.hikari.app.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The YouTube page's own view model, kept where the page and the rest of the app can find it.
 * (The player panel is Hikari's own; the feed uses this for "what plays next" lookups.)
 */
object YouTubeSession {
    @Volatile var feed: YouTubeViewModel? = null

    /** Counts settings changes, so the page knows to reload its feed when it's next shown. */
    var settingsVersion by mutableIntStateOf(0)
    var appliedSettingsVersion = 0

    /** You've chosen a caption (or Off) for the video playing. */
    @Volatile var captionPicked = false

    /** The captions picker is open over the player. */
    var showCaptions by mutableStateOf(false)

    /** The live chat is showing over the player. */
    var chatOn by mutableStateOf(false)
}
