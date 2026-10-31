package com.cinetv.launcher.party;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.remote.RelayHost;
import com.cinetv.launcher.remote.RemotePairing;
import com.cinetv.launcher.service.PosterCache;
import com.cinetv.launcher.ui.Brand;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The TV half of CineParty: one full-screen canvas that draws whichever game is running from the
 * room's state. Glass panels, a slow aurora in the game's colour, real posters and stills, and a
 * revolver cylinder for CineRoulette. It only redraws while something moves (a timer, the cylinder,
 * a reveal), so an idle question costs nothing; weak TVs get the same layout without the aurora.
 */
public final class PartyStageView extends View implements CinePartyManager.Listener {

    public interface Host {
        void exit();
        void play(String title);
    }

    private static final float W = 1920f, H = 1080f;
    private final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
    private final boolean lite;
    private final CinePartyManager party;
    private Host host;
    private JSONObject st = new JSONObject();
    private String mode = "lobby", phase = "setup";
    private int accent = Brand.CINEQUIZ_PURPLE;
    private long stateAt = SystemClock.elapsedRealtime();

    private final Typeface black, bold, medium;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Matrix m = new Matrix();
    private final ColorMatrixColorFilter grey;

    private final Map<String, Bitmap> pics = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private Bitmap qr;

    // Roulette animation
    private float cylAngle, cylFrom, cylTo;
    private long cylStart, cylDur = 1;
    private int lastAligned = -1;
    private String lastShot = "";
    private long flashAt, shotAt;
    private long revealAt, solvedAt, phaseAt;

