package com.cinetv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import java.util.List;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.cinetv.launcher.device.DeviceTweaks;
import com.cinetv.launcher.device.HardwareKeys;
import com.cinetv.launcher.device.MemoryOptimizer;
import com.cinetv.launcher.service.AppLauncher;

/**
 * The one accessibility service of CineTV, so remote buttons work everywhere, not only on the home
 * screen:
 *  - dedicated buttons and user mappings resolve through {@link HardwareKeys};
 *  - the stock Netflix / YouTube / Prime apps that some remotes launch directly are swapped for the
 *    apps chosen here, and the tuner app that MediaTek TVs open on boot is sent back Home;
 *  - when an app is left, its background processes are trimmed (casting, voice and Bluetooth are
 *    protected);
 *  - the system night mode stays dark, like the launcher.
 *
 * The component name is stable because the TV stores it in Settings.Secure.
 */
public class TvKeyInterceptorService extends AccessibilityService {

    private static volatile TvKeyInterceptorService instance;
    public static volatile boolean launchedYouTubeFromCineTv;
    public static volatile boolean launchedNuvioFromCineTv;
    public static volatile boolean lastNuvioLaunchPreferResume;


    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPackage;
    /** The Back press now in progress belongs to Nuvio/YouTube (decided on its DOWN, kept for its UP). */
    private boolean backToNuvio;
    private long lastMediaPlayingAt;

    public String getLastPackage() {
        return lastPackage;
    }
    private com.cinetv.launcher.ui.SignatureOverlay signature;
    private com.cinetv.launcher.device.AirPodsLink airPods;

    /** Idle over another app (Nuvio, YouTube menus...) with nothing playing: show our screensaver. */
    private final Runnable idleSaver = new Runnable() {
        @Override
        public void run() {
            String top = lastPackage;
            android.util.Log.i("CineTvSaver", "idle over " + top + ", playing=" + DeviceTweaks.isMediaPlaying(TvKeyInterceptorService.this));
            // Never over stock launcher, live TV / HDMI (the tuner's audio is not "music"), nor while anything plays.
            if (top == null || top.equals(getPackageName()) || isTvCore(top) || isStockLauncher(top)
                    || DeviceTweaks.isMediaPlaying(TvKeyInterceptorService.this)) {
                handler.postDelayed(this, 60_000L);
                return;
            }
            startActivity(new Intent(TvKeyInterceptorService.this, ScreensaverActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
    };

    /**
     * The live-TV / last-input apps TV makers start by themselves at power-on (MediaTek-based Arçelik, Beko,
     * Grundig, Philips, TCL, Hisense, Sony, Xiaomi, Toshiba, Vestel and stock Android TV). Stopping a package a
     * TV does not have does nothing.
     */
    static final String[] LIVE_TV_APPS = {
            "com.mediatek.wwtv.tvcenter", "com.mediatek.tv.oneworld.tvcenter", "com.arcelik.tv.livetv",
            "org.droidtv.playtv", "org.droidtv.nettvbrowser", "com.tcl.tv", "com.tcl.tvweishi", "com.tcl.esticker",
            "com.hisense.tv", "com.jamdeo.tv.vod", "com.sony.dtv.tvx", "com.sony.dtv.tvinput.tuner",
            "com.xiaomi.mitv.tvplayer", "com.mitv.tvhome.atv", "com.toshiba.tv.livetv", "com.vestel.livetv", "com.vestel.tvcenter",
            "com.android.tv", "com.google.android.tv"};

    /** When the screen last came on: other makers' live TV is only sent away in the seconds after power-on. */
    private static volatile long wokeAt = -60_000L;

    static boolean isLiveTvApp(String pkg) {
        for (String p : LIVE_TV_APPS) if (p.equals(pkg)) return true;
        return pkg.contains("livetv") || pkg.contains("tvcenter") || pkg.contains("tvplayer") || pkg.startsWith("org.droidtv.playtv");
    }

    /** Wake from standby: CineTV first, and the tuner the TV starts by itself is stopped before it shows. */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (com.cinetv.launcher.device.Uninstaller.isRemoving(TvKeyInterceptorService.this)) return;
            wokeAt = android.os.SystemClock.elapsedRealtime();
            if (!DeviceTweaks.isUnwantedTuner()) return;
            try {
                startActivity(new Intent(TvKeyInterceptorService.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (Throwable ignored) {
            }
            final StringBuilder stop = new StringBuilder();
            for (String p : LIVE_TV_APPS) stop.append("am force-stop ").append(p).append(" 2>/dev/null; ");
            new Thread(() -> com.cinetv.launcher.remote.LocalAdb.shell(TvKeyInterceptorService.this, stop.toString(), 1500), "cinetv-wake").start();
        }
    };

    private final BroadcastReceiver timeTick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            DeviceTweaks.syncNightMode(TvKeyInterceptorService.this);
            MemoryOptimizer.ensureAirPlayReceiver(TvKeyInterceptorService.this);
        }
    };

    private final BroadcastReceiver celebrationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String toast = intent.getStringExtra("toast");
            if (toast != null && !toast.trim().isEmpty()) {
                com.cinetv.launcher.ui.UiKit.toast(TvKeyInterceptorService.this, toast);
            }
            if ("com.cinetv.launcher.ACTION_CELEBRATION".equals(intent.getAction())
                    || intent.getBooleanExtra("force_celebration", false)
                    || intent.getBooleanExtra("show_celebration", false)) {
                com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.today(TvKeyInterceptorService.this);
                if (day == null && intent.getBooleanExtra("force_celebration", false)) {
                    day = com.cinetv.launcher.service.SpecialDay.forDate(2026, 10, 29, 12, 0);
                }
                if (day != null) {
                    com.cinetv.launcher.ui.CelebrationOverlay.show(TvKeyInterceptorService.this, day);
                }
            }
        }
    };

