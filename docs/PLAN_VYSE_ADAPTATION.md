# Implementation plan — Vyse-inspired Live TV, player polish, motion, cohesion, Home zoning

**Status: APPROVED. Phases A–E are implemented on `feat/vyse-adaptation`; release 0.13.0 is being published.**
**Target app:** Lucid Hikari 0.13.0 (`com.lucid.hikari`)
**Prepared:** 15 Nov 2026

### Progress log

| Phase | State | Commit |
|---|---|---|
| 0 — pre-flight | done | branch `feat/vyse-adaptation`, baseline screenshots in `docs/screenshots/baseline/` |
| A — foundations | done | `2f2eb4d0` |
| B — Home zoning (phone) | done | `5a8d5dfe`, `4b131b76` |
| B11 — TV zoning | done | tagline, donation banner and duplicate floating provider pill removed; header pill kept (`5a8d5dfe`) |
| C — Live TV mechanics | done | `d2e43d5a`, `60039118` |
| D — player proof of quality | done | `be716342` |
| E — cohesion + animation pass | done | `8c26ad88` |

---

## 0. How to approve

Reply with either:

- `APPROVE ALL` — I start at Phase 0 and run the phases in order, stopping after each for your review, or
- `APPROVE A,B,C,D,E` — a subset, or
- `APPROVE with D2=B, D3=A, …` — the decisions in §4 changed, everything else as planned.

I will not create a branch, edit a file, or run a build until you reply.

---

## 1. Scope

This plan covers exactly four requests:

| # | Request | Phase |
|---|---|---|
| 1 | Live TV mechanics: byte-sniffing health, EPG exact-feed finder, feed-variant filters | C |
| 2 | Player "proof of quality" line (formats, chapter/segment marks, ends-at) | D |
| 3 | Animations + a more cohesive UI/UX and design system | A + E |
| 4 | Home zoning rewrite | B |

Everything else in the Vyse report is **out of scope** for this approval (see §16).

---

## 2. The no-breakage contract

These are hard rules for every phase. If a step cannot satisfy them, it is redesigned, not forced.

1. **Additive first.** New files and new optional parameters with defaults. No renames, no signature removals, no route changes, no XML id renames.
2. **No dependency or SDK changes.** No Gradle upgrades, no `minSdk`/`targetSdk`/`compileSdk` changes, no new libraries (except an optional test-only JUnit entry, marked in §4 D12).
3. **No schema migrations.** New DataStore keys only, always with a default. Existing keys keep their meaning and format. Removing a key is forbidden.
4. **Playback logic is untouchable.** `PlayerActivity`'s source selection, failover, dual-audio pairing, autoplay handoff, PiP and subtitle pipeline are not modified. Phase D only reads existing state and updates new views.
5. **Extension and reader engines are untouchable.** `cs3/`, `nuvio/`, `stremio`, `reader/`, `manga/`, `aniyomi/`, `kotatsu/`, `lnreader/`, `telegram/`, `youtube/`, `mediaservicecore/` are not edited at all.
6. **One concern per commit.** Every commit builds on its own and can be reverted without reverting the phase.
7. **Old code stays until the new path is verified.** Old composables/functions are only deleted in a separate cleanup commit, after your sign-off.
8. **Every phase ends with a build + a manual test pass** against §13. A failed check stops the phase; it does not roll forward.
9. **No behaviour change without a visible reason.** If a change cannot be seen or measured by the user, it does not ship.

---

## 3. What the code actually does today (facts this plan relies on)

