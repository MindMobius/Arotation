package de.xianmu.arotation.data;

import android.content.Context;
import android.content.SharedPreferences;

import de.xianmu.arotation.quick.QuickAction;

public final class Prefs {
    public final SharedPreferences store;
    public Prefs(Context context) { store=context.getSharedPreferences("arotation",Context.MODE_PRIVATE); }
    public int quickActions(){return QuickAction.normalize(store.getInt("quick_actions",QuickAction.ROTATE.bit));}
    public boolean needsAccessibility(){
        int mask=quickActions();
        return (mask&QuickAction.BACK.bit)!=0||(mask&QuickAction.RECENTS.bit)!=0;
    }
    public boolean setQuickAction(QuickAction action,boolean enabled) {
        int mask=enabled?quickActions()|action.bit:quickActions()&~action.bit;
        if(mask==0)return false;
        store.edit().putInt("quick_actions",mask).apply();return true;
    }
    public int size() { return Math.max(0,Math.min(2,store.getInt("size",1))); }
    public int visualDp() { return new int[]{40,48,56}[size()]; }
    public int touchDp() { return visualDp()+16; }
    public int opacity() { return Math.max(25,Math.min(85,store.getInt("opacity",56))); }
    public boolean snap() { return store.getBoolean("snap",true); }
    public boolean reverseLandscape() { return store.getBoolean("reverse",false); }
    public boolean desired() { return store.getBoolean("enabled",false); }
    public void desired(boolean enabled) { store.edit().putBoolean("enabled",enabled).apply(); }
    public float x(boolean landscape) { return store.getFloat(landscape?"land_x":"port_x",1f); }
    public float y(boolean landscape) { return store.getFloat(landscape?"land_y":"port_y",.62f); }
    public void position(boolean landscape,float x,float y) {
        store.edit().putFloat(landscape?"land_x":"port_x",x).putFloat(landscape?"land_y":"port_y",y).apply();
    }
    public void resetPositions() { store.edit().remove("land_x").remove("land_y").remove("port_x").remove("port_y").apply(); }
}
