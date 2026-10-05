package de.xianmu.arotation.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import de.xianmu.arotation.R;
import de.xianmu.arotation.ui.Palette;

/** The floating dot: fixed round chrome with a small ring glyph, never an action icon. */
public final class FloatingDot extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Palette palette;
    private ValueAnimator press;
    private boolean ringOpen;
    private float pressScale=1;
    private int visualDp=48;
    public FloatingDot(Context context) {
        super(context);palette=new Palette(context);setClickable(true);setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateDescription();
    }
    public void setVisualDp(int value){visualDp=value;invalidate();}
    public void refreshPalette(){palette=new Palette(getContext());invalidate();}
    public void setRingOpen(boolean open){ringOpen=open;updateDescription();}
    private void updateDescription(){
        setContentDescription(getContext().getString(ringOpen?R.string.quick_menu_close:R.string.quick_menu_open));
    }
    @Override public void setPressed(boolean pressed){
        super.setPressed(pressed);if(press!=null)press.cancel();
        float target=pressed?.94f:1f;
        // Scale only the drawing, never the hit area or accessibility bounds.
        if(ValueAnimator.areAnimatorsEnabled()) {
            press=ValueAnimator.ofFloat(pressScale,target);press.setDuration(100);
            press.addUpdateListener(a->{pressScale=(Float)a.getAnimatedValue();invalidate();});press.start();
        } else {pressScale=target;invalidate();}
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;
        float diameter=visualDp*d,cx=getWidth()/2f,cy=getHeight()/2f;
        canvas.save();canvas.scale(pressScale,pressScale,cx,cy);
        paint.setStyle(Paint.Style.FILL);paint.setColor(palette.button);
        canvas.drawCircle(cx,cy,diameter/2f,paint);
        // The iOS-style ring is the fixed "logo" of the dot.
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f*d);paint.setColor(palette.buttonInk);
        canvas.drawCircle(cx,cy,diameter*.16f,paint);
        canvas.restore();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        // Reserve only this small control for dragging, never an entire screen edge.
        setSystemGestureExclusionRects(java.util.Collections.singletonList(new Rect(0,0,w,h)));
    }
    @Override protected void onDetachedFromWindow(){if(press!=null)press.cancel();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");info.setClickable(true);info.setLongClickable(false);
    }
}
