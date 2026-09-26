package de.xianmu.arotation.system;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import de.xianmu.arotation.R;

/** Public settings APIs only. OEM background policy remains a user-controlled system setting. */
public final class BackgroundSettings {
    private BackgroundSettings() {}

    public record State(boolean batteryExempt, boolean backgroundRestricted, boolean powerSave) {}

    public static State read(Activity activity) {
        PowerManager power = activity.getSystemService(PowerManager.class);
        ActivityManager manager = activity.getSystemService(ActivityManager.class);
        return new State(power.isIgnoringBatteryOptimizations(activity.getPackageName()),
                manager.isBackgroundRestricted(), power.isPowerSaveMode());
    }

    public static void open(Activity activity, String action) {
        Intent target = new Intent(action);
        if (Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(action)) {
            target.setData(Uri.parse("package:" + activity.getPackageName()));
        }
        try {
            activity.startActivity(target);
        } catch (ActivityNotFoundException | SecurityException missing) {
            if (!Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(action)) {
                open(activity, Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            } else {
                Toast.makeText(activity, R.string.background_settings_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }
}
