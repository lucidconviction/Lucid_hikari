package com.hikari.app.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which right-side YouTube panel the player should show. */
enum class YtPanelMode { INFO, COMMENTS }

data class YtVideoInfo(
    val title: String,
    val author: String,
    val views: String,
    val published: String,
    val likes: String,
    val description: String,
    val thumbnail: String?,
)

data class YtComment(
    val id: String,
    val author: String,
    val avatar: String?,
    val message: String,
    val published: String,
    val likes: String,
    val replies: String,
    val nestedKey: String?,
)

data class YtCommentPage(val comments: List<YtComment>, val nextKey: String?)

/** Reads the current video's info and comments through the same engine as the YouTube tab. */
object YouTubeVideoDetails {

    private fun service(context: android.content.Context): com.liskovsoft.mediaserviceinterfaces.ServiceManager {
        GlobalPreferences.instance(context)
        YouTubeContentRegion.apply(context)
        return YouTubeServiceManager.instance()
    }

    suspend fun info(context: android.content.Context, videoId: String): YtVideoInfo? =
        withContext(Dispatchers.IO) {
            val meta = runCatching {
                service(context).mediaItemService.getMetadataObserve(videoId).blockingFirst()
            }.getOrNull() ?: return@withContext null
            YtVideoInfo(
                title = meta.title.orEmpty(),
                author = meta.author.orEmpty(),
                views = meta.viewCount.orEmpty(),
                published = meta.publishedDate.orEmpty(),
                likes = meta.likeCount.orEmpty(),
                description = meta.description.orEmpty(),
                thumbnail = videoId.takeIf { it.isNotBlank() }
                    ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
            )
        }

    suspend fun comments(
        context: android.content.Context,
        videoId: String,
        nestedKey: String? = null,
    ): YtCommentPage = withContext(Dispatchers.IO) {
        val svc = service(context)
        val key = nestedKey?.takeIf { it.isNotBlank() } ?: runCatching {
            svc.mediaItemService.getMetadataObserve(videoId).blockingFirst().commentsKey
        }.getOrNull()
        if (key.isNullOrBlank()) return@withContext YtCommentPage(emptyList(), null)
        val group = runCatching {
            svc.commentsService.getCommentsObserve(key).blockingFirst()
        }.getOrNull() ?: return@withContext YtCommentPage(emptyList(), null)
        val items = group.comments.orEmpty().mapNotNull { c ->
            val message = c.message.orEmpty()
            if (message.isBlank()) return@mapNotNull null
            YtComment(
                id = c.id.orEmpty(),
                author = c.authorName.orEmpty().ifBlank { "YouTube user" },
                avatar = c.authorPhoto,
                message = message,
                published = c.publishedDate.orEmpty(),
                likes = c.likeCount.orEmpty(),
                replies = c.replyCount.orEmpty(),
                nestedKey = c.nestedCommentsKey,
            )
        }
        YtCommentPage(items, group.nextCommentsKey)
    }
}

/**
 * The player's right-side Info and Comments panels.
 *
 * Same geometry and slide-in as "More Videos": a translucent scrim over the
 * video and a full-height panel on the right, so the three buttons live in one
 * continuous surface instead of three different UI languages.
 */
