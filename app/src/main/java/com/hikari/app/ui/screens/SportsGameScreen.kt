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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.hikari.app.sports.ChannelMatch
import com.hikari.app.sports.GameState
import com.hikari.app.sports.ScoringPlay
import com.hikari.app.sports.Side
import com.hikari.app.sports.SportsCatalog
import com.hikari.app.sports.SportsDates
import com.hikari.app.sports.SportsEvent
import com.hikari.app.sports.SportsRepository
import com.hikari.app.sports.SportsStandings
import com.hikari.app.sports.SportsWatch
import com.hikari.app.sports.SportsYouTube
import com.hikari.app.sports.TeamInfo
import com.hikari.app.ui.navigation.LocalTaskbarInset
import kotlinx.coroutines.launch
import android.text.format.DateFormat

/**
 * A game's page: the score, the channels showing it (from the IPTV playlists
 * and extension/addon playlists, matched by team and network), the latest
 * scoring, the league table, and remind.
 */
@Composable
fun SportsGameScreen(nav: androidx.navigation.NavHostController, eventId: String) {
    val context = LocalContext.current
    val events by SportsRepository.events.collectAsState()
    val known by SportsRepository.known.collectAsState()
    val matches by SportsRepository.matches.collectAsState()
    val standings by SportsRepository.standings.collectAsState()
    val event = remember(events, known, eventId) {
        events.firstOrNull { it.id == eventId } ?: known.firstOrNull { it.id == eventId }
    }
    val league = event?.let { SportsCatalog.league(it.leagueId) }
    var plays by remember(eventId) { mutableStateOf<List<ScoringPlay>>(emptyList()) }
    var loadingPlays by remember(eventId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bottom = LocalTaskbarInset.current
    val use24h = remember { DateFormat.is24HourFormat(context) }

    // Live/upcoming: streams found by searching every installed extension,
    // addon and IPTV playlist for this fixture (the IPTV channel matches are
    // already shown directly below "Watch on").
    var watchStreams by remember(eventId) { mutableStateOf<List<SportsWatch.StreamLink>>(emptyList()) }
    var watchLoading by remember(eventId) { mutableStateOf(false) }
    var watchReload by remember(eventId) { mutableIntStateOf(0) }
    // Promotional clips before the event; highlights/interviews after it.
    // Both come from the YouTube tab's engine (SportsYouTube).
    var ytClips by remember(eventId) { mutableStateOf<List<SportsYouTube.Clip>>(emptyList()) }
    var ytLoading by remember(eventId) { mutableStateOf(false) }

    LaunchedEffect(event?.id, event?.state, watchReload) {
        val ev = event ?: return@LaunchedEffect
        // Every state searches now: a finished game still has replays,
        // highlights and full-game archives in the app's addons/playlists.
        watchLoading = true
        watchStreams = SportsWatch.search(ev)
        watchLoading = false
    }
    LaunchedEffect(event?.id, event?.state) {
        val ev = event ?: return@LaunchedEffect
        ytLoading = true
        ytClips = SportsYouTube.clipsFor(ev, postEvent = ev.state == GameState.POST)
        ytLoading = false
    }

    LaunchedEffect(event?.id) {
        val ev = event ?: return@LaunchedEffect
        if (ev.espnId != null) {
            loadingPlays = true
            plays = SportsRepository.summaryFor(ev)
            loadingPlays = false
        }
    }
    LaunchedEffect(event?.leagueId) {
        event?.leagueId?.let { SportsRepository.ensureStandings(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(bottom = bottom)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    league?.name ?: "Game",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                event?.let {
                    Text(
                        "${SportsDates.dayLabel(it.startMs)}${if (it.hasTime) " · ${startTime(it.startMs, use24h)}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (league != null) {
                TextButton(onClick = { nav.navigate("sports-league:${android.net.Uri.encode(league.id)}") }) { Text("League") }
            }
        }
        if (event == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This game is no longer in the feed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            event.image?.takeIf { it.isNotBlank() }?.let { image ->
                item {
                    coil.compose.AsyncImage(
                        model = image,
                        contentDescription = event.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }

            item { ScoreCard(event) }

            item {
                var reminded by remember(event.id) { mutableStateOf(SportsRepository.isReminded(event.id)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable {
                            scope.launch {
                                SportsRepository.toggleRemind(event)
                                reminded = SportsRepository.isReminded(event.id)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (reminded) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                        contentDescription = null,
                        tint = if (reminded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (reminded) "Reminder set" else "Remind me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reminded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (event.hasTime && event.state == GameState.PRE) {
                        Text(
                            "at ${startTime(event.startMs, use24h)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- channels
            val found = matches[event.id].orEmpty()
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Watch on",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (watchLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = { watchReload++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Search again")
                    }
                }
            }
            if (event.broadcasts.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        event.broadcasts.distinct().forEach { network ->
                            Text(
                                network,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            if (found.isEmpty()) {
                item {
                    Text(
                        "No channel in your playlists matches this game yet. Add the league's network channel in IPTV, or import a playlist in Extensions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(found, key = { "${event.id}:${it.channelName}" }) { match ->
                    ChannelRow(match)
                }
            }

            // Everything the app found for this fixture: the IPTV channels
            // above, then one clickable row per stream returned by every
            // extension, Stremio addon and playlist. Finished games search
            // highlights/replays too.
            if (watchLoading || watchStreams.isNotEmpty()) {
                item {
                    Text(
                        "From your apps & add-ons",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (watchLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "Searching your playlists, apps and add-ons for this event…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(watchStreams, key = { "w:${it.key}" }) { link ->
                WatchStreamRow(link) { SportsWatch.play(context, link) }
            }

            // ---- YouTube: build-up before the event, highlights after it.
            // Both resolve through the YouTube tab's own engine.
            if (ytLoading || ytClips.isNotEmpty()) {
                item {
                    Text(
                        if (event.state == GameState.POST) "Highlights & interviews" else "Pre-event videos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (ytLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Finding videos on YouTube…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(ytClips, key = { it.videoId }) { clip ->
                                YouTubeClipCard(clip) {
                                    scope.launch {
                                        val problem = SportsYouTube.play(context, clip)
                                        if (problem != null) {
                                            android.widget.Toast.makeText(
                                                context, problem, android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- latest scoring
            if (plays.isNotEmpty()) {
                item {
                    Text("Latest scoring", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                items(plays.take(12), key = { "p:${it.clock}:${it.text}" }) { play ->
                    ScoringRow(play)
                }
            } else if (loadingPlays) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Getting the latest plays…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- table
            val view = standings[event.leagueId]
            if (view != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            view.tables.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "Table",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { nav.navigate("sports-league:${android.net.Uri.encode(event.leagueId)}") }) { Text("Full table") }
                    }
                }
                item {
                    StandingsCard(
                        rows = view.tables.firstOrNull()?.rows.orEmpty(),
                        highlight = listOfNotNull(event.away?.team?.id, event.home?.team?.id).toSet(),
                        limit = 10,
                    )
                }
            }

            // ---- about / broadcast
            event.about?.takeIf { it.isNotBlank() }?.let { about ->
                item {
                    Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ScoreCard(event: SportsEvent) {
    val league = SportsCatalog.league(event.leagueId)
    val ordered = remember(event, league) { league?.let { event.ordered(it.sport) } ?: event.sides }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                league?.let { LeagueMark(it, 22.dp) }
                Text(
                    league?.name ?: event.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.state == GameState.LIVE) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            if (ordered.isEmpty()) {
                Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                event.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ordered.forEachIndexed { index, side ->
                        if (index > 0) {
                            Text(
                                if (event.hasScore) "–" else "v",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BigSide(side, event)
                    }
                }
                event.status?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (event.state == GameState.LIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val periods = ordered.flatMap { it.periods }
                if (periods.any { it.isNotBlank() }) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ordered.forEach { side ->
                            Text(
                                side.periods.joinToString("  "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            event.standings.takeIf { it.isNotEmpty() }?.let { lines ->
                HorizontalDivider()
                lines.take(10).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(line.pos, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(34.dp))
                        Text(line.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        line.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            event.situation?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider()
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BigSide(side: Side, event: SportsEvent) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
        AsyncImageOrMark(side.team, 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            side.team.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (side.winner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (event.hasScore) {
            Text(
                side.score.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        side.record?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AsyncImageOrMark(team: TeamInfo, size: androidx.compose.ui.unit.Dp) {
    if (!team.logo.isNullOrBlank()) {
        AsyncImage(
            model = team.logo,
            contentDescription = team.name,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(team.color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(team.abbr.ifBlank { team.name.take(2) }.uppercase(), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChannelRow(match: ChannelMatch) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = match.channel != null) { match.channel?.let { playIptvChannel(context, it) } },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!match.logo.isNullOrBlank()) {
                AsyncImage(
                    model = match.logo,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text("📺") }
            }
            Column(Modifier.weight(1f)) {
                Text(match.channelName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (match.sure) "Likely showing this game" else "Possible match",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (match.sure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (match.channel != null) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** One clickable stream found by the app-wide event search. */
@Composable
private fun WatchStreamRow(link: SportsWatch.StreamLink, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!link.poster.isNullOrBlank()) {
                AsyncImage(
                    model = link.poster,
                    contentDescription = null,
                    modifier = Modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(5.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text("📡") }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    link.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(link.provider, link.sourceLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** One YouTube clip on the game page (engine-resolved when tapped). */
@Composable
private fun YouTubeClipCard(clip: SportsYouTube.Clip, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(212.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(119.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF181820)),
        ) {
            if (!clip.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = clip.thumbnail,
                    contentDescription = clip.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Box(
                Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape).background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            clip.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        clip.author?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScoringRow(play: ScoringPlay) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(play.clock, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
        play.teamAbbr?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
        }
        Text(play.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (play.awayScore != null || play.homeScore != null) {
            Text(
                "${play.awayScore.orEmpty()}–${play.homeScore.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StandingsCard(
    rows: List<SportsStandings.StandingsRow>,
    highlight: Set<String> = emptySet(),
    limit: Int = Int.MAX_VALUE,
) {
    if (rows.isEmpty()) return
    val show = rows.take(limit)
    val hasPts = rows.any { it.pts > 0 }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("#", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Team", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("W-L", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (hasPts) "PTS" else "PCT", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(38.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            show.forEachIndexed { i, row ->
                val mine = row.team.id in highlight
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                    Text(
                        row.team.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (row.pts > 0) "${row.won}-${row.lost}" else SportsStandings.record(row),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        if (hasPts) "${row.pts}" else if (row.pct > 0f) String.format(java.util.Locale.US, "%.3f", row.pct) else "—",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(38.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
