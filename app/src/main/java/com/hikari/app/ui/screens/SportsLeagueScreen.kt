package com.hikari.app.ui.screens

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hikari.app.sports.GameState
import com.hikari.app.sports.SportsCatalog
import com.hikari.app.sports.SportsDates
import com.hikari.app.sports.SportsEvent
import com.hikari.app.sports.SportsRepository
import com.hikari.app.sports.SportsSettings
import com.hikari.app.ui.navigation.LocalTaskbarInset
import kotlinx.coroutines.launch
import android.text.format.DateFormat

/** A league's page: its fixtures by day, its table, and follow/pin. */
@Composable
fun SportsLeagueScreen(nav: androidx.navigation.NavHostController, leagueId: String) {
    val league = remember(leagueId) { SportsCatalog.league(leagueId) }
    val events by SportsRepository.events.collectAsState()
    val known by SportsRepository.known.collectAsState()
    val follows by SportsRepository.follows.collectAsState()
    val standings by SportsRepository.standings.collectAsState()
    val settings by SportsRepository.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val use24h = remember { DateFormat.is24HourFormat(context) }
    val bottom = LocalTaskbarInset.current

    LaunchedEffect(leagueId) {
        SportsRepository.ensureStandings(leagueId)
        SportsRepository.ensureChannels(context)
    }

    if (league == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("League not found.") }
        return
    }
    val leagueEvents = remember(events, known, leagueId) {
        (events + known).filter { it.leagueId == leagueId }.distinctBy { it.id }.sortedBy { it.startMs }
    }
    val upcoming = leagueEvents.filter { it.state == GameState.PRE || it.state == GameState.OFF }
    val live = leagueEvents.filter { it.state == GameState.LIVE }
    val past = leagueEvents.filter { it.state == GameState.POST }.sortedByDescending { it.startMs }
    val followed = follows.leagues.contains(league.id)
    val pinned = follows.pins.contains(league.id)

    Column(Modifier.fillMaxSize().padding(bottom = bottom)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            LeagueMark(league, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(league.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(league.sport.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { scope.launch { SportsRepository.togglePin(league.id) } }) {
                Icon(
                    if (pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (pinned) "Unpin" else "Pin",
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { scope.launch { SportsRepository.toggleLeague(league.id) } }) {
                Icon(
                    if (followed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (followed) "Unfollow" else "Follow",
                    tint = if (followed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (live.isNotEmpty()) {
                item { SectionTitle("Live Now", true) }
                items(live, key = { "live:${it.id}" }) { ev -> LeagueGameRow(ev, settings, use24h) { nav.navigate("sports-game:${android.net.Uri.encode(ev.id)}") } }
            }
            if (upcoming.isNotEmpty()) {
                item { SectionTitle("Upcoming") }
                items(upcoming, key = { "up:${it.id}" }) { ev -> LeagueGameRow(ev, settings, use24h) { nav.navigate("sports-game:${android.net.Uri.encode(ev.id)}") } }
            }
            val view = standings[league.id]
            if (view != null) {
                item { SectionTitle(view.tables.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "Table") }
                item {
                    StandingsCard(view.tables.firstOrNull()?.rows.orEmpty(), limit = 20)
                }
                if (view.tables.size > 1) {
                    view.tables.drop(1).take(3).forEach { table ->
                        item { SectionTitle(table.name.ifBlank { "Table" }) }
                        item { StandingsCard(table.rows, limit = 12) }
                    }
                }
            }
            if (past.isNotEmpty()) {
                item { SectionTitle("Results") }
                items(past, key = { "post:${it.id}" }) { ev -> LeagueGameRow(ev, settings, use24h) { nav.navigate("sports-game:${android.net.Uri.encode(ev.id)}") } }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, live: Boolean = false) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LeagueGameRow(
    event: SportsEvent,
    settings: SportsSettings,
    use24h: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.width(70.dp)) {
                Text(
                    if (event.hasTime) "${SportsDates.shortDayLabel(event.startMs)}\n${startTime(event.startMs, use24h)}" else SportsDates.shortDayLabel(event.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val ordered = event.ordered(SportsCatalog.league(event.leagueId)?.sport ?: com.hikari.app.sports.Sport.OTHER)
                if (ordered.isEmpty()) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    event.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                } else {
                    ordered.forEach { side ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TeamLogo(side.team.logo, side.team.name, side.team.color, 20.dp)
                            Text(
                                side.team.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (side.winner) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!settings.hideScores && event.hasScore) {
                                Text(side.score.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = if (side.winner) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            event.status?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}
