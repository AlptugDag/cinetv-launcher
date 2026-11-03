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

    /** Blocking: the current frame as a small JPEG (about 768 px wide), or null when it can't be captured. */
    public static byte[] captureJpeg(Context ctx) {
        try {
            // Fast path on Android 11+ (API 30+): AccessibilityService.takeScreenshot takes ~100ms vs 4-7s for adb screencap
            Bitmap fastShot = serviceScreenshot();
            if (fastShot != null) {
                byte[] fastBytes = encodeFrame(fastShot);
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
            o.inSampleSize = Math.max(1, o.outWidth / 640);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeByteArray(raw, start, raw.length - start, o);
            if (b == null) return null;
            return encodeFrame(b);
        } catch (Throwable t) {
            Log.w(TAG, "Capture failed: " + t);
            return null;
        }
    }

    /** The frame as a JPEG (empty when the picture is protected and captured black). Recycles {@code b}. */
    private static byte[] encodeFrame(Bitmap b) {
        try {
            // Protected video (Netflix and the like) captures as pure black: say so instead of guessing.
            if (isBlank(b)) {
                b.recycle();
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 65, out);
            b.recycle();
            return out.toByteArray();
        } catch (Throwable t) {
            Log.w(TAG, "Capture failed: " + t);
            return null;
        }
    }

    /** Android 11+: the accessibility service's own screenshot, scaled to about 640 px. Blocking, null when unavailable. */
    @android.annotation.TargetApi(30)
    private static Bitmap serviceScreenshot() {
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
                                    int w = Math.min(640, hw.getWidth());
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

    public static synchronized String recentAmbientSummary() {
        if (RECENT_AMBIENT_TITLES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : RECENT_AMBIENT_TITLES) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Blocking. {@code ambient} asks for the 1 or 2 most rewarding pieces of trivia across visual,
     * cultural, prop, actor, location, music, and subtitle categories for unprompted notes during playback;
     * otherwise up to five things to point at.
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
        byte[] jpeg = frame != null ? frame : captureJpeg(ctx);
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
        // The last film started from CineTV is only a hint: the viewer may be watching anything since. The title
        // shown on screen is the one the model recognises in this frame (it used to stay "Oldboy" whatever played).
        String hint = NowPlaying.describe(ctx);
        String lang = tr ? "Turkish" : "English";
        String context = hint.isEmpty() ? "" : "The last film opened from the launcher was " + hint
                + ", but the viewer may be watching something else: trust only what the frame shows. ";
        String recent = ambient ? recentAmbientSummary() : "";
        String avoidRecent = recent.isEmpty() ? "" : "Already shown recently (do NOT repeat these items; pick different details in the scene or subtitles): " + recent + ". ";
        String sys = "You are CineSUB, a cinephile's trivia companion that tells a film viewer what is worth knowing about what they see, "
                + "without spoiling anything that has not happened yet. " + context + avoidRecent
                + "Only state facts you are confident about; if you can't identify something, describe what it is instead of naming it. "
                + "Never identify a real person from their face alone unless the film/series is recognisable or their name appears on screen; "
                + "when the film or series is recognisable, name both the character and the actor and share an insightful production or career fact. "
                + "Never return generic descriptions ('a man standing', 'a dark room'): every item must teach something "
                + "specific and interesting. Write in " + lang + ".";
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
                : "List the 1 to 4 most rewarding things in this frame (quality over quantity: a weak or obvious item is worse than none): "
                + "cameos (a director, writer, musician or other real celebrity appearing briefly in a known film: kind cameo, say who and why it is there), "
                + "historic buildings and landmarks (kind landmark: name, city, era, architect when known) and filming locations (kind place), "
                + "the music playing in this scene when the film is known and the scene is recognisable, or a song named or shown on screen "
                + "(kind music: song, artist, year and why it was chosen; no box), artworks, notable objects, vehicles, clothing, "
                + "food and drink, characters, and any name, place, event, book or song mentioned in the visible subtitles. "
                + "Read the visible subtitles closely: explain any specific concept, term, slang, idiom or historical reference in them, "
                + "and when a line quotes or alludes to something (another film, a book, the Bible, a myth, a famous speech, a song, a real event), "
                + "say what it refers to and why the character says it, e.g. 'He is quoting Apocalypse Now (1979)'. These come with subtitle:true and kind text or event; "
                + "a strong subtitle reference beats a weak object in the frame. "
                + "Think like a film buff and an art historian: an artwork gets its title, artist and year (e.g. 'Napoleon Crossing the Alps, Jacques-Louis David, 1801'); "
                + "a landmark its name and city; a car, weapon, watch or record its exact model and era; a prop or costume the story behind it in this film. "
                + "Food comes first when it is on screen: name the dish itself (e.g. 'Ratatouille', 'Spaghetti alla puttanesca') and say where it comes from or why it matters. "
                + "Never return an unnamed person ('a man', 'male character'): a person needs a character or actor name, otherwise leave them out. "
                + "JSON: {\"film\":\"the film or series if you recognise it, else empty\",\"items\":[{\"kind\":\"person|cameo|place|landmark|object|food|art|music|text|event\",\"label\":\"2-4 words\",\"title\":\"what or who it is\","
                + "\"fact\":\"one or two sentences, under 220 characters\",\"box\":[ymin,xmin,ymax,xmax],\"subtitle\":true if it came from the subtitles}]} "
                + "with box in 0-1000 screen coordinates. The box must enclose exactly that one thing and nothing else: the painting's frame, "
                + "the plate, the building's facade, the car's body, the cameo's face and shoulders; never a whole region, never a box around "
                + "several things. If you cannot place it precisely, leave box empty rather than guess.";
        LlmClient.Result r = LlmClient.vision(ctx, sys, ask, jpeg, ambient ? 500 : 650, ambient);
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
