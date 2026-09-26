package de.xianmu.arotation.rotation;

import android.content.Context;
import android.provider.Settings;
import android.view.Display;

import de.xianmu.arotation.data.Prefs;

/** Only class allowed to write system rotation settings. Persists a recoverable ownership record. */
// Synchronous tiny journal writes are deliberate: recovery must survive immediate process death.
@android.annotation.SuppressLint("ApplySharedPref")
public final class RotationController {
    private final Context context;
    private final Prefs prefs;
    private final Display display;
    public RotationController(Context context,Prefs prefs) {
        this.context=context;this.prefs=prefs;
        display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
    }
    public int rotation() { return display.getRotation(); }
    public boolean landscape() {
        android.graphics.Point size=new android.graphics.Point();display.getRealSize(size);return size.x>size.y;
    }
    public boolean canWrite() { return Settings.System.canWrite(context); }
    private int read(String key,int fallback) { return Settings.System.getInt(context.getContentResolver(),key,fallback); }
    private void write(String key,int value) {
        if(!Settings.System.putInt(context.getContentResolver(),key,value)) throw new IllegalStateException("系统未接受旋转设置");
    }
    public void begin() {
        if(!canWrite()) throw new SecurityException("请先允许修改系统设置");
        if(!prefs.store.getBoolean("session",false)) {
            // Commit before taking ownership, so a killed process can recover on the next launch.
            boolean saved=prefs.store.edit().putBoolean("session",true)
                .putInt("saved_auto",read(Settings.System.ACCELEROMETER_ROTATION,1))
                .putInt("saved_rotation",read(Settings.System.USER_ROTATION,0))
                .putInt("last_rotation",rotation()).commit();
            if(!saved) throw new IllegalStateException("无法保存原有旋转设置");
        }
        try { apply(rotation()); } catch(RuntimeException e) { restore();throw e; }
    }
    private void apply(int rotation) {
        // Write the selected angle before disabling the sensor to avoid a visible jump.
        write(Settings.System.USER_ROTATION,rotation);
        prefs.store.edit().putInt("last_rotation",rotation).commit();
        write(Settings.System.ACCELEROMETER_ROTATION,0);
    }
    public int toggle() {
        android.graphics.Point size=new android.graphics.Point();display.getRealSize(size);int r=rotation();
        boolean natural=RotationPolicy.naturalLandscape(size.x,size.y,r);
        int target=RotationPolicy.toggleTarget(r,natural,prefs.reverseLandscape());
        apply(target);return target;
    }
    public void restore() {
        if(!prefs.store.getBoolean("session",false)) return;
        if(!canWrite()) return; // Keep recovery record until access returns; never silently forget it.
        int auto=read(Settings.System.ACCELEROMETER_ROTATION,1);
        int angle=read(Settings.System.USER_ROTATION,0);
        if(RotationPolicy.ownsSettings(auto,angle,prefs.store.getInt("last_rotation",-1))) {
            write(Settings.System.USER_ROTATION,prefs.store.getInt("saved_rotation",0));
            write(Settings.System.ACCELEROMETER_ROTATION,prefs.store.getInt("saved_auto",1));
        }
        // Respect a newer choice made by the user in system controls.
        prefs.store.edit().remove("session").remove("saved_auto").remove("saved_rotation").remove("last_rotation").commit();
    }
}
