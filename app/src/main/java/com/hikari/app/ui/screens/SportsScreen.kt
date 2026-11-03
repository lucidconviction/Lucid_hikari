package com.hikari.app.ui.screens

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BreakfastDining
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hikari.app.net.Http
import com.hikari.app.tv.tvPress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

// ─── Data models ────────────────────────────────────────────────────────────

data class SportEvent(
    val id: String,
    val name: String,
    val league: String,
    val sportKey: String,
    val startTime: Long,        // epoch ms
    val homeTeam: String?,
    val awayTeam: String?,
    val homeScore: String?,
    val awayScore: String?,
    val status: String,         // "pre" | "in" | "post"
    val isLive: Boolean,
    val posterUrl: String? = null,
    val broadcaster: String = "",
    val allBroadcasters: List<String> = emptyList(),
)

data class HeroSlide(
    val imageUrl: String,
    val title: String,
    val subtitle: String,
    val dateStr: String,
    val broadcaster: String?,
    val event: SportEvent?,
)

data class SportDef(
    val key: String,
    val label: String,
    val endpoint: String?, // e.g. "football/nfl"
    val icon: ImageVector,
    val iconLabel: String,
)

// ─── Verified working ESPN sport/league endpoints ────────────────────────────

private const val ESPN_BASE = "https://site.api.espn.com/apis/site/v2/sports"

private val SPORTS_CATALOG = listOf(
    SportDef("all", "All Sports", null, Icons.Filled.LiveTv, "All"),
    SportDef("nfl", "NFL", "football/nfl", Icons.Filled.SportsFootball, "NFL"),
    SportDef("nba", "NBA", "basketball/nba", Icons.Filled.SportsBasketball, "NBA"),
    SportDef("nhl", "NHL", "hockey/nhl", Icons.Filled.SportsHockey, "NHL"),
    SportDef("mlb", "MLB", "baseball/mlb", Icons.Filled.BreakfastDining, "MLB"),
    SportDef("mma", "MMA / UFC", "mma/ufc", Icons.Filled.SportsMma, "MMA"),
    SportDef("golf", "Golf", "golf/pga", Icons.Filled.SportsGolf, "Golf"),
    SportDef("tennis", "Tennis", "tennis/wta", Icons.Filled.SportsTennis, "Tennis"),
)

// ─── Watchlist / Notifications persistence ───────────────────────────────────

private const val SPORT_PREFS = "lucid_hikari_sports"
private const val KEY_WATCHLIST = "watchlist"
private const val KEY_NOTIFICATIONS = "notifications"

// Sports notification constants
internal const val SPORTS_NOTIF_CHANNEL_ID = "hikari_sports_reminder"
internal const val SPORTS_NOTIF_ID_PREFIX = 0x535000L
private const val ALARM_LEAD_MS = 5 * 60 * 1000L  // 5 minutes before event
private const val ACTION_NOTIFY = "com.hikari.app.sports.REMINDER"
private const val EXTRA_EVENT_ID = "event_id"

object SportsPrefs {
    fun getWatchlist(ctx: Context): Set<String> =
        ctx.getSharedPreferences(SPORT_PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_WATCHLIST, emptySet()) ?: emptySet()

    fun addToWatchlist(ctx: Context, id: String) {
        val prefs = ctx.getSharedPreferences(SPORT_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WATCHLIST, mutableSetOf<String>())?.toMutableSet() ?: mutableSetOf()
        set.add(id)
        prefs.edit().putStringSet(KEY_WATCHLIST, set).apply()
    }

    fun removeFromWatchlist(ctx: Context, id: String) {
        val prefs = ctx.getSharedPreferences(SPORT_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WATCHLIST, mutableSetOf<String>())?.toMutableSet() ?: return
        set.remove(id)
        prefs.edit().putStringSet(KEY_WATCHLIST, set).apply()
    }

    fun isInWatchlist(ctx: Context, id: String): Boolean =
        getWatchlist(ctx).contains(id)

    fun getNotifications(ctx: Context): Set<String> =
        ctx.getSharedPreferences(SPORT_PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_NOTIFICATIONS, emptySet()) ?: emptySet()

