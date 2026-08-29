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

## v1.16 — crash-after-play + startup lag + Android volume sync

- Playback stability: Android AudioFX (Equalizer/BassBoost/Virtualizer) is no longer attached eagerly on every song prepare. It now attaches lazily only when DSP controls are used, avoiding vendor MediaPlayer/AudioEffect crashes that can happen seconds after playback starts.
- Startup lag: removed the automatic `loadTrack(..., false)` boot path. Startup now hydrates the mini/full player cheaply without fetching lyrics, extracting palette, updating media session, or touching native playback.
- Startup lag: folder auto-rescan is deferred 8 seconds after folder DB/session restore so the WebView becomes interactive first.
- Performance: Android playback polling remains lighter, with duration calls throttled.
- Volume sync: added native STREAM_MUSIC volume observer and JS bridge. Hardware volume buttons now update the DaddyAmp volume slider/icon. The in-app slider now controls the same Android media volume instead of a disconnected per-player value.
- Rebuilt signed APK as versionCode 17 / versionName 1.16.

## v1.17 — 10k library persistence + real folder fast scan

- Fixed large scanned libraries disappearing after restart: device scan results now persist in IndexedDB (`deviceTracks`) instead of overflowing localStorage. localStorage now stores only small manual imports/settings.
- Fixed folder scans saying complete but not appearing reliably: folder tracks are persisted through a cleared/rebuilt IndexedDB store at scan completion, and loaded back into the library on boot.
- Added a MediaStore-only fast path for full folder scans when the SAF folder maps to device storage. 10k+ indexed songs are now built from one MediaStore query in large batches instead of centuries of SAF walking/retriever opens.
- Fixed virtualized library only showing the first handful/chunk on some WebViews: rows now mount before observer registration, initial chunk is larger, and a scroll fallback always pumps more rows.
- Fixed false playing animation on app open: rows only show the playing/equalizer animation when audio is actually playing, not just because currentIndex points at a remembered song.
- Reset now clears the large IndexedDB catalogs too.
- Rebuilt signed APK as versionCode 18 / versionName 1.17.

## v1.18 — Poweramp seamless refinement pass

- Added a Poweramp-style refinement map documenting what is strong, what still needs work, and the next refinement queue.
- Startup/session restore refinement: non-playing restored sessions now hydrate UI cheaply instead of running full `loadTrack()` and causing lyrics/palette/native work on boot.
- Fixed cold-boot play button: pressing play on a visually hydrated song now loads that track instead of trying to resume an empty native MediaPlayer.
- Added tappable player audio-info chip cycling between format, time/library count, artist/album, and filename.
- Added long-press context menu behavior for catalog rows and full-player artwork.
- Pull-to-refresh now refreshes persistent folder roots plus Android MediaStore, so refreshed music remains after restart.
- Added calmer Poweramp-like touch feedback and guarded LRCLIB fetch for WebViews/test environments without fetch.
- Rebuilt signed APK as versionCode 19 / versionName 1.18.

## v1.19 — premium mini-player overhaul

- Redesigned the mini-player from a flat generated-looking bar into a floating glass control deck above the bottom navigation.
- Added subtle album-art tinting behind the mini-player via the current cover artwork, connected to the existing ambient art system.
- Added mini-player drag handle, state/time kicker, restrained playing LED, and a five-bar micro meter that animates only during real playback.
- Improved thumb ergonomics and visual hierarchy for favorite, play/pause, and next controls.
- Added swipe-up on the mini-player to expand Now Playing, while preserving horizontal swipe previous/next.
- Added safer mini progress/time updates and calmer press feedback to reduce the AI-generated/clunky feel.
- Rebuilt signed APK as versionCode 20 / versionName 1.19.

## v1.20 — mini-player collision fix / designer layout pass

- Fixed mini-player time text colliding into the heart/actions area.
- Reworked the mini-player into strict zones: fixed artwork, flexible clipped metadata, optional meter, fixed action rail.
- Changed mini time display to compact elapsed-only while playing; full elapsed/duration stays in Now Playing where there is enough room.
- Added responsive rules: on narrow phones the decorative meter and then heart yield before text/control collision can happen.
- Tightened typography, overflow, and ellipsis rules so long track names/artists/time cannot jumble the card.
- Rebuilt signed APK as versionCode 21 / versionName 1.20.

