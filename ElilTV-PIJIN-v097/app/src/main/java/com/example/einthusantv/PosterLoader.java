package com.example.einthusantv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PosterLoader {
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final LruCache<String,Bitmap> cache = new LruCache<>(48);
    public void load(Activity activity, ImageView view, String address) {
        if (address==null || !address.startsWith("https://")) return;
        view.setTag(address);
        view.animate().cancel();
        float intendedAlpha=view.getAlpha();
        Bitmap cached;
        synchronized(cache){cached=cache.get(address);}
        if(cached!=null) {view.setImageBitmap(cached);view.setAlpha(intendedAlpha);return;}
        view.setImageDrawable(null);
        view.setAlpha(0f);
        workers.submit(()->{
            HttpURLConnection con=null;
            try{
                con=(HttpURLConnection)new URL(address).openConnection();
                con.setConnectTimeout(7000);con.setReadTimeout(9000);con.setInstanceFollowRedirects(false);
                con.setRequestProperty("User-Agent",EinthusanClient.UA);
                con.setRequestProperty("Referer",EinthusanClient.BASE+"/");
                if(con.getResponseCode()!=200||con.getContentLength()>4*1024*1024)return;
                Bitmap bmp;
                try(InputStream in=con.getInputStream()) {bmp=BitmapFactory.decodeStream(in);}
                if(bmp==null)return;
                synchronized(cache){cache.put(address,bmp);}
                activity.runOnUiThread(()->{
                    if(!activity.isFinishing()&&address.equals(view.getTag())){
                        view.setImageBitmap(bmp);
                        view.animate().cancel();
                        view.animate().alpha(intendedAlpha).setDuration(275).start();
                    }
                });
            }catch(Exception ignored){}finally{if(con!=null)con.disconnect();}
        });
    }
    public void shutdown(){workers.shutdownNow();}
}
