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
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Elil TV — Brought by PIJIN. A native remote-first Fire TV experience. */
public class MainActivity extends Activity {
    private static final int BG=0xff07090e, NAV=0xff0c111a, SURFACE=0xff141c28;
    private static final int WHITE=0xfff8f9fb, MUTED=0xffa0afc4, GOLD=0xfff0c47f;
    private static final int BORDER=0xff263246, BLUE=0xff182939;
    private static final String[] LANGUAGES={"Tamil","Hindi","Telugu","Malayalam","Kannada","Bengali","Marathi","Punjabi"};
    private static final String[] MOVIE_TABS={"All Movies","Recently Added","Popular","Featured","Staff Picks","Ultra HD","Collections"};
    private static final String[] SOURCES={"Recently Added","Popular","Featured","Staff Picks"};
    private static final int HERO_INTERVAL_MS=10000;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<Film> heroFilms=new ArrayList<>();
    private int heroIndex=0;
    private long lastRemoteInput=0;
    private LinearLayout heroDots;
    private TextView heroKicker;
    private View heroPlay;
    private final ArrayList<RailState> homeRows=new ArrayList<>();
    private final ArrayList<String> ultraCursors=new ArrayList<>();
    private String qualityFilter="all";
    private boolean catalogueError=false;
    private static final class RailState {
        String section,next;
        HorizontalScrollView rail;
        LinearLayout items;
        int generation;
        boolean loading,personal;
        Set<String> known=new HashSet<>();
    }
    private final Runnable heroRotate=new Runnable(){
        @Override public void run(){
            if("home".equals(mode) && !drawerShown && heroFilms.size()>1 &&
               (heroDots==null || !heroDots.hasFocus())){
                // Don't rotate during interaction, but idle startup focus
                // on the Play button must not freeze the carousel forever.
                if(SystemClock.elapsedRealtime()-lastRemoteInput>6500L)
                    changeHero(1);
            }
            ui.postDelayed(this,HERO_INTERVAL_MS);
        }
    };
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
    private FrameLayout screenRoot;
    private TextView homeMenuItem,settingsMenuItem,drawerAccountStatus;
    // Explicit D-pad focus order: never allow focus to drift into background content.
    private final ArrayList<TextView> drawerDestinations=new ArrayList<>();
    private ScreenSnapshot priorToDetails;
    private static final class ScreenSnapshot {
        FrameLayout root,drawerLayer;
        View drawerScrim,drawerPanel,focused,more;
        LinearLayout page,sidebar,grid;
        ScrollView scroll;
        TextView status,accountChip,accountBanner,heroTitle,heroDesc;
        ImageView heroArt;
        TextView homeMenuItem,settingsMenuItem,drawerAccountStatus;
        ArrayList<TextView> drawerDestinations;
        int scrollY,generation;
        boolean browsing,loading;
        String mode;
    }
    private static final class PosterProgress {
        final Film film;
        final ProgressBar bar;
        PosterProgress(Film film, ProgressBar bar){this.film=film;this.bar=bar;}
    }

