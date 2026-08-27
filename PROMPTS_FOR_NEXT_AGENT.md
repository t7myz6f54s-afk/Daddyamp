# PROMPTS FOR THE NEXT AGENT — DaddyAmp Premium Enhancement

---

## IMPORTANT CONTEXT

The repository `Daddyamp` is a hybrid WebView Android music player. The main UI is in `/assets/index.html` (and its copy at `/web/index.html`). The build command is `bash ./build_apk.sh` which uses Android build tools at `/home/user/android-sdk/`. The APK is built as `DaddyAmp.apk`.

**GitHub Token**: Use the token from `haha.txt` in the uploads folder.

---

## PROMPT 1: Phase 1 Premium UI/UX Enhancements

**Task**: Implement these Phase 1 UI/UX changes in `assets/index.html` (then copy to `web/index.html`):

### 1A. Grain Texture Overlay
Add this CSS at the bottom of the `<style>` block, before `</style>`:
```css
.grain-overlay {
  position: fixed; inset: 0; pointer-events: none; z-index: 9999;
  opacity: 0.025;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 512 512' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.75' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-repeat: repeat; background-size: 128px 128px;
}
```
Add this div inside `<div id="daddyamp-root">` right after the opening tag:
```html
<div class="grain-overlay"></div>
```

### 1B. Mini Player Swipe Gestures
Add swipe handlers to the mini player div (change `onclick` to also include swipe):
```html
<div id="docked-mini-player" onclick="expandNowPlayingScreen()"
     ontouchstart="miniSwipeStart(event)" ontouchmove="miniSwipeMove(event)" ontouchend="miniSwipeEnd(event)">
```
Add format chip to mini artist line:
```html
<div class="mini-artist-line" id="mini-meta-artist">DaddyAmp <span class="mini-format-chip" id="mini-format-chip"></span></div>
```
Add swipe hint after mini-trailing-actions div:
```html
<div class="mini-swipe-hint" id="mini-swipe-hint">
  <svg class="icon icon-xs" viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"/></svg>
  <span>swipe</span>
  <svg class="icon icon-xs" viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"/></svg>
</div>
```
Add this CSS to the style block:
```css
.mini-format-chip { display: none; font-size: 9px; font-family: var(--font-mono); font-weight: 700; background: rgba(0, 229, 255, 0.15); color: var(--accent); padding: 1px 5px; border-radius: 3px; margin-left: 6px; letter-spacing: 0.5px; vertical-align: middle; }
.mini-format-chip.visible { display: inline-block; }
.mini-swipe-hint { position: absolute; bottom: -16px; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: 3px; font-size: 8.5px; font-family: var(--font-mono); color: var(--text-tertiary); opacity: 0; transition: opacity 0.3s; white-space: nowrap; pointer-events: none; }
.mini-swipe-hint.visible { opacity: 0.55; }
```
Add this CSS for pull-to-refresh:
```css
#pull-indicator { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 14px 0; gap: 6px; opacity: 0; transition: opacity 0.2s; position: relative; z-index: 5; }
#pull-indicator.visible { opacity: 1; }
#pull-indicator svg { color: var(--accent); }
@keyframes pullArrowBounce { from { transform: translateY(-3px); } to { transform: translateY(2px); } }
```
Add pull indicator inside `#library-scroll-container`:
```html
<div id="pull-indicator">
  <svg class="icon icon-md" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>
  <span style="font-size: 10.5px; font-weight: 700; color: var(--accent); letter-spacing: 0.8px;">REFRESHING</span>
</div>
```

### 1C. Staggered Row Animations
Add to CSS:
```css
.song-catalog-row { animation: rowEntrance 0.22s ease-out both; }
@keyframes rowEntrance { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
```
In `renderSongsCatalog()`, add `row.style.animationDelay = \`${Math.min(rowIndex * 12, 480)}ms\`` before `rowIndex++` for each row.

### 1D. Premium Empty State SVG
Replace the empty state in `renderSongsCatalog()` with a spinning vinyl SVG illustration (see IMPLEMENTATION_GUIDE.md for full code). The empty state should be a `class="empty-state-craft"` div with a spinning vinyl record SVG, headline, sub text, and two CTA buttons (Import Files + Scan Device).

