# Plan — Telegram autoplay/PiP preview + report-sourced additions

**Status: approved 25 Nov 2026, with two addenda:**
- **PiP:** both — the in-player corner preview **and** Android system PiP, with Android PiP also wanted around the in-app PIP feature globally.
- **WebView hardening:** every site redirect and every external app call is blocked and asks **Always block / Allow once / Always unblock**.

Confirmed: Telegram queue uses the loaded list only; IPTV group management is included.

**Implementation order:** WebView hardening → Telegram autoplay/preview → IPTV groups → Android system PiP integration.

**PiP decision (final):** when the app is backgrounded with in-app PIP panes open, the **active pane is promoted** to an Android system PiP window. Other panes pause and are restored on return. See §3.8. The plan is final; no open decisions remain.

Source reports: `/Users/robbdeeze/Documents/projects/app Upstream reports /upstream-report-2026-11-25.md` (also `LATEST.md`, `INDEX.md`, `PLAYBOOK.md`).

---

## 1. What the report says

The 2026-11-25 report's Hikari section:

- Lucid Hikari is at `4fbd18d4` / 0.16.0, clean CI, dirty local tree from the dock work.
- **"Hikari: add features locally, port nothing."**
- Canonical `codegeasse1/hikari` only landed version-bump commits (0.12.145–0.12.161); there are **no safe upstream patches to port**.
- `upstream/main` is **577 behind / 203 ahead**; do not merge or rebase.
- Fork Live TV fixes from 0.12.76 predate 0.16.0 and are not applicable.
- The report's Hikari feature ideas:

| Idea | Report call | Current Lucid Hikari state |
|---|---|---|
| Optional movie-collection row on movie details | new feature, small/low | **Already implemented**: `DetailScreen.kt` shows TMDB `belongs_to_collection` above Related/Similar, gated by the `collections` TMDB module (`TmdbMeta.kt`). No work. |
| Channel category management: move, rename, hide, lock | upgrade, medium/medium | **Missing.** This is the one report idea worth adding. |
| Preview follows OK only, not scroll | upgrade, medium/low | **Not applicable to Hikari's current IPTV layout.** Hikari has no focus-driven guide preview; "PIP Mode/Ch Preview" is a deliberate floating-window action, not a preview that jumps while scrolling. Skip unless we build a focus preview later. |
| Sports future-game jump to its channel/providers | upgrade, medium/medium | **Already covered** by the per-game **Watch on** search for live/upcoming fixtures (`SportsGameScreen.kt`). No new work unless a specific gap is reported. |

Cross-app items in the same report (recording launch safety, libmpv fallback, Usenet tuner, frosted-glass sidepanel, hardware image decoding) are Nuvio TV/Mobile work, not Hikari surfaces. Dependency upgrades and upstream merges are explicitly out of scope.

### The user request on top of the report

Telegram videos from the Telegram page should **auto-play the next video**, with the existing **corner "Up next" preview** before the current one ends. This is not in the report; it is a local feature request and the main part of this plan.

---

## 2. What we will add

### P0 — Telegram autoplay next + corner preview
Open a Telegram video from a chat/channel feed and make the rest of that loaded feed a queue:

- 12 seconds before the current video ends, the next video starts **muted in the corner** ("Up next · Ns").
- Tapping the corner preview promotes it immediately.
- On natural end, the preview continues fullscreen and the main player takes over in place.
- If the preview is disabled but autoplay is on, the next video starts at the end without the corner preview.
- If autoplay is off, nothing advances.
- The existing **Autoplay** and **Next-video preview** settings apply; no new setting.

### P1 — IPTV channel group management (from the report)
On a playlist page, each group tile gets a management sheet:

- **Rename** the group label.
- **Move up / Move down** in the group grid.
- **Hide** the group (with a "Hidden groups" restore entry).
- **Lock / unlock** a group: while locked, rename/move/hide are disabled; playback is unaffected.

