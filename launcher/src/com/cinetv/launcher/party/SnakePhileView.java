package com.cinetv.launcher.party;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.ui.Brand;
import com.cinetv.launcher.ui.Glass;
import com.cinetv.launcher.ui.UiKit;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Fullscreen 3D Photorealistic Snake Game ("SnakePhile") for CineTV.
 * Features:
 * - 3D organic segmented snake with cylindrical lighting, specular sheen, and reptilian scale patterns.
 * - Anatomical 3D viper head with golden slit-pupil eyes and dynamic animated flicking forked tongue.
 * - Targets are authentic TMDB plain movie posters (DVD-boxless, 2:3 ratio) matching CineTV Home.
 * - Liquid glass HUD (Brand wordmark, live score, current target film, D-pad controls).
 * - Full TV remote D-Pad controls and intelligent autonomous slither AI for live presentations.
 * - Game Over self-bite mechanic: the movie the snake failed to eat is crowned and launched on TV!
 */
public final class SnakePhileView extends View {

    public interface OnSnakePhileListener {
        void onMovieLaunch(String cleanMovieTitle);
        void onExit();
    }

    private final Paint floorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint snakeBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tonguePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint posterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float eatRippleRadius = 0f;
    private float eatRippleX = 0f;
    private float eatRippleY = 0f;

    private final RectF posterRect = new RectF();
    private final Path clipPath = new Path();
    private final Path headPath = new Path();
    private final Path tonguePath = new Path();

    // Snake Spine Coordinates
    private static final int MAX_SEGMENTS = 72;
    private final float[] segX = new float[MAX_SEGMENTS];
    private final float[] segY = new float[MAX_SEGMENTS];
    private final float[] segAngles = new float[MAX_SEGMENTS];
    private int currentSegmentCount = 20;

    private float headX = 960f;
    private float headY = 540f;
    private float headAngle = 0f; // radians
    private float targetAngle = 0f;
    private float speed = 5.2f;
    private long tick = 0;
    private boolean isManualControl = false;
    private boolean showHelpOverlay = false;

    // Food / Poster State
    private float foodX = 1320f;
    private float foodY = 520f;
    private int currentFoodIndex = 0;
    private int score = 0;
    private int highScore = 3250;

    // Game Over State
    private boolean isGameOver = false;
    private int gameOverFocusIndex = 0; // 0 = Launch Movie, 1 = Restart, 2 = Exit
    private String gamzeLore = "";
    private final RectF btnLaunchRect = new RectF();
    private final RectF btnRestartRect = new RectF();
    private final RectF btnExitRect = new RectF();

    private static final String[] POSTER_ASSETS = {
            "posters/interstellar.jpg",
            "posters/pulp_fiction.jpg",
            "posters/godfather.jpg",
            "posters/inception.jpg",
            "posters/fight_club.jpg",
            "posters/taxi_driver.jpg",
            "posters/blade_runner.jpg",
            "posters/whiplash.jpg"
    };

    private static final String[] POSTER_TITLES = {
            "Interstellar (2014) · 8.7/10",
            "Pulp Fiction (1994) · 8.9/10",
            "The Godfather (1972) · 9.2/10",
            "Inception (2010) · 8.8/10",
            "Fight Club (1999) · 8.8/10",
            "Taxi Driver (1976) · 8.2/10",
            "Blade Runner (1982) · 8.1/10",
            "Whiplash (2014) · 8.5/10"
    };

    private final List<Bitmap> posterBitmaps = new ArrayList<>();
    private OnSnakePhileListener listener;
    private Runnable onExitListener;

    public SnakePhileView(Context context) {
        super(context);
        init();
    }

    public SnakePhileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setOnSnakePhileListener(OnSnakePhileListener listener) {
        this.listener = listener;
    }

    public void setOnExitListener(Runnable listener) {
        this.onExitListener = listener;
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        // Load TMDB posters from assets
        for (String assetPath : POSTER_ASSETS) {
            try (InputStream in = getContext().getAssets().open(assetPath)) {
                Bitmap bmp = BitmapFactory.decodeStream(in);
                posterBitmaps.add(bmp);
            } catch (Throwable ignored) {
                posterBitmaps.add(null);
            }
        }

        resetGame();

        shadowPaint.setColor(0x55000000);
        shadowPaint.setStyle(Paint.Style.FILL);

        scalePaint.setStyle(Paint.Style.STROKE);
        scalePaint.setStrokeWidth(UiKit.pxf(1.2f));
        scalePaint.setColor(0x33A7F3D0);

        tonguePaint.setColor(Color.parseColor("#EF4444"));
        tonguePaint.setStyle(Paint.Style.STROKE);
        tonguePaint.setStrokeWidth(UiKit.pxf(2.5f));
        tonguePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTypeface(FontManager.getBold(getContext()));
        textPaint.setAntiAlias(true);
    }

