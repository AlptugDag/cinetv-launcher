package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * One DVD keep case drawn as a real box in perspective: the cover under its shrink-wrap, the
 * printed spine, the plastic top, bottom and opening edges, each lit by a light above-left of the
 * viewer, a soft shadow underneath and, when focused, the film's own colour glowing behind it.
 *
 * Every face is a textured quad (no paths, no clipping), so a case costs the GPU about as much as a
 * flat poster - this is what lets even a 1 GB TV show the shelf in 3D. Faces turned away are culled.
 */
public class DvdCaseView extends View {

    private static final float LX = -0.35f, LY = -0.55f, LZ = -0.76f;

    private DvdCase dvd;
    private final int caseW, caseH, depth;
    private final int glowPad;
    private float yaw, tilt, roll, scale = 1f, lift, spatialYaw;
    private float dim = 1f;
    private boolean spineRight;
    private float drift;
    private float blur;
    private float glint;
    private android.graphics.ColorFilter themeFilter;
    private float ca, sa, cb, sb, cr = 1f, sr = 0f, persp;

    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint wrapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint celloSeamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint celloThreadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint celloFoldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final float[] proj = new float[16];
    private final float[] floor = new float[8];
    private final float[] pt = new float[2];
    private final float[] src = new float[8];
    private final float[] dst = new float[8];
    private final int[] idx = new int[4];
    private final android.graphics.Rect sliceSrc = new android.graphics.Rect();

    public DvdCaseView(Context ctx, int caseW, int glowPad) {
        super(ctx);
        this.caseW = caseW;
        this.caseH = Math.round(caseW / DvdCase.ASPECT);
        this.depth = Math.max(6, Math.round(caseW * DvdCase.DEPTH));
        this.glowPad = glowPad;
    }

    /** Alpha is applied per draw call instead of through an offscreen layer (much cheaper on TV GPUs). */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public int caseWidth() { return caseW; }
    public int caseHeight() { return caseH; }

    /** View size that leaves room for the bloom and the turned case. */
    public int viewWidth() { return caseW + Glow.padH(glowPad) * 2; }
    public int viewHeight() { return caseH + glowPad * 2; }

    public void setCase(DvdCase dvd) {
        setCase(dvd, -1);
    }

    /**
     * A case arriving in an empty slot dissolves in place at its exact shelf position (after {@code appearDelayMs}),
     * without vertical dropping or scale jumping. Replacing one case with another is instant.
     */
    public void setCase(DvdCase dvd, long appearDelayMs) {
        boolean arriving = this.dvd == null && dvd != null && appearDelayMs >= 0;
        this.dvd = dvd;
        animate().cancel();
        if (dvd == null) {
            setAlpha(0f);
            setScaleX(1f);
            setScaleY(1f);
        } else if (arriving) {
            setAlpha(0f);
            setScaleX(0.92f);
            setScaleY(0.92f);
            animate().alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(appearDelayMs)
                    .setDuration(com.cinetv.launcher.device.PerformanceProfile.animationMs(getContext(), 460))
                    .setInterpolator(UiKit.EASE_IN_OUT)
                    .start();
        } else {
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
        }
        invalidate();
    }

    /** The film's own case, or null while the slot still shows the shared empty one. */
    public DvdCase dvdCase() { return dvd == null || dvd.placeholder ? null : dvd; }

    public void setPose(float yaw, float tilt, float scale, float lift, float extraYaw) {
        setPose(yaw, tilt, scale, lift, extraYaw, 1f, false, 0f);
    }

    /** Sets 3D roll angle (Z-axis bank in degrees) for full 3-axis physical orientation. */
    public void setRoll(float roll) {
        if (Math.abs(this.roll - roll) < 0.005f) return;
        this.roll = roll;
        invalidate();
    }

    /**
     * yaw/tilt in degrees; lift 0..1 (focus); extraYaw corrects perspective for the screen position;
     * dim darkens cases further back (solid objects, never see-through); spineRight mirrors the case
     * so cases right of the focus also show a printed spine; drift is the shelf's motion, which makes
     * the shrink-wrap reflections slide.
     */
    public void setPose(float yaw, float tilt, float scale, float lift, float extraYaw, float dim, boolean spineRight, float drift) {
        if (Math.abs(this.yaw - yaw) < 0.005f
                && Math.abs(this.tilt - tilt) < 0.005f
                && Math.abs(this.scale - scale) < 0.001f
                && Math.abs(this.lift - lift) < 0.002f
                && Math.abs(this.spatialYaw - extraYaw) < 0.005f
                && Math.abs(this.dim - dim) < 0.002f
                && this.spineRight == spineRight
                && Math.abs(this.drift - drift) < 0.002f) {
            return;
        }
        this.yaw = yaw;
        this.tilt = tilt;
        this.scale = scale;
        this.lift = lift;
        this.spatialYaw = extraYaw;
        this.dim = dim;
        this.spineRight = spineRight;
        this.drift = drift;
        invalidate();
    }

