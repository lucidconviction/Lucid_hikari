package com.hikari.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's motion vocabulary.
 *
 * Two durations and one curve cover almost everything: a state change is
 * [quick], a reveal or expansion is [standard], and a page-level move (a sheet,
 * a full-screen transition) is [spatial]. Anything longer reads as lag on a
 * phone; anything shorter reads as a jump.
 *
 * Accessibility: [animationsEnabled] reads Android's own animator duration
 * scale, so a user who has turned animations off system-wide gets instant
 * swaps without a second in-app setting. Callers that add a new animation
 * should check [rememberAnimationsEnabled] and skip the animation (not the
 * state change) when it is false.
 *
 * This file changes no existing motion. The navigation transitions in
 * AppNav.kt and the player's control fades keep their current timings; new
 * work uses these values so it matches.
 */
object HikariMotion {
    /** A state change: selection, colour, a badge appearing. */
    const val quick = 180

    /** A reveal or expansion: a shelf appearing, a panel growing. */
    const val standard = 260

    /** A spatial move: a sheet, a page-level transition. */
    const val spatial = 320

    /** Between two items in a staggered reveal. */
    const val stagger = 40

    /** Fast out, gentle settle. The default for reveals and state changes. */
    val easeOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Symmetric; for something that moves out and back. */
    val easeInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * False when the system animator scale is 0 (Developer options → Animator
     * duration scale → Off, or an accessibility/battery setting that does the
     * same). Never throws: a resolver that refuses the read is treated as
     * "animations on", which is the platform default.
     */
    fun animationsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0.01f
    }.getOrDefault(true)
}

/**
 * [HikariMotion.animationsEnabled] for composition. Remembered per context: the
 * value only changes when the user leaves the app to change it, and a new
 * composition after returning picks the new value up.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { HikariMotion.animationsEnabled(context) }
}
