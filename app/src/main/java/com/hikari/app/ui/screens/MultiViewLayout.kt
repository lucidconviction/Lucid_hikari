package com.hikari.app.ui.screens

/**
 * F4 — Multi-view window layouts, ported from Nuvio's MultiWindowLayouts: 47
 * named slot arrangements (1..9 windows). [SlotPos] describes one window's
 * position in the layout's row/col grid; spans allow asymmetric layouts
 * (1+2, 2+1v, 1-2-1, …) that plain LazyGrid columns can't express.
 */
data class SlotPos(val index: Int, val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)

enum class MultiViewLayout(val label: String) {
    V1_FULL("1"),
    V2_SPLIT("1\u00D72"),
    V2_STACK("2\u00D71"),
    V3_STACK("3 vert"),
    V3_TOP1_BOT2("1+2"),
    V3_LEFT2_RIGHT1("2+1"),
    V3_HORIZ("1\u00D73"),
    V3_TOP2_BOT1("2+1v"),
    V3_LEFT1_RIGHT2("1+2h"),
    V4_GRID("2\u00D72"),
    V4_1_2_1("1-2-1"),
    V4_HORIZ("1\u00D74"),
    V4_VERT("4 vert"),
    V4_LEFT1_RIGHT3("1+3"),
    V4_LEFT3_RIGHT1("3+1"),
    V4_2_1_1("2-1-1"),
    V4_1_1_2("1-1-2"),
    V5_GRID4_BOT1("4+1"),
    V5_TOP1_BOT4("1+4"),
    V5_LEFT3_RIGHT2("3+2"),
    V5_HORIZ("1\u00D75"),
    V5_TOP2_BOT3("2+3"),
    V5_TOP1_GRID4("1+2\u00D72"),
    V5_3LEFT_2RIGHT("3+2v"),
    V6_3x2("3\u00D72"),
    V6_2x3("2\u00D73"),
    V6_1_2_2_1("1-2-2-1"),
    V6_HORIZ("1\u00D76"),
    V6_VERT("6 vert"),
    V6_GRID4_2ROW("4+2"),
    V6_3ROW_2ROW("3+3"),
    V7_1_3_3("1-3-3"),
    V7_TOP1_BOT6("1+6"),
    V7_3x2_PLUS_1("3\u00D72+1"),
    V7_2x3_PLUS_1("2\u00D73+1"),
    V7_LEFT3_GRID4("1+3+3"),
    V7_ASYMM("7 asym"),
    V8_GRID4x2("4\u00D72"),
    V8_2x4("2\u00D74"),
    V8_1_3_3_1("1-3-3-1"),
    V8_3x2_PLUS_2("3\u00D72+2"),
    V8_2x3_PLUS_1x2("2\u00D73+1x2"),
    V8_GRID4_2ROW_PLUS_1("4+2+1"),
    V9_GRID3x3("3\u00D73"),
    V9_GRID3x3_CENTER("3\u00D73+"),
    V9_3x2_PLUS_3x1("3\u00D72+3"),
    V9_2x3_PLUS_3x1("2\u00D73+3"),
    ;

    /** Grid width in layout columns this layout can span (for the old
     *  fixed-grid callers; Nuvio layouts are span-based, not uniform). */
    val cols: Int
        get() = calculateSlots(Int.MAX_VALUE).maxOfOrNull { it.col + it.colSpan } ?: 1

    /** Grid height in layout rows this layout can span. */
    val rows: Int
        get() = calculateSlots(Int.MAX_VALUE).maxOfOrNull { it.row + it.rowSpan } ?: 1

    /** How many windows this layout can host (its full slot count). */
    val maxWindows: Int
        get() = calculateSlots(Int.MAX_VALUE).size

    companion object {
        /** Old default was GRID_2x3; its Nuvio equivalent is V6_2x3. */
        val DEFAULT: MultiViewLayout = V6_2x3

        /** Resolve a stored layout key (enum name) to a layout. */
        fun fromKey(key: String?): MultiViewLayout =
            entries.firstOrNull { it.name == key } ?: DEFAULT

        /** Like [fromKey] but also understands the legacy 13-grid names
         *  (GRID_2x2, GRID_2x3, …) persisted by the old multi-view screen,
         *  so a saved layout survives the port. */
        fun fromLegacyKey(key: String?): MultiViewLayout =
            when (key) {
                "GRID_2_VERTICAL" -> V2_STACK
                "GRID_3_VERTICAL" -> V3_STACK
                "GRID_4_VERTICAL" -> V4_VERT
                "GRID_5_VERTICAL" -> V5_TOP1_BOT4
                "GRID_6_VERTICAL" -> V6_VERT
                "GRID_2_HORIZONTAL" -> V2_SPLIT
                "GRID_3_HORIZONTAL" -> V3_HORIZ
                "GRID_4_HORIZONTAL" -> V4_HORIZ
                "GRID_2x2" -> V4_GRID
                "GRID_2x3" -> V6_2x3
                "GRID_3x2" -> V6_3x2
                "GRID_2x4" -> V8_2x4
                "GRID_3x3" -> V9_GRID3x3
                else -> fromKey(key)
            }
    }
}

/** Layouts valid for a given window count and orientation (Nuvio's rules). */
fun getValidLayouts(count: Int, isPortrait: Boolean, isTablet: Boolean = false): List<MultiViewLayout> {
    return when (count) {
        1 -> listOf(MultiViewLayout.V1_FULL)
        2 -> listOf(
            MultiViewLayout.V2_SPLIT,
            MultiViewLayout.V2_STACK,
        )
        3 -> buildList {
            if (isPortrait || isTablet) {
                add(MultiViewLayout.V3_TOP1_BOT2)
                add(MultiViewLayout.V3_TOP2_BOT1)
                add(MultiViewLayout.V3_STACK)
            }
            if (!isPortrait || isTablet) {
                add(MultiViewLayout.V3_HORIZ)
                add(MultiViewLayout.V3_LEFT1_RIGHT2)
                add(MultiViewLayout.V3_LEFT2_RIGHT1)
            }
        }
        4 -> buildList {
            add(MultiViewLayout.V4_GRID)
            if (isPortrait || isTablet) {
                add(MultiViewLayout.V4_VERT)
                add(MultiViewLayout.V4_1_2_1)
                add(MultiViewLayout.V4_2_1_1)
                add(MultiViewLayout.V4_1_1_2)
                add(MultiViewLayout.V4_LEFT3_RIGHT1)
            }
            if (!isPortrait || isTablet) {
                add(MultiViewLayout.V4_HORIZ)
                add(MultiViewLayout.V4_LEFT1_RIGHT3)
            }
        }
        5 -> buildList {
            add(MultiViewLayout.V5_GRID4_BOT1)
            if (isPortrait || isTablet) {
                add(MultiViewLayout.V5_TOP1_BOT4)
                add(MultiViewLayout.V5_TOP1_GRID4)
                add(MultiViewLayout.V5_TOP2_BOT3)
            }
            if (!isPortrait || isTablet) {
                add(MultiViewLayout.V5_HORIZ)
                add(MultiViewLayout.V5_LEFT3_RIGHT2)
                add(MultiViewLayout.V5_3LEFT_2RIGHT)
            }
        }
        6 -> buildList {
            if (isPortrait || isTablet) {
                add(MultiViewLayout.V6_3x2)
                add(MultiViewLayout.V6_1_2_2_1)
                add(MultiViewLayout.V6_VERT)
                add(MultiViewLayout.V6_3ROW_2ROW)
            }
            if (!isPortrait || isTablet) {
                add(MultiViewLayout.V6_2x3)
                add(MultiViewLayout.V6_HORIZ)
                add(MultiViewLayout.V6_GRID4_2ROW)
            }
        }
        7 -> buildList {
            if (isPortrait || isTablet) {
                add(MultiViewLayout.V7_1_3_3)
                add(MultiViewLayout.V7_TOP1_BOT6)
                add(MultiViewLayout.V7_LEFT3_GRID4)
                add(MultiViewLayout.V7_ASYMM)
            }
            if (!isPortrait || isTablet) {
                add(MultiViewLayout.V7_3x2_PLUS_1)
                add(MultiViewLayout.V7_2x3_PLUS_1)
            }
        }
        8 -> buildList {
            if (isPortrait || isTablet) {
                add(MultiViewLayout.V8_GRID4x2)
                add(MultiViewLayout.V8_1_3_3_1)
                add(MultiViewLayout.V8_GRID4_2ROW_PLUS_1)
            }
            if (!isPortrait || isTablet) {
                add(MultiViewLayout.V8_2x4)
                add(MultiViewLayout.V8_3x2_PLUS_2)
                add(MultiViewLayout.V8_2x3_PLUS_1x2)
            }
        }
        9 -> buildList {
            add(MultiViewLayout.V9_GRID3x3)
            if (isTablet) {
                add(MultiViewLayout.V9_GRID3x3_CENTER)
                add(MultiViewLayout.V9_3x2_PLUS_3x1)
                add(MultiViewLayout.V9_2x3_PLUS_3x1)
            }
        }
        else -> emptyList()
    }
}

