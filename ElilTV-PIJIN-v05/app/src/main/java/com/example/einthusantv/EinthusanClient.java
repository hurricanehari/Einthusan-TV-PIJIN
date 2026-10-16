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
import java.net.HttpCookie;
import java.util.Collections;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent, private Android implementation informed by EinTamil's MIT-licensed Kodi add-on.
 * Account credentials are submitted only to the first-party HTTPS login endpoint.
 * No passwords, tokens or session cookies are logged or stored in app preferences.
 */
public final class EinthusanClient {
    public static final String BASE = "https://einthusan.tv";
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int LIMIT = 3 * 1024 * 1024;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private volatile boolean accountVerified = false;
    private final Map<String,String> titleCache = new HashMap<>();
    public EinthusanClient() { CookieHandler.setDefault(cookies); }
    public boolean isSignedIn() {return accountVerified;}
    public static final class LoginResult {
        public final boolean verified;
        public final String message;
        LoginResult(boolean verified, String message) { this.verified=verified;this.message=message; }
    }

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
        String sessionCookie=cookieHeader(c.getURL().toString());
        if(!sessionCookie.isEmpty())c.setRequestProperty("Cookie",sessionCookie);
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
    private static boolean usableTitle(String value, String id){
        if(value==null)return false;
        String t=value.trim();
        return t.length()>1 && !t.equalsIgnoreCase(id) &&
            !t.equalsIgnoreCase("Movie "+id) && !t.equalsIgnoreCase("Watch") &&
            !t.equalsIgnoreCase("Play") && !t.equalsIgnoreCase("Einthusan") &&
            !t.equalsIgnoreCase("Tamil") && !t.equalsIgnoreCase("Movie");
    }
    private static String tidyTitle(String name){
        if(name==null)return "";
        String t=Jsoup.parseBodyFragment(name).text().replaceAll("\\s+"," ").trim();
        t=t.replaceAll("(?i)\\s*[-|]\\s*Einthusan(?: TV)?\\s*$","");
        return t.trim();
    }
    /** Find the nearest titled movie card, not merely the <a> containing the image.
     * Current result pages place <h3> beside or AFTER the watch link.
     */
    private static Element cardFor(Element a){
        for(Element p=a; p!=null&&!p.tagName().equals("body");p=p.parent()){
            if(p.hasClass("block1")||"newrelease_tab".equals(p.attr("name")))return p;
        }
        for(Element p=a.parent();p!=null&&!p.tagName().equals("body");p=p.parent()){
            int links=p.select("a[href*='movie/watch/']").size();
            if(links==1 && !p.select("h2,h3,h4,.movie-title,.title").isEmpty())return p;
        }
        return a.parent()==null?a:a.parent();
    }
    private static String extractTitle(Element a,Element card,String html,String id){
        String[] selectors={"h3","h2","h4",".movie-title",".film-title",".title", "[itemprop=name]"};
        // A heading in the card is the most reliable match: it may be an anchor sibling.
        for(String selector:selectors){
            Element heading=card.selectFirst(selector);
            if(heading!=null){String t=tidyTitle(heading.text());if(usableTitle(t,id))return t;}
        }
        for(String key:new String[]{"title","aria-label","data-title"}){
            String t=tidyTitle(a.attr(key));if(usableTitle(t,id))return t;
        }
        String anchor=tidyTitle(a.text());if(usableTitle(anchor,id))return anchor;
        for(Element p=a; p!=null&&!p.tagName().equals("body"); p=p.parent()){
            for(Element sibling:new Element[]{p.nextElementSibling(),p.previousElementSibling()}){
                if(sibling==null)continue;
                Element h=sibling.is("h3,h2,h4")?sibling:sibling.selectFirst("h3,h2,h4");
                if(h!=null){String t=tidyTitle(h.text());if(usableTitle(t,id))return t;}
            }
            if(p.select("a[href*='movie/watch/']").size()>1)break;
        }
        Element img=a.selectFirst("img[alt]");if(img==null)img=card.selectFirst("img[alt]");
        if(img!=null){String t=tidyTitle(img.attr("alt"));if(usableTitle(t,id))return t;}
        // Last on-page method: match the ID, then the nearest next h3, stopping
        // before the next movie's watch URL to avoid mismatching titles.
        Matcher link=Pattern.compile("(?is)movie/watch/"+Pattern.quote(id)+"/[^\"']*").matcher(html);
        if(link.find()){
            String fragment=html.substring(link.end(),Math.min(html.length(),link.end()+6500));
            int next=fragment.indexOf("movie/watch/");if(next>=0)fragment=fragment.substring(0,next);
            Matcher heading=Pattern.compile("(?is)<h[234][^>]*>(.*?)</h[234]>").matcher(fragment);
            if(heading.find()){
                String t=tidyTitle(heading.group(1));if(usableTitle(t,id))return t;
            }
        }
        return "";
    }
    /** Fetch the official watch page only when listing HTML has no usable title. */
    private String titleFromWatch(String watch,String referer,String id){
        synchronized(titleCache){if(titleCache.containsKey(watch))return titleCache.get(watch);}
        String found="";
        try{
            Document detail=Jsoup.parse(get(watch,referer),BASE);
            for(String selector:new String[]{"meta[property=og:title]","meta[name=twitter:title]"}){
                Element meta=detail.selectFirst(selector);
                if(meta!=null){String t=tidyTitle(meta.attr("content"));if(usableTitle(t,id)){found=t;break;}}
            }
            if(found.isEmpty()){
                for(String selector:new String[]{"h1","h2","[itemprop=name]"}){
                    String t=tidyTitle(readText(detail.selectFirst(selector)));
                    if(usableTitle(t,id)){found=t;break;}
                }
            }
            if(found.isEmpty()){
                String t=tidyTitle(detail.title());
                if(usableTitle(t,id) && !t.toLowerCase(Locale.ROOT).startsWith("login"))found=t;
            }
        }catch(Exception ignored){} // Some watch pages redirect; leave title unknown rather than show ID.
        synchronized(titleCache){if(titleCache.size()>320)titleCache.clear();titleCache.put(watch,found);}
        return found;
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
        ArrayList<Film> result=new ArrayList<>();
        // A movie can have BOTH an image anchor and a separate title anchor.
        // Never discard the second link before considering its text.
        Map<String,Film> candidates=new LinkedHashMap<>();
        int links=0;String langFallback=Uri.parse(safe).getQueryParameter("lang");
        for(Element a:doc.select("a[href]")){
            String href=a.attr("href");if(!href.contains("movie/watch/"))continue;
            links++;String watch=absolute(href);if(watch.isEmpty())continue;
            Uri uri=Uri.parse(watch);List<String> segments=uri.getPathSegments();
            if(segments.size()<3||!segments.get(0).equals("movie")||!segments.get(1).equals("watch"))continue;
            String id=segments.get(2),lang=uri.getQueryParameter("lang");
            if(TextUtils.isEmpty(lang))lang=langFallback;
            if(TextUtils.isEmpty(lang)||!lang.matches("[A-Za-z]+")||!id.matches("[A-Za-z0-9_-]+"))continue;
            String filmKey=lang+":"+id;
            Element card=cardFor(a);
            // A descriptive title anchor wins over a blank poster anchor.
            String title=tidyTitle(a.text());
            if(!usableTitle(title,id))title=extractTitle(a,card,html,id);
            Element plot=card.selectFirst(".synopsis,.summary,.description,p.synopsis");
            String synopsis=readText(plot);
            String poster=imageOf(card);if(poster.isEmpty())poster=imageOf(a);
            String quality=card.outerHtml().toLowerCase(Locale.ROOT).contains("ultrahd")?"uhd":"shd";
            Film old=candidates.get(filmKey);
            if(old!=null){
                if(!usableTitle(title,id))title=old.title;
                else if(usableTitle(old.title,id) && old.title.length()>=title.length())title=old.title;
                if(poster.isEmpty())poster=old.poster;
                if(synopsis.isEmpty())synopsis=old.synopsis;
                if(old.quality.equals("uhd"))quality="uhd";
            }
            candidates.put(filmKey,new Film(id,lang,title,poster,synopsis,watch,quality));
        }
        for(Film partial:candidates.values()){
            String title=partial.title;
            if(!usableTitle(title,partial.id))title=titleFromWatch(partial.detailUrl,safe,partial.id);
            if(!usableTitle(title,partial.id))title="Title unavailable";
            result.add(new Film(partial.id,partial.language,title,partial.poster,
                partial.synopsis,partial.detailUrl,partial.quality));
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
        if(next.isEmpty()){
            // Einthusan also uses data-disabled on numeric glyph pagination controls.
            // The total-page indicator is considerably more robust than "Next" text.
            Matcher count=Pattern.compile("(?i)page\\s+\\d+\\s+of\\s+(\\d+)").matcher(doc.text());
            if(count.find()){
                int last=Integer.parseInt(count.group(1));
                if(page<last)next=addPage(safe,page+1);
            }
        }
        return new Page(result,next,page);
    }
    private static String encode(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
    private static String attr(String html,String name){Element e=Jsoup.parse(html).selectFirst("["+name+"]");return e==null?"":e.attr(name);}
    private String cookieHeader(String url){
        try{
            List<String> nativeCookies=cookies.get(URI.create(url),Collections.emptyMap()).get("Cookie");
            if(nativeCookies!=null&&!nativeCookies.isEmpty())return TextUtils.join("; ",nativeCookies);
        }catch(Exception ignored){}
        return officialCookie();
    }
    /** Native sign-in via the same first-party Login AJAX flow used by the MIT Kodi add-on.
     * The password is never saved, and success is not asserted without an account check.
     */
    public LoginResult login(String email,String password,String language)throws Exception{
        if(email==null||!email.contains("@")||password==null||password.isEmpty())
            return new LoginResult(false,"Enter your email address and password.");
        if(language==null||!language.matches("[a-z]+"))language="tamil";
        String loginUrl=BASE+"/login/?lang="+Uri.encode(language);
        Document page=Jsoup.parse(get(loginUrl,BASE),BASE);
        Element pageId=page.selectFirst("[data-pageid]");
        String csrf=pageId==null?"":pageId.attr("data-pageid");
        if(csrf.isEmpty())return new LoginResult(false,"The sign-in form has changed. No credentials were submitted.");
        JSONObject credentials=new JSONObject();
        credentials.put("Email",email.trim());credentials.put("Password",password);
        String form="xEvent="+encode("Login")+"&xJson="+encode(credentials.toString())+
          "&arcVersion=3&appVersion=59&tabID="+encode(csrf+"48")+
          "&gorilla.csrf.Token="+encode(csrf);
        HttpURLConnection ajax=(HttpURLConnection)new URL(BASE+"/ajax/login/?lang="+Uri.encode(language)).openConnection();
        ajax.setRequestProperty("Origin",BASE);
        ajax.setRequestProperty("Referer",loginUrl);
        ajax.setRequestProperty("X-Requested-With","XMLHttpRequest");
        String response;
        try { response=request(ajax,form); }
        catch(Exception redirect){
            String why=redirect.getMessage();
            if(why!=null && (why.startsWith("HTTP 301")||why.startsWith("HTTP 302")||
              why.startsWith("HTTP 303")||why.startsWith("HTTP 307")||why.startsWith("HTTP 308"))){
                // The site can redirect after accepting authentication.
                // Never treat redirect alone as a successful login; verify account.
                response="";
            }else throw redirect;
        }
        credentials=null;
        // The reply can contain a site-specific error object; don't show its raw
        // contents because servers can echo submitted data or sensitive tokens.
        if(response.trim().startsWith("{")){
            try{
                JSONObject json=new JSONObject(response);
                if(json.optBoolean("Error",false) || "false".equalsIgnoreCase(json.optString("Success")))
                    return new LoginResult(false,"Sign-in was rejected. Please check your details.");
            }catch(Exception ignored){}
        }
        // Same follow-up URL as Kodi: the server can finalize the session here.
        String account=BASE+"/account/?flashmessage=success%3A%3A%3AYou+are+now+logged+in.&lang="+Uri.encode(language);
        String state;
        try{state=get(account,loginUrl);}catch(Exception e){
            return new LoginResult(false,"Credentials submitted, but account verification failed. Check your membership on the official site.");
        }
        if(verifiedAccountPage(state)){
            accountVerified=true;
            syncCookiesToWebView();
            return new LoginResult(true,"Signed in. Premium eligibility is checked when requesting an Ultra HD stream.");
        }
        accountVerified=false;
        return new LoginResult(false,"Login was submitted, but we could not verify an authenticated account. No premium access has been assumed.");
    }
    private static boolean verifiedAccountPage(String html){
        Document doc=Jsoup.parse(html);
        String text=doc.title().toLowerCase(Locale.ROOT);
        if(!doc.select("a[href*=logout],a[href*=signout],form[action*=logout]").isEmpty())return true;
        if(text.contains("login")||text.contains("sign in"))return false;
        // Account pages normally contain account settings, not a public login form.
        boolean account= text.contains("account") ||
          html.toLowerCase(Locale.ROOT).contains("account settings") ||
          html.toLowerCase(Locale.ROOT).contains("your membership");
        return account && !html.toLowerCase(Locale.ROOT).contains("please login to access account");
    }
    public boolean refreshAccount(String language){
        try{
            String html=get(BASE+"/account/?lang="+Uri.encode(language),BASE);
            accountVerified=verifiedAccountPage(html);
        }catch(Exception e){accountVerified=false;}
        return accountVerified;
    }
    /** Bridge authorized first-party cookies for the existing website fallback. */
    private void syncCookiesToWebView(){
        try{
            android.webkit.CookieManager web=android.webkit.CookieManager.getInstance();
            for(HttpCookie cookie:cookies.getCookieStore().getCookies()){
                String domain=cookie.getDomain();
                if(domain!=null&&!domain.equals("einthusan.tv")&&!domain.endsWith(".einthusan.tv"))continue;
                if(cookie.hasExpired()||cookie.getName().isEmpty())continue;
                web.setCookie(BASE,cookie.getName()+"="+cookie.getValue()+"; Path=/; Secure");
            }
            web.flush();
        }catch(Exception ignored){}
    }
    public void signOut(){
        accountVerified=false;
        cookies.getCookieStore().removeAll();
        try{android.webkit.CookieManager.getInstance().removeAllCookies(removed ->
            android.webkit.CookieManager.getInstance().flush());}catch(Exception ignored){}
    }
    /** The site's own membership checks decide whether UHD will play. */
    public Stream resolve(Film film,String fromListing,boolean uhd)throws Exception{
        if(uhd&&!accountVerified)throw new Exception("Sign in to an eligible account inside Elil TV before requesting Ultra HD.");
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
