package com.hikari.app.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vertical dock's sizing is the one part of the tablet layout that can be
 * checked without a screen: a 15-tab dock has to fit the shortest tablet window
 * the app supports while keeping every icon tappable.
 */
class TabletDockMetricsTest {

    @Test
    fun fifteenTabsFitASixHundredDpWindow() {
        val metrics = verticalDockMetrics(tabCount = 15, availableHeight = 600.dp)
        assertTrue(
            "plate is taller than the window: ${metrics.plateHeight}",
            metrics.plateHeight <= 600.dp,
        )
        assertTrue("cell too small: ${metrics.cell}", metrics.cell >= 34.dp)
        assertTrue("tile too small: ${metrics.tile}", metrics.tile >= 20.dp)
    }

    @Test
    fun threeTabsUseTheRestingCellAndStayCentred() {
        val metrics = verticalDockMetrics(tabCount = 3, availableHeight = 600.dp)
        assertEquals(48.dp, metrics.cell)
        assertEquals(32.dp, metrics.tile)
        assertTrue(metrics.plateHeight < 600.dp)
    }

    @Test
    fun everyTabCountKeepsATappableTile() {
        for (count in 1..15) {
            val metrics = verticalDockMetrics(count, 600.dp)
            assertTrue("count=$count tile=${metrics.tile}", metrics.tile >= 20.dp)
            assertTrue("count=$count plate=${metrics.plateHeight}", metrics.plateHeight <= 600.dp)
        }
    }

    @Test
    fun anEmptyDockStillHasUsableNumbers() {
        val metrics = verticalDockMetrics(tabCount = 0, availableHeight = 600.dp)
        assertTrue(metrics.cell > 0.dp)
        assertTrue(metrics.tile > 0.dp)
        assertTrue(metrics.plateHeight > 0.dp)
    }
}
