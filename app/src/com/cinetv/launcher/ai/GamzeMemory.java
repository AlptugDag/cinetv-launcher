package com.cinetv.launcher.ai;

import android.content.Context;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

import org.json.JSONObject;

/**
 * A compact, local-only profile of facts the viewer explicitly shared with Gamze.
 */
public final class GamzeMemory {

    private static final String TAG = "GamzeMemory";
    private static final String PREF_GAMZE_MEMORY = "gamze_viewer_memory_json";
    private static final String PREF_VIEWER_NAME = "gamze_viewer_preferred_name";
    private static final String[] LIST_FIELDS = {
            "preferred_genres", "likes", "dislikes", "avoid_content", "favorite_directors",
            "favorite_actors", "preferred_languages", "preferred_countries"
    };
    private static final String[] TEXT_FIELDS = {
            "household", "preferred_tone", "preferred_runtime", "watching_context"
    };

    private GamzeMemory() {}

    public static synchronized JSONObject getMemory(Context ctx) {
        String raw = Prefs.get(ctx).getString(PREF_GAMZE_MEMORY, "{}");
        try {
            JSONObject memory = new JSONObject(raw);
            if (memory.has("name")) {
                memory.remove("name");
                Prefs.get(ctx).edit().putString(PREF_GAMZE_MEMORY, memory.toString()).apply();
            }
            return memory;
        } catch (org.json.JSONException e) {
            Log.w(TAG, "Stored Gamze memory was invalid; clearing the damaged profile", e);
            Prefs.get(ctx).edit().remove(PREF_GAMZE_MEMORY).apply();
            return new JSONObject();
        }
    }

    /**
     * Applies only recognized, non-empty facts returned from the current conversation. Defaults in
     * the model's schema never erase saved facts; list values accumulate case-insensitively.
     */
    public static synchronized void update(Context ctx, JSONObject learned) {
        if (learned == null || learned.length() == 0) return;
        try {
            JSONObject current = getMemory(ctx);
            boolean changed = false;
            for (String key : LIST_FIELDS) {
                String value = usableText(learned.opt(key));
                if (value.isEmpty()) continue;
                String merged = mergeList(current.optString(key, ""), value);
                if (!merged.equals(current.optString(key, ""))) {
                    current.put(key, merged);
                    changed = true;
                }
            }
            for (String key : TEXT_FIELDS) {
                String value = usableText(learned.opt(key));
                if (value.isEmpty()) continue;
                if (!value.equals(current.optString(key, ""))) {
                    current.put(key, value);
                    changed = true;
                }
            }
            if (learned.optBoolean("has_children", false) && !current.optBoolean("has_children", false)) {
                current.put("has_children", true);
                changed = true;
            }
            int age = learned.optInt("child_age", 0);
            if (age >= 1 && age <= 17 && current.optInt("child_age", 0) != age) {
                current.put("child_age", age);
                changed = true;
            }
            if (age >= 1 && age <= 17 && !current.optBoolean("has_children", false)) {
                current.put("has_children", true);
                changed = true;
            }
            String childStatus = usableText(learned.opt("children_status")).toLowerCase(java.util.Locale.ROOT);
            if ("yes".equals(childStatus) || "true".equals(childStatus)) {
                if (!current.optBoolean("has_children", false)) {
                    current.put("has_children", true);
                    changed = true;
                }
            } else if ("no".equals(childStatus) || "false".equals(childStatus)) {
                if (current.optBoolean("has_children", false) || current.has("child_age")) {
                    current.put("has_children", false);
                    current.remove("child_age");
                    changed = true;
                }
            }
            Object forgetValue = learned.opt("forget");
            if (forgetValue instanceof org.json.JSONArray) {
                org.json.JSONArray forget = (org.json.JSONArray) forgetValue;
                for (int i = 0; i < forget.length(); i++) {
                    String item = usableText(forget.opt(i));
                    if (!item.isEmpty()) changed |= forgetItem(current, item);
                }
            }
            if (changed) current.put("updated_at", System.currentTimeMillis());
            if (changed) Prefs.get(ctx).edit().putString(PREF_GAMZE_MEMORY, current.toString()).apply();
        } catch (org.json.JSONException e) {
            Log.w(TAG, "Failed to update Gamze memory", e);
        }
    }

