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
        return "closet".equals(scene) || "cinema".equals(scene) || "redcarpet".equals(scene) ? new Layer(ctx, scene, caseW, caseH, true) : null;
    }

    /** The room's light on the cases: tailored cinematic lighting grade per theme. */
    static android.graphics.ColorMatrix grade(String scene) {
        if ("redcarpet".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    1.12f, 0.02f, 0f, 0, 10,
                    0.01f, 0.94f, 0.01f, 0, 3,
                    0f, 0.02f, 0.88f, 0, -2,
                    0, 0, 0, 1, 0});
        }
        if ("cinema".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    1.04f, 0.06f, 0f, 0, 8,
                    0.02f, 0.92f, 0.02f, 0, 6,
                    0f, 0.02f, 0.74f, 0, -8,
                    0, 0, 0, 1, 0});
        }
        if ("neon".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    1.08f, 0.02f, 0.04f, 0, 12,
                    0.01f, 0.92f, 0.02f, 0, -2,
                    0.04f, 0.02f, 1.12f, 0, 14,
                    0, 0, 0, 1, 0});
        }
        if ("metal".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    1.02f, 0.02f, 0.02f, 0, 2,
                    0.02f, 1.02f, 0.02f, 0, 2,
                    0.02f, 0.02f, 1.06f, 0, 6,
                    0, 0, 0, 1, 0});
        }
        if ("glass".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    0.96f, 0.02f, 0.02f, 0, 0,
                    0.01f, 0.98f, 0.03f, 0, 3,
                    0.02f, 0.04f, 1.08f, 0, 10,
                    0, 0, 0, 1, 0});
        }
        if ("closet".equals(scene)) {
            return new android.graphics.ColorMatrix(new float[]{
                    0.98f, 0.02f, 0.02f, 0, 3,
                    0.01f, 0.98f, 0.02f, 0, 4,
                    0.01f, 0.02f, 1.00f, 0, 4,
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

        private float leftBleed() {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            return loc[0] > 0 ? (float) loc[0] : UiKit.pxf(64);
        }

        private float rightBleed() {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            float screenW = getResources().getDisplayMetrics().widthPixels;
            float r = screenW - (loc[0] + getWidth());
            return r > 0 ? r : UiKit.pxf(64);
        }

        /** Where the cases beside the focused one stand: their bottom edge. */
        private float floor() {
            return getHeight() / 2f + UiKit.pxf(12) + caseH * 0.86f / 2f;
        }

        @Override
        protected void onDraw(Canvas c) {
            if ("redcarpet".equals(scene)) {
                redCarpetStage(c);
            } else if ("cinema".equals(scene)) {
                cinemaStage(c);
            } else if (front) {
                shelfFront(c);
            } else {
                shelfBack(c);
            }
        }

        // ---- cinema ---------------------------------------------------------------------------------

        private void cinemaStage(Canvas c) {
            int w = getWidth(), h = getHeight();
            float fl = floor();
            float lb = leftBleed();
            float rb = rightBleed();

            // Seamless dark stage contact shadow under the cases (no yellow tint)
            float shadowH = UiKit.pxf(26);
            p.setShader(new LinearGradient(0, fl - shadowH * 0.35f, 0, fl + shadowH,
                    new int[]{Color.TRANSPARENT, Color.argb(140, 12, 1, 3), Color.argb(50, 10, 1, 2), Color.TRANSPARENT},
                    new float[]{0f, 0.30f, 0.70f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - shadowH * 0.35f, w + rb, fl + shadowH, p);
            p.setShader(null);

            // Architectural theatrical proscenium velvet side framing pinned flush to screen borders
            float curtainVisible = UiKit.pxf(145);
            float topY = 0;
            float botY = h;

            // --- Left Curtain (Pinned to x = -lb) ---
            float lx0 = -lb;
            float lx1 = lx0 + lb + curtainVisible;
            drawProsceniumCurtain(c, lx0, lx1, topY, botY, false);

            // --- Right Curtain (Pinned to x = w + rb) ---
            float rx0 = w - curtainVisible;
            float rx1 = w + rb;
            drawProsceniumCurtain(c, rx0, rx1, topY, botY, true);
        }

        private void drawProsceniumCurtain(Canvas c, float x0, float x1, float topY, float botY, boolean isRight) {
            float curtainW = x1 - x0;
            // 1. Velvet base texture
            android.graphics.Bitmap velvet = velvet();
            if (velvet != null) {
                android.graphics.BitmapShader bs = new android.graphics.BitmapShader(velvet, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                android.graphics.Matrix m = new android.graphics.Matrix();
                float sc = (botY - topY) / (float) velvet.getHeight();
                m.setScale(sc, sc);
                m.postTranslate(x0, topY);
                bs.setLocalMatrix(m);
                p.setShader(bs);
                c.drawRect(x0, topY, x1, botY, p);
                p.setShader(null);
            }

            // 2. Realistic 3D cylindrical velvet flutes (vertical folds with deep crevice shadows)
            int folds = 4;
            float fw = curtainW / folds;
            for (int i = 0; i < folds; i++) {
                float fx = x0 + i * fw;
                p.setShader(new LinearGradient(fx, 0, fx + fw, 0,
                        new int[]{
                                Color.argb(235, 18, 2, 4),   // deep shadow crevice
                                Color.argb(120, 48, 5, 9),   // shaded rise
                                Color.argb(velvet != null ? 30 : 255, 142, 14, 27), // velvet highlight ridge
                                Color.argb(130, 48, 5, 9),   // receding slope
                                Color.argb(235, 18, 2, 4)    // crevice
                        },
                        new float[]{0f, 0.22f, 0.50f, 0.78f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(fx, topY, fx + fw + 0.5f, botY, p);
            }

            // 3. Crisp vertical piping edge along the inner curtain fold
            float pipeX = isRight ? x0 : x1;
            p.setShader(null);
            p.setColor(Color.argb(180, 168, 22, 35));
            c.drawRect(pipeX - UiKit.pxf(1.5f), topY, pipeX + UiKit.pxf(1.5f), botY, p);

            // 4. Soft ambient shadow cast from the curtain inward onto the stage and passing posters
            float shadowStart = isRight ? x0 : x1 - UiKit.pxf(8);
            float shadowEnd = isRight ? x0 - UiKit.pxf(48) : x1 + UiKit.pxf(48);
            p.setShader(new LinearGradient(shadowStart, 0, shadowEnd, 0,
                    new int[]{Color.argb(150, 8, 1, 2), Color.argb(60, 8, 1, 2), Color.TRANSPARENT},
                    new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(Math.min(shadowStart, shadowEnd), topY, Math.max(shadowStart, shadowEnd), botY, p);
            p.setShader(null);
        }

        // ---- redcarpet (Kırmızı Halı) ----------------------------------------------------------------

        private void redCarpetStage(Canvas c) {
            int w = getWidth(), h = getHeight();
            float fl = floor();
            float lb = leftBleed(), rb = rightBleed();

            // Seamless plush velvet contact shadow directly under the cases walking the red carpet
            float shadowH = UiKit.pxf(24);
            p.setShader(new LinearGradient(0, fl - shadowH * 0.35f, 0, fl + shadowH,
                    new int[]{Color.TRANSPARENT, Color.argb(175, 30, 2, 6), Color.argb(70, 20, 1, 3), Color.TRANSPARENT},
                    new float[]{0f, 0.28f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - shadowH * 0.35f, w + rb, fl + shadowH, p);
            p.setShader(null);

            // Subtle paparazzi starburst flash glints along the gala red carpet
            drawGlint(c, w * 0.20f, fl + UiKit.pxf(2), UiKit.pxf(6f));
            drawGlint(c, w * 0.80f, fl + UiKit.pxf(2), UiKit.pxf(5f));
        }

        private void drawGlint(Canvas c, float gx, float gy, float r) {
            p.setShader(null);
            p.setColor(Color.WHITE);
            c.drawCircle(gx, gy, r * 0.35f, p);
            p.setColor(Color.argb(210, 255, 245, 180));
            c.drawLine(gx - r * 1.4f, gy, gx + r * 1.4f, gy, p);
            c.drawLine(gx, gy - r * 1.4f, gx, gy + r * 1.4f, p);
        }

        // ---- closet (Film Dolabı) -------------------------------------------------------------------

        private void shelfBack(Canvas c) {
            int w = getWidth();
            float fl = floor();
            float top = fl - caseH * 1.05f;

            // 1. Deep ambient occlusion alcove shadow behind cases (deepening towards corners)
            p.setShader(new LinearGradient(0, top, 0, fl,
                    new int[]{Color.argb(140, 6, 7, 10), Color.argb(45, 12, 13, 17), Color.argb(120, 8, 9, 12)},
                    new float[]{0f, 0.40f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, top, w + bleed, fl, p);

            // 2. Lateral corner vignettes (shadows deepening where the back wall meets the side walls)
            p.setShader(new LinearGradient(-bleed, 0, w * 0.18f, 0,
                    Color.argb(125, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, top, w * 0.18f, fl, p);

            p.setShader(new LinearGradient(w + bleed, 0, w * 0.82f, 0,
                    Color.argb(125, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(w * 0.82f, top, w + bleed, fl, p);

            // 3. Dense physical contact crevice shadow where DVD spines rest on the shelf deck
            p.setShader(new LinearGradient(0, fl - UiKit.pxf(16), 0, fl,
                    Color.TRANSPARENT, Color.argb(200, 4, 4, 6), Shader.TileMode.CLAMP));
            c.drawRect(-bleed, fl - UiKit.pxf(16), w + bleed, fl, p);
            p.setShader(null);
        }

        private void shelfFront(Canvas c) {
            int w = getWidth();
            float fl = floor();

            // Perspective shelf plank thickness (architectural 28mm solid shelf nosing)
            float nosingH = UiKit.pxf(24);
            float y0 = fl - UiKit.pxf(4); // slight overlap with case bottoms
            float y1 = y0 + nosingH;

            // 1. Top Shelf Deck Plane (visible depth receding into the closet)
            p.setShader(new LinearGradient(0, y0, 0, y0 + UiKit.pxf(5),
                    new int[]{Color.argb(240, 248, 247, 244), Color.argb(220, 235, 233, 229)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y0, w + bleed, y0 + UiKit.pxf(5), p);

            // 2. Crisp specular highlight along the top front chamfer (warm ivory ceiling light catch)
            p.setShader(null);
            p.setColor(Color.argb(235, 255, 248, 240));
            c.drawRect(-bleed, y0 + UiKit.pxf(4.5f), w + bleed, y0 + UiKit.pxf(6f), p);

            // 3. Architectural Fascia Face (vertical bullnose/bevel profile with rich material depth)
            p.setShader(new LinearGradient(0, y0 + UiKit.pxf(6f), 0, y1,
                    new int[]{
                            Color.argb(245, 244, 243, 240), // upper satin catch
                            Color.argb(235, 226, 224, 220), // main ivory face
                            Color.argb(220, 204, 202, 198), // lower curvature transition
                            Color.argb(240, 168, 165, 160)  // bottom undercut shadow
                    },
                    new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y0 + UiKit.pxf(6f), w + bleed, y1, p);

            // 4. Subtle horizontal timber grain texture lines across the shelf face
            p.setShader(null);
            p.setColor(Color.argb(24, 0, 0, 0));
            float gY1 = y0 + UiKit.pxf(11f);
            float gY2 = y0 + UiKit.pxf(17f);
            c.drawRect(-bleed, gY1, w + bleed, gY1 + UiKit.pxf(1f), p);
            c.drawRect(-bleed, gY2, w + bleed, gY2 + UiKit.pxf(1f), p);

            // 5. Underside cove dark shadow line
            p.setColor(Color.argb(160, 40, 38, 35));
            c.drawRect(-bleed, y1 - UiKit.pxf(1.5f), w + bleed, y1, p);

            // 6. Dual-stage physical drop shadow below the shelf (creates massive floating 3D depth)
            // A) Core dense penumbra
            p.setShader(new LinearGradient(0, y1, 0, y1 + UiKit.pxf(10),
                    Color.argb(170, 0, 0, 0), Color.argb(40, 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y1, w + bleed, y1 + UiKit.pxf(10), p);

            // B) Soft ambient room spread
            p.setShader(new LinearGradient(0, y1 + UiKit.pxf(10), 0, y1 + UiKit.pxf(38),
                    Color.argb(40, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-bleed, y1 + UiKit.pxf(10), w + bleed, y1 + UiKit.pxf(38), p);
            p.setShader(null);
        }
    }
}
