package com.hikari.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two panes side by side — the shape behind tablet Detail (art/meta left,
 * episodes and servers right), tablet Settings (folders left, cards right) and
 * the television's own split screens.
 *
 * The split is a [Row] of two slots rather than a list-detail controller,
 * because Hikari's detail screens are already driven by their own state, not by
 * a back stack: a pane is shown because the title is open, not because a
 * `NavHost` moved. Keeping the primitive this small means adopting it is a
 * layout wrapper, never a rewrite.
 *
 * On a narrow window the second pane is not drawn at all — [showSecond] is the
 * caller's decision (a phone shows the detail as its own screen). This
 * composable never guesses, so a screen can opt into two panes without changing
 * how it behaves anywhere else.
 */
@Composable
fun TwoPane(
    modifier: Modifier = Modifier,
    showSecond: Boolean = true,
    firstWeight: Float = 0.38f,
    secondWeight: Float = 1f,
    paneGap: Dp = 24.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(paneGap),
    ) {
        Box(
            Modifier
                .weight(firstWeight)
                .fillMaxHeight(),
        ) { first() }
        if (showSecond) {
            Box(
                Modifier
                    .weight(secondWeight)
                    .fillMaxHeight(),
            ) { second() }
        }
    }
}

/**
 * True when the screen has room for two panes: an expanded window on a touch
 * device, or a television (which always does).
 *
 * [WindowSize.MEDIUM] is deliberately not enough for a split: two panes at
 * 600-839dp look cramped, and a large phone in landscape reaches MEDIUM
 * legitimately without being a tablet.
 */
@Composable
fun rememberTwoPane(): Boolean {
    val device = rememberDeviceClass()
    if (device == DeviceClass.TV) return true
    return rememberWindowSize() == WindowSize.EXPANDED
}

/**
 * Centres a column of content and stops it growing past [maxWidth].
 *
 * A tablet in landscape can be 1280dp wide; a single column of settings rows
 * stretched across all of it is unreadable, and a poster grid stretched across
 * it looks sparse. This wrapper is the cheap fix: content stays at a sane
 * measure, with the background painting edge to edge behind it. Pass
 * [Dp.Infinity] to opt out.
 */
@Composable
fun ConstrainedContent(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (maxWidth == Dp.Infinity) {
        Box(modifier.fillMaxWidth()) { content() }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) { content() }
    }
}

/**
 * A hairline vertical divider for a two-pane split, tuned to the theme rather
 * than to a hardcoded grey.
 */
@Composable
fun PaneDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    )
}
