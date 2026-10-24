package com.cinetv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.cinetv.launcher.device.DeviceTweaks;
import com.cinetv.launcher.device.HardwareKeys;
import com.cinetv.launcher.device.MemoryOptimizer;
import com.cinetv.launcher.service.AppLauncher;

/**
 * The one accessibility service of CineTV, so remote buttons work everywhere, not only on the home
 * screen:
 *  - dedicated buttons and user mappings resolve through {@link HardwareKeys};
 *  - the stock Netflix / YouTube / Prime apps that some remotes launch directly are swapped for the
 *    apps chosen here, and the tuner app that MediaTek TVs open on boot is sent back Home;
 *  - when an app is left, its background processes are trimmed (casting, voice and Bluetooth are
 *    protected);
 *  - the system night mode follows the launcher's dark / light / auto appearance.
 *
 * The component name is stable because the TV stores it in Settings.Secure.
 */
public class TvKeyInterceptorService extends AccessibilityService {

    private static volatile TvKeyInterceptorService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPackage;

    private final BroadcastReceiver timeTick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            DeviceTweaks.syncNightMode(TvKeyInterceptorService.this);
        }
    };

    public static TvKeyInterceptorService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(timeTick, f);
        DeviceTweaks.syncNightMode(this);
        MemoryOptimizer.ensureAirPlayReceiver(this);
    }

    @Override
    public void onDestroy() {
        instance = null;
        try {
            unregisterReceiver(timeTick);
        } catch (Throwable ignored) {
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if ("com.android.systemui".equals(pkg) || "android".equals(pkg)) return;

        switch (pkg) {
            case "com.mediatek.wwtv.tvcenter":
                // The tuner screen MediaTek TVs show after a cold boot: go to CineTV instead.
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                return;
            case "com.netflix.ninja":
                AppLauncher.launchFirst(this, AppLauncher.NUVIO_PACKAGES);
                return;
            case "com.google.android.youtube.tv":
                AppLauncher.launchFirst(this, AppLauncher.YOUTUBE_PACKAGES);
                return;
            case "com.amazon.amazonvideo.livingroom":
                AppLauncher.launchFirst(this, AppLauncher.WEB_VIDEO_CASTER_PACKAGES);
                return;
            default:
                break;
        }

        if (lastPackage != null && !lastPackage.equals(pkg)) {
            final String left = lastPackage;
            final boolean home = getPackageName().equals(pkg);
            handler.postDelayed(() -> {
                MemoryOptimizer.kill(this, left);
                if (home) MemoryOptimizer.sweepAsync(this);
            }, 900);
        }
        lastPackage = pkg;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int source = event.getSource();
        boolean gamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if (gamepad || HardwareKeys.isNavigation(event.getKeyCode())) return false;
        // CineTV's own window handles its keys itself (settings, key capture...).
        if (getPackageName().equals(lastPackage)) return false;
        String action = HardwareKeys.actionFor(this, event.getKeyCode(), event.getScanCode());
        if (action == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (!HardwareKeys.perform(this, action)) {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("action", "show_all_apps"));
            }
        }
        return true;
    }

    @Override
    public void onInterrupt() {
    }
}
