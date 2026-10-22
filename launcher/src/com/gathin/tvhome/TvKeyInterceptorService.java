package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles:
 * 1) Global Remote Key Interception:
 *    - TV+ button -> Turkcell TV+ (com.turkcell.ott)
 *    - Netflix button -> Stremio (com.stremio.one)
 *    - YouTube button -> TizenTube (io.gh.reisxd.tizentube.cobalt)
 *    - Amazon Prime button -> Non-intrusive In-Video Cinema Picture & Audio HUD Overlay (never pauses or exits video!)
 * 2) Smart RAM Optimization:
 *    - Automatically closes any exited app's background processes when returning to Home or switching apps,
 *      while strictly whitelisting AirPlay, Chromecast, Google Assistant, and Bluetooth/AirPods.
 * 3) Automatic Time-of-Day Dark/Light Mode Synchronization (every minute via ACTION_TIME_TICK).
 */
public class TvKeyInterceptorService extends AccessibilityService {

    public static final String PREFS_NAME = "cleanyourtv_prefs";
    public static final String KEY_LEARN_MODE = "learn_mode_target";
    public static final String KEY_CUSTOM_TVPLUS = "custom_keycode_tvplus";
    public static final String KEY_CUSTOM_NETFLIX = "custom_keycode_netflix";
    public static final String KEY_CUSTOM_AMAZON = "custom_keycode_amazon";
    public static final String KEY_CUSTOM_BEIN = "custom_keycode_bein";
    public static final String KEY_CUSTOM_YOUTUBE = "custom_keycode_youtube";

