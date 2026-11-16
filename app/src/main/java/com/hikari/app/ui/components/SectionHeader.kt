package com.hikari.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.theme.HikariSpacing
import com.hikari.app.ui.theme.HikariType

/**
 * One shelf/page header for the whole app.
 *
 * The shape is deliberately small: a 3dp accent bar, a 15sp bold title, an
 * optional one-line provenance subtitle, an optional count, and one trailing
 * action. It replaces the six different header implementations the app grew
 * (Home's MediaRow header, the TV Cs3ShelfHeader stays separate, IPTV, Sports,
 * SportsLeague, Extensions, Music), so two shelves in two sections finally look
 * like the same product.
 *
 * [title] is passed already translated (callers use tr()), because a shared
 * component cannot know whether the string came from an extension or from the
 * app's own copy.
 *
 * @param count shown as a quiet number before the action ("· 42").
 * @param onAction when non-null the whole trailing block is a button.
 * @param actionLabel the visible action text; when null only the chevron is
 *   drawn, so pass a translated label wherever the action is not obvious.
 */
@Composable
fun HikariSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    count: Int? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
    showChevron: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val trailingModifier = if (onAction != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onAction)
            .padding(horizontal = HikariSpacing.s, vertical = HikariSpacing.xs)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HikariSpacing.l, vertical = HikariSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The accent bar is 3dp wide and a little taller than the title's cap
        // height, so it reads as a marker rather than a border.
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(HikariSpacing.m))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = HikariType.heading,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Provenance is metadata, not a second title: 10.5sp, uppercase,
            // and noticeably dimmer than the heading above it.
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle.uppercase(),
                    style = HikariType.micro,
                    color = dim.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (count != null) {
            Text(
                text = count.toString(),
                style = HikariType.label,
                color = dim.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = HikariSpacing.s),
            )
        }

        if (onAction != null) {
            Row(
                modifier = trailingModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HikariSpacing.xs),
            ) {
                if (!actionLabel.isNullOrBlank()) {
                    Text(
                        text = actionLabel,
                        style = HikariType.label,
                        color = accent,
                        maxLines = 1,
                    )
                }
                if (showChevron) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = accent,
                    )
                }
            }
        }
    }
}
