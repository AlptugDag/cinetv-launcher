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
    private static float x = 960, y = 540;
    private static final Runnable hide = AirMouse::hideNow;

    private AirMouse() {}

    /** Moves the pointer by (dx, dy) in phone-space units (about one TV pixel at 1080p). Main thread. */
    public static void move(Context ctx, float dx, float dy) {
        if (!show(ctx)) return;
        float scale = width / 1920f;
        x = Math.max(0, Math.min(width - 1, x + dx * scale));
        y = Math.max(0, Math.min(height - 1, y + dy * scale));
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        try {
            ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).updateViewLayout(view, lp);
        } catch (Throwable ignored) {
        }
        keepAlive();
    }

    /** A tap where the pointer is. Any thread. */
    public static void click(final Context ctx) {
        final int cx = Math.round(x), cy = Math.round(y);
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
        final int cx = Math.round(x), cy = Math.round(y);
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
            x = width / 2f;
            y = height / 2f;
            int size = Math.round(width / 1920f * 44);
            int winType = (!canAppOverlay && svc != null)
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : (Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
            lp = new WindowManager.LayoutParams(size, size, winType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Math.round(x);
            lp.y = Math.round(y);
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
    }

    /** The arrow: white with a dark rim, readable on any picture; it flashes on a click. */
    private static final class Pointer extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private float flash;

        Pointer(Context c) {
            super(c);
            fill.setColor(Color.WHITE);
            rim.setColor(Color.argb(200, 0, 0, 0));
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeJoin(Paint.Join.ROUND);
        }

        void pulse() {
            flash = 1f;
            animate().cancel();
            setScaleX(0.8f);
            setScaleY(0.8f);
            animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = getWidth();
            rim.setStrokeWidth(s * 0.07f);
            arrow.reset();
            arrow.moveTo(s * 0.1f, s * 0.06f);
            arrow.lineTo(s * 0.1f, s * 0.78f);
            arrow.lineTo(s * 0.3f, s * 0.6f);
            arrow.lineTo(s * 0.45f, s * 0.92f);
            arrow.lineTo(s * 0.58f, s * 0.86f);
            arrow.lineTo(s * 0.44f, s * 0.55f);
            arrow.lineTo(s * 0.72f, s * 0.55f);
            arrow.close();
            if (flash > 0) {
                fill.setColor(Color.rgb(196, 181, 253));
                flash = 0;
                postDelayed(() -> {
                    fill.setColor(Color.WHITE);
                    invalidate();
                }, 160);
            }
            c.drawPath(arrow, rim);
            c.drawPath(arrow, fill);
        }
    }
}
