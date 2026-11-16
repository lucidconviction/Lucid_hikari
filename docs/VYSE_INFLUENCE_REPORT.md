# Lucid Hikari × VyseTV — design and product report

**Prepared for:** Lucid Hikari (0.12.12)
**Compared against:** VyseTV 1.0.0, commit `64bbd47`
**Scope:** what to borrow, what to ignore, and in what order
**Date:** 15 Nov 2026

---

## 0. Summary — the six things I would actually do

1. **Do not copy Vyse's components. Copy its discipline.** Vyse looks good because each screen has one job, one accent, one glow and no floating chrome. Hikari already has more component variety than Vyse; what it lacks is a budget for how many things may compete on one screen.
2. **Fix Home first.** Remove the donation banner from the scroll path, move the provider switch out of a floating pill over posters, drop the genre strip under the hero, and give Continue Watching a compact landscape card. That single pass will change perceived quality more than any new feature.
3. **Steal Live TV's information architecture, not its scraper.** Matches → Channels → Portals → Playlists is a better model than Quick Channels → Portal results → IPTV → Channel Wall. Hikari already has every data source needed to do it; it is an IA change, not a data change.
4. **Port three Live TV mechanics that Hikari genuinely lacks:**
   - byte-sniffing health (a 200 HTML error page is currently green),
   - EPG-driven exact-feed finding for sports (Hikari's `SportsMatcher` still says "Hikari has no EPG"),
   - feed-variant filters (`beIN 2 · AR · FHD`) instead of one tile per channel name.
5. **Give the player a "proof of quality" line** — format/quality badges, chapter or segment markers, "ends at 12:02 AM". It is cheap, and it is the thing that makes a player feel premium.
6. **Keep MultiView, YouTube and extension breadth as the signature.** Vyse cannot do MultiView. Competing on Vyse's watch-party server is not worth it; competing on "watch four games at once, from any playlist, with EPG" is.

---

## 1. Method

I read the Lucid Hikari tree (576 Kotlin files, 223k lines), its design docs (`docs/POSTER_STYLING.md`, `DETAIL_HEADER.md`, `PLAYER_PANELS.md`, `PERFORMANCE.md`), the theme and component layer, the IPTV stack (`iptv/`, `IptvScreen.kt`, `XtreamEpg.kt`, `StreamProbe.kt`, `SportsMatcher.kt`), and compared it with the VyseTV screens and the Live TV implementation documented in `vysetv-1.0.0-breakdown.md`.

Important context for the recommendations:

- Vyse is **desktop-first** (left rail, mouse hover, 16:9 everything). Hikari is **phone/TV-first**. Any layout idea has to be translated, not transplanted.
- Vyse is **Flutter**; Hikari is **Kotlin + Compose + Media3**. Only ideas and algorithms port directly; UI code does not.
- Both are **GPL-3.0**, so code porting is licence-compatible, but should still be attributed (see §11).

---

## 2. The honest comparison

| Capability | Lucid Hikari | VyseTV | Verdict |
|---|---|---|---|
| Extensions / sources breadth | CloudStream, Nuvio, Stremio, Hikari, Vega | 40 film sources + 10 anime + addons | **Hikari ahead** |
| Player engineering | Media3, dual-audio pairing, seamless autoplay, PiP, subtitle sync, SponsorBlock | mpv, HDR passthrough, shaders, libass, IntroDB | Different strengths; Hikari ahead on UX, Vyse ahead on format/HDR |
| Multi-window | MultiView up to 4 (phone) / 9-grid | none | **Hikari ahead — signature feature** |
| YouTube / Music / Telegram | built in | separate Music tab, no YouTube | **Hikari ahead** |
| Manga / reader | Aniyomi + Kotatsu + LNReader stacks | not present | **Hikari ahead** |
| Live TV portal acquisition | Cloud Vault + Reddit + paste.sh (ported) | same lineage | Parity |
| Live TV information architecture | playlist-first, four competing zones | fixture-first, four clean tabs | **Vyse ahead** |
| Live TV stream health | HTTP status only | byte-level video signature | **Vyse ahead** |
| Sports → exact feed | name/heuristic matching | EPG text scoring around kick-off | **Vyse ahead** |
| EPG UX | XMLTV now/next strip | per-channel short EPG + player strip | Different tradeoffs; Vyse better in-player |
| Visual system | 4 themes, accent, app font, 7 poster effects, 3 hero styles, detail header styles | one theme family, restrained | **Hikari has more; Vyse has better judgement** |
| Watch party | none | PocketBase + LiveKit | Vyse ahead, expensive to match |
| Downloads | resumable, manga chapters | file/HLS/torrent | Parity |
| Updates | GitHub releases | signed manifest + SHA-256 | **Vyse ahead on integrity** |

The pattern: **Hikari has more surface area; Vyse has more editorial control.** The report therefore focuses on control, not features.

---

## 3. What Vyse gets right that is portable

### 3.1 The "one job per zone" rule

Every Vyse page has a small number of zones, and nothing floats over them:

- **Home:** hero → spotlight shelf → popular shelf.
- **Discover:** poster wall + a filter rail. No hero, no banner, no chips over content.
- **Live TV:** tab row → day/sport row → match shelves.
- **Player:** art, then one baseline of controls.

Hikari's Home currently has, in the first two screens: header + tagline, hero carousel, dots, genre strip, donation card, Continue Watching with remove buttons, provider label per shelf, poster badges, a floating provider pill, and a five-item taskbar. That is not a component problem; it is a **zoning** problem.

**Rule to adopt:** one screen may have one primary action, one navigation affordance, and one floating element. Everything else belongs in scroll or in a sheet.

### 3.2 Content hierarchy as a contract

Vyse makes the path explicit: **catalogue → title → playable source → player**. Hikari has the same path but exposes configuration at every step (provider picker on Home, server picker, source switches, playlist switches). Vyse hides configuration behind one obvious control per page (Settings → Sources, the Portals tab).

**Rule to adopt:** configuration lives in one place per domain; browse screens show content, and a single "manage" entry point.

### 3.3 Proof of quality

Vyse tells you *why* what you are watching is good:

- player line: `4K · Knaben · 4K · HDR · HDR10 · DD+ · 5.1 · HEVC`
- exact feed: `beIN Sports 2 · AR`
- EPG: `NOW  Champions League 20:00–22:00` with a progress bar
- source sheet: `Miami · 4K · HLS · ready`, dead links dimmed

Hikari already collects this data (`formatSummary`, `Tracks`, codec names, quality badges, `TitleQuality`) but spreads it across panels. Consolidating it into a visible line is the cheapest perceived-quality upgrade available.

### 3.4 Consistency of states

Vyse uses one pattern each for loading (`Shimmer`/skeleton), empty (`icon + title + body + action`), and error (`status line + stop/retry`). Hikari has `EmptyState`, `ShimmerRow`, `GlassDialog`, `VerificationNudge` — good primitives — but screens compose them differently. A short "states" contract fixes that.

---

## 4. What Hikari already does better — protect these

- **MultiView.** Four live windows on a phone, nine on a tablet. This is the single most distinctive feature either app has. Do not trade it for a watch-party build.
- **Seamless autoplay.** Ten-second muted preview, pause, expand, same timestamp. Vyse has nothing comparable.
- **Extension breadth.** Five ecosystems in one library. Vyse's 40 scrapers are hardcoded and fragile; Hikari's plugin model is more durable.
- **Manga/reader.** Yomi/Kotatsu/Chimahon readers are years ahead of anything Vyse has.
- **Per-post-proof config.** Accent, app font, seven poster treatments, hero styles, detail header styles, player control slots. This is a real differentiator for power users — keep it, but contain it (see §5.7).
- **Settings search.** Vyse has no settings search. Keep and surface it.
- **Performance discipline.** `docs/PERFORMANCE.md` is better than most commercial apps. Any new visual effect must obey it.

---

## 5. Design system changes

The goal is not to look like Vyse. It is to look **inevitable** — one accent, one type scale, one spacing rhythm, one effect per surface.

### 5.1 Colour: one accent, four semantic greys

Hikari already has an accent system. Keep it, but formalise the semantic set so screens stop inventing colours:

| Token | Dark | Light | Use |
|---|---|---|---|
| `bg` | `#000000` (AMOLED) / `#0A0A0F` | `#F7F7FA` | page |
| `surface` | `#121218` | `#FFFFFF` | panels |
| `surfaceHi` | `#1B1B24` | `#EFEFF4` | raised panels |
| `stroke` | `white 10%` | `black 8%` | hairlines |
| `text` | `#F2F3F7` | `#121318` | primary |
| `textDim` | `#A7ABB8` | `#5C6070` | secondary |
| `textFaint` | `#6E7280` | `#8A8E9C` | tertiary |
| `accent` | user accent | user accent | one per screen |
| `accentHot` | `#FF6B4A` | — | destructive/live only |
| `star` | `#F5C451` | — | ratings only |

**Rules**

- Accent appears no more than 3 times per screen: one selected control, one progress/indicator, one link.
- Ratings are the only yellow. Live is the only red. Quality badges are white-on-black, never accent.
- No coloured provider names in shelf headers. Provider identity is metadata, not decoration.

### 5.2 Type: fewer sizes, tighter tracking, tabular numbers

Hikari's `Type.kt` defines seven styles but call sites add ad-hoc `fontSize = 11.sp/12.5.sp/13.sp/14.sp/15.sp`. Define a six-step scale and forbid inline sizes:

| Token | Size / line | Weight | Use |
|---|---|---|---|
| `display` | 30 / 34 | 800, -0.6 tracking | hero title only |
| `title` | 20 / 26 | 700, -0.3 | page titles |
| `heading` | 15 / 20 | 700 | shelf headers |
| `body` | 14 / 20 | 400 | descriptions |
| `label` | 12 / 16 | 600 | buttons, chips |
| `micro` | 10.5 / 14 | 700, uppercase, +0.6 | provider names, format badges |

- Use **tabular figures** (`FontFeatureSettings("tnum")`) for scores, timers, durations, and "ends at". This is one line of code and makes the player look professional.
- Use one bundled variable font (Manrope, Inter or Geist) at weights 400/600/700/800 instead of five families. Hikari already supports an app font; make the default a good one rather than `SansSerif`.
- Section header: 15sp/700 title, optional 11sp/600 uppercase subtitle at 55% opacity, no accent colour.

### 5.3 Spacing: an 8-point rhythm with two exceptions

| Token | dp |
|---|---|
| `xs` | 4 |
| `s` | 8 |
| `m` | 12 |
| `l` | 16 |
| `xl` | 24 |
| `xxl` | 32 |
| page gutter | 16 (phone), 24 (tablet), 32 (TV safe) |

- Vertical page sections: `xl` top, `l` bottom.
- Shelf header to first card: `s`.
- Card gap: `s` (portrait), `m` (landscape).
- Never mix 10/14/18/22 paddings; those are the numbers that make a layout feel hand-tuned rather than designed.

### 5.4 Radius and elevation

- Radius scale: **8** (chips/badges), **12** (cards, inputs), **16** (sheets), **22** (dialogs).
- On dark themes: **no drop shadows**. Depth comes from a 1px hairline plus a 2–4% white fill gradient — which Hikari's `Glass.kt` already does correctly.
- Reserve blur for overlays that sit *over video* (player panels, mini player). Do not blur static content panels; it costs GPU and reads as noise.

### 5.5 Motion: two durations, one curve

| Motion | Duration | Curve |
|---|---|---|
| state change (selection, fade, progress) | 180 ms | `easeOutCubic` |
| spatial change (sheet, page, expand) | 320 ms | `easeOutCubic` |
| press feedback | 90 ms | spring, low bounce |

- Stagger reveals only on **first load**, never on scroll.
- One animated element per viewport, maximum. Hikari's poster effects can animate `sheen` + `aura` + `tilt` simultaneously; that is the motion equivalent of three accents.
- Respect "reduce motion": disable sheen/aura/tilt, keep fades.

### 5.6 The chrome budget

Count the elements that float above content on a screen. Budget: **1**.

Current Home has at least two (the "All providers" pill and the taskbar overlap). Move the provider switch into the header as a pill next to the title (Vyse's Live TV does exactly this with its tab row), and let the taskbar own the bottom inset.

### 5.7 The one-effect rule (poster treatments)

Hikari's `PosterEffects` is excellent engineering and a power-user feature. The problem is the default: a set of seven treatments can all be on, and each draws its own layer.

Recommendation:

- Introduce **three presets** instead of a free-form set:
  - **Clean** — no effects, hairline, score badge. Default on phones.
  - **Cinematic** — `lit` + `spotlight` only, halos on hero and Continue Watching. Default on TV.
  - **Showcase** — the full set, for people who want it.
- Keep the advanced editor, but bury it under "Custom" so the default is a decision, not a pile.
- On TV, force `tilt` off and cap to `lit` — 3D tilt under d-pad focus reads as jitter.

This preserves the feature while making the out-of-box look coherent.

### 5.8 States contract

| State | Pattern |
|---|---|
| Loading first time | skeleton matching the final layout (Hikari has `ShimmerRow`; extend to grids) |
| Loading more | small inline spinner at the end, never a full-screen one |
| Empty | icon (24), title (15/700), one sentence (13/dim), one primary action |
| Error | same as empty, plus "Retry" and a secondary "Open settings" where relevant |
| Partial failure | a quiet status line above the content: "2 of 5 sources answered" + retry |

Copy rules (from Vyse, worth adopting verbatim as a style): short, specific, no exclamation marks, no marketing voice inside the app.

---

## 6. Screen-by-screen

### 6.1 Home — the highest-value redesign

**Problems visible today**

- The donation card sits between the hero and Continue Watching. It is the second thing a user sees, every launch.
- The genre strip consumes a full row immediately under the hero, before any content.
- Continue Watching cards overlay large title text on the still, show a permanent ✕, and have inconsistent metadata.
- Every shelf repeats its provider name in accent colour.
- The provider picker floats over posters as a pill and physically covers titles.
- The taskbar overlays the last shelf.

**Proposed structure**

```
┌──────────────────────────────────────────┐
│ Lucid Hikari            [provider ▾] [🔍]│   ← one header row, no tagline after first run
├──────────────────────────────────────────┤
│                                          │
│              HERO  (16:9)                │   ← title, meta, Play, +, info only
│                                          │
├──────────────────────────────────────────┤
│ Resume                              ›    │   ← 3–6 landscape cards, progress bar
│ ┌──────┐ ┌──────┐ ┌──────┐               │
├──────────────────────────────────────────┤
│ Popular Movies                      ›    │   ← no provider label; art + one-line title
│ ┌──┐┌──┐┌──┐┌──┐┌──┐                     │
├──────────────────────────────────────────┤
│ Continue with Anime                 ›    │
└──────────────────────────────────────────┘
```

**Specific changes**

| Change | Why |
|---|---|
| Donation banner → Settings → About, plus a one-time dismissible card after 7 days | It is the loudest non-content element on the most-visited screen |
| Genre strip → a single "Genres" chip that opens a sheet, or move to Discover | It is a filter pretending to be content |
| Provider pill → header pill with the provider's icon and name; tapping opens the existing picker sheet | Removes the only element floating over posters |
| Continue Watching: 16:9 card, 2-line max title at 13/600, thin 2dp progress bar, ✕ only in long-press menu | Matches the "resume" mental model; removes visual noise |
| Shelf header: 3dp accent bar + 15/700 title + optional `Show all ›` chevron | One header style for the whole app |
| Provider name: only when a shelf mixes providers | It is provenance, not a title |
| Bottom content padding = taskbar height + nav bar | Fixes content hidden behind the bar |
| Poster title: 12.5/500, one line, 6dp below art | Titles under posters are cleaner than overlays |

**Hero variants:** Hikari already has `HeroStyles` (carousel/spotlight/compact/showcase). Keep them; just make the default "spotlight" with **one** title, **one** primary action, and dots that are 3dp and 40% opacity. Vyse's hero works because it is quiet, not because it is cinematic.

### 6.2 Discover

Vyse's Discover is the best screen in either app: a true poster wall plus a right-hand filter rail with live counts.

- On tablets/TV: adopt the wall + rail (rail width ~300dp, collapsible).
- On phones: wall + a "Filters" pill that opens a full-height sheet with the same controls.
- Show the result count prominently: `40,002 titles`.
- Filters that matter: type, genre, year, rating, original language, streaming service, sort. Each chip shows whether it is active; the "Show N titles" button applies.
- Posters should bleed edge-to-edge with 8dp gutters; no card chrome, no badges unless the user enabled them.

### 6.3 Search

Hikari already streams results per source. Two additions:

1. **Paste anything.** If the query is a URL, magnet, `url:user:pass` portal login, `.m3u`, Stremio manifest, or CloudStream plugin link, show a single "Open this" card at the top instead of a text search. Vyse's search does this for streams/magnets; Hikari can do it for six more types because it already understands all of them.
2. **Source status chips.** Under the field: `NetMirror 12 · 0.8s`, `Nuvio 4 · 2.1s`, `Stremio —`. Tapping one filters results. This turns a spinner into an explanation.

### 6.4 Detail

Vyse's detail page has a useful idea Hikari lacks: a single **"Sources" block** that is honest about state.

- Group servers by provider with a provider icon, and show per-server: quality, format, audio language, and a state dot (`ready` / `trying` / `dead`).
- Dead servers stay visible but dimmed with a "Retry" affordance; hiding them makes the app look like it found fewer sources.
- Put the **Play** button's default target at the top of the list and remember the last successful server per title.
- Show `TMDB logo` as the title (Hikari already does this — good), and keep the mark strip.

### 6.5 Player

Hikari's player is functionally deeper than Vyse's. What it lacks is a **single line that explains the stream**.

Proposed bottom line (above the seek bar):

```
THE ODYSSEY   ·   4K · HDR10 · DD+ 5.1 · HEVC   ·   Chapter 9 · The Cave
```

- Left: title wordmark or title (12.5/700).
- Middle: format badges parsed from `Tracks`/`Format` (they already exist in `formatSummary`): resolution, HDR, audio codec/channels, video codec, container.
- Right: `1:17:48 / 2:52:35 · ends 12:02 AM` with tabular figures.
- Below: chapter/segment marks on the seek bar. This is the one thing Vyse has that Hikari does not, and it is visible in every screenshot.

**Chapter marks without a chapter database:** parse MP4 `chpl`/`tref` and Matroska `Chapters` on first play, cache per URL. If absent, fall back to IntroDB/AniSkip segments (see §8).

### 6.6 Live TV — see §7 (the structural change)

### 6.7 Sports

Hikari's Sports is already strong (follow teams, scores, standings, Watch on). Two upgrades:

- **Exact feed.** Replace "ESPN HD" with "ESPN HD · 2 feeds" and, once EPG is read, "beIN Sports 2 · AR". This is the single most "how did they do that" feature in Vyse.
- **Empty state that teaches.** When no channel matches, say which networks the fixture lists and offer "Search all playlists for ESPN" as an action, not a dead end.

### 6.8 Settings

- Keep search; add a **"What changed"** section at the top of the root (last 3 toggles touched) so people can undo experiments.
- Group by intent, not by implementation: Playback · Sources · Live TV · Appearance · Storage · Privacy · About. Hikari currently mixes "Playback & Servers", "App Layout", "Extensions" in ways that require reading.
- Every destructive action: confirm sheet with the consequence spelled out, not just "Are you sure?".

### 6.9 Music / Manga / Downloads

These pages already have their own strong identities. The only system change: apply the same section header, spacing scale, and empty-state pattern so moving between tabs feels like one app. Avoid adding Vyse-style landscape shelves here; posters and covers are the right shape.

---

## 7. Live TV deep dive — the five things worth porting

Vyse's Live TV is not better because it has more features. It is better because it answers four questions in order: **what is on, which channel has it, which of my sources carries that channel, and is that source alive**.

### 7.1 Restructure the page around the questions

Current Hikari Live TV opens on Quick Channels with portal results and playlists in the same scroll. Proposed:

| Tab | Content | Source of truth |
|---|---|---|
| **Matches** (default) | today's fixtures by league, live scores, "where to watch" | Hikari Sports + ESPN/Watch on |
| **Channels** | curated Channel Wall by category + search + feed filters | `ChannelWallChannels` |
| **Portals** | Xtream portals, health, expiry, generate, verify | `IptvScreen` portal section |
| **Playlists** | M3U by URL/file, groups, channel counts | `IptvScreen` playlist section |

This removes the "Quick Channels / CH Wall / Portal search results / IPTV playlists" stack from the critical path. Quick Channels becomes a **row inside Matches** ("Your quick channels") rather than a competing top-level zone.

**Effort:** M (mostly moving existing composables). **Impact:** 5/5.

### 7.2 Byte-sniffing health — a real bug fix

Today `StreamProbe.probe()` returns `ProbeResult(code, latency)` and `isAlive = code in 200..399`. A provider that answers `200 text/html` with "account expired" or a Cloudflare interstitial is shown **green**.

Vyse's checker (`IptvAliveChecker`) is the correct model. Port the classification into `StreamProbe`:

```kotlin
sealed interface Verdict {
    data object Live : Verdict
    data object Dead : Verdict
    data object NotVideo : Verdict
    data class Unknown(val code: Int) : Verdict
}

suspend fun probeVerdict(url: String, headers: Map<String, String>): Verdict {
    // GET with Range: bytes=0-65535, 8s timeout
    // reject text/html, application/json, text/xml, text/plain
    // reject content-length in 1..5_000_000 (placeholder heuristic)
    // HLS: first 1 KB contains "#EXTM3U"
    // MPEG-TS: byte[0] == 0x47 and every 188th byte == 0x47 for >= 3 packets
    // MP4: bytes 4..8 == "ftyp"
    // codecs: ADTS 0xFFFx, Matroska 1A45DFA3, Ogg "OggS", MPEG start codes
    // else: >= 32 KB of bytes is accepted
}
```

Why this matters: the health dot, the "Validate" action, and the Channel Wall's alive filtering all become trustworthy. A green dot that means "the server said hello with a web page" is worse than no dot.

Keep the existing 5-minute TTL and the headers-aware probe (that part is already better than Vyse's).

**Effort:** S (one file, reuse the sniffing already in `StreamProbe.follow()`). **Impact:** 4/5.

### 7.3 Feed variants, not channel names

A portal often carries `beIN SPORTS 1 AR`, `beIN SPORTS 1 FR`, `beIN SPORTS 2 AR`, `beIN SPORTS 2 FHD`. Today the wall shows them as separate rows with no way to narrow.

Port `HitTags` logic into a `FeedTags` parser and render **conditional filter rows**:

```kotlin
data class FeedTags(val number: String?, val lang: String?, val quality: String?)

// only show a row when more than one value exists across the current results
// CHANNEL:  1   2   3
// LANGUAGE: AR  EN  FR
// QUALITY:  4K  FHD HD
```

Details worth copying:

- Parse number from the first 1–2 digit token.
- Parse language from a fixed set (`AR, EN, FR, TR, ES, DE, IT, PT, NL, PL, RU, UK, US, CA, MENA, FA, UR, HI, IN, BR, MX, GR, RO, AL, SE, NO, DK, FI, KU, HE, ID, MY, PH, AF, BE, CH, AT, KR, JP`).
- Quality precedence: `4K/UHD` > `FHD/1080` > `HD` > `SD`.
- Sort favourites first, then exact-feed matches, then the rest.
- Favourite by **stream URL**, not channel name, so the same feed on two portals is two favourites.

**Effort:** S–M. **Impact:** 5/5 on channels with many variants.

### 7.4 EPG-driven exact-feed finding

Hikari now has `XtreamEpg` (XMLTV now/next) but `SportsMatcher` still says *"Hikari has no EPG"*. That comment is now false, and the upgrade is high-value.

Algorithm (Vyse's `MatchFinder`, adapted):

1. For the tapped fixture, get candidate channels from the existing `SportsMatcher`.
2. Group them by `number|language` so ten portals carrying "beIN 2 AR" cost one lookup.
3. For each group, fetch that channel's guide around kick-off. Two options:
   - `player_api.php?action=get_short_epg&stream_id=…&limit=4` (cheap, per channel, works on Xtream), or
   - the already-downloaded XMLTV snapshot if it covers the day (Hikari's `XtreamEpg` is now/next only; extend it with a "programmes around time T" query, capped).
4. Score each programme in the window **kick-off −30 min … +2h30**:
   - fold accents, lowercase, strip punctuation;
   - tokens must be ≥4 chars and not in a generic list (`united, city, real, club, sporting, athletic, atletico, inter, state, university, town, county, rovers, wanderers, olympique, racing, deportivo, sport, sports, team, new, saint`);
   - tokens ≥6 chars: substring; shorter: word boundary;
   - score = number of team sides mentioned (2 = both teams = strong).
5. Publish the result as `feedLabel` on the channel card: `beIN Sports 2 · AR`, and put matched feeds first.
6. Cache per `matchId|channelId` for the session.

This is a genuinely differentiating feature: "Tap the game, the app tells you which of your 20 beIN feeds is actually showing it."

**Effort:** M (250–400 lines including tests). **Impact:** 5/5.

### 7.5 A guide-shaped portal browser for tablets and TV

Vyse's portal browser is three panes: categories | channels with now/next | player. Hikari's IPTV page is a phone-first scroll with sheets.

- **Phone:** categories → channel list (with now/next rows) → player. Two back steps, not three.
- **Tablet/TV:** three panes, 240dp / flexible / 16:9 player. Focus moves left→right; selecting a channel plays in the pane without leaving the page.
- Channel rows: logo, name, `NOW · programme · 20:00–21:00`, progress bar, health dot.
- A "Favourites" pseudo-category at the top, plus "Working only" toggle.

The player pane must be created once and re-opened on channel change, not recreated, or the stream reconnects on every zap.

**Effort:** L (new screen, reuse `BrowserLivePlayer`-style component). **Impact:** 4/5 on TV, 3/5 on phone.

### 7.6 EPG in the live player

Currently the On-now strip lives on the IPTV page. Add it to the live player:

```
LIVE  beIN Sports 2 · AR
NOW   Champions League        20:00 – 22:00   [progress]
Next  Post-match              22:00
```

Plus a "Go to live" button after pausing (shows latency), and a CH− / CH+ pair that follows the filtered list order.

**Effort:** S. **Impact:** 4/5.

### 7.7 Request budgeting for guide data

Vyse's `GuideLoader` is worth reading for the policy, even if Hikari keeps XMLTV:

- only rows on screen request data;
- a row must be visible 220 ms before its request starts;
- LIFO queue (newest row first), max 3 concurrent;
- a request for a row that scrolled away is dropped;
- retry windows: empty 15 min, stale 5 min, "knows now but not next" 10 min;
- a 30 s clock tick redraws progress without fetching.

Hikari's XMLTV approach (stream-parse, 12 MB cap, now/next only) is actually better for large panels. The fix is to add **per-channel short EPG** for details/player, with the same concurrency and visibility gating, and keep XMLTV for the wall's "On now" strip.

**Effort:** S–M. **Impact:** 3/5 (protects panels from rate limits).

---

## 8. Player polish list

Ranked by perceived quality per hour of work.

| # | Item | Notes | Effort |
|---|---|---|---|
| 1 | Format/quality badge line | reuse `formatSummary`; white-on-black chips, 10.5sp, 3dp gaps | S |
| 2 | "Ends at 12:02 AM" | tabular figures; already have position/duration | S |
| 3 | Chapter or segment marks on seek bar | parse container chapters once; fall back to IntroDB/AniSkip | M |
| 4 | Auto skip-intro with a visible pill | Hikari has a manual SKIP control; wire it to IntroDB (`api.theintrodb.org/v4/media?tmdb_id=…`) and AniSkip for anime | M |
| 5 | Subtitle sync ± buttons in the subtitle sheet | the offset already exists; expose `−0.5s / +0.5s / Reset` with a live preview | S |
| 6 | Tap the source chip → server list | currently a separate control; make the badge row tappable | S |
| 7 | Double-tap seek with a ripple | left/right 10s; long-press 2× speed | S |
| 8 | Mini-player: snap above taskbar, close/expand/play-pause only | one row of controls, 16:9, 30% width max | M |
| 9 | Live: latency indicator + "Go to live" | after pause/seek in a live stream | S |
| 10 | Track language names, not codes | `eng → English`, `ara → Arabic`; Hikari already has i18n | S |

Avoid: adding more player controls to the configurable set before the default layout is clean. Vyse's bar works because it has five groups, not fifteen buttons.

---

## 9. Backlog with effort and impact

| # | Item | Area | Effort | Impact |
|---|---|---|---|---|
| 1 | Home zoning pass (banner, provider pill, genre strip, resume cards, insets) | Design | M | 5 |
| 2 | Section header + spacing + type tokens | Design | S | 4 |
| 3 | Poster effect presets (Clean / Cinematic / Showcase) | Design | S | 4 |
| 4 | Live TV IA: Matches · Channels · Portals · Playlists | Live TV | M | 5 |
| 5 | Byte-sniffing probe verdicts | Live TV | S | 4 |
| 6 | Feed variant parser + conditional filters | Live TV | M | 5 |
| 7 | EPG exact-feed finder for Sports | Live TV | M | 5 |
| 8 | Player info line (badges, chapter, ends-at) | Player | S | 4 |
| 9 | Chapter/segment marks + auto skip-intro | Player | M | 4 |
| 10 | Guide-shaped portal browser (tablet/TV) | Live TV | L | 4 |
| 11 | Paste-anything search card | Search | S | 3 |
| 12 | Source-state detail block | Detail | M | 3 |
| 13 | Discover filter rail/sheet | Discover | M | 4 |
| 14 | Settings: recent changes + intent grouping | Settings | S | 3 |
| 15 | Signed update manifest + SHA-256 | Trust | M | 3 |

Suggested order: **1 → 2 → 3 → 5 → 4 → 6 → 8 → 7 → 9 → 13 → 11 → 12 → 10 → 14 → 15.**

Rationale: the first three change how the whole app feels, 5 is a correctness fix, 4/6/7 are the Live TV story, 8/9 make the player feel premium. The rest are compounding polish.

---

## 10. Six-week plan

**Week 1 — Home and tokens**
- Add `Spacing`, `Radii`, `VyseColors` equivalents in `ui/theme`.
- Add one `SectionHeader` composable and replace ad-hoc headers.
- Home: remove donation banner from the feed, move provider switch into the header, collapse the genre strip behind a chip, redesign Continue Watching, fix bottom inset.

**Week 2 — Poster presets and states**
- Three presets with migration from the current effect set.
- Audit empty/loading/error on Home, Search, IPTV, Downloads; make them use one pattern.
- Reduce motion setting honoured by poster effects.

**Week 3 — Live TV correctness and IA**
- `probeVerdict()` with byte sniffing; wire to health dots, Validate, Channel Wall.
- Move Live TV to four tabs; Quick Channels becomes a row in Matches.
- Add a "Sources" count line per tab (e.g. `3 portals · 12,403 channels`).

**Week 4 — Live TV discovery**
- `FeedTags` parser + conditional filter rows.
- Favourites by stream URL.
- Channel rows with now/next and progress.

**Week 5 — Sports and guide**
- Exact-feed finder with EPG scoring and caching.
- Match cards show `beIN Sports 2 · AR`.
- Portal browser guide layout for tablets/TV (start with TV).

**Week 6 — Player**
- Info line (badges + chapter + ends-at).
- Chapter marks from container, IntroDB/AniSkip fallback.
- Subtitle sync buttons, source chip tap target, mini-player polish.

Every week ends with a profile run (`docs/PERFORMANCE.md` rules) and a TV focus pass.

---

## 11. What not to copy

### 11.1 Taste

- **Vyse's desktop layout.** Left icon rail, hover captions, 16:9 everything. Hikari is touch + d-pad; hover states must become focus states.
- **Vyse's sparse metadata on phones.** Vyse's cards assume a large screen and a mouse. Hikari's density controls are an advantage; keep them.
- **Vyse's 184 hardcoded channels.** Hikari already has its own `ChannelWallChannels`; merging two catalogues would create duplicates and disagreements. Improve the one you have with feed variants and health, not with more entries.
- **Vyse's watch party.** PocketBase + LiveKit + a relay is a second product. Hikari's MultiView and pairing already cover the social need locally.

### 11.2 Ethics and legal

- **Do not make portal generation automatic on first launch.** Vyse does (`IptvController.init()` → `scrape()` when empty). Hikari's `PortalGenerator` is user-triggered — keep it that way, keep it labelled, and keep it out of onboarding.
- **Do not ship Vyse's name, logo, screenshots, or channel art.** The GPL covers code, not trademarks or the curated art set.
- **Do not copy Vyse's Reddit OAuth client IDs or user-agent.** Vyse uses three borrowed IDs (`Slide`, `RedReader`, `Stealth`) and `PlayTorrio/1.3.6`. That is impersonation and will break or get those apps rate-limited. If Hikari keeps Reddit discovery, use its own registered app ID or the RSS path only.

### 11.3 Licensing

Both projects are **GPL-3.0**, so porting code is compatible. If you port more than an idea:

- keep the original copyright header,
- add the author to `README.md` credits,
- note the file's origin in a comment (`Ported from VyseTV, GPL-3.0`),
- do not relicense the combined work.

Algorithms, layout patterns and interaction ideas do not require this, but attribution is still the polite and defensible choice.

---

## 12. How to measure whether it worked

| Signal | Where | Target |
|---|---|---|
| Time to first playable frame | player logs | −15% after player pass |
| Channel tap → video | IPTV logs | > 80% of taps play within 4 s |
| Green-dot accuracy | spot-check 50 channels | > 95% of green dots actually play |
| Exact-feed hit rate | matches with a label / matches opened | > 60% for major leagues |
| Jank | `Chrome`/`FrameMetrics` | no new jank on Home scroll |
| Crash-free sessions | existing logs | no regression |
| D-pad traversal | manual TV pass | every screen reachable in ≤ 6 focus moves |

If a change cannot be measured, it is taste — which is fine, but it should not delay the measurable work.

---

## Appendix A — Vyse files worth reading

| File | Why |
|---|---|
| `lib/screens/live_tv_page.dart` | four-tab IA, tab counts, portal manager |
| `lib/screens/live_tv_pages.dart` | channel page, conditional feed filters, match guide panel |
| `lib/screens/portal_browser_page.dart` | three-pane guide layout, player key, alive checking |
| `lib/data/live_guide.dart` | `GuideLoader`: visibility gating, LIFO queue, retry windows |
| `lib/data/match_finder.dart` | EPG text scoring, grouping by number+language |
| `lib/services/iptv/iptv_network.dart` | `IptvAliveChecker` byte sniffing; `HitTags` equivalents |
| `lib/services/iptv/hardcoded_channels.dart` | keyword/exclude matching rules |
| `lib/screens/live_tv_player_page.dart` | live player chrome, EPG strip, channel drawer |
| `lib/data/sports_schedule.dart` | ESPN leagues, broadcaster aliases |
| `lib/core/theme.dart`, `lib/core/layout.dart` | spacing, glow, section header patterns |
| `lib/screens/matches_view.dart` | match card and "where to watch" grid |

## Appendix B — Hikari files to touch

| Area | File |
|---|---|
| Theme tokens | `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `Glass.kt` |
| Shared components | `ui/components/Components.kt` (header, empty state, hero, rows) |
| Poster effects | `ui/PosterStyle.kt`, `data/AppStore.kt` |
| Home | `ui/screens/HomeScreen.kt` |
| Live TV | `ui/screens/IptvScreen.kt`, `iptv/ChannelWall*.kt`, `iptv/IptvSearch.kt` |
| Probe | `net/StreamProbe.kt` |
| EPG | `data/XtreamEpg.kt` |
| Sports | `sports/SportsMatcher.kt`, `sports/SportsCatalog.kt`, `ui/screens/SportsScreen.kt` |
| Player | `player/PlayerActivity.kt`, `player/PlayerControls.kt`, `player/PlayerSkins.kt` |
| Detail | `ui/screens/DetailScreen.kt` |
| Search | `ui/screens/SearchScreen.kt`, `ui/navigation/AppNav.kt` |
| Settings | `ui/screens/SettingsScreen.kt` |

---

## Closing note

Vyse's best idea is not a visual style — it is the decision to make the app answer one question per screen, and to prove the answer with data the app already has (health, EPG, format, feed). Hikari already has more data and more capability than Vyse. Apply that discipline to Home, Live TV and the player, and the result will be better than either app, without copying a single file.
