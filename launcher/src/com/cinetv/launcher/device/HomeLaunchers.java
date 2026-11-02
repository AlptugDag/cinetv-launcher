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
 *   <li>{@link #disableOthers}: switches the other home apps off with {@code pm disable-user} through the
 *       TV's own adb (the one-time network-debugging step from setup). Every package is written to a
 *       journal first; nothing is uninstalled.</li>
 *   <li>{@link #restoreOthers}: switches exactly those back on and clears CineTV as preferred, so the TV's
 *       launcher is Home again. "Remove CineTV" runs it too.</li>
 * </ul>
 */
public final class HomeLaunchers {
    private static final String JOURNAL = "home_disabled_launchers";
    public static final int REQ_ROLE = 4711;
    /** Settings apps answer HOME as a fallback while booting: never touched. */
    private static final String[] NEVER = {"com.android.settings", "com.android.tv.settings", "com.android.provision",
            "com.google.android.tungsten.setupwraith", "com.android.managedprovisioning"};

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

    /** The other apps that answer the Home button. */
    public static List<String> others(Context ctx) {
        Set<String> out = new LinkedHashSet<>();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : ctx.getPackageManager().queryIntentActivities(home, 0)) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (p.equals(ctx.getPackageName()) || isNever(p) || ri.activityInfo.name.endsWith("FallbackHome")) continue;
            if (!ri.activityInfo.enabled || !isPackageEnabled(ctx, p)) continue;
            out.add(p);
        }
        return new ArrayList<>(out);
    }

    private static boolean isNever(String p) {
        for (String n : NEVER) if (n.equals(p)) return true;
        return false;
    }

    public static boolean hasDisabled(Context ctx) {
        return !Prefs.get(ctx).getString(JOURNAL, "").isEmpty();
    }

    /** Blocking (adb). Returns how many launchers were switched off; -1 when the TV's adb is not available. */
    public static int disableOthers(Context ctx) {
        List<String> list = others(ctx);
        if (list.isEmpty()) return 0;
        Set<String> journal = new LinkedHashSet<>(journal(ctx));
        journal.addAll(list);
        Prefs.get(ctx).edit().putString(JOURNAL, android.text.TextUtils.join(",", journal)).commit();
        StringBuilder cmd = new StringBuilder();
        for (String p : list) cmd.append("pm disable-user --user 0 ").append(p).append(" 2>/dev/null; ");
        String pkg = ctx.getPackageName();
        cmd.append("cmd package set-home-activity --user 0 ").append(pkg).append("/.MainActivity 2>/dev/null");
        if (!LocalAdb.shell(ctx, cmd.toString(), 8000)) return -1;
        int off = 0;
        for (String p : list) {
            try {
                if (!ctx.getPackageManager().getApplicationInfo(p, 0).enabled) off++;
            } catch (Throwable ignored) {
            }
        }
        return off;
    }

    /** Blocking (adb): the launchers CineTV switched off come back, and the TV's own Home is chosen again. */
    public static boolean restoreOthers(Context ctx) {
        List<String> list = journal(ctx);
        StringBuilder cmd = new StringBuilder();
        for (String p : list) cmd.append("pm enable ").append(p).append(" 2>/dev/null; ");
        cmd.append("for l in com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher; do pm enable $l 2>/dev/null; done; ");
        cmd.append("pm clear-package-preferred-activities ").append(ctx.getPackageName()).append(" 2>/dev/null; ");
        // the TV's own launcher Home again: CineTV gives up the Home role, the first launcher it switched off takes it
        cmd.append("cmd role remove-role-holder --user 0 android.app.role.HOME ").append(ctx.getPackageName()).append(" 2>/dev/null; ");
        String stock = list.isEmpty() ? null : list.get(0);
        for (String l : new String[]{"com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher"}) {
            if (stock == null) try { ctx.getPackageManager().getPackageInfo(l, 0); stock = l; } catch (Throwable ignored) {}
        }
        if (stock != null) {
            cmd.append("cmd role add-role-holder --user 0 android.app.role.HOME ").append(stock).append(" 2>/dev/null; ");
            cmd.append("h=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME ")
                    .append(stock).append(" 2>/dev/null | tail -1); [ -n \"$h\" ] && cmd package set-home-activity --user 0 \"$h\" 2>/dev/null");
        }
        boolean ok = LocalAdb.shell(ctx, cmd.toString(), 8000);
        if (!ok) return false;
        for (String p : list) if (!isPackageEnabled(ctx, p)) return false;
        Prefs.get(ctx).edit().remove(JOURNAL).commit();
        return true;
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

    private static List<String> journal(Context ctx) {
        List<String> out = new ArrayList<>();
        for (String p : Prefs.get(ctx).getString(JOURNAL, "").split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }
}
