# Bugs killed

Build: **v1.13** (versionCode 14) · Suite: `/home/user/smoke/smoke.mjs` → **163/163**

---

## P0 — false "can't play" + frozen wavebar (user-reported)

**Symptom:** tap play → "can't play the song" toast **while the song audibly plays**; wavebar
frozen at 0 / not tracking; pause→unpause "unlocks" it (toast clears, bar moves).

- **Cause (root):** `window.onAudioError` was trusted unconditionally. On Android,
  `playAudio()` does `mediaPlayer.reset()` → `setDataSource` → `prepareAsync()` on a single
  MediaPlayer, so `onError` can and does arrive **stale** — from the previous source, a reset
  race, or an aborted transition — while the *current* load goes on to play fine. Every such
  stale error did three harmful things:
  1. `displayToast("Can't play…")` — the lie.
  2. `state.isPlaying = false` — which froze the wavebar, because
     `syncPlaybackPosition()` (Android) and the `timeupdate` handler (web) both bail when
     `!state.isPlaying`. Sound kept playing; the UI stopped tracking → "frozen wavebar".
  3. Pause→unpause worked as a "fix" only because `resumeAudio()`/`play()` clears the error
     state and `togglePlayPauseState` re-set `isPlaying = true` — that's the secret handshake.
- **Secondary causes killed:**
  - Web path had **no** `audioEl` `error` listener at all, and `play().catch(() => {})`
    swallowed *every* rejection silently — either path produced a lying UI.
  - `playAudio` returning `false` (sync `setDataSource`/`prepareAsync` throw) was ignored.
  - Stale palettes/lyrics/seek from an aborted load were already generation-guarded (v1.12)
    — errors were the one event still bypassing the generation token.

- **Fix:** a **verified-error engine** — one verdict per `playGen`:
  1. `loadTrack` bumps `playGen`, clears any pending error verification, and nulls
     `audioReadyGen`.
  2. Every error signal (`onAudioError`, web `audioEl` `error` event, web `play()` rejection,
     sync `playAudio() === false`) funnels into `scheduleVerifiedAudioError(gen)` — deduped
     per generation, debounced **650 ms** so the load can actually settle/prepare.
  3. The verdict only fires if **all** are true: `gen` is still current (user hasn't moved
     on), **nothing is audible** (`AndroidBridge.isPlaying()` — raw MediaPlayer truth — or
     the web element state), and the load **never prepared** (`audioReadyGen !== gen`, unless
     the error arrived after prepare — that path only toasts if still silent).
  4. `onAudioPrepared` sets `audioReadyGen = playGen` and calls `hideErrorToast()` — a
     prepared load always wins over any stale error.
  5. `startPlayback` calls `hideErrorToast()` on every fresh play effort; on genuine failure
     it stops the UI **and** `saveSession(true)` so a force-kill restore can never autoplay a
     dead track.
- **Pause/unpause still required?** **NO.** Proven by suite:
  `wavebar tracks position while playing`, `wavebar keeps moving (no second-tap unlock)`,
  `stale error during load: no toast / still playing same track / no rogue play`,
  `stale error after prepare: no toast / playing state intact`,
  `clean reload dismisses error toast`.

---

## Other

| Bug | Cause | Fix |
|---|---|---|
| Play button faked "playing" with **no track selected** | `togglePlayPauseState` never checked for a current song | Guard: stop audio, `isPlaying=false`, icon reset, toast "No track selected" (`empty play never fakes playing`) |
| Web `play()` rejection completely swallowed | `catch(() => {})` | Routed into the verified-error engine — real failures now surface honestly, abort-style interruptions don't |
| Scan-completion toasts could still spam on every rescan | done-handler toast policy | Already inline-only (v1.12); re-verified: no sticky banner, no "Reading music" anywhere (`no sticky scan banner element`) |
| Rapid taps could leave a late error from tap 1 | errors not generation-bound | `rapid taps: last tap wins`, `rapid taps: late error from tap1 ignored` |
| Deep-library taps (beyond row 80) untested post-virtualization | test gap | `150+ library renders rows`, `150+ tap row = that exact track`, `150+ scroll streams remaining rows`, `150+ deep row tap = that exact track` |
| Repeat-one / shuffle interplay with identity untested | test gap | `repeat-one replays same id`, `shuffle never reorders library` |
| Verified error left session saying `playing:true` | error path skipped save | `verified error saves paused session` |

**Sweep results (pass, no change needed):** tap = that file (2 songs + 150+), no rogue
skip/swap/auto-play, play/pause icon = actual audio, seek = playhead (no jump-to-0 on
lyrics/library), repeat/shuffle identity-safe, mini-player same id as full player, single
audio path (one MediaPlayer / one `audioEl` — no overlapping players), scan background-only,
folders persist, lyrics atmosphere layer intact (no pause/reset), palette matches current
track by generation guard, no white flash/debug leftovers, no crash on skip spam/lyrics/rotate.

