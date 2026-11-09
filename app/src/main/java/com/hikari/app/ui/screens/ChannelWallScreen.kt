@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hikari.app.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.hikari.app.HikariApp
import com.hikari.app.data.IptvChannel
import com.hikari.app.data.XtreamCredentials
import com.hikari.app.iptv.ChannelWallChannels
import com.hikari.app.iptv.WallChannel
import com.hikari.app.providers.IptvProvider
import com.hikari.app.providers.gatherIptvChannels
import com.hikari.app.ui.navigation.Routes
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val WALL_PREFS = "channel_wall"
private const val WALL_CUSTOM = "custom_channels"

/** A stream the user added themselves to the Channel Wall. */
private data class WallCustomChannel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String?,
)

private fun parseCustomChannels(raw: String?): List<WallCustomChannel> {
    if (raw.isNullOrBlank()) return emptyList()
    val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    val out = ArrayList<WallCustomChannel>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val name = o.optString("name")
        val url = o.optString("url")
        if (name.isBlank() || url.isBlank()) continue
        out += WallCustomChannel(
            id = o.optString("id").ifBlank { "custom_$i" },
            name = name,
            url = url,
            logo = o.optString("logo").takeIf { it.isNotBlank() },
        )
    }
    return out
}

private fun encodeCustomChannels(list: List<WallCustomChannel>): String =
    JSONArray().apply {
        list.forEach { channel ->
            put(
                JSONObject()
                    .put("id", channel.id)
                    .put("name", channel.name)
                    .put("url", channel.url)
                    .put("logo", channel.logo.orEmpty()),
            )
        }
    }.toString()

/** Artwork found for a wall channel in the user's own playlists/portals,
 *  keyed by curated channel id. Updated after a channel scan so the wall
 *  cards get a real thumbnail even when the curated icon URL is dead. */
private object ChannelWallArt {
    val byChannel = androidx.compose.runtime.mutableStateMapOf<String, String>()
}

/**
 * The Live TV directory from PlayTorrio, rebuilt as Hikari's Channel Wall.
 *
 * The wall itself is a curated set of channel *searches* (ESPN, UFC, HBO…).
 * Tapping one asks every installed M3U playlist, Xtream portal and addon
 * playlist for matching channels and plays the chosen stream in Hikari's
 * player. The Sources sheet manages those portals/playlists through Hikari's
 * own IPTV provider — the same Xtream validation and M3U parser the IPTV tab
 * uses — so nothing is duplicated here.
 */
