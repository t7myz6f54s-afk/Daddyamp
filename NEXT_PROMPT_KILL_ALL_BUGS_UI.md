# NEXT_PROMPT_KILL_ALL_BUGS_UI — No bugs, kill 'em all + UI refinement

> Task for the implementation agent. Playback identity (tap A → play A) is assumed
> landed (v1.12). This pass is **correctness + polish**, not new features.

## RULE ZERO — NO BUGS. KILL 'EM ALL.
If the UI can lie, toast, hitch, or desync from audio, it is a P0. Do not add features.
Hunt every bug, kill it, then refine UI to Poweramp.

### USER-REPORTED (must fix first)
- On Play, "can't play the song" appears EVEN THOUGH the song plays.
- Wavebar/seek PAUSED / frozen / not tracking while audio plays.
- Pause then unpause → then it "starts working" (toast gone, wavebar moves).
This is a false-error + playhead-sync race. Kill it.

Then full sweep: toasts, media errors, waveform, play/pause, lyrics, scan banner,
wrong-track, crashes, gestures, safe area. Then Poweramp UI refinement only.
Write BUGS_KILLED.md.

## Required behavior (P0)
1. Never show "can't play" unless: no audible playback AND element is in a real error
   state AFTER the current playGen load finished.
2. Ignore AbortError / MEDIA_ERR_ABORTED / interruption while replacing source.
3. Wavebar bound to timeupdate + duration of the CURRENT src only; starts moving when
   playing is true — not only after a second pause/play.
4. Single playGen: stale error/pause/ended must not update UI.
5. Pause/unpause must NOT be required for seekbar or toasts to be correct.
6. If play succeeds, dismiss any error toast immediately.
7. Copy only if truly failed: "Can't play this file" + filename.

## Proof
- Known-good track: no error toast; sound AND wavebar move within 300ms of audible start.
- Skip A→B: no false error; wavebar resets and runs on B.
- Pause/unpause: no change except pause/resume (no "unlock").
- Bad file: toast only then; next good file clean.

## Checklist (§3): Player / False UI / Lyrics-atmosphere / Library / Stability
(per prompt: pass or fix each item; no "later")

## UI refinement (§4, only after P0 + checklist green)
OLED, art-blur, desaturated accent, glass nav, mini-player; wavebar looks alive when
audio is alive; collapse gap from removed scan banner; type/8px rhythm/44px/play 1.35×,
motion tokens 90/180/380/420/600. No new tabs/AI/import flows.

## Process
1. Reproduce false toast + frozen wavebar; fix with playGen + honest playing/error.
2. Run §3; kill every fail.
3. UI refine.
4. Regression: 2-song A/B, pause/unpause not required, lyrics, force-kill, no scan banner.
5. BUGS_KILLED.md: bug, cause, fix (template in prompt; "still open: none").