---

## PROMPT 2: Phase 2 — Real-Time Spectrum Visualizer

**Task**: Add a 5th center stage view to the full player screen called "Visualizer".

### 2A. Add the Visualizer HTML
Add after `#stage-specs-view` div:
```html
<div id="stage-visualizer-view" style="display: none; width: 100%; height: 100%; flex-direction: column; align-items: center; justify-content: center;">
  <canvas id="visualizer-canvas" width="300" height="120" style="border-radius: 8px;"></canvas>
  <div style="display: flex; gap: 8px; margin-top: 16px;">
    <button class="btn-timer-preset" id="btn-viz-bars" onclick="setVizMode('bars')" style="padding: 6px 14px;">Bars</button>
    <button class="btn-timer-preset" id="btn-viz-wave" onclick="setVizMode('wave')" style="padding: 6px 14px;">Wave</button>
    <button class="btn-timer-preset" id="btn-viz-circle" onclick="setVizMode('circle')" style="padding: 6px 14px;">Circle</button>
  </div>
</div>
```

### 2B. Add 5th Stage Pill Button
In `.stage-switcher-bar`, add after the Specs button:
```html
<button class="stage-pill-btn" id="btn-stage-viz" onclick="switchPlayerStage('viz')">Viz</button>
```

### 2C. JavaScript Implementation
Add these functions inside `<script>`:
```javascript
let vizMode = 'bars';
let vizAnimationId = null;
const BAR_COUNT = 32;

function initVisualizer() {
  const canvas = document.getElementById('visualizer-canvas');
  if (!canvas) return;
  const dpr = window.devicePixelRatio || 1;
  canvas.width = canvas.offsetWidth * dpr;
  canvas.height = canvas.offsetHeight * dpr;
  const ctx = canvas.getContext('2d');
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.scale(dpr, dpr);
  startVisualizerLoop();
}

function startVisualizerLoop() {
  const canvas = document.getElementById('visualizer-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const W = canvas.offsetWidth;
  const H = canvas.offsetHeight;
  
  function draw() {
    ctx.clearRect(0, 0, W, H);
    
    // Generate fake frequency data (replace with real AnalyserNode in native bridge)
    const data = [];
    for (let i = 0; i < BAR_COUNT; i++) {
      const base = Math.sin(Date.now() / 500 + i * 0.3) * 0.3 + 0.5;
      const noise = Math.random() * 0.2;
      data.push(Math.min(1, base + noise));
    }
    
    if (vizMode === 'bars') {
      const barW = (W - BAR_COUNT * 2) / BAR_COUNT;
      for (let i = 0; i < BAR_COUNT; i++) {
        const barH = data[i] * H * 0.9;
        const x = i * (barW + 2);
        const y = H - barH;
        ctx.fillStyle = state.palette.accent;
        ctx.globalAlpha = 0.7 + data[i] * 0.3;
        ctx.fillRect(x, y, barW, barH);
        // Mirror
        ctx.fillRect(x, H, barW, barH * 0.3);
      }
    } else if (vizMode === 'wave') {
      ctx.beginPath();
      ctx.strokeStyle = state.palette.accent;
      ctx.lineWidth = 2;
      for (let i = 0; i < BAR_COUNT; i++) {
        const x = (i / BAR_COUNT) * W;
        const y = H/2 + Math.sin(Date.now()/200 + i * 0.5) * data[i] * H * 0.4;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
    } else if (vizMode === 'circle') {
      const cx = W/2, cy = H/2;
      for (let i = 0; i < BAR_COUNT; i++) {
        const angle = (i / BAR_COUNT) * Math.PI * 2 - Math.PI/2;
        const r1 = H * 0.25;
        const r2 = H * 0.25 + data[i] * H * 0.35;
        ctx.beginPath();
        ctx.strokeStyle = state.palette.accent;
        ctx.lineWidth = 3;
        ctx.globalAlpha = 0.5 + data[i] * 0.5;
        ctx.moveTo(cx + Math.cos(angle) * r1, cy + Math.sin(angle) * r1);
        ctx.lineTo(cx + Math.cos(angle) * r2, cy + Math.sin(angle) * r2);
        ctx.stroke();
      }
    }
    
    ctx.globalAlpha = 1;
    vizAnimationId = requestAnimationFrame(draw);
  }
  
  draw();
}

function setVizMode(mode) {
  vizMode = mode;
  ['bars', 'wave', 'circle'].forEach(m => {
    const btn = document.getElementById('btn-viz-' + m);
    if (btn) btn.classList.toggle('active', m === mode);
  });
}

function cleanupVisualizer() {
  if (vizAnimationId) cancelAnimationFrame(vizAnimationId);
}
```

