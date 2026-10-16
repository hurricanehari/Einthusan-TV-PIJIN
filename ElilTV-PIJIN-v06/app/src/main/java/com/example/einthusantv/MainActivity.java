package com.example.einthusantv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
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

/** Elil TV — Brought by PIJIN. A native remote-first Fire TV experience. */
public class MainActivity extends Activity {
    private static final int BG=0xff07090e, NAV=0xff0c111a, SURFACE=0xff141c28;
    private static final int WHITE=0xfff8f9fb, MUTED=0xffa0afc4, GOLD=0xfff0c47f;
    private static final int BORDER=0xff263246, BLUE=0xff182939;
    private static final String[] LANGUAGES={"Tamil","Hindi","Telugu","Malayalam","Kannada","Bengali","Marathi","Punjabi"};
    private static final String[] RAILS={"Recently Added","Popular","Featured","Staff Picks"};
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final PosterLoader images=new PosterLoader();
    private final EinthusanClient source=new EinthusanClient();
    private final List<Film> films=new ArrayList<>();
    private final Set<String> loaded=new HashSet<>();
    private LinearLayout page,grid;
    private ScrollView scroll;
    private TextView status,accountChip;
    private ImageView heroArt;
    private TextView heroTitle,heroDesc;
    private View more;
    private String language="tamil",category="Recently Added",query="",next="";
    private boolean browsing=false,loading=false;
    private int generation=0,gridPosition=0;
    private long lastAuto=0;
    private Film selected,pendingUltra;
    private String mode="home";

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);home();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout.LayoutParams size(int w,int h,int right,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));
        p.setMargins(0,0,dp(right),dp(bottom));return p;
    }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable outlined(int color,int radius,int stroke){
        GradientDrawable d=bg(color,radius);d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String t,int sp,int color,boolean bold){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(color);
        if(bold)v.setTypeface(null,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    /** Styled focusable text actions avoid the browser-like Android default button chrome. */
    private TextView action(String t,Runnable run,boolean primary){
        TextView b=text(t,15,primary?BG:WHITE,true);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(18),dp(5),dp(18),dp(5));
        b.setFocusable(true);b.setClickable(true);
        b.setBackground(outlined(primary?GOLD:SURFACE,10,primary?GOLD:BORDER));
        b.setOnFocusChangeListener((v,focus)->{
            b.setBackground(outlined(focus?WHITE:primary?GOLD:SURFACE,10,focus?WHITE:primary?GOLD:BORDER));
            b.setTextColor(focus?BG:primary?BG:WHITE);
            v.setScaleX(focus?1.035f:1f);v.setScaleY(focus?1.035f:1f);
            v.setElevation(focus?dp(10):0);
        });
        b.setOnClickListener(v->run.run());return b;
    }
    private TextView navigation(String label,Runnable click){
        TextView v=text(label,16,MUTED,false);v.setPadding(dp(22),0,dp(10),0);
        v.setBackground(bg(NAV,10));v.setFocusable(true);v.setClickable(true);
        v.setOnFocusChangeListener((view,focus)->{
            v.setBackground(outlined(focus?BLUE:NAV,10,focus?GOLD:NAV));
            v.setTextColor(focus?WHITE:MUTED);
        });
        v.setOnClickListener(view->click.run());return v;
    }
    /** Fixed left navigation and scrollable content match native TV streaming patterns. */
    private void shell(String heading){
        FrameLayout full=new FrameLayout(this);full.setBackgroundColor(BG);setContentView(full);
        LinearLayout outer=row();outer.setGravity(Gravity.TOP);full.addView(outer,new FrameLayout.LayoutParams(-1,-1));
        ScrollView navigationScroll=new ScrollView(this);
        navigationScroll.setFillViewport(false);
        navigationScroll.setVerticalScrollBarEnabled(false);
        navigationScroll.setBackgroundColor(NAV);
        outer.addView(navigationScroll,size(208,-1,0,0));
        LinearLayout left=column();left.setBackgroundColor(NAV);
        left.setPadding(dp(14),dp(21),dp(12),dp(25));
        navigationScroll.addView(left);
        TextView mark=text("ELIL  TV",34,WHITE,true);mark.setLetterSpacing(.06f);
        mark.setPadding(dp(12),0,0,0);left.addView(mark,size(-1,55,0,0));
        TextView tagline=text("BROUGHT BY  PIJIN",11,GOLD,true);tagline.setLetterSpacing(.16f);
        tagline.setPadding(dp(12),0,0,0);left.addView(tagline,size(-1,28,0,30));
        left.addView(navigation("⌂   Home",this::home),size(-1,52,0,5));
        left.addView(navigation("▦   All films",()->browse("Recently Added","")),size(-1,52,0,5));
        left.addView(navigation("✦   Popular",()->browse("Popular","")),size(-1,52,0,5));
        left.addView(navigation("★   Featured",()->browse("Featured","")),size(-1,52,0,5));
        left.addView(navigation("◎   Staff picks",()->browse("Staff Picks","")),size(-1,52,0,5));
        left.addView(navigation("⌕   Search",this::search),size(-1,52,0,5));
        left.addView(navigation("A–Z   Collections",this::collectionPicker),size(-1,52,0,22));
        left.addView(navigation("♙   My account",this::account),size(-1,52,0,6));
        TextView mini=text("CINEMA AT HOME",11,0xff64748a,true);mini.setPadding(dp(13),dp(15),0,0);left.addView(mini);

        LinearLayout right=column();outer.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout top=row();top.setPadding(dp(30),dp(10),dp(35),dp(8));right.addView(top,size(-1,74,0,0));
        TextView headingView=text(heading,18,WHITE,true);top.addView(headingView,new LinearLayout.LayoutParams(0,-1,1));
        TextView lang=action("▾  "+capitalize(language),this::pickLanguage,false);top.addView(lang,size(150,43,12,0));
        accountChip=action(source.isSignedIn()?"●  SIGNED IN":"○  SIGN IN",this::account,false);
        top.addView(accountChip,size(133,43,0,0));
        View line=new View(this);line.setBackgroundColor(0xff1b2633);right.addView(line,size(-1,1,0,0));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page=column();page.setClipChildren(false);
        page.setPadding(dp(30),dp(24),dp(30),dp(66));scroll.addView(page);
        scroll.setOnScrollChangeListener((v,sx,sy,ox,oy)->{
            if(!browsing||loading||TextUtils.isEmpty(next))return;
            int remaining=page.getHeight()-scroll.getHeight()-sy;
            if(remaining<dp(440)&&SystemClock.elapsedRealtime()-lastAuto>1400){
                lastAuto=SystemClock.elapsedRealtime();loadBatch();
            }
        });
    }
    private static String capitalize(String value){
        if(value==null||value.isEmpty())return "Tamil";
        return value.substring(0,1).toUpperCase(Locale.ROOT)+value.substring(1);
    }
    private void pickLanguage(){
        new AlertDialog.Builder(this).setTitle("Select language").setItems(LANGUAGES,(d,index)->{
            language=LANGUAGES[index].toLowerCase(Locale.ROOT);
            if(mode.equals("browse"))browse(category,query);
            else home();
        }).show();
    }
    private void eyebrow(LinearLayout dest,String value){
        TextView v=text(value.toUpperCase(Locale.ROOT),12,GOLD,true);v.setLetterSpacing(.17f);
        dest.addView(v,size(-1,32,0,5));
    }
    private void title(LinearLayout dest,String value,int font){
        TextView v=text(value,font,WHITE,true);dest.addView(v,size(-1,58,0,6));
    }
    private void hero(Film first){
        FrameLayout card=new FrameLayout(this);card.setBackground(outlined(SURFACE,16,BORDER));
        page.addView(card,size(-1,370,0,29));
        heroArt=new ImageView(this);heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroArt.setBackground(bg(0xff182634,16));card.addView(heroArt,new FrameLayout.LayoutParams(-1,-1));
        GradientDrawable shade=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
          new int[]{0xff080c13,0xee080c13,0xa0080c13,0x1a080c13});
        View scrim=new View(this);scrim.setBackground(shade);card.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout copy=column();copy.setPadding(dp(25),dp(24),dp(10),dp(25));
        int available=Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density)-208-60;
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(Math.min(465,Math.max(265,available-25))),-1,Gravity.LEFT);
        card.addView(copy,cp);
        eyebrow(copy,"ELIL ORIGINAL EXPERIENCE   •   "+capitalize(language));
        heroTitle=text(first==null?"Your films. Beautifully presented.":first.title,34,WHITE,true);
        heroTitle.setMaxLines(2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heroTitle,size(-1,100,0,7));
        heroDesc=text(first==null?"Discover South Asian cinema with native playback, curated collections and a simple remote-first experience.":first.synopsis,
          15,0xffd3dbe7,false);
        heroDesc.setMaxLines(3);heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heroDesc,size(-1,101,0,9));
        LinearLayout buttons=row();copy.addView(buttons,size(-1,53,0,0));
        buttons.addView(action("▶  Explore films",()->browse("Recently Added",""),true),size(174,47,10,0));
        buttons.addView(action("Search",this::search,false),size(103,47,0,0));
        if(first!=null)images.load(this,heroArt,first.poster);
    }
    private void home(){
        generation++;mode="home";browsing=false;loading=false;next="";shell("Discover  /  "+capitalize(language));hero(null);
        final int gen=generation;
        for(String section:RAILS){
            LinearLayout head=row();page.addView(head,size(-1,49,0,8));
            TextView h=text(section,24,WHITE,true);head.addView(h,new LinearLayout.LayoutParams(0,-1,1));
            head.addView(action("View all  →",()->browse(section,""),false),size(120,38,0,0));
            HorizontalScrollView swipe=new HorizontalScrollView(this);swipe.setHorizontalScrollBarEnabled(false);
            swipe.setClipToPadding(false);swipe.setClipChildren(false);
            LinearLayout items=row();items.setGravity(Gravity.TOP);items.setClipChildren(false);swipe.addView(items);
            page.addView(swipe,size(-1,327,0,29));
            items.addView(text("Loading "+section+"…",16,MUTED,false),size(260,90,0,0));
            io.submit(()->{
                try{
                    EinthusanClient.Page results=source.browse(source.listingUrl(language,section,""));
                    runOnUiThread(()->{
                        if(isFinishing()||gen!=generation)return;
                        items.removeAllViews();
                        for(Film film:results.films)items.addView(movieCard(film));
                        if(section.equals("Recently Added")&&!results.films.isEmpty()){
                            Film first=results.films.get(0);
                            heroTitle.setText(first.title);
                            heroDesc.setText(first.synopsis.isEmpty()?"Featured from the latest collection":first.synopsis);
                            images.load(this,heroArt,first.poster);
                        }
                    });
                }catch(Exception e){
                    runOnUiThread(()->{if(isFinishing()||gen!=generation)return;
                        items.removeAllViews();items.addView(text("Collection currently unavailable",14,MUTED,false));});
                }
            });
        }
        TextView note=text("ELIL TV  ·  Brought by PIJIN  ·  Unofficial client",12,0xff6b7b90,false);
        page.addView(note,size(-1,36,0,0));
    }
    private void browse(String section,String q){
        generation++;mode="browse";browsing=true;loading=false;
        category=section;query=q;films.clear();loaded.clear();gridPosition=0;
        next=source.listingUrl(language,section,q);
        shell(capitalize(language)+"  /  "+(q.isEmpty()?section:"Search"));
        eyebrow(page,"THE COMPLETE COLLECTION");
        title(page,q.isEmpty()?section:"Search results for \""+q+"\"",34);
        TextView description=text("More films load as you browse  •  All titles play in Elil TV's native player",15,MUTED,false);
        page.addView(description,size(-1,37,0,12));
        LinearLayout tools=row();page.addView(tools,size(-1,53,0,17));
        tools.addView(action("A–Z",this::pickLetter,false),size(93,43,9,0));
        tools.addView(action("Year",this::pickYear,false),size(103,43,9,0));
        tools.addView(action("Decade",this::pickDecade,false),size(113,43,9,0));
        tools.addView(action("Ratings",this::pickRating,false),size(115,43,9,0));
        tools.addView(action("⌕  Search",this::search,false),size(132,43,0,0));
        status=text("Loading collection…",14,GOLD,false);page.addView(status,size(-1,36,0,8));
        grid=column();grid.setClipChildren(false);page.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        more=action("Load more films   ↓",this::loadBatch,false);page.addView(more,size(-1,49,0,0));
        more.setVisibility(View.GONE);
        loadBatch();
    }
    private String key(Film f){return f.language+":"+f.id;}
    private void loadBatch(){
        if(!browsing||loading||TextUtils.isEmpty(next))return;
        loading=true;int gen=generation;more.setVisibility(View.GONE);
        status.setText("Finding films  •  "+films.size()+" loaded");String start=next;
        io.submit(()->{
            ArrayList<Film> batch=new ArrayList<>();Set<String> inBatch=new HashSet<>();
            String cursor=start,prior="",failure="";
            try{
                for(int i=0;i<4&&!TextUtils.isEmpty(cursor);i++){
                    if(cursor.equals(prior))break;
                    EinthusanClient.Page result=source.browse(cursor);
                    for(Film film:result.films)if(inBatch.add(key(film)))batch.add(film);
                    prior=cursor;cursor=result.next;
                }
            }catch(Exception ex){failure=ex.getMessage();}
            final String nextPage=cursor,error=failure;
            runOnUiThread(()->{
                if(isFinishing()||gen!=generation)return;
                int inserted=0;
                for(Film film:batch)if(loaded.add(key(film))){films.add(film);addToGrid(film);inserted++;}
                next=nextPage;
                if(inserted==0)next="";
                if(!error.isEmpty()){
                    status.setText(films.size()+" films loaded  •  Additional pages: "+error);
                    next="";
                }else status.setText(films.size()+" films loaded  •  Browse for more");
                loading=false;
                more.setVisibility(TextUtils.isEmpty(next)?View.GONE:View.VISIBLE);
            });
        });
    }
    private LinearLayout currentGridRow;
    private int gridColumns(){
        float dpWidth=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
        float contentWidth=dpWidth-208-60; // left navigation and right content padding
        return contentWidth>=900?4:contentWidth>=575?3:2;
    }
    private int gridCardWidth(){
        float dpWidth=getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density;
        int columns=gridColumns();
        return Math.max(125,(int)((dpWidth-208-60-(columns-1)*15)/columns));
    }
    private void addToGrid(Film film){
        int cols=gridColumns(),width=gridCardWidth();
        if(gridPosition%cols==0){
            currentGridRow=row();currentGridRow.setGravity(Gravity.TOP);
            currentGridRow.setClipChildren(false);
            grid.addView(currentGridRow,size(-1,323,0,15));
        }
        currentGridRow.addView(movieCard(film,width),size(width,299,gridPosition%cols==cols-1?0:15,0));
        gridPosition++;
    }
    private View movieCard(Film film){return movieCard(film,185);}
    private View movieCard(Film film,int width){
        LinearLayout tile=column();tile.setBackground(bg(SURFACE,12));
        tile.setPadding(dp(5),dp(5),dp(5),dp(8));tile.setFocusable(true);tile.setClickable(true);
        tile.setLayoutParams(size(width,299,15,0));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(bg(0xff213045,8));tile.addView(poster,size(-1,190,0,5));
        images.load(this,poster,film.poster);
        TextView name=text(film.title,14,WHITE,true);name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(5),0,dp(5),0);
        tile.addView(name,size(-1,43,0,1));
        TextView badge=text(film.badge,11,GOLD,true);
        badge.setPadding(dp(5),0,0,0);
        tile.addView(badge,size(-1,19,0,1));
        TextView meta=text(("uhd".equals(film.quality)?"ULTRA HD  •  ":"")+capitalize(film.language),11,
          "uhd".equals(film.quality)?GOLD:MUTED,true);
        meta.setSingleLine(true);meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(dp(5),0,0,0);tile.addView(meta,size(-1,20,0,0));
        tile.setOnFocusChangeListener((v,focus)->{
            tile.setBackground(outlined(focus?0xff243247:SURFACE,12,focus?GOLD:SURFACE));
            tile.setScaleX(focus?1.055f:1f);tile.setScaleY(focus?1.055f:1f);
            tile.setElevation(focus?dp(12):0);
        });
        tile.setOnClickListener(v->details(film));return tile;
    }
    /** Full in-app details screen, rather than a small Android alert dialog. */
    private void details(Film film){
        selected=film;generation++;mode="details";browsing=false;loading=false;
        shell(capitalize(film.language)+"  /  Film details");
        LinearLayout content=row();content.setGravity(Gravity.TOP);
        content.setBackground(outlined(SURFACE,16,BORDER));content.setPadding(dp(22),dp(22),dp(20),dp(22));
        page.addView(content,size(-1,555,0,22));
        ImageView artwork=new ImageView(this);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackground(bg(0xff203047,9));content.addView(artwork,size(235,370,26,0));
        images.load(this,artwork,film.poster);
        LinearLayout info=column();content.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        eyebrow(info,"ELIL FILM  •  "+capitalize(film.language));
        if(!film.badge.isEmpty()){
            TextView promo=text(film.badge.toUpperCase(Locale.ROOT),12,GOLD,true);
            info.addView(promo,size(-1,25,0,2));
        }
        TextView name=text(film.title,36,WHITE,true);name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);info.addView(name,size(-1,126,0,12));
        TextView quality=text("uhd".equals(film.quality)?"PREMIUM ULTRA HD AVAILABLE":"HD STREAM",13,GOLD,true);
        info.addView(quality,size(-1,27,0,15));
        TextView synopsis=text(film.synopsis.isEmpty()?"Discover this film using Elil TV's built-in player.":film.synopsis,
          16,MUTED,false);synopsis.setMaxLines(5);synopsis.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(synopsis,size(-1,104,0,12));
        LinearLayout controls=row();info.addView(controls,size(-1,48,0,8));
        controls.addView(action("▶  Play",()->play(film,false),true),size(123,47,10,0));
        if("uhd".equals(film.quality))controls.addView(action("Ultra HD  ✦",()->play(film,true),false),size(158,47,0,0));
        LinearLayout secondary=row();info.addView(secondary,size(-1,48,0,0));
        secondary.addView(action(source.isSignedIn()?"My account":"Sign in",this::account,false),size(133,44,10,0));
        secondary.addView(action("Website fallback",()->openWebsite(film.detailUrl),false),size(183,44,0,0));
        TextView note=text("Ultra HD requires an eligible membership. Actual stream resolution is shown by the player.",13,MUTED,false);
        note.setMaxLines(2);page.addView(note,size(-1,56,0,0));
    }
    private void play(Film film,boolean ultra){
        if(ultra&&!source.isSignedIn()){
            pendingUltra=film;
            new AlertDialog.Builder(this).setTitle("Sign in required")
                .setMessage("Sign in directly inside Elil TV to request this film's Premium Ultra HD stream.")
                .setPositiveButton("Sign in",(d,w)->account()).setNegativeButton("Cancel",null).show();
            return;
        }
        AlertDialog spinner=new AlertDialog.Builder(this).setTitle("Elil TV")
            .setMessage((ultra?"Checking Ultra HD availability for ":"Preparing ")+film.title+"…").create();
        spinner.show();
        String referer=source.listingUrl(film.language,category,query);
        io.submit(()->{
            try{
                EinthusanClient.Stream stream=source.resolve(film,referer,ultra);
                runOnUiThread(()->{
                    spinner.dismiss();if(isFinishing())return;
                    Intent intent=new Intent(this,PlayerActivity.class);
                    intent.putExtra("video_url",stream.url);
                    intent.putExtra("video_headers",new HashMap<>(stream.headers));
                    intent.putExtra("video_title",film.title);
                    intent.putExtra("requested_ultra",ultra);
                    startActivity(intent);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    spinner.dismiss();if(isFinishing())return;
                    new AlertDialog.Builder(this).setTitle(ultra?"Ultra HD unavailable":"Playback unavailable")
                        .setMessage(e.getMessage())
                        .setPositiveButton("OK",null)
                        .setNeutralButton("Website fallback",(d,w)->openWebsite(film.detailUrl)).show();
                });
            }
        });
    }
    /** User-facing sign-in page is native; passwords are not stored or logged. */
    private void account(){
        generation++;mode="account";browsing=false;loading=false;
        shell("Account  /  Membership");
        eyebrow(page,"YOUR CINEMA ACCOUNT");
        title(page,"Your membership",36);
        TextView explanation=text("Sign in here to connect your official Einthusan account with Elil TV's native player. You won't be sent to a browser.",17,MUTED,false);
        explanation.setMaxLines(3);page.addView(explanation,size(-1,79,0,20));
        LinearLayout panel=column();panel.setPadding(dp(30),dp(25),dp(30),dp(24));
        panel.setBackground(outlined(SURFACE,17,BORDER));page.addView(panel,size(-1,-2,0,15));
        TextView accountStatus=text(source.isSignedIn()?"●  Signed in — membership checked at playback":"○  Not signed in",20,
          source.isSignedIn()?GOLD:WHITE,true);panel.addView(accountStatus,size(-1,50,0,8));
        TextView caveat=text("Elil TV uses Einthusan's official HTTPS login service. Credentials are used for sign-in only, not saved in the app.",14,MUTED,false);
        panel.addView(caveat,size(-1,65,0,12));
        EditText email=new EditText(this);email.setSingleLine(true);email.setTextSize(17);
        email.setTextColor(WHITE);email.setHintTextColor(MUTED);email.setHint("Email address");
        email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setPadding(dp(16),0,dp(16),0);email.setBackground(outlined(NAV,9,BORDER));
        panel.addView(email,size(-1,56,0,12));
        EditText password=new EditText(this);password.setSingleLine(true);password.setTextSize(17);
        password.setTextColor(WHITE);password.setHintTextColor(MUTED);password.setHint("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setPadding(dp(16),0,dp(16),0);password.setBackground(outlined(NAV,9,BORDER));
        panel.addView(password,size(-1,56,0,16));
        if(android.os.Build.VERSION.SDK_INT>=26){
            email.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        LinearLayout actions=row();panel.addView(actions,size(-1,52,0,9));
        TextView signIn=action("Sign in securely",()->{
            String address=email.getText().toString().trim();String secret=password.getText().toString();
            password.setText(""); // No password remains in the editable field.
            if(address.isEmpty()||secret.isEmpty()){
                accountStatus.setText("Enter your email and password.");return;
            }
            accountStatus.setText("Connecting to official account…");
            io.submit(()->{
                try{
                    EinthusanClient.LoginResult result=source.login(address,secret,language);
                    runOnUiThread(()->{
                        if(isFinishing()||!"account".equals(mode))return;
                        accountStatus.setText(result.message);
                        if(result.verified){
                            accountChip.setText("●  SIGNED IN");
                            Film pending=pendingUltra;pendingUltra=null;
                            if(pending!=null){
                                new AlertDialog.Builder(this).setTitle("Sign-in verified")
                                    .setMessage("Your account is connected. Request the selected film's Ultra HD stream now?")
                                    .setPositiveButton("Play Ultra HD",(d,w)->{details(pending);play(pending,true);})
                                    .setNegativeButton("Later",(d,w)->details(pending)).show();
                            }
                        }
                    });
                }catch(Exception error){
                    runOnUiThread(()->{if(!isFinishing()&&"account".equals(mode))
                        accountStatus.setText("Sign-in couldn't be completed: "+error.getMessage());});
                }
            });
        },true);
        actions.addView(signIn,size(186,48,12,0));
        actions.addView(action("Refresh status",()->{
            accountStatus.setText("Checking session…");
            io.submit(()->{boolean ok=source.refreshAccount(language);
                runOnUiThread(()->{
                    if(!isFinishing()&&"account".equals(mode)){
                        accountStatus.setText(ok?"●  Signed in — membership checked at playback":"○  No verified session");
                        accountChip.setText(ok?"●  SIGNED IN":"○  SIGN IN");
                    }
                });
            });
        },false),size(167,48,12,0));
        actions.addView(action("Sign out",()->{
            source.signOut();pendingUltra=null;
            accountStatus.setText("○  Signed out");accountChip.setText("○  SIGN IN");
        },false),size(116,48,0,0));
        TextView note=text("Premium access and available resolution are determined by Einthusan. Signing in does not automatically guarantee Ultra HD or 2160p 4K.",14,MUTED,false);
        page.addView(note,size(-1,73,0,0));
        signIn.requestFocus();
    }
    private void search(){
        EditText box=new EditText(this);box.setSingleLine(true);box.setHint("Search film titles");
        new AlertDialog.Builder(this).setTitle("Search "+capitalize(language)+" films").setView(box)
            .setPositiveButton("Search",(d,w)->browse("Recently Added",box.getText().toString().trim()))
            .setNegativeButton("Cancel",null).show();
    }
    private void choose(String prompt,String[] options,java.util.function.Consumer<String> click){
        new AlertDialog.Builder(this).setTitle(prompt).setItems(options,(d,i)->click.accept(options[i])).show();
    }
    private void collectionPicker(){
        choose("Explore collections",new String[]{"A–Z","Years","Decades","Ratings"},item->{
            if(item.equals("A–Z"))pickLetter();else if(item.equals("Years"))pickYear();
            else if(item.equals("Decades"))pickDecade();else pickRating();
        });
    }
    private void pickLetter(){
        String[] letters=new String[27];letters[0]="Numbers";
        for(int i=1;i<27;i++)letters[i]=String.valueOf((char)('A'+i-1));
        choose("Browse by letter",letters,l->browse(l.equals("Numbers")?"Numbers":"A-Z:"+l,""));
    }
    private void pickYear(){
        int now=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String[] years=new String[now-1940+1];for(int i=0;i<years.length;i++)years[i]=String.valueOf(now-i);
        choose("Browse by year",years,y->browse("Year:"+y,""));
    }
    private void pickDecade(){
        choose("Browse by decade",new String[]{"2020","2010","2000","1990","1980","1970","1960","1950","1940"},
            year->browse("Decade:"+year,""));
    }
    private void pickRating(){
        choose("Browse by rating",new String[]{"Action","Comedy","Romance","Storyline","Performance"},
            rating->browse("Rating:"+rating,""));
    }
    private void openWebsite(String url){
        Intent intent=new Intent(this,BrowserActivity.class);intent.putExtra("url",url);startActivity(intent);
    }
    @Override public void onBackPressed(){
        if("details".equals(mode) && selected!=null){browse(category,query);return;}
        if(!"home".equals(mode)){home();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){generation++;io.shutdownNow();images.shutdown();super.onDestroy();}
}
