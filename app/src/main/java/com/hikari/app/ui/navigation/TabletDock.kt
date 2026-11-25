package com.hikari.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hikari.app.i18n.tr
import com.hikari.app.ui.theme.rememberGlassTokens
import kotlin.math.abs

/** The tablet dock's width. The page inset leaves a 12dp gap. */
val TabletDockWidth = 72.dp

/** The room a page keeps clear while the dock is open: plate + the 12dp gap.
 *  Folding the dock drops the page back to the full window width. */
val TabletDockInset = 84.dp

/** The dock's vertical numbers, kept separate from Compose so they can be
 *  unit-tested: a 15-tab dock has to fit a 600dp-tall landscape window. */
internal data class VerticalDockMetrics(
    val cell: Dp,
    val tile: Dp,
    val plateHeight: Dp,
)

/**
 * How a vertical dock lays its tabs out for the height it has.
 *
 * The cells share the height rather than scroll in the normal case, exactly
 * like the phone dock where the cells share the width. 10dp of plate padding
 * top and bottom, and 2dp between cells, are part of the calculation so the
 * plate never asks for more room than it has. The floor is what keeps the
 * icons tappable on a short split-screen window; if the result still cannot
 * fit, [TabletDock] falls back to a scrolling column.
 */
internal fun verticalDockMetrics(tabCount: Int, availableHeight: Dp): VerticalDockMetrics {
    val count = tabCount.coerceAtLeast(1)
    val gaps = 2.dp * (count - 1)
    val usable = (availableHeight - 24.dp - gaps).coerceAtLeast(120.dp)
    val cell = (usable / count).coerceAtMost(48.dp).coerceAtLeast(34.dp)
    val tile = (cell - 12.dp).coerceIn(20.dp, 32.dp)
    return VerticalDockMetrics(cell, tile, cell * count + gaps + 20.dp)
}

/**
 * The Mac dock, rotated for a tablet.
 *
 * A tablet is held in two hands or propped on a stand, and its wide edge is
 * where the page lives — so the dock runs down the left edge, vertically
 * centred, rather than across the bottom. It is the same dock as the phone:
 * the same plate, per-tab tiles, magnification wave, running-app dot, haptics,
 * long-press fold and folded-circle tab wheel. Only the axis changes, which is
 * why the tile and its material live in DockParts.kt and this file only owns
 * the vertical arrangement.
 *
 * The dock sits flush to the screen edge and is rounded on the content side
 * only. The page already keeps 96dp clear on a tablet, so the plate never
 * covers content and no screen needs a new inset. Long-press folds it to a
 * circle at the left centre; a tap unfolds it, and a vertical swipe on the
 * circle walks the tabs while the shelf stays closed.
 */
