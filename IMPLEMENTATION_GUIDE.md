# DaddyAmp Premium Enhancement — Phase 1 Implementation

## 🎯 EXECUTE THESE CHANGES IN ORDER

### CHANGE 1: Add noise/grain texture to the app (premium feel)
Add this SVG filter to the `<head>` section of index.html, right after the `<style>` opening:

```css
/* Add this inside the <style> block, at the very top after :root { ... } */
```

Then add the grain texture class:

```css
/* =========================================================================
   GRAIN TEXTURE OVERLAY (Premium tactile feel)
   ========================================================================= */
.grain-overlay {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 9999;
  opacity: 0.018;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 512 512' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.75' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-repeat: repeat;
  background-size: 128px 128px;
}
```

Add this div right after `<div id="daddyamp-root">`:
```html
<div class="grain-overlay"></div>
```

---

### CHANGE 2: Enhanced Mini Player with format badge and swipe gestures

Replace the entire `#docked-mini-player` div with this enhanced version:

```html
<!-- DOCKED PERSISTENT MINI PLAYER -->
<div id="docked-mini-player" id="docked-mini-player" ontouchstart="miniSwipeStart(event)" ontouchmove="miniSwipeMove(event)" ontouchend="miniSwipeEnd(event)">
  <div class="mini-progress-accent-line" id="mini-progress-line"></div>
  <img src="artwork/default.png" alt="Cover" class="mini-cover-thumb" id="mini-thumb-img">
  
  <div class="mini-meta-col">
    <div class="mini-title-line" id="mini-meta-title">No track selected</div>
    <div class="mini-artist-line" id="mini-meta-artist">
      DaddyAmp
      <span class="mini-format-chip" id="mini-format-chip"></span>
    </div>
  </div>

  <div class="mini-trailing-actions" onclick="event.stopPropagation()">
    <button class="btn-inline-heart" id="mini-heart-btn" onclick="toggleActiveTrackFavorite()">
      <svg class="icon icon-md" id="mini-heart-svg" viewBox="0 0 24 24"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
    </button>
    <button class="mini-master-play-btn" id="mini-play-btn" onclick="togglePlayPauseState()">
      <svg class="icon icon-md icon-fill" id="mini-play-svg" viewBox="0 0 24 24"><polygon points="6 3 20 12 6 21 6 3"/></svg>
    </button>
    <button class="btn-action-tap" onclick="executePlayNext()">
      <svg class="icon icon-md" viewBox="0 0 24 24"><polygon points="5 4 15 12 5 20 5 4"/><line x1="19" y1="5" x2="19" y2="19"/></svg>
    </button>
  </div>
  
  <!-- Swipe hint indicator -->
  <div class="mini-swipe-hint" id="mini-swipe-hint">
    <svg class="icon icon-xs" viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"/></svg>
    <span>swipe to skip</span>
    <svg class="icon icon-xs" viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"/></svg>
  </div>
</div>
```

Add these CSS classes:

```css
/* Mini Player Format Chip */
.mini-format-chip {
  display: none;
  font-size: 9px;
  font-family: var(--font-mono);
  font-weight: 700;
  background: rgba(0, 229, 255, 0.15);
  color: var(--accent);
  padding: 1px 5px;
  border-radius: 3px;
  margin-left: 6px;
  letter-spacing: 0.5px;
  vertical-align: middle;
}

.mini-format-chip.visible {
  display: inline-block;
}

/* Mini Player Swipe Hint */
.mini-swipe-hint {
  position: absolute;
  bottom: -18px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 9px;
  font-family: var(--font-mono);
  color: var(--text-tertiary);
  opacity: 0;
  transition: opacity 0.3s;
  white-space: nowrap;
  pointer-events: none;
}

.mini-swipe-hint.visible {
  opacity: 0.6;
}

/* Pull-to-Refresh */
.pull-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px 0;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.pull-indicator.visible {
  opacity: 1;
}

.pull-indicator svg {
  color: var(--accent);
  animation: pullArrowBounce 0.6s ease-in-out infinite alternate;
}

@keyframes pullArrowBounce {
  from { transform: translateY(-4px); }
  to { transform: translateY(2px); }
}
```

Add swipe tracking JS:

