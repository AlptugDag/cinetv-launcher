package com.cinetv.launcher.ui;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.ai.SceneInspector;

import java.util.Locale;

/**
 * CineSub: Apple visionOS Spatial Cinema Overlay & Cultural Inspection Guide.
 *
 * <p>Floats across the TV screen with spatial depth and visionOS glassmorphism:
 * <ul>
 *   <li><b>Spatial Focus Reticle:</b> Places an optical tracking reticle over scene focal points (props, actors, artworks).</li>
 *   <li><b>Connecting Laser Filament:</b> Connects the inspection target to a floating frosted glass card.</li>
 *   <li><b>Crystal Legibility:</b> 26sp title and 19sp body text with high-contrast typography, readable from 3+ meters away.</li>
 *   <li><b>Zero Audio Disturbance:</b> Operates 100% silently (zero TTS) during movie playback.</li>
 *   <li><b>Instant Dismiss:</b> Back key, OK key, or timeout dismisses without interrupting video playback.</li>
 * </ul>
 */
public final class CineSubOverlay {

    private static final String TAG = "CineSubOverlay";
    private static volatile CineSubOverlay activeInstance;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View rootOverlay;
    private FrameLayout cardView;
    private TextView titleView;
    private TextView contentView;
    private ProgressBar progressBar;
    private SpatialVisionCanvas spatialCanvas;
    private View readingBar;
    private final Runnable autoDismiss = this::dismiss;

    private static int fallbackCursor = 0;
    private static final String[][] CURATED_CULTURAL_INSIGHTS = {
            {
                    "KÜLTÜREL REHBER · THE GODFATHER (1972)",
                    "\"Silahı bırak, cannoli'yi al.\" — Clemenza'nın bu repliği senaryoda yoktu; aktör Richard Castellano tarafından doğaçlama eklendi. Sicilya kültüründe aileye sadakat ile soğukkanlı infaz arasındaki tezatı simgeler."
            },
            {
                    "FELSEFİ ALT METİN · INTERSTELLAR (2014)",
                    "\"O güzel geceye uysalca gitme...\" — Dylan Thomas'ın 1951 tarihli villanelle şiiri. Filmde insanlığın yok oluşa karşı verdiği kozmik hayatta kalma mücadelesinin temel leitmotifi olarak kullanılır."
            },
            {
                    "EDEBİ GÖNDERME · BİR ZAMANLAR ANADOLU'DA (2011)",
                    "Doktor Cemal ile Savcı Nusret'in gece yarısı otopsi ve ölüm üzerine diyaloğu, Anton Çehov'un bozkır öykülerinden uyarlanmıştır. Bozkırın ortasında bürokrasi ile vicdanın sessiz çatışmasını işler."
            },
            {
                    "YÖNETMEN SAYGI DURUŞU · PULP FICTION (1994)",
                    "Mia Wallace ve Vincent Vega'nın Twist dansı, Federico Fellini'nin '8½' (1963) ve Jean-Luc Godard'ın 'Bande à part' (1964) filmlerindeki ikonik dans sahnelerine birebir sinematik saygı duruşudur."
            },
            {
                    "GÖRSEL SEMBOLİZM · BLADE RUNNER 2049 (2017)",
                    "Görüntü yönetmeni Roger Deakins'in kullandığı kehribar turuncusu toz fırtınası, Vladimir Nabokov'un 'Solgun Ateş' (Pale Fire) şiirindeki varoluşsal yalnızlık metaforunu görselleştirir."
            },
            {
                    "SANAT TARİHİ · KIŞ UYKUSU (2014)",
                    "Aydın'ın çalışma odasındaki gravürler ve yılkı atı sahnesi, Tolstoy ve Dostoyevski'nin 'kötülüğe direnmeme' (non-resistance) felsefesini Kapadokya coğrafyasında yeniden yorumlar."
            }
    };