    fun toggleNotification(ctx: Context, id: String): Boolean {
        val prefs = ctx.getSharedPreferences(SPORT_PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_NOTIFICATIONS, mutableSetOf<String>())?.toMutableSet() ?: mutableSetOf()
        val added = set.add(id)
        if (!added) set.remove(id)
        prefs.edit().putStringSet(KEY_NOTIFICATIONS, set).apply()
        return set.contains(id)
    }

    fun hasNotification(ctx: Context, id: String): Boolean =
        getNotifications(ctx).contains(id)

    /** Check whether POST_NOTIFICATIONS permission is granted (API 33+). */
    fun notificationPermissionGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Schedule an exact alarm at `eventTime - alarmLeadMs` to fire a notification. */
    fun scheduleNotification(ctx: Context, eventId: String, eventTimeMs: Long) {
        val alarmTime = (eventTimeMs - ALARM_LEAD_MS).coerceAtLeast(0L)
        val mgr = ContextCompat.getSystemService(ctx, AlarmManager::class.java) ?: return
        val intent = Intent(ctx, SportsNotificationReceiver::class.java).apply {
            action = ACTION_NOTIFY
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
            } else {
                @Suppress("deprecation")
                mgr.set(AlarmManager.RTC_WAKEUP, alarmTime, pi)
            }
        } catch (_: SecurityException) {
            // Permission not granted — will retry when user grants it
        }
    }

    /** Cancel any pending alarm for this event ID. */
    fun cancelNotification(ctx: Context, eventId: String) {
        val mgr = ContextCompat.getSystemService(ctx, AlarmManager::class.java) ?: return
        val intent = Intent(ctx, SportsNotificationReceiver::class.java).apply {
            action = ACTION_NOTIFY
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val pi = PendingIntent.getBroadcast(
            ctx,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pi?.let {
            try {
                mgr.cancel(it)
            } catch (_: Exception) {}
            it.cancel()
        }
    }
}

// ─── Sports Notification Receiver ────────────────────────────────────────────

class SportsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val eventId = intent?.getStringExtra(EXTRA_EVENT_ID)?.takeIf { it.isNotBlank() } ?: return
        val mgr = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                SPORTS_NOTIF_CHANNEL_ID,
                "Sports Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders for watched sports events"
                enableVibration(true)
                enableLights(true)
            }
            mgr.createNotificationChannel(channel)
        }
        val title = ctx.getString(com.hikari.app.R.string.app_name)
        val text = "Event reminder — tap to search streams"
        val openIntent = Intent(ctx, com.hikari.app.MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_sports_tab", true)
        }
        val pi = PendingIntent.getActivity(
            ctx,
            (eventId.hashCode() + 1000).coerceAtLeast(1),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(ctx, SPORTS_NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        mgr.notify((eventId.hashCode().toLong() + SPORTS_NOTIF_ID_PREFIX).toInt() and 0x7FFFFFFF, notif)
    }
}

// ─── Network Fetching ────────────────────────────────────────────────────────

private val USER_AGENT = Http.UA

suspend fun fetchEspnScoreboard(endpoint: String, sportKey: String): List<SportEvent> = withContext(Dispatchers.IO) {
    val url = "$ESPN_BASE/$endpoint/scoreboard"
    val raw = Http.getString(url, mapOf("User-Agent" to USER_AGENT, "Referer" to "https://www.espn.com/"))
        ?: return@withContext emptyList()
    parseEspnJson(raw, sportKey)
}

