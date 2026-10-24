package com.cinetv.launcher.service;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.cinetv.launcher.R;
import com.cinetv.launcher.device.AvOptimizer;
import com.cinetv.launcher.device.NetworkProbe;

/**
 * Turns the measured connection and the TV's capabilities into the stream quality Nuvio should
 * prefer, expressed as a Torrentio configuration (sort, quality filter, size cap). Nothing is
 * changed silently: Nuvio shows its own install/update screen for the addon.
 */
public final class StreamProfile {

    public final int labelRes;
    /** Torrentio path segment, e.g. "sort=qualitysize|qualityfilter=..." */
    public final String torrentioConfig;

    private StreamProfile(int labelRes, String torrentioConfig) {
        this.labelRes = labelRes;
        this.torrentioConfig = torrentioConfig;
    }

    public static StreamProfile recommend(NetworkProbe.Result net, AvOptimizer.Report av) {
        double mbps = net != null && net.ok ? net.downloadMbps : 25;
        boolean uhd = av == null || av.is4kPanel();
        boolean hdr = av == null || av.hdr10 || av.hlg;
        boolean dv = av != null && av.dolbyVision;

        StringBuilder filter = new StringBuilder("scr,cam,unknown");
        if (!dv) filter.append(",dolbyvision");     // pure Dolby Vision would play with wrong colours
        if (!hdr) filter.append(",hdrall,dolbyvisionwithhdr");

        int label;
        String size;
        if (uhd && mbps >= 80) {
            label = R.string.stream_remux;           // 4K remux needs 60-80 Mbps peaks
            size = "80GB";
        } else if (uhd && mbps >= 30) {
            label = R.string.stream_4k;              // 4K WEB-DL / HEVC encodes, ~15-25 Mbps
            filter.append(",brremux");
            size = "30GB";
        } else if (mbps >= 12) {
            label = R.string.stream_1080;
            filter.append(",brremux,4k");
            size = "12GB";
        } else {
            label = R.string.stream_720;
            filter.append(",brremux,4k,1080p");
            size = "4GB";
        }
        return new StreamProfile(label, "sort=qualitysize|qualityfilter=" + filter + "|sizefilter=" + size + "|limit=8");
    }

    public String manifestUrl() {
        return "https://torrentio.strem.fun/" + torrentioConfig + "/manifest.json";
    }

    /** Opens Nuvio's own addon screen with this configuration (the user confirms there). */
    public boolean openInNuvio(Context ctx) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("stremio://torrentio.strem.fun/" + torrentioConfig + "/manifest.json"))
                .setPackage(AppLauncher.NUVIO);
        return AppLauncher.start(ctx, i);
    }
}