Scope is the playlist's own `group-title` mode. Generated modes (`Language`, `Category`, `Country`) are not editable.

### Explicitly not adding
- No upstream merge/cherry-pick: the report says there is nothing safe to port.
- No Nuvio TV-specific recording/libmpv/Usenet/sidepanel work.
- No dependency/Compose/Gradle jumps.
- No new preview-on-OK setting: Hikari has no focus preview to change.
- No TMDB collection row: already shipped.
- No Sports future-game jump: already covered by Watch on.
- No "Are you still watching?" guard in this pass (can be a later optional add).

---

## 2.5 P0 — WebView redirect and app-link hardening (approved addendum)

This extends the redirect dialog already in `WebViewActivity`:

- **Site redirects:** every automatic main-frame redirect, same-site or cross-site, is blocked and asks. Direct link taps remain navigations and load (a redirect is not a tap).
- **App calls:** every external app call (`intent://`, `market://`, `mailto:`, `tel:`, custom schemes) is blocked and asks, including a tapped one. No silent external launch.
- **Prompt:** exactly **Always block / Allow once / Always unblock**.
  - **Always block:** persist the host (site) or app key (scheme/package) and do not ask again.
  - **Allow once:** this redirect/app call only.
  - **Always unblock:** persist the host/app key and open it.
- **Persistence:** a new synchronous `WebViewLinkGuard` (SharedPreferences-backed, like `RedirectAllow`), with separate redirect and app-call block sets; allowed entries continue to use the existing "Allowed redirect links" list. "Always block" must not reuse the ad-block list.
- **Settings:** add the blocked app/redirect entries (or a clear action) under Settings → Privacy & Browsing → WebView safety, so "Always block" is reversible.
- **One dialog at a time**, per-URL throttle; the existing Cloudflare-verification and donation guards stay first.
- **Initial page load, reload, and typed URLs** are not redirects.

The current code already has the site dialog and the strong automatic-redirect rule; this addendum renames the choices, adds the persistent block set, and routes `handleExternalScheme` through the same prompt.

---

## 3. Telegram autoplay + PiP preview — design

### 3.1 What "PiP preview" means here

**Approved: both.**

- The in-player corner preview is the muted "Up next" window in `PlayerActivity`.
- Android system PiP already exists in `PlayerActivity`; it will continue to work alongside the corner preview.
- The in-app PIP feature (`PipMode`/`PipWindowStore`, the floating panes over MainActivity) is a separate mechanism and is **not** Android system PiP. The approved addendum adds Android system PiP around that feature globally; §3.8 defines the active-pane promotion.
- TDLib-backed Telegram videos deliberately stay in the full player; public Telegram CDN videos can use Android system PiP.

### 3.2 Queue model

A new process-wide store, modelled on `PlaybackQueueStore`:

```kotlin
// com.hikari.app.player.TelegramPlaybackQueue
data class Item(
    val title: String,
    val sourcesJson: String,   // same JSON shape as the "sources" intent extra
    val fileId: Int = 0,       // TDLib prewarm; 0 for public CDN videos
)

object TelegramPlaybackQueue {
    fun set(items: List<Item>, at: Int)
    fun current(): Item?
    fun next(): Item?
    fun hasNext(): Boolean
    fun advance(): Item?
    fun clear()
}
```

- The Telegram screen builds the queue from the **currently loaded list** (the current page plus any older pages already loaded), with the tapped video's index.
- Process-lifetime, like `PlaybackQueueStore`; not persisted across process death. A player recreation in the same process keeps it.
- No content-provider call is needed: the next video's source is already known.

### 3.3 Telegram screen changes

Two feed paths, both get the same treatment:

