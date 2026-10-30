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

    public static synchronized void trigger(Context ctx) {
        if (activeInstance != null) {
            activeInstance.dismiss();
        }
        CineSubOverlay overlay = new CineSubOverlay(ctx);
        activeInstance = overlay;
        overlay.showAndInspect();
    }

    public static synchronized void dismissActive() {
        if (activeInstance != null) {
            activeInstance.dismiss();
            activeInstance = null;
        }
    }

    private void showAndInspect() {
        try {
            UiKit.init(context);
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());

            FrameLayout card = new FrameLayout(context);
            int cardW = UiKit.px(420);
            card.setBackground(Glass.panel(card, 26));
            card.setPadding(UiKit.px(24), UiKit.px(20), UiKit.px(24), UiKit.px(22));

            LinearLayout contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);

            // Badge Row: Glowing Icon + "✦ CINESUB KÜLTÜREL REHBER"
            LinearLayout badgeRow = new LinearLayout(context);
            badgeRow.setOrientation(LinearLayout.HORIZONTAL);
            badgeRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView badge = UiKit.bold(context, 13, Color.parseColor("#F59E0B")); // Solar Amber
            badge.setText(tr ? "✦ CINESUB KÜLTÜREL REHBER" : "✦ CINESUB CULTURAL GUIDE");
            badge.setLetterSpacing(0.06f);
            badgeRow.addView(badge);
            contentLayout.addView(badgeRow);

            titleView = UiKit.bold(context, 19, UiKit.INK);
            titleView.setText(tr ? "Sahne İnceleniyor..." : "Analyzing Scene...");
            titleView.setPadding(0, UiKit.px(8), 0, UiKit.px(6));
            contentLayout.addView(titleView);

            contentView = UiKit.label(context, 14, UiKit.INK_DIM);
            contentView.setLineSpacing(UiKit.pxf(4), 1.15f);
            contentView.setText(tr
                    ? "Altyazı ve sahnedeki sanat eseri, tarihi olay, felsefi kavram veya yönetmen göndermesi tespit ediliyor..."
                    : "Detecting background artworks, historical events, philosophy, or director homages...");
            contentLayout.addView(contentView);

            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(true);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.px(4));
            plp.topMargin = UiKit.px(14);
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
            lp.y = UiKit.px(44);

            overlayView.setAlpha(0f);
            overlayView.setTranslationX(UiKit.pxf(60));
            windowManager.addView(overlayView, lp);

            overlayView.animate().alpha(1f).translationX(0f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

            // Run Gemini Vision & Cultural Scene Inspector
            SceneInspector.inspectScene(context, new SceneInspector.Callback() {
                @Override
                public void onResult(String insight) {
                    handler.post(() -> {
                        if (overlayView == null) return;
                        progressBar.setVisibility(View.GONE);
                        // Format insight: split title if exists
                        if (insight.contains(":") && insight.indexOf(":") < 40) {
                            int colon = insight.indexOf(":");
                            titleView.setText(insight.substring(0, colon).trim());
                            contentView.setText(insight.substring(colon + 1).trim());
                        } else {
                            titleView.setText(tr ? "Sinematografik Detay" : "Cinematographic Detail");
                            contentView.setText(insight.trim());
                        }
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
                        titleView.setText(tr ? "CineSub Notu" : "CineSub Note");
                        contentView.setText(tr
                                ? "Sahne detayı şu an çözümlenemedi. (Gemini AI anahtarınızı Ayarlar'dan kontrol edebilirsiniz)."
                                : message);
                        handler.removeCallbacks(autoDismiss);
                        handler.postDelayed(autoDismiss, 4000);
                    });
                }
            });

        } catch (Throwable t) {
            Log.w(TAG, "Failed to display CineSubOverlay: " + t.getMessage());
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
