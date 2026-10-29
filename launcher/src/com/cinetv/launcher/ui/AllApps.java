package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.AppCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The app drawer, phone-style: every app as the same metallic logo tile as on Home, five per row,
 * with its own coloured bloom on focus. Separates Entertainment Apps vs Tools/System into tabs.
 * Artwork is drawn off the main thread so the drawer opens instantly even on slow TVs.
 */
public final class AllApps {

    public interface Listener {
        void open(String pkg);
        /** Long-press / Menu on a tile; may be null. */
        void actions(String pkg, String label);
    }

    private static final int COLS = 5, TILE_W = 300, TILE_H = 169, BLOOM_PAD = 48, GAP = 26;

    private AllApps() {}

    public static boolean isTool(String pkg, String label) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        String l = label != null ? label.toLowerCase(Locale.ROOT) : "";
        if (p.contains("settings") || l.contains("ayar") || l.contains("setting")) return true;
        if (p.contains("vending") || p.contains("playstore") || l.contains("play store") || l.contains("store")) return true;
        if (p.contains("file") || p.contains("explorer") || l.contains("dosya") || l.contains("file") || l.contains("x-plore") || p.contains("cxinventor")) return true;
        if (p.contains("terminal") || p.contains("adb") || p.contains("speedtest") || l.contains("hız") || l.contains("speed")) return true;
        if (p.contains("browser") || p.contains("chrome") || l.contains("tarayıcı") || l.contains("browser")) return true;
        if (p.contains("mediatek") || p.contains("xiaomi") || p.contains("arcelik") || p.contains("beko") || p.contains("grundig") || p.contains("android.tv.frameworkpackagestubs")) return true;
        if (p.contains("input") || p.contains("tuner") || p.contains("tvcenter") || p.contains("livetv")) return true;
        if (p.contains("system") || p.contains("packageinstaller") || p.contains("documentsui") || p.contains("certinstaller")) return true;
        return false;
    }

    public static void show(final Context ctx, final ModalHost host, final CharSequence title,
                            final List<AppCatalog.Entry> apps, final CharSequence emptyText, final Listener l) {
        host.push(() -> build(ctx, host, title, apps, emptyText, l));
    }

    private static View build(final Context ctx, final ModalHost host, CharSequence titleText,
                              List<AppCatalog.Entry> apps, CharSequence emptyText, final Listener l) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(titleText);
        title.setPadding(UiKit.px(8), 0, 0, UiKit.px(12));
        root.addView(title);

        if (apps.isEmpty()) {
            TextView empty = UiKit.label(ctx, 22, UiKit.INK_DIM);
            empty.setText(emptyText);
            empty.setPadding(UiKit.px(8), UiKit.px(10), UiKit.px(8), UiKit.px(24));
            root.addView(empty, new LinearLayout.LayoutParams(UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(Controls.button(ctx, "OK", v -> host.back()));
            return root;
        }

        final List<AppCatalog.Entry> appsList = new ArrayList<>();
        final List<AppCatalog.Entry> toolsList = new ArrayList<>();
        for (AppCatalog.Entry e : apps) {
            if (isTool(e.packageName, e.label)) toolsList.add(e);
            else appsList.add(e);
        }
        final List<AppCatalog.Entry> hiddenList = AppCatalog.hiddenApps(ctx);

        final FrameLayout container = new FrameLayout(ctx);
        container.setClipChildren(false);

        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        if (!toolsList.isEmpty() || !hiddenList.isEmpty()) {
            LinearLayout tabRow = new LinearLayout(ctx);
            tabRow.setOrientation(LinearLayout.HORIZONTAL);
            tabRow.setPadding(UiKit.px(8), 0, 0, UiKit.px(10));

            final List<TextView> allTabs = new ArrayList<>();
            TextView tabApps = makeTab(ctx, (tr ? "Uygulamalar" : "Apps") + " (" + appsList.size() + ")", true);
            allTabs.add(tabApps);
            TextView tabTools = null;
            if (!toolsList.isEmpty()) {
                tabTools = makeTab(ctx, (tr ? "Araçlar & Sistem" : "Tools & System") + " (" + toolsList.size() + ")", false);
                allTabs.add(tabTools);
            }
            TextView tabHidden = null;
            if (!hiddenList.isEmpty()) {
                tabHidden = makeTab(ctx, (tr ? "Gizlenenler" : "Hidden") + " (" + hiddenList.size() + ")", false);
                allTabs.add(tabHidden);
            }

            final TextView fTabApps = tabApps;
            final TextView fTabTools = tabTools;
            final TextView fTabHidden = tabHidden;

            tabApps.setOnClickListener(v -> {
                for (TextView t : allTabs) {
                    boolean sel = (t == fTabApps);
                    t.setTag(sel);
                    applyTabBg(t, sel, t.hasFocus());
                }
                renderGrid(ctx, container, host, appsList, l);
            });

            if (tabTools != null) {
                tabTools.setOnClickListener(v -> {
                    for (TextView t : allTabs) {
                        boolean sel = (t == fTabTools);
                        t.setTag(sel);
                        applyTabBg(t, sel, t.hasFocus());
                    }
                    renderGrid(ctx, container, host, toolsList, l);
                });
            }

            if (tabHidden != null) {
                tabHidden.setOnClickListener(v -> {
                    for (TextView t : allTabs) {
                        boolean sel = (t == fTabHidden);
                        t.setTag(sel);
                        applyTabBg(t, sel, t.hasFocus());
                    }
                    renderGrid(ctx, container, host, hiddenList, l);
                });
            }

            tabRow.addView(tabApps);
            if (tabTools != null) {
                View sp1 = new View(ctx);
                tabRow.addView(sp1, new LinearLayout.LayoutParams(UiKit.px(14), 1));
                tabRow.addView(tabTools);
            }
            if (tabHidden != null) {
                View sp2 = new View(ctx);
                tabRow.addView(sp2, new LinearLayout.LayoutParams(UiKit.px(14), 1));
                tabRow.addView(tabHidden);
            }
            root.addView(tabRow);

            renderGrid(ctx, container, host, appsList, l);
        } else {
            renderGrid(ctx, container, host, apps, l);
        }

        root.addView(container);
        return root;
    }

    private static TextView makeTab(Context ctx, String text, boolean selected) {
        TextView tv = UiKit.bold(ctx, 16, selected ? Color.WHITE : UiKit.INK_DIM);
        tv.setText(text);
        int padH = UiKit.px(22), padV = UiKit.px(8);
        tv.setPadding(padH, padV, padH, padV);
        tv.setFocusable(true);
        tv.setClickable(true);
        tv.setTag(selected);
        applyTabBg(tv, selected, false);
        tv.setOnFocusChangeListener((v, hasFocus) -> {
            boolean isSel = Boolean.TRUE.equals(tv.getTag());
            applyTabBg(tv, isSel, hasFocus);
            v.animate().scaleX(hasFocus ? 1.06f : 1f).scaleY(hasFocus ? 1.06f : 1f).setDuration(140).start();
        });
        return tv;
    }

    private static void applyTabBg(TextView tv, boolean selected, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(UiKit.pxf(20));
        if (focused) {
            gd.setColor(Color.parseColor("#4C1D95"));
            gd.setStroke(UiKit.px(2), Color.parseColor("#A78BFA"));
            tv.setTextColor(Color.WHITE);
        } else if (selected) {
            gd.setColor(Color.parseColor("#2E1065"));
            gd.setStroke(UiKit.px(1), Color.parseColor("#7C3AED"));
            tv.setTextColor(Color.WHITE);
        } else {
            gd.setColor(Color.argb(35, 255, 255, 255));
            gd.setStroke(0, 0);
            tv.setTextColor(UiKit.INK_DIM);
        }
        tv.setBackground(gd);
    }

    private static void renderGrid(final Context ctx, final FrameLayout container, final ModalHost host,
                                   final List<AppCatalog.Entry> apps, final Listener l) {
        container.removeAllViews();
        if (apps.isEmpty()) {
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());
            TextView empty = UiKit.label(ctx, 20, UiKit.INK_DIM);
            empty.setText(tr ? "Bu kategoride uygulama bulunamadı." : "No apps found in this category.");
            empty.setPadding(UiKit.px(8), UiKit.px(20), UiKit.px(8), UiKit.px(20));
            container.addView(empty);
            return;
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        int pad = UiKit.px(BLOOM_PAD / 2 + 10);
        scroll.setPadding(pad, pad, pad, pad);
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        final ImageView[] arts = new ImageView[apps.size()];
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(ctx);
                row.setClipChildren(false);
                grid.addView(row);
            }
            final AppCatalog.Entry e = apps.get(i);
            FrameLayout tile = tile(ctx, e, i, l, host);
            arts[i] = (ImageView) ((ViewGroup) tile.getChildAt(1)).getChildAt(0);
            row.addView(tile);
        }
        scroll.addView(grid);
        int rows = (apps.size() + COLS - 1) / COLS;
        int h = Math.min(rows, 4) * UiKit.px(TILE_H + GAP) + pad * 2;
        container.addView(scroll, new FrameLayout.LayoutParams(COLS * UiKit.px(TILE_W + GAP) + pad * 2, h));

        // Artwork in the background, in display order.
        final Context app = ctx.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            for (int i = 0; i < apps.size(); i++) {
                AppCatalog.Entry e = apps.get(i);
                Drawable banner = null;
                try {
                    banner = app.getPackageManager().getApplicationBanner(e.packageName);
                } catch (Throwable ignored) {
                }
                final Drawable art = AppArtwork.createDesignerLogoCard(app, e.packageName,
                        AppArtwork.getCleanLabel(e.packageName, e.label), banner, e.icon, true);
                final ImageView target = arts[i];
                ui.post(() -> target.setImageDrawable(art));
            }
        }, "cinetv-drawer").start();
    }

    private static FrameLayout tile(final Context ctx, final AppCatalog.Entry e, final int index, final Listener l, final ModalHost host) {
        final FrameLayout frame = new FrameLayout(ctx);
        frame.setFocusable(true);
        frame.setClickable(true);
        frame.setClipChildren(false);
        frame.setCameraDistance(ctx.getResources().getDisplayMetrics().density * 3200f);
        final int color = AppArtwork.getAppNeonColor(e.packageName);
        final boolean spatial = com.cinetv.launcher.service.Prefs.spatial3d(ctx);
        final float norm = ((index % COLS) - (COLS - 1) * 0.5f) / ((COLS - 1) * 0.5f);
        final float restRotY = spatial ? -norm * 16f : 0f;
        final float restRotX = spatial ? -5.5f : 0f;
        frame.setRotationY(restRotY);
        frame.setRotationX(restRotX);

        final ImageView bloom = new ImageView(ctx);
        bloom.setScaleType(ImageView.ScaleType.FIT_XY);
        bloom.setAlpha(0f);
        int bp = UiKit.px(BLOOM_PAD), bph = Glow.padH(bp);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(UiKit.px(TILE_W) + bph * 2, UiKit.px(TILE_H) + bp * 2, Gravity.CENTER);
        blp.setMargins(-bph, -bp, -bph, -bp);
        frame.addView(bloom, blp);

        final FrameLayout face = new FrameLayout(ctx);
        ImageView art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.FIT_XY);
        face.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final GlassBevelView bevel = new GlassBevelView(ctx, UiKit.pxf(20), false);
        face.addView(bevel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        face.setAlpha(0.9f);
        frame.addView(face, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        frame.setOnFocusChangeListener((v, has) -> {
            int ms = PerformanceProfile.animationMs(ctx, 160);
            if (has) {
                bloom.setImageBitmap(Glow.bloom(UiKit.px(TILE_W), UiKit.px(TILE_H), bp, UiKit.pxf(30), color));
                bloom.animate().alpha(1f).setDuration(ms).start();
                SpatialEnvironment env = SpatialEnvironment.current();
                if (env != null) env.focusOn(v, color);
            } else {
                bloom.animate().alpha(0f).setDuration(ms).withEndAction(() -> bloom.setImageDrawable(null)).start();
            }
            bevel.setFocusedState(has);
            face.animate().alpha(has ? 1f : 0.9f).setDuration(ms).start();
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f)
                    .rotationY(has ? 0f : restRotY).rotationX(has ? 0f : restRotX)
                    .translationZ(has ? UiKit.pxf(40) : 0f).setDuration(ms).start();
        });
        frame.setOnClickListener(v -> {
            host.close();
            l.open(e.packageName);
        });
        if (l != null) {
            frame.setOnKeyListener((v, keyCode, ev) -> {
                if (keyCode == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_DOWN) {
                    l.actions(e.packageName, e.label);
                    return true;
                }
                return false;
            });
            frame.setOnLongClickListener(v -> {
                l.actions(e.packageName, e.label);
                return true;
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiKit.px(TILE_W), UiKit.px(TILE_H));
        lp.setMargins(UiKit.px(GAP / 2f), UiKit.px(GAP / 2f), UiKit.px(GAP / 2f), UiKit.px(GAP / 2f));
        frame.setLayoutParams(lp);
        return frame;
    }
}
