package com.hikari.app.sports

import com.hikari.app.data.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Matches sports events to channels in the user's IPTV playlists.
 *
 * Hikari has no EPG, so the matching uses what the playlist and the feed know:
 * the teams playing (strongly), the networks the fixture is broadcast on, the
 * league, and the channel's group. A plain "ESPN" channel is not offered for
 * every game in the world — a match has to be about *this* game.
 */
object SportsMatcher {

    /** Matches [events] to the user's IPTV channels. Returns eventId -> List<ChannelMatch>. */
    suspend fun match(events: List<SportsEvent>, channels: List<IptvChannel>): Map<String, List<ChannelMatch>> = withContext(Dispatchers.Default) {
        if (channels.isEmpty()) return@withContext emptyMap()
        val normalizedChannels = channels.map { channel ->
            NormalizedChannel(
                channel = channel,
                normName = SportsCatalog.norm(channel.name),
                normGroup = SportsCatalog.norm(channel.group),
                normTvgId = channel.tvgId?.let { SportsCatalog.norm(it) } ?: "",
            )
        }
        events.mapNotNull { event ->
            val league = SportsCatalog.league(event.leagueId)
            val matches = normalizedChannels
                .mapNotNull { nc -> matchEventToChannel(event, league, nc) }
                .sortedWith(
                    compareByDescending<ChannelMatch> { it.score }
                        .thenByDescending { it.channel?.logo != null },
                )
                .take(8)
            if (matches.isNotEmpty()) event.id to matches else null
        }.toMap()
    }

    private fun matchEventToChannel(
        event: SportsEvent,
        league: League?,
        nc: NormalizedChannel,
    ): ChannelMatch? {
        var score = 0
        val why = mutableListOf<String>()
        val sideNames = mutableListOf<Pair<String, Boolean>>() // name -> strong

        fun scoreName(raw: String, strong: Boolean, team: String) {
            val n = SportsCatalog.norm(raw)
            if (n.length < 3) return
            if (!nc.normName.contains(n) && !nc.normTvgId.contains(" $n ")) return
            score += if (strong) 55 else 22
            why += "team:$team"
        }

        listOfNotNull(event.away, event.home).forEach { side ->
            if (league != null) {
                val (strong, weak) = SportsCatalog.teamNames(side.team, league)
                strong.forEach { scoreName(it, true, side.team.name) }
                weak.forEach { scoreName(it, false, side.team.name) }
                sideNames += strong.map { it to true }
            }
        }

        // Both teams named on one channel is what a dedicated broadcast looks like.
        val namedTeams = why.count { it.startsWith("team:") }
        if (namedTeams >= 2) {
            score += 45
            why += "bothTeams"
        }

        // The networks the fixture itself lists ("ESPN", "Sky Sports Main Event").
        event.broadcasts.forEach { network ->
            val n = SportsCatalog.norm(network)
            if (n.length >= 2 && (nc.normName.contains(n) || nc.normGroup.contains(n))) {
                score += 35
                why += "network:$network"
            }
        }

        // The league's own name is weaker evidence, but still evidence.
        league?.guideWords?.forEach { word ->
            val n = SportsCatalog.norm(word)
            if (n.length >= 3 && (nc.normName.contains(n) || nc.normGroup.contains(n))) {
                score += 12
                why += "league:$word"
            }
        }

        // Sports channels: only a small nudge, never enough on its own.
        league?.sport?.let { sport ->
            val n = SportsCatalog.norm(sport.label)
            if (nc.normGroup.contains(n)) {
                score += 5
                why += "sportGroup"
            }
        }

        // General entertainment channels are a poor place for a game.
        if (nc.normGroup.contains("news") || nc.normGroup.contains("movie") ||
            nc.normGroup.contains("music") || nc.normGroup.contains("kids")
        ) {
            score -= 25
        }

        if (score < 35) return null
        return ChannelMatch(
            channelName = nc.channel.name,
            score = score.coerceAtMost(100),
            how = why.distinct().joinToString(","),
            programTitle = null,
            quality = null,
            catchup = false,
            logo = nc.channel.logo,
            channel = nc.channel,
        )
    }

    private data class NormalizedChannel(
        val channel: IptvChannel,
        val normName: String,
        val normGroup: String,
        val normTvgId: String,
    )
}