```javascript
/* =========================================================================
   MINI PLAYER SWIPE GESTURES
   ========================================================================= */
let miniSwipeStartX = 0;
let miniSwipeStartY = 0;
let miniSwipeTracking = false;
let miniSwipeAccumDelta = 0;
const MINI_SWIPE_THRESHOLD = 60;

function miniSwipeStart(e) {
  const touch = e.touches ? e.touches[0] : e;
  miniSwipeStartX = touch.clientX;
  miniSwipeStartY = touch.clientY;
  miniSwipeTracking = false;
  miniSwipeAccumDelta = 0;
}

function miniSwipeMove(e) {
  const touch = e.touches ? e.touches[0] : e;
  const dx = touch.clientX - miniSwipeStartX;
  const dy = touch.clientY - miniSwipeStartY;
  
  if (!miniSwipeTracking && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
    miniSwipeTracking = Math.abs(dx) > Math.abs(dy);
  }
  
  if (miniSwipeTracking) {
    e.preventDefault();
    miniSwipeAccumDelta += dx;
    const player = document.getElementById("docked-mini-player");
    const clamped = Math.max(-60, Math.min(60, miniSwipeAccumDelta * 0.4));
    player.style.transform = `translateX(${clamped}px)`;
    
    // Show hint on first interaction
    const hint = document.getElementById("mini-swipe-hint");
    if (hint && !hint.dataset.shown) {
      hint.classList.add("visible");
      setTimeout(() => { hint.classList.remove("visible"); hint.dataset.shown = "1"; }, 2000);
    }
  }
}

function miniSwipeEnd(e) {
  const player = document.getElementById("docked-mini-player");
  player.style.transition = "transform 0.2s ease-out";
  player.style.transform = "translateX(0)";
  
  setTimeout(() => { player.style.transition = ""; }, 200);
  
  if (miniSwipeTracking && Math.abs(miniSwipeAccumDelta) > MINI_SWIPE_THRESHOLD) {
    if (miniSwipeAccumDelta < 0) {
      executePlayNext();
    } else {
      executePlayPrevious();
    }
  }
  
  miniSwipeTracking = false;
  miniSwipeAccumDelta = 0;
}
```

---

### CHANGE 3: Enhanced Empty States with Custom SVG Illustrations

Replace the empty state HTML in `renderSongsCatalog()` with:

```javascript
// Replace the empty state innerHTML with:
target.innerHTML = `
  <div class="empty-state-craft">
    <svg class="empty-state-illustration" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
      <!-- Vinyl record illustration -->
      <circle cx="60" cy="60" r="52" stroke="rgba(255,255,255,0.06)" stroke-width="1"/>
      <circle cx="60" cy="60" r="38" stroke="rgba(255,255,255,0.06)" stroke-width="1"/>
      <circle cx="60" cy="60" r="24" stroke="rgba(255,255,255,0.08)" stroke-width="1"/>
      <circle cx="60" cy="60" r="10" fill="rgba(255,255,255,0.05)" stroke="rgba(255,255,255,0.1)" stroke-width="1"/>
      <!-- Sound wave lines -->
      <path d="M85 50 Q90 45, 85 40" stroke="rgba(0,229,255,0.3)" stroke-width="1.5" stroke-linecap="round" fill="none"/>
      <path d="M90 55 Q97 48, 90 41" stroke="rgba(0,229,255,0.2)" stroke-width="1.5" stroke-linecap="round" fill="none"/>
      <path d="M93 60 Q102 50, 93 40" stroke="rgba(0,229,255,0.1)" stroke-width="1.5" stroke-linecap="round" fill="none"/>
      <path d="M35 50 Q30 45, 35 40" stroke="rgba(0,229,255,0.3)" stroke-width="1.5" stroke-linecap="round" fill="none"/>
      <path d="M30 55 Q23 48, 30 41" stroke="rgba(0,229,255,0.2)" stroke-width="1.5" stroke-linecap="round" fill="none"/>
      <path d="M27 60 Q18 50, 27 40" stroke="rgba(0,229,255,0.1)" stroke-width="1.5" stroke-linecap="round" fill="none"/>
    </svg>
    <div class="empty-state-headline">${emptyMsg || "Your library is empty"}</div>
    <div class="empty-state-sub">Import audio files or scan your device to get started</div>
    <div class="empty-state-ctas">
      <button class="btn-import-header" onclick="invokeAudioImport()">
        <svg class="icon icon-sm" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        Import Files
      </button>
      <button class="btn-import-header" style="background: var(--accent-soft); border-color: var(--accent);" onclick="invokeDeviceScan()">
        <svg class="icon icon-sm" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
        Scan Device
      </button>
    </div>
  </div>
`;
```

Add these CSS classes:

```css
/* Premium Empty State */
.empty-state-craft {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px 64px 20px;
  text-align: center;
  gap: 14px;
}

.empty-state-illustration {
  width: 100px;
  height: 100px;
  opacity: 0.8;
  animation: vinylSpin 8s linear infinite;
}

@keyframes vinylSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.empty-state-headline {
  font-size: 15px;
  font-weight: 750;
  color: var(--text-pure);
  letter-spacing: -0.2px;
}

.empty-state-sub {
  font-size: 12.5px;
  color: var(--text-tertiary);
  max-width: 280px;
  line-height: 1.55;
}

.empty-state-ctas {
  display: flex;
  gap: 10px;
  margin-top: 4px;
  flex-wrap: wrap;
  justify-content: center;
}
```

