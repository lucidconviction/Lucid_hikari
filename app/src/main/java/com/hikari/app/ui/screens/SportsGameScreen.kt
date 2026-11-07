package com.hikari.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hikari.app.data.IptvChannel
import com.hikari.app.player.PlayerActivity
import com.hikari.app.sports.CivilTime
import com.hikari.app.sports.GameState
import com.hikari.app.sports.League
import com.hikari.app.sports.LiveTvTimeZones
import com.hikari.app.sports.SportsCatalog
import com.hikari.app.sports.SportsDates
import com.hikari.app.sports.SportsEvent
import com.hikari.app.sports.SportsRepository
import com.hikari.app.sports.Side

import kotlinx.coroutines.launch

/**
 * Detail screen for a single sports event. Shows teams, score, status,
 * matched channels, and a play button for the best match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsGameScreen(nav: androidx.navigation.NavHostController, eventId: String) {
    val context = LocalContext.current
    val events by SportsRepository.events.collectAsState()
    val matches by SportsRepository.matches.collectAsState()
    val standings by SportsRepository.standings.collectAsState()

    val event = events.find { it.id == eventId } ?: return
    val eventMatches = matches[eventId] ?: emptyList()

    val league = SportsCatalog.league(event.leagueId)
    val awayScore = event.away?.score?.takeIf { it.isNotBlank() } ?: "-"
    val homeScore = event.home?.score?.takeIf { it.isNotBlank() } ?: "-"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(league?.name ?: event.title, style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Score section
            item {
                ScoreSection(
                    away = event.away,
                    home = event.home,
                    state = event.state,
                    status = event.status
                )
            }

            // Event info
            if (event.subtitle?.isNotBlank() == true || event.note?.isNotBlank() == true) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            event.subtitle?.let {
                                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            event.note?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Channels section
            if (eventMatches.isNotEmpty()) {
                item {
                    Text("Broadcast Channels", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    eventMatches
                        .filter { it.score >= 50 }
                        .sortedByDescending { it.score }
                        .forEach { match ->
                            val sport = com.hikari.app.sports.SportsCatalog.league(event.leagueId)?.sport
                            ChannelRow(
                                channel = IptvChannel(
                                    name = match.channelName,
                                    group = "",
                                    tvgId = null,
                                    logo = match.logo,
                                    url = "",
                                    providerId = "",
                                    country = ""
                                ),
                                score = match.score,
                                how = match.how,
                                logo = match.logo,
                                sport = sport,
                                onPlay = { launchChannel(context, it) }
                            )
                        }
                }
            }

            // Standings for this league
            standings[event.leagueId]?.let { sv ->
                sv.tables.firstOrNull()?.rows?.firstOrNull { row ->
                    row.team.id == event.away?.team?.id || row.team.id == event.home?.team?.id
                }?.let { row ->
                    item {
                        Text("Standings Position", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rank: ${row.played + row.won + row.lost + row.tied + row.otl}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Status section
            item {
                StatusSection(state = event.state, status = event.status)
            }

            // Remind section
            item {
                val scope = rememberCoroutineScope()
                var reminded by remember { mutableStateOf(SportsRepository.isReminded(event.id)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                SportsRepository.toggleRemind(event)
                                reminded = SportsRepository.isReminded(event.id)
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (reminded) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                        contentDescription = "Remind",
                        tint = if (reminded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (reminded) "Reminder set" else "Remind me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reminded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun launchChannel(context: Context, channel: IptvChannel) {
    val isM3u8 = channel.url.endsWith(".m3u8", ignoreCase = true)
    val isMpd = channel.url.substringBefore('?').contains(".mpd", ignoreCase = true)
    val sources = org.json.JSONArray().apply {
        put(org.json.JSONObject()
            .put("name", channel.name)
            .put("url", channel.url)
            .put("isM3u8", isM3u8)
            .put("isMpd", isMpd)
            .put("provider", "IPTV")
            .put("providerId", channel.providerId)
            .put("providerName", "")
            .put("details", "")
            .put("ytId", "")
            .put("isTorrent", false)
        )
    }
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", channel.name)
        putExtra("sources", sources.toString())
        putExtra("askServer", false)
        putExtra("histProviderId", channel.providerId)
    }
    context.startActivity(intent)
}

@Composable
private fun ScoreSection(away: Side?, home: Side?, state: GameState, status: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Away team
                TeamScoreSection(side = away, isHome = false)
                // Score
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${away?.score?.takeIf { it.isNotBlank() } ?: "-"} - ${home?.score?.takeIf { it.isNotBlank() } ?: "-"}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Home team
                TeamScoreSection(side = home, isHome = true)
            }

            // Status
            if (status?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state == GameState.LIVE) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state == GameState.LIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamScoreSection(side: Side?, isHome: Boolean) {
    Column(horizontalAlignment = if (isHome) Alignment.End else Alignment.Start) {
        side?.team?.let { team ->
            if (!team.logo.isNullOrBlank()) {
                AsyncImage(
                    model = team.logo,
                    contentDescription = team.name,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Text(
                text = team.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!team.abbr.isBlank()) {
                Text(
                    text = team.abbr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: IptvChannel,
    score: Int,
    how: String,
    logo: String? = null,
    sport: com.hikari.app.sports.Sport? = null,
    onPlay: (IptvChannel) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay(channel) }
            .padding(4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Text(
                    text = sport?.icon ?: "📺",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.size(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Match: $score%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusSection(state: GameState, status: String?) {
    val label = when (state) {
        GameState.PRE -> "Upcoming"
        GameState.LIVE -> "Live"
        GameState.POST -> "Final"
        else -> "TBD"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (state == GameState.LIVE) Icons.Filled.RemoveRedEye else Icons.Filled.Schedule,
                contentDescription = null,
                tint = if (state == GameState.LIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label + (if (status?.isNotBlank() == true) " · $status" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Helper to look up standings rank - would need proper implementation
private fun ordinal(teamId: String, table: com.hikari.app.sports.SportsStandings.StandingsTable): String {
    val idx = table.rows.indexOfFirst { it.team.id == teamId }
    return if (idx >= 0) "${idx + 1}" else "-"
}