private fun parseEspnJson(raw: String, sportKey: String): List<SportEvent> {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val events = root.optJSONArray("events") ?: return emptyList()
    val out = mutableListOf<SportEvent>()

    val leaguesArr = root.optJSONArray("leagues")
    val defaultLeague = if (leaguesArr != null && leaguesArr.length() > 0) {
        leaguesArr.optJSONObject(0)?.optString("name") ?: sportKey.uppercase()
    } else sportKey.uppercase()

    for (i in 0 until events.length()) {
        val ev = events.optJSONObject(i) ?: continue
        val id = ev.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
        
        val name = ev.optString("name").ifEmpty {
            val shortName = ev.optString("shortName")
            if (shortName.isNotBlank()) shortName else "Event $id"
        }

        val statusObj = ev.optJSONObject("status")
        val typeObj = statusObj?.optJSONObject("type")
        val state = typeObj?.optString("state").orEmpty()
        val isLive = state == "in"

        val competitions = ev.optJSONArray("competitions")
        val mainComp = if (competitions != null && competitions.length() > 0) competitions.optJSONObject(0) else null

        var homeTeam: String? = null
        var awayTeam: String? = null
        var homeScore: String? = null
        var awayScore: String? = null
        var posterUrl: String? = null
        val allBroadcasters = mutableListOf<String>()

        if (mainComp != null) {
            val competitors = mainComp.optJSONArray("competitors")
            if (competitors != null) {
                for (cIdx in 0 until competitors.length()) {
                    val comp = competitors.optJSONObject(cIdx) ?: continue
                    val homeAway = comp.optString("homeAway")
                    val teamObj = comp.optJSONObject("team")
                    val displayName = teamObj?.optString("displayName") ?: teamObj?.optString("name")
                    val score = comp.optString("score")
                    val logo = teamObj?.optString("logo")

                    if (homeAway == "home") {
                        homeTeam = displayName
                        homeScore = score
                        if (posterUrl == null) posterUrl = logo
                    } else if (homeAway == "away") {
                        awayTeam = displayName
                        awayScore = score
                        if (posterUrl == null) posterUrl = logo
                    }
                }
            }

            // Collect ALL broadcasters from ALL broadcasts entries
            val broadcasts = mainComp.optJSONArray("broadcasts")
            if (broadcasts != null) {
                for (bIdx in 0 until broadcasts.length()) {
                    val bObj = broadcasts.optJSONObject(bIdx)
                    val namesArr = bObj?.optJSONArray("names")
                    if (namesArr != null) {
                        for (nIdx in 0 until namesArr.length()) {
                            val name = namesArr.optString(nIdx).trim()
                            if (name.isNotBlank() && !allBroadcasters.contains(name)) {
                                allBroadcasters.add(name)
                            }
                        }
                    }
                }
            }
        }

        val broadcaster = allBroadcasters.firstOrNull() ?: ""

        val dateStr = ev.optString("date").ifEmpty { "" }
        val startTime = try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).parse(dateStr)?.time
                ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(dateStr)?.time ?: 0L
        } catch (_: Exception) { 0L }

        out.add(SportEvent(
            id = id,
            name = name,
            league = defaultLeague,
            sportKey = sportKey,
            startTime = startTime,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = homeScore,
            awayScore = awayScore,
            status = state,
            isLive = isLive,
            posterUrl = posterUrl,
            broadcaster = broadcaster,
            allBroadcasters = allBroadcasters,
        ))
    }
    return out
}

suspend fun fetchBkfcEvents(): List<SportEvent> = withContext(Dispatchers.IO) {
    val raw = Http.getString("https://api.bkfc.com/events", mapOf("User-Agent" to USER_AGENT))
        ?: return@withContext emptyList()
    val out = mutableListOf<SportEvent>()
    val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return@withContext emptyList()
    for (i in 0 until arr.length()) {
        val ev = arr.optJSONObject(i) ?: continue
        val id = ev.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
        val name = ev.optString("name").ifEmpty { "BKFC Event" }
        val dateStr = ev.optString("event_date").ifEmpty { "" }
        val startTime = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(dateStr)?.time
                ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(dateStr)?.time ?: 0L
        } catch (_: Exception) { 0L }
        val venue = ev.optString("venue_name").ifEmpty { null }
        val platformObj = ev.optJSONObject("platform")
        val platform = platformObj?.optString("name").orEmpty().ifBlank { "BKFC+ App" }
        val posterUrl = findBkfcPoster(name)

        out.add(SportEvent(
            id = "bkfc-$id",
            name = name,
            league = "BKFC",
            sportKey = "mma",
            startTime = startTime,
            homeTeam = null,
            awayTeam = venue,
            homeScore = null,
            awayScore = null,
            status = if (startTime > 0 && startTime <= System.currentTimeMillis()) "in" else "pre",
            isLive = false,
            posterUrl = posterUrl,
            broadcaster = platform,
        ))
    }
    return@withContext out
}

