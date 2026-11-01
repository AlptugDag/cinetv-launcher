package com.cinetv.launcher.remote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.R;
import com.cinetv.launcher.ui.Controls;
import com.cinetv.launcher.ui.UiKit;

/**
 * "Allow this phone?" on top of whatever is on screen: Nuvio, the player, a game, live TV. The question
 * used to live inside CineTV's home screen, so when CineTV was not in front (or Android refused to bring
 * it forward) the phone waited for a question the TV never showed. Main thread only.
 */
public final class PairingPrompt {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View shown;
    private static WindowManager shownIn;
    private static Runnable lapse;

    private PairingPrompt() {}

    /** False when no overlay is possible on this TV (no overlay permission and no key service): the caller asks in CineTV instead. */
    public static boolean show(Context ctx, String device, final Runnable allow, final Runnable deny) {
        dismiss();
        com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
        boolean appOverlay = android.provider.Settings.canDrawOverlays(ctx);
        if (!appOverlay && svc == null) return false;
        Context host = appOverlay ? ctx.getApplicationContext() : svc;
        UiKit.init(host);
        final WindowManager wm = (WindowManager) host.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return false;

        final boolean[] answered = {false};
        FrameLayout root = new FrameLayout(host) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP && !answered[0]) {
                        answered[0] = true;
                        deny.run();
                        dismiss();
                    }
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        root.setBackgroundColor(Color.argb(150, 0, 0, 0));

        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiKit.px(44), UiKit.px(38), UiKit.px(44), UiKit.px(38));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(240, 16, 18, 26));
        bg.setCornerRadius(UiKit.pxf(32));
        bg.setStroke(UiKit.px(1), Color.argb(70, 255, 255, 255));
        box.setBackground(bg);

        TextView title = UiKit.bold(host, 30, UiKit.INK);
        title.setText(host.getString(R.string.pair_ask_title, device));
        box.addView(title);
        TextView body = UiKit.label(host, 20, UiKit.INK_DIM);
        body.setText(R.string.pair_ask_body);
        body.setPadding(0, UiKit.px(10), 0, UiKit.px(26));
        box.addView(body, new LinearLayout.LayoutParams(UiKit.px(640), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(host);
        final View yes = Controls.button(host, host.getString(R.string.pair_allow), v -> {
            if (answered[0]) return;
            answered[0] = true;
            allow.run();
            dismiss();
        });
        View no = Controls.button(host, host.getString(R.string.pair_deny), v -> {
            if (answered[0]) return;
            answered[0] = true;
            deny.run();
            dismiss();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiKit.px(16);
        row.addView(yes, lp);
        row.addView(no, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(row);
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        int type = appOverlay
                ? (Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE)
                : WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        try {
            wm.addView(root, wlp);
        } catch (Throwable t) {
            return false;
        }
        shown = root;
        shownIn = wm;
        com.cinetv.launcher.device.SystemAudio.open(host);
        root.post(yes::requestFocus);
        // unanswered requests lapse quietly
        lapse = () -> {
            if (!answered[0]) {
                answered[0] = true;
                deny.run();
            }
            dismiss();
        };
        MAIN.postDelayed(lapse, 60_000L);
        return true;
    }

    public static void dismiss() {
        if (lapse != null) MAIN.removeCallbacks(lapse);
        lapse = null;
        if (shown != null && shownIn != null) {
            try {
                shownIn.removeViewImmediate(shown);
            } catch (Throwable ignored) {
            }
        }
        shown = null;
        shownIn = null;
    }
}
