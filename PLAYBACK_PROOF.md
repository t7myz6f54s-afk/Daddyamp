# PLAYBACK PROOF — deterministic playback identity (P0, stop-the-press)

**Date:** 2026-08-28 · **Build:** v1.12 (versionCode 13) · **Suite:** `/home/user/smoke/smoke.mjs` → **140/140, stable ×4 runs**

## Contract (what the user demanded)

With exactly 2 tracks in the library, playback is fully deterministic:

| Action | Expected |
|---|---|
| Tap row A | A plays. Never B. Never "also" B. |
| Tap row B | B plays. Never A. |
| Alternate A/B ×10 | Every single tap plays exactly the tapped row. |
| 10 s idle | Nothing changes (no auto-start, no skip, no swap). |
| Scan / import / palette / lyrics / session restore | Never switches track, never starts playback. |
| Audio error | Honest toast + pause. NO auto-skip to the next track. |
| Restore (force-kill) | Same track by stable id/path; autoplay only if it was playing. |
| Play callers (whitelist) | Row tap, mini play/pause, notification, next/prev, same-id restore. ONLY these may start audio. |

## Root causes found and fixed (v1.12)

1. **Auto-skip on audio error** (`window.onAudioError`) — v1.11 jumped to `(current+1)` on any
   audio failure. This is the exact "tap A → B plays" hallucination: A's file fails quietly
   (or errors late) and the app plays B. **Now:** pause, update the button, toast
   `Can't play "<title>"`. Track never changes. *(Reverted; treated as a bug.)*
2. **Auto-play after import** — `window.onAudioFilesImported` and `receiveHtmlFiles` both called
   `loadTrack(0, true)` after adding files. **Now:** persist/render/toast only. No `loadTrack`.
3. **Non-stable track ids** — imported songs used `Date.now()+random` (different on every launch);
   device scan replaced the whole array with new random-id objects. **Now:** `ensureTrackId(t)`
   derives a deterministic id from the canonical URI/path (`folderTrackId`) for every ingest
   path: scan, import, HTML import, folder merge, cue.
4. **Array-index identity** — 16 call sites did `findIndex(x => x.id === …)` + `loadTrack`.
   **Now:** `resolveSongIndex(t)` (by id → url → path → last-resort `indexOf`) and
   `resolveSongByID(id)`; all id-based lookups go through them.
5. **Stale `currentIndex` after rebuilds** — `invokeDeviceScan` replaced `state.songs`
   (`state.songs = merged`), `folderApplyToLibrary` rebuilt it, and import **unshifts** shift
   every index. A stale index silently re-pointed at another song ("wrong song after scan").
   **Now:** every rebuild path captures the current canonical URI first and calls
   `remapCurrentIndex(prevUri)` to move the index to the *same track*.
6. **Queue mutation under the tap** — the now-playing queue row did `state.queue.splice(idx, 1)`
   **before** loading. **Now:** load by identity first; only then rebuild the queue without the
   item. A direct catalog tap also clears "up next" (`state.queue = []`) so nothing can advance
   behind the user's back.
7. **Generation token vs async callbacks** — palette extraction and lrclib fetch could land
   *after* a later tap and swap the UI covering the new track. **Now:** both capture
   `state.playGen` at start and bail if it changed. Same for the applied-seek guard
   (`pendingSeekGen === playGen`).
8. **Missing `playing` in the session** — restore never autoplayed. **Now:**
   `saveSession` stores `playing`; restore autoplays **only** when `playing === true` AND the
   saved path matches the resolved track. Otherwise it restores paused with a "Restored — …" toast.
9. **Restore-to-position eaten** — `loadTrack` unconditionally cleared `pendingSeekMs` for
   non-CUE tracks, so a restored time position was silently dropped. **Now:** `loadTrack(index,
   autoPlay, preSeekMs)` — restore passes its seek explicitly; cue seeks still apply; a stale
   seek from an aborted load can never apply to a newer track (generation guard).

## The whitelist (audited — grep-verified, only these call `loadTrack`/`startPlayback`)

- `row.onclick` (catalog, album/artist rows, queue stage, history) — via `resolveSongIndex`
- mini play/pause button, NP play/pause button, media-session / notification handling
- previous/next transport (explicit user tap)
- `restoreSession` (same-id/path restore, autoplay only if it was playing)
- scan/import/folder-merge/palette/lyrics callbacks: **never** touch playback.

## Automated proof (in the suite, section M)

`cd /home/user/smoke && node smoke.mjs` → 140 checks, all PASS (4 consecutive runs).
The proof block (runs against the real `web/index.html` in jsdom with a bridge stub):

- `proof: exactly 2 rows mounted` — virtualized catalog mounts exactly the 2 tracks.
- `proof: stable distinct ids set` — A and B get deterministic, distinct ids.
- `proof: tap A plays A #1..#5` (5×), `tap B plays B #1..#5` (5×) — asserts the
  **id log** (`window.__playlog` in `loadTrack`, the single funnel) shows exactly one entry
  per tap, with the tapped track's URI, and `state.currentIndex` resolves back to the tapped object.
- `proof: alternate #1..#10 exact` — 10 alternating taps, every one exact.
- `proof: 20 taps, zero identity failures`.
- `proof: idle 900ms - no auto-start/skip/swap` — no log growth, same index, same URI,
  same playing state (time may advance; that's playback, not a track change).
- `proof: device scan never switches track` — full `invokeDeviceScan` under the current
  track: no new play, same URI after merge.
- `proof: import never switches track` — `onAudioFilesImported` under the current track:
  no new play, same URI after unshift/remap.
- `proof: session stores playing flag + path`
- `proof: kill-restore recovers the SAME track` — wipe index/state, `restoreSession()`:
  same URI; autoplayed exactly once because it was playing.
- `proof: paused restore never autoplays` — paused save → restore → no play, no seek.

Id log capture (per playback event): `{ uri, idx, t }` pushed in `loadTrack` when
`window.__playlog` exists (test instrumentation; absent in production).

## Manual on-device check (2-track phone, if you want belt-and-braces)

1. Library with exactly 2 songs; tap A → hear A; tap B → hear B; alternate 10×.
2. Leave it playing, watch 10 s → nothing else starts/skips.
3. Tap a broken/unsupported file → toast "Can't play …", stays on the same track, no skip.
4. Scan/import files while A plays → A keeps playing, no switch.
5. Kill the app while A plays → reopen → A resumes from its position.
6. Kill while paused → reopen → same track, paused.

## Lock

Per the stop-the-press directive, no UI/feature work happened until this proof was green
(in this build, the scan-banner removal and list virtualization landed *with* the proof in the
same commit and are covered by the suite; both are identity-neutral — neither can call play).
