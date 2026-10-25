package com.cinetv.launcher.device;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.cinetv.launcher.R;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * AirPods (and other paired headphones) connect to the TV by themselves: while the screen is on and
 * a paired pair is not connected, the TV asks for it every half minute. Opening the case near the TV
 * is enough; audio moves to them at once and back to the TV when they go into the case.
 *
 * Spatial audio / Dolby Atmos on AirPods is an Apple-device feature (head tracking runs on the
 * iPhone/Apple TV); on Android they play standard stereo AAC.
 */
public final class AirPodsLink {

    private static final String TAG = "CineTvAirPods";
    private static final long EVERY_MS = 30_000L;

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothA2dp a2dp;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                attempt();
            } catch (Throwable t) {
                Log.w(TAG, "attempt failed: " + t);
            }
            handler.postDelayed(this, EVERY_MS);
        }
    };

    private final BroadcastReceiver connected = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED) return;
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (d != null && isHeadphones(d)) {
                Toast.makeText(ctx, ctx.getString(R.string.airpods_connected, name(d)), Toast.LENGTH_SHORT).show();
            }
        }
    };

    public AirPodsLink(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void start() {
        if (running) return;
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) return;
        running = true;
        try {
            bt.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    a2dp = (BluetoothA2dp) proxy;
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    a2dp = null;
                }
            }, BluetoothProfile.A2DP);
            ctx.registerReceiver(connected, new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED));
        } catch (Throwable t) {
            Log.w(TAG, "Bluetooth unavailable: " + t);
        }
        handler.postDelayed(tick, 5000);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        try {
            ctx.unregisterReceiver(connected);
        } catch (Throwable ignored) {
        }
    }

    private void attempt() throws Exception {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (bt == null || !bt.isEnabled() || a2dp == null || pm == null || !pm.isInteractive()) return;
        // Something is already playing over Bluetooth (these AirPods, a soundbar...): leave it alone.
        if (!a2dp.getConnectedDevices().isEmpty()) return;
        for (BluetoothDevice d : bt.getBondedDevices()) {
            if (!isHeadphones(d)) continue;
            if (a2dp.getConnectionState(d) != BluetoothProfile.STATE_DISCONNECTED) continue;
            // BluetoothA2dp.connect is not public API but is open to apps on Android TV 9-11.
            Method connect = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
            Object ok = connect.invoke(a2dp, d);
            Log.i(TAG, "connect " + name(d) + ": " + ok);
            return;
        }
    }

    static boolean isHeadphones(BluetoothDevice d) {
        String n = name(d).toLowerCase(Locale.ROOT);
        if (n.contains("airpods") || n.contains("beats") || n.contains("buds")) return true;
        BluetoothClass c = d.getBluetoothClass();
        if (c == null) return false;
        int dc = c.getDeviceClass();
        return dc == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES || dc == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET;
    }

    private static String name(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null ? "" : n;
        } catch (Throwable t) {
            return "";
        }
    }
}
