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
    private FrameLayout drawerLayer;
    private View drawerScrim,drawerPanel;
    private View lastContentFocus;
    private boolean drawerShown=false;
    private Film featured;
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
        // Poster carousels link their last film to the Explore action. Other
        // horizontal rows (hero controls, detail actions, search filters) keep
        // their existing focus behaviour instead of jumping to the top bar.
        if(direction==KeyEvent.KEYCODE_DPAD_RIGHT){
            Object linked=row.getTag();
            if(linked instanceof View){
                View explore=(View)linked;
                if(explore.isShown() && explore.isEnabled() && explore.requestFocus())return true;
            }
            return true;
        }
        // Moving left beyond the first poster opens the hidden navigation.
        return focusSidebarFromContent(current);
    }
    /** Content occupies the whole television. The drawer appears only at the left edge. */
    private boolean focusSidebarFromContent(View current){
        openDrawer(current);
        return true;
    }
    private void openDrawer(View from){
        if(drawerLayer==null || drawerShown)return;
        lastContentFocus=from;
        drawerShown=true;
        drawerLayer.setVisibility(View.VISIBLE);
        drawerLayer.bringToFront();
        if(drawerScrim!=null){
            drawerScrim.animate().cancel();
            drawerScrim.setAlpha(0f);
            drawerScrim.animate().alpha(1f).setDuration(175).start();
        }
        if(drawerPanel!=null){
            drawerPanel.animate().cancel();
            drawerPanel.setTranslationX(-dp(233));
            drawerPanel.animate().translationX(0f).setDuration(175).start();
        }
        if(sidebar!=null)sidebar.post(()->{
            if(!drawerShown || sidebar==null)return;
            for(View candidate:sidebar.getFocusables(View.FOCUS_FORWARD)){
                if(candidate.isShown() && candidate.isEnabled() && candidate.requestFocus())break;
            }
        });
    }
    private void closeDrawer(){
        if(!drawerShown)return;
        drawerShown=false;
        if(drawerScrim!=null)drawerScrim.animate().alpha(0f).setDuration(140).start();
        if(drawerPanel!=null)drawerPanel.animate().translationX(-dp(233)).setDuration(140)
            .withEndAction(()->{if(!drawerShown && drawerLayer!=null)drawerLayer.setVisibility(View.GONE);}).start();
        View focus=lastContentFocus;
        if(focus!=null && focus.isAttachedToWindow() && focus.isShown() && focus.requestFocus())return;
        if(page!=null)page.post(()->{
            if(drawerShown)return;
            for(View candidate:page.getFocusables(View.FOCUS_FORWARD)){
                if(candidate.isShown() && candidate.isEnabled() && candidate.requestFocus())break;
            }
        });
    }
    private boolean focusContentFromSidebar(View sidebarItem){
        closeDrawer();return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){
                if(drawerShown)closeDrawer();else openDrawer(getCurrentFocus());
                return true;
            }
            if(drawerShown && event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT){
                closeDrawer();return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    /** Restrained television actions: flat surfaces, no blue outlined form buttons. */
    private TextView action(String t,Runnable run,boolean primary){
        TextView b=text(t,13,primary?BG:WHITE,true);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(4),dp(12),dp(4));
        b.setFocusable(true);b.setClickable(true);
        b.setBackground(bg(primary?0xfff1f2f4:0xff202735,5));
        b.setOnFocusChangeListener((v,focus)->{
            b.setBackground(outlined(focus?GOLD:(primary?0xfff1f2f4:0xff202735),5,
                focus?GOLD:(primary?0xfff1f2f4:0xff202735)));
            b.setTextColor(focus?BG:(primary?BG:WHITE));
            v.animate().scaleX(focus?1.025f:1f).scaleY(focus?1.025f:1f).setDuration(115).start();
        });
        b.setOnKeyListener((view,key,event)->event.getAction()==KeyEvent.ACTION_DOWN &&
            focusWithinRow(view,key));
        b.setOnClickListener(v->run.run());return b;
    }
    private TextView navigation(String label,Runnable click){
        TextView v=text(label,14,0xffbcc7d3,false);
        v.setPadding(dp(16),0,dp(8),0);v.setFocusable(true);v.setClickable(true);
        v.setBackground(bg(NAV,4));
        v.setOnFocusChangeListener((view,focus)->{
            v.setBackground(bg(focus?0xff263243:NAV,4));
            v.setTextColor(focus?GOLD:0xffbcc7d3);
        });
        v.setOnKeyListener((view,key,event)->event.getAction()==KeyEvent.ACTION_DOWN &&
            key==KeyEvent.KEYCODE_DPAD_RIGHT && focusContentFromSidebar(view));
        v.setOnClickListener(view->click.run());return v;
    }
    /** Explore sits above a horizontal film rail, but the D-pad must enter
     * that rail rather than jump to the next vertically aligned Explore link.
     * If the rail is loading, keep focus on Explore until films are available.
     */
    private void linkExploreToRail(TextView explore,HorizontalScrollView rail,LinearLayout items){
        items.setTag(explore); // Used by focusWithinRow at the last film.
        explore.setOnKeyListener((view,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN ||
               key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            for(int i=0;i<items.getChildCount();i++){
                View movie=items.getChildAt(i);
                if(movie.isFocusable() && movie.isEnabled() && movie.isShown()){
                    rail.smoothScrollTo(0,0);
                    movie.requestFocus();
                    return true;
                }
            }
            // Avoid unintentionally jumping to the Explore link below while
            // the catalogue network request is still filling this row.
            return true;
        });
    }
    /** Text-only header links disappear into the background until focused. */
    private TextView headerLink(String label,Runnable click){
        TextView v=text(label,12,0xffe2e6ea,false);
        v.setGravity(Gravity.CENTER);v.setPadding(dp(7),0,dp(7),0);
        v.setFocusable(true);v.setClickable(true);v.setBackground(bg(0x00000000,3));
        v.setOnFocusChangeListener((item,focused)->{
            v.setBackground(bg(focused?GOLD:0x00000000,3));
            v.setTextColor(focused?BG:0xffe2e6ea);
        });
        v.setOnClickListener(item->click.run());return v;
    }
    private LinearLayout headerContent(boolean onArt,String heading){
        LinearLayout top=row();top.setPadding(dp(32),0,dp(30),0);
        TextView brand=text("ELIL",20,WHITE,true);
        brand.setLetterSpacing(.09f);brand.setFocusable(true);
        brand.setOnClickListener(v->openDrawer(v));
        brand.setOnKeyListener((v,k,e)->e.getAction()==KeyEvent.ACTION_DOWN &&
            (k==KeyEvent.KEYCODE_DPAD_LEFT || k==KeyEvent.KEYCODE_DPAD_CENTER) &&
            focusSidebarFromContent(v));
        top.addView(brand,size(57,-1,0,0));
        TextView tv=text("TV",20,GOLD,true);tv.setFocusable(true);
        tv.setOnKeyListener((v,k,e)->e.getAction()==KeyEvent.ACTION_DOWN &&
            k==KeyEvent.KEYCODE_DPAD_LEFT && focusSidebarFromContent(v));
        tv.setOnClickListener(v->openDrawer(v));
        top.addView(tv,size(36,-1,0,0));
        if(!onArt){
            TextView path=text(heading,12,MUTED,false);
            path.setSingleLine(true);path.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(path,new LinearLayout.LayoutParams(0,-1,1));
        }else{
            top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        }
        TextView profile=headerLink("◉  "+viewing.activeName(),this::profiles);
        profile.setSingleLine(true);profile.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(profile,size(121,38,7,0));
        top.addView(headerLink("▾  "+capitalize(language),this::pickLanguage),size(95,38,7,0));
        accountChip=headerLink(source.isSignedIn()?"●  SIGNED IN":"○  SIGN IN",this::account);
        top.addView(accountChip,size(101,38,0,0));
        return top;
    }
    /** Full-width content and an independently animated, hidden navigation drawer. */
    private void shell(String heading){
        FrameLayout full=new FrameLayout(this);full.setBackgroundColor(BG);setContentView(full);
        drawerShown=false;lastContentFocus=null;
        boolean immersive="home".equals(mode)||"details".equals(mode);
        LinearLayout body=column();full.addView(body,new FrameLayout.LayoutParams(-1,-1));
        if(!immersive){
            body.addView(headerContent(false,heading),size(-1,51,0,0));
            View line=new View(this);line.setBackgroundColor(0xff192331);
            body.addView(line,size(-1,1,0,0));
        }
        accountBanner=null;
        if("account".equals(mode)){
            accountBanner=text(source.isSignedIn()?"●  Signed in — your session is active":
                "○  Not signed in — enter your account details below",14,
                source.isSignedIn()?GOLD:WHITE,true);
            accountBanner.setMaxLines(2);accountBanner.setEllipsize(TextUtils.TruncateAt.END);
            accountBanner.setPadding(dp(28),dp(5),dp(20),dp(5));
            accountBanner.setBackgroundColor(0xff1c2938);
            body.addView(accountBanner,size(-1,45,0,0));
        }
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page=column();page.setClipChildren(false);
        page.setPadding(immersive?0:dp(30),immersive?0:dp(20),
            immersive?0:dp(30),dp(52));scroll.addView(page);
        scroll.setOnScrollChangeListener((v,sx,sy,ox,oy)->{
            if(!browsing||loading||TextUtils.isEmpty(next))return;
            int remaining=page.getHeight()-scroll.getHeight()-sy;
            if(remaining<dp(400)&&SystemClock.elapsedRealtime()-lastAuto>1400){
                lastAuto=SystemClock.elapsedRealtime();loadBatch();
            }
        });
        drawerLayer=new FrameLayout(this);
        drawerLayer.setVisibility(View.GONE);drawerLayer.setFocusable(false);
        full.addView(drawerLayer,new FrameLayout.LayoutParams(-1,-1));
        drawerScrim=new View(this);drawerScrim.setBackgroundColor(0xcc03060b);
        drawerScrim.setOnClickListener(v->closeDrawer());
        drawerLayer.addView(drawerScrim,new FrameLayout.LayoutParams(-1,-1));
        ScrollView navigationScroll=new ScrollView(this);drawerPanel=navigationScroll;
        navigationScroll.setVerticalScrollBarEnabled(false);
        navigationScroll.setBackgroundColor(NAV);
        FrameLayout.LayoutParams drawerPosition=new FrameLayout.LayoutParams(dp(225),-1,Gravity.LEFT);
        drawerLayer.addView(navigationScroll,drawerPosition);
        LinearLayout left=column();left.setPadding(dp(14),dp(26),dp(12),dp(18));
        left.setBackgroundColor(NAV);sidebar=left;navigationScroll.addView(left);
        TextView mark=text("ELIL",23,WHITE,true);mark.setLetterSpacing(.07f);
        mark.setPadding(dp(10),0,0,0);left.addView(mark,size(-1,38,0,0));
        TextView tagline=text("TV   /   BROUGHT BY PIJIN",10,GOLD,true);
        tagline.setPadding(dp(10),0,0,0);left.addView(tagline,size(-1,34,0,22));
        left.addView(navigation("⌂    Home",this::home),size(-1,44,0,3));
        left.addView(navigation("⌕    Search",this::search),size(-1,44,0,3));
        left.addView(navigation("▦    All films",()->browse("Recently Added","")),size(-1,44,0,3));
        left.addView(navigation("▶    Continue Watching",()->localCollection("Continue Watching")),size(-1,44,0,3));
        left.addView(navigation("＋    My List",()->localCollection("My List")),size(-1,44,0,3));
        left.addView(navigation("✦    Popular",()->browse("Popular","")),size(-1,44,0,3));
        left.addView(navigation("★    Featured",()->browse("Featured","")),size(-1,44,0,3));
        left.addView(navigation("⚙    Settings",this::settingsPage),size(-1,44,0,20));
        TextView signed=text((source.isSignedIn()?"●  Signed in":"○  Not signed in")+
            "  ·  "+viewing.activeName(),11,source.isSignedIn()?GOLD:MUTED,false);
        signed.setPadding(dp(13),dp(10),0,0);left.addView(signed,size(-1,42,0,0));
    }
    private void settingsPage(){
        generation++;mode="settings";browsing=false;loading=false;next="";
        shell("Settings");eyebrow(page,"ELIL TV  •  BROUGHT BY PIJIN");
        title(page,"Settings",24);
        TextView expl=text("Membership, household profiles, collections and app updates.",15,MUTED,false);
        page.addView(expl,size(-1,47,0,12));
        LinearLayout one=row();page.addView(one,size(-1,58,0,12));
        one.addView(action("My account",this::account,false),size(163,47,12,0));
        one.addView(action("Switch profile",this::profiles,false),size(173,47,12,0));
        one.addView(action("App updates",()->startActivity(new Intent(this,UpdateActivity.class)),false),size(167,47,0,0));
        LinearLayout two=row();page.addView(two,size(-1,58,0,12));
        two.addView(action("Language",this::pickLanguage,false),size(150,47,12,0));
        two.addView(action("Collections",this::collectionPicker,false),size(169,47,12,0));
        two.addView(action("Recently watched",()->localCollection("Recently Watched"),false),size(207,47,0,0));
        TextView note=text("Navigation: press LEFT past the first film to open the menu. Press RIGHT or BACK to close it.",13,MUTED,false);
        page.addView(note,size(-1,60,0,0));
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
        TextView v=text(value,Math.min(font,22),WHITE,true);dest.addView(v,size(-1,42,0,5));
    }
    /** Poster occupies the right side of the hero, treated as cinematic art, not a card. */
    private ImageView artBackdrop(FrameLayout host,Film film,int height){
        host.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff0b1728,0xff162d3b,0xff101a2b,0xff080c13}));
        ImageView art=new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setAlpha(.92f);
        FrameLayout.LayoutParams artParams=new FrameLayout.LayoutParams(
            dp((int)(screenWidthDp()*.64f)),dp(height),Gravity.TOP|Gravity.RIGHT);
        host.addView(art,artParams);
        if(film!=null)images.load(this,art,film.poster);
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff090e17,0xff090e17,0xf2090e17,0x9a090e17,0x18090e17}));
        host.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View fade=new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x9a060b12,0x00060b12,0x10060b12,BG}));
        host.addView(fade,new FrameLayout.LayoutParams(-1,-1));
        return art;
    }
    private void hero(Film film){
        featured=film;
        FrameLayout canvas=new FrameLayout(this);
        page.addView(canvas,size(-1,295,0,0));
        heroArt=artBackdrop(canvas,film,295);
        // Compact overlay sits on the film itself, with no persistent navigation bar.
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,dp(55),Gravity.TOP);
        canvas.addView(headerContent(true,""),hp);
        // Let the title, two-line description and both action buttons choose
        // their natural height. The old 238dp frame included 66dp top padding
        // and was shorter than its children, clipping Play and More info.
        LinearLayout words=column();
        words.setPadding(dp(37),0,dp(12),0);
        FrameLayout.LayoutParams wp=new FrameLayout.LayoutParams(
            dp((int)Math.min(525,screenWidthDp()*.61f)),-2,
            Gravity.LEFT|Gravity.BOTTOM);
        wp.bottomMargin=dp(20);
        canvas.addView(words,wp);
        TextView eyebrow=text("FEATURED  /  "+capitalize(language).toUpperCase(Locale.ROOT),10,GOLD,true);
        eyebrow.setLetterSpacing(.18f);words.addView(eyebrow,size(-1,25,0,7));
        heroTitle=text(film==null?"CINEMA, AT HOME":film.title,29,WHITE,true);
        heroTitle.setMaxLines(2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(heroTitle,size(-1,70,0,5));
        heroDesc=text(film==null?"Find your next favourite film. New stories, every night.":
            (film.synopsis.isEmpty()?"Discover this film and more on Elil TV.":film.synopsis),
            12,0xffc7ced7,false);
        heroDesc.setMaxLines(2);heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(heroDesc,size(-1,44,0,9));
        LinearLayout buttons=row();words.addView(buttons,size(-1,43,0,0));
        TextView playButton=action("▶   Play",()->{
            if(featured!=null)play(featured,false,false);
            else browse("Recently Added","");
        },true);
        buttons.addView(playButton,size(115,39,8,0));
        buttons.addView(action("More info",()->{
            if(featured!=null)details(featured);
            else browse("Recently Added","");
        },false),size(113,39,0,0));
        playButton.post(()->{if(!isFinishing() && "home".equals(mode) && !drawerShown)
            playButton.requestFocus();});
    }
    /** High-density catalogue row: artwork is the UI; no bright bordered containers. */
    private void catalogueRail(String section,int generationId){
        LinearLayout rowHeader=row();rowHeader.setPadding(dp(29),0,dp(28),0);
        page.addView(rowHeader,size(-1,37,0,2));
        TextView sectionTitle=text(section,16,WHITE,true);
        rowHeader.addView(sectionTitle,new LinearLayout.LayoutParams(0,-1,1));
        TextView seeAll=headerLink("Explore  ›",()->browse(section,""));
        rowHeader.addView(seeAll,size(91,32,0,0));
        HorizontalScrollView rail=new HorizontalScrollView(this);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setClipToPadding(false);rail.setClipChildren(false);
        LinearLayout items=row();items.setGravity(Gravity.TOP);items.setClipChildren(false);
        items.setPadding(dp(29),0,dp(29),0);rail.addView(items);
        linkExploreToRail(seeAll,rail,items);
        page.addView(rail,size(-1,railHeight(),0,7));
        // Show a complete row instead of a single "Loading..." message.
        int width=railCardWidth();
        for(int i=0;i<gridColumns();i++){
            LinearLayout placeholder=column();
            placeholder.setBackground(bg(0xff192230,5));
            items.addView(placeholder,size(width,posterHeight(),9,0));
        }
        io.submit(()->{
            try{
                EinthusanClient.Page results=source.browse(source.listingUrl(language,section,""));
                runOnUiThread(()->{
                    if(isFinishing()||generationId!=generation)return;
                    items.removeAllViews();
                    for(Film film:results.films)items.addView(movieCard(film,railCardWidth()));
                    if(items.getChildCount()==0){
                        TextView retry=headerLink("No films found. Open collection  ›",()->browse(section,""));
                        items.addView(retry,size(286,46,0,0));
                    }
                    if((section.equals("Popular") ||
                        (section.equals("Recently Added") && featured==null)) &&
                        !results.films.isEmpty()){
                        Film first=results.films.get(0);featured=first;
                        heroTitle.setText(first.title);
                        heroDesc.setText(first.synopsis.isEmpty()?"Watch now on Elil TV":first.synopsis);
                        images.load(this,heroArt,first.poster);
                    }
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(isFinishing()||generationId!=generation)return;
                    items.removeAllViews();
                    TextView fail=headerLink("Can't load "+section+"  ·  Browse films  ›",()->browse(section,""));
                    items.addView(fail,size(338,45,0,0));
                });
            }
        });
    }
    private void home(){
        generation++;mode="home";browsing=false;loading=false;next="";
        shell("Home");hero(null);
        int gen=generation;
        // Never lead with an almost empty Continue Watching strip.
        // Immediately show a catalogue row with plenty of artwork.
        catalogueRail("Popular",gen);
        personalRail("Continue Watching",viewing.continueWatching());
        catalogueRail("Recently Added",gen);
        personalRail("My List",viewing.myList());
        catalogueRail("Featured",gen);
        catalogueRail("Staff Picks",gen);
        TextView copyright=text("ELIL TV   ·   BROUGHT BY PIJIN",10,0xff788594,false);
        copyright.setPadding(dp(30),dp(12),0,0);page.addView(copyright,size(-1,47,0,0));
    }
    private List<ViewingStore.Entry> personalEntries(String kind){
        if("Continue Watching".equals(kind))return viewing.continueWatching();
        if("My List".equals(kind))return viewing.myList();
        return viewing.recentlyWatched();
    }
    private void personalRail(String kind,List<ViewingStore.Entry> entries){
        if(entries.isEmpty())return;
        LinearLayout rowHeader=row();rowHeader.setPadding(dp(29),0,dp(28),0);
        page.addView(rowHeader,size(-1,37,0,2));
        TextView name=text(kind,16,WHITE,true);
        rowHeader.addView(name,new LinearLayout.LayoutParams(0,-1,1));
        TextView seeAll=headerLink("Explore  ›",()->localCollection(kind));
        rowHeader.addView(seeAll,size(91,32,0,0));
        HorizontalScrollView rail=new HorizontalScrollView(this);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setClipToPadding(false);rail.setClipChildren(false);
        LinearLayout items=row();items.setGravity(Gravity.TOP);items.setClipChildren(false);
        items.setPadding(dp(29),0,dp(29),0);rail.addView(items);
        linkExploreToRail(seeAll,rail,items);
        page.addView(rail,size(-1,railHeight(),0,7));
        for(int i=0;i<Math.min(entries.size(),20);i++){
            ViewingStore.Entry entry=entries.get(i);
            View filmCard=movieCard(entry.film,railCardWidth());
            if("Continue Watching".equals(kind))filmCard.setOnClickListener(v->play(entry.film,entry.ultra,true));
            items.addView(filmCard);
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
    private float screenWidthDp(){
        return getResources().getDisplayMetrics().widthPixels/
            getResources().getDisplayMetrics().density;
    }
    private int gridColumns(){
        float width=screenWidthDp()-70;
        return width>=880?8:width>=780?7:width>=675?6:width>=550?5:width>=460?4:3;
    }
    private int gridCardWidth(){
        int columns=gridColumns();
        return Math.max(86,(int)((screenWidthDp()-70-columns*9)/columns));
    }
    private int railCardWidth(){return gridCardWidth();}
    private int posterHeight(){return (int)(railCardWidth()*1.32f);}
    private int railHeight(){return posterHeight()+37;}
    private void addToGrid(Film film){
        int cols=gridColumns(),width=gridCardWidth();
        if(gridPosition%cols==0){
            currentGridRow=row();currentGridRow.setGravity(Gravity.TOP);
            currentGridRow.setClipChildren(false);
            grid.addView(currentGridRow,size(-1,railHeight()+8,0,10));
        }
        currentGridRow.addView(movieCard(film,width));
        gridPosition++;
    }
    private View movieCard(Film film){return movieCard(film,railCardWidth());}
    private View movieCard(Film film,int width){
        LinearLayout tile=column();tile.setFocusable(true);tile.setClickable(true);
        tile.setClipChildren(false);tile.setClipToPadding(false);
        tile.setBackgroundColor(Color.TRANSPARENT);
        tile.setPadding(dp(2),dp(2),dp(2),0);
        tile.setLayoutParams(size(width,railHeight(),9,0));
        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(bg(0xff202b3a,5));
        tile.addView(frame,size(-1,posterHeight(),0,4));
        ImageView poster=new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(bg(0xff192432,5));
        frame.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        images.load(this,poster,film.poster);
        ViewingStore.Entry progress=viewing.entry(film);
        if(progress!=null&&progress.started&&progress.durationMs>0){
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);bar.setProgress(progress.percent());
            bar.setProgressTintList(ColorStateList.valueOf(GOLD));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff4b5868));
            frame.addView(bar,new FrameLayout.LayoutParams(-1,dp(3),Gravity.BOTTOM));
        }
        TextView label=text(film.title,11,0xffe9eaf0,false);
        label.setMaxLines(1);label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label,size(-1,24,0,0));
        tile.setOnFocusChangeListener((v,focused)->{
            frame.setBackground(outlined(focused?GOLD:0xff202b3a,5,
                focused?GOLD:0xff202b3a));
            frame.setPadding(focused?dp(2):0,focused?dp(2):0,focused?dp(2):0,focused?dp(2):0);
            label.setTextColor(focused?GOLD:0xffe9eaf0);
            tile.animate().scaleX(focused?1.045f:1f).scaleY(focused?1.045f:1f)
                .setDuration(115).start();
            tile.setElevation(focused?dp(12):0);
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
    /** Immersive full-width details, sharing the same cinematic visual system. */
    private void details(Film film){
        if(!"details".equals(mode))returnMode=mode;
        selected=film;generation++;mode="details";browsing=false;loading=false;
        shell(film.title);
        FrameLayout cinema=new FrameLayout(this);
        page.addView(cinema,size(-1,335,0,12));
        artBackdrop(cinema,film,335);
        cinema.addView(headerContent(true,""),
            new FrameLayout.LayoutParams(-1,dp(55),Gravity.TOP));
        LinearLayout copy=column();copy.setPadding(dp(38),dp(78),dp(14),0);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(
            dp((int)Math.min(590,screenWidthDp()*.68f)),-1,Gravity.LEFT);
        cinema.addView(copy,cp);
        TextView tag=text(capitalize(film.language).toUpperCase(Locale.ROOT)+
            ("uhd".equals(film.quality)?"  ·  ULTRA HD AVAILABLE":"  ·  HD"),11,GOLD,true);
        tag.setLetterSpacing(.12f);copy.addView(tag,size(-1,23,0,4));
        TextView name=text(film.title,30,WHITE,true);
        name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name,size(-1,65,0,6));
        TextView intro=text(film.synopsis.isEmpty()?"Watch on Elil TV.":film.synopsis,
            13,0xffc9d2df,false);
        intro.setMaxLines(3);intro.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(intro,size(-1,53,0,10));
        ViewingStore.Entry entry=viewing.entry(film);
        LinearLayout controls=row();copy.addView(controls,size(-1,44,0,8));
        TextView primary;
        if(entry!=null&&entry.canResume()){
            primary=action("▶   Resume",()->play(film,entry.ultra,true),true);
            controls.addView(primary,size(121,39,10,0));
            controls.addView(action("↻  Restart",()->play(film,false,false),false),size(114,39,10,0));
        }else{
            primary=action("▶   Play",()->play(film,false,false),true);
            controls.addView(primary,size(121,39,10,0));
        }
        boolean saved=viewing.isSaved(film);
        controls.addView(action(saved?"✓  In My List":"＋  My List",()->{
            viewing.setSaved(film,!saved);details(film);
        },false),size(119,39,0,0));
        primary.setFocusableInTouchMode(true);
        primary.post(()->{if(!isFinishing() && "details".equals(mode) && selected==film)
            primary.requestFocus();});
        LinearLayout options=row();options.setPadding(dp(38),0,dp(38),0);
        page.addView(options,size(-1,47,0,14));
        if("uhd".equals(film.quality))
            options.addView(action("✦  Play Ultra HD",()->play(film,true,false),false),size(161,39,12,0));
        if(entry!=null&&entry.canResume()&&!entry.hidden)
            options.addView(action("Remove from Continue Watching",()->{
                viewing.removeFromContinue(film);details(film);
            },false),size(246,39,12,0));
        options.addView(action("Website fallback",()->openWebsite(film.detailUrl),false),size(153,39,12,0));
        TextView note=text("Each household profile has separate viewing history and My List.",
            12,MUTED,false);
        note.setPadding(dp(38),0,0,0);page.addView(note,size(-1,37,0,0));
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
    /** Update both the form message and the pinned banner visible above the scroll. */
    private void setAccountFeedback(TextView local,String message){
        local.setText(message);
        if(accountBanner!=null)accountBanner.setText(message);
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
                setAccountFeedback(accountStatus,"Enter your email and password.");return;
            }
            setAccountFeedback(accountStatus,"◌  Signing in — checking your account…");
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
                        setAccountFeedback(accountStatus,result.verified ?
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
                        setAccountFeedback(accountStatus,"Sign-in couldn't be completed: "+error.getMessage());});
                }
            });
        },true);
        actions.addView(signIn,size(186,48,12,0));
        actions.addView(action("Refresh status",()->{
            setAccountFeedback(accountStatus,"Checking session…");
            io.submit(()->{boolean ok=source.restoreSession(language);
                runOnUiThread(()->{
                    if(!isFinishing()&&"account".equals(mode)){
                        setAccountFeedback(accountStatus,ok?"●  Signed in — your account session is active":"○  No verified session");
                        accountStatus.setTextColor(ok?GOLD:WHITE);
                        accountChip.setText(ok?"●  SIGNED IN":"○  SIGN IN");
                    }
                });
            });
        },false),size(167,48,12,0));
        actions.addView(action("Sign out",()->{
            source.signOut();pendingUltra=null;
            setAccountFeedback(accountStatus,"○  Signed out");accountStatus.setTextColor(WHITE);
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
        if(drawerShown){closeDrawer();return;}
        if("details".equals(mode) && selected!=null){
            if("personal".equals(returnMode))localCollection(localKind);
            else if("home".equals(returnMode))home();
            else browse(category,query);
            return;
        }
        if(!"home".equals(mode)){home();return;}
        openDrawer(getCurrentFocus());
    }
    @Override protected void onDestroy(){generation++;io.shutdownNow();images.shutdown();super.onDestroy();}
}
