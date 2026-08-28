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