    private boolean drawerShown=false;
    private Film featured;
    private ScrollView scroll;
    private TextView status,accountChip,accountBanner;
    private ImageView heroArt;
    private TextView heroTitle,heroDesc;
    private View more;
    private String language="tamil",category="Recently Added",query="",next="";
    private boolean browsing=false,loading=false;
    private int generation=0,gridPosition=0,initialGridFillRounds=0;
    private long lastAuto=0;
    private Film selected;
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
                    if(drawerAccountStatus!=null)drawerAccountStatus.setText(verified?"●  Signed in":"○  Not signed in");
                    if(accountBanner!=null && "account".equals(mode))
                        accountBanner.setText(verified?
                            "●  Signed in — your account session is active":
                            "○  Not signed in — enter your account details below");
                }
            });
        });
        getWindow().getDecorView().setSystemUiVisibility(5894);
        ui.postDelayed(heroRotate,HERO_INTERVAL_MS);
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
            // Do not rebuild the catalogue. Its poster focus and scroll position
            // must remain exactly where the viewer started playback.
            refreshPosterProgress(page);
            if(priorToDetails!=null)refreshPosterProgress(priorToDetails.page);
            if("details".equals(mode)&&selected!=null)details(selected);
        }
    }
    /** Keep the actual browse view tree alive while a film's details are open.
     * Rebuilding the catalogue on Back caused loss of poster focus, scroll
     * position and even loaded films. A detached view tree is cheap to keep
     * for the lifetime of one details screen and avoids the entire problem.
     */
    private void saveBrowseScreen(){
        if(screenRoot==null)return;
        ScreenSnapshot p=new ScreenSnapshot();
        p.root=screenRoot;p.page=page;p.grid=grid;p.scroll=scroll;
        p.drawerLayer=drawerLayer;p.drawerPanel=drawerPanel;
        p.drawerScrim=drawerScrim;p.sidebar=sidebar;
        p.homeMenuItem=homeMenuItem;p.settingsMenuItem=settingsMenuItem;
        p.drawerAccountStatus=drawerAccountStatus;
        p.drawerDestinations=new ArrayList<>(drawerDestinations);
        p.focused=getCurrentFocus();p.more=more;
        p.status=status;p.accountChip=accountChip;p.accountBanner=accountBanner;
        p.heroTitle=heroTitle;p.heroDesc=heroDesc;p.heroArt=heroArt;
        p.scrollY=scroll!=null?scroll.getScrollY():0;
        p.generation=generation;p.browsing=browsing;p.loading=loading;p.mode=mode;
        priorToDetails=p;
    }
    private boolean restoreBrowseScreen(){
        ScreenSnapshot p=priorToDetails;
        if(p==null || p.root==null)return false;
        priorToDetails=null;
        mode=p.mode;generation=p.generation;browsing=p.browsing;
        // An in-flight browse request may have finished while details was open.
        // Reset loading so a saved collection cannot get stuck after Back.
        loading=false;
        screenRoot=p.root;page=p.page;grid=p.grid;scroll=p.scroll;
        drawerLayer=p.drawerLayer;drawerScrim=p.drawerScrim;
        drawerPanel=p.drawerPanel;sidebar=p.sidebar;
        homeMenuItem=p.homeMenuItem;settingsMenuItem=p.settingsMenuItem;
        drawerAccountStatus=p.drawerAccountStatus;
        drawerDestinations.clear();
        if(p.drawerDestinations!=null)drawerDestinations.addAll(p.drawerDestinations);
        more=p.more;status=p.status;accountChip=p.accountChip;accountBanner=p.accountBanner;
        heroTitle=p.heroTitle;heroDesc=p.heroDesc;heroArt=p.heroArt;
        drawerShown=false;lastContentFocus=null;
        selected=null;
        setContentView(p.root);
        if(accountChip!=null)accountChip.setText(source.isSignedIn()?"●  SIGNED IN":"○  SIGN IN");
        if(drawerAccountStatus!=null)drawerAccountStatus.setText(source.isSignedIn()?"●  Signed in":"○  Not signed in");
        p.root.setAlpha(1f);
        if(p.scroll!=null)p.scroll.post(()->{
            if(isFinishing())return;
            if(p.focused!=null && p.focused.isAttachedToWindow() && p.focused.isShown())
                p.focused.requestFocus();
            p.scroll.scrollTo(0,p.scrollY);
        });
        return true;
    }
    private void refreshPosterProgress(View node){
        if(node==null)return;
        Object data=node.getTag();
        if(data instanceof PosterProgress){
            PosterProgress pp=(PosterProgress)data;
            ViewingStore.Entry entry=viewing.entry(pp.film);
            boolean seen=entry!=null&&entry.started&&entry.durationMs>0;
            pp.bar.setVisibility(seen?View.VISIBLE:View.GONE);
            if(seen)pp.bar.setProgress(entry.percent());
        }
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++)
                refreshPosterProgress(group.getChildAt(i));
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
        // A row edge does not unexpectedly jump to unrelated header controls.
        if(direction==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
        // Moving left beyond the first poster opens the hidden navigation.
        return focusSidebarFromContent(current);
    }
    /** Content occupies the television. The drawer opens only from browsing pages. */
    private boolean focusSidebarFromContent(View current){
        if(!"details".equals(mode))openDrawer(current);
        return true;
    }
    private int selectedDrawerIndex(){
        if("browse".equals(mode))return 1;
        if("personal".equals(mode)&&"My List".equals(localKind))return 2;
        if("search".equals(mode))return 3;
        if("settings".equals(mode))return 4;
        return 0;
    }
    private int focusedDrawerIndex(){
        View current=getCurrentFocus();
        int index=drawerDestinations.indexOf(current);
        return index>=0?index:selectedDrawerIndex();
    }
    private void focusDrawerItem(int index){
        if(!drawerShown || drawerDestinations.isEmpty())return;
        int safe=Math.max(0,Math.min(index,drawerDestinations.size()-1));
        TextView item=drawerDestinations.get(safe);
        // Menus stay inside the panel even if the background has focusable views.
        if(item.isAttachedToWindow() && item.isShown())item.requestFocus();
    }
    private void openDrawer(View from){
        if(drawerLayer==null || drawerShown)return;
        View previous=(from!=null && from.isFocusable())?from:getCurrentFocus();
        lastContentFocus=previous!=null && previous.isFocusable()?previous:null;
        drawerShown=true;
        drawerLayer.setVisibility(View.VISIBLE);
        drawerLayer.bringToFront();
        if(drawerScrim!=null){
            drawerScrim.animate().cancel();
            drawerScrim.setAlpha(0f);
            drawerScrim.animate().alpha(1f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
        }
        if(drawerPanel!=null){
            drawerPanel.animate().cancel();
            drawerPanel.setTranslationX(-dp(204));
            drawerPanel.animate().translationX(0f).setDuration(235)
                .setInterpolator(new DecelerateInterpolator()).start();
        }
        if(sidebar!=null)sidebar.post(()->focusDrawerItem(selectedDrawerIndex()));
    }
    private void restoreFocusAfterDrawer(View previous){
        if(drawerShown)return;
        if(previous!=null && previous.isAttachedToWindow() && previous.isShown()
                && previous.isEnabled() && previous.isFocusable() && previous.requestFocus())return;
        // Most recent selectable film/hero action is safer than the header's
        // sign-in chip. Never restore to an icon inside the navigation rail.
        if("home".equals(mode) && heroPlay!=null && heroPlay.isAttachedToWindow()
                && heroPlay.isShown() && heroPlay.requestFocus())return;
        if(page!=null){
            for(View candidate:page.getFocusables(View.FOCUS_FORWARD)){
                if(candidate.isShown() && candidate.isEnabled() && candidate.requestFocus())break;
            }
        }
    }
    private void closeDrawer(){
        if(!drawerShown)return;
        drawerShown=false;
        final View previous=lastContentFocus;
        if(drawerScrim!=null){
            drawerScrim.animate().cancel();
            drawerScrim.animate().alpha(0f).setDuration(185).start();
        }
        if(drawerPanel!=null){
            drawerPanel.animate().cancel();
            drawerPanel.animate().translationX(-dp(204)).setDuration(195)
                .withEndAction(()->{
                    if(!drawerShown){
                        if(drawerLayer!=null)drawerLayer.setVisibility(View.GONE);
                        restoreFocusAfterDrawer(previous);
                    }
                }).start();
        }else{
            if(drawerLayer!=null)drawerLayer.setVisibility(View.GONE);
            restoreFocusAfterDrawer(previous);
        }
    }
    // Sidebar directions are navigation only. OK/Enter opens a destination.
    // The current film/poster is restored when Right closes the sidebar.
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            lastRemoteInput=SystemClock.elapsedRealtime();
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_MENU){
                if(drawerShown)closeDrawer();
                else if(!"details".equals(mode))openDrawer(getCurrentFocus());
                return true;
            }
            if(drawerShown){
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                    if(event.getRepeatCount()==0)focusDrawerItem(focusedDrawerIndex()+1);
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_UP){
                    if(event.getRepeatCount()==0)focusDrawerItem(focusedDrawerIndex()-1);
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){
                    if(event.getRepeatCount()==0)closeDrawer();
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)return true;
                if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) &&
                   !drawerDestinations.isEmpty()){
                    if(event.getRepeatCount()==0)
                        drawerDestinations.get(focusedDrawerIndex()).performClick();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
    /** Restrained television actions: flat surfaces, no blue outlined form buttons. */
    private TextView action(String t,Runnable run,boolean primary){
        TextView b=text(t,13,primary?BG:WHITE,true);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(4),dp(12),dp(4));
        b.setFocusable(true);b.setClickable(true);
        b.setBackground(outlined(primary?GOLD:0x30151d29,7,primary?GOLD:0x18334254));
        b.setTextColor(primary?BG:WHITE);
        b.setOnFocusChangeListener((v,focus)->{
            b.animate().cancel();
            b.setBackground(outlined(focus?0xffffd796:(primary?GOLD:0x30151d29),7,focus?GOLD:(primary?GOLD:0x18334254)));
            b.setTextColor(focus?BG:(primary?BG:WHITE));
            v.animate().scaleX(focus?1.025f:1f).scaleY(focus?1.025f:1f)
                .translationY(focus?-dp(2):0).setDuration(185)
                .setInterpolator(new DecelerateInterpolator()).start();
        });
        b.setOnKeyListener((view,key,event)->event.getAction()==KeyEvent.ACTION_DOWN &&
            focusWithinRow(view,key));
        b.setOnClickListener(v->run.run());return b;
    }
    private TextView navigation(String label,Runnable click){
        TextView v=text(label,14,0xffbcc7d3,false);
        v.setPadding(dp(12),0,dp(8),0);v.setFocusable(true);v.setClickable(true);
        v.setBackground(bg(Color.TRANSPARENT,12));
        v.setOnFocusChangeListener((view,focus)->{
            v.animate().cancel();
            v.setBackground(bg(focus?0x332e343b:Color.TRANSPARENT,7));
            v.setTextColor(focus?GOLD:0xffbcc7d3);
            v.animate().translationX(focus?dp(3):0).setDuration(175)
                .setInterpolator(new DecelerateInterpolator()).start();
        });
        v.setOnClickListener(view->click.run());return v;
    }

    /** Text-only header links disappear into the background until focused. */
    private TextView headerLink(String label,Runnable click){
        TextView v=text(label,12,0xffe2e6ea,false);
        v.setGravity(Gravity.CENTER);v.setPadding(dp(7),0,dp(7),0);
        v.setFocusable(true);v.setClickable(true);v.setBackground(bg(0x00000000,3));
        v.setOnFocusChangeListener((item,focused)->{
            v.setBackground(bg(focused?GOLD:0x00000000,11));
            v.setTextColor(focused?BG:0xffe2e6ea);
            v.animate().cancel();
            v.animate().scaleX(focused?1.045f:1f).scaleY(focused?1.045f:1f)
                .setDuration(155).setInterpolator(new DecelerateInterpolator()).start();
        });
        v.setOnClickListener(item->click.run());return v;
    }
    private LinearLayout headerContent(boolean onArt,String heading){
        LinearLayout top=row();
        top.setPadding(dp(66),0,dp(26),0);
        ImageView brand=new ImageView(this);
        brand.setImageResource(R.drawable.ezhil_header);
        brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.setFocusable(true);brand.setClickable(true);
        brand.setContentDescription("எழில் TV — open navigation");
        brand.setOnClickListener(v->{
            if(!"details".equals(mode))openDrawer(v);
        });
        brand.setOnKeyListener((v,k,e)->e.getAction()==KeyEvent.ACTION_DOWN &&
            k==KeyEvent.KEYCODE_DPAD_LEFT && focusSidebarFromContent(v));
        top.addView(brand,size(128,49,18,0));
        // The navigation drawer owns destinations; do not duplicate its links
        // across the cinematic feature artwork.

        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        TextView profile=headerLink("◉  "+viewing.activeName(),this::profiles);
        profile.setSingleLine(true);profile.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(profile,size(120,37,4,0));
        accountChip=headerLink(source.isSignedIn()?"●  SIGNED IN":"○  SIGN IN",this::account);
        top.addView(accountChip,size(109,37,0,0));
        return top;
    }
    /** Full-width content and an independently animated, hidden navigation drawer. */
    private void shell(String heading){
        FrameLayout full=new FrameLayout(this);full.setBackgroundColor(BG);
        screenRoot=full;
        setContentView(full);
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
        page.setPadding(immersive?0:dp(58),immersive?0:dp(12),
            immersive?0:dp(30),dp(52));scroll.addView(page);
        // A restrained ease-in replaces the sudden cut between TV pages.
        page.setAlpha(0f);page.setTranslationY(dp(10));
        final LinearLayout enterPage=page;
        enterPage.post(()->enterPage.animate().alpha(1f).translationY(0f)
            .setDuration(260).setInterpolator(new DecelerateInterpolator()).start());
        scroll.setOnScrollChangeListener((v,sx,sy,ox,oy)->{
            if(!browsing||loading||TextUtils.isEmpty(next))return;
            int remaining=page.getHeight()-scroll.getHeight()-sy;
            if(remaining<dp(400)&&SystemClock.elapsedRealtime()-lastAuto>1400){
                lastAuto=SystemClock.elapsedRealtime();loadBatch();
            }
        });
        // Hide the small icon rail entirely on film details: it otherwise
        // overlaps the cinematic artwork and can cover text or controls.
        // On browsing screens it is visual only; Left from a focused poster,
        // header or hero action opens the actual drawer.
        if(!"details".equals(mode)){
        LinearLayout compact=column();
        compact.setBackgroundColor(0xe60a101a);
        compact.setPadding(dp(5),dp(18),dp(5),dp(10));
        FrameLayout.LayoutParams compactLp=new FrameLayout.LayoutParams(dp(45),-1,Gravity.LEFT);
        full.addView(compact,compactLp);
        TextView miniBrand=text("E",19,GOLD,true);miniBrand.setGravity(Gravity.CENTER);
        compact.addView(miniBrand,size(-1,45,0,18));
        String[] miniIcons={"⌂","▦","＋","⌕","⚙"};
        for(String icon:miniIcons){
            TextView mini=text(icon,21,0xffb7c1cf,false);
            mini.setGravity(Gravity.CENTER);mini.setFocusable(false);
            mini.setBackground(bg(Color.TRANSPARENT,7));
            mini.setOnClickListener(v->openDrawer(getCurrentFocus()));
            compact.addView(mini,size(-1,49,0,8));
        }
        }
        drawerLayer=new FrameLayout(this);
        drawerLayer.setVisibility(View.GONE);drawerLayer.setFocusable(false);
        full.addView(drawerLayer,new FrameLayout.LayoutParams(-1,-1));
        drawerScrim=new View(this);drawerScrim.setBackgroundColor(0x9c03070e);
        drawerScrim.setOnClickListener(v->closeDrawer());
        drawerLayer.addView(drawerScrim,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout navPanel=column();drawerPanel=navPanel;
        navPanel.setBackgroundColor(0xff080f19);
        navPanel.setElevation(dp(12));
        FrameLayout.LayoutParams drawerPosition=new FrameLayout.LayoutParams(
            dp(204),-1,Gravity.LEFT);
        drawerLayer.addView(navPanel,drawerPosition);

        // Header, scrolling-free navigation and account footer are separate
        // layout regions. The footer can never be pushed off a short TV screen.
        LinearLayout left=column();sidebar=left;
        left.setPadding(dp(11),0,dp(11),0);
        navPanel.addView(left,new LinearLayout.LayoutParams(-1,0,1));
        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.ezhil_header);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setContentDescription("எழில் TV");
        left.addView(mark,size(-1,55,0,0));
        TextView tagline=text("BROUGHT BY PIJIN",10,GOLD,true);
        tagline.setPadding(dp(13),0,0,0);
        left.addView(tagline,size(-1,25,0,19));
        drawerDestinations.clear();
        homeMenuItem=navigation("⌂    Home",this::home);
        drawerDestinations.add(homeMenuItem);
        left.addView(homeMenuItem,size(-1,45,0,5));
        TextView moviesItem=navigation("▦    Movies",()->browse("All Movies",""));
        drawerDestinations.add(moviesItem);
        left.addView(moviesItem,size(-1,45,0,5));
        TextView listItem=navigation("＋    My List",()->localCollection("My List"));
        drawerDestinations.add(listItem);
        left.addView(listItem,size(-1,45,0,5));
        TextView searchItem=navigation("⌕    Search",this::search);
        drawerDestinations.add(searchItem);
        left.addView(searchItem,size(-1,45,0,7));
        View divider=new View(this);divider.setBackgroundColor(0xff253245);
        left.addView(divider,size(-1,1,0,10));
        settingsMenuItem=navigation("⚙    Settings",this::settingsPage);
        drawerDestinations.add(settingsMenuItem);
        left.addView(settingsMenuItem,size(-1,45,0,0));

        View footLine=new View(this);footLine.setBackgroundColor(0xff253245);
        LinearLayout.LayoutParams lp=size(-1,1,0,0);
        lp.leftMargin=dp(18);lp.rightMargin=dp(18);
        navPanel.addView(footLine,lp);
        LinearLayout footer=row();footer.setPadding(dp(21),dp(6),dp(10),dp(8));
        navPanel.addView(footer,size(-1,58,0,0));
        TextView avatar=text("◉",24,GOLD,true);avatar.setGravity(Gravity.CENTER);
        footer.addView(avatar,size(33,40,8,0));
        LinearLayout identity=column();footer.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=text(viewing.activeName(),13,WHITE,true);
        name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(name,size(-1,20,0,0));
        drawerAccountStatus=text(source.isSignedIn()?"●  Signed in":"○  Not signed in",11,
            source.isSignedIn()?GOLD:MUTED,false);
        identity.addView(drawerAccountStatus,size(-1,17,0,0));
        footer.setFocusable(false);
    }
    private void chooseDefaultQuality(){
        String existing=getSharedPreferences("elil_playback",MODE_PRIVATE)
            .getString("preferred_quality","auto");
        final String[] options={"Auto (best available)","Standard","Ultra HD (when available)"};
        int selected="standard".equals(existing)?1:"ultra".equals(existing)?2:0;
        new AlertDialog.Builder(this).setTitle("Default playback quality")
            .setSingleChoiceItems(options,selected,(d,i)->{
                String value=new String[]{"auto","standard","ultra"}[i];
                getSharedPreferences("elil_playback",MODE_PRIVATE)
                    .edit().putString("preferred_quality",value).apply();
                d.dismiss();
                Toast.makeText(this,"Playback preference saved",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel",null).show();
    }
    /** Disney+-style flat settings list; all destinations retain their working logic. */
    private View settingsItem(LinearLayout list,String icon,String title,String description,Runnable click){
        LinearLayout line=row();line.setPadding(dp(16),dp(4),dp(15),dp(4));
        line.setBackground(bg(Color.TRANSPARENT,8));
        line.setFocusable(true);line.setClickable(true);
        TextView glyph=text(icon,21,GOLD,false);glyph.setGravity(Gravity.CENTER);
        line.addView(glyph,size(46,52,12,0));
        LinearLayout labels=column();line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(text(title,17,WHITE,true),size(-1,28,0,0));
        TextView secondary=text(description,12,MUTED,false);
        secondary.setSingleLine(true);secondary.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(secondary,size(-1,21,0,0));
        TextView arrow=text("›",25,0xff77889b,false);arrow.setGravity(Gravity.CENTER);
        line.addView(arrow,size(27,44,0,0));
        line.setOnFocusChangeListener((v,focused)->{
            line.setBackground(bg(focused?0xff1b2532:Color.TRANSPARENT,8));
            line.animate().translationX(focused?dp(5):0).setDuration(170).start();
            arrow.setTextColor(focused?GOLD:0xff77889b);
        });
        line.setOnClickListener(v->click.run());
        line.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT)return focusSidebarFromContent(v);
            if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){
                int index=list.indexOfChild(v); // even indices are rows; odd indices are dividers
                int next=Math.max(0,Math.min(index+(key==KeyEvent.KEYCODE_DPAD_DOWN?2:-2),
                    Math.max(0,list.getChildCount()-2)));
                View target=list.getChildAt(next);
                if(target!=null && target.isFocusable())target.requestFocus();
                return true;
            }
            return false;
        });
        list.addView(line,size(-1,68,0,0));
        View divider=new View(this);divider.setBackgroundColor(0xff27313d);
        list.addView(divider,size(-1,1,0,0));
        return line;
    }
    private void settingsPage(){
        generation++;mode="settings";browsing=false;loading=false;next="";
        shell("Settings");
        eyebrow(page,"எழில் TV  •  BROUGHT BY PIJIN");
        title(page,"Settings",26);
        TextView subtitle=text("Your cinema, your preferences.",13,MUTED,false);
        page.addView(subtitle,size(-1,30,0,14));
        LinearLayout list=column();
        list.setPadding(dp(6),0,dp(6),0);
        page.addView(list,size(-1,-2,0,20));
        final View firstSetting=settingsItem(list,"◉","Account & Membership",source.isSignedIn()?"Signed in · Manage your membership":"Sign in to your Einthusan account",this::account);
        settingsItem(list,"♙","Household Profiles","Switch profiles or change a profile name",this::profiles);
        settingsItem(list,"◎","Language Preferences","Currently browsing "+capitalize(language),this::pickLanguage);
        settingsItem(list,"▶","Playback Quality","Auto, Standard or Ultra HD where available",this::chooseDefaultQuality);
        settingsItem(list,"↺","Recently Watched","Your viewing history",()->localCollection("Recently Watched"));
        settingsItem(list,"⬆","App Updates","Check for the latest signed version",()->startActivity(new Intent(this,UpdateActivity.class)));
        settingsItem(list,"ⓘ","About எழில் TV","Version "+BuildConfig.VERSION_NAME+" · Brought by PIJIN",()->
            new AlertDialog.Builder(this).setTitle("எழில் TV")
                .setMessage("எழில் TV "+BuildConfig.VERSION_NAME+"\nBrought by PIJIN\n\nDesigned for your Fire TV cinema experience.")
                .setPositiveButton("Close",null).show());
        // Don't let Android select the profile/sign-in link at the top of Settings.
        // Focus the first real Settings action after the new screen is laid out.
        final LinearLayout thisPage=page;
        firstSetting.post(()->{
            if(!isFinishing() && "settings".equals(mode) && page==thisPage){
                firstSetting.requestFocus();
                scroll.smoothScrollTo(0,0);
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
            dp((int)(screenWidthDp()*.50f)),dp(height),Gravity.TOP|Gravity.RIGHT);
        host.addView(art,artParams);
        if(film!=null)images.load(this,art,film.poster);
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff080e17,0xff080e17,0xf2080e17,0x99080e17,0x16080e17}));
        host.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View fade=new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x9a060b12,0x00060b12,0x10060b12,BG}));
        host.addView(fade,new FrameLayout.LayoutParams(-1,-1));
        return art;
    }
    private void changeHero(int delta){
        if(!"home".equals(mode)||heroFilms.isEmpty())return;
        heroIndex=(heroIndex+delta+heroFilms.size())%heroFilms.size();
        Film film=heroFilms.get(heroIndex);
        featured=film;
        if(heroTitle!=null)heroTitle.setText(film.title);
        if(heroDesc!=null)heroDesc.setText(film.synopsis.isEmpty()?"Discover this film on Elil TV.":film.synopsis);
        if(heroKicker!=null)heroKicker.setText("FEATURED  ·  "+capitalize(film.language).toUpperCase(Locale.ROOT));
        if(heroArt!=null){
            heroArt.animate().cancel();
            heroArt.setAlpha(.92f);
            // PosterLoader owns the asynchronous fade and discards stale loads.
            images.load(this,heroArt,film.poster);
        }
        if(heroDots!=null){
            heroDots.setContentDescription("Featured film "+(heroIndex+1)+" of "+heroFilms.size()+
                ": "+film.title+". Press left or right to change films.");
            heroDots.removeAllViews();
            int show=Math.min(heroFilms.size(),8);
            for(int i=0;i<show;i++){
                TextView dot=text(i==heroIndex?"●":"•",i==heroIndex?17:15,
                    i==heroIndex?GOLD:0xff8895a4,false);
                dot.setGravity(Gravity.CENTER);
                heroDots.addView(dot,size(21,24,0,0));
            }
        }
    }
    private void populateFeatured(List<Film> candidates,int gen){
        if(gen!=generation||!"home".equals(mode)||candidates.isEmpty())return;
        heroFilms.clear();
        Set<String> seen=new HashSet<>();
        for(Film film:candidates){
            if(seen.add(key(film))){heroFilms.add(film);if(heroFilms.size()>=8)break;}
        }
        heroIndex=heroFilms.size()>1?(int)(System.currentTimeMillis()/HERO_INTERVAL_MS%heroFilms.size()):0;
        changeHero(0);
    }
    /** The dot indicator is ONE stable D-pad target: updating dots never steals focus.
     * Outside of that target, Left means "open navigation", not "previous film".
     */
    private void hero(Film film){
        featured=film;
        FrameLayout canvas=new FrameLayout(this);
        page.addView(canvas,size(-1,278,0,5));
        heroArt=artBackdrop(canvas,film,278);
        canvas.addView(headerContent(true,""),new FrameLayout.LayoutParams(-1,dp(54),Gravity.TOP));
        LinearLayout words=column();words.setPadding(dp(64),0,dp(10),0);
        FrameLayout.LayoutParams wp=new FrameLayout.LayoutParams(
            dp((int)Math.min(570,screenWidthDp()*.65f)),-2,Gravity.LEFT|Gravity.BOTTOM);
        wp.bottomMargin=dp(14);canvas.addView(words,wp);
        heroKicker=text("FEATURED  ·  "+capitalize(language).toUpperCase(Locale.ROOT),10,GOLD,true);
        heroKicker.setLetterSpacing(.16f);words.addView(heroKicker,size(-1,20,0,1));
        heroTitle=text("Your next favourite film",27,WHITE,true);
        heroTitle.setMaxLines(2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(heroTitle,size(-1,59,0,3));
        heroDesc=text("Discover the latest featured films.",12,0xffc7ced7,false);
        heroDesc.setMaxLines(2);heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(heroDesc,size(-1,36,0,5));
        LinearLayout controls=row();words.addView(controls,size(-1,41,0,2));
        TextView playButton=action("▶  Play",()->{
            if(featured!=null)play(featured,false,false);
        },true);
        TextView moreInfo=action("More info",()->{
            if(featured!=null)details(featured);
        },false);
        heroPlay=playButton;
        controls.addView(playButton,size(107,39,8,0));
        controls.addView(moreInfo,size(112,39,0,0));

        heroDots=row();
        heroDots.setGravity(Gravity.CENTER_VERTICAL);
        heroDots.setPadding(dp(3),0,0,0);
        heroDots.setFocusable(true);
        heroDots.setClickable(true);
        heroDots.setBackground(bg(Color.TRANSPARENT,7));
        heroDots.setOnFocusChangeListener((v,focused)->
            heroDots.setBackground(bg(focused?0x38273441:Color.TRANSPARENT,7)));
        words.addView(heroDots,size(174,25,0,0));
        heroDots.setOnClickListener(v->changeHero(1));
        heroDots.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){
                if(event.getRepeatCount()==0)changeHero(-1);
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(event.getRepeatCount()==0)changeHero(1);
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_UP)return playButton.requestFocus();
            if(key==KeyEvent.KEYCODE_DPAD_DOWN)return moveToRow(0,0);
            return false;
        });
        for(View focus:new View[]{playButton,moreInfo}){
            focus.setOnKeyListener((v,key,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN)return heroDots.requestFocus();
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)return focusSidebarFromContent(v);
                // Never leave the hero and jump to account status when at the edge.
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT && v==moreInfo)return true;
                return false;
            });
        }
        playButton.post(()->{
            if(!isFinishing()&&"home".equals(mode)&&!drawerShown)playButton.requestFocus();
        });
        final int gen=generation;
        io.submit(()->{
            try{
                EinthusanClient.Page result=source.browse(source.listingUrl(language,"Featured",""));
                runOnUiThread(()->populateFeatured(result.films,gen));
            }catch(Exception ignored){
                // Recently Added will supply a fallback if Featured is unavailable.
            }
        });
    }

    /** Fill the visible shelf immediately, without requiring the user to
     * navigate to movie six before the next six are fetched.
     * Manual edge navigation still loads subsequent pages when needed.
     */
    private void loadRail(RailState state,boolean focusFirstNew){
        if(state.loading||TextUtils.isEmpty(state.next))return;
        state.loading=true;
        String url=state.next;
        // First paint should contain at least a full row plus a few offscreen
        // films so there is no exposed empty region on wide Fire TV screens.
        final int desired=focusFirstNew ? 1 : Math.max(10,gridColumns()+2);
        final int present=state.items.getChildCount();
        final Set<String> alreadyKnown=new HashSet<>(state.known);
        io.submit(()->{
            final ArrayList<Film> incoming=new ArrayList<>();
            final Set<String> seenMovies=new HashSet<>(alreadyKnown);
            final Set<String> seenUrls=new HashSet<>();
            String cursor=url;
            ArrayList<Film> featuredFallback=null;
            String failure="";
            int pages=0;
            while(!TextUtils.isEmpty(cursor) && pages<5 &&
                  (focusFirstNew || present+incoming.size()<desired)){
                if(!seenUrls.add(cursor)){
                    cursor="";
                    break;
                }
                try{
                    EinthusanClient.Page result=source.browse(cursor);
                    if(featuredFallback==null && !result.films.isEmpty())
                        featuredFallback=new ArrayList<>(result.films);
                    for(Film film:result.films){
                        if(seenMovies.add(key(film)))incoming.add(film);
                    }
                    cursor=result.next;
                    pages++;
                    if(focusFirstNew && !incoming.isEmpty())break;
                }catch(Exception error){
                    failure=error.getMessage();
                    break;
                }
            }
            final String following=cursor;
            final String err=failure;
            final ArrayList<Film> fallback=featuredFallback;
            runOnUiThread(()->{
                if(isFinishing()||!"home".equals(mode)||state.generation!=generation)return;
                int original=state.items.getChildCount(),added=0;
                for(Film film:incoming){
                    if(state.known.add(key(film))){
                        state.items.addView(railMovieCard(state,film));
                        added++;
                    }
                }
                state.next=following;
                state.loading=false;
                if(focusFirstNew && added>0){
                    View candidate=state.items.getChildAt(original);
                    candidate.post(candidate::requestFocus);
                }
                if(added==0 && focusFirstNew && !TextUtils.isEmpty(state.next) && err.isEmpty())
                    loadRail(state,true);
                if(added==0 && original==0 && TextUtils.isEmpty(state.next)){
                    TextView empty=text(err.isEmpty()?"No films in this collection.":"Could not load films. Retry",12,MUTED,false);
                    if(!err.isEmpty()){
                        empty.setFocusable(true);
                        empty.setOnClickListener(v->{state.items.removeAllViews();state.next=url;loadRail(state,false);});
                    }
                    state.items.addView(empty,size(224,55,0,0));
                }
                if("Recently Added".equals(state.section)&&heroFilms.isEmpty() &&
                   fallback!=null && !fallback.isEmpty())populateFeatured(fallback,state.generation);
            });
        });
    }
    private boolean moveToRow(int target,int column){return moveToRow(target,column,1);}
    private boolean moveToRow(int target,int column,int direction){
        if(target<0 || target>=homeRows.size())return true;
        for(int rowIndex=target;rowIndex>=0 && rowIndex<homeRows.size();rowIndex+=direction){
            RailState state=homeRows.get(rowIndex);
            int count=state.items.getChildCount();
            if(count==0)continue;
            int index=Math.max(0,Math.min(column,count-1));
            View child=state.items.getChildAt(index);
            if(child.isFocusable()&&child.isShown()){
                child.requestFocus();return true;
            }
        }
        // If a row is still loading, don't jump to unrelated header links.
        return true;
    }
    private View railMovieCard(RailState state,Film film){
        View card=movieCard(film,railCardWidth());
        card.setOnKeyListener((view,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            int index=state.items.indexOfChild(view);
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(index<state.items.getChildCount()-1){
                    state.items.getChildAt(index+1).requestFocus();
                }else if(!TextUtils.isEmpty(state.next))loadRail(state,true);
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){
                if(index>0)state.items.getChildAt(index-1).requestFocus();
                else openDrawer(view);
                return true;
            }
            int rowIndex=homeRows.indexOf(state);
            if(key==KeyEvent.KEYCODE_DPAD_UP){
                if(rowIndex>0)return moveToRow(rowIndex-1,index,-1);
                if(heroDots!=null)return heroDots.requestFocus();
                if(heroPlay!=null)return heroPlay.requestFocus();
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                return moveToRow(rowIndex+1,index,1);
            }
            return false;
        });
        return card;
    }
    private RailState createRail(String section,boolean personal){
        final int gen=generation;
        RailState state=new RailState();state.section=section;state.generation=gen;state.personal=personal;
        LinearLayout header=row();header.setPadding(dp(62),0,dp(22),0);
        page.addView(header,size(-1,36,0,3));
        Runnable viewAll=personal?()->localCollection(section):()->browse(section,"");
        TextView heading=headerLink(section+"  ›",viewAll);
        heading.setTextSize(16);heading.setTypeface(null,Typeface.BOLD);
        heading.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        header.addView(heading,new LinearLayout.LayoutParams(-1,dp(35)));
        state.rail=new HorizontalScrollView(this);
        state.rail.setHorizontalScrollBarEnabled(false);
        state.rail.setClipToPadding(false);state.rail.setClipChildren(false);
        state.items=row();state.items.setGravity(Gravity.TOP);
        state.items.setClipChildren(false);
        state.items.setPadding(dp(62),0,dp(26),0);
        state.rail.addView(state.items);
        page.addView(state.rail,size(-1,railHeight()+5,0,7));
        homeRows.add(state);
        heading.setOnKeyListener((v,key,event)->{
            if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN)
                return moveToRow(homeRows.indexOf(state),0);
            return false;
        });
        return state;
    }
    private void catalogueRail(String section){
        RailState state=createRail(section,false);
        state.next=source.listingUrl(language,section,"");
        loadRail(state,false);
    }
    private void personalRail(String kind,List<ViewingStore.Entry> entries){
        if(entries.isEmpty())return;
        RailState state=createRail(kind,true);
        state.next="";
        for(ViewingStore.Entry entry:entries){
            View card=railMovieCard(state,entry.film);
            if("Continue Watching".equals(kind))card.setOnClickListener(v->play(entry.film,entry.ultra,true));
            state.items.addView(card);
        }
    }
    private void home(){
        priorToDetails=null;
        generation++;mode="home";browsing=false;loading=false;next="";
        homeRows.clear();heroFilms.clear();heroIndex=0;
        shell("Home");hero(null);
        personalRail("Continue Watching",viewing.continueWatching());
        catalogueRail("Recently Added");
        catalogueRail("Popular");
        catalogueRail("Staff Picks");
        catalogueRail("Featured");
        personalRail("My List",viewing.myList());
        TextView copyright=text("ELIL TV  ·  BROUGHT BY PIJIN",10,0xff788594,false);
        copyright.setPadding(dp(65),dp(10),0,0);page.addView(copyright,size(-1,41,0,0));
    }
    private List<ViewingStore.Entry> personalEntries(String kind){
        if("Continue Watching".equals(kind))return viewing.continueWatching();
        if("My List".equals(kind))return viewing.myList();
        return viewing.recentlyWatched();
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
        if(entries.isEmpty())page.addView(action("Browse movies",()->browse("All Movies",""),true),size(180,48,0,0));
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
        if("Collections".equals(section)){collectionPicker();return;}
        generation++;mode="browse";browsing=true;loading=false;
        category=section;query=q;films.clear();loaded.clear();gridPosition=0;
        initialGridFillRounds=0;
        ultraCursors.clear();
        SharedPreferences prefs=getSharedPreferences("elil_catalogue_prefs",MODE_PRIVATE);
        qualityFilter=prefs.getString("quality", "all");
        if(!"all".equals(qualityFilter)&&!"hd".equals(qualityFilter)&&!"uhd".equals(qualityFilter))qualityFilter="all";
        if("Ultra HD".equals(category)){
            qualityFilter="uhd";
            for(String item:SOURCES)ultraCursors.add(source.listingUrl(language,item,""));
            next="ultra:discover";
        }else next=source.listingUrl(language,section,q);
        shell(capitalize(language)+"  /  Movies");
        LinearLayout titleBar=row();titleBar.setPadding(dp(0),0,dp(0),0);
        page.addView(titleBar,size(-1,49,0,6));
        TextView title=text(q.isEmpty()?"Movies":"Search results for “"+q+"”",23,WHITE,true);
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);
        titleBar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        HorizontalScrollView nav=new HorizontalScrollView(this);
        nav.setHorizontalScrollBarEnabled(false);
        nav.setFillViewport(false);
        LinearLayout tabs=row();tabs.setPadding(0,0,dp(15),0);
        nav.addView(tabs);page.addView(nav,size(-1,47,0,12));
        final ArrayList<TextView> tabItems=new ArrayList<>();
        for(String item:MOVIE_TABS){
            final int tabIndex=tabItems.size();
            boolean active=item.equals(category);
            TextView tab=headerLink((item.equals("Ultra HD")?"✦ ":"")+item,()->browse(item,""));
            tab.setTextSize(13);
            // The selected category stays visibly distinct even when it loses focus.
            tab.setTextColor(active?GOLD:0xffcad4df);
            tab.setBackground(bg(active?0xff27303b:Color.TRANSPARENT,7));
            tab.setOnFocusChangeListener((v,focused)->{
                tab.setBackground(bg(focused?GOLD:(active?0xff27303b:Color.TRANSPARENT),7));
                tab.setTextColor(focused?BG:(active?GOLD:0xffcad4df));
                tab.animate().cancel();
                tab.animate().scaleX(focused?1.035f:1f).scaleY(focused?1.035f:1f)
                    .setDuration(145).start();
                if(focused)nav.smoothScrollTo(Math.max(0,tab.getLeft()-dp(60)),0);
            });
            tab.setOnKeyListener((v,key,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
                if(key==KeyEvent.KEYCODE_DPAD_LEFT){
                    if(tabIndex==0)return focusSidebarFromContent(v);
                    return tabItems.get(tabIndex-1).requestFocus();
                }
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){
                    if(tabIndex+1<tabItems.size())return tabItems.get(tabIndex+1).requestFocus();
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){
                    if(grid!=null && grid.getChildCount()>0){
                        View firstGridRow=grid.getChildAt(0);
                        if(firstGridRow instanceof ViewGroup && ((ViewGroup)firstGridRow).getChildCount()>0)
                            return ((ViewGroup)firstGridRow).getChildAt(0).requestFocus();
                    }
                    // Wait for the initial catalogue page; don't jump to sign-in or the header.
                    return true;
                }
                return false;
            });
            tabs.addView(tab,size(item.equals("Recently Added")?127: item.equals("Staff Picks")?108: item.equals("Collections")?109: item.equals("All Movies")?99:98,39,8,0));
            tabItems.add(tab);
        }
        final LinearLayout thisBrowsePage=page;
        int wantedTab=java.util.Arrays.asList(MOVIE_TABS).indexOf(category);
        final TextView startingTab=tabItems.get(wantedTab>=0?wantedTab:0);
        startingTab.post(()->{
            if(!isFinishing() && "browse".equals(mode) && page==thisBrowsePage){
                startingTab.requestFocus();
                nav.smoothScrollTo(Math.max(0,startingTab.getLeft()-dp(60)),0);
            }
        });
        LinearLayout filters=row();filters.setPadding(0,0,dp(24),0);
        page.addView(filters,size(-1,43,0,6));
        filters.addView(headerLink("Language: "+capitalize(language)+"  ▾",this::pickLanguage),size(159,35,12,0));
        filters.addView(headerLink("Quality: "+("uhd".equals(qualityFilter)?"Ultra HD": "hd".equals(qualityFilter)?"HD":"All")+"  ▾",this::pickQuality),size(166,35,12,0));
        filters.addView(headerLink("⌕  Search",this::search),size(95,35,0,0));
        status=text("",12,MUTED,false);
        status.setVisibility(View.GONE);status.setPadding(0,0,0,0);
        page.addView(status,size(-1,28,0,5));
        grid=column();grid.setClipChildren(false);grid.setPadding(0,0,dp(8),0);
        page.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        more=headerLink("Load more films  ↓",this::loadBatch);
        page.addView(more,size(-1,49,0,0));more.setVisibility(View.GONE);
        loadBatch();
    }
    private void pickQuality(){
        String[] names={"All qualities","Ultra HD only","HD only"};
        new AlertDialog.Builder(this).setTitle("Filter movie quality")
            .setItems(names,(dialog,i)->{
                qualityFilter=new String[]{"all","uhd","hd"}[i];
                getSharedPreferences("elil_catalogue_prefs",MODE_PRIVATE).edit()
                    .putString("quality",qualityFilter).apply();
                // An Ultra HD tab always shows UHD. Other qualities switch to All Movies.
                browse("Ultra HD".equals(category)&&!"uhd".equals(qualityFilter)?"All Movies":category,query);
            }).show();
    }
    private boolean allowedFilm(Film f){
        return "all".equals(qualityFilter)||("uhd".equals(qualityFilter)=="uhd".equals(f.quality));
    }
    private void loadBatch(){
        if(!browsing||loading||TextUtils.isEmpty(next))return;
        loading=true;final int gen=generation;
        final int gridTarget=Math.max(16,gridColumns()*2);
        final int existingCount=gridPosition;
        more.setVisibility(View.GONE);
        String start=next;
        status.setVisibility(View.GONE);
        io.submit(()->{
            ArrayList<Film> batch=new ArrayList<>();
            ArrayList<String> resultingCursors=new ArrayList<>();
            String cursor=start,problem="";
            try{
                if("Ultra HD".equals(category)){
                    // Explicitly scan multiple existing collection listings. This is a
                    // discovery subset, NOT a claim of every Ultra HD title on Einthusan.
                    ArrayList<String> cursors=new ArrayList<>(ultraCursors);
                    for(int i=0;i<cursors.size();i++){
                        String ptr=cursors.get(i);
                        if(TextUtils.isEmpty(ptr))continue;
                        EinthusanClient.Page result=source.browse(ptr);
                        for(Film f:result.films)if("uhd".equals(f.quality))batch.add(f);
                        cursors.set(i,ptr.equals(result.next)?"":result.next);
                    }
                    resultingCursors.addAll(cursors);
                    cursor="";
                    for(String c:cursors)if(!TextUtils.isEmpty(c)){cursor="ultra:discover";break;}
                }else{
                    String prior="";
                    // Scan enough pages to be useful with a selected quality filter.
                    for(int i=0;i<8&&!TextUtils.isEmpty(cursor);i++){
                        if(cursor.equals(prior))break;
                        EinthusanClient.Page result=source.browse(cursor);
                        for(Film f:result.films)if(allowedFilm(f))batch.add(f);
                        prior=cursor;cursor=result.next;
                        if(existingCount+batch.size()>=gridTarget)break;
                    }
                }
            }catch(Exception ex){problem=ex.getMessage();cursor=start;}
            final String remaining=cursor,err=problem;
            runOnUiThread(()->{
                if(isFinishing()||gen!=generation||!"browse".equals(mode))return;
                if("Ultra HD".equals(category)&&!resultingCursors.isEmpty()){
                    ultraCursors.clear();ultraCursors.addAll(resultingCursors);
                }
                int inserted=0;
                for(Film f:batch)if(loaded.add(key(f))){films.add(f);addToGrid(f);inserted++;}
                next=remaining;
                loading=false;
                if(!err.isEmpty()){
                    status.setText("Could not load more films. "+err);
                    status.setVisibility(View.VISIBLE);
                } else if("Ultra HD".equals(category)){
                    status.setText("Ultra HD-labelled films found in scanned collections · More appear as you browse");
                    status.setVisibility(View.VISIBLE);
                }else if(inserted==0&&TextUtils.isEmpty(next)){
                    status.setText(films.isEmpty()?"No matching films were found.":"");
                    status.setVisibility(films.isEmpty()?View.VISIBLE:View.GONE);
                }else if(inserted==0){
                    status.setText("No matching films in these pages. Load more to keep searching.");
                    status.setVisibility(View.VISIBLE);
                }
                more.setVisibility(TextUtils.isEmpty(next)?View.GONE:View.VISIBLE);
                // Populate two full rows without waiting for a user to focus
                // the last tile. Bounded retries prevent endless scans when
                // quality filters match very few movies.
                if(gridPosition<gridTarget && !TextUtils.isEmpty(next) &&
                   initialGridFillRounds++<2 && err.isEmpty()){
                    grid.post(this::loadBatch);
                }
            });
        });
    }

    private String key(Film film){return film.language+":"+film.id;}
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
        return Math.max(86,(int)((screenWidthDp()-106-columns*9)/columns));
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
        frame.setBackground(bg(0xff17202c,6));
        frame.setClipToOutline(true);
        tile.addView(frame,size(-1,posterHeight(),0,4));
        ImageView poster=new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(bg(0xff192432,6));
        frame.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        images.load(this,poster,film.poster);
        ViewingStore.Entry progress=viewing.entry(film);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(GOLD));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff4b5868));
        if(progress!=null&&progress.started&&progress.durationMs>0)
            bar.setProgress(progress.percent());
        else bar.setVisibility(View.GONE);
        frame.addView(bar,new FrameLayout.LayoutParams(-1,dp(3),Gravity.BOTTOM));
        tile.setTag(new PosterProgress(film,bar));
        TextView label=text(film.title,11,0xffe9eaf0,false);
        label.setMaxLines(1);label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label,size(-1,24,0,0));
        tile.setOnFocusChangeListener((v,focused)->{
            frame.setBackground(outlined(focused?0xff222c39:0xff17202c,8,
                focused?GOLD:0xff17202c));
            frame.setPadding(focused?dp(2):0,focused?dp(2):0,focused?dp(2):0,focused?dp(2):0);
            label.setTextColor(focused?WHITE:0xffe9eaf0);
            tile.animate().cancel();
            tile.animate().scaleX(focused?1.055f:1f).scaleY(focused?1.055f:1f)
                .translationY(focused?-dp(3):0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
            tile.setElevation(focused?dp(14):0);
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
        if(!"details".equals(mode)){
            returnMode=mode;
            saveBrowseScreen();
        }
        selected=film;mode="details";browsing=false;loading=false;
        shell(film.title);
        FrameLayout cinema=new FrameLayout(this);
        page.addView(cinema,size(-1,282,0,10));
        artBackdrop(cinema,film,282);
        cinema.addView(headerContent(true,""),
            new FrameLayout.LayoutParams(-1,dp(55),Gravity.TOP));
        LinearLayout copy=column();copy.setPadding(dp(34),dp(65),dp(14),0);
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
            12,0xffc9d2df,false);
        intro.setMaxLines(3);intro.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(intro,size(-1,53,0,10));
        ViewingStore.Entry entry=viewing.entry(film);
        LinearLayout controls=row();copy.addView(controls,size(-1,44,0,8));
        TextView primary;
        if(entry!=null&&entry.canResume()){
            primary=action("▶   Resume",()->play(film,false,true),true);
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
        primary.post(()->{
            if(!isFinishing() && "details".equals(mode) && selected==film){
                primary.requestFocus();
                if(scroll!=null)scroll.scrollTo(0,0);
            }
        });
        // Focus timing varies on Fire OS after returning from an EditText
        // search keyboard. Restore focus after the screen entrance transition.
        primary.postDelayed(()->{
            if(!isFinishing() && "details".equals(mode) && selected==film && !drawerShown)
                primary.requestFocus();
        },285);
        LinearLayout options=row();options.setPadding(dp(38),0,dp(38),0);
        page.addView(options,size(-1,47,0,14));
        if(entry!=null&&entry.canResume()&&!entry.hidden)
            options.addView(action("Remove from Continue Watching",()->{
                viewing.removeFromContinue(film);details(film);
            },false),size(246,39,12,0));
        options.addView(action("Website fallback",()->openWebsite(film.detailUrl),false),size(153,39,12,0));

    }
    private void play(Film film,boolean ultra){play(film,ultra,false);}
    /** One Play/Resume action. Auto checks labelled UHD on a verified account,
     * then falls back to the standard authorised stream if unavailable.
     * Forced Ultra HD is a saved user preference, not an entitlement bypass.
     */
    private void play(Film film,boolean ignoredUltra,boolean resume){
        final String preference=getSharedPreferences("elil_playback",MODE_PRIVATE)
            .getString("preferred_quality","auto");
        final boolean auto=!"standard".equals(preference) && !"ultra".equals(preference);
        final boolean eligible="uhd".equals(film.quality) && source.isSignedIn();
        final boolean requestUltra=eligible && (auto || "ultra".equals(preference));
        final ViewingStore.Entry progress=viewing.entry(film);
        final long startAt=resume&&progress!=null&&progress.canResume()?progress.positionMs:0L;
        final String profileId=viewing.activeId();
        AlertDialog spinner=new AlertDialog.Builder(this).setTitle("எழில் TV")
            .setMessage("Preparing "+film.title+"…").create();
        spinner.show();
        String referer=source.listingUrl(film.language,category,query);
        io.submit(()->{
            try{
                boolean actualUltra=requestUltra;
                EinthusanClient.Stream stream;
                try{
                    stream=source.resolve(film,referer,actualUltra);
                }catch(Exception uhdFailure){
                    if(!actualUltra)throw uhdFailure;
                    // Ultra HD can be labelled but entitlement may differ.
                    // Never fail Auto playback solely because UHD is unavailable.
                    actualUltra=false;
                    stream=source.resolve(film,referer,false);
                }
                final boolean usingUltra=actualUltra;
                final EinthusanClient.Stream selectedStream=stream;
                runOnUiThread(()->{
                    spinner.dismiss();if(isFinishing())return;
                    viewing.begin(profileId,film,usingUltra,!resume);
                    Intent intent=new Intent(this,PlayerActivity.class);
                    intent.putExtra("video_url",selectedStream.url);
                    intent.putExtra("video_headers",new HashMap<>(selectedStream.headers));
                    intent.putExtra("video_title",film.title);
                    intent.putExtra("film_id",film.id);
                    intent.putExtra("film_language",film.language);
                    intent.putExtra("film_poster",film.poster);
                    intent.putExtra("film_synopsis",film.synopsis);
                    intent.putExtra("film_url",film.detailUrl);
                    intent.putExtra("film_quality",film.quality);
                    intent.putExtra("film_badge",film.badge);
                    intent.putExtra("film_listing_referer",referer);
                    intent.putExtra("profile_id",profileId);
                    intent.putExtra("start_position_ms",startAt);
                    intent.putExtra("requested_ultra",usingUltra);
                    returningFromPlayer=true;
                    startActivity(intent);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    spinner.dismiss();if(isFinishing())return;
                    new AlertDialog.Builder(this).setTitle("Playback unavailable")
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
                            if(drawerAccountStatus!=null)drawerAccountStatus.setText("●  Signed in");
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
            source.signOut();
            setAccountFeedback(accountStatus,"○  Signed out");accountStatus.setTextColor(WHITE);
            accountChip.setText("○  SIGN IN");
            if(drawerAccountStatus!=null)drawerAccountStatus.setText("○  Not signed in");
        },false),size(116,48,0,0));
        TextView note=text("Remember Login uses this Firestick's Android Keystore. Sign out clears your saved credentials. Premium access is controlled by Einthusan.",12,MUTED,false);
        note.setMaxLines(3);
        page.addView(note,size(-1,65,0,0));
        signIn.requestFocus();
    }
    private void search(){
        generation++;mode="search";browsing=false;loading=false;next="";
        shell("Search");
        eyebrow(page,"FIND YOUR NEXT FILM");
        title(page,"Search movies",26);
        TextView hint=text("Search titles in "+capitalize(language)+"",13,MUTED,false);
        page.addView(hint,size(-1,30,0,8));
        LinearLayout searchRow=row();page.addView(searchRow,size(-1,54,0,18));
        EditText box=new EditText(this);
        box.setSingleLine(true);box.setTextSize(19);
        box.setHint("Film title");box.setTextColor(WHITE);box.setHintTextColor(MUTED);
        box.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_NORMAL);
        box.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        box.setBackground(outlined(0xff172231,11,0xff40516a));
        box.setPadding(dp(19),0,dp(19),0);
        searchRow.addView(box,new LinearLayout.LayoutParams(0,dp(49),1));
        Runnable submit=()->{String q=box.getText().toString().trim();
            if(!q.isEmpty())browse("Recently Added",q);
            else Toast.makeText(this,"Enter a film title",Toast.LENGTH_SHORT).show();};
        box.setOnEditorActionListener((v,action,event)->{
            if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                submit.run();return true;
            }return false;
        });
        searchRow.addView(action("Search  →",submit,false),size(129,49,0,0));
        TextView advice=text("Search your existing Elil TV catalogue. Results will appear below.",
            13,MUTED,false);
        page.addView(advice,size(-1,42,0,0));
        box.requestFocus();
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
            if(restoreBrowseScreen())return;
            // Fallback only if Android has destroyed the original view tree.
            if("personal".equals(returnMode))localCollection(localKind);
            else if("home".equals(returnMode))home();
            else browse(category,query);
            return;
        }
        if(!"home".equals(mode)){home();return;}
        openDrawer(getCurrentFocus());
    }
    @Override protected void onDestroy(){
        ui.removeCallbacks(heroRotate);priorToDetails=null;generation++;io.shutdownNow();images.shutdown();super.onDestroy();}
}
