package com.cinetv.launcher.party;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.cinetv.launcher.device.SystemAudio;
import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.ui.UiKit;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Photorealistic 3D Gunmetal Revolver Cylinder ("SineRoulette") for film suggestion party games.
 * Features brushed steel fluted cylinder, cylindrical bullet chambers with movie posters,
 * central ratchet extractor gear, top hammer alignment notch, and realistic mechanical deceleration.
 */
public final class RouletteWheelView extends View {

    private final List<String> items = new ArrayList<>();
    private final List<Bitmap> posterBitmaps = new ArrayList<>();
    private float currentAngle = 0f;
    private float needleDeflection = 0f;
    private boolean isSpinning = false;

    private final Paint cylinderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chamberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint posterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint steelBevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ratchetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF wheelRect = new RectF();
    private final RectF chamberRect = new RectF();
    private final Path chamberPath = new Path();

    // Poster asset filenames for the revolver cylinder chambers
    private static final String[] POSTER_ASSETS = {
            "posters/interstellar.jpg",
            "posters/godfather.jpg",
            "posters/pulp_fiction.jpg",
            "posters/inception.jpg",
            "posters/fight_club.jpg",
            "posters/taxi_driver.jpg",
            "posters/blade_runner.jpg",
            "posters/whiplash.jpg"
    };

    // Vibrant cinema poster tones fallback
    private static final int[] POSTER_COLORS = {
            0xFF881337, // Crimson Noir (Taxi Driver)
            0xFF1E3A8A, // Midnight Sapphire (Interstellar)
            0xFF065F46, // Velvet Emerald (The Godfather)
            0xFFB45309, // Solar Amber (Pulp Fiction)
            0xFF581C87, // Electric Amethyst (Fight Club)
            0xFF0F766E, // Deep Teal (The Matrix)
            0xFF991B1B, // Ruby Red (Inception)
            0xFF312E81  // Deep Indigo (Whiplash)
    };

    public RouletteWheelView(Context context) {
        super(context);
        init();
    }

    public RouletteWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(FontManager.getBold(getContext()));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(UiKit.pxf(3), 0, UiKit.pxf(1), 0xDD000000);

        indicatorPaint.setColor(Color.parseColor("#38BDF8")); // Electric Cyan/Blue Hammer Notch

