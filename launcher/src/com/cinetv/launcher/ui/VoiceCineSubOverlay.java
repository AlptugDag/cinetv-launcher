package com.cinetv.launcher.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.ai.EmaLightningTts;
import com.cinetv.launcher.ai.SceneInspector;
import com.cinetv.launcher.device.DeviceTweaks;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.device.SystemAudio;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Voice CineSUB: Voice-triggered visual analysis overlay exclusively for TVs with a physical
 * microphone button on the remote while watching content in Nuvio.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Zero UI boxes, dialogs, cards, or pills ("kutucuk çıkmasın").</li>
 *   <li>The video frame freezes immediately ("görüntü donsun").</li>
 *   <li>Rich golden glow speech animations emerge from all screen borders ("ekranın kenarlarından golden glow").</li>
 *   <li>Spoken answer is delivered via TTS ("sesli bir yanıt verilsin").</li>
 *   <li>Playback unfreezes and resumes automatically when finished ("ardından film oynamaya devam etsin").</li>
 * </ul>
 */
public final class VoiceCineSubOverlay extends FrameLayout {

    private static final String TAG = "VoiceCineSub";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile VoiceCineSubOverlay active;

    private enum State {
        STARTING,
        LISTENING,
        THINKING,
        SPEAKING,
        DISMISSING
    }

    private final WindowManager wm;
    private final GoldenGlowEdgeView glowView;
    private SpeechRecognizer recognizer;
    private State state = State.STARTING;
    private byte[] capturedJpeg;
    private boolean dismissed = false;

    private final Runnable timeoutRunnable = () -> {
        Log.w(TAG, "Voice CineSUB timed out, dismissing");
        dismiss();
    };

