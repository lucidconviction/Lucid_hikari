# Plan: MultiWindow Multi-View Port (Nuvio → Lucid_hikari)

Status: **READY TO IMPLEMENT** — all reference source read, all Lucid integration points mapped.

> Scope note: **PortalNutz / IPTV NutZ scraper work is OUT of scope** (user: "no portalnutz").
> The existing `IptvScreen.kt` NutZ section + `IptvNutZScreen.kt` + `PortalNutzScraper.kt` stay as-is.
> This plan only ports Nuvio's **MultiWindow** grid system into Lucid's MultiView tab + navigation bar.

## Objective
Port Nuvio's MultiWindow system — 47 layout grid, per-cell glass controls, slot swaps, volume/audio-focus, layout pills — into Lucid_hikari's MultiView bottom-bar tab, replacing the minimal `MultiViewScreen.kt` / `MultiViewLayout.kt`.

Build must pass `:app:compileDebugKotlin` and `:app:assembleDebug`.

---

## Source mapping (Nuvio → Lucid)

| Nuvio | Lucid equivalent |
|---|---|
| `expect MultiWindowPlayerManager` (createPlayer/setVolume/setAudioFocus/pause/resume/release/releaseAll) | `PlayerPool` (ExoPlayer pool, MAX_PLAYERS=9) — `pool.get(index)`, `player.volume = v`, `player.pause()`/`player.play()`, `pool.release(index)`, `pool.releaseAll()` |
| `expect MultiWindowVideoSurface(handle, modifier, resizeMode)` | `AndroidView { PlayerView(it) }` (already used in current `MultiViewScreen.kt`) |
| Nuvio `IptvChannel` (has `id`, `sourceType`, `sourceId`) | Lucid `IptvChannel` (name/url/logo/… — **no id**) → window identity = `url`; `WindowStream` keyed by `url` |
| `MultiWindowStore` (KMP-agnostic) | plain Kotlin `object` with Compose `mutableStateOf/List/Map` (works on Android) |
| Nuvio `WindowStream.channel.name/.url/.logo` | `WindowStream(name, url, logo, slotIndex)` directly |
| Colors (Digital Kinetic tokens) | Lucid glass tokens via `MaterialTheme.colorScheme` + existing `rememberGlassTokens()` where available; hardcode the few Nuvio tokens (GlassBg 0x991E1E1E etc.) privately like Nuvio does |
| "⛶" / "◀" / "▶" / "⋮" text glyphs | keep as text (Nuvio uses glyphs, no icon dep needed) |

## Existing Lucid touch points (keep compiling)

- `AppNav.kt` line ~108: `import com.hikari.app.ui.screens.MultiViewLayout`; line ~1437 `MultiViewLayout.fromKey(layoutKey)`; bottom tab at ~1029 (`Icons.Filled.GridView`, `Routes.MULTI_VIEW`), `multiViewTabFlow` in `visibleTabs`; route parse ~1433 builds `MultiViewScreen(sessions, layout, onBack)`.
- `AppNav.kt` line 335: `Routes.multiView(sessions, layout: String = "")` — layout passed as **enum name string**.
- `SettingsScreen.kt` line 8644/8686: `MultiViewLayout.fromKey(key)`, `MultiViewLayout.entries.forEach { l -> ... setMultiViewLayout(l.name) }`.
- `AppStore.kt`: `multiViewSessions()` (JSON via `MultiViewSession.parse/encode`), `setMultiViewLayout(key)` (stores enum name string).
- `PlayerActivity.kt` line 134: imports `MultiViewLayout`; line 7964 `openMultiWindow()`.
- `MultiViewSession(name, url)` in `data/MultiViewSession.kt` — the session list source.

---

## File 1: `app/src/main/java/com/hikari/app/ui/screens/MultiViewLayout.kt` — replace (Nuvio `MultiWindowLayouts` port)

