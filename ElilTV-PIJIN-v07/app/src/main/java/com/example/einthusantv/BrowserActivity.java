package com.example.einthusantv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** The website fallback is separate from native browsing AND native account sign-in. */
public class BrowserActivity extends Activity {
    private FrameLayout root, fullscreen;
    private WebView web;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        root=new FrameLayout(this);root.setBackgroundColor(0xff0a0a12);setContentView(root);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        root.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(this);layout.addView(bar);
        button(bar,"← ELIL TV",()->finish());
        button(bar,"Back",()->{if(web.canGoBack())web.goBack();else finish();});
        button(bar,"Reload",()->web.reload());
        web=new WebView(this);web.setBackgroundColor(0xff080808);web.setFocusableInTouchMode(true);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String address){
                return !address.startsWith("https://einthusan.tv/") && !address.equals("https://einthusan.tv");
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(fullscreenView!=null){callback.onCustomViewHidden();return;}
                fullscreenView=view;fullscreenCallback=callback;
                fullscreen=new FrameLayout(BrowserActivity.this);fullscreen.setBackgroundColor(0xff000000);
                fullscreen.addView(view,new FrameLayout.LayoutParams(-1,-1));
                root.addView(fullscreen,new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView(){closeFullscreen();}
        });
        layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        String url=getIntent().getStringExtra("url");
        if(url==null||!url.startsWith("https://einthusan.tv/"))url=EinthusanClient.BASE;
        web.loadUrl(url);web.requestFocus();
    }
    private void button(LinearLayout bar,String label,Runnable click){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->click.run());bar.addView(b);
    }
    private void closeFullscreen(){
        if(fullscreenView==null)return;
        root.removeView(fullscreen);fullscreen.removeAllViews();fullscreen=null;fullscreenView=null;
        if(fullscreenCallback!=null)fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;
    }
    @Override public boolean onKeyDown(int key,KeyEvent e){
        if(key==KeyEvent.KEYCODE_BACK){
            if(fullscreenView!=null)closeFullscreen();
            else if(web!=null&&web.canGoBack())web.goBack();else finish();
            return true;
        }
        return super.onKeyDown(key,e);
    }
    @Override protected void onPause(){android.webkit.CookieManager.getInstance().flush();super.onPause();}
    @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