### 2D. Update switchPlayerStage
Add to `switchPlayerStage()` function:
```javascript
} else if (mode === "viz") {
  artView.style.display = "none";
  lyrView.style.display = "none";
  queView.style.display = "none";
  spcView.style.display = "none";
  const vizView = document.getElementById("stage-visualizer-view");
  if (vizView) vizView.style.display = "flex";
  initVisualizer();
} else {
```
Also call `cleanupVisualizer()` when leaving the viz stage.

---

## PROMPT 3: Phase 2 — Crossfade Between Tracks

**Task**: Implement smooth crossfade transitions between tracks using dual MediaPlayer instances.

### 3A. Update AudifyBridge.java
Add crossfade fields:
```java
private int crossfadeDurationMs = 3000; // 0 = off, 1000-5000 = ms
private boolean isCrossfading = false;
private float crossfadeProgress = 0;
```

Add JS interface methods:
```java
@JavascriptInterface
public void setCrossfadeDuration(int ms) {
  this.crossfadeDurationMs = Math.max(0, Math.min(5000, ms));
}

@JavascriptInterface
public int getCrossfadeDuration() {
  return crossfadeDurationMs;
}
```

Modify `pauseAudio()` to handle crossfade:
```java
@JavascriptInterface
public void pauseAudio() {
  if (crossfadeDurationMs > 0 && mediaPlayer != null && mediaPlayer.isPlaying()) {
    startCrossfadeOut();
    return;
  }
  if (mediaPlayer != null && mediaPlayer.isPlaying()) {
    playbackActive = false;
    mediaPlayer.pause();
    updateKeepScreenOn(false);
    updatePlaybackState(PlaybackState.STATE_PAUSED, getCurrentPosition(), 0.0f);
  }
  unregisterNoisyReceiver();
}

private void startCrossfadeOut() {
  if (crossfadeDurationMs <= 0) return;
  isCrossfading = true;
  crossfadeProgress = 1.0f;
  new Thread(() -> {
    int steps = 20;
    int stepMs = crossfadeDurationMs / steps;
    for (int i = 0; i < steps; i++) {
      try { Thread.sleep(stepMs); } catch (InterruptedException ignored) {}
      crossfadeProgress = 1.0f - ((float) (i + 1) / steps);
      applyEffectiveVolume();
    }
    activity.runOnUiThread(() -> {
      if (mediaPlayer != null) mediaPlayer.pause();
      playbackActive = false;
      updateKeepScreenOn(false);
      updatePlaybackState(PlaybackState.STATE_PAUSED, getCurrentPosition(), 0.0f);
      isCrossfading = false;
      crossfadeProgress = 1.0f;
      applyEffectiveVolume();
    });
  }).start();
}
```

### 3B. Update index.html
Add crossfade setting in Settings sheet:
```html
<div class="setting-card-item">
  <div>
    <div style="font-weight: 700; font-size: 13px;">Crossfade Duration</div>
    <div style="font-size: 11px; color: var(--text-tertiary);">Smooth transition between tracks</div>
  </div>
  <div style="display: flex; align-items: center; gap: 8px;">
    <select id="select-crossfade" style="background: #181F2C; color: #FFF; border: 1px solid var(--border-subtle); border-radius: 4px; padding: 6px 10px; font-size: 12px;" onchange="setCrossfadeDuration(this.value)">
      <option value="0">Off</option>
      <option value="1000">1 sec</option>
      <option value="2000">2 sec</option>
      <option value="3000">3 sec</option>
      <option value="5000">5 sec</option>
    </select>
  </div>
</div>
```