| Fact | Evidence |
|---|---|
| Home is one `LazyColumn` in a `Box`, with a floating provider pill at `BottomEnd` | `HomeScreen.kt` ~990–1030, ~1415–1463 |
| The feed opens: header → donation banner → Continue Watching → collections → shelves | `HomeScreen.kt` ~1159–1310 |
| `ContinueWatchingRow` is used by Home only | `Components.kt:1495`, one call site |
| The taskbar inset is already handled | `LocalTaskbarInset` used in Home's `contentPadding` |
| IPTV health is HTTP-status only | `StreamProbe.probe()` → `ProbeResult(code, latency)`, `isAlive = 200..399` |
| The wall already matches channels by keywords/exclude across playlists | `iptv/ChannelWallChannels.kt`, `ChannelWallScreen.openWall` |
| The portal generator already ports Cloud Vault + Reddit + paste.sh | `iptv/PortalGenerator.kt` |
| XMLTV now/next exists, capped at 12 MB | `data/XtreamEpg.kt` |
| There is no per-channel short EPG call yet | no `get_short_epg` in the tree |
| `SportsMatcher` matches by teams/networks/group, not EPG text | `sports/SportsMatcher.kt` header comment |
| `ChannelMatch` already has `programTitle` but no feed label | `sports/SportsModels.kt:158` |
| The player already has `badge_duration`, `badge_quality`, `badge_source` | `player_controller.xml`, `PlayerActivity` ~1855 |
| The player already computes HDR/audio/video summaries for "Codec details" | `videoSummary()`, `audioSummary()`, `rangeLabel()` ~4164–4200 |
| The SKIP button is a blind +85 seconds | `PlayerActivity` ~2396 |
| No chapter extraction exists anywhere in the player | no `chapter` matches in `player/` |
| Six different section-header implementations exist | `MediaRow`, `Cs3ShelfHeader`, `ExtensionsScreen.SectionHeader`, `SportsScreen.LiveSectionTitle`, `SportsLeagueScreen.SectionTitle`, `MusicSectionTitle` |
| No test source set exists | `app/src` contains only `main` |
| `gradlew` is a stub that calls system `gradle` | `gradlew` is 45 bytes |

---

## 4. Decisions I need from you

Each has a recommendation. Reply with only the letters you want to change.

| # | Decision | Options | Recommended |
|---|---|---|---|
| **D1** | Home brand header | (A) keep title + tagline; (B) keep title, drop tagline; (C) title only, collapse on scroll | **B** — the tagline costs ~20dp on every launch and says nothing after day one |
| **D2** | Donation banner | (A) remove from Home, move to Settings → About; (B) keep but make it a one-time dismissible card; (C) remove entirely | **A** — it is the loudest non-content element on the first screen |
| **D3** | Genre strip | (A) keep, shrink to one 32dp row below Resume; (B) move to Discover; (C) turn into a "Genres" chip that opens a sheet | **A** — least risk, keeps the feature, stops it blocking the hero |
| **D4** | Provider switch | (A) header chip (recommended); (B) keep floating pill but move it above the taskbar centre; (C) leave as is | **A** |
| **D5** | Continue Watching | (A) compact 16:9, max 6, remove via long-press; (B) keep current cards, just hide the ✕ until long-press | **A** |
| **D6** | Animation intensity | (A) subtle (alpha/translate, 180–260ms); (B) expressive (scale/spring, 300–420ms) | **A** |
| **D7** | Default poster preset | (A) Clean on phones, Cinematic on TV; (B) keep the current default; (C) Cinematic everywhere | **A** |
| **D8** | Live TV IA | (A) leave the current page structure, only add mechanics; (B) four tabs Matches/Channels/Portals/Playlists | **A now, B later** — B is a separate 3-day phase and changes navigation habits |
| **D9** | Exact-feed lookup | (A) only when the user opens a fixture; (B) also prefetch when the Sports tab opens | **A** — B multiplies requests by the number of fixtures |
| **D10** | Chapter marks | (A) IntroDB/AniSkip segment marks first, container chapters later; (B) container chapters only; (C) both now | **A** — segment marks are isolated and also power skip; container parsing touches playback-adjacent code |
| **D11** | Cleanup of old Home code | (A) delete old composables after sign-off; (B) keep them unused | **A** — dead code is how the next change breaks silently |
| **D12** | Unit tests for pure parsers (`FeedTags`, `FeedFinder` scoring) | (A) add a `test` source set + JUnit (build-file change, additive); (B) no tests, verify manually | **A** — these are pure functions and the highest-value tests in the plan |
| **D13** | Runtime fallback for Home | (A) none, rely on git revert; (B) a hidden "Classic Home" setting | **A** — a second layout is a maintenance trap |

---

## 5. Phase map

| Phase | Name | Effort* | Depends on | User-visible change |
|---|---|---|---|---|
| 0 | Pre-flight and baseline | 0.5 d | — | none |
| A | Foundations: tokens, motion, shared header | 2 d | 0 | almost none (new components unused) |
| B | Home zoning | 5 d | A | large |
| C | Live TV mechanics | 8 d | A | large |
| D | Player proof of quality | 5 d | A | medium |
| E | Cohesion pass across screens | 5 d | A, B | medium |

\* Implementation time, excluding your review time. Phases are sequential; each ends with a build and a test pass.

