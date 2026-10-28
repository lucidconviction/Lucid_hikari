# Lucid_hikari Feature Enhancement Plan
Based on Nuvio_robbdeeze Architecture Analysis

## Executive Summary
Nuvio_robbdeeze is a Kotlin Multiplatform Compose app (composeApp + tvApp + iosApp + androidApp) with sophisticated IPTV features. Lucid_hikari (Android-only, ExoPlayer + Compose) should adopt key architectural patterns from Nuvio to achieve feature parity and superior UX.

---

## 1. Architecture Improvements

### 1.1 Introduce Shared State Stores (Nuvio Pattern)
Nuvio uses dedicated stores:
- `MultiWindowStore` - manages multi-view slots, upcoming playlists, audio focus
- `IptvPlayerStore` - player launch triggers, add-source triggers
- `IptvRepository` - single source of truth for all IPTV data
- `StreamValidationStore` - stream health statuses
- `QuickChannelList` - pinned channels
- `StreamValidationController` - background scanning

**Lucid Action**: Create equivalent stores in `data/` package:
- `MultiWindowStore` - slot management, upcoming playlists, audio focus
- `StreamValidationStore` - health statuses with auto-refresh
- `QuickChannelStore` - pinned channels
- `IptvRepository` - unified data access layer

### 1.2 Navigation & Screen Structure
Nuvio uses `PageStateStore` for save/restore scroll position and UI state.
**Lucid Action**: Implement `PageStateStore` pattern for all screens.

---

## 2. IPTV Screen Redesign (Nuvio IptvScreen.kt)

### 2.1 TV Mode (≥1024dp breakpoint)
Nuvio uses `BoxWithConstraints` with 1024dp breakpoint:
- **Fixed Top Bar**: Persistent search bar, back button, source selector, lock button
- **Source Chips Row**: Horizontal scrolling chips with channel counts (ALL + each source)
- **Category Chips Row**: Horizontal scrolling category filter
- **Featured Bento Grid**: Hero + 2 side cards with live badges
- **Quick Channels Section**: Pinned channels horizontal scroll
- **Recommended Channels Grid**: Adaptive grid (200dp min)
- **Playlists Section**: Collapsible cards per source type (M3U, XT, Stalker, Addon, EPG)
- **VOD Series Section**: Series with season/episode navigation
- **History Section**: Recently watched horizontal scroll
- **Bottom Status Bar**: Server status, latency, upcoming, clock

**Lucid Action**: Refactor `IptvScreen.kt` to split into `IptvTvMode` and `IptvMobileMode` with 1024dp breakpoint.

### 2.2 Mobile Mode
- Scaffold with TopAppBar
- SearchSection
- QuickAccessSection (favorites)
- HistorySection
- QuickChannelsSection
- PlaylistsSection (expanded cards)
- VodSeriesSection
- SourceChipsSection (horizontal scroll)
- CategoryChipsSection
- Expandable channel groups by category
- Favorites section (expandable)
- AddSourceBottomSheet with Xtream panel

### 2.3 Xtream Panel (Already Exists in Lucid)
Nuvio has full Xtream support in AddSourceBottomSheet:
- Panel URL, Username, Password fields
- Test/Fill button → calls `IptvProvider.validateXtream()`
- Validates via `player_api.php` before saving
- Inline error display
- Auto-fills m3u URL from panel

**Lucid Status**: ✅ Already implemented (recent commit). Verify UI matches.

---

## 3. Channel Cards & Interactions

### 3.1 Channel Card Components (Nuvio `TvChannelCard` / `ChannelCard`)
Both modes share features:
- **Poster/Logo** with gradient overlay
- **Top-left**: StreamStatusDot (green/red/gray)
- **Top-right**: QuickPin (☆/★), Favorite (♥/♡), MultiWindow (⊕)
- **Center**: Play button overlay
- **Bottom**: Channel name, group, EPG now/next row
- **Long press**: EPG sheet
- **Click**: Play
- **Long press**: EPG sheet

**Lucid Action**: Create unified `ChannelCard` composable with all these features.

### 3.2 Channel Surf Overlay Buttons (NEW REQUEST)
User wants overlay buttons on far left/right edges:
- Appear when controls visible
- Timeout with controls
- Left: Previous channel
- Right: Next channel
- Only visible when `channelSurfController != null` (IPTV playlist channels)

**Lucid Action**: Add `ChannelSurfOverlay` composable to `player_controller.xml` and `PlayerActivity.kt`.

---

## 4. Multi-Window / Multi-View

### 4.1 Nuvio's MultiView Architecture
- **MultiViewStore**: Manages slots, streams, upcoming playlists, audio focus, pause state
- **tvApp/MultiViewScreen.kt**: 
  - Grid layout (2-3 columns based on max slots)
  - SlotCard with video surface, play/pause, audio focus, mute, close
  - EmptySlotCard for empty slots
  - UpcomingPlaylistPanel with per-slot playlist rows
  - Dynamic column count (2 for ≤4 slots, 3 for 5-6, 3 for 7+)
  - `calculateMaxSlots()` based on device RAM
  - `MultiViewStore` persists upcoming playlists per slot

