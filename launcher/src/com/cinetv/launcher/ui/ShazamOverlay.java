package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.ai.ShazamRecognizer;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.device.SystemAudio;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Shazam Music Recognition Overlay for CineTV.
 *
 * <p>Features a CineSUB-inspired electric-blue perimeter glow and wave animation around all four
 * edges of the TV screen while listening, paired with a floating Apple tvOS-style Liquid Glass
 * capsule at the bottom of the screen that displays the identified song title, artist, album,
 * and cover artwork for a generous duration (18 seconds).
 */
public final class ShazamOverlay extends FrameLayout {

    private static final String TAG = "ShazamOverlay";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile ShazamOverlay active;

    /** How long the identified song card remains visible at the bottom of the screen (18 seconds). */
    private static final long TRACK_DISPLAY_DURATION_MS = 18_000L;
    private static final long ERROR_DISPLAY_DURATION_MS = 5_200L;

    private enum State {
        LISTENING,
        FOUND,
        NOT_FOUND,
        DISMISSING
    }

    private final WindowManager wm;
    private final BlueGlowEdgeView glowView;
    private final LinearLayout glassCard;
    private final FrameLayout artContainer;
    private final ImageView artImage;
    private final BlueAcousticWaveView waveBars;
    private final TextView eyebrowText;
    private final TextView titleText;
    private final TextView subtitleText;
    private final TextView syncBadgeText;

    private State state = State.LISTENING;
    private boolean dismissed = false;
    private final Runnable autoDismissRunnable = this::dismiss;

