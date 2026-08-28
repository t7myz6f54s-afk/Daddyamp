# DaddyAmp → Poweramp-Grade Master Plan

Goal: make DaddyAmp feel like a premium, native, local-first Android music instrument while preserving what already works: fast large-library persistence, current playback stability, mini/full player identity, folder scanning, queue/playlists, lyrics, and hardware volume sync.

## Research baseline

Poweramp’s strongest UX patterns are not just visuals. They are a system:

1. **Player-first ergonomics**
   - Artwork gestures: left/right for track changes, up for lyrics, down/tap to return to current library category, long-press for context.
   - Middle control strip: visualization, sleep timer, track counter, repeat, shuffle.
   - Pro controls: previous/next track plus previous/next category.
   - Waveseek behind/near controls for precise thumb seeking.

2. **Library trust and scale**
   - Fast folder and library scans.
   - Folder hierarchy and library categories with metadata counts/durations.
   - Playlists survive storage/folder changes where possible.
   - Large libraries stay responsive.

3. **Audio authority**
   - EQ, tone, preamp, balance, replay gain, gapless/crossfade.
   - Clear audio info/processing chain.
   - Output-aware behavior.

4. **Customization without chaos**
   - Skins/themes, alternate layouts, static/wave seekbar, visualizer options.
   - Options are organized; premium means control, not random effects.

5. **Android-native integration**
   - MediaSession/lockscreen, headset/Bluetooth behavior, Android Auto, widgets, volume keys.

## Non-negotiable preservation rules

- Do not regress big-library persistence.
- Do not eagerly attach risky native AudioFX on playback start.
- Do not add startup work before first paint.
- Do not show playing animations unless audio is actually playing.
- Do not overload the mini-player; it is a control surface, not a full player.
- Every row/menu/play action must resolve by stable identity, not index guesses.

## Phase 1 — Premium interaction polish, low risk

Status: started in v1.18–v1.21.

- Mini-player strict layout zones: fixed art, clipped metadata, fixed time pill, fixed controls.
- Swipe-up mini-player expansion.
- Long-press rows/artwork context menu.
- Metadata chip cycling.
- Pull-to-refresh persistent refresh.
- Calmer press feedback.

Next safe tasks:
- Add tiny track counter mode to audio info chip: `23 / 10,248`.
- Add long-press repeat/shuffle quick choice sheets.
- Add current library context label: `Folder • Albums • Queue`.
- Add optional “Play and open player” setting for list taps.

## Phase 2 — Library category intelligence

- Category skip: previous/next album/folder from player controls.
- Current track in library: artwork tap/down opens the exact folder/album/artist context.
- Folder/album/artist headers show total tracks and total duration.
- Better sorting: album artist, disc number, track number, filename fallback, natural sort.
- Recently Added and Most Played “Show all”.

## Phase 3 — Audio confidence

- Detailed audio info chain screen: source file → decoder → DSP → output.
- ReplayGain read/use if tags exist.
- Output mode indicators: phone speaker, wired, Bluetooth, USB DAC if detectable.
- Per-device/per-output EQ presets after stability validation.
- Optional limiter/preamp visualization, but not enabled by default.

## Phase 4 — Visual system maturity

- Two built-in skins: Industrial OLED and Clean Graphite.
- Static vs wave seekbar setting already exists; make it cleaner and easier.
- Visualizer should be optional and restrained by default.
- Reduce ornamental glow on low-end devices or when large library scrolling.
- Replace generic copy with concise audio-product language.

## Phase 5 — Android-native completeness

- Headset/Bluetooth connect behavior settings.
- Long volume key track change setting if safely implementable.
- Notification/lockscreen artwork and action reliability pass.
- Widget support later, not before core stability.
- Android Auto later, after library/player model is stable.

## Current v1.21 focus

Fix remaining mini-player clunk:
- time must never collide with heart/play/next actions.
- metadata must ellipsize before controls move.
- decorative meter is optional and disappears first on narrow screens.
- heart disappears before play button on narrow screens.
- mini-player should feel like a tiny hardware deck: art, title, artist, time pill, play.

## v1.22 Phase 2 start — category intelligence

Implemented the first Poweramp-style category layer safely:
- Full player now has previous/next category controls around previous/next track.
- Category means folder when the song comes from a folder scan, otherwise album, then artist/library fallback.
- Header context is now actionable and shows `Type • Name • current/total`, e.g. `Album • Meteora • 3/13`.
- Tapping the context label jumps back to the current folder or album instead of dumping the user into a generic library.
- Audio info chip includes context-aware track counter modes.

