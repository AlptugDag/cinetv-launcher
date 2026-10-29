package com.cinetv.launcher.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.R;
import com.cinetv.launcher.device.PerformanceProfile;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Modern, fluid in-app Voice Search Overlay.
 * Replaces the mismatched external Google Assistant dialog with a sleek frosted-glass platter,
 * real-time animated acoustic soundwaves, and guaranteed audio hardware release that prevents
 * the Android 12-14 green microphone privacy indicator dot from getting stuck on screen.
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
    private TextView statusTitle;
    private TextView statusSub;
    private TextView resultText;

    public VoiceOverlay(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        setFocusable(true);
        setClickable(true);
        setBackgroundColor(Color.argb(120, 0, 0, 0));
        buildUi(ctx);
    }

    private void buildUi(Context ctx) {
        // Siri / visionOS style chromatic edge waves that illuminate the screen perimeter
        siriWaves = new SiriEdgeWavesView(ctx);
        addView(siriWaves, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Floating crystal capsule at bottom-center (modern Apple Intelligence pill)
        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = UiKit.px(38), padV = UiKit.px(24);
        card.setPadding(padH, padV, padH, padV);
        card.setBackground(Glass.panel(card, 32));
        card.setClipChildren(false);

        statusTitle = UiKit.bold(ctx, 22, UiKit.INK);
        statusTitle.setGravity(Gravity.CENTER);
        statusTitle.setPadding(0, 0, 0, 0);
        card.addView(statusTitle);

        resultText = UiKit.bold(ctx, 24, Color.parseColor("#38BDF8"));
        resultText.setGravity(Gravity.CENTER);
        resultText.setVisibility(GONE);
        resultText.setPadding(0, UiKit.px(8), 0, 0);
        card.addView(resultText);

        statusSub = UiKit.label(ctx, 15, UiKit.INK_DIM);
        statusSub.setGravity(Gravity.CENTER);
        statusSub.setPadding(0, UiKit.px(6), 0, 0);
        card.addView(statusSub);

        LayoutParams clp = new LayoutParams(UiKit.px(580), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = UiKit.px(60);
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
        statusSub.setText(tr ? "Bir film, dizi veya sesli komut söyleyin" : "Say a film, series or TV command");
        resultText.setVisibility(GONE);
        siriWaves.reset();

        setAlpha(0f);
        setVisibility(VISIBLE);
        int animDuration = PerformanceProfile.animationMs(getContext(), 350);
        animate().alpha(1f).setDuration(animDuration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        card.setTranslationY(UiKit.pxf(35));
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
                }

                @Override
                public void onBeginningOfSpeech() {
                    siriWaves.onSpeechStarted();
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    siriWaves.setVolume(rmsdB);
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
                            float norm = Math.max(0f, Math.min(1f, avg / 2800f));
                            siriWaves.setRawEnergy(norm);
                        }
                    }
                }

                @Override
                public void onEndOfSpeech() {
                    siriWaves.setListening(false);
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
                    cleanup(); // CRITICAL: Stop and destroy recognizer immediately so mic privacy indicator turns off
                    if (!active) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        final String said = matches.get(0);
                        statusTitle.setText("tr".equals(Locale.getDefault().getLanguage()) ? "Anlaşıldı" : "Heard");
                        statusTitle.setTextColor(Color.parseColor("#34D399"));
                        resultText.setText("“" + said + "”");
                        resultText.setVisibility(VISIBLE);
                        siriWaves.setSuccess(true);
                        main.postDelayed(() -> {
                            dismiss();
                            if (listener != null) listener.onResult(said);
                        }, 500);
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
        cleanup(); // Releases mic hardware immediately to shut off green dot
        card.animate().translationY(UiKit.pxf(25)).alpha(0f)
                .setDuration(PerformanceProfile.animationMs(getContext(), 180)).start();
        animate().alpha(0f).setDuration(PerformanceProfile.animationMs(getContext(), 220)).withEndAction(() -> {
            setVisibility(GONE);
            siriWaves.reset();
        }).start();
    }

    /**
     * Completely stops listening, cancels session and destroys SpeechRecognizer instance.
     * This immediately releases the AudioRecord native thread, turning off Android's green mic dot.
     */
    private void cleanup() {
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
     * Organic, visionOS / Siri style chromatic edge waves.
     * Radiates multi-spectral chromatic waves (Cyan, Hot Magenta, Violet, Radiant Amber, Electric Sky)
     * undulating along the display perimeter, dynamically pulsating with real-time microphone RMS volume.
     */
    private static final class SiriEdgeWavesView extends View {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wavePath = new Path();

        private float volume = 0f;
        private float targetVolume = 0f;
        private boolean listening = false;
        private boolean success = false;
        private float phase = 0f;
        private float speechPulse = 0f;

        // Apple Siri chromatic color palette
        private static final int[] CHROMATIC_COLORS = {
                0xFF00F0FF, // Electric Cyan
                0xFF3B82F6, // Apple Royal Blue
                0xFF9333EA, // Deep Electric Violet
                0xFFFF007F, // Hot Magenta
                0xFFFF9500, // Solar Amber
                0xFF00F0FF  // Loop back to Cyan
        };
        private static final float[] CHROMATIC_POSITIONS = {
                0.0f, 0.22f, 0.45f, 0.70f, 0.88f, 1.0f
        };

        SiriEdgeWavesView(Context ctx) {
            super(ctx);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);

            crestPaint.setStyle(Paint.Style.STROKE);
            crestPaint.setColor(0xE6FFFFFF);
            crestPaint.setStrokeWidth(UiKit.pxf(2.5f));
            crestPaint.setStrokeCap(Paint.Cap.ROUND);
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
            // Broad sensitive dynamic range: TV remotes report between -18dB and +10dB
            float norm = Math.max(0f, Math.min(1f, (rmsdB + 14f) / 22f));
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
            if (w <= 0 || h <= 0) return;

            // Smooth spring attack & decay for fluid, responsive volume undulation
            targetVolume *= 0.88f;
            if (speechPulse > 0f) {
                targetVolume = Math.max(targetVolume, speechPulse * 0.75f);
                speechPulse = Math.max(0f, speechPulse - 0.04f);
            }
            volume += (targetVolume - volume) * 0.35f;

            // Organic breathing baseline while listening so the screen perimeter is always living & undulating
            float breathe = listening ? (0.16f + (float) Math.sin(phase * 1.5f) * 0.09f) : 0f;
            float effectiveVol = Math.max(breathe, volume);

            phase += 0.040f + effectiveVol * 0.09f;

            // 1. Perimeter Diffuse Ambient Glow (volumetric light leaking into the dark room)
            int glowAlpha = success ? 140 : (listening ? (int) (65 + effectiveVol * 115) : 35);
            int primaryColor = success ? 0xFF10B981 : 0xFF00F0FF;
            int secondaryColor = success ? 0xFF34D399 : 0xFFFF007F;

            // Bottom edge ambient bloom
            float bloomH = UiKit.pxf(65) + effectiveVol * UiKit.pxf(60);
            glowPaint.setShader(new android.graphics.LinearGradient(0, h, 0, h - bloomH,
                    UiKit.withAlpha(primaryColor, glowAlpha), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, h - bloomH, w, h, glowPaint);

            // Top edge ambient bloom
            float topBloom = UiKit.pxf(40) + effectiveVol * UiKit.pxf(40);
            glowPaint.setShader(new android.graphics.LinearGradient(0, 0, 0, topBloom,
                    UiKit.withAlpha(secondaryColor, (int) (glowAlpha * 0.7f)), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, topBloom, glowPaint);

            // Left edge ambient bloom
            float sideBloom = UiKit.pxf(45) + effectiveVol * UiKit.pxf(40);
            glowPaint.setShader(new android.graphics.LinearGradient(0, 0, sideBloom, 0,
                    UiKit.withAlpha(0xFF9333EA, (int) (glowAlpha * 0.65f)), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, sideBloom, h, glowPaint);

            // Right edge ambient bloom
            glowPaint.setShader(new android.graphics.LinearGradient(w, 0, w - sideBloom, 0,
                    UiKit.withAlpha(0xFFFF9500, (int) (glowAlpha * 0.65f)), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(w - sideBloom, 0, w, h, glowPaint);

            // 2. Continuous Chromatic Perimeter Sweeping Wave
            strokePaint.setStrokeWidth(UiKit.pxf(5.5f) + effectiveVol * UiKit.pxf(5.5f));
            if (success) {
                strokePaint.setShader(null);
                strokePaint.setColor(0xFF34D399);
            } else {
                strokePaint.setShader(new android.graphics.SweepGradient(w / 2f, h / 2f, CHROMATIC_COLORS, CHROMATIC_POSITIONS));
            }

            // Draw animated fluid undulating border path
            float amp = UiKit.pxf(8f) + effectiveVol * UiKit.pxf(28f);
            float baseInset = UiKit.pxf(6f);

            wavePath.reset();
            // Start at top-left
            wavePath.moveTo(baseInset, baseInset);

            // Top edge
            int stepsX = 32;
            for (int i = 1; i <= stepsX; i++) {
                float x = (w * i) / (float) stepsX;
                float y = baseInset + (float) Math.sin(phase * 1.2f + i * 0.35f) * amp;
                wavePath.lineTo(x, y);
            }

            // Right edge
            int stepsY = 24;
            for (int i = 1; i <= stepsY; i++) {
                float y = (h * i) / (float) stepsY;
                float x = (w - baseInset) - (float) Math.sin(phase * 1.5f + i * 0.45f) * amp;
                wavePath.lineTo(x, y);
            }

            // Bottom edge (most dynamic and responsive, billows upward with voice)
            float botAmp = amp * 1.35f;
            for (int i = stepsX - 1; i >= 0; i--) {
                float x = (w * i) / (float) stepsX;
                float y = (h - baseInset) - (float) Math.sin(phase * 2.0f + (stepsX - i) * 0.42f) * botAmp;
                wavePath.lineTo(x, y);
            }

            // Left edge
            for (int i = stepsY - 1; i >= 0; i--) {
                float y = (h * i) / (float) stepsY;
                float x = baseInset + (float) Math.sin(phase * 1.4f + (stepsY - i) * 0.38f) * amp;
                wavePath.lineTo(x, y);
            }
            wavePath.close();

            // Draw glowing chromatic wave outline
            c.drawPath(wavePath, strokePaint);

            // Draw high-specular white inner crest line for vivid OLED luminance
            crestPaint.setAlpha(Math.min(255, (int) (120 + volume * 135)));
            c.drawPath(wavePath, crestPaint);

            // Secondary harmonic ethereal wave ribbon
            if (listening || volume > 0.05f) {
                Path ribbon = new Path();
                float rAmp = amp * 0.65f;
                ribbon.moveTo(baseInset, h - baseInset);
                for (int i = 1; i <= stepsX; i++) {
                    float x = (w * i) / (float) stepsX;
                    float y = (h - baseInset * 2f) - (float) Math.cos(phase * 2.4f + i * 0.55f) * rAmp;
                    ribbon.lineTo(x, y);
                }
                Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                ribbonPaint.setStyle(Paint.Style.STROKE);
                ribbonPaint.setStrokeWidth(UiKit.pxf(2.8f));
                ribbonPaint.setColor(UiKit.withAlpha(0xFF38BDF8, 160));
                c.drawPath(ribbon, ribbonPaint);
            }

            if (listening || volume > 0.01f || isAttachedToWindow()) {
                postInvalidateOnAnimation();
            }
        }
    }
}
