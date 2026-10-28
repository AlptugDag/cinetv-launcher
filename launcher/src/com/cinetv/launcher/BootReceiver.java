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
            com.cinetv.launcher.service.AppUpdater.checkAndAutoUpdateOnBoot(context);
        }
    }
}
