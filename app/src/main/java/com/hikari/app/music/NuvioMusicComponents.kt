package com.hikari.app.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.R

/**
 * The visual layer of the Nuvio One / BitChord music section, ported to Lucid
 * Hikari and fed by this app's own Deezer/Qobuz/YouTube engine.
 *
 * The palette, type scale, card shapes and focus treatment follow the source
 * section (GPLv3, see the README credits). Keeping it in one file means the
 * Music tab can be re-skinned or swapped to the full engine without touching
 * the screens.
 */
internal object NuvioMusicPalette {
    val background = Color(0xFF08070B)
    val backgroundTop = Color(0xFF0E0C14)
    val surface = Color(0xFF14111C)
    val surfaceRaised = Color(0xFF1C1826)
    val surfaceFocused = Color(0xFF271F35)
    val border = Color(0xFF2A2436)
    val text = Color(0xFFF8F6FF)
    val textSecondary = Color(0xFFA9A4B8)
    val textMuted = Color(0xFF6D677E)
    val accent = Color(0xFFA78BFA)
    val accentPurple = Color(0xFF7C3AED)
    val accentSoft = Color(0xFF231A33)
    val accentTeal = Color(0xFF5EEAD4)
    val accentCoral = Color(0xFFFB7185)
    val error = Color(0xFFFF6B6B)
}

internal val NuvioSans = FontFamily(
    Font(R.font.schibsted_grotesk_regular, FontWeight.Normal),
    Font(R.font.schibsted_grotesk_medium, FontWeight.Medium),
    Font(R.font.schibsted_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.schibsted_grotesk_bold, FontWeight.Bold),
)

internal val NuvioDisplay = NuvioSans

internal enum class NuvioItemKind { TRACK, ARTIST, ALBUM, PLAYLIST }

/** One card in a Nuvio shelf. Exactly one of the id fields is set per kind. */
internal data class NuvioShelfItem(
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val kind: NuvioItemKind,
    val track: MusicTrack? = null,
    val artistId: String = "",
    val albumId: String = "",
    val playlistId: String = "",
    val isVideo: Boolean = false,
)

internal enum class NuvioShelfLayout { CARDS, LIST, VIDEO }

internal data class NuvioShelf(
    val title: String,
    val subtitle: String = "",
    val items: List<NuvioShelfItem>,
    val layout: NuvioShelfLayout = NuvioShelfLayout.CARDS,
)

/** Two colours keyed off the artwork, for the soft halo behind a hero. */
internal fun nuvioAuroraColors(seed: String?): Pair<Color, Color> {
    val hash = (seed.orEmpty()).hashCode()
    val hue = ((hash and 0x7FFFFFFF) % 360).toFloat()
    val first = Color.hsv(hue, 0.55f, 0.55f)
    val second = Color.hsv((hue + 48f) % 360f, 0.65f, 0.40f)
    return first to second
}

@Composable
internal fun NuvioAuroraBackground(
    seed: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val (first, second) = remember(seed) { nuvioAuroraColors(seed) }
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        NuvioMusicPalette.backgroundTop,
                        NuvioMusicPalette.background,
                    ),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            first.copy(alpha = 0.22f),
                            second.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
internal fun NuvioArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
    circle: Boolean = false,
    icon: ImageVector = Icons.Filled.MusicNote,
    iconSize: Int = 24,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier
            .clip(shape)
            .background(NuvioMusicPalette.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = NuvioMusicPalette.textMuted,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

@Composable
internal fun NuvioSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = NuvioMusicPalette.text,
                fontFamily = NuvioDisplay,
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    color = NuvioMusicPalette.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = NuvioMusicPalette.accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
internal fun NuvioCard(
    item: NuvioShelfItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 150,
    circle: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(140),
        label = "nuvio-card-scale",
    )
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .width(width.dp)
            .then(if (focused) Modifier else Modifier)
            .clip(shape)
            .background(if (focused) NuvioMusicPalette.surfaceRaised else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (focused) NuvioMusicPalette.accent else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (item.isVideo) 16f / 9f else 1f)
                .graphicsScale(scale),
        ) {
            NuvioArtwork(
                url = item.imageUrl,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = if (circle) 0 else 12,
                circle = circle,
                icon = when (item.kind) {
                    NuvioItemKind.ARTIST -> Icons.Filled.Person
                    NuvioItemKind.ALBUM, NuvioItemKind.PLAYLIST -> Icons.Filled.Album
                    NuvioItemKind.TRACK -> Icons.Filled.MusicNote
                },
            )
            if (focused && !circle) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
                )
            }
            if (item.kind == NuvioItemKind.TRACK && focused) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(29.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC08070B))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = NuvioMusicPalette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                item.subtitle,
                color = NuvioMusicPalette.textSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A tiny graphicsLayer helper so the card artwork scales with focus. */
