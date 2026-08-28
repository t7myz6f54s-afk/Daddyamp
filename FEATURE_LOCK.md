# FEATURE_LOCK — v1.12

Status of the §1 LOCK list (SCAN_BG_AND_POWERAMP_UI_PROMPT.md). These are **locked**:
they may be changed only via the documented exception paths; nothing else may touch them.

## Locked (behavior contracts, frozen)

| Area | Contract | Exception |
|---|---|---|
| Folders persistence | folderState roots/tracks in IndexedDB; native roots are source of truth; `folderTrackId` deterministic ids; merge throttled ~900 ms | none |
| Lyrics live background | one living lyrics atmosphere layer; no seek/pause reset; auto-follow preserved; lrclib fetch gated by `state.playGen` | none |
| Waveseek gestures | track markers/bookmarks seek; mini swipe expand/collapse | none |
| Gestures | pull-to-refresh, mini swipe, cover tap → lyrics | none |
| 4 nav | Library / EQ / Search / Menu; mini above nav | none |
| Mini above nav | `#docked-mini-player` above `#docked-bottom-nav` | none |
| Schema | settings/state/session keys (`daddyamp_*`) stable | none |
| Audio engine | single native MediaPlayer + WebView bridge; no second session | none |
| Playback identity P0 | tap-exact playback; stable ids; generation tokens; scan/import never play; audio errors never skip; restore same-track-only | allowed to change: no |

## Allowed to change (the three exception paths)

1. **List virtualization** — row chunking (80) + sentinel observer; `dataset.songId`/`path` identity; lazy art.
2. **Queue identity** — load-by-id then rebuild; direct taps clear "up next".
3. **Scan scheduling** — chunked native scan; inline status only; no sticky chrome.

Anything else = new feature: requires its own prompt + regression battery first.

## Regression battery (must stay green)

`cd /home/user/smoke && node smoke.mjs` → **140/140** (A boot, B scan/folders, C themes,
D EQ, E library, F session, G playlists, H history, I queue, J player stages,
K atmosphere, L verify incl. honest audio-error, M playback-identity proof).

## Known gaps / next candidates (not started)

- FEATURE_LOCK checklist item: full on-device 500–2000 track perf measurement.
- Off-main-thread cover decode is delegated to WebView lazy/async decoding; a native
  thumbnail cache (2–4 concurrent jobs) remains a candidate.
- Public `keystore/` decision still pending (keystore is in-repo; make it private if public).
- Rotate the deploy token: v1.12 was pushed with the session PAT (used inline; no
  residue in .git/config). Generate a fresh PAT before the next push and delete the old one.