## v1.21 — Poweramp designer plan + mini-player strict zones

- Added POWERAMP_GRADE_MASTER_PLAN.md: a full research-backed roadmap for making DaddyAmp Poweramp-grade without regressing what already works.
- Fixed remaining mini-player clunk by moving time into a fixed-width pill outside the flexible metadata area.
- Converted the mini-player to strict grid zones: art, metadata, optional meter, time pill, controls.
- Hidden duplicate inline time from the metadata kicker so it cannot run into the heart/play rail.
- Added responsive hierarchy: meter hides first, then heart/next yields on narrow devices, while play remains protected.
- Rebuilt signed APK as versionCode 22 / versionName 1.21.

## v1.22 — Poweramp category intelligence phase 1

- Added Poweramp-style previous/next category buttons in the full player. They step by current album for library/device tracks and by current folder for folder-scanned tracks.
- Upgraded the player context label to `Type • Name • current/total` instead of generic `Playing from Album`.
- Context label is now actionable: tap it to jump back to the current album/folder context.
- Added context-aware track counter information to the cycling audio chip.
- Category navigation uses stable track identity and sorted context groups; no index guessing.
- Preserved startup, playback, library persistence, and mini-player fixes.
- Rebuilt signed APK as versionCode 23 / versionName 1.22.

## v1.23 — artist detail + natural track ordering

- Current context jump now supports artist detail when no album/folder context is stronger.
- Rebuilt artist detail as a proper windowed render target; it no longer renders into the default library target and moves DOM afterward.
- Artist headers now show song count, album count, and total duration.
- Artist detail gained Shuffle Artist.
- Context ordering now sorts by album, disc, track number, then natural title.
- Native Android device/folder scans now include trackNumber where MediaStore/retriever exposes it; JS persists and uses it for album/artist ordering.
- Fixed favorite lookup to compare stable ids as strings, preventing missed favorite toggles on mixed numeric/string ids.
- Rebuilt signed APK as versionCode 24 / versionName 1.23.

## v1.24 — library totals + optional list-to-player behavior

- Album grid cards now show total album duration.
- Artist grid cards now show song count, album count, and total duration.
- Folder grid cards now show total folder duration.
- Duration formatting now switches to h:mm:ss for long albums/folders/artists.
- Album detail re-sorts incoming lists through the natural album order pipeline, so context/search opens are stable.
- Added optional Playback setting: Tap Track Opens Player. It defaults off, preserving current browsing behavior, but can mimic Poweramp-style immediate Now Playing launch.
- Rebuilt signed APK as versionCode 25 / versionName 1.24.

## v1.25 — human design pass + playback mode chooser

- Removed overproduced/generated-sounding visible copy: no “flagship audio instrument”, “verified zero-emojis”, HAL jargon, or robotic About text.
- Rewrote settings descriptions to sound simpler, calmer, and more like a finished music app.
- Default/idle copy now uses plain labels like “Ready” and “Startup recovered.”
- Added long-press Shuffle/Repeat playback mode sheet with direct choices: shuffle off, shuffle library, shuffle current context, repeat off, repeat all, repeat one.
- Existing single-tap shuffle/repeat behavior is unchanged.
- Shuffle/repeat button visuals now restore correctly from saved session state.
- Rebuilt signed APK as versionCode 26 / versionName 1.25.

## v1.26 — intelligent mini-player + pinch list sizes

- Added Poweramp-style pinch-to-zoom list sizing: Text, Compact, Comfort, and Large.
- Pinch inward makes lists denser; pinch outward enlarges song rows/cards.
- Added Look & Feel > List Size pills so the feature is discoverable without knowing the gesture.
- List size persists and is applied on restart.
- Album/artist/folder grids also respond to list density.
- Mini-player now shows current playback context position, e.g. Album 3/12, with a small context label.
- Mini-player short horizontal swipe skips tracks; longer horizontal swipe skips album/folder/category.
- Mini-player swipe down opens the current context; swipe up still opens Now Playing.
- Preserved strict mini-player zones so time/heart/action collision stays fixed.
- Rebuilt signed APK as versionCode 27 / versionName 1.26.

