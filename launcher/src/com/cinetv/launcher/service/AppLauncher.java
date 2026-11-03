package com.cinetv.launcher.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import java.util.List;

/** Every way the launcher starts another app or a system screen. Shared by the home UI, key service and remote. */
public final class AppLauncher {

    public static final String NUVIO = "com.nuvio.tv";
    public static final String[] NUVIO_PACKAGES = {
            NUVIO, "com.stremio.one", "com.stremio.tv", "com.nuvio.rs",
            "com.nuvio.legacy", "com.nuvio.app", "com.nuvio",
            "org.stremio.one", "com.stremio"
    };

    /** Discovers all installed Nuvio/Stremio packages (including Nuvio RS, Nuvio Legacy, custom APKs). */
    public static List<String> installedNuvioPackages(Context ctx) {
        List<String> list = new java.util.ArrayList<>();
        if (ctx == null) return list;
        PackageManager pm = ctx.getPackageManager();
        if (pm == null) return list;

        try {
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/movie/tt0000001"));
            List<ResolveInfo> activities = pm.queryIntentActivities(probe, 0);
            for (ResolveInfo ri : activities) {
                if (ri.activityInfo != null && !list.contains(ri.activityInfo.packageName)) {
                    list.add(ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {}

        for (String p : NUVIO_PACKAGES) {
            if (!list.contains(p) && isLaunchable(pm, p)) {
                list.add(p);
            }
        }

        try {
            List<android.content.pm.PackageInfo> installed = pm.getInstalledPackages(0);
            for (android.content.pm.PackageInfo pi : installed) {
                String lp = pi.packageName.toLowerCase(java.util.Locale.ROOT);
                if ((lp.contains("nuvio") || lp.contains("stremio")) && !list.contains(pi.packageName) && isLaunchable(pm, pi.packageName)) {
                    list.add(pi.packageName);
                }
            }
        } catch (Throwable ignored) {}

        return list;
    }

    /** Resolves active Nuvio package (respects user selection in Settings if multiple are installed). */
    public static String resolveNuvioPackage(Context ctx) {
        String saved = Prefs.preferredNuvioPackage(ctx);
        PackageManager pm = ctx.getPackageManager();
        if (saved != null && isLaunchable(pm, saved)) return saved;
        List<String> pkgs = installedNuvioPackages(ctx);
        if (!pkgs.isEmpty()) return pkgs.get(0);
        return isLaunchable(pm, NUVIO) ? NUVIO : null;
    }
    public static final String[] YOUTUBE_PACKAGES = {
            "io.gh.reisxd.tizentube.cobalt", "com.reisxd.tizentube", "com.teamsmart.videomanager.tv",
            "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"};

    /** Opens CineTV's "send a video from your phone" screen (casting is built in). */
    public static void openPhoneCast(Context ctx) {
        start(ctx, new Intent(ctx, com.cinetv.launcher.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("action", "phone_cast"));
    }
    public static final String[] TV_PLUS_PACKAGES = {"com.turkcell.ott", "com.turkcell.tvplus"};

    private static final String TAG = "CineTvLaunch";

    /** Home's TV tile. Not a package: it opens the TV's own channels (tuner), whatever the brand. */
    public static final String LIVE_TV = "cinetv.livetv";
    /** Tuner apps by brand, for TVs whose system does not answer a channel link. */
    private static final String[] LIVE_TV_PACKAGES = {
            "com.google.android.tv", "com.android.tv", "com.mediatek.wwtv.tvcenter", "com.mediatek.tv.oneworld.tvcenter",
            "com.sony.dtv.tvx",
            "com.xiaomi.mitv.tvplayer"};

    private AppLauncher() {}

    public static Intent launchIntent(PackageManager pm, String pkg) {
        if (pm == null || pkg == null || pkg.isEmpty()) return null;
        Intent intent = null;
        try {
            intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                Intent probe = new Intent(Intent.ACTION_MAIN).setPackage(pkg);
                List<ResolveInfo> list = pm.queryIntentActivities(probe, 0);
                if (!list.isEmpty()) {
                    intent = new Intent(Intent.ACTION_MAIN)
                            .setComponent(new ComponentName(pkg, list.get(0).activityInfo.name));
                }
            }
        } catch (Throwable ignored) {
        }
        if (intent == null && NUVIO.equals(pkg)) {
            intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(NUVIO, NUVIO + ".MainActivity"));
        }
        if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    /** The app that shows the TV's channels: the system's handler for channel links, else a known tuner app; null without a tuner. */
    public static String liveTvPackage(PackageManager pm) {
        try {
            // with the channel type too: some tuner apps (Grundig's) only answer typed links
            ResolveInfo ri = pm.resolveActivity(new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI), 0);
            if (ri == null || ri.activityInfo == null || "android".equals(ri.activityInfo.packageName)) {
                ri = pm.resolveActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.Channels.CONTENT_URI,
                        TvContract.Channels.CONTENT_TYPE), 0);
            }
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) return ri.activityInfo.packageName;
        } catch (Throwable ignored) {
        }
        for (String p : LIVE_TV_PACKAGES) if (launchIntent(pm, p) != null) return p;
        return null;
    }

