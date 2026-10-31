package com.cinetv.launcher.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Gamze AI Dynamic Local Context Memory:
 * Stores compact, dynamically updated household & viewer profiles in local storage
 * (household composition, children presence/ages, sensitive content filters, favorite genres & directors)
 * so Gamze remembers the viewer and continually refines personalized curation.
 */
public final class GamzeMemory {

    private static final String TAG = "GamzeMemory";
    private static final String PREF_GAMZE_MEMORY = "gamze_viewer_memory_json";

    private GamzeMemory() {}

    public static synchronized JSONObject getMemory(Context ctx) {
        String raw = Prefs.get(ctx).getString(PREF_GAMZE_MEMORY, "{}");
        try {
            return new JSONObject(raw);
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    /**
     * Merges what Gamze learned in a reply. Empty or default values never overwrite what is known (the
     * model fills every field of its schema), and likes/dislikes accumulate instead of replacing.
     */
    public static synchronized void update(Context ctx, JSONObject learned) {
        if (learned == null || learned.length() == 0) return;
        try {
            JSONObject current = getMemory(ctx);
            boolean changed = false;
            Iterator<String> keys = learned.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                Object v = learned.opt(k);
                if (v == null || v == JSONObject.NULL) continue;
                if (v instanceof String) {
                    String sv = ((String) v).trim();
                    if (sv.isEmpty() || sv.equalsIgnoreCase("unknown") || sv.equalsIgnoreCase("none")) continue;
                    if ("likes".equals(k) || "dislikes".equals(k) || "preferred_genres".equals(k) || "avoid_content".equals(k)) {
                        sv = mergeList(current.optString(k, ""), sv);
                    }
                    if (!sv.equals(current.optString(k, null))) {
                        current.put(k, sv);
                        changed = true;
                    }
                } else if (v instanceof Boolean) {
                    if ((Boolean) v && !current.optBoolean(k)) {
                        current.put(k, true);
                        changed = true;
                    }
                } else if (v instanceof Number) {
                    if (((Number) v).doubleValue() > 0 && current.optDouble(k, 0) != ((Number) v).doubleValue()) {
                        current.put(k, v);
                        changed = true;
                    }
                }
            }
            if (changed) Prefs.get(ctx).edit().putString(PREF_GAMZE_MEMORY, current.toString()).apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to update Gamze memory: " + t.getMessage());
        }
    }

    /** "a, b" + "b, c" = "a, b, c", keeping the newest twelve. */
    private static String mergeList(String old, String add) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String part : (old + "," + add).split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                set.remove(p);
                set.add(p);
            }
        }
        java.util.List<String> list = new java.util.ArrayList<>(set);
        return android.text.TextUtils.join(", ", list.subList(Math.max(0, list.size() - 12), list.size()));
    }

    public static synchronized void clear(Context ctx) {
        Prefs.get(ctx).edit().remove(PREF_GAMZE_MEMORY).apply();
    }

    /**
     * Formats a concise context block for injection into the Gemini system prompt.
     */
    public static synchronized String formatForPrompt(Context ctx) {
        JSONObject mem = getMemory(ctx);
        if (mem.length() == 0) return "";
        StringBuilder sb = new StringBuilder("\nWHAT YOU KNOW ABOUT THIS HOUSEHOLD (from earlier conversations; use it naturally, never recite it):\n");
        if (mem.has("name")) sb.append("- The viewer's name: ").append(mem.optString("name")).append("\n");
        if (mem.has("household")) sb.append("- Usually watches: ").append(mem.optString("household")).append("\n");
        if (mem.optBoolean("has_children")) {
            sb.append("- There are children at home");
            if (mem.optInt("child_age") > 0) sb.append(" (about ").append(mem.optInt("child_age")).append(")");
            sb.append(": in family settings avoid violence, sex and trauma.\n");
        }
        if (mem.has("preferred_genres")) sb.append("- Likes: ").append(mem.optString("preferred_genres")).append("\n");
        if (mem.has("likes")) sb.append("- Loved titles: ").append(mem.optString("likes")).append("\n");
        if (mem.has("dislikes")) sb.append("- Did not enjoy: ").append(mem.optString("dislikes")).append("\n");
        if (mem.has("avoid_content")) sb.append("- Avoid: ").append(mem.optString("avoid_content")).append("\n");
        if (mem.has("preferred_tone")) sb.append("- Prefers this tone: ").append(mem.optString("preferred_tone")).append("\n");
        return sb.toString();
    }

    /** The viewer's name, if they ever told Gamze; empty otherwise. */
    public static String name(Context ctx) {
        return getMemory(ctx).optString("name", "");
    }
}
