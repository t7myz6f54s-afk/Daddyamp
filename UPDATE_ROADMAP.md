# DaddyAmp → Poweramp Parity: Update Roadmap (mapped 2026-08-29)

## 0. Where the project stands (verified from the repo, not the docs' claims)

**Architecture**
- Thin native shell: `MainActivity.java` (WebView host, 213 lines), `AudifyBridge.java`
  (JS↔native bridge, 1,573 lines: MediaPlayer, crossfade, tempo, balance, preamp, EQ bands,
  bass boost, virtualizer, ReplayGain hook, Visualizer capture, MediaSession, audio-focus
  ducking, system-volume sync, MediaStore scan), `FolderEngine.java` (SAF recursive scanner,
  802 lines, MediaStore fast-path).
- Entire app UI/player logic: one file — `web/index.html` (~10,780 lines, 350+ functions,
  IndexedDB persistence, jsdom-tested design). `assets/index.html` is a build-time copy.
- Build: `build_apk.sh` manual pipeline (aapt2 → javac 8 → d8 → zipalign → apksigner).
  v1.42 / versionCode 43, minSdk 21, target 34. No Gradle.
- Package `com.audify.music`, label "DaddyAmp".

**State of play:** Phases 1–2 of `POWERAMP_GRADE_MASTER_PLAN.md` are essentially done
(interaction polish, category intelligence, library portal, genres, gestures). v1.40–1.42
were stability corrections (self-scroll / idle heat). The docs' regression suite
(`/home/user/smoke/smoke.mjs`, ~145–163 checks) is **not committed to the repo** — it lived
on a previous contributor's machine and is lost.

**Repo hygiene issues found today**
1. Smoke/regression suite is missing from the repo → nothing verifies new changes now.
2. `main.py` (99KB) is a dead PySide6 desktop prototype importing an `app/` package that is
   not in the repo. Dead weight, confuses new contributors.
3. `keystore/audify.keystore` + passwords (`audify123`, in `build_apk.sh`) are committed.
   If the repo is ever public, anyone can sign updates as you. Rotate or move to secrets.
4. Deploy token issue already noted in FEATURE_LOCK; the PAT shared this session should be
   revoked/rotated when we're done.
5. No version automation: versionName/label live in 3 places (script, manifest, strings).
6. Building requires an Android SDK at `/home/user/android-sdk`, which doesn't exist in a
   fresh environment → build is not reproducible. Needs a provisioning step or CI.

**Biggest gaps vs. real Poweramp** (what users will actually feel, in impact order)
1. **No true background playback stack**: MediaSession lives inside the Activity; there is
   no foreground service, no media-style notification with artwork/transport buttons, no
   `POST_NOTIFICATIONS`/`FOREGROUND_SERVICE` permissions. Screen-off / app-swept playback
   is fragile. Poweramp's lockscreen+notification control is table stakes. *(Phase 5 in the
   master plan, but it's really a Phase-3-priority gap.)*
2. **Audio confidence layer unfinished** (master-plan Phase 3): no audio-info chain screen,
   ReplayGain is an estimate (no real tag scanning), no per-output EQ, no output indicator.
3. **Visual maturity** (Phase 4): single skin; visualizer options minimal.
4. **Android-native completeness** (Phase 5): no headset/BT connect behaviors, no
   volume-key long-press skip, no widget, no Android Auto.
5. **Library depth**: no M3U/M3U8 playlist import-export, no Album-Artist category,
   no gapless verification harness.

---

## Guiding rules for every update below (inherited from FEATURE_LOCK + master plan)

- Frozen contracts stay frozen: folder persistence model, deterministic `folderTrackId`,
  playback-identity P0 (tap-exact, generation tokens, honest errors, never auto-skip),
  4-tab nav + mini-above-nav, schema keys (`daddyamp_*`), single MediaPlayer session.
- Allowed-change lanes only: list virtualization, queue identity, scan scheduling.
- Performance-first: optimize invisible work; never remove signature visuals (waveform
  seekbar, adaptive album palette) except in explicit Speed/Line modes.
- No fake controls — if a feature isn't real, the button doesn't ship.
- Every phase lands with: rebuilt smoke suite green + APK build/sign/align verified.

---

## Phase A — Foundation & repo hygiene (1 short sprint, zero user-facing change)
Goal: make the project safe and reproducible before touching features.

- **A1.** Rebuild the smoke suite from the specs in `BUGLOG.md`/`FEATURE_AUDIT.md`
  (jsdom + fake-indexeddb + bridge stubs, sections A–M) and **commit it to the repo**
  at `/smoke/`. Gate: all green on unmodified v1.42.
- **A2.** Delete `main.py` + dead desktop docs (or move to `/archive/` with a note).
- **A3.** Single-source versioning: `VERSION` file → build script injects manifest +
  versionName automatically.