**Calendar plan:** 4–5 weeks at a comfortable pace with review gaps, or ~2 weeks of uninterrupted work. I recommend Phase B first (Home), then C, then D, then E, because Home gives the biggest immediate quality jump and validates the token/motion work before it is applied broadly.

---

## 6. Phase 0 — Pre-flight (0.5 day)

**Goal:** a known-good baseline and a way back.

1. Confirm the working tree is clean; commit or stash your current work.
2. Create branch `feat/vyse-adaptation` from your current HEAD.
3. Build and install once: `gradle :app:assembleDebug`.
4. Capture a baseline: screenshots of Home (top, scrolled), Live TV, a channel sheet, Sports match page, player (VOD + live), Settings root — on phone portrait, phone landscape and TV if available.
5. Record a 10-second `dumpsys gfxinfo` scroll trace on Home for later comparison (optional but useful).

**Exit:** a branch, a debug APK, a screenshot folder, no code changes.

---

## 7. Phase A — Foundations (2 days)

**Goal:** create the vocabulary the rest of the work uses, without changing any existing screen.

### A1. Spacing and radius tokens

New file `ui/theme/Spacing.kt`:

```kotlin
object HikariSpacing { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object HikariRadii { val chip = 8.dp; val card = 12.dp; val sheet = 16.dp; val dialog = 22.dp }
```

No call site changes yet.

### A2. Motion tokens + a system-level "reduce motion" signal

New file `ui/theme/Motion.kt`:

```kotlin
object HikariMotion {
    const val quick = 180          // state change
    const val standard = 260        // reveal / expand
    const val spatial = 320         // sheets, page-level moves
    val easeOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}
```

`HikariMotion.enabled(context)` reads Android's animator duration scale (`Settings.Global.ANIMATOR_DURATION_SCALE`). If animations are off system-wide, every new animation becomes an instant swap. This avoids adding a new app setting and respects accessibility.

### A3. Shared section header

New file `ui/components/SectionHeader.kt`, with one composable used by every new/migrated screen:

```
| accent bar (3dp) | Title (15/700) | optional provider/subtitle (10.5 uppercase, 55%) | ... | action (chevron or text) |
```

Variants: `plain` (no action), `withCount`, `withChevron`. Live TV and Sports pass a count; Home passes a chevron.

### A4. Shared chip row

New file `ui/components/ChipRow.kt`: one horizontally scrollable row with consistent height (32dp), padding (12×6), selected state (accent fill, ink text) and unselected state (surface + hairline). Replaces `FilterChipLine`, `IptvSectionStrip`, `SportsTab` only in Phase E, one screen at a time.

### A5. Shared badge

New file `ui/components/Badge.kt`: `HikariBadge(text, kind = Neutral | Accent | Live | Quality)`. White-on-black for quality, accent for live only, neutral for metadata. Used by Phase D's player line (in Compose screens) and by Home.

**Exit criteria**

- `gradle :app:assembleDebug` passes.
- The app looks and behaves exactly as before (new components unused).
- No existing file edited except possibly imports.

**Risk:** none functional. **Rollback:** delete the branch's four new files.

---

## 8. Phase B — Home zoning rewrite (5 days)

**Goal:** one job per zone; no element floating over content; content appears before configuration.

### B1. Target structure

```
BEFORE                                    AFTER
┌───────────────────────────────┐         ┌───────────────────────────────┐
│ Lucid Hikari                  │         │ Lucid Hikari   [Provider ▾] 🔍│  ← one header row
│ Every stream, one place.      │         ├───────────────────────────────┤
├───────────────────────────────┤         │            HERO               │  ← 16:9, quiet
│           HERO                │         ├───────────────────────────────┤
├───────────────────────────────┤         │ Resume                    ›   │  ← compact 16:9 cards
│ [Action][Adventure][Drama]…   │         ├───────────────────────────────┤
├───────────────────────────────┤         │ [Genres]  ← one 32dp chip row │  ← moved below Resume
│ 🎁 Donate card                │         ├───────────────────────────────┤
├───────────────────────────────┤         │ Popular Movies            ›   │  ← no provider label
│ Continue Watching (big cards) │         ├───────────────────────────────┤
├───────────────────────────────┤         │ …shelves…                     │
│ Popular Movies   (NETMIRROR)  │         │                               │
│ …            [All providers]  │  ← pill │                               │
└───────────────────────────────┘         └───────────────────────────────┘
```

### B2. Steps

