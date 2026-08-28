# SCAN_BG + POWERAMP UI PROMPT (B-track — resumes only after P0 proof is green)

> Delivered inline; preserved here for the record. Status: implemented in v1.12 (after the
> identity proof went green), locks honored.

## §1 Scan: background-only, zero chrome
- Remove the permanent top "Reading music / folder done" banner. Scans run background-only
  (chunked — keep the chunking).
- No blocking UI, no sticky done strip. Optional **one-shot** progress only: Library
  empty/header or a 2px line under the app bar; later rescans silent or settings-only.
- Errors inline in Music Folders. Now Playing stays clean.
- **LOCK (may change only list virtualization, queue identity, scan scheduling):**
  folders persistence, lyrics live background, waveseek, gestures, 4 nav, mini above nav, schema.

## §2B Scale: 100+ tracks lag/crash/wrong-song
- Virtualize lists: ~15–30 rows mounted, `key={id}` not index.
- Off-main-thread cover decode + thumbnail cache, cap 2–4 concurrent jobs.
- Immutable queue snapshot at play time. Target: smooth at 500–2000+ tracks.

## §3 Poweramp UI polish
- OLED ladder, 8px rhythm, cover 55–62%, 44px touch targets, play 1.35×,
  motion tokens 90/180/380/420/600, no leftover banner-region header, no fake controls.

## End state
`FEATURE_LOCK.md`, regression battery, don't break what works.

## Implementation notes (v1.12)
- Banner pill fully removed (CSS+HTML+JS). Inline status: "Scanning… N tracks" in Music
  Folders settings rows and folder cards while running. Completion: no strip, no toast unless
  error or added/removed > 0. Scan errors inline.
- Catalog: 80-row chunks + IntersectionObserver sentinel (600px margin), lazy/async art,
  identity via dataset songId/path. Queue identity patched; scans never play.
- Now Playing: cover 60vw ≤400px r16, mini play 50px, NP play 68px (1.35× mini), tokens kept.
- Remaining known gaps: FEATURE_LOCK.md not written, full 500–2000 physical-device perf
  measurement pending, off-main-thread decode is delegated to the WebView/`decoding="async"`.