- `data class SlotPos(val index: Int, val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)`
- `enum class MultiViewLayout(val label: String)` — all **47 Nuvio layouts** with identical labels:
  `V1_FULL("1")`, `V2_SPLIT("1×2")`, `V2_STACK("2×1")`, `V3_STACK("3 vert")`, `V3_TOP1_BOT2("1+2")`, `V3_LEFT2_RIGHT1("2+1")`, `V3_HORIZ("1×3")`, `V3_TOP2_BOT1("2+1v")`, `V3_LEFT1_RIGHT2("1+2h")`, `V4_GRID("2×2")`, `V4_1_2_1("1-2-1")`, `V4_HORIZ("1×4")`, `V4_VERT("4 vert")`, `V4_LEFT1_RIGHT3("1+3")`, `V4_LEFT3_RIGHT1("3+1")`, `V4_2_1_1("2-1-1")`, `V4_1_1_2("1-1-2")`, `V5_GRID4_BOT1("4+1")`, `V5_TOP1_BOT4("1+4")`, `V5_LEFT3_RIGHT2("3+2")`, `V5_HORIZ("1×5")`, `V5_TOP2_BOT3("2+3")`, `V5_TOP1_GRID4("1+2×2")`, `V5_3LEFT_2RIGHT("3+2v")`, `V6_3x2("3×2")`, `V6_2x3("2×3")`, `V6_1_2_2_1("1-2-2-1")`, `V6_HORIZ("1×6")`, `V6_VERT("6 vert")`, `V6_GRID4_2ROW("4+2")`, `V6_3ROW_2ROW("3+3")`, `V7_1_3_3("1-3-3")`, `V7_TOP1_BOT6("1+6")`, `V7_3x2_PLUS_1("3×2+1")`, `V7_2x3_PLUS_1("2×3+1")`, `V7_LEFT3_GRID4("1+3+3")`, `V7_ASYMM("7 asym")`, `V8_GRID4x2("4×2")`, `V8_2x4("2×4")`, `V8_1_3_3_1("1-3-3-1")`, `V8_3x2_PLUS_2("3×2+2")`, `V8_2x3_PLUS_1x2("2×3+1x2")`, `V8_GRID4_2ROW_PLUS_1("4+2+1")`, `V9_GRID3x3("3×3")`, `V9_GRID3x3_CENTER("3×3+")`, `V9_3x2_PLUS_3x1("3×2+3")`, `V9_2x3_PLUS_3x1("2×3+3")`
- **Back-compat shims** for existing callers (old names like `GRID_2x3` stored in DataStore must still resolve):
  ```kotlin
  val MultiViewLayout.cols: Int get() = calculateSlots(Int.MAX_VALUE).maxOfOrNull { it.col + it.colSpan } ?: 1
  val MultiViewLayout.rows: Int get() = calculateSlots(Int.MAX_VALUE).maxOfOrNull { it.row + it.rowSpan } ?: 1
  val MultiViewLayout.maxWindows: Int get() = calculateSlots(Int.MAX_VALUE).size
  companion object {
      fun fromKey(key: String?): MultiViewLayout =
          entries.firstOrNull { it.name == key } ?: V6_2x3   // old default GRID_2x3 → V6_2x3
      fun fromLegacyKey(key: String?): MultiViewLayout = // optional alias if old names needed
          when (key) {
              "GRID_2x2" -> V4_GRID
              "GRID_2x3" -> V6_2x3
              "GRID_3x2" -> V6_3x2
              "GRID_3x3" -> V9_GRID3x3
              "GRID_2x4" -> V8_2x4
              else -> fromKey(key)
          }
  }
  ```
  (Update the 3 call sites that used `fromKey` to `fromLegacyKey` if we want old saved layouts to survive — AppNav 1437, SettingsScreen 8644. Otherwise plain `fromKey` with V6_2x3 fallback is fine.)
- `fun getValidLayouts(count: Int, isPortrait: Boolean, isTablet: Boolean = false): List<MultiViewLayout>` — Nuvio's `when` verbatim (count 1..9, portrait/landscape/tablet gating).
- `fun defaultLayout(count: Int, isPortrait: Boolean, isTablet: Boolean = false): MultiViewLayout = getValidLayouts(...).firstOrNull() ?: V1_FULL`
- `fun MultiViewLayout.calculateSlots(count: Int): List<SlotPos>` — full Nuvio `when` (all 47 arms) + `.take(count)`.

## File 2: NEW `app/src/main/java/com/hikari/app/ui/screens/MultiWindowStore.kt`

