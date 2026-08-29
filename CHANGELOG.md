# DaddyAmp changelog

Per-version user-facing + engineering changes. The full historical narrative
lives in `POWERAMP_GRADE_MASTER_PLAN.md` / `BUGS_KILLED.md`; new entries land here.

## v1.43 (build 44) — 2026-08-29

**Bug-kill release driven by device reports: library scrolling itself forever,
artwork gone everywhere, "wrong song" feel.**

Root-cause fixes (not band-aids):

- **Runaway self-scroll killed for good.** `overflow-anchor` is not inherited,
  so rebuilt virtual rows were still scroll-anchor candidates; Chrome/WebView
  compensated their translate churn by nudging `scrollTop`, and
  `scroll-behavior:smooth` animated each nudge — a self-sustaining downward
  drift that also shifted rows under taps (the "hallucinating songs" feel and
  flickering covers). Fixed: anchoring disabled for the whole catalog subtree,
  scroller forced to instant programmatic behavior, translate writes skipped
  when unchanged.
- **Album artwork restored.** The native MediaStore fast-scan (v1.14+) emitted
  `content://…/albumart` URIs that a WebView hosting a `file://` page cannot
  paint on current builds — every cover collapsed to the placeholder. The
  scanner now decodes each album's art natively into the on-disk cache once
  and emits `file://` paths (one decode per album id per run, same budget as
  v1.14). The JS layer sanitizes stale persisted `content://` art, and a
  one-time migration re-scans enabled folder roots so existing libraries
  regain covers without user action. Runs once per install; never duplicates
  the scheduled auto-rescan.
- **Intermittent whole-catalog loss fixed.** A scan whose native done event
  carried an empty trailing batch (exact batch-boundary libraries) skipped
  persistence entirely — the library looked fine, then vanished on restart.
  Done now always persists exactly once.
- **Silent manual EQ sliders fixed.** Band drags previously only moved
  desktop WebAudio filters — on Android they changed nothing. They now drive
  the native EQ (+ bass boost on band 0) like presets do.
- **Canvas-less environments no longer abort boot** (missing 2D context →
  inert scrubber/palette instead of mount failure).

Engineering:

- **The regression battery is back — and in the repo this time** (`/smoke`,
  jsdom + fake-indexeddb + bridge stubs, 61 checks, hermetic). It reproduces
  every bug above before the fix exists; it is the push/build gate.
- Version bumped to 1.43 / code 44 in manifest + `build_apk.sh`.

Not changed (locked, per `FEATURE_LOCK.md`): folder persistence model,
deterministic `folderTrackId`, playback-identity contracts, single
MediaPlayer session, nav/mini layout, scan scheduling, virtualization lanes.
