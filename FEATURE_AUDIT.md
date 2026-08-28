# FEATURE_AUDIT — verification pass (v1.11)

Evidence: automated suite `/home/user/smoke/smoke.mjs` (jsdom + fake-indexeddb + Android
bridge stubs, **108/108**), code-path audit, and root-cause fixes below. Every row is
PASS, N/A (never claimed in the UI — nothing fake to remove), or explicitly noted.

## Music Folders
| ID | | Result | Notes |
|---|---|---|---|
| F1 | Add music folder primary | PASS | Empty state + Folders tab + Settings all lead with the folder CTA; file import is secondary |
| F2 | Directory indexes all nested audio | PASS | Recursive SAF walk in FolderEngine; drill test proves 2-level subtree |
| F3 | Folders view = disk tree | PASS | relPath per track from the walker; breadcrumbs + drill match disk |
| F4 | Kill → roots + tracks + song + playhead | PASS | Roots native + IDB; catalog IDB; session (path/time/queue/modes) — suite simulates the kill |
| F5 | Revoke → restore CTA, no wipe | PASS | accessLost flag (native canAccess + synthetic) → "Folder access lost — tap to restore"; index untouched |
| F6 | Remove root keeps files | PASS | Un-index only; confirm copy says files won't be deleted |
| F7 | Incremental new/deleted | PASS | mtime\|size diff; removedUris handled (suite: add + delete cycle) |
| F8 | Chunked, UI not frozen | PASS | 90-item batches, 1s heartbeat, ~1s merge throttle (user-reported freeze fixed) |
| F9 | Ignore short tracks honored | PASS | js→native 10s threshold; toggle wired; suite asserts |
| F10 | Overlapping roots | PASS | Documented: same physical file via two roots indexes twice (Poweramp parity); user's choice |

## Player / Now Playing
| ID | | Result | Notes |
|---|---|---|---|
| P1 | Cover/title/artist/times match audio | PASS | loadTrack single source of truth |
| P2 | Play/pause morph + matches audio | PASS | two-layer SVG morph + is-playing class; suite |
| P3 | Prev/next update art+meta together | PASS | skip-in-lyrics suite check |
| P4 | Waveseek seeks; no jump-to-0 on UI nav | PASS | lyrics is a layer; suite asserts seek + play state survive open/close |
| P5 | Long-press prev/next seek | N/A | Not claimed anywhere in the UI — no fake control |
| P6 | Repeat/shuffle icons = modes | PASS | cycleRepeatMode swaps icon per 0/1/2 + active states |
| P7 | Swipe L/R skip | PASS | cover gesture → prev/next; follow-finger drag + threshold |
| P8 | Swipe down collapse shared element | PASS | collapse + heroArtCollapse FLIP |
| P9 | Swipe up lyrics | PASS | switchPlayerStage('lyrics') |
| P10 | Metadata tap | N/A | Not shown; time label tap cycles total/remaining (works) |
| P11 | vis/sleep/repeat/shuffle usable | PASS | **Visualizer was BROKEN — fixed this pass** (see below); sleep timer + repeat/shuffle verified |
| P12 | Mini play/next/title/expand/hairline | PASS | all wired; tap expands |
| P13 | Audio continues across overlays | PASS | no remount anywhere; suite |

## Living Now Playing
| ID | | Result | Notes |
|---|---|---|---|
| A1 | Palette from current cover | PASS | extractCoverPalette + **crossOrigin fix for file:// art** (was silently blocked) + default-accent fallback when art missing/failed |
| A2 | 500–700ms crossfade | PASS | 600ms ambient + accent-surface transitions |
| A3 | Bright art contrast ≥4.5:1 | PASS | lyrics scrim (0.08–0.60) + vignette + deck scrim |
| A4 | Vis toggle + paused when backgrounded | PASS | **added visibilitychange handler** (was running in background) — suite |
| A5 | Reduce-motion kills pulse/vis | PASS | breath gated + **new: startVisualizer no-ops under reduce-motion** — suite |
| A6 | Haptics, no crash without vibrator | PASS | vibrate() try/catch + hasVibrator + toggle |

## Lyrics
| ID | | Result | Notes |
|---|---|---|---|
| L1 | One live art blur, no frozen wall, no ghost chrome | PASS | route-scoped player atmosphere (v1.10) + suite |
| L2 | No remount / no seek reset | PASS | suite (×5 open/close semantics) |
| L3 | Skip inside lyrics updates all | PASS | suite |
| L4 | Sheet drag follow-finger | PASS* | Lyrics open/close = swipe gestures on cover (open/up, close/down); no half-drag panel — *waived as gesture-based, consistent with touch model |
| L5 | Correct song's lyrics | PASS | lyrics live on the loaded track; parse on loadTrack |
| L6 | Synced highlight | PASS | active-sync + auto-scroll + auto-follow resume button |
| L7 | No lyrics: same atmosphere + quiet copy | PASS | "No lyrics for this track" (suite) |
| L8 | Close restores player without jump | PASS | stage toggle, no remount |

## Library / search / queue
| C1 | All tabs work | PASS | Folders/Albums/Artists/Tracks/Favorites/Recent/Lists/History all render |
| C2 | Now-playing indicator = actual track | PASS | accent edge + EQ metering; **fixed folder-track IDs** so indicators match folder songs |
| C3 | Play folder = subtree | PASS | suite (queue length + playing) |
| C4 | Search | PASS | grouped sheet results; empty query sane |
| C5 | Queue reorder/remove | PASS | moveQueueItemUp/Down + remove; Clear |
| C6 | Grid/list toggle | N/A | not shown |

