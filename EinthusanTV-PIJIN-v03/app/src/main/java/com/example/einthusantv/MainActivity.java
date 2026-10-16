package com.example.einthusantv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-first native Fire TV catalogue. No website is embedded in this screen. */
public class MainActivity extends Activity {
    private static final int BACKGROUND=0xff0b101b, PANEL=0xff172236, ACCENT=0xff67d7dd;
    private static final String[] LANGUAGES={"Tamil","Hindi","Telugu","Malayalam","Kannada","Bengali","Marathi","Punjabi"};
    private static final String[] SECTIONS={"Recently Added","Featured","Most Watched","Staff Picks","A-Z"};
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final PosterLoader posters=new PosterLoader();
    private final EinthusanClient source=new EinthusanClient();
    private final List<Film> films=new ArrayList<>();
    private ScrollView scroll;
    private LinearLayout screen;
    private GridLayout grid;
    private TextView status;
    private Button more;
    private String lang="tamil", section="Recently Added", query="", currentUrl="", next="";
    private int generation=0;
    private boolean busy=false;

    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private GradientDrawable background(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private Button button(String caption,Runnable action){
        Button b=new Button(this);b.setText(caption);b.setTextSize(14);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackground(background(PANEL,10));
        b.setPadding(dp(12),dp(4),dp(12),dp(4));b.setFocusable(true);
        b.setOnFocusChangeListener((v,focus)->{
            b.setBackground(background(focus?0xff276778:PANEL,10));
            v.setScaleX(focus?1.06f:1f);v.setScaleY(focus?1.06f:1f);
        });
        b.setOnClickListener(v->action.run());return b;
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894);render();load(false);}

