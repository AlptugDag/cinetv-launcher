package com.cinetv.launcher.ui;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;

import com.cinetv.launcher.device.DeviceTweaks;

/**
 * Alptuğ Dağ's signature in a fixed corner over Nuvio, drawn as an accessibility overlay (no extra
 * permission, never takes focus or touches). Softer while a film plays, so it never competes with
 * the picture.
 */
public final class SignatureOverlay {

    private static final float BROWSING_ALPHA = 0.75f, PLAYING_ALPHA = 0.35f;

    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView view;

    private final Runnable track = new Runnable() {
        @Override
        public void run() {
            if (view == null) return;
            float target = DeviceTweaks.isMediaPlaying(service) ? PLAYING_ALPHA : BROWSING_ALPHA;
            if (Math.abs(view.getAlpha() - target) > 0.01f) view.animate().alpha(target).setDuration(600).start();
            handler.postDelayed(this, 3000);
        }
    };

    public SignatureOverlay(AccessibilityService service) {
        this.service = service;
    }

    public void show() {
        if (view != null) return;
        try {
            UiKit.init(service);
            view = new ImageView(service);
            int topColor = Color.parseColor("#FFF2E6"); // Champagne warmth
            int botColor = Color.parseColor("#FF2B38"); // Subtle Netflix Crimson luster
            view.setImageBitmap(withShadow(Brand.gradientSignature(service, topColor, botColor, UiKit.px(160))));
            view.setAlpha(0f);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(UiKit.px(104), UiKit.px(104),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = UiKit.px(34);
            lp.y = UiKit.px(26);
            ((WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE)).addView(view, lp);
            view.animate().alpha(BROWSING_ALPHA).setDuration(500).start();
            handler.postDelayed(track, 3000);
        } catch (Throwable t) {
            android.util.Log.w("CineTvSignature", "overlay failed: " + t);
            view = null;
        }
    }

    /** A soft dark halo under the white signature, so it reads over bright scenes too. */
    private static android.graphics.Bitmap withShadow(android.graphics.Bitmap sig) {
        int pad = Math.max(4, sig.getWidth() / 14);
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(sig.getWidth() + pad * 2, sig.getHeight() + pad * 2,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint blur = new android.graphics.Paint();
        blur.setMaskFilter(new android.graphics.BlurMaskFilter(pad * 0.8f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        int[] off = new int[2];
        android.graphics.Bitmap halo = sig.extractAlpha(blur, off);
        android.graphics.Paint dark = new android.graphics.Paint();
        dark.setColor(Color.argb(170, 0, 0, 0));
        c.drawBitmap(halo, pad + off[0], pad + off[1], dark);
        c.drawBitmap(sig, pad, pad, null);
        return out;
    }

    public void hide() {
        handler.removeCallbacks(track);
        if (view == null) return;
        try {
            ((WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE)).removeView(view);
        } catch (Throwable ignored) {
        }
        view = null;
    }
}
