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

    /** Blocking: the current frame as a small JPEG, or null when it can't be captured. */
    public static byte[] captureJpeg(Context ctx) {
        return captureJpeg(ctx, false);
    }

    /** Blocking: captures frame at 768px for manual inspection or 640px for ambient background scan. */
    public static byte[] captureJpeg(Context ctx, boolean manualHighDetail) {
        int targetWidth = manualHighDetail ? 768 : 640;
        int jpegQuality = manualHighDetail ? 76 : 65;
        try {
            // Fast path on Android 11+ (API 30+): AccessibilityService.takeScreenshot takes ~100ms vs 4-7s for adb screencap
            Bitmap fastShot = serviceScreenshot(targetWidth);
            if (fastShot != null) {
                byte[] fastBytes = encodeFrame(fastShot, jpegQuality);
                if (fastBytes != null && fastBytes.length > 0) {
                    return fastBytes;
                }
            }
            byte[] raw = com.cinetv.launcher.remote.LocalAdb.exec(ctx, "screencap -p", 4500);
            if (raw == null || raw.length < 1000) {
                return fastShot != null ? new byte[0] : null;
            }
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
            o.inSampleSize = Math.max(1, o.outWidth / targetWidth);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeByteArray(raw, start, raw.length - start, o);
            if (b == null) return null;
            return encodeFrame(b, jpegQuality);
        } catch (Throwable t) {
            Log.w(TAG, "Capture failed: " + t);
            return null;
        }
    }

    /** The frame as a JPEG (empty when the picture is protected and captured black). Recycles {@code b}. */
    private static byte[] encodeFrame(Bitmap b, int quality) {
        try {
            // Protected video (Netflix and the like) captures as pure black: say so instead of guessing.
            if (isBlank(b)) {
                b.recycle();
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, quality, out);
            b.recycle();
            return out.toByteArray();
        } catch (Throwable t) {
            Log.w(TAG, "Capture failed: " + t);
            return null;
        }
    }

    /** Android 11+: the accessibility service's own screenshot, scaled to targetWidth. Blocking, null when unavailable. */
    @android.annotation.TargetApi(30)
    private static Bitmap serviceScreenshot(final int targetWidth) {
        if (android.os.Build.VERSION.SDK_INT < 30) return null;
        com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
        if (svc == null) return null;
        final Bitmap[] out = {null};
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        try {
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, svc.getMainExecutor(),
                    new android.accessibilityservice.AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(android.accessibilityservice.AccessibilityService.ScreenshotResult r) {
                            try (android.hardware.HardwareBuffer hb = r.getHardwareBuffer()) {
                                Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                                if (hw != null) {
                                    int w = Math.min(targetWidth, hw.getWidth());
                                    int h = Math.round(hw.getHeight() * (w / (float) hw.getWidth()));
                                    Bitmap soft = hw.copy(Bitmap.Config.ARGB_8888, false);
                                    hw.recycle();
                                    out[0] = soft == null ? null : Bitmap.createScaledBitmap(soft, w, h, true);
                                    if (soft != null && soft != out[0]) soft.recycle();
                                }
                            } catch (Throwable ignored) {
                            }
                            done.countDown();
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            done.countDown();
                        }
                    });
            done.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            return null;
        }
        return out[0];
    }

    private static boolean isBlank(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), bright = 0;
        for (int y = 1; y < 8; y++) for (int x = 1; x < 8; x++) {
            int c = b.getPixel(x * w / 8, y * h / 8);
            if (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF) > 45) bright++;
        }
        return bright < 2;
    }

    private static final java.util.LinkedList<String> RECENT_AMBIENT_TITLES = new java.util.LinkedList<>();
    private static final java.util.LinkedList<String> RECENT_MANUAL_TITLES = new java.util.LinkedList<>();

    public static synchronized void recordAmbientTitle(String title) {
        if (title == null) return;
        String clean = title.trim();
        if (clean.isEmpty()) return;
        RECENT_AMBIENT_TITLES.remove(clean);
        RECENT_AMBIENT_TITLES.addLast(clean);
        while (RECENT_AMBIENT_TITLES.size() > 8) {
            RECENT_AMBIENT_TITLES.removeFirst();
        }
    }

    public static synchronized void recordManualTitle(String title) {
        if (title == null) return;
        String clean = title.trim();
        if (clean.isEmpty()) return;
        RECENT_MANUAL_TITLES.remove(clean);
        RECENT_MANUAL_TITLES.addLast(clean);
        while (RECENT_MANUAL_TITLES.size() > 10) {
            RECENT_MANUAL_TITLES.removeFirst();
        }
    }

    public static synchronized String recentAmbientSummary() {
        if (RECENT_AMBIENT_TITLES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : RECENT_AMBIENT_TITLES) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    public static synchronized String recentManualSummary() {
        if (RECENT_MANUAL_TITLES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : RECENT_MANUAL_TITLES) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Blocking. {@code ambient} asks for the 1 or 2 most rewarding pieces of trivia across visual,
     * cultural, prop, actor, location, music, and subtitle categories for unprompted notes during playback;
     * otherwise up to five things to point at with deep cinephile & cultural analysis.
     */
    public static Scene inspect(Context ctx, boolean ambient) {
        return inspect(ctx, ambient, null);
    }

    /** With {@code frame} already captured (the unprompted notes look first, then ask about that same frame). */
    public static Scene inspect(Context ctx, boolean ambient, byte[] frame) {
        Scene scene = new Scene();
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        if (!LlmClient.isConfigured(ctx)) {
            scene.error = tr ? "CineSUB için Ayarlar'dan bir yapay zeka anahtarı ekleyin (Gemini ücretsiz)."
                    : "Add an AI key in Settings to use CineSUB (Gemini is free).";
            return scene;
        }
        byte[] jpeg = frame != null ? frame : captureJpeg(ctx, !ambient);
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
        // The last film started from CineTV is only a hint: the viewer may be watching anything since.
        String hint = NowPlaying.describe(ctx);
        String lang = tr ? "Turkish" : "English";
        String context = hint.isEmpty() ? "" : "The last film opened from the launcher was " + hint
                + ", but the viewer may be watching something else: trust only what the frame shows. ";
        String recent = ambient ? recentAmbientSummary() : recentManualSummary();
        String avoidRecent = recent.isEmpty() ? "" : "Already shown recently (prefer fresh, deeper details in this scene or subtitles rather than repeating these): " + recent + ". ";
        String sys = "You are CineSUB, an encyclopedic film historian, cinematographer, art curator, and cultural critic sitting beside the viewer. "
                + "You reveal what is truly fascinating, hidden, or meaningful in the frame and subtitles without ever spoiling future plot points. "
                + context + avoidRecent
                + "Only state facts you are confident about; if you can't identify a specific name, explain the historical/cinematic/visual significance of what is shown. "
                + "Never identify a real person from their face alone unless the film/series is recognisable or their name appears on screen; "
                + "when the film or series is recognisable, name both the character and the actor and share a sharp behind-the-scenes, directorial, or career insight. "
                + "Never return shallow descriptions ('a man standing', 'a dark room'): every item must make the viewer say 'I didn't know that!'. "
                + "Write in fluent, natural " + lang + ".";
        String ask = ambient
                ? "Actively scan this frame and its visible subtitles and return the 1 or 2 most fascinating details you can detect across ANY of these categories: "
                + "(1) visible subtitles: idioms, slang, cultural/historical references, real events/people, or quotes/allusions to films, books, myths or songs (set subtitle:true, kind text or event); "
                + "(2) recognisable characters & actors or cameos in a known film/series (kind person or cameo: character + actor name + behind-the-scenes or career fact); "
                + "(3) filming locations, cities, architectural landmarks or set design details (kind place or landmark); "
                + "(4) specific vehicles (exact car/motorcycle/aircraft model & era), watches, weapons, instruments, gadgets, books, records, iconic costumes or props (kind object); "
                + "(5) paintings, sculptures, posters or visual artworks on screen (kind art: title, artist, year); "
                + "(6) dishes, drinks or culinary details on screen (kind food); "
                + "(7) scene soundtrack/score composer or song when the scene is recognisable, or cinematography/director's visual motif & easter egg (kind music or event). "
                + "Aim to find at least 1 rewarding detail whenever the frame shows a real scene or dialogue; only return an empty list on blank/loading/logo screens. "
                + "JSON: {\"film\":\"the film or series if you recognise it, else empty\",\"items\":[{\"kind\":\"person|cameo|place|landmark|object|food|art|music|text|event\",\"label\":\"2-4 words\",\"title\":\"name\",\"fact\":\"1-2 rich sentences under 180 characters\","
                + "\"box\":[ymin,xmin,ymax,xmax],\"subtitle\":false}]} with box in 0-1000 screen coordinates enclosing that specific item (leave box empty for subtitle/music/general scene notes)."
                : "The viewer pressed the CineSUB button to inspect this exact moment. Deliver the 2 to 5 sharpest, smartest, most rewarding insights in this frame (high intellectual & cinephile value): "
                + "(1) SUBTITLES & DIALOGUE SUBTEXT: Read visible subtitles closely—decode cultural idioms, historical events, philosophical/literary/religious allusions, double meanings, or intertextual film quotes (subtitle:true, kind text or event, explain the exact reference and why the character invokes it); "
                + "(2) ACTORS, CHARACTERS & CAMEOS: When the work is recognisable, identify key characters/actors or cameos (kind person or cameo) with a non-obvious production secret, improvisation, method-acting detail, or career connection; "
                + "(3) DIRECTORIAL & CINEMATOGRAPHIC CRAFT: Notice visual symbolism, framing/blocking, lens choice, color palette motif, production design easter eggs, or homage to another filmmaker/painting (kind event or art); "
                + "(4) EXACT OBJECTS, PROPS & HOROLOGY/AUTOMOTIVE: Identify exact models and eras of cars, motorcycles, watches, firearms, cameras, musical instruments, books, vinyl records, or bespoke costumes and their symbolic role in the scene (kind object); "
                + "(5) ARCHITECTURE, FILMING LOCATIONS & ARTWORKS: Name real filming locations, historic buildings/architects (kind landmark or place), paintings/sculptures with artist & year (kind art), or regional dishes/drinks on screen (kind food); "
                + "(6) SCORE & SOUNDTRACK: When the scene's music or leitmotif is known, name the piece/composer/artist and how it shapes the tension (kind music, no box). "
                + "Never return an unnamed person ('Erkek karakter', 'A man'): every person must have a real character or actor name. "
                + "JSON: {\"film\":\"the film, series, or video title if recognisable, else empty\",\"items\":[{\"kind\":\"person|cameo|place|landmark|object|food|art|music|text|event\",\"label\":\"2-4 punchy words\",\"title\":\"specific name or concept\","
                + "\"fact\":\"2-3 dense, insightful sentences (up to 250 characters) packed with concrete trivia, history, or cinematic subtext\",\"box\":[ymin,xmin,ymax,xmax],\"subtitle\":true if from subtitles}]} "
                + "with box in 0-1000 screen coordinates tightly enclosing that specific item (leave box empty for subtitle, music, or whole-frame cinematography notes).";
        LlmClient.Result r = LlmClient.vision(ctx, sys, ask, jpeg, ambient ? 500 : 780, ambient);
        if (!r.success) {
            scene.error = r.text;
            return scene;
        }
        try {
            JSONObject root = LlmClient.extractJsonObject(r.text);
            scene.film = root.optString("film", "").trim();
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
                if (generic(p)) continue;
                JSONArray b = o.optJSONArray("box");
                if (b != null && b.length() == 4 && !p.fromSubtitle) {
                    float y0 = clamp(b.optDouble(0)), x0 = clamp(b.optDouble(1)), y1 = clamp(b.optDouble(2)), x1 = clamp(b.optDouble(3));
                    if (x1 - x0 > 0.01f && y1 - y0 > 0.01f) p.box.set(x0, y0, x1, y1);
                }
                scene.pois.add(p);
                if (ambient) {
                    recordAmbientTitle(p.title);
                } else {
                    recordManualTitle(p.title);
                }
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

    /** An anonymous person or a vague label teaches nothing ("Erkek Karakter", "A man standing"). */
    static boolean generic(Poi p) {
        String t = (p.title == null ? "" : p.title).toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) return true;
        String[] vague = {"karakter", "character", "erkek", "kadın", "kadin", "adam", "kız", "çocuk", "man", "woman", "person", "kişi", "biri", "someone", "unknown", "bilinmeyen"};
        if ("person".equals(p.kind) || t.split("\\s+").length <= 2) {
            for (String v : vague) {
                if (t.equals(v) || t.startsWith(v + " ") || t.endsWith(" " + v)) return "person".equals(p.kind) || t.split("\\s+").length == 1;
            }
        }
        return false;
    }
}