## v1.27 — fluid player gestures + smoother pinch

- Added fluid full-player drag-down minimize from the header/deck with live movement feedback.
- Artwork gestures are more tactile: swipe up opens lyrics, swipe down minimizes, left/right skips tracks.
- Lyrics stage now supports swipe down to return to artwork.
- Reworked pinch-to-size so it does not re-render the list while fingers are moving.
- Pinch now shows a lightweight List Size HUD and compositor-only preview scale, then applies the actual density once on release.
- Removed duplicate pinch touchmove path and moved preview updates through requestAnimationFrame.
- Preserved strict mini-player collision zones, category swipes, folder scans, refresh, and large-library persistence.
- Rebuilt signed APK as versionCode 28 / versionName 1.27.

## v1.28 — major swipe-in Library Home portal

- Added a Poweramp-style swipe-left Library Home portal with animated category cards.
- Portal categories: All Songs, Folders, Albums, Artists, Genres, Playlists, Favorites, Recently Played, Queue, and Search.
- Swipe right from library lists quickly returns to Now Playing when a track is loaded.
- Swipe right inside the portal fluidly closes it back to the library/mini-player layer.
- Portal includes live drag feedback, staggered card entrance, tinted glass cards, and sticky current-track return.
- Added real Genre browsing with genre grid, genre detail, play genre, and shuffle genre.
- Fixed portal scroll fallback for WebViews/environments without smooth scrollTo.
- Preserved mini-player gestures, pinch sizing, player lyrics/artwork gestures, folder scans, refresh, persistence, and strict mini-player layout zones.
- Rebuilt signed APK as versionCode 29 / versionName 1.28.

## v1.29 — instant Library Home portal performance fix

- Fixed the reported slow swipe-left Library Home portal opening.
- Removed synchronous genre/folder/history indexing from portal open.
- Portal now prebuilds in idle time and opens from cached DOM.
- Portal counts update asynchronously after the animation starts, not before.
- Swipe-left now shows live panel drag feedback immediately instead of waiting until release.
- Lowered the swipe threshold so normal swipes open reliably.
- Reduced transition cost with lighter shadows, shorter card stagger, containment, will-change, and backface layer hints.
- Added portal handling to Android back action.
- 10k-track smoke test showed openLibraryPortal sync time around 1 ms after priming, with all 10 cards and counts intact.
- Rebuilt signed APK as versionCode 30 / versionName 1.29.

## v1.30 — smart genres + large-library smoothness

- Fixed Genres collapsing into generic Local/Device/Folder buckets.
- Android device scanner now reads MediaStore genre tags instead of hardcoding Device Audio.
- Folder fast-scan now reads MediaStore genre tags.
- Folder retriever fallback now allows missing genres to be inferred instead of forcing Folder Audio.
- Added smart genre normalization and alias cleanup for common tags like hip-hop/rap, R&B, EDM/electronic, lo-fi, soundtrack, qawwali, naat, nasheed, Quran, etc.
- Added deterministic local genre inference from title, artist, album, path, filename, and folder names when metadata is missing/generic.
- Existing persisted tracks and new imports/scans are classified through the smart genre pipeline.
- Genre index is cached by songs revision for faster reopen.
- Huge libraries now disable per-row/card entrance animations to reduce clunky scrolling.
- Initial catalog mount now uses smaller dynamic chunks on 1k/3k+ libraries plus DocumentFragment batching.
- 10k-track smoke verified multiple genre buckets, 64-row initial mount, large-library smoothness class, and ~1 ms cached portal open.
- Rebuilt signed APK as versionCode 31 / versionName 1.30.

## v1.31 — background bleed fix + stronger genre intelligence

