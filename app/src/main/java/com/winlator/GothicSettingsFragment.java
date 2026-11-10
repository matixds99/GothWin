package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.box64.Box64EditPresetDialog;
import com.winlator.box64.Box64PresetManager;
import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.GPUHelper;
import com.winlator.core.GeneralComponents;
import com.winlator.core.KeyValueSet;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ekran ustawień kontenera Gothica (otwierany zębatką w launcherze). */
public class GothicSettingsFragment extends Fragment {
    private static final String PREF_CONTAINER_ID = "gothic_container_id";
    public static final String PREF_STRETCH_SCREEN = "gothic_stretch_screen";
    public static final String PREF_CONTROLS_PROFILE = "gothic_controls_profile";
    public static final String EXTRA_CONTROLS_PROFILE = "gothic_controls_profile";

    private static final String[] RESOLUTIONS = {"800x600", "960x540", "1280x720", "1600x900", "1920x1080"};
    private static final String[] AUDIO_IDS = {AudioDrivers.ALSA, AudioDrivers.PULSEAUDIO};
    private static final String[] AUDIO_NAMES = {"ALSA", "PulseAudio"};
    private static final String[] VULKAN_IDS = {GraphicsDrivers.TURNIP, GraphicsDrivers.VORTEK};
    private static final String[] WINCOMPONENT_NAMES = {"Wine (wbudowany)", "Windows (natywny)"};

    private SharedPreferences preferences;
    private Container container;
    private LinearLayout llSettings;

    private Spinner sResolution;
    private Switch swStretch;
    private Spinner sVulkan;
    private Spinner sDxvk;
    private Spinner sAudio;
    private Spinner sDirectSound;
    private Spinner sDirectMusic;
    private Spinner sBox64Preset;
    private Spinner sControlsProfile;
    private Switch swEsync;

