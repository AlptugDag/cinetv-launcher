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
 *  - the system night mode stays dark, like the launcher.
 *
 * The component name is stable because the TV stores it in Settings.Secure.
 */
public class TvKeyInterceptorService extends AccessibilityService {

    private static volatile TvKeyInterceptorService instance;

    private static final long APP_IDLE_SAVER_MS = 3 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPackage;
    private com.cinetv.launcher.ui.SignatureOverlay signature;
    private com.cinetv.launcher.device.AirPodsLink airPods;

    /** Idle over another app (Nuvio, YouTube menus...) with nothing playing: show our screensaver. */
    private final Runnable idleSaver = new Runnable() {
        @Override
        public void run() {
            String top = lastPackage;
            android.util.Log.i("CineTvSaver", "idle over " + top + ", playing=" + DeviceTweaks.isMediaPlaying(TvKeyInterceptorService.this));
            // Never over live TV / HDMI (the tuner's audio is not "music"), nor while anything plays.
            if (top == null || top.equals(getPackageName()) || isTvCore(top)
                    || DeviceTweaks.isMediaPlaying(TvKeyInterceptorService.this)) {
                handler.postDelayed(this, 60_000L);
                return;
            }
            startActivity(new Intent(TvKeyInterceptorService.this, ScreensaverActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
    };

    private final BroadcastReceiver timeTick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            DeviceTweaks.syncNightMode(TvKeyInterceptorService.this);
        }
    };

    /** Puts text into the input field that has focus on screen, in any app; false when there is none. */
    public static boolean typeIntoFocused(String text) {
        TvKeyInterceptorService s = instance;
        if (s == null) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo node = s.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null || !node.isEditable()) return false;
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Returns true if there is an editable input field currently focused on screen. */
    public static boolean isInputFocused() {
        TvKeyInterceptorService s = instance;
        if (s == null) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo node = s.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            return node != null && node.isEditable();
        } catch (Throwable t) {
            return false;
        }
    }

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
        MemoryOptimizer.applyTvOptimizations(this);
        airPods = new com.cinetv.launcher.device.AirPodsLink(this);
        airPods.start();
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (airPods != null) airPods.stop();
        if (signature != null) signature.hide();
        try {
            unregisterReceiver(timeTick);
        } catch (Throwable ignored) {
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (instance == null) return;
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if ("com.android.systemui".equals(pkg) || "android".equals(pkg)) return;

        switch (pkg) {
            case "com.mediatek.wwtv.tvcenter":
            case "com.mediatek.tv.oneworld.tvcenter": // Grundig
                // The "scan channels" tuner screen MediaTek TVs push right after power-on: go home.
                // Later (Inputs, HDMI, live TV opened by the user) it is left alone.
                if (DeviceTweaks.isUnwantedBootTuner()) {
                    startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    return;
                }
                break;
            case "com.google.android.katniss":
            case "com.google.android.googlequicksearchbox":
            case "com.google.android.apps.googleassistant":
                // If CineTV did not deliberately start listening, dismiss Katniss/Assistant immediately,
                // pause any playing content on screen if possible, and NEVER dump the viewer to the home screen.
                if (android.os.SystemClock.elapsedRealtime() - MainActivity.ourVoiceAt > 20_000) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    DeviceTweaks.pauseMedia(this);
                    return;
                }
                break;
            case "com.netflix.ninja":
                AppLauncher.launchFirst(this, AppLauncher.NUVIO_PACKAGES);
                return;
            case "com.google.android.youtube.tv":
                AppLauncher.launchFirst(this, AppLauncher.YOUTUBE_PACKAGES);
                return;
            case "com.amazon.amazonvideo.livingroom":
                // The Prime button: send a video from the phone (built-in casting).
                AppLauncher.openPhoneCast(this);
                return;
            default:
                break;
        }

        if (lastPackage != null && !lastPackage.equals(pkg) && isAppSwitch(pkg)) {
            // The app just left is closed completely a minute later, unless the viewer went back to it.
            final String left = lastPackage;
            handler.postDelayed(() -> {
                if (!left.equals(lastPackage)) MemoryOptimizer.kill(this, left);
            }, 60_000L);
            if (getPackageName().equals(pkg)) handler.postDelayed(() -> MemoryOptimizer.sweepAsync(this), 2000);
        }
        lastPackage = pkg;
        armIdleSaver();
        if (signature == null) signature = new com.cinetv.launcher.ui.SignatureOverlay(this);
        if (AppLauncher.NUVIO.equals(pkg)) signature.show();
        else if (isAppSwitch(pkg)) signature.hide();
    }

    /** The TV's own live TV / inputs app (Arçelik/Beko: wwtv, Grundig: oneworld). */
    private static boolean isTvCore(String pkg) {
        return pkg.startsWith("com.mediatek.wwtv") || pkg.startsWith("com.mediatek.tv.oneworld.tvcenter");
    }

    /** A real change of app (not a keyboard, toast or pop-up drawn over Nuvio). */
    private boolean isAppSwitch(String pkg) {
        if (pkg.equals(getPackageName()) || isTvCore(pkg)) return true;
        if (pkg.contains("inputmethod") || pkg.contains("katniss")) return false;
        return AppLauncher.isLaunchable(getPackageManager(), pkg);
    }

    private void armIdleSaver() {
        handler.removeCallbacks(idleSaver);
        handler.postDelayed(idleSaver, APP_IDLE_SAVER_MS);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (instance == null) return false;
        armIdleSaver();
        int source = event.getSource();
        boolean gamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if (gamepad || HardwareKeys.isNavigation(event.getKeyCode())) return false;
        // The remote's microphone: pause playing content on screen without dumping the user to the home menu
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_ASSIST) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                DeviceTweaks.pauseMedia(this);
            }
            return true;
        }
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
