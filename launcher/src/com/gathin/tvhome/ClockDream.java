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

    private static class CinemaMoment {
        final String quote;
        final String imdbId;
        CinemaMoment(String quote, String imdbId) {
            this.quote = quote;
            this.imdbId = imdbId;
        }
    }

    private class VibrantDriftView extends View {
        private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint quotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint starfieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint scenePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", new Locale("tr", "TR"));

        private final Bitmap signatureBitmap;
        private final Drawable fallbackLogo;
        private boolean isRunning = false;

        private final CinemaMoment[] cinemaMoments = new CinemaMoment[]{
            new CinemaMoment("\"Bütün o anlar zamanda kaybolup gidecek, yağmurdaki gözyaşları gibi...\" — Blade Runner", "tt0083658"),
            new CinemaMoment("\"We used to look up at the sky and wonder at our place in the stars.\" — Interstellar", "tt0816692"),
            new CinemaMoment("\"Rüyalar gerçektir, sadece uyanıkken onları göremezsin.\" — Inception", "tt1375666"),
            new CinemaMoment("\"Her şey bir seçimle başlar.\" — The Matrix", "tt0133093"),
            new CinemaMoment("\"Sinema, zamanı ve anıları donduran tek büyüdür.\" — Solaris", "tt0069291"),
            new CinemaMoment("\"Karanlık seni korkutmasın, asıl korkulacak olan ışıktaki sahteliktir.\" — Fight Club", "tt0137523"),
            new CinemaMoment("\"Korku aklı yok eden küçük ölümdür. Korkumla yüzleşeceğim.\" — Dune", "tt1160419")
        };

        private Bitmap activeSceneBackdrop = null;
        private int lastLoadedSceneIdx = -1;
        private boolean isFetchingScene = false;

        // Cinematic floating dust particles & stars
        private static final int STAR_COUNT = 55;
        private final float[] starX = new float[STAR_COUNT];
        private final float[] starY = new float[STAR_COUNT];
        private final float[] starSpeed = new float[STAR_COUNT];
        private final float[] starRadius = new float[STAR_COUNT];
        private final float[] starAlpha = new float[STAR_COUNT];

        VibrantDriftView(Context ctx) {
            super(ctx);
            clockPaint.setTypeface(FontManager.getBlack(ctx));
            clockPaint.setTextSize(78f);
            clockPaint.setColor(Color.WHITE);
            clockPaint.setLetterSpacing(0.05f);

            datePaint.setTypeface(FontManager.getMedium(ctx));
            datePaint.setTextSize(18f);
            datePaint.setColor(Color.parseColor("#94A3B8"));
            datePaint.setLetterSpacing(0.14f);

            quotePaint.setTypeface(FontManager.getBold(ctx));
            quotePaint.setTextSize(36f);
            quotePaint.setColor(Color.parseColor("#F8FAFC"));
            quotePaint.setLetterSpacing(0.04f);

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
                    fallback = ctx.getDrawable(R.drawable.ic_user_signature);
                } catch (Throwable t) {
                    try {
                        fallback = ctx.getDrawable(R.drawable.ic_tv_clean);
                    } catch (Throwable ignored) {
                    }
                }
                if (fallback != null) {
                    fallback = fallback.mutate();
                    fallback.setTint(Color.WHITE);
                }
            }
            fallbackLogo = fallback;

            java.util.Random rnd = new java.util.Random(42);
            for (int i = 0; i < STAR_COUNT; i++) {
                starX[i] = rnd.nextFloat() * 1920f;
                starY[i] = rnd.nextFloat() * 1080f;
                starSpeed[i] = 0.12f + rnd.nextFloat() * 0.38f;
                starRadius[i] = 1.0f + rnd.nextFloat() * 2.4f;
                starAlpha[i] = 0.15f + rnd.nextFloat() * 0.50f;
            }
        }

        private void fetchDynamicTmdbBackdrop(final int momentIdx) {
            if (isFetchingScene || momentIdx == lastLoadedSceneIdx) return;
            isFetchingScene = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String imdbId = cinemaMoments[momentIdx].imdbId;
                        String tmdbKey = null;
                        try {
                            tmdbKey = android.provider.Settings.Secure.getString(getContentResolver(), "tv_tmdb_api_key");
                        } catch (Throwable ignored) {
                        }

                        String backdropUrl = null;
                        if (tmdbKey != null && !tmdbKey.trim().isEmpty()) {
                            try {
                                String findUrl = "https://api.themoviedb.org/3/find/" + imdbId + "?api_key=" + tmdbKey.trim() + "&external_source=imdb_id";
                                java.net.HttpURLConnection fConn = (java.net.HttpURLConnection) new java.net.URL(findUrl).openConnection();
                                fConn.setConnectTimeout(5000);
                                fConn.setReadTimeout(5000);
                                if (fConn.getResponseCode() == 200) {
                                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(fConn.getInputStream()));
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while ((line = br.readLine()) != null) sb.append(line);
                                    br.close();
                                    org.json.JSONObject findObj = new org.json.JSONObject(sb.toString());
                                    org.json.JSONArray mResults = findObj.optJSONArray("movie_results");
                                    if (mResults != null && mResults.length() > 0) {
                                        int tmdbMovieId = mResults.getJSONObject(0).optInt("id", 0);
                                        if (tmdbMovieId > 0) {
                                            String imgApiUrl = "https://api.themoviedb.org/3/movie/" + tmdbMovieId + "/images?api_key=" + tmdbKey.trim();
                                            java.net.HttpURLConnection imgConn = (java.net.HttpURLConnection) new java.net.URL(imgApiUrl).openConnection();
                                            imgConn.setConnectTimeout(5000);
                                            imgConn.setReadTimeout(5000);
                                            if (imgConn.getResponseCode() == 200) {
                                                java.io.BufferedReader ibr = new java.io.BufferedReader(new java.io.InputStreamReader(imgConn.getInputStream()));
                                                StringBuilder isb = new StringBuilder();
                                                while ((line = ibr.readLine()) != null) isb.append(line);
                                                ibr.close();
                                                org.json.JSONObject imgObj = new org.json.JSONObject(isb.toString());
                                                org.json.JSONArray bArr = imgObj.optJSONArray("backdrops");
                                                if (bArr != null && bArr.length() > 0) {
                                                    // Pick a high-quality backdrop
                                                    int pickIdx = (int) (System.currentTimeMillis() % bArr.length());
                                                    String fPath = bArr.getJSONObject(pickIdx).optString("file_path", "");
                                                    if (!fPath.isEmpty()) {
                                                        backdropUrl = "https://image.tmdb.org/t/p/w1280" + fPath;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }

                        if (backdropUrl == null) {
                            backdropUrl = "https://images.metahub.space/background/medium/" + imdbId + "/img";
                        }

                        java.net.URL url = new java.net.URL(backdropUrl);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(6000);
                        conn.setReadTimeout(10000);
                        conn.setInstanceFollowRedirects(true);
                        if (conn.getResponseCode() == 200) {
                            InputStream is = conn.getInputStream();
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inSampleSize = 2; // Lightweight 960x540
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                            final Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                            is.close();
                            if (bmp != null) {
                                post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (activeSceneBackdrop != null && !activeSceneBackdrop.isRecycled()) {
                                            activeSceneBackdrop.recycle();
                                        }
                                        activeSceneBackdrop = bmp;
                                        lastLoadedSceneIdx = momentIdx;
                                        invalidate();
                                    }
                                });
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        isFetchingScene = false;
                    }
                }
            }).start();
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
            // Deep OLED void black
            canvas.drawColor(Color.parseColor("#030407"));

            int w = getWidth() > 0 ? getWidth() : 1920;
            int h = getHeight() > 0 ? getHeight() : 1080;

            long nowMs = SystemClock.uptimeMillis();

            // 0. Dynamic Movie Scene Background from TMDB (low-opacity, zero device storage)
            long quoteCycleMs = 18000L;
            long quoteElapsed = nowMs % quoteCycleMs;
            int quoteIdx = (int) ((nowMs / quoteCycleMs) % cinemaMoments.length);
            fetchDynamicTmdbBackdrop(quoteIdx);

            float quoteAlpha = 1.0f;
            if (quoteElapsed < 1500L) {
                quoteAlpha = quoteElapsed / 1500f; // Fade in
            } else if (quoteElapsed > quoteCycleMs - 1500L) {
                quoteAlpha = (quoteCycleMs - quoteElapsed) / 1500f; // Fade out
            }

            if (activeSceneBackdrop != null && !activeSceneBackdrop.isRecycled()) {
                int sceneAlpha = (int) (quoteAlpha * 58); // ~22% ethereal low opacity
                scenePaint.setAlpha(sceneAlpha);
                Rect src = new Rect(0, 0, activeSceneBackdrop.getWidth(), activeSceneBackdrop.getHeight());
                RectF dst = new RectF(0, 0, w, h);
                canvas.drawBitmap(activeSceneBackdrop, src, dst, scenePaint);
            }

            // 1. Organic Starfield & Micro-Dust Particles
            for (int i = 0; i < STAR_COUNT; i++) {
                starY[i] -= starSpeed[i];
                if (starY[i] < 0) {
                    starY[i] = h;
                    starX[i] = (float) (Math.random() * w);
                }
                float twinkle = (float) (Math.sin((nowMs * 0.002) + i) * 0.30 + 0.70);
                int a = (int) (starAlpha[i] * twinkle * 255);
                starfieldPaint.setColor(Color.argb(Math.min(255, Math.max(0, a)), 210, 230, 255));
                canvas.drawCircle(starX[i], starY[i], starRadius[i], starfieldPaint);
            }

            // 2. Slow subtle orbital drift (Lissajous ±55px / ±32px) for 100% OLED burn-in immunity
            double driftT = (nowMs % 180000L) / 1000.0;
            float driftX = (float) (Math.sin(driftT * 0.09) * 55.0 + Math.sin(driftT * 0.20) * 20.0);
            float driftY = (float) (Math.cos(driftT * 0.07) * 32.0 + Math.sin(driftT * 0.15) * 15.0);

            float cx = (w / 2f) + driftX;
            float cy = (h / 2f) - 25f + driftY;

            // 3. Luxurious Cinematic Breathing Palette (Cyan-Sapphire-Amethyst-Warm Gold-Emerald)
            float baseHue = (float) ((nowMs % 48000L) / 48000.0 * 360.0);
            float auroraHue = (baseHue + 45f) % 360f;
            int cCore = Color.HSVToColor(new float[]{baseHue, 0.70f, 0.95f});
            int cOuter = Color.HSVToColor(new float[]{auroraHue, 0.85f, 0.85f});

            // 4. Subtle, ethereal volumetric ambient fog (soft filmic diffusion behind signature)
            float pulse = (float) (Math.sin(nowMs * 0.0009) * 0.04 + 1.0);
            float sigSize = Math.min(w, h) * 0.76f * pulse; // Monumental centerpiece filling 76% of screen
            float nebulaR = sigSize * 0.95f;

            auraPaint.setShader(new RadialGradient(
                    cx, cy, nebulaR,
                    new int[]{
                            Color.argb(0x35, Color.red(cCore), Color.green(cCore), Color.blue(cCore)),
                            Color.argb(0x16, Color.red(cOuter), Color.green(cOuter), Color.blue(cOuter)),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.58f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, nebulaR, auraPaint);

            // 5. Centered Giant Signature Watermark with breathing frosted-crystal luminescence
            float sigLeft = cx - sigSize / 2f;
            float sigTop = cy - sigSize / 2f;

            // Soft breathing alpha: 0.20 to 0.34
            float breathAlpha = (float) (Math.sin(nowMs * 0.0008) * 0.07 + 0.27);
            int sigAlphaInt = Math.min(255, Math.max(0, (int) (breathAlpha * 255)));

            if (signatureBitmap != null) {
                bitmapPaint.setAlpha(sigAlphaInt);
                Rect src = new Rect(0, 0, signatureBitmap.getWidth(), signatureBitmap.getHeight());
                RectF dst = new RectF(sigLeft, sigTop, sigLeft + sigSize, sigTop + sigSize);
                canvas.drawBitmap(signatureBitmap, src, dst, bitmapPaint);
            } else if (fallbackLogo != null) {
                fallbackLogo.setAlpha(sigAlphaInt);
                int leftI = Math.round(sigLeft);
                int topI = Math.round(sigTop);
                int sizeI = Math.round(sigSize);
                fallbackLogo.setBounds(leftI, topI, leftI + sizeI, topI + sizeI);
                fallbackLogo.draw(canvas);
            }

            // 6. Typographic Hero Clock (Poppins Black, Crisp White with subtle neon halo)
            Date now = new Date();
            String timeStr = timeFmt.format(now);
            String dateStr = dateFmt.format(now).toUpperCase(new Locale("tr", "TR"));

            float clockY = cy + (sigSize * 0.34f);
            clockPaint.setShadowLayer(32f, 0f, 4f, Color.argb(0x77, Color.red(cCore), Color.green(cCore), Color.blue(cCore)));
            float tw = clockPaint.measureText(timeStr);
            canvas.drawText(timeStr, cx - tw / 2f, clockY, clockPaint);

            // 7. Date string below clock
            datePaint.setShadowLayer(14f, 0f, 2f, Color.parseColor("#99000000"));
            float dw = datePaint.measureText(dateStr);
            canvas.drawText(dateStr, cx - dw / 2f, clockY + 34f, datePaint);

            // 8. Rotating Cinema Poetry & Philosophy (Prominent, High-Readability Font)
            String currentQuote = cinemaMoments[quoteIdx].quote;
            int qA = (int) (quoteAlpha * 240);
            quotePaint.setColor(Color.argb(Math.min(255, Math.max(0, qA)), 241, 245, 249));
            quotePaint.setShadowLayer(18f, 0f, 3f, Color.parseColor("#EE000000"));
            float qw = quotePaint.measureText(currentQuote);
            canvas.drawText(currentQuote, cx - qw / 2f, clockY + 74f, quotePaint);

            if (isRunning) {
                postInvalidateOnAnimation();
            }
        }
    }
}
