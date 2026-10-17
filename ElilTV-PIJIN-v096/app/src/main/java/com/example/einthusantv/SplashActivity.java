package com.example.einthusantv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Branded cold-start opening; ordinary navigation and playback never show it. */
public final class SplashActivity extends Activity {
    private static volatile boolean shownInProcess=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean proceeding=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(shownInProcess){openHome();return;}
        shownInProcess=true;
        getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xff070d17);
        ImageView image=new ImageView(this);
        image.setImageResource(R.drawable.elil_splash);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        // Brief enough not to slow the viewing experience.
        handler.postDelayed(this::openHome,1250);
    }
    private void openHome(){
        if(proceeding)return;
        proceeding=true;
        Intent intent=new Intent(this,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