    public void resetGame() {
        isGameOver = false;
        gameOverFocusIndex = 0;
        isManualControl = false;
        score = 0;
        currentSegmentCount = 20;
        speed = 5.2f;
        tick = 0;

        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;
        headX = w * 0.45f;
        headY = h * 0.50f;
        foodX = w * 0.72f;
        foodY = h * 0.48f;

        float initAngle = (float) Math.toRadians(25);
        headAngle = initAngle;
        targetAngle = initAngle;
        for (int i = 0; i < MAX_SEGMENTS; i++) {
            float t = i * 24f;
            float wave = (float) Math.sin(i * 0.35f) * 40f;
            segX[i] = headX - t * (float) Math.cos(initAngle) - wave * (float) Math.sin(initAngle);
            segY[i] = headY - t * (float) Math.sin(initAngle) + wave * (float) Math.cos(initAngle);
            segAngles[i] = initAngle;
        }
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(getContext(), "SnakePhile başladı! Film afişlerini topla, kendine dikkat et.");
        invalidate();
    }

    private void triggerGameOver() {
        isGameOver = true;
        gameOverFocusIndex = 0;
        if (score > highScore) highScore = score;
        com.cinetv.launcher.device.SystemAudio.open(getContext());
        gamzeLore = "Gamze AI sinematik notu hazırlanıyor...";
        String currentRaw = POSTER_TITLES[currentFoodIndex % POSTER_TITLES.length];
        String movie = cleanMovieTitle(currentRaw);
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(getContext(),
                "Yılan kendini ısırdı, oyun bitti! Bu akşam salonda izlenecek film: " + movie + ".");
        CinePartyManager.get(getContext()).getGamzeMovieLore(movie, lore -> {
            gamzeLore = lore;
            if (lore != null && !lore.isEmpty()) {
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(getContext(), "Gamze sinema notu: " + lore);
            }
            invalidate();
        });
        invalidate();
    }

    public static String cleanMovieTitle(String raw) {
        if (raw == null) return "Interstellar";
        return raw.split("\\(")[0].replace("·", "").replace("★", "").trim();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (showHelpOverlay) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_MENU) {
                showHelpOverlay = false;
                invalidate();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_MENU) {
            showHelpOverlay = !showHelpOverlay;
            invalidate();
            return true;
        }

        if (isGameOver) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    gameOverFocusIndex = (gameOverFocusIndex + 2) % 3;
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    gameOverFocusIndex = (gameOverFocusIndex + 1) % 3;
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (gameOverFocusIndex == 0) {
                        // Launch movie!
                        String currentRaw = POSTER_TITLES[currentFoodIndex % POSTER_TITLES.length];
                        String movie = cleanMovieTitle(currentRaw);
                        if (listener != null) listener.onMovieLaunch(movie);
                        else if (onExitListener != null) onExitListener.run();
                    } else if (gameOverFocusIndex == 1) {
                        resetGame();
                    } else {
                        if (listener != null) listener.onExit();
                        else if (onExitListener != null) onExitListener.run();
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (listener != null) listener.onExit();
                    else if (onExitListener != null) onExitListener.run();
                    return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        isManualControl = true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                targetAngle = (float) (-Math.PI / 2.0);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                targetAngle = (float) (Math.PI / 2.0);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                targetAngle = (float) Math.PI;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                targetAngle = 0f;
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                speed = speed > 6f ? 5.2f : 9.0f; // Turbo boost toggle
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (listener != null) {
                    listener.onExit();
                    return true;
                } else if (onExitListener != null) {
                    onExitListener.run();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            if (isGameOver) {
                if (btnLaunchRect.contains(x, y)) {
                    String currentRaw = POSTER_TITLES[currentFoodIndex % POSTER_TITLES.length];
                    String movie = cleanMovieTitle(currentRaw);
                    if (listener != null) listener.onMovieLaunch(movie);
                    else if (onExitListener != null) onExitListener.run();
                    return true;
                } else if (btnRestartRect.contains(x, y)) {
                    resetGame();
                    return true;
                } else if (btnExitRect.contains(x, y)) {
                    if (listener != null) listener.onExit();
                    else if (onExitListener != null) onExitListener.run();
                    return true;
                }
            } else {
                // Steer toward tap location
                isManualControl = true;
                targetAngle = (float) Math.atan2(y - headY, x - headX);
                return true;
            }
        }
        return true;
    }

