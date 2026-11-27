package com.hikari.app.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hikari.app.i18n.tr
import com.hikari.app.ui.theme.GlassTokens
import com.hikari.app.ui.theme.glassSheen

/**
 * The Mac dock's shared parts: the plate material, the per-tab tints and the
 * tile itself.
 *
 * These lived inside AppNav.kt while the dock only existed at the bottom of a
 * phone. The tablet now draws the same dock vertically, and two copies of the
 * tile would drift the moment one of them gained a tweak — the exact failure
 * the app has already paid for elsewhere. Keeping the material and the tile in
 * one file means the phone and the tablet cannot disagree about what the dock
 * looks like; only the axis and the amount of lift differ ([DockTile.vertical]).
 */

/** The dock's smoked-glass plate, identical on both orientations. */
internal fun dockPlateBrush(glass: GlassTokens): Brush =
    if (glass.dark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF2A2B34).copy(alpha = 0.78f),
                Color(0xFF121319).copy(alpha = 0.90f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.94f),
                Color.White.copy(alpha = 0.78f),
            )
        )
    }

/**
 * The dock's glass plate: shadow, clip, fill, hairline and sheen in the same
 * order everywhere. A caller supplies the shape because the phone's plate is a
 * fully rounded rectangle while the tablet's is only rounded on the side that
 * faces the content.
 */
internal fun Modifier.dockPlate(
    glass: GlassTokens,
    shape: Shape,
    shadow: Dp = if (glass.dark) 0.dp else 18.dp,
): Modifier = this
    .shadow(shadow, shape)
    .clip(shape)
    .background(dockPlateBrush(glass))
    .border(
        width = 1.dp,
        color = if (glass.dark) Color.White.copy(alpha = 0.14f)
        else Color.Black.copy(alpha = 0.08f),
        shape = shape,
    )
    .then(Modifier.glassSheen(glass))

/** How much bigger than its resting size the dock's pop-out wave gets.
 *  The multiplier applies to the growth above 1.0, so 1.5 means "50% more
 *  pop" and 2.5 means "150% more pop" without turning the resting icons into
 *  a permanent bulge. Both phone orientations use the phone value. */
internal const val TabletDockPopGrowth = 1.5f
internal const val PhoneDockPopGrowth = 2.5f

/** Expand a base magnification ([base] >= 1) by [growth] for the pop-out. */
internal fun dockPopScale(base: Float, growth: Float): Float =
    1f + (base - 1f) * growth