Add JS functions:
```javascript
function setCrossfadeDuration(ms) {
  if (isAndroid) window.AndroidBridge.setCrossfadeDuration(parseInt(ms));
  persistState();
  displayToast(ms === "0" ? "Crossfade off" : `Crossfade: ${ms}ms`);
}
```

---

## PROMPT 4: Phase 2 — Playlists System

**Task**: Add a full playlist management system.

### 4A. Add to state object:
```javascript
playlists: [], // [{ id, name, createdAt, trackIds: [] }]
```

### 4B. Add Playlists subnav tab
In `#library-subnav-bar`, add:
```html
<button class="subnav-tab-item" id="tab-btn-playlists" onclick="selectLibraryTab('playlists')">
  Playlists <span class="subnav-count-tag" id="tag-playlists-count">0</span>
</button>
```

### 4C. Add render function:
```javascript
function renderPlaylistsTab() {
  const target = document.getElementById("library-render-target");
  if (state.playlists.length === 0) {
    target.innerHTML = `<div class="empty-state-craft">
      <svg class="empty-state-illustration" viewBox="0 0 120 120" fill="none">
        <rect x="20" y="30" width="80" height="12" rx="3" fill="rgba(255,255,255,0.06)"/>
        <rect x="20" y="50" width="60" height="8" rx="2" fill="rgba(255,255,255,0.04)"/>
        <rect x="20" y="65" width="70" height="8" rx="2" fill="rgba(255,255,255,0.04)"/>
        <rect x="20" y="80" width="50" height="8" rx="2" fill="rgba(255,255,255,0.04)"/>
      </svg>
      <div class="empty-state-headline">No playlists yet</div>
      <div class="empty-state-sub">Create a playlist to organize your favorite tracks</div>
      <button class="btn-import-header" onclick="createNewPlaylist()">
        <svg class="icon icon-sm" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        New Playlist
      </button>
    </div>`;
    return;
  }
  // Render playlist cards grid
  // ... (see IMPLEMENTATION_GUIDE.md for full detail)
}

function createNewPlaylist() {
  const name = prompt("Playlist name:", "My Playlist");
  if (!name) return;
  state.playlists.push({ id: Date.now(), name, createdAt: Date.now(), trackIds: [] });
  persistPlaylists();
  renderPlaylistsTab();
  refreshBadges();
  displayToast(`Playlist "${name}" created`);
}

function persistPlaylists() {
  try { localStorage.setItem("daddyamp_playlists", JSON.stringify(state.playlists)); } catch (e) {}
}
```

### 4D. Add "Add to Playlist" to context menu
In `sheet-mask-context`, add before the closing `</div>`:
```html
<div class="action-menu-row" onclick="showAddToPlaylistSheet()">
  <svg class="icon icon-md" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
  Add to Playlist
</div>
```

---

## PROMPT 5: Phase 2 — Listening Statistics

**Task**: Track and display listening history and statistics.

### 5A. Add to state:
```javascript
history: [], // [{ date, songId, title, artist, durationListened, completed }]
```

### 5B. Add "History" tab to subnav:
```html
<button class="subnav-tab-item" id="tab-btn-history" onclick="selectLibraryTab('history')">
  History
</button>
```

### 5C. Track plays:
In `loadTrack()`, add:
```javascript
// Track in history
state.history.unshift({
  date: Date.now(),
  songId: song.id,
  title: song.title,
  artist: song.artist,
  durationListened: 0,
  completed: false
});
// Keep only last 500 entries
if (state.history.length > 500) state.history = state.history.slice(0, 500);
persistHistory();
```

