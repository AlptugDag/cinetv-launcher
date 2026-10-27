package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
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
 * Every app as glass platters in one list, row after row: the first row is simply the first apps of the
 * list, so a free place in a row is always filled by the next app (nothing waits below a half-empty row). Focus lifts a tile with the
 * app-coloured bloom; at rest the rows keep a gentle cylinder curvature when Spatial HUD is on.
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

    /** The theme's density; more apps raise it so tiles stay one size on every row. */
    public void setColumns(int columns) { this.baseColumns = columns; }

    private int baseColumns = 4;

    private int columnsFor(int apps) {
        int c = baseColumns;
        if (apps > 13) c = Math.max(c, 5);
        if (apps > 19) c = Math.max(c, 6);
        return c;
    }
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

    /** One list: the first row takes the first {@code columns} apps, the rows below the rest, in order. */
    private void rebalance() {
        java.util.ArrayList<AppTileItem> all = new java.util.ArrayList<>(dock);
        all.addAll(grid);
        columns = columnsFor(all.size() + 1);
        dock = new java.util.ArrayList<>(all.subList(0, Math.min(columns, all.size())));
        grid = new java.util.ArrayList<>(all.subList(Math.min(columns, all.size()), all.size()));
    }

    private int total() {
        return dock.size() + grid.size();
    }

    public void render() {
        if (dock == null) return;
        rebalance();
        // Every tile has the same size; a row with fewer apps than columns is centred, never left with a gap.
        int slots = Math.max(columns, dock.size());
        dockRow.removeAllViews();
        dockRow.setWeightSum(slots);
        dockRow.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int i = 0; i < dock.size(); i++) {
            dockRow.addView(tile(dock.get(i), true, i, columns), tileParams(1f));
        }
        // a short list: "Add" sits right after the last app in the first row
        if (grid.isEmpty() && dock.size() < columns) dockRow.addView(addTile(dock.size(), true), tileParams(1f));

        // The grid always ends with the "Add" tile, right after the last app.
        gridBox.removeAllViews();
        LinearLayout row = null;
        int cells = gridCells();
        for (int i = 0; i < cells; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(ctx);
                row.setWeightSum(columns);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                gridBox.addView(row);
            }
            row.addView(i < grid.size() ? tile(grid.get(i), false, i, columns) : addTile(i, false), tileParams(1f));
        }
    }

    /** Grid apps plus the trailing "Add" tile (which is in the first row while the list fits there). */
    private int gridCells() {
        return grid.isEmpty() && dock.size() < columns ? 0 : grid.size() + 1;
    }

    private LinearLayout.LayoutParams tileParams(float weight) {
        // 16:9 tiles sized from the column count (1792 design px of row width).
        float w = 1792f / columns - 24f;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiKit.px(w * TILE_H / TILE_W), weight);
        lp.setMargins(UiKit.px(12), UiKit.px(8), UiKit.px(12), UiKit.px(8));
        return lp;
    }

    /** Resting pose on the cylinder: the outer tiles turn in towards the viewer. */
    private float[] restPose(boolean inDock, int index, int count) {
        if (!spatial) return new float[]{0, 0, 0};
        // the curve is measured on the tiles actually in this row (a short row is centred), so both ends of
        // every row lean the same way and by the same amount: the Spatial HUD's full curve, never lopsided
        int col = inDock ? index : index % columns;
        int row = inDock ? 0 : index / columns + 1;
        int n = Math.max(1, rowCount(inDock, index));
        float mid = (n - 1) / 2f;
        float norm = n <= 1 ? 0 : (col - mid) / mid;
        float rotY = -norm * 24f;
        float rotX = -11f - row * 2.5f;
        float z = (1f - norm * norm) * 14f - 12f;
        return new float[]{rotY, Math.max(-18f, rotX), z};
    }

    /** How many tiles (apps and the "Add" tile) the row of this tile holds. */
    private int rowCount(boolean inDock, int index) {
        if (inDock) return dock.size() + (grid.isEmpty() && dock.size() < columns ? 1 : 0);
        int cells = gridCells(), rowStart = (index / columns) * columns;
        return Math.min(columns, cells - rowStart);
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
            // On light rooms a full neon bloom looks smeared: keep it as a faint tint.
            bloom.animate().alpha(UiKit.isLight() ? 0.35f : 1f).setDuration(ms).start();
        } else {
            bloom.animate().alpha(0f).setDuration(ms).withEndAction(() -> bloom.setImageDrawable(null)).start();
        }
        art.animate().alpha(has ? 1f : 0.92f).setDuration(ms).start();
        pushNeighbours(frame, has, ms);
        frame.animate().scaleX(has ? 1.10f : 1f).scaleY(has ? 1.10f : 1f)
                .rotationY(has ? 0f : rest[0]).rotationX(has ? 0f : rest[1])
                .translationY(has ? -UiKit.pxf(12) : 0f).translationZ(has ? UiKit.pxf(55) : rest[2])
                .setDuration(ms).start();
    }

    /** The focused tile grows; its row-mates move aside by half the growth so nothing overlaps. */
    private void pushNeighbours(View tile, boolean has, int ms) {
        if (!(tile.getParent() instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) tile.getParent();
        int at = row.indexOfChild(tile);
        float shift = tile.getWidth() * 0.05f + UiKit.pxf(6);
        for (int i = 0; i < row.getChildCount(); i++) {
            View n = row.getChildAt(i);
            if (n == tile) continue;
            n.animate().translationX(has ? (i < at ? -shift : shift) : 0f).setDuration(ms).start();
        }
    }

    private View addTile(final int index, final boolean inDock) {
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

        final FrameLayout face = new FrameLayout(ctx);
        final ImageView art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.FIT_XY);
        art.setImageDrawable(AppArtwork.createAddCard(ctx));
        face.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final GlassBevelView bevel = new GlassBevelView(ctx, UiKit.pxf(22), false);
        face.addView(bevel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        face.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        face.setAlpha(0.92f);
        frame.addView(face, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final float[] rest = restPose(inDock, index, columns);
        frame.setRotationY(rest[0]);
        frame.setRotationX(rest[1]);
        frame.setTranslationZ(rest[2]);

        final int neonColor = Color.parseColor("#CBD5E1");

        frame.setOnFocusChangeListener((v, has) -> {
            int ms = PerformanceProfile.animationMs(ctx, 170);
            if (has) {
                bloom.setImageBitmap(Glow.bloom(UiKit.px(TILE_W), UiKit.px(TILE_H), UiKit.px(BLOOM_PAD), UiKit.pxf(36), neonColor));
                bloom.animate().alpha(UiKit.isLight() ? 0.35f : 1f).setDuration(ms).start();
            } else {
                bloom.animate().alpha(0f).setDuration(ms).withEndAction(() -> bloom.setImageDrawable(null)).start();
            }
            bevel.setFocusedState(has);
            face.animate().alpha(has ? 1f : 0.92f).setDuration(ms).start();
            pushNeighbours(frame, has, ms);
            frame.animate().scaleX(has ? 1.10f : 1f).scaleY(has ? 1.10f : 1f)
                    .rotationY(has ? 0f : rest[0]).rotationX(has ? 0f : rest[1])
                    .translationY(has ? -UiKit.pxf(12) : 0f).translationZ(has ? UiKit.pxf(55) : rest[2])
                    .setDuration(ms).start();
            if (has) host.onTileFocused(v, neonColor);
        });
        frame.setOnClickListener(v -> host.onAddToDock());
        frame.setOnKeyListener((v, keyCode, e) -> e.getAction() == KeyEvent.ACTION_DOWN && moving == null && navigate(keyCode, inDock, index));
        return frame;
    }

    /** Wrap-around navigation inside the dock and grid; up from the dock goes to the shelf. */
    private boolean navigate(int keyCode, boolean inDock, int index) {
        if (inDock) {
            int n = dockRow.getChildCount();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) { dockRow.getChildAt(n - 1).requestFocus(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == n - 1) { dockRow.getChildAt(0).requestFocus(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { host.onEdgeUp(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (gridCells() > 0) focusGrid(Math.min(index, Math.min(columns, gridCells()) - 1));
                return true;
            }
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

    /** Arrow keys while carrying a tile, through the one list. Returns true when consumed. */
    public boolean handleMoveKey(int keyCode) {
        if (moving == null) return false;
        java.util.ArrayList<AppTileItem> all = new java.util.ArrayList<>(dock);
        all.addAll(grid);
        int at = movingInDock ? movingIndex : dock.size() + movingIndex;
        int target = at;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: target = at - 1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: target = at + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: target = at - columns; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: target = Math.min(all.size() - 1, at + columns); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                stopMove();
                return true;
            default:
                return true;
        }
        if (target >= 0 && target < all.size() && target != at) {
            AppTileItem item = all.remove(at);
            all.add(target, item);
            at = target;
        }
        dock = new java.util.ArrayList<>(all);
        grid = new java.util.ArrayList<>();
        rebalance();
        movingInDock = at < dock.size();
        movingIndex = movingInDock ? at : at - dock.size();
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

    /** An app added to Home goes to the end of the list, in the first free place. */
    public void addToDock(AppTileItem item) {
        dock.remove(item);
        grid.remove(item);
        grid.add(item);
        rebalance();
        AppCatalog.saveOrder(ctx, dock, grid);
        render();
    }

    public List<AppTileItem> dockItems() { return dock; }
    public List<AppTileItem> gridItems() { return grid; }
}
