package com.hikari.app.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device/window classification is pure arithmetic, so it is pinned here:
 * every screen's layout shape depends on these two answers, and a silent
 * off-by-one at the 600dp breakpoint is the difference between a tablet rail
 * and a phone taskbar.
 */
class LayoutClassTest {

    @Test
    fun televisionWinsOverWidth() {
        // A TV box can report any width; it must never become a touch tablet.
        assertEquals(DeviceClass.TV, DeviceClass.from(isTv = true, smallestWidthDp = 320))
        assertEquals(DeviceClass.TV, DeviceClass.from(isTv = true, smallestWidthDp = 1280))
    }

    @Test
    fun tabletStartsAtSixHundredDp() {
        assertEquals(DeviceClass.PHONE, DeviceClass.from(isTv = false, smallestWidthDp = 599))
        assertEquals(DeviceClass.TABLET, DeviceClass.from(isTv = false, smallestWidthDp = 600))
        assertEquals(DeviceClass.TABLET, DeviceClass.from(isTv = false, smallestWidthDp = 1024))
    }

    @Test
    fun windowSizesFollowMaterialBreakpoints() {
        assertEquals(WindowSize.COMPACT, windowSizeFor(599))
        assertEquals(WindowSize.MEDIUM, windowSizeFor(600))
        assertEquals(WindowSize.MEDIUM, windowSizeFor(839))
        assertEquals(WindowSize.EXPANDED, windowSizeFor(840))
    }

    @Test
    fun onlyTouchLayoutsWithoutRailArePhoneLike() {
        assertFalse(DeviceClass.PHONE.hasRail)
        assertTrue(DeviceClass.PHONE.isTouch)
        assertTrue(DeviceClass.TABLET.hasRail)
        assertTrue(DeviceClass.TABLET.isTouch)
        assertTrue(DeviceClass.TV.hasRail)
        assertFalse(DeviceClass.TV.isTouch)
    }
}
