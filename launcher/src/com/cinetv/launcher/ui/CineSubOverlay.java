package com.cinetv.launcher.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.ai.SceneInspector;

import java.io.File;
import java.util.Locale;

/**
 * CineSub: Interactive Cultural Subtitle & Visual Cinema Guide.
 *
 * <p>Floats as an unobtrusive, liquid-glass cinephile trivia card on the screen edge
 * during playback (Nuvio, Stremio, YouTube, Live TV), explaining cultural references,
 * historical events, philosophical concepts, paintings, food, or director homages
 * without pausing the video or stealing input focus.
 */
public final class CineSubOverlay {

    private static final String TAG = "CineSubOverlay";
    private static volatile CineSubOverlay activeInstance;

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View overlayView;
    private TextView titleView;
    private TextView contentView;
    private ProgressBar progressBar;
    private final Runnable autoDismiss = this::dismiss;

    private CineSubOverlay(Context ctx) {
        this.context = ctx;
        this.windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

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

    /**
     * AI-powered Trivia & Cinema Discovery Search.
     * Queries Gamze AI for deep trivia, hidden easter eggs, philosophical subtext, and discovery
     * recommendations, displaying the result silently on the TV top-right liquid glass HUD
     * (never using TTS so movie audio is never interrupted) and returning it to the phone remote.
     */
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
        return activeInstance != null && activeInstance.overlayView != null;
    }

    private void showStaticCard(String customTitle, String insight) {
        try {
            UiKit.init(context);
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());

            FrameLayout card = new FrameLayout(context);
            int cardW = UiKit.px(420);
            card.setBackground(Glass.panel(card, 22));
            card.setPadding(UiKit.px(22), UiKit.px(18), UiKit.px(22), UiKit.px(18));

            LinearLayout contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);

            // Badge Row: Glowing Icon + "✦ CineSUB · KÜLTÜREL REHBER"
            LinearLayout badgeRow = new LinearLayout(context);
            badgeRow.setOrientation(LinearLayout.HORIZONTAL);
            badgeRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView star = UiKit.bold(context, 13, Color.parseColor("#F59E0B"));
            star.setText("• ");
            badgeRow.addView(star);

            TextView wordmark = UiKit.bold(context, 14, UiKit.INK);
            wordmark.setText(Brand.cinesubWordmark());
            wordmark.setLetterSpacing(0.04f);
            badgeRow.addView(wordmark);

            TextView guide = UiKit.bold(context, 11.5f, Color.parseColor("#F59E0B"));
            guide.setText(tr ? " · KÜLTÜREL NOT REHBERİ" : " · CULTURAL GUIDE");
            guide.setLetterSpacing(0.05f);
            badgeRow.addView(guide);
            contentLayout.addView(badgeRow);

            titleView = UiKit.bold(context, 17, UiKit.INK);
            titleView.setText(customTitle != null && !customTitle.isEmpty()
                    ? customTitle
                    : (tr ? "Sinematografik Detay" : "Cinematographic Detail"));
            titleView.setPadding(0, UiKit.px(6), 0, UiKit.px(4));
            contentLayout.addView(titleView);

            contentView = UiKit.label(context, 13, UiKit.INK_DIM);
            contentView.setLineSpacing(UiKit.pxf(3.5f), 1.15f);
            contentView.setText(insight != null ? insight.trim() : "");
            contentLayout.addView(contentView);