    private static final Set<String> PROTECTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.gathin.tvhome",
            "android",
            "com.android.systemui",
            "com.android.tv.settings",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.katniss", // Google Voice Assistant
            "com.google.android.tts",
            "com.google.android.apps.mediashell", // Chromecast built-in
            "com.google.android.tv.remote.service",
            "com.android.bluetooth",
            "com.waxrain.airplaydmr",
            "com.waxrain.airplaydmr2",
            "io.github.jqssun.airplay",
            "com.github.jqssun.airplay",
            "com.ionitech.airscreen",
            "com.apowersoft.mirror.tv",
            "com.nuvio.tv",
            "com.instantbits.cast.receiver"
    ));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastForegroundPackage = null;
    private WindowManager windowManager;
    private View overlayHudView;
    private int hudSelectedIndex = 0;
    private TextView[] hudOptionViews;
    private int cinemaBrightnessPct = 85;
    private int cinemaPresetIndex = 0;
    private final String[] cinemaPresets = new String[] {
        "Saf Sinema (Filmmaker 24p • D65 Doğal Renk • Müdahalesiz)",
        "IMAX / HDR10 Referans (Yüksek Dinamik Aralık • Ton Eşleme Doğal)",
        "Gece Sineması (Karanlık Oda • Göz Yormayan Kontrast)"
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            syncDayNightUiMode();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(timeReceiver, filter);
        } catch (Throwable ignored) {
        }
        syncDayNightUiMode();
        ensureAirPlayReceiverRunning(this);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(timeReceiver);
        } catch (Throwable ignored) {
        }
        hideCinemaHudOverlay();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String currentPkg = pkgCs.toString();

        if (currentPkg.equals("com.android.systemui") || currentPkg.equals("android")) {
            return;
        }

        // Smart RAM Optimization: When exiting any app back to Home, or switching between heavy media apps,
        // close the exited app's background processes immediately while protecting AirPlay/Chromecast/Assistant.
        if (lastForegroundPackage != null
                && !lastForegroundPackage.equals(currentPkg)
                && !isProtectedPackage(lastForegroundPackage)) {
            final String pkgToClean = lastForegroundPackage;
            final String activeNow = currentPkg;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    killAppBackgroundProcesses(pkgToClean);
                    if ("com.gathin.tvhome".equals(activeNow)) {
                        cleanAllBackgroundApps(TvKeyInterceptorService.this, activeNow);
                    }
                }
            }, 900L);
        }

        lastForegroundPackage = currentPkg;
    }

    public static boolean isProtectedPackage(String pkg) {
        if (pkg == null) return true;
        if (PROTECTED_PACKAGES.contains(pkg)) return true;
        String lower = pkg.toLowerCase();
        return lower.contains("airplay") || lower.contains("airscreen") || lower.contains("airpin")
                || lower.contains("mediashell") || lower.contains("chromecast")
                || lower.contains("bluetooth") || lower.contains("inputmethod")
                || lower.contains("katniss") || lower.contains("tv.settings");
    }

    private void killAppBackgroundProcesses(String pkg) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(pkg);
            }
        } catch (Throwable ignored) {
        }
    }

    public static int cleanAllBackgroundApps(Context context, String activeForegroundPkg) {
        int cleaned = 0;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            PackageManager pm = context.getPackageManager();
            if (am == null || pm == null) return 0;
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : apps) {
                if (ai.packageName == null) continue;
                if (ai.packageName.equals(activeForegroundPkg)) continue;
                if (isProtectedPackage(ai.packageName)) continue;
                // Only clean user apps or media/streaming apps, never core OS services
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                        || ai.packageName.contains("youtube")
                        || ai.packageName.contains("netflix")
                        || ai.packageName.contains("amazon")
                        || ai.packageName.contains("turkcell")
                        || ai.packageName.contains("stremio")
                        || ai.packageName.contains("cloudstream")) {
                    am.killBackgroundProcesses(ai.packageName);
                    cleaned++;
                }
            }
        } catch (Throwable ignored) {
        }
        return cleaned;
    }

    public static void ensureAirPlayReceiverRunning(Context context) {
        String[] airplayPackages = new String[] {
            "com.github.jqssun.airplay",
            "com.waxrain.airplaydmr",
            "com.waxrain.airplaydmr2",
            "com.ionitech.airscreen"
        };
        PackageManager pm = context.getPackageManager();
        for (String pkg : airplayPackages) {
            try {
                pm.getPackageInfo(pkg, 0);
                Intent serviceIntent = new Intent();
                serviceIntent.setClassName(pkg, pkg + ".service.AirPlayService");
                context.startService(serviceIntent);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        int action = event.getAction();

        // When the in-video Cinema HUD overlay is visible, route D-Pad keys to the overlay
        // so the background movie keeps playing smoothly without pausing or exiting!
        if (overlayHudView != null) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    hudSelectedIndex = (hudSelectedIndex - 1 + hudOptionViews.length) % hudOptionViews.length;
                    updateHudSelection();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    hudSelectedIndex = (hudSelectedIndex + 1) % hudOptionViews.length;
                    updateHudSelection();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    adjustHudOptionHorizontal(hudSelectedIndex, -1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    adjustHudOptionHorizontal(hudSelectedIndex, 1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    executeHudOption(hudSelectedIndex);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || isAmazonPrimeKey(keyCode, scanCode)) {
                    hideCinemaHudOverlay();
                    return true;
                }
            }
            return true;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String learnTarget = prefs.getString(KEY_LEARN_MODE, null);
        if (learnTarget != null && !learnTarget.isEmpty()) {
            if (action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                prefs.edit().remove(KEY_LEARN_MODE).apply();
                return false;
            }
            if (action == KeyEvent.ACTION_DOWN && !isStandardNavigationKey(keyCode)) {
                int token = (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode != 0) ? keyCode : (10000 + scanCode);
                if ("tvplus".equals(learnTarget)) {
                    prefs.edit().putInt(KEY_CUSTOM_TVPLUS, token).remove(KEY_LEARN_MODE).apply();
                    Toast.makeText(this, "TV+ tuşu kaydedildi (Kod: " + token + ")", Toast.LENGTH_SHORT).show();
                } else if ("netflix".equals(learnTarget)) {
                    prefs.edit().putInt(KEY_CUSTOM_NETFLIX, token).remove(KEY_LEARN_MODE).apply();
                    Toast.makeText(this, "Netflix -> Nuvio tuşu kaydedildi (Kod: " + token + ")", Toast.LENGTH_SHORT).show();
                } else if ("amazon".equals(learnTarget)) {
                    prefs.edit().putInt(KEY_CUSTOM_AMAZON, token).remove(KEY_LEARN_MODE).apply();
                    Toast.makeText(this, "Amazon Prime -> Sinema HUD tuşu kaydedildi (Kod: " + token + ")", Toast.LENGTH_SHORT).show();
                } else if ("bein".equals(learnTarget)) {
                    prefs.edit().putInt(KEY_CUSTOM_BEIN, token).remove(KEY_LEARN_MODE).apply();
                    Toast.makeText(this, "Bein Connect -> Reklamsız YouTube tuşu kaydedildi (Kod: " + token + ")", Toast.LENGTH_SHORT).show();
                } else if ("youtube".equals(learnTarget)) {
                    prefs.edit().putInt(KEY_CUSTOM_YOUTUBE, token).remove(KEY_LEARN_MODE).apply();
                    Toast.makeText(this, "YouTube -> Reklamsız YouTube tuşu kaydedildi (Kod: " + token + ")", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        }

        // 1. Netflix Button -> Opens Nuvio
        if (isNetflixKey(keyCode, scanCode, prefs)) {
            if (action == KeyEvent.ACTION_DOWN) {
                launchPackageWithFallback(new String[]{"com.nuvio.tv"}, "Nuvio");
            }
            return true;
        }

        // 2. YouTube Button -> Opens Reklamsız YouTube (TizenTube / Cobalt)
        if (isYouTubeKey(keyCode, scanCode, prefs)) {
            if (action == KeyEvent.ACTION_DOWN) {
                launchPackageWithFallback(new String[]{
                        "io.gh.reisxd.tizentube.cobalt",
                        "com.reisxd.tizentube",
                        "com.teamsmart.videomanager.tv",
                        "com.liskovsoft.smarttubetv.beta",
                        "com.liskovsoft.smarttubetv"
                }, "Reklamsız YouTube");
            }
            return true;
        }

        // 3. Amazon Prime / Bein Button -> Opens Web Video Caster
        if (isAmazonPrimeKey(keyCode, scanCode, prefs) || isBeinConnectKey(keyCode, scanCode, prefs)) {
            if (action == KeyEvent.ACTION_DOWN) {
                launchPackageWithFallback(new String[]{
                        "com.instantbits.cast.receiver",
                        "com.instantbits.cast.webvideo"
                }, "Web Video Caster");
            }
            return true;
        }

        // 4. TV+ Button -> Opens Turkcell TV+
        if (isTvPlusKey(keyCode, scanCode, prefs)) {
            if (action == KeyEvent.ACTION_DOWN) {
                launchPackageWithFallback(new String[]{"com.turkcell.ott", "com.turkcell.tvplus"}, "TV+");
            }
            return true;
        }

        return false;
    }

    private boolean isStandardNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_POWER;
    }

    private boolean matchesCustomToken(int keyCode, int scanCode, int customToken) {
        if (customToken <= 0) return false;
        if (customToken >= 10000) {
            return scanCode == (customToken - 10000);
        }
        return keyCode == customToken;
    }

    private boolean isTvPlusKey(int keyCode, int scanCode, SharedPreferences prefs) {
        int custom = prefs.getInt(KEY_CUSTOM_TVPLUS, -1);
        if (matchesCustomToken(keyCode, scanCode, custom)) return true;
        return keyCode == KeyEvent.KEYCODE_TV
                || keyCode == KeyEvent.KEYCODE_GUIDE
                || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
                || keyCode == KeyEvent.KEYCODE_BUTTON_4
                || keyCode == KeyEvent.KEYCODE_PROG_BLUE
                || scanCode == 377 || scanCode == 362;
    }

    private boolean isNetflixKey(int keyCode, int scanCode, SharedPreferences prefs) {
        int custom = prefs.getInt(KEY_CUSTOM_NETFLIX, -1);
        if (matchesCustomToken(keyCode, scanCode, custom)) return true;
        return keyCode == KeyEvent.KEYCODE_BUTTON_1
                || keyCode == KeyEvent.KEYCODE_PROG_RED
                || keyCode == 191 || keyCode == 265
                || scanCode == 191 || scanCode == 412 || scanCode == 249;
    }

    private boolean isBeinConnectKey(int keyCode, int scanCode, SharedPreferences prefs) {
        int custom = prefs.getInt(KEY_CUSTOM_BEIN, -1);
        if (matchesCustomToken(keyCode, scanCode, custom)) return true;
        return keyCode == KeyEvent.KEYCODE_PROG_GREEN
                || keyCode == KeyEvent.KEYCODE_TV_DATA_SERVICE
                || keyCode == 248 || keyCode == 252
                || scanCode == 248 || scanCode == 252 || scanCode == 414;
    }

    private boolean isYouTubeKey(int keyCode, int scanCode, SharedPreferences prefs) {
        int custom = prefs.getInt(KEY_CUSTOM_YOUTUBE, -1);
        if (matchesCustomToken(keyCode, scanCode, custom)) return true;
        return keyCode == KeyEvent.KEYCODE_BUTTON_2
                || keyCode == 190
                || scanCode == 190 || scanCode == 250;
    }

    private boolean isAmazonPrimeKey(int keyCode, int scanCode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return isAmazonPrimeKey(keyCode, scanCode, prefs);
    }

    private boolean isAmazonPrimeKey(int keyCode, int scanCode, SharedPreferences prefs) {
        int custom = prefs.getInt(KEY_CUSTOM_AMAZON, -1);
        if (matchesCustomToken(keyCode, scanCode, custom)) return true;
        return keyCode == KeyEvent.KEYCODE_BUTTON_3
                || keyCode == KeyEvent.KEYCODE_PROG_YELLOW
                || keyCode == KeyEvent.KEYCODE_SETTINGS
                || keyCode == 192 || keyCode == 290 || keyCode == 303
                || scanCode == 192 || scanCode == 251 || scanCode == 413;
    }

    private void launchPackageWithFallback(String[] packages, String displayName) {
        PackageManager pm = getPackageManager();
        for (String pkg : packages) {
            try {
                Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage(pkg);
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    startActivity(intent);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(this, displayName + " uygulaması bulunamadı.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Non-intrusive floating glass Cinema HUD overlay over any playing video (Amazon Prime button).
     */
    private void showCinemaHudOverlay() {
        if (overlayHudView != null || windowManager == null) {
            return;
        }
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(36, 30, 36, 30);

            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setColor(Color.parseColor("#EB0A0D14"));
            panelBg.setCornerRadius(28f);
            panelBg.setStroke(2, Color.parseColor("#45FFFFFF"));
            root.setBackground(panelBg);

            TextView title = new TextView(this);
            title.setText("SİNEMA KUSURSUZLUK & SES HUD");
            title.setTypeface(FontManager.getBlack(this));
            title.setTextSize(20f);
            title.setTextColor(Color.parseColor("#F8FAFC"));
            root.addView(title);

            TextView subtitle = new TextView(this);
            subtitle.setText("Film durmadan canlı müdahale • Sol/Sağ: Ayarla • OK: Seç • Geri: Kapat");
            subtitle.setTypeface(FontManager.getMedium(this));
            subtitle.setTextSize(12f);
            subtitle.setTextColor(Color.parseColor("#94A3B8"));
            subtitle.setPadding(0, 4, 0, 18);
            root.addView(subtitle);

            hudOptionViews = new TextView[8];
            hudSelectedIndex = 0;

            for (int i = 0; i < hudOptionViews.length; i++) {
                TextView item = new TextView(this);
                item.setTypeface(FontManager.getBold(this));
                item.setTextSize(14f);
                item.setPadding(22, 14, 22, 14);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(0, 3, 0, 3);
                root.addView(item, lp);
                hudOptionViews[i] = item;
            }

            refreshHudLabels();
            updateHudSelection();

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    720,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            params.x = 36;

            windowManager.addView(root, params);
            overlayHudView = root;
        } catch (Throwable t) {
            openSystemPictureSettings();
        }
    }

    private void refreshHudLabels() {
        if (hudOptionViews == null) return;
        hudOptionViews[0].setText("◂ Görüntü Kalibrasyonu: " + cinemaPresets[cinemaPresetIndex] + " ▸");
        hudOptionViews[1].setText("◂ Panel Parlaklık / Arka Işık: %" + cinemaBrightnessPct + " (Saf HDR) ▸");
        hudOptionViews[2].setText("• TV Yerleşik Görüntü & HDR Yan Panelini Aç");
        hudOptionViews[3].setText("• TV Yerleşik Ses & Passthrough (Bit-Perfect) Ayarlarını Aç");
        hudOptionViews[4].setText("• AirPods / Bluetooth Ses Eşleştir & Gecikme Senkronla");
        hudOptionViews[5].setText("• Web Video Caster Uygulamasına Geç");
        hudOptionViews[6].setText("• Akıllı RAM: Arka Plandaki Gereksiz Süreçleri Anında Temizle");
        hudOptionViews[7].setText("• HUD Panelini Kapat (Filme Kesintisiz Devam Et)");
    }

    private void adjustHudOptionHorizontal(int index, int delta) {
        if (index == 0) {
            cinemaPresetIndex = (cinemaPresetIndex + delta + cinemaPresets.length) % cinemaPresets.length;
            applyPureCinemaSettings(cinemaPresetIndex);
            refreshHudLabels();
        } else if (index == 1) {
            cinemaBrightnessPct = Math.max(15, Math.min(100, cinemaBrightnessPct + (delta * 5)));
            try {
                int raw = (int) Math.round((cinemaBrightnessPct / 100.0) * 255);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, raw);
            } catch (Throwable ignored) {
            }
            refreshHudLabels();
        }
    }

    private void updateHudSelection() {
        if (hudOptionViews == null) return;
        for (int i = 0; i < hudOptionViews.length; i++) {
            TextView tv = hudOptionViews[i];
            if (i == hudSelectedIndex) {
                GradientDrawable hl = new GradientDrawable();
                hl.setColor(Color.parseColor("#FF2563EB"));
                hl.setCornerRadius(16f);
                tv.setBackground(hl);
                tv.setTextColor(Color.WHITE);
            } else {
                tv.setBackground(null);
                tv.setTextColor(Color.parseColor("#D1D5DB"));
            }
        }
    }

    private void hideCinemaHudOverlay() {
        if (overlayHudView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayHudView);
            } catch (Throwable ignored) {
            }
            overlayHudView = null;
            hudOptionViews = null;
        }
    }

    private void executeHudOption(int index) {
        if (index == 0 || index == 1) {
            adjustHudOptionHorizontal(index, 1);
            return;
        }
        hideCinemaHudOverlay();
        switch (index) {
            case 2:
                openSystemPictureSettings();
                break;
            case 3:
                openSystemSoundSettings();
                break;
            case 4:
                openBluetoothSettings();
                break;
            case 5:
                launchPackageWithFallback(new String[]{"com.instantbits.cast.receiver", "com.instantbits.cast.webvideo"}, "Web Video Caster");
                break;
            case 6:
                int count = cleanAllBackgroundApps(this, lastForegroundPackage);
                Toast.makeText(this, "Akıllı RAM: " + count + " arka plan süreci kapatıldı.", Toast.LENGTH_SHORT).show();
                break;
            case 7:
            default:
                break;
        }
    }

    private void openSystemPictureSettings() {
        // First try vendor floating picture settings panels (Arçelik/Grundig/MediaTek/Realtek/Android TV)
        String[][] vendorTargets = new String[][] {
            {"com.android.tv.settings", "com.android.tv.settings.MainSettings"},
            {"com.arcelik.tv.settings", "com.arcelik.tv.settings.PictureActivity"},
            {"com.grundig.tv.settings", "com.grundig.tv.settings.PictureActivity"},
            {"com.mediatek.tv.settings", "com.mediatek.tv.settings.PictureSettingsActivity"}
        };
        String[] actions = new String[] {
            "android.settings.PICTURE_SETTINGS",
            "com.android.tv.settings.PICTURE_SETTINGS",
            "android.settings.DISPLAY_SETTINGS",
            Settings.ACTION_DISPLAY_SETTINGS,
            Settings.ACTION_SETTINGS
        };
        for (String act : actions) {
            try {
                Intent i = new Intent(act);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
        for (String[] vt : vendorTargets) {
            try {
                Intent i = new Intent();
                i.setClassName(vt[0], vt[1]);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private void openSystemSoundSettings() {
        String[] actions = new String[] {
            "android.settings.SOUND_SETTINGS",
            Settings.ACTION_SOUND_SETTINGS,
            Settings.ACTION_SETTINGS
        };
        for (String act : actions) {
            try {
                Intent i = new Intent(act);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private void openBluetoothSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private void applyPureCinemaSettings(int preset) {
        try {
            // Pure Filmmaker settings: no artificial motion interpolation, natural dynamic range matching, 24p frame rate matching
            Settings.Secure.putInt(getContentResolver(), "match_content_frame_rate", 1);
            Settings.Global.putInt(getContentResolver(), "are_user_disabled_hdr_formats_allowed", 0);
            Settings.Global.putInt(getContentResolver(), "hdr_conversion_mode", 1);
            Settings.System.putInt(getContentResolver(), "sound_effects_enabled", 0);
            if (preset == 1) {
                cinemaBrightnessPct = 100;
            } else if (preset == 2) {
                cinemaBrightnessPct = 65;
            } else {
                cinemaBrightnessPct = 85;
            }
            int raw = (int) Math.round((cinemaBrightnessPct / 100.0) * 255);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, raw);
        } catch (Throwable ignored) {
        }
    }

    public void syncDayNightUiMode() {
        try {
            UiModeManager uiManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (uiManager != null) {
                uiManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
            }
            Settings.Secure.putInt(getContentResolver(), "ui_night_mode", 2);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onInterrupt() {
        hideCinemaHudOverlay();
    }
}
