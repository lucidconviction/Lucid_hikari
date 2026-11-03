package com.hikari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hikari.app.HikariApp
import com.hikari.app.data.MediaType
import com.hikari.app.nuvio.TmdbResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A single TMDB search suggestion. Each suggestion shows the title, year (if
 * available) and a type chip so the user can tell movie from show at a glance.
 */
data class TmdbSuggestion(
    val id: String,
    val title: String,
    val year: String?,
    val mediaType: MediaType,
    val posterUrl: String? = null,
) {
    companion object {
        /** Label that the dropdown shows for this item's type. */
        fun labelFor(t: MediaType) = when (t) {
            MediaType.MOVIE -> "Movie"
            MediaType.SERIES -> "Series"
            else -> "Item"
        }
    }
}

sealed class SearchSuggestionsState {
    object Empty : SearchSuggestionsState()
    object Loading : SearchSuggestionsState()
    data class Done(val suggestions: List<TmdbSuggestion>) : SearchSuggestionsState()
}

/**
 * Fetches search suggestions from TMDB for the query [q]. Results are stable
 * across recompositions for the same query string so the network only pays
 * once per query chunk.
 */
@Composable
fun rememberTmdbSuggestions(query: String): StateFlow<SearchSuggestionsState> {
    val ctx = LocalContext.current
    val app: HikariApp? = remember(ctx) { ctx.applicationContext as? HikariApp }
    if (app == null) return remember { MutableStateFlow(SearchSuggestionsState.Empty) }

    // Stable across recompositions for the same query string.
    val result = remember(query) { MutableStateFlow<SearchSuggestionsState>(SearchSuggestionsState.Empty) }

    LaunchedEffect(query, app) {
        if (query.isBlank()) {
            result.value = SearchSuggestionsState.Empty
            return@LaunchedEffect
        }
        result.value = SearchSuggestionsState.Loading
        val items = withContext(Dispatchers.IO) {
            fetchSuggestions(app, query)
        }
        result.value = SearchSuggestionsState.Done(items)
    }
    return result
}

private suspend fun fetchSuggestions(app: HikariApp, query: String): List<TmdbSuggestion> {
    val resolver = TmdbResolver
    val movieResults = try {
        resolver.apiGet("/search/movie", mapOf("query" to query))
    } catch (_: Exception) { null }
    val tvResults = try {
        resolver.apiGet("/search/tv", mapOf("query" to query))
    } catch (_: Exception) { null }

    val movies = parseSuggestions(movieResults, MediaType.MOVIE)
    val tvs = parseSuggestions(tvResults, MediaType.SERIES)

    return (movies + tvs).take(10)
}

private fun parseSuggestions(obj: JSONObject?, type: MediaType): List<TmdbSuggestion> {
    val arr = obj?.optJSONArray("results") ?: return emptyList()
    val out = mutableListOf<TmdbSuggestion>()
    for (i in 0 until minOf(arr.length(), 5)) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optString("id").trim()
        if (id.isBlank() || id == "null") continue
        val title = o.optString("title").ifEmpty { o.optString("name") }.trim()
        if (title.isBlank()) continue
        val date = o.optString("release_date").ifEmpty { o.optString("first_air_date") }
        val year = if (date.isNotBlank() && date.length >= 4) date.substring(0, 4) else null
        val poster = o.optString("poster_path")
        val posterUrl = if (poster.isNotBlank()) "https://image.tmdb.org/t/p/w200$poster" else null
        out.add(TmdbSuggestion(id = id, title = title, year = year, mediaType = type, posterUrl = posterUrl))
    }
    return out
}

/**
 * Shows a dropdown of TMDB search suggestions below the search box. The user
 * picks one to immediately run a normal search for it. Shows nothing when
 * the query is empty, still loading, or the platform is TV (where a D-pad
 * dropdown would be unusable).
 */
@Composable
fun SearchSuggestionsDropdown(
    query: String,
    onPick: (String, MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (com.hikari.app.tv.TvMode.current()) return
    val state by rememberTmdbSuggestions(query).collectAsState(SearchSuggestionsState.Empty)
    if (state is SearchSuggestionsState.Loading || state is SearchSuggestionsState.Empty) return
    val suggestions = (state as? SearchSuggestionsState.Done)?.suggestions ?: return

    Column(modifier = modifier) {
        // Thin divider under the suggestion row so it visually belongs with
        // the search box rather than the results.
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(thickness = 1.dp, color = Color.Black.copy(alpha = 0.08f))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(vertical = 4.dp),
        ) {
            items(suggestions, key = { it.id }) { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(s.title, s.mediaType) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (s.posterUrl != null) {
                            Icon(
                                imageVector = if (s.mediaType == MediaType.MOVIE) Icons.Filled.Movie else Icons.Filled.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (s.year != null) {
                            Text(
                                s.year,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = false,
                        onClick = { onPick(s.title, s.mediaType) },
                        label = { Text(TmdbSuggestion.labelFor(s.mediaType), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}