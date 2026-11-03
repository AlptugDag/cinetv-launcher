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
        MAIN.post(() -> {
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
     * Atmospheric Golden Glow perimeter shader view.
     * Animates four radiant golden gradients around the screen edges with voice volume reactivity,
     * shimmering travelling light, and zero text or card artifacts.
     */
    private static final class GoldenGlowEdgeView extends View {

        private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint leftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF bounds = new RectF();
        private State currentState = State.STARTING;
        private float volume = 0f;
        private float targetVolume = 0f;
        private float phase = 0f;
        private int lastW = -1, lastH = -1;

        // Radiant golden spectrum
        private static final int GOLD_BRIGHT = Color.parseColor("#FFFFD700"); // Rich gold
        private static final int GOLD_AMBER = Color.parseColor("#FFF59E0B");  // Deep amber
        private static final int GOLD_HONEY = Color.parseColor("#FFFBBF24");  // Radiant honey
        private static final int GOLD_TRANSPARENT = Color.argb(0, 245, 158, 11);

        public GoldenGlowEdgeView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
        }

        public void setState(State state) {
            this.currentState = state;
            postInvalidateOnAnimation();
        }

        public void onBeginningOfSpeech() {
            targetVolume = 0.5f;
            postInvalidateOnAnimation();
        }

        public void setRms(float rmsdB) {
            // Map rmsdB (-2 to 10 typical) to 0.0 - 1.0
            float norm = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 10f));
            targetVolume = norm;
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Smooth volume interpolation
            volume += (targetVolume - volume) * 0.22f;
            phase += (currentState == State.THINKING ? 0.07f : 0.035f);
            if (phase > (float) (Math.PI * 2.0)) {
                phase -= (float) (Math.PI * 2.0);
            }

            // Glow depth expands dynamically with user speech
            float depthFactor = 0.16f + (volume * 0.10f);
            int edgeDepth = (int) (Math.min(w, h) * depthFactor);
            edgeDepth = Math.max(UiKit.px(90), Math.min(edgeDepth, UiKit.px(240)));

            // Modulation alpha based on state & speech volume
            float baseAlpha = 0.65f;
            if (currentState == State.LISTENING) {
                baseAlpha = 0.60f + (volume * 0.38f);
            } else if (currentState == State.THINKING) {
                baseAlpha = 0.65f + (float) Math.sin(phase * 2f) * 0.20f;
            } else if (currentState == State.SPEAKING) {
                baseAlpha = 0.70f + (float) Math.sin(phase * 1.5f) * 0.18f;
            } else if (currentState == State.DISMISSING) {
                baseAlpha = 0.25f;
            }
            baseAlpha = Math.max(0.1f, Math.min(1f, baseAlpha));

            int alphaInt = (int) (255 * baseAlpha);
            int cEdge = Color.argb(alphaInt, Color.red(GOLD_BRIGHT), Color.green(GOLD_BRIGHT), Color.blue(GOLD_BRIGHT));
            int cMid = Color.argb((int) (alphaInt * 0.60f), Color.red(GOLD_AMBER), Color.green(GOLD_AMBER), Color.blue(GOLD_AMBER));

            // Rebuild edge linear gradient shaders
            topPaint.setShader(new LinearGradient(0, 0, 0, edgeDepth,
                    new int[]{cEdge, cMid, GOLD_TRANSPARENT}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            bottomPaint.setShader(new LinearGradient(0, h, 0, h - edgeDepth,
                    new int[]{cEdge, cMid, GOLD_TRANSPARENT}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            leftPaint.setShader(new LinearGradient(0, 0, edgeDepth, 0,
                    new int[]{cEdge, cMid, GOLD_TRANSPARENT}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            rightPaint.setShader(new LinearGradient(w, 0, w - edgeDepth, 0,
                    new int[]{cEdge, cMid, GOLD_TRANSPARENT}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));

            // Draw four edge auras
            canvas.drawRect(0, 0, w, edgeDepth, topPaint);
            canvas.drawRect(0, h - edgeDepth, w, h, bottomPaint);
            canvas.drawRect(0, 0, edgeDepth, h, leftPaint);
            canvas.drawRect(w - edgeDepth, 0, w, h, rightPaint);

            // Shimmering accent pulse traveling along the perimeter
            drawPerimeterAccent(canvas, w, h, phase, baseAlpha);

            // Keep 60fps animation active while overlay is showing
            if (currentState != State.DISMISSING) {
                postInvalidateOnAnimation();
            }
        }

        private void drawPerimeterAccent(Canvas canvas, int w, int h, float phase, float alpha) {
            float perimeter = 2f * (w + h);
            float offset = ((phase / (float) (Math.PI * 2.0)) * perimeter) % perimeter;

            accentPaint.setStyle(Paint.Style.STROKE);
            accentPaint.setStrokeWidth(UiKit.pxf(6));
            int accentAlpha = (int) (190 * alpha);
            accentPaint.setColor(Color.argb(accentAlpha, 255, 240, 160));

            // Subtle outer edge stroke with golden glow
            canvas.drawRect(0, 0, w, h, accentPaint);
        }
    }
}