| Step | Change | Files | Safety |
|---|---|---|---|
| B2.1 | Add `providerLabel`, `providerIcon`, `onProviderClick`, `compact` optional params to `HomeHeader`; render a chip when non-null | `HomeScreen.kt` | Defaults keep every existing call working |
| B2.2 | Pass the picker callback into the header; delete the floating `Surface` pill | `HomeScreen.kt` | Only after the chip renders in the same build |
| B2.3 | Move the donation item out of the `LazyColumn`; add a row in Settings → About | `HomeScreen.kt`, `SettingsScreen.kt` | Settings row is a new item; nothing removed |
| B2.4 | Move `HomeGenreStrip` below Continue Watching; shrink to 32dp height and 10 visible chips | `HomeScreen.kt` | Pure reorder + size constants |
| B2.5 | Add `variant` param to `ContinueWatchingRow` (default `Legacy`); implement `Compact` | `Components.kt` | Default preserves the current call site if anything else ever uses it |
| B2.6 | Switch Home to `variant = Compact`, max 6 entries, remove action moves to long-press menu | `HomeScreen.kt` | One-line change to the call |
| B2.7 | Replace Home shelf headers with `HikariSectionHeader`; add `showProvider: Boolean = true` to `MediaRow`, Home passes false for single-provider rows | `Components.kt`, `HomeScreen.kt` | Default keeps TV and other call sites identical |
| B2.8 | Tighten shelf spacing (24 top / 12 bottom) and poster title to 12.5sp one line | `Components.kt` | Visual only |
| B2.9 | First-load shelf reveal: first 8 cards, 40ms stagger, 220ms fade+6dp rise, once per row; disabled on TV and when reduce-motion is on | `Components.kt` (row wrapper) | Wrapper only; `PosterArt` is untouched |
| B2.10 | Genre/collection/provider empty states use the shared `EmptyState` | `HomeScreen.kt` | Existing component |

### B3. What does not change

- The feed logic (`HomeViewModel`, `rowsFlowFor`, `HomeFeedCache`, selection, collections) — no edits.
- Scroll restoration (`ScrollKeeper`, `feedKeys`) — the key list changes only to remove `donate-banner`; `ScrollKeeper` tolerates missing keys by design (it re-finds by item key).
- `PosterArt`, `PosterStyle`, `PosterEffects`, `PosterLoader` — no edits. Poster rendering is your signature; this phase only changes what surrounds it.
- TV home (`TvCinematicHero`, `TvHomeHeaderBar`, `TvProviderStrip`) — no edits in Phase B. TV is a separate layout path and gets its own pass in Phase E.

### B4. Exit criteria

- Home shows header chip, hero, Resume, genre row, shelves; no floating pill; no donation card in the feed.
- Scrolling still restores position after opening a title and pressing Back.
- Provider switching, collections, multi-select and empty/loading states all still work.
- Taskbar never covers the last row (verify with a long feed).
- No jank introduced: compare `gfxinfo` with the Phase 0 trace.
- Screenshots match the target structure on phone portrait/landscape and tablet.

**Risk:** medium (this is the largest visual change). **Mitigation:** every step is a separate commit; B2.2 happens after B2.1 is verified; the old header/pill code is deleted only in the cleanup commit.

---

## 9. Phase C — Live TV mechanics (8 days)

**Goal:** make the green dot mean "this stream plays", make channel variants navigable, and name the exact feed showing a fixture.

### C1. Byte-sniffing health (1.5 days)

**New** in `net/StreamProbe.kt` (additive):

```kotlin
enum class Verdict { LIVE, NOT_VIDEO, DEAD, UNKNOWN }

suspend fun probeVerdict(url: String, headers: Map<String, String>): ProbeResult
```

`ProbeResult` gains `val verdict: Verdict = Verdict.UNKNOWN` and `val isPlayable: Boolean get() = verdict == Verdict.LIVE`.

Classification (reusing the sniffing already in `follow()`):

| Check | Rule |
|---|---|
| HTTP | 2xx/206, else `DEAD` |
| Content type | `text/html`, `application/json`, `text/xml`, `text/plain` → `NOT_VIDEO` |
| Content length | 1 … 5,000,000 bytes → `NOT_VIDEO` (placeholder heuristic) |
| HLS | first 1 KB contains `#EXTM3U` → `LIVE` |
| MPEG-TS | byte 0 `0x47`, every 188th `0x47`, ≥3 packets → `LIVE` |
| MP4 | bytes 4–8 `ftyp` → `LIVE` |
| Codecs | ADTS `0xFFFx`, Matroska `1A45DFA3`, Ogg `OggS`, MPEG start code → `LIVE` |
| Fallback | ≥32 KB of bytes → `LIVE`, else `DEAD` |

