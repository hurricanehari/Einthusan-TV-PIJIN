package com.example.einthusantv;

import android.net.Uri;
import android.text.TextUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import android.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Translates the publicly readable Einthusan Kodi add-on catalogue and HLS flow to Java.
 * Experimental independent integration. No passwords stored and no premium restriction bypass.
 */
public final class EinthusanClient {
    public static final String BASE = "https://einthusan.tv";
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int LIMIT = 2 * 1024 * 1024;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    public EinthusanClient() { CookieHandler.setDefault(cookies); }

    public static final class Page {
        public final List<Film> films;
        public final String next;
        Page(List<Film> films, String next) { this.films=films; this.next=next; }
    }
    public static final class Stream {
        public final String url;
        public final Map<String,String> headers;
        Stream(String url, Map<String,String> headers) { this.url=url; this.headers=headers; }
    }

    public String listingUrl(String lang, String category, String search) {
        String q = "lang=" + Uri.encode(lang);
        if (search != null && !search.trim().isEmpty()) return BASE + "/movie/results/?" + q + "&query=" + Uri.encode(search.trim());
        switch(category) {
            case "Featured": return BASE + "/movie/browse/?" + q;
            case "Most Watched": return BASE + "/movie/results/?find=Popularity&ptype=View&tp=l30d&" + q;
            case "Staff Picks": return BASE + "/movie/results/?find=StaffPick&" + q;
            case "A-Z": return BASE + "/movie/results/?find=Alphabets&alpha=A&" + q;
            default: return BASE + "/movie/results/?find=Recent&" + q;
        }
    }

    private static String absolute(String ref) {
        if (ref == null || ref.trim().isEmpty()) return "";
        try {
            URI u = URI.create(BASE).resolve(ref.trim());
            if (!"https".equalsIgnoreCase(u.getScheme())) return "";
            String h = u.getHost();
            return h != null && (h.equals("einthusan.tv") || h.endsWith(".einthusan.io") || h.endsWith(".einthusan.tv")) ? u.toString() : "";
        } catch(Exception e) { return ""; }
    }
    private static String image(String ref) {
        if (TextUtils.isEmpty(ref)) return "";
        if (ref.startsWith("//")) return "https:"+ref;
        if (ref.startsWith("https://")) return ref;
        return absolute(ref);
    }
    private static String imageOf(Element element) {
        Element img = element.selectFirst("img");
        if (img == null) return "";
        for (String key : new String[]{"data-src","data-original","src","data-lazy-src"}) {
            String value = img.attr(key);
            if (!value.isEmpty() && !value.startsWith("data:")) return image(value);
        }
        return "";
    }
    private static String request(HttpURLConnection c, String form) throws Exception {
        c.setConnectTimeout(12000); c.setReadTimeout(18000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9");
        c.setInstanceFollowRedirects(false);
        if (form != null) {
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream o = c.getOutputStream()) { o.write(form.getBytes(StandardCharsets.UTF_8)); }
        }
        int status = c.getResponseCode();
        if (status != 200) throw new Exception("Einthusan returned HTTP " + status + (status >= 300 && status < 400 ? " (redirect / login may be required)" : ""));
        try (InputStream in=c.getInputStream(); ByteArrayOutputStream b=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while((n=in.read(buffer))!=-1) { b.write(buffer,0,n); if(b.size()>LIMIT) throw new Exception("Response too large"); }
            return b.toString("UTF-8");
        } finally { c.disconnect(); }
    }
    private String get(String address, String referer) throws Exception {
        String safe=absolute(address); if(safe.isEmpty()) throw new Exception("Unexpected catalogue host");
        HttpURLConnection c=(HttpURLConnection)new URL(safe).openConnection();
        if(referer!=null) c.setRequestProperty("Referer",referer);
        return request(c,null);
    }
    public Page browse(String url) throws Exception {
        String safe = absolute(url);
        if (safe.isEmpty() || !safe.contains("/movie/")) throw new Exception("Invalid browse URL");
        Document doc = Jsoup.parse(get(safe,BASE), BASE);
        ArrayList<Film> films = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean featured = safe.contains("/movie/browse/");
        Elements cards = featured ? doc.select("[name=newrelease_tab]") : doc.select(".block1");
        if (cards.isEmpty()) cards = doc.select("a[href*='/movie/watch/']");
        for(Element card : cards) {
            Element a=card.is("a[href*='/movie/watch/']") ? card : card.selectFirst("a[href*='/movie/watch/']");
            if(a==null) continue;
            String watch=absolute(a.attr("abs:href").isEmpty()?a.attr("href"):a.attr("abs:href"));
            if(watch.isEmpty() || !watch.contains("/movie/watch/")) continue;
            Uri u=Uri.parse(watch);
            String lang=u.getQueryParameter("lang");
            String path=u.getPath();
            if(path==null) continue;
            String[] parts=path.split("/");
            if(parts.length<4 || TextUtils.isEmpty(lang) || !lang.matches("[a-zA-Z]+")) continue;
            String id=parts[3];
            if(!id.matches("[a-zA-Z0-9_-]+") || !seen.add(lang+":"+id)) continue;
            Element h = card.selectFirst("h3,h2");
            if (h==null) h = a.selectFirst("h3,h2");
            String title=(h==null?a.text():h.text()).trim();
            if(title.isEmpty()) title=a.attr("title");
            if(title.isEmpty()) continue;
            Element p=card.selectFirst(".synopsis");
            String desc=p==null?"":p.text();
            String poster=imageOf(card);
            if(poster.isEmpty()) poster=imageOf(a);
            String quality=card.html().contains("ultrahd")?"uhd":"shd";
            films.add(new Film(id,lang,title,poster,desc,watch,quality));
        }
        String next="";
        for(Element a: doc.select("a[href]")) {
            String text=a.text().trim().toLowerCase(Locale.ROOT);
            if((text.equals("next")||text.endsWith("next")||a.attr("rel").equals("next")) && !a.attr("data-disabled").equals("true")) {
                String candidate=absolute(a.attr("href"));
                if(!candidate.isEmpty()&&candidate.contains("/movie/")) next=candidate;
            }
        }
        if(films.isEmpty()) throw new Exception("No films recognised. Einthusan's page layout or access may have changed.");
        return new Page(films,next);
    }