        // Load authentic TMDB posters into the bullet chambers
        for (String assetPath : POSTER_ASSETS) {
            try (InputStream in = getContext().getAssets().open(assetPath)) {
                posterBitmaps.add(BitmapFactory.decodeStream(in));
            } catch (Throwable ignored) {
                posterBitmaps.add(null);
            }
        }
    }

    public void setItems(List<String> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        invalidate();
    }

    public void spinTo(int winningIndex, Runnable onFinished) {
        if (items.isEmpty() || isSpinning) return;
        isSpinning = true;

        int count = items.size();
        float sliceAngle = 360f / count;
        // The pointer is at the TOP (270 degrees or -90 degrees in standard polar coords)
        // We want (winningIndex * sliceAngle + sliceAngle / 2) to land under the pointer at top (-90)
        float targetSliceCenter = winningIndex * sliceAngle + (sliceAngle / 2f);
        float endAngle = 360f * 6 + (270f - targetSliceCenter);

        final float startAngle = currentAngle % 360f;
        final float totalDelta = (endAngle - startAngle);

        final float[] lastPeg = {startAngle};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(4400);
        anim.setInterpolator(new DecelerateInterpolator(2.4f));

        anim.addUpdateListener(animation -> {
            float frac = (float) animation.getAnimatedValue();
            currentAngle = startAngle + totalDelta * frac;

            // Trigger audio ticks when passing slice/peg boundaries
            float degPassed = currentAngle - lastPeg[0];
            if (degPassed >= sliceAngle) {
                lastPeg[0] = currentAngle;
                needleDeflection = -14f * (1f - frac);
                SystemAudio.tick(getContext());
            } else {
                needleDeflection *= 0.85f;
            }
            invalidate();
        });

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isSpinning = false;
                needleDeflection = 0f;
                invalidate();
                SystemAudio.open(getContext());
                if (onFinished != null) onFinished.run();
            }
        });

        anim.start();
    }

    public void clickNextChamber(Runnable onFinished) {
        if (items.isEmpty() || isSpinning) return;
        isSpinning = true;

        int count = Math.max(4, Math.min(8, items.size()));
        float sliceAngle = 360f / count;
        final float startAngle = currentAngle;
        final float targetAngle = startAngle + sliceAngle;

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(360);
        anim.setInterpolator(new android.view.animation.OvershootInterpolator(1.4f));
        anim.addUpdateListener(a -> {
            float frac = (float) a.getAnimatedValue();
            currentAngle = startAngle + (targetAngle - startAngle) * frac;
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isSpinning = false;
                invalidate();
                SystemAudio.tick(getContext());
                if (onFinished != null) onFinished.run();
            }
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w > 0 ? w : UiKit.px(360), h > 0 ? h : UiKit.px(360));
        if (size <= 0) size = UiKit.px(360);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - UiKit.pxf(16);
        if (radius <= 0) return;

        wheelRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        canvas.save();
        canvas.rotate(currentAngle, cx, cy);

        // 1. Brushed Gunmetal Steel Revolver Cylinder Base
        cylinderPaint.setStyle(Paint.Style.FILL);
        cylinderPaint.setShader(new RadialGradient(cx - radius * 0.25f, cy - radius * 0.25f, radius * 1.15f,
                new int[]{0xFF334155, 0xFF1E293B, 0xFF0F172A}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, cylinderPaint);
        cylinderPaint.setShader(null);

        // Outer Steel Chamfer Bevel Ring
        steelBevelPaint.setStyle(Paint.Style.STROKE);
        steelBevelPaint.setStrokeWidth(UiKit.pxf(3));
        steelBevelPaint.setColor(Color.parseColor("#64748B"));
        canvas.drawCircle(cx, cy, radius - UiKit.pxf(2), steelBevelPaint);

        int count = Math.max(4, Math.min(8, items.size()));
        float sliceAngle = 360f / count;

        // 2. Revolver Cylinder Outer Flutes (Scalloped cutouts)
        steelBevelPaint.setStyle(Paint.Style.FILL);
        steelBevelPaint.setColor(0x33000000);
        float fluteR = radius * 0.22f;
        for (int i = 0; i < count; i++) {
            float midAng = (float) Math.toRadians(i * sliceAngle + sliceAngle / 2f);
            float fx = cx + radius * (float) Math.cos(midAng);
            float fy = cy + radius * (float) Math.sin(midAng);
            canvas.drawCircle(fx, fy, fluteR, steelBevelPaint);
        }

        // 3. Bullet Chambers (Mermi Yuvaları) with DVD-boxless Movie Posters
        float chamberDist = radius * 0.60f;
        float chamberR = radius * 0.26f;

        for (int i = 0; i < count; i++) {
            float ang = (float) Math.toRadians(i * sliceAngle);
            float chX = cx + chamberDist * (float) Math.cos(ang);
            float chY = cy + chamberDist * (float) Math.sin(ang);

            // Bore Hole Deep Shadow
            chamberPaint.setStyle(Paint.Style.FILL);
            chamberPaint.setColor(Color.parseColor("#090D14"));
            canvas.drawCircle(chX, chY, chamberR, chamberPaint);

            // Brass Cartridge Lip Ring
            chamberPaint.setStyle(Paint.Style.STROKE);
            chamberPaint.setStrokeWidth(UiKit.pxf(2));
            chamberPaint.setColor(Color.parseColor("#D97706"));
            canvas.drawCircle(chX, chY, chamberR - UiKit.pxf(1), chamberPaint);

            // Plain Poster (DVD-boxless, 2:3 ratio) matching CineTV Home
            float pw = chamberR * 1.30f;
            float ph = pw * 1.48f;
            chamberRect.set(chX - pw / 2f, chY - ph / 2f, chX + pw / 2f, chY + ph / 2f);
            float pCorner = pw * 0.085f;

            Bitmap posterBmp = (i < posterBitmaps.size()) ? posterBitmaps.get(i) : null;
            if (posterBmp != null) {
                chamberPath.reset();
                chamberPath.addRoundRect(chamberRect, pCorner, pCorner, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(chamberPath);
                canvas.drawBitmap(posterBmp, null, chamberRect, null);
                canvas.restore();
            } else {
                int pCol = POSTER_COLORS[i % POSTER_COLORS.length];
                posterPaint.setStyle(Paint.Style.FILL);
                posterPaint.setColor(pCol);
                canvas.drawRoundRect(chamberRect, pCorner, pCorner, posterPaint);
            }

            // Specular glass sheen on poster
            posterPaint.setStyle(Paint.Style.FILL);
            posterPaint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(chamberRect, pCorner, pCorner, posterPaint);

            // Outer crisp thin glass border
            posterPaint.setStyle(Paint.Style.STROKE);
            posterPaint.setStrokeWidth(UiKit.pxf(1.4f));
            posterPaint.setColor(0x88FFFFFF);
            canvas.drawRoundRect(chamberRect, pCorner, pCorner, posterPaint);

            // Movie Title text inside bottom of poster with glass backing
            if (!items.isEmpty()) {
                String title = items.get(i % items.size());
                if (title.length() > 9) title = title.substring(0, 8) + "..";
                textPaint.setTextSize(UiKit.pxf(9.5f));
                canvas.drawText(title, chX, chY + ph * 0.40f, textPaint);
            }
        }

        // 4. Center Extractor Star Ratchet Gear (Yıldız Dişli)
        float ratchetR = radius * 0.22f;
        ratchetPaint.setStyle(Paint.Style.FILL);
        ratchetPaint.setColor(Color.parseColor("#0F172A"));
        canvas.drawCircle(cx, cy, ratchetR, ratchetPaint);

        ratchetPaint.setStyle(Paint.Style.STROKE);
        ratchetPaint.setStrokeWidth(UiKit.pxf(2));
        ratchetPaint.setColor(Color.parseColor("#38BDF8")); // Cyan Accent
        canvas.drawCircle(cx, cy, ratchetR, ratchetPaint);

        // Center Pin
        ratchetPaint.setStyle(Paint.Style.FILL);
        ratchetPaint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawCircle(cx, cy, ratchetR * 0.45f, ratchetPaint);

        canvas.restore(); // Restore cylinder rotation

        // 5. Top Hammer Alignment Indicator (at 12 o'clock firing position)
        canvas.save();
        canvas.translate(cx, cy - radius);

        Path indicatorPath = new Path();
        float iw = UiKit.pxf(16);
        float ih = UiKit.pxf(24);
        indicatorPath.moveTo(-iw / 2f, -ih * 0.6f);
        indicatorPath.lineTo(iw / 2f, -ih * 0.6f);
        indicatorPath.lineTo(0, ih * 0.5f);
        indicatorPath.close();

        // Drop Shadow
        indicatorPaint.setColor(0x99000000);
        canvas.drawPath(indicatorPath, indicatorPaint);

        // Electric Blue Sight Body
        indicatorPaint.setColor(Color.parseColor("#38BDF8"));
        canvas.translate(0, -UiKit.pxf(2));
        canvas.drawPath(indicatorPath, indicatorPaint);

        // Glowing sight bead
        indicatorPaint.setColor(Color.WHITE);
        canvas.drawCircle(0, -ih * 0.4f, UiKit.pxf(3), indicatorPaint);

        canvas.restore();
    }
}
