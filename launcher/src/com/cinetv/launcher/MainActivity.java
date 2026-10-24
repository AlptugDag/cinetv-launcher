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
import com.cinetv.launcher.remote.TvWebRemoteServer;
import com.cinetv.launcher.service.AppCatalog;
import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.AppTileItem;
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
        }, "cinetv-init").start();

        home = new HomeScreen(this, this, this);
        setContentView(home.root);
        applyTheme(ThemeManager.current(this).id, false);
        home.applySpatial(Prefs.spatial3d(this));
        home.modal.setOnClosed(() -> {
            if (!home.shelf.hasFocus() && getCurrentFocus() == null) home.focusLogo();
        });
        home.shelf.setListener(new com.cinetv.launcher.ui.PosterShelf.Listener() {
            @Override
            public void onOpen(Movie movie, DvdCaseView view) {
                openTitle(movie, view);
            }

            @Override
            public void onFocusCase(DvdCaseView view, int accent) {
                home.environment.focusOn(view, accent);
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
        handler.postDelayed(this::refreshShelf, 1500);

        TvWebRemoteServer.setController(this);
        TvWebRemoteServer.start(this);

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
            PerformanceProfile.watchFrames(this, 6000, this::recreate);
        });
        handleIntent(getIntent());
        if (!Prefs.get(this).getBoolean(Prefs.FIRST_SETUP_DONE, false)) {
            // First start only: tune picture & sound and measure the network once.
            handler.postDelayed(() -> Panels.setup(this, home.modal, true), 4000);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        inFront = true;
        home.opening.dismiss();
        home.environment.resume();
        DeviceTweaks.syncNightMode(this);
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        refreshWeather();
        loadApps(false);
        MemoryOptimizer.sweepAsync(this);
        resetIdle();
        if (isNewDay()) refreshShelf();
    }

    private static boolean inFront;

    public static boolean isInFront() {
        return inFront;
    }

    @Override
    protected void onPause() {
        inFront = false;
        home.environment.pause();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(idle);
        handler.removeCallbacks(sleepPanel);
        hideScreensaver();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        TvWebRemoteServer.setController(null);
        try {
            unregisterReceiver(packageReceiver);
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
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down) {
            resetIdle();
        }
        if (home.screensaver.getVisibility() == View.VISIBLE) {
            if (down) hideScreensaver();
            return true;
        }
        if (home.pattern.isShowing()) {
            if (down && (e.getKeyCode() == KeyEvent.KEYCODE_BACK || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER)) home.pattern.show("off");
            return true;
        }
        if (home.opening.isPlaying()) return home.opening.dispatchKeyEvent(e);
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
        if (home.isAiCardShowing()) {
            home.hideAiCard();
        } else if (!home.shelf.hasFocus()) {
            home.shelf.requestFocus();
            home.scroll.smoothScrollTo(0, 0);
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
        if (ThemeManager.MODE_AUTO.equals(ThemeManager.appearance(this))) applyAppearance();
    }

    @Override
    public void refreshWeather() {
        WeatherService.fetch(this, false, text -> home.weather.setText(text));
    }

    @Override
    public void onLogo() {
        onSettings();
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
        AppLauncher.openTvInputs(this);
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
                            showActions(item, false, -1);
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

    @Override
    public void onSearch(final String query) {
        home.search.setText(query);
        final MovieAssistant.Kind kind = MovieAssistant.classify(this, query);
        final String assistant = LlmClient.providerName(this) != null ? LlmClient.providerName(this) : getString(R.string.app_name);
        if (kind == MovieAssistant.Kind.SHELF) {
            home.shelfLabel.setText(getString(R.string.shelf_building, MovieAssistant.cleanShelfName(query)).toUpperCase(Locale.getDefault()));
        }
        background(() -> {
            switch (kind) {
                case SHELF: {
                    MovieAssistant.Shelf s = MovieAssistant.shelfFor(this, query, 12);
                    ui(() -> {
                        if (s == null || s.movies.isEmpty()) {
                            home.showAiCard(assistant, query, getString(R.string.nothing_found));
                            restoreShelfLabel();
                        } else {
                            setShelf(s.title.isEmpty() ? MovieAssistant.cleanShelfName(query) : s.title, s.movies, true);
                            home.shelf.requestFocus();
                        }
                    });
                    break;
                }
                case CHAT: {
                    String answer = MovieAssistant.chat(this, query);
                    ui(() -> home.showAiCard(assistant, query, answer));
                    break;
                }
                default: {
                    Movie m = MovieAssistant.findToPlay(this, query);
                    ui(() -> {
                        if (m == null || m.imdbId.isEmpty()) {
                            home.showAiCard(assistant, query, getString(R.string.nothing_found));
                        } else {
                            home.showAiCard(assistant, query, getString(R.string.opening_title, m.title));
                            openTitle(m, null);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void curate(final String mood) {
        String provider = LlmClient.providerName(this);
        home.shelfLabel.setText(getString(R.string.shelf_curating, provider == null ? "AI" : provider).toUpperCase(Locale.getDefault()));
        background(() -> {
            MovieAssistant.Shelf s = MovieAssistant.curate(this, mood, 12);
            ui(() -> {
                if (s != null && !s.movies.isEmpty()) setShelf(s.title, s.movies, true);
                else restoreShelfLabel();
            });
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

    private boolean isNewDay() {
        int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        return Prefs.get(this).getInt(Prefs.VITRIN_DAY, -1) != today;
    }

    private int cachedShelfSize() {
        try {
            return new JSONArray(Prefs.get(this).getString(SHELF_CACHE, "[]")).length();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** A new selection every day (and an AI-curated one every few hours when a key is set). */
    private void refreshShelf() {
        final Context app = getApplicationContext();
        int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        long lastAi = Prefs.get(this).getLong(Prefs.AI_VITRIN_AT, 0);
        if (LlmClient.isConfigured(this) && System.currentTimeMillis() - lastAi > AI_SHELF_EVERY_MS) {
            Prefs.get(this).edit().putLong(Prefs.AI_VITRIN_AT, System.currentTimeMillis()).putInt(Prefs.VITRIN_DAY, today).apply();
            curate(null);
            return;
        }
        if (!isNewDay() && cachedShelfSize() >= PerformanceProfile.shelfSize(this)) return;
        Prefs.get(this).edit().putInt(Prefs.VITRIN_DAY, today).apply();
        final MovieCatalog.Category cat = MovieCatalog.CATEGORIES[today % MovieCatalog.CATEGORIES.length];
        background(() -> {
            List<Movie> list = MovieCatalog.category(app, cat, PerformanceProfile.shelfSize(app));
            if (!list.isEmpty()) ui(() -> setShelf(getString(cat.nameRes), list, true));
        });
    }

    // ---- opening a film ---------------------------------------------------------------------------

    /** Plays the DVD opening (when enabled and the TV can afford it), then opens the title in Nuvio. */
    public void openTitle(final Movie m, DvdCaseView from) {
        final Runnable launch = () -> AppLauncher.openInNuvio(this, m.title, m.imdbId, m.type, m.posterUrl);
        boolean animate = Prefs.dvdAnimation(this);
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
            DvdCase dvd = DvdCase.bake(app, poster, m.title, m.year, AppArtwork.sampleDistinctPosterColor(poster, 0, 0), cw, ch, 0);
            ui(() -> {
                float cx = home.root.getWidth() / 2f, cy = home.root.getHeight() / 2f;
                home.opening.play(dvd, m, new RectF(cx - cw / 2f, cy + ch * 0.4f, cx + cw / 2f, cy + ch * 1.4f), 3200, cb);
                writeInnerNote(m, dvd);
            });
        });
    }

    /** Fetches the synopsis and a still, then writes the note inside the open case. */
    private void writeInnerNote(final Movie m, final DvdCase dvd) {
        final Context app = getApplicationContext();
        final int w = home.opening.innerWidth(), h = home.opening.innerHeight();
        background(() -> {
            MovieCatalog.details(app, m);
            Bitmap still = m.backdropUrl.isEmpty() ? null : PosterCache.load(app, m.backdropUrl, w * 2, h);
            Bitmap inner = DvdOpeningView.bakeInner(app, w, h, dvd.cover, still, m);
            ui(() -> home.opening.setInner(inner));
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
                home.apps.setColumns(ThemeManager.current(this).columns);
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

    @Override
    public void showActions(final AppTileItem item, final boolean inDock, final int index) {
        home.modal.push(() -> {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            android.widget.TextView name = UiKit.bold(this, 26, UiKit.INK);
            name.setText(AppArtwork.getCleanLabel(item.packageName, item.label));
            name.setPadding(0, 0, UiKit.px(28), 0);
            row.addView(name);
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
        });
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
        String wp = ThemeManager.wallpaperNow(t);
        String caption = ThemeManager.caption(this, wp);
        home.caption.setText(caption == null ? "" : caption);
        if (persist) loadApps(true);
    }

    @Override
    public void applyAppearance() {
        home.environment.apply(ThemeManager.current(this), ThemeManager.isLight(this));
        DeviceTweaks.syncNightMode(this);
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

    @Override
    public void onPerformanceChanged() {
        handler.postDelayed(this::recreate, 300);
    }

    @Override
    public void previewScreensaver() {
        showScreensaver();
    }

    @Override
    public void onKeysChanged() {
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
