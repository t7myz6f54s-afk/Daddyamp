# Agent Briefing: Poweramp-Grade Fluid UI Redesign

(Full briefing document — see chat history for the complete text. This file is the canonical copy stored in-repo for the implementation agent.)

**Mission (verbatim):** Redesign the entire UI so it feels like Poweramp: a high-end local music player. Dark, immersive, cover-art-tinted, with buttery 60-120 Hz animations, waveform seek, glass-soft surfaces, giant playback controls, and a bottom navigation that never fights the music. Do not flatten it into a generic Material 3 template. Do not copy Spotify's 'card feed'. Do not add clutter. Motion is a first-class feature. Album art is the brand.

**Constraints:** restyle existing screens/flows only; preserve all features; dark OLED-first; accessibility non-negotiable (contrast, 44-48px targets, reduce-motion).

**Visual DNA:** not Material-first; function over fashion; album art is the environment (full-bleed blurred/color-extracted cover as background, crossfading on track change); OLED dark layered blacks (#050506 void -> #0C0C0E -> #141418 -> #1C1C22); accent from album palette, desaturated 15-25% + luminance capped; waveseek (wavebars behind, played=accent, remaining=white 18%); glass chrome (rgba(12,12,16,0.55-0.72) + blur 20-32px + 1px rgba(255,255,255,0.10) border) on mini-player/nav/control cluster only; corner radius 20-28 cards / 999 play / 14-18 chips; text #F2F2F5 / rgba(255,255,255,0.62) / rgba(255,255,255,0.38); tabular timecodes; stroke 1.75-2px rounded icons; filled circle play 56-72px.

**Motion DNA:** every screen change animated; no hard cuts; art morphs (shared element) list thumb -> now-playing cover; track change art scales 1->0.92 fade + new 1.06->1 fade, bg crossfade 400-600ms, title/artist slide+fade; spring sheets 380ms; press scale 0.92-0.96 with release overshoot; seek thumb follows finger with no lag, waveform lights played vs remaining; play/pause icon morphs 180-220ms (not a swap).

**Easing bible:** enter/emphasis cubic-bezier(0.16,1,0.3,1); exit cubic-bezier(0.4,0,1,1); shared element cubic-bezier(0.2,0.8,0.2,1) 320-420ms; springs stiffness 280-380 damping 22-28; durations micro 80-120ms / UI 180-280ms / page 320-480ms / ambient bg 500-800ms. Reduce motion: OS prefers-reduced-motion -> crossfade only 150ms, no springs, no vis.

**IA mapping:** hero surface = now playing (art/blur treatment); primary action = play (giant morphing control); power tools = EQ/settings one tap from nav as spring sheet; lists always show mini hero bar when playing.

**Screen-by-screen:** now playing (cover 55-62% vh radius 8-16, waveseek 48-64px tall with pro buttons overlaid, utility icons 44px, metadata line tappable, bottom nav 56-64px + safe area); mini-player 64-72px glass above nav with 40px rounded art, progress 2px accent line on top edge, shared-element expand; library rows 56-64px with 48px art, now-playing row accent left bar 3px + eq bars; queue sheet 85% with drag-reorder; lyrics full-bleed blur, active line accent scale 1.04; EQ dedicated nav destination with band stagger on preset change; settings expose Look & Feel (theme, accent, blur intensity, seek style wavebars/line, layout, vis, reduce motion, corner radius).

**Motion tokens:** micro 90ms / ui 180ms cubic-bezier(0.16,1,0.3,1) / sheet spring 380ms / hero 420ms cubic-bezier(0.2,0.8,0.2,1) / page 360ms / ambient 600ms. Stagger lists 40-60ms apart max 8 items.

**Interaction rules:** one-tap to music; gestures accelerate, buttons never disappear; max 4 nav destinations; metadata cycles on tap; hit targets >= 44px; contrast >= 4.5:1 on blurred bg; desaturated accents; performance is UX (compositor-only properties on hot paths).

**Process:** A audit -> B tokenize -> C player first + mini-player + nav -> D lists & sheets -> E motion pass -> F polish (haptics, vis toggle, look-and-feel, reduce-motion, contrast QA on busy covers) -> G do/don't checklist.

**Acceptance criteria (done when):** 1 track change crossfades bg + morphs art, no flicker; 2 seek is waveform tracking finger; 3 play/pause morphs not swaps; 4 full player collapses into mini-player with shared element; 5 bottom nav 4 items glass always visible; 6 lists stable 60fps; 7 accent from cover palette with fallback; 8 contrast + reduce-motion OK; 9 EQ/power panel one tap from nav, animates bands; 10 "This feels like Poweramp."
