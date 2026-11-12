package com.hikari.app.ui.tutorial

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hikari.app.ui.navigation.Routes
import kotlinx.coroutines.delay

/**
 * One step of a first-visit tour.
 *
 * The tours are deliberately short and screen-specific: a page teaches the two
 * or three things it can do that are not obvious, then stays out of the way.
 * Everything a page does in detail belongs to Settings → About & Updates →
 * Feature guide, which is where the replay buttons live as well.
 */
data class TutorialPage(val title: String, val body: String)

data class Tutorial(
    val id: String,
    val title: String,
    val routes: List<String>,
    val pages: List<TutorialPage>,
)

/** Whether a tour has been shown, kept on this device. */
object TutorialStore {
    private const val PREFS = "tutorial_seen"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSeen(context: Context, id: String) = prefs(context).getBoolean(id, false)

    fun markSeen(context: Context, id: String) =
        prefs(context).edit().putBoolean(id, true).apply()

    fun reset(context: Context, id: String) =
        prefs(context).edit().remove(id).apply()

    fun resetAll(context: Context) = prefs(context).edit().clear().apply()
}

/**
 * Every tour, in the order Settings lists them. Routes match the navigation
 * patterns in [Routes]; a route with arguments matches by its base.
 */
object Tutorials {

    val all: List<Tutorial> = listOf(
        Tutorial(
            "home",
            "Home",
            listOf(Routes.HOME),
            listOf(
                TutorialPage(
                    "Everything starts here",
                    "Featured titles at the top, then the shelves from your installed providers. Continue Watching keeps the exact place you stopped, across every extension and every tab.",
                ),
                TutorialPage(
                    "One search for everything",
                    "The search button looks through every extension, IPTV playlist and add-on at once. Pick a provider first to search only inside it.",
                ),
                TutorialPage(
                    "Make it yours",
                    "Settings → Appearance & Theme changes the accent, the poster shape and the banner style. Settings → Taskbar buttons decides which tabs the bar shows.",
                ),
            ),
        ),
        Tutorial(
            "search",
            "Search",
            listOf(Routes.SEARCH),
            listOf(
                TutorialPage(
                    "Search everything",
                    "Type a title and Hikari asks every enabled provider at the same time. Results stream in as each provider answers, so a slow one never holds up the rest.",
                ),
                TutorialPage(
                    "Narrow it down",
                    "Pick a provider, or search inside one extension with its scoped search. Tap a result to open its detail page; long-press to add it to a collection.",
                ),
            ),
        ),
        Tutorial(
            "sports",
            "Sports",
            listOf(Routes.SPORTS),
            listOf(
                TutorialPage(
                    "Follow what you watch",
                    "Tap the star on a league or team so its games appear on the Sports tab. The Scores rail covers every league, followed or not.",
                ),
                TutorialPage(
                    "Live and upcoming",
                    "An event card opens its game page: broadcasters, a Watch on search across your playlists and add-ons, and the streams that actually match this fixture.",
                ),
                TutorialPage(
                    "After the whistle",
                    "Finished games drop Watch on and offer replays and highlights instead. The Replays tab keeps recent UFC, boxing, football and racing cards.",
                ),
            ),
        ),
        Tutorial(
            "iptv",
            "Live TV",
            listOf(Routes.IPTV),
            listOf(
                TutorialPage(
                    "Add a source",
                    "Use + to add an M3U link, a file, or an Xtream portal. Xtream portals are validated before they are saved and show their channel and group counts.",
                ),
                TutorialPage(
                    "Generate portals",
                    "Generate portals checks Cloud Vault and Reddit and keeps 15 working Xtream accounts per run. They wait under Portal search results — tap Add on the ones you want. Every Xtream tile also wears its expiry date.",
                ),
                TutorialPage(
                    "Know what is on",
                    "Each Xtream account shows its expiry date, with a warning when it is close. The On now strip reads the panel's own XMLTV guide, so you can see the current programme without opening a channel.",
                ),
                TutorialPage(
                    "Quick Ch and MultiView",
                    "In the player, Quick Ch jumps to a curated pattern (news, sports, a network) inside the playlist you are watching. MultiView shows four channels on a phone in portrait, and a tablet view can use up to nine windows.",
                ),
            ),
        ),
        Tutorial(
            "channel-wall",
            "Channel Wall",
            listOf(Routes.CHANNEL_WALL),
            listOf(
                TutorialPage(
                    "A wall of what you can watch",
                    "The wall is a curated directory of channels. Each tile asks every playlist, portal and add-on for its best match and opens the stream in Hikari's player.",
                ),
                TutorialPage(
                    "Full-bleed artwork",
                    "Every tile — curated or one you add — uses a full-bleed channel icon or promo image behind its name, so the wall reads as a wall of programmes rather than a list of logos. Tap a tile to see every source found for it.",
                ),
                TutorialPage(
                    "Favorites and new shelves",
                    "Long-press any tile to add it to Favorites (long-press again to remove it); the shelf stays at the top only while it has channels. The wall also carries Music, Anime, Lifestyle, Black Media and an Adult shelf that appears only while NSFW is enabled.",
                ),
            ),
        ),
        Tutorial(
            "youtube",
            "YouTube",
            listOf(Routes.YOUTUBE),
            listOf(
                TutorialPage(
                    "Your feeds",
                    "Home, Live, Music, Gaming, News and Sports are ready without signing in. Signing in adds Subscriptions, History, Playlists and Channels.",
                ),
                TutorialPage(
                    "Save and like",
                    "The ⋮ menu on any video card saves it to Watch later or likes it. Both lists live on this device and are collected on the You tab — no account needed.",
                ),
                TutorialPage(
                    "Plays in Hikari",
                    "Videos open in Hikari's own player, with the quality picker, subtitles and SponsorBlock you already use for everything else.",
                ),
            ),
        ),
        Tutorial(
            "extensions",
            "Extensions",
            listOf(Routes.EXTENSIONS),
            listOf(
                TutorialPage(
                    "Add a repository",
                    "Repositories are added by URL, from a file, or from a shared link. CloudStream plugins, Nuvio scrapers and Stremio add-ons are installed into the section they belong to.",
                ),
                TutorialPage(
                    "If a provider fails",
                    "A dead site, a Cloudflare wall and a broken plugin look different on the card. Open the provider's own site in the in-app browser to clear a challenge, then retry.",
                ),
            ),
        ),
        Tutorial(
            "library",
            "My Stuff",
            listOf(Routes.LIBRARY, Routes.HISTORY, Routes.DOWNLOADS),
            listOf(
                TutorialPage(
                    "One library",
                    "Saved titles, watch history and downloads share one page and one set of filters. Continue Watching on Home is the same history, reduced to what you have not finished.",
                ),
                TutorialPage(
                    "Offline when you need it",
                    "Downloads play without a connection. A downloaded title also remembers its place, so resuming works exactly like streaming.",
                ),
            ),
        ),
        Tutorial(
            "music",
            "Music",
            listOf(Routes.MUSIC),
            listOf(
                TutorialPage(
                    "Browse and play",
                    "Charts, search, radio and your library are on one page. The mini player stays with you while you move around the app.",
                ),
                TutorialPage(
                    "Your library",
                    "Liked songs and playlists are kept on this device. Opening the full player gives you the queue, shuffle and repeat.",
                ),
            ),
        ),
        Tutorial(
            "websites",
            "Web Apps",
            listOf(Routes.WEBSITES),
            listOf(
                TutorialPage(
                    "Turn sites into web apps",
                    "Save the sites you use as web apps for one-tap access — turn any site into an app to watch more media your way. Pages open in Hikari's in-app browser, with ad blocking, redirect protection and the same cookies as extension playback.",
                ),
                TutorialPage(
                    "At home in the browser",
                    "The address bar opens a URL, or searches Google when you type a phrase instead of an address. The ⋯ menu adds desktop mode, a video detector and Open in browser for pages that need a full browser.",
                ),
            ),
        ),
        Tutorial(
            "telegram",
            "Telegram",
            listOf(Routes.TELEGRAM),
            listOf(
                TutorialPage(
                    "Public channels",
                    "Add a public channel by its link or handle. Videos are listed here and play in Hikari's player; downloads go through the app's download engine.",
                ),
            ),
        ),
        Tutorial(
            "manga",
            "Manga",
            listOf(Routes.MANGA),
            listOf(
                TutorialPage(
                    "Read in the app",
                    "Browse an engine's catalog, follow a title and download chapters. The reader keeps the page position when you rotate or come back.",
                ),
            ),
        ),
        Tutorial(
            "detail",
            "Title pages",
            listOf(Routes.DETAIL),
            listOf(
                TutorialPage(
                    "Pick a source",
                    "The Play button asks every installed provider for servers. Each server row shows its quality, codec and size; tap one to start.",
                ),
                TutorialPage(
                    "Episodes, trailers and more",
                    "The season picker, trailer, cast and recommendations all live on the same page. The heart saves the title to My Stuff; the download button keeps it offline.",
                ),
            ),
        ),
        Tutorial(
            "multi-view",
            "MultiView",
            listOf(Routes.MULTI_VIEW),
            listOf(
                TutorialPage(
                    "Several channels at once",
                    "Tap a cell to change its stream, mute it or make it fullscreen. A phone in portrait holds four windows; rotate it or open MultiView on a tablet for grids of up to nine.",
                ),
            ),
        ),
        Tutorial(
            "settings",
            "Settings",
            listOf(Routes.SETTINGS),
            listOf(
                TutorialPage(
                    "Find any setting",
                    "The search box searches every card, folder and switch. Folders open in place, and Back steps out one level instead of leaving Settings.",
                ),
                TutorialPage(
                    "About & Updates",
                    "Version, update check, feature guide and these tutorials are all under About & Updates. Replay a tour from there at any time.",
                ),
            ),
        ),
    )

