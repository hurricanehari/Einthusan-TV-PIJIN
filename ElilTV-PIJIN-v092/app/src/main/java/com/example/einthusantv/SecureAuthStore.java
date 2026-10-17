package com.example.einthusantv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.security.KeyStore;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted device-local first-party login/session storage.
 * Android Keystore holds the AES key; no plaintext passwords or session cookies
 * are put in SharedPreferences, GitHub, logs or backups. minSdk = 23.
 */
final class SecureAuthStore {
    private static final String PREFS="elil_secure_auth_v1";
    private static final String ALIAS="elil_einthusan_auth_aes_v1";
    private static final String CREDS="login";
    private static final String COOKIES="cookies";
    private static final String HOST="einthusan.tv";
    private final SharedPreferences prefs;
    SecureAuthStore(Context context){
        prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    private static String seal(String plaintext) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(cipher.doFinal(plaintext.getBytes("UTF-8")),Base64.NO_WRAP);
    }
    private static String open(String encrypted) throws Exception {
        String[] components=encrypted.split(":",2);
        if(components.length!=2)throw new IllegalArgumentException("Bad encrypted record");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,
            Base64.decode(components[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(components[1],Base64.NO_WRAP)),"UTF-8");
    }
    synchronized boolean saveCredentials(String email,String password){
        if(email==null||email.trim().isEmpty()||password==null||password.isEmpty())return false;
        try{
            JSONObject value=new JSONObject();value.put("email",email.trim());value.put("password",password);
            return prefs.edit().putString(CREDS,seal(value.toString())).commit();
        }catch(Exception ignored){return false;}
    }
    synchronized String[] loadCredentials(){
        String encrypted=prefs.getString(CREDS,"");if(encrypted.isEmpty())return null;
        try{
            JSONObject obj=new JSONObject(open(encrypted));
            String email=obj.optString("email"),password=obj.optString("password");
            return email.isEmpty()||password.isEmpty()?null:new String[]{email,password};
        }catch(Exception ignored){return null;}
    }
    synchronized boolean hasCredentials(){return loadCredentials()!=null;}
    synchronized void clearCredentials(){prefs.edit().remove(CREDS).commit();}
    private static boolean eligible(String domain){
        if(domain==null)return false;
        String d=domain.startsWith(".")?domain.substring(1):domain;
        return d.equalsIgnoreCase(HOST)||d.toLowerCase(java.util.Locale.ROOT).endsWith("."+HOST);
    }
    synchronized void saveCookies(CookieManager manager){
        try{
            JSONArray list=new JSONArray();
            long now=System.currentTimeMillis();
            for(HttpCookie cookie:manager.getCookieStore().getCookies()){
                // Host-only cookies often have a null Domain attribute. The native
                // client only connects to the first-party host, so preserve them.
                String domain=cookie.getDomain()==null?HOST:cookie.getDomain();
                if(cookie.hasExpired()||!eligible(domain)||cookie.getName().isEmpty())continue;
                JSONObject row=new JSONObject();
                row.put("name",cookie.getName());row.put("value",cookie.getValue());
                row.put("domain",domain);row.put("path",cookie.getPath());
                row.put("secure",cookie.getSecure());row.put("httpOnly",cookie.isHttpOnly());
                // Session cookies (-1) survive app restarts, but only the site
                // decides whether they remain authenticated.
                long age=cookie.getMaxAge();
                row.put("expiry",age<0?-1:now+Math.min(age,31536000L)*1000L);
                list.put(row);
            }
            prefs.edit().putString(COOKIES,seal(list.toString())).commit();
        }catch(Exception ignored){}
    }
    synchronized void restoreCookies(CookieManager manager){
        String encrypted=prefs.getString(COOKIES,"");if(encrypted.isEmpty())return;
        try{
            JSONArray list=new JSONArray(open(encrypted));
            long now=System.currentTimeMillis();
            for(int i=0;i<list.length();i++){
                JSONObject row=list.optJSONObject(i);if(row==null)continue;
                String domain=row.optString("domain");if(!eligible(domain))continue;
                long expiry=row.optLong("expiry",-1);
                if(expiry>=0 && expiry<=now)continue;
                String name=row.optString("name"),value=row.optString("value");
                if(name.isEmpty())continue;
                HttpCookie cookie=new HttpCookie(name,value);
                cookie.setDomain(domain);cookie.setPath(row.optString("path","/"));
                cookie.setSecure(row.optBoolean("secure",true));
                cookie.setHttpOnly(row.optBoolean("httpOnly",false));
                if(expiry>=0)cookie.setMaxAge(Math.max(1,(expiry-now)/1000L));
                manager.getCookieStore().add(URI.create("https://"+HOST+"/"),cookie);
            }
        }catch(Exception ignored){}
    }
    synchronized void clearCookies(){prefs.edit().remove(COOKIES).commit();}
}
