package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * 5-Minute Idle System Screensaver (DreamService) featuring Alptuğ's personal signature logo,
 * minimalist Poppins Black clock & date, and burn-in safe smooth drifting.
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
            handler.postDelayed(this, 40000L);
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
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", new Locale("tr", "TR"));
        private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", new Locale("tr", "TR"));
        private final Drawable logoDrawable;
        private float offsetX = 0f;
        private float offsetY = 0f;

        DriftSignatureView() {
            super(ClockDream.this);
            clockPaint.setTypeface(FontManager.getBlack(ClockDream.this));
            clockPaint.setTextSize(96f);
            clockPaint.setColor(Color.parseColor("#F8FAFC"));

            datePaint.setTypeface(FontManager.getMedium(ClockDream.this));
            datePaint.setTextSize(24f);
            datePaint.setColor(Color.parseColor("#94A3B8"));

            Drawable d = null;
            try {
                d = getDrawable(R.drawable.ic_tv_clean);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(Color.WHITE);
                }
            } catch (Throwable ignored) {
            }
            logoDrawable = d;
        }

        void drift() {
            offsetX = (random.nextFloat() - 0.5f) * 320f;
            offsetY = (random.nextFloat() - 0.5f) * 180f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#050609"));

            float cx = (getWidth() / 2f) + offsetX;
            float cy = (getHeight() / 2f) + offsetY;

            // Subtle ambient radial glow behind Alptuğ signature logo
            glowPaint.setShader(new RadialGradient(
                    cx, cy - 40f, 260f,
                    new int[]{Color.parseColor("#2638BDF8"), Color.parseColor("#00050609")},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy - 40f, 260f, glowPaint);

            // Draw Alptuğ Signature Vector Logo (1:1 aspect ratio, 170x170)
            if (logoDrawable != null) {
                int logoSize = 170;
                int left = Math.round(cx - logoSize / 2f);
                int top = Math.round(cy - 190f);
                logoDrawable.setBounds(left, top, left + logoSize, top + logoSize);
                logoDrawable.draw(canvas);
            }

            Date now = new Date();
            String timeStr = timeFmt.format(now);
            String dateStr = dateFmt.format(now);

            float tw = clockPaint.measureText(timeStr);
            canvas.drawText(timeStr, cx - tw / 2f, cy + 78f, clockPaint);

            float dw = datePaint.measureText(dateStr);
            canvas.drawText(dateStr, cx - dw / 2f, cy + 118f, datePaint);
        }
    }
}