1. **Signed-in / TDLib:** `TelegramChatVideos` and the account branch of `TelegramChannelVideos` receive `List<Td.ChatVideo>`.
   - Build `TelegramPlaybackQueue.Item` for every video, in list order.
   - TDLib source JSON: `TdFileDataSource.uriFor(fileId, chatId, messageId)`, `local = true`, no headers.
   - Set the queue at the tapped index, then call the existing `playTdVideo`.
2. **Anonymous / public web:** the public branch of `TelegramChannelVideos` receives `List<TelegramVideo>`.
   - Build items from the CDN URL + the existing `User-Agent`/`Referer` headers.
   - Set the queue at the tapped index, then call `playTelegramVideo`.

Implementation points:

- Extract the source-JSON builders out of `playTdVideo`/`playTelegramVideo` so the current video and the queue items cannot drift (`tdSourceJson(video)`, `webSourceJson(video)`).
- `ChatVideoCollection` gets an index-aware play callback (or the parent computes the index with `list.indexOf(video)`), so list/tile/poster views all enqueue the same list.
- The public `LazyColumn` uses `itemsIndexed` for the same reason.
- Chat search results (`ChatVideoSearchResults`) can enqueue their own result list; include it if the refactor stays small, otherwise it is a follow-up.
- Link rows (one video each) have no queue and behave exactly as today.
- The queue is only the **loaded** list. At the last item, autoplay stops. Fetching the next Telegram page from inside the player is not in v1 (open decision).

### 3.4 Player integration

`PlayerActivity` already has the whole corner-preview pipeline. The additions:

- Extend `NextPlaybackPreview` with a `telegram: TelegramPlaybackQueue.Item?` field.
- In `prepareNextPlaybackPreview`, check the Telegram queue **first**:
  - If `TelegramPlaybackQueue.next()` exists, parse its `sourcesJson` with the existing `parseSourcesPayload`.
  - Set the preview title and sources directly; no `ContentRepository`, episode or shelf lookup.
  - Call `Td.prewarm(fileId)` in the background for TDLib items.
- In `showNextPlaybackPreview`, make the preview data source Telegram-aware:
  - Wrap the existing `DefaultDataSource`/OkHttp chain in `TdDataSourceFactory(...)`.
  - Skip the media cache for `hikari-td://` sources (the main player already does this).
  - Keep the existing header handling for public CDN URLs.
- In `handOffNextPreview`, if `preview.telegram != null`:
  - Promote the frozen preview fullscreen (same as episodes).
  - Call a new `startTelegramNext(item, sources, position)` in place:
    - `TelegramPlaybackQueue.advance()` exactly once.
    - Set `sources`, `currentIndex = 0`, `startPositionMs = preview position`, `playSource(0)`.
    - Update the player title and `intent` extras (`title`, `sources`).
    - Hide episode/series buttons; show the Autoplay pill when the queue has more.
    - Do not create an episode/series history entry; Telegram videos do not have one today.
  - The preview's position is the few seconds already shown muted; the next video continues from there, exactly like the episode handoff.
- In `onPlaybackEnded`:
  - If a preview started, existing handoff handles it.
  - If no preview but the Telegram queue has a next item and autoplay is on, start it directly (start position 0) after showing the loading cover.
  - Respect `autoplayNextOn`; respect `nextPreviewOn` only for showing the preview, not for advancing.
- The `autoplayBtn` is currently only visible for series. Make it visible when a Telegram queue is active, so the global Autoplay setting is reachable from the player.
- No changes to `PipLauncher`/Android PiP.

### 3.5 Edge cases