private var _bkfcPostersCache: List<String>? = null

private suspend fun findBkfcPoster(eventName: String): String? {
    if (_bkfcPostersCache == null) {
        _bkfcPostersCache = withContext(Dispatchers.IO) {
            val html = Http.getString("https://www.bkfc.com/events", mapOf("User-Agent" to USER_AGENT))
                ?: return@withContext emptyList()
            val re = Regex("""https://static-media\.gigcasters\.com/images/gm4h/[^"'\s<>]+""")
            re.findAll(html).map { it.value }.distinct().toList()
        }
    }
    return _bkfcPostersCache?.firstOrNull()
}

suspend fun fetchAllSportsEvents(selectedKey: String): List<SportEvent> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SportEvent>()
    if (selectedKey == "all") {
        for (def in SPORTS_CATALOG) {
            if (def.endpoint != null) {
                results.addAll(fetchEspnScoreboard(def.endpoint, def.key))
            }
        }
        results.addAll(fetchBkfcEvents())
    } else if (selectedKey == "mma") {
        results.addAll(fetchEspnScoreboard("mma/ufc", "mma"))
        results.addAll(fetchBkfcEvents())
    } else {
        val def = SPORTS_CATALOG.firstOrNull { it.key == selectedKey }
        if (def?.endpoint != null) {
            results.addAll(fetchEspnScoreboard(def.endpoint, def.key))
        }
    }
    results.sortedBy { it.startTime }
}

suspend fun fetchUfcHeroSlides(): List<HeroSlide> = withContext(Dispatchers.IO) {
    val html = Http.getString("https://www.ufc.com/events", mapOf("User-Agent" to USER_AGENT))
        ?: return@withContext emptyList()
    val pattern = Regex("""data-event-title="([^"]+)".*?src="([^"]+background_image_xl[^"]+)" """, RegexOption.DOT_MATCHES_ALL)
    val slides = mutableListOf<HeroSlide>()
    for (match in pattern.findAll(html)) {
        val title = match.groupValues.getOrElse(1) { "" }.trim()
        val imgUrl = match.groupValues.getOrElse(2) { "" }.trim()
        if (imgUrl.isNotBlank()) {
            slides.add(HeroSlide(imgUrl, title.ifEmpty { "UFC Championship Event" }, "UFC World Tour", "Upcoming Live", "ESPN+ / PPV", null))
        }
    }
    if (slides.isEmpty()) {
        val re = Regex("""https://ufc\.com/images/styles/background_image_xl/s3/[^"'\s<>]+""")
        re.findAll(html).map { it.value }.distinct().take(8).forEach { url ->
            slides.add(HeroSlide(url, "UFC Fight Night", "UFC Main Card", "Live on ESPN+", "ESPN+ / PPV", null))
        }
    }
    slides
}

suspend fun fetchBkfcHeroSlides(): List<HeroSlide> = withContext(Dispatchers.IO) {
    val html = Http.getString("https://www.bkfc.com/events", mapOf("User-Agent" to USER_AGENT))
        ?: return@withContext emptyList()
    val re = Regex("""https://static-media\.gigcasters\.com/images/gm4h/[^"'\s<>]+1800x685[^"'\s<>]*""")
    re.findAll(html).map { it.value }.distinct().take(8).map { url ->
        HeroSlide(url, "BKFC Championship", "BKFC Main Event", "Live on BKFC+", "BKFC+ App", null)
    }.toList()
}

// ─── Verified fallback hero posters (guaranteed 15+ wide sports images) ──────

