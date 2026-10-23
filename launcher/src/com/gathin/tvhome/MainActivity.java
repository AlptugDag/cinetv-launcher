package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.PixelFormat;
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
import android.media.AudioManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
import android.graphics.Rect;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    private static final String KEY_DOCK_ORDER = "custom_dock_order_csv";
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
    private VibrantScreensaverView screensaverView;
    private boolean isScreensaverActive = false;
    private TextView vitrinCategoryLabel;
    private FrameLayout brandMedallion;
    private View gearIconBtn;
    private View btnNetwork;
    private View btnInputs;
    private View btnAllApps;
    private View searchBarView;
    private BroadcastReceiver packageReceiver;
    private FrameLayout settingsModalOverlay;
    private FrameLayout allAppsOverlay;
    private FrameLayout aiResponseFloatingCard;
    private LinearLayout appActionPillBar;
    private EditText searchInputView;


    private final List<AppTileItem> dockItems = new ArrayList<>();
    private final List<AppTileItem> gridItems = new ArrayList<>();
    private final List<ImageView> posterImageViews = new ArrayList<>();
    private final List<ImageView> posterBloomViews = new ArrayList<>();
    private final List<FrameLayout> posterCardFrames = new ArrayList<>();
    private final List<Integer> posterNeonColors = new ArrayList<>();
    private final List<String> posterImdbIds = new ArrayList<>();
    private final List<String> posterTitles = new ArrayList<>();
    private final List<String> posterUrls = new ArrayList<>();
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

    private final Runnable autoShutdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isScreensaverActive) return;
            if (isMediaPlaying()) {
                // Media is actively streaming or playing audio, defer shutdown
                handler.postDelayed(this, 2 * 60 * 1000L);
                return;
            }
            executeFullPowerDown();
        }
    };

    private boolean isMediaPlaying() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            return am != null && am.isMusicActive();
        } catch (Throwable t) {
            return false;
        }
    }

    private void executeFullPowerDown() {
        try {
            Runtime.getRuntime().exec(new String[]{"reboot", "-p"});
        } catch (Throwable ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"}); // KEYCODE_POWER
        } catch (Throwable ignored) {
        }
        try {
            Intent shut = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
            shut.putExtra("android.intent.extra.KEY_CONFIRM", false);
            shut.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(shut);
        } catch (Throwable ignored) {
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                java.lang.reflect.Method goToSleep = pm.getClass().getMethod("goToSleep", long.class);
                goToSleep.invoke(pm, SystemClock.uptimeMillis());
            }
        } catch (Throwable ignored) {
        }
    }

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
        private final Bitmap watermarkBitmap;
        private final Bitmap tileBitmap;
        private final Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint watermarkGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
            Bitmap bm = null;
            Bitmap scaled = null;
            try {
                InputStream is = ctx.getAssets().open("signature_logo.png");
                bm = BitmapFactory.decodeStream(is);
                is.close();
                if (bm != null) {
                    int tSize = 114; // Clearly perceivable, recognizable motif tile size
                    scaled = Bitmap.createScaledBitmap(bm, tSize, tSize, true);
                }
            } catch (Throwable ignored) {
            }
            watermarkBitmap = bm;
            tileBitmap = scaled;
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

            // 2. 3D Parallax Non-Overlapping Geometric Motif Pattern ("Desen")
            if (tileBitmap != null) {
                canvas.save();
                float parallaxX = (glowX - (w / 2f)) * -0.035f;
                float parallaxY = (glowY - (h / 2f)) * -0.025f;

                int tileSize = tileBitmap.getWidth();
                int stepX = 168;
                int stepY = 146;
                float halfTile = tileSize * 0.5f;

                // Translucent luxury watermark sheen (clearly recognizable yet elegant)
                watermarkPaint.setAlpha(isLightMode ? 18 : 28);

                int cols = (w / stepX) + 3;
                int rows = (h / stepY) + 3;
                Rect src = new Rect(0, 0, tileBitmap.getWidth(), tileBitmap.getHeight());
                RectF dst = new RectF(-halfTile, -halfTile, halfTile, halfTile);

                for (int row = -1; row < rows; row++) {
                    float y = row * stepY + parallaxY;
                    // Stagger alternate rows by half-step for a diamond monogram motif
                    float xOffset = ((row % 2) != 0) ? (stepX * 0.5f) : 0f;
                    for (int col = -1; col < cols; col++) {
                        float x = col * stepX + xOffset + parallaxX;

                        // Deterministic rotation per tile so angles are varied but completely stable
                        int seed = (row * 374761393) ^ (col * 668265263);
                        seed = (seed ^ (seed >> 13)) * 1274126177;
                        float rot = ((seed % 360) + 360) % 360;

                        canvas.save();
                        canvas.translate(x, y);
                        canvas.rotate(rot);
                        canvas.drawBitmap(tileBitmap, src, dst, watermarkPaint);
                        canvas.restore();
                    }
                }
                canvas.restore();
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
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 900000);
            Settings.Secure.putInt(getContentResolver(), "sleep_timeout", 900000);
            Settings.Secure.putInt(getContentResolver(), "screensaver_timeout", 300000);
            Settings.Secure.putInt(getContentResolver(), "screensaver_enabled", 1);
            Settings.Secure.putString(getContentResolver(), "screensaver_components", "com.gathin.tvhome/com.gathin.tvhome.ClockDream");
        } catch (Throwable ignored) {}

        try {
            getWindow().setBackgroundDrawable(new ColorDrawable(0xFF040508));
            getWindow().setFormat(PixelFormat.OPAQUE);
        } catch (Throwable ignored) {}
        applyThemeMode();

        TvWebRemoteServer.start(this);
        TvWebRemoteServer.setCallback(new TvWebRemoteServer.RemoteActionCallback() {
            @Override
            public void onMovieSearchRequested(final String query) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleSearchOrAiQuery(query);
                    }
                });
            }

            @Override
            public void onAppLaunchRequested(final String pkg) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        launchAppByPackage(pkg);
                    }
                });
            }
        });

        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadAppsAndPopulate(true);
            }
        };
        try {
            IntentFilter pFilter = new IntentFilter();
            pFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            pFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            pFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            pFilter.addDataScheme("package");
            registerReceiver(packageReceiver, pFilter);
        } catch (Throwable ignored) {
        }

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
        triggerAutoAiVitrinIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (packageReceiver != null) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Throwable ignored) {}
            packageReceiver = null;
        }
        TvWebRemoteServer.stop();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyThemeMode();
        cleanupBackgroundAppsMemory();
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

    private void cleanupBackgroundAppsMemory() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                        if (procs != null) {
                            String myPkg = getPackageName();
                            for (ActivityManager.RunningAppProcessInfo p : procs) {
                                if (p.pkgList != null) {
                                    for (String pkg : p.pkgList) {
                                        if (!pkg.equals(myPkg)
                                                && !pkg.startsWith("com.android.systemui")
                                                && !pkg.startsWith("com.google.android.inputmethod")
                                                && !pkg.startsWith("com.mediatek.wwtv")) {
                                            am.killBackgroundProcesses(pkg);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    System.gc();
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }

    @Override
    protected void onPause() {
        if (ambientBackdropView != null) {
            ambientBackdropView.stopSeamlessBreathing();
        }
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(weatherRefreshRunnable);
        handler.removeCallbacks(idleScreensaverTrigger);
        handler.removeCallbacks(autoShutdownRunnable);
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

            int kc = event.getKeyCode();
            // Dedicated Remote Hardware Buttons
            if (kc == 207 || kc == 188 || kc == 183 || kc == 265 || kc == 229) {
                openNuvioApp();
                return true;
            }
            if (kc == 218 || kc == 189 || kc == 190 || kc == 284) {
                openYouTubeApp();
                return true;
            }
            if (kc == 185 || kc == 209 || kc == 228 || kc == 192) {
                openWebVideoCasterApp();
                return true;
            }
            if (kc == 277 || kc == 170 || kc == 172 || kc == 186) {
                openTvPlusApp();
                return true;
            }

            if (isMovingTile) {
                resetTileMoveTimeout();
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
                    showAppLeanbackContextMenu(movingTileItem, movingTileInDock, movingTileIndex);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void resetIdleScreensaverTimer() {
        handler.removeCallbacks(idleScreensaverTrigger);
        handler.removeCallbacks(autoShutdownRunnable);
        handler.postDelayed(idleScreensaverTrigger, SCREENSAVER_IDLE_MS);
    }

    private void showIdleScreensaver() {
        if (screensaverOverlay == null || isScreensaverActive) return;
        isScreensaverActive = true;
        if (screensaverView != null) {
            screensaverView.startAnimating();
        }
        screensaverOverlay.setAlpha(0f);
        screensaverOverlay.setVisibility(View.VISIBLE);
        screensaverOverlay.animate().alpha(1f).setDuration(600L).start();
        handler.removeCallbacks(autoShutdownRunnable);
        handler.postDelayed(autoShutdownRunnable, 5 * 60 * 1000L); // 5 min screensaver = 10 min total idle
    }

    private void hideIdleScreensaver() {
        if (!isScreensaverActive || screensaverOverlay == null) return;
        isScreensaverActive = false;
        handler.removeCallbacks(autoShutdownRunnable);
        if (screensaverView != null) {
            screensaverView.stopAnimating();
        }
        screensaverOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (isScreensaverActive) {
            hideIdleScreensaver();
            resetIdleScreensaverTimer();
            return;
        }
        if (aiResponseFloatingCard != null && aiResponseFloatingCard.getVisibility() == View.VISIBLE) {
            hideAiResponseFloatingCard();
            return;
        }
        if (settingsModalOverlay != null && settingsModalOverlay.getVisibility() == View.VISIBLE) {
            hideSettingsModal();
            return;
        }
        if (allAppsOverlay != null && allAppsOverlay.getVisibility() == View.VISIBLE) {
            hideAllAppsDrawer();
            return;
        }
        if (appActionPillBar != null && appActionPillBar.getVisibility() == View.VISIBLE) {
            hideAppActionPillBar();
            return;
        }
        if (isMovingTile) {
            stopTileMove();
            return;
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

            boolean isLight = "light".equalsIgnoreCase(currentThemeMode);

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

        brandMedallion = new FrameLayout(this);
        brandMedallion.setFocusable(true);
        brandMedallion.setClickable(true);
        brandMedallion.setBackground(null); // Pure transparent logo floating directly on canvas

        final ImageView brandTvLogo = new ImageView(this);
        brandTvLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            InputStream is = getAssets().open("signature_logo.png");
            Bitmap logoBm = BitmapFactory.decodeStream(is);
            is.close();
            if (logoBm != null) {
                brandTvLogo.setImageBitmap(logoBm);
            } else {
                Drawable logoDrawable = getDrawable(R.drawable.ic_user_signature);
                if (logoDrawable == null) logoDrawable = getDrawable(R.drawable.ic_tv_clean);
                if (logoDrawable != null) {
                    logoDrawable = logoDrawable.mutate();
                    logoDrawable.setTint(Color.parseColor("#F8FAFC"));
                    brandTvLogo.setImageDrawable(logoDrawable);
                }
            }
        } catch (Throwable ignored) {
            try {
                Drawable logoDrawable = getDrawable(R.drawable.ic_user_signature);
                if (logoDrawable == null) logoDrawable = getDrawable(R.drawable.ic_tv_clean);
                if (logoDrawable != null) {
                    logoDrawable = logoDrawable.mutate();
                    logoDrawable.setTint(Color.parseColor("#F8FAFC"));
                    brandTvLogo.setImageDrawable(logoDrawable);
                }
            } catch (Throwable ignored2) {
            }
        }

        FrameLayout.LayoutParams innerLogoLp = new FrameLayout.LayoutParams(76, 76, Gravity.CENTER);
        brandMedallion.addView(brandTvLogo, innerLogoLp);

        brandMedallion.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate()
                        .scaleX(hasFocus ? 1.18f : 1.0f)
                        .scaleY(hasFocus ? 1.18f : 1.0f)
                        .setDuration(220L)
                        .start();
                if (hasFocus && ambientBackdropView != null) {
                    ambientBackdropView.animateToTarget(v, Color.parseColor("#38BDF8"));
                }
            }
        });

        brandMedallion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLauncherSettingsModal();
            }
        });

        brandMedallion.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (searchBarView != null) {
                            searchBarView.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(82, 82);
        logoLp.setMargins(0, 0, 22, 0);
        leftBrandAndClock.addView(brandMedallion, logoLp);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(2, 52);
        divLp.setMargins(0, 0, 22, 0);
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

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        leftLp.setMargins(0, 0, 36, 0);
        topBar.addView(leftBrandAndClock, leftLp);

        // Center Search / Gemini Chat Bar
        searchBarView = createSearchBar();
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchLp.setMargins(0, 0, 28, 0);
        topBar.addView(searchBarView, searchLp);

        // Header Action Buttons: Network, Inputs, All Apps, Settings
        LinearLayout headerActions = new LinearLayout(this);
        headerActions.setOrientation(LinearLayout.HORIZONTAL);
        headerActions.setGravity(Gravity.CENTER_VERTICAL);
        headerActions.setClipChildren(false);
        headerActions.setClipToPadding(false);

        btnNetwork = createHeaderIconButton(R.drawable.ic_network, "Ağ Ayarları", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable ignored) {}
                }
            }
        }, null);

        btnInputs = createHeaderIconButton(R.drawable.ic_inputs, "Girişler", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTvInputs();
            }
        }, null);

        btnAllApps = createHeaderIconButton(R.drawable.ic_all_apps, "Tüm Uygulamalar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAllAppsDrawer();
            }
        }, null);

        gearIconBtn = createHeaderIconButton(R.drawable.ic_settings_gear, "Ayarlar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLauncherSettingsModal();
            }
        }, new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable ignored) {}
                return true;
            }
        });

        // Key Navigation for Header Action Buttons
        btnNetwork.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && searchBarView != null) {
                        searchBarView.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && btnInputs != null) {
                        btnInputs.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        btnInputs.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && btnNetwork != null) {
                        btnNetwork.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && btnAllApps != null) {
                        btnAllApps.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        btnAllApps.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && btnInputs != null) {
                        btnInputs.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && gearIconBtn != null) {
                        gearIconBtn.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        gearIconBtn.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && btnAllApps != null) {
                        btnAllApps.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(52, 52);
        btnLp.setMargins(10, 0, 0, 0);
        headerActions.addView(btnNetwork, btnLp);
        headerActions.addView(btnInputs, btnLp);
        headerActions.addView(btnAllApps, btnLp);
        headerActions.addView(gearIconBtn, btnLp);

        topBar.addView(headerActions);
        mainContainer.addView(topBar);

        // 2. MOVIE POSTER VITRİN (Hover-Only Wide Optical Bloom + Chromatic Hue Separation)
        vitrinCategoryLabel = new TextView(this);
        vitrinCategoryLabel.setTypeface(FontManager.getMedium(this));
        vitrinCategoryLabel.setTextSize(13f);
        vitrinCategoryLabel.setTextColor(Color.parseColor("#94A3B8"));
        vitrinCategoryLabel.setLetterSpacing(0.12f);
        vitrinCategoryLabel.setText("GÜNÜN AKILLI SİNEMA SEÇKİSİ");
        LinearLayout.LayoutParams vclLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        vclLp.setMargins(32, 10, 0, 0);
        mainContainer.addView(vitrinCategoryLabel, vclLp);

        heroScroll = new HorizontalScrollView(this);
        heroScroll.setHorizontalScrollBarEnabled(false);
        heroScroll.setClipToPadding(false);
        heroScroll.setClipChildren(false);
        heroShelfRow = new LinearLayout(this);
        heroShelfRow.setOrientation(LinearLayout.HORIZONTAL);
        heroShelfRow.setClipToPadding(false);
        heroShelfRow.setClipChildren(false);
        heroShelfRow.setPadding(80, 40, 80, 40);
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

        screensaverView = new VibrantScreensaverView(this);
        screensaverOverlay.addView(screensaverView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        masterFrame.addView(screensaverOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        
        // 6. LIQUID GLASS SETTINGS MODAL OVERLAY
        settingsModalOverlay = new FrameLayout(this);
        settingsModalOverlay.setBackgroundColor(Color.argb(215, 3, 5, 9));
        settingsModalOverlay.setVisibility(View.GONE);
        settingsModalOverlay.setFocusable(true);
        settingsModalOverlay.setClickable(true);
        masterFrame.addView(settingsModalOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 7. FULL-SCREEN LIQUID GLASS ALL APPS DRAWER OVERLAY
        allAppsOverlay = new FrameLayout(this);
        allAppsOverlay.setBackgroundColor(Color.argb(238, 3, 5, 9));
        allAppsOverlay.setVisibility(View.GONE);
        allAppsOverlay.setFocusable(true);
        allAppsOverlay.setClickable(true);
        masterFrame.addView(allAppsOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 8. FLOATING AI CONVERSATION HUD CARD
        aiResponseFloatingCard = new FrameLayout(this);
        aiResponseFloatingCard.setVisibility(View.GONE);
        aiResponseFloatingCard.setClipChildren(false);
        aiResponseFloatingCard.setClipToPadding(false);
        masterFrame.addView(aiResponseFloatingCard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 9. APP LEANBACK ACTION PILL BAR (Floating action bar on long-press)
        appActionPillBar = new LinearLayout(this);
        appActionPillBar.setOrientation(LinearLayout.HORIZONTAL);
        appActionPillBar.setGravity(Gravity.CENTER);
        appActionPillBar.setBackground(createReorderHudBackground());
        appActionPillBar.setPadding(28, 12, 28, 12);
        appActionPillBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        pillLp.setMargins(0, 0, 0, 40);
        masterFrame.addView(appActionPillBar, pillLp);

        setContentView(masterFrame);
        ambientBackdropView.startSeamlessBreathing();
        populateMoviePosterVitrin();
        loadAppsAndPopulate(true);
        updateClock();
    }

    private View createHeaderIconButton(int iconRes, String contentDesc, View.OnClickListener onClick, View.OnLongClickListener onLongClick) {
        final FrameLayout frame = new FrameLayout(this);
        frame.setFocusable(true);
        frame.setClickable(true);
        if (onLongClick != null) frame.setLongClickable(true);

        final GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(165, 30, 41, 59), Color.argb(145, 15, 23, 42)}
        );
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(2, Color.argb(85, 255, 255, 255));
        frame.setBackground(bg);

        final ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Drawable d = getDrawable(iconRes);
            if (d != null) {
                d = d.mutate();
                d.setTint(Color.parseColor("#F1F5F9"));
                icon.setImageDrawable(d);
            }
        } catch (Throwable ignored) {
        }

        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(26, 26, Gravity.CENTER);
        frame.addView(icon, iconLp);

        frame.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? 3 : 2,
                        hasFocus ? Color.parseColor("#38BDF8") : Color.argb(85, 255, 255, 255));
                bg.setColor(hasFocus ? Color.argb(220, 30, 41, 59) : Color.argb(165, 30, 41, 59));
                v.animate()
                        .scaleX(hasFocus ? 1.15f : 1.0f)
                        .scaleY(hasFocus ? 1.15f : 1.0f)
                        .setDuration(180L)
                        .start();
                if (hasFocus && ambientBackdropView != null) {
                    ambientBackdropView.animateToTarget(v, Color.parseColor("#38BDF8"));
                }
            }
        });

        frame.setOnClickListener(onClick);
        if (onLongClick != null) {
            frame.setOnLongClickListener(onLongClick);
        }
        return frame;
    }

    private View createSearchBar() {
        final LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setFocusable(false);
        bar.setClickable(true);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setPadding(18, 6, 18, 6);

        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(24f);
        bg.setColor(Color.argb(165, 15, 23, 42)); // Glass obsidian
        bg.setStroke(2, Color.argb(80, 255, 255, 255));
        bar.setBackground(bg);

        ImageView searchIcon = new ImageView(this);
        searchIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            Drawable sd = getDrawable(R.drawable.ic_search);
            if (sd != null) {
                sd = sd.mutate();
                sd.setTint(Color.parseColor("#38BDF8"));
                searchIcon.setImageDrawable(sd);
            }
        } catch (Throwable ignored) {}
        LinearLayout.LayoutParams silp = new LinearLayout.LayoutParams(22, 22);
        silp.setMargins(0, 0, 12, 0);
        bar.addView(searchIcon, silp);

        searchInputView = new EditText(this);
        searchInputView.setHint("Film ara veya Gemini'ye sor...");
        searchInputView.setHintTextColor(Color.parseColor("#64748B"));
        searchInputView.setTextColor(Color.WHITE);
        searchInputView.setTextSize(14f);
        searchInputView.setTypeface(FontManager.getMedium(this));
        searchInputView.setBackground(null);
        searchInputView.setSingleLine(true);
        searchInputView.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInputView.setPadding(0, 0, 0, 0);

        searchInputView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bar.animate()
                        .scaleX(hasFocus ? 1.04f : 1.0f)
                        .scaleY(hasFocus ? 1.04f : 1.0f)
                        .setDuration(180L)
                        .start();
                bg.setColor(hasFocus ? Color.argb(225, 30, 41, 59) : Color.argb(165, 15, 23, 42));
                bg.setStroke(hasFocus ? 3 : 2, hasFocus ? Color.parseColor("#38BDF8") : Color.argb(80, 255, 255, 255));
                if (hasFocus && ambientBackdropView != null) {
                    ambientBackdropView.animateToTarget(bar, Color.parseColor("#38BDF8"));
                }
            }
        });

        searchInputView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    String q = v.getText().toString().trim();
                    if (!q.isEmpty()) {
                        handleSearchOrAiQuery(q);
                        v.setText("");
                    }
                    return true;
                }
                return false;
            }
        });

        searchInputView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    EditText et = (EditText) v;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && et.getSelectionStart() == 0) {
                        if (brandMedallion != null) {
                            brandMedallion.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && et.getSelectionEnd() == et.getText().length()) {
                        if (btnNetwork != null) {
                            btnNetwork.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(searchInputView, inLp);

        bar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (searchInputView != null) {
                    searchInputView.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(searchInputView, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        });

        return bar;
    }

    private void openTvInputs() {
        String[][] targets = new String[][]{
            {"com.android.tv.settings", "com.android.tv.settings.inputs.InputsActivity"},
            {"com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity"},
            {"com.google.android.tv.frameworkpackagestubs", "com.google.android.tv.frameworkpackagestubs.InputActivity"}
        };
        for (String[] t : targets) {
            try {
                Intent intent = new Intent();
                intent.setClassName(t[0], t[1]);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Throwable ignored) {}
        }
        try {
            Intent intent = new Intent("android.media.tv.action.QUERY_CONTENT_RATING_SYSTEMS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Throwable ignored) {}
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {}
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
        posterUrls.clear();

        int prevPosterColor = 0;
        SharedPreferences vitrinPrefs = getSharedPreferences("vitrin_cache", MODE_PRIVATE);

        for (int i = 0; i < BUNDLED_MOVIES.length; i++) {
            final int slot = i;
            String assetPath = BUNDLED_MOVIES[i][0];
            String imdbId = BUNDLED_MOVIES[i][1];
            String initialTitle = BUNDLED_MOVIES[i][2];

            String savedCmImdb = vitrinPrefs.getString("cm_imdb_" + i, null);
            if (savedCmImdb != null && !savedCmImdb.isEmpty()) {
                imdbId = savedCmImdb;
            }
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
            posterUrls.add("");

            final FrameLayout cardFrame = new FrameLayout(this);
            cardFrame.setFocusable(true);
            cardFrame.setClickable(true);
            cardFrame.setClipChildren(false);
            cardFrame.setClipToPadding(false);
            cardFrame.setElevation(8f);

            File cachedFile = new File(getCacheDir(), "cm_p_" + imdbId + ".jpg");
            if (!cachedFile.exists() || cachedFile.length() < 1024) {
                if (savedSlug != null && !savedSlug.isEmpty()) {
                    cachedFile = new File(getCacheDir(), "wt_p_" + Math.abs(savedSlug.hashCode()) + ".jpg");
                }
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
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(cachedFile.getAbsolutePath(), opts);
                int sample = 1;
                if (opts.outWidth > 320 || opts.outHeight > 480) {
                    int halfW = opts.outWidth / 2;
                    int halfH = opts.outHeight / 2;
                    while ((halfW / sample) >= 320 && (halfH / sample) >= 480) {
                        sample *= 2;
                    }
                }
                opts.inSampleSize = Math.max(1, sample);
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                rawBmp = BitmapFactory.decodeFile(cachedFile.getAbsolutePath(), opts);
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
                if (!rawBmp.isRecycled()) {
                    rawBmp.recycle();
                }
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
                    String id = slot < posterImdbIds.size() ? posterImdbIds.get(slot) : (slot < BUNDLED_MOVIES.length ? BUNDLED_MOVIES[slot][1] : "tt15239678");
                    String title = slot < posterTitles.size() ? posterTitles.get(slot) : (slot < BUNDLED_MOVIES.length ? BUNDLED_MOVIES[slot][2] : "Film");
                    String pUrl = slot < posterUrls.size() ? posterUrls.get(slot) : "";
                    openMovieItem(title, id, pUrl);
                }
            });

            cardFrame.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        int totalCards = posterCardFrames.size();
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && slot == 0 && totalCards > 1) {
                            posterCardFrames.get(totalCards - 1).requestFocus();
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && slot == totalCards - 1 && totalCards > 1) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (dockRow != null && dockRow.getChildCount() > 0) {
                                dockRow.getChildAt(0).requestFocus();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (brandMedallion != null) {
                                brandMedallion.requestFocus();
                                return true;
                            }
                        }
                    }
                    return false;
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

    private static class VitrinCategory {
        final String name;
        final String catalogUrl;
        VitrinCategory(String name, String catalogUrl) {
            this.name = name;
            this.catalogUrl = catalogUrl;
        }
    }

    private static final VitrinCategory[] VITRIN_CATEGORIES = new VitrinCategory[] {
        new VitrinCategory("Günün Akıllı Sinema Seçkisi", "gemini_ai"),
        new VitrinCategory("Cannes & Venedik Altın Palmiye Başyapıtları", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Drama.json"),
        new VitrinCategory("Christopher Nolan & Denis Villeneuve Vizyonu", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Sci-Fi.json"),
        new VitrinCategory("Siberpunk & Retro-Fütüristik Distopyalar", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Fantasy.json"),
        new VitrinCategory("İskandinav & Fransız Suç Gerilimleri", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Crime.json"),
        new VitrinCategory("Kore Sineması: Zeka ve Gerilim Başyapıtları", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Thriller.json"),
        new VitrinCategory("Studio Ghibli & Büyülü Sinema Evrenleri", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Animation.json"),
        new VitrinCategory("90'lar Atmosferik Neo-Noir & Gizem", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Mystery.json"),
        new VitrinCategory("Kült Bilim Kurgu & Kozmik Yolculuklar", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json"),
        new VitrinCategory("Psikolojik Çıkmazlar ve Zihin Oyunları", "https://cinemeta-catalogs.strem.io/top/catalog/movie/imdbRating.json")
    };

    private static HttpURLConnection openWithRedirects(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setInstanceFollowRedirects(true);
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            if (newUrl != null && !newUrl.isEmpty()) {
                conn.disconnect();
                return openWithRedirects(newUrl);
            }
        }
        return conn;
    }

    private void fetchLiveTheatricalPosters() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = getSharedPreferences("vitrin_cache", MODE_PRIVATE);
                    int catIdx = prefs.getInt("active_category_idx", 0);
                    final VitrinCategory category = VITRIN_CATEGORIES[Math.abs(catIdx) % VITRIN_CATEGORIES.length];
                    prefs.edit().putInt("active_category_idx", catIdx + 1).apply();

                    if ("gemini_ai".equals(category.catalogUrl)) {
                        if (getGeminiApiKey() != null) {
                            fetchGeminiCuratedMovies(null);
                            return;
                        }
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (vitrinCategoryLabel != null) {
                                vitrinCategoryLabel.setText(category.name.toUpperCase(Locale.getDefault()));
                            }
                        }
                    });

                    fetchCategoryMovies(category);
                } catch (Throwable t) {
                    try {
                        fetchCategoryMovies(VITRIN_CATEGORIES[0]);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }).start();
    }

    private void fetchCategoryMovies(VitrinCategory category) {
        try {
            HttpURLConnection conn = openWithRedirects(category.catalogUrl);
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
                if (posterUrl.isEmpty() || imdbId.isEmpty()) continue;

                String fileKey = "cm_p_" + imdbId + ".jpg";
                File cacheFile = new File(getCacheDir(), fileKey);
                String currentTitle = (i < posterTitles.size()) ? posterTitles.get(i) : "";
                String savedImdb = prefs.getString("cm_imdb_" + i, "");
                boolean needsUpdate = !imdbId.equals(savedImdb) || !title.equals(currentTitle);

                if (!cacheFile.exists() || cacheFile.length() < 1024) {
                    try {
                        HttpURLConnection pConn = openWithRedirects(posterUrl);
                        if (pConn.getResponseCode() == 200) {
                            InputStream is = pConn.getInputStream();
                            FileOutputStream fos = new FileOutputStream(cacheFile);
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                            fos.close();
                            is.close();
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (cacheFile.exists() && cacheFile.length() > 1024) {
                    if (needsUpdate || (i < posterImageViews.size() && posterImageViews.get(i).getDrawable() == null)) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), opts);
                        int sample = 1;
                        if (opts.outWidth > 320 || opts.outHeight > 480) {
                            int halfW = opts.outWidth / 2;
                            int halfH = opts.outHeight / 2;
                            while ((halfW / sample) >= 320 && (halfH / sample) >= 480) {
                                sample *= 2;
                            }
                        }
                        opts.inSampleSize = Math.max(1, sample);
                        opts.inJustDecodeBounds = false;
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        Bitmap rawBmp = BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), opts);
                        if (rawBmp != null) {
                            final int distinctColor = YouTubeDisguiseHelper.sampleDistinctPosterColor(rawBmp, prevColor, i);
                            prevColor = distinctColor;
                            final Bitmap glassPoster = YouTubeDisguiseHelper.createLiquidGlassPosterBitmap(rawBmp);
                            if (!rawBmp.isRecycled()) {
                                rawBmp.recycle();
                            }
                            final Bitmap hoverBloom = YouTubeDisguiseHelper.createWideSoftHoverBloom(VITRIN_POSTER_W, VITRIN_POSTER_H, VITRIN_BLOOM_PAD, 26f, distinctColor);
                            final int idx = i;
                            final String finalPosterUrl = posterUrl;

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
                                    if (idx < posterImdbIds.size()) {
                                        posterImdbIds.set(idx, imdbId);
                                    }
                                    if (idx < posterTitles.size()) {
                                        posterTitles.set(idx, title);
                                    }
                                    if (idx < posterUrls.size()) {
                                        posterUrls.set(idx, finalPosterUrl);
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

    private void openMovieItem(String title, String imdbId, String posterUrl) {
        if (imdbId != null && !imdbId.isEmpty()) {
            try {
                Intent nuvio = new Intent();
                nuvio.setComponent(new ComponentName("com.nuvio.tv", "com.nuvio.tv.MainActivity"));
                nuvio.putExtra("launchMode", "stream");
                nuvio.putExtra("contentId", imdbId);
                nuvio.putExtra("contentType", "movie");
                nuvio.putExtra("videoId", imdbId);
                nuvio.putExtra("name", title != null ? title : "");
                if (posterUrl != null && !posterUrl.isEmpty()) {
                    nuvio.putExtra("poster", posterUrl);
                }
                nuvio.putExtra("playOnLoad", true);
                nuvio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
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

    private void openMovieItem(String title, String imdbId) {
        openMovieItem(title, imdbId, null);
    }

    private void openMovieInStremio(String imdbId) {
        openMovieItem("Film", imdbId);
    }

    private final Set<String> lastKnownPackages = new HashSet<>();

    private void loadAppsAndPopulate() {
        loadAppsAndPopulate(false);
    }

    private void loadAppsAndPopulate(boolean force) {
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

        Set<String> currentRawPackages = new HashSet<>();
        for (ResolveInfo ri : combined) {
            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                currentRawPackages.add(ri.activityInfo.packageName);
            }
        }

        // Upstream #666 Instant Return to Home:
        // Skip rebuild if package signatures haven't changed and dock is already populated!
        if (!force && !dockItems.isEmpty() && dockRow != null && dockRow.getChildCount() > 0 && currentRawPackages.equals(lastKnownPackages)) {
            return;
        }
        lastKnownPackages.clear();
        lastKnownPackages.addAll(currentRawPackages);

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

        // Filter out any uninstalled apps
        List<AppTileItem> validDiscovered = new ArrayList<>();
        for (AppTileItem itm : discovered) {
            try {
                if (pm.getLaunchIntentForPackage(itm.packageName) != null || pm.getLeanbackLaunchIntentForPackage(itm.packageName) != null) {
                    validDiscovered.add(itm);
                }
            } catch (Throwable ignored) {}
        }
        discovered.clear();
        discovered.addAll(validDiscovered);

        dockItems.clear();
        gridItems.clear();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedDockCsv = prefs.getString(KEY_DOCK_ORDER, "");
        List<String> dockOrderList;
        if (savedDockCsv != null && !savedDockCsv.isEmpty()) {
            dockOrderList = Arrays.asList(savedDockCsv.split(","));
        } else {
            // Default user order: Nuvio -> YouTube -> Web Video Caster -> TV+
            dockOrderList = Arrays.asList(
                "com.nuvio.tv",
                findInstalledTizenTubePackage(seenPackages),
                seenPackages.contains("com.instantbits.cast.receiver") ? "com.instantbits.cast.receiver" : "com.instantbits.cast.webvideo",
                "com.turkcell.ott"
            );
        }

        Set<String> dockSet = new HashSet<>();
        for (String dockPkg : dockOrderList) {
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
        StringBuilder sbGrid = new StringBuilder();
        for (int i = 0; i < gridItems.size(); i++) {
            if (i > 0) sbGrid.append(",");
            sbGrid.append(gridItems.get(i).packageName);
        }
        StringBuilder sbDock = new StringBuilder();
        for (int i = 0; i < dockItems.size(); i++) {
            if (i > 0) sbDock.append(",");
            sbDock.append(dockItems.get(i).packageName);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_APP_ORDER, sbGrid.toString())
                .putString(KEY_DOCK_ORDER, sbDock.toString())
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

        // Dedicated '+' Add App Card at dock end
        View addCard = createAddAppCard();
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(160, 214);
        addLp.setMargins(12, 6, 12, 6);
        dockRow.addView(addCard, addLp);

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

    private void focusGridChild(int flatIndex) {
        if (appsGridContainer == null) return;
        int columns = 4;
        int rowIndex = flatIndex / columns;
        int colIndex = flatIndex % columns;
        if (rowIndex < appsGridContainer.getChildCount()) {
            View row = appsGridContainer.getChildAt(rowIndex);
            if (row instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) row;
                if (colIndex < vg.getChildCount()) {
                    vg.getChildAt(colIndex).requestFocus();
                }
            }
        }
    }

    private View createLiquidGlassAppTile(final AppTileItem item, final boolean inDock, final int index) {
        final FrameLayout cardFrame = new FrameLayout(this);
        cardFrame.setFocusable(true);
        cardFrame.setClickable(true);
        cardFrame.setLongClickable(false);
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
                showAppLeanbackContextMenu(item, inDock, index);
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
                            tileKeyHandler.postDelayed(tileLongPressAction, 1200L);
                        }
                        if (longPressConsumed[0]) {
                            return true;
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
                } else if (!isMovingTile && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (inDock) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) {
                            int count = dockRow.getChildCount();
                            if (count > 1) {
                                dockRow.getChildAt(count - 1).requestFocus();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == dockRow.getChildCount() - 1) {
                            if (dockRow.getChildCount() > 1) {
                                dockRow.getChildAt(0).requestFocus();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                                posterCardFrames.get(0).requestFocus();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (appsGridContainer != null && appsGridContainer.getChildCount() > 0) {
                                focusGridChild(0);
                                return true;
                            }
                        }
                    } else {
                        // Grid item
                        int columns = 4;
                        int col = index % columns;
                        int total = gridItems.size();
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && col == 0) {
                            int endCol = Math.min(index + columns - 1, total - 1);
                            focusGridChild(endCol);
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && (col == columns - 1 || index == total - 1)) {
                            int startCol = index - col;
                            focusGridChild(startCol);
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && index < columns) {
                            int dockTarget = Math.min(col, dockRow.getChildCount() - 1);
                            if (dockTarget >= 0 && dockTarget < dockRow.getChildCount()) {
                                dockRow.getChildAt(dockTarget).requestFocus();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            int nextRowIndex = index + columns;
                            if (nextRowIndex >= total) {
                                // Loop vertically back to corresponding dock item
                                int dockTarget = Math.min(col, dockRow.getChildCount() - 1);
                                if (dockTarget >= 0 && dockTarget < dockRow.getChildCount()) {
                                    dockRow.getChildAt(dockTarget).requestFocus();
                                    return true;
                                }
                            }
                        }
                    }
                }
                return false;
            }
        });

        return cardFrame;
    }

    private void proactiveMemoryGovernor(String targetPackage) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                String[] candidatePkgs = new String[]{
                    "com.android.vending",
                    "com.google.android.gms",
                    "com.google.android.youtube.tv",
                    "com.instantbits.cast.receiver",
                    "com.cybercat.adbappcontrol.tv"
                };
                for (String pkg : candidatePkgs) {
                    if (!pkg.equals(targetPackage)) {
                        am.killBackgroundProcesses(pkg);
                    }
                }
            }
            System.gc();
        } catch (Throwable ignored) {
        }
    }

    private void launchAppItem(AppTileItem item) {
        if (item != null && item.packageName != null) {
            proactiveMemoryGovernor(item.packageName);
        }
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

    private void showAppLeanbackContextMenu(final AppTileItem item, final boolean inDock, final int index) {
        if (item == null || appActionPillBar == null) return;
        appActionPillBar.removeAllViews();
        appActionPillBar.setVisibility(View.VISIBLE);
        appActionPillBar.setAlpha(0f);
        appActionPillBar.animate().alpha(1f).setDuration(180L).start();

        TextView title = new TextView(this);
        title.setText(item.label + ": ");
        title.setTypeface(FontManager.getBold(this));
        title.setTextSize(14f);
        title.setTextColor(Color.parseColor("#38BDF8"));
        title.setPadding(10, 0, 14, 0);
        appActionPillBar.addView(title);

        View pillMove = createActionPill("↔️ Taşı", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAppActionPillBar();
                startTileMove(item, inDock, index);
            }
        });
        appActionPillBar.addView(pillMove);

        View pillHide = createActionPill("👁️ Gizle", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAppActionPillBar();
                hideAppFromHome(item.packageName, item.label);
            }
        });
        appActionPillBar.addView(pillHide);

        View pillUninstall = createActionPill("🗑️ Kaldır", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAppActionPillBar();
                uninstallPackage(item.packageName);
            }
        });
        appActionPillBar.addView(pillUninstall);

        View pillInfo = createActionPill("ℹ️ Bilgi", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAppActionPillBar();
                openAppInfo(item.packageName);
            }
        });
        appActionPillBar.addView(pillInfo);

        pillMove.requestFocus();
    }

    private void hideAppActionPillBar() {
        if (appActionPillBar == null || appActionPillBar.getVisibility() != View.VISIBLE) return;
        appActionPillBar.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        appActionPillBar.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private View createActionPill(String label, View.OnClickListener onClick) {
        final TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTypeface(FontManager.getMedium(this));
        btn.setTextSize(13f);
        btn.setTextColor(Color.WHITE);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setPadding(20, 8, 20, 8);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(18f);
        normalBg.setColor(Color.argb(140, 30, 41, 59));
        normalBg.setStroke(1, Color.argb(60, 255, 255, 255));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setShape(GradientDrawable.RECTANGLE);
        focusBg.setCornerRadius(18f);
        focusBg.setColor(Color.argb(230, 30, 41, 59));
        focusBg.setStroke(2, Color.parseColor("#38BDF8"));

        btn.setBackground(normalBg);

        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                btn.setBackground(hasFocus ? focusBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.08f : 1.0f).scaleY(hasFocus ? 1.08f : 1.0f).setDuration(140L).start();
            }
        });

        btn.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(6, 0, 6, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void openAppInfo(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private View createAddAppCard() {
        final FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(false);
        card.setClipToPadding(false);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(24f);
        normalBg.setColor(Color.argb(130, 15, 23, 42)); // Glass obsidian
        normalBg.setStroke(2, Color.argb(80, 255, 255, 255));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setShape(GradientDrawable.RECTANGLE);
        focusBg.setCornerRadius(24f);
        focusBg.setColor(Color.argb(190, 15, 23, 42));
        focusBg.setStroke(3, Color.parseColor("#38BDF8"));

        card.setBackground(normalBg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);

        TextView plusIcon = new TextView(this);
        plusIcon.setText("+");
        plusIcon.setTypeface(FontManager.getBlack(this));
        plusIcon.setTextSize(34f);
        plusIcon.setTextColor(Color.parseColor("#38BDF8"));
        plusIcon.setGravity(Gravity.CENTER);
        content.addView(plusIcon);

        TextView plusText = new TextView(this);
        plusText.setText("Ekle");
        plusText.setTypeface(FontManager.getBold(this));
        plusText.setTextSize(13f);
        plusText.setTextColor(Color.parseColor("#94A3B8"));
        plusText.setGravity(Gravity.CENTER);
        content.addView(plusText);

        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                card.setBackground(hasFocus ? focusBg : normalBg);
                v.animate()
                        .scaleX(hasFocus ? 1.085f : 1.0f)
                        .scaleY(hasFocus ? 1.085f : 1.0f)
                        .translationY(hasFocus ? -8f : 0f)
                        .setDuration(190L)
                        .start();
                plusText.setTextColor(hasFocus ? Color.WHITE : Color.parseColor("#94A3B8"));
                if (hasFocus && ambientBackdropView != null) {
                    ambientBackdropView.animateToTarget(v, Color.parseColor("#38BDF8"));
                }
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddAppToDockDialog();
            }
        });

        card.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            posterCardFrames.get(0).requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (appsGridContainer != null && appsGridContainer.getChildCount() > 0) {
                            focusGridChild(0);
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        return card;
    }

    private void showAddAppToDockDialog() {
        Set<String> dockPkgs = new HashSet<>();
        for (AppTileItem d : dockItems) dockPkgs.add(d.packageName);

        final List<AppTileItem> candidates = new ArrayList<>();
        for (AppTileItem g : gridItems) {
            if (!dockPkgs.contains(g.packageName)) {
                candidates.add(g);
            }
        }

        if (candidates.isEmpty()) {
            Toast.makeText(this, "Tüm uygulamalar zaten ana ekranda.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            names[i] = candidates.get(i).label;
        }

        new AlertDialog.Builder(this)
                .setTitle("Ana Ekrana Uygulama Ekle")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppTileItem picked = candidates.get(which);
                        gridItems.remove(picked);
                        dockItems.add(picked);
                        saveCurrentAppOrder();
                        renderDockAndGrid();
                        Toast.makeText(MainActivity.this, picked.label + " ana ekrana eklendi.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void showAllAppsDrawer() {
        if (allAppsOverlay == null) return;
        allAppsOverlay.removeAllViews();
        allAppsOverlay.setVisibility(View.VISIBLE);
        allAppsOverlay.setAlpha(0f);
        allAppsOverlay.animate().alpha(1f).setDuration(220L).start();

        final PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> lbApps = pm.queryIntentActivities(leanbackIntent, 0);

        java.util.Map<String, ResolveInfo> unique = new java.util.LinkedHashMap<>();
        if (apps != null) for (ResolveInfo r : apps) unique.put(r.activityInfo.packageName, r);
        if (lbApps != null) for (ResolveInfo r : lbApps) unique.put(r.activityInfo.packageName, r);

        List<ResolveInfo> sorted = new ArrayList<>(unique.values());
        Collections.sort(sorted, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo o1, ResolveInfo o2) {
                String l1 = o1.loadLabel(pm).toString();
                String l2 = o2.loadLabel(pm).toString();
                return l1.compareToIgnoreCase(l2);
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 36, 60, 36);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 20);

        TextView title = new TextView(this);
        title.setText("TÜM UYGULAMALAR (" + sorted.size() + ")");
        title.setTypeface(FontManager.getBlack(this));
        title.setTextSize(24f);
        title.setTextColor(Color.parseColor("#F8FAFC"));
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeHint = new TextView(this);
        closeHint.setText("[Geri]: Kapat");
        closeHint.setTypeface(FontManager.getMedium(this));
        closeHint.setTextSize(14f);
        closeHint.setTextColor(Color.parseColor("#64748B"));
        header.addView(closeHint);

        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        int perRow = 6;
        LinearLayout currentRow = null;
        View firstTile = null;

        for (int i = 0; i < sorted.size(); i++) {
            if (i % perRow == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setPadding(0, 8, 0, 8);
                grid.addView(currentRow);
            }

            final ResolveInfo info = sorted.get(i);
            final String pkg = info.activityInfo.packageName;
            final String label = info.loadLabel(pm).toString();
            Drawable icon = info.loadIcon(pm);

            View tile = createAppDrawerCard(icon, label, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideAllAppsDrawer();
                    try {
                        Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(launchIntent);
                        }
                    } catch (Throwable t) {
                        Toast.makeText(MainActivity.this, label + " başlatılamadı.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            if (firstTile == null) firstTile = tile;
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(160, 130);
            tlp.setMargins(8, 0, 8, 0);
            currentRow.addView(tile, tlp);
        }

        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        allAppsOverlay.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (firstTile != null) {
            firstTile.requestFocus();
        }
    }

    private void hideAllAppsDrawer() {
        if (allAppsOverlay == null || allAppsOverlay.getVisibility() != View.VISIBLE) return;
        allAppsOverlay.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        allAppsOverlay.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private View createAppDrawerCard(Drawable icon, String label, View.OnClickListener onClick) {
        final FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(22f);
        normalBg.setColor(Color.argb(135, 15, 23, 42)); // visionOS glass
        normalBg.setStroke(1, Color.argb(60, 255, 255, 255));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setShape(GradientDrawable.RECTANGLE);
        focusBg.setCornerRadius(22f);
        focusBg.setColor(Color.argb(220, 15, 23, 42));
        focusBg.setStroke(3, Color.parseColor("#38BDF8"));

        card.setBackground(normalBg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(12, 10, 12, 10);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (icon != null) iv.setImageDrawable(icon);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(52, 52);
        ivLp.setMargins(0, 0, 0, 6);
        content.addView(iv, ivLp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTypeface(FontManager.getMedium(this));
        tv.setTextSize(12f);
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        content.addView(tv);

        card.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                card.setBackground(hasFocus ? focusBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.10f : 1.0f).scaleY(hasFocus ? 1.10f : 1.0f).setDuration(160L).start();
            }
        });

        card.setOnClickListener(onClick);
        return card;
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
        loadAppsAndPopulate(true);
    }

    private final Runnable autoStopTileMoveRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMovingTile) {
                stopTileMove();
            }
        }
    };

    private void resetTileMoveTimeout() {
        handler.removeCallbacks(autoStopTileMoveRunnable);
        if (isMovingTile) {
            handler.postDelayed(autoStopTileMoveRunnable, 45000L); // 45 seconds of inactivity auto-commits
        }
    }

    private void startTileMove(AppTileItem item, boolean inDock, int index) {
        isMovingTile = true;
        movingTileItem = item;
        movingTileInDock = inDock;
        movingTileIndex = index;
        resetTileMoveTimeout();
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
        handler.removeCallbacks(autoStopTileMoveRunnable);
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

    private String getAiApiKey() {
        try {
            String key = Settings.Secure.getString(getContentResolver(), "tv_ai_api_key");
            if (key != null && !key.trim().isEmpty()) return key.trim();
        } catch (Throwable ignored) {
        }
        try {
            String key = Settings.Secure.getString(getContentResolver(), "tv_gemini_api_key");
            if (key != null && !key.trim().isEmpty()) return key.trim();
        } catch (Throwable ignored) {
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String key = prefs.getString("ai_api_key", null);
        if (key != null && !key.trim().isEmpty()) return key.trim();
        key = prefs.getString("gemini_api_key", null);
        if (key != null && !key.trim().isEmpty()) return key.trim();
        return null;
    }

    private String getGeminiApiKey() {
        return getAiApiKey();
    }

    private String getAiProvider(String apiKey) {
        if (apiKey != null && apiKey.startsWith("sk-ant-")) {
            return "claude";
        }
        if (apiKey != null && apiKey.startsWith("AIza")) {
            return "gemini";
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString("ai_provider", "gemini");
    }

    public String getTmdbApiKey() {
        try {
            String key = Settings.Secure.getString(getContentResolver(), "tv_tmdb_api_key");
            if (key != null && !key.trim().isEmpty()) return key.trim();
        } catch (Throwable ignored) {
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString("tmdb_api_key", null);
    }

    public static class MovieResolvedInfo {
        public String title;
        public String imdbId;
        public String posterUrl;
        public String backdropUrl;
        public double rating;
    }

    private MovieResolvedInfo resolveMovieViaTmdb(String title, int year) {
        if (title == null || title.trim().isEmpty()) return null;
        String cleanTitle = title.trim();
        String apiKey = getTmdbApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                String encoded = URLEncoder.encode(cleanTitle, "UTF-8");
                String urlStr = "https://api.themoviedb.org/3/search/movie?api_key=" + apiKey.trim() + "&query=" + encoded;
                if (year > 1900) urlStr += "&year=" + year;
                HttpURLConnection conn = openWithRedirects(urlStr);
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray results = root.optJSONArray("results");
                    if (results != null && results.length() > 0) {
                        JSONObject first = results.getJSONObject(0);
                        int tmdbId = first.optInt("id", 0);
                        String pPath = first.optString("poster_path", "");
                        String bPath = first.optString("backdrop_path", "");
                        String realTitle = first.optString("title", cleanTitle);

                        MovieResolvedInfo info = new MovieResolvedInfo();
                        info.title = realTitle;
                        info.rating = first.optDouble("vote_average", 0.0);
                        if (!pPath.isEmpty()) {
                            info.posterUrl = "https://image.tmdb.org/t/p/w500" + pPath;
                        }
                        if (!bPath.isEmpty()) {
                            info.backdropUrl = "https://image.tmdb.org/t/p/w1280" + bPath;
                        }
                        if (tmdbId > 0) {
                            try {
                                String extUrl = "https://api.themoviedb.org/3/movie/" + tmdbId + "/external_ids?api_key=" + apiKey.trim();
                                HttpURLConnection extConn = openWithRedirects(extUrl);
                                if (extConn.getResponseCode() == 200) {
                                    BufferedReader ebr = new BufferedReader(new InputStreamReader(extConn.getInputStream()));
                                    StringBuilder esb = new StringBuilder();
                                    while ((line = ebr.readLine()) != null) esb.append(line);
                                    ebr.close();
                                    JSONObject extObj = new JSONObject(esb.toString());
                                    info.imdbId = extObj.optString("imdb_id", "");
                                }
                            } catch (Throwable ignored) {}
                        }
                        return info;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Cinemeta search fallback
        try {
            String encoded = URLEncoder.encode(cleanTitle, "UTF-8");
            String cUrl = "https://v3-cinemeta.strem.io/catalog/movie/top/search=" + encoded + ".json";
            HttpURLConnection conn = openWithRedirects(cUrl);
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject root = new JSONObject(sb.toString());
                JSONArray metas = root.optJSONArray("metas");
                if (metas != null && metas.length() > 0) {
                    JSONObject m = metas.getJSONObject(0);
                    MovieResolvedInfo info = new MovieResolvedInfo();
                    info.title = m.optString("name", cleanTitle);
                    info.imdbId = m.optString("imdb_id", "");
                    info.posterUrl = m.optString("poster", "");
                    if (info.posterUrl.isEmpty() && !info.imdbId.isEmpty()) {
                        info.posterUrl = "https://images.metahub.space/poster/medium/" + info.imdbId + "/img";
                    }
                    return info;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void triggerAutoAiVitrinIfNeeded() {
        if (getAiApiKey() == null) return;
        SharedPreferences prefs = getSharedPreferences("vitrin_cache", MODE_PRIVATE);
        long lastTime = prefs.getLong("last_ai_auto_vitrin", 0L);
        long now = System.currentTimeMillis();
        // Auto-refresh creative selection every 3 hours or on initial boot
        if (now - lastTime > 3 * 60 * 60 * 1000L) {
            prefs.edit().putLong("last_ai_auto_vitrin", now).apply();
            fetchGeminiCuratedMovies(null);
        }
    }

    private void launchAppByPackage(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Throwable ignored) {
        }
    }

    private void openNuvioApp() {
        launchPackageWithFallback(new String[]{"com.nuvio.tv", "com.stremio.one"}, "Nuvio");
    }

    private void openYouTubeApp() {
        launchPackageWithFallback(new String[]{
                "io.gh.reisxd.tizentube.cobalt",
                "com.reisxd.tizentube",
                "com.teamsmart.videomanager.tv",
                "com.liskovsoft.smarttubetv.beta",
                "com.liskovsoft.smarttubetv"
        }, "YouTube");
    }

    private void openWebVideoCasterApp() {
        launchPackageWithFallback(new String[]{"com.instantbits.cast.receiver", "com.instantbits.cast.webvideo"}, "Web Video Caster");
    }

    private void openTvPlusApp() {
        launchPackageWithFallback(new String[]{"com.turkcell.ott", "com.turkcell.tvplus"}, "TV+");
    }

    private void launchPackageWithFallback(String[] packages, String name) {
        PackageManager pm = getPackageManager();
        for (String p : packages) {
            try {
                Intent launch = pm.getLeanbackLaunchIntentForPackage(p);
                if (launch == null) launch = pm.getLaunchIntentForPackage(p);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(this, name + " bulunamadı.", Toast.LENGTH_SHORT).show();
    }

    private void searchAndLaunchMovie(final String rawQuery) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String clean = rawQuery.replaceAll("(?i)(filmini|filmi|film|aç|oynat|başlat|izle)", "").trim();
                    if (clean.isEmpty()) clean = rawQuery.trim();

                    final MovieResolvedInfo resolved = resolveMovieViaTmdb(clean, 0);
                    if (resolved != null && resolved.imdbId != null && !resolved.imdbId.isEmpty()) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Nuvio ile başlatılıyor: " + resolved.title, Toast.LENGTH_LONG).show();
                                openMovieItem(resolved.title, resolved.imdbId, resolved.posterUrl);
                            }
                        });
                    } else if (getAiApiKey() != null) {
                        executeAiConversationPrompt(rawQuery);
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "'" + rawQuery + "' bulunamadı.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Throwable t) {
                    android.util.Log.e("MovieSearch", "Search error", t);
                }
            }
        }).start();
    }

    private void executeAiConversationPrompt(final String userQuery) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String provider = getAiProvider(getAiApiKey());
                    final String apiKey = getAiApiKey();
                    if (apiKey == null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Yapay zekâ anahtarı yapılandırılmamış.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    String sysPrompt = "Sen akıllı ve kültürlü bir Android TV yapay zekâ asistanısın. Kullanıcının sorusuna televizyon ekranında rahatça okunacak biçimde net, samimi, özlü ve Türkçe yanıt ver. Maksimum 3-4 cümle olsun.";
                    String fullPrompt = sysPrompt + "\n\nKullanıcı: " + userQuery;

                    HttpURLConnection conn;
                    if ("claude".equals(provider)) {
                        URL url = new URL("https://api.anthropic.com/v1/messages");
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("x-api-key", apiKey);
                        conn.setRequestProperty("anthropic-version", "2023-06-01");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(15000);
                        conn.setDoOutput(true);

                        JSONObject claudeReq = new JSONObject();
                        claudeReq.put("model", "claude-3-5-haiku-latest");
                        claudeReq.put("max_tokens", 512);
                        JSONArray messages = new JSONArray();
                        JSONObject userMsg = new JSONObject();
                        userMsg.put("role", "user");
                        userMsg.put("content", fullPrompt);
                        messages.put(userMsg);
                        claudeReq.put("messages", messages);

                        OutputStream os = conn.getOutputStream();
                        os.write(claudeReq.toString().getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.close();
                    } else {
                        JSONObject promptObj = new JSONObject();
                        JSONArray contents = new JSONArray();
                        JSONObject part = new JSONObject();
                        JSONArray parts = new JSONArray();
                        JSONObject textPart = new JSONObject();
                        textPart.put("text", fullPrompt);
                        parts.put(textPart);
                        part.put("parts", parts);
                        contents.put(part);
                        promptObj.put("contents", contents);

                        String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + apiKey;
                        URL url = new URL(urlStr);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(12000);
                        conn.setDoOutput(true);

                        OutputStream os = conn.getOutputStream();
                        os.write(promptObj.toString().getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.close();
                    }

                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();

                        String responseText = "";
                        if ("claude".equals(provider)) {
                            JSONObject resp = new JSONObject(sb.toString());
                            JSONArray contentArr = resp.optJSONArray("content");
                            if (contentArr != null && contentArr.length() > 0) {
                                responseText = contentArr.getJSONObject(0).optString("text", "").trim();
                            }
                        } else {
                            JSONObject resp = new JSONObject(sb.toString());
                            JSONArray candidates = resp.optJSONArray("candidates");
                            if (candidates != null && candidates.length() > 0) {
                                JSONObject cand = candidates.getJSONObject(0);
                                JSONObject content = cand.optJSONObject("content");
                                if (content != null) {
                                    JSONArray pArray = content.optJSONArray("parts");
                                    if (pArray != null && pArray.length() > 0) {
                                        responseText = pArray.getJSONObject(0).optString("text", "").trim();
                                    }
                                }
                            }
                        }

                        final String finalMsg = responseText;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showAiResponseFloatingCard(userQuery, finalMsg);
                            }
                        });
                    }
                } catch (Throwable t) {
                    android.util.Log.e("AiChat", "Error executing prompt", t);
                }
            }
        }).start();
    }



    private void fetchGeminiCuratedMovies(final String moodOrPrompt) {
        final String apiKey = getAiApiKey();
        if (apiKey == null) {
            Toast.makeText(this, "Yapay zekâ API anahtarı (Gemini veya Claude) tanımlanmamış.", Toast.LENGTH_LONG).show();
            return;
        }
        final String provider = getAiProvider(apiKey);
        final String providerName = "claude".equals(provider) ? "Claude" : "Gemini";

        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "🤖 " + providerName + " yapay zekâ sinema seçkisini hazırlıyor...", Toast.LENGTH_SHORT).show();
                if (vitrinCategoryLabel != null) {
                    vitrinCategoryLabel.setText("🤖 " + providerName.toUpperCase(Locale.getDefault()) + " AI • SİNEMA SEÇKİSİ HAZIRLANIYOR...");
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Calendar cal = Calendar.getInstance();
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    String dayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, new Locale("tr", "TR"));
                    String timeContext;
                    if (hour >= 5 && hour < 12) {
                        timeContext = "Sabah saatleri (" + dayName + "). Zihni açan, taze, ilham verici sinema başyapıtları.";
                    } else if (hour >= 12 && hour < 18) {
                        timeContext = "Öğleden sonra (" + dayName + "). Yüksek anlatı gücüne sahip prestijli festival filmleri veya görsel şölen sunan yönetmen seçkileri.";
                    } else if (hour >= 18 && hour < 23) {
                        timeContext = "Akşam saatleri (" + dayName + "). Sürükleyici, yüksek gerilimli, zihin açıcı modern kült başyapıtlar.";
                    } else {
                        timeContext = "Gece yarısı / loş ışık saatleri (" + dayName + "). Atmosferik neo-noir, siberpunk, kozmik yalnızlık ve derin psikolojik gerilimler.";
                    }

                    String promptText = (moodOrPrompt != null && !moodOrPrompt.trim().isEmpty())
                            ? moodOrPrompt.trim()
                            : "Şu anki atmosfer: " + timeContext + ". Sinema zevki yüksek izleyiciler için tek ve KESKİN bir tema etrafında 10 kült film öner. Dram ve komediyi asla birbiriyle karıştırma; seçki kesinlikle tek ve keskin bir sanatsal vizyona sahip olsun (Örn: belirli bir yönetmen retrospektifi, festival kazananları, siberpunk veya neo-noir). Başlık doğrudan konsepti anlatsın (Örn: '90'LAR NEO-NOIR & GİZEM' veya 'DENIS VILLENEUVE VİZYONU').";

                    String systemInstruction = "Sen dünyanın en iyi film eleştirmeni ve sinema küratörüsün. " + promptText + "\n"
                            + "Yanıtı YALNIZCA geçerli bir JSON olarak ver. Markdown (```json) veya hiçbir ek metin yazma. Format tam olarak şöyle olmalıdır:\n"
                            + "{\"category_title\": \"KESKİN BAŞLIK\", \"movies\": [{\"title\": \"Exact English or International Title\", \"year\": 2014, \"director\": \"...\"}]}";

                    HttpURLConnection conn;
                    if ("claude".equals(provider)) {
                        URL url = new URL("https://api.anthropic.com/v1/messages");
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("x-api-key", apiKey);
                        conn.setRequestProperty("anthropic-version", "2023-06-01");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(15000);
                        conn.setDoOutput(true);

                        JSONObject claudeReq = new JSONObject();
                        claudeReq.put("model", "claude-3-5-haiku-latest");
                        claudeReq.put("max_tokens", 1024);
                        JSONArray messages = new JSONArray();
                        JSONObject userMsg = new JSONObject();
                        userMsg.put("role", "user");
                        userMsg.put("content", systemInstruction);
                        messages.put(userMsg);
                        claudeReq.put("messages", messages);

                        OutputStream os = conn.getOutputStream();
                        os.write(claudeReq.toString().getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.close();
                    } else {
                        // Google Gemini
                        JSONObject promptObj = new JSONObject();
                        JSONArray contents = new JSONArray();
                        JSONObject part = new JSONObject();
                        JSONArray parts = new JSONArray();
                        JSONObject textPart = new JSONObject();
                        textPart.put("text", systemInstruction);
                        parts.put(textPart);
                        part.put("parts", parts);
                        contents.put(part);
                        promptObj.put("contents", contents);

                        String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + apiKey;
                        URL url = new URL(urlStr);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(12000);
                        conn.setDoOutput(true);

                        OutputStream os = conn.getOutputStream();
                        os.write(promptObj.toString().getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        os.close();
                    }

                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();

                        String rawText = "";
                        if ("claude".equals(provider)) {
                            JSONObject resp = new JSONObject(sb.toString());
                            JSONArray contentArr = resp.optJSONArray("content");
                            if (contentArr != null && contentArr.length() > 0) {
                                rawText = contentArr.getJSONObject(0).optString("text", "").trim();
                            }
                        } else {
                            JSONObject resp = new JSONObject(sb.toString());
                            JSONArray candidates = resp.optJSONArray("candidates");
                            if (candidates != null && candidates.length() > 0) {
                                JSONObject cand = candidates.getJSONObject(0);
                                JSONObject content = cand.optJSONObject("content");
                                if (content != null) {
                                    JSONArray pArray = content.optJSONArray("parts");
                                    if (pArray != null && pArray.length() > 0) {
                                        rawText = pArray.getJSONObject(0).optString("text", "").trim();
                                    }
                                }
                            }
                        }

                        if (!rawText.isEmpty()) {
                            if (rawText.startsWith("```json")) rawText = rawText.substring(7);
                            if (rawText.startsWith("```")) rawText = rawText.substring(3);
                            if (rawText.endsWith("```")) rawText = rawText.substring(0, rawText.length() - 3);
                            rawText = rawText.trim();

                            JSONObject aiData = new JSONObject(rawText);
                            final String catTitle = aiData.optString("category_title", providerName.toUpperCase(Locale.getDefault()) + " AI • SİZE ÖZEL SEÇKİ");
                            JSONArray movies = aiData.optJSONArray("movies");
                            if (movies != null && movies.length() > 0) {
                                applyGeminiMoviesToVitrin(catTitle, movies);
                            }
                        }
                    } else {
                        android.util.Log.e("AiCurator", "API error: " + conn.getResponseCode() + " " + conn.getResponseMessage());
                    }
                } catch (Throwable t) {
                    android.util.Log.e("AiCurator", "Error in AI curation", t);
                }
            }
        }).start();
    }

    private void showGeminiMoodDialog() {
        final String[] moods = new String[]{
            "🌌  4K HDR Görsel Şölen & Bilim-Kurgu",
            "🧠  Zihin Bükücü & Akıl Oyunları (Mind-Benders)",
            "🌙  Gece Yarısı Sakinliği & Derin Sinema",
            "⚡  Yüksek Adrenalin & Soluksuz Gerilim",
            "🎭  Nostaljik 90'lar & Kült Klasikler",
            "🍿  Kafa Yormayan Eğlenceli Akşam Filmi",
            "✍️  Özel İstek Yaz..."
        };

        new AlertDialog.Builder(this)
                .setTitle("🤖 Gemini AI Sinema Küratörü")
                .setItems(moods, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 6) {
                            promptCustomMood();
                        } else {
                            fetchGeminiCuratedMovies(moods[which]);
                        }
                    }
                })
                .show();
    }

    private void promptCustomMood() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Örn: David Fincher tarzı karanlık dedektif");
        input.setPadding(30, 20, 30, 20);

        new AlertDialog.Builder(this)
                .setTitle("Özel Film İsteği")
                .setMessage("Yapay zekâdan nasıl bir film önerisi istersiniz?")
                .setView(input)
                .setPositiveButton("Öner", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String text = input.getText().toString().trim();
                        if (!text.isEmpty()) {
                            fetchGeminiCuratedMovies(text);
                        }
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showSetGeminiKeyDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("AIzaSy... veya sk-ant-...");
        input.setPadding(30, 20, 30, 20);
        String current = getAiApiKey();
        if (current != null) input.setText(current);

        new AlertDialog.Builder(this)
                .setTitle("Yapay Zekâ Sinema Küratörü")
                .setMessage("Google Gemini (AIzaSy...) veya Anthropic Claude (sk-ant-...) API anahtarınızı girin:\n(Anahtar TV'nizin güvenli donanım deposunda saklanır)")
                .setView(input)
                .setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String val = input.getText().toString().trim();
                        if (val.isEmpty()) return;
                        String provider = getAiProvider(val);
                        String providerLabel = "claude".equals(provider) ? "Anthropic Claude" : "Google Gemini";

                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putString("ai_api_key", val)
                                .putString("gemini_api_key", val)
                                .putString("ai_provider", provider)
                                .apply();
                        Settings.Secure.putString(getContentResolver(), "tv_ai_api_key", val);
                        Settings.Secure.putString(getContentResolver(), "tv_gemini_api_key", val);
                        Settings.Secure.putString(getContentResolver(), "tv_ai_provider", provider);

                        Toast.makeText(MainActivity.this, "🤖 " + providerLabel + " API anahtarı başarıyla kaydedildi.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void handleSearchOrAiQuery(final String q) {
        if (q == null || q.trim().isEmpty()) return;
        final String query = q.trim();
        String lower = query.toLowerCase(Locale.getDefault());

        boolean isFilmographyOrCategory = lower.contains("filmleri") || lower.contains("filmografi")
                || lower.contains("filmler") || lower.contains("sineması") || lower.contains("yönettiği")
                || lower.contains("oynadığı") || lower.contains("seçkisi") || lower.contains("koleksiyonu")
                || lower.contains("kitano") || lower.contains("demirkubuz") || lower.contains("tarkovski")
                || lower.contains("tarkovsky") || lower.contains("kurosawa") || lower.contains("ceylan")
                || lower.contains("villeneuve") || lower.contains("nolan") || lower.contains("lynch")
                || lower.contains("kubrick") || lower.contains("fincher") || lower.contains("hitchcock")
                || lower.contains("scorsese") || lower.contains("coen") || lower.contains("gerilim")
                || lower.contains("korku") || lower.contains("bilimkurgu") || lower.contains("anime");

        if (isFilmographyOrCategory) {
            fetchFilmographyOrGenreVitrin(query);
            return;
        }

        boolean isAssistantPrompt = lower.contains("?")
                || lower.contains("nedir") || lower.contains("kimdir") || lower.contains("nasıl")
                || lower.contains("hava") || lower.contains("öner") || lower.contains("tavsiye")
                || lower.contains("gemini") || lower.contains("felsefe") || lower.contains("neden")
                || lower.contains("kim") || lower.contains("kaç") || lower.contains("anlat")
                || lower.contains("tarih") || lower.contains("saat");

        if (isAssistantPrompt && getAiApiKey() != null) {
            executeAiConversationPrompt(query);
        } else {
            searchAndLaunchMovie(query);
        }
    }

    private void fetchFilmographyOrGenreVitrin(final String query) {
        Toast.makeText(this, "🎬 " + query + " derleniyor...", Toast.LENGTH_SHORT).show();
        if (vitrinCategoryLabel != null) {
            vitrinCategoryLabel.setText("🎬 " + query.toUpperCase(Locale.getDefault()) + " DERLENİYOR...");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String cleanName = query.replaceAll("(?i)(filmleri|filmografisi|filmler|sineması|yönettiği|oynadığı|seçkisi|koleksiyonu)", "").trim();
                    if (cleanName.isEmpty()) cleanName = query;

                    JSONArray moviesArr = null;
                    String categoryTitle = cleanName.toUpperCase(Locale.getDefault()) + " FİLMOGRAFİSİ";

                    final String apiKey = getAiApiKey();
                    if (apiKey != null) {
                        try {
                            String prompt = "Kullanıcı Android TV sinema vitrini için şu sanatçı, yönetmen veya türü aradı: '" + query + "'.\n"
                                    + "Bu kişinin/türün en seçkin, en önemli ve kült 10 filminin tam adını ve yılını ver.\n"
                                    + "Sadece ve sadece aşağıdaki saf JSON formatında yanıt ver, markdown olmasın:\n"
                                    + "{\"category_title\": \"" + cleanName.toUpperCase(Locale.getDefault()) + " BAŞYAPITLARI\", \"movies\": [{\"title\": \"...\", \"year\": 2000}]}";

                            final String provider = getAiProvider(apiKey);
                            HttpURLConnection conn;
                            if ("claude".equals(provider)) {
                                URL url = new URL("https://api.anthropic.com/v1/messages");
                                conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("x-api-key", apiKey);
                                conn.setRequestProperty("anthropic-version", "2023-06-01");
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setConnectTimeout(7000);
                                conn.setReadTimeout(12000);
                                conn.setDoOutput(true);

                                JSONObject claudeReq = new JSONObject();
                                claudeReq.put("model", "claude-3-5-haiku-latest");
                                claudeReq.put("max_tokens", 700);
                                JSONArray msgs = new JSONArray();
                                JSONObject u = new JSONObject();
                                u.put("role", "user");
                                u.put("content", prompt);
                                msgs.put(u);
                                claudeReq.put("messages", msgs);

                                OutputStream os = conn.getOutputStream();
                                os.write(claudeReq.toString().getBytes(StandardCharsets.UTF_8));
                                os.flush();
                                os.close();
                            } else {
                                JSONObject promptObj = new JSONObject();
                                JSONArray contents = new JSONArray();
                                JSONObject part = new JSONObject();
                                JSONArray parts = new JSONArray();
                                JSONObject textPart = new JSONObject();
                                textPart.put("text", prompt);
                                parts.put(textPart);
                                part.put("parts", parts);
                                contents.put(part);
                                promptObj.put("contents", contents);

                                String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + apiKey;
                                URL url = new URL(urlStr);
                                conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setConnectTimeout(7000);
                                conn.setReadTimeout(12000);
                                conn.setDoOutput(true);

                                OutputStream os = conn.getOutputStream();
                                os.write(promptObj.toString().getBytes(StandardCharsets.UTF_8));
                                os.flush();
                                os.close();
                            }

                            if (conn.getResponseCode() == 200) {
                                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = br.readLine()) != null) sb.append(line);
                                br.close();

                                String rawText = "";
                                if ("claude".equals(provider)) {
                                    JSONObject resp = new JSONObject(sb.toString());
                                    JSONArray contentArr = resp.optJSONArray("content");
                                    if (contentArr != null && contentArr.length() > 0) {
                                        rawText = contentArr.getJSONObject(0).optString("text", "").trim();
                                    }
                                } else {
                                    JSONObject resp = new JSONObject(sb.toString());
                                    JSONArray candidates = resp.optJSONArray("candidates");
                                    if (candidates != null && candidates.length() > 0) {
                                        JSONObject cand = candidates.getJSONObject(0);
                                        JSONObject contentObj = cand.optJSONObject("content");
                                        if (contentObj != null) {
                                            JSONArray pArray = contentObj.optJSONArray("parts");
                                            if (pArray != null && pArray.length() > 0) {
                                                rawText = pArray.getJSONObject(0).optString("text", "").trim();
                                            }
                                        }
                                    }
                                }

                                int start = rawText.indexOf("{");
                                int end = rawText.lastIndexOf("}");
                                if (start >= 0 && end > start) {
                                    JSONObject parsed = new JSONObject(rawText.substring(start, end + 1));
                                    categoryTitle = parsed.optString("category_title", categoryTitle);
                                    moviesArr = parsed.optJSONArray("movies");
                                }
                            }
                        } catch (Throwable t) {
                            android.util.Log.e("Filmography", "AI call error", t);
                        }
                    }

                    if (moviesArr == null || moviesArr.length() == 0) {
                        String tmdbKey = getTmdbApiKey();
                        if (tmdbKey != null && !tmdbKey.isEmpty()) {
                            try {
                                String searchUrl = "https://api.themoviedb.org/3/search/person?api_key=" + tmdbKey
                                        + "&query=" + URLEncoder.encode(cleanName, "UTF-8");
                                HttpURLConnection sc = (HttpURLConnection) new URL(searchUrl).openConnection();
                                if (sc.getResponseCode() == 200) {
                                    BufferedReader br = new BufferedReader(new InputStreamReader(sc.getInputStream()));
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while ((line = br.readLine()) != null) sb.append(line);
                                    br.close();
                                    JSONObject sRoot = new JSONObject(sb.toString());
                                    JSONArray results = sRoot.optJSONArray("results");
                                    if (results != null && results.length() > 0) {
                                        JSONObject person = results.getJSONObject(0);
                                        int personId = person.getInt("id");
                                        String personName = person.optString("name", cleanName);
                                        categoryTitle = personName.toUpperCase(Locale.getDefault()) + " FİLMOGRAFİSİ";

                                        String creditsUrl = "https://api.themoviedb.org/3/person/" + personId
                                                + "/movie_credits?api_key=" + tmdbKey;
                                        HttpURLConnection cc = (HttpURLConnection) new URL(creditsUrl).openConnection();
                                        if (cc.getResponseCode() == 200) {
                                            BufferedReader cbr = new BufferedReader(new InputStreamReader(cc.getInputStream()));
                                            StringBuilder csb = new StringBuilder();
                                            while ((line = cbr.readLine()) != null) csb.append(line);
                                            cbr.close();
                                            JSONObject cRoot = new JSONObject(csb.toString());
                                            JSONArray cast = cRoot.optJSONArray("crew");
                                            if (cast == null || cast.length() == 0) cast = cRoot.optJSONArray("cast");
                                            if (cast != null) {
                                                moviesArr = new JSONArray();
                                                for (int m = 0; m < Math.min(10, cast.length()); m++) {
                                                    JSONObject mo = cast.getJSONObject(m);
                                                    JSONObject item = new JSONObject();
                                                    item.put("title", mo.optString("title", ""));
                                                    String rel = mo.optString("release_date", "2000");
                                                    int yr = 2000;
                                                    if (rel.length() >= 4) {
                                                        try { yr = Integer.parseInt(rel.substring(0, 4)); } catch (Throwable ignored) {}
                                                    }
                                                    item.put("year", yr);
                                                    moviesArr.put(item);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                android.util.Log.e("Filmography", "TMDB error", t);
                            }
                        }
                    }

                    if (moviesArr != null && moviesArr.length() > 0) {
                        final String finalCatTitle = categoryTitle;
                        final JSONArray finalMovies = moviesArr;
                        applyGeminiMoviesToVitrin(finalCatTitle, finalMovies);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "🎬 " + finalCatTitle + " vitrine yüklendi!", Toast.LENGTH_LONG).show();
                                if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                                    posterCardFrames.get(0).requestFocus();
                                }
                            }
                        });
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "'" + query + "' için filmografi bulunamadı.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Throwable t) {
                    android.util.Log.e("Filmography", "Overall error", t);
                }
            }
        }).start();
    }

    private void showAiResponseFloatingCard(final String query, final String answer) {
        if (aiResponseFloatingCard == null) return;
        aiResponseFloatingCard.removeAllViews();
        aiResponseFloatingCard.setVisibility(View.VISIBLE);
        aiResponseFloatingCard.setAlpha(0f);
        aiResponseFloatingCard.setTranslationY(-60f);
        aiResponseFloatingCard.animate().alpha(1f).translationY(0f).setDuration(260L).start();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 26, 36, 26);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(24f);
        bg.setColor(Color.argb(240, 15, 23, 42)); // Deep glass
        bg.setStroke(2, Color.parseColor("#38BDF8"));
        card.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("🤖 Gemini Asistanı");
        title.setTypeface(FontManager.getBold(this));
        title.setTextSize(17f);
        title.setTextColor(Color.parseColor("#38BDF8"));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hint = new TextView(this);
        hint.setText("[Geri]: Kapat");
        hint.setTypeface(FontManager.getMedium(this));
        hint.setTextSize(12f);
        hint.setTextColor(Color.parseColor("#64748B"));
        top.addView(hint);

        card.addView(top);

        TextView qView = new TextView(this);
        qView.setText("\"" + query + "\"");
        qView.setTypeface(FontManager.getMedium(this));
        qView.setTextSize(13f);
        qView.setTextColor(Color.parseColor("#94A3B8"));
        qView.setPadding(0, 8, 0, 12);
        card.addView(qView);

        TextView ansView = new TextView(this);
        ansView.setText(answer);
        ansView.setTypeface(FontManager.getMedium(this));
        ansView.setTextSize(15f);
        ansView.setTextColor(Color.parseColor("#F8FAFC"));
        ansView.setLineSpacing(6f, 1.15f);
        card.addView(ansView);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(760, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        flp.setMargins(0, 90, 0, 0);
        aiResponseFloatingCard.addView(card, flp);

        handler.removeCallbacks(autoDismissAiCardRunnable);
        handler.postDelayed(autoDismissAiCardRunnable, 12000L);
    }

    private final Runnable autoDismissAiCardRunnable = new Runnable() {
        @Override
        public void run() {
            hideAiResponseFloatingCard();
        }
    };

    private void hideAiResponseFloatingCard() {
        if (aiResponseFloatingCard == null || aiResponseFloatingCard.getVisibility() != View.VISIBLE) return;
        handler.removeCallbacks(autoDismissAiCardRunnable);
        aiResponseFloatingCard.animate()
                .alpha(0f)
                .translationY(-40f)
                .setDuration(220L)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        aiResponseFloatingCard.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private void applyGeminiMoviesToVitrin(final String categoryTitle, final JSONArray movies) {
        if (heroShelfRow != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    heroShelfRow.animate().alpha(0.20f).setDuration(220L).start();
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = Math.min(BUNDLED_MOVIES.length, movies.length());
                final List<Bitmap> readyPosters = new ArrayList<>();
                final List<Bitmap> readyBlooms = new ArrayList<>();
                final List<Integer> readyColors = new ArrayList<>();
                final List<String> readyImdbs = new ArrayList<>();
                final List<String> readyTitles = new ArrayList<>();
                final List<String> readyUrls = new ArrayList<>();

                int prevColor = 0;
                for (int i = 0; i < count; i++) {
                    JSONObject m = movies.optJSONObject(i);
                    if (m == null) continue;
                    String mTitle = m.optString("title", "");
                    int mYear = m.optInt("year", 0);
                    if (mTitle.isEmpty()) continue;

                    MovieResolvedInfo resolved = resolveMovieViaTmdb(mTitle, mYear);
                    if (resolved == null) {
                        resolved = new MovieResolvedInfo();
                        resolved.title = mTitle;
                        resolved.imdbId = "tt" + Math.abs(mTitle.hashCode());
                        resolved.posterUrl = "";
                        resolved.backdropUrl = "";
                    }

                    String imdbId = (resolved.imdbId != null && !resolved.imdbId.isEmpty()) ? resolved.imdbId : ("tt_res_" + Math.abs(mTitle.hashCode()));
                    String title = (resolved.title != null && !resolved.title.isEmpty()) ? resolved.title : mTitle;
                    String posterUrl = resolved.posterUrl;
                    if (posterUrl == null || posterUrl.isEmpty()) {
                        posterUrl = "https://images.metahub.space/poster/medium/" + imdbId + "/img";
                    }

                    String fileKey = "cm_p_" + Math.abs(mTitle.hashCode()) + ".jpg";
                    File cacheFile = new File(getCacheDir(), fileKey);

                    if (!cacheFile.exists() || cacheFile.length() < 1024) {
                        try {
                            HttpURLConnection pConn = openWithRedirects(posterUrl);
                            if (pConn.getResponseCode() == 200) {
                                InputStream is = pConn.getInputStream();
                                FileOutputStream fos = new FileOutputStream(cacheFile);
                                byte[] buf = new byte[8192];
                                int r;
                                while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                                fos.close();
                                is.close();
                            }
                        } catch (Throwable ignored) {}
                    }

                    Bitmap rawBmp = null;
                    if (cacheFile.exists() && cacheFile.length() > 1024) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        rawBmp = BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), opts);
                    }

                    int distinctColor = YouTubeDisguiseHelper.sampleDistinctPosterColor(rawBmp, prevColor, i);
                    prevColor = distinctColor;
                    Bitmap glassPoster = YouTubeDisguiseHelper.createLiquidGlassPosterBitmap(rawBmp);
                    if (rawBmp != null && !rawBmp.isRecycled()) {
                        rawBmp.recycle();
                    }
                    Bitmap hoverBloom = YouTubeDisguiseHelper.createWideSoftHoverBloom(VITRIN_POSTER_W, VITRIN_POSTER_H, VITRIN_BLOOM_PAD, 26f, distinctColor);

                    readyPosters.add(glassPoster);
                    readyBlooms.add(hoverBloom);
                    readyColors.add(distinctColor);
                    readyImdbs.add(imdbId);
                    readyTitles.add(title);
                    readyUrls.add(posterUrl);
                }

                // ATOMIC UI UPDATE
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (vitrinCategoryLabel != null) {
                            vitrinCategoryLabel.setText(categoryTitle.toUpperCase(Locale.getDefault()));
                        }
                        for (int k = 0; k < readyPosters.size() && k < posterImageViews.size(); k++) {
                            posterImageViews.get(k).setImageBitmap(readyPosters.get(k));
                            if (k < posterBloomViews.size()) posterBloomViews.get(k).setImageBitmap(readyBlooms.get(k));
                            if (k < posterNeonColors.size()) posterNeonColors.set(k, readyColors.get(k));
                            if (k < posterImdbIds.size()) posterImdbIds.set(k, readyImdbs.get(k));
                            if (k < posterTitles.size()) posterTitles.set(k, readyTitles.get(k));
                            if (k < posterUrls.size()) posterUrls.set(k, readyUrls.get(k));
                        }
                        if (heroShelfRow != null) {
                            heroShelfRow.animate().alpha(1.0f).setDuration(350L).start();
                        }
                        if (posterCardFrames != null && !posterCardFrames.isEmpty()) {
                            smoothScrollToVitrinCard(posterCardFrames.get(0));
                        }
                    }
                });
            }
        }).start();
    }

    private void showLauncherSettingsModal() {
        if (settingsModalOverlay == null) return;
        settingsModalOverlay.removeAllViews();
        settingsModalOverlay.setVisibility(View.VISIBLE);
        settingsModalOverlay.setAlpha(0f);
        settingsModalOverlay.animate().alpha(1f).setDuration(220L).start();

        final String currentCity = Settings.Global.getString(getContentResolver(), "tvhome_city");
        final String apiKey = getAiApiKey();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(44, 30, 44, 34);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(26f);
        bg.setColor(Color.argb(240, 11, 15, 25));
        bg.setStroke(2, Color.parseColor("#38BDF8"));
        card.setBackground(bg);

        ImageView logoIcon = new ImageView(this);
        logoIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            InputStream is = getAssets().open("signature_logo.png");
            Bitmap bm = BitmapFactory.decodeStream(is);
            is.close();
            if (bm != null) logoIcon.setImageBitmap(bm);
        } catch (Throwable ignored) {}
        LinearLayout.LayoutParams lilp = new LinearLayout.LayoutParams(52, 52);
        lilp.setMargins(0, 0, 0, 8);
        card.addView(logoIcon, lilp);

        TextView title = new TextView(this);
        title.setText("CİNETV SİSTEM AYARLARI");
        title.setTypeface(FontManager.getBold(this));
        title.setTextSize(18f);
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setLetterSpacing(0.06f);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Kişiselleştirme & Sistem Yönetimi • [Geri]: Kapat");
        sub.setTypeface(FontManager.getMedium(this));
        sub.setTextSize(12f);
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setPadding(0, 4, 0, 20);
        card.addView(sub);

        // 1) Tema Ayarlama
        View btnTheme = createModalMenuButton("🌓  Tema Modu", "Saf OLED, Otomatik Gece/Gündüz veya Aydınlık Cam", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleThemeMode();
            }
        });
        card.addView(btnTheme);

        // 2) Şehir Ayarlama
        String cityLabel = (currentCity != null && !currentCity.trim().isEmpty()) ? currentCity.trim() : "Ayarlanmadı";
        View btnCity = createModalMenuButton("🌤️  Hava Durumu Şehri", "Mevcut: " + cityLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInlineCityInputModal();
            }
        });
        card.addView(btnCity);

        // 3) Televizyonu Yeniden Başlatma
        View btnReboot = createModalMenuButton("🔄  Televizyonu Yeniden Başlat", "Cihazı güvenle kapatıp yeniden başlatır", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRebootTv();
            }
        });
        card.addView(btnReboot);

        // 4) Yapay Zekâ & TMDB API Anahtarı
        String keyLabel = (apiKey != null) ? "Aktif (" + getAiProvider(apiKey).toUpperCase() + ")" : "Tanımlanmadı";
        View btnAiKey = createModalMenuButton("🔑  Yapay Zekâ & TMDB API Anahtarı", "Gemini / Claude / TMDB • Durum: " + keyLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInlineApiKeyModal();
            }
        });
        card.addView(btnAiKey);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(600, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        settingsModalOverlay.addView(card, clp);
        btnTheme.requestFocus();
    }

    private void hideSettingsModal() {
        if (settingsModalOverlay == null || settingsModalOverlay.getVisibility() != View.VISIBLE) return;
        settingsModalOverlay.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        settingsModalOverlay.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    private View createModalMenuButton(String title, String desc, View.OnClickListener onClick) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(24, 14, 24, 14);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(16f);
        normalBg.setColor(Color.argb(120, 30, 41, 59));
        normalBg.setStroke(1, Color.argb(60, 255, 255, 255));

        final GradientDrawable focusBg = new GradientDrawable();
        focusBg.setShape(GradientDrawable.RECTANGLE);
        focusBg.setCornerRadius(16f);
        focusBg.setColor(Color.argb(220, 30, 41, 59));
        focusBg.setStroke(2, Color.parseColor("#38BDF8"));

        row.setBackground(normalBg);

        TextView tView = new TextView(this);
        tView.setText(title);
        tView.setTypeface(FontManager.getBold(this));
        tView.setTextSize(15f);
        tView.setTextColor(Color.parseColor("#F8FAFC"));
        row.addView(tView);

        TextView dView = new TextView(this);
        dView.setText(desc);
        dView.setTypeface(FontManager.getMedium(this));
        dView.setTextSize(12f);
        dView.setTextColor(Color.parseColor("#94A3B8"));
        dView.setPadding(0, 3, 0, 0);
        row.addView(dView);

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                row.setBackground(hasFocus ? focusBg : normalBg);
                v.animate().scaleX(hasFocus ? 1.04f : 1.0f).scaleY(hasFocus ? 1.04f : 1.0f).setDuration(160L).start();
            }
        });

        row.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 10);
        row.setLayoutParams(lp);
        return row;
    }

    private void cycleThemeMode() {
        String current = Settings.Global.getString(getContentResolver(), "tvhome_theme_mode");
        if (current == null) current = "dark";
        String nextMode;
        String nextLabel;
        if ("dark".equalsIgnoreCase(current)) {
            nextMode = "auto";
            nextLabel = "Otomatik Gece / Gündüz";
        } else if ("auto".equalsIgnoreCase(current)) {
            nextMode = "light";
            nextLabel = "Aydınlık Buzlu Cam";
        } else {
            nextMode = "dark";
            nextLabel = "Saf OLED Siyah";
        }
        Settings.Global.putString(getContentResolver(), "tvhome_theme_mode", nextMode);
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("theme_mode", nextMode).apply();
        applyThemeMode();
        Toast.makeText(this, "Tema Değiştirildi: " + nextLabel, Toast.LENGTH_SHORT).show();
        showLauncherSettingsModal();
    }

    private void confirmRebootTv() {
        hideSettingsModal();
        Toast.makeText(this, "Televizyon yeniden başlatılıyor...", Toast.LENGTH_LONG).show();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(new String[]{"reboot"});
                } catch (Throwable t) {
                    try {
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        if (pm != null) pm.reboot(null);
                    } catch (Throwable ignored) {}
                }
            }
        }, 800L);
    }

    private void showInlineCityInputModal() {
        hideSettingsModal();
        SharedPreferences p = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        String current = Settings.Global.getString(getContentResolver(), "tvhome_city");
        if (current == null) current = p.getString("city", "Istanbul");

        if (settingsModalOverlay == null) return;
        settingsModalOverlay.removeAllViews();
        settingsModalOverlay.setVisibility(View.VISIBLE);
        settingsModalOverlay.setAlpha(0f);
        settingsModalOverlay.animate().alpha(1f).setDuration(200L).start();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 28, 36, 28);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(24f);
        bg.setColor(Color.argb(240, 11, 15, 25));
        bg.setStroke(2, Color.parseColor("#38BDF8"));
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("🌤️ HAVA DURUMU ŞEHRİ");
        title.setTypeface(FontManager.getBold(this));
        title.setTextSize(18f);
        title.setTextColor(Color.WHITE);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Ana ekranda sıcaklık ve hava durumunu görmek istediğiniz şehri girin:");
        desc.setTextSize(13f);
        desc.setTextColor(Color.parseColor("#94A3B8"));
        desc.setPadding(0, 6, 0, 16);
        card.addView(desc);

        final EditText cityInput = new EditText(this);
        cityInput.setText(current);
        cityInput.setTextColor(Color.WHITE);
        cityInput.setTextSize(15f);
        cityInput.setSingleLine(true);
        cityInput.setPadding(20, 14, 20, 14);
        GradientDrawable inBg = new GradientDrawable();
        inBg.setCornerRadius(14f);
        inBg.setColor(Color.argb(160, 30, 41, 59));
        inBg.setStroke(2, Color.parseColor("#38BDF8"));
        cityInput.setBackground(inBg);
        card.addView(cityInput);

        View btnSave = createModalMenuButton("💾  Kaydet ve Güncelle", "Hava durumunu anında yeni şehre göre çeker", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String val = cityInput.getText().toString().trim();
                if (!val.isEmpty()) {
                    Settings.Global.putString(getContentResolver(), "tvhome_city", val);
                    getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("city", val).apply();
                    fetchLiveWeather();
                    Toast.makeText(MainActivity.this, "Şehir kaydedildi: " + val, Toast.LENGTH_SHORT).show();
                }
                hideSettingsModal();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, 16, 0, 0);
        card.addView(btnSave, blp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(540, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        settingsModalOverlay.addView(card, clp);
        cityInput.requestFocus();
    }

    private void showInlineApiKeyModal() {
        hideSettingsModal();
        final String currentKey = getAiApiKey();

        if (settingsModalOverlay == null) return;
        settingsModalOverlay.removeAllViews();
        settingsModalOverlay.setVisibility(View.VISIBLE);
        settingsModalOverlay.setAlpha(0f);
        settingsModalOverlay.animate().alpha(1f).setDuration(200L).start();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 28, 36, 28);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(24f);
        bg.setColor(Color.argb(240, 11, 15, 25));
        bg.setStroke(2, Color.parseColor("#38BDF8"));
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("🔑 YAPAY ZEKÂ & TMDB API ANAHTARI");
        title.setTypeface(FontManager.getBold(this));
        title.setTextSize(18f);
        title.setTextColor(Color.WHITE);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Google Gemini (AIzaSy...) veya Claude (sk-ant-...) anahtarınızı girin:\n(Anahtar TV'nin güvenli donanım deposunda saklanır)");
        desc.setTextSize(12f);
        desc.setTextColor(Color.parseColor("#94A3B8"));
        desc.setPadding(0, 6, 0, 16);
        card.addView(desc);

        final EditText keyInput = new EditText(this);
        if (currentKey != null) keyInput.setText(currentKey);
        keyInput.setHint("AIzaSy... veya sk-ant-...");
        keyInput.setHintTextColor(Color.parseColor("#64748B"));
        keyInput.setTextColor(Color.WHITE);
        keyInput.setTextSize(14f);
        keyInput.setSingleLine(true);
        keyInput.setPadding(20, 14, 20, 14);
        GradientDrawable inBg = new GradientDrawable();
        inBg.setCornerRadius(14f);
        inBg.setColor(Color.argb(160, 30, 41, 59));
        inBg.setStroke(2, Color.parseColor("#38BDF8"));
        keyInput.setBackground(inBg);
        card.addView(keyInput);

        View btnSave = createModalMenuButton("💾  Anahtarı Güvenle Kaydet", "Vitrin küratörünü ve arama asistanını etkinleştirir", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String val = keyInput.getText().toString().trim();
                if (!val.isEmpty()) {
                    Settings.Secure.putString(getContentResolver(), "tv_ai_curator_api_key", val);
                    Toast.makeText(MainActivity.this, "Yapay zekâ anahtarı kaydedildi (" + getAiProvider(val).toUpperCase() + ")", Toast.LENGTH_SHORT).show();
                }
                hideSettingsModal();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, 16, 0, 0);
        card.addView(btnSave, blp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(580, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        settingsModalOverlay.addView(card, clp);
        keyInput.requestFocus();
    }

    private void updateClock() {
        Date now = new Date();
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", Locale.getDefault());
        String t = timeFmt.format(now);
        String d = dateFmt.format(now);
        if (clockText != null) clockText.setText(t);
        if (dateText != null) dateText.setText(d);
    }

    private class VibrantScreensaverView extends View {
        private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint quotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint starfieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint scenePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", new Locale("tr", "TR"));

        private final Bitmap signatureBitmap;
        private final Drawable fallbackLogo;
        private boolean isRunning = false;

        private final List<ClockDream.CinemaMoment> momentsPool;
        private final Random random = new Random();
        private int currentMomentIndex = 0;
        private long lastQuoteSwitchTime = 0;
        private static final long QUOTE_DURATION_MS = 14000L;

        private Bitmap activeSceneBackdrop = null;
        private boolean isFetchingScene = false;

        private static final int STAR_COUNT = 55;
        private final float[] starX = new float[STAR_COUNT];
        private final float[] starY = new float[STAR_COUNT];
        private final float[] starSpeed = new float[STAR_COUNT];
        private final float[] starRadius = new float[STAR_COUNT];
        private final float[] starAlpha = new float[STAR_COUNT];

        VibrantScreensaverView(Context ctx) {
            super(ctx);
            momentsPool = ClockDream.createExtensiveMomentsPool();
            Collections.shuffle(momentsPool, random);
            currentMomentIndex = random.nextInt(momentsPool.size());
            lastQuoteSwitchTime = SystemClock.uptimeMillis();

            clockPaint.setTypeface(FontManager.getBlack(ctx));
            clockPaint.setTextSize(64f);
            clockPaint.setColor(Color.WHITE);
            clockPaint.setLetterSpacing(0.04f);

            datePaint.setTypeface(FontManager.getMedium(ctx));
            datePaint.setTextSize(16f);
            datePaint.setColor(Color.parseColor("#94A3B8"));
            datePaint.setLetterSpacing(0.12f);

            quotePaint.setTypeface(FontManager.getBold(ctx));
            quotePaint.setTextSize(34f);
            quotePaint.setColor(Color.parseColor("#F8FAFC"));
            quotePaint.setLetterSpacing(0.03f);

            titlePaint.setTypeface(FontManager.getMedium(ctx));
            titlePaint.setTextSize(18f);
            titlePaint.setColor(Color.parseColor("#38BDF8"));
            titlePaint.setLetterSpacing(0.14f);

            logoPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

            Bitmap sig = null;
            try {
                InputStream is = ctx.getAssets().open("signature_logo.png");
                sig = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Throwable ignored) {
            }
            signatureBitmap = sig;

            Drawable fallback = null;
            if (sig == null) {
                try {
                    fallback = ctx.getDrawable(R.drawable.ic_user_signature);
                    if (fallback == null) fallback = ctx.getDrawable(R.drawable.ic_tv_clean);
                } catch (Throwable ignored) {
                }
            }
            fallbackLogo = fallback;

            for (int i = 0; i < STAR_COUNT; i++) {
                starX[i] = (float) (Math.random() * 1920);
                starY[i] = (float) (Math.random() * 1080);
                starSpeed[i] = (float) (Math.random() * 0.45 + 0.15);
                starRadius[i] = (float) (Math.random() * 2.2 + 0.8);
                starAlpha[i] = (float) (Math.random() * 0.60 + 0.25);
            }

            fetchTmdbBackdropForMoment(momentsPool.get(currentMomentIndex));
        }

        private void fetchTmdbBackdropForMoment(final ClockDream.CinemaMoment moment) {
            if (isFetchingScene || moment == null) return;
            isFetchingScene = true;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String tmdbKey = Settings.Secure.getString(getContext().getContentResolver(), "tv_tmdb_api_key");
                        String backdropUrl = null;

                        if (tmdbKey != null && !tmdbKey.trim().isEmpty()) {
                            try {
                                String apiUrl = "https://api.themoviedb.org/3/movie/" + (moment.tmdbId > 0 ? moment.tmdbId : moment.imdbId)
                                        + "/images?api_key=" + tmdbKey.trim();
                                HttpURLConnection c = (HttpURLConnection) new URL(apiUrl).openConnection();
                                c.setConnectTimeout(4000);
                                c.setReadTimeout(5000);
                                if (c.getResponseCode() == 200) {
                                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while ((line = br.readLine()) != null) sb.append(line);
                                    br.close();
                                    JSONObject root = new JSONObject(sb.toString());
                                    JSONArray backdrops = root.optJSONArray("backdrops");
                                    if (backdrops != null && backdrops.length() > 0) {
                                        int bIdx = random.nextInt(Math.min(5, backdrops.length()));
                                        String filePath = backdrops.getJSONObject(bIdx).optString("file_path");
                                        if (filePath != null && !filePath.isEmpty()) {
                                            backdropUrl = "https://image.tmdb.org/t/p/w1280" + filePath;
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }

                        if (backdropUrl == null) {
                            backdropUrl = "https://images.metahub.space/background/medium/" + moment.imdbId + "/img";
                        }

                        HttpURLConnection imgConn = (HttpURLConnection) new URL(backdropUrl).openConnection();
                        imgConn.setConnectTimeout(6000);
                        imgConn.setReadTimeout(9000);
                        if (imgConn.getResponseCode() == 200) {
                            InputStream is = imgConn.getInputStream();
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                            final Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                            is.close();
                            if (bmp != null) {
                                post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (activeSceneBackdrop != null && !activeSceneBackdrop.isRecycled()) {
                                            activeSceneBackdrop.recycle();
                                        }
                                        activeSceneBackdrop = bmp;
                                        invalidate();
                                    }
                                });
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        isFetchingScene = false;
                    }
                }
            }).start();
        }

        void startAnimating() {
            isRunning = true;
            postInvalidateOnAnimation();
        }

        void stopAnimating() {
            isRunning = false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#030407"));

            int w = getWidth() > 0 ? getWidth() : 1920;
            int h = getHeight() > 0 ? getHeight() : 1080;

            long nowMs = SystemClock.uptimeMillis();
            long elapsedSinceSwitch = nowMs - lastQuoteSwitchTime;

            if (elapsedSinceSwitch >= QUOTE_DURATION_MS) {
                lastQuoteSwitchTime = nowMs;
                elapsedSinceSwitch = 0;
                int nextIdx;
                do {
                    nextIdx = random.nextInt(momentsPool.size());
                } while (nextIdx == currentMomentIndex && momentsPool.size() > 1);
                currentMomentIndex = nextIdx;
                fetchTmdbBackdropForMoment(momentsPool.get(currentMomentIndex));
            }

            float quoteAlpha = 1.0f;
            if (elapsedSinceSwitch < 1400L) {
                quoteAlpha = elapsedSinceSwitch / 1400f;
            } else if (elapsedSinceSwitch > QUOTE_DURATION_MS - 1400L) {
                quoteAlpha = (QUOTE_DURATION_MS - elapsedSinceSwitch) / 1400f;
            }

            if (activeSceneBackdrop != null && !activeSceneBackdrop.isRecycled()) {
                int sceneAlpha = (int) (quoteAlpha * 65);
                scenePaint.setAlpha(sceneAlpha);
                Rect src = new Rect(0, 0, activeSceneBackdrop.getWidth(), activeSceneBackdrop.getHeight());
                RectF dst = new RectF(0, 0, w, h);
                canvas.drawBitmap(activeSceneBackdrop, src, dst, scenePaint);
            }

            for (int i = 0; i < STAR_COUNT; i++) {
                starY[i] -= starSpeed[i];
                if (starY[i] < 0) {
                    starY[i] = h;
                    starX[i] = (float) (Math.random() * w);
                }
                float twinkle = (float) (Math.sin((nowMs * 0.002) + i) * 0.30 + 0.70);
                int a = (int) (starAlpha[i] * twinkle * 255);
                starfieldPaint.setColor(Color.argb(Math.min(255, Math.max(0, a)), 210, 230, 255));
                canvas.drawCircle(starX[i], starY[i], starRadius[i], starfieldPaint);
            }

            float cx = w / 2f;
            float cy = (h / 2f) - 60f;
            float sigSize = Math.min(w, h) * 0.21f;

            float baseHue = (float) ((nowMs % 48000L) / 48000.0 * 360.0);
            int cCore = Color.HSVToColor(new float[]{baseHue, 0.65f, 0.90f});
            float auraR = sigSize * 1.35f;

            auraPaint.setShader(new RadialGradient(
                    cx, cy, auraR,
                    new int[]{
                            Color.argb(0x30, Color.red(cCore), Color.green(cCore), Color.blue(cCore)),
                            Color.argb(0x10, Color.red(cCore), Color.green(cCore), Color.blue(cCore)),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.60f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, auraR, auraPaint);

            float breathAlpha = (float) (Math.sin(nowMs * 0.001) * 0.08 + 0.52);
            int sigAlphaInt = Math.min(255, Math.max(0, (int) (breathAlpha * 255)));
            logoPaint.setAlpha(sigAlphaInt);

            float sigLeft = cx - sigSize / 2f;
            float sigTop = cy - sigSize / 2f;

            if (signatureBitmap != null) {
                Rect src = new Rect(0, 0, signatureBitmap.getWidth(), signatureBitmap.getHeight());
                RectF dst = new RectF(sigLeft, sigTop, sigLeft + sigSize, sigTop + sigSize);
                canvas.drawBitmap(signatureBitmap, src, dst, logoPaint);
            } else if (fallbackLogo != null) {
                fallbackLogo.setAlpha(sigAlphaInt);
                int leftI = Math.round(sigLeft);
                int topI = Math.round(sigTop);
                int sizeI = Math.round(sigSize);
                fallbackLogo.setBounds(leftI, topI, leftI + sizeI, topI + sizeI);
                fallbackLogo.draw(canvas);
            }

            Date now = new Date();
            String timeStr = timeFmt.format(now);
            String dateStr = dateFmt.format(now).toUpperCase(new Locale("tr", "TR"));

            float clockY = cy + (sigSize / 2f) + 64f;
            clockPaint.setShadowLayer(26f, 0f, 4f, Color.argb(0x77, Color.red(cCore), Color.green(cCore), Color.blue(cCore)));
            float tw = clockPaint.measureText(timeStr);
            canvas.drawText(timeStr, cx - tw / 2f, clockY, clockPaint);

            datePaint.setShadowLayer(14f, 0f, 2f, Color.parseColor("#99000000"));
            float dw = datePaint.measureText(dateStr);
            canvas.drawText(dateStr, cx - dw / 2f, clockY + 30f, datePaint);

            ClockDream.CinemaMoment activeMoment = momentsPool.get(currentMomentIndex);
            String quoteText = activeMoment.quote;
            String filmLabel = "— " + activeMoment.filmTitle;

            int qA = (int) (quoteAlpha * 245);
            quotePaint.setColor(Color.argb(Math.min(255, Math.max(0, qA)), 248, 250, 252));
            quotePaint.setShadowLayer(22f, 0f, 4f, Color.parseColor("#F0000000"));

            titlePaint.setColor(Color.argb(Math.min(255, Math.max(0, (int) (quoteAlpha * 220))), 56, 189, 248));
            titlePaint.setShadowLayer(18f, 0f, 3f, Color.parseColor("#EE000000"));

            float quoteBottomY = h - 110f;
            float qw = quotePaint.measureText(quoteText);

            if (qw > w - 160) {
                int mid = quoteText.length() / 2;
                int split = quoteText.indexOf(" ", mid);
                if (split < 0) split = mid;
                String line1 = quoteText.substring(0, split).trim();
                String line2 = quoteText.substring(split).trim();
                float w1 = quotePaint.measureText(line1);
                float w2 = quotePaint.measureText(line2);
                canvas.drawText(line1, cx - w1 / 2f, quoteBottomY - 48f, quotePaint);
                canvas.drawText(line2, cx - w2 / 2f, quoteBottomY - 4f, quotePaint);
            } else {
                canvas.drawText(quoteText, cx - qw / 2f, quoteBottomY - 20f, quotePaint);
            }

            float fw = titlePaint.measureText(filmLabel);
            canvas.drawText(filmLabel, cx - fw / 2f, quoteBottomY + 36f, titlePaint);

            if (isRunning) {
                postInvalidateOnAnimation();
            }
        }
    }

}