**Compatibility rule:** `isAlive` keeps its current meaning (`200..399`). Only `healthOf()`, the wall and MultiView migrate to `isPlayable`, one call site per commit.

**Bulk validate:** `validateAllPlaylists()` currently probes 3 playlists deep with per-channel `probe()` calls. Add a global semaphore of 8 and use `probeVerdict`. Cancel on screen exit.

**Exit:** a 200 HTML "account expired" page shows red; a real TS/HLS shows green; a 404 shows red; the legend explains the colours once.

### C2. Feed variants and filters (2.5 days)

**New** `iptv/FeedTags.kt` (pure):

```kotlin
data class FeedTags(val number: String?, val lang: String?, val quality: String?)
fun parseFeedTags(name: String): FeedTags
```

Rules: first 1–2 digit token; language from the fixed set already used in your channel data (`AR, EN, FR, TR, ES, DE, IT, PT, …`); quality precedence `4K/UHD > FHD/1080 > HD > SD`.

**New** `iptv/FeedFilters.kt` (Compose): conditional filter rows. A row appears only when the current result set has more than one value for that dimension. State is per-sheet, reset on open.

Apply in this order:

1. `ChannelWallScreen` wall sheet (the list after tapping a tile).
2. Quick Channels sheet in `IptvScreen`.
3. Portal/category channel list (if the group page renders a flat channel list; otherwise defer).

Sorting: pinned → exact-feed match (C3) → quality rank → name.

**Pinning by stream URL:** the wall’s favourite is per wall id; add a per-source star keyed by `wallId|url` stored in a new DataStore key (JSON map). Additive; the existing favourite behaviour is unchanged.

### C3. EPG exact-feed finder (4 days)

**New** `data/XtreamShortEpg.kt`:

```
GET {root}/player_api.php?username=&password=&action=get_short_epg&stream_id={id}&limit=6
```

- Resolve creds from `XtreamEpg.credentialsFor(providerId)` (already exists).
- Resolve `streamId` from the channel URL path (`/(\d+)\.(ts|m3u8|mp4)$`); skip channels with no numeric id.
- Base64-decode `title`/`description`; parse `start_timestamp`/`stop_timestamp` (seconds) with string fallback.
- Cache per `root|user|streamId` for 10 minutes; concurrency 3; 15s timeout.

**New** `sports/FeedFinder.kt` (pure scoring + orchestration):

1. Group candidates by `number|language` (so ten portals carrying the same feed cost one lookup).
2. For each group, take the first candidate that resolves to an Xtream channel with a stream id; fetch its guide; if empty, try the next portal in the group (max 3).
3. Score programmes in the window `kickoff −30 min … +2h30`:
   - accent-fold, lowercase, strip punctuation;
   - tokens ≥4 chars, excluding the generic list (`united, city, real, club, sporting, athletic, atletico, inter, state, university, town, county, rovers, wanderers, olympique, racing, deportivo, sport, sports, team, new, saint`);
   - tokens ≥6 chars: substring; shorter: word boundary;
   - score = number of team sides mentioned; 2 sides = strong.
4. Return `feedLabel` (`"beIN Sports 2 · AR"`) and `feedTitle` (the programme).
5. Cache per `matchId|channelId` for the session.

**Data model (additive):** `ChannelMatch` gains `feedLabel: String? = null`, `feedTitle: String? = null`, `feedScore: Int = 0`. Existing constructors keep working.

**UI:**
- On the match page, channel tiles show the exact feed under the name once found.
- A small status line: "Reading guides 3/8" while working; nothing if no EPG.
- Matched feeds sort first.
- Only runs when the fixture is opened (`D9 = A`).
- M3U-only channels fall back to `XtreamEpg.lookupForChannel` (now/next); if no guide, no label.

**Exit:** a Champions League fixture shows `beIN Sports 2 · AR` and `beIN Sports 1 · FR` as separate tiles, and the wrong feeds are not labelled.

### C4. Optional (only if `D8 = B`, separate approval)

The four-tab Live TV restructure (Matches / Channels / Portals / Playlists). Not part of this phase unless you approve it.

---

## 10. Phase D — Player proof of quality (5 days)

**Goal:** the player states what is playing and where you are, without opening a panel.

### D1. Format badge line (1 day)