    private void render(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BACKGROUND);setContentView(root);
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(38),dp(20),dp(38),dp(28));scroll.addView(screen);
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(heading,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView brand=text("EINTHUSAN TV",32,Color.WHITE);brand.setTypeface(null,Typeface.BOLD);
        heading.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        TextView edition=text("PIJIN  •  v0.3",15,ACCENT);heading.addView(edition);
        TextView hint=text("Native movie catalogue  •  Select a language and category",14,0xffaab6c8);
        hint.setPadding(0,0,0,dp(12));screen.addView(hint);
        LinearLayout languages=new LinearLayout(this);languages.setOrientation(LinearLayout.HORIZONTAL);
        screen.addView(languages);
        for(String language:LANGUAGES){
            Button b=button(language,()->{lang=language.toLowerCase(java.util.Locale.ROOT);query="";load(false);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(0,0,dp(6),0);languages.addView(b,lp);
        }
        LinearLayout sections=new LinearLayout(this);sections.setPadding(0,dp(12),0,dp(12));
        screen.addView(sections);
        for(String item:SECTIONS){
            Button b=button(item,()->{section=item;query="";load(false);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);lp.setMargins(0,0,dp(10),0);sections.addView(b,lp);
        }
        Button search=button("⌕ Search films",this::search);
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(-1,dp(43));searchLp.setMargins(0,0,0,dp(10));screen.addView(search,searchLp);
        status=text("Loading catalogue…",17,0xffe0e9f5);status.setPadding(0,dp(8),0,dp(12));screen.addView(status);
        grid=new GridLayout(this);grid.setColumnCount(4);screen.addView(grid);
        more=button("Load more films ↓",()->{if(!next.isEmpty())load(true);});
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-1,dp(55));mlp.setMargins(0,dp(15),0,dp(15));screen.addView(more,mlp);
        more.setVisibility(View.GONE);
        Button website=button("Website fallback (only if native playback fails)",()->openWebsite(EinthusanClient.BASE));
        screen.addView(website,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView foot=text("Private PIJIN Edition test build · Independent from Einthusan and EinTamil",12,0xff8495aa);
        foot.setPadding(0,dp(20),0,0);screen.addView(foot);
    }
    private void search(){
        EditText entry=new EditText(this);entry.setSingleLine(true);entry.setHint("Movie title");
        entry.setText(query);entry.setPadding(dp(14),dp(12),dp(14),dp(12));
        new AlertDialog.Builder(this).setTitle("Search " + lang + " films")
            .setView(entry).setPositiveButton("Search",(d,w)->{
                query=entry.getText().toString().trim();load(false);
            }).setNegativeButton("Cancel",null).show();
    }
    private void load(boolean append){
        if(busy)return;
        String address=append?next:source.listingUrl(lang,section,query);
        if(!append){films.clear();grid.removeAllViews();next="";}
        int gen=++generation;
        busy=true;more.setEnabled(false);
        status.setText("Loading " + (query.isEmpty()?section:query) + " · " + lang + "…");
        io.submit(()->{
            try{
                EinthusanClient.Page page=source.browse(address);
                runOnUiThread(()->{
                    if(isFinishing()||generation!=gen)return;
                    currentUrl=address; next=page.next;
                    films.addAll(page.films);
                    for(Film film:page.films)addCard(film);
                    status.setText(films.size()+" films · "+lang+" · "+(query.isEmpty()?section:"Search: "+query));
                    more.setVisibility(next.isEmpty()?View.GONE:View.VISIBLE);
                    more.setEnabled(true);busy=false;
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    if(isFinishing()||generation!=gen)return;
                    status.setText("Catalogue unavailable: " + ex.getMessage());
                    more.setEnabled(true);busy=false;
                });
            }
        });
    }
    private void addCard(Film film){
        LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);
        cell.setBackground(background(PANEL,9));cell.setPadding(dp(7),dp(7),dp(7),dp(7));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0xff263752);
        cell.addView(poster,new LinearLayout.LayoutParams(-1,dp(147)));
        posters.load(this,poster,film.poster);
        TextView name=text(film.title,14,Color.WHITE);name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,dp(51));titleParams.setMargins(0,dp(7),0,0);
        cell.addView(name,titleParams);
        cell.setFocusable(true);cell.setClickable(true);
        cell.setOnFocusChangeListener((v,focus)->{
            v.setBackground(background(focus?0xff296779:PANEL,9));
            v.setScaleX(focus?1.05f:1f);v.setScaleY(focus?1.05f:1f);
        });
        cell.setOnClickListener(v->details(film));
        GridLayout.LayoutParams glp=new GridLayout.LayoutParams();
        glp.width=dp(190);glp.height=dp(225);glp.setMargins(0,0,dp(13),dp(15));
        grid.addView(cell,glp);
    }
    private void details(Film film){
        String body=(film.synopsis.isEmpty()?"Select Play to open in the app's native video player.":film.synopsis)
            +"\n\nLanguage: "+film.language+"  ·  Quality: "+film.quality.toUpperCase(java.util.Locale.ROOT);
        new AlertDialog.Builder(this).setTitle(film.title).setMessage(body)
            .setPositiveButton("▶ Play native",(d,w)->play(film))
            .setNeutralButton("Website fallback",(d,w)->openWebsite(film.detailUrl))
            .setNegativeButton("Back",null).show();
    }
    private void play(Film film){
        AlertDialog progress=new AlertDialog.Builder(this).setTitle("PIJIN Edition")
            .setMessage("Resolving native playback for "+film.title+"…")
            .setCancelable(true).create();progress.show();
        final String ref=currentUrl;
        io.submit(()->{
            try{
                EinthusanClient.Stream s=source.resolve(film,ref);
                runOnUiThread(()->{
                    progress.dismiss();if(isFinishing())return;
                    Intent intent=new Intent(this,PlayerActivity.class);
                    intent.putExtra("video_url",s.url);
                    intent.putExtra("video_headers",new HashMap<>(s.headers));
                    intent.putExtra("video_title",film.title);
                    startActivity(intent);
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    progress.dismiss();if(isFinishing())return;
                    new AlertDialog.Builder(this).setTitle("Native playback unavailable")
                        .setMessage(ex.getMessage()+"\n\nYou can still play this film using the existing website option.")
                        .setPositiveButton("Open website",(d,w)->openWebsite(film.detailUrl))
                        .setNegativeButton("Back",null).show();
                });
            }
        });
    }
    private void openWebsite(String address){
        Intent intent=new Intent(this,BrowserActivity.class);
        intent.putExtra("url",address);startActivity(intent);
    }
    @Override protected void onDestroy(){generation++;io.shutdownNow();posters.shutdown();super.onDestroy();}
}
