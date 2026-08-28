# Combined agent prompt: Poweramp UI + Living Now Playing + Folder Library

This replaces and merges: (1) Poweramp-grade fluid UI restyle, (2) Living Now Playing
(premium vibe), (3) **Poweramp Music Folders** — pick folders (directories that contain
many songs), never a thousand individual files; library **survives app restart**.

## Mission
Restyle like Poweramp (OLED, art-blur, 120 Hz motion). Now Playing alive (palette, vis,
haptics, lyrics). Library is folder-rooted: user grants one or more music folders,
app scans recursively, persists roots + catalog, and on every launch the music is still
there — same as Poweramp Settings → Library → Music Folders.

Hard rules: folder is the primary import (never bulk multi-file pick); no library loss
on kill/restart (persistence is a bug if missing); no social/AI/discover; no feature
drops; reduce-motion, contrast, 44-48px targets apply.

## Feature A — Poweramp folder system
- Pick top-level music folders (SAF ACTION_OPEN_DOCUMENT_TREE + takePersistableUriPermission
  on Android; directory picker on desktop/web). Subfolders included by default.
- App-owned independent library (not the OS media store as source of truth).
- Roots stored; no re-picker on next open. Optional rescan while app is in use.
- Large libraries scan in chunks; show progress ("Reading "Music"... 1,240 tracks"), don't freeze UI.
- Ignore junk: ringtones, notifications, tiny clips ("ignore short tracks" toggle).
- Folders = first-class Library category; hierarchy mirrors disk tree.
- User actions: Add folder / see roots (path, count, last scan, enable toggle) /
  Remove root (un-index only, never delete files) / incremental rescan + full rescan /
  browse Folders tree (root → subfolders → tracks), play a folder = play subtree.
- Forbidden UX: multi-select mp3 picker as main CTA; copy-into-sandbox as the only path;
  empty library after restart; re-asking permission every launch if OS still grants it.
- Persistence (non-negotiable): root URIs + names; OS persistable permission
  (takePersistableUriPermission); indexed tracks (path, title, artist, album, album
  artist, duration, mtime, size, codec, embedded-art cache key, folder_id); folder tree
  nodes; user toggles (enabled roots, ignore-short, last scan); player state (queue,
  last track, playhead, repeat/shuffle, EQ, look-and-feel). Real DB (SQLite/IndexedDB),
  not memory, not a JSON file you forget to reload.
- Startup: open DB → paint last session immediately; restore folder permissions (if
  revoked show "Folder access lost — tap to restore", do NOT wipe index until user
  confirms); background incremental scan; never block first frame on full scan.
- Restart test (acceptance): add folder with many albums → force-kill → reopen → same
  folders, same tracks, last song, playhead. Fail = not shipped.
- Scanner: recurse from enabled roots; supported audio extensions; skip ._, Thumbs.db,
  hidden; optional skip < N sec; read tags; embed art cached on disk (not re-decoded
  each scroll); chunked + progress; only user-chosen roots (never whole device).
- Library IA: Folders (default after first import) · Albums · Artists · Tracks ·
  Playlists · Queue · Genres (if tagged). Folders screen: row per directory (icon or
  art mosaic, name, count); drill in; header play + shuffle; breadcrumbs; long-press
  play/queue/exclude. Empty state: primary "Add music folder", subtext about albums,
  secondary "How it works" sheet, no "select files" as primary.
- Settings analogue: Library → Music Folders: roots list w/ toggles + counts, add
  folder, rescan / full rescan, ignore short tracks, auto-scan on launch (default on).

## Feature B — Living Now Playing (same milestone as folders)
- Layer 1 Atmosphere: 4–6 colors from cover → scrim, accent, mini-player, nav, seek
  played region. Crossfade 500–700 ms on track change. Extra scrim on bright art.
  Contrast ≥ 4.5:1.
- Layer 2 Pulse: play morph; light RMS (or gentle) breath on cover/play; haptics on
  skip/play/seek, off if reduce-motion.
- Layer 3 Visualization: utility-row toggle; soft spectrum or ring, accent, 60 fps,
  pause when backgrounded / reduce-motion. No rainbow rave.
- Layer 4 Lyrics: swipe up on cover; synced lines if timestamps, else static; quiet
  empty state.
- Fold-in: waveseek (wavebars), mini-player accent hairline.

## Feature C — Poweramp visual / motion language (restyle)
- Not generic Material, not Spotify editorial. OLED ladder void #050506 → surfaces
  #0C0C0E / #141418 / #1C1C22, text #F2F2F5, hairlines white 8%, desaturated accent from
  art; fallback amber #E8A54B or cyan #7EC8E3 (pick one → cyan chosen).
