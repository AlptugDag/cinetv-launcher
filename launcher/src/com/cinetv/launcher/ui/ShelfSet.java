package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * Solid, opaque furniture grounding the DVD cases for each room theme.
 * Completely opaque (no transparency); cases rest solidly on the shelf deck.
 * Renders entirely behind the cases (BACK_Z), so the shelf nosing never clips or passes in front of any DVD case.
 */
final class ShelfSet {
    static final float BACK_Z = -50f;

    private ShelfSet() {}

    static View back(Context ctx, String scene, int caseW, float caseH) {
        return new Layer(ctx, scene, caseW, caseH);
    }

    static View front(Context ctx, String scene, int caseW, float caseH) {
        // Shelf is entirely behind the cases so shelf nosing never passes over DVD covers or spines.
        return null;
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
        private final float caseH;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Layer(Context ctx, String scene, int caseW, float caseH) {
            super(ctx);
            this.scene = scene;
            this.caseH = caseH;
            setTranslationZ(BACK_Z);
            setFocusable(false);
            setClickable(false);
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
                redCarpetShelf(c);
            } else if ("cinema".equals(scene)) {
                cinemaShelf(c);
            } else if ("closet".equals(scene)) {
                closetShelf(c);
            } else {
                themedShelf(c);
            }
        }

