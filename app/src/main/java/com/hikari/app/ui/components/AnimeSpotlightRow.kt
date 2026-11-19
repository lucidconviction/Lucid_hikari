package com.hikari.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hikari.app.data.AnimeCatalogRepository
import com.hikari.app.data.MediaItem
import com.hikari.app.i18n.tr
import com.hikari.app.ui.theme.glassSheen
import com.hikari.app.ui.theme.rememberGlassTokens
import kotlin.math.abs

/**
 * Home's Anime spotlight: a floating glass shelf under Streaming services,
 * with a centre-snapped poster carousel that lifts the focused card out of
 * the rail.
 *
 * Why it is always present: it is AniList data, not an extension's catalogue,
 * so a user with no anime source installed still gets a real shelf (and the
 * detail page then resolves it like any other AniList row). The switch in
 * Settings → App Layout hides it.
 *
 * Motion rules:
 *  - scale/alpha/lift/shadow are computed in the DRAW phase from the lazy
 *    list's layout info (the same trick as the Home rows), so scrolling never
 *    recomposes a card;
 *  - only the centre card is at 100%; neighbours sit 10dp lower at 84% and
 *    fade to 62%, which is what makes the active card read as "popped out";
 *  - snapping is CENTER-based, so the rail always rests on a card rather than
 *    half a poster, and the fling uses a little more friction than the
 *    default so one swipe travels a couple of cards, not ten.
 */
@Composable
fun AnimeSpotlightRow(
    onOpen: (MediaItem) -> Unit,
    onShowAll: () -> Unit,
) {
    var entries by remember { mutableStateOf<List<AnimeCatalogRepository.SpotlightEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        // Last known list first (no spinner on a re-open), then refresh.
        if (entries.isEmpty()) {
            entries = runCatching { AnimeCatalogRepository.cachedSpotlight() }.getOrDefault(emptyList())
        }
        loading = entries.isEmpty()
        val fresh = runCatching { AnimeCatalogRepository.spotlight(force = attempt > 0) }
            .getOrDefault(emptyList())
        if (fresh.isNotEmpty()) entries = fresh
        loading = false
    }

    Column(Modifier.padding(top = 20.dp)) {
        HikariSectionHeader(
            title = tr("Anime spotlight"),
            subtitle = if (entries.isNotEmpty()) tr("Trending on AniList") else tr("Trending anime, picked for you"),
            onAction = onShowAll,
            actionLabel = tr("Show All"),
        )
        when {
            entries.isNotEmpty() -> SpotlightCarousel(entries, onOpen)
            loading -> SpotlightSkeleton()
            else -> SpotlightUnavailable(onRetry = { attempt++ })
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SpotlightCarousel(
    entries: List<AnimeCatalogRepository.SpotlightEntry>,
    onOpen: (MediaItem) -> Unit,
) {
    val tv = com.hikari.app.tv.TvMode.current()
    val cardW: Dp = if (tv) {
        (com.hikari.app.tv.TvUi.posterWidth() * 1.35f).coerceAtLeast(120.dp)
    } else {
        148.dp
    }
    val cardH: Dp = cardW * 1.5f
    val shape: Shape = RoundedCornerShape(if (tv) 26.dp else 22.dp)
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val sidePad = ((screenW - cardW) / 2).coerceAtLeast(14.dp)
    val rowH = cardH + if (tv) 36.dp else 30.dp
    // The shelf is deliberately SHORTER than the cards: the active card hangs
    // over its top and bottom edges, which is what makes it read as floating
    // above the rail instead of sitting in a box.
    val panelH = cardH - if (tv) 12.dp else 16.dp
    val tokens = rememberGlassTokens()
    val accent = MaterialTheme.colorScheme.primary
    // The rail's own slot in ScrollKeeper: coming back from a title lands on
    // the same card, exactly like every other Home shelf.
    val rowKeys = remember(entries) { entries.map { it.item.uniqueId } }
    val state = keptListState("homeRow:anime-spotlight", itemKeys = rowKeys)

    // Centre snapping, with a little more friction than the default: the rail
    // should answer a flick immediately and come to rest on one card.
    val fling = remember(state) {
        snapFlingBehavior(
            SnapLayoutInfoProvider(state, SnapPosition.Center),
            exponentialDecay(1.30f),
            spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
        )
    }

    var focusedKey by remember { mutableStateOf<Any?>(null) }
    val activeIndex by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - center) }
                ?.index ?: 0
        }
    }
    val active = entries.getOrNull(activeIndex) ?: entries.first()
    val progress by animateFloatAsState(
        targetValue = ((activeIndex + 1).toFloat() / entries.size.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "spotlightProgress",
    )

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(rowH),
            contentAlignment = Alignment.Center,
        ) {
            // The floating shelf. Cards are taller than this panel and hang
            // over its top and bottom edges by a few dp — that overhang is
            // the "pop out".
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(panelH)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(tokens.fillTop, tokens.fillBottom)))
                    .border(1.dp, tokens.border, RoundedCornerShape(30.dp))
                    .glassSheen(tokens),
            )
            LazyRow(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(horizontal = sidePad),
                horizontalArrangement = Arrangement.spacedBy(if (tv) 18.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(entries, key = { _, e -> e.item.uniqueId }) { _, entry ->
                    SpotlightCard(
                        entry = entry,
                        state = state,
                        focused = focusedKey == entry.item.uniqueId,
                        cardW = cardW,
                        cardH = cardH,
                        shape = shape,
                        accent = accent,
                        onFocusChanged = { on ->
                            focusedKey = if (on) entry.item.uniqueId else null
                        },
                        onClick = { onOpen(entry.item) },
                    )
                }
            }
        }

        // One caption for the centred card: the title, score and next airing.
        // Crossfading the whole block (instead of printing every poster's
        // caption) is what keeps the rail uncluttered and readable.
        Crossfade(
            targetState = activeIndex,
            animationSpec = tween(180),
            label = "spotlightCaption",
        ) { index ->
            val e = entries.getOrNull(index) ?: return@Crossfade
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (tv) 14.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    e.item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Row(
                    Modifier
                        .padding(top = 5.dp)
                        .height(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    e.item.rating?.takeIf { it > 0.0 }?.let { rating ->
                        SpotlightChip(Icons.Filled.Star, String.format(java.util.Locale.US, "%.1f", rating))
                    }
                    airingLabel(e.item.nextEpisodeDate, e.nextEpisode)?.let { label ->
                        SpotlightChip(
                            Icons.Filled.CalendarMonth,
                            if (e.nextEpisode != null && e.nextEpisode > 0) tr("Ep") + " " + label else label,
                        )
                    }
                }
            }
        }

        // A slim progress rail, not twenty dots: it stays honest at any list
        // length and costs one thin line of height.
        Box(
            Modifier
                .padding(top = if (tv) 12.dp else 9.dp)
                .width(if (tv) 180.dp else 132.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                .align(Alignment.CenterHorizontally),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(accent, CircleShape),
            )
        }
    }
}

