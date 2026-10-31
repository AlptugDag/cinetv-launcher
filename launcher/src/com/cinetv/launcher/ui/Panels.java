package com.cinetv.launcher.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Environment;
import android.text.InputType;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.HomeGuard;
import com.cinetv.launcher.MainActivity;
import com.cinetv.launcher.R;
import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.ai.LlmClient;
import com.cinetv.launcher.device.DeviceTweaks;
import com.cinetv.launcher.device.HardwareKeys;
import com.cinetv.launcher.device.MemoryOptimizer;
import com.cinetv.launcher.device.NuvioSetup;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.device.PictureCalibration;
import com.cinetv.launcher.remote.RemotePairing;
import com.cinetv.launcher.service.AppCatalog;
import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.AppUpdater;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.Prefs;
import com.cinetv.launcher.service.SecretStore;
import com.cinetv.launcher.service.ThemeManager;

import java.util.Locale;

/** The glass windows opened from the home screen: settings, pairing, keys, city, remote keys, benchmark. */
public final class Panels {

    /** Things a panel asks the home screen to do. */
    public interface Actions {
        void applyTheme(String id);
        void applyAppearance();
        void setSpatial(boolean on);
        void setDvdCases(boolean on);
        void setTrailerMoments(boolean on);
        void startTour();
        void onPerformanceChanged();
        void previewScreensaver();
        void curate(String mood);
        void refreshWeather();
        void onKeysChanged();
        void reboot();
        void search(String query);
        void onAppsChanged();
    }

    public interface PartyLauncher {
        void launchSnake();
        void launchQuiz();
        void launchRoulette();
        void launchSessiz();
    }

    private Panels() {}

    // ---- Settings (Paginated: Temalar, Görsel Deneyim, Yapay Zeka & Ağ, Sistem & TV) ------------

    public static final int PAGE_THEMES = 0;
    public static final int PAGE_VISUAL = 1;
    public static final int PAGE_AI = 2;
    public static final int PAGE_SYSTEM = 3;

    public static void settings(final Context ctx, final ModalHost host, final Actions a) {
        host.push(() -> buildSettings(ctx, host, a, null));
    }

    private static View buildSettings(final Context ctx, final ModalHost host, final Actions a) {
        return buildSettings(ctx, host, a, null);
    }

    private static View buildSettings(final Context ctx, final ModalHost host, final Actions a, final int pageIndex, final String focusTag) {
        return buildSettings(ctx, host, a, focusTag);
    }

