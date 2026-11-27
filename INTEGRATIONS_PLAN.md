# Custom Integrations Integration Plan

## Overview

This document outlines the plan to integrate the **Custom Integrations** (Custom metadata tweaks) settings from the upstream Hikari repository (`codegeasse1/hikari`) into Lucid Hikari without breaking existing features.

## Current State

### Lucid Hikari (Your Fork)
- **Version**: 0.15.5
- **Settings → Integrations** has basic structure:
  - `INTEGRATIONS` folder with sub-folders
  - `INTEGRATIONS_TMDB` - TMDB enrichment settings
  - `INTEGRATIONS_MDBLIST` - MDBList ratings settings
  - `INTEGRATIONS_ANIME` - Simplified card saying "All titles come from TMDB"
- **Missing**: Full metadata source selection, AioMetadata support, manga metadata, native provider toggles

### Upstream Hikari Features
The upstream has a comprehensive **Custom metadata tweaks** page with:

1. **Anime Metadata Sources**
   - Simkl toggle + optional app ID
   - AniList toggle
   - TMDB toggle
   - AioMetadata toggle + endpoint URL

2. **Movies & Series Metadata**
   - TMDB toggle
   - AioMetadata toggle + endpoint URL

3. **Metadata Addons**
   - List of installed Stremio addons with metadata capability detection
   - Per-addon enable/disable toggles
   - Priority order follows Extensions order

4. **Extension Metadata**
   - "Apply external metadata to" toggles per extension family
   - Native provider toggles (sources whose own metadata is better)

5. **Manga Metadata**
   - AniList toggle (manhwa, manhua, manga blurbs, genres, covers)
   - Adult manhwa (PornHwaDB) toggle + API key validation

6. **TMDB Modules Floating Window**
   - Granular control over which TMDB enrichments are requested
   - Shared dialog opened from Custom metadata tweaks

## Files to Compare/Update

### Core Settings Files
| File | Upstream Lines | Your Version | Action |
|------|---------------|--------------|--------|
| `app/src/main/java/com/hikari/app/ui/screens/SettingsScreen.kt` | ~9382 lines | ~9000 lines | Merge `AnimeMetadataIntegrationCard` |
| `app/src/main/java/com/hikari/app/data/AppStore.kt` | ~4611 lines | ~5615 lines | Add missing preferences + flows |

### Additional Dependencies
| File/Component | Purpose | Status in Lucid |
|----------------|---------|-----------------|
| `AioMetadata.kt` | AioMetadata API client | ❓ Need to check |
| `MangaMetadataRepository.kt` | AniList + PornHwaDB metadata | ❓ Need to check |
| `MetaSources.kt` | Extension family definitions | ❓ Need to check |
| `PornHwaDB` integration | Adult manhwa metadata | ❓ Need to check |

## Implementation Phases

### Phase 1: Assessment (No Code Changes)
- [x] Check if `AioMetadata.kt` exists in Lucid Hikari — **❌ MISSING**
- [x] Check if `MangaMetadataRepository.kt` exists — **❌ MISSING**
- [x] Check if `MetaSources.kt` exists — **❌ MISSING**
- [x] Check if `PornHwaDB` integration exists — **❌ MISSING**
- [x] Verify `AppStore.kt` has all required preference keys — **❌ MISSING (0 AIO refs)**
- [x] Compare `AnimeMetadataIntegrationCard` implementations — **Your version is a stub**

**Assessment Complete**: Your fork is missing all 4 supporting files. The upstream `AnimeMetadataIntegrationCard` is ~400 lines of UI that depends on these files. This is a full feature integration, not a settings tweak.

### Phase 2: AppStore.kt Updates
Add missing preference keys and flows:

```kotlin
// Anime metadata sources
ANIME_SIMKL, ANIME_ANILIST, ANIME_TMDB, ANIME_AIO
ANIME_SIMKL_APP_ID

// Video (movies/series) metadata
VIDEO_TMDB, VIDEO_AIO

// AioMetadata configuration
AIO_BASE_URL, AIO_API_KEY

// Manga metadata
MANGA_ANILIST, ADULT_MANHWA, PORNHWA_API_KEY

// Extension metadata
NATIVE_PROVIDERS (stringSetPreferencesKey)
META_FAMILY_EXTERNAL (stringSetPreferencesKey)

// Corresponding flows and setters for each
```

### Phase 3: SettingsScreen.kt Updates
Replace the simplified `AnimeMetadataIntegrationCard` with the full upstream version:

1. **Import missing components**:
   - `AioMetadata` resolver/client
   - `MangaMetadataRepository` for key validation
   - `MetaSources` for extension family definitions
   - `ProviderType.STREMIO` for addon detection

