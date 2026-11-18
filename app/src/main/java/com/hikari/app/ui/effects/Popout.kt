package com.hikari.app.ui.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hikari.app.ui.theme.HikariMotion
import com.hikari.app.ui.theme.HikariRadii
import com.hikari.app.ui.theme.HikariSpacing
import com.hikari.app.ui.theme.rememberGlassTokens

/**
 * A small panel that pops out of the control that opened it.
 *
 * The refresh distinguishes two kinds of overlay. A *sheet* covers the screen
 * because the choice is the whole task; a *popout* appears beside the thing it
 * belongs to, because the choice is a footnote to it. The server list behind a
 * "Servers" button, the actions behind a long-pressed poster and the quality
 * list behind a pill are all popouts — they should feel like they grew out of
 * their button, not like the app changed pages.
 *
 * Implemented with [Popup] rather than a dialog so the content underneath keeps
 * its own layout and scroll position, and anchored at the caller's chosen
 * point so a television's D-pad stays on the same row.
 *
 * [focusable] matters on a television: a focusable popup traps the D-pad, which
 * is right for a menu and wrong for a pure readout.
 */
@Composable
fun Popout(
    visible: Boolean,
    onDismiss: () -> Unit,
    focusable: Boolean = true,
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset.Zero,
    content: @Composable () -> Unit,
) {
    val level = rememberMotionLevel()
    val enter = if (level.animates) {
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(level.duration(200), easing = HikariMotion.easeOut),
        ) + fadeIn(tween(level.duration(140)))
    } else {
        fadeIn(tween(0))
    }
    val exit = if (level.animates) {
        scaleOut(
            targetScale = 0.94f,
            animationSpec = tween(level.duration(130), easing = HikariMotion.easeOut),
        ) + fadeOut(tween(level.duration(110)))
    } else {
        fadeOut(tween(0))
    }
    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        Popup(
            alignment = alignment,
            offset = offset,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = focusable),
        ) {
            content()
        }
    }
}

/**
 * The visual body of a [Popout]: the same glass recipe as every other panel in
 * the app, at a size that suits a menu rather than a page.
 *
 * The width is capped because a popout is a footnote; if the content needs to
 * be wider than this it wants a dialog, not a popout.
 */
@Composable
fun PopoutPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = rememberGlassTokens()
    val shape = RoundedCornerShape(HikariRadii.dialog)
    // Same rule as GlassCard: a shadow only separates the panel on the LIGHT
    // theme, where it sits on paper. On the dark themes it would mud the
    // translucent fill, and the hairline does the work instead.
    val elevation = if (glass.dark) Modifier else Modifier.shadow(10.dp, shape)
    Column(
        modifier
            .widthIn(min = 220.dp, max = 340.dp)
            .then(elevation)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(glass.fillTop, glass.fillBottom)),
                shape,
            )
            .border(1.dp, glass.border, shape)
            .padding(HikariSpacing.xs),
        content = content,
    )
}
