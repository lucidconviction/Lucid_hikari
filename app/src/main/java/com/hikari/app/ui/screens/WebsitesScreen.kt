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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.hikari.app.HikariApp
import com.hikari.app.data.Site
import com.hikari.app.i18n.I18n
import com.hikari.app.i18n.tr
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
    var removeTarget by remember { mutableStateOf<Site?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Websites") + " · ${sites.size}") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                    placeholder = { Text(tr("Paste any URL or search"), color = Color.White.copy(alpha = 0.5f)) },
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
                            if (raw.isEmpty()) return@KeyboardActions
                            val normalized = if (raw.startsWith("http://", ignoreCase = true) ||
                                raw.startsWith("https://", ignoreCase = true)
                            ) raw else "https://$raw"
                            urlInput = ""
                            context.startActivity(
                                Intent(context, WebViewActivity::class.java).apply {
                                    putExtra("url", normalized)
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
                            val raw = urlInput.trim()
                            if (raw.isEmpty()) return@clickable
                            val normalized = if (raw.startsWith("http://", ignoreCase = true) ||
                                raw.startsWith("https://", ignoreCase = true)
                            ) raw else "https://$raw"
                            urlInput = ""
                            context.startActivity(
                                Intent(context, WebViewActivity::class.java).apply {
                                    putExtra("url", normalized)
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
                tr("Saved websites"),
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
                            tr("No websites added yet — add one below to open any site ad-free."),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(50),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Add website"), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(tr("Add website")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text(tr("Website name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
                    )
                    OutlinedTextField(
                        value = addUrl,
                        onValueChange = { addUrl = it },
                        label = { Text(tr("Website URL")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = addUrl.trim().isNotBlank(),
                    onClick = {
                        val url = addUrl.trim()
                        val name = addName.trim().ifEmpty { url }
                        val normalized = if (url.startsWith("http://", ignoreCase = true) ||
                            url.startsWith("https://", ignoreCase = true)
                        ) url else "https://$url"
                        scope.launch {
                            val app = context.applicationContext as HikariApp
                            app.store.addSite(Site(name, normalized))
                        }
                        showAddDialog = false
                        addName = ""
                        addUrl = ""
                    },
                ) {
                    Text(tr("Add"), color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(tr("Cancel"), color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }

    removeTarget?.let { site ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(tr("Remove website")) },
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
            Icon(
                Icons.Filled.Public,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
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
                    contentDescription = tr("Delete website"),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