2. **Update `AnimeMetadataIntegrationCard`**:
   - Add all metadata source toggles
   - Add AioMetadata endpoint editor
   - Add Simkl app ID editor
   - Add manga metadata section
   - Add PornHwaDB API key with validation
   - Add native provider toggle list
   - Add "Apply external metadata to" family toggles
   - Add TMDB modules floating window button

3. **Update search keywords** for Settings search to include new options

### Phase 4: Integration Testing
Test each metadata source independently:

1. **TMDB only** (current default) - ensure existing behavior unchanged
2. **AioMetadata** - test endpoint validation, metadata fetching
3. **Simkl** - test with/without app ID
4. **AniList** - test anime + manga metadata
5. **PornHwaDB** - test API key validation, adult title enrichment
6. **Native providers** - test extension metadata override
7. **Combination** - test priority order (Aio → Simkl → AniList → TMDB)

### Phase 5: UI/UX Polish
- Ensure Material 3 styling matches Lucid Hikari theme
- Test on phone + TV layouts
- Verify translations work with new strings
- Add tutorial hints for first-time users

## Risk Assessment

### Low Risk (Safe to Add)
- ✅ TMDB module toggles (already partially implemented)
- ✅ MDBList provider toggles (already present)
- ✅ Native provider toggles (just on/off switches)
- ✅ Extension family metadata toggles

### Medium Risk (Test Thoroughly)
- ⚠️ AioMetadata integration (requires endpoint configuration)
- ⚠️ Simkl app ID handling (optional, with fallback)
- ⚠️ Manga AniList metadata (needs parallel fetching)

### Higher Risk (Requires Careful Testing)
- ⚠️ PornHwaDB API integration (external service, key validation)
- ⚠️ Metadata priority merging (first-hit-wins logic)
- ⚠️ Stremio addon metadata detection (reflection/casting)

## Compatibility Notes

### What Won't Break
- Existing TMDB enrichment (your current default)
- MDBList ratings (separate toggle)
- Current extension system (native toggles are additive)
- Watch history / trackers (independent system)
- Player, downloads, IPTV, YouTube (unrelated modules)

### What Needs Testing
- Detail page loading speed (parallel metadata fetching)
- Episode list population (multiple sources)
- Search result metadata (which source wins)
- Memory usage (multiple metadata clients active)

## Code Locations to Review

### Upstream References
```
# Full AnimeMetadataIntegrationCard
git show upstream/main:app/src/main/java/com/hikari/app/ui/screens/SettingsScreen.kt | sed -n '6524,6900p'

# AppStore preferences
git show upstream/main:app/src/main/java/com/hikari/app/data/AppStore.kt | grep -A2 "ANIME_\|VIDEO_\|AIO_\|MANGA_\|ADULT_\|PORNHWA_\|NATIVE_\|META_FAMILY"

# AioMetadata client
find upstream -name "AioMetadata.kt" -o -name "*AioMetadata*"

# Manga metadata repository
find upstream -name "MangaMetadataRepository.kt"
```

### Your Current Files
```
# Current simplified card
sed -n '7456,7465p' app/src/main/java/com/hikari/app/ui/screens/SettingsScreen.kt

# AppStore (check for existing keys)
grep -n "anime\|anilist\|aio\|manga\|pornhwa\|native" app/src/main/java/com/hikari/app/data/AppStore.kt
```

## Next Steps

1. **Review this plan** and approve/reject/modify
2. **Run assessment commands** to check existing implementations
3. **Create backup branch** before any changes
4. **Implement in phases** with testing after each phase
5. **Test on real device** with various metadata configurations

## Questions to Consider

1. Do you want **all** upstream metadata sources, or only specific ones?
2. Should AioMetadata be included (requires external service setup)?
3. Do you want PornHwaDB adult metadata (requires API key)?
4. Should the priority order be configurable, or fixed as upstream?
5. Any Lucid-specific branding changes needed for the UI text?

---

# Addendum: Other Upstream Code Worth Porting

Second pass over `upstream/main` (`93a24320`, v0.12.120) looking for anything **beyond** the Integrations page that is safe to bring over.

Ground rule used for every entry: **adding a new file is always safe** — nothing references it, so nothing can break. The real question is whether *wiring it in* touches code Lucid rewrote. So each item is rated on its wiring cost, not its file.

---

## Tier 1 — Adopt now (zero or one-line build change)

### 1. `app/src/main/baseline-prof.txt` — 52 lines
- **What**: hand-authored baseline profile (startup + first-scroll AOT list).
- **Deps**: none. Gradle reads it from `src/main/` automatically; `profileinstaller` comes in transitively.
- **Current state**: absent from Lucid (`find` returns nothing).
- **Risk**: **none.** Unmatched rules are ignored at install. Worst case it's a no-op.
- **Gain**: real. Sideloaded installs get no Play cloud profiles, so this is the only source of AOT compilation for cold start.
- **Verdict**: **add.**