@Composable
fun ChannelWallScreen(nav: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val scope = rememberCoroutineScope()
    val allProviders by app.providers.providers.collectAsState()
    val playlists = remember(allProviders) { allProviders.filterIsInstance<IptvProvider>() }
    val prefs = remember { context.getSharedPreferences(WALL_PREFS, Context.MODE_PRIVATE) }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var customChannels by remember { mutableStateOf<List<WallCustomChannel>>(emptyList()) }
    var showAddCustom by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var selectedWall by remember { mutableStateOf<WallChannel?>(null) }
    var wallMatches by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var wallLoading by remember { mutableStateOf(false) }
    var allChannels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var channelsLoading by remember { mutableStateOf(false) }
    var channelsJob by remember { mutableStateOf<Deferred<List<IptvChannel>>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // A portal/M3U dialog: "xtream" or "m3u", null when closed.
    var portalMode by remember { mutableStateOf<String?>(null) }
    var portalName by remember { mutableStateOf("") }
    var portalUrl by remember { mutableStateOf("") }
    var portalUser by remember { mutableStateOf("") }
    var portalPass by remember { mutableStateOf("") }
    var portalBusy by remember { mutableStateOf(false) }
    var portalError by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun saveCustomChannels(list: List<WallCustomChannel>) {
        customChannels = list
        prefs.edit().putString(WALL_CUSTOM, encodeCustomChannels(list)).apply()
    }

    LaunchedEffect(Unit) {
        customChannels = parseCustomChannels(prefs.getString(WALL_CUSTOM, null))
    }

    fun requestChannels(force: Boolean = false): Deferred<List<IptvChannel>> {
        val existing = channelsJob
        if (!force && existing != null) return existing
        val next = scope.async(Dispatchers.IO) { gatherIptvChannels(playlists, force) }
        channelsJob = next
        return next
    }

    LaunchedEffect(playlists, reloadKey) {
        allChannels = emptyList()
        channelsJob = null
        if (playlists.isEmpty()) {
            channelsLoading = false
            return@LaunchedEffect
        }
        channelsLoading = true
        val loaded = requestChannels(force = reloadKey > 0).await()
        allChannels = loaded
        channelsLoading = false
        // A wall channel often has a curated logo that has gone stale; the
        // user's own playlist already carries the live logo/thumbnail. Scan
        // once per load and use it as the card/hero image.
        val art = withContext(Dispatchers.Default) {
            val out = HashMap<String, String>()
            for (wall in ChannelWallChannels.all) {
                val match = loaded.firstOrNull { channel ->
                    !channel.artwork.isNullOrBlank() && ChannelWallChannels.matches(wall, channel.name)
                } ?: continue
                match.artwork?.let { out[wall.id] = it }
            }
            out
        }
        ChannelWallArt.byChannel.clear()
        ChannelWallArt.byChannel.putAll(art)
    }

    fun openWall(channel: WallChannel) {
        selectedWall = channel
        wallMatches = emptyList()
        wallLoading = true
        scope.launch {
            val channels = if (allChannels.isNotEmpty()) allChannels else requestChannels().await()
            if (allChannels.isEmpty()) allChannels = channels
            wallMatches = withContext(Dispatchers.Default) {
                channels
                    .filter { ChannelWallChannels.matches(channel, it.name) || ChannelWallChannels.matches(channel, it.group) }
                    .distinctBy { it.url }
            }
            wallMatches.firstOrNull()?.artwork?.takeIf { it.isNotBlank() }?.let {
                ChannelWallArt.byChannel[channel.id] = it
            }
            wallLoading = false
        }
    }

    fun playChannel(channel: IptvChannel) {
        selectedWall = null
        playIptvChannel(context, channel)
    }

    fun openPortalDialog(mode: String) {
        portalMode = mode
        portalName = ""
        portalUrl = ""
        portalUser = ""
        portalPass = ""
        portalError = null
    }

    fun addPortal() {
        if (portalBusy) return
        val mode = portalMode ?: return
        val url = portalUrl.trim()
        val name = portalName.trim()
        if (url.isBlank()) {
            portalError = "Enter the portal/playlist address."
            return
        }
        portalBusy = true
        portalError = null
        scope.launch {
            try {
                val result = if (mode == "xtream") {
                    val creds = XtreamCredentials(
                        baseUrl = url,
                        username = portalUser.trim(),
                        password = portalPass.trim(),
                    )
                    val credError = creds.validate()
                    if (credError != null) {
                        portalError = credError
                        return@launch
                    }
                    val preview = IptvProvider.previewXtream(creds).getOrElse {
                        portalError = it.message ?: "Could not reach that Xtream portal."
                        return@launch
                    }
                    addIptvPlaylist(app, preview.m3uUrl, null, name.ifBlank { preview.serverName })
                } else {
                    addIptvPlaylist(app, url, null, name)
                }
                result.fold(
                    onSuccess = { count ->
                        app.providers.refresh()
                        reloadKey++
                        portalMode = null
                        toast(
                            if (mode == "xtream") "Xtream portal added ($count channels)"
                            else "M3U playlist added ($count channels)",
                        )
                    },
                    onFailure = { portalError = it.message ?: "Could not save that playlist." },
                )
            } finally {
                portalBusy = false
            }
        }
    }

    val wallQuery = query.trim()
    val searchResults = remember(wallQuery) {
        if (wallQuery.isBlank()) emptyList()
        else ChannelWallChannels.all.filter { channel ->
            channel.name.contains(wallQuery, ignoreCase = true) ||
                channel.category.contains(wallQuery, ignoreCase = true) ||
                channel.keywords.any { it.contains(wallQuery, ignoreCase = true) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF080A0F))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(Icons.Filled.Search, contentDescription = "Search channels", tint = Color.White.copy(alpha = 0.85f))
            }
            IconButton(onClick = { showSources = true }) {
                Icon(Icons.Filled.LiveTv, contentDescription = "IPTV portals & playlists", tint = Color.White.copy(alpha = 0.85f))
            }
            IconButton(onClick = { reloadKey++ }) {
                if (channelsLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh channels", tint = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                placeholder = { Text("Search the wall…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
            )
        }

        if (wallQuery.isNotBlank()) {
            if (searchResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No curated channel matches \"$wallQuery\".", color = Color.White.copy(alpha = 0.6f))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(searchResults, key = { it.id }) { channel ->
                        WallGridCard(channel) { openWall(channel) }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    WallHero(ChannelWallChannels.featured) { openWall(it) }
                }
                item {
                    WallQuickRow(
                        custom = customChannels,
                        onPlay = { custom ->
                            playIptvChannel(
                                context,
                                IptvChannel(
                                    name = custom.name,
                                    url = custom.url,
                                    logo = custom.logo,
                                    providerId = "channel-wall",
                                ),
                            )
                        },
                        onAdd = { showAddCustom = true },
                        onRemove = { custom -> saveCustomChannels(customChannels - custom) },
                    )
                }

                val espnIds = listOf(
                    "espn_plus", "espn", "espn2", "espnu", "ncaa_cbb",
                    "ncaa_mens_cbb", "ncaa_womens_cbb", "sec_network",
                    "acc_network", "big_ten_network", "pac_12_network",
                    "espnews", "espn_deportes", "longhorn_network", "bally_sports",
                )
                val usLeagueIds = listOf(
                    "nba", "nfl", "nfl_redzone", "mlb", "nhl",
                    "fox_sports", "cbs_sports", "nbc_sports", "dazn", "eurosport",
                )
                val espn = espnIds.mapNotNull(ChannelWallChannels::byId)
                val usLeagues = usLeagueIds.mapNotNull(ChannelWallChannels::byId)
                if (espn.isNotEmpty()) {
                    item { WallSection("ESPN & College", "Every ESPN feed, NCAA and the conference networks", espn, ::openWall) }
                }
                if (usLeagues.isNotEmpty()) {
                    item { WallSection("US Major Leagues", "NBA, NFL, MLB, NHL and the national sports networks", usLeagues, ::openWall) }
                }
                val categories = listOf(
                    Triple("Soccer", "Champions League, Premier League, beIN and more", "Soccer"),
                    Triple("Combat", "UFC, WWE, AEW, boxing and PPV", "Combat"),
                    Triple("Racing", "Formula 1, MotoGP, NASCAR and rally", "Racing"),
                    Triple("Movies & Premium", "HBO, Showtime, Starz and the movie channels", "Movies"),
                    Triple("News", "CNN, BBC, Sky News and the world's newsrooms", "News"),
                    Triple("Discovery & Documentaries", "Nat Geo, Discovery, History and more", "Discovery"),
                    Triple("Kids & Family", "Cartoon Network, Disney, Nickelodeon and more", "Kids"),
                    Triple("US Region", "US networks and local broadcasters", "US"),
                    Triple("UK Region", "BBC, ITV, Sky and Channel 4", "UK"),
                    Triple("Canada Region", "CBC, CTV, TSN and Sportsnet", "CA"),
                    Triple("Bay Area", "The Bay Area's local stations", "Bay Area"),
                    Triple("International Sports", "Cricket, rugby, international feeds and more", "Int. Sports"),
                    Triple("Arabic & Regional", "MBC, Rotana, OSN and the Arabic hubs", "Arabic"),
                    Triple("More Sports", "The rest of the sports directory", "Sports"),
                )
                categories.forEach { (title, subtitle, category) ->
                    val channels = ChannelWallChannels.byCategory(category)
                    if (channels.isNotEmpty()) {
                        item(key = "cat:$category") {
                            WallSection(title, subtitle, channels, ::openWall)
                        }
                    }
                }
            }
        }
    }

    if (showSources) {
        WallSourcesSheet(
            playlists = playlists,
            onDismiss = { showSources = false },
            onBrowse = { provider ->
                showSources = false
                nav.navigate(Routes.iptvPlaylist(provider.config.id))
            },
            onAddXtream = { showSources = false; openPortalDialog("xtream") },
            onAddM3u = { showSources = false; openPortalDialog("m3u") },
            onGenerate = { showSources = false; showGenerator = true },
        )
    }

    selectedWall?.let { channel ->
        WallMatchSheet(
            channel = channel,
            matches = wallMatches,
            loading = wallLoading,
            onDismiss = { selectedWall = null },
            onPlay = { playChannel(it) },
            onSources = { selectedWall = null; showSources = true },
        )
    }


    if (showGenerator) {
        PortalGeneratorDialog(
            app = app,
            onDismiss = {
                showGenerator = false
                reloadKey++
            },
        )
    }

    if (showAddCustom) {
        AddCustomChannelDialog(
            onAdd = { name, url, logo ->
                saveCustomChannels(
                    customChannels + WallCustomChannel(
                        id = "custom_" + System.currentTimeMillis(),
                        name = name,
                        url = url,
                        logo = logo.takeIf { it.isNotBlank() },
                    ),
                )
                showAddCustom = false
            },
            onDismiss = { showAddCustom = false },
        )
    }

    if (portalMode != null) {
        AddPortalDialog(
            xtream = portalMode == "xtream",
            name = portalName,
            url = portalUrl,
            user = portalUser,
            pass = portalPass,
            busy = portalBusy,
            error = portalError,
            onName = { portalName = it },
            onUrl = { portalUrl = it },
            onUser = { portalUser = it },
            onPass = { portalPass = it },
            onConfirm = ::addPortal,
            onDismiss = { if (!portalBusy) portalMode = null },
        )
    }
}

