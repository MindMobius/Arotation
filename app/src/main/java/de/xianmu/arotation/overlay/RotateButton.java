package de.xianmu.arotation.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import de.xianmu.arotation.data.Prefs;
import de.xianmu.arotation.ui.IconCatalog;
import de.xianmu.arotation.ui.Palette;

/** Circle/edge capsule are button chrome; the glyph is an unmodified upstream Tabler asset. */
public final class RotateButton extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Palette palette;
    private Drawable glyph;
    private ValueAnimator press;
    private boolean landscape,dockLeft;
    private float dock,pressScale=1;
    private int visualDp=48,iconIndex;
    public RotateButton(Context context) {
        super(context);palette=new Palette(context);setClickable(true);setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setIcon(new Prefs(context).icon());setLandscape(false);
    }
    public void setVisualDp(int value){visualDp=value;invalidate();}
    public void setDock(float value,boolean left){dock=value;dockLeft=left;invalidate();}
    public void setIcon(int value){
        iconIndex=IconCatalog.bounded(value);glyph=getContext().getDrawable(IconCatalog.DRAWABLES[iconIndex]).mutate();
        glyph.setTint(palette.buttonInk);setStateDescription("图标："+IconCatalog.NAMES[iconIndex]);invalidate();
    }
    public void refreshPalette(){palette=new Palette(getContext());glyph.setTint(palette.buttonInk);invalidate();}
    public void setLandscape(boolean value){
        landscape=value;setContentDescription(value?"切换为竖屏":"切换为横屏");invalidate();
    }
    public boolean isLandscape(){return landscape;}
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
        float diameter=visualDp*d,cy=getHeight()/2f;
        float edgeCenter=dockLeft?6*d:getWidth()-6*d;
        float cx=lerp(getWidth()/2f,edgeCenter,dock);
        float w=lerp(diameter,12*d,dock),h=lerp(diameter,36*d,dock),radius=lerp(diameter/2f,6*d,dock);
        canvas.save();canvas.scale(pressScale,pressScale,cx,cy);
        paint.setStyle(Paint.Style.FILL);paint.setColor(palette.button);
        canvas.drawRoundRect(cx-w/2,cy-h/2,cx+w/2,cy+h/2,radius,radius,paint);
        // A fine contrasting rim keeps the small handle legible over similarly colored content.
        if(dock>0) {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);
            paint.setColor(palette.buttonInk);paint.setAlpha(Math.round(80*dock));
            canvas.drawRoundRect(cx-w/2+d/2,cy-h/2+d/2,cx+w/2-d/2,cy+h/2-d/2,radius,radius,paint);
        }
        int half=Math.round(diameter*.29f);
        glyph.setAlpha(Math.round(255*(1-Math.min(1,dock*2))));
        glyph.setBounds(Math.round(cx)-half,Math.round(cy)-half,Math.round(cx)+half,Math.round(cy)+half);
        if(iconIndex==5&&landscape)canvas.rotate(90,cx,cy);
        glyph.draw(canvas);canvas.restore();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        // Reserve only this small control for dragging, never an entire screen edge.
        setSystemGestureExclusionRects(java.util.Collections.singletonList(new Rect(0,0,w,h)));
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    @Override protected void onDetachedFromWindow(){if(press!=null)press.cancel();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");info.setClickable(true);info.setLongClickable(false);
    }
}
