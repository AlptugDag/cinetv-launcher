package com.cinetv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Wakes up CineTV as soon as the TV finishes booting or powers on from quick standby,
 * ensuring the launcher activates even on TVs where it is not yet the default home app.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "CineTvBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || com.cinetv.launcher.device.Uninstaller.isRemoving(context)) return;
        String action = intent.getAction();
        Log.i(TAG, "boot action received: " + action);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            com.cinetv.launcher.device.DeviceTweaks.ensureKeyService(context);
            HomeGuard.runUpdateOptimizationsAsync(context);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            // The TV's tuner app is left running: killing it at boot made the TV restart it (3 times, the heaviest
            // start there is), which slowed the whole power-on. CineTV simply comes to the front.
            Intent launch = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                context.startActivity(launch);
            } catch (Throwable t) {
                Log.w(TAG, "failed to start on boot: " + t);
            }
            // Aggressive follow-up reinforcements to suppress OEM launchers (Android 9, 11, 14 TV boxes)
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            long[] delays = {2500L}; // one follow-up is enough; four launches in 4.5 s weighed on the TV's own boot
            for (long delay : delays) {
                h.postDelayed(() -> {
                    try {
                        Intent followUp = new Intent(context, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        context.startActivity(followUp);
                    } catch (Throwable ignored) {
                    }
                }, delay);
            }
            // the setup check (adb) waits until the TV has finished starting: HomeGuard.install runs it later, once
            com.cinetv.launcher.device.MemoryOptimizer.ensureAirPlayReceiver(context);
            com.cinetv.launcher.service.AppUpdater.checkAndAutoUpdateOnBoot(context);
        }
    }
}
