package com.cinetv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import java.util.List;
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
    public static volatile boolean launchedYouTubeFromCineTv;
    public static volatile boolean launchedNuvioFromCineTv;

    private static final long APP_IDLE_SAVER_MS = 3 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPackage;

    public String getLastPackage() {
        return lastPackage;
    }
    private com.cinetv.launcher.ui.SignatureOverlay signature;
    private com.cinetv.launcher.device.AirPodsLink airPods;

    /** Idle over another app (Nuvio, YouTube menus...) with nothing playing: show our screensaver. */
    private final Runnable idleSaver = new Runnable() {
        @Override
        public void run() {
            String top = lastPackage;
            android.util.Log.i("CineTvSaver", "idle over " + top + ", playing=" + DeviceTweaks.isMediaPlaying(TvKeyInterceptorService.this));
            // Never over stock launcher, live TV / HDMI (the tuner's audio is not "music"), nor while anything plays.
            if (top == null || top.equals(getPackageName()) || isTvCore(top) || isStockLauncher(top)
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
            MemoryOptimizer.ensureAirPlayReceiver(TvKeyInterceptorService.this);
        }
    };

    private final BroadcastReceiver celebrationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String toast = intent.getStringExtra("toast");
            if (toast != null && !toast.trim().isEmpty()) {
                android.widget.Toast.makeText(context, toast, android.widget.Toast.LENGTH_LONG).show();
            }
            if ("com.cinetv.launcher.ACTION_CELEBRATION".equals(intent.getAction())
                    || intent.getBooleanExtra("force_celebration", false)
                    || intent.getBooleanExtra("show_celebration", false)) {
                com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.today(TvKeyInterceptorService.this);
                if (day == null && intent.getBooleanExtra("force_celebration", false)) {
                    day = com.cinetv.launcher.service.SpecialDay.forDate(2026, 10, 29, 12, 0);
                }
                if (day != null) {
                    com.cinetv.launcher.ui.CelebrationOverlay.show(TvKeyInterceptorService.this, day);
                }
            }
        }
    };

    private final BroadcastReceiver cinesubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String title = intent.getStringExtra("title");
            String insight = intent.getStringExtra("insight");
            if (insight != null && !insight.isEmpty()) {
                com.cinetv.launcher.ui.CineSubOverlay.showInsight(TvKeyInterceptorService.this, title, insight);
            } else {
                com.cinetv.launcher.ui.CineSubOverlay.trigger(TvKeyInterceptorService.this);
            }
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

    /**
     * Performs D-pad navigation, click, or global actions in the currently active window via Accessibility
     * when KeyBridge (ADB) is unavailable.
     */
    public boolean dispatchAccessibilityKey(int code, boolean longPress) {
        try {
            if (code == KeyEvent.KEYCODE_BACK) return performGlobalAction(GLOBAL_ACTION_BACK);
            if (code == KeyEvent.KEYCODE_HOME) return performGlobalAction(GLOBAL_ACTION_HOME);
            if (code == KeyEvent.KEYCODE_APP_SWITCH) return performGlobalAction(GLOBAL_ACTION_RECENTS);
            android.view.accessibility.AccessibilityNodeInfo focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (focused != null) {
                    int action = longPress
                            ? android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK
                            : android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
                    if (focused.performAction(action)) return true;
                    android.view.accessibility.AccessibilityNodeInfo p = focused.getParent();
                    while (p != null) {
                        if (p.isClickable() && p.performAction(action)) return true;
                        p = p.getParent();
                    }
                }
                return false;
            }
            int dir = 0;
            if (code == KeyEvent.KEYCODE_DPAD_UP) dir = android.view.View.FOCUS_UP;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dir = android.view.View.FOCUS_DOWN;
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT) dir = android.view.View.FOCUS_LEFT;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dir = android.view.View.FOCUS_RIGHT;
            if (dir != 0 && focused != null) {
                android.view.accessibility.AccessibilityNodeInfo next = focused.focusSearch(dir);
                if (next != null && next.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)) {
                    return true;
                }
                android.view.accessibility.AccessibilityNodeInfo p = focused;
                int scrollAction = (dir == android.view.View.FOCUS_DOWN || dir == android.view.View.FOCUS_RIGHT)
                        ? android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        : android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                while (p != null) {
                    if (p.isScrollable() && p.performAction(scrollAction)) return true;
                    p = p.getParent();
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Injects a screen tap at (x, y) via AccessibilityService gesture API when KeyBridge is unavailable. */
    public boolean dispatchTap(int x, int y) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(Math.max(0, x), Math.max(0, y));
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50);
            return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Injects a swipe gesture from (x1, y1) to (x2, y2) via AccessibilityService when KeyBridge is unavailable. */
    public boolean dispatchSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(Math.max(0, x1), Math.max(0, y1));
            path.lineTo(Math.max(0, x2), Math.max(0, y2));
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, Math.max(50, durationMs));
            return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(timeTick, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(timeTick, f);
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "timeTick register failed: " + t.getMessage());
        }
        try {
            IntentFilter cf = new IntentFilter("com.cinetv.launcher.ACTION_CELEBRATION");
            cf.addAction("com.cinetv.launcher.ACTION_TOAST");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(celebrationReceiver, cf, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(celebrationReceiver, cf);
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "celebrationReceiver register failed: " + t.getMessage());
        }
        try {
            IntentFilter csf = new IntentFilter("com.cinetv.launcher.ACTION_CINESUB");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(cinesubReceiver, csf, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(cinesubReceiver, csf);
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "cinesubReceiver register failed: " + t.getMessage());
        }
        try {
            final com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.today(this);
            if (day != null) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                int year = c.get(java.util.Calendar.YEAR);
                String prefKey = "celebration_shown_" + day.id + "_" + year;
                boolean alreadyShown = com.cinetv.launcher.service.Prefs.get(this).getBoolean(prefKey, false);
                if (!alreadyShown) {
                    com.cinetv.launcher.service.Prefs.get(this).edit().putBoolean(prefKey, true).apply();
                    handler.postDelayed(() -> com.cinetv.launcher.ui.CelebrationOverlay.show(this, day), 2500);
                }
            }
        } catch (Throwable ignored) {}
        try {
            DeviceTweaks.syncNightMode(this);
        } catch (Throwable ignored) {}
        try {
            MemoryOptimizer.ensureAirPlayReceiver(this);
        } catch (Throwable ignored) {}
        try {
            MemoryOptimizer.applyTvOptimizations(this);
        } catch (Throwable ignored) {}
        try {
            com.cinetv.launcher.device.AirPodsLink.ensureStarted(this);
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "AirPodsLink start failed: " + t.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (airPods != null) airPods.stop();
        if (signature != null) signature.hide();
        try {
            unregisterReceiver(timeTick);
        } catch (Throwable ignored) {}
        try {
            unregisterReceiver(celebrationReceiver);
        } catch (Throwable ignored) {}
        try {
            unregisterReceiver(cinesubReceiver);
        } catch (Throwable ignored) {}
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (instance == null) return;
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if ("com.android.systemui".equals(pkg) || "android".equals(pkg)) return;

        CharSequence cls = event.getClassName();
        if (cls != null) {
            String cName = cls.toString().toLowerCase(java.util.Locale.US);
            if ((cName.contains("screensaver") || cName.contains("dream")) && !getPackageName().equals(pkg)) {
                if (!DeviceTweaks.isMediaPlaying(this)) {
                    startActivity(new Intent(this, ScreensaverActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    return;
                }
            }
        }

        if (isStockLauncher(pkg)) {
            // Stock TV launcher (Xiaomi / Google TV) intercepted HOME key or boot: redirect immediately to CineTV
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            return;
        }

        switch (pkg) {
            case "com.mediatek.wwtv.tvcenter":
            case "com.mediatek.tv.oneworld.tvcenter": // Grundig
            case "com.arcelik.tv.livetv":
            case "com.arcelik.smartremoteservice":
            case "com.android.tv":
            case "com.google.android.tv":
                // The OEM tuner MediaTek/Arçelik TVs push on boot/wake: suppress immediately and stay in CineTV
                // If user deliberately pressed Inputs / Live TV, it is left alone.
                if (DeviceTweaks.isUnwantedTuner()) {
                    new Thread(() -> com.cinetv.launcher.remote.LocalAdb.shell(this, "am force-stop " + pkg + " 2>/dev/null; am force-stop com.mediatek.wwtv.tvcenter 2>/dev/null; am force-stop com.arcelik.tv.livetv 2>/dev/null; am force-stop com.arcelik.smartremoteservice 2>/dev/null", 800)).start();
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
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

    /** Stock TV launchers (Xiaomi Mi TV, Google TV, Android TV, Next Media Box, Amlogic, Droidlogic, Mstar, Rockchip). */
    private boolean isStockLauncher(String pkg) {
        if (pkg == null || pkg.equals(getPackageName())) return false;
        if (pkg.equals("com.google.android.tvlauncher")
                || pkg.equals("com.google.android.apps.tv.launcherx")
                || pkg.equals("com.xiaomi.mitv.tvhome")
                || pkg.equals("com.android.tv.launcher")
                || pkg.contains("tvlauncher")
                || pkg.contains("mitv.tvhome")
                || pkg.contains("droidlogic")
                || pkg.contains("mstar")
                || pkg.contains("homeshell")
                || pkg.contains("rockchip")
                || pkg.contains("elinkway")
                || pkg.contains("shafa")
                || pkg.contains("dangbei")
                || pkg.contains("next.launcher")) {
            return true;
        }
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> homes = getPackageManager().queryIntentActivities(homeIntent, 0);
            for (ResolveInfo ri : homes) {
                if (ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** A real change of app (not a keyboard, toast or pop-up drawn over Nuvio). */
    private boolean isAppSwitch(String pkg) {
        if (pkg.equals(getPackageName()) || isTvCore(pkg) || isStockLauncher(pkg)) return true;
        if (pkg.contains("inputmethod") || pkg.contains("katniss")) return false;
        return AppLauncher.isLaunchable(getPackageManager(), pkg);
    }

    private void armIdleSaver() {
        com.cinetv.launcher.ui.CinemaScreensaverView.warmUp(this);
        handler.removeCallbacks(idleSaver);
        long timeout = AppLauncher.NUVIO.equals(lastPackage) ? 45_000L : APP_IDLE_SAVER_MS;
        handler.postDelayed(idleSaver, timeout);
    }

    private boolean isNuvioApp(String pkg) {
        if (pkg == null) return false;
        for (String p : AppLauncher.NUVIO_PACKAGES) {
            if (pkg.equals(p)) return true;
        }
        return pkg.contains("nuvio") || pkg.contains("stremio");
    }

    private boolean isYouTubeApp(String pkg) {
        if (pkg == null) return false;
        for (String p : AppLauncher.YOUTUBE_PACKAGES) {
            if (pkg.equals(p)) return true;
        }
        return pkg.contains("youtube") || pkg.contains("smarttube");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (instance == null) return false;
        // Holiday celebration modal: any key closes the celebration overlay cleanly
        if (com.cinetv.launcher.ui.CelebrationOverlay.isShowing()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                com.cinetv.launcher.ui.CelebrationOverlay.dismiss();
            }
            return true;
        }
        armIdleSaver();
        int code = event.getKeyCode();
        // Aggressive Home capture: Home button on any remote returns instantly to CineTV
        if (code == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                return true;
            }
            return true;
        }

        // Dedicated Settings / Menu key: always opens CineTV settings
        if (code == KeyEvent.KEYCODE_SETTINGS || code == 176 || code == 268) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra("action", "settings"));
                return true;
            }
            return true;
        }

        // Direct return to CineTV: when exiting Nuvio or YouTube, Back returns straight to CineTV Launcher Home
        // Intercept BOTH ACTION_DOWN and ACTION_UP BEFORE navigation check so Nuvio NEVER unwinds its internal history stack!
        boolean inNuvio = isNuvioApp(lastPackage) || launchedNuvioFromCineTv;
        boolean inYouTube = isYouTubeApp(lastPackage) || launchedYouTubeFromCineTv;
        if ((inNuvio || inYouTube) && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return true; // absorb DOWN so Nuvio does not pop backstack fragments
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                launchedYouTubeFromCineTv = false;
                launchedNuvioFromCineTv = false;
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                return true;
            }
            return true;
        }

        int source = event.getSource();
        boolean gamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        if (gamepad || HardwareKeys.isNavigation(code)) return false;
        // The remote's microphone: pause playing content and launch CineTV voice search overlay
        if (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_ASSIST) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                DeviceTweaks.pauseMedia(this);
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra("action", "voice"));
            }
            return true;
        }
        // CineTV's own window handles its keys itself (settings, key capture...).
        if (getPackageName().equals(lastPackage)) return false;
        String action = HardwareKeys.actionFor(this, event.getKeyCode(), event.getScanCode());
        if (action == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (HardwareKeys.SIMILAR_MOVIES.equals(action)) {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra("action", "similar"));
                return true;
            }
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
