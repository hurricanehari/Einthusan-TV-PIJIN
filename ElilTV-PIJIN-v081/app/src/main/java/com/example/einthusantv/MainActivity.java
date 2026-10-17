package com.example.einthusantv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewParent;
import android.view.View;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private ViewingStore viewing;
    private final List<Film> films=new ArrayList<>();
    private final Set<String> loaded=new HashSet<>();
    private LinearLayout page,grid,sidebar;
    private ScrollView scroll;
    private TextView status,accountChip,accountBanner;
    private ImageView heroArt;
    private TextView heroTitle,heroDesc;
    private View more;
    private String language="tamil",category="Recently Added",query="",next="";
    private boolean browsing=false,loading=false;
    private int generation=0,gridPosition=0;
    private long lastAuto=0;
    private Film selected,pendingUltra;
    private String mode="home",returnMode="home",localKind="";
    private boolean returningFromPlayer=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        viewing=new ViewingStore(this);
        source.initialize(this);
        // Enqueue authentication BEFORE home catalogue jobs to avoid HTTP 307
        // while a remembered session is being restored or renewed.
        io.submit(()->{
            boolean verified=source.restoreSession(language);
            runOnUiThread(()->{
                if(!isFinishing() && accountChip!=null){
                    accountChip.setText(verified?"●  SIGNED IN":"○  SIGN IN");
                    if(accountBanner!=null && "account".equals(mode))
                        accountBanner.setText(verified?
                            "●  Signed in — your account session is active":
                            "○  Not signed in — enter your account details below");
                }
            });
        });
        getWindow().getDecorView().setSystemUiVisibility(5894);
        home();
    }
    /** Refresh viewing rows and progress when returning from the player. */
    @Override protected void onResume(){
        super.onResume();
        if(viewing!=null && returningFromPlayer){
            // PlayerActivity wrote its own profile progress into SharedPreferences.
            // Reload it before rebuilding the home rails; otherwise the old in-memory
            // JSONObject would hide the latest Continue Watching entries.
            viewing=new ViewingStore(this);
            returningFromPlayer=false;
            if("details".equals(mode)&&selected!=null)details(selected);
            else if("personal".equals(mode))localCollection(localKind);
            else if("browse".equals(mode))browse(category,query);
            else home();
        }
    }
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
    /**
     * Prevent Fire TV from jumping to the profile chip when there is a button
     * or movie poster immediately to the right. Keep focus at the row edge.
     */
    private boolean focusWithinRow(View current,int direction){
        if(direction!=KeyEvent.KEYCODE_DPAD_RIGHT &&
           direction!=KeyEvent.KEYCODE_DPAD_LEFT)return false;
        ViewParent parent=current.getParent();
        if(!(parent instanceof LinearLayout))return false;
        LinearLayout row=(LinearLayout)parent;
        if(row.getOrientation()!=LinearLayout.HORIZONTAL)return false;
        int currentIndex=row.indexOfChild(current);
        int step=direction==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
        for(int i=currentIndex+step;i>=0 && i<row.getChildCount();i+=step){
            View candidate=row.getChildAt(i);
            if(candidate.getVisibility()==View.VISIBLE && candidate.isEnabled() &&
               candidate.isFocusable() && candidate.requestFocus())return true;
        }
        // At a row's right edge, stay here rather than jumping to the profile.
        // At its left edge, return to the nearest navigation item.
        return direction==KeyEvent.KEYCODE_DPAD_RIGHT || focusSidebarFromContent(current);
    }
    /** Return from content to the navigation item nearest the same screen height. */
    private boolean focusSidebarFromContent(View current){
        if(sidebar==null)return false;
        Rect from=new Rect();current.getGlobalVisibleRect(from);
        int targetY=from.centerY();
        View best=null;int nearest=Integer.MAX_VALUE;
        for(View candidate:sidebar.getFocusables(View.FOCUS_FORWARD)){
            if(!candidate.isShown()||!candidate.isEnabled()||!candidate.isFocusable())continue;
            Rect bounds=new Rect();
            if(!candidate.getGlobalVisibleRect(bounds)||bounds.isEmpty())continue;
            int distance=Math.abs(bounds.centerY()-targetY);
            if(distance<nearest){nearest=distance;best=candidate;}
        }
        return best!=null&&best.requestFocus();
    }
    /** Choose a visible main-content action near the sidebar item's height. */
    private boolean focusContentFromSidebar(View sidebarItem){
        if(page==null)return true;
        Rect current=new Rect();
        sidebarItem.getGlobalVisibleRect(current);
        int targetY=current.centerY();
        View best=null;
        int bestDistance=Integer.MAX_VALUE;
        for(View candidate:page.getFocusables(View.FOCUS_FORWARD)){
            if(!candidate.isEnabled() || !candidate.isShown() || !candidate.isFocusable())continue;
            Rect bounds=new Rect();
            if(!candidate.getGlobalVisibleRect(bounds) || bounds.isEmpty())continue;
            int dy=Math.abs(bounds.centerY()-targetY);
            // Prefer a control horizontally aligned with the chosen sidebar item.
            int distance=dy*5+Math.max(0,bounds.left-current.right)/16;
            if(distance<bestDistance){bestDistance=distance;best=candidate;}
        }
        if(best!=null){best.requestFocus();return true;}
        // When content has not laid out yet, give it another chance on next frame.
        page.post(()->{
            if(page==null)return;
            for(View candidate:page.getFocusables(View.FOCUS_FORWARD)){
                if(candidate.isShown() && candidate.isEnabled() && candidate.requestFocus())break;
            }
        });
        return true;
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
        b.setOnKeyListener((view,key,event)->event.getAction()==KeyEvent.ACTION_DOWN &&
            focusWithinRow(view,key));
        b.setOnClickListener(v->run.run());return b;
    }
    private TextView navigation(String label,Runnable click){
        TextView v=text(label,16,MUTED,false);v.setPadding(dp(22),0,dp(10),0);
        v.setBackground(bg(NAV,10));v.setFocusable(true);v.setClickable(true);
        v.setOnFocusChangeListener((view,focus)->{
            v.setBackground(outlined(focus?BLUE:NAV,10,focus?GOLD:NAV));
            v.setTextColor(focus?WHITE:MUTED);
        });
        v.setOnKeyListener((view,key,event)->event.getAction()==KeyEvent.ACTION_DOWN &&
            key==KeyEvent.KEYCODE_DPAD_RIGHT && focusContentFromSidebar(view));
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
        sidebar=left;
        left.setPadding(dp(14),dp(21),dp(12),dp(25));
        navigationScroll.addView(left);
        TextView mark=text("ELIL  TV",34,WHITE,true);mark.setLetterSpacing(.06f);
        mark.setPadding(dp(12),0,0,0);left.addView(mark,size(-1,55,0,0));
        TextView tagline=text("BROUGHT BY  PIJIN",11,GOLD,true);tagline.setLetterSpacing(.16f);
        tagline.setPadding(dp(12),0,0,0);left.addView(tagline,size(-1,28,0,30));
        left.addView(navigation("⌕   Search",this::search),size(-1,52,0,5));
        left.addView(navigation("⌂   Home",this::home),size(-1,52,0,5));
        left.addView(navigation("▶   Continue Watching",()->localCollection("Continue Watching")),size(-1,52,0,5));
        left.addView(navigation("＋   My List",()->localCollection("My List")),size(-1,52,0,5));
        left.addView(navigation("↺   Recently Watched",()->localCollection("Recently Watched")),size(-1,52,0,5));
        left.addView(navigation("▦   All films",()->browse("Recently Added","")),size(-1,52,0,5));
        left.addView(navigation("✦   Popular",()->browse("Popular","")),size(-1,52,0,5));
        left.addView(navigation("★   Featured",()->browse("Featured","")),size(-1,52,0,5));
        left.addView(navigation("◎   Staff picks",()->browse("Staff Picks","")),size(-1,52,0,5));
        left.addView(navigation("A–Z   Collections",this::collectionPicker),size(-1,52,0,22));
        left.addView(navigation("♙   My account",this::account),size(-1,52,0,6));
        left.addView(navigation("⬆   App Updates",()->startActivity(new Intent(this,UpdateActivity.class))),size(-1,52,0,6));
        left.addView(navigation("◉   Switch profile",this::profiles),size(-1,52,0,6));
        TextView mini=text("CINEMA AT HOME",11,0xff64748a,true);mini.setPadding(dp(13),dp(15),0,0);left.addView(mini);

        LinearLayout right=column();outer.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout top=row();top.setPadding(dp(30),dp(10),dp(35),dp(8));right.addView(top,size(-1,74,0,0));
        TextView headingView=text(heading,18,WHITE,true);top.addView(headingView,new LinearLayout.LayoutParams(0,-1,1));
        TextView profileChip=action("◉  "+viewing.activeName(),this::profiles,false);
        profileChip.setSingleLine(true);profileChip.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(profileChip,size(145,43,10,0));
        TextView lang=action("▾  "+capitalize(language),this::pickLanguage,false);top.addView(lang,size(130,43,12,0));
        accountChip=action(source.isSignedIn()?"●  SIGNED IN":"○  SIGN IN",this::account,false);
        top.addView(accountChip,size(133,43,0,0));
        View line=new View(this);line.setBackgroundColor(0xff1b2633);right.addView(line,size(-1,1,0,0));
        // Account confirmation stays on-screen when the form scrolls to its buttons.
        accountBanner=null;
        if("account".equals(mode)){
            accountBanner=text(source.isSignedIn()?
                "●  Signed in — your account session is active":
                "○  Not signed in — enter your account details below",
                15,source.isSignedIn()?GOLD:WHITE,true);
            accountBanner.setMaxLines(2);
            accountBanner.setEllipsize(TextUtils.TruncateAt.END);
            accountBanner.setPadding(dp(30),dp(4),dp(22),dp(4));
            accountBanner.setBackgroundColor(0xff1c2938);
            right.addView(accountBanner,size(-1,56,0,0));
        }
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
        TextView v=text(value,Math.min(font,24),WHITE,true);dest.addView(v,size(-1,46,0,5));
    }
    private void hero(Film first){
        FrameLayout card=new FrameLayout(this);card.setBackground(outlined(SURFACE,16,BORDER));
        page.addView(card,size(-1,348,0,24));
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
        heroTitle=text(first==null?"Your films. Beautifully presented.":first.title,24,WHITE,true);
        heroTitle.setMaxLines(2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heroTitle,size(-1,82,0,7));
        heroDesc=text(first==null?"Discover South Asian cinema with native playback, curated collections and a simple remote-first experience.":first.synopsis,
          15,0xffd3dbe7,false);
        heroDesc.setMaxLines(3);heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heroDesc,size(-1,86,0,9));
        LinearLayout buttons=row();copy.addView(buttons,size(-1,53,0,0));
        buttons.addView(action("▶  Explore films",()->browse("Recently Added",""),true),size(174,47,10,0));
        buttons.addView(action("Search",this::search,false),size(103,47,0,0));
        if(first!=null)images.load(this,heroArt,first.poster);
    }
    private void home(){
        generation++;mode="home";browsing=false;loading=false;next="";shell("Discover  /  "+capitalize(language));hero(null);
        personalRail("Continue Watching",viewing.continueWatching());
        personalRail("My List",viewing.myList());
        personalRail("Recently Watched",viewing.recentlyWatched());
        final int gen=generation;
        for(String section:RAILS){
            LinearLayout head=row();page.addView(head,size(-1,49,0,8));
            TextView h=text(section,18,WHITE,true);head.addView(h,new LinearLayout.LayoutParams(0,-1,1));
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
    private List<ViewingStore.Entry> personalEntries(String kind){
        if("Continue Watching".equals(kind))return viewing.continueWatching();
        if("My List".equals(kind))return viewing.myList();
        return viewing.recentlyWatched();
    }
    private void personalRail(String kind,List<ViewingStore.Entry> entries){
        if(entries.isEmpty())return;
        LinearLayout head=row();page.addView(head,size(-1,49,0,8));
        TextView title=text(kind,18,WHITE,true);head.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        head.addView(action("View all  →",()->localCollection(kind),false),size(120,38,0,0));
        HorizontalScrollView rail=new HorizontalScrollView(this);
        rail.setHorizontalScrollBarEnabled(false);rail.setClipToPadding(false);rail.setClipChildren(false);
        LinearLayout items=row();items.setGravity(Gravity.TOP);items.setClipChildren(false);
        rail.addView(items);page.addView(rail,size(-1,327,0,29));
        for(int i=0;i<Math.min(entries.size(),20);i++){
            ViewingStore.Entry e=entries.get(i);
            View card=movieCard(e.film);
            if("Continue Watching".equals(kind))
                card.setOnClickListener(v->play(e.film,e.ultra,true));
            items.addView(card);
        }
    }
    private void localCollection(String kind){
        generation++;mode="personal";localKind=kind;browsing=false;loading=false;next="";
        shell(viewing.activeName()+"  /  "+kind);
        eyebrow(page,"YOUR PERSONAL CINEMA");title(page,kind,34);
        List<ViewingStore.Entry> entries=personalEntries(kind);
        TextView explanation=text(entries.isEmpty()?"Nothing here yet. Start watching films or add one to My List.":
            entries.size()+" films saved for "+viewing.activeName(),15,MUTED,false);
        page.addView(explanation,size(-1,60,0,18));
        grid=column();grid.setClipChildren(false);page.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        gridPosition=0;
        for(ViewingStore.Entry e:entries)addToGrid(e.film);
        if(entries.isEmpty())page.addView(action("Explore films",()->browse("Recently Added",""),true),size(180,48,0,0));
    }
    private void profiles(){
        generation++;mode="profiles";browsing=false;loading=false;next="";
        shell("Profiles  /  "+viewing.activeName());
        eyebrow(page,"ONE EINTHUSAN ACCOUNT · SEPARATE LOCAL PROFILES");
        title(page,"Who's watching?",36);
        TextView intro=text("Each profile has its own My List, playback progress and viewing history on this Firestick. Your Einthusan sign-in is shared across the household.",16,MUTED,false);
        intro.setMaxLines(3);page.addView(intro,size(-1,95,0,18));
        for(ViewingStore.Profile p:viewing.profiles()){
            String label=(p.id.equals(viewing.activeId())?"●  ":"○  ")+p.name;
            LinearLayout profileRow=row();
            page.addView(profileRow,size(-1,57,0,12));
            TextView choose=action(label,()->{viewing.switchTo(p.id);home();},
                p.id.equals(viewing.activeId()));
            choose.setSingleLine(true);choose.setEllipsize(TextUtils.TruncateAt.END);
            profileRow.addView(choose,size(330,53,12,0));
            profileRow.addView(action("✎  Rename",()->renameProfile(p),false),size(150,53,0,0));
        }
        page.addView(action("+ Add household profile",this::addProfile,false),size(360,52,0,22));
        TextView caution=text("Profiles are for convenience, not private accounts: anyone using this Firestick can select another local profile. No separate PIN or parental restrictions are implemented.",13,MUTED,false);
        caution.setMaxLines(4);page.addView(caution,size(-1,90,0,0));
    }
    private void renameProfile(ViewingStore.Profile profile){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText(profile.name);
        input.selectAll();
        input.setHint("Your profile name");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        new AlertDialog.Builder(this).setTitle("Rename profile")
            .setMessage("Choose the name shown in Elil TV. Your watch history and My List won't change.")
            .setView(input)
            .setPositiveButton("Save",(d,w)->{
                if(viewing.renameProfile(profile.id,input.getText().toString()))profiles();
                else Toast.makeText(this,"Enter a unique profile name (1–24 characters).",Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Cancel",null).show();
    }
    private void addProfile(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Profile name (up to 24 characters)");
        new AlertDialog.Builder(this).setTitle("New household profile").setView(input)
            .setPositiveButton("Create",(d,w)->{
                if(viewing.addProfile(input.getText().toString()))home();
                else Toast.makeText(this,"Use a name up to 24 characters (maximum 6 profiles).",Toast.LENGTH_LONG).show();
            }).setNegativeButton("Cancel",null).show();
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
        FrameLayout artwork=new FrameLayout(this);
        tile.addView(artwork,size(-1,190,0,5));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(bg(0xff213045,8));artwork.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        images.load(this,poster,film.poster);
        ViewingStore.Entry saved=viewing.entry(film);
        if(saved!=null && saved.started && saved.durationMs>0){
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);progress.setProgress(saved.percent());
            progress.setProgressTintList(ColorStateList.valueOf(GOLD));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff3c4a58));
            FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,dp(5),Gravity.BOTTOM);
            artwork.addView(progress,pp);
        }
        TextView name=text(film.title,12,WHITE,true);name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(5),0,dp(5),0);
        tile.addView(name,size(-1,43,0,1));
        TextView badge=text(film.badge,11,GOLD,true);
        badge.setPadding(dp(5),0,0,0);tile.addView(badge,size(-1,19,0,1));
        TextView meta=text(("uhd".equals(film.quality)?"ULTRA HD  •  ":"")+capitalize(film.language),11,
          "uhd".equals(film.quality)?GOLD:MUTED,true);
        meta.setSingleLine(true);meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(dp(5),0,0,0);tile.addView(meta,size(-1,20,0,0));
        tile.setOnFocusChangeListener((v,focus)->{
            tile.setBackground(outlined(focus?0xff243247:SURFACE,12,focus?GOLD:SURFACE));
            tile.setScaleX(focus?1.055f:1f);tile.setScaleY(focus?1.055f:1f);
            tile.setElevation(focus?dp(12):0);
        });
        tile.setOnKeyListener((view,key,event)->event.getAction()==KeyEvent.ACTION_DOWN &&
            focusWithinRow(view,key));
        tile.setOnClickListener(v->details(film));
        tile.setOnLongClickListener(v->{quickMenu(film);return true;});
        return tile;
    }
    private void quickMenu(Film film){
        ViewingStore.Entry entry=viewing.entry(film);
        boolean resume=entry!=null&&entry.canResume();
        boolean saved=viewing.isSaved(film);
        List<String> choices=new ArrayList<>();
        if(resume)choices.add("▶ Resume from "+formatTime(entry.positionMs));
        choices.add("Film details");
        choices.add("↻ Play from beginning");
        choices.add(saved?"− Remove from My List":"+ Add to My List");
        if(resume && !entry.hidden)choices.add("× Remove from Continue Watching");
        new AlertDialog.Builder(this).setTitle(film.title)
            .setItems(choices.toArray(new String[0]),(d,index)->{
                String choice=choices.get(index);
                if(choice.equals("Film details"))details(film);
                else if(choice.startsWith("▶"))play(film,entry.ultra,true);
                else if(choice.startsWith("↻"))play(film,false,false);
                else if(choice.contains("My List")){
                    viewing.setSaved(film,!saved);
                    refreshCurrentScreen();
                }else if(choice.startsWith("×")){
                    viewing.removeFromContinue(film);refreshCurrentScreen();
                }
            }).show();
    }
    private static String formatTime(long time){
        long minutes=Math.max(0,time)/60000;
        return minutes>=60?(minutes/60)+"h "+(minutes%60)+"m":minutes+"m";
    }
    private void refreshCurrentScreen(){
        if("personal".equals(mode))localCollection(localKind);
        else if("details".equals(mode)&&selected!=null)details(selected);
        else if("home".equals(mode))home();
        else if("browse".equals(mode))browse(category,query);
    }
    /** Full in-app details screen, rather than a small Android alert dialog. */
    /** Full in-app film details, including resume, restart and local watchlist. */
    private void details(Film film){
        if(!"details".equals(mode))returnMode=mode;
        selected=film;generation++;mode="details";browsing=false;loading=false;
        shell(capitalize(film.language)+"  /  Film details");
        LinearLayout content=row();content.setGravity(Gravity.TOP);
        content.setBackground(outlined(SURFACE,16,BORDER));content.setPadding(dp(22),dp(22),dp(20),dp(22));
        page.addView(content,size(-1,655,0,22));
        ImageView artwork=new ImageView(this);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackground(bg(0xff203047,9));content.addView(artwork,size(235,370,26,0));
        images.load(this,artwork,film.poster);
        LinearLayout info=column();content.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        eyebrow(info,"ELIL FILM  •  "+capitalize(film.language));
        if(!film.badge.isEmpty()){
            TextView promo=text(film.badge.toUpperCase(Locale.ROOT),12,GOLD,true);
            info.addView(promo,size(-1,25,0,2));
        }
        TextView name=text(film.title,24,WHITE,true);name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);info.addView(name,size(-1,98,0,8));
        TextView quality=text("uhd".equals(film.quality)?"PREMIUM ULTRA HD AVAILABLE":"HD STREAM",13,GOLD,true);
        info.addView(quality,size(-1,27,0,10));
        TextView synopsis=text(film.synopsis.isEmpty()?"Discover this film using Elil TV's built-in player.":film.synopsis,
          16,MUTED,false);synopsis.setMaxLines(5);synopsis.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(synopsis,size(-1,95,0,9));
        ViewingStore.Entry entry=viewing.entry(film);
        String time=entry!=null&&entry.canResume()?"Last watched at "+formatTime(entry.positionMs)+
            (entry.remainingMs()>0?"  ·  "+formatTime(entry.remainingMs())+" remaining":""):
            entry!=null&&entry.completed?"Watched · Ready to restart":"Ready to watch";
        TextView progress=text(time,13,GOLD,false);info.addView(progress,size(-1,31,0,5));
        LinearLayout controls=row();info.addView(controls,size(-1,49,0,8));
        if(entry!=null&&entry.canResume()){
            controls.addView(action("▶  Resume",()->play(film,entry.ultra,true),true),size(137,47,9,0));
            controls.addView(action("↻  Restart",()->play(film,false,false),false),size(135,47,9,0));
        }else{
            controls.addView(action("▶  Play",()->play(film,false,false),true),size(122,47,9,0));
            if(entry!=null&&entry.started)controls.addView(action("↻  Restart",()->play(film,false,false),false),size(135,47,9,0));
        }
        if("uhd".equals(film.quality))controls.addView(action("Ultra HD  ✦",()->play(film,true,false),false),size(155,47,0,0));
        // Focus the main Play/Resume action when opening details, not the header
        // profile chip. Post until Android has attached and laid out the view.
        if(controls.getChildCount()>0){
            View primaryPlay=controls.getChildAt(0);
            primaryPlay.setFocusableInTouchMode(true);
            primaryPlay.post(()->{
                if(!isFinishing() && "details".equals(mode) && selected==film)
                    primaryPlay.requestFocus();
            });
        }
        LinearLayout savedRow=row();info.addView(savedRow,size(-1,49,0,8));
        boolean isSaved=viewing.isSaved(film);
        savedRow.addView(action(isSaved?"✓  In My List":"+  My List",()->{
            viewing.setSaved(film,!isSaved);details(film);
        },false),size(155,44,10,0));
        if(entry!=null&&entry.canResume()&&!entry.hidden)
            savedRow.addView(action("Remove from Continue",()->{
                viewing.removeFromContinue(film);details(film);
            },false),size(203,44,0,0));
        LinearLayout secondary=row();info.addView(secondary,size(-1,48,0,0));
        secondary.addView(action(source.isSignedIn()?"My account":"Sign in",this::account,false),size(133,44,10,0));
        secondary.addView(action("Website fallback",()->openWebsite(film.detailUrl),false),size(183,44,0,0));
        TextView note=text("All profiles share one Einthusan membership. Progress, My List and history stay separate on this Firestick.",13,MUTED,false);
        note.setMaxLines(2);page.addView(note,size(-1,56,0,0));
    }
    private void play(Film film,boolean ultra){play(film,ultra,false);}
    /** Resolve a fresh authorised stream before resuming; never persist stream URLs. */
    private void play(Film film,boolean ultra,boolean resume){
        if(ultra&&!source.isSignedIn()){
            pendingUltra=film;
            new AlertDialog.Builder(this).setTitle("Sign in required")
                .setMessage("Sign in inside Elil TV to request this film's Premium Ultra HD stream.")
                .setPositiveButton("Sign in",(d,w)->account()).setNegativeButton("Cancel",null).show();
            return;
        }
        final ViewingStore.Entry progress=viewing.entry(film);
        final long startAt=resume&&progress!=null&&progress.canResume()?progress.positionMs:0L;
        final String profileId=viewing.activeId();
        AlertDialog spinner=new AlertDialog.Builder(this).setTitle("Elil TV")
            .setMessage((ultra?"Checking Ultra HD availability for ":"Preparing ")+film.title+"…").create();
        spinner.show();
        String referer=source.listingUrl(film.language,category,query);
        io.submit(()->{
            try{
                EinthusanClient.Stream stream=source.resolve(film,referer,ultra);
                runOnUiThread(()->{
                    spinner.dismiss();if(isFinishing())return;
                    viewing.begin(profileId,film,ultra,!resume);
                    Intent intent=new Intent(this,PlayerActivity.class);
                    intent.putExtra("video_url",stream.url);
                    intent.putExtra("video_headers",new HashMap<>(stream.headers));
                    intent.putExtra("video_title",film.title);
                    intent.putExtra("film_id",film.id);
                    intent.putExtra("film_language",film.language);
                    intent.putExtra("film_poster",film.poster);
                    intent.putExtra("film_synopsis",film.synopsis);
                    intent.putExtra("film_url",film.detailUrl);
                    intent.putExtra("film_quality",film.quality);
                    intent.putExtra("film_badge",film.badge);
                    intent.putExtra("profile_id",profileId);
                    intent.putExtra("start_position_ms",startAt);
                    intent.putExtra("requested_ultra",ultra);
                    returningFromPlayer=true;
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
    /** Native first-party login. Optional credential storage is encrypted with Android Keystore. */
    private void account(){
        generation++;mode="account";browsing=false;loading=false;
        shell("Account  /  Membership");
        eyebrow(page,"YOUR CINEMA ACCOUNT");
        title(page,"Your membership",36);
        TextView explanation=text("Sign in once to use Einthusan with Elil TV's native player. All household profiles share the same account.",14,MUTED,false);
        explanation.setMaxLines(2);page.addView(explanation,size(-1,52,0,10));
        LinearLayout panel=column();panel.setPadding(dp(24),dp(15),dp(24),dp(15));
        panel.setBackground(outlined(SURFACE,17,BORDER));page.addView(panel,size(-1,-2,0,12));
        // This banner is outside the ScrollView, so sign-in results never scroll off-screen.
        final TextView accountStatus=accountBanner;
        EditText email=new EditText(this);email.setSingleLine(true);email.setTextSize(16);
        email.setTextColor(WHITE);email.setHintTextColor(MUTED);email.setHint("Email address");
        email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setPadding(dp(16),0,dp(16),0);email.setBackground(outlined(NAV,9,BORDER));
        panel.addView(email,size(-1,49,0,9));
        EditText password=new EditText(this);password.setSingleLine(true);password.setTextSize(16);
        password.setTextColor(WHITE);password.setHintTextColor(MUTED);password.setHint("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setPadding(dp(16),0,dp(16),0);password.setBackground(outlined(NAV,9,BORDER));
        panel.addView(password,size(-1,49,0,9));
        CheckBox remember=new CheckBox(this);
        remember.setText("Remember my login on this Firestick (encrypted)");
        remember.setTextColor(WHITE);remember.setTextSize(14);
        remember.setButtonTintList(ColorStateList.valueOf(GOLD));
        remember.setChecked(source.hasRememberedLogin());
        remember.setFocusable(true);
        panel.addView(remember,size(-1,43,0,9));
        if(android.os.Build.VERSION.SDK_INT>=26){
            email.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        LinearLayout actions=row();panel.addView(actions,size(-1,52,0,0));
        TextView signIn=action("Sign in securely",()->{
            String address=email.getText().toString().trim();String secret=password.getText().toString();
            final boolean keep=remember.isChecked();
            password.setText(""); // No password remains in the editable field.
            if(address.isEmpty()||secret.isEmpty()){
                accountStatus.setText("Enter your email and password.");return;
            }
            accountStatus.setText("◌  Signing in — checking your account…");
            io.submit(()->{
                try{
                    EinthusanClient.LoginResult result=source.login(address,secret,language);
                    boolean stored=false;
                    if(result.verified){
                        if(keep)stored=source.rememberLogin(address,secret);
                        else source.forgetRememberedLogin();
                    }
                    final boolean remembered=stored;
                    runOnUiThread(()->{
                        if(isFinishing()||!"account".equals(mode))return;
                        accountStatus.setText(result.verified ?
                            (keep && !remembered ?
                                "●  Signed in, but Remember Login could not save the password":
                                "●  Signed in successfully — your session is active") :
                            "○  Sign-in unsuccessful — "+result.message);
                        accountStatus.setTextColor(result.verified?GOLD:WHITE);
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
            io.submit(()->{boolean ok=source.restoreSession(language);
                runOnUiThread(()->{
                    if(!isFinishing()&&"account".equals(mode)){
                        accountStatus.setText(ok?"●  Signed in — your account session is active":"○  No verified session");
                        accountStatus.setTextColor(ok?GOLD:WHITE);
                        accountChip.setText(ok?"●  SIGNED IN":"○  SIGN IN");
                    }
                });
            });
        },false),size(167,48,12,0));
        actions.addView(action("Sign out",()->{
            source.signOut();pendingUltra=null;
            accountStatus.setText("○  Signed out");accountStatus.setTextColor(WHITE);
            accountChip.setText("○  SIGN IN");
        },false),size(116,48,0,0));
        TextView note=text("Remember Login uses this Firestick's Android Keystore. Sign out clears your saved credentials. Premium access is controlled by Einthusan.",12,MUTED,false);
        note.setMaxLines(3);
        page.addView(note,size(-1,65,0,0));
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
        if("details".equals(mode) && selected!=null){
            if("personal".equals(returnMode))localCollection(localKind);
            else if("home".equals(returnMode))home();
            else browse(category,query);
            return;
        }
        if(!"home".equals(mode)){home();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){generation++;io.shutdownNow();images.shutdown();super.onDestroy();}
}
