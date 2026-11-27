# Plan — vertical Mac dock for tablets + Web App Market

**Status: approved 25 Nov 2026 — dock first, then market.**
Target release: 0.17.0 (two PRs — see §7).

**Implementation status (25 Nov 2026):** Phase 1 (tablet dock) and Phase 2 (market) are implemented. `:app:compileDebugKotlin`, the parser/catalogue tests and `TabletDockMetricsTest` pass. On-device review of the market is pending — the Fold (`10.0.0.107:40823`) went offline after the dock pass, and the local emulators do not have enough free space for the 92 MB debug APK.

## 0. Post-device-review adjustments

The Fold pass and the follow-up requests changed these numbers from the original spec; where they disagree, this section wins:

- **Dock width:** 72 dp (was 84), page inset **84 dp** while open (was 96), 0 while shelved. The Classic rail keeps 96 dp.
- **Dock sizing:** cells max 48 dp (floor 34), tiles max 32 dp (floor 20).
- **Pop-out:** the hovered vertical tile is **2.0x** with a 14 dp lift toward the content; neighbours step 1.5x / 1.2x.
- **Shelving:** folding the tablet dock releases the page's left inset and the page runs edge-to-edge; the glass circle overlays the full-width page. Unfolding restores the 84 dp inset with a 220 ms animation. The **phone** dock now follows the same rule: folding it drops the 64 dp bottom inset.
- **Market caps:** FMHY now takes up to **40** per category (414 SFW apps in the seed); ThePornDude takes up to **20** per adult category (100 runtime-only).
- **Market thumbnails:** every card has an 84 dp header, a 44 dp favicon tile with a 32 dp image, and a 68 dp text area, so all cards and thumbnails are the same size.
- **Source names:** the market UI and its tour do not name the catalogue sources; only the internal code and this plan do.
- **Browser redirects (separate from this plan):** every automatic main-frame redirect, same site or foreign, is blocked and asks Block / Allow once / Always allow / Always block. A real tap, the initial page load, a reload and an allowed host still pass.

This plan covers two features:

1. **Tablet navigation** — the Mac-style dock, rotated vertical, pinned to the far-left centre of a tablet.
2. **Web App Market** — a browsable catalogue inside the Web Apps tab, built from FMHY and ThePornDude, with one-tap save into the user's Web Apps.

