package com.hikari.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's one spacing scale.
 *
 * Why this file exists: the screens grew a mix of 4/6/8/10/12/14/16/18/20/22/24
 * dp paddings, which is what makes a layout read as hand-tuned instead of
 * designed. New and migrated screens use these names, so a gap that means the
 * same thing is the same number everywhere.
 *
 * This is additive only: nothing in the app reads it yet, and no existing
 * value changes because of it. Migration happens screen by screen (see
 * docs/PLAN_VYSE_ADAPTATION.md).
 */
object HikariSpacing {
    /** Hairline gaps: icon to label, badge internals. */
    val xxs = 2.dp

    /** Tight gaps: chip internals, title to subtitle. */
    val xs = 4.dp

    /** The default gap between siblings in a row. */
    val s = 8.dp

    /** Card internals and the gap between cards. */
    val m = 12.dp

    /** The page gutter on a phone. */
    val l = 16.dp

    /** Between page sections, and the page gutter on a tablet. */
    val xl = 24.dp

    /** Above a major page block. */
    val xxl = 32.dp

    /** The largest rhythm step: hero breathing room on a television. */
    val xxxl = 48.dp

    /** Horizontal page padding: phone. Tablets/TV add their own inset. */
    val page = 16.dp

    /** Horizontal page padding on a tablet — the wider gutter the left rail
     *  leaves room for. */
    val pageTablet = 32.dp
}

/**
 * Corner radii, one per kind of surface.
 *
 * The app's glass recipe (see [com.hikari.app.ui.theme.rememberGlassTokens])
 * draws hairlines and fills; the radius is what tells a chip from a card from a
 * sheet. Keeping the four values here stops a screen from inventing a fifth.
 */
object HikariRadii {
    /** Chips, badges, small pills. */
    val chip = 8.dp

    /** Posters, panels, list cards. */
    val card = 12.dp

    /** Bottom sheets and large dialogs. */
    val sheet = 16.dp

    /** Full dialogs. */
    val dialog = 22.dp

    /** Pills: taskbar tabs, filter chips, the "View Details" button. */
    val pill = 28.dp
}