- Fixed Library Home/player background bleed by making the portal/full player opaque and preventing drag opacity from revealing the library behind it.
- Added famous-artist genre hints, including Britney Spears -> Pop, before keyword fallback.
- Expanded local deterministic genre inference for pop, rap, R&B, Bollywood, qawwali, rock, metal, electronic, jazz, classical, soundtrack, reggae, country, and more.
- Retested a 10,000-track Britney/Local Device case: Genres classified all 10,000 as Pop with 0 Unsorted.
- Reduced large-library jank further by disabling heavy mini-player blur/reflection, ambient glow, and expensive shadows in large-library mode.
- Kept genre inference local and deterministic; no files are uploaded and no external classifier is called.
- Rebuilt signed APK as versionCode 32 / versionName 1.31.

## v1.32 — Poweramp-research premium smoothness + audio leveling

- Added a Poweramp-inspired Performance Profile: Auto, Speed, and Rich.
- Auto now enters a lean rendering path for very large libraries; Speed can force it on; Rich keeps the heavier visual atmosphere when the device can handle it.
- Performance mode disables expensive blur/glow/shadow/row animations, caps canvas DPR, uses a lighter line seekbar, skips palette extraction, and defers lyrics/media-session/theme work until after first paint.
- Fixed a major playback-lag hotspot: context counters no longer sort/filter thousands of tracks repeatedly during track load and playback ticks. Current album/folder counters are cached by library revision and use a single-pass fast path in performance mode.
- Added Replay Leveling. When real ReplayGain-like fields are present they are honored; otherwise DaddyAmp applies a conservative local genre-based gain estimate so loud modern tracks and quieter genres sit closer together.
- Android builds now call the native replay-gain bridge; web playback applies the same gain through the existing Web Audio preamp.
- Settings gained clean controls for Replay Leveling and Performance Profile without removing existing folder scan, swipe refresh, gesture, genre, queue, playlist, and player behavior.
- 10k-track smoke after the cache fix: 64 initial rows, Performance mode on, 0 JS errors, Britney/Local Device classified as Pop, portal open 0 ms, track load dropped from multi-second sort work to ~27 ms, context update ~1 ms, native replay-gain bridge called.
- Rebuilt signed APK as versionCode 33 / versionName 1.32.

## v1.33 — fluidity sweep: queue, indexes, and first-paint lag

- Removed more large-library jank from album/artist/genre tabs by avoiding per-group natural sorting for giant groups in Performance mode. Detail pages still stay identity-safe and stream rows instead of mounting everything.
- Track tab first paint now skips unnecessary full-library title sorting for huge libraries when the default order is already stable, while user-selected sort modes still work.
- Queue rendering is capped in both the full-player Queue stage and Queue sheet so playing a 5,000–10,000 track album/genre/folder does not create thousands of DOM rows.
- Track changes no longer rebuild the hidden in-player queue stage. It renders only when the Queue stage is actually visible.
- Current playing row updates now touch only the previous active row and current row instead of walking every mounted song row.
- Session restore for queued tracks now uses a path map instead of repeated linear searches, and session queue persistence is capped to keep localStorage writes fast.
- Folder/album/artist/genre “play all” paths avoid extra first-track queue shifts and reduce big-array copies.
- Added more containment and lighter image styling for Performance mode to improve scroll/touch smoothness in Android WebView.
- 10k-track JSDOM smoke: no JS errors, Performance mode on, Pop genre inference intact, album tab ~15 ms, giant album detail ~66 ms, giant play-album path ~136 ms with hidden queue rendering skipped, session queue capped to 300 paths.
- Rebuilt signed APK as versionCode 34 / versionName 1.33.

## v1.34 — restore waveform/adaptive colour + glitch cleanup

- Restored the Poweramp-style waveform seekbar in Auto/large-library mode. Performance mode still keeps list rendering lean, but it no longer silently downgrades the loved wavebar to a plain line.
- Restored the adaptive album-art colour atmosphere in Auto/large-library mode. Album colours now drive the player/app wash again after track changes.
- Kept explicit Speed mode available for worst-case devices, but made it the only mode that hard-disables palette extraction and ambient wall effects.
- Added a separate `speed-mode` CSS class so list performance optimizations no longer remove the app's signature visuals by accident.
- Fixed interface glitches caused by raw track text/IDs in row HTML: track titles, artists, albums, artwork paths, and queue rows are escaped before insertion.
- Fixed row-heart actions to use the row dataset instead of injecting raw ids into inline JavaScript.
- Revalidated a 10,000-track library: waveform setting remains `wave`, adaptive art URL is applied, Auto performance stays on for smooth lists, explicit speed mode stays off, row text is escaped, and 64 initial rows mount with no JS errors.
- Rebuilt signed APK as versionCode 35 / versionName 1.34.

