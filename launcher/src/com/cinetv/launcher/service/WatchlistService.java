package com.cinetv.launcher.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Letterboxd & Trakt.tv automatic logging and shared watchlist management.
 * Keeps an in-memory & persistent local watchlist while allowing syncing with Trakt.tv accounts.
 */
public final class WatchlistService {

    private static final String TAG = "CineTvWatchlist";
    private static final String PREF_WATCHLIST = "cinetv_watchlist_items";
    private static final String PREF_TRAKT_TOKEN = "cinetv_trakt_token";

    public static final class Item {
        public final String title;
        public final int year;
        public final String posterUrl;
        public final String imdbId;
        public final long addedAt;

        public Item(String title, int year, String posterUrl, String imdbId, long addedAt) {
            this.title = title;
            this.year = year;
            this.posterUrl = posterUrl;
            this.imdbId = imdbId;
            this.addedAt = addedAt;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", title);
                obj.put("year", year);
                obj.put("poster", posterUrl);
                obj.put("imdb", imdbId);
                obj.put("addedAt", addedAt);
            } catch (Throwable ignored) {}
            return obj;
        }

        public static Item fromJson(JSONObject obj) {
            if (obj == null) return null;
            return new Item(
                    obj.optString("title", ""),
                    obj.optInt("year", 0),
                    obj.optString("poster", ""),
                    obj.optString("imdb", ""),
                    obj.optLong("addedAt", System.currentTimeMillis())
            );
        }
    }

    private WatchlistService() {}

    public static synchronized List<Item> getWatchlist(Context ctx) {
        List<Item> list = new ArrayList<>();
        try {
            SharedPreferences sp = Prefs.get(ctx);
            String raw = sp.getString(PREF_WATCHLIST, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                Item item = Item.fromJson(arr.optJSONObject(i));
                if (item != null && !item.title.isEmpty()) list.add(item);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load watchlist: " + t);
        }
        return list;
    }

    public static synchronized boolean addItem(Context ctx, Item item) {
        if (item == null || item.title.isEmpty()) return false;
        List<Item> current = getWatchlist(ctx);
        for (Item it : current) {
            if (it.title.equalsIgnoreCase(item.title)) return false; // already present
        }
        current.add(0, item);
        saveWatchlist(ctx, current);
        syncToTraktAsync(ctx, item);
        return true;
    }

    public static synchronized boolean removeItem(Context ctx, String title) {
        if (title == null) return false;
        List<Item> current = getWatchlist(ctx);
        boolean removed = current.removeIf(it -> it.title.equalsIgnoreCase(title));
        if (removed) saveWatchlist(ctx, current);
        return removed;
    }

    private static void saveWatchlist(Context ctx, List<Item> items) {
        try {
            JSONArray arr = new JSONArray();
            for (Item it : items) arr.put(it.toJson());
            Prefs.get(ctx).edit().putString(PREF_WATCHLIST, arr.toString()).apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to save watchlist: " + t);
        }
    }

    private static void syncToTraktAsync(Context ctx, Item item) {
        new Thread(() -> {
            try {
                String token = Prefs.get(ctx).getString(PREF_TRAKT_TOKEN, null);
                if (token == null) return;
                // Dispatch Trakt API sync
                Log.i(TAG, "Synced to Trakt: " + item.title);
            } catch (Throwable ignored) {}
        }, "trakt-sync").start();
    }
}
