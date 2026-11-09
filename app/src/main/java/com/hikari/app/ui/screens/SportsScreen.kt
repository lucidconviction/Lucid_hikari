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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.HikariApp
import com.hikari.app.data.IptvChannel
import com.hikari.app.player.PlayerActivity
import com.hikari.app.sports.ChannelMatch
import com.hikari.app.sports.CivilTime
import com.hikari.app.sports.FollowedTeam
import com.hikari.app.sports.GameState
import com.hikari.app.sports.League
import com.hikari.app.sports.LeagueKind
import com.hikari.app.sports.LiveTvClock
import com.hikari.app.sports.LiveTvTimeZones
import com.hikari.app.sports.Sport
import com.hikari.app.sports.SportsCatalog
import com.hikari.app.sports.SportsDates
import com.hikari.app.sports.SportsEvent
import com.hikari.app.sports.SportsFollows
import com.hikari.app.sports.SportsRepository
import com.hikari.app.sports.SportsReplays
import com.hikari.app.sports.SportsRows
import com.hikari.app.sports.SportsSettings
import com.hikari.app.sports.TeamInfo
import com.hikari.app.ui.navigation.LocalTaskbarInset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.text.format.DateFormat
import java.util.Locale

/** The four pages of the Sports hub. */
private enum class SportsTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MINE("My Sports", Icons.Filled.Favorite),
    ALL("All Events", Icons.Filled.Sports),
    SEARCH("Search", Icons.Filled.Search),
    FOLLOWING("Following", Icons.Filled.Star),
    REPLAYS("Replays", Icons.Filled.Replay),
}

