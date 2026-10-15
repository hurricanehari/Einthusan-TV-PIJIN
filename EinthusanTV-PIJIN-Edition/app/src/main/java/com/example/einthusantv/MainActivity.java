package com.example.einthusantv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** PIJIN Edition v0.2: native TV home, safe browsing fallback, and a native Media3 player. */
public class MainActivity extends Activity {
    private static final String HOME = "https://einthusan.tv/";
    private FrameLayout root;
    private FrameLayout webLayer;
    private FrameLayout fullLayer;
    private WebView browser;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private boolean webShowing = false;

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        root = new FrameLayout(this); root.setBackgroundColor(Color.rgb(12,15,24)); setContentView(root);
        showHome();
    }
    private void showHome() {
        webShowing = false;
        if (webLayer != null) webLayer.setVisibility(View.GONE);
        if (root.findViewWithTag("home") != null) root.removeView(root.findViewWithTag("home"));
        ScrollView scroller = new ScrollView(this); scroller.setTag("home"); scroller.setFillViewport(true);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(52),dp(30),dp(52),dp(24)); scroller.addView(panel);
        root.addView(scroller, new FrameLayout.LayoutParams(-1,-1));
        TextView brand = label("EINTHUSAN TV", 34, Color.WHITE);
        panel.addView(brand);
        TextView edition = label("PIJIN EDITION  •  Native TV preview", 17, 0xff74d8eb);
        panel.addView(edition);
        TextView subtitle = label("Browse films or try the built-in video player", 20, 0xffd0d6e0);
        subtitle.setPadding(0,dp(35),0,dp(25)); panel.addView(subtitle);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(row);
        addTile(row, "Browse Einthusan", () -> showWeb(HOME));
        addTile(row, "Native video player", this::askStreamUrl);
        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0,dp(16),0,0); panel.addView(row2);
        addTile(row2, "About PIJIN", this::showAbout);
        TextView note = label("The catalogue still opens in the existing website view. Native playback works only with a direct, permitted MP4/HLS video URL; no Einthusan stream extraction is implemented.", 16, 0xffa8b2c3);
        note.setPadding(0,dp(36),0,0); panel.addView(note);
    }
    private void addTile(LinearLayout row, String text, Runnable callback) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(18);
        b.setFocusable(true); b.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(80),1);
        lp.setMargins(0,0,dp(14),0); row.addView(b,lp);
    }
    private void askStreamUrl() {
        EditText input = new EditText(this); input.setSingleLine(true);
        input.setHint("https://example.org/video.mp4 or .m3u8");
        input.setInputType(17);
        new AlertDialog.Builder(this).setTitle("Play a direct video URL")
            .setMessage("Use a video URL you are authorised to access. This cannot extract video from Einthusan pages.")
            .setView(input)
            .setPositiveButton("Play", (d,w) -> {
                String url = input.getText().toString().trim();
                if (url.startsWith("https://")) {
                    Intent i = new Intent(this,PlayerActivity.class);
                    i.putExtra("video_url",url); startActivity(i);
                } else {
                    new AlertDialog.Builder(this).setMessage("Enter a valid HTTPS video URL.").setPositiveButton("OK",null).show();
                }
            }).setNegativeButton("Cancel",null).show();
    }
    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Einthusan TV • PIJIN Edition")
            .setMessage("Special shout-out to PIJIN! ✨\n\nNative TV preview v0.2. Independent experimental project, not affiliated with Einthusan or EinTamil.\n\nThe built-in Media3 player is a separate proof of concept. Einthusan website playback remains in the browser view.")
            .setPositiveButton("Back",null).show();
    }
    private void showWeb(String url) {
        View home = root.findViewWithTag("home"); if (home != null) root.removeView(home);
        webShowing = true;
        if (webLayer == null) createBrowser();
        webLayer.setVisibility(View.VISIBLE);
        browser.loadUrl(url);
        browser.requestFocus();
    }
    private void createBrowser() {
        webLayer = new FrameLayout(this); root.addView(webLayer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        webLayer.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xff141926); column.addView(bar,new LinearLayout.LayoutParams(-1,dp(55)));
        navButton(bar,"← PIJIN Home", () -> showHome());
        navButton(bar,"Back", () -> { if(browser.canGoBack()) browser.goBack(); else showHome(); });
        navButton(bar,"Reload", () -> browser.reload());
        browser = new WebView(this); browser.setBackgroundColor(Color.BLACK);
        browser.setFocusable(true); browser.setFocusableInTouchMode(true);
        WebSettings ws = browser.getSettings(); ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true); ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setAllowFileAccess(false); ws.setAllowContentAccess(false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String target) {
                return !(target.equals("https://einthusan.tv") || target.startsWith("https://einthusan.tv/"));
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView=v; customCallback=cb;
                fullLayer=new FrameLayout(MainActivity.this); fullLayer.setBackgroundColor(Color.BLACK);
                fullLayer.addView(v,new FrameLayout.LayoutParams(-1,-1));
                root.addView(fullLayer,new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView() { closeFullscreen(); }
        });
        column.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void navButton(LinearLayout bar, String title, Runnable fn) {
        Button b=new Button(this); b.setText(title); b.setAllCaps(false);
        b.setOnClickListener(v -> fn.run()); bar.addView(b);
    }
    private void closeFullscreen() {
        if (customView==null) return;
        root.removeView(fullLayer); fullLayer.removeAllViews(); fullLayer=null; customView=null;
        if(customCallback!=null) customCallback.onCustomViewHidden(); customCallback=null;
    }
    @Override public boolean onKeyDown(int code,KeyEvent e) {
        if(code==KeyEvent.KEYCODE_BACK) {
            if(customView!=null) closeFullscreen();
            else if(webShowing && browser!=null && browser.canGoBack()) browser.goBack();
            else if(webShowing) showHome();
            else return super.onKeyDown(code,e);
            return true;
        }
        return super.onKeyDown(code,e);
    }
    @Override protected void onDestroy() { if(browser!=null) browser.destroy(); super.onDestroy(); }
}
