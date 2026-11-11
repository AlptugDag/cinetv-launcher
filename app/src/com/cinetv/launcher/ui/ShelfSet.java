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
        private final Path wallShadowPath = new Path();
        private final Path rimPath = new Path();
        private final Path fasciaPath = new Path();
        private final Path moldingPath = new Path();
        private final Path undercutPath = new Path();
        private final Path dropShadowPath = new Path();
        private final Path contactShadowPath = new Path();
        private final Path grain1Path = new Path();
        private final Path grain2Path = new Path();
        private final Path grain3Path = new Path();
        private final Path refractionPath = new Path();
        private final Path neonTubePath = new Path();

        private float cachedW = -1, cachedH = -1, cachedLb = -1, cachedRb = -1, cachedDeckD = -1, cachedNosingH = -1;
        private int cachedPtCount = -1;

        Layer(Context ctx, String scene, int caseW, float caseH) {
            super(ctx);
            this.scene = scene;
            this.caseW = caseW;
            this.caseH = caseH;
            this.spatial = false;
            setTranslationZ(BACK_Z);
            setFocusable(false);
            setClickable(false);
            setLayerType(LAYER_TYPE_NONE, null);
        }

        public void setSpatial(boolean on) {
            if (this.spatial) {
                this.spatial = false;
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

        private final float[] tmpDeckBackY = new float[128];
        private final float[] tmpWallShadowY = new float[128];
        private final float[] tmpNosingBotY = new float[128];
        private final float[] tmpDropShadowY = new float[128];
        private final float[] tmpContactShadowY = new float[128];
        private final float[] tmpMoldingY = new float[128];
        private final float[] tmpGrain1Y = new float[128];
        private final float[] tmpGrain2Y = new float[128];
        private final float[] tmpGrain3Y = new float[128];
        private final float[] tmpRefractionY = new float[128];

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
            float wallShadowH = UiKit.pxf(10);

            deckPath.reset();
            wallShadowPath.reset();
            rimPath.reset();
            fasciaPath.reset();
            moldingPath.reset();
            undercutPath.reset();
            dropShadowPath.reset();
            contactShadowPath.reset();
            grain1Path.reset();
            grain2Path.reset();
            grain3Path.reset();
            refractionPath.reset();
            neonTubePath.reset();

            if (ptCount < 2) {
                float fl = h * 0.78f;
                float startX = -lb, endX = w + rb;
                wallShadowPath.addRect(startX, fl - deckDepth - wallShadowH, endX, fl - deckDepth, Path.Direction.CW);
                deckPath.addRect(startX, fl - deckDepth, endX, fl, Path.Direction.CW);
                rimPath.moveTo(startX, fl); rimPath.lineTo(endX, fl);
                fasciaPath.addRect(startX, fl, endX, fl + nosingH, Path.Direction.CW);
                moldingPath.moveTo(startX, fl + nosingH - UiKit.pxf(2.0f)); moldingPath.lineTo(endX, fl + nosingH - UiKit.pxf(2.0f));
                undercutPath.moveTo(startX, fl + nosingH); undercutPath.lineTo(endX, fl + nosingH);
                dropShadowPath.addRect(startX, fl + nosingH, endX, fl + nosingH + shadowH, Path.Direction.CW);
                contactShadowPath.addRect(startX, fl - contactH, endX, fl, Path.Direction.CW);
                grain1Path.moveTo(startX, fl - deckDepth * 0.72f); grain1Path.lineTo(endX, fl - deckDepth * 0.72f);
                grain2Path.moveTo(startX, fl - deckDepth * 0.46f); grain2Path.lineTo(endX, fl - deckDepth * 0.46f);
                grain3Path.moveTo(startX, fl - deckDepth * 0.22f); grain3Path.lineTo(endX, fl - deckDepth * 0.22f);
                refractionPath.moveTo(startX, fl - deckDepth * 0.10f); refractionPath.lineTo(endX, fl - deckDepth * 0.10f);
                neonTubePath.moveTo(startX, fl + nosingH + UiKit.pxf(1.0f)); neonTubePath.lineTo(endX, fl + nosingH + UiKit.pxf(1.0f));
                return;
            }

            float maxDist = Math.max(1f, w * 0.5f + Math.max(lb, rb));
            for (int i = 0; i < ptCount; i++) {
                float dX = Math.abs(ptsX[i] - w * 0.5f);
                float norm = Math.min(1f, dX / maxDist);
                float persp = 1.0f - 0.65f * (norm * norm);

                float curDeckD = deckDepth * (0.32f + 0.68f * persp);
                float curNosingH = nosingH * (0.42f + 0.58f * persp);
                float curShadowH = shadowH * (0.45f + 0.55f * persp);
                float curContactH = contactH * (0.40f + 0.60f * persp);
                float curWallShadowH = wallShadowH * (0.45f + 0.55f * persp);

                tmpDeckBackY[i] = ptsY[i] - curDeckD;
                tmpWallShadowY[i] = tmpDeckBackY[i] - curWallShadowH;
                tmpNosingBotY[i] = ptsY[i] + curNosingH;
                tmpMoldingY[i] = ptsY[i] + curNosingH - UiKit.pxf(2.0f);
                tmpDropShadowY[i] = tmpNosingBotY[i] + curShadowH;
                tmpContactShadowY[i] = ptsY[i] - curContactH;

                tmpGrain1Y[i] = ptsY[i] - curDeckD * 0.74f;
                tmpGrain2Y[i] = ptsY[i] - curDeckD * 0.48f;
                tmpGrain3Y[i] = ptsY[i] - curDeckD * 0.24f;
                tmpRefractionY[i] = ptsY[i] - curDeckD * 0.12f;
            }

            // Wall contact shadow
            wallShadowPath.moveTo(ptsX[0], tmpWallShadowY[0]);
            buildSmoothCurveForward(wallShadowPath, ptsX, tmpWallShadowY, ptCount);
            wallShadowPath.lineTo(ptsX[ptCount - 1], tmpDeckBackY[ptCount - 1]);
            buildSmoothCurveReverse(wallShadowPath, ptsX, tmpDeckBackY, ptCount);
            wallShadowPath.close();

            // 1. Deck polygon
            deckPath.moveTo(ptsX[0], tmpDeckBackY[0]);
            buildSmoothCurveForward(deckPath, ptsX, tmpDeckBackY, ptCount);
            deckPath.lineTo(ptsX[ptCount - 1], ptsY[ptCount - 1]);
            buildSmoothCurveReverse(deckPath, ptsX, ptsY, ptCount);
            deckPath.close();

            // 2. Rim catch line
            rimPath.moveTo(ptsX[0], ptsY[0]);
            buildSmoothCurveForward(rimPath, ptsX, ptsY, ptCount);

            // 3. Fascia nosing
            fasciaPath.moveTo(ptsX[0], ptsY[0]);
            buildSmoothCurveForward(fasciaPath, ptsX, ptsY, ptCount);
            fasciaPath.lineTo(ptsX[ptCount - 1], tmpNosingBotY[ptCount - 1]);
            buildSmoothCurveReverse(fasciaPath, ptsX, tmpNosingBotY, ptCount);
            fasciaPath.close();

            // 4. Molding line
            moldingPath.moveTo(ptsX[0], tmpMoldingY[0]);
            buildSmoothCurveForward(moldingPath, ptsX, tmpMoldingY, ptCount);

            // 5. Undercut line
            undercutPath.moveTo(ptsX[0], tmpNosingBotY[0]);
            buildSmoothCurveForward(undercutPath, ptsX, tmpNosingBotY, ptCount);

            // 6. Drop shadow polygon
            dropShadowPath.moveTo(ptsX[0], tmpNosingBotY[0]);
            buildSmoothCurveForward(dropShadowPath, ptsX, tmpNosingBotY, ptCount);
            dropShadowPath.lineTo(ptsX[ptCount - 1], tmpDropShadowY[ptCount - 1]);
            buildSmoothCurveReverse(dropShadowPath, ptsX, tmpDropShadowY, ptCount);
            dropShadowPath.close();

            // 7. Contact shadow under DVD cases
            contactShadowPath.moveTo(ptsX[0], tmpContactShadowY[0]);
            buildSmoothCurveForward(contactShadowPath, ptsX, tmpContactShadowY, ptCount);
            contactShadowPath.lineTo(ptsX[ptCount - 1], ptsY[ptCount - 1]);
            buildSmoothCurveReverse(contactShadowPath, ptsX, ptsY, ptCount);
            contactShadowPath.close();

            // 8. Procedural grain & refraction paths
            grain1Path.moveTo(ptsX[0], tmpGrain1Y[0]);
            buildSmoothCurveForward(grain1Path, ptsX, tmpGrain1Y, ptCount);

            grain2Path.moveTo(ptsX[0], tmpGrain2Y[0]);
            buildSmoothCurveForward(grain2Path, ptsX, tmpGrain2Y, ptCount);

            grain3Path.moveTo(ptsX[0], tmpGrain3Y[0]);
            buildSmoothCurveForward(grain3Path, ptsX, tmpGrain3Y, ptCount);

            refractionPath.moveTo(ptsX[0], tmpRefractionY[0]);
            buildSmoothCurveForward(refractionPath, ptsX, tmpRefractionY, ptCount);

            neonTubePath.moveTo(ptsX[0], tmpNosingBotY[0] + UiKit.pxf(1.0f));
            buildSmoothCurveForward(neonTubePath, ptsX, tmpNosingBotY, ptCount);
        }

        @Override
        protected void onDraw(Canvas c) {
            if ("redcarpet".equals(scene)) {
                redCarpetShelf(c);
            } else if ("cinema".equals(scene)) {
                cinemaShelf(c);
            } else if ("closet".equals(scene)) {
                closetShelf(c);
            } else if ("metal".equals(scene)) {
                metalShelf(c);
            } else if ("glass".equals(scene)) {
                glassShelf(c);
            } else if ("neon".equals(scene)) {
                neonShelf(c);
            } else if ("oled".equals(scene)) {
                oledShelf(c);
            } else {
                cinetvShelf(c);
            }
        }

        private void renderCurvedShelf(Canvas c, float deckDepth, float nosingH,
                                       int[] deckColors, float[] deckPos,
                                       int rimColor, float rimWidth,
                                       int rimApexColor,
                                       int[] faceColors, float[] facePos,
                                       int moldingColor,
                                       int undercutColor,
                                       int spotColor,
                                       int contactShadowColor,
                                       int grainColor,
                                       boolean isGlass,
                                       boolean isNeon) {
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

            // Ambient room lighting falloff mask
            int edgeDim = Color.argb(255, 115, 115, 120);
            int midDim = Color.argb(255, 210, 210, 215);
            int centerBright = Color.WHITE;
            Shader hDimMask = new LinearGradient(
                    startX, 0, endX, 0,
                    new int[]{edgeDim, midDim, centerBright, midDim, edgeDim},
                    new float[]{0f, 0.28f, 0.5f, 0.72f, 1f},
                    Shader.TileMode.CLAMP);

            // 0. Soft ambient contact shadow against the rear wall
            Shader wallGrad = new LinearGradient(0, minFloor - deckDepth - UiKit.pxf(10), 0, minFloor - deckDepth,
                    Color.TRANSPARENT, Color.argb(130, 0, 0, 0), Shader.TileMode.CLAMP);
            p.setStyle(Paint.Style.FILL);
            p.setShader(wallGrad);
            c.drawPath(wallShadowPath, p);

            // 1. Deck surface
            Shader deckV = new LinearGradient(0, minFloor - deckDepth, 0, maxFloor,
                    deckColors, deckPos, Shader.TileMode.CLAMP);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new ComposeShader(deckV, hDimMask, PorterDuff.Mode.MULTIPLY));
            c.drawPath(deckPath, p);

            // 1a. Procedural timber grain or brushed hairline passes
            if (grainColor != 0) {
                c.save();
                c.clipPath(deckPath);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(UiKit.pxf(1.0f));
                p.setShader(new LinearGradient(startX, 0, endX, 0,
                        new int[]{Color.TRANSPARENT, grainColor, Color.TRANSPARENT},
                        new float[]{0.1f, 0.5f, 0.9f}, Shader.TileMode.CLAMP));
                c.drawPath(grain1Path, p);
                c.drawPath(grain2Path, p);
                c.drawPath(grain3Path, p);
                c.restore();
            }

            // 1b. Optical glass internal refraction caustics
            if (isGlass) {
                c.save();
                c.clipPath(deckPath);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(UiKit.pxf(1.6f));
                p.setShader(new LinearGradient(startX, 0, endX, 0,
                        new int[]{Color.argb(40, 56, 189, 248), Color.argb(160, 224, 242, 254), Color.argb(40, 56, 189, 248)},
                        new float[]{0.05f, 0.5f, 0.95f}, Shader.TileMode.CLAMP));
                c.drawPath(refractionPath, p);
                c.restore();
            }

            // 2. Contact shadow under DVD cases
            if (contactShadowColor != 0) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, minFloor - UiKit.pxf(8), 0, maxFloor,
                        Color.TRANSPARENT, contactShadowColor, Shader.TileMode.CLAMP));
                c.drawPath(contactShadowPath, p);
            }

            // 3. Solid front rim bullnose catch line
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

            // 3b. Razor-sharp specular apex highlight bead
            if (rimApexColor != 0) {
                int apexDim = Color.argb(18, Color.red(rimApexColor), Color.green(rimApexColor), Color.blue(rimApexColor));
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(UiKit.pxf(1.0f));
                p.setShader(new LinearGradient(startX, 0, endX, 0,
                        new int[]{apexDim, rimApexColor, apexDim},
                        new float[]{0.15f, 0.5f, 0.85f},
                        Shader.TileMode.CLAMP));
                c.drawPath(rimPath, p);
            }

            // 4. Fascia nosing
            Shader faceV = new LinearGradient(0, minFloor, 0, maxFloor + nosingH,
                    faceColors, facePos, Shader.TileMode.CLAMP);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new ComposeShader(faceV, hDimMask, PorterDuff.Mode.MULTIPLY));
            c.drawPath(fasciaPath, p);

            // 5. Decorative / joinery molding line
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
                p.setStrokeWidth(UiKit.pxf(1.8f));
                c.drawPath(moldingPath, p);
            }

            // 6. Undercut bottom chamfer line
            if (undercutColor != 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(UiKit.pxf(1.2f));
                p.setShader(new LinearGradient(startX, 0, endX, 0,
                        new int[]{Color.argb(80, 0, 0, 0), undercutColor, Color.argb(80, 0, 0, 0)},
                        new float[]{0.1f, 0.5f, 0.9f}, Shader.TileMode.CLAMP));
                c.drawPath(undercutPath, p);
            }

            // 7. Neon diffuser tube & radiant under-glow (Neon Night theme)
            if (isNeon) {
                p.setStyle(Paint.Style.STROKE);
                // Wide radiant ambient wash
                p.setStrokeWidth(UiKit.pxf(12.0f));
                p.setShader(new LinearGradient(startX, 0, endX, 0,
                        new int[]{Color.argb(30, 6, 182, 212), Color.argb(85, 6, 182, 212), Color.argb(30, 6, 182, 212)},
                        new float[]{0.1f, 0.5f, 0.9f}, Shader.TileMode.CLAMP));
                c.drawPath(neonTubePath, p);
                // Vibrant magenta neon tube halo
                p.setStrokeWidth(UiKit.pxf(5.5f));
                p.setShader(new LinearGradient(startX, 0, endX, 0,
                        new int[]{Color.argb(60, 244, 114, 182), Color.argb(180, 244, 114, 182), Color.argb(60, 244, 114, 182)},
                        new float[]{0.15f, 0.5f, 0.85f}, Shader.TileMode.CLAMP));
                c.drawPath(neonTubePath, p);
                // Bright white-hot core tube
                p.setStrokeWidth(UiKit.pxf(1.8f));
                p.setShader(new LinearGradient(startX, 0, endX, 0,
                        new int[]{Color.argb(90, 255, 230, 250), Color.rgb(255, 240, 252), Color.argb(90, 255, 230, 250)},
                        new float[]{0.2f, 0.5f, 0.8f}, Shader.TileMode.CLAMP));
                c.drawPath(neonTubePath, p);
            }

            // 8. Architectural drop shadow
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, minFloor + nosingH, 0, maxFloor + nosingH + UiKit.pxf(24),
                    Color.argb(190, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawPath(dropShadowPath, p);

            // 8b. Atmospheric stage footlight / underglow wash
            if (spotColor != 0) {
                float cx = w * 0.5f;
                float flCenter = ptCount > 0 ? ptsY[ptCount / 2] : h * 0.78f;
                float underR = UiKit.pxf(300);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new android.graphics.RadialGradient(cx, flCenter + nosingH + UiKit.pxf(6), underR,
                        spotColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.save();
                c.clipPath(dropShadowPath);
                c.drawRect(cx - underR, flCenter + nosingH, cx + underR, flCenter + nosingH + UiKit.pxf(40), p);
                c.restore();
                p.setShader(null);
            }

            // 9. Center stage ambient pool
            p.setShader(null);
            drawCenterSpotlight(c, w, h, deckDepth, spotColor);
        }

        private void drawCenterSpotlight(Canvas c, int w, int h, float deckDepth, int spotColor) {
            if (spotColor == 0) return;
            float cx = w * 0.5f;
            float flCenter = ptCount > 0 ? ptsY[ptCount / 2] : h * 0.78f;
            float spotR = UiKit.pxf(340);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new android.graphics.RadialGradient(cx, flCenter - UiKit.pxf(14), spotR,
                    spotColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.save();
            c.clipPath(deckPath);
            c.drawRect(cx - spotR, flCenter - deckDepth * 1.5f, cx + spotR, flCenter + UiKit.pxf(10), p);
            c.restore();
            p.setShader(null);
        }

        private float shelfDeckDepth() {
            return Math.max(UiKit.pxf(44), caseW * 0.30f);
        }

        private void cinemaShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth() * 1.12f, UiKit.pxf(26),
                    new int[]{Color.rgb(18, 9, 6), Color.rgb(38, 17, 11), Color.rgb(58, 26, 17)},
                    new float[]{0f, 0.65f, 1f},
                    Color.rgb(238, 190, 62), UiKit.pxf(2.8f),
                    Color.rgb(255, 246, 196),
                    new int[]{Color.rgb(44, 20, 14), Color.rgb(28, 12, 8), Color.rgb(16, 7, 5)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(168, 126, 38),
                    Color.argb(230, 12, 5, 4),
                    Color.argb(62, 255, 204, 98),
                    Color.argb(145, 12, 5, 4),
                    Color.argb(32, 255, 200, 150),
                    false, false);
        }

        private void redCarpetShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth() * 1.10f, UiKit.pxf(24),
                    new int[]{Color.rgb(90, 8, 18), Color.rgb(148, 12, 28), Color.rgb(182, 16, 34)},
                    new float[]{0f, 0.65f, 1f},
                    Color.rgb(255, 220, 72), UiKit.pxf(2.8f),
                    Color.rgb(255, 250, 195),
                    new int[]{Color.rgb(44, 6, 14), Color.rgb(28, 4, 10), Color.rgb(16, 2, 6)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(195, 155, 45),
                    Color.argb(220, 20, 2, 6),
                    Color.argb(56, 255, 232, 135),
                    Color.argb(135, 35, 3, 8),
                    Color.argb(26, 255, 180, 190),
                    false, false);
        }

        private void closetShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth() * 0.96f, UiKit.pxf(22),
                    new int[]{Color.rgb(38, 40, 46), Color.rgb(62, 66, 74), Color.rgb(86, 92, 102)},
                    new float[]{0f, 0.60f, 1f},
                    Color.rgb(180, 185, 196), UiKit.pxf(2.4f),
                    Color.rgb(248, 250, 254),
                    new int[]{Color.rgb(102, 106, 116), Color.rgb(76, 80, 88), Color.rgb(52, 55, 62)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(32, 34, 40),
                    Color.argb(200, 24, 26, 30),
                    Color.argb(42, 255, 255, 255),
                    Color.argb(130, 12, 14, 18),
                    Color.argb(28, 230, 235, 245),
                    false, false);
        }

        private void metalShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth(), UiKit.pxf(22),
                    new int[]{Color.rgb(36, 42, 52), Color.rgb(58, 68, 82), Color.rgb(84, 98, 118)},
                    new float[]{0f, 0.65f, 1f},
                    Color.rgb(226, 232, 240), UiKit.pxf(2.2f),
                    Color.rgb(255, 255, 255),
                    new int[]{Color.rgb(40, 46, 56), Color.rgb(26, 30, 38), Color.rgb(16, 18, 24)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(100, 116, 139),
                    Color.argb(210, 10, 12, 16),
                    Color.argb(46, 241, 245, 249),
                    Color.argb(120, 12, 16, 22),
                    Color.argb(32, 203, 213, 225),
                    false, false);
        }

        private void glassShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth() * 1.05f, UiKit.pxf(24),
                    new int[]{Color.rgb(8, 14, 26), Color.rgb(18, 30, 50), Color.rgb(30, 48, 78)},
                    new float[]{0f, 0.60f, 1f},
                    Color.rgb(56, 189, 248), UiKit.pxf(2.6f),
                    Color.rgb(240, 249, 255),
                    new int[]{Color.rgb(18, 32, 54), Color.rgb(12, 20, 36), Color.rgb(6, 12, 22)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(125, 211, 252),
                    Color.argb(200, 14, 165, 233),
                    Color.argb(68, 56, 189, 248),
                    Color.argb(115, 4, 10, 20),
                    0,
                    true, false);
        }

        private void neonShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth(), UiKit.pxf(22),
                    new int[]{Color.rgb(14, 10, 24), Color.rgb(28, 16, 44), Color.rgb(44, 22, 68)},
                    new float[]{0f, 0.65f, 1f},
                    Color.rgb(244, 114, 182), UiKit.pxf(2.4f),
                    Color.rgb(255, 220, 245),
                    new int[]{Color.rgb(24, 14, 36), Color.rgb(14, 8, 22), Color.rgb(8, 4, 14)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(6, 182, 212),
                    Color.argb(220, 6, 2, 12),
                    Color.argb(50, 244, 114, 182),
                    Color.argb(130, 8, 4, 14),
                    0,
                    false, true);
        }

        private void oledShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth(), UiKit.pxf(20),
                    new int[]{Color.rgb(6, 6, 8), Color.rgb(12, 13, 16), Color.rgb(18, 20, 24)},
                    new float[]{0f, 0.60f, 1f},
                    Color.rgb(100, 116, 139), UiKit.pxf(1.6f),
                    Color.rgb(148, 163, 184),
                    new int[]{Color.rgb(12, 13, 16), Color.rgb(8, 9, 11), Color.rgb(4, 4, 5)},
                    new float[]{0f, 0.5f, 1f},
                    0,
                    Color.argb(240, 2, 2, 2),
                    0,
                    Color.argb(160, 0, 0, 0),
                    0,
                    false, false);
        }

        private void cinetvShelf(Canvas c) {
            renderCurvedShelf(c, shelfDeckDepth(), UiKit.pxf(22),
                    new int[]{Color.rgb(20, 22, 28), Color.rgb(34, 38, 48), Color.rgb(48, 54, 68)},
                    new float[]{0f, 0.65f, 1f},
                    Color.rgb(218, 226, 238), UiKit.pxf(2.4f),
                    Color.rgb(255, 255, 255),
                    new int[]{Color.rgb(28, 32, 40), Color.rgb(18, 20, 26), Color.rgb(10, 12, 16)},
                    new float[]{0f, 0.5f, 1f},
                    Color.rgb(148, 163, 184),
                    Color.argb(220, 8, 10, 14),
                    Color.argb(42, 56, 189, 248),
                    Color.argb(125, 6, 8, 12),
                    Color.argb(18, 218, 226, 238),
                    false, false);
        }
    }
}
