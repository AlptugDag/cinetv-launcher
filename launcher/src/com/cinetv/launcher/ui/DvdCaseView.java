package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private float yaw, tilt, scale = 1f, lift, spatialYaw;
    private float dim = 1f;
    private boolean spineRight;
    private float drift;
    private float blur;
    private float glint;
    private float ca, sa, cb, sb, persp;

    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint wrapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
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
        this.dvd = dvd;
        invalidate();
    }

    public DvdCase dvdCase() { return dvd; }

    public void setPose(float yaw, float tilt, float scale, float lift, float extraYaw) {
        setPose(yaw, tilt, scale, lift, extraYaw, 1f, false, 0f);
    }

    /**
     * yaw/tilt in degrees; lift 0..1 (focus); extraYaw corrects perspective for the screen position;
     * dim darkens cases further back (solid objects, never see-through); spineRight mirrors the case
     * so cases right of the focus also show a printed spine; drift is the shelf's motion, which makes
     * the shrink-wrap reflections slide.
     */
    public void setPose(float yaw, float tilt, float scale, float lift, float extraYaw, float dim, boolean spineRight, float drift) {
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

    /** Extra shrink-wrap reflection while the focused case turns on its own (0..1). */
    public void setGlint(float glint) {
        this.glint = glint;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        double a = Math.toRadians(yaw + spatialYaw), b = Math.toRadians(tilt);
        ca = (float) Math.cos(a); sa = (float) Math.sin(a); cb = (float) Math.cos(b); sb = (float) Math.sin(b);
        persp = caseH * 2.2f;
        if (lift > 0.01f && dvd != null && dvd.bloom != null) {
            if (dvd.halo) {
                // A lamp behind the box: wide, soft, a little low, never an outline.
                fxPaint.setAlpha((int) (235 * lift));
                float bw = caseW * scale * 2.3f, bh = caseH * scale * 1.6f, by = cy + caseH * scale * 0.08f;
                rect.set(cx - bw / 2f, by - bh / 2f, cx + bw / 2f, by + bh / 2f);
            } else {
                fxPaint.setAlpha((int) (255 * lift));
                float bw = dvd.bloom.getWidth() * scale, bh = dvd.bloom.getHeight() * scale;
                rect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
            }
            canvas.drawBitmap(dvd.bloom, null, rect, fxPaint);
        }
        drawShadow(canvas, cx, cy);
        if (dvd == null) return;

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
        Bitmap sh = DvdCase.shadow();
        float hw = caseW / 2f, hh = caseH / 2f;
        float hd = dvd != null && dvd.spine == null ? caseW * 0.05f : depth / 2f;
        float up = lift * caseH * 0.05f;
        // Wide penumbra.
        footprint(cx, cy, -hw * 1.10f + hw * 0.10f, hw * 1.10f + hw * 0.16f, -hd - caseW * 0.10f, hd + caseW * 0.34f, hh + up);
        fxPaint.setAlpha((int) ((120 - lift * 50) * dim));
        drawQuad(canvas, sh, floor, fxPaint);
        // Tight core right under the box.
        footprint(cx, cy, -hw * 1.03f, hw * 1.03f, -hd - caseW * 0.03f, hd + caseW * 0.06f, hh + up);
        fxPaint.setAlpha((int) ((200 - lift * 120) * dim));
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

    /** Rotates a box-space point (yaw then tilt), places it at distance D and projects it into pt. */
    private void point(float cx, float cy, float x, float y, float z) {
        float x1 = x * ca + z * sa;
        float z1 = -x * sa + z * ca;
        float y2 = y * cb - z1 * sb;
        float z2 = y * sb + z1 * cb;
        float f = persp / (persp + z2) * scale;
        pt[0] = cx + x1 * f;
        pt[1] = cy + y2 * f;
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

        // Lambert shading, rotated like the box.
        float x1 = nx * ca + nz * sa;
        float z1 = -nx * sa + nz * ca;
        float y2 = ny * cb - z1 * sb;
        float z2 = ny * sb + z1 * cb;
        float lambert = Math.max(0f, x1 * LX + y2 * LY + z2 * LZ);
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
        bmpPaint.setColorFilter(filter);
        if (soft == null || blur < 0.98f) {
            mapTo(tex, 0, tex.getWidth());
            bmpPaint.setAlpha(255);
            canvas.drawBitmap(tex, matrix, bmpPaint);
        }
        if (soft != null && blur > 0.02f) {
            // Depth of field: the out-of-focus print fades in over the sharp one.
            mapTo(soft, 0, soft.getWidth());
            bmpPaint.setAlpha((int) (255 * Math.min(1f, blur)));
            canvas.drawBitmap(soft, matrix, bmpPaint);
            bmpPaint.setAlpha(255);
        }

        if (wrapped) {
            // Shrink-wrap: a window onto a wide reflection texture, sliding as the case turns and the shelf moves.
            Bitmap wrap = DvdCase.wrap();
            float facing = Math.max(0f, -z2);
            float slide = (x1 * 0.5f + 0.5f) * 0.55f + drift * 0.08f + glint * 0.6f;
            int win = wrap.getWidth() / 3;
            int off = (int) ((slide - Math.floor(slide)) * (wrap.getWidth() - win));
            mapTo(tex, 0, tex.getWidth());
            wrapPaint.setAlpha((int) (255 * Math.min(1f, (0.45f + 0.55f * lift + 1.4f * glint) * (0.6f + 0.4f * facing)) * dim));
            sliceSrc.set(off, 0, off + win, wrap.getHeight());
            rect.set(0, 0, tex.getWidth(), tex.getHeight());
            canvas.save();
            canvas.concat(matrix);
            canvas.drawBitmap(wrap, sliceSrc, rect, wrapPaint);
            if (glint > 0.05f) {
                // Turning towards the light: a second, offset pass makes the sleeve flare as it passes.
                int off2 = (int) ((slide * 1.7f + 0.3f - Math.floor(slide * 1.7f + 0.3f)) * (wrap.getWidth() - win));
                sliceSrc.set(off2, 0, off2 + win, wrap.getHeight());
                wrapPaint.setAlpha((int) (255 * glint * dim));
                canvas.drawBitmap(wrap, sliceSrc, rect, wrapPaint);
            }
            canvas.restore();
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
