package com.hikari.app.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.hikari.app.tv.TvMode

/**
 * The three content layouts Hikari draws, decided once per composition.
 *
 * The app used to model this as a boolean (`TvMode.isTv`): television or not.
 * That is why tablets inherited the phone layout wholesale — an 11" tablet got
 * the same bottom taskbar and the same 20dp gutters as a 6" phone, and the
 * only tablet-aware screen in the app was MultiView.
 *
 * A device class is a different question from a window size. A phone rotated
 * into landscape is still a phone (bottom bar, thumb-reach); a tablet in a
 * split-screen window is still a tablet (rail, two-pane). [DeviceClass] answers
 * "what kind of device is this", [WindowSize] answers "how much room do we have
 * right now", and layouts consult both.
 *
 *  - [PHONE]  — one column, bottom taskbar, edge-to-edge art.
 *  - [TABLET] — a tall left rail, two-pane detail/settings, wider gutters.
 *  - [TV]     — the existing living-room layout, deliberately kept separate
 *               (its own focus model, rail and poster sizing — see TvUi).
 *
 * The television answer always wins over the width test: a television box can
 * report a 960dp width, which would otherwise make it a "tablet" and swap the
 * focus-aware rail for a touch rail.
 */
enum class DeviceClass(val key: String) {
    PHONE("phone"),
    TABLET("tablet"),
    TV("tv");

    /** True for the two touch layouts. */
    val isTouch: Boolean get() = this != TV

    /** True when the layout has a permanent left navigation rail. */
    val hasRail: Boolean get() = this != PHONE

    companion object {
        /** Android's own tablet breakpoint (`sw600dp`), used by every client
         *  that has ever drawn a two-pane layout. */
        const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

        fun from(isTv: Boolean, smallestWidthDp: Int): DeviceClass = when {
            isTv -> TV
            smallestWidthDp >= TABLET_MIN_SMALLEST_WIDTH_DP -> TABLET
            else -> PHONE
        }
    }
}

/**
 * [DeviceClass] for the current composition.
 *
 * Reads [TvMode.current], so flipping "This device is a TV" in Settings
 * re-composes every layout that asks this question — the same live behaviour
 * the old boolean had.
 */
@Composable
fun rememberDeviceClass(): DeviceClass {
    val smallestWidth = LocalConfiguration.current.smallestScreenWidthDp
    return DeviceClass.from(TvMode.current(), smallestWidth)
}