---

### CHANGE 4: Staggered Row Animations

In `renderSongsCatalog()`, add this staggered entrance after creating the list element:

```javascript
// After: listEl.className = "song-rows-catalog";
// Add staggered animation:

// Force reflow
listEl.offsetHeight;

const rows = listEl.querySelectorAll(".song-catalog-row");
rows.forEach((row, i) => {
  row.style.opacity = "0";
  row.style.transform = "translateY(8px)";
  row.style.transition = "opacity 0.22s ease-out, transform 0.22s ease-out";
  
  // Cap at 500ms total delay
  const delay = Math.min(i * 12, 500);
  setTimeout(() => {
    row.style.opacity = "1";
    row.style.transform = "translateY(0)";
  }, delay);
});
```

---

### CHANGE 5: Track Markers/Bookmarks System

Add to the state object:

```javascript
// Add to state:
markers: {}, // { [songId]: [{ time: number, label: string, id: string }] }
editingMarkerTrackId: null,
```

Add marker-related functions:

```javascript
/* =========================================================================
   TRACK MARKERS / BOOKMARKS
   ========================================================================= */

function addMarkerAtCurrentPosition() {
  const song = state.songs[state.currentIndex];
  if (!song) return;
  
  const time = state.currentTime;
  const markerId = "m_" + Date.now();
  const label = prompt("Bookmark label (optional):", `Moment at ${formatDuration(time)}`);
  if (label === null) return;
  
  if (!state.markers[song.id]) state.markers[song.id] = [];
  state.markers[song.id].push({ id: markerId, time, label: label || formatDuration(time) });
  state.markers[song.id].sort((a, b) => a.time - b.time);
  persistState();
  
  drawPowerampScrubber(true);
  displayToast(`Bookmark added: ${label || formatDuration(time)}`);
}

function removeMarker(songId, markerId) {
  if (!state.markers[songId]) return;
  state.markers[songId] = state.markers[songId].filter(m => m.id !== markerId);
  if (state.markers[songId].length === 0) delete state.markers[songId];
  persistState();
  drawPowerampScrubber(true);
}

function seekToMarker(songId, markerTime) {
  state.currentTime = markerTime;
  commitSeek();
}

function getMarkersForCurrentTrack() {
  const song = state.songs[state.currentIndex];
  if (!song) return [];
  return state.markers[song.id] || [];
}
```

Modify `drawPowerampScrubber()` to draw marker dots:

```javascript
// Add after drawing the position marker (inside drawPowerampScrubber):

// Draw track markers
const song = state.songs[state.currentIndex] || {};
const markers = state.markers[song.id] || [];
const duration = state.duration || 1;

markers.forEach(m => {
  const ratio = Math.max(0, Math.min(1, m.time / duration));
  const x = left + ratio * usableWidth;
  
  // Marker diamond shape
  ctx.save();
  ctx.fillStyle = "rgba(255, 51, 102, 0.85)";
  ctx.beginPath();
  const ds = 5;
  ctx.moveTo(x, centerY - ds);
  ctx.lineTo(x + ds * 0.7, centerY);
  ctx.lineTo(x, centerY + ds);
  ctx.lineTo(x - ds * 0.7, centerY);
  ctx.closePath();
  ctx.fill();
  
  // Label on hover (simplified: always show for active area)
  if (Math.abs(x - markerX) < 40) {
    ctx.fillStyle = "rgba(255,51,102,0.9)";
    ctx.font = "9px monospace";
    ctx.textAlign = "center";
    ctx.fillText(m.label || formatDuration(m.time), x, centerY - 12);
  }
  ctx.restore();
});
```

Add marker button to the player deck:

```html
<!-- Add after the repeat button in deck-transport-row -->
<button class="transport-btn-round" onclick="addMarkerAtCurrentPosition()" title="Add Bookmark">
  <svg class="icon icon-sm" viewBox="0 0 24 24"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
</button>
```

Add marker management to the context menu (in sheet-mask-context):

```html
<div class="action-menu-row" onclick="showMarkersSheet()">
  <svg class="icon icon-md" viewBox="0 0 24 24"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
  Track Markers
</div>
```

Add marker sheet HTML:

```html
<!-- MARKERS SHEET -->
<div class="sheet-backdrop-mask" id="sheet-mask-markers" onclick="dismissAllSheets()">
  <div class="sheet-content-dialog" onclick="event.stopPropagation()">
    <div class="sheet-top-strip">
      <span class="sheet-title-text">Track Markers</span>
      <button class="btn-action-tap" onclick="dismissAllSheets()">
        <svg class="icon icon-sm" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
      </button>
    </div>
    <div class="sheet-scroll-body" id="markers-list-container">
      <!-- Rendered by JS -->
    </div>
  </div>
</div>
```

