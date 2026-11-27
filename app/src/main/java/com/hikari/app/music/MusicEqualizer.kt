package com.hikari.app.music

import android.media.audiofx.Equalizer
import com.hikari.app.data.Logs

/**
 * The Music section's equalizer, attached to the ExoPlayer audio session.
 *
 * Presets are expressed as a fraction of the device's own band-level range so
 * a phone with a five-band EQ and a TV with ten bands both get a sensible
 * curve. Failure is never fatal: a device without the effect simply plays
 * untouched.
 */
object MusicEqualizer {

    private var equalizer: Equalizer? = null
    private var sessionId = 0
    private var preset: MusicEqualizerPreset = MusicEqualizerPreset.OFF

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == sessionId && equalizer != null) return
        release()
        sessionId = audioSessionId
        equalizer = runCatching {
            Equalizer(0, audioSessionId).apply { enabled = false }
        }.onFailure {
            Logs.log("MusicEQ", "equalizer unavailable: ${it.message}")
        }.getOrNull()
        apply(preset)
    }

    @Synchronized
    fun apply(next: MusicEqualizerPreset) {
        preset = next
        val eq = equalizer ?: return
        runCatching {
            if (next == MusicEqualizerPreset.OFF) {
                eq.enabled = false
                return
            }
            eq.enabled = false
            val range = eq.bandLevelRange
            val min = range.getOrNull(0)?.toInt() ?: -1500
            val max = range.getOrNull(1)?.toInt() ?: 1500
            val maxGain = if (max > 0) max.toFloat() else 1000f
            val shape = when (next) {
                MusicEqualizerPreset.FLAT -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
                MusicEqualizerPreset.BASS -> floatArrayOf(0.85f, 0.60f, 0.10f, -0.20f, -0.30f)
                MusicEqualizerPreset.VOCAL -> floatArrayOf(-0.45f, -0.20f, 0.55f, 0.75f, 0.25f)
                MusicEqualizerPreset.ROCK -> floatArrayOf(0.65f, 0.35f, -0.10f, 0.35f, 0.55f)
                MusicEqualizerPreset.OFF -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
            }
            val bands = eq.numberOfBands.toInt().coerceAtLeast(1)
            for (band in 0 until bands) {
                val fraction = when {
                    bands == 1 -> shape[2]
                    else -> {
                        val pos = band.toFloat() / (bands - 1).toFloat()
                        val x = pos * (shape.size - 1)
                        val low = x.toInt().coerceIn(0, shape.size - 1)
                        val high = (low + 1).coerceAtMost(shape.size - 1)
                        val t = x - low
                        shape[low] * (1f - t) + shape[high] * t
                    }
                }
                val level = (fraction * maxGain).toInt().coerceIn(min, max)
                runCatching { eq.setBandLevel(band.toShort(), level.toShort()) }
            }
            eq.enabled = true
        }.onFailure {
            Logs.log("MusicEQ", "preset ${next.name} failed: ${it.message}")
        }
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.enabled = false }
        runCatching { equalizer?.release() }
        equalizer = null
        sessionId = 0
    }
}
