package com.cinetv.launcher.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cinetv.launcher.R;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.AppCatalog;

import java.util.List;

/** The app library: every launchable app as a glass card, five per row. */
public final class AllApps {

    public interface Listener {
        void open(String pkg);
        void actions(String pkg, String label);
    }

    private AllApps() {}

    public static void show(final Context ctx, final ModalHost host, final Listener l) {
        host.push(() -> build(ctx, host, l));
    }

    private static View build(final Context ctx, final ModalHost host, final Listener l) {
        List<AppCatalog.Entry> apps = AppCatalog.allApps(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.bold(ctx, 32, UiKit.INK);
        title.setText(ctx.getString(R.string.all_apps_count, apps.size()));
        title.setPadding(UiKit.px(8), 0, 0, UiKit.px(20));
        root.addView(title);

        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(UiKit.px(10), UiKit.px(10), UiKit.px(10), UiKit.px(10));
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(ctx);
                grid.addView(row);
            }
            final AppCatalog.Entry e = apps.get(i);
            row.addView(card(ctx, e, v -> {
                host.close();
                l.open(e.packageName);
            }, v -> {
                l.actions(e.packageName, e.label);
                return true;
            }));
        }
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(UiKit.px(5 * 316), UiKit.px(760)));
        return root;
    }

    private static View card(Context ctx, AppCatalog.Entry e, View.OnClickListener click, View.OnLongClickListener longClick) {
        final LinearLayout c = new LinearLayout(ctx);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(UiKit.px(16), UiKit.px(14), UiKit.px(16), UiKit.px(14));
        final Glass g = new Glass(c, UiKit.pxf(20), Glass.Style.CONTROL);
        c.setBackground(g);
        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(e.icon);
        c.addView(icon, new LinearLayout.LayoutParams(UiKit.px(52), UiKit.px(52)));
        final TextView t = UiKit.bold(ctx, 19, UiKit.INK);
        t.setText(e.label);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(UiKit.px(14), 0, 0, 0);
        c.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.setOnFocusChangeListener((v, has) -> {
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            t.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            UiKit.lift(v, has, 1.05f, PerformanceProfile.animationMs(ctx, 140));
        });
        c.setOnClickListener(click);
        c.setOnLongClickListener(longClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiKit.px(296), UiKit.px(92));
        lp.setMargins(UiKit.px(10), UiKit.px(10), UiKit.px(10), UiKit.px(10));
        c.setLayoutParams(lp);
        return c;
    }
}