## Still open
- none (required) — all P0 items + §3 checklist items pass or were fixed; suite 163/163
  (run: `cd /home/user/smoke && node smoke.mjs`; deps: `npm i jsdom fake-indexeddb`).
- On-device confirmation (recommended, not blocking): install v1.13, play a known-good file
  → no toast, wavebar runs within ~300 ms; skip A→B clean; pause/unpause changes nothing
  but pause/resume; a broken file toasts only once.

## v1.15 — startup guard + lag/collision cleanup

- Added a boot-level JavaScript error/rejection guard so a startup exception is logged through the Android bridge and surfaced without leaving a dead blank WebView.
- Removed the full-viewport `backdrop-filter` from the main scrolling library container. This was re-blurring the entire catalog on every scroll frame and contradicted the earlier lag-fix note.
- Reduced default ambient blur cost: medium 48px → 32px, high 72px → 48px, low 24px → 18px.
- Reduced Android playback polling from 10Hz to 4Hz and throttled native duration bridge calls to once per second while preserving progress/lyrics updates.
- Replaced the 100k-bucket folder track id hash with a safe 53-bit stable id. The old hash collided heavily in large libraries and could mark/play/menu the wrong row.
- Native imports now use stable URI-derived ids instead of time/random ids, preventing duplicate identities after re-import/restart.
- Folder scanner progress emission no longer depends on a shared active-root field, so concurrent/root-overlapping scans cannot report progress against the wrong root.
- Rebuilt signed APK as versionCode 16 / versionName 1.15.

## v1.16 — crash-after-play + startup lag + Android volume sync

- Playback stability: Android AudioFX (Equalizer/BassBoost/Virtualizer) is no longer attached eagerly on every song prepare. It now attaches lazily only when DSP controls are used, avoiding vendor MediaPlayer/AudioEffect crashes that can happen seconds after playback starts.
- Startup lag: removed the automatic `loadTrack(..., false)` boot path. Startup now hydrates the mini/full player cheaply without fetching lyrics, extracting palette, updating media session, or touching native playback.
- Startup lag: folder auto-rescan is deferred 8 seconds after folder DB/session restore so the WebView becomes interactive first.
- Performance: Android playback polling remains lighter, with duration calls throttled.
- Volume sync: added native STREAM_MUSIC volume observer and JS bridge. Hardware volume buttons now update the DaddyAmp volume slider/icon. The in-app slider now controls the same Android media volume instead of a disconnected per-player value.
- Rebuilt signed APK as versionCode 17 / versionName 1.16.

## v1.17 — 10k library persistence + real folder fast scan

- Fixed large scanned libraries disappearing after restart: device scan results now persist in IndexedDB (`deviceTracks`) instead of overflowing localStorage. localStorage now stores only small manual imports/settings.
- Fixed folder scans saying complete but not appearing reliably: folder tracks are persisted through a cleared/rebuilt IndexedDB store at scan completion, and loaded back into the library on boot.
- Added a MediaStore-only fast path for full folder scans when the SAF folder maps to device storage. 10k+ indexed songs are now built from one MediaStore query in large batches instead of centuries of SAF walking/retriever opens.
- Fixed virtualized library only showing the first handful/chunk on some WebViews: rows now mount before observer registration, initial chunk is larger, and a scroll fallback always pumps more rows.
- Fixed false playing animation on app open: rows only show the playing/equalizer animation when audio is actually playing, not just because currentIndex points at a remembered song.
- Reset now clears the large IndexedDB catalogs too.
- Rebuilt signed APK as versionCode 18 / versionName 1.17.

## v1.18 — Poweramp seamless refinement pass

- Added a Poweramp-style refinement map documenting what is strong, what still needs work, and the next refinement queue.
- Startup/session restore refinement: non-playing restored sessions now hydrate UI cheaply instead of running full `loadTrack()` and causing lyrics/palette/native work on boot.
- Fixed cold-boot play button: pressing play on a visually hydrated song now loads that track instead of trying to resume an empty native MediaPlayer.
- Added tappable player audio-info chip cycling between format, time/library count, artist/album, and filename.
- Added long-press context menu behavior for catalog rows and full-player artwork.
- Pull-to-refresh now refreshes persistent folder roots plus Android MediaStore, so refreshed music remains after restart.
- Added calmer Poweramp-like touch feedback and guarded LRCLIB fetch for WebViews/test environments without fetch.
- Rebuilt signed APK as versionCode 19 / versionName 1.18.

