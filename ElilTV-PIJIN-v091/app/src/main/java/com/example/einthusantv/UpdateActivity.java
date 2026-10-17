package com.example.einthusantv;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-initiated Fire TV updates from official GitHub Releases.
 * Never silently installs packages. Only a newer, same-package APK signed by
 * the installed signing certificate is eligible for installation.
 */
public final class UpdateActivity extends Activity {
    private static final String API =
        "https://api.github.com/repos/hurricanehari/Einthusan-TV-PIJIN/releases/latest";
    private static final String ASSET = "Elil-TV.apk";
    private static final long MAX_DOWNLOAD = 200L * 1024L * 1024L;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status, check, install;
    private volatile boolean busy;
    private Release latest;
    private File verifiedApk;

    private static final class Release {
        final String tag, url;
        final long size;
        Release(String tag, String url, long size) {
            this.tag=tag;this.url=url;this.size=size;
        }
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));
        d.setStroke(dp(1),stroke);return d;
    }
    private TextView label(String s,int sp,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private TextView button(String s,Runnable click) {
        TextView t=label(s,17,Color.WHITE,true);
        t.setPadding(dp(22),dp(14),dp(22),dp(14));t.setGravity(Gravity.CENTER);
        t.setBackground(shape(0xff202c3c,0xff394e65));t.setFocusable(true);
        t.setOnFocusChangeListener((v,focused)->{
            t.setBackground(shape(focused?0xffe9b76b:0xff202c3c,focused?0xffffffff:0xff394e65));
            t.setTextColor(focused?0xff080c16:Color.WHITE);
        });
        t.setOnClickListener(v->click.run());return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(54),dp(40),dp(54),dp(40));
        content.setBackgroundColor(0xff080c16);setContentView(content);
        TextView heading=label("App Updates",30,Color.WHITE,true);
        content.addView(heading,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView desc=label("ELIL TV  •  BROUGHT BY PIJIN",13,0xffe9b76b,true);
        content.addView(desc,new LinearLayout.LayoutParams(-1,dp(38)));
        status=label("Installed version: "+BuildConfig.VERSION_NAME+"\nChecking GitHub Releases…",
            17,0xffcbd5e1,false);
        status.setGravity(Gravity.TOP);status.setPadding(0,dp(16),0,dp(14));
        content.addView(status,new LinearLayout.LayoutParams(-1,dp(125)));
        check=button("Check for updates",this::checkForUpdates);
        content.addView(check,new LinearLayout.LayoutParams(dp(290),dp(59)));
        install=button("Download & install update",this::downloadOrInstall);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(350),dp(59));
        mp.topMargin=dp(16);content.addView(install,mp);
        install.setVisibility(View.GONE);
        TextView note=label("Updates require a Fire TV installation confirmation. " +
            "Only official, signed Elil TV APKs are accepted. " +
            "Your profiles and watch history remain stored on this device when updating normally.",
            14,0xffaab3c4,false);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(110));np.topMargin=dp(24);
        content.addView(note,np);
        check.requestFocus();checkForUpdates();
    }
    private void message(String value){runOnUiThread(()->{if(!isFinishing())status.setText(value);});}
    private static int[] version(String tag){
        String v=tag==null?"":tag.trim().replaceFirst("^[vV]","");
        if(!v.matches("\\d+\\.\\d+(\\.\\d+)?"))return null;
        String[] parts=v.split("\\.");
        return new int[]{Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),
            parts.length>2?Integer.parseInt(parts[2]):0};
    }
    private static boolean newer(String tag,String current){
        int[] a=version(tag),b=version(current);if(a==null||b==null)return false;
        for(int i=0;i<3;i++)if(a[i]!=b[i])return a[i]>b[i];
        return false;
    }
    private static String readLimited(InputStream in,int limit)throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>limit)throw new Exception("Response too large");}
        return out.toString("UTF-8");
    }
    private static HttpURLConnection open(String address)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent","Elil-TV-UpdateChecker/0.8");
        c.setRequestProperty("Accept","application/vnd.github+json");
        c.setInstanceFollowRedirects(true);return c;
    }
    private void checkForUpdates(){
        if(busy)return;
        busy=true;latest=null;verifiedApk=null;install.setVisibility(View.GONE);
        message("Installed version: "+BuildConfig.VERSION_NAME+"\nChecking for updates…");
        io.submit(()->{
            HttpURLConnection c=null;
            try{
                c=open(API);
                if(c.getResponseCode()!=200)throw new Exception("GitHub update check: HTTP "+c.getResponseCode());
                JSONObject data=new JSONObject(readLimited(c.getInputStream(),256*1024));
                String tag=data.optString("tag_name");
                if(!newer(tag,BuildConfig.VERSION_NAME)){
                    message("Elil TV "+BuildConfig.VERSION_NAME+" is up to date.");return;
                }
                JSONArray files=data.optJSONArray("assets");Release result=null;
                if(files!=null)for(int i=0;i<files.length();i++){
                    JSONObject a=files.optJSONObject(i);if(a==null||!ASSET.equals(a.optString("name")))continue;
                    String url=a.optString("browser_download_url");long size=a.optLong("size",0L);
                    if(url.startsWith("https://github.com/hurricanehari/Einthusan-TV-PIJIN/releases/download/")
                       && size>0&&size<=MAX_DOWNLOAD){result=new Release(tag,url,size);break;}
                }
                if(result==null){message("Release "+tag+" exists but no signed "+ASSET+
                    " is attached. Try again after publication.");return;}
                latest=result;
                message("Elil TV "+tag+" is available.\nSelect Download & install update to continue.");
                runOnUiThread(()->{if(!isFinishing())install.setVisibility(View.VISIBLE);});
            }catch(Exception e){message("Could not check for updates: "+e.getMessage());}
            finally{if(c!=null)c.disconnect();busy=false;}
        });
    }
    private void downloadOrInstall(){
        if(busy||latest==null)return;
        if(verifiedApk!=null&&verifiedApk.isFile()){showInstaller();return;}
        busy=true;message("Downloading official Elil TV update…");
        io.submit(()->{
            HttpURLConnection c=null;File temp=null;
            try{
                c=open(latest.url);
                if(c.getResponseCode()!=200)throw new Exception("Download failed: HTTP "+c.getResponseCode());
                long announced=c.getContentLengthLong();
                if(announced>MAX_DOWNLOAD)throw new Exception("APK is too large");
                File dir=new File(getCacheDir(),"updates");
                if(!dir.exists()&&!dir.mkdirs())throw new Exception("Could not prepare update storage");
                temp=new File(dir,"incoming.apk");
                byte[] buf=new byte[65536];int n;long count=0,lastShown=-1;
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
                    while((n=in.read(buf))!=-1){
                        count+=n;if(count>MAX_DOWNLOAD)throw new Exception("APK exceeds size limit");
                        out.write(buf,0,n);
                        long pct=latest.size>0?100*count/latest.size:0;
                        if(pct/10!=lastShown/10){lastShown=pct;message("Downloading update… "+Math.min(100,pct)+"%");}
                    }
                }
                if(count!=latest.size)throw new Exception("Downloaded APK size does not match the release");
                validatePackage(temp);
                File destination=new File(dir,"Elil-TV.apk");
                if(destination.exists()&&!destination.delete())throw new Exception("Could not replace cached update");
                if(!temp.renameTo(destination))throw new Exception("Could not finalise update");
                verifiedApk=destination;
                message("Update verified.\nSelect Install to continue on Fire TV.");
                runOnUiThread(()->{if(!isFinishing())install.setText("Install verified update");});
            }catch(Exception e){if(temp!=null)temp.delete();message("Update not installed: "+e.getMessage());}
            finally{if(c!=null)c.disconnect();busy=false;}
        });
    }
    @SuppressWarnings("deprecation")
    private Signature[] signerSet(PackageInfo info){
        if(info==null)return null;
        if(Build.VERSION.SDK_INT>=28&&info.signingInfo!=null){
            Signature[] s=info.signingInfo.getApkContentsSigners();
            if(s!=null&&s.length>0)return s;
        }
        return info.signatures;
    }
    @SuppressWarnings("deprecation")
    private void validatePackage(File apk)throws Exception {
        PackageManager pm=getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:
            PackageManager.GET_SIGNATURES;
        PackageInfo proposed=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        PackageInfo installed=pm.getPackageInfo(getPackageName(),flags);
        if(proposed==null||!getPackageName().equals(proposed.packageName))
            throw new Exception("APK is not the correct Elil TV package");
        if(proposed.versionCode<=installed.versionCode)
            throw new Exception("Update build number must be newer than the installed app");
        Signature[] a=signerSet(proposed),b=signerSet(installed);
        if(a==null||b==null||a.length!=b.length||a.length==0)
            throw new Exception("APK signature could not be verified");
        for(int i=0;i<a.length;i++)if(!Arrays.equals(a[i].toByteArray(),b[i].toByteArray()))
            throw new Exception("APK signature differs from installed Elil TV");
    }
    private void showInstaller(){
        if(verifiedApk==null||!verifiedApk.isFile())return;
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            message("Allow Elil TV to install updates in Fire TV Settings, then select Install again.");
            Intent permission=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:"+getPackageName()));
            try{startActivity(permission);}catch(Exception e){message("Enable installation from Elil TV in Fire TV Settings.");}
            return;
        }
        try{
            validatePackage(verifiedApk);
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",verifiedApk);
            Intent installIntent=new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(uri,"application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.setClipData(ClipData.newUri(getContentResolver(),"Elil TV Update",uri));
            startActivity(installIntent);
        }catch(Exception e){message("Could not start installer: "+e.getMessage());}
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
