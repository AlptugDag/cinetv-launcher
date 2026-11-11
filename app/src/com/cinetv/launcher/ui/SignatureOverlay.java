package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.TvKeyInterceptorService;

/**
 * A small, non-touchable watermark in the top-right corner when Nuvio is on screen:
 * Alptug Dag's signature in pure white (#FFFFFF) with "hard light" blend mode so it adapts
 * subtly to both bright and dark scenes without ever distracting the viewer.
 */
public final class SignatureOverlay {

    private static View staticView;
    private static WindowManager staticWm;

    private final Context ctx;
    private View instanceView;
    private WindowManager instanceWm;

    public SignatureOverlay(Context ctx) {
        this.ctx = ctx;
    }

    public static void setVisible(Context ctx, boolean show) {
        if (show) showStatic(ctx);
        else hideStatic();
    }

    public void show() {
        if (instanceView != null) return;
        Context host = TvKeyInterceptorService.getInstance();
        if (host == null) host = ctx.getApplicationContext();
        instanceWm = (WindowManager) host.getSystemService(Context.WINDOW_SERVICE);
        if (instanceWm == null) return;
        instanceView = attachOverlay(host, instanceWm);
    }

    public void hide() {
        if (instanceView == null || instanceWm == null) return;
        final View v = instanceView;
        instanceView = null;
        try {
            instanceWm.removeView(v);
        } catch (Throwable ignored) {
        }
    }

    private static void showStatic(Context ctx) {
        if (staticView != null) return;
        Context host = TvKeyInterceptorService.getInstance();
        if (host == null) host = ctx.getApplicationContext();
        staticWm = (WindowManager) host.getSystemService(Context.WINDOW_SERVICE);
        if (staticWm == null) return;
        staticView = attachOverlay(host, staticWm);
    }

    private static void hideStatic() {
        if (staticView == null || staticWm == null) return;
        final View v = staticView;
        staticView = null;
        try {
            staticWm.removeView(v);
        } catch (Throwable ignored) {
        }
    }

    private static View attachOverlay(Context host, WindowManager wm) {
        int type = host instanceof TvKeyInterceptorService
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = UiKit.px(32);
        lp.y = UiKit.px(24);
        lp.setTitle("CineTVSignature");

        HardLightLinearLayout box = new HardLightLinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.END);

        ImageView sig = new HardLightImageView(host);
        sig.setImageBitmap(Brand.tinted(host, Color.WHITE, UiKit.px(320)));
        sig.setScaleType(ImageView.ScaleType.FIT_END);
        sig.setAdjustViewBounds(true);
        sig.setMaxWidth(UiKit.px(148));
        sig.setMaxHeight(UiKit.px(44));
        box.addView(sig, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView by = new TextView(host);
        by.setText("by " + Brand.AUTHOR);
        by.setTextColor(Color.WHITE);
        by.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.pxf(10.5f));
        by.setTypeface(com.cinetv.launcher.FontManager.getMedium(host), Typeface.NORMAL);
        by.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams byLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        byLp.topMargin = UiKit.px(1);
        box.addView(by, byLp);

        // Subtle, unobtrusive presence in both bright and dark scenes via Hard Light blend
        box.setAlpha(0f);

        try {
            wm.addView(box, lp);
            box.animate().alpha(0.38f).setDuration(360).start();
            return box;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyHardLightPaint(Paint paint) {
        if (Build.VERSION.SDK_INT >= 29) {
            paint.setBlendMode(BlendMode.HARD_LIGHT);
        } else {
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        }
    }

    /**
     * Container that composites its children using the "hard light" blend mode on a hardware layer.
     */
    private static final class HardLightLinearLayout extends LinearLayout {
        private final Paint layerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        HardLightLinearLayout(Context context) {
            super(context);
            applyHardLightPaint(layerPaint);
            setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
        }
    }

    /**
     * Pure white signature mark rendered with Hard Light blend mode and no dark drop shadow,
     * ensuring it never looks harsh on either bright or dark frames.
     */
    private static final class HardLightImageView extends ImageView {
        private final Paint blendPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF dstRect = new RectF();
        private Bitmap bitmap;

        HardLightImageView(Context context) {
            super(context);
            applyHardLightPaint(blendPaint);
            setLayerType(LAYER_TYPE_HARDWARE, blendPaint);
        }

        @Override
        public void setImageBitmap(Bitmap bm) {
            this.bitmap = bm;
            super.setImageBitmap(bm);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) {
                super.onDraw(canvas);
                return;
            }
            int vw = getWidth() - getPaddingLeft() - getPaddingRight();
            int vh = getHeight() - getPaddingTop() - getPaddingBottom();
            if (vw <= 0 || vh <= 0) {
                super.onDraw(canvas);
                return;
            }
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            float scale = Math.min(vw / (float) bw, vh / (float) bh);
            float dw = bw * scale;
            float dh = bh * scale;
            float left = getWidth() - getPaddingRight() - dw;
            float top = getPaddingTop() + (vh - dh) * 0.5f;
            dstRect.set(left, top, left + dw, top + dh);
            canvas.drawBitmap(bitmap, null, dstRect, blendPaint);
        }
    }
}