- Add `badge_meta` TextView to `player_controller.xml` after `badge_quality` (additive id, `visibility="gone"`).
- Build its text from existing helpers: `rangeLabel(f)` (HDR10/HLG/SDR), `codecName()` for audio/video, `channelsBadge()` (5.1/7.1), `codecName()` short form.
- Update in the same callbacks that already set `badge_quality` (`onKnownVideoSize`, `onTracksChanged`).
- Example: `4K` `HDR10` `DD+ 5.1` `HEVC`.

**No new parsing, no new network.** All helpers already exist.

### D2. Ends-at (0.5 day)

- Extend `badge_duration` text from `2:52:35` to `2:52:35 · ends 12:02 AM` (or add a separate `badge_ends` — decision during implementation based on width).
- Compute from `duration - currentPosition` plus wall clock; refresh on play/pause/seek and every 30s while playing (one `Handler` message, cancelled in `onStop`).
- 24-hour setting respected if one exists; otherwise follow the system locale.

### D3. Segment marks + smarter skip (2 days, `D10 = A`)

- **New** `data/IntroSegments.kt`: fetch TheIntroDB (`api.theintrodb.org/v4/media?tmdb_id=…&season=…&episode=…`) and AniSkip for anime; cache per title/episode for the session; 8s timeout; empty on failure.
- Only runs when a TMDB id is available. Pass a new optional `tmdbId` extra from Detail (additive; absent for extensions → no segments, unchanged behaviour).
- Draw marks on the seek bar: intro (1 colour), credits (another), preview/recap (dim).
- Replace the blind `SKIP +85s` with: if a segment covers the current position, seek to its end and show the segment name; otherwise keep the current +85s behaviour.
- Settings: a switch to turn segment lookup off (new DataStore key, default on).

**Why not container chapters first:** MP4 `chpl` / Matroska chapter parsing is a new parser on the playback path and cannot be tested without sample files. Segment marks are isolated, network-only, and also improve skip. Container chapters can be D3b later, behind the same marker UI.

### D4. Chapter/segment marker view (1 day)

- **New** `player/ChapterMarkerView.kt`: a custom `View` that draws 2dp ticks at fractions.
- Added to the player root (not to media3's `exo_basic_controls`, per the layout's own warning).
- Positioned on `exo_progress`'s layout bounds via `addOnLayoutChangeListener`; re-positioned on rotation and control show/hide.
- Non-interactive in v1 (no tap targets) to avoid stealing touches from the time bar.
- Hidden when there are no marks.

### D5. Subtitle sync quick buttons (0.5 day)

- The offset already exists. Add `−0.5s / Reset / +0.5s` in the subtitle sheet header, with the current value shown.
- No change to the subtitle pipeline.

**Exit:** a VOD title shows quality + HDR + audio + codec; ends-at is correct across pause/seek; intro marks appear on a TMDB title and SKIP jumps to the segment end; live TV shows no chapter marks and no ends-at (duration is 0).

---

## 11. Phase E — Cohesion and animation across the app (5 days)

**Goal:** every screen uses the same header, chips, badges, states and motion.

### E1. Unify section headers (1.5 days)

Replace, one screen per commit:

1. `SportsScreen.LiveSectionTitle`
2. `SportsLeagueScreen.SectionTitle`
3. `ExtensionsScreen.SectionHeader`
4. `MusicSectionTitle`
5. `MediaRow` header (already from Phase B)

`Cs3ShelfHeader` (TV) is **not** touched — it is a TV-specific layout with focus chevrons.

### E2. Unify chips/tabs (1.5 days)

- Replace `FilterChipLine` (Home), `IptvSectionStrip` (IPTV), `SportsTab` and the wall's region chips with `HikariChipRow`.
- Keep each screen's behaviour; only the visual shell changes.
- IPTV's `HorizontalPager` stays as is.

### E3. Replace hardcoded colours and sizes in the two heaviest screens (1 day)

- `IptvScreen.kt` has ~50 `Color.White`/`Color(0xFF…)` and many inline `fontSize` values; `ChannelWallScreen.kt` ~61.
- Replace with `MaterialTheme.colorScheme` and `HikariType` tokens **only where the value is semantically a token** (text, muted, surface, accent). Do not touch channel-brand gradients or generated art.
- This is the riskiest cosmetic work; it is done file-section by file-section with screenshots.

### E4. Animation pass (1 day)