Add marker sheet rendering:

```javascript
function showMarkersSheet() {
  dismissAllSheets();
  renderMarkersList();
  document.getElementById("sheet-mask-markers").classList.add("open");
}

function renderMarkersList() {
  const container = document.getElementById("markers-list-container");
  const markers = getMarkersForCurrentTrack();
  
  if (markers.length === 0) {
    container.innerHTML = `
      <div style="padding: 24px 0; text-align: center; color: var(--text-tertiary); font-size: 12px; line-height: 1.7;">
        No bookmarks for this track.<br>Tap the bookmark icon in the player to save moments.
      </div>
    `;
    return;
  }
  
  container.innerHTML = markers.map(m => `
    <div class="song-catalog-row" onclick="seekToMarker(${state.songs[state.currentIndex]?.id}, ${m.time})">
      <div style="display: flex; flex-direction: column; gap: 2px;">
        <div style="font-size: 13px; font-weight: 650; color: var(--text-pure);">${escHtml(m.label)}</div>
        <div style="font-size: 11px; font-family: var(--font-mono); color: var(--text-tertiary);">${formatDuration(m.time)}</div>
      </div>
      <button class="btn-action-tap" style="margin-left: auto;" onclick="event.stopPropagation(); removeMarker(${state.songs[state.currentIndex]?.id}, '${m.id}')">
        <svg class="icon icon-sm" style="color: #FF5A5A;" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
      </button>
    </div>
  `).join("");
}
```

---

### CHANGE 6: Format Badge on Mini Player

Add to `loadTrack()` function, update the mini player format chip:

```javascript
// In loadTrack(), after setting mini-meta-artist:
const ext = (song.path || "").split(".").pop().toUpperCase();
const formatMap = { "WAV": "WAV", "FLAC": "FLAC", "MP3": "MP3", "M4A": "AAC", "OGG": "OGG", "OPUS": "OPUS" };
const shortFormat = formatMap[ext] || ext.substring(0, 4);
const chip = document.getElementById("mini-format-chip");
if (chip) {
  chip.textContent = shortFormat;
  chip.classList.add("visible");
}
```

---

### CHANGE 7: Pull-to-Refresh on Library

Add pull indicator to `#library-scroll-container`:

```html
<div id="pull-indicator" class="pull-indicator">
  <svg class="icon icon-md" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>
  <span style="font-size: 11px; font-weight: 600; color: var(--accent); letter-spacing: 0.5px;">REFRESHING...</span>
</div>
```

Add pull-to-refresh JS:

```javascript
/* =========================================================================
   PULL-TO-REFRESH ON LIBRARY
   ========================================================================= */
let pullStartY = 0;
let pullPulling = false;
const PULL_THRESHOLD = 80;

const libScroll = document.getElementById("library-scroll-container");

libScroll.addEventListener("touchstart", e => {
  if (libScroll.scrollTop === 0) {
    pullStartY = e.touches[0].clientY;
    pullPulling = true;
  }
}, { passive: true });

libScroll.addEventListener("touchmove", e => {
  if (!pullPulling || libScroll.scrollTop > 0) return;
  
  const dy = e.touches[0].clientY - pullStartY;
  if (dy > 0) {
    e.preventDefault();
    const indicator = document.getElementById("pull-indicator");
    const progress = Math.min(dy / PULL_THRESHOLD, 1.5);
    indicator.style.opacity = progress;
    indicator.classList.add("visible");
  }
}, { passive: false });

libScroll.addEventListener("touchend", e => {
  if (!pullPulling) return;
  
  const indicator = document.getElementById("pull-indicator");
  const dy = (e.changedTouches ? e.changedTouches[0].clientY : 0) - pullStartY;
  
  if (dy > PULL_THRESHOLD) {
    indicator.querySelector("svg").style.animation = "pullArrowBounce 0.3s ease-in-out infinite alternate";
    // Refresh library
    invokeDeviceScan();
    setTimeout(() => {
      indicator.querySelector("svg").style.animation = "";
      indicator.classList.remove("visible");
    }, 1500);
  } else {
    indicator.classList.remove("visible");
  }
  
  pullPulling = false;
}, { passive: true });
```

---

## 📝 IMPLEMENTATION NOTES

1. **All CSS changes** go inside the `<style>` block in `index.html`
2. **All HTML additions** go inside `<div id="daddyamp-root">` 
3. **All JS additions** go inside the `<script>` block, before the closing `</script>`
4. **Marker persistence**: Add `markers` to `persistState()` and `loadPersistedData()`
5. **Test**: After each change, run `bash ./build_apk.sh` and install on device
6. **SVG illustrations** — ALL must be inline SVG, zero emoji, zero unicode

---
