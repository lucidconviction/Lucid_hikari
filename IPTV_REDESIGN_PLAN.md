# IPTV Tab Redesign Plan

## Goal
Replace VidNutz tab with Portal Nutz, add Quick Channels, redesign IPTV screen with collapsible playlists, improve player channel switching, add Xtream account management.

## Files to Remove/Drop
- `data/VidNutzModels.kt`
- `data/VidNutzCacheStore.kt`
- `data/VidNutzHubs.kt`
- `data/VidNutzRepository.kt`
- `screens/VidNutzScreen.kt`

## Files to Create (under `app/src/main/java/com/hikari/app/`)
### Data layer
- `data/QuickChannel.kt` — QuickChannel data class
- `data/QuickChannelList.kt` — default channels, categories, matching logic, pin state
- `data/CustomQuickChannelStore.kt` — persist custom quick channels (JSON file)
- `data/PortalEntry.kt` — PortalEntry data model
- `data/PortalLicense.kt` — PortalLicenseKey + LicenseStatus
- `data/PortalInstallStore.kt` — installed portals persistence
- `data/PortalNutzScraper.kt` — ported from Nuvio (lite version without license gating, keeps scraping)
- `data/XtreamAccount.kt` — saved Xtream account model
- `data/XtreamAccountStore.kt` — persists Xtream accounts

### UI layer
- `ui/screens/IptvQuickChannelsSheet.kt` — Quick Channels selection sheet (pills + resolved channels)
- `ui/screens/IptvPortalScreen.kt` — Portal Nutz screen (form + result list)
- `ui/screens/IptvXtreamScreen.kt` — Xtream account management screen

### Player
- `player/ChannelStripController.kt` — replaces/augments ChannelSurfController for pass-through URL list
- Update `player/PlayerActivity.kt` — show channel switcher overlay on demand, pass full channel list

## Files to Modify
- `ui/navigation/AppNav.kt` — remove VidNutz tab/route, add IPTV_NUTZ → IptvPortalScreen, wire up new routes
- `ui/screens/IptvScreen.kt` — complete redesign: collapsible playlist list, quick channels section, portal section
- `ui/screens/DetailScreen.kt` — pass `channelSurfChannels` for IPTV channels (already done in prior session)
- `data/AppStore.kt` — add prefs for quick channel pins, custom quick channels, Xtream accounts, installed portals
- `providers/IptvProvider.kt` — add methods for portal-managed playlists, Xtream accounts
- `player/ChannelSurfController.kt` — extend for full channel list (names + URLs + logos)
- `res/layout/activity_player.xml` — add channel-switch buttons to player controller layout

## Player Channel Switching Design
When `channelSurfController != null`:
- D-pad UP/DOWN → prev/next channel (already wired in PlayerActivity)
- New button `R.id.channel_prev_btn` and `R.id.channel_next_btn` in player_controller.xml
- When user long-presses on player → shows channel list bottom sheet (same as Nuvio's PlayerPlaybackOverlays)
- Channel list item: logo + name + group, tap to switch

## Collapsible Playlist List Design
- Each playlist is a `CollapsibleListSection` (like a dropdown)
- Header: playlist name + channel count + [Exp] badge if expired + [MW] button
- Tapping header expands/collapses
- Expanded: shows groups as chips; tapping a group navigates to catalog
- New button in header: "collapse all" / "expand all"

## Quick Channels Design
- Horizontal scrollable chip row: All | US | UK | CA | Sports | News | Premium
- Selecting a category shows a grid of QuickChannel cards (channel name + region tag)
- Tapping a QuickChannel resolves matching channels from ALL installed playlists
- Shows resolved channels in a new sheet — user picks which playlist/channel to play
- Pin: long-press → adds to top "Pinned" row
- Custom channels: + button in top bar opens add-dialog

## Portal Nutz Design
- Dedicated screen (replaces VidNutz tab)
- Search input for portal keywords
- "Search Portals" button → shows progress bar + results
- Each result: card with label, URL (truncated), channel count, expiration date, trial badge, Install button
- Installed portals appear in a separate "Installed" section below
- Tap installed portal → opens in playlist tab (adds as new provider)
- Expired portals shown in grey with "Expired" badge

## Xtream Account Design
- Screen accessible from IPTV tab header (gear icon or dedicated pill)
- List of saved Xtream accounts (url, username, display name)
- "Add Account" → form: URL, username, password, custom name
- Test button → shows connection status + channel count preview
- Save → adds as provider
- Each account shows: active channels, movies, series counts (fetched from API)
- Delete button per account

## Implementation Order
1. Port data models (QuickChannel, PortalEntry, XtreamAccount)
2. Port QuickChannelList + CustomQuickChannelStore  
3. Port PortalNutzScraper + PortalInstallStore + PortalLicense
4. Add AppStore functions for new prefs
5. Redesign IptvScreen (collapsible playlists + quick channels + portal nav)
6. Add player channel switching UI (buttons + overlay)
7. Build and verify

## Notes
- No new Gradle dependencies required
- Uses existing PlayerPool, OkHttp, Compose
- PortalNutzScraper uses public proxies (keep CORS/proxy support from Nuvio)
- Xtream account preview reuses existing `IptvProvider.previewXtream()`
- QuickChannelList default list: port ~50 channels from Nuvio (US/UK/CA + sports/news/premium)
- Player channel list passed via intent extra `"channelSurfChannels"` as JSON (already partially done in prior session — extend to include logos and full names)
