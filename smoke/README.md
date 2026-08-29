# DaddyAmp regression battery

Boots the **real** `web/index.html` in jsdom with a recording `AndroidBridge`
stub + fake-indexeddb, then drives the app like a user: root picks, scan
batches, taps, scroll storms, kills. The battery is the gate for every change
to the web layer.

## Run

```bash
cd smoke
npm install     # jsdom + fake-indexeddb (dev-time only, never shipped)
node smoke.mjs
```

Exit code 0 = green. `SMOKE_DEBUG=1` prints persistence/IDB introspection.

## What it covers (v1.43)

| Section | Guards |
|---|---|
| P0 | Harness sanity — inline handlers reach script scope |
| A | Boot, chrome structure (mini above nav), folder-first empty state |
| B | Folder-scan ingest (150 tracks), badge, virtualized rows, covers in rows, **persist-on-done with empty trailing batch** |
| C | EQ sheet → manual band reaches `setNativeEqBand` (fake-control guard) |
| D | CSS invariants for the runaway-scroll kill (`overflow-anchor` on *all* descendants, `scroll-behavior:auto` on scroller) |
| E | Scroll storms never move `scrollTop`; window translate math; no duplicate rows |
| F | Player art + mini thumb + ambient walls receive the cover; no `content://` art |
| G | Lyrics stage never seeks/pauses |
| H | Verified audio errors never auto-skip; honest toast only |
| I | App-kill simulation: catalog + session + playhead rehydrate |
| J | Transport: next advances deterministically |
| K | Artwork-recovery migration: fires once when needed, never re-scans when covered |
| L | Idle-loop sanity |
| M | Tap-exact playback identity incl. virtualization rebuilds, rapid taps, error storms |

Regressions this battery was rebuilt to cover (v1.42 → v1.43):

1. **Runaway self-scroll** — `overflow-anchor` is not inherited; rebuilt rows
   were still anchor candidates, and `scroll-behavior:smooth` animated the
   anchoring compensation → self-sustaining downward drift.
2. **Lifeless player (no artwork anywhere)** — native fast-scan emitted
   `content://media/external/audio/albumart` URIs the WebView can't paint from
   a `file://` page. Native now decodes to the on-disk art cache (file://, one
   decode per album), JS sanitizes persisted stale URIs, and a one-time
   migration re-scans to repopulate.
3. **Silent EQ sliders** — manual band moves never reached the native EQ.
4. **Intermittent catalog loss** — done-phase persistence was gated on the
   done batch carrying changes; exact batch-boundary scans lost everything.
5. **Canvas-less boot abort** — a missing 2D context killed app mount.

## Rules

- The battery must be green before every push/APK build.
- When you fix a bug, add its regression check first; reproduce, then fix.
- No network: `fetch` is stubbed. Do not break this — tests must be hermetic.