    fun forRoute(route: String?): Tutorial? {
        if (route.isNullOrBlank()) return null
        val base = route.substringBefore('?')
        return all.firstOrNull { tutorial ->
            tutorial.routes.any { r ->
                val rb = r.substringBefore('?')
                base == rb || base.startsWith("$rb/") ||
                    (rb != Routes.HOME && base.startsWith(rb + "?"))
            }
        }
    }
}

/**
 * The first-visit tour for the page currently on screen. A tour is shown once
 * per device; dismissing it (either button) marks it seen.
 */
@Composable
fun FirstVisitTutorial(route: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tutorial = remember(route) { Tutorials.forRoute(route) } ?: return
    var visible by remember(tutorial.id) { mutableStateOf(false) }
    LaunchedEffect(tutorial.id) {
        if (TutorialStore.hasSeen(context, tutorial.id)) return@LaunchedEffect
        // Let the page lay itself out first; a tour over a half-built screen
        // explains the wrong thing.
        delay(650)
        visible = true
    }
    if (visible) {
        TutorialDialog(tutorial) {
            TutorialStore.markSeen(context, tutorial.id)
            visible = false
        }
    }
}

/** The tour card itself, also used by the replay buttons in Settings. */
@Composable
fun TutorialDialog(tutorial: Tutorial, onDismiss: () -> Unit) {
    var page by remember(tutorial.id) { mutableIntStateOf(0) }
    val current = tutorial.pages.getOrNull(page) ?: tutorial.pages.first()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            tutorial.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Quick tour · ${page + 1} of ${tutorial.pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Skip") }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    current.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tutorial.pages.indices.forEach { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index <= page) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (page > 0) {
                        TextButton(onClick = { page-- }) { Text("Back") }
                    }
                    Spacer(Modifier.weight(1f))
                    if (page < tutorial.pages.lastIndex) {
                        Button(onClick = { page++ }) { Text("Next") }
                    } else {
                        Button(onClick = onDismiss) { Text("Got it") }
                    }
                }
            }
        }
    }
}
