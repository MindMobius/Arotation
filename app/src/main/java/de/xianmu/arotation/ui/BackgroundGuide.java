package de.xianmu.arotation.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.xianmu.arotation.R;
import de.xianmu.arotation.system.BackgroundSettings;

/** Small, on-demand guide. Reads system state again when returning from Settings. */
public final class BackgroundGuide {
    private final Activity activity;
    private AlertDialog dialog;
    private TextView status;
    private Button saver;

    public BackgroundGuide(Activity activity) { this.activity = activity; }

    public void show() {
        if (dialog != null && dialog.isShowing()) return;
        Palette colors = new Palette(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(8));
        status = text("", 14, colors.ink);
        status.setLineSpacing(dp(6), 1);
        content.addView(status);
        TextView note = text(activity.getString(R.string.background_help), 14, colors.muted);
        note.setPadding(0, dp(18), 0, dp(12));
        note.setLineSpacing(dp(4), 1);
        content.addView(note);
        action(content, R.string.battery_settings, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        action(content, R.string.app_background_settings, Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        saver = action(content, R.string.power_save_settings, Settings.ACTION_BATTERY_SAVER_SETTINGS);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.background_title).setView(scroll)
                .setPositiveButton(R.string.close, null).create();
        refresh();
        dialog.show();
    }

    public void refresh() {
        if (dialog == null) return;
        BackgroundSettings.State state = BackgroundSettings.read(activity);
        status.setText(activity.getString(R.string.background_status,
                activity.getString(state.batteryExempt() ? R.string.battery_exempt : R.string.battery_optimized),
                activity.getString(state.backgroundRestricted() ? R.string.background_restricted : R.string.background_unrestricted)));
        saver.setVisibility(state.powerSave() ? View.VISIBLE : View.GONE);
    }

    public void dismiss() {
        if (dialog != null) { dialog.dismiss(); dialog = null; }
    }

    private Button action(LinearLayout parent, int label, String action) {
        Button button = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setOnClickListener(v -> BackgroundSettings.open(activity, action));
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        return text;
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
