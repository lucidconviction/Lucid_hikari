package com.hikari.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.theme.HikariType

/**
 * The four kinds of small label the app puts on artwork and rows.
 *
 * The rule the colours enforce: a rating is the only yellow, LIVE is the only
 * red, and a quality/format chip is always white on a dark pill. An accent
 * "Movie" chip on a card that also carries a score reads as a second score,
 * which is why [Neutral] exists.
 */
enum class HikariBadgeKind {
    /** Provenance and facts: "Movie", "S01E04", "1080p". */
    Neutral,

    /** A count or state tied to the app's accent ("3 new"). */
    Accent,

    /** A live stream. The dot is part of the badge, not separate text. */
    Live,

    /** A format or quality claim: "4K", "HDR10", "DD+ 5.1". */
    Quality,
}

/**
 * One small, self-sizing badge.
 *
 * Text is passed already translated/formatted by the caller; the style is the
 * shared [HikariType.micro], so a badge in the player and a badge on a poster
 * are the same size and weight.
 */
@Composable
fun HikariBadge(
    text: String,
    modifier: Modifier = Modifier,
    kind: HikariBadgeKind = HikariBadgeKind.Neutral,
) {
    val colors = when (kind) {
        HikariBadgeKind.Neutral -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f) to
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
        HikariBadgeKind.Accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) to
            MaterialTheme.colorScheme.primary
        HikariBadgeKind.Live -> Color(0xFFE5383B) to Color.White
        HikariBadgeKind.Quality -> Color.Black.copy(alpha = 0.72f) to Color.White
    }
    val background = colors.first
    val foreground = colors.second

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (kind == HikariBadgeKind.Live) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = HikariType.micro,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
