package com.example.einthusantv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Elil TV — native cinematic player. The system player renders only video;
 * the remote-first controls are owned by this activity, not an Android widget.
 * Trick-play is timeline scrubbing (no unsupported reverse audio playback).
 */
public final class PlayerActivity extends Activity {
    private static final int BG=0xff070c14, GOLD=0xfff0c47f, WHITE=0xfff7f8fa;
    private static final int MUTED=0xffc3cdd8;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private PlayerView videoView;
    private LinearLayout controls;
    private TextView upperTitle;
    private TextView positionText,durationText,speedBadge,titleText;
    private ProgressBar timeline;
    private ViewingStore history;
    private Film film;
    private String profileId;
    private boolean ultra,ready=false,completed=false,foreground=false,wasPlayingAtStop=false;
    private boolean controlsVisible=false,holding=false,wasPlayingBeforeTrickplay=false,wasPlayingBeforeHold=false;
    private long previewMs=-1;
    private int holdDirection=0,trickDirection=0,trickStep=-1;
    private int targetSpeed=1;
    private long lastPersist=0;

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable gradient(int[] colors){
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,colors);
    }
    private TextView label(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private void refreshKeepScreenOn(){
        boolean playing=foreground&&player!=null&&!completed&&
            player.getPlayWhenReady()&&
            (player.getPlaybackState()==Player.STATE_READY ||
             player.getPlaybackState()==Player.STATE_BUFFERING);
        if(playing)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(videoView!=null)videoView.setKeepScreenOn(playing);
    }
    @Override @SuppressWarnings("unchecked") public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        String url=getIntent().getStringExtra("video_url");
        if(url==null||!url.startsWith("https://")){finish();return;}
        film=new Film(extra("film_id"),extra("film_language"),extra("video_title"),
            extra("film_poster"),extra("film_synopsis"),extra("film_url"),
            extra("film_quality"),extra("film_badge"));
        profileId=extra("profile_id");
        ultra=getIntent().getBooleanExtra("requested_ultra",false);
        history=new ViewingStore(this);
        long start=Math.max(0L,getIntent().getLongExtra("start_position_ms",0));
        Map<String,String> headers=new HashMap<>();
        Object data=getIntent().getSerializableExtra("video_headers");
        if(data instanceof HashMap)headers.putAll((HashMap<String,String>)data);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent(EinthusanClient.UA)
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(14000)
            .setReadTimeoutMs(16000);
        player=new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(
                new DefaultDataSource.Factory(this,http))).build();
        videoView=new PlayerView(this);
        videoView.setUseController(false);
        videoView.setBackgroundColor(Color.BLACK);
        videoView.setShutterBackgroundColor(Color.BLACK);
        videoView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        videoView.setKeepScreenOn(false);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(videoView,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        setupOverlay(root);
        videoView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY){ready=true;persist(false);}
                else if(state==Player.STATE_ENDED){completed=true;endTrickplay(false);persist(true);showControls();}
                refreshKeepScreenOn();updateProgress();
            }
            @Override public void onPlayWhenReadyChanged(boolean playWhenReady,int reason){
                refreshKeepScreenOn();updateProgress();
            }
            @Override public void onIsPlayingChanged(boolean playing){
                refreshKeepScreenOn();if(!playing)persist(completed);
                updateProgress();
            }
            @Override public void onPlayerError(PlaybackException error){
                endTrickplay(false);persist(false);refreshKeepScreenOn();
                showControls();
                Toast.makeText(PlayerActivity.this,
                    "Playback unavailable: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
        });
        MediaItem item=new MediaItem.Builder().setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8).build();
        player.setMediaItem(item);player.prepare();
        if(start>0)player.seekTo(start);
        player.play();
        hideControls();
        handler.post(ticker);
    }
    private String extra(String key){String s=getIntent().getStringExtra(key);return s==null?"":s;}
    /** Disney+-inspired information-only overlay. No focusable on-screen controls.
     * The Fire TV remote owns all playback commands.
     */
    private void setupOverlay(FrameLayout root){
        upperTitle=label(film.title,16,WHITE,true);
        upperTitle.setSingleLine(true);
        upperTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        upperTitle.setPadding(dp(38),dp(14),dp(38),0);
        upperTitle.setGravity(Gravity.CENTER_VERTICAL);
        upperTitle.setBackground(gradient(new int[]{0xaa080c14,0x00080c14}));
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP);
        root.addView(upperTitle,top);
        upperTitle.setVisibility(View.GONE);
        upperTitle.setAlpha(0f);

        controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(38),dp(22),dp(38),dp(13));
        controls.setBackground(gradient(new int[]{0x00080c14,0x88080c14,0xe8080c14}));
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,dp(88),Gravity.BOTTOM);
        root.addView(controls,bottom);

        LinearLayout upper=new LinearLayout(this);upper.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(upper,new LinearLayout.LayoutParams(-1,dp(24)));
        titleText=label("",12,MUTED,false);
        upper.addView(titleText,new LinearLayout.LayoutParams(0,-1,1));
        speedBadge=label("",12,GOLD,true);
        speedBadge.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        upper.addView(speedBadge,new LinearLayout.LayoutParams(dp(115),-1));

        timeline=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        timeline.setMax(1000);
        timeline.setProgressTintList(ColorStateList.valueOf(GOLD));
        timeline.setProgressBackgroundTintList(ColorStateList.valueOf(0xff64717d));
        LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(3));
        line.topMargin=dp(2);controls.addView(timeline,line);

        LinearLayout times=new LinearLayout(this);times.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(-1,dp(24));
        t.topMargin=dp(2);controls.addView(times,t);
        positionText=label("0:00",12,WHITE,false);
        durationText=label("—",12,WHITE,false);
        durationText.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        times.addView(positionText,new LinearLayout.LayoutParams(0,-1,1));
        times.addView(durationText,new LinearLayout.LayoutParams(0,-1,1));
        controls.setFocusable(false);
        controls.setVisibility(View.GONE);
        controls.setAlpha(0f);
    }
    private String time(long ms){
        if(ms<0)return "—";
        long sec=ms/1000;
        return String.format(Locale.UK,"%d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);
    }
    private long bounded(long desired){
        if(player==null)return 0;
        long duration=player.getDuration();
        if(duration==C.TIME_UNSET||duration<=0)return Math.max(0,desired);
        return Math.max(0,Math.min(desired,duration-400));
    }
    private void seekBy(long ms){
        if(player==null)return;
        if(previewMs>=0)previewMs=bounded(previewMs+ms);
        else player.seekTo(bounded(player.getCurrentPosition()+ms));
        showControls();updateProgress();
    }
    private void updateProgress(){
        if(player==null||timeline==null)return;
        long duration=player.getDuration(),position=Math.max(0,
            previewMs>=0?previewMs:player.getCurrentPosition());
        boolean valid=duration!=C.TIME_UNSET&&duration>0;
        timeline.setProgress(valid?(int)Math.min(1000,position*1000/duration):0);
        positionText.setText(time(position));durationText.setText(valid?time(duration):"—");
        boolean paused=!player.getPlayWhenReady();
        if(trickDirection!=0)speedBadge.setText((trickDirection>0?"▶▶ ":"◀◀ ")+targetSpeed+"×");
        else if(holding)speedBadge.setText((holdDirection>0?"▶ ":"◀ ")+"SCRUB");
        else speedBadge.setText(paused?"PAUSED":"");
    }
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            updateProgress();
            if(player!=null&&player.isPlaying()&&android.os.SystemClock.elapsedRealtime()-lastPersist>=10000){
                persist(false);lastPersist=android.os.SystemClock.elapsedRealtime();
            }
            handler.postDelayed(this,300);
        }
    };
    private final Runnable hide=new Runnable(){
        @Override public void run(){
            if(player!=null&&player.getPlayWhenReady()&&!holding&&trickDirection==0)hideControls();
        }
    };
    private void scheduleHide(){handler.removeCallbacks(hide);handler.postDelayed(hide,1050);}
    private void showControls(){
        handler.removeCallbacks(hide);
        if(controls==null)return;
        controls.animate().cancel();upperTitle.animate().cancel();
        controls.setVisibility(View.VISIBLE);upperTitle.setVisibility(View.VISIBLE);
        controls.animate().alpha(1f).setDuration(160).start();
        upperTitle.animate().alpha(1f).setDuration(160).start();
        controlsVisible=true;
        if(player!=null&&player.getPlayWhenReady()&&trickDirection==0&&!holding)
            scheduleHide();
    }
    private void hideControls(){
        handler.removeCallbacks(hide);
        if(controls==null)return;
        controlsVisible=false;
        controls.animate().cancel();upperTitle.animate().cancel();
        controls.animate().alpha(0f).setDuration(190)
            .withEndAction(()->{if(!controlsVisible)controls.setVisibility(View.GONE);}).start();
        upperTitle.animate().alpha(0f).setDuration(190)
            .withEndAction(()->{if(!controlsVisible)upperTitle.setVisibility(View.GONE);}).start();
    }
    private final Runnable scrub=new Runnable(){
        @Override public void run(){
            if(player==null)return;
            if(holding&&holdDirection!=0){
                // Each 110ms tick advances 0.7s: visible responsive scrubbing.
                previewMs=bounded(previewMs+holdDirection*700L);
                updateProgress();handler.postDelayed(this,110);
            }
        }
    };
    private final Runnable trickTick=new Runnable(){
        @Override public void run(){
            if(player==null||trickDirection==0)return;
            previewMs=bounded(previewMs+trickDirection*targetSpeed*220L);
            updateProgress();handler.postDelayed(this,220);
        }
    };
    private void stopHold(){
        holding=false;holdDirection=0;handler.removeCallbacks(scrub);
        if(previewMs>=0&&player!=null){player.seekTo(previewMs);previewMs=-1;}
        if(player!=null&&wasPlayingBeforeHold)player.play();
        updateProgress();scheduleHide();
    }
    private void startHold(int direction){
        if(holding||trickDirection!=0)return;
        holding=true;holdDirection=direction;
        wasPlayingBeforeHold=player.getPlayWhenReady();
        previewMs=player.getCurrentPosition();player.pause();
        handler.removeCallbacks(scrub);handler.post(scrub);showControls();
    }
    private void endTrickplay(boolean resume){
        if(trickDirection==0)return;
        trickDirection=0;trickStep=-1;targetSpeed=1;
        handler.removeCallbacks(trickTick);
        if(previewMs>=0&&player!=null){player.seekTo(previewMs);previewMs=-1;}
        if(resume&&player!=null){
            if(wasPlayingBeforeTrickplay)player.play();else player.pause();
        }
        updateProgress();
    }
    private void cycleTrickplay(int direction){
        if(player==null)return;
        if(trickDirection==0){
            wasPlayingBeforeTrickplay=player.getPlayWhenReady();
            previewMs=player.getCurrentPosition();
        }
        trickStep=trickDirection==direction?(trickStep+1)%3:0;
        trickDirection=direction;
        targetSpeed=new int[]{2,4,8}[trickStep];
        player.pause();
        handler.removeCallbacks(trickTick);
        handler.post(trickTick);
        showControls();updateProgress();
    }
    private void toggleWithOk(){
        if(player==null)return;
        if(trickDirection!=0){
            endTrickplay(false);
            player.play();
            showControls();
        }else if(player.getPlayWhenReady()){
            player.pause();
            showControls(); // Keep visible while paused with OK.
        }else{
            player.play();
            showControls(); // Hides after one second of playback.
        }
        updateProgress();
    }
    private void hardwarePlayback(int key){
        if(player==null)return;
        if(trickDirection!=0)endTrickplay(false);
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY)player.play();
        else if(key==KeyEvent.KEYCODE_MEDIA_PAUSE)player.pause();
        else if(player.getPlayWhenReady())player.pause();
        else player.play();
        // Physical Play/Pause must never summon an overlay.
        hideControls();updateProgress();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(player==null)return super.dispatchKeyEvent(event);
        int key=event.getKeyCode(),action=event.getAction();
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
            int dir=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
            if(action==KeyEvent.ACTION_DOWN){
                if(event.getRepeatCount()>0 && event.getEventTime()-event.getDownTime()>360){
                    startHold(dir);
                } else if(event.getRepeatCount()==0){showControls();}
                return true;
            }
            if(action==KeyEvent.ACTION_UP){
                if(holding)stopHold();else seekBy(dir*10000L);
                return true;
            }
        }
        if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND){
            if(action==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)
                cycleTrickplay(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?1:-1);
            return true;
        }
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||
           key==KeyEvent.KEYCODE_MEDIA_PAUSE){
            if(action==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)
                hardwarePlayback(key);
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
            if(action==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)
                toggleWithOk();
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(action==KeyEvent.ACTION_DOWN)showControls();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void persist(boolean ended){
        if(player==null||!ready||history==null||film.id.isEmpty())return;
        long duration=player.getDuration();if(duration==C.TIME_UNSET||duration<0)duration=0;
        long pos=Math.max(0,player.getCurrentPosition());
        history.savePosition(profileId,film,pos,duration,ultra,ended);
    }
    @Override public void onBackPressed(){
        if(holding)stopHold();
        if(trickDirection!=0)endTrickplay(true);
        if(controlsVisible){hideControls();return;}
        super.onBackPressed();
    }
    @Override protected void onStart(){super.onStart();}
    @Override protected void onResume(){
        super.onResume();foreground=true;
        if(wasPlayingAtStop&&player!=null){player.play();wasPlayingAtStop=false;}
        refreshKeepScreenOn();
    }
    @Override protected void onPause(){
        if(holding)stopHold();
        if(trickDirection!=0)endTrickplay(false);
        foreground=false;refreshKeepScreenOn();persist(completed);super.onPause();
    }
    @Override protected void onStop(){
        if(player!=null){
            wasPlayingAtStop=player.isPlaying();persist(completed);player.pause();
        }
        foreground=false;refreshKeepScreenOn();super.onStop();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(player!=null){persist(completed);player.release();player=null;}
        foreground=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
