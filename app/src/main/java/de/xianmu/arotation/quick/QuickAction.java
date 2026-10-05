package de.xianmu.arotation.quick;

import android.accessibilityservice.AccessibilityService;
import de.xianmu.arotation.R;

/**
 * Stable preference bits, independent of menu position or the selected floating icon.
 * Rotate and home need no elevated permission: home is a plain CATEGORY_HOME activity start,
 * while back and recents have no public API other than the accessibility global actions.
 */
public enum QuickAction {
    ROTATE(1, R.string.action_rotate, R.drawable.ic_tabler_rotate_clockwise, 0, false),
    BACK(2, R.string.action_back, R.drawable.ic_tabler_arrow_left, AccessibilityService.GLOBAL_ACTION_BACK, true),
    HOME(4, R.string.action_home, R.drawable.ic_tabler_home, 0, false),
    RECENTS(8, R.string.action_recents, R.drawable.ic_tabler_apps, AccessibilityService.GLOBAL_ACTION_RECENTS, true);

    public static final int ALL=15;
    public final int bit,label,icon,systemAction;
    public final boolean requiresAccessibility;
    QuickAction(int bit,int label,int icon,int systemAction,boolean requiresAccessibility) {
        this.bit=bit;this.label=label;this.icon=icon;this.systemAction=systemAction;
        this.requiresAccessibility=requiresAccessibility;
    }
    public boolean isNavigation(){return this!=ROTATE;}
    public static int normalize(int mask){int valid=mask&ALL;return valid==0?ROTATE.bit:valid;}
    public static QuickAction[] selected(int mask) {
        int valid=normalize(mask),index=0;
        QuickAction[] result=new QuickAction[Integer.bitCount(valid)];
        for(QuickAction action:values())if((valid&action.bit)!=0)result[index++]=action;
        return result;
    }
}