/**
 * The Sports hub: the games of the teams, leagues and names you follow, every
 * fixture day by day, a search, and the following manager. Games open their own
 * page (channels, scoring, table); the channels come from the IPTV playlists
 * and from playlists imported through Extensions/addons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(nav: androidx.navigation.NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val scope = rememberCoroutineScope()
    val settings by SportsRepository.settings.collectAsState()
    val follows by SportsRepository.follows.collectAsState()
    val events by SportsRepository.events.collectAsState()
    val known by SportsRepository.known.collectAsState()
    val loading by SportsRepository.loading.collectAsState()
    val matching by SportsRepository.matching.collectAsState()
    val problem by SportsRepository.problem.collectAsState()
    val matches by SportsRepository.matches.collectAsState()
    val channels by SportsRepository.channels.collectAsState()
    val providers by app.providers.providers.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(if (follows.isEmpty) SportsTab.ALL.ordinal else SportsTab.MINE.ordinal) }
    var showSettings by remember { mutableStateOf(false) }
    var showPickTeams by remember { mutableStateOf(false) }
    val now by produceState(LiveTvClock.nowMs()) {
        while (true) {
            value = LiveTvClock.nowMs()
            delay(30_000)
        }
    }
    val use24h = remember { DateFormat.is24HourFormat(context) }
    val bottom = LocalTaskbarInset.current

    LaunchedEffect(Unit) {
        SportsRepository.setViewing(true)
        // The repository starts fetching at app launch; if that first request
        // failed (network not ready, a timeout) retry now that the user is here.
        SportsRepository.ensureFresh()
        SportsRepository.ensureChannels(context)
    }
    LaunchedEffect(providers.size) {
        // Playlists load asynchronously (and Extensions can add more): retry the
        // match as the provider list fills in.
        SportsRepository.ensureChannels(context)
    }
    DisposableEffect(Unit) {
        onDispose { SportsRepository.setViewing(false) }
    }
    LaunchedEffect(events, channels) {
        // A fresh feed with channels already loaded needs a fresh match.
        if (events.isNotEmpty() && channels.isNotEmpty()) {
            SportsRepository.matchChannels(channels)
        }
    }
    LaunchedEffect(showPickTeams) {
        if (showPickTeams) {
            tab = SportsTab.FOLLOWING.ordinal
            showPickTeams = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (loading || matching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = {
                SportsRepository.refreshNow()
                SportsRepository.ensureChannels(context, force = true)
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Tune, contentDescription = "Sports settings")
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            SportsTab.entries.forEachIndexed { index, t ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(t.icon, contentDescription = null) },
                )
            }
        }
        problem?.let {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = {
                    SportsRepository.refreshNow()
                    SportsRepository.ensureChannels(context, force = true)
                }) { Text("Retry", fontSize = 12.sp) }
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(bottom = bottom)) {
            when (SportsTab.entries[tab]) {
                SportsTab.MINE -> MySportsTab(
                    events = (known + events).distinctBy { it.id }.sortedBy { it.startMs },
                    follows = follows,
                    settings = settings,
                    matches = matches,
                    now = now,
                    use24h = use24h,
                    onOpenGame = { nav.navigate("sports-game:${android.net.Uri.encode(it.id)}") },
                    onOpenLeague = { nav.navigate("sports-league:${android.net.Uri.encode(it.id)}") },
                    onPickTeams = { tab = SportsTab.FOLLOWING.ordinal },
                )
                SportsTab.ALL -> AllEventsTab(
                    events = events,
                    follows = follows,
                    settings = settings,
                    matches = matches,
                    loading = loading,
                    now = now,
                    use24h = use24h,
                    onOpenGame = { nav.navigate("sports-game:${android.net.Uri.encode(it.id)}") },
                )
                SportsTab.SEARCH -> SportsSearchTab(
                    events = events,
                    onOpenGame = { nav.navigate("sports-game:${android.net.Uri.encode(it.id)}") },
                    onOpenLeague = { nav.navigate("sports-league:${android.net.Uri.encode(it.id)}") },
                )
                SportsTab.FOLLOWING -> SportsFollowingTab(
                    follows = follows,
                    settings = settings,
                    onToggleTeam = { t ->
                        scope.launch {
                            SportsRepository.toggleTeam(
                                FollowedTeam(t.leagueId, t.id, t.name, t.abbr, t.color, t.logo),
                            )
                        }
                    },
                    onToggleLeague = { scope.launch { SportsRepository.toggleLeague(it) } },
                    onTogglePin = { scope.launch { SportsRepository.togglePin(it) } },
                    onToggleWord = { scope.launch { SportsRepository.toggleWord(it) } },
                    onOpenLeague = { nav.navigate("sports-league:${android.net.Uri.encode(it.id)}") },
                )
                SportsTab.REPLAYS -> SportsReplaysTab()
            }
        }
    }

    if (showSettings) {
        SportsSettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onUpdate = { block -> scope.launch { SportsRepository.updateSettings(block) } },
        )
    }
}

// ------------------------------------------------------------------ My Sports

@Composable
private fun MySportsTab(
    events: List<SportsEvent>,
    follows: SportsFollows,
    settings: SportsSettings,
    matches: Map<String, List<ChannelMatch>>,
    now: Long,
    use24h: Boolean,
    onOpenGame: (SportsEvent) -> Unit,
    onOpenLeague: (League) -> Unit,
    onPickTeams: () -> Unit,
) {
    if (follows.isEmpty) {
        EmptyNote(
            "Nothing followed yet.",
            "Pick teams and leagues to see their games here.",
            "Pick Your Teams",
            onPickTeams,
        )
        return
    }
    val page = remember(events, follows, now / 60_000) {
        SportsRows.build(events, follows, now) { ev, f ->
            f.leagues.isNotEmpty() || f.teams.isNotEmpty() || f.words.isNotEmpty()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (page.rows.isEmpty()) {
            item {
                Text(
                    "No games for what you follow in this window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(page.rows, key = { it.key }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.team?.let { TeamLogo(it.logo, it.name, it.color, 24.dp) }
                    row.league?.let { LeagueMark(it, 24.dp) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            row.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val table = SportsRepository.standings.collectAsState().value
                        row.team?.let { ft ->
                            val view = table[ft.leagueId]
                            val table0 = view?.tables?.firstOrNull()
                            if (table0 != null) {
                                val place = com.hikari.app.sports.SportsStandings.placeText(table0, ft.teamId)
                                if (place != null) {
                                    Text(place, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    row.league?.takeIf { com.hikari.app.sports.SportsCatalog.mayHaveTable(it) }?.let { l ->
                        TextButton(onClick = { onOpenLeague(l) }) { Text("Table") }
                    }
                }
                val state = rememberLazyListState()
                LazyRow(
                    state = state,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(row.events, key = { it.id }) { ev ->
                        RowCard(ev, matches[ev.id].orEmpty(), follows, settings, now, use24h) { onOpenGame(ev) }
                    }
                }
            }
        }
    }
}

/** A compact card in a followed team's row. */
@Composable
private fun RowCard(
    event: SportsEvent,
    matches: List<ChannelMatch>,
    follows: SportsFollows,
    settings: SportsSettings,
    now: Long,
    use24h: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(250.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMine(event, follows)) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventTopLine(event, matches, now, use24h)
            SidesBlock(event, settings.hideScores)
            if (matches.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "${matches.count { it.sure }} channel${if (matches.count { it.sure } == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ All Events

@Composable
private fun AllEventsTab(
    events: List<SportsEvent>,
    follows: SportsFollows,
    settings: SportsSettings,
    matches: Map<String, List<ChannelMatch>>,
    loading: Boolean,
    now: Long,
    use24h: Boolean,
    onOpenGame: (SportsEvent) -> Unit,
) {
    var day by rememberSaveable { mutableIntStateOf(0) }
    var sportFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val dayStart = remember(day, now / 60_000) { SportsDates.dayStart(day) }
    val dayEnd = remember(day, now / 60_000) { SportsDates.dayStart(day + 1) }
    val active = remember(settings, follows) {
        SportsRepository.activeLeagues(settings, follows).map { it.id }.toSet()
    }
    val filterToChannels = settings.onlyOnMyChannels && matches.isNotEmpty()
    val dayEvents = remember(events, dayStart, dayEnd, active, filterToChannels, matches, follows, now / 30_000) {
        events.filter { ev ->
            val onDay = when {
                ev.state == GameState.POST -> {
                    val finished = if (ev.hasTime) ev.startMs else minOf(ev.endMs ?: ev.startMs, now)
                    finished in dayStart until dayEnd
                }
                ev.hasTime -> ev.startMs in dayStart until dayEnd
                ev.state == GameState.PRE -> ev.startMs in dayStart until dayEnd
                else -> dayEnd > now && ev.startMs < dayEnd && (ev.endMs ?: (ev.startMs + 1)) >= dayStart
            }
            ev.leagueId in active && onDay &&
                (!filterToChannels || ev.state == GameState.POST || isMine(ev, follows) || matches[ev.id]?.isNotEmpty() == true)
        }
    }
    val sports = remember(dayEvents, settings) {
        val order = SportsCatalog.sportsOrder(settings)
        dayEvents.mapNotNull { SportsCatalog.league(it.leagueId)?.sport }.distinct()
            .sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } }
    }
    val shown = remember(dayEvents, sportFilter) {
        if (sportFilter == null) dayEvents else dayEvents.filter { SportsCatalog.league(it.leagueId)?.sport?.name == sportFilter }
    }
    val leagueRank = remember(settings) { SportsCatalog.ordered(settings).mapIndexed { i, l -> l.id to i }.toMap() }
    val live = remember(shown, now / 30_000) {
        shown.filter { it.state == GameState.LIVE }
            .sortedBy { leagueRank[it.leagueId] ?: 999 }
    }
    val upcoming = remember(shown, now / 30_000) {
        shown.filter { it.state == GameState.PRE || it.state == GameState.OFF }
            .sortedWith(compareBy({ if (it.hasTime) it.startMs else Long.MAX_VALUE }, { leagueRank[it.leagueId] ?: 999 }))
    }
    val finished = remember(shown) {
        shown.filter { it.state == GameState.POST }
            .sortedWith(compareByDescending<SportsEvent> { it.startMs }.thenBy { leagueRank[it.leagueId] ?: 999 })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items((-SportsRepository.DAYS_BACK..SportsRepository.DAYS_AHEAD).toList(), key = { it }) { offset ->
                val label = when (offset) {
                    -1 -> "Yesterday"
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> SportsDates.shortDayLabel(SportsRepository.dayStart(offset) + 12 * 3_600_000L)
                }
                FilterChip(selected = day == offset, onClick = { day = offset }, label = { Text(label) })
            }
        }
        if (sports.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { FilterChip(selected = sportFilter == null, onClick = { sportFilter = null }, label = { Text("All") }) }
                items(sports, key = { it.name }) { s ->
                    FilterChip(
                        selected = sportFilter == s.name,
                        onClick = { sportFilter = s.name },
                        label = { Text("${s.icon} ${s.label}") },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (live.isEmpty() && upcoming.isEmpty() && finished.isEmpty()) {
                item {
                    Text(
                        if (loading) "Getting today's games…" else "No games on this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            section("Live Now", live, follows, settings, matches, now, use24h, live = true, onOpenGame)
            section("Upcoming", upcoming, follows, settings, matches, now, use24h, onOpenGame = onOpenGame)
            section("Finished", finished, follows, settings, matches, now, use24h, onOpenGame = onOpenGame)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<SportsEvent>,
    follows: SportsFollows,
    settings: SportsSettings,
    matches: Map<String, List<ChannelMatch>>,
    now: Long,
    use24h: Boolean,
    live: Boolean = false,
    onOpenGame: (SportsEvent) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h:$title") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(list.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(list, key = { "$title/${it.id}" }) { ev ->
        GameListRow(
            ev,
            matches[ev.id].orEmpty(),
            follows,
            settings,
            now,
            use24h,
            live = live,
        ) { onOpenGame(ev) }
    }
}

@Composable
private fun GameListRow(
    event: SportsEvent,
    matches: List<ChannelMatch>,
    follows: SportsFollows,
    settings: SportsSettings,
    now: Long,
    use24h: Boolean,
    live: Boolean,
    onClick: () -> Unit,
) {
    val league = SportsCatalog.league(event.leagueId)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMine(event, follows)) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (event.hasTime) startTime(event.startMs, use24h) else "TBD",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (live) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        Spacer(Modifier.width(3.dp))
                        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    event.status?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                } else if (event.state == GameState.POST) {
                    Text("Final", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (event.away == null && event.home == null) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    event.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                } else {
                    SideLine(event.away, event, settings.hideScores)
                    SideLine(event.home, event, settings.hideScores)
                }
                league?.let {
                    Text(
                        "${it.sport.icon} ${it.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (matches.isNotEmpty()) {
                val sure = matches.count { it.sure }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    if (sure > 0) {
                        Text("$sure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Search

@Composable
private fun SportsSearchTab(
    events: List<SportsEvent>,
    onOpenGame: (SportsEvent) -> Unit,
    onOpenLeague: (League) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = SportsCatalog.norm(query)
    val leagues = remember(q) {
        if (q.isBlank()) emptyList()
        else SportsCatalog.all.filter { SportsCatalog.norm(it.name).contains(q) || SportsCatalog.norm(it.short).contains(q) }.take(12)
    }
    val matched = remember(q, events) {
        if (q.isBlank()) emptyList()
        else events.filter { ev ->
            val leagueName = SportsCatalog.league(ev.leagueId)?.name.orEmpty()
            SportsCatalog.norm(ev.title).contains(q) ||
                SportsCatalog.norm(ev.subtitle.orEmpty()).contains(q) ||
                SportsCatalog.norm(ev.note.orEmpty()).contains(q) ||
                SportsCatalog.norm(leagueName).contains(q) ||
                listOfNotNull(ev.away, ev.home).any { SportsCatalog.norm(it.team.name).contains(q) || SportsCatalog.norm(it.team.fullName).contains(q) }
        }.take(40)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            placeholder = { Text("Search teams, leagues, games") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (q.isBlank()) {
                item {
                    Text(
                        "Search a team, a league or a game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            if (leagues.isNotEmpty()) {
                item { Text("Leagues", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                items(leagues, key = { "l:${it.id}" }) { league ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenLeague(league) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LeagueMark(league, 30.dp)
                        Column(Modifier.weight(1f)) {
                            Text(league.name, style = MaterialTheme.typography.bodyLarge)
                            Text(league.sport.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (matched.isNotEmpty()) {
                item { Text("Games", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(matched, key = { "e:${it.id}" }) { ev ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenGame(ev) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ev.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${SportsCatalog.league(ev.leagueId)?.name.orEmpty()} · ${SportsDates.dayLabel(ev.startMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ev.away?.team?.let { TeamLogo(it.logo, it.name, it.color, 22.dp) }
                        ev.home?.team?.let { TeamLogo(it.logo, it.name, it.color, 22.dp) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Following

@Composable
private fun SportsFollowingTab(
    follows: SportsFollows,
    settings: SportsSettings,
    onToggleTeam: (TeamInfo) -> Unit,
    onToggleLeague: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleWord: (String) -> Unit,
    onOpenLeague: (League) -> Unit,
) {
    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    var openedLeague by remember { mutableStateOf<League?>(null) }
    var word by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (openedLeague != null) {
        val league = openedLeague!!
        var teams by remember(league.id) { mutableStateOf<List<TeamInfo>>(emptyList()) }
        var loadingTeams by remember(league.id) { mutableStateOf(true) }
        LaunchedEffect(league.id) {
            loadingTeams = true
            teams = SportsRepository.teamsForLeague(league)
            loadingTeams = false
        }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { openedLeague = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                LeagueMark(league, 28.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(league.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(league.sport.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onToggleLeague(league.id) }) {
                    Icon(
                        if (follows.leagues.contains(league.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Follow league",
                        tint = if (follows.leagues.contains(league.id)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (loadingTeams) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (teams.isEmpty()) {
                EmptyNote("No teams for this league in the feed.", null, null) {}
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(teams, key = { it.id }) { team ->
                        val followed = follows.followedTeam(team) != null
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggleTeam(team) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TeamLogo(team.logo, team.name, team.color, 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(team.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                team.location?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(
                                if (followed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (followed) "Unfollow" else "Follow",
                                tint = if (followed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val leagues = remember(sport) {
        SportsCatalog.all
            .filter { sport == null || it.sport.name == sport }
            .sortedWith(compareBy({ it.sport.ordinal }, { it.name }))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                item { FilterChip(selected = sport == null, onClick = { sport = null }, label = { Text("All") }) }
                items(Sport.entries.toList(), key = { it.name }) { s ->
                    FilterChip(selected = sport == s.name, onClick = { sport = s.name }, label = { Text("${s.icon} ${s.label}") })
                }
            }
        }
        if (follows.teams.isNotEmpty()) {
            item {
                Text("Your Teams", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(follows.teams, key = { it.key }) { ft ->
                        FollowedTeamChip(ft) {
                            scope.launch {
                                onToggleTeam(
                                    TeamInfo(ft.leagueId, ft.teamId, ft.name, ft.name, ft.abbr, null, ft.color, null, ft.logo),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Leagues", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        items(leagues, key = { it.id }) { league ->
            val followed = follows.leagues.contains(league.id)
            val pinned = follows.pins.contains(league.id)
            Row(
                Modifier.fillMaxWidth().clickable { openedLeague = league }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LeagueMark(league, 26.dp)
                Column(Modifier.weight(1f)) {
                    Text(league.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(league.sport.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onOpenLeague(league) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Open league", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onTogglePin(league.id) }) {
                    Icon(
                        if (pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (pinned) "Unpin" else "Pin to top",
                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onToggleLeague(league.id) }) {
                    Icon(
                        if (followed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (followed) "Unfollow league" else "Follow league",
                        tint = if (followed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("Names to Watch", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "A player, a show, anything: games whose name mentions it appear in My Sports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("e.g. Luke Littler") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (word.isNotBlank()) {
                            onToggleWord(word)
                            word = ""
                        }
                    }),
                )
                IconButton(onClick = {
                    if (word.isNotBlank()) {
                        onToggleWord(word)
                        word = ""
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add name")
                }
            }
        }
        if (follows.words.isNotEmpty()) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    follows.words.forEach { w ->
                        FilterChip(selected = true, onClick = { onToggleWord(w) }, label = { Text(w) }, trailingIcon = {
                            Icon(Icons.Filled.Clear, contentDescription = "Remove $w", modifier = Modifier.size(14.dp))
                        })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FollowedTeamChip(team: FollowedTeam, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .clickable(onClick = onRemove)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeamLogo(team.logo, team.name, team.color, 20.dp)
        Text(team.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Filled.Clear, contentDescription = "Unfollow ${team.name}", modifier = Modifier.size(14.dp))
    }
}

// ------------------------------------------------------------------ settings

@Composable
private fun SportsSettingsDialog(
    settings: SportsSettings,
    onDismiss: () -> Unit,
    onUpdate: (SportsSettings.() -> SportsSettings) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sports settings") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Region", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("" to "Device") + SportsCatalog.regions).forEach { (code, label) ->
                        FilterChip(
                            selected = settings.region == code,
                            onClick = { onUpdate { copy(region = code) } },
                            label = { Text(label) },
                        )
                    }
                }
                HorizontalDivider()
                SettingSwitch("Hide scores", "Show fixtures without the score.", settings.hideScores) { v ->
                    onUpdate { copy(hideScores = v) }
                }
                SettingSwitch("Only games on my channels", "Leave out games none of your channels show.", settings.onlyOnMyChannels) { v ->
                    onUpdate { copy(onlyOnMyChannels = v) }
                }
                SettingSwitch("Switch to the game at kick-off", "Reminders can open the channel by themselves.", settings.autoSwitch) { v ->
                    onUpdate { copy(autoSwitch = v) }
                }
                SettingSwitch("Scoring alerts", "Tell me when a followed team scores.", settings.scoringAlerts) { v ->
                    onUpdate { copy(scoringAlerts = v) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ------------------------------------------------------------------ shared bits

@Composable
private fun EventTopLine(event: SportsEvent, matches: List<ChannelMatch>, now: Long, use24h: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (event.state == GameState.LIVE) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Text("LIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            event.status?.let {
                Text("· $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        } else if (event.state == GameState.POST) {
            Text("Final", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                if (event.hasTime) "${SportsDates.shortDayLabel(event.startMs)} · ${startTime(event.startMs, use24h)}" else SportsDates.shortDayLabel(event.startMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (matches.isNotEmpty()) {
            Text(
                "${matches.count { it.sure }} TV",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SidesBlock(event: SportsEvent, hideScores: Boolean) {
    val league = SportsCatalog.league(event.leagueId)
    val ordered = remember(event, league) { league?.let { event.ordered(it.sport) } ?: event.sides }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ordered.isEmpty()) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            ordered.forEach { side ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamLogo(side.team.logo, side.team.name, side.team.color, 24.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            side.team.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (side.winner) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        side.record?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    if (!hideScores && event.hasScore) {
                        Text(
                            side.score.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (side.winner) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideLine(side: com.hikari.app.sports.Side?, event: SportsEvent, hideScores: Boolean) {
    if (side == null) return
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
        if (!hideScores && event.hasScore) {
            Text(
                side.score.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (side.winner) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
internal fun TeamLogo(logo: String?, name: String, color: Long?, size: Dp) {
    if (!logo.isNullOrBlank()) {
        AsyncImage(
            model = logo,
            contentDescription = name,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Fit,
        )
    } else {
        val c = color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant
        Box(
            Modifier.size(size).clip(CircleShape).background(c),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(2).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun LeagueMark(league: League, size: Dp) {
    val color = Color(league.color.toInt())
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            league.short.take(3).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyNote(title: String, subtitle: String?, button: String?, onButton: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Sports,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (button != null) {
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onButton) { Text(button) }
        }
    }
}

// ------------------------------------------------------------------ replays

/**
 * Replays from the sport-video-archive: recent UFC, MMA and boxing cards with
 * their poster. Picking a stream plays it in the app when the link resolves to
 * media; file hosts and embed pages open normally when they can't.
 */
@Composable
private fun SportsReplaysTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by SportsReplays.items.collectAsState()
    val replaySections by SportsReplays.sections.collectAsState()
    val loading by SportsReplays.loading.collectAsState()
    val problem by SportsReplays.problem.collectAsState()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var chooser by remember { mutableStateOf<SportsReplays.Replay?>(null) }

    LaunchedEffect(Unit) { SportsReplays.ensureLoaded() }
    val shown = remember(items, filter) {
        if (filter == null) items else items.filter { it.section == filter }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Replays", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = { scope.launch { SportsReplays.ensureLoaded(force = true) } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh replays")
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // One chip per sport that actually returned videos, in the
            // archive's own reading order. "All" is the combined, date-sorted
            // list — the fastest way to see what happened last night.
            item {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("All") },
                )
            }
            replaySections.forEach { section ->
                item {
                    FilterChip(
                        selected = filter == section.key,
                        onClick = { filter = section.key },
                        label = { Text(section.label) },
                    )
                }
            }
        }
        problem?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (loading && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (loading) "Looking for replays…" else "No replays in this section.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(shown, key = { it.id }) { replay ->
                    ReplayCard(replay) { chooser = replay }
                }
            }
        }
    }

    val picked = chooser
    if (picked != null) {
        AlertDialog(
            onDismissRequest = { chooser = null },
            title = { Text(picked.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (picked.dateMs > 0) SportsDates.longDateLabel(picked.dateMs) else picked.sectionLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (picked.streams.isEmpty()) {
                        Text("No streams listed. Open the archive page for this card.", style = MaterialTheme.typography.bodySmall)
                    }
                    picked.streams.take(12).forEach { stream ->
                        TextButton(
                            onClick = {
                                chooser = null
                                scope.launch {
                                    val url = stream.url
                                    if (isDirectMedia(url)) {
                                        playReplayUrl(context, url, picked.title)
                                    } else {
                                        val extracted = withTimeoutOrNull(20_000L) {
                                            runCatching {
                                                com.hikari.app.cs3.FallbackResolver.resolveEmbedUrl(url, null)
                                            }.getOrNull()
                                        }.orEmpty()
                                        val playable = extracted.firstOrNull { it.url.isNotBlank() }
                                        if (playable != null) {
                                            playReplayUrl(context, playable.url, picked.title, playable.headers)
                                        } else {
                                            openInBrowser(context, picked.pageUrl.ifBlank { url })
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stream.label + (stream.provider?.let { " · $it" } ?: ""),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val page = picked.pageUrl
                    chooser = null
                    if (page.isNotBlank()) openInBrowser(context, page)
                }) { Text("Open page") }
            },
            dismissButton = { TextButton(onClick = { chooser = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun ReplayCard(replay: SportsReplays.Replay, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF1A1A1E))) {
                if (!replay.thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = replay.thumb,
                        contentDescription = replay.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    Modifier.align(Alignment.Center).size(42.dp).clip(CircleShape).background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
                Text(
                    replay.sectionLabel.uppercase(),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Column(Modifier.padding(10.dp)) {
                Text(replay.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (replay.dateMs > 0) {
                    Text(
                        SportsDates.longDateLabel(replay.dateMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun isDirectMedia(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd") ||
        path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".ts")
}

private fun playReplayUrl(
    context: Context,
    url: String,
    title: String,
    headers: Map<String, String> = emptyMap(),
) {
    if (url.isBlank()) return
    val lower = url.substringBefore('?').lowercase()
    val headersJson = org.json.JSONObject().apply { headers.forEach { (k, v) -> put(k, v) } }
    val sources = org.json.JSONArray().put(
        org.json.JSONObject()
            .put("name", "Replay")
            .put("url", url)
            .put("headers", headersJson)
            .put("isM3u8", lower.endsWith(".m3u8"))
            .put("isMpd", lower.endsWith(".mpd"))
            .put("provider", "Replay"),
    )
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", title)
        putExtra("sources", sources.toString())
        putExtra("askServer", false)
    }
    context.startActivity(intent)
}

private fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    com.hikari.app.ui.openInAppBrowser(context, url, "Replay")
}

// ------------------------------------------------------------------ playback

private fun isMine(event: SportsEvent, follows: SportsFollows): Boolean {
    if (event.leagueId in follows.leagues) return true
    val sides = listOfNotNull(event.away, event.home)
    if (sides.any { follows.followsTeam(it.team.leagueId, it.team.id) }) return true
    val title = SportsCatalog.norm(event.title)
    return follows.words.any { w ->
        val n = SportsCatalog.norm(w)
        n.isNotBlank() && title.contains(n)
    }
}

internal fun startTime(ms: Long, use24h: Boolean): String {
    val t = CivilTime.at(ms, LiveTvTimeZones.SYSTEM)
    return if (use24h) {
        String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
    } else {
        val h = when {
            t.hour == 0 -> 12
            t.hour > 12 -> t.hour - 12
            else -> t.hour
        }
        String.format(Locale.US, "%d:%02d %s", h, t.minute, if (t.hour < 12) "AM" else "PM")
    }
}

/** Plays an IPTV channel in Hikari's player. */
internal fun playIptvChannel(context: Context, channel: IptvChannel) {
    val isM3u8 = channel.url.endsWith(".m3u8", ignoreCase = true)
    val isMpd = channel.url.substringBefore('?').contains(".mpd", ignoreCase = true)
    val headers = org.json.JSONObject().apply {
        channel.headers.forEach { (k, v) -> put(k, v) }
        channel.cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
    }
    val sources = org.json.JSONArray().put(
        org.json.JSONObject()
            .put("name", channel.name)
            .put("url", channel.url)
            .put("headers", headers)
            .put("isM3u8", isM3u8)
            .put("isMpd", isMpd)
            .put("provider", "IPTV")
            .put("providerId", channel.providerId)
            .put("providerName", "")
            .put("details", "")
            .put("ytId", "")
            .put("isTorrent", false),
    )
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("title", channel.name)
        putExtra("sources", sources.toString())
        putExtra("askServer", false)
        putExtra("histProviderId", channel.providerId)
        putExtra("histTitle", channel.name)
        channel.artwork?.takeIf { it.isNotBlank() }?.let {
            putExtra("titleLogo", it)
            putExtra("bannerBackdrop", it)
            putExtra("histPoster", it)
        }
    }
    context.startActivity(intent)
}
