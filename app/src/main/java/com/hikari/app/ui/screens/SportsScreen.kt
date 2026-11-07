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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hikari.app.HikariApp
import com.hikari.app.data.IptvChannel
import com.hikari.app.player.PlayerActivity
import com.hikari.app.sports.ChannelMatch
import com.hikari.app.sports.CivilTime
import com.hikari.app.sports.EventSource
import com.hikari.app.sports.GameState
import com.hikari.app.sports.League
import com.hikari.app.sports.LiveTvTimeZones
import com.hikari.app.sports.Sport
import com.hikari.app.sports.SportsCatalog
import com.hikari.app.sports.SportsDates
import com.hikari.app.sports.SportsEvent
import com.hikari.app.sports.SportsFollows
import com.hikari.app.sports.SportsRepository
import com.hikari.app.sports.TeamInfo
import com.hikari.app.sports.FollowedTeam

/**
 * Main Sports hub. Shows My Sports / All Events / Following tabs.
 * [nav] is used to open detail screens; passes through [context] to the
 * game page so it can call into the player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(nav: androidx.navigation.NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SportsRepository.settings.collectAsState()
    val follows by SportsRepository.follows.collectAsState()
    val events by SportsRepository.events.collectAsState()
    val loading by SportsRepository.loading.collectAsState()
    val problem by SportsRepository.problem.collectAsState()
    val matches by SportsRepository.matches.collectAsState()

    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("My Sports", "All Events", "Following")

    // Initialize repository and match channels when needed
    LaunchedEffect(Unit) {
        SportsRepository.setViewing(true)
    }

    // First-time setup: open the team picker so users can follow teams right away
    LaunchedEffect(follows.setUp) {
        if (!follows.setUp) tab = 2
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sports", style = MaterialTheme.typography.headlineSmall) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Filled.Favorite
                                    1 -> Icons.Filled.Sports
                                    else -> Icons.Filled.Search
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // Content
            when (tab) {
                0 -> MySportsTab(
                    events = events,
                    follows = follows,
                    settings = settings,
                    matches = matches,
                    onPlay = { event, channel ->
                        launchChannel(context, channel)
                    },
                    onDetail = { event ->
                        nav.navigate("sports-game:${event.id}")
                    },
                    onFollow = { teamInfo ->
                        scope.launch {
                            SportsRepository.toggleTeam(
                                FollowedTeam(
                                    leagueId = teamInfo.leagueId,
                                    teamId = teamInfo.id,
                                    name = teamInfo.name,
                                    abbr = teamInfo.abbr,
                                    color = teamInfo.color,
                                    logo = teamInfo.logo
                                )
                            )
                        }
                    },
                    onSearch = {}
                )
                1 -> AllEventsTab(
                    events = events,
                    matches = matches,
                    onPlay = { channel -> launchChannel(context, channel) },
                    onDetail = { event ->
                        nav.navigate("sports-game:${event.id}")
                    }
                )
                2 -> FollowingTab(
                    follows = follows,
                    leagues = SportsCatalog.leagues,
                    onToggleLeague = { leagueId ->
                        scope.launch { SportsRepository.toggleLeague(leagueId) }
                    },
                    onToggleTeam = { teamInfo ->
                        scope.launch {
                            SportsRepository.toggleTeam(
                                FollowedTeam(
                                    leagueId = teamInfo.leagueId,
                                    teamId = teamInfo.id,
                                    name = teamInfo.name,
                                    abbr = teamInfo.abbr,
                                    color = teamInfo.color,
                                    logo = teamInfo.logo
                                )
                            )
                        }
                    },
                    onAddWord = { word ->
                        scope.launch { SportsRepository.toggleWord(word) }
                    },
                    onSelectTeam = {}
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MySportsTab(
    events: List<SportsEvent>,
    follows: SportsFollows,
    settings: com.hikari.app.sports.SportsSettings,
    matches: Map<String, List<ChannelMatch>>,
    onPlay: (SportsEvent, IptvChannel) -> Unit,
    onDetail: (SportsEvent) -> Unit,
    onFollow: (TeamInfo) -> Unit,
    onSearch: () -> Unit
) {
    val filteredEvents = remember(events, follows) {
        events.filter { event ->
            // Include events for followed teams or leagues
            follows.teams.any { ft ->
                (event.away?.team?.id == ft.teamId && event.away?.team?.leagueId == ft.leagueId) ||
                (event.home?.team?.id == ft.teamId && event.home?.team?.leagueId == ft.leagueId)
            } || follows.leagues.contains(event.leagueId)
        }
    }

    if (filteredEvents.isEmpty()) {
        EmptySportsView(
            message = "No followed sports events",
            subtitle = "Follow teams or leagues in the Following tab to see events here"
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(filteredEvents) { event ->
            EventCard(
                event = event,
                matches = matches[event.id] ?: emptyList(),
                onPlay = { channel -> onPlay(event, channel) },
                onDetail = { onDetail(event) }
            )
        }
    }
}

@Composable
private fun AllEventsTab(
    events: List<SportsEvent>,
    matches: Map<String, List<ChannelMatch>>,
    onPlay: (IptvChannel) -> Unit,
    onDetail: (SportsEvent) -> Unit
) {
    val todayKey = SportsDates.dayKey(SportsDates.dayStart(0))
    val tomorrowKey = SportsDates.dayKey(SportsDates.dayStart(1))
    val yesterdayKey = SportsDates.dayKey(SportsDates.dayStart(-1))

    var selectedDate by remember { mutableStateOf(todayKey) }
    val dateKeys = listOf(yesterdayKey, todayKey, tomorrowKey)
    val dateLabels = listOf("Yesterday", "Today", "Tomorrow")

    val groupedEvents = remember(events, selectedDate) {
        dateKeys.associateWith { dateKey ->
            val dayStart = SportsDates.dayKeyStart(dateKey) ?: return@associateWith emptyList()
            val dayEnd = dayStart + 86_400_000L
            events.filter { it.startMs in dayStart until dayEnd }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Date selector
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dateKeys.size) { i ->
                val key = dateKeys[i]
                val isSelected = key == selectedDate
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selectedDate = key }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = dateLabels[i],
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Events list
        val dayEvents = groupedEvents[selectedDate] ?: emptyList()
        if (dayEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No events for this day", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dayEvents) { event ->
                    EventCard(
                        event = event,
                        matches = matches[event.id] ?: emptyList(),
                        onPlay = { channel -> onPlay(channel) },
                        onDetail = { onDetail(event) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowingTab(
    follows: SportsFollows,
    leagues: List<League>,
    onToggleLeague: (String) -> Unit,
    onToggleTeam: (TeamInfo) -> Unit,
    onAddWord: (String) -> Unit,
    onSelectTeam: () -> Unit
) {
    var selectedSport by remember { mutableStateOf<Sport?>(null) }
    var selectedLeague by remember { mutableStateOf<League?>(null) }
    val scope = rememberCoroutineScope()
    var teams by remember { mutableStateOf<List<TeamInfo>>(emptyList()) }
    var teamsLoaded by remember { mutableStateOf(false) }

    // Load teams when a league is selected
    LaunchedEffect(selectedLeague) {
        val league = selectedLeague ?: return@LaunchedEffect
        if (!teamsLoaded) {
            teamsLoaded = true
            teams = SportsRepository.teamsForLeague(league)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sport chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SportChip(selected = selectedSport == null, label = "All", onClick = { selectedSport = null })
            }
            items(Sport.values().toList()) { sport ->
                SportChip(
                    selected = selectedSport == sport,
                    label = sport.label,
                    onClick = { selectedSport = if (selectedSport == sport) null else sport }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedLeague == null) {
            // League picker
            Text("Pick a League", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val visibleLeagues = remember(leagues, selectedSport, follows) {
                leagues.filter { league ->
                    (selectedSport == null || league.sport == selectedSport) &&
                    (league.espnPath != null || league.teams.isNotEmpty())
                }
            }

            if (visibleLeagues.isEmpty()) {
                Text("No leagues available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleLeagues) { league ->
                        val isFollowed = follows.leagues.contains(league.id)
                        val isPinned = follows.pins.contains(league.id)
                        LeagueRow(
                            league = league,
                            followed = isFollowed,
                            pinned = isPinned,
                            onToggle = { onToggleLeague(league.id) },
                            onClick = { 
                                selectedLeague = league 
                                teamsLoaded = false // Reset for new league
                            }
                        )
                    }
                }
            }
        } else {
            // Team picker for selected league
            val league = selectedLeague!!
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = league.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { selectedLeague = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (teams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(teams) { team ->
                        val isFollowed = follows.followsTeam(league.id, team.id)
                        TeamPickerRow(
                            team = team,
                            league = league,
                            followed = isFollowed,
                            onToggle = {
                                scope.launch {
                                    onToggleTeam(team)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(8.dp))

        // Followed teams summary
        Text("Following (${follows.teams.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))

        if (follows.teams.isEmpty()) {
            Text(
                "Pick teams above to see their games here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(follows.teams) { ft ->
                    val league = leagues.firstOrNull { it.id == ft.leagueId }
                    FollowedChip(
                        team = ft,
                        leagueName = league?.name,
                        onRemove = {
                            scope.launch {
                                onToggleTeam(
                                    TeamInfo(
                                        leagueId = ft.leagueId,
                                        id = ft.teamId,
                                        name = ft.name,
                                        fullName = ft.name,
                                        abbr = ft.abbr,
                                        location = null,
                                        color = ft.color,
                                        altColor = null,
                                        logo = ft.logo
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SportChip(selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun LeagueRow(
    league: League,
    followed: Boolean,
    pinned: Boolean,
    onToggle: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val clickAction = onClick ?: onToggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { clickAction() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (followed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (followed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = league.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (pinned) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.Sports, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TeamRow(
    team: FollowedTeam,
    onUnfollow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onUnfollow() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!team.logo.isNullOrBlank()) {
            AsyncImage(
                model = team.logo,
                contentDescription = team.name,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = team.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.FavoriteBorder, contentDescription = "Unfollow", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
    }
}

/** One team in the league picker: tap the heart to follow / unfollow. */
@Composable
private fun TeamPickerRow(
    team: TeamInfo,
    league: League,
    followed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!team.logo.isNullOrBlank()) {
            AsyncImage(
                model = team.logo,
                contentDescription = team.name,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else {
            Spacer(modifier = Modifier.width(38.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (team.location.isNullOrBlank().not()) {
                Text(
                    text = team.location!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (followed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (followed) "Unfollow" else "Follow",
                tint = if (followed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** A small chip showing a followed team, tappable to unfollow. */
@Composable
private fun FollowedChip(
    team: FollowedTeam,
    leagueName: String?,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .clickable { onRemove() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!team.logo.isNullOrBlank()) {
                AsyncImage(
                    model = team.logo,
                    contentDescription = team.name,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = team.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
        }
        if (leagueName != null) {
            Text(
                text = leagueName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventCard(
    event: SportsEvent,
    matches: List<ChannelMatch>,
    onPlay: (IptvChannel) -> Unit,
    onDetail: () -> Unit
) {
    val sport = SportsCatalog.league(event.leagueId)?.sport ?: Sport.OTHER
    val timeLabel = if (event.hasTime) {
        val t = CivilTime.at(event.startMs, LiveTvTimeZones.SYSTEM)
        "${t.hour}:${if (t.minute < 10) "0" else ""}${t.minute}"
    } else ""

    val stateLabel = when (event.state) {
        GameState.PRE -> "Upcoming"
        GameState.LIVE -> "LIVE"
        GameState.POST -> "Final"
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetail() }
            .padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: state badge + sport
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.state == GameState.LIVE) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = sport.icon + " " + sport.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (stateLabel.isNotBlank()) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.state == GameState.LIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Subtitle
            event.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (timeLabel.isNotBlank()) {
                    Text(text = timeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                event.status?.let { status ->
                    Text(text = status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Channels row
            if (matches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(matches.filter { it.score >= 50 }.take(3)) { match ->
                        ChannelPill(channelName = match.channelName, logo = match.logo)
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ChannelPill(channelName: String, logo: String? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = channelName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptySportsView(message: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Sports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
