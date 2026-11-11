package com.cinetv.launcher.device;

import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A field profiler for TVs where no debugger can attach: while /data/local/tmp/cinetv_profile exists (made by
 * adb, so never on a viewer's TV by accident), the main thread's stack is sampled every 20 ms for the given time
 * and the busiest app methods are written to the log under "CineTvSampler". Costs nothing otherwise.
 */
public final class MainThreadSampler {
    private static final String TAG = "CineTvSampler";

    private MainThreadSampler() {}

    public static void maybeStart(final String label, final long durationMs) {
        if (!new File("/data/local/tmp/cinetv_profile").exists()) return;
        final Thread main = Looper.getMainLooper().getThread();
        new Thread(() -> {
            Map<String, Integer> self = new HashMap<>(), inclusive = new HashMap<>();
            int samples = 0;
            long end = System.currentTimeMillis() + durationMs;
            while (System.currentTimeMillis() < end) {
                StackTraceElement[] st = main.getStackTrace();
                samples++;
                boolean idle = st.length > 0 && st[0].getMethodName().equals("nativePollOnce");
                if (!idle) {
                    // the first CineTV frame from the top: where our own code spends the time
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    boolean first = true;
                    for (StackTraceElement e : st) {
                        String k = e.getClassName() + "." + e.getMethodName();
                        if (platform(e.getClassName())) continue;
                        if (first) {
                            self.merge(k + ":" + e.getLineNumber() + " <- " + (st[0].getClassName() + "." + st[0].getMethodName()), 1, Integer::sum);
                            first = false;
                        }
                        if (seen.add(k)) inclusive.merge(k, 1, Integer::sum);
                    }
                    if (first) self.merge("(framework) " + st[0].getClassName() + "." + st[0].getMethodName(), 1, Integer::sum);
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    return;
                }
            }
            Log.i(TAG, label + ": " + samples + " samples");
            dump("top", self);
            dump("incl", inclusive);
        }, "cinetv-sampler").start();
    }

    /** The release build is obfuscated: anything that is not the platform's is ours (mapped back with mapping.txt). */
    private static boolean platform(String c) {
        return c.startsWith("android.") || c.startsWith("java.") || c.startsWith("javax.") || c.startsWith("dalvik.")
                || c.startsWith("com.android.") || c.startsWith("androidx.") || c.startsWith("libcore.") || c.startsWith("sun.")
                || c.startsWith("org.json.") || c.startsWith("okhttp3.") || c.startsWith("okio.");
    }

    private static void dump(String kind, Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < l.size() && i < 30; i++) Log.i(TAG, kind + " " + l.get(i).getValue() + " " + l.get(i).getKey());
    }
}