- **TDLib file references expire.** Every queue URI includes `chatId`/`messageId`, so `TdFileDataSource.repair` can refresh the file; the preview data source must use the same `TdDataSourceFactory`.
- **TDLib preview buffering.** Prewarm the next file when the preview is resolved (~28 s before the end). If the preview is not ready at 12 s, the existing preview watchdog/handoff behavior applies.
- **Public Telegram CDN.** The preview needs the same `User-Agent`/`Referer` headers as the main source; the existing `setDefaultRequestProperties(src.headers)` covers it.
- **Low-memory TV.** `showNextPlaybackPreview` already skips the corner preview on tiny-memory TVs; the direct autoplay path still works at the end.
- **Preview off / autoplay off.** Both are independent; preview off → direct advance; autoplay off → end screen.
- **Last item / last loaded page.** No next; end screen. Loading the next Telegram page from inside the player is a follow-up.
- **Duplicate or deleted posts.** Queue keys use chat/message IDs; if Telegram refuses a deleted post, the player's existing error path moves to the next source or ends. The queue should skip a dead item or stop; decide during implementation.
- **Rotation / process death.** Queue is process-wide; player recreation keeps it. Process death loses the queue, same as `PlaybackQueueStore`; the player restarts on the current video only.
- **Android PiP.** TDLib videos stay full-player; public Telegram videos keep today's PiP behavior. The corner preview remains the autoplay surface.

### 3.6 Files

| File | Change |
|---|---|
| `player/TelegramPlaybackQueue.kt` *(new)* | Queue items, index, next/advance. |
| `ui/screens/TelegramScreen.kt` | Build queues for chat/channel feed paths; extract source-JSON builders; index-aware callbacks; search results if small. |
| `player/PlayerActivity.kt` | Telegram branch in `NextPlaybackPreview`, prepare/show/handoff/direct advance; Telegram-aware preview data source; show Autoplay pill. |
| `telegram/TdStream.kt` | No functional change expected; reuse `TdDataSourceFactory`. |
| `test/.../player/TelegramPlaybackQueueTest.kt` *(new)* | Queue set/next/advance/clear. |

### 3.7 Tests

- Unit: queue order, advance bounds, empty queue, duplicate titles.
- Manual on the Fold:
  - TDLib channel with 3+ videos: tap first, wait for the 12 s preview, hand off, confirm title/video, audio resumes, queue continues; last item ends.
  - Public Telegram channel: same over CDN.
  - Tap the corner preview early.
  - Preview off; autoplay off; both off.
  - Rotate during preview and during playback.
  - TDLib video with a stale file reference (reopen a long-loaded feed).
  - Low-memory TV path (if available).
  - Android PiP on a public Telegram video.

### 3.8 Android system PiP — active-pane promotion (approved)

This is the "Android PiP around the in-app PIP feature" half of the approved PiP scope. The in-player corner preview and `PlayerActivity`'s existing Android PiP are unchanged; this section is only for the floating panes hosted by `PipHost` over `MainActivity`.

#### What "active pane" means
- `PipWindowStore` gains `activePaneId`, set by `show`, `add`, `bringToFront`, pane tap/focus and restore.
- Fallback: the newest pane. If the active pane is minimized, it is promoted back for the PiP window; if there are no panes at all, no system PiP is entered.

#### Entering system PiP
- **API 31+:** `MainActivity` calls `setPictureInPictureParams(... setAutoEnterEnabled(true))` only while:
  - PIP Mode is on (`PipModeSettings.isOn`),
  - at least one in-app pane exists,
  - the device/app permits PiP (the same `isPipAllowedNow` check `PlayerActivity` uses).
  Params are refreshed when the pane list or active pane changes.
- **API 26–30:** `onUserLeaveHint` calls `enterPictureInPictureMode` under the same conditions.
- Aspect ratio follows the active pane's video where known, 16:9 otherwise; API 31+ enables seamless resize.
- Android TV: keep the in-app overlay only. If the device does not advertise PiP, nothing changes on TV.
- No new setting: the existing PIP Mode/Ch Preview toggle is the switch. Turning it off while in PiP exits and clears.

