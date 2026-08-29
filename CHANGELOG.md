# DaddyAmp changelog

Per-version user-facing + engineering changes. The full historical narrative
lives in `POWERAMP_GRADE_MASTER_PLAN.md` / `BUGS_KILLED.md`; new entries land here.

## v1.46 (build 47) — 2026-08-29

**"Still no cover in the player" — the GPU-truth fix.**

v1.45's verified-first pipeline changed nothing visible on the device, which proved the
image DATA always arrived — the hero was being painted into a hardware layer your GPU
wouldn't show. Row thumbnails (small, unpromoted, software path) always worked. That is
the classic WebView black-texture class, and this build removes every GPU sin in the
Now Playing route:

- **Hero off the composite fast-lane**: removed the permanent `will-change` promotion from
  the now-playing cover — it paints on the same software path as every library thumbnail
  that visibly worked all along.
- **Blur baked once, not per frame**: ambient walls were a `blur(56px)` filter repainted
  on a 120%-screen image *every frame* (plus a duplicate override block doing it again!).
  Now each cover is downscaled once to a baked 48px thumbnail — the layout's upscale
  reproduces the gaussian look at ~1/1000th the raster cost; tainted/failed bakes fall
  back to the old CSS-filter path unchanged.
- **Container animation cleaned**: `ambientDrift` no longer animates `filter:` (was
  invalidating the most expensive layer on screen every frame, forever).
- **Device truth-table**: long-press the "Playing from …" header in Now Playing shows a
  diagnostic toast (src, decode state, rect, opacity) — one tap and any future report
  carries the exact state, but you shouldn't need it now.

Suite: **102/102 green** (new FPS guards: no `will-change` on hero/ambient imgs, no filter
animation in ambientDrift, baked-class fallback present, bake pipeline + diagnostics
armed). Dynamic-adaptation (v1.45 palette/deep backdrop) retained.

## v1.45 (build 46) — 2026-08-29

**"No cover in the player" fix + Premium adaptive Now Playing (Poweramp-style theming).**

- **Root-cause class found by adversarial audit**: the now-playing artwork pipeline was a
  swap-heavy cascade — up to 6 concurrent decodes of the same cover (hero + mini + 4
  ambient slots) with opacity animation mid-decode, fenced by a global ONE-SHOT
  `img error → placeholder` fallback (`data-fb` flag per element). Any transient
  decode failure would strand dynamic surfaces on the placeholder while long-lived
  library rows kept their covers — exactly the reported symptom.
- **Verified-first art (single-flight)**: a new `preloadTrackArt()` preloader decodes
  each cover ONCE; the player hero, mini thumb and ambient walls only ever receive
  PROVEN image URLs. A failed decode paints the placeholder for THAT url only, and a
  later good track fully recovers — the one-shot stranding is gone by construction.
- **Device diagnostics channel**: first decode failure per track logs one
  `ART_DECODE_FAIL id=… url=…` via `AndroidBridge.log` — the next field report will
  carry the definitive reason if any device still loses art.
- **Hero-motion hardening**: expand/collapse shared-element transforms now refuse
  0/∞ scales (pre-layout rects), so the cover can never be flung off-screen.
- **Premium whole-player adaptation** (the feature ask): the full Now Playing screen
  now breathes with the playing cover — deep OLED-dark backdrop gradient derived
  from the artwork's palette (new `--deep-1/2/3` tokens), glass control deck tinted
  by cover tones, existing accent/scrim/ambient-glow channels retained. Defaults are
  byte-identical to the classic abyss look: no artwork = zero visual change.
  Old WebViews (<111) keep the classic deck via an explicit color-mix fallback.
- Harness: jsdom now simulates image raster with **failure injection**, and the F2
  battery (14 checks) proves: fallback on undecodable art, mini-thumb in lockstep,
  exactly one diagnostic log, full recovery, hero-scale guards, deep-token defaults,
  palette-driven player background, deck fallback ordering.
- Suite: **96/96 green**.
- Honest limitation: the device-visible 3D effect (palette gradients, art raster on
  the physical screen) is verified by pipeline contracts + cascade-static guards, not
  by eye on hardware — no device in this environment. `ART_DECODE_FAIL` closes that
  loop on the next install.

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