### 2. `com/lagradost/cloudstream3/plugins/Plugin.kt` shadow — 63 lines + 1 build line
- **What**: a local `@Stable`-annotated copy of the CS3 `Plugin` class, paired with excluding the jar's original from the merged jar.
- **What it fixes**: newer CloudStream plugins compiled by a newer Kotlin emit `$stable` fields; loading them against the old jar class throws `NoSuchFieldError: $stable` and the plugin fails to load entirely.
- **Current state**: `app/libs/cloudstream3.jar` **does** contain `plugins/Plugin.class`, and Lucid's `cloudstreamJarClean` task excludes R / WebViewResolver / CloudflareKiller / CloudStreamApp but **not** `plugins/Plugin.class`. The shadow file does not exist.
- **Risk**: **low.** Only the plugin classloader path changes. `Cs3PluginManager` already walks the cause chain on `LinkageError`, so if anything regresses it shows up in Logs instead of silently breaking.
- **Verdict**: **add.** This is a bug fix for plugin compatibility, not a new feature.

---

## Tier 2 — Adopt, one wiring point each (low risk)

### 3. `data/TvdbArt.kt` — 263 lines
- **What**: keyless TVDB artwork fallback (poster + wide banner + English overview + year + companies), with disk cache.
- **Deps verified present in Lucid**: `TmdbResolver.Resolved`, `Http.postStringQuiet`, `TmdbMeta.queryVariants`, `MediaItem.searchTitle` / `allTitles`. All exist.
- **Wiring**: one extra leg inside `TmdbMeta.artwork()`. Lucid's `artwork()` currently returns early and falls back to `imdbArtwork()`; upstream instead tries TMDB → TVDB → IMDb. Only call site is `ui/Artwork.kt:259`.
- **Risk**: **low.** Artwork is best-effort and already wrapped in `runCatching` at the call site. If TVDB is down, the existing fallbacks still run.
- **Gain**: better posters/banners for titles TMDB misses, especially older series and anime.
- **Verdict**: **add**, after the Integrations work or independently.

### 4. `cs3/Cs3MangaProvider.kt` — 242 lines
- **What**: exposes CloudStream `.cs3` plugins that are actually manga sources as Hikari manga providers, using the existing `MangaProvider` bridge.
- **Deps verified present**: `com.hikari.app.manga.MangaProvider`, `ContentProvider`, `ProviderConfig`, `CatalogRef`, `StreamSource`.
- **Wiring**: three places upstream — `ProviderManager` MANGA branch, `Cs3ProviderSync` (manga prefix + type assignment), and a `MANGA_PREFIX` convention. Upstream's diff vs Lucid is only ~81 lines on `Cs3ProviderSync`.
- **Risk**: **low-medium.** Both files exist in Lucid and are small; the fork's `ProviderManager` MANGA branch just gains one more `else if`. The manga detail/reader screens already consume generic `ContentProvider`, so no reader work.
- **Gain**: a whole class of CS3 manga extensions becomes usable. Currently they either don't register or land as video providers with broken detail pages.
- **Verdict**: **add** — highest value-per-line on this list.

### 5. `ui/components/LangTag.kt` — 44 lines
- **What**: the little language chip (`EN`, `HI`, `TA`…) + `shortLangCode()`.
- **Deps verified present**: `ui/SourceLangs.kt` with `SourceLang` and `classifyProvider()`.
- **Wiring**: render it on extension rows in `ExtensionsScreen` (Lucid currently has a flag helper but no chip composable).
- **Risk**: **none while unwired**; purely additive when wired.
- **Verdict**: **add if you want the chip**; skip if the current flag badge is enough.

### 6. `ui/components/TelegramDialog.kt` — 103 lines
- **What**: the "join our Telegram" prompt dialog.
- **Deps verified present**: `ui/ExternalLinks.kt` already has `TELEGRAM_CHANNEL_URL`, `REDDIT_COMMUNITY_URL`, `DISCORD_INVITE_URL`, `openTelegram`, `openCommunity`.
- **Wiring**: none required — add the file, call it from wherever you want a community prompt (Lucid has no such prompt today).
- **Risk**: **none.**
- **Verdict**: **optional** — only if you want the nudge. Cosmetic.

---

## Tier 3 — Worth it, but real integration work

### 7. `reader/MangaTranslate.kt` (2,231 lines) + `reader/NovelTranslate.kt` (125 lines)
- **What**: on-device ML Kit OCR + translation overlaid on manga pages and novel text, plus per-series overrides.
- **Deps missing**: 7 ML Kit artifacts in `app/build.gradle.kts` (`text-recognition`, `-japanese`, `-korean`, `-chinese`, `-devanagari`, `translate`, `language-id`). Also reaches into `TranslationOverlayView`.
- **Wiring**: ~4 touch points — `PagerPageHolder`, `WebtoonPageHolder`, `MangaReaderScreen`, reader settings UI. Lucid's page holders have diverged (184-line diff on the webtoon one), so this is a manual port, not a copy.
- **Risk**: **high.** APK grows, page holders are performance-sensitive, and a failed first-use model download leaves a dead overlay unless the retry/backoff logic is ported intact.
- **Verdict**: **not now.** Big feature, big surface. Flag it as its own project if you want it.

