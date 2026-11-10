package com.winlator;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Shortcut;
import com.winlator.core.EnvVars;
import com.winlator.core.GeneralComponents;
import com.winlator.core.KeyValueSet;
import com.winlator.core.WineUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/** Launcher for the Gothic container. The selected EXE is added from the container
 * file manager using its "Dodaj skrót" context-menu action. */
public class GothicLauncherFragment extends Fragment {
    private static final String PREF_CONTAINER_ID = "gothic_container_id";
    private static final String EXPECTED_SHORTCUT_NAME = "Gothic2.desktop";

    private SharedPreferences preferences;
    private Button btnConfigure;
    private ImageButton btnSettings;
    private ImageButton btnChangeLocation;
    private Button btnAction;
    private TextView tvGameStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_gothic_launcher, parent, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        btnConfigure = view.findViewById(R.id.btnConfigure);
        btnSettings = view.findViewById(R.id.btnSettings);
        btnChangeLocation = view.findViewById(R.id.btnChangeLocation);
        btnAction = view.findViewById(R.id.btnAction);
        tvGameStatus = view.findViewById(R.id.tvGameStatus);

        btnConfigure.setOnClickListener(v -> createGothicContainer());
        btnSettings.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity)getActivity()).showFragment(new GothicSettingsFragment());
        });
        btnChangeLocation.setOnClickListener(v -> {
            Container container = getGothicContainer();
            if (container != null) openContainerFileManager(container.id);
        });
        btnAction.setOnClickListener(v -> onMainAction());
        updateUIStates();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUIStates();
    }

    private Container getGothicContainer() {
        if (getContext() == null || preferences == null) return null;
        int containerId = preferences.getInt(PREF_CONTAINER_ID, -1);
        return new ContainerManager(requireContext()).getContainerById(containerId);
    }

    /** The actual container desktop is inside the container root, not files/containers/<id>. */
    @Nullable
    private File findGameShortcut(Container container) {
        if (container == null) return null;
        File desktopDir = new File(container.getUserDir(), "Desktop");
        File expected = new File(desktopDir, EXPECTED_SHORTCUT_NAME);
        if (expected.isFile()) return expected;

        File[] entries = desktopDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".desktop"));
        if (entries == null || entries.length == 0) return null;

        // This launcher is dedicated to Gothic. Prefer a shortcut whose name mentions it;
        // otherwise use the sole available desktop shortcut created from the selected EXE.
        for (File entry : entries) {
            if (entry.getName().toLowerCase(Locale.ROOT).contains("gothic")) return entry;
        }
        return entries.length == 1 ? entries[0] : null;
    }

    private void updateUIStates() {
        if (!isAdded() || btnConfigure == null || btnAction == null || tvGameStatus == null) return;

        Container container = getGothicContainer();
        File shortcut = findGameShortcut(container);
        // Zębatka ustawień jest dostępna dopiero, gdy kontener istnieje.
        if (btnSettings != null) btnSettings.setVisibility(container != null ? View.VISIBLE : View.GONE);
        if (btnChangeLocation != null) btnChangeLocation.setVisibility(container != null ? View.VISIBLE : View.GONE);
        if (container == null) {
            btnConfigure.setText("Konfiguruj");
            btnConfigure.setEnabled(true);
            btnConfigure.setTextColor(Color.parseColor("#D8C79F"));
            btnAction.setEnabled(false);
            btnAction.setText("Utwórz kontener gry");
            btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#343944")));
            tvGameStatus.setText("Najpierw skonfiguruj środowisko gry.");
            tvGameStatus.setTextColor(Color.parseColor("#E5B765"));
        } else {
            btnConfigure.setText("✓ Gotowy");
            btnConfigure.setEnabled(false);
            btnConfigure.setTextColor(Color.parseColor("#82C99B"));
            btnAction.setEnabled(true);

            if (shortcut == null) {
                btnAction.setText("Wybierz plik EXE gry");
                btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#426D9D")));
                tvGameStatus.setText("Środowisko gotowe · dodaj skrót do pliku EXE, aby rozpocząć.");
                tvGameStatus.setTextColor(Color.parseColor("#E5B765"));
            } else {
                btnAction.setText("▶   URUCHOM GOTHIC");
                btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#92703B")));
                tvGameStatus.setText("GRA GOTOWA   ·   Skrót: " + shortcut.getName());
                tvGameStatus.setTextColor(Color.parseColor("#82C99B"));
            }
        }
    }

    private void onMainAction() {
        Container container = getGothicContainer();
        if (container == null) {
            Toast.makeText(requireContext(), "Najpierw utwórz kontener gry", Toast.LENGTH_SHORT).show();
            return;
        }

        File shortcut = findGameShortcut(container);
        if (shortcut == null) {
            openContainerFileManager(container.id);
        } else {
            launchGameShortcut(container, shortcut);
        }
    }

    private void launchGameShortcut(Container container, File shortcut) {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("stretch_screen", preferences.getBoolean(GothicSettingsFragment.PREF_STRETCH_SCREEN, true));
        intent.putExtra(GothicSettingsFragment.EXTRA_CONTROLS_PROFILE,
                preferences.getInt(GothicSettingsFragment.PREF_CONTROLS_PROFILE, 0));

        // Uruchamiamy dokładnie tak samo jak przy pierwszym starcie po wyborze EXE
        // (exec_path), bo ta ścieżka działa. Parsowanie samego .desktop zostaje jako zapas.
        String execUnixPath = preferences.getString("gothic_exe_unix_path", null);
        if (execUnixPath != null && !new File(execUnixPath).isFile()) execUnixPath = null;

        if (execUnixPath == null) {
            try {
                Shortcut parsed = new Shortcut(container, shortcut);
                String dosPath = parsed.path;
                if (dosPath != null && dosPath.toLowerCase(Locale.ROOT).endsWith(".exe")) {
                    String unixPath = WineUtils.dosToUnixPath(dosPath, container);
                    if (unixPath != null && !unixPath.isEmpty()) execUnixPath = unixPath;
                }
            } catch (Exception ignored) {}
        }

        if (execUnixPath != null) intent.putExtra("exec_path", execUnixPath);
        else intent.putExtra("shortcut_path", shortcut.getAbsolutePath());
        Toast.makeText(requireContext(), execUnixPath != null ? "Start: exec_path" : "Start: shortcut_path", Toast.LENGTH_SHORT).show();
        startActivity(intent);
    }

    private void createGothicContainer() {
        if (!isAdded()) return;
        btnConfigure.setEnabled(false);

        MainActivity activity = (MainActivity) getActivity();
        Context context = requireContext();
        try {
            if (activity != null) activity.preloaderDialog.show(R.string.starting_up);

            ContainerManager manager = new ContainerManager(context);
            JSONObject data = new JSONObject();
            data.put("name", "Gothic II - New Balance");
            data.put("screenSize", "1280x720");
            data.put("audioDriver", AudioDrivers.PULSEAUDIO);
            data.put("graphicsDriver", GraphicsDrivers.VORTEK + "," + GraphicsDrivers.DEFAULT_OPENGL_DRIVER);
            data.put("box64Preset", Box64Preset.PERFORMANCE);

            // DXVK 2.3.1 nie jest w paczce aplikacji (są tylko 1.10.3 i 2.4.1), więc ustawiamy
            // go tylko wtedy, gdy został wcześniej doinstalowany; inaczej zostaje wersja domyślna.
            data.put("dxwrapper", DXWrappers.DXVK);
            File dxvk231 = new File(GeneralComponents.getComponentDir(GeneralComponents.Type.DXVK, context), "dxvk-2.3.1.tzst");
            if (dxvk231.isFile()) data.put("dxwrapperConfig", "version=2.3.1,framerate=0");

            // DirectSound i DirectMusic: natywne Windows (1); pozostałe składniki domyślne.
            KeyValueSet wincomponents = new KeyValueSet(Container.DEFAULT_WINCOMPONENTS);
            wincomponents.put("directsound", "1");
            wincomponents.put("directmusic", "1");
            data.put("wincomponents", wincomponents.toString());

            // Domyślne zmienne Winlatora + WINEESYNC wyłączone + ustawienia Box64.
            EnvVars envVars = new EnvVars(Container.DEFAULT_ENV_VARS);
            envVars.put("WINEESYNC", "0");
            envVars.put("BOX64_DYNAREC_BIGBLOCK", "2");
            envVars.put("BOX64_DYNAREC_STRONG_MEM", "0");
            data.put("envVars", envVars.toString());

            manager.createContainerAsync(data, container -> {
                if (!isAdded()) return;
                if (activity != null) {
                    try { activity.preloaderDialog.close(); } catch (Exception ignored) {}
                }

                if (container == null) {
                    Toast.makeText(context, "Nie udało się utworzyć kontenera!", Toast.LENGTH_LONG).show();
                    btnConfigure.setEnabled(true);
                    updateUIStates();
                    return;
                }

                preferences.edit().putInt(PREF_CONTAINER_ID, container.id).apply();
                Toast.makeText(context, "Kontener utworzony pomyślnie", Toast.LENGTH_SHORT).show();
                updateUIStates();
                openContainerFileManager(container.id);
            });
        } catch (Exception e) {
            if (activity != null) {
                try { activity.preloaderDialog.close(); } catch (Exception ignored) {}
            }
            btnConfigure.setEnabled(true);
            Toast.makeText(context, "Błąd tworzenia kontenera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openContainerFileManager(int containerId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showFragment(new GothicContainerFileManagerFragment(containerId));
        }
    }
}
