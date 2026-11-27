package com.hikari.app.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.hikari.app.ui.theme.HikariMotion
import kotlin.math.abs

/**
 * The card-level effects shared by every tappable surface.
 *
 * All of these are `Modifier` extensions so a card opts in with one line and
 * removes the effect by removing that line — nothing here is baked into
 * `GlassCard` or a screen. Each one is a `graphicsLayer` transform, which means
 * it runs on the render thread and never triggers a recomposition per frame.
 *
 * Every effect reads [rememberMotionLevel] and degrades to a no-op at
 * [MotionLevel.OFF]. Crucially the state reads are unconditional (only the
 * drawn values depend on the level), because a composable that adds or removes
 * remembered slots based on a runtime preference is a crash waiting to happen.
 *
 * The showier effects — [cardTilt], [cardSheen] — are gated on
 * [MotionLevel.rich], so a television or a low-end box never pays for them.
 */

/**
 * Press feedback: the card sinks a little while a finger is on it.
 *
 * This is the effect that makes a grid feel physical; it is also the cheapest
 * (one layer transform). A [pressedScale] near 1 matters — 0.9 reads as a
 * bounce, 0.975 as weight.
 */
@Composable
fun Modifier.cardPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.975f,
): Modifier {
    val level = rememberMotionLevel()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (level.animates && pressed) level.scale(pressedScale) else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cardPressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Hover and focus lift: the card grows slightly when a pointer is over it or
 * the D-pad has landed on it.
 *
 * Two targets rather than one because the two inputs mean different things: a
 * mouse hovering is a preview, a remote focusing is a commitment. The focus
 * target is smaller so a wall of cards does not ripple while the user walks
 * the grid.
 */
@Composable
fun Modifier.cardLift(
    interactionSource: InteractionSource,
    hoverScale: Float = 1.025f,
    focusScale: Float = 1.012f,
): Modifier {
    val level = rememberMotionLevel()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val target = when {
        focused -> level.scale(focusScale)
        hovered -> level.scale(hoverScale)
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = if (level.animates) target else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cardLiftScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A subtle 3D tilt that follows the pointer.
 *
 * Deliberately small (4° by default) and pointer-only: on a touch screen the
 * finger is already on the card, so tilting under it would fight the content
 * being read. It only applies at [MotionLevel.rich] — a D-pad cannot express a
 * position, so a television never gets it, and weak GPUs never pay for the
 * extra layer.
 */
@Composable
fun Modifier.cardTilt(
    maxDegrees: Float = 4f,
    enabled: Boolean = true,
): Modifier {
    val level = rememberMotionLevel()
    if (!level.rich || !enabled) return this

    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    val animatedX by animateFloatAsState(
        targetValue = tiltX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardTiltX",
    )
    val animatedY by animateFloatAsState(
        targetValue = tiltY,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardTiltY",
    )
    return this
        // Initial pass only: the card still receives every tap, drag and long
        // press, the tilt merely observes where the pointer is.
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Exit) {
                        tiltX = 0f
                        tiltY = 0f
                        continue
                    }
                    val change = event.changes.firstOrNull() ?: continue
                    if (!change.pressed) {
                        tiltX = 0f
                        tiltY = 0f
                        continue
                    }
                    val w = size.width.coerceAtLeast(1)
                    val h = size.height.coerceAtLeast(1)
                    tiltY = ((change.position.x / w) - 0.5f) * 2f * maxDegrees
                    tiltX = -((change.position.y / h) - 0.5f) * 2f * maxDegrees
                }
            }
        }
        .graphicsLayer {
            rotationX = animatedX
            rotationY = animatedY
            // The camera has to sit far enough back that 4° does not look like
            // a funhouse mirror; 16× density is the Compose default tuned up.
            cameraDistance = 16f * density
        }
}

/**
 * A band of light that sweeps across the card once per press.
 *
 * This is the "glass" half of the refresh: the surface catches the light for a
 * moment, then goes still. It is drawn as a gradient rather than a bitmap, so
 * it costs one draw call; on the light theme it is skipped entirely, where a
 * white sweep on a white card is invisible at best.
 */
@Composable
fun Modifier.cardSheen(
    interactionSource: InteractionSource,
): Modifier {
    val level = rememberMotionLevel()
    val pressed by interactionSource.collectIsPressedAsState()
    val progress = remember { Animatable(0f) }

    LaunchedEffect(pressed) {
        if (pressed && level.rich) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = HikariMotion.easeOut))
        }
    }

    return this.drawWithContent {
        drawContent()
        if (!level.rich) return@drawWithContent
        val p = progress.value
        if (p <= 0f || p >= 1f) return@drawWithContent
        val band = size.width * 0.45f
        val x = -band + (size.width + band * 2f) * p
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                start = Offset(x, 0f),
                end = Offset(x + band, size.height),
            ),
        )
    }
}

/**
 * A shelf card's entrance cue: items near the edge of the viewport sit dimmer
 * and slightly lower, and settle to full strength as they reach the middle.
 *
 * Reads the lazy layout inside `graphicsLayer`, so scrolling invalidates only
 * the layer — this is the phone/tablet counterpart of the television's
 * `tvScrollReveal`, and it exists for the same reason: a row that fades in as
 * it is reached feels deeper than a row that is simply there.
 */
@Composable
fun Modifier.cardReveal(
    state: LazyListState,
    index: Int,
): Modifier {
    val level = rememberMotionLevel()
    if (!level.animates) return this
    val minAlpha = if (level == MotionLevel.FULL) 0.55f else 0.75f
    return this.graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            ?: return@graphicsLayer
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val itemCenter = item.offset + item.size / 2f
        val edge = (abs(itemCenter - center) / (viewport / 2f)).coerceIn(0f, 1f)
        val depth = 1f - edge
        alpha = minAlpha + (1f - minAlpha) * depth
        translationY = (1f - depth) * 10f * density
    }
}

/**
 * [cardReveal] for a poster grid.
 *
 * Grid items expose an `IntOffset` rather than a scalar offset, so the cue is
 * measured on the vertical axis — the direction a grid actually scrolls.
 */
@Composable
fun Modifier.gridReveal(
    state: LazyGridState,
    index: Int,
): Modifier {
    val level = rememberMotionLevel()
    if (!level.animates) return this
    val minAlpha = if (level == MotionLevel.FULL) 0.55f else 0.75f
    return this.graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            ?: return@graphicsLayer
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val itemCenter = item.offset.y + item.size.height / 2f
        val edge = (abs(itemCenter - center) / (viewport / 2f)).coerceIn(0f, 1f)
        val depth = 1f - edge
        alpha = minAlpha + (1f - minAlpha) * depth
        translationY = (1f - depth) * 12f * density
    }
}

/**
 * Scroll parallax for artwork inside a card.
 *
 * The art should be drawn slightly larger than its frame (a 1.06-1.10 scale) so
 * the shift never exposes an edge; the caller owns that. The shift itself is a
 * fraction of the card height, measured against how far the card is from the
 * middle of the viewport, which is what makes a shelf feel like it has depth.
 */
@Composable
fun Modifier.artworkParallax(
    state: LazyListState,
    index: Int,
    strength: Float = 0.06f,
): Modifier {
    val level = rememberMotionLevel()
    if (!level.rich) return this
    return this.graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            ?: return@graphicsLayer
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val itemCenter = item.offset + item.size / 2f
        val offset = ((itemCenter - center) / viewport).coerceIn(-0.5f, 0.5f)
        translationY = -offset * size.height * strength
    }
}