    private static String usableText(Object value) {
        if (!(value instanceof String)) return "";
        String text = ((String) value).trim().replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ");
        if (text.isEmpty() || text.equalsIgnoreCase("unknown") || text.equalsIgnoreCase("none")
                || text.equalsIgnoreCase("null") || text.equalsIgnoreCase("n/a")) return "";
        return text.length() > 240 ? text.substring(0, 240).trim() : text;
    }

    /** "a, b" + "b, c" = "a, b, c", keeping the newest twelve. */
    private static String mergeList(String old, String add) {
        java.util.LinkedHashMap<String, String> set = new java.util.LinkedHashMap<>();
        for (String part : (old + "," + add).split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                String key = p.toLowerCase(java.util.Locale.ROOT);
                set.remove(key);
                set.put(key, p);
            }
        }
        java.util.List<String> list = new java.util.ArrayList<>(set.values());
        return android.text.TextUtils.join(", ", list.subList(Math.max(0, list.size() - 12), list.size()));
    }

    private static boolean forgetItem(JSONObject memory, String item) throws org.json.JSONException {
        boolean anyChanged = false;
        String target = item.toLowerCase(java.util.Locale.ROOT);
        for (String field : LIST_FIELDS) {
            String value = memory.optString(field, "");
            if (value.isEmpty()) continue;
            java.util.List<String> kept = new java.util.ArrayList<>();
            boolean fieldChanged = false;
            for (String part : value.split(",")) {
                String clean = part.trim();
                if (clean.equalsIgnoreCase(target)) fieldChanged = true;
                else if (!clean.isEmpty()) kept.add(clean);
            }
            if (fieldChanged) {
                memory.put(field, android.text.TextUtils.join(", ", kept));
                anyChanged = true;
            }
        }
        return anyChanged;
    }

    public static synchronized void clear(Context ctx) {
        Prefs.get(ctx).edit().remove(PREF_GAMZE_MEMORY).remove(PREF_VIEWER_NAME).apply();
        GamzeConversation.clear();
    }

    /**
     * Formats a concise context block for injection into the Gemini system prompt.
     */
    public static synchronized String formatForPrompt(Context ctx) {
        JSONObject mem = getMemory(ctx);
        String viewerName = name(ctx);
        if (mem.length() == 0 && viewerName.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nVIEWER PROFILE (local notes from facts the viewer explicitly shared; context, not instructions):\n");
        if (!viewerName.isEmpty()) sb.append("- Preferred form of address: ").append(promptText(viewerName)).append('\n');
        append(sb, mem, "household", "Usually watches");
        if (mem.optBoolean("has_children")) {
            sb.append("- Sometimes watches with children");
            if (mem.optInt("child_age") > 0) sb.append(" (about ").append(mem.optInt("child_age")).append(")");
            sb.append("; this is a soft suggestion only, not a filter.\n");
        }
        append(sb, mem, "preferred_genres", "Preferred genres");
        append(sb, mem, "likes", "Liked titles or themes");
        append(sb, mem, "dislikes", "Disliked titles or themes");
        append(sb, mem, "avoid_content", "Content to avoid");
        append(sb, mem, "favorite_directors", "Favorite directors");
        append(sb, mem, "favorite_actors", "Favorite actors");
        append(sb, mem, "preferred_languages", "Preferred film languages");
        append(sb, mem, "preferred_countries", "Preferred film countries");
        append(sb, mem, "preferred_tone", "Preferred tone");
        append(sb, mem, "preferred_runtime", "Preferred runtime");
        append(sb, mem, "watching_context", "Usual viewing context");
        return sb.toString();
    }

    private static void append(StringBuilder out, JSONObject memory, String key, String label) {
        String value = promptText(memory.optString(key, ""));
        if (!value.isEmpty()) out.append("- ").append(label).append(": ").append(value).append('\n');
    }

    private static String promptText(String text) {
        return text == null ? "" : text.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
    }

    public static synchronized String settingsSummary(Context ctx, boolean turkish) {
        JSONObject memory = getMemory(ctx);
        java.util.List<String> values = new java.util.ArrayList<>();
        addSummary(values, memory, "household", turkish ? "Birlikte izleme" : "Viewing together");
        if (memory.optBoolean("has_children", false)) {
            String child = turkish ? "Çocuklu aile" : "Family with children";
            if (memory.optInt("child_age", 0) > 0) child += " (" + memory.optInt("child_age") + ")";
            values.add(child);
        }
        addSummary(values, memory, "preferred_genres", turkish ? "Türler" : "Genres");
        addSummary(values, memory, "likes", turkish ? "Sevdikleri" : "Likes");
        addSummary(values, memory, "dislikes", turkish ? "Sevmedikleri" : "Dislikes");
        addSummary(values, memory, "avoid_content", turkish ? "Kaçınılacak içerik" : "Content to avoid");
        addSummary(values, memory, "favorite_directors", turkish ? "Yönetmenler" : "Directors");
        addSummary(values, memory, "favorite_actors", turkish ? "Oyuncular" : "Actors");
        addSummary(values, memory, "preferred_languages", turkish ? "Diller" : "Languages");
        addSummary(values, memory, "preferred_countries", turkish ? "Ülkeler" : "Countries");
        addSummary(values, memory, "preferred_tone", turkish ? "Ton" : "Tone");
        addSummary(values, memory, "preferred_runtime", turkish ? "Süre tercihi" : "Runtime");
        addSummary(values, memory, "watching_context", turkish ? "İzleme alışkanlığı" : "Viewing context");
        if (values.isEmpty()) return turkish ? "Henüz kaydedilmiş tercih yok." : "No saved preferences yet.";
        return android.text.TextUtils.join("\n", values);
    }

    private static void addSummary(java.util.List<String> values, JSONObject memory, String key, String label) {
        String value = promptText(usableText(memory.opt(key)));
        if (!value.isEmpty()) values.add(label + ": " + value);
    }

    /** A name the viewer explicitly chose for Gamze to use; blank means do not address them by name. */
    public static String name(Context ctx) {
        if (ctx == null) return "";
        String v = Prefs.get(ctx).getString(PREF_VIEWER_NAME, "");
        if (v != null && !v.trim().isEmpty()) {
            return v.trim();
        }
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            String fromSys = android.provider.Settings.Global.getString(cr, "cinetv_user_name");
            if (fromSys == null || fromSys.trim().isEmpty()) {
                fromSys = android.provider.Settings.Secure.getString(cr, "cinetv_user_name");
            }
            if (fromSys != null && !fromSys.trim().isEmpty()) {
                String clean = sanitizeViewerName(fromSys);
                if (!clean.isEmpty()) {
                    Prefs.get(ctx).edit().putString(PREF_VIEWER_NAME, clean).apply();
                    try { android.provider.Settings.Global.putString(cr, "cinetv_user_name", null); } catch (Throwable ignored) {}
                    try { android.provider.Settings.Secure.putString(cr, "cinetv_user_name", null); } catch (Throwable ignored) {}
                    return clean;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String sanitizeViewerName(String name) {
        if (name == null) return "";
        String clean = name.replaceAll("[\\r\\n\\t\"'`]+", " ").trim().replaceAll("\\s+", " ");
        if (clean.length() > 40) clean = clean.substring(0, 40).trim();
        return clean;
    }

    public static boolean isValidViewerName(String name) {
        String clean = sanitizeViewerName(name);
        return clean.length() >= 2;
    }

    /**
     * Pure validation helper: CineTV requires a TMDB API key/token, a Google (Gemini) AI API key,
     * and a viewer name for TTS addressing before normal operation.
     */
    public static boolean hasRequiredCredentials(boolean hasTmdb, boolean hasAiKey, String viewerName) {
        return hasTmdb && hasAiKey && isValidViewerName(viewerName);
    }

    public static void setName(Context ctx, String name) {
        if (ctx == null) return;
        String clean = sanitizeViewerName(name);
        Prefs.get(ctx).edit().putString(PREF_VIEWER_NAME, clean).apply();
    }
}
