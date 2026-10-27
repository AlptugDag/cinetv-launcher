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
            "com.mediatek.wwtv.mediaplayer", "com.swap.noroot", "com.allakore.swapnoroot"));

    /** Never offered anywhere: this launcher, firmware services, factory and test menus. */
    private static final String[] NEVER_FRAGMENTS = {
            "com.cinetv.launcher", "factory", "webview", "tv.service", "tvcenter", "setupwraith", "tvlauncher",
            "launcherx", "rctest", "channeleditor", "wwtv.mediaplayer", "swap", "noroot", "zram", "ramexpander"};

    private static final String[] HIDDEN_FRAGMENTS = {
            "factory", "webview", "mediaplayer", "rctest", "channeleditor", "airplay", "nova", "bein", "swap", "noroot", "zram", "ramexpander"};

    private AppCatalog() {}

    /**
     * The installer's choices for this viewer, handed over once through Settings.Global:
     * {@code cinetv_live_source} (tuner, hdmi, hdmi:N or off) and {@code cinetv_dock} (Home's first row).
     */
    private static void applyHandoff(Context ctx) {
        android.content.ContentResolver cr = ctx.getContentResolver();
        try {
            String source = android.provider.Settings.Global.getString(cr, "cinetv_live_source");
            if (source != null && !source.isEmpty()) {
                if ("off".equals(source)) hide(ctx, AppLauncher.LIVE_TV);
                else {
                    Prefs.get(ctx).edit().putString(Prefs.LIVE_SOURCE, source).apply();
                    addToHome(ctx, AppLauncher.LIVE_TV);
                }
                android.provider.Settings.Global.putString(cr, "cinetv_live_source", null);
            }
            String dock = android.provider.Settings.Global.getString(cr, "cinetv_dock");
            if (dock != null && !dock.isEmpty()) {
                Prefs.get(ctx).edit().putString(Prefs.DOCK_ORDER, dock).apply();
                android.provider.Settings.Global.putString(cr, "cinetv_dock", null);
            }
        } catch (Throwable ignored) {
        }
    }

    public static Snapshot load(Context ctx) {
        applyHandoff(ctx);
        PackageManager pm = ctx.getPackageManager();
        SharedPreferences prefs = Prefs.get(ctx);
        Set<String> userHidden = csvSet(prefs.getString(Prefs.HIDDEN_APPS, ""));
        Set<String> pinned = csvSet(prefs.getString(Prefs.PINNED_APPS, ""));
        Snapshot snap = new Snapshot();
        Map<String, AppTileItem> found = new LinkedHashMap<>();

        for (ResolveInfo ri : queryLaunchers(pm)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            snap.packages.add(pkg);
            if (found.containsKey(pkg) || userHidden.contains(pkg) || (isHidden(pkg) && !pinned.contains(pkg))) continue;
            AppTileItem item = new AppTileItem();
            item.packageName = pkg;
            CharSequence label = ri.loadLabel(pm);
            item.label = label != null ? label.toString() : pkg;
            found.put(pkg, item);
        }

        String youtube = installedTizenTube(snap.packages);
        for (String essential : new String[]{AppLauncher.NUVIO, youtube, "com.turkcell.ott"}) {
            if (!found.containsKey(essential) && !userHidden.contains(essential) && AppLauncher.isLaunchable(pm, essential)) {
                AppTileItem item = new AppTileItem();
                item.packageName = essential;
                item.label = essential;
                found.put(essential, item);
            }
        }

        // The TV's own channels, on TVs that have a tuner; hidden from Home (or Settings) like any app.
        if (!userHidden.contains(AppLauncher.LIVE_TV) && (AppLauncher.liveTvPackage(pm) != null || AppLauncher.hasHdmiSource(ctx))) {
            AppTileItem tv = new AppTileItem();
            tv.packageName = AppLauncher.LIVE_TV;
            tv.label = ctx.getString(com.cinetv.launcher.R.string.live_tv);
            found.put(AppLauncher.LIVE_TV, tv);
        }

        String dockCsv = prefs.getString(Prefs.DOCK_ORDER, "");
        List<String> dockOrder = dockCsv.isEmpty()
                ? Arrays.asList(AppLauncher.NUVIO, youtube, "com.turkcell.ott", AppLauncher.LIVE_TV)
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

    /** The app drawer: real apps only, without this launcher and the TV's own service packages. */
    public static List<Entry> drawerApps(Context ctx) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : allApps(ctx)) if (!isNever(e.packageName) && !isHidden(e.packageName)) out.add(e);
        return out;
    }

    /** Apps that could be added to Home: installed, not on Home now, not a firmware service. */
    public static List<Entry> addable(Context ctx, Set<String> onHome) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : allApps(ctx)) if (!onHome.contains(e.packageName) && !isNever(e.packageName)) out.add(e);
        return out;
    }

    /** Puts an app back on Home (after Hide, or one CineTV hides by default). */
    public static void addToHome(Context ctx, String pkg) {
        SharedPreferences prefs = Prefs.get(ctx);
        Set<String> hidden = csvSet(prefs.getString(Prefs.HIDDEN_APPS, ""));
        hidden.remove(pkg);
        Set<String> pinned = csvSet(prefs.getString(Prefs.PINNED_APPS, ""));
        if (isHidden(pkg)) pinned.add(pkg);
        prefs.edit().putString(Prefs.HIDDEN_APPS, String.join(",", hidden))
                .putString(Prefs.PINNED_APPS, String.join(",", pinned)).apply();
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
        Set<String> pinned = csvSet(prefs.getString(Prefs.PINNED_APPS, ""));
        pinned.remove(pkg);
        prefs.edit().putString(Prefs.HIDDEN_APPS, String.join(",", hidden))
                .putString(Prefs.PINNED_APPS, String.join(",", pinned)).apply();
    }

    /** Whether Home shows the TV-channels tile (Settings switch). */
    public static boolean liveTvShown(Context ctx) {
        return !csvSet(Prefs.get(ctx).getString(Prefs.HIDDEN_APPS, "")).contains(AppLauncher.LIVE_TV);
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

    private static boolean isNever(String pkg) {
        String lower = pkg.toLowerCase(Locale.ROOT);
        for (String f : NEVER_FRAGMENTS) if (lower.contains(f)) return true;
        return false;
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
