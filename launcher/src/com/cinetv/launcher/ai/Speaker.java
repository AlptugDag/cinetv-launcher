package com.cinetv.launcher.ai;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Reads the assistant's answers aloud with the TV's speech engine, in the device language.
 * One shared engine, created on first use; {@link #say} reports when speaking ends so the answer
 * bubble can leave right after.
 */
public final class Speaker {

    public interface Done {
        void onDone();
    }

    private static TextToSpeech tts;
    private static boolean ready;
    private static String pending;
    private static Done pendingDone;
    private static Done current;

    private Speaker() {}

    public static synchronized void say(Context ctx, String text, Done done) {
        if (text == null || text.trim().isEmpty()) {
            if (done != null) done.onDone();
            return;
        }
        current = done;
        if (tts == null) {
            pending = text;
            pendingDone = done;
            tts = new TextToSpeech(ctx.getApplicationContext(), status -> {
                synchronized (Speaker.class) {
                    ready = status == TextToSpeech.SUCCESS;
                    if (!ready) {
                        finish(pendingDone);
                        return;
                    }
                    int lang = tts.setLanguage(Locale.getDefault());
                    if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.ENGLISH);
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) { finish(current); }
                        @Override public void onError(String id) { finish(current); }
                    });
                    if (pending != null) speak(pending);
                    pending = null;
                }
            });
            return;
        }
        if (!ready) {
            finish(done);
            return;
        }
        speak(text);
    }

    public static synchronized void stop() {
        if (tts != null && ready) tts.stop();
        current = null;
    }

    private static void speak(String text) {
        tts.setSpeechRate(1.02f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cinetv-answer");
    }

    private static void finish(Done d) {
        if (d != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(d::onDone);
    }
}