#### What the PiP window shows
- `PipHost` reads a new `PipWindowStore.systemPip` state.
- In system PiP it draws a **full-screen black host with only the active pane**, reusing that pane's player and its stored position; the rest of the app (nav, pages, taskbar, other panes) is not visible.
- Only the active pane is composed/decoded in PiP; the other panes are released and their positions saved. This avoids the two-decoder stutter that made `PlayerActivity` release its corner preview in PiP.
- The active pane keeps its mute/volume state; other panes are silent.
- No drag/resize/lock chrome in the system PiP window; a tap expands the app (system behavior). The normal in-app controls return when MainActivity is back.

#### Leaving / returning
- `onPictureInPictureModeChanged(false)` clears `systemPip`; `PipHost` restores the normal shelf and re-composes the other panes at their saved positions. Panes that were playing resume; paused panes stay paused.
- Closing the system PiP with its ✕ finishes MainActivity; `onDestroy` clears/releases the pane players so audio cannot continue invisibly.
- Rotation while in PiP keeps the active pane; the normal layout is recalculated on return.
- If the user changes panes while in PiP (not possible in the small window, but can happen via a remote/notification action later), `activePaneId` updates and the params refresh.
- `PlayerActivity`'s own Android PiP and the Telegram queue are untouched.

#### Files
| File | Change |
|---|---|
| `MainActivity.kt` | PiP permission/params, API 31+ auto-enter, API 26–30 `onUserLeaveHint`, `onPictureInPictureModeChanged`, `onDestroy` cleanup. |
| `player/PipMode.kt` | `activePaneId`, `systemPip` state/flow, `PipHost` system branch with the minimal active-pane view; keep other panes' positions. |
| `AndroidManifest.xml` | `android:supportsPictureInPicture="true"` on `MainActivity` (PlayerActivity already has it). |
| `player/PlayerActivity.kt` | Optional: move/share `isPipAllowedNow` so MainActivity and the player use one check; no behavior change. |
| `test/...` | Active-pane selection and system-PiP state unit tests where possible. |

#### Tests
- API 31+ phone: two panes, focus the second, press Home → system PiP shows the second; first is paused/released; return restores both; the second resumes.
- API 26–30 phone: same via `onUserLeaveHint`.
- Active pane minimized before Home → it is promoted; the other pane does not decode.
- PIP Mode off / no panes / PiP denied by OS → no system PiP.
- Close PiP ✕ → audio stops, panes are gone on next launch.
- Tablet, rotation, and a device that reports no PiP feature.
- `PlayerActivity` Android PiP and the Telegram corner preview still behave as before.

---

## 4. IPTV channel group management — design

### 4.0 Baseline — the IPTV tab and page stay in place

- The tab is already in `BottomTabs` as `Routes.IPTV` ("IPTV", `Icons.Filled.LiveTv`) and is wired in `AppNav` as `composable(Routes.IPTV) { IptvScreen(nav) }`.
- `IptvScreen` is the page; `IptvPlaylistScreen` is the per-playlist group page that this feature extends.
- The tab's existing visibility switch (`AppStore.iptvTabFlow`, off by default, Settings → Taskbar buttons) is **not changed** by this work. If the user has a playlist and wants the tab, the existing switch turns it on; the group actions are additive and never remove or replace the page.
- Acceptance: after the change the IPTV tab still opens the same page, group actions are available on the playlist page, and channel playback/channel lists are untouched.

### 4.1 UX

On `IptvPlaylistScreen` (the group grid):

- Long-press a group tile (or its overflow) opens a sheet:
  - **Rename group**
  - **Move up / Move down**
  - **Hide group**
  - **Lock / Unlock**
- A locked group shows a small lock badge; rename/move/hide are disabled until unlocked.
- Hidden groups are not drawn in the grid. A footer row **"Hidden groups (N)"** opens the same sheet to restore one.
- "All channels" is not editable.
- The merged **View All Playlists** grid applies the same hidden/order/label overrides per playlist.

### 4.2 Storage

A per-playlist override, persisted in `AppStore` (new key + flow; backward-compatible default = empty):

