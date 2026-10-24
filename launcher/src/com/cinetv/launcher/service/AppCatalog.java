package com.cinetv.launcher.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Installed apps, split into the dock and the grid, with the user's order and hidden list. */
public final class AppCatalog {

    public static final class Snapshot {
        public final List<AppTileItem> dock = new ArrayList<>();
        public final List<AppTileItem> grid = new ArrayList<>();
        public final Set<String> packages = new HashSet<>();
    }

    /** Raw entry for the all-apps drawer. */
    public static final class Entry {
        public final String packageName;
        public final String label;
        public final Drawable icon;

        Entry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final Set<String> HIDDEN_SYSTEM_PACKAGES = new HashSet<>(Arrays.asList(
            "com.cinetv.launcher", "com.google.android.youtube.tv", "com.google.android.tungsten.setupwraith",
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "tr.com.bein.connect.tv",
            "io.freemytv", "com.cybercat.adbappcontrol.tv", "com.arcelik.ChannelEditor", "com.arcelik.tv.rctest",
            "com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.tvcenter.rro", "mediatek.factorymenu.ui",
            "arcelik.factorymenu.hatone", "com.mediatek.tv.factory", "com.mediatek.wwtv.webview",
            "com.google.android.webview", "com.mediatek.tv.service", "io.github.jqssun.airplay", "org.courville.nova",
            "com.mediatek.wwtv.mediaplayer"));

    private static final String[] HIDDEN_FRAGMENTS = {
            "factory", "webview", "mediaplayer", "rctest", "channeleditor", "airplay", "nova", "bein"};

    private AppCatalog() {}

    public static Snapshot load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        SharedPreferences prefs = Prefs.get(ctx);
        Set<String> userHidden = csvSet(prefs.getString(Prefs.HIDDEN_APPS, ""));
        Snapshot snap = new Snapshot();
        Map<String, AppTileItem> found = new LinkedHashMap<>();

        for (ResolveInfo ri : queryLaunchers(pm)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            snap.packages.add(pkg);
            if (found.containsKey(pkg) || isHidden(pkg) || userHidden.contains(pkg)) continue;
            AppTileItem item = new AppTileItem();
            item.packageName = pkg;
            CharSequence label = ri.loadLabel(pm);
            item.label = label != null ? label.toString() : pkg;
            found.put(pkg, item);
        }

        String youtube = installedTizenTube(snap.packages);
        String caster = snap.packages.contains("com.instantbits.cast.receiver")
                ? "com.instantbits.cast.receiver" : "com.instantbits.cast.webvideo";
        for (String essential : new String[]{AppLauncher.NUVIO, youtube, caster, "com.turkcell.ott"}) {
            if (!found.containsKey(essential) && !userHidden.contains(essential) && AppLauncher.isLaunchable(pm, essential)) {
                AppTileItem item = new AppTileItem();
                item.packageName = essential;
                item.label = essential;
                found.put(essential, item);
            }
        }

        String dockCsv = prefs.getString(Prefs.DOCK_ORDER, "");
        List<String> dockOrder = dockCsv.isEmpty()
                ? Arrays.asList(AppLauncher.NUVIO, youtube, caster, "com.turkcell.ott")
                : Arrays.asList(dockCsv.split(","));
        for (String pkg : dockOrder) {
            AppTileItem item = found.remove(pkg);
            if (item != null) snap.dock.add(item);
        }
        snap.grid.addAll(found.values());
        final List<String> gridOrder = Arrays.asList(prefs.getString(Prefs.APP_ORDER, "").split(","));
        Collections.sort(snap.grid, (a, b) -> {
            int ia = gridOrder.indexOf(a.packageName);
            int ib = gridOrder.indexOf(b.packageName);
            if (ia == -1 && ib == -1) return a.label.compareToIgnoreCase(b.label);
            if (ia == -1) return 1;
            if (ib == -1) return -1;
            return Integer.compare(ia, ib);
        });
        return snap;
    }

    /** Package names of every launchable app, to detect installs/removals cheaply. */
    public static Set<String> launchablePackages(Context ctx) {
        Set<String> out = new HashSet<>();
        for (ResolveInfo ri : queryLaunchers(ctx.getPackageManager())) {
            if (ri.activityInfo != null) out.add(ri.activityInfo.packageName);
        }
        return out;
    }

    public static List<Entry> allApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (ResolveInfo ri : queryLaunchers(pm)) {
            if (ri.activityInfo == null || unique.containsKey(ri.activityInfo.packageName)) continue;
            CharSequence label = ri.loadLabel(pm);
            unique.put(ri.activityInfo.packageName, new Entry(ri.activityInfo.packageName,
                    label == null ? ri.activityInfo.packageName : label.toString(), ri.loadIcon(pm)));
        }
        List<Entry> list = new ArrayList<>(unique.values());
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    public static void saveOrder(Context ctx, List<AppTileItem> dock, List<AppTileItem> grid) {
        Prefs.get(ctx).edit()
                .putString(Prefs.DOCK_ORDER, csv(dock))
                .putString(Prefs.APP_ORDER, csv(grid))
                .apply();
    }

    public static void hide(Context ctx, String pkg) {
        SharedPreferences prefs = Prefs.get(ctx);
        Set<String> hidden = csvSet(prefs.getString(Prefs.HIDDEN_APPS, ""));
        hidden.add(pkg);
        prefs.edit().putString(Prefs.HIDDEN_APPS, String.join(",", hidden)).apply();
    }

    public static boolean isTizenTube(String pkg) {
        if (pkg == null) return false;
        String lower = pkg.toLowerCase(Locale.ROOT);
        return lower.contains("tizentube") || lower.contains("smarttube") || lower.equals("com.teamsmart.videomanager.tv");
    }

    private static String installedTizenTube(Set<String> pkgs) {
        for (String p : AppLauncher.YOUTUBE_PACKAGES) if (pkgs.contains(p)) return p;
        for (String p : pkgs) if (isTizenTube(p)) return p;
        return AppLauncher.YOUTUBE_PACKAGES[0];
    }

    private static boolean isHidden(String pkg) {
        if (HIDDEN_SYSTEM_PACKAGES.contains(pkg)) return true;
        String lower = pkg.toLowerCase(Locale.ROOT);
        for (String f : HIDDEN_FRAGMENTS) if (lower.contains(f)) return true;
        return false;
    }

    private static List<ResolveInfo> queryLaunchers(PackageManager pm) {
        List<ResolveInfo> all = new ArrayList<>();
        try {
            all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
            all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        } catch (Throwable ignored) {
        }
        return all;
    }

    private static Set<String> csvSet(String csv) {
        Set<String> out = new HashSet<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private static String csv(List<AppTileItem> items) {
        StringBuilder sb = new StringBuilder();
        for (AppTileItem i : items) {
            if (sb.length() > 0) sb.append(',');
            sb.append(i.packageName);
        }
        return sb.toString();
    }
}