            // Razor-thin amber auto-dismiss reading countdown line
            final View readingBar = new View(context);
            readingBar.setBackgroundColor(Color.parseColor("#F59E0B"));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(2));
            rlp.topMargin = UiKit.px(10);
            contentLayout.addView(readingBar, rlp);

            card.addView(contentLayout);
            this.overlayView = card;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    cardW,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    (context instanceof AccessibilityService)
                            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            : (android.os.Build.VERSION.SDK_INT >= 26
                                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : WindowManager.LayoutParams.TYPE_PHONE),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = UiKit.px(36);
            lp.y = UiKit.px(36);

            overlayView.setAlpha(0f);
            overlayView.setTranslationX(UiKit.pxf(60));
            windowManager.addView(overlayView, lp);

            com.cinetv.launcher.device.SystemAudio.tick(context);

            overlayView.animate().alpha(1f).translationX(0f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

            // Animate reading countdown bar over 9 seconds
            readingBar.setPivotX(0f);
            readingBar.animate().scaleX(0f).setDuration(9000).setInterpolator(new android.view.animation.LinearInterpolator()).start();

            // Auto-dismiss after 9 seconds so it never intrudes on film watching
            handler.removeCallbacks(autoDismiss);
            handler.postDelayed(autoDismiss, 9000);

        } catch (Throwable t) {
            Log.w(TAG, "Failed to display static CineSubOverlay: " + t.getMessage());
        }
    }

    private void showAndInspect() {
        try {
            UiKit.init(context);
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());

            FrameLayout card = new FrameLayout(context);
            int cardW = UiKit.px(420);
            card.setBackground(Glass.panel(card, 22));
            card.setPadding(UiKit.px(22), UiKit.px(18), UiKit.px(22), UiKit.px(18));

            LinearLayout contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);

            // Badge Row: Glowing Icon + "✦ CineSUB · KÜLTÜREL REHBER"
            LinearLayout badgeRow = new LinearLayout(context);
            badgeRow.setOrientation(LinearLayout.HORIZONTAL);
            badgeRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView star = UiKit.bold(context, 13, Color.parseColor("#F59E0B"));
            star.setText("• ");
            badgeRow.addView(star);

            TextView wordmark = UiKit.bold(context, 14, UiKit.INK);
            wordmark.setText(Brand.cinesubWordmark());
            wordmark.setLetterSpacing(0.04f);
            badgeRow.addView(wordmark);

            TextView guide = UiKit.bold(context, 11.5f, Color.parseColor("#F59E0B"));
            guide.setText(tr ? " · KÜLTÜREL NOT REHBERİ" : " · CULTURAL GUIDE");
            guide.setLetterSpacing(0.05f);
            badgeRow.addView(guide);
            contentLayout.addView(badgeRow);

            titleView = UiKit.bold(context, 17, UiKit.INK);
            titleView.setText(tr ? "Sahne İnceleniyor..." : "Analyzing Scene...");
            titleView.setPadding(0, UiKit.px(6), 0, UiKit.px(4));
            contentLayout.addView(titleView);

            contentView = UiKit.label(context, 13, UiKit.INK_DIM);
            contentView.setLineSpacing(UiKit.pxf(3.5f), 1.15f);
            contentView.setText(tr
                    ? "Altyazı ve sahnedeki sanat eseri, tarihi olay, felsefi kavram veya yönetmen göndermesi tespit ediliyor..."
                    : "Detecting background artworks, historical events, philosophy, or director homages...");
            contentLayout.addView(contentView);

            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(true);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.px(3));
            plp.topMargin = UiKit.px(10);
            contentLayout.addView(progressBar, plp);

            card.addView(contentLayout);
            this.overlayView = card;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    cardW,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    (context instanceof AccessibilityService)
                            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            : (android.os.Build.VERSION.SDK_INT >= 26
                                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : WindowManager.LayoutParams.TYPE_PHONE),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = UiKit.px(36);
            lp.y = UiKit.px(36);

            overlayView.setAlpha(0f);
            overlayView.setTranslationX(UiKit.pxf(60));
            windowManager.addView(overlayView, lp);

            com.cinetv.launcher.device.SystemAudio.tick(context);

            overlayView.animate().alpha(1f).translationX(0f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

            // Run Gamze Vision & Cultural Scene Inspector
            SceneInspector.inspectScene(context, new SceneInspector.Callback() {
                @Override
                public void onResult(String insight) {
                    handler.post(() -> {
                        if (overlayView == null) return;
                        progressBar.setVisibility(View.GONE);
                        com.cinetv.launcher.device.SystemAudio.tick(context);
                        // Format insight: split title if exists
                        if (insight.contains(":") && insight.indexOf(":") < 40) {
                            int colon = insight.indexOf(":");
                            titleView.setText(insight.substring(0, colon).trim());
                            contentView.setText(insight.substring(colon + 1).trim());
                        } else {
                            titleView.setText(tr ? "Sinematografik Detay" : "Cinematographic Detail");
                            contentView.setText(insight.trim());
                        }

                        final View readingBar = new View(context);
                        readingBar.setBackgroundColor(Color.parseColor("#F59E0B"));
                        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(2));
                        rlp.topMargin = UiKit.px(10);
                        contentLayout.addView(readingBar, rlp);
                        readingBar.setPivotX(0f);
                        readingBar.animate().scaleX(0f).setDuration(9000).setInterpolator(new android.view.animation.LinearInterpolator()).start();

                        // Auto-dismiss after 9 seconds
                        handler.removeCallbacks(autoDismiss);
                        handler.postDelayed(autoDismiss, 9000);
                    });
                }

                @Override
                public void onError(String message) {
                    handler.post(() -> {
                        if (overlayView == null) return;
                        progressBar.setVisibility(View.GONE);
                        com.cinetv.launcher.device.SystemAudio.tick(context);
                        String[] fallback = CURATED_CULTURAL_INSIGHTS[fallbackCursor % CURATED_CULTURAL_INSIGHTS.length];
                        fallbackCursor++;
                        titleView.setText(fallback[0]);
                        contentView.setText(fallback[1]);

                        final View readingBar = new View(context);
                        readingBar.setBackgroundColor(Color.parseColor("#F59E0B"));
                        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(2));
                        rlp.topMargin = UiKit.px(10);
                        contentLayout.addView(readingBar, rlp);
                        readingBar.setPivotX(0f);
                        readingBar.animate().scaleX(0f).setDuration(9000).setInterpolator(new android.view.animation.LinearInterpolator()).start();

                        handler.removeCallbacks(autoDismiss);
                        handler.postDelayed(autoDismiss, 9000);
                    });
                }
            });

        } catch (Throwable t) {
            Log.w(TAG, "Failed to display CineSubOverlay: " + t.getMessage());
        }
    }

    private void showDiscoverySearch(final String query, final java.util.function.Consumer<String> onResultCallback) {
        try {
            UiKit.init(context);
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());

            FrameLayout card = new FrameLayout(context);
            int cardW = UiKit.px(440);
            card.setBackground(Glass.panel(card, 22));
            card.setPadding(UiKit.px(22), UiKit.px(18), UiKit.px(22), UiKit.px(18));

            LinearLayout contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);

            // Badge Row: Wordmark + "TRIVIA & KEŞİF ARAMASI"
            LinearLayout badgeRow = new LinearLayout(context);
            badgeRow.setOrientation(LinearLayout.HORIZONTAL);
            badgeRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView star = UiKit.bold(context, 13, Color.parseColor("#F59E0B"));
            star.setText("• ");
            badgeRow.addView(star);

            TextView wordmark = UiKit.bold(context, 14, UiKit.INK);
            wordmark.setText(Brand.cinesubWordmark());
            wordmark.setLetterSpacing(0.04f);
            badgeRow.addView(wordmark);

            TextView guide = UiKit.bold(context, 11.5f, Color.parseColor("#F59E0B"));
            guide.setText(tr ? " · SİNEMA TRIVIA & KEŞİF" : " · CINEMA TRIVIA & DISCOVERY");
            guide.setLetterSpacing(0.05f);
            badgeRow.addView(guide);
            contentLayout.addView(badgeRow);

            titleView = UiKit.bold(context, 17, UiKit.INK);
            titleView.setText(query != null && !query.isEmpty() ? ("\"" + query + "\"") : (tr ? "Sinematografik Arama" : "Cinema Search"));
            titleView.setPadding(0, UiKit.px(6), 0, UiKit.px(4));
            contentLayout.addView(titleView);

            contentView = UiKit.label(context, 13, UiKit.INK_DIM);
            contentView.setLineSpacing(UiKit.pxf(3.5f), 1.15f);
            contentView.setText(tr
                    ? "Gamze AI ile kültürel referanslar, gizli detaylar ve benzeri sinematik keşifler taranıyor..."
                    : "Scanning cultural references, hidden easter eggs and cinematic discoveries via Gamze AI...");
            contentLayout.addView(contentView);

            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(true);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.px(3));
            plp.topMargin = UiKit.px(10);
            contentLayout.addView(progressBar, plp);

            card.addView(contentLayout);
            this.overlayView = card;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    cardW,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    (context instanceof AccessibilityService)
                            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            : (android.os.Build.VERSION.SDK_INT >= 26
                                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : WindowManager.LayoutParams.TYPE_PHONE),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = UiKit.px(36);
            lp.y = UiKit.px(36);

            overlayView.setAlpha(0f);
            overlayView.setTranslationX(UiKit.pxf(60));
            windowManager.addView(overlayView, lp);

            com.cinetv.launcher.device.SystemAudio.tick(context);

            overlayView.animate().alpha(1f).translationX(0f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

            // Perform Gamze AI trivia search in background thread (ZERO TTS!)
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
                    if (overlayView == null) return;
                    progressBar.setVisibility(View.GONE);
                    com.cinetv.launcher.device.SystemAudio.tick(context);

                    if (finalInsight.contains(":") && finalInsight.indexOf(":") < 45) {
                        int colon = finalInsight.indexOf(":");
                        titleView.setText(finalInsight.substring(0, colon).trim());
                        contentView.setText(finalInsight.substring(colon + 1).trim());
                    } else {
                        titleView.setText(tr ? "Sinematik Trivia & Keşif" : "Cinematic Trivia & Discovery");
                        contentView.setText(finalInsight);
                    }

                    final View readingBar = new View(context);
                    readingBar.setBackgroundColor(Color.parseColor("#F59E0B"));
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(2));
                    rlp.topMargin = UiKit.px(10);
                    contentLayout.addView(readingBar, rlp);
                    readingBar.setPivotX(0f);
                    readingBar.animate().scaleX(0f).setDuration(12000).setInterpolator(new android.view.animation.LinearInterpolator()).start();

                    if (onResultCallback != null) {
                        onResultCallback.accept(finalInsight);
                    }

                    handler.removeCallbacks(autoDismiss);
                    handler.postDelayed(autoDismiss, 12000);
                });
            }, "cinesub-trivia-search").start();

        } catch (Throwable t) {
            Log.w(TAG, "Failed to display discovery search CineSub: " + t.getMessage());
        }
    }

    public void dismiss() {
        handler.removeCallbacks(autoDismiss);
        if (overlayView != null) {
            final View v = overlayView;
            overlayView = null;
            try {
                v.animate().alpha(0f).translationX(UiKit.pxf(60)).setDuration(240).withEndAction(() -> {
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
}
