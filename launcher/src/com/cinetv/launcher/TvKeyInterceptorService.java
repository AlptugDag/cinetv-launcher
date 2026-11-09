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
    public static volatile long lastNuvioActiveAt = -120_000L;


    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPackage;
    /** The Back press now in progress belongs to YouTube (decided on its DOWN, kept for its UP). */
    private boolean backToNuvio;
    private long lastMediaPlayingAt;
    private volatile long lastHomeKeyAt = -60_000L;
    private volatile long lastInstallerAutoClickAt = 0L;
    private volatile long lastUserKeyWallMs = 0L;

    public static void markNuvioActive(String pkg) {
        lastNuvioActiveAt = android.os.SystemClock.elapsedRealtime();
        TvKeyInterceptorService svc = instance;
        if (svc != null && pkg != null && !pkg.isEmpty()) {
            svc.lastPackage = pkg;
        }
    }

    public boolean hasUserInteractedSince(long startedAtWallMs) {
        return lastUserKeyWallMs > startedAtWallMs + 350L;
    }

    public String getLastPackage() {
        return lastPackage;
    }

    public boolean isInMediaOrThirdPartyApp() {
        if (MainActivity.foreground) return false;
        if (android.os.SystemClock.elapsedRealtime() - lastNuvioActiveAt < 45_000L) return true;
        String pkg = lastPackage;
        if (pkg == null || pkg.equals(getPackageName()) || isLiveTvApp(pkg)) return false;
        return isNuvioApp(pkg) || isYouTubeApp(pkg) || pkg.contains("nova") || pkg.contains("vlc") || pkg.contains("kodi")
                || pkg.contains("stremio") || (pkg.contains("player") && !isLiveTvApp(pkg));
    }

    private com.cinetv.launcher.ui.SignatureOverlay signature;
    private com.cinetv.launcher.device.AirPodsLink airPods;

    /** Idle over another app with nothing playing: show our screensaver (never over Nuvio or active video players). */
    private final Runnable idleSaver = new Runnable() {
        @Override
        public void run() {
            String top = lastPackage;
            android.util.Log.i("CineTvSaver", "idle over " + top + ", playing=" + DeviceTweaks.isMediaPlaying(TvKeyInterceptorService.this));
            // Never over stock launcher, live TV / HDMI, Nuvio / media players (tunnelled/passthrough audio does not report as music), nor while anything plays.
            if (top == null || top.equals(getPackageName()) || isTvCore(top) || isStockLauncher(top)
                    || isNuvioApp(top) || isYouTubeApp(top) || top.contains("nova") || top.contains("vlc") || top.contains("kodi")
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
    public static final String[] LIVE_TV_APPS = {
            "com.mediatek.wwtv.tvcenter", "com.mediatek.tv.oneworld.tvcenter", "com.arcelik.tv.livetv",
            "com.arcelik.tv.dtv", "org.droidtv.playtv", "org.droidtv.nettvbrowser", "org.droidtv.channels",
            "com.tcl.tv", "com.tcl.tvplayer", "com.tcl.live", "com.tcl.tvweishi", "com.tcl.esticker",
            "com.hisense.tv", "com.hisense.tv.livetv", "com.jamdeo.tv.vod",
            "com.sony.dtv.tvx", "com.sony.dtv.tvinput.tuner", "com.sony.dtv.tvplayer",
            "com.xiaomi.mitv.tvplayer", "com.mitv.tvhome.atv", "com.xiaomi.mitv.media.tvplayer",
            "com.toshiba.tv.livetv", "com.toshiba.livetv", "com.vestel.livetv", "com.vestel.tvcenter", "com.vestel.dtv",
            "com.android.tv", "com.google.android.tv", "com.droidlogic.tvinput", "org.droidlogic.tvinput",
            "com.mstar.tv.tvplayer", "com.realtek.tvplayer", "com.realtek.dtv"};

    /** When the screen last came on: other makers' live TV is only sent away in the seconds after power-on. */
    private static volatile long wokeAt = -60_000L;

    public static boolean isLiveTvApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (String p : LIVE_TV_APPS) if (p.equals(pkg)) return true;
        String lower = pkg.toLowerCase(java.util.Locale.US);
        return lower.contains("livetv") || lower.contains("tvcenter") || lower.contains("tvplayer")
                || lower.contains("tvinput") || lower.contains("tuner") || lower.contains(".dtv")
                || lower.startsWith("org.droidtv.playtv") || lower.contains("droidtv.channels");
    }

    /** Wake from standby: CineTV first, and the tuner the TV starts by itself is stopped before it shows. */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (com.cinetv.launcher.device.Uninstaller.isRemoving(TvKeyInterceptorService.this)) return;
            wokeAt = android.os.SystemClock.elapsedRealtime();
            if (!DeviceTweaks.isUnwantedTuner() || isInMediaOrThirdPartyApp()) return;
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
            if (MainActivity.foreground) {
                DeviceTweaks.syncNightMode(TvKeyInterceptorService.this);
            }
            if (!com.cinetv.launcher.device.Uninstaller.isRemoving(TvKeyInterceptorService.this)
                    && !isInMediaOrThirdPartyApp()
                    && !HomeGuard.isDefault(TvKeyInterceptorService.this)) {
                HomeGuard.ensureDefaultHome(TvKeyInterceptorService.this);
            }
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

    /**
     * True when the TV is displaying its native hardware tuner / Live TV app or embedded TV input surface.
     * CineSUB and subtitle overlays must never activate on Live TV / hardware tuner channels.
     */
    public static boolean isLiveTvOrTunerActive(Context ctx) {
        if (MainActivity.isTvInputActive()) return true;
        TvKeyInterceptorService svc = instance;
        String pkg = svc != null ? svc.lastPackage : null;
        if (pkg != null && isLiveTvApp(pkg)) return true;
        return false;
    }

    private final BroadcastReceiver cinesubReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (isLiveTvOrTunerActive(TvKeyInterceptorService.this)) return;
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
     * Applies live typing (character deletion, text addition, or full replacement, plus optional Enter submit)
     * directly to the currently focused input field in any app via Accessibility (or MainActivity EditText),
     * working 100% natively without ADB.
     */
    public static boolean applyRemoteTyping(int del, String add, String fullText, boolean enter) {
        final MainActivity ma = MainActivity.instance;
        if (ma != null && MainActivity.isInFront()) {
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] handledOnUi = {false};
            ma.runOnUiThread(() -> {
                try {
                    android.view.View fv = ma.getCurrentFocus();
                    if (fv instanceof android.widget.EditText) {
                        android.widget.EditText et = (android.widget.EditText) fv;
                        android.text.Editable ed = et.getText();
                        if (fullText != null) {
                            et.setText(fullText);
                            et.setSelection(et.getText().length());
                        } else {
                            if (del > 0 && ed != null && ed.length() > 0) {
                                int n = Math.min(del, ed.length());
                                ed.delete(ed.length() - n, ed.length());
                            }
                            if (add != null && !add.isEmpty() && ed != null) {
                                ed.append(add);
                            }
                        }
                        if (enter) {
                            et.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                            ma.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                            ma.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                        }
                        handledOnUi[0] = true;
                    }
                } catch (Throwable ignored) {
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await(250, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {}
            if (handledOnUi[0]) return true;
        }

        TvKeyInterceptorService s = instance;
        if (s == null) return false;
        try {
            android.view.accessibility.AccessibilityNodeInfo node = s.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null || !node.isEditable()) {
                node = findEditableNode(s.getRootInActiveWindow(), 0);
            }
            if (node == null) return false;
            String text;
            if (fullText != null) {
                text = fullText;
            } else {
                CharSequence cur = node.getText();
                String base = (cur == null || (android.os.Build.VERSION.SDK_INT >= 26 && node.isShowingHintText()))
                        ? "" : cur.toString();
                if (del > 0 && !base.isEmpty()) {
                    base = base.substring(0, Math.max(0, base.length() - del));
                }
                if (add != null && !add.isEmpty()) {
                    base = base + add;
                }
                text = base;
            }
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean ok = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (ok) {
                android.os.Bundle sel = new android.os.Bundle();
                sel.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length());
                sel.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
                node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
            }
            if (enter) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    node.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                } else {
                    node.performAction(0x01020054);
                }
            }
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private static android.view.accessibility.AccessibilityNodeInfo findEditableNode(android.view.accessibility.AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 35) return null;
        if (n.isEditable() && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo r = findEditableNode(n.getChild(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Adds text at the end of the focused input field in one step (a paste from the phone): typed key by key it
     * outran the key bridge's timeout and characters the TV keyboard can't spell were dropped. False when there is none.
     */
    public static boolean appendToFocused(String add) {
        if (add == null || add.isEmpty()) return false;
        return applyRemoteTyping(0, add, null, false);
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
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !isNuvioApp(root.getPackageName().toString())) {
                root = null;
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                    if (r != null && r.getPackageName() != null && isNuvioApp(r.getPackageName().toString())) {
                        root = r;
                        break;
                    }
                }
            }
            if (root == null || root.getPackageName() == null || !isNuvioApp(root.getPackageName().toString())) {
                return false;
            }
            // If Nuvio is already searching for streams or displaying the stream picker, stop polling immediately!
            android.view.accessibility.AccessibilityNodeInfo scraping = findPrefix(root,
                    new String[]{"akışlar aranıyor", "kaynaklar aranıyor", "loading streams", "scraping", "akış seç", "select stream", "kaynak seç"}, 0);
            if (scraping != null) {
                return true;
            }
            android.graphics.Rect screen = new android.graphics.Rect();
            root.getBoundsInScreen(screen);
            if (series) return seriesButton(root, preferResume, elapsedMs);

            // First priority: search for ANY resume button with prefix/contains matching
            android.view.accessibility.AccessibilityNodeInfo resumeNode = findResumeNode(root, 0);
            if (resumeNode != null) {
                return performClickOnNode(root, resumeNode, screen, "Resume/Devam");
            }

            // If user explicitly launched via Continue Watching, wait up to 2.2s for async progress before falling back to Play
            if (preferResume && elapsedMs < 2200L) {
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
        if (!ok && !b.isEmpty()) {
            ok = dispatchTap(b.centerX(), b.centerY());
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
            if (preferResume && elapsedMs < 2200L) {
                return false;
            }
            if (lastNuvioLaunchPreferResume) {
                com.cinetv.launcher.service.WatchHistory.onNuvioResumeMissing(this);
            }
            boolean ok = dispatchAccessibilityKey(KeyEvent.KEYCODE_DPAD_DOWN, false);
            android.util.Log.d("CineTvPlay", "series never watched: episode list " + ok);
            return true;
        }
        android.view.accessibility.AccessibilityNodeInfo c = n;
        for (int i = 0; c != null && !c.isClickable() && !c.isFocusable() && i < 8; i++) c = c.getParent();
        boolean ok = c != null && c.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
        if (!ok && c != null) {
            android.graphics.Rect b = new android.graphics.Rect();
            c.getBoundsInScreen(b);
            if (!b.isEmpty()) ok = dispatchTap(b.centerX(), b.centerY());
        }
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
            if (com.cinetv.launcher.ui.SpatialAudioOverlay.isShowing()
                    && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
                final int k = code;
                handler.post(() -> com.cinetv.launcher.ui.SpatialAudioOverlay.onKey(k));
                return true;
            }
            if (com.cinetv.launcher.ui.CineSubOverlay.isShowing()) {
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                    handler.post(com.cinetv.launcher.ui.CineSubOverlay::dismissActive);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP
                        || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                    final int k = code;
                    handler.post(() -> com.cinetv.launcher.ui.CineSubOverlay.onKey(k));
                    return true;
                }
            }

            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                return performGlobalAction(GLOBAL_ACTION_BACK);
            }
            if (code == KeyEvent.KEYCODE_HOME) {
                try {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    return true;
                } catch (Throwable ignored) {
                    return performGlobalAction(GLOBAL_ACTION_HOME);
                }
            }
            if (code == KeyEvent.KEYCODE_APP_SWITCH) return performGlobalAction(GLOBAL_ACTION_RECENTS);
            if (code == KeyEvent.KEYCODE_SETTINGS || code == KeyEvent.KEYCODE_MENU) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .putExtra("action", "settings"));
                return true;
            }
            if (code == KeyEvent.KEYCODE_POWER || code == KeyEvent.KEYCODE_SLEEP) {
                if (android.os.Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) return true;
                return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            }
            if (code == KeyEvent.KEYCODE_DEL) {
                return applyRemoteTyping(1, "", null, false);
            }
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                if (applyRemoteTyping(0, String.valueOf(code - KeyEvent.KEYCODE_0), null, false)) return true;
            }

            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            android.view.accessibility.AccessibilityNodeInfo focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused == null && root != null) focused = findFirstFocusedOrFocusable(root, 0);

            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (focused != null) {
                    if (focused.isEditable() && !longPress) {
                        if (android.os.Build.VERSION.SDK_INT >= 30
                                && focused.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())) {
                            return true;
                        }
                    }
                    int action = longPress
                            ? android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK
                            : android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
                    if (focused.performAction(action)) return true;
                    android.view.accessibility.AccessibilityNodeInfo p = focused.getParent();
                    while (p != null) {
                        if ((longPress ? p.isLongClickable() : p.isClickable()) && p.performAction(action)) return true;
                        p = p.getParent();
                    }
                    android.graphics.Rect b = new android.graphics.Rect();
                    focused.getBoundsInScreen(b);
                    if (!b.isEmpty() && b.centerX() > 0 && b.centerY() > 0) {
                        return longPress ? dispatchLongTap(b.centerX(), b.centerY()) : dispatchTap(b.centerX(), b.centerY());
                    }
                }
                return false;
            }

            if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_PAGE_UP) {
                if (scrollActiveContainer(root, focused, false)) return true;
                code = KeyEvent.KEYCODE_DPAD_UP;
            } else if (code == KeyEvent.KEYCODE_CHANNEL_DOWN || code == KeyEvent.KEYCODE_PAGE_DOWN) {
                if (scrollActiveContainer(root, focused, true)) return true;
                code = KeyEvent.KEYCODE_DPAD_DOWN;
            }

            int dir = 0;
            if (code == KeyEvent.KEYCODE_DPAD_UP) dir = android.view.View.FOCUS_UP;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dir = android.view.View.FOCUS_DOWN;
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT) dir = android.view.View.FOCUS_LEFT;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dir = android.view.View.FOCUS_RIGHT;

            if (dir != 0) {
                if (focused != null) {
                    android.view.accessibility.AccessibilityNodeInfo next = focused.focusSearch(dir);
                    if (next != null && (next.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
                            || next.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))) {
                        return true;
                    }
                    if (root != null) {
                        android.view.accessibility.AccessibilityNodeInfo spatial = findNearestFocusableInDirection(root, focused, dir);
                        if (spatial != null && (spatial.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
                                || spatial.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))) {
                            return true;
                        }
                    }
                }
                boolean forward = (dir == android.view.View.FOCUS_DOWN || dir == android.view.View.FOCUS_RIGHT);
                if (scrollActiveContainer(root, focused, forward)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static android.view.accessibility.AccessibilityNodeInfo findFirstFocusedOrFocusable(
            android.view.accessibility.AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30) return null;
        if (n.isFocused() || n.isAccessibilityFocused()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo r = findFirstFocusedOrFocusable(n.getChild(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private static boolean scrollActiveContainer(android.view.accessibility.AccessibilityNodeInfo root,
                                                 android.view.accessibility.AccessibilityNodeInfo focused,
                                                 boolean forward) {
        int scrollAction = forward
                ? android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                : android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        android.view.accessibility.AccessibilityNodeInfo p = focused;
        while (p != null) {
            if (p.isScrollable() && p.performAction(scrollAction)) return true;
            p = p.getParent();
        }
        if (root != null) {
            return scrollFirstScrollable(root, scrollAction, 0);
        }
        return false;
    }

    private static boolean scrollFirstScrollable(android.view.accessibility.AccessibilityNodeInfo n, int scrollAction, int depth) {
        if (n == null || depth > 25) return false;
        if (n.isScrollable() && n.isVisibleToUser() && n.performAction(scrollAction)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            if (scrollFirstScrollable(n.getChild(i), scrollAction, depth + 1)) return true;
        }
        return false;
    }

    private static android.view.accessibility.AccessibilityNodeInfo findNearestFocusableInDirection(
            android.view.accessibility.AccessibilityNodeInfo root,
            android.view.accessibility.AccessibilityNodeInfo current,
            int dir) {
        if (root == null || current == null) return null;
        android.graphics.Rect curRect = new android.graphics.Rect();
        current.getBoundsInScreen(curRect);
        if (curRect.isEmpty()) return null;
        int cx = curRect.centerX(), cy = curRect.centerY();
        android.view.accessibility.AccessibilityNodeInfo[] best = new android.view.accessibility.AccessibilityNodeInfo[1];
        double[] bestScore = {Double.MAX_VALUE};
        collectBestSpatialCandidate(root, current, cx, cy, dir, best, bestScore, 0);
        return best[0];
    }

    private static void collectBestSpatialCandidate(
            android.view.accessibility.AccessibilityNodeInfo n,
            android.view.accessibility.AccessibilityNodeInfo current,
            int cx, int cy, int dir,
            android.view.accessibility.AccessibilityNodeInfo[] best,
            double[] bestScore, int depth) {
        if (n == null || depth > 32) return;
        if (!n.equals(current) && n.isVisibleToUser() && n.isEnabled() && (n.isFocusable() || n.isClickable())) {
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            if (r.width() > 8 && r.height() > 8) {
                int nx = r.centerX(), ny = r.centerY();
                int dx = nx - cx, dy = ny - cy;
                boolean inDir = false;
                double primary = 0, orthogonal = 0;
                if (dir == android.view.View.FOCUS_UP && dy < -8) {
                    inDir = true; primary = -dy; orthogonal = Math.abs(dx);
                } else if (dir == android.view.View.FOCUS_DOWN && dy > 8) {
                    inDir = true; primary = dy; orthogonal = Math.abs(dx);
                } else if (dir == android.view.View.FOCUS_LEFT && dx < -8) {
                    inDir = true; primary = -dx; orthogonal = Math.abs(dy);
                } else if (dir == android.view.View.FOCUS_RIGHT && dx > 8) {
                    inDir = true; primary = dx; orthogonal = Math.abs(dy);
                }
                if (inDir) {
                    double score = primary + orthogonal * 2.3;
                    if (score < bestScore[0]) {
                        bestScore[0] = score;
                        best[0] = n;
                    }
                }
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collectBestSpatialCandidate(n.getChild(i), current, cx, cy, dir, best, bestScore, depth + 1);
        }
    }

    /** Injects a screen tap at (x, y) via AccessibilityService gesture API when KeyBridge is unavailable. */
    public boolean dispatchTap(int x, int y) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(Math.max(0, x), Math.max(0, y));
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 55);
            return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Performs an AirMouse click at screen coordinates (x, y) in any app without requiring ADB:
     * 1. Dispatches a hardware-level Accessibility gesture tap at (x, y) (enabled via canPerformGestures="true").
     * 2. Also resolves the deepest clickable/focusable AccessibilityNodeInfo at (x, y) and triggers ACTION_FOCUS + ACTION_CLICK
     *    for TV apps that only listen to D-pad / Accessibility click actions rather than touch events.
     */
    public boolean performAirMouseClick(int x, int y) {
        boolean tapped = dispatchTap(x, y);
        boolean clickedNode = false;
        try {
            android.view.accessibility.AccessibilityNodeInfo bestNode = null;
            long[] bestArea = new long[]{Long.MAX_VALUE};
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    if (w.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                    android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                    if (r != null) {
                        android.view.accessibility.AccessibilityNodeInfo hit = findSmallestClickableAt(r, x, y, bestArea, 0);
                        if (hit != null) bestNode = hit;
                    }
                }
            }
            if (bestNode == null) {
                android.view.accessibility.AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null) {
                    bestNode = findSmallestClickableAt(r, x, y, bestArea, 0);
                }
            }
            if (bestNode != null) {
                if (bestNode.isFocusable()) {
                    bestNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS);
                }
                if (!tapped) {
                    clickedNode = bestNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        } catch (Throwable ignored) {
        }
        return tapped || clickedNode;
    }

    private static android.view.accessibility.AccessibilityNodeInfo findSmallestClickableAt(
            android.view.accessibility.AccessibilityNodeInfo node, int x, int y, long[] bestArea, int depth) {
        if (node == null || depth > 36 || !node.isVisibleToUser()) return null;
        android.graphics.Rect b = new android.graphics.Rect();
        node.getBoundsInScreen(b);
        if (!b.contains(x, y)) return null;

        android.view.accessibility.AccessibilityNodeInfo best = null;
        if (node.isClickable() || node.isEditable()) {
            long area = (long) Math.max(1, b.width()) * Math.max(1, b.height());
            if (area <= bestArea[0]) {
                bestArea[0] = area;
                best = node;
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            android.view.accessibility.AccessibilityNodeInfo childBest = findSmallestClickableAt(node.getChild(i), x, y, bestArea, depth + 1);
            if (childBest != null) best = childBest;
        }
        if (best == null && node.isFocusable()) {
            long area = (long) Math.max(1, b.width()) * Math.max(1, b.height());
            if (area <= bestArea[0]) {
                bestArea[0] = area;
                best = node;
            }
        }
        return best;
    }

    /** Injects a long-press tap at (x, y) via AccessibilityService gesture API when KeyBridge is unavailable. */
    public boolean dispatchLongTap(int x, int y) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(Math.max(0, x), Math.max(0, y));
            android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                    new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 620);
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
            new Thread(() -> com.cinetv.launcher.remote.KeyBridge.warmUp(this), "cinetv-keys-boot").start();
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
        if (event == null || event.getPackageName() == null) return;
        int eventType = event.getEventType();
        String pkg = event.getPackageName().toString();
        if (pkg.contains("packageinstaller") || pkg.contains("installer") || pkg.contains("permissioncontroller")
                || ((pkg.contains("settings") || pkg.contains("tv.settings"))
                && com.cinetv.launcher.service.AppUpdater.isSilentInstallPending(this))) {
            maybeAutoConfirmPackageInstaller(event);
        }
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if ("com.android.systemui".equals(pkg) || "android".equals(pkg) || "com.arcelik.smartremoteservice".equals(pkg)) return;
        // While CineTV removes itself it must not pull the viewer back from the TV's own Home.
        if (com.cinetv.launcher.device.Uninstaller.isRemoving(this)) { lastPackage = pkg; return; }

        // CRITICAL: When SignatureOverlay, CineSubOverlay, VoiceCineSubOverlay, or a CineTV toast appears over Nuvio
        // or another foreground app, Android fires TYPE_WINDOW_STATE_CHANGED with pkg = "com.cinetv.launcher" even
        // though MainActivity is in the background (!MainActivity.foreground && !CinePlayerActivity.active).
        // Never treat our own overlay windows as an app switch to CineTV, otherwise lastPackage becomes
        // "com.cinetv.launcher", inActiveMediaApp becomes false, and background TV tuner/system events pull the viewer
        // out of Nuvio to the Home screen!
        if (getPackageName().equals(pkg) && !MainActivity.foreground && !CinePlayerActivity.active) {
            return;
        }

        if (isNuvioApp(pkg)) {
            markNuvioActive(pkg);
        }

        boolean recentlyInNuvio = !MainActivity.foreground
                && (android.os.SystemClock.elapsedRealtime() - lastNuvioActiveAt < 45_000L);
        boolean inActiveMediaApp = recentlyInNuvio || (lastPackage != null && (isNuvioApp(lastPackage) || isYouTubeApp(lastPackage)
                || lastPackage.contains("nova") || lastPackage.contains("vlc") || lastPackage.contains("kodi")
                || lastPackage.contains("stremio") || lastPackage.contains("player")));
        boolean bootOrWakeWindow = DeviceTweaks.isUnwantedBootTuner()
                || (android.os.SystemClock.elapsedRealtime() - wokeAt < 15_000L);
        boolean homeJustPressed = android.os.SystemClock.elapsedRealtime() - lastHomeKeyAt < 3_500L;

        CharSequence cls = event.getClassName();
        if (cls != null) {
            String cName = cls.toString().toLowerCase(java.util.Locale.US);
            if ((cName.contains("screensaver") || cName.contains("dream")) && !getPackageName().equals(pkg) && !isSystemTool(pkg)) {
                if (!inActiveMediaApp && !DeviceTweaks.isMediaPlaying(this)) {
                    startActivity(new Intent(this, ScreensaverActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    return;
                }
            }
        }

        if (isStockLauncher(pkg)) {
            // Never yank the user out of Nuvio or another active media app if a background stock launcher service emits a window event
            if (inActiveMediaApp && !homeJustPressed) {
                return;
            }
            // Stock TV launcher (Xiaomi / Google TV) intercepted HOME key or boot: re-lock CineTV as Home and redirect immediately
            HomeGuard.ensureDefaultHome(this);
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            return;
        }

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
                if (inActiveMediaApp) {
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
                break;
            case "com.google.android.katniss":
            case "com.google.android.googlequicksearchbox":
            case "com.google.android.apps.googleassistant":
                // If the viewer is in Nuvio or another media app, NEVER pull them back to MainActivity when Katniss/Search
                // wakes in the background or when the mic button is pressed!
                if (inActiveMediaApp) {
                    if (android.os.SystemClock.elapsedRealtime() - MainActivity.ourVoiceAt > 15_000) {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        if (isNuvioApp(lastPackage) && !com.cinetv.launcher.ui.VoiceCineSubOverlay.isShowing()) {
                            handler.postDelayed(() -> com.cinetv.launcher.ui.VoiceCineSubOverlay.trigger(this), 250);
                        }
                    }
                    return;
                }
                if (android.os.SystemClock.elapsedRealtime() - MainActivity.ourVoiceAt > 15_000) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    DeviceTweaks.pauseMedia(this);
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    handler.postDelayed(() -> startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            .putExtra("action", "voice")), 350);
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

        if (!isAppSwitch(pkg)) {
            // Non-app window (keyboard, system dialog, permission overlay, subtitle service): never overwrite lastPackage!
            return;
        }

        if (lastPackage != null && !lastPackage.equals(pkg)) {
            // Never force-stop Nuvio, Nova Player, or YouTube in the background: force-stopping Nuvio when an
            // external player or system overlay briefly opens causes Nuvio to crash/close on Arçelik TVs.
            final String left = lastPackage;
            if (!isNuvioApp(left) && !isYouTubeApp(left) && !left.contains("nova") && !left.equals(getPackageName())) {
                handler.postDelayed(() -> {
                    if (!left.equals(lastPackage)) MemoryOptimizer.kill(this, left);
                }, 60_000L);
            }
            if (getPackageName().equals(pkg) && MainActivity.foreground) {
                handler.postDelayed(() -> MemoryOptimizer.sweepAsync(this), 2000);
            }
        }
        lastPackage = pkg;
        if (isNuvioApp(pkg)) {
            com.cinetv.launcher.remote.TvWebRemoteServer.start(this);
        }
        armIdleSaver();
        if (signature == null) signature = new com.cinetv.launcher.ui.SignatureOverlay(this);
        if (isNuvioApp(pkg)) signature.show();
        else if (isAppSwitch(pkg)) signature.hide();
        if (isLiveTvApp(pkg)) {
            com.cinetv.launcher.ui.CineSubOverlay.dismissActive();
            com.cinetv.launcher.ui.VoiceCineSubOverlay.dismissActive();
        }
    }

    /**
     * Automatically clicks the positive confirmation button ("Yükle" / "Install" / "Güncelle" / "Update" / "Tamam" / "OK")
     * on Android's PackageInstaller dialog ONLY when CineTV itself initiated a silent self-update or self-uninstall.
     */
    private void maybeAutoConfirmPackageInstaller(AccessibilityEvent event) {
        try {
            boolean updating = com.cinetv.launcher.service.AppUpdater.isSilentInstallPending(this);
            boolean removing = com.cinetv.launcher.device.Uninstaller.isRemoving(this);
            if (!updating && !removing) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastInstallerAutoClickAt < 450L) return;
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null && event != null) root = event.getSource();
            if (root != null) {
                if (clickInstallerPositiveNode(root, 0)) {
                    lastInstallerAutoClickAt = now;
                    return;
                }
            }
            // Retry shortly in case the TV PackageInstaller finishes inflating its buttons after window transition
            handler.postDelayed(() -> {
                try {
                    boolean up2 = com.cinetv.launcher.service.AppUpdater.isSilentInstallPending(this);
                    boolean rm2 = com.cinetv.launcher.device.Uninstaller.isRemoving(this);
                    if (!up2 && !rm2) return;
                    long t2 = android.os.SystemClock.elapsedRealtime();
                    if (t2 - lastInstallerAutoClickAt < 450L) return;
                    android.view.accessibility.AccessibilityNodeInfo r2 = getRootInActiveWindow();
                    if (r2 != null && clickInstallerPositiveNode(r2, 0)) {
                        lastInstallerAutoClickAt = t2;
                    }
                } catch (Throwable ignored) {
                }
            }, 260L);
        } catch (Throwable ignored) {
        }
    }

    private boolean clickInstallerPositiveNode(android.view.accessibility.AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 16) return false;
        CharSequence idCs = node.getViewIdResourceName();
        String id = idCs != null ? idCs.toString().toLowerCase(java.util.Locale.US) : "";
        CharSequence textCs = node.getText();
        CharSequence descCs = node.getContentDescription();
        java.util.Locale trLoc = new java.util.Locale("tr");
        String text = textCs != null ? textCs.toString().trim().toLowerCase(trLoc) : "";
        String desc = descCs != null ? descCs.toString().trim().toLowerCase(trLoc) : "";
        if (id.endsWith(":id/button1") || id.endsWith(":id/ok_button") || id.endsWith(":id/install_confirm_question")
                || id.endsWith(":id/confirm_button") || id.endsWith(":id/guidedactions_item_content")
                || text.equals("yükle") || text.equals("güncelle") || text.equals("install") || text.equals("update")
                || text.equals("tamam") || text.equals("ok") || text.equals("kaldır") || text.equals("uninstall")
                || text.equals("evet") || text.equals("yes") || text.equals("bitti") || text.equals("done")
                || text.equals("devam") || text.equals("continue") || text.equals("izin ver") || text.equals("allow")
                || desc.equals("yükle") || desc.equals("güncelle") || desc.equals("install") || desc.equals("update")
                || desc.equals("tamam") || desc.equals("ok") || desc.equals("kaldır") || desc.equals("uninstall")) {
            android.view.accessibility.AccessibilityNodeInfo target = node;
            while (target != null && !target.isClickable()) {
                target = target.getParent();
            }
            if (target != null && target.isEnabled()) {
                if (target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
                target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS);
                target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SELECT);
                android.graphics.Rect b = new android.graphics.Rect();
                target.getBoundsInScreen(b);
                if (!b.isEmpty() && dispatchTap(b.centerX(), b.centerY())) {
                    return true;
                }
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            if (clickInstallerPositiveNode(node.getChild(i), depth + 1)) return true;
        }
        if (node.isScrollable()) {
            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
        return false;
    }

    /** The TV's own live TV / inputs app (Arçelik/Beko: wwtv, Grundig: oneworld). */
    private static boolean isTvCore(String pkg) {
        return pkg.startsWith("com.mediatek.wwtv") || pkg.startsWith("com.mediatek.tv.oneworld.tvcenter");
    }

    /** Stock TV launchers (Xiaomi Mi TV, Google TV, Android TV, Next Media Box, Amlogic/MStar/Rockchip home launchers). */
    private boolean isStockLauncher(String pkg) {
        if (pkg == null || pkg.equals(getPackageName())) return false;
        // Settings, accessibility, system services (droidlogic subtitle/overlay, mstar tv service) and file managers are never the launcher.
        if (isSystemTool(pkg)) return false;
        if (pkg.contains("subtitleservice") || pkg.contains("overlay") || pkg.contains("systemui")
                || pkg.contains("tvinput") || pkg.contains("cec") || pkg.contains("audio") || pkg.contains("bluetooth")) {
            return false;
        }
        if (pkg.equals("com.google.android.tvlauncher")
                || pkg.equals("com.google.android.apps.tv.launcherx")
                || pkg.equals("com.xiaomi.mitv.tvhome")
                || pkg.equals("com.android.tv.launcher")
                || pkg.equals("com.droidlogic.tvlauncher")
                || pkg.equals("com.droidlogic.mboxlauncher")
                || pkg.equals("com.ampere.launcher")
                || pkg.equals("com.ugoos.launcher")
                || pkg.contains("tvlauncher")
                || pkg.contains("mboxlauncher")
                || pkg.contains("mitv.tvhome")
                || pkg.contains("homeshell")
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
        if (pkg.equals(getPackageName())) return MainActivity.foreground || CinePlayerActivity.active;
        if (isTvCore(pkg) || isStockLauncher(pkg)) return true;
        if (pkg.contains("inputmethod") || pkg.contains("katniss") || pkg.contains("subtitleservice")
                || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")) return false;
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
    private long lastVolumeDownAt = 0L;
    private long syntheticChannelReplayAt = 0L;
    private final Runnable pendingStopRepeat = this::stopHeldKeyRepeat;
    private final java.util.concurrent.ExecutorService volumeRepeatExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "cinetv-vol-repeat");
                t.setDaemon(true);
                return t;
            });

    private void adjustStreamVolumeDirect(int dir) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int before = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI);
                int after = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                if (after == before) {
                    am.adjustSuggestedStreamVolume(dir, android.media.AudioManager.USE_DEFAULT_STREAM_TYPE, android.media.AudioManager.FLAG_SHOW_UI);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void adjustTvVolume(final int keyCode, final int dir) {
        adjustStreamVolumeDirect(dir);
        volumeRepeatExecutor.execute(() -> {
            try {
                com.cinetv.launcher.remote.KeyBridge.key(TvKeyInterceptorService.this, keyCode, false);
            } catch (Throwable ignored) {
            }
        });
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
                adjustTvVolume(k, dir);
                handler.postDelayed(this, 95L);
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
                if (pkg == null || pkg.equals(getPackageName()) || isLiveTvApp(pkg)) return;
                boolean nuvio = isNuvioApp(pkg);
                boolean player = nuvio || isYouTubeApp(pkg) || pkg.contains("nova") || pkg.contains("vlc")
                        || pkg.contains("mxtech") || pkg.contains("kodi") || (!isLiveTvApp(pkg) && (pkg.contains("player") || pkg.contains("video")));
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
            // Never walk AccessibilityNodeInfo trees on D-pad navigation keys inside Nuvio!
            // Rapidly traversing getRootInActiveWindow() during D-pad scrolling races with Nuvio's UI renderer
            // and causes Nuvio to crash while browsing catalogs.
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return;
            if (!isNuvioApp(lastPackage) || DeviceTweaks.isMediaPlaying(this)) return;
            android.view.accessibility.AccessibilityNodeInfo focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused == null) return;
            CharSequence t = focused.getText() != null ? focused.getText() : focused.getContentDescription();
            String ft = t != null ? t.toString().trim() : "";
            try { focused.recycle(); } catch (Throwable ignored) {}
            if (isNuvioRemoveLabel(ft)) {
                handler.postDelayed(() -> com.cinetv.launcher.service.WatchHistory.syncWithNuvio(this), 900L);
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
            lastUserKeyWallMs = System.currentTimeMillis();
            if (isNuvioApp(lastPackage)) {
                lastNuvioActiveAt = lastNavKeyAt;
            }
        }
        // Mute passes straight through to the OS without any interception
        if (code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }

        // Volume Up/Down: NEVER return true (never swallow volume keys!).
        // Returning true in AccessibilityService.onKeyEvent blocks the key from reaching PhoneWindowManager / OEM TV
        // hardware amplifiers / HDMI-CEC. At the same time, Android's AccessibilityInputFilter sets POLICY_FLAG_INJECTED
        // on filtered keys, which stops InputDispatcher from synthesizing repeats while a button is held down.
        // Therefore we always return false AND drive continuous repeat steps when held.
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Synthetic keys injected by KeyInjector (KeyCharacterMap.VIRTUAL_KEYBOARD == -1) pass straight through
            // to PhoneWindowManager without altering our physical button hold state.
            if (event.getDeviceId() < 0) {
                return false;
            }
            long now = android.os.SystemClock.elapsedRealtime();
            int dir = (code == KeyEvent.KEYCODE_VOLUME_UP)
                    ? android.media.AudioManager.ADJUST_RAISE
                    : android.media.AudioManager.ADJUST_LOWER;
            if (action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(pendingStopRepeat);
                boolean isRapidPulse = (heldRepeatKeyCode == code && (now - lastVolumeDownAt) < 280L);
                lastVolumeDownAt = now;
                if (event.getRepeatCount() > 0 || isRapidPulse) {
                    adjustStreamVolumeDirect(dir);
                    handler.removeCallbacks(holdRepeatTick);
                    handler.postDelayed(holdRepeatTick, 200L);
                    return false;
                }
                stopHeldKeyRepeat();
                heldRepeatKeyCode = code;
                heldRepeatStartedAt = now;
                handler.postDelayed(holdRepeatTick, 230L);
                return false;
            } else if (action == KeyEvent.ACTION_UP) {
                if (heldRepeatKeyCode == code) {
                    // Stop synthetic Bluetooth hold timer immediately on physical release, while keeping
                    // heldRepeatKeyCode for 190ms so IR remotes pulsing DOWN/UP every ~108ms are detected as rapid pulses.
                    handler.removeCallbacks(holdRepeatTick);
                    handler.removeCallbacks(pendingStopRepeat);
                    handler.postDelayed(pendingStopRepeat, 190L);
                }
                return false;
            }
            return false;
        }

        // Holiday celebration modal: any non-volume key closes the celebration overlay cleanly
        if (com.cinetv.launcher.ui.CelebrationOverlay.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN) {
                com.cinetv.launcher.ui.CelebrationOverlay.dismiss();
            }
            return true;
        }
        if (com.cinetv.launcher.device.Uninstaller.isRemoving(this)) return false;

        // Continuous hold-to-repeat for Channel Up/Down across all TV remotes:
        if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            if (event.getDeviceId() < 0 || android.os.SystemClock.elapsedRealtime() - syntheticChannelReplayAt < 120L) {
                return false;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(pendingStopRepeat);
                if (event.getRepeatCount() == 0 && heldRepeatKeyCode != code) {
                    stopHeldKeyRepeat();
                    heldRepeatKeyCode = code;
                    heldRepeatStartedAt = android.os.SystemClock.elapsedRealtime();
                    handler.postDelayed(holdRepeatTick, 320L);
                    return false;
                }
                return false;
            } else if (action == KeyEvent.ACTION_UP) {
                if (heldRepeatKeyCode == code) {
                    stopHeldKeyRepeat();
                }
                return false;
            }
        } else if (action == KeyEvent.ACTION_DOWN && heldRepeatKeyCode != 0) {
            stopHeldKeyRepeat();
        }
        armIdleSaver();
        // Aggressive Home capture: Home button on any remote returns instantly to CineTV
        if (code == KeyEvent.KEYCODE_HOME) {
            lastHomeKeyAt = android.os.SystemClock.elapsedRealtime();
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

        // Never hijack Back inside Nuvio: Nuvio handles its own player, source picker, detail page, and exit naturally.
        if (CinePlayerActivity.active && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) return false;
        boolean playingNow = DeviceTweaks.isMediaPlaying(this);
        if (playingNow) {
            lastMediaPlayingAt = android.os.SystemClock.elapsedRealtime();
        }
        boolean directYouTube = isYouTubeApp(lastPackage) && launchedYouTubeFromCineTv;
        if (directYouTube && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                boolean recentlyPlaying = playingNow || (android.os.SystemClock.elapsedRealtime() - lastMediaPlayingAt < 1800L);
                backToNuvio = recentlyPlaying;
            }
            if (backToNuvio) {
                if (event.getAction() == KeyEvent.ACTION_UP) backToNuvio = false;
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                launchedYouTubeFromCineTv = false;
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                return true;
            }
            return true;
        }

        // The yellow key opens CineSUB over any player, NEVER on Live TV / hardware tuner!
        if (code == KeyEvent.KEYCODE_PROG_YELLOW && !MainActivity.foreground) {
            if (isLiveTvOrTunerActive(this)) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                // over AlptugPlayer the player prepares a frame CineSUB can see first
                if (!(CinePlayerActivity.active && CinePlayerActivity.cinesub())) com.cinetv.launcher.ui.CineSubOverlay.trigger(this);
            }
            return true;
        }

        int source = event.getSource();
        boolean gamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        // The remote's microphone: in Nuvio it always opens voice CineSUB in-place without leaving Nuvio; otherwise launches CineTV voice search
        if (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_ASSIST) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (!isLiveTvOrTunerActive(this) && (isNuvioApp(lastPackage) || (!MainActivity.foreground && isInMediaOrThirdPartyApp()))) {
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
        // The blue key held for a second: a screenshot for the phone remote, on any screen.
        if (HardwareKeys.isBlueKey(code, event.getScanCode())
                && android.os.SystemClock.elapsedRealtime() - blueReplayAt > 1500) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    blueHeld = false;
                    handler.removeCallbacks(blueScreenshot);
                    handler.postDelayed(blueScreenshot, 900);
                }
                return false;
            }
            handler.removeCallbacks(blueScreenshot);
            if (blueHeld) return true;
        }
        // CineTV's own window handles its keys itself (settings, key capture...).
        if (MainActivity.foreground) return false;
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
