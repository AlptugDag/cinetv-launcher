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

    public static synchronized void update(Context ctx, JSONObject learned) {
        if (learned == null || learned.length() == 0) return;
        try {
            JSONObject current = getMemory(ctx);
            Iterator<String> keys = learned.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                current.put(k, learned.get(k));
            }
            Prefs.get(ctx).edit().putString(PREF_GAMZE_MEMORY, current.toString()).apply();
            Log.i(TAG, "Gamze local memory updated: " + learned.toString());
        } catch (Throwable t) {
            Log.w(TAG, "Failed to update Gamze memory: " + t.getMessage());
        }
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
        StringBuilder sb = new StringBuilder("\nVIEWER LOCAL CONTEXT & HOUSEHOLD MEMORY (Persisted across sessions):\n");
        if (mem.has("household")) sb.append("- İzleme Ortamı: ").append(mem.optString("household")).append("\n");
        if (mem.has("has_children") && mem.optBoolean("has_children")) {
            sb.append("- Çocuk Durumu: Evde çocuk var");
            if (mem.has("child_age")) sb.append(" (Yaş: ").append(mem.optInt("child_age")).append(")");
            sb.append(". Aile ortamında şiddet, aşırı cinsellik ve travmatik unsurlardan kaçın.\n");
        }
        if (mem.has("preferred_genres")) sb.append("- Sevilen Türler: ").append(mem.optString("preferred_genres")).append("\n");
        if (mem.has("avoid_content")) sb.append("- Kaçınılacak Temalar: ").append(mem.optString("avoid_content")).append("\n");
        if (mem.has("preferred_tone")) sb.append("- İletişim Tonu: ").append(mem.optString("preferred_tone")).append("\n");
        return sb.toString();
    }
}
