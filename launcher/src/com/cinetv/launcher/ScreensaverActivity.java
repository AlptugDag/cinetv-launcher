package com.cinetv.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.cinetv.launcher.ui.CinemaScreensaverView;
import com.cinetv.launcher.ui.UiKit;

/**
 * The film-moments screensaver over any app. Apps such as Nuvio keep the screen on and draw their own
 * idle screen, so the system Dream never starts; the key service opens this instead when the TV has
 * been idle with nothing playing. Any key returns to the app underneath.
 */
public class ScreensaverActivity extends Activity {

    private CinemaScreensaverView view;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sleep = () -> {
        if (!com.cinetv.launcher.device.DeviceTweaks.isMediaPlaying(this)) com.cinetv.launcher.device.DeviceTweaks.sleepPanel(this);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        UiKit.init(this);
        view = new CinemaScreensaverView(this);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
        handler.postDelayed(sleep, 10 * 60 * 1000L);
    }

    @Override
    protected void onPause() {
        view.stop();
        handler.removeCallbacks(sleep);
        super.onPause();
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        finish();
        return true;
    }
}
