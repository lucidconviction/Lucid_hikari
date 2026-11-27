package com.hikari.app.iptv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hikari.app.data.IptvChannel
import com.hikari.app.i18n.I18n
import com.hikari.app.i18n.tr
import com.hikari.app.ui.components.HikariChipRow
import com.hikari.app.ui.theme.HikariType

/**
 * The conditional feed filters shown above a channel source list.
 *
 * A row only appears when the current results contain more than one value for
 * that dimension. On a portal with a single "Sky Sports HD" there is nothing to
 * filter and the sheet draws exactly as it did before; on a portal with
 * `beIN 1 AR`, `beIN 1 FR`, `beIN 2 AR` the user gets the three rows that make
 * those distinguishable.
 *
 * The state is hoisted: the caller owns [filter], so opening another channel
 * can start it at "All" and the list can apply the same filter it is drawing.
 */
@Composable
fun FeedFilterRows(
    channels: List<IptvChannel>,
    filter: FeedFilter,
    onChange: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tagsByUrl = remember(channels) {
        channels.associate { it.url to FeedTagsParser.parse(it.name) }
    }
    val numbers = remember(tagsByUrl) {
        tagsByUrl.values.mapNotNull { it.number }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    }
    val languages = remember(tagsByUrl) {
        tagsByUrl.values.mapNotNull { it.lang }.distinct().sorted()
    }
    val qualities = remember(tagsByUrl) {
        val present = tagsByUrl.values.mapNotNull { it.quality }.toSet()
        listOf("4K", "FHD", "HD", "SD").filter { it in present }
    }

    if (numbers.size < 2 && languages.size < 2 && qualities.size < 2) return

    Column(modifier.fillMaxWidth()) {
        if (numbers.size > 1) {
            FeedFilterRow(
                label = tr("Channel"),
                values = numbers,
                selected = filter.number,
                onSelect = { onChange(filter.copy(number = it)) },
            )
        }
        if (languages.size > 1) {
            FeedFilterRow(
                label = tr("Language"),
                values = languages,
                selected = filter.lang,
                onSelect = { onChange(filter.copy(lang = it)) },
            )
        }
        if (qualities.size > 1) {
            FeedFilterRow(
                label = tr("Quality"),
                values = qualities,
                selected = filter.quality,
                onSelect = { onChange(filter.copy(quality = it)) },
            )
        }
    }
}

@Composable
private fun FeedFilterRow(
    label: String,
    values: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = label.uppercase(),
            style = HikariType.micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        HikariChipRow(
            items = listOf<String?>(null) + values,
            selected = selected,
            label = { it ?: I18n.t("All") },
            onSelect = onSelect,
            contentPadding = PaddingValues(0.dp),
            key = { it ?: "__all" },
        )
    }
}
