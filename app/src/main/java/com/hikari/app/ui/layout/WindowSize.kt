package com.hikari.app.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much horizontal room a layout actually has, right now.
 *
 * This is separate from [DeviceClass] because the same device can be in more
 * than one window size: an unfolded foldable is EXPANDED, the same phone
 * app-paired in split screen is COMPACT. A screen that wants "two panes when
 * there is room" should ask this; a screen that wants "this is a tablet, use
 * the rail" should ask [DeviceClass].
 *
 * The breakpoints are the Material 3 window size classes, spelled out here so
 * the app does not take a dependency on `material3-adaptive` (the project's
 * dependency upgrades are deliberately deferred — see the upgrade plan).
 */
enum class WindowSize { COMPACT, MEDIUM, EXPANDED }

const val MEDIUM_MIN_WIDTH_DP = 600
const val EXPANDED_MIN_WIDTH_DP = 840

fun windowSizeFor(widthDp: Int): WindowSize = when {
    widthDp >= EXPANDED_MIN_WIDTH_DP -> WindowSize.EXPANDED
    widthDp >= MEDIUM_MIN_WIDTH_DP -> WindowSize.MEDIUM
    else -> WindowSize.COMPACT
}

/** [WindowSize] from the window's current width. */
@Composable
fun rememberWindowSize(): WindowSize =
    windowSizeFor(LocalConfiguration.current.screenWidthDp)

/**
 * [WindowSize] measured from the constraints this composable is actually given,
 * which is the correct answer inside a split-pane or a sheet. Prefer this over
 * [rememberWindowSize] anywhere the content may not span the whole window.
 */
@Composable
fun BoxWithConstraintsScope.windowSize(): WindowSize =
    windowSizeFor(maxWidth.value.toInt())

/** The width at and above which a two-pane list/detail split is allowed. */
val TwoPaneMinWidth: Dp = EXPANDED_MIN_WIDTH_DP.dp
