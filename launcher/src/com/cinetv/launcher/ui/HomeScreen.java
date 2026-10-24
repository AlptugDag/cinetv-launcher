package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;
import com.cinetv.launcher.device.PerformanceProfile;

/**
 * The home screen's view tree. Owns layout and focus routing between the header, the DVD shelf and
 * the app rows; behaviour lives in MainActivity and the service layer.
 *
 *   header   [mark | clock · date · weather]   [ AI search ]   [phone] [network] [inputs] [apps] [settings]
 *   label    CATEGORY OF THE SHELF
 *   shelf    DVD cases (cover-flow)
 *   dock     favourite apps
 *   grid     all other apps
 *
 * With Spatial HUD on, the header groups keep the original visor curvature: the left group turns
 * +17 degrees, the right group -17 degrees, everything tilts 8.5 degrees toward the optical centre.
 */
public class HomeScreen {

    public interface Listener {
        void onSearch(String query);
        void onLogo();
        void onPhone();
        void onNetwork();
        void onInputs();
        void onAllApps();
        void onSettings();
        void onFocusLight(View v, int color);
    }

    public final FrameLayout root;
    public final SpatialEnvironment environment;
    public final ScrollView scroll;
    public final PosterShelf shelf;
    public final AppGrid apps;
    public final ModalHost modal;
    public final DvdOpeningView opening;
    public final CalibrationPatternView pattern;
    public final FrameLayout screensaver;
    public final CinemaScreensaverView screensaverView;
    public final TextView clock, date, weather, shelfLabel, caption, moveHint;
    public final EditText search;

    private final Context ctx;
    private final Listener listener;
    private final LinearLayout leftGroup, rightGroup;
    private final FrameLayout searchBox;
    private final FrameLayout logoButton;
    private final FrameLayout aiCard;
    private final View[] headerButtons;

    public HomeScreen(Context ctx, Listener listener, AppGrid.Host appHost) {
        this.ctx = ctx;
        this.listener = listener;
        root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.BLACK);

