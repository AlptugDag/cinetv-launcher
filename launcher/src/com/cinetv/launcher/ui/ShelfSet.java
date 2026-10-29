package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.view.View;

import com.cinetv.launcher.service.Prefs;

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

    static final class Layer extends View {
        private final String scene;
        private final int caseW;
        private final float caseH;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] loc = new int[2];
        private boolean spatial;

        private float[] ptsX = new float[32];
        private float[] ptsY = new float[32];
        private int ptCount = 0;

        private final Path deckPath = new Path();
        private final Path rimPath = new Path();
        private final Path fasciaPath = new Path();
        private final Path moldingPath = new Path();
        private final Path dropShadowPath = new Path();
        private final Path contactShadowPath = new Path();

        private float cachedW = -1, cachedH = -1, cachedLb = -1, cachedRb = -1, cachedDeckD = -1, cachedNosingH = -1;
        private int cachedPtCount = -1;

        Layer(Context ctx, String scene, int caseW, float caseH) {
            super(ctx);
            this.scene = scene;
            this.caseW = caseW;
            this.caseH = caseH;
            this.spatial = Prefs.spatial3d(ctx);
            setTranslationZ(BACK_Z);
            setFocusable(false);
            setClickable(false);
            setLayerType(LAYER_TYPE_NONE, null);
        }

        public void setSpatial(boolean on) {
            if (this.spatial != on) {
                this.spatial = on;
                cachedW = -1;
                invalidate();
            }
        }

        public void setCurvePoints(float[] xs, float[] ys, int count) {
            if (count < 2) return;
            if (ptsX.length < count) {
                ptsX = new float[count + 8];
                ptsY = new float[count + 8];
            }
            System.arraycopy(xs, 0, ptsX, 0, count);
            System.arraycopy(ys, 0, ptsY, 0, count);
            this.ptCount = count;
            cachedW = -1; // force path rebuild
            invalidate();
        }

        private float leftBleed() {
            getLocationOnScreen(loc);
            return Math.max(UiKit.pxf(300), (float) Math.max(0, loc[0]) + UiKit.pxf(160));
        }

        private float rightBleed() {
            getLocationOnScreen(loc);
            float screenW = getResources().getDisplayMetrics().widthPixels;
            float r = screenW - (loc[0] + getWidth());
            return Math.max(UiKit.pxf(300), (float) Math.max(0, r) + UiKit.pxf(160));
        }

        private final float[] tmpDeckBackY = new float[64];
        private final float[] tmpNosingBotY = new float[64];
        private final float[] tmpDropShadowY = new float[64];
        private final float[] tmpContactShadowY = new float[64];
        private final float[] tmpMoldingY = new float[64];

        private void buildSmoothCurveForward(Path path, float[] xs, float[] ys, int count) {
            if (count < 2) return;
            path.moveTo(xs[0], ys[0]);
            for (int i = 0; i < count - 1; i++) {
                float p0x = i > 0 ? xs[i - 1] : xs[0] - (xs[1] - xs[0]);
                float p0y = i > 0 ? ys[i - 1] : ys[0] - (ys[1] - ys[0]);
                float p1x = xs[i];
                float p1y = ys[i];
                float p2x = xs[i + 1];
                float p2y = ys[i + 1];
                float p3x = i < count - 2 ? xs[i + 2] : xs[count - 1] + (xs[count - 1] - xs[count - 2]);
                float p3y = i < count - 2 ? ys[i + 2] : ys[count - 1] + (ys[count - 1] - ys[count - 2]);

                float cp1x = p1x + (p2x - p0x) / 6f;
                float cp1y = p1y + (p2y - p0y) / 6f;
                float cp2x = p2x - (p3x - p1x) / 6f;
                float cp2y = p2y - (p3y - p1y) / 6f;
                path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y);
            }
        }

        private void buildSmoothCurveReverse(Path path, float[] xs, float[] ys, int count) {
            if (count < 2) return;
            for (int i = count - 1; i > 0; i--) {
                float p0x = i < count - 1 ? xs[i + 1] : xs[count - 1] + (xs[count - 1] - xs[count - 2]);
                float p0y = i < count - 1 ? ys[i + 1] : ys[count - 1] + (ys[count - 1] - ys[count - 2]);
                float p1x = xs[i];
                float p1y = ys[i];
                float p2x = xs[i - 1];
                float p2y = ys[i - 1];
                float p3x = i > 1 ? xs[i - 2] : xs[0] - (xs[1] - xs[0]);
                float p3y = i > 1 ? ys[i - 2] : ys[0] - (ys[1] - ys[0]);

                float cp1x = p1x + (p2x - p0x) / 6f;
                float cp1y = p1y + (p2y - p0y) / 6f;
                float cp2x = p2x - (p3x - p1x) / 6f;
                float cp2y = p2y - (p3y - p1y) / 6f;
                path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y);
            }
        }

        private void ensurePaths(float w, float h, float lb, float rb, float deckDepth, float nosingH) {
            if (w == cachedW && h == cachedH && lb == cachedLb && rb == cachedRb
                    && deckDepth == cachedDeckD && nosingH == cachedNosingH
                    && ptCount == cachedPtCount) {
                return;
            }
            cachedW = w; cachedH = h; cachedLb = lb; cachedRb = rb;
            cachedDeckD = deckDepth; cachedNosingH = nosingH;
            cachedPtCount = ptCount;

            float shadowH = UiKit.pxf(24);
            float contactH = UiKit.pxf(8);

            deckPath.reset();
            rimPath.reset();
            fasciaPath.reset();
            moldingPath.reset();
            dropShadowPath.reset();
            contactShadowPath.reset();

            if (ptCount < 2) {
                // Fallback before cases are laid out
                float fl = h * 0.78f;
                float startX = -lb, endX = w + rb;
                deckPath.addRect(startX, fl - deckDepth, endX, fl, Path.Direction.CW);
                rimPath.moveTo(startX, fl); rimPath.lineTo(endX, fl);
                fasciaPath.addRect(startX, fl, endX, fl + nosingH, Path.Direction.CW);
                moldingPath.moveTo(startX, fl + nosingH - UiKit.pxf(1.5f)); moldingPath.lineTo(endX, fl + nosingH - UiKit.pxf(1.5f));
                dropShadowPath.addRect(startX, fl + nosingH, endX, fl + nosingH + shadowH, Path.Direction.CW);
                contactShadowPath.addRect(startX, fl - contactH, endX, fl, Path.Direction.CW);
                return;
            }

            // True 3D perspective foreshortening across the shelf:
            // Center is closest to camera: wider deck surface (up to 60px) and taller front nosing (24px).
            // Receding wings: narrower deck (tapers to 22px) and thinner nosing (tapers to 13px).
            float maxDist = Math.max(1f, w * 0.5f + Math.max(lb, rb));
            for (int i = 0; i < ptCount; i++) {
                float dX = Math.abs(ptsX[i] - w * 0.5f);
                float norm = Math.min(1f, dX / maxDist);
                float persp = 1.0f - 0.58f * (norm * norm);

                float curDeckD = deckDepth * (0.38f + 0.62f * persp);
                float curNosingH = nosingH * (0.48f + 0.52f * persp);
                float curShadowH = shadowH * (0.45f + 0.55f * persp);
                float curContactH = contactH * (0.40f + 0.60f * persp);

                tmpDeckBackY[i] = ptsY[i] - curDeckD;
                tmpNosingBotY[i] = ptsY[i] + curNosingH;
                tmpMoldingY[i] = ptsY[i] + curNosingH - UiKit.pxf(1.5f);
                tmpDropShadowY[i] = tmpNosingBotY[i] + curShadowH;
                tmpContactShadowY[i] = ptsY[i] - curContactH;
            }

            // 1. Deck polygon: continuous, smooth Catmull-Rom cubic Bezier curve (no flat lines or sharp corners)
            deckPath.moveTo(ptsX[0], tmpDeckBackY[0]);
            buildSmoothCurveForward(deckPath, ptsX, tmpDeckBackY, ptCount);
            deckPath.lineTo(ptsX[ptCount - 1], ptsY[ptCount - 1]);
            buildSmoothCurveReverse(deckPath, ptsX, ptsY, ptCount);
            deckPath.close();

            // 2. Rim catch line: passing exactly under every single DVD base with continuous oval roundness
            rimPath.moveTo(ptsX[0], ptsY[0]);
            buildSmoothCurveForward(rimPath, ptsX, ptsY, ptCount);

            // 3. Fascia nosing: smooth forward along rim, reverse along nosing bottom
            fasciaPath.moveTo(ptsX[0], ptsY[0]);
            buildSmoothCurveForward(fasciaPath, ptsX, ptsY, ptCount);
            fasciaPath.lineTo(ptsX[ptCount - 1], tmpNosingBotY[ptCount - 1]);
            buildSmoothCurveReverse(fasciaPath, ptsX, tmpNosingBotY, ptCount);
            fasciaPath.close();

            // 4. Molding line
            moldingPath.moveTo(ptsX[0], tmpMoldingY[0]);
            buildSmoothCurveForward(moldingPath, ptsX, tmpMoldingY, ptCount);

            // 5. Drop shadow polygon
            dropShadowPath.moveTo(ptsX[0], tmpNosingBotY[0]);
            buildSmoothCurveForward(dropShadowPath, ptsX, tmpNosingBotY, ptCount);
            dropShadowPath.lineTo(ptsX[ptCount - 1], tmpDropShadowY[ptCount - 1]);
            buildSmoothCurveReverse(dropShadowPath, ptsX, tmpDropShadowY, ptCount);
            dropShadowPath.close();

            // 6. Contact shadow under DVD cases
            contactShadowPath.moveTo(ptsX[0], tmpContactShadowY[0]);
            buildSmoothCurveForward(contactShadowPath, ptsX, tmpContactShadowY, ptCount);
            contactShadowPath.lineTo(ptsX[ptCount - 1], ptsY[ptCount - 1]);
            buildSmoothCurveReverse(contactShadowPath, ptsX, ptsY, ptCount);
            contactShadowPath.close();
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

        private void renderCurvedShelf(Canvas c, float deckDepth, float nosingH,
                                       int[] deckColors, float[] deckPos,
                                       int rimColor, float rimWidth,
                                       int[] faceColors, float[] facePos,
                                       int moldingColor,
                                       int spotColor,
                                       int contactShadowColor) {
            int w = getWidth(), h = getHeight();
            float lb = leftBleed(), rb = rightBleed();
            ensurePaths(w, h, lb, rb, deckDepth, nosingH);

            float minFloor = ptCount > 0 ? ptsY[0] : h * 0.78f;
            float maxFloor = ptCount > 0 ? ptsY[0] : h * 0.78f;
            for (int i = 1; i < ptCount; i++) {
                if (ptsY[i] < minFloor) minFloor = ptsY[i];
                if (ptsY[i] > maxFloor) maxFloor = ptsY[i];
            }

            float startX = -lb, endX = w + rb;

            // Horizontal lighting falloff mask:
            // Just as the DVDs fade from 100% illumination at the center down to 45% moody shadow at the wings,
            // the shelf deck, rim, and fascia share the exact same ambient room light falloff.
            int edgeDim = Color.argb(255, 115, 115, 120);
            int midDim = Color.argb(255, 210, 210, 215);
            int centerBright = Color.WHITE;
            Shader hDimMask = new LinearGradient(
                    startX, 0, endX, 0,
                    new int[]{edgeDim, midDim, centerBright, midDim, edgeDim},
                    new float[]{0f, 0.28f, 0.5f, 0.72f, 1f},
                    Shader.TileMode.CLAMP);

            // 1. Deck: theme vertical gradient multiplied with horizontal ambient lighting falloff
            Shader deckV = new LinearGradient(0, minFloor - deckDepth, 0, maxFloor,
                    deckColors, deckPos, Shader.TileMode.CLAMP);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new ComposeShader(deckV, hDimMask, PorterDuff.Mode.MULTIPLY));
            c.drawPath(deckPath, p);

            // 2. Solid front rim catch line: gleaming bright in center, smoothly burnishing into dark metallic at wings
            int rimDim = Color.argb(255,
                    (int) (Color.red(rimColor) * 0.42f),
                    (int) (Color.green(rimColor) * 0.42f),
                    (int) (Color.blue(rimColor) * 0.42f));
            Shader rimShader = new LinearGradient(
                    startX, 0, endX, 0,
                    new int[]{rimDim, rimColor, rimDim},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(rimWidth);
            p.setShader(rimShader);
            c.drawPath(rimPath, p);

            // 3. Fascia nosing multiplied with horizontal falloff
            Shader faceV = new LinearGradient(0, minFloor, 0, maxFloor + nosingH,
                    faceColors, facePos, Shader.TileMode.CLAMP);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new ComposeShader(faceV, hDimMask, PorterDuff.Mode.MULTIPLY));
            c.drawPath(fasciaPath, p);

            // 4. Molding line (if present)
            if (moldingColor != 0) {
                int moldDim = Color.argb(255,
                        (int) (Color.red(moldingColor) * 0.42f),
                        (int) (Color.green(moldingColor) * 0.42f),
                        (int) (Color.blue(moldingColor) * 0.42f));
                p.setShader(new LinearGradient(
                        startX, 0, endX, 0,
                        new int[]{moldDim, moldingColor, moldDim},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(UiKit.pxf(1.5f));
                c.drawPath(moldingPath, p);
            }

            // 5. Deep architectural drop shadow below shelf
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, minFloor + nosingH, 0, maxFloor + nosingH + UiKit.pxf(24),
                    Color.argb(190, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawPath(dropShadowPath, p);

            // 6. Contact shadow under DVD cases
            if (contactShadowColor != 0) {
                p.setShader(new LinearGradient(0, minFloor - UiKit.pxf(8), 0, maxFloor,
                        Color.TRANSPARENT, contactShadowColor, Shader.TileMode.CLAMP));
                c.drawPath(contactShadowPath, p);
            }

            // 7. Spotlight on center stage
            p.setShader(null);
            drawCenterSpotlight(c, w, h, deckDepth, spotColor);
        }

        private void drawCenterSpotlight(Canvas c, int w, int h, float deckDepth, int spotColor) {
            float cx = w * 0.5f;
            float flCenter = ptCount > 0 ? ptsY[ptCount / 2] : h * 0.78f;
            float spotR = UiKit.pxf(320);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new android.graphics.RadialGradient(cx, flCenter - UiKit.pxf(14), spotR,
                    spotColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.save();
            c.clipPath(deckPath);
            c.drawRect(cx - spotR, flCenter - deckDepth * 1.5f, cx + spotR, flCenter + UiKit.pxf(10), p);
            c.restore();
            p.setShader(null);
        }

        private void cinemaShelf(Canvas c) {
            renderCurvedShelf(c, UiKit.pxf(48), UiKit.pxf(22),
                    new int[]{Color.rgb(18, 10, 8), Color.rgb(36, 18, 14), Color.rgb(44, 22, 16)},
                    new float[]{0f, 0.7f, 1f},
                    Color.rgb(228, 180, 52), UiKit.pxf(2.4f),
                    new int[]{Color.rgb(32, 16, 12), Color.rgb(22, 11, 8), Color.rgb(14, 7, 5)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(140, 105, 30),
                    Color.argb(42, 245, 194, 107),
                    Color.argb(110, 10, 4, 3));
        }

        private void redCarpetShelf(Canvas c) {
            renderCurvedShelf(c, UiKit.pxf(48), UiKit.pxf(22),
                    new int[]{Color.rgb(128, 10, 22), Color.rgb(175, 14, 30), Color.rgb(195, 18, 36)},
                    new float[]{0f, 0.7f, 1f},
                    Color.rgb(255, 215, 64), UiKit.pxf(2.4f),
                    new int[]{Color.rgb(36, 6, 12), Color.rgb(22, 4, 8), Color.rgb(14, 2, 5)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(180, 145, 40),
                    Color.argb(46, 255, 229, 127),
                    Color.argb(100, 30, 2, 6));
        }

        private void closetShelf(Canvas c) {
            renderCurvedShelf(c, UiKit.pxf(36), UiKit.pxf(14),
                    new int[]{Color.rgb(38, 40, 44), Color.rgb(58, 60, 66), Color.rgb(74, 76, 82)},
                    new float[]{0f, 0.6f, 1f},
                    Color.rgb(132, 135, 142), UiKit.pxf(2.0f),
                    new int[]{Color.rgb(82, 84, 90), Color.rgb(64, 66, 72), Color.rgb(46, 48, 52)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(36, 38, 42),
                    Color.argb(30, 245, 245, 244),
                    Color.argb(95, 10, 10, 12));
        }

        private void themedShelf(Canvas c) {
            int deckTop, deckBot, rimCol, faceTop, faceBot, spotCol;
            if ("metal".equals(scene)) {
                deckTop = Color.rgb(42, 48, 58);
                deckBot = Color.rgb(68, 78, 92);
                rimCol = Color.rgb(226, 232, 240); // laser-etched silver
                faceTop = Color.rgb(36, 40, 48);
                faceBot = Color.rgb(20, 24, 30);
                spotCol = Color.argb(40, 226, 232, 240);
            } else if ("glass".equals(scene)) {
                deckTop = Color.rgb(14, 22, 38);
                deckBot = Color.rgb(26, 40, 64);
                rimCol = Color.rgb(56, 189, 248); // luminous sapphire cyan
                faceTop = Color.rgb(18, 28, 46);
                faceBot = Color.rgb(10, 16, 28);
                spotCol = Color.argb(44, 125, 211, 252);
            } else if ("neon".equals(scene)) {
                deckTop = Color.rgb(18, 12, 30);
                deckBot = Color.rgb(32, 16, 48);
                rimCol = Color.rgb(244, 114, 182); // electric synthwave pink
                faceTop = Color.rgb(22, 12, 34);
                faceBot = Color.rgb(12, 8, 20);
                spotCol = Color.argb(48, 244, 114, 182);
            } else {
                deckTop = Color.rgb(26, 28, 36);
                deckBot = Color.rgb(40, 44, 54);
                rimCol = Color.rgb(210, 220, 235); // solid platinum
                faceTop = Color.rgb(28, 30, 38);
                faceBot = Color.rgb(18, 20, 26);
                spotCol = Color.argb(38, 56, 189, 248);
            }

            renderCurvedShelf(c, UiKit.pxf(48), UiKit.pxf(22),
                    new int[]{deckTop, deckBot}, null,
                    rimCol, UiKit.pxf(2.4f),
                    new int[]{faceTop, faceBot}, null,
                    0,
                    spotCol,
                    Color.argb(100, 0, 0, 0));
        }
    }
}