### 8. `download/BatchServerPlanner.kt` — 205 lines
- **What**: pure function that picks which server to use per episode for a batch download, given the sources found.
- **Deps verified present**: only `data/StreamSource`.
- **Wiring**: Lucid already has its own batch flow (`serversForBatch` + 3-pass retry in `DetailScreen.kt`). Adopting the planner means **replacing** that logic, not adding to it.
- **Risk**: **medium** — batch download is a working feature; swapping its server-selection strategy without a device test could regress queue success rates.
- **Verdict**: **skip unless batch downloads are currently disappointing.** Lucid's version already does partial-result retention, which is the main thing the planner buys.

### 9. `data/AddonMetaDetails.kt` — 392 lines
- **What**: pure `org.json` parser for Stremio addon `meta` responses.
- **Wiring**: only useful once `MetaSources` exists — i.e. it is a **dependency of the Integrations work**, not a standalone add.
- **Verdict**: **fold into Integrations Phase 3**, don't schedule separately.

### 10. `ui/screens/GenresOverlay.kt` — 248 lines
- **What**: full-screen genre browser with image cards.
- **Deps present**: uses `TmdbMeta` genre endpoints.
- **Wiring**: replaces `HomeGenreStrip` (`HomeScreen.kt:4147`) or opens from it.
- **Risk**: **low-medium** — purely visual, but a home-screen rewrite of the genre entry point.
- **Verdict**: **optional / cosmetic.** Only if you want the richer browser.

---

## Tier 4 — Deliberately skipping

| Upstream code | Size | Why skip |
|---|---|---|
| `dub/` (AI dubbing) | 29 files, ~10.3k lines | Needs `sherpa-onnx.aar` + Piper voice packs fetched in CI, `commons-compress`, and a whole voice-model download UI. Not a port, a subsystem. |
| upstream `music/` | 19 files | Lucid has its own music stack (`MusicPlayer`, `MusicScreen`, Deezer/Qobuz resolvers) on ExoPlayer. Upstream's is a different architecture — porting would collide on every file name. |
| upstream `iptv/` + `LiveTv*` screens | many | Lucid has its own IPTV stack. Same collision problem. |
| `player/PlayerUiStyle.kt`, `ServerStreamsPage.kt` | — | Lucid has its own `PlayerSkins.kt` and player layout system. |
| `tv/TvHomeStage.kt` | 829 lines | **Unused upstream** (zero references). Dead code. |
| `ui/screens/SearchTv.kt` | 193 lines | **Unused upstream** (zero references). Dead code. |
| `ui/components/ScrollAnim.kt` | 76 lines | **Unused upstream.** Fork already has its own scroll effects under `ui/effects/`. |
| `ui/PosterSizing.kt` + `ui/screens/PosterCardsScreen.kt` | 115 + 281 | Duplicates Lucid's `ui/PosterStyle.kt` / `PosterStyleCard` (blur, corner, badges, glass, effects). Would give two competing poster settings. |
| `ui/HeaderPrefs.kt` | 50 lines | Duplicates Lucid's existing Featured-banner header size (0.5–1.5 scale in `heroFrame`, `Components.kt`). |
| `cs3/Cs3NewPipe.kt` | 78 lines | Lucid already initialises NewPipe lazily in `PlayableResolver.ensureReady()`. Would be a second init path. |
| `music/potoken/*` + `assets/po_token.html` | — | Tied to upstream's YouTube music stack; Lucid's YouTube engine is separate. |

---

## Recommended order if you approve everything

1. **Baseline profile** — 1 file, zero risk, do it first.
2. **Plugin.kt shadow + jar exclude** — 1 file + 1 build line, fixes a real plugin crash.
3. **Integrations Phase 2 → Phase 3** (AppStore keys, then the settings page) — the actual request.
4. **`Cs3MangaProvider` + `Cs3ProviderSync` manga branch + `ProviderManager` line** — unlocks CS3 manga extensions.
5. **`TvdbArt` → `TmdbMeta.artwork()` leg** — better artwork coverage.
6. *(Optional)* `LangTag`, `TelegramDialog`, `GenresOverlay` — cosmetic.
7. *(Separate project)* `MangaTranslate` + `NovelTranslate`.

Nothing above is coded yet.

---

**Status**: Plan + addendum complete, awaiting your approval. Tell me which tiers you want and I'll create a backup branch and start at the top of the order.
