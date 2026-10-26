package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The room's furniture around the DVD row, for themes whose scenery should touch the cases:
 *
 *   cinema    the hall's own red velvet (cut from its photo) hangs in front of both ends of the row:
 *             the cases further down the shelf slide out from behind it, the focused one stands in
 *             the clear between; every case takes the hall's warm light (grade())
 *   closet    the row is one shelf of a film closet: white laminate back and board, the board's front
 *             edge in front of the cases' feet; the focused case, pulled forward, stands in front of
 *             it as if just taken off the shelf
 *
 * Two plain views inside the shelf: the back one below every case, the front one above every case
 * but the focused one (translationZ 95 between the focused case's 100 and its neighbours' 90).
 * Each is drawn once into its display list; nothing animates.
 */
final class ShelfSet {
    static final float BACK_Z = -50f, FRONT_Z = 95f;

    private ShelfSet() {}

    static View back(Context ctx, String scene, int caseW, float caseH) {
        return "closet".equals(scene) ? new Layer(ctx, scene, caseW, caseH, false) : null;
    }

    static View front(Context ctx, String scene, int caseW, float caseH) {
        return "closet".equals(scene) || "cinema".equals(scene) ? new Layer(ctx, scene, caseW, caseH, true) : null;
    }

    /** The room's light on the cases: the hall's warm stage light, the closet's cool ceiling tubes. */
    static android.graphics.ColorMatrix grade(String scene) {
        if ("cinema".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    0.98f, 0.06f, 0f, 0, 6,
                    0.02f, 0.80f, 0.02f, 0, -2,
                    0f, 0.04f, 0.66f, 0, -6,
                    0, 0, 0, 1, 0});
        }
        if ("closet".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    0.96f, 0.02f, 0.02f, 0, 2,
                    0.01f, 0.97f, 0.02f, 0, 3,
                    0.01f, 0.02f, 1.0f, 0, 5,
                    0, 0, 0, 1, 0});
        }
        return null;
    }

    private static final class Layer extends View {
        private final String scene;
        private final boolean front;
        private final float caseH;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** How far beyond the shelf's own bounds the furniture reaches (Home's side margins). */
        private final float bleed;

        Layer(Context ctx, String scene, int caseW, float caseH, boolean front) {
            super(ctx);
            this.scene = scene;
            this.front = front;
            this.caseH = caseH;
            this.bleed = UiKit.pxf(64);
            setTranslationZ(front ? FRONT_Z : BACK_Z);
            setFocusable(false);
            setClickable(false);
        }

        private static android.graphics.Bitmap velvetCache;

        private android.graphics.Bitmap velvet() {
            if (velvetCache == null) velvetCache = com.cinetv.launcher.service.PosterCache.decodeAsset(getContext(), "themes/velvet.jpg", 360, 720);
            return velvetCache;
        }

        /** Where the cases beside the focused one stand: their bottom edge. */
        private float floor() {
            return getHeight() / 2f + UiKit.pxf(12) + caseH * 0.86f / 2f;
        }

        @Override
        protected void onDraw(Canvas c) {
            if ("cinema".equals(scene)) curtains(c);
            else if (front) shelfFront(c);
            else shelfBack(c);
        }

        // ---- cinema ---------------------------------------------------------------------------------

        private void curtains(Canvas c) {
            int w = getWidth(), h = getHeight();
            float panel = w * 0.13f + bleed;
            for (int side = 0; side < 2; side++) {
                c.save();
                if (side == 1) {
                    c.scale(-1, 1, w / 2f, 0);
                }
                float x0 = -bleed;
                // the inner edge is gathered by a tie-back: it swings out towards the floor
                Path drape = new Path();
                drape.moveTo(x0, -UiKit.pxf(40));
                drape.lineTo(x0 + panel, -UiKit.pxf(40));
                drape.cubicTo(x0 + panel * 0.92f, h * 0.35f, x0 + panel * 0.55f, h * 0.55f, x0 + panel * 0.62f, h * 0.62f);
                drape.cubicTo(x0 + panel * 0.72f, h * 0.75f, x0 + panel * 1.05f, h * 0.92f, x0 + panel * 1.1f, h + UiKit.pxf(30));
                drape.lineTo(x0, h + UiKit.pxf(30));
                drape.close();
                c.save();
                c.clipPath(drape);
                android.graphics.Bitmap velvet = velvet();
                if (velvet != null) {
                    // the hall's own curtain, from its photo; mirrored so its folds never show a seam
                    android.graphics.BitmapShader bs = new android.graphics.BitmapShader(velvet, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
                    android.graphics.Matrix m = new android.graphics.Matrix();
                    float sc = (h + UiKit.pxf(80)) / velvet.getHeight();
                    m.setScale(sc * 0.8f, sc);
                    m.postTranslate(x0, -UiKit.pxf(40));
                    bs.setLocalMatrix(m);
                    p.setShader(bs);
                    c.drawRect(x0, -UiKit.pxf(40), x0 + panel * 1.15f, h + UiKit.pxf(40), p);
                    // the drape gathers towards the tie-back: deeper folds there
                    p.setShader(new LinearGradient(0, h * 0.4f, 0, h * 0.62f, Color.TRANSPARENT, Color.argb(90, 20, 0, 2), Shader.TileMode.MIRROR));
                    c.drawRect(x0, h * 0.4f, x0 + panel * 1.15f, h * 0.84f, p);
                } else {
                    int folds = 9;
                    float fw = (panel * 1.12f) / folds;
                    for (int i = 0; i < folds; i++) {
                        float fx = x0 + i * fw;
                        p.setShader(new LinearGradient(fx, 0, fx + fw, 0,
                                new int[]{Color.parseColor("#2A0306"), Color.parseColor("#7A0A12"), Color.parseColor("#B3172A"),
                                        Color.parseColor("#6E0810"), Color.parseColor("#2A0306")},
                                new float[]{0f, 0.3f, 0.48f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                        c.drawRect(fx, -UiKit.pxf(40), fx + fw + 1, h + UiKit.pxf(40), p);
                    }
                }
                // light from the screen falls on the inner folds, the outer ones sink into the dark
                p.setShader(new LinearGradient(x0, 0, x0 + panel, 0, Color.argb(170, 0, 0, 0), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP));
                c.drawRect(x0, -UiKit.pxf(40), x0 + panel * 1.2f, h + UiKit.pxf(40), p);
                p.setShader(new LinearGradient(0, 0, 0, h, Color.argb(60, 255, 210, 170), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP));
                c.drawRect(x0 + panel * 0.5f, 0, x0 + panel * 1.2f, h * 0.5f, p);
                c.restore();
                // the edge's soft shadow onto the cases behind it
                p.setShader(new LinearGradient(x0 + panel * 0.62f, 0, x0 + panel * 0.62f + UiKit.pxf(60), 0,
                        Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(x0 + panel * 0.62f, h * 0.45f, x0 + panel * 0.62f + UiKit.pxf(60), h * 0.8f, p);
                // gold tie-back cord with its tassel
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(UiKit.pxf(7));
                p.setColor(Color.parseColor("#C8963E"));
                Path cord = new Path();
                cord.moveTo(x0, h * 0.6f);
                cord.quadTo(x0 + panel * 0.35f, h * 0.67f, x0 + panel * 0.63f, h * 0.61f);
                c.drawPath(cord, p);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(x0 + panel * 0.63f, h * 0.61f, UiKit.pxf(16),
                        Color.parseColor("#F5D58A"), Color.parseColor("#8A6220"), Shader.TileMode.CLAMP));
                c.drawCircle(x0 + panel * 0.63f, h * 0.61f, UiKit.pxf(14), p);
                p.setShader(new LinearGradient(0, h * 0.62f, 0, h * 0.74f, Color.parseColor("#E2B45A"), Color.parseColor("#7A5418"), Shader.TileMode.CLAMP));
                Path tassel = new Path();
                tassel.moveTo(x0 + panel * 0.63f - UiKit.pxf(8), h * 0.625f);
                tassel.lineTo(x0 + panel * 0.63f + UiKit.pxf(8), h * 0.625f);
                tassel.lineTo(x0 + panel * 0.63f + UiKit.pxf(16), h * 0.74f);
                tassel.lineTo(x0 + panel * 0.63f - UiKit.pxf(16), h * 0.74f);
                tassel.close();
                c.drawPath(tassel, p);
                p.setShader(null);
                c.restore();
            }
        }

        // ---- dvd store ------------------------------------------------------------------------------

        private void shelfBack(Canvas c) {
            int w = getWidth(), h = getHeight();
            float floor = floor();
            float top = floor - caseH * 1.05f;
            RectF cavity = new RectF(-bleed, top, w + bleed, floor);
            // the shelf's white back panel, lit from the ceiling, greyer where the shelf above shades it
            p.setShader(new LinearGradient(0, top, 0, floor, new int[]{Color.parseColor("#8E9094"), Color.parseColor("#C9CACC"), Color.parseColor("#B4B5B8")},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(cavity, p);
            p.setShader(new LinearGradient(0, top, 0, top + caseH * 0.35f, Color.argb(170, 20, 22, 26), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, top, w + bleed, top + caseH * 0.35f, p);
            // the board the cases stand on, seen from slightly above
            p.setShader(new LinearGradient(0, floor - UiKit.pxf(22), 0, floor, Color.parseColor("#D9DADC"), Color.parseColor("#F4F4F3"), Shader.TileMode.CLAMP));
            c.drawRect(-bleed, floor - UiKit.pxf(22), w + bleed, floor, p);
            // contact shadow where the cases meet the board
            p.setShader(new LinearGradient(0, floor - UiKit.pxf(30), 0, floor - UiKit.pxf(14), Color.TRANSPARENT, Color.argb(90, 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(-bleed, floor - UiKit.pxf(30), w + bleed, floor - UiKit.pxf(14), p);
            p.setShader(null);
        }

        private void shelfFront(Canvas c) {
            int w = getWidth();
            float floor = floor();
            float lip = UiKit.pxf(30);
            float y = floor - UiKit.pxf(14);
            // the front edge of the white board hides the feet of the cases standing on it
            p.setShader(new LinearGradient(0, y, 0, y + lip, new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#E6E6E4"), Color.parseColor("#B9BABC")},
                    new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y, w + bleed, y + lip, p);
            p.setShader(null);
            p.setColor(Color.argb(200, 255, 255, 255));
            c.drawRect(-bleed, y, w + bleed, y + UiKit.pxf(2), p);
            // its shadow on whatever is below
            p.setShader(new LinearGradient(0, y + lip, 0, y + lip + UiKit.pxf(40), Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y + lip, w + bleed, y + lip + UiKit.pxf(40), p);
            // white uprights at both ends, in front of the outermost cases
            float post = UiKit.pxf(26), top = floor - caseH * 1.05f;
            for (float x : new float[]{-bleed, w + bleed - post}) {
                p.setShader(new LinearGradient(x, 0, x + post, 0, new int[]{Color.parseColor("#A9AAAD"), Color.parseColor("#F2F2F1"), Color.parseColor("#C4C5C7")},
                        null, Shader.TileMode.CLAMP));
                c.drawRect(x, top - UiKit.pxf(60), x + post, y + lip, p);
            }
            p.setShader(null);
        }
    }
}
