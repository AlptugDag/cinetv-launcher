package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.R;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.AppCatalog;
import com.cinetv.launcher.service.AppTileItem;

import java.util.Collections;
import java.util.List;

/**
 * The favourite apps (dock) and every other app (grid) as glass platters. Focus lifts a tile with the
 * app-coloured bloom; at rest the rows keep the JARVIS cylinder curvature when Spatial HUD is on.
 * Long-press OK opens Move / Hide / Uninstall / Info; in move mode the arrows carry the tile.
 */
public class AppGrid {

    public interface Host {
        void onTileFocused(View tile, int color);
        void onLaunch(AppTileItem item);
        void onEdgeUp();
        void showActions(AppTileItem item, boolean inDock, int index, View tile);
        void onAddToDock();
        void onOrderChanged();
    }

    private static final int TILE_W = 380, TILE_H = 214, BLOOM_PAD = 68;

    private final Context ctx;
    private final Host host;
    private final LinearLayout dockRow;
    private final LinearLayout gridBox;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<AppTileItem> dock;
    private List<AppTileItem> grid;
    private int columns = 4;
    private boolean spatial;

    private AppTileItem moving;
    private boolean movingInDock;
    private int movingIndex = -1;

    public AppGrid(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        dockRow = new LinearLayout(ctx);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        gridBox = new LinearLayout(ctx);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setClipChildren(false);
        gridBox.setClipToPadding(false);
    }

    public View dockView() { return dockRow; }
    public View gridView() { return gridBox; }

    public void setColumns(int columns) { this.columns = columns; }
    public void setSpatial(boolean on) { spatial = on; }

    public void setApps(List<AppTileItem> dock, List<AppTileItem> grid) {
        this.dock = dock;
        this.grid = grid;
        for (AppTileItem item : dock) ensureArtwork(item);
        for (AppTileItem item : grid) ensureArtwork(item);
        render();
    }

    private void ensureArtwork(AppTileItem item) {
        if (item.bannerDrawable != null) return;
        item.label = AppArtwork.getCleanLabel(item.packageName, item.label);
        item.neonColor = AppArtwork.getAppNeonColor(item.packageName);
        android.graphics.drawable.Drawable icon = null, banner = null;
        try {
            banner = ctx.getPackageManager().getApplicationBanner(item.packageName);
            icon = ctx.getPackageManager().getApplicationIcon(item.packageName);
        } catch (Throwable ignored) {
        }
        item.bannerDrawable = AppArtwork.createDesignerLogoCard(ctx, item.packageName, item.label, banner, icon, true);
    }

    public void focusDock(int index) {
        if (dockRow.getChildCount() == 0) return;
        dockRow.getChildAt(Math.max(0, Math.min(index, dockRow.getChildCount() - 1))).requestFocus();
    }

    public boolean isMoving() { return moving != null; }