    private ShazamOverlay(Context ctx) {
        super(ctx);
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        setFocusable(false);
        setClickable(false);
        setBackgroundColor(Color.TRANSPARENT);

        // 1. Full-screen CineSUB-style blue perimeter glow & acoustic waves
        glowView = new BlueGlowEdgeView(ctx);
        addView(glowView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 2. Apple visionOS Spatial Glass floating window at bottom-center
        glassCard = new LinearLayout(ctx);
        glassCard.setOrientation(LinearLayout.HORIZONTAL);
        glassCard.setGravity(Gravity.CENTER_VERTICAL);
        int padH = UiKit.px(30), padTop = UiKit.px(22), padBot = UiKit.px(26);
        glassCard.setPadding(padH, padTop, padH, padBot);
        glassCard.setBackground(new VisionSpatialGlassDrawable());
        if (Build.VERSION.SDK_INT >= 21) {
            glassCard.setElevation(UiKit.pxf(28));
        }
        glassCard.setClipChildren(false);

        // Album artwork / Spatial Sonic Orb container on the left
        int artSize = UiKit.px(80);
        artContainer = new FrameLayout(ctx);
        artContainer.setBackground(new VinylDiscDrawable());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(artSize, artSize);
        alp.rightMargin = UiKit.px(22);
        glassCard.addView(artContainer, alp);

        artImage = new ImageView(ctx);
        artImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artImage.setVisibility(GONE);
        artContainer.addView(artImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Right column: visionOS optic pill (wave bars + badge) + Song Title + Artist/Album + Mobile Sync pill
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glassCard.addView(textCol, tclp);

        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(UiKit.px(10), UiKit.px(4), UiKit.px(12), UiKit.px(4));
        headerRow.setBackground(new FrostedPillDrawable());

        waveBars = new BlueAcousticWaveView(ctx);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(UiKit.px(46), UiKit.px(18));
        wlp.rightMargin = UiKit.px(9);
        headerRow.addView(waveBars, wlp);

        eyebrowText = UiKit.bold(ctx, 11, Color.parseColor("#7DD3FC"));
        eyebrowText.setLetterSpacing(0.11f);
        headerRow.addView(eyebrowText);

        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textCol.addView(headerRow, hlp);

        titleText = UiKit.bold(ctx, 24, Color.WHITE);
        titleText.setMaxLines(2);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setMaxWidth(UiKit.px(660));
        titleText.setPadding(0, UiKit.px(7), 0, UiKit.px(2));
        textCol.addView(titleText);

        subtitleText = UiKit.label(ctx, 15, Color.parseColor("#E0F2FE"));
        subtitleText.setMaxLines(2);
        subtitleText.setEllipsize(TextUtils.TruncateAt.END);
        subtitleText.setMaxWidth(UiKit.px(660));
        textCol.addView(subtitleText);

        syncBadgeText = UiKit.bold(ctx, 11, Color.parseColor("#6EE7B7"));
        syncBadgeText.setLetterSpacing(0.05f);
        syncBadgeText.setPadding(0, UiKit.px(6), 0, 0);
        syncBadgeText.setVisibility(GONE);
        textCol.addView(syncBadgeText);

        LayoutParams clp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = UiKit.px(48);
        addView(glassCard, clp);
    }

    private static Context host(Context ctx) {
        TvKeyInterceptorService acc = TvKeyInterceptorService.getInstance();
        return acc != null ? acc : ctx.getApplicationContext();
    }

    /** Shows the blue Shazam listening animation and Liquid Glass bottom card. */
    public static void showListening(Context ctx) {
        MAIN.post(() -> {
            if (active != null) {
                active.dismissImmediate();
            }
            Context h = host(ctx);
            ShazamOverlay o = new ShazamOverlay(h);
            if (!o.attach()) return;
            active = o;
            o.configureListening();
        });
    }

    /** Updates live audio energy (0..1) while Shazam is recording PCM samples. */
    public static void updateEnergy(final float normEnergy) {
        MAIN.post(() -> {
            ShazamOverlay o = active;
            if (o != null && !o.dismissed) {
                o.glowView.setEnergy(normEnergy);
                o.waveBars.setEnergy(normEnergy);
            }
        });
    }

    /** Transitions the overlay to display the identified track in the Liquid Glass bottom card for 18 seconds. */
    public static void showResult(Context ctx, final ShazamRecognizer.TrackInfo info) {
        MAIN.post(() -> {
            ShazamOverlay o = active;
            if (o == null || o.dismissed) {
                Context h = host(ctx);
                o = new ShazamOverlay(h);
                if (!o.attach()) return;
                active = o;
            }
            if (info != null && !info.title.isEmpty()) {
                o.configureFound(info);
            } else {
                o.configureNotFound(null);
            }
        });
    }

    /** Shows a custom error message in the Liquid Glass bottom card and fades out. */
    public static void showError(Context ctx, final String errorMsg) {
        MAIN.post(() -> {
            ShazamOverlay o = active;
            if (o == null || o.dismissed) {
                Context h = host(ctx);
                o = new ShazamOverlay(h);
                if (!o.attach()) return;
                active = o;
            }
            o.configureNotFound(errorMsg);
        });
    }

    public static boolean isShowing() {
        return active != null && !active.dismissed;
    }

    public static void dismissActive() {
        MAIN.post(() -> {
            if (active != null) active.dismiss();
        });
    }

    private boolean attach() {
        try {
            int type = getContext() instanceof TvKeyInterceptorService
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.setTitle("CineShazam");

            setAlpha(0f);
            wm.addView(this, lp);
            int dur = PerformanceProfile.animationMs(getContext(), 320);
            animate().alpha(1f).setDuration(dur).start();
            glassCard.setTranslationY(UiKit.pxf(36));
            glassCard.setAlpha(0f);
            glassCard.animate().translationY(0f).alpha(1f).setDuration(dur + 40)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to attach ShazamOverlay: " + t);
            return false;
        }
    }

    private void configureListening() {
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        state = State.LISTENING;
        glowView.setState(State.LISTENING);
        waveBars.setState(State.LISTENING);
        artImage.setVisibility(GONE);
        syncBadgeText.setVisibility(GONE);

        eyebrowText.setText(tr ? "VISION AUDIO · SHAZAM DİNLİYOR" : "VISION AUDIO · SHAZAM LISTENING");
        eyebrowText.setTextColor(Color.parseColor("#38BDF8"));
        titleText.setText(tr ? "Çalan Müzik Dinleniyor…" : "Listening for Music…");
        subtitleText.setText(tr ? "Uzamsal akustik parmak izi analiz ediliyor" : "Analyzing spatial acoustic fingerprint");
        subtitleText.setTextColor(Color.parseColor("#BAE6FD"));

        SystemAudio.play(getContext(), SystemAudio.Sfx.RISER);
        scheduleDismiss(14_000L);
    }

    private void configureFound(final ShazamRecognizer.TrackInfo info) {
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        state = State.FOUND;
        glowView.setState(State.FOUND);
        waveBars.setState(State.FOUND);

        eyebrowText.setText(tr ? "✦ SHAZAM SPATIAL ID · EŞLEŞTİ" : "✦ SHAZAM SPATIAL ID · MATCHED");
        eyebrowText.setTextColor(Color.parseColor("#7DD3FC"));
        titleText.setText(info.title);

        StringBuilder sub = new StringBuilder();
        if (info.artist != null && !info.artist.isEmpty()) {
            sub.append(info.artist);
        }
        if (info.album != null && !info.album.isEmpty()) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append(info.album);
        }
        if (info.year != null && !info.year.isEmpty()) {
            sub.append(" (").append(info.year).append(")");
        }
        subtitleText.setText(sub.length() > 0 ? sub.toString() : "Shazam");
        subtitleText.setTextColor(Color.parseColor("#E0F2FE"));

        syncBadgeText.setText(tr
                ? "● Spotify & YouTube Music · Mobil Kumanda'da Hazır"
                : "● Ready on Mobile Remote · Open in Spotify & YouTube Music");
        syncBadgeText.setVisibility(VISIBLE);

        // Pulse the visionOS Spatial Glass card subtly to celebrate match
        glassCard.animate().scaleX(1.035f).scaleY(1.035f).setDuration(180).withEndAction(() ->
                glassCard.animate().scaleX(1f).scaleY(1f).setDuration(240).start()
        ).start();

        SystemAudio.play(getContext(), SystemAudio.Sfx.CHIME);

        // Load album cover artwork asynchronously if available
        if (info.artworkUrl != null && info.artworkUrl.startsWith("http")) {
            final String urlStr = info.artworkUrl;
            new Thread(() -> {
                Bitmap bmp = fetchArtworkBitmap(urlStr);
                if (bmp != null) {
                    final Bitmap rounded = makeRoundedBitmap(bmp, UiKit.px(80), UiKit.pxf(20));
                    MAIN.post(() -> {
                        if (!dismissed && artImage != null && rounded != null) {
                            artImage.setImageBitmap(rounded);
                            artImage.setAlpha(0f);
                            artImage.setVisibility(VISIBLE);
                            artImage.animate().alpha(1f).setDuration(280).start();
                        }
                    });
                }
            }, "shazam-artwork").start();
        }

        // Keep the song title & Spatial Glass window visible for 18 seconds
        scheduleDismiss(TRACK_DISPLAY_DURATION_MS);
    }

    private void configureNotFound(String customMsg) {
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        state = State.NOT_FOUND;
        glowView.setState(State.NOT_FOUND);
        waveBars.setState(State.NOT_FOUND);
        syncBadgeText.setVisibility(GONE);

        eyebrowText.setText(tr ? "VISION AUDIO · SHAZAM" : "VISION AUDIO · SHAZAM");
        eyebrowText.setTextColor(Color.parseColor("#93C5FD"));
        titleText.setText(customMsg != null && !customMsg.isEmpty()
                ? customMsg
                : (tr ? "Müzik Tanınamadı" : "Could Not Identify Track"));
        subtitleText.setText(tr
                ? "Müziğin daha net duyulduğu bir anda tekrar deneyin"
                : "Please try again when the music is clearer");
        subtitleText.setTextColor(Color.parseColor("#94A3B8"));

        SystemAudio.play(getContext(), SystemAudio.Sfx.ERROR);
        scheduleDismiss(ERROR_DISPLAY_DURATION_MS);
    }

    private void scheduleDismiss(long delayMs) {
        MAIN.removeCallbacks(autoDismissRunnable);
        MAIN.postDelayed(autoDismissRunnable, delayMs);
    }

    public void dismiss() {
        MAIN.removeCallbacks(autoDismissRunnable);
        if (dismissed) return;
        dismissed = true;
        if (active == this) active = null;

        state = State.DISMISSING;
        glowView.setState(State.DISMISSING);
        int dur = PerformanceProfile.animationMs(getContext(), 320);
        glassCard.animate().translationY(UiKit.pxf(28)).alpha(0f).setDuration(dur).start();
        animate().alpha(0f).setDuration(dur).withEndAction(() -> {
            try {
                wm.removeView(ShazamOverlay.this);
            } catch (Throwable ignored) {
            }
        }).start();
    }

    private void dismissImmediate() {
        MAIN.removeCallbacks(autoDismissRunnable);
        dismissed = true;
        if (active == this) active = null;
        try {
            wm.removeView(this);
        } catch (Throwable ignored) {
        }
    }

    private static Bitmap fetchArtworkBitmap(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setDoInput(true);
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static Bitmap makeRoundedBitmap(Bitmap src, int sizePx, float radiusPx) {
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            RectF r = new RectF(0, 0, sizePx, sizePx);
            c.drawRoundRect(r, radiusPx, radiusPx, p);

            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(UiKit.pxf(1.6f));
            rim.setShader(new LinearGradient(0, 0, sizePx, sizePx,
                    new int[]{Color.argb(210, 255, 255, 255), Color.argb(120, 56, 189, 248), Color.argb(160, 139, 92, 246)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            r.inset(1f, 1f);
            c.drawRoundRect(r, radiusPx, radiusPx, rim);
            if (scaled != src) scaled.recycle();
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    /** Apple visionOS Spatial Glass window drawable with specular rim, subsurface bloom, and bottom grabber bar. */
    private static final class VisionSpatialGlassDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF inner = new RectF();
        private final RectF grabber = new RectF();

        @Override
        public void draw(Canvas c) {
            rect.set(getBounds());
            float w = rect.width(), h = rect.height();
            if (w <= 0 || h <= 0) return;
            float rad = UiKit.pxf(34);

            // 1. Deep translucent spatial glass body (obsidian-sapphire frost)
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{
                            Color.argb(236, 18, 26, 44),
                            Color.argb(226, 12, 18, 34),
                            Color.argb(242, 8, 12, 24)
                    },
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, p);

            // 2. Subsurface cyan-violet spatial ambient bloom
            p.setShader(new RadialGradient(
                    rect.left + w * 0.22f, rect.top + h * 0.25f, Math.max(w, h) * 0.78f,
                    new int[]{
                            Color.argb(64, 56, 189, 248),
                            Color.argb(32, 99, 102, 241),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, p);

            // 3. Top visionOS optical frost sheen
            p.setShader(new LinearGradient(0, rect.top, 0, rect.top + h * 0.46f,
                    Color.argb(44, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, p);

            // 4. Inner 1px optical bevel
            inner.set(rect);
            inner.inset(UiKit.pxf(1.6f), UiKit.pxf(1.6f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(UiKit.pxf(1.0f));
            p.setShader(new LinearGradient(0, inner.top, 0, inner.bottom,
                    Color.argb(82, 255, 255, 255), Color.argb(14, 125, 211, 252), Shader.TileMode.CLAMP));
            c.drawRoundRect(inner, rad - UiKit.pxf(1.5f), rad - UiKit.pxf(1.5f), p);

            // 5. Directional specular glass border
            p.setStrokeWidth(UiKit.pxf(1.6f));
            p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{
                            Color.argb(215, 255, 255, 255),
                            Color.argb(145, 56, 189, 248),
                            Color.argb(55, 255, 255, 255),
                            Color.argb(140, 129, 140, 248)
                    },
                    new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, p);

            // 6. Signature visionOS bottom window grabber bar
            float gw = Math.min(UiKit.pxf(56), w * 0.20f);
            float gh = UiKit.pxf(3.5f);
            float gx = rect.centerX() - gw * 0.5f;
            float gy = rect.bottom - UiKit.pxf(9.5f);
            grabber.set(gx, gy, gx + gw, gy + gh);
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(Color.argb(95, 255, 255, 255));
            c.drawRoundRect(grabber, gh * 0.5f, gh * 0.5f, p);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Frosted visionOS optic pill behind the acoustic wave bars and status eyebrow. */
    private static final class FrostedPillDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        @Override
        public void draw(Canvas c) {
            rect.set(getBounds());
            float h = rect.height();
            if (rect.width() <= 0 || h <= 0) return;
            float r = h * 0.5f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    Color.argb(65, 56, 189, 248), Color.argb(38, 99, 102, 241), Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, r, r, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(UiKit.pxf(1.0f));
            p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    Color.argb(140, 186, 230, 253), Color.argb(65, 165, 180, 252), Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, r, r, p);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Stylized visionOS Spatial Sonic Orb rendered behind or in place of album artwork. */
    private static final class VinylDiscDrawable extends Drawable {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        VinylDiscDrawable() {
            ringPaint.setStyle(Paint.Style.STROKE);
            notePaint.setStyle(Paint.Style.FILL);
            notePaint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas c) {
            rect.set(getBounds());
            float w = rect.width(), h = rect.height();
            if (w <= 0 || h <= 0) return;
            float rad = UiKit.pxf(20);

            bgPaint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{Color.parseColor("#0284C7"), Color.parseColor("#1E3A8A"), Color.parseColor("#0F172A")},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, bgPaint);

            float cx = rect.centerX(), cy = rect.centerY();
            bgPaint.setShader(new RadialGradient(cx - w * 0.15f, cy - h * 0.15f, w * 0.55f,
                    Color.argb(95, 56, 189, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, bgPaint);

            ringPaint.setStrokeWidth(UiKit.pxf(1.4f));
            ringPaint.setColor(Color.argb(90, 186, 230, 253));
            c.drawCircle(cx, cy, w * 0.36f, ringPaint);
            ringPaint.setColor(Color.argb(60, 165, 180, 252));
            c.drawCircle(cx, cy, w * 0.24f, ringPaint);

            // Outer glass rim on orb
            ringPaint.setColor(Color.argb(120, 255, 255, 255));
            ringPaint.setStrokeWidth(UiKit.pxf(1.2f));
            rect.inset(1f, 1f);
            c.drawRoundRect(rect, rad, rad, ringPaint);

            // Draw musical eighth-note icon in the center
            float nw = w * 0.10f;
            c.drawCircle(cx - nw * 0.6f, cy + nw * 0.8f, nw * 0.75f, notePaint);
            c.drawCircle(cx + nw * 1.1f, cy + nw * 0.5f, nw * 0.75f, notePaint);
            ringPaint.setColor(Color.WHITE);
            ringPaint.setStrokeWidth(UiKit.pxf(2.4f));
            c.drawLine(cx - nw * 0.05f, cy + nw * 0.7f, cx - nw * 0.05f, cy - nw * 1.3f, ringPaint);
            c.drawLine(cx + nw * 1.65f, cy + nw * 0.4f, cx + nw * 1.65f, cy - nw * 1.6f, ringPaint);
            c.drawLine(cx - nw * 0.05f, cy - nw * 1.3f, cx + nw * 1.65f, cy - nw * 1.6f, ringPaint);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** 9-band animated blue acoustic spectrum inside the Liquid Glass capsule. */
    private static final class BlueAcousticWaveView extends View {
        private static final int BAR_COUNT = 9;
        private static final float[] BAR_WEIGHTS = {0.42f, 0.70f, 0.95f, 0.78f, 1.0f, 0.82f, 0.92f, 0.66f, 0.42f};
        private static final int[] BAR_COLORS = {
                0xFF00F0FF, 0xFF38BDF8, 0xFF0EA5E9, 0xFF60A5FA,
                0xFF7DD3FC, 0xFF60A5FA, 0xFF0EA5E9, 0xFF38BDF8, 0xFF00F0FF
        };

        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF barRect = new RectF();
        private State state = State.LISTENING;
        private float energy = 0.25f;
        private float targetEnergy = 0.25f;
        private float phase = 0f;

        BlueAcousticWaveView(Context ctx) {
            super(ctx);
            barPaint.setStyle(Paint.Style.FILL);
        }

        void setState(State s) {
            this.state = s;
            postInvalidateOnAnimation();
        }

        void setEnergy(float norm) {
            this.targetEnergy = Math.max(this.targetEnergy, Math.max(0f, Math.min(1f, norm)));
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            targetEnergy *= 0.88f;
            float idle = state == State.LISTENING ? 0.35f : (state == State.FOUND ? 0.28f : 0.08f);
            float eff = Math.max(idle, targetEnergy);
            energy += (eff - energy) * 0.30f;
            phase += (state == State.LISTENING ? 0.16f : 0.09f) + energy * 0.14f;

            float gap = UiKit.pxf(2.6f);
            float barW = (w - gap * (BAR_COUNT - 1)) / BAR_COUNT;
            float cy = h * 0.5f;
            float minH = UiKit.pxf(3.5f);
            float maxH = h * 0.92f;

            for (int i = 0; i < BAR_COUNT; i++) {
                float harmonic = 0.5f + 0.5f * (float) Math.sin(phase + i * 0.75f + Math.cos(phase * 0.55f - i));
                float level = Math.min(1f, (0.20f + energy * 0.85f) * BAR_WEIGHTS[i] * (0.45f + 0.55f * harmonic));
                float bh = Math.max(minH, level * maxH);
                float left = i * (barW + gap);
                barRect.set(left, cy - bh * 0.5f, left + barW, cy + bh * 0.5f);
                barPaint.setColor( BAR_COLORS[i]);
                c.drawRoundRect(barRect, barW * 0.5f, barW * 0.5f, barPaint);
            }

            if (state != State.DISMISSING && isAttachedToWindow()) {
                postInvalidateOnAnimation();
            }
        }
    }

    /**
     * CineSUB-style Electric Blue Glow & Wave Perimeter View around all four screen edges
     * plus a luminous blue floor bloom under the Liquid Glass capsule.
     */
    private static final class BlueGlowEdgeView extends View {

        private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint leftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint floorBloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wavePath = new Path();
        private final Matrix bloomMatrix = new Matrix();
        private RadialGradient floorShader;
        private int cachedEdgeW = -1, cachedEdgeH = -1, cachedEdgeDepth = -1;

        private State state = State.LISTENING;
        private float energy = 0.3f;
        private float targetEnergy = 0.3f;
        private float phase = 0f;

        // Luminous Shazam Blue palette
        private static final int BLUE_CYAN = Color.parseColor("#FF00F0FF");
        private static final int BLUE_SKY = Color.parseColor("#FF38BDF8");
        private static final int BLUE_ROYAL = Color.parseColor("#FF2563EB");
        private static final int BLUE_TRANSPARENT = Color.argb(0, 37, 99, 235);

        BlueGlowEdgeView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            wavePaint.setStyle(Paint.Style.FILL);
            rimPaint.setStyle(Paint.Style.STROKE);
            floorBloomPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;
            floorShader = new RadialGradient(0f, 0f, 1.0f,
                    new int[]{0x8800F0FF, 0x5538BDF8, 0x282563EB, 0x00000000},
                    new float[]{0f, 0.42f, 0.75f, 1f},
                    Shader.TileMode.CLAMP);
        }

        void setState(State s) {
            this.state = s;
            if (s == State.FOUND) {
                this.targetEnergy = 0.85f;
            }
            postInvalidateOnAnimation();
        }

        void setEnergy(float norm) {
            this.targetEnergy = Math.max(this.targetEnergy * 0.75f, Math.max(0f, Math.min(1f, norm)));
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            targetEnergy *= 0.90f;
            float basePulse = state == State.LISTENING
                    ? (0.36f + 0.18f * (float) Math.sin(phase * 1.9f))
                    : state == State.FOUND
                    ? (0.24f + 0.10f * (float) Math.sin(phase * 1.3f))
                    : 0.12f;
            float eff = Math.max(basePulse, targetEnergy);
            energy += (eff - energy) * 0.24f;
            phase += (state == State.LISTENING ? 0.062f : 0.036f) + energy * 0.045f;
            if (phase > (float) (Math.PI * 20.0)) {
                phase -= (float) (Math.PI * 20.0);
            }

            // Edge glow alpha: vibrant during listening, softer ambient frame once track is found
            float edgeAlphaFactor = state == State.LISTENING
                    ? (0.62f + energy * 0.36f)
                    : state == State.FOUND
                    ? (0.38f + energy * 0.22f)
                    : 0.20f;
            edgeAlphaFactor = Math.max(0.08f, Math.min(1f, edgeAlphaFactor));

            float depthFactor = (state == State.LISTENING ? 0.14f : 0.10f) + energy * 0.09f;
            int edgeDepth = (int) (Math.min(w, h) * depthFactor);
            edgeDepth = Math.max(UiKit.px(75), Math.min(edgeDepth, UiKit.px(230)));

            int alphaInt = (int) (255 * edgeAlphaFactor);
            if (w != cachedEdgeW || h != cachedEdgeH || edgeDepth != cachedEdgeDepth) {
                cachedEdgeW = w;
                cachedEdgeH = h;
                cachedEdgeDepth = edgeDepth;
                int baseEdge = Color.argb(255, Color.red(BLUE_SKY), Color.green(BLUE_SKY), Color.blue(BLUE_SKY));
                int baseMid = Color.argb(143, Color.red(BLUE_ROYAL), Color.green(BLUE_ROYAL), Color.blue(BLUE_ROYAL));
                int[] cols = new int[]{baseEdge, baseMid, BLUE_TRANSPARENT};
                float[] stops = new float[]{0f, 0.45f, 1f};
                topPaint.setShader(new LinearGradient(0, 0, 0, edgeDepth, cols, stops, Shader.TileMode.CLAMP));
                bottomPaint.setShader(new LinearGradient(0, h, 0, h - edgeDepth, cols, stops, Shader.TileMode.CLAMP));
                leftPaint.setShader(new LinearGradient(0, 0, edgeDepth, 0, cols, stops, Shader.TileMode.CLAMP));
                rightPaint.setShader(new LinearGradient(w, 0, w - edgeDepth, 0, cols, stops, Shader.TileMode.CLAMP));
            }
            topPaint.setAlpha(alphaInt);
            bottomPaint.setAlpha(alphaInt);
            leftPaint.setAlpha(alphaInt);
            rightPaint.setAlpha(alphaInt);

            canvas.drawRect(0, 0, w, edgeDepth, topPaint);
            canvas.drawRect(0, h - edgeDepth, w, h, bottomPaint);
            canvas.drawRect(0, 0, edgeDepth, h, leftPaint);
            canvas.drawRect(w - edgeDepth, 0, w, h, rightPaint);

            // Draw undulating blue acoustic waves around the 4 display edges
            drawBlueEdgeWaves(canvas, w, h, edgeAlphaFactor);

            // Luminous blue floor bloom anchored under the bottom Liquid Glass card
            if (floorShader != null) {
                float cx = w * 0.5f;
                float cy = h - UiKit.pxf(56f);
                float rx = UiKit.pxf(460f) + energy * UiKit.pxf(150f);
                float ry = UiKit.pxf(145f) + energy * UiKit.pxf(55f);
                bloomMatrix.reset();
                bloomMatrix.setScale(rx, ry);
                bloomMatrix.postTranslate(cx, cy);
                floorShader.setLocalMatrix(bloomMatrix);
                floorBloomPaint.setShader(floorShader);
                floorBloomPaint.setAlpha(Math.min(240, (int) (130 + energy * 110)));
                canvas.drawRect(0, h - ry * 1.6f, w, h, floorBloomPaint);
            }

            // Crisp cyan-blue outer border glow
            rimPaint.setStrokeWidth(UiKit.pxf(5.5f));
            rimPaint.setColor(Color.argb(Math.min(255, (int) (195 * edgeAlphaFactor)), 125, 211, 252));
            canvas.drawRect(0, 0, w, h, rimPaint);

            if (state != State.DISMISSING && isAttachedToWindow()) {
                postInvalidateOnAnimation();
            }
        }

        private void drawBlueEdgeWaves(Canvas canvas, int w, int h, float alpha) {
            float amp = UiKit.pxf(10f) + energy * UiKit.pxf(34f);
            float baseThickness = UiKit.pxf(8f) + energy * UiKit.pxf(14f);
            int waveAlpha = Math.min(225, (int) (130 * alpha + energy * 85));
            wavePaint.setColor(Color.argb(waveAlpha, Color.red(BLUE_CYAN), Color.green(BLUE_CYAN), Color.blue(BLUE_CYAN)));

            int steps = 28;
            // Top wave
            wavePath.reset();
            wavePath.moveTo(0, 0);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float x = t * w;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 8.0 + phase * 2.3f) * 0.6f + Math.cos(t * 14.0 - phase * 3.0f) * 0.4f);
                float y = baseThickness + env * amp * (0.55f + 0.45f * wave);
                wavePath.lineTo(x, Math.max(2f, y));
            }
            wavePath.lineTo(w, 0);
            wavePath.close();
            canvas.drawPath(wavePath, wavePaint);

            // Bottom wave
            wavePath.reset();
            wavePath.moveTo(0, h);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float x = t * w;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 8.0 - phase * 2.5f + 1.1f) * 0.6f + Math.cos(t * 13.0 + phase * 3.2f) * 0.4f);
                float y = h - (baseThickness + env * amp * (0.55f + 0.45f * wave));
                wavePath.lineTo(x, Math.min(h - 2f, y));
            }
            wavePath.lineTo(w, h);
            wavePath.close();
            canvas.drawPath(wavePath, wavePaint);

            // Left & Right vertical waves
            int vSteps = 20;
            wavePath.reset();
            wavePath.moveTo(0, 0);
            for (int i = 0; i <= vSteps; i++) {
                float t = i / (float) vSteps;
                float y = t * h;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 7.0 + phase * 2.1f + 0.4f) * 0.6f + Math.cos(t * 11.0 - phase * 2.8f) * 0.4f);
                float x = baseThickness * 0.85f + env * amp * 0.78f * (0.55f + 0.45f * wave);
                wavePath.lineTo(Math.max(2f, x), y);
            }
            wavePath.lineTo(0, h);
            wavePath.close();
            canvas.drawPath(wavePath, wavePaint);

            wavePath.reset();
            wavePath.moveTo(w, 0);
            for (int i = 0; i <= vSteps; i++) {
                float t = i / (float) vSteps;
                float y = t * h;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 7.0 - phase * 2.4f + 1.9f) * 0.6f + Math.cos(t * 11.0 + phase * 2.6f) * 0.4f);
                float x = w - (baseThickness * 0.85f + env * amp * 0.78f * (0.55f + 0.45f * wave));
                wavePath.lineTo(Math.min(w - 2f, x), y);
            }
            wavePath.lineTo(w, h);
            wavePath.close();
            canvas.drawPath(wavePath, wavePaint);
        }
    }
}