```kotlin
@Serializable
data class IptvGroupOverride(
    val name: String,       // original group-title
    val label: String = "", // display label only
    val order: Int = 0,     // lower first; unset = after ordered groups
    val hidden: Boolean = false,
    val locked: Boolean = false,
)
```

- Keyed by provider id; the original `group-title` is the stable key.
- Catalog IDs remain the original `catalogIdForGroup(name)`, so channel lists and deep links keep working; only the tile's label/order/visibility changes.
- Hidden is a **category-tile** setting: the channels remain in "All channels" and in scoped search. This avoids making channels unreachable when a group is hidden.
- Lock only protects the tile's own editing; it does not block playback or channel access.

### 4.3 Applying overrides

`readGroups` (and the merged grid reader) applies:
1. Filter `hidden`.
2. Sort: explicit `order` first (ascending), then the existing biggest-first/name order.
3. Display `label` when set; keep `name` for the filter/catalog lookup.
4. `locked` only affects the action sheet.

Only `IptvPlaylist.normalizeGroupMode(mode) == "groups"` is editable. Language/category/country modes keep their current generated order.

### 4.4 Files

| File | Change |
|---|---|
| `data/IptvGroupPrefs.kt` *(new)* | Serialized overrides, lookup/merge helpers. |
| `data/AppStore.kt` | New key, flow, save method; default empty. |
| `ui/screens/IptvScreen.kt` | Group tile long-press/overflow; management sheet; rename dialog; move/hide/lock actions; hidden-groups footer; apply overrides in `readGroups` and the merged grid. |
| `test/.../data/IptvGroupPrefsTest.kt` *(new)* | Ordering, hidden, rename, lock, serialization. |

### 4.5 Tests

- Unit: ordering with/without explicit order, hidden filter, rename mapping, lock no-op.
- Manual: rename/move/hide/restore/lock; restart persistence; merged grid; channel list still opens under the original ID; TV focus and long-press; no effect on Language/Category/Country modes.

---

## 5. Sequencing and effort

### Phase 0 — WebView redirect/app-link hardening
1. Rename the site prompt choices to **Always block / Allow once / Always unblock**.
2. Add the persistent `WebViewLinkGuard` block sets and the app-call prompt in `handleExternalScheme`.
3. Settings entries/clear action for blocked links.
4. Tests: site redirect, app call, persistence, one-dialog rule.

**Effort: 0.5–1 day.**

### Phase 1 — Telegram autoplay + preview (recommended first)
1. `TelegramPlaybackQueue` + unit tests.
2. Source-JSON extraction and queue wiring in both Telegram feed paths.
3. `PlayerActivity` preview branch, Telegram-aware data source, handoff/direct advance.
4. Player UI (Autoplay pill, title).
5. Manual matrix on the Fold.

**Effort: 2–3 days.** Highest user-visible value; isolated from the rest.

### Phase 2 — IPTV group management
1. Data model + store.
2. Apply overrides in `readGroups`/merged grid.
3. Management sheet + dialogs.
4. Tests and device pass.

**Effort: 1.5–2 days.**

### Phase 3 — Android system PiP for the in-app PIP
1. `activePaneId`/`systemPip` state in `PipWindowStore`.
2. MainActivity params, auto-enter and leave/PiP callbacks; manifest flag.
3. `PipHost` system branch showing only the active pane; pause/release the rest; restore on return.
4. Device matrix (API 26/31+, phone/tablet, PiP denied).

**Effort: 1–1.5 days.**

### No work / not doing
- No upstream ports or merge.
- No dependency upgrades.
- No Nuvio TV-specific recording/Usenet/sidepanel work.
- No collection row (already shipped).
- No Sports future-game jump (already covered by Watch on).
- No preview-on-OK (no focus preview in Hikari).

---

## 6. Final decisions (approved)

