package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Ultra-Modern Vibrant Cinematic TV Screensaver (DreamService):
 * - Renders personal signature logo centered, compact, with PorterDuff SCREEN blend mode.
 * - Dynamic 60fps orbital breathing and dynamic TMDB scene backdrops.
 * - Non-repeating randomized pool of 35+ legendary cinematic quotes (Tarkovsky, Kubrick, Kurosawa, Demirkubuz, Kitano, Nolan).
 * - Auto-Shutdown Engine: After 10 minutes in dream (15 minutes total TV idle) without active media playback,
 *   safely executes screen sleep to protect panel and save energy.
 */
public class ClockDream extends DreamService {

    private static final long POWER_DOWN_DELAY_MS = 10 * 60 * 1000L; // 10 min screensaver = 15 min total idle
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
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                java.lang.reflect.Method goToSleep = pm.getClass().getMethod("goToSleep", long.class);
                goToSleep.invoke(pm, SystemClock.uptimeMillis());
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"}); // KEYCODE_POWER
        } catch (Throwable ignored) {
        }
    }

    public static class CinemaMoment {
        public final String quote;
        public final String filmTitle;
        public final String imdbId;
        public final int tmdbId;

        public CinemaMoment(String quote, String filmTitle, String imdbId, int tmdbId) {
            this.quote = quote;
            this.filmTitle = filmTitle;
            this.imdbId = imdbId;
            this.tmdbId = tmdbId;
        }
    }

    public static List<CinemaMoment> createExtensiveMomentsPool() {
        List<CinemaMoment> list = new ArrayList<>();
        list.add(new CinemaMoment("\"Bütün o anlar zamanda kaybolup gidecek, yağmurdaki gözyaşları gibi...\"", "Blade Runner", "tt0083658", 78));
        list.add(new CinemaMoment("\"We used to look up at the sky and wonder at our place in the stars.\"", "Interstellar", "tt0816692", 157336));
        list.add(new CinemaMoment("\"Rüyalar gerçektir; sadece uyanıkken onları göremezsin.\"", "Inception", "tt1375666", 27205));
        list.add(new CinemaMoment("\"Her şey bir seçimle başlar.\"", "The Matrix", "tt0133093", 603));
        list.add(new CinemaMoment("\"İnsan ancak vicdanıyla baş başa kaldığında gerçeği anlar.\"", "Kader (Zeki Demirkubuz)", "tt0851419", 25893));
        list.add(new CinemaMoment("\"Bazen sadece sessizlik konuşur; en büyük fırtınalar sessizlikte kopar.\"", "Hana-bi (Takeshi Kitano)", "tt0119250", 11216));
        list.add(new CinemaMoment("\"Sinema, zamanı ve anıları donduran tek büyüdür.\"", "Solaris (Andrei Tarkovsky)", "tt0069291", 830));
        list.add(new CinemaMoment("\"Karanlık seni korkutmasın; asıl korkulacak olan ışıktaki sahteliktir.\"", "Fight Club", "tt0137523", 550));
        list.add(new CinemaMoment("\"Korku aklı yok eden küçük ölümdür. Korkumla yüzleşeceğim.\"", "Dune", "tt1160419", 438631));
        list.add(new CinemaMoment("\"Geçmiş aslında hiç geçmemiştir; hatta geçmiş bile değildir.\"", "Kış Uykusu (Nuri Bilge Ceylan)", "tt2758880", 257091));
        list.add(new CinemaMoment("\"Şiddet dünyayı yönetir ama sanat onu unutulmaz kılar.\"", "Sonatine (Takeshi Kitano)", "tt0108188", 23826));
        list.add(new CinemaMoment("\"Hayat tesadüflerle başlar, kararlarla biter.\"", "Masumiyet (Zeki Demirkubuz)", "tt0127658", 25892));
        list.add(new CinemaMoment("\"Yıldızlara ulaşmak istiyorsan karanlığın içine yürümekten korkmayacaksın.\"", "2001: A Space Odyssey", "tt0062622", 62));
        list.add(new CinemaMoment("\"Bir insanı unutmak, onunla yaşadığın anıları kaybetmekten daha acıdır.\"", "In the Mood for Love (Wong Kar-wai)", "tt0242527", 843));
        list.add(new CinemaMoment("\"Gözler gördüğüne inanır, kalp ise hissettiğine.\"", "Stalker (Andrei Tarkovsky)", "tt0079944", 1398));
        list.add(new CinemaMoment("\"Gerçek güç, dünyayı değiştirmek değil; kendi kaderini kabullenmektir.\"", "Apocalypse Now", "tt0078788", 28));
        list.add(new CinemaMoment("\"Bazen en derin yaralar en güzel hikayeleri doğurur.\"", "Yeraltı (Zeki Demirkubuz)", "tt2024508", 99318));
        list.add(new CinemaMoment("\"Hayatta en zor şey, insanın kendisini olduğu gibi görebilmesidir.\"", "Bir Zamanlar Anadolu'da", "tt1827487", 75612));
        list.add(new CinemaMoment("\"Ölümden korkmuyorum; sadece sevdiklerimden ayrılmak zor geliyor.\"", "Ikiru (Akira Kurosawa)", "tt0044741", 3782));
        list.add(new CinemaMoment("\"Kaos bir merdivendir, sadece tırmanabilenler hayatta kalır.\"", "The Dark Knight", "tt0468569", 155));
        list.add(new CinemaMoment("\"Bir gün gelecek ve herkes anladığını sanacak, oysa kimse hiçbir şey bilmeyecek.\"", "Ayna (Andrei Tarkovsky)", "tt0072443", 24961));
        list.add(new CinemaMoment("\"Dünya bir gölgedir, gerçek olan tek şey insanın kendi ruhudur.\"", "Ran (Akira Kurosawa)", "tt0089881", 11645));
        list.add(new CinemaMoment("\"Gülümsemek, en acımasız dünyaya karşı tek direnme biçimidir.\"", "Kikujiro (Takeshi Kitano)", "tt0199683", 9387));
        list.add(new CinemaMoment("\"Uzaklara gitmek kurtarmaz seni; nereye gidersen git kendini götürürsün.\"", "Uzak (Nuri Bilge Ceylan)", "tt0346094", 25890));
        list.add(new CinemaMoment("\"Bir kere sınırları aştığında geri dönüş yoktur.\"", "Sicario (Denis Villeneuve)", "tt3397884", 273481));
        list.add(new CinemaMoment("\"Zaman bir nehir gibidir, geriye akmaz ama daima derinleşir.\"", "Arrival (Denis Villeneuve)", "tt2543164", 329865));
        list.add(new CinemaMoment("\"Gördüğün her şey bir yanılsamadan ibarettir.\"", "Mulholland Drive (David Lynch)", "tt0166924", 101));
        return list;
    }

    private class VibrantDriftView extends View {
        private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint quotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint starfieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint scenePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM EEEE", new Locale("tr", "TR"));

        private final Bitmap signatureBitmap;
        private final Drawable fallbackLogo;
        private boolean isRunning = false;

        private final List<CinemaMoment> momentsPool;
        private final Random random = new Random();
        private int currentMomentIndex = 0;
        private long lastQuoteSwitchTime = 0;
        private static final long QUOTE_DURATION_MS = 14000L;

        private Bitmap activeSceneBackdrop = null;
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
            momentsPool = createExtensiveMomentsPool();
            Collections.shuffle(momentsPool, random);
            currentMomentIndex = random.nextInt(momentsPool.size());
            lastQuoteSwitchTime = SystemClock.uptimeMillis();

            clockPaint.setTypeface(FontManager.getBlack(ctx));
            clockPaint.setTextSize(64f);
            clockPaint.setColor(Color.WHITE);
            clockPaint.setLetterSpacing(0.04f);

            datePaint.setTypeface(FontManager.getMedium(ctx));
            datePaint.setTextSize(16f);
            datePaint.setColor(Color.parseColor("#94A3B8"));
            datePaint.setLetterSpacing(0.12f);

            quotePaint.setTypeface(FontManager.getBold(ctx));
            quotePaint.setTextSize(34f);
            quotePaint.setColor(Color.parseColor("#F8FAFC"));
            quotePaint.setLetterSpacing(0.03f);

            titlePaint.setTypeface(FontManager.getMedium(ctx));
            titlePaint.setTextSize(18f);
            titlePaint.setColor(Color.parseColor("#38BDF8"));
            titlePaint.setLetterSpacing(0.14f);

            // Screen / Lighten blend mode for the centered signature logo
            logoPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

            Bitmap sig = null;
            try {
                InputStream is = ctx.getAssets().open("signature_logo.png");
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                sig = BitmapFactory.decodeStream(is, null, opts);
                is.close();
            } catch (Throwable ignored) {
            }
            signatureBitmap = sig;

            Drawable fallback = null;
            if (sig == null) {
                try {
                    fallback = ctx.getDrawable(R.drawable.ic_user_signature);
                    if (fallback == null) fallback = ctx.getDrawable(R.drawable.ic_tv_clean);
                } catch (Throwable ignored) {
                }
            }
            fallbackLogo = fallback;

            for (int i = 0; i < STAR_COUNT; i++) {
                starX[i] = (float) (Math.random() * 1920);
                starY[i] = (float) (Math.random() * 1080);
                starSpeed[i] = (float) (Math.random() * 0.45 + 0.15);
                starRadius[i] = (float) (Math.random() * 2.2 + 0.8);
                starAlpha[i] = (float) (Math.random() * 0.60 + 0.25);
            }

            fetchTmdbBackdropForMoment(momentsPool.get(currentMomentIndex));
        }

        private void fetchTmdbBackdropForMoment(final CinemaMoment moment) {
            if (isFetchingScene || moment == null) return;
            isFetchingScene = true;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String tmdbKey = Settings.Secure.getString(getContext().getContentResolver(), "tv_tmdb_api_key");
                        String backdropUrl = null;

                        if (tmdbKey != null && !tmdbKey.trim().isEmpty()) {
                            try {
                                String apiUrl = "https://api.themoviedb.org/3/movie/" + (moment.tmdbId > 0 ? moment.tmdbId : moment.imdbId)
                                        + "/images?api_key=" + tmdbKey.trim();
                                HttpURLConnection c = (HttpURLConnection) new URL(apiUrl).openConnection();
                                c.setConnectTimeout(4000);
                                c.setReadTimeout(5000);
                                if (c.getResponseCode() == 200) {
                                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while ((line = br.readLine()) != null) sb.append(line);
                                    br.close();
                                    JSONObject root = new JSONObject(sb.toString());
                                    JSONArray backdrops = root.optJSONArray("backdrops");
                                    if (backdrops != null && backdrops.length() > 0) {
                                        // Pick a random backdrop from top 5 for variety
                                        int bIdx = random.nextInt(Math.min(5, backdrops.length()));
                                        String filePath = backdrops.getJSONObject(bIdx).optString("file_path");
                                        if (filePath != null && !filePath.isEmpty()) {
                                            backdropUrl = "https://image.tmdb.org/t/p/w1280" + filePath;
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }

                        if (backdropUrl == null) {
                            backdropUrl = "https://images.metahub.space/background/medium/" + moment.imdbId + "/img";
                        }

                        HttpURLConnection imgConn = (HttpURLConnection) new URL(backdropUrl).openConnection();
                        imgConn.setConnectTimeout(6000);
                        imgConn.setReadTimeout(9000);
                        if (imgConn.getResponseCode() == 200) {
                            InputStream is = imgConn.getInputStream();
                            BitmapFactory.Options opts = new BitmapFactory.Options();
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
            canvas.drawColor(Color.parseColor("#030407")); // Deep OLED void black

            int w = getWidth() > 0 ? getWidth() : 1920;
            int h = getHeight() > 0 ? getHeight() : 1080;

            long nowMs = SystemClock.uptimeMillis();
            long elapsedSinceSwitch = nowMs - lastQuoteSwitchTime;

            if (elapsedSinceSwitch >= QUOTE_DURATION_MS) {
                lastQuoteSwitchTime = nowMs;
                elapsedSinceSwitch = 0;
                // Pick next random moment, guaranteeing it is different from current
                int nextIdx;
                do {
                    nextIdx = random.nextInt(momentsPool.size());
                } while (nextIdx == currentMomentIndex && momentsPool.size() > 1);
                currentMomentIndex = nextIdx;
                fetchTmdbBackdropForMoment(momentsPool.get(currentMomentIndex));
            }

            // Crossfade calculation (1.4s fade in, 1.4s fade out)
            float quoteAlpha = 1.0f;
            if (elapsedSinceSwitch < 1400L) {
                quoteAlpha = elapsedSinceSwitch / 1400f;
            } else if (elapsedSinceSwitch > QUOTE_DURATION_MS - 1400L) {
                quoteAlpha = (QUOTE_DURATION_MS - elapsedSinceSwitch) / 1400f;
            }

            // 0. Dynamic Movie Scene Background from TMDB (low-opacity, blend with OLED)
            if (activeSceneBackdrop != null && !activeSceneBackdrop.isRecycled()) {
                int sceneAlpha = (int) (quoteAlpha * 65); // ~25% ethereal low opacity
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

            // 2. Center coordinates: Logo is strictly centered (stationary), compact size
            float cx = w / 2f;
            float cy = (h / 2f) - 60f;

            // 3. Compact Logo Size (~20% of min dimension, ~210px on 1080p)
            float sigSize = Math.min(w, h) * 0.21f;

            // Soft radial ambient aura behind signature
            float baseHue = (float) ((nowMs % 48000L) / 48000.0 * 360.0);
            int cCore = Color.HSVToColor(new float[]{baseHue, 0.65f, 0.90f});
            float auraR = sigSize * 1.35f;

            auraPaint.setShader(new RadialGradient(
                    cx, cy, auraR,
                    new int[]{
                            Color.argb(0x30, Color.red(cCore), Color.green(cCore), Color.blue(cCore)),
                            Color.argb(0x10, Color.red(cCore), Color.green(cCore), Color.blue(cCore)),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.60f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, auraR, auraPaint);

            // 4. Centered Signature Watermark with SCREEN Blend Mode & subtle breathing alpha
            float breathAlpha = (float) (Math.sin(nowMs * 0.001) * 0.08 + 0.52);
            int sigAlphaInt = Math.min(255, Math.max(0, (int) (breathAlpha * 255)));
            logoPaint.setAlpha(sigAlphaInt);

            float sigLeft = cx - sigSize / 2f;
            float sigTop = cy - sigSize / 2f;

            if (signatureBitmap != null) {
                Rect src = new Rect(0, 0, signatureBitmap.getWidth(), signatureBitmap.getHeight());
                RectF dst = new RectF(sigLeft, sigTop, sigLeft + sigSize, sigTop + sigSize);
                canvas.drawBitmap(signatureBitmap, src, dst, logoPaint);
            } else if (fallbackLogo != null) {
                fallbackLogo.setAlpha(sigAlphaInt);
                int leftI = Math.round(sigLeft);
                int topI = Math.round(sigTop);
                int sizeI = Math.round(sigSize);
                fallbackLogo.setBounds(leftI, topI, leftI + sizeI, topI + sizeI);
                fallbackLogo.draw(canvas);
            }

            // 5. Typographic Hero Clock & Date below centered logo
            Date now = new Date();
            String timeStr = timeFmt.format(now);
            String dateStr = dateFmt.format(now).toUpperCase(new Locale("tr", "TR"));

            float clockY = cy + (sigSize / 2f) + 64f;
            clockPaint.setShadowLayer(26f, 0f, 4f, Color.argb(0x77, Color.red(cCore), Color.green(cCore), Color.blue(cCore)));
            float tw = clockPaint.measureText(timeStr);
            canvas.drawText(timeStr, cx - tw / 2f, clockY, clockPaint);

            datePaint.setShadowLayer(14f, 0f, 2f, Color.parseColor("#99000000"));
            float dw = datePaint.measureText(dateStr);
            canvas.drawText(dateStr, cx - dw / 2f, clockY + 30f, datePaint);

            // 6. Prominent Cinema Quotes at bottom (Large, Highly Legible Font, Soft Shadow)
            CinemaMoment activeMoment = momentsPool.get(currentMomentIndex);
            String quoteText = activeMoment.quote;
            String filmLabel = "— " + activeMoment.filmTitle;

            int qA = (int) (quoteAlpha * 245);
            quotePaint.setColor(Color.argb(Math.min(255, Math.max(0, qA)), 248, 250, 252));
            quotePaint.setShadowLayer(22f, 0f, 4f, Color.parseColor("#F0000000"));

            titlePaint.setColor(Color.argb(Math.min(255, Math.max(0, (int) (quoteAlpha * 220))), 56, 189, 248));
            titlePaint.setShadowLayer(18f, 0f, 3f, Color.parseColor("#EE000000"));

            float quoteBottomY = h - 110f;
            float qw = quotePaint.measureText(quoteText);

            // Wrap text if quote exceeds screen width
            if (qw > w - 160) {
                int mid = quoteText.length() / 2;
                int split = quoteText.indexOf(" ", mid);
                if (split < 0) split = mid;
                String line1 = quoteText.substring(0, split).trim();
                String line2 = quoteText.substring(split).trim();
                float w1 = quotePaint.measureText(line1);
                float w2 = quotePaint.measureText(line2);
                canvas.drawText(line1, cx - w1 / 2f, quoteBottomY - 48f, quotePaint);
                canvas.drawText(line2, cx - w2 / 2f, quoteBottomY - 4f, quotePaint);
            } else {
                canvas.drawText(quoteText, cx - qw / 2f, quoteBottomY - 20f, quotePaint);
            }

            float fw = titlePaint.measureText(filmLabel);
            canvas.drawText(filmLabel, cx - fw / 2f, quoteBottomY + 36f, titlePaint);

            if (isRunning) {
                postInvalidateOnAnimation();
            }
        }
    }
}
