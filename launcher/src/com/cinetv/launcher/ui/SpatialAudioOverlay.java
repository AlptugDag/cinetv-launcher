package com.cinetv.launcher.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cinetv.launcher.device.AirPodsLink;
import com.cinetv.launcher.service.Prefs;

import java.util.Locale;

/**
 * Liquid-glass side HUD overlay for quick AirPods & Spatial Audio control.
 * Can appear over movies, YouTube, or CineTV Home without interrupting playback.
 */
public final class SpatialAudioOverlay {

    private static WindowManager wm;
    private static View firstChip, selectedChip;

    private static boolean isDescendant(View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == hostView) return true;
        return false;
    }
    private static FrameLayout hostView;
    private static LinearLayout panel;

    private SpatialAudioOverlay() {}

    /**
     * A remote key from the key service: the panel keeps its own focus (the selected button lights up),
     * arrows move it, OK presses it, Back (or left past the first column) closes the panel.
     */
    public static void onKey(int code) {
        if (hostView == null || panel == null) return;
        java.util.ArrayList<View> items = new java.util.ArrayList<>();
        panel.addFocusables(items, View.FOCUS_DOWN);
        if (items.isEmpty()) return;
        View cur = panel.findFocus();
        int i = Math.max(0, items.indexOf(cur));
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                dismiss();
                return;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (cur == null) cur = items.get(0);
                cur.performClick();
                return;
            case KeyEvent.KEYCODE_DPAD_LEFT: {
                View next = cur == null ? null : cur.focusSearch(View.FOCUS_LEFT);
                if (cur == null || next == null || !items.contains(next)) {
                    if (i > 0 && cur != null && items.get(i - 1).getParent() == cur.getParent()) items.get(i - 1).requestFocusFromTouch();
                    else dismiss();
                } else next.requestFocusFromTouch();
                return;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP: {
                int dir = code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN : View.FOCUS_RIGHT;
                View next = cur == null ? null : cur.focusSearch(dir);
                if (cur == null) items.get(0).requestFocusFromTouch();
                else if (next != null && items.contains(next)) next.requestFocusFromTouch();
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i + 1 < items.size()) items.get(i + 1).requestFocusFromTouch();
                else if (code == KeyEvent.KEYCODE_DPAD_DOWN && i + 1 < items.size()) items.get(i + 1).requestFocusFromTouch();
                else if (code == KeyEvent.KEYCODE_DPAD_UP && i > 0) items.get(i - 1).requestFocusFromTouch();
                return;
            }
            default:
        }
    }

    public static boolean isShowing() {
        return hostView != null;
    }

    public static void toggle(Context ctx) {
        if (isShowing()) dismiss();
        else show(ctx);
    }

    public static void show(final Context ctx) {
        if (hostView != null) return;
        firstChip = selectedChip = null;
        final Context app = ctx.getApplicationContext();
        UiKit.init(app);
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        hostView = new FrameLayout(app) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                int code = event.getKeyCode();
                if (event.getAction() == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
                    dismiss();
                    return true;
                }
                if (super.dispatchKeyEvent(event)) return true;
                // the arrows move between the panel's buttons; left past the first button closes the panel
                if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    View f = findFocus();
                    View next = f == null ? null : f.focusSearch(View.FOCUS_LEFT);
                    if (next != null && next != f && isDescendant(next)) next.requestFocus();
                    else dismiss();
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN && findFocus() == null && firstChip != null) {
                    firstChip.requestFocus();
                    return true;
                }
                return false;
            }
        };
        hostView.setFocusable(false);
        hostView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        hostView.setClipChildren(false);

        // Outside touch dismiss area
        hostView.setOnClickListener(v -> dismiss());

        final int panelW = UiKit.px(360);
        panel = new LinearLayout(app);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setFocusable(false);
        panel.setPadding(UiKit.px(24), UiKit.px(36), UiKit.px(24), UiKit.px(36));

        // Liquid glass styling: dark frosted translucent backdrop with luminous edge
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 12, 14, 20));
        bg.setCornerRadii(new float[]{UiKit.pxf(28), UiKit.pxf(28), 0, 0, 0, 0, UiKit.pxf(28), UiKit.pxf(28)});
        bg.setStroke(UiKit.px(1), Color.argb(60, 255, 255, 255));
        panel.setBackground(bg);

        // Header
        TextView title = UiKit.bold(app, 22, UiKit.INK);
        title.setText(tr ? "AirPods & Uzamsal Ses" : "AirPods & Spatial Audio");
        panel.addView(title);

        TextView sub = UiKit.label(app, 14, Color.parseColor("#34D399"));
        sub.setText(tr ? "● Bağlı · Ses ayarları" : "● Connected · Audio settings");
        sub.setPadding(0, UiKit.px(6), 0, UiKit.px(24));
        panel.addView(sub);

        // Section 1: Spatial Audio Mode
        TextView s1 = UiKit.bold(app, 15, UiKit.INK_DIM);
        s1.setText(tr ? "UZAMSAL SES MODU" : "SPATIAL AUDIO");
        panel.addView(s1);

        String[] spatialModes = tr
                ? new String[]{"Kapalı", "Sanal Surround", "Geniş Sahne"}
                : new String[]{"Off", "Virtual Surround", "Wide Stage"};
        // Head tracking needs Apple's own link to the AirPods, which Android does not offer: these are real
        // virtualizer strengths applied to the TV's output.
        final int currentSpatial = Math.min(2, Prefs.get(app).getInt(Prefs.AIRPODS_SPATIAL_MODE, 1));
        panel.addView(createChipRow(app, spatialModes, currentSpatial, idx -> {
            Prefs.get(app).edit().putInt(Prefs.AIRPODS_SPATIAL_MODE, idx).apply();
            com.cinetv.launcher.device.AirPodsLink.applySpatialMode(app);
            Toast.makeText(app, (tr ? "Uzamsal Ses: " : "Spatial Audio: ") + spatialModes[idx], Toast.LENGTH_SHORT).show();
        }));

        // Section 3: Auto-connect toggle
        TextView s3 = UiKit.bold(app, 15, UiKit.INK_DIM);
        s3.setText(tr ? "OTOMATİK TV BAĞLANTISI" : "AUTO RECONNECT");
        s3.setPadding(0, UiKit.px(20), 0, 0);
        panel.addView(s3);

        final boolean autoConn = Prefs.get(app).getBoolean(Prefs.AIRPODS_AUTO_CONNECT, true);
        String[] autoOpts = tr ? new String[]{"Açık", "Kapalı"} : new String[]{"Enabled", "Disabled"};
        panel.addView(createChipRow(app, autoOpts, autoConn ? 0 : 1, idx -> {
            boolean enable = (idx == 0);
            Prefs.get(app).edit().putBoolean(Prefs.AIRPODS_AUTO_CONNECT, enable).apply();
            if (!enable) AirPodsLink.markDisconnected();
            Toast.makeText(app, (tr ? "Otomatik Bağlantı: " : "Auto Reconnect: ") + autoOpts[idx], Toast.LENGTH_SHORT).show();
        }));

        // Section 4: Disconnect / Reconnect Button
        View spacer = new View(app);
        panel.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView actionBtn = Controls.button(app, tr ? "Telefon / Mac İçin Ayır" : "Release for Phone / Mac", v -> {
            AirPodsLink.markDisconnected();
            dismiss();
            Toast.makeText(app, tr ? "AirPods ayrıldı. Telefonunuzdan bağlayabilirsiniz." : "AirPods released for phone/Mac.", Toast.LENGTH_LONG).show();
        });
        panel.addView(actionBtn);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(panelW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        hostView.addView(panel, plp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                android.os.Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        try {
            wm.addView(hostView, lp);
            panel.setTranslationX(panelW);
            panel.animate().translationX(0f).setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            // the remote lands on the selected spatial mode, ready to change it
            final FrameLayout hv = hostView;
            hv.post(() -> {
                View target = selectedChip != null ? selectedChip : firstChip;
                if (target != null) target.requestFocusFromTouch();
            });
        } catch (Throwable t) {
            hostView = null;
            wm = null;
        }
    }

    private static LinearLayout createChipRow(Context ctx, String[] labels, int selectedIdx, ChipCallback cb) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UiKit.px(8), 0, UiKit.px(8));

        final View[] chips = new View[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            final boolean active = (i == selectedIdx);

            TextView chip = new TextView(ctx);
            chip.setText(labels[i]);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(13));
            chip.setTextColor(active ? Color.WHITE : UiKit.INK_DIM);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(UiKit.px(10), UiKit.px(8), UiKit.px(10), UiKit.px(8));
            chip.setFocusable(true);
            chip.setClickable(true);

            GradientDrawable cbg = new GradientDrawable();
            cbg.setCornerRadius(UiKit.pxf(12));
            cbg.setColor(active ? Color.parseColor("#38BDF8") : Color.argb(35, 255, 255, 255));
            chip.setBackground(cbg);

            chip.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    cbg.setColor(Color.WHITE);
                    chip.setTextColor(Color.BLACK);
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start();
                } else {
                    boolean nowActive = chips[idx].getTag() != null && (boolean) chips[idx].getTag();
                    cbg.setColor(nowActive ? Color.parseColor("#38BDF8") : Color.argb(35, 255, 255, 255));
                    chip.setTextColor(nowActive ? Color.WHITE : UiKit.INK_DIM);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
            });

            chip.setTag(active);
            chips[i] = chip;
            if (firstChip == null) firstChip = chip;
            if (active && selectedChip == null) selectedChip = chip;

            chip.setOnClickListener(v -> {
                for (int j = 0; j < chips.length; j++) {
                    chips[j].setTag(j == idx);
                    GradientDrawable b = (GradientDrawable) chips[j].getBackground();
                    b.setColor(j == idx ? Color.parseColor("#38BDF8") : Color.argb(35, 255, 255, 255));
                    ((TextView) chips[j]).setTextColor(j == idx ? Color.WHITE : UiKit.INK_DIM);
                }
                cb.onSelected(idx);
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) clp.leftMargin = UiKit.px(6);
            row.addView(chip, clp);
        }
        return row;
    }

    private interface ChipCallback {
        void onSelected(int index);
    }

    public static void dismiss() {
        if (hostView == null || wm == null) return;
        final FrameLayout hv = hostView;
        final LinearLayout p = panel;
        hostView = null;
        panel = null;

        if (p != null) {
            p.animate().translationX(p.getWidth()).setDuration(220)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            try {
                                wm.removeViewImmediate(hv);
                            } catch (Throwable ignored) {}
                        }
                    }).start();
        } else {
            try {
                wm.removeViewImmediate(hv);
            } catch (Throwable ignored) {}
        }
    }
}
