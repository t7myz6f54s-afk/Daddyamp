# DaddyAmp changelog

Per-version user-facing + engineering changes. The full historical narrative
lives in `POWERAMP_GRADE_MASTER_PLAN.md` / `BUGS_KILLED.md`; new entries land here.

## v1.44 (build 45) — 2026-08-29

**Roadmap Phase B — Background Playback Stack** (all FEATURE_LOCK contracts preserved): music now survives the app being swiped away, with a media notification and lockscreen controls, Poweramp-grade.

- **NEW `PlaybackService`** (framework-only, zero new dependencies): typed `mediaPlayback` foreground service with a `MediaStyle` notification — artwork large-icon, prev / play-pause / next in the compact row, content-intent back into the app, swipe-dismiss only when actually paused, and a 15-minute paused-idle auto-stop. Swiping the notification away while paused no longer leaves a ghost service.
- **NEW `AppHolder`**: survived-teardown reference for the WebView + bridge. Swiping the app away while playing no longer restarts the app on relaunch — the SAME WebView, MediaPlayer, and every byte of JS state (queue, position, folders, settings) are re-attached in place of a cold boot.
- **Lockscreen + MediaSession unified**: `AudifyBridge.transport()` is now the single control path — lockscreen buttons, Bluetooth headsets, notification buttons, and in-app controls all hit the same JS handler (`window.onMediaSessionTransport`), so surfaces can never disagree.
- **Android 13+ notification permission**: asked once, at the first moment it matters (first press of play) — never at cold boot. Denial only hides the notification; playback keeps working.
- **Android 14 ready**: `POST_NOTIFICATIONS` / `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` declared; `startForeground` uses the typed overload on API 29+.
- **Adversarial-review hardening**: the survived WebView's chrome client re-homes its host Activity on relaunch — JS dialogs and the file picker can never target a destroyed window (would have been a crash on device); notification artwork uses the pre-scaled bitmap to avoid per-track memory churn.
- Dead code removal: the unused 99KB `main.py` React-web prototype is gone (recoverable from history at `19a5bb4`).
- Keystore parameterization: `build_apk.sh` honors `DADDYAMP_KEYSTORE` / `DADDYAMP_KEY_ALIAS` / `DADDYAMP_KEY_PASS` env overrides (defaults unchanged).
- Suite: **82/82 green** (61 JS/agent battery + 21 native static-guard checks: manifest permissions, non-exported media service, MediaStyle compact actions, immutable PendingIntents, idle-stop, delete-intent, no-androidx anywhere, handler-based `runOnJs`, full 37-method @JavascriptInterface golden contract, WebView reuse, `isPlaying()` teardown guard).
- Honest limitation: notification rendering, lockscreen behavior, and swipe-survival are verified by compile + contract checks in this build; they have NOT yet been exercised on a physical device.

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