- **A4.** Reproducible build: SDK provisioning script (commandline-tools + build-tools
  34 + platform 34, the "known-good URLs" approach already used before); document env.
- **A5.** Keystore decision: if repo goes public — remove keystore, rotate signing key,
  move creds to env vars. Confirm `.gitignore` actually covers build output.
- **A6.** Consolidate the 14 root planning `.md` files into `/docs/`; keep one CHANGELOG.

*Risk: none to users. Unlocks everything below.*

## Phase B — Bulletproof background playback + system controls (the big one)
Goal: Poweramp-grade "it just keeps playing, and I can control it from anywhere."

- **B1.** Foreground playback service (new `PlaybackService`) with `FOREGROUND_SERVICE` +
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `POST_NOTIFICATIONS`; MediaPlayer ownership moves
  (or is proxied) so audio never dies with the Activity/WebView.
- **B2.** Media-style notification: artwork, play/pause/next/prev, seekbar where supported
  (Android 13+), tap-to-open app. Move MediaSession into the service; keep
  `updateMediaSession` bridge contract intact for the web layer.
- **B3.** Lockscreen/car/BT buttons & AVRCP metadata via the service-side session.
- **B4.** Audio-focus polish: pause on call, duck on navigation prompt (toggle exists in
  bridge — wire UI settings), "becoming noisy" already handled.
- **B5.** Kill-resilience regression test in the new smoke suite (service restart path →
  session restore contract unchanged).

*Files: manifest, new service java, MainActivity, AudifyBridge wiring; web layer barely
touched. Risk: medium (lifecycle) — mitigated by A1's battery + B5.*

## Phase C — Audio confidence (master-plan Phase 3, made concrete)
Goal: the screen an audio nerd opens and trusts.

- **C1.** Audio Info screen: source (codec/bitrate/samplerate from scanner tags) → decoder
  → DSP chain (EQ/preamp/tempo/balance state) → output route. Poweramp-style "Audio Info".
- **C2.** Real ReplayGain: scan track/album gain tags natively (MediaMetadataRetriever
  already in FolderEngine; add R128/REPLAYGAIN keys) and apply; keep estimate fallback.
- **C3.** Output indicators + per-output EQ presets (speaker/wired/BT/USB), with
  explicit "this is per-device" copy. Ship after Phase B so routing detection is solid.
- **C4.** Gapless/crossfade verification harness: `setNextAudio` exists — add a smoke
  section + settings copy that matches reality.

## Phase D — Visual system maturity (Phase 4)
- **D1.** Skin #2 "Clean Graphite" (skin token layer over the existing OLED ladder) with a
  skins picker in Look & Feel; dynamic palette stays the default.
- **D2.** Seek styles surfaced cleanly: Wave / Line (exists — make first-class).
- **D3.** Visualizer options: style choice + off-by-default restraint; native capture
  permission flow already exists.
- **D4.** Large-library flourish guardrails (perf profile already exists: Auto/Speed/Rich).

## Phase E — Android-native completeness (Phase 5, trimmed to what fits this shell)
- **E1.** Headset/Bluetooth connect behaviors (resume on connect, pause on disconnect —
  bridge has the noisy receiver; add settings).
- **E2.** Long-press volume-key track skip (only where system permits; honest setting).
- **E3.** Widget (small + medium) — after Phase B service exists.
- **E4.** Android Auto — last; needs the stable library model + service from B.

## Phase F — Library & playlist depth (ongoing, low-risk slices)
- **F1.** M3U/M3U8 import + export for playlists (plain files under folder roots too).
- **F2.** Album-Artist category; folder→album quick chips.
- **F3.** On-device 500–2000+ track perf measurement (the FEATURE_LOCK open item) with
  results recorded in `/docs/PERF.md`.
- **F4.** Native thumbnail-cache (2–4 concurrent decode jobs) if F3 shows decode jank.

---

## Sequencing & decision points

```
A (hygiene+tests)  →  B (background playback)  →  C (audio confidence)
                                                    ↘ D (skins) ↗
                                                  E/F (parallel, low-risk slices)
```

- **Recommended first real sprint after A:** Phase B — it's the single biggest felt gap
  between DaddyAmp and Poweramp today.
- **If you want visible wow first instead:** do D1–D2 right after A, then B. Trade-off:
  users still can't rely on screen-off playback.
- **Do not** start C3 (per-output EQ) before B — routing detection depends on the service.

## Exit criteria for "Poweramp-grade" (measurable)
1. Plays 8h+ with screen off, controllable from lockscreen/notification/BT — battery-safe.
2. 10k-track library: cold open < 1.5s to first paint; scroll at 60fps; portal ~instant
   (already ~1ms in harness — prove on device, Phase F3).
3. Audio Info screen shows a true chain; ReplayGain real when tags exist.
4. Tapfeels catalog regression battery ≥ the rebuilt A–M suite, green on every release.
