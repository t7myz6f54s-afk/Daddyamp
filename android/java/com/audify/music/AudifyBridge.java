package com.audify.music;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.Visualizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AudifyBridge implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "DaddyAmpBridge";

    private final Activity activity;
    private final WebView webView;
    private MediaPlayer mediaPlayer;
    private MediaPlayer nextMediaPlayer;
    private Equalizer nativeEqualizer;
    private BassBoost nativeBassBoost;
    private Virtualizer nativeVirtualizer;
    private MediaSession mediaSession;
    private AudioManager audioManager;

    private float masterVolume = 0.75f;
    private float balanceLeft = 1.0f;
    private float balanceRight = 1.0f;
    private float preampGainDb = 0.0f;
    private float replayGainDb = 0.0f;
    private boolean replayGainEnabled = false;
    private float tempo = 1.0f;
    private boolean playbackActive = false;
    private Visualizer nativeVisualizer = null;
    private boolean vizCaptureOn = false;
    private boolean audioFxRequested = false;
    private ContentObserver volumeObserver = null;
    private int lastVolumePercent = -1;
    private int audioSessionId = 0;
    private int vizSessionId = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String currentPath = "";
    private String nextPath = "";
    private boolean wasPlayingBeforeInterruption = false;
    private boolean pausedForLossTransient = false;
    private boolean isDucking = false;
    private boolean duckingEnabled = true;
    private boolean autoPauseOnUnplug = true;

    private BroadcastReceiver noisyReceiver;
    private boolean isReceiverRegistered = false;
    private FolderEngine folderEngine;
    private Object audioFocusRequestObject; // API 26+

    public AudifyBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.folderEngine = new FolderEngine(activity, webView);
        setupNoisyReceiver();
        setupMediaSession();
        setupSystemVolumeObserver();
    }

    private void setupNoisyReceiver() {
        this.noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    if (autoPauseOnUnplug && isPlaying()) {
                        pauseAudio();
                        runOnJs("if (window.onAudioBecomingNoisy) { window.onAudioBecomingNoisy(); }");
                    }
                }
            }
        };
    }

    private void setupMediaSession() {
        try {
            mediaSession = new MediaSession(activity, "DaddyAmpMediaSession");
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    resumeAudio();
                    runOnJs("if (window.onMediaSessionTransport) { window.onMediaSessionTransport('play'); }");
                }

                @Override
                public void onPause() {
                    pauseAudio();
                    runOnJs("if (window.onMediaSessionTransport) { window.onMediaSessionTransport('pause'); }");
                }

                @Override
                public void onSkipToNext() {
                    runOnJs("if (window.onMediaSessionTransport) { window.onMediaSessionTransport('next'); }");
                }

                @Override
                public void onSkipToPrevious() {
                    runOnJs("if (window.onMediaSessionTransport) { window.onMediaSessionTransport('previous'); }");
                }

                @Override
                public void onSeekTo(long pos) {
                    seekAudio((int) pos);
                    runOnJs("if (window.onMediaSessionTransport) { window.onMediaSessionTransport('seek', " + pos + "); }");
                }

                @Override
                public void onStop() {
                    pauseAudio();
                    runOnJs("if (window.onMediaSessionTransport) { window.onMediaSessionTransport('stop'); }");
                }
            });
            mediaSession.setActive(true);
        } catch (Exception e) {
            Log.w(TAG, "Could not initialize MediaSession: " + e.getMessage());
        }
    }

    private void registerNoisyReceiver() {
        if (!isReceiverRegistered && noisyReceiver != null) {
            try {
                activity.registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                isReceiverRegistered = true;
            } catch (Exception ignored) {}
        }
    }

    private void unregisterNoisyReceiver() {
        if (isReceiverRegistered && noisyReceiver != null) {
            try {
                activity.unregisterReceiver(noisyReceiver);
                isReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
    }

    private void setupSystemVolumeObserver() {
        try {
            lastVolumePercent = getMusicVolumePercent();
            volumeObserver = new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    notifySystemVolumeIfChanged(false);
                }
            };
            activity.getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, volumeObserver);
        } catch (Exception e) {
            Log.w(TAG, "System volume observer unavailable: " + e.getMessage());
        }
    }

    private int getMusicVolumePercent() {
        try {
            if (audioManager == null) return 75;
            int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            return Math.max(0, Math.min(100, Math.round((cur * 100.0f) / max)));
        } catch (Exception e) {
            return 75;
        }
    }

    private void notifySystemVolumeIfChanged(boolean force) {
        final int pct = getMusicVolumePercent();
        if (!force && pct == lastVolumePercent) return;
        lastVolumePercent = pct;
        runOnJs("if (window.onSystemVolumeChanged) { window.onSystemVolumeChanged(" + pct + "); }");
    }


    @JavascriptInterface
    public void setWindowTransparency(final boolean enabled, final float dim) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int bg = enabled ? Color.TRANSPARENT : Color.parseColor("#08090D");
                    activity.getWindow().setBackgroundDrawable(new ColorDrawable(bg));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        int sys = enabled ? Color.TRANSPARENT : Color.parseColor("#08090D");
                        activity.getWindow().setStatusBarColor(sys);
                        activity.getWindow().setNavigationBarColor(sys);
                    }
                    webView.setBackgroundColor(Color.TRANSPARENT);
                } catch (Exception e) {
                    Log.w(TAG, "setWindowTransparency failed", e);
                }
            }
        });
    }

    @JavascriptInterface
    public int getSystemVolumePercent() {
        return getMusicVolumePercent();
    }

    @JavascriptInterface
    public void setSystemVolumePercent(int percent) {
        try {
            if (audioManager == null) return;
            int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int target = Math.round(Math.max(0, Math.min(100, percent)) * max / 100.0f);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            notifySystemVolumeIfChanged(true);
        } catch (Exception e) {
            Log.w(TAG, "setSystemVolumePercent failed: " + e.getMessage());
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                wasPlayingBeforeInterruption = false;
                pausedForLossTransient = false;
                pauseAudio();
                runOnJs("if (window.onAudioFocusChange) { window.onAudioFocusChange('loss'); }");
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    wasPlayingBeforeInterruption = true;
                    pausedForLossTransient = true;
                    mediaPlayer.pause();
                    runOnJs("if (window.onAudioFocusChange) { window.onAudioFocusChange('transient_loss'); }");
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    if (duckingEnabled) {
                        isDucking = true;
                        applyEffectiveVolume();
                        runOnJs("if (window.onAudioFocusChange) { window.onAudioFocusChange('duck'); }");
                    } else {
                        wasPlayingBeforeInterruption = true;
                        pausedForLossTransient = true;
                        mediaPlayer.pause();
                        runOnJs("if (window.onAudioFocusChange) { window.onAudioFocusChange('transient_loss'); }");
                    }
                }
                break;

            case AudioManager.AUDIOFOCUS_GAIN:
                if (isDucking) {
                    isDucking = false;
                    applyEffectiveVolume();
                    runOnJs("if (window.onAudioFocusChange) { window.onAudioFocusChange('unduck'); }");
                } else if (pausedForLossTransient && wasPlayingBeforeInterruption) {
                    pausedForLossTransient = false;
                    wasPlayingBeforeInterruption = false;
                    resumeAudio();
                    runOnJs("if (window.onAudioFocusChange) { window.onAudioFocusChange('gain'); }");
                }
                break;
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build();
            this.audioFocusRequestObject = focusRequest;
            return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            return audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequestObject != null) {
                audioManager.abandonAudioFocusRequest((AudioFocusRequest) audioFocusRequestObject);
            } else {
                audioManager.abandonAudioFocus(this);
            }
        } catch (Exception ignored) {}
    }

    private void updateKeepScreenOn(final boolean keep) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (keep) {
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    @JavascriptInterface
    public void log(String message) {
        Log.d(TAG, message);
    }

    @JavascriptInterface
    public void showToast(final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return activity.checkSelfPermission("android.permission.READ_MEDIA_AUDIO") == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= 23) {
            return activity.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @JavascriptInterface
    public void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.requestPermissions(new String[]{"android.permission.READ_MEDIA_AUDIO"}, 101);
        } else if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, 101);
        }
    }

    @JavascriptInterface
    public void openAudioPicker() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("audio/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    activity.startActivityForResult(intent, MainActivity.AUDIO_PICKER_REQUEST);
                } catch (Exception e) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("audio/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    activity.startActivityForResult(Intent.createChooser(intent, "Import Audio"), MainActivity.AUDIO_PICKER_REQUEST);
                }
            }
        });
    }

    public void handleAudioPickerResult(Intent data) {
        List<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        if (uris.isEmpty()) return;

        final JSONArray importedSongs = new JSONArray();
        ContentResolver resolver = activity.getContentResolver();

        for (Uri uri : uris) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    try {
                        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                }

                String displayName = "Unknown Track";
                Cursor cursor = resolver.query(uri, null, null, null, null);
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex);
                    }
                    cursor.close();
                }

                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(activity, uri);

                String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                String genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                String trackNumStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
                String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String bitrateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                String mimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                String sampleRateStr = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    sampleRateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
                }

                if (title == null || title.trim().isEmpty()) {
                    title = displayName;
                    if (title.contains(".")) {
                        title = title.substring(0, title.lastIndexOf('.'));
                    }
                }
                if (artist == null || artist.trim().isEmpty() || artist.equals("<unknown>")) {
                    artist = "Unknown Artist";
                }
                if (album == null || album.trim().isEmpty()) {
                    album = "Imported Audio";
                }
                if (genre == null || genre.trim().isEmpty()) {
                    genre = "Local";
                }

                long durationSec = 0;
                if (durationStr != null) {
                    try { durationSec = Long.parseLong(durationStr) / 1000; } catch (Exception ignored) {}
                }

                int bitrateKbps = 0;
                if (bitrateStr != null) {
                    try { bitrateKbps = (int) (Long.parseLong(bitrateStr) / 1000); } catch (Exception ignored) {}
                }

                int sampleRateHz = 44100;
                if (sampleRateStr != null) {
                    try { sampleRateHz = Integer.parseInt(sampleRateStr); } catch (Exception ignored) {}
                }

                String artworkBase64 = null;
                byte[] embeddedPic = mmr.getEmbeddedPicture();
                if (embeddedPic != null && embeddedPic.length > 0) {
                    artworkBase64 = "data:image/jpeg;base64," + Base64.encodeToString(embeddedPic, Base64.NO_WRAP);
                }

                mmr.release();

                JSONObject song = new JSONObject();
                song.put("id", stableTrackId(uri.toString()));
                song.put("title", title);
                song.put("artist", artist);
                song.put("album", album);
                song.put("genre", genre);
                song.put("year", 2026);
                song.put("duration", durationSec);
                song.put("trackNumber", parseTrackNumber(trackNumStr));
                song.put("bitrate", bitrateKbps);
                song.put("sampleRate", sampleRateHz);
                song.put("mimeType", mimeType != null ? mimeType : "audio/*");
                song.put("path", uri.toString());
                song.put("artwork", artworkBase64 != null ? artworkBase64 : "artwork/default.png");
                song.put("favorite", 0);
                song.put("play_count", 0);
                song.put("lyrics", "");
                song.put("imported", true);

                importedSongs.put(song);
            } catch (Exception e) {
                Log.e(TAG, "Error importing file: " + uri, e);
            }
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String js = "if (window.onAudioFilesImported) { window.onAudioFilesImported(" + importedSongs.toString() + "); }";
                webView.evaluateJavascript(js, null);
                showToast("Imported " + importedSongs.length() + " songs successfully");
            }
        });
    }

    @JavascriptInterface
    public boolean playAudio(final String pathOrUri) {
        this.currentPath = pathOrUri;
        try {
            requestAudioFocus();
            registerNoisyReceiver();
            updateKeepScreenOn(true);

            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                );
            } else {
                mediaPlayer.reset();
            }

            setSourceOnPlayer(mediaPlayer, pathOrUri);
            applyEffectiveVolume();

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (Build.VERSION.SDK_INT >= 23 && tempo != 1.0f) {
                        try {
                            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(tempo));
                        } catch (Exception ignored) {}
                    }
                    mp.start();
                    playbackActive = true;
                    setupAudioFx(mp.getAudioSessionId());
                    final int duration = mp.getDuration();
                    updatePlaybackState(PlaybackState.STATE_PLAYING, 0, 1.0f);
                    runOnJs("if (window.onAudioPrepared) { window.onAudioPrepared(" + duration + "); }");
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    playbackActive = false;
                    updateKeepScreenOn(false);
                    updatePlaybackState(PlaybackState.STATE_PAUSED, mp.getDuration(), 0.0f);
                    runOnJs("if (window.onAudioFinished) { window.onAudioFinished(); }");
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    playbackActive = false;
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    updateKeepScreenOn(false);
                    runOnJs("if (window.onAudioError) { window.onAudioError(" + what + "); }");
                    return true;
                }
            });

            mediaPlayer.prepareAsync();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio: " + pathOrUri, e);
            updateKeepScreenOn(false);
            return false;
        }
    }

    private void setSourceOnPlayer(MediaPlayer player, String pathOrUri) throws Exception {
        if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("http://") || pathOrUri.startsWith("https://")) {
            player.setDataSource(activity, Uri.parse(pathOrUri));
        } else if (pathOrUri.startsWith("file:///android_asset/")) {
            String assetPath = pathOrUri.substring("file:///android_asset/".length());
            android.content.res.AssetFileDescriptor afd = activity.getAssets().openFd(assetPath);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } else {
            player.setDataSource(pathOrUri);
        }
    }

    @JavascriptInterface
    public void setNextAudio(final String nextPathOrUri) {
        this.nextPath = nextPathOrUri;
        if (mediaPlayer == null || nextPathOrUri == null || nextPathOrUri.isEmpty()) {
            return;
        }

        try {
            if (nextMediaPlayer != null) {
                try { nextMediaPlayer.release(); } catch (Exception ignored) {}
                nextMediaPlayer = null;
            }

            nextMediaPlayer = new MediaPlayer();
            nextMediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );

            setSourceOnPlayer(nextMediaPlayer, nextPathOrUri);
            nextMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (mediaPlayer != null) {
                        try {
                            mediaPlayer.setNextMediaPlayer(mp);
                            Log.d(TAG, "Gapless transition armed for: " + nextPath);
                        } catch (Exception e) {
                            Log.w(TAG, "Could not set next media player: " + e.getMessage());
                        }
                    }
                }
            });
            nextMediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "Error arming gapless track: " + e.getMessage());
        }
    }

    private void setupAudioFx(int audioSessionId) {
        if (nativeVisualizer != null && this.audioSessionId != 0 && this.audioSessionId != audioSessionId) {
            // Source switched to a new audio session: kill the stale visualizer or it
            // keeps reporting as "running" while never delivering data.
            try { nativeVisualizer.release(); } catch (Exception ignored) {}
            nativeVisualizer = null;
            vizCaptureOn = false;
        }
        this.audioSessionId = audioSessionId;
        if (!audioFxRequested) {
            // Stability/performance: do not attach Android AudioEffect engines on
            // every play. Some vendor ROMs crash media_server/WebView after a few
            // seconds with eager EQ/Bass/Virtualizer. Attach lazily only after the
            // user touches DSP controls.
            return;
        }
        try {
            if (nativeEqualizer != null) {
                try { nativeEqualizer.release(); } catch (Exception ignored) {}
            }
            nativeEqualizer = new Equalizer(0, audioSessionId);
            nativeEqualizer.setEnabled(true);
            int bandCount = nativeEqualizer.getNumberOfBands();
            short[] range = nativeEqualizer.getBandLevelRange();
            Log.i(TAG, "HAL Equalizer attached: " + bandCount + " bands, range: [" + range[0] + ", " + range[1] + "] mB");

            if (nativeBassBoost != null) {
                try { nativeBassBoost.release(); } catch (Exception ignored) {}
            }
            nativeBassBoost = new BassBoost(0, audioSessionId);
            nativeBassBoost.setEnabled(true);

            if (nativeVirtualizer != null) {
                try { nativeVirtualizer.release(); } catch (Exception ignored) {}
            }
            nativeVirtualizer = new Virtualizer(0, audioSessionId);
            nativeVirtualizer.setEnabled(true);
            Log.i(TAG, "Virtualizer supported: " + nativeVirtualizer.getStrengthSupported());
        } catch (Exception e) {
            Log.w(TAG, "Native AudioFx initialization issue: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public String getNativeAudioCapabilities() {
        JSONObject caps = new JSONObject();
        try {
            if (nativeEqualizer != null) {
                caps.put("available", true);
                int bands = nativeEqualizer.getNumberOfBands();
                caps.put("bandCount", bands);
                short[] range = nativeEqualizer.getBandLevelRange();
                JSONArray rangeArr = new JSONArray();
                rangeArr.put(range[0]);
                rangeArr.put(range[1]);
                caps.put("levelRange", rangeArr);

                JSONArray freqs = new JSONArray();
                for (short i = 0; i < bands; i++) {
                    freqs.put(nativeEqualizer.getCenterFreq(i) / 1000);
                }
                caps.put("centerFreqs", freqs);
            } else {
                caps.put("available", false);
                caps.put("bandCount", 5);
            }

            boolean virtSup = nativeVirtualizer != null && nativeVirtualizer.getStrengthSupported();
            caps.put("virtualizerSupported", virtSup);
            caps.put("replayGainSupported", true);
            caps.put("gaplessSupported", true);
        } catch (Exception e) {
            Log.w(TAG, "Error building caps JSON: " + e.getMessage());
        }
        return caps.toString();
    }

    private void ensureAudioFxAttached() {
        audioFxRequested = true;
        if (audioSessionId != 0 && nativeEqualizer == null) {
            setupAudioFx(audioSessionId);
        }
    }

    @JavascriptInterface
    public void setNativeEqBand(int band, int millibels) {
        try {
            ensureAudioFxAttached();
            if (nativeEqualizer != null && nativeEqualizer.getEnabled()) {
                short min = nativeEqualizer.getBandLevelRange()[0];
                short max = nativeEqualizer.getBandLevelRange()[1];
                short target = (short) Math.max(min, Math.min(max, millibels));
                nativeEqualizer.setBandLevel((short) band, target);
                Log.d(TAG, "EQ Band " + band + " set to " + target + " mB");
            }
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void setNativeBassBoost(int strength) {
        try {
            ensureAudioFxAttached();
            if (nativeBassBoost != null && nativeBassBoost.getEnabled()) {
                short target = (short) Math.max(0, Math.min(1000, strength));
                nativeBassBoost.setStrength(target);
            }
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void setNativeVirtualizer(int strength) {
        try {
            ensureAudioFxAttached();
            if (nativeVirtualizer != null && nativeVirtualizer.getStrengthSupported()) {
                short target = (short) Math.max(0, Math.min(1000, strength));
                nativeVirtualizer.setStrength(target);
            }
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void setNativePreamp(float gainDb) {
        this.preampGainDb = gainDb;
        applyEffectiveVolume();
        Log.d(TAG, "Preamp set to " + gainDb + " dB");
    }

    @JavascriptInterface
    public void setNativeBalance(float leftRatio, float rightRatio) {
        this.balanceLeft = Math.max(0.0f, Math.min(1.0f, leftRatio));
        this.balanceRight = Math.max(0.0f, Math.min(1.0f, rightRatio));
        applyEffectiveVolume();
    }

    @JavascriptInterface
    public void setNativeReplayGain(float trackGainDb, boolean enabled) {
        this.replayGainDb = trackGainDb;
        this.replayGainEnabled = enabled;
        applyEffectiveVolume();
        Log.d(TAG, "ReplayGain configured: " + trackGainDb + " dB (enabled: " + enabled + ")");
    }

    @JavascriptInterface
    public void setAutoPauseOnUnplug(boolean enabled) {
        this.autoPauseOnUnplug = enabled;
    }

    @JavascriptInterface
    public void setDuckingEnabled(boolean enabled) {
        this.duckingEnabled = enabled;
    }

    private void applyEffectiveVolume() {
        if (mediaPlayer == null) return;
        try {
            float rgMultiplier = 1.0f;
            if (replayGainEnabled) {
                rgMultiplier = (float) Math.pow(10.0, replayGainDb / 20.0);
            }

            float preampMultiplier = (float) Math.pow(10.0, preampGainDb / 20.0);
            float duckMultiplier = isDucking ? 0.3f : 1.0f;

            float left = masterVolume * balanceLeft * rgMultiplier * preampMultiplier * duckMultiplier;
            float right = masterVolume * balanceRight * rgMultiplier * preampMultiplier * duckMultiplier;

            float finalLeft = Math.max(0.0f, Math.min(1.0f, left));
            float finalRight = Math.max(0.0f, Math.min(1.0f, right));

            mediaPlayer.setVolume(finalLeft, finalRight);
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            playbackActive = false;
            mediaPlayer.pause();
            updateKeepScreenOn(false);
            updatePlaybackState(PlaybackState.STATE_PAUSED, getCurrentPosition(), 0.0f);
        }
        unregisterNoisyReceiver();
    }

    @JavascriptInterface
    public void resumeAudio() {
        requestAudioFocus();
        registerNoisyReceiver();
        if (mediaPlayer != null) {
            playbackActive = true;
            mediaPlayer.start();
            updateKeepScreenOn(true);
            updatePlaybackState(PlaybackState.STATE_PLAYING, getCurrentPosition(), 1.0f);
        }
    }

    // ---------- REAL-TIME SPECTRUM CAPTURE (android.media.audiofx.Visualizer) ----------

    @JavascriptInterface
    public boolean hasVisualizerPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            return activity.checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @JavascriptInterface
    public void requestVisualizerPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 102);
        }
    }

    @JavascriptInterface
    public boolean startVisualizerCapture() {
        try {
            if (nativeVisualizer != null && vizSessionId == audioSessionId) return true;
            if (audioSessionId == 0 || mediaPlayer == null) return false;
            if (nativeVisualizer != null) {
                try { nativeVisualizer.release(); } catch (Exception ignored) {}
                nativeVisualizer = null;
            }
            nativeVisualizer = new Visualizer(audioSessionId);
            vizSessionId = audioSessionId;
            int capSize = Math.min(512, Visualizer.getCaptureSizeRange()[1]);
            nativeVisualizer.setCaptureSize(Math.max(Visualizer.getCaptureSizeRange()[0], capSize));
            nativeVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int rate) {}

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                    if (!vizCaptureOn) return;
                    try {
                        final int bands = 32;
                        final int half = fft.length / 2;
                        StringBuilder sb = new StringBuilder(bands * 4);
                        for (int i = 0; i < bands; i++) {
                            double pos = Math.pow(half, (double) i / (bands - 1));
                            int bin = Math.max(1, Math.min(half - 1, (int) pos));
                            double re = fft[2 * bin];
                            double im = fft[2 * bin + 1];
                            double mag = Math.sqrt(re * re + im * im) / 127.0;
                            mag = Math.min(1.0, mag * 2.4);
                            if (i > 0) sb.append(',');
                            sb.append((int) Math.round(mag * 255));
                        }
                        final String csv = sb.toString();
                        runOnJs("if (window.onVisualizerData) { window.onVisualizerData('" + csv + "'); }");
                    } catch (Exception ignored) {}
                }
            }, Math.max(2000, Visualizer.getMaxCaptureRate() / 2), true, false);
            nativeVisualizer.setEnabled(true);
            vizCaptureOn = true;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Visualizer unavailable: " + e.getMessage());
            nativeVisualizer = null;
            return false;
        }
    }

    @JavascriptInterface
    public void stopVisualizerCapture() {
        vizCaptureOn = false;
        try {
            if (nativeVisualizer != null) {
                nativeVisualizer.setEnabled(false);
                nativeVisualizer.release();
            }
        } catch (Exception ignored) {}
        nativeVisualizer = null;
    }

    // ---------- CROSSFADE (dual MediaPlayer with volume ramps) ----------

    @JavascriptInterface
    public boolean crossfadeAudio(final String pathOrUri, final float seconds) {
        if (mediaPlayer == null || seconds <= 0f) return false;
        try {
            final MediaPlayer next = new MediaPlayer();
            next.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            setSourceOnPlayer(next, pathOrUri);
            next.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(final MediaPlayer mp) {
                    try {
                        final long fadeMs = Math.max(150, (long) (seconds * 1000));
                        final int steps = 20;
                        mp.setVolume(0f, 0f);
                        mp.start();
                        for (int i = 1; i <= steps; i++) {
                            final float v = i / (float) steps;
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        mp.setVolume(v, v);
                                        if (mediaPlayer != null && mediaPlayer != mp) {
                                            mediaPlayer.setVolume(1f - v, 1f - v);
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }, fadeMs * i / steps);
                        }
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    setupAudioFx(mp.getAudioSessionId());
                                    MediaPlayer old = mediaPlayer;
                                    mediaPlayer = mp;
                                    playbackActive = true;
                                    currentPath = pathOrUri;
                                    if (old != null) {
                                        try { old.release(); } catch (Exception ignored) {}
                                    }
                                    updatePlaybackState(PlaybackState.STATE_PLAYING, 0, 1.0f);
                                    runOnJs("if (window.onCrossfadeComplete) { window.onCrossfadeComplete(); }");
                                } catch (Exception e) {
                                    Log.w(TAG, "crossfade promote failed: " + e.getMessage());
                                }
                            }
                        }, fadeMs + 60);
                    } catch (Exception e) {
                        Log.w(TAG, "crossfade start failed: " + e.getMessage());
                    }
                }
            });
            next.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    playbackActive = false;
                    updateKeepScreenOn(false);
                    runOnJs("if (window.onAudioFinished) { window.onAudioFinished(); }");
                }
            });
            next.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    playbackActive = false;
                    runOnJs("if (window.onAudioError) { window.onAudioError(" + what + "); }");
                    return true;
                }
            });
            next.prepareAsync();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "crossfade error: " + e.getMessage());
            return false;
        }
    }

    @JavascriptInterface
    public void setTempo(final float speed) {
        this.tempo = Math.max(0.5f, Math.min(2.0f, speed));
        if (mediaPlayer == null || Build.VERSION.SDK_INT < 23) return;
        try {
            PlaybackParams pp = mediaPlayer.getPlaybackParams();
            mediaPlayer.setPlaybackParams(pp.setSpeed(this.tempo));
            if (playbackActive) {
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.w(TAG, "setTempo failed: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public void seekAudio(int msec) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(msec);
            int state = mediaPlayer.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            updatePlaybackState(state, msec, mediaPlayer.isPlaying() ? 1.0f : 0.0f);
        }
    }

    @JavascriptInterface
    public void setVolume(float vol) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, vol));
        applyEffectiveVolume();
    }

    @JavascriptInterface
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try { return mediaPlayer.getCurrentPosition(); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    @JavascriptInterface
    public int getDuration() {
        if (mediaPlayer != null) {
            try { return mediaPlayer.getDuration(); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    @JavascriptInterface
    public boolean isPlaying() {
        if (mediaPlayer != null) {
            try { return mediaPlayer.isPlaying(); } catch (Exception e) { return false; }
        }
        return false;
    }

    @JavascriptInterface
    public void updateMediaSession(String title, String artist, String album, String albumArtist, String genre, long year, long durationSec, String artworkPath, boolean playing, long positionSec) {
        try {
            if (artworkPath != null && (artworkPath.startsWith("content://"))) {
                applySessionMetadata(title, artist, album, albumArtist, genre, year, durationSec, decodeContentArt(artworkPath), playing, positionSec);
            } else if (artworkPath != null && (artworkPath.startsWith("http://") || artworkPath.startsWith("https://"))) {
                // apply immediately without art, fetch art on background thread
                applySessionMetadata(title, artist, album, albumArtist, genre, year, durationSec, null, playing, positionSec);
                final String artUrl = artworkPath;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap bmp = downloadArtwork(artUrl);
                        if (bmp != null) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    applySessionMetadata(title, artist, album, albumArtist, genre, year, durationSec, bmp, playing, positionSec);
                                }
                            });
                        }
                    }
                }).start();
            } else {
                applySessionMetadata(title, artist, album, albumArtist, genre, year, durationSec, decodeLocalArt(artworkPath), playing, positionSec);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error updating MediaSession metadata: " + e.getMessage());
        }
    }

    private void applySessionMetadata(String title, String artist, String album, String albumArtist, String genre, long year, long durationSec, Bitmap art, boolean playing, long positionSec) {
        if (mediaSession == null) return;
        try {
            MediaMetadata.Builder mb = new MediaMetadata.Builder();
            mb.putString(MediaMetadata.METADATA_KEY_TITLE, title);
            mb.putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
            mb.putString(MediaMetadata.METADATA_KEY_ALBUM, album);
            mb.putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, albumArtist);
            if (genre != null && !genre.isEmpty()) mb.putString(MediaMetadata.METADATA_KEY_GENRE, genre);
            if (year > 0) mb.putLong(MediaMetadata.METADATA_KEY_YEAR, year);
            mb.putLong(MediaMetadata.METADATA_KEY_DURATION, durationSec * 1000);
            if (art != null) {
                Bitmap scaled = art;
                if (Math.max(art.getWidth(), art.getHeight()) > 1024) {
                    float r = 1024f / Math.max(art.getWidth(), art.getHeight());
                    scaled = Bitmap.createScaledBitmap(art, (int) (art.getWidth() * r), (int) (art.getHeight() * r), true);
                }
                mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, scaled);
            }
            mediaSession.setMetadata(mb.build());
            int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            updatePlaybackState(state, positionSec * 1000, playing ? 1.0f : 0.0f);
        } catch (Exception ignored) {}
    }

    private Bitmap decodeLocalArt(String artworkPath) {
        try {
            if (artworkPath == null || artworkPath.isEmpty()) return null;
            if (artworkPath.startsWith("data:image")) {
                int commaIdx = artworkPath.indexOf(',');
                if (commaIdx != -1) {
                    byte[] decoded = Base64.decode(artworkPath.substring(commaIdx + 1), Base64.DEFAULT);
                    return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                }
            } else if (artworkPath.startsWith("file:///android_asset/")) {
                String sub = artworkPath.substring("file:///android_asset/".length());
                InputStream is = activity.getAssets().open(sub);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                return bmp;
            } else if (artworkPath.startsWith("file://")) {
                // Folder-scan art cache (app cache dir)
                return BitmapFactory.decodeFile(artworkPath.substring("file://".length()));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Bitmap decodeContentArt(String artworkPath) {
        try {
            android.net.Uri uri = android.net.Uri.parse(artworkPath);
            InputStream is = activity.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Exception e) {
            Log.w(TAG, "content art decode failed: " + e.getMessage());
            return null;
        }
    }

    private Bitmap downloadArtwork(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) DaddyAmp/1.7");
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            is.close();
            byte[] data = bos.toByteArray();
            if (data.length > 2 * 1024 * 1024) return null;
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            Log.w(TAG, "artwork download failed: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @JavascriptInterface
    public boolean updateMediaStoreMetadata(String filePath, String title, String artist, String album, String genre, int year) {
        try {
            if (filePath == null || filePath.isEmpty()) return false;
            android.content.ContentValues cv = new android.content.ContentValues();
            if (title != null && !title.isEmpty()) cv.put(MediaStore.Audio.Media.TITLE, title);
            if (artist != null && !artist.isEmpty()) cv.put(MediaStore.Audio.Media.ARTIST, artist);
            if (album != null && !album.isEmpty()) cv.put(MediaStore.Audio.Media.ALBUM, album);
            if (genre != null && !genre.isEmpty()) cv.put(MediaStore.Audio.Media.GENRE, genre);
            if (year > 0) cv.put(MediaStore.Audio.Media.YEAR, year);
            if (cv.size() == 0) return false;
            int updated;
            if (filePath.startsWith("content://")) {
                updated = activity.getContentResolver().update(android.net.Uri.parse(filePath), cv, null, null);
            } else {
                updated = activity.getContentResolver().update(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv,
                        MediaStore.Audio.Media.DATA + "=?",
                        new String[]{ filePath });
            }
            return updated > 0;
        } catch (Exception e) {
            Log.w(TAG, "updateMediaStoreMetadata failed: " + e.getMessage());
            return false;
        }
    }

    @JavascriptInterface
    public String scanCueSheets() {
        org.json.JSONArray out = new org.json.JSONArray();
        try {
            String[] proj;
            try {
                proj = new String[]{ MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.RELATIVE_PATH };
            } catch (Throwable t) {
                proj = new String[]{ MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA };
            }
            android.database.Cursor c = activity.getContentResolver().query(
                    MediaStore.Files.getContentUri("external"),
                    proj,
                    MediaStore.MediaColumns.DISPLAY_NAME + " LIKE '%.cue' OR " + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE '%.CUE'",
                    null, null);
            if (c == null) return out.toString();
            while (c.moveToNext()) {
                try {
                    long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                    String name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
                    String uri = MediaStore.Files.getContentUri("external") + "/" + id;
                    // read text
                    StringBuilder sb = new StringBuilder();
                    InputStream is = activity.getContentResolver().openInputStream(android.net.Uri.parse(uri));
                    if (is != null) {
                        byte[] buf = new byte[8192];
                        int total = 0, n;
                        while ((n = is.read(buf)) != -1 && total < 512 * 1024) {
                            sb.append(new String(buf, 0, n, "UTF-8"));
                            total += n;
                        }
                        is.close();
                    }
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("name", name);
                    o.put("uri", uri);
                    o.put("text", sb.toString());
                    out.put(o);
                } catch (Exception ignored) {}
            }
            c.close();
        } catch (Exception e) {
            Log.w(TAG, "cue scan failed: " + e.getMessage());
        }
        return out.toString();
    }

    private void updatePlaybackState(int state, long positionMs, float speed) {
        if (mediaSession == null) return;
        try {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                    PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                    PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP;

            PlaybackState.Builder psb = new PlaybackState.Builder();
            psb.setActions(actions);
            psb.setState(state, positionMs, speed);
            mediaSession.setPlaybackState(psb.build());
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public String scanDeviceMusic(boolean filterShortTracks) {
        JSONArray songList = new JSONArray();
        try {
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.GENRE
            };

            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            if (filterShortTracks) {
                selection += " AND " + MediaStore.Audio.Media.DURATION + " >= 30000";
            }
            String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

            Cursor cursor = activity.getContentResolver().query(
                    musicUri,
                    projection,
                    selection,
                    null,
                    sortOrder
            );

            if (cursor != null) {
                int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                int yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
                int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                int trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
                int genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album = cursor.getString(albumCol);
                    long duration = cursor.getLong(durCol);
                    String path = cursor.getString(dataCol);
                    long albumId = cursor.getLong(albumIdCol);
                    int year = yearCol != -1 ? cursor.getInt(yearCol) : 2026;
                    String mime = mimeCol != -1 ? cursor.getString(mimeCol) : "audio/mpeg";
                    int trackNo = trackCol != -1 ? cursor.getInt(trackCol) : 0;
                    String genre = genreCol != -1 ? cursor.getString(genreCol) : "";

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    Uri albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);

                    JSONObject song = new JSONObject();
                    song.put("id", id);
                    song.put("title", title != null ? title : "Unknown Track");
                    song.put("artist", artist != null && !artist.equals("<unknown>") ? artist : "Unknown Artist");
                    song.put("album", album != null ? album : "Unknown Album");
                    song.put("genre", genre != null && !genre.equals("<unknown>") ? genre : "");
                    song.put("year", year > 0 ? year : 2026);
                    song.put("duration", duration > 0 ? duration / 1000 : 0);
                    song.put("trackNumber", normalizeTrackNumber(trackNo));
                    song.put("mimeType", mime);
                    song.put("path", contentUri.toString());
                    song.put("filePath", path != null ? path : "");
                    song.put("albumId", albumId);
                    song.put("artwork", albumArtUri.toString());
                    song.put("favorite", 0);
                    song.put("play_count", 0);
                    song.put("lyrics", "");
                    song.put("imported", true);

                    songList.put(song);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying MediaStore: " + e.getMessage());
        }
        return songList.toString();
    }

    @JavascriptInterface
    public String scanDeviceMusic() {
        return scanDeviceMusic(true);
    }

    /* ======================================================================
       POWERAMP MUSIC FOLDERS (SAF tree picker + persistent library roots)
       ====================================================================== */

    @JavascriptInterface
    public void pickFolderTree() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            activity.startActivityForResult(intent, MainActivity.FOLDER_TREE_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "pickFolderTree failed: " + e.getMessage());
        }
    }

    /** Called from MainActivity after the system folder picker returns. */
    public void handleFolderPicked(Uri folderUri) {
        if (folderUri == null) return;
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Log.w(TAG, "Persistable grant failed: " + e.getMessage());
        }
        String name = queryRootName(folderUri);
        String uriStr = folderUri.toString();
        folderEngine.addRoot(uriStr, name);
        JSONObject o = new JSONObject();
        try {
            o.put("uri", uriStr);
            o.put("name", name);
        } catch (Exception ignored) {}
        runOnJs("if (typeof window.onFolderRootPicked === 'function') { window.onFolderRootPicked(" + o.toString() + "); }");
    }

    private String queryRootName(Uri uri) {
        try {
            Cursor c = activity.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0 && c.moveToFirst()) return c.getString(i);
                } finally { c.close(); }
            }
        } catch (Exception ignored) {}
        String seg = uri.getLastPathSegment();
        if (seg != null) return seg.replace("primary:", "");
        return "Music";
    }

    @JavascriptInterface
    public String getFolderRoots() {
        if (folderEngine == null) return "[]";
        return folderEngine.getRoots(true).toString();
    }

    @JavascriptInterface
    public boolean isFolderScanning(String rootUri) {
        return folderEngine != null && folderEngine.isScanning(rootUri);
    }

    @JavascriptInterface
    public void scanFolder(String rootUri, boolean full, long ignoreShortMs) {
        if (folderEngine == null) return;
        if (!full && !rootUriAvailable(rootUri)) return; // JS shows the restore CTA
        folderEngine.scan(rootUri, full, ignoreShortMs);
    }

    private boolean rootUriAvailable(String rootUri) {
        try {
            Uri tree = Uri.parse(rootUri);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree));
            Cursor c = activity.getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
            if (c != null) c.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public int removeFolder(String rootUri) {
        int n = folderEngine != null ? folderEngine.removeRoot(rootUri) : 0;
        try {
            activity.getContentResolver().releasePersistableUriPermission(
                    Uri.parse(rootUri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        return n;
    }

    @JavascriptInterface
    public void setFolderEnabled(String rootUri, boolean enabled) {
        if (folderEngine != null) folderEngine.setEnabled(rootUri, enabled);
    }

    @JavascriptInterface
    public long getFolderLastScan(String rootUri) {
        return folderEngine != null ? folderEngine.lastScan(rootUri) : 0;
    }

    @JavascriptInterface
    public void vibrate(long milliseconds) {
        try {
            Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(milliseconds);
            }
        } catch (Exception ignored) {}
    }

    private int parseTrackNumber(String raw) {
        if (raw == null) return 0;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(raw);
            if (m.find()) return normalizeTrackNumber(Integer.parseInt(m.group()));
        } catch (Exception ignored) {}
        return 0;
    }

    private int normalizeTrackNumber(int raw) {
        // Android MediaStore sometimes stores disc*1000 + track. Keep only the
        // visible track number for natural album ordering.
        int v = Math.abs(raw);
        if (v > 1000) v = v % 1000;
        return v > 0 && v < 1000 ? v : 0;
    }

    private long stableTrackId(String key) {
        // Stable 53-bit positive id: safe for JavaScript Number equality and
        // far larger than the old random/tiny buckets that caused collisions.
        if (key == null) key = "";
        long h = 0xcbf29ce484222325L; // FNV-1a 64-bit offset
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x100000001b3L;
        }
        return h & 0x1fffffffffffffL; // Number.MAX_SAFE_INTEGER mask
    }

    private void runOnJs(final String jsCode) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.evaluateJavascript(jsCode, null);
                }
            }
        });
    }

    public void release() {
        unregisterNoisyReceiver();
        abandonAudioFocus();
        updateKeepScreenOn(false);

        if (volumeObserver != null) {
            try { activity.getContentResolver().unregisterContentObserver(volumeObserver); } catch (Exception ignored) {}
            volumeObserver = null;
        }

        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {}
            mediaSession = null;
        }

        if (nativeEqualizer != null) {
            try { nativeEqualizer.release(); } catch (Exception ignored) {}
            nativeEqualizer = null;
        }
        if (nativeBassBoost != null) {
            try { nativeBassBoost.release(); } catch (Exception ignored) {}
            nativeBassBoost = null;
        }
        if (nativeVirtualizer != null) {
            try { nativeVirtualizer.release(); } catch (Exception ignored) {}
            nativeVirtualizer = null;
        }
        if (nextMediaPlayer != null) {
            try { nextMediaPlayer.release(); } catch (Exception ignored) {}
            nextMediaPlayer = null;
        }
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }
}
