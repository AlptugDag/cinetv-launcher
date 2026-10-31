package com.cinetv.launcher.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.util.Log;

import com.cinetv.launcher.service.NowPlaying;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CineSUB's eyes: grabs the frame on screen (whatever app is playing), shrinks it, and asks a
 * vision model what is worth knowing in it: people, places, objects, food, art, and the names,
 * places and events the subtitles mention. Every answer comes with where it is on screen, so the
 * overlay can point at it.
 */
public final class SceneInspector {

    private static final String TAG = "CineSub";

    /** One thing in the frame. {@code box} is in 0..1 screen fractions; empty for subtitle mentions. */
    public static final class Poi {
        public String kind = "object", label = "", title = "", fact = "";
        public final RectF box = new RectF();
        public boolean fromSubtitle;
    }

    public static final class Scene {
        public final List<Poi> pois = new ArrayList<>();
        public String error = "";
        public String film = "";
    }

    private SceneInspector() {}

    /** Blocking: the current frame as a small JPEG (about 960 px wide), or null when it can't be captured. */
    public static byte[] captureJpeg(Context ctx) {
        try {
            byte[] raw = com.cinetv.launcher.remote.LocalAdb.exec(ctx, "screencap -p", 8000);
            if (raw == null || raw.length < 1000) return null;
            // Some MediaTek firmwares print a line before the PNG: start at its signature.
            int start = 0;
            for (int i = 0; i < Math.min(raw.length - 4, 4096); i++) {
                if ((raw[i] & 0xFF) == 0x89 && raw[i + 1] == 'P' && raw[i + 2] == 'N' && raw[i + 3] == 'G') {
                    start = i;
                    break;
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, start, raw.length - start, o);
            o.inSampleSize = Math.max(1, o.outWidth / 960);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeByteArray(raw, start, raw.length - start, o);
            if (b == null) return null;
            // Protected video (Netflix and the like) captures as pure black: say so instead of guessing.
            if (isBlank(b)) {
                b.recycle();
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 74, out);
            b.recycle();
            return out.toByteArray();
        } catch (Throwable t) {
            Log.w(TAG, "Capture failed: " + t);
            return null;
        }
    }

    private static boolean isBlank(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), bright = 0;
        for (int y = 1; y < 8; y++) for (int x = 1; x < 8; x++) {
            int c = b.getPixel(x * w / 8, y * h / 8);
            if (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF) > 45) bright++;
        }
        return bright < 2;
    }

    /**
     * Blocking. {@code ambient} asks only for the single best piece of trivia (or nothing), for the
     * unprompted notes during playback; otherwise up to five things to point at.
     */
    public static Scene inspect(Context ctx, boolean ambient) {
        Scene scene = new Scene();
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        if (!LlmClient.isConfigured(ctx)) {
            scene.error = tr ? "CineSUB için Ayarlar'dan bir yapay zeka anahtarı ekleyin (Gemini ücretsiz)."
                    : "Add an AI key in Settings to use CineSUB (Gemini is free).";
            return scene;
        }
        byte[] jpeg = captureJpeg(ctx);
        if (jpeg == null) {
            scene.error = tr ? "Ekran yakalanamadı. Kurulumdaki 'Ağ hata ayıklaması' izni kapalı olabilir."
                    : "Couldn't capture the screen. Network debugging may be off.";
            return scene;
        }
        if (jpeg.length == 0) {
            scene.error = tr ? "Bu uygulama görüntüsünü koruyor (DRM); CineSUB bu sahneyi göremiyor."
                    : "This app protects its picture (DRM); CineSUB can't see this scene.";
            return scene;
        }
        scene.film = NowPlaying.describe(ctx);
        String lang = tr ? "Turkish" : "English";
        String context = scene.film.isEmpty() ? "" : "The viewer is probably watching " + scene.film + ". ";
        String sys = "You are CineSUB, a quiet companion that tells a film viewer what is worth knowing about what they see, "
                + "without spoiling anything that has not happened yet. " + context
                + "Only state facts you are confident about; if you can't identify something, describe what it is instead of naming it. "
                + "Never identify a real person from their face alone: name actors only when the film is known or the name appears on screen, "
                + "and then name the character and the actor. If the frame is clearly from a famous film or series, you may recognise the film itself "
                + "and use that knowledge. Never return generic descriptions ('a man standing', 'a dark room'): every item must teach something "
                + "specific and interesting. Fewer good items beat many weak ones; an empty list is fine. Write in " + lang + ".";
        String ask = ambient
                ? "If, and only if, something in this frame or its subtitles deserves a short, surprising piece of trivia (a real place, a painting, "
                + "a dish, a historical event, a person, a book or a song mentioned in the dialogue), return it; otherwise return an empty list. "
                + "JSON: {\"items\":[{\"kind\":\"person|place|object|food|art|text|event\",\"label\":\"2-4 words\",\"title\":\"name\",\"fact\":\"under 150 characters\","
                + "\"box\":[ymin,xmin,ymax,xmax],\"subtitle\":false}]} with box in 0-1000 screen coordinates (empty for subtitle mentions)."
                : "List up to 5 things in this frame worth knowing about: identifiable places and landmarks, artworks, notable objects, vehicles, clothing, "
                + "food and drink, characters, and any name, place, event, book or song mentioned in the visible subtitles. "
                + "JSON: {\"film\":\"the film or series if you recognise it, else empty\",\"items\":[{\"kind\":\"person|place|object|food|art|text|event\",\"label\":\"2-4 words\",\"title\":\"what or who it is\","
                + "\"fact\":\"one or two sentences, under 220 characters\",\"box\":[ymin,xmin,ymax,xmax],\"subtitle\":true if it came from the subtitles}]} "
                + "with box in 0-1000 screen coordinates, tight around the thing.";
        LlmClient.Result r = LlmClient.vision(ctx, sys, ask, jpeg, ambient ? 400 : 1200);
        if (!r.success) {
            scene.error = r.text;
            return scene;
        }
        try {
            JSONObject root = LlmClient.extractJsonObject(r.text);
            if (scene.film.isEmpty()) scene.film = root.optString("film", "").trim();
            JSONArray items = root.optJSONArray("items");
            for (int i = 0; items != null && i < items.length() && scene.pois.size() < 5; i++) {
                JSONObject o = items.optJSONObject(i);
                if (o == null || o.optString("fact").trim().isEmpty()) continue;
                Poi p = new Poi();
                p.kind = o.optString("kind", "object");
                p.label = o.optString("label");
                p.title = o.optString("title", p.label);
                p.fact = o.optString("fact").trim();
                p.fromSubtitle = o.optBoolean("subtitle");
                JSONArray b = o.optJSONArray("box");
                if (b != null && b.length() == 4 && !p.fromSubtitle) {
                    float y0 = clamp(b.optDouble(0)), x0 = clamp(b.optDouble(1)), y1 = clamp(b.optDouble(2)), x1 = clamp(b.optDouble(3));
                    if (x1 - x0 > 0.01f && y1 - y0 > 0.01f) p.box.set(x0, y0, x1, y1);
                }
                scene.pois.add(p);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unreadable vision reply");
            scene.error = tr ? "Gamze bu sahneyi çözemedi, birazdan tekrar deneyin." : "Couldn't read this scene; try again in a moment.";
        }
        return scene;
    }

    /** Blocking: a longer, spoiler-free explanation of one thing, for "tell me more". */
    public static String more(Context ctx, Poi p, String film) {
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LlmClient.Result r = LlmClient.generate(ctx,
                "You are CineSUB. Explain to a film viewer, in " + (tr ? "Turkish" : "English") + ", in 3-4 sentences (under 480 characters), "
                        + "the background of what they asked about: history, meaning, why it matters in the film. No spoilers. Only confident facts. Plain text.",
                p.title + " (" + p.kind + ")" + (film.isEmpty() ? "" : ", seen in " + film) + ". Known so far: " + p.fact, 500, true);
        return r.success ? r.text.trim() : "";
    }

    /** A free-text question about films (from the phone's CineSUB search). Blocking. */
    public static String answer(Context ctx, String question) {
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        String film = NowPlaying.describe(ctx);
        LlmClient.Result r = LlmClient.generate(ctx,
                "You are CineSUB, a film companion. Answer in " + (tr ? "Turkish" : "English") + " in 2-4 sentences, no spoilers, only confident facts. Plain text."
                        + (film.isEmpty() ? "" : " The viewer is watching " + film + "."), question, 500, true);
        return r.success ? r.text.trim() : r.text;
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v / 1000.0));
    }
}