- Full-bleed blurred cover wallpaper + dark scrim on player, muted on library.
- Glass nav + mini-player rgba(12,12,16,0.55–0.72) + blur 20–32; radius 20–28 cards;
  play 56–72 circle.
- 4-item nav: Library · Equalizer · Search · Menu. Mini-player above nav.
- Player: cover (swipe L/R skip, down = library, up = lyrics) → title/artist →
  vis/sleep/repeat/shuffle → waveseek + pro buttons → times → tappable metadata → nav.
- Motion: micro 90 / ui 180 / sheet spring ~380 / hero 420 / ambient 600; only
  transform+opacity on hot paths; shared-element cover list → player → mini.
- Type: one sans; tabular times. Giant miss-proof play. No spreadsheet tabs.
  No layout animation jank.

## Build order
1. DB + Music Folders persistence + recursive scan + Folders browser + empty state +
   restart test. 2. Tokens + player chrome + nav + mini-player wired to real scanned
   tracks. 3. Living Now Playing (palette, waveseek, vis, lyrics, haptics). 4. Lists
   (Albums/Artists/Tracks) from same index. 5. EQ sheet (already in product).
   6. Look & Feel: blur, seek style, vis, reduce-motion.

## Acceptance
1. Primary import is folder. 2. Subfolders + all audio inside appear without picking
each file. 3. Kill → reopen: roots, tracks, last track + playhead still there.
4. Remove root = gone from library, files stay on disk. 5. Permission lost → restore
CTA, index not silently wiped. 6. Scan chunked; UI stays fluid. 7. Folders category
matches disk tree. 8. Track change re-tints chrome, morphs art, no flicker.
9. Waveform seek, play morph, shared-element collapse to mini-player.
10. Vis/pulse 60fps while playing; off when paused/background/reduce-motion.
11. Four-item glass nav; "feels like Poweramp."

## Copy
CTA "Add music folder"; sub "Pick a folder with your albums. Everything inside is
scanned and kept when you reopen the app."; scanning `Reading "Music"… 1,240 tracks`;
access lost "Folder access lost — tap to restore"; remove "Remove from library? Files
on your device won't be deleted."; ignore short "Skip very short clips (notifications,
ringtones)". Tone: confident, audiophile, no "Let's jam!".

## Platform notes
Android: ACTION_OPEN_DOCUMENT_TREE + takePersistableUriPermission + tree walk.
Desktop: directory path + bookmark/security-scoped URL; watch mtimes.
Web: showDirectoryPicker() + persist handles in IndexedDB where allowed; if handles die,
one-tap re-grant without wiping DB. Always bind preview servers to 0.0.0.0; relative URLs.

## Non-goals this milestone
AI DJ, social, per-file import as default, copying entire libraries into app storage as
default, scanning whole disk unattended, rainbow vis, new IA unrelated to music.

## EXECUTION STATUS (v1.9 — shipped)
Implemented as one release (Android build v1.9, versionCode 10, commit see git log):
- Native FolderEngine.java: SQLite roots+files tables, SAF tree walker, tag/art
  extraction with disk art cache, chunked progress callbacks, incremental mtime/size
  diff, vaulted permission lifecycle; bridge methods pickFolderTree/getFolderRoots/
  scanFolder/removeFolder/setFolderEnabled/vibrate; MainActivity FOLDER_TREE_REQUEST +
  persistable grant + JS confirm/alert dialogs; VIBRATE permission; MediaSession art
  decode for cache file:// URLs.
- Web: IndexedDB catalog + roots (daddyamp_db), folder-roots browser (cards, drill,
  breadcrumbs, play/shuffle, long-press menu), Music Folders settings section (roots,
  toggles, rescan/full rescan, ignore short, auto-scan), empty state "Add music folder"
  + How it works sheet, scan progress pill, access-lost restore CTA, session persistence
  (last track + playhead + queue + repeat/shuffle) with restore at boot, auto incremental
  rescan, legacy device/imports preserved under "OTHER FOLDERS".
- Living Now Playing: multi-color palette (accent + accent2 + scrim tint) with 600ms
  crossfade on accent-driven surfaces, cover breath (time-based + RMS-driven from
  visualizer data), haptics (play/skip/seek, gated by reduce-motion + toggle), cover
  swipe gestures (L/R skip, up lyrics, down collapse), mini-player accent hairline,
  cyan accent #7EC8E3 chosen.
- Nav = Library · Equalizer · Search · Menu (4 items); global search sheet with grouped
  results (songs/albums/artists/folders); Menu opens settings (features preserved).
- Smoke suite: 119/119 (jsdom + fake-indexeddb + bridge stubs incl. folder lifecycle,
  simulated restart via IDB reload, session restore, haptics gating, breathing class,
  palette vars, nav/search/drill checks).
