package com.example.einthusantv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.VideoSize;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

/** Native Fire TV player with periodic device-local resume checkpoints. */
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private final Handler checkpointHandler=new Handler(Looper.getMainLooper());
    private ViewingStore history;
    private Film film;
    private String profileId;
    private boolean ultra,playbackReady=false,completed=false,wasPlayingAtStop=false;
    private final Runnable checkpoint=new Runnable(){
        @Override public void run(){
            if(player!=null && player.isPlaying())persist(false);
            checkpointHandler.postDelayed(this,10000);
        }
    };

    @Override @SuppressWarnings("unchecked") public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        String url=getIntent().getStringExtra("video_url");
        if(url==null||!url.startsWith("https://")){finish();return;}
        film=new Film(
            stringExtra("film_id"),stringExtra("film_language"),stringExtra("video_title"),
            stringExtra("film_poster"),stringExtra("film_synopsis"),stringExtra("film_url"),
            stringExtra("film_quality"),stringExtra("film_badge"));
        profileId=stringExtra("profile_id");
        ultra=getIntent().getBooleanExtra("requested_ultra",false);
        history=new ViewingStore(this);
        long seek=Math.max(0L,getIntent().getLongExtra("start_position_ms",0L));
        Map<String,String> headers=new HashMap<>();
        Object supplied=getIntent().getSerializableExtra("video_headers");
        if(supplied instanceof HashMap)headers.putAll((HashMap<String,String>)supplied);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent(EinthusanClient.UA)
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(14000)
            .setReadTimeoutMs(16000);
        DefaultDataSource.Factory dataSource=new DefaultDataSource.Factory(this,http);
        player=new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource)).build();
        PlayerView playerView=new PlayerView(this);
        playerView.setUseController(true);playerView.setControllerAutoShow(true);
        setContentView(playerView);playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            private boolean shownResolution=false;
            @Override public void onVideoSizeChanged(VideoSize size){
                if(shownResolution||size.width<=0||size.height<=0)return;
                shownResolution=true;
                String level=size.width>=3840&&size.height>=2160?"2160p 4K":
                    size.height>=1080?"1080p":size.height>=720?"720p":"SD";
                Toast.makeText(PlayerActivity.this,"Playing "+level+" ("+size.width+" × "+size.height+")",Toast.LENGTH_LONG).show();
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY){playbackReady=true;persist(false);}
                else if(state==Player.STATE_ENDED){completed=true;persist(true);}
            }
            @Override public void onIsPlayingChanged(boolean playing){
                if(!playing)persist(completed);
            }
            @Override public void onPlayerError(PlaybackException error){
                persist(false);
                Toast.makeText(PlayerActivity.this,"Playback failed: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
        });
        MediaItem video=new MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build();
        player.setMediaItem(video);player.prepare();
        if(seek>0)player.seekTo(seek);
        player.play();
    }
    @Override protected void onStart(){
        super.onStart();
        checkpointHandler.removeCallbacks(checkpoint);
        checkpointHandler.postDelayed(checkpoint,10000);
        if(wasPlayingAtStop && player!=null){player.play();wasPlayingAtStop=false;}
    }
    private String stringExtra(String name){String s=getIntent().getStringExtra(name);return s==null?"":s;}
    private void persist(boolean ended){
        if(player==null||!playbackReady||history==null||film.id.isEmpty())return;
        long duration=player.getDuration();if(duration==C.TIME_UNSET||duration<0)duration=0;
        long position=player.getCurrentPosition();if(position<0)position=0;
        history.savePosition(profileId,film,position,duration,ultra,ended);
    }
    @Override protected void onPause(){persist(completed);super.onPause();}
    @Override protected void onStop(){
        checkpointHandler.removeCallbacks(checkpoint);
        if(player!=null){
            wasPlayingAtStop=player.isPlaying();
            persist(completed);
            player.pause();
        }
        super.onStop();
    }
    @Override protected void onDestroy(){
        checkpointHandler.removeCallbacks(checkpoint);
        persist(completed);
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}
