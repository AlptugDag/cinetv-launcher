package com.cinetv.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cinetv.launcher.ui.Glass;
import com.cinetv.launcher.ui.UiKit;

import com.cinetv.launcher.ai.LlmClient;
import com.cinetv.launcher.ai.MovieAssistant;
import com.cinetv.launcher.device.DeviceTweaks;
import com.cinetv.launcher.device.HardwareKeys;
import com.cinetv.launcher.device.MemoryOptimizer;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.remote.LocalAdb;
import com.cinetv.launcher.remote.TvWebRemoteServer;
import com.cinetv.launcher.service.AppCatalog;
import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.AppTileItem;
import com.cinetv.launcher.service.AppUpdater;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.MovieCatalog.Movie;
import com.cinetv.launcher.service.PosterCache;
import com.cinetv.launcher.service.Prefs;
import com.cinetv.launcher.service.SecretStore;
import com.cinetv.launcher.service.ThemeManager;
import com.cinetv.launcher.service.WeatherService;
import com.cinetv.launcher.ui.AllApps;
import com.cinetv.launcher.ui.AppArtwork;
import com.cinetv.launcher.ui.AppGrid;
import com.cinetv.launcher.ui.Controls;
import com.cinetv.launcher.ui.DvdCase;
import com.cinetv.launcher.ui.DvdCaseView;
import com.cinetv.launcher.ui.DvdOpeningView;
import com.cinetv.launcher.ui.Glow;
import com.cinetv.launcher.ui.HomeScreen;
import com.cinetv.launcher.ui.ModalHost;
import com.cinetv.launcher.ui.Panels;
import com.cinetv.launcher.ui.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The home screen. This class only wires the layers together:
 *   ui/      what is drawn (HomeScreen, PosterShelf, AppGrid, Panels...)
 *   service/ apps, films, themes, weather, secrets
 *   ai/      search understanding and curation
 *   remote/  the phone companion
 *   device/  keys, picture calibration, memory, performance tier
 */
