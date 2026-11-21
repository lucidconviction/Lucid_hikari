package com.hikari.app.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb

/**
 * The opening splash: a black screen carrying the build name and a loading
 * animation, drawn OVER the app while it composes its first frame.
 *
 * Why a View overlay rather than a Compose screen: the label has to be on
 * screen from the instant the system splash goes away, and a Compose splash
 * would itself need a frame to compose. This is added to the activity's content
 * root immediately after `setContent`, so there is never a frame of unstyled
 * app between the two. It is also independent of the theme, the app font and the
 * user's UI scale — a splash that changes shape per device is not a splash.
 *
 * The system splash is configured to the same black (see `themes.xml` and
 * `splash_blank`), so the hand-off from the platform's launch screen is
 * invisible: black, then black with the name.
 *
 * [HOLD_MS] is a MINIMUM, not a wait for readiness: the app keeps composing
 * underneath the whole time, and the overlay fades out on its own so a slow
 * first frame is never hidden indefinitely by it.
 */
object BootSplash {

    /** How long the name stays fully visible before the fade starts. */
    private const val HOLD_MS = 1_100L

    /** The fade itself. Short enough not to feel like a second screen. */
    private const val FADE_MS = 320L

    /** The one line every build shows, exactly as the owner asked for it. */
    private const val LABEL = "Lucid Conviction Build"

    fun show(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val density = activity.resources.displayMetrics.density
        // The loading animation wears the app's own accent, read from the
        // synchronous mirror the player uses — available before any Compose.
        val accent = runCatching {
            AccentStore.app(activity).start.toArgb()
        }.getOrDefault(Color.WHITE)

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            // Swallow taps while visible: a tap that lands on a not-yet-drawn
            // button underneath would do something the user cannot see.
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        column.addView(
            TextView(activity).apply {
                text = LABEL
                setTextColor(Color.WHITE)
                textSize = 15f
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        column.addView(
            ProgressBar(activity).apply {
                indeterminateTintList = ColorStateList.valueOf(accent)
            },
            LinearLayout.LayoutParams(
                (26 * density).toInt(),
                (26 * density).toInt(),
            ).apply { topMargin = (18 * density).toInt() },
        )

        root.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        column.animate()
            .alpha(0f)
            .setStartDelay(HOLD_MS)
            .setDuration(FADE_MS)
            .withEndAction { root.removeView(column) }
            .start()
    }
}
