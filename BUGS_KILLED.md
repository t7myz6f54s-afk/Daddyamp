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