        private void cinemaShelf(Canvas c) {
            int w = getWidth();
            float fl = floor();
            float lb = leftBleed();
            float rb = rightBleed();
            float deckDepth = UiKit.pxf(48);
            float nosingH = UiKit.pxf(22);

            // 1. Polished Cinema Stage Mahogany Parquet Deck (100% OPAQUE)
            p.setShader(new LinearGradient(0, fl - deckDepth, 0, fl,
                    new int[]{Color.rgb(18, 10, 8), Color.rgb(36, 18, 14), Color.rgb(44, 22, 16)},
                    new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - deckDepth, w + rb, fl, p);

            // 2. Solid warm brass front footlight rim catch (100% OPAQUE)
            p.setShader(null);
            p.setColor(Color.rgb(228, 180, 52));
            c.drawRect(-lb, fl - UiKit.pxf(2f), w + rb, fl, p);

            // 3. Solid dark mahogany front nosing / fascia (100% OPAQUE, sits beneath case foot line)
            p.setShader(new LinearGradient(0, fl, 0, fl + nosingH,
                    new int[]{Color.rgb(32, 16, 12), Color.rgb(22, 11, 8), Color.rgb(14, 7, 5)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl, w + rb, fl + nosingH, p);

            // 4. Subtle brass bottom molding line (100% OPAQUE)
            p.setShader(null);
            p.setColor(Color.rgb(140, 105, 30));
            c.drawRect(-lb, fl + nosingH - UiKit.pxf(1.5f), w + rb, fl + nosingH, p);

            // 5. Deep architectural drop shadow below the shelf
            p.setShader(new LinearGradient(0, fl + nosingH, 0, fl + nosingH + UiKit.pxf(24),
                    Color.argb(190, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl + nosingH, w + rb, fl + nosingH + UiKit.pxf(24), p);

            // 6. Contact shadow under DVD cases
            p.setShader(new LinearGradient(0, fl - UiKit.pxf(8), 0, fl,
                    Color.TRANSPARENT, Color.argb(110, 10, 4, 3), Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - UiKit.pxf(8), w + rb, fl, p);
            p.setShader(null);
        }

        private void redCarpetShelf(Canvas c) {
            int w = getWidth();
            float fl = floor();
            float lb = leftBleed();
            float rb = rightBleed();
            float deckDepth = UiKit.pxf(48);
            float nosingH = UiKit.pxf(22);

            // 1. Plush Gala Red Carpet Deck Plane (100% OPAQUE)
            p.setShader(new LinearGradient(0, fl - deckDepth, 0, fl,
                    new int[]{Color.rgb(128, 10, 22), Color.rgb(175, 14, 30), Color.rgb(195, 18, 36)},
                    new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - deckDepth, w + rb, fl, p);

            // 2. Solid gold braided trim at the front edge (100% OPAQUE)
            p.setShader(null);
            p.setColor(Color.rgb(255, 215, 64));
            c.drawRect(-lb, fl - UiKit.pxf(2f), w + rb, fl, p);

            // 3. Solid dark premiere stage front fascia (100% OPAQUE)
            p.setShader(new LinearGradient(0, fl, 0, fl + nosingH,
                    new int[]{Color.rgb(36, 6, 12), Color.rgb(22, 4, 8), Color.rgb(14, 2, 5)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl, w + rb, fl + nosingH, p);

            // 4. Gold edge line at nosing bottom
            p.setColor(Color.rgb(180, 145, 40));
            c.drawRect(-lb, fl + nosingH - UiKit.pxf(1.5f), w + rb, fl + nosingH, p);

            // 5. Drop shadow
            p.setShader(new LinearGradient(0, fl + nosingH, 0, fl + nosingH + UiKit.pxf(24),
                    Color.argb(190, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl + nosingH, w + rb, fl + nosingH + UiKit.pxf(24), p);

            // 6. Contact shadow under cases
            p.setShader(new LinearGradient(0, fl - UiKit.pxf(8), 0, fl,
                    Color.TRANSPARENT, Color.argb(100, 30, 2, 6), Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - UiKit.pxf(8), w + rb, fl, p);
            p.setShader(null);
        }

        private void closetShelf(Canvas c) {
            int w = getWidth();
            float fl = floor();
            float lb = leftBleed();
            float rb = rightBleed();
            float deckDepth = UiKit.pxf(24);
            float nosingH = UiKit.pxf(12);

            // 1. Muted, realistic warm grey shelf deck (harmonizes with closet wallpaper, not stark white!)
            p.setShader(new LinearGradient(0, fl - deckDepth, 0, fl,
                    new int[]{Color.rgb(42, 44, 48), Color.rgb(68, 70, 76), Color.rgb(88, 90, 96)},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - deckDepth, w + rb, fl, p);

            // 2. Soft pearl-grey front edge catch (no harsh glaring white!)
            p.setShader(null);
            p.setColor(Color.rgb(155, 158, 165));
            c.drawRect(-lb, fl - UiKit.pxf(1.5f), w + rb, fl, p);

            // 3. Muted satin fascia face matching the closet lighting
            p.setShader(new LinearGradient(0, fl, 0, fl + nosingH,
                    new int[]{Color.rgb(96, 98, 104), Color.rgb(74, 76, 82), Color.rgb(54, 56, 60)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl, w + rb, fl + nosingH, p);

            // 4. Subtle bottom groove
            p.setColor(Color.rgb(42, 44, 48));
            c.drawRect(-lb, fl + nosingH - UiKit.pxf(1f), w + rb, fl + nosingH, p);

            // 5. Natural soft ambient drop shadow onto the lower shelves
            p.setShader(new LinearGradient(0, fl + nosingH, 0, fl + nosingH + UiKit.pxf(16),
                    Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl + nosingH, w + rb, fl + nosingH + UiKit.pxf(16), p);

            // 6. Contact shadow under DVD cases
            p.setShader(new LinearGradient(0, fl - UiKit.pxf(6), 0, fl,
                    Color.TRANSPARENT, Color.argb(90, 10, 10, 12), Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - UiKit.pxf(6), w + rb, fl, p);
            p.setShader(null);
        }

        private void themedShelf(Canvas c) {
            int w = getWidth();
            float fl = floor();
            float lb = leftBleed();
            float rb = rightBleed();
            float deckDepth = UiKit.pxf(48);
            float nosingH = UiKit.pxf(22);

            int deckTop, deckBot, rimCol, faceTop, faceBot;
            if ("metal".equals(scene)) {
                deckTop = Color.rgb(42, 48, 58);
                deckBot = Color.rgb(68, 78, 92);
                rimCol = Color.rgb(226, 232, 240); // laser-etched silver
                faceTop = Color.rgb(36, 40, 48);
                faceBot = Color.rgb(20, 24, 30);
            } else if ("glass".equals(scene)) {
                deckTop = Color.rgb(14, 22, 38);
                deckBot = Color.rgb(26, 40, 64);
                rimCol = Color.rgb(56, 189, 248); // luminous sapphire cyan
                faceTop = Color.rgb(18, 28, 46);
                faceBot = Color.rgb(10, 16, 28);
            } else if ("neon".equals(scene)) {
                deckTop = Color.rgb(18, 12, 30);
                deckBot = Color.rgb(32, 16, 48);
                rimCol = Color.rgb(244, 114, 182); // electric synthwave pink
                faceTop = Color.rgb(22, 12, 34);
                faceBot = Color.rgb(12, 8, 20);
            } else {
                deckTop = Color.rgb(26, 28, 36);
                deckBot = Color.rgb(40, 44, 54);
                rimCol = Color.rgb(210, 220, 235); // solid platinum
                faceTop = Color.rgb(28, 30, 38);
                faceBot = Color.rgb(18, 20, 26);
            }

            // 1. Solid opaque top deck plane (100% OPAQUE)
            p.setShader(new LinearGradient(0, fl - deckDepth, 0, fl, deckTop, deckBot, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - deckDepth, w + rb, fl, p);

            // 2. Solid front rim catch line (100% OPAQUE)
            p.setShader(null);
            p.setColor(rimCol);
            c.drawRect(-lb, fl - UiKit.pxf(2f), w + rb, fl, p);

            // 3. Solid front fascia nosing (100% OPAQUE)
            p.setShader(new LinearGradient(0, fl, 0, fl + nosingH, faceTop, faceBot, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl, w + rb, fl + nosingH, p);

            // 4. Drop shadow
            p.setShader(new LinearGradient(0, fl + nosingH, 0, fl + nosingH + UiKit.pxf(24),
                    Color.argb(190, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl + nosingH, w + rb, fl + nosingH + UiKit.pxf(24), p);

            // 5. Contact shadow under cases
            p.setShader(new LinearGradient(0, fl - UiKit.pxf(8), 0, fl,
                    Color.TRANSPARENT, Color.argb(100, 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(-lb, fl - UiKit.pxf(8), w + rb, fl, p);
            p.setShader(null);
        }
    }
}
