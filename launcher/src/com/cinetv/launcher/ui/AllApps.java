package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
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

import java.util.List;

/**
 * The app drawer, phone-style: every app as the same metallic logo tile as on Home, five per row,
 * with its own coloured bloom on focus. Used for "All apps" and for "Add" (apps not on Home yet).
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
            FrameLayout tile = tile(ctx, e, l, host);
            arts[i] = (ImageView) ((ViewGroup) tile.getChildAt(1)).getChildAt(0);
            row.addView(tile);
        }
        scroll.addView(grid);
        int rows = (apps.size() + COLS - 1) / COLS;
        int h = Math.min(rows, 4) * UiKit.px(TILE_H + GAP) + pad * 2;
        root.addView(scroll, new LinearLayout.LayoutParams(COLS * UiKit.px(TILE_W + GAP) + pad * 2, h));

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
        return root;
    }

    private static FrameLayout tile(final Context ctx, final AppCatalog.Entry e, final Listener l, final ModalHost host) {
        final FrameLayout frame = new FrameLayout(ctx);
        frame.setFocusable(true);
        frame.setClickable(true);
        frame.setClipChildren(false);
        final int color = AppArtwork.getAppNeonColor(e.packageName);

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
            } else {
                bloom.animate().alpha(0f).setDuration(ms).withEndAction(() -> bloom.setImageDrawable(null)).start();
            }
            bevel.setFocusedState(has);
            face.animate().alpha(has ? 1f : 0.9f).setDuration(ms).start();
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationZ(has ? UiKit.pxf(40) : 0f).setDuration(ms).start();
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
