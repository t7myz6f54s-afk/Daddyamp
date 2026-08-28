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

## v1.28 major navigation update — swipe-in Library Home portal

Implemented the major Poweramp-style navigation layer requested by the user:
- Added a swipe-left Library Home portal, modeled after Poweramp's category-first Library Home Page and swipe-based list/player navigation.
- Portal shows animated category cards for All Songs, Folders, Albums, Artists, Genres, Playlists, Favorites, Recently Played, Queue, and Search.
- Swipe right from normal library lists quickly returns to Now Playing when a track exists.
- Swipe right inside the Library Home portal fluidly closes it back to the previous list/mini-player layer.
- Portal uses live drag feedback, staggered card entrance animations, glass/tinted cards, and a sticky current-track return card instead of a basic drawer.
- Added real Genre browsing: genre grid, genre detail view, play genre, and shuffle genre.
- Kept existing subnav tabs intact so older navigation muscle memory still works.
- Preserved mini-player, player gestures, pinch list size, folder scans, refresh, persistence, and category skip behavior.

## v1.29 hotfix/performance update — instant Library Home portal

Fixed the user's reported portal lag/unusable swipe behavior:
- Removed all heavy synchronous work from opening the swipe-left Library Home portal.
- Portal is now prebuilt during idle time and opens immediately from cached 10-card DOM.
- Counts/genre/folder/history metrics update asynchronously after the panel is already visible, instead of blocking the gesture.
- Replaced open-time genre indexing and folder indexing with a tiny cached metric snapshot.
- Swipe-left now gives live follow-your-finger portal feedback as soon as horizontal intent is detected.
- Lowered the open threshold so a normal, casual swipe opens the portal reliably.
- Reduced expensive visual effects during the portal transition: lighter shadows, shorter stagger, containment, and will-change/backface layer hints.
- Added Android back handling for the portal.
- 10k-track JSDOM performance smoke: openLibraryPortal synchronous path dropped to ~1 ms after priming; portal still renders 10 cards and updates counts correctly.

## v1.30 major polish — smart genres + large-library smoothness

Implemented a major bug-kill/performance pass based on the user's feedback:
- Fixed Genres showing as only generic Local/Device/Folder buckets.
- Native Android device MediaStore scan now carries real MediaStore genre tags into JS instead of hardcoding Device Audio.
- Native folder MediaStore fast-scan now carries real MediaStore genre tags too.
- Folder retriever fallback now emits blank genre when no real tag exists, letting JS infer instead of collapsing everything into Folder Audio.
- Added smart genre normalization: aliases such as hip hop/rap, rnb/R&B, edm/dance, lofi, soundtrack, qawwali, naat, nasheed, Quran, etc. collapse into clean genre names.
- Added fast local genre inference from title/artist/album/folder/path when tags are missing or generic. This uses deterministic heuristics only; it does not upload audio or call an online AI service.
- Existing persisted tracks are normalized/inferred on load, and newly imported/device/folder tracks are classified on ingestion.
- Genre index is now cached by songs revision instead of rebuilt every time.
- Huge libraries now get a large-library smoothness mode: row/card entrance animations are disabled after 1200 tracks.
- Initial row mount was reduced dynamically for large libraries and now uses DocumentFragment batching to cut layout churn.
- 10k-track smoke: initial mounted rows reduced to 64, large-library class enabled, portal open stayed ~1 ms, and smart genre view produced multiple useful genres.

## v1.31 honesty/performance pass — background bleed fix + stronger genre intelligence

Implemented another bug-kill pass based on direct user feedback:
- Fixed swipe portal/player background bleed by making the Library Home portal and full player use opaque base backgrounds, and by stopping portal drag from fading transparent over the library.
- Added a much stronger local artist-to-genre hint table for famous artists and common libraries, including Britney Spears -> Pop.
- Smart genre inference now checks artist identity before keyword fallback, so well-known artists are classified even when title/album/path are generic and metadata says Local Device/Device Audio.
- Added broader rules for Pop, Hip-Hop/Rap, R&B/Soul, Bollywood, Qawwali, Rock, Metal, Electronic, Jazz, Classical, Soundtrack, Reggae, Country, etc.
- Retested the reported Britney-style case with 10,000 Local Device tracks: all 10,000 were classified as Pop, 0 remained Unsorted.
- Added more large-library jank reduction: disable heavy mini-player blur/artwork reflection and ambient glow in large-library mode; lighter shadows; portal remains opacity 1 while swiping.
- Kept deterministic local inference only; no online upload, no network classification, no fake AI claims.