    private ArrayList<Box64Preset> box64Presets;
    private ArrayList<ControlsProfile> controlsProfiles;
    private ArrayList<String> dxvkVersions;
    private boolean selectedPresetEdited;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gothic_settings, parent, false);
        Context context = requireContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        llSettings = view.findViewById(R.id.llSettings);

        int containerId = preferences.getInt(PREF_CONTAINER_ID, -1);
        container = new ContainerManager(context).getContainerById(containerId);
        if (container == null) {
            Toast.makeText(context, "Najpierw utwórz kontener gry.", Toast.LENGTH_LONG).show();
            goBack();
            return view;
        }

        buildRows(context);

        Button btnSave = view.findViewById(R.id.btnSave);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnSave.setOnClickListener(v -> save());
        btnCancel.setOnClickListener(v -> goBack());
        return view;
    }

    private void buildRows(Context context) {
        // --- OBRAZ ---
        addSection("OBRAZ");

        ArrayList<String> resolutions = new ArrayList<>(Arrays.asList(RESOLUTIONS));
        String currentResolution = container.getScreenSize();
        if (!resolutions.contains(currentResolution)) resolutions.add(0, currentResolution);
        sResolution = addSpinnerRow("Rozdzielczość", resolutions, resolutions.indexOf(currentResolution));

        swStretch = addSwitchRow("Rozciągnięty ekran", "Obraz gry wypełnia cały ekran telefonu",
            preferences.getBoolean(PREF_STRETCH_SCREEN, true));

        String[] drivers = GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver());
        ArrayList<String> vulkanNames = new ArrayList<>();
        int vulkanIndex = 0;
        for (int i = 0; i < VULKAN_IDS.length; i++) {
            vulkanNames.add(GraphicsDrivers.getName(VULKAN_IDS[i]));
            if (VULKAN_IDS[i].equals(drivers[0])) vulkanIndex = i;
        }
        sVulkan = addSpinnerRow("Sterownik grafiki", vulkanNames, vulkanIndex);

        dxvkVersions = GeneralComponents.getBuiltinComponentNames(GeneralComponents.Type.DXVK);
        dxvkVersions.addAll(GeneralComponents.getInstalledComponentNames(GeneralComponents.Type.DXVK, context));
        dxvkVersions.sort((a, b) -> Integer.compare(GPUHelper.vkMakeVersion(a), GPUHelper.vkMakeVersion(b)));
        String currentDxvk = getDxvkConfigSet().get("version");
        if (currentDxvk.isEmpty()) currentDxvk = DefaultVersion.DXVK(drivers[0]);
        int dxvkIndex = Math.max(0, dxvkVersions.indexOf(currentDxvk));
        sDxvk = addSpinnerRow("Wersja DXVK", dxvkVersions, dxvkIndex);

        // --- DŹWIĘK ---
        addSection("DŹWIĘK");

        int audioIndex = 0;
        for (int i = 0; i < AUDIO_IDS.length; i++) if (AUDIO_IDS[i].equals(container.getAudioDriver())) audioIndex = i;
        sAudio = addSpinnerRow("Sterownik dźwięku", Arrays.asList(AUDIO_NAMES), audioIndex);

        KeyValueSet wincomponents = new KeyValueSet(container.getWinComponents());
        sDirectSound = addSpinnerRow("DirectSound (dsound)", Arrays.asList(WINCOMPONENT_NAMES),
            wincomponents.get("directsound").equals("1") ? 1 : 0);
        sDirectMusic = addSpinnerRow("DirectMusic (dmusic)", Arrays.asList(WINCOMPONENT_NAMES),
            wincomponents.get("directmusic").equals("1") ? 1 : 0);

        // --- WYDAJNOŚĆ ---
        addSection("WYDAJNOŚĆ");

        sBox64Preset = addSpinnerRow("Preset Box64", new ArrayList<>(), 0);
        loadBox64Presets(container.getBox64Preset());
        addActionRow(getString(R.string.gothic_add_preset), () -> editBox64Preset(null),
                getString(R.string.gothic_edit_preset), () -> editBox64Preset(getSelectedBox64PresetId()),
                getString(R.string.gothic_duplicate_preset), this::duplicateBox64Preset);

        swEsync = addSwitchRow("WINEESYNC", "Synchronizacja Wine przez eventfd",
            new EnvVars(container.getEnvVars()).get("WINEESYNC").equals("1"));

        addSection(getString(R.string.gothic_controls_section));
        sControlsProfile = addSpinnerRow(getString(R.string.gothic_controls_profile), new ArrayList<>(), 0);
        loadControlsProfiles(preferences.getInt(PREF_CONTROLS_PROFILE, 0));
        addActionRow(getString(R.string.gothic_manage_controls), this::manageControlsProfiles, null, null, null, null);
        addHint(getString(R.string.gothic_editors_save_immediately));
    }

    private String getSelectedBox64PresetId() {
        int position = sBox64Preset.getSelectedItemPosition();
        return position >= 0 && position < box64Presets.size() ? box64Presets.get(position).id : null;
    }

    private void loadBox64Presets(String selectedId) {
        box64Presets = Box64PresetManager.getPresets(requireContext());
        ArrayList<String> names = new ArrayList<>();
        int selected = -1;
        for (int i = 0; i < box64Presets.size(); i++) {
            Box64Preset preset = box64Presets.get(i);
            names.add(preset.name);
            if (preset.id.equals(selectedId)) selected = i;
        }
        if (selected < 0 && selectedId != null) {
            selected = names.size();
            box64Presets.add(new Box64Preset(selectedId, getString(R.string.gothic_missing_profile_name, selectedId)));
            names.add(box64Presets.get(selected).name);
            Toast.makeText(requireContext(), R.string.gothic_missing_preset, Toast.LENGTH_LONG).show();
        }
        sBox64Preset.setAdapter(new DarkAdapter(requireContext(), names));
        sBox64Preset.setSelection(Math.max(0, selected));
    }

    private void editBox64Preset(@Nullable String presetId) {
        if (presetId != null) {
            Box64Preset preset = Box64PresetManager.getPreset(requireContext(), presetId);
            if (preset == null) {
                Toast.makeText(requireContext(), R.string.gothic_missing_preset, Toast.LENGTH_LONG).show();
                return;
            }
            if (!preset.isCustom()) {
                Toast.makeText(requireContext(), R.string.gothic_builtin_preset_readonly, Toast.LENGTH_LONG).show();
            }
        }
        String newId = Box64Preset.CUSTOM + "-" + Box64PresetManager.getNextPresetId(requireContext());
        Box64EditPresetDialog dialog = new Box64EditPresetDialog(requireContext(), presetId);
        dialog.setOnConfirmCallback(() -> {
            String selectedId = presetId != null ? presetId : newId;
            if (presetId != null && presetId.equals(container.getBox64Preset())
                    && Box64PresetManager.getPreset(requireContext(), presetId).isCustom()) {
                selectedPresetEdited = true;
            }
            loadBox64Presets(selectedId);
        });
        dialog.show();
    }

    private void duplicateBox64Preset() {
        String selectedId = getSelectedBox64PresetId();
        if (selectedId == null || Box64PresetManager.getPreset(requireContext(), selectedId) == null) {
            Toast.makeText(requireContext(), R.string.gothic_missing_preset, Toast.LENGTH_LONG).show();
            return;
        }
        String newId = Box64Preset.CUSTOM + "-" + Box64PresetManager.getNextPresetId(requireContext());
        Box64PresetManager.duplicatePreset(requireContext(), selectedId);
        loadBox64Presets(newId);
    }

    private int getSelectedControlsProfileId() {
        int position = sControlsProfile.getSelectedItemPosition();
        return position > 0 && position <= controlsProfiles.size() ? controlsProfiles.get(position - 1).id : 0;
    }

    private void loadControlsProfiles(int selectedId) {
        controlsProfiles = new InputControlsManager(requireContext()).getProfiles(true);
        ArrayList<String> names = new ArrayList<>();
        names.add(getString(R.string.gothic_no_controls));
        int selected = 0;
        for (int i = 0; i < controlsProfiles.size(); i++) {
            ControlsProfile profile = controlsProfiles.get(i);
            names.add(profile.getName());
            if (profile.id == selectedId) selected = i + 1;
        }
        if (selectedId > 0 && selected == 0) {
            ControlsProfile missingProfile = new ControlsProfile(requireContext(), selectedId);
            missingProfile.setName(getString(R.string.gothic_missing_profile_name, String.valueOf(selectedId)));
            controlsProfiles.add(missingProfile);
            names.add(missingProfile.getName());
            selected = controlsProfiles.size();
            Toast.makeText(requireContext(), R.string.gothic_missing_controls, Toast.LENGTH_LONG).show();
        }
        sControlsProfile.setAdapter(new DarkAdapter(requireContext(), names));
        sControlsProfile.setSelection(selected);
    }

    private void manageControlsProfiles() {
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.putExtra("edit_input_controls", true);
        intent.putExtra("selected_profile_id", getSelectedControlsProfileId());
        startActivityForResult(intent, MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && sControlsProfile != null) {
            int selectedId = resultCode == Activity.RESULT_OK && data != null
                    ? data.getIntExtra("selected_profile_id", 0) : getSelectedControlsProfileId();
            loadControlsProfiles(selectedId);
        }
    }

    private KeyValueSet getDxvkConfigSet() {
        String config = container.getDXWrapperConfig();
        int separator = config.indexOf("|");
        return new KeyValueSet(separator != -1 ? config.substring(0, separator) : config);
    }

    private void save() {
        if (container == null) return;

        String presetId = getSelectedBox64PresetId();
        if (presetId == null || Box64PresetManager.getPreset(requireContext(), presetId) == null) {
            Toast.makeText(requireContext(), R.string.gothic_missing_preset, Toast.LENGTH_LONG).show();
            return;
        }
        int controlsId = getSelectedControlsProfileId();
        if (controlsId > 0) {
            boolean found = false;
            for (ControlsProfile profile : new InputControlsManager(requireContext()).getProfiles(true)) {
                if (profile.id == controlsId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Toast.makeText(requireContext(), R.string.gothic_missing_controls, Toast.LENGTH_LONG).show();
                return;
            }
        }

        EnvVars overrides = new EnvVars(container.getEnvVars());
        EnvVars presetVars = Box64PresetManager.getEnvVars(requireContext(), presetId);
        ArrayList<String> conflictingVars = new ArrayList<>();
        if (!presetId.equals(container.getBox64Preset()) || selectedPresetEdited) {
            for (String name : presetVars) if (overrides.has(name)) conflictingVars.add(name);
        }
        if (!conflictingVars.isEmpty()) {
            ContentDialog dialog = new ContentDialog(requireContext());
            dialog.setMessage(getString(R.string.gothic_preset_override_warning, String.join(", ", conflictingVars)));
            dialog.setOnConfirmCallback(() -> saveSettings(presetId, conflictingVars));
            dialog.show();
        } else {
            saveSettings(presetId, conflictingVars);
        }
    }

    private void saveSettings(String presetId, List<String> conflictingVars) {
        container.setScreenSize(sResolution.getSelectedItem().toString());

        String[] drivers = GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver());
        String vulkan = VULKAN_IDS[sVulkan.getSelectedItemPosition()];
        container.setGraphicsDriver(vulkan + "," + drivers[1]);

        // Zmieniamy tylko wersję DXVK; reszta konfiguracji (np. po znaku "|") zostaje bez zmian.
        String config = container.getDXWrapperConfig();
        int separator = config.indexOf("|");
        String tail = separator != -1 ? config.substring(separator) : "";
        KeyValueSet dxvkConfig = getDxvkConfigSet();
        dxvkConfig.put("version", dxvkVersions.get(sDxvk.getSelectedItemPosition()));
        container.setDXWrapperConfig(dxvkConfig.toString() + tail);

        container.setAudioDriver(AUDIO_IDS[sAudio.getSelectedItemPosition()]);

        KeyValueSet wincomponents = new KeyValueSet(container.getWinComponents());
        wincomponents.put("directsound", sDirectSound.getSelectedItemPosition());
        wincomponents.put("directmusic", sDirectMusic.getSelectedItemPosition());
        container.setWinComponents(wincomponents.toString());

        container.setBox64Preset(presetId);

        EnvVars envVars = new EnvVars(container.getEnvVars());
        for (String name : conflictingVars) envVars.remove(name);
        envVars.put("WINEESYNC", swEsync.isChecked() ? "1" : "0");
        container.setEnvVars(envVars.toString());

        container.saveData();
        preferences.edit().putBoolean(PREF_STRETCH_SCREEN, swStretch.isChecked())
                .putInt(PREF_CONTROLS_PROFILE, getSelectedControlsProfileId()).apply();

        Toast.makeText(requireContext(), "Zapisano ustawienia", Toast.LENGTH_SHORT).show();
        goBack();
    }

    private void goBack() {
        if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).showFragment(new GothicLauncherFragment());
    }

    // ---------------------------------------------------------------- UI helpers

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addSection(String title) {
        TextView tv = new TextView(requireContext());
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#8995A5"));
        tv.setTextSize(10);
        tv.setLetterSpacing(0.13f);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(2);
        llSettings.addView(tv, lp);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_launcher_status);
        row.setMinimumHeight(dp(54));
        row.setPadding(dp(14), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        llSettings.addView(row, lp);
        return row;
    }

    private TextView newLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#E6E2D8"));
        tv.setTextSize(14);
        return tv;
    }

    private Spinner addSpinnerRow(String label, List<String> items, int selected) {
        LinearLayout row = newRow();
        row.addView(newLabel(label), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Spinner spinner = new Spinner(requireContext());
        spinner.setPopupBackgroundDrawable(new ColorDrawable(Color.parseColor("#1A1D23")));
        spinner.setAdapter(new DarkAdapter(requireContext(), new ArrayList<>(items)));
        spinner.setSelection(Math.max(0, selected));
        row.addView(spinner, new LinearLayout.LayoutParams(dp(170), dp(44)));
        return spinner;
    }

    private Switch addSwitchRow(String label, String hint, boolean checked) {
        LinearLayout row = newRow();

        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(newLabel(label));
        TextView tvHint = new TextView(requireContext());
        tvHint.setText(hint);
        tvHint.setTextColor(Color.parseColor("#7D8795"));
        tvHint.setTextSize(11);
        texts.addView(tvHint);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(requireContext());
        sw.setChecked(checked);
        row.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sw;
    }

    private void addActionRow(String first, Runnable firstAction, @Nullable String second,
                              @Nullable Runnable secondAction, @Nullable String third, @Nullable Runnable thirdAction) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        llSettings.addView(row, rowParams);
        addActionButton(row, first, firstAction);
        if (second != null && secondAction != null) addActionButton(row, second, secondAction);
        if (third != null && thirdAction != null) addActionButton(row, third, thirdAction);
    }

    private void addActionButton(LinearLayout row, String label, Runnable action) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextColor(Color.parseColor("#E4D2AA"));
        button.setTextSize(12);
        button.setTextAllCaps(false);
        button.setMinWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackgroundResource(R.drawable.bg_launcher_secondary_button);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        row.addView(button, params);
    }

    private void addHint(String text) {
        TextView hint = new TextView(requireContext());
        hint.setText(text);
        hint.setTextColor(Color.parseColor("#A4AFBC"));
        hint.setTextSize(12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        llSettings.addView(hint, params);
    }

    /** Adapter spinnera w ciemnych kolorach launchera. */
    private static class DarkAdapter extends ArrayAdapter<String> {
        DarkAdapter(Context context, List<String> items) {
            super(context, android.R.layout.simple_spinner_item, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView tv = (TextView)super.getView(position, convertView, parent);
            tv.setTextColor(Color.parseColor("#E4D2AA"));
            tv.setTextSize(14);
            tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            return tv;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView tv = (TextView)super.getDropDownView(position, convertView, parent);
            tv.setTextColor(Color.parseColor("#F3F1EC"));
            tv.setBackgroundColor(Color.parseColor("#1A1D23"));
            tv.setTextSize(14);
            tv.setPadding(dpToPx(parent, 16), dpToPx(parent, 12), dpToPx(parent, 16), dpToPx(parent, 12));
            return tv;
        }

        private static int dpToPx(View view, int dp) {
            return Math.round(dp * view.getResources().getDisplayMetrics().density);
        }
    }
}
