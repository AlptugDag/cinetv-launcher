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
    private static int pointerSize = 76;
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
        final com.cinetv.launcher.MainActivity ma = com.cinetv.launcher.MainActivity.instance;
        if (ma != null && com.cinetv.launcher.MainActivity.isInFront()) {
            main.post(() -> {
                if (clickInMainActivity(ma, cx, cy)) return;
                long now = android.os.SystemClock.uptimeMillis();
                android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, cx, cy, 0);
                android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, cx, cy, 0);
                ma.dispatchTouchEvent(down);
                ma.dispatchTouchEvent(up);
                down.recycle();
                up.recycle();
            });
            return;
        }
        new Thread(() -> {
            if (KeyBridge.tap(ctx, cx, cy)) return;
            com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
            if (svc != null && svc.performAirMouseClick(cx, cy)) return;
        }, "cinetv-mouse").start();
    }

    /**
     * Resolves and executes a click at screen coordinates (cx, cy) inside MainActivity:
     * handles GlassKeyboard, ModalHost, open 3D DVD case (DvdOpeningView), 3D DVD shelf (PosterShelf),
     * and any clickable/editable/focusable View in the hierarchy without relying on touch-mode state.
     */
    private static boolean clickInMainActivity(com.cinetv.launcher.MainActivity ma, int cx, int cy) {
        try {
            com.cinetv.launcher.ui.HomeScreen home = ma.getHome();
            if (home != null) {
                // 1. On-screen GlassKeyboard if visible
                if (home.keyboard != null && home.keyboard.getVisibility() == View.VISIBLE) {
                    View kbHit = findClickableViewAt(home.keyboard, cx, cy);
                    if (kbHit != null) {
                        if (kbHit.isFocusable()) kbHit.requestFocus();
                        if (kbHit.performClick()) return true;
                    }
                    if (isPointInsideView(home.keyboard, cx, cy)) {
                        return dispatchSyntheticTouchTo(ma, cx, cy);
                    }
                }
                // 2. Modal dialog if showing
                if (home.modal != null && home.modal.isShowing()) {
                    View modalHit = findClickableViewAt(home.modal, cx, cy);
                    if (modalHit != null && modalHit != home.modal) {
                        if (modalHit.isFocusable()) modalHit.requestFocus();
                        if (modalHit.performClick()) return true;
                        return dispatchSyntheticTouchTo(ma, cx, cy);
                    }
                    home.modal.close();
                    return true;
                }
                // 3. Open 3D DVD case / trailer overlay
                if (home.opening != null && home.opening.getVisibility() == View.VISIBLE) {
                    if (home.opening.handleAirMouseClick(cx, cy)) return true;
                }
            }

            View decor = ma.getWindow() != null ? ma.getWindow().getDecorView() : null;
            if (decor != null) {
                View hit = findClickableViewAt(decor, cx, cy);
                if (hit instanceof com.cinetv.launcher.ui.PosterShelf) {
                    return ((com.cinetv.launcher.ui.PosterShelf) hit).handleAirMouseClick(cx, cy);
                }
                if (hit != null) {
                    if (hit.isFocusable()) hit.requestFocus();
                    if (hit.performClick()) return true;
                }
            }
            if (home != null && home.shelf != null && home.shelf.getVisibility() == View.VISIBLE) {
                if (home.shelf.handleAirMouseClick(cx, cy)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean dispatchSyntheticTouchTo(com.cinetv.launcher.MainActivity ma, int cx, int cy) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, cx, cy, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, cx, cy, 0);
        boolean handled = ma.dispatchTouchEvent(down) | ma.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        return handled;
    }

    private static boolean isPointInsideView(View v, int x, int y) {
        if (v == null || v.getVisibility() != View.VISIBLE || v.getAlpha() <= 0.02f) return false;
        int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float sx = v.getScaleX(), sy = v.getScaleY();
        float cx = loc[0] + w * 0.5f;
        float cy = loc[1] + h * 0.5f;
        float hw = (w * Math.max(0.5f, sx)) * 0.5f;
        float hh = (h * Math.max(0.5f, sy)) * 0.5f;
        return x >= cx - hw && x <= cx + hw && y >= cy - hh && y <= cy + hh;
    }

    private static View findClickableViewAt(View v, int x, int y) {
        if (v == null || v.getVisibility() != View.VISIBLE || !v.isShown() || v.getAlpha() <= 0.02f) return null;
        if (v instanceof com.cinetv.launcher.ui.PosterShelf) {
            return isPointInsideView(v, x, y) ? v : null;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                View child = vg.getChildAt(i);
                View hit = findClickableViewAt(child, x, y);
                if (hit != null) return hit;
            }
        }
        if ((v.isClickable() || v.hasOnClickListeners() || v instanceof android.widget.EditText) && isPointInsideView(v, x, y)) {
            return v;
        }
        return null;
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
        main.post(() -> {
            hideNow();
            // taps left the TV in touch mode, where nothing holds the focus and the arrows seem dead
            com.cinetv.launcher.MainActivity ma = com.cinetv.launcher.MainActivity.instance;
            if (ma != null && com.cinetv.launcher.MainActivity.isInFront()) ma.restoreKeyFocus();
        });
        new Thread(() -> KeyBridge.key(ctx, android.view.KeyEvent.KEYCODE_UNKNOWN, false), "cinetv-mouse-off").start();
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
            pointerSize = Math.max(60, Math.round(width / 1920f * 76));
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
     * Modern Spatial Glow Orb Cursor:
     * Multi-layered radial luminescence (soft cyan-violet atmospheric bloom, bright inner halo,
     * specular frosted rim, and pure white glowing core) with an expanding glow wave on click.
     */
    private static final class Pointer extends View {
        private final Paint outerBloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rippleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rippleRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float rippleProgress = 1f;
        private int cachedW = -1, cachedH = -1;

        Pointer(Context c) {
            super(c);
            outerBloomPaint.setStyle(Paint.Style.FILL);
            innerHaloPaint.setStyle(Paint.Style.FILL);

            glassRimPaint.setStyle(Paint.Style.STROKE);
            glassRimPaint.setColor(Color.argb(135, 224, 242, 254));

            corePaint.setStyle(Paint.Style.FILL);
            rippleFillPaint.setStyle(Paint.Style.FILL);

            rippleRingPaint.setStyle(Paint.Style.STROKE);
            rippleRingPaint.setColor(Color.argb(235, 125, 211, 252));
        }

        private void ensureShaders(int w, int h) {
            if (w == cachedW && h == cachedH) return;
            cachedW = w;
            cachedH = h;
            float cx = w * 0.5f, cy = h * 0.5f;
            float maxR = Math.min(cx, cy);

            // 1. Soft atmospheric outer glow (cyan -> violet -> transparent)
            outerBloomPaint.setShader(new android.graphics.RadialGradient(
                    cx, cy, Math.max(1f, maxR * 0.96f),
                    new int[]{
                            Color.argb(150, 56, 189, 248),
                            Color.argb(85, 139, 92, 246),
                            Color.argb(26, 56, 189, 248),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.36f, 0.72f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));

            // 2. Concentrated inner neon glow halo
            innerHaloPaint.setShader(new android.graphics.RadialGradient(
                    cx, cy, Math.max(1f, maxR * 0.48f),
                    new int[]{
                            Color.argb(235, 186, 230, 253),
                            Color.argb(165, 56, 189, 248),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.45f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));

            // 3. Hot white-cyan core gradient
            corePaint.setShader(new android.graphics.RadialGradient(
                    cx, cy, Math.max(1f, maxR * 0.19f),
                    new int[]{
                            Color.WHITE,
                            Color.argb(250, 224, 242, 254),
                            Color.argb(180, 56, 189, 248)
                    },
                    new float[]{0f, 0.65f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));
        }

        void pulse() {
            rippleProgress = 0f;
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(340);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                rippleProgress = (Float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            ensureShaders(w, h);

            float cx = w * 0.5f, cy = h * 0.5f;
            float maxR = Math.min(cx, cy);

            // Subtle scale flare during click pulse
            float clickFlare = rippleProgress < 1f ? (float) Math.sin(rippleProgress * Math.PI) : 0f;
            float bloomScale = 1f + clickFlare * 0.14f;
            float coreScale = 1f - clickFlare * 0.16f;

            // 1. Soft outer atmospheric glow bloom
            c.save();
            c.scale(bloomScale, bloomScale, cx, cy);
            c.drawCircle(cx, cy, maxR * 0.95f, outerBloomPaint);
            c.restore();

            // 2. Concentrated inner glow halo
            c.drawCircle(cx, cy, maxR * 0.46f * bloomScale, innerHaloPaint);

            // 3. Expanding luminous glow wave on click
            if (rippleProgress < 1f) {
                float rippleR = maxR * (0.24f + rippleProgress * 0.68f);
                int ringAlpha = Math.round(235 * (1f - rippleProgress));
                rippleRingPaint.setAlpha(ringAlpha);
                rippleRingPaint.setStrokeWidth(Math.max(1.5f, w * 0.038f * (1f - rippleProgress * 0.55f)));
                c.drawCircle(cx, cy, rippleR, rippleRingPaint);

                rippleFillPaint.setColor(Color.argb(Math.round(90 * (1f - rippleProgress)), 56, 189, 248));
                c.drawCircle(cx, cy, rippleR, rippleFillPaint);
            }

            // 4. Frosted specular glass rim around the core
            glassRimPaint.setStrokeWidth(Math.max(1.2f, w * 0.024f));
            c.drawCircle(cx, cy, maxR * 0.25f * coreScale, glassRimPaint);

            // 5. Pure white-cyan glowing center orb
            c.save();
            c.scale(coreScale, coreScale, cx, cy);
            c.drawCircle(cx, cy, maxR * 0.18f, corePaint);
            c.restore();
        }
    }
}