## v1.35 — signature visuals protection + metadata glitch hardening

- Added a guardrail so future smoothness work does not silently remove the signature waveform or album-colour experience again.
- The Poweramp-style waveform is now cached per track/count, keeping it cheap enough to remain enabled in Auto performance mode.
- Performance Profile changes now immediately restore the current track's artwork atmosphere and redraw the waveform when switching back from Speed to Auto/Rich.
- Added a proper Dynamic Artwork Palette handler: toggling it now immediately applies the current album colours or manual accent instead of only changing a saved flag.
- Tightened metadata rendering in song rows and queue rows: titles, artists, albums, artwork URLs, and queue text are escaped to prevent broken layout from special characters.
- Revalidated a 10,000-track library: Auto performance stays on, explicit Speed is off, seek style remains Wave, adaptive artwork URL is applied, dynamic palette toggle works both ways, row metadata is escaped, 64 initial rows mount, and no JS errors appear.
- Rebuilt signed APK as versionCode 36 / versionName 1.35.

## v1.36 — upgrade recovery for waveform + stronger album-colour atmosphere

- Added an upgrade recovery pass for installs affected by the stripped visual builds: it restores Wave seek style, turns Dynamic Artwork Palette back on, and moves accidental Speed profile back to Auto once.
- Kept the manual controls afterwards; users can still choose Line, turn palette off, or choose Speed again deliberately.
- Made the waveform cheaper by keeping cached peaks and verified it draws in Auto performance mode.
- Strengthened the album-art colour wash so the player feels more “in your face” again: stronger accent ambient and stronger per-cover scrim tint.
- Revalidated old bad settings (`line`, dynamic palette off, Speed) upgrading into the new build: settings recovered to Wave + adaptive palette + Auto, waveform path rendered, artwork ambience applied, no JS errors.
- Rebuilt signed APK as versionCode 37 / versionName 1.36.

## v1.37 — visual richness + interface glitch cleanup continuation

- Made the restored waveform more visible by giving the scrubber a taller 44px canvas instead of the thinner 36px strip.
- Strengthened the player album-colour layer so adaptive cover colours feel vivid again instead of barely tinted.
- Continued interface glitch cleanup by escaping remaining album/folder/playlist artwork URLs and visible metadata in playlist/detail/history surfaces.
- Large playlist editing no longer mounts thousands of rows at once: Performance mode shows a smooth editable first window and a footer for the hidden remainder.
- Playlist row insertion now uses a DocumentFragment to reduce layout churn.
- Revalidated a 5,000-track broken-settings upgrade: Wave restored, Dynamic Artwork Palette restored, Speed reset to Auto, waveform draw path executed, artwork ambience applied, unsafe metadata escaped, 120 playlist rows capped, and no JS errors.
- Rebuilt signed APK as versionCode 38 / versionName 1.37.

## v1.38 — Android WebView fluidity + instant album-colour response

- Tuned the Android WebView host for smoother compositing: hardware layer, no scrollbars/overscroll glow, normal text zoom, default cache mode, mixed-content compatibility, and offscreen pre-raster on supported Android versions.
- Added instant per-song mood colouring before cover palette extraction finishes. Track changes now immediately tint the app from local genre/track context, then the real album-art palette replaces it when decoded.
- Kept the restored waveform active in Auto performance mode and verified the waveform draw path still executes after recovering from old bad Line/Speed/off-palette settings.
- Rechecked large-library fluidity: 10k-track smoke kept Auto performance on, Speed off, Wave restored, Dynamic Artwork Palette restored, portal open 0 ms, first track rows 64, and no JS errors.
- Rebuilt signed APK as versionCode 39 / versionName 1.38.

## v1.39 — UI/Motion polish pass

