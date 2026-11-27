package com.hikari.app.sports

import java.util.TimeZone

/**
 * A calendar date and time of day, in some time zone.
 */
internal data class CivilTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int
) {
    companion object {
        /** The civil time of [epochMs] in [zone] (a zone id, [LiveTvTimeZones.SYSTEM] or [LiveTvTimeZones.UTC]). */
        fun at(epochMs: Long, zone: String): CivilTime = atUtc(epochMs + LiveTvTimeZones.offsetMs(zone, epochMs))

        fun atUtc(epochMs: Long): CivilTime {
            val days = epochMs.floorDiv(86_400_000L)
            var rem = epochMs - days * 86_400_000L
            val hour = (rem / 3_600_000L).toInt(); rem -= hour * 3_600_000L
            val minute = (rem / 60_000L).toInt(); rem -= minute * 60_000L
            val second = (rem / 1_000L).toInt()
            // Civil date from days since the epoch (Howard Hinnant's algorithm).
            val z = days + 719_468
            val era = z.floorDiv(146_097L)
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return CivilTime(y, m, d, hour, minute, second)
        }

        /** Days since 1970-01-01 for a calendar date. */
        fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097 + doe - 719_468
        }

        /** The instant at a civil time in [zone]. */
        fun toEpochMs(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, zone: String): Long {
            val asUtc = (daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second) * 1_000L
            // The offset depends on the instant (summer time): take it at the UTC guess, then
            // once more at the result, which settles it except right at a clock change.
            val guess = asUtc - LiveTvTimeZones.offsetMs(zone, asUtc)
            return asUtc - LiveTvTimeZones.offsetMs(zone, guess)
        }
    }
}

/** The platform's time zone database, for the few places catch-up needs server-local time. */
internal object LiveTvTimeZones {
    /** Stands for the device's own zone. */
    val SYSTEM = "SYSTEM"
    /** Stands for UTC. */
    val UTC = "UTC"

    /** Whether [zoneId] names a zone the platform knows. */
    fun isKnown(zoneId: String): Boolean {
        if (zoneId == SYSTEM || zoneId == UTC) return true
        return try { TimeZone.getTimeZone(zoneId).id != "GMT" } catch (e: Exception) { false }
    }

    /** [zone]'s offset from UTC at [epochMs], in milliseconds (0 for an unknown zone). */
    fun offsetMs(zone: String, epochMs: Long): Long {
        return when (zone) {
            SYSTEM -> {
                val tz = TimeZone.getDefault()
                tz.getOffset(epochMs).toLong()
            }
            UTC -> 0L
            else -> {
                try {
                    val tz = TimeZone.getTimeZone(zone)
                    if (tz.id == "GMT" && zone != "GMT") 0L else tz.getOffset(epochMs).toLong()
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }
}