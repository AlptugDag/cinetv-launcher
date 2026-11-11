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

    /** Blocking: captures frame at 540px for manual inspection or 448px for ambient background scan. */
    public static byte[] captureJpeg(Context ctx, boolean manualHighDetail) {
        int targetWidth = manualHighDetail ? 540 : 448;
        int jpegQuality = manualHighDetail ? 68 : 60;
        try {
            // Fast path on Android 11+ (API 30+): AccessibilityService.takeScreenshot takes ~80ms vs 3-5s for adb screencap -p
            Bitmap fastShot = serviceScreenshot(targetWidth);
            if (fastShot == null) {
                fastShot = captureActiveActivityDecor(targetWidth);
            }
            if (fastShot != null) {
                return encodeFrame(fastShot, jpegQuality);
            }
            // Never spawn heavy ADB screencap -p processes during unprompted ambient scans over Nuvio or on low-RAM TVs or when ADB is off
            if (!com.cinetv.launcher.remote.LocalAdb.isAvailable()
                    || (!manualHighDetail && (!com.cinetv.launcher.CinePlayerActivity.active
                    || com.cinetv.launcher.device.PerformanceProfile.lowMemory(ctx)))) {
                return null;
            }
            byte[] raw = com.cinetv.launcher.remote.LocalAdb.exec(ctx, "screencap -p", 2600);
            if (raw == null || raw.length < 1000) {
                return null;
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
    public static Bitmap serviceScreenshot(final int targetWidth) {
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

    /** Captures CineTV's own active window on Android 9/10 when takeScreenshot API 30 is unavailable. */
    public static Bitmap captureActiveActivityDecor(final int targetWidth) {
        final com.cinetv.launcher.MainActivity ma = com.cinetv.launcher.MainActivity.instance;
        if (ma == null || !com.cinetv.launcher.MainActivity.isInFront()) return null;
        final Bitmap[] out = {null};
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        ma.runOnUiThread(() -> {
            try {
                android.view.View decor = ma.getWindow() != null ? ma.getWindow().getDecorView() : null;
                if (decor != null && decor.getWidth() > 0 && decor.getHeight() > 0) {
                    int w = Math.min(targetWidth, decor.getWidth());
                    float scale = w / (float) decor.getWidth();
                    int h = Math.max(1, Math.round(decor.getHeight() * scale));
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(b);
                    c.scale(scale, scale);
                    decor.draw(c);
                    out[0] = b;
                }
            } catch (Throwable ignored) {
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(800, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}
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
            scene.error = tr ? "Ekran yakalanamadı. Ayarlar › Erişilebilirlik menüsünden CineTV servisinin açık olduğundan emin olun."
                    : "Couldn't capture the screen. Make sure the CineTV Accessibility service is enabled in Settings.";
            return scene;
        }
        if (jpeg.length == 0) {
            scene.error = tr ? "Bu uygulama görüntüsünü koruyor (DRM); CineSUB bu sahneyi göremiyor."
                    : "This app protects its picture (DRM); CineSUB can't see this scene.";
            return scene;
        }
        // The work started or playing is only a soft candidate: verify visually before trusting it.
        String hint = NowPlaying.describe(ctx);
        String lang = tr ? "Turkish" : "English";
        String context = hint.isEmpty() ? "" : "Unverified candidate hint from the player/launcher: " + hint
                + ". ONLY use this hint if the faces, costumes, setting, or subtitles in the frame genuinely match this work; "
                + "if the viewer switched to a different film, TV series (dizi), or video, IGNORE the hint completely and never force it onto the frame. ";
        String recent = ambient ? recentAmbientSummary() : recentManualSummary();
        String avoidRecent = recent.isEmpty() ? "" : "Already shown recently (prefer fresh, deeper details in this scene or subtitles rather than repeating these): " + recent + ". ";
        String sys = "You are CineSUB, an encyclopedic cinema & television historian (covering both films and TV series / diziler equally), cinematographer, art curator, and cultural critic sitting beside the viewer. "
                + "You reveal what is truly fascinating, hidden, or meaningful in the frame and subtitles without ever spoiling future plot points. "
                + context + avoidRecent
                + "CRITICAL ACCURACY RULE: Never guess or hallucinate the title of a film or TV series! Only set the \"film\" field (which holds either the film title OR the TV series / dizi title) if you are 100% certain of the exact film or TV series from unmistakable characters/actors, subtitles, or verified visual evidence; if you are not 100% certain which film or TV series this is, leave \"film\":\"\" empty and focus on the verifiable objects, locations, history, or subtitle references in the frame. "
                + "Never identify a real person from their face alone unless the film or TV series is unmistakably recognised or their name appears on screen; "
                + "when the film or TV series is recognised, name both the character and the actor and share a sharp behind-the-scenes, directorial, showrunner, or career insight. "
                + "CRITICAL SUBTITLE RULE: In subtitles (subtitle:true), NEVER return, repeat, or quote the literal subtitle words in 'title' or 'fact' (e.g. NEVER quote what was spoken or say 'Karakter ... diyor')! ONLY create a subtitle item if there is an implicit cultural, historical, philosophical, mythological, or intertextual reference, subtext, or double meaning that is not obvious from the words alone. If dialogue is straightforward conversation without implicit context or references, DO NOT return any subtitle item! 'title' must be the named concept/reference itself (e.g. 'Troya Savaşı Göndermesi'), NEVER the subtitle phrase. "
                + "Never return shallow descriptions ('a man standing', 'a dark room') or empty placeholders: every item must make the viewer say 'I didn't know that!'. "
                + "Write in fluent, natural " + lang + ".";
        String ask = ambient
                ? "Actively scan this frame and its visible subtitles (whether from a film or a TV series / dizi) and return the 1 or 2 most fascinating details you can detect across ANY of these categories: "
                + "(1) visible subtitles: ONLY if there is an implicit idiom, slang, cultural/historical/mythological reference, or literary allusion (set subtitle:true, kind text or event, NEVER quote or repeat the literal subtitle sentence, title must be the underlying concept); "
                + "(2) recognisable characters & actors or cameos in a known film or TV series / dizi (kind person or cameo: character + actor name + behind-the-scenes or career fact); "
                + "(3) filming locations, cities, architectural landmarks or set design details (kind place or landmark); "
                + "(4) specific vehicles (exact car/motorcycle/aircraft model & era), watches, weapons, instruments, gadgets, books, records, iconic costumes or props (kind object); "
                + "(5) paintings, sculptures, posters or visual artworks on screen (kind art: title, artist, year); "
                + "(6) dishes, drinks or culinary details on screen (kind food); "
                + "(7) scene soundtrack/score composer, theme music, or song when the scene is recognisable, or cinematography/director's/showrunner's visual motif & easter egg (kind music or event). "
                + "CRITICAL: If the frame shows ANY app menu, catalog/poster grid, movie/series detail page, episode list, stream source picker, settings overlay, static wallpaper, or a PAUSED video player (visible pause icon, timeline/seekbar, or player controls overlay), you MUST return {\"film\":\"\",\"items\":[]} immediately! Only return items when the frame is an active, clean, unpaused film/series/video scene. "
                + "JSON: {\"film\":\"exact film or TV series (dizi) title ONLY if 100% certain, else empty\",\"items\":[{\"kind\":\"person|cameo|place|landmark|object|food|art|music|text|event\",\"label\":\"2-4 words\",\"title\":\"name\",\"fact\":\"1-2 rich sentences under 180 characters\","
                + "\"box\":[ymin,xmin,ymax,xmax],\"subtitle\":false}]} with box in 0-1000 screen coordinates enclosing that specific item (leave box empty for subtitle/music/general scene notes)."
                : "The viewer pressed CineSUB to inspect this moment in a film or TV series (dizi). Return the 2 to 4 sharpest, highest-value insights in this frame: "
                + "(1) SUBTITLES: decode implicit idioms, historical/cultural/literary/mythological references, or subtext (subtitle:true, kind text or event; NEVER repeat the literal subtitle line, title must be the concept name); "
                + "(2) ACTORS & CHARACTERS: if the film/series is unmistakably recognised, name character + actor with a production secret or trivia (kind person or cameo); "
                + "(3) CRAFT, PROPS & LOCATIONS: exact models of vehicles/watches/props (kind object), real filming locations/landmarks (kind landmark or place), artworks (kind art), food (kind food), or composer/score (kind music). "
                + "Never return generic labels ('Erkek karakter', 'A man'). "
                + "JSON: {\"film\":\"exact title ONLY if 100% certain, else empty\",\"items\":[{\"kind\":\"person|cameo|place|landmark|object|food|art|music|text|event\",\"label\":\"2-4 words\",\"title\":\"specific name\","
                + "\"fact\":\"1-2 rich sentences (under 190 chars) with concrete trivia\",\"box\":[ymin,xmin,ymax,xmax],\"subtitle\":false}]} "
                + "with box in 0-1000 screen coordinates (empty for subtitle/music/general notes).";
        LlmClient.Result r = LlmClient.vision(ctx, sys, ask, jpeg, ambient ? 360 : 420, true);
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
                if (o == null) continue;
                String f = o.optString("fact").trim();
                String t = o.optString("title", o.optString("label")).trim();
                // Reject empty balloons or non-substantive entries
                if (f.isEmpty() || t.isEmpty() || f.length() < 12) continue;
                if (f.equalsIgnoreCase(t)) continue;
                String lowerT = t.toLowerCase(Locale.ROOT);
                if (lowerT.equals("yok") || lowerT.equals("none") || lowerT.equals("bilinmiyor")
                        || lowerT.equals("tanımsız") || lowerT.equals("altyazı") || lowerT.equals("subtitle")) continue;
                Poi p = new Poi();
                p.kind = o.optString("kind", "object");
                p.label = o.optString("label", t).trim();
                p.title = t;
                p.fact = f;
                p.fromSubtitle = o.optBoolean("subtitle");
                if (p.fromSubtitle) {
                    // Strip enclosing quotes from title if model wrapped them
                    if (p.title.startsWith("\"") && p.title.endsWith("\"") && p.title.length() > 2) {
                        p.title = p.title.substring(1, p.title.length() - 1).trim();
                    }
                    if (p.title.startsWith("“") && p.title.endsWith("”") && p.title.length() > 2) {
                        p.title = p.title.substring(1, p.title.length() - 1).trim();
                    }
                    String lowerF = p.fact.toLowerCase(Locale.ROOT);
                    // Discard if model merely regurgitated literal dialogue lines or lacked contextual depth
                    if (lowerF.startsWith("diyalogda ") || lowerF.startsWith("karakter şöyle diyor")
                            || lowerF.startsWith("altyazıda ") || p.fact.length() < 20) {
                        continue;
                    }
                }
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

    /**
     * Answers a viewer's spoken question about the frozen video frame in CineSUB voice mode.
     * Returned text is spoken directly to the viewer via TTS.
     */
    public static String answerVisualQuestion(Context ctx, String userQuestion, byte[] frame) {
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        if (!LlmClient.isConfigured(ctx)) {
            return tr ? "CineSUB sesli yanıt için Ayarlar'dan bir yapay zeka anahtarı ekleyin."
                    : "Add an AI key in Settings to use CineSUB voice answers.";
        }
        byte[] jpeg = frame != null && frame.length > 0 ? frame : captureJpeg(ctx, true);
        if (jpeg == null || jpeg.length == 0) {
            return answer(ctx, userQuestion);
        }
        String hint = NowPlaying.describe(ctx);
        String lang = tr ? "Turkish" : "English";
        String sys = "You are CineSUB, an expert cinema companion and visual analyst sitting right next to the viewer in CineTV. "
                + "The viewer paused a film or TV series and asked a spoken question looking directly at the current video frame on their TV. "
                + "Answer their question directly, accurately, and naturally in 1 to 3 spoken sentences (under 280 characters). "
                + "Do NOT use bullet points, markdown bolding, asterisks, emojis, quotes, or JSON—your response will be spoken aloud to the viewer via Text-to-Speech. "
                + "Be confident, concise, and encyclopedic. Accurately identify the exact actor, character, object, vehicle model, landmark, art piece, or scene subtext they asked about. "
                + (hint.isEmpty() ? "" : "Candidate title from launcher: " + hint + " (verify visually with the frame). ")
                + "Always answer in natural spoken " + lang + ".";
        String ask = "The viewer asks: \"" + userQuestion + "\". Look at this paused frame and provide a natural, concise spoken answer to their exact question.";
        LlmClient.Result r = LlmClient.vision(ctx, sys, ask, jpeg, 320, true);
        if (r.success && r.text != null && !r.text.trim().isEmpty()) {
            return r.text.trim();
        }
        return answer(ctx, userQuestion);
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v / 1000.0));
    }

    /** An anonymous person, empty balloon, or a vague label teaches nothing. */
    public static boolean generic(Poi p) {
        String t = (p.title == null ? "" : p.title).toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) return true;
        String f = (p.fact == null ? "" : p.fact).trim();
        if (f.isEmpty() || f.length() < 12 || f.equalsIgnoreCase(t)) return true;
        String[] vague = {"karakter", "character", "erkek", "kadın", "kadin", "adam", "kız", "çocuk", "man", "woman", "person", "kişi", "biri", "someone", "unknown", "bilinmeyen", "yok", "none", "tanımsız", "altyazı", "subtitle"};
        for (String v : vague) {
            if (t.equals(v)) return true;
        }
        if ("person".equals(p.kind) || t.split("\\s+").length <= 2) {
            for (String v : vague) {
                if (t.equals(v) || t.startsWith(v + " ") || t.endsWith(" " + v)) return "person".equals(p.kind) || t.split("\\s+").length == 1;
            }
        }
        return false;
    }
}
