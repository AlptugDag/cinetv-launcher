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
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "boot action received: " + action);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Intent launch = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                context.startActivity(launch);
            } catch (Throwable t) {
                Log.w(TAG, "failed to start on boot: " + t);
            }
            // Aggressive follow-up reinforcements to suppress OEM launchers (Android 9, 11, 14 TV boxes)
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            long[] delays = {1000L, 2200L, 4500L};
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
            new Thread(() -> HomeGuard.ensureDefault(context), "cinetv-boot-guard").start();
            com.cinetv.launcher.service.AppUpdater.checkAndAutoUpdateOnBoot(context);
        }
    }
}