| # | Decision | Recommendation |
|---|---|---|
| D1 | PiP preview | **Approved: both** — corner preview + Android system PiP. |
| D2 | Autoplay and preview settings: use the existing global **Autoplay** and **Next-video preview** toggles, or add a Telegram-only setting? | **Use the global toggles**; no new settings. |
| D3 | Queue scope | **Loaded list only** (confirmed). |
| D4 | Telegram feed coverage | **Include chat/channel lists and chat search results**; the search result list already has the videos in hand. |
| D5 | IPTV group management: move/rename/hide/lock only the playlist's own `Groups` mode, with lock protecting edits but not playback? | **Yes**; generated Language/Category/Country modes stay read-only. |
| D6 | Hidden group: hide only the category tile, or also remove its channels from All channels/search? | **Tile only**; channels stay reachable. |
| D7 | Add the report's "Are you still watching?" autoplay guard in this pass? | **No**; keep the plan focused. Can be a later optional add. |
| D8 | In-app PIP → Android system PiP | **Promote the active pane when backgrounded**; other panes pause and restore on return. See §3.8. |

---

## 7. Acceptance criteria

### WebView hardening
- [ ] Every automatic main-frame redirect is blocked and asks; direct link taps still load.
- [ ] Every external app call is blocked and asks, including a tapped one; no silent launch.
- [ ] The prompt has Always block / Allow once / Always unblock; Always block and Always unblock persist.
- [ ] The Cloudflare-verification, donation and ad guards still win first; initial load/reload/typed URL are not redirects.
- [ ] Blocked decisions are visible/reversible in Settings → Privacy & Browsing → WebView safety.
- [ ] Only one redirect/app dialog can be open at a time.

### Telegram
- [ ] Tapping a video in a loaded Telegram chat/channel feed queues the rest of the loaded list.
- [ ] 12 s before the end, the next video is muted in the corner with an "Up next" label.
- [ ] Tapping the preview promotes it; natural end hands off in place with sound restored.
- [ ] Works for signed-in TDLib videos and anonymous public Telegram CDN videos.
- [ ] TDLib preview uses `TdDataSourceFactory`; no black screen or infinite buffering.
- [ ] Preview off → direct advance; autoplay off → no advance.
- [ ] Last item stops at the end; no crash.
- [ ] Queue survives player recreation in the same process; no persistence required.
- [ ] `PlayerActivity`'s existing Android PiP is unchanged.

### Android system PiP
- [ ] With PIP Mode on and at least one pane, leaving the app promotes the active pane to system PiP (auto-enter API 31+, `onUserLeaveHint` API 26–30).
- [ ] Only the active pane decodes in PiP; the others pause and resume on return.
- [ ] Active pane follows the last focused/tapped/restored pane; a minimized active pane is promoted.
- [ ] Returning restores the in-app shelf; closing PiP stops audio and clears the panes.
- [ ] No system PiP with PIP Mode off, no panes, PiP denied, or a device without the feature; TV keeps the in-app overlay.
- [ ] `PlayerActivity` PiP and the Telegram corner preview are unchanged.

### IPTV
- [ ] Group rename, move, hide, restore, lock/unlock persist.
- [ ] Locked groups cannot be renamed/moved/hidden until unlocked.
- [ ] Catalog IDs and channel playback are unchanged.
- [ ] Hidden groups disappear from the playlist grid and merged grid, but their channels remain in All channels/search.
- [ ] Language/Category/Country modes are unchanged.

---

## 8. Implementation status — 25 Nov 2026

Code for all four phases is implemented and compiles; the full unit suite and `assembleDebug` pass.

### Phase 0 — WebView hardening — done
- Redirect prompt choices are **Always block / Allow once / Always unblock**.
- `WebViewLinkGuard` stores persistent blocked redirects and allowed/blocked app calls. Both automatic main-frame redirect gates consult the blocked set before prompting.
- `handleExternalScheme` now asks on every app call (tap or script), with the target app's label where resolvable; Cloudflare/donation/add-on guards still win first.
- Settings → Privacy & Browsing → WebView safety lists the counts and clears the decisions. Initial loads, reloads, typed URLs and direct link taps stay exempt.

