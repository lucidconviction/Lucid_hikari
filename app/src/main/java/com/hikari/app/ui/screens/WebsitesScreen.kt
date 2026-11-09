package com.hikari.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.hikari.app.HikariApp
import com.hikari.app.data.Site
import com.hikari.app.i18n.I18n
import com.hikari.app.i18n.tr
import com.hikari.app.web.WebAppShots
import com.hikari.app.web.WebViewActivity
import kotlinx.coroutines.launch

/**
 * The Websites tab: a one-tap home for the user's saved ad-free WebView
 * sites, plus a universal address bar that opens any URL in the same browser
 * that extensions use. Sites are kept in [HikariApp.store] alongside
 * extensions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsitesScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val sites by app.store.sitesFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<Site?>(null) }
    var isGrid by remember { mutableStateOf(true) }

    /** A valid web address with a scheme, or null when it is not one. */
    fun normalizeSiteUrl(raw: String): String? {
        var value = raw.trim()
        if (value.isBlank()) return null
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "https://$value"
        }
        val host = runCatching { java.net.URI(value).host }.getOrNull().orEmpty()
        if (host.isBlank() || (!host.contains('.') && host != "localhost" &&
                !host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")))) {
            return null
        }
        return value
    }

    /** Opens the name-only add dialog. The URL is the address bar's, as asked:
     *  no second URL field to paste into. */
    fun openAddDialog() {
        addError = null
        addName = ""
        addUrl = urlInput.trim()
        showAddDialog = true
    }

    fun saveSite() {
        val normalized = normalizeSiteUrl(addUrl.ifBlank { urlInput })
        if (normalized == null) {
            addError = I18n.t("Enter a website address, for example example.com")
            return
        }
        val name = addName.trim().ifEmpty {
            runCatching { java.net.URI(normalized).host }.getOrNull()
                ?.removePrefix("www.")
                ?.ifBlank { normalized }
                ?: normalized
        }
        // The app-wide scope, not the screen's: a save started as the dialog
        // closes must not be cancelled by that recomposition, which is what
        // made a site occasionally vanish / never appear in the list.
        app.appScope.launch { app.store.addSite(Site(name, normalized)) }
        showAddDialog = false
        addName = ""
        addUrl = ""
        urlInput = ""
        addError = null
    }

    /**
     * What the address bar opens.
     *
     * A real address opens as usual. Anything that is not one — a search phrase,
     * a bare word, or an empty bar — goes to Google instead of failing as an
     * "https://" URL, which is what made the bar look broken for ordinary
     * searches.
     */
    fun barTarget(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return "https://www.google.com"
        normalizeSiteUrl(value)?.let { return it }
        val query = runCatching {
            java.net.URLEncoder.encode(value, "UTF-8")
        }.getOrDefault(value)
        return "https://www.google.com/search?q=$query"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Web Apps") + " · ${sites.size}") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { isGrid = !isGrid }) {
                        Icon(
                            if (isGrid) Icons.Filled.ViewList else Icons.Filled.GridOn,
                            contentDescription = if (isGrid) tr("List view") else tr("Grid view"),
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            // Address bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text(tr("Paste a URL or search Google"), color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("websites_url_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val raw = urlInput.trim()
                            urlInput = ""
                            context.startActivity(
                                Intent(context, WebViewActivity::class.java).apply {
                                    putExtra("url", barTarget(raw))
                                    putExtra("title", I18n.t("Go"))
                                }
                            )
                        }
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                        .clickable {
                            // The + between the search bar and the globe saves
                            // the site IN THE ADDRESS BAR: it asks only for a
                            // name, never a second URL.
                            openAddDialog()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = tr("Save to Web Apps"), tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                        .clickable {
                            val raw = urlInput.trim()
                            urlInput = ""
                            context.startActivity(
                                Intent(context, WebViewActivity::class.java).apply {
                                    putExtra("url", barTarget(raw))
                                    putExtra("title", I18n.t("Go"))
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                tr("Saved web apps"),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (sites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr("No web apps added yet — add one below to open any site ad-free."),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            onClick = { openAddDialog() },
                            shape = RoundedCornerShape(50),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Add web app"), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                if (isGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(sites, key = { it.url }) { site ->
                            SiteGridCard(site = site, onOpen = {
                                context.startActivity(
                                    Intent(context, WebViewActivity::class.java).apply {
                                        putExtra("url", site.url)
                                        putExtra("title", site.name)
                                    }
                                )
                            }, onDelete = { removeTarget = site })
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sites, key = { it.url }) { site ->
                            SiteCard(site = site, onOpen = {
                                context.startActivity(
                                    Intent(context, WebViewActivity::class.java).apply {
                                        putExtra("url", site.url)
                                        putExtra("title", site.name)
                                    }
                                )
                            }, onDelete = { removeTarget = site })
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(tr("Add web app")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The URL is the one in the search bar, shown here read-only
                    // so it is obvious what "+" is about to save.
                    Text(
                        addUrl.ifBlank { I18n.t("Paste the website address in the search bar first.") },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = if (addUrl.isBlank()) androidx.compose.material3.MaterialTheme.colorScheme.error
                        else Color.White.copy(alpha = 0.65f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text(tr("Web app name")) },
                        singleLine = true,
                        isError = addError != null,
                        supportingText = addError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveSite() }),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalizeSiteUrl(addUrl) != null,
                    onClick = { saveSite() },
                ) {
                    Text(tr("Add"), color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    addError = null
                }) {
                    Text(tr("Cancel"), color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }

    removeTarget?.let { site ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(tr("Remove web app")) },
            text = { Text(tr("Remove \"{}\"?").replace("{}", site.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val app = context.applicationContext as HikariApp
                        app.store.removeSite(site.url)
                    }
                    removeTarget = null
                }) {
                    Text(tr("Delete"), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(tr("Cancel"), color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }
}

/** Keyless website screenshot (mShots), cached by WordPress's renderer. */
private fun siteScreenshotUrl(url: String): String {
    val encoded = runCatching { java.net.URLEncoder.encode(url, "UTF-8") }.getOrDefault(url)
    return "https://s.wordpress.com/mshots/v1/$encoded?w=640&h=360"
}

/**
 * A card's thumbnail model: the WebView's own capture when one exists, the
 * mShots URL otherwise. The capture's cache key carries [version], because the
 * file is overwritten in place on the next visit and a plain File model could
 * otherwise be served from Coil's memory cache as the old page.
 */
private fun siteShotModel(context: android.content.Context, url: String, version: Long): Any {
    val captured = WebAppShots.cachedFile(context, url) ?: return siteScreenshotUrl(url)
    return coil.request.ImageRequest.Builder(context)
        .data(captured)
        .memoryCacheKey("webapp-shot:${WebAppShots.hostKey(url)}:$version")
        .build()
}

@Composable
fun SiteCard(site: Site, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onOpen,
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            val context = LocalContext.current
            val shotVersion = WebAppShots.versionOf(site.url)
            val shot = remember(site.url, shotVersion) {
                siteShotModel(context, site.url, shotVersion)
            }
            SubcomposeAsyncImage(
                model = shot,
                contentDescription = site.name,
                modifier = Modifier
                    .width(88.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize().background(Color(0xFF181820)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Public, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize().background(Color(0xFF181820)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Public, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                },
                success = { SubcomposeAsyncImageContent() },
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    site.name,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    site.url,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = tr("Delete web app"),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun SiteGridCard(site: Site, onOpen: () -> Unit, onDelete: () -> Unit) {
    val faviconUrl = remember(site.url) {
        val host = runCatching { java.net.URI(site.url).host }.getOrNull() ?: site.url
        "https://www.google.com/s2/favicons?domain=$host&sz=64"
    }
    val context = LocalContext.current
    val shotVersion = WebAppShots.versionOf(site.url)
    val screenshotUrl = remember(site.url, shotVersion) {
        // A capture from this WebView is the real page, taken after it fully
        // loaded. Only a site that has never been opened falls back to mShots
        // (keyless, but its cache can lag behind a fresh addition).
        siteShotModel(context, site.url, shotVersion)
    }
    Surface(
        onClick = onOpen,
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            SubcomposeAsyncImage(
                model = screenshotUrl,
                contentDescription = site.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
                loading = {
                    SiteScreenshotFallback(faviconUrl, site.name)
                },
                error = {
                    SiteScreenshotFallback(faviconUrl, site.name)
                },
                success = { SubcomposeAsyncImageContent() },
            )
            // The site name sits ON the screenshot, over a dark bottom fade so
            // it stays readable on both light and dark pages.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x22000000), Color(0xD9000000)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    site.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    site.url.substringAfter("://").substringBefore('/'),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .background(Color(0x99000000), RoundedCornerShape(14.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = tr("Delete web app"),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SiteScreenshotFallback(faviconUrl: String, name: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color(0xFF181820)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = faviconUrl,
            contentDescription = name,
            modifier = Modifier.size(42.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
