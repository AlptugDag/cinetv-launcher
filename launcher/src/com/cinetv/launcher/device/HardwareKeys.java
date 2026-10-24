package com.cinetv.launcher.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.widget.Toast;

import com.cinetv.launcher.R;
import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.Prefs;

/**
 * One table for the remote's dedicated buttons across vendors. Both the home screen and the
 * accessibility key service resolve keys here, so a button never does two different things.
 *
 * Key codes and scan codes come from Arçelik/Beko/Grundig (MediaTek), Hisense, TCL, Xiaomi and
 * Sony remotes; scan codes catch buttons whose key code the firmware leaves as UNKNOWN.
 */
public final class HardwareKeys {

    public static final String NUVIO = "nuvio";
    public static final String YOUTUBE = "youtube";
    public static final String WEB_VIDEO = "webvideo";
    public static final String TV_PLUS = "tvplus";
    public static final String CLEAN_RAM = "clean_ram";
    public static final String ALL_APPS = "all_apps";

    private static final int[] NETFLIX_KEYS = {207, 188, 183, 265, 229, 289, 398, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_BUTTON_1};
    private static final int[] NETFLIX_SCANS = {183, 191, 398, 412, 249};
    private static final int[] YOUTUBE_KEYS = {218, 184, 189, 190, 250, 284, 290, 399, 415, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_BUTTON_2};
    private static final int[] YOUTUBE_SCANS = {184, 190, 218, 250, 399, 413, 414, 415};
    private static final int[] PRIME_KEYS = {185, 209, 228, 192, 291, 303, 400, KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_BUTTON_3};
    private static final int[] PRIME_SCANS = {185, 192, 251, 400, 416, 417};
    private static final int[] TV_PLUS_KEYS = {277, 170, 172, 186, 292, 401, KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_GUIDE};
    private static final int[] TV_PLUS_SCANS = {186, 377, 362, 401};

    private HardwareKeys() {}

    /** The action bound to a key: a user mapping first, then the built-in vendor table; null for ordinary keys. */
    public static String actionFor(Context ctx, int keyCode, int scanCode) {
        SharedPreferences prefs = Prefs.get(ctx);
        String custom = keyCode > 0 ? prefs.getString(Prefs.CUSTOM_KEY_PREFIX + keyCode, null) : null;
        if (custom == null && scanCode > 0) custom = prefs.getString(Prefs.CUSTOM_SCAN_PREFIX + scanCode, null);
        if (custom != null) return custom;
        return builtIn(keyCode, scanCode);
    }

    public static String builtIn(int keyCode, int scanCode) {
        if (matches(keyCode, scanCode, NETFLIX_KEYS, NETFLIX_SCANS)) return NUVIO;
        if (matches(keyCode, scanCode, YOUTUBE_KEYS, YOUTUBE_SCANS)) return YOUTUBE;
        if (matches(keyCode, scanCode, PRIME_KEYS, PRIME_SCANS)) return WEB_VIDEO;
        if (matches(keyCode, scanCode, TV_PLUS_KEYS, TV_PLUS_SCANS)) return TV_PLUS;
        return null;
    }

    public static void bind(Context ctx, int keyCode, int scanCode, String action) {
        SharedPreferences.Editor e = Prefs.get(ctx).edit();
        if (keyCode > 0) e.putString(Prefs.CUSTOM_KEY_PREFIX + keyCode, action);
        if (scanCode > 0) e.putString(Prefs.CUSTOM_SCAN_PREFIX + scanCode, action);
        e.apply();
    }

    public static boolean isNavigation(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return false;
        }
    }

    /**
     * Runs an action that needs no home-screen UI. Returns false for {@link #ALL_APPS}, which the
     * caller handles by showing its library.
     */
    public static boolean perform(Context ctx, String action) {
        boolean ok;
        switch (action) {
            case NUVIO: ok = AppLauncher.launchFirst(ctx, AppLauncher.NUVIO_PACKAGES); break;
            case YOUTUBE: ok = AppLauncher.launchFirst(ctx, AppLauncher.YOUTUBE_PACKAGES); break;
            case WEB_VIDEO: ok = AppLauncher.launchFirst(ctx, AppLauncher.PRIME_FALLBACK_PACKAGES); break;
            case TV_PLUS: ok = AppLauncher.launchFirst(ctx, AppLauncher.TV_PLUS_PACKAGES); break;
            case CLEAN_RAM:
                int n = MemoryOptimizer.cleanBackground(ctx, ctx.getPackageName());
                Toast.makeText(ctx, ctx.getString(R.string.ram_cleaned, n), Toast.LENGTH_SHORT).show();
                return true;
            case ALL_APPS:
                return false;
            default:
                ok = AppLauncher.launch(ctx, action.startsWith("pkg:") ? action.substring(4) : action);
        }
        if (!ok) Toast.makeText(ctx, R.string.app_not_found, Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean matches(int keyCode, int scanCode, int[] keys, int[] scans) {
        for (int k : keys) if (k == keyCode && keyCode > 0) return true;
        for (int s : scans) if (s == scanCode && scanCode > 0) return true;
        return false;
    }
}