    private void updatePhysics() {
        if (isGameOver) return;
        tick++;

        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;

        float dx = foodX - headX;
        float dy = foodY - headY;
        float distToFood = (float) Math.hypot(dx, dy);

        if (!isManualControl) {
            // Serpentine autonomous AI: hunts the movie poster with organic slither
            float angleToFood = (float) Math.atan2(dy, dx);
            float slitherOffset = (float) Math.sin(tick * 0.12f) * 0.40f;
            targetAngle = angleToFood + slitherOffset;
        }

        // Smooth angular interpolation
        float diff = targetAngle - headAngle;
        while (diff < -Math.PI) diff += (float) (Math.PI * 2);
        while (diff > Math.PI) diff -= (float) (Math.PI * 2);
        headAngle += diff * (isManualControl ? 0.35f : 0.08f);

        // Move head forward
        headX += speed * (float) Math.cos(headAngle);
        headY += speed * (float) Math.sin(headAngle);

        // Screen boundary safety wrap/bounce
        if (headX < UiKit.pxf(60)) { headX = UiKit.pxf(60); targetAngle = 0f; }
        if (headX > w - UiKit.pxf(60)) { headX = w - UiKit.pxf(60); targetAngle = (float) Math.PI; }
        if (headY < UiKit.pxf(100)) { headY = UiKit.pxf(100); targetAngle = (float) (Math.PI / 2.0); }
        if (headY > h - UiKit.pxf(80)) { headY = h - UiKit.pxf(80); targetAngle = (float) (-Math.PI / 2.0); }

        // Update body segments following the spine
        segX[0] = headX;
        segY[0] = headY;
        segAngles[0] = headAngle;

        float segSpacing = UiKit.pxf(20);
        for (int i = 1; i < currentSegmentCount; i++) {
            float sx = segX[i - 1] - segX[i];
            float sy = segY[i - 1] - segY[i];
            float curDist = (float) Math.hypot(sx, sy);
            if (curDist > 0.1f) {
                segAngles[i] = (float) Math.atan2(sy, sx);
                segX[i] = segX[i - 1] - segSpacing * (float) Math.cos(segAngles[i]);
                segY[i] = segY[i - 1] - segSpacing * (float) Math.sin(segAngles[i]);
            }
        }

        // Self-collision (Biting own tail/body) detection
        if (isManualControl && tick > 50) {
            float biteThreshold = UiKit.pxf(24f);
            for (int i = 6; i < currentSegmentCount; i++) {
                float bdx = headX - segX[i];
                float bdy = headY - segY[i];
                if (Math.hypot(bdx, bdy) < biteThreshold) {
                    triggerGameOver();
                    return;
                }
            }
        }

        // Check food eaten
        if (distToFood < UiKit.pxf(68)) {
            String eatenMovie = cleanMovieTitle(POSTER_TITLES[currentFoodIndex % POSTER_TITLES.length]);
            score += 150;
            if (score > highScore) highScore = score;
            eatRippleX = foodX;
            eatRippleY = foodY;
            eatRippleRadius = UiKit.pxf(16f);
            com.cinetv.launcher.device.SystemAudio.tick(getContext());
            currentFoodIndex = (currentFoodIndex + 1) % POSTER_TITLES.length;
            String nextMovie = cleanMovieTitle(POSTER_TITLES[currentFoodIndex % POSTER_TITLES.length]);
            if (isManualControl) {
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(getContext(),
                        eatenMovie + " yakalandı! Sıradaki hedef: " + nextMovie + ".");
            }
            // Grow snake body!
            if (currentSegmentCount < MAX_SEGMENTS) {
                currentSegmentCount = Math.min(MAX_SEGMENTS, currentSegmentCount + 3);
            }
            // Spawn next food position away from current head
            foodX = UiKit.pxf(280) + (float) ((tick * 157) % (w - UiKit.pxf(560)));
            foodY = UiKit.pxf(220) + (float) ((tick * 193) % (h - UiKit.pxf(400)));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updatePhysics();

        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;

        // 1. Cinematic Studio Background with Deep Obsidian Radial Vignette
        floorPaint.setStyle(Paint.Style.FILL);
        floorPaint.setShader(new RadialGradient(w * 0.5f, h * 0.45f, w * 0.75f,
                new int[]{0xFF0E1A2E, 0xFF080D1A, 0xFF03070E},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, floorPaint);
        floorPaint.setShader(null);

        // Subtle 3D Studio Floor Grid
        floorPaint.setStyle(Paint.Style.STROKE);
        floorPaint.setStrokeWidth(UiKit.pxf(1));
        floorPaint.setColor(0x0C38BDF8);
        int gridSize = UiKit.px(80);
        for (int x = 0; x < w; x += gridSize) {
            canvas.drawLine(x, 0, x, h, floorPaint);
        }
        for (int y = 0; y < h; y += gridSize) {
            canvas.drawLine(0, y, w, y, floorPaint);
        }

        // 2. Render Food Target: Plain Movie Poster (DVD-boxless, 2:3 ratio) matching CineTV Home
        drawMoviePosterTarget(canvas, foodX, foodY);

        // Food Eating Ripple Shockwave Animation
        if (eatRippleRadius > 0f) {
            eatRippleRadius += UiKit.pxf(9f);
            float maxR = UiKit.pxf(130f);
            if (eatRippleRadius < maxR) {
                float alphaFrac = 1f - (eatRippleRadius / maxR);
                ripplePaint.setStyle(Paint.Style.STROKE);
                ripplePaint.setStrokeWidth(UiKit.pxf(3f));
                ripplePaint.setColor(Color.parseColor("#10B981"));
                ripplePaint.setAlpha((int) (alphaFrac * 220));
                canvas.drawCircle(eatRippleX, eatRippleY, eatRippleRadius, ripplePaint);
            } else {
                eatRippleRadius = 0f;
            }
        }

        // 3. Render 3D Realistic Snake Body
        drawRealisticSnake(canvas);

        // 4. Liquid Glass HUD Overlay
        drawLiquidGlassHud(canvas, w, h);

        // 5. Game Over Liquid Glass Card & Movie Launch Modal
        if (isGameOver) {
            drawGameOverModal(canvas, w, h);
        } else if (showHelpOverlay) {
            drawHelpOverlay(canvas, w, h);
        }

        // 60 FPS animation loop
        if (!isGameOver) {
            postInvalidateOnAnimation();
        }
    }

    private void drawMoviePosterTarget(Canvas canvas, float fx, float fy) {
        float pw = UiKit.pxf(130);
        float ph = pw * 1.48f; // Standard 2:3 poster ratio
        posterRect.set(fx - pw / 2f, fy - ph / 2f, fx + pw / 2f, fy + ph / 2f);

        // Ambient colored pulse underglow
        float glowRadius = pw * 1.1f;
        posterPaint.setStyle(Paint.Style.FILL);
        posterPaint.setShader(new RadialGradient(fx, fy, glowRadius,
                new int[]{0x6610B981, 0x1A10B981, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(fx, fy, glowRadius, posterPaint);
        posterPaint.setShader(null);

        // Poster Drop Shadow
        shadowPaint.setColor(0x88000000);
        float pCorner = pw * 0.085f;
        canvas.drawRoundRect(posterRect.left + UiKit.pxf(6), posterRect.top + UiKit.pxf(8),
                posterRect.right + UiKit.pxf(6), posterRect.bottom + UiKit.pxf(8),
                pCorner, pCorner, shadowPaint);

        // Plain Poster Bitmap
        Bitmap posterBmp = (currentFoodIndex < posterBitmaps.size()) ? posterBitmaps.get(currentFoodIndex) : null;
        if (posterBmp != null) {
            clipPath.reset();
            clipPath.addRoundRect(posterRect, pCorner, pCorner, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawBitmap(posterBmp, null, posterRect, null);
            canvas.restore();
        } else {
            posterPaint.setStyle(Paint.Style.FILL);
            posterPaint.setColor(Color.parseColor("#1E3A8A"));
            canvas.drawRoundRect(posterRect, pCorner, pCorner, posterPaint);
        }

        // Specular glass sheen on poster surface
        posterPaint.setStyle(Paint.Style.FILL);
        posterPaint.setColor(0x24FFFFFF);
        canvas.drawRoundRect(posterRect, pCorner, pCorner, posterPaint);

        // Outer crisp thin glass border
        posterPaint.setStyle(Paint.Style.STROKE);
        posterPaint.setStrokeWidth(UiKit.pxf(1.8f));
        posterPaint.setColor(0x88FFFFFF);
        canvas.drawRoundRect(posterRect, pCorner, pCorner, posterPaint);

        // Target Film Title Badge below poster
        String fTitle = POSTER_TITLES[currentFoodIndex % POSTER_TITLES.length];
        textPaint.setTextSize(UiKit.pxf(14));
        textPaint.setTextAlign(Paint.Align.CENTER);
        float tw = textPaint.measureText(fTitle) + UiKit.pxf(32);
        float th = UiKit.pxf(32);
        RectF tagRect = new RectF(fx - tw / 2f, posterRect.bottom + UiKit.pxf(12), fx + tw / 2f, posterRect.bottom + UiKit.pxf(12) + th);

        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(0xD90B1220);
        canvas.drawRoundRect(tagRect, th / 2f, th / 2f, hudPaint);

        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(UiKit.pxf(1.5f));
        hudPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawRoundRect(tagRect, th / 2f, th / 2f, hudPaint);

        textPaint.setColor(Color.WHITE);
        canvas.drawText(fTitle, fx, tagRect.centerY() + UiKit.pxf(5), textPaint);
    }

    private void drawRealisticSnake(Canvas canvas) {
        // 1. Draw Body Segment Floor Shadows
        for (int i = currentSegmentCount - 1; i >= 0; i--) {
            float frac = (float) i / currentSegmentCount;
            float r = UiKit.pxf(26f - frac * 17f);
            canvas.drawCircle(segX[i] + UiKit.pxf(8), segY[i] + UiKit.pxf(10), r * 1.15f, shadowPaint);
        }

        // 2. Draw 3D Cylindrical Segments from Tail to Neck
        for (int i = currentSegmentCount - 1; i >= 1; i--) {
            float frac = (float) i / currentSegmentCount;
            float r;
            if (i > currentSegmentCount * 0.75f) {
                r = UiKit.pxf(9f + (1f - (i - currentSegmentCount * 0.75f) / (currentSegmentCount * 0.25f)) * 14f);
            } else if (i < 4) {
                r = UiKit.pxf(19f + i * 1.5f);
            } else {
                r = UiKit.pxf(25f);
            }

            float cx = segX[i];
            float cy = segY[i];

            snakeBodyPaint.setStyle(Paint.Style.FILL);
            snakeBodyPaint.setShader(new RadialGradient(
                    cx - r * 0.35f, cy - r * 0.35f, r * 1.35f,
                    new int[]{0xFF6EE7B7, 0xFF10B981, 0xFF047857, 0xFF064E3B, 0xFF022C22},
                    new float[]{0f, 0.28f, 0.65f, 0.88f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, snakeBodyPaint);
            snakeBodyPaint.setShader(null);

            // Diamond scale texture across segment
            canvas.drawCircle(cx, cy, r * 0.75f, scalePaint);
        }

        // 3. Draw Anatomical 3D Viper Head
        draw3DViperHead(canvas);
    }

    private void draw3DViperHead(Canvas canvas) {
        canvas.save();
        canvas.translate(headX, headY);
        canvas.rotate((float) Math.toDegrees(headAngle));

        float hw = UiKit.pxf(44);
        float hh = UiKit.pxf(32);

        // Flicking Forked Tongue
        float tongueAnim = (float) Math.sin(tick * 0.22f);
        if (tongueAnim > 0.15f && !isGameOver) {
            float tLen = UiKit.pxf(32f * tongueAnim);
            tonguePath.reset();
            tonguePath.moveTo(hw * 0.5f, 0);
            tonguePath.lineTo(hw * 0.5f + tLen * 0.7f, 0);
            tonguePath.lineTo(hw * 0.5f + tLen, -UiKit.pxf(6));
            tonguePath.moveTo(hw * 0.5f + tLen * 0.7f, 0);
            tonguePath.lineTo(hw * 0.5f + tLen, UiKit.pxf(6));
            canvas.drawPath(tonguePath, tonguePaint);
        }

        // Viper Skull Silhouette
        headPath.reset();
        headPath.moveTo(hw * 0.55f, 0);
        headPath.lineTo(hw * 0.15f, -hh * 0.35f);
        headPath.lineTo(-hw * 0.35f, -hh * 0.58f);
        headPath.lineTo(-hw * 0.50f, 0);
        headPath.lineTo(-hw * 0.35f, hh * 0.58f);
        headPath.lineTo(hw * 0.15f, hh * 0.35f);
        headPath.close();

        // 3D Skull Shading
        snakeBodyPaint.setStyle(Paint.Style.FILL);
        snakeBodyPaint.setShader(new RadialGradient(0, -hh * 0.2f, hw * 0.9f,
                new int[]{0xFFA7F3D0, 0xFF10B981, 0xFF059669, 0xFF064E3B},
                new float[]{0f, 0.35f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(headPath, snakeBodyPaint);
        snakeBodyPaint.setShader(null);

        // Specular Ridge across snout
        scalePaint.setColor(0x44FFFFFF);
        canvas.drawLine(-hw * 0.2f, 0, hw * 0.45f, 0, scalePaint);

        // Golden Reptilian Slit-Pupil Eyes on Both Sides
        float eyeOffsetX = hw * 0.08f;
        float eyeOffsetY = hh * 0.40f;
        drawReptilianEye(canvas, eyeOffsetX, -eyeOffsetY);
        drawReptilianEye(canvas, eyeOffsetX, eyeOffsetY);

        // Nostril pits
        eyePaint.setColor(0x99064E3B);
        canvas.drawCircle(hw * 0.38f, -hh * 0.14f, UiKit.pxf(1.8f), eyePaint);
        canvas.drawCircle(hw * 0.38f, hh * 0.14f, UiKit.pxf(1.8f), eyePaint);

        canvas.restore();
    }

    private void drawReptilianEye(Canvas canvas, float ex, float ey) {
        float eyeR = UiKit.pxf(6f);

        // Golden Amber Iris
        eyePaint.setStyle(Paint.Style.FILL);
        eyePaint.setShader(new RadialGradient(ex, ey, eyeR,
                new int[]{0xFFFDE68A, 0xFFF59E0B, 0xFFB45309},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(ex, ey, eyeR, eyePaint);
        eyePaint.setShader(null);

        // Vertical Slit Pupil
        eyePaint.setColor(0xFF000000);
        RectF pupilRect = new RectF(ex - eyeR * 0.28f, ey - eyeR * 0.88f, ex + eyeR * 0.28f, ey + eyeR * 0.88f);
        canvas.drawOval(pupilRect, eyePaint);

        // Specular Glint reflection
        eyePaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(ex + eyeR * 0.35f, ey - eyeR * 0.35f, eyeR * 0.28f, eyePaint);
    }

    private void drawLiquidGlassHud(Canvas canvas, int w, int h) {
        // --- Top Liquid Glass Bar ---
        float topBarH = UiKit.pxf(72);
        RectF topBarRect = new RectF(UiKit.pxf(36), UiKit.pxf(24), w - UiKit.pxf(36), UiKit.pxf(24) + topBarH);

        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(0xD90B1220);
        canvas.drawRoundRect(topBarRect, UiKit.pxf(20), UiKit.pxf(20), hudPaint);

        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(UiKit.pxf(1.6f));
        hudPaint.setColor(0x3310B981);
        canvas.drawRoundRect(topBarRect, UiKit.pxf(20), UiKit.pxf(20), hudPaint);

        // Wordmark: "Snake" (white) + "Phile" (emerald #10B981)
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(UiKit.pxf(22));

        float tx = topBarRect.left + UiKit.pxf(28);
        float ty = topBarRect.centerY() + UiKit.pxf(8);

        textPaint.setColor(Color.WHITE);
        canvas.drawText("Snake", tx, ty, textPaint);
        tx += textPaint.measureText("Snake");

        textPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawText("Phile", tx, ty, textPaint);
        tx += textPaint.measureText("Phile");

        textPaint.setTextSize(UiKit.pxf(13));
        textPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawText(" · SİNEMATİK 3D ARCADE", tx + UiKit.pxf(10), ty - UiKit.pxf(2), textPaint);

        // Right side: Score Badges
        textPaint.setTextAlign(Paint.Align.RIGHT);
        float rx = topBarRect.right - UiKit.pxf(28);

        textPaint.setTextSize(UiKit.pxf(16));
        textPaint.setColor(Color.parseColor("#94A3B8"));
        String scoreStr = "SKOR: " + score + "   |   EN YÜKSEK: " + highScore;
        canvas.drawText(scoreStr, rx, ty, textPaint);

        // --- Bottom TV Remote D-Pad Navigation Pill ---
        if (!isGameOver) {
            float botBarW = UiKit.pxf(640);
            float botBarH = UiKit.pxf(48);
            RectF botBarRect = new RectF(w / 2f - botBarW / 2f, h - botBarH - UiKit.pxf(32),
                    w / 2f + botBarW / 2f, h - UiKit.pxf(32));

            hudPaint.setStyle(Paint.Style.FILL);
            hudPaint.setColor(0xD90B1220);
            canvas.drawRoundRect(botBarRect, botBarH / 2f, botBarH / 2f, hudPaint);

            hudPaint.setStyle(Paint.Style.STROKE);
            hudPaint.setStrokeWidth(UiKit.pxf(1.4f));
            hudPaint.setColor(0x28FFFFFF);
            canvas.drawRoundRect(botBarRect, botBarH / 2f, botBarH / 2f, hudPaint);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(UiKit.pxf(14));
            textPaint.setColor(Color.parseColor("#E2E8F0"));
            canvas.drawText("[▲▼◄►] Yönlendirme  ·  [OK] Hızlan  ·  [INFO/MENU] Nasıl Oynanır?  ·  [BACK] Menü",
                    botBarRect.centerX(), botBarRect.centerY() + UiKit.pxf(5), textPaint);
        }
    }

    private void drawHelpOverlay(Canvas canvas, int w, int h) {
        // Dimmed backdrop
        floorPaint.setStyle(Paint.Style.FILL);
        floorPaint.setColor(0xCC000000);
        canvas.drawRect(0, 0, w, h, floorPaint);

        float mw = UiKit.pxf(720);
        float mh = UiKit.pxf(440);
        float left = w / 2f - mw / 2f;
        float top = h / 2f - mh / 2f;
        RectF cardRect = new RectF(left, top, left + mw, top + mh);

        // Card Shadow & Liquid Glass Surface
        shadowPaint.setColor(0x99000000);
        canvas.drawRoundRect(left + UiKit.pxf(8), top + UiKit.pxf(12),
                left + mw + UiKit.pxf(8), top + mh + UiKit.pxf(12),
                UiKit.pxf(28), UiKit.pxf(28), shadowPaint);

        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(0xEE0B1220);
        canvas.drawRoundRect(cardRect, UiKit.pxf(28), UiKit.pxf(28), hudPaint);

        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(UiKit.pxf(1.8f));
        hudPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawRoundRect(cardRect, UiKit.pxf(28), UiKit.pxf(28), hudPaint);

        // Title
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(UiKit.pxf(24));
        textPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawText("SnakePhile · Nasıl Oynanır?", cardRect.centerX(), top + UiKit.pxf(44), textPaint);

        // Rules
        textPaint.setTextAlign(Paint.Align.LEFT);
        float rx = left + UiKit.pxf(36);
        float ry = top + UiKit.pxf(90);

        String[][] rules = {
                {"Amaç:", "3D yılanı yöneterek TMDB film afişlerini yiyin. Her afiş boyu uzatır."},
                {"Kontroller:", "TV Kumandası D-Pad tuşları veya telefondan dokunmatik / 3D Gyro sensörü."},
                {"Film Kuralı:", "Yılan kendini ısırdığında yiyemediği son film televizyonda otomatik başlar!"},
                {"Gamze AI:", "Oyun bitiminde Gamze AI yönetmen trivia ve film lore'unu sunar."}
        };

        for (String[] r : rules) {
            textPaint.setTextSize(UiKit.pxf(15));
            textPaint.setColor(Color.parseColor("#34D399"));
            canvas.drawText(r[0], rx, ry, textPaint);

            textPaint.setTextSize(UiKit.pxf(14));
            textPaint.setColor(Color.WHITE);
            canvas.drawText(r[1], rx + UiKit.pxf(110), ry, textPaint);
            ry += UiKit.pxf(56);
        }

        // Close Button
        float btnW = UiKit.pxf(220);
        float btnH = UiKit.pxf(44);
        RectF btnRect = new RectF(cardRect.centerX() - btnW / 2f, top + mh - UiKit.pxf(64),
                cardRect.centerX() + btnW / 2f, top + mh - UiKit.pxf(20));
        drawButton(canvas, btnRect, "Anladım (Kapat)", true, Color.parseColor("#10B981"));
    }

    private void drawGameOverModal(Canvas canvas, int w, int h) {
        // Dimmed backdrop
        floorPaint.setStyle(Paint.Style.FILL);
        floorPaint.setColor(0xB3000000);
        canvas.drawRect(0, 0, w, h, floorPaint);

        float mw = UiKit.pxf(780);
        float mh = UiKit.pxf(480);
        float left = w / 2f - mw / 2f;
        float top = h / 2f - mh / 2f;
        RectF cardRect = new RectF(left, top, left + mw, top + mh);

        // Card Shadow
        shadowPaint.setColor(0x99000000);
        canvas.drawRoundRect(left + UiKit.pxf(8), top + UiKit.pxf(12),
                left + mw + UiKit.pxf(8), top + mh + UiKit.pxf(12),
                UiKit.pxf(28), UiKit.pxf(28), shadowPaint);

        // Liquid Glass Surface
        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(0xEE0B1220);
        canvas.drawRoundRect(cardRect, UiKit.pxf(28), UiKit.pxf(28), hudPaint);

        // Crisp Border
        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(UiKit.pxf(1.8f));
        hudPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawRoundRect(cardRect, UiKit.pxf(28), UiKit.pxf(28), hudPaint);

        // Header: Brand & Title
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(UiKit.pxf(26));
        textPaint.setColor(Color.parseColor("#EF4444"));
        canvas.drawText("OYUN BİTTİ · YILAN KENDİNİ ISIRDI!", cardRect.centerX(), top + UiKit.pxf(48), textPaint);

        textPaint.setTextSize(UiKit.pxf(15));
        textPaint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawText("Yiyemediğin son film bu akşam salonda izlenmeye hak kazandı:",
                cardRect.centerX(), top + UiKit.pxf(78), textPaint);

        // Center: Failed Movie Poster & Details
        float pw = UiKit.pxf(120);
        float ph = pw * 1.48f;
        float pLeft = left + UiKit.pxf(60);
        float pTop = top + UiKit.pxf(110);
        RectF pRect = new RectF(pLeft, pTop, pLeft + pw, pTop + ph);
        float pCorner = pw * 0.085f;

        Bitmap posterBmp = (currentFoodIndex < posterBitmaps.size()) ? posterBitmaps.get(currentFoodIndex) : null;
        if (posterBmp != null) {
            clipPath.reset();
            clipPath.addRoundRect(pRect, pCorner, pCorner, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawBitmap(posterBmp, null, pRect, null);
            canvas.restore();
        } else {
            posterPaint.setStyle(Paint.Style.FILL);
            posterPaint.setColor(Color.parseColor("#1E3A8A"));
            canvas.drawRoundRect(pRect, pCorner, pCorner, posterPaint);
        }

        // Specular & Border
        posterPaint.setStyle(Paint.Style.STROKE);
        posterPaint.setStrokeWidth(UiKit.pxf(1.6f));
        posterPaint.setColor(0x88FFFFFF);
        canvas.drawRoundRect(pRect, pCorner, pCorner, posterPaint);

        // Movie Info Text on right of poster
        float textX = pRect.right + UiKit.pxf(32);
        float textY = pTop + UiKit.pxf(40);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(UiKit.pxf(24));
        textPaint.setColor(Color.WHITE);
        String currentRaw = POSTER_TITLES[currentFoodIndex % POSTER_TITLES.length];
        String movieTitle = cleanMovieTitle(currentRaw);
        canvas.drawText(movieTitle, textX, textY, textPaint);

        textPaint.setTextSize(UiKit.pxf(14));
        textPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawText("BU AKŞAMIN TERCİHİ  ·  Skor: " + score + " Puan", textX, textY + UiKit.pxf(26), textPaint);

        // Gamze AI Lore Card
        float loreW = mw - UiKit.pxf(60) - pw - UiKit.pxf(60);
        float loreH = UiKit.pxf(68);
        RectF loreRect = new RectF(textX, textY + UiKit.pxf(38), textX + loreW, textY + UiKit.pxf(38) + loreH);

        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(0x2810B981);
        canvas.drawRoundRect(loreRect, UiKit.pxf(14), UiKit.pxf(14), hudPaint);

        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(UiKit.pxf(1.2f));
        hudPaint.setColor(0x5510B981);
        canvas.drawRoundRect(loreRect, UiKit.pxf(14), UiKit.pxf(14), hudPaint);

        textPaint.setTextSize(UiKit.pxf(11.5f));
        textPaint.setColor(Color.parseColor("#6EE7B7"));
        canvas.drawText("GAMZE AI SİNEMA NOTU:", textX + UiKit.pxf(14), loreRect.top + UiKit.pxf(20), textPaint);

        textPaint.setTextSize(UiKit.pxf(12.5f));
        textPaint.setColor(Color.WHITE);
        String loreDisplay = (gamzeLore != null && !gamzeLore.isEmpty())
                ? gamzeLore : "Yönetmen vizyonu ve sinematografik derinliğiyle öne çıkan modern bir başyapıt.";
        if (loreDisplay.length() > 68) loreDisplay = loreDisplay.substring(0, 65) + "...";
        canvas.drawText(loreDisplay, textX + UiKit.pxf(14), loreRect.top + UiKit.pxf(44), textPaint);

        // Buttons Row at bottom
        float btnY = top + mh - UiKit.pxf(80);
        float btnH = UiKit.pxf(48);

        // Button 1: [ Şimdi TV'de Başlat ]
        float btn1W = UiKit.pxf(260);
        btnLaunchRect.set(left + UiKit.pxf(60), btnY, left + UiKit.pxf(60) + btn1W, btnY + btnH);
        drawButton(canvas, btnLaunchRect, "Şimdi TV'de Başlat", gameOverFocusIndex == 0, Color.parseColor("#10B981"));

        // Button 2: [ Yeniden Oyna ]
        float btn2W = UiKit.pxf(180);
        btnRestartRect.set(btnLaunchRect.right + UiKit.pxf(20), btnY, btnLaunchRect.right + UiKit.pxf(20) + btn2W, btnY + btnH);
        drawButton(canvas, btnRestartRect, "Yeniden", gameOverFocusIndex == 1, Color.parseColor("#38BDF8"));

        // Button 3: [ Çıkış ]
        float btn3W = UiKit.pxf(140);
        btnExitRect.set(btnRestartRect.right + UiKit.pxf(20), btnY, btnRestartRect.right + UiKit.pxf(20) + btn3W, btnY + btnH);
        drawButton(canvas, btnExitRect, "Çıkış", gameOverFocusIndex == 2, Color.parseColor("#64748B"));
    }

    private void drawButton(Canvas canvas, RectF rect, String text, boolean isFocused, int accentColor) {
        hudPaint.setStyle(Paint.Style.FILL);
        if (isFocused) {
            hudPaint.setColor(accentColor);
        } else {
            hudPaint.setColor(0x33FFFFFF);
        }
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, hudPaint);

        hudPaint.setStyle(Paint.Style.STROKE);
        hudPaint.setStrokeWidth(UiKit.pxf(isFocused ? 2.5f : 1.2f));
        hudPaint.setColor(isFocused ? Color.WHITE : 0x55FFFFFF);
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, hudPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(UiKit.pxf(15));
        textPaint.setColor(isFocused ? Color.parseColor("#0B1220") : Color.WHITE);
        canvas.drawText(text, rect.centerX(), rect.centerY() + UiKit.pxf(5), textPaint);
    }
}