private fun Modifier.graphicsScale(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}

@Composable
internal fun NuvioShelfRow(
    shelf: NuvioShelf,
    onItemClick: (NuvioShelfItem, Int) -> Unit,
    modifier: Modifier = Modifier,
    circleCards: Boolean = false,
) {
    if (shelf.items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        NuvioSectionHeader(shelf.title, shelf.subtitle)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                shelf.items,
                key = { index, item ->
                    "${item.kind}:${item.track?.id ?: item.artistId}${item.albumId}${item.playlistId}#$index"
                },
            ) { index, item ->
                NuvioCard(
                    item = item,
                    onClick = { onItemClick(item, index) },
                    circle = circleCards || item.kind == NuvioItemKind.ARTIST,
                )
            }
        }
    }
}

@Composable
internal fun NuvioTrackRow(
    track: MusicTrack,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    showArtwork: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = when {
            focused -> NuvioMusicPalette.surfaceFocused
            isCurrent -> NuvioMusicPalette.accentSoft
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "nuvio-track-row",
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(
                width = 2.dp,
                color = if (focused) NuvioMusicPalette.accent else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = NuvioMusicPalette.accent,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    (index + 1).toString(),
                    color = if (focused) NuvioMusicPalette.textSecondary else NuvioMusicPalette.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
        if (showArtwork) {
            NuvioArtwork(
                url = track.coverUrl,
                modifier = Modifier.size(44.dp),
                cornerRadius = 7,
                iconSize = 18,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) NuvioMusicPalette.accent else NuvioMusicPalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(track.artist)
                    if (track.album.isNotBlank() && track.album != track.title) {
                        if (isNotEmpty()) append(" · ")
                        append(track.album)
                    }
                },
                color = NuvioMusicPalette.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.formattedDuration != "--:--") {
            Text(
                track.formattedDuration,
                color = NuvioMusicPalette.textMuted,
                fontSize = 11.sp,
            )
        }
        if (onMenu != null) {
            IconButton(onClick = onMenu, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = NuvioMusicPalette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun NuvioPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(32.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Color.White
                    focused -> NuvioMusicPalette.surfaceFocused
                    else -> NuvioMusicPalette.surface
                },
            )
            .border(
                width = when {
                    focused && selected -> 2.dp
                    focused -> 2.dp
                    else -> 1.dp
                },
                color = when {
                    focused && selected -> NuvioMusicPalette.accent
                    focused -> NuvioMusicPalette.accent
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = when {
                selected -> NuvioMusicPalette.background
                else -> NuvioMusicPalette.textSecondary
            },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun NuvioSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search songs, artists, albums…",
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = NuvioMusicPalette.text,
            fontSize = 14.sp,
            fontFamily = NuvioSans,
        ),
        cursorBrush = SolidColor(NuvioMusicPalette.accent),
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(NuvioMusicPalette.surface)
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(23.dp)),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = NuvioMusicPalette.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(
                            placeholder,
                            color = NuvioMusicPalette.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
internal fun NuvioHero(
    track: MusicTrack,
    onPlay: () -> Unit,
    onArtist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (first, second) = remember(track.coverUrl) { nuvioAuroraColors(track.coverUrl) }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(first.copy(alpha = 0.8f), NuvioMusicPalette.surface, second.copy(alpha = 0.7f)),
                ),
            ),
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, NuvioMusicPalette.background.copy(alpha = 0.92f)),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                "FEATURED TRACK",
                color = NuvioMusicPalette.accentTeal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                track.title,
                color = Color.White,
                fontFamily = NuvioDisplay,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track.artist,
                color = NuvioMusicPalette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = NuvioMusicPalette.background,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Play",
                        color = NuvioMusicPalette.background,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (onArtist != null) {
                    Row(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NuvioMusicPalette.surface.copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .clickable(onClick = onArtist)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Artist",
                            color = NuvioMusicPalette.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NuvioGenreTile(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = 0.88f), color.copy(alpha = 0.30f)),
                ),
            )
            .border(
                2.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(18.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            label,
            color = Color.White,
            fontFamily = NuvioDisplay,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NuvioStationTile(
    name: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NuvioMusicPalette.surface)
            .border(
                2.dp,
                if (focused) NuvioMusicPalette.accent else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(18.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            name,
            color = NuvioMusicPalette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NuvioLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NuvioMusicPalette.accent, strokeWidth = 2.dp)
    }
}

@Composable
internal fun NuvioErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                color = NuvioMusicPalette.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            NuvioPill("Try again", selected = true, onClick = onRetry)
        }
    }
}

@Composable
internal fun NuvioEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = NuvioMusicPalette.textMuted,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                color = NuvioMusicPalette.text,
                fontFamily = NuvioDisplay,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                color = NuvioMusicPalette.textSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
