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
