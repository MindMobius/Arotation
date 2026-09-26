package de.xianmu.arotation.overlay;

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

import de.xianmu.arotation.data.Prefs;

/** One bounded overlay window, including the 48dp-wide hit area of the edge handle. */
public final class OverlayHost {
    public interface Actions { void rotate(); void lostPermission(); }
    private final Context context;
    private final WindowManager wm;
    private final android.view.Display display;
    private final Prefs prefs;
    private final Actions actions;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final int slop;
    private RotateButton view;
    private WindowManager.LayoutParams params;
    private ValueAnimator transition;
    private int minX,maxX,minY,maxY,width,height,size,edgeLeft,edgeRight;
    private boolean landscape,attached,dragging,touching,cancelled,dockLeft,screenVisible=true;
    private float dock,downX,downY,dragDx,dragDy,startAnchor;
    private int startY;
    private final Runnable dim=this::idle;

    public OverlayHost(Context context,Prefs prefs,Actions actions) {
        display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY);
        this.context=context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        wm=this.context.getSystemService(WindowManager.class);this.prefs=prefs;this.actions=actions;
        slop=ViewConfiguration.get(this.context).getScaledTouchSlop();
    }
    // x/y are physical screen coordinates; START would invert placement in RTL.
    @android.annotation.SuppressLint("RtlHardcoded")
    public void show() {
        view=new RotateButton(context);size=dp(prefs.touchDp());view.setVisualDp(prefs.visualDp());
        params=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.setFitInsetsTypes(0);
        params.setTitle("Arotation floating button");params.windowAnimations=0;
        // We own interpolation. A second WindowManager move animation makes dragging trail the finger.
        params.setCanPlayMoveAnimation(false);
        view.setOnClickListener(v->{reveal();actions.rotate();scheduleIdle();});
        view.setOnTouchListener(this::touch);
        updateBounds();placeSaved();
        wm.addView(view,params);attached=true;scheduleIdle();
    }
    public void refresh() {
        if(!attached)return;
        cancelTouch();cancelTransition();
        size=dp(prefs.touchDp());params.width=size;params.height=size;
        view.setVisualDp(prefs.visualDp());view.setIcon(prefs.icon());view.refreshPalette();
        updateBounds();placeSaved();setAppearance(0,1);update();scheduleIdle();
    }
    public void displayChanged() {
        if(!attached)return;
        android.graphics.Point r=new android.graphics.Point();display.getRealSize(r);
        if(r.x==width&&r.y==height) {view.setLandscape(width>height);return;}
        cancelTouch();cancelTransition();params.width=size;
        updateBounds();placeSaved();setAppearance(0,1);update();scheduleIdle();
    }
    private void updateBounds() {
        android.view.WindowMetrics m=wm.getMaximumWindowMetrics();
        // Window-context configuration can lag rotation; physical display coordinates do not.
        android.graphics.Point b=new android.graphics.Point();display.getRealSize(b);width=b.x;height=b.y;
        landscape=width>height;
        Insets in=m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        edgeLeft=in.left;edgeRight=in.right;
        minX=edgeLeft+dp(4);maxX=Math.max(minX,width-edgeRight-size-dp(4));
        minY=in.top+dp(8);maxY=Math.max(minY,height-in.bottom-size-dp(8));
        view.setLandscape(landscape);
    }
    private void placeSaved() {
        params.x=Math.round(Placement.clamp(Placement.resolve(prefs.x(landscape),0,width-size),minX,maxX));
        params.y=Math.round(Placement.clamp(Placement.resolve(prefs.y(landscape),0,height-size),minY,maxY));
        if(prefs.snap())params.x=Placement.nearestEdge(params.x,minX,maxX);
    }
    private void savePosition(int x) {
        // Never persist the transient, narrower dock window as the expanded position.
        prefs.position(landscape,Placement.normalize(x,0,width-size),Placement.normalize(params.y,0,height-size));
    }
    private boolean touch(View v,MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelTransition();handler.removeCallbacks(dim);
                touching=true;dragging=false;cancelled=false;
                downX=event.getRawX();downY=event.getRawY();dragDx=dragDy=0;
                startAnchor=params.x+(dockLeft?0:params.width);startY=params.y;
                view.setPressed(true);
                // Brighten in place. The handle must not jump away from the finger on down.
                animateTo(params.x,params.width,dock,1,100);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(!touching||cancelled)return true;
                dragDx=event.getRawX()-downX;dragDy=event.getRawY()-downY;
                if(!dragging&&dragDx*dragDx+dragDy*dragDy>slop*slop) {
                    dragging=true;view.setPressed(false);
                    animateTo(params.x,size,0,1,160);
                }
                if(dragging){placeDragged();update();}
                return true;
            case MotionEvent.ACTION_UP:
                if(!touching)return true;
                touching=false;view.setPressed(false);
                if(dragging)settleDrag();
                else if(!cancelled&&event.getEventTime()-event.getDownTime()<ViewConfiguration.getLongPressTimeout())view.performClick();
                else reveal();
                dragging=false;scheduleIdle();return true;
            case MotionEvent.ACTION_CANCEL:
                if(!touching)return true;
                touching=false;view.setPressed(false);
                if(dragging)settleDrag();else reveal();
                dragging=false;scheduleIdle();return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // An interrupted/multi-touch gesture can never become a rotate click.
                cancelled=true;cancelTransition();view.setPressed(false);return true;
            default:return true;
        }
    }
    private void placeDragged() {
        int rightBound=Math.max(minX,width-edgeRight-params.width-dp(4));
        // Anchor the outer edge while the handle unfolds; do not rebase the finger mid-drag.
        params.x=Math.round(Placement.clamp(startAnchor+dragDx-(dockLeft?0:params.width),minX,rightBound));
        params.y=Math.round(Placement.clamp(startY+dragDy,minY,maxY));
    }
    private void settleDrag() {
        cancelTransition();dragging=false;
        int expandedX=Math.round(Placement.clamp(params.x+(params.width-size)/2f,minX,maxX));
        int target=prefs.snap()?Placement.nearestEdge(expandedX,minX,maxX):expandedX;
        savePosition(target);animateTo(target,size,0,1,220);
    }
    private void reveal() {
        handler.removeCallbacks(dim);
        int target=Math.round(Placement.clamp(params.x+(dockLeft?0:params.width-size),minX,maxX));
        if(prefs.snap())target=Placement.nearestEdge(target,minX,maxX);
        animateTo(target,size,0,1,160);
    }
    private void scheduleIdle() {
        handler.removeCallbacks(dim);if(!screenVisible||touching)return;
        AccessibilityManager accessibility=context.getSystemService(AccessibilityManager.class);
        handler.postDelayed(dim,accessibility.getRecommendedTimeoutMillis(2800,AccessibilityManager.FLAG_CONTENT_CONTROLS));
    }
    private void idle() {
        if(!attached||touching||dragging||!screenVisible)return;
        AccessibilityManager accessibility=context.getSystemService(AccessibilityManager.class);
        if(prefs.hide()&&!accessibility.isTouchExplorationEnabled()) {
            dockLeft=params.x+params.width/2<width/2;
            int hitWidth=dp(48);
            int target=dockLeft?edgeLeft:width-edgeRight-hitWidth;
            animateTo(target,hitWidth,1,prefs.opacity()/100f,240);
        } else animateTo(params.x,size,0,prefs.opacity()/100f,220);
    }
    private void setAppearance(float value,float alpha) {
        dock=value;view.setDock(value,dockLeft);view.setAlpha(alpha);
    }
    private void animateTo(int x,int hitWidth,float toDock,float alpha,long duration) {
        cancelTransition();
        int fromX=params.x,fromWidth=params.width;
        float fromDock=dock,fromAlpha=view.getAlpha();
        if(!ValueAnimator.areAnimatorsEnabled()) {
            params.x=x;params.width=hitWidth;setAppearance(toDock,alpha);
            if(dragging)placeDragged();update();return;
        }
        transition=ValueAnimator.ofFloat(0,1);transition.setDuration(duration);
        transition.setInterpolator(new DecelerateInterpolator(1.5f));
        transition.addUpdateListener(a->{
            float t=(Float)a.getAnimatedValue();
            int oldX=params.x,oldWidth=params.width;
            params.width=Math.round(fromWidth+(hitWidth-fromWidth)*t);
            params.x=Math.round(fromX+(x-fromX)*t);
            setAppearance(fromDock+(toDock-fromDock)*t,fromAlpha+(alpha-fromAlpha)*t);
            if(dragging)placeDragged();
            if(oldX!=params.x||oldWidth!=params.width)update();
        });
        transition.start();
    }
    private void cancelTransition(){if(transition!=null){transition.cancel();transition=null;}}
    private void cancelTouch(){touching=false;dragging=false;cancelled=true;view.setPressed(false);}
    private void update() {
        if(!attached)return;
        try {wm.updateViewLayout(view,params);}catch(RuntimeException failure){handler.post(actions::lostPermission);}
    }
    public void setScreenVisible(boolean visible) {
        screenVisible=visible;if(!attached)return;
        cancelTouch();cancelTransition();handler.removeCallbacks(dim);
        view.setVisibility(visible?View.VISIBLE:View.GONE);
        if(visible){params.width=size;placeSaved();setAppearance(0,1);update();scheduleIdle();}
    }
    public void close() {
        handler.removeCallbacksAndMessages(null);cancelTransition();
        if(attached){attached=false;try{wm.removeViewImmediate(view);}catch(IllegalArgumentException ignored){}}
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