    private static String encode(String v) throws Exception { return URLEncoder.encode(v,"UTF-8"); }
    private static String attr(String html, String name) {
        Element element = Jsoup.parse(html).selectFirst("["+name+"]");
        return element==null?"":element.attr(name);
    }
    private String cookieHeader(String url) {
        try {
            Map<String,List<String>> values=cookies.get(URI.create(url),new HashMap<>());
            List<String> result=values.get("Cookie");
            return result==null?"":TextUtils.join("; ",result);
        } catch(Exception e) {return "";}
    }
    /** Port of omega/main.py get_video: page's EJOutcomes -> AJAX -> HLSLink. */
    public Stream resolve(Film film, String fromListing) throws Exception {
        if(film==null) throw new Exception("No film selected");
        String path="/movie/watch/"+film.id+"/?lang="+Uri.encode(film.language);
        String referer=absolute(fromListing); if(referer.isEmpty()) referer=listingUrl(film.language,"Recently Added",null);
        String baseWatch=BASE+path;
        HttpURLConnection check=(HttpURLConnection)new URL(baseWatch).openConnection();
        check.setRequestProperty("Origin",BASE);check.setRequestProperty("Referer",referer);
        check.setRequestProperty("User-Agent",UA);check.setInstanceFollowRedirects(false);
        int status=check.getResponseCode();check.disconnect();
        if(status==301||status==302||status==307||status==308) path="/premium"+path;
        String playPage=BASE+path;
        HttpURLConnection page=(HttpURLConnection)new URL(playPage).openConnection();
        page.setRequestProperty("Origin",BASE);page.setRequestProperty("Referer",referer);
        String html=request(page,null);
        if(html.contains("Go Premium")) throw new Exception("This film requires Premium access. Sign in via the website fallback.");
        if(html.contains("SERVERS ARE ALMOST AT CAPACITY")) throw new Exception("Einthusan servers are busy. Try later.");
        String ejp=attr(html,"data-ejpingables");
        String csrf=attr(html,"data-pageid");
        if(ejp.isEmpty()||csrf.isEmpty()) throw new Exception("Playback token unavailable; website structure may have changed.");
        JSONObject data=new JSONObject();data.put("EJOutcomes",ejp);data.put("NativeHLS",false);
        String post="xEvent="+encode("UIVideoPlayer.PingOutcome")+
            "&xJson="+encode(data.toString())+
            "&arcVersion=3&appVersion=59&gorilla.csrf.Token="+encode(csrf);
        HttpURLConnection ajax=(HttpURLConnection)new URL(BASE+"/ajax"+path).openConnection();
        ajax.setRequestProperty("Origin",BASE);ajax.setRequestProperty("Referer",referer);
        ajax.setRequestProperty("User-Agent",UA);
        String json=request(ajax,post);
        JSONObject object=new JSONObject(json);
        String packed=object.getJSONObject("Data").getString("EJLinks");
        if(packed.length()<14) throw new Exception("Invalid playback response");
        // The Kodi add-on rearranges an obfuscated string before decoding Base64.
        String rearranged=packed.substring(0,10)+packed.charAt(packed.length()-1)+packed.substring(12,packed.length()-1);
        String decoded=new String(Base64.decode(rearranged,Base64.DEFAULT),StandardCharsets.UTF_8);
        String hls=new JSONObject(decoded).getString("HLSLink");
        if(!hls.startsWith("https://")) throw new Exception("Unsupported stream URL");
        Map<String,String> headers=new HashMap<>();
        headers.put("User-Agent",UA);headers.put("Referer",playPage);headers.put("Origin",BASE);
        String cookie=cookieHeader(playPage); if(!cookie.isEmpty()) headers.put("Cookie",cookie);
        return new Stream(hls,headers);
    }
}
