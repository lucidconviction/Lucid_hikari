package com.hikari.app.sports

import com.hikari.app.data.IptvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Matches sports events to channels in the user's IPTV playlists using text matching.
 * Since Hikari doesn't have EPG data, this uses channel names, groups, and logos to find
 * the best match for a given event.
 */
object SportsMatcher {

    /** Matches [events] to the user's IPTV channels. Returns eventId -> List<ChannelMatch>. */
    suspend fun match(events: List<SportsEvent>, channels: List<IptvChannel>): Map<String, List<ChannelMatch>> = withContext(Dispatchers.Default) {
        if (channels.isEmpty()) emptyMap() else {
            val normalizedChannels = channels.map { channel ->
                val normName = SportsCatalog.norm(channel.name)
                val normGroup = SportsCatalog.norm(channel.group)
                val normLogo = channel.logo?.let { SportsCatalog.norm(it) } ?: ""
                val normTvgId = channel.tvgId?.let { SportsCatalog.norm(it) } ?: ""
                NormalizedChannel(channel, normName, normGroup, normLogo, normTvgId)
            }
            
            events.mapNotNull { event ->
                val league = SportsCatalog.league(event.leagueId)
                val matches = normalizedChannels.mapNotNull { nc -> matchEventToChannel(event, league, nc) }
                    .sortedByDescending { it.score }
                    .take(5) // Top 5 matches per event
                if (matches.isNotEmpty()) event.id to matches else null
            }.toMap()
        }
    }

    /** Matches a single event to a single channel. */
    private fun matchEventToChannel(event: SportsEvent, league: League?, nc: NormalizedChannel): ChannelMatch? {
        var score = 0
        val how = mutableListOf<String>()
        
        // League name in channel name/group
        league?.let { l ->
            l.guideWords.forEach { word ->
                val normWord = SportsCatalog.norm(word)
                if (nc.normName.contains(normWord) || nc.normGroup.contains(normWord)) {
                    score += 40
                    how.add("league:$word")
                }
            }
            if (l.espnPath != null) {
                val pathWord = l.espnPath.split('/').last()
                val normPath = SportsCatalog.norm(pathWord)
                if (nc.normName.contains(normPath) || nc.normGroup.contains(normPath)) {
                    score += 30
                    how.add("espnPath:$pathWord")
                }
            }
        }
        
        // Team names in channel name
        event.away?.let { side ->
            val (strong, weak) = SportsCatalog.teamNames(side.team, league!!)
            strong.forEach { name ->
                val normName = SportsCatalog.norm(name)
                if (nc.normName.contains(normName)) {
                    score += 35
                    how.add("team:${side.team.name}")
                }
            }
            weak.forEach { name ->
                val normName = SportsCatalog.norm(name)
                if (nc.normName.contains(normName)) {
                    score += 15
                    how.add("team(loc):${side.team.name}")
                }
            }
        }
        
        event.home?.let { side ->
            val (strong, weak) = SportsCatalog.teamNames(side.team, league!!)
            strong.forEach { name ->
                val normName = SportsCatalog.norm(name)
                if (nc.normName.contains(normName)) {
                    score += 35
                    how.add("team:${side.team.name}")
                }
            }
            weak.forEach { name ->
                val normName = SportsCatalog.norm(name)
                if (nc.normName.contains(normName)) {
                    score += 15
                    how.add("team(loc):${side.team.name}")
                }
            }
        }
        
        // Sport name in group
        league?.sport?.let { sport ->
            val sportNorm = SportsCatalog.norm(sport.label)
            if (nc.normGroup.contains(sportNorm)) {
                score += 20
                how.add("sportGroup:$sportNorm")
            }
            if (nc.normName.contains(sportNorm)) {
                score += 10
                how.add("sportName:$sportNorm")
            }
        }
        
        // TVG-ID matching (if available and matches team/league abbreviations)
        league?.let { l ->
            event.away?.let { side ->
                val abbr = side.team.abbr.lowercase()
                if (abbr.length >= 2 && nc.normTvgId.contains(" $abbr ")) {
                    score += 25
                    how.add("tvgId:$abbr")
                }
            }
            event.home?.let { side ->
                val abbr = side.team.abbr.lowercase()
                if (abbr.length >= 2 && nc.normTvgId.contains(" $abbr ")) {
                    score += 25
                    how.add("tvgId:$abbr")
                }
            }
        }
        
        // Known sports network names
        val sportsNetworks = mapOf(
            "espn" to 25, "sky sports" to 25, "bt sport" to 25, "tnt sports" to 25,
            "bein" to 20, "nbc sports" to 20, "cbs sports" to 20, "fox sports" to 20,
            "tsn" to 20, "sportsnet" to 20, "kayo" to 20, "kayofreesports" to 15,
            "dazn" to 20, "viaplay" to 15, "premier sports" to 20,
            "eurosport" to 20, "canal+" to 15, "canal plus" to 15,
            "sky sport" to 25, "super sport" to 15, "supersport" to 15
        )
        sportsNetworks.forEach { (net, pts) ->
            val normNet = SportsCatalog.norm(net)
            if (nc.normName.contains(normNet) || nc.normGroup.contains(normNet)) {
                score += pts
                how.add("network:$net")
            }
        }
        
        // News/general channels that might show sports (lower score)
        val generalTerms = listOf("news", "general", "entertainment", "variety")
        generalTerms.forEach { term ->
            val normTerm = SportsCatalog.norm(term)
            if (nc.normGroup.contains(normTerm)) {
                score -= 10
            }
        }
        
        return if (score > 0) {
            ChannelMatch(
                channelName = nc.original.name,
                score = score,
                how = how.joinToString(","),
                programTitle = null, // No EPG data
                quality = null,
                catchup = false,
                logo = nc.original.logo
            )
        } else null
    }

    private data class NormalizedChannel(
        val original: IptvChannel,
        val normName: String,
        val normGroup: String,
        val normLogo: String,
        val normTvgId: String
    )
}