    private static View buildSettings(final Context ctx, final ModalHost host, final Actions a, final String focusTag) {
        // One unified, continuous scrollable settings screen containing all settings sequentially.
        final android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final int settingsW = Math.round(dm.widthPixels * 0.82f);
        final int settingsH = Math.round(dm.heightPixels * 0.84f);
        final boolean wide = settingsW >= UiKit.px(1100);
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(true);
        root.setClipToPadding(true);
        root.setLayoutParams(new LinearLayout.LayoutParams(settingsW, settingsH));

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(UiKit.px(44), UiKit.px(8), UiKit.px(44), UiKit.px(6));

        ImageView logo = new ImageView(ctx);
        logo.setImageBitmap(Brand.mark(ctx));
        head.addView(logo, new LinearLayout.LayoutParams(UiKit.px(36), UiKit.px(36)));

        TextView sub = UiKit.label(ctx, 13, UiKit.INK_DIM);
        ThemeManager.Theme curTheme = ThemeManager.current(ctx);
        sub.setText("   " + (tr ? "Atmosfer: " : "Theme: ") + themeName(ctx, curTheme) + "  ·  v" + AppUpdater.currentVersion(ctx));
        head.addView(sub, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView navHint = UiKit.label(ctx, 13, UiKit.INK_DIM);
        navHint.setText(tr ? "▲ ▼ Dairesel Gezin  ·  Geri: Kapat" : "▲ ▼ Circular Scroll  ·  Back: Close");
        head.addView(navHint);
        root.addView(head);

        final LinearLayout cols = new LinearLayout(ctx);
        cols.setOrientation(LinearLayout.VERTICAL);
        cols.setClipChildren(true);
        cols.setClipToPadding(true);
        cols.setPadding(UiKit.px(16), UiKit.px(12), UiKit.px(16), UiKit.px(16));

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(cols, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Quick Category Jump Bar: instantly smooth-scrolls to each major section
        final LinearLayout jumpBar = new LinearLayout(ctx);
        jumpBar.setOrientation(LinearLayout.HORIZONTAL);
        jumpBar.setGravity(Gravity.CENTER_VERTICAL);
        jumpBar.setPadding(UiKit.px(44), 0, UiKit.px(44), UiKit.px(10));
        jumpBar.setClipChildren(false);

        final String[][] categories = {
                {tr ? "Temalar" : "Themes", "sec_themes"},
                {tr ? "Atmosfer" : "Atmosphere", "sec_atmosphere"},
                {tr ? "Görünüm & 3D" : "Visual & 3D", "sec_visual"},
                {tr ? "Yapay Zekâ" : "AI & Voice", "sec_ai"},
                {tr ? "Sistem" : "System", "sec_system"}
        };
        final View[] jumpPills = new View[categories.length];
        for (int i = 0; i < categories.length; i++) {
            final int pillIdx = i;
            final String secTag = categories[i][1];
            final TextView pill = UiKit.bold(ctx, 14, UiKit.INK);
            pill.setText(categories[i][0]);
            pill.setGravity(Gravity.CENTER);
            pill.setFocusable(true);
            pill.setClickable(true);
            pill.setPadding(UiKit.px(16), UiKit.px(7), UiKit.px(16), UiKit.px(7));
            final Glass pg = new Glass(pill, UiKit.pxf(14), Glass.Style.CONTROL);
            pill.setBackground(pg);

            pill.setOnFocusChangeListener((v, has) -> {
                if (has) com.cinetv.launcher.device.SystemAudio.tick(ctx);
                pg.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
                pill.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
                UiKit.lift(v, has, 1.05f, 120);
            });

            pill.setOnClickListener(v -> {
                com.cinetv.launcher.device.SystemAudio.click(ctx);
                View targetSec = cols.findViewWithTag(secTag);
                if (targetSec != null) {
                    scroll.smoothScrollTo(0, targetSec.getTop());
                }
            });

            pill.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        View targetSec = cols.findViewWithTag(secTag);
                        if (targetSec != null) {
                            scroll.smoothScrollTo(0, targetSec.getTop());
                            targetSec.post(() -> {
                                View next = targetSec.findFocus();
                                if (next == null) next = targetSec.focusSearch(View.FOCUS_DOWN);
                                if (next != null) next.requestFocus();
                            });
                            return true;
                        }
                    }
                    if (pillIdx == 0 && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        jumpPills[jumpPills.length - 1].requestFocus();
                        return true;
                    }
                    if (pillIdx == categories.length - 1 && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        jumpPills[0].requestFocus();
                        return true;
                    }
                }
                return false;
            });

            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) plp.leftMargin = UiKit.px(10);
            jumpBar.addView(pill, plp);
            jumpPills[i] = pill;
        }
        root.addView(jumpBar);

        final FrameLayout frame = new FrameLayout(ctx);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        slp.setMargins(UiKit.px(44), UiKit.px(10), UiKit.px(44), UiKit.px(18));
        frame.addView(scroll, slp);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        flp.topMargin = UiKit.px(2);
        root.addView(frame, flp);

        final int colWidth = 0; // sections lay their tiles out in a full-width, self-filling grid

        // =========================================================================
        // 1. SECTION: TEMALAR & ATMOSFER (THEMES & ATMOSPHERE)
        // =========================================================================
        LinearLayout c1 = column(ctx, cols, R.string.col_themes, colWidth);
        c1.setTag("sec_themes");
        ThemeManager.Theme current = ThemeManager.current(ctx);
        for (int k = 0; k < ThemeManager.THEMES.length; k++) {
            final ThemeManager.Theme t = ThemeManager.THEMES[k];
            final String themeTag = "theme_" + t.id;
            LinearLayout tile = Controls.tile(ctx, themeName(ctx, t), null, t.id.equals(current.id), v -> {
                a.applyTheme(t.id);
                host.replace(() -> buildSettings(ctx, host, a, themeTag));
            });
            tile.setTag(themeTag);
            c1.addView(tile);
        }
        boolean customBg = ThemeManager.usesCustomWallpaper(ctx);
        c1.addView(Controls.tile(ctx, ctx.getString(R.string.background), ctx.getString(customBg ? R.string.background_custom : R.string.background_hint), customBg,
                v -> host.push(() -> buildPairing(ctx, host, "background"))));
        if (customBg) {
            c1.addView(Controls.tile(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "Temanın Arka Planına Dön" : "Restore Theme Background",
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Gönderdiğin resmi kaldırıp temanın kendi arka planını geri getir" : "Remove your picture and bring back the theme's own background", false,
                    v -> {
                        a.applyTheme(ThemeManager.current(ctx).id);
                        host.replace(() -> buildSettings(ctx, host, a, "theme_" + ThemeManager.current(ctx).id));
                    }));
        }

        LinearLayout c2 = column(ctx, cols, tr ? "Gece İzleme & Atmosfer" : "Night Viewing & Atmosphere", colWidth);
        c2.setTag("sec_atmosphere");
        boolean nightViewActive = com.cinetv.launcher.device.NightCare.isNightViewingModeActive(ctx);
        boolean visualPref = com.cinetv.launcher.device.NightCare.isVisualPrefEnabled(ctx);
        boolean audioPref = com.cinetv.launcher.device.NightCare.isAudioPrefEnabled(ctx);

        final LinearLayout visualTile = Controls.toggleTile(ctx,
                tr ? "↳ Gece Göz Koruma Filtresi (Sıcak Amber Ton)" : "↳ Night Eye-Care Warm Tone",
                visualPref ? (tr ? "Açık · Sıcak amber ton" : "On · Warm amber tone") : (tr ? "Kapalı" : "Off"),
                visualPref, (newActive, subLabel, sw) -> {
                    com.cinetv.launcher.device.NightCare.setVisualActive(ctx, newActive);
                    subLabel.setText(newActive ? (tr ? "Açık · Sıcak amber ton" : "On · Warm amber tone") : (tr ? "Kapalı" : "Off"));
                    a.applyAppearance();
                });
        visualTile.setTag("opt_night_visual");

        final LinearLayout audioTileNight = Controls.toggleTile(ctx,
                tr ? "↳ Gece Dinamik Ses Dengeleme (Fısıltı Seviyesi)" : "↳ Night Dynamic Audio Leveling",
                audioPref ? (tr ? "Açık · Fısıltı & ses sınırı aktif" : "On · Volume capped & leveled") : (tr ? "Kapalı" : "Off"),
                audioPref, (newActive, subLabel, sw) -> {
                    com.cinetv.launcher.device.NightCare.setAudioActive(ctx, newActive);
                    subLabel.setText(newActive ? (tr ? "Açık · Fısıltı & ses sınırı aktif" : "On · Volume capped & leveled") : (tr ? "Kapalı" : "Off"));
                });
        audioTileNight.setTag("opt_night_audio");

        LinearLayout nightTile = Controls.toggleTile(ctx,
                tr ? "Gece İzleme Modu (23:00 - 05:00)" : "Night Viewing Mode (23:00 - 05:00)",
                nightViewActive
                        ? (tr ? "Açık · 23:00-05:00 saatleri aktif" : "On · Active 23:00-05:00")
                        : (tr ? "Kapalı · 23:00-05:00 arası ekran açılınca sorulur" : "Off · Prompted between 23:00 and 05:00"),
                nightViewActive, (newActive, subLabel, sw) -> {
                    com.cinetv.launcher.device.NightCare.setNightViewingMode(ctx, newActive);
                    subLabel.setText(newActive
                            ? (tr ? "Açık · 23:00-05:00 saatleri aktif" : "On · Active 23:00-05:00")
                            : (tr ? "Kapalı · 23:00-05:00 arası ekran açılınca sorulur" : "Off · Prompted between 23:00 and 05:00"));
                    visualTile.setAlpha(newActive ? 1.0f : 0.5f);
                    audioTileNight.setAlpha(newActive ? 1.0f : 0.5f);
                    a.applyAppearance();
                });
        nightTile.setTag("opt_night_view_mode");
        visualTile.setAlpha(nightViewActive ? 1.0f : 0.5f);
        audioTileNight.setAlpha(nightViewActive ? 1.0f : 0.5f);
        c2.addView(nightTile);
        c2.addView(visualTile);
        c2.addView(audioTileNight);

        c2.addView(Controls.tile(ctx, ctx.getString(R.string.night_title), nightState(ctx), com.cinetv.launcher.device.NightCare.sleepMinutesLeft(ctx) > 0,
                v -> host.push(() -> buildNight(ctx, host, a))));
        c2.addView(Controls.tile(ctx, ctx.getString(R.string.tour_title), ctx.getString(R.string.tour_hint), false, v -> a.startTour()));

        // =========================================================================
        // 2. SECTION: SİNEMA DENEYİMİ & 3D (CINEMA & 3D EXPERIENCE)
        // =========================================================================
        LinearLayout cVisual = column(ctx, cols, R.string.col_visual, colWidth);
        cVisual.setTag("sec_visual");
        boolean spatial = Prefs.spatial3d(ctx);
        LinearLayout spatialTile = Controls.toggleTile(ctx, ctx.getString(R.string.spatial_hud),
                spatial ? (tr ? "Açık · 3D Derinlik, Vizör Kırılması & Akustik" : "On · 3D Depth, Visor Refraction & Acoustics") : ctx.getString(R.string.state_off),
                spatial, (newActive, subLabel, sw) -> {
            Prefs.setFlag(ctx, Prefs.SPATIAL_3D, newActive);
            subLabel.setText(newActive ? (tr ? "Açık · 3D Derinlik, Vizör Kırılması & Akustik" : "On · 3D Depth, Visor Refraction & Acoustics") : ctx.getString(R.string.state_off));
            a.setSpatial(newActive);
        });
        spatialTile.setTag("opt_spatial");
        cVisual.addView(spatialTile);

        boolean bevel = Prefs.glassBevel(ctx);
        LinearLayout bevelTile = Controls.toggleTile(ctx, ctx.getString(R.string.glass_bevel),
                bevel ? (tr ? "Açık · Prizmatik Kristal Kontur" : "On · Prismatic Crystal Contour") : ctx.getString(R.string.state_off),
                bevel, (newActive, subLabel, sw) -> {
            Prefs.setFlag(ctx, Prefs.GLASS_BEVEL, newActive);
            subLabel.setText(newActive ? (tr ? "Açık · Prizmatik Kristal Kontur" : "On · Prismatic Crystal Contour") : ctx.getString(R.string.state_off));
            GlassBevelView.setAberrationEnabled(newActive);
        });
        bevelTile.setTag("opt_bevel");
        cVisual.addView(bevelTile);

        boolean cases = Prefs.dvdCases(ctx);
        LinearLayout casesTile = Controls.toggleTile(ctx, ctx.getString(R.string.dvd_cases),
                cases ? ctx.getString(R.string.dvd_cases_on) : ctx.getString(R.string.dvd_cases_off),
                cases, (newActive, subLabel, sw) -> {
            Prefs.setFlag(ctx, Prefs.DVD_CASES, newActive);
            subLabel.setText(newActive ? ctx.getString(R.string.dvd_cases_on) : ctx.getString(R.string.dvd_cases_off));
            a.setDvdCases(newActive);
        });
        casesTile.setTag("opt_cases");
        cVisual.addView(casesTile);

        boolean ambilight = com.cinetv.launcher.device.AmbilightSync.isEnabled(ctx);
        LinearLayout ambilightTile = Controls.toggleTile(ctx,
                tr ? "Ambilight & Arka Işık Senkronu" : "Ambilight & Backlight Sync",
                ambilight ? (tr ? "Açık · Ortam ışıkları vitrin renkleriyle senkronize" : "On · Synced with poster colors")
                          : (tr ? "Kapalı" : "Off"),
                ambilight, (newActive, subLabel, sw) -> {
                    com.cinetv.launcher.device.AmbilightSync.setEnabled(ctx, newActive);
                    subLabel.setText(newActive ? (tr ? "Açık · Ortam ışıkları vitrin renkleriyle senkronize" : "On · Synced with poster colors") : (tr ? "Kapalı" : "Off"));
                });
        ambilightTile.setTag("opt_ambilight");
        cVisual.addView(ambilightTile);

        boolean trailers = Prefs.trailerMoments(ctx);
        LinearLayout trailersTile = Controls.toggleTile(ctx, ctx.getString(R.string.trailer_moments),
                trailers ? ctx.getString(R.string.trailer_moments_on) : ctx.getString(R.string.state_off),
                trailers, (newActive, subLabel, sw) -> {
            Prefs.setFlag(ctx, Prefs.TRAILER_MOMENTS, newActive);
            subLabel.setText(newActive ? ctx.getString(R.string.trailer_moments_on) : ctx.getString(R.string.state_off));
            a.setTrailerMoments(newActive);
        });
        trailersTile.setTag("opt_trailers");
        cVisual.addView(trailersTile);

        boolean trailerAudio = Prefs.trailerAudio(ctx);
        LinearLayout audioTile = Controls.toggleTile(ctx,
                tr ? "Fragman Sesi" : "Trailer Audio",
                Prefs.trailerAudioLabel(ctx),
                trailerAudio, (newActive, subLabel, sw) -> {
                    Prefs.setTrailerAudio(ctx, newActive);
                    subLabel.setText(Prefs.trailerAudioLabel(ctx));
                });
        audioTile.setTag("opt_trailer_audio");
        cVisual.addView(audioTile);

        boolean sysAudio = com.cinetv.launcher.device.SystemAudio.isEnabled(ctx);
        LinearLayout sysAudioTile = Controls.toggleTile(ctx,
                tr ? "Arayüz & Sistem Sesleri" : "UI & System Audio",
                sysAudio ? (tr ? "Açık · Hans Zimmer & Apple Akustik Tınılar" : "On · Hans Zimmer & Apple Acoustics") : ctx.getString(R.string.state_off),
                sysAudio, (newActive, subLabel, sw) -> {
            com.cinetv.launcher.device.SystemAudio.setEnabled(ctx, newActive);
            subLabel.setText(newActive ? (tr ? "Açık · Hans Zimmer & Apple Akustik Tınılar" : "On · Hans Zimmer & Apple Acoustics") : ctx.getString(R.string.state_off));
        });
        sysAudioTile.setTag("opt_sys_audio");
        cVisual.addView(sysAudioTile);

        LinearLayout cAnim = column(ctx, cols, tr ? "Animasyon & Performans" : "Animation & Performance", colWidth);
        boolean dvd = Prefs.dvdAnimation(ctx);
        LinearLayout dvdTile = Controls.toggleTile(ctx, ctx.getString(R.string.dvd_animation),
                dvd ? (tr ? "Açık · 3D Kapak Açılışı & Disk" : "On · 3D Case Opening & Disc") : ctx.getString(R.string.state_off),
                dvd, (newActive, subLabel, sw) -> {
            Prefs.setFlag(ctx, Prefs.DVD_ANIMATION, newActive);
            subLabel.setText(newActive ? (tr ? "Açık · 3D Kapak Açılışı & Disk" : "On · 3D Case Opening & Disc") : ctx.getString(R.string.state_off));
        });
        dvdTile.setTag("opt_dvd");
        cAnim.addView(dvdTile);

        String perfMode = PerformanceProfile.mode(ctx);
        String tierName = ctx.getString(tierRes(PerformanceProfile.tier(ctx)));
        cAnim.addView(Controls.tile(ctx, ctx.getString(R.string.performance),
                ctx.getString(perfModeRes(perfMode)) + " · " + tierName, false, v -> host.push(() -> buildPerformancePicker(ctx, host, a))));
        cAnim.addView(Controls.tile(ctx, ctx.getString(R.string.screensaver), ctx.getString(R.string.screensaver_preview), false, v -> {
            host.close();
            a.previewScreensaver();
        }));

        cAnim.addView(Controls.tile(ctx,
                tr ? "Arayüz Ölçeği & Yazı Boyutu" : "UI Scale & Typography Size",
                tr ? (Prefs.uiScaleLabel(ctx) + " · " + Prefs.fontScaleLabel(ctx) + " (Canlı Önizlemeli)")
                   : (Prefs.uiScaleLabel(ctx) + " · " + Prefs.fontScaleLabel(ctx) + " (Live Preview)"),
                false, v -> host.push(() -> buildScalePreviewModal(ctx, host, a))));

        java.util.List<String> nuvioPkgs = AppLauncher.installedNuvioPackages(ctx);
        if (nuvioPkgs.size() > 1) {
            String currentSelected = AppLauncher.resolveNuvioPackage(ctx);
            String currentPkgLabel = AppArtwork.getCleanLabel(currentSelected, currentSelected);
            cAnim.addView(Controls.tile(ctx,
                    tr ? "Varsayılan Film Oynatıcı" : "Default Movie Player",
                    currentPkgLabel, true, v -> {
                        int idx = nuvioPkgs.indexOf(currentSelected);
                        int nextIdx = (idx + 1) % nuvioPkgs.size();
                        Prefs.setPreferredNuvioPackage(ctx, nuvioPkgs.get(nextIdx));
                        host.replace(() -> buildSettings(ctx, host, a, "opt_nuvio_player"));
                    }));
        }

        // =========================================================================
        // 3. SECTION: YAPAY ZEKA & AĞ (AI & NETWORK)
        // =========================================================================
        LinearLayout cAi = column(ctx, cols, R.string.col_ai, colWidth);
        cAi.setTag("sec_ai");
        String provider = LlmClient.providerName(ctx);
        cAi.addView(Controls.tile(ctx, ctx.getString(R.string.ai_key), provider != null ? provider : ctx.getString(R.string.not_set),
                provider != null, v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.AI_KEY, R.string.ai_key, R.string.ai_key_hint))));
        boolean tmdb = MovieCatalog.hasTmdb(ctx);
        cAi.addView(Controls.tile(ctx, ctx.getString(R.string.tmdb_key), ctx.getString(tmdb ? R.string.state_on : R.string.not_set), tmdb,
                v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.TMDB_KEY, R.string.tmdb_key, R.string.tmdb_key_hint))));
        boolean mdb = SecretStore.has(ctx, SecretStore.MDBLIST_KEY);
        cAi.addView(Controls.tile(ctx, ctx.getString(R.string.mdblist_key), ctx.getString(mdb ? R.string.state_on : R.string.not_set), mdb,
                v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.MDBLIST_KEY, R.string.mdblist_key, R.string.mdblist_key_hint))));

        boolean emaOn = Prefs.emaTts(ctx);
        boolean emaSupported = com.cinetv.launcher.ai.EmaLightningTts.isSupportedHardware(ctx);
        int level = Prefs.ttsLevel(ctx);
        String levelLabel = level == Prefs.TTS_LEVEL_SEARCH_ONLY
                ? (tr ? "Sadece Arama" : "Search Only")
                : level == Prefs.TTS_LEVEL_PROACTIVE
                ? (tr ? "Tam Proaktif" : "Full Proactive")
                : (tr ? "Dengeli" : "Balanced");
        String emaTitle = tr
                ? "Gamze Sesli Asistan (EMA Lightning & Uzamsal Ses)"
                : "Gamze Voice Assistant (EMA Lightning & Spatial Audio)";
        String emaSub = emaOn
                ? (tr
                        ? "Açık · " + levelLabel + " · Akustik uzamsal ses devrede · Seviyeyi ayarlamak için dokunun"
                        : "On · " + levelLabel + " · Spatial acoustics active · Tap to adjust")
                : (tr
                        ? (emaSupported ? "Kapalı · Sesli yanıtları ve asistanı etkinleştirmek için dokunun" : "Kapalı · Yalnızca orta ve üst performans profilinde önerilir")
                        : "Off · Recommended for Mid and High performance profiles");
        LinearLayout emaTile = Controls.tile(ctx, emaTitle, emaSub, emaOn, v -> {
            host.push(() -> buildTtsSettings(ctx, host, a));
        });
        emaTile.setTag("opt_ema_tts");
        cAi.addView(emaTile);

        cAi.addView(Controls.tile(ctx,
                tr ? "Letterboxd Sinema Günlüğü (Scrobble)" : "Letterboxd Cinema Diary",
                com.cinetv.launcher.service.LetterboxdSync.isConfigured(ctx)
                        ? ("@" + com.cinetv.launcher.service.LetterboxdSync.getUsername(ctx) + " · " + (tr ? "Aktif" : "Active"))
                        : (tr ? "İzlenenleri otomatik kaydet & CSV dışa aktar" : "Auto-log watches & export CSV"),
                com.cinetv.launcher.service.LetterboxdSync.isConfigured(ctx),
                v -> host.push(() -> buildLetterboxdModal(ctx, host))));

        cAi.addView(Controls.tile(ctx,
                tr ? "CineSUB Apple Vision Öğreticisi" : "CineSUB Apple Vision Tutorial",
                tr ? "Tam ekran uzamsal kültürel altyazı ve sahne keşif rehberi" : "Fullscreen spatial cultural subtitle & scene discovery guide",
                false, v -> host.push(() -> buildCineSubTutorialModal(ctx, host))));

        LinearLayout cNet = column(ctx, cols, tr ? "Ağ & Ortam" : "Network & Environment", colWidth);
        String city = Prefs.city(ctx);
        cNet.addView(Controls.tile(ctx, ctx.getString(R.string.weather_city), city != null ? city : ctx.getString(R.string.weather_auto), city != null,
                v -> host.push(() -> buildCityInput(ctx, host, a))));
        cNet.addView(Controls.tile(ctx, tr ? "Görüntü, Ses, Ağ & Optimizasyon" : "Picture, Sound, Network & Optimization",
                tr ? "TV Denetçisi verilerine göre optimize et, DNS seç ve test et" : "Optimize from TV Inspector data, pick DNS, test",
                false, v -> setup(ctx, host, true)));

        // =========================================================================
        // 4. SECTION: SİSTEM & TV DONANIMI (SYSTEM & TV HARDWARE)
        // =========================================================================
        LinearLayout cTv = column(ctx, cols, R.string.col_tv, colWidth);
        cTv.setTag("sec_system");
        com.cinetv.launcher.device.AcousticsCalibrator.AudioOutputInfo outInfo = com.cinetv.launcher.device.AcousticsCalibrator.detectAudioOutput(ctx);
        boolean isCalibrated = com.cinetv.launcher.device.AcousticsCalibrator.isCalibrated(ctx);
        cTv.addView(Controls.tile(ctx,
                tr ? "Oda Akustiği & Ses Kalibrasyonu" : "Room Acoustics & Sound Calibration",
                isCalibrated ? (tr ? "Kalibre Edildi · " + outInfo.name : "Calibrated · " + outInfo.name)
                             : (tr ? outInfo.name + " · Kalibre etmek veya test etmek için dokunun" : outInfo.name + " · Tap to test & calibrate"),
                isCalibrated || outInfo.isExternal,
                v -> host.push(() -> buildAcousticsCalibrationModal(ctx, host))));

        boolean hpConnected = com.cinetv.launcher.device.AirPodsLink.isHeadphoneConnected(ctx);
        cTv.addView(Controls.tile(ctx,
                tr ? "AirPods & Kulaklık Zenginleştirme" : "AirPods & Headphone Spatial Audio",
                hpConnected ? (tr ? "Kulaklık Bağlı · Uzamsal Ses ve Netlik" : "Connected · Spatial Audio & Clarity")
                            : (tr ? "Uzamsal ses, gürültü engelleme ve netlik ayarları" : "Spatial audio, ANC profiles & clarity"),
                hpConnected, v -> host.push(() -> buildAirPodsSettingsModal(ctx, host))));

        cTv.addView(Controls.tile(ctx, tr ? "Kurulum Sihirbazı & Bileşen Kontrolü" : "Setup Wizard & Component Health",
                tr ? "Eksik bileşenleri kontrol et ve kur" : "Check and setup components", false,
                v -> wizard(ctx, host)));
        boolean isHome = com.cinetv.launcher.HomeGuard.isDefault(ctx);
        cTv.addView(Controls.tile(ctx, tr ? "Ana Başlatıcı Yap" : "Set as Default Home",
                isHome ? (tr ? "CineTV varsayılan ana ekran" : "CineTV is default home")
                       : (tr ? "Kumanda ana tuşu için dokunun" : "Tap to set as default launcher"),
                isHome, v -> com.cinetv.launcher.HomeGuard.promptHomeChooser(ctx)));
        boolean a11y = com.cinetv.launcher.TvKeyInterceptorService.getInstance() != null;
        cTv.addView(Controls.tile(ctx,
                tr ? "Kumanda Tuş Entegrasyonu" : "Remote Key Integration",
                a11y ? (tr ? "Yerel tuşlar & global servis devrede · Özelleştirmek için dokunun" : "Native keys & global service active · Tap to customize")
                     : (tr ? "Yerel kumanda tuşları aktif · Tuşları özelleştirmek için dokunun" : "Native keys active · Tap to customize"),
                true, v -> host.push(() -> buildKeyCapture(ctx, host, a))));
        cTv.addView(Controls.tile(ctx, ctx.getString(R.string.tv_settings), ctx.getString(R.string.tv_settings_hint), false,
                v -> AppLauncher.openSystemSettings(ctx)));
        if (AppLauncher.liveTvPackage(ctx.getPackageManager()) != null || AppLauncher.hasHdmiSource(ctx)) {
            boolean liveTv = AppCatalog.liveTvShown(ctx);
            LinearLayout liveTvTile = Controls.toggleTile(ctx, ctx.getString(R.string.live_tv_tile),
                    liveTv ? ctx.getString(R.string.live_tv_tile_on) : ctx.getString(R.string.state_off),
                    liveTv, (newActive, subLabel, sw) -> {
                if (!newActive) AppCatalog.hide(ctx, AppLauncher.LIVE_TV);
                else AppCatalog.addToHome(ctx, AppLauncher.LIVE_TV);
                subLabel.setText(newActive ? ctx.getString(R.string.live_tv_tile_on) : ctx.getString(R.string.state_off));
                a.onAppsChanged();
            });
            liveTvTile.setTag("opt_livetv");
            cTv.addView(liveTvTile);
        }

        LinearLayout cMaint = column(ctx, cols, tr ? "Bakım & Donanım" : "Maintenance & Hardware", colWidth);
        cMaint.addView(Controls.tile(ctx,
                tr ? "CineTV Turu & Yenilikler Rehberi" : "CineTV Tour & What's New",
                tr ? "v1.0.0.16'dan bu yana olan tüm yeni özellikleri incele" : "Explore all new features since v1.0.0.16", false,
                v -> host.push(() -> buildCineTvTourModal(ctx, host))));
        cMaint.addView(Controls.tile(ctx, tr ? "TV Denetçisi (Donanım, Panel & Telemetri)" : "TV Inspector (Hardware, Panel & Telemetry)",
                tr ? "Ekran, HDR, ses çözücüleri, GitHub telemetrisi ve canlı tuşlar" : "Display, HDR, audio codecs, GitHub telemetry & live keys", false,
                v -> host.push(() -> buildTvInspector(ctx, host))));
        cMaint.addView(Controls.tile(ctx,
                tr ? "Fiziksel Kumanda & Renkli Tuşlar" : "Remote & Color Keys",
                tr ? "Kırmızı, Yeşil, Sarı, Mavi tuşlara CineSub ve eylem ata" : "Assign CineSub and actions to colored keys", false,
                v -> host.push(() -> buildKeyCapture(ctx, host, a))));
        String curVer = AppUpdater.currentVersion(ctx);
        cMaint.addView(Controls.tile(ctx, tr ? "Sürüm & Güncelleme" : "Version & Updates",
                "v" + curVer + (tr ? " · Güncellemeleri Denetle" : " · Check for Updates"), false, v -> {
            Toast.makeText(ctx, tr ? "GitHub güncellemeleri denetleniyor..." : "Checking GitHub updates...", Toast.LENGTH_SHORT).show();
            AppUpdater.checkForUpdates(ctx, new AppUpdater.UpdateCheckCallback() {
                @Override
                public void onUpdateAvailable(AppUpdater.ReleaseInfo info) {
                    if (ctx instanceof android.app.Activity) {
                        AppUpdater.showUpdateDialog((android.app.Activity) ctx, info);
                    }
                }

                @Override
                public void onUpToDate(String currentVersion) {
                    Toast.makeText(ctx, tr
                            ? "CineTV en güncel sürümde (v" + currentVersion + ")"
                            : "CineTV is up to date (v" + currentVersion + ")", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(ctx, tr
                            ? "Güncelleme denetimi başarısız: " + message
                            : "Update check failed: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        }));
        boolean adbActive = false;
        try {
            adbActive = android.provider.Settings.Global.getInt(ctx.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (Throwable ignored) {}
        final boolean finalAdb = adbActive;
        LinearLayout adbTile = Controls.toggleTile(ctx,
                tr ? "Geliştirici & Hata Ayıklama Güvenliği" : "Developer & Debugging Security",
                finalAdb ? (tr ? "Açık (Ağda aktif) · Kapatıp TV'yi izole etmek için dokunun" : "Active (Enabled on network) · Tap to disable & isolate TV")
                         : (tr ? "Kapalı (Güvenli) · Mobil kumanda güvenle çalışır" : "Disabled (Secure) · Mobile remote works securely"),
                finalAdb, (newActive, subLabel, sw) -> {
                    try {
                        android.provider.Settings.Global.putInt(ctx.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, newActive ? 1 : 0);
                    } catch (Throwable ignored) {}
                    com.cinetv.launcher.remote.LocalAdb.shell(ctx, "settings put global adb_enabled " + (newActive ? "1" : "0"), 1500);
                    subLabel.setText(newActive
                            ? (tr ? "Açık (Ağda aktif) · Kapatıp TV'yi izole etmek için dokunun" : "Active (Enabled on network) · Tap to disable & isolate TV")
                            : (tr ? "Kapalı (Güvenli) · Mobil kumanda güvenle çalışır" : "Disabled (Secure) · Mobile remote works securely"));
                    Toast.makeText(ctx, newActive
                            ? (tr ? "Ağda hata ayıklama etkinleştirildi" : "Debugging enabled")
                            : (tr ? "USB Hata ayıklama kapatıldı. TV'niz güvenli modda." : "USB debugging disabled. TV is secured."), Toast.LENGTH_SHORT).show();
                });
        adbTile.setTag("opt_adb_sec");
        cMaint.addView(adbTile);
        cMaint.addView(Controls.tile(ctx, tr ? "Nuvio Eklentilerini Yönet & Kur" : "Manage & Install Nuvio Addons",
                tr ? "Eklenti ve profil seçim penceresini aç" : "Select and install curated addons", false,
                v -> host.push(() -> buildNuvioUpdateAddons(ctx, host))));
        cMaint.addView(Controls.tile(ctx, ctx.getString(R.string.reboot), null, false, v -> a.reboot()));
        cMaint.addView(Controls.alarm(ctx, ctx.getString(R.string.destroy), v -> host.push(() -> buildDestroy(ctx, host))));

        // Lay all columns into multi-column responsive grids
        final int gridCols = wide ? 3 : 2;
        final List<LinearLayout> allRows = new ArrayList<>();
        for (int i = 0; i < cols.getChildCount(); i++) {
            if (cols.getChildAt(i) instanceof LinearLayout) {
                gridify(ctx, (LinearLayout) cols.getChildAt(i), gridCols, i, allRows);
            }
        }

        // Circular Navigation: Seamless loop from bottom-most tiles to top-most tiles and vice versa
        if (!allRows.isEmpty()) {
            final LinearLayout firstRow = allRows.get(0);
            final LinearLayout lastRow = allRows.get(allRows.size() - 1);

            for (int r = 0; r < allRows.size(); r++) {
                final LinearLayout row = allRows.get(r);
                final boolean isFirstRow = (r == 0);
                final boolean isLastRow = (r == allRows.size() - 1);
                final int rowCount = row.getChildCount();

                for (int c = 0; c < rowCount; c++) {
                    final View tile = row.getChildAt(c);
                    final int colIdx = c;
                    final boolean isFirstCol = (c == 0);
                    final boolean isLastCol = (c == rowCount - 1);

                    tile.setOnKeyListener((v, keyCode, event) -> {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (isFirstRow && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                scroll.scrollTo(0, cols.getBottom());
                                int target = Math.min(colIdx, lastRow.getChildCount() - 1);
                                lastRow.getChildAt(target).post(() -> lastRow.getChildAt(target).requestFocus());
                                return true;
                            }
                            if (isLastRow && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                scroll.scrollTo(0, 0);
                                int target = Math.min(colIdx, firstRow.getChildCount() - 1);
                                firstRow.getChildAt(target).post(() -> firstRow.getChildAt(target).requestFocus());
                                return true;
                            }
                            if (isFirstCol && rowCount > 1 && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                row.getChildAt(rowCount - 1).requestFocus();
                                return true;
                            }
                            if (isLastCol && rowCount > 1 && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                row.getChildAt(0).requestFocus();
                                return true;
                            }
                        }
                        return false;
                    });
                }
            }
        }

        if (focusTag != null) {
            root.post(() -> {
                View target = root.findViewWithTag(focusTag);
                if (target != null) {
                    target.requestFocus();
                }
            });
        }
        return root;
    }

    private static LinearLayout column(Context ctx, LinearLayout cols, int titleRes, int widthDesign) {
        return column(ctx, cols, ctx.getString(titleRes), widthDesign);
    }

    private static LinearLayout column(Context ctx, LinearLayout cols, String titleStr, int widthDesign) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        c.setClipToPadding(false);
        c.addView(Controls.header(ctx, titleStr));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = cols.getChildCount() == 0 ? 0 : UiKit.px(28);
        cols.addView(c, lp);
        return c;
    }

    private static final int[] SECTION_TINTS = {0xFFF59E0B, 0xFF38BDF8, 0xFFC084FC, 0xFF34D399, 0xFFFB7185, 0xFF60A5FA, 0xFFFBBF24, 0xFF2DD4BF, 0xFFA78BFA};

    /** Lays a section's tiles into full rows (no orphan gaps) that stretch to fill the width, tinted per section. */
    private static void gridify(Context ctx, LinearLayout section, int gridCols, int index) {
        gridify(ctx, section, gridCols, index, null);
    }

    private static void gridify(Context ctx, LinearLayout section, int gridCols, int index, List<LinearLayout> allRows) {
        int tint = SECTION_TINTS[index % SECTION_TINTS.length];
        View header = section.getChildAt(0);
        if (header instanceof TextView) {
            ((TextView) header).setTextColor(tint);
            ((TextView) header).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(19));
            header.setPadding(UiKit.px(6), 0, 0, UiKit.px(12));
        }
        List<View> tiles = new ArrayList<>();
        for (int i = 1; i < section.getChildCount(); i++) tiles.add(section.getChildAt(i));
        section.removeViews(1, section.getChildCount() - 1);
        int n = tiles.size();
        if (n == 0) return;
        int rows = (n + gridCols - 1) / gridCols;
        int base = n / rows, extra = n % rows, at = 0;
        final int gap = UiKit.px(14);
        for (int r = 0; r < rows; r++) {
            int count = base + (r < extra ? 1 : 0);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            for (int k = 0; k < count; k++) {
                View tile = tiles.get(at++);
                tile.setMinimumHeight(UiKit.px(104));
                if (tile instanceof ViewGroup) {
                    ((ViewGroup) tile).setClipChildren(false);
                    ((ViewGroup) tile).setClipToPadding(false);
                }
                if (tile instanceof LinearLayout) {
                    ((LinearLayout) tile).setGravity(Gravity.CENTER_VERTICAL);
                    View st = ((LinearLayout) tile).getChildCount() > 1 ? ((LinearLayout) tile).getChildAt(1) : null;
                    if (st instanceof TextView) {
                        ((TextView) st).setSingleLine(false);
                        ((TextView) st).setMaxLines(2);
                        ((TextView) st).setEllipsize(android.text.TextUtils.TruncateAt.END);
                    }
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.rightMargin = k < count - 1 ? gap : 0;
                row.addView(tile, lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = UiKit.px(4);
            rlp.bottomMargin = gap;
            section.addView(row, rlp);
            if (allRows != null) {
                allRows.add(row);
            }
        }
    }

    /** Arrows around the page: up/down while it scrolls. Left/right removed for clean single-page UX. */
    private static void attachScrollArrows(final Context ctx, final FrameLayout frame, final ScrollView scroll, final LinearLayout content) {
        final TextView up = arrow(ctx, "▲", Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        final TextView down = arrow(ctx, "▼", Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        frame.addView(up);
        frame.addView(down);
        final Runnable update = () -> {
            up.setAlpha(scroll.canScrollVertically(-1) ? 1f : 0f);
            down.setAlpha(scroll.canScrollVertically(1) ? 1f : 0f);
        };
        scroll.getViewTreeObserver().addOnScrollChangedListener(update::run);
        frame.getViewTreeObserver().addOnGlobalFocusChangeListener((o, n) -> update.run());
        frame.addOnLayoutChangeListener((v, a, b, c, d, e, f, g, h) -> {
            // Content shorter than the window: grow the tile rows so the page has no empty bottom.
            int viewport = scroll.getHeight();
            int used = content.getHeight();
            if (viewport > 0 && used > 0 && used < viewport && content.getTag() == null) {
                content.setTag("stretched");
                int rowsCount = 0;
                for (int i = 0; i < content.getChildCount(); i++) {
                    View s = content.getChildAt(i);
                    if (s instanceof LinearLayout) rowsCount += Math.max(0, ((LinearLayout) s).getChildCount() - 1);
                }
                if (rowsCount > 0) {
                    int add = (viewport - used) / rowsCount;
                    for (int i = 0; i < content.getChildCount(); i++) {
                        View s = content.getChildAt(i);
                        if (!(s instanceof LinearLayout)) continue;
                        LinearLayout sec = (LinearLayout) s;
                        for (int k = 1; k < sec.getChildCount(); k++) {
                            View row = sec.getChildAt(k);
                            if (row instanceof LinearLayout) {
                                for (int t = 0; t < ((LinearLayout) row).getChildCount(); t++) {
                                    View tile = ((LinearLayout) row).getChildAt(t);
                                    tile.setMinimumHeight(tile.getMinimumHeight() + add);
                                }
                            }
                        }
                    }
                }
            }
            update.run();
        });
        frame.post(update);
    }

    private static boolean isDescendant(View ancestor, View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) {
            if (p == ancestor) return true;
        }
        return false;
    }

    private static TextView arrow(Context ctx, String glyph, int gravity) {
        TextView t = UiKit.bold(ctx, 24, UiKit.INK);
        t.setText(glyph);
        t.setGravity(Gravity.CENTER);
        t.setBackground(new Glass(t, UiKit.pxf(22), Glass.Style.CONTROL));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(UiKit.px(44), UiKit.px(32), gravity);
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) lp.height = UiKit.px(44);
        t.setLayoutParams(lp);
        t.setAlpha(0.18f);
        return t;
    }

    public static String themeName(Context ctx, ThemeManager.Theme t) {
        int id = ctx.getResources().getIdentifier("theme_" + t.id.replace('-', '_'), "string", ctx.getPackageName());
        return id != 0 ? ctx.getString(id) : t.name;
    }

    static int dnsRes(String choice) {
        if (com.cinetv.launcher.device.NetworkOptimizer.DNS_ADGUARD.equals(choice)) return R.string.dns_adguard;
        if (com.cinetv.launcher.device.NetworkOptimizer.DNS_CLOUDFLARE.equals(choice)) return R.string.dns_cloudflare;
        return R.string.dns_off;
    }

    private static int perfModeRes(String m) {
        if (PerformanceProfile.MODE_LOW.equals(m)) return R.string.perf_low;
        if (PerformanceProfile.MODE_MID.equals(m)) return R.string.perf_mid;
        if (PerformanceProfile.MODE_HIGH.equals(m)) return R.string.perf_high;
        return R.string.perf_auto;
    }

    private static int tierRes(PerformanceProfile.Tier t) {
        switch (t) {
            case LOW: return R.string.tier_low;
            case HIGH: return R.string.tier_high;
            default: return R.string.tier_mid;
        }
    }

    // ---- Performance --------------------------------------------------------------------------------

    /** Low, Medium or High, each adapting by itself; or a benchmark picks one. */
    private static View buildPerformancePicker(final Context ctx, final ModalHost host, final Actions a) {
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Controls.header(ctx, ctx.getString(R.string.performance)));
        String current = PerformanceProfile.mode(ctx);
        String[][] modes = {
                {PerformanceProfile.MODE_LOW, ctx.getString(R.string.perf_low), ctx.getString(R.string.perf_low_hint)},
                {PerformanceProfile.MODE_MID, ctx.getString(R.string.perf_mid), ctx.getString(R.string.perf_mid_hint)},
                {PerformanceProfile.MODE_HIGH, ctx.getString(R.string.perf_high), ctx.getString(R.string.perf_high_hint)}};
        for (final String[] m : modes) {
            root.addView(Controls.tile(ctx, m[1], m[2], m[0].equals(current), v -> {
                host.back();
                if (m[0].equals(PerformanceProfile.mode(ctx))) return;
                PerformanceProfile.setMode(ctx, m[0]);
                a.onPerformanceChanged();
            }));
        }
        root.addView(Controls.tile(ctx, ctx.getString(R.string.perf_detect),
                ctx.getString(R.string.perf_detect_hint, ctx.getString(tierRes(PerformanceProfile.detectedTier(ctx)))), false, v -> {
                    final TextView status = UiKit.label(ctx, 24, UiKit.INK_DIM);
                    status.setText(R.string.perf_detecting);
                    status.setPadding(0, UiKit.px(18), 0, 0);
                    root.addView(status);
                    new Thread(() -> {
                        final PerformanceProfile.Result r = PerformanceProfile.benchmark(ctx);
                        status.post(() -> {
                            status.setText(ctx.getString(R.string.perf_detected, ctx.getString(perfModeRes(PerformanceProfile.modeFor(r.tier))),
                                    r.cpuScore, r.drawScore, r.freeRamMb, r.totalRamMb));
                            status.postDelayed(() -> {
                                host.back();
                                a.onPerformanceChanged();
                            }, 2500);
                        });
                    }, "cinetv-perf").start();
                }));
        root.setLayoutParams(new LinearLayout.LayoutParams(UiKit.px(680), ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    // ---- Inputs ------------------------------------------------------------------------------------

    /** The TV's inputs by the names devices give themselves, folding empty ports. */
    public static void inputs(final Context ctx, final ModalHost host) {
        inputsView(ctx, host, false);
    }

    private static void inputsView(final Context ctx, final ModalHost host, final boolean showAll) {
        host.push(() -> {
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.addView(Controls.header(ctx, ctx.getString(R.string.inputs)));
            List<com.cinetv.launcher.device.TvSources.Source> all = com.cinetv.launcher.device.TvSources.list(ctx);
            List<com.cinetv.launcher.device.TvSources.Source> connected = new ArrayList<>();
            List<com.cinetv.launcher.device.TvSources.Source> empty = new ArrayList<>();
            for (com.cinetv.launcher.device.TvSources.Source s : all) {
                if (s.connected || s.inputId == null) connected.add(s);
                else empty.add(s);
            }
            List<com.cinetv.launcher.device.TvSources.Source> toShow = (showAll || empty.isEmpty()) ? all : connected;
            for (final com.cinetv.launcher.device.TvSources.Source s : toShow) {
                String state = s.detail != null ? s.detail : null;
                if (s.connected && s.inputId != null) state = (state != null ? state + " · " : "") + ctx.getString(R.string.input_connected);
                root.addView(Controls.tile(ctx, s.name, state, false, v -> {
                    host.close();
                    com.cinetv.launcher.device.TvSources.open(ctx, s);
                }));
            }
            if (!showAll && !empty.isEmpty()) {
                boolean tr = "tr".equals(Locale.getDefault().getLanguage());
                root.addView(Controls.tile(ctx, tr ? "Diğer Girişler (" + empty.size() + " Boş Port)" : "Other Inputs (" + empty.size() + " Empty)",
                        tr ? "Boşta olan portları göster" : "Show unconnected ports", false, v -> {
                    host.replace(() -> buildInputsRoot(ctx, host, true));
                }));
            }
            root.addView(Controls.tile(ctx, ctx.getString(R.string.input_menu), null, false, v -> {
                host.close();
                AppLauncher.openTvInputs(ctx);
            }));
            root.setLayoutParams(new LinearLayout.LayoutParams(UiKit.px(640), ViewGroup.LayoutParams.WRAP_CONTENT));
            return root;
        });
    }

    private static View buildInputsRoot(final Context ctx, final ModalHost host, final boolean showAll) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Controls.header(ctx, ctx.getString(R.string.inputs)));
        List<com.cinetv.launcher.device.TvSources.Source> all = com.cinetv.launcher.device.TvSources.list(ctx);
        List<com.cinetv.launcher.device.TvSources.Source> connected = new ArrayList<>();
        List<com.cinetv.launcher.device.TvSources.Source> empty = new ArrayList<>();
        for (com.cinetv.launcher.device.TvSources.Source s : all) {
            if (s.connected || s.inputId == null) connected.add(s);
            else empty.add(s);
        }
        List<com.cinetv.launcher.device.TvSources.Source> toShow = (showAll || empty.isEmpty()) ? all : connected;
        for (final com.cinetv.launcher.device.TvSources.Source s : toShow) {
            String state = s.detail != null ? s.detail : null;
            if (s.connected && s.inputId != null) state = (state != null ? state + " · " : "") + ctx.getString(R.string.input_connected);
            root.addView(Controls.tile(ctx, s.name, state, s.connected, v -> {
                host.close();
                com.cinetv.launcher.device.TvSources.open(ctx, s);
            }));
        }
        root.addView(Controls.tile(ctx, ctx.getString(R.string.input_menu), null, false, v -> {
            host.close();
            AppLauncher.openTvInputs(ctx);
        }));
        root.setLayoutParams(new LinearLayout.LayoutParams(UiKit.px(640), ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    // ---- Sleep timer and quiet hours ----------------------------------------------------------

    private static String nightState(Context ctx) {
        int left = com.cinetv.launcher.device.NightCare.sleepMinutesLeft(ctx);
        String sleep = left > 0 ? ctx.getString(R.string.sleep_in, left) : ctx.getString(R.string.sleep_off);
        return com.cinetv.launcher.device.NightCare.quietHours(ctx) ? sleep + " · " + ctx.getString(R.string.quiet_on_short) : sleep;
    }

    private static View buildNight(final Context ctx, final ModalHost host, final Actions a) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Controls.header(ctx, ctx.getString(R.string.sleep_timer)));
        final int left = com.cinetv.launcher.device.NightCare.sleepMinutesLeft(ctx);
        for (final int m : com.cinetv.launcher.device.NightCare.SLEEP_CHOICES) {
            final String name = m == 0 ? ctx.getString(R.string.sleep_off) : ctx.getString(R.string.sleep_minutes, m);
            final boolean on = m == 0 ? left == 0 : left > 0 && Math.abs(left - m) < 1;
            root.addView(Controls.tile(ctx, name, null, on, v -> {
                com.cinetv.launcher.device.NightCare.setSleep(ctx, m);
                host.replace(() -> buildNight(ctx, host, a));
            }));
        }
        final boolean quiet = com.cinetv.launcher.device.NightCare.quietHours(ctx);
        root.addView(Controls.tile(ctx, ctx.getString(R.string.quiet_hours), ctx.getString(quiet ? R.string.quiet_hours_on : R.string.state_off), quiet, v -> {
            com.cinetv.launcher.device.NightCare.setQuietHours(ctx, !com.cinetv.launcher.device.NightCare.quietHours(ctx));
            host.replace(() -> buildNight(ctx, host, a));
        }));
        root.setLayoutParams(new LinearLayout.LayoutParams(UiKit.px(640), ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    // ---- Remove CineTV ------------------------------------------------------------------------------

    private static View buildDestroy(final Context ctx, final ModalHost host) {
        // First layer: red alarm glow along the screen edges, blinking; then a second "Are you sure?" layer.
        host.setAlarm(true);
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.bold(ctx, 34, Brand.TV_RED);
        title.setText(R.string.destroy_title);
        root.addView(title);
        TextView body = UiKit.label(ctx, 21, UiKit.INK_DIM);
        body.setText(R.string.destroy_body);
        body.setLineSpacing(UiKit.pxf(7), 1f);
        body.setPadding(0, UiKit.px(14), 0, UiKit.px(26));
        body.setMaxWidth(UiKit.px(760));
        root.addView(body);
        LinearLayout row = new LinearLayout(ctx);
        row.addView(Controls.button(ctx, ctx.getString(R.string.destroy_cancel), v -> host.back()));
        View sp = new View(ctx);
        row.addView(sp, new LinearLayout.LayoutParams(UiKit.px(16), 1));
        row.addView(Controls.alarmButton(ctx, ctx.getString(R.string.destroy_confirm), v -> host.push(() -> buildDestroyAreYouSure(ctx, host, tr))));
        root.addView(row);
        return root;
    }

    /** The second layer of Remove CineTV: "Emin misin?" with Yes / No. */
    private static View buildDestroyAreYouSure(final Context ctx, final ModalHost host, boolean tr) {
        host.setAlarm(true);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = UiKit.bold(ctx, 46, Brand.TV_RED);
        title.setText(tr ? "Emin misin?" : "Are you sure?");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView body = UiKit.label(ctx, 22, UiKit.INK_DIM);
        body.setText(tr ? "CineTV bu televizyondan kaldırılacak. Bu işlem geri alınamaz."
                : "CineTV will be removed from this TV. This cannot be undone.");
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, UiKit.px(14), 0, UiKit.px(30));
        body.setMaxWidth(UiKit.px(680));
        root.addView(body);
        LinearLayout row = new LinearLayout(ctx);
        final TextView no = Controls.button(ctx, tr ? "Hayır" : "No", v -> host.back());
        row.addView(no);
        View sp = new View(ctx);
        row.addView(sp, new LinearLayout.LayoutParams(UiKit.px(24), 1));
        row.addView(Controls.alarmButton(ctx, tr ? "Evet, Yok Et" : "Yes, Remove", v -> {
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                String problem = com.cinetv.launcher.device.Uninstaller.run(app);
                v.post(() -> {
                    if (problem != null) Toast.makeText(ctx, problem, Toast.LENGTH_LONG).show();
                    else host.close();
                });
            }, "cinetv-remove").start();
        }));
        root.addView(row);
        // Safe default: focus lands on "No".
        root.post(no::requestFocus);
        return root;
    }

    /** CineTV's author; opened from the signature at the top left of Home. */
    public static void about(Context ctx, ModalHost host, Actions a) {
        host.push(() -> buildAbout(ctx, host, a));
    }

    // ---- About ------------------------------------------------------------------------------------

    /** CineTV's author: photo and bio from TMDB, and QR codes to follow him from a phone. */
    private static View buildAbout(final Context ctx, final ModalHost host, final Actions a) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout root = new LinearLayout(ctx);
        root.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        final ImageView photo = new ImageView(ctx);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setClipToOutline(true);
        photo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        photo.setBackground(new Glass(photo, UiKit.pxf(72), Glass.Style.CONTROL));
        try (java.io.InputStream is = ctx.getAssets().open("remote/creator.jpg")) {
            Bitmap localPortrait = android.graphics.BitmapFactory.decodeStream(is);
            if (localPortrait != null) photo.setImageBitmap(localPortrait);
        } catch (Throwable ignored) {}
        headerRow.addView(photo, new LinearLayout.LayoutParams(UiKit.px(136), UiKit.px(136)));

        LinearLayout titleCol = new LinearLayout(ctx);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(UiKit.px(22), 0, 0, 0);

        TextView name = UiKit.bold(ctx, 36, UiKit.INK);
        name.setText(com.cinetv.launcher.service.Creator.NAME);
        titleCol.addView(name);

        TextView handlePill = UiKit.bold(ctx, 16, Color.parseColor("#F472B6"));
        handlePill.setText("Instagram: @" + com.cinetv.launcher.service.Creator.INSTAGRAM + "  ·  YouTube: " + com.cinetv.launcher.service.Creator.YOUTUBE);
        handlePill.setPadding(0, UiKit.px(4), 0, UiKit.px(4));
        titleCol.addView(handlePill);

        TextView role = UiKit.bold(ctx, 14, Color.parseColor("#C4B5FD"));
        role.setLetterSpacing(0.09f);
        role.setText(ctx.getString(R.string.about_role).toUpperCase(Locale.getDefault()));
        titleCol.addView(role);

        headerRow.addView(titleCol);
        left.addView(headerRow);

        final TextView bio = UiKit.label(ctx, 18, UiKit.INK_DIM);
        bio.setText(R.string.about_bio);
        bio.setLineSpacing(UiKit.pxf(6), 1.08f);
        bio.setPadding(0, UiKit.px(18), 0, UiKit.px(20));
        bio.setMaxWidth(UiKit.px(620));
        left.addView(bio);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.addView(Controls.button(ctx, tr ? "▶ Kısa Filmi 'Sessiz'i İzle" : "▶ Watch Short Film 'Sessiz'", v -> {
            host.close();
            AppLauncher.openYouTubeVideo(ctx, com.cinetv.launcher.service.Creator.SESSIZ_YOUTUBE_URL);
        }));
        View sp1 = new View(ctx);
        buttons.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(12), 1));
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.his_films), v -> {
            host.close();
            a.search(com.cinetv.launcher.service.Creator.NAME + " filmleri");
        }));
        View sp2 = new View(ctx);
        buttons.addView(sp2, new LinearLayout.LayoutParams(UiKit.px(12), 1));
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.done), v -> host.back()));
        left.addView(buttons);
        root.addView(left, new LinearLayout.LayoutParams(UiKit.px(630), ViewGroup.LayoutParams.WRAP_CONTENT));

        // Dual QR plates: 1) Direct Instagram @alptugdag QR for zero-friction follow, 2) Full Portfolio & Career links
        LinearLayout qrRow = new LinearLayout(ctx);
        qrRow.setOrientation(LinearLayout.HORIZONTAL);
        qrRow.setGravity(Gravity.CENTER_VERTICAL);

        View igQr = followCode(ctx, com.cinetv.launcher.service.Creator.INSTAGRAM_URL,
                tr ? "Instagram'da Takip Et" : "Follow on Instagram",
                "@alptugdag · Film & İletişim", 220, 36);
        qrRow.addView(igQr);

        String base = RemotePairing.baseUrl();
        String page = base == null ? com.cinetv.launcher.service.Creator.LINKEDIN_URL : base + "/me";
        View allQr = followCode(ctx, page,
                tr ? "Portfolyo & İş Birliği" : "Portfolio & Career",
                "LinkedIn · YouTube · Letterboxd", 220, 24);
        qrRow.addView(allQr);

        root.addView(qrRow);

        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            String url = MovieCatalog.personPhoto(app, com.cinetv.launcher.service.Creator.TMDB_PERSON);
            Bitmap img = url.isEmpty() ? null : com.cinetv.launcher.service.PosterCache.load(app, url, UiKit.px(180), UiKit.px(180));
            bio.post(() -> {
                if (img != null) photo.setImageBitmap(img);
            });
        }, "cinetv-about").start();
        return root;
    }

    private static View followCode(Context ctx, String url, String site, String handle) {
        return followCode(ctx, url, site, handle, 280, 48);
    }

    private static View followCode(Context ctx, String url, String site, String handle, int sizeDp, int leftMarginDp) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout plate = new FrameLayout(ctx);
        plate.setBackground(new Glass(plate, UiKit.pxf(24), Glass.Style.FOCUSED));
        int size = UiKit.px(sizeDp);
        ImageView qr = new ImageView(ctx);
        Bitmap code = RemotePairing.qr(url, size, Color.parseColor("#0B1220"), Color.WHITE);
        if (code != null) qr.setImageBitmap(code);
        plate.addView(qr, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        plate.setPadding(UiKit.px(14), UiKit.px(14), UiKit.px(14), UiKit.px(14));
        col.addView(plate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView label = UiKit.bold(ctx, 19, UiKit.INK);
        label.setText(site);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, UiKit.px(12), 0, 0);
        col.addView(label);
        TextView h = UiKit.label(ctx, 15, UiKit.INK_DIM);
        h.setText(handle);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setMaxWidth(size + UiKit.px(28));
        col.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.px(leftMarginDp), 0, 0, 0);
        col.setLayoutParams(lp);
        return col;
    }

    // ---- Pairing (QR) -----------------------------------------------------------------------------

    private static View buildPairing(final Context ctx, final ModalHost host, final String section) {
        LinearLayout root = new LinearLayout(ctx);
        root.setGravity(Gravity.CENTER_VERTICAL);
        // the QR code opens the phone page on GitHub, linked to this TV directly (any network, any browser);
        // the local address stays written below for phones on the same Wi-Fi
        String url = RemotePairing.baseUrl() == null && !online(ctx) ? null : com.cinetv.launcher.remote.RelayHost.pairingUrl(ctx, section);

        FrameLayout qrPlate = new FrameLayout(ctx);
        qrPlate.setBackground(new Glass(qrPlate, UiKit.pxf(28), Glass.Style.FOCUSED));
        ImageView qr = new ImageView(ctx);
        int size = UiKit.px(380);
        Bitmap code = url == null ? null : RemotePairing.qr(url, size, Color.parseColor("#0B1220"), Color.WHITE);
        if (code != null) qr.setImageBitmap(code);
        qrPlate.addView(qr, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        qrPlate.setPadding(UiKit.px(22), UiKit.px(22), UiKit.px(22), UiKit.px(22));
        root.addView(qrPlate);

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(UiKit.px(48), 0, 0, 0);
        TextView title = UiKit.bold(ctx, 36, UiKit.INK);
        title.setText("cal".equals(section) ? R.string.calibration : "cast".equals(section) ? R.string.cast_from_phone
                : "background".equals(section) ? R.string.background : R.string.connect_phone);
        text.addView(title);
        TextView body = UiKit.label(ctx, 22, UiKit.INK_DIM);
        body.setText("cal".equals(section) ? R.string.pair_body_cal : "cast".equals(section) ? R.string.pair_body_cast
                : "background".equals(section) ? R.string.pair_body_background : R.string.pair_body);
        body.setLineSpacing(UiKit.pxf(8), 1f);
        body.setPadding(0, UiKit.px(16), 0, UiKit.px(16));
        body.setMaxWidth(UiKit.px(620));
        text.addView(body);
        TextView link = UiKit.label(ctx, 18, UiKit.INK_FAINT);
        link.setText(url == null ? ctx.getString(R.string.no_network) : RemotePairing.baseUrl());
        text.addView(link);
        if (url != null) {
            TextView trouble = UiKit.label(ctx, 17, UiKit.INK_FAINT);
            trouble.setText(R.string.pair_trouble);
            trouble.setMaxWidth(UiKit.px(620));
            trouble.setPadding(0, UiKit.px(10), 0, 0);
            text.addView(trouble);
        }
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setPadding(0, UiKit.px(26), 0, 0);
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.done), v -> host.back()));
        View sp = new View(ctx);
        buttons.addView(sp, new LinearLayout.LayoutParams(UiKit.px(16), 1));
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.new_code), v -> {
            RemotePairing.rotate(ctx);
            host.replace(() -> buildPairing(ctx, host, section));
        }));
        text.addView(buttons);
        root.addView(text);
        return root;
    }

    private static boolean online(Context ctx) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.getActiveNetwork() != null;
        } catch (Throwable t) {
            return true;
        }
    }

    public static void pairing(Context ctx, ModalHost host, String section) {
        host.push(() -> buildPairing(ctx, host, section));
    }

    public static void changelog(Context ctx, ModalHost host) {
        host.push(() -> buildChangelog(ctx, host));
    }

    private static View buildChangelog(Context ctx, ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(UiKit.px(44), UiKit.px(32), UiKit.px(44), UiKit.px(32));
        outer.setBackground(new Glass(outer, UiKit.pxf(24), Glass.Style.PANEL));

        TextView title = UiKit.bold(ctx, 24, UiKit.INK);
        title.setText(tr ? "CineTV v1.0.0.19 — Yenilikler ve Değişiklikler" : "CineTV v1.0.0.19 — What's New");
        outer.addView(title);

        TextView sub = UiKit.label(ctx, 14, UiKit.INK_DIM);
        sub.setText(tr ? "Alptuğ Dağ İmzasıyla En Son CineTV Sürümündeki Geliştirmeler" : "Latest updates crafted by Alptuğ Dağ");
        sub.setPadding(0, UiKit.px(6), 0, UiKit.px(16));
        outer.addView(sub);

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        String[][] items = {
            {(tr ? "Fiziksel Titanyum & Pirinç Film Öneri Ruleti" : "Physical Titanium & Brass Movie Roulette"),
             tr ? "Hem TV'de hem mobilde senkronize çalışan fotogerçekçi, altın perçinli, kadife tonlu dilimli ve yaylı ibreli sinema ruleti."
                : "Synchronized photorealistic cinema roulette wheel with titanium rim, golden rivets, velvet slices, and tactile deflection physics."},
            {(tr ? "35mm Selüloit Film Şeridi Sinefil Yılanı" : "35mm Celluloid Filmstrip Snake Game"),
             tr ? "Retina ekranlara özel Kodak delikli selüloit kareler, vintage lens başlığı, 3D selefon parlamalı minyatür DVD hedefleri ve gyro eğim kontrolü."
                : "Kodak 35mm perforated celluloid filmstrip, vintage lens camera head, 3D cellophane DVD cases, and responsive gyro tilt steering."},
            {(tr ? "10 Soruluk Sinefil Bilmece Arenası" : "10-Question Cinephile Quiz Arena"),
             tr ? "Oturma odasındaki herkesin telefonundan anlık katılabildiği, festival bileti tasarımlı 10 soruluk gerçek zamanlı sinefil yarışması."
                : "Multi-player real-time cinema quiz show where all connected smartphones compete live on the TV screen."},
            {(tr ? "CineSub: İnteraktif Kültürel Altyazı Rehberi" : "CineSub: Interactive Cultural Subtitle Companion"),
             tr ? "Sahneyi durdurmadan altyazıda geçen tarihi olayları, felsefi kavramları ve yönetmen göndermelerini TV köşesinde mini kart olarak açar."
                : "Instant pop-up cultural annotation cards for historical context, philosophy, and director tributes without pausing playback."},
            {(tr ? "Yerel Donanım Hızlandırmalı Fragman Oynatıcı" : "Native Hardware-Accelerated Trailer Engine"),
             tr ? "YouTube UI barındırmayan, doğrudan donanım seviyesinde H.264/AAC ExoPlayer motoru. Anında başlar, pürüzsüz loop eder ve disk önbelleği kullanır."
                : "Zero YouTube UI, sub-second start time, native seamless looping, minimal CPU/GPU usage via Media3 ExoPlayer with disk prefetch."},
            {(tr ? "Tek Sayfa Ayarlar & Tema Uyumlu iOS Switchler" : "Single-Page Settings & Theme-Adaptive iOS Switches"),
             tr ? "Tüm ayarlar bölümlere ayrılmadan tek sayfada toplandı. Üst hızlı kategori hapları, tema rengine bürünen iOS switchler ve dairesel gezinme eklendi."
                : "All settings unified on a single continuous page with top category jump pills, theme-matched switches and wrap-around navigation."},
            {(tr ? "Nuvio & YouTube Geri Tuşu Doğrudan Eve Dönüş" : "Direct Home Navigation on Back in Nuvio & YouTube"),
             tr ? "Nuvio veya YouTube'dan çıkarken eski izleme geçmişi pencerelerinin ardı ardına ekrana gelmesi engellendi, tek dokunuşla doğrudan ana ekrana döner."
                : "Back key in Nuvio/YouTube instantly returns to CineTV Home without rewinding video/screen history."},
            {(tr ? "Android & iOS Bağımsız Web Uygulaması (PWA / APK)" : "Android & iOS Standalone App (PWA / APK)"),
             tr ? "QR kod ile açılan mobil kumanda, hem iPhone hem Android'de tarayıcı çerçevesi olmadan tam ekran bağımsız CineTV uygulaması olarak ana ekrana yüklenir."
                : "The mobile remote can now be installed as a full-screen standalone application on both iOS & Android with CineTV icon."},
            {(tr ? "Apple Siri, Google Home & Amazon Alexa Entegrasyonu" : "Apple Siri, Google Home & Alexa Integration"),
             tr ? "Siri Kestirmeleri, Google Home ve Alexa üzerinden CineTV ses açma, film arama, kapatma ve oynatma komutları tam yerel çalışır."
                : "Control CineTV through Siri Shortcuts, Google Home and Alexa via local zero-cloud API endpoints."},
            {(tr ? "Apple TV Tarzı Sıfır Gecikmeli Akustik Sistem Sesleri" : "Apple-Style Zero-Latency Synthesized UI Audio"),
             tr ? "Kumandayla menüde gezinirken, switch açıp kapatırken ve vitrinde odak değişirken Apple TV hissiyatında tatlı tık ve dokunuş sesleri."
                : "Subtle, synthesized low-latency audio feedback on focus movements, tile clicks, switch toggles and modals."},
            {(tr ? "Cihaz Donanım Seviyelerine Göre Akıllı Optimizasyon" : "Smart Hardware Tier Optimization (Low / Mid / High)"),
             tr ? "RAM ve GPU gücüne göre efektler otomatik ölçeklenir; düşük donanımlı cihazlarda dahi takılmasız, sıfır kasmayla sabit 60 FPS sinema keyfi."
                : "Dynamic visual scaling for Low (<=1.5GB), Mid (2GB) and High (>2.5GB) hardware tiers guaranteeing fluid 60 FPS."},
            {(tr ? "Akıllı Gece İzleme Modu" : "Smart Night Viewing Mode"),
             tr ? "Gece saatlerinde beliren onay sorgusu ile sıcak amber göz koruma tonu ve fısıltı ses seviyesi ayrı ayrı açılıp kapatılabilir."
                : "Separate warm amber visual tone and dynamic volume whisper leveling during night hours."},
            {(tr ? "Gamze AI: Sosyal Bağlam ve Nüktedan Diyaloglar" : "Gamze AI: Witty Social Context & Couple Recommendations"),
             tr ? "Kiminle izlediğinize duyarlı, esprili ve zeki film önerileri; ailece, sevgiliyle veya tek başına izleme durumuna göre anlık kürasyon."
                : "Clever, socially aware recommendations tailored to family, couple or solo viewing moments."},
            {(tr ? "Yüzen Cam Güncelleme Bildirimi" : "Floating Update Banner"),
             tr ? "TV kapalıyken ekranı uyararak güncellemeyi haber verir, dokunulmazsa TV'yi tekrar standby uykusuna alır; yanıp sönen yeşil ok ile dikkat çeker."
                : "Floating glass banner with animated pulsing green arrow and guaranteed wake-to-display."}
        };

        for (int i = 0; i < items.length; i++) {
            String[] it = items[i];
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(UiKit.px(6), UiKit.px(10), UiKit.px(6), UiKit.px(10));

            TextView h = UiKit.bold(ctx, 16, Color.parseColor("#F59E0B"));
            h.setText(it[0]);
            row.addView(h);

            TextView d = UiKit.label(ctx, 13, UiKit.INK);
            d.setText(it[1]);
            d.setPadding(0, UiKit.px(3), 0, 0);
            row.addView(d);

            list.addView(row);

            // Clean hairline divider between items - NO nested boxes!
            if (i < items.length - 1) {
                View divider = new View(ctx);
                divider.setBackgroundColor(Color.argb(25, 255, 255, 255));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(1));
                dlp.setMargins(0, UiKit.px(4), 0, UiKit.px(4));
                list.addView(divider, dlp);
            }
        }

        sv.addView(list);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(UiKit.px(760), UiKit.px(420));
        svLp.bottomMargin = UiKit.px(16);
        outer.addView(sv, svLp);

        View btnClose = Controls.button(ctx, tr ? "Tamam, Sinemaya Dön" : "Done, Return to Cinema", v -> host.back());
        btnClose.setFocusable(true);
        outer.addView(btnClose, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(54)));
        btnClose.requestFocus();

        return outer;
    }

    // ---- Inputs -----------------------------------------------------------------------------------

    private static EditText field(Context ctx, String value, int hintRes, boolean secret) {
        EditText et = new EditText(ctx);
        et.setTextColor(UiKit.INK);
        et.setHintTextColor(UiKit.INK_FAINT);
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.pxf(24));
        et.setSingleLine(true);
        et.setHint(hintRes);
        if (value != null) et.setText(value);
        et.setPadding(UiKit.px(24), UiKit.px(18), UiKit.px(24), UiKit.px(18));
        et.setBackground(new Glass(et, UiKit.pxf(22), Glass.Style.CONTROL));
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (secret) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        return et;
    }

    private static View form(Context ctx, int titleRes, int bodyRes, EditText input, View.OnClickListener onSave) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(titleRes);
        root.addView(title);
        TextView body = UiKit.label(ctx, 20, UiKit.INK_DIM);
        body.setText(bodyRes);
        body.setPadding(0, UiKit.px(12), 0, UiKit.px(24));
        body.setMaxWidth(UiKit.px(760));
        root.addView(body);
        root.addView(input, new LinearLayout.LayoutParams(UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setPadding(0, UiKit.px(26), 0, 0);
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.save), onSave));
        root.addView(buttons);
        input.setOnEditorActionListener((v, id, e) -> {
            onSave.onClick(v);
            return true;
        });
        return root;
    }

    static View buildTtsSettings(final Context ctx, final ModalHost host, final Actions a) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(28), UiKit.px(36), UiKit.px(28));

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(tr ? "Gamze Sesli Asistan (Gamze AI & EMA TTS)" : "Gamze Voice Assistant (Gamze AI & EMA TTS)");
        root.addView(title);

        TextView sub = UiKit.label(ctx, 14, UiKit.INK_DIM);
        sub.setText(tr
                ? "Ölçülü, ağırbaşlı ve zeki sinematik asistanınız Gamze. Yalnızca ihtiyaç anında konuşur, asla bunaltıcı olmaz."
                : "A laconic, dignified cinematic assistant Gamze. Speaks only when asked, never overwhelming.");
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = UiKit.px(6);
        subLp.bottomMargin = UiKit.px(16);
        root.addView(sub, subLp);

        boolean enabled = Prefs.emaTts(ctx);
        int currentLevel = Prefs.ttsLevel(ctx);

        // 1. Master Switch
        String masterTitle = tr ? "Gamze Asistanı Etkinleştir" : "Enable Gamze Assistant";
        String masterSub = enabled
                ? (tr ? "Açık · Gamze seçtiğiniz düzeyde konuşur" : "On · Gamze speaks according to your chosen activity level")
                : (tr ? "Kapalı · Gamze tamamen sessizdir" : "Off · Gamze is completely silent");
        root.addView(Controls.tile(ctx, masterTitle, masterSub, enabled, v -> {
            boolean next = !Prefs.emaTts(ctx);
            Prefs.setFlag(ctx, Prefs.EMA_TTS, next);
            if (next) {
                com.cinetv.launcher.ai.EmaLightningTts.speak(ctx, tr ? "Merhaba, ben Gamze. Sinematik asistanınız hazır." : "Hello, I am Gamze. Your cinema assistant is ready.");
            } else {
                com.cinetv.launcher.ai.EmaLightningTts.stop();
            }
            host.replace(() -> buildTtsSettings(ctx, host, a));
        }));

        // Divider
        View sep = new View(ctx);
        sep.setBackgroundColor(Color.argb(35, 255, 255, 255));
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(1));
        sepLp.setMargins(0, UiKit.px(12), 0, UiKit.px(12));
        root.addView(sep, sepLp);

        TextView levelHeader = UiKit.bold(ctx, 18, UiKit.INK);
        levelHeader.setText(tr ? "Aktiflik Düzeyi" : "Activity Level");
        root.addView(levelHeader);

        // Level 1: Search only
        boolean isL1 = enabled && currentLevel == Prefs.TTS_LEVEL_SEARCH_ONLY;
        String l1Title = tr ? "Sadece Sesli Arama (Minimal)" : "Voice Search Only (Minimal)";
        String l1Sub = tr
                ? "Yalnızca mikrofonla arama yaptığınızda arama sonuçlarını ve cevapları seslendirir."
                : "Only speaks when you search via the microphone.";
        root.addView(Controls.tile(ctx, l1Title, l1Sub, isL1, v -> {
            Prefs.setFlag(ctx, Prefs.EMA_TTS, true);
            Prefs.setTtsLevel(ctx, Prefs.TTS_LEVEL_SEARCH_ONLY);
            com.cinetv.launcher.ai.EmaLightningTts.speak(ctx, tr ? "Aktiflik düzeyi: Sadece sesli arama." : "Level set to Voice search only.");
            host.replace(() -> buildTtsSettings(ctx, host, a));
        }));

        // Level 2: Balanced / Gamze Mode (Recommended)
        boolean isL2 = enabled && currentLevel == Prefs.TTS_LEVEL_BALANCED;
        String l2Title = tr ? "Gamze Modu: Akıllı Arama & AI (Önerilen)" : "Gamze Mode: Smart Search & AI (Recommended)";
        String l2Sub = tr
                ? "Hem sesli hem TV/telefon kumandasından yapılan yazılı aramalarda ve AI sorularında ölçülü, zeki geri bildirim verir."
                : "Provides concise, smart spoken feedback for voice, TV and phone remote searches, and answers AI queries.";
        root.addView(Controls.tile(ctx, l2Title, l2Sub, isL2, v -> {
            Prefs.setFlag(ctx, Prefs.EMA_TTS, true);
            Prefs.setTtsLevel(ctx, Prefs.TTS_LEVEL_BALANCED);
            com.cinetv.launcher.ai.EmaLightningTts.speak(ctx, tr ? "Gamze modu devrede." : "Gamze mode active.");
            host.replace(() -> buildTtsSettings(ctx, host, a));
        }));

        // Level 3: Proactive Assistant
        boolean isL3 = enabled && currentLevel == Prefs.TTS_LEVEL_PROACTIVE;
        String l3Title = tr ? "Gelişmiş Asistan (Proaktif)" : "Advanced Assistant (Proactive)";
        String l3Sub = tr
                ? "Aramalar ve AI yanıtlarına ek olarak TV açılışında kısa ve sıcak bir selamlama."
                : "Search & AI responses plus a brief greeting when the TV wakes.";
        root.addView(Controls.tile(ctx, l3Title, l3Sub, isL3, v -> {
            Prefs.setFlag(ctx, Prefs.EMA_TTS, true);
            Prefs.setTtsLevel(ctx, Prefs.TTS_LEVEL_PROACTIVE);
            com.cinetv.launcher.ai.EmaLightningTts.speak(ctx, tr ? "Gelişmiş asistan devrede." : "Advanced assistant active.");
            host.replace(() -> buildTtsSettings(ctx, host, a));
        }));

        // Divider
        View sep2 = new View(ctx);
        sep2.setBackgroundColor(Color.argb(35, 255, 255, 255));
        LinearLayout.LayoutParams sepLp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(1));
        sepLp2.setMargins(0, UiKit.px(12), 0, UiKit.px(12));
        root.addView(sep2, sepLp2);

        root.addView(Controls.button(ctx, tr ? "Gamze'nin Sesini Test Et" : "Test Gamze's Voice", v -> {
            com.cinetv.launcher.ai.EmaLightningTts.speak(ctx, tr
                    ? "Merhaba, ben Gamze. CineTV sinematik asistanınız emrinizde."
                    : "Hello, I am Gamze. Your CineTV cinema assistant is at your service.");
        }));

        return root;
    }

    private static View buildSecretInput(final Context ctx, final ModalHost host, final String name, int titleRes, int hintRes) {
        return buildSecretInput(ctx, host, name, titleRes, hintRes, null);
    }

    private static View buildSecretInput(final Context ctx, final ModalHost host, final String name, int titleRes, int hintRes, final Runnable onSaved) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final EditText et = field(ctx, null, hintRes, false);

        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = UiKit.bold(ctx, 30, UiKit.INK);
        title.setText(ctx.getString(titleRes) + (tr ? " (%100 Ücretsiz)" : " (100% Free)"));
        root.addView(title);

        String guideText;
        String qrUrl;
        String qrSite;
        String qrSub;
        if (SecretStore.AI_KEY.equals(name)) {
            guideText = tr
                    ? "• Tamamen Ücretsizdir: Google AI Studio (aistudio.google.com/apikey) üzerinden kredi kartı gerekmeden 30 saniyede ücretsiz alınır.\n"
                    + "• Ne İşe Yarar? Sesli ve yazılı doğal dil aramalarını (\"Nolan'ın uzay filmini aç\", \"Yağmurlu bir gece için gerilim öner\"), saate ve hava durumuna göre akıllı DVD vitrin küratörlüğünü ve Türkçe sinema asistanını çalıştırır.\n"
                    + "• Nasıl Girilir? Yandaki QR kodu telefonunuzla okutup \"Create API Key\" düğmesiyle anahtarınızı alın; kumandanızla veya CineTV Telefon Kumandası klavyesiyle aşağıya girip Kaydet'e basın."
                    : "• 100% Free: Get it in 30 seconds at Google AI Studio (aistudio.google.com/apikey) with no credit card required.\n"
                    + "• What It Unlocks: Natural-language voice & text film search, time/weather-aware smart DVD shelf curation, and your AI cinema assistant.\n"
                    + "• How to Enter: Scan the QR code with your phone to get your key, then type or paste it via the CineTV Phone Remote below.";
            qrUrl = "https://aistudio.google.com/apikey";
            qrSite = "Google AI Studio";
            qrSub = "aistudio.google.com/apikey";
        } else if (SecretStore.TMDB_KEY.equals(name)) {
            guideText = tr
                    ? "• Tamamen Ücretsizdir: The Movie Database (themoviedb.org/settings/api) üzerinden ücretsiz hesap açarak anında alınır.\n"
                    + "• Ne İşe Yarar? 3B DVD kutularının yüksek çözünürlüklü afişlerini, açılan DVD kutusunun içindeki Türkçe film özetlerini, oyuncu/yönetmen kadrosunu, IMDb puanlarını ve fragman videolarını getirir.\n"
                    + "• Nasıl Girilir? Yandaki QR kodu telefonunuzla okutup ücretsiz API (v3 auth) anahtarınızı alın ve aşağıya girip Kaydet'e basın."
                    : "• 100% Free: Create a free account at The Movie Database (themoviedb.org/settings/api) to get your API key.\n"
                    + "• What It Unlocks: High-res 3D DVD cover art, inner booklet synopses, cast/director credits, ratings, and live trailer playback.\n"
                    + "• How to Enter: Scan the QR code with your phone, copy your API v3 key, and enter it below.";
            qrUrl = "https://www.themoviedb.org/settings/api";
            qrSite = "TMDB API (Ücretsiz)";
            qrSub = "themoviedb.org/settings/api";
        } else {
            guideText = tr
                    ? "• Tamamen Ücretsizdir: mdblist.com/preferences adresinden ücretsiz alınır.\n"
                    + "• Ne İşe Yarar? IMDb, Rotten Tomatoes, Metacritic ve Letterboxd puanlarını ve özel film listelerini zenginleştirir."
                    : "• 100% Free: Available at mdblist.com/preferences to enrich IMDb, Rotten Tomatoes & Letterboxd ratings.";
            qrUrl = "https://mdblist.com/preferences/";
            qrSite = "MDBList API";
            qrSub = "mdblist.com/preferences";
        }

        TextView body = UiKit.label(ctx, 18, UiKit.INK_DIM);
        body.setText(guideText);
        body.setLineSpacing(UiKit.pxf(6), 1.12f);
        body.setPadding(0, UiKit.px(12), 0, UiKit.px(20));
        body.setMaxWidth(UiKit.px(740));
        root.addView(body);

        root.addView(et, new LinearLayout.LayoutParams(UiKit.px(740), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, UiKit.px(22), 0, 0);

        final View.OnClickListener saveAction = v -> {
            String val = et.getText().toString().trim();
            if (!val.isEmpty()) {
                boolean ok = SecretStore.put(ctx, name, val);
                Toast.makeText(ctx, ok ? R.string.saved_securely : R.string.save_failed, Toast.LENGTH_SHORT).show();
            }
            host.back();
            if (onSaved != null) v.postDelayed(onSaved, 200);
        };

        buttons.addView(Controls.button(ctx, ctx.getString(R.string.save), saveAction));
        View sp = new View(ctx);
        buttons.addView(sp, new LinearLayout.LayoutParams(UiKit.px(16), 1));
        buttons.addView(Controls.button(ctx, tr ? "Geri Dön" : "Back", v -> host.back()));
        root.addView(buttons);

        et.setOnEditorActionListener((v, id, e) -> {
            saveAction.onClick(v);
            return true;
        });

        outer.addView(root, new LinearLayout.LayoutParams(UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(followCode(ctx, qrUrl, qrSite, qrSub));
        return outer;
    }

    private static View buildCityInput(final Context ctx, final ModalHost host, final Actions a) {
        final EditText et = field(ctx, Prefs.city(ctx), R.string.weather_city_hint, false);
        return form(ctx, R.string.weather_city, R.string.weather_city_body, et, v -> {
            Prefs.putGlobal(ctx, Prefs.GLOBAL_CITY, et.getText().toString().trim());
            a.refreshWeather();
            host.back();
        });
    }

    private static View buildMoodPicker(final Context ctx, final ModalHost host, final Actions a) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(R.string.ai_curate);
        title.setPadding(0, 0, 0, UiKit.px(22));
        root.addView(title);
        String[] moods = ctx.getResources().getStringArray(R.array.moods);
        for (final String mood : moods) {
            root.addView(Controls.tile(ctx, mood, null, false, v -> {
                host.close();
                a.curate(mood);
            }), new LinearLayout.LayoutParams(UiKit.px(640), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        final EditText custom = field(ctx, null, R.string.mood_custom_hint, false);
        custom.setOnEditorActionListener((v, id, e) -> {
            String text = custom.getText().toString().trim();
            if (!text.isEmpty()) {
                host.close();
                a.curate(text);
            }
            return true;
        });
        root.addView(custom, new LinearLayout.LayoutParams(UiKit.px(640), ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    // ---- UI Scale & Font Size Modal with Live Preview -----------------------------------------------

    public static View buildScalePreviewModal(final Context ctx, final ModalHost host, final Actions a) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final int modalW = Math.round(dm.widthPixels * 0.84f);
        final int modalH = Math.round(dm.heightPixels * 0.82f);

        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setLayoutParams(new LinearLayout.LayoutParams(modalW, modalH));

        // Header
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = UiKit.bold(ctx, 24, UiKit.INK);
        title.setText(tr ? "Arayüz Ölçeği ve Yazı Boyutu Ayarı" : "UI Scale & Typography Size");
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeHint = UiKit.label(ctx, 13, UiKit.INK_DIM);
        closeHint.setText(tr ? "Geri: İptal · Seç: Canlı İzle" : "Back: Cancel · Select: Live Preview");
        header.addView(closeHint);
        root.addView(header);

        // Body: Two columns
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(0, UiKit.px(16), 0, UiKit.px(14));
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(body, bodyLp);

        final int[] curUi = new int[]{Prefs.uiScaleLevel(ctx)};
        final int[] curFont = new int[]{Prefs.fontScaleLevel(ctx)};

        // Right column: Live Interactive Preview Card
        final FrameLayout previewBox = new FrameLayout(ctx);
        previewBox.setBackground(new Glass(previewBox, UiKit.pxf(20), Glass.Style.CONTROL));
        previewBox.setPadding(UiKit.px(24), UiKit.px(20), UiKit.px(24), UiKit.px(20));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f);
        rightLp.leftMargin = UiKit.px(24);

        final Runnable updatePreview = () -> {
            previewBox.removeAllViews();
            float uiMultiplier = curUi[0] == 0 ? 0.88f : (curUi[0] == 2 ? 1.14f : (curUi[0] == 3 ? 1.28f : 1.0f));
            float fontMultiplier = curFont[0] == 0 ? 0.90f : (curFont[0] == 2 ? 1.15f : (curFont[0] == 3 ? 1.30f : 1.0f));

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Math.round(UiKit.px(18) * uiMultiplier), Math.round(UiKit.px(14) * uiMultiplier), Math.round(UiKit.px(18) * uiMultiplier), Math.round(UiKit.px(14) * uiMultiplier));

            TextView prevBadge = UiKit.bold(ctx, Math.round(12 * fontMultiplier), Color.parseColor("#34C759"));
            prevBadge.setText(tr ? "CANLI ÖNİZLEME · 60 FPS SİNEMA" : "LIVE PREVIEW · 60 FPS CINEMA");
            card.addView(prevBadge);

            TextView prevTitle = UiKit.bold(ctx, Math.round(26 * fontMultiplier), UiKit.INK);
            prevTitle.setText("Interstellar");
            prevTitle.setPadding(0, Math.round(UiKit.px(6) * uiMultiplier), 0, 0);
            card.addView(prevTitle);

            TextView prevMeta = UiKit.label(ctx, Math.round(14 * fontMultiplier), UiKit.INK_DIM);
            prevMeta.setText("2014 · 2s 49dk · IMDb 8.7 · 4K HDR · Dolby Atmos");
            prevMeta.setPadding(0, Math.round(UiKit.px(4) * uiMultiplier), 0, Math.round(UiKit.px(8) * uiMultiplier));
            card.addView(prevMeta);

            TextView prevPlot = UiKit.label(ctx, Math.round(14 * fontMultiplier), UiKit.INK);
            prevPlot.setLineSpacing(UiKit.pxf(3) * fontMultiplier, 1f);
            prevPlot.setText(tr ? "İnsanlığın geleceği tehlikeye girdiğinde, bir grup astronot yaşanabilir yeni bir gezegen bulmak için solucan deliğinden geçer."
                                : "When human survival is at stake, a team of explorers undertakes the most important mission in history: traveling beyond this galaxy.");
            card.addView(prevPlot);

            LinearLayout btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, Math.round(UiKit.px(14) * uiMultiplier), 0, 0);

            TextView btn1 = UiKit.bold(ctx, Math.round(14 * fontMultiplier), UiKit.INK);
            btn1.setText(tr ? "▶ Filmi Başlat" : "▶ Play Film");
            btn1.setPadding(Math.round(UiKit.px(16) * uiMultiplier), Math.round(UiKit.px(8) * uiMultiplier), Math.round(UiKit.px(16) * uiMultiplier), Math.round(UiKit.px(8) * uiMultiplier));
            btn1.setBackground(new Glass(btn1, UiKit.pxf(12), Glass.Style.CONTROL));
            btnRow.addView(btn1);

            TextView btn2 = UiKit.label(ctx, Math.round(14 * fontMultiplier), UiKit.INK);
            btn2.setText(tr ? "Fragman" : "Trailer");
            btn2.setPadding(Math.round(UiKit.px(16) * uiMultiplier), Math.round(UiKit.px(8) * uiMultiplier), Math.round(UiKit.px(16) * uiMultiplier), Math.round(UiKit.px(8) * uiMultiplier));
            btn2.setBackground(new Glass(btn2, UiKit.pxf(12), Glass.Style.CONTROL));
            LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            b2Lp.leftMargin = Math.round(UiKit.px(10) * uiMultiplier);
            btnRow.addView(btn2, b2Lp);

            card.addView(btnRow);
            previewBox.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        };

        // Left column: Scrollable Options
        ScrollView optScroll = new ScrollView(ctx);
        optScroll.setVerticalScrollBarEnabled(false);
        LinearLayout leftCol = new LinearLayout(ctx);
        leftCol.setOrientation(LinearLayout.VERTICAL);

        // Section A: Arayüz Ölçeği
        TextView lblUi = UiKit.bold(ctx, 16, UiKit.INK);
        lblUi.setText(tr ? "1. Arayüz Ölçeği" : "1. UI Scale");
        leftCol.addView(lblUi);

        final String[][] uiChoices = {
                {tr ? "Küçük (%88) · Kompakt" : "Small (88%) · Compact", "0"},
                {tr ? "Standart (%100) · Varsayılan" : "Standard (100%) · Default", "1"},
                {tr ? "Büyük (%114) · Rahat Okuma" : "Large (114%) · Comfy", "2"},
                {tr ? "Çok Büyük (%128) · Uzak Ekran" : "Extra Large (128%) · Living Room", "3"}
        };
        final View[] uiButtons = new View[4];
        for (int i = 0; i < uiChoices.length; i++) {
            final int idx = i;
            final TextView chip = UiKit.bold(ctx, 14, curUi[0] == idx ? Color.parseColor("#34C759") : UiKit.INK);
            chip.setText((curUi[0] == idx ? "● " : "○ ") + uiChoices[i][0]);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setPadding(UiKit.px(14), UiKit.px(8), UiKit.px(14), UiKit.px(8));
            final Glass cg = new Glass(chip, UiKit.pxf(14), curUi[0] == idx ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            chip.setBackground(cg);
            chip.setOnFocusChangeListener((v, has) -> {
                if (has) com.cinetv.launcher.device.SystemAudio.tick(ctx);
                cg.setStyle(has ? Glass.Style.FOCUSED : (curUi[0] == idx ? Glass.Style.FOCUSED : Glass.Style.CONTROL));
                chip.setTextColor(has ? UiKit.INK_DARK : (curUi[0] == idx ? Color.parseColor("#34C759") : UiKit.INK));
                UiKit.lift(v, has, 1.04f, 120);
            });
            chip.setOnClickListener(v -> {
                com.cinetv.launcher.device.SystemAudio.click(ctx);
                curUi[0] = idx;
                for (int j = 0; j < 4; j++) {
                    ((TextView) uiButtons[j]).setText((curUi[0] == j ? "● " : "○ ") + uiChoices[j][0]);
                    ((TextView) uiButtons[j]).setTextColor(curUi[0] == j ? Color.parseColor("#34C759") : UiKit.INK);
                }
                updatePreview.run();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = UiKit.px(5);
            leftCol.addView(chip, clp);
            uiButtons[i] = chip;
        }

        // Section B: Yazı Boyutu
        TextView lblFont = UiKit.bold(ctx, 16, UiKit.INK);
        lblFont.setText(tr ? "2. Yazı Boyutu" : "2. Font Size");
        lblFont.setPadding(0, UiKit.px(14), 0, 0);
        leftCol.addView(lblFont);

        final String[][] fontChoices = {
                {tr ? "Küçük (%90)" : "Small (90%)", "0"},
                {tr ? "Normal (%100)" : "Normal (100%)", "1"},
                {tr ? "Büyük (%115)" : "Large (115%)", "2"},
                {tr ? "Çok Büyük (%130)" : "Extra Large (130%)", "3"}
        };
        final View[] fontButtons = new View[4];
        for (int i = 0; i < fontChoices.length; i++) {
            final int idx = i;
            final TextView chip = UiKit.bold(ctx, 14, curFont[0] == idx ? Color.parseColor("#34C759") : UiKit.INK);
            chip.setText((curFont[0] == idx ? "● " : "○ ") + fontChoices[i][0]);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setPadding(UiKit.px(14), UiKit.px(8), UiKit.px(14), UiKit.px(8));
            final Glass cg = new Glass(chip, UiKit.pxf(14), curFont[0] == idx ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            chip.setBackground(cg);
            chip.setOnFocusChangeListener((v, has) -> {
                if (has) com.cinetv.launcher.device.SystemAudio.tick(ctx);
                cg.setStyle(has ? Glass.Style.FOCUSED : (curFont[0] == idx ? Glass.Style.FOCUSED : Glass.Style.CONTROL));
                chip.setTextColor(has ? UiKit.INK_DARK : (curFont[0] == idx ? Color.parseColor("#34C759") : UiKit.INK));
                UiKit.lift(v, has, 1.04f, 120);
            });
            chip.setOnClickListener(v -> {
                com.cinetv.launcher.device.SystemAudio.click(ctx);
                curFont[0] = idx;
                for (int j = 0; j < 4; j++) {
                    ((TextView) fontButtons[j]).setText((curFont[0] == j ? "● " : "○ ") + fontChoices[j][0]);
                    ((TextView) fontButtons[j]).setTextColor(curFont[0] == j ? Color.parseColor("#34C759") : UiKit.INK);
                }
                updatePreview.run();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = UiKit.px(5);
            leftCol.addView(chip, clp);
            fontButtons[i] = chip;
        }

        optScroll.addView(leftCol);
        body.addView(optScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        body.addView(previewBox, rightLp);

        // Footer buttons: Save & Cancel
        LinearLayout footer = new LinearLayout(ctx);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(0, UiKit.px(8), 0, 0);

        View btnCancel = Controls.button(ctx, tr ? "İptal" : "Cancel", v -> host.back());
        footer.addView(btnCancel, new LinearLayout.LayoutParams(UiKit.px(150), UiKit.px(46)));

        View btnSave = Controls.button(ctx, tr ? "Uygula ve Kaydet" : "Apply & Save", v -> {
            Prefs.setUiScaleLevel(ctx, curUi[0]);
            Prefs.setFontScaleLevel(ctx, curFont[0]);
            UiKit.init(ctx);
            a.applyAppearance();
            Toast.makeText(ctx, tr ? "Arayüz ölçeği başarıyla uygulandı" : "UI scale successfully applied", Toast.LENGTH_SHORT).show();
            host.back();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(UiKit.px(210), UiKit.px(46));
        saveLp.leftMargin = UiKit.px(16);
        footer.addView(btnSave, saveLp);

        root.addView(footer);

        updatePreview.run();
        uiButtons[curUi[0]].requestFocus();
        return root;
    }

    // ---- Remote keys --------------------------------------------------------------------------------

    private static View buildKeyCapture(final Context ctx, final ModalHost host, final Actions a) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusable(true);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(tr ? "Kumanda Tuşlarını Özelleştir" : "Customize Remote Buttons");
        root.addView(title);

        final TextView body = UiKit.label(ctx, 16, UiKit.INK_DIM);
        body.setText(tr ? "Aşağıdaki renkli tuşlardan birini seçin veya kumandanızdaki herhangi bir tuşa doğrudan basın:"
                        : "Select one of the color keys below, or press any button on your remote:");
        body.setPadding(0, UiKit.px(10), 0, UiKit.px(16));
        body.setMaxWidth(UiKit.px(760));
        root.addView(body);

        final LinearLayout choices = new LinearLayout(ctx);
        choices.setOrientation(LinearLayout.VERTICAL);

        final String[][] actions = {
                {HardwareKeys.CINESUB, tr ? "CineSub (İnteraktif Kültürel Altyazı & Sahne Taraması)" : "CineSub (Cultural Subtitles & Visual Inspector)"},
                {HardwareKeys.SIMILAR_MOVIES, tr ? "Gamze AI (Yapay Zekâ Sinema Asistanı & Arama)" : "Gamze AI (Cinema Assistant & Voice)"},
                {HardwareKeys.NUVIO, tr ? "Nuvio (Film & Dizi İzle)" : "Nuvio (Movies & TV)"},
                {HardwareKeys.YOUTUBE, tr ? "YouTube (Reklamsız Akış)" : "YouTube (Ad-free)"},
                {HardwareKeys.ROULETTE, tr ? "Film Öneri Ruleti (Çarkı Çevir)" : "Movie Roulette"},
                {HardwareKeys.CLEAN_RAM, tr ? "RAM Temizle & TV'yi Hızlandır" : "Optimize Memory & RAM"},
                {HardwareKeys.NIGHT_MODE, tr ? "Gece İzleme Modu (Aç / Kapat)" : "Toggle Night Viewing Mode"},
                {HardwareKeys.WEB_VIDEO, tr ? "Telefondan Web Video / Cast" : "Cast from Phone"},
                {HardwareKeys.ALL_APPS, tr ? "Tüm Uygulamalar" : "All Applications"}
        };

        final java.util.function.BiConsumer<Integer, Integer> showActionChooser = (kc, sc) -> {
            choices.removeAllViews();
            TextView subH = UiKit.bold(ctx, 18, Color.parseColor("#34C759"));
            subH.setText(tr ? "Bu tuş basıldığında ne açılsın?" : "What should this button open?");
            subH.setPadding(0, 0, 0, UiKit.px(10));
            choices.addView(subH);

            for (final String[] act : actions) {
                choices.addView(Controls.tile(ctx, act[1], null, false, c -> {
                    HardwareKeys.bind(ctx, kc, sc, act[0]);
                    Toast.makeText(ctx, tr ? (act[1] + " tuşa atandı!") : ("Assigned to " + act[1]), Toast.LENGTH_SHORT).show();
                    a.onKeysChanged();
                    host.back();
                }), new LinearLayout.LayoutParams(UiKit.px(580), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (choices.getChildCount() > 1) choices.getChildAt(1).requestFocus();
        };

        // Preset 4-Color Keys Bar
        LinearLayout colorKeysRow = new LinearLayout(ctx);
        colorKeysRow.setOrientation(LinearLayout.HORIZONTAL);
        colorKeysRow.setPadding(0, 0, 0, UiKit.px(16));

        final Object[][] presets = {
                {tr ? "Kırmızı Tuş" : "Red Button", KeyEvent.KEYCODE_PROG_RED, 183},
                {tr ? "Yeşil Tuş" : "Green Button", KeyEvent.KEYCODE_PROG_GREEN, 184},
                {tr ? "Sarı Tuş" : "Yellow Button", KeyEvent.KEYCODE_PROG_YELLOW, 185},
                {tr ? "Mavi Tuş" : "Blue Button", KeyEvent.KEYCODE_PROG_BLUE, 186},
                {"Netflix", KeyEvent.KEYCODE_BUTTON_1, 188},
                {"Prime Video", KeyEvent.KEYCODE_BUTTON_3, 192},
                {"YouTube", KeyEvent.KEYCODE_BUTTON_2, 190},
                {"Disney+ / App", KeyEvent.KEYCODE_BUTTON_4, 191},
                {tr ? "Rehber / TV" : "Guide / TV", KeyEvent.KEYCODE_GUIDE, 172}
        };

        for (int i = 0; i < presets.length; i++) {
            final String name = (String) presets[i][0];
            final int pkc = (Integer) presets[i][1];
            final int psc = (Integer) presets[i][2];

            final TextView btn = UiKit.bold(ctx, 13, UiKit.INK);
            String curAct = HardwareKeys.actionFor(ctx, pkc, psc);
            String actLabel = tr ? "Varsayılan" : "Default";
            if (curAct != null) {
                for (String[] act : actions) {
                    if (act[0].equals(curAct)) {
                        actLabel = act[1].replace("✦ ", "");
                        break;
                    }
                }
            }
            if (actLabel.length() > 18) actLabel = actLabel.substring(0, 16) + "...";
            btn.setText(name + "\n" + actLabel);
            btn.setGravity(Gravity.CENTER);
            btn.setFocusable(true);
            btn.setClickable(true);
            btn.setPadding(UiKit.px(16), UiKit.px(10), UiKit.px(16), UiKit.px(10));
            final Glass bg = new Glass(btn, UiKit.pxf(14), Glass.Style.CONTROL);
            btn.setBackground(bg);
            btn.setOnFocusChangeListener((v, has) -> {
                if (has) com.cinetv.launcher.device.SystemAudio.tick(ctx);
                bg.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
                btn.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
                UiKit.lift(v, has, 1.05f, 120);
            });
            btn.setOnClickListener(v -> {
                com.cinetv.launcher.device.SystemAudio.click(ctx);
                body.setText(name + (tr ? " tuşu seçildi. Yeni görevini belirleyin:" : " selected. Choose action:"));
                colorKeysRow.setVisibility(View.GONE);
                showActionChooser.accept(pkc, psc);
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) blp.leftMargin = UiKit.px(12);
            colorKeysRow.addView(btn, blp);
        }
        root.addView(colorKeysRow);
        root.addView(choices);

        root.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN || HardwareKeys.isNavigation(keyCode) || choices.getChildCount() > 0) return false;
            final int kc = keyCode, sc = e.getScanCode();
            body.setText(ctx.getString(R.string.remote_keys_captured, kc, sc));
            colorKeysRow.setVisibility(View.GONE);
            showActionChooser.accept(kc, sc);
            return true;
        });

        root.post(() -> {
            if (colorKeysRow.getChildCount() > 0) colorKeysRow.getChildAt(0).requestFocus();
            else root.requestFocus();
        });
        return root;
    }

    // ---- First-setup tuning: picture & sound, network, Nuvio quality ---------------------------------

    /** Detects the TV's picture/sound path, tests the network (Cloudflare) and recommends Nuvio settings. */
    public static void setup(final Context ctx, final ModalHost host, final boolean applyAv) {
        host.push(() -> buildSetup(ctx, host, applyAv));
    }

    private static View buildSetup(final Context ctx, final ModalHost host, final boolean applyAv) {
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.bold(ctx, 24, UiKit.INK);
        title.setText(R.string.setup_tune);
        root.addView(title);

        final android.widget.ScrollView scroller = new android.widget.ScrollView(ctx);
        scroller.setVerticalScrollBarEnabled(false);
        final LinearLayout scrollContent = new LinearLayout(ctx);
        scrollContent.setOrientation(LinearLayout.VERTICAL);

        final TextView body = UiKit.label(ctx, 16, UiKit.INK_DIM);
        body.setText(R.string.setup_running);
        body.setLineSpacing(UiKit.pxf(4), 1f);
        body.setPadding(0, UiKit.px(10), 0, UiKit.px(14));
        body.setMaxWidth(UiKit.px(980));
        scrollContent.addView(body, new LinearLayout.LayoutParams(UiKit.px(980), ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout buttons = new LinearLayout(ctx);
        buttons.setVisibility(View.INVISIBLE);
        scrollContent.addView(buttons);

        scroller.addView(scrollContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(UiKit.px(980), UiKit.px(440)));
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
          try {
            final com.cinetv.launcher.device.AvOptimizer.Report av = applyAv
                    ? com.cinetv.launcher.device.AvOptimizer.optimize(ctx) : com.cinetv.launcher.device.AvOptimizer.detect(ctx);
            String dns = com.cinetv.launcher.device.NetworkOptimizer.choice(ctx);
            final boolean dnsOk = applyAv ? com.cinetv.launcher.device.NetworkOptimizer.apply(ctx, dns)
                    : com.cinetv.launcher.device.NetworkOptimizer.privateDnsActive(ctx, String.valueOf(com.cinetv.launcher.device.NetworkOptimizer.host(dns)));
            final String dnsLabel = ctx.getString(dnsRes(dns));
            // Optimize & test, merged: RAM/disk tuning and the benchmark run together with the picture/sound/network step.
            final MemoryOptimizer.Benchmark bench;
            final int tuned;
            if (applyAv) {
                tuned = MemoryOptimizer.tuneSystem(ctx);
                com.cinetv.launcher.service.PosterCache.trimDisk(ctx);
                bench = MemoryOptimizer.runBenchmark(ctx);
                com.cinetv.launcher.service.PosterCache.trimMemory();
                Glow.clear();
                // The performance mode follows the hardware the TV Inspector reports, unless the viewer chose one.
                if (PerformanceProfile.MODE_AUTO.equals(PerformanceProfile.mode(ctx))) {
                    PerformanceProfile.setMode(ctx, PerformanceProfile.modeFor(PerformanceProfile.detectedTier(ctx)));
                }
            } else {
                tuned = 0;
                bench = null;
            }
            final com.cinetv.launcher.device.NetworkProbe.Result net = com.cinetv.launcher.device.NetworkProbe.run();
            final com.cinetv.launcher.service.StreamProfile profile = com.cinetv.launcher.service.StreamProfile.recommend(net, av);
            NuvioSetup.deployConfigs(ctx);
            Prefs.get(ctx).edit().putString("setup_report", av.toJson().toString())
                    .putFloat("setup_mbps", (float) net.downloadMbps).putBoolean(Prefs.FIRST_SETUP_DONE, true).apply();
            main.post(() -> {
                StringBuilder sb = new StringBuilder();
                String hdr = (av.hdr10 ? "HDR10 " : "") + (av.hlg ? "HLG " : "") + (av.hdr10Plus ? "HDR10+ " : "") + (av.dolbyVision ? "Dolby Vision" : "");
                sb.append(ctx.getString(R.string.setup_picture, av.panelWidth > 0 ? av.panelClass() : av.uiWidth + "×" + av.uiHeight,
                        hdr.trim().isEmpty() ? "SDR" : hdr.trim(), Math.round(av.peakNits))).append('\n');
                sb.append(ctx.getString(R.string.setup_sound, av.hdmiAudio
                        ? (av.earc ? "HDMI eARC" : "HDMI ARC") + (av.encodings.isEmpty() ? "" : " · " + android.text.TextUtils.join(", ", av.encodings))
                        : ctx.getString(R.string.setup_tv_speakers))).append('\n');
                sb.append(ctx.getString(dnsOk ? R.string.setup_dns_ok : R.string.setup_dns_off, dnsLabel)).append('\n');
                sb.append(net.ok ? ctx.getString(R.string.setup_network, Math.round(net.downloadMbps), net.latencyMs, net.jitterMs)
                        : ctx.getString(R.string.setup_network_failed)).append('\n');
                if (!av.applied.isEmpty()) sb.append(ctx.getString(R.string.setup_applied, android.text.TextUtils.join(", ", av.applied))).append('\n');
                if (bench != null) {
                    sb.append('\n').append(ctx.getString(R.string.optimize_result, bench.freeRamMb, bench.totalRamMb, bench.freedRamMb, bench.killed,
                            String.format(Locale.US, "%.1f", bench.cpuMs), String.format(Locale.US, "%.0f", bench.diskMBs),
                            ctx.getString(tierRes(PerformanceProfile.tier(ctx))))).append('\n')
                            .append(ctx.getString(R.string.optimize_system, tuned)).append('\n');
                }
                sb.append('\n').append(ctx.getString(R.string.setup_recommend, ctx.getString(profile.labelRes)));
                if (av.is4kPanel() && av.uiWidth < 3840) sb.append('\n').append(ctx.getString(R.string.setup_ui_note));
                if (!"mediatek".equals(PictureCalibration.engine(ctx))) {
                    // The picture engine of this TV only takes values from its own menu: show the filmmaker checklist.
                    sb.append("\n\n").append(ctx.getString(R.string.setup_picture_manual));
                }
                body.setText(sb);
                buttons.removeAllViews();
                buttons.addView(Controls.button(ctx, ctx.getString(R.string.setup_apply_nuvio), v -> {
                    new Thread(() -> NuvioSetup.deployConfigs(ctx)).start();
                    if (!profile.openInNuvio(ctx)) Toast.makeText(ctx, R.string.app_not_found, Toast.LENGTH_SHORT).show();
                }));
                View sp1 = new View(ctx);
                buttons.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(16), 1));
                if (!applyAv) {
                    buttons.addView(Controls.button(ctx, ctx.getString(R.string.setup_apply_av), v -> host.replace(() -> buildSetup(ctx, host, true))));
                    View sp2 = new View(ctx);
                    buttons.addView(sp2, new LinearLayout.LayoutParams(UiKit.px(16), 1));
                }
                buttons.addView(Controls.button(ctx, ctx.getString(R.string.setup_restore), v -> {
                    boolean done = com.cinetv.launcher.device.AvOptimizer.restore(ctx);
                    Toast.makeText(ctx, done ? R.string.setup_restored : R.string.setup_nothing_to_restore, Toast.LENGTH_SHORT).show();
                }));
                View sp3 = new View(ctx);
                buttons.addView(sp3, new LinearLayout.LayoutParams(UiKit.px(16), 1));
                View sp4 = new View(ctx);
                buttons.addView(sp4, new LinearLayout.LayoutParams(UiKit.px(16), 1));
                final TextView dnsBtn = Controls.button(ctx, ctx.getString(R.string.dns_title) + ": " + ctx.getString(dnsRes(com.cinetv.launcher.device.NetworkOptimizer.choice(ctx))), null);
                dnsBtn.setOnClickListener(v -> {
                    final String next = com.cinetv.launcher.device.NetworkOptimizer.next(com.cinetv.launcher.device.NetworkOptimizer.choice(ctx));
                    new Thread(() -> {
                        boolean ok = com.cinetv.launcher.device.NetworkOptimizer.apply(ctx, next);
                        v.post(() -> {
                            if (!ok && !com.cinetv.launcher.device.NetworkOptimizer.DNS_OFF.equals(next)) Toast.makeText(ctx, R.string.dns_blocked, Toast.LENGTH_LONG).show();
                            dnsBtn.setText(ctx.getString(R.string.dns_title) + ": " + ctx.getString(dnsRes(com.cinetv.launcher.device.NetworkOptimizer.choice(ctx))));
                        });
                    }, "cinetv-dns").start();
                });
                buttons.addView(dnsBtn);
                View sp5 = new View(ctx);
                buttons.addView(sp5, new LinearLayout.LayoutParams(UiKit.px(16), 1));
                buttons.addView(Controls.button(ctx, ctx.getString(R.string.done), v -> host.back()));
                buttons.setVisibility(View.VISIBLE);
                buttons.getChildAt(0).requestFocus();
            });
          } catch (Throwable t) {
            // A TV that hides a setting must never crash Home in a loop: finish the setup without that step.
            android.util.Log.w("CineTvSetup", "tuning stopped", t);
            Prefs.get(ctx).edit().putBoolean(Prefs.FIRST_SETUP_DONE, true).apply();
            main.post(() -> {
                body.setText(R.string.setup_partial);
                buttons.removeAllViews();
                buttons.addView(Controls.button(ctx, ctx.getString(R.string.done), v -> host.back()));
                buttons.setVisibility(View.VISIBLE);
                buttons.getChildAt(0).requestFocus();
            });
          }
        }, "cinetv-setup").start();
        return root;
    }

    // ---- Benchmark ----------------------------------------------------------------------------------

    private static View buildBenchmark(final Context ctx, final ModalHost host) {
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(R.string.optimize);
        root.addView(title);
        final TextView body = UiKit.label(ctx, 22, UiKit.INK_DIM);
        body.setText(R.string.optimize_running);
        body.setPadding(0, UiKit.px(18), 0, UiKit.px(24));
        body.setLineSpacing(UiKit.pxf(10), 1f);
        root.addView(body);
        final TextView done = Controls.button(ctx, ctx.getString(R.string.done), v -> host.back());
        done.setVisibility(View.INVISIBLE);
        LinearLayout row = new LinearLayout(ctx);
        row.addView(done);
        root.addView(row);
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
          try {
            final int tuned = MemoryOptimizer.tuneSystem(ctx);
            com.cinetv.launcher.service.PosterCache.trimDisk(ctx);
            final MemoryOptimizer.Benchmark b = MemoryOptimizer.runBenchmark(ctx);
            com.cinetv.launcher.service.PosterCache.trimMemory();
            Glow.clear();
            main.post(() -> {
                body.setText(ctx.getString(R.string.optimize_result, b.freeRamMb, b.totalRamMb, b.freedRamMb, b.killed,
                        String.format(Locale.US, "%.1f", b.cpuMs), String.format(Locale.US, "%.0f", b.diskMBs),
                        ctx.getString(tierRes(PerformanceProfile.tier(ctx)))) + "\n\n" + ctx.getString(R.string.optimize_system, tuned));
                done.setVisibility(View.VISIBLE);
                done.requestFocus();
            });
          } catch (Throwable t) {
            android.util.Log.w("CineTvBench", "benchmark stopped", t);
            main.post(() -> {
                done.setVisibility(View.VISIBLE);
                done.requestFocus();
            });
          }
        }, "cinetv-bench").start();
        return root;
    }

    // ---- In-App Setup Wizard: ensures standalone installs are 100% self-sufficient --------------------

    public static void wizard(final Context ctx, final ModalHost host) {
        if (!Prefs.get(ctx).contains("nuvio_choice_made") && !AppLauncher.isLaunchable(ctx.getPackageManager(), AppLauncher.NUVIO)) {
            host.push(() -> buildNuvioPrompt(ctx, host));
        } else {
            host.push(() -> buildWizard(ctx, host));
        }
    }

    private static View buildNuvioPrompt(final Context ctx, final ModalHost host) {
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);

        final boolean turkish = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(ctx);
        logo.setImageBitmap(Brand.mark(ctx));
        head.addView(logo, new LinearLayout.LayoutParams(UiKit.px(44), UiKit.px(44)));
        TextView title = UiKit.bold(ctx, 30, UiKit.INK);
        title.setText(turkish ? "Nuvio Medya Merkezi Kurulsun mu?" : "Install Nuvio Media Center?");
        title.setPadding(UiKit.px(16), 0, 0, 0);
        head.addView(title);
        root.addView(head);

        TextView subtitle = UiKit.label(ctx, 18, UiKit.INK_DIM);
        subtitle.setText(turkish
                ? "CineTV film ve dizi kütüphanesini izleyebilmeniz için Nuvio Medya Merkezi ve Türkçe altyazı eklentileri (Netflix, TR Altyazı, Stremio) arka planda kurulsun mu?"
                : "Would you like CineTV to automatically install Nuvio and streaming addons in the background?");
        subtitle.setPadding(0, UiKit.px(14), 0, UiKit.px(24));
        root.addView(subtitle);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        buttons.addView(Controls.button(ctx, turkish ? "Evet, Kurulsun (Önerilen)" : "Yes, Install (Recommended)", v -> {
            Prefs.get(ctx).edit().putBoolean("nuvio_choice_made", true).putBoolean("nuvio_install_allowed", true).apply();
            Toast.makeText(ctx, turkish ? "Nuvio ve Türkçe eklentiler kuruluyor..." : "Installing Nuvio & addons...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                NuvioSetup.installNuvio(ctx, true);
                NuvioSetup.installPlayer(ctx, true);
            }, "cinetv-nuvio-bg").start();
            host.replace(() -> buildWizard(ctx, host));
        }));

        View space = new View(ctx);
        buttons.addView(space, new LinearLayout.LayoutParams(UiKit.px(20), 1));

        buttons.addView(Controls.button(ctx, turkish ? "Hayır, Şimdilik Geç" : "Skip for Now", v -> {
            Prefs.get(ctx).edit().putBoolean("nuvio_choice_made", true).putBoolean("nuvio_install_allowed", false).apply();
            host.replace(() -> buildWizard(ctx, host));
        }));

        root.addView(buttons);
        root.post(() -> {
            if (buttons.getChildCount() > 0) buttons.getChildAt(0).requestFocus();
        });
        return root;
    }

    private static View buildWizard(final Context ctx, final ModalHost host) {
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setPadding(UiKit.px(12), UiKit.px(8), UiKit.px(12), UiKit.px(12));
        scroll.addView(root);

        final boolean turkish = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(ctx);
        logo.setImageBitmap(Brand.mark(ctx));
        head.addView(logo, new LinearLayout.LayoutParams(UiKit.px(44), UiKit.px(44)));
        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(turkish ? "CineTV Kurulum Sihirbazı" : "CineTV Setup Wizard");
        title.setPadding(UiKit.px(16), 0, 0, 0);
        head.addView(title);
        root.addView(head);

        TextView subtitle = UiKit.label(ctx, 17, UiKit.INK_DIM);
        subtitle.setText(turkish
                ? "Sol sütundan sistem/medya bileşenlerini, sağ sütundan ise %100 ücretsiz Gemini & TMDB sinema anahtarlarınızı kolayca yapılandırabilirsiniz:"
                : "Configure system & media components on the left, and enter your 100% free Gemini & TMDB cinema keys on the right:");
        subtitle.setPadding(0, UiKit.px(8), 0, UiKit.px(14));
        root.addView(subtitle);

        final LinearLayout twoCols = new LinearLayout(ctx);
        twoCols.setOrientation(LinearLayout.HORIZONTAL);
        twoCols.setClipChildren(false);
        root.addView(twoCols);

        final LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        twoCols.addView(list, leftLp);

        final LinearLayout rightCol = new LinearLayout(ctx);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setClipChildren(false);
        rightCol.setClipToPadding(false);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.leftMargin = UiKit.px(24);
        twoCols.addView(rightCol, rightLp);

        // 1. Home Launcher
        boolean isHome = HomeGuard.isDefault(ctx);
        list.addView(Controls.tile(ctx,
                turkish ? "1. Varsayılan Ana Ekran (Home Launcher)" : "1. Default Home Launcher",
                isHome ? (turkish ? "CineTV varsayılan ana ekran olarak devrede" : "CineTV is default home")
                       : (turkish ? "Kumanda Home tuşu için dokunun (Her zaman seçin)" : "Tap to set as default launcher (choose Always)"),
                isHome, v -> {
                    HomeGuard.promptHomeChooser(ctx);
                    v.postDelayed(() -> host.replace(() -> buildWizard(ctx, host)), 1500);
                }));

        // 2. Nuvio Media Center
        final boolean nuvioOk = AppLauncher.isLaunchable(ctx.getPackageManager(), AppLauncher.NUVIO);
        list.addView(Controls.tile(ctx,
                turkish ? "2. Nuvio Medya Merkezi & Türkçe Eklentiler" : "2. Nuvio Media Center & Addons",
                nuvioOk ? (turkish ? "Yüklü ve hazır (filmler, diziler, Türkçe eklentiler)" : "Installed and ready")
                        : (turkish ? "Yüklü değil (dokunarak arka planda indirin ve kurun)" : "Not installed (tap to download & install)"),
                nuvioOk, v -> {
                    if (nuvioOk) {
                        Toast.makeText(ctx, turkish ? "Nuvio eklentileri ve profilleri eşitleniyor..." : "Syncing Nuvio addons & configs...", Toast.LENGTH_SHORT).show();
                        NuvioSetup.syncAddonsAndConfigs(ctx, true);
                    } else {
                        Toast.makeText(ctx, turkish ? "Nuvio ve eklentileri indiriliyor..." : "Installing Nuvio and addons...", Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            NuvioSetup.installNuvio(ctx, true);
                            v.post(() -> host.replace(() -> buildWizard(ctx, host)));
                        }, "cinetv-wiz-nuvio").start();
                    }
                }));

        // 3. Video Player
        final boolean playerOk = NuvioSetup.hasPlayer(ctx);
        list.addView(Controls.tile(ctx,
                turkish ? "3. Video Oynatıcı (Stream Player)" : "3. Video Stream Player",
                playerOk ? (turkish ? "Yayın oynatıcı mevcut ve hazır" : "Video player ready")
                         : (turkish ? "Nova Player yüklemek için dokunun (telefondan aktarım için)" : "Tap to install Nova Player"),
                playerOk, v -> {
                    if (!playerOk) {
                        Toast.makeText(ctx, turkish ? "Nova Player indiriliyor..." : "Downloading Nova Player...", Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            NuvioSetup.installPlayer(ctx, true);
                            v.post(() -> host.replace(() -> buildWizard(ctx, host)));
                        }, "cinetv-wiz-player").start();
                    }
                }));

        // 4. Remote Keys & System Integration (Accessibility is strictly optional, native keys always work)
        list.addView(Controls.tile(ctx,
                turkish ? "4. Kumanda Tuşları & Sistem Uyumu" : "4. Remote Keys & System Integration",
                turkish ? "Doğrudan yerel kumanda kontrolü hazır (Erişilebilirlik zorunlu değil)" : "Native remote key navigation ready (Accessibility optional)",
                true, v -> {
                    com.cinetv.launcher.HomeGuard.ensureDefault(ctx);
                    HomeGuard.openAccessibilitySettings(ctx);
                    v.postDelayed(() -> host.replace(() -> buildWizard(ctx, host)), 2000);
                }));

        // 5. Virtual RAM & TV Optimization
        boolean optOk = MemoryOptimizer.isTvOptimized(ctx);
        list.addView(Controls.tile(ctx,
                turkish ? "5. 2 GB Sanal RAM & Arka Plan Hızlandırma" : "5. 2 GB Virtual RAM & Optimization",
                optOk ? (turkish ? "Sanal RAM devrede, arka plan ve TV Denetçisi optimize edildi" : "Virtual RAM active, system optimized")
                      : (turkish ? "Sanal RAM oluşturup gereksiz servisleri kapatmak için dokunun" : "Tap to apply 2 GB virtual RAM & optimize"),
                optOk, v -> {
                    MemoryOptimizer.applyTvOptimizations(ctx);
                    Toast.makeText(ctx, turkish ? "Sanal RAM ve optimizasyon uygulandı" : "Virtual RAM & optimizations applied", Toast.LENGTH_SHORT).show();
                    v.postDelayed(() -> host.replace(() -> buildWizard(ctx, host)), 800);
                }));

        // Right Column: Free Gemini & TMDB API Key Guide & Entry
        FrameLayout guideCard = new FrameLayout(ctx);
        guideCard.setBackground(Glass.panel(guideCard, 18));
        guideCard.setPadding(UiKit.px(18), UiKit.px(14), UiKit.px(18), UiKit.px(14));
        TextView guideTv = UiKit.label(ctx, 15, UiKit.INK_DIM);
        guideTv.setLineSpacing(UiKit.pxf(4), 1.12f);
        guideTv.setText(turkish
                ? "ÜCRETSİZ YAPAY ZEKA & SİNEMA ARŞİVİ ANAHTARLARI\n"
                + "Bu iki kod tamamen ücretsizdir (kredi kartı gerekmez) ve CineTV'nin tüm akıllı sinema gücünü açar:\n\n"
                + "• Gemini AI (aistudio.google.com/apikey): Sesli/yazılı doğal dilde film araması (\"Nolan'ın uzay filmini aç\"), saate ve hava durumuna göre akıllı DVD vitrin küratörlüğü ve Türkçe sinema asistanı sağlar.\n"
                + "• TMDB (themoviedb.org/settings/api): 3B DVD kutularının yüksek çözünürlüklü afişlerini, açılan DVD'nin içindeki Türkçe film özetini, oyuncu kadrosunu ve fragman videolarını getirir."
                : "100% FREE AI & CINEMA ARCHIVE KEYS\n"
                + "Both keys are completely free (no credit card needed) and unlock CineTV's full intelligence:\n\n"
                + "• Gemini AI (aistudio.google.com/apikey): Natural-language voice/text search, time & weather-aware smart DVD shelf curation, and your AI cinema assistant.\n"
                + "• TMDB (themoviedb.org/settings/api): High-res 3D DVD cover art, inner booklet synopses, cast credits, and live trailers.");
        guideCard.addView(guideTv);
        LinearLayout.LayoutParams gclp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gclp.bottomMargin = UiKit.px(10);
        rightCol.addView(guideCard, gclp);

        // 6. Gemini AI Key
        String provider = LlmClient.providerName(ctx);
        boolean hasAi = provider != null;
        rightCol.addView(Controls.tile(ctx,
                turkish ? "6. Gemini Yapay Zeka Anahtarı (%100 Ücretsiz)" : "6. Gemini AI Key (100% Free)",
                hasAi ? (turkish ? "Aktif (" + provider + ") · Değiştirmek için dokunun" : "Active (" + provider + ")")
                      : (turkish ? "aistudio.google.com/apikey · QR kodlu rehber ve anahtar girişi için dokunun" : "Tap for QR guide & key input (aistudio.google.com)"),
                hasAi, v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.AI_KEY, R.string.ai_key, R.string.ai_key_hint,
                        () -> host.replace(() -> buildWizard(ctx, host))))));

        // 7. TMDB Key
        boolean hasTmdb = MovieCatalog.hasTmdb(ctx);
        rightCol.addView(Controls.tile(ctx,
                turkish ? "7. TMDB Film, Afiş & Fragman Anahtarı (%100 Ücretsiz)" : "7. TMDB Poster & Trailer Key (100% Free)",
                hasTmdb ? (turkish ? "Aktif (Yüksek çözünürlüklü afiş, özet ve fragmanlar devrede)" : "Active (Posters, synopses & trailers enabled)")
                        : (turkish ? "themoviedb.org/settings/api · QR kodlu rehber ve anahtar girişi için dokunun" : "Tap for QR guide & key input (themoviedb.org)"),
                hasTmdb, v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.TMDB_KEY, R.string.tmdb_key, R.string.tmdb_key_hint,
                        () -> host.replace(() -> buildWizard(ctx, host))))));

        // 8. Unified Picture, Sound, DNS & Benchmark (TV Denetçisi)
        boolean avDone = Prefs.get(ctx).getBoolean(Prefs.FIRST_SETUP_DONE, false);
        rightCol.addView(Controls.tile(ctx,
                turkish ? "8. Görüntü, Ses, DNS & Ağ Optimizasyonu (TV Denetçisi)" : "8. Picture, Sound, DNS & Network Optimization",
                turkish ? "TV Denetçisi verileriyle panel, HDR, ses, DNS ve Nuvio akış kalitesini eşitle"
                        : "Sync panel, HDR, audio, DNS & Nuvio stream profile from TV Inspector",
                avDone, v -> setup(ctx, host, true)));

        // Footer buttons: Otomatik Tamamla & Geri Dön
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, UiKit.px(14), 0, 0);

        buttons.addView(Controls.button(ctx, turkish ? "Otomatik Tamamla" : "Auto-Setup All", v -> {
            Toast.makeText(ctx, turkish ? "Kurulum ve optimizasyonlar başlatılıyor..." : "Completing setup...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                if (!HomeGuard.isDefault(ctx)) {
                    v.post(() -> HomeGuard.promptHomeChooser(ctx));
                }
                if (Prefs.get(ctx).getBoolean("nuvio_install_allowed", true)) {
                    NuvioSetup.installNuvio(ctx, true);
                    NuvioSetup.installPlayer(ctx, true);
                }
                MemoryOptimizer.applyTvOptimizations(ctx);
                com.cinetv.launcher.device.AvOptimizer.optimize(ctx);
                NuvioSetup.deployConfigs(ctx);
                Prefs.get(ctx).edit().putBoolean("wizard_done", true).putBoolean(Prefs.FIRST_SETUP_DONE, true).apply();
                v.post(() -> {
                    Toast.makeText(ctx, turkish ? "Tüm bileşenler ve TV Denetçisi optimizasyonları başarıyla uygulandı!" : "All components & optimizations successfully applied!", Toast.LENGTH_LONG).show();
                    host.replace(() -> buildWizard(ctx, host));
                });
            }, "cinetv-wiz-auto").start();
        }));

        View space = new View(ctx);
        buttons.addView(space, new LinearLayout.LayoutParams(UiKit.px(20), 1));

        buttons.addView(Controls.button(ctx, turkish ? "Geri Dön" : "Back", v -> {
            Prefs.get(ctx).edit().putBoolean("wizard_done", true).putBoolean(Prefs.FIRST_SETUP_DONE, true).apply();
            host.back();
            if (ctx instanceof com.cinetv.launcher.MainActivity) {
                ((com.cinetv.launcher.MainActivity) ctx).onWizardFinished();
            }
        }));

        root.addView(buttons);
        root.post(() -> {
            if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
        });
        return scroll;
    }

    // ---- TV Inspector (Hardware, Display, Decoders & Remote Key Inspector) -------------------------

    private static View buildTvInspector(final Context ctx, final ModalHost host) {
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(28), UiKit.px(20), UiKit.px(28), UiKit.px(24));
        int modalW = Math.round(ctx.getResources().getDisplayMetrics().widthPixels * 0.82f);
        scroll.addView(root, new ViewGroup.LayoutParams(modalW, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(tr ? "TV Denetçisi · Donanım & Panel Teşhisi" : "TV Inspector · Hardware & Panel Diagnostics");
        root.addView(title);

        TextView intro = UiKit.label(ctx, 18, UiKit.INK_DIM);
        intro.setText(tr ? "Gelişmiş donanım ve panel standardında ekran, çözücü yetenekleri, bellek ve canlı kumanda dedektörü"
                         : "Advanced hardware and panel display, decoders, memory & remote key diagnostics");
        intro.setPadding(0, UiKit.px(6), 0, UiKit.px(18));
        root.addView(intro);

        // Responsive 2-Column Balanced Dashboard
        LinearLayout twoCols = new LinearLayout(ctx);
        twoCols.setOrientation(LinearLayout.HORIZONTAL);
        twoCols.setClipChildren(false);
        twoCols.setClipToPadding(false);

        LinearLayout col1 = new LinearLayout(ctx);
        col1.setOrientation(LinearLayout.VERTICAL);

        LinearLayout col2 = new LinearLayout(ctx);
        col2.setOrientation(LinearLayout.VERTICAL);

        // 1. Ekran & Panel
        col1.addView(inspectorHeader(ctx, tr ? "Ekran & Panel Yetenekleri" : "Display & Panel"));
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Display d = wm != null ? wm.getDefaultDisplay() : null;
        StringBuilder dispSb = new StringBuilder();
        final com.cinetv.launcher.device.AvOptimizer.Report av = com.cinetv.launcher.device.AvOptimizer.detect(ctx);
        int pw = av.panelWidth > 0 ? av.panelWidth : av.uiWidth, ph = av.panelHeight > 0 ? av.panelHeight : av.uiHeight;
        dispSb.append(tr ? "Panel Çözünürlüğü: " : "Panel Resolution: ").append(pw).append(" × ").append(ph).append(" (").append(av.panelClass()).append(")");
        if (av.uiWidth > 0 && (av.uiWidth != pw || av.uiHeight != ph)) {
            dispSb.append(tr ? "\nArayüz Katmanı: " : "\nUI Plane: ").append(av.uiWidth).append(" × ").append(av.uiHeight)
                    .append(tr ? " (video 4K panele doğrudan gider)" : " (video goes straight to the 4K panel)");
        }
        dispSb.append(tr ? "\nYenileme Hızı: " : "\nRefresh Rate: ").append(String.format(Locale.US, "%.0f Hz", av.maxRefresh));
        List<String> hdrs = new ArrayList<>();
        if (av.dolbyVision) hdrs.add("Dolby Vision");
        if (av.hdr10) hdrs.add("HDR10");
        if (av.hdr10Plus) hdrs.add("HDR10+");
        if (av.hlg) hdrs.add("HLG");
        if (!hdrs.isEmpty()) {
            dispSb.append(tr ? "\nHDR Formatları: " : "\nHDR Formats: ").append(android.text.TextUtils.join(", ", hdrs));
            if (av.peakNits > 0) dispSb.append(String.format(Locale.US, " · ~%.0f nit", av.peakNits));
        } else {
            dispSb.append(tr ? "\nHDR Desteği: Desteklenmiyor (SDR panel)" : "\nHDR Support: Unsupported (SDR panel)");
        }
        dispSb.append(tr ? "\nGeniş Renk Gamı (BT.2020): " : "\nWide Color Gamut (BT.2020): ")
                .append(av.wideGamut ? (tr ? "Destekleniyor" : "Supported") : (tr ? "Desteklenmiyor" : "Unsupported"));
        dispSb.append(tr ? "\nHDMI Ses: " : "\nHDMI Audio: ")
                .append(av.hdmiAudio ? (av.earc ? "eARC " : "ARC ") + (tr ? "Destekleniyor" : "Supported") : (tr ? "Desteklenmiyor (TV hoparlörü)" : "Unsupported (TV speakers)"));
        col1.addView(inspectorCard(ctx, dispSb.toString()));

        // 2. Bellek & Depolama
        col1.addView(inspectorHeader(ctx, tr ? "Bellek & Depolama" : "Memory & Storage"));
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mem);
        StringBuilder memSb = new StringBuilder();
        long totalMb = mem.totalMem / (1024 * 1024);
        long availMb = mem.availMem / (1024 * 1024);
        memSb.append(tr ? "Toplam RAM: " : "Total RAM: ").append(totalMb).append(" MB");
        memSb.append(tr ? "\nKullanılabilir RAM: " : "\nAvailable RAM: ").append(availMb).append(" MB");
        memSb.append(tr ? "\nDüşük Bellek Cihazı: " : "\nLow RAM Device: ").append(am != null && am.isLowRamDevice() ? (tr ? "Evet" : "Yes") : (tr ? "Hayır" : "No"));
        memSb.append(tr ? "\nZ-RAM Durumu: " : "\nZ-RAM Status: ").append(MemoryOptimizer.getZramInfo(ctx));
        File dataDir = Environment.getDataDirectory();
        long freeGb = dataDir.getFreeSpace() / (1024 * 1024 * 1024);
        long totalGb = dataDir.getTotalSpace() / (1024 * 1024 * 1024);
        memSb.append(tr ? "\nDahili Depolama: " : "\nInternal Storage: ").append(freeGb).append(" GB ").append(tr ? "boş / " : "free / ").append(totalGb).append(" GB");
        col1.addView(inspectorCard(ctx, memSb.toString()));

        // 3. Sistem & Anakart
        col1.addView(inspectorHeader(ctx, tr ? "Sistem & Anakart" : "System & Board"));
        StringBuilder sysSb = new StringBuilder();
        sysSb.append(tr ? "Cihaz: " : "Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL);
        sysSb.append(tr ? "\nAnakart: " : "\nBoard: ").append(Build.BOARD).append(" (").append(Build.HARDWARE).append(")");
        sysSb.append(tr ? "\nAndroid TV: " : "\nAndroid TV: ").append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sysSb.append(tr ? "\nGüvenlik Yaması: " : "\nSecurity Patch: ").append(Build.VERSION.SECURITY_PATCH);
        }
        sysSb.append(tr ? "\nİşlemci Mimarisi: " : "\nCPU ABI: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "bilinmiyor");
        col1.addView(inspectorCard(ctx, sysSb.toString()));

        // 4. Medya Çözücüler (Video & Audio Decoders)
        col2.addView(inspectorHeader(ctx, tr ? "Donanımsal Medya Çözücüler" : "Hardware Media Decoders"));
        String[][] probeCodecs = {
                {"video/av01", "AV1 (AOMedia)"},
                {"video/hevc", "HEVC / H.265 (4K/8K HDR)"},
                {"video/x-vnd.on2.vp9", "VP9 (YouTube 4K)"},
                {"video/avc", "AVC / H.264"},
                {"video/dolby-vision", "Dolby Vision Video"},
                {"audio/eac3-joc", "Dolby Atmos (E-AC3 JOC)"},
                {"audio/eac3", "Dolby Digital Plus (E-AC3)"},
                {"audio/ac3", "Dolby Digital (AC-3)"},
                {"audio/vnd.dts", "DTS Audio"},
                {"audio/vnd.dts.hd", "DTS-HD Master Audio"},
                {"audio/true-hd", "Dolby TrueHD"}
        };
        StringBuilder codecSb = new StringBuilder();
        try {
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] infos = mcl.getCodecInfos();
            for (String[] pair : probeCodecs) {
                boolean supported = false;
                for (MediaCodecInfo info : infos) {
                    if (info.isEncoder()) continue;
                    for (String type : info.getSupportedTypes()) {
                        if (type.equalsIgnoreCase(pair[0])) {
                            supported = true;
                            break;
                        }
                    }
                    if (supported) break;
                }
                codecSb.append(supported ? "✓ " : "✗ ").append(pair[1])
                        .append(supported ? (tr ? "  Destekleniyor\n" : "  Supported\n") : (tr ? "  Desteklenmiyor\n" : "  Unsupported\n"));
            }
        } catch (Throwable t) {
            codecSb.append(t.getMessage());
        }
        col2.addView(inspectorCard(ctx, codecSb.toString().trim()));

        // 5. Canlı Kumanda Tuş Dedektörü
        col2.addView(inspectorHeader(ctx, tr ? "Canlı Kumanda Tuş Dedektörü" : "Live Remote Key Inspector"));
        final FrameLayout keyBox = new FrameLayout(ctx);
        keyBox.setFocusable(true);
        keyBox.setClickable(true);
        final TextView keyResult = UiKit.bold(ctx, 20, Color.parseColor("#38BDF8"));
        keyResult.setText(tr ? "Kumandanızdan bir tuşa basın...\n(Tuş adı, KeyCode, ScanCode ve cihaz bilgisi anlık gösterilir)"
                             : "Press any remote button...\n(Key name, KeyCode, ScanCode and device info appear live)");
        keyResult.setLineSpacing(UiKit.pxf(6), 1.2f);
        keyResult.setPadding(UiKit.px(20), UiKit.px(16), UiKit.px(20), UiKit.px(16));
        keyBox.addView(keyResult);
        keyBox.setBackground(Glass.panel(keyBox, 20));
        keyBox.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            String kName = KeyEvent.keyCodeToString(keyCode);
            int sc = event.getScanCode();
            String dev = event.getDevice() != null ? event.getDevice().getName() : "Remote";
            keyResult.setText(String.format(Locale.getDefault(),
                    (tr ? "Tuş: %s\nKeyCode: %d | ScanCode: %d\nCihaz: %s"
                        : "Key: %s\nKeyCode: %d | ScanCode: %d\nDevice: %s"),
                    kName, keyCode, sc, dev));
            return !HardwareKeys.isNavigation(keyCode);
        });
        col2.addView(keyBox);

        LinearLayout.LayoutParams c1lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        twoCols.addView(col1, c1lp);
        LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        c2lp.leftMargin = UiKit.px(24);
        twoCols.addView(col2, c2lp);

        root.addView(twoCols);

        // Verification & Community Tested Device Feedback
        col2.addView(inspectorHeader(ctx, tr ? "Cihaz Uyumluluğu & Onay" : "Device Compatibility & Verification"));
        final TextView feedbackBtn = Controls.button(ctx, tr ? "Uyumluluk Raporu Gönder (Topluluk Listesine Ekle)" : "Submit Device Compatibility Report", v -> {
            com.cinetv.launcher.device.DeviceVerifier.submitFeedback(ctx, "Verified & Running Smoothly", new com.cinetv.launcher.device.DeviceVerifier.Callback() {
                @Override
                public void onSuccess(String msg) {
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
                    final String issueUrl = com.cinetv.launcher.device.DeviceVerifier.getVerificationIssueUrl(ctx, "Verified & Running Smoothly");
                    host.push(() -> {
                        LinearLayout box = new LinearLayout(ctx);
                        box.setGravity(Gravity.CENTER_VERTICAL);
                        box.setPadding(UiKit.px(24), UiKit.px(20), UiKit.px(24), UiKit.px(20));
                        FrameLayout qrPlate = new FrameLayout(ctx);
                        qrPlate.setBackground(new Glass(qrPlate, UiKit.pxf(24), Glass.Style.FOCUSED));
                        int size = UiKit.px(380);
                        ImageView qr = new ImageView(ctx);
                        Bitmap code = RemotePairing.qr(issueUrl, size, Color.parseColor("#0B1220"), Color.WHITE);
                        if (code != null) qr.setImageBitmap(code);
                        qrPlate.addView(qr, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
                        qrPlate.setPadding(UiKit.px(24), UiKit.px(24), UiKit.px(24), UiKit.px(24));
                        box.addView(qrPlate);

                        LinearLayout info = new LinearLayout(ctx);
                        info.setOrientation(LinearLayout.VERTICAL);
                        info.setPadding(UiKit.px(36), 0, 0, 0);
                        TextView t = UiKit.bold(ctx, 28, UiKit.INK);
                        t.setText(tr ? "Doğrulanmış Cihaz Kaydı Hazır" : "Verified Device Profile Ready");
                        info.addView(t);
                        TextView sub = UiKit.label(ctx, 18, UiKit.INK_DIM);
                        sub.setText(tr
                                ? "Donanım raporunuz televizyona kaydedildi (/sdcard/Download/cinetv_verified_device.json).\n\nTelefonunuzla yandaki QR kodu okutarak televizyonunuzu tek dokunuşla resmi GitHub uyumluluk listesine ekleyebilirsiniz."
                                : "Hardware profile saved locally. Scan the QR code with your phone to add your TV to the official GitHub compatibility list in one tap.");
                        sub.setLineSpacing(UiKit.pxf(6), 1.15f);
                        sub.setMaxWidth(UiKit.px(620));
                        sub.setPadding(0, UiKit.px(12), 0, UiKit.px(24));
                        info.addView(sub);
                        TextView okBtn = Controls.button(ctx, tr ? "Tamam" : "Done", b -> host.back());
                        info.addView(okBtn);
                        box.addView(info);
                        box.post(okBtn::requestFocus);
                        return box;
                    });
                }
                @Override
                public void onError(String err) {
                    Toast.makeText(ctx, "Hata: " + err, Toast.LENGTH_SHORT).show();
                }
            });
        });
        col2.addView(feedbackBtn);

        // Kapat Butonu
        final TextView close = Controls.button(ctx, tr ? "Kapat" : "Close", v -> host.close());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(UiKit.px(320), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = UiKit.px(24);
        root.addView(close, clp);

        root.post(keyBox::requestFocus);
        return scroll;
    }

    // ---- Nuvio Addon Update Selection Window ----------------------------------------------------

    public static void nuvioUpdateAddons(final Context ctx, final ModalHost host) {
        host.push(() -> buildNuvioUpdateAddons(ctx, host));
    }

    private static View buildNuvioUpdateAddons(final Context ctx, final ModalHost host) {
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(28), UiKit.px(20), UiKit.px(28), UiKit.px(20));
        scroll.addView(root);

        TextView title = UiKit.bold(ctx, 30, UiKit.INK);
        title.setText("Nuvio Eklenti ve Profil Güncellemesi");
        root.addView(title);

        TextView subtitle = UiKit.label(ctx, 18, UiKit.INK_DIM);
        subtitle.setText("CineTV güncellendi. Nuvio medya merkezinize aşağıdaki eklenti ve yapılandırmalardan hangilerinin kurulmasını istersiniz?");
        subtitle.setLineSpacing(UiKit.pxf(6), 1.15f);
        subtitle.setPadding(0, UiKit.px(12), 0, UiKit.px(20));
        subtitle.setMaxWidth(UiKit.px(980));
        root.addView(subtitle);

        final String[][] items = {
                {"TR Altyazı", "Resmi Türkçe altyazı motoru (AltyaziTR, OpenSubtitles)", "tr-altyazi.altyazi.workers.dev/manifest.json"},
                {"Anthology", "Türkçe dizi, film ve canlı yayın kanalları", "falsisdev.github.io/anthology/stremio/manifest.json"},
                {"Netflix Catalog", "Popüler ve güncel streaming katalog düzeni", "7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhdHAsaGJtLGFtcCxtYmk6OjoxNzkyODA2MTk4NzgwOjA6MDo%3D/manifest.json"},
                {"Xperience", "Gelişmiş kategori, afiş ve metadata sağlayıcısı", "xperience-app.com/manifest/3d7c0742-30bc-4285-8a05-2b9327cad6b0/eyJhbGciOiJIUzI1NiJ9.eyJwaWQiOiIzZDdjMDc0Mi0zMGJjLTQyODUtOGEwNS0yYjkzMjdjYWQ2YjAiLCJraWQiOiI1OTg2ZGMxYS00MzNhLTQ0MTQtYmU3Zi1iMzQ0M2NhZWY0NjQiLCJzY29wZSI6Im1hbmlmZXN0Iiwic3ViIjoiZjI3NmYwYjEtM2EzZC00ZGU1LTgwZDctZTljOWE3ZWE4YTFhIiwiaWF0IjowfQ.3KVz5c33icFDay9jjTLOtqJEl5m8huYoz9xYHPAwBhE/manifest.json"},
                {"Alptuğ'un Filmleri & Şablonlar", "Özel seçkin koleksiyon profili ve hazır şablonlar (/sdcard/Download/)", "deploy_configs"}
        };

        final boolean[] checked = new boolean[items.length];
        for (int i = 0; i < checked.length; i++) checked[i] = true;

        final LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            final LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(UiKit.px(20), UiKit.px(12), UiKit.px(20), UiKit.px(12));

            final Glass g = new Glass(row, UiKit.pxf(14), Glass.Style.CONTROL);
            row.setBackground(g);

            final TextView checkIndicator = UiKit.bold(ctx, 22, 0xFF34D399);
            checkIndicator.setText("[✓]");
            checkIndicator.setPadding(0, 0, UiKit.px(16), 0);
            row.addView(checkIndicator);

            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView itemTitle = UiKit.bold(ctx, 20, UiKit.INK);
            itemTitle.setText(items[i][0]);
            textCol.addView(itemTitle);

            TextView itemSub = UiKit.label(ctx, 15, UiKit.INK_DIM);
            itemSub.setText(items[i][1]);
            itemSub.setPadding(0, UiKit.px(2), 0, 0);
            textCol.addView(itemSub);

            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnFocusChangeListener((v, hasFocus) -> {
                g.setStyle(hasFocus ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
                itemTitle.setTextColor(hasFocus ? UiKit.INK_DARK : UiKit.INK);
                itemSub.setTextColor(hasFocus ? UiKit.withAlpha(UiKit.INK_DARK, 170) : UiKit.INK_DIM);
                UiKit.lift(v, hasFocus, 1.03f, PerformanceProfile.animationMs(ctx, 120));
            });

            row.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                checkIndicator.setText(checked[idx] ? "[✓]" : "[  ]");
                checkIndicator.setTextColor(checked[idx] ? 0xFF34D399 : 0xFF9CA3AF);
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = UiKit.px(8);
            list.addView(row, rlp);
        }
        root.addView(list);

        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, UiKit.px(16), 0, 0);

        buttons.addView(Controls.button(ctx, "Seçilenleri Kur", v -> {
            Prefs.get(ctx).edit()
                    .putBoolean("nuvio_addons_configured", true)
                    .putBoolean("nuvio_addons_dismissed", true)
                    .putBoolean("nuvio_update_prompt_v1_0_0_9b", true)
                    .apply();
            host.back();
            Toast.makeText(ctx, "Seçilen eklentiler Nuvio'ya aktarılıyor...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    for (int i = 0; i < items.length; i++) {
                        if (!checked[i]) continue;
                        if ("deploy_configs".equals(items[i][2])) {
                            NuvioSetup.deployConfigs(ctx);
                        } else {
                            NuvioSetup.addAddon(ctx, items[i][2]);
                        }
                    }
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        Toast.makeText(ctx, "Nuvio eklenti kurulumu tamamlandı.", Toast.LENGTH_SHORT).show();
                    });
                } catch (Throwable t) {
                    android.util.Log.w("CineTvNuvio", "addon install error: " + t);
                }
            }, "nuvio-addon-installer").start();
        }));

        View spacer = new View(ctx);
        buttons.addView(spacer, new LinearLayout.LayoutParams(UiKit.px(16), 1));

        buttons.addView(Controls.button(ctx, "Şimdi Değil", v -> {
            Prefs.get(ctx).edit()
                    .putBoolean("nuvio_addons_configured", true)
                    .putBoolean("nuvio_addons_dismissed", true)
                    .putBoolean("nuvio_update_prompt_v1_0_0_9b", true)
                    .apply();
            host.back();
        }));

        root.addView(buttons);
        return scroll;
    }

    public static View buildNuvioInstallGuideModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(UiKit.px(32), UiKit.px(24), UiKit.px(32), UiKit.px(24));
        box.setBackground(Glass.panel(box, 24));

        FrameLayout qrPlate = new FrameLayout(ctx);
        qrPlate.setBackground(new Glass(qrPlate, UiKit.pxf(20), Glass.Style.FOCUSED));
        int size = UiKit.px(340);
        ImageView qr = new ImageView(ctx);
        String url = "https://github.com/AlptugDag/cinetv-launcher/releases/latest";
        Bitmap code = RemotePairing.qr(url, size, Color.parseColor("#0B1220"), Color.WHITE);
        if (code != null) qr.setImageBitmap(code);
        qrPlate.addView(qr, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        qrPlate.setPadding(UiKit.px(20), UiKit.px(20), UiKit.px(20), UiKit.px(20));
        box.addView(qrPlate);

        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(UiKit.px(32), 0, 0, 0);

        TextView t = UiKit.bold(ctx, 28, UiKit.INK);
        t.setText(tr ? "Nuvio Kurulum Rehberi" : "Nuvio Setup Guide");
        info.addView(t);

        TextView sub = UiKit.label(ctx, 16, UiKit.INK_DIM);
        sub.setLineSpacing(UiKit.pxf(6), 1.15f);
        sub.setMaxWidth(UiKit.px(640));
        sub.setText(tr
                ? "1. Telefonunuzun kamerasıyla yandaki QR kodu okutun.\n"
                + "2. Açılan sayfadan Nuvio APK dosyasını indirin veya telefonunuzdaki 'Send Files to TV' uygulamasıyla TV'nize gönderip kurun.\n"
                + "3. Kurulum tamamlandıktan sonra CineTV vitrinindeki tüm filmler doğrudan 4K ve Türkçe altyazıyla açılacaktır."
                : "1. Scan the QR code on the left with your phone.\n"
                + "2. Download the Nuvio APK and install it on your Android TV.\n"
                + "3. Once installed, all CineTV movies will play seamlessly in 4K with auto subtitles.");
        sub.setPadding(0, UiKit.px(12), 0, UiKit.px(24));
        info.addView(sub);

        TextView okBtn = Controls.button(ctx, tr ? "Anladım / Geri Dön" : "Done / Back", b -> host.back());
        info.addView(okBtn);
        box.addView(info);
        box.post(okBtn::requestFocus);
        return box;
    }

    public static View buildPartyQuizModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(32), UiKit.px(20), UiKit.px(32), UiKit.px(20));
        root.setBackground(Glass.panel(root, 28));

        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = UiKit.bold(ctx, 14, Color.parseColor("#F59E0B"));
        badge.setText(tr ? "CINETV SİNEFİL BİLMECE PARTİSİ" : "CINETV CINEPHILE TRIVIA PARTY");
        head.addView(badge);
        root.addView(head);

        final TextView qTitle = UiKit.bold(ctx, 24, UiKit.INK);
        qTitle.setPadding(0, UiKit.px(10), 0, UiKit.px(8));
        root.addView(qTitle);

        final TextView qHint = UiKit.label(ctx, 16, UiKit.INK_DIM);
        root.addView(qHint);

        final LinearLayout bodyRow = new LinearLayout(ctx);
        bodyRow.setOrientation(LinearLayout.HORIZONTAL);
        bodyRow.setGravity(Gravity.CENTER_VERTICAL);
        bodyRow.setPadding(0, UiKit.px(16), 0, UiKit.px(16));

        final ImageView qImg = new ImageView(ctx);
        qImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(UiKit.px(380), UiKit.px(220));
        ilp.rightMargin = UiKit.px(28);
        bodyRow.addView(qImg, ilp);

        final LinearLayout countdownBox = new LinearLayout(ctx);
        countdownBox.setOrientation(LinearLayout.VERTICAL);
        countdownBox.setGravity(Gravity.CENTER);
        final TextView timerTv = UiKit.bold(ctx, 48, Color.parseColor("#38BDF8"));
        countdownBox.addView(timerTv);
        final TextView timerLabel = UiKit.label(ctx, 14, UiKit.INK_DIM);
        timerLabel.setText(tr ? "Saniye Kaldı" : "Seconds Left");
        countdownBox.addView(timerLabel);
        bodyRow.addView(countdownBox);

        root.addView(bodyRow);

        final LinearLayout playersContainer = new LinearLayout(ctx);
        playersContainer.setOrientation(LinearLayout.HORIZONTAL);
        playersContainer.setPadding(0, UiKit.px(8), 0, UiKit.px(16));
        root.addView(playersContainer);

        final TextView answerReveal = UiKit.bold(ctx, 22, Color.parseColor("#34D399"));
        answerReveal.setVisibility(View.GONE);
        answerReveal.setPadding(0, UiKit.px(8), 0, UiKit.px(16));
        root.addView(answerReveal);

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView stopBtn = Controls.button(ctx, tr ? "Oyunu Bitir" : "End Game", v -> {
            com.cinetv.launcher.party.CinePartyManager.get(ctx).stopQuiz();
            host.back();
        });
        bRow.addView(stopBtn);
        root.addView(bRow);

        com.cinetv.launcher.party.CinePartyManager.get(ctx).setListener(new com.cinetv.launcher.party.CinePartyManager.Listener() {
            @Override
            public void onGameStateChanged(JSONObject state) {
                root.post(() -> {
                    boolean active = state.optBoolean("quizActive", false);
                    int qIdx = state.optInt("questionIndex", 0) + 1;
                    int total = state.optInt("totalQuestions", 10);
                    int count = state.optInt("countdown", 0);
                    boolean revealing = state.optBoolean("revealing", false);

                    if (!active && qIdx > total) {
                        qTitle.setText(tr ? "OYUN BİTTİ · ŞAMPİYON BELLİ OLDU!" : "GAME OVER · CHAMPION CROWNED!");
                        JSONArray pls = state.optJSONArray("players");
                        String winnerName = (pls != null && pls.length() > 0) ? pls.optJSONObject(0).optString("name") : "Sinefil";
                        qHint.setText(tr
                                ? "Tebrikler " + winnerName + "! Bugün, kazanan hangi filmi seçerse o izlenir!"
                                : "Congratulations " + winnerName + "! The winner picks tonight's movie!");
                        timerTv.setText("00:00");
                        bodyRow.setVisibility(View.VISIBLE);
                        answerReveal.setVisibility(View.GONE);
                        return;
                    }

                    qTitle.setText((tr ? "Soru " : "Question ") + qIdx + " / " + total + ": " + state.optString("question"));
                    qHint.setText(tr ? ("İpucu: " + state.optString("hint")) : ("Hint: " + state.optString("hint")));
                    timerTv.setText(String.valueOf(count));
                    if (count <= 3) timerTv.setTextColor(Color.parseColor("#F87171"));
                    else timerTv.setTextColor(Color.parseColor("#38BDF8"));

                    String imgUrl = state.optString("image");
                    if (!imgUrl.isEmpty()) {
                        new Thread(() -> {
                            android.graphics.Bitmap bmp = com.cinetv.launcher.service.PosterCache.load(ctx, imgUrl, 400, 400);
                            if (bmp != null) {
                                root.post(() -> qImg.setImageBitmap(bmp));
                            }
                        }).start();
                    }

                    if (revealing) {
                        answerReveal.setVisibility(View.VISIBLE);
                        answerReveal.setText((tr ? "DOĞRU CEVAP: " : "CORRECT ANSWER: ") + state.optString("displayAnswer"));
                    } else {
                        answerReveal.setVisibility(View.GONE);
                    }

                    playersContainer.removeAllViews();
                    JSONArray pls = state.optJSONArray("players");
                    if (pls != null) {
                        for (int i = 0; i < pls.length(); i++) {
                            JSONObject po = pls.optJSONObject(i);
                            if (po == null) continue;
                            TextView pBadge = UiKit.bold(ctx, 14, UiKit.INK);
                            String pName = po.optString("name");
                            int pScore = po.optInt("score");
                            boolean answered = po.optBoolean("answered");
                            if (revealing) {
                                boolean cor = po.optBoolean("correct");
                                String ans = po.optString("answer");
                                pBadge.setText(pName + ": " + (ans.isEmpty() ? (tr ? "Cevapsız" : "No answer") : ("\"" + ans + "\"")) + " (" + pScore + "p)");
                                pBadge.setTextColor(cor ? Color.parseColor("#34D399") : Color.parseColor("#F87171"));
                            } else {
                                pBadge.setText(pName + " (" + pScore + "p) " + (answered ? "✓" : "..."));
                                pBadge.setTextColor(answered ? Color.parseColor("#38BDF8") : UiKit.INK_DIM);
                            }
                            pBadge.setPadding(UiKit.px(14), UiKit.px(8), UiKit.px(14), UiKit.px(8));
                            pBadge.setBackground(Glass.panel(pBadge, 14));
                            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            plp.rightMargin = UiKit.px(12);
                            playersContainer.addView(pBadge, plp);
                        }
                    }
                });
            }

            @Override
            public void onRouletteFinished(String movieTitle, String suggestedBy) {}
        });

        root.post(stopBtn::requestFocus);
        return root;
    }

    public static View buildMovieRouletteModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackgroundColor(0xFF000000); // Pure pitch black arena for maximum tension & revolver realism

        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = UiKit.bold(ctx, 14, Color.parseColor("#F59E0B")); // Solar Amber
        badge.setText(tr ? "SİNEFİL FİLM ÖNERİ RULETİ" : "CINEPHILE MOVIE SUGGESTION ROULETTE");
        head.addView(badge);
        root.addView(head);

        LinearLayout stageRow = new LinearLayout(ctx);
        stageRow.setOrientation(LinearLayout.HORIZONTAL);
        stageRow.setGravity(Gravity.CENTER_VERTICAL);
        stageRow.setPadding(0, UiKit.px(16), 0, UiKit.px(16));

        // Left Column: Custom luxury photorealistic gunmetal cylinder
        final com.cinetv.launcher.party.RouletteWheelView wheel = new com.cinetv.launcher.party.RouletteWheelView(ctx);
        int wheelSize = UiKit.px(380);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(wheelSize, wheelSize);
        wlp.rightMargin = UiKit.px(36);
        stageRow.addView(wheel, wlp);

        // Right Column: Details, Suggestions, and Live Winner Card
        LinearLayout infoCol = new LinearLayout(ctx);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = UiKit.bold(ctx, 24, UiKit.INK);
        title.setText(tr ? "Akşam Hangi Filmi İzleyelim?" : "What Are We Watching Tonight?");
        infoCol.addView(title);

        TextView sub = UiKit.label(ctx, 15, UiKit.INK_DIM);
        sub.setPadding(0, UiKit.px(6), 0, UiKit.px(14));
        sub.setMaxWidth(UiKit.px(640));
        sub.setText(tr
                ? "Herkes kendi telefonundan 'Oyunlar' sekmesine girip bir film önersin. Çarkı çevirdiğinizde kazanan film belirlenir!"
                : "Everyone send a recommendation from their phone's 'Party' tab. Spin the wheel to crown tonight's movie!");
        infoCol.addView(sub);

        final LinearLayout suggestionsList = new LinearLayout(ctx);
        suggestionsList.setOrientation(LinearLayout.VERTICAL);
        suggestionsList.setPadding(0, 0, 0, UiKit.px(14));
        infoCol.addView(suggestionsList);

        final LinearLayout winnerCard = new LinearLayout(ctx);
        winnerCard.setOrientation(LinearLayout.VERTICAL);
        winnerCard.setBackground(Glass.panel(winnerCard, 20));
        winnerCard.setPadding(UiKit.px(22), UiKit.px(16), UiKit.px(22), UiKit.px(16));
        winnerCard.setVisibility(View.GONE);

        TextView winBadge = UiKit.bold(ctx, 13, Color.parseColor("#F59E0B"));
        winBadge.setText(tr ? "BU AKŞAMIN SEÇİLEN BAŞYAPITI" : "TONIGHT'S CHOSEN MASTERPIECE");
        winnerCard.addView(winBadge);

        final TextView winnerTv = UiKit.bold(ctx, 24, Color.WHITE);
        winnerTv.setPadding(0, UiKit.px(6), 0, UiKit.px(4));
        winnerCard.addView(winnerTv);

        final TextView winnerUserTv = UiKit.label(ctx, 14, Color.parseColor("#38BDF8"));
        winnerCard.addView(winnerUserTv);

        final TextView launchFilmBtn = Controls.button(ctx, tr ? "▶ Filmi TV'de Başlat" : "▶ Launch Movie on TV", v -> {
            String win = winnerTv.getText().toString();
            if (!win.isEmpty() && ctx instanceof com.cinetv.launcher.MainActivity) {
                ((com.cinetv.launcher.MainActivity) ctx).launchFilmByTitle(win);
            }
        });
        winnerCard.addView(launchFilmBtn);

        infoCol.addView(winnerCard);

        stageRow.addView(infoCol);
        root.addView(stageRow);

        final com.cinetv.launcher.party.CinePartyManager party = com.cinetv.launcher.party.CinePartyManager.get(ctx);
        final List<String> currentTitles = new ArrayList<>();

        final Runnable updateList = () -> {
            suggestionsList.removeAllViews();
            currentTitles.clear();
            JSONObject st = party.getState();
            org.json.JSONArray arr = st != null ? st.optJSONArray("suggestions") : null;
            if (arr != null && arr.length() > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s != null) {
                        String t = s.optString("title");
                        currentTitles.add(t);
                        TextView item = UiKit.label(ctx, 15, UiKit.INK);
                        item.setText(t + " (" + s.optString("user") + ")");
                        item.setPadding(0, UiKit.px(2), 0, UiKit.px(2));
                        suggestionsList.addView(item);
                    }
                }
            } else {
                currentTitles.add("The Godfather");
                currentTitles.add("Interstellar");
                currentTitles.add("Inception");
                currentTitles.add("Pulp Fiction");
                TextView empty = UiKit.label(ctx, 14, UiKit.INK_DIM);
                empty.setText(tr ? "Telefonda 'Oyunlar' sekmesinden film önerisi fırlatın..." : "Hurl movie suggestions from phone 'Party' tab...");
                suggestionsList.addView(empty);
            }
            wheel.setItems(currentTitles);
        };
        updateList.run();

        party.setListener(new com.cinetv.launcher.party.CinePartyManager.Listener() {
            @Override
            public void onGameStateChanged(JSONObject state) {
                root.post(updateList);
            }

            @Override
            public void onRouletteFinished(String movieTitle, String suggestedBy) {
                root.post(() -> {
                    winnerCard.setVisibility(View.VISIBLE);
                    winnerTv.setText(movieTitle);
                    winnerUserTv.setText(tr ? ("Öneren Sinefil: " + suggestedBy) : ("Suggested by: " + suggestedBy));
                });
            }
        });

        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, UiKit.px(10), 0, 0);

        TextView spinBtn = Controls.button(ctx, tr ? "Çarkı Çevir!" : "Spin the Wheel!", v -> {
            if (currentTitles.isEmpty()) {
                Toast.makeText(ctx, tr ? "Henüz film önerisi eklenmedi." : "No suggestions added yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            winnerCard.setVisibility(View.GONE);
            int winIdx = (int) (Math.random() * currentTitles.size());
            wheel.spinTo(winIdx, () -> {
                JSONObject winObj = party.spinRoulette();
                String mTitle = winObj != null ? winObj.optString("title") : currentTitles.get(winIdx);
                String mUser = winObj != null ? winObj.optString("user") : "Misafir";
                winnerCard.setVisibility(View.VISIBLE);
                winnerTv.setText(mTitle);
                winnerUserTv.setText(tr ? ("Öneren Sinefil: " + mUser) : ("Suggested by: " + mUser));
            });
        });
        btns.addView(spinBtn);

        View sp1 = new View(ctx);
        btns.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(16), 1));

        TextView clearBtn = Controls.button(ctx, tr ? "Temizle" : "Clear", v -> {
            party.clearRouletteSuggestions();
            suggestionsList.removeAllViews();
            winnerCard.setVisibility(View.GONE);
            updateList.run();
        });
        btns.addView(clearBtn);

        View sp2 = new View(ctx);
        btns.addView(sp2, new LinearLayout.LayoutParams(UiKit.px(16), 1));

        TextView closeBtn = Controls.button(ctx, tr ? "Kapat" : "Close", v -> host.back());
        btns.addView(closeBtn);

        root.addView(btns);
        root.post(spinBtn::requestFocus);
        return root;
    }

    public static View buildLetterboxdModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(32), UiKit.px(24), UiKit.px(32), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(tr ? "Letterboxd Sinema Günlüğü (Scrobble)" : "Letterboxd Cinema Diary");
        root.addView(title);

        TextView sub = UiKit.label(ctx, 16, UiKit.INK_DIM);
        sub.setPadding(0, UiKit.px(10), 0, UiKit.px(20));
        sub.setLineSpacing(UiKit.pxf(6), 1.15f);
        sub.setMaxWidth(UiKit.px(740));
        sub.setText(tr
                ? "CineTV'de izlediğiniz her film otomatik olarak resmi Letterboxd formatında kaydedilir:\n"
                + "Dosya Yolu: /sdcard/Download/cinetv_letterboxd_diary.csv\n\n"
                + "Letterboxd kullanıcı adınızı aşağıdan girerek günlüğü profilinizle bağlayabilir veya günlüğünüzü tek tıkla letterboxd.com/import adresine aktarabilirsiniz."
                : "Every movie you watch on CineTV is automatically recorded in official Letterboxd format:\n"
                + "File Path: /sdcard/Download/cinetv_letterboxd_diary.csv\n\n"
                + "Enter your Letterboxd username to link your diary or import into letterboxd.com/import.");
        root.addView(sub);

        String curUser = com.cinetv.launcher.service.LetterboxdSync.getUsername(ctx);
        LinearLayout userTile = Controls.tile(ctx,
                tr ? "Letterboxd Kullanıcı Adı" : "Letterboxd Username",
                curUser.isEmpty() ? (tr ? "Girilmedi (Girmek için dokunun)" : "Not set") : ("@" + curUser),
                !curUser.isEmpty(),
                v -> host.push(() -> buildLetterboxdUserInput(ctx, host)));
        root.addView(userTile);

        LinearLayout exportTile = Controls.tile(ctx,
                tr ? "Günlük Dosyasını İncele / QR Kodla Telefona Al" : "Export Diary CSV",
                tr ? "cinetv_letterboxd_diary.csv dosyasını telefonunuza indirin" : "Download diary to phone",
                true,
                v -> host.push(() -> {
                    LinearLayout qrBox = new LinearLayout(ctx);
                    qrBox.setGravity(Gravity.CENTER_VERTICAL);
                    qrBox.setPadding(UiKit.px(24), UiKit.px(20), UiKit.px(24), UiKit.px(20));
                    int size = UiKit.px(340);
                    ImageView qr = new ImageView(ctx);
                    String url = "https://letterboxd.com/import/";
                    Bitmap code = RemotePairing.qr(url, size, Color.parseColor("#0B1220"), Color.WHITE);
                    if (code != null) qr.setImageBitmap(code);
                    qrBox.addView(qr, new LinearLayout.LayoutParams(size, size));
                    LinearLayout qInfo = new LinearLayout(ctx);
                    qInfo.setOrientation(LinearLayout.VERTICAL);
                    qInfo.setPadding(UiKit.px(32), 0, 0, 0);
                    TextView qTitle = UiKit.bold(ctx, 24, UiKit.INK);
                    qTitle.setText(tr ? "Letterboxd İçe Aktarma" : "Letterboxd Import");
                    qInfo.addView(qTitle);
                    TextView qSub = UiKit.label(ctx, 16, UiKit.INK_DIM);
                    qSub.setText(tr
                            ? "QR kodu okutarak doğrudan Letterboxd içe aktarma sayfasına gidin ve televizyonunuzdaki cinetv_letterboxd_diary.csv dosyasını yükleyin."
                            : "Scan to open Letterboxd Import page and upload cinetv_letterboxd_diary.csv.");
                    qSub.setPadding(0, UiKit.px(10), 0, UiKit.px(20));
                    qInfo.addView(qSub);
                    TextView bBack = Controls.button(ctx, tr ? "Geri" : "Back", b -> host.back());
                    qInfo.addView(bBack);
                    qrBox.addView(qInfo);
                    qrBox.post(bBack::requestFocus);
                    return qrBox;
                }));
        root.addView(exportTile);

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setPadding(0, UiKit.px(16), 0, 0);
        TextView backBtn = Controls.button(ctx, tr ? "Tamam" : "Done", v -> host.back());
        bRow.addView(backBtn);
        root.addView(bRow);

        root.post(backBtn::requestFocus);
        return root;
    }

    private static View buildLetterboxdUserInput(Context ctx, ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        String cur = com.cinetv.launcher.service.LetterboxdSync.getUsername(ctx);
        final EditText et = field(ctx, cur, R.string.not_set, false);
        et.setHint(tr ? "Letterboxd kullanıcı adınız (örn: alptugdag)" : "Your Letterboxd username");
        return form(ctx, tr ? "Letterboxd Kullanıcı Adı" : "Letterboxd Username",
                tr ? "İzlenen filmlerin otomatik günlüğe kaydedilmesi için kullanıcı adınızı girin:" : "Enter your Letterboxd username for automatic cinema scrobbling:",
                et, v -> {
                    com.cinetv.launcher.service.LetterboxdSync.setUsername(ctx, et.getText().toString());
                    Toast.makeText(ctx, tr ? "Letterboxd profili kaydedildi!" : "Letterboxd profile saved!", Toast.LENGTH_SHORT).show();
                    host.back();
                });
    }

    private static View form(Context ctx, String titleStr, String bodyStr, EditText input, View.OnClickListener onSave) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(titleStr);
        root.addView(title);
        TextView body = UiKit.label(ctx, 20, UiKit.INK_DIM);
        body.setText(bodyStr);
        body.setPadding(0, UiKit.px(12), 0, UiKit.px(24));
        body.setMaxWidth(UiKit.px(760));
        root.addView(body);
        root.addView(input, new LinearLayout.LayoutParams(UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setPadding(0, UiKit.px(26), 0, 0);
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.save), onSave));
        root.addView(buttons);
        input.setOnEditorActionListener((v, id, e) -> {
            onSave.onClick(v);
            return true;
        });
        return root;
    }

    public static View buildCineTvTourModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));
        scroll.addView(root);

        TextView title = UiKit.bold(ctx, 30, UiKit.INK);
        title.setText(tr ? "CineTV Turu & Yenilikler (v1.0.0.19)" : "CineTV Tour & What's New (v1.0.0.19)");
        root.addView(title);

        TextView sub = UiKit.label(ctx, 16, UiKit.INK_DIM);
        sub.setPadding(0, UiKit.px(8), 0, UiKit.px(20));
        sub.setText(tr
                ? "Sürüm 1.0.0.16'dan bu yana eklenen ve geliştirilen tüm çığır açıcı özellikler:"
                : "All breakthrough features introduced and refined since version 1.0.0.16:");
        root.addView(sub);

        final String[][] features = {
                {"CineSub (İnteraktif Kültürel Altyazı & Gamze Vision Sahne Taraması)",
                 tr ? "Film izlerken kumandadaki Sarı tuşa veya mobil kumandaya basıldığında sahneyi durdurmadan kenarda beliren mini sinema notu kartı. Altyazıdaki tarihi olayları, felsefi kavramları ve arka plandaki tabloları/sanat eserlerini Gamze Vision ile tanır."
                    : "Press the Yellow key or phone remote during playback to inspect cultural trivia, paintings, and philosophical homages without pausing the video."},

                {"Çoklu Telefon Deneyimi & Sinefil Parti Oyunları",
                 tr ? "Aynı anda birden fazla telefonun TV'ye bağlanıp yarışabileceği 10 soruluk Sinefil Bilmece Quiz'i (kazananın seçtiği film izlenir!), film öneri ruleti ve gyro sensörüyle oynanan Sinefil Yılanı mini oyunu."
                    : "Multiplayer 10-question cinephile quiz with live mobile buzzers, movie roulette, and gyro-controlled poster snake game."},

                {"PWA Bağımsız Mobil Kumanda (Tarayıcısız Tam Ekran)",
                 tr ? "Mobil kumanda artık hem Android hem iOS'ta güvenlik engellerine takılmadan 1 tıkla cihaza yerel bir uygulama gibi kuruluyor. Apple Liquid Glass uyumlu şık simgesi ve pürüzsüz gyro VR lazer reticle'ı ile çalışır."
                    : "Installable PWA remote with native standalone fullscreen experience on iOS and Android, VR laser reticle, and zero browser chrome."},

                {"Letterboxd Otomatik Sinema Günlüğü (Scrobble)",
                 tr ? "CineTV'de izlediğiniz tüm filmler anında /sdcard/Download/cinetv_letterboxd_diary.csv kütüğüne işlenir ve profilinizle senkronize edilir."
                    : "Automatically records watched movies to an official Letterboxd-compatible diary CSV with one-click export."},

                {"Fiziksel Kumanda Renkli Tuş Özelleştirmesi",
                 tr ? "Kumandanızdaki Kırmızı, Yeşil, Sarı ve Mavi tuşlara CineSub, Gamze AI, Nuvio, YouTube veya dilediğiniz eylemleri atayabilme özgürlüğü."
                    : "Custom action bindings for Red, Green, Yellow, and Blue remote buttons."},

                {"Gamze AI \"Kiminle İzliyorsun?\" & Yerel Bağlam Hafızası",
                 tr ? "Gamze artık aile, sevgili, arkadaş veya yalnız izleme ortamına göre akıllı sansür ve yaş filtreleri uyguluyor; ev halkı zevklerini yerel depoda hatırlayıp kişiselleştiriyor."
                    : "Social context-aware curation with persistent local memory for household preferences and age restrictions."},

                {"Hans Zimmer & Apple Akustik Ses Motoru",
                 tr ? "44.1 kHz PCM sentezlenmiş dokunsal ahşap-akrilik tıklamalar, sinematik açılış akoru ve ayarlardan arayüz seslerini açıp kapatabilme seçeneği."
                    : "Pure 44.1 kHz PCM acoustic UI sounds with an option to toggle on/off in settings."},

                {"Fiziksel DVD Kutusu Mikro-Nefes & Fresnel Plastik Yansıması",
                 tr ? "Çiğ salınımlar yerine 2.2 derecelik ultra zarif mikro-nefes, polipropilen şeffaf kılıfın organik prizmatik ışık kırılması ve tema tabanlı renk derecelendirmesi."
                    : "Subtle 2.2-degree organic micro-breathing yaw with realistic thin-film Fresnel cellophane reflections and theme color grading."},

                {"Nuvio Akıllı Oynatma & Tanılama Rehberi",
                 tr ? "Televizyonda Nuvio veya Stremio bulunmadığında sessizce durmak yerine kullanıcıyı QR kodlu hızlı kurulum rehberiyle karşılayan akıllı sistem."
                    : "Friendly in-app setup guide modal if a compatible video player is missing."},

                {"TV Denetçisi & Harici Ses Sistemi Tespiti",
                 tr ? "HDMI ARC, eARC, Optik SPDIF, Bluetooth ve USB DAC ses sistemlerinin otomatik tespiti ve tek tıkla GitHub uyumluluk raporu QR kodu oluşturma."
                    : "Automatic detection of HDMI ARC/eARC, Optical, and Bluetooth audio devices, plus community hardware report QR code."}
        };

        for (String[] f : features) {
            FrameLayout card = new FrameLayout(ctx);
            card.setBackground(Glass.panel(card, 16));
            card.setPadding(UiKit.px(20), UiKit.px(14), UiKit.px(20), UiKit.px(14));

            LinearLayout cl = new LinearLayout(ctx);
            cl.setOrientation(LinearLayout.VERTICAL);

            TextView ft = UiKit.bold(ctx, 18, Color.parseColor("#38BDF8"));
            ft.setText(f[0]);
            cl.addView(ft);

            TextView fd = UiKit.label(ctx, 15, UiKit.INK_DIM);
            fd.setText(f[1]);
            fd.setLineSpacing(UiKit.pxf(4), 1.15f);
            fd.setPadding(0, UiKit.px(6), 0, 0);
            cl.addView(fd);

            card.addView(cl);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = UiKit.px(12);
            root.addView(card, clp);
        }

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setPadding(0, UiKit.px(14), 0, 0);
        TextView ok = Controls.button(ctx, tr ? "Harika / Geri Dön" : "Awesome / Back", v -> host.back());
        bRow.addView(ok);
        root.addView(bRow);

        root.post(ok::requestFocus);
        return scroll;
    }

    private static TextView inspectorHeader(Context ctx, String text) {
        TextView tv = UiKit.bold(ctx, 22, UiKit.INK);
        tv.setText(text);
        tv.setPadding(0, UiKit.px(16), 0, UiKit.px(8));
        return tv;
    }

    private static View inspectorCard(Context ctx, String text) {
        FrameLayout fl = new FrameLayout(ctx);
        fl.setBackground(Glass.panel(fl, 18));
        fl.setPadding(UiKit.px(20), UiKit.px(14), UiKit.px(20), UiKit.px(14));
        TextView tv = UiKit.label(ctx, 18, UiKit.INK_DIM);
        tv.setText(colorSupport(text));
        tv.setLineSpacing(UiKit.pxf(5), 1.15f);
        fl.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiKit.px(10);
        fl.setLayoutParams(lp);
        return fl;
    }

    /** "Destekleniyor"/"Supported" in green, "Desteklenmiyor"/"Unsupported" in red (the check marks too). */
    private static CharSequence colorSupport(String text) {
        android.text.SpannableString sp = new android.text.SpannableString(text);
        int red = Color.parseColor("#F87171"), green = Color.parseColor("#34D399");
        String[] bad = {"Desteklenmiyor", "Unsupported", "✗", "SDR Panel"};
        String[] good = {"Destekleniyor", "Supported", "✓"};
        for (String w : bad) paint(sp, text, w, red, null);
        for (String w : good) paint(sp, text, w, green, "Unsupported");
        return sp;
    }

    private static void paint(android.text.SpannableString sp, String text, String word, int color, String notInside) {
        int from = 0;
        while ((from = text.indexOf(word, from)) >= 0) {
            boolean inside = notInside != null && from >= 2 && text.regionMatches(true, from - 2, notInside, 0, notInside.length());
            if (!inside) {
                sp.setSpan(new android.text.style.ForegroundColorSpan(color), from, from + word.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), from, from + word.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            from += word.length();
        }
    }

    // ---- CineParty Games & Activities Folder Modal ------------------------------------------------

    public static View buildPartyGamesFolderModal(final Context ctx, final ModalHost host, final PartyLauncher launcher) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));
        scroll.addView(root);

        // Header with CineParty branding (Zero emojis)
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(Brand.cinequizWordmark());
        head.addView(title);

        TextView subBadge = UiKit.bold(ctx, 13, Color.parseColor("#8B5CF6"));
        subBadge.setText(tr ? " · OYUNLAR VE ETKİNLİKLER" : " · GAMES & PARTY HUB");
        subBadge.setLetterSpacing(0.06f);
        head.addView(subBadge);
        root.addView(head);

        TextView sub = UiKit.label(ctx, 15, UiKit.INK_DIM);
        sub.setPadding(0, UiKit.px(6), 0, UiKit.px(16));
        sub.setText(tr
                ? "Salondaki herkes telefonlarıyla bağlanıp yarışabilir veya TV kumandasıyla tek başınıza oynayabilirsiniz."
                : "Everyone in the living room can connect their phones or play solo using the TV remote.");
        root.addView(sub);

        // Phone Pairing Status Banner
        FrameLayout pairCard = new FrameLayout(ctx);
        pairCard.setBackground(Glass.panel(pairCard, 18));
        pairCard.setPadding(UiKit.px(20), UiKit.px(14), UiKit.px(20), UiKit.px(14));

        LinearLayout pairLayout = new LinearLayout(ctx);
        pairLayout.setOrientation(LinearLayout.HORIZONTAL);
        pairLayout.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout pTextCol = new LinearLayout(ctx);
        pTextCol.setOrientation(LinearLayout.VERTICAL);
        pTextCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView pTitle = UiKit.bold(ctx, 15, Color.parseColor("#38BDF8"));
        pTitle.setText(tr ? "Çoklu Telefon Bağlantısı" : "Multi-Phone Pairing");
        pTextCol.addView(pTitle);

        final String pairingUrl = com.cinetv.launcher.remote.RelayHost.pairingUrl(ctx, "party");
        TextView pUrl = UiKit.label(ctx, 14, UiKit.INK);
        pUrl.setText(pairingUrl);
        pUrl.setPadding(0, UiKit.px(3), 0, 0);
        pTextCol.addView(pUrl);
        pairLayout.addView(pTextCol);

        TextView pairBtn = Controls.button(ctx, tr ? "Eşleşme QR Kodu" : "Pairing QR", v -> {
            Panels.pairing(ctx, host, "party");
        });
        pairLayout.addView(pairBtn);

        pairCard.addView(pairLayout);
        LinearLayout.LayoutParams pclp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pclp.bottomMargin = UiKit.px(20);
        root.addView(pairCard, pclp);

        // Exactly 4 Party Games (CineSUB is NOT a game!)
        final Object[][] games = {
                {AppCatalog.PKG_SNAKE,
                 Brand.cineduelWordmark(),
                 tr ? "Sinema Nişancısı & Gyro Hedef Arenası. Telefonunuzu jiroskopla hareket ettirip TV ekranındaki replik, sahne, aktör ve trivia hedeflerine nişan alın ve vurun!"
                    : "Cinema shooter & gyro target arena. Aim your phone and shoot floating lines, scenes, actors and trivia!",
                 (Runnable) () -> { if (launcher != null) launcher.launchSnake(); },
                 (Runnable) () -> host.push(() -> buildCineDuelHowToPlayModal(ctx, host))},

                {AppCatalog.PKG_QUIZ,
                 Brand.cinequizWordmark(),
                 tr ? "10 Soruluk Canlı Sinefil Bilmecesi & Mobil Buzzer. TMDB aktör portreleri ve Gamze AI bilmeceleri; kazanan akşamın filmini seçer!"
                    : "10-question live cinephile trivia with mobile buzzers. Champion gets to launch tonight's movie!",
                 (Runnable) () -> { if (launcher != null) launcher.launchQuiz(); },
                 (Runnable) () -> host.push(() -> buildCineQuizHowToPlayModal(ctx, host))},

                {AppCatalog.PKG_ROULETTE,
                 Brand.cinerouletteWordmark(),
                 tr ? "6 Hazneli 3D Revolver Ruleti. Herkes telefonundan bir film fırlatsın; tetiği çekin, patlayan film salonda başlasın!"
                    : "6-chamber 3D revolver movie selector. Pull the trigger: blank click or movie explosion!",
                 (Runnable) () -> { if (launcher != null) launcher.launchRoulette(); },
                 (Runnable) () -> host.push(() -> buildCineRouletteHowToPlayModal(ctx, host))},

                {AppCatalog.PKG_SESSIZ,
                 Brand.cinesessizWordmark(),
                 tr ? "Sinema & Dizi Sessiz Sinema / Tabu. Telefona gelen gizli kartı anlatıcı oynar, salondakiler telefonlarından tahmin eder!"
                    : "Cinema charades & movie taboo. Guess the film on your phone with zero time or question limits!",
                 (Runnable) () -> { if (launcher != null) launcher.launchSessiz(); },
                 (Runnable) () -> host.push(() -> buildCineSessizHowToPlayModal(ctx, host))}
        };

        View firstPlayBtn = null;
        for (Object[] g : games) {
            final String pkg = (String) g[0];
            final CharSequence gTitle = (CharSequence) g[1];
            final String gDesc = (String) g[2];
            final Runnable onPlay = (Runnable) g[3];
            final Runnable onInfo = (Runnable) g[4];

            FrameLayout gCard = new FrameLayout(ctx);
            gCard.setBackground(Glass.panel(gCard, 18));
            gCard.setPadding(UiKit.px(16), UiKit.px(12), UiKit.px(16), UiKit.px(12));

            LinearLayout gRow = new LinearLayout(ctx);
            gRow.setOrientation(LinearLayout.HORIZONTAL);
            gRow.setGravity(Gravity.CENTER_VERTICAL);

            // Vector Art Preview Tile
            ImageView gArt = new ImageView(ctx);
            gArt.setScaleType(ImageView.ScaleType.FIT_XY);
            try {
                Bitmap bmp = AppArtwork.getCustomVectorCard(ctx, pkg, false);
                if (bmp != null) gArt.setImageBitmap(bmp);
            } catch (Throwable ignored) {}
            int artW = UiKit.px(96);
            int artH = UiKit.px(54);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(artW, artH);
            alp.rightMargin = UiKit.px(18);
            gRow.addView(gArt, alp);

            LinearLayout tCol = new LinearLayout(ctx);
            tCol.setOrientation(LinearLayout.VERTICAL);
            tCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView gTitleTv = UiKit.bold(ctx, 18, UiKit.INK);
            gTitleTv.setText(gTitle);
            tCol.addView(gTitleTv);

            TextView gDescTv = UiKit.label(ctx, 13.5f, UiKit.INK_DIM);
            gDescTv.setText(gDesc);
            gDescTv.setLineSpacing(UiKit.pxf(3.5f), 1.15f);
            gDescTv.setPadding(0, UiKit.px(3), 0, 0);
            tCol.addView(gDescTv);
            gRow.addView(tCol);

            TextView playBtn = Controls.button(ctx, tr ? "Oyunu Başlat ›" : "Play Game ›", v -> onPlay.run());
            if (firstPlayBtn == null) firstPlayBtn = playBtn;
            gRow.addView(playBtn);

            View sp1 = new View(ctx);
            gRow.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(12), 1));

            TextView infoBtn = Controls.button(ctx, tr ? "Nasıl Oynanır" : "Rules", v -> onInfo.run());
            gRow.addView(infoBtn);

            gCard.addView(gRow);
            LinearLayout.LayoutParams gclp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gclp.bottomMargin = UiKit.px(12);
            root.addView(gCard, gclp);
        }

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setPadding(0, UiKit.px(12), 0, 0);
        TextView backBtn = Controls.button(ctx, tr ? "Kapat" : "Close", v -> host.back());
        bRow.addView(backBtn);
        root.addView(bRow);

        if (firstPlayBtn != null) {
            final View fp = firstPlayBtn;
            root.post(fp::requestFocus);
        }
        return scroll;
    }

    private static View buildRuleSectionCard(Context ctx, String badgeText, String title, String desc, int accentColor) {
        FrameLayout card = new FrameLayout(ctx);
        card.setBackground(Glass.panel(card, 18));
        card.setPadding(UiKit.px(20), UiKit.px(14), UiKit.px(20), UiKit.px(14));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hRow = new LinearLayout(ctx);
        hRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = UiKit.bold(ctx, 12, accentColor);
        badge.setText(badgeText);
        badge.setPadding(UiKit.px(10), UiKit.px(3), UiKit.px(10), UiKit.px(3));
        badge.setBackground(Glass.panel(badge, 999));
        hRow.addView(badge);

        View spB = new View(ctx);
        hRow.addView(spB, new LinearLayout.LayoutParams(UiKit.px(12), 1));

        TextView titleTv = UiKit.bold(ctx, 17, UiKit.INK);
        titleTv.setText(title);
        hRow.addView(titleTv);
        col.addView(hRow);

        TextView descTv = UiKit.label(ctx, 14.5f, UiKit.INK_DIM);
        descTv.setText(desc);
        descTv.setLineSpacing(UiKit.pxf(3.5f), 1.15f);
        descTv.setPadding(0, UiKit.px(6), 0, 0);
        col.addView(descTv);

        card.addView(col);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = UiKit.px(12);
        card.setLayoutParams(clp);
        return card;
    }

    public static View buildSnakePhileHowToPlayModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));
        scroll.addView(root);

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(Brand.snakephileWordmark());
        root.addView(title);

        TextView sub = UiKit.bold(ctx, 14, Color.parseColor("#10B981"));
        sub.setText(tr ? "3D GERÇEKÇİ YILAN · NASIL OYNANIR?" : "3D REALISTIC SNAKE · HOW TO PLAY");
        sub.setPadding(0, UiKit.px(4), 0, UiKit.px(18));
        root.addView(sub);

        root.addView(buildRuleSectionCard(ctx, "01",
                tr ? "Oyunun Amacı & Kurgusu" : "Goal & Concept",
                tr ? "Gerçekçi 3D yılanı yöneterek ekranda beliren gerçek TMDB film afişlerini yiyin. Her afiş yendiğinde yılan uzar ve salonda sesli efekt çalar."
                   : "Guide the 3D snake to eat real movie posters. The snake grows longer with every eaten film poster.",
                Color.parseColor("#10B981")));

        root.addView(buildRuleSectionCard(ctx, "02",
                tr ? "TV Kumandası & Kontroller" : "TV Remote Controls",
                tr ? "TV Kumandasının Yön (D-Pad) tuşlarıyla yılanın yönünü anlık çevirin. [OK] tuşuna basarak yılanı hızlandırabilir, [BACK] tuşuyla oyundan çıkabilirsiniz."
                   : "Use the remote D-Pad arrows to steer. Hold [OK] to sprint and [BACK] to exit.",
                Color.parseColor("#10B981")));

        root.addView(buildRuleSectionCard(ctx, "03",
                tr ? "Telefonla Katılım & 3D Gyro" : "Phone Controls & Gyro",
                tr ? "Telefon kumandasındaki Oyunlar sekmesinden dokunmatik tuşlarla veya telefonu sağa-sola, öne-arkaya eğerek 3D jiroskop (gyro) sensörüyle yılanı yönetebilirsiniz!"
                   : "Play from the Party tab on your mobile remote using on-screen D-pad or 3D gyroscope tilt controls!",
                Color.parseColor("#10B981")));

        root.addView(buildRuleSectionCard(ctx, "04",
                tr ? "Akşamın Filmi Kuralı" : "Tonight's Movie Rule",
                tr ? "Yılan kendi kuyruğunu ısırdığında oyun biter. Yılanın en son yiyemediği afişin ait olduğu film, oyun biter bitmez televizyonda otomatik olarak açılır!"
                   : "When the snake bites itself, the game ends. The film poster the snake failed to eat is crowned and immediately launched on TV!",
                Color.parseColor("#10B981")));

        root.addView(buildRuleSectionCard(ctx, "05",
                tr ? "Gamze AI Film Lore'u" : "Gamze AI Film Lore",
                tr ? "Oyun bittiğinde Gamze AI devreye girer; o filme ait yönetmen notlarını, felsefi trivia'yı ve neden bu akşam izlenmesi gerektiğini akıllı rozet olarak sunar."
                   : "Upon game over, Gamze AI delivers deep director trivia and cinematic lore explaining why tonight's film was chosen.",
                Color.parseColor("#10B981")));

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setPadding(0, UiKit.px(10), 0, 0);
        TextView ok = Controls.button(ctx, tr ? "Anladım / Geri" : "Got It / Back", v -> host.back());
        bRow.addView(ok);
        root.addView(bRow);
        root.post(ok::requestFocus);
        return scroll;
    }

    public static View buildCineQuizHowToPlayModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));
        scroll.addView(root);

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(Brand.cinequizWordmark());
        root.addView(title);

        TextView sub = UiKit.bold(ctx, 14, Color.parseColor("#8B5CF6"));
        sub.setText(tr ? "ÇOK OYUNCULU BİLMECELİ SİNEMA YARIŞMASI" : "MULTIPLAYER TRIVIA ARENA");
        sub.setPadding(0, UiKit.px(4), 0, UiKit.px(18));
        root.addView(sub);

        root.addView(buildRuleSectionCard(ctx, "01",
                tr ? "10 Soruluk Yarışma & Süre Kuralı" : "10 Questions & 10s Timer",
                tr ? "Yarışma başladığında TV ekranına sırayla 10 adet soru gelir. Her soru için 10 saniyelik geri sayım süresi bulunur."
                   : "10 trivia questions appear on the TV screen, each with a 10-second timer.",
                Color.parseColor("#8B5CF6")));

        root.addView(buildRuleSectionCard(ctx, "02",
                tr ? "Çoklu Telefonla Anlık Katılım" : "Multi-Phone Buzzers",
                tr ? "Salondaki tüm katılımcılar telefonlarından QR veya IP adresiyle odaya bağlanır. Sorular ekrana geldikçe herkes kendi telefonundan A, B, C, D seçeneklerine basarak ya da metin kutusuna cevabını yazarak katılır."
                   : "Room players connect via phone remote to answer A, B, C, D choices or free-text answers.",
                Color.parseColor("#8B5CF6")));

        root.addView(buildRuleSectionCard(ctx, "03",
                tr ? "Puanlama & Doğru Cevaplar" : "Scoring & Results",
                tr ? "10 saniyelik süre bittiğinde doğru cevap TV'de açılır. Soruyu doğru bilen tüm oyuncular anında +10 puan kazanır."
                   : "When the timer hits zero, the correct answer reveals. Correct players earn +10 points.",
                Color.parseColor("#8B5CF6")));

        root.addView(buildRuleSectionCard(ctx, "04",
                tr ? "Şampiyonun Film Seçimi" : "Champion's Movie Privilege",
                tr ? "10 soru sonunda en çok puan toplayan oyuncu Şampiyon ilan edilir. Kazananın telefonunda film arama kutusu açılır ve seçtiği film anında televizyonda başlar!"
                   : "The highest-scoring player is crowned Champion and selects tonight's movie to launch on TV!",
                Color.parseColor("#8B5CF6")));

        root.addView(buildRuleSectionCard(ctx, "05",
                tr ? "Gamze AI Dinamik Soruları" : "Gamze AI Live Engine",
                tr ? "Soruların tekrara düşmesini engellemek için yerleşik Gamze AI devreye girer ve anlık olarak benzersiz yeni sinema bilmeceleri üretir."
                   : "Gamze AI dynamically generates endless fresh cinema trivia questions so the game never repeats.",
                Color.parseColor("#8B5CF6")));

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setPadding(0, UiKit.px(10), 0, 0);
        TextView ok = Controls.button(ctx, tr ? "Anladım / Geri" : "Got It / Back", v -> host.back());
        bRow.addView(ok);
        root.addView(bRow);
        root.post(ok::requestFocus);
        return scroll;
    }

    public static View buildCineRouletteHowToPlayModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));
        scroll.addView(root);

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(Brand.cinerouletteWordmark());
        root.addView(title);

        TextView sub = UiKit.bold(ctx, 14, Color.parseColor("#38BDF8"));
        sub.setText(tr ? "3D REVOLVER FİLM SEÇİCİ · NASIL OYNANIR?" : "3D REVOLVER MOVIE CHOOSER · HOW TO PLAY");
        sub.setPadding(0, UiKit.px(4), 0, UiKit.px(18));
        root.addView(sub);

        root.addView(buildRuleSectionCard(ctx, "01",
                tr ? "Revolver Göbeği & Mermi Delikleri" : "Revolver Cylinder Chambers",
                tr ? "3D revolver göbeğinde 6 adet mermi deliği bulunur. Her bir mermi deliğine salonun eklediği ya da Gamze AI'ın önerdiği gerçek bir film afişi yerleştirilir."
                   : "The 3D revolver cylinder contains 6 chambers, each holding a real DVD-boxless movie poster.",
                Color.parseColor("#38BDF8")));

        root.addView(buildRuleSectionCard(ctx, "02",
                tr ? "Telefonlardan Film Fırlatma" : "Hurl Movies From Phones",
                tr ? "Salondaki herkes kendi telefonundan izlemek istediği bir filmi hazneye fırlatır veya tematik hazır listelerden (90'lar Suç, Bilim Kurgu, Kült Sinema) birini seçer."
                   : "Participants submit their favorite movie picks from their phones or choose curated AI themes.",
                Color.parseColor("#38BDF8")));

        root.addView(buildRuleSectionCard(ctx, "03",
                tr ? "Rus Ruleti Kurgusu (Klik veya Güm)" : "Russian Roulette Mechanics",
                tr ? "10 saniyelik zaman sayacında sırası gelen oyuncu tetiğe basar. Boş kovan denk gelirse 'KLİK' sesiyle sıra geçer; canlı mermi patlarsa 'GÜM' efektiyle o deliğe ait film TV'de başlar!"
                   : "Players pull the trigger during the 10s tension countdown: blank click passes turn; live round explodes and starts the film on TV!",
                Color.parseColor("#38BDF8")));

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setPadding(0, UiKit.px(10), 0, 0);
        TextView ok = Controls.button(ctx, tr ? "Anladım / Geri" : "Got It / Back", v -> host.back());
        bRow.addView(ok);
        root.addView(bRow);
        root.post(ok::requestFocus);
        return scroll;
    }

    public static View buildCineSessizHowToPlayModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));
        scroll.addView(root);

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(Brand.cinesessizWordmark());
        root.addView(title);

        TextView sub = UiKit.bold(ctx, 14, Color.parseColor("#F59E0B"));
        sub.setText(tr ? "SİNEMA & DİZİ TABU · NASIL OYNANIR?" : "CINEMA CHARADES · HOW TO PLAY");
        sub.setPadding(0, UiKit.px(4), 0, UiKit.px(18));
        root.addView(sub);

        root.addView(buildRuleSectionCard(ctx, "01",
                tr ? "Oyunun Konsepti & Süresiz Akış" : "Concept & Endless Play",
                tr ? "Klasik sessiz sinema ve Tabu oyununun sinema-dizi versiyonu! Soru veya zaman sınırı yoktur; dilediğiniz kadar tur oynayabilirsiniz."
                   : "The cinema-edition of Charades & Taboo. Zero time or question limits; enjoy endless party rounds.",
                Color.parseColor("#F59E0B")));

        root.addView(buildRuleSectionCard(ctx, "02",
                tr ? "Anlatıcının Gizli Kasası" : "Teller's Secret Vault",
                tr ? "Anlatıcı rolündeki kişinin telefonunda gizli sinema kartı ve yasaklı kelimeler gözükür (Örn: Fight Club - Norton, Pitt, Sabun, Marla, İlk Kural). Anlatıcı bu kelimeleri söylemeden filmi anlatır!"
                   : "The teller's phone reveals the secret film card and taboo words. The teller describes the movie without uttering any taboo words!",
                Color.parseColor("#F59E0B")));

        root.addView(buildRuleSectionCard(ctx, "03",
                tr ? "Salondakilerin Canlı Tahminleri" : "Live Guesses",
                tr ? "Diğer katılımcılar telefonlarındaki tahmin kutusundan cevabı yazıp fırlatırlar. TV'de canlı akış görünür; doğru cevabı ilk yazan +10 puan kazanır!"
                   : "Other players submit guesses from their phones. The live feed streams on TV; first correct answer scores +10 pts!",
                Color.parseColor("#F59E0B")));

        root.addView(buildRuleSectionCard(ctx, "04",
                tr ? "Gamze AI Dinamik Kartlar" : "Gamze Dynamic Cards",
                tr ? "'Gamze Yeni Kartlar' butonuyla hem yerli hem yabancı binlerce film, yönetmen ve kült dizi sahnesi anında oyuna dahil edilir."
                   : "Generate thousands of Turkish and global cinema cards on the fly with Gamze AI.",
                Color.parseColor("#F59E0B")));

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setPadding(0, UiKit.px(10), 0, 0);
        TextView ok = Controls.button(ctx, tr ? "Anladım / Geri" : "Got It / Back", v -> host.back());
        bRow.addView(ok);
        root.addView(bRow);
        root.post(ok::requestFocus);
        return scroll;
    }

    public static View buildCineSubHowToPlayModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));
        scroll.addView(root);

        TextView title = UiKit.bold(ctx, 28, UiKit.INK);
        title.setText(Brand.cinesubWordmark());
        root.addView(title);

        TextView sub = UiKit.bold(ctx, 14, Color.parseColor("#F59E0B"));
        sub.setText(tr ? "KÜLTÜREL ALTYAZI REHBERİ · NASIL ÇALIŞIR?" : "CULTURAL SUBTITLES · HOW IT WORKS");
        sub.setPadding(0, UiKit.px(4), 0, UiKit.px(18));
        root.addView(sub);

        root.addView(buildRuleSectionCard(ctx, "01",
                tr ? "Kültürel Not & Sahne Analizi" : "Cultural Notes & Scene Trivia",
                tr ? "Film oynarken sahneyi durdurmadan altyazıda geçen tarihi şahsiyetleri, felsefi kavramları ve arka plandaki sanat eserlerini tanıtan mini kenar bilgi kartı."
                   : "Inspect cultural homages, philosophical terms, and paintings during playback without pausing the movie.",
                Color.parseColor("#F59E0B")));

        root.addView(buildRuleSectionCard(ctx, "02",
                tr ? "Kumandadaki Sarı Tuş (Yellow Key)" : "Remote Yellow Key",
                tr ? "Fiziksel TV kumandanızdaki SARI (Yellow) tuşa basarak veya mobil kumandadaki CineSUB butonuna dokunarak kartı istediğiniz an ekrana getirebilirsiniz."
                   : "Press the YELLOW key on your TV remote or tap the CineSUB button on your phone remote to trigger the card instantly.",
                Color.parseColor("#F59E0B")));

        root.addView(buildRuleSectionCard(ctx, "03",
                tr ? "Tüm Oynatıcılara Tam Entegre" : "Fully Integrated",
                tr ? "Nuvio, YouTube dahili oynatıcı ve APK ile kurulan tüm harici video oynatıcılarla tam uyumlu çalışır; altyazı alanını asla kapatmaz ve izleme deneyimini bölmez."
                   : "Fully integrated into Nuvio, YouTube, and external APK video players without blocking subtitle zone.",
                Color.parseColor("#F59E0B")));

        LinearLayout bRow = new LinearLayout(ctx);
        bRow.setOrientation(LinearLayout.HORIZONTAL);
        bRow.setPadding(0, UiKit.px(14), 0, 0);

        TextView testBtn = Controls.button(ctx, tr ? "Canlı Sahne Kartını Test Et ›" : "Test Live Subtitle Card ›", v -> {
            host.back();
            if (ctx instanceof com.cinetv.launcher.MainActivity) {
                ((com.cinetv.launcher.MainActivity) ctx).showLiveCineSubScene();
            } else {
                com.cinetv.launcher.ui.CineSubOverlay.trigger(ctx);
            }
        });
        bRow.addView(testBtn);

        View spSub = new View(ctx);
        bRow.addView(spSub, new LinearLayout.LayoutParams(UiKit.px(16), 1));

        TextView ok = Controls.button(ctx, tr ? "Anladım / Geri" : "Got It / Back", v -> host.back());
        bRow.addView(ok);
        root.addView(bRow);
        root.post(testBtn::requestFocus);
        return scroll;
    }

    public static View buildCineSessizModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 28));

        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView wordmark = UiKit.bold(ctx, 16, UiKit.INK);
        wordmark.setText(Brand.cinesessizWordmark());
        wordmark.setLetterSpacing(0.04f);
        head.addView(wordmark);

        TextView subBadge = UiKit.bold(ctx, 13, Color.parseColor("#F59E0B"));
        subBadge.setText(tr ? " · SİNEMA VE DİZİ TABU ARENASI" : " · CINEMA & SERIES TABOO ARENA");
        subBadge.setLetterSpacing(0.06f);
        head.addView(subBadge);
        root.addView(head);

        final com.cinetv.launcher.party.CinePartyManager party = com.cinetv.launcher.party.CinePartyManager.get(ctx);
        if (!party.isSessizActive()) {
            party.startSessiz();
        }

        // Two Column Layout
        LinearLayout stageRow = new LinearLayout(ctx);
        stageRow.setOrientation(LinearLayout.HORIZONTAL);
        stageRow.setPadding(0, UiKit.px(16), 0, UiKit.px(16));

        // Left Column: Secret Card Status on TV
        LinearLayout cardBox = new LinearLayout(ctx);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        cardBox.setBackground(Glass.panel(cardBox, 22));
        cardBox.setPadding(UiKit.px(24), UiKit.px(20), UiKit.px(24), UiKit.px(20));

        final TextView tellerTv = UiKit.bold(ctx, 18, Color.parseColor("#F59E0B"));
        cardBox.addView(tellerTv);

        final TextView catBadge = UiKit.bold(ctx, 14, Color.parseColor("#38BDF8"));
        catBadge.setPadding(0, UiKit.px(8), 0, UiKit.px(12));
        cardBox.addView(catBadge);

        TextView secretVaultNotice = UiKit.label(ctx, 15, UiKit.INK_DIM);
        secretVaultNotice.setLineSpacing(UiKit.pxf(4), 1.15f);
        secretVaultNotice.setText(tr
                ? "GİZLİ SİNEMA KARTI ANLATICI TELEFONUNDA AÇIK\n\n"
                + "• Anlatıcı, karttaki 5 yasaklı kelimeyi kullanmadan filmi/diziyi/aktörü salondakilere anlatır.\n"
                + "• Salondaki oyuncular telefonlarındaki CineTV uzantısından tahminlerini yazar.\n"
                + "• Doğru bilen oyuncu anında +10 puan kazanır ve sonraki karta geçilir!"
                : "SECRET CARD IS OPEN ON THE TELLER'S PHONE\n\n"
                + "• Teller describes the cinema element without saying the 5 taboo words.\n"
                + "• Room players submit their guesses via the phone remote.\n"
                + "• First correct guess scores +10 points and advances to next card!");
        cardBox.addView(secretVaultNotice);

        stageRow.addView(cardBox);

        View spStage = new View(ctx);
        stageRow.addView(spStage, new LinearLayout.LayoutParams(UiKit.px(24), 1));

        // Right Column: Live Guess Stream & Leaderboard
        LinearLayout rightCol = new LinearLayout(ctx);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView feedHeader = UiKit.bold(ctx, 15, UiKit.INK);
        feedHeader.setText(tr ? "Canlı Tahmin Akışı (Telefonlardan):" : "Live Guesses (From Phones):");
        rightCol.addView(feedHeader);

        final LinearLayout feedList = new LinearLayout(ctx);
        feedList.setOrientation(LinearLayout.VERTICAL);
        feedList.setPadding(0, UiKit.px(8), 0, UiKit.px(14));
        rightCol.addView(feedList);

        TextView boardHeader = UiKit.bold(ctx, 15, UiKit.INK);
        boardHeader.setText(tr ? "Puan Tablosu:" : "Leaderboard:");
        rightCol.addView(boardHeader);

        final LinearLayout leaderboardList = new LinearLayout(ctx);
        leaderboardList.setOrientation(LinearLayout.HORIZONTAL);
        leaderboardList.setPadding(0, UiKit.px(8), 0, 0);
        rightCol.addView(leaderboardList);

        stageRow.addView(rightCol);
        root.addView(stageRow);

        final Runnable updateSessiz = () -> {
            JSONObject st = party.getState();
            if (st == null) return;
            String tName = st.optString("tellerName", "Sinefil");
            tellerTv.setText((tr ? "Anlatıcı: " : "Current Teller: ") + tName);
            String cat = st.optString("sessizCategory", "Kült Film");
            catBadge.setText((tr ? "Kategori: " : "Category: ") + cat);

            // Update Feed
            feedList.removeAllViews();
            JSONArray feed = st.optJSONArray("sessizFeed");
            if (feed != null && feed.length() > 0) {
                for (int i = 0; i < Math.min(5, feed.length()); i++) {
                    JSONObject f = feed.optJSONObject(i);
                    if (f == null) continue;
                    boolean cor = f.optBoolean("correct");
                    String u = f.optString("user");
                    String g = f.optString("guess");

                    FrameLayout chip = new FrameLayout(ctx);
                    chip.setBackground(Glass.panel(chip, 14));
                    chip.setPadding(UiKit.px(14), UiKit.px(8), UiKit.px(14), UiKit.px(8));

                    LinearLayout fRow = new LinearLayout(ctx);
                    fRow.setGravity(Gravity.CENTER_VERTICAL);

                    TextView uTv = UiKit.bold(ctx, 13, cor ? Color.parseColor("#34D399") : Color.parseColor("#F59E0B"));
                    uTv.setText(u + ": ");
                    fRow.addView(uTv);

                    TextView gTv = UiKit.label(ctx, 14, cor ? Color.WHITE : UiKit.INK);
                    gTv.setText("\"" + g + "\"" + (cor ? "  ✓ (DOĞRU BİLDİ! +10p)" : ""));
                    if (cor) gTv.setTypeface(FontManager.getBold(ctx));
                    fRow.addView(gTv);

                    chip.addView(fRow);
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clp.bottomMargin = UiKit.px(6);
                    feedList.addView(chip, clp);
                }
            } else {
                TextView empty = UiKit.label(ctx, 13, UiKit.INK_DIM);
                empty.setText(tr ? "Telefonlardan tahmin bekleniyor..." : "Waiting for guesses from phones...");
                feedList.addView(empty);
            }

            // Update Leaderboard
            leaderboardList.removeAllViews();
            JSONArray pls = st.optJSONArray("players");
            if (pls != null) {
                for (int i = 0; i < pls.length(); i++) {
                    JSONObject po = pls.optJSONObject(i);
                    if (po == null) continue;
                    TextView pBadge = UiKit.bold(ctx, 13, UiKit.INK);
                    pBadge.setText(po.optString("name") + " (" + po.optInt("score") + "p)");
                    pBadge.setPadding(UiKit.px(12), UiKit.px(6), UiKit.px(12), UiKit.px(6));
                    pBadge.setBackground(Glass.panel(pBadge, 12));
                    LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    plp.rightMargin = UiKit.px(10);
                    leaderboardList.addView(pBadge, plp);
                }
            }
        };
        updateSessiz.run();

        party.setListener(new com.cinetv.launcher.party.CinePartyManager.Listener() {
            @Override
            public void onGameStateChanged(JSONObject state) {
                root.post(updateSessiz);
            }
            @Override
            public void onMovieLaunchRequested(String movieTitle) {}
        });

        // Control Buttons Row
        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, UiKit.px(12), 0, 0);

        TextView nextBtn = Controls.button(ctx, tr ? "Sonraki Kart ›" : "Next Card ›", v -> party.nextSessizCard(false));
        btns.addView(nextBtn);

        View spB = new View(ctx);
        btns.addView(spB, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView passBtn = Controls.button(ctx, tr ? "Pas Geç" : "Pass", v -> party.nextSessizCard(true));
        btns.addView(passBtn);

        View spG = new View(ctx);
        btns.addView(spG, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView geminiBtn = Controls.button(ctx, tr ? "Gamze Yeni Kartlar" : "Gamze New Cards", v -> {
            party.requestGamzeSessizCards(3, () -> {
                Toast.makeText(ctx, tr ? "Gamze AI Yeni Kartları Hazırladı!" : "Gamze AI Cards Ready!", Toast.LENGTH_SHORT).show();
            });
        });
        btns.addView(geminiBtn);

        View spI = new View(ctx);
        btns.addView(spI, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView infoBtn = Controls.button(ctx, tr ? "Nasıl Oynanır" : "Rules", v -> host.push(() -> buildCineSessizHowToPlayModal(ctx, host)));
        btns.addView(infoBtn);

        View spE = new View(ctx);
        btns.addView(spE, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView endBtn = Controls.button(ctx, tr ? "Oyunu Bitir" : "End Game", v -> {
            party.stopSessiz();
            host.back();
        });
        btns.addView(endBtn);

        root.addView(btns);
        root.post(nextBtn::requestFocus);
        return root;
    }

    public static View buildUpdateModal(final Context ctx, final ModalHost host, final com.cinetv.launcher.service.AppUpdater.ReleaseInfo info) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(26), UiKit.px(36), UiKit.px(26));
        root.setBackground(Glass.panel(root, 24));
        root.setFocusable(true);

        TextView title = UiKit.bold(ctx, 26, UiKit.INK);
        title.setText(tr ? ("CineTV Güncellemesi Hazır (" + info.tagName + ")") : ("CineTV Update Ready (" + info.tagName + ")"));
        root.addView(title);

        TextView notes = UiKit.label(ctx, 16, UiKit.INK_DIM);
        notes.setPadding(0, UiKit.px(10), 0, UiKit.px(22));
        notes.setLineSpacing(UiKit.pxf(5), 1.15f);
        notes.setMaxWidth(UiKit.px(740));
        String text = info.releaseNotes != null && !info.releaseNotes.isEmpty() ? info.releaseNotes : (tr ? "Performans iyileştirmeleri ve hata düzeltmeleri." : "Performance improvements and bug fixes.");
        if (text.length() > 320) text = text.substring(0, 318) + "...";
        notes.setText(text);
        root.addView(notes);

        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        TextView updateBtn = Controls.button(ctx, tr ? "Şimdi Güncelle" : "Update Now", v -> {
            host.closeImmediate();
            if (ctx instanceof android.app.Activity) {
                com.cinetv.launcher.service.AppUpdater.downloadAndInstall((android.app.Activity) ctx, info);
            }
        });
        btns.addView(updateBtn);

        View sp = new View(ctx);
        btns.addView(sp, new LinearLayout.LayoutParams(UiKit.px(16), 1));

        TextView closeBtn = Controls.button(ctx, tr ? "Daha Sonra / Kapat" : "Later / Close", v -> host.back());
        btns.addView(closeBtn);

        root.addView(btns);
        root.post(updateBtn::requestFocus);
        return root;
    }

    public static View buildUpToDateModal(final Context ctx, final ModalHost host, final String currentVersion) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(26), UiKit.px(36), UiKit.px(26));
        root.setBackground(Glass.panel(root, 24));
        root.setFocusable(true);

        TextView title = UiKit.bold(ctx, 26, UiKit.INK);
        title.setText(tr ? "CineTV En Güncel Sürümde" : "CineTV is Up to Date");
        root.addView(title);

        TextView desc = UiKit.label(ctx, 16, UiKit.INK_DIM);
        desc.setPadding(0, UiKit.px(10), 0, UiKit.px(22));
        desc.setText(tr
                ? ("Televizyonunuzda en son CineTV sürümü (v" + currentVersion + ") kurulu. Tüm özellikler günceldir.")
                : ("Your TV is running the latest CineTV version (v" + currentVersion + "). Everything is up to date."));
        root.addView(desc);

        TextView okBtn = Controls.button(ctx, tr ? "Tamam" : "OK", v -> host.back());
        root.addView(okBtn);
        root.post(okBtn::requestFocus);
        return root;
    }

    public static View buildAcousticsCalibrationModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 26));
        root.setFocusable(true);

        TextView badge = UiKit.bold(ctx, 13, Color.parseColor("#38BDF8"));
        badge.setText(tr ? "ODA AKUSTİĞİ & KALİBRASYON" : "ROOM ACOUSTICS & CALIBRATION");
        root.addView(badge);

        TextView title = UiKit.bold(ctx, 26, UiKit.INK);
        title.setText(tr ? "Oda Akustiğini Ayarla" : "Tune Living Room Acoustics");
        title.setPadding(0, UiKit.px(6), 0, UiKit.px(12));
        root.addView(title);

        final com.cinetv.launcher.device.AcousticsCalibrator.AudioOutputInfo outInfo =
                com.cinetv.launcher.device.AcousticsCalibrator.detectAudioOutput(ctx);
        boolean isCal = com.cinetv.launcher.device.AcousticsCalibrator.isCalibrated(ctx);

        TextView statusTv = UiKit.bold(ctx, 15, isCal ? Color.parseColor("#10B981") : UiKit.INK_DIM);
        statusTv.setText((tr ? "Ses Çıkışı: " : "Audio Output: ") + outInfo.name + " (" + outInfo.type + ")\n"
                + (isCal ? (tr ? "✓ Telefon ile kalibre edildi (Özel EQ aktif)" : "✓ Calibrated via phone mic (Active EQ)")
                         : (tr ? "Varsayılan Akustik Profil Devrede" : "Default Acoustics Profile Active")));
        statusTv.setPadding(0, 0, 0, UiKit.px(16));
        root.addView(statusTv);

        TextView desc = UiKit.label(ctx, 15, UiKit.INK_DIM);
        desc.setLineSpacing(UiKit.pxf(4), 1.15f);
        desc.setMaxWidth(UiKit.px(740));
        desc.setText(tr
                ? "Telefonunuzun mikrofonu ile TV'den çalınacak 4 saniyelik akustik test sesini dinleyerek salonunuzun yankı ve frekans profilini otomatik olarak hesaplayabilirsiniz.\n\n"
                + "1. Telefonunuzdan CineTV uzaktan kumandasını açın.\n"
                + "2. Ayar çarkına dokunup 'Oda Akustiğini Kalibre Et' butonuna basın.\n"
                + "3. TV'den gelen test sesini dinleyin; kalibrasyon saniyeler içinde tamamlanır."
                : "Tune living room reverb and frequency response automatically using your phone's microphone.\n\n"
                + "1. Open CineTV web remote on your phone.\n"
                + "2. Tap the gear icon and select 'Calibrate Room Acoustics'.\n"
                + "3. Listen to the 4-second sweep tone from the TV.");
        desc.setPadding(0, 0, 0, UiKit.px(20));
        root.addView(desc);

        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        TextView sweepBtn = Controls.button(ctx, tr ? "▶ Akustik Sweep Testi Çal" : "▶ Play Test Sweep", v -> {
            Toast.makeText(ctx, tr ? "Akustik test sinyali çalınıyor..." : "Playing acoustic sweep...", Toast.LENGTH_SHORT).show();
            com.cinetv.launcher.device.AcousticsCalibrator.playSweep(ctx, () -> {
                Toast.makeText(ctx, tr ? "Akustik test tamamlandı." : "Sweep finished.", Toast.LENGTH_SHORT).show();
            });
        });
        btns.addView(sweepBtn);

        View sp1 = new View(ctx);
        btns.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView resetBtn = Controls.button(ctx, tr ? "Varsayılana Sıfırla" : "Reset to Default", v -> {
            com.cinetv.launcher.device.AcousticsCalibrator.resetCalibration(ctx);
            Toast.makeText(ctx, tr ? "Akustik kalibrasyon sıfırlandı." : "Calibration reset to defaults.", Toast.LENGTH_SHORT).show();
            host.back();
        });
        btns.addView(resetBtn);

        View sp2 = new View(ctx);
        btns.addView(sp2, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView closeBtn = Controls.button(ctx, tr ? "Kapat" : "Close", v -> host.back());
        btns.addView(closeBtn);

        root.addView(btns);
        root.post(sweepBtn::requestFocus);
        return root;
    }

    public static View buildAirPodsSettingsModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 26));
        root.setFocusable(true);

        TextView badge = UiKit.bold(ctx, 13, Color.parseColor("#A855F7"));
        badge.setText(tr ? "KULAKLIK & UZAMSAL SES ZENGİNLEŞTİRME" : "HEADPHONE & SPATIAL AUDIO ENHANCEMENT");
        root.addView(badge);

        TextView title = UiKit.bold(ctx, 26, UiKit.INK);
        title.setText(tr ? "AirPods & Kulaklık Ayarları" : "AirPods & Headphone Settings");
        title.setPadding(0, UiKit.px(6), 0, UiKit.px(14));
        root.addView(title);

        boolean connected = com.cinetv.launcher.device.AirPodsLink.isHeadphoneConnected(ctx);
        TextView status = UiKit.bold(ctx, 15, connected ? Color.parseColor("#10B981") : UiKit.INK_DIM);
        status.setText(connected ? (tr ? "✓ Kulaklık TV'ye Bağlı · Aktif Ses Çıkışı" : "✓ Headphones Connected · Active Audio Output")
                                 : (tr ? "Kulaklık Bağlı Değil (Kutuyu TV'nin yanında açarak bağlanabilirsiniz)" : "Headphones Disconnected"));
        status.setPadding(0, 0, 0, UiKit.px(16));
        root.addView(status);

        final LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        boolean autoConn = com.cinetv.launcher.service.Prefs.get(ctx).getBoolean(com.cinetv.launcher.service.Prefs.AIRPODS_AUTO_CONNECT, true);
        list.addView(Controls.toggleTile(ctx,
                tr ? "Kapak Açılınca Otomatik Bağlan" : "Auto-Connect on Lid Open",
                autoConn ? (tr ? "Açık · Kulaklık kutusu açıldığında ses doğrudan kulaklığa geçer" : "On · Audio shifts automatically")
                         : (tr ? "Kapalı · Yalnızca menüden bağlanır" : "Off"),
                autoConn, (active, sub, sw) -> {
                    com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean(com.cinetv.launcher.service.Prefs.AIRPODS_AUTO_CONNECT, active).apply();
                }));

        boolean curSpatial = com.cinetv.launcher.service.Prefs.get(ctx).getBoolean("airpods_spatial_binaural", true);
        list.addView(Controls.toggleTile(ctx,
                tr ? "Sinematik Uzamsal Ses (3D Binaural)" : "Cinematic Spatial Audio (3D Binaural)",
                curSpatial ? (tr ? "Açık · Çok kanallı 5.1/7.1 film sesleri kulaklıkta 3D sahnelenir" : "On · Multichannel 5.1/7.1 spatialized in 3D")
                           : (tr ? "Kapalı · Düz stereo çıkış" : "Off · Flat stereo"),
                curSpatial, (active, sub, sw) -> {
                    com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean("airpods_spatial_binaural", active).apply();
                    com.cinetv.launcher.device.AirPodsLink.applyHeadphoneSpatialAudio(ctx, active);
                    Toast.makeText(ctx, active ? (tr ? "Uzamsal Ses Açıldı" : "Spatial Audio Enabled")
                                               : (tr ? "Uzamsal Ses Kapatıldı" : "Spatial Audio Disabled"), Toast.LENGTH_SHORT).show();
                }));

        boolean curVocal = com.cinetv.launcher.service.Prefs.get(ctx).getBoolean("headphone_dialog_clarity", true);
        list.addView(Controls.toggleTile(ctx,
                tr ? "Diyalog Netleştirme & Vokal Güçlendirme" : "Dialog Clarity & Vocal Intelligibility",
                curVocal ? (tr ? "Açık · Fısıltılar ve film konuşmaları öne çıkarılır" : "On · Whispers and dialogue boosted")
                         : (tr ? "Kapalı" : "Off"),
                curVocal, (active, sub, sw) -> {
                    com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean("headphone_dialog_clarity", active).apply();
                    Toast.makeText(ctx, active ? (tr ? "Diyalog Netleştirme Açık" : "Dialog Clarity On")
                                               : (tr ? "Diyalog Netleştirme Kapalı" : "Dialog Clarity Off"), Toast.LENGTH_SHORT).show();
                }));

        root.addView(list);

        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, UiKit.px(16), 0, 0);

        TextView reconnectBtn = Controls.button(ctx, tr ? "Yeniden Bağlan" : "Reconnect", v -> {
            com.cinetv.launcher.device.AirPodsLink.clearDisconnectCooldown();
            com.cinetv.launcher.device.AirPodsLink.ensureStarted(ctx);
            Toast.makeText(ctx, tr ? "Kulaklık aranıyor ve bağlanılıyor..." : "Connecting headphones...", Toast.LENGTH_SHORT).show();
            host.back();
        });
        btns.addView(reconnectBtn);

        View sp = new View(ctx);
        btns.addView(sp, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView closeBtn = Controls.button(ctx, tr ? "Tamam" : "Done", v -> host.back());
        btns.addView(closeBtn);

        root.addView(btns);
        root.post(closeBtn::requestFocus);
        return root;
    }

    public static View buildCineSubTutorialModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 26));
        root.setFocusable(true);

        TextView badge = UiKit.bold(ctx, 13, Color.parseColor("#F59E0B"));
        badge.setText(tr ? "APPLE VISION UZAMSAL ALTYAZI SİSTEMİ" : "APPLE VISION SPATIAL SUBTITLE SYSTEM");
        root.addView(badge);

        TextView title = UiKit.bold(ctx, 26, UiKit.INK);
        title.setText(Brand.cinesubWordmark());
        title.setPadding(0, UiKit.px(6), 0, UiKit.px(14));
        root.addView(title);

        TextView desc = UiKit.label(ctx, 15.5f, UiKit.INK_DIM);
        desc.setLineSpacing(UiKit.pxf(5), 1.15f);
        desc.setMaxWidth(UiKit.px(760));
        desc.setText(tr
                ? "CineSUB, video oynatılırken sahnedeki kültürel ögeleri, gizli detayları, tarihi göndermeleri ve oyuncu bilgilerini Apple visionOS anlayışıyla tüm ekrana yayılan uzamsal bir katmanda inceler.\n\n"
                + "• Kumandanızın CineSub / Mavi / Kırmızı tuşuna veya mobil kumandadaki CineSUB butonuna basarak tetikleyebilirsiniz.\n"
                + "• Ekrandaki ögeye nişan alan optik tarama retikülü ve lazer kılavuz hattı ile doğrudan sahneyle etkileşime geçer.\n"
                + "• 26sp başlık ve 19sp gövde metni ile salondaki herkes tarafından rahatça okunur; 22 saniye boyunca ekranda kalır.\n"
                + "• Video sesini bastırmamak için sesli okuma (TTS) KULLANMAZ, tamamen sessiz bir kültürel keşif vizörüdür.\n"
                + "• İstediğiniz an [GERİ] veya [OK] tuşuna basarak anında kapatabilirsiniz."
                : "CineSUB is an Apple visionOS-inspired spatial cultural subtitle engine exploring scene details and cinematic lore.\n\n"
                + "• Trigger anytime using the CineSub/Color remote key or the CineSUB button on mobile remote.\n"
                + "• Connects to screen elements via optical reticles and laser filament lines.\n"
                + "• High-contrast 26sp/19sp typography, 22-second reading timer, and zero TTS to avoid drowning film audio.\n"
                + "• Dismiss instantly with [BACK] or [OK].");
        desc.setPadding(0, 0, 0, UiKit.px(20));
        root.addView(desc);

        LinearLayout btns = new LinearLayout(ctx);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        TextView testBtn = Controls.button(ctx, tr ? "CineSUB'ı Ekranda Dene" : "Test CineSUB", v -> {
            host.closeImmediate();
            com.cinetv.launcher.ui.CineSubOverlay.trigger(ctx);
        });
        btns.addView(testBtn);

        View sp = new View(ctx);
        btns.addView(sp, new LinearLayout.LayoutParams(UiKit.px(14), 1));

        TextView closeBtn = Controls.button(ctx, tr ? "Tamam" : "Done", v -> host.back());
        btns.addView(closeBtn);

        root.addView(btns);
        root.post(testBtn::requestFocus);
        return root;
    }

    public static View buildCineDuelHowToPlayModal(final Context ctx, final ModalHost host) {
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.px(36), UiKit.px(24), UiKit.px(36), UiKit.px(24));
        root.setBackground(Glass.panel(root, 26));
        root.setFocusable(true);

        TextView badge = UiKit.bold(ctx, 13, Color.parseColor("#EF4444"));
        badge.setText(tr ? "SİNEMA NİŞANCISI & GYRO ARENASI" : "CINEMA SHOOTER & GYRO ARENA");
        root.addView(badge);

        TextView title = UiKit.bold(ctx, 26, UiKit.INK);
        title.setText(Brand.cineduelWordmark());
        title.setPadding(0, UiKit.px(6), 0, UiKit.px(14));
        root.addView(title);

        TextView desc = UiKit.label(ctx, 15.5f, UiKit.INK_DIM);
        desc.setLineSpacing(UiKit.pxf(5), 1.15f);
        desc.setMaxWidth(UiKit.px(760));
        desc.setText(tr
                ? "SineDüello, telefonunuzu bir joystick gibi kullanarak TV ekranındaki hareketli sinema hedeflerini vurduğunuz yüksek rekabetli bir arena oyunudur!\n\n"
                + "• Ekrandaki replik, sahne, yönetmen, aktör ve trivia soruları 4 adet süzülen hedefle salona gelir.\n"
                + "• Telefonunuzun 3D jiroskop sensörüyle (veya TV kumandası D-pad ile) hedefi nişan alıp [ATEŞ] tuşuna basın!\n"
                + "• Gamze AI da kendi nişangâhıyla aynı anda yarışır; doğru hedefi ilk vuran +100 puan kazanır!\n"
                + "• Düellonun sonunda şampiyon olan oyuncunun seçtiği başyapıt TV'de otomatik olarak açılır!"
                : "CineDuel turns your phone into a gyro motion crosshair controller aiming at floating cinema trivia targets on the TV!");
        desc.setPadding(0, 0, 0, UiKit.px(20));
        root.addView(desc);

        TextView closeBtn = Controls.button(ctx, tr ? "Anladım, Oyuna Dön" : "Back to Game", v -> host.back());
        root.addView(closeBtn);
        root.post(closeBtn::requestFocus);
        return root;
    }
}