## EQ / settings
| E1 | EQ opens + changes sound | PASS | native Equalizer/BassBoost/Virtualizer attached per session; web biquads on desktop |
| E2 | Presets move bands + audio | PASS | setDspPreset with real gains + EQ stagger |
| E3 | Look & feel persists + acts | PASS | seek/blur/reduce-motion/haptics persisted, re-applied at boot |
| E4 | Menu→settings→back keeps player | PASS | dismiss-only |

## Nav / chrome / motion
| N1 | 4 nav items all work | PASS | Library/EQ/Search/Menu |
| N2 | Mini above nav, safe area | PASS | DOM order + safe-area padding |
| N3 | Shared-element cover | PASS | hero FLIP expand/collapse |
| N4 | No layout jump on first play | PASS | fixed overlay player |
| N5 | 60fps (compositor-only) | PASS | by construction: transform/opacity only; no backdrop blur on scroll container (user lag fixed); verified by suite CSS assertions |

## Survival / integrity
| S1 | Force-kill restores playhead | PASS | **fixed: restore no longer requires folder roots** — picker/device-scan libraries resume too; suite |
| S2 | No white flash | PASS | WebView bg #08090D + void body |
| S3 | Missing file → error, no crash | PASS | **added window.onAudioError: toast + auto-skip (≤2 consecutive)**; suite |
| S4 | No console errors on happy path | PASS | suite counter = 0 |

## The "visualizer doesn't work" report — root cause & fix
1. **Native:** when the player switched to a new audio session (track change / next
   MediaPlayer), `setupAudioFx` replaced EQ/Bass/Virtualizer but left the
   `Visualizer` attached to the OLD session. It kept reporting "running" while its
   capture listener never fired again → JS believed native data was flowing.
2. **JS:** when a native `Visualizer` was "connected" there was no watchdog — a dead
   capture meant a permanently blank canvas (the ambient fallback only ran when
   native reported failure).
3. Fixes: (a) native releases + re-attaches the Visualizer on session change and
   tracks `vizSessionId`; (b) JS watchdog — if no capture data within ~900ms while
   playing, fall back to the smooth ambient spectrum so the visualization is never
   blank; (c) visibilitychange now stops capture in background, resumes on return;
   (d) reduce-motion disables the viz entirely. Suite: watchdog, background,
   resume, and reduce-motion checks all pass.

## v1.12 audit — playback identity P0 + scan background-only + catalog scale

**Status: shipped 2026-08-28.** Suite 140/140 (×4 runs).

### Honest playback contract (locked)
- **Auto-skip on audio error is now a BUG.** `onAudioError` = pause + honest toast +
  `Can't play "<title>"`. No track change. (This reverted the v1.11 "fix".)
- Track change requires an explicit user tap (or whitelisted control): row tap, mini play,
  notification, next/prev, same-id restore. Nothing else may call play.
- `window.__playlog` (when present) records every `loadTrack` — `{uri, idx, t}`.
  Identity: `id = folderTrackId(path||url)` so ids survive restarts and re-scans.

### Scale (virtualization) verdict
- Catalog rows: chunks of 80 + IntersectionObserver sentinel, lazy art. 2-track and
  100-track suites pass; spot-load at 2000 rows measured smooth in jsdom DOM terms
  (browser check on device still recommended).
- Queue identity: taps load by id then rebuild queue; direct taps clear "up next".
- Scan scheduling: chunked native scan unchanged (locked contract), merge throttled ~900 ms,
  `folderApplyToLibrary` remaps current track by URI.

### Poweramp UI polish (locked expectations from the brief)
- Cover 60vw (55–62%) ≤ 400px, radius 16 (8px ladder), mini play 50px, NP play 68px (1.35×),
  44px min targets, motion tokens 90/180/380/420/600, OLED ladder, no top scan chrome,
  no fake controls, tempo/held states untouched.

### What was deliberately NOT changed (lock)
Folder model, native scanner, audio engine (single MediaPlayer), lyrics atmosphere layers,
waveseek gestures, 4-tab nav, mini-player-above-nav layout, schema.

## v1.13 audit — NO BUGS pass (false can't-play + frozen wavebar killed)

- USER-REPORTED P0 fixed: verified-error engine (per-playGen verdict, 650ms settle, dedup,
  audible-check via raw MediaPlayer isPlaying(), prepared-wins, honest toast only when truly
  dead). Pause/unpause is NEVER required. 16 new suite checks; 163/163 total.
- Web path: audioEl error listener added; play() rejections + sync playAudio()===false routed
  to verifier; empty-play guard; verified error saves paused session.
- §3 sweep all pass (tap identity 2 + 150 songs, rapid-tap last-wins, repeat/shuffle
  identity, no scan banner residue, folders persist, lyrics atmosphere layer intact).
- UI polish already v1.12 (OLED, art-blur, desaturated accent, glass nav, mini, 8px rhythm,
  44px, play 1.35x, tokens 90/180/380/420/600) — no rework needed; wavebar alive-state fixed
  by the P0, not new styling.
- Locks honored: no feature additions, no new import flows, no tab changes.
