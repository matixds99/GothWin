package com.winlator.contentdialog;

import android.content.SharedPreferences;
import android.widget.CheckBox;

import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.widget.FrameRating;

public class FpsHudDialog extends ContentDialog {
    public static final String PREF_HUD_PANELS = "hud_panels";

    public FpsHudDialog(XServerDisplayActivity activity, FrameRating frameRating) {
        super(activity, R.layout.fps_hud_dialog);
        setTitle(R.string.fps_hud);
        setIcon(R.drawable.icon_fps_hud);

        final CheckBox[] checkBoxes = {
            findViewById(R.id.CBHudFps), findViewById(R.id.CBHudGpu), findViewById(R.id.CBHudRam),
            findViewById(R.id.CBHudCpu), findViewById(R.id.CBHudBattery)
        };

        boolean[] panels = frameRating.getPanels();
        for (int i = 0; i < checkBoxes.length; i++) checkBoxes[i].setChecked(panels[i]);

        setOnConfirmCallback(() -> {
            int mask = 0;
            for (int i = 0; i < checkBoxes.length; i++) if (checkBoxes[i].isChecked()) mask |= 1 << i;
            PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt(PREF_HUD_PANELS, mask).apply();
            applyMask(frameRating, mask);
        });
    }

    public static void applyMask(FrameRating frameRating, int mask) {
        frameRating.setPanels((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0);
    }
}