    /** 0 = sharp, 1 = fully out of focus (cases far down the shelf). */
    public void setDepthBlur(float blur) {
        if (Math.abs(this.blur - blur) < 0.01f) return;
        this.blur = blur;
        invalidate();
    }

    /** Returns the exact lowest Y offset of the projected box inside this view. */
    public float getBaseY() {
        float cy = (caseH + glowPad * 2) * 0.5f;
        double a = Math.toRadians(yaw + spatialYaw), b = Math.toRadians(tilt), r = Math.toRadians(roll);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
        float cr = (float) Math.cos(r), sr = (float) Math.sin(r);
        float persp = caseH * 2.2f;
        float hw = caseW * 0.5f, hh = caseH * 0.5f;
        float hd = (dvd != null && dvd.spine == null) ? 0f : depth * 0.5f;
        float maxBottom = cy;
        for (int i = 0; i < 4; i++) {
            float bx = (i == 1 || i == 3) ? -hw : hw;
            float bz = (i < 2) ? -hd : hd;
            float x1 = bx * ca + bz * sa;
            float z1 = -bx * sa + bz * ca;
            float y2 = hh * cb - z1 * sb;
            float z2 = hh * sb + z1 * cb;
            float y3 = x1 * sr + y2 * cr;
            float f = persp / (persp + z2) * scale;
            float py = cy + y3 * f;
            if (py > maxBottom) maxBottom = py;
        }
        return maxBottom;
    }

    public void setThemeFilter(android.graphics.ColorFilter filter) {
        if (this.themeFilter != filter) {
            this.themeFilter = filter;
            invalidate();
        }
    }

    /** Extra shrink-wrap reflection while the focused case turns on its own (0..1). */
    private boolean sleeve = true;

    /** Whether the cellophane sleeve's reflection is drawn (off down the shelf on slow TVs). */
    public void setSleeve(boolean on) {
        if (sleeve == on) return;
        sleeve = on;
        invalidate();
    }

    public void setGlint(float glint) {
        if (Math.abs(this.glint - glint) < 0.002f) return;
        this.glint = glint;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        double a = Math.toRadians(yaw + spatialYaw), b = Math.toRadians(tilt), r = Math.toRadians(roll);
        ca = (float) Math.cos(a); sa = (float) Math.sin(a);
        cb = (float) Math.cos(b); sb = (float) Math.sin(b);
        cr = (float) Math.cos(r); sr = (float) Math.sin(r);
        persp = caseH * 2.2f;
        if (lift > 0.01f && dvd != null && dvd.bloom != null) {
            // Subtle 3D backlight parallax: halo shifts slightly opposite the box's 3D yaw/pitch tilt
            float haloShiftX = -sa * caseW * scale * 0.08f;
            float haloShiftY = -sb * caseH * scale * 0.08f;
            if (dvd.halo) {
                // A soft lamp behind the box: gentle ambient glow without overpowering glare.
                fxPaint.setAlpha((int) (115 * lift));
                float bw = caseW * scale * 1.85f, bh = caseH * scale * 1.35f;
                float bx = cx + haloShiftX, by = cy + caseH * scale * 0.06f + haloShiftY;
                rect.set(bx - bw / 2f, by - bh / 2f, bx + bw / 2f, by + bh / 2f);
            } else {
                fxPaint.setAlpha((int) (135 * lift));
                float bw = dvd.bloom.getWidth() * scale, bh = dvd.bloom.getHeight() * scale;
                float bx = cx + haloShiftX, by = cy + haloShiftY;
                rect.set(bx - bw / 2f, by - bh / 2f, bx + bw / 2f, by + bh / 2f);
            }
            canvas.drawBitmap(dvd.bloom, null, rect, fxPaint);
        }
        // the case and its shadow always come together (the shadow used to stand alone until the cover was baked)
        if (dvd == null) return;
        drawShadow(canvas, cx, cy);

        project(cx, cy);
        if (dvd.spine == null) {
            // Plain poster shelf: only the front face, no case, no wrap.
            drawFace(canvas, 0, 1, 2, 3, dvd.cover, dvd.coverSoft, 0f, 0f, -1f, false);
            return;
        }
        // Faces as corner indexes, seen from outside: front, left, right, top, bottom.
        drawFace(canvas, 0, 1, 2, 3, dvd.cover, dvd.coverSoft, 0f, 0f, -1f, true);
        drawFace(canvas, 4, 0, 3, 7, spineRight ? DvdCase.edge(UiKit.isLight()) : dvd.spine, spineRight ? null : dvd.spineSoft, -1f, 0f, 0f, !spineRight);
        drawFace(canvas, 1, 5, 6, 2, spineRight ? dvd.spine : DvdCase.edge(UiKit.isLight()), spineRight ? dvd.spineSoft : null, 1f, 0f, 0f, spineRight);
        drawFace(canvas, 4, 5, 1, 0, DvdCase.edge(UiKit.isLight()), null, 0f, -1f, 0f, false);
        drawFace(canvas, 3, 2, 6, 7, DvdCase.edge(UiKit.isLight()), null, 0f, 1f, 0f, false);
    }