### 4.2 Lucid Current State
- Has `MultiViewScreen.kt` with 13 layouts
- `PlayerPool.kt` for ExoPlayer pooling
- `MultiViewLayoutCard` in Settings
- Missing: Upcoming playlist panel, audio focus management, slot persistence

**Lucid Action**:
1. Create `MultiWindowStore` (DataStore/Room) for slot persistence
2. Add `UpcomingPlaylistPanel` to `MultiViewScreen`
3. Add audio focus indicator to `SlotCard`
4. Implement `MultiViewStore` with upcoming playlist per slot

### 4.3 Multi-Window Button in Player Controls
User wants a multi-window button in player controls:
- Add to `player_controller.xml` pill row (after Auto-Next)
- Click → opens MultiViewScreen with current stream
- Current stream auto-added to first available slot

**Lucid Status**: ✅ Button added (recent commit). Need to wire navigation.

---

## 5. Player Enhancements

### 5.1 Nuvio Player Features
- **TvPlayerEngine** (tvApp) / **PlayerEngine** (composeApp) - wrapper around ExoPlayer
- **PlayerLaunch** data class for cross-screen launch
- **PlayerSettingsStorage** - playback speed, icon sizes, dual audio, etc.
- **PlayerTrackPreferenceStorage** - audio/subtitle preferences
- **PlayerLaunchStore** - coordinates player launch from any screen
- **IptvPlayerStore** - coordinates add-source from player
- **ExternalPlayerLauncherEffect** - open in external player
- **ExternalPlayerPlatform** - platform-specific external launch

**Lucid Action**: 
- Create `PlayerLaunch` data class
- Create `PlayerLaunchStore` for cross-screen coordination
- Implement `IptvPlayerStore` for add-source trigger from player

### 5.2 Playback Hardening (Already Implemented)
- UA fallback (VLC-style for IPTV streams) ✅
- Retry window 45s ✅
- Live buffer tuning ✅

### 5.3 Playback Controls (Nuvio Pattern)
- Center cluster: prev/rew/play/ffwd/next
- Bottom pills: speed/episodes/prev-ep/next-ep/autoplay/auto-next/sources/quality/audio/subs/rotate/skip/enhance/fav/download/pip/options/lock/external
- Top bar: back/title/badges/fav/download/pip/options/lock
- Channel surf: D-pad up/down (TV), overlay buttons (mobile)

---

## 6. Channel Surf Implementation

### 6.1 Nuvio Approach
- **TV Mode**: D-pad up/down when controls hidden → `channelSurfController.prev()/next()`
- **Mobile**: No swipe (used for volume/seek), but overlay buttons requested
- **ChannelSurfController**: Tracks current index, channels list, `next()/prev()/switch(index)`
- **ChannelSurfToast**: Optional toast showing "⏺ Channel X/Y — Name"

### 6.2 Lucid Current State
- `ChannelSurfController.kt` exists with `next()/prev()/switch()`
- TV key handling in `PlayerActivity.kt` (KEYCODE_DPAD_UP/DOWN)
- Missing: Mobile overlay buttons (recently added overlay XML but not wired)

**Lucid Action**:
1. Wire `channelPrevOverlay`/`channelNextOverlay` click listeners to `channelSurfController.prev()/next()` ✅ (done)
2. Show/hide overlay with `controllerVisible` callback ✅ (done)
3. Add timeout to auto-hide overlay with controls
4. Ensure only visible when `channelSurfController != null`

---

## 7. Xtream Panel Implementation

### 7.1 Nuvio Xtream Flow
1. User taps "Xtream panel" pill in AddSourceBottomSheet
2. Three fields: Panel URL, Username, Password
3. "Test / Fill" button → `IptvProvider.validateXtream(creds)`
   - Calls `player_api.php?username=...&password=...`
   - Validates credentials, fetches server info
   - Returns `XtreamPreview(channelCount, serverName, m3uUrl)`
4. On success: Shows "Login OK", auto-fills `addLink` with m3u URL
5. On error: Shows error inline + toast
3. User presses "Add" → `addIptvPlaylist()` saves via `IptvProvider`

### 7.2 Lucid Status
- ✅ Xtream panel in Add dialog (recent commit)
- ✅ `XtreamCredentials` with validation
- ✅ `IptvProvider.validateXtream()` and `previewXtream()`
- ❌ Need to verify UI completeness and error handling

---

## 8. Search & Filtering

### 8.1 Nuvio Search
- **TV Mode**: Persistent search bar in top bar (always visible)
- **Mobile**: SearchSection in LazyColumn
- Filters by channel name, group, source
- Real-time filtering via `IptvRepository.setSearchQuery()`

### 8.2 Lucid
- Need persistent search in TV mode
- Mobile search section exists

---

## 8. Stream Validation

### 8.1 Nuvio
- `StreamValidationStore` - statuses map (url → Boolean)
- `StreamValidationController` - background scanning with `SourceScanState`
- `StreamValidationController.scans[id]` → `SourceScanState(checked, total, alive, dead, active)`
- UI shows scan progress in playlist cards

