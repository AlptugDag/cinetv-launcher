package com.cinetv.launcher.device;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import com.cinetv.launcher.R;
import com.cinetv.launcher.service.AppLauncher;

import java.util.ArrayList;
import java.util.List;

/**
 * The TV's inputs, named the way people know them: an HDMI port shows the name the connected device
 * gives itself over CEC ("PlayStation 5", "Digiturk") rather than "HDMI 2", or the label the owner
 * gave it. Live TV (the tuner) comes first.
 */
public final class TvSources {

    public static final class Source {
        public String inputId;      // null for Live TV
        public String name;
        public String detail;       // the port, when the name is a device's
        public boolean connected;
    }

    private TvSources() {}

    public static List<Source> list(Context ctx) {
        List<Source> out = new ArrayList<>();
        if (com.cinetv.launcher.service.AppCatalog.liveTvShown(ctx) && AppLauncher.liveTvPackage(ctx.getPackageManager()) != null) {
            Source tv = new Source();
            tv.name = ctx.getString(R.string.live_tv);
            tv.connected = false;
            out.add(tv);
        }
        try {
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            List<TvInputInfo> all = tim.getTvInputList();
            List<TvInputInfo> ports = new ArrayList<>();
            for (TvInputInfo i : all) {
                final int t = i.getType();
                if (i.getParentId() == null && (t == TvInputInfo.TYPE_HDMI || t == TvInputInfo.TYPE_COMPOSITE
                        || t == TvInputInfo.TYPE_COMPONENT || t == TvInputInfo.TYPE_SCART || t == TvInputInfo.TYPE_VGA
                        || t == TvInputInfo.TYPE_SVIDEO || t == TvInputInfo.TYPE_DVI)) ports.add(i);
            }
            java.util.Collections.sort(ports, (a, b) -> a.getId().compareTo(b.getId()));
            for (TvInputInfo port : ports) {
                Source s = new Source();
                s.inputId = port.getId();
                final CharSequence label = port.loadLabel(ctx), custom = port.loadCustomLabel(ctx);
                String device = null;
                for (TvInputInfo child : all) {
                    if (port.getId().equals(child.getParentId())) {
                        CharSequence c = child.loadLabel(ctx);
                        if (c != null && c.length() > 0) device = c.toString();
                    }
                }
                final String portName = label != null ? label.toString() : "HDMI";
                if (device != null) {
                    s.name = device;
                    s.detail = portName;
                } else if (custom != null && custom.length() > 0 && !custom.toString().equals(portName)) {
                    s.name = custom.toString();
                    s.detail = portName;
                } else {
                    s.name = portName;
                }
                s.connected = device != null || tim.getInputState(port.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
                out.add(s);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static boolean open(Context ctx, Source s) {
        return s.inputId == null ? AppLauncher.openLiveTv(ctx) : AppLauncher.openInput(ctx, s.inputId);
    }
}