/**
 * One app-style tile in the dock. The tile carries its own tinted glass, a
 * gloss along the top edge, a spring lift and the macOS running-app dot.
 *
 * [vertical] flips the lift from "up, out of the plate" to "right, out of the
 * plate" and uses a smaller magnification: a vertical dock only has the rail's
 * width to grow into, and a 1.95x tile at the far-left edge would spill into the
 * page content. The wave itself — the tile under the finger largest, its
 * neighbours stepping down — is the same on both axes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DockTile(
    tab: BottomTab,
    selected: Boolean,
    hovered: Boolean,
    distance: Int,
    tileSize: Dp,
    width: Dp,
    vertical: Boolean = false,
    /** Extra pop above the resting size: 1 = current, 1.5 = 50% bigger,
     *  2.5 = 150% bigger. Applied by [dockPopScale]. */
    popGrowth: Float = 1f,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onHover: () -> Unit,
    onHoverEnd: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val mouseHovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(mouseHovered) { if (mouseHovered) onHover() else onHoverEnd() }
    val (top, bottom) = dockTint(tab.route)
    // The macOS dock wave: the tile under the finger is the largest, its
    // immediate neighbours lift and grow a little, the next ones a little less.
    val baseScale = when {
        vertical -> when {
            pressed -> 1.90f
            hovered -> 2.00f
            distance == 0 -> 1.15f
            distance == 1 -> 1.50f
            distance == 2 -> 1.20f
            else -> 1f
        }

        pressed -> 1.75f
        hovered -> 1.95f
        distance == 0 -> 1.12f
        distance == 1 -> 1.45f
        distance == 2 -> 1.18f
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = dockPopScale(baseScale, popGrowth),
        animationSpec = spring(dampingRatio = 0.46f, stiffness = 440f),
        label = "dockTileScale",
    )
    val lift by animateDpAsState(
        targetValue = when {
            vertical -> when {
                pressed || hovered -> 14.dp
                distance == 0 -> 6.dp
                distance == 1 -> 5.dp
                distance == 2 -> 2.dp
                else -> 0.dp
            }

            pressed || hovered -> 14.dp
            distance == 0 -> 7.dp
            distance == 1 -> 6.dp
            distance == 2 -> 2.dp
            else -> 0.dp
        },
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 420f),
        label = "dockTileLift",
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "dockDot",
    )
    val tileShape = RoundedCornerShape(if (tileSize > 28.dp) 13.dp else 9.dp)
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            // A magnified tile must paint over its neighbours rather than
            // under the next row's plate edge; on the vertical dock the rows
            // are closer than the tiles are wide.
            .zIndex(if (hovered || pressed) 1f else 0f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(tileSize)
                .offset(
                    x = if (vertical) lift else 0.dp,
                    y = if (vertical) 0.dp else -lift,
                )
                .graphicsLayer { scaleX = scale; scaleY = scale }
                // A soft halo in the tab's own colour is the dock's "glow":
                // no blur pass, just a radial wash behind the tile.
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                top.copy(alpha = if (selected || hovered) 0.34f else 0.12f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.maxDimension * 0.95f,
                        ),
                    )
                }
                .clip(tileShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (selected || hovered) top else top.copy(alpha = 0.62f),
                            if (selected || hovered) bottom else bottom.copy(alpha = 0.55f),
                        )
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (selected || hovered) 0.34f else 0.18f),
                    tileShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Gloss: the top third catches the light, the rest falls away.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(tileSize * 0.42f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)
                        )
                    )
            )
            Icon(
                tab.icon,
                contentDescription = tr(tab.label),
                tint = Color.White.copy(alpha = if (selected || hovered) 1f else 0.9f),
                modifier = Modifier.size((tileSize * 0.56f).coerceAtLeast(10.dp)),
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(3.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(
                    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                        Color.White.copy(alpha = 0.9f)
                    } else {
                        top
                    }
                )
        )
    }
}

/** The dock tile tints: each tab keeps its own colour pair, so the dock reads
 *  as a row of app icons instead of fifteen copies of the accent. Bright top,
 *  deep bottom is the same light direction every glass panel uses. */
internal fun dockTint(route: String): Pair<Color, Color> = when (route) {
    Routes.HOME -> Color(0xFF6EA8FF) to Color(0xFF2C52D8)
    Routes.ANIME_CALENDAR -> Color(0xFFB57BFF) to Color(0xFF6B2BC4)
    Routes.SEARCH -> Color(0xFF4EDBC8) to Color(0xFF117C74)
    Routes.LIBRARY -> Color(0xFFFFB968) to Color(0xFFB86414)
    Routes.SPORTS -> Color(0xFF63E590) to Color(0xFF1B8646)
    Routes.YOUTUBE -> Color(0xFFFF7373) to Color(0xFFAD1E1E)
    Routes.MUSIC -> Color(0xFFFF83CD) to Color(0xFF9E1C6B)
    Routes.MANGA -> Color(0xFFFFD46B) to Color(0xFFB07A0A)
    Routes.IPTV -> Color(0xFF62D4FF) to Color(0xFF146A9C)
    Routes.MULTI_VIEW -> Color(0xFF9FA9FF) to Color(0xFF4132B0)
    Routes.STATS -> Color(0xFFB2E66B) to Color(0xFF4B8518)
    Routes.TELEGRAM -> Color(0xFF79BCFF) to Color(0xFF1C6BBE)
    Routes.WEBSITES -> Color(0xFF8FD8FF) to Color(0xFF286787)
    Routes.EXTENSIONS -> Color(0xFFCA97FF) to Color(0xFF662BA4)
    Routes.SETTINGS -> Color(0xFFB6BEC9) to Color(0xFF545C69)
    else -> Color(0xFF8E9AA8) to Color(0xFF3B424C)
}
