package com.cinetv.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.cinetv.launcher.service.PosterCache;

/**
 * Full-height theatrical velvet proscenium curtains framing the TV screen in Cinema Hall theme.
 * Reaches from the absolute top to the absolute bottom of the screen (0 to height).
 * Both left and right curtains are symmetrically semi-transparent (alpha ~0.88),
 * and organically billow/wave based on vitrin shelf motion direction.
 * translationZ = 95f keeps them behind header (Z=200) and shelf title (Z=200),
 * while gently draping in front of shelf edges.
 */
public final class CinemaCurtainsView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path foldPath = new Path();
    private static Bitmap velvetCache;

    private float motionLean = 0f;
    private float flutter = 0f;
    private float wavePhase = 0f;
    private ValueAnimator flutterAnim;

    public CinemaCurtainsView(Context ctx) {
        super(ctx);
        setFocusable(false);
        setClickable(false);
        setTranslationZ(95f);
        // Symmetrical uniform semi-transparency on both curtains
        setAlpha(0.88f);
    }

    private Bitmap velvet() {
        if (velvetCache == null) {
            velvetCache = PosterCache.decodeAsset(getContext(), "themes/velvet.webp", 360, 720);
            if (velvetCache == null) velvetCache = PosterCache.decodeAsset(getContext(), "themes/velvet.jpg", 360, 720);
        }
        return velvetCache;
    }

    /** Called on each vitrin frame with the shelf's motion velocity/direction. */
    public void setMotion(float lean) {
        this.motionLean = lean;
        float targetFlutter = Math.max(-12f, Math.min(12f, lean * 0.75f));
        if (flutterAnim != null) flutterAnim.cancel();
        flutterAnim = ValueAnimator.ofFloat(flutter, targetFlutter, 0f);
        flutterAnim.setDuration(450);
        flutterAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        flutterAnim.addUpdateListener(a -> {
            flutter = (float) a.getAnimatedValue();
            wavePhase += 0.12f;
            invalidate();
        });
        flutterAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float topY = 0f;
        float botY = (float) h;
        float curtainW = UiKit.pxf(145);

        // --- Left Curtain (Pinned flush to x = 0 at the left border) ---
        drawProsceniumCurtain(c, 0f, curtainW, topY, botY, false);

        // --- Right Curtain (Pinned flush to x = w at the right border) ---
        drawProsceniumCurtain(c, w - curtainW, (float) w, topY, botY, true);
    }

    private void drawProsceniumCurtain(Canvas c, float x0, float x1, float topY, float botY, boolean isRight) {
        float curtainW = x1 - x0;
        int folds = 4;
        float fw = curtainW / folds;

        // Dynamic motion wave offset based on shelf motion velocity
        float billow = flutter * UiKit.pxf(0.8f);

        // 1. Velvet base texture (both curtains identical semi-transparent richness)
        Bitmap velvet = velvet();
        if (velvet != null) {
            BitmapShader bs = new BitmapShader(velvet, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Matrix m = new Matrix();
            float sc = (botY - topY) / (float) velvet.getHeight();
            m.setScale(sc, sc);
            m.postTranslate(x0, topY);
            bs.setLocalMatrix(m);
            p.setShader(bs);
            p.setAlpha(225); // Uniform semi-transparency
            c.drawRect(x0, topY, x1, botY, p);
            p.setShader(null);
        } else {
            p.setColor(Color.argb(225, 75, 8, 14));
            c.drawRect(x0, topY, x1, botY, p);
        }

        // 2. Realistic 3D cylindrical velvet flutes with wave billow
        int steps = 16;
        float dy = (botY - topY) / steps;

        for (int i = 0; i < folds; i++) {
            float fx = isRight ? (x0 + i * fw) : (x0 + i * fw);
            float innerProximity = isRight ? (1f - (float) i / folds) : ((float) (i + 1) / folds);

            // Shading gradient across each cylindrical fold
            p.setShader(new LinearGradient(fx, 0, fx + fw, 0,
                    new int[]{
                            Color.argb(210, 20, 2, 4),   // deep shadow crevice
                            Color.argb(110, 48, 5, 9),   // shaded rise
                            Color.argb(velvet != null ? 40 : 220, 148, 16, 29), // velvet highlight ridge
                            Color.argb(120, 48, 5, 9),   // receding slope
                            Color.argb(210, 20, 2, 4)    // crevice
                    },
                    new float[]{0f, 0.22f, 0.50f, 0.78f, 1f}, Shader.TileMode.CLAMP));

            // Wave path along the fold
            foldPath.reset();
            foldPath.moveTo(fx, topY);
            for (int s = 0; s <= steps; s++) {
                float y = topY + s * dy;
                float wave = (float) Math.sin((y / (botY - topY)) * Math.PI * 3f + wavePhase) * billow * innerProximity;
                foldPath.lineTo(fx + fw + wave, y);
            }
            foldPath.lineTo(fx + fw, botY);
            foldPath.lineTo(fx, botY);
            for (int s = steps; s >= 0; s--) {
                float y = topY + s * dy;
                float wave = (float) Math.sin((y / (botY - topY)) * Math.PI * 3f + wavePhase) * billow * innerProximity;
                foldPath.lineTo(fx + wave, y);
            }
            foldPath.close();

            c.drawPath(foldPath, p);
        }

        // 3. Crisp vertical piping edge along the inner curtain fold (waves with motion)
        float pipeX = isRight ? x0 : x1;
        p.setShader(null);
        p.setColor(Color.argb(200, 175, 24, 38));

        foldPath.reset();
        foldPath.moveTo(pipeX, topY);
        for (int s = 0; s <= steps; s++) {
            float y = topY + s * dy;
            float wave = (float) Math.sin((y / (botY - topY)) * Math.PI * 3f + wavePhase) * billow;
            foldPath.lineTo(pipeX + wave, y);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(UiKit.pxf(3f));
        c.drawPath(foldPath, p);
        p.setStyle(Paint.Style.FILL);

        // 4. Soft ambient shadow cast from the curtain inward onto passing vitrin items
        float shadowStart = isRight ? x0 : x1 - UiKit.pxf(6);
        float shadowEnd = isRight ? x0 - UiKit.pxf(48) : x1 + UiKit.pxf(48);
        p.setShader(new LinearGradient(shadowStart, 0, shadowEnd, 0,
                new int[]{Color.argb(130, 8, 1, 2), Color.argb(50, 8, 1, 2), Color.TRANSPARENT},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(Math.min(shadowStart, shadowEnd), topY, Math.max(shadowStart, shadowEnd), botY, p);
        p.setShader(null);
    }
}