    public PartyStageView(Context ctx) {
        super(ctx);
        party = CinePartyManager.get(ctx);
        lite = PerformanceProfile.lowMemory(ctx);
        black = FontManager.getBlack(ctx);
        bold = FontManager.getBold(ctx);
        medium = FontManager.getMedium(ctx);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.05f);
        cm.postConcat(new ColorMatrix(new float[]{0.5f, 0, 0, 0, 0, 0, 0.5f, 0, 0, 0, 0, 0, 0.5f, 0, 0, 0, 0, 0, 1, 0}));
        grey = new ColorMatrixColorFilter(cm);
        setFocusable(true);
        setFocusableInTouchMode(true);
        stroke.setStyle(Paint.Style.STROKE);
    }

    public void setHost(Host h) {
        host = h;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        party.addListener(this);
        onGameStateChanged(party.state(null));
        requestFocus();
        new Thread(() -> {
            String url = RemotePairing.pairingUrl(getContext(), "party");
            if (url == null) url = RelayHost.pairingUrl(getContext(), "party");
            final Bitmap code = RemotePairing.qr(url, 360, 0xFF0B0C10, Color.WHITE);
            post(() -> {
                qr = code;
                invalidate();
            });
        }, "cinetv-party-qr").start();
    }

    @Override
    protected void onDetachedFromWindow() {
        party.removeListener(this);
        loader.shutdownNow();
        super.onDetachedFromWindow();
    }

    @Override
    public void onGameStateChanged(JSONObject s) {
        String oldPhase = phase;
        st = s;
        mode = s.optString("mode", "lobby");
        phase = s.optString("phase", "setup");
        stateAt = SystemClock.elapsedRealtime();
        accent = "roulette".equals(mode) ? Brand.CINEROULETTE_BLUE : "sessiz".equals(mode) ? Brand.CINESESSIZ_GOLD : Brand.CINEQUIZ_PURPLE;
        if (!phase.equals(oldPhase)) {
            phaseAt = stateAt;
            if ("reveal".equals(phase)) revealAt = stateAt;
            if ("solved".equals(phase)) solvedAt = stateAt;
        }
        JSONObject ro = s.optJSONObject("roulette");
        if (ro != null) {
            int al = ro.optInt("aligned");
            String shot = ro.optString("last");
            if (al != lastAligned) {
                float target = -al * 60f;
                float from = cylAngle;
                if ("spin".equals(shot) || lastAligned < 0) target -= 720f;
                while (target > from) target -= 360f;
                if (from - target > 1080f) target += 360f * (int) ((from - target - 720f) / 360f);
                cylFrom = from;
                cylTo = target;
                cylStart = stateAt;
                cylDur = "spin".equals(shot) || lastAligned < 0 ? 1500 : 520;
                lastAligned = al;
            }
            if (!shot.equals(lastShot)) {
                if ("bang".equals(shot)) flashAt = stateAt;
                if ("click".equals(shot) || "bang".equals(shot)) shotAt = stateAt;
                lastShot = shot;
            }
        }
        invalidate();
    }

    // ---- Input -----------------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                party.close();
                if (host != null) host.exit();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                onOk();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if ("roulette".equals(mode)) party.rouletteSpin(null);
                if ("sessiz".equals(mode) && "card".equals(phase)) party.sessizPass(null);
                return true;
            default:
                return super.onKeyDown(keyCode, e);
        }
    }

    private void onOk() {
        if ("finished".equals(phase)) {
            String film = party.chosenFilm();
            if (!film.isEmpty() && host != null) host.play(film);
            else party.open(CinePartyManager.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
            return;
        }
        switch (mode) {
            case "quiz":
                if ("setup".equals(phase)) party.quizStart(null);
                break;
            case "roulette":
                if ("setup".equals(phase)) party.rouletteStart(null);
                else if ("turn".equals(phase)) party.roulettePull(null);
                break;
            case "sessiz":
                if ("setup".equals(phase)) party.sessizStart(null);
                break;
            default:
                break;
        }
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    /**
     * What the next frame needs: motion (cylinder, flip, flash) redraws every vsync; a ticking timer
     * or the aurora only a few times a second, which a TV cannot tell apart and costs a tenth.
     */
    private boolean smooth, ticking;

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth() / W, getHeight() / H);
        c.save();
        c.translate((getWidth() - W * s) / 2f, (getHeight() - H * s) / 2f);
        c.scale(s, s);
        long now = SystemClock.elapsedRealtime();
        smooth = false;
        ticking = drawBackdrop(c, now);
        drawHeader(c);
        drawScores(c);
        boolean motion;
        switch (mode) {
            case "quiz": motion = drawQuiz(c, now); break;
            case "roulette": motion = drawRoulette(c, now); break;
            case "sessiz": motion = drawSessiz(c, now); break;
            default: drawLobby(c); motion = false; break;
        }
        c.restore();
        if (smooth) postInvalidateOnAnimation();
        else if (motion || ticking) postInvalidateDelayed(lite ? 250 : 90);
    }

    private boolean drawBackdrop(Canvas c, long now) {
        c.drawColor(0xFF05060A);
        float t = lite ? 0 : (now % 40000L) / 40000f;
        float ax = 380 + 220 * (float) Math.sin(t * Math.PI * 2), ay = 260 + 120 * (float) Math.cos(t * Math.PI * 2);
        fill.setShader(new RadialGradient(ax, ay, 1000, withA(accent, 96), 0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        fill.setShader(new RadialGradient(W - ax * 0.7f, H - ay * 0.6f, 1000, withA(0xFF2A3BFF, 46), 0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        fill.setShader(new LinearGradient(0, 0, 0, H, 0x00000000, 0xB0000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        fill.setShader(null);
        return !lite;
    }

    private void drawHeader(Canvas c) {
        String tail;
        int col;
        switch (mode) {
            case "roulette": tail = "Roulette"; col = Brand.CINEROULETTE_BLUE; break;
            case "sessiz": tail = "Sessiz"; col = Brand.CINESESSIZ_GOLD; break;
            case "quiz": tail = "Quiz"; col = Brand.CINEQUIZ_PURPLE; break;
            default: tail = "Party"; col = Brand.TV_RED; break;
        }
        text.setTypeface(black);
        text.setTextSize(68);
        text.setLetterSpacing(0.04f);
        text.setColor(0xFFF5F5F7);
        text.setShadowLayer(26, 0, 0, withA(col, 120));
        float x = 96, y = 128;
        c.drawText("Cine", x, y, text);
        float w = text.measureText("Cine");
        text.setColor(col);
        c.drawText(tail, x + w, y, text);
        text.clearShadowLayer();
        text.setLetterSpacing(0);
        String stat = statusLine();
        if (!stat.isEmpty()) {
            text.setTypeface(medium);
            text.setTextSize(26);
            float tw = text.measureText(stat);
            float px = x + w + measure(black, 68, tail) + 34;
            glassRect(c, px, 82, px + tw + 52, 132, 25, 0x24FFFFFF);
            text.setColor(0xE6F5F5F7);
            c.drawText(stat, px + 26, 116, text);
        }
        // Join code, always visible once a game runs (the setup card shows it large): anyone can join mid-game.
        if (qr != null && !"setup".equals(phase)) {
            float qx = W - 96 - 150, qy = 56;
            glassRect(c, qx - 14, qy - 14, qx + 164, qy + 192, 26, 0x2EFFFFFF);
            r.set(qx, qy, qx + 150, qy + 150);
            path.reset();
            path.addRoundRect(r, 14, 14, Path.Direction.CW);
            c.save();
            c.clipPath(path);
            c.drawBitmap(qr, null, r, img);
            c.restore();
            text.setTypeface(bold);
            text.setTextSize(20);
            text.setColor(0xCCF5F5F7);
            centered(c, tr ? "Telefonla katıl" : "Join on phone", qx + 75, qy + 180);
        }
    }

    private String statusLine() {
        String status = st.optString("status");
        if (!status.isEmpty()) return status;
        JSONObject q = st.optJSONObject("quiz");
        if ("quiz".equals(mode) && q != null && ("question".equals(phase) || "reveal".equals(phase))) {
            long left = Math.max(0, q.optLong("timeLeft") - (SystemClock.elapsedRealtime() - stateAt));
            return (tr ? "Soru " : "Question ") + q.optInt("no") + "  ·  " + clock(left) + (tr ? " kaldı" : " left");
        }
        JSONObject z = st.optJSONObject("sessiz");
        if ("sessiz".equals(mode) && z != null && z.optInt("no") > 0) return (tr ? "Kart " : "Card ") + z.optInt("no");
        return "";
    }

    private void drawScores(Canvas c) {
        JSONArray pl = st.optJSONArray("players");
        float x0 = 1452, x1 = W - 96, y = 300;
        int n = pl == null ? 0 : Math.min(8, pl.length());
        float h = 86 + Math.max(1, n) * 74;
        glassRect(c, x0, y, x1, y + h, 34, 0x1FFFFFFF);
        text.setTypeface(bold);
        text.setTextSize(22);
        text.setLetterSpacing(0.12f);
        text.setColor(0x99F5F5F7);
        c.drawText(tr ? "SALON" : "ROOM", x0 + 34, y + 52, text);
        text.setLetterSpacing(0);
        if (n == 0) {
            text.setTypeface(medium);
            text.setTextSize(24);
            text.setColor(0x80F5F5F7);
            c.drawText(tr ? "QR kodu okutun" : "Scan the QR code", x0 + 34, y + 116, text);
            return;
        }
        String turn = "";
        JSONObject ro = st.optJSONObject("roulette");
        JSONObject z = st.optJSONObject("sessiz");
        if (ro != null && "turn".equals(phase)) turn = ro.optString("turn");
        if (z != null && "card".equals(phase)) turn = z.optString("teller");
        String host = st.optString("host");
        for (int i = 0; i < n; i++) {
            JSONObject p = pl.optJSONObject(i);
            float ry = y + 86 + i * 74;
            int col = parse(p.optString("color"), 0xFFFFFFFF);
            boolean away = p.optBoolean("away");
            if (p.optString("id").equals(turn)) {
                fill.setColor(withA(col, 46));
                r.set(x0 + 14, ry - 8, x1 - 14, ry + 58);
                c.drawRoundRect(r, 22, 22, fill);
            }
            fill.setColor(away ? 0x55FFFFFF : col);
            c.drawCircle(x0 + 52, ry + 25, 11, fill);
            text.setTypeface(bold);
            text.setTextSize(28);
            text.setColor(away ? 0x66F5F5F7 : 0xFFF5F5F7);
            String name = ellipsize(p.optString("name") + (p.optString("id").equals(host) ? "  ★" : ""), 210);
            c.drawText(name, x0 + 80, ry + 35, text);
            String right;
            if ("quiz".equals(mode) && "question".equals(phase)) right = p.optBoolean("answered") ? "✓" : "…";
            else right = String.valueOf(p.optInt("score"));
            text.setTypeface(black);
            text.setTextSize(30);
            text.setColor("✓".equals(right) ? 0xFF30D158 : 0xFFF5F5F7);
            float rw = text.measureText(right);
            c.drawText(right, x1 - 36 - rw, ry + 36, text);
        }
    }

    private void drawLobby(Canvas c) {
        bigMessage(c, tr ? "Bir oyun seçin" : "Pick a game", "");
    }

    // ---- CineQuiz --------------------------------------------------------------------------------

    private boolean drawQuiz(Canvas c, long now) {
        JSONObject q = st.optJSONObject("quiz");
        if (q == null) return false;
        switch (phase) {
            case "setup":
                setupCard(c, tr ? "Süre: " + q.optInt("minutes") + " dakika  ·  Soru başına " + q.optInt("seconds") + " sn"
                                : q.optInt("minutes") + " minutes  ·  " + q.optInt("seconds") + " s per question",
                        tr ? "Süre bitene kadar en çok doğru bilen kazanır ve bu gecenin filmini seçer."
                                : "Most right answers before time runs out wins, and picks tonight's film.");
                return false;
            case "loading":
                return spinner(c, now, st.optString("status"));
            case "finished":
                return podium(c, q, now);
            default:
                break;
        }
        boolean revealing = "reveal".equals(phase);
        boolean tall = "tall".equals(q.optString("shape"));
        float ix = 96, iy = 214;
        float iw = tall ? 440 : 860, ih = tall ? 660 : 484;
        // Picture
        drawPicture(c, q.optString("image"), ix, iy, ix + iw, iy + ih, 34, false);
        String cap = q.optString("caption");
        if (!cap.isEmpty()) {
            text.setTypeface(bold);
            text.setTextSize(30);
            text.setColor(0xFFF5F5F7);
            c.drawText(ellipsize(cap, iw), ix + 6, iy + ih + 50, text);
        }
        // Prompt and timer
        float px = ix + iw + 60, pw = 1400 - px;
        text.setTypeface(black);
        text.setTextSize(tall ? 52 : 44);
        text.setColor(0xFFF5F5F7);
        float ty = drawWrapped(c, q.optString("prompt"), px, iy + 50, pw, 3, 1.18f);
        long total = Math.max(1, q.optLong("total"));
        long left = q.optBoolean("reading") ? total : Math.max(0, q.optLong("left") - (now - stateAt));
        if (!revealing) ring(c, px + 64, ty + 90, 54, left / (float) total, String.valueOf((left + 999) / 1000), left < 5500 ? 0xFFFF453A : accent);
        JSONArray opts = q.optJSONArray("options");
        float oy = revealing ? ty + 30 : ty + 190;
        if (opts != null && opts.length() > 0) {
            int correct = q.optInt("correct", -1);
            float ow = tall ? (pw - 24) / 2 : pw, oh = 92;
            for (int i = 0; i < opts.length(); i++) {
                float cx = tall ? px + (i % 2) * (ow + 24) : px, cy = tall ? oy + (i / 2) * (oh + 22) : oy + i * (oh - 8);
                float h2 = tall ? oh : oh - 20;
                boolean right = revealing && i == correct;
                int tint = right ? 0x5530D158 : revealing ? 0x0FFFFFFF : 0x1FFFFFFF;
                glassRect(c, cx, cy, cx + ow, cy + h2, h2 / 2, tint);
                if (right) {
                    stroke.setStrokeWidth(3);
                    stroke.setColor(0xFF30D158);
                    r.set(cx, cy, cx + ow, cy + h2);
                    c.drawRoundRect(r, h2 / 2, h2 / 2, stroke);
                }
                text.setTypeface(black);
                text.setTextSize(26);
                text.setColor(right ? 0xFF30D158 : withA(accent, revealing ? 120 : 255));
                c.drawText(String.valueOf((char) ('A' + i)), cx + 30, cy + h2 / 2 + 10, text);
                text.setTypeface(bold);
                text.setTextSize(tall ? 28 : 27);
                text.setColor(revealing && !right ? 0x66F5F5F7 : 0xFFF5F5F7);
                c.drawText(ellipsize(opts.optString(i), ow - 100), cx + 72, cy + h2 / 2 + 10, text);
            }
        } else if (!revealing) {
            text.setTypeface(medium);
            text.setTextSize(28);
            text.setColor(0x99F5F5F7);
            c.drawText(tr ? "Cevabınızı telefonunuza yazın" : "Type your answer on your phone", px, oy + 20, text);
        }
        if (revealing) return drawReveal(c, q, px, pw, now);
        return true;
    }

    private boolean drawReveal(Canvas c, JSONObject q, float px, float pw, long now) {
        float k = ease(Math.min(1f, (now - revealAt) / 600f));
        JSONArray opts = q.optJSONArray("options");
        float y = opts != null && opts.length() > 0 ? 760 : 470;
        if (opts == null || opts.length() == 0) {
            glassRect(c, px, 380, px + pw, 470, 30, 0x4030D158);
            text.setTypeface(black);
            text.setTextSize(36);
            text.setColor(0xFF30D158);
            c.drawText(ellipsize(q.optString("answer"), pw - 60), px + 30, 438, text);
        }
        // Everyone's answer, together.
        JSONArray pl = st.optJSONArray("players");
        float x = px;
        for (int i = 0; pl != null && i < pl.length() && i < 6; i++) {
            JSONObject p = pl.optJSONObject(i);
            String a = p.optString("answer");
            boolean ok = p.optBoolean("correct");
            text.setTypeface(bold);
            text.setTextSize(22);
            String label = p.optString("name") + "  " + (a.isEmpty() ? "—" : a);
            label = ellipsize(label, 300);
            float w = text.measureText(label) + 70;
            if (x + w > px + pw) {
                x = px;
                y += 70;
            }
            float yy = y + (1 - k) * 30;
            glassRect(c, x, yy, x + w, yy + 54, 27, ok ? 0x4430D158 : 0x18FFFFFF);
            text.setColor(ok ? 0xFF30D158 : 0x99F5F5F7);
            c.drawText(ok ? "✓" : "✕", x + 20, yy + 36, text);
            text.setColor(0xFFF5F5F7);
            c.drawText(label, x + 50, yy + 36, text);
            if (ok && k < 1) {
                text.setTypeface(black);
                text.setTextSize(30);
                text.setColor(withA(0xFF30D158, (int) (255 * (1 - k))));
                c.drawText("+10", x + w - 60, yy - 20 - 40 * k, text);
            }
            x += w + 16;
        }
        String fact = q.optString("fact");
        if (!fact.isEmpty()) {
            float fy = 930;
            glassRect(c, 96, fy - 10, 1400, fy + 96, 34, 0x24FFFFFF);
            gamzeOrb(c, 150, fy + 43, 24, now);
            text.setTypeface(medium);
            text.setTextSize(27);
            text.setColor(0xEEF5F5F7);
            drawWrapped(c, fact, 196, fy + 32, 1170, 2, 1.25f);
        }
        smooth |= k < 1;
        return !fact.isEmpty();
    }

    private boolean podium(Canvas c, JSONObject q, long now) {
        String champ = q.optString("championName");
        String picked = q.optString("picked");
        float k = ease(Math.min(1f, (now - phaseAt) / 900f));
        glassRect(c, 300, 260, 1340, 900, 48, 0x22FFFFFF);
        text.setTypeface(bold);
        text.setTextSize(26);
        text.setLetterSpacing(0.2f);
        text.setColor(withA(accent, 230));
        centered(c, tr ? "BU GECENİN ŞAMPİYONU" : "TONIGHT'S CHAMPION", 820, 370);
        text.setLetterSpacing(0);
        text.setTypeface(black);
        text.setTextSize(96 + 10 * (1 - k));
        text.setColor(0xFFF5F5F7);
        text.setShadowLayer(40, 0, 0, withA(accent, 180));
        centered(c, champ.isEmpty() ? "—" : ellipsize(champ, 900), 820, 520);
        text.clearShadowLayer();
        text.setTypeface(medium);
        text.setTextSize(32);
        text.setColor(0xCCF5F5F7);
        String line = !picked.isEmpty() ? (tr ? "▶  " + picked + " açılıyor" : "▶  Starting " + picked)
                : champ.isEmpty() ? (tr ? "OK: yeniden oyna" : "OK: play again")
                : (tr ? champ + " filmi telefonundan seçiyor…" : champ + " is picking the film on their phone…");
        centered(c, line, 820, 640);
        JSONArray pl = st.optJSONArray("players");
        float x = 420;
        for (int i = 0; pl != null && i < Math.min(4, pl.length()); i++) {
            JSONObject p = pl.optJSONObject(i);
            text.setTypeface(bold);
            text.setTextSize(26);
            text.setColor(parse(p.optString("color"), 0xFFFFFFFF));
            c.drawText((i + 1) + ". " + ellipsize(p.optString("name"), 160) + "  " + p.optInt("score"), x, 790, text);
            x += 230;
        }
        smooth |= k < 1;
        return false;
    }

    // ---- CineRoulette ----------------------------------------------------------------------------

    private boolean drawRoulette(Canvas c, long now) {
        JSONObject ro = st.optJSONObject("roulette");
        if (ro == null) return false;
        JSONArray ch = ro.optJSONArray("chambers");
        boolean anim = false;
        // Cylinder angle
        float u = Math.min(1f, (now - cylStart) / (float) cylDur);
        cylAngle = cylFrom + (cylTo - cylFrom) * (1 - (float) Math.pow(1 - u, 3));
        smooth |= u < 1;
        float shake = 0;
        if ("bang".equals(lastShot) && now - flashAt < 500) {
            shake = 14 * (1 - (now - flashAt) / 500f) * (float) Math.sin((now - flashAt) / 18.0);
            smooth = true;
        }
        float cx = 640 + shake, cy = 600, R = 380;
        drawCylinder(c, cx, cy, R, ch, ro.optInt("aligned"), now);
        // Right column: whose turn, how long.
        float px = 1090, pw = 330;
        if ("setup".equals(phase)) {
            text.setTypeface(black);
            text.setTextSize(40);
            text.setColor(0xFFF5F5F7);
            drawWrapped(c, tr ? "Herkes bir film yüklesin" : "Everyone loads a film", px, 300, pw, 2, 1.15f);
            text.setTypeface(medium);
            text.setTextSize(25);
            text.setColor(0xB3F5F5F7);
            drawWrapped(c, tr ? "Tetik her boş çıktığında o haznedeki film elenir. Kurşun hangi filmdeyse bu gece o izlenir. Kendi filmin namluya geldiğinde risk alacak mısın?"
                    : "Every empty chamber knocks its film out. The bullet picks tonight's film. Will you risk it when your own film is under the hammer?", px, 420, pw, 8, 1.3f);
            text.setTypeface(bold);
            text.setTextSize(22);
            text.setColor(withA(accent, 230));
            c.drawText(tr ? "OK  ·  Başlat" : "OK  ·  Start", px, 900, text);
        } else if ("turn".equals(phase) || "pulling".equals(phase)) {
            String name = ro.optString("turnName");
            text.setTypeface(bold);
            text.setTextSize(24);
            text.setLetterSpacing(0.16f);
            text.setColor(0x99F5F5F7);
            c.drawText(tr ? "SIRA" : "TURN", px, 320, text);
            text.setLetterSpacing(0);
            text.setTypeface(black);
            text.setTextSize(56);
            text.setColor(0xFFF5F5F7);
            c.drawText(ellipsize(name.isEmpty() ? (tr ? "Kumanda" : "Remote") : name, pw), px, 390, text);
            long left = Math.max(0, ro.optLong("left") - (now - stateAt));
            long total = Math.max(1, ro.optLong("total"));
            if ("turn".equals(phase)) {
                float beat = left < 4000 ? 1 + 0.06f * (float) Math.max(0, Math.sin(now / 90.0)) : 1;
                ring(c, px + 90, 520, 72 * beat, left / (float) total, String.valueOf((left + 999) / 1000), left < 4000 ? 0xFFFF453A : accent);
                anim = true;
            }
            JSONObject top = ch != null ? ch.optJSONObject(ro.optInt("aligned")) : null;
            if (top != null) {
                text.setTypeface(medium);
                text.setTextSize(24);
                text.setColor(0xB3F5F5F7);
                c.drawText(tr ? "Namludaki film" : "Under the hammer", px, 660, text);
                text.setTypeface(bold);
                text.setTextSize(32);
                text.setColor(0xFFF5F5F7);
                c.drawText(ellipsize(top.optString("title"), pw), px, 706, text);
            }
            text.setTypeface(bold);
            text.setTextSize(22);
            text.setColor(withA(accent, 230));
            c.drawText(tr ? "OK  ·  Tetik      ►  ·  Çevir" : "OK  ·  Trigger      ►  ·  Spin", px, 900, text);
        }
        if ("finished".equals(phase)) smooth |= drawWinner(c, ro, now);
        if (now - flashAt < 260) {
            fill.setColor(Color.argb((int) (255 * (1 - (now - flashAt) / 260f)), 255, 250, 240));
            c.drawRect(0, 0, W, H, fill);
            smooth = true;
        }
        return anim;
    }

    private void drawCylinder(Canvas c, float cx, float cy, float R, JSONArray ch, int aligned, long now) {
        // Shadow and body
        glow.setShader(new RadialGradient(cx, cy + 30, R * 1.25f, 0xAA000000, 0, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy + 30, R * 1.25f, glow);
        glow.setShader(null);
        fill.setShader(new RadialGradient(cx - R * 0.35f, cy - R * 0.4f, R * 1.6f,
                new int[]{0xFF5A6068, 0xFF2A2E34, 0xFF101215}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, fill);
        fill.setShader(new SweepGradient(cx, cy, new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF}, null));
        c.drawCircle(cx, cy, R, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(6);
        stroke.setColor(0x40FFFFFF);
        c.drawCircle(cx, cy, R - 3, stroke);
        // Flutes between the chambers
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(cylAngle + i * 60 + 30 - 90);
            float fx = cx + (float) Math.cos(a) * R * 0.97f, fy = cy + (float) Math.sin(a) * R * 0.97f;
            fill.setShader(new RadialGradient(fx, fy, R * 0.2f, 0xEE05060A, 0x0005060A, Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, R * 0.2f, fill);
        }
        fill.setShader(null);
        // Chambers with posters
        float pr = R * 0.62f, cw = R * 0.36f, chh = cw * 1.5f;
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(cylAngle + i * 60 - 90);
            float x = cx + (float) Math.cos(a) * pr, y = cy + (float) Math.sin(a) * pr;
            JSONObject o = ch != null ? ch.optJSONObject(i) : null;
            boolean out = o != null && o.optBoolean("out");
            boolean top = i == aligned && !"setup".equals(phase);
            // Bore
            fill.setColor(0xFF07080A);
            c.drawCircle(x, y, R * 0.29f, fill);
            stroke.setStrokeWidth(4);
            stroke.setColor(top ? withA(accent, 230) : 0x50FFFFFF);
            c.drawCircle(x, y, R * 0.29f, stroke);
            float sc = top ? 1.08f : 1f;
            float l = x - cw / 2 * sc, t = y - chh / 2 * sc * 0.82f, rr = x + cw / 2 * sc, b = y + chh / 2 * sc * 0.82f;
            String title = o == null ? "" : o.optString("title");
            if (title.isEmpty()) {
                text.setTypeface(black);
                text.setTextSize(64);
                text.setColor(0x40F5F5F7);
                centered(c, "+", x, y + 22);
                continue;
            }
            img.setColorFilter(out ? grey : null);
            drawPicture(c, o.optString("poster"), l, t, rr, b, 14, out);
            img.setColorFilter(null);
            if (out) {
                stroke.setStrokeWidth(8);
                stroke.setColor(0xCCFF453A);
                c.drawLine(l + 16, t + 16, rr - 16, b - 16, stroke);
                c.drawLine(rr - 16, t + 16, l + 16, b - 16, stroke);
            }
            int oc = ownerColor(o.optString("owner"));
            if (oc != 0) {
                fill.setColor(oc);
                c.drawCircle(rr - 10, t + 10, 11, fill);
            }
            if (top && "turn".equals(phase)) {
                for (int g = 1; g <= 4; g++) {
                    stroke.setStrokeWidth(5);
                    stroke.setColor(withA(accent, 120 / g));
                    r.set(l - g * 5, t - g * 5, rr + g * 5, b + g * 5);
                    c.drawRoundRect(r, 14 + g * 5, 14 + g * 5, stroke);
                }
            }
        }
        // Centre pin and ratchet
        fill.setShader(new RadialGradient(cx - 10, cy - 10, R * 0.16f, new int[]{0xFF8A9099, 0xFF2B2F35}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R * 0.15f, fill);
        fill.setShader(null);
        fill.setColor(0xFF0B0C0F);
        c.drawCircle(cx, cy, R * 0.05f, fill);
        // Barrel notch at twelve o'clock
        path.reset();
        path.moveTo(cx - 26, cy - R - 40);
        path.lineTo(cx + 26, cy - R - 40);
        path.lineTo(cx, cy - R + 2);
        path.close();
        fill.setColor(accent);
        c.drawPath(path, fill);
    }

    private boolean drawWinner(Canvas c, JSONObject ro, long now) {
        float k = ease(Math.min(1f, (now - shotAt - 250) / 900f));
        if (k <= 0) return true;
        fill.setColor(Color.argb((int) (200 * k), 0, 0, 0));
        c.drawRect(0, 0, W, H, fill);
        JSONArray ch = ro.optJSONArray("chambers");
        JSONObject win = ch != null ? ch.optJSONObject(ro.optInt("aligned")) : null;
        if (win == null) return k < 1;
        float pw = 420 * (0.7f + 0.3f * k), ph = pw * 1.5f;
        float x = 560 - pw / 2, y = 540 - ph / 2;
        drawPicture(c, win.optString("poster"), x, y, x + pw, y + ph, 28, false);
        text.setTypeface(bold);
        text.setTextSize(28);
        text.setLetterSpacing(0.2f);
        text.setColor(withA(accent, (int) (255 * k)));
        c.drawText(tr ? "BU GECENİN FİLMİ" : "TONIGHT'S FILM", 900, 400, text);
        text.setLetterSpacing(0);
        text.setTypeface(black);
        text.setTextSize(78);
        text.setColor(withA(0xFFF5F5F7, (int) (255 * k)));
        float y2 = drawWrapped(c, win.optString("title"), 900, 500, 900, 2, 1.1f);
        text.setTypeface(medium);
        text.setTextSize(30);
        text.setColor(withA(0xCCF5F5F7, (int) (255 * k)));
        String by = win.optString("ownerName");
        c.drawText((by.isEmpty() ? "" : (tr ? by + " yükledi  ·  " : "Loaded by " + by + "  ·  ")) + (tr ? "OK ile izle" : "OK to watch"), 900, y2 + 60, text);
        return k < 1;
    }

    private int ownerColor(String owner) {
        JSONArray pl = st.optJSONArray("players");
        for (int i = 0; pl != null && i < pl.length(); i++) {
            JSONObject p = pl.optJSONObject(i);
            if (p.optString("id").equals(owner)) return parse(p.optString("color"), 0);
        }
        return "gamze".equals(owner) ? 0xFFF5F5F7 : 0;
    }

    // ---- CineSessiz ------------------------------------------------------------------------------

    private boolean drawSessiz(Canvas c, long now) {
        JSONObject z = st.optJSONObject("sessiz");
        if (z == null) return false;
        if ("setup".equals(phase)) {
            setupCard(c, tr ? "Süre yok, soru sınırı yok" : "No timer, no limit",
                    tr ? "Anlatıcı telefonundan bir film, dizi ya da oyuncu seçer ve Gamze'nin yasakladığı kelimeleri söylemeden anlatır. İlk doğru tahmin +10, anlatıcı +5."
                            : "The teller picks a film, series or actor on their phone and describes it without the words Gamze forbids. First right guess +10, teller +5.");
            return false;
        }
        if ("loading".equals(phase)) return spinner(c, now, st.optString("status"));
        if ("choosing".equals(phase)) {
            String who = z.optString("tellerName");
            return spinner(c, now, who.isEmpty() ? (tr ? "Oyuncu bekleniyor" : "Waiting for players")
                    : (tr ? who + " anlatacağı şeyi seçiyor…" : who + " is picking what to describe…"));
        }
        boolean solved = "solved".equals(phase);
        float k = solved ? ease(Math.min(1f, (now - solvedAt) / 700f)) : 0;
        // The card: face down until someone gets it.
        float cx = 96, cy = 214, cw = 560, chh = 760;
        if (!solved || k < 0.5f) {
            float sx = solved ? 1 - k * 2 : 1;
            c.save();
            c.scale(Math.max(0.01f, sx), 1, cx + cw / 2, cy + chh / 2);
            glassRect(c, cx, cy, cx + cw, cy + chh, 44, withA(accent, 40));
            stroke.setStrokeWidth(2);
            stroke.setColor(withA(accent, 140));
            r.set(cx + 18, cy + 18, cx + cw - 18, cy + chh - 18);
            c.drawRoundRect(r, 32, 32, stroke);
            text.setTypeface(black);
            text.setTextSize(200);
            text.setColor(withA(accent, 200));
            centered(c, "?", cx + cw / 2, cy + chh / 2 + 70);
            text.setTypeface(bold);
            text.setTextSize(30);
            text.setLetterSpacing(0.18f);
            text.setColor(0xFFF5F5F7);
            centered(c, z.optString("typeName").toUpperCase(new Locale("tr")), cx + cw / 2, cy + chh - 80);
            text.setLetterSpacing(0);
            c.restore();
        } else {
            float sx = (k - 0.5f) * 2;
            c.save();
            c.scale(Math.max(0.01f, sx), 1, cx + cw / 2, cy + chh / 2);
            drawPicture(c, z.optString("image"), cx, cy, cx + cw, cy + chh, 44, false);
            c.restore();
        }
        float px = cx + cw + 70, pw = 1400 - px;
        if (solved) {
            text.setTypeface(bold);
            text.setTextSize(28);
            text.setColor(0xFF30D158);
            c.drawText((tr ? "✓  " + z.optString("solvedBy") + " bildi" : "✓  " + z.optString("solvedBy") + " got it"), px, 300, text);
            text.setTypeface(black);
            text.setTextSize(76);
            text.setColor(0xFFF5F5F7);
            drawWrapped(c, z.optString("title"), px, 400, pw, 3, 1.1f);
        } else {
            text.setTypeface(bold);
            text.setTextSize(24);
            text.setLetterSpacing(0.16f);
            text.setColor(0x99F5F5F7);
            c.drawText(tr ? "ANLATICI" : "TELLER", px, 290, text);
            text.setLetterSpacing(0);
            text.setTypeface(black);
            text.setTextSize(60);
            text.setColor(0xFFF5F5F7);
            String teller = z.optString("tellerName");
            c.drawText(ellipsize(teller.isEmpty() ? (tr ? "Oyuncu bekleniyor" : "Waiting for players") : teller, pw), px, 360, text);
            String hint = z.optString("hint");
            if (!hint.isEmpty()) {
                glassRect(c, px, 400, px + pw, 500, 32, 0x24FFFFFF);
                gamzeOrb(c, px + 50, 450, 22, now);
                text.setTypeface(medium);
                text.setTextSize(28);
                text.setColor(0xEEF5F5F7);
                drawWrapped(c, hint, px + 92, 440, pw - 120, 2, 1.2f);
            }
        }
        // Live guesses
        JSONArray feed = z.optJSONArray("feed");
        float fy = 560;
        for (int i = 0; feed != null && i < feed.length() && i < 5; i++) {
            JSONObject f = feed.optJSONObject(i);
            boolean ok = f.optBoolean("ok");
            int col = f.optInt("color", 0xFFFFFFFF);
            float alpha = 1f - i * 0.16f;
            glassRect(c, px, fy, px + pw, fy + 62, 31, ok ? 0x4430D158 : 0x16FFFFFF);
            fill.setColor(withA(col, (int) (255 * alpha)));
            c.drawCircle(px + 32, fy + 31, 9, fill);
            text.setTypeface(bold);
            text.setTextSize(25);
            text.setColor(withA(0xFFF5F5F7, (int) (220 * alpha)));
            String who = ellipsize(f.optString("name"), 200);
            c.drawText(who, px + 56, fy + 40, text);
            float ww = text.measureText(who);
            text.setTypeface(medium);
            text.setColor(ok ? 0xFF30D158 : withA(0xFFF5F5F7, (int) (160 * alpha)));
            c.drawText(ellipsize(f.optString("text"), pw - ww - 100), px + 76 + ww, fy + 40, text);
            fy += 76;
        }
        smooth |= solved && k < 1;
        return !z.optString("hint").isEmpty();
    }

    // ---- Shared pieces ---------------------------------------------------------------------------

    private void setupCard(Canvas c, String line1, String line2) {
        glassRect(c, 96, 240, 1400, 900, 48, 0x1CFFFFFF);
        if (qr != null) {
            r.set(170, 330, 590, 750);
            fill.setColor(Color.WHITE);
            c.drawRoundRect(r.left - 18, r.top - 18, r.right + 18, r.bottom + 18, 30, 30, fill);
            c.drawBitmap(qr, null, r, img);
        }
        text.setTypeface(black);
        text.setTextSize(54);
        text.setColor(0xFFF5F5F7);
        c.drawText(tr ? "Telefonunuzla katılın" : "Join with your phone", 670, 400, text);
        text.setTypeface(bold);
        text.setTextSize(30);
        text.setColor(withA(accent, 240));
        c.drawText(line1, 670, 470, text);
        text.setTypeface(medium);
        text.setTextSize(28);
        text.setColor(0xB3F5F5F7);
        float y = drawWrapped(c, line2, 670, 540, 660, 4, 1.3f);
        String host = "";
        JSONArray pl = st.optJSONArray("players");
        for (int i = 0; pl != null && i < pl.length(); i++) {
            JSONObject p = pl.optJSONObject(i);
            if (p.optString("id").equals(st.optString("host"))) host = p.optString("name");
        }
        text.setTypeface(bold);
        text.setTextSize(26);
        text.setColor(0xE6F5F5F7);
        String start = host.isEmpty() ? (tr ? "Kumandada OK ile de başlatabilirsiniz" : "You can also start with OK on the remote")
                : (tr ? host + " telefonundan başlatacak  ·  ya da OK" : host + " starts it from their phone  ·  or press OK");
        c.drawText(start, 670, Math.max(y + 60, 790), text);
        String status = st.optString("status");
        if (!status.isEmpty()) {
            text.setColor(0xFFFF9F0A);
            c.drawText(ellipsize(status, 680), 670, 850, text);
        }
    }

    private void bigMessage(Canvas c, String title, String sub) {
        text.setTypeface(black);
        text.setTextSize(64);
        text.setColor(0xFFF5F5F7);
        centered(c, title, 760, 560);
        if (!sub.isEmpty()) {
            text.setTypeface(medium);
            text.setTextSize(30);
            text.setColor(0xB3F5F5F7);
            centered(c, sub, 760, 620);
        }
    }

    private boolean spinner(Canvas c, long now, String label) {
        float a = (now % 1200L) / 1200f * 360f;
        stroke.setStrokeWidth(10);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(0x22FFFFFF);
        c.drawCircle(760, 520, 70, stroke);
        stroke.setColor(accent);
        r.set(690, 450, 830, 590);
        c.drawArc(r, a, 100, false, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        text.setTypeface(medium);
        text.setTextSize(32);
        text.setColor(0xCCF5F5F7);
        centered(c, label, 760, 680);
        smooth = !lite;
        return true;
    }

    private void ring(Canvas c, float cx, float cy, float rad, float frac, String label, int col) {
        stroke.setStrokeWidth(12);
        stroke.setColor(0x22FFFFFF);
        c.drawCircle(cx, cy, rad, stroke);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(col);
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        c.drawArc(r, -90, 360 * Math.max(0, Math.min(1, frac)), false, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        text.setTypeface(black);
        text.setTextSize(rad * 0.82f);
        text.setColor(0xFFF5F5F7);
        centered(c, label, cx, cy + rad * 0.3f);
    }

    /** Gamze's little light, breathing while she talks. */
    private void gamzeOrb(Canvas c, float x, float y, float rad, long now) {
        float b = lite ? 1 : 1 + 0.12f * (float) Math.sin(now / 260.0);
        fill.setShader(new RadialGradient(x - rad * 0.3f, y - rad * 0.3f, rad * 1.4f * b,
                new int[]{0xFFFFFFFF, 0xFFE0B3FF, 0xFFBF5AF2, 0x00BF5AF2}, new float[]{0, 0.25f, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad * 1.4f * b, fill);
        fill.setShader(null);
    }

    /** Frosted glass: a translucent fill, a top sheen and a hairline rim. */
    private void glassRect(Canvas c, float l, float t, float rr, float b, float rad, int tint) {
        r.set(l, t, rr, b);
        fill.setColor(tint);
        c.drawRoundRect(r, rad, rad, fill);
        fill.setShader(new LinearGradient(0, t, 0, t + Math.min(120, (b - t) * 0.6f), 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(1.6f);
        stroke.setShader(new LinearGradient(0, t, 0, b, 0x66FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, stroke);
        stroke.setShader(null);
    }

    /** A picture cropped to fill a rounded frame, with a soft shadow; fetched in the background. */
    private void drawPicture(Canvas c, String url, float l, float t, float rr, float b, float rad, boolean dim) {
        r.set(l, t + 18, rr, b + 18);
        fill.setColor(0x66000000);
        c.drawRoundRect(r, rad, rad, fill);
        r.set(l, t, rr, b);
        Bitmap bm = picture(url);
        if (bm == null) {
            fill.setColor(0xFF15171C);
            c.drawRoundRect(r, rad, rad, fill);
            return;
        }
        BitmapShader sh = new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float sc = Math.max((rr - l) / bm.getWidth(), (b - t) / bm.getHeight());
        m.setScale(sc, sc);
        m.postTranslate(l + ((rr - l) - bm.getWidth() * sc) / 2f, t + ((b - t) - bm.getHeight() * sc) / 2f);
        sh.setLocalMatrix(m);
        img.setShader(sh);
        c.drawRoundRect(r, rad, rad, img);
        img.setShader(null);
        // Glass sheen over the print.
        fill.setShader(new LinearGradient(l, t, l, t + (b - t) * 0.45f, dim ? 0x10FFFFFF : 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(0x40FFFFFF);
        c.drawRoundRect(r, rad, rad, stroke);
    }

    private Bitmap picture(final String url) {
        if (url == null || url.isEmpty()) return null;
        Bitmap b = pics.get(url);
        if (b != null) return b;
        b = PosterCache.getFast(url);
        if (b != null) {
            remember(url, b);
            return b;
        }
        if (loading.add(url) && !loader.isShutdown()) {
            final Context app = getContext().getApplicationContext();
            loader.execute(() -> {
                final Bitmap got = PosterCache.load(app, url, 960, 960);
                post(() -> {
                    loading.remove(url);
                    if (got != null) {
                        remember(url, got);
                        invalidate();
                    }
                });
            });
        }
        return null;
    }

    private void remember(String url, Bitmap b) {
        if (pics.size() > 14) pics.clear();
        pics.put(url, b);
    }

    private float drawWrapped(Canvas c, String s, float x, float y, float width, int maxLines, float lead) {
        if (s == null) return y;
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        float lh = text.getTextSize() * lead;
        for (int i = 0; i < words.length; i++) {
            String tryLine = line.length() == 0 ? words[i] : line + " " + words[i];
            if (text.measureText(tryLine) > width && line.length() > 0) {
                if (lines == maxLines - 1) {
                    c.drawText(ellipsize(line + " " + TextUtils.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)), width), x, y, text);
                    return y + lh;
                }
                c.drawText(line.toString(), x, y, text);
                y += lh;
                lines++;
                line = new StringBuilder(words[i]);
            } else {
                line = new StringBuilder(tryLine);
            }
        }
        if (line.length() > 0) {
            c.drawText(line.toString(), x, y, text);
            y += lh;
        }
        return y;
    }

    private String ellipsize(String s, float width) {
        if (s == null) return "";
        if (text.measureText(s) <= width) return s;
        int n = s.length();
        while (n > 1 && text.measureText(s, 0, n) + text.measureText("…") > width) n--;
        return s.substring(0, n).trim() + "…";
    }

    private void centered(Canvas c, String s, float cx, float y) {
        c.drawText(s, cx - text.measureText(s) / 2f, y, text);
    }

    private float measure(Typeface tf, float size, String s) {
        Typeface old = text.getTypeface();
        float oldSize = text.getTextSize();
        text.setTypeface(tf);
        text.setTextSize(size);
        float w = text.measureText(s);
        text.setTypeface(old);
        text.setTextSize(oldSize);
        return w;
    }

    private static String clock(long ms) {
        long sec = (ms + 999) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }

    private static float ease(float x) {
        return 1 - (float) Math.pow(1 - Math.max(0, x), 3);
    }

    private static int withA(int c, int a) {
        return (c & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    private static int parse(String hex, int def) {
        try {
            return Color.parseColor(hex);
        } catch (Throwable t) {
            return def;
        }
    }
}