    private static final LightingColorFilter[] LIGHTING = new LightingColorFilter[256];

    private static final java.util.HashMap<Integer, LightingColorFilter> FOG = new java.util.HashMap<>();

    /** Multiply by m, then add a: shading plus a wash of the light room's colour (cached, no per-frame allocation). */
    private static LightingColorFilter fogLighting(int m, int a) {
        m = Math.max(0, Math.min(255, m)) & ~3;
        a = Math.max(0, Math.min(255, a)) & ~3;
        int key = (m << 8) | a;
        LightingColorFilter f = FOG.get(key);
        if (f == null) {
            f = new LightingColorFilter(Color.rgb(m, m, m), Color.rgb(a, a, a));
            FOG.put(key, f);
        }
        return f;
    }

    private static LightingColorFilter lighting(int l) {
        LightingColorFilter f = LIGHTING[l];
        if (f == null) LIGHTING[l] = f = new LightingColorFilter(Color.rgb(l, l, l), 0);
        return f;
    }

    /**
     * Contact shadow: the box's footprint on the floor, projected with the box itself, so its edges
     * run parallel to the case's bottom at any angle. A tight dark core plus a wider soft penumbra
     * falling back-right, away from the light. The case lifting off the floor softens it.
     */
    private void drawShadow(Canvas canvas, float cx, float cy) {
        if (lift > 0.01f) return; // Vitrinde bir dvd kutusu odakta ise kutunun altında gölge olmasın
        float shadowVisibility = Math.max(0f, 1f - lift * 1.4f);
        if (shadowVisibility <= 0.01f) return;
        Bitmap sh = DvdCase.shadow();
        float hw = caseW / 2f, hh = caseH / 2f;
        float hd = dvd != null && dvd.spine == null ? caseW * 0.05f : depth / 2f;
        float up = lift * caseH * 0.05f;
        // Wide penumbra (near the focus only: down the shelf the core alone reads the same, at half the cost).
        if (dim > 0.7f) {
            footprint(cx, cy, -hw * 1.06f + hw * 0.06f, hw * 1.06f + hw * 0.12f, -hd - caseW * 0.08f, hd + caseW * 0.14f, hh + up);
            fxPaint.setAlpha((int) (110 * shadowVisibility * dim));
            drawQuad(canvas, sh, floor, fxPaint);
        }
        // Tight core right under the box.
        footprint(cx, cy, -hw * 1.03f, hw * 1.03f, -hd - caseW * 0.03f, hd + caseW * 0.06f, hh + up);
        fxPaint.setAlpha((int) (200 * shadowVisibility * dim));
        drawQuad(canvas, sh, floor, fxPaint);
    }

    private void footprint(float cx, float cy, float x0, float x1, float z0, float z1, float y) {
        point(cx, cy, x0, y, z0); floor[0] = pt[0]; floor[1] = pt[1];
        point(cx, cy, x1, y, z0); floor[2] = pt[0]; floor[3] = pt[1];
        point(cx, cy, x1, y, z1); floor[4] = pt[0]; floor[5] = pt[1];
        point(cx, cy, x0, y, z1); floor[6] = pt[0]; floor[7] = pt[1];
    }

    private void drawQuad(Canvas canvas, Bitmap tex, float[] quad, Paint paint) {
        src[0] = 0; src[1] = 0;
        src[2] = tex.getWidth(); src[3] = 0;
        src[4] = tex.getWidth(); src[5] = tex.getHeight();
        src[6] = 0; src[7] = tex.getHeight();
        matrix.setPolyToPoly(src, 0, quad, 0, 4);
        canvas.drawBitmap(tex, matrix, paint);
    }