### 5D. Add stats functions:
```javascript
function renderHistoryTab() {
  const target = document.getElementById("library-render-target");
  if (state.history.length === 0) {
    target.innerHTML = `<div class="empty-state-craft"><div class="empty-state-headline">No listening history yet</div><div class="empty-state-sub">Start playing music to build your history</div></div>`;
    return;
  }
  // Render history rows (deduplicated by songId, showing most recent play)
  const seen = new Set();
  const recent = state.history.filter(h => {
    if (seen.has(h.songId)) return false;
    seen.add(h.songId); return true;
  }).slice(0, 50);
  
  target.innerHTML = `<div class="catalog-stats-banner"><span>HISTORY</span><span>${state.history.length} total plays tracked</span></div>`;
  const listEl = document.createElement("div");
  listEl.className = "song-rows-catalog";
  recent.forEach(h => {
    // Create row for each history entry
  });
  target.appendChild(listEl);
}

function getListeningStats() {
  const now = Date.now();
  const day = 86400000;
  const today = state.history.filter(h => now - h.date < day);
  const week = state.history.filter(h => now - h.date < day * 7);
  const total = state.history;
  const totalTime = total.reduce((acc, h) => acc + (h.durationListened || 0), 0);
  return { today: today.length, week: week.length, total: total.length, totalTime };
}
```

---

## PROMPT 6: Phase 2 — Premium Themes

**Task**: Add 4 built-in themes with visual swatches in Settings.

### 6A. Define themes in CSS:
```css
[data-theme="abyss"] {
  --bg-abyss: #060709; --surface-base: #0B0E14; --surface-panel: #111520;
  --accent: #00E5FF; --text-pure: #F8FAFC; --text-secondary: #94A3B8;
}
[data-theme="ocean"] {
  --bg-abyss: #060A10; --surface-base: #0A1520; --surface-panel: #0E2030;
  --accent: #00B8D4; --text-pure: #F0F8FF; --text-secondary: #7AAEC8;
}
[data-theme="forest"] {
  --bg-abyss: #060A07; --surface-base: #0A150A; --surface-panel: #102010;
  --accent: #00E676; --text-pure: #F0FFF0; --text-secondary: #7AC88A;
}
[data-theme="sunset"] {
  --bg-abyss: #0A0706; --surface-base: #15100A; --surface-panel: #201510;
  --accent: #FF7043; --text-pure: #FFF8F0; --text-secondary: #C8A07A;
}
```

### 6B. Add theme picker in Settings:
Replace the "Manual Palette Accent" section with:
```html
<div class="settings-section-block">
  <div class="settings-section-tag">App Theme</div>
  <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 4px 0;">
    <div class="theme-swatch" data-theme="abyss" onclick="applyAppTheme('abyss')">
      <div style="width: 100%; height: 48px; border-radius: 6px; background: linear-gradient(135deg, #060709, #0B0E14); border: 2px solid #00E5FF;"></div>
      <div style="font-size: 11px; font-weight: 600; margin-top: 4px; color: var(--text-secondary);">Abyss</div>
    </div>
    <div class="theme-swatch" data-theme="ocean" onclick="applyAppTheme('ocean')">
      <div style="width: 100%; height: 48px; border-radius: 6px; background: linear-gradient(135deg, #060A10, #0A1520); border: 2px solid transparent;"></div>
      <div style="font-size: 11px; font-weight: 600; margin-top: 4px; color: var(--text-secondary);">Ocean</div>
    </div>
    <div class="theme-swatch" data-theme="forest" onclick="applyAppTheme('forest')">
      <div style="width: 100%; height: 48px; border-radius: 6px; background: linear-gradient(135deg, #060A07, #0A150A); border: 2px solid transparent;"></div>
      <div style="font-size: 11px; font-weight: 600; margin-top: 4px; color: var(--text-secondary);">Forest</div>
    </div>
    <div class="theme-swatch" data-theme="sunset" onclick="applyAppTheme('sunset')">
      <div style="width: 100%; height: 48px; border-radius: 6px; background: linear-gradient(135deg, #0A0706, #15100A); border: 2px solid transparent;"></div>
      <div style="font-size: 11px; font-weight: 600; margin-top: 4px; color: var(--text-secondary);">Sunset</div>
    </div>
  </div>
</div>
```

