package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

/**
 * Routes official YouTube intents, voice assistant queries, and deep links directly to TizenTube Cobalt.
 */
public class YouTubeBridgeActivity extends Activity {
    private static final String[] TIZENTUBE_PACKAGES = new String[] {
        "io.gh.reisxd.tizentube.cobalt",
        "com.reisxd.tizentube",
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv.beta",
        "com.liskovsoft.smarttubetv"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent incoming = getIntent();
        Uri data = incoming != null ? incoming.getData() : null;
        PackageManager pm = getPackageManager();

        for (String targetPkg : TIZENTUBE_PACKAGES) {
            try {
                if (data != null) {
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW, data);
                    viewIntent.setPackage(targetPkg);
                    viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(viewIntent);
                    finish();
                    return;
                }
                Intent launch = pm.getLeanbackLaunchIntentForPackage(targetPkg);
                if (launch == null) {
                    launch = pm.getLaunchIntentForPackage(targetPkg);
                }
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    finish();
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        finish();
    }
}