- Added a sliding underline indicator to the Library tab strip. Counts are lighter, separated from labels, and the active state now moves instead of hard color-swapping.
- Song rows now have larger 12px vertical padding, clean ellipsis behavior, and tactile press feedback with scale/opacity response.
- Album/row/mini/player thumbnails use consistent square cropping, 8px rounding, and shimmer skeleton placeholders while art loads.
- Bottom nav now uses a unified outline Search icon and animated icon/label scale/color transitions.
- Mini-player progress is now a visible 3px glowing accent line driven by transform for smoother updates.
- Mini-player swipe-up now springs back/expands with a longer physical curve and haptic feedback; existing left/right skip gestures remain.
- Full-player ambient background now breathes/drifts slowly on a 9.5s loop, keeping the restored album-colour atmosphere alive.
- Track-change art animation now scales in from 0.90 with a slight overshoot instead of hard-cutting.
- Art/Lyrics/Queue/Specs/Viz tabs now use a sliding/morphing pill indicator instead of a static active background.
- Waveform handle now has a soft accent glow, and bars near the playhead get a subtle pulse while playing.
- Transport/buttons/cards gained consistent press/ripple feedback; skip buttons get directional slide-bounce.
- Volume slider thumb now matches the waveform handle with accent glow on drag.
- Added motion-polish JS helpers for tab/pill indicators, bottom-nav active animation, thumbnail load states, and global tactile tap feedback.
- Revalidated 10k-track visual recovery: Wave restored, Dynamic Artwork Palette restored, Auto mode restored from old bad settings, waveform draw path and handle glow executed, subnav/stage indicators mounted, metadata escaped, 64 initial rows, 0 JS errors.
- Rebuilt signed APK as versionCode 40 / versionName 1.39.

## v1.40 — performance-first library strip-down + real transparent window

- Stripped the main Library screen down to a single Songs list. The Albums/Artists/Favorites/Recent/Folders/Lists/History tab strip, search/action strip, shuffle banner, and stats/codec banner are hidden from the main screen.
- Main song rows now render only two pieces of text: title and artist. No album art, no duration, no format badge, no favorite/menu rail, and no extra metadata clutter on the main list.
- Replaced the main song list's append-only windowing with a fixed-height recycler: only the visible row window plus overscan is mounted, and scrolling swaps the visible row range instead of accumulating thousands of DOM nodes.
- Removed album-art loading from the main list entirely, eliminating one likely scroll jank source. Artwork remains in the mini/full player and detail surfaces.
- Added a Wallpaper Background setting with adjustable Wallpaper Dim. When enabled, Android uses an actual translucent Activity/window + transparent WebView/background, not a fake blurred screenshot. The CSS dim overlay keeps text readable.
- Added a native Android bridge method `setWindowTransparency(enabled, dim)` and changed the Activity theme/layout/WebView backgrounds to support true transparency.
- Kept only minimal tactile row press feedback and the existing smooth now-playing transition; no new decorative animation work was added in this pass.
- JSDOM performance proof with 10,000 tracks: main rows mounted 27 initially, still 27 after scrolling deep; first visible row changed from Song 0 to Song 93; main rows contained no images and no duration nodes; stripped bars computed `display:none`; transparency setting toggled `transparent-bg`, dim set to 0.55, and native bridge call was observed.
- Rebuilt signed APK as versionCode 41 / versionName 1.40.

## v1.41 — covers restored + self-scroll/heat loop fix

- Restored album cover thumbnails in the main Library list while keeping the stripped layout: cover + title + artist only.
- Replaced the spacer-based virtual list with an absolute-positioned recycler. The old top/bottom spacer model could trigger browser/WebView scroll anchoring and appear as a self-scrolling/jumping list; the new model keeps one fixed total list height and translates only the visible row window.
- Main list remains minimal: no duration, no format badge, no album text, no favorite/menu rail, no counts, and no tab clutter.
- Disabled decorative infinite idle loops introduced by earlier polish: ambient drift, shimmer skeleton loops, and empty-state spinning are stopped for stability/heat. Playback-only indicators still only run while playing.
- Removed the requestAnimationFrame mini-progress loop; progress now updates from the existing playback sync path instead of running an extra frame loop.
- Kept true Android transparent-window support and dim setting from v1.40.
- JSDOM stability proof with 10,000 tracks: 25 visible rows mounted initially, 25 after deep scroll; album art present; no duration/trailing/format nodes; scrollTop stayed exactly 5800 after 25 repeated scroll events; first visible row changed to Song 94; idle RAF delta over 500ms was 0; transparency bridge still fired; 0 JS errors.
- Rebuilt signed APK as versionCode 42 / versionName 1.41.

