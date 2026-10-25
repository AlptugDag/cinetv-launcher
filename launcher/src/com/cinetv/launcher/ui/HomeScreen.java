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
 * With Spatial HUD on, the screen becomes a curved visor: the left header group turns +24 degrees,
 * the right group -24 degrees and everything tilts 11 degrees toward the optical centre.
 */
public class HomeScreen {

    public interface Listener {
        void onSearch(String query);
        void onVoice();
        void onLogo();
        void onPhone();
        void onNetwork();
        void onInputs();
        void onAllApps();
        void onSettings();
        /** Long press on the gear: the TV's own settings. */
        void onSystemSettings();
        void onFocusLight(View v, int color);
    }

    public final FrameLayout root;
    /** Names the focused header icon; for the gear it also says how to reach the TV's own settings. */
    private TextView buttonHint;
    public final SpatialEnvironment environment;
    public final GlideScrollView scroll;
    public final PosterShelf shelf;
    public final AppGrid apps;
    public final ModalHost modal;
    public final DvdOpeningView opening;
    public final CalibrationPatternView pattern;
    public final FrameLayout screensaver;
    private final FrameLayout splash;
    public final CinemaScreensaverView screensaverView;
    public final TextView clock, date, weather, shelfLabel, caption, moveHint;
    public final EditText search;
    public final GlassKeyboard keyboard;

    private final Context ctx;
    private final Listener listener;
    private final LinearLayout leftGroup, rightGroup;
    private final FrameLayout searchBox;
    private final FrameLayout logoButton;
    private final FrameLayout aiCard;
    private final FrameLayout micButton;
    private final View[] headerButtons;

    public HomeScreen(Context ctx, Listener listener, AppGrid.Host appHost) {
        this.ctx = ctx;
        this.listener = listener;
        root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.BLACK);

        environment = new SpatialEnvironment(ctx);
        root.addView(environment, match());

