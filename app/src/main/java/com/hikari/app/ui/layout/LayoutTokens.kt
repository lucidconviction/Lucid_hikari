package com.hikari.app.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hikari.app.tv.TvDesktop
import com.hikari.app.tv.TvUi
import com.hikari.app.ui.theme.HikariSpacing

/** How a layout gets to its tabs. */
enum class NavKind {
    /** The floating taskbar at the bottom of a phone. */
    BOTTOM_BAR,

    /** A permanent rail down the left edge (tablet and television). */
    RAIL,
}

/**
 * The layout numbers for one device class.
 *
 * Screens read this instead of hardcoding `TvMode.current()` branches and
 * per-screen padding constants. One object per class means the gutters, the
 * grid density and the navigation shape cannot drift apart — the failure the
 * old code kept producing, where Home's grid and Search's grid disagreed about
 * how wide a poster should be.
 *
 * The television numbers are deliberately pinned to the values the existing TV
 * layout already uses ([TvDesktop], [TvUi]). Adopting this object must be a
 * no-op on a television until a screen opts into the refresh, which is what
 * keeps the TV path safe to migrate one page at a time.
 */
data class LayoutTokens(
    val device: DeviceClass,
    /** Horizontal padding at the page's edges. */
    val pageGutter: Dp,
    /** Vertical gap between major sections (shelves, cards). */
    val sectionGap: Dp,
    /** Gap between cards in a row or grid. */
    val cardGap: Dp,
    /** Minimum cell width for an adaptive poster grid. */
    val gridMin: Dp,
    /** Width a column of content should stop growing at, or [Dp.Infinity]. */
    val maxContentWidth: Dp,
    /** How the tabs are drawn. */
    val navKind: NavKind,
    /** Left inset a page keeps clear of a rail (0 for a bottom bar). */
    val navInset: Dp,
    /** True when list/detail screens should split into two panes. */
    val twoPane: Boolean,
) {
    /** The page's own padding, ready for a `LazyColumn`'s `contentPadding`. */
    val pagePadding: PaddingValues
        get() = PaddingValues(horizontal = pageGutter, vertical = sectionGap)
}

/**
 * [LayoutTokens] for the current device.
 *
 * TV values are frozen to today's constants on purpose — see the class doc.
 * Phone and tablet values are the refresh's new scale.
 */
@Composable
fun rememberLayoutTokens(): LayoutTokens {
    val device = rememberDeviceClass()
    return when (device) {
        DeviceClass.TV -> LayoutTokens(
            device = DeviceClass.TV,
            pageGutter = 0.dp,
            sectionGap = TvDesktop.SpaceL,
            cardGap = TvDesktop.SpaceM,
            gridMin = TvUi.posterWidth(),
            maxContentWidth = Dp.Infinity,
            navKind = NavKind.RAIL,
            navInset = TvUi.RAIL_WIDTH,
            twoPane = false,
        )

        DeviceClass.TABLET -> LayoutTokens(
            device = DeviceClass.TABLET,
            pageGutter = HikariSpacing.pageTablet,
            sectionGap = HikariSpacing.xl,
            cardGap = HikariSpacing.m,
            gridMin = 140.dp,
            maxContentWidth = 1100.dp,
            navKind = NavKind.RAIL,
            navInset = 96.dp,
            twoPane = true,
        )

        DeviceClass.PHONE -> LayoutTokens(
            device = DeviceClass.PHONE,
            pageGutter = HikariSpacing.page,
            sectionGap = HikariSpacing.xl,
            cardGap = HikariSpacing.s,
            gridMin = 120.dp,
            maxContentWidth = Dp.Infinity,
            navKind = NavKind.BOTTOM_BAR,
            navInset = 0.dp,
            twoPane = false,
        )
    }
}