The two features share one small change in `AppNav.kt` (the Web Apps tab becomes the market's parent route), so they are planned together but implemented independently.

---

## 1. Goals

### Tablet dock
- A tablet gets the **same Mac dock** as a phone, not the labelled rail: gradient tiles, magnification wave, running-app dot, haptics, long-press fold, folded-circle tab wheel, glass plate.
- The dock is **vertical, flush to the left edge, vertically centred**.
- It keeps the existing 96 dp content inset, so no page has to change.
- The phone, television and every existing screen are untouched.

### Web App Market
- A curated market inside **Web Apps**, not a new top-level tab.
- Content comes from the six sources named in the brief:
  - `fmhy.net/video` — up to **40** sites per FMHY streaming category.
  - ThePornDude — **20** sites per category for the five adult pages.
- Organised by **type of site** in tabs, with a search field.
- Tapping a site opens it in the existing ad-free `WebViewActivity`.
- A bookmark control saves the site into the existing **Saved Web Apps** list (`AppStore` sites), so it appears on the Web Apps tab immediately.
- Adult categories respect the app's existing **Settings → Content → NSFW** switch.

### Non-goals (v1)
- No new top-level navigation tab.
- No in-app playback/proxying of market sites; they open in the existing browser.
- No auto-saving of the catalogue. The user opts in per app.
- No scraping of site screenshots or descriptions for adult entries.
- No change to CloudStream/Stremio extension handling.

---

## 2. Current state (verified in the codebase)

### Navigation
- `AppNav.kt` chooses three layouts: phone (`AppBottomBar`), tablet (`TabletNavRail`), TV (`TvNavRail`).
- The tablet rail is `96.dp` wide, labelled, and always drawn for tab routes (`TabletNavRail.kt`). It ignores the `navStyle` setting.
- The Mac dock (`AppDockBar` + `DockTile`) exists only in the phone bottom-bar branch:
  - gradient-tinted square tiles, per-route colours (`dockTint`),
  - hover/drag magnification wave,
  - selected running dot,
  - long-press folds to a 56 dp circle; tap unfolds; vertical swipe on the circle walks tabs,
  - haptics on slide.
- The dock's `expanded` and `showLabels` parameters are currently **unused** inside `AppDockBar`; the scroll-shrink described in its comments and the "Show text on taskbar buttons" setting do not affect it. That is pre-existing, and this plan leaves phone behaviour unchanged unless we choose the optional fix in §3.7.
- `LocalTaskbarInset` is applied when `showBar && !isTv` — which includes tablets. Tablet pages therefore reserve up to **64 dp of phantom bottom space** for a bar that is not there. This should be fixed as part of the dock work.
- `Routes.tabBaseOf()` is the hook that keeps a sub-route highlighted on its parent tab (used by Search, Manga, MultiView). A market route can use it to keep the Web Apps tab and the rail visible.

### Web Apps
- `WebsitesScreen.kt` is the Web Apps tab: address bar, add flow, saved grid/list, `SiteGridCard`/`SiteCard`, screenshots from `WebAppShots` or mShots.
- Saved sites are `Site(name, url)` in `AppStore` (`sitesFlow`, `addSite`, `removeSite`), stored as JSON in DataStore. No schema change is needed to save market entries.
- `WebViewActivity` has its **own** separate 12-item bookmark strip (`webview_bookmarks` SharedPreferences). It is not the same list as Saved Web Apps. For v1 the market saves to **Saved Web Apps**; unifying the two lists is an optional follow-up (§5.7).
- `NsfwGate` + `AppStore.nsfwEnabledFlow()` is the app-wide adult switch (default ON). The market's adult group should reuse it rather than inventing a second age gate.

### Networking and storage available today
- OkHttp 5 (`Http.getQuiet` / `Http.getStringQuiet`, `Http.UA`), Jsoup, kotlinx.serialization, Coil — all already dependencies. **No new libraries are needed.**
- `AdBlocker` uses a file cache plus a 7-day staleness check; the market can follow that pattern.
- Assets live in `app/src/main/assets/` (currently ~3.3 MB).

### Sources verified (live fetch, 25 Nov 2026)
- `fmhy.net/video` is a VitePress page with server-rendered `<h2>/<h3 id="…">` headings and `<ul><li>` entries. The first external link in each `<li>` is the site; mirrors are numbered links after it.
- FMHY section counts we can use (unique external hosts, first 40 max):

  | Section id | Entries available |
  |---|---|
  | `stream-aggregators` | 67 |
  | `p-stream-forks` | 6 |
  | `dedicated-server` | 27 |
  | `multi-server` | 41 |
  | `stream-lounges` | 15 |
  | `free-w-ads` | 24 |
  | `video-streaming` | 17 |
  | `anime-streaming` | 66 |
  | `cartoon-streaming` | 9 |
  | `tv-streaming` | 19 |
  | `drama-streaming` | 18 |
  | `classics-public-domain` | 12 |
  | `film-archives` | 29 |
  | `live-tv` | 36 |
  | `live-sports` | 52 |
  | `sports-replays` | 43 |

  So "40 per category" is achievable in the long sections; P-Stream Forks (6), Cartoon Streaming (10), Classics (12), Stream Lounges (15), Video Streaming (18), Drama Streaming (18), TV Streaming (20), Free with Ads (25), Dedicated Servers (27), Film Archives (29), Live TV (36) and Multi-Server (38) take everything they have.
- Each ThePornDude category page embeds a Schema.org `ItemList` (`position`, `name`, internal review URL, `numberOfItems`). Following a review's JSON endpoint (`/json/<id>/<slug>`) returns `display_slug` (the real site), `data_category_link` (sometimes an affiliate redirect), and `is_deadsite`. No HTML scraping of the ranking is needed.
- `free-onlyfans-accounts` entries have `display_slug = https://onlyfans.com`; the affiliate redirect resolves to `https://onlyfans.com/<profile>/c<digits>`. The plan resolves and cleans those to the profile URL, and never stores `pdude.link`.

---

## 3. Part A — vertical Mac dock on tablets

### 3.1 UX

- **Placement:** a vertical glass plate flush to the left edge, **vertically centred**. On a tablet in landscape, it sits in the middle of the left edge rather than at the top or bottom.
- **Plate:** 84 dp wide, height hugs the tabs, rounded on the content side (right), square on the screen edge (left), same smoked-glass gradient, hairline, and sheen as the phone dock.
- **Tiles:** the same per-tab gradient tiles and icons as the phone dock. The selected tile keeps its running dot. No labels at rest (matches the Mac dock; see decision D2).
- **Magnification:** sliding a finger up/down the dock grows the tile under it and its neighbours; lifting on a tile opens that tab. Mouse hover does the same on a tablet with a pointer.
- **Fold:** long-press anywhere on the dock folds it into a 56 dp glass circle at the left centre, showing the current tab. Tap unfolds. Swipe up/down on the folded circle walks the tabs, exactly like the phone.
- **Haptics:** TextHandleMove on tile change, LongPress on fold, same as phone.
- **Entrance:** slides in from the left when the tab page appears.
- **MultiView:** hides with the existing `mvBarVisible` fade, same as the tablet rail today.
- **Themes:** plate uses `rememberGlassTokens()` like the phone dock; works on Dark, Dark Glass, AMOLED and Light.
- **RTL:** `CenterStart`/`start` padding mirrors the dock to the right edge, consistent with the rest of the app.

### 3.2 Sizing

`BottomTabs` has 15 entries; 13 are visible by default (IPTV and Stats are off by default). A 600 dp-tall tablet window must still fit every tab.

- `cell = min(54.dp, (availableHeight - 20.dp) / tabCount)`, floor 36 dp.
- 15 tabs at 600 dp height → ~38 dp cells → 32 dp tiles; fits.
- 3 tabs → 54 dp cells, plate hugs content and is centred.
- If a future tab count cannot fit, the dock scrolls vertically rather than clipping (same centring/scroll rule as `TabletNavRail`).
- Tile scale stays within the plate width; the hovered tile gets `zIndex(1f)` so it draws over its neighbours.
- The plate is a sibling of the tile column, so magnification is not clipped by the glass.

### 3.3 Inset rules

- Keep `LayoutTokens.navInset = 96.dp` for tablets; the 84 dp dock leaves a 12 dp gap before page content.
- Fix `LocalTaskbarInset` to `0.dp` on tablets: `if (showBar && !isTv && !isTablet)`. This removes the current phantom bottom padding on every tablet page.
- The dock is drawn inside the existing `Alignment.CenterStart` slot; no page changes are required.

### 3.4 Style mapping

The existing Settings → App Layout → Navigation bar choice remains authoritative:

| `navStyle` | Tablet navigation |
|---|---|
| `ANIMATED` (default, "Mac dock") | **vertical Mac dock** |
| `FLOATING` ("Dock, always on") | **vertical Mac dock**, always full size |
| `CLASSIC` | existing labelled `TabletNavRail` |
| `COMPACT` | existing labelled `TabletNavRail` |

This gives the setting a real effect on tablets without forcing the dock on users who deliberately chose a labelled rail. TV stays on `TvNavRail`.

### 3.5 Code changes

| File | Change |
|---|---|
| `ui/navigation/AppNav.kt` | Extract `DockTile`, `dockTint` and the plate brush into `DockParts.kt`. Add a `TabletDock` branch in `AppRoot` keyed on `isTablet && style in {ANIMATED, FLOATING}`. Keep `TabletNavRail` for `CLASSIC/COMPACT`. Fix the `LocalTaskbarInset` condition. |
| `ui/navigation/DockParts.kt` *(new)* | Shared dock tile + plate primitives. Add an `orientation` parameter to `DockTile` so `lift` becomes rightward in vertical mode. Add `zIndex` for the hovered tile. |
| `ui/navigation/TabletDock.kt` *(new)* | `TabletDock(currentRoute, hidden, onNavigate, modifier)`; metrics, plate, tile column, fold circle, swipe wheel, haptics, entrance animation. |
| `ui/layout/LayoutTokens.kt` | No value change; document that `navInset = 96.dp` covers the 84 dp vertical dock. |
| `ui/layout/TabletNavRail.kt` | Unchanged; remains the classic/compact fallback. |
| `ui/screens/SettingsScreen.kt` | One-line supporting text on the Navigation bar card: tablets draw the Mac/Floating styles down the left edge. No new preference. |
| `ui/tutorial/Tutorials.kt` | Optional one-line addition to the Web Apps tour; no new dock tutorial. |
| `test/.../ui/layout/VerticalDockMetricsTest.kt` *(new)* | Pure metrics tests (see §8). |

### 3.6 Edge cases to verify

- Tablet portrait (600×960), landscape (960×600), and split-screen widths.
- 3 visible tabs, 13 default, all 15 visible.
- Switch nav style in Settings while on a tablet: dock ↔ rail re-lays out live.
- Rotate while folded: the fold state is intentionally not persisted; rotation shows the dock again (same rule as the phone).
- System navigation bar visible vs immersive.
- MultiView auto-hide and touch-to-show.
- Dark / Dark Glass / AMOLED / Light.
- RTL (Arabic/Hebrew) — dock mirrors, content inset mirrors.
- Mouse hover and drag on a tablet with a trackpad.
- TV regression: `TvMode` still wins over the 600 dp width test; no dock on TV.
- Phone regression: unchanged dock.

### 3.7 Optional cleanup (not required)

`AppDockBar`'s `expanded`/`showLabels` parameters are dead. Options:
- leave them alone (zero risk), or
- implement the documented scroll-shrink and make "Show text on taskbar buttons" apply, in both orientations.

**Recommendation:** leave phone behaviour unchanged for this feature; revisit separately. The tablet dock should not silently diverge from the phone.

---

## 4. Part B — Web App Market

### 4.1 Entry points

- A **storefront icon** in the Web Apps top bar, next to the grid/list toggle.
- When the saved list is **empty**, a primary "Browse the market" button beside "Add web app".
- No permanent banner when the list is populated; the icon is enough and avoids clutter.

Nothing else in the app needs a new entry point in v1.

### 4.2 Navigation

- New route: `Routes.WEB_APP_MARKET = "webapps-market"`.
- `Routes.tabBaseOf()` maps it to `Routes.WEBSITES`, so:
  - the bottom taskbar stays and highlights Web Apps on a phone,
  - the tablet rail/dock stays and highlights Web Apps,
  - tapping the Web Apps tab while on the market pops back to the saved list.
- Composable: `composable(Routes.WEB_APP_MARKET) { WebAppMarketScreen(nav) }`.
- Back: `nav.popBackStack()`; fallback to `Routes.navigateTab(nav, Routes.WEBSITES)` if the back stack is empty.

### 4.3 Information architecture

Two-level tabs keep 21 categories usable without a 21-chip scroll:

**Groups** (top row): `Movies & TV`, `Anime & Cartoons`, `Live TV`, `Sports`, `Archives`, `Adult`.

**Categories** (second row, only when a group has more than one):

| Group | Category tab | Source | Cap |
|---|---|---|---|
| Movies & TV | Stream Aggregators | FMHY | 40 |
| | P-Stream Forks | FMHY | 40 (6 available) |
| | Dedicated Servers | FMHY | 40 |
| | Multi-Server | FMHY | 40 |
| | Stream Lounges | FMHY | 40 |
| | Free with Ads | FMHY | 40 |
| | Video Streaming | FMHY | 40 |
| | TV Streaming | FMHY | 40 |
| | Drama Streaming | FMHY | 40 |
| Anime & Cartoons | Anime Streaming | FMHY | 40 |
| | Cartoon Streaming | FMHY | 40 |
| Live TV | Live TV | FMHY | 40 |
| Sports | Live Sports | FMHY | 40 |
| | Sports Replays | FMHY | 40 |
| Archives | Classics / Public Domain | FMHY | 40 |
| | Film Archives | FMHY | 40 |
| Adult | Tubes | ThePornDude | 20 |
| | Cams | ThePornDude | 20 |
| | AI | ThePornDude | 20 |
| | OnlyFans sites | ThePornDude | 20 |
| | Free OF accounts | ThePornDude | 20 |

Max catalog: **414 SFW + 100 adult = 514 apps** (16 FMHY categories at 40 each is 640; the shorter sections bring the seed to 414. Adult caps are 20 each, runtime-only, so up to 100 more).

- Default selection: `Movies & TV → Stream Aggregators`.
- Search is **global** across all visible categories; while a query is active the category rows stay visible but do not filter the results. Clearing the query returns to the selected category.
- Category chips show a count (`Stream Aggregators · 40`).
- The Adult group is hidden entirely when `NsfwGate.enabled` is false. No second age gate: the app's existing NSFW switch is the consent boundary. The market footer names that setting so it is discoverable.

### 4.4 Data model

```kotlin
@Serializable
data class MarketApp(
    val name: String,
    val url: String,
    val categoryId: String,
    val source: String,      // "fmhy" | "tpd"
    val rank: Int,           // 1..N after filtering
    val note: String = "",   // FMHY descriptor only; never adult text
    val adult: Boolean = false,
)

@Serializable
data class MarketCatalog(
    val schema: Int = 1,
    val fetchedAt: Long,
    val apps: List<MarketApp>,
)

data class MarketGroup(val id: String, val label: String, val categories: List<MarketCategory>)
data class MarketCategory(
    val id: String,          // stable section id, e.g. "stream-aggregators"
    val groupId: String,
    val label: String,       // English key, wrapped in tr() at draw time
    val source: MarketSource,
    val cap: Int,
    val adult: Boolean,
    val sourceUrl: String,   // for attribution / "open source page"
)
```

- Categories and groups are **code**, not scraped data, so labels/order cannot drift.
- The cache file contains only apps + `fetchedAt` + schema.
- `MarketApp` has no database id; the URL is the identity. Lazy grid keys use `categoryId + url`.
- Saving uses the existing `Site(name, url)`; no schema change is required. (Optional: add `Site.adult` later to avoid explicit saved-site screenshots — see §5.7.)

### 4.5 Storage and refresh

```
filesDir/webapp_market/catalog.json     ← cache, written atomically
assets/webapp_market_seed.json          ← SFW seed only, generated at build time
```

- **First open:** load the seed instantly. It contains FMHY categories only. Adult categories start empty and are fetched only when the Adult group is opened with NSFW ON.
- **Cache:** if the cache exists, has `schema == 1` and is non-empty, it wins over the seed.
- **Staleness:** older than **7 days** → background refresh on market open. A manual refresh always runs.
- **Refresh scope:** FMHY always; ThePornDude only if NSFW is ON. If NSFW is off, existing adult entries stay in the cache but are not displayed and no adult URLs are requested.
- **Partial failure:** replace a category's apps only if its scrape returned at least one valid entry; otherwise keep the previous category and surface a stale/error banner. One dead source must not blank the market.
- **Concurrency:** a `Mutex` prevents overlapping refreshes; HTTP work on `Dispatchers.IO`; at most 4 review-page requests in flight.
- **Persistence:** write to `catalog.json.tmp` then rename; never leave a half-written file.
- **Logging:** `Hikari/Market` logs source + category counts and error types. Never log adult site names or URLs (logs are shareable).
- **Repository shape:** `object WebAppMarketRepo` with `MutableStateFlow<MarketState>` (catalog, loading, refreshing, per-source error), loaded lazily by the screen; no app-start network call.

### 4.6 FMHY scraper

Source: `https://fmhy.net/video` (HTML, no auth).

Algorithm:
1. `Jsoup.parse(html, baseUrl)`.
2. Iterate `main h3[id]`. The `id` is the stable category key; map it through `MARKET_FMHY_SECTIONS`. Unknown sections are ignored.
3. For each heading, walk `nextElementSiblings()` until the next `h2`/`h3`, collecting `ul > li`.
4. For each `<li>`:
   - choose the first `<a href>` that is external (`http/https`) and whose text is not a bare mirror number (`2`, `3`, …); prefer an anchor inside `<strong>` when present;
   - name = anchor text, with zero-width/control characters and a trailing `New` badge removed;
   - `note` = the item's text after the site links, trimmed to ~120 chars (optional; not rendered in v1);
   - skip links to `fmhy.net` itself, Discord/Telegram/Reddit, and duplicate hosts already taken in this category.
5. Cap at the category cap (40) or what the section has, and renumber `rank = 1..N`.

Pseudocode:

```kotlin
for (h in doc.select("main h3[id]")) {
    val category = FMHY_BY_ID[h.id()] ?: continue
    val items = h.nextElementSiblings()
        .takeWhile { it.tagName() !in setOf("h2", "h3") }
        .flatMap { it.select("li") }
    val apps = items.mapNotNull(::toMarketApp)
        .distinctBy { hostAndPath(it.url) }
        .take(category.cap)
        .mapIndexed { i, app -> app.copy(rank = i + 1) }
}
```

Live validation: all 16 section ids above exist today; P-Stream Forks has 6 entries, so that tab takes all 6. The seed currently holds 414 SFW apps.

### 4.7 ThePornDude scraper

One listing page per category:

| Category id | Listing URL |
|---|---|
| `tpd-tubes` | `https://theporndude.com/top-porn-tube-sites` |
| `tpd-cams` | `https://theporndude.com/top-sex-cam-sites` |
| `tpd-ai` | `https://theporndude.com/ai-porn-sites` |
| `tpd-onlyfans` | `https://theporndude.com/onlyfans-porn-sites` |
| `tpd-free-of` | `https://theporndude.com/free-onlyfans-accounts` |

Algorithm:
1. Fetch the listing, parse the first `script[type=application/ld+json]` as `ItemList`.
2. Take the first `cap + skipped` `itemListElement` entries in `position` order.
3. For each entry, derive `/json/<id>/<slug>` from its internal review URL and fetch the review JSON.
4. Skip `is_deadsite == true`; take the next ranked entry until 20 live sites are collected.
5. URL choice:
   - Use `display_slug` when it is a real destination (has a non-root path, or is not a platform root).
   - If `display_slug` is only `https://onlyfans.com` (the Free OF accounts category), resolve `data_category_link` with a redirect-following request, require the final host to be `onlyfans.com`, and keep only the profile segment: `/skylarfreexo/c89` → `/skylarfreexo`.
   - Never store `pdude.link`, `theporndude.com`, or affiliate tracking parameters.
6. Adult entries get `note = ""`; no scraped descriptions are stored or shown.
7. Cache the five categories together; a failed category keeps its previous apps.

Pseudocode:

```kotlin
val list = JSONObject(doc.selectFirst("script[type=application/ld+json]")!!.data())
for (item in list.getJSONArray("itemListElement")) {
    val review = JSONObject(Http.getStringQuiet("https://theporndude.com/json/$id/$slug") ?: continue)
    if (review.optBoolean("is_deadsite")) continue
    val url = canonicalUrl(review.optString("display_slug"), review.optString("data_category_link"))
    if (url != null) apps += MarketApp(name, url, category.id, "tpd", rank = apps.size + 1, adult = true)
    if (apps.size == category.cap) break
}
```

Rate/robustness:
- 5 listings + up to ~25 review JSONs + up to 5 redirect resolutions; concurrency 4.
- One retry on timeout/5xx; otherwise keep the old category.
- If ThePornDude shows a Cloudflare challenge, the JSON is not what we expect; treat it as a source error and keep the cache. The seed does not cover adult, so an offline first open shows a retry card — acceptable because adult is opt-in via NSFW.

### 4.8 Seed generation

- New dev-only script `tools/webapp_market_seed.py` (Python 3, `requests` + `beautifulsoup4`), using the same rules as §4.6.
- Output: `app/src/main/assets/webapp_market_seed.json` with `schema`, `generatedAt`, FMHY apps only.
- Run manually before a release; commit the JSON. It is not part of the APK build logic.
- A unit test parses the seed and asserts: schema version, known category ids, cap limits, valid http(s) URLs, no duplicates within a category.
- The seed gives a working market on first install and a fallback when FMHY is blocked or changed.

**Why not bundle the adult catalogue:** the repo/APK should not ship a porn directory, and adult entries need to respect NSFW from the first run. Fetching them on first Adult open keeps the seed clean and the feature opt-in.

### 4.9 UI specification

`WebAppMarketScreen`:

- Transparent `Scaffold`, content under the app's glass background, same as `WebsitesScreen`.
- Top bar: back, title `Web App Market`, source line `Curated from FMHY and ThePornDude`, refresh action.
- Search field: `Search the market`; filters name, host and category label; case-insensitive.
- Group row: glass pills, horizontally scrolling.
- Category row: smaller chips with counts; hidden when the group has one category.
- Grid: `LazyVerticalGrid(GridCells.Adaptive(160.dp))`; tablet min 180 dp; gap 8/12 dp.
- Card:
  - 72 dp tinted header with a 40 dp favicon (Google s2 favicons), rank badge `#1`, bookmark button top-right;
  - name (2 lines) + host (1 line);
  - saved state from `sitesFlow()`; filled bookmark means saved;
  - tap opens `WebViewActivity` with `url` + `title`;
  - bookmark tap calls `addSite`/`removeSite`, shows `Saved to Web Apps` / `Removed` toast.
- No page screenshots in the market. Favicons only: 160 cards’ worth of mShots would be slow, and adult thumbnails must not be shown.
- States:
  - loading skeleton / seed load is effectively instant;
  - empty category: `No apps in this category yet — refresh the market.`;
  - stale/error banner: `Couldn't refresh — showing the last catalogue.` + Retry;
  - last updated footer: `Updated 3 days ago · Sites open in Hikari's browser.`;
  - one-line footer note: `Adult categories follow Settings → Content → NSFW.`
- Accessibility: contentDescription for tabs, cards, bookmark state; 48 dp touch targets.
- Tablet: content already starts after the dock/rail via `navInset`; use `LayoutTokens.pageGutter` (32 dp) and more columns.
- Bottom: `LocalTaskbarInset.current + 24.dp` content padding so the last row is reachable behind the phone dock. Add the same fix to the existing `WebsitesScreen` grid/list.
- The market top bar should not duplicate the app's back gesture; system back pops.

### 4.10 Saving and "bookmarks"

- "Bookmark" = **Saved Web Apps**, the existing `AppStore.sites` list. This is the list shown on the Web Apps tab and the one the app already calls "Saved web apps".
- Saving from the market:
  ```kotlin
  app.store.addSite(Site(name = app.name, url = app.url))
  ```
  `addSite` already de-duplicates by URL and handles the DataStore read/write ordering.
- Removing uses `removeSite(url)`.
- Saved state is global by URL, so a site saved in one category shows as saved in every category it appears in.
- The WebView's own bottom bookmark strip remains separate in v1. **Optional follow-up:** migrate that strip to read the same `AppStore.sites` list (with a one-time import of `webview_bookmarks`), so there is one bookmark list everywhere. Not included unless requested (decision D5).
- Optional `Site.adult` flag: when saving an adult market app, mark it so `SiteGridCard` can use a neutral favicon instead of a page screenshot. Backward-compatible JSON field; not required for the market to function.

### 4.11 Adult content handling

- Reuse `NsfwGate` and `AppStore.nsfwEnabledFlow()`; default ON, as today.
- NSFW OFF:
  - Adult group is not rendered at all — no locked tab, no teaser.
  - No requests to ThePornDude.
  - If the Adult group was selected when the switch changed, fall back to the first SFW group and show a one-line toast.
  - Cached adult entries remain on disk but are not drawn.
- NSFW ON:
  - Adult group appears at the end of the group row, labelled `Adult`.
  - First open of the group triggers a ThePornDude refresh if no adult cache exists.
  - Adult cards show name + domain + favicon only; no descriptions, screenshots or preview text.
- No extra age dialog: the app's NSFW switch is the consent boundary, and adding a second one would be redundant.
- Add a short disclaimer in the market footer: links only, nothing hosted; adult categories follow the NSFW setting.

### 4.12 Code changes

| File | Change |
|---|---|
| `web/market/MarketModels.kt` *(new)* | `MarketApp`, `MarketCatalog`, `MarketState`, `MarketSource`. |
| `web/market/MarketCategories.kt` *(new)* | 6 groups, 21 categories, source URLs, caps, adult flags. Order is the UI order. |
| `web/market/MarketStore.kt` *(new)* | Seed load, cache read/write (atomic), schema check, 7-day staleness. |
| `web/market/FmhyMarketSource.kt` *(new)* | Jsoup parser from §4.6. |
| `web/market/ThePornDudeMarketSource.kt` *(new)* | JSON-LD + review JSON parser, dead-site skip, canonical URL/redirect cleanup from §4.7. |
| `web/market/WebAppMarketRepo.kt` *(new)* | StateFlow, mutex, refresh orchestration, per-category merge, error reporting. |
| `ui/screens/WebAppMarketScreen.kt` *(new)* | Groups, chips, search, grid, cards, states, adult lock. |
| `ui/screens/WebsitesScreen.kt` | Storefront action, empty-state market button, `LocalTaskbarInset` bottom padding. |
| `ui/navigation/AppNav.kt` | `Routes.WEB_APP_MARKET`, `tabBaseOf` mapping, composable, tablet dock branch, tablet inset fix. |
| `ui/tutorial/Tutorials.kt` | `webapp-market` first-visit tour + one line in the Web Apps tour. |
| `assets/webapp_market_seed.json` *(new)* | FMHY-only seed, generated by the script. |
| `tools/webapp_market_seed.py` *(new)* | Dev-only seed generator. |
| `test/.../web/market/*` *(new)* | Parser, catalog, URL and filter tests; fixtures. |
| `docs/WEB_APP_MARKET.md` *(new)* | Source list, refresh policy, attribution, NSFW behaviour, how to regenerate the seed. |

### 4.13 Tests

Unit (plain JUnit, existing test source set):
- FMHY fixture: starred item, mirror link after the site, internal link first, duplicate host, 12 items → top 10, correct names/URLs, no internal/mirror URLs.
- ThePornDude fixture: JSON-LD order, dead site skipped and next rank used, `display_slug` preferred, `pdude.link` rejected, onlyfans `/c89` stripped.
- URL normalisation: zero-width characters, `www.`, trailing slash, query tracking.
- Catalog: seed parses, schema is 1, all category ids known, caps respected, no duplicates per category.
- Filters: NSFW off hides only adult; search matches name/host/category; clearing search restores the selected category.
- Dock metrics: 15 tabs at 600 dp height fit with tiles ≥ 24 dp; 3 tabs centre; no negative sizes.

Manual:
- Phone: market opens/saves/removes/opens site; last grid row clears the dock; back returns to Web Apps.
- Tablet: market is inset past the dock; dock highlights Web Apps; rotation; both orientations.
- Offline: seed shows, refresh banner, saved apps still work.
- NSFW off: no Adult group and no ThePornDude request (verify via logcat); the footer points to Settings.
- NSFW on: adult group loads, first save appears in Saved Web Apps, no explicit description/screenshot.
- Themes and font scale.
- Process death/restore while on the market.

### 4.14 Attribution and risk

- Footer/overflow: `Catalogue sources: FMHY (fmhy.net/video) and ThePornDude. Links only — Lucid Hikari does not host or proxy these sites.`
- Add a `Source` line in the market overflow sheet with tappable source URLs.
- Adult categories are the user's choice via NSFW; the market itself does not auto-open adult content.
- ThePornDude affiliate links are never stored; canonical site URLs are.
- Scraping is best-effort and can break when a source changes; the seed + per-category cache make that a stale-data problem, not a blank-screen problem.

---

## 5. Risks and mitigations

| Risk | Mitigation |
|---|---|
| FMHY markup changes | Parse stable `h3[id]` sections; keep old category on failure; seed fallback; fixture tests. |
| ThePornDude markup/Cloudflare changes | Use Schema.org JSON-LD and review JSON; keep cache; seed does not depend on it for SFW; show retry. |
| Adult catalogue in repo/APK | Adult data is runtime-only and NSFW-gated. |
| Affiliate/tracking redirects | Prefer `display_slug`; resolve only platform roots; strip `/cNNN` and query; reject `pdude.link`. |
| 160-card grid performance | `LazyVerticalGrid`, favicons only, no mShots. |
| Dock overlaps content | Fixed 96 dp inset; 84 dp plate; metrics tests; last-row checks. |
| 15 tabs on a short tablet | Dynamic cell sizing; scroll fallback. |
| Existing tablet bottom padding | Fixed by the `!isTablet` condition. |
| Duplicate bookmark lists | Documented; market uses Saved Web Apps; optional WebView strip unification. |
| Network/privacy | No adult requests when NSFW off; no adult names/URLs in logs; source list documented. |

---

## 6. Sequencing

**Phase 1 — Tablet dock (independent, shippable alone)**
1. Extract `DockParts.kt`; verify phone dock is pixel-identical.
2. Add `TabletDock.kt` with metrics and fold.
3. Wire the `AppNav` branch + style mapping.
4. Fix `LocalTaskbarInset` for tablets.
5. Metrics tests + manual tablet matrix.
6. Ship as 0.16.1 or the first commit of 0.17.0.

**Phase 2 — Market data layer**
1. Models/categories.
2. FMHY parser + fixtures/tests.
3. ThePornDude parser + fixtures/tests.
4. Store/cache/repo.
5. Seed script + generated seed asset.
6. Verify counts against live sources.

**Phase 3 — Market UI**
1. Route + `tabBaseOf` mapping.
2. Screen shell, search, group/category rows.
3. Cards, saved state, toasts.
4. States, adult lock, footer.
5. Web Apps entry points + bottom inset fix.
6. Tutorial.

**Phase 4 — Polish and release**
1. Manual matrix (phone/tablet/TV/themes/RTL/offline/NSFW).
2. Docs (`docs/WEB_APP_MARKET.md`), README bullet, CHANGELOG, version bump.
3. Optional: unify WebView bookmark strip; optional `Site.adult`.

Rough effort: **dock 1–1.5 days; market 4–5 days; polish 0.5–1 day.** Two PRs is recommended: dock first (low risk), market second (needs review of the source behaviour).

---

## 7. Approved decisions (25 Nov 2026)

| # | Decision | Chosen |
|---|---|---|
| D1 | Market tabs: two-level groups + category chips, or one flat 21-tab row? | **Two-level** (6 groups + chips). |
| D2 | Tablet dock labels: icon-only like the Mac dock, or labels under the tiles? | **Icon-only**, matching the phone; contentDescriptions cover accessibility. |
| D3 | Adult catalogue: runtime-only (not in the seed), or bundled in the seed? | **Runtime-only**; the seed is SFW-only and NSFW gates the rest. |
| D4 | Free OnlyFans accounts: resolve the profile URL and strip tracking, or drop this category? | **Resolve** (`onlyfans.com/<profile>`), never store `pdude.link`; skip and take the next rank on failure. |
| D5 | Unify the WebView's bottom bookmark strip with Saved Web Apps? | **Not in v1**; Saved Web Apps is the market's bookmark target. |
| D6 | Ship order | **Dock first, then market** (two PRs). |

---

## 8. Acceptance criteria

### Tablet dock
- [ ] On a tablet (sw ≥ 600 dp), the default Mac style draws a vertical glass dock flush left, vertically centred.
- [ ] Same tabs/hidden set as the phone and rail; never empty.
- [ ] Tiles magnify, slide-to-select, show the running dot, haptic, long-press fold, folded-circle swipe, tap-to-unfold.
- [ ] Fits 15 tabs at 600 dp height; 3 tabs are centred; no clipping.
- [ ] Pages start after 96 dp and have **no** phantom bottom bar padding.
- [ ] Switching nav style live swaps dock ↔ rail; MultiView hides it; TV and phone unchanged.
- [ ] Portrait/landscape, RTL, four themes, mouse and touch pass.

### Market
- [ ] Web Apps has a visible market entry; empty state offers it.
- [ ] Market loads instantly from the SFW seed; 6 groups / 21 categories with the caps in §4.3.
- [ ] FMHY refresh returns up to 40 per category; P-Stream 6 and Cartoon 10 today.
- [ ] ThePornDude refresh returns 20 per adult category, skipping dead sites; no `pdude.link` stored; onlyfans profiles cleaned.
- [ ] Manual refresh updates the cache; offline/partial failure keeps old data with a clear banner; last-updated shown.
- [ ] Search works across visible categories; category counts match the grid.
- [ ] Bookmark adds/removes from Saved Web Apps and survives restart; saved state is global by URL.
- [ ] Tapping a card opens the site in `WebViewActivity`; back returns to the market.
- [ ] NSFW off hides the Adult group entirely and makes zero ThePornDude requests; NSFW on loads it on first open.
- [ ] No adult descriptions or page screenshots in the market or saved grid (favicon tiles).
- [ ] Unit tests and the manual matrix pass; docs/README/CHANGELOG updated.

---

## 9. Source notes (for the implementation)

- FMHY: `https://fmhy.net/video`. Attribution: FMHY. No content is copied from the page beyond site names and URLs; `note` is optional and SFW-only.
- ThePornDude:
  - `https://theporndude.com/top-porn-tube-sites`
  - `https://theporndude.com/top-sex-cam-sites`
  - `https://theporndude.com/ai-porn-sites`
  - `https://theporndude.com/onlyfans-porn-sites`
  - `https://theporndude.com/free-onlyfans-accounts`
- Both sources are fetched with the app's existing OkHttp client and Jsoup; no new dependency.
- The market stores links only. Opening a site uses the existing in-app browser with ad blocking, popup/redirect protection and the user's shared cookies.