Next in Phase 2:
- Add artist detail context jump.
- Add album/folder total duration chips in more surfaces.
- Add natural sort using disc/track metadata from native scanner when available.
- Add setting for list tap behavior: play only vs play and open player.

## v1.23 Phase 2 continuation — artist/detail sorting

Implemented next category-intelligence layer:
- Current context jump now opens artist detail when the playing song has artist context but no stronger album/folder target.
- Artist detail is now a proper windowed list render target, not a fragile move-from-default-render hack.
- Artist detail header now includes songs, album count, and total duration.
- Artist detail gained a shuffle action.
- Context sorting now uses album → disc → track number → natural title, so large artist/album views feel less random.
- Android MediaStore/device scan and folder scan now carry trackNumber where available, improving album order without changing playback identity.

## v1.24 Phase 2 continuation — library context polish

Implemented low-risk library polish from the roadmap:
- Album cards now show total duration alongside artist and track count.
- Artist cards now show song count, album count, and total duration.
- Folder cards now show total duration alongside track count.
- Long library totals now render as h:mm:ss instead of oversized minute counts.
- Album detail always sorts with the natural album ordering pipeline even if opened from search/context with an unsorted list.
- Added optional Playback setting: Tap Track Opens Player. Default remains off to preserve the existing list-browsing behavior; users who want a Poweramp-style immediate Now Playing jump can enable it.

## v1.25 design pass — less generated, more intentional

Implemented a human-facing design/copy pass plus Poweramp-style playback mode affordances:
- Removed overproduced/generated-sounding product copy from the visible app: no “flagship audio instrument”, “verified zero-emojis”, or HAL/Biquad jargon in About/Specs.
- Rewrote settings microcopy to sound direct and useful instead of synthetic.
- Kept the DaddyAmp identity, but made the empty/default labels calmer: “Ready”, “Startup recovered”, and plain audio wording.
- Added a clean Playback mode sheet opened by long-pressing Shuffle or Repeat.
- Playback mode sheet exposes Shuffle off, Shuffle library, Shuffle current album/folder/artist context, Repeat off, Repeat all, and Repeat one.
- Repeat/shuffle state now restores its button visuals after session restore.
- Changes preserve existing single-tap shuffle/repeat behavior, existing folder scans, library persistence, and mini-player layout fixes.

## v1.26 Poweramp gesture pass — intelligent mini-player + pinch list sizes

Implemented the researched Poweramp gesture behavior the user requested:
- Added pinch-to-zoom list density control with four levels: Text, Compact, Comfort, Large.
- Pinching inward makes song lists denser; pinching outward makes rows/cards larger.
- Added a visible List Size control in Look & Feel so the same behavior is discoverable without guessing the gesture.
- List density persists in settings and applies on restart.
- Album/artist/folder grids also respond to density so browse views feel like Poweramp List Options/View As behavior.
- Mini-player is more context-aware: state line now shows current album/folder/artist position, with the context label kept separate from title/artist/time zones.
- Mini-player horizontal swipe now has two levels: short swipe = previous/next track; longer committed swipe = previous/next album/folder/category.
- Mini-player swipe down opens the current album/folder/artist context; swipe up still opens Now Playing.
- Kept strict mini-player collision zones from v1.20/v1.21 to avoid the old time/heart overlap bug.

## v1.27 fluid gesture pass — player minimize, lyrics/artwork, smoother pinch

Researched Poweramp player/list gestures and implemented the next interaction pass:
- Full player now supports a fluid downward drag from the header/deck to minimize back to the mini-player, with live transform feedback instead of a sudden jump.
- Artwork gestures now feel more tactile: swipe up on artwork opens lyrics; swipe down on artwork minimizes; left/right still skips tracks.
- When lyrics are open, a downward swipe on the player stage returns to artwork, making lyrics/artwork feel like two sides of the same panel.
- Pinch-to-size was rewritten to be lighter: no list re-render during finger movement, just a tiny compositor-only preview scale plus a small List Size HUD.
- Pinch threshold was reduced slightly and the code now uses one requestAnimationFrame-backed move pipeline instead of duplicate touchmove handlers.
- On release, the list density changes once and persists; this should avoid lag on large libraries.
- Kept all previous large-library, folder scan, mini-player collision, category swipe, and playback behavior intact.
