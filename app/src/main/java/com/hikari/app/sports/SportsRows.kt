package com.hikari.app.sports

/**
 * Builds the row structure for My Sports and All Events tabs.
 */
object SportsRows {

    data class Row(
        val key: String,           // "team:league:teamId" or "league:leagueId" or "word:name"
        val title: String,         // Display title
        val league: League?,       // The league (for color/logo)
        val team: FollowedTeam?,   // The followed team (if any)
        val events: List<SportsEvent>,
        val startIndex: Int = 0,   // For horizontal scroll position
    )

    data class Page(
        val rows: List<Row>,
    )

    /**
     * Builds My Sports rows from followed teams/leagues/words.
     * [isMine] is a predicate that says whether an event belongs to this row.
     */
    fun build(
        events: List<SportsEvent>,
        follows: SportsFollows,
        now: Long,
        isMine: (SportsEvent, SportsFollows) -> Boolean
    ): Page {
        val byTeam = mutableMapOf<String, MutableList<SportsEvent>>()
        val byLeague = mutableMapOf<String, MutableList<SportsEvent>>()
        val byWord = mutableMapOf<String, MutableList<SportsEvent>>()

        // Bucket events
        events.forEach { ev ->
            // By followed team
            follows.teams.forEach { ft ->
                val awayTeam = ev.away?.team
                val homeTeam = ev.home?.team
                if ((awayTeam?.id == ft.teamId && awayTeam.leagueId == ft.leagueId) ||
                    (homeTeam?.id == ft.teamId && homeTeam.leagueId == ft.leagueId)) {
                    byTeam.getOrPut("t:${ft.leagueId}:${ft.teamId}") { mutableListOf() }.add(ev)
                } else if (SportsCatalog.teamFamily(awayTeam?.leagueId ?: "") == SportsCatalog.teamFamily(ft.leagueId) &&
                           awayTeam?.id == ft.teamId ||
                           SportsCatalog.teamFamily(homeTeam?.leagueId ?: "") == SportsCatalog.teamFamily(ft.leagueId) &&
                           homeTeam?.id == ft.teamId) {
                    byTeam.getOrPut("t:${ft.leagueId}:${ft.teamId}") { mutableListOf() }.add(ev)
                }
            }
            // By followed league
            follows.leagues.forEach { lid ->
                if (ev.leagueId == lid) {
                    byLeague.getOrPut("l:$lid") { mutableListOf() }.add(ev)
                }
            }
            // By followed words
            follows.words.forEach { word ->
                val normWord = SportsCatalog.norm(word)
                val normTitle = SportsCatalog.norm(ev.title)
                if (normTitle.contains(normWord)) {
                    byWord.getOrPut("w:$word") { mutableListOf() }.add(ev)
                }
            }
        }

        // Build rows in the user's custom order
        val rows = mutableListOf<Row>()
        val usedKeys = mutableSetOf<String>()

        follows.order.forEach { key ->
            if (key.startsWith("t:")) {
                val parts = key.substring(2).split(':')
                if (parts.size == 2) {
                    val (lid, tid) = parts[0] to parts[1]
                    val ft = follows.teams.firstOrNull { it.leagueId == lid && it.teamId == tid }
                    ft?.let {
                        val evs = byTeam["t:$lid:$tid"] ?: emptyList()
                        if (evs.isNotEmpty()) {
                            val league = SportsCatalog.league(lid)
                            rows += Row(key, ft.name, league, ft, evs.sortedBy { it.startMs })
                            usedKeys += key
                        }
                    }
                }
            } else if (key.startsWith("l:")) {
                val lid = key.substring(2)
                val league = SportsCatalog.league(lid)
                val evs = byLeague["l:$lid"] ?: emptyList()
                if (evs.isNotEmpty() && league != null) {
                    rows += Row(key, league.name, league, null, evs.sortedBy { it.startMs })
                    usedKeys += key
                }
            } else if (key.startsWith("w:")) {
                val word = key.substring(2)
                val evs = byWord["w:$word"] ?: emptyList()
                if (evs.isNotEmpty()) {
                    rows += Row(key, word, null, null, evs.sortedBy { it.startMs })
                    usedKeys += key
                }
            }
        }

        // Add remaining buckets not in order
        byTeam.entries.filter { (k, _) -> k !in usedKeys }.forEach { (k, evs) ->
            val parts = k.substring(2).split(':')
            if (parts.size == 2) {
                val (lid, tid) = parts[0] to parts[1]
                val ft = follows.teams.firstOrNull { it.leagueId == lid && it.teamId == tid }
                val league = SportsCatalog.league(lid)
                rows += Row(k, ft?.name ?: "Team", league, ft, evs.sortedBy { it.startMs })
            }
        }
        byLeague.entries.filter { (k, _) -> k !in usedKeys }.forEach { (k, evs) ->
            val lid = k.substring(2)
            val league = SportsCatalog.league(lid)
            league?.let {
                rows += Row(k, league.name, league, null, evs.sortedBy { it.startMs })
            }
        }
        byWord.entries.filter { (k, _) -> k !in usedKeys }.forEach { (k, evs) ->
            val word = k.substring(2)
            rows += Row(k, word, null, null, evs.sortedBy { it.startMs })
        }

        return Page(rows)
    }
}