fun defaultLayout(count: Int, isPortrait: Boolean, isTablet: Boolean = false): MultiViewLayout =
    getValidLayouts(count, isPortrait, isTablet).firstOrNull() ?: MultiViewLayout.V1_FULL

/**
 * The [count] window slots for this layout, each with its row/col position
 * and span. [count] is clamped to the layout's own slot count.
 */
fun MultiViewLayout.calculateSlots(count: Int): List<SlotPos> {
    return when (this) {
        MultiViewLayout.V1_FULL -> listOf(
            SlotPos(0, 0, 0, 1, 1),
        )
        MultiViewLayout.V2_SPLIT -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
        )
        MultiViewLayout.V2_STACK -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1),
        )
        MultiViewLayout.V3_STACK -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1), SlotPos(2, 2, 0, 1, 1),
        )
        MultiViewLayout.V3_TOP1_BOT2 -> listOf(
            SlotPos(0, 0, 0, 1, 2), SlotPos(1, 1, 0, 1, 1), SlotPos(2, 1, 1, 1, 1),
        )
        MultiViewLayout.V3_LEFT2_RIGHT1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1), SlotPos(2, 0, 1, 2, 1),
        )
        MultiViewLayout.V3_HORIZ -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
        )
        MultiViewLayout.V3_TOP2_BOT1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 1, 0, 1, 2),
        )
        MultiViewLayout.V3_LEFT1_RIGHT2 -> listOf(
            SlotPos(0, 0, 0, 2, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 1, 1, 1, 1),
        )
        MultiViewLayout.V4_GRID -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
        )
        MultiViewLayout.V4_1_2_1 -> listOf(
            SlotPos(0, 0, 0, 1, 2), SlotPos(1, 1, 0, 1, 1),
            SlotPos(2, 1, 1, 1, 1), SlotPos(3, 2, 0, 1, 2),
        )
        MultiViewLayout.V4_HORIZ -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 0, 2, 1, 1), SlotPos(3, 0, 3, 1, 1),
        )
        MultiViewLayout.V4_VERT -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1),
            SlotPos(2, 2, 0, 1, 1), SlotPos(3, 3, 0, 1, 1),
        )
        MultiViewLayout.V4_LEFT1_RIGHT3 -> listOf(
            SlotPos(0, 0, 0, 3, 1),
            SlotPos(1, 0, 1, 1, 1), SlotPos(2, 1, 1, 1, 1), SlotPos(3, 2, 1, 1, 1),
        )
        MultiViewLayout.V4_LEFT3_RIGHT1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1), SlotPos(2, 2, 0, 1, 1),
            SlotPos(3, 0, 1, 3, 1),
        )
        MultiViewLayout.V4_2_1_1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 2), SlotPos(3, 2, 0, 1, 2),
        )
        MultiViewLayout.V4_1_1_2 -> listOf(
            SlotPos(0, 0, 0, 1, 2), SlotPos(1, 1, 0, 1, 2),
            SlotPos(2, 2, 0, 1, 1), SlotPos(3, 2, 1, 1, 1),
        )
        MultiViewLayout.V5_GRID4_BOT1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 2),
        )
        MultiViewLayout.V5_TOP1_BOT4 -> listOf(
            SlotPos(0, 0, 0, 1, 2), SlotPos(1, 1, 0, 1, 1), SlotPos(2, 1, 1, 1, 1),
            SlotPos(3, 2, 0, 1, 1), SlotPos(4, 2, 1, 1, 1),
        )
        MultiViewLayout.V5_LEFT3_RIGHT2 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1), SlotPos(2, 2, 0, 1, 1),
            SlotPos(3, 0, 1, 1, 1), SlotPos(4, 1, 1, 1, 1),
        )
        MultiViewLayout.V5_HORIZ -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 0, 2, 1, 1), SlotPos(3, 0, 3, 1, 1), SlotPos(4, 0, 4, 1, 1),
        )
        MultiViewLayout.V5_TOP2_BOT3 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1), SlotPos(4, 1, 2, 1, 1),
        )
        MultiViewLayout.V5_TOP1_GRID4 -> listOf(
            SlotPos(0, 0, 0, 1, 2),
            SlotPos(1, 1, 0, 1, 1), SlotPos(2, 1, 1, 1, 1),
            SlotPos(3, 2, 0, 1, 1), SlotPos(4, 2, 1, 1, 1),
        )
        MultiViewLayout.V5_3LEFT_2RIGHT -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1), SlotPos(2, 2, 0, 1, 1),
            SlotPos(3, 0, 1, 1, 1), SlotPos(4, 1, 1, 1, 1),
        )
        MultiViewLayout.V6_3x2 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1),
        )
        MultiViewLayout.V6_2x3 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 0, 1, 1), SlotPos(4, 1, 1, 1, 1), SlotPos(5, 1, 2, 1, 1),
        )
        MultiViewLayout.V6_1_2_2_1 -> listOf(
            SlotPos(0, 0, 0, 1, 2),
            SlotPos(1, 1, 0, 1, 1), SlotPos(2, 1, 1, 1, 1),
            SlotPos(3, 2, 0, 1, 1), SlotPos(4, 2, 1, 1, 1),
            SlotPos(5, 3, 0, 1, 2),
        )
        MultiViewLayout.V6_HORIZ -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 0, 3, 1, 1), SlotPos(4, 0, 4, 1, 1), SlotPos(5, 0, 5, 1, 1),
        )
        MultiViewLayout.V6_VERT -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 1, 0, 1, 1),
            SlotPos(2, 2, 0, 1, 1), SlotPos(3, 3, 0, 1, 1),
            SlotPos(4, 4, 0, 1, 1), SlotPos(5, 5, 0, 1, 1),
        )
        MultiViewLayout.V6_GRID4_2ROW -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1),
        )
        MultiViewLayout.V6_3ROW_2ROW -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1),
        )
        MultiViewLayout.V7_1_3_3 -> listOf(
            SlotPos(0, 0, 0, 1, 3),
            SlotPos(1, 1, 0, 1, 1), SlotPos(2, 1, 1, 1, 1), SlotPos(3, 1, 2, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1), SlotPos(6, 2, 2, 1, 1),
        )
        MultiViewLayout.V7_TOP1_BOT6 -> listOf(
            SlotPos(0, 0, 0, 1, 2),
            SlotPos(1, 1, 0, 1, 1), SlotPos(2, 1, 1, 1, 1),
            SlotPos(3, 2, 0, 1, 1), SlotPos(4, 2, 1, 1, 1),
            SlotPos(5, 3, 0, 1, 1), SlotPos(6, 3, 1, 1, 1),
        )
        MultiViewLayout.V7_3x2_PLUS_1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 0, 1, 1), SlotPos(4, 1, 1, 1, 1), SlotPos(5, 1, 2, 1, 1),
            SlotPos(6, 2, 0, 1, 3),
        )
        MultiViewLayout.V7_2x3_PLUS_1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1),
            SlotPos(6, 3, 0, 1, 2),
        )
        MultiViewLayout.V7_LEFT3_GRID4 -> listOf(
            SlotPos(0, 0, 0, 3, 1),
            SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 1, 1, 1), SlotPos(4, 1, 2, 1, 1),
            SlotPos(5, 2, 1, 1, 1), SlotPos(6, 2, 2, 1, 1),
        )
        MultiViewLayout.V7_ASYMM -> listOf(
            SlotPos(0, 0, 0, 2, 2),
            SlotPos(1, 0, 2, 1, 1), SlotPos(2, 1, 2, 1, 1),
            SlotPos(3, 2, 0, 1, 1), SlotPos(4, 2, 1, 1, 1), SlotPos(5, 2, 2, 1, 1),
        )
        MultiViewLayout.V8_GRID4x2 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1),
            SlotPos(6, 3, 0, 1, 1), SlotPos(7, 3, 1, 1, 1),
        )
        MultiViewLayout.V8_2x4 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1), SlotPos(3, 0, 3, 1, 1),
            SlotPos(4, 1, 0, 1, 1), SlotPos(5, 1, 1, 1, 1), SlotPos(6, 1, 2, 1, 1), SlotPos(7, 1, 3, 1, 1),
        )
        MultiViewLayout.V8_1_3_3_1 -> listOf(
            SlotPos(0, 0, 0, 1, 3),
            SlotPos(1, 1, 0, 1, 1), SlotPos(2, 1, 1, 1, 1), SlotPos(3, 1, 2, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1), SlotPos(6, 2, 2, 1, 1),
            SlotPos(7, 3, 0, 1, 3),
        )
        MultiViewLayout.V8_3x2_PLUS_2 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 0, 1, 1), SlotPos(4, 1, 1, 1, 1), SlotPos(5, 1, 2, 1, 1),
            SlotPos(6, 2, 0, 1, 1), SlotPos(7, 2, 1, 1, 1),
        )
        MultiViewLayout.V8_2x3_PLUS_1x2 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 0, 1, 1), SlotPos(4, 1, 1, 1, 1), SlotPos(5, 1, 2, 1, 1),
            SlotPos(6, 2, 0, 1, 3),
        )
        MultiViewLayout.V8_GRID4_2ROW_PLUS_1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1),
            SlotPos(6, 3, 0, 1, 1), SlotPos(7, 3, 1, 1, 1),
        )
        MultiViewLayout.V9_GRID3x3 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 0, 1, 1), SlotPos(4, 1, 1, 1, 1), SlotPos(5, 1, 2, 1, 1),
            SlotPos(6, 2, 0, 1, 1), SlotPos(7, 2, 1, 1, 1), SlotPos(8, 2, 2, 1, 1),
        )
        MultiViewLayout.V9_GRID3x3_CENTER -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 0, 1, 1), SlotPos(4, 1, 1, 2, 2), SlotPos(5, 1, 2, 1, 1),
            SlotPos(6, 2, 0, 1, 1), SlotPos(7, 2, 1, 1, 1), SlotPos(8, 2, 2, 1, 1),
        )
        MultiViewLayout.V9_3x2_PLUS_3x1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1), SlotPos(2, 0, 2, 1, 1),
            SlotPos(3, 1, 0, 1, 1), SlotPos(4, 1, 1, 1, 1), SlotPos(5, 1, 2, 1, 1),
            SlotPos(6, 2, 0, 1, 1), SlotPos(7, 2, 1, 1, 1), SlotPos(8, 2, 2, 1, 1),
        )
        MultiViewLayout.V9_2x3_PLUS_3x1 -> listOf(
            SlotPos(0, 0, 0, 1, 1), SlotPos(1, 0, 1, 1, 1),
            SlotPos(2, 1, 0, 1, 1), SlotPos(3, 1, 1, 1, 1),
            SlotPos(4, 2, 0, 1, 1), SlotPos(5, 2, 1, 1, 1),
            SlotPos(6, 3, 0, 1, 1), SlotPos(7, 3, 1, 1, 1), SlotPos(8, 3, 2, 1, 1),
        )
    }.take(count)
}
