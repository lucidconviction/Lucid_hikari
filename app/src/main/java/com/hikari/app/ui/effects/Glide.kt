package com.hikari.app.ui.effects

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.hikari.app.ui.theme.HikariMotion
import kotlinx.coroutines.launch

/**
 * The app's smooth-motion vocabulary.
 *
 * "Glide" is the refresh's name for motion that carries the eye: a shelf that
 * snaps to a card edge, a page that slides along the axis you moved in, a list
 * that returns to the top in one gesture. None of it changes what a control
 * does — it changes how the change is perceived.
 *
 * Every helper degrades at [MotionLevel.OFF]: transitions become a plain
 * cross-fade and snapping stays, because snapping is a *positioning* rule, not
 * an animation. Turning motion off must never make a shelf land between cards.
 */

/**
 * A shelf that always comes to rest on a card edge.
 *
 * Without this a horizontal poster row stops wherever the finger leaves it,
 * showing half a card at each end — the visual equivalent of a sentence cut in
 * the middle of a word. Snapping is independent of [MotionLevel] on purpose:
 * it is how the row is supposed to sit, not an effect.
 */
@Composable
fun rememberShelfSnap(state: LazyListState) = rememberSnapFlingBehavior(lazyListState = state)

/**
 * A one-gesture return to the top of a list, e.g. when the user re-taps the tab
 * they are already on.
 */
@Composable
fun rememberGlideToTop(state: LazyListState): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        { scope.launch { state.animateScrollToItem(0) } }
    }
}

/**
 * Page transitions along the horizontal axis — moving between sibling tabs.
 *
 * A short slide plus a fade is enough to say "this page is beside that one";
 * the distance is deliberately small (a twelfth of the width) so it reads as a
 * glide rather than a carousel.
 */
@Composable
fun glideEnter(): EnterTransition {
    val level = rememberMotionLevel()
    val ms = level.duration(HikariMotion.standard)
    return if (!level.animates) {
        fadeIn(tween(0))
    } else {
        fadeIn(tween(ms)) +
            slideInHorizontally(tween(ms, easing = HikariMotion.easeOut)) { it / 12 }
    }
}

@Composable
fun glideExit(): ExitTransition {
    val level = rememberMotionLevel()
    val ms = level.duration(HikariMotion.quick)
    return if (!level.animates) {
        fadeOut(tween(0))
    } else {
        fadeOut(tween(ms))
    }
}

@Composable
fun glidePopEnter(): EnterTransition {
    val level = rememberMotionLevel()
    val ms = level.duration(HikariMotion.standard)
    return if (!level.animates) {
        fadeIn(tween(0))
    } else {
        fadeIn(tween(ms)) +
            slideInHorizontally(tween(ms, easing = HikariMotion.easeOut)) { -it / 12 }
    }
}

@Composable
fun glidePopExit(): ExitTransition {
    val level = rememberMotionLevel()
    val ms = level.duration(HikariMotion.quick)
    return if (!level.animates) {
        fadeOut(tween(0))
    } else {
        fadeOut(tween(ms)) +
            slideOutHorizontally(tween(ms, easing = HikariMotion.easeOut)) { it / 12 }
    }
}

/**
 * The duration for a glide at the current level.
 *
 * Exposed so a screen with its own animation (a pager, a collapsing header) can
 * obey the same preference instead of hardcoding a tween.
 */
@Composable
fun glideDuration(baseMs: Int = HikariMotion.standard): Int =
    rememberMotionLevel().duration(baseMs)
