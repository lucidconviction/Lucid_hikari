package com.hikari.app.ui.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.theme.HikariMotion
import com.hikari.app.ui.theme.HikariRadii
import com.hikari.app.ui.theme.HikariSpacing
import com.hikari.app.ui.theme.HikariType
import kotlin.math.roundToInt

/**
 * A slider that floats above the surface it controls.
 *
 * The platform slider is a rail with a handle; on Hikari's glass this reads as
 * a stamped metal part dropped onto the page. This is the same control drawn as
 * a pill of light with a value bubble that rises from the thumb while it is
 * being dragged — the number appears where the eye already is, instead of in a
 * label at the end of the row.
 *
 * Behavioural contract is identical to a normal slider, which matters for the
 * settings screens: [onValueChange] fires continuously during a drag, while
 * [onValueChangeFinished] fires once on release. Callers that apply an
 * expensive change (video enhancement, stream quality) keep committing on that
 * second callback — the same one-swap-per-gesture rule the player already
 * follows.
 *
 * [steps] follows the Material convention: the number of stops *between* the
 * endpoints. Zero means continuous.
 */
@Composable
fun FloatingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    alwaysShowBubble: Boolean = false,
    label: (Float) -> String = { it.roundToInt().toString() },
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val level = rememberMotionLevel()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var lastStep by remember { mutableStateOf<Int?>(null) }

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        // While a finger is down the thumb must track it exactly; an animation
        // in between reads as lag. The tween is only for programmatic changes.
        animationSpec = if (dragging || !level.animates) {
            snap()
        } else {
            tween(level.duration(170), easing = HikariMotion.easeOut)
        },
        label = "floatingSliderFraction",
    )

    fun quantize(raw: Float): Float {
        if (steps <= 0) return raw
        val stepSize = span / (steps + 1)
        val snapped = valueRange.start +
            (((raw - valueRange.start) / stepSize).roundToInt() * stepSize)
        return snapped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    fun emit(raw: Float) {
        val next = quantize(raw)
        if (steps > 0) {
            val index = (((next - valueRange.start) / span) * (steps + 1)).roundToInt()
            if (index != lastStep) {
                lastStep = index
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        onValueChange(next)
    }

    val thumb = 20.dp
    val bubbleWidth = 64.dp

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(
                if (!enabled) Modifier
                else Modifier
                    .pointerInput(valueRange, steps, enabled) {
                        detectTapGestures { offset ->
                            val usable = (size.width - thumb.toPx()).coerceAtLeast(1f)
                            val f = ((offset.x - thumb.toPx() / 2f) / usable).coerceIn(0f, 1f)
                            emit(valueRange.start + f * span)
                            onValueChangeFinished?.invoke()
                        }
                    }
                    .pointerInput(valueRange, steps, enabled) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                lastStep = null
                            },
                            onDragEnd = {
                                dragging = false
                                onValueChangeFinished?.invoke()
                            },
                            onDragCancel = { dragging = false },
                        ) { change, _ ->
                            val usable = (size.width - thumb.toPx()).coerceAtLeast(1f)
                            val f = ((change.position.x - thumb.toPx() / 2f) / usable)
                                .coerceIn(0f, 1f)
                            emit(valueRange.start + f * span)
                        }
                    },
            ),
    ) {
        val usable = (maxWidth - thumb).coerceAtLeast(0.dp)
        val thumbCenter = thumb / 2 + usable * animatedFraction
        val bubbleX = (thumbCenter - bubbleWidth / 2).coerceIn(0.dp, (maxWidth - bubbleWidth).coerceAtLeast(0.dp))

        AnimatedVisibility(
            visible = (dragging || alwaysShowBubble) && enabled,
            enter = fadeIn(tween(level.duration(120))) ,
            exit = fadeOut(tween(level.duration(100))),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = bubbleX),
        ) {
            Box(
                Modifier
                    .width(bubbleWidth)
                    .clip(RoundedCornerShape(HikariRadii.chip))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = HikariSpacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(value),
                    style = HikariType.label,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
            }
        }

        // The track and its filled portion sit centred in the lower half, so the
        // bubble above has room to rise without moving the whole control.
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (enabled) 0.14f else 0.07f,
                        )
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(thumbCenter)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (enabled) 1f else 0.35f,
                        )
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbCenter - thumb / 2)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    ),
            )
        }
    }
}
