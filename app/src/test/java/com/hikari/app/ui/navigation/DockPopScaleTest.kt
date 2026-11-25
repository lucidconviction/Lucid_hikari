package com.hikari.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dock's pop-out size is a pure multiplier, so the promised tablet and
 * portrait boosts stay checkable without a screen.
 */
class DockPopScaleTest {

    @Test
    fun tabletPopIsFiftyPercentBigger() {
        // Base hover scale on the vertical tablet dock is 2.00.
        assertEquals(2.50f, dockPopScale(2.00f, TabletDockPopGrowth), 0.0001f)
        // The pressed and neighbour steps grow by the same proportion.
        assertEquals(2.35f, dockPopScale(1.90f, TabletDockPopGrowth), 0.0001f)
        assertEquals(1.75f, dockPopScale(1.50f, TabletDockPopGrowth), 0.0001f)
    }

    @Test
    fun phonePopIsOneHundredFiftyPercentBigger() {
        // Base hover scale on the bottom dock is 1.95.
        assertEquals(3.375f, dockPopScale(1.95f, PhoneDockPopGrowth), 0.0001f)
        assertEquals(2.875f, dockPopScale(1.75f, PhoneDockPopGrowth), 0.0001f)
    }

    @Test
    fun restingTilesNeverGrow() {
        assertEquals(1f, dockPopScale(1f, TabletDockPopGrowth), 0.0001f)
        assertEquals(1f, dockPopScale(1f, PhoneDockPopGrowth), 0.0001f)
    }

    @Test
    fun landscapePhoneGetsTheSameBigPop() {
        assertEquals(3.375f, dockPopScale(1.95f, PhoneDockPopGrowth), 0.0001f)
    }
}
