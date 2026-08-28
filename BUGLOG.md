# BUGLOG — hostile QA pass (v1.10)

Audit driven by BUGFIX_QA_POWERAMP_POLISH_PROMPT + user device reports
("scanning forever", "app is really laggy", "lyrics background is fixed/wrong").
Every entry: symptom → root cause → fix. No new product features.

## P0 — Lyrics background fixed / wrong layer
- **Symptom:** With a track playing and lyrics open, the background is a solid/fixed
  layer; library chrome and controls can read through the player.
- **Cause:** The full player was a transparent fixed overlay (`background: transparent`
  from the fluid pass) over `#daddyamp-root` (the whole library UI). Nothing scoped
  the atmosphere to the player route, so behind the art stage/lyrics you could see
  nav, mini-player, headers and list rows — a "second, frozen" background. There was
  also no contrast scrim behind lyric lines.
- **Fix:** Added a **route-scoped living atmosphere** inside `#full-player-screen`
  (`#player-ambient-layer`: two crossfading `<img>` walls + scrim + tint + vignette).
  `setAmbientArt()` now swaps BOTH pairs (app wall + player wall) in lockstep in the
  same call, so track changes mid-lyrics crossfade both together — no frozen clone,
  no static screenshot, one atmosphere per route. Layering inside the player is now
  explicit: atmosphere(0) → glow/scrim(1) → stage(2) → glass deck(3) → header(4).
- **Lyrics scrim:** `#stage-lyrics-view::before` gradient (0.08–0.60 black) keeps white
  active lines ≥4.5:1 on bright art; the empty state reads "No lyrics for this track"
  on the SAME atmosphere. Safe-area bottom padding on the lyrics stream + deck.
- **Verified:** opening lyrics never remounts the route (stage switch is display-only),
  seek position and play state are untouched, and skip-in-lyrics swaps both walls.

## Scan "forever" + UI freeze while adding a folder
- **Symptom:** After picking a folder the app appears to scan forever and the UI lags.
- **Causes/fixes:**
  1. **No progress during unchanged walks** — incremental scans emitted only when a
     90-item batch filled; long unchanged trees looked frozen at "0 tracks".
     → Native **heartbeat** every ~1s (scanned/added/updated/currentPath) so the pill
     always ticks while the walker runs.
  2. **Tag reads were slow** — `MediaMetadataRetriever.setDataSource(context, uri)`
     re-resolved the content URI per key. → `openFileDescriptor` + `setDataSource(fd)`
     (fallback to URI), a large speed-up per file.
  3. **Every 90-file batch triggered an IndexedDB write + full state.songs rebuild +
     badge recompute** (O(N) per batch × hundreds of batches).
     → Merging is now **throttled to ~1/s** (`folderMergeSchedule`/`folderMergeNow`);
     the final flush is synchronous on `phase: done`.

## App "really laggy" (general 60fps/Lag)
- **`backdrop-filter: blur(28px)` on `#library-scroll-container`** — re-blurs the whole
  viewport on every scroll frame. → Removed (ambient art is already blurred by the
  img filter; scrim raised to 0.68). Backdrop blur remains only on small chrome
  (header, subnav, mini-player, nav, sheets, deck).
- **`mix-blend-mode: multiply` on the full-screen ambient tint** — forces extra
  compositing pass over the whole stack on mobile. → Plain alpha (`normal`, 0.5).
- **Player deck blur 26px → 18px**; ambient glow 0.6 → 0.45 (less overdraw).
- Scan merge throttling (above) also removes the mid-scan render storms.

## Palette stuck on folder-library tracks
- **Cause:** `extractCoverPalette` set `img.crossOrigin = "anonymous"` for ALL art;
  file:// art-cache URLs then fail CORS and `onload` never fires → accent never
  updates from folder-scan covers.
- **Fix:** crossOrigin set only for http(s) sources. Fallback accent `#7EC8E3`.

## Broken-cover icons (loud broken image on corrupt/missing art)
- **Fix:** global capture-phase `error` fallback — any artwork `<img>` that fails
  swaps once to `artwork/default.png`; ambient walls fall back per-pair instead of
  showing a broken front layer (stale-wall risk removed).

## Folder-first empty state
- The library empty state still led with "Import Files / Scan Device".
  → Primary CTA is now **Add music folder** (spec copy), Import/Scan demoted to
  secondary; sub copy updated.

## Polish per §4
- Now-playing row: accent edge (`inset 3px 0 0 var(--accent)`) + 3-bar EQ metering
  (compositor-only scaleY, static when paused, killed by reduce-motion).
- `player-audio-tech-chip` ellipsizes long "FLAC • 1411 kbps • 44.1 kHz" strings.
- Lyrics copy normalized to "No lyrics for this track".
- Safe-area bottom padding added to the player deck + lyrics stream (gesture bar).

## Audited, no action
- `.grain-overlay` (z 9999, opacity 0.025, pointer-events none) — intentional premium
  grain, above player/sheets; negligible cost.
- 25 remaining `backdrop-filter`s are thin chrome strips or open-sheet surfaces.
- Overlapping folder roots may index the same physical file twice (different tree
  URIs) — accepted (user's own root choice; Poweramp behaves the same).
- `heroArtExpand/Collapse` FLIP — transforms always cleared; no stuck cover state.

## Verification
131/131 smoke checks (jsdom + fake-indexeddb + bridge stubs) including:
player atmosphere swap, lyrics-as-layer (no seek/pause reset), skip-in-lyrics
atmosphere update, empty lyrics copy, no-backdrop-filter on the scroll container,
deck blur cap, tint blend mode, throttled scan merge, broken-art fallback, now-playing
row EQ bars. Full suite: folders persistence/restart, session restore, cue sheets,
tag write-back, media session, crossfade, playlists, stats, themes, history.

## v1.11 — verification pass (see FEATURE_AUDIT.md)

- P1 "Visualizer doesn't work": stale native Visualizer survived audio-session
  changes (fix: re-attach on session change + vizSessionId), JS had no watchdog for
  a dead capture (fix: 900ms watchdog → ambient fallback; never blank), no
  background stop (fix: visibilitychange), reduce-motion ignored by viz (fix: gate).
- window.onAudioError was fired by native but unhandled in JS → silent stall on a
  missing/corrupt file. Fix: toast "This file can't be played — skipping" + auto-skip
  (max 2 consecutive).
- Session restore only ran when folder roots existed → picker/device-scan libraries
  lost their playhead after kill. Fix: restore whenever a session + library exist.
- Palette could stay one track behind: file:// art blocked by crossOrigin (fixed in
  v1.10) and no fallback when a cover was missing or failed to decode. Fix:
  applyDefaultAccent on missing/onerror; artwork-less tracks no longer keep the
  previous track's accent.
- Folder-scan tracks had no stable `id` → now-playing row indicator/EQ metering
  didn't match folder songs. Fix: deterministic folderTrackId assigned at merge.
- Suite relocated to a persistent workspace path (/home/user/smoke/smoke.mjs) after
  /tmp was recycled; rebuilt with full A→L coverage + verify section; 108/108.
