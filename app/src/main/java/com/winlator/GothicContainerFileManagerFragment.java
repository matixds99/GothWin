package com.winlator;

import android.content.Intent;
import android.widget.Toast;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.FileInfo;
import com.winlator.core.FileUtils;
import com.winlator.core.StringUtils;
import com.winlator.core.WineUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/** File manager variant that creates and immediately launches Gothic's direct-EXE shortcut. */
public class GothicContainerFileManagerFragment extends ContainerFileManagerFragment {
    private String selectedExecutablePath;
    private boolean instructionShown;

    public GothicContainerFileManagerFragment() {
        super();
    }

    public GothicContainerFileManagerFragment(int containerId) {
        super(containerId);
    }

    public GothicContainerFileManagerFragment(int containerId, String startPath) {
        super(containerId, startPath);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!instructionShown && isAdded() && getContext() != null) {
            instructionShown = true;
            Toast.makeText(requireContext(), "Utwórz skrót do gry, wybierając plik .exe", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected String getDesktopShortcutName(FileInfo exe) {
        selectedExecutablePath = exe.path;
        return "Gothic2";
    }

    @Override
    protected boolean createWindowsLinkForDesktopShortcut() {
        return false;
    }

    @Override
    protected boolean replaceExistingDesktopShortcut() {
        return true;
    }

    @Override
    protected void onDesktopShortcutCreated(File desktopShortcut) {
        if (!isAdded() || getContext() == null) return;

        Container container = new ContainerManager(requireContext()).getContainerById(getContainerId());
        if (container == null || selectedExecutablePath == null) {
            Toast.makeText(requireContext(), "Nie można odczytać wybranego pliku EXE", Toast.LENGTH_LONG).show();
            return;
        }

        String executableDosPath = WineUtils.unixToDOSPath(selectedExecutablePath, container);
        if (executableDosPath.isEmpty() || !executableDosPath.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            Toast.makeText(requireContext(), "Nieprawidłowa ścieżka do Gothic EXE", Toast.LENGTH_LONG).show();
            return;
        }

        String executableUnixPath = WineUtils.dosToUnixPath(executableDosPath, container);
        File executableDir = new File(executableUnixPath).getParentFile();
        if (executableUnixPath.isEmpty() || executableDir == null) {
            Toast.makeText(requireContext(), "Nie można rozwiązać ścieżki do Gothic EXE", Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<String> entryLines = new ArrayList<>();
        for (String line : FileUtils.readLines(desktopShortcut, true)) {
            if (line.startsWith("Exec=") || line.startsWith("Path=") || line.startsWith("StartupWMClass=")) continue;
            entryLines.add(line);
        }
        entryLines.add("Exec=wine " + StringUtils.escapeDOSPath(executableDosPath));
        entryLines.add("Path=" + executableDir.getAbsolutePath());
        entryLines.add("StartupWMClass=gothic2.exe");
        if (!FileUtils.writeString(desktopShortcut, String.join("\n", entryLines) + "\n")) {
            Toast.makeText(requireContext(), "Nie udało się zapisać skrótu Gothic", Toast.LENGTH_LONG).show();
            return;
        }

        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
            .putString("gothic_exe_unix_path", executableUnixPath).apply();

        Toast.makeText(requireContext(), "Skrót Gothic II utworzony — uruchamiam grę", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireContext(), XServerDisplayActivity.class);
        intent.putExtra("container_id", getContainerId());
        // Launch the exact selected EXE through Winlator's normal exec_path path rather
        // than reparsing the newly written desktop shortcut for the initial launch.
        intent.putExtra("exec_path", executableUnixPath);
        startActivity(intent);
    }
}
