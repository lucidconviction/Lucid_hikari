package com.hikari.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The six text roles the app actually needs.
 *
 * These are DERIVED from [MaterialTheme.typography] rather than built from
 * scratch, which matters: the app lets the user pick an app-wide font (see
 * typographyWith in Type.kt), and a style that hard-codes a family would
 * silently opt out of that choice. Reading the Material styles first keeps the
 * user's family; only the size, weight and tracking are set here.
 *
 * Call sites that migrate to these roles stop passing ad-hoc `fontSize = 12.sp`
 * values, which is what makes two screens that show the same kind of text
 * actually look the same.
 */
object HikariType {
    /** The one hero title on a page. Never used for a card or a shelf. */
    val display: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineLarge.copy(
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp,
        )

    /** A page title ("Live TV", "Discover"). */
    val title: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineMedium.copy(
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )

    /** A shelf header ("Popular Movies"). */
    val heading: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        )

    /** Body copy, descriptions, episode plots. */
    val body: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
        )

    /** Buttons, chips, labels. */
    val label: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

    /**
     * Provenance and metadata: the provider under a shelf, a format badge,
     * a quality chip. Uppercase and tracked out by the caller when needed; the
     * size is already small enough that it never competes with a title.
     */
    val micro: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
}
