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
            try {
                com.cinetv.launcher.service.Prefs.get(context).edit().remove("silent_install_pending_at").apply();
            } catch (Throwable ignored) {}
            com.cinetv.launcher.device.DeviceTweaks.ensureKeyService(context);
            HomeGuard.runUpdateOptimizationsAsync(context);
            TvKeyInterceptorService svc = TvKeyInterceptorService.getInstance();
            if ((svc == null || !svc.isInMediaOrThirdPartyApp()) && !com.cinetv.launcher.device.DeviceTweaks.isMediaPlaying(context)) {
                try {
                    Intent home = new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    context.startActivity(home);
                } catch (Throwable ignored) {}
            }
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            TvKeyInterceptorService svc = TvKeyInterceptorService.getInstance();
            if (svc != null && svc.isInMediaOrThirdPartyApp()) {
                return;
            }
            if (!MainActivity.isInFront()) {
                Intent launch = new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                try {
                    context.startActivity(launch);
                } catch (Throwable t) {
                    Log.w(TAG, "failed to start on boot: " + t);
                }
            }
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(() -> {
                if (MainActivity.isInFront()) return;
                TvKeyInterceptorService s = TvKeyInterceptorService.getInstance();
                if (s != null && s.isInMediaOrThirdPartyApp()) return;
                if (com.cinetv.launcher.device.DeviceTweaks.isMediaPlaying(context)) return;
                try {
                    Intent followUp = new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    context.startActivity(followUp);
                } catch (Throwable ignored) {
                }
            }, 2500L);
        }
    }
}