| Element | Trigger | Motion | Guard |
|---|---|---|---|
| Home shelf cards | first composition, first 8 | fade + 6dp rise, 40ms stagger, 220ms | TV off, reduce-motion off |
| Poster press | press | scale 0.98 → 1, spring | no layout change |
| Continue Watching progress | value change | `animateFloatAsState` 400ms | cheap |
| Section header | first composition | fade + 4dp rise, 180ms | once per screen |
| IPTV health dot | verdict change | colour crossfade 200ms | no pulse |
| EPG progress | time tick | linear animation between updates | no extra fetches |
| Sheet open/close | existing | keep Material default | — |
| Player badges | text change | fade 150ms | no layout jump |
| Page transitions | existing | keep; do not lengthen | — |

Explicitly **not** animated: poster `tilt` (TV focus), list size changes, blur radii, shared-element transitions (experimental API).

### E5. States audit (0.5 day)

Make every screen use the same empty/loading/error component:
Home, Search, IPTV, Sports, Downloads, History, Music, Manga, Extensions.
Fix the copy: one sentence, no exclamation marks, no marketing.

**Exit:** a screen-by-screen screenshot set shows one header style, one chip style, one empty state, and consistent motion.

---

## 12. File manifest

### New files

| File | Phase | Purpose |
|---|---|---|
| `ui/theme/Spacing.kt` | A | spacing/radius tokens |
| `ui/theme/Motion.kt` | A | durations, easing, reduce-motion |
| `ui/components/SectionHeader.kt` | A | one header for every screen |
| `ui/components/ChipRow.kt` | A | one chip/tab row |
| `ui/components/Badge.kt` | A | metadata/live/quality badges |
| `iptv/FeedTags.kt` | C | pure name → number/lang/quality parser |
| `iptv/FeedFilters.kt` | C | conditional filter rows |
| `data/XtreamShortEpg.kt` | C | per-channel short EPG |
| `sports/FeedFinder.kt` | C | EPG scoring + orchestration |
| `data/IntroSegments.kt` | D | IntroDB / AniSkip segments |
| `player/ChapterMarkerView.kt` | D | seek-bar tick marks |
| `app/src/test/.../FeedTagsTest.kt` (D12=A) | C | parser tests |
| `app/src/test/.../FeedFinderTest.kt` (D12=A) | C | scoring tests |

### Edited files (additive only)

| File | Phase | Nature of edit |
|---|---|---|
| `HomeScreen.kt` | B | header params, remove pill/banner, reorder, call-site flags |
| `Components.kt` | B | `ContinueWatchingRow` variant, `MediaRow` header + flags |
| `SettingsScreen.kt` | B | one About row for donations |
| `net/StreamProbe.kt` | C | `Verdict`, `probeVerdict`, `isPlayable` |
| `IptvScreen.kt` | C | verdict health, validate concurrency, feed filters |
| `ChannelWallScreen.kt` | C | filters, per-source pin, feed labels |
| `SportsScreen.kt` | C | feed labels + status line |
| `SportsModels.kt` | C | three optional fields on `ChannelMatch` |
| `player_controller.xml` | D | `badge_meta`, optional `badge_ends` |
| `PlayerActivity.kt` | D | set badge, ends-at timer, marker attach, segment skip |
| `DetailScreen.kt` | D | pass optional `tmdbId` extra |
| `SportsLeagueScreen.kt`, `ExtensionsScreen.kt`, `MusicScreen.kt` | E | header migration only |
| `assets/i18n/*.json` | B–E | new English keys only; no removed keys |

### Explicitly untouched

`player/PlayerHttp.kt`, source failover logic, `PlayerActivity` playback pipeline, `PlayerActivity` autoplay handoff, `cs3/`, `nuvio/`, `reader/`, `manga/`, `aniyomi/`, `telegram/`, `youtube/`, `mediaservicecore/`, `download/`, `tracker/`, `pair/`, `updater`, `AppStore` existing keys, `MainActivity`, navigation routes, Gradle files (except the optional test dependency).

---

## 13. Verification matrix

Every phase is checked against this. A tick means it was actually run, not assumed.

### Build

- [ ] `gradle :app:assembleDebug` passes.
- [ ] `gradle :app:assembleRelease` passes (if a signing config is available), or `assembleDebug` + R8 dry-run.
- [ ] No new warnings in `:app:compileDebugKotlin`.

### Screens

