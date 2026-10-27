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
            if ("cinema".equals(scene)) cinemaStage(c);
            else if (front) shelfFront(c);
            else shelfBack(c);
        }

        // ---- cinema ---------------------------------------------------------------------------------

        private void cinemaStage(Canvas c) {
            int w = getWidth(), h = getHeight();
            float fl = floor();

            // Seamless warm stage reflection & contact shadow under the cases
            float shadowH = UiKit.pxf(28);
            p.setShader(new LinearGradient(0, fl - shadowH * 0.4f, 0, fl + shadowH,
                    new int[]{Color.TRANSPARENT, Color.argb(130, 18, 2, 5), Color.argb(45, 245, 194, 107), Color.TRANSPARENT},
                    new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, fl - shadowH * 0.4f, w + bleed, fl + shadowH, p);
            p.setShader(null);

            // Luxurious, soft atmospheric velvet side framing (seamlessly feathered into the room)
            float panel = w * 0.12f + bleed;
            for (int side = 0; side < 2; side++) {
                c.save();
                if (side == 1) {
                    c.scale(-1, 1, w / 2f, 0);
                }
                float x0 = -bleed;
                // Soft velvet drape path with natural organic drape curve
                Path drape = new Path();
                drape.moveTo(x0, -UiKit.pxf(40));
                drape.lineTo(x0 + panel, -UiKit.pxf(40));
                drape.cubicTo(x0 + panel * 0.88f, h * 0.32f, x0 + panel * 0.52f, h * 0.54f, x0 + panel * 0.58f, h * 0.62f);
                drape.cubicTo(x0 + panel * 0.68f, h * 0.74f, x0 + panel * 0.98f, h * 0.90f, x0 + panel * 1.05f, h + UiKit.pxf(30));
                drape.lineTo(x0, h + UiKit.pxf(30));
                drape.close();

                c.save();
                c.clipPath(drape);
                android.graphics.Bitmap velvet = velvet();
                if (velvet != null) {
                    android.graphics.BitmapShader bs = new android.graphics.BitmapShader(velvet, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
                    android.graphics.Matrix m = new android.graphics.Matrix();
                    float sc = (h + UiKit.pxf(80)) / (float) velvet.getHeight();
                    m.setScale(sc * 0.85f, sc);
                    m.postTranslate(x0, -UiKit.pxf(40));
                    bs.setLocalMatrix(m);
                    p.setShader(bs);
                    c.drawRect(x0, -UiKit.pxf(40), x0 + panel * 1.15f, h + UiKit.pxf(40), p);
                } else {
                    int folds = 8;
                    float fw = (panel * 1.1f) / folds;
                    for (int i = 0; i < folds; i++) {
                        float fx = x0 + i * fw;
                        p.setShader(new LinearGradient(fx, 0, fx + fw, 0,
                                new int[]{Color.parseColor("#220204"), Color.parseColor("#65080E"), Color.parseColor("#9B1220"),
                                        Color.parseColor("#5A060C"), Color.parseColor("#220204")},
                                new float[]{0f, 0.28f, 0.48f, 0.72f, 1f}, Shader.TileMode.CLAMP));
                        c.drawRect(fx, -UiKit.pxf(40), fx + fw + 1, h + UiKit.pxf(40), p);
                    }
                }
                // Seamless soft feathering from the edge into the hall
                p.setShader(new LinearGradient(x0 + panel * 0.4f, 0, x0 + panel * 1.08f, 0,
                        Color.TRANSPARENT, Color.argb(180, 10, 1, 2), Shader.TileMode.CLAMP));
                c.drawRect(x0, -UiKit.pxf(40), x0 + panel * 1.15f, h + UiKit.pxf(40), p);

                // Subtle warm hall lighting glow across the inner fold
                p.setShader(new LinearGradient(x0 + panel * 0.3f, 0, x0 + panel * 0.9f, 0,
                        Color.argb(40, 245, 194, 107), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(x0, 0, x0 + panel * 1.1f, h * 0.65f, p);
                c.restore();

                // Soft shadow from the curtain onto the shelf behind
                p.setShader(new LinearGradient(x0 + panel * 0.58f, 0, x0 + panel * 0.58f + UiKit.pxf(55), 0,
                        Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawRect(x0 + panel * 0.58f, h * 0.45f, x0 + panel * 0.58f + UiKit.pxf(55), h * 0.82f, p);
                p.setShader(null);
                c.restore();
            }
        }

        // ---- closet (Film Dolabı) -------------------------------------------------------------------

        private void shelfBack(Canvas c) {
            int w = getWidth(), h = getHeight();
            float fl = floor();
            float top = fl - caseH * 1.02f;

            // Ambient occlusion shadow behind the cases: keeps closet wallpaper visible while grounding cases
            p.setShader(new LinearGradient(0, top, 0, fl,
                    new int[]{Color.argb(120, 10, 11, 14), Color.argb(40, 14, 16, 20), Color.argb(95, 8, 9, 12)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, top, w + bleed, fl, p);

            // Realistic contact shadow directly where the cases sit on the shelf
            p.setShader(new LinearGradient(0, fl - UiKit.pxf(24), 0, fl,
                    Color.TRANSPARENT, Color.argb(140, 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(-bleed, fl - UiKit.pxf(24), w + bleed, fl, p);
            p.setShader(null);
        }

        private void shelfFront(Canvas c) {
            int w = getWidth();
            float fl = floor();
            float lip = UiKit.pxf(20);
            float y = fl - UiKit.pxf(8);

            // The shelf's front laminate bevel: soft, natural white/ivory shelf edge matching closet's lighting
            p.setShader(new LinearGradient(0, y, 0, y + lip,
                    new int[]{Color.argb(220, 245, 245, 244), Color.argb(190, 218, 220, 222), Color.argb(150, 160, 162, 165)},
                    new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y, w + bleed, y + lip, p);

            // Top highlight reflection along the shelf front rim
            p.setShader(null);
            p.setColor(Color.argb(160, 255, 255, 255));
            c.drawRect(-bleed, y, w + bleed, y + UiKit.pxf(1.5f), p);

            // Gentle ambient drop shadow below the shelf front
            p.setShader(new LinearGradient(0, y + lip, 0, y + lip + UiKit.pxf(26),
                    Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y + lip, w + bleed, y + lip + UiKit.pxf(26), p);
            p.setShader(null);
        }
    }
}
