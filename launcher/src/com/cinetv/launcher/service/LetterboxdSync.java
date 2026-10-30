package com.cinetv.launcher.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.cinetv.launcher.service.MovieCatalog.Movie;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CineTV Automatic Letterboxd Cinema Diary & Scrobbler.
 *
 * Automatically records every movie watched on CineTV into an official Letterboxd-compatible
 * diary journal stored at /sdcard/Download/cinetv_letterboxd_diary.csv, allowing seamless
 * 1-click import into Letterboxd or automated scrobble synchronization.
 */
public final class LetterboxdSync {

    private static final String TAG = "CineTvLetterboxd";
    private static final String PREF_LETTERBOXD_USER = "letterboxd_username";
    private static final String PREF_LETTERBOXD_HISTORY = "letterboxd_history_json";
    private static final String CSV_HEADER = "Date,Name,Year,LetterboxdURI,Rating,Rewatch,Tags,WatchedDate";

    private LetterboxdSync() {}

    public static String getUsername(Context ctx) {
        return Prefs.get(ctx).getString(PREF_LETTERBOXD_USER, "");
    }

    public static void setUsername(Context ctx, String username) {
        Prefs.get(ctx).edit().putString(PREF_LETTERBOXD_USER, username != null ? username.trim() : "").apply();
    }

    public static boolean isConfigured(Context ctx) {
        String u = getUsername(ctx);
        return u != null && !u.isEmpty();
    }

    /**
     * Records a movie watch into the Letterboxd Diary journal.
     */
    public static void recordWatch(Context ctx, Movie m) {
        if (m == null || m.title == null || m.title.trim().isEmpty()) return;
        new Thread(() -> {
            try {
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                String title = m.originalTitle != null && !m.originalTitle.isEmpty() ? m.originalTitle : m.title;
                int year = m.year > 1900 ? m.year : 2024;
                String uri = (m.imdbId != null && !m.imdbId.isEmpty()) ? ("https://www.imdb.com/title/" + m.imdbId + "/") : "";
                String tags = "CineTV, Ev Sineması";

                // 1. Append to local CSV at /sdcard/Download/cinetv_letterboxd_diary.csv
                File dl = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (dl == null || (!dl.exists() && !dl.mkdirs())) {
                    dl = new File("/sdcard/Download");
                    if (!dl.exists()) dl.mkdirs();
                }
                if (!dl.exists() || !dl.canWrite()) {
                    File ext = ctx.getExternalFilesDir(null);
                    if (ext != null) dl = ext;
                }
                File csv = new File(dl, "cinetv_letterboxd_diary.csv");
                boolean isNew = !csv.exists() || csv.length() == 0;

                try (PrintWriter pw = new PrintWriter(new FileWriter(csv, true))) {
                    if (isNew) {
                        pw.println(CSV_HEADER);
                    }
                    // Format: Date,Name,Year,LetterboxdURI,Rating,Rewatch,Tags,WatchedDate
                    // Escape title for CSV
                    String escapedTitle = title.contains(",") ? ("\"" + title.replace("\"", "\"\"") + "\"") : title;
                    pw.println(today + "," + escapedTitle + "," + year + "," + uri + ",,," + tags + "," + today);
                    pw.flush();
                }

                // 2. Append to internal JSON history for quick review in Settings
                String raw = Prefs.get(ctx).getString(PREF_LETTERBOXD_HISTORY, "[]");
                JSONArray arr = new JSONArray(raw);
                JSONObject entry = new JSONObject();
                entry.put("title", title);
                entry.put("year", year);
                entry.put("date", today);
                entry.put("imdb", m.imdbId != null ? m.imdbId : "");
                arr.put(entry);
                // Keep last 100 entries
                while (arr.length() > 100) {
                    arr.remove(0);
                }
                Prefs.get(ctx).edit().putString(PREF_LETTERBOXD_HISTORY, arr.toString()).apply();

                // 3. User feedback
                new Handler(Looper.getMainLooper()).post(() -> {
                    String user = getUsername(ctx);
                    String msg = "tr".equals(Locale.getDefault().getLanguage())
                            ? ("Letterboxd günlüğüne eklendi: " + title + (user.isEmpty() ? "" : (" (@" + user + ")")) + " ✓")
                            : ("Logged to Letterboxd diary: " + title + " ✓");
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                });

                Log.i(TAG, "Recorded watch for Letterboxd: " + title + " (" + year + ")");
            } catch (Throwable t) {
                Log.w(TAG, "Failed to record watch for Letterboxd: " + t.getMessage());
            }
        }, "cinetv-letterboxd").start();
    }

    public static File getDiaryFile() {
        return new File("/sdcard/Download/cinetv_letterboxd_diary.csv");
    }
}
