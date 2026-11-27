# Nuvio One / BitChord music merge

Source: <https://github.com/zoga2o3-ops/Nuvio-One-M> (`livetv-nownext-sports`).
The music section there is itself a GPLv3 port of **BitChord** by
kushagrasinghx. Lucid Hikari is GPLv3, so the code is licence-compatible;
the README credits both projects.

## Phase 1 — done (this tree)

The Music tab now uses the Nuvio/BitChord visual language over Lucid's
existing Deezer/Qobuz/YouTube engine:

- `music/NuvioMusicComponents.kt` — palette, Schibsted Grotesk, aurora
  background, artwork, cards, shelves, track rows, pills, search field,
  hero, genre/station tiles, loading/error/empty states.
- `music/NuvioMusicTabs.kt` — Home, Explore, Library and Search screens.
- `music/MusicScreen.kt` — new top bar and pill navigation; the old
  Home/Search/Browse/Radio/Library composables remain available but are no
  longer the tab body.
- `res/font/schibsted_grotesk_*.ttf` (SIL OFL).
- `music/FloatingMusicPiP.kt` — the draggable/resizable floating card from
  Lucid Nuvio IPTV+ Mobile, including the video-on-top layout, 3-second
  control fade, audio/video switch and music-note route. It replaces the
  static global music bar; the Music tab keeps its compact bar.
- Existing player, notification service and Audio/Video toggle are unchanged
  underneath.

Verified on an arm64 Android 14 emulator: Home hero + shelves, Explore grid,
Library tiles, live search, track playback, mini player and the 4-action
music notification.

## Phase 2a — engine vendored (done)

- `music/ytm/**` — the BitChord engine, models, innertube client/parser,
  lyrics, TrackMatcher and locale/debug helpers (29 files, ~480 KB).
- Ktor client/OkHttp/content-negotiation/serialization dependencies added.
- `music/YtMusicCatalog.kt` adapts anonymous Home, Search, artist, album and
  playlist pages to Lucid's `MusicTrack`/detail models.
- `MusicTrack.videoId` and a direct path in `MusicSourceResolver` play the
  exact YouTube video instead of searching by title.
- Settings → Music → Catalogue switches Home/Search between Deezer and
  YouTube Music; playback, mini player and notification are unchanged.

Verified on the Android 14 emulator: YouTube Music home shelves, search,
album/artist pages, playback and the 4-action notification.

## Phase 2a — account bridge (done)

- `music/YtAccountMusic.kt` reads the account's Music home, Liked Music,
  playlists and playlist tracks through **MediaServiceCore**, the same
  OAuth session the YouTube tab signs in with.
- Music → Library reuses the YouTube page's `YouTubeAccountViewModel`,
  `YouTubeSignInDialog` and `YouTubeAccountsDialog` (same device code at
  youtube.com/activate, same account switcher, same sign-out).
- Signed in: account shelves merge into Home, account liked songs join the
  Liked list, account playlists open in the existing detail sheet, and likes
  are mirrored to Liked Music.

## Merged catalogue + settings (done)

- `MusicLibrary.catalog` defaults to **both**; Home, Search and Explore merge
  Deezer, public YouTube Music and the signed-in account. Deezer browse ids
  are tagged `dz:`, YouTube Music ids `yt:`, account playlists `account:` so
  every card opens the right service.
- `MusicSettings` + `MusicEqualizer` port the Nuvio/BitChord playback
  preferences: audio quality, autoplay, skip silence, speed, crossfade,
  equalizer, download quality, Wi-Fi-only downloads.
- The full Music settings sheet also carries the catalogue choice, account,
  notification-control status and audio-source choice. Audio-quality caps
  apply only to YouTube streams so FLAC is never filtered.
- Explore also reads YouTube Music's mood/genre categories.

## Local, servers, party and tools (done)

- `LocalMusicRepository` + the Local tab: MediaStore scan, album art,
  READ_MEDIA_AUDIO permission and direct `content://` playback.
- `WebDavClient` + `NavidromeClient` and `NuvioServersScreen`: folder browser,
  Subsonic album/song browser, auth and direct streams.
- `MusicPartyServer` + `NuvioPartyScreen`: a LAN HTML page where guests search
  Deezer and add songs to the host queue; `MusicPlayer.enqueue` backs it.
