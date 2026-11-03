package com.cinetv.launcher.ai;

import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Gamze's multi-turn conversation memory: keeps the ongoing chat history so Gamze acts as a
 * comprehensive Gemini chatbot across TV voice/text and the mobile web UI, resolving follow-ups,
 * deep film discussions, trivia, and TV control seamlessly.
 */
public final class GamzeConversation {

    public static final class Turn {
        public final String viewer;
        public final String gamze;
        public final long timestampMs;
        public final String shelfTitle;

        public Turn(String viewer, String gamze, long timestampMs, String shelfTitle) {
            this.viewer = viewer == null ? "" : viewer.trim();
            this.gamze = gamze == null ? "" : gamze.trim();
            this.timestampMs = timestampMs;
            this.shelfTitle = shelfTitle == null ? "" : shelfTitle.trim();
        }
    }

    private static final int MAX_TURNS = 24;
    private static final long FORGET_AFTER_MS = 60 * 60_000L;
    private static final Deque<Turn> turns = new ArrayDeque<>();
    private static long lastAt;

    private GamzeConversation() {}

    public static synchronized void record(String viewer, String gamze) {
        record(viewer, gamze, "");
    }

    public static synchronized void record(String viewer, String gamze, String shelfTitle) {
        expire();
        if (viewer == null || viewer.trim().isEmpty()) return;
        turns.addLast(new Turn(viewer, gamze, System.currentTimeMillis(), shelfTitle));
        while (turns.size() > MAX_TURNS) turns.removeFirst();
        lastAt = SystemClock.elapsedRealtime();
    }

    /** True while a conversation is still warm (within the last few minutes). */
    public static synchronized boolean active() {
        expire();
        return !turns.isEmpty() && SystemClock.elapsedRealtime() - lastAt < 5 * 60_000L;
    }

    public static synchronized List<Turn> entries() {
        expire();
        return new ArrayList<>(turns);
    }

    public static synchronized JSONArray toJsonArray() {
        expire();
        JSONArray arr = new JSONArray();
        for (Turn t : turns) {
            try {
                JSONObject o = new JSONObject();
                o.put("viewer", t.viewer);
                o.put("gamze", t.gamze);
                o.put("ts", t.timestampMs);
                if (!t.shelfTitle.isEmpty()) o.put("shelfTitle", t.shelfTitle);
                arr.put(o);
            } catch (Throwable ignored) {}
        }
        return arr;
    }

    public static synchronized String formatForPrompt() {
        expire();
        if (turns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nTHE CONVERSATION SO FAR (oldest first; continue it naturally, resolve 'that one', 'older', 'another', 'why', 'tell me more' against it):\n");
        for (Turn t : turns) {
            sb.append("Viewer: ").append(t.viewer).append('\n');
            if (!t.gamze.isEmpty()) sb.append("Gamze: ").append(t.gamze).append('\n');
            if (!t.shelfTitle.isEmpty()) sb.append("  [Curated Shelf: ").append(t.shelfTitle).append("]\n");
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        turns.clear();
        lastAt = 0L;
    }

    private static void expire() {
        if (!turns.isEmpty() && lastAt > 0 && SystemClock.elapsedRealtime() - lastAt > FORGET_AFTER_MS) {
            turns.clear();
        }
    }
}
