package com.example.einthusantv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;

/** Unofficial testing wrapper for the public Einthusan site. No Kodi integration or stream extraction. */
public class MainActivity extends Activity {
    private static final String HOME = "https://einthusan.tv/";
    private WebView browser;
    private FrameLayout root;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 15, 24));
        setContentView(root);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        root.addView(layout, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setBackgroundColor(Color.rgb(20, 25, 38));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView title = new TextView(this);
        title.setText("EINTHUSAN  •  PIJIN EDITION"); title.setTextColor(Color.WHITE); title.setTextSize(20);
        title.setPadding(dp(22), 0, dp(16), 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        addButton(toolbar, "← Back", v -> navigateBack());
        addButton(toolbar, "Home", v -> browser.loadUrl(HOME));
        addButton(toolbar, "Reload", v -> browser.reload());
        addButton(toolbar, "About", v -> showAbout());
        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        browser.setFocusable(true);
        browser.setFocusableInTouchMode(true);
        WebSettings s = browser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Do not silently navigate to unknown third-party domains.
                return !url.startsWith("https://einthusan.tv/");
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
                fullscreenView = view; fullscreenCallback = callback;
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setBackgroundColor(Color.BLACK);
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(-1, -1));
                root.addView(fullscreenContainer, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() { closeFullscreen(); }
        });
        layout.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        browser.loadUrl(HOME);
    }
    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("Einthusan TV • PIJIN Edition")
            .setMessage("Special shout-out to PIJIN! ✨\n\nThanks for the idea of bringing an easy-to-use TV experience to the Firestick.\n\nUnofficial experimental website-wrapper prototype; not affiliated with Einthusan or EinTamil.")
            .setPositiveButton("Back to movies", (dialog, which) -> dialog.dismiss())
            .show();
    }
    private void addButton(LinearLayout parent, String text, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(text); button.setTextColor(Color.WHITE); button.setAllCaps(false);
        button.setOnClickListener(action);
        parent.addView(button, new LinearLayout.LayoutParams(-2, dp(52)));
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private void closeFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenContainer);
        fullscreenContainer.removeAllViews();
        fullscreenView = null; fullscreenContainer = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }
    private void navigateBack() {
        if (fullscreenView != null) closeFullscreen();
        else if (browser != null && browser.canGoBack()) browser.goBack();
        else if (browser != null) browser.loadUrl(HOME);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { navigateBack(); return true; }
        return super.onKeyDown(keyCode, event);
    }
    @Override protected void onDestroy() {
        if (browser != null) { browser.destroy(); browser = null; }
        super.onDestroy();
    }
}