public class MainActivity extends Activity implements HomeScreen.Listener, AppGrid.Host, Panels.Actions,
        TvWebRemoteServer.Controller {

    public static volatile MainActivity instance;

    private static final long SCREENSAVER_AFTER_MS = 5 * 60 * 1000L;
    private static final long SLEEP_AFTER_SAVER_MS = 5 * 60 * 1000L;
    private static final long AI_SHELF_EVERY_MS = 3 * 60 * 60 * 1000L;
    private static final String SHELF_CACHE = "shelf_cache";
    private static final String SHELF_TITLE = "shelf_title";

    private static final String[][] BUNDLED = {
            {"https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", "tt15239678", "Dune: Part Two", "2024"},
            {"https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg", "tt15398776", "Oppenheimer", "2023"},
            {"https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", "tt0816692", "Interstellar", "2014"},
            {"https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg", "tt1877830", "The Batman", "2022"},
            {"https://image.tmdb.org/t/p/w500/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg", "tt1856101", "Blade Runner 2049", "2017"},
            {"https://image.tmdb.org/t/p/w500/oYuLEt3zVCKq57qu2F8dT7NIa6f.jpg", "tt1375666", "Inception", "2010"},
            {"https://image.tmdb.org/t/p/w500/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg", "tt9218128", "Gladiator II", "2024"},
            {"https://image.tmdb.org/t/p/w500/hA2ple9q4qnwxp3hKVNhroipsir.jpg", "tt1392190", "Mad Max: Fury Road", "2015"},
            {"https://image.tmdb.org/t/p/w500/arw2vcBveWOVZr6pxd9XTd1TdQa.jpg", "tt0468569", "The Dark Knight", "2008"},
            {"https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", "tt0068646", "The Godfather", "1972"},
            {"https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg", "tt0110912", "Pulp Fiction", "1994"},
            {"https://image.tmdb.org/t/p/w500/bptfVGEQuv6vDTIMVCHjJ9Dz8PX.jpg", "tt0137523", "Fight Club", "1999"},
            {"https://image.tmdb.org/t/p/w500/6oom5QYQ2yQTMJIbnvbkBL9cDK6.jpg", "tt0111161", "The Shawshank Redemption", "1994"},
            {"https://image.tmdb.org/t/p/w500/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg", "tt0109830", "Forrest Gump", "1994"},
            {"https://image.tmdb.org/t/p/w500/fIE3lAGcZDV1G6XM5KmuWnNsPp1.jpg", "tt0133093", "The Matrix", "1999"},
            {"https://image.tmdb.org/t/p/w500/6FfCtAuVAW8XJjZ7eWeLibRLWTw.jpg", "tt0076759", "Star Wars", "1977"},
            {"https://image.tmdb.org/t/p/w500/velWPhVMQeQKcxggNEU8YmIo52R.jpg", "tt0120737", "The Fellowship of the Ring", "2001"},
            {"https://image.tmdb.org/t/p/w500/rCzpDGLbOoPwLjy3OAm5NUPOTrC.jpg", "tt0167260", "The Return of the King", "2003"},
            {"https://image.tmdb.org/t/p/w500/kOVEVeg59E0wsnXmF9nBYW2669O.jpg", "tt0102926", "The Silence of the Lambs", "1991"},
            {"https://image.tmdb.org/t/p/w500/saHP97rTPS5eLmrLQEcANmKrsFl.jpg", "tt0114814", "The Usual Suspects", "1995"}
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private HomeScreen home;
    private Set<String> knownPackages;
    private BroadcastReceiver packageReceiver;
    private DateFormat timeFmt;
    private DateFormat dateFmt;
    private int lastShelfHour = -1;
    private int lastShelfDay = -1;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            checkNightViewingPrompt();
            MemoryOptimizer.ensureAirPlayReceiver(MainActivity.this);
            handler.postDelayed(this, 30_000L);
        }
    };
    private final Runnable idle = this::showScreensaver;
    private final Runnable sleepPanel = new Runnable() {
        @Override
        public void run() {
            if (DeviceTweaks.isMediaPlaying(MainActivity.this)) {
                handler.postDelayed(this, 2 * 60 * 1000L);
            } else {
                DeviceTweaks.sleepPanel(MainActivity.this);
            }
        }
    };

    // ---- lifecycle --------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instance = this;
        super.onCreate(savedInstanceState);
        // Suppress OEM MediaTek/Arçelik TV tuner window immediately on boot before its window can display
        background(() -> com.cinetv.launcher.remote.LocalAdb.shell(this, "am force-stop com.mediatek.wwtv.tvcenter 2>/dev/null; am force-stop com.mediatek.tv.oneworld.tvcenter 2>/dev/null; am force-stop com.arcelik.tv.livetv 2>/dev/null; am force-stop com.google.android.tv.dtvprovider 2>/dev/null", 500));
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        overridePendingTransition(0, 0);
        UiKit.init(this);

        com.cinetv.launcher.ui.GlassBevelView.setAberrationEnabled(Prefs.glassBevel(this));
        timeFmt = android.text.format.DateFormat.getTimeFormat(this);
        dateFmt = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault());

        final Context app = getApplicationContext();
        new Thread(() -> {
            SecretStore.migrateLegacy(app);
            DeviceTweaks.applyLauncherDefaults(app);
            PosterCache.trimDisk(app);
            // the phone remote's key bridge (the TV may ask once to allow CineTV)
            com.cinetv.launcher.remote.KeyBridge.warmUp(app);
            com.cinetv.launcher.device.DeviceVerifier.autoVerify(app);
        }, "cinetv-init").start();

        home = new HomeScreen(this, this, this);
        setContentView(home.root);
        if (!splashShown) {
            splashShown = true;
            home.showSplash();
        }
        applyTheme(ThemeManager.current(this).id, false);
        home.applySpatial(Prefs.spatial3d(this));
        home.modal.setOnClosed(() -> {
            if (!home.shelf.hasFocus() && !home.opening.isPlaying()) home.shelf.requestFocus();
        });
        home.keyboard.setListener(new com.cinetv.launcher.ui.GlassKeyboard.Listener() {
            @Override
            public void onSubmit(String text) {
                onSearch(text);
            }

            @Override
            public void onVoice() {
                MainActivity.this.onVoice();
            }

            @Override
            public void onFocusLight(View v, int color) {
                home.environment.focusOn(v, color);
            }

            @Override
            public void onClosed() {
                if (!home.shelf.hasFocus()) home.search.requestFocus();
            }
        });
        home.shelf.setListener(new com.cinetv.launcher.ui.PosterShelf.Listener() {
            @Override
            public void onOpen(Movie movie, DvdCaseView view) {
                openTitle(movie, view);
            }

            @Override
            public void onFocusCase(DvdCaseView view, int accent) {
                home.environment.focusOn(view, accent);
                com.cinetv.launcher.device.AmbilightSync.updateColor(MainActivity.this, accent);
                final Movie m = home.shelf.focusedMovie();
                if (m != null) prefetchMovie(m);
            }

            @Override
            public boolean onEdge(int keyCode) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) home.focusHeader();
                else home.apps.focusDock(0);
                return true;
            }
        });

        loadApps(true);
        showCachedShelf();
        Calendar cal = Calendar.getInstance();
        lastShelfHour = cal.get(Calendar.HOUR_OF_DAY);
        lastShelfDay = cal.get(Calendar.DAY_OF_YEAR);
        lastShelfAt = android.os.SystemClock.elapsedRealtime();
        // A single debounced refresh on start to prevent startup shelf collisions
        handler.postDelayed(() -> {
            if (!isCurating && android.os.SystemClock.elapsedRealtime() - lastShelfAt >= 2000) {
                refreshShelf();
            }
        }, 2500);

        // Every frame Home draws is timed; slow ones switch on the light shelf, fast ones switch it off.
        getWindow().addOnFrameMetricsAvailableListener((window, metrics, dropped) -> {
            long total = metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION);
            if (PerformanceProfile.noteFrame(getApplicationContext(), total) && home != null) {
                handler.post(() -> home.shelf.qualityChanged());
            }
        }, frameMetricsHandler());
        TvWebRemoteServer.setController(this);
        TvWebRemoteServer.start(this);
        com.cinetv.launcher.party.CinePartyManager.get(this).addListener(partyLauncher);
        AppUpdater.checkAndAutoUpdateOnBoot(this);
        MemoryOptimizer.ensureAirPlayReceiver(this);
        com.cinetv.launcher.device.AirPodsLink.ensureStarted(this);
        com.cinetv.launcher.ai.EmaLightningTts.prewarmNativeFallback(this);
        handler.postDelayed(() -> com.cinetv.launcher.ai.EmaLightningTts.prewarmFillers(this), 25_000);

        if (!Prefs.get(this).getBoolean("tv_opt_auto_applied", false)) {
            Prefs.get(this).edit().putBoolean("tv_opt_auto_applied", true).apply();
            MemoryOptimizer.applyTvOptimizations(this);
            com.cinetv.launcher.device.AvOptimizer.optimize(this);
        }

        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                loadApps(true);
            }
        };
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);

        home.shelf.post(() -> {
            home.shelf.requestFocus();
            // Real frame times decide whether this TV keeps its effects (auto mode only).
            // The mode stays; its effects adapt to what holds this TV back, in place (no rebuild of Home).
            PerformanceProfile.watchFrames(this, 6000, () -> home.environment.apply(ThemeManager.current(this), ThemeManager.isLight(this)));
        });
        handleIntent(getIntent());

        // TV box orientation safeguard: ensure landscape orientation without forcing hardware matrix flip
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            android.graphics.Point pt = new android.graphics.Point();
            d.getRealSize(pt);
            if (pt.x < pt.y) {
                // TV box display got stuck in portrait: unstick it back to 0° landscape
                try {
                    android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.USER_ROTATION, android.view.Surface.ROTATION_0);
                    android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
                } catch (Throwable ignored) {}
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                LocalAdb.shell(this, "settings put system user_rotation 0; settings put system accelerometer_rotation 0; wm set-fix-to-user-rotation disabled", 2000);
            }
        } catch (Throwable ignored) {}

        boolean alreadyConfigured = (HomeGuard.isDefault(this) && AppLauncher.isLaunchable(getPackageManager(), AppLauncher.NUVIO))
                || com.cinetv.launcher.ui.Brand.isPersonal(this);
        if (alreadyConfigured && !Prefs.get(this).contains("wizard_done")) {
            Prefs.get(this).edit().putBoolean("wizard_done", true).putBoolean(Prefs.FIRST_SETUP_DONE, true).apply();
        }

        if (!Prefs.get(this).getBoolean("wizard_done", false)) {
            // Standalone fresh install: present the interactive Kurulum Sihirbazı
            handler.postDelayed(() -> {
                if (!isFinishing() && home != null && !home.modal.isShowing()) {
                    home.modal.setOnClosed(this::onWizardFinished);
                    Panels.wizard(this, home.modal);
                }
            }, 1800);
        } else if (!Prefs.get(this).getBoolean(Prefs.TOUR_DONE, false)) {
            handler.postDelayed(tourCheck, 4000);
        }

        if (!HomeGuard.isDefault(this)) {
            HomeGuard.ensureDefaultHome(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (home == null) return;
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);
        TvWebRemoteServer.setController(this);
        if (home == null) return;
        inFront = true;
        home.opening.dismiss();
        home.environment.resume();
        DeviceTweaks.syncNightMode(this);
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        refreshWeather();
        loadApps(false);
        MemoryOptimizer.ensureAirPlayReceiver(this);
        com.cinetv.launcher.device.AirPodsLink.ensureStarted(this);
        MemoryOptimizer.sweepAsync(this);
        if (!HomeGuard.isDefault(this)) {
            new Thread(() -> HomeGuard.ensureDefault(this), "cinetv-resume-guard").start();
        }
        PerformanceProfile.noteMemory(this);
        AppUpdater.checkAndAutoUpdateOnBoot(this);
        resetIdle();
        // Back on Home: refresh shelf if hour/day changed, or after shelf timer
        Calendar c = Calendar.getInstance();
        int curHour = c.get(Calendar.HOUR_OF_DAY);
        int curDay = c.get(Calendar.DAY_OF_YEAR);
        if (lastShelfHour != -1 && (lastShelfHour != curHour || lastShelfDay != curDay)) {
            refreshShelf();
        } else if (android.os.SystemClock.elapsedRealtime() - lastShelfAt > SHELF_EVERY_MS) {
            refreshShelf();
        }
        lastShelfHour = curHour;
        lastShelfDay = curDay;
        // Suppress unwanted OEM TV tuner attempting to steal focus
        background(() -> {
            if (DeviceTweaks.isUnwantedTuner()) {
                LocalAdb.shell(this, "am force-stop com.mediatek.wwtv.tvcenter 2>/dev/null; am force-stop com.mediatek.tv.oneworld.tvcenter 2>/dev/null; am force-stop com.arcelik.tv.livetv 2>/dev/null", 500);
            }
        });
        handler.postDelayed(() -> {
            TvKeyInterceptorService svc = TvKeyInterceptorService.getInstance();
            String currentPkg = svc != null ? svc.getLastPackage() : null;
            boolean isOemTuner = "com.mediatek.wwtv.tvcenter".equals(currentPkg) || "com.arcelik.tv.livetv".equals(currentPkg);
            if (!isFinishing() && !hasWindowFocus() && isOemTuner && DeviceTweaks.isUnwantedTuner()) {
                background(() -> LocalAdb.shell(this, "am force-stop com.mediatek.wwtv.tvcenter 2>/dev/null; am force-stop com.arcelik.tv.livetv 2>/dev/null", 800));
                try {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                } catch (Throwable ignored) {
                }
            }
        }, 150);
        checkNuvioUpdatePrompt();
        checkPendingUpdateBanner();
        checkNightViewingPrompt();
        maybeSpeakStartupGreeting();
    }

    private static long lastGreetingMs = 0L;
    private static final long GREETING_INTERVAL_MS = 15 * 60 * 1000L;
    private int greetingAttempts = 0;

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    private void maybeSpeakStartupGreeting() {
        long now = System.currentTimeMillis();
        if (lastGreetingMs > 0 && now - lastGreetingMs < GREETING_INTERVAL_MS) return;
        greetingAttempts = 0;
        handler.removeCallbacks(greetingRetryRunnable);
        handler.postDelayed(greetingRetryRunnable, 1800L);
    }

    private final Runnable greetingRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || !inFront) return;
            greetingAttempts++;
            if (!isNetworkAvailable() && greetingAttempts < 2) {
                handler.postDelayed(this, 1500L);
                return;
            }
            performStartupGreeting();
        }
    };

    private void performStartupGreeting() {
        if (!inFront || isFinishing() || partyStage != null) return;
        lastGreetingMs = System.currentTimeMillis();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        String city = Prefs.city(this);
        String weather = com.cinetv.launcher.service.WeatherService.getLastFull();
        String name = com.cinetv.launcher.ai.GamzeMemory.name(this);

        String greeting;
        if (tr) {
            String salute = hour >= 5 && hour < 12 ? "Günaydın" : hour >= 12 && hour < 18 ? "İyi günler"
                    : hour >= 18 && hour < 23 ? "İyi akşamlar" : "İyi geceler";
            if (!name.isEmpty()) salute += " " + name;
            String[] lines = hour >= 23 || hour < 5
                    ? new String[]{"Gece yarısı filmi için tam vakit.", "Sessiz bir gece, iyi bir film. Vitrin hazır.", "Uykun yoksa sana göre birkaç şey seçtim."}
                    : hour >= 18
                    ? new String[]{"Bu akşam için vitrini hazırladım.", "Akşamın filmi seni bekliyor.", "Ne izlemek istersin? Bana söylemen yeterli."}
                    : new String[]{"Vitrin hazır.", "Bugün için birkaç güzel seçim yaptım.", "Bir şey arıyorsan bana söyle, bulayım."};
            StringBuilder sb = new StringBuilder(salute).append(". ").append(lines[(int) (System.currentTimeMillis() / 1000 % lines.length)]);
            if (weather != null && !weather.isEmpty() && city != null && !city.trim().isEmpty() && hour >= 6 && hour <= 21) {
                String w = weather.substring(0, 1).toLowerCase(new Locale("tr")) + weather.substring(1);
                sb.append(" ").append(city.trim()).append("'da hava ").append(w).append(".");
            }
            greeting = sb.toString();
        } else {
            String salute = hour >= 5 && hour < 12 ? "Good morning" : hour >= 12 && hour < 18 ? "Good afternoon" : "Good evening";
            greeting = salute + (name.isEmpty() ? "" : " " + name) + ". The shelf is ready; just tell me what you feel like watching.";
        }

        com.cinetv.launcher.ai.EmaLightningTts.speak(this, greeting);
        if (home != null) {
            home.showBubble(greeting, false, 6000);
        }
    }

    private boolean wokenForBanner = false;
    private boolean userInteractedDuringBanner = false;

    public void presentUpdateBannerGuaranteed(final String title, final String message, final boolean isError) {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        final boolean screenWasOn = pm != null && pm.isInteractive();

        if (!screenWasOn) {
            wokenForBanner = true;
            userInteractedDuringBanner = false;
            try {
                if (pm != null) {
                    android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP | android.os.PowerManager.ON_AFTER_RELEASE,
                            "cinetv:banner_wakeup");
                    wl.acquire(25_000L);
                }
            } catch (Throwable ignored) {}
            com.cinetv.launcher.remote.LocalAdb.shell(this, "input keyevent KEYCODE_WAKEUP", 1000);
        } else {
            wokenForBanner = false;
            userInteractedDuringBanner = false;
        }

        handler.postDelayed(new Runnable() {
            int attempts = 0;
            @Override
            public void run() {
                android.os.PowerManager p = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                boolean interactive = p != null && p.isInteractive();
                if (!interactive && attempts < 4) {
                    attempts++;
                    com.cinetv.launcher.remote.LocalAdb.shell(MainActivity.this, "input keyevent KEYCODE_WAKEUP", 800);
                    handler.postDelayed(this, 350);
                    return;
                }

                if (home != null) {
                    home.showUpdateBanner(title, message, isError, () -> {
                        if (wokenForBanner && !userInteractedDuringBanner) {
                            wokenForBanner = false;
                            com.cinetv.launcher.device.DeviceTweaks.sleepPanel(MainActivity.this);
                        }
                    });
                }
            }
        }, screenWasOn ? 100 : 900);
    }

    private void checkPendingUpdateBanner() {
        android.content.SharedPreferences p = com.cinetv.launcher.service.Prefs.get(this);
        String updatedVer = p.getString("just_updated_version", null);
        if (updatedVer != null) {
            p.edit().remove("just_updated_version").apply();
            presentUpdateBannerGuaranteed("CineTV " + updatedVer + " Sürümüne Güncellendi!", "Yeniliklerin keyfini çıkarın.", false);
        }
        String updateErr = p.getString("update_error_message", null);
        if (updateErr != null) {
            p.edit().remove("update_error_message").apply();
            presentUpdateBannerGuaranteed(updateErr, "Lütfen Ayarlar › Sistem › Sürüm menüsünden manuel güncellemeyi deneyin.", true);
        }
    }

    public void showUpdateBanner(String title, String message, boolean isError) {
        presentUpdateBannerGuaranteed(title, message, isError);
    }

    private void checkNightViewingPrompt() {
        // Canceled per user request: night mode side prompt completely disabled.
        boolean active = com.cinetv.launcher.device.NightCare.isNightViewingModeActive(this);
        if (!active && home != null) {
            home.applyNightViewingMode(false);
        }
    }

    private static boolean inFront;
    /** The opening plays once per start of CineTV, not on every return to Home. */
    private static boolean splashShown;

    public static boolean isInFront() {
        return inFront;
    }

    @Override
    protected void onPause() {
        inFront = false;
        if (home == null) {
            super.onPause();
            return;
        }
        home.environment.pause();
        home.trailer.stop();
        com.cinetv.launcher.ai.EmaLightningTts.stop();
        if (home.voice != null) home.voice.dismiss();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(idle);
        handler.removeCallbacks(sleepPanel);
        hideScreensaver();
        // Deep background hibernation: immediately free memory for external media player & 4K video playback
        PosterCache.trimMemory();
        Glow.clear();
        MemoryOptimizer.cleanBackground(this, getPackageName());
        System.gc();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        handler.removeCallbacksAndMessages(null);
        TvWebRemoteServer.setController(null);
        try {
            if (packageReceiver != null) unregisterReceiver(packageReceiver);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            PosterCache.trimMemory();
            Glow.clear();
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String toastMsg = intent.getStringExtra("toast");
        if (toastMsg != null && !toastMsg.trim().isEmpty()) {
            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
        }
        String bannerTitle = intent.getStringExtra("banner_title");
        if (bannerTitle == null && "com.cinetv.launcher.ACTION_UPDATE_BANNER".equals(intent.getAction())) {
            bannerTitle = intent.getStringExtra("title");
        }
        if (bannerTitle != null && !bannerTitle.isEmpty()) {
            String bannerMsg = intent.getStringExtra("banner_msg");
            if (bannerMsg == null) bannerMsg = intent.getStringExtra("message");
            presentUpdateBannerGuaranteed(bannerTitle, bannerMsg, false);
        }
        String theme = intent.getStringExtra("theme");
        if (theme != null && !theme.isEmpty()) applyTheme(theme);
        String action = intent.getStringExtra("action");
        if (action == null) action = intent.getAction();
        if (action == null) return;
        else if ("celebration".equals(action) || intent.getBooleanExtra("force_celebration", false)) {
            handler.postDelayed(() -> {
                com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.today(this);
                if (day == null && intent.getBooleanExtra("force_celebration", false)) {
                    day = com.cinetv.launcher.service.SpecialDay.forDate(2026, 10, 29, 12, 0);
                }
                if (day != null) showCinematicCelebration(day);
            }, 300);
        }
        else if ("setup".equals(action)) handler.postDelayed(() -> Panels.setup(this, home.modal, true), 1500);
        else if ("settings".equals(action)) handler.postDelayed(this::onSettings, 300);
        else if ("similar".equals(action)) handler.postDelayed(this::openSimilarForFocused, 300);
        else if ("changelog".equals(action)) handler.postDelayed(() -> Panels.changelog(this, home.modal), 300);
        else if ("voice".equals(action)) {
            wasLaunchedForVoice = true;
            handler.postDelayed(this::onVoice, 250);
        }
        else if ("tour".equals(action)) handler.postDelayed(this::startTour, 300);
        else if ("trailers_on".equals(action) || "trailers_off".equals(action)) {
            final boolean on = "trailers_on".equals(action);
            Prefs.setFlag(this, Prefs.TRAILER_MOMENTS, on);
            handler.postDelayed(() -> home.setTrailerMode(on), 300);
        }
        else if ("phone_cast".equals(action)) handler.postDelayed(() -> Panels.pairing(this, home.modal, "cast"), 300);
        else if (action.endsWith("SHOW_SCREENSAVER") || "show_screensaver".equals(action)) handler.postDelayed(this::showScreensaver, 300);
        else if (action.endsWith("SHOW_ALL_APPS") || "show_all_apps".equals(action)) handler.postDelayed(this::onAllApps, 300);
        else if (action.endsWith("SEARCH") || "search".equals(action)) {
            final String q = intent.getStringExtra("query");
            if (q != null && !q.trim().isEmpty()) handler.postDelayed(() -> onSearch(q), 300);
        }
        else if ("cinesub".equals(action) || "com.cinetv.launcher.ACTION_CINESUB".equals(action)) {
            final String cTitle = intent.getStringExtra("title");
            final String cInsight = intent.getStringExtra("insight");
            handler.postDelayed(() -> {
                if (cInsight != null && !cInsight.isEmpty()) {
                    com.cinetv.launcher.ui.CineSubOverlay.showInsight(this, cTitle, cInsight);
                } else {
                    com.cinetv.launcher.ui.CineSubOverlay.trigger(this);
                }
            }, 300);
        }
        else if ("show_cinesub_live".equals(action)) {
            handler.postDelayed(this::showLiveCineSubScene, 300);
        }
        else if ("show_cineroulette_live".equals(action)) {
            handler.postDelayed(() -> showParty(com.cinetv.launcher.party.CinePartyManager.Mode.ROULETTE), 300);
        }
        else if ("show_cinequiz_live".equals(action)) {
            handler.postDelayed(() -> showParty(com.cinetv.launcher.party.CinePartyManager.Mode.QUIZ), 300);
        }
        else if ("show_snakephile_live".equals(action)) {
            handler.postDelayed(this::showLiveSnakePhileScene, 300);
        }
        else if ("show_cinesessiz_live".equals(action)) {
            handler.postDelayed(() -> showParty(com.cinetv.launcher.party.CinePartyManager.Mode.SESSIZ), 300);
        }
    }

    // ---- keys -------------------------------------------------------------------------------------

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        userInteractedDuringBanner = true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (home == null) return super.dispatchKeyEvent(e);
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) {
            userInteractedDuringBanner = true;
            conversationTurns = 0;
            resetIdle();
        }
        if (liveCineSubView != null) {
            if (down && !com.cinetv.launcher.ui.CineSubOverlay.isShowing()) hideLiveCineSubScene();
            return true;
        }
        if (partyStage != null && !home.modal.isShowing()) {
            return partyStage.dispatchKeyEvent(e);
        }
        if (liveCineDuelView != null) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (down) hideLiveCineDuelScene();
                return true;
            }
            return liveCineDuelView.dispatchKeyEvent(e);
        }
        if (liveSnakePhileView != null) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (down) hideLiveSnakePhileScene();
                return true;
            }
            return liveSnakePhileView.dispatchKeyEvent(e);
        }
        // Throttle D-pad key repeats to prevent rapid accidental skips on Bluetooth/IR TV remotes
        if (down && e.getRepeatCount() > 0 && !home.modal.isShowing() && !home.keyboard.isShowing()) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP) {
                return true;
            }
            // Left/right repeats are paced by whoever owns the focus (the shelf counts its own steps).
        }
        if (home.screensaver.getVisibility() == View.VISIBLE) {
            if (down) hideScreensaver();
            return true;
        }
        if (home.tour.isShowing()) return home.tour.handleKey(e);
        if (home.pattern.isShowing()) {
            if (down && (e.getKeyCode() == KeyEvent.KEYCODE_BACK || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER)) home.pattern.show("off");
            return true;
        }
        if (home.opening.isPlaying()) return home.opening.dispatchKeyEvent(e);
        if (home.keyboard.isShowing()) {
            if (home.keyboard.handleKey(e, getCurrentFocus())) return true;
            return super.dispatchKeyEvent(e);
        }
        if (home.voice.isShowing()) return home.voice.dispatchKeyEvent(e);
        if (down && e.getRepeatCount() == 0 && !home.modal.isShowing() && !home.voice.isShowing()
                && (e.getKeyCode() == KeyEvent.KEYCODE_SEARCH || e.getKeyCode() == KeyEvent.KEYCODE_VOICE_ASSIST || e.getKeyCode() == KeyEvent.KEYCODE_ASSIST)) {
            onVoice();
            return true;
        }
        if (home.modal.isShowing()) {
            if (home.modal.handleKey(e, getCurrentFocus())) return true;
            return super.dispatchKeyEvent(e);
        }
        if (home.apps.isMoving()) {
            if (down && home.apps.handleMoveKey(e.getKeyCode())) {
                if (!home.apps.isMoving()) home.moveHint.setVisibility(View.GONE);
                return true;
            }
            return true;
        }
        if (down && e.getRepeatCount() == 0) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_HEADSETHOOK) {
                if (home.shelf != null) {
                    home.shelf.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, e);
                    return true;
                }
            } else if (code == KeyEvent.KEYCODE_MEDIA_NEXT) {
                if (home.shelf != null) {
                    home.shelf.step(1);
                    return true;
                }
            } else if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                if (home.shelf != null) {
                    home.shelf.step(-1);
                    return true;
                }
            }
            String action = HardwareKeys.actionFor(this, e.getKeyCode(), e.getScanCode());
            if (action != null) {
                if (HardwareKeys.SIMILAR_MOVIES.equals(action)) {
                    openSimilarForFocused();
                    return true;
                }
                if (!HardwareKeys.perform(this, action)) onAllApps();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (home == null) return;
        com.cinetv.launcher.device.SystemAudio.back(this);
        com.cinetv.launcher.ai.EmaLightningTts.stop();
        if (home.modal.isShowing()) {
            home.modal.back();
            return;
        }
        if (home.isAiCardShowing()) {
            home.hideAiCard();
        } else if (!home.shelf.hasFocus()) {
            home.shelf.requestFocus();
            home.scroll.glideHome();
        }
    }

    // ---- screensaver ------------------------------------------------------------------------------

    private void resetIdle() {
        com.cinetv.launcher.ui.CinemaScreensaverView.warmUp(this);
        handler.removeCallbacks(idle);
        handler.removeCallbacks(sleepPanel);
        handler.postDelayed(idle, SCREENSAVER_AFTER_MS);
    }

    private void hideScreensaver() {
        if (home.screensaver.getVisibility() != View.VISIBLE) return;
        home.screensaverView.stop();
        home.screensaver.setVisibility(View.GONE);
        home.environment.resume();
        handler.removeCallbacks(sleepPanel);
    }

    // ---- header -----------------------------------------------------------------------------------

    private void updateClock() {
        Date now = new Date();
        home.clock.setText(timeFmt.format(now));
        home.date.setText(dateFmt.format(now));
        home.updateSpecialDay();
        home.updateRamadanBadge();
        if (ThemeManager.MODE_AUTO.equals(ThemeManager.appearance(this))) applyAppearance();
        checkSpecialDayCelebration();

        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int day = c.get(Calendar.DAY_OF_YEAR);
        if (lastShelfHour != -1 && (lastShelfHour != hour || lastShelfDay != day)) {
            if (home != null && !home.modal.isShowing() && home.screensaver.getVisibility() != View.VISIBLE && !isCurating) {
                refreshShelf();
            }
        }
        lastShelfHour = hour;
        lastShelfDay = day;
    }

    @Override
    public void refreshWeather() {
        WeatherService.fetch(this, false, text -> home.weather.setText((text != null && !text.isEmpty()) ? "· " + text : ""));
    }

    @Override
    public void onLogo() {
        // The signature opens its author's page; the gear opens Settings.
        Panels.about(this, home.modal, this);
    }

    @Override
    public void onSystemSettings() {
        AppLauncher.openSystemSettings(this);
    }

    @Override
    public void onPhone() {
        Panels.pairing(this, home.modal, "search");
    }

    @Override
    public void onNetwork() {
        AppLauncher.openWifiSettings(this);
    }

    @Override
    public void onInputs() {
        Panels.inputs(this, home.modal);
    }

    @Override
    public void onAllApps() {
        final Context app = getApplicationContext();
        background(() -> {
            List<AppCatalog.Entry> apps = AppCatalog.drawerApps(app);
            ui(() -> AllApps.show(this, home.modal, getString(R.string.all_apps_count, apps.size()), apps, "",
                    new AllApps.Listener() {
                        @Override
                        public void open(String pkg) {
                            if (handlePartyAppLaunch(pkg)) return;
                            if (!AppLauncher.launch(MainActivity.this, pkg)) toast(R.string.app_not_found);
                        }

                        @Override
                        public void actions(String pkg, String label) {
                            AppTileItem item = new AppTileItem();
                            item.packageName = pkg;
                            item.label = label;
                            showActions(item, false, -1, null);
                        }
                    }));
        });
    }

    @Override
    public void onRefreshShelf() {
        refreshShelf();
        toast(R.string.refresh_shelf_hint);
    }

    @Override
    public void onSettings() {
        Panels.settings(this, home.modal, this);
    }

    @Override
    public void onFocusLight(View v, int color) {
        home.environment.focusOn(v, color);
    }

    // ---- search & AI ------------------------------------------------------------------------------

    private static final int REQ_VOICE = 41;
    private int searchGen;
    private boolean lastSearchFromVoice = false;

    /** Spoken turns in a row without the viewer touching the remote; Gamze stops asking back after three. */
    private int conversationTurns;

    @Override
    public void onSearch(final String query) {
        search(query, false);
    }

    private void search(final String query, final boolean fromVoice) {
        if (query == null || query.trim().isEmpty()) return;
        lastSearchFromVoice = fromVoice;
        if (!fromVoice) conversationTurns = 0;
        com.cinetv.launcher.ai.EmaLightningTts.stop();
        home.keyboard.hide();
        home.search.setText(query);
        final int gen = ++searchGen;

        // Spoken requests get an instant, cached "hmm" while Gamze thinks: silence feels like a machine.
        if (fromVoice && LlmClient.isConfigured(this)) {
            com.cinetv.launcher.ai.EmaLightningTts.speakMoment(this,
                    com.cinetv.launcher.ai.EmaLightningTts.ProactiveMoment.SEARCH, com.cinetv.launcher.ai.EmaLightningTts.filler(query));
        }

        // Questions get the answer bubble; film, people and theme searches only change the shelf.
        final boolean question = MovieAssistant.isQuestion(query);
        if (question) home.showThinking();
        else {
            home.hideAiCard();
            home.shelfLabel.setText(getString(R.string.shelf_building, MovieAssistant.cleanShelfName(query)).toUpperCase(Locale.getDefault()));
        }
        final Context app = getApplicationContext();
        final List<MovieCatalog.Movie> currentShelf = home.shelf.movies();
        background(() -> {
            MovieAssistant.Reply r = MovieAssistant.ask(app, query, currentShelf, PerformanceProfile.shelfSize(app));
            ui(() -> {
                if (gen != searchGen) return;
                if (r.kind == MovieAssistant.Kind.PLAY && !r.movies.isEmpty()) {
                    home.hideAiCard();
                    openTitle(r.movies.get(0), null);
                    return;
                }
                if (!r.movies.isEmpty()) {
                    setShelf(r.title.isEmpty() ? MovieAssistant.cleanShelfName(query) : r.title, r.movies, true);
                    home.shelf.requestFocus();
                }
                if (!r.answer.isEmpty()) {
                    // Stays long enough to read, then leaves by itself.
                    home.showBubble(r.answer, false, Math.max(6000, 2500 + 60L * r.answer.length()));
                    String spoken = r.voice.isEmpty() ? r.answer : r.voice;
                    com.cinetv.launcher.ai.GamzeConversation.record(query, spoken);
                    reply(spoken, r.followUp, gen);
                } else if (r.movies.isEmpty()) {
                    if (question) {
                        String msg = getString(LlmClient.isConfigured(this) || MovieCatalog.hasTmdb(this)
                                ? R.string.nothing_found : R.string.ai_err_no_key);
                        home.showBubble(msg, false, 6000);
                        com.cinetv.launcher.ai.EmaLightningTts.speakMoment(MainActivity.this,
                                com.cinetv.launcher.ai.EmaLightningTts.ProactiveMoment.SEARCH, msg);
                    } else {
                        home.shelfLabel.setText(getString(R.string.shelf_nothing, MovieAssistant.cleanShelfName(query)).toUpperCase(Locale.getDefault()));
                        handler.postDelayed(() -> { if (gen == searchGen) restoreShelfLabel(); }, 4000);
                        com.cinetv.launcher.ai.EmaLightningTts.speakMoment(MainActivity.this,
                                com.cinetv.launcher.ai.EmaLightningTts.ProactiveMoment.SEARCH,
                                MovieAssistant.cleanShelfName(query) + " için film bulunamadı.");
                    }
                } else {
                    String voiceSpeech = (r.voice != null && !r.voice.isEmpty()) ? r.voice
                            : ((r.title.isEmpty() ? MovieAssistant.cleanShelfName(query) : r.title) + " seçkisi vitrine getirildi.");
                    home.showBubble(voiceSpeech, false, Math.max(6500, 2500 + 65L * voiceSpeech.length()));
                    com.cinetv.launcher.ai.GamzeConversation.record(query, voiceSpeech);
                    reply(voiceSpeech, r.followUp, gen);
                }
            });
        });
    }

    /**
     * Gamze answers out loud. When she asked something back and the viewer was talking (not typing),
     * the microphone reopens by itself after her last word, like a conversation; any key press ends it.
     */
    private void reply(String text, boolean askedBack, final int gen) {
        final boolean listenAgain = askedBack && lastSearchFromVoice && conversationTurns < 3;
        com.cinetv.launcher.ai.EmaLightningTts.speakMoment(this, com.cinetv.launcher.ai.EmaLightningTts.ProactiveMoment.SEARCH, text,
                listenAgain ? () -> {
                    if (gen != searchGen || !isInFront() || home.modal.isShowing() || partyStage != null) return;
                    conversationTurns++;
                    onVoice();
                } : null);
    }

    /** When CineTV itself last started listening (the key service leaves Google's voice window open then). */
    public static volatile long ourVoiceAt;

    @Override
    public void onVoice() {
        com.cinetv.launcher.ai.EmaLightningTts.stop();
        home.keyboard.hide();
        ourVoiceAt = android.os.SystemClock.elapsedRealtime();

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_VOICE);
            startSystemVoiceFallback();
            return;
        }

        home.voice.start(new com.cinetv.launcher.ui.VoiceOverlay.Listener() {
            @Override
            public void onResult(String text) {
                onSpoken(text);
            }

            @Override
            public void onFallback() {
                startSystemVoiceFallback();
            }
        });
    }

    private void startSystemVoiceFallback() {
        try {
            Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt));
            startActivityForResult(i, REQ_VOICE);
        } catch (Throwable t) {
            home.showBubble(getString(R.string.voice_unavailable), false, 5000);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VOICE || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> said = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        if (said != null && !said.isEmpty()) handler.postDelayed(() -> onSpoken(said.get(0)), 200);
    }

    private boolean wasLaunchedForVoice = false;

    /**
     * Something said to the TV (its remote's microphone, or the phone's): a TV command ("sesi kıs", "HDMI 2",
     * "Nuvio'yu aç", "30 dakika sonra kapan") runs; anything else is a voice search ("Inception'ı aç",
     * "Nolan'ın filmlerini listele", a question).
     */
    public void onSpoken(String said) {
        com.cinetv.launcher.ai.VoiceCommands.Command c = com.cinetv.launcher.ai.VoiceCommands.parse(said);
        if (c != null && com.cinetv.launcher.ai.VoiceCommands.run(this, c, voiceHost)) {
            home.showBubble("“" + said + "”", false, 2500);
            if (wasLaunchedForVoice && (c.kind == com.cinetv.launcher.ai.VoiceCommands.Kind.KEY
                    || c.kind == com.cinetv.launcher.ai.VoiceCommands.Kind.CH_UP
                    || c.kind == com.cinetv.launcher.ai.VoiceCommands.Kind.CH_DOWN
                    || c.kind == com.cinetv.launcher.ai.VoiceCommands.Kind.CHANNEL
                    || c.kind == com.cinetv.launcher.ai.VoiceCommands.Kind.VOL_UP
                    || c.kind == com.cinetv.launcher.ai.VoiceCommands.Kind.VOL_DOWN
                    || c.kind == com.cinetv.launcher.ai.VoiceCommands.Kind.MUTE)) {
                wasLaunchedForVoice = false;
                handler.postDelayed(() -> moveTaskToBack(true), 1200);
            }
            return;
        }
        search(said, true);
    }

    private final com.cinetv.launcher.ai.VoiceCommands.Host voiceHost = new com.cinetv.launcher.ai.VoiceCommands.Host() {
        @Override
        public void showScreensaver() {
            MainActivity.this.showScreensaver();
        }

        @Override
        public void openSettings() {
            onSettings();
        }

        @Override
        public void openInputs() {
            onInputs();
        }

        @Override
        public void say(String text) {
            home.showBubble(text, false, 4000);
            com.cinetv.launcher.ai.EmaLightningTts.speak(MainActivity.this, text);
        }

        @Override
        public void applyTheme(String themeId) {
            MainActivity.this.applyTheme(themeId);
        }

        @Override
        public void setTrailerMoments(boolean on) {
            Prefs.get(MainActivity.this).edit().putBoolean(Prefs.TRAILER_MOMENTS, on).apply();
        }

        @Override
        public void setSpatial(boolean on) {
            MainActivity.this.setSpatial(on);
        }

        @Override
        public void setGlass(boolean on) {
            Prefs.get(MainActivity.this).edit().putBoolean(Prefs.GLASS_BEVEL, on).apply();
            com.cinetv.launcher.ui.GlassBevelView.setAberrationEnabled(on);
            getWindow().getDecorView().invalidate();
        }

        @Override
        public void setDvdCases(boolean on) {
            MainActivity.this.setDvdCases(on);
        }

        @Override
        public void setAmbilight(boolean on) {
            com.cinetv.launcher.device.AmbilightSync.setEnabled(MainActivity.this, on);
        }

        @Override
        public void setPerformanceMode(String mode) {
            com.cinetv.launcher.device.PerformanceProfile.setMode(MainActivity.this, mode);
        }

        @Override
        public void setDns(String dns) {
            String choice = "adguard".equals(dns) ? com.cinetv.launcher.device.NetworkOptimizer.DNS_ADGUARD
                    : ("cloudflare".equals(dns) ? com.cinetv.launcher.device.NetworkOptimizer.DNS_CLOUDFLARE : com.cinetv.launcher.device.NetworkOptimizer.DNS_OFF);
            com.cinetv.launcher.device.NetworkOptimizer.apply(MainActivity.this, choice);
        }

        @Override
        public void resetBackground() {
            MainActivity.this.resetBackground();
        }

        @Override
        public void queryRamadan(String city) {
            if (city != null) Prefs.putGlobal(MainActivity.this, Prefs.GLOBAL_CITY, city);
            com.cinetv.launcher.service.RamadanTimes.getStatusAsync(MainActivity.this, status -> {
                String msg = (status != null && !status.isEmpty())
                        ? status
                        : (city != null ? city : "Bulunduğunuz il") + " için iftar vakti: 18:15 · İmsak: 05:45";
                home.showBubble(msg, false, 6000);
                com.cinetv.launcher.ai.EmaLightningTts.speak(MainActivity.this, msg);
            });
        }
    };

    private static volatile boolean isCurating;

    @Override
    public void curate(final String mood) {
        if (isCurating) return;
        isCurating = true;
        String provider = LlmClient.providerName(this);
        home.shelfLabel.setText(getString(R.string.shelf_curating, "Gamze").toUpperCase(Locale.getDefault()));
        final String moment = MovieAssistant.momentContext(this, com.cinetv.launcher.service.WeatherService.getLastFull());
        background(() -> {
            try {
                MovieAssistant.Shelf s = MovieAssistant.curate(this, mood, moment, PerformanceProfile.shelfSize(this));
                ui(() -> {
                    try {
                        if (s != null && !s.movies.isEmpty()) setShelf(s.title, s.movies, true);
                        else if (mood == null) categoryShelf();
                        else restoreShelfLabel();
                    } finally {
                        isCurating = false;
                    }
                });
            } catch (Throwable t) {
                ui(() -> {
                    isCurating = false;
                    if (mood == null) categoryShelf();
                    else restoreShelfLabel();
                });
            }
        });
    }

    // ---- shelf ------------------------------------------------------------------------------------

    private void showCachedShelf() {
        List<Movie> cached = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.get(this).getString(SHELF_CACHE, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Movie m = new Movie();
                m.title = o.optString("title");
                m.originalTitle = o.optString("original");
                m.year = o.optInt("year");
                m.imdbId = o.optString("imdb");
                m.type = o.optString("type", "movie");
                m.posterUrl = o.optString("poster");
                m.tmdbId = o.optInt("tmdb");
                cached.add(m);
            }
        } catch (Throwable ignored) {
        }
        if (cached.isEmpty()) {
            for (String[] b : BUNDLED) {
                Movie m = new Movie();
                m.posterUrl = b[0];
                m.imdbId = b[1];
                m.title = b[2];
                m.year = Integer.parseInt(b[3]);
                cached.add(m);
            }
        }
        setShelf(Prefs.get(this).getString(SHELF_TITLE, getString(R.string.cat_trending)), cached, false);
        if (cached.size() < 14) {
            refreshShelf();
        }
    }

    private void restoreShelfLabel() {
        home.shelfLabel.setText(Prefs.get(this).getString(SHELF_TITLE, getString(R.string.cat_trending)).toUpperCase(Locale.getDefault()));
    }

    private void setShelf(String title, List<Movie> movies, boolean remember) {
        String cleanTitle = (title != null) ? title.replaceAll("\\b\\d{1,2}[:.]\\d{2}\\b\\s*", "").trim() : "";
        if (cleanTitle.isEmpty()) cleanTitle = getString(R.string.cat_trending);
        home.shelfLabel.setText(cleanTitle.toUpperCase(Locale.getDefault()));
        home.shelf.setMovies(movies);
        final Context app = getApplicationContext();
        for (int i = 0; i < Math.min(4, movies.size()); i++) {
            MovieCatalog.prefetchTrailer(app, movies.get(i));
        }
        if (!remember) return;
        JSONArray arr = new JSONArray();
        for (Movie m : movies) arr.put(m.toJson());
        Prefs.get(this).edit().putString(SHELF_CACHE, arr.toString()).putString(SHELF_TITLE, title).apply();
    }



    /** A new shelf at every power-on and whenever Home is reopened after this long. */
    private static final long SHELF_EVERY_MS = 45 * 60 * 1000L;
    /** Process-wide, so a power-on (new process) always refreshes and a quick return does not. */
    private static long lastShelfAt = -SHELF_EVERY_MS;

    /**
     * The moment's shelf: with an AI key, one picked for this hour, weekday, city and weather
     * ("Pazar gecesi için suç filmleri"); without one, or if the AI fails, a different category.
     */
    private void refreshShelf() {
        if (isCurating) return;
        lastShelfAt = android.os.SystemClock.elapsedRealtime();
        if (LlmClient.isConfigured(this)) curate(null);
        else categoryShelf();
    }

    private void categoryShelf() {
        final Context app = getApplicationContext();
        String current = Prefs.get(this).getString(SHELF_TITLE, "");
        Calendar cal = Calendar.getInstance();
        int slot = (cal.get(Calendar.DAY_OF_YEAR) * 24 + cal.get(Calendar.HOUR_OF_DAY));
        MovieCatalog.Category pick = null;
        for (int i = 0; i < MovieCatalog.CATEGORIES.length; i++) {
            MovieCatalog.Category c = MovieCatalog.CATEGORIES[(slot + i) % MovieCatalog.CATEGORIES.length];
            if (!getString(c.nameRes).equals(current)) {
                pick = c;
                break;
            }
        }
        final MovieCatalog.Category cat = pick != null ? pick : MovieCatalog.CATEGORIES[0];

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int day = cal.get(Calendar.DAY_OF_WEEK);
        int month = cal.get(Calendar.MONTH);
        String[] daysTr = {"", "Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"};
        String[] monthsTr = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
        String dayStr = (day >= 1 && day <= 7) ? daysTr[day] : "";
        String monthStr = (month >= 0 && month <= 11) ? monthsTr[month] : "";
        String timeSegment;
        if (hour >= 5 && hour < 12) timeSegment = "Sabah";
        else if (hour >= 12 && hour < 17) timeSegment = "İkindi";
        else if (hour >= 17 && hour < 22) timeSegment = "Akşam";
        else timeSegment = "Gece";
        String weather = com.cinetv.launcher.service.WeatherService.getLastCondition();
        final String richTitle = String.format(Locale.getDefault(), "%s %s · %s %s",
                dayStr, timeSegment, (weather.isEmpty() ? monthStr : weather), getString(cat.nameRes)).toUpperCase(Locale.getDefault());

        background(() -> {
            List<Movie> list = MovieCatalog.category(app, cat, PerformanceProfile.shelfSize(app));
            if (!list.isEmpty()) ui(() -> setShelf(richTitle, list, true));
        });
    }

    // ---- opening a film ---------------------------------------------------------------------------

    public boolean handlePartyAppLaunch(String pkg) {
        if (pkg == null) return false;
        if (AppCatalog.PKG_PARTY_FOLDER.equals(pkg)) {
            showPartyGamesFolderModal();
            return true;
        }
        if (AppCatalog.PKG_SNAKE.equals(pkg)) {
            showLiveSnakePhileScene();
            return true;
        }
        if (AppCatalog.PKG_DUEL.equals(pkg)) {
            showLiveCineDuelScene();
            return true;
        }
        if (AppCatalog.PKG_QUIZ.equals(pkg)) {
            showParty(com.cinetv.launcher.party.CinePartyManager.Mode.QUIZ);
            return true;
        }
        if (AppCatalog.PKG_ROULETTE.equals(pkg)) {
            showParty(com.cinetv.launcher.party.CinePartyManager.Mode.ROULETTE);
            return true;
        }
        if (AppCatalog.PKG_SESSIZ.equals(pkg)) {
            showParty(com.cinetv.launcher.party.CinePartyManager.Mode.SESSIZ);
            return true;
        }
        return false;
    }

    public void showPartyGamesFolderModal() {
        if (home == null || home.modal == null) return;
        home.modal.push(() -> Panels.buildPartyGamesFolderModal(this, home.modal, new Panels.PartyLauncher() {
            @Override
            public void launchSnake() {
                home.modal.closeImmediate();
                showLiveSnakePhileScene();
            }

            @Override
            public void launchDuel() {
                home.modal.closeImmediate();
                showLiveCineDuelScene();
            }

            @Override
            public void launchQuiz() {
                home.modal.closeImmediate();
                showParty(com.cinetv.launcher.party.CinePartyManager.Mode.QUIZ);
            }

            @Override
            public void launchRoulette() {
                home.modal.closeImmediate();
                showParty(com.cinetv.launcher.party.CinePartyManager.Mode.ROULETTE);
            }

            @Override
            public void launchSessiz() {
                home.modal.closeImmediate();
                showParty(com.cinetv.launcher.party.CinePartyManager.Mode.SESSIZ);
            }
        }));
    }

    public void showPartyHowToPlay(String pkg) {
        if (home == null || home.modal == null) return;
        if (AppCatalog.PKG_SNAKE.equals(pkg) || AppCatalog.PKG_DUEL.equals(pkg)) {
            home.modal.push(() -> Panels.buildSnakePhileHowToPlayModal(this, home.modal));
        } else if (AppCatalog.PKG_QUIZ.equals(pkg)) {
            home.modal.push(() -> Panels.buildCineQuizHowToPlayModal(this, home.modal));
        } else if (AppCatalog.PKG_ROULETTE.equals(pkg)) {
            home.modal.push(() -> Panels.buildCineRouletteHowToPlayModal(this, home.modal));
        } else if (AppCatalog.PKG_SESSIZ.equals(pkg)) {
            home.modal.push(() -> Panels.buildCineSessizHowToPlayModal(this, home.modal));
        } else {
            showPartyGamesFolderModal();
        }
    }

    private static android.os.Handler frameHandler;

    private static synchronized android.os.Handler frameMetricsHandler() {
        if (frameHandler == null) {
            android.os.HandlerThread t = new android.os.HandlerThread("cinetv-frames", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            t.start();
            frameHandler = new android.os.Handler(t.getLooper());
        }
        return frameHandler;
    }

    // ---- CineParty -------------------------------------------------------------------------------

    private com.cinetv.launcher.party.PartyStageView partyStage;

    /** The room asks for a film (quiz champion, roulette bullet): close the stage and play it. */
    private final com.cinetv.launcher.party.CinePartyManager.Listener partyLauncher = new com.cinetv.launcher.party.CinePartyManager.Listener() {
        @Override
        public void onMovieLaunchRequested(String movieTitle) {
            handler.post(() -> {
                if (home != null && home.modal != null && home.modal.isShowing()) home.modal.closeImmediate();
                hidePartyStage();
                hideLiveCineDuelScene();
                hideLiveSnakePhileScene();
                launchFilmByTitle(movieTitle);
            });
        }
    };

    /** Opens a CineParty game full screen on the TV; the phones join it from the party tab. */
    public void showParty(com.cinetv.launcher.party.CinePartyManager.Mode mode) {
        if (home == null) return;
        if (home.modal != null && home.modal.isShowing()) home.modal.closeImmediate();
        hideLiveCineSubScene();
        hideLiveCineDuelScene();
        hideLiveSnakePhileScene();
        com.cinetv.launcher.party.CinePartyManager party = com.cinetv.launcher.party.CinePartyManager.get(this);
        if (party.mode() != mode) party.open(mode);
        if (partyStage == null) {
            partyStage = new com.cinetv.launcher.party.PartyStageView(this);
            partyStage.setHost(new com.cinetv.launcher.party.PartyStageView.Host() {
                @Override
                public void exit() {
                    hidePartyStage();
                }

                @Override
                public void play(String title) {
                    hidePartyStage();
                    launchFilmByTitle(title);
                }
            });
            // Under the modal layer, so a pairing request or a toast-like dialog still shows on top of the game.
            home.root.addView(partyStage, home.root.indexOfChild(home.modal),
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            home.environment.pause();
        }
        partyStage.requestFocus();
    }

    public void hidePartyStage() {
        if (partyStage == null) return;
        home.root.removeView(partyStage);
        partyStage = null;
        home.environment.resume();
        if (home.shelf != null) home.shelf.requestFocus();
    }

    public void launchFilm(final Movie m) {
        if (m == null) return;
        if (com.cinetv.launcher.service.LetterboxdSync.shouldPromptBeforeWatch(this)) {
            promptLetterboxdWatch(m);
        } else {
            doLaunchFilm(m);
        }
    }

    public void doLaunchFilm(final Movie m) {
        if (m == null) return;
        boolean ok = AppLauncher.openInNuvio(this, m.title, m.imdbId, m.type, m.posterUrl);
        if (!ok) {
            showNuvioNotFoundDialog(m);
        } else {
            com.cinetv.launcher.device.SystemAudio.launch(this);
            com.cinetv.launcher.service.NowPlaying.set(this, m);
            com.cinetv.launcher.service.LetterboxdSync.recordWatch(this, m);
        }
    }

    public void promptLetterboxdWatch(final Movie m) {
        if (home == null || home.modal == null) {
            doLaunchFilm(m);
            return;
        }
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final String user = com.cinetv.launcher.service.LetterboxdSync.getUsername(this);
        home.modal.push(() -> {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UiKit.px(36), UiKit.px(26), UiKit.px(36), UiKit.px(26));
            root.setBackground(Glass.panel(root, 24));
            root.setFocusable(true);

            TextView title = UiKit.bold(this, 26, UiKit.INK);
            title.setText(tr ? "Letterboxd Sinema Günlüğü" : "Letterboxd Cinema Diary");
            root.addView(title);

            TextView desc = UiKit.label(this, 16, UiKit.INK_DIM);
            desc.setPadding(0, UiKit.px(10), 0, UiKit.px(22));
            desc.setLineSpacing(UiKit.pxf(5), 1.15f);
            desc.setMaxWidth(UiKit.px(720));
            desc.setText(tr
                    ? ("'" + m.title + "' filmini izliyorsunuz. Bu izleme @" + user + " Letterboxd hesabınıza eklensin mi?")
                    : ("You are watching '" + m.title + "'. Would you like to log this watch to @" + user + "?"));
            root.addView(desc);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);

            TextView btnYes = Controls.button(this, tr ? "Evet, Bu Sefer Ekle" : "Yes, Log Watch", v -> {
                home.modal.closeImmediate();
                doLaunchFilm(m);
            });
            btns.addView(btnYes);

            View sp1 = new View(this);
            btns.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(14), 1));

            TextView btnAlways = Controls.button(this, tr ? "Her Zaman Otomatik Ekle" : "Always Auto-Log", v -> {
                com.cinetv.launcher.service.LetterboxdSync.setAutoRecord(this, true);
                home.modal.closeImmediate();
                doLaunchFilm(m);
            });
            btns.addView(btnAlways);

            View sp2 = new View(this);
            btns.addView(sp2, new LinearLayout.LayoutParams(UiKit.px(14), 1));

            TextView btnNo = Controls.button(this, tr ? "Bu Sefer Ekleme / Hayır" : "Don't Log / Launch", v -> {
                home.modal.closeImmediate();
                boolean ok = AppLauncher.openInNuvio(this, m.title, m.imdbId, m.type, m.posterUrl);
                if (!ok) showNuvioNotFoundDialog(m);
            });
            btns.addView(btnNo);

            root.addView(btns);
            root.post(btnYes::requestFocus);
            return root;
        });
    }

    public void showUpdateAvailableModal(final com.cinetv.launcher.service.AppUpdater.ReleaseInfo info) {
        if (home == null || home.modal == null || info == null) return;
        home.modal.push(() -> Panels.buildUpdateModal(this, home.modal, info));
    }

    public void launchFilmByTitle(String title) {
        if (title == null || title.trim().isEmpty()) return;
        final String clean = title.trim();
        background(() -> {
            Movie match = null;
            try {
                List<Movie> all = MovieCatalog.category(this, MovieCatalog.CATEGORIES[0], 100);
                for (Movie m : all) {
                    if (clean.equalsIgnoreCase(m.title) || (m.originalTitle != null && clean.equalsIgnoreCase(m.originalTitle))) {
                        match = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {}
            final Movie finalMatch = match;
            ui(() -> {
                if (home != null && home.modal != null && home.modal.isShowing()) home.modal.closeImmediate();
                hideLiveCineDuelScene();
                hideLiveSnakePhileScene();
                if (finalMatch != null) {
                    launchFilm(finalMatch);
                } else {
                    onSearch(clean);
                }
            });
        });
    }

    public void showNuvioNotFoundDialog(final Movie m) {
        if (home == null || home.modal == null) return;
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        home.modal.push(() -> {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UiKit.px(36), UiKit.px(28), UiKit.px(36), UiKit.px(28));
            root.setBackground(Glass.panel(root, 24));
            root.setFocusable(true);

            TextView title = UiKit.bold(this, 26, UiKit.INK);
            title.setText(tr ? "Nuvio veya Uyumlu Oynatıcı Bulunamadı" : "Nuvio Player Not Found");
            root.addView(title);

            TextView desc = UiKit.label(this, 16, UiKit.INK_DIM);
            desc.setPadding(0, UiKit.px(12), 0, UiKit.px(24));
            desc.setLineSpacing(UiKit.pxf(6), 1.15f);
            desc.setMaxWidth(UiKit.px(740));
            desc.setText(tr
                    ? "CineTV'nin filmleri ve dizileri 4K/1080p kalitesinde açabilmesi için televizyonunuzda Nuvio veya Stremio oynatıcısının kurulu olması gerekir.\n\n"
                    + "Aşağıdaki seçeneklerden biriyle Nuvio'yu hemen kurabilir veya cihazınızdaki başka bir oynatıcıyı deneyebilirsiniz."
                    : "To play movies and series in 4K/1080p, CineTV requires Nuvio or Stremio installed on your TV.\n\n"
                    + "You can install Nuvio now or attempt opening with another installed media player.");
            root.addView(desc);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);

            TextView installBtn = Controls.button(this, tr ? "Nuvio Kurulum Rehberi (QR Kod)" : "Install Nuvio (QR Guide)", v -> {
                home.modal.replace(() -> Panels.buildNuvioInstallGuideModal(this, home.modal));
            });
            btns.addView(installBtn);

            View sp1 = new View(this);
            btns.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(16), 1));

            TextView genericBtn = Controls.button(this, tr ? "Harici Oynatıcı ile Dene" : "Try External Player", v -> {
                home.modal.back();
                try {
                    Intent generic = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.imdb.com/title/" + m.imdbId));
                    generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(generic);
                } catch (Throwable ignored) {
                    Toast.makeText(this, tr ? "Uygun harici oynatıcı bulunamadı." : "No external player found.", Toast.LENGTH_SHORT).show();
                }
            });
            btns.addView(genericBtn);

            View sp2 = new View(this);
            btns.addView(sp2, new LinearLayout.LayoutParams(UiKit.px(16), 1));

            TextView closeBtn = Controls.button(this, tr ? "Kapat" : "Close", v -> home.modal.back());
            btns.addView(closeBtn);

            root.addView(btns);
            root.post(installBtn::requestFocus);
            return root;
        });
    }

    /** Plays the DVD opening (when enabled and the TV can afford it), then opens the title in Nuvio. */
    public void openTitle(final Movie m, DvdCaseView from) {
        com.cinetv.launcher.ai.EmaLightningTts.stop();
        final Context appCtx = getApplicationContext();
        final Runnable launch = () -> {
            if (com.cinetv.launcher.service.Creator.isSessiz(m)) {
                AppLauncher.openYouTubeVideo(this, com.cinetv.launcher.service.Creator.SESSIZ_YOUTUBE_URL);
                return;
            }
            if (!m.imdbId.isEmpty()) {
                launchFilm(m);
                return;
            }
            // Search results carry only TMDB ids: look the IMDb id up now.
            background(() -> {
                MovieCatalog.ensureImdb(appCtx, m);
                if (m.imdbId.isEmpty() && m.title != null && !m.title.isEmpty()) {
                    Movie resolved = MovieCatalog.resolve(appCtx, m.title, m.year);
                    if (resolved != null && !resolved.imdbId.isEmpty()) m.imdbId = resolved.imdbId;
                }
                ui(() -> launchFilm(m));
            });
        };
        // trailer moments play inside the opened case, so they open it even with the animation off
        boolean animate = Prefs.dvdAnimation(this) || Prefs.trailerMoments(this);
        if (!animate) {
            launch.run();
            return;
        }
        final DvdOpeningView.Callback cb = new DvdOpeningView.Callback() {
            @Override
            public void onLaunch() {
                launch.run();
            }

            @Override
            public void onCancelled() {
                home.shelf.requestFocus();
            }
        };
        if (from != null && from.dvdCase() != null) {
            int[] loc = new int[2];
            from.getLocationOnScreen(loc);
            float cx = loc[0] + from.getWidth() / 2f, cy = loc[1] + from.getHeight() / 2f;
            float w = from.caseWidth() * 1.52f, h = from.caseHeight() * 1.52f;
            com.cinetv.launcher.device.AmbilightSync.updateColor(this, from.dvdCase().accent);
            home.opening.play(from.dvdCase(), m, new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), 0, cb);
            writeInnerNote(m, from.dvdCase());
            return;
        }
        // Opened from the phone or search: bake a case from the poster first.
        final Context app = getApplicationContext();
        background(() -> {
            int cw = UiKit.px(236), ch = Math.round(cw / DvdCase.ASPECT);
            Bitmap poster = PosterCache.load(app, m.posterUrl, cw, ch);
            if (poster == null) {
                ui(launch);
                return;
            }
            DvdCase dvd = DvdCase.bake(app, poster, m.coverTitle(), m.year, AppArtwork.sampleDistinctPosterColor(poster, 0, 0), cw, ch, 0);
            ui(() -> {
                float cx = home.root.getWidth() / 2f, cy = home.root.getHeight() / 2f;
                home.opening.play(dvd, m, new RectF(cx - cw / 2f, cy + ch * 0.4f, cx + cw / 2f, cy + ch * 1.4f), 3200, cb);
                writeInnerNote(m, dvd);
            });
        });
    }

    private Runnable trailerPrefetchRunnable;

    private void prefetchMovie(final Movie m) {
        if (m == null) return;
        final Context app = getApplicationContext();
        final int w = home.opening.innerWidth(), h = home.opening.innerHeight();
        MovieCatalog.prefetchTrailer(app, m);
        if (home != null && home.trailer != null && Prefs.trailerMoments(app)) {
            if (trailerPrefetchRunnable != null) handler.removeCallbacks(trailerPrefetchRunnable);
            trailerPrefetchRunnable = () -> {
                if (home != null && home.trailer != null) {
                    home.trailer.prepare(m);
                }
            };
            handler.postDelayed(trailerPrefetchRunnable, 320L);
        }
        background(() -> {
            try {
                if (m.cast == null || m.cast.isEmpty()) {
                    MovieCatalog.details(app, m);
                }
                // Portraits too, even when the details came earlier and a photo didn't make it in time.
                MovieCatalog.ensureCastPhotos(app, m, 4000);
                if (m.backdropUrl != null && !m.backdropUrl.isEmpty()) {
                    PosterCache.load(app, m.backdropUrl, w * 2, h);
                }
                // Also prefetch adjacent shelf neighbors so booklet & photos open instantaneously
                int idx = home.shelf.focusedIndex();
                List<Movie> list = home.shelf.movies();
                if (list != null && !list.isEmpty()) {
                    if (idx + 1 < list.size()) {
                        Movie next = list.get(idx + 1);
                        if (next != null && (next.cast == null || next.cast.isEmpty())) {
                            MovieCatalog.details(app, next);
                        }
                        MovieCatalog.ensureCastPhotos(app, next, 3000);
                    }
                    if (idx - 1 >= 0) {
                        Movie prev = list.get(idx - 1);
                        if (prev != null && (prev.cast == null || prev.cast.isEmpty())) {
                            MovieCatalog.details(app, prev);
                        }
                        MovieCatalog.ensureCastPhotos(app, prev, 3000);
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /** Fetches the synopsis and a still, then writes the note inside the open case. */
    private void writeInnerNote(final Movie m, final DvdCase dvd) {
        final Context app = getApplicationContext();
        final int w = home.opening.innerWidth(), h = home.opening.innerHeight();
        background(() -> {
            try {
                boolean needsDetails = (m.cast == null || m.cast.isEmpty() || m.overview == null || m.overview.isEmpty());
                if (needsDetails) {
                    MovieCatalog.details(app, m);
                }
                Bitmap still = null;
                if (m.backdropUrl != null && !m.backdropUrl.isEmpty()) {
                    still = PosterCache.getFast(m.backdropUrl);
                    if (still == null) still = PosterCache.load(app, m.backdropUrl, w * 2, h);
                }
                boolean allPhotos = MovieCatalog.ensureCastPhotos(app, m, 2500);
                final Bitmap inner = DvdOpeningView.bakeInner(app, w, h, dvd.cover, still, m, dvd.accent);
                ui(() -> home.opening.setInner(inner));
                // A slow portrait still lands in the booklet: write the note again once it's here.
                if (!allPhotos && MovieCatalog.ensureCastPhotos(app, m, 10_000)) {
                    final Bitmap again = DvdOpeningView.bakeInner(app, w, h, dvd.cover, still, m, dvd.accent);
                    ui(() -> {
                        if (home.opening.isShowing(m)) home.opening.setInner(again);
                    });
                }
            } catch (Throwable t) {
                android.util.Log.w("CineTv", "writeInnerNote failed: " + t.getMessage());
            }
        });
    }

    // ---- apps -------------------------------------------------------------------------------------

    private void loadApps(boolean force) {
        final Context app = getApplicationContext();
        background(() -> {
            Set<String> now = AppCatalog.launchablePackages(app);
            if (!force && now.equals(knownPackages)) return;
            AppCatalog.Snapshot snap = AppCatalog.load(app);
            ui(() -> {
                knownPackages = now;
                // one layout in every theme: five apps a row (more only when there are many apps)
                home.apps.setColumns(5);
                home.apps.setApps(snap.dock, snap.grid);
            });
        });
    }

    @Override
    public void onTileFocused(View tile, int color) {
        home.environment.focusOn(tile, color);
    }

    @Override
    public void onLaunch(AppTileItem item) {
        if (item != null && handlePartyAppLaunch(item.packageName)) return;
        MemoryOptimizer.kill(this, "com.android.vending");
        if (!AppLauncher.launch(this, item.packageName)) toast(R.string.app_not_found);
    }

    @Override
    public void onEdgeUp() {
        home.shelf.requestFocus();
    }

    /** Long-press: Move / Hide / Uninstall / Info as a glass bar attached to the tile itself. */
    @Override
    public void showActions(final AppTileItem item, final boolean inDock, final int index, View tile) {
        if (item != null && item.packageName != null && item.packageName.startsWith("com.cinetv.party.")) {
            final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
            ModalHost.Builder bar = () -> {
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                if (tile == null) {
                    android.widget.TextView name = UiKit.bold(this, 26, UiKit.INK);
                    name.setText(AppArtwork.getCleanLabel(item.packageName, item.label));
                    name.setPadding(UiKit.px(8), 0, UiKit.px(20), 0);
                    row.addView(name);
                }
                row.addView(spaced(Controls.button(this, tr ? "▶ Oyunu Başlat" : "▶ Launch Game", v -> {
                    home.modal.close();
                    handlePartyAppLaunch(item.packageName);
                })));
                if (index >= 0) {
                    row.addView(spaced(Controls.button(this, getString(R.string.move), v -> {
                        home.modal.close();
                        home.moveHint.setVisibility(View.VISIBLE);
                        home.apps.startMove(item, inDock, index);
                    })));
                }
                row.addView(spaced(Controls.button(this, tr ? "ℹ Nasıl Oynanır?" : "ℹ Rules", v -> {
                    home.modal.close();
                    showPartyHowToPlay(item.packageName);
                })));
                row.addView(spaced(Controls.button(this, getString(R.string.hide), v -> {
                    home.modal.close();
                    AppCatalog.hide(this, item.packageName);
                    loadApps(true);
                })));
                return row;
            };
            if (tile != null) home.modal.pushAnchored(bar, tile);
            else home.modal.push(bar);
            return;
        }
        ModalHost.Builder bar = () -> {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            if (tile == null) {
                android.widget.TextView name = UiKit.bold(this, 26, UiKit.INK);
                name.setText(AppArtwork.getCleanLabel(item.packageName, item.label));
                name.setPadding(UiKit.px(8), 0, UiKit.px(20), 0);
                row.addView(name);
            }
            if (index >= 0) {
                row.addView(spaced(Controls.button(this, getString(R.string.move), v -> {
                    home.modal.close();
                    home.moveHint.setVisibility(View.VISIBLE);
                    home.apps.startMove(item, inDock, index);
                })));
            } else {
                final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
                row.addView(spaced(Controls.button(this, tr ? "Ana Ekrana Ekle" : "Add to Home", v -> {
                    home.modal.close();
                    AppCatalog.addToHome(this, item.packageName);
                    loadApps(true);
                    Toast.makeText(MainActivity.this, tr ? "Ana ekrana eklendi" : "Added to home screen", Toast.LENGTH_SHORT).show();
                })));
            }
            row.addView(spaced(Controls.button(this, getString(R.string.hide), v -> {
                home.modal.close();
                AppCatalog.hide(this, item.packageName);
                loadApps(true);
            })));
            row.addView(spaced(Controls.button(this, getString(R.string.uninstall), v -> {
                home.modal.close();
                AppLauncher.uninstall(this, item.packageName);
            })));
            row.addView(spaced(Controls.button(this, getString(R.string.app_info), v -> {
                home.modal.close();
                AppLauncher.openAppInfo(this, item.packageName);
            })));
            return row;
        };
        if (tile != null) home.modal.pushAnchored(bar, tile);
        else home.modal.push(bar);
    }

    private View spaced(View v) {
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.px(8), 0, UiKit.px(8), 0);
        v.setLayoutParams(lp);
        return v;
    }

    /** "Add" sits after the last app: it offers every installed app that is not on Home yet. */
    @Override
    public void onAddToDock() {
        final Context app = getApplicationContext();
        final Set<String> onHome = new java.util.HashSet<>();
        for (AppTileItem i : home.apps.dockItems()) onHome.add(i.packageName);
        for (AppTileItem i : home.apps.gridItems()) onHome.add(i.packageName);
        background(() -> {
            List<AppCatalog.Entry> apps = AppCatalog.addable(app, onHome);
            ui(() -> AllApps.show(this, home.modal, getString(R.string.add_to_home), apps, getString(R.string.add_nothing),
                    new AllApps.Listener() {
                        @Override
                        public void open(String pkg) {
                            AppCatalog.addToHome(app, pkg);
                            loadApps(true);
                        }

                        @Override
                        public void actions(String pkg, String label) {
                        }
                    }));
        });
    }

    @Override
    public void onOrderChanged() {
        home.moveHint.setVisibility(View.GONE);
        toast(R.string.order_saved);
    }

    // ---- settings actions -------------------------------------------------------------------------

    @Override
    public void applyTheme(String id) {
        applyTheme(id, true);
    }

    private void applyTheme(String id, boolean persist) {
        if (persist) ThemeManager.setTheme(this, id);
        ThemeManager.Theme t = ThemeManager.byId(id);
        home.environment.apply(t, ThemeManager.isLight(this));
        home.shelf.setTheme(t.id, ThemeManager.usesCustomWallpaper(this) ? null : t.furniture);
        home.applyTheme(t);
        // every glass panel and button takes the new room's material at once
        redraw(getWindow().getDecorView());
        String wp = ThemeManager.wallpaperNow(t);
        String caption = ThemeManager.caption(this, wp);
        home.caption.setText(caption == null ? "" : caption);
        if (persist) loadApps(true);
    }

    private static void redraw(View v) {
        v.invalidate();
        if (v.getBackground() != null) v.getBackground().invalidateSelf();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) redraw(g.getChildAt(i));
        }
    }

    @Override
    public void applyAppearance() {
        DeviceTweaks.syncNightMode(this);
        UiKit.init(this);
        // Light and dark use different ink everywhere: rebuild the screen when it flips.
        if (ThemeManager.isLight(this) != UiKit.isLight()) {
            recreate();
            return;
        }
        home.environment.apply(ThemeManager.current(this), ThemeManager.isLight(this));
    }

    @Override
    public void setSpatial(boolean on) {
        home.applySpatial(on);
        loadApps(true);
    }

    @Override
    public void setDvdCases(boolean on) {
        home.shelf.setCaseMode(on);
    }

    /** Once the first tuning is done and its dialog closed, the tour starts (once). */
    private final Runnable tourCheck = new Runnable() {
        @Override
        public void run() {
            if (home == null || Prefs.get(MainActivity.this).getBoolean(Prefs.TOUR_DONE, false)) return;
            boolean ready = Prefs.get(MainActivity.this).getBoolean("wizard_done", false)
                    && !home.modal.isShowing() && !home.opening.isPlaying() && inFront
                    && home.screensaver.getVisibility() != View.VISIBLE && home.shelf.count() > 0;
            if (ready) startTour();
            else handler.postDelayed(this, 2500);
        }
    };

    private boolean wizardTransitionTriggered;

    public void onWizardFinished() {
        if (wizardTransitionTriggered) return;
        if (home != null && home.modal != null) home.modal.setOnClosed(null);
        if (Prefs.get(this).getBoolean(Prefs.TOUR_DONE, false)) return;
        wizardTransitionTriggered = true;
        Prefs.get(this).edit().putBoolean("wizard_done", true).putBoolean(Prefs.FIRST_SETUP_DONE, true).apply();
        handler.postDelayed(() -> {
            if (isFinishing() || home == null) return;
            boolean turkish = "tr".equals(java.util.Locale.getDefault().getLanguage());
            final String welcomeMsg = turkish
                    ? "Kusursuz sinema deneyimine adım atacağın CineTV by Alptuğ Dağ'a hoş geldin!"
                    : "Welcome to CineTV by Alptuğ Dağ, your ultimate cinema experience!";
            showCinematicWelcome(welcomeMsg, this::startTour);
        }, 350);
    }

    public void showCinematicWelcome(final String message, final Runnable onFinished) {
        if (home == null || home.root == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        final android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setBackgroundColor(android.graphics.Color.argb(230, 8, 9, 13));
        container.setAlpha(0f);
        container.setFocusable(true);
        container.setClickable(true);

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(UiKit.px(48), UiKit.px(36), UiKit.px(48), UiKit.px(36));

        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageBitmap(com.cinetv.launcher.ui.Brand.mark(this));
        int logoSize = UiKit.px(84);
        android.widget.LinearLayout.LayoutParams mlp = new android.widget.LinearLayout.LayoutParams(logoSize, logoSize);
        mlp.bottomMargin = UiKit.px(24);
        mlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        box.addView(mark, mlp);

        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(message);
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setLineSpacing(UiKit.pxf(6), 1.15f);
        tv.setTypeface(FontManager.getMedium(this));
        tv.setShadowLayer(UiKit.pxf(14), 0, UiKit.pxf(4), android.graphics.Color.argb(180, 0, 0, 0));
        box.addView(tv, new android.widget.LinearLayout.LayoutParams(UiKit.px(840), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER);
        container.addView(box, flp);
        home.root.addView(container, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable finishAction = () -> {
            home.root.removeView(container);
            if (onFinished != null) onFinished.run();
        };

        container.animate().alpha(1f).setDuration(500).withEndAction(() -> {
            container.postDelayed(() -> {
                container.animate().alpha(0f).setDuration(600).withEndAction(finishAction).start();
            }, 2600);
        }).start();

        container.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                container.animate().cancel();
                finishAction.run();
                return true;
            }
            return false;
        });
        container.requestFocus();
    }

    private void checkNuvioUpdatePrompt() {
        if (isFinishing() || home == null || home.modal == null || home.modal.isShowing()) return;
        boolean nuvioInstalled = AppLauncher.isLaunchable(getPackageManager(), AppLauncher.NUVIO);
        if (nuvioInstalled) {
            // Existing Nuvio found: mark configured and preserve user addons without interruption
            Prefs.get(this).edit()
                    .putBoolean("nuvio_addons_configured", true)
                    .putBoolean("nuvio_addons_dismissed", true)
                    .putBoolean("nuvio_update_prompt_v1_0_0_9b", true)
                    .putBoolean("nuvio_setup_done", true)
                    .apply();
            return;
        }
    }

    private void checkSpecialDayCelebration() {
        final com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.today(this);
        if (day == null || isFinishing()) return;
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        String prefKey = "celebration_shown_" + day.id + "_" + year;
        boolean alreadyShown = Prefs.get(this).getBoolean(prefKey, false);
        if (!alreadyShown && home != null && !home.modal.isShowing() && home.screensaver.getVisibility() != View.VISIBLE) {
            Prefs.get(this).edit().putBoolean(prefKey, true).apply();
            showCinematicCelebration(day);
        }
    }

    public void showCinematicCelebration(final com.cinetv.launcher.service.SpecialDay day) {
        if (day == null) return;
        if (com.cinetv.launcher.TvKeyInterceptorService.getInstance() != null) {
            com.cinetv.launcher.ui.CelebrationOverlay.show(this, day);
            return;
        }
        if (home == null || home.root == null) return;
        final android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setBackgroundColor(Color.argb(188, 4, 5, 9));
        container.setAlpha(0f);
        container.setFocusable(true);
        container.setClickable(true);

        // Floating 3D teardrop celebratory balloons (or solemn memorial embers on 10 Kasım)
        container.addView(new com.cinetv.launcher.ui.FloatingBalloonsView(this, day.mourning),
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        View card = com.cinetv.launcher.ui.CelebrationOverlay.buildCard(this, day);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        container.addView(card, flp);
        home.root.addView(container, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable finishAction = () -> {
            try {
                home.root.removeView(container);
            } catch (Throwable ignored) {}
        };

        container.animate().alpha(1f).setDuration(420).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(460)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        container.postDelayed(() -> {
            if (container.getParent() != null) {
                container.animate().cancel();
                container.animate().alpha(0f).setDuration(240).withEndAction(finishAction).start();
            }
        }, 9000);

        container.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                container.animate().cancel();
                container.animate().alpha(0f).setDuration(240).withEndAction(finishAction).start();
                return true;
            }
            return false;
        });
        container.setOnClickListener(v -> {
            container.animate().cancel();
            container.animate().alpha(0f).setDuration(240).withEndAction(finishAction).start();
        });
        container.requestFocus();
    }

    @Override
    public void startTour() {
        home.modal.close();
        home.shelf.requestFocus();
        home.tour.setListener((index, step) -> {
            if (index == 2) {
                // Step 3 (0-indexed 2: "Bir DVD aç"): Automatically open focused DVD case!
                final DvdCaseView v = home.shelf.focusedCase();
                final Movie m = home.shelf.focusedMovie();
                if (v != null && m != null && !home.opening.isPlaying()) {
                    openTitle(m, v);
                }
            } else {
                // Step 4 ("Fragman anları") and other steps: close opened DVD case
                if (home.opening.isPlaying()) {
                    home.opening.dismiss();
                }
            }
        });
        home.shelf.post(() -> home.tour.start(home.tourSteps(),
                () -> {
                    home.tour.setListener(null);
                    if (home.opening.isPlaying()) home.opening.dismiss();
                    Prefs.setFlag(this, Prefs.TOUR_DONE, true);
                }));
    }

    @Override
    public void setTrailerMoments(boolean on) {
        home.setTrailerMode(on);
    }

    @Override
    public void onPerformanceChanged() {
        // in place: rebuilding the activity is what let the TV's old launcher step in on some TVs
        handler.postDelayed(() -> {
            home.environment.apply(ThemeManager.current(this), ThemeManager.isLight(this));
            loadApps(true);
        }, 300);
    }

    @Override
    public void previewScreensaver() {
        showScreensaver();
    }

    @Override
    public void onKeysChanged() {
    }

    @Override
    public void search(String query) {
        onSearch(query);
    }

    @Override
    public void onAppsChanged() {
        loadApps(true);
    }

    @Override
    public void reboot() {
        toast(R.string.rebooting);
        handler.postDelayed(() -> DeviceTweaks.reboot(this), 800);
    }

    // ---- phone remote -----------------------------------------------------------------------------

    @Override
    public void searchOnTv(String query) {
        onSearch(query);
    }

    public void openSimilarForFocused() {
        if (home == null || home.shelf == null) return;
        final MovieCatalog.Movie m = home.shelf.focusedMovie();
        if (m == null) {
            Toast.makeText(this, "Önce bir film seçin", Toast.LENGTH_SHORT).show();
            return;
        }
        home.showBubble(m.title + " benzeri filmler aranıyor...", false, 3000);
        new Thread(() -> {
            List<MovieCatalog.Movie> sim = MovieCatalog.similar(MainActivity.this, m.tmdbId, m.type, m.title, 24);
            runOnUiThread(() -> {
                if (sim != null && !sim.isEmpty()) {
                    showSimilarOnTv((m.title + " BENZERİ FİLMLER").toUpperCase(Locale.getDefault()), sim);
                } else {
                    home.showBubble("Benzer film bulunamadı", false, 3000);
                }
            });
        }, "cinetv-similar-btn").start();
    }

    @Override
    public void showSimilarOnTv(String title, List<MovieCatalog.Movie> movies) {
        if (movies != null && !movies.isEmpty()) {
            setShelf(title == null || title.isEmpty() ? "BENZER FİLMLER" : title, movies, true);
            home.shelf.requestFocus();
        }
    }

    @Override
    public void onWatchlistSuggested(String movieTitle, String userLabel) {
        String msg = (userLabel != null && !userLabel.isEmpty())
                ? userLabel + ", '" + movieTitle + "' filmini ortak listeye önerdi!"
                : "Ortak listeye yeni film önerildi: " + movieTitle;
        if (home != null) home.showBubble(msg, false, 5000);
        com.cinetv.launcher.ai.EmaLightningTts.speak(this, msg);
    }

    @Override
    public void voiceOnTv(String said) {
        if (!inFront) AppLauncher.start(this, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        onSpoken(said);
    }

    @Override
    public void showScreensaver() {
        if (home.screensaver.getVisibility() != View.VISIBLE) showScreensaverInternal();
    }

    private View liveCineSubView;

    public void showLiveCineSubScene() {
        if (home != null && home.modal != null && home.modal.isShowing()) home.modal.close();
        hideLiveSnakePhileScene();
        if (liveCineSubView != null) {
            home.root.removeView(liveCineSubView);
            liveCineSubView = null;
        }

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.BLACK);
        container.setFocusable(true);
        container.setClickable(true);

        try {
            java.io.InputStream in = getAssets().open("brand/sessiz_frame.jpg");
            Bitmap frame = BitmapFactory.decodeStream(in);
            in.close();

            if (frame != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(frame);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
                container.addView(iv, ivLp);
            }
        } catch (Throwable ignored) {}

        // Subtitle bar at bottom center matching the exact film dialogue
        FrameLayout subBar = new FrameLayout(this);
        subBar.setBackground(Glass.panel(subBar, 14));
        subBar.setPadding(UiKit.px(28), UiKit.px(10), UiKit.px(28), UiKit.px(10));
        TextView subText = UiKit.text(this, 22, Color.parseColor("#FEF08A"), FontManager.getMedium(this));
        subText.setText("“Cellatları dilsizlerden seçerlermiş derler... ama ya ben?”");
        subBar.addView(subText);

        FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sLp.bottomMargin = UiKit.px(54);
        container.addView(subBar, sLp);

        liveCineSubView = container;
        home.root.addView(liveCineSubView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The real thing on a real frame: CineSUB looks at this still exactly as it would at a film.
        com.cinetv.launcher.service.NowPlaying.set(this, "Sessiz", 2023);
        handler.postDelayed(() -> com.cinetv.launcher.ui.CineSubOverlay.trigger(this), 600);

        liveCineSubView.setOnClickListener(v -> hideLiveCineSubScene());
    }

    public void hideLiveCineSubScene() {
        if (liveCineSubView != null) {
            home.root.removeView(liveCineSubView);
            liveCineSubView = null;
            com.cinetv.launcher.ui.CineSubOverlay.dismissActive();
        }
    }

    private com.cinetv.launcher.party.CineDuelView liveCineDuelView;

    public com.cinetv.launcher.party.CineDuelView getLiveCineDuelView() {
        return liveCineDuelView;
    }

    public void showLiveCineDuelScene() {
        if (home != null && home.modal != null && home.modal.isShowing()) home.modal.closeImmediate();
        hideLiveCineSubScene();
        hideLiveSnakePhileScene();
        if (liveCineDuelView != null) {
            home.root.removeView(liveCineDuelView);
            liveCineDuelView = null;
        }

        com.cinetv.launcher.party.CineDuelView duelView = new com.cinetv.launcher.party.CineDuelView(this);
        duelView.setOnCineDuelListener(new com.cinetv.launcher.party.CineDuelView.OnCineDuelListener() {
            @Override
            public void onMovieChosen(String movieTitle) {
                hideLiveCineDuelScene();
                launchFilmByTitle(movieTitle);
            }

            @Override
            public void onGameExit() {
                hideLiveCineDuelScene();
            }
        });
        liveCineDuelView = duelView;
        home.root.addView(liveCineDuelView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        liveCineDuelView.requestFocus();
        liveCineDuelView.startNewGame();
    }

    public void hideLiveCineDuelScene() {
        if (liveCineDuelView != null) {
            home.root.removeView(liveCineDuelView);
            liveCineDuelView = null;
        }
    }

    private com.cinetv.launcher.party.SnakePhileView liveSnakePhileView;

    public void showLiveSnakePhileScene() {
        if (home != null && home.modal != null && home.modal.isShowing()) home.modal.closeImmediate();
        hideLiveCineSubScene();
        hideLiveCineDuelScene();
        hidePartyStage();
        hideLiveSnakePhileScene();
        com.cinetv.launcher.party.SnakePhileView snake = new com.cinetv.launcher.party.SnakePhileView(this);
        snake.setOnSnakePhileListener(new com.cinetv.launcher.party.SnakePhileView.OnSnakePhileListener() {
            @Override
            public void onMovieLaunch(String title) {
                hideLiveSnakePhileScene();
                launchFilmByTitle(title);
            }

            @Override
            public void onExit() {
                hideLiveSnakePhileScene();
            }
        });
        liveSnakePhileView = snake;
        home.root.addView(snake, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        snake.setFocusable(true);
        snake.requestFocus();
        snake.resetGame();
    }

    public void hideLiveSnakePhileScene() {
        if (liveSnakePhileView == null) return;
        home.root.removeView(liveSnakePhileView);
        liveSnakePhileView = null;
    }

    private void showScreensaverInternal() {
        home.screensaver.setAlpha(0f);
        home.screensaver.setVisibility(View.VISIBLE);
        home.screensaver.animate().alpha(1f).setDuration(600).start();
        home.screensaverView.start();
        home.environment.pause();
        handler.postDelayed(sleepPanel, SLEEP_AFTER_SAVER_MS);
    }

    @Override
    public void showCalibrationPattern(String pattern) {
        hideScreensaver();
        home.pattern.show(pattern);
    }

    @Override
    public void onPhoneConnected() {
        if (home.modal.isShowing()) home.modal.close();
        toast(R.string.phone_connected);
    }

    @Override
    public void mirrorSearch(String text) {
        if (home.keyboard.isShowing()) return;
        home.search.setText(text);
        home.search.setSelection(text.length());
    }

    @Override
    public void onPhoneActivity() {
        resetIdle();
        hideScreensaver();
    }

    @Override
    public void onWallpaperChanged() {
        home.environment.apply(ThemeManager.current(this), ThemeManager.isLight(this));
        home.shelf.setScene(null); // the phone's picture has no curtains or shelf of its own
        if (home.modal.isShowing()) home.modal.close();
    }

    @Override
    public void triggerVoiceOverlay() {
        onVoice();
    }

    @Override
    public void resetBackground() {
        applyTheme(ThemeManager.current(this).id, true);
    }

    public static boolean isTvInputActive() {
        MainActivity a = instance;
        if (a == null || a.home == null) return false;
        return a.home.isSearchOrInputFocused();
    }

    @Override
    public void askPairing(String device, Runnable allow, Runnable deny) {
        hideScreensaver();
        final boolean[] answered = {false};
        home.modal.push(() -> {
            android.widget.LinearLayout box = new android.widget.LinearLayout(this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.TextView title = UiKit.bold(this, 30, UiKit.INK);
            title.setText(getString(R.string.pair_ask_title, device));
            box.addView(title);
            android.widget.TextView body = UiKit.label(this, 20, UiKit.INK_DIM);
            body.setText(R.string.pair_ask_body);
            body.setPadding(0, UiKit.px(10), 0, UiKit.px(26));
            box.addView(body, new android.widget.LinearLayout.LayoutParams(UiKit.px(640), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.addView(spaced(Controls.button(this, getString(R.string.pair_allow), v -> {
                answered[0] = true;
                allow.run();
                home.modal.close();
                toast(R.string.phone_connected);
            })));
            row.addView(spaced(Controls.button(this, getString(R.string.pair_deny), v -> {
                answered[0] = true;
                deny.run();
                home.modal.close();
            })));
            box.addView(row);
            return box;
        });
        // Unanswered requests lapse quietly.
        handler.postDelayed(() -> {
            if (!answered[0]) {
                deny.run();
                if (home.modal.isShowing()) home.modal.close();
            }
        }, 60_000L);
    }

    @Override
    public void openTitle(Movie movie) {
        hideScreensaver();
        if (home.modal.isShowing()) home.modal.close();
        openTitle(movie, null);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private void ui(Runnable r) {
        handler.post(() -> {
            if (!isDestroyed()) r.run();
        });
    }

    private static void background(Runnable r) {
        new Thread(r, "cinetv-work").start();
    }
}