    /** Rotates a box-space point in full 3D (yaw -> pitch/tilt -> roll), places it at distance D and projects into pt. */
    private void point(float cx, float cy, float x, float y, float z) {
        float x1 = x * ca + z * sa;
        float z1 = -x * sa + z * ca;
        float y2 = y * cb - z1 * sb;
        float z2 = y * sb + z1 * cb;
        float x3 = x1 * cr - y2 * sr;
        float y3 = x1 * sr + y2 * cr;
        float f = persp / (persp + z2) * scale;
        pt[0] = cx + x3 * f;
        pt[1] = cy + y3 * f;
    }

    /** Projects the 8 corners of the box. */
    private void project(float cx, float cy) {
        float hw = caseW / 2f, hh = caseH / 2f, hd = dvd != null && dvd.spine == null ? 0f : depth / 2f;
        for (int i = 0; i < 8; i++) {
            float x = (i == 0 || i == 3 || i == 4 || i == 7) ? -hw : hw;
            float y = (i == 0 || i == 1 || i == 4 || i == 5) ? -hh : hh;
            float z = i < 4 ? -hd : hd;
            point(cx, cy, x, y, z);
            proj[i * 2] = pt[0];
            proj[i * 2 + 1] = pt[1];
        }
    }

    private void drawFace(Canvas canvas, int i0, int i1, int i2, int i3, Bitmap tex, Bitmap soft,
                          float nx, float ny, float nz, boolean wrapped) {
        idx[0] = i0; idx[1] = i1; idx[2] = i2; idx[3] = i3;
        float area = 0;
        for (int k = 0; k < 4; k++) {
            int p = idx[k], q = idx[(k + 1) % 4];
            area += proj[p * 2] * proj[q * 2 + 1] - proj[q * 2] * proj[p * 2 + 1];
        }
        if (area <= 0) return;

        // Full 3-axis Lambert + specular rim shading rotated with the box (yaw, pitch, roll).
        float x1 = nx * ca + nz * sa;
        float z1 = -nx * sa + nz * ca;
        float y2 = ny * cb - z1 * sb;
        float z2 = ny * sb + z1 * cb;
        float x3 = x1 * cr - y2 * sr;
        float y3 = x1 * sr + y2 * cr;
        float lambert = Math.max(0f, x3 * LX + y3 * LY + z2 * LZ);
        float light = Math.min(1f, 0.38f + 0.62f * lambert / 0.76f);
        int l = Math.min(255, (int) (255 * light * dim));
        LightingColorFilter filter = l >= 250 ? null : lighting(l);
        if (UiKit.isLight()) {
            // A light room: softer shading, and distance fades towards the room's colour, not black.
            float shade = 0.62f + 0.38f * light, fog = Math.max(0f, Math.min(0.75f, (1f - dim) * 0.9f));
            filter = fogLighting(Math.round(255 * shade * (1f - fog)), Math.round(232 * fog));
        }

        for (int k = 0; k < 4; k++) {
            dst[k * 2] = proj[idx[k] * 2];
            dst[k * 2 + 1] = proj[idx[k] * 2 + 1];
        }
        bmpPaint.setColorFilter(themeFilter != null ? themeFilter : filter);
        // Depth of field cross-fades the soft print over the sharp one; past half-way the sharp one is
        // invisible anyway, so it is not drawn (one bitmap per face instead of two down the shelf).
        if (soft == null || blur < 0.5f) {
            mapTo(tex, 0, tex.getWidth());
            bmpPaint.setAlpha(255);
            canvas.drawBitmap(tex, matrix, bmpPaint);
        }
        if (soft != null && blur > 0.02f) {
            // Depth of field: the out-of-focus print fades in over the sharp one.
            mapTo(soft, 0, soft.getWidth());
            bmpPaint.setAlpha(blur >= 0.5f ? 255 : (int) (255 * blur * 2f));
            canvas.drawBitmap(soft, matrix, bmpPaint);
            bmpPaint.setAlpha(255);
        }

        if (wrapped && sleeve) {
            // Soft collectors' satin cellophane sheen coupled directly to 3D yaw, pitch (tilt), and roll:
            Bitmap wrap = DvdCase.wrap();
            float facing = Math.max(0f, -z2);
            float yawDelta = (yaw + spatialYaw) - (-16f);
            float slide = (x3 * 0.5f + 0.5f) * 0.42f + yawDelta * 0.018f - tilt * 0.015f + drift * 0.05f + glint * 0.35f;
            int win = wrap.getWidth() / 3;
            float phase = (float) (slide - Math.floor(slide));
            float smooth = phase <= 0.5f ? phase * 2f : (1f - phase) * 2f;
            int off = (int) (smooth * (wrap.getWidth() - win));
            mapTo(tex, 0, tex.getWidth());
            // Dynamic theme-calibrated surface reflection & wrap gloss (restrained so cover art remains crisp):
            String themeId = com.cinetv.launcher.service.ThemeManager.current(getContext()).id;
            float themeWrapMul = "glass".equalsIgnoreCase(themeId) ? 1.15f :
                                 "closet".equalsIgnoreCase(themeId) ? 0.65f :
                                 "cinema".equalsIgnoreCase(themeId) || "redcarpet".equalsIgnoreCase(themeId) ? 0.95f :
                                 "metal".equalsIgnoreCase(themeId) ? 1.05f :
                                 "neon".equalsIgnoreCase(themeId) ? 1.10f : 0.90f;
            float wrapStrength = (0.06f + 0.03f * lift + 0.05f * glint) * (0.5f + 0.5f * facing) * dim * themeWrapMul;
            wrapPaint.setAlpha((int) (255 * Math.min(0.16f, wrapStrength)));
            sliceSrc.set(off, 0, off + win, wrap.getHeight());
            rect.set(0, 0, tex.getWidth(), tex.getHeight());
            canvas.save();
            canvas.concat(matrix);
            canvas.drawBitmap(wrap, sliceSrc, rect, wrapPaint);

            // Subtle 3D-synchronized satin sheen and spine bevel catchlight (no harsh center glare):
            if (lift > 0.05f) {
                float tw = tex.getWidth(), th = tex.getHeight();
                ensureSheenShaders(tw, th, themeId);
                float normX = Math.max(0.06f, Math.min(0.94f, 0.50f + yawDelta * 0.044f + (glint - 0.5f) * 0.24f));
                float normY = Math.max(0.08f, Math.min(0.92f, 0.46f - tilt * 0.055f + roll * 0.025f));
                float flareX = tw * normX;
                float flareY = th * normY;
                float streakShiftY = (normY - 0.46f) * th * 0.85f;
                float streakAngle = -roll * 2.5f + tilt * 0.85f;

                int modAlpha = Math.max(0, Math.min(110, Math.round(96f * lift * dim)));
                sheenMatrix.setTranslate(flareX, streakShiftY);
                sheenMatrix.postRotate(streakAngle, flareX, flareY);

                haloShader.setLocalMatrix(sheenMatrix);
                sheenPaint.setShader(haloShader);
                sheenPaint.setAlpha(modAlpha);
                canvas.drawRect(0, 0, tw, th, sheenPaint);

                coreShader.setLocalMatrix(sheenMatrix);
                sheenPaint.setShader(coreShader);
                sheenPaint.setAlpha(modAlpha);
                canvas.drawRect(0, 0, tw, th, sheenPaint);

                if (nz < -0.5f) {
                    float specAlign = 0.55f + 0.45f * (float) Math.cos(Math.toRadians(yawDelta * 5.0f - tilt * 4.0f));
                    int flareAlpha = Math.max(0, Math.min(110, Math.round(modAlpha * (0.50f + 0.30f * specAlign))));
                    // Rounded spine-hinge bevel catchlight gliding vertically with 3D pitch/roll:
                    flareMatrix.setTranslate(tw * 0.022f, flareY);
                    flareMatrix.preScale(0.18f, 0.85f);
                    spineFlareShader.setLocalMatrix(flareMatrix);
                    sheenPaint.setShader(spineFlareShader);
                    sheenPaint.setAlpha(flareAlpha);
                    canvas.drawRect(0, 0, tw * 0.065f, th, sheenPaint);
                }

                // Subtle top edge gloss modulated by 3D pitch (tilt):
                int topEdgeAlpha = Math.max(0, Math.min(110, Math.round(modAlpha * Math.max(0.25f, Math.min(0.90f, 0.65f - tilt * 0.06f)))));
                sheenPaint.setShader(topGlossShader);
                sheenPaint.setAlpha(topEdgeAlpha);
                canvas.drawRect(0, 0, tw, th * 0.032f, sheenPaint);
                sheenPaint.setShader(null);

                // Retail factory shrink-wrap (jelatin) seams, corner fold creases & tension wrinkles:
                drawCellophaneWrap(canvas, tw, th, modAlpha, yawDelta, tilt, roll);
            }
            canvas.restore();
        }
    }

