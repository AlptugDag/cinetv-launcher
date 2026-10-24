package com.cinetv.launcher.device;

import com.cinetv.launcher.service.Http;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;

/**
 * Measures the TV's own connection against Cloudflare's speed-test endpoints (speed.cloudflare.com):
 * round-trip latency and download throughput. Runs once at first setup (and on demand) because a
 * full test moves ~25 MB.
 */
public final class NetworkProbe {

    public static final class Result {
        public double downloadMbps;
        public int latencyMs;
        public int jitterMs;
        public boolean ok;
    }

    private static final String BASE = "https://speed.cloudflare.com/__down?bytes=";

    private NetworkProbe() {}

    /** Blocking, at most ~12 s. */
    public static Result run() {
        Result r = new Result();
        try {
            long[] pings = new long[6];
            for (int i = 0; i < pings.length; i++) {
                long t0 = System.nanoTime();
                HttpURLConnection c = Http.open(BASE + "0", null, 4000);
                c.getResponseCode();
                c.disconnect();
                pings[i] = (System.nanoTime() - t0) / 1_000_000L;
            }
            // The first request pays for DNS and TLS; ignore it.
            long[] warm = Arrays.copyOfRange(pings, 1, pings.length);
            Arrays.sort(warm);
            r.latencyMs = (int) warm[warm.length / 2];
            r.jitterMs = (int) (warm[warm.length - 1] - warm[0]);

            // Download in two parallel streams for up to 8 s, which saturates typical home links.
            final long deadline = System.nanoTime() + 8_000_000_000L;
            final long[] bytes = new long[2];
            Thread[] workers = new Thread[2];
            final long start = System.nanoTime();
            for (int w = 0; w < workers.length; w++) {
                final int slot = w;
                workers[w] = new Thread(() -> {
                    try {
                        HttpURLConnection c = Http.open(BASE + 25_000_000, null, 9000);
                        try (InputStream in = c.getInputStream()) {
                            byte[] buf = new byte[64 * 1024];
                            int n;
                            while ((n = in.read(buf)) > 0 && System.nanoTime() < deadline) bytes[slot] += n;
                        } finally {
                            c.disconnect();
                        }
                    } catch (Throwable ignored) {
                    }
                });
                workers[w].start();
            }
            for (Thread t : workers) t.join(10_000);
            double seconds = Math.max(0.2, (System.nanoTime() - start) / 1e9);
            r.downloadMbps = (bytes[0] + bytes[1]) * 8 / 1e6 / seconds;
            r.ok = r.downloadMbps > 0;
        } catch (Throwable ignored) {
        }
        return r;
    }
}
