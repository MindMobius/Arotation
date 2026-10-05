package de.xianmu.arotation.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Locale;

import de.xianmu.arotation.data.Prefs;
import de.xianmu.arotation.quick.QuickAction;

/**
 * One bounded dot window plus, while the menu is open, one small window per action arranged in a
 * half-circle toward the screen interior. Every key owns its own window, so no invisible area
 * swallows touches.
 */
public final class OverlayHost {
    public interface Actions { void rotate(); void navigate(QuickAction action); void lostPermission(); }
    private static final float BUTTON_DP=52f,RING_GAP_DP=10f,EDGE_DP=4f;
    private static final double FAN_STEP_DEG=60d;
    private final Context context;
    private final WindowManager wm;
    private final android.view.Display display;
    private final Prefs prefs;
    private final Actions actions;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final int slop;
    private final ArrayList<ActionKey> ring=new ArrayList<>();
    private FloatingDot view;
    private WindowManager.LayoutParams params;
    private ValueAnimator ballTransition,ringTransition;
    private int minX,maxX,minY,maxY,width,height,size,edgeLeft,edgeRight,edgeTop,edgeBottom;
    private boolean landscape,attached,dragging,touching,cancelled,screenVisible=true,menuOpen;
    private float downX,downY,dragDx,dragDy;
    private int startX,startY,savedX,savedY;
    private final Runnable dim=this::idle;
    private final Runnable menuTimeout=()->{closeMenu(true,true);scheduleIdle();};

    private static final class ActionKey {
        final QuickAction action;
        final View view;
        final WindowManager.LayoutParams params;
        float fromX,fromY,toX,toY;
        ActionKey(QuickAction action,View view,WindowManager.LayoutParams params,float fromX,float fromY,float toX,float toY) {
            this.action=action;this.view=view;this.params=params;
            this.fromX=fromX;this.fromY=fromY;this.toX=toX;this.toY=toY;
        }
    }

