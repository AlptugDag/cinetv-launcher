package com.cinetv.launcher;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

import com.cinetv.launcher.device.DeviceTweaks;
import com.cinetv.launcher.ui.CinemaScreensaverView;
import com.cinetv.launcher.ui.UiKit;

/**
 * The system screensaver (Daydream): the same film-moments screen the home shows when idle. After
 * ten minutes without media playing it puts the panel to sleep. The component name is kept stable
 * because the TV stores it in Settings.Secure.screensaver_components.
 */
public class ClockDream extends DreamService {

    private static final long SLEEP_AFTER_MS = 10 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private CinemaScreensaverView view;

    private final Runnable sleep = new Runnable() {
        @Override
        public void run() {
            if (DeviceTweaks.isMediaPlaying(ClockDream.this)) {
                handler.postDelayed(this, 2 * 60 * 1000L);
                return;
            }
            DeviceTweaks.sleepPanel(ClockDream.this);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        UiKit.init(this);
        view = new CinemaScreensaverView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.start();
        handler.postDelayed(sleep, SLEEP_AFTER_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacksAndMessages(null);
        if (view != null) view.stop();
        super.onDreamingStopped();
    }
}
