package com.cinetv.launcher.device;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import com.cinetv.launcher.remote.LocalAdb;
import com.cinetv.launcher.service.Prefs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CineTV as the TV's only Home, by the viewer's choice and always reversible.
 *
 * <ul>
 *   <li>{@link #requestDefault}: Android 10+ asks the viewer with the system's own "default home app"
 *       dialog (RoleManager ROLE_HOME); older TVs open the system's Home settings.</li>
 *   <li>{@link #disableOthers}: makes CineTV the Home through the TV's own adb (Home role and home activity).
 *       The other launchers are no longer switched off: when CineTV was removed from the TV's own Settings,
 *       a launcher switched off stayed off and the TV fell to a pink screen with no Home at all.</li>
 *   <li>{@link #restoreOthers}: switches exactly those back on and clears CineTV as preferred, so the TV's
 *       launcher is Home again. "Remove CineTV" runs it too.</li>
 * </ul>
 */
public final class HomeLaunchers {
    private static final String JOURNAL = "home_disabled_launchers";
    private static final String VERIFIED_VER = "stock_launcher_verified_ver";
    public static final int REQ_ROLE = 4711;
    /** Settings apps answer HOME as a fallback while booting: never touched. */
    private static final String[] NEVER = {"com.android.settings", "com.android.tv.settings", "com.android.provision",
            "com.google.android.tungsten.setupwraith", "com.android.managedprovisioning"};

    /** Known Android TV, Google TV, and OEM default launchers across firmware builds (Android 6–14). */
    public static final String[] STOCK_LAUNCHERS = {
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.tvrecommendations",
            "com.android.tv.launcher",
            "com.android.launcher3",
            "com.android.launcher2",
            "com.android.launcher",
            "com.google.android.launcher",
            "com.droidlogic.tvlauncher",
            "com.droidlogic.mboxlauncher",
            "com.droidlogic.Launcher",
            "com.amlogic.tvlauncher",
            "com.amlogic.mboxlauncher",
            "com.ugoos.launcher",
            "com.UGOOS.launcher",
            "com.ampere.launcher",
            "com.mstar.tv.launcher",
            "com.rockchip.mediacenter",
            "com.mitv.tvhome",
            "com.xiaomi.mitv.tvhome",
            "com.tcl.cyberui",
            "com.sony.dtv.osat.launcher",
            "com.hisense.vidaa.launcher",
            "com.jamdeo.tv.mediacenter",
            "org.droidtv.leanback",
            "org.droidtv.eum",
            "com.arcelik.home",
            "com.vestel.launcher"
    };

    private HomeLaunchers() {}

    public static void requestDefault(Activity a) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_ROLE);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            a.startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
            // no system page on this TV: HomeGuard's adb path keeps CineTV as Home
        }
    }

    /** The other apps that answer the Home button. Uses MATCH_ALL so Android 6 firmwares never filter to only the preferred Home. */
    public static List<String> others(Context ctx) {
        Set<String> out = new LinkedHashSet<>();
        if (ctx == null) return new ArrayList<>(out);
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = ctx.getPackageManager();
            int[] flagSets = new int[]{PackageManager.MATCH_ALL, 0};
            for (int flags : flagSets) {
                List<ResolveInfo> list = pm.queryIntentActivities(home, flags);
                if (list == null) continue;
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                    String p = ri.activityInfo.packageName;
                    if (p.equals(ctx.getPackageName()) || isNever(p) || ri.activityInfo.name.endsWith("FallbackHome")) continue;
                    if (!ri.activityInfo.enabled || !isPackageEnabled(ctx, p)) continue;
                    out.add(p);
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(out);
    }

    /** Discovers all candidate stock launcher packages on this TV, including disabled or uninstalled-for-user-0 packages. */
    public static List<String> discoverStockPackages(Context ctx) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : STOCK_LAUNCHERS) out.add(s);
        if (ctx != null) {
            out.addAll(journal(ctx));
            String self = ctx.getPackageName();
            try {
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                int disabledFlag = android.os.Build.VERSION.SDK_INT >= 24
                        ? PackageManager.MATCH_DISABLED_COMPONENTS : PackageManager.GET_DISABLED_COMPONENTS;
                int flags = PackageManager.MATCH_ALL | disabledFlag | 0x00002000; // MATCH_UNINSTALLED_PACKAGES
                List<ResolveInfo> all = ctx.getPackageManager().queryIntentActivities(home, flags);
                if (all != null) {
                    for (ResolveInfo ri : all) {
                        if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                        String p = ri.activityInfo.packageName;
                        if (self.equals(p) || isNever(p) || ri.activityInfo.name.endsWith("FallbackHome")) continue;
                        out.add(p);
                    }
                }
            } catch (Throwable ignored) {
            }
            out.remove(self);
        }
        return new ArrayList<>(out);
    }

    /**
     * Shell script that checks firmware packages (`pm list packages -u`), re-installs any stock launcher
     * that was uninstalled/deleted for user 0 (`cmd package install-existing` or `pm path -u` APK install),
     * and re-enables/unhides any stock launcher that was disabled. Compatible with Android 6+ (API 23+).
     */
    public static String stockRestoreCommands(Context ctx) {
        List<String> candidates = discoverStockPackages(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("all_u=$(pm list packages -u 2>/dev/null | tr -d '\\r'); ");
        sb.append("inst=$(pm list packages 2>/dev/null | tr -d '\\r'); ");
        sb.append("dis=$(pm list packages -d 2>/dev/null | tr -d '\\r'); ");
        sb.append("extra=''; ");
        sb.append("if [ -f /sdcard/cinetv-debloat.txt ]; then ");
        sb.append("extra=$(awk '{print $2}' /sdcard/cinetv-debloat.txt 2>/dev/null | grep -iE 'launcher|tvhome|leanback|cyberui|mbox' | tr '\\n' ' '); ");
        sb.append("fi; ");
        sb.append("fw_launchers=$(printf '%s\\n' \"$all_u\" | sed 's/^package://' | grep -iE 'tvlauncher|mboxlauncher|launcherx|leanbacklauncher|tv\\.launcher|launcher2|launcher3|tvhome|cyberui|vidaa\\.launcher|osat\\.launcher|ugoos.*launcher|droidlogic.*launcher|amlogic.*launcher' | grep -v 'com\\.cinetv\\.launcher' | tr '\\n' ' '); ");
        sb.append("for p in ");
        for (String p : candidates) {
            if (p != null && p.matches("[a-zA-Z0-9._]+")) {
                sb.append(p).append(' ');
            }
        }
        sb.append("$extra $fw_launchers; do ");
        sb.append("[ -n \"$p\" ] || continue; ");
        sb.append("case \"$all_u\" in *\"package:$p\"*) ");
        sb.append("case \"$inst\" in *\"package:$p\"*) ;; *) ");
        sb.append("cmd package install-existing --user 0 \"$p\" >/dev/null 2>&1 || pm install-existing --user 0 \"$p\" >/dev/null 2>&1 || { ");
        sb.append("apk=$(pm path -u \"$p\" 2>/dev/null | head -n 1 | cut -d: -f2); ");
        sb.append("[ -n \"$apk\" ] && (pm install -r --user 0 \"$apk\" >/dev/null 2>&1 || pm install -r \"$apk\" >/dev/null 2>&1); }; ");
        sb.append(";; esac; ");
        sb.append("pm enable --user 0 \"$p\" >/dev/null 2>&1 || pm enable \"$p\" >/dev/null 2>&1; ");
        sb.append("pm unhide --user 0 \"$p\" >/dev/null 2>&1 || pm unhide \"$p\" >/dev/null 2>&1; ");
        sb.append("pm default-state --user 0 \"$p\" >/dev/null 2>&1; ");
        sb.append(";; esac; ");
        sb.append("done; ");
        return sb.toString();
    }

    private static boolean isNever(String p) {
        for (String n : NEVER) if (n.equals(p)) return true;
        return false;
    }

    public static boolean hasDisabled(Context ctx) {
        return !Prefs.get(ctx).getString(JOURNAL, "").isEmpty();
    }

    /** Records the TV's original default Home launcher package once before CineTV takes over. */
    public static void recordOriginalHomeIfNeeded(Context ctx) {
        if (ctx == null) return;
        android.content.SharedPreferences p = Prefs.get(ctx);
        if (!p.getString("original_home_pkg", "").isEmpty()) return;
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = ctx.getPackageManager().resolveActivity(home, 0);
            if (r != null && r.activityInfo != null) {
                String pkg = r.activityInfo.packageName;
                if (pkg != null && !pkg.equals(ctx.getPackageName()) && !isNever(pkg) && !pkg.equals("android")) {
                    p.edit().putString("original_home_pkg", pkg).apply();
                    return;
                }
            }
            List<String> avail = others(ctx);
            String best = pickBestStockPackage(avail, journal(ctx));
            if (best != null && !best.isEmpty()) {
                p.edit().putString("original_home_pkg", best).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Blocking (adb). CineTV takes the Home role and the home activity, and disables competing stock launchers
     * (recording them in the journal so restoreOthers / Remove CineTV re-enables them cleanly).
     * Returns the count of disabled competing launchers (or 0 if already disabled), or -1 when adb is unavailable.
     */
    public static int disableOthers(Context ctx) {
        if (ctx == null || Uninstaller.isRemoving(ctx)) return -1;
        recordOriginalHomeIfNeeded(ctx);
        List<String> competing = others(ctx);
        Set<String> recorded = new LinkedHashSet<>(journal(ctx));
        StringBuilder cmd = new StringBuilder(homeCommand(ctx));
        int count = 0;
        for (String p : competing) {
            if (p != null && p.matches("[a-zA-Z0-9._]+") && !p.equals(ctx.getPackageName()) && !isNever(p)) {
                recorded.add(p);
                cmd.append("pm disable-user --user 0 ").append(p).append(" >/dev/null 2>&1 || pm hide --user 0 ").append(p).append(" >/dev/null 2>&1; ");
                count++;
            }
        }
        cmd.append(homeCommand(ctx));
        if (!LocalAdb.shell(ctx, cmd.toString(), 15_000)) return -1;
        if (!recorded.isEmpty()) {
            StringBuilder j = new StringBuilder();
            for (String s : recorded) {
                if (j.length() > 0) j.append(',');
                j.append(s);
            }
            Prefs.get(ctx).edit().putString(JOURNAL, j.toString()).commit();
        }
        return count;
    }

    private static String homeCommand(Context ctx) {
        String pkg = ctx.getPackageName();
        return "cmd role add-role-holder --user 0 --flags 1 android.app.role.HOME " + pkg + " 2>/dev/null; "
                + "cmd role add-role-holder --user 0 android.app.role.HOME " + pkg + " 2>/dev/null; "
                + "cmd package set-home-activity --user 0 " + pkg + "/.MainActivity 2>/dev/null; "
                + "cmd package set-home-activity --user current " + pkg + "/.MainActivity 2>/dev/null; "
                + "cmd package set-home-activity " + pkg + "/.MainActivity 2>/dev/null; ";
    }

    /**
     * Ensures the original stock launcher package is recorded and CineTV is locked as the active default Home
     * without re-enabling competing stock launchers during normal use (which would cause Android TV / Google TV
     * to revert to the stock launcher). Competing stock launchers are restored only in {@link #restoreOthers}.
     */
    public static boolean ensureStockLaunchersReady(Context ctx) {
        if (ctx == null || Uninstaller.isRemoving(ctx)) return false;
        recordOriginalHomeIfNeeded(ctx);
        return disableOthers(ctx) >= 0;
    }

    /** Blocking (adb): re-enables the stock launchers while keeping CineTV as the preferred Home when explicitly requested from Settings. */
    public static boolean reenableKeepingHome(Context ctx) {
        if (ctx == null) return false;
        String cmd = stockRestoreCommands(ctx) + homeCommand(ctx);
        boolean ok = LocalAdb.shell(ctx, cmd, 15_000);
        if (ok) {
            Prefs.get(ctx).edit().remove(JOURNAL).commit();
        }
        return ok;
    }

    /**
     * Blocking (adb): re-installs and re-enables all stock/default launchers on the TV, removes CineTV
     * from the Home role, and hands the default Home role and home-activity back to the TV's stock launcher.
     */
    public static boolean restoreOthers(Context ctx) {
        List<String> list = journal(ctx);
        String origHome = ctx != null ? Prefs.get(ctx).getString("original_home_pkg", "") : "";
        if (!origHome.isEmpty() && !list.contains(origHome)) {
            list.add(0, origHome);
        }
        String pkg = ctx.getPackageName();
        StringBuilder cmd = new StringBuilder();
        cmd.append(stockRestoreCommands(ctx));
        for (String p : list) {
            if (p != null && p.matches("[a-zA-Z0-9._]+")) {
                cmd.append("cmd package install-existing --user 0 ").append(p).append(" 2>/dev/null; ");
                cmd.append("pm enable --user 0 ").append(p).append(" 2>/dev/null || pm enable ").append(p).append(" 2>/dev/null; ");
            }
        }
        cmd.append("pm clear-package-preferred-activities ").append(pkg).append(" 2>/dev/null; ");
        cmd.append("cmd role remove-role-holder --user 0 android.app.role.HOME ").append(pkg).append(" 2>/dev/null; ");
        cmd.append("for s in ");
        if (origHome != null && origHome.matches("[a-zA-Z0-9._]+")) {
            cmd.append(origHome).append(' ');
        }
        for (String s : STOCK_LAUNCHERS) {
            cmd.append(s).append(' ');
        }
        cmd.append("; do ");
        cmd.append("pm enable --user 0 \"$s\" 2>/dev/null || pm enable \"$s\" 2>/dev/null; ");
        cmd.append("h=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME \"$s\" 2>/dev/null | grep '/' | tail -1 | tr -d '\\r '); ");
        cmd.append("if [ -n \"$h\" ]; then ");
        cmd.append("cmd role add-role-holder --user 0 android.app.role.HOME \"$s\" 2>/dev/null; ");
        cmd.append("cmd package set-home-activity --user 0 \"$h\" 2>/dev/null; ");
        cmd.append("cmd package set-home-activity --user current \"$h\" 2>/dev/null; ");
        cmd.append("cmd package set-home-activity \"$h\" 2>/dev/null; ");
        cmd.append("break; ");
        cmd.append("fi; done; ");

        boolean ok = LocalAdb.shell(ctx, cmd.toString(), 15_000);
        if (!ok && others(ctx).isEmpty()) return false;

        List<String> available = others(ctx);
        if (!available.isEmpty()) {
            String stock = pickBestStockPackage(available, list);
            if (stock != null) {
                String handoff = "pm enable --user 0 " + stock + " 2>/dev/null || pm enable " + stock + " 2>/dev/null; "
                        + "cmd role add-role-holder --user 0 android.app.role.HOME " + stock + " 2>/dev/null; "
                        + "h=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME "
                        + stock + " 2>/dev/null | grep '/' | tail -1 | tr -d '\\r '); "
                        + "[ -n \"$h\" ] && cmd package set-home-activity --user 0 \"$h\" 2>/dev/null";
                LocalAdb.shell(ctx, handoff, 6000);
            }
            Prefs.get(ctx).edit().remove(JOURNAL).commit();
            return true;
        }
        return false;
    }

    /** Resolves and launches the best available non-CineTV stock Home activity (even on Android 6 where cmd role does not exist). */
    public static boolean startStockHome(Context ctx) {
        if (ctx == null) return false;
        try {
            PackageManager pm = ctx.getPackageManager();
            List<String> avail = others(ctx);
            String stock = pickBestStockPackage(avail, journal(ctx));
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = pm.queryIntentActivities(home, PackageManager.MATCH_ALL);
            if (list != null) {
                ResolveInfo chosen = null;
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                    String p = ri.activityInfo.packageName;
                    if (p.equals(ctx.getPackageName())) continue;
                    if (stock != null && stock.equals(p)) {
                        chosen = ri;
                        break;
                    }
                    if (chosen == null && !isNever(p)) {
                        chosen = ri;
                    }
                }
                if (chosen != null && chosen.activityInfo != null) {
                    Intent launch = new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setClassName(chosen.activityInfo.packageName, chosen.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    ctx.startActivity(launch);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Picks the primary stock launcher package from the available Home packages. */
    public static String pickBestStockPackage(List<String> available, List<String> journaled) {
        if (available == null || available.isEmpty()) return null;
        if (journaled != null) {
            for (String j : journaled) {
                if (available.contains(j)) return j;
            }
        }
        for (String s : STOCK_LAUNCHERS) {
            if (available.contains(s)) return s;
        }
        return available.get(0);
    }

    private static boolean isPackageEnabled(Context ctx, String pkg) {
        try {
            int state = ctx.getPackageManager().getApplicationEnabledSetting(pkg);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    || state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static List<String> journal(Context ctx) {
        List<String> out = new ArrayList<>();
        if (ctx == null) return out;
        for (String p : Prefs.get(ctx).getString(JOURNAL, "").split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }
}
