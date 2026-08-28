# STOP-THE-PRESS: playback identity (P0 — wins until green)

> Delivered inline as a stop-the-press. Preserved here for the record.

## The fact
In a library with 2 songs: tap A → **B plays**. The player sometimes starts/skips on its own.

## The rule (acceptance criteria)
- Tap A → A plays. Tap B → B plays. Alternate as much as you want — always exact.
- 10 s idle → nothing changes (no auto-start, no skip, no swap).
- No auto-play/skip/swap from: scans, imports, palette extraction, lyrics fetch, session restore.
- **Forbidden:** `play(index)` against a reordered array; shuffle/scan mutating under the tap;
  two audio sessions; autoplay on mount/scan-chunk/palette-extract/lyrics-open; any "helpful"
  auto-start. Audio errors never auto-skip — honest toast, unchanged track.

## Required mechanics
- Stable unique id per track = canonical URI/path. Never 0/1 array index as identity.
- `onRowTap(id)`: cancel any in-flight load (generation token), stop current, load **that**
  URI. Only these may call play: row tap, mini play, notification, next/prev, same-id restore.
- Scan must never call play or replace the current track. Shuffle/repeat disabled if they
  fight identity.

## Proof (required before any UI work resumes)
`PLAYBACK_PROOF.md` with the id log. In this build: `window.__playlog` + suite section M
(30 checks) + manual 6-step checklist.
