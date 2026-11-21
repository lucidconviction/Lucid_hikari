package com.hikari.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.effects.cardPress
import com.hikari.app.ui.theme.HikariMotion
import com.hikari.app.ui.theme.HikariRadii
import com.hikari.app.ui.theme.HikariSpacing
import com.hikari.app.ui.theme.HikariType
import com.hikari.app.ui.theme.rememberAnimationsEnabled

/**
 * One selectable chip.
 *
 * Shared so a filter row on Home, a section strip on Live TV and a sport
 * selector on Sports stop being three different-looking controls. The height
 * is a MINIMUM, not a fixed size: at a 1.3x system font a fixed 32dp chip clips
 * its own label, which is exactly the kind of accessibility bug a shared
 * component exists to prevent.
 *
 * [selected] is the accent fill; unselected is a quiet translucent surface with
 * the same hairline every other panel in the app uses.
 */
@Composable
fun HikariChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val animations = rememberAnimationsEnabled()
    val spec: AnimationSpec<Color> =
        if (animations) tween<Color>(HikariMotion.quick, easing = HikariMotion.easeOut) else snap()

    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        animationSpec = spec,
        label = "hikariChipBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spec,
        label = "hikariChipForeground",
    )
    val shape = RoundedCornerShape(HikariRadii.pill)
    val interactions = remember { MutableInteractionSource() }
    val indication = androidx.compose.foundation.LocalIndication.current

    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                shape = shape,
            )
            .then(if (enabled) Modifier.cardPress(interactions, pressedScale = 0.96f) else Modifier)
            .clickable(
                interactionSource = interactions,
                indication = indication,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = HikariSpacing.m, vertical = HikariSpacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(HikariSpacing.xs))
        }
        Text(
            text = label,
            style = HikariType.label,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A horizontally scrolling row of [HikariChip]s.
 *
 * [key] must be unique per item when the labels can repeat (two portals can
 * both have a "Sports" group); without one the label is used, which is fine for
 * a plain section strip. [leading] is for a fixed chip that is not part of the
 * data (an "All" entry, a settings affordance).
 */
@Composable
fun <T> HikariChipRow(
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = HikariSpacing.l),
    key: ((T) -> Any)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(HikariSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            item(key = "hikariChipLeading") { leading() }
        }
        items(
            items = items,
            key = { item -> key?.invoke(item) ?: label(item) },
        ) { item ->
            HikariChip(
                label = label(item),
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}