@Composable
fun TabletDock(
    currentRoute: String?,
    hidden: Set<String>,
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens()
    val haptics = LocalHapticFeedback.current
    // Same "never empty" rule as the taskbar: a dock with nothing in it would
    // lock the user out of every page, so an all-hidden set is ignored.
    val tabs = remember(hidden) {
        BottomTabs.filter { it.route !in hidden }.ifEmpty { BottomTabs }
    }
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    var appeared by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    // The fold itself is owned by AppRoot: shelving the dock also lets the page
    // take the full window width, so the nav inset cannot live down here.
    LaunchedEffect(Unit) { appeared = true }
    // Hiding a tab in Settings can shrink the list under a live hover index;
    // left stale it would make every tile measure its distance from a tab that
    // no longer exists and none of them would be the hovered one.
    LaunchedEffect(tabs.size) {
        if (hoveredIndex >= tabs.size) hoveredIndex = -1
    }

    BoxWithConstraints(
        modifier
            .width(TabletDockWidth)
            .fillMaxHeight(),
    ) {
        val metrics = verticalDockMetrics(tabs.size, maxHeight)
        val fits = metrics.plateHeight <= maxHeight
        val plateHeight = minOf(metrics.plateHeight, maxHeight)
        val plateShape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 24.dp,
            bottomEnd = 24.dp,
            bottomStart = 0.dp,
        )

        AnimatedVisibility(
            visible = appeared && !collapsed,
            enter = slideInHorizontally(tween(420)) { -it / 3 } + fadeIn(tween(300)),
            exit = scaleOut(targetScale = 0.55f, animationSpec = tween(160)) + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .width(TabletDockWidth)
                    .height(plateHeight),
            ) {
                // Sibling of the tiles, not their parent: a magnified tile is
                // never clipped by the glass.
                Box(Modifier.matchParentSize().dockPlate(glass, plateShape))

                if (fits) {
                    val cellPx = with(LocalDensity.current) { metrics.cell.toPx() }
                    val gapPx = with(LocalDensity.current) { 2.dp.toPx() }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp)
                            // Follow the finger up and down the dock. The
                            // pointer is never consumed here, so a plain tap
                            // still reaches the tile's own click and a
                            // long-press still folds the dock.
                            .pointerInput(metrics.cell, tabs.size) {
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    fun indexAt(y: Float): Int =
                                        (y / (cellPx + gapPx)).toInt()
                                            .coerceIn(0, tabs.lastIndex)
                                    var current = indexAt(down.position.y)
                                    var moved = false
                                    hoveredIndex = current
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (abs(change.position.y - down.position.y) > touchSlop) {
                                            moved = true
                                        }
                                        val next = indexAt(change.position.y)
                                        if (next != current) {
                                            current = next
                                            hoveredIndex = next
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                            )
                                        }
                                        if (!change.pressed) break
                                    }
                                    hoveredIndex = -1
                                    // A slide is a selection gesture, not a
                                    // tap: lifting over a tile opens that tab.
                                    if (moved && current in tabs.indices) {
                                        onNavigate(tabs[current].route)
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Box(
                                Modifier
                                    .height(metrics.cell)
                                    .fillMaxWidth()
                                    // The magnified tile must draw over its
                                    // neighbours; zIndex belongs on the
                                    // sibling wrapper, not inside it.
                                    .zIndex(if (index == hoveredIndex) 1f else 0f),
                            ) {
                                DockTile(
                                    tab = tab,
                                    selected = currentRoute == tab.route,
                                    hovered = index == hoveredIndex,
                                    distance = if (hoveredIndex >= 0) {
                                        abs(index - hoveredIndex)
                                    } else {
                                        Int.MAX_VALUE
                                    },
                                    tileSize = metrics.tile,
                                    width = TabletDockWidth,
                                    vertical = true,
                                    popGrowth = TabletDockPopGrowth,
                                    onClick = { onNavigate(tab.route) },
                                    onLongClick = {
                                        onCollapsedChange(true)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onHover = { hoveredIndex = index },
                                    onHoverEnd = {
                                        if (hoveredIndex == index) hoveredIndex = -1
                                    },
                                )
                            }
                        }
                    }
                } else {
                    // Very short split-screen window: every tab is still
                    // reachable, the dock scrolls instead of shrinking the
                    // icons past the point of being tappable.
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(tabs, key = { it.route }) { tab ->
                            val index = tabs.indexOf(tab)
                            Box(
                                Modifier
                                    .height(metrics.cell)
                                    .fillMaxWidth()
                                    .zIndex(if (index == hoveredIndex) 1f else 0f),
                            ) {
                                DockTile(
                                    tab = tab,
                                    selected = currentRoute == tab.route,
                                    hovered = index == hoveredIndex,
                                    distance = if (hoveredIndex >= 0) {
                                        abs(index - hoveredIndex)
                                    } else {
                                        Int.MAX_VALUE
                                    },
                                    tileSize = metrics.tile,
                                    width = TabletDockWidth,
                                    vertical = true,
                                    popGrowth = TabletDockPopGrowth,
                                    onClick = { onNavigate(tab.route) },
                                    onLongClick = {
                                        onCollapsedChange(true)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onHover = { hoveredIndex = index },
                                    onHoverEnd = {
                                        if (hoveredIndex == index) hoveredIndex = -1
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // The folded dock: one glass circle at the left centre, showing the
        // current tab. Tap it and the dock unfolds; swipe it up/down to walk
        // the tabs while the shelf stays closed.
        AnimatedVisibility(
            visible = appeared && collapsed,
            enter = scaleIn(initialScale = 0.45f) + fadeIn(tween(160)),
            exit = scaleOut(targetScale = 0.45f) + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(52.dp)
                    .dockPlate(glass, CircleShape, shadow = if (glass.dark) 0.dp else 12.dp)
                    .pointerInput(tabs, selectedIndex) {
                        val threshold = 24.dp.toPx()
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                total += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(total) > threshold && tabs.isNotEmpty()) {
                                    val step = if (total > 0f) 1 else -1
                                    val next = ((selectedIndex + step) % tabs.size + tabs.size) % tabs.size
                                    onNavigate(tabs[next].route)
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove
                                    )
                                }
                                total = 0f
                            },
                            onDragCancel = { total = 0f },
                        )
                    }
                    .clickable {
                        onCollapsedChange(false)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tabs.getOrNull(selectedIndex)?.icon ?: Icons.Filled.GridView,
                    contentDescription = tr("Open navigation"),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f))
                )
            }
        }
    }
}