### 8.2 Lucid
- Has `StreamValidationStore` and `StreamValidationController` ✅
- Need to verify UI integration in playlist cards

---

## 9. Quick Channels / Favorites

### 9.1 Nuvio
- `QuickChannelList` - pinned channels with toggle
- `QuickChannelList.isPinned(name)` for UI
- QuickAccessSection in mobile (horizontal scroll)
- QuickChannelsSection in TV (horizontal scroll)
- QuickAccessCard with multi-window button (⊕)

### 9.2 Lucid
- Has `QuickChannelList` ✅
- Need to verify UI integration

---

## 10. Implementation Priority

### Phase 1: Core Architecture (Week 1)
1. Create `MultiWindowStore` with Room/DataStore
2. Create `StreamValidationStore` with auto-refresh
3. Create `QuickChannelStore`
4. Implement `PageStateStore` pattern

### Phase 2: IPTV Screen Redesign (Week 2)
1. Split `IptvScreen` into `IptvTvMode` / `IptvMobileMode`
2. Implement TV mode layout (1024dp breakpoint)
3. Source chips, category chips, featured grid
4. Playlists section with source type cards
5. Add search bar to TV mode top bar

### Phase 3: Channel Cards & Surf (Week 3)
1. Unified `ChannelCard` with all actions
2. Channel surf overlay buttons (left/right)
3. Multi-window button in pill row
4. Wire channel surf overlay to `channelSurfController`

### Phase 4: Multi-View Enhancement (Week 4)
1. `MultiWindowStore` with Room persistence
2. `UpcomingPlaylistPanel` in MultiViewScreen
3. Audio focus indicator in SlotCard
3. Wire multi-window button to navigation

### Phase 5: Player Integration (Week 5)
1. `PlayerLaunch` data class
2. `PlayerLaunchStore` for cross-screen launch
3. `IptvPlayerStore` for add-source trigger
4. Wire multi-window button to open MultiViewScreen with current stream

### Phase 6: Polish (Week 6)
1. Verify Xtream panel UI completeness
2. Stream validation UI in playlist cards
3. Quick channels integration
4. Search bar in TV mode
5. Real emulator screenshots

---

## 10. Key Files to Modify/Create

### New Files
- `data/MultiWindowStore.kt` - Room/DataStore for slots, playlists, audio focus
- `data/QuickChannelStore.kt` - Pinned channels
- `data/PageStateStore.kt` - Save/restore UI state
- `data/PlayerLaunchStore.kt` - Cross-screen player launch
- `data/IptvPlayerStore.kt` - Add-source trigger from player
- `ui/screens/IptvTvMode.kt` - TV mode layout
- `ui/screens/IptvMobileMode.kt` - Mobile mode layout
- `ui/components/ChannelSurfOverlay.kt` - Overlay buttons
- `ui/components/ChannelCard.kt` - Unified channel card
- `ui/screens/MultiViewScreen.kt` - Add UpcomingPlaylistPanel
- `data/MultiWindowStore.kt` - Upcoming playlist per slot

### Modified Files
- `IptvScreen.kt` → Split into TvMode/MobileMode
- `PlayerActivity.kt` - Channel surf overlay, multi-window button
- `player_controller.xml` - Add multi-window btn, overlay buttons
- `MultiViewScreen.kt` - Add UpcomingPlaylistPanel, audio focus
- `player_controller.xml` - Channel surf overlay buttons
- `IptvScreen.kt` - Split modes, add search bar
- `IptvModels.kt` - Add MultiViewSession fields
- `strings.xml` - New strings for overlay, multi-window

---

## 11. Testing Checklist

### Functional
- [ ] TV mode (≥1024dp) shows sidebar with search, sources, categories
- [ ] Mobile mode shows Scaffold with all sections
- [ ] Xtream panel: add panel → test credentials → save
- [ ] Channel surf: D-pad up/down (TV), overlay buttons (mobile)
- [ ] Channel surf overlay appears with controls, times out
- [ ] Multi-window button opens MultiViewScreen with current stream
- [ ] MultiViewScreen shows grid, audio focus, upcoming playlist
- [ ] Channel cards: play, fav, quick-pin, multi-window, EPG
- [ ] Multi-window button in player sends current stream to slot
- [ ] Channel surf overlay appears with controls, times out
- [ ] Stream validation shows in playlist cards
- [ ] Search works in both modes
- [ ] Xtream panel validates credentials before save

### Performance
- [ ] Smooth 60fps scrolling in LazyColumn/LazyVerticalGrid
- [ ] No frame drops during channel surf
- [ ] Multi-view 4+ streams play simultaneously
- [ ] Memory usage < 500MB with 4 streams

---

## 12. Dependencies to Verify

```kotlin
// build.gradle.kts additions if needed
dependencies {
    // Room for MultiWindowStore
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    
    // Coil for images
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

---

This plan provides a roadmap to bring Lucid_hikari to feature parity with Nuvio's IPTV experience while maintaining its Android-first architecture. The key is adopting Nuvio's state management patterns (stores), TV/mobile split, and multi-window architecture while leveraging Lucid's existing ExoPlayer foundation.