## v1.42 — idle loop removal + recycler stabilization follow-up

- Removed the always-on Android playback sync interval. The 250ms MediaPlayer position timer now starts only while Android audio is actually playing and is cleared again on pause/error/stop, so the idle Library screen has no permanent interval wake-up.
- Kept album covers restored in the minimal main list: cover + title + artist only.
- Added a same-data render guard for the main Songs recycler so redundant `renderLibraryTab()` calls do not tear down/rebuild the list or reset the visible window when the track array reference has not changed.
- Disabled remaining decorative infinite loops that could keep GPU/CPU active during normal use: cover breathing, mini meter bounce, EQ row bounce, skeleton shimmer, ambient drift, and empty-state spin are overridden off for the stability build.
- Main recycler remains absolute/fixed-height to avoid WebView scroll anchoring. No spacer height mutation while scrolling.
- JSDOM profiling proof: idle intervals before/after 500ms = 0/0, idle RAF delta = 0, 25 rows mounted before and after deep scroll, album art present, duration/trailing controls absent, scrollTop stayed 5800 after 30 scroll events, playing starts one interval, pause clears it back to 0, no JS errors.
- Rebuilt signed APK as versionCode 43 / versionName 1.42.

## v1.43 — the "self-scrolling, lifeless, forgetful" triple kill

1. **Library scrolled itself forever.** Root cause: `overflow-anchor` is NOT
   inherited; v1.41 only disabled it on `.simple-virtual-list`, so any rebuilt
   row/img inside could still become the scroll anchor. Each rebuild changed the
   rows' translate → Chromium "compensated" scrollTop → `scroll-behavior:smooth`
   animated each compensation → more scroll events → more rebuilds: a
   self-sustaining downward drift. Side effects: rows shifted under taps
   (felt like "playing songs hallucinate") and covers flickered.
   Killed: `overflow-anchor:none !important` for the entire
   `.simple-virtual-list`/`.song-rows-catalog` subtrees,
   `scroll-behavior:auto !important` on `#library-scroll-container`
   (explicit `scrollTo({behavior:"smooth"})` calls still animate), translate
   writes skipped when unchanged. Suite E: scroll storms of 30 events never
   move `scrollTop`; window translate matches overscan math.
2. **Artwork gone everywhere (lifeless player).** Native fast-path
   (`buildSongFastFromStore`) emitted
   `content://media/external/audio/albumart/<id>` URIs; a WebView hosting a
   `file://` page refuses those on current builds → every cover fell back to
   `default.png`. Fix: `extractStoreAlbumArt()` decodes each album id once per
   run into the disk cache and emits `file://` (same cache/budget as the
   retriever paths). JS gate `sanitizeTrackArtwork()` neutralizes stale
   persisted `content://` art; one-time migration `maybeRunArtworkRecovery()`
   (versioned, `artRecoveryVersion`) re-scans enabled roots to repopulate —
   and the migration stamp is NOT in default settings so upgrades actually
   trigger it (the mistake v1.36's visual recovery made).
3. **Manual EQ sliders were silent on Android** — only WebAudio (desktop)
   filters moved. Now they drive `setNativeEqBand` (+ bass boost on band 0).
4. **Whole catalog could vanish on restart.** Done-phase persistence was
   gated on the done event's own batch being non-empty (`changed`); libraries
   ending on an exact batch boundary persisted nothing. Done now always calls
   `folderMergeNow()` (chunked, once per scan — v1.14 contract).
5. **No 2D canvas context = dead boot.** `getContext("2d")` null at top level
   aborted mount (`setTransform`). Null-object ctx + palette-pass guard.
