package com.hikari.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A four-step text ramp, replacing the app's old pair (`onSurface` +
 * `onSurfaceVariant`).
 *
 * Two values are not enough to rank information. A screen ends up borrowing
 * the mutest tone for things that are not equally quiet — a rating, a year and
 * a disabled control all looked the same weight. Four steps make the hierarchy
 * explicit:
 *
 *  - [primary] — the thing the row is about (a title, a value).
 *  - [secondary] — supporting facts (year, genre, duration).
 *  - [tertiary] — labels and hints that only matter while you are reading.
 *  - [disabled] — genuinely unavailable content, and nothing else.
 *
 * The steps are derived from the theme's own ink rather than hardcoded, so the
 * Light theme gets dark ink on paper and the dark themes keep white ink — and
 * all four theme modes (Dark, Glass, AMOLED, Light) get the same hierarchy.
 */
data class HikariInk(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val disabled: Color,
)

@Composable
fun rememberHikariInk(): HikariInk {
    val scheme = MaterialTheme.colorScheme
    // Same detector as the glass recipe: the Light theme is the only one whose
    // page is brighter than mid-grey. Glass is transparent (luminance 0), which
    // lands on the dark side on purpose — it is a dark theme.
    val dark = scheme.background.luminance() < 0.5f
    return remember(dark, scheme.onSurface) {
        val ink = scheme.onSurface
        if (dark) {
            HikariInk(
                primary = ink,
                secondary = ink.copy(alpha = 0.72f),
                tertiary = ink.copy(alpha = 0.52f),
                disabled = ink.copy(alpha = 0.34f),
            )
        } else {
            HikariInk(
                primary = ink,
                secondary = ink.copy(alpha = 0.74f),
                tertiary = ink.copy(alpha = 0.56f),
                disabled = ink.copy(alpha = 0.38f),
            )
        }
    }
}
