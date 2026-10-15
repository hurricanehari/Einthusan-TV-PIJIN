package com.example.einthusantv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/** Media3-based player for direct, authorised HTTP(S) media URLs. */
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String url=getIntent().getStringExtra("video_url");
        if(url==null || !url.startsWith("https://")) { finish(); return; }
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        setContentView(playerView);
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this,"Can't play this video: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play();
    }
    @Override protected void onStop() {
        if(player!=null) { player.release(); player=null; }
        super.onStop();
    }
}