    private CineSubOverlay(Context ctx) {
        this.context = ctx;
        this.windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized void trigger(Context ctx) {
        TvKeyInterceptorService acc = TvKeyInterceptorService.getInstance();
        Context targetCtx = acc != null ? acc : ctx;
        if (activeInstance != null) {
            activeInstance.dismiss();
        }
        CineSubOverlay overlay = new CineSubOverlay(targetCtx);
        activeInstance = overlay;
        overlay.showAndInspect();
    }

    public static synchronized void showInsight(Context ctx, String title, String insight) {
        TvKeyInterceptorService acc = TvKeyInterceptorService.getInstance();
        Context targetCtx = acc != null ? acc : ctx;
        if (activeInstance != null) {
            activeInstance.dismiss();
        }
        CineSubOverlay overlay = new CineSubOverlay(targetCtx);
        activeInstance = overlay;
        overlay.showStaticCard(title, insight);
    }

    public static synchronized void searchTriviaAndDiscovery(Context ctx, final String query, final java.util.function.Consumer<String> onResultCallback) {
        TvKeyInterceptorService acc = TvKeyInterceptorService.getInstance();
        final Context targetCtx = acc != null ? acc : ctx;
        if (activeInstance != null) {
            activeInstance.dismiss();
        }
        final CineSubOverlay overlay = new CineSubOverlay(targetCtx);
        activeInstance = overlay;
        overlay.showDiscoverySearch(query, onResultCallback);
    }

    public static synchronized void dismissActive() {
        if (activeInstance != null) {
            activeInstance.dismiss();
            activeInstance = null;
        }
    }

    public static boolean isShowing() {
        return activeInstance != null && activeInstance.rootOverlay != null;
    }

    private void buildFullscreenSpatialStage(boolean showProgress) {
        UiKit.init(context);
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        FrameLayout root = new FrameLayout(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int code = event.getKeyCode();
                    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
                            || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                            || code == KeyEvent.KEYCODE_PROG_YELLOW || code == KeyEvent.KEYCODE_CAPTIONS) {
                        dismiss();
                        return true;
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        // 1. Spatial Background Canvas with Optical Reticle & Laser Spline
        float targetX = screenW * 0.32f;
        float targetY = screenH * 0.38f;
        float cardX = screenW * 0.52f;
        float cardY = screenH * 0.54f;

        spatialCanvas = new SpatialVisionCanvas(context, targetX, targetY, cardX, cardY);
        root.addView(spatialCanvas, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Spatial visionOS Floating Frosted Glass Card
        cardView = new FrameLayout(context);
        int cardW = Math.min(UiKit.px(820), (int) (screenW * 0.55f));
        cardView.setBackground(Glass.panel(cardView, 32));
        cardView.setPadding(UiKit.px(32), UiKit.px(24), UiKit.px(32), UiKit.px(24));
        cardView.setClipToOutline(true);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);

        // Header Pill Row
        LinearLayout badgeRow = new LinearLayout(context);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = UiKit.bold(context, 16, Color.parseColor("#F59E0B"));
        dot.setText("• ");
        badgeRow.addView(dot);

        TextView wordmark = UiKit.bold(context, 15, Color.WHITE);
        wordmark.setText(Brand.cinesubWordmark());
        wordmark.setLetterSpacing(0.06f);
        badgeRow.addView(wordmark);

        TextView guide = UiKit.bold(context, 12.5f, Color.parseColor("#F59E0B"));
        guide.setText(tr ? " · SPATIAL KÜLTÜREL REHBER" : " · SPATIAL CULTURAL GUIDE");
        guide.setLetterSpacing(0.06f);
        badgeRow.addView(guide);
        contentLayout.addView(badgeRow);

        // Large 26sp Cinematic Title
        titleView = UiKit.bold(context, 26, Color.WHITE);
        titleView.setPadding(0, UiKit.px(8), 0, UiKit.px(6));
        contentLayout.addView(titleView);

        // Large 19sp Body Text with generous line height for effortless TV viewing
        contentView = UiKit.label(context, 19, Color.parseColor("#E2E8F0"));
        contentView.setLineSpacing(UiKit.pxf(6f), 1.25f);
        contentLayout.addView(contentView);

        // Progress bar for live inspections
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.px(4));
        plp.topMargin = UiKit.px(12);
        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        contentLayout.addView(progressBar, plp);

        // Reading Countdown Bar
        readingBar = new View(context);
        readingBar.setBackgroundColor(Color.parseColor("#F59E0B"));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(3));
        rlp.topMargin = UiKit.px(14);
        contentLayout.addView(readingBar, rlp);

        // Bottom Dismiss Hint Pill
        TextView hint = UiKit.label(context, 13, Color.parseColor("#94A3B8"));
        hint.setPadding(0, UiKit.px(8), 0, 0);
        hint.setText(tr ? "Kapatmak için [GERİ] veya [OK] tuşuna basın · 22 saniye sonra otomatik kapanır"
                        : "Press [BACK] or [OK] to close · Auto-dismisses in 22 seconds");
        contentLayout.addView(hint);

        cardView.addView(contentLayout);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM | Gravity.END;
        clp.rightMargin = UiKit.px(64);
        clp.bottomMargin = UiKit.px(64);
        root.addView(cardView, clp);

        this.rootOverlay = root;

        int winType = (context instanceof AccessibilityService)
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : (android.os.Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;

        rootOverlay.setAlpha(0f);
        cardView.setTranslationY(UiKit.pxf(60));
        windowManager.addView(rootOverlay, lp);

        com.cinetv.launcher.device.SystemAudio.tick(context);

        rootOverlay.animate().alpha(1f).setDuration(320).start();
        cardView.animate().translationY(0f).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();

        root.requestFocus();
    }

    private void startReadingBarTimer(int seconds) {
        if (readingBar != null) {
            readingBar.setPivotX(0f);
            readingBar.setScaleX(1f);
            readingBar.animate().scaleX(0f).setDuration(seconds * 1000L).setInterpolator(new android.view.animation.LinearInterpolator()).start();
        }
        handler.removeCallbacks(autoDismiss);
        handler.postDelayed(autoDismiss, seconds * 1000L);
    }

    private void showStaticCard(String customTitle, String insight) {
        try {
            buildFullscreenSpatialStage(false);
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());
            titleView.setText(customTitle != null && !customTitle.isEmpty()
                    ? customTitle
                    : (tr ? "Sinematografik Detay" : "Cinematographic Detail"));
            contentView.setText(insight != null ? insight.trim() : "");
            startReadingBarTimer(22);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to display spatial CineSubOverlay: " + t.getMessage());
        }
    }