    public void render() {
        if (dock == null) return;
        dockRow.removeAllViews();
        for (int i = 0; i < dock.size(); i++) {
            dockRow.addView(tile(dock.get(i), true, i, dock.size()), tileParams(1f));
        }

        // The grid always ends with the "Add" tile, right after the last app.
        gridBox.removeAllViews();
        LinearLayout row = null;
        int cells = gridCells();
        for (int i = 0; i < cells; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(ctx);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                gridBox.addView(row);
            }
            row.addView(i < grid.size() ? tile(grid.get(i), false, i, columns) : addTile(i), tileParams(1f));
        }
        if (row != null) {
            for (int r = cells % columns; r != 0 && r < columns; r++) row.addView(new View(ctx), tileParams(1f));
        }
    }

    /** Grid apps plus the trailing "Add" tile. */
    private int gridCells() {
        return grid.size() + 1;
    }

    private LinearLayout.LayoutParams tileParams(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiKit.px(columns > 4 ? TILE_H * 0.75f : TILE_H), weight);
        lp.setMargins(UiKit.px(12), UiKit.px(8), UiKit.px(12), UiKit.px(8));
        return lp;
    }

    /** Resting pose on the cylinder: the outer tiles turn in towards the viewer. */
    private float[] restPose(boolean inDock, int index, int count) {
        if (!spatial) return new float[]{0, 0, 0};
        int col = inDock ? index : index % columns;
        int n = inDock ? count : columns;
        int row = inDock ? 0 : index / columns;
        float mid = (n - 1) / 2f;
        float norm = n <= 1 ? 0 : (col - mid) / mid;
        float rotY = -norm * (inDock ? 27f : 24f);
        float rotX = -11f - row * 2.5f;
        float z = (1f - norm * norm) * (inDock ? 16f : 14f) - (inDock ? 10f : 12f);
        return new float[]{rotY, Math.max(-18f, rotX), z};
    }

    private View tile(final AppTileItem item, final boolean inDock, final int index, int count) {
        final FrameLayout frame = new FrameLayout(ctx);
        frame.setFocusable(true);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        frame.setCameraDistance(ctx.getResources().getDisplayMetrics().density * 2800f);

        final ImageView bloom = new ImageView(ctx);
        bloom.setScaleType(ImageView.ScaleType.FIT_XY);
        bloom.setAlpha(0f);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                UiKit.px(TILE_W) + Glow.padH(UiKit.px(BLOOM_PAD)) * 2, UiKit.px(TILE_H) + UiKit.px(BLOOM_PAD) * 2, Gravity.CENTER);
        blp.setMargins(-Glow.padH(UiKit.px(BLOOM_PAD)), -UiKit.px(BLOOM_PAD), -Glow.padH(UiKit.px(BLOOM_PAD)), -UiKit.px(BLOOM_PAD));
        frame.addView(bloom, blp);

        // Artwork and bevel are cached together as one GPU texture; the bloom stays outside the layer
        // because it spreads beyond the tile. Moving the shelf then costs one quad per tile.
        final FrameLayout face = new FrameLayout(ctx);
        final ImageView art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.FIT_XY);
        art.setImageDrawable(item.bannerDrawable);
        face.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final GlassBevelView bevel = new GlassBevelView(ctx, UiKit.pxf(22), false);
        face.addView(bevel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        face.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        face.setAlpha(0.92f);
        frame.addView(face, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final float[] rest = restPose(inDock, index, count);
        frame.setRotationY(rest[0]);
        frame.setRotationX(rest[1]);
        frame.setTranslationZ(rest[2]);
        boolean carried = moving != null && moving.packageName.equals(item.packageName);
        if (carried) {
            applyFocus(frame, bloom, face, item, true, rest);
            bevel.setFocusedState(true);
        }

        frame.setOnFocusChangeListener((v, has) -> {
            if (moving != null && !carried) return;
            applyFocus(frame, bloom, face, item, has, rest);
            bevel.setFocusedState(has);
            if (has) host.onTileFocused(v, item.neonColor);
        });
        frame.setOnClickListener(v -> host.onLaunch(item));

        final boolean[] longPressed = {false};
        final Runnable longPress = () -> {
            longPressed[0] = true;
            host.showActions(item, inDock, index, frame);
        };
        frame.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                    longPressed[0] = false;
                    handler.postDelayed(longPress, 900);
                } else if (e.getAction() == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(longPress);
                    if (longPressed[0]) return true;
                }
                return longPressed[0];
            }
            if (keyCode == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_DOWN) {
                host.showActions(item, inDock, index, frame);
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN || moving != null) return false;
            return navigate(keyCode, inDock, index);
        });
        return frame;
    }

    private void applyFocus(View frame, ImageView bloom, View art, AppTileItem item, boolean has, float[] rest) {
        int ms = PerformanceProfile.animationMs(ctx, 170);
        if (has) {
            bloom.setImageBitmap(Glow.bloom(UiKit.px(TILE_W), UiKit.px(TILE_H), UiKit.px(BLOOM_PAD), UiKit.pxf(36), item.neonColor));
            bloom.animate().alpha(1f).setDuration(ms).start();
        } else {
            bloom.animate().alpha(0f).setDuration(ms).withEndAction(() -> bloom.setImageDrawable(null)).start();
        }
        art.animate().alpha(has ? 1f : 0.92f).setDuration(ms).start();
        frame.animate().scaleX(has ? 1.10f : 1f).scaleY(has ? 1.10f : 1f)
                .rotationY(has ? 0f : rest[0]).rotationX(has ? 0f : rest[1])
                .translationY(has ? -UiKit.pxf(12) : 0f).translationZ(has ? UiKit.pxf(55) : rest[2])
                .setDuration(ms).start();
    }

    private View addTile(final int index) {
        final FrameLayout card = new FrameLayout(ctx);
        card.setFocusable(true);
        final Glass material = Glass.panel(card, 36f);
        card.setBackground(material);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        TextView plus = UiKit.centered(UiKit.text(ctx, 56, UiKit.INK, com.cinetv.launcher.FontManager.getMedium(ctx)));
        plus.setText("+");
        content.addView(plus);
        TextView label = UiKit.centered(UiKit.label(ctx, 20, UiKit.INK_DIM));
        label.setText(R.string.add);
        content.addView(label);
        card.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final GlassBevelView bevel = new GlassBevelView(ctx, UiKit.pxf(36), false);
        card.addView(bevel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final float[] rest = restPose(false, index, columns);
        card.setCameraDistance(ctx.getResources().getDisplayMetrics().density * 2800f);
        card.setRotationY(rest[0]);
        card.setRotationX(rest[1]);
        card.setOnFocusChangeListener((v, has) -> {
            material.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.PANEL);
            bevel.setFocusedState(has);
            plus.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            label.setTextColor(has ? UiKit.INK_DARK : UiKit.INK_DIM);
            v.animate().scaleX(has ? 1.10f : 1f).scaleY(has ? 1.10f : 1f).rotationY(has ? 0 : rest[0]).rotationX(has ? 0 : rest[1])
                    .setDuration(PerformanceProfile.animationMs(ctx, 170)).start();
            if (has) host.onTileFocused(v, UiKit.INK);
        });
        card.setOnClickListener(v -> host.onAddToDock());
        card.setOnKeyListener((v, keyCode, e) -> e.getAction() == KeyEvent.ACTION_DOWN && moving == null && navigate(keyCode, false, index));
        return card;
    }

    /** Wrap-around navigation inside the dock and grid; up from the dock goes to the shelf. */
    private boolean navigate(int keyCode, boolean inDock, int index) {
        if (inDock) {
            int n = dockRow.getChildCount();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) { dockRow.getChildAt(n - 1).requestFocus(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == n - 1) { dockRow.getChildAt(0).requestFocus(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { host.onEdgeUp(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { focusGrid(Math.min(index, Math.min(columns, gridCells()) - 1)); return true; }
            return false;
        }
        int col = index % columns, cells = gridCells();
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && col == 0) { focusGrid(Math.min(index + columns - 1, cells - 1)); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && (col == columns - 1 || index == cells - 1)) { focusGrid(index - col); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && index < columns) {
            if (dockRow.getChildCount() == 0) host.onEdgeUp();
            else focusDock(Math.min(col, dockRow.getChildCount() - 1));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && index + columns >= cells) {
            if (index + columns < ((cells + columns - 1) / columns) * columns) { focusGrid(cells - 1); return true; }
            return true;
        }
        return false;
    }

    private void focusGrid(int flat) {
        int row = flat / columns, col = flat % columns;
        if (row < gridBox.getChildCount()) {
            ViewGroup r = (ViewGroup) gridBox.getChildAt(row);
            if (col < r.getChildCount()) r.getChildAt(col).requestFocus();
        }
    }

    // ---- move mode --------------------------------------------------------------------------------

    public void startMove(AppTileItem item, boolean inDock, int index) {
        moving = item;
        movingInDock = inDock;
        movingIndex = index;
        render();
        refocusMoving();
    }

    public void stopMove() {
        if (moving == null) return;
        moving = null;
        AppCatalog.saveOrder(ctx, dock, grid);
        render();
        host.onOrderChanged();
    }

    /** Arrow keys while carrying a tile. Returns true when consumed. */
    public boolean handleMoveKey(int keyCode) {
        if (moving == null) return false;
        List<AppTileItem> list = movingInDock ? dock : grid;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int target = movingIndex + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (target >= 0 && target < list.size()) {
                    Collections.swap(list, movingIndex, target);
                    movingIndex = target;
                }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (movingInDock) {
                    grid.add(0, dock.remove(movingIndex));
                    movingInDock = false;
                    movingIndex = 0;
                } else if (movingIndex + columns < grid.size()) {
                    Collections.swap(grid, movingIndex, movingIndex + columns);
                    movingIndex += columns;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!movingInDock && movingIndex < columns) {
                    dock.add(grid.remove(movingIndex));
                    movingInDock = true;
                    movingIndex = dock.size() - 1;
                } else if (!movingInDock) {
                    Collections.swap(grid, movingIndex, movingIndex - columns);
                    movingIndex -= columns;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                stopMove();
                return true;
            default:
                return true;
        }
        render();
        refocusMoving();
        return true;
    }

    private void refocusMoving() {
        handler.post(() -> {
            if (movingInDock) focusDock(movingIndex);
            else focusGrid(movingIndex);
        });
    }

    public void addToDock(AppTileItem item) {
        grid.remove(item);
        dock.add(item);
        AppCatalog.saveOrder(ctx, dock, grid);
        render();
    }

    public List<AppTileItem> dockItems() { return dock; }
    public List<AppTileItem> gridItems() { return grid; }
}
