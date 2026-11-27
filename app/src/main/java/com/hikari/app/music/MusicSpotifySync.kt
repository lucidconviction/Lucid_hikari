package com.hikari.app.music

import android.content.Context
import com.hikari.app.music.spotifyweb.SpotifySource
import com.hikari.app.music.spotifyweb.SpotifyTrack
import com.hikari.app.music.spotifyweb.SpotifyWebClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Copies the linked Spotify account's Liked Songs and playlists into local
 * Lucid playlists. The Spotify side is the desktop `sp_dc` web session; every
 * track is matched to Deezer, and an unmatched track is still kept for
 * YouTube playback by title.
 */
object MusicSpotifySync {

    data class State(
        val busy: Boolean = false,
        val progress: String? = null,
        val result: String? = null,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun sync(context: Context) {
        if (_state.value.busy) return
        val app = context.applicationContext
        _state.value = State(busy = true, progress = "Reading your Spotify library…")
        scope.launch {
            runCatching {
                MusicLibrary.init(app)
                MusicSpotifySession.init(app)
                val cookie = MusicSpotifySession.cookie(app)
                if (cookie.isBlank()) error("Connect Spotify first.")
                val client = SpotifyWebClient.connect(cookie, requireSignedIn = true).getOrThrow()
                val account = client.account().getOrThrow()
                var playlists = 0
                var tracks = 0
                for (entry in account.library) {
                    _state.value = State(
                        busy = true,
                        progress = "Reading ${entry.name}…",
                    )
                    val playlist = when (val source = entry.source) {
                        is SpotifySource.Liked -> client.liked().getOrNull()
                        is SpotifySource.Playlist -> client.playlist(source.id).getOrNull()
                    } ?: continue
                    val title = "Spotify · " + playlist.name.ifBlank { entry.name }
                    val existing = MusicLibrary.playlists.value.firstOrNull { it.title == title }
                        ?: MusicLibrary.createPlaylist(title)
                    val matched = ArrayList<MusicTrack>()
                    for ((index, spotifyTrack) in playlist.tracks.withIndex()) {
                        _state.value = State(
                            busy = true,
                            progress = "Matching ${index + 1} / ${playlist.tracks.size} · ${entry.name}",
                        )
                        matched += match(spotifyTrack, entry.name, index)
                        if (index % 3 == 2) delay(120)
                    }
                    MusicLibrary.addTracksToPlaylist(existing.id, matched)
                    playlists++
                    tracks += matched.size
                }
                State(
                    busy = false,
                    result = "Synced $playlists playlists · $tracks tracks",
                )
            }.onSuccess { _state.value = it }
                .onFailure {
                    _state.value = State(
                        busy = false,
                        error = it.message ?: "Spotify sync failed",
                    )
                }
        }
    }

    private suspend fun match(
        track: SpotifyTrack,
        playlistName: String,
        index: Int,
    ): MusicTrack {
        if (!track.local) {
            val query = "${track.name} ${track.artistLine}".trim()
            val found = runCatching { DeezerApi.searchTracks(query, 1).firstOrNull() }.getOrNull()
            if (found != null) return found
        }
        return MusicTrack(
            id = "spotify:${playlistName}:$index:${track.name.hashCode()}",
            title = track.name,
            artist = track.artistLine.ifBlank { "Spotify" },
            album = playlistName,
            coverUrl = track.art.orEmpty(),
            durationSeconds = (track.durationMs / 1000L).toInt().coerceAtLeast(0),
        )
    }
}
