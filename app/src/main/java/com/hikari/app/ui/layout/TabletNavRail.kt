package com.hikari.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.i18n.tr
import com.hikari.app.ui.effects.cardPress
import com.hikari.app.ui.navigation.BottomTab
import com.hikari.app.ui.navigation.BottomTabs
import com.hikari.app.ui.theme.HikariSpacing
import com.hikari.app.ui.theme.rememberGlassTokens

/** The tablet rail's width. Also the left inset a page keeps clear of it. */
val TabletRailWidth = 96.dp

/**
 * The tablet's tall left navigation bar.
 *
 * A tablet is held in two hands or propped on a stand, so the bottom edge is
 * not where the thumbs are — and the screen is wide enough that a bottom bar
 * wastes the shape. The rail is the same idea the television already uses, but
 * built for touch: always labelled (a TV rail collapses to icons because the
 * remote is modal; a finger benefits from seeing every destination), a small
 * press response instead of a focus ring, and no D-pad-only affordances.
 *
 * The tabs are the caller's existing list and hidden set, so the two layouts
 * cannot disagree about which pages exist or which the user switched off.
 */
@Composable
fun TabletNavRail(
    currentRoute: String?,
    hidden: Set<String>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens()
    // Same "never empty" rule as the taskbar: a rail with nothing in it would
    // lock the user out of every page, so an all-hidden set is ignored.
    val tabs: List<BottomTab> = remember(hidden) {
        BottomTabs.filter { it.route !in hidden }.ifEmpty { BottomTabs }
    }
    val state = rememberLazyListState()

    LazyColumn(
        modifier
            .width(TabletRailWidth)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(listOf(glass.fillTop, glass.fillBottom)),
            )
            // One hairline on the content side: the rail is a pane of glass
            // standing at the edge of the page, not a coloured slab. Without it
            // the rail and the page read as one dark surface on AMOLED.
            .drawWithContent {
                drawContent()
                drawLine(
                    color = glass.border,
                    start = Offset(size.width - 0.5f, 0f),
                    end = Offset(size.width - 0.5f, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(vertical = HikariSpacing.s),
        state = state,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HikariSpacing.xxs),
    ) {
        items(tabs, key = { it.route }) { tab ->
            TabletRailItem(
                tab = tab,
                selected = tab.route == currentRoute,
                onClick = { onNavigate(tab.route) },
            )
        }
    }
}

@Composable
private fun TabletRailItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val accent = MaterialTheme.colorScheme.primary
    val ink = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HikariSpacing.xs)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .cardPress(interactions, pressedScale = 0.94f)
            .clickable(
                interactionSource = interactions,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
            )
            .padding(vertical = HikariSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tr(tab.label),
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tr(tab.label),
            color = ink,
            // A rail label is a signpost, not a sentence: one line, small, and
            // ellipsised rather than wrapped (a wrapped label makes one rail
            // item taller than its neighbours and breaks the rhythm).
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