### Phase 1 — Telegram autoplay + preview — done
- `TelegramPlaybackQueue` carries the loaded chat/channel/search list (verified by `TelegramPlaybackQueueTest`).
- Both TDLib lists (chat feed, channel grid, search results) and public-channel lists seed the queue; queued playback always uses the full player.
- `PlayerActivity` resolves the Telegram next item first for the 12-second corner preview (TDLib prewarm + `TdDataSourceFactory`), hands off in place, and advances directly when preview is off. The queue stops at the last item and never falls through to a catalogue shelf.
- Autoplay pill/title reflect the queue; opening any non-Telegram video clears it.

### Phase 2 — IPTV group management — done
- `IptvGroupPrefs` (JSON, provider-keyed) + `AppStore.IPTV_GROUP_PREFS`; unit-tested in `IptvGroupPrefsTest`.
- Long-press/overflow sheet on the playlist page: rename, move up/down, hide, lock/unlock. Hidden footer restores groups. Lock badge on the tile.
- Overrides apply to the playlist grid and the merged View All Playlists grid; hidden is tile-only, catalog IDs and channels keep the original group key.
- Only the playlist's own **Groups** mode is editable. The IPTV tab and page wiring are unchanged.

### Phase 3 — Android system PiP for in-app panes — done
- `PipWindowStore.activePaneId` + `systemPip`, active-pane tracking on show/add/restore/bring-to-front.
- `MainActivity` declares `supportsPictureInPicture`, sets API 31+ auto-enter params only while PIP Mode is on, panes exist and the OS permits PiP; API 26–30 enter from `onUserLeaveHint`.
- `PipHost` in system PiP draws a black host with only the active pane full screen, no chrome; the other panes leave composition (decoders released), save their position/play state and return where they stopped. Closing PiP ✕ clears the panes in `onDestroy`.
- `PlayerActivity` PiP and the corner preview are untouched.

### Follow-up hardening after device testing — 25 Nov
- **Redirects (all, blocked in the background):** the old touch-window shortcut (`recentUserGesture`, 2.5 s) let a page's script/meta/server redirect through after a tap. A tap now approves exactly one target URL (query-sensitive); every following target is cancelled silently, even same-site and inside the old window — the current page stays, the target never renders, and a toast points at the ⋮ menu's **Allow redirect to host**. Whitelisted ad hosts no longer bypass the gate. A last-resort `onPageStarted` stop catches WebView redirects that never reach `shouldOverrideUrlLoading`. Back/Forward are marked as user navigation and the initial typed/bookmark load stays exempt. Verified on the Fold with a meta refresh: the page stayed put and no landing page appeared.
- **Browser keyboard:** the edge-to-edge WebView window now reserves the IME bottom inset as well as the system bars, so the bottom address bar + bookmark strip stay above the on-screen keyboard. Verified on the Fold.
- **Browser menu:** **Go to Web Apps** sits directly under **Go to app home**; it finishes the site view and lands on the saved Web Apps page (`HikariApp.webAppsTabRequest` → `Routes.WEBSITES`). Verified on the Fold.
- **Dock pop-out:** the icon magnification wave now scales its growth above 1.0 — ×1.5 on the vertical tablet dock (hover 2.00 → 2.50) and ×2.5 on the phone dock in both orientations (hover 1.95 → 3.375). `DockPopScaleTest` covers the multipliers; both layouts checked on the Fold.

### Remaining verification
- Fold is connected again (`10.0.0.107:42201`); APK installed and the browser, Web Apps menu, IPTV group management and dock pop checks above were run on it.
- Emulators still reject the 92 MB debug APK for storage.
- No code work remains for the approved scope.
