package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cinetv.launcher.R;
import com.cinetv.launcher.ai.LlmClient;
import com.cinetv.launcher.device.HardwareKeys;
import com.cinetv.launcher.device.MemoryOptimizer;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.device.PictureCalibration;
import com.cinetv.launcher.remote.RemotePairing;
import com.cinetv.launcher.service.AppLauncher;
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
        void onPerformanceChanged();
        void previewScreensaver();
        void curate(String mood);
        void refreshWeather();
        void onKeysChanged();
        void reboot();
        void search(String query);
    }

    private Panels() {}

    // ---- Settings ---------------------------------------------------------------------------------

    public static void settings(final Context ctx, final ModalHost host, final Actions a) {
        host.push(() -> buildSettings(ctx, host, a));
    }

    private static View buildSettings(final Context ctx, final ModalHost host, final Actions a) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(ctx);
        logo.setImageBitmap(Brand.mark(ctx));
        head.addView(logo, new LinearLayout.LayoutParams(UiKit.px(48), UiKit.px(48)));
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(R.string.settings_title);
        title.setPadding(UiKit.px(18), 0, 0, 0);
        head.addView(title);
        root.addView(head);
        LinearLayout cols = new LinearLayout(ctx);
        cols.setClipChildren(false);
        LinearLayout.LayoutParams colsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colsLp.topMargin = UiKit.px(28);
        root.addView(cols, colsLp);

        // 1. Themes
        LinearLayout c1 = column(ctx, cols, R.string.col_themes, 470);
        ThemeManager.Theme current = ThemeManager.current(ctx);
        for (int i = 0; i < ThemeManager.THEMES.length; i += 2) {
            LinearLayout pair = new LinearLayout(ctx);
            for (int k = i; k < Math.min(i + 2, ThemeManager.THEMES.length); k++) {
                final ThemeManager.Theme t = ThemeManager.THEMES[k];
                LinearLayout tile = Controls.tile(ctx, themeName(ctx, t), null, t.id.equals(current.id), v -> {
                    a.applyTheme(t.id);
                    host.replace(() -> buildSettings(ctx, host, a));
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(k == i ? 0 : UiKit.px(6), 0, k == i ? UiKit.px(6) : 0, UiKit.px(12));
                pair.addView(tile, lp);
            }
            c1.addView(pair);
        }
        String mode = ThemeManager.appearance(ctx);
        c1.addView(Controls.tile(ctx, ctx.getString(R.string.appearance), appearanceName(ctx, mode), false, v -> {
            ThemeManager.setAppearance(ctx, ThemeManager.nextAppearance(ThemeManager.appearance(ctx)));
            a.applyAppearance();
        }));
        boolean customBg = ThemeManager.usesCustomWallpaper(ctx);
        c1.addView(Controls.tile(ctx, ctx.getString(R.string.background), ctx.getString(customBg ? R.string.background_custom : R.string.background_hint), customBg,
                v -> host.push(() -> buildPairing(ctx, host, "background"))));

        // 2. Look & feel
        LinearLayout c2 = column(ctx, cols, R.string.col_visual, 390);
        boolean spatial = Prefs.spatial3d(ctx);
        c2.addView(Controls.tile(ctx, ctx.getString(R.string.spatial_hud), ctx.getString(spatial ? R.string.state_on_spatial : R.string.state_off), spatial, v -> {
            boolean next = !Prefs.spatial3d(ctx);
            Prefs.setFlag(ctx, Prefs.SPATIAL_3D, next);
            a.setSpatial(next);
            host.replace(() -> buildSettings(ctx, host, a));
        }));
        boolean bevel = Prefs.glassBevel(ctx);
        c2.addView(Controls.tile(ctx, ctx.getString(R.string.glass_bevel), ctx.getString(bevel ? R.string.state_on : R.string.state_off), bevel, v -> {
            boolean next = !Prefs.glassBevel(ctx);
            Prefs.setFlag(ctx, Prefs.GLASS_BEVEL, next);
            GlassBevelView.setAberrationEnabled(next);
            host.replace(() -> buildSettings(ctx, host, a));
        }));
        boolean cases = Prefs.dvdCases(ctx);
        c2.addView(Controls.tile(ctx, ctx.getString(R.string.dvd_cases), ctx.getString(cases ? R.string.dvd_cases_on : R.string.dvd_cases_off), cases, v -> {
            boolean next = !Prefs.dvdCases(ctx);
            Prefs.setFlag(ctx, Prefs.DVD_CASES, next);
            a.setDvdCases(next);
            host.replace(() -> buildSettings(ctx, host, a));
        }));
        boolean dvd = Prefs.dvdAnimation(ctx);
        c2.addView(Controls.tile(ctx, ctx.getString(R.string.dvd_animation), ctx.getString(dvd ? R.string.state_on : R.string.state_off), dvd, v -> {
            Prefs.setFlag(ctx, Prefs.DVD_ANIMATION, !Prefs.dvdAnimation(ctx));
            host.replace(() -> buildSettings(ctx, host, a));
        }));
        String perfMode = PerformanceProfile.mode(ctx);
        String tierName = ctx.getString(tierRes(PerformanceProfile.tier(ctx)));
        c2.addView(Controls.tile(ctx, ctx.getString(R.string.performance),
                ctx.getString(perfModeRes(perfMode)) + " · " + tierName, false, v -> host.push(() -> buildPerformancePicker(ctx, host, a))));
        c2.addView(Controls.tile(ctx, ctx.getString(R.string.screensaver), ctx.getString(R.string.screensaver_preview), false, v -> {
            host.close();
            a.previewScreensaver();
        }));

        // 3. AI & media
        LinearLayout c3 = column(ctx, cols, R.string.col_ai, 390);
        String provider = LlmClient.providerName(ctx);
        c3.addView(Controls.tile(ctx, ctx.getString(R.string.ai_key), provider != null ? provider : ctx.getString(R.string.not_set),
                provider != null, v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.AI_KEY, R.string.ai_key, R.string.ai_key_hint))));
        boolean tmdb = MovieCatalog.hasTmdb(ctx);
        c3.addView(Controls.tile(ctx, ctx.getString(R.string.tmdb_key), ctx.getString(tmdb ? R.string.state_on : R.string.not_set), tmdb,
                v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.TMDB_KEY, R.string.tmdb_key, R.string.tmdb_key_hint))));
        boolean mdb = SecretStore.has(ctx, SecretStore.MDBLIST_KEY);
        c3.addView(Controls.tile(ctx, ctx.getString(R.string.mdblist_key), ctx.getString(mdb ? R.string.state_on : R.string.not_set), mdb,
                v -> host.push(() -> buildSecretInput(ctx, host, SecretStore.MDBLIST_KEY, R.string.mdblist_key, R.string.mdblist_key_hint))));
        String city = Prefs.city(ctx);
        final String dnsChoice = com.cinetv.launcher.device.NetworkOptimizer.choice(ctx);
        c3.addView(Controls.tile(ctx, ctx.getString(R.string.dns_title), ctx.getString(dnsRes(dnsChoice)),
                !com.cinetv.launcher.device.NetworkOptimizer.DNS_OFF.equals(dnsChoice), v -> {
                    final String next = com.cinetv.launcher.device.NetworkOptimizer.next(com.cinetv.launcher.device.NetworkOptimizer.choice(ctx));
                    new Thread(() -> {
                        boolean ok = com.cinetv.launcher.device.NetworkOptimizer.apply(ctx, next);
                        v.post(() -> {
                            if (!ok && !com.cinetv.launcher.device.NetworkOptimizer.DNS_OFF.equals(next)) {
                                Toast.makeText(ctx, R.string.dns_blocked, Toast.LENGTH_LONG).show();
                            }
                            host.replace(() -> buildSettings(ctx, host, a));
                        });
                    }, "cinetv-dns").start();
                }));
        c3.addView(Controls.tile(ctx, ctx.getString(R.string.weather_city), city != null ? city : ctx.getString(R.string.weather_auto), city != null,
                v -> host.push(() -> buildCityInput(ctx, host, a))));

        // 4. TV & phone
        LinearLayout c4 = column(ctx, cols, R.string.col_tv, 390);
        c4.addView(Controls.tile(ctx, ctx.getString(R.string.tv_settings), ctx.getString(R.string.tv_settings_hint), false,
                v -> AppLauncher.openSystemSettings(ctx)));
        c4.addView(Controls.tile(ctx, ctx.getString(R.string.setup_tune), ctx.getString(R.string.setup_tune_hint), false,
                v -> setup(ctx, host, false)));
        c4.addView(Controls.tile(ctx, ctx.getString(R.string.optimize), ctx.getString(R.string.optimize_hint), false,
                v -> host.push(() -> buildBenchmark(ctx, host))));
        c4.addView(Controls.tile(ctx, ctx.getString(R.string.remote_keys), ctx.getString(R.string.remote_keys_hint), false,
                v -> host.push(() -> buildKeyCapture(ctx, host, a))));
        c4.addView(Controls.tile(ctx, ctx.getString(R.string.reboot), null, false, v -> a.reboot()));
        c4.addView(Controls.alarm(ctx, ctx.getString(R.string.destroy), v -> host.push(() -> buildDestroy(ctx, host))));
        return root;
    }

    private static LinearLayout column(Context ctx, LinearLayout cols, int titleRes, int widthDesign) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        c.addView(Controls.header(ctx, ctx.getString(titleRes)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiKit.px(widthDesign), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(cols.getChildCount() == 0 ? 0 : UiKit.px(24), 0, 0, 0);
        cols.addView(c, lp);
        return c;
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

    private static String appearanceName(Context ctx, String mode) {
        if (ThemeManager.MODE_LIGHT.equals(mode)) return ctx.getString(R.string.appearance_light);
        if (ThemeManager.MODE_AUTO.equals(mode)) return ctx.getString(R.string.appearance_auto);
        return ctx.getString(R.string.appearance_dark);
    }

    private static int perfModeRes(String m) {
        if (PerformanceProfile.MODE_ECO.equals(m)) return R.string.perf_eco;
        if (PerformanceProfile.MODE_MAX.equals(m)) return R.string.perf_max;
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

    /** A deliberate choice, never a cycling tap: Maximum on a weak TV would hurt. */
    private static View buildPerformancePicker(final Context ctx, final ModalHost host, final Actions a) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(Controls.header(ctx, ctx.getString(R.string.performance)));
        String current = PerformanceProfile.mode(ctx);
        String[][] modes = {
                {PerformanceProfile.MODE_AUTO, ctx.getString(R.string.perf_auto), ctx.getString(R.string.perf_auto_hint, ctx.getString(tierRes(PerformanceProfile.detectedTier(ctx))))},
                {PerformanceProfile.MODE_ECO, ctx.getString(R.string.perf_eco), ctx.getString(R.string.perf_eco_hint)},
                {PerformanceProfile.MODE_MAX, ctx.getString(R.string.perf_max), ctx.getString(R.string.perf_max_hint)}};
        for (final String[] m : modes) {
            root.addView(Controls.tile(ctx, m[1], m[2], m[0].equals(current), v -> {
                host.back();
                if (m[0].equals(PerformanceProfile.mode(ctx))) return;
                PerformanceProfile.setMode(ctx, m[0]);
                a.onPerformanceChanged();
            }));
        }
        root.setLayoutParams(new LinearLayout.LayoutParams(UiKit.px(640), ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    // ---- Remove CineTV ------------------------------------------------------------------------------

    private static View buildDestroy(final Context ctx, final ModalHost host) {
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
        row.addView(Controls.alarmButton(ctx, ctx.getString(R.string.destroy_confirm), v -> {
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
        return root;
    }

    /** CineTV's author; opened from the signature at the top left of Home. */
    public static void about(Context ctx, ModalHost host, Actions a) {
        host.push(() -> buildAbout(ctx, host, a));
    }

    // ---- About ------------------------------------------------------------------------------------

    /** CineTV's author: photo and bio from TMDB, and QR codes to follow him from a phone. */
    private static View buildAbout(final Context ctx, final ModalHost host, final Actions a) {
        LinearLayout root = new LinearLayout(ctx);
        root.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        final ImageView photo = new ImageView(ctx);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setClipToOutline(true);
        photo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        photo.setBackground(new Glass(photo, UiKit.pxf(90), Glass.Style.CONTROL));
        left.addView(photo, new LinearLayout.LayoutParams(UiKit.px(180), UiKit.px(180)));
        TextView name = UiKit.bold(ctx, 40, UiKit.INK);
        name.setText(com.cinetv.launcher.service.Creator.NAME);
        name.setPadding(0, UiKit.px(22), 0, 0);
        left.addView(name);
        TextView role = UiKit.bold(ctx, 17, Color.parseColor("#C4B5FD"));
        role.setLetterSpacing(0.12f);
        role.setText(ctx.getString(R.string.about_role).toUpperCase(Locale.getDefault()));
        left.addView(role);
        final TextView bio = UiKit.label(ctx, 20, UiKit.INK_DIM);
        bio.setText(R.string.about_bio);
        bio.setLineSpacing(UiKit.pxf(7), 1f);
        bio.setPadding(0, UiKit.px(18), 0, UiKit.px(24));
        bio.setMaxWidth(UiKit.px(560));
        left.addView(bio);
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.his_films), v -> {
            host.close();
            a.search(com.cinetv.launcher.service.Creator.NAME + " filmleri");
        }));
        View sp = new View(ctx);
        buttons.addView(sp, new LinearLayout.LayoutParams(UiKit.px(16), 1));
        buttons.addView(Controls.button(ctx, ctx.getString(R.string.done), v -> host.back()));
        left.addView(buttons);
        root.addView(left, new LinearLayout.LayoutParams(UiKit.px(540), ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scan with a phone: one code opens a page on this TV with every profile.
        String base = RemotePairing.baseUrl();
        String page = base == null ? com.cinetv.launcher.service.Creator.GITHUB_URL : base + "/me";
        root.addView(followCode(ctx, page, ctx.getString(R.string.about_scan),
                "Instagram · YouTube · Letterboxd · LinkedIn · GitHub"));

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
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout plate = new FrameLayout(ctx);
        plate.setBackground(new Glass(plate, UiKit.pxf(26), Glass.Style.FOCUSED));
        int size = UiKit.px(300);
        ImageView qr = new ImageView(ctx);
        Bitmap code = RemotePairing.qr(url, size, Color.parseColor("#0B1220"), Color.WHITE);
        if (code != null) qr.setImageBitmap(code);
        plate.addView(qr, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        plate.setPadding(UiKit.px(16), UiKit.px(16), UiKit.px(16), UiKit.px(16));
        col.addView(plate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView label = UiKit.bold(ctx, 21, UiKit.INK);
        label.setText(site);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, UiKit.px(16), 0, 0);
        col.addView(label);
        TextView h = UiKit.label(ctx, 17, UiKit.INK_DIM);
        h.setText(handle);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setMaxWidth(size + UiKit.px(32));
        col.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(UiKit.px(60), 0, 0, 0);
        col.setLayoutParams(lp);
        return col;
    }

    // ---- Pairing (QR) -----------------------------------------------------------------------------

    private static View buildPairing(final Context ctx, final ModalHost host, final String section) {
        LinearLayout root = new LinearLayout(ctx);
        root.setGravity(Gravity.CENTER_VERTICAL);
        String url = RemotePairing.pairingUrl(ctx, section);

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

    private static View buildSecretInput(final Context ctx, final ModalHost host, final String name, int titleRes, int hintRes) {
        final EditText et = field(ctx, null, hintRes, true);
        return form(ctx, titleRes, R.string.secret_body, et, v -> {
            String val = et.getText().toString().trim();
            if (!val.isEmpty()) {
                boolean ok = SecretStore.put(ctx, name, val);
                Toast.makeText(ctx, ok ? R.string.saved_securely : R.string.save_failed, Toast.LENGTH_SHORT).show();
            }
            host.back();
        });
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
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(R.string.setup_tune);
        root.addView(title);
        final TextView body = UiKit.label(ctx, 21, UiKit.INK_DIM);
        body.setText(R.string.setup_running);
        body.setLineSpacing(UiKit.pxf(9), 1f);
        body.setPadding(0, UiKit.px(18), 0, UiKit.px(24));
        body.setMaxWidth(UiKit.px(980));
        root.addView(body, new LinearLayout.LayoutParams(UiKit.px(980), ViewGroup.LayoutParams.WRAP_CONTENT));
        final LinearLayout buttons = new LinearLayout(ctx);
        buttons.setVisibility(View.INVISIBLE);
        root.addView(buttons);
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            final com.cinetv.launcher.device.AvOptimizer.Report av = applyAv
                    ? com.cinetv.launcher.device.AvOptimizer.optimize(ctx) : com.cinetv.launcher.device.AvOptimizer.detect(ctx);
            String dns = com.cinetv.launcher.device.NetworkOptimizer.choice(ctx);
            final boolean dnsOk = applyAv ? com.cinetv.launcher.device.NetworkOptimizer.apply(ctx, dns)
                    : com.cinetv.launcher.device.NetworkOptimizer.privateDnsActive(ctx, String.valueOf(com.cinetv.launcher.device.NetworkOptimizer.host(dns)));
            final String dnsLabel = ctx.getString(dnsRes(dns));
            final com.cinetv.launcher.device.NetworkProbe.Result net = com.cinetv.launcher.device.NetworkProbe.run();
            final com.cinetv.launcher.service.StreamProfile profile = com.cinetv.launcher.service.StreamProfile.recommend(net, av);
            Prefs.get(ctx).edit().putString("setup_report", av.toJson().toString())
                    .putFloat("setup_mbps", (float) net.downloadMbps).putBoolean(Prefs.FIRST_SETUP_DONE, true).apply();
            main.post(() -> {
                StringBuilder sb = new StringBuilder();
                String hdr = (av.hdr10 ? "HDR10 " : "") + (av.hlg ? "HLG " : "") + (av.hdr10Plus ? "HDR10+ " : "") + (av.dolbyVision ? "Dolby Vision" : "");
                sb.append(ctx.getString(R.string.setup_picture, av.is4kPanel() ? "4K UHD" : av.uiWidth + "×" + av.uiHeight,
                        hdr.trim().isEmpty() ? "SDR" : hdr.trim(), Math.round(av.peakNits))).append('\n');
                sb.append(ctx.getString(R.string.setup_sound, av.hdmiAudio
                        ? (av.earc ? "HDMI eARC" : "HDMI ARC") + (av.encodings.isEmpty() ? "" : " · " + android.text.TextUtils.join(", ", av.encodings))
                        : ctx.getString(R.string.setup_tv_speakers))).append('\n');
                sb.append(ctx.getString(dnsOk ? R.string.setup_dns_ok : R.string.setup_dns_off, dnsLabel)).append('\n');
                sb.append(net.ok ? ctx.getString(R.string.setup_network, Math.round(net.downloadMbps), net.latencyMs, net.jitterMs)
                        : ctx.getString(R.string.setup_network_failed)).append('\n');
                if (!av.applied.isEmpty()) sb.append(ctx.getString(R.string.setup_applied, android.text.TextUtils.join(", ", av.applied))).append('\n');
                sb.append('\n').append(ctx.getString(R.string.setup_recommend, ctx.getString(profile.labelRes)));
                if (av.is4kPanel() && av.uiWidth < 3840) sb.append('\n').append(ctx.getString(R.string.setup_ui_note));
                if (!"mediatek".equals(PictureCalibration.engine(ctx))) {
                    // The picture engine of this TV only takes values from its own menu: show the filmmaker checklist.
                    sb.append("\n\n").append(ctx.getString(R.string.setup_picture_manual));
                }
                body.setText(sb);
                buttons.removeAllViews();
                buttons.addView(Controls.button(ctx, ctx.getString(R.string.setup_apply_nuvio), v -> {
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
                buttons.addView(Controls.button(ctx, ctx.getString(R.string.done), v -> host.back()));
                buttons.setVisibility(View.VISIBLE);
                buttons.getChildAt(0).requestFocus();
            });
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
        }, "cinetv-bench").start();
        return root;
    }
}
