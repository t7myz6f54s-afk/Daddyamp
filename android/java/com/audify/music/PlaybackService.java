package com.audify.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * DaddyAmp persistent playback host (v1.44).
 *
 * Owns the foreground service + media notification; the AudifyBridge remains the
 * single audio owner (locked contract — MediaPlayer, AudioFX, folder engine all
 * live there, all JS contracts unchanged). This service exists so that:
 *   1. the process survives the Activity being destroyed while music plays
 *      (screen off, app swiped away — Poweramp behavior), and
 *   2. users get lockscreen-quality notification transport: artwork, play/pause,
 *      next/previous, tap-to-reopen, swipe-to-dismiss when paused.
 *
 * Transport contract: actions forward to the SAME JS entry point the existing
 * MediaSession callbacks use (window.onMediaSessionTransport) — one control path,
 * no second source of truth.
 */
public class PlaybackService extends Service {
    private static final String TAG = "DaddyAmpPlaybackService";
    private static final int NOTIFICATION_ID = 42;
    private static final String CHANNEL_ID = "playback";
    private static final long PAUSED_IDLE_STOP_MS = 15 * 60 * 1000; // like Poweramp: dismissible, stops eventually

    public static final String ACTION_SYNC = "com.audify.music.action.SYNC";
    public static final String ACTION_PLAY = "com.audify.music.action.PLAY";
    public static final String ACTION_PAUSE = "com.audify.music.action.PAUSE";
    public static final String ACTION_NEXT = "com.audify.music.action.NEXT";
    public static final String ACTION_PREVIOUS = "com.audify.music.action.PREVIOUS";
    public static final String ACTION_STOP = "com.audify.music.action.STOP";
    public static final String ACTION_DISMISS = "com.audify.music.action.DISMISS";

    /* ---------------- static host state ---------------- */

    /** What the notification currently shows. Static so syncs landing before
     * service start still render correctly on first frame. */
    static final class Snapshot {
        String title = "";
        String artist = "";
        Bitmap art = null;
        boolean playing = false;
        long positionMs = 0;
        long durationMs = 0;
        boolean hasTrack = false;
    }

    private static final Snapshot snap = new Snapshot();
    private static PlaybackService instance = null;
    private static AudifyBridge bridge = null;
    private static MediaSession.Token sessionToken = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleStopper = new Runnable() {
        @Override public void run() {
            if (!snap.playing) shutdown();
        }
    };

    /* ---------------- static API used by AudifyBridge ---------------- */

    public static void attachBridge(AudifyBridge b) { bridge = b; }
    public static boolean bridgeIs(AudifyBridge b) { return bridge == b; }
    public static void detachBridge(AudifyBridge b) { if (bridge == b) bridge = null; }