```kotlin
data class WindowStream(
    val id: String,          // "mw_<counter>"
    val name: String,
    val url: String,
    val slotIndex: Int,
    val logo: String? = null,
    val isPlaying: Boolean = false,
)
```
`object MultiWindowStore` (ports Nuvio's, minus IptvChannel-specific bits):
- State: `streams = mutableStateListOf<WindowStream>()`, `volumes/paused/resizeModes = mutableStateMapOf<String, Float/Boolean/Int>()`, `_audioFocusId: String?`, `playerIndexById = mutableMapOf<String, Int>()` (pool index), `_currentLayout = mutableStateOf<MultiViewLayout?>(null)`, `_layoutLocked = mutableStateOf(false)`, `slotPlaylists/slotPlaylistIdx` (kept for future; optional).
- Constants: `MAX_SLOTS = 9` (== PlayerPool.MAX_PLAYERS).
- Resize-mode consts: `RESIZE_FILL=0, RESIZE_FIT=1, RESIZE_FIXED_WIDTH=2, RESIZE_FIXED_HEIGHT=3, RESIZE_ZOOM=4` (map to `ContentScale` in the view).
- Functions: `addToSlot(name, url, logo, slotIndex)` (dedupe by url — replace existing at slot or in other slot; first stream gets audio focus + volume 1f, rest 0f), `removeSlot(slotIndex)`, `remove(id)`, `getStreamsForSlot`, `getOccupiedSlots`, `isSlotAvailable`, `nextAvailableSlot`, `setVolume/getVolume` (coerceIn 0..1), `setPaused/isPaused`, `setAudioFocus/isAudioFocused`, `setResizeMode/getResizeMode`, `storePlayerIndex(streamId, poolIndex)` / `getPlayerIndex`, `currentLayout()/isLayoutLocked()/setLayout(layout)/setAutoLayout()/resolveLayout(count, isPortrait, isTablet)`, `swapSlots(a, b)` (swap list positions **and** slotIndex so grid-by-list-order updates visually), `clear()`.

## File 3: `app/src/main/java/com/hikari/app/ui/screens/MultiViewScreen.kt` — full rewrite (Nuvio `MultiWindowGrid` port)

Keep public signature (AppNav call site unchanged):
```kotlin
@Composable
fun MultiViewScreen(
    sessions: List<MultiViewSession>,
    layout: MultiViewLayout = MultiViewLayout.V6_2x3,
    onBack: () -> Unit,
)
```

Structure:
1. **Seed store once** from `sessions` — `LaunchedEffect(sessions)` : if store empty or session list changed, `clear()` then add each session to `nextAvailableSlot()`. (Sessions come from `Routes.multiView` encode; layout param seeds `MultiWindowStore.setLayout(layout)` when provided, else auto.)
2. **Player pool**: `val pool = remember { PlayerPool(context) }`; `DisposableEffect(Unit) { onDispose { pool.releaseAll(); MultiWindowStore.clear() } }`.
3. **Empty state** (streams.isEmpty()): centered 64dp "MW" tile + "No streams added" + pill button "Browse IPTV Channels" → `nav.navigate(Routes.IPTV)` (pass nav controller via `LocalNavController.current`) + "★ Saved Layouts" outline pill (no-op or opens layout sheet).
4. **Header row**: "MultiNutz" (primary, bold 20sp, letterSpacing -0.3) + red-dot badge pill "N ACTIVE" (error container alpha bg, pulsing dot optional).
5. **Pill row** (`horizontalScroll`):
   - `Auto` pill (active when `!MultiWindowStore.isLayoutLocked()`)
   - one pill per `getValidLayouts(count, isPortrait, isTablet)` — selected = primaryContainer bg / white text, else surfaceContainer + 0.5dp outlineVariant border
   - divider, then `⚡ Quick` pill (navigates to IPTV channel browse)
   - `Mute All`/`Sound All` (all volume 0 → restore 1), `Pause All`/`Play All`, `Refresh All` (re-prepare players), `Close All` (error border, red text → `MultiWindowStore.clear()` + `pool.releaseAll()`)
6. **Grid** (`BoxWithConstraints`): `isPortrait = maxWidth < maxHeight`, `isTablet = maxWidth >= 600.dp`; `layout = MultiWindowStore.resolveLayout(count, isPortrait, isTablet)`; `slots = layout.calculateSlots(count)`; `totalRows`/`totalCols` from slot max row+rowSpan / col+colSpan. Nested `for (row) Row(weight(1f)) { for (col) { slot → weight((slot.colSpan*totalRows).toFloat()) } }` (Nuvio's exact weighting math). Occupied slot → `key(stream.id) { VideoCell(...) }`; empty slot → faint dashed-style placeholder box.
7. **VideoCell(stream, gridPosition, …)**:
   - Pool index: stable `index = stream.slotIndex` (0..8 == pool slots). `val player = remember(stream.id) { pool.get(stream.slotIndex) }`.
   - `DisposableEffect(stream.id)`: `player.setMediaItem(MediaItem.fromUri(stream.url))`; `player.volume = MultiWindowStore.getVolume(stream.id)`; `player.playWhenReady = true`; `player.prepare()`; listener for completion (playlist advance — optional); `onDispose { pool.release(stream.slotIndex) }`.
   - `AndroidView { PlayerView(it).apply { this.player = player; layoutParams = MATCH_PARENT } }`.
   - **Audio-focus border**: 2dp primary border when `MultiWindowStore.isAudioFocused(stream.id)` else 0.5dp outlineVariant/0.3.
   - **Glass overlay** `Box(graphicsLayer { alpha = controlsAlpha })` — `controlsAlpha` = `animateFloatAsState(1f/0f)`, auto-hide `LaunchedEffect(controlsVisible, trigger) { if (visible) { delay(3500); visible = false } }`:
     - bg `GlassBg = Color(0x991E1E1E)`
     - top-left badge: `"$gridPosition | $name"` on primaryContainer
     - top-right ⛶ (28dp circle) → fullscreen: open existing player for this URL (`PlayerActivity` / existing full-play nav used elsewhere in app) + remove from grid or keep
     - center ◀ / ▶ swap arrows (28dp circles) → `MultiWindowStore.swapSlots(curSlot, prev/next occupied slot)`
     - center 36dp play/pause circle (Pause/PlayArrow icon, primary tint) → `player.pause()/play()`
     - bottom-right volume chip (VolumeUp/VolumeOff + "N%") → toggle: `player.volume = 1f/0f`, store volume, on enable `setAudioFocus(stream.id)`
     - bottom-left ⋮ chip → `onCellLongPress` (options dialog: Play Fullscreen / Remove window)
     - bottom 3dp progress bar: elapsed-time based (Nuvio style) — `LaunchedEffect(isPlaying) { while (isPlaying) { delay(1000); elapsed++; seekProgress = (elapsed % 3600)/3600f } }`, primaryContainer fill + 6dp dot when playing
   - `onInteraction()` resets controls visibility + grabs audio focus.

## File 4: `AppNav.kt` — verify / minor
- MultiView bottom tab already exists (~1029) — verify still compiles with new `MultiViewLayout` (import line 108 unchanged).
- If we add `fromLegacyKey`, update line 1437 to use it (one-line change). Otherwise leave as `fromKey`.
- No new tab needed — the MultiView tab IS the multi-window destination. (If the user additionally wants a dedicated multi-view button in the *IPTV page navigation bar*, add a `GridView` icon button in the IPTV header → `Routes.multiView(currentSessions)`. Decide at implement time; default: existing bottom tab is enough.)

## Build & verify
1. `cd /Users/robbdeeze/Documents/projects/Lucid_hikari && ./gradlew :app:compileDebugKotlin`
2. Fix compile errors (hotspots: `key()` import, PlayerPool API shape, `ContentScale` mapping for resize modes, icon availability, DataStore layout keys).
3. `./gradlew :app:assembleDebug` → APKs in `app/build/outputs/apk/debug/`.

## Known risks / mitigations
- **Enum name change breaks stored layout keys** ("GRID_2x3" etc. in DataStore) → `fromKey` fallback to `V6_2x3`; optional `fromLegacyKey` mapping for the 5 common old names.
- **PlayerPool API**: `pool.get(index)` returns ExoPlayer, `pool.release(index)`, `pool.releaseAll()`, `MAX_PLAYERS=9` — slotIndex 0..8 maps 1:1. Verify `get()` creates on demand (read `PlayerPool.kt` lines 34-58 before wiring).
- **9 PlayerViews + 9 ExoPlayers** on phone: Nuvio caps maxSlots at 6 for non-tablet — keep that (`val maxSlots = if (isTablet) 9 else 6`; `count = min(streams.size, maxSlots)` and hide overflow streams or cap additions).
- **`item`/LazyGrid import collisions** — not applicable: new grid uses plain nested Row/Column.
- **Icons**: use text glyphs (⛶ ◀ ▶ ⋮) like Nuvio; icons needed: `Pause`, `PlayArrow`, `VolumeUp`, `VolumeOff`, `GridView` — all standard/filled, already in project.

## Implementation order
1. `MultiViewLayout.kt` (replace)
2. `MultiWindowStore.kt` (new)
3. `MultiViewScreen.kt` (replace)
4. `AppNav.kt` verify/1-line
5. `:app:compileDebugKotlin` → fix → `:app:assembleDebug`

---

## Status: COMPLETE ✅

- `MultiViewLayout.kt` — 47-layout enum port done; compiles.
- `MultiWindowStore.kt` — slot stream state store done; compiles.
- `MultiViewScreen.kt` — Nuvio MultiWindowGrid UI port done; compiles.
- `AppNav.kt` — route `multi-view?{sessions}&layout={layout}` call site at line 1437 unchanged; `MultiViewLayout.fromKey(layoutKey)` resolves legacy keys.
- `:app:compileDebugKotlin` — **BUILD SUCCESSFUL**.
- `:app:assembleDebug` — **BUILD SUCCESSFUL**; APKs in `app/build/outputs/apk/debug/`:
  - `app-arm64-v8a-debug.apk` (76M)
  - `app-armeabi-v7a-debug.apk` (74M)
  - `app-universal-debug.apk` (96M)
- `_test_write.txt` scratch file removed.