@Composable
private fun WallHero(channels: List<WallChannel>, onOpen: (WallChannel) -> Unit) {
    if (channels.isEmpty()) return
    val pager = rememberPagerState(pageCount = { channels.size })
    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(230.dp)) { page ->
            val channel = channels[page]
            val colors = remember(channel.gradient) { channel.gradient.map(::Color) }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(colors.ifEmpty { listOf(Color(0xFF24104F), Color(0xFF0B0B10)) })),
            ) {
                AsyncImage(
                    model = channel.backdropUrl
                        ?: ChannelWallArt.byChannel[channel.id]
                        ?: channel.iconUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x66000000), Color(0xF2080A0F)),
                            ),
                        ),
                )
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WallLogo(channel, 62)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.category.uppercase(), color = Color(0xFF8FE3FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(channel.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Surface(onClick = { onOpen(channel) }, shape = RoundedCornerShape(50), color = Color.White) {
                                Row(Modifier.padding(horizontal = 13.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF080A0F), modifier = Modifier.size(17.dp))
                                    Text("Watch now", color = Color(0xFF080A0F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { onOpen(channel) },
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                            ) {
                                Text("Sources", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 24.dp),
        ) {
            repeat(channels.size) { index ->
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(if (index == pager.currentPage) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (index == pager.currentPage) Color.White else Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

@Composable
private fun WallQuickRow(
    custom: List<WallCustomChannel>,
    onPlay: (WallCustomChannel) -> Unit,
    onAdd: () -> Unit,
    onRemove: (WallCustomChannel) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(custom, key = { "custom:${it.id}" }) { channel ->
            Box(Modifier.width(138.dp)) {
                WallCustomCard(channel) { onPlay(channel) }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xCCCC0000))
                        .clickable { onRemove(channel) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove channel", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
        item {
            Box(
                Modifier
                    .width(138.dp)
                    .height(168.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C0F17))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFF7C5CFF), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(5.dp))
                    Text("Add Channel", color = Color(0xFF7C5CFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A user-added stream: the wall's Add Channel writes one of these. */
@Composable
private fun WallCustomCard(channel: WallCustomChannel, onClick: () -> Unit) {
    val palettes = listOf(
        listOf(Color(0xFF2A155C), Color(0xFF0B0B10)),
        listOf(Color(0xFF0B4F4F), Color(0xFF0B0B10)),
        listOf(Color(0xFF5C2A15), Color(0xFF0B0B10)),
        listOf(Color(0xFF1B3A6B), Color(0xFF0B0B10)),
    )
    val colors = remember(channel.id) { palettes[(channel.id.hashCode() and Int.MAX_VALUE) % palettes.size] }
    Column(
        Modifier
            .width(138.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val initials = channel.name.trim().split(' ').take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                .ifBlank { channel.name.take(3).uppercase() }
            if (!channel.logo.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier.size(60.dp),
                    contentScale = ContentScale.Fit,
                    loading = { WallCustomFallback(initials) },
                    error = { WallCustomFallback(initials) },
                    success = { SubcomposeAsyncImageContent() },
                )
            } else {
                WallCustomFallback(initials)
            }
        }
        Text(channel.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Custom stream", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun WallCustomFallback(initials: String) {
    Box(
        Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WallSection(
    title: String,
    subtitle: String,
    channels: List<WallChannel>,
    onOpen: (WallChannel) -> Unit,
) {
    Column {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(channels, key = { it.id }) { channel ->
                Box(Modifier.width(138.dp)) {
                    WallCard(channel) { onOpen(channel) }
                }
            }
        }
    }
}

@Composable
private fun WallCard(channel: WallChannel, onClick: () -> Unit) {
    val colors = remember(channel.gradient) { channel.gradient.map(::Color) }
    Column(
        Modifier
            .width(138.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors.ifEmpty { listOf(Color(0xFF1A1A1A), Color(0xFF0B0B10)) }))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            WallLogo(channel, 60)
        }
        Text(channel.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(channel.category, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun WallGridCard(channel: WallChannel, onClick: () -> Unit) {
    val colors = remember(channel.gradient) { channel.gradient.map(::Color) }
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors.ifEmpty { listOf(Color(0xFF1A1A1A), Color(0xFF0B0B10)) }))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            WallLogo(channel, 50)
        }
        Text(channel.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(channel.category, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun WallLogo(channel: WallChannel, size: Int) {
    val icon = ChannelWallArt.byChannel[channel.id] ?: channel.iconUrl
    if (!icon.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = icon,
            contentDescription = channel.name,
            modifier = Modifier.size(size.dp),
            contentScale = ContentScale.Fit,
            loading = { WallLogoFallback(channel, size) },
            error = { WallLogoFallback(channel, size) },
            success = { SubcomposeAsyncImageContent() },
        )
    } else {
        WallLogoFallback(channel, size)
    }
}

/** Every wall channel has a visible tile even when its logo URL is missing or dead. */
@Composable
private fun WallLogoFallback(channel: WallChannel, size: Int) {
    val label = channel.short.ifBlank {
        channel.name.trim().split(' ').take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
    }.ifBlank { channel.name.take(3).uppercase() }
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = (size / 3).coerceAtLeast(11).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun WallMatchSheet(
    channel: WallChannel,
    matches: List<IptvChannel>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onPlay: (IptvChannel) -> Unit,
    onSources: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WallLogo(channel, 58)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(channel.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(channel.category, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                if (matches.isNotEmpty()) {
                    IconButton(onClick = { onPlay(matches.first()) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play best match", tint = Color(0xFF00D2EF), modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF7C5CFF))
                }
                matches.isEmpty() -> {
                    Text(
                        "No channel in your playlists or portals matches \"${channel.name}\" yet.",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Add an Xtream portal or an M3U playlist from Sources, then tap refresh.",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> {
                    Text("${matches.size} source${if (matches.size == 1) "" else "s"} found", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(matches, key = { it.url }) { match ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPlay(match) }
                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val logo = match.artwork
                                if (!logo.isNullOrBlank()) {
                                    AsyncImage(
                                        model = logo,
                                        contentDescription = null,
                                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(match.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(match.group.takeIf { it.isNotBlank() }, match.providerId.takeIf { it.isNotBlank() })
                                            .joinToString(" · "),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF00D2EF))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSources) { Text("Manage portals") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WallSourcesSheet(
    playlists: List<IptvProvider>,
    onDismiss: () -> Unit,
    onBrowse: (IptvProvider) -> Unit,
    onAddXtream: () -> Unit,
    onAddM3u: () -> Unit,
    onGenerate: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 16.dp)) {
            Text("IPTV Portals & Playlists", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                "Xtream portals and M3U playlists are managed by Hikari: the same validation, channel lists and player the IPTV tab uses.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))
            if (playlists.isEmpty()) {
                Text(
                    "No portals or playlists yet. Add an Xtream portal or paste an M3U link below.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(playlists, key = { it.config.id }) { provider ->
                        val count = IptvProvider.channelCounts[provider.config.id] ?: 0
                        val isXtream = provider.config.url.contains("get.php", true) ||
                            provider.config.url.contains("player_api", true) ||
                            provider.config.url.contains("type=m3u", true)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onBrowse(provider) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF7C5CFF).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color(0xFF9B84FF))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(provider.config.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    buildString {
                                        append(if (isXtream) "Xtream" else "M3U")
                                        if (count > 0) append(" · $count channels")
                                        val host = runCatching { java.net.URI(provider.config.url).host }.getOrNull()
                                        if (!host.isNullOrBlank()) append(" · $host")
                                    },
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("Browse", color = Color(0xFF00D2EF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddXtream,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFF)),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Xtream portal", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onAddM3u, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("M3U playlist", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3A3)),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate portals (Cloud Vault)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Importing an M3U file from storage is done from IPTV → + → Playlist.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AddCustomChannelDialog(
    onAdd: (name: String, url: String, logo: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun normalized(): String? {
        var value = url.trim()
        if (value.isBlank()) return null
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "https://$value"
        }
        val host = runCatching { java.net.URI(value).host }.getOrNull().orEmpty()
        if (host.isBlank()) return null
        return value
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add a stream of your own to the wall. It plays in Hikari's player like any playlist channel.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL (m3u8, mpd, mp4 or direct link)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = logo,
                    onValueChange = { logo = it },
                    label = { Text("Logo / thumbnail URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val address = normalized()
                    if (name.isBlank()) {
                        error = "Give the channel a name."
                    } else if (address == null) {
                        error = "Enter a valid stream address."
                    } else {
                        onAdd(name.trim(), address, logo.trim())
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddPortalDialog(
    xtream: Boolean,
    name: String,
    url: String,
    user: String,
    pass: String,
    busy: Boolean,
    error: String?,
    onName: (String) -> Unit,
    onUrl: (String) -> Unit,
    onUser: (String) -> Unit,
    onPass: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (xtream) "Add Xtream portal" else "Add M3U playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (xtream) "The panel is validated before it is saved; its channels appear in the wall immediately."
                    else "Hikari downloads and parses the playlist, then its channels join the wall.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrl,
                    label = { Text(if (xtream) "Portal URL (http://host:port)" else "M3U / M3U8 URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (xtream) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = onUser,
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = onPass,
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (xtream) "Validate & add" else "Add playlist")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
