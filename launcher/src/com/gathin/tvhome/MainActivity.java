package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apple tvOS Human Interface Guidelines (HIG) Liquid Glass & Focus Engine:
 * - Seamless, continuously breathing background tone with a giant, barely-perceptible watermark
 *   of Alptuğ's personal signature logo integrated into the living canvas.
 * - No outer box/frame around the 4 main Dock apps — they float directly in the 4-column grid.
 * - Unfocused cards rest at 0.88 alpha with always-on internal logo luminescence.
 * - Focused item elevates with tvOS parallax scale, diagonal Liquid Glass specular shimmer,
 *   and wide-spread, low-density hover neon diffusion that interacts with the background.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = TvKeyInterceptorService.PREFS_NAME;
    private static final String KEY_APP_ORDER = "custom_app_order_csv";
    private static final long SCREENSAVER_IDLE_MS = 5 * 60 * 1000L; // 5 minutes

    private static final Set<String> HIDDEN_SYSTEM_PACKAGES = new HashSet<>(Arrays.asList(
        "com.gathin.tvhome",
        "com.google.android.youtube.tv",
        "com.google.android.tungsten.setupwraith",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "tr.com.bein.connect.tv",
        "io.freemytv",
        "com.cybercat.adbappcontrol.tv",
        "com.arcelik.ChannelEditor",
        "com.arcelik.tv.rctest",
        "com.mediatek.wwtv.tvcenter",
        "com.mediatek.wwtv.tvcenter.rro",
        // Unwanted from row 3 & dock requested by user:
        "mediatek.factorymenu.ui",
        "arcelik.factorymenu.hatone",
        "com.mediatek.tv.factory",
        "com.mediatek.wwtv.webview",
        "com.google.android.webview",
        "com.mediatek.tv.service",
        "io.github.jqssun.airplay",
        "org.courville.nova",
        "com.mediatek.wwtv.mediaplayer"
    ));

    private static final String[][] BUNDLED_MOVIES = new String[][] {
        {"posters/poster_1.png", "tt15239678", "Dune: Part Two"},
        {"posters/poster_2.png", "tt15398776", "Oppenheimer"},
        {"posters/poster_3.png", "tt0816692", "Interstellar"},
        {"posters/poster_4.png", "tt1877830", "The Batman"},
        {"posters/poster_5.png", "tt1856101", "Blade Runner 2049"},
        {"posters/poster_6.png", "tt1375666", "Inception"},
        {"posters/poster_7.png", "tt9218128", "Gladiator II"},
        {"posters/poster_8.png", "tt1392190", "Mad Max: Fury Road"},
        {"posters/poster_1.png", "tt27419466", "Moana"},
        {"posters/poster_2.png", "tt12037194", "Furiosa: A Mad Max Saga"},
        {"posters/poster_3.png", "tt1136617", "The Dark Knight Rises"},
        {"posters/poster_4.png", "tt1160419", "Dune"},
        {"posters/poster_5.png", "tt9362722", "Spider-Man: Across the Spider-Verse"},
        {"posters/poster_6.png", "tt9603212", "Mission: Impossible - Dead Reckoning"},
        {"posters/poster_7.png", "tt1745960", "Top Gun: Maverick"},
        {"posters/poster_8.png", "tt1630029", "Avatar: The Way of Water"},
        {"posters/poster_1.png", "tt30144381", "Nutcrackers"},
        {"posters/poster_2.png", "tt29714073", "Bad Apples"},
        {"posters/poster_3.png", "tt34379051", "By Any Means"},
        {"posters/poster_4.png", "tt37775809", "The Musical"}
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private SeamlessLivingBackdropView ambientBackdropView;
    private TextView clockText;
    private TextView dateText;
    private TextView weatherText;
    private String currentThemeMode = "dark";
    private final Runnable weatherRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchLiveWeather();
            handler.postDelayed(this, 30 * 60 * 1000L);
        }
    };
    private HorizontalScrollView heroScroll;
    private LinearLayout heroShelfRow;
    private LinearLayout dockRow;
    private LinearLayout appsGridContainer;

    private final Set<String> userHiddenApps = new HashSet<>();
    private boolean isMovingTile = false;
    private AppTileItem movingTileItem = null;
    private boolean movingTileInDock = false;
    private int movingTileIndex = -1;
    private boolean isVitrinFocused = false;

    private LinearLayout reorderHudBar;
    private TextView reorderStatusText;
    private TextView reorderHideBtn;
    private TextView reorderUninstallBtn;

    private FrameLayout screensaverOverlay;
    private LinearLayout screensaverCluster;
    private TextView screensaverClockText;
    private TextView screensaverDateText;
    private boolean isScreensaverActive = false;

    private final List<AppTileItem> dockItems = new ArrayList<>();
    private final List<AppTileItem> gridItems = new ArrayList<>();
    private final List<ImageView> posterImageViews = new ArrayList<>();
    private final List<ImageView> posterBloomViews = new ArrayList<>();
    private final List<FrameLayout> posterCardFrames = new ArrayList<>();
    private final List<Integer> posterNeonColors = new ArrayList<>();
    private final List<String> posterImdbIds = new ArrayList<>();
    private final List<String> posterTitles = new ArrayList<>();
    private long lastVitrinCheckTime = 0;

    private static final int VITRIN_POSTER_W = 260;
    private static final int VITRIN_POSTER_H = 390;
    private static final int VITRIN_BLOOM_PAD = 70;
    private static final int VITRIN_OVERLAP_MARGIN = -52;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 30000L);
        }
    };

    private final Runnable idleScreensaverTrigger = new Runnable() {
        @Override
        public void run() {
            showIdleScreensaver();
        }
    };

    private final Runnable vitrinAutoSlide = new Runnable() {
        @Override
        public void run() {
            if (!isVitrinFocused && heroScroll != null && heroShelfRow != null && heroShelfRow.getChildCount() > 1) {
                int step = VITRIN_POSTER_W + VITRIN_OVERLAP_MARGIN;
                int maxScroll = heroShelfRow.getWidth() - heroScroll.getWidth();
                if (maxScroll > 0) {
                    int nextX = heroScroll.getScrollX() + step;
                    if (nextX >= maxScroll - 40) {
                        heroScroll.smoothScrollTo(0, 0);
                    } else {
                        heroScroll.smoothScrollTo(nextX, 0);
                    }
                }
            }
            handler.postDelayed(this, 10000L);
        }
    };

    private final Runnable screensaverDriftRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScreensaverActive && screensaverCluster != null) {
                float dx = (random.nextFloat() - 0.5f) * 320f;
                float dy = (random.nextFloat() - 0.5f) * 180f;
                screensaverCluster.animate()
                        .translationX(dx)
                        .translationY(dy)
                        .setDuration(1800L)
                        .start();
                updateClock();
                handler.postDelayed(this, 40000L);
            }
        }
    };

    public static class AppTileItem {
        public String packageName;
        public String label;
        public Drawable bannerDrawable;
        public int neonColor;
    }

    /**
     * Full-screen living backdrop that:
     * 1) Continuously and seamlessly shifts its deep atmospheric tone across a non-blue cinema palette
     *    (Amethyst Noir -> Warm Amber Noir -> Crimson Wine Noir -> Emerald Noir -> Orchid Noir).
     * 2) Renders Alptuğ's personal signature vector logo as a grand, barely-perceptible watermark
     *    across the background canvas that catches the ambient light.
     * 3) Smoothly glides a wide-spread interactive light field behind whichever item is currently focused.
     */
    private class SeamlessLivingBackdropView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint breathingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint interactiveGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();
        private final Drawable watermarkLogo;

        // Curated deep cinema ambient palette (avoids repetitive blue!)
        private final int[] AMBIENT_CYCLE_COLORS = new int[] {
            Color.parseColor("#180A1C"), // Deep Amethyst Noir
            Color.parseColor("#1D0B11"), // Midnight Ruby Wine
            Color.parseColor("#1A1108"), // Warm Amber Gold Noir
            Color.parseColor("#081712"), // Deep Emerald Noir
            Color.parseColor("#160B22")  // Deep Violet Orchid
        };

        private float glowX = 960f;
        private float glowY = 540f;
        private int glowColor = Color.parseColor("#FFD60A");
        private float cyclePhase = 0f;
        private boolean isLightMode = false;
        private ValueAnimator focusAnimator;
        private ValueAnimator breathingAnimator;

        SeamlessLivingBackdropView(Context ctx) {
            super(ctx);
            watermarkLogo = null;
        }

        public void setLightMode(boolean light) {
            this.isLightMode = light;
            invalidate();
        }

        void startSeamlessBreathing() {
            if (breathingAnimator != null && breathingAnimator.isRunning()) return;
            breathingAnimator = ValueAnimator.ofFloat(0f, (float) AMBIENT_CYCLE_COLORS.length);
            breathingAnimator.setDuration(45000L); // 45-second ultra-slow seamless cycle
            breathingAnimator.setRepeatCount(ValueAnimator.INFINITE);
            breathingAnimator.setRepeatMode(ValueAnimator.RESTART);
            breathingAnimator.setInterpolator(new LinearInterpolator());
            breathingAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    cyclePhase = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            breathingAnimator.start();
        }

        void stopSeamlessBreathing() {
            if (breathingAnimator != null) {
                breathingAnimator.cancel();
                breathingAnimator = null;
            }
        }

        void animateToTarget(View focusedView, int targetColor) {
            int[] loc = new int[2];
            focusedView.getLocationOnScreen(loc);
            final float targetX = loc[0] + focusedView.getWidth() * 0.5f;
            final float targetY = loc[1] + focusedView.getHeight() * 0.5f;
            final float startX = glowX;
            final float startY = glowY;
            final int startColor = glowColor;
            final int endColor = targetColor;

            if (focusAnimator != null) {
                focusAnimator.cancel();
            }
            focusAnimator = ValueAnimator.ofFloat(0f, 1f);
            focusAnimator.setDuration(440L);
            focusAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
            focusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float f = (Float) animation.getAnimatedValue();
                    glowX = startX + (targetX - startX) * f;
                    glowY = startY + (targetY - startY) * f;
                    glowColor = (Integer) argbEvaluator.evaluate(f, startColor, endColor);
                    invalidate();
                }
            });
            focusAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth() > 0 ? getWidth() : 1920;
            int h = getHeight() > 0 ? getHeight() : 1080;

            int idxA = ((int) Math.floor(cyclePhase)) % AMBIENT_CYCLE_COLORS.length;
            int idxB = (idxA + 1) % AMBIENT_CYCLE_COLORS.length;
            float frac = cyclePhase - (float) Math.floor(cyclePhase);
            int breathingTone = (Integer) argbEvaluator.evaluate(frac, AMBIENT_CYCLE_COLORS[idxA], AMBIENT_CYCLE_COLORS[idxB]);

            // 1. Seamless, slowly morphing background gradient
            if (isLightMode) {
                bgPaint.setShader(new LinearGradient(
                        0f, 0f, w, h,
                        new int[]{Color.parseColor("#F8FAFC"), Color.parseColor("#E2E8F0"), Color.parseColor("#CBD5E1")},
                        new float[]{0f, 0.52f, 1f},
                        Shader.TileMode.CLAMP
                ));
            } else {
                bgPaint.setShader(new LinearGradient(
                        0f, 0f, w, h,
                        new int[]{Color.parseColor("#060608"), breathingTone, Color.parseColor("#050507")},
                        new float[]{0f, 0.52f, 1f},
                        Shader.TileMode.CLAMP
                ));
            }
            canvas.drawRect(0, 0, w, h, bgPaint);

            // Slow orbital ambient drift across the upper-center canvas
            double angle = (cyclePhase / AMBIENT_CYCLE_COLORS.length) * Math.PI * 2.0;
            float orbitX = (w * 0.52f) + (float) Math.cos(angle) * 260f;
            float orbitY = (h * 0.45f) + (float) Math.sin(angle) * 140f;
            int br = Color.red(breathingTone);
            int bg = Color.green(breathingTone);
            int bb = Color.blue(breathingTone);
            breathingPaint.setShader(new RadialGradient(
                    orbitX, orbitY, 920f,
                    new int[]{
                        Color.argb(65, Math.min(255, br * 2), Math.min(255, bg * 2), Math.min(255, bb * 2)),
                        Color.argb(24, br, bg, bb),
                        Color.argb(0, 0, 0, 0)
                    },
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, breathingPaint);

            // 2. Barely-perceptible, architectural watermark of Alptuğ's personal signature logo
            if (watermarkLogo != null) {
                int logoSize = 920;
                int left = (w - logoSize) / 2;
                int top = (h - logoSize) / 2 - 20;
                int r = Color.red(glowColor);
                int g = Color.green(glowColor);
                int b = Color.blue(glowColor);
                // Tinted subtly by the active neon color blended with soft silver at ~4.8% opacity (alpha = 12)
                watermarkLogo.setTint(Color.rgb(
                        (r + 255 * 2) / 3,
                        (g + 255 * 2) / 3,
                        (b + 255 * 2) / 3
                ));
                watermarkLogo.setAlpha(13);
                watermarkLogo.setBounds(left, top, left + logoSize, top + logoSize);
                watermarkLogo.draw(canvas);
            }

            // 3. Interactive wide-dispersion atmospheric light field following the focused card/poster
            int r = Color.red(glowColor);
            int g = Color.green(glowColor);
            int b = Color.blue(glowColor);
            interactiveGlowPaint.setShader(new RadialGradient(
                    glowX, glowY, 860f,
                    new int[]{
                        Color.argb(46, r, g, b),
                        Color.argb(28, r, g, b),
                        Color.argb(14, r, g, b),
                        Color.argb(5, r, g, b),
                        Color.argb(0, r, g, b)
                    },
                    new float[]{0f, 0.28f, 0.56f, 0.80f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, interactiveGlowPaint);
        }
    }

    private final Runnable midnightRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            triggerMidnightVitrinRefresh();
            scheduleMidnightPosterRefresh();
        }
    };

    private void scheduleMidnightPosterRefresh() {
        Calendar now = Calendar.getInstance();
        Calendar nextMidnight = Calendar.getInstance();
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);
        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);

        long delayMs = nextMidnight.getTimeInMillis() - now.getTimeInMillis();
        handler.removeCallbacks(midnightRefreshRunnable);
        handler.postDelayed(midnightRefreshRunnable, Math.max(1000L, delayMs));
    }

    private void checkDailyMidnightVitrinRefresh() {
        try {
            SharedPreferences prefs = getSharedPreferences("vitrin_meta", MODE_PRIVATE);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            String lastDate = prefs.getString("last_refresh_date", "");
            if (!today.equals(lastDate)) {
                prefs.edit().putString("last_refresh_date", today).apply();
                triggerMidnightVitrinRefresh();
            }
        } catch (Throwable ignored) {
        }
    }

    private void triggerMidnightVitrinRefresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File cacheDir = getCacheDir();
                    if (cacheDir != null && cacheDir.exists()) {
                        File[] files = cacheDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().startsWith("vitrin_")) {
                                    f.delete();
                                }
                            }
                        }
                    }
                    fetchLiveTheatricalPosters();
                } catch (Throwable t) {
                    android.util.Log.e("TVHomeWeather", "Weather fetch error", t);
                }
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyThemeMode();
        SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String hCsv = p.getString("user_hidden_apps_csv", "");
        if (hCsv != null && !hCsv.isEmpty()) {
            for (String s : hCsv.split(",")) {
                if (!s.trim().isEmpty()) userHiddenApps.add(s.trim());
            }
        }
        buildUi();
        checkDailyMidnightVitrinRefresh();
        scheduleMidnightPosterRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyThemeMode();
        if (ambientBackdropView != null) {
            ambientBackdropView.startSeamlessBreathing();
        }
        loadAppsAndPopulate();
        updateClock();
        fetchLiveWeather();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        handler.removeCallbacks(weatherRefreshRunnable);
        handler.postDelayed(weatherRefreshRunnable, 30 * 60 * 1000L);
        handler.removeCallbacks(vitrinAutoSlide);
        handler.postDelayed(vitrinAutoSlide, 10000L);
        resetIdleScreensaverTimer();

        long now = System.currentTimeMillis();
        if (now - lastVitrinCheckTime > 15000L) {
            lastVitrinCheckTime = now;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    TvKeyInterceptorService.cleanAllBackgroundApps(MainActivity.this, "com.gathin.tvhome");
                    TvKeyInterceptorService.ensureAirPlayReceiverRunning(MainActivity.this);
                    fetchLiveTheatricalPosters();
                }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        if (ambientBackdropView != null) {
            ambientBackdropView.stopSeamlessBreathing();
        }
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(weatherRefreshRunnable);
        handler.removeCallbacks(idleScreensaverTrigger);
        handler.removeCallbacks(screensaverDriftRunnable);
        handler.removeCallbacks(vitrinAutoSlide);
        hideIdleScreensaver();
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (isScreensaverActive) {
                hideIdleScreensaver();
                resetIdleScreensaverTimer();
                return true;
            }
            resetIdleScreensaverTimer();

            if (isMovingTile) {
                int code = event.getKeyCode();
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    moveCarriedTileHorizontal(-1);
                    return true;
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    moveCarriedTileHorizontal(1);
                    return true;
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    moveCarriedTileVertical(-1);
                    return true;
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    moveCarriedTileVertical(1);
                    return true;
                } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) {
                    stopTileMove();
                    return true;
                } else if (code == KeyEvent.KEYCODE_MENU) {
                    showAppSimpleMenu(movingTileItem);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void resetIdleScreensaverTimer() {
        handler.removeCallbacks(idleScreensaverTrigger);
        handler.postDelayed(idleScreensaverTrigger, SCREENSAVER_IDLE_MS);
    }

    private void showIdleScreensaver() {
        if (screensaverOverlay == null || isScreensaverActive) return;
        isScreensaverActive = true;
        updateClock();
        screensaverCluster.setTranslationX(0f);
        screensaverCluster.setTranslationY(0f);
        screensaverOverlay.setAlpha(0f);
        screensaverOverlay.setVisibility(View.VISIBLE);
        screensaverOverlay.animate().alpha(1f).setDuration(600L).start();
        handler.removeCallbacks(screensaverDriftRunnable);
        handler.postDelayed(screensaverDriftRunnable, 40000L);
    }

    private void hideIdleScreensaver() {
        if (!isScreensaverActive || screensaverOverlay == null) return;
        isScreensaverActive = false;
        handler.removeCallbacks(screensaverDriftRunnable);
        screensaverOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (isScreensaverActive) {
            hideIdleScreensaver();
            resetIdleScreensaverTimer();
        }
    }

    private void applyThemeMode() {
        try {
            SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
            String mode = Settings.Global.getString(getContentResolver(), "tvhome_theme_mode");
            if (mode == null || mode.trim().isEmpty()) {
                mode = prefs.getString("theme_mode", "dark");
            }
            currentThemeMode = mode.toLowerCase();

            boolean isDaytime = true;
            if ("auto".equals(currentThemeMode)) {
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                isDaytime = (hour >= 7 && hour < 19);
            } else if ("light".equals(currentThemeMode)) {
                isDaytime = true;
            } else {
                isDaytime = false;
            }

            boolean isLight = ("light".equals(currentThemeMode)) || ("auto".equals(currentThemeMode) && isDaytime);

            UiModeManager uiManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (uiManager != null) {
                uiManager.setNightMode(isLight ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
            }

            if (ambientBackdropView != null) {
                ambientBackdropView.setLightMode(isLight);
            }

            if (clockText != null) {
                clockText.setTextColor(isLight ? Color.parseColor("#0F172A") : Color.parseColor("#F8FAFC"));
            }
            if (dateText != null) {
                dateText.setTextColor(isLight ? Color.parseColor("#475569") : Color.parseColor("#94A3B8"));
            }
            if (weatherText != null) {
                weatherText.setTextColor(isLight ? Color.parseColor("#0284C7") : Color.parseColor("#38BDF8"));
            }
        } catch (Throwable ignored) {
        }
    }

    private void fetchLiveWeather() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
                    String city = Settings.Global.getString(getContentResolver(), "tvhome_city");
                    if (city == null || city.trim().isEmpty()) {
                        city = prefs.getString("city", null);
                    }

                    String queryUrl = (city != null && !city.trim().isEmpty())
                            ? ("https://wttr.in/" + URLEncoder.encode(city.trim(), "UTF-8") + "?format=%t+%c")
                            : "https://wttr.in/?format=%l:+%t+%c";

                    android.util.Log.d("TVHomeWeather", "Fetching weather from: " + queryUrl);
                    URL url = new URL(queryUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "curl/8.4.0");
                    conn.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

                    int code = conn.getResponseCode();
                    android.util.Log.d("TVHomeWeather", "HTTP response code: " + code);
                    if (code == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        String line = br.readLine();
                        br.close();
                        android.util.Log.d("TVHomeWeather", "Raw response line: " + line);

                        if (line != null && !line.trim().isEmpty()) {
                            final String displayStr;
                            if (city != null && !city.trim().isEmpty()) {
                                displayStr = city.trim() + " • " + line.trim();
                            } else {
                                displayStr = line.trim();
                            }
                            android.util.Log.d("TVHomeWeather", "Final weather text: " + displayStr);

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (weatherText != null) {
                                        weatherText.setText(displayStr);
                                        weatherText.setVisibility(View.VISIBLE);
                                    }
                                }
                            });
                        }
                    }
                } catch (Throwable t) {
                    android.util.Log.e("TVHomeWeather", "Weather fetch error", t);
                }
            }
        }).start();
    }

    private void buildUi() {
        FrameLayout masterFrame = new FrameLayout(this);

        // Layer 0: Seamless Living Backdrop + Barely-Perceptible Alptuğ Signature Watermark + Interactive Ambilight
        ambientBackdropView = new SeamlessLivingBackdropView(this);
        masterFrame.addView(ambientBackdropView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setVerticalScrollBarEnabled(false);
        rootScroll.setClipChildren(false);
        rootScroll.setClipToPadding(false);
        rootScroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setClipChildren(false);
        mainContainer.setClipToPadding(false);
        mainContainer.setPadding(60, 28, 60, 48);
        rootScroll.addView(mainContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 1. MINIMAL TOP BAR: Alptuğ Signature Logo + Clock + Date on Left, Liquid Glass Vector Gear on Right
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(12, 0, 12, 16);

        LinearLayout leftBrandAndClock = new LinearLayout(this);
        leftBrandAndClock.setOrientation(LinearLayout.HORIZONTAL);
        leftBrandAndClock.setGravity(Gravity.CENTER_VERTICAL);

        ImageView brandTvLogo = new ImageView(this);
        brandTvLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Drawable logoDrawable = getDrawable(R.drawable.ic_tv_clean);
            if (logoDrawable != null) {
                logoDrawable = logoDrawable.mutate();
                logoDrawable.setTint(Color.parseColor("#F8FAFC"));
                brandTvLogo.setImageDrawable(logoDrawable);
            }
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(44, 44);
        logoLp.setMargins(0, 0, 18, 0);
        leftBrandAndClock.addView(brandTvLogo, logoLp);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#28FFFFFF"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(2, 34);
        divLp.setMargins(0, 0, 18, 0);
        leftBrandAndClock.addView(divider, divLp);

        LinearLayout clockLine = new LinearLayout(this);
        clockLine.setOrientation(LinearLayout.HORIZONTAL);
        clockLine.setGravity(Gravity.BOTTOM);

        clockText = new TextView(this);
        clockText.setTypeface(FontManager.getBlack(this));
        clockText.setTextSize(38f);
        clockText.setTextColor(Color.parseColor("#F8FAFC"));
        clockLine.addView(clockText);

        dateText = new TextView(this);
        dateText.setTypeface(FontManager.getMedium(this));
        dateText.setTextSize(15f);
        dateText.setPadding(16, 0, 0, 7);
        dateText.setTextColor(Color.parseColor("#94A3B8"));
        clockLine.addView(dateText);

        weatherText = new TextView(this);
        weatherText.setTypeface(FontManager.getMedium(this));
        weatherText.setTextSize(15f);
        weatherText.setPadding(18, 0, 0, 7);
        weatherText.setTextColor(Color.parseColor("#38BDF8"));
        weatherText.setVisibility(View.GONE);
        clockLine.addView(weatherText);

        leftBrandAndClock.addView(clockLine);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(leftBrandAndClock, leftLp);

        View gearIconBtn = createVectorGearButton(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) {
                    android.util.Log.e("TVHomeWeather", "Weather fetch error", t);
                }
            }
        }, new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showLauncherSettingsDialog();
                return true;
            }
        });
        topBar.addView(gearIconBtn);
        mainContainer.addView(topBar);

        // 2. MOVIE POSTER VITRİN (Hover-Only Wide Optical Bloom + Chromatic Hue Separation)
        heroScroll = new HorizontalScrollView(this);
        heroScroll.setHorizontalScrollBarEnabled(false);
        heroScroll.setClipToPadding(false);
        heroScroll.setClipChildren(false);
        heroShelfRow = new LinearLayout(this);
        heroShelfRow.setOrientation(LinearLayout.HORIZONTAL);
        heroShelfRow.setClipToPadding(false);
        heroShelfRow.setClipChildren(false);
        heroShelfRow.setPadding(30, 36, 30, 36);
        heroScroll.addView(heroShelfRow);
        mainContainer.addView(heroScroll);

        // 3. MAIN 4 FAVORITE APPS ROW (Frameless — no outer box around the 4 apps!)
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(0, 8, 0, 8);

        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dockLp.setMargins(0, 14, 0, 10);
        mainContainer.addView(dockRow, dockLp);

        // 4. ALL APPS LIQUID GLASS GRID (Aligned identically with the 4 main apps above)
        appsGridContainer = new LinearLayout(this);
        appsGridContainer.setOrientation(LinearLayout.VERTICAL);
        appsGridContainer.setClipChildren(false);
        appsGridContainer.setClipToPadding(false);
        mainContainer.addView(appsGridContainer);

        masterFrame.addView(rootScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 4B. REORDER HUD BAR (Arrow-key tile moving & direct actions: Kaldır & Ana Ekrandan Sil)
        reorderHudBar = new LinearLayout(this);
        reorderHudBar.setOrientation(LinearLayout.HORIZONTAL);
        reorderHudBar.setGravity(Gravity.CENTER);
        reorderHudBar.setBackground(createReorderHudBackground());
        reorderHudBar.setPadding(32, 14, 32, 14);
        reorderHudBar.setVisibility(View.GONE);

        reorderStatusText = new TextView(this);
        reorderStatusText.setTypeface(FontManager.getMedium(this));
        reorderStatusText.setTextSize(15f);
        reorderStatusText.setTextColor(Color.parseColor("#F8FAFC"));
        reorderStatusText.setText("Yön tuşları (← ↑ → ↓) ile taşıyın • [Tamam]: Bırak");
        reorderHudBar.addView(reorderStatusText);

        View hudSpacer = new View(this);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(36, 1);
        reorderHudBar.addView(hudSpacer, spLp);

        reorderHideBtn = createReorderButton("✕ Ana Ekrandan Sil", Color.parseColor("#E06C75"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (movingTileItem != null) {
                    hideAppFromHome(movingTileItem.packageName, movingTileItem.label);
                    stopTileMove();
                }
            }
        });
        reorderHudBar.addView(reorderHideBtn);

        View btnSpacer = new View(this);
        LinearLayout.LayoutParams bSpLp = new LinearLayout.LayoutParams(16, 1);
        reorderHudBar.addView(btnSpacer, bSpLp);

        reorderUninstallBtn = createReorderButton("🗑 Kaldır", Color.parseColor("#E5C07B"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (movingTileItem != null) {
                    String pkg = movingTileItem.packageName;
                    stopTileMove();
                    uninstallPackage(pkg);
                }
            }
        });
        reorderHudBar.addView(reorderUninstallBtn);

        View.OnKeyListener navUpListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        renderDockAndGrid();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                        stopTileMove();
                        return true;
                    }
                }
                return false;
            }
        };
        reorderHideBtn.setOnKeyListener(navUpListener);
        reorderUninstallBtn.setOnKeyListener(navUpListener);

        FrameLayout.LayoutParams hudLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        hudLp.setMargins(0, 0, 0, 36);
        masterFrame.addView(reorderHudBar, hudLp);

        // 5. 5-MINUTE IDLE SCREENSAVER OVERLAY
        screensaverOverlay = new FrameLayout(this);
        screensaverOverlay.setBackgroundColor(Color.parseColor("#050609"));
        screensaverOverlay.setVisibility(View.GONE);

        screensaverCluster = new LinearLayout(this);
        screensaverCluster.setOrientation(LinearLayout.VERTICAL);
        screensaverCluster.setGravity(Gravity.CENTER);

        ImageView dreamLogo = new ImageView(this);
        dreamLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            InputStream is = getAssets().open("signature_logo.png");
            Bitmap sigBmp = BitmapFactory.decodeStream(is);
            is.close();
            if (sigBmp != null) {
                dreamLogo.setImageBitmap(sigBmp);
            } else {
                Drawable d = getDrawable(R.drawable.ic_tv_clean);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(Color.WHITE);
                    dreamLogo.setImageDrawable(d);
                }
            }
        } catch (Throwable ignored) {
            try {
                Drawable d = getDrawable(R.drawable.ic_tv_clean);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(Color.WHITE);
                    dreamLogo.setImageDrawable(d);
                }
            } catch (Throwable ignored2) {
            }
        }
        LinearLayout.LayoutParams dreamLogoLp = new LinearLayout.LayoutParams(260, 160);
        dreamLogoLp.setMargins(0, 0, 0, 24);
        screensaverCluster.addView(dreamLogo, dreamLogoLp);

        screensaverClockText = new TextView(this);
        screensaverClockText.setTypeface(FontManager.getBlack(this));
        screensaverClockText.setTextSize(68f);
        screensaverClockText.setTextColor(Color.parseColor("#F8FAFC"));
        screensaverClockText.setGravity(Gravity.CENTER);
        screensaverCluster.addView(screensaverClockText);

        screensaverDateText = new TextView(this);
        screensaverDateText.setTypeface(FontManager.getMedium(this));
        screensaverDateText.setTextSize(18f);
        screensaverDateText.setTextColor(Color.parseColor("#94A3B8"));
        screensaverDateText.setGravity(Gravity.CENTER);
        screensaverCluster.addView(screensaverDateText);

        FrameLayout.LayoutParams clusterLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        screensaverOverlay.addView(screensaverCluster, clusterLp);

        masterFrame.addView(screensaverOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(masterFrame);
        ambientBackdropView.startSeamlessBreathing();
        populateMoviePosterVitrin();
        loadAppsAndPopulate();
        updateClock();
    }

    private View createVectorGearButton(View.OnClickListener onClick, View.OnLongClickListener onLongClick) {
        final FrameLayout frame = new FrameLayout(this);
        frame.setFocusable(true);
        frame.setClickable(true);
        if (onLongClick != null) frame.setLongClickable(true);

        final GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(145, 38, 38, 44), Color.argb(125, 18, 18, 22)}
        );
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(2, Color.argb(75, 255, 255, 255));
        frame.setBackground(bg);

        final ImageView gearIcon = new ImageView(this);
        gearIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Drawable d = getDrawable(R.drawable.ic_settings_gear);
            if (d != null) {
                d = d.mutate();
                d.setTint(Color.parseColor("#F1F5F9"));
                gearIcon.setImageDrawable(d);
            }
        } catch (Throwable ignored) {
        }

        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(26, 26, Gravity.CENTER);
        frame.addView(gearIcon, iconLp);

        frame.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? 3 : 2,
                        hasFocus ? Color.WHITE : Color.argb(75, 255, 255, 255));
                v.animate()
                        .scaleX(hasFocus ? 1.15f : 1.0f)
                        .scaleY(hasFocus ? 1.15f : 1.0f)
                        .rotation(hasFocus ? 45f : 0f)
                        .setDuration(180)
                        .start();
                if (hasFocus && ambientBackdropView != null) {
                    ambientBackdropView.animateToTarget(v, Color.parseColor("#E2E8F0"));
                }
            }
        });
        frame.setOnClickListener(onClick);
        if (onLongClick != null) frame.setOnLongClickListener(onLongClick);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(52, 52);
        frame.setLayoutParams(lp);
        return frame;
    }

    /**
     * Populates the Vitrin with 3D Parallax CoverFlow Movie Posters.
     * Overlapping intertwined cards with distance-based falloff:
     * - Hovered/focused card is distinctly larger (1.30x), in the front (highest elevation & Z), with vivid neon bloom.
     * - Neighbors scale down smoothly, recede into background depth, dim, and blur.
     * - Deep drop shadows physically ground the overlapping cards against the dark TV backdrop.
     */
    private void populateMoviePosterVitrin() {
        heroShelfRow.removeAllViews();
        posterCardFrames.clear();
        posterImageViews.clear();
        posterBloomViews.clear();
        posterNeonColors.clear();
        posterImdbIds.clear();
        posterTitles.clear();

        int prevPosterColor = 0;
        SharedPreferences vitrinPrefs = getSharedPreferences("vitrin_cache", MODE_PRIVATE);

        for (int i = 0; i < BUNDLED_MOVIES.length; i++) {
            final int slot = i;
            String assetPath = BUNDLED_MOVIES[i][0];
            String imdbId = BUNDLED_MOVIES[i][1];
            String initialTitle = BUNDLED_MOVIES[i][2];

            String savedSlug = vitrinPrefs.getString("slug_" + i, null);
            String savedTitle = vitrinPrefs.getString("title_" + i, null);
            if (savedTitle != null && !savedTitle.isEmpty()) {
                initialTitle = savedTitle;
            }
            if (savedSlug != null && !savedSlug.isEmpty()) {
                String savedImdb = vitrinPrefs.getString("imdb_" + savedSlug, null);
                if (savedImdb != null && !savedImdb.isEmpty()) {
                    imdbId = savedImdb;
                }
            }

            posterImdbIds.add(imdbId);
            posterTitles.add(initialTitle);

            final FrameLayout cardFrame = new FrameLayout(this);
            cardFrame.setFocusable(true);
            cardFrame.setClickable(true);
            cardFrame.setClipChildren(false);
            cardFrame.setClipToPadding(false);
            cardFrame.setElevation(8f);

            File cachedFile = null;
            if (savedSlug != null && !savedSlug.isEmpty()) {
                cachedFile = new File(getCacheDir(), "wt_p_" + Math.abs(savedSlug.hashCode()) + ".jpg");
            }
            if (cachedFile == null || !cachedFile.exists() || cachedFile.length() < 1024) {
                cachedFile = new File(getCacheDir(), "vitrin_wt_" + i + ".jpg");
            }
            if (!cachedFile.exists() || cachedFile.length() < 1024) {
                cachedFile = new File(getCacheDir(), "vitrin_ts_" + i + ".jpg");
            }
            if (!cachedFile.exists() || cachedFile.length() < 1024) {
                cachedFile = new File(getCacheDir(), "vitrin_poster_" + i + ".jpg");
            }
            Bitmap rawBmp = null;
            if (cachedFile.exists() && cachedFile.length() > 1024) {
                rawBmp = BitmapFactory.decodeFile(cachedFile.getAbsolutePath());
            }
            if (rawBmp == null) {
                try {
                    InputStream is = getAssets().open(assetPath);
                    rawBmp = BitmapFactory.decodeStream(is);
                    is.close();
                } catch (Throwable t) {
                    android.util.Log.e("TVHomeWeather", "Weather fetch error", t);
                }
            }

            int sampledColor = YouTubeDisguiseHelper.sampleDistinctPosterColor(rawBmp, prevPosterColor, i);
            prevPosterColor = sampledColor;
            posterNeonColors.add(sampledColor);

            // Layer 0: Deep Atmospheric Cinema Drop Shadow (always visible, creates 3D separation between overlapping cards)
            final ImageView dropShadowView = new ImageView(this);
            dropShadowView.setScaleType(ImageView.ScaleType.FIT_XY);
            dropShadowView.setImageBitmap(
                    YouTubeDisguiseHelper.createCardDropShadow(VITRIN_POSTER_W, VITRIN_POSTER_H, VITRIN_BLOOM_PAD, 26f)
            );
            FrameLayout.LayoutParams shadowLp = new FrameLayout.LayoutParams(
                    VITRIN_POSTER_W + VITRIN_BLOOM_PAD * 2,
                    VITRIN_POSTER_H + VITRIN_BLOOM_PAD * 2,
                    Gravity.CENTER
            );
            shadowLp.setMargins(-VITRIN_BLOOM_PAD, -VITRIN_BLOOM_PAD, -VITRIN_BLOOM_PAD, -VITRIN_BLOOM_PAD);
            cardFrame.addView(dropShadowView, shadowLp);

            // Layer 1: Hover-Only Wide-Dispersion Soft Neon Bloom (alpha = 0f at rest, bright on hover)
            final ImageView hoverBloomView = new ImageView(this);
            hoverBloomView.setScaleType(ImageView.ScaleType.FIT_XY);
            hoverBloomView.setImageBitmap(
                    YouTubeDisguiseHelper.createWideSoftHoverBloom(VITRIN_POSTER_W, VITRIN_POSTER_H, VITRIN_BLOOM_PAD, 26f, sampledColor)
            );
            hoverBloomView.setAlpha(0f);
            FrameLayout.LayoutParams bloomLp = new FrameLayout.LayoutParams(
                    VITRIN_POSTER_W + VITRIN_BLOOM_PAD * 2,
                    VITRIN_POSTER_H + VITRIN_BLOOM_PAD * 2,
                    Gravity.CENTER
            );
            bloomLp.setMargins(-VITRIN_BLOOM_PAD, -VITRIN_BLOOM_PAD, -VITRIN_BLOOM_PAD, -VITRIN_BLOOM_PAD);
            cardFrame.addView(hoverBloomView, bloomLp);

            // Layer 2: Clean 2:3 Liquid Glass Movie Poster (0.88 alpha at rest)
            final ImageView posterView = new ImageView(this);
            posterView.setScaleType(ImageView.ScaleType.FIT_XY);
            posterView.setAlpha(0.88f);
            if (rawBmp != null) {
                posterView.setImageBitmap(YouTubeDisguiseHelper.createLiquidGlassPosterBitmap(rawBmp));
            }
            cardFrame.addView(posterView, new FrameLayout.LayoutParams(
                    VITRIN_POSTER_W,
                    VITRIN_POSTER_H,
                    Gravity.CENTER
            ));

            cardFrame.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        isVitrinFocused = true;
                        updateVitrinParallax(slot);
                        if (ambientBackdropView != null && slot < posterNeonColors.size()) {
                            ambientBackdropView.animateToTarget(v, posterNeonColors.get(slot));
                        }
                        smoothScrollToVitrinCard(v);
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                boolean anyFocused = false;
                                for (FrameLayout cf : posterCardFrames) {
                                    if (cf.hasFocus()) {
                                        anyFocused = true;
                                        break;
                                    }
                                }
                                if (!anyFocused) {
                                    isVitrinFocused = false;
                                    updateVitrinParallax(-1);
                                }
                            }
                        });
                    }
                }
            });

            cardFrame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String id = slot < posterImdbIds.size() ? posterImdbIds.get(slot) : "tt15239678";
                    String title = slot < posterTitles.size() ? posterTitles.get(slot) : (slot < BUNDLED_MOVIES.length ? BUNDLED_MOVIES[slot][2] : "Film");
                    openMovieItem(title, id);
                }
            });

            // Intertwining: Each card after the first has negative margin, overlapping like a physical deck
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(VITRIN_POSTER_W, VITRIN_POSTER_H);
            int leftMargin = (i == 0) ? 60 : VITRIN_OVERLAP_MARGIN;
            int rightMargin = (i == BUNDLED_MOVIES.length - 1) ? 60 : 0;
            lp.setMargins(leftMargin, 30, rightMargin, 30);
            heroShelfRow.addView(cardFrame, lp);

            posterCardFrames.add(cardFrame);
            posterImageViews.add(posterView);
            posterBloomViews.add(hoverBloomView);
        }
    }

    /**
     * Updates the 3D Parallax Depth for all Vitrin cards based on distance to focused card:
     * - dist = 0: En önde, belirgin büyük (scale 1.30x), parlak neon ışık, en yüksek elevation (50f)
     * - dist = 1: Hemen yanındaki kartlar arkaya çekilir, küçülür (0.94x), hafif soluklaşır
     * - dist = 2: İkinci mesafe kartlar daha arkaya gider (0.80x), derinliğe gömülür (0.55 alpha)
     * - dist >= 3: En uzaktaki kartlar en arkada, en küçük (0.68x), en soluk (0.35 alpha)
     * - focusedIndex < 0: Rölanti / odak yok durumu, tüm kartlar eşit hizaya döner
     */
    private void updateVitrinParallax(int focusedIndex) {
        int count = posterCardFrames.size();
        if (count == 0) return;

        // In hardware accelerated Android, translationZ and elevation dictate 3D Z-ordering
        // without re-indexing or disturbing LinearLayout's fixed horizontal layout.

        for (int i = 0; i < count; i++) {
            final FrameLayout card = posterCardFrames.get(i);
            final ImageView posterView = i < posterImageViews.size() ? posterImageViews.get(i) : null;
            final ImageView bloomView = i < posterBloomViews.size() ? posterBloomViews.get(i) : null;

            if (focusedIndex < 0) {
                card.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationY(0f)
                        .translationZ(0f)
                        .setDuration(220L)
                        .start();
                card.setElevation(8f);
                if (posterView != null) {
                    posterView.animate().alpha(0.88f).setDuration(220L).start();
                }
                if (bloomView != null) {
                    bloomView.animate().alpha(0.0f).setDuration(180L).start();
                }
            } else {
                int dist = Math.abs(i - focusedIndex);
                float targetScale;
                float targetAlpha;
                float targetTransZ;
                float targetTransY;
                float elevation;
                float bloomAlpha;

                if (dist == 0) {
                    // Hovered / focused: En önde, belirgin bir biçimde büyük
                    targetScale = 1.30f;
                    targetAlpha = 1.0f;
                    targetTransZ = 65f;
                    targetTransY = -22f;
                    elevation = 50f;
                    bloomAlpha = 1.0f;
                } else if (dist == 1) {
                    targetScale = 0.94f;
                    targetAlpha = 0.82f;
                    targetTransZ = 25f;
                    targetTransY = 0f;
                    elevation = 20f;
                    bloomAlpha = 0.0f;
                } else if (dist == 2) {
                    targetScale = 0.80f;
                    targetAlpha = 0.55f;
                    targetTransZ = 12f;
                    targetTransY = 8f;
                    elevation = 10f;
                    bloomAlpha = 0.0f;
                } else {
                    targetScale = 0.68f;
                    targetAlpha = 0.35f;
                    targetTransZ = 0f;
                    targetTransY = 14f;
                    elevation = 2f;
                    bloomAlpha = 0.0f;
                }

                card.setElevation(elevation);
                card.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .translationY(targetTransY)
                        .translationZ(targetTransZ)
                        .setDuration(220L)
                        .start();

                if (posterView != null) {
                    posterView.animate()
                            .alpha(targetAlpha)
                            .setDuration(220L)
                            .start();
                }
                if (bloomView != null) {
                    bloomView.animate()
                            .alpha(bloomAlpha)
                            .setDuration(bloomAlpha > 0f ? 240L : 180L)
                            .start();
                }
            }
        }
    }

    private void smoothScrollToVitrinCard(final View card) {
        if (heroScroll == null || card == null) return;
        heroScroll.post(new Runnable() {
            @Override
            public void run() {
                int scrollW = heroScroll.getWidth();
                if (scrollW <= 0) return;
                int cardLeft = card.getLeft();
                int cardW = card.getWidth();
                int cardCenterX = cardLeft + cardW / 2;
                int targetX = cardCenterX - (scrollW / 2);
                if (targetX < 0) targetX = 0;
                heroScroll.smoothScrollTo(targetX, 0);
            }
        });
    }

    private void fetchLiveTheatricalPosters() {
        try {
            String vitrinPref = Settings.Global.getString(getContentResolver(), "tvhome_vitrin_source");
            boolean useWebteizle = false;
            if ("webteizle".equalsIgnoreCase(vitrinPref)) {
                useWebteizle = true;
            } else if ("cinemeta".equalsIgnoreCase(vitrinPref)) {
                useWebteizle = false;
            } else {
                useWebteizle = Locale.getDefault().getLanguage().equalsIgnoreCase("tr");
            }

            if (useWebteizle) {
                fetchFromWebteizleSource();
            } else {
                fetchFromCinemetaFallback();
            }
        } catch (Throwable t) {
            try {
                fetchFromCinemetaFallback();
            } catch (Throwable ignored) {
            }
        }
    }

    private void fetchFromWebteizleSource() {
        List<String[]> list = new ArrayList<>();
        try {
            URL url = new URL("https://webteizle.info/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) {
                fetchFromCinemetaFallback();
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "windows-1254"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            br.close();

            Pattern p = Pattern.compile("<div class=\"card golgever\">\\s*<a[^>]+href=\"(/hakkinda/[^\"]+)\"[^>]*>.*?<img[^>]+data-src=\"([^\"]+)\".*?<div class=\"filmname\">([^<]+)</div>", Pattern.DOTALL);
            Matcher m = p.matcher(sb.toString());
            while (m.find() && list.size() < 24) {
                String hakkindaSlug = m.group(1).trim();
                String rawPoster = m.group(2).trim();
                String title = m.group(3).replace("&amp;", "&").replace("&#39;", "'").trim();
                String posterUrl = rawPoster.startsWith("http") ? rawPoster : ("https://webteizle.info" + rawPoster);
                list.add(new String[]{title, posterUrl, hakkindaSlug});
            }
        } catch (Throwable t) {
            fetchFromCinemetaFallback();
            return;
        }

        if (list.isEmpty()) {
            fetchFromCinemetaFallback();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("vitrin_cache", MODE_PRIVATE);
        int count = Math.min(BUNDLED_MOVIES.length, list.size());
        int prevColor = 0;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            final String title = list.get(i)[0];
            final String posterUrl = list.get(i)[1];
            final String slug = list.get(i)[2];

            String currentTitle = (idx < posterTitles.size()) ? posterTitles.get(idx) : "";
            String savedSlug = prefs.getString("slug_" + idx, "");
            boolean needsUpdate = !slug.equals(savedSlug) || !title.equals(currentTitle);

            // Resolve IMDb ID from cache or Webteizle /hakkinda/<slug>
            String resolvedImdbId = prefs.getString("imdb_" + slug, null);
            if (resolvedImdbId == null || resolvedImdbId.isEmpty()) {
                try {
                    URL hUrl = new URL("https://webteizle.info" + slug);
                    HttpURLConnection hConn = (HttpURLConnection) hUrl.openConnection();
                    hConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
                    hConn.setConnectTimeout(4000);
                    hConn.setReadTimeout(4000);
                    if (hConn.getResponseCode() == 200) {
                        BufferedReader hbr = new BufferedReader(new InputStreamReader(hConn.getInputStream(), "windows-1254"));
                        StringBuilder hsb = new StringBuilder();
                        String hLine;
                        while ((hLine = hbr.readLine()) != null) hsb.append(hLine).append("\n");
                        hbr.close();

                        Matcher imMatcher = Pattern.compile("imdb\\.com/title/(tt\\d+)").matcher(hsb.toString());
                        if (imMatcher.find()) {
                            resolvedImdbId = imMatcher.group(1);
                            prefs.edit().putString("imdb_" + slug, resolvedImdbId).apply();
                        }
                    }
                } catch (Throwable t) {
                    android.util.Log.e("TVHomeWeather", "Weather fetch error", t);
                }
            }

            if (resolvedImdbId == null || resolvedImdbId.isEmpty()) {
                if (idx < BUNDLED_MOVIES.length) {
                    resolvedImdbId = BUNDLED_MOVIES[idx][1];
                } else {
                    resolvedImdbId = "tt15239678";
                }
            }

            final String finalImdbId = resolvedImdbId;
            String fileKey = "wt_p_" + Math.abs(slug.hashCode()) + ".jpg";
            File cacheFile = new File(getCacheDir(), fileKey);
            try {
                if (!cacheFile.exists() || cacheFile.length() < 1024) {
                    URL pUrl = new URL(posterUrl);
                    HttpURLConnection pConn = (HttpURLConnection) pUrl.openConnection();
                    pConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    pConn.setConnectTimeout(5000);
                    pConn.setReadTimeout(5000);
                    if (pConn.getResponseCode() == 200) {
                        InputStream is = pConn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(cacheFile);
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                        fos.close();
                        is.close();
                    }
                }
            } catch (Throwable ignored) {
            }

            if (cacheFile.exists() && cacheFile.length() > 1024) {
                if (needsUpdate || (idx < posterImageViews.size() && posterImageViews.get(idx).getDrawable() == null)) {
                    Bitmap rawBmp = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                    if (rawBmp != null) {
                        final int distinctColor = YouTubeDisguiseHelper.sampleDistinctPosterColor(rawBmp, prevColor, idx);
                        prevColor = distinctColor;
                        final Bitmap glassPoster = YouTubeDisguiseHelper.createLiquidGlassPosterBitmap(rawBmp);
                        final Bitmap hoverBloom = YouTubeDisguiseHelper.createWideSoftHoverBloom(VITRIN_POSTER_W, VITRIN_POSTER_H, VITRIN_BLOOM_PAD, 26f, distinctColor);

                        prefs.edit().putString("slug_" + idx, slug).putString("title_" + idx, title).apply();

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (idx < posterImageViews.size()) {
                                    posterImageViews.get(idx).setImageBitmap(glassPoster);
                                }
                                if (idx < posterBloomViews.size()) {
                                    posterBloomViews.get(idx).setImageBitmap(hoverBloom);
                                }
                                if (idx < posterNeonColors.size()) {
                                    posterNeonColors.set(idx, distinctColor);
                                }
                                if (idx < posterImdbIds.size()) {
                                    posterImdbIds.set(idx, finalImdbId);
                                }
                                if (idx < posterTitles.size()) {
                                    posterTitles.set(idx, title);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    private void fetchFromCinemetaFallback() {
        try {
            URL url = new URL("https://v3-cinemeta.strem.io/catalog/movie/top.json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() != 200) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONArray metas = root.optJSONArray("metas");
            if (metas == null) return;

            SharedPreferences prefs = getSharedPreferences("vitrin_cache", MODE_PRIVATE);
            int count = Math.min(BUNDLED_MOVIES.length, metas.length());
            int prevColor = 0;
            for (int i = 0; i < count; i++) {
                JSONObject m = metas.getJSONObject(i);
                final String imdbId = m.optString("imdb_id", "");
                final String title = m.optString("name", "Film");
                String posterUrl = m.optString("poster", "");
                if (posterUrl.isEmpty() && !imdbId.isEmpty()) {
                    posterUrl = "https://images.metahub.space/poster/medium/" + imdbId + "/img";
                }
                if (posterUrl.isEmpty()) continue;

                String fileKey = !imdbId.isEmpty() ? ("cm_p_" + imdbId + ".jpg") : ("vitrin_poster_" + i + ".jpg");
                File cacheFile = new File(getCacheDir(), fileKey);
                String currentTitle = (i < posterTitles.size()) ? posterTitles.get(i) : "";
                String savedImdb = prefs.getString("cm_imdb_" + i, "");
                boolean needsUpdate = !imdbId.equals(savedImdb) || !title.equals(currentTitle);

                if (!cacheFile.exists() || cacheFile.length() < 1024) {
                    URL pUrl = new URL(posterUrl);
                    HttpURLConnection pConn = (HttpURLConnection) pUrl.openConnection();
                    pConn.setConnectTimeout(4000);
                    pConn.setReadTimeout(4000);
                    if (pConn.getResponseCode() == 200) {
                        InputStream is = pConn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(cacheFile);
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                        fos.close();
                        is.close();
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 1024) {
                    if (needsUpdate || (i < posterImageViews.size() && posterImageViews.get(i).getDrawable() == null)) {
                        Bitmap rawBmp = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                        if (rawBmp != null) {
                            final int distinctColor = YouTubeDisguiseHelper.sampleDistinctPosterColor(rawBmp, prevColor, i);
                            prevColor = distinctColor;
                            final Bitmap glassPoster = YouTubeDisguiseHelper.createLiquidGlassPosterBitmap(rawBmp);
                            final Bitmap hoverBloom = YouTubeDisguiseHelper.createWideSoftHoverBloom(VITRIN_POSTER_W, VITRIN_POSTER_H, VITRIN_BLOOM_PAD, 26f, distinctColor);
                            final int idx = i;

                            prefs.edit().putString("cm_imdb_" + i, imdbId).putString("title_" + i, title).apply();

                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (idx < posterImageViews.size()) {
                                        posterImageViews.get(idx).setImageBitmap(glassPoster);
                                    }
                                    if (idx < posterBloomViews.size()) {
                                        posterBloomViews.get(idx).setImageBitmap(hoverBloom);
                                    }
                                    if (idx < posterNeonColors.size()) {
                                        posterNeonColors.set(idx, distinctColor);
                                    }
                                    if (idx < posterImdbIds.size() && !imdbId.isEmpty()) {
                                        posterImdbIds.set(idx, imdbId);
                                    }
                                    if (idx < posterTitles.size()) {
                                        posterTitles.set(idx, title);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void openMovieItem(String title, String imdbId) {
        if (imdbId != null && !imdbId.isEmpty()) {
            try {
                Intent nuvio = new Intent();
                nuvio.setComponent(new ComponentName("com.nuvio.tv", "com.nuvio.tv.MainActivity"));
                nuvio.putExtra("launchMode", "stream");
                nuvio.putExtra("contentId", imdbId);
                nuvio.putExtra("contentType", "movie");
                nuvio.putExtra("videoId", imdbId);
                nuvio.putExtra("name", title);
                nuvio.putExtra("playOnLoad", true);
                nuvio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(nuvio);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            PackageManager pm = getPackageManager();
            Intent launch = pm.getLeanbackLaunchIntentForPackage("com.nuvio.tv");
            if (launch == null) launch = pm.getLaunchIntentForPackage("com.nuvio.tv");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private void openMovieInStremio(String imdbId) {
        openMovieItem("Film", imdbId);
    }

    private void loadAppsAndPopulate() {
        PackageManager pm = getPackageManager();
        Set<String> seenPackages = new HashSet<>();
        List<AppTileItem> discovered = new ArrayList<>();

        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvApps = pm.queryIntentActivities(leanbackIntent, 0);

        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> phoneApps = pm.queryIntentActivities(mainIntent, 0);

        List<ResolveInfo> combined = new ArrayList<>();
        if (tvApps != null) combined.addAll(tvApps);
        if (phoneApps != null) combined.addAll(phoneApps);

        for (ResolveInfo ri : combined) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            String lowerPkg = pkg != null ? pkg.toLowerCase() : "";
            if (pkg == null || seenPackages.contains(pkg) || HIDDEN_SYSTEM_PACKAGES.contains(pkg)
                    || userHiddenApps.contains(pkg)
                    || lowerPkg.contains("factory") || lowerPkg.contains("webview") || lowerPkg.contains("mediaplayer")
                    || lowerPkg.contains("rctest") || lowerPkg.contains("channeleditor")
                    || lowerPkg.contains("airplay") || lowerPkg.contains("nova")
                    || lowerPkg.equals("com.mediatek.tv.service") || lowerPkg.contains("bein")) {
                continue;
            }
            seenPackages.add(pkg);

            String rawLabel = ri.loadLabel(pm) != null ? ri.loadLabel(pm).toString() : pkg;
            String cleanLabel = YouTubeDisguiseHelper.getCleanLabel(pkg, rawLabel);

            Drawable rawBanner = ri.activityInfo.loadBanner(pm);
            Drawable rawIcon = null;
            try {
                rawIcon = ri.activityInfo.loadIcon(pm);
            } catch (Throwable ignored) {
            }

            AppTileItem item = new AppTileItem();
            item.packageName = pkg;
            item.label = cleanLabel;
            item.neonColor = YouTubeDisguiseHelper.getAppNeonColor(pkg);
            item.bannerDrawable = YouTubeDisguiseHelper.createDesignerLogoCard(
                    this, pkg, rawLabel, rawBanner, rawIcon, true
            );
            discovered.add(item);
        }

        ensureEssentialAppTile(discovered, seenPackages, "com.turkcell.ott", "TV+");
        if (!seenPackages.contains("com.instantbits.cast.receiver") && !seenPackages.contains("com.instantbits.cast.webvideo")) {
            ensureEssentialAppTile(discovered, seenPackages, "com.instantbits.cast.receiver", "Web Video Caster");
        }
        if (!hasAnyTizenTube(seenPackages)) {
            ensureEssentialAppTile(discovered, seenPackages, "io.gh.reisxd.tizentube.cobalt", "YouTube");
        }
        ensureEssentialAppTile(discovered, seenPackages, "com.nuvio.tv", "Nuvio");

        // Ensure Chrome is discoverable in apps grid
        String chromePkg = seenPackages.contains("org.cromite.cromite") ? "org.cromite.cromite" : "com.android.chrome";
        if (!userHiddenApps.contains(chromePkg)) {
            ensureEssentialAppTile(discovered, seenPackages, chromePkg, "Chrome");
        }

        dockItems.clear();
        gridItems.clear();

        String[] targetDockOrder = new String[] {
            "com.turkcell.ott",
            seenPackages.contains("com.instantbits.cast.receiver") ? "com.instantbits.cast.receiver" : "com.instantbits.cast.webvideo",
            findInstalledTizenTubePackage(seenPackages),
            "com.nuvio.tv",
            "io.github.jqssun.airplay"
        };

        Set<String> dockSet = new HashSet<>();
        for (String dockPkg : targetDockOrder) {
            for (AppTileItem item : discovered) {
                if (item.packageName.equals(dockPkg) && !dockSet.contains(item.packageName)) {
                    dockItems.add(item);
                    dockSet.add(item.packageName);
                    break;
                }
            }
        }

        for (AppTileItem item : discovered) {
            if (!dockSet.contains(item.packageName)) {
                gridItems.add(item);
            }
        }

        applySavedAppOrder(gridItems);
        renderDockAndGrid();
    }

    private boolean hasAnyTizenTube(Set<String> pkgs) {
        for (String p : pkgs) {
            if (YouTubeDisguiseHelper.isTizenTube(p)) return true;
        }
        return false;
    }

    private String findInstalledTizenTubePackage(Set<String> pkgs) {
        for (String p : pkgs) {
            if (YouTubeDisguiseHelper.isTizenTube(p)) return p;
        }
        return "io.gh.reisxd.tizentube.cobalt";
    }

    private void ensureEssentialAppTile(List<AppTileItem> list, Set<String> seen, String pkg, String label) {
        if (seen.contains(pkg)) return;
        seen.add(pkg);
        AppTileItem item = new AppTileItem();
        item.packageName = pkg;
        item.label = label;
        item.neonColor = YouTubeDisguiseHelper.getAppNeonColor(pkg);
        item.bannerDrawable = YouTubeDisguiseHelper.createDesignerLogoCard(this, pkg, label, null, null, true);
        list.add(item);
    }

    private void applySavedAppOrder(List<AppTileItem> items) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String csv = prefs.getString(KEY_APP_ORDER, "");
        if (csv == null || csv.isEmpty()) return;
        final List<String> orderList = Arrays.asList(csv.split(","));
        Collections.sort(items, new Comparator<AppTileItem>() {
            @Override
            public int compare(AppTileItem a, AppTileItem b) {
                int ia = orderList.indexOf(a.packageName);
                int ib = orderList.indexOf(b.packageName);
                if (ia == -1 && ib == -1) return a.label.compareToIgnoreCase(b.label);
                if (ia == -1) return 1;
                if (ib == -1) return -1;
                return Integer.compare(ia, ib);
            }
        });
    }

    private void saveCurrentAppOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gridItems.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(gridItems.get(i).packageName);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_APP_ORDER, sb.toString())
                .apply();
    }

    /**
     * Both the 4 main apps (dockRow) and the grid rows below use identical frameless 4-column spacing
     * (no extra outer box around the 4 main apps!).
     */
    private void renderDockAndGrid() {
        dockRow.removeAllViews();
        for (int i = 0; i < dockItems.size(); i++) {
            final AppTileItem item = dockItems.get(i);
            View tile = createLiquidGlassAppTile(item, true, i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 214, 1f);
            lp.setMargins(12, 6, 12, 6);
            dockRow.addView(tile, lp);
        }

        appsGridContainer.removeAllViews();
        int columns = 4;
        LinearLayout currentRow = null;
        for (int i = 0; i < gridItems.size(); i++) {
            if (i % columns == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setClipChildren(false);
                currentRow.setClipToPadding(false);
                currentRow.setPadding(0, 8, 0, 8);
                appsGridContainer.addView(currentRow);
            }
            final AppTileItem item = gridItems.get(i);
            View tile = createLiquidGlassAppTile(item, false, i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 214, 1f);
            lp.setMargins(12, 6, 12, 6);
            currentRow.addView(tile, lp);
        }
        if (currentRow != null && gridItems.size() % columns != 0) {
            int rem = columns - (gridItems.size() % columns);
            for (int r = 0; r < rem; r++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 214, 1f);
                lp.setMargins(12, 6, 12, 6);
                currentRow.addView(spacer, lp);
            }
        }
    }

    private View createLiquidGlassAppTile(final AppTileItem item, final boolean inDock, final int index) {
        final FrameLayout cardFrame = new FrameLayout(this);
        cardFrame.setFocusable(true);
        cardFrame.setClickable(true);
        cardFrame.setLongClickable(true);
        cardFrame.setClipChildren(false);
        cardFrame.setClipToPadding(false);

        final int cardW = 380;
        final int cardH = 214;
        final int bloomPad = 68; // Wide 68px optical dispersion around all 4 sides on hover

        // Layer 1: Hover-Only Wide-Dispersion Optical Neon Bloom (alpha = 0f at rest)
        final ImageView hoverBloomView = new ImageView(this);
        hoverBloomView.setScaleType(ImageView.ScaleType.FIT_XY);
        hoverBloomView.setImageBitmap(
                YouTubeDisguiseHelper.createWideSoftHoverBloom(cardW, cardH, bloomPad, 24f, item.neonColor)
        );
        hoverBloomView.setAlpha(0f);
        FrameLayout.LayoutParams bloomLp = new FrameLayout.LayoutParams(
                cardW + bloomPad * 2,
                cardH + bloomPad * 2,
                Gravity.CENTER
        );
        bloomLp.setMargins(-bloomPad, -bloomPad, -bloomPad, -bloomPad);
        cardFrame.addView(hoverBloomView, bloomLp);

        // Layer 2: Translucent Apple Liquid Glass Card with Always-On Internal Logo Neon
        final ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_XY);
        img.setImageDrawable(item.bannerDrawable);
        img.setAlpha(0.90f); // Apple tvOS HIG: slightly calmer at rest, full luminance on focus
        cardFrame.addView(img, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        boolean isCurrentMoving = isMovingTile && movingTileItem != null && movingTileItem.packageName.equals(item.packageName);
        if (isCurrentMoving) {
            hoverBloomView.setAlpha(1.0f);
            cardFrame.setScaleX(1.12f);
            cardFrame.setScaleY(1.12f);
            cardFrame.setTranslationY(-10f);
            cardFrame.setTranslationZ(45f);
        }

        cardFrame.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (isMovingTile) return;
                hoverBloomView.animate()
                        .alpha(hasFocus ? 1.0f : 0.0f)
                        .setDuration(hasFocus ? 260L : 190L)
                        .start();
                img.animate()
                        .alpha(hasFocus ? 1.0f : 0.90f)
                        .setDuration(180L)
                        .start();
                v.animate()
                        .scaleX(hasFocus ? 1.085f : 1.0f)
                        .scaleY(hasFocus ? 1.085f : 1.0f)
                        .translationY(hasFocus ? -8f : 0f)
                        .translationZ(hasFocus ? 28f : 0f)
                        .setDuration(190L)
                        .start();
                if (hasFocus && ambientBackdropView != null) {
                    ambientBackdropView.animateToTarget(v, item.neonColor);
                }
            }
        });

        cardFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchAppItem(item);
            }
        });

        final boolean[] longPressConsumed = new boolean[]{false};
        final Handler tileKeyHandler = new Handler(Looper.getMainLooper());
        final Runnable tileLongPressAction = new Runnable() {
            @Override
            public void run() {
                longPressConsumed[0] = true;
                startTileMove(item, inDock, index);
            }
        };

        cardFrame.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getRepeatCount() == 0) {
                            longPressConsumed[0] = false;
                            tileKeyHandler.removeCallbacks(tileLongPressAction);
                            tileKeyHandler.postDelayed(tileLongPressAction, 480L);
                        }
                        return false;
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        tileKeyHandler.removeCallbacks(tileLongPressAction);
                        if (longPressConsumed[0]) {
                            longPressConsumed[0] = false;
                            return true;
                        }
                        return false;
                    }
                }
                return false;
            }
        });

        cardFrame.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startTileMove(item, inDock, index);
                return true;
            }
        });

        return cardFrame;
    }

    private void launchAppItem(AppTileItem item) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(item.packageName);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(item.packageName);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, item.label + " bulunamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppSimpleMenu(final AppTileItem item) {
        if (item == null) return;
        final String[] actions = new String[] {
            "Ana Ekrandan Sil",
            "Kaldır"
        };

        new AlertDialog.Builder(this)
                .setTitle(item.label)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            hideAppFromHome(item.packageName, item.label);
                            if (isMovingTile) stopTileMove();
                        } else if (which == 1) {
                            if (isMovingTile) stopTileMove();
                            uninstallPackage(item.packageName);
                        }
                    }
                })
                .show();
    }

    private void hideAppFromHome(String pkg, String label) {
        userHiddenApps.add(pkg);
        StringBuilder sb = new StringBuilder();
        for (String p : userHiddenApps) {
            if (sb.length() > 0) sb.append(",");
            sb.append(p);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("user_hidden_apps_csv", sb.toString())
                .apply();
        Toast.makeText(this, label + " ana ekrandan silindi.", Toast.LENGTH_SHORT).show();
        loadAppsAndPopulate();
    }

    private void startTileMove(AppTileItem item, boolean inDock, int index) {
        isMovingTile = true;
        movingTileItem = item;
        movingTileInDock = inDock;
        movingTileIndex = index;
        if (reorderHudBar != null) {
            reorderHudBar.setVisibility(View.VISIBLE);
        }
        if (reorderStatusText != null) {
            reorderStatusText.setText(item.label + " taşınıyor (← ↑ → ↓) • [Tamam]: Bırak");
        }
        Toast.makeText(this, item.label + ": Yön tuşları (← ↑ → ↓) ile taşıyın, [Tamam]: Bırak.", Toast.LENGTH_SHORT).show();
        renderDockAndGrid();
    }

    private void stopTileMove() {
        if (!isMovingTile) return;
        isMovingTile = false;
        movingTileItem = null;
        movingTileInDock = false;
        movingTileIndex = -1;
        if (reorderHudBar != null) {
            reorderHudBar.setVisibility(View.GONE);
        }
        saveCurrentAppOrder();
        Toast.makeText(this, "Yeni konum kaydedildi.", Toast.LENGTH_SHORT).show();
        renderDockAndGrid();
    }

    private void moveCarriedTileHorizontal(int delta) {
        List<AppTileItem> list = movingTileInDock ? dockItems : gridItems;
        if (movingTileIndex < 0 || movingTileIndex >= list.size()) return;
        int target = movingTileIndex + delta;
        if (target < 0 || target >= list.size()) return;
        Collections.swap(list, movingTileIndex, target);
        movingTileIndex = target;
        saveCurrentAppOrder();
        renderDockAndGrid();
    }

    private void moveCarriedTileVertical(int direction) {
        if (movingTileInDock) {
            if (direction > 0 && !gridItems.isEmpty()) {
                AppTileItem item = dockItems.remove(movingTileIndex);
                gridItems.add(0, item);
                movingTileInDock = false;
                movingTileIndex = 0;
                saveCurrentAppOrder();
                renderDockAndGrid();
            }
        } else {
            int columns = 4;
            int target = movingTileIndex + (direction * columns);
            if (direction < 0 && target < 0 && !dockItems.isEmpty()) {
                AppTileItem item = gridItems.remove(movingTileIndex);
                dockItems.add(item);
                movingTileInDock = true;
                movingTileIndex = dockItems.size() - 1;
                saveCurrentAppOrder();
                renderDockAndGrid();
            } else if (direction > 0 && target >= gridItems.size()) {
                if (reorderHideBtn != null) {
                    reorderHideBtn.requestFocus();
                }
            } else if (target >= 0 && target < gridItems.size()) {
                Collections.swap(gridItems, movingTileIndex, target);
                movingTileIndex = target;
                saveCurrentAppOrder();
                renderDockAndGrid();
            }
        }
    }

    private TextView createReorderButton(String text, final int accentColor, View.OnClickListener onClick) {
        final TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTypeface(FontManager.getBold(this));
        btn.setTextSize(14f);
        btn.setTextColor(Color.WHITE);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setPadding(24, 10, 24, 10);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#262938"));
        normalBg.setCornerRadius(16f);
        normalBg.setStroke(2, Color.parseColor("#3E445E"));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(accentColor);
        focusBg.setCornerRadius(16f);

        btn.setBackground(normalBg);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                btn.setBackground(hasFocus ? focusBg : normalBg);
                btn.setTextColor(hasFocus ? Color.parseColor("#0F1117") : Color.WHITE);
                btn.setScaleX(hasFocus ? 1.08f : 1.0f);
                btn.setScaleY(hasFocus ? 1.08f : 1.0f);
            }
        });
        btn.setOnClickListener(onClick);
        return btn;
    }

    private Drawable createReorderHudBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor("#F00F111A"));
        d.setCornerRadius(22f);
        d.setStroke(2, Color.parseColor("#38BDF8"));
        return d;
    }

    private void uninstallPackage(String pkg) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            openAppDetails(pkg);
        }
    }

    private void openAppDetails(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void showCollapsibleInputsDialog() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add("TV+ (Canlı Yayın)");
        actions.add(new Runnable() {
            @Override
            public void run() {
                AppTileItem tvPlus = new AppTileItem();
                tvPlus.packageName = "com.turkcell.ott";
                tvPlus.label = "TV+";
                launchAppItem(tvPlus);
            }
        });

        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> inputs = tim.getTvInputList();
                if (inputs != null) {
                    for (final TvInputInfo info : inputs) {
                        CharSequence customLabel = info.loadCustomLabel(this);
                        CharSequence baseLabel = info.loadLabel(this);
                        String name = customLabel != null ? customLabel.toString()
                                : (baseLabel != null ? baseLabel.toString() : info.getId());
                        labels.add(name);
                        actions.add(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Uri uri = Uri.parse("content://android.media.tv/passthrough/" + Uri.encode(info.getId()));
                                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(i);
                                } catch (Throwable t) {
                                    openSystemInputsMenu();
                                }
                            }
                        });
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < actions.size()) {
                            actions.get(which).run();
                        }
                    }
                })
                .show();
    }

    private void openSystemInputsMenu() {
        String[] actions = new String[] {
            "com.android.tv.action.OPEN_INPUTS",
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

    private void showLauncherSettingsDialog() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String[] options = new String[] {
            "🌤️  Hava Durumu Şehri Ayarla (Weather City)",
            "🌓  Tema Modu (Saf OLED / Gece-Gündüz / Aydınlık)",
            "🎬  Sinema Vitrin Akış Kaynağı (Webteizle / Cinemeta)",
            "🔌  Giriş Kaynakları (HDMI / TV+)",
            "🎧  AirPods & Bluetooth Cihazları",
            "🌙  Ekran Koruyucuyu Şimdi Başlat",
            "👁️  Gizlenen Uygulamaları Geri Getir",
            "🎮  Kumanda Tuşu Öğret: TV+",
            "🎮  Kumanda Tuşu Öğret: Netflix -> Nuvio",
            "🎮  Kumanda Tuşu Öğret: Amazon Prime -> Ayarlar HUD",
            "⚙️  Android Cihaz Ayarları"
        };

        new AlertDialog.Builder(this)
                .setTitle("TV Master Başlatıcı Ayarları")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showSetCityDialog();
                        } else if (which == 1) {
                            showThemeSelectionDialog();
                        } else if (which == 2) {
                            showVitrinSourceDialog();
                        } else if (which == 3) {
                            showCollapsibleInputsDialog();
                        } else if (which == 4) {
                            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else if (which == 5) {
                            showIdleScreensaver();
                        } else if (which == 6) {
                            userHiddenApps.clear();
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString("user_hidden_apps_csv", "").apply();
                            loadAppsAndPopulate();
                            Toast.makeText(MainActivity.this, "Tüm gizlenen uygulamalar geri yüklendi.", Toast.LENGTH_SHORT).show();
                        } else if (which == 7) {
                            prefs.edit().putString(TvKeyInterceptorService.KEY_LEARN_MODE, "tvplus").apply();
                            Toast.makeText(MainActivity.this, "Kumandadaki TV+ tuşuna basın...", Toast.LENGTH_LONG).show();
                        } else if (which == 8) {
                            prefs.edit().putString(TvKeyInterceptorService.KEY_LEARN_MODE, "netflix").apply();
                            Toast.makeText(MainActivity.this, "Kumandadaki NETFLIX tuşuna basın...", Toast.LENGTH_LONG).show();
                        } else if (which == 9) {
                            prefs.edit().putString(TvKeyInterceptorService.KEY_LEARN_MODE, "amazon").apply();
                            Toast.makeText(MainActivity.this, "Kumandadaki AMAZON PRIME tuşuna basın...", Toast.LENGTH_LONG).show();
                        } else if (which == 10) {
                            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                    }
                })
                .show();
    }

    private void showSetCityDialog() {
        SharedPreferences p = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        String current = Settings.Global.getString(getContentResolver(), "tvhome_city");
        if (current == null) current = p.getString("city", "");

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(current);
        input.setHint("Örn: Istanbul, Ankara, Berlin, New York");
        input.setPadding(30, 20, 30, 20);

        new AlertDialog.Builder(this)
                .setTitle("Hava Durumu Şehri")
                .setMessage("Ana ekranda anlık hava durumunu görmek istediğiniz şehri girin:")
                .setView(input)
                .setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String val = input.getText().toString().trim();
                        Settings.Global.putString(getContentResolver(), "tvhome_city", val);
                        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("city", val).apply();
                        fetchLiveWeather();
                        Toast.makeText(MainActivity.this, "Şehir kaydedildi: " + val, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showThemeSelectionDialog() {
        String[] modes = new String[] {
            "Saf OLED Siyah (Deep Obsidian Dark Noir)",
            "Otomatik Gece / Gündüz (Auto Day/Night)",
            "Sürekli Aydınlık Buzlu Cam (Modern Frosted Light Glass)"
        };
        final String[] keys = new String[]{"dark", "auto", "light"};

        new AlertDialog.Builder(this)
                .setTitle("Tema Tercihi")
                .setItems(modes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String selected = keys[which];
                        Settings.Global.putString(getContentResolver(), "tvhome_theme_mode", selected);
                        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("theme_mode", selected).apply();
                        applyThemeMode();
                        Toast.makeText(MainActivity.this, "Tema uygulandı: " + modes[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showVitrinSourceDialog() {
        String[] sources = new String[] {
            "Webteizle (Türkçe Dublaj & Altyazı • 4K/1080p)",
            "Cinemeta Global Box Office (Uluslararası / İngilizce)"
        };
        final String[] keys = new String[]{"webteizle", "cinemeta"};

        new AlertDialog.Builder(this)
                .setTitle("Sinema Vitrin Akış Kaynağı")
                .setItems(sources, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String selected = keys[which];
                        Settings.Global.putString(getContentResolver(), "tvhome_vitrin_source", selected);
                        fetchLiveTheatricalPosters();
                        Toast.makeText(MainActivity.this, "Vitrin kaynağı güncellendi: " + sources[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void updateClock() {
        Date now = new Date();
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", Locale.getDefault());
        String t = timeFmt.format(now);
        String d = dateFmt.format(now);
        if (clockText != null) clockText.setText(t);
        if (dateText != null) dateText.setText(d);
        if (screensaverClockText != null) screensaverClockText.setText(t);
        if (screensaverDateText != null) screensaverDateText.setText(d);
    }
}
