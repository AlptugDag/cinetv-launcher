package com.cinetv.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

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

    private static final long SCREENSAVER_AFTER_MS = 5 * 60 * 1000L;
    private static final long SLEEP_AFTER_SAVER_MS = 5 * 60 * 1000L;
    private static final long AI_SHELF_EVERY_MS = 3 * 60 * 60 * 1000L;
    private static final String SHELF_CACHE = "shelf_cache";
    private static final String SHELF_TITLE = "shelf_title";

    private static final String[][] BUNDLED = {
            {"posters/poster_1.png", "tt15239678", "Dune: Part Two", "2024"},
            {"posters/poster_2.png", "tt15398776", "Oppenheimer", "2023"},
            {"posters/poster_3.png", "tt0816692", "Interstellar", "2014"},
            {"posters/poster_4.png", "tt1877830", "The Batman", "2022"},
            {"posters/poster_5.png", "tt1856101", "Blade Runner 2049", "2017"},
            {"posters/poster_6.png", "tt1375666", "Inception", "2010"},
            {"posters/poster_7.png", "tt9218128", "Gladiator II", "2024"},
            {"posters/poster_8.png", "tt1392190", "Mad Max: Fury Road", "2015"},
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
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(null);
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
            if (!home.shelf.hasFocus() && getCurrentFocus() == null) home.focusLogo();
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

        TvWebRemoteServer.setController(this);
        TvWebRemoteServer.start(this);
        AppUpdater.checkInBackground(this);

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
        handler.postDelayed(tourCheck, 6000);
        boolean alreadyConfigured = (HomeGuard.isDefault(this) && AppLauncher.isLaunchable(getPackageManager(), AppLauncher.NUVIO))
                || com.cinetv.launcher.ui.Brand.isPersonal(this) || Prefs.get(this).contains(Prefs.FIRST_SETUP_DONE);
        if (alreadyConfigured && !Prefs.get(this).contains("wizard_done")) {
            Prefs.get(this).edit().putBoolean("wizard_done", true).apply();
        }
        if (!Prefs.get(this).getBoolean("wizard_done", false)) {
            handler.postDelayed(() -> {
                if (!isFinishing() && home != null && !home.modal.isShowing()) {
                    home.modal.setOnClosed(this::onWizardFinished);
                    Panels.wizard(this, home.modal);
                }
            }, 3000);
        } else if (!Prefs.get(this).getBoolean(Prefs.FIRST_SETUP_DONE, false)) {
            // First start only: tune picture & sound and measure the network once.
            handler.postDelayed(() -> {
                if (!isFinishing() && home != null && !home.modal.isShowing()) Panels.setup(this, home.modal, true);
            }, 4000);
        }
        if (!HomeGuard.isDefault(this) && !Prefs.get(this).getBoolean("prompted_default_home", false)) {
            Prefs.get(this).edit().putBoolean("prompted_default_home", true).apply();
            handler.postDelayed(() -> {
                if (!isFinishing() && !HomeGuard.isDefault(this)) HomeGuard.promptHomeChooser(this);
            }, 8000);
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
        MemoryOptimizer.sweepAsync(this);
        PerformanceProfile.noteMemory(this);
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
        handler.postDelayed(() -> {
            if (!isFinishing() && !hasWindowFocus() && DeviceTweaks.isUnwantedTuner()) {
                background(() -> LocalAdb.shell(this, "am force-stop com.mediatek.wwtv.tvcenter 2>/dev/null; am force-stop com.arcelik.tv.livetv 2>/dev/null", 800));
                try {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                } catch (Throwable ignored) {
                }
            }
        }, 300);
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
        if (home.voice != null) home.voice.dismiss();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(idle);
        handler.removeCallbacks(sleepPanel);
        hideScreensaver();
        // Deep background hibernation: immediately free memory for torrent & external media playback
        PosterCache.trimMemory();
        Glow.clear();
        MemoryOptimizer.cleanBackground(this, getPackageName());
        System.gc();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
        String theme = intent.getStringExtra("theme");
        if (theme != null && !theme.isEmpty()) applyTheme(theme);
        String action = intent.getStringExtra("action");
        if (action == null) action = intent.getAction();
        if (action == null) return;
        if ("setup".equals(action)) handler.postDelayed(() -> Panels.setup(this, home.modal, true), 1500);
        else if ("settings".equals(action)) handler.postDelayed(this::onSettings, 300);
        else if ("voice".equals(action)) handler.postDelayed(this::onVoice, 250);
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
    }

    // ---- keys -------------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (home == null) return super.dispatchKeyEvent(e);
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) {
            resetIdle();
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
            String action = HardwareKeys.actionFor(this, e.getKeyCode(), e.getScanCode());
            if (action != null) {
                if (!HardwareKeys.perform(this, action)) onAllApps();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (home == null) return;
        if (home.isAiCardShowing()) {
                home.hideAiCard();
        } else if (!home.shelf.hasFocus()) {
            home.shelf.requestFocus();
            home.scroll.glideHome();
        }
    }

    // ---- screensaver ------------------------------------------------------------------------------

    private void resetIdle() {
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
        if (ThemeManager.MODE_AUTO.equals(ThemeManager.appearance(this))) applyAppearance();

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
        WeatherService.fetch(this, false, text -> home.weather.setText(text));
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

    @Override
    public void onSearch(final String query) {
        if (query == null || query.trim().isEmpty()) return;
        home.keyboard.hide();
        home.search.setText(query);
        final int gen = ++searchGen;
        // Questions get the answer bubble; film, people and theme searches only change the shelf.
        final boolean question = MovieAssistant.isQuestion(query);
        if (question) home.showThinking();
        else {
            home.hideAiCard();
            home.shelfLabel.setText(getString(R.string.shelf_building, MovieAssistant.cleanShelfName(query)).toUpperCase(Locale.getDefault()));
        }
        final Context app = getApplicationContext();
        background(() -> {
            MovieAssistant.Reply r = MovieAssistant.ask(app, query, PerformanceProfile.shelfSize(app));
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
                } else if (r.movies.isEmpty()) {
                    if (question) {
                        home.showBubble(getString(LlmClient.isConfigured(this) || MovieCatalog.hasTmdb(this)
                                ? R.string.nothing_found : R.string.ai_err_no_key), false, 6000);
                    } else {
                        home.shelfLabel.setText(getString(R.string.shelf_nothing, MovieAssistant.cleanShelfName(query)).toUpperCase(Locale.getDefault()));
                        handler.postDelayed(() -> { if (gen == searchGen) restoreShelfLabel(); }, 4000);
                    }
                } else {
                    home.hideAiCard();
                }
            });
        });
    }

    /** When CineTV itself last started listening (the key service leaves Google's voice window open then). */
    public static volatile long ourVoiceAt;

    @Override
    public void onVoice() {
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

    /**
     * Something said to the TV (its remote's microphone, or the phone's): a TV command ("sesi kıs", "HDMI 2",
     * "Nuvio'yu aç", "30 dakika sonra kapan") runs; anything else is a voice search ("Inception'ı aç",
     * "Nolan'ın filmlerini listele", a question).
     */
    public void onSpoken(String said) {
        com.cinetv.launcher.ai.VoiceCommands.Command c = com.cinetv.launcher.ai.VoiceCommands.parse(said);
        if (c != null && com.cinetv.launcher.ai.VoiceCommands.run(this, c, voiceHost)) {
            home.showBubble("“" + said + "”", false, 2500);
            return;
        }
        onSearch(said);
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
        }
    };

    private static volatile boolean isCurating;

    @Override
    public void curate(final String mood) {
        if (isCurating) return;
        isCurating = true;
        String provider = LlmClient.providerName(this);
        home.shelfLabel.setText(getString(R.string.shelf_curating, provider == null ? "AI" : provider).toUpperCase(Locale.getDefault()));
        final String moment = MovieAssistant.momentContext(home.weather.getText().toString());
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
                m.posterUrl = "asset:" + b[0];
                m.imdbId = b[1];
                m.title = b[2];
                m.year = Integer.parseInt(b[3]);
                cached.add(m);
            }
        }
        setShelf(Prefs.get(this).getString(SHELF_TITLE, getString(R.string.cat_trending)), cached, false);
    }

    private void restoreShelfLabel() {
        home.shelfLabel.setText(Prefs.get(this).getString(SHELF_TITLE, getString(R.string.cat_trending)).toUpperCase(Locale.getDefault()));
    }

    private void setShelf(String title, List<Movie> movies, boolean remember) {
        home.shelfLabel.setText(title.toUpperCase(Locale.getDefault()));
        home.shelf.setMovies(movies);
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
        background(() -> {
            List<Movie> list = MovieCatalog.category(app, cat, PerformanceProfile.shelfSize(app));
            if (!list.isEmpty()) ui(() -> setShelf(getString(cat.nameRes), list, true));
        });
    }

    // ---- opening a film ---------------------------------------------------------------------------

    /** Plays the DVD opening (when enabled and the TV can afford it), then opens the title in Nuvio. */
    public void openTitle(final Movie m, DvdCaseView from) {
        final Context appCtx = getApplicationContext();
        final Runnable launch = () -> {
            if (!m.imdbId.isEmpty()) {
                AppLauncher.openInNuvio(this, m.title, m.imdbId, m.type, m.posterUrl);
                return;
            }
            // Search results carry only TMDB ids: look the IMDb id up now.
            background(() -> {
                MovieCatalog.ensureImdb(appCtx, m);
                ui(() -> AppLauncher.openInNuvio(this, m.title, m.imdbId, m.type, m.posterUrl));
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

    private void prefetchMovie(final Movie m) {
        if (m == null) return;
        final Context app = getApplicationContext();
        final int w = home.opening.innerWidth(), h = home.opening.innerHeight();
        background(() -> {
            try {
                if (m.cast == null || m.cast.isEmpty()) {
                    MovieCatalog.details(app, m);
                }
                if (m.backdropUrl != null && !m.backdropUrl.isEmpty()) {
                    PosterCache.load(app, m.backdropUrl, w * 2, h);
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
                final String initialBackdrop = m.backdropUrl;
                final Bitmap[] stillBox = new Bitmap[1];
                Thread stillThread = null;
                if (initialBackdrop != null && !initialBackdrop.isEmpty()) {
                    stillThread = new Thread(() -> stillBox[0] = PosterCache.load(app, initialBackdrop, w * 2, h));
                    stillThread.start();
                }
                MovieCatalog.details(app, m);
                if (stillThread != null) {
                    try { stillThread.join(2500); } catch (Throwable ignored) {}
                }
                Bitmap still = stillBox[0];
                if (still == null && m.backdropUrl != null && !m.backdropUrl.isEmpty()) {
                    still = PosterCache.load(app, m.backdropUrl, w * 2, h);
                }
                final Bitmap inner = DvdOpeningView.bakeInner(app, w, h, dvd.cover, still, m, dvd.accent);
                ui(() -> home.opening.setInner(inner));
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
        ModalHost.Builder bar = () -> {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            if (tile == null) {
                android.widget.TextView name = UiKit.bold(this, 26, UiKit.INK);
                name.setText(AppArtwork.getCleanLabel(item.packageName, item.label));
                name.setPadding(UiKit.px(8), 0, UiKit.px(20), 0);
                row.addView(name);
            }
            if (index >= 0) row.addView(spaced(Controls.button(this, getString(R.string.move), v -> {
                home.modal.close();
                home.moveHint.setVisibility(View.VISIBLE);
                home.apps.startMove(item, inDock, index);
            })));
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
            boolean ready = Prefs.get(MainActivity.this).getBoolean(Prefs.FIRST_SETUP_DONE, false)
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

    @Override
    public void startTour() {
        home.modal.close();
        home.shelf.requestFocus();
        home.shelf.post(() -> home.tour.start(home.tourSteps(),
                () -> Prefs.setFlag(this, Prefs.TOUR_DONE, true)));
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
            refreshShelf();
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

    @Override
    public void voiceOnTv(String said) {
        if (!inFront) AppLauncher.start(this, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        onSpoken(said);
    }

    @Override
    public void showScreensaver() {
        if (home.screensaver.getVisibility() != View.VISIBLE) showScreensaverInternal();
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
