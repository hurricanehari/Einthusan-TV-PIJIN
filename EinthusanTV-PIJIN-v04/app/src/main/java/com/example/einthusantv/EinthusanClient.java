package com.example.einthusantv;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent, private Android implementation informed by EinTamil's MIT-licensed Kodi add-on.
 * Credentials are only entered on the first-party website in BrowserActivity.
 */
public final class EinthusanClient {
    public static final String BASE = "https://einthusan.tv";
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int LIMIT = 3 * 1024 * 1024;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    public EinthusanClient() { CookieHandler.setDefault(cookies); }

    public static final class Page {
        public final List<Film> films;
        public final String next;
        public final int page;
        Page(List<Film> films, String next, int page) {this.films=films;this.next=next;this.page=page;}
    }
    public static final class Stream {
        public final String url;
        public final Map<String,String> headers;
        Stream(String url, Map<String,String> headers) {this.url=url;this.headers=headers;}
    }
    public String listingUrl(String lang, String category, String query) {
        String q="lang="+Uri.encode(lang);
        if (query!=null && !query.trim().isEmpty()) return BASE+"/movie/results/?"+q+"&query="+Uri.encode(query.trim());
        if (category.equals("Featured")) return BASE+"/movie/browse/?"+q;
        if (category.equals("Popular")) return BASE+"/movie/results/?find=Popularity&ptype=View&tp=l30d&"+q;
        if (category.equals("Staff Picks")) return BASE+"/movie/results/?find=StaffPick&"+q;
        if (category.equals("Numbers")) return BASE+"/movie/results/?find=Numbers&"+q;
        if (category.startsWith("A-Z:")) return BASE+"/movie/results/?find=Alphabets&alpha="+Uri.encode(category.substring(4))+"&"+q;
        if (category.startsWith("Year:")) return BASE+"/movie/results/?find=Year&year="+Uri.encode(category.substring(5))+"&"+q;
        if (category.startsWith("Decade:")) return BASE+"/movie/results/?find=Decade&decade="+Uri.encode(category.substring(7))+"&"+q;
        if (category.startsWith("Rating:")) {
            String r=category.substring(7).toLowerCase(Locale.ROOT);
            String[] attrs={"action","comedy","romance","storyline","performance"};
            StringBuilder b=new StringBuilder(BASE+"/movie/results/?find=Rating&"+q);
            for(String attr:attrs) b.append("&").append(attr).append("=").append(attr.equals(r)?"4":"0");
            b.append("&ratecount=1");return b.toString();
        }
        return BASE+"/movie/results/?find=Recent&"+q;
    }
    private static String absolute(String ref) {
        if(ref==null||ref.trim().isEmpty())return "";
        try {
            URI uri=URI.create(BASE+"/").resolve(ref.trim());
            if(!"https".equalsIgnoreCase(uri.getScheme()))return "";
            String h=uri.getHost();
            return h!=null&&(h.equals("einthusan.tv")||h.endsWith(".einthusan.tv"))?uri.toString():"";
        }catch(Exception e){return "";}
    }
    private static String image(String ref){
        if(TextUtils.isEmpty(ref))return "";
        if(ref.startsWith("//"))return "https:"+ref;
        if(ref.startsWith("https://"))return ref;
        return absolute(ref);
    }
    private static String imageOf(Element e) {
        if(e==null)return "";
        Element img=e.is("img")?e:e.selectFirst("img");
        if(img==null)return "";
        for(String key:new String[]{"data-src","data-original","data-lazy-src","src","srcset"}){
            String v=img.attr(key);if(key.equals("srcset")&&v.contains(" "))v=v.split("[ ,]")[0];
            if(!v.isEmpty()&&!v.startsWith("data:"))return image(v);
        }
        return "";
    }
    private static String officialCookie(){
        try {String c=android.webkit.CookieManager.getInstance().getCookie(BASE);return c==null?"":c;}
        catch(Exception e){return "";}
    }
    private String request(HttpURLConnection c,String form) throws Exception {
        c.setConnectTimeout(12000);c.setReadTimeout(18000);
        c.setRequestProperty("User-Agent",UA);
        c.setRequestProperty("Accept-Language","en-GB,en;q=0.9");
        c.setInstanceFollowRedirects(false);
        String loginCookie=officialCookie();
        if(!loginCookie.isEmpty())c.setRequestProperty("Cookie",loginCookie);
        if(form!=null){
            c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
            try(OutputStream out=c.getOutputStream()){out.write(form.getBytes(StandardCharsets.UTF_8));}
        }
        int status=c.getResponseCode();
        if(status!=200)throw new Exception("HTTP "+status+(status>=300&&status<400?" (redirect or login required)":""));
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>LIMIT)throw new Exception("Catalogue page too large");}
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    private String get(String url,String referer)throws Exception{
        String safe=absolute(url);if(safe.isEmpty())throw new Exception("Unexpected movie URL");
        HttpURLConnection c=(HttpURLConnection)new URL(safe).openConnection();
        if(referer!=null)c.setRequestProperty("Referer",referer);
        return request(c,null);
    }
    private static String readText(Element e){return e==null?"":e.text().trim();}
    private static String extractTitle(Element a,Element card,String fullHtml,String id){
        for(Element base:new Element[]{card,a,a.parent()}){
            if(base==null)continue;
            Element h=base.selectFirst("h3,h2,h4,.movie-title,.title,.film-title");
            if(!readText(h).isEmpty())return readText(h);
        }
        String label=a.attr("title");if(!label.trim().isEmpty())return label.trim();
        if(!a.text().trim().isEmpty())return a.text().trim();
        Element img=a.selectFirst("img[alt]");if(img==null)img=card.selectFirst("img[alt]");
        if(img!=null&&!img.attr("alt").trim().isEmpty())return img.attr("alt").trim();
        // Kodi's parser reads an <h3> AFTER the watch link; it can be a sibling
        // rather than a child, so locate it in a bounded section after this ID.
        Matcher m=Pattern.compile("(?is)/movie/watch/"+Pattern.quote(id)+"/[^\"']*[\"']").matcher(fullHtml);
        if(m.find()){
            String segment=fullHtml.substring(m.end(),Math.min(fullHtml.length(),m.end()+2000));
            int next=segment.indexOf("/movie/watch/");if(next>=0)segment=segment.substring(0,next);
            Matcher h=Pattern.compile("(?is)<h[234][^>]*>(.*?)</h[234]>").matcher(segment);
            if(h.find()){
                String title=Jsoup.parseBodyFragment(h.group(1)).text().trim();
                if(!title.isEmpty())return title;
            }
        }
        return "Movie "+id;
    }
    private static Element cardFor(Element a){
        Element parent=a;
        for(int level=0;level<6&&parent!=null;level++,parent=parent.parent()){
            if(parent.hasClass("block1")||parent.hasAttr("name")&&parent.attr("name").equals("newrelease_tab"))return parent;
        }
        for(parent=a.parent();parent!=null;parent=parent.parent()){
            if(parent.select("a[href*='/movie/watch/']").size()==1&&parent.select("h2,h3,h4").size()>0)return parent;
            if(parent.tagName().equals("body"))break;
        }
        return a.parent()==null?a:a.parent();
    }
    private static String addPage(String original,int index){
        Uri uri=Uri.parse(original);
        Uri.Builder b=uri.buildUpon().clearQuery();
        for(String name:uri.getQueryParameterNames()){
            if(!"page".equalsIgnoreCase(name)){
                String value=uri.getQueryParameter(name);
                if(value!=null)b.appendQueryParameter(name,value);
            }
        }
        return b.appendQueryParameter("page",String.valueOf(index)).build().toString();
    }
    public Page browse(String url)throws Exception{
        String safe=absolute(url);if(safe.isEmpty()||!safe.contains("/movie/"))throw new Exception("Invalid catalogue URL");
        String html=get(safe,BASE);Document doc=Jsoup.parse(html,BASE);
        ArrayList<Film> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        int links=0;String langFallback=Uri.parse(safe).getQueryParameter("lang");
        for(Element a:doc.select("a[href]")){
            String href=a.attr("href");if(!href.contains("movie/watch/"))continue;
            links++;String watch=absolute(href);if(watch.isEmpty())continue;
            Uri uri=Uri.parse(watch);List<String> segments=uri.getPathSegments();
            if(segments.size()<3||!segments.get(0).equals("movie")||!segments.get(1).equals("watch"))continue;
            String id=segments.get(2),lang=uri.getQueryParameter("lang");
            if(TextUtils.isEmpty(lang))lang=langFallback;
            if(TextUtils.isEmpty(lang)||!lang.matches("[A-Za-z]+")||!id.matches("[A-Za-z0-9_-]+"))continue;
            if(!ids.add(lang+":"+id))continue;
            Element card=cardFor(a);
            String title=extractTitle(a,card,html,id);
            Element plot=card.selectFirst(".synopsis,.summary,.description,p.synopsis");
            String synopsis=readText(plot);
            String poster=imageOf(card);if(poster.isEmpty())poster=imageOf(a);
            String quality=card.outerHtml().toLowerCase(Locale.ROOT).contains("ultrahd")?"uhd":"shd";
            result.add(new Film(id,lang,title,poster,synopsis,watch,quality));
        }
        if(result.isEmpty()){
            String text=doc.body()==null?"":doc.body().text().toLowerCase(Locale.ROOT);
            if(text.contains("captcha")||text.contains("access denied")||text.contains("verify you are human"))
                throw new Exception("Einthusan returned a verification page; try the website fallback.");
            throw new Exception("No films found (watch links: "+links+"). Catalogue layout or session may have changed.");
        }
        int page=1;try{page=Integer.parseInt(Uri.parse(safe).getQueryParameter("page"));}catch(Exception ignored){}
        String next="";
        for(Element a:doc.select("a[href]")){
            String href=absolute(a.attr("href"));if(!href.contains("/movie/")||href.equals(safe))continue;
            String rel=a.attr("rel").toLowerCase(Locale.ROOT),label=a.text().toLowerCase(Locale.ROOT);
            boolean isNext=rel.contains("next")||label.contains("next")||a.attr("aria-label").toLowerCase(Locale.ROOT).contains("next");
            if(!isNext||"true".equalsIgnoreCase(a.attr("data-disabled")))continue;
            next=href;break;
        }
        if(next.isEmpty()){
            // Some versions of the pager use only a glyph; link to page N+1
            // if the page displays a greater total page count.
            Matcher count=Pattern.compile("(?i)page\\s+\\d+\\s+of\\s+(\\d+)").matcher(doc.text());
            if(count.find()){
                int total=Integer.parseInt(count.group(1));
                if(page<total)next=addPage(safe,page+1);
            }
        }
        return new Page(result,next,page);
    }
    private static String encode(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
    private static String attr(String html,String name){Element e=Jsoup.parse(html).selectFirst("["+name+"]");return e==null?"":e.attr(name);}
    private String cookieHeader(String url){
        String web=officialCookie();if(!web.isEmpty())return web;
        try{List<String> v=cookies.get(URI.create(url),new HashMap<>()).get("Cookie");return v==null?"":TextUtils.join("; ",v);}catch(Exception e){return "";}
    }
    /** The site's own membership checks decide whether UHD will play. */
    public Stream resolve(Film film,String fromListing,boolean uhd)throws Exception{
        if(film==null)throw new Exception("No movie selected");
        String path="/movie/watch/"+film.id+"/?lang="+Uri.encode(film.language);
        String ref=absolute(fromListing);if(ref.isEmpty())ref=listingUrl(film.language,"Recently Added",null);
        HttpURLConnection check=(HttpURLConnection)new URL(BASE+path).openConnection();
        check.setRequestProperty("Origin",BASE);check.setRequestProperty("Referer",ref);check.setRequestProperty("User-Agent",UA);
        String c=officialCookie();if(!c.isEmpty())check.setRequestProperty("Cookie",c);
        check.setInstanceFollowRedirects(false);int status=check.getResponseCode();check.disconnect();
        if(status==301||status==302||status==307||status==308)path="/premium"+path;
        if(uhd)path += "&uhd=true";
        String play=BASE+path;
        HttpURLConnection watch=(HttpURLConnection)new URL(play).openConnection();
        watch.setRequestProperty("Origin",BASE);watch.setRequestProperty("Referer",ref);
        String html=request(watch,null);
        if(html.contains("SERVERS ARE ALMOST AT CAPACITY"))throw new Exception("The service is busy; try again later.");
        String ej=attr(html,"data-ejpingables"),csrf=attr(html,"data-pageid");
        if(ej.isEmpty()||csrf.isEmpty())throw new Exception(uhd?"4K unavailable; sign in with an eligible Premium account, or choose HD.":"Playback token not available. Try the website fallback.");
        JSONObject json=new JSONObject();json.put("EJOutcomes",ej);json.put("NativeHLS",false);
        String form="xEvent="+encode("UIVideoPlayer.PingOutcome")+"&xJson="+encode(json.toString())+
            "&arcVersion=3&appVersion=59&gorilla.csrf.Token="+encode(csrf);
        HttpURLConnection ajax=(HttpURLConnection)new URL(BASE+"/ajax"+path).openConnection();
        ajax.setRequestProperty("Origin",BASE);ajax.setRequestProperty("Referer",play);
        String response=request(ajax,form);
        String packed=new JSONObject(response).getJSONObject("Data").getString("EJLinks");
        if(packed.length()<14)throw new Exception("Invalid server playback response");
        String rearranged=packed.substring(0,10)+packed.charAt(packed.length()-1)+packed.substring(12,packed.length()-1);
        String decoded=new String(Base64.decode(rearranged,Base64.DEFAULT),StandardCharsets.UTF_8);
        String hls=new JSONObject(decoded).getString("HLSLink");
        if(!hls.startsWith("https://"))throw new Exception("Unsupported stream URL");
        Map<String,String> headers=new HashMap<>();
        headers.put("User-Agent",UA);headers.put("Referer",play);headers.put("Origin",BASE);
        String sessionCookie=cookieHeader(play);if(!sessionCookie.isEmpty())headers.put("Cookie",sessionCookie);
        return new Stream(hls,headers);
    }
    public Stream resolve(Film f,String from)throws Exception{return resolve(f,from,false);}
}