## v1.19 — premium mini-player overhaul

- Redesigned the mini-player from a flat generated-looking bar into a floating glass control deck above the bottom navigation.
- Added subtle album-art tinting behind the mini-player via the current cover artwork, connected to the existing ambient art system.
- Added mini-player drag handle, state/time kicker, restrained playing LED, and a five-bar micro meter that animates only during real playback.
- Improved thumb ergonomics and visual hierarchy for favorite, play/pause, and next controls.
- Added swipe-up on the mini-player to expand Now Playing, while preserving horizontal swipe previous/next.
- Added safer mini progress/time updates and calmer press feedback to reduce the AI-generated/clunky feel.
- Rebuilt signed APK as versionCode 20 / versionName 1.19.

## v1.20 — mini-player collision fix / designer layout pass

- Fixed mini-player time text colliding into the heart/actions area.
- Reworked the mini-player into strict zones: fixed artwork, flexible clipped metadata, optional meter, fixed action rail.
- Changed mini time display to compact elapsed-only while playing; full elapsed/duration stays in Now Playing where there is enough room.
- Added responsive rules: on narrow phones the decorative meter and then heart yield before text/control collision can happen.
- Tightened typography, overflow, and ellipsis rules so long track names/artists/time cannot jumble the card.
- Rebuilt signed APK as versionCode 21 / versionName 1.20.

## v1.21 — Poweramp designer plan + mini-player strict zones

- Added POWERAMP_GRADE_MASTER_PLAN.md: a full research-backed roadmap for making DaddyAmp Poweramp-grade without regressing what already works.
- Fixed remaining mini-player clunk by moving time into a fixed-width pill outside the flexible metadata area.
- Converted the mini-player to strict grid zones: art, metadata, optional meter, time pill, controls.
- Hidden duplicate inline time from the metadata kicker so it cannot run into the heart/play rail.
- Added responsive hierarchy: meter hides first, then heart/next yields on narrow devices, while play remains protected.
- Rebuilt signed APK as versionCode 22 / versionName 1.21.

## v1.22 — Poweramp category intelligence phase 1

- Added Poweramp-style previous/next category buttons in the full player. They step by current album for library/device tracks and by current folder for folder-scanned tracks.
- Upgraded the player context label to `Type • Name • current/total` instead of generic `Playing from Album`.
- Context label is now actionable: tap it to jump back to the current album/folder context.
- Added context-aware track counter information to the cycling audio chip.
- Category navigation uses stable track identity and sorted context groups; no index guessing.
- Preserved startup, playback, library persistence, and mini-player fixes.
- Rebuilt signed APK as versionCode 23 / versionName 1.22.

## v1.23 — artist detail + natural track ordering

- Current context jump now supports artist detail when no album/folder context is stronger.
- Rebuilt artist detail as a proper windowed render target; it no longer renders into the default library target and moves DOM afterward.
- Artist headers now show song count, album count, and total duration.
- Artist detail gained Shuffle Artist.
- Context ordering now sorts by album, disc, track number, then natural title.
- Native Android device/folder scans now include trackNumber where MediaStore/retriever exposes it; JS persists and uses it for album/artist ordering.
- Fixed favorite lookup to compare stable ids as strings, preventing missed favorite toggles on mixed numeric/string ids.
- Rebuilt signed APK as versionCode 24 / versionName 1.23.

## v1.24 — library totals + optional list-to-player behavior

- Album grid cards now show total album duration.
- Artist grid cards now show song count, album count, and total duration.
- Folder grid cards now show total folder duration.
- Duration formatting now switches to h:mm:ss for long albums/folders/artists.
- Album detail re-sorts incoming lists through the natural album order pipeline, so context/search opens are stable.
- Added optional Playback setting: Tap Track Opens Player. It defaults off, preserving current browsing behavior, but can mimic Poweramp-style immediate Now Playing launch.
- Rebuilt signed APK as versionCode 25 / versionName 1.24.

## v1.25 — human design pass + playback mode chooser

- Removed overproduced/generated-sounding visible copy: no “flagship audio instrument”, “verified zero-emojis”, HAL jargon, or robotic About text.
- Rewrote settings descriptions to sound simpler, calmer, and more like a finished music app.
- Default/idle copy now uses plain labels like “Ready” and “Startup recovered.”
- Added long-press Shuffle/Repeat playback mode sheet with direct choices: shuffle off, shuffle library, shuffle current context, repeat off, repeat all, repeat one.
- Existing single-tap shuffle/repeat behavior is unchanged.
- Shuffle/repeat button visuals now restore correctly from saved session state.
- Rebuilt signed APK as versionCode 26 / versionName 1.25.
