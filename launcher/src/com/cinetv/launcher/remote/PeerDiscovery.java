package com.cinetv.launcher.remote;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Several CineTV TVs on one network find each other (DNS-SD "_cinetv._tcp"), so the phone page can
 * list them and switch between them with one tap; each TV still pairs its phones on its own.
 */
public final class PeerDiscovery {

    private static final String TAG = "CineTvPeers";
    private static final String TYPE = "_cinetv._tcp.";
    private static final Map<String, String> peers = new ConcurrentHashMap<>();
    private static boolean started;

    private PeerDiscovery() {}

    public static synchronized void start(Context ctx) {
        if (started) return;
        started = true;
        try {
            final NsdManager nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
            if (nsd == null) return;
            NsdServiceInfo me = new NsdServiceInfo();
            me.setServiceName(RemotePairing.tvName(ctx));
            me.setServiceType(TYPE);
            me.setPort(TvWebRemoteServer.port());
            nsd.registerService(me, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo i) {}
                @Override public void onRegistrationFailed(NsdServiceInfo i, int e) {}
                @Override public void onServiceUnregistered(NsdServiceInfo i) {}
                @Override public void onUnregistrationFailed(NsdServiceInfo i, int e) {}
            });
            nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, new NsdManager.DiscoveryListener() {
                @Override
                public void onServiceFound(NsdServiceInfo info) {
                    nsd.resolveService(info, new NsdManager.ResolveListener() {
                        @Override public void onResolveFailed(NsdServiceInfo i, int e) {}

                        @Override
                        public void onServiceResolved(NsdServiceInfo i) {
                            if (i.getHost() == null) return;
                            String ip = i.getHost().getHostAddress();
                            if (ip == null || ip.equals(RemotePairing.localIp())) return;
                            peers.put(i.getServiceName(), "http://" + ip + ":" + i.getPort());
                        }
                    });
                }

                @Override
                public void onServiceLost(NsdServiceInfo info) {
                    peers.remove(info.getServiceName());
                }

                @Override public void onDiscoveryStarted(String t) {}
                @Override public void onDiscoveryStopped(String t) {}
                @Override public void onStartDiscoveryFailed(String t, int e) {}
                @Override public void onStopDiscoveryFailed(String t, int e) {}
            });
        } catch (Throwable t) {
            Log.w(TAG, "Discovery unavailable: " + t);
        }
    }

    /** Other CineTV TVs here: [{name, url}]. */
    public static JSONArray peers() {
        JSONArray out = new JSONArray();
        for (Map.Entry<String, String> e : peers.entrySet()) {
            try {
                out.put(new JSONObject().put("name", e.getKey()).put("url", e.getValue()));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }
}
