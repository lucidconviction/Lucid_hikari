package com.hikari.app.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The one place a layout decides where its navigation goes.
 *
 * Today `AppRoot` encodes this decision itself with two `if (isTv)` branches —
 * one for the taskbar, one for the rail. That works while there are only two
 * device classes, but a tablet needs the rail *and* its own rail component, and
 * the television needs its existing one unchanged. This wrapper takes the two
 * nav slots as parameters and positions whichever [NavKind] the current
 * [LayoutTokens] asks for, so the root screen does not have to grow a third
 * branch.
 *
 * The slots are plain composables on purpose: the phone's floating taskbar and
 * the television's `TvNavRail` stay exactly as they are, and adopting this
 * wrapper changes only their placement.
 *
 * [content] receives the whole box, so screens that paint edge to edge (the
 * hero carousels) keep doing so.
 */
@Composable
fun AdaptiveNav(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    rail: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val navKind = rememberLayoutTokens().navKind
    Box(modifier) {
        content()
        when (navKind) {
            NavKind.BOTTOM_BAR ->
                Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }

            NavKind.RAIL ->
                Box(Modifier.align(Alignment.CenterStart)) { rail() }
        }
    }
}

/**
 * The left inset a page's content must keep clear of the navigation, or 0 when
 * there is nothing to clear.
 *
 * A rail overlays the content edge (the existing television layout does exactly
 * this), so any page that wants to start after it adds this to its start
 * padding. On a phone the bottom bar floats as well, which is why the phone
 * answer is 0 — the bar's clearance is vertical, handled by the bar's own
 * height where a list needs it.
 */
@Composable
fun navStartInset(): androidx.compose.ui.unit.Dp = rememberLayoutTokens().navInset
