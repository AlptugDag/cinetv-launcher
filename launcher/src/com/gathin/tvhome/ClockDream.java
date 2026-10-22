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
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", new Locale("tr", "TR"));

        private final Bitmap signatureBitmap;
        private final Drawable fallbackLogo;
        private boolean isRunning = false;

        VibrantDriftView(Context ctx) {
            super(ctx);
            clockPaint.setTypeface(FontManager.getBlack(ctx));
            clockPaint.setTextSize(92f);
            clockPaint.setColor(Color.WHITE);

            datePaint.setTypeface(FontManager.getMedium(ctx));
            datePaint.setTextSize(22f);
            datePaint.setColor(Color.parseColor("#CBD5E1"));
            datePaint.setLetterSpacing(0.08f);

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
            canvas.drawColor(Color.parseColor("#020306"));

            int w = getWidth() > 0 ? getWidth() : 1920;
            int h = getHeight() > 0 ? getHeight() : 1080;

            long nowMs = SystemClock.uptimeMillis();

            // Slow subtle Lissajous drift across canvas (±70px) for 100% OLED burn-in protection
            double driftT = (nowMs % 120000L) / 1000.0;
            float driftX = (float) (Math.sin(driftT * 0.18) * 70.0 + Math.sin(driftT * 0.37) * 30.0);
            float driftY = (float) (Math.cos(driftT * 0.14) * 40.0 + Math.sin(driftT * 0.28) * 20.0);

            float cx = (w / 2f) + driftX;
            float cy = (h / 2f) - 50f + driftY;

            // Cardiac cycle: 1250ms (~48 bpm deep cinematic resting heartbeat)
            double beatPhase = ((nowMs % 1250L) / 1250.0);
            float beatImpulse = 0f;
            if (beatPhase < 0.16) {
                // Primary systolic thump ("lub")
                beatImpulse = (float) Math.sin(beatPhase / 0.16 * Math.PI) * 0.22f;
            } else if (beatPhase >= 0.20 && beatPhase < 0.34) {
                // Secondary diastolic thump ("dub")
                beatImpulse = (float) Math.sin((beatPhase - 0.20) / 0.14 * Math.PI) * 0.11f;
            }

            // 6.5s deep organic respiration wave
            float respiration = (float) Math.sin((nowMs % 6500L) / 6500.0 * Math.PI * 2.0);
            float respirationScale = 1.0f + 0.07f * respiration;
            float heartScale = respirationScale + beatImpulse; // Total beating heart scale

            // Edge breathing wave: surges with respiration + surges on heartbeat
            float breathNorm = (respiration + 1.0f) * 0.5f;
            float edgeSurge = breathNorm * 0.65f + (beatImpulse * 1.4f);

            // Inward reach of colorful edge glows from screen borders
            float topReach = 190f + edgeSurge * 230f;
            float bottomReach = 190f + edgeSurge * 230f;
            float sideReach = 250f + edgeSurge * 280f;

            // 4 distinct shifting chromatic neon colors for the 4 edges
            float baseHue = (float) ((nowMs % 28000L) / 28000.0 * 360.0);
            float topHue = baseHue;
            float rightHue = (baseHue + 90f) % 360f;
            float bottomHue = (baseHue + 180f) % 360f;
            float leftHue = (baseHue + 270f) % 360f;

            int cTop = Color.HSVToColor(new float[]{topHue, 0.95f, 1.0f});
            int cRight = Color.HSVToColor(new float[]{rightHue, 0.92f, 1.0f});
            int cBottom = Color.HSVToColor(new float[]{bottomHue, 0.95f, 1.0f});
            int cLeft = Color.HSVToColor(new float[]{leftHue, 0.92f, 1.0f});

            // Edge density / opacity surges on breathing and heartbeat
            int edgeAlpha = (int) (52 + edgeSurge * 140);

            // 1. Top edge glow surging inward
            edgePaint.setShader(new LinearGradient(
                    0, 0, 0, topReach,
                    new int[]{Color.argb(edgeAlpha, Color.red(cTop), Color.green(cTop), Color.blue(cTop)), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, topReach, edgePaint);

            // 2. Bottom edge glow surging inward
            edgePaint.setShader(new LinearGradient(
                    0, h, 0, h - bottomReach,
                    new int[]{Color.argb(edgeAlpha, Color.red(cBottom), Color.green(cBottom), Color.blue(cBottom)), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, h - bottomReach, w, h, edgePaint);

            // 3. Left edge glow surging inward
            edgePaint.setShader(new LinearGradient(
                    0, 0, sideReach, 0,
                    new int[]{Color.argb((int)(edgeAlpha * 0.88f), Color.red(cLeft), Color.green(cLeft), Color.blue(cLeft)), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, sideReach, h, edgePaint);

            // 4. Right edge glow surging inward
            edgePaint.setShader(new LinearGradient(
                    w, 0, w - sideReach, 0,
                    new int[]{Color.argb((int)(edgeAlpha * 0.88f), Color.red(cRight), Color.green(cRight), Color.blue(cRight)), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(w - sideReach, 0, w, h, edgePaint);

            // Corner atmospheric blooms for continuous surround lighting
            float cornerR = sideReach * 1.25f;
            edgePaint.setShader(new RadialGradient(
                    0, 0, cornerR,
                    Color.argb((int)(edgeAlpha * 0.60f), Color.red(cTop), Color.green(cTop), Color.blue(cTop)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(0, 0, cornerR, edgePaint);

            edgePaint.setShader(new RadialGradient(
                    w, 0, cornerR,
                    Color.argb((int)(edgeAlpha * 0.60f), Color.red(cRight), Color.green(cRight), Color.blue(cRight)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(w, 0, cornerR, edgePaint);

            edgePaint.setShader(new RadialGradient(
                    0, h, cornerR,
                    Color.argb((int)(edgeAlpha * 0.60f), Color.red(cLeft), Color.green(cLeft), Color.blue(cLeft)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(0, h, cornerR, edgePaint);

            edgePaint.setShader(new RadialGradient(
                    w, h, cornerR,
                    Color.argb((int)(edgeAlpha * 0.60f), Color.red(cBottom), Color.green(cBottom), Color.blue(cBottom)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(w, h, cornerR, edgePaint);

            // Heart logo chromatic palette
            float primaryHue = (baseHue + 45f) % 360f;
            float secondaryHue = (primaryHue + 60f) % 360f;
            float tertiaryHue = (primaryHue + 140f) % 360f;
            int cHeartPrimary = Color.HSVToColor(new float[]{primaryHue, 0.95f, 1.0f});
            int cHeartSec = Color.HSVToColor(new float[]{secondaryHue, 0.90f, 0.95f});
            int cHeartTert = Color.HSVToColor(new float[]{tertiaryHue, 0.85f, 0.90f});

            // Outer cardiac mist surging with heartbeat
            float outerR = 560f * heartScale;
            auraPaint.setShader(new RadialGradient(
                    cx, cy, outerR,
                    new int[]{
                            Color.argb((int)(0x35 + beatImpulse * 0x45), Color.red(cHeartTert), Color.green(cHeartTert), Color.blue(cHeartTert)),
                            Color.argb(0x15, Color.red(cHeartSec), Color.green(cHeartSec), Color.blue(cHeartSec)),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.65f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, outerR, auraPaint);

            // Mid vibrant electric aura surging with heartbeat
            float midR = 350f * heartScale;
            auraPaint.setShader(new RadialGradient(
                    cx, cy, midR,
                    new int[]{
                            Color.argb((int)(0x75 + beatImpulse * 0x65), Color.red(cHeartSec), Color.green(cHeartSec), Color.blue(cHeartSec)),
                            Color.argb(0x28, Color.red(cHeartPrimary), Color.green(cHeartPrimary), Color.blue(cHeartPrimary)),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, midR, auraPaint);

            // Inner intense cardiac core
            float innerR = 210f * heartScale;
            auraPaint.setShader(new RadialGradient(
                    cx, cy, innerR,
                    new int[]{
                            Color.argb((int)(0xBB + beatImpulse * 0x44), Color.red(cHeartPrimary), Color.green(cHeartPrimary), Color.blue(cHeartPrimary)),
                            Color.argb(0x35, Color.red(cHeartPrimary), Color.green(cHeartPrimary), Color.blue(cHeartPrimary)),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.50f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, innerR, auraPaint);

            // Draw Huge Beating Heart Logo (Base size 380px, surging to ~480px on heartbeat!)
            float logoSize = 380f * heartScale;
            float logoLeft = cx - logoSize / 2f;
            float logoTop = cy - logoSize / 2f;

            if (signatureBitmap != null) {
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

            // Poppins Clock & Date positioned below the beating heart
            Date now = new Date();
            String timeStr = timeFmt.format(now);
            String dateStr = dateFmt.format(now).toUpperCase(new Locale("tr", "TR"));

            float clockY = cy + (logoSize / 2f) + 64f;
            clockPaint.setShadowLayer(26f, 0f, 6f, Color.argb(0x99, Color.red(cHeartPrimary), Color.green(cHeartPrimary), Color.blue(cHeartPrimary)));
            float tw = clockPaint.measureText(timeStr);
            canvas.drawText(timeStr, cx - tw / 2f, clockY, clockPaint);

            datePaint.setShadowLayer(14f, 0f, 3f, Color.parseColor("#80000000"));
            float dw = datePaint.measureText(dateStr);
            canvas.drawText(dateStr, cx - dw / 2f, clockY + 36f, datePaint);

            if (isRunning) {
                postInvalidateOnAnimation();
            }
        }
    }
}