    /** Draws realistic retail cellophane shrink-wrap (jelatin) details with zero object allocations. */
    private void drawCellophaneWrap(Canvas canvas, float tw, float th, int modAlpha, float yaw, float tilt, float roll) {
        if (modAlpha <= 10) return;

        // 1. Factory heat-sealed top and bottom weld seams
        float seamW = Math.max(1f, th * 0.0022f);
        celloSeamPaint.setStyle(Paint.Style.STROKE);
        celloSeamPaint.setStrokeWidth(seamW);
        celloSeamPaint.setColor(Color.argb(Math.round(modAlpha * 0.32f), 255, 255, 255));
        float topSeamY = th * 0.016f;
        float btmSeamY = th * 0.984f;
        canvas.drawLine(tw * 0.05f, topSeamY, tw * 0.98f, topSeamY, celloSeamPaint);
        canvas.drawLine(tw * 0.05f, btmSeamY, tw * 0.98f, btmSeamY, celloSeamPaint);

        // Seam micro drop shadow underneath
        celloSeamPaint.setColor(Color.argb(Math.round(modAlpha * 0.20f), 0, 0, 0));
        canvas.drawLine(tw * 0.05f, topSeamY + seamW, tw * 0.98f, topSeamY + seamW, celloSeamPaint);
        canvas.drawLine(tw * 0.05f, btmSeamY - seamW, tw * 0.98f, btmSeamY - seamW, celloSeamPaint);

        // 2. Right opening edge factory heat-crimp weld
        celloSeamPaint.setColor(Color.argb(Math.round(modAlpha * 0.28f), 255, 255, 255));
        float rightX = tw * 0.990f;
        canvas.drawLine(rightX, th * 0.04f, rightX, th * 0.96f, celloSeamPaint);

        // 3. Authentic triangular corner envelope folds (heat shrink creases)
        celloFoldPaint.setStyle(Paint.Style.STROKE);
        celloFoldPaint.setStrokeWidth(Math.max(1f, th * 0.0018f));
        celloFoldPaint.setColor(Color.argb(Math.round(modAlpha * 0.24f), 255, 255, 255));

        // Top-right corner fold
        canvas.drawLine(tw * 0.86f, 0, tw, th * 0.085f, celloFoldPaint);
        // Bottom-right corner fold
        canvas.drawLine(tw * 0.86f, th, tw, th * 0.915f, celloFoldPaint);
        // Top-left corner fold
        canvas.drawLine(tw * 0.03f, 0, 0, th * 0.055f, celloFoldPaint);
        // Bottom-left corner fold
        canvas.drawLine(tw * 0.03f, th, 0, th * 0.945f, celloFoldPaint);

        // 4. Tension micro-wrinkles radiating inwards from corners
        celloFoldPaint.setStrokeWidth(Math.max(0.8f, th * 0.0012f));
        celloFoldPaint.setColor(Color.argb(Math.round(modAlpha * 0.14f), 255, 255, 255));
        canvas.drawLine(tw * 0.90f, th * 0.03f, tw * 0.96f, th * 0.065f, celloFoldPaint);
        canvas.drawLine(tw * 0.88f, th * 0.05f, tw * 0.94f, th * 0.09f, celloFoldPaint);
        canvas.drawLine(tw * 0.90f, th * 0.97f, tw * 0.96f, th * 0.935f, celloFoldPaint);
        canvas.drawLine(tw * 0.88f, th * 0.95f, tw * 0.94f, th * 0.91f, celloFoldPaint);
    }

