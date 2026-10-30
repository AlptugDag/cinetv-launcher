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

    private Panels() {}

    // ---- Settings (Paginated: Temalar, Görsel Deneyim, Yapay Zeka & Ağ, Sistem & TV) ------------

    public static final int PAGE_THEMES = 0;
    public static final int PAGE_VISUAL = 1;
    public static final int PAGE_AI = 2;
    public static final int PAGE_SYSTEM = 3;

    public static void settings(final Context ctx, final ModalHost host, final Actions a) {
        host.push(() -> buildSettings(ctx, host, a, 0, null));
    }

    private static View buildSettings(final Context ctx, final ModalHost host, final Actions a) {
        return buildSettings(ctx, host, a, 0, null);
    }

    private static View buildSettings(final Context ctx, final ModalHost host, final Actions a, final String focusTag) {
        return buildSettings(ctx, host, a, 0, focusTag);
    }

    private static View buildSettings(final Context ctx, final ModalHost host, final Actions a, final int pageIndex, final String focusTag) {
        // One scrollable page (the old tab pages are now sections), sized from the real screen, not a fixed design box.
        final android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final int settingsW = Math.round(dm.widthPixels * 0.76f);
        final int settingsH = Math.round(dm.heightPixels * 0.80f);
        final boolean wide = settingsW >= UiKit.px(1100);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setLayoutParams(new LinearLayout.LayoutParams(settingsW, settingsH));

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(ctx);
        logo.setImageBitmap(Brand.mark(ctx));
        head.addView(logo, new LinearLayout.LayoutParams(UiKit.px(48), UiKit.px(48)));
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(R.string.settings_title);
        title.setPadding(UiKit.px(18), 0, UiKit.px(36), 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView navHint = UiKit.label(ctx, 16, UiKit.INK_DIM);
        navHint.setText("tr".equals(Locale.getDefault().getLanguage()) ? "▲ ▼ ◀ ▶ gezin · Geri: kapat" : "▲ ▼ ◀ ▶ navigate · Back: close");
        head.addView(navHint);
        root.addView(head);

        final String[] tabTitles = "tr".equals(Locale.getDefault().getLanguage())
                ? new String[]{"Temalar & Atmosfer", "Sinema Deneyimi", "Yapay Zeka & Ağ", "Sistem & TV"}
                : new String[]{"Themes & Atmosphere", "Cinema Experience", "AI & Network", "System & TV"};
        final int[] tabIcons = {
                R.drawable.ic_tab_palette,
                R.drawable.ic_tab_cinema,
                R.drawable.ic_tab_ai,
                R.drawable.ic_settings_gear
        };
        final LinearLayout tabRow = new LinearLayout(ctx);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.setClipChildren(false);
        tabRow.setClipToPadding(false);
        tabRow.setPadding(UiKit.px(48), UiKit.px(10), UiKit.px(48), UiKit.px(8));
        final View[] tabViews = new View[4];
        for (int t = 0; t < 4; t++) {
            final int thisTab = t;
            final boolean active = (t == pageIndex);

            final LinearLayout tabPill = new LinearLayout(ctx);
            tabPill.setOrientation(LinearLayout.HORIZONTAL);
            tabPill.setGravity(Gravity.CENTER_VERTICAL);
            tabPill.setFocusable(true);
            tabPill.setClickable(true);
            tabPill.setPadding(UiKit.px(18), UiKit.px(9), UiKit.px(22), UiKit.px(9));

            final ImageView tabIcon = new ImageView(ctx);
            android.graphics.drawable.Drawable icD = ctx.getDrawable(tabIcons[t]).mutate();
            icD.setTint(active ? Color.WHITE : UiKit.INK_DIM);
            tabIcon.setImageDrawable(icD);
            LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(UiKit.px(20), UiKit.px(20));
            icLp.setMargins(0, 0, UiKit.px(10), 0);
            tabPill.addView(tabIcon, icLp);

            final TextView tabText = UiKit.bold(ctx, 15, active ? Color.WHITE : UiKit.INK_DIM);
            tabText.setText(tabTitles[t]);
            tabPill.addView(tabText);

            final android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
            pillBg.setCornerRadius(UiKit.pxf(16));
            pillBg.setColor(active ? Color.parseColor("#38BDF8") : Color.argb(32, 255, 255, 255));
            tabPill.setBackground(pillBg);

            tabPill.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    pillBg.setColor(Color.WHITE);
                    tabText.setTextColor(Color.BLACK);
                    icD.setTint(Color.BLACK);
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                } else {
                    pillBg.setColor(active ? Color.parseColor("#38BDF8") : Color.argb(32, 255, 255, 255));
                    tabText.setTextColor(active ? Color.WHITE : UiKit.INK_DIM);
                    icD.setTint(active ? Color.WHITE : UiKit.INK_DIM);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
            });
            tabPill.setOnClickListener(v -> {
                if (thisTab != pageIndex) {
                    host.replace(() -> buildSettings(ctx, host, a, thisTab, "tab_" + thisTab));
                }
            });
            tabPill.setTag("tab_" + t);
            tabViews[t] = tabPill;
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 0, UiKit.px(12), 0);
            tabRow.addView(tabPill, tlp);
        }
        for (int t = 0; t < 4; t++) {
            final int currT = t;
            tabViews[t].setOnKeyListener((v, keyCode, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && currT > 0) {
                    host.replace(() -> buildSettings(ctx, host, a, currT - 1, "tab_" + (currT - 1)));
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && currT < 3) {
                    host.replace(() -> buildSettings(ctx, host, a, currT + 1, "tab_" + (currT + 1)));
                    return true;
                }
                return false;
            });
        }
        root.addView(tabRow);

        final LinearLayout cols = new LinearLayout(ctx);
        cols.setOrientation(LinearLayout.VERTICAL);
        cols.setClipChildren(false);
        cols.setClipToPadding(false);
        cols.setPadding(UiKit.px(10), UiKit.px(10), UiKit.px(10), UiKit.px(10));

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(cols, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout frame = new FrameLayout(ctx);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        slp.setMargins(UiKit.px(52), UiKit.px(24), UiKit.px(52), UiKit.px(24));
        frame.addView(scroll, slp);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        flp.topMargin = UiKit.px(4);
        root.addView(frame, flp);

        final int colWidth = 0; // sections lay their tiles out in a full-width, self-filling grid

        if (pageIndex == PAGE_THEMES) {
            // Page 1: Themes & Atmosphere
            LinearLayout c1 = column(ctx, cols, R.string.col_themes, colWidth);
            ThemeManager.Theme current = ThemeManager.current(ctx);
            for (int k = 0; k < ThemeManager.THEMES.length; k++) {
                final ThemeManager.Theme t = ThemeManager.THEMES[k];
                final String themeTag = "theme_" + t.id;
                LinearLayout tile = Controls.tile(ctx, themeName(ctx, t), null, t.id.equals(current.id), v -> {
                    a.applyTheme(t.id);
                    host.replace(() -> buildSettings(ctx, host, a, PAGE_THEMES, themeTag));
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
                            host.replace(() -> buildSettings(ctx, host, a, PAGE_THEMES, "theme_" + ThemeManager.current(ctx).id));
                        }));
            }

            LinearLayout c2 = column(ctx, cols, "tr".equals(Locale.getDefault().getLanguage()) ? "Atmosfer & Dinlenme" : "Atmosphere & Rest", colWidth);
            c2.addView(Controls.tile(ctx, ctx.getString(R.string.night_title), nightState(ctx), com.cinetv.launcher.device.NightCare.sleepMinutesLeft(ctx) > 0,
                    v -> host.push(() -> buildNight(ctx, host, a))));
            c2.addView(Controls.tile(ctx, ctx.getString(R.string.tour_title), ctx.getString(R.string.tour_hint), false, v -> a.startTour()));
            c2.addView(Controls.tile(ctx,
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Alptuğ Dağ · Yaratıcı & İletişim" : "Alptuğ Dağ · Creator & Contact",
                    "tr".equals(Locale.getDefault().getLanguage()) ? "@alptugdag · Instagram, Kısa Film 'Sessiz' ve Portfolyo" : "@alptugdag · Instagram, Short Film 'Sessiz' & Portfolio",
                    false, v -> host.push(() -> buildAbout(ctx, host, a))));

        } else if (pageIndex == PAGE_VISUAL) {
            // Page 2: Visual Experience & 3D
            LinearLayout c1 = column(ctx, cols, R.string.col_visual, colWidth);
            boolean spatial = Prefs.spatial3d(ctx);
            LinearLayout spatialTile = Controls.tile(ctx, ctx.getString(R.string.spatial_hud), ctx.getString(spatial ? R.string.state_on_spatial : R.string.state_off), spatial, v -> {
                boolean next = !Prefs.spatial3d(ctx);
                Prefs.setFlag(ctx, Prefs.SPATIAL_3D, next);
                a.setSpatial(next);
                host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_spatial"));
            });
            spatialTile.setTag("opt_spatial");
            c1.addView(spatialTile);

            boolean bevel = Prefs.glassBevel(ctx);
            LinearLayout bevelTile = Controls.tile(ctx, ctx.getString(R.string.glass_bevel), ctx.getString(bevel ? R.string.state_on : R.string.state_off), bevel, v -> {
                boolean next = !Prefs.glassBevel(ctx);
                Prefs.setFlag(ctx, Prefs.GLASS_BEVEL, next);
                GlassBevelView.setAberrationEnabled(next);
                host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_bevel"));
            });
            bevelTile.setTag("opt_bevel");
            c1.addView(bevelTile);

            boolean cases = Prefs.dvdCases(ctx);
            LinearLayout casesTile = Controls.tile(ctx, ctx.getString(R.string.dvd_cases), ctx.getString(cases ? R.string.dvd_cases_on : R.string.dvd_cases_off), cases, v -> {
                boolean next = !Prefs.dvdCases(ctx);
                Prefs.setFlag(ctx, Prefs.DVD_CASES, next);
                a.setDvdCases(next);
                host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_cases"));
            });
            casesTile.setTag("opt_cases");
            c1.addView(casesTile);

            boolean ambilight = com.cinetv.launcher.device.AmbilightSync.isEnabled(ctx);
            LinearLayout ambilightTile = Controls.tile(ctx,
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Ambilight & Arka Işık Senkronu" : "Ambilight & Backlight Sync",
                    ambilight ? ("tr".equals(Locale.getDefault().getLanguage()) ? "Ortam ışıkları vitrin renkleriyle senkronize" : "Ambient lights synced with poster colors")
                              : ("tr".equals(Locale.getDefault().getLanguage()) ? "Kapalı" : "Off"),
                    ambilight, v -> {
                        boolean next = !com.cinetv.launcher.device.AmbilightSync.isEnabled(ctx);
                        com.cinetv.launcher.device.AmbilightSync.setEnabled(ctx, next);
                        host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_ambilight"));
                    });
            ambilightTile.setTag("opt_ambilight");
            c1.addView(ambilightTile);

            boolean trailers = Prefs.trailerMoments(ctx);
            LinearLayout trailersTile = Controls.tile(ctx, ctx.getString(R.string.trailer_moments), ctx.getString(trailers ? R.string.trailer_moments_on : R.string.state_off), trailers, v -> {
                boolean next = !Prefs.trailerMoments(ctx);
                Prefs.setFlag(ctx, Prefs.TRAILER_MOMENTS, next);
                a.setTrailerMoments(next);
                host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_trailers"));
            });
            trailersTile.setTag("opt_trailers");
            c1.addView(trailersTile);

            LinearLayout c2 = column(ctx, cols, "Animasyon & Performans", colWidth);
            boolean dvd = Prefs.dvdAnimation(ctx);
            LinearLayout dvdTile = Controls.tile(ctx, ctx.getString(R.string.dvd_animation), ctx.getString(dvd ? R.string.state_on : R.string.state_off), dvd, v -> {
                Prefs.setFlag(ctx, Prefs.DVD_ANIMATION, !Prefs.dvdAnimation(ctx));
                host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_dvd"));
            });
            dvdTile.setTag("opt_dvd");
            c2.addView(dvdTile);

            String perfMode = PerformanceProfile.mode(ctx);
            String tierName = ctx.getString(tierRes(PerformanceProfile.tier(ctx)));
            c2.addView(Controls.tile(ctx, ctx.getString(R.string.performance),
                    ctx.getString(perfModeRes(perfMode)) + " · " + tierName, false, v -> host.push(() -> buildPerformancePicker(ctx, host, a))));
            c2.addView(Controls.tile(ctx, ctx.getString(R.string.screensaver), ctx.getString(R.string.screensaver_preview), false, v -> {
                host.close();
                a.previewScreensaver();
            }));

            c2.addView(Controls.tile(ctx,
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Arayüz Ölçeği" : "UI Scale",
                    Prefs.uiScaleLabel(ctx), false, v -> {
                        Prefs.setUiScaleLevel(ctx, Prefs.uiScaleLevel(ctx) + 1);
                        UiKit.init(ctx);
                        a.applyAppearance();
                        host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_ui_scale"));
                    }));
            c2.addView(Controls.tile(ctx,
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Yazı Boyutu" : "Font Size",
                    Prefs.fontScaleLabel(ctx), false, v -> {
                        Prefs.setFontScaleLevel(ctx, Prefs.fontScaleLevel(ctx) + 1);
                        UiKit.init(ctx);
                        a.applyAppearance();
                        host.replace(() -> buildSettings(ctx, host, a, PAGE_VISUAL, "opt_font_scale"));
                    }));

        } else if (pageIndex == PAGE_AI) {
            // Page 3: AI & Media / Network
            LinearLayout c1 = column(ctx, cols, R.string.col_ai, colWidth);
            String provider = LlmClient.providerName(ctx);
            c1.addView(Controls.tile(ctx, ctx.getString(R.string.ai_key), provider != null ? provider : ctx.getString(R.string.not_set),
                    provider != null, v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.AI_KEY, R.string.ai_key, R.string.ai_key_hint))));
            boolean tmdb = MovieCatalog.hasTmdb(ctx);
            c1.addView(Controls.tile(ctx, ctx.getString(R.string.tmdb_key), ctx.getString(tmdb ? R.string.state_on : R.string.not_set), tmdb,
                    v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.TMDB_KEY, R.string.tmdb_key, R.string.tmdb_key_hint))));
            boolean mdb = SecretStore.has(ctx, SecretStore.MDBLIST_KEY);
            c1.addView(Controls.tile(ctx, ctx.getString(R.string.mdblist_key), ctx.getString(mdb ? R.string.state_on : R.string.not_set), mdb,
                    v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.MDBLIST_KEY, R.string.mdblist_key, R.string.mdblist_key_hint))));
            boolean emaOn = Prefs.emaTts(ctx);
            boolean emaSupported = com.cinetv.launcher.ai.EmaLightningTts.isSupportedHardware(ctx);
            int level = Prefs.ttsLevel(ctx);
            String levelLabel = level == Prefs.TTS_LEVEL_SEARCH_ONLY
                    ? ("tr".equals(Locale.getDefault().getLanguage()) ? "Sadece Arama" : "Search Only")
                    : level == Prefs.TTS_LEVEL_PROACTIVE
                    ? ("tr".equals(Locale.getDefault().getLanguage()) ? "Tam Proaktif" : "Full Proactive")
                    : ("tr".equals(Locale.getDefault().getLanguage()) ? "Dengeli" : "Balanced");
            String emaTitle = "tr".equals(Locale.getDefault().getLanguage())
                    ? "Doğal Seslendirme & Asistan (EMA Lightning)"
                    : "Natural Voice & Assistant (EMA Lightning)";
            String emaSub = emaOn
                    ? ("tr".equals(Locale.getDefault().getLanguage())
                            ? "Açık · " + levelLabel + " · Aktiflik düzeyini ayarlamak için dokunun"
                            : "On · " + levelLabel + " · Tap to adjust activity level")
                    : ("tr".equals(Locale.getDefault().getLanguage())
                            ? (emaSupported ? "Kapalı · Sesli yanıtları ve asistanı etkinleştirmek için dokunun" : "Kapalı · Yalnızca orta ve üst performans profilinde önerilir")
                            : "Off · Recommended for Mid and High performance profiles");
            LinearLayout emaTile = Controls.tile(ctx, emaTitle, emaSub, emaOn, v -> {
                host.push(() -> buildTtsSettings(ctx, host, a));
            });
            emaTile.setTag("opt_ema_tts");
            c1.addView(emaTile);

            LinearLayout c2 = column(ctx, cols, "Ağ & Ortam", colWidth);

            String city = Prefs.city(ctx);
            c2.addView(Controls.tile(ctx, ctx.getString(R.string.weather_city), city != null ? city : ctx.getString(R.string.weather_auto), city != null,
                    v -> host.push(() -> buildCityInput(ctx, host, a))));

            LinearLayout c3 = column(ctx, cols, "tr".equals(Locale.getDefault().getLanguage()) ? "Optimizasyon" : "Optimization", colWidth);
            c3.addView(Controls.tile(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "Görüntü, Ses, Ağ & Optimizasyon" : "Picture, Sound, Network & Optimization",
                    "tr".equals(Locale.getDefault().getLanguage()) ? "TV Denetçisi verilerine göre optimize et, DNS seç ve test et" : "Optimize from TV Inspector data, pick DNS, test",
                    false, v -> setup(ctx, host, true)));

        } else {
            // Page 4: System & TV
            LinearLayout c1 = column(ctx, cols, R.string.col_tv, colWidth);
            c1.addView(Controls.tile(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "Kurulum Sihirbazı & Bileşen Kontrolü" : "Setup Wizard & Component Health",
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Eksik bileşenleri kontrol et ve kur" : "Check and setup components", false,
                    v -> wizard(ctx, host)));
            boolean isHome = com.cinetv.launcher.HomeGuard.isDefault(ctx);
            c1.addView(Controls.tile(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "Ana Başlatıcı Yap" : "Set as Default Home",
                    isHome ? ("tr".equals(Locale.getDefault().getLanguage()) ? "CineTV varsayılan ana ekran" : "CineTV is default home")
                           : ("tr".equals(Locale.getDefault().getLanguage()) ? "Kumanda ana tuşu için dokunun" : "Tap to set as default launcher"),
                    isHome, v -> com.cinetv.launcher.HomeGuard.promptHomeChooser(ctx)));
            boolean a11y = com.cinetv.launcher.TvKeyInterceptorService.getInstance() != null;
            c1.addView(Controls.tile(ctx,
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Kumanda Tuş Entegrasyonu" : "Remote Key Integration",
                    a11y ? ("tr".equals(Locale.getDefault().getLanguage()) ? "Yerel tuşlar & global servis devrede" : "Native keys & global service active")
                         : ("tr".equals(Locale.getDefault().getLanguage()) ? "Yerel kumanda tuşları aktif · Erişilebilirlik zorunlu değil" : "Native keys active · Accessibility optional"),
                    true, v -> {
                        com.cinetv.launcher.HomeGuard.ensureDefault(ctx);
                        com.cinetv.launcher.HomeGuard.openAccessibilitySettings(ctx);
                    }));
            c1.addView(Controls.tile(ctx, ctx.getString(R.string.tv_settings), ctx.getString(R.string.tv_settings_hint), false,
                    v -> AppLauncher.openSystemSettings(ctx)));
            if (AppLauncher.liveTvPackage(ctx.getPackageManager()) != null || AppLauncher.hasHdmiSource(ctx)) {
                boolean liveTv = AppCatalog.liveTvShown(ctx);
                LinearLayout liveTvTile = Controls.tile(ctx, ctx.getString(R.string.live_tv_tile), ctx.getString(liveTv ? R.string.live_tv_tile_on : R.string.state_off), liveTv, v -> {
                    if (AppCatalog.liveTvShown(ctx)) AppCatalog.hide(ctx, AppLauncher.LIVE_TV);
                    else AppCatalog.addToHome(ctx, AppLauncher.LIVE_TV);
                    a.onAppsChanged();
                    host.replace(() -> buildSettings(ctx, host, a, PAGE_SYSTEM, "opt_livetv"));
                });
                liveTvTile.setTag("opt_livetv");
                c1.addView(liveTvTile);
            }

            LinearLayout c2 = column(ctx, cols, "Bakım & Donanım", colWidth);
            c2.addView(Controls.tile(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "TV Denetçisi (Donanım & Panel Analizi)" : "TV Inspector (Hardware & Panel Diagnostics)",
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Ekran, HDR, ses çözücüleri, bellek ve canlı tuşlar" : "Display, HDR, audio codecs, memory & live keys", false,
                    v -> host.push(() -> buildTvInspector(ctx, host))));
            c2.addView(Controls.tile(ctx,
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Ekran Yönünü Sıfırla (0° Yatay / Landscape)" : "Reset Screen Orientation (Landscape)",
                    "tr".equals(Locale.getDefault().getLanguage()) ? "TV kutusu yan dönmüşse ekranı standart yatay konuma kilitler" : "Restores normal landscape orientation if TV box is sideways",
                    false, v -> {
                        try {
                            android.provider.Settings.System.putInt(ctx.getContentResolver(), android.provider.Settings.System.USER_ROTATION, android.view.Surface.ROTATION_0);
                            android.provider.Settings.System.putInt(ctx.getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
                        } catch (Throwable ignored) {}
                        com.cinetv.launcher.remote.LocalAdb.shell(ctx, "settings put system user_rotation 0; settings put system accelerometer_rotation 0; wm set-fix-to-user-rotation disabled", 2000);
                        if (ctx instanceof android.app.Activity) {
                            ((android.app.Activity) ctx).setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        }
                        Toast.makeText(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "Ekran yönü standart yatay olarak sıfırlandı" : "Screen orientation reset to landscape", Toast.LENGTH_SHORT).show();
                    }));
            c2.addView(Controls.tile(ctx, ctx.getString(R.string.remote_keys), ctx.getString(R.string.remote_keys_hint), false,
                    v -> host.push(() -> buildKeyCapture(ctx, host, a))));
            String curVer = AppUpdater.currentVersion(ctx);
            c2.addView(Controls.tile(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "Sürüm & Güncelleme" : "Version & Updates",
                    "v" + curVer + ("tr".equals(Locale.getDefault().getLanguage()) ? " · Güncellemeleri Denetle" : " · Check for Updates"), false, v -> {
                Toast.makeText(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "GitHub güncellemeleri denetleniyor..." : "Checking GitHub updates...", Toast.LENGTH_SHORT).show();
                AppUpdater.checkForUpdates(ctx, new AppUpdater.UpdateCheckCallback() {
                    @Override
                    public void onUpdateAvailable(AppUpdater.ReleaseInfo info) {
                        if (ctx instanceof android.app.Activity) {
                            AppUpdater.showUpdateDialog((android.app.Activity) ctx, info);
                        }
                    }

                    @Override
                    public void onUpToDate(String currentVersion) {
                        Toast.makeText(ctx, "tr".equals(Locale.getDefault().getLanguage())
                                ? "CineTV en güncel sürümde (v" + currentVersion + ")"
                                : "CineTV is up to date (v" + currentVersion + ")", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ctx, "tr".equals(Locale.getDefault().getLanguage())
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
            LinearLayout adbTile = Controls.tile(ctx,
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Geliştirici & Hata Ayıklama Güvenliği" : "Developer & Debugging Security",
                    finalAdb ? ("tr".equals(Locale.getDefault().getLanguage()) ? "Açık (Ağda aktif) · Kapatıp TV'yi izole etmek için dokunun" : "Active (Enabled on network) · Tap to disable & isolate TV")
                             : ("tr".equals(Locale.getDefault().getLanguage()) ? "Kapalı (Güvenli) · Mobil kumanda güvenle çalışır" : "Disabled (Secure) · Mobile remote works securely"),
                    finalAdb, v -> {
                        if (finalAdb) {
                            try {
                                android.provider.Settings.Global.putInt(ctx.getContentResolver(), android.provider.Settings.Global.ADB_ENABLED, 0);
                            } catch (Throwable ignored) {}
                            com.cinetv.launcher.remote.LocalAdb.shell(ctx, "settings put global adb_enabled 0", 1500);
                            Toast.makeText(ctx, "tr".equals(Locale.getDefault().getLanguage())
                                    ? "USB Hata ayıklama kapatıldı. TV'niz güvenli modda."
                                    : "USB debugging disabled. TV is secured.", Toast.LENGTH_SHORT).show();
                            host.replace(() -> buildSettings(ctx, host, a, PAGE_SYSTEM, "opt_adb_sec"));
                        } else {
                            try {
                                ctx.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                            } catch (Throwable t) {
                                AppLauncher.openSystemSettings(ctx);
                            }
                        }
                    });
            adbTile.setTag("opt_adb_sec");
            c2.addView(adbTile);
            c2.addView(Controls.tile(ctx, "tr".equals(Locale.getDefault().getLanguage()) ? "Nuvio Eklentilerini Yönet & Kur" : "Manage & Install Nuvio Addons",
                    "tr".equals(Locale.getDefault().getLanguage()) ? "Eklenti ve profil seçim penceresini aç" : "Select and install curated addons", false,
                    v -> host.push(() -> buildNuvioUpdateAddons(ctx, host))));
            c2.addView(Controls.tile(ctx, ctx.getString(R.string.reboot), null, false, v -> a.reboot()));
            c2.addView(Controls.alarm(ctx, ctx.getString(R.string.destroy), v -> host.push(() -> buildDestroy(ctx, host))));
        }

        final int gridCols = wide ? 3 : 2;
        for (int i = 0; i < cols.getChildCount(); i++) {
            if (cols.getChildAt(i) instanceof LinearLayout) gridify(ctx, (LinearLayout) cols.getChildAt(i), gridCols, i);
        }
        attachScrollArrows(ctx, frame, scroll, cols);

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
            rlp.bottomMargin = gap;
            section.addView(row, rlp);
        }
    }

    /** Arrows around the page: up/down while it scrolls, left/right where focus can still move sideways. */
    private static void attachScrollArrows(final Context ctx, final FrameLayout frame, final ScrollView scroll, final LinearLayout content) {
        final TextView up = arrow(ctx, "▲", Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        final TextView down = arrow(ctx, "▼", Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        final TextView left = arrow(ctx, "◀", Gravity.START | Gravity.CENTER_VERTICAL);
        final TextView right = arrow(ctx, "▶", Gravity.END | Gravity.CENTER_VERTICAL);
        frame.addView(up);
        frame.addView(down);
        frame.addView(left);
        frame.addView(right);
        final Runnable update = () -> {
            up.setAlpha(scroll.canScrollVertically(-1) ? 1f : 0.18f);
            down.setAlpha(scroll.canScrollVertically(1) ? 1f : 0.18f);
            View f = frame.findFocus();
            boolean l = false, r = false;
            if (f != null) {
                View ln = f.focusSearch(View.FOCUS_LEFT), rn = f.focusSearch(View.FOCUS_RIGHT);
                l = ln != null && ln != f && isDescendant(content, ln);
                r = rn != null && rn != f && isDescendant(content, rn);
            }
            left.setAlpha(l ? 1f : 0.18f);
            right.setAlpha(r ? 1f : 0.18f);
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
                root.addView(Controls.tile(ctx, s.name, state, s.connected, v -> {
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
        title.setText(tr ? "Gamze Sesli Asistan (Gemini AI & EMA TTS)" : "Gamze Voice Assistant (Gemini AI & EMA TTS)");
        root.addView(title);

        TextView sub = UiKit.label(ctx, 14, UiKit.INK_DIM);
        sub.setText(tr
                ? "AI anahtarı (Gemini) üzerinden çalışan ölçülü, ağırbaşlı ve zeki sinematik asistanınız Gamze. Yalnızca ihtiyaç anında konuşur, asla bunaltıcı olmaz."
                : "A laconic, dignified cinematic assistant Gamze powered by your Gemini AI key. Speaks only when asked, never overwhelming.");
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
        String l1Title = (isL1 ? "✓ " : "") + (tr ? "Sadece Sesli Arama (Minimal)" : "Voice Search Only (Minimal)");
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
        String l2Title = (isL2 ? "✓ " : "") + (tr ? "Gamze Modu: Akıllı Arama & AI (Önerilen)" : "Gamze Mode: Smart Search & AI (Recommended)");
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
        String l3Title = (isL3 ? "✓ " : "") + (tr ? "Gelişmiş Asistan (Proaktif)" : "Advanced Assistant (Proactive)");
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

    // ---- Remote keys --------------------------------------------------------------------------------

    private static View buildKeyCapture(final Context ctx, final ModalHost host, final Actions a) {
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusable(true);
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(R.string.remote_keys);
        root.addView(title);
        final TextView body = UiKit.label(ctx, 22, UiKit.INK_DIM);
        body.setText(R.string.remote_keys_press);
        body.setPadding(0, UiKit.px(16), 0, UiKit.px(16));
        body.setMaxWidth(UiKit.px(700));
        root.addView(body);
        final LinearLayout choices = new LinearLayout(ctx);
        choices.setOrientation(LinearLayout.VERTICAL);
        root.addView(choices);
        root.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN || HardwareKeys.isNavigation(keyCode) || choices.getChildCount() > 0) return false;
            final int kc = keyCode, sc = e.getScanCode();
            body.setText(ctx.getString(R.string.remote_keys_captured, kc, sc));
            String[][] actions = {
                    {HardwareKeys.NUVIO, "Nuvio"}, {HardwareKeys.YOUTUBE, "YouTube"}, {HardwareKeys.WEB_VIDEO, ctx.getString(R.string.cast_from_phone)},
                    {HardwareKeys.TV_PLUS, "TV+"}, {HardwareKeys.CLEAN_RAM, ctx.getString(R.string.optimize)},
                    {HardwareKeys.ALL_APPS, ctx.getString(R.string.all_apps)}};
            for (final String[] act : actions) {
                choices.addView(Controls.tile(ctx, act[1], null, false, c -> {
                    HardwareKeys.bind(ctx, kc, sc, act[0]);
                    Toast.makeText(ctx, ctx.getString(R.string.remote_keys_bound, act[1]), Toast.LENGTH_SHORT).show();
                    a.onKeysChanged();
                    host.back();
                }), new LinearLayout.LayoutParams(UiKit.px(560), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            choices.getChildAt(0).requestFocus();
            return true;
        });
        root.post(root::requestFocus);
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
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(UiKit.px(740), ViewGroup.LayoutParams.WRAP_CONTENT);
        twoCols.addView(list, leftLp);

        final LinearLayout rightCol = new LinearLayout(ctx);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setClipChildren(false);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(UiKit.px(740), ViewGroup.LayoutParams.WRAP_CONTENT);
        rightLp.leftMargin = UiKit.px(28);
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
                ? "★ ÜCRETSİZ YAPAY ZEKA & SİNEMA ARŞİVİ ANAHTARLARI\n"
                + "Bu iki kod tamamen ücretsizdir (kredi kartı gerekmez) ve CineTV'nin tüm akıllı sinema gücünü açar:\n\n"
                + "• Gemini AI (aistudio.google.com/apikey): Sesli/yazılı doğal dilde film araması (\"Nolan'ın uzay filmini aç\"), saate ve hava durumuna göre akıllı DVD vitrin küratörlüğü ve Türkçe sinema asistanı sağlar.\n"
                + "• TMDB (themoviedb.org/settings/api): 3B DVD kutularının yüksek çözünürlüklü afişlerini, açılan DVD'nin içindeki Türkçe film özetini, oyuncu kadrosunu ve fragman videolarını getirir."
                : "★ 100% FREE AI & CINEMA ARCHIVE KEYS\n"
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
                hasAi ? (turkish ? "✓ Aktif (" + provider + ") · Değiştirmek için dokunun" : "✓ Active (" + provider + ")")
                      : (turkish ? "aistudio.google.com/apikey · QR kodlu rehber ve anahtar girişi için dokunun" : "Tap for QR guide & key input (aistudio.google.com)"),
                hasAi, v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.AI_KEY, R.string.ai_key, R.string.ai_key_hint,
                        () -> host.replace(() -> buildWizard(ctx, host))))));

        // 7. TMDB Key
        boolean hasTmdb = MovieCatalog.hasTmdb(ctx);
        rightCol.addView(Controls.tile(ctx,
                turkish ? "7. TMDB Film, Afiş & Fragman Anahtarı (%100 Ücretsiz)" : "7. TMDB Poster & Trailer Key (100% Free)",
                hasTmdb ? (turkish ? "✓ Aktif (Yüksek çözünürlüklü afiş, özet ve fragmanlar devrede)" : "✓ Active (Posters, synopses & trailers enabled)")
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
        scroll.addView(root, new ViewGroup.LayoutParams(UiKit.px(1600), ViewGroup.LayoutParams.WRAP_CONTENT));

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

        LinearLayout.LayoutParams c1lp = new LinearLayout.LayoutParams(UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT);
        twoCols.addView(col1, c1lp);
        LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT);
        c2lp.leftMargin = UiKit.px(32);
        twoCols.addView(col2, c2lp);

        root.addView(twoCols);

        // Verification & Community Tested Device Feedback
        col2.addView(inspectorHeader(ctx, tr ? "Cihaz Uyumluluğu & Onay" : "Device Compatibility & Verification"));
        final TextView feedbackBtn = Controls.button(ctx, tr ? "✓ Uyumluluk Raporu Gönder (Topluluk Listesine Ekle)" : "✓ Submit Device Compatibility Report", v -> {
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
}
