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

    private static AirPodsLink instance;

    public static synchronized void ensureStarted(Context ctx) {
        if (instance == null) {
            instance = new AirPodsLink(ctx);
        }
        instance.start();
    }

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothA2dp a2dp;
    private boolean running;

    private static volatile long disconnectedAt = 0L;
    private static final long RECONNECT_COOLDOWN_MS = 25 * 60 * 1000L; // 25 min cooldown: never steal back from phone!

    public static void clearDisconnectCooldown() {
        disconnectedAt = 0L;
    }

    public static void markDisconnected() {
        disconnectedAt = System.currentTimeMillis();
    }

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
            int state = i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                if (d != null && isHeadphones(d)) {
                    disconnectedAt = 0L;
                    Toast.makeText(ctx, ctx.getString(R.string.airpods_connected, name(d)), Toast.LENGTH_SHORT).show();
                    applyHeadphoneSpatialAudio(ctx, true);
                }
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectedAt = System.currentTimeMillis();
                applyHeadphoneSpatialAudio(ctx, false);
            }
        }
    };

    public AirPodsLink(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void start() {
        if (running) return;
        if (android.os.Build.VERSION.SDK_INT >= 31 && ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "BLUETOOTH_CONNECT permission not granted, skipping AirPodsLink");
            return;
        }
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) return;
        running = true;
        try {
            bt.getProfileProxy(ctx, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    a2dp = (BluetoothA2dp) proxy;
                    try {
                        if (a2dp != null) {
                            for (BluetoothDevice d : a2dp.getConnectedDevices()) {
                                if (isHeadphones(d)) {
                                    applyHeadphoneSpatialAudio(ctx, true);
                                    break;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    a2dp = null;
                }
            }, BluetoothProfile.A2DP);
            IntentFilter filter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(connected, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ctx.registerReceiver(connected, filter);
            }
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
        if (!com.cinetv.launcher.service.Prefs.get(ctx).getBoolean(com.cinetv.launcher.service.Prefs.AIRPODS_AUTO_CONNECT, true)) return;
        if (System.currentTimeMillis() - disconnectedAt < RECONNECT_COOLDOWN_MS) {
            // Respect manual disconnect: TV will NOT steal connection back while user is on phone/Mac
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 31 && ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
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

    public static boolean isHeadphoneConnected(Context ctx) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.media.AudioDeviceInfo[] devices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS);
                for (android.media.AudioDeviceInfo d : devices) {
                    int t = d.getType();
                    if (t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Activates or disables TV's internal virtual surround and binaural 3D stage for headphones.
     * When enabled, multichannel 5.1/7.1 film tracks in Nuvio or Nova Player are spatialized
     * across stereo headphones instead of flat stereo downmixing.
     */
    public static void applyHeadphoneSpatialAudio(Context ctx, boolean enable) {
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            int val = enable ? 1 : 0;
            android.provider.Settings.Global.putInt(cr, "sound_advanced_surround_virtualizer", val);
            android.provider.Settings.Global.putInt(cr, "sound_surround_virtualizer", val);
            android.provider.Settings.Global.putInt(cr, "virtual_surround", val);
            android.provider.Settings.Global.putInt(cr, "sound_dts_virtual_x", val);
            Log.i(TAG, "Headphone spatial binaural virtualizer set to " + enable);
        } catch (Throwable t) {
            Log.w(TAG, "Failed setting spatial audio virtualizer: " + t);
        }
    }
}