## v1.32 Poweramp-research premium pass — smoothness first, honest audio control

Implemented from the latest Poweramp premium research without making fake claims about unsupported engine features:
- Added a user-visible Performance Profile: Auto, Speed, Rich. Auto protects huge libraries automatically; Speed forces the lean path; Rich preserves full visual polish on faster devices.
- Added a lean rendering pipeline for big libraries and lower-end WebViews: no blur/glow/shadow-heavy surfaces, capped canvas DPR, simple seek rendering, no palette extraction in speed mode, and deferred lyrics/theme/media-session work after track-change paint.
- Killed a real clunk source in playback: current context counters and category groups are now cached by `songsRev`; performance mode no longer sorts thousands of same-album tracks during `loadTrack()` or timer updates.
- Added Replay Leveling as a practical premium-style DSP control. It honors ReplayGain-like metadata when available and otherwise uses conservative local genre estimates. Android routes it through the native bridge; web routes it through the existing preamp gain.
- Kept the existing non-regression rules: no fake startup playback animation, no removed folder scanning, no removed swipe-down refresh, no AI/cloud classifier, no index guessing for playback identity.

Validation added for this pass:
- JS syntax check for the extracted inline script.
- JSDOM large-library smoke with 10,000 tracks: Pop genre inference, 64 initial rows, Performance class enabled, portal open 0 ms, track load ~27 ms, context update ~1 ms, replay-gain bridge called, no JS errors.
- Android APK build/sign/align verification before release.

## v1.33 fluidity sweep — less work per touch

Implemented another Poweramp-like responsiveness pass focused on removing invisible work:
- Big album/artist/genre indexes no longer sort every giant group just to draw category cards in Performance mode.
- Track list first paint avoids an unnecessary full-library default sort for huge stable libraries.
- Queue UI is capped and lazy: large queues remain playable, but hidden queue panels do not rebuild on every track change.
- Playing-row updates are now targeted instead of scanning every mounted row.
- Session queue writes/restores were trimmed so long queues do not make track changes feel sticky.
- Performance-mode CSS now adds containment and removes remaining image shadow cost.

This keeps the core Poweramp-inspired behavior while making the UI do less work on every tap, swipe, track load, and tab change.

## v1.34 correction — keep the beautiful parts while staying smooth

User feedback: the performance pass made the app feel stripped because the waveform seekbar and adaptive album-art colour wash were effectively removed in large libraries.

Fixes:
- Auto performance mode now targets list/index/queue work, not the signature player visuals.
- Waveform seekbar is restored unless the user explicitly selects the Line seek style.
- Album-art adaptive colour extraction is restored in Auto/Rich mode.
- Explicit Speed mode remains for weak devices and is the only mode that removes palette/ambient effects.
- Escaped row and queue HTML to reduce visible interface glitches from special characters in song metadata.

Direction going forward: optimization must not delete beloved identity features. Smoothness work should remove invisible work first: sorting, hidden rendering, repeated scans, excessive DOM, and storage writes.

## v1.35 visual-protection pass

Added a non-regression rule in code and behavior: smoothness work must preserve the waveform and adaptive album-colour atmosphere unless the user explicitly chooses Speed/Line/off.
- Cached waveform peaks so the wavebar remains visually rich without repeated per-frame recomputation.
- Made performance-profile switching reapply artwork ambience and redraw the scrubber immediately.
- Made the Dynamic Artwork Palette toggle active immediately, not just persisted for later.
- Escaped row/queue metadata as another interface-glitch hardening pass.

## v1.36 visual recovery release

Added a one-time recovery for anyone who installed the over-optimized builds: DaddyAmp will restore Wave seekbar, Dynamic Artwork Palette, and Auto performance profile automatically. This protects the app's visual identity while keeping all invisible lag reductions.

## v1.37 fluid visual continuation

Further corrected the visual regression without giving back the old lag:
- Waveform is taller and more visible.
- Album-art colour atmosphere is stronger.
- More metadata/artwork paths are escaped to prevent interface glitches from unusual tags.
- Large playlist detail is capped and fragment-rendered in Performance mode so queue/list smoothness work extends to playlists too.

## v1.38 continuation — native shell smoothness + instant colour

Continued the smooth/fluid push without deleting signature visuals:
- Android WebView is now explicitly hardware-composited, de-scrolled, and configured for stable local-app rendering.
- Album-colour feel no longer waits for image decode; DaddyAmp applies an immediate per-song mood tint, then refines it from the cover palette.
- Waveform and adaptive colour remain protected defaults after upgrade recovery.