- `SpotifyImport`: public embed page → local playlist, with Deezer matching and
  a YouTube fallback.
- Google Drive upload via the system `OpenDocumentTree` provider.
- `MusicWidgetProvider` + widget layout/info: artwork, title and transport.
- Full `NuvioDownloadsPage` and `NuvioUserPlaylistPage`.

## Bit-perfect USB (done)

- `audio/decent-usb-audio-driver` — LastWave's MIT-licensed native USB Audio
  Class driver (JNI/CMake) is built as an in-tree Gradle module.
- `music/audio/` — `MusicBitPerfectUsb`, `ExclusiveUsbOutput`,
  `MusicBitPerfectAudioSink`, `MusicAudioRenderersFactory`, `UacFeatureVolume`
  and `ExclusivePcmGain`, adapted to Lucid's settings and player.
- `MusicPlayer` builds with the USB-aware renderers factory; supported
  lossless PCM goes direct through the exclusive sink, everything else falls
  back to Media3's normal sink.
- Settings → Music → USB audio requests Android USB permission and shows the
  live `active`/`bitPerfect` status.

## Phase 2b — remaining BitChord backend

Audit of the source section: **103 Kotlin files / ~28,000 lines** under
`composeApp/src/androidMain/kotlin/com/nuvio/app/music`, plus the engine
under `music/engine`, resources and fonts.

### Portable without app-level dependencies

The engine package is self-contained except for Ktor:

- `engine/data/model/*` — `Song`, `HomeShelf`, `ShelfItem`, `SearchResult`,
  `ArtistRef`, `Account`, `LibraryState`, queue tiers, artwork helpers.
- `engine/data/innertube/{Innertube,InnertubeParser,UploadedTracks}` —
  WEB_REMIX client and response parser (~150 KB).
- `engine/data/{YtMusicRepository,TrackMatcher,Http,DebugLog,LocaleTags,LikeState}`.
- `engine/data/lyrics/*` — lrclib, Bini, TTML and YouTube lyrics.
- `engine/data/webdav/WebDavAuth`, `engine/auth/DataSyncId`.

Needs new Gradle dependencies: `ktor-client-core`, `ktor-client-okhttp`,
`ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`
(version 3.5.0 is already in `libs.versions.toml` as `ktor`).

### App-coupled (port or adapt)

- `MusicPlayer.kt` + `MusicPlaybackService.kt` — Media3 `MediaSession`,
  bit-perfect USB renderers, queue persistence, sleep timer.
  Needs `androidx.media3:media3-session` and the audio/USB module.
- `MusicViewModel.kt` — home/search/explore/library/account state.
- Account library — `YouTubeAccountViewModel`, cookies, `Innertube.cookie`;
  can reuse Lucid's MediaServiceCore YouTube sign-in instead of porting a
  second account stack.
- `MusicLibrary`/`MusicDownloads`/`LocalMusicRepository` — likes, playlists,
  downloads, MediaStore scan.
- `MusicScreen.kt` and the 20+ screens — 515 Compose-resource strings;
  either generate a compatibility string map from
  `composeResources/values/strings.xml` or rewrite to `tr()`/literals.
- `MusicSourcesScreen`, `MusicIntegrationsScreen`, `MusicNavidrome*`,
  `MusicWebDav*`, `MusicParty*`, `MusicSpotify*`, `MusicDriveUploader`,
  `MusicWidgetProvider`, `MusicEqualizer`, USB audio.

### Recommended order

1. Vendor engine/model/innertube + Ktor deps; compile a `YtMusicCatalog`
   adapter with anonymous home/search/detail.
2. Map `Song.videoId` to Lucid's stream resolver; play through the current
   `MusicPlayer` and keep the notification/mini player.
3. Port `MusicPlayer` + `MediaSession` only if the account/download features
   need it; otherwise keep the existing player.
4. Add the account library through the existing YouTube sign-in.
5. Port Downloads, Local/MediaStore, WebDAV and Navidrome.
6. Party, Spotify import, Drive upload, widgets, equalizer and USB audio.
7. Add the 515 strings and the remaining screens, then the test matrix.

Do not copy the engine into the tree without the Ktor dependencies: the
Innertube client will not compile without them.
