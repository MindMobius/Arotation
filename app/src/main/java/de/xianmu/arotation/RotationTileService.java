package de.xianmu.arotation;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import de.xianmu.arotation.data.Prefs;
import de.xianmu.arotation.ui.IconCatalog;

/** A normal listening tile: SystemUI binds while visible, with no polling or idle work. */
public final class RotationTileService extends TileService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Prefs prefs;
    private boolean listening;
    private long lastClick;
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refreshTile(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
    }

    @Override public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            registerReceiver(stateReceiver, new IntentFilter(RotationService.STATE), Context.RECEIVER_NOT_EXPORTED);
            prefs.store.registerOnSharedPreferenceChangeListener(this);
            listening = true;
        }
        refreshTile();
    }

    @Override public void onStopListening() {
        stopListening();
        super.onStopListening();
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    private void stopListening() {
        if (!listening) return;
        listening = false;
        unregisterReceiver(stateReceiver);
        prefs.store.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences store, String key) {
        if ("icon".equals(key)) refreshTile();
    }

    @Override public void onClick() {
        super.onClick();
        long now = SystemClock.uptimeMillis();
        if (now - lastClick < 600) return;
        lastClick = now;
        if (isLocked()) unlockAndRun(this::toggle);
        else toggle();
    }

    private void toggle() {
        if (RotationService.running) {
            prefs.desired(false);
            stopService(new Intent(this, RotationService.class));
        } else if (!hasAccess()) {
            openSettingsToEnable();
        } else {
            try {
                startForegroundService(new Intent(this, RotationService.class).setAction(RotationService.START));
            } catch (IllegalStateException | SecurityException e) {
                // Some systems restrict background FGS starts even after a tile click.
                // Let the same user action reach our foreground Activity instead.
                Log.w("Arotation", "Tile start needs foreground Activity", e);
                openSettingsToEnable();
            }
        }
        // Never claim success from desired preferences; the service publishes its actual state.
        refreshTile();
    }

    private boolean hasAccess() {
        return Settings.canDrawOverlays(this) && Settings.System.canWrite(this);
    }

    private void refreshTile() {
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = RotationService.running && hasAccess();
        String state = !hasAccess() ? getString(R.string.tile_needs_access)
                : getString(active ? R.string.tile_on : R.string.tile_off);
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.app_name));
        tile.setSubtitle(state);
        tile.setStateDescription(state);
        tile.setContentDescription(getString(R.string.app_name) + "，" + state);
        tile.setIcon(Icon.createWithResource(this, IconCatalog.DRAWABLES[prefs.icon()]));
        tile.updateTile();
    }

    private void openSettingsToEnable() {
        Intent intent = new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_ENABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 21, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startActivityAndCollapse(pending);
    }
}
