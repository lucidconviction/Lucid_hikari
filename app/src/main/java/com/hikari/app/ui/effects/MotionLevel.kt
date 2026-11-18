package com.hikari.app.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.hikari.app.ui.theme.rememberAnimationsEnabled

/**
 * How much motion the app is allowed to draw.
 *
 * Hikari runs on phones, tablets, Fire TV Sticks and no-name Android boxes.
 * The same press animation that feels premium on a flagship is a dropped frame
 * on a 1GB box, and some users simply do not want movement. One preference,
 * three levels, respected by every effect in `ui/effects`:
 *
 *  - [OFF]    — no transforms at all; state changes are instant cross-fades.
 *  - [SUBTLE] — small scales (≤1.02), short glides, no tilt or sheen. The
 *               default on televisions, where the remote already provides the
 *               feedback and the hardware is the weakest in the fleet.
 *  - [FULL]   — the complete set. The default on phones and tablets.
 *
 * The system animator scale is an additional hard gate: when it is 0 (Developer
 * options → Animator duration scale → Off, or an accessibility/battery setting
 * that does the same), every level behaves as [OFF]. That is not optional —
 * ignoring it is how apps make people motion-sick.
 */
enum class MotionLevel(val key: String, val label: String) {
    OFF("off", "Off"),
    SUBTLE("subtle", "Subtle"),
    FULL("full", "Full");

    /** Whether any transform should be drawn. */
    val animates: Boolean get() = this != OFF

    /** Whether the showier effects (tilt, sheen, parallax) are allowed. */
    val rich: Boolean get() = this == FULL

    /**
     * A duration in milliseconds, scaled for the level.
     *
     * [SUBTLE] shortens rather than slows: a 260ms reveal becomes 195ms, which
     * still reads as motion but spends less time repainting on a weak GPU.
     */
    fun duration(baseMs: Int): Int = when (this) {
        OFF -> 0
        SUBTLE -> (baseMs * 0.75f).toInt()
        FULL -> baseMs
    }

    /** A scale delta (e.g. 0.975 pressed) pulled toward 1 on [SUBTLE]. */
    fun scale(target: Float): Float = when (this) {
        OFF -> 1f
        SUBTLE -> 1f + (target - 1f) * 0.6f
        FULL -> target
    }

    companion object {
        const val DEFAULT_PHONE = "full"
        const val DEFAULT_TV = "subtle"

        /** The level a device gets before the user chooses one. */
        fun defaultFor(isTv: Boolean): MotionLevel = if (isTv) SUBTLE else FULL

        fun fromKey(key: String?): MotionLevel = when (key?.trim()?.lowercase()) {
            OFF.key -> OFF
            SUBTLE.key -> SUBTLE
            FULL.key -> FULL
            else -> FULL
        }
    }
}

/**
 * The level in force for this composition.
 *
 * Defaults to [MotionLevel.FULL]; `AppRoot` provides the user's choice and the
 * device default in Phase 1. Effects read this rather than the store directly,
 * so previews and tests do not need a `HikariApp`.
 */
val LocalMotionLevel = compositionLocalOf { MotionLevel.FULL }

/**
 * The effective level: the requested one, gated by the system animator scale.
 *
 * Use this in effects. [LocalMotionLevel] alone would ignore the developer
 * setting that says "do not animate".
 */
@Composable
fun rememberMotionLevel(): MotionLevel {
    val requested = LocalMotionLevel.current
    if (requested == MotionLevel.OFF) return MotionLevel.OFF
    val animationsEnabled = rememberAnimationsEnabled()
    return if (animationsEnabled) requested else MotionLevel.OFF
}