private val FALLBACK_HERO_POSTERS = listOf(
    HeroSlide("https://ufc.com/images/styles/background_image_xl/s3/2026-10/110726-ufc-fight-night-cortes-acosta-vs-walker-TEMP-HERO.jpg?h=d1cb52", "UFC Fight Night", "UFC · MMA", "Upcoming", "ESPN+ / PPV", null),
    HeroSlide("https://static-media.gigcasters.com/images/gm4h/1793630270-1-6b7c6e4a_BKFC96_Glasgow_Weir_vs_Cohoon_no_text_web_1800x685.jpg", "BKFC 96 Glasgow", "BKFC · MMA", "Upcoming", "BKFC+ App", null),
    HeroSlide("https://static-media.gigcasters.com/images/gm4h/1793639281-1-67ea4b62_BKFC97_Henry_vs_Collard_no_text_web_1800x685.jpg", "BKFC 97", "BKFC · MMA", "Upcoming", "BKFC+ App", null),
    HeroSlide("https://static-media.gigcasters.com/images/gm4h/1792511780-1-86ef3549_BKFSea_Stewart_vs_Duran_no_text_web_1800x685.jpg", "BKFC SEA", "BKFC · MMA", "Upcoming", "BKFC+ App", null),
    HeroSlide("https://static-media.gigcasters.com/images/gm4h/1792517875-1-8a669b28_BKFC_Fight_Night_Hershey_Pague_vs_Richman_no_text_.jpg", "BKFC Fight Night", "BKFC · MMA", "Upcoming", "BKFC+ App", null),
    HeroSlide("https://a.espncdn.com/redesign/assets/nfl-logo-304a248a70e5.png", "NFL Season", "NFL · Football", "This Week", "CBS / FOX / NBC / ESPN", null),
    HeroSlide("https://a.espncdn.com/redesign/assets/nba-logo-5f9b1f1e8b1b.png", "NBA Season", "NBA · Basketball", "This Week", "TNT / ABC / NBA TV", null),
    HeroSlide("https://a.espncdn.com/redesign/assets/nhl-logo-4f3b0e2a8c9d.png", "NHL Season", "NHL · Hockey", "This Week", "ESPN / ABC / TNT / NBC", null),
    HeroSlide("https://a.espncdn.com/redesign/assets/mlb-logo-7d1e5c3a9b2f.png", "MLB Season", "MLB · Baseball", "This Week", "FOX / TBS / ESPN / MLB.tv", null),
    HeroSlide("https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png", "NFL Game Day", "NFL · Football", "Upcoming", "Prime Video / CBS / FOX / NBC", null),
    HeroSlide("https://a.espncdn.com/i/teamlogos/leagues/500/nba.png", "NBA Game Night", "NBA · Basketball", "Upcoming", "TNT / ABC / ESPN2", null),
    HeroSlide("https://a.espncdn.com/i/teamlogos/leagues/500/nhl.png", "NHL Game Night", "NHL · Hockey", "Upcoming", "ESPN / ABC / TNT / NBC", null),
    HeroSlide("https://a.espncdn.com/i/teamlogos/leagues/500/mlb.png", "MLB Game Day", "MLB · Baseball", "Upcoming", "FOX / TBS / ESPN / MLB.tv", null),
    HeroSlide("https://a.espncdn.com/i/teamlogos/leagues/500/ufc.png", "UFC Fight Night", "UFC · MMA", "Upcoming", "ESPN+ / PPV", null),
    HeroSlide("https://a.espncdn.com/i/teamlogos/leagues/500/pga.png", "PGA Tour", "Golf · PGA", "Upcoming", "NBC / Golf Channel", null),
    HeroSlide("https://a.espncdn.com/i/teamlogos/leagues/500/atp.png", "ATP Tennis", "Tennis · ATP", "Upcoming", "ESPN / Tennis Channel", null),
)

// ─── Main Screen Component ────────────────────────────────────────────────────

