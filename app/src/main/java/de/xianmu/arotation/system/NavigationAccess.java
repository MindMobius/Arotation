package de.xianmu.arotation.system;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import de.xianmu.arotation.R;

/** Public system settings entry; never writes accessibility settings on the user's behalf. */
public final class NavigationAccess {
    private NavigationAccess(){}
    public static void open(Activity activity) {
        try{activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
        catch(ActivityNotFoundException failure){Toast.makeText(activity,R.string.navigation_settings_unavailable,Toast.LENGTH_LONG).show();}
    }
}
