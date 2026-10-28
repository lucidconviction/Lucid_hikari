# Plan: Port MultiNutz & IPTVNutz Features to Hikari

## Overview
Port two feature sets from Nuvio_Robbdeeze into Lucid_hikari:
1. **VidNutz (MultiNutz)** — YouTube search via Invidious/Piped instances, trending/live/search, hub/sub catalog organized in rails
2. **IPTV NutZ** — Portal scraper that discovers and verifies IPTV subscriptions from Reddit catalogs

## Current Hikari State
- Already compiles (`:app:compileDebugKotlin` BUILD SUCCESSFUL)
- Existing tabs: Home, Anime, Search, My Stuff, Manga, IPTV, Stats, Telegram, Extensions, Settings
- Existing `IptvScreen.kt` — manual playlist management (no portal scraper)
- Existing `MultiViewScreen.kt` — multi-window playback (already wired)
- AppNav.kt — bottom bar with `BottomTabs` list + `Routes` object

## What Needs to Be Built

### 1. VidNutz (MultiNutz) — NEW TAB
**Screen**: `app/src/main/java/com/hikari/app/ui/screens/VidNutzScreen.kt`
**Models**: `app/src/main/java/com/hikari/app/data/VidNutzModels.kt`
**Repository**: `app/src/main/java/com/hikari/app/data/VidNutzRepository.kt`
**Route**: `Routes.VID_NUTZ = "vid-nutz"`
**Tab**: Add to `BottomTabs` in AppNav.kt with `Icons.Filled.VideoLibrary` (or a custom icon)

**Features to port:**
- Search via Invidious + Piped instances (rotating proxies)
- Trending / Live / Category browsing
- Hub/Sub chip navigation (VidNutzHubs static catalog)
- Video grid with thumbnails, play action launches player
- Recent searches persistence (DataStore)
- Video resolution/quality selection

**Player integration:**
- Use existing Hikari player (PlayerActivity or ExoPlayer)
- Video URL extracted from Invidious/Piped API response → pass to player

### 2. IPTV NutZ — ENHANCE EXISTING IPTV TAB
**File**: Create `app/src/main/java/com/hikari/app/ui/screens/IptvNutZScreen.kt`
**Scraper**: `app/src/main/java/com/hikari/app/data/PortalNutzScraper.kt`
**Route**: Same `Routes.IPTV` — add a "NutZ" sub-mode or tab switch inside the screen

**Features to port:**
- Reddit-based IPTV portal discovery (CATALOG_SUBS: IPTV_ZONENEW, FreeIPTV, iptvguru, IPTVfree)
- Portal verification (login test + channel count)
- Cache verified portals for 2h
- Display verified portals with expiry, max connections, active connections
- Add portal → auto-parse M3U playlist → feed into existing IptvScreen

### 3. Navigation Changes (AppNav.kt)
- Add `Routes.VID_NUTZ` constant
- Add `BottomTab` for VidNutz (inserted after IPTV, before Stats)
- Register `composable(Routes.VID_NUTZ) { VidNutzScreen(...) }` in NavHost
- Add `Routes.IPTV_NUTZ` sub-route if needed, or handle within IptvScreen

### 4. Dependencies
No new Gradle dependencies needed — all uses existing:
- `kotlinx-serialization-json` (already present)
- `okhttp3` (already present)
- `coil3` for thumbnails (already present)
- `androidx.datastore-preferences` (already present)

## File List

### New Files
1. `app/src/main/java/com/hikari/app/data/VidNutzModels.kt` — data classes
2. `app/src/main/java/com/hikari/app/data/VidNutzRepository.kt` — search/fetch logic
3. `app/src/main/java/com/hikari/app/data/VidNutzCacheStore.kt` — DataStore cache
4. `app/src/main/java/com/hikari/app/data/PortalNutzScraper.kt` — portal discovery
5. `app/src/main/java/com/hikari/app/ui/screens/VidNutzScreen.kt` — main UI
6. `app/src/main/java/com/hikari/app/ui/screens/IptvNutZScreen.kt` — NutZ portal screen

### Modified Files
1. `app/src/main/java/com/hikari/app/ui/navigation/AppNav.kt` — new routes + tabs + NavHost entries
2. `app/src/main/java/com/hikari/app/ui/screens/IptvScreen.kt` — add NutZ mode toggle

## Implementation Order
1. Write VidNutzModels + Repository (data layer first)
2. Write VidNutzScreen (UI)
3. Wire VidNutz into AppNav
4. Write PortalNutzScraper
5. Enhance IptvScreen with NutZ tab
6. Wire into AppNav
7. Build & verify

## Risks
- Invidious/Piped instances may be down or rate-limited — rotation handles this
- Reddit scraping may break if Reddit changes API — use OAuth client IDs from Nuvio
- Hikari uses `com.hikari.app.data` package; Nuvio uses `com.nuvio.app.features.*` — all imports must be rewritten
- `PlayerLaunch` model in Hikari differs from Nuvio's — adapt play callback
