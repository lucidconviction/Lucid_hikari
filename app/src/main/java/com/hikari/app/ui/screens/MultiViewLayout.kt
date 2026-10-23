package com.hikari.app.ui.screens

/** F4 — Multi-view window layouts (13 standard grids). */
enum class MultiViewLayout(val cols: Int, val rows: Int, val maxWindows: Int) {
    GRID_2_VERTICAL(1, 2, 2),
    GRID_3_VERTICAL(1, 3, 3),
    GRID_4_VERTICAL(1, 4, 4),
    GRID_5_VERTICAL(1, 5, 5),
    GRID_6_VERTICAL(1, 6, 6),
    GRID_2_HORIZONTAL(2, 1, 2),
    GRID_3_HORIZONTAL(3, 1, 3),
    GRID_4_HORIZONTAL(4, 1, 4),
    GRID_2x2(2, 2, 4),
    GRID_2x3(2, 3, 6),
    GRID_3x2(3, 2, 6),
    GRID_2x4(2, 4, 8),
    GRID_3x3(3, 3, 9),
    ;
    companion object {
        val DEFAULTS = mapOf(
            "phone_portrait" to GRID_2x3,
            "phone_landscape" to GRID_3x2,
            "tablet" to GRID_3x2,
        )
        fun fromKey(key: String?): MultiViewLayout = entries.firstOrNull { it.name == key } ?: GRID_2x3
    }
}