    public OverlayHost(Context context,Prefs prefs,Actions actions) {
        display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY);
        this.context=context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        wm=this.context.getSystemService(WindowManager.class);this.prefs=prefs;this.actions=actions;
        slop=ViewConfiguration.get(this.context).getScaledTouchSlop();
    }
    // x/y are physical screen coordinates; START would invert placement in RTL.
    @android.annotation.SuppressLint("RtlHardcoded")
    public void show() {
        view=new FloatingDot(context);size=dp(prefs.touchDp());view.setVisualDp(prefs.visualDp());
        params=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.setFitInsetsTypes(0);
        params.setTitle("Arotation floating button");params.windowAnimations=0;
        // We own interpolation. A second WindowManager move animation makes dragging trail the finger.
        params.setCanPlayMoveAnimation(false);
        view.setOnClickListener(v->onBallClick());
        view.setOnTouchListener(this::touch);
        updateBounds();placeSaved();
        wm.addView(view,params);attached=true;scheduleIdle();
    }
    public void refresh() {
        if(!attached)return;
        cancelTouch();resetMotion();
        size=dp(prefs.touchDp());params.width=size;params.height=size;
        view.setVisualDp(prefs.visualDp());view.refreshPalette();
        updateBounds();placeSaved();view.setAlpha(1f);update();scheduleIdle();
    }
    public void displayChanged() {
        if(!attached)return;
        android.graphics.Point r=new android.graphics.Point();display.getRealSize(r);
        if(r.x==width&&r.y==height)return;
        cancelTouch();resetMotion();params.width=size;
        updateBounds();placeSaved();view.setAlpha(1f);update();scheduleIdle();
    }
    private void updateBounds() {
        android.view.WindowMetrics m=wm.getMaximumWindowMetrics();
        // Window-context configuration can lag rotation; physical display coordinates do not.
        android.graphics.Point b=new android.graphics.Point();display.getRealSize(b);width=b.x;height=b.y;
        landscape=width>height;
        Insets in=m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        edgeLeft=in.left;edgeRight=in.right;edgeTop=in.top;edgeBottom=in.bottom;
        minX=edgeLeft+dp(EDGE_DP);maxX=Math.max(minX,width-edgeRight-size-dp(EDGE_DP));
        minY=edgeTop+dp(8);maxY=Math.max(minY,height-edgeBottom-size-dp(8));
    }
    private void placeSaved() {
        params.x=Math.round(Placement.clamp(Placement.resolve(prefs.x(landscape),0,width-size),minX,maxX));
        params.y=Math.round(Placement.clamp(Placement.resolve(prefs.y(landscape),0,height-size),minY,maxY));
        if(prefs.snap())params.x=Placement.nearestEdge(params.x,minX,maxX);
    }
    private void savePosition(int x,int y) {
        prefs.position(landscape,Placement.normalize(x,0,width-size),Placement.normalize(y,0,height-size));
    }
    private boolean touch(View v,MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelBallTransition();handler.removeCallbacks(dim);
                touching=true;dragging=false;cancelled=false;
                downX=event.getRawX();downY=event.getRawY();dragDx=dragDy=0;
                startX=params.x;startY=params.y;
                view.setPressed(true);view.setAlpha(1f);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(!touching||cancelled)return true;
                dragDx=event.getRawX()-downX;dragDy=event.getRawY()-downY;
                if(!dragging&&dragDx*dragDx+dragDy*dragDy>slop*slop) {
                    dragging=true;view.setPressed(false);
                    closeMenu(true,false);
                    cancelBallTransition();
                    // Rebase on the current position so closing the ring never makes the ball jump.
                    startX=params.x;startY=params.y;
                    downX=event.getRawX();downY=event.getRawY();dragDx=dragDy=0;
                }
                if(dragging){placeDragged();update();}
                return true;
            case MotionEvent.ACTION_UP:
                if(!touching)return true;
                touching=false;view.setPressed(false);
                if(dragging)settleDrag();
                else if(!cancelled&&event.getEventTime()-event.getDownTime()<ViewConfiguration.getLongPressTimeout())view.performClick();
                dragging=false;scheduleIdle();return true;
            case MotionEvent.ACTION_CANCEL:
                if(!touching)return true;
                touching=false;view.setPressed(false);
                if(dragging)settleDrag();
                dragging=false;scheduleIdle();return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // An interrupted/multi-touch gesture can never become a click.
                cancelled=true;view.setPressed(false);return true;
            default:return true;
        }
    }
    private void placeDragged() {
        params.x=Math.round(Placement.clamp(startX+dragDx,minX,maxX));
        params.y=Math.round(Placement.clamp(startY+dragDy,minY,maxY));
    }
    private void settleDrag() {
        dragging=false;
        int x=prefs.snap()?Placement.nearestEdge(params.x,minX,maxX):params.x;
        savePosition(x,params.y);
        animateBall(x,params.y,1f,220);
    }
    private void onBallClick() {
        QuickAction[] selected=QuickAction.selected(prefs.quickActions());
        if(menuOpen){closeMenu(true,true);scheduleIdle();return;}
        handler.removeCallbacks(dim);view.setAlpha(1f);
        showRing(selected);
    }
    private void perform(QuickAction action) {
        if(action==QuickAction.ROTATE)actions.rotate();else actions.navigate(action);
    }
    /** iOS-style fan: the dot keeps its edge position, keys spread over a half circle inward. */
    private void showRing(QuickAction[] selected) {
        if(!attached||menuOpen)return;
        menuOpen=true;
        handler.removeCallbacks(dim);handler.removeCallbacks(menuTimeout);
        savedX=params.x;savedY=params.y;
        float buttonSize=dp(BUTTON_DP),buttonRadius=buttonSize/2f;
        float ringRadius=dp(prefs.visualDp()/2f+RING_GAP_DP)+buttonRadius;
        boolean openRight=savedX+size/2f<width/2f;
        double step=Math.toRadians(FAN_STEP_DEG);
        double[] relative=new double[selected.length];double maxSin=0;
        for(int i=0;i<selected.length;i++) {
            relative[i]=(i-(selected.length-1)/2d)*step;
            maxSin=Math.max(maxSin,Math.abs(Math.sin(relative[i])));
        }
        float reach=ringRadius*(float)maxSin+buttonRadius+dp(EDGE_DP);
        float centerY=Placement.clamp(savedY+size/2f,edgeTop+reach,height-edgeBottom-reach);
        float inward=ringRadius+buttonRadius+dp(EDGE_DP);
        float centerX=savedX+size/2f;
        if(openRight)centerX=Math.min(centerX,width-edgeRight-inward);
        else centerX=Math.max(centerX,edgeLeft+inward);
        centerX=Placement.clamp(centerX,minX+size/2f,maxX+size/2f);
        animateBall(Math.round(centerX-size/2f),Math.round(centerY-size/2f),1f,180);
        view.setRingOpen(true);
        ring.clear();
        for(int i=0;i<selected.length;i++) {
            float x=centerX+(openRight?1f:-1f)*(float)Math.cos(relative[i])*ringRadius;
            float y=centerY+(float)Math.sin(relative[i])*ringRadius;
            ring.add(actionKey(selected[i],x,y,centerX,centerY,buttonSize));
        }
        for(ActionKey key:ring) {
            try{wm.addView(key.view,key.params);}catch(RuntimeException ignored){}
        }
        animateRing(true);
        AccessibilityManager accessibility=context.getSystemService(AccessibilityManager.class);
        handler.postDelayed(menuTimeout,accessibility.getRecommendedTimeoutMillis(5000,AccessibilityManager.FLAG_CONTENT_CONTROLS));
    }
    private ActionKey actionKey(QuickAction action,float x,float y,float fromX,float fromY,float buttonSize) {
        QuickActionView key=new QuickActionView(context,action);
        key.setOnClickListener(v->onMenuAction(action));
        key.setAlpha(0f);key.setScaleX(.4f);key.setScaleY(.4f);
        WindowManager.LayoutParams layout=new WindowManager.LayoutParams(Math.round(buttonSize),Math.round(buttonSize),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;layout.setFitInsetsTypes(0);
        layout.setTitle("Arotation action "+action.name().toLowerCase(Locale.ROOT));
        layout.windowAnimations=0;layout.setCanPlayMoveAnimation(false);
        layout.x=Math.round(fromX-buttonSize/2f);layout.y=Math.round(fromY-buttonSize/2f);
        return new ActionKey(action,key,layout,fromX,fromY,x,y);
    }
    private void animateRing(boolean opening) {
        if(ring.isEmpty())return;
        cancelRingTransition();
        if(!ValueAnimator.areAnimatorsEnabled()) {
            for(ActionKey key:ring) {
                place(key,opening?key.toX:key.fromX,opening?key.toY:key.fromY);
                key.view.setAlpha(opening?1f:0f);
                key.view.setScaleX(opening?1f:.4f);key.view.setScaleY(opening?1f:.4f);
                update(key);
            }
            if(!opening)removeRing();
            return;
        }
        ringTransition=ValueAnimator.ofFloat(opening?0f:1f,opening?1f:0f);
        ringTransition.setDuration(opening?200:140);
        ringTransition.setInterpolator(new DecelerateInterpolator(1.5f));
        ringTransition.addUpdateListener(a->{
            float t=(Float)a.getAnimatedValue();
            for(ActionKey key:ring) {
                place(key,key.fromX+(key.toX-key.fromX)*t,key.fromY+(key.toY-key.fromY)*t);
                key.view.setAlpha(t);key.view.setScaleX(.4f+.6f*t);key.view.setScaleY(.4f+.6f*t);
                update(key);
            }
        });
        ringTransition.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator animation) {
                if(!menuOpen)removeRing();
                else for(ActionKey key:ring) {
                    key.view.setAlpha(1f);key.view.setScaleX(1f);key.view.setScaleY(1f);update(key);
                }
            }
        });
        ringTransition.start();
    }
    private void place(ActionKey key,float centerX,float centerY) {
        key.params.x=Math.round(centerX-key.params.width/2f);
        key.params.y=Math.round(centerY-key.params.height/2f);
    }
    private void onMenuAction(QuickAction action) {
        closeMenu(true,true);perform(action);scheduleIdle();
    }
    private void closeMenu(boolean animate,boolean restoreBall) {
        handler.removeCallbacks(menuTimeout);
        if(!menuOpen)return;
        menuOpen=false;
        view.setRingOpen(false);
        if(restoreBall)animateBall(savedX,savedY,1f,180);
        if(ring.isEmpty())return;
        float endX=(restoreBall?savedX:params.x)+size/2f,endY=(restoreBall?savedY:params.y)+size/2f;
        for(ActionKey key:ring) {
            key.fromX=key.params.x+key.params.width/2f;key.fromY=key.params.y+key.params.height/2f;
            key.toX=endX;key.toY=endY;
        }
        if(animate)animateRing(false);
        else removeRing();
    }
    /** Rotation and preference changes must never let a stale animation write old coordinates back. */
    private void resetMotion() {
        cancelBallTransition();
        handler.removeCallbacks(menuTimeout);
        if(menuOpen||!ring.isEmpty()) {
            menuOpen=false;
            view.setRingOpen(false);
            removeRing();
        } else cancelRingTransition();
    }
    private void removeRing() {
        cancelRingTransition();
        for(ActionKey key:ring) {
            try{wm.removeViewImmediate(key.view);}catch(IllegalArgumentException ignored){}
        }
        ring.clear();
    }
    private void scheduleIdle() {
        handler.removeCallbacks(dim);if(!screenVisible||touching)return;
        AccessibilityManager accessibility=context.getSystemService(AccessibilityManager.class);
        handler.postDelayed(dim,accessibility.getRecommendedTimeoutMillis(2800,AccessibilityManager.FLAG_CONTENT_CONTROLS));
    }
    private void idle() {
        if(!attached||touching||dragging||!screenVisible)return;
        if(menuOpen){closeMenu(true,true);scheduleIdle();return;}
        animateBall(params.x,params.y,prefs.opacity()/100f,220);
    }
    private void animateBall(int x,int y,float alpha,long duration) {
        cancelBallTransition();
        int fromX=params.x,fromY=params.y;float fromAlpha=view.getAlpha();
        if(!ValueAnimator.areAnimatorsEnabled()) {
            params.x=x;params.y=y;view.setAlpha(alpha);
            if(dragging)placeDragged();
            update();return;
        }
        ballTransition=ValueAnimator.ofFloat(0,1);ballTransition.setDuration(duration);
        ballTransition.setInterpolator(new DecelerateInterpolator(1.5f));
        ballTransition.addUpdateListener(a->{
            float t=(Float)a.getAnimatedValue();
            params.x=Math.round(fromX+(x-fromX)*t);
            params.y=Math.round(fromY+(y-fromY)*t);
            view.setAlpha(fromAlpha+(alpha-fromAlpha)*t);
            if(dragging)placeDragged();
            update();
        });
        ballTransition.start();
    }
    private void cancelBallTransition(){if(ballTransition!=null){ballTransition.cancel();ballTransition=null;}}
    private void cancelRingTransition(){if(ringTransition!=null){ringTransition.cancel();ringTransition=null;}}
    private void cancelTouch(){touching=false;dragging=false;cancelled=true;view.setPressed(false);}
    private void update() {
        if(!attached)return;
        try{wm.updateViewLayout(view,params);}catch(RuntimeException failure){handler.post(actions::lostPermission);}
    }
    private void update(ActionKey key) {
        if(!attached)return;
        try{wm.updateViewLayout(key.view,key.params);}catch(RuntimeException ignored){}
    }
    public void setScreenVisible(boolean visible) {
        screenVisible=visible;if(!attached)return;
        cancelTouch();resetMotion();handler.removeCallbacks(dim);
        view.setVisibility(visible?View.VISIBLE:View.GONE);
        if(visible){params.width=size;placeSaved();view.setAlpha(1f);update();scheduleIdle();}
    }
    public void close() {
        handler.removeCallbacksAndMessages(null);
        cancelBallTransition();closeMenu(false,false);
        if(attached){attached=false;try{wm.removeViewImmediate(view);}catch(IllegalArgumentException ignored){}}
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
