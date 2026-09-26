package de.xianmu.arotation.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import de.xianmu.arotation.R;

import java.util.ArrayList;

/** Six unmodified Tabler glyphs, shared by the overlay and Android launcher aliases. */
public final class IconCatalog {
    private IconCatalog() {}
    public static final int[] DRAWABLES={R.drawable.ic_tabler_rotate_clockwise,R.drawable.ic_tabler_refresh,
        R.drawable.ic_tabler_rotate_rectangle,R.drawable.ic_tabler_rotate_clockwise_2,
        R.drawable.ic_tabler_arrows_exchange,R.drawable.ic_tabler_device_tablet};
    public static final String[] NAMES={"圆弧","双环","转向","点线","双箭头","平板"};
    public static final String[] ALIASES={"IconClassic","IconCycle","IconAngle","IconDots","IconSwap","IconTablet"};
    public static int bounded(int value) {return Math.max(0,Math.min(DRAWABLES.length-1,value));}
    public static void applyLauncher(Context context,int value) {
        int selected=bounded(value);PackageManager pm=context.getPackageManager();
        ArrayList<PackageManager.ComponentEnabledSetting> changes=new ArrayList<>();
        boolean different=false;
        for(int i=0;i<ALIASES.length;i++) {
            ComponentName component=new ComponentName(context.getPackageName(),context.getPackageName()+"."+ALIASES[i]);
            int state=pm.getComponentEnabledSetting(component);
            boolean enabled=state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||
                (state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&i==0);
            different|=enabled!=(i==selected);
            changes.add(new PackageManager.ComponentEnabledSetting(component,i==selected?
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP));
        }
        // Batch API (33+) avoids a moment with zero launchable entries and keeps the service alive.
        if(different)pm.setComponentEnabledSettings(changes);
    }
}