        scroll = new GlideScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(UiKit.px(64), UiKit.px(22), UiKit.px(64), UiKit.px(80));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, match());

        // ---- header
        LinearLayout header = new LinearLayout(ctx);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        // Room inside the header's GPU layer for the visor tilt, which would otherwise clip text.
        header.setPadding(UiKit.px(12), UiKit.px(18), UiKit.px(12), UiKit.px(18));

        leftGroup = new LinearLayout(ctx);
        leftGroup.setGravity(Gravity.CENTER_VERTICAL);
        leftGroup.setClipChildren(false);
        logoButton = new FrameLayout(ctx);
        logoButton.setFocusable(true);
        logoButton.setClickable(true);
        ImageView mark = new ImageView(ctx);
        mark.setImageBitmap(UiKit.isLight() ? Brand.tinted(ctx, Color.BLACK, UiKit.px(160)) : Brand.mark(ctx));
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
        TextView signed = UiKit.bold(ctx, 15, UiKit.INK_DIM);
        signed.setLetterSpacing(0.04f);
        CharSequence credit = Brand.signedWordmark(UiKit.INK_FAINT);
        if (!com.cinetv.launcher.service.Integrity.isGenuine(ctx)) {
            credit = new android.text.SpannableStringBuilder(credit).append(" · ").append(ctx.getString(R.string.unofficial_short));
        }
        signed.setText(credit);
        timeCol.addView(signed);
        leftGroup.addView(timeCol);
        header.addView(leftGroup);

        searchBox = new FrameLayout(ctx);
        final Glass searchGlass = new Glass(searchBox, UiKit.pxf(34), Glass.Style.CONTROL);
        searchBox.setBackground(searchGlass);
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(UiKit.px(26), 0, UiKit.px(12), 0);
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
        // CineTV's own glass keyboard opens instead of the system one.
        search.setShowSoftInputOnFocus(false);
        search.setPadding(UiKit.px(16), 0, 0, 0);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // Voice search: the microphone at the end of the bar.
        micButton = new FrameLayout(ctx);
        micButton.setFocusable(true);
        micButton.setClickable(true);
        micButton.setContentDescription(ctx.getString(R.string.voice_search));
        // A plain circle, not sampled glass: inside the tilted visor the glass sample would sit off-centre.
        final android.graphics.drawable.GradientDrawable micDisc = new android.graphics.drawable.GradientDrawable();
        micDisc.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        micDisc.setColor(Color.TRANSPARENT);
        micButton.setBackground(micDisc);
        final ImageView micIcon = new ImageView(ctx);
        final android.graphics.drawable.Drawable md = ctx.getDrawable(R.drawable.ic_mic).mutate();
        md.setTint(UiKit.INK_DIM);
        micIcon.setImageDrawable(md);
        micButton.addView(micIcon, new FrameLayout.LayoutParams(UiKit.px(26), UiKit.px(26), Gravity.CENTER));
        micButton.setOnFocusChangeListener((v, has) -> {
            micDisc.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            md.setTint(has ? UiKit.INK_DARK : UiKit.INK_DIM);
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(140).start();
            if (has) listener.onFocusLight(v, Color.parseColor("#A78BFA"));
        });
        micButton.setOnClickListener(v -> listener.onVoice());
        LinearLayout.LayoutParams mlp0 = new LinearLayout.LayoutParams(UiKit.px(48), UiKit.px(48));
        mlp0.setMargins(UiKit.px(10), 0, 0, 0);
        searchRow.addView(micButton, mlp0);
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
                headerButton(R.drawable.ic_phone, R.string.connect_phone, R.string.connect_phone, v -> listener.onPhone()),
                headerButton(R.drawable.ic_network, R.string.network, R.string.network, v -> listener.onNetwork()),
                headerButton(R.drawable.ic_inputs, R.string.inputs, R.string.inputs, v -> listener.onInputs()),
                headerButton(R.drawable.ic_all_apps, R.string.all_apps, R.string.all_apps, v -> listener.onAllApps()),
                headerButton(R.drawable.ic_settings_gear, R.string.settings_title, R.string.settings_hold_hint, v -> listener.onSettings()),
        };
        headerButtons[headerButtons.length - 1].setOnLongClickListener(v -> {
            listener.onSystemSettings();
            return true;
        });
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
        aiCard.setClipChildren(false);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(UiKit.px(860), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        alp.setMargins(0, UiKit.px(118), 0, 0);
        root.addView(aiCard, alp);

        buttonHint = UiKit.label(ctx, 17, UiKit.INK);
        buttonHint.setSingleLine(true);
        buttonHint.setPadding(UiKit.px(20), UiKit.px(10), UiKit.px(20), UiKit.px(10));
        buttonHint.setBackground(new Glass(buttonHint, UiKit.pxf(22), Glass.Style.PANEL));
        buttonHint.setVisibility(View.GONE);
        root.addView(buttonHint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        keyboard = new GlassKeyboard(ctx);
        root.addView(keyboard, match());

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
        splash = buildSplash(ctx);
        root.addView(splash, match());
        screensaver = new FrameLayout(ctx);
        screensaver.setVisibility(View.GONE);
        screensaverView = new CinemaScreensaverView(ctx);
        screensaver.addView(screensaverView, match());
        root.addView(screensaver, match());

        wireFocus();
    }

    private View headerButton(int icon, int desc, int hint, View.OnClickListener click) {
        final FrameLayout b = Controls.iconButton(ctx, ctx.getDrawable(icon), ctx.getString(desc), click);
        View.OnFocusChangeListener inner = b.getOnFocusChangeListener();
        b.setOnFocusChangeListener((v, has) -> {
            inner.onFocusChange(v, has);
            if (has) {
                listener.onFocusLight(v, Color.parseColor("#38BDF8"));
                showButtonHint(v, ctx.getString(hint));
            } else if (buttonHint != null) {
                buttonHint.setVisibility(View.GONE);
            }
        });
        return b;
    }

    /** Shows the icon's name just under it, kept inside the screen. */
    private void showButtonHint(View button, CharSequence text) {
        if (buttonHint == null) return;
        buttonHint.setText(text);
        buttonHint.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2], origin = new int[2];
        button.getLocationInWindow(at);
        root.getLocationInWindow(origin);
        float x = at[0] - origin[0] + button.getWidth() / 2f - buttonHint.getMeasuredWidth() / 2f;
        x = Math.max(UiKit.px(24), Math.min(x, root.getWidth() - buttonHint.getMeasuredWidth() - UiKit.px(24)));
        buttonHint.setTranslationX(x);
        buttonHint.setTranslationY(at[1] - origin[1] + button.getHeight() + UiKit.px(14));
        buttonHint.setAlpha(0f);
        buttonHint.setVisibility(View.VISIBLE);
        buttonHint.animate().alpha(1f).setStartDelay(250).setDuration(PerformanceProfile.animationMs(ctx, 180)).start();
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
                    (idx == 0 ? micButton : headerButtons[idx - 1]).requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && idx == headerButtons.length - 1) return true;
                return toShelf.onKey(v, keyCode, e);
            });
        }
        search.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (e.getAction() == KeyEvent.ACTION_UP) keyboard.show(search);
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                logoButton.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                micButton.requestFocus();
                return true;
            }
            return toShelf.onKey(v, keyCode, e);
        });
        micButton.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                search.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                headerButtons[0].requestFocus();
                return true;
            }
            return toShelf.onKey(v, keyCode, e);
        });
    }

    /** The opening: the signature, then "CineTV" (red TV) and "by Alptuğ Dağ", fading into Home. */
    private static FrameLayout buildSplash(Context ctx) {
        FrameLayout f = new FrameLayout(ctx);
        f.setBackgroundColor(Color.parseColor("#050507"));
        f.setVisibility(View.GONE);
        f.setClickable(true);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView mark = new ImageView(ctx);
        mark.setImageBitmap(Brand.tinted(ctx, Color.WHITE, UiKit.px(260)));
        col.addView(mark, new LinearLayout.LayoutParams(UiKit.px(200), UiKit.px(200)));
        TextView word = UiKit.text(ctx, 104, UiKit.INK, FontManager.getBlack(ctx));
        word.setText(Brand.wordmark());
        word.setLetterSpacing(-0.02f);
        col.addView(word);
        TextView by = UiKit.label(ctx, 30, UiKit.INK_DIM);
        by.setText("by " + Brand.AUTHOR);
        by.setLetterSpacing(0.08f);
        col.addView(by);
        if (!com.cinetv.launcher.service.Integrity.isGenuine(ctx)) {
            TextView note = UiKit.label(ctx, 22, Brand.TV_RED);
            note.setText(R.string.unofficial);
            note.setGravity(Gravity.CENTER);
            note.setPadding(0, UiKit.px(30), 0, 0);
            note.setMaxWidth(UiKit.px(1100));
            col.addView(note);
        }
        f.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return f;
    }

    public void showSplash() {
        splash.setVisibility(View.VISIBLE);
        splash.setAlpha(1f);
        View content = splash.getChildAt(0);
        content.setAlpha(0f);
        content.setScaleX(0.94f);
        content.setScaleY(0.94f);
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520).start();
        splash.animate().alpha(0f).setStartDelay(1700).setDuration(520)
                .withEndAction(() -> splash.setVisibility(View.GONE)).start();
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
        float[][] rot = {{24f, 11f}, {0f, 11f}, {-24f, 11f}, {18f, 6f}};
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

    /**
     * The assistant's bubble, right under the search bar like Siri's: the answer (or "thinking"
     * while it works) on frosted glass with a soft violet-to-cyan rim. It leaves by itself.
     */
    public void showAiCard(CharSequence title, CharSequence query, CharSequence answer) {
        showBubble(answer, false, 9000);
    }

    public void showThinking() {
        showBubble(ctx.getString(R.string.assistant_thinking), true, 30000);
    }

    /** Shows an answer; {@code staysMs} after which it leaves unless {@link #hideAiCardSoon} is called first. */
    public void showBubble(CharSequence text, boolean thinking, long staysMs) {
        aiCard.removeAllViews();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(UiKit.px(30), UiKit.px(22), UiKit.px(34), UiKit.px(24));
        card.setBackground(Glass.panel(card, 34));
        View orb = new View(ctx);
        android.graphics.drawable.GradientDrawable od = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#F472B6"), Color.parseColor("#A78BFA"), Color.parseColor("#22D3EE")});
        od.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        orb.setBackground(od);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(UiKit.px(34), UiKit.px(34));
        olp.setMargins(0, 0, UiKit.px(22), 0);
        card.addView(orb, olp);
        if (thinking) {
            orb.animate().scaleX(0.7f).scaleY(0.7f).setDuration(600).withEndAction(new Runnable() {
                boolean in = true;
                @Override public void run() {
                    if (aiCard.getVisibility() != View.VISIBLE || orb.getParent() == null) return;
                    in = !in;
                    orb.animate().scaleX(in ? 0.7f : 1f).scaleY(in ? 0.7f : 1f).setDuration(600).withEndAction(this).start();
                }
            }).start();
        }
        TextView a = UiKit.label(ctx, 23, thinking ? UiKit.INK_DIM : UiKit.INK);
        a.setText(text);
        a.setLineSpacing(UiKit.pxf(7), 1f);
        card.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        aiCard.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (aiCard.getVisibility() != View.VISIBLE) {
            aiCard.setVisibility(View.VISIBLE);
            aiCard.setAlpha(0f);
            aiCard.setTranslationY(-UiKit.pxf(24));
            aiCard.setScaleX(0.96f);
            aiCard.animate().alpha(1f).translationY(0).scaleX(1f).setDuration(240).start();
        }
        aiCard.removeCallbacks(hideAi);
        aiCard.postDelayed(hideAi, staysMs);
    }

    public void hideAiCardSoon(long ms) {
        aiCard.removeCallbacks(hideAi);
        aiCard.postDelayed(hideAi, ms);
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
