package com.audify.music;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    public static final int FILE_CHOOSER_REQUEST = 2001;
    public static final int AUDIO_PICKER_REQUEST = 2002;
    public static final int FOLDER_TREE_REQUEST = 2003;

    private WebView webView;
    private AudifyBridge bridge;
    private HostedChromeClient chromeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#08090D"));
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(Color.parseColor("#08090D"));
        }

        setContentView(R.layout.activity_main);

        // v1.44 swipe-survival: if the bridge survived an Activity teardown while
        // playing, the SAME WebView (and its live audio engine + JS state) is
        // re-attached instead of cold-loading the app again.
        android.widget.FrameLayout container = findViewById(R.id.webview_container);
        if (AppHolder.webView != null && AppHolder.bridge != null) {
            webView = AppHolder.webView;
            if (webView.getParent() != null) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            container.addView(webView, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            bridge = AppHolder.bridge;
            bridge.attachActivity(this);
            chromeClient = AppHolder.chromeClient;
            if (chromeClient != null) {
                chromeClient.cancelChooser(); // any chooser on the dead activity is void
                chromeClient.setHost(this);
            }
        } else {
            webView = new WebView(this);
            webView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(webView);
            setupWebView();

            bridge = new AudifyBridge(this, webView);
            webView.addJavascriptInterface(bridge, "AndroidBridge");
            AppHolder.webView = webView;
            AppHolder.bridge = bridge;
            AppHolder.chromeClient = chromeClient;

            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(Color.TRANSPARENT);

        chromeClient = new HostedChromeClient();
        chromeClient.setHost(this);
        webView.setWebChromeClient(chromeClient);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("if (window.onAudifyAndroidReady) { window.onAudifyAndroidReady(); }", null);
            }
        });
    }

    /** Launches the system file picker for the current host activity (host of
     * record for HostedChromeClient, kept swappable across Activity relaunches). */
    void launchFilePicker(WebChromeClient.FileChooserParams params) {
        try {
            Intent intent = params.createIntent();
            startActivityForResult(intent, FILE_CHOOSER_REQUEST);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select Audio Files"), FILE_CHOOSER_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            ValueCallback<Uri[]> callback = chromeClient != null ? chromeClient.consumeChooser() : null;
            if (callback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
                callback.onReceiveValue(results);
            }
        } else if (requestCode == AUDIO_PICKER_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null && bridge != null) {
                bridge.handleAudioPickerResult(data);
            }
        } else if (requestCode == FOLDER_TREE_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && bridge != null) {
                bridge.handleFolderPicked(data.getData());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            webView.evaluateJavascript("if (window.onVisualizerPermissionResult) { window.onVisualizerPermissionResult(" + granted + "); }", null);
            return;
        }
        if (requestCode == 101) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            webView.evaluateJavascript("if (window.onStoragePermissionResult) { window.onStoragePermissionResult(" + granted + "); }", null);
        }
        // requestCode 103 (POST_NOTIFICATIONS): no JS fanfare — denial only hides
        // the notification, never playback.
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("if (window.handleAndroidBack) { window.handleAndroidBack(); } else { 'false'; }", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"\"true\"".equals(value) && !"true".equals(value)) {
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        boolean keepAlive = false;
        try { keepAlive = (bridge != null && bridge.isPlaying()); } catch (Exception ignored) {}

        if (keepAlive) {
            // Poweramp-grade background behavior: swiping the app away while music
            // plays must NOT kill playback. PlaybackService keeps the process
            // foreground; AppHolder keeps this WebView (+ its MediaPlayer through
            // the bridge) alive; onCreate re-attaches it on relaunch.
            if (chromeClient != null) chromeClient.setHost(null);
        } else {
            AppHolder.webView = null;
            AppHolder.bridge = null;
            AppHolder.chromeClient = null;
            if (chromeClient != null) chromeClient.setHost(null);
            if (bridge != null) {
                bridge.release();
            }
            if (webView != null) {
                try { webView.removeAllViews(); } catch (Exception ignored) {}
                webView.destroy();
            }
        }
        super.onDestroy();
    }

    /**
     * WebChromeClient whose host Activity can be SWAPPED. Survived WebViews keep
     * this client across Activity destruction; on relaunch the new activity
     * becomes the host so dialogs/choosers never target a destroyed window.
     */
    static final class HostedChromeClient extends WebChromeClient {
        private MainActivity host;
        private ValueCallback<Uri[]> pendingChooser;

        synchronized void setHost(MainActivity h) { host = h; }

        synchronized ValueCallback<Uri[]> consumeChooser() {
            ValueCallback<Uri[]> c = pendingChooser;
            pendingChooser = null;
            return c;
        }

        synchronized void cancelChooser() {
            if (pendingChooser != null) {
                try { pendingChooser.onReceiveValue(null); } catch (Exception ignored) {}
                pendingChooser = null;
            }
        }

        private android.content.Context dialogHost(WebView view) {
            MainActivity h;
            synchronized (this) { h = host; }
            return h != null ? h : view.getContext();
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
            new android.app.AlertDialog.Builder(dialogHost(view))
                    .setMessage(message)
                    .setPositiveButton("Yes", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) { result.confirm(); }
                    })
                    .setNegativeButton("Cancel", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) { result.cancel(); }
                    })
                    .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                        public void onCancel(android.content.DialogInterface d) { result.cancel(); }
                    })
                    .show();
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
            new android.app.AlertDialog.Builder(dialogHost(view))
                    .setMessage(message)
                    .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) { result.confirm(); }
                    })
                    .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                        public void onCancel(android.content.DialogInterface d) { result.cancel(); }
                    })
                    .show();
            return true;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {
            MainActivity h;
            synchronized (this) { h = host; }
            if (h == null || h.isDestroyed() || h.isFinishing()) {
                try { callback.onReceiveValue(null); } catch (Exception ignored) {}
                return false;
            }
            cancelChooser();
            synchronized (this) { pendingChooser = callback; }
            try {
                h.launchFilePicker(fileChooserParams);
                return true;
            } catch (Exception e) {
                cancelChooser();
                return false;
            }
        }
    }
}
