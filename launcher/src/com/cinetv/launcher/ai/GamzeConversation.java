package com.cinetv.launcher.ai;

import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Gamze's short-term memory: the last few exchanges of the current conversation, so "peki daha
 * eskisi?" or "onu aç" mean something. Forgotten after a quiet spell, like a person would.
 */
public final class GamzeConversation {

    private static final int MAX_TURNS = 6;
    private static final long FORGET_AFTER_MS = 20 * 60_000L;
    private static final Deque<String[]> turns = new ArrayDeque<>();
    private static long lastAt;

    private GamzeConversation() {}

    public static synchronized void record(String viewer, String gamze) {
        expire();
        if (viewer == null || viewer.trim().isEmpty()) return;
        turns.addLast(new String[]{viewer.trim(), gamze == null ? "" : gamze.trim()});
        while (turns.size() > MAX_TURNS) turns.removeFirst();
        lastAt = SystemClock.elapsedRealtime();
    }

    /** True while a conversation is still warm (within the last few minutes). */
    public static synchronized boolean active() {
        expire();
        return !turns.isEmpty() && SystemClock.elapsedRealtime() - lastAt < 3 * 60_000L;
    }

    public static synchronized String formatForPrompt() {
        expire();
        if (turns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nTHE CONVERSATION SO FAR (oldest first; continue it naturally, resolve 'that one', 'older', 'another' against it):\n");
        for (String[] t : turns) {
            sb.append("Viewer: ").append(t[0]).append('\n');
            if (!t[1].isEmpty()) sb.append("Gamze: ").append(t[1]).append('\n');
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        turns.clear();
    }

    private static void expire() {
        if (!turns.isEmpty() && SystemClock.elapsedRealtime() - lastAt > FORGET_AFTER_MS) turns.clear();
    }
}