        environment = new SpatialEnvironment(ctx);
        root.addView(environment, match());

        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(UiKit.px(64), UiKit.px(40), UiKit.px(64), UiKit.px(80));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, match());

        // ---- header
        LinearLayout header = new LinearLayout(ctx);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);

        leftGroup = new LinearLayout(ctx);
        leftGroup.setGravity(Gravity.CENTER_VERTICAL);
        leftGroup.setClipChildren(false);
        logoButton = new FrameLayout(ctx);
        logoButton.setFocusable(true);
        logoButton.setClickable(true);
        ImageView mark = new ImageView(ctx);
        mark.setImageBitmap(Brand.mark(ctx));
        logoButton.addView(mark, match());
        logoButton.setOnFocusChangeListener((v, has) -> {
            UiKit.lift(v, has, 1.15f, 160);
            if (has) listener.onFocusLight(v, Color.parseColor("#38BDF8"));
        });
        logoButton.setOnClickListener(v -> listener.onLogo());
        leftGroup.addView(logoButton, new LinearLayout.LayoutParams(UiKit.px(76), UiKit.px(76)));
        View divider = new View(ctx);
        divider.setBackgroundColor(Color.argb(60, 255, 255, 255));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Math.max(1, UiKit.px(2)), UiKit.px(52));
        dlp.setMargins(UiKit.px(22), 0, UiKit.px(22), 0);
        leftGroup.addView(divider, dlp);
        LinearLayout timeCol = new LinearLayout(ctx);
        timeCol.setOrientation(LinearLayout.VERTICAL);
        clock = UiKit.text(ctx, 56, UiKit.INK, FontManager.getBlack(ctx));
        timeCol.addView(clock);
        LinearLayout sub = new LinearLayout(ctx);
        date = UiKit.label(ctx, 20, UiKit.INK_DIM);
        weather = UiKit.label(ctx, 20, Color.parseColor("#7DD3FC"));
        weather.setPadding(UiKit.px(16), 0, 0, 0);
        sub.addView(date);
        sub.addView(weather);
        timeCol.addView(sub);
        leftGroup.addView(timeCol);
        header.addView(leftGroup);

        searchBox = new FrameLayout(ctx);
        final Glass searchGlass = new Glass(searchBox, UiKit.pxf(34), Glass.Style.CONTROL);
        searchBox.setBackground(searchGlass);
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(UiKit.px(26), 0, UiKit.px(26), 0);
        ImageView glass = new ImageView(ctx);
        android.graphics.drawable.Drawable sd = ctx.getDrawable(R.drawable.ic_search).mutate();
        sd.setTint(UiKit.INK_DIM);
        glass.setImageDrawable(sd);
        searchRow.addView(glass, new LinearLayout.LayoutParams(UiKit.px(28), UiKit.px(28)));
        search = new EditText(ctx);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setHint(R.string.search_hint);
        search.setHintTextColor(UiKit.INK_FAINT);
        search.setTextColor(UiKit.INK);
        search.setTypeface(FontManager.getMedium(ctx));
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.pxf(22));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(UiKit.px(16), 0, 0, 0);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchBox.addView(searchRow, match());
        final GlassBevelView searchBevel = new GlassBevelView(ctx, UiKit.pxf(34), false);
        searchBox.addView(searchBevel, match());
        search.setOnFocusChangeListener((v, has) -> {
            searchBevel.setFocusedState(has);
            searchBox.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(160).start();
            searchBox.setAlpha(has ? 1f : 0.92f);
            if (has) listener.onFocusLight(searchBox, Color.parseColor("#A78BFA"));
        });
        search.setOnEditorActionListener((v, id, e) -> {
            String q = v.getText().toString().trim();
            if (!q.isEmpty()) {
                listener.onSearch(q);
                InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            return true;
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, UiKit.px(68), 1f);
        slp.setMargins(UiKit.px(40), 0, UiKit.px(32), 0);
        header.addView(searchBox, slp);

        rightGroup = new LinearLayout(ctx);
        rightGroup.setGravity(Gravity.CENTER_VERTICAL);
        rightGroup.setClipChildren(false);
        headerButtons = new View[]{
                headerButton(R.drawable.ic_phone, R.string.connect_phone, v -> listener.onPhone()),
                headerButton(R.drawable.ic_network, R.string.network, v -> listener.onNetwork()),
                headerButton(R.drawable.ic_inputs, R.string.inputs, v -> listener.onInputs()),
                headerButton(R.drawable.ic_all_apps, R.string.all_apps, v -> listener.onAllApps()),
                headerButton(R.drawable.ic_settings_gear, R.string.settings_title, v -> listener.onSettings()),
        };
        for (View b : headerButtons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiKit.px(68), UiKit.px(68));
            lp.setMargins(UiKit.px(12), 0, 0, 0);
            rightGroup.addView(b, lp);
        }
        header.addView(rightGroup);
        // The header only changes on focus or each minute: keep it as a GPU texture between frames.
        header.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        content.addView(header);

        // ---- shelf
        shelfLabel = UiKit.bold(ctx, 20, UiKit.INK_DIM);
        shelfLabel.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(UiKit.px(36), UiKit.px(30), 0, 0);
        content.addView(shelfLabel, llp);

        shelf = new PosterShelf(ctx);
        content.addView(shelf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, shelf.shelfHeight()));

        // ---- apps
        apps = new AppGrid(ctx, appHost);
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.setMargins(0, UiKit.px(6), 0, UiKit.px(10));
        content.addView(apps.dockView(), dockLp);
        content.addView(apps.gridView(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- overlays
        caption = UiKit.label(ctx, 15, UiKit.INK_FAINT);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        clp.setMargins(0, 0, UiKit.px(40), UiKit.px(24));
        root.addView(caption, clp);

        aiCard = new FrameLayout(ctx);
        aiCard.setVisibility(View.GONE);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(UiKit.px(900), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        alp.setMargins(0, UiKit.px(130), 0, 0);
        root.addView(aiCard, alp);

        moveHint = UiKit.label(ctx, 20, UiKit.INK);
        moveHint.setText(R.string.move_hint);
        moveHint.setPadding(UiKit.px(32), UiKit.px(16), UiKit.px(32), UiKit.px(16));
        moveHint.setBackground(new Glass(moveHint, UiKit.pxf(28), Glass.Style.PANEL));
        moveHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        mlp.setMargins(0, 0, 0, UiKit.px(40));
        root.addView(moveHint, mlp);

        modal = new ModalHost(ctx);
        root.addView(modal, match());
        opening = new DvdOpeningView(ctx);
        root.addView(opening, match());
        pattern = new CalibrationPatternView(ctx);
        root.addView(pattern, match());
        screensaver = new FrameLayout(ctx);
        screensaver.setVisibility(View.GONE);
        screensaverView = new CinemaScreensaverView(ctx);
        screensaver.addView(screensaverView, match());
        root.addView(screensaver, match());

        wireFocus();
    }

    private View headerButton(int icon, int desc, View.OnClickListener click) {
        final FrameLayout b = Controls.iconButton(ctx, ctx.getDrawable(icon), ctx.getString(desc), click);
        View.OnFocusChangeListener inner = b.getOnFocusChangeListener();
        b.setOnFocusChangeListener((v, has) -> {
            inner.onFocusChange(v, has);
            if (has) listener.onFocusLight(v, Color.parseColor("#38BDF8"));
        });
        return b;
    }

    /** Header → shelf → dock, with the search field reachable from both sides. */
    private void wireFocus() {
        View.OnKeyListener toShelf = (v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                shelf.requestFocus();
                return true;
            }
            return false;
        };
        logoButton.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                search.requestFocus();
                return true;
            }
            return toShelf.onKey(v, keyCode, e);
        });
        for (int i = 0; i < headerButtons.length; i++) {
            final int idx = i;
            headerButtons[i].setOnKeyListener((v, keyCode, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    (idx == 0 ? search : headerButtons[idx - 1]).requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && idx == headerButtons.length - 1) return true;
                return toShelf.onKey(v, keyCode, e);
            });
        }
        search.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && search.getSelectionStart() == 0) {
                logoButton.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && search.getSelectionEnd() == search.length()) {
                headerButtons[0].requestFocus();
                return true;
            }
            return toShelf.onKey(v, keyCode, e);
        });
    }

    public void focusHeader() {
        search.requestFocus();
    }

    public void focusLogo() {
        logoButton.requestFocus();
    }

    /** Applies or removes the HUD visor curvature of the header. */
    public void applySpatial(boolean on) {
        float cam = ctx.getResources().getDisplayMetrics().density * (on ? 2800f : 8500f);
        View[] groups = {leftGroup, searchBox, rightGroup, shelfLabel};
        float[][] rot = {{17f, 8.5f}, {0f, 8.5f}, {-17f, 8.5f}, {14f, 5f}};
        for (int i = 0; i < groups.length; i++) {
            groups[i].setCameraDistance(cam);
            groups[i].animate().rotationY(on ? rot[i][0] : 0f).rotationX(on ? rot[i][1] : 0f)
                    .setDuration(PerformanceProfile.animationMs(ctx, 240)).start();
        }
        shelf.setSpatial(on);
        apps.setSpatial(on);
    }

    // ---- AI answer card ---------------------------------------------------------------------------

    private final Runnable hideAi = this::hideAiCard;

    public void showAiCard(CharSequence title, CharSequence query, CharSequence answer) {
        aiCard.removeAllViews();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.px(40), UiKit.px(30), UiKit.px(40), UiKit.px(32));
        card.setBackground(Glass.panel(card, 36));
        TextView t = UiKit.bold(ctx, 22, Color.parseColor("#C4B5FD"));
        t.setText(title);
        card.addView(t);
        TextView q = UiKit.label(ctx, 18, UiKit.INK_DIM);
        q.setText("“" + query + "”");
        q.setPadding(0, UiKit.px(8), 0, UiKit.px(14));
        card.addView(q);
        TextView a = UiKit.label(ctx, 22, UiKit.INK);
        a.setText(answer);
        a.setLineSpacing(UiKit.pxf(8), 1f);
        card.addView(a);
        aiCard.addView(card, match());
        aiCard.setVisibility(View.VISIBLE);
        aiCard.setAlpha(0f);
        aiCard.setTranslationY(-UiKit.pxf(30));
        aiCard.animate().alpha(1f).translationY(0).setDuration(220).start();
        aiCard.removeCallbacks(hideAi);
        aiCard.postDelayed(hideAi, 8000);
    }

    public boolean isAiCardShowing() {
        return aiCard.getVisibility() == View.VISIBLE;
    }

    public void hideAiCard() {
        if (aiCard.getVisibility() != View.VISIBLE) return;
        aiCard.animate().alpha(0f).translationY(-UiKit.pxf(30)).setDuration(200).withEndAction(() -> aiCard.setVisibility(View.GONE)).start();
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
