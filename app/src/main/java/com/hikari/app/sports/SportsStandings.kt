package com.hikari.app.sports

/**
 * Standings parsing and display for leagues that have them.
 */
object SportsStandings {

    /** Kinds of standings tables. */
    enum class StandingsKind { REGULAR, PLAYOFF, CONFERENCE, DIVISION, GROUP, RACE, CUP }

    /** One row in a standings table. */
    data class StandingsRow(
        val team: TeamInfo,
        val played: Int,
        val won: Int,
        val lost: Int,
        val tied: Int = 0,
        val otl: Int = 0, // OT/SO losses
        val gf: Int = 0,  // goals/runs/points for
        val ga: Int = 0,  // goals/runs/points against
        val gd: Int = 0,  // goal diff
        val pts: Int = 0,
        val pct: Float = 0f,
        val streak: String? = null,
        val home: String? = null,
        val away: String? = null,
        val divisionRank: Int? = null,
        val conferenceRank: Int? = null,
        val wildCardRank: Int? = null,
        val clinched: String? = null, // "x", "y", "z", "p"
    )

    /** A standings view (one or more tables for a league). */
    data class StandingsView(
        val leagueId: String,
        val kind: StandingsKind,
        val tables: List<StandingsTable>,
        val asOf: String // e.g. "2026-10-20"
    )

    /** One table (e.g. Eastern Conference, or a single table). */
    data class StandingsTable(
        val name: String, // "Eastern Conference", "Atlantic Division", or ""
        val rows: List<StandingsRow>
    )

    /** Formats a team's record as "W-L" or "W-L-T" or "W-L-OTL". */
    fun record(row: StandingsRow): String {
        return if (row.tied > 0) "${row.won}-${row.lost}-${row.tied}"
        else if (row.otl > 0) "${row.won}-${row.lost}-${row.otl}"
        else "${row.won}-${row.lost}"
    }

    /** Formats a soccer record as "W-D-L" with pts. */
    fun soccerRecord(row: StandingsRow): String {
        return "${row.won}-${row.tied}-${row.lost} (${row.pts})"
    }

    /** The place text for My Sports row (e.g. "1st", "WC2", "5th"). */
    fun placeText(table: StandingsTable, teamId: String): String? {
        val idx = table.rows.indexOfFirst { it.team.id == teamId }
        if (idx < 0) return null
        val row = table.rows[idx]
        return when {
            row.wildCardRank != null -> "WC${row.wildCardRank}"
            row.divisionRank != null -> ordinal(row.divisionRank!!)
            row.conferenceRank != null -> ordinal(row.conferenceRank!!)
            else -> ordinal(idx + 1)
        }
    }

    private fun ordinal(n: Int): String = when (n % 100) {
        in 11..13 -> "${n}th"
        else -> when (n % 10) {
            1 -> "${n}st"
            2 -> "${n}nd"
            3 -> "${n}rd"
            else -> "${n}th"
        }
    }
}