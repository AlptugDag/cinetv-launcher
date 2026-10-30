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
import android.graphics.RadialGradient;
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
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_AUDIO) {
                        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
                        statusTitle.setText(tr ? "Ses anlaşılamadı" : "Could not understand");
                        statusTitle.setTextColor(Color.parseColor("#F87171"));
                        statusSub.setText(tr ? "Mikrofon düğmesine basıp tekrar deneyin" : "Press the mic button and try again");
                        statusSub.setTextColor(Color.parseColor("#FCA5A5"));
                        main.postDelayed(VoiceOverlay.this::dismiss, 1800);
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
     * Apple TV / visionOS inspired ambient floor bloom view.
     * Renders an organic, acoustic-reactive luminous floor bloom anchored beneath
     * the floating liquid-glass capsule, with zero heap allocations during animation.
     */
    private static final class SiriEdgeWavesView extends View {
        private final Paint bloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Matrix bloomMatrix = new Matrix();

        private RadialGradient listeningBloomShader;
        private RadialGradient successBloomShader;
        private RadialGradient coreBloomShader;

        private float volume = 0f;
        private float targetVolume = 0f;
        private boolean listening = false;
        private boolean success = false;
        private float phase = 0f;
        private float speechPulse = 0f;

        SiriEdgeWavesView(Context ctx) {
            super(ctx);
            bloomPaint.setStyle(Paint.Style.FILL);
            corePaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;

            // Unit radial shaders (radius = 1.0f) transformed via Matrix for zero-allocation rendering
            listeningBloomShader = new RadialGradient(0f, 0f, 1.0f,
                    new int[]{0x6600F0FF, 0x483B82F6, 0x2A8B5CF6, 0x0EEC4899, 0x00000000},
                    new float[]{0f, 0.35f, 0.65f, 0.88f, 1.0f},
                    Shader.TileMode.CLAMP);

            successBloomShader = new RadialGradient(0f, 0f, 1.0f,
                    new int[]{0x7734D399, 0x4410B981, 0x18059669, 0x00000000},
                    new float[]{0f, 0.40f, 0.75f, 1.0f},
                    Shader.TileMode.CLAMP);

            coreBloomShader = new RadialGradient(0f, 0f, 1.0f,
                    new int[]{0x88FFFFFF, 0x4438BDF8, 0x0038BDF8},
                    new float[]{0f, 0.45f, 1.0f},
                    Shader.TileMode.CLAMP);
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
            if (w <= 0 || h <= 0 || listeningBloomShader == null) return;

            // Natural acoustic spring physics
            targetVolume *= 0.87f;
            if (speechPulse > 0f) {
                targetVolume = Math.max(targetVolume, speechPulse * 0.78f);
                speechPulse = Math.max(0f, speechPulse - 0.035f);
            }
            volume += (targetVolume - volume) * 0.32f;

            float breathe = listening ? (0.12f + (float) Math.sin(phase * 1.5f) * 0.05f) : 0.03f;
            float effectiveVol = Math.max(breathe, volume);
            phase += 0.045f + effectiveVol * 0.08f;

            float cx = w * 0.5f;
            float cy = h - UiKit.pxf(48f); // Anchored right beneath the floating capsule

            // 1. Broad elliptical ambient floor bloom
            float rx = UiKit.pxf(440f) + effectiveVol * UiKit.pxf(160f);
            float ry = UiKit.pxf(140f) + effectiveVol * UiKit.pxf(60f);

            bloomMatrix.reset();
            bloomMatrix.setScale(rx, ry);
            bloomMatrix.postTranslate(cx, cy);

            RadialGradient activeShader = success ? successBloomShader : listeningBloomShader;
            activeShader.setLocalMatrix(bloomMatrix);
            bloomPaint.setShader(activeShader);
            bloomPaint.setAlpha(success ? 220 : Math.min(240, (int) (110 + effectiveVol * 130)));
            c.drawRect(0, h - ry * 1.6f, w, h, bloomPaint);

            // 2. Focused luminous core bloom under capsule
            if (effectiveVol > 0.08f || success) {
                float rxCore = rx * 0.42f;
                float ryCore = ry * 0.40f;
                bloomMatrix.reset();
                bloomMatrix.setScale(rxCore, ryCore);
                bloomMatrix.postTranslate(cx, cy);
                coreBloomShader.setLocalMatrix(bloomMatrix);
                corePaint.setShader(coreBloomShader);
                corePaint.setAlpha(success ? 160 : Math.min(180, (int) (effectiveVol * 190)));
                c.drawRect(cx - rxCore, cy - ryCore, cx + rxCore, cy + ryCore, corePaint);
            }

            if (listening || volume > 0.01f || isAttachedToWindow()) {
                postInvalidateOnAnimation();
            }
        }
    }
}