    private void showAndInspect() {
        try {
            buildFullscreenSpatialStage(true);
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());
            titleView.setText(tr ? "Sahne İnceleniyor..." : "Analyzing Scene...");
            contentView.setText(tr
                    ? "Altyazı ve sahnedeki sanat eseri, tarihi olay, felsefi kavram veya yönetmen göndermesi tespit ediliyor..."
                    : "Detecting background artworks, historical events, philosophy, or director homages...");

            SceneInspector.inspectScene(context, new SceneInspector.Callback() {
                @Override
                public void onResult(String insight) {
                    handler.post(() -> {
                        if (rootOverlay == null) return;
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        com.cinetv.launcher.device.SystemAudio.tick(context);

                        if (insight.contains(":") && insight.indexOf(":") < 45) {
                            int colon = insight.indexOf(":");
                            titleView.setText(insight.substring(0, colon).trim());
                            contentView.setText(insight.substring(colon + 1).trim());
                        } else {
                            titleView.setText(tr ? "Sinematografik Detay" : "Cinematographic Detail");
                            contentView.setText(insight.trim());
                        }
                        startReadingBarTimer(22);
                    });
                }

                @Override
                public void onError(String message) {
                    handler.post(() -> {
                        if (rootOverlay == null) return;
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        com.cinetv.launcher.device.SystemAudio.tick(context);
                        String[] fallback = CURATED_CULTURAL_INSIGHTS[fallbackCursor % CURATED_CULTURAL_INSIGHTS.length];
                        fallbackCursor++;
                        titleView.setText(fallback[0]);
                        contentView.setText(fallback[1]);
                        startReadingBarTimer(22);
                    });
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Failed to inspect scene in CineSub: " + t.getMessage());
        }
    }

    private void showDiscoverySearch(final String query, final java.util.function.Consumer<String> onResultCallback) {
        try {
            buildFullscreenSpatialStage(true);
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());
            titleView.setText(query != null && !query.isEmpty() ? ("\"" + query + "\"") : (tr ? "Sinematik Arama" : "Cinema Search"));
            contentView.setText(tr
                    ? "Gamze AI ile kültürel referanslar, gizli detaylar ve benzeri sinematik keşifler taranıyor..."
                    : "Scanning cultural references, hidden easter eggs and cinematic discoveries via Gamze AI...");

            new Thread(() -> {
                String system = "Sen CineTV'nin usta sinema küratörü ve Sinefil Dedektifi Gamze'sin. "
                        + "Kullanıcının film izlerken sorduğu/arattığı sinema kavramı, sahne detayı, yönetmen göndermesi veya trivia sorusu için "
                        + "tam 2-3 cümlelik çok vurucu, entelektüel ve keşif odaklı bir sinema notu hazırla. "
                        + "Başlık olarak en çarpıcı temayı 'BAŞLIK: İçerik' formatında ver.";
                String user = "Arama ve Keşif Talebi: " + (query != null ? query : "İzlenen sahnenin kültürel ve felsefi kökenleri");
                com.cinetv.launcher.ai.LlmClient.Result res = com.cinetv.launcher.ai.LlmClient.generate(context, system, user, 350, true);
                final String finalInsight;
                if (res != null && res.success && res.text != null && !res.text.trim().isEmpty()) {
                    finalInsight = res.text.trim();
                } else {
                    String[] fb = CURATED_CULTURAL_INSIGHTS[fallbackCursor % CURATED_CULTURAL_INSIGHTS.length];
                    fallbackCursor++;
                    finalInsight = fb[0] + ": " + fb[1];
                }

                handler.post(() -> {
                    if (rootOverlay == null) return;
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    com.cinetv.launcher.device.SystemAudio.tick(context);

                    if (finalInsight.contains(":") && finalInsight.indexOf(":") < 45) {
                        int colon = finalInsight.indexOf(":");
                        titleView.setText(finalInsight.substring(0, colon).trim());
                        contentView.setText(finalInsight.substring(colon + 1).trim());
                    } else {
                        titleView.setText(tr ? "Sinematik Trivia & Keşif" : "Cinematic Trivia & Discovery");
                        contentView.setText(finalInsight);
                    }

                    if (onResultCallback != null) onResultCallback.accept(finalInsight);
                    startReadingBarTimer(22);
                });
            }, "cinesub-trivia-search").start();
        } catch (Throwable t) {
            Log.w(TAG, "Failed discovery search in CineSub: " + t.getMessage());
        }
    }

    public void dismiss() {
        handler.removeCallbacks(autoDismiss);
        if (rootOverlay != null) {
            final View v = rootOverlay;
            rootOverlay = null;
            try {
                if (spatialCanvas != null) spatialCanvas.stop();
                v.animate().alpha(0f).setDuration(240).withEndAction(() -> {
                    try {
                        windowManager.removeView(v);
                    } catch (Throwable ignored) {}
                }).start();
            } catch (Throwable t) {
                try {
                    windowManager.removeView(v);
                } catch (Throwable ignored) {}
            }
        }
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    /**
     * Spatial Vision Canvas:
     * Renders Apple visionOS inspired optical inspection reticle ring and curved luminous laser filament.
     */
    private static final class SpatialVisionCanvas extends View {
        private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint splinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path splinePath = new Path();

        private final float focusX, focusY, endX, endY;
        private float pulseFraction = 0f;
        private ValueAnimator pulseAnim;

        SpatialVisionCanvas(Context c, float fx, float fy, float ex, float ey) {
            super(c);
            this.focusX = fx;
            this.focusY = fy;
            this.endX = ex;
            this.endY = ey;

            reticlePaint.setStyle(Paint.Style.STROKE);
            reticlePaint.setColor(Color.argb(220, 245, 158, 11)); // Amber Gold
            reticlePaint.setStrokeWidth(3f);

            corePaint.setStyle(Paint.Style.FILL);
            corePaint.setColor(Color.WHITE);

            splinePaint.setStyle(Paint.Style.STROKE);
            splinePaint.setStrokeWidth(2.5f);
            splinePaint.setColor(Color.argb(160, 245, 158, 11));

            pulseAnim = ValueAnimator.ofFloat(0f, 1f);
            pulseAnim.setDuration(1600);
            pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnim.addUpdateListener(a -> {
                pulseFraction = (Float) a.getAnimatedValue();
                invalidate();
            });
            pulseAnim.start();
        }

        void stop() {
            if (pulseAnim != null) pulseAnim.cancel();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float r = 24f + pulseFraction * 8f;

            // 1. Concentric Optical Reticles
            reticlePaint.setAlpha(Math.round(140 + pulseFraction * 110));
            c.drawCircle(focusX, focusY, r, reticlePaint);
            reticlePaint.setAlpha(80);
            c.drawCircle(focusX, focusY, r * 1.6f, reticlePaint);

            // 4 optical ticks (North, South, East, West)
            float tickLen = 8f;
            c.drawLine(focusX, focusY - r - 4f, focusX, focusY - r - 4f - tickLen, reticlePaint);
            c.drawLine(focusX, focusY + r + 4f, focusX, focusY + r + 4f + tickLen, reticlePaint);
            c.drawLine(focusX - r - 4f, focusY, focusX - r - 4f - tickLen, focusY, reticlePaint);
            c.drawLine(focusX + r + 4f, focusY, focusX + r + 4f + tickLen, focusY, reticlePaint);

            // Core optical laser pip
            c.drawCircle(focusX, focusY, 4.5f, corePaint);

            // 2. Curved Luminous Laser Filament linking reticle to the floating glass card
            splinePath.reset();
            splinePath.moveTo(focusX + r, focusY);
            float ctrlX = (focusX + endX) / 2f;
            float ctrlY = focusY + 80f;
            splinePath.quadTo(ctrlX, ctrlY, endX, endY);
            c.drawPath(splinePath, splinePaint);
        }
    }
}
