package com.example.einthusantv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** PIJIN Edition - remote-first home rails and progressive full-catalogue browser. */
public class MainActivity extends Activity {
    private static final int BG=0xff080c16, SURFACE=0xff161c2a, ON=Color.WHITE;
    private static final int GOLD=0xffe9b76b, MUTED=0xffaab3c4, STROKE=0xff324057;
    private static final String[] LANGS={"Tamil","Hindi","Telugu","Malayalam","Kannada","Bengali","Marathi","Punjabi"};
    private static final String[] HOME_RAILS={"Recently Added","Popular","Featured","Staff Picks"};
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final PosterLoader posters=new PosterLoader();
    private final EinthusanClient client=new EinthusanClient();
    private final List<Film> films=new ArrayList<>();
    private final Set<String> loaded=new HashSet<>();
    private LinearLayout rootContent,mainColumn,gridHost;
    private ScrollView scroll;
    private TextView status,heroTitle,heroDescription;
    private ImageView heroArt;
    private Button more;
    private String lang="tamil",section="Recently Added",query="",next="",lastUrl="";
    private boolean browseMode=false,busy=false;
    private int generation=0;
    private long lastAuto=0L;

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894);home();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable fill(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable border(int color,int radius,int outline){GradientDrawable g=fill(color,radius);g.setStroke(dp(1),outline);return g;}
    private TextView label(String t,int size,int col,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(col);v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private Button button(String t,Runnable click,boolean featured){
        Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(featured?BG:ON);
        b.setBackground(border(featured?GOLD:SURFACE,10,featured?GOLD:STROKE));b.setFocusable(true);
        b.setOnFocusChangeListener((v,f)->{
            v.setScaleX(f?1.04f:1f);v.setScaleY(f?1.04f:1f);
            b.setBackground(border(f?0xff56637b:featured?GOLD:SURFACE,10,f?GOLD:featured?GOLD:STROKE));
            b.setTextColor(f?ON:featured?BG:ON);
        });
        b.setOnClickListener(v->click.run());return b;
    }
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int right,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));p.setMargins(0,0,dp(right),dp(bottom));return p;}
    private void shell(){
        FrameLayout full=new FrameLayout(this);full.setBackgroundColor(BG);setContentView(full);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);full.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        rootContent=vertical();rootContent.setPadding(dp(40),dp(16),dp(38),dp(24));scroll.addView(rootContent);
        LinearLayout top=horizontal();rootContent.addView(top,lp(-1,58,0,5));
        TextView brand=label("PIJIN",29,GOLD,true);brand.setLetterSpacing(.13f);top.addView(brand,lp(128,-1,4,0));
        TextView sub=label("EINTHUSAN TV",15,ON,true);top.addView(sub,new LinearLayout.LayoutParams(0,-1,1));
        Button home=button("Home",this::home,false);top.addView(home,lp(91,42,9,0));
        Button catalog=button("Browse All",()->browse("Recently Added",""),false);top.addView(catalog,lp(120,42,9,0));
        Button search=button("Search",this::search,false);top.addView(search,lp(95,42,9,0));
        Button account=button("Premium Sign In",this::login,false);top.addView(account,lp(143,42,0,0));
        LinearLayout languages=horizontal();rootContent.addView(languages,lp(-1,46,0,13));
        for(String l:LANGS){
            boolean selected=l.equalsIgnoreCase(lang);
            Button ch=button(l,()->{lang=l.toLowerCase(Locale.ROOT);if(browseMode)browse(section,query);else home();},selected);
            languages.addView(ch,new LinearLayout.LayoutParams(0,dp(41),1));
        }
        mainColumn=vertical();rootContent.addView(mainColumn,new LinearLayout.LayoutParams(-1,-2));
        scroll.setOnScrollChangeListener((v,sx,sy,ox,oy)->{
            if(!browseMode||busy||TextUtils.isEmpty(next))return;
            int remaining=rootContent.getHeight()-scroll.getHeight()-sy;
            if(remaining<dp(380)&&SystemClock.elapsedRealtime()-lastAuto>1200){lastAuto=SystemClock.elapsedRealtime();loadBatch(true);}
        });
    }
    private void pillRow(LinearLayout dest){
        LinearLayout row=horizontal();dest.addView(row,lp(-1,52,0,16));
        String[] names={"Recently Added","Popular","Featured","Staff Picks","A-Z","Year","Decade","Ratings"};
        for(String name:names){
            Button b=button(name,()->{
                if(name.equals("A-Z"))pickAlphabet();else if(name.equals("Year"))pickYear();
                else if(name.equals("Decade"))pickDecade();else if(name.equals("Ratings"))pickRating();
                else browse(name,"");
            },name.equals(section)&&browseMode);
            row.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
        }
    }
    private void home(){
        ++generation;browseMode=false;busy=false;next="";shell();
        LinearLayout hero=horizontal();hero.setPadding(dp(28),dp(19),dp(16),dp(18));hero.setBackground(border(0xff131b2c,16,STROKE));
        mainColumn.addView(hero,lp(-1,252,0,18));
        LinearLayout info=vertical();hero.addView(info,new LinearLayout.LayoutParams(0,-1,3));
        TextView eyebrow=label("CURATED FOR YOUR SCREEN    •    "+lang.toUpperCase(Locale.ROOT),13,GOLD,true);info.addView(eyebrow,lp(-1,36,0,11));
        heroTitle=label("Cinema, without the clutter.",27,ON,true);heroTitle.setMaxLines(2);info.addView(heroTitle,lp(-1,75,0,7));
        heroDescription=label("Discover South Asian films in a native Fire TV experience. Select a title to play inside PIJIN.",15,MUTED,false);
        heroDescription.setMaxLines(3);info.addView(heroDescription,lp(-1,58,0,12));
        Button browse=button("Explore complete catalogue  →",()->browse("Recently Added",""),true);info.addView(browse,lp(274,42,0,0));
        heroArt=new ImageView(this);heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);heroArt.setBackground(fill(0xff30435e,12));
        hero.addView(heroArt,new LinearLayout.LayoutParams(0,-1,2));
        pillRow(mainColumn);
        final int gen=generation;
        for(String category:HOME_RAILS){
            LinearLayout titleRow=horizontal();mainColumn.addView(titleRow,lp(-1,38,0,10));
            TextView heading=label(category,21,ON,true);titleRow.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
            Button all=button("View all  →",()->browse(category,""),false);titleRow.addView(all,lp(120,36,0,0));
            HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);
            LinearLayout items=horizontal();rail.addView(items);mainColumn.addView(rail,lp(-1,244,0,22));
            items.addView(label("Loading films…",15,MUTED,false),lp(230,60,0,0));
            io.submit(()->{
                try{
                    EinthusanClient.Page page=client.browse(client.listingUrl(lang,category,""));
                    runOnUiThread(()->{
                        if(isFinishing()||gen!=generation)return;
                        items.removeAllViews();
                        for(Film film:page.films)items.addView(card(film,true));
                        if(category.equals("Recently Added")&&!page.films.isEmpty()){
                            Film head=page.films.get(0);
                            heroTitle.setText(head.title);heroDescription.setText(head.synopsis.isEmpty()?"Featured in "+lang+" films":head.synopsis);
                            posters.load(this,heroArt,head.poster);
                        }
                    });
                }catch(Exception error){
                    runOnUiThread(()->{
                        if(gen!=generation||isFinishing())return;
                        items.removeAllViews();items.addView(label("Couldn't load "+category+". Browse by collection or try again.",13,MUTED,false));
                    });
                }
            });
        }
        TextView foot=label("PIJIN EDITION  •  Private TV prototype  •  Not affiliated with the streaming service",12,MUTED,false);
        mainColumn.addView(foot,lp(-1,38,0,0));
    }
    private void browse(String category,String newQuery){
        ++generation;browseMode=true;section=category;query=newQuery;busy=false;films.clear();loaded.clear();next="";
        shell();pillRow(mainColumn);
        String title=query.isEmpty()?category:"Search: "+query;
        TextView head=label(title,29,ON,true);mainColumn.addView(head,lp(-1,48,0,2));
        TextView desc=label("Browse the complete "+lang+" collection. More movies appear automatically as you scroll.",15,MUTED,false);
        mainColumn.addView(desc,lp(-1,34,0,9));
        status=label("Loading movies…",15,GOLD,false);mainColumn.addView(status,lp(-1,33,0,8));
        gridHost=vertical();mainColumn.addView(gridHost,new LinearLayout.LayoutParams(-1,-2));
        more=button("Load the next movies  ↓",()->loadBatch(true),false);mainColumn.addView(more,lp(-1,52,0,13));
        more.setVisibility(View.GONE);
        Button fallback=button("Open official Einthusan website (fallback)",()->openWebsite(EinthusanClient.BASE),false);
        mainColumn.addView(fallback,lp(-1,44,0,0));
        lastUrl=client.listingUrl(lang,section,query);next=lastUrl;
        loadBatch(false);
    }
    private String key(Film f){return f.language+":"+f.id;}
    /** Four six-film pages at a time; paging continues until the site reports the end. */
    private void loadBatch(boolean append){
        if(!browseMode||busy||TextUtils.isEmpty(next))return;
        busy=true;int gen=generation;
        more.setVisibility(View.GONE);
        status.setText("Loading "+lang+" films  •  "+films.size()+" shown…");
        final String start=next;
        io.submit(()->{
            ArrayList<Film> batch=new ArrayList<>();Set<String> local=new HashSet<>();
            String cursor=start,prior="",error="";int pages=0;
            try{
                for(;pages<4&&!TextUtils.isEmpty(cursor);pages++){
                    if(cursor.equals(prior))break;
                    EinthusanClient.Page result=client.browse(cursor);
                    for(Film film:result.films){if(local.add(key(film)))batch.add(film);}
                    prior=cursor;cursor=result.next;
                }
            }catch(Exception e){error=e.getMessage();}
            String remaining=cursor,fail=error;int fetchedPages=pages;
            runOnUiThread(()->{
                if(isFinishing()||gen!=generation)return;
                int added=0;
                for(Film film:batch){if(loaded.add(key(film))){films.add(film);addGridCard(film);added++;}}
                next=remaining;
                if(added==0)next="";
                if(added>0)status.setText(films.size()+" films loaded  •  "+lang+"  •  "+section+"  •  scroll for more");
                else status.setText(fail.isEmpty()?"End of this collection":"Catalogue: "+fail);
                if(!fail.isEmpty()&&added>0){next="";status.setText(films.size()+" films loaded; more pages unavailable: "+fail);}
                if(TextUtils.isEmpty(next))more.setVisibility(View.GONE);else more.setVisibility(View.VISIBLE);
                busy=false;
            });
        });
    }
    private LinearLayout currentRow;
    private int rowCount;
    private void addGridCard(Film film){
        if(films.size()==1){currentRow=null;rowCount=0;}
        if(currentRow==null||rowCount>=4){
            currentRow=horizontal();currentRow.setGravity(Gravity.TOP);gridHost.addView(currentRow,lp(-1,245,0,13));rowCount=0;
        }
        currentRow.addView(card(film,false),lp(185,230,14,0));rowCount++;
    }
    private View card(Film film,boolean rail){
        LinearLayout cell=vertical();cell.setPadding(dp(5),dp(5),dp(5),dp(7));cell.setBackground(border(SURFACE,9,STROKE));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackground(fill(0xff28364b,7));
        cell.addView(poster,new LinearLayout.LayoutParams(-1,dp(rail?162:157)));posters.load(this,poster,film.poster);
        TextView title=label(film.title,14,ON,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(title,lp(-1,43,0,0));
        if("uhd".equals(film.quality)){
            TextView mark=label("ULTRA HD AVAILABLE",10,GOLD,true);cell.addView(mark,lp(-1,15,0,0));
        }
        cell.setFocusable(true);cell.setClickable(true);
        cell.setOnFocusChangeListener((v,focus)->{
            cell.setBackground(border(focus?0xff344158:SURFACE,9,focus?GOLD:STROKE));
            cell.setScaleX(focus?1.05f:1f);cell.setScaleY(focus?1.05f:1f);
        });
        cell.setOnClickListener(v->details(film));
        if(rail){LinearLayout.LayoutParams p=lp(178,222,13,0);cell.setLayoutParams(p);}
        return cell;
    }
    private void details(Film film){
        LinearLayout body=horizontal();body.setPadding(dp(18),dp(15),dp(18),dp(15));body.setBackground(fill(SURFACE,16));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackground(fill(0xff28364b,10));
        body.addView(poster,lp(210,285,20,0));posters.load(this,poster,film.poster);
        LinearLayout right=vertical();body.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        TextView head=label(film.title,28,ON,true);head.setMaxLines(2);right.addView(head,lp(-1,80,0,9));
        TextView meta=label(film.language.toUpperCase(Locale.ROOT)+"    •    "+("uhd".equals(film.quality)?"ULTRA HD OPTION":"HD STREAM"),13,GOLD,true);
        right.addView(meta,lp(-1,33,0,10));
        TextView synopsis=label(film.synopsis.isEmpty()?"Stream with PIJIN's built-in player.":film.synopsis,15,MUTED,false);
        synopsis.setMaxLines(5);right.addView(synopsis,lp(-1,105,0,12));
        LinearLayout actions=horizontal();right.addView(actions,lp(-1,45,0,9));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(body).create();
        Button hd=button("▶ Play HD",()->{dialog.dismiss();play(film,false);},true);actions.addView(hd,lp(130,44,8,0));
        Button uhd=button("Play 4K",()->{dialog.dismiss();play(film,true);},false);
        if(film.quality.equals("uhd"))actions.addView(uhd,lp(110,44,0,0));
        LinearLayout extras=horizontal();right.addView(extras,lp(-1,44,0,0));
        Button web=button("Website fallback",()->{dialog.dismiss();openWebsite(film.detailUrl);},false);
        extras.addView(web,lp(178,42,8,0));
        Button account=button("Premium sign in",()->{dialog.dismiss();login();},false);extras.addView(account,lp(168,42,0,0));
        dialog.show();dialog.getWindow().setLayout(Math.min(dp(950),getResources().getDisplayMetrics().widthPixels-dp(60)),-2);
        hd.requestFocus();
    }
    private void play(Film film,boolean ultra){
        AlertDialog waiting=new AlertDialog.Builder(this).setTitle("PIJIN TV")
            .setMessage((ultra?"Checking 4K entitlement and stream for ":"Preparing ")+film.title+"…").create();waiting.show();
        final String ref=client.listingUrl(film.language,section,query);
        io.submit(()->{
            try{
                EinthusanClient.Stream stream=client.resolve(film,ref,ultra);
                runOnUiThread(()->{
                    waiting.dismiss();if(isFinishing())return;
                    Intent intent=new Intent(this,PlayerActivity.class);
                    intent.putExtra("video_url",stream.url);
                    intent.putExtra("video_headers",new HashMap<>(stream.headers));
                    intent.putExtra("video_title",film.title);startActivity(intent);
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    waiting.dismiss();if(isFinishing())return;
                    new AlertDialog.Builder(this).setTitle(ultra?"4K playback unavailable":"Playback unavailable")
                        .setMessage(ex.getMessage()+"\n\nFor Ultra HD, sign in with an eligible Premium account and select a supported film.")
                        .setPositiveButton("Official website",(d,w)->openWebsite(film.detailUrl))
                        .setNeutralButton("Sign in",(d,w)->login())
                        .setNegativeButton("Back",null).show();
                });
            }
        });
    }
    private void login(){
        new AlertDialog.Builder(this).setTitle("Einthusan Premium")
            .setMessage("Sign in on Einthusan's official login page. PIJIN never asks for or stores your password. Return to the app after logging in, then select Play 4K for an Ultra HD-labelled film. The service checks your entitlement.")
            .setPositiveButton("Open official sign in",(d,w)->openWebsite(EinthusanClient.BASE+"/login/?lang="+lang))
            .setNegativeButton("Cancel",null).show();
    }
    private void search(){
        EditText text=new EditText(this);text.setSingleLine(true);text.setHint("Film title");text.setText(query);
        new AlertDialog.Builder(this).setTitle("Search "+lang+" films").setView(text)
            .setPositiveButton("Search",(d,w)->browse("Recently Added",text.getText().toString().trim()))
            .setNegativeButton("Cancel",null).show();
    }
    private void select(String title,String[] choices,java.util.function.Consumer<String> callback){
        new AlertDialog.Builder(this).setTitle(title).setItems(choices,(d,which)->callback.accept(choices[which]))
            .setNegativeButton("Cancel",null).show();
    }
    private void pickAlphabet(){
        String[] letters=new String[27];letters[0]="Numbers";
        for(int i=1;i<27;i++)letters[i]=String.valueOf((char)('A'+i-1));
        select("Browse A–Z",letters,choice->browse(choice.equals("Numbers")?"Numbers":"A-Z:"+choice,""));
    }
    private void pickYear(){
        String[] years=new String[2026-1940+1];for(int i=0;i<years.length;i++)years[i]=String.valueOf(2026-i);
        select("Choose release year",years,y->browse("Year:"+y,""));
    }
    private void pickDecade(){
        String[] decades={"2020","2010","2000","1990","1980","1970","1960","1950","1940"};
        select("Choose decade",decades,d->browse("Decade:"+d,""));
    }
    private void pickRating(){
        select("Browse by rating",new String[]{"Action","Comedy","Romance","Storyline","Performance"},r->browse("Rating:"+r,""));
    }
    private void openWebsite(String address){Intent i=new Intent(this,BrowserActivity.class);i.putExtra("url",address);startActivity(i);}
    @Override protected void onDestroy(){generation++;io.shutdownNow();posters.shutdown();super.onDestroy();}
}