    private float cachedSheenW = -1f, cachedSheenH = -1f;
    private String cachedThemeId = null;
    private android.graphics.LinearGradient haloShader, coreShader, topGlossShader;
    private android.graphics.RadialGradient flareShader, spineFlareShader;
    private final Matrix sheenMatrix = new Matrix();
    private final Matrix flareMatrix = new Matrix();
    private static final float[] HALO_STOPS = {0f, 0.42f, 0.58f, 1f};
    private static final float[] FLARE_STOPS = {0f, 0.22f, 0.52f, 0.82f, 1f};

    private void ensureSheenShaders(float tw, float th, String themeId) {
        if (tw == cachedSheenW && th == cachedSheenH && themeId != null && themeId.equals(cachedThemeId) && haloShader != null) return;
        cachedSheenW = tw;
        cachedSheenH = th;
        cachedThemeId = themeId;
        float slant = th * 0.32f;

        if ("glass".equalsIgnoreCase(themeId)) {
            // Liquid Glass: razor-sharp crystal glass specular glare, optical sapphire/cyan prism dispersion
            haloShader = new android.graphics.LinearGradient(
                    -tw * 0.30f - slant, -th * 0.25f, tw * 0.30f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(45, 125, 211, 252), Color.argb(35, 56, 189, 248), Color.TRANSPARENT},
                    HALO_STOPS, Shader.TileMode.CLAMP);
            coreShader = new android.graphics.LinearGradient(
                    -tw * 0.038f - slant, -th * 0.25f, tw * 0.038f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(115, 255, 255, 255), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP);
            topGlossShader = new android.graphics.LinearGradient(
                    0f, 0f, 0f, Math.max(1f, th * 0.045f),
                    Color.argb(92, 224, 242, 254), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            float flareRadius = Math.max(16f, tw * 0.38f);
            flareShader = new android.graphics.RadialGradient(
                    0f, 0f, flareRadius,
                    new int[]{
                            Color.argb(125, 255, 255, 255),
                            Color.argb(72, 186, 230, 253),
                            Color.argb(34, 56, 189, 248),
                            Color.argb(8, 14, 165, 233),
                            Color.TRANSPARENT
                    },
                    FLARE_STOPS, Shader.TileMode.CLAMP);
            spineFlareShader = new android.graphics.RadialGradient(
                    0f, 0f, Math.max(12f, th * 0.36f),
                    new int[]{
                            Color.argb(145, 255, 255, 255),
                            Color.argb(68, 125, 211, 252),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);

        } else if ("closet".equalsIgnoreCase(themeId)) {
            // Film Closet: gentle, soft ambient wood reflection, warm velvety ivory/cream sheen without harsh glare
            haloShader = new android.graphics.LinearGradient(
                    -tw * 0.55f - slant, -th * 0.25f, tw * 0.55f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(18, 254, 243, 199), Color.argb(14, 253, 230, 138), Color.TRANSPARENT},
                    HALO_STOPS, Shader.TileMode.CLAMP);
            coreShader = new android.graphics.LinearGradient(
                    -tw * 0.16f - slant, -th * 0.25f, tw * 0.16f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(26, 255, 250, 240), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP);
            topGlossShader = new android.graphics.LinearGradient(
                    0f, 0f, 0f, Math.max(1f, th * 0.030f),
                    Color.argb(24, 254, 243, 199), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            float flareRadius = Math.max(20f, tw * 0.65f);
            flareShader = new android.graphics.RadialGradient(
                    0f, 0f, flareRadius,
                    new int[]{
                            Color.argb(35, 255, 252, 245),
                            Color.argb(20, 254, 240, 210),
                            Color.argb(10, 253, 230, 180),
                            Color.argb(3, 245, 220, 170),
                            Color.TRANSPARENT
                    },
                    FLARE_STOPS, Shader.TileMode.CLAMP);
            spineFlareShader = new android.graphics.RadialGradient(
                    0f, 0f, Math.max(12f, th * 0.36f),
                    new int[]{
                            Color.argb(42, 255, 250, 238),
                            Color.argb(18, 253, 230, 138),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);

        } else if ("cinema".equalsIgnoreCase(themeId) || "redcarpet".equalsIgnoreCase(themeId)) {
            // Cinema Hall & Red Carpet: warm theatrical tungsten proscenium reflection, amber/gold spotlight beam
            haloShader = new android.graphics.LinearGradient(
                    -tw * 0.42f - slant, -th * 0.25f, tw * 0.42f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(38, 245, 158, 11), Color.argb(25, 217, 119, 6), Color.TRANSPARENT},
                    HALO_STOPS, Shader.TileMode.CLAMP);
            coreShader = new android.graphics.LinearGradient(
                    -tw * 0.070f - slant, -th * 0.25f, tw * 0.070f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(65, 255, 244, 224), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP);
            topGlossShader = new android.graphics.LinearGradient(
                    0f, 0f, 0f, Math.max(1f, th * 0.038f),
                    Color.argb(58, 254, 240, 138), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            float flareRadius = Math.max(16f, tw * 0.48f);
            flareShader = new android.graphics.RadialGradient(
                    0f, 0f, flareRadius,
                    new int[]{
                            Color.argb(90, 255, 248, 220),
                            Color.argb(48, 251, 191, 36),
                            Color.argb(22, 245, 158, 11),
                            Color.argb(5, 180, 83, 9),
                            Color.TRANSPARENT
                    },
                    FLARE_STOPS, Shader.TileMode.CLAMP);
            spineFlareShader = new android.graphics.RadialGradient(
                    0f, 0f, Math.max(12f, th * 0.36f),
                    new int[]{
                            Color.argb(105, 255, 248, 220),
                            Color.argb(46, 251, 191, 36),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);

        } else if ("metal".equalsIgnoreCase(themeId)) {
            // Metallic: cold titanium anisotropic brushed metal reflection streak, crisp chamfer
            haloShader = new android.graphics.LinearGradient(
                    -tw * 0.35f - slant, -th * 0.25f, tw * 0.35f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(34, 203, 213, 225), Color.argb(24, 148, 163, 184), Color.TRANSPARENT},
                    HALO_STOPS, Shader.TileMode.CLAMP);
            coreShader = new android.graphics.LinearGradient(
                    -tw * 0.052f - slant, -th * 0.25f, tw * 0.052f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(82, 241, 245, 249), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP);
            topGlossShader = new android.graphics.LinearGradient(
                    0f, 0f, 0f, Math.max(1f, th * 0.040f),
                    Color.argb(75, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            float flareRadius = Math.max(16f, tw * 0.42f);
            flareShader = new android.graphics.RadialGradient(
                    0f, 0f, flareRadius,
                    new int[]{
                            Color.argb(92, 248, 250, 252),
                            Color.argb(48, 203, 213, 225),
                            Color.argb(20, 148, 163, 184),
                            Color.argb(4, 100, 116, 139),
                            Color.TRANSPARENT
                    },
                    FLARE_STOPS, Shader.TileMode.CLAMP);
            spineFlareShader = new android.graphics.RadialGradient(
                    0f, 0f, Math.max(12f, th * 0.36f),
                    new int[]{
                            Color.argb(115, 248, 250, 252),
                            Color.argb(48, 203, 213, 225),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);

        } else if ("neon".equalsIgnoreCase(themeId)) {
            // Neon Night: dual-color chromatic electric cyan / magenta neon tube reflection
            haloShader = new android.graphics.LinearGradient(
                    -tw * 0.38f - slant, -th * 0.25f, tw * 0.38f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(48, 34, 211, 238), Color.argb(42, 244, 114, 182), Color.TRANSPARENT},
                    HALO_STOPS, Shader.TileMode.CLAMP);
            coreShader = new android.graphics.LinearGradient(
                    -tw * 0.048f - slant, -th * 0.25f, tw * 0.048f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(92, 255, 255, 255), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP);
            topGlossShader = new android.graphics.LinearGradient(
                    0f, 0f, 0f, Math.max(1f, th * 0.042f),
                    Color.argb(76, 34, 211, 238), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            float flareRadius = Math.max(16f, tw * 0.45f);
            flareShader = new android.graphics.RadialGradient(
                    0f, 0f, flareRadius,
                    new int[]{
                            Color.argb(110, 255, 255, 255),
                            Color.argb(58, 34, 211, 238),
                            Color.argb(30, 236, 72, 153),
                            Color.argb(8, 168, 85, 247),
                            Color.TRANSPARENT
                    },
                    FLARE_STOPS, Shader.TileMode.CLAMP);
            spineFlareShader = new android.graphics.RadialGradient(
                    0f, 0f, Math.max(12f, th * 0.36f),
                    new int[]{
                            Color.argb(130, 255, 255, 255),
                            Color.argb(58, 34, 211, 238),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);

        } else {
            // Default CineTV / OLED: balanced studio master reflection
            haloShader = new android.graphics.LinearGradient(
                    -tw * 0.42f - slant, -th * 0.25f, tw * 0.42f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(26, 225, 240, 255), Color.argb(26, 255, 250, 240), Color.TRANSPARENT},
                    HALO_STOPS, Shader.TileMode.CLAMP);
            coreShader = new android.graphics.LinearGradient(
                    -tw * 0.075f - slant, -th * 0.25f, tw * 0.075f - slant, th * 1.25f,
                    new int[]{Color.TRANSPARENT, Color.argb(52, 255, 255, 255), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP);
            topGlossShader = new android.graphics.LinearGradient(
                    0f, 0f, 0f, Math.max(1f, th * 0.038f),
                    Color.argb(44, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            float flareRadius = Math.max(16f, tw * 0.46f);
            flareShader = new android.graphics.RadialGradient(
                    0f, 0f, flareRadius,
                    new int[]{
                            Color.argb(56, 255, 253, 248),
                            Color.argb(30, 224, 244, 255),
                            Color.argb(14, 255, 232, 200),
                            Color.argb(4, 200, 230, 255),
                            Color.TRANSPARENT
                    },
                    FLARE_STOPS, Shader.TileMode.CLAMP);
            spineFlareShader = new android.graphics.RadialGradient(
                    0f, 0f, Math.max(12f, th * 0.36f),
                    new int[]{
                            Color.argb(82, 255, 255, 255),
                            Color.argb(36, 220, 240, 255),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    /** Maps the horizontal slice [x0, x1] of a texture onto the current face quad. */
    private void mapTo(Bitmap tex, float x0, float x1) {
        src[0] = x0; src[1] = 0;
        src[2] = x1; src[3] = 0;
        src[4] = x1; src[5] = tex.getHeight();
        src[6] = x0; src[7] = tex.getHeight();
        matrix.setPolyToPoly(src, 0, dst, 0, 4);
    }
}
