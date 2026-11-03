package com.hikari.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * A real flag image ([ProviderFlags.urlOf]) with the emoji flag as the offline
 * fallback, so the row shows a flag with or without network.
 */
@Composable
fun FlagIcon(
    code: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var failed by remember(code) { mutableStateOf(false) }
    if (failed) {
        Text(
            ProviderFlags.emojiOf(code),
            fontSize = (size.value * 0.72f).sp,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = ProviderFlags.urlOf(code),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}