| Area | Checks |
|---|---|
| Home | top, scrolled, provider switch, collection, multi-pick, empty, loading, offline |
| Home | Back from detail restores row + scroll position |
| Home | taskbar does not cover the last item; with 3 different taskbar layouts |
| Live TV | wall sheet filters (number/lang/quality), pin, open, play |
| Live TV | health dot on a real 200-HTML fake URL (should be red) |
| Live TV | 20k-channel portal: validate completes, no ANR, cancel works |
| Sports | fixture with EPG → feed label; fixture without EPG → unchanged |
| Player | VOD MP4, HLS live, MKV, dual audio, subtitles, PiP, autoplay |
| Player | ends-at after pause/seek; no ends-at for live |
| Player | segment marks + skip on a TMDB title; no marks elsewhere |
| All | AMOLED, Dark, Glass, Light themes |
| All | font scale 0.85 / 1.0 / 1.3; in-app UI scale on/off |
| All | English, Arabic (RTL), Hindi (long strings) |
| TV | d-pad traversal: every new element reachable in ≤6 moves; no focus loss |
| Perf | Home scroll `gfxinfo` within 5% of baseline; no new dropped frames |

### Regression spot-checks specific to this work

- Extension installation still works.
- Manga reader still opens and remembers position.
- Downloads still resume.
- YouTube playback unchanged.
- Updates check unchanged.
- Party/pairing unchanged.
- Settings search still finds every setting (including the new About row).

---

## 14. Rollback and checkpoints

- One branch: `feat/vyse-adaptation`.
- One tag per phase start: `pre-A`, `pre-B`, `pre-C`, `pre-D`, `pre-E`.
- One commit per numbered step (e.g. `B2.5 continue-watching: add Compact variant`).
- Every commit builds. If a phase fails its exit criteria, revert that phase's commits to the tag; nothing else is affected.
- No force-pushes, no rebases of your existing history.

---

## 15. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Home reorder breaks scroll restoration | Medium | Low | `ScrollKeeper` re-finds by key; test Back from detail before merging |
| Floating pill removal hides provider switching | Low | Medium | Header chip ships in the same commit; test multi-pick |
| Verdict probe misclassifies a working stream | Medium | High | Fallback threshold 32 KB; verify against 20 known-good channels; keep `isAlive` untouched |
| `get_short_epg` missing on some panels | High | Low | Skip group, try next portal, no label — current behaviour |
| EPG requests slow down the match page | Medium | Medium | Only on open, 3 concurrent, 15s timeout, session cache, cancel on leave |
| New XML view breaks media3 layout | Low | High | Additive `TextView` only; marker view lives in the player root, not `exo_basic_controls` |
| Chapter marker misalignment | Medium | Low | Calibrate on 3 densities; hide on live; non-interactive |
| Token migration changes a colour unintentionally | Medium | Low | Semantic-only replacements; screenshot diff per screen |
| Animations cause TV focus jank | Medium | Medium | Disabled on TV; compositor-only properties; reduce-motion respected |
| i18n: new strings untranslated | High | Low | English fallback is the existing behaviour; translations can follow |
| 19k-line `PlayerActivity` edit conflicts | Low | High | Insertions only, at named existing callbacks; no reformatting |

---

## 16. Out of scope (not in this approval)

- Live TV four-tab IA restructure (D8 = B).
- Portal browser three-pane guide layout.
- Discover filter rail/sheet.
- Paste-anything search.
- Source-state detail block.
- Vyse-style watch party.
- Signed update manifest.
- Any scraper/source changes.
- Any extension/reader/YouTube/download/tracker change.
- Removing or replacing `PosterEffects`.
- Changing the app's default font family.

Each can be planned separately.

---

## 17. Definition of done

A phase is done when:

1. It builds in debug (and release where signing allows).
2. Its exit criteria in §7–§11 are met.
3. The verification matrix in §13 is ticked for the affected rows.
4. Screenshots are captured for the changed screens in 3 themes and 2 form factors.
5. A short changelog entry is written in the style of your existing `CHANGELOG.md`.
6. You have reviewed the diff and said "merge".

---

## 18. What I need from you

1. **Approve the phases** you want (or all).
2. **Answer the decisions in §4** that you want changed.
3. **Choose the start point**: `A → B → C → D → E` (recommended) or `B first, then A` (faster visible change, but Home would be built before the tokens exist — not recommended).
4. **Confirm the branch name** `feat/vyse-adaptation`, or give me another.
5. **Confirm the build command** — I will use `gradle :app:assembleDebug`; tell me if you use a different task or a local JDK setup.

Once you reply, I will start with Phase 0 only, show you the baseline, and wait for your go-ahead before Phase A.
