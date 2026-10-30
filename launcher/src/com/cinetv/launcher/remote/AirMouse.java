package com.cinetv.launcher.remote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * The phone as an air mouse: moving the phone (or a finger on its touchpad) moves a pointer over any app
 * on the TV, a tap clicks where it points, two fingers scroll. The pointer is a small overlay that never
 * takes touches itself; clicks and scrolls are real touches injected through the key bridge.
 * It fades away a few seconds after the phone stops moving it.
 */
public final class AirMouse {
    private static final long HIDE_AFTER_MS = 6000;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Pointer view;
    private static WindowManager.LayoutParams lp;
    private static int width = 1920, height = 1080;
    private static float targetX = 960, targetY = 540;
    private static float currentX = 960, currentY = 540;
    private static int pointerSize = 52;
    private static boolean animating = false;
    private static final Runnable hide = AirMouse::hideNow;

    private static final android.view.Choreographer.FrameCallback frameCallback = new android.view.Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (view == null || lp == null) {
                animating = false;
                return;
            }
            float dx = targetX - currentX;
            float dy = targetY - currentY;
            if (Math.abs(dx) > 0.4f || Math.abs(dy) > 0.4f) {
                // Smooth VR beam lerp interpolation (eliminates gyro sensor jitter)
                currentX += dx * 0.42f;
                currentY += dy * 0.42f;
                lp.x = Math.round(currentX - pointerSize / 2f);
                lp.y = Math.round(currentY - pointerSize / 2f);
                try {
                    ((WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE)).updateViewLayout(view, lp);
                } catch (Throwable ignored) {}
                android.view.Choreographer.getInstance().postFrameCallback(this);
            } else {
                currentX = targetX;
                currentY = targetY;
                lp.x = Math.round(currentX - pointerSize / 2f);
                lp.y = Math.round(currentY - pointerSize / 2f);
                try {
                    ((WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE)).updateViewLayout(view, lp);
                } catch (Throwable ignored) {}
                animating = false;
            }
        }
    };

    private AirMouse() {}

    /** Moves the pointer by (dx, dy) in phone-space units. Main thread. */
    public static void move(Context ctx, float dx, float dy) {
        if (!show(ctx)) return;
        float scale = width / 1920f;
        targetX = Math.max(0, Math.min(width - 1, targetX + dx * scale));
        targetY = Math.max(0, Math.min(height - 1, targetY + dy * scale));
        if (!animating) {
            animating = true;
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback);
        }
        keepAlive();
    }

    /** A tap where the pointer is. Any thread. */
    public static void click(final Context ctx) {
        final int cx = Math.round(currentX), cy = Math.round(currentY);
        main.post(() -> {
            if (view != null) view.pulse();
            keepAlive();
        });
        new Thread(() -> {
            if (KeyBridge.tap(ctx, cx, cy)) return;
            com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
            if (svc != null && svc.dispatchTap(cx, cy)) return;
            final com.cinetv.launcher.MainActivity ma = com.cinetv.launcher.MainActivity.instance;
            if (ma != null && com.cinetv.launcher.MainActivity.isInFront()) {
                main.post(() -> {
                    long now = android.os.SystemClock.uptimeMillis();
                    android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, cx, cy, 0);
                    android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, cx, cy, 0);
                    ma.dispatchTouchEvent(down);
                    ma.dispatchTouchEvent(up);
                    down.recycle();
                    up.recycle();
                });
            }
        }, "cinetv-mouse").start();
    }

    /** Scrolls the page under the pointer: a short swipe (positive dy moves the content up). Any thread. */
    public static void scroll(final Context ctx, final float dy) {
        final int cx = Math.round(currentX), cy = Math.round(currentY);
        final int dist = Math.round(Math.max(-height / 3f, Math.min(height / 3f, -dy * (width / 1920f) * 3f)));
        final int ty = Math.max(1, Math.min(height - 2, cy + dist));
        main.post(AirMouse::keepAlive);
        new Thread(() -> {
            if (KeyBridge.swipe(ctx, cx, cy, cx, ty, 180)) return;
            com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
            if (svc != null) svc.dispatchSwipe(cx, cy, cx, ty, 180);
        }, "cinetv-mouse").start();
    }

    public static void off(Context ctx) {
        main.post(AirMouse::hideNow);
    }

    private static void keepAlive() {
        main.removeCallbacks(hide);
        main.postDelayed(hide, HIDE_AFTER_MS);
    }

    private static boolean show(Context ctx) {
        if (view != null) return true;
        try {
            com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
            boolean canAppOverlay = android.provider.Settings.canDrawOverlays(ctx);
            Context hostCtx = (!canAppOverlay && svc != null) ? svc : ctx.getApplicationContext();
            WindowManager wm = (WindowManager) hostCtx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics m = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(m);
            width = m.widthPixels;
            height = m.heightPixels;
            targetX = currentX = width / 2f;
            targetY = currentY = height / 2f;
            pointerSize = Math.max(40, Math.round(width / 1920f * 52));
            int winType = (!canAppOverlay && svc != null)
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : (Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
            lp = new WindowManager.LayoutParams(pointerSize, pointerSize, winType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Math.round(currentX - pointerSize / 2f);
            lp.y = Math.round(currentY - pointerSize / 2f);
            Pointer p = new Pointer(hostCtx);
            wm.addView(p, lp);
            view = p;
            return true;
        } catch (Throwable t) {
            view = null;
            return false; // no display-over-apps permission: the touchpad still works as arrows
        }
    }

    private static void hideNow() {
        if (view == null) return;
        try {
            ((WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(view);
        } catch (Throwable ignored) {
        }
        view = null;
        animating = false;
    }

    /**
     * Futuristic VR Laser Game Controller Reticle:
     * Sleek optical targeting ring, luminous halo, precision laser core dot, and expanding pulse shockwave.
     */
    private static final class Pointer extends View {
        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float rippleProgress = 1f;

        Pointer(Context c) {
            super(c);
            haloPaint.setStyle(Paint.Style.STROKE);
            haloPaint.setColor(Color.argb(100, 56, 189, 248)); // luminous cyan

            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setColor(Color.argb(235, 255, 255, 255)); // sleek white glass ring

            corePaint.setStyle(Paint.Style.FILL);
            corePaint.setColor(Color.WHITE);

            ripplePaint.setStyle(Paint.Style.STROKE);
            ripplePaint.setColor(Color.argb(220, 52, 211, 153)); // green/cyan confirmation shockwave
        }

        void pulse() {
            rippleProgress = 0f;
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(320);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                rippleProgress = (Float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w * 0.5f, cy = h * 0.5f;
            float r = Math.min(cx, cy) * 0.72f;

            // 1. Soft glowing outer halo
            haloPaint.setStrokeWidth(w * 0.16f);
            c.drawCircle(cx, cy, r * 0.85f, haloPaint);

            // 2. Precision targeting ring
            ringPaint.setStrokeWidth(w * 0.055f);
            c.drawCircle(cx, cy, r, ringPaint);

            // 3. 4 optical crosshair pips (North, South, East, West)
            float pipLen = r * 0.28f;
            float pipGap = r * 0.08f;
            c.drawLine(cx, cy - r - pipGap, cx, cy - r - pipGap - pipLen, ringPaint);
            c.drawLine(cx, cy + r + pipGap, cx, cy + r + pipGap + pipLen, ringPaint);
            c.drawLine(cx - r - pipGap, cy, cx - r - pipGap - pipLen, cy, ringPaint);
            c.drawLine(cx + r + pipGap, cy, cx + r + pipGap + pipLen, cy, ringPaint);

            // 4. Center laser pip
            c.drawCircle(cx, cy, r * 0.22f, corePaint);

            // 5. VR trigger shockwave ripple on click
            if (rippleProgress < 1f) {
                float rippleR = r + rippleProgress * r * 0.9f;
                int alpha = Math.round(230 * (1f - rippleProgress));
                ripplePaint.setAlpha(alpha);
                ripplePaint.setStrokeWidth(w * 0.045f * (1f - rippleProgress * 0.5f));
                c.drawCircle(cx, cy, rippleR, ripplePaint);
            }
        }
    }
}
