package de.xianmu.arotation.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import de.xianmu.arotation.quick.QuickAction;

/** User-enabled system navigation only. No event subscription or window-content capability. */
public final class NavigationAccessibilityService extends AccessibilityService {
    public static final String STATE="de.xianmu.arotation.NAVIGATION_STATE";
    private static volatile NavigationAccessibilityService connected;

    public static boolean isConnected(){return connected!=null;}
    public static boolean isAvailable(QuickAction action) {
        if(!action.isNavigation())return true;
        NavigationAccessibilityService service=connected;
        if(service==null)return false;
        try {
            for(AccessibilityAction system:service.getSystemActions())
                if(system.getId()==action.systemAction)return true;
        }catch(RuntimeException failure){android.util.Log.w("Arotation","System action unavailable",failure);}
        return false;
    }
    public static boolean perform(QuickAction action) {
        NavigationAccessibilityService service=connected;
        if(service==null||!action.isNavigation()||!isAvailable(action))return false;
        try{return service.performGlobalAction(action.systemAction);}
        catch(RuntimeException failure){android.util.Log.w("Arotation","System navigation failed",failure);return false;}
    }
    @Override protected void onServiceConnected(){super.onServiceConnected();connected=this;publish();}
    @Override public void onSystemActionsChanged(){publish();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){/* No subscribed events. */}
    @Override public void onInterrupt(){/* No continuous feedback to interrupt. */}
    @Override public boolean onUnbind(Intent intent){disconnect();return super.onUnbind(intent);}
    @Override public void onDestroy(){disconnect();super.onDestroy();}
    private void disconnect(){if(connected==this){connected=null;publish();}}
    private void publish(){sendBroadcast(new Intent(STATE).setPackage(getPackageName()));}
}