    private VoiceCineSubOverlay(Context ctx) {
        super(ctx);
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setBackgroundColor(Color.TRANSPARENT);

        glowView = new GoldenGlowEdgeView(ctx);
        addView(glowView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private static Context host(Context ctx) {
        TvKeyInterceptorService acc = TvKeyInterceptorService.getInstance();
        return acc != null ? acc : ctx.getApplicationContext();
    }

    /** Triggers Voice CineSUB over the current screen. */
    public static void trigger(Context ctx) {
        if (TvKeyInterceptorService.isLiveTvOrTunerActive(ctx)) return;
        MAIN.post(() -> {
            if (TvKeyInterceptorService.isLiveTvOrTunerActive(ctx)) return;
            CineSubOverlay.dismissActive();
            if (active != null) {
                active.dismiss();
            }
            Context h = host(ctx);
            VoiceCineSubOverlay o = new VoiceCineSubOverlay(h);
            if (!o.attach()) return;
            active = o;
            o.startFlow();
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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.setTitle("VoiceCineSUB");

            setAlpha(0f);
            wm.addView(this, lp);
            requestFocus();
            int animDur = PerformanceProfile.animationMs(getContext(), 280);
            animate().alpha(1f).setDuration(animDur).start();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to attach VoiceCineSubOverlay: " + t);
            return false;
        }
    }

    private void startFlow() {
        // 1. Immediately freeze the video frame underneath
        DeviceTweaks.pauseMedia(getContext());
        SystemAudio.play(getContext(), SystemAudio.Sfx.RISER);

        // Safety timeout to avoid getting stuck if recognition never finishes
        MAIN.postDelayed(timeoutRunnable, 30_000L);

        // 2. Capture the paused frame immediately in background
        final Context app = getContext().getApplicationContext();
        new Thread(() -> {
            capturedJpeg = SceneInspector.captureJpeg(app, true);
        }, "voice-cinesub-capture").start();

        // 3. Start speech listening
        startListening();
    }

    private void startListening() {
        Context ctx = getContext();
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission missing");
            dismiss();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Log.w(TAG, "SpeechRecognizer not available");
            dismiss();
            return;
        }

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    state = State.LISTENING;
                    glowView.setState(State.LISTENING);
                }

                @Override
                public void onBeginningOfSpeech() {
                    state = State.LISTENING;
                    glowView.onBeginningOfSpeech();
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    glowView.setRms(rmsdB);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    state = State.THINKING;
                    glowView.setState(State.THINKING);
                }

                @Override
                public void onError(int error) {
                    Log.w(TAG, "Speech recognition error: " + error);
                    cleanupRecognizer();
                    MAIN.post(() -> dismiss());
                }

                @Override
                public void onResults(Bundle results) {
                    cleanupRecognizer();
                    if (dismissed) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches == null || matches.isEmpty()) {
                        dismiss();
                        return;
                    }

                    // Pick highest confidence or first recognized spoken query
                    float[] conf = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                    int best = 0;
                    for (int i = 1; conf != null && i < conf.length && i < matches.size(); i++) {
                        if (conf[i] > conf[best]) best = i;
                    }
                    final String query = matches.get(best).trim();
                    if (query.isEmpty()) {
                        dismiss();
                        return;
                    }

                    processQuery(query);
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag());
            recognizer.startListening(intent);
        } catch (Throwable t) {
            Log.e(TAG, "SpeechRecognizer start failure: " + t);
            cleanupRecognizer();
            dismiss();
        }
    }

    private void processQuery(final String query) {
        state = State.THINKING;
        glowView.setState(State.THINKING);

        final Context app = getContext().getApplicationContext();
        new Thread(() -> {
            // Wait briefly if frame capture is still writing
            int waited = 0;
            while (capturedJpeg == null && waited < 1500) {
                SystemClock.sleep(50);
                waited += 50;
            }

            final String answer = SceneInspector.answerVisualQuestion(app, query, capturedJpeg);

            MAIN.post(() -> {
                if (dismissed) return;
                state = State.SPEAKING;
                glowView.setState(State.SPEAKING);

                // Speak the answer aloud without displaying any card or box
                EmaLightningTts.speakAlways(app, answer, () -> {
                    // When speech completes, dismiss and automatically resume playback
                    dismiss();
                });
            });
        }, "voice-cinesub-query").start();
    }

    private void cleanupRecognizer() {
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

    public void dismiss() {
        MAIN.removeCallbacks(timeoutRunnable);
        if (dismissed) return;
        dismissed = true;
        if (active == this) active = null;

        state = State.DISMISSING;
        glowView.setState(State.DISMISSING);
        cleanupRecognizer();

        // Resume film playback
        DeviceTweaks.resumeMedia(getContext());

        int animDur = PerformanceProfile.animationMs(getContext(), 260);
        animate().alpha(0f).setDuration(animDur).withEndAction(() -> {
            try {
                wm.removeView(VoiceCineSubOverlay.this);
            } catch (Throwable ignored) {
            }
        }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
                    || code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_MEDIA_PLAY
                    || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                EmaLightningTts.stop();
                dismiss();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Atmospheric Golden Glow talk animation view around the TV display edges.
     * Renders four radiant golden edge gradients plus organic, voice-reactive golden wave ribbons
     * that undulate with user speech (LISTENING) and AI TTS speech cadence (SPEAKING) while the
     * film playback remains frozen underneath, with zero UI boxes or cards.
     */
    private static final class GoldenGlowEdgeView extends View {

        private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint leftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint waveFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path wavePath = new android.graphics.Path();
        private int cachedEdgeW = -1, cachedEdgeH = -1, cachedEdgeDepth = -1;

        private State currentState = State.STARTING;
        private float volume = 0f;
        private float targetVolume = 0f;
        private float phase = 0f;

        // Radiant golden spectrum
        private static final int GOLD_BRIGHT = Color.parseColor("#FFFFD700"); // Rich gold
        private static final int GOLD_AMBER = Color.parseColor("#FFF59E0B");  // Deep amber
        private static final int GOLD_HONEY = Color.parseColor("#FFFDE68A");  // Luminous champagne gold
        private static final int GOLD_TRANSPARENT = Color.argb(0, 245, 158, 11);

        public GoldenGlowEdgeView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            waveFillPaint.setStyle(Paint.Style.FILL);
        }

        public void setState(State state) {
            this.currentState = state;
            postInvalidateOnAnimation();
        }

        public void onBeginningOfSpeech() {
            targetVolume = 0.55f;
            postInvalidateOnAnimation();
        }

        public void setRms(float rmsdB) {
            // Map rmsdB (-2 to 10 typical) to 0.0 - 1.0
            float norm = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 10f));
            targetVolume = Math.max(targetVolume * 0.7f, norm);
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Natural talk cadence modulation while TTS is speaking the response
            if (currentState == State.SPEAKING) {
                float talkCadence = 0.35f + 0.52f * Math.abs((float) (Math.sin(phase * 2.8f) * Math.cos(phase * 1.35f + 0.7f)));
                targetVolume = talkCadence;
            } else if (currentState == State.THINKING) {
                targetVolume = 0.28f + 0.18f * (float) Math.sin(phase * 2.2f);
            } else if (currentState == State.LISTENING) {
                targetVolume *= 0.90f;
            } else {
                targetVolume = 0f;
            }

            // Smooth spring-like volume interpolation
            volume += (targetVolume - volume) * 0.25f;
            float speed = currentState == State.SPEAKING ? (0.065f + volume * 0.055f)
                    : currentState == State.THINKING ? 0.075f : (0.042f + volume * 0.05f);
            phase += speed;
            if (phase > (float) (Math.PI * 20.0)) {
                phase -= (float) (Math.PI * 20.0);
            }

            // Glow depth expands dynamically with speech energy
            float depthFactor = 0.14f + (volume * 0.11f);
            int edgeDepth = (int) (Math.min(w, h) * depthFactor);
            edgeDepth = Math.max(UiKit.px(88), Math.min(edgeDepth, UiKit.px(250)));

            // Modulation alpha based on state & speech volume
            float baseAlpha = 0.65f;
            if (currentState == State.LISTENING) {
                baseAlpha = 0.58f + (volume * 0.40f);
            } else if (currentState == State.THINKING) {
                baseAlpha = 0.64f + (float) Math.sin(phase * 2f) * 0.20f;
            } else if (currentState == State.SPEAKING) {
                baseAlpha = 0.68f + (volume * 0.30f);
            } else if (currentState == State.DISMISSING) {
                baseAlpha = 0.20f;
            }
            baseAlpha = Math.max(0.1f, Math.min(1f, baseAlpha));

            int alphaInt = (int) (255 * baseAlpha);
            if (w != cachedEdgeW || h != cachedEdgeH || edgeDepth != cachedEdgeDepth) {
                cachedEdgeW = w;
                cachedEdgeH = h;
                cachedEdgeDepth = edgeDepth;
                int baseEdge = Color.argb(255, Color.red(GOLD_BRIGHT), Color.green(GOLD_BRIGHT), Color.blue(GOLD_BRIGHT));
                int baseMid = Color.argb(148, Color.red(GOLD_AMBER), Color.green(GOLD_AMBER), Color.blue(GOLD_AMBER));
                int[] cols = new int[]{baseEdge, baseMid, GOLD_TRANSPARENT};
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

            // Draw four ambient golden edge auras
            canvas.drawRect(0, 0, w, edgeDepth, topPaint);
            canvas.drawRect(0, h - edgeDepth, w, h, bottomPaint);
            canvas.drawRect(0, 0, edgeDepth, h, leftPaint);
            canvas.drawRect(w - edgeDepth, 0, w, h, rightPaint);

            // Draw organic golden talk waves along the 4 edges of the TV display
            drawGoldenTalkWaves(canvas, w, h, edgeDepth, baseAlpha);

            // Shimmering golden rim around the perimeter
            drawPerimeterAccent(canvas, w, h, baseAlpha);

            // Keep 60fps animation active while overlay is showing
            if (currentState != State.DISMISSING) {
                postInvalidateOnAnimation();
            }
        }

        private void drawGoldenTalkWaves(Canvas canvas, int w, int h, int edgeDepth, float alpha) {
            float amp = UiKit.pxf(12f) + volume * UiKit.pxf(38f);
            float baseThickness = UiKit.pxf(10f) + volume * UiKit.pxf(16f);
            int waveAlpha = Math.min(235, (int) (135 * alpha + volume * 95));
            waveFillPaint.setColor(Color.argb(waveAlpha, Color.red(GOLD_HONEY), Color.green(GOLD_HONEY), Color.blue(GOLD_HONEY)));

            int steps = 28;
            // Top & Bottom horizontal talk waves
            wavePath.reset();
            wavePath.moveTo(0, 0);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float x = t * w;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 9.0 + phase * 2.4f) * 0.6f + Math.cos(t * 15.0 - phase * 3.1f) * 0.4f);
                float y = baseThickness + env * amp * (0.55f + 0.45f * wave);
                wavePath.lineTo(x, Math.max(2f, y));
            }
            wavePath.lineTo(w, 0);
            wavePath.close();
            canvas.drawPath(wavePath, waveFillPaint);

            wavePath.reset();
            wavePath.moveTo(0, h);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float x = t * w;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 9.0 - phase * 2.6f + 1.2f) * 0.6f + Math.cos(t * 14.0 + phase * 3.3f) * 0.4f);
                float y = h - (baseThickness + env * amp * (0.55f + 0.45f * wave));
                wavePath.lineTo(x, Math.min(h - 2f, y));
            }
            wavePath.lineTo(w, h);
            wavePath.close();
            canvas.drawPath(wavePath, waveFillPaint);

            // Left & Right vertical talk waves
            int vSteps = 20;
            wavePath.reset();
            wavePath.moveTo(0, 0);
            for (int i = 0; i <= vSteps; i++) {
                float t = i / (float) vSteps;
                float y = t * h;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 7.0 + phase * 2.2f + 0.5f) * 0.6f + Math.cos(t * 11.0 - phase * 2.9f) * 0.4f);
                float x = baseThickness * 0.85f + env * amp * 0.8f * (0.55f + 0.45f * wave);
                wavePath.lineTo(Math.max(2f, x), y);
            }
            wavePath.lineTo(0, h);
            wavePath.close();
            canvas.drawPath(wavePath, waveFillPaint);

            wavePath.reset();
            wavePath.moveTo(w, 0);
            for (int i = 0; i <= vSteps; i++) {
                float t = i / (float) vSteps;
                float y = t * h;
                float env = (float) Math.sin(t * Math.PI);
                float wave = (float) (Math.sin(t * 7.0 - phase * 2.5f + 2.1f) * 0.6f + Math.cos(t * 11.0 + phase * 2.7f) * 0.4f);
                float x = w - (baseThickness * 0.85f + env * amp * 0.8f * (0.55f + 0.45f * wave));
                wavePath.lineTo(Math.min(w - 2f, x), y);
            }
            wavePath.lineTo(w, h);
            wavePath.close();
            canvas.drawPath(wavePath, waveFillPaint);
        }

        private void drawPerimeterAccent(Canvas canvas, int w, int h, float alpha) {
            accentPaint.setStyle(Paint.Style.STROKE);
            accentPaint.setStrokeWidth(UiKit.pxf(6));
            int accentAlpha = Math.min(255, (int) ((185 + volume * 65) * alpha));
            accentPaint.setColor(Color.argb(accentAlpha, 255, 242, 168));
            canvas.drawRect(0, 0, w, h, accentPaint);
        }
    }
}
