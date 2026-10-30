package com.cinetv.launcher;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.cinetv.launcher.device.DeviceTweaks;

/**
 * On MediaTek TVs the firmware starts its tuner app over Home right after power-on, showing "please
 * scan channels" until boot finishes (~20 s). Our process starts with Home, long before the key
 * service is bound, so it re-fronts Home a few times during that window. Needs the display-over-apps
 * permission the installer grants; the user pressing an input key cancels it.
 */
public class CineTvApp extends Application {

    private static volatile CineTvApp appInstance;
    private static final long[] BOOT_RESCUES_MS = {1500, 3500, 6000, 9000, 13000};

    public static CineTvApp get() {
        return appInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appInstance = this;
        com.cinetv.launcher.service.PosterCache.configure(com.cinetv.launcher.device.PerformanceProfile.lowMemory(this));
        HomeGuard.install(this);
        // The phone remote works whatever is on screen: its server and key bridge live with the process
        // (kept alive by the remote-button service), not with the home screen.
        // An update that changes the remote-button service's capabilities makes Android switch it off:
        // switch it back on (CineTV holds WRITE_SECURE_SETTINGS).
        DeviceTweaks.ensureKeyService(this);
        com.cinetv.launcher.remote.TvWebRemoteServer.start(this);
        com.cinetv.launcher.device.NightCare.start(this);
        new Thread(() -> com.cinetv.launcher.remote.KeyBridge.warmUp(this), "cinetv-keys").start();
        if (SystemClock.elapsedRealtime() > DeviceTweaks.BOOT_GUARD_MS) return;
        Handler h = new Handler(Looper.getMainLooper());
        for (long delay : BOOT_RESCUES_MS) {
            h.postDelayed(() -> {
                if (!DeviceTweaks.isUnwantedBootTuner() || MainActivity.isInFront()) return;
                try {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                } catch (Throwable ignored) {
                }
            }, delay);
        }
    }

    /** Home is behind another app, or memory is short: drop every decoded picture; they reload from disk when needed. */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            com.cinetv.launcher.service.PosterCache.trimMemory();
            com.cinetv.launcher.ui.Glow.clear();
        }
    }
}