@Composable
private fun SpotlightChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpotlightCard(
    entry: AnimeCatalogRepository.SpotlightEntry,
    state: LazyListState,
    focused: Boolean,
    cardW: Dp,
    cardH: Dp,
    shape: Shape,
    accent: Color,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val key = entry.item.uniqueId
    Box(
        Modifier
            .width(cardW)
            .height(cardH)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .clickable(onClick = onClick)
            .graphicsLayer {
                val eased = if (focused) 1f else spotlightFraction(state, key)
                val scale = 0.84f + 0.16f * eased
                scaleX = scale
                scaleY = scale
                alpha = 0.62f + 0.38f * eased
                translationY = (1f - eased) * 10.dp.toPx()
                shadowElevation = 2.dp.toPx() + 20.dp.toPx() * eased
                this.shape = shape
                clip = true
            },
    ) {
        val url = entry.item.posterUrl
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = entry.item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.item.title.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // Rim light on the centred card only. Drawn as its own layer so the
        // alpha follows the draw-phase fraction without recomposition.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val eased = if (focused) 1f else spotlightFraction(state, key)
                    alpha = eased * 0.55f
                }
                .border(1.dp, accent, shape),
        )
    }
}

@Composable
private fun SpotlightSkeleton() {
    val tokens = rememberGlassTokens()
    val cardW = 148.dp
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(3) { i ->
            val w = if (i == 1) cardW else cardW * 0.82f
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .width(w)
                    .height(w * 1.5f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(tokens.fillTop, tokens.fillBottom)))
                    .border(1.dp, tokens.border, RoundedCornerShape(22.dp)),
            )
        }
    }
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(14.dp),
    )
}

@Composable
private fun SpotlightUnavailable(onRetry: () -> Unit) {
    val tokens = rememberGlassTokens()
    Column(
        Modifier
            .padding(top = 10.dp, start = 14.dp, end = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(tokens.fillTop, tokens.fillBottom)))
            .border(1.dp, tokens.border, RoundedCornerShape(24.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            tr("Couldn't reach AniList — the spotlight will retry."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onRetry) { Text(tr("Retry")) }
    }
}

/** Smoothstep of how centred [key] is: 1 = dead centre, 0 = half a screen away. */
private fun spotlightFraction(state: LazyListState, key: Any): Float {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    val half = ((info.viewportEndOffset - info.viewportStartOffset) / 2f).coerceAtLeast(1f)
    val raw = (1f - abs(itemCenter - center) / half).coerceIn(0f, 1f)
    return raw * raw * (3f - 2f * raw)
}

/** AniList's ISO instant + episode number → "7 · Wed". */
private fun airingLabel(date: String?, episode: Int?): String? {
    if (date.isNullOrBlank()) return null
    val instant = runCatching { java.time.Instant.parse(date) }.getOrNull() ?: return null
    val day = runCatching {
        java.time.format.DateTimeFormatter.ofPattern("EEE")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrNull() ?: return null
    return if (episode != null && episode > 0) "$episode · $day" else day
}