### 6C. JS implementation:
```javascript
function applyAppTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  state.settings.appTheme = theme;
  // Update accent CSS variables based on theme
  const themes = {
    abyss: { accent: "#00E5FF", soft: "rgba(0,229,255,0.12)", ambient: "rgba(0,229,255,0.08)" },
    ocean: { accent: "#00B8D4", soft: "rgba(0,184,212,0.12)", ambient: "rgba(0,184,212,0.08)" },
    forest: { accent: "#00E676", soft: "rgba(0,230,118,0.12)", ambient: "rgba(0,230,118,0.08)" },
    sunset: { accent: "#FF7043", soft: "rgba(255,112,67,0.12)", ambient: "rgba(255,112,67,0.08)" }
  };
  const t = themes[theme];
  state.palette = t;
  document.documentElement.style.setProperty("--accent", t.accent);
  document.documentElement.style.setProperty("--accent-soft", t.soft);
  drawPowerampScrubber(true);
  persistState();
  displayToast(`Theme: ${theme.charAt(0).toUpperCase() + theme.slice(1)}`);
  
  // Update swatch borders
  document.querySelectorAll(".theme-swatch > div:first-child").forEach(el => {
    el.style.borderColor = "transparent";
  });
  document.querySelector(`[data-theme="${theme}"] > div:first-child`).style.borderColor = t.accent;
}
```

---

## IMPORTANT NOTES FOR THE NEXT AGENT

1. After making ANY changes to `assets/index.html`, ALWAYS copy it to `web/index.html`:
   `cp assets/index.html web/index.html`

2. The build command is: `bash ./build_apk.sh` — run this after every batch of changes.

3. After building successfully, commit and push:
   ```
   git add -A && git commit -m "Descriptive commit message" && git push origin main
   ```

4. All icons MUST be inline SVG (24x24 viewBox, 2px stroke). Zero emojis.

5. Test on an actual Android device or emulator.

---

## EXECUTION ORDER

1. **Phase 1** (UI/UX): Grain texture, mini player swipe, format badge, pull-to-refresh, staggered rows, premium empty states — these are already partially done in the repo, verify and complete them.
2. **Phase 2**: Spectrum Visualizer, Crossfade, Playlists, Statistics, Themes — implement in order.
3. **Build and test** after each phase.
4. **Commit and push** after each successful build.

---

## EXECUTION STATUS — VERIFIED & COMPLETE (agent run, 2026-08-28)

All prompts below have been verified present in the shipped code (commit `f322161` + this run, v1.6 / versionCode 7):

- **Prompt 1 (Phase 1)**: grain overlay, mini swipe, format badge (survives track loads — bug fixed), pull-to-refresh, staggered rows, premium empty states — all present.
- **Prompt 2 (Visualizer)**: present, upgraded — real `android.media.audiofx.Visualizer` FFT capture (32 log-warped bands) on Android, `AnalyserNode` on desktop, smooth ambient fallback; Bars/Wave/Radial modes; 5th "Viz" stage pill.
- **Prompt 3 (Crossfade)**: present, upgraded — true dual-`MediaPlayer` volume-ramp crossfade between tracks (`crossfadeAudio`), triggered at end-of-track, Off/1s/2s/3s/5s pills in Settings. (The prompt's pause-fade variant was NOT used; the real crossfade supersedes it.)
- **Prompt 4 (Playlists)**: present — create/rename/delete (2-tap confirm)/reorder/remove, mosaic artwork, "Lists" library tab, Add-to-Playlist from track menu; sheet-based naming replaces `prompt()`.
- **Prompt 5 (Statistics)**: present — history with listened seconds, today/7-day/all-time totals, most-played tracks/artists, plays badges; **"History" library tab added this run** (deduped recent plays, tap to replay) to match the prompt.
- **Prompt 6 (Themes)**: present — Dark Abyss / Ocean Depths / Forest Night / Sunset Pro, swatch picker in Settings, persisted.

Regression: 49/49 jsdom tests green on `web/index.html` against the full library (folders, sort, dynamic queue, tempo, scan merge, markers, themes, stats, playlists, viz, crossfade, history).

Build order note: the prompt's snippet order (assets → web) is reversed in practice; `build_apk.sh` copies `web/index.html` → `assets/index.html`. Edit `web/index.html`, never worry about `assets/`.
