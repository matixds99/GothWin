package com.winlator.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.box64.Box64Utils;
import com.winlator.core.CPUStatus;
import com.winlator.core.StringUtils;

import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    public enum Mode {DISABLED, SIMPLE, FULL}
    private long lastTime = 0;
    private short frameCount = 0;
    private float lastFPS = 0;
    private final LinearLayout fpsPanel;
    private final LinearLayout gpuPanel;
    private final LinearLayout ramPanel;
    private final LinearLayout cpuPanel;
    private final LinearLayout batteryPanel;
    private Mode mode = Mode.SIMPLE;
    private ActivityManager activityManager;
    private ActivityManager.MemoryInfo memoryInfo;
    private String cpuInfo = null;
    private byte tick = 0;
    private String gpuInfo = "N/A";

    public FrameRating(Context context) {
        this(context, null);
    }

    public FrameRating(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameRating(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        fpsPanel = view.findViewById(R.id.LLFPSPanel);
        gpuPanel = view.findViewById(R.id.LLGPUPanel);
        ramPanel = view.findViewById(R.id.LLRAMPanel);
        cpuPanel = view.findViewById(R.id.LLCPUPanel);
        batteryPanel = view.findViewById(R.id.LLBatteryPanel);
        addView(view);
        setupPanels();
    }

    private void setupPanels() {
        switch (mode) {
            case DISABLED:
                fpsPanel.setVisibility(GONE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);
                batteryPanel.setVisibility(GONE);
                
                activityManager = null;
                memoryInfo = null;
                break;
            case SIMPLE:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);
                batteryPanel.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case FULL:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(VISIBLE);
                ramPanel.setVisibility(VISIBLE);
                cpuPanel.setVisibility(VISIBLE);
                batteryPanel.setVisibility(VISIBLE);

                Context context = getContext();
                activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                memoryInfo = new ActivityManager.MemoryInfo();
                break;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setupPanels();
        tick = 2;
        if (mode == Mode.DISABLED) setVisibility(GONE);
        else if (lastTime != 0) post(this);
    }

    public void setGPUInfo(String gpuInfo) {
        this.gpuInfo = gpuInfo;
        post(() -> ((TextView)gpuPanel.getChildAt(1)).setText(gpuInfo));
    }

    public void reset() {
        frameCount = 0;
        lastTime = SystemClock.elapsedRealtime();
        lastFPS = 0;
        tick = 2;
    }

    public void update() {
        if (mode == Mode.DISABLED) return;
        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            post(this);
            lastTime = time;
            frameCount = 0;
        }

        frameCount++;
    }

    @Override
    public void run() {
        if (mode == Mode.DISABLED) return;
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        ((TextView)fpsPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));

        if (mode == Mode.FULL && ++tick >= 2) {
            tick = 0;
            ((TextView)gpuPanel.getChildAt(1)).setText(gpuInfo);
            updateBattery();
            activityManager.getMemoryInfo(memoryInfo);
            long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
            String ramText = StringUtils.formatBytes(usedMem, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem);
            ((TextView)ramPanel.getChildAt(1)).setText(ramText);

            if (cpuInfo == null) {
                cpuInfo = "Box64 v"+ Box64Utils.extractBinVersion(cpuPanel.getContext());
            }

            short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
            int maxClockSpeed = 0;
            for (short clockSpeed : clockSpeeds) maxClockSpeed = Math.max(maxClockSpeed, clockSpeed);
            ((TextView)cpuPanel.getChildAt(1)).setText(CPUStatus.formatClockSpeed(maxClockSpeed)+" | "+cpuInfo);
        }
    }

    private void updateBattery() {
        Intent intent = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        boolean charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        String text = (level >= 0 ? (level * 100 / scale) + "%" : "?") + (charging ? "+" : "") +
            " | " + String.format(Locale.ENGLISH, "%.1f°C", temp / 10f);
        ((TextView)batteryPanel.getChildAt(1)).setText(text);
    }
}
