package com.hikari.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The one place Hikari's "roundy glass" card recipe lives.
 *
 * Every panel in the app (settings cards, extension cards, search field,
 * bottom bar) paints itself from these four values, so the whole UI reads as
 * one material: a translucent panel with a soft top-to-bottom falloff and a
 * 1px hairline edge, sitting on a near-black page with a faint accent glow
 * above it (drawn by AppRoot).
 *
 * Two reasons this is a single packed recipe instead of raw alphas at each call
 * site:
 *
 *  * it has to work on BOTH ends of the scale — on the dark/glass/AMOLED themes
 *    a card is a WHISPER of white over the page, while on the light theme the
 *    same card has to be a white panel with a soft grey edge;
 *  * the numbers are tuned together (fill vs. border vs. shadow); changing one
 *    without the others is what makes a glass UI look like dark grey boxes, so
 *    there is deliberately only one place to change them.
 *
 * [dark] is decided by the theme's own background luminance, which means a
 * future theme gets the right treatment automatically. Note the Dark Glass
 * theme's background is [Color.Transparent] — its luminance is 0, so it lands on
 * the dark side, which is correct: it is a glass theme.
 */
data class GlassTokens(
    /** Fill at the card's top edge. */
    val fillTop: Color,
    /** Fill at the card's bottom edge — a whisper darker. That tiny falloff is
     *  what makes a flat panel read as glass instead of paint. */
    val fillBottom: Color,
    /** The hairline that separates a card from the page. This is what the
     *  roundy-glass look is actually made of: without it, translucent cards on
     *  a near-black page turn into muddy rectangles. */
    val border: Color,
    /** True for the dark, glass and AMOLED themes. */
    val dark: Boolean,
    /** The specular top edge: a touch brighter than [border], it is the line
     *  that makes a translucent panel read as a piece of glass catching the
     *  room light rather than a flat fill with an outline. */
    val highlight: Color = border,
)

@Composable
fun rememberGlassTokens(): GlassTokens {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Built once per theme rather than once per card: this is called by every
    // panel in the app (settings cards, extensions, search field, the bottom
    // bar), i.e. hundreds of times per screen.
    return remember(dark) {
        if (dark) {
            GlassTokens(
                fillTop = Color.White.copy(alpha = 0.11f),
                fillBottom = Color.White.copy(alpha = 0.038f),
                border = Color.White.copy(alpha = 0.14f),
                dark = true,
                highlight = Color.White.copy(alpha = 0.22f),
            )
        } else {
            GlassTokens(
                fillTop = Color.White,
                fillBottom = Color.White,
                border = Color.Black.copy(alpha = 0.08f),
                dark = false,
                highlight = Color.Black.copy(alpha = 0.05f),
            )
        }
    }
}

/**
 * The specular sheen every glass panel carries: a soft diagonal light across
 * the top-left corner plus the 1px bright edge. Drawn BELOW the panel's
 * content, so text and artwork stay crisp; it is only the material that catches
 * the light.
 *
 * On the light theme the panel is already opaque white, so the sheen is left
 * off (a white wash on white would only dull the border).
 */
fun Modifier.glassSheen(tokens: GlassTokens): Modifier =
    if (!tokens.dark) this
    else this.drawWithContent {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.085f),
                    Color.White.copy(alpha = 0.018f),
                    Color.Transparent,
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.85f, size.height * 0.55f),
            ),
        )
        drawLine(
            color = tokens.highlight.copy(alpha = 0.55f),
            start = Offset(0f, 0.5f),
            end = Offset(size.width, 0.5f),
            strokeWidth = 1f,
        )
        drawContent()
    }
