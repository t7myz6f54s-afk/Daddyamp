package com.audify.music;

import android.webkit.WebView;

/**
 * Process-lifetime holder for the live WebView + AudifyBridge (v1.44).
 *
 * A WebView music player is its UI: if the Activity is destroyed while audio is
 * playing (user swipes the task away, system trims, theme/config teardown), the
 * WebView must NOT be destroyed or playback dies with it. Poweramp solves this
 * with a playback service; DaddyAmp solves it with PlaybackService keeping the
 * PROCESS foreground, plus this holder keeping the WebView ALIVE so the same
 * JS app (and the bridge's MediaPlayer) simply outlives the Activity instance.
 *
 * On relaunch, MainActivity re-attaches the held WebView instead of creating a
 * new one — zero state loss, zero reload, music never missed a beat.
 *
 * Deliberate tradeoff (documented, not accidental): while playback is ongoing and
 * the task is gone, the old Activity's WebView context is retained. That memory is
 * bounded (one WebView) and is exactly the price of gapless-survival for a
 * WebView-hosted player; it is reclaimed the moment playback stops and the app
 * exits normally (MainActivity.onDestroy releases both).
 */
final class AppHolder {
    static WebView webView = null;
    static AudifyBridge bridge = null;
    /** Chrome client attached to the survived WebView; its host Activity is
     * re-assigned on relaunch (never targets a destroyed window). */
    static MainActivity.HostedChromeClient chromeClient = null;
    private AppHolder() {}
}