    /** Metadata + play-state push from the bridge (called on track changes and
     * play/pause transitions — the same moments JS calls updateMediaSession). */
    public static void sync(Context ctx, String title, String artist, Bitmap art,
                            boolean playing, long positionMs, long durationMs,
                            MediaSession.Token token) {
        snap.title = title != null ? title : "";
        snap.artist = artist != null ? artist : "";
        if (art != null) snap.art = art;
        snap.playing = playing;
        snap.positionMs = Math.max(0, positionMs);
        snap.durationMs = Math.max(0, durationMs);
        snap.hasTrack = snap.title.length() > 0 || snap.durationMs > 0;
        if (token != null) sessionToken = token;

        if (instance != null) {
            instance.refresh();
            return;
        }
        // Starting the service is only safe while the app is visible. First syncs
        // always arrive in that context (a user initiated playback); later syncs
        // hit the instance!=null path above. Anything else is ignored, never fatal.
        try {
            Intent i = new Intent(ctx, PlaybackService.class);
            i.setAction(ACTION_SYNC);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "service start deferred: " + e.getMessage());
        }
    }

    /** Play/pause flips that arrive without fresh metadata. */
    public static void syncState(Context ctx, boolean playing, long positionMs) {
        if (snap.playing == playing && instance != null) { snap.positionMs = positionMs; return; }
        snap.playing = playing;
        snap.positionMs = Math.max(0, positionMs);
        if (instance != null) instance.refresh();
        else if (playing) sync(ctx, snap.title, snap.artist, null, true, positionMs, snap.durationMs, sessionToken);
    }

    public static void shutdownIfOurs(Context ctx, AudifyBridge b) {
        if (bridgeIs(b) || bridge == null) shutdown(ctx);
    }

    public static void shutdown(Context ctx) {
        if (instance != null) instance.shutdown();
        else {
            try { ctx.stopService(new Intent(ctx, PlaybackService.class)); } catch (Exception ignored) {}
        }
    }

    /* ---------------- service lifecycle ---------------- */

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent != null && intent.getAction() != null ? intent.getAction() : ACTION_SYNC;
        switch (action) {
            case ACTION_PLAY:    transport("play"); break;
            case ACTION_PAUSE:   transport("pause"); break;
            case ACTION_NEXT:    transport("next"); break;
            case ACTION_PREVIOUS: transport("previous"); break;
            case ACTION_STOP:
                transport("stop");
                shutdown();
                return START_NOT_STICKY;
            case ACTION_DISMISS:
                // Notification is only dismissible while paused → treat as "stop & go away".
                if (!snap.playing) { shutdown(); return START_NOT_STICKY; }
                break;
            case ACTION_SYNC:
            default:
                break;
        }
        refresh();
        // If playback is gone (e.g. process restarted without state), don't linger.
        if (action.equals(ACTION_SYNC) && !snap.hasTrack && !snap.playing) {
            shutdown();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void goForeground(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            // Without POST_NOTIFICATIONS (API 33+) the notification is suppressed by
            // the system but startForeground must still succeed; any failure here is
            // logged only — playback continues regardless.
            Log.w(TAG, "startForeground: " + e.getMessage());
        }
    }

    /** Rebuilds/publishes the notification from the current snapshot and
     * (re)schedules the paused idle-stop. */
    private void refresh() {
        if (!snap.hasTrack) {
            // Nothing loaded yet: keep a minimal ongoing notification (fgs rule).
            Notification bare = baseBuilder().setOngoing(true).build();
            goForeground(bare);
            return;
        }
        Notification n = buildMediaNotification();
        goForeground(n);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) { try { nm.notify(NOTIFICATION_ID, n); } catch (Exception ignored) {} }

        mainHandler.removeCallbacks(idleStopper);
        if (!snap.playing) mainHandler.postDelayed(idleStopper, PAUSED_IDLE_STOP_MS);
    }

    private Notification.Builder baseBuilder() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL_ID);
        else b = new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play)
         .setVisibility(Notification.VISIBILITY_PUBLIC)
         .setColor(0xFF08090D)
         .setContentIntent(openAppIntent())
         .setDeleteIntent(deleteIntent());
        if (Build.VERSION.SDK_INT >= 26) {
            try { b.setColorized(true); } catch (Exception ignored) {}
        }
        return b;
    }

    private Notification buildMediaNotification() {
        Notification.Builder b = baseBuilder();
        b.setContentTitle(snap.title.length() > 0 ? snap.title : "DaddyAmp")
         .setContentText(snap.artist.length() > 0 ? snap.artist : "Ready")
         .setOngoing(snap.playing);
        if (snap.art != null) b.setLargeIcon(snap.art);

        b.addAction(action(ACTION_PREVIOUS, android.R.drawable.ic_media_previous, "Previous"));
        b.addAction(action(snap.playing ? ACTION_PAUSE : ACTION_PLAY,
                snap.playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                snap.playing ? "Pause" : "Play"));
        b.addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, "Next"));

        if (sessionToken != null) {
            Notification.MediaStyle style = new Notification.MediaStyle();
            style.setMediaSession(sessionToken);
            style.setShowActionsInCompactView(0, 1, 2);
            b.setStyle(style);
        }
        return b.build();
    }

    private Notification.Action action(String action, int icon, String label) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new Notification.Action.Builder(icon, label, pi).build();
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 1001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    private PendingIntent deleteIntent() {
        Intent i = new Intent(this, PlaybackService.class).setAction(ACTION_DISMISS);
        return PendingIntent.getService(this, 1002, i,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                            "Playback", NotificationManager.IMPORTANCE_LOW); // silent, as playback channels must be
                    ch.setDescription("Now playing");
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
            } catch (Exception ignored) {}
        }
    }

    /* ---------------- transport ---------------- */

    private void transport(String cmd) {
        AudifyBridge b = bridge;
        if (b != null) {
            // Same JS entry point the MediaSession callbacks use — one control path.
            b.transport(cmd);
        }
        // Optimistic notification flip so the UI feels responsive; the real state
        // returns via the normal JS→bridge→sync cycle right after.
        if ("play".equals(cmd)) snap.playing = true;
        if ("pause".equals(cmd)) snap.playing = false;
    }

    private void shutdown() {
        mainHandler.removeCallbacks(idleStopper);
        try { stopForeground(true); } catch (Exception ignored) {}
        try { stopSelf(); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(idleStopper);
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
