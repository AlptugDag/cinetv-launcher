package com.cinetv.launcher.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.device.PerformanceProfile;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Studio-grade in-app Voice Search & Control Overlay.
 *
 * <p>Features a zero-allocation, multi-harmonic chromatic perimeter light ribbon inspired by
 * visionOS / Apple Intelligence, paired with a live 9-band acoustic spectrum visualizer inside
 * the floating liquid-glass capsule and guaranteed immediate microphone release so Android 12–14's
 * green privacy dot never sticks on screen.
 */
public class VoiceOverlay extends FrameLayout {

    public interface Listener {
        void onResult(String text);
        void onFallback();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Listener listener;
    private boolean active;

    private LinearLayout card;
    private SiriEdgeWavesView siriWaves;
    private AcousticPillWaveView pillWave;
    private TextView statusTitle;
    private TextView statusSub;
    private TextView resultText;

    public VoiceOverlay(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        setFocusable(true);
        setClickable(true);
        setBackgroundColor(Color.argb(115, 3, 4, 8));
        buildUi(ctx);
    }

    private void buildUi(Context ctx) {
        // Multi-layered sound-reactive chromatic perimeter aura
        siriWaves = new SiriEdgeWavesView(ctx);
        addView(siriWaves, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Floating crystal capsule at bottom-center
        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = UiKit.px(42), padV = UiKit.px(22);
        card.setPadding(padH, padV, padH, padV);
        card.setBackground(Glass.panel(card, 34));
        card.setClipChildren(false);

        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER);

        pillWave = new AcousticPillWaveView(ctx);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(UiKit.px(64), UiKit.px(28));
        wlp.rightMargin = UiKit.px(14);
        headerRow.addView(pillWave, wlp);

        statusTitle = UiKit.bold(ctx, 22, UiKit.INK);
        statusTitle.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.addView(statusTitle);

        card.addView(headerRow);

        resultText = UiKit.bold(ctx, 24, Color.parseColor("#38BDF8"));
        resultText.setGravity(Gravity.CENTER);
        resultText.setVisibility(GONE);
        resultText.setPadding(0, UiKit.px(8), 0, 0);
        card.addView(resultText);

        statusSub = UiKit.label(ctx, 15, UiKit.INK_DIM);
        statusSub.setGravity(Gravity.CENTER);
        statusSub.setPadding(0, UiKit.px(6), 0, 0);
        card.addView(statusSub);

        LayoutParams clp = new LayoutParams(UiKit.px(600), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = UiKit.px(56);
        addView(card, clp);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE && active;
    }

    public void start(Listener l) {
        this.listener = l;
        this.active = true;

        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            dismiss();
            if (listener != null) listener.onFallback();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            dismiss();
            if (listener != null) listener.onFallback();
            return;
        }

        cleanup();

        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        statusTitle.setText(tr ? "Dinleniyor..." : "Listening...");
        statusTitle.setTextColor(UiKit.INK);
        statusSub.setText(tr ? "Bir film, yönetmen, soru veya TV komutu söyleyin" : "Say a film, director, question or TV command");
        resultText.setVisibility(GONE);
        siriWaves.reset();
        pillWave.reset();

        setAlpha(0f);
        setVisibility(VISIBLE);
        int animDuration = PerformanceProfile.animationMs(getContext(), 340);
        animate().alpha(1f).setDuration(animDuration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        card.setTranslationY(UiKit.pxf(32));
        card.setAlpha(0f);
        card.animate().translationY(0f).alpha(1f).setDuration(animDuration + 30)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        requestFocus();

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    siriWaves.setListening(true);
                    pillWave.setListening(true);
                }

                @Override
                public void onBeginningOfSpeech() {
                    siriWaves.onSpeechStarted();
                    pillWave.onSpeechStarted();
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    siriWaves.setVolume(rmsdB);
                    pillWave.setVolume(rmsdB);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    if (buffer != null && buffer.length > 0) {
                        long sum = 0;
                        int count = buffer.length / 2;
                        for (int i = 0; i < buffer.length - 1; i += 2) {
                            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                            sum += Math.abs(sample);
                        }
                        if (count > 0) {
                            float avg = sum / (float) count;
                            float norm = Math.max(0f, Math.min(1f, avg / 2600f));
                            siriWaves.setRawEnergy(norm);
                            pillWave.setRawEnergy(norm);
                        }
                    }
                }

                @Override
                public void onEndOfSpeech() {
                    siriWaves.setListening(false);
                    pillWave.setListening(false);
                    boolean tr = "tr".equals(Locale.getDefault().getLanguage());
                    statusTitle.setText(tr ? "İşleniyor..." : "Processing...");
                }

                @Override
                public void onError(int error) {
                    cleanup();
                    if (!active) return;
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
                        statusTitle.setText(tr ? "Ses anlaşılamadı" : "No speech detected");
                        statusTitle.setTextColor(Color.parseColor("#F87171"));
                        main.postDelayed(VoiceOverlay.this::dismiss, 1200);
                    } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        dismiss();
                        if (listener != null) listener.onFallback();
                    } else {
                        dismiss();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    cleanup(); // Stop and destroy recognizer immediately so mic privacy indicator turns off
                    if (!active) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        final String said = matches.get(0);
                        statusTitle.setText("tr".equals(Locale.getDefault().getLanguage()) ? "Anlaşıldı" : "Heard");
                        statusTitle.setTextColor(Color.parseColor("#34D399"));
                        resultText.setText("“" + said + "”");
                        resultText.setVisibility(VISIBLE);
                        siriWaves.setSuccess(true);
                        pillWave.setSuccess(true);
                        main.postDelayed(() -> {
                            dismiss();
                            if (listener != null) listener.onResult(said);
                        }, 480);
                    } else {
                        dismiss();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        resultText.setText("“" + partial.get(0) + "”");
                        resultText.setVisibility(VISIBLE);
                        siriWaves.onSpeechStarted();
                        pillWave.onSpeechStarted();
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            startBluetoothScoIfConnected();
            recognizer.startListening(intent);
        } catch (Throwable t) {
            cleanup();
            dismiss();
            if (listener != null) listener.onFallback();
        }
    }

    public void dismiss() {
        if (!active && getVisibility() != VISIBLE) return;
        active = false;
        cleanup();
        card.animate().translationY(UiKit.pxf(25)).alpha(0f)
                .setDuration(PerformanceProfile.animationMs(getContext(), 180)).start();
        animate().alpha(0f).setDuration(PerformanceProfile.animationMs(getContext(), 220)).withEndAction(() -> {
            setVisibility(GONE);
            siriWaves.reset();
            pillWave.reset();
        }).start();
    }

    private boolean scoActive = false;

    private void startBluetoothScoIfConnected() {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (am != null && (am.isBluetoothA2dpOn() || am.isBluetoothScoAvailableOffCall())) {
                am.startBluetoothSco();
                am.setBluetoothScoOn(true);
                scoActive = true;
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvVoice", "Bluetooth SCO initiation: " + t);
        }
    }

    private void stopBluetoothSco() {
        if (!scoActive) return;
        scoActive = false;
        try {
            android.media.AudioManager am = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.stopBluetoothSco();
                am.setBluetoothScoOn(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void cleanup() {
        stopBluetoothSco();
        if (recognizer != null) {
            try {
                recognizer.stopListening();
                recognizer.cancel();
                recognizer.destroy();
            } catch (Throwable ignored) {
            }
            recognizer = null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (active && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                dismiss();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        cleanup();
        super.onDetachedFromWindow();
    }

    /**
     * 9-band live acoustic spectrum indicator inside the floating glass capsule.
     * Pre-allocates all paints and rects for zero GC churn during 60fps animation.
     */
    private static final class AcousticPillWaveView extends View {
        private static final int BAR_COUNT = 9;
        private static final float[] BAR_WEIGHTS = {0.42f, 0.68f, 0.92f, 0.75f, 1.0f, 0.80f, 0.90f, 0.64f, 0.40f};
        private static final int[] BAR_COLORS = {
                0xFF00F0FF, 0xFF38BDF8, 0xFF6366F1, 0xFF8B5CF6,
                0xFFEC4899, 0xFFF43F5E, 0xFFF59E0B, 0xFF8B5CF6, 0xFF00F0FF
        };

        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF barRect = new RectF();
        private float volume = 0f;
        private float targetVolume = 0f;
        private float phase = 0f;
        private boolean listening = false;
        private boolean success = false;

        AcousticPillWaveView(Context ctx) {
            super(ctx);
            barPaint.setStyle(Paint.Style.FILL);
        }

        void setListening(boolean l) { this.listening = l; invalidate(); }
        void onSpeechStarted() { this.targetVolume = Math.max(this.targetVolume, 0.68f); invalidate(); }
        void setVolume(float rmsdB) {
            float norm = Math.max(0f, Math.min(1f, (rmsdB + 14f) / 21f));
            this.targetVolume = Math.max(this.targetVolume, norm);
            invalidate();
        }
        void setRawEnergy(float norm) {
            this.targetVolume = Math.max(this.targetVolume, norm);
            invalidate();
        }
        void setSuccess(boolean s) { this.success = s; invalidate(); }
        void reset() {
            this.volume = 0f;
            this.targetVolume = 0f;
            this.listening = false;
            this.success = false;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            targetVolume *= 0.86f;
            volume += (targetVolume - volume) * 0.34f;
            float base = listening ? 0.22f : 0.10f;
            float eff = Math.max(base, volume);
            phase += 0.14f + eff * 0.18f;

            float gap = UiKit.pxf(3.2f);
            float barW = (w - gap * (BAR_COUNT - 1)) / BAR_COUNT;
            float cy = h * 0.5f;
            float minH = UiKit.pxf(4f);
            float maxH = h * 0.92f;

            for (int i = 0; i < BAR_COUNT; i++) {
                float harmonic = 0.5f + 0.5f * (float) Math.sin(phase + i * 0.72f + Math.cos(phase * 0.6f - i));
                float level = Math.min(1f, (0.18f + eff * 0.88f) * BAR_WEIGHTS[i] * (0.45f + 0.55f * harmonic));
                float bh = Math.max(minH, level * maxH);
                float left = i * (barW + gap);
                barRect.set(left, cy - bh * 0.5f, left + barW, cy + bh * 0.5f);
                barPaint.setColor(success ? 0xFF34D399 : BAR_COLORS[i]);
                c.drawRoundRect(barRect, barW * 0.5f, barW * 0.5f, barPaint);
            }

            if (listening || volume > 0.01f || isAttachedToWindow()) {
                postInvalidateOnAnimation();
            }
        }
    }

    /**
     * Studio-grade, zero-allocation Chromatic Perimeter Light Ribbon.
     *
     * <p>Renders:
     * <ol>
     *   <li>4 smooth inward volumetric edge vignettes pre-allocated in {@link #onSizeChanged},</li>
     *   <li>A rotating 360° {@link SweepGradient} driven by a reusable {@link Matrix} (zero per-frame heap allocations),</li>
     *   <li>3 concentric smooth Bezier-curved perimeter ribbons (Outer Aura, Primary Silk Wave, Specular Core Filament)
     *       that hug the screen corners and billow organically in response to live voice frequencies.</li>
     * </ol>
     */
    private static final class SiriEdgeWavesView extends View {
        private final Paint edgeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint primaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint harmonicPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint filamentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path primaryPath = new Path();
        private final Path harmonicPath = new Path();
        private final Matrix sweepMatrix = new Matrix();
        private final Matrix glowMatrix = new Matrix();

        private SweepGradient chromaticSweep;
        private SweepGradient harmonicSweep;
        private LinearGradient unitBottomGlow;
        private LinearGradient unitTopGlow;
        private LinearGradient unitLeftGlow;
        private LinearGradient unitRightGlow;

        private float volume = 0f;
        private float targetVolume = 0f;
        private boolean listening = false;
        private boolean success = false;
        private float phase = 0f;
        private float rotationDeg = 0f;
        private float speechPulse = 0f;

        // Rich studio cinema & visionOS spectrum
        private static final int[] CHROMATIC_COLORS = {
                0xFF00F0FF, // Electric Cyan
                0xFF3B82F6, // Royal Sapphire
                0xFF8B5CF6, // Amethyst Violet
                0xFFEC4899, // Luminous Rose
                0xFFF43F5E, // Cinema Crimson
                0xFFF59E0B, // Solar Amber
                0xFF10B981, // Emerald Mint
                0xFF00F0FF  // Seamless loop
        };
        private static final float[] CHROMATIC_POSITIONS = {
                0.0f, 0.14f, 0.30f, 0.46f, 0.62f, 0.78f, 0.90f, 1.0f
        };

        // Pre-allocated vertex buffers for smooth closed Catmull/Bezier spline perimeter
        private static final int PERIMETER_POINTS = 96;
        private final float[] px = new float[PERIMETER_POINTS];
        private final float[] py = new float[PERIMETER_POINTS];
        private final float[] hx = new float[PERIMETER_POINTS];
        private final float[] hy = new float[PERIMETER_POINTS];

        SiriEdgeWavesView(Context ctx) {
            super(ctx);
            for (Paint p : new Paint[]{auraPaint, primaryPaint, harmonicPaint, filamentPaint}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
            }
            filamentPaint.setColor(0xEEFFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;
            float cx = w * 0.5f, cy = h * 0.5f;
            chromaticSweep = new SweepGradient(cx, cy, CHROMATIC_COLORS, CHROMATIC_POSITIONS);
            harmonicSweep = new SweepGradient(cx, cy, CHROMATIC_COLORS, CHROMATIC_POSITIONS);

            // Unit shaders (length = 1px) scaled via glowMatrix so zero LinearGradients are allocated in onDraw
            unitBottomGlow = new LinearGradient(0, 0, 0, 1f,
                    new int[]{0x00000000, 0x663B82F6, 0xCC00F0FF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            unitTopGlow = new LinearGradient(0, 0, 0, 1f,
                    new int[]{0xB3EC4899, 0x4D8B5CF6, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            unitLeftGlow = new LinearGradient(0, 0, 1f, 0,
                    new int[]{0xB38B5CF6, 0x4D3B82F6, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            unitRightGlow = new LinearGradient(0, 0, 1f, 0,
                    new int[]{0x00000000, 0x4DF59E0B, 0xB3F43F5E},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }

        void setListening(boolean l) {
            this.listening = l;
            invalidate();
        }

        void onSpeechStarted() {
            this.speechPulse = 1.0f;
            invalidate();
        }

        void setVolume(float rmsdB) {
            float norm = Math.max(0f, Math.min(1f, (rmsdB + 14f) / 21f));
            this.targetVolume = Math.max(this.targetVolume, norm);
            invalidate();
        }

        void setRawEnergy(float norm) {
            this.targetVolume = Math.max(this.targetVolume, norm);
            invalidate();
        }

        void setSuccess(boolean s) {
            this.success = s;
            invalidate();
        }

        void reset() {
            this.volume = 0f;
            this.targetVolume = 0f;
            this.listening = false;
            this.success = false;
            this.phase = 0f;
            this.speechPulse = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || chromaticSweep == null) return;

            // Smooth spring physics for natural acoustic response
            targetVolume *= 0.87f;
            if (speechPulse > 0f) {
                targetVolume = Math.max(targetVolume, speechPulse * 0.78f);
                speechPulse = Math.max(0f, speechPulse - 0.035f);
            }
            volume += (targetVolume - volume) * 0.32f;

            float breathe = listening ? (0.14f + (float) Math.sin(phase * 1.3f) * 0.06f) : 0.04f;
            float effectiveVol = Math.max(breathe, volume);

            phase += 0.042f + effectiveVol * 0.095f;
            rotationDeg = (rotationDeg + 0.85f + effectiveVol * 2.4f) % 360f;

            float cx = w * 0.5f, cy = h * 0.5f;
            sweepMatrix.setRotate(rotationDeg, cx, cy);
            chromaticSweep.setLocalMatrix(sweepMatrix);

            sweepMatrix.setRotate((rotationDeg + 140f) % 360f, cx, cy);
            harmonicSweep.setLocalMatrix(sweepMatrix);

            // 1. Soft Volumetric Edge Vignettes (scaled via Matrix with zero allocations)
            int glowAlpha = success ? 150 : Math.min(230, (int) (75 + effectiveVol * 145));
            edgeGlowPaint.setAlpha(glowAlpha);

            float botH = UiKit.pxf(56f) + effectiveVol * UiKit.pxf(68f);
            glowMatrix.setScale(1f, botH);
            glowMatrix.postTranslate(0f, h - botH);
            unitBottomGlow.setLocalMatrix(glowMatrix);
            edgeGlowPaint.setShader(unitBottomGlow);
            c.drawRect(0, h - botH, w, h, edgeGlowPaint);

            float topH = UiKit.pxf(38f) + effectiveVol * UiKit.pxf(46f);
            glowMatrix.setScale(1f, topH);
            unitTopGlow.setLocalMatrix(glowMatrix);
            edgeGlowPaint.setShader(unitTopGlow);
            c.drawRect(0, 0, w, topH, edgeGlowPaint);

            float sideW = UiKit.pxf(40f) + effectiveVol * UiKit.pxf(46f);
            glowMatrix.setScale(sideW, 1f);
            unitLeftGlow.setLocalMatrix(glowMatrix);
            edgeGlowPaint.setShader(unitLeftGlow);
            c.drawRect(0, 0, sideW, h, edgeGlowPaint);

            glowMatrix.setScale(sideW, 1f);
            glowMatrix.postTranslate(w - sideW, 0f);
            unitRightGlow.setLocalMatrix(glowMatrix);
            edgeGlowPaint.setShader(unitRightGlow);
            c.drawRect(w - sideW, 0, w, h, edgeGlowPaint);

            // 2. Compute smooth perimeter wave coordinates with rounded corners
            float baseInset = UiKit.pxf(8f);
            float amp = UiKit.pxf(5f) + effectiveVol * UiKit.pxf(24f);
            float cornerPush = UiKit.pxf(18f);

            int nTop = 28, nSide = 20; // 28 + 20 + 28 + 20 = 96 points
            int idx = 0;

            // Top edge (left -> right)
            for (int i = 0; i < nTop; i++) {
                float u = i / (float) nTop;
                float cornerPinch = (float) Math.sin(u * Math.PI);
                float cornerCurve = (1f - cornerPinch) * cornerPush;
                float wave1 = (float) (Math.sin(phase * 1.4f + i * 0.42f) + 0.45 * Math.cos(phase * 2.3f - i * 0.65f)) * 0.68f;
                float wave2 = (float) (Math.cos(phase * 1.9f - i * 0.50f) + 0.40 * Math.sin(phase * 2.7f + i * 0.35f)) * 0.68f;
                px[idx] = baseInset + u * (w - 2f * baseInset);
                py[idx] = baseInset + cornerCurve + Math.max(0f, wave1) * amp * cornerPinch;
                hx[idx] = px[idx];
                hy[idx] = baseInset + cornerCurve + Math.max(0f, wave2) * amp * 0.85f * cornerPinch;
                idx++;
            }

            // Right edge (top -> bottom)
            for (int i = 0; i < nSide; i++) {
                float u = i / (float) nSide;
                float cornerPinch = (float) Math.sin(u * Math.PI);
                float cornerCurve = (1f - cornerPinch) * cornerPush;
                float wave1 = (float) (Math.sin(phase * 1.6f + (nTop + i) * 0.46f) + 0.45 * Math.sin(phase * 2.5f - i * 0.6f)) * 0.68f;
                float wave2 = (float) (Math.cos(phase * 2.1f - (nTop + i) * 0.48f)) * 0.85f;
                py[idx] = baseInset + u * (h - 2f * baseInset);
                px[idx] = (w - baseInset - cornerCurve) - Math.max(0f, wave1) * amp * cornerPinch;
                hy[idx] = py[idx];
                hx[idx] = (w - baseInset - cornerCurve) - Math.max(0f, wave2) * amp * 0.85f * cornerPinch;
                idx++;
            }

            // Bottom edge (right -> left) — strongest acoustic responsiveness near the voice capsule
            float botAmp = amp * 1.38f;
            for (int i = 0; i < nTop; i++) {
                float u = i / (float) nTop;
                float cornerPinch = (float) Math.sin(u * Math.PI);
                float cornerCurve = (1f - cornerPinch) * cornerPush;
                float wave1 = (float) (Math.sin(phase * 2.1f + i * 0.48f) + 0.55 * Math.cos(phase * 3.1f - i * 0.72f)) * 0.65f;
                float wave2 = (float) (Math.cos(phase * 2.6f - i * 0.52f) + 0.45 * Math.sin(phase * 1.7f + i * 0.60f)) * 0.65f;
                px[idx] = (w - baseInset) - u * (w - 2f * baseInset);
                py[idx] = (h - baseInset - cornerCurve) - Math.max(0f, wave1) * botAmp * cornerPinch;
                hx[idx] = px[idx];
                hy[idx] = (h - baseInset - cornerCurve) - Math.max(0f, wave2) * botAmp * 0.82f * cornerPinch;
                idx++;
            }

            // Left edge (bottom -> top)
            for (int i = 0; i < nSide; i++) {
                float u = i / (float) nSide;
                float cornerPinch = (float) Math.sin(u * Math.PI);
                float cornerCurve = (1f - cornerPinch) * cornerPush;
                float wave1 = (float) (Math.sin(phase * 1.5f + i * 0.45f) + 0.45 * Math.cos(phase * 2.4f + i * 0.55f)) * 0.68f;
                float wave2 = (float) (Math.cos(phase * 2.0f - i * 0.48f)) * 0.85f;
                py[idx] = (h - baseInset) - u * (h - 2f * baseInset);
                px[idx] = baseInset + cornerCurve + Math.max(0f, wave1) * amp * cornerPinch;
                hy[idx] = py[idx];
                hx[idx] = baseInset + cornerCurve + Math.max(0f, wave2) * amp * 0.85f * cornerPinch;
                idx++;
            }

            buildSmoothClosedPath(primaryPath, px, py, PERIMETER_POINTS);
            buildSmoothClosedPath(harmonicPath, hx, hy, PERIMETER_POINTS);

            // 3. Pass 1: Wide Volumetric Aura Ribbon
            auraPaint.setStrokeWidth(UiKit.pxf(18f) + effectiveVol * UiKit.pxf(22f));
            if (success) {
                auraPaint.setShader(null);
                auraPaint.setColor(0x6610B981);
            } else {
                auraPaint.setShader(chromaticSweep);
                auraPaint.setAlpha(Math.min(180, (int) (70 + effectiveVol * 105)));
            }
            c.drawPath(primaryPath, auraPaint);

            // 4. Pass 2: Secondary Counter-Rotating Harmonic Silk Ribbon
            harmonicPaint.setStrokeWidth(UiKit.pxf(4.5f) + effectiveVol * UiKit.pxf(6f));
            if (success) {
                harmonicPaint.setShader(null);
                harmonicPaint.setColor(0xAA34D399);
            } else {
                harmonicPaint.setShader(harmonicSweep);
                harmonicPaint.setAlpha(Math.min(220, (int) (110 + effectiveVol * 105)));
            }
            c.drawPath(harmonicPath, harmonicPaint);

            // 5. Pass 3: Primary Chromatic Wave Ribbon
            primaryPaint.setStrokeWidth(UiKit.pxf(6.5f) + effectiveVol * UiKit.pxf(8.5f));
            if (success) {
                primaryPaint.setShader(null);
                primaryPaint.setColor(0xFF34D399);
            } else {
                primaryPaint.setShader(chromaticSweep);
                primaryPaint.setAlpha(245);
            }
            c.drawPath(primaryPath, primaryPaint);

            // 6. Pass 4: Specular OLED Core Filament
            filamentPaint.setStrokeWidth(UiKit.pxf(2.0f) + effectiveVol * UiKit.pxf(1.5f));
            filamentPaint.setAlpha(Math.min(255, (int) (135 + effectiveVol * 120)));
            c.drawPath(primaryPath, filamentPaint);

            if (listening || volume > 0.01f || isAttachedToWindow()) {
                postInvalidateOnAnimation();
            }
        }

        /**
         * Connects periodic perimeter points with midpoint-quadratic Bezier splines so corners
         * and wave crests are 100% smooth with zero polygonal kinks.
         */
        private static void buildSmoothClosedPath(Path out, float[] xs, float[] ys, int n) {
            out.reset();
            float startMidX = (xs[n - 1] + xs[0]) * 0.5f;
            float startMidY = (ys[n - 1] + ys[0]) * 0.5f;
            out.moveTo(startMidX, startMidY);
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                float midX = (xs[i] + xs[next]) * 0.5f;
                float midY = (ys[i] + ys[next]) * 0.5f;
                out.quadTo(xs[i], ys[i], midX, midY);
            }
            out.close();
        }
    }
}