@Composable
fun YouTubeVideoPanel(
    videoId: String,
    mode: YtPanelMode,
    onClose: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var info by remember(videoId) { mutableStateOf<YtVideoInfo?>(null) }
    var loading by remember(videoId, mode) { mutableStateOf(true) }
    var comments by remember(videoId) { mutableStateOf<YtCommentPage?>(null) }
    var replyTo by remember(videoId) { mutableStateOf<YtComment?>(null) }
    var error by remember(videoId, mode) { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId, mode) {
        if (videoId.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        if (mode == YtPanelMode.INFO) {
            info = YouTubeVideoDetails.info(context, videoId)
            if (info == null) error = "Couldn't load video info."
        } else {
            comments = YouTubeVideoDetails.comments(context, videoId)
            if (comments == null) error = "Couldn't load comments."
        }
        loading = false
    }

    LaunchedEffect(replyTo) {
        val target = replyTo ?: return@LaunchedEffect
        loading = true
        comments = YouTubeVideoDetails.comments(context, videoId, target.nestedKey)
        loading = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Exactly the More Videos geometry: a quarter of the player width for
        // the card, plus its padding, capped at 60% of the screen. The Info and
        // Comments panels therefore line up with the list they sit beside.
        val thumbnailWidth = (maxWidth / 4).coerceIn(150.dp, 360.dp)
        val panelWidth = (thumbnailWidth + 28.dp).coerceAtMost(maxWidth * 0.6f)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(onClick = onClose),
        )
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(panelWidth)
                // Fully opaque, 25% stronger than the previous translucent panel.
                .background(Color(0xFF111114))
                .clickable(enabled = false) {},
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (mode == YtPanelMode.INFO) Icons.Filled.Info else Icons.Filled.Comment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            mode == YtPanelMode.INFO -> "Info"
                            replyTo != null -> "Replies"
                            else -> "Comments"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                if (replyTo != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { replyTo = null }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back to comments",
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                        Text(
                            "All comments",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    }
                    error != null -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(error!!, color = Color(0xFFB0B0B0), fontSize = 13.sp)
                    }
                    mode == YtPanelMode.INFO -> InfoContent(info)
                    replyTo != null -> ReplyHeader(replyTo!!)
                }

                if (mode == YtPanelMode.COMMENTS && !loading && error == null) {
                    val page = comments
                    if (page == null || page.comments.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No comments yet.", color = Color(0xFFB0B0B0), fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(page.comments, key = { it.id.ifBlank { it.author + it.message } }) { comment ->
                                CommentRow(
                                    comment = comment,
                                    canOpenReplies = replyTo == null && !comment.nestedKey.isNullOrBlank(),
                                    onClick = {
                                        if (replyTo == null && !comment.nestedKey.isNullOrBlank()) {
                                            replyTo = comment
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoContent(info: YtVideoInfo?) {
    if (info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No info for this video.", color = Color(0xFFB0B0B0), fontSize = 13.sp)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF222222)),
            )
        }
        item {
            Text(
                info.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                info.author,
                color = Color(0xFFD0D0D0),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Text(
                listOf(info.views, info.published, info.likes.takeIf { it.isNotBlank() }?.let { "$it likes" })
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = Color(0xFFA8A8A8),
                fontSize = 11.sp,
            )
        }
        if (info.description.isNotBlank()) {
            item {
                Text(
                    info.description,
                    color = Color(0xFFC8C8C8),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ReplyHeader(comment: YtComment) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            "Replying to",
            color = Color(0xFFA8A8A8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            comment.author,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            comment.message,
            color = Color(0xFFC8C8C8),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CommentRow(comment: YtComment, canOpenReplies: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = canOpenReplies, onClick = onClick)
            .padding(6.dp),
    ) {
        if (!comment.avatar.isNullOrBlank()) {
            AsyncImage(
                model = comment.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF2A2A2E)),
            )
        } else {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF2A2A2E)),
                contentAlignment = Alignment.Center,
            ) {
                Text(comment.author.take(1), color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${comment.author}  ${comment.published}",
                color = Color(0xFFB8B8B8),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(comment.message, color = Color.White, fontSize = 12.sp)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (comment.likes.isNotBlank()) {
                    Text("👍 ${comment.likes}", color = Color(0xFF9A9A9A), fontSize = 10.sp)
                }
                if (comment.replies.isNotBlank()) {
                    Text("Replies ${comment.replies}", color = Color(0xFF9A9A9A), fontSize = 10.sp)
                }
                if (canOpenReplies) {
                    Text("View replies", color = Color(0xFF7FB8FF), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
