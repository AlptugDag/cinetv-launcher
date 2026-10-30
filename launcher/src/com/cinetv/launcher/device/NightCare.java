package com.cinetv.launcher.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.cinetv.launcher.R;
import com.cinetv.launcher.service.Prefs;

import java.util.Calendar;

/**
 * Evenings: a sleep timer that puts the TV to sleep after a while (with a minute's warning), and quiet
 * hours that keep the volume under a cap from 22:00 to 08:00. Both run in CineTV's process, which the
 * remote-button service keeps alive, so they work whatever is on screen.
 */
public final class NightCare {
    public static final int[] SLEEP_CHOICES = {0, 15, 30, 45, 60, 90, 120};
    private static final String SLEEP_AT = "sleep_timer_at";
    private static final String QUIET = "quiet_hours_on";
    private static final float QUIET_CAP = 0.30f;
    private static final int QUIET_FROM = 22, QUIET_TO = 8;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Runnable warn, sleep;
    private static boolean started;

    private NightCare() {}

    /** Called once per process: re-arms a timer set before a restart and starts quiet hours. */
    public static synchronized void start(Context ctx) {
        if (started) return;
        started = true;
        final Context app = ctx.getApplicationContext();
        arm(app);
        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                capVolume(app);
            }
        }, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                capVolume(app);
                main.postDelayed(this, 5 * 60 * 1000L);
            }
        }, 30_000L);
    }

    // ---- sleep timer ----------------------------------------------------------------------------

    /** Minutes left, 0 when no timer runs. */
    public static int sleepMinutesLeft(Context ctx) {
        long at = Prefs.get(ctx).getLong(SLEEP_AT, 0);
        long left = at - System.currentTimeMillis();
        return left > 0 ? (int) Math.ceil(left / 60000.0) : 0;
    }

    /** Puts the TV to sleep in `minutes` (0 cancels). */
    public static void setSleep(Context ctx, int minutes) {
        Context app = ctx.getApplicationContext();
        Prefs.get(app).edit().putLong(SLEEP_AT, minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0).apply();
        arm(app);
    }

    /** The next choice after the current one: off, 15, 30, ... 120, off. */
    public static int nextSleepChoice(Context ctx) {
        int left = sleepMinutesLeft(ctx);
        for (int c : SLEEP_CHOICES) if (c > left) return c;
        return 0;
    }

    private static void arm(final Context app) {
        if (warn != null) main.removeCallbacks(warn);
        if (sleep != null) main.removeCallbacks(sleep);
        final long at = Prefs.get(app).getLong(SLEEP_AT, 0);
        final long left = at - System.currentTimeMillis();
        if (at == 0 || left <= 0) return;
        warn = () -> Toast.makeText(app, R.string.sleep_warning, Toast.LENGTH_LONG).show();
        sleep = () -> {
            Prefs.get(app).edit().putLong(SLEEP_AT, 0).apply();
            DeviceTweaks.sleepPanel(app);
        };
        if (left > 60_000L) main.postDelayed(warn, left - 60_000L);
        main.postDelayed(sleep, left);
    }

    // ---- quiet hours ----------------------------------------------------------------------------

    public static final String PREF_NIGHT_VIEW_MODE = "pref_night_view_mode";
    public static final String PREF_NIGHT_VISUAL = "pref_night_visual";
    public static final String PREF_NIGHT_AUDIO = "pref_night_audio";
    public static final String PREF_NIGHT_PROMPTED_NIGHT = "pref_night_prompted_night";

    /**
     * Checks if current local time is in the night window: 23:00 to 05:00.
     */
    public static boolean isNightViewingHours() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 23 || h < 5;
    }

    /**
     * True when the user has enabled Night Viewing Mode in Settings.
     */
    public static boolean isNightViewingModeEnabled(Context ctx) {
        return Prefs.get(ctx).getBoolean(PREF_NIGHT_VIEW_MODE, false);
    }

    /**
     * Night viewing mode is active when enabled by user, with primary engagement during night hours.
     */
    public static boolean isNightViewingModeActive(Context ctx) {
        return isNightViewingModeEnabled(ctx);
    }

    public static boolean isVisualPrefEnabled(Context ctx) {
        return Prefs.get(ctx).getBoolean(PREF_NIGHT_VISUAL, true);
    }

    public static boolean isAudioPrefEnabled(Context ctx) {
        return Prefs.get(ctx).getBoolean(PREF_NIGHT_AUDIO, true);
    }

    public static boolean isVisualActive(Context ctx) {
        return isNightViewingModeEnabled(ctx) && isVisualPrefEnabled(ctx);
    }

    public static boolean isAudioActive(Context ctx) {
        return isNightViewingModeEnabled(ctx) && isAudioPrefEnabled(ctx);
    }

    public static void setVisualActive(Context ctx, boolean on) {
        Prefs.get(ctx).edit().putBoolean(PREF_NIGHT_VISUAL, on).apply();
    }

    public static void setAudioActive(Context ctx, boolean on) {
        Prefs.get(ctx).edit().putBoolean(PREF_NIGHT_AUDIO, on).apply();
        capVolume(ctx.getApplicationContext());
    }

    public static void setNightViewingMode(Context ctx, boolean on) {
        Prefs.get(ctx).edit().putBoolean(PREF_NIGHT_VIEW_MODE, on).apply();
        if (on) {
            setQuietHours(ctx, true);
        }
    }

    /**
     * Identifier for the current night (from 23:00 to next day's 05:00).
     */
    public static String currentNightKey() {
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        if (h < 5) {
            cal.add(Calendar.DATE, -1);
        }
        return cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
    }

    public static boolean shouldPromptNightViewingMode(Context ctx) {
        // Canceled per user request: night viewing prompt is disabled
        return false;
    }

    public static void markNightPromptDismissed(Context ctx) {
        Prefs.get(ctx).edit().putString(PREF_NIGHT_PROMPTED_NIGHT, currentNightKey()).apply();
    }

    public static boolean quietHours(Context ctx) {
        return Prefs.get(ctx).getBoolean(QUIET, false);
    }

    public static void setQuietHours(Context ctx, boolean on) {
        Prefs.get(ctx).edit().putBoolean(QUIET, on).apply();
        capVolume(ctx.getApplicationContext());
    }

    private static boolean isNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= QUIET_FROM || h < QUIET_TO;
    }

    private static void capVolume(Context app) {
        if ((!quietHours(app) && !isAudioActive(app)) || !isNight()) return;
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cap = Math.max(1, Math.round(max * QUIET_CAP));
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
        } catch (Throwable ignored) {
        }
    }
}
