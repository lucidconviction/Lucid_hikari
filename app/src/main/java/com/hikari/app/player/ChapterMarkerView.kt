package com.hikari.app.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * The thin strip above the seek bar that marks intro / recap / preview / credits
 * segments. Non-interactive on purpose: the time bar underneath keeps every
 * touch, drag and accessibility action, and the view is only ever a picture.
 *
 * Marks are fractions of the duration, so they stay correct when the bar is
 * resized on rotation. The tick sits above the time bar's 5dp track (the
 * controller gives this view the same 22dp height as the bar) and never covers
 * the playhead.
 */
class ChapterMarkerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Mark(val start: Float, val end: Float, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var marks: List<Mark> = emptyList()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setMarks(value: List<Mark>) {
        marks = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (marks.isEmpty() || width <= 0) return
        val top = 1f * density
        val height = 4f * density
        val radius = 2f * density
        for (mark in marks) {
            val left = mark.start.coerceIn(0f, 1f) * width
            val right = mark.end.coerceIn(0f, 1f) * width
            if (right <= left) continue
            paint.color = mark.color
            canvas.drawRoundRect(left, top, right, top + height, radius, radius, paint)
        }
    }
}
