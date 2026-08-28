# DaddyAmp Poweramp-Seamless Refinement Map

Research references used:
- Poweramp Player UI guide: artwork gestures, waveseek, bottom navigation, metadata cycling, and contextual menus.
- Poweramp Equalizer feature profile: configurable EQ, visualizers, replay gain, limiter/preamp/balance, and UI customization.
- Poweramp feature summaries: fast library/folder handling, CUE, lyrics, playlists, deep Android integration.

## What DaddyAmp already does well

1. **Strong visual identity** — dark industrial UI, ambient artwork backdrop, OLED-friendly materials, and mini/full player split.
2. **Local-first Android bridge** — native MediaPlayer playback, MediaSession, hardware volume bridge, folder picker, device scan, haptics.
3. **Advanced music features already present** — queue, playlists, folder browser, lyrics, sleep timer, EQ controls, crossfade hooks, bookmarks, tag editor.
4. **Large-library foundation** — virtualized rows/grids and IndexedDB persistence for big catalogs.
5. **Poweramp-like gestures in progress** — mini swipe, artwork swipe, lyrics stage, folder scanning.

## Biggest gaps vs Poweramp feel

1. **Instantness** — Poweramp feels ready immediately. DaddyAmp must avoid heavy work on first paint: no scan, lyrics, palette extraction, or native playback touch until needed.
2. **Library trust** — scans must persist and visibly load all tracks. For 10k+ libraries, IndexedDB + MediaStore fast paths are mandatory.
3. **Context everywhere** — long-press on artwork/rows should open the same track menu; three-dot actions must resolve by stable identity.
4. **Player information density** — Poweramp lets metadata/audio info cycle without opening a settings screen. DaddyAmp needs tappable info chips.
5. **No fake playback state** — a selected/restored row must never animate like it is playing unless audio is actually playing.
6. **Calmer motion** — premium feel comes from subtle response, not constant large animations or full-screen blur.
7. **Refresh behavior** — pull-to-refresh should refresh persistent folders and MediaStore, and results must survive restarts.

## Work started in v1.18

- Added tappable/cycling audio info chip.
- Added row long-press context menu behavior.
- Added artwork long-press context menu behavior.
- Fixed play button after cold boot: if the selected track was only hydrated visually, pressing play now loads the track instead of calling resume on an empty native player.
- Changed session restore so non-playing sessions hydrate cheaply and do not run full `loadTrack()` on startup.
- Pull-to-refresh now refreshes folder roots and Android MediaStore through persistent paths.
- Added calmer touch feedback and reduced clunky transitions.

## Next refinement queue

1. Add category-skip buttons/gestures: previous/next album/folder, not only previous/next track.
2. Add real track counter mode in the player chip: `023 / 10000`.
3. Add per-output DSP presets once native stability is confirmed.
4. Add a compact first-run assistant explaining folder scan, player gestures, and DSP.
5. Add smarter album artist grouping and disc/track-number sorting.
6. Add persistent queue/session restoration with track identity rather than only path.
7. Add Android notification artwork fallback improvements.

## v1.19 mini-player premium target

Poweramp-like mini-player should behave as a small physical control deck, not a generic generated bar:
- Floating glass card above bottom nav, not a flat full-width rectangle.
- Artwork-tinted but subtle, with real album art context and no noisy neon overuse.
- Clear state/time line so the user knows whether it is ready, paused, or playing.
- Thumb-first controls: favorite, play/pause, next, with large enough targets.
- Gesture loop: horizontal swipe changes tracks, vertical swipe-up opens Now Playing.
- Playing indication must be restrained: tiny LED + 5-bar meter only while audio is playing.
- Metadata chip and mini-player should feel connected to the full player through shared artwork and accent.
