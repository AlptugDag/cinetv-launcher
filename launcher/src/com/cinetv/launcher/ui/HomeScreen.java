package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
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

import java.util.Locale;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.ThemeManager;

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
        void onRefreshShelf();
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
    public final CinemaCurtainsView cinemaCurtains;
    /** Trailer moments (Settings): the trailer in the opened DVD's tray. */
    public final TrailerPlayer trailer;
    public final TourView tour;
    public final AppGrid apps;
    public final ModalHost modal;
    public final DvdOpeningView opening;
    public final CalibrationPatternView pattern;
    public final FrameLayout screensaver;
    private final FrameLayout splash;
    public final CinemaScreensaverView screensaverView;
    public final TextView clock, date, weather, shelfLabel, caption, moveHint;
    public final LinearLayout specialBadge;
    public final TextView specialBadgeText;
    public final ImageView specialBadgeIcon;
    private String specialDayId = "";
    public final EditText search;
    public final GlassKeyboard keyboard;
    public final VoiceOverlay voice;

    private final Context ctx;
    private final Listener listener;
    private final LinearLayout content;
    private final LinearLayout leftGroup, rightGroup;
    private final FrameLayout searchBox;
    private final FrameLayout logoButton;
    private final FrameLayout aiCard;
    private final FrameLayout micButton;
    private final View[] headerButtons;
    private final ImageView mark;
    private final TextView signed;
    private final TextView ramadanBadge;
    private final ImageView searchIcon;
    private final ImageView micIcon;
    private ThemeManager.Theme currentTheme;
    private boolean spatialOn;
    private LinearLayout updateBanner;
    private TextView updateBannerTitle;
    private TextView updateBannerMessage;
    private ImageView updateBannerIcon;
    private Runnable updateBannerOnDismiss;
    private final Runnable dismissUpdateBannerRunnable = this::hideUpdateBanner;

    private LinearLayout nightPromptView;
    private Runnable nightPromptOnDecline;
    private final Runnable dismissNightPromptRunnable = this::hideNightModePrompt;

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
        content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(UiKit.px(64), UiKit.px(22), UiKit.px(64), UiKit.px(80));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, match());

        // Full-height proscenium velvet curtains framing the TV screen
        cinemaCurtains = new CinemaCurtainsView(ctx);
        cinemaCurtains.setVisibility(View.GONE);
        root.addView(cinemaCurtains, match());

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
        mark = new ImageView(ctx);
        mark.setImageBitmap(UiKit.isLight() ? Brand.tinted(ctx, Color.BLACK, UiKit.px(160)) : Brand.mark(ctx));
        logoButton.addView(mark, match());
        logoButton.setOnFocusChangeListener((v, has) -> {
            UiKit.lift(v, has, 1.15f, 160);
            if (has) listener.onFocusLight(v, currentTheme != null ? currentTheme.accent : Color.parseColor("#38BDF8"));
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

        // A small, non-focusable badge under the date on special days (drawn icon + one line); it never takes the D-pad.
        specialBadge = new LinearLayout(ctx);
        specialBadge.setOrientation(LinearLayout.HORIZONTAL);
        specialBadge.setGravity(Gravity.CENTER_VERTICAL);
        specialBadge.setFocusable(false);
        specialBadge.setClickable(false);
        specialBadge.setPadding(UiKit.px(10), UiKit.px(4), UiKit.px(14), UiKit.px(4));
        specialBadgeIcon = new ImageView(ctx);
        specialBadge.addView(specialBadgeIcon, new LinearLayout.LayoutParams(UiKit.px(30), UiKit.px(20)));
        specialBadgeText = UiKit.bold(ctx, 13, Color.WHITE);
        specialBadgeText.setSingleLine(true);
        specialBadgeText.setPadding(UiKit.px(10), 0, 0, 0);
        specialBadge.addView(specialBadgeText);
        specialBadge.setVisibility(View.GONE);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sblp.topMargin = UiKit.px(4);
        sblp.bottomMargin = UiKit.px(3);
        timeCol.addView(specialBadge, sblp);

        signed = UiKit.bold(ctx, 15, UiKit.INK_DIM);
        signed.setLetterSpacing(0.04f);
        CharSequence credit = Brand.signedWordmark(UiKit.INK_FAINT);
        if (!com.cinetv.launcher.service.Integrity.isGenuine(ctx)) {
            credit = new android.text.SpannableStringBuilder(credit).append(" · ").append(ctx.getString(R.string.unofficial_short));
        }
        signed.setText(credit);
        timeCol.addView(signed);

        ramadanBadge = UiKit.bold(ctx, 13, Color.parseColor("#FDE68A"));
        ramadanBadge.setSingleLine(true);
        ramadanBadge.setVisibility(View.GONE);
        ramadanBadge.setShadowLayer(UiKit.pxf(6), 0, UiKit.pxf(1), Color.argb(180, 0, 0, 0));
        LinearLayout.LayoutParams rblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rblp.topMargin = UiKit.px(3);
        timeCol.addView(ramadanBadge, rblp);

        leftGroup.addView(timeCol);
        header.addView(leftGroup);

        searchBox = new FrameLayout(ctx);
        final Glass searchGlass = new Glass(searchBox, UiKit.pxf(34), Glass.Style.CONTROL);
        searchBox.setBackground(searchGlass);
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(UiKit.px(26), 0, UiKit.px(12), 0);
        searchIcon = new ImageView(ctx);
        android.graphics.drawable.Drawable sd = ctx.getDrawable(R.drawable.ic_search).mutate();
        sd.setTint(UiKit.INK_DIM);
        searchIcon.setImageDrawable(sd);
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(UiKit.px(28), UiKit.px(28)));
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
        micIcon = new ImageView(ctx);
        final android.graphics.drawable.Drawable md = ctx.getDrawable(R.drawable.ic_mic).mutate();
        md.setTint(UiKit.INK_DIM);
        micIcon.setImageDrawable(md);
        micButton.addView(micIcon, new FrameLayout.LayoutParams(UiKit.px(26), UiKit.px(26), Gravity.CENTER));
        micButton.setOnFocusChangeListener((v, has) -> {
            micDisc.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            md.setTint(has ? UiKit.INK_DARK : UiKit.INK_DIM);
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(140).start();
            if (has) {
                listener.onFocusLight(v, currentTheme != null ? currentTheme.accent : Color.parseColor("#A78BFA"));
                showButtonHint(v, ctx.getString(R.string.voice_search));
            } else if (buttonHint != null) {
                buttonHint.animate().cancel();
                buttonHint.setVisibility(View.GONE);
            }
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
            if (has) listener.onFocusLight(searchBox, currentTheme != null ? currentTheme.accent : Color.parseColor("#A78BFA"));
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
                headerButton(R.drawable.ic_refresh, R.string.refresh_shelf, R.string.refresh_shelf_hint, v -> listener.onRefreshShelf()),
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
        header.setTranslationZ(200f);
        content.addView(header);

        // ---- shelf
        shelfLabel = UiKit.bold(ctx, 20, UiKit.INK_DIM);
        shelfLabel.setLetterSpacing(0.14f);
        shelfLabel.setTranslationZ(200f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(UiKit.px(80), UiKit.px(30), 0, 0);
        content.addView(shelfLabel, llp);

        shelf = new PosterShelf(ctx);
        shelf.setMotionListener(cinemaCurtains::setMotion);
        // Extend shelf furniture edge-to-edge across the full screen width, counteracting content horizontal padding
        LinearLayout.LayoutParams shelfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, shelf.shelfHeight());
        shelfLp.leftMargin = -UiKit.px(64);
        shelfLp.rightMargin = -UiKit.px(64);
        content.addView(shelf, shelfLp);

        // ---- apps
        apps = new AppGrid(ctx, appHost);
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // one list of apps: the first row sits as close to the next as any two rows
        dockLp.setMargins(0, UiKit.px(6), 0, 0);
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

        buttonHint = UiKit.bold(ctx, 15, Color.WHITE);
        buttonHint.setSingleLine(true);
        buttonHint.setLetterSpacing(0.03f);
        buttonHint.setShadowLayer(UiKit.pxf(4), 0, UiKit.pxf(2), Color.argb(180, 0, 0, 0));
        buttonHint.setPadding(UiKit.px(22), UiKit.px(10), UiKit.px(22), UiKit.px(10));
        buttonHint.setBackground(new Glass(buttonHint, UiKit.pxf(22), Glass.Style.PANEL));
        buttonHint.setTranslationZ(500f);
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
        trailer = new TrailerPlayer(ctx);
        root.addView(trailer, new FrameLayout.LayoutParams(1, 1));
        opening.setTrailerSlot(new DvdOpeningView.TrailerSlot() {
            @Override
            public void show(com.cinetv.launcher.service.MovieCatalog.Movie movie, android.graphics.RectF screen, float corner) {
                trailer.show(movie, screen, corner, () -> opening.switchToVerticalDiscMode());
            }

            @Override
            public void prepare(com.cinetv.launcher.service.MovieCatalog.Movie movie) {
                trailer.prepare(movie);
            }

            @Override
            public void hide() {
                trailer.stop();
            }
        });
        pattern = new CalibrationPatternView(ctx);
        root.addView(pattern, match());
        if (!com.cinetv.launcher.service.Integrity.isGenuine(ctx)) {
            // a changed copy always says whose work it is and where the original is, on every screen of Home
            TextView badge = UiKit.label(ctx, 18, Color.WHITE);
            badge.setText(R.string.unofficial);
            badge.setMaxWidth(UiKit.px(760));
            badge.setPadding(UiKit.px(20), UiKit.px(12), UiKit.px(20), UiKit.px(12));
            badge.setBackgroundColor(Color.argb(200, 160, 20, 20));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            blp.setMargins(0, 0, UiKit.px(24), UiKit.px(24));
            root.addView(badge, blp);
        }
        tour = new TourView(ctx);
        root.addView(tour, match());
        splash = buildSplash(ctx);
        root.addView(splash, match());
        voice = new VoiceOverlay(ctx);
        root.addView(voice, match());
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
                listener.onFocusLight(v, currentTheme != null ? currentTheme.accent : Color.parseColor("#38BDF8"));
                showButtonHint(v, ctx.getString(hint));
            } else if (buttonHint != null) {
                buttonHint.animate().cancel();
                buttonHint.setVisibility(View.GONE);
            }
        });
        return b;
    }

    /** Shows the icon's name just under it, kept inside the screen with exact HUD visor curvature and high Z-depth. */
    private void showButtonHint(View button, CharSequence text) {
        if (buttonHint == null) return;
        buttonHint.animate().cancel();
        buttonHint.setText(text);
        buttonHint.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2], origin = new int[2];
        button.getLocationInWindow(at);
        root.getLocationInWindow(origin);
        float centerX = at[0] - origin[0] + button.getWidth() / 2f;
        float x = centerX - buttonHint.getMeasuredWidth() / 2f;
        x = Math.max(UiKit.px(24), Math.min(x, root.getWidth() - buttonHint.getMeasuredWidth() - UiKit.px(24)));
        buttonHint.setTranslationX(x);
        buttonHint.setTranslationY(at[1] - origin[1] + button.getHeight() + UiKit.px(14));

        // Always sit at HUD floating plane (500f Z) above curtains (95f) and header (200f)
        buttonHint.setTranslationZ(500f);

        // Exact HUD visor cylindrical perspective curvature matching its horizontal location on screen
        float cam = ctx.getResources().getDisplayMetrics().density * (spatialOn ? 3200f : 8500f);
        buttonHint.setCameraDistance(cam);
        float nx = Math.max(-1f, Math.min(1f, (centerX / (float) Math.max(1, root.getWidth()) - 0.5f) * 2f));
        float pNx = Float.isNaN(lastParallaxNx) ? 0f : lastParallaxNx;
        float pNy = Float.isNaN(lastParallaxNy) ? 0f : lastParallaxNy;
        float rotY = spatialOn ? (-nx * 24f - pNx * 2.4f) : 0f;
        float rotX = spatialOn ? (11f - pNy * 1.6f) : 0f;
        buttonHint.setRotationY(rotY);
        buttonHint.setRotationX(rotX);

        buttonHint.setScaleX(0.92f);
        buttonHint.setScaleY(0.92f);
        buttonHint.setAlpha(0f);
        buttonHint.setVisibility(View.VISIBLE);
        buttonHint.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(180)
                .setDuration(PerformanceProfile.animationMs(ctx, 180))
                .start();
    }

    /**
     * Top-right VisionOS floating banner: notifies user of silent background updates or errors.
     * Automatically disappears after 10 seconds.
     */
    public void showUpdateBanner(final String title, final String message, final boolean isError) {
        showUpdateBanner(title, message, isError, null);
    }

    /**
     * Top-right VisionOS floating banner: notifies user of silent background updates or errors.
     * Automatically disappears after 15 seconds. If unattended on a sleeping TV, allows returning to standby.
     */
    public void showUpdateBanner(final String title, final String message, final boolean isError, final Runnable onDismiss) {
        if (root == null) return;
        this.updateBannerOnDismiss = onDismiss;
        root.post(() -> {
            if (updateBanner == null) {
                buildUpdateBanner();
            }
            root.removeCallbacks(dismissUpdateBannerRunnable);
            updateBannerTitle.setText(title);
            if (message != null && !message.isEmpty()) {
                updateBannerMessage.setText(message);
                updateBannerMessage.setVisibility(View.VISIBLE);
            } else {
                updateBannerMessage.setVisibility(View.GONE);
            }
            if (isError) {
                updateBannerIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                updateBannerIcon.setColorFilter(Color.parseColor("#FF453A"), PorterDuff.Mode.SRC_IN);
            } else {
                updateBannerIcon.setImageResource(android.R.drawable.stat_sys_upload_done);
                updateBannerIcon.setColorFilter(Color.parseColor("#30D158"), PorterDuff.Mode.SRC_IN);
            }
            updateBanner.setVisibility(View.VISIBLE);
            updateBanner.setAlpha(0f);
            updateBanner.setTranslationY(-UiKit.pxf(30));
            updateBanner.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(this::startBannerPulse)
                    .start();
            root.postDelayed(dismissUpdateBannerRunnable, 15_000L); // 15 seconds popup
        });
    }

    private android.animation.ObjectAnimator bannerPulseAnim;

    private void startBannerPulse() {
        stopBannerPulse();
        if (updateBanner == null) return;
        bannerPulseAnim = android.animation.ObjectAnimator.ofFloat(updateBanner, "alpha", 1f, 0.65f, 1f);
        bannerPulseAnim.setDuration(1200);
        bannerPulseAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        bannerPulseAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        bannerPulseAnim.start();
    }

    private void stopBannerPulse() {
        if (bannerPulseAnim != null) {
            bannerPulseAnim.cancel();
            bannerPulseAnim = null;
        }
        if (updateBanner != null) updateBanner.setAlpha(1f);
    }

    public void hideUpdateBanner() {
        stopBannerPulse();
        if (updateBanner == null || updateBanner.getVisibility() != View.VISIBLE) return;
        final Runnable cb = updateBannerOnDismiss;
        updateBannerOnDismiss = null;
        updateBanner.animate()
                .alpha(0f)
                .translationY(-UiKit.pxf(30))
                .setDuration(300)
                .withEndAction(() -> {
                    if (updateBanner != null) updateBanner.setVisibility(View.GONE);
                    if (cb != null) cb.run();
                })
                .start();
    }

    public void showNightModePrompt(final Runnable onAccept, final Runnable onDecline) {
        if (root == null) return;
        this.nightPromptOnDecline = onDecline;
        root.post(() -> {
            if (nightPromptView == null) {
                buildNightModePrompt(onAccept, onDecline);
            }
            root.removeCallbacks(dismissNightPromptRunnable);
            nightPromptView.setVisibility(View.VISIBLE);
            nightPromptView.setAlpha(0f);
            nightPromptView.setTranslationX(UiKit.pxf(50));
            nightPromptView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(380)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            root.postDelayed(dismissNightPromptRunnable, 15_000L);
        });
    }

    public void hideNightModePrompt() {
        if (nightPromptView == null || nightPromptView.getVisibility() != View.VISIBLE) return;
        final Runnable decline = nightPromptOnDecline;
        nightPromptOnDecline = null;
        nightPromptView.animate()
                .alpha(0f)
                .translationX(UiKit.pxf(50))
                .setDuration(300)
                .withEndAction(() -> {
                    if (nightPromptView != null) nightPromptView.setVisibility(View.GONE);
                    if (decline != null) decline.run();
                })
                .start();
    }

    private void buildNightModePrompt(final Runnable onAccept, final Runnable onDecline) {
        nightPromptView = new LinearLayout(ctx);
        nightPromptView.setOrientation(LinearLayout.VERTICAL);
        nightPromptView.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = UiKit.px(24);
        int padV = UiKit.px(18);
        nightPromptView.setPadding(padH, padV, padH, padV);
        nightPromptView.setBackground(new Glass(nightPromptView, UiKit.pxf(20), Glass.Style.PANEL));
        nightPromptView.setTranslationZ(850f);
        nightPromptView.setElevation(UiKit.pxf(28));

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView moonIcon = new ImageView(ctx);
        moonIcon.setImageResource(android.R.drawable.ic_lock_idle_alarm);
        moonIcon.setColorFilter(Color.parseColor("#F59E0B"), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(UiKit.px(24), UiKit.px(24));
        icLp.rightMargin = UiKit.px(10);
        topRow.addView(moonIcon, icLp);

        TextView title = UiKit.bold(ctx, 16, UiKit.INK);
        title.setText("🌙 Gece İzleme Modu");
        topRow.addView(title);
        nightPromptView.addView(topRow);

        TextView desc = UiKit.label(ctx, 13, UiKit.INK_DIM);
        desc.setText("Göz yormayan sıcak ton ve dengeli ses açılsın mı?");
        desc.setPadding(0, UiKit.px(6), 0, UiKit.px(14));
        nightPromptView.addView(desc);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        // Evet button
        TextView btnYes = UiKit.bold(ctx, 14, Color.WHITE);
        btnYes.setText("  Evet  ");
        btnYes.setFocusable(true);
        btnYes.setClickable(true);
        btnYes.setPadding(UiKit.px(20), UiKit.px(8), UiKit.px(20), UiKit.px(8));
        final android.graphics.drawable.GradientDrawable yesBg = new android.graphics.drawable.GradientDrawable();
        yesBg.setCornerRadius(UiKit.pxf(14));
        yesBg.setColor(Color.parseColor("#34C759"));
        btnYes.setBackground(yesBg);
        btnYes.setOnFocusChangeListener((v, has) -> {
            yesBg.setColor(has ? Color.parseColor("#30D158") : Color.parseColor("#34C759"));
            UiKit.lift(v, has, 1.08f, 120);
        });
        btnYes.setOnClickListener(v -> {
            nightPromptOnDecline = null;
            hideNightModePrompt();
            if (onAccept != null) onAccept.run();
        });
        btnRow.addView(btnYes);

        // Spacer
        View spacer = new View(ctx);
        btnRow.addView(spacer, new LinearLayout.LayoutParams(UiKit.px(12), 1));

        // Hayır button
        TextView btnNo = UiKit.bold(ctx, 14, UiKit.INK_DIM);
        btnNo.setText("  Hayır  ");
        btnNo.setFocusable(true);
        btnNo.setClickable(true);
        btnNo.setPadding(UiKit.px(20), UiKit.px(8), UiKit.px(20), UiKit.px(8));
        final android.graphics.drawable.GradientDrawable noBg = new android.graphics.drawable.GradientDrawable();
        noBg.setCornerRadius(UiKit.pxf(14));
        noBg.setColor(Color.argb(40, 255, 255, 255));
        btnNo.setBackground(noBg);
        btnNo.setOnFocusChangeListener((v, has) -> {
            noBg.setColor(has ? Color.argb(90, 255, 255, 255) : Color.argb(40, 255, 255, 255));
            btnNo.setTextColor(has ? Color.WHITE : UiKit.INK_DIM);
            UiKit.lift(v, has, 1.08f, 120);
        });
        btnNo.setOnClickListener(v -> {
            hideNightModePrompt();
        });
        btnRow.addView(btnNo);

        nightPromptView.addView(btnRow);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.END);
        flp.setMargins(0, 0, UiKit.px(40), 0);
        root.addView(nightPromptView, flp);
    }

    public void applyNightViewingMode(boolean on) {
        if (environment != null) {
            environment.postInvalidate();
        }
    }

    private void buildUpdateBanner() {
        updateBanner = new LinearLayout(ctx);
        updateBanner.setOrientation(LinearLayout.HORIZONTAL);
        updateBanner.setGravity(Gravity.CENTER_VERTICAL);
        int padH = UiKit.px(24);
        int padV = UiKit.px(16);
        updateBanner.setPadding(padH, padV, padH, padV);
        updateBanner.setBackground(new Glass(updateBanner, UiKit.pxf(22), Glass.Style.PANEL));
        updateBanner.setTranslationZ(800f);
        updateBanner.setElevation(UiKit.pxf(28));
        updateBanner.setFocusable(true);
        updateBanner.setClickable(true);
        updateBanner.setOnFocusChangeListener((v, has) -> {
            if (has) stopBannerPulse();
            UiKit.lift(v, has, 1.08f, 140);
        });
        updateBanner.setOnClickListener(v -> {
            stopBannerPulse();
            hideUpdateBanner();
            if (modal != null) {
                Panels.changelog(ctx, modal);
            }
        });

        updateBannerIcon = new ImageView(ctx);
        int iconSize = UiKit.px(26);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.rightMargin = UiKit.px(14);
        updateBanner.addView(updateBannerIcon, iconLp);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);

        updateBannerTitle = UiKit.bold(ctx, 16, UiKit.INK);
        textCol.addView(updateBannerTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        updateBannerMessage = UiKit.label(ctx, 13, UiKit.INK_DIM);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = UiKit.px(3);
        textCol.addView(updateBannerMessage, msgLp);

        updateBanner.addView(textCol, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        flp.setMargins(0, UiKit.px(28), UiKit.px(36), 0);
        root.addView(updateBanner, flp);
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
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (idx < headerButtons.length - 1) {
                        headerButtons[idx + 1].requestFocus();
                    }
                    return true;
                }
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

    private static final class SplashLogoLayout extends LinearLayout {
        private float sheenProgress = -1f;
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SplashLogoLayout(Context ctx) {
            super(ctx);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        void setSheenProgress(float p) {
            this.sheenProgress = p;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (sheenProgress >= -0.25f && sheenProgress <= 1.25f) {
                int w = getWidth(), h = getHeight();
                float bandW = UiKit.pxf(140);
                float totalSpan = w + bandW * 2f + h * 0.45f;
                float startX = totalSpan * sheenProgress - bandW - h * 0.45f;
                sheenPaint.setShader(new LinearGradient(
                        startX, 0,
                        startX + bandW, h,
                        new int[]{
                                Color.TRANSPARENT,
                                Color.argb(40, 255, 255, 255),
                                Color.argb(220, 255, 255, 255),
                                Color.argb(40, 255, 255, 255),
                                Color.TRANSPARENT
                        },
                        new float[]{0f, 0.32f, 0.50f, 0.68f, 1f},
                        Shader.TileMode.CLAMP
                ));
                sheenPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                canvas.drawRect(0, 0, w, h, sheenPaint);
                sheenPaint.setXfermode(null);
                sheenPaint.setShader(null);
            }
        }
    }

    /** The opening: cinematic glowing emblem, grand centered "CineTV" and "by Alptuğ Dağ", dissolving into Home. */
    private static FrameLayout buildSplash(Context ctx) {
        FrameLayout f = new FrameLayout(ctx);
        f.setBackgroundColor(Color.parseColor("#030305"));
        f.setVisibility(View.GONE);
        f.setClickable(true);

        // Grand cinematic ambient backlight: authentic brand red heartbeat glow
        View aura = new View(ctx) {
            private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas c) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                float r = Math.min(getWidth(), getHeight()) * 0.52f;
                auraPaint.setShader(new RadialGradient(cx, cy, r,
                        new int[]{
                                Color.argb(165, 235, 18, 35),
                                Color.argb(95, 180, 0, 25),
                                Color.argb(35, 110, 0, 15),
                                Color.TRANSPARENT
                        },
                        new float[]{0f, 0.35f, 0.70f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r, auraPaint);
            }
        };
        f.addView(aura, match());

        SplashLogoLayout col = new SplashLogoLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        ImageView mark = new ImageView(ctx);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setImageBitmap(Brand.tinted(ctx, Color.WHITE, UiKit.px(340)));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(UiKit.px(320), UiKit.px(130));
        markLp.gravity = Gravity.CENTER_HORIZONTAL;
        markLp.bottomMargin = UiKit.px(8);
        col.addView(mark, markLp);

        TextView word = UiKit.text(ctx, 116, UiKit.INK, FontManager.getBlack(ctx));
        word.setGravity(Gravity.CENTER);
        word.setText(Brand.wordmark());
        word.setLetterSpacing(0.04f);
        word.setShadowLayer(UiKit.pxf(24), 0, 0, Color.argb(120, 204, 0, 41));
        LinearLayout.LayoutParams wordLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wordLp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(word, wordLp);

        TextView by = UiKit.label(ctx, 32, Color.parseColor("#F5D59A"));
        by.setGravity(Gravity.CENTER);
        by.setText("by " + Brand.AUTHOR.toUpperCase(new Locale("tr")));
        by.setLetterSpacing(0.22f);
        by.setShadowLayer(UiKit.pxf(14), 0, UiKit.pxf(2), Color.argb(90, 0, 0, 0));
        LinearLayout.LayoutParams byLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        byLp.gravity = Gravity.CENTER_HORIZONTAL;
        byLp.topMargin = UiKit.px(12);
        col.addView(by, byLp);

        if (!com.cinetv.launcher.service.Integrity.isGenuine(ctx)) {
            TextView note = UiKit.label(ctx, 22, Brand.TV_RED);
            note.setText(R.string.unofficial);
            note.setGravity(Gravity.CENTER);
            note.setPadding(0, UiKit.px(30), 0, 0);
            note.setMaxWidth(UiKit.px(1100));
            col.addView(note);
        }

        f.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return f;
    }

    /** The signature is written left to right, "CineTV" types itself in, then "by Alptuğ Dağ" rises. */
    /** The first-run tour: CineTV's best parts, each card pointing at its place on Home. */
    public TourView.Step[] tourSteps() {
        View phone = headerButtons[1], gear = headerButtons[headerButtons.length - 1], inputs = headerButtons[3];
        return new TourView.Step[]{
                new TourView.Step(null, R.string.tour_welcome_t, R.string.tour_welcome),
                new TourView.Step(shelf, R.string.tour_shelf_t, R.string.tour_shelf),
                new TourView.Step(shelf, R.string.tour_dvd_t, R.string.tour_dvd),
                new TourView.Step(gear, R.string.tour_trailers_t, R.string.tour_trailers),
                new TourView.Step(search, R.string.tour_search_t, R.string.tour_search),
                new TourView.Step(micButton, R.string.tour_voice_t, R.string.tour_voice),
                new TourView.Step(gear, R.string.tour_ai_t, R.string.tour_ai, keyStatus(com.cinetv.launcher.ai.LlmClient.isConfigured(ctx))),
                new TourView.Step(gear, R.string.tour_tmdb_t, R.string.tour_tmdb, keyStatus(com.cinetv.launcher.service.MovieCatalog.hasTmdb(ctx))),
                new TourView.Step(gear, R.string.tour_mdb_t, R.string.tour_mdb,
                        keyStatus(com.cinetv.launcher.service.SecretStore.has(ctx, com.cinetv.launcher.service.SecretStore.MDBLIST_KEY))),
                new TourView.Step(null, R.string.tour_allow_t, R.string.tour_allow),
                new TourView.Step(phone, R.string.tour_phone_t, R.string.tour_phone),
                new TourView.Step(phone, R.string.tour_cast_t, R.string.tour_cast),
                new TourView.Step(inputs, R.string.tour_inputs_t, R.string.tour_inputs),
                new TourView.Step(apps.dockView(), R.string.tour_apps_t, R.string.tour_apps),
                new TourView.Step(gear, R.string.tour_settings_t, R.string.tour_settings),
                new TourView.Step(gear, R.string.tour_perf_t, R.string.tour_perf),
                new TourView.Step(null, R.string.tour_saver_t, R.string.tour_saver),
                new TourView.Step(logoButton, R.string.tour_done_t, R.string.tour_done)};
    }

    /** "On this TV: entered ✓" in green, or "not entered yet" in amber. */
    private CharSequence keyStatus(boolean set) {
        android.text.SpannableString s = new android.text.SpannableString(ctx.getString(set ? R.string.tour_key_set : R.string.tour_key_missing));
        s.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor(set ? "#4ADE80" : "#FBBF24")), 0, s.length(), 0);
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, s.length(), 0);
        return s;
    }

    /** Trailer moments are switched in Settings; a trailer playing now stops when they are switched off. */
    public void setTrailerMode(boolean on) {
        if (!on) trailer.stop();
    }

    public void showSplash() {
        splash.setVisibility(View.VISIBLE);
        splash.setAlpha(1f);
        final View aura = splash.getChildAt(0);
        final SplashLogoLayout col = (SplashLogoLayout) splash.getChildAt(1);
        final View mark = col.getChildAt(0);
        final TextView word = (TextView) col.getChildAt(1);
        final View by = col.getChildAt(2);
        final CharSequence full = Brand.wordmark();

        aura.setAlpha(0f);
        aura.setScaleX(0.85f);
        aura.setScaleY(0.85f);

        // Heartbeat pulse animation: rhythmic lub-dub contraction & expansion of the red aura
        android.animation.Keyframe k0 = android.animation.Keyframe.ofFloat(0f, 0.85f);
        android.animation.Keyframe k1 = android.animation.Keyframe.ofFloat(0.14f, 1.15f); // lub crest
        android.animation.Keyframe k2 = android.animation.Keyframe.ofFloat(0.24f, 1.02f); // lub trough
        android.animation.Keyframe k3 = android.animation.Keyframe.ofFloat(0.38f, 1.26f); // dub crest (strongest)
        android.animation.Keyframe k4 = android.animation.Keyframe.ofFloat(0.65f, 0.95f); // settling
        android.animation.Keyframe k5 = android.animation.Keyframe.ofFloat(1.0f, 0.85f);  // rest
        android.animation.PropertyValuesHolder pvhX = android.animation.PropertyValuesHolder.ofKeyframe(View.SCALE_X, k0, k1, k2, k3, k4, k5);
        android.animation.PropertyValuesHolder pvhY = android.animation.PropertyValuesHolder.ofKeyframe(View.SCALE_Y, k0, k1, k2, k3, k4, k5);

        android.animation.Keyframe a0 = android.animation.Keyframe.ofFloat(0f, 0.50f);
        android.animation.Keyframe a1 = android.animation.Keyframe.ofFloat(0.14f, 0.90f);
        android.animation.Keyframe a2 = android.animation.Keyframe.ofFloat(0.24f, 0.72f);
        android.animation.Keyframe a3 = android.animation.Keyframe.ofFloat(0.38f, 1.0f);
        android.animation.Keyframe a4 = android.animation.Keyframe.ofFloat(0.65f, 0.65f);
        android.animation.Keyframe a5 = android.animation.Keyframe.ofFloat(1.0f, 0.50f);
        android.animation.PropertyValuesHolder pvhA = android.animation.PropertyValuesHolder.ofKeyframe(View.ALPHA, a0, a1, a2, a3, a4, a5);

        android.animation.ObjectAnimator heartbeat = android.animation.ObjectAnimator.ofPropertyValuesHolder(aura, pvhX, pvhY, pvhA);
        heartbeat.setDuration(1100);
        heartbeat.setRepeatCount(2);
        heartbeat.setInterpolator(new android.view.animation.LinearInterpolator());
        heartbeat.start();

        mark.setAlpha(0f);
        mark.setScaleX(0.85f);
        mark.setScaleY(0.85f);
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        word.setAlpha(0f);
        word.setScaleX(0.92f);
        word.setScaleY(0.92f);
        word.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(200).setDuration(1400)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(1600);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator());
        a.addUpdateListener(v -> {
            float t = (float) v.getAnimatedValue();
            int shown = Math.round(Math.max(0f, (t - 0.2f) / 0.65f) * full.length());
            android.text.SpannableString s = new android.text.SpannableString(full.toString());
            for (int i = 0; i < full.length(); i++) {
                int c = i >= shown ? Color.TRANSPARENT : i >= 4 ? Brand.TV_RED : UiKit.INK;
                s.setSpan(new android.text.style.ForegroundColorSpan(c), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            word.setText(s);
        });
        a.start();

        // Collector's specular sheen glint passing across the logo mark & letters
        android.animation.ValueAnimator sheenAnim = android.animation.ValueAnimator.ofFloat(-0.25f, 1.25f);
        sheenAnim.setStartDelay(1050);
        sheenAnim.setDuration(950);
        sheenAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        sheenAnim.addUpdateListener(anim -> col.setSheenProgress((float) anim.getAnimatedValue()));
        sheenAnim.start();

        by.setAlpha(0f);
        by.setTranslationY(UiKit.pxf(24));
        by.animate().alpha(1f).translationY(0f).setStartDelay(1000).setDuration(700)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        splash.animate().alpha(0f).setStartDelay(2800).setDuration(600)
                .withEndAction(() -> {
                    heartbeat.cancel();
                    sheenAnim.cancel();
                    splash.setVisibility(View.GONE);
                }).start();
    }

    public boolean isSearchOrInputFocused() {
        return (search != null && search.hasFocus()) || (keyboard != null && keyboard.isShowing());
    }

    public void focusHeader() {
        search.requestFocus();
    }

    public void focusLogo() {
        logoButton.requestFocus();
    }

    /** Applies or removes the HUD visor curvature of the header and spreads spatial 3D across overlays. */
    public void applySpatial(boolean on) {
        this.spatialOn = on;
        float cam = ctx.getResources().getDisplayMetrics().density * (on ? 3200f : 8500f);
        View[] groups = {leftGroup, searchBox, rightGroup, shelfLabel};
        float[][] rot = {{24f, 11f}, {0f, 11f}, {-24f, 11f}, {18f, 6f}};
        float[] z = {UiKit.pxf(18), UiKit.pxf(26), UiKit.pxf(18), UiKit.pxf(14)};
        for (int i = 0; i < groups.length; i++) {
            groups[i].setCameraDistance(cam);
            groups[i].animate()
                    .rotationY(on ? rot[i][0] : 0f)
                    .rotationX(on ? rot[i][1] : 0f)
                    .translationZ(on ? z[i] : 0f)
                    .setDuration(PerformanceProfile.animationMs(ctx, 240)).start();
        }
        aiCard.setCameraDistance(cam);
        aiCard.setRotationX(on ? 7.5f : 0f);
        aiCard.setTranslationZ(on ? UiKit.pxf(38) : 0f);
        if (buttonHint != null) {
            buttonHint.setCameraDistance(cam);
            buttonHint.setRotationX(on ? 11f : 0f);
            buttonHint.setTranslationZ(on ? 500f : 250f);
        }
        moveHint.setCameraDistance(cam);
        moveHint.setRotationX(on ? -8f : 0f);
        moveHint.setTranslationZ(on ? UiKit.pxf(34) : 0f);
        shelf.setSpatial(on);
        apps.setSpatial(on);
        keyboard.setSpatial(on);
        modal.setSpatial(on);
    }

    private float lastParallaxNx = Float.NaN, lastParallaxNy = Float.NaN;

    /** Subtle 3D optical counter-parallax on the curved HUD visor as focus moves across the screen. */
    public void updateVisorParallax(float focusX, float focusY) {
        if (!spatialOn || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        float nx = Math.max(-1f, Math.min(1f, (focusX / root.getWidth() - 0.5f) * 2f));
        float ny = Math.max(-1f, Math.min(1f, (focusY / root.getHeight() - 0.5f) * 2f));
        if (!Float.isNaN(lastParallaxNx) && Math.abs(nx - lastParallaxNx) < 0.25f && Math.abs(ny - lastParallaxNy) < 0.25f) {
            return;
        }
        lastParallaxNx = nx;
        lastParallaxNy = ny;
        int ms = PerformanceProfile.animationMs(ctx, 180);
        float pitch = 11f - ny * 1.6f;
        leftGroup.animate().rotationY(24f - nx * 2.4f).rotationX(pitch).setDuration(ms).start();
        searchBox.animate().rotationY(-nx * 2.8f).rotationX(pitch).setDuration(ms).start();
        rightGroup.animate().rotationY(-24f - nx * 2.4f).rotationX(pitch).setDuration(ms).start();
        shelfLabel.animate().rotationY(18f - nx * 2.0f).rotationX(6f - ny * 1.2f).setDuration(ms).start();
        if (buttonHint != null && buttonHint.getVisibility() == View.VISIBLE) {
            float hintCenterX = buttonHint.getTranslationX() + buttonHint.getWidth() / 2f;
            float hnx = Math.max(-1f, Math.min(1f, (hintCenterX / (float) Math.max(1, root.getWidth()) - 0.5f) * 2f));
            buttonHint.animate().rotationY(-hnx * 24f - nx * 2.4f).rotationX(11f - ny * 1.6f).setDuration(ms).start();
        }
    }

    /** Updates all UI elements (vitrin title, clock, signature, search bar, icons) to perfectly match each room's aesthetic. */
    public void applyTheme(ThemeManager.Theme t) {
        if (t == null) return;
        this.currentTheme = t;

        boolean isCinema = "cinema".equals(t.id);
        if (cinemaCurtains != null) {
            cinemaCurtains.setVisibility(isCinema ? View.VISIBLE : View.GONE);
        }
        int sidePad = isCinema ? UiKit.px(148) : UiKit.px(64);
        content.setPadding(sidePad, UiKit.px(22), sidePad, UiKit.px(80));

        // 1. Vitrin Başlığı (shelfLabel) - specifically red with dark drop shadow in redcarpet theme
        boolean isRedCarpet = "redcarpet".equals(t.id);
        if (isRedCarpet) {
            shelfLabel.setTextColor(Color.parseColor("#FF2638")); // Vivid Premiere Ruby Scarlet
            shelfLabel.setShadowLayer(UiKit.pxf(10), 0, UiKit.pxf(2), Color.argb(240, 0, 0, 0));
        } else if ("cinema".equals(t.id)) {
            shelfLabel.setTextColor(Color.parseColor("#F5C26B")); // Marquee warm gold
            shelfLabel.setShadowLayer(UiKit.pxf(6), 0, UiKit.pxf(1), Color.argb(160, 0, 0, 0));
        } else if ("neon".equals(t.id)) {
            shelfLabel.setTextColor(Color.parseColor("#F472B6")); // Hot synthwave pink
            shelfLabel.setShadowLayer(UiKit.pxf(8), 0, 0, Color.argb(200, 244, 114, 182));
        } else if ("metal".equals(t.id)) {
            shelfLabel.setTextColor(Color.parseColor("#CBD5E1")); // Laser-etched platinum
            shelfLabel.setShadowLayer(UiKit.pxf(4), 0, UiKit.pxf(1), Color.argb(160, 0, 0, 0));
        } else if ("glass".equals(t.id)) {
            shelfLabel.setTextColor(Color.parseColor("#BAE6FD")); // Icy sapphire
            shelfLabel.setShadowLayer(UiKit.pxf(5), 0, UiKit.pxf(1), Color.argb(140, 0, 0, 0));
        } else {
            shelfLabel.setTextColor(UiKit.INK_DIM);
            shelfLabel.setShadowLayer(UiKit.pxf(4), 0, UiKit.pxf(1), Color.argb(120, 0, 0, 0));
        }

        // 2. Saat, Tarih & Hava Durumu
        if (isRedCarpet) {
            clock.setTextColor(Color.WHITE);
            clock.setShadowLayer(UiKit.pxf(8), 0, UiKit.pxf(2), Color.argb(180, 0, 0, 0));
            date.setTextColor(Color.parseColor("#FFE57F")); // Starlight champagne gold
            weather.setTextColor(Color.parseColor("#F5C26B"));
        } else if ("cinema".equals(t.id)) {
            clock.setTextColor(Color.parseColor("#FFFDF5")); // Marquee warm ivory
            clock.setShadowLayer(UiKit.pxf(8), 0, UiKit.pxf(2), Color.argb(180, 0, 0, 0));
            date.setTextColor(Color.parseColor("#F5C26B")); // Marquee gold
            weather.setTextColor(Color.parseColor("#E5A93C"));
        } else if ("neon".equals(t.id)) {
            clock.setTextColor(Color.parseColor("#FFF0F6"));
            clock.setShadowLayer(UiKit.pxf(10), 0, 0, Color.argb(180, 244, 114, 182));
            date.setTextColor(Color.parseColor("#F472B6"));
            weather.setTextColor(Color.parseColor("#38BDF8"));
        } else if ("metal".equals(t.id)) {
            clock.setTextColor(Color.parseColor("#F8FAFC"));
            clock.setShadowLayer(UiKit.pxf(4), 0, UiKit.pxf(1), Color.argb(160, 0, 0, 0));
            date.setTextColor(Color.parseColor("#CBD5E1"));
            weather.setTextColor(Color.parseColor("#94A3B8"));
        } else if ("glass".equals(t.id)) {
            clock.setTextColor(Color.WHITE);
            clock.setShadowLayer(UiKit.pxf(6), 0, UiKit.pxf(1), Color.argb(140, 0, 0, 0));
            date.setTextColor(Color.parseColor("#BAE6FD"));
            weather.setTextColor(Color.parseColor("#7DD3FC"));
        } else if ("closet".equals(t.id)) {
            clock.setTextColor(Color.parseColor("#FAFAF9"));
            clock.setShadowLayer(UiKit.pxf(4), 0, UiKit.pxf(1), Color.argb(140, 0, 0, 0));
            date.setTextColor(Color.parseColor("#D6D3D1"));
            weather.setTextColor(Color.parseColor("#A8A29E"));
        } else {
            clock.setTextColor(UiKit.INK);
            clock.setShadowLayer(UiKit.pxf(6), 0, UiKit.pxf(1), Color.argb(140, 0, 0, 0));
            date.setTextColor(UiKit.INK_DIM);
            weather.setTextColor(Color.parseColor("#7DD3FC"));
        }

        // 3. Alptuğ Dağ İmzası ve Wordmark
        mark.setImageBitmap(Brand.signatureForTheme(ctx, t.id, UiKit.px(160)));
        signed.setText(Brand.signedWordmarkForTheme(t.id));

        // 4. Arama Çubuğu (Search Bar)
        int searchAccent = t.accent;
        if (searchIcon != null) {
            android.graphics.drawable.Drawable sd = ctx.getDrawable(R.drawable.ic_search).mutate();
            sd.setTint(isRedCarpet ? Color.parseColor("#FFE57F") : searchAccent);
            searchIcon.setImageDrawable(sd);
        }
        if (micIcon != null) {
            android.graphics.drawable.Drawable md = ctx.getDrawable(R.drawable.ic_mic).mutate();
            md.setTint(isRedCarpet ? Color.parseColor("#FFE57F") : searchAccent);
            micIcon.setImageDrawable(md);
        }
        search.setHintTextColor(isRedCarpet ? Color.parseColor("#D4A373") : UiKit.withAlpha(searchAccent, 140));
    }

    // ---- AI answer card ---------------------------------------------------------------------------

    private final Runnable hideAi = this::hideAiCard;

    /**
     * The assistant's bubble, right under the search bar like Siri's: the answer (or "thinking"
     * while it works) on frosted glass with a soft violet-to-cyan rim. It leaves by itself.
     */
    public void showAiCard(CharSequence title, CharSequence query, CharSequence answer) {
        showBubble(answer, false, 9000);
        updateSpecialDay(true);
    }

    /** Shows today's special day (if any) beside the clock. {@code force} re-applies colours after a theme change. */
    public void updateSpecialDay(boolean force) {
        com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.today(ctx);
        String id = day == null ? "" : day.id + "|" + day.subtitle;
        if (!force && id.equals(specialDayId)) return;
        specialDayId = id;
        if (day == null) {
            specialBadge.setVisibility(View.GONE);
            return;
        }
        specialBadgeIcon.setImageDrawable(SpecialDayArt.drawable(day.icon, day.accent));
        specialBadgeText.setText(day.title);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setCornerRadius(UiKit.pxf(14));
        if (day.mourning) {
            gd.setColor(Color.argb(215, 22, 22, 26));
            gd.setStroke(UiKit.px(1), Color.argb(170, 156, 163, 175));
            specialBadgeText.setTextColor(Color.parseColor("#E5E7EB"));
            // a solemn tone for the whole header on 10 Kasım
            clock.setTextColor(Color.parseColor("#E5E7EB"));
            clock.setShadowLayer(UiKit.pxf(6), 0, UiKit.pxf(2), Color.argb(180, 0, 0, 0));
            date.setTextColor(Color.parseColor("#9CA3AF"));
            weather.setTextColor(Color.parseColor("#9CA3AF"));
        } else {
            gd.setColor(Color.argb(200, 160, 18, 28));
            gd.setStroke(UiKit.px(1), Color.argb(200, 255, 255, 255));
            specialBadgeText.setTextColor(Color.WHITE);
        }
        specialBadge.setBackground(gd);
        specialBadge.setVisibility(View.VISIBLE);
    }

    public void updateSpecialDay() {
        updateSpecialDay(false);
    }

    public void updateRamadanBadge() {
        com.cinetv.launcher.service.RamadanTimes.getStatusAsync(ctx, status -> {
            if (ramadanBadge == null) return;
            if (status != null && !status.isEmpty()) {
                ramadanBadge.setText(status);
                ramadanBadge.setVisibility(View.VISIBLE);
            } else {
                ramadanBadge.setVisibility(View.GONE);
            }
        });
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
        card.setPadding(UiKit.px(24), UiKit.px(14), UiKit.px(28), UiKit.px(16));
        card.setBackground(new Glass(card, UiKit.pxf(24), Glass.Style.PANEL));
        View orb = new View(ctx);
        android.graphics.drawable.GradientDrawable od = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#F472B6"), Color.parseColor("#A78BFA"), Color.parseColor("#22D3EE")});
        od.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        orb.setBackground(od);
        int orbSize = UiKit.px(24);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(orbSize, orbSize);
        olp.setMargins(0, 0, UiKit.px(16), 0);
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
        TextView a = UiKit.label(ctx, 16, thinking ? UiKit.INK_DIM : UiKit.INK);
        a.setText(text);
        a.setLineSpacing(UiKit.pxf(4), 1f);
        card.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        aiCard.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        aiCard.setTranslationZ(900f);
        if (aiCard.getVisibility() != View.VISIBLE) {
            aiCard.setVisibility(View.VISIBLE);
            aiCard.setAlpha(0f);
            aiCard.setTranslationY(-UiKit.pxf(14));
            aiCard.animate().alpha(1f).translationY(0).setDuration(220).start();
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
