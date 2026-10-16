package com.example.einthusantv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;
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

/** Native Media3 HLS player, using Kodi-compatible HTTP headers. */
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    @Override @SuppressWarnings("unchecked") public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        String url=getIntent().getStringExtra("video_url");
        if(url==null||!url.startsWith("https://")){finish();return;}
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
        final boolean requestedUltra=getIntent().getBooleanExtra("requested_ultra",false);
        player.addListener(new Player.Listener(){
            private boolean shownResolution=false;
            @Override public void onVideoSizeChanged(VideoSize size){
                if(shownResolution||size.width<=0||size.height<=0)return;
                shownResolution=true;
                String resolution=size.width+" × "+size.height;
                String level=size.width>=3840 && size.height>=2160 ? "2160p 4K" :
                    size.height>=1080?"1080p" : size.height>=720?"720p":"SD";
                String message="Playing "+level+" ("+resolution+")";
                if(requestedUltra && size.height<2160)
                    message+=" · Ultra HD request did not deliver 4K";
                Toast.makeText(PlayerActivity.this,message,Toast.LENGTH_LONG).show();
            }
            @Override public void onPlayerError(PlaybackException error){
                Toast.makeText(PlayerActivity.this,"Playback failed: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
        });
        MediaItem video=new MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build();
        player.setMediaItem(video);player.prepare();player.play();
    }
    @Override protected void onStop(){if(player!=null){player.release();player=null;}super.onStop();}
}