    private final BroadcastReceiver cinesubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String title = intent.getStringExtra("title");
            String insight = intent.getStringExtra("insight");
            if (insight != null && !insight.isEmpty()) {
                com.cinetv.launcher.ui.CineSubOverlay.showInsight(TvKeyInterceptorService.this, title, insight);
            } else if (!(CinePlayerActivity.active && CinePlayerActivity.cinesub())) {
                com.cinetv.launcher.ui.CineSubOverlay.trigger(TvKeyInterceptorService.this);
            }
        }
    };

    /** Puts text into the input field that has focus on screen, in any app; false when there is none. */
    public static boolean typeIntoFocused(String text) {
        TvKeyInterceptorService s = instance;
        if (s == null) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo node = s.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null || !node.isEditable()) return false;
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Adds text at the end of the focused input field in one step (a paste from the phone): typed key by key it
     * outran the key bridge's timeout and characters the TV keyboard can't spell were dropped. False when there is none.
     */
    public static boolean appendToFocused(String add) {
        TvKeyInterceptorService s = instance;
        if (s == null || add == null || add.isEmpty()) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo node = s.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null || !node.isEditable()) return false;
            CharSequence cur = node.getText();
            // a hint shows as the text of an empty field
            String base = cur == null || (android.os.Build.VERSION.SDK_INT >= 26 && node.isShowingHintText()) ? "" : cur.toString();
            String text = base + add;
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            if (!node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false;
            android.os.Bundle sel = new android.os.Bundle();
            sel.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length());
            sel.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Returns true if there is an editable input field currently focused on screen. */
    public static boolean isInputFocused() {
        TvKeyInterceptorService s = instance;
        if (s == null) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo node = s.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            return node != null && node.isEditable();
        } catch (Throwable t) {
            return false;
        }
    }

    public static TvKeyInterceptorService getInstance() {
        return instance;
    }

    /**
     * Performs D-pad navigation, click, or global actions in the currently active window via Accessibility
     * when KeyBridge (ADB) is unavailable.
     */
    private static void findText(android.view.accessibility.AccessibilityNodeInfo n, String label,
                                 java.util.List<android.view.accessibility.AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 40 || !out.isEmpty()) return;
        CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
        if (t != null && t.toString().trim().equalsIgnoreCase(label)) {
            out.add(n);
            return;
        }
        for (int i = 0; i < n.getChildCount(); i++) findText(n.getChild(i), label, out, depth + 1);
    }

    /** Clicks the play button on Nuvio's title page; false while the page (or the button) is not there yet. */
    public boolean clickNuvioPlay() {
        return clickNuvioPlay(false);
    }

    /**
     * Series: an episode the viewer started is resumed ("Devam Et"); a series never watched opens its episode list
     * instead of starting S1E1 ("Oynat" is never pressed for a series). True once done.
     */
    public boolean clickNuvioPlay(boolean series) {
        return clickNuvioPlay(series, false, 5000L);
    }

    public boolean clickNuvioPlay(boolean series, boolean preferResume) {
        return clickNuvioPlay(series, preferResume, 5000L);
    }

    public boolean clickNuvioPlay(boolean series, boolean preferResume, long elapsedMs) {
        try {
            // The active window can be CineTV's own signature overlay drawn over Nuvio: look through every window.
            android.view.accessibility.AccessibilityNodeInfo root = null;
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null && isNuvioApp(r.getPackageName().toString())) {
                    root = r;
                    break;
                }
            }
            if (root == null) root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !isNuvioApp(root.getPackageName().toString())) {
                android.util.Log.d("CineTvPlay", "no Nuvio window yet (" + (root == null ? "none" : root.getPackageName()) + ")");
                return false;
            }
            android.graphics.Rect screen = new android.graphics.Rect();
            root.getBoundsInScreen(screen);
            if (series) return seriesButton(root, preferResume, elapsedMs);

            // First priority: search for ANY resume button with prefix/contains matching
            android.view.accessibility.AccessibilityNodeInfo resumeNode = findResumeNode(root, 0);
            if (resumeNode != null) {
                return performClickOnNode(root, resumeNode, screen, "Resume/Devam");
            }

            // If user explicitly launched via Continue Watching, wait up to 3.5s for async progress before falling back to Play
            if (preferResume && elapsedMs < 3500L) {
                android.util.Log.d("CineTvPlay", "Waiting for Nuvio resume state (" + elapsedMs + "ms)...");
                return false;
            }

            String[] labels = preferResume
                    ? new String[]{"İzlemeye Devam Et", "Devam Et", "Kaldığın Yerden Devam Et", "Resume", "Continue", "Oynat", "Play"}
                    : new String[]{"Oynat", "Play", "İzlemeye Devam Et", "Devam Et", "Kaldığın Yerden Devam Et", "Resume", "Continue"};

            for (String label : labels) {
                java.util.List<android.view.accessibility.AccessibilityNodeInfo> found = new java.util.ArrayList<>();
                findText(root, label, found, 0);
                for (android.view.accessibility.AccessibilityNodeInfo n : found) {
                    CharSequence t = n.getText();
                    if (t == null) continue;
                    String s = t.toString().trim();
                    if (!s.equalsIgnoreCase(label) && !isResumeLabel(s)) continue;
                    if (insideTrailer(n)) continue;
                    if (preferResume && resumeNode == null && ("Oynat".equalsIgnoreCase(label) || "Play".equalsIgnoreCase(label))) {
                        com.cinetv.launcher.service.WatchHistory.onNuvioResumeMissing(this);
                    }
                    return performClickOnNode(root, n, screen, label);
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvPlay", "clickNuvioPlay: " + t);
        }
        return false;
    }

    private static boolean isResumeLabel(String text) {
        if (text == null) return false;
        String s = text.trim().toLowerCase(new java.util.Locale("tr"));
        return s.startsWith("devam") || s.contains("devam et") || s.startsWith("resume")
                || s.startsWith("continue") || s.contains("kaldığın") || s.contains("izlemeye devam");
    }

    private static android.view.accessibility.AccessibilityNodeInfo findResumeNode(android.view.accessibility.AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return null;
        CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
        if (t != null && isResumeLabel(t.toString()) && !insideTrailer(n)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo r = findResumeNode(n.getChild(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private boolean performClickOnNode(android.view.accessibility.AccessibilityNodeInfo root,
                                       android.view.accessibility.AccessibilityNodeInfo n,
                                       android.graphics.Rect screen, String label) {
        android.view.accessibility.AccessibilityNodeInfo c = n;
        for (int i = 0; c != null && !c.isClickable() && !c.isFocusable() && i < 8; i++) c = c.getParent();
        if (c == null || !(c.isClickable() || c.isFocusable())) {
            android.view.accessibility.AccessibilityNodeInfo f2 = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            android.graphics.Rect lb = new android.graphics.Rect(), fb = new android.graphics.Rect();
            n.getBoundsInScreen(lb);
            if (f2 != null) f2.getBoundsInScreen(fb);
            if (f2 != null && fb.contains(lb)) c = f2;
            else return false;
        }
        android.graphics.Rect b = new android.graphics.Rect();
        c.getBoundsInScreen(b);
        if (screen.height() > 0 && (b.height() > screen.height() / 4 || b.width() > screen.width() / 2)) return false;
        c.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS);
        boolean ok = c.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        if (!ok && c.isFocused()) {
            ok = com.cinetv.launcher.remote.LocalAdb.shell(this, "input keyevent 23", 1500);
        }
        android.util.Log.d("CineTvPlay", "'" + label + "' click " + ok);
        return ok;
    }

    static String[] filmPlayLabels(boolean preferResume) {
        return preferResume
                ? new String[]{"İzlemeye Devam Et", "Devam Et", "Kaldığın Yerden Devam Et", "Resume", "Continue", "Oynat", "Play"}
                : new String[]{"Oynat", "Play", "İzlemeye Devam Et", "Devam Et", "Kaldığın Yerden Devam Et", "Resume", "Continue"};
    }

    /** The first text node starting with one of the prefixes (case-insensitive). */
    private static android.view.accessibility.AccessibilityNodeInfo findPrefix(android.view.accessibility.AccessibilityNodeInfo n, String[] prefixes, int depth) {
        if (n == null || depth > 40) return null;
        CharSequence t = n.getText();
        if (t != null) {
            String s = t.toString().trim().toLowerCase(new java.util.Locale("tr"));
            for (String p : prefixes) if (s.startsWith(p)) return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo r = findPrefix(n.getChild(i), prefixes, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Nuvio's series page names the next episode on its button ("Oynat S1 B1", "Oynat S2 B4", "Devam Et ..."). The first
     * episode means never watched: the focus goes down to the episode list instead. Any later one is where the viewer
     * stopped: it is pressed and Nuvio resumes there.
     */
    private boolean seriesButton(android.view.accessibility.AccessibilityNodeInfo root, boolean preferResume, long elapsedMs) {
        android.view.accessibility.AccessibilityNodeInfo n = findPrefix(root, new String[]{"oynat", "devam", "izlemeye devam", "kaldığın yerden", "play", "resume", "continue"}, 0);
        if (n == null) return false; // the page is not there yet
        String t = n.getText().toString().replaceAll("\\s+", " ").trim().toLowerCase(new java.util.Locale("tr"));
        boolean isResume = isResumeLabel(t);
        boolean first = t.matches(".*\\b(s1 ?b1|s1 ?e1|s01 ?e01|1\\. sezon 1\\. bölüm)\\b.*") && !isResume;
        if (first) {
            if (preferResume && elapsedMs < 3500L) {
                // Wait for resume metadata
                return false;
            }
            if (lastNuvioLaunchPreferResume) {
                com.cinetv.launcher.service.WatchHistory.onNuvioResumeMissing(this);
            }
            boolean ok = com.cinetv.launcher.remote.LocalAdb.shell(this, "input keyevent 20", 1500);
            android.util.Log.d("CineTvPlay", "series never watched: episode list " + ok);
            return true;
        }
        android.view.accessibility.AccessibilityNodeInfo c = n;
        for (int i = 0; c != null && !c.isClickable() && !c.isFocusable() && i < 8; i++) c = c.getParent();
        boolean ok = c != null && c.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        if (!ok) ok = com.cinetv.launcher.remote.LocalAdb.shell(this, "input keyevent 23", 1500);
        android.util.Log.d("CineTvPlay", "series resumed (" + t + ") " + ok);
        return true;
    }

    /** True only when Nuvio is showing a title's detail page (with its Play/Resume button), not an in-player menu or stream picker. */
    private boolean isOnNuvioDetailPage() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = null;
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null && isNuvioApp(r.getPackageName().toString())) {
                    root = r;
                    break;
                }
            }
            if (root == null) root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !isNuvioApp(root.getPackageName().toString())) {
                return false;
            }
            android.view.accessibility.AccessibilityNodeInfo n = findPrefix(root,
                    new String[]{"oynat", "devam et", "izlemeye devam", "kaldığın yerden", "play", "resume", "continue", "oynatma kullanılamıyor", "playback unavailable"}, 0);
            return n != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True only when Nuvio is on catalog browsing tabs, stream scraping modal, or detail page before playback. */
    private boolean isNuvioBrowsingOrScraping() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !isNuvioApp(root.getPackageName().toString())) {
                return false;
            }
            // 1. Catalog browsing navigation tabs
            android.view.accessibility.AccessibilityNodeInfo nav = findPrefix(root,
                    new String[]{"keşfet", "kütüphane", "eklentiler", "koleksiyonlar", "discover", "library", "addons", "collections", "board"}, 0);
            if (nav != null) return true;

            // 2. Stream scraping / loading modal or stream selection list
            android.view.accessibility.AccessibilityNodeInfo scraping = findPrefix(root,
                    new String[]{"akışlar aranıyor", "kaynaklar aranıyor", "loading streams", "scraping", "akış seç", "select stream"}, 0);
            if (scraping != null) return true;

            // 3. Detail page before playback
            android.view.accessibility.AccessibilityNodeInfo detail = findPrefix(root,
                    new String[]{"fragman", "trailer", "kütüphaneye ekle", "add to library", "oynatma kullanılamıyor", "playback unavailable"}, 0);
            if (detail != null) return true;

            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** A node in Nuvio's trailer area ("Fragman", "Trailer" in it or its ancestors' text). */
    private static boolean insideTrailer(android.view.accessibility.AccessibilityNodeInfo n) {
        android.view.accessibility.AccessibilityNodeInfo c = n;
        for (int i = 0; c != null && i < 5; i++, c = c.getParent()) {
            CharSequence t = c.getText() != null ? c.getText() : c.getContentDescription();
            if (t != null) {
                String s = t.toString().toLowerCase(java.util.Locale.ROOT);
                if (s.contains("fragman") || s.contains("trailer") || s.contains("teaser")) return true;
            }
        }
        return false;
    }

    public boolean dispatchAccessibilityKey(int code, boolean longPress) {
        try {
            if (code == KeyEvent.KEYCODE_BACK) return performGlobalAction(GLOBAL_ACTION_BACK);
            if (code == KeyEvent.KEYCODE_HOME) return performGlobalAction(GLOBAL_ACTION_HOME);
            if (code == KeyEvent.KEYCODE_APP_SWITCH) return performGlobalAction(GLOBAL_ACTION_RECENTS);
            android.view.accessibility.AccessibilityNodeInfo focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (focused != null) {
                    int action = longPress
                            ? android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK
                            : android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
                    if (focused.performAction(action)) return true;
                    android.view.accessibility.AccessibilityNodeInfo p = focused.getParent();
                    while (p != null) {
                        if (p.isClickable() && p.performAction(action)) return true;
                        p = p.getParent();
                    }
                }
                return false;
            }
            int dir = 0;
            if (code == KeyEvent.KEYCODE_DPAD_UP) dir = android.view.View.FOCUS_UP;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dir = android.view.View.FOCUS_DOWN;
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT) dir = android.view.View.FOCUS_LEFT;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dir = android.view.View.FOCUS_RIGHT;
            if (dir != 0 && focused != null) {
                android.view.accessibility.AccessibilityNodeInfo next = focused.focusSearch(dir);
                if (next != null && next.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)) {
                    return true;
                }
                android.view.accessibility.AccessibilityNodeInfo p = focused;
                int scrollAction = (dir == android.view.View.FOCUS_DOWN || dir == android.view.View.FOCUS_RIGHT)
                        ? android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        : android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                while (p != null) {
                    if (p.isScrollable() && p.performAction(scrollAction)) return true;
                    p = p.getParent();
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Injects a screen tap at (x, y) via AccessibilityService gesture API when KeyBridge is unavailable. */
    public boolean dispatchTap(int x, int y) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(Math.max(0, x), Math.max(0, y));
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50);
            return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Injects a swipe gesture from (x1, y1) to (x2, y2) via AccessibilityService when KeyBridge is unavailable. */
    public boolean dispatchSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(Math.max(0, x1), Math.max(0, y1));
            path.lineTo(Math.max(0, x2), Math.max(0, y2));
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, Math.max(50, durationMs));
            return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        handler.removeCallbacks(cinesubAmbient);
        handler.postDelayed(cinesubAmbient, 15_000);
        try {
            // The moment the screen comes on, before the TV's own tuner / last HDMI input gets its window up.
            IntentFilter on = new IntentFilter(Intent.ACTION_SCREEN_ON);
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenOn, on, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenOn, on);
            IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(timeTick, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(timeTick, f);
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "timeTick register failed: " + t.getMessage());
        }
        try {
            IntentFilter cf = new IntentFilter("com.cinetv.launcher.ACTION_CELEBRATION");
            cf.addAction("com.cinetv.launcher.ACTION_TOAST");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(celebrationReceiver, cf, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(celebrationReceiver, cf);
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "celebrationReceiver register failed: " + t.getMessage());
        }
        try {
            IntentFilter csf = new IntentFilter("com.cinetv.launcher.ACTION_CINESUB");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(cinesubReceiver, csf, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(cinesubReceiver, csf);
            }
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "cinesubReceiver register failed: " + t.getMessage());
        }
        try {
            final com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.today(this);
            if (day != null) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                int year = c.get(java.util.Calendar.YEAR);
                String prefKey = "celebration_shown_" + day.id + "_" + year;
                boolean alreadyShown = com.cinetv.launcher.service.Prefs.get(this).getBoolean(prefKey, false);
                if (!alreadyShown) {
                    com.cinetv.launcher.service.Prefs.get(this).edit().putBoolean(prefKey, true).apply();
                    handler.postDelayed(() -> com.cinetv.launcher.ui.CelebrationOverlay.show(this, day), 2500);
                }
            }
        } catch (Throwable ignored) {}
        try {
            DeviceTweaks.syncNightMode(this);
        } catch (Throwable ignored) {}
        try {
            com.cinetv.launcher.remote.TvWebRemoteServer.start(this);
            com.cinetv.launcher.remote.RelayHost.resumeIfStopped(this);
        } catch (Throwable ignored) {}
        try {
            MemoryOptimizer.ensureAirPlayReceiver(this);
        } catch (Throwable ignored) {}
        // (the memory tuning is a setup step now: HomeGuard runs it once per version, after the TV has started)
        try {
            com.cinetv.launcher.device.AirPodsLink.ensureStarted(this);
        } catch (Throwable t) {
            android.util.Log.w("CineTvKeyService", "AirPodsLink start failed: " + t.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        stopHeldKeyRepeat();
        if (airPods != null) airPods.stop();
        if (signature != null) signature.hide();
        try {
            unregisterReceiver(timeTick);
        } catch (Throwable ignored) {}
        try {
            unregisterReceiver(screenOn);
        } catch (Throwable ignored) {}
        try {
            unregisterReceiver(celebrationReceiver);
        } catch (Throwable ignored) {}
        try {
            unregisterReceiver(cinesubReceiver);
        } catch (Throwable ignored) {}
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (instance == null) return;
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if ("com.android.systemui".equals(pkg) || "android".equals(pkg) || "com.arcelik.smartremoteservice".equals(pkg)) return;
        // While CineTV removes itself it must not pull the viewer back from the TV's own Home.
        if (com.cinetv.launcher.device.Uninstaller.isRemoving(this)) { lastPackage = pkg; return; }

        CharSequence cls = event.getClassName();
        if (cls != null) {
            String cName = cls.toString().toLowerCase(java.util.Locale.US);
            if ((cName.contains("screensaver") || cName.contains("dream")) && !getPackageName().equals(pkg) && !isSystemTool(pkg)) {
                if (!DeviceTweaks.isMediaPlaying(this)) {
                    startActivity(new Intent(this, ScreensaverActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    return;
                }
            }
        }

        if (isStockLauncher(pkg)) {
            // Stock TV launcher (Xiaomi / Google TV) intercepted HOME key or boot: redirect immediately to CineTV
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            return;
        }

        boolean inActiveMediaApp = lastPackage != null && (isNuvioApp(lastPackage) || isYouTubeApp(lastPackage)
                || lastPackage.contains("nova") || lastPackage.contains("vlc") || lastPackage.contains("kodi"));
        boolean bootOrWakeWindow = DeviceTweaks.isUnwantedBootTuner()
                || (android.os.SystemClock.elapsedRealtime() - wokeAt < 15_000L);

        // Other makers' live TV, started by the TV itself at power-on (not by the viewer): back to CineTV.
        if (!inActiveMediaApp && isLiveTvApp(pkg) && !pkg.startsWith("com.mediatek") && !pkg.startsWith("com.arcelik") && !pkg.equals("com.android.tv")
                && !pkg.equals("com.google.android.tv") && DeviceTweaks.isUnwantedTuner() && !isTvMenu(cls)
                && bootOrWakeWindow) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            return;
        }
        switch (pkg) {
            case "com.mediatek.wwtv.tvcenter":
            case "com.mediatek.tv.oneworld.tvcenter": // Grundig
            case "com.arcelik.tv.livetv":
            case "com.android.tv":
            case "com.google.android.tv":
                // While the viewer is inside Nuvio or another media app, MediaTek/Arçelik TV services often emit
                // background window events (volume OSD, audio mode, CEC): never pull the viewer out of Nuvio or
                // overwrite lastPackage.
                if (inActiveMediaApp && DeviceTweaks.isUnwantedTuner()) {
                    return;
                }
                // The OEM tuner MediaTek/Arçelik TVs push on boot/wake: suppress immediately and stay in CineTV.
                // If user deliberately pressed Inputs / Live TV, it is left alone.
                // MediaTek TVs draw their own settings, picture and sound menus inside the tuner app: those stay open.
                if (bootOrWakeWindow && DeviceTweaks.isUnwantedTuner() && !isTvMenu(cls)) {
                    new Thread(() -> com.cinetv.launcher.remote.LocalAdb.shell(this, "am force-stop " + pkg + " 2>/dev/null; am force-stop com.mediatek.wwtv.tvcenter 2>/dev/null; am force-stop com.arcelik.tv.livetv 2>/dev/null", 800)).start();
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    return;
                }
                if (inActiveMediaApp && isTvMenu(cls)) {
                    // A quick TV picture/sound OSD over Nuvio is not leaving Nuvio
                    return;
                }
                break;
            case "com.google.android.katniss":
            case "com.google.android.googlequicksearchbox":
            case "com.google.android.apps.googleassistant":
                // If CineTV did not deliberately start listening, dismiss Katniss/Assistant immediately,
                // pause any playing content on screen if possible, and NEVER dump the viewer to the home screen.
                if (android.os.SystemClock.elapsedRealtime() - MainActivity.ourVoiceAt > 15_000) {
                    // The remote's mic woke Google's assistant (Grundig, Arçelik and other remotes send the voice
                    // straight to it, so the key never reaches CineTV): close it at once and open Gamze instead.
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    DeviceTweaks.pauseMedia(this);
                    // (katniss is no longer force-stopped here: on Google TV the same app can serve speech recognition,
                    // and stopping it left Gamze's microphone deaf. The assistant is taken over instead: HomeGuard.)
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    handler.postDelayed(() -> startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            .putExtra("action", "voice")), 350); // once the assistant has let go of the microphone
                    return;
                }
                break;
            case "com.netflix.ninja":
                AppLauncher.launchFirst(this, AppLauncher.NUVIO_PACKAGES);
                return;
            case "com.google.android.youtube.tv":
                AppLauncher.launchFirst(this, AppLauncher.YOUTUBE_PACKAGES);
                return;
            case "com.amazon.amazonvideo.livingroom":
                // The Prime button: send a video from the phone (built-in casting).
                AppLauncher.openPhoneCast(this);
                return;
            default:
                break;
        }

        if (lastPackage != null && !lastPackage.equals(pkg) && isAppSwitch(pkg)) {
            // Never force-stop Nuvio, Nova Player, or YouTube in the background: force-stopping Nuvio when an
            // external player or system overlay briefly opens causes Nuvio to crash/close on Arçelik TVs.
            final String left = lastPackage;
            if (!isNuvioApp(left) && !isYouTubeApp(left) && !left.contains("nova") && !left.equals(getPackageName())) {
                handler.postDelayed(() -> {
                    if (!left.equals(lastPackage)) MemoryOptimizer.kill(this, left);
                }, 60_000L);
            }
            if (getPackageName().equals(pkg)) handler.postDelayed(() -> MemoryOptimizer.sweepAsync(this), 2000);
        }
        lastPackage = pkg;
        if (isNuvioApp(pkg)) {
            com.cinetv.launcher.remote.TvWebRemoteServer.start(this);
            com.cinetv.launcher.remote.RelayHost.resumeIfStopped(this);
            new Thread(() -> com.cinetv.launcher.remote.KeyBridge.warmUp(this), "cinetv-keys-nuvio").start();
        }
        armIdleSaver();
        if (signature == null) signature = new com.cinetv.launcher.ui.SignatureOverlay(this);
        if (AppLauncher.NUVIO.equals(pkg)) signature.show();
        else if (isAppSwitch(pkg)) signature.hide();
    }

    /** The TV's own live TV / inputs app (Arçelik/Beko: wwtv, Grundig: oneworld). */
    private static boolean isTvCore(String pkg) {
        return pkg.startsWith("com.mediatek.wwtv") || pkg.startsWith("com.mediatek.tv.oneworld.tvcenter");
    }

    /** Stock TV launchers (Xiaomi Mi TV, Google TV, Android TV, Next Media Box, Amlogic, Droidlogic, Mstar, Rockchip). */
    private boolean isStockLauncher(String pkg) {
        if (pkg == null || pkg.equals(getPackageName())) return false;
        // Settings, accessibility and file managers are never the launcher, even when they carry a fallback Home.
        if (isSystemTool(pkg)) return false;
        if (pkg.equals("com.google.android.tvlauncher")
                || pkg.equals("com.google.android.apps.tv.launcherx")
                || pkg.equals("com.xiaomi.mitv.tvhome")
                || pkg.equals("com.android.tv.launcher")
                || pkg.contains("tvlauncher")
                || pkg.contains("mitv.tvhome")
                || pkg.contains("droidlogic")
                || pkg.contains("mstar")
                || pkg.contains("homeshell")
                || pkg.contains("rockchip")
                || pkg.contains("elinkway")
                || pkg.contains("shafa")
                || pkg.contains("dangbei")
                || pkg.contains("next.launcher")) {
            return true;
        }
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> homes = getPackageManager().queryIntentActivities(homeIntent, 0);
            for (ResolveInfo ri : homes) {
                // FallbackHome (Settings) declares HOME at priority -1000: only real launchers count.
                if (ri.activityInfo != null && ri.priority >= 0 && pkg.equals(ri.activityInfo.packageName)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** A settings / menu screen of the TV's own tuner app, as opposed to its live-TV picture. */
    private static boolean isTvMenu(CharSequence cls) {
        if (cls == null) return false;
        String c = cls.toString().toLowerCase(java.util.Locale.US);
        return c.contains("setting") || c.contains("menu") || c.contains("setup") || c.contains("option")
                || c.contains("picture") || c.contains("sound") || c.contains("network") || c.contains("system")
                || c.contains("dialog") || c.contains("wizard") || c.contains("source") || c.contains("input")
                || c.contains("preference") || c.contains("accessib") || c.contains("timer") || c.contains("parental")
                || c.contains("volume") || c.contains("audio") || c.contains("osd");
    }

    /** The TV's own settings, accessibility pages and file managers: left alone when the viewer opens them. */
    private static boolean isSystemTool(String pkg) {
        String p = pkg.toLowerCase(java.util.Locale.US);
        return p.contains("settings") || p.contains("accessibility") || p.contains("talkback")
                || p.contains("documentsui") || p.contains("filemanager") || p.contains("file.manager")
                || p.contains("fileexplorer") || p.contains("explorer") || p.contains(".files")
                || p.contains("packageinstaller") || p.contains("permissioncontroller") || p.contains("mediacenter")
                || p.contains("mediabrowser") || p.contains("tv.settings") || p.contains("provision");
    }

    /** A real change of app (not a keyboard, toast or pop-up drawn over Nuvio). */
    private boolean isAppSwitch(String pkg) {
        if (pkg.equals(getPackageName()) || isTvCore(pkg) || isStockLauncher(pkg)) return true;
        if (pkg.contains("inputmethod") || pkg.contains("katniss")) return false;
        return AppLauncher.isLaunchable(getPackageManager(), pkg);
    }

    private void armIdleSaver() {
        handler.removeCallbacks(idleSaver);
        int minutes = com.cinetv.launcher.ui.Panels.screensaverMinutes(this);
        if (minutes <= 0 || !com.cinetv.launcher.service.Prefs.get(this).getBoolean(com.cinetv.launcher.ui.Panels.SCREENSAVER_OVER_APPS, true)) return;
        long timeout = minutes * 60_000L;
        handler.postDelayed(idleSaver, timeout);
    }

    private volatile long lastNavKeyAt = 0L;
    private boolean blueHeld;
    /** Re-sent short blue presses come back through here: let those through untouched. */
    private long blueReplayAt;

    /** Continuous hold-to-repeat state for Volume Up/Down and Channel Up/Down on TV remotes. */
    private int heldRepeatKeyCode = 0;
    private long heldRepeatStartedAt = 0L;
    private long syntheticChannelReplayAt = 0L;
    private final Runnable pendingStopRepeat = this::stopHeldKeyRepeat;

    private void adjustTvVolume(int dir) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.adjustSuggestedStreamVolume(dir, android.media.AudioManager.USE_DEFAULT_STREAM_TYPE, android.media.AudioManager.FLAG_SHOW_UI);
                am.adjustVolume(dir, android.media.AudioManager.FLAG_SHOW_UI);
            }
        } catch (Throwable t) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI);
                }
            } catch (Throwable ignored) {}
        }
    }

    private final Runnable holdRepeatTick = new Runnable() {
        @Override
        public void run() {
            final int k = heldRepeatKeyCode;
            if (k == 0) return;
            long heldFor = android.os.SystemClock.elapsedRealtime() - heldRepeatStartedAt;
            if (heldFor > 8_000L) {
                stopHeldKeyRepeat();
                return;
            }
            if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN) {
                int dir = (k == KeyEvent.KEYCODE_VOLUME_UP)
                        ? android.media.AudioManager.ADJUST_RAISE
                        : android.media.AudioManager.ADJUST_LOWER;
                adjustTvVolume(dir);
                handler.postDelayed(this, 90L);
            } else if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                stepChannelWhileHeld(k);
                handler.postDelayed(this, 175L);
            }
        }
    };

    private void stopHeldKeyRepeat() {
        heldRepeatKeyCode = 0;
        handler.removeCallbacks(holdRepeatTick);
        handler.removeCallbacks(pendingStopRepeat);
    }

    private void stepChannelWhileHeld(final int channelKeyCode) {
        syntheticChannelReplayAt = android.os.SystemClock.elapsedRealtime();
        MainActivity ma = MainActivity.instance;
        if (ma != null && MainActivity.isInFront()) {
            long now = android.os.SystemClock.uptimeMillis();
            ma.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, channelKeyCode, 0));
            ma.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, channelKeyCode, 0));
            return;
        }
        new Thread(() -> {
            if (!com.cinetv.launcher.remote.KeyBridge.key(TvKeyInterceptorService.this, channelKeyCode, false)) {
                com.cinetv.launcher.remote.LocalAdb.shell(TvKeyInterceptorService.this, "input keyevent " + channelKeyCode, 800);
            }
        }, "cinetv-ch-repeat").start();
    }

    /** The blue key held: a screenshot of whatever is on screen, which the phone remote then shows. */
    private final Runnable blueScreenshot = new Runnable() {
        @Override
        public void run() {
            blueHeld = true;
            final boolean tr = "tr".equals(java.util.Locale.getDefault().getLanguage());
            com.cinetv.launcher.remote.ScreenShot.takeAsync(TvKeyInterceptorService.this, ok -> handler.post(() ->
                    com.cinetv.launcher.ui.UiKit.toast(TvKeyInterceptorService.this, ok
                            ? (tr ? "Ekran görüntüsü telefona gönderildi" : "Screenshot sent to your phone")
                            : (tr ? "Ekran görüntüsü alınamadı" : "Couldn't take a screenshot"))));
        }
    };
    private boolean yellowHeld;

    /** The yellow key held: a screenshot of whatever is on screen, which the phone remote then shows. */
    private final Runnable yellowScreenshot = new Runnable() {
        @Override
        public void run() {
            yellowHeld = true;
            final boolean tr = "tr".equals(java.util.Locale.getDefault().getLanguage());
            com.cinetv.launcher.remote.ScreenShot.takeAsync(TvKeyInterceptorService.this, ok -> handler.post(() ->
                    com.cinetv.launcher.ui.UiKit.toast(TvKeyInterceptorService.this, ok
                            ? (tr ? "Ekran görüntüsü telefona gönderildi" : "Screenshot sent to your phone")
                            : (tr ? "Ekran görüntüsü alınamadı" : "Couldn't take a screenshot"))));
        }
    };

    /** CineSUB's unprompted notes: checked every 18 seconds while a player is actively playing video. */
    private final Runnable cinesubAmbient = new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, 18_000);
            try {
                // off (the default) or low-memory TV: no background tree walks or captures while a film plays
                if (!com.cinetv.launcher.service.Prefs.cinesubAmbient(TvKeyInterceptorService.this)) return;
                if (com.cinetv.launcher.device.PerformanceProfile.lowMemory(TvKeyInterceptorService.this)) return;
                // AlptugPlayer lives inside CineTV: it counts while a film plays in it (its own state, since
                // tunnelled or passthrough sound does not show up as "music active")
                if (CinePlayerActivity.active) {
                    if (CinePlayerActivity.isPlayingNow()) com.cinetv.launcher.ui.CineSubOverlay.ambientTick(TvKeyInterceptorService.this);
                    return;
                }
                String pkg = lastPackage;
                if (pkg == null || pkg.equals(getPackageName())) return;
                boolean nuvio = isNuvioApp(pkg);
                boolean player = nuvio || isYouTubeApp(pkg) || pkg.contains("nova") || pkg.contains("vlc")
                        || pkg.contains("mxtech") || pkg.contains("kodi") || pkg.contains("player") || pkg.contains("video");
                if (!player) return;
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                boolean audioActive = am != null && am.isMusicActive();
                boolean isMediaActive = audioActive || DeviceTweaks.isMediaPlaying(TvKeyInterceptorService.this);
                long idleMs = android.os.SystemClock.elapsedRealtime() - lastNavKeyAt;
                // Require active media playback AND at least 6s since last remote navigation key so we never
                // fire ambient CineSUB on paused menus, detail screens, or static posters.
                if (!isMediaActive || idleMs < 6_000L) return;
                if (nuvio && isNuvioBrowsingOrScraping()) return;
                com.cinetv.launcher.ui.CineSubOverlay.ambientTick(TvKeyInterceptorService.this);
            } catch (Throwable ignored) {
                // the periodic check never dies on one bad tick
            }
        }
    };

    /**
     * Checks whether a button label in Nuvio removes an item from Continue Watching / Watchlist / Library.
     */
    public static boolean isNuvioRemoveLabel(String text) {
        if (text == null) return false;
        String s = text.trim().toLowerCase(new java.util.Locale("tr"));
        if (s.isEmpty()) return false;
        return s.equals("kaldır") || s.equals("sil") || s.equals("remove") || s.equals("delete")
                || s.contains("listeden kaldır") || s.contains("listeden çıkar")
                || s.contains("kütüphaneden kaldır") || s.contains("kütüphaneden çıkar")
                || s.contains("izleme listesinden") || s.contains("devam et'ten kaldır")
                || s.contains("devam etten kaldır") || s.contains("izlemeye devam") && s.contains("kaldır")
                || s.contains("ilerlemeyi temizle") || s.contains("ilerlemeyi sıfırla")
                || s.contains("geçmişten kaldır") || s.contains("geçmişi temizle")
                || s.contains("remove from") || s.contains("clear progress")
                || s.contains("delete from") || s.contains("remove from watchlist")
                || s.contains("remove from library") || s.contains("remove from continue");
    }

    private static void collectWindowText(android.view.accessibility.AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (n == null || depth > 28 || sb.length() > 6000) return;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) sb.append(t).append(' ');
        CharSequence cd = n.getContentDescription();
        if (cd != null && cd.length() > 0) sb.append(cd).append(' ');
        int count = n.getChildCount();
        for (int i = 0; i < count; i++) {
            collectWindowText(n.getChild(i), sb, depth + 1);
        }
    }

    private String lastNuvioMatchedTitle;

    /**
     * Tracks which WatchHistory title is currently focused or open in Nuvio, and detects if its resume/watchlist
     * state has been cleared on Nuvio's detail page.
     */
    private void inspectNuvioForWatchHistorySync() {
        try {
            if (!isNuvioApp(lastPackage) || DeviceTweaks.isMediaPlaying(this)) return;
            java.util.List<com.cinetv.launcher.service.WatchHistory.Entry> entries = com.cinetv.launcher.service.WatchHistory.all(this);
            if (entries.isEmpty()) return;
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !isNuvioApp(root.getPackageName().toString())) return;
            StringBuilder sb = new StringBuilder();
            collectWindowText(root, sb, 0);
            String screen = sb.toString();
            String lower = screen.toLowerCase(new java.util.Locale("tr"));
            for (com.cinetv.launcher.service.WatchHistory.Entry e : entries) {
                if (e.movie == null) continue;
                String t = e.movie.title == null ? "" : e.movie.title.trim().toLowerCase(new java.util.Locale("tr"));
                String orig = e.movie.originalTitle == null ? "" : e.movie.originalTitle.trim().toLowerCase(new java.util.Locale("tr"));
                if ((!t.isEmpty() && lower.contains(t)) || (!orig.isEmpty() && lower.contains(orig))) {
                    lastNuvioMatchedTitle = e.movie.title != null && !e.movie.title.isEmpty() ? e.movie.title : e.movie.originalTitle;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void checkNuvioKeyForWatchlistRemoval(int code) {
        try {
            if (!isNuvioApp(lastPackage) || DeviceTweaks.isMediaPlaying(this)) return;
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                handler.postDelayed(this::inspectNuvioForWatchHistorySync, 180L);
                return;
            }
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return;
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            android.view.accessibility.AccessibilityNodeInfo focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            StringBuilder focusedText = new StringBuilder();
            if (focused != null) {
                collectWindowText(focused, focusedText, 0);
                android.view.accessibility.AccessibilityNodeInfo p = focused.getParent();
                if (p != null && focusedText.length() == 0) collectWindowText(p, focusedText, 0);
            }
            String ft = focusedText.toString().trim();
            if (isNuvioRemoveLabel(ft)) {
                StringBuilder allText = new StringBuilder();
                collectWindowText(root, allText, 0);
                if (lastNuvioMatchedTitle != null && !lastNuvioMatchedTitle.isEmpty()) {
                    allText.append(' ').append(lastNuvioMatchedTitle);
                }
                com.cinetv.launcher.service.WatchHistory.forgetMatchingFromNuvio(this, allText.toString(), true);
                handler.postDelayed(() -> com.cinetv.launcher.service.WatchHistory.syncWithNuvio(this), 900L);
            } else {
                handler.postDelayed(this::inspectNuvioForWatchHistorySync, 350L);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isNuvioApp(String pkg) {
        if (pkg == null) return false;
        for (String p : AppLauncher.NUVIO_PACKAGES) {
            if (pkg.equals(p)) return true;
        }
        return pkg.contains("nuvio") || pkg.contains("stremio");
    }

    private boolean isYouTubeApp(String pkg) {
        if (pkg == null) return false;
        for (String p : AppLauncher.YOUTUBE_PACKAGES) {
            if (pkg.equals(p)) return true;
        }
        return pkg.contains("youtube") || pkg.contains("smarttube");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (instance == null) return false;
        int code = event.getKeyCode();
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            lastNavKeyAt = android.os.SystemClock.elapsedRealtime();
            if (event.getRepeatCount() == 0 && isNuvioApp(lastPackage)) {
                checkNuvioKeyForWatchlistRemoval(code);
            }
        }
        // Holiday celebration modal: any key closes the celebration overlay cleanly
        if (com.cinetv.launcher.ui.CelebrationOverlay.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN) {
                com.cinetv.launcher.ui.CelebrationOverlay.dismiss();
            }
            return true;
        }
        if (com.cinetv.launcher.device.Uninstaller.isRemoving(this)) return false;

        // Continuous hold-to-repeat for Volume Up/Down and Channel Up/Down across all TV remotes (IR, BLE, CEC):
        // Many Android TV firmwares (Arçelik/MediaTek) only step by 1 unit when an AccessibilityService filters keys.
        if (HardwareKeys.isRepeatableHoldKey(code)) {
            if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                if (android.os.SystemClock.elapsedRealtime() - syntheticChannelReplayAt < 120L) {
                    return false;
                }
            }
            if (action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(pendingStopRepeat);
                if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    int dir = (code == KeyEvent.KEYCODE_VOLUME_UP)
                            ? android.media.AudioManager.ADJUST_RAISE
                            : android.media.AudioManager.ADJUST_LOWER;
                    if (event.getRepeatCount() > 0) {
                        adjustTvVolume(dir);
                        return true;
                    }
                    if (heldRepeatKeyCode != code) {
                        stopHeldKeyRepeat();
                        heldRepeatKeyCode = code;
                        heldRepeatStartedAt = android.os.SystemClock.elapsedRealtime();
                        handler.postDelayed(holdRepeatTick, 260L);
                        return false;
                    } else {
                        adjustTvVolume(dir);
                        return true;
                    }
                }
                if (event.getRepeatCount() == 0 && heldRepeatKeyCode != code) {
                    stopHeldKeyRepeat();
                    heldRepeatKeyCode = code;
                    heldRepeatStartedAt = android.os.SystemClock.elapsedRealtime();
                    handler.postDelayed(holdRepeatTick, 340L);
                    return false; // Let the initial 1-unit step go to the OS/app naturally
                } else {
                    return true;
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (heldRepeatKeyCode == code) {
                    if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        handler.removeCallbacks(pendingStopRepeat);
                        handler.postDelayed(pendingStopRepeat, 160L);
                    } else {
                        stopHeldKeyRepeat();
                    }
                }
                return false;
            }
        } else if (action == KeyEvent.ACTION_DOWN && heldRepeatKeyCode != 0) {
            stopHeldKeyRepeat();
        }

        // Mute passes straight through to the OS
        if (code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }
        armIdleSaver();
        // Aggressive Home capture: Home button on any remote returns instantly to CineTV
        if (code == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (MainActivity.foreground && MainActivity.instance != null) {
                    handler.post(() -> {
                        MainActivity act = MainActivity.instance;
                        if (act != null) act.focusShelfCenter();
                    });
                    return true;
                }
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                return true;
            }
            return true;
        }

        // Dedicated Settings / Menu key: always opens CineTV settings
        if (code == KeyEvent.KEYCODE_SETTINGS || code == 176 || code == 268) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra("action", "settings"));
                return true;
            }
            return true;
        }

        // The AirPods panel: an overlay the TV does not always give the remote to, so its keys go to it from here
        if (com.cinetv.launcher.ui.SpatialAudioOverlay.isShowing()
                && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                final int k = code;
                handler.post(() -> com.cinetv.launcher.ui.SpatialAudioOverlay.onKey(k));
            }
            return true;
        }

        // CineSUB open over a film: Back closes it, the arrows and OK belong to it; the film keeps playing.
        if (com.cinetv.launcher.ui.CineSubOverlay.isShowing()) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) com.cinetv.launcher.ui.CineSubOverlay.dismissActive();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP
                    || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) com.cinetv.launcher.ui.CineSubOverlay.onKey(code);
                return true;
            }
        }

        // Direct return to CineTV ONLY when exiting content that was directly launched from CineTV Home.
        // Never intercept Back during video playback, inside in-player settings menus, or when Nuvio/YouTube was opened as an app.
        if (CinePlayerActivity.active && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) return false;
        boolean playingNow = DeviceTweaks.isMediaPlaying(this);
        if (playingNow) {
            lastMediaPlayingAt = android.os.SystemClock.elapsedRealtime();
        }
        boolean directNuvio = isNuvioApp(lastPackage) && launchedNuvioFromCineTv;
        boolean directYouTube = isYouTubeApp(lastPackage) && launchedYouTubeFromCineTv;
        if ((directNuvio || directYouTube) && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                boolean recentlyPlaying = playingNow || (android.os.SystemClock.elapsedRealtime() - lastMediaPlayingAt < 1800L);
                if (recentlyPlaying) {
                    // Inside the video player or closing an in-player settings/subtitle/quality menu: always let the app handle Back!
                    backToNuvio = true;
                } else if (directNuvio) {
                    // In Nuvio: only return to CineTV Home when backing out from the title's detail page itself.
                    // If the user is in a stream source picker, episode list, or dialog, let Nuvio handle Back.
                    backToNuvio = !isOnNuvioDetailPage();
                } else {
                    // Direct YouTube video (e.g. Sessiz) after playback stopped: return to CineTV Home.
                    backToNuvio = false;
                }
            }
            if (backToNuvio) {
                if (event.getAction() == KeyEvent.ACTION_UP) backToNuvio = false;
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return true; // absorb DOWN so Nuvio does not pop backstack fragments
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                launchedYouTubeFromCineTv = false;
                launchedNuvioFromCineTv = false;
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                return true;
            }
            return true;
        }

        // The yellow key opens CineSUB over any player. Subtitles and Info stay with the player: they have jobs there.
        // (Asked of the activity, not lastPackage: CineTV's own overlays over Nuvio report CineTV's package.)
        if (code == KeyEvent.KEYCODE_PROG_YELLOW && !MainActivity.foreground) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                // over AlptugPlayer the player prepares a frame CineSUB can see first
                if (!(CinePlayerActivity.active && CinePlayerActivity.cinesub())) com.cinetv.launcher.ui.CineSubOverlay.trigger(this);
            }
            return true;
        }

        int source = event.getSource();
        boolean gamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        // The remote's microphone: on TVs with a physical mic button, in Nuvio it opens voice CineSUB; otherwise launches CineTV voice search
        if (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_ASSIST) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (isNuvioApp(lastPackage) && (HardwareKeys.hasRemoteWithMic(this) || isVoiceHardwareEvent(event))) {
                    com.cinetv.launcher.ui.VoiceCineSubOverlay.trigger(this);
                    return true;
                }
                DeviceTweaks.pauseMedia(this);
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra("action", "voice").putExtra("from", lastPackage));
            }
            return true;
        }
        // The blue key held for a second: a screenshot for the phone remote, on any screen. A short press keeps its
        // own job (the key's shortcut, or CineTV's own handling), now sent on release.
        if (code == KeyEvent.KEYCODE_PROG_BLUE
                && android.os.SystemClock.elapsedRealtime() - blueReplayAt > 1500) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    blueHeld = false;
                    handler.removeCallbacks(blueScreenshot);
                    handler.postDelayed(blueScreenshot, 900);
                }
                return true;
            }
            handler.removeCallbacks(blueScreenshot);
            if (blueHeld) return true;
            // Short press of Blue button: Shazam music recognition!
            com.cinetv.launcher.ai.ShazamRecognizer.recognize(this);
            return true;
        }
        // CineTV's own window handles its keys itself (settings, key capture...).
        if (getPackageName().equals(lastPackage)) return false;
        String keyAction = HardwareKeys.actionFor(this, event.getKeyCode(), event.getScanCode());
        if (keyAction == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (HardwareKeys.SIMILAR_MOVIES.equals(keyAction)) {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra("action", "similar"));
                return true;
            }
            if (!HardwareKeys.perform(this, keyAction)) {
                startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("action", "show_all_apps"));
            }
        }
        return true;
    }

    private boolean isVoiceHardwareEvent(KeyEvent event) {
        if (event == null) return false;
        InputDevice dev = event.getDevice();
        return dev != null && !dev.isVirtual();
    }

    @Override
    public void onInterrupt() {
    }
}
