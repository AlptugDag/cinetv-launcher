package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.view.View;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ultra-Modern Vibrant Cinematic TV Screensaver (DreamService):
 * - Renders personal signature logo with multi-layered, continuously morphing chromatic neon auras.
 * - Dynamic 60fps orbital floating motion & subtle breathing for 100% OLED/QLED burn-in prevention.
 * - Poppins Black typography with chromatic ambient drop shadow.
 * - Auto-Shutdown Engine: After 5 minutes in dream (10 minutes total TV idle) without active media playback,
 *   safely executes a full hardware power down to protect panel and save energy.
 */
public class ClockDream extends DreamService {

    private static final long POWER_DOWN_DELAY_MS = 5 * 60 * 1000L; // 5 min dream = 10 min total idle
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VibrantDriftView dreamView;

    private final Runnable autoPowerDownRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMediaPlaying()) {
                // If media (music, podcast, stream) is still playing in the background, defer power down
                handler.postDelayed(this, 2 * 60 * 1000L);
                return;
            }
            executePowerDown();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        dreamView = new VibrantDriftView(this);
        setContentView(dreamView);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (dreamView != null) {
            dreamView.startAnimating();
        }
        handler.removeCallbacks(autoPowerDownRunnable);
        handler.postDelayed(autoPowerDownRunnable, POWER_DOWN_DELAY_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(autoPowerDownRunnable);
        if (dreamView != null) {
            dreamView.stopAnimating();
        }
        super.onDreamingStopped();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Any user interaction resets power down timer and wakes the TV
        handler.removeCallbacks(autoPowerDownRunnable);
        wakeUp();
        finish();
        return super.dispatchKeyEvent(event);
    }

    private boolean isMediaPlaying() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            return am != null && am.isMusicActive();
        } catch (Throwable t) {
            return false;
        }
    }

    private void executePowerDown() {
        try {
            Runtime.getRuntime().exec(new String[]{"reboot", "-p"});
        } catch (Throwable ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"}); // KEYCODE_POWER
        } catch (Throwable ignored) {
        }
        try {
            Intent shut = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
            shut.putExtra("android.intent.extra.KEY_CONFIRM", false);
            shut.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(shut);
        } catch (Throwable ignored) {
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                java.lang.reflect.Method goToSleep = pm.getClass().getMethod("goToSleep", long.class);
                goToSleep.invoke(pm, SystemClock.uptimeMillis());
            }
        } catch (Throwable ignored) {
        }
    }

    private class VibrantDriftView extends View {
        private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint starfieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", new Locale("tr", "TR"));

        private final Bitmap signatureBitmap;
        private final Drawable fallbackLogo;
        private boolean isRunning = false;

        private static final int STAR_COUNT = 45;
        private final float[] starX = new float[STAR_COUNT];
        private final float[] starY = new float[STAR_COUNT];
        private final float[] starSpeed = new float[STAR_COUNT];
        private final float[] starRadius = new float[STAR_COUNT];
        private final float[] starAlpha = new float[STAR_COUNT];

        VibrantDriftView(Context ctx) {
            super(ctx);
            clockPaint.setTypeface(FontManager.getBlack(ctx));
            clockPaint.setTextSize(104f);
            clockPaint.setColor(Color.WHITE);
            clockPaint.setLetterSpacing(0.04f);

            datePaint.setTypeface(FontManager.getMedium(ctx));
            datePaint.setTextSize(21f);
            datePaint.setColor(Color.parseColor("#94A3B8"));
            datePaint.setLetterSpacing(0.12f);

            Bitmap sig = null;
            try {
                InputStream is = ctx.getAssets().open("signature_logo.png");
                sig = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Throwable ignored) {
            }
            signatureBitmap = sig;

            Drawable fallback = null;
            if (sig == null) {
                try {
                    fallback = ctx.getDrawable(R.drawable.ic_tv_clean);
                    if (fallback != null) {
                        fallback = fallback.mutate();
                        fallback.setTint(Color.WHITE);
                    }
                } catch (Throwable ignored) {
                }
            }
            fallbackLogo = fallback;

            java.util.Random rnd = new java.util.Random(42);
            for (int i = 0; i < STAR_COUNT; i++) {
                starX[i] = rnd.nextFloat() * 1920f;
                starY[i] = rnd.nextFloat() * 1080f;
                starSpeed[i] = 0.15f + rnd.nextFloat() * 0.35f;
                starRadius[i] = 1.0f + rnd.nextFloat() * 2.2f;
                starAlpha[i] = 0.15f + rnd.nextFloat() * 0.45f;
            }
        }

        void startAnimating() {
            isRunning = true;
            postInvalidateOnAnimation();
        }

        void stopAnimating() {
            isRunning = false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#040508"));

            int w = getWidth() > 0 ? getWidth() : 1920;
            int h = getHeight() > 0 ? getHeight() : 1080;

            long nowMs = SystemClock.uptimeMillis();

            // Starfield
            for (int i = 0; i < STAR_COUNT; i++) {
                starY[i] -= starSpeed[i];
                if (starY[i] < 0) {
                    starY[i] = h;
                    starX[i] = (float) (Math.random() * w);
                }
                float twinkle = (float) (Math.sin((nowMs * 0.002) + i) * 0.25 + 0.75);
                int a = (int) (starAlpha[i] * twinkle * 255);
                starfieldPaint.setColor(Color.argb(Math.min(255, Math.max(0, a)), 200, 220, 255));
                canvas.drawCircle(starX[i], starY[i], starRadius[i], starfieldPaint);
            }

            // Orbital drift
            double driftT = (nowMs % 160000L) / 1000.0;
            float driftX = (float) (Math.sin(driftT * 0.12) * 85.0 + Math.sin(driftT * 0.25) * 35.0);
            float driftY = (float) (Math.cos(driftT * 0.10) * 50.0 + Math.sin(driftT * 0.19) * 25.0);

            float cx = (w / 2f) + driftX;
            float cy = (h / 2f) - 40f + driftY;

            float baseHue = (float) ((nowMs % 40000L) / 40000.0 * 360.0);
            float auroraHue = (baseHue + 40f) % 360f;
            int cCore = Color.HSVToColor(new float[]{baseHue, 0.75f, 0.95f});
            int cOuter = Color.HSVToColor(new float[]{auroraHue, 0.85f, 0.85f});

            float pulse = (float) (Math.sin(nowMs * 0.001) * 0.05 + 1.0);
            float nebulaR = 520f * pulse;
            auraPaint.setShader(new RadialGradient(
                    cx, cy - 20f, nebulaR,
                    new int[]{
                            Color.argb(0x38, Color.red(cCore), Color.green(cCore), Color.blue(cCore)),
                            Color.argb(0x18, Color.red(cOuter), Color.green(cOuter), Color.blue(cOuter)),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy - 20f, nebulaR, auraPaint);

            float logoSize = 160f * pulse;
            float logoLeft = cx - logoSize / 2f;
            float logoTop = cy - 130f;

            if (signatureBitmap != null) {
                bitmapPaint.setAlpha(220);
                Rect src = new Rect(0, 0, signatureBitmap.getWidth(), signatureBitmap.getHeight());
                RectF dst = new RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize);
                canvas.drawBitmap(signatureBitmap, src, dst, bitmapPaint);
            } else if (fallbackLogo != null) {
                int leftI = Math.round(logoLeft);
                int topI = Math.round(logoTop);
                int sizeI = Math.round(logoSize);
                fallbackLogo.setBounds(leftI, topI, leftI + sizeI, topI + sizeI);
                fallbackLogo.draw(canvas);
            }

            Date now = new Date();
            String timeStr = timeFmt.format(now);
            String dateStr = dateFmt.format(now).toUpperCase(new Locale("tr", "TR"));

            float clockY = cy + 90f;
            clockPaint.setShadowLayer(38f, 0f, 4f, Color.argb(0x88, Color.red(cCore), Color.green(cCore), Color.blue(cCore)));
            float tw = clockPaint.measureText(timeStr);
            canvas.drawText(timeStr, cx - tw / 2f, clockY, clockPaint);

            datePaint.setShadowLayer(16f, 0f, 2f, Color.parseColor("#99000000"));
            float dw = datePaint.measureText(dateStr);
            canvas.drawText(dateStr, cx - dw / 2f, clockY + 44f, datePaint);

            if (isRunning) {
                postInvalidateOnAnimation();
            }
        }
    }
}
