package de.xianmu.arotation.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import de.xianmu.arotation.quick.QuickAction;
import de.xianmu.arotation.ui.Palette;

/** One circular quick-action key; the glyph is an unmodified upstream Tabler asset. */
final class QuickActionView extends View {
    private final Palette palette;
    private final Drawable glyph;
    QuickActionView(Context context,QuickAction action) {
        super(context);
        palette=new Palette(context);
        glyph=context.getDrawable(action.icon).mutate();
        glyph.setTint(palette.buttonInk);
        GradientDrawable oval=new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(palette.button);
        oval.setStroke(Math.round(context.getResources().getDisplayMetrics().density),rim(palette));
        setBackground(new RippleDrawable(ColorStateList.valueOf(palette.line),oval,null));
        setContentDescription(context.getString(action.label));
        setClickable(true);setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private static int rim(Palette palette){
        return Color.argb(44,Color.red(palette.buttonInk),Color.green(palette.buttonInk),Color.blue(palette.buttonInk));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float d=getResources().getDisplayMetrics().density;
        int half=Math.round(11*d),cx=getWidth()/2,cy=getHeight()/2;
        glyph.setBounds(cx-half,cy-half,cx+half,cy+half);
        glyph.draw(canvas);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.ImageButton");info.setClickable(true);info.setLongClickable(false);
    }
}