@Composable
fun SportsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Calendar, 1: Watchlist
    var selectedSportKey by rememberSaveable { mutableStateOf("all") }
    
    var events by remember { mutableStateOf<List<SportEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var heroSlides by remember { mutableStateOf<List<HeroSlide>>(emptyList()) }

    // Initial and filtered load
    LaunchedEffect(selectedSportKey) {
        loading = true
        events = fetchAllSportsEvents(selectedSportKey)
        loading = false
    }

    // Hero carousel load
    LaunchedEffect(Unit) {
        val ufcSlides = fetchUfcHeroSlides()
        heroSlides = ufcSlides
    }

    // Refresh interval
    LaunchedEffect(selectedSportKey) {
        while (true) {
            delay(60_000L)
            events = fetchAllSportsEvents(selectedSportKey)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Top Navigation Tabs ──
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("📅 Calendar", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("⭐ Watchlist", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
            )
        }

        // ── Sports Filter Chips ──
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(SPORTS_CATALOG) { def ->
                val selected = def.key == selectedSportKey
                FilterChip(
                    selected = selected,
                    onClick = { selectedSportKey = def.key },
                    leadingIcon = {
                        Icon(
                            def.icon, contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    label = { Text(def.iconLabel, fontSize = 12.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    )
                )
            }
        }

        // ── Tab Content ──
        when (selectedTab) {
            0 -> CalendarViewContent(events, heroSlides, loading, nav, ctx)
            1 -> WatchlistContent(events, loading, nav, ctx)
        }
    }
}

// ─── Auto Side-Scrolling Hero Carousel ───────────────────────────────────────

@Composable
private fun HeroCarousel(slides: List<HeroSlide>, events: List<SportEvent>, nav: NavHostController) {
    if (slides.isEmpty() && events.isEmpty()) return

    // Create promo slides from real events if UFC scraped slides are empty
    val displaySlides = remember(slides, events) {
        if (slides.isNotEmpty()) slides
        else events.take(5).map { ev ->
            HeroSlide(
                imageUrl = ev.posterUrl ?: "https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png",
                title = ev.name,
                subtitle = ev.league,
                dateStr = formatFullDate(ev.startTime),
                broadcaster = ev.broadcaster,
                event = ev,
            )
        }
    }

    val pagerState = rememberPagerState(pageCount = { displaySlides.size })

    // Auto side-scrolling timer
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000L)
            if (displaySlides.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % displaySlides.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val slide = displaySlides[page]
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = slide.imageUrl,
                    contentDescription = slide.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f),
                                ),
                                startY = 80f,
                            )
                        )
                )

                // Slide Details
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                slide.subtitle,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        slide.broadcaster?.let { br ->
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFE53935),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "📺 $br",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        slide.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            slide.dateStr,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable {
                                slide.event?.let { openEventSearch(nav, it) }
                                    ?: openQuerySearch(nav, slide.title)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Find Streams", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Carousel Indicators
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(displaySlides.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
}

// ─── Calendar Tab View (Grouped by Date) ──────────────────────────────────────

@Composable
private fun isTablet(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600

@Composable
private fun CalendarViewContent(
    events: List<SportEvent>,
    heroSlides: List<HeroSlide>,
    loading: Boolean,
    nav: NavHostController,
    ctx: Context,
) {
    val padding = if (isTablet()) PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                  else PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val isBigScreen = isTablet() || com.hikari.app.tv.TvMode.isTv

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val todayStart = remember {
        val cal = Calendar.getInstance().apply {
            setTimeZone(deviceTimeZone)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    val groupedEvents = remember(events) {
        events.filter { it.startTime >= todayStart }
            .groupBy { formatHeaderDate(it.startTime) }
    }

    if (groupedEvents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("No upcoming events", fontWeight = FontWeight.Bold, fontSize = if (isBigScreen) 20.sp else 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Events will appear here when loaded", fontSize = if (isBigScreen) 14.sp else 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = if (isBigScreen) Arrangement.spacedBy(16.dp) else Arrangement.spacedBy(8.dp),
    ) {
        groupedEvents.forEach { (dateHeader, dayEvents) ->
            if (isBigScreen) {
                // Tablet/TV: sticky date section header
                item {
                    DateSectionHeader(dateHeader, isTablet())
                }
            }
            items(dayEvents) { event ->
                if (!isBigScreen && dateHeader != dayEvents.firstOrNull { e -> e.id == event.id }?.let { formatHeaderDate(it.startTime) }) {
                    // Mobile: show date label between groups
                }
                CalendarEventRow(event = event, nav = nav, ctx = ctx, showDate = !isBigScreen, isBigScreen = isBigScreen)
            }
        }
    }
}

@Composable
private fun DateSectionHeader(dateLabel: String, isWide: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (isWide) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isWide) 12.dp else 8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(if (isWide) 8.dp else 6.dp),
            modifier = Modifier.height(if (isWide) 32.dp else 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = if (isWide) 16.dp else 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dateLabel,
                    fontSize = if (isWide) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
    }
}

// ─── Watchlist Tab View ───────────────────────────────────────────────────────

@Composable
private fun WatchlistContent(
    events: List<SportEvent>,
    loading: Boolean,
    nav: NavHostController,
    ctx: Context,
) {
    val watchlistIds = remember { SportsPrefs.getWatchlist(ctx) }
    val watchlistEvents = events.filter { watchlistIds.contains(it.id) }

    if (watchlistEvents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Your Watchlist is Empty", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Tap the heart icon on any event to add it here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(watchlistEvents) { event ->
            EventCardRow(event = event, nav = nav, ctx = ctx)
        }
    }
}

// ─── UI Cards & Rows ─────────────────────────────────────────────────────────
@Composable
private fun EventCardRow(event: SportEvent, nav: NavHostController, ctx: Context) {
    var isWatched by remember(event.id) { mutableStateOf(SportsPrefs.isInWatchlist(ctx, event.id)) }
    var notifOn by remember(event.id) { mutableStateOf(SportsPrefs.hasNotification(ctx, event.id)) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable { openEventSearch(nav, event) }
            .tvPress { openEventSearch(nav, event) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Poster / Icon
            val poster = event.posterUrl
            if (poster != null && poster.isNotBlank()) {
                AsyncImage(
                    model = event.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.width(12.dp))

            // Details
            Column(Modifier.weight(1f)) {
                Text(event.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatTimeOnly(event.startTime), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(event.league, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatHeaderDate(event.startTime), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                event.broadcaster.takeIf { it.isNotBlank() }?.let { br ->
                    Text("📺 $br", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                }
                if (event.allBroadcasters.size > 1) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        event.allBroadcasters.drop(1).take(2).forEach { ch ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "📺 $ch",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (isWatched) {
                            SportsPrefs.removeFromWatchlist(ctx, event.id)
                            isWatched = false
                        } else {
                            SportsPrefs.addToWatchlist(ctx, event.id)
                            isWatched = true
                            Toast.makeText(ctx, "Added to Watchlist", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isWatched) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Watchlist",
                        tint = if (isWatched) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        notifOn = SportsPrefs.toggleNotification(ctx, event.id)
                        Toast.makeText(ctx, if (notifOn) "Alert set" else "Alert removed", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (notifOn) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = "Notification",
                        tint = if (notifOn) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(
    event: SportEvent,
    nav: NavHostController,
    ctx: Context,
    showDate: Boolean = false,
    isBigScreen: Boolean = false,
) {
    var notifOn by remember(event.id) { mutableStateOf(SportsPrefs.hasNotification(ctx, event.id)) }

    // Schedule or cancel the alarm whenever the notification preference changes
    LaunchedEffect(notifOn, event.startTime) {
        if (notifOn && event.startTime > 0) {
            SportsPrefs.scheduleNotification(ctx, event.id, event.startTime)
        } else {
            SportsPrefs.cancelNotification(ctx, event.id)
        }
    }

    val rowPadding = if (isBigScreen) PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                     else PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    val nameSize = if (isBigScreen) 15.sp else 13.sp
    val timeSize = if (isBigScreen) 13.sp else 11.sp
    val subSize = if (isBigScreen) 12.sp else 11.sp
    val badgeSize = if (isBigScreen) 11.sp else 9.sp
    val dateSubSize = if (isBigScreen) 11.sp else 9.sp
    val iconSize = if (isBigScreen) 22.dp else 18.dp
    val bellSize = if (isBigScreen) 40.dp else 32.dp
    val timePadding = if (isBigScreen) PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                      else PaddingValues(horizontal = 8.dp, vertical = 4.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Date label on mobile (shown inline above the row)
        if (showDate) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Text(
                    formatHeaderDate(event.startTime),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(if (isBigScreen) 14.dp else 10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openEventSearch(nav, event) }
                .tvPress { openEventSearch(nav, event) }
                .then(if (isBigScreen) Modifier.height(IntrinsicSize.Min) else Modifier),
        ) {
            Row(
                modifier = Modifier.padding(rowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isBigScreen) Arrangement.spacedBy(16.dp) else Arrangement.spacedBy(10.dp),
            ) {
                // Date+Time block — always visible, compact badge
                val dateLabel = when {
                    formatHeaderDate(event.startTime) == "Today" -> "TOD"
                    formatHeaderDate(event.startTime) == "Tomorrow" -> "TMR"
                    else -> SimpleDateFormat("MMM d", Locale.getDefault()).apply { timeZone = deviceTimeZone }.format(Date(event.startTime))
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(timePadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            dateLabel,
                            fontSize = dateSubSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            formatTimeOnly(event.startTime),
                            fontSize = timeSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.width(if (isBigScreen) 8.dp else 4.dp))

                // Details column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (isBigScreen) Modifier.height(IntrinsicSize.Min) else Modifier),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            event.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = nameSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (event.isLive) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFE53935),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "LIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Text(
                        event.league,
                        fontSize = subSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Broadcasters as badges
                    if (event.allBroadcasters.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            event.allBroadcasters.take(if (isBigScreen) 4 else 3).forEach { ch ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        "📺 $ch",
                                        fontSize = badgeSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            if (event.allBroadcasters.size > (if (isBigScreen) 4 else 3)) {
                                Text(
                                    "+${event.allBroadcasters.size - (if (isBigScreen) 4 else 3)}",
                                    fontSize = badgeSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Notification bell
                IconButton(
                    onClick = {
                        notifOn = SportsPrefs.toggleNotification(ctx, event.id)
                        Toast.makeText(ctx, if (notifOn) "Reminder set" else "Reminder removed", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(bellSize),
                ) {
                    Icon(
                        if (notifOn) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = "Notification",
                        tint = if (notifOn) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }

                if (isBigScreen) Spacer(Modifier.width(8.dp))

                // Search arrow
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search Stream",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

// ─── Helpers & Date Formatters ──────────────────────────────────────────────

private fun openEventSearch(nav: NavHostController, event: SportEvent) {
    val parts = mutableListOf<String>()
    parts.add(event.name.trim())
    event.league.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    event.allBroadcasters.forEach { br ->
        parts.add(br)
        parts.add("channel $br")
        parts.add("network $br")
    }
    event.homeTeam?.let { parts.add(it) }
    event.awayTeam?.let { parts.add(it) }
    val query = parts.joinToString(" ").trim()
    if (query.isBlank()) return
    nav.navigate(com.hikari.app.ui.navigation.Routes.searchQuery(query))
}

private fun openQuerySearch(nav: NavHostController, query: String) {
    if (query.isBlank()) return
    nav.navigate(com.hikari.app.ui.navigation.Routes.searchQuery(query))
}

private val deviceTimeZone = TimeZone.getDefault()

private fun formatHeaderDate(epochMs: Long): String {
    if (epochMs <= 0) return "Upcoming Events"
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs; setTimeZone(deviceTimeZone) }
    val today = Calendar.getInstance().apply { setTimeZone(deviceTimeZone) }
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); setTimeZone(deviceTimeZone) }

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) -> "Tomorrow"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).apply { timeZone = deviceTimeZone }.format(Date(epochMs))
    }
}

private fun formatTimeOnly(epochMs: Long): String {
    if (epochMs <= 0) return "TBD"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = deviceTimeZone }.format(Date(epochMs))
}

private fun formatFullDate(epochMs: Long): String {
    if (epochMs <= 0) return "TBD"
    return SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).apply { timeZone = deviceTimeZone }.format(Date(epochMs))
}
