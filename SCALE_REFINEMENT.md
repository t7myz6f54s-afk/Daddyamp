# SCALE & REFINEMENT — why the app felt unfinished, and what changed (v1.14)

> User report: 500+ song folder scan "takes forever"; 10,000+ songs across 3 folders is
> unusable; the app is clunky and laggy. This pass = correctness + scale + polish. No new features.

## Why it was slow — four compounding causes (verified by reading the code)

### 1. The scanner opened `MediaMetadataRetriever` per file, serially  ← the big one
`FolderEngine.buildSong()` did, for **every** file: `openFileDescriptor` + `setDataSource` +
8× `extractMetadata` + `getEmbeddedPicture`, one after another on a single thread.
A retriever open on a large FLAC/WAV/MP3 often takes 50–300 ms → **10,000 files ≈ 10–50
minutes**, and 500+ files already felt endless. Art extraction added a second full decode
pass per file (deduped only per album *key*, and only if the disk cache missed).

**Fix:** a **MediaStore fast path**. One query of `MediaStore.Audio.Media` (scoped to the
folder tree by `DATA LIKE <tree path>/%`) → in-memory map `path → {title, artist, album,
albumId, duration, year, mime}`. SAF document IDs are decoded back to filesystem paths
(`primary:Music/x.mp3 → /storage/emulated/0/Music/x.mp3`), so walking still uses the same
recursive DocumentsProvider traversal, but metadata for indexed files now comes from the
map — **zero file opens**. Only files MediaStore doesn't know (or with no duration) fall
back to the retriever (rare). Album art: extracted at most **once per album id** (disk-cached
`art/<sha1>.jpg`, one retriever open per album instead of per track), keyed by `ALBUM_ID`
instead of a string. Batch emit raised 90 → 200 songs.

### 2. Every 90-song batch triggered a full-library persist + rebuild  ← O(n²)
`onFolderScanProgress` → per batch → `folderMergeSchedule()` → after 900 ms →
`persistFolderSongs()` (**serialize ALL folder songs + write to IndexedDB in 400-item
chunks**) + `folderApplyToLibrary()` (**rebuild `state.songs` = filter ≯ concat 10k +
`refreshBadges()` recomputing albums/artists over 10k**). Scanning 10k songs = ~50 batches
→ ~50 full persists + rebuilds: seconds of CPU churn and constant main-thread jank **while
scanning** — the app felt frozen and the scan seemed slower than it was.

**Fix:** the walk phase now merges **lightly** — batches accumulate into the `folderState`
map (cheap), one throttled (2 s) `folderApplyToLibrary()` applies to the visible library;
**one** full `persistFolderSongs()` happens **only at scan done** (chunk 400 → 1000).
Also added `folderState.rev` + a size guard so unchanged scans perform **zero** rebuilds
(the apply is skipped entirely when nothing changed).

### 3. Album/artist/folder views built one DOM card per album/artist/folder
10k songs ⇒ ~1,250 album cards / ~1,500 artist cards / thousands of folder cards, all
`createElement` + `innerHTML` + image request at once ⇒ multi-second render, memory spikes,
layout thrash. Album/artist *detail* views built every track row at once too.

**Fix:** a shared **windowed grid** (`renderWindowedGrid`, 36 cells mounted +
load-more sentinel, same IntersectionObserver as the catalog) — albums, artists and the
folder grid now mount ≤36 cards and stream the rest on scroll. Album/artist detail views
use the virtualized `renderSongsCatalog` with a track-number `leadHtml`, so a 500-track
artist page mounts ≤80 rows.

### 4. Everything re-derived from the whole library on every event
- `applyCurrentSort` **sorted all 10k on every render** (every tab switch, every render call).
- `refreshBadges` rebuilt albums/artists Sets over all songs on **every call** (scan batches,
  play, favorites, imports…).
- `buildFolderIndex` rebuilt per render; `folderSongs()` serialized the map on every call
  (incl. per keystroke in search); `handleInstantSearch` filtered + re-rendered **every
  keystroke**; global search also ran `state.songs.indexOf(s)` **inside an inline onclick**
  (identity bug: tap could play the wrong song after a reorder) and scanned 10k for
  folder files per keystroke.

**Fix:** a single `state.songsRev` version counter bumped only where `state.songs` actually
changes; memoized `libraryTracks()`, `applyCurrentSortCached`, `refreshBadges` (1 loop,
cached counts), `albumIndexEntries()`, `artistIndexEntries()`, `buildFolderIndexCached()`.
Search: 160 ms debounce, capped results (150 instant / 80 songs, 12 albums, 12 artists,
8 folders), "showing first N" footer, and search taps now resolve **by track id**
(`playSongFromSearch` → `resolveSongByID`, which was also fixed to compare id *as string* —
search rows pass string ids, the old strict `===` silently missed every hit).

## What the user experiences now
1. First scan of a big folder: MediaStore-backed, roughly a walk + one DB batch — expected
   tens of seconds for 10k files instead of tens of minutes; playback and UI stay fluid
   (batches don't rebuild the library; merges are throttled and skippable).
2. Incremental rescans: no retriever opens at all for unchanged files (`mtimesize` short-circuit).
3. Library: 10k tracks render instantly (≤80 rows), albums/artists/folders stream (≤36 cards),
   search is debounced + capped, badges/sort/folder index are O(1) after first compute.
4. After a scan, the current track and queue stay put (URI remap, identity contract kept).
5. Playback honesty (v1.13 verified-error engine) unchanged; no new UI surface was added.

## Verification — `/home/user/smoke/smoke.mjs` → 145/145
New section O "SCALE refactor proof": 10k-track render <2 s (jsdom), catalog rows windowed
≤80, sort memoized + invalidates, album grid ≤36 + streams, badges cached + recompute on
rev, **500-batch scan storm → one throttled merge, no per-batch rebuild**, search debounced,
search play resolves by id. All previous suites (identity proof, verified-error, honest
scan, folders, atmosphere) still green.

## Lock kept
No features added. Scan = background-only (no banner), folders/schema/lyrics/waveseek/4-nav/
mini-above-nav untouched, single MediaPlayer + single `<audio>`, tap-exact playback.
