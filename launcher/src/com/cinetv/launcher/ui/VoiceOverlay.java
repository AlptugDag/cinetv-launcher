package com.cinetv.launcher.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
 * Modern, visionOS-inspired in-app Voice Search Overlay.
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
    private WaveView waveView;
    private TextView statusTitle;
    private TextView statusSub;
    private TextView resultText;

    public VoiceOverlay(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        setFocusable(true);
        setClickable(true);
        setBackgroundColor(Color.argb(140, 0, 0, 0));
        buildUi(ctx);
    }

    private void buildUi(Context ctx) {
        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = UiKit.px(44), padV = UiKit.px(36);
        card.setPadding(padH, padV, padH, padV);
        card.setBackground(Glass.panel(card, 38));
        card.setClipChildren(false);

        // Acoustic sound wave pulsation view
        waveView = new WaveView(ctx);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(UiKit.px(100), UiKit.px(100));
        wlp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(waveView, wlp);

        statusTitle = UiKit.bold(ctx, 23, UiKit.INK);
        statusTitle.setGravity(Gravity.CENTER);
        statusTitle.setPadding(0, UiKit.px(16), 0, 0);
        card.addView(statusTitle);

        statusSub = UiKit.label(ctx, 16, UiKit.INK_DIM);
        statusSub.setGravity(Gravity.CENTER);
        statusSub.setPadding(0, UiKit.px(6), 0, UiKit.px(10));
        card.addView(statusSub);

        resultText = UiKit.bold(ctx, 20, Color.parseColor("#38BDF8"));
        resultText.setGravity(Gravity.CENTER);
        resultText.setVisibility(GONE);
        card.addView(resultText);

        TextView hint = UiKit.label(ctx, 13, Color.argb(130, 255, 255, 255));
        hint.setGravity(Gravity.CENTER);
        hint.setText("tr".equals(Locale.getDefault().getLanguage())
                ? "Kapatmak için kumandada Geri (Back) tuşuna basın"
                : "Press Back on remote to cancel");
        hint.setPadding(0, UiKit.px(14), 0, 0);
        card.addView(hint);

        LayoutParams clp = new LayoutParams(UiKit.px(540), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
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
        waveView.reset();

        setAlpha(0f);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(PerformanceProfile.animationMs(getContext(), 180)).start();
        requestFocus();

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    waveView.setListening(true);
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    waveView.setVolume(rmsdB);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    waveView.setListening(false);
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
                        waveView.setSuccess(true);
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
        animate().alpha(0f).setDuration(PerformanceProfile.animationMs(getContext(), 140)).withEndAction(() -> {
            setVisibility(GONE);
            waveView.reset();
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

    /** Real-time concentric sound wave rings pulsating with audio RMS level. */
    private static final class WaveView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float volume = 0f;
        private boolean listening = false;
        private boolean success = false;
        private float phase = 0f;

        WaveView(Context ctx) {
            super(ctx);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(UiKit.pxf(2.4f));
        }

        void setListening(boolean l) {
            this.listening = l;
            invalidate();
        }

        void setVolume(float rmsdB) {
            // rmsdB typically ranges from -2 to 10
            float norm = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 10f));
            this.volume = this.volume * 0.4f + norm * 0.6f;
            invalidate();
        }

        void setSuccess(boolean s) {
            this.success = s;
            invalidate();
        }

        void reset() {
            this.volume = 0f;
            this.listening = false;
            this.success = false;
            this.phase = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float baseR = w * 0.24f;

            int accent = success ? 0xFF34D399 : (listening ? 0xFF38BDF8 : 0xFFCBD5E1);

            // Pulsing concentric ripple rings
            if (listening) {
                phase += 0.08f;
                for (int i = 0; i < 3; i++) {
                    float ringR = baseR + UiKit.pxf(10) * (i + 1) + (volume * UiKit.pxf(18));
                    int alpha = Math.max(0, Math.min(180, (int) (140 - (i * 40) + volume * 50)));
                    ringPaint.setColor(UiKit.withAlpha(accent, alpha));
                    c.drawCircle(cx, cy, ringR, ringPaint);
                }
            }

            // Central glowing microphone orb
            orbPaint.setColor(accent);
            c.drawCircle(cx, cy, baseR, orbPaint);

            // Inner microphone symbol
            orbPaint.setColor(0xFF0F172A);
            float micW = baseR * 0.46f, micH = baseR * 0.85f;
            RectF body = new RectF(cx - micW / 2f, cy - micH / 2f, cx + micW / 2f, cy + micH * 0.2f);
            c.drawRoundRect(body, micW / 2f, micW / 2f, orbPaint);

            // U-bracket around microphone
            ringPaint.setColor(0xFF0F172A);
            ringPaint.setStrokeWidth(UiKit.pxf(2.2f));
            RectF arc = new RectF(cx - micW * 0.8f, cy - micH * 0.1f, cx + micW * 0.8f, cy + micH * 0.42f);
            c.drawArc(arc, 0, 180, false, ringPaint);
            c.drawLine(cx, cy + micH * 0.42f, cx, cy + micH * 0.65f, ringPaint);
            c.drawLine(cx - micW * 0.4f, cy + micH * 0.65f, cx + micW * 0.4f, cy + micH * 0.65f, ringPaint);
        }
    }
}