    /** A satellite, cable or IPTV box on HDMI chosen as this viewer's live TV. */
    public static boolean hasHdmiSource(Context ctx) {
        return Prefs.get(ctx).getString(Prefs.LIVE_SOURCE, "tuner").startsWith("hdmi");
    }

    /** HDMI port N (0: the connected one), e.g. for "HDMI 2" said to the TV. */
    public static boolean openHdmiPort(Context ctx, int port) {
        return openHdmi(ctx, port);
    }

    /** Switches the TV to the HDMI input of the viewer's box: port N if given, else the connected one. */
    private static boolean openHdmi(Context ctx, int port) {
        try {
            android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return false;
            List<android.media.tv.TvInputInfo> ports = new java.util.ArrayList<>();
            for (android.media.tv.TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == android.media.tv.TvInputInfo.TYPE_HDMI && info.getParentId() == null) ports.add(info);
            }
            if (ports.isEmpty()) return false;
            java.util.Collections.sort(ports, (a, b) -> a.getId().compareTo(b.getId()));
            android.media.tv.TvInputInfo pick = null;
            for (android.media.tv.TvInputInfo info : ports) {
                CharSequence label = info.loadLabel(ctx);
                if (port > 0 && label != null && label.toString().replaceAll("\\D+", "").equals(String.valueOf(port))) pick = info;
            }
            if (pick == null && port > 0 && port <= ports.size()) pick = ports.get(port - 1);
            if (pick == null) {
                for (android.media.tv.TvInputInfo info : ports) {
                    if (tim.getInputState(info.getId()) == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED) {
                        pick = info;
                        break;
                    }
                }
            }
            if (pick == null) pick = ports.get(0);
            return openInput(ctx, pick.getId());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Switches the TV to one input (an HDMI port, AV, ...), through the TV's own app. */
    public static boolean openInput(Context ctx, String inputId) {
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId));
        String tvApp = liveTvPackage(ctx.getPackageManager());
        if (tvApp != null && start(ctx, new Intent(view).setPackage(tvApp))) return true;
        if (start(ctx, view)) return true;
        // the TV's app takes no input links (Grundig): its own input list instead
        openTvInputs(ctx);
        return true;
    }

    public static boolean openLiveTv(Context ctx) {
        String source = Prefs.get(ctx).getString(Prefs.LIVE_SOURCE, "tuner");
        if (source.startsWith("hdmi")) {
            int port = 0;
            try {
                port = Integer.parseInt(source.substring(source.indexOf(':') + 1));
            } catch (Throwable ignored) {
            }
            if (openHdmi(ctx, port)) return true;
            openTvInputs(ctx);
            return true;
        }
        String pkg = liveTvPackage(ctx.getPackageManager());
        if (pkg == null) return false;
        // Asked for: the boot guard must not send the tuner back to Home.
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        Intent intent = launchIntent(ctx.getPackageManager(), pkg);
        if (intent != null && start(ctx, intent)) return true;
        return start(ctx, new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI).setPackage(pkg));
    }

    public static boolean isLaunchable(PackageManager pm, String pkg) {
        if (LIVE_TV.equals(pkg)) return liveTvPackage(pm) != null;
        try {
            return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean launch(Context ctx, String pkg) {
        if (LIVE_TV.equals(pkg)) return openLiveTv(ctx);
        // Launching an app normally from the dock or drawer is not a direct content launch: Back stays inside that app.
        if (pkg != null) {
            for (String np : NUVIO_PACKAGES) {
                if (pkg.equals(np)) com.cinetv.launcher.TvKeyInterceptorService.launchedNuvioFromCineTv = false;
            }
            for (String yp : YOUTUBE_PACKAGES) {
                if (pkg.equals(yp)) com.cinetv.launcher.TvKeyInterceptorService.launchedYouTubeFromCineTv = false;
            }
        }
        Intent intent = launchIntent(ctx.getPackageManager(), pkg);
        if (intent == null) return false;
        return start(ctx, intent);
    }

    /** Starts the first installed package of the list; returns false when none is installed. */
    public static boolean launchFirst(Context ctx, String... packages) {
        for (String pkg : packages) {
            if (launch(ctx, pkg)) return true;
        }
        return false;
    }

    /** Opens a YouTube video URL directly inside the installed ad-free YouTube app (TizenTube). */
    public static boolean openYouTubeVideo(Context ctx, String url) {
        if (url == null || url.isEmpty()) {
            com.cinetv.launcher.TvKeyInterceptorService.launchedYouTubeFromCineTv = false;
            return launchFirst(ctx, YOUTUBE_PACKAGES);
        }
        com.cinetv.launcher.TvKeyInterceptorService.launchedYouTubeFromCineTv = true;

        String vid = null;
        if (url.contains("youtu.be/")) {
            int idx = url.indexOf("youtu.be/");
            vid = url.substring(idx + 9);
            if (vid.contains("?")) vid = vid.substring(0, vid.indexOf("?"));
            if (vid.contains("&")) vid = vid.substring(0, vid.indexOf("&"));
        } else if (url.contains("v=")) {
            int idx = url.indexOf("v=");
            vid = url.substring(idx + 2);
            if (vid.contains("&")) vid = vid.substring(0, vid.indexOf("&"));
        }

        String finalUrl = vid != null ? ("https://youtu.be/" + vid) : url;
        Uri uri = Uri.parse(finalUrl);

        for (String pkg : YOUTUBE_PACKAGES) {
            Intent view = new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (vid != null) {
                view.putExtra("video_id", vid);
                view.putExtra("v", vid);
                view.putExtra("url", finalUrl);
            }
            if (start(ctx, view)) return true;
        }
        Intent generic = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (vid != null) {
            generic.putExtra("video_id", vid);
            generic.putExtra("v", vid);
        }
        return start(ctx, generic);
    }

    /** Opens a title on Nuvio's detail/stream page; falls back to Nuvio's start screen. */
    /**
     * Once Nuvio's title page is up, clicks its "Oynat" button itself (found by its label, never a blind OK that
     * could land on "watched" or "+"), which opens the film's sources or plays it, per the viewer's Nuvio settings.
     * Tries for up to 15 seconds, then leaves the page as it is.
     */
    private static void pressPlayWhenReady(final Context app, final long startedAt, final boolean series, final boolean preferResume) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
            long elapsed = System.currentTimeMillis() - startedAt;
            boolean clicked = !com.cinetv.launcher.MainActivity.foreground && svc != null && svc.clickNuvioPlay(series, preferResume, elapsed);
            if (clicked) android.util.Log.i("CineTvPlay", (series ? "Series: resumed or episode list" : "Oynat") + " on Nuvio's title page");
            if (!clicked && elapsed < 15_000) pressPlayWhenReady(app, startedAt, series, preferResume);
        }, 800);
    }

    public static boolean openInNuvio(Context ctx, String title, String imdbId, String type, String posterUrl) {
        return openInNuvio(ctx, title, null, 0, imdbId, 0, type, posterUrl, false);
    }

    /**
     * Stremio-style stream add-ons answer IMDb ids ("tt…"); only a film with no IMDb id at all goes by its TMDB
     * id ("tmdb:123"). A film never falls back to Nuvio's search or home: that is how "Oynat" used to land on
     * Nuvio's home screen.
     */
    public static boolean openInNuvio(Context ctx, String title, String imdbId, int tmdbId, String type, String posterUrl) {
        return openInNuvio(ctx, title, null, 0, imdbId, tmdbId, type, posterUrl, false);
    }

    /** Opens a title and selects Nuvio's resume action first only for CineTV's Continue Watching card. */
    public static boolean openInNuvio(Context ctx, String title, String imdbId, int tmdbId, String type,
                                      String posterUrl, boolean preferResume) {
        return openInNuvio(ctx, title, null, 0, imdbId, tmdbId, type, posterUrl, preferResume);
    }

    public static boolean openInNuvio(Context ctx, String title, String originalTitle, int year,
                                      String imdbId, int tmdbId, String type,
                                      String posterUrl, boolean preferResume) {
        if ((title != null && "sessiz".equals(Creator.fold(title))) || "tt38983576".equals(imdbId)) {
            return openYouTubeVideo(ctx, Creator.SESSIZ_YOUTUBE_URL);
        }
        // If imdbId is missing or doesn't start with "tt", attempt to resolve using original title + year to prevent duplicate Turkish title conflicts
        if ((imdbId == null || !imdbId.startsWith("tt")) && title != null && !title.trim().isEmpty()) {
            try {
                com.cinetv.launcher.service.MovieCatalog.Movie resolved =
                        com.cinetv.launcher.service.MovieCatalog.resolve(ctx, title, originalTitle, year, type);
                if (resolved != null && resolved.imdbId != null && resolved.imdbId.startsWith("tt")) {
                    imdbId = resolved.imdbId;
                    if (resolved.tmdbId > 0) tmdbId = resolved.tmdbId;
                }
            } catch (Throwable ignored) {}
        }
        // Only fall back to "tmdb:<id>" if no valid "tt..." IMDb ID is available; stream addons in Nuvio require "tt..." IDs.
        if ((imdbId == null || !imdbId.startsWith("tt")) && tmdbId > 0 && !"tv".equals(type) && !"channel".equals(type)) {
            imdbId = "tmdb:" + tmdbId;
        }
        com.cinetv.launcher.TvKeyInterceptorService.launchedNuvioFromCineTv = true;
        com.cinetv.launcher.TvKeyInterceptorService.lastNuvioLaunchPreferResume = preferResume;
        String targetPkg = resolveNuvioPackage(ctx);

        if (imdbId != null && (imdbId.startsWith("tv:") || "tv".equals(type) || "channel".equals(type))) {
            Intent tvIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/tv/" + imdbId));
            if (targetPkg != null) tvIntent.setPackage(targetPkg);
            tvIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (start(ctx, tvIntent)) return true;
            tvIntent.setPackage(null);
            if (start(ctx, tvIntent)) return true;
        }
        if (imdbId != null && (imdbId.startsWith("tt") || imdbId.startsWith("tmdb:"))) {
            String kind = "series".equals(type) || "show".equals(type) ? "series" : "movie";
            // Tested on the TV (Nuvio 1.1.0): only nuvio://detail/<type>/<imdb> opens a title, and only when Nuvio
            // starts fresh. stremio:///detail/... and any link sent to an already running Nuvio are ignored, which is
            // why "Oynat" left the viewer on Nuvio's home or on the last film. So: a fresh task, the nuvio link,
            // and then OK on the page's focused "Oynat" (Nuvio resumes from the saved position by itself).
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("nuvio://detail/" + kind + "/" + imdbId));
            if (targetPkg != null) open.setPackage(targetPkg);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (start(ctx, open)) {
                pressPlayWhenReady(ctx.getApplicationContext(), System.currentTimeMillis(), "series".equals(kind), preferResume);
                return true;
            }
            Intent detail = new Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/" + kind + "/" + imdbId));
            if (targetPkg != null) detail.setPackage(targetPkg);
            detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (start(ctx, detail)) return true;

            Intent nuvioUri = new Intent(Intent.ACTION_VIEW, Uri.parse("nuvio:///detail/" + kind + "/" + imdbId));
            if (targetPkg != null) nuvioUri.setPackage(targetPkg);
            nuvioUri.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (start(ctx, nuvioUri)) return true;

            detail.setPackage(null);
            if (start(ctx, detail)) return true;
        }
        // Fallback: If imdbId is missing or stream intent didn't catch, deep-link search directly into Nuvio
        String searchTarget = (originalTitle != null && !originalTitle.trim().isEmpty()) ? originalTitle.trim() : (title != null ? title.trim() : "");
        if (!searchTarget.isEmpty()) {
            try {
                Intent searchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///search?search=" + Uri.encode(searchTarget)));
                if (targetPkg != null) searchIntent.setPackage(targetPkg);
                searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                if (start(ctx, searchIntent)) return true;
                searchIntent.setPackage(null);
                if (start(ctx, searchIntent)) return true;
            } catch (Throwable ignored) {}
        }
        if (targetPkg != null && launch(ctx, targetPkg)) return true;
        for (String p : installedNuvioPackages(ctx)) {
            if (launch(ctx, p)) return true;
        }
        return launchFirst(ctx, NUVIO_PACKAGES);
    }

    public static void openWifiSettings(Context ctx) {
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        Intent[] candidates = {
                new Intent(Settings.ACTION_WIFI_SETTINGS),
                new Intent("android.settings.NETWORK_SETTINGS"),
                new Intent(Settings.ACTION_WIRELESS_SETTINGS),
                new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity"),
                new Intent(Settings.ACTION_SETTINGS)
        };
        for (Intent i : candidates) {
            if (start(ctx, i)) return;
        }
    }

    public static void openSystemSettings(Context ctx) {
        openSystemPage(ctx, Settings.ACTION_SETTINGS, ".MainSettings");
    }

    public static boolean openNotificationAccessSettings(Context ctx) {
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            Log.w("AppLauncher", "Notification access settings are unavailable", e);
            return false;
        }
    }

    public static void openTvInputs(Context ctx) {
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        String[][] targets = {
                {"com.android.tv.settings", "com.android.tv.settings.inputs.InputsActivity"},
                {"com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity"},
                {"com.mediatek.tv.oneworld.tvcenter", "com.mediatek.tv.oneworld.tvcenter.nav.TurnkeyUiMainActivity"},
                {"com.google.android.tv.frameworkpackagestubs", "com.google.android.tv.frameworkpackagestubs.InputActivity"},
        };
        for (String[] t : targets) {
            if (start(ctx, new Intent().setClassName(t[0], t[1]))) return;
        }
        openSystemSettings(ctx);
    }

    public static void openAppInfo(Context ctx, String pkg) {
        start(ctx, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    public static void uninstall(Context ctx, String pkg) {
        if (!start(ctx, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)))) openAppInfo(ctx, pkg);
    }

    /**
     * Opens a page of the TV's own settings. On Google TV / Android TV the standard Settings actions are often
     * missing or point at a stub that closes at once (that is why accessibility "closed by itself"): the TV
     * settings app's real page is tried first, then the standard action, then the settings home. Only opens;
     * never changes a setting.
     */
    public static boolean openSystemPage(Context ctx, String action, String... tvSettingsActivities) {
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        android.content.pm.PackageManager pm = ctx.getPackageManager();
        String[] settingsPkgs = {"com.android.tv.settings", "com.google.android.tv.settings", "com.android.settings"};
        for (String pkg : settingsPkgs) {
            for (String cls : tvSettingsActivities) {
                String fullCls = cls.startsWith(".") ? pkg + cls : cls;
                Intent i = new Intent().setClassName(pkg, fullCls);
                if (i.resolveActivity(pm) != null && start(ctx, i)) return true;
            }
        }
        if (Settings.ACTION_ACCESSIBILITY_SETTINGS.equals(action)) {
            String[][] a11yTargets = {
                    {"com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"},
                    {"com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity"},
                    {"com.google.android.tv.settings", "com.google.android.tv.settings.accessibility.AccessibilityActivity"},
                    {"com.android.settings", "com.android.settings.Settings$AccessibilitySettingsActivity"},
                    {"com.android.settings", "com.android.settings.AccessibilitySettings"}
            };
            for (String[] t : a11yTargets) {
                Intent i = new Intent().setClassName(t[0], t[1]);
                if (i.resolveActivity(pm) != null && start(ctx, i)) return true;
            }
        }
        if (action != null) {
            Intent query = new Intent(action);
            try {
                java.util.List<ResolveInfo> handlers = pm.queryIntentActivities(query, 0);
                if (handlers != null) {
                    for (ResolveInfo ri : handlers) {
                        if (ri.activityInfo == null) continue;
                        String p = ri.activityInfo.packageName;
                        if (p.contains("frameworkpackagestubs")) continue;
                        Intent explicit = new Intent(action).setClassName(p, ri.activityInfo.name);
                        if (start(ctx, explicit)) return true;
                    }
                }
            } catch (Throwable ignored) {
            }
            android.content.ComponentName c = query.resolveActivity(pm);
            if (c != null && !c.getPackageName().contains("frameworkpackagestubs") && start(ctx, query)) return true;
        }
        for (String pkg : settingsPkgs) {
            Intent home = new Intent().setClassName(pkg, pkg + ".MainSettings");
            if (home.resolveActivity(pm) != null && start(ctx, home)) return true;
        }
        return start(ctx, new Intent(Settings.ACTION_SETTINGS));
    }

    public static boolean start(Context ctx, Intent intent) {
        // Anything the viewer opens from CineTV (the TV's settings and menus included) is theirs: never suppressed.
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ctx.startActivity(intent);
            if (ctx instanceof android.app.Activity) {
                ((android.app.Activity) ctx).overridePendingTransition(0, 0);
            }
            return true;
        } catch (Throwable t) {
            Log.d(TAG, "Cannot start " + intent + ": " + t.getMessage());
            return false;
        }
    }
}
