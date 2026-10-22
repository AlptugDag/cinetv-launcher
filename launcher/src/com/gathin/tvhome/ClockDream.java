package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Modern Cinematic TV Screensaver (DreamService) featuring:
 * - High-resolution signature logo with luminous ambient chromatic glow
 * - Ultra-modern Poppins Black clock & date typography
 * - Smooth interpolated drift animation for 100% burn-in protection on OLED/QLED panels.
 */
public class ClockDream extends DreamService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private DriftSignatureView dreamView;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (dreamView != null) {
                dreamView.drift();
            }
            handler.postDelayed(this, 35000L);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        dreamView = new DriftSignatureView();
        setContentView(dreamView);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tickRunnable);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tickRunnable);
        super.onDreamingStopped();
    }

    private class DriftSignatureView extends View {
        private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", Locale.getDefault());

        private final Bitmap signatureBitmap;
        private final Drawable fallbackLogo;
        private float offsetX = 0f;
        private float offsetY = 0f;
        private ValueAnimator driftAnimator;

        DriftSignatureView() {
            super(ClockDream.this);
            clockPaint.setTypeface(FontManager.getBlack(ClockDream.this));
            clockPaint.setTextSize(112f);
            clockPaint.setColor(Color.WHITE);
            clockPaint.setShadowLayer(16f, 0f, 6f, Color.parseColor("#80000000"));

            datePaint.setTypeface(FontManager.getMedium(ClockDream.this));
            datePaint.setTextSize(24f);
            datePaint.setColor(Color.parseColor("#94A3B8"));
            datePaint.setLetterSpacing(0.08f);

            Bitmap sig = null;
            try {
                InputStream is = getAssets().open("signature_logo.png");
                sig = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Throwable ignored) {
            }
            signatureBitmap = sig;

            Drawable fallback = null;
            if (sig == null) {
                try {
                    fallback = getDrawable(R.drawable.ic_tv_clean);
                    if (fallback != null) {
                        fallback = fallback.mutate();
                        fallback.setTint(Color.WHITE);
                    }
                } catch (Throwable ignored) {
                }
            }
            fallbackLogo = fallback;
        }

        void drift() {
            final float startX = offsetX;
            final float startY = offsetY;
            final float targetX = (random.nextFloat() - 0.5f) * 360f;
            final float targetY = (random.nextFloat() - 0.5f) * 200f;

            if (driftAnimator != null) {
                driftAnimator.cancel();
            }

            driftAnimator = ValueAnimator.ofFloat(0f, 1f);
            driftAnimator.setDuration(1600L);
            driftAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            driftAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float f = animation.getAnimatedFraction();
                    offsetX = startX + (targetX - startX) * f;
                    offsetY = startY + (targetY - startY) * f;
                    invalidate();
                }
            });
            driftAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#030508"));

            float cx = (getWidth() / 2f) + offsetX;
            float cy = (getHeight() / 2f) + offsetY;

            // Luxurious ambient multi-stop radial glow
            glowPaint.setShader(new RadialGradient(
                    cx, cy - 60f, 340f,
                    new int[]{
                            Color.parseColor("#3838BDF8"), // Neon cyan glow core
                            Color.parseColor("#226366F1"), // Indigo mist
                            Color.parseColor("#0C0E7490"), // Deep teal aura
                            Color.parseColor("#00030508")  // Fade into pure OLED pitch black
                    },
                    new float[]{0f, 0.35f, 0.70f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy - 60f, 340f, glowPaint);

            // Draw Signature Logo (crisp, modern 220x220)
            int logoSize = 220;
            float logoLeft = cx - logoSize / 2f;
            float logoTop = cy - 210f;

            if (signatureBitmap != null) {
                Rect src = new Rect(0, 0, signatureBitmap.getWidth(), signatureBitmap.getHeight());
                RectF dst = new RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize);
                canvas.drawBitmap(signatureBitmap, src, dst, bitmapPaint);
            } else if (fallbackLogo != null) {
                int leftI = Math.round(logoLeft);
                int topI = Math.round(logoTop);
                fallbackLogo.setBounds(leftI, topI, leftI + logoSize, topI + logoSize);
                fallbackLogo.draw(canvas);
            }

            Date now = new Date();
            String timeStr = timeFmt.format(now);
            String dateStr = dateFmt.format(now).toUpperCase(new Locale("tr", "TR"));

            float tw = clockPaint.measureText(timeStr);
            canvas.drawText(timeStr, cx - tw / 2f, cy + 76f, clockPaint);

            float dw = datePaint.measureText(dateStr);
            canvas.drawText(dateStr, cx - dw / 2f, cy + 124f, datePaint);
        }
    }
}

