package com.cinetv.launcher.party;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.ai.EmaLightningTts;
import com.cinetv.launcher.device.SystemAudio;
import com.cinetv.launcher.ui.Brand;
import com.cinetv.launcher.ui.Glass;
import com.cinetv.launcher.ui.UiKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CineDuel (Sinema Nişancısı):
 * An exciting cinema-focused arena game where players use their phone's gyro sensor
 * or virtual joystick (or TV D-pad) to aim and shoot floating cinema targets:
 * iconic lines, scene descriptions, actor/director matches, posters, and trivia!
 * Can be played solo against Gamze AI or multi-player against connected phones.
 */
public final class CineDuelView extends View {

    public interface OnCineDuelListener {
        void onGameExit();
        void onMovieChosen(String movieTitle);
    }

    private static final int ROUND_TIME_SEC = 15;
    private static final int TOTAL_ROUNDS = 8;

    public static final class DuelChallenge {
        public final String promptType; // "REPLİK", "SAHNE", "YÖNETMEN", "AKTÖR", "AFİŞ"
        public final String question;
        public final String[] targets;   // 4 choices
        public final int correctIndex;
        public final String correctMovie;
        public final String lore;

        public DuelChallenge(String type, String q, String[] targets, int correctIdx, String movie, String lore) {
            this.promptType = type;
            this.question = q;
            this.targets = targets;
            this.correctIndex = correctIdx;
            this.correctMovie = movie;
            this.lore = lore;
        }
    }

    private static final DuelChallenge[] CHALLENGES = {
            new DuelChallenge(
                    "REPLİK AVI",
                    "\"Ona reddedemeyeceği bir teklif yapacağım.\"",
                    new String[]{"The Godfather", "Scarface", "Goodfellas", "Casino"},
                    0, "The Godfather",
                    "Don Vito Corleone'nin ikonik repliği; Amerikan sinema tarihinin en unutulmaz sahnesidir."
            ),
            new DuelChallenge(
                    "SAHNE EŞLEŞTİRME",
                    "Dev dalgaların zamanı büktüğü su gezegeni Miller'a inen astronotlar...",
                    new String[]{"Gravity", "Interstellar", "Ad Astra", "Solaris"},
                    1, "Interstellar",
                    "Christopher Nolan'ın görecelilik teorisini görselleştirdiği bu gezegende geçen her saat Dünya'da 7 yıla denktir."
            ),
            new DuelChallenge(
                    "REPLİK AVI",
                    "\"İlk kural: Kulüp hakkında asla konuşmayacaksın!\"",
                    new String[]{"Seven", "Dövüş Kulübü", "Zodiac", "Oyun"},
                    1, "Fight Club",
                    "Chuck Palahniuk uyarlaması David Fincher başyapıtı; modern tüketim kültürüne sert bir başkaldırıdır."
            ),
            new DuelChallenge(
                    "YÖNETMEN DÜELLOSU",
                    "Mia Wallace ve Vincent Vega'nın Jack Rabbit Slim's twist dansını kim yönetti?",
                    new String[]{"Martin Scorsese", "Stanley Kubrick", "Quentin Tarantino", "David Lynch"},
                    2, "Pulp Fiction",
                    "Tarantino, Godard'ın Bande à Part filmine saygı duruşu niteliğindeki bu sahneyle sinema tarihine geçti."
            ),
            new DuelChallenge(
                    "REPLİK AVI",
                    "\"Kırmızı hapı alırsan harikalar diyarında kalırsın ve sana tavşan deliğinin ne kadar derin olduğunu gösteririm.\"",
                    new String[]{"Inception", "The Matrix", "Karanlık Şehir", "Terminatör"},
                    1, "The Matrix",
                    "Wachowski Kardeşler'in felsefi siber-punk başyapıtı; simülasyon argümanının temel taşıdır."
            ),
            new DuelChallenge(
                    "SAHNE EŞLEŞTİRME",
                    "Aynanın karşısına geçip \"Bana mı konuşuyorsun? (You talkin' to me?)\" diyen yalnız taksi şoförü...",
                    new String[]{"Taxi Driver", "Kızgın Boğa", "Yaralı Yüz", "Serpico"},
                    0, "Taxi Driver",
                    "Robert De Niro'nun tamamen doğaçlama oynadığı bu sahne, yalnızlığın ve yabancılaşmanın zirvesidir."
            ),
            new DuelChallenge(
                    "AKTÖR DÜELLOSU",
                    "Joker rolüyle En İyi Yardımcı Erkek Oyuncu Oscar'ını kazanan efsane aktör kimdir?",
                    new String[]{"Joaquin Phoenix", "Heath Ledger", "Jack Nicholson", "Jared Leto"},
                    1, "The Dark Knight",
                    "Heath Ledger'ın metod oyunculuğu ile yarattığı anarşist Joker portresi, sinema tarihinin en etkileyici performanslarındandır."
            ),
            new DuelChallenge(
                    "SİNEMA TRIVIA",
                    "Rüyalar içinde rüyaya girip bilinçaltına fikir ekleme (inception) operasyonu yürüten zihin hırsızı kimdir?",
                    new String[]{"Dom Cobb", "Neo", "Cooper", "Tyler Durden"},
                    0, "Inception",
                    "Leonardo DiCaprio'nun canlandırdığı Cobb karakteri, dönen fırıldak totemi ile gerçekliği sorgular."
            )
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private OnCineDuelListener listener;

    private int currentRound = 0;
    private int p1Score = 0;
    private int aiScore = 0;
    private int secondsRemaining = ROUND_TIME_SEC;
    private boolean roundActive = false;
    private boolean gameOver = false;

    // Crosshair coordinates (normalized 0..1)
    private float p1X = 0.5f, p1Y = 0.5f;
    private float targetP1X = 0.5f, targetP1Y = 0.5f;

    // AI Crosshair (simulated player)
    private float aiX = 0.5f, aiY = 0.5f;
    private float aiTargetX = 0.5f, aiTargetY = 0.5f;

    // Target cards floating in arena
    private static final class FloatingTarget {
        public String label;
        public boolean isCorrect;
        public float x, y; // 0..1
        public float vx, vy;
        public float radius;
        public boolean hit = false;
        public int color;
    }

    private final List<FloatingTarget> targets = new ArrayList<>();
    private final RectF hitRect = new RectF();

    // Paints
    private final Paint bgPaint = new Paint();
    private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aiReticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint targetBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!roundActive || gameOver) return;
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                // Round timed out
                resolveRound(false, "Süre Doldu!");
            } else {
                // Gamze AI smart targeting logic: aims smoothly at targets
                updateAiBehavior();
                invalidate();
                main.postDelayed(this, 1000);
            }
        }
    };

    private final Runnable frameTick = new Runnable() {
        @Override
        public void run() {
            if (gameOver) return;
            updatePhysics();
            invalidate();
            main.postDelayed(this, 16);
        }
    };

    public CineDuelView(Context context) {
        super(context);
        init();
    }

    public CineDuelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);

        bgPaint.setColor(Color.parseColor("#050811"));

        reticlePaint.setStyle(Paint.Style.STROKE);
        reticlePaint.setStrokeWidth(UiKit.pxf(3.5f));
        reticlePaint.setColor(Color.parseColor("#38BDF8")); // Electric Cyan

        aiReticlePaint.setStyle(Paint.Style.STROKE);
        aiReticlePaint.setStrokeWidth(UiKit.pxf(3f));
        aiReticlePaint.setColor(Color.parseColor("#F59E0B")); // Amber Gold (Gamze AI)

        textPaint.setTypeface(FontManager.getBold(getContext()));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);

        titlePaint.setTypeface(FontManager.getBlack(getContext()));
        titlePaint.setColor(Color.WHITE);

        glowPaint.setStyle(Paint.Style.STROKE);

        startNewGame();
    }

    public void setOnCineDuelListener(OnCineDuelListener l) {
        this.listener = l;
    }

    public void startNewGame() {
        currentRound = 0;
        p1Score = 0;
        aiScore = 0;
        gameOver = false;
        loadRound(0);
        main.post(frameTick);

        EmaLightningTts.speakGame(getContext(), "SineDüello Başlıyor! Telefonunuzu jiroskopla hareket ettirin veya TV kumandasıyla hedefe nişan alıp vurun!");
    }

    private void loadRound(int roundIdx) {
        if (roundIdx >= CHALLENGES.length) {
            endGame();
            return;
        }
        currentRound = roundIdx;
        secondsRemaining = ROUND_TIME_SEC;
        roundActive = true;

        DuelChallenge c = CHALLENGES[roundIdx];
        targets.clear();

        int[] colors = {
                0xFF1E3A8A, 0xFF881337, 0xFF065F46, 0xFF7C2D12
        };

        // Spawn 4 floating target orbs with slight drifting velocity
        float[][] coords = {
                {0.25f, 0.40f, 0.0012f, 0.0008f},
                {0.75f, 0.40f, -0.0010f, 0.0011f},
                {0.25f, 0.70f, 0.0009f, -0.0012f},
                {0.75f, 0.70f, -0.0011f, -0.0009f}
        };

        for (int i = 0; i < 4; i++) {
            FloatingTarget t = new FloatingTarget();
            t.label = c.targets[i];
            t.isCorrect = (i == c.correctIndex);
            t.x = coords[i][0];
            t.y = coords[i][1];
            t.vx = coords[i][2];
            t.vy = coords[i][3];
            t.radius = UiKit.pxf(90);
            t.color = colors[i % colors.length];
            targets.add(t);
        }

        p1X = 0.5f; p1Y = 0.55f;
        targetP1X = p1X; targetP1Y = p1Y;

        aiX = 0.5f; aiY = 0.55f;
        aiTargetX = targets.get((int) (Math.random() * 4)).x;
        aiTargetY = targets.get((int) (Math.random() * 4)).y;

        main.removeCallbacks(timerTick);
        main.postDelayed(timerTick, 1000);

        String prompt = "Soru " + (roundIdx + 1) + ": " + c.question;
        EmaLightningTts.speakGame(getContext(), prompt);
        invalidate();
    }

    private void updatePhysics() {
        if (!roundActive) return;

        // Lerp player crosshair smoothly
        p1X += (targetP1X - p1X) * 0.35f;
        p1Y += (targetP1Y - p1Y) * 0.35f;

        // Lerp AI crosshair smoothly
        aiX += (aiTargetX - aiX) * 0.08f;
        aiY += (aiTargetY - aiY) * 0.08f;

        // Float targets gently with bounce off arena boundaries
        for (FloatingTarget t : targets) {
            t.x += t.vx;
            t.y += t.vy;
            if (t.x < 0.15f || t.x > 0.85f) t.vx = -t.vx;
            if (t.y < 0.30f || t.y > 0.82f) t.vy = -t.vy;
        }
    }

    private void updateAiBehavior() {
        // Gamze AI decides to aim towards target
        DuelChallenge c = CHALLENGES[currentRound];
        if (Math.random() < 0.65) {
            // AI targets correct answer!
            FloatingTarget correct = targets.get(c.correctIndex);
            aiTargetX = correct.x + (float) ((Math.random() - 0.5) * 0.04);
            aiTargetY = correct.y + (float) ((Math.random() - 0.5) * 0.04);
            if (secondsRemaining <= 4 && Math.random() < 0.5) {
                // AI fires!
                shootAi();
            }
        } else {
            // AI targets random
            FloatingTarget rand = targets.get((int) (Math.random() * targets.size()));
            aiTargetX = rand.x;
            aiTargetY = rand.y;
        }
    }

    /** Called from Mobile Web Remote (via Gyro, touch joystick or network trigger) */
    public void setPlayerReticle(float normX, float normY) {
        targetP1X = Math.max(0.08f, Math.min(0.92f, normX));
        targetP1Y = Math.max(0.20f, Math.min(0.88f, normY));
        invalidate();
    }

    /** Called from Mobile Web Remote or TV Remote D-Pad Center/Enter to Fire! */
    public void shootPlayer() {
        if (!roundActive || gameOver) return;
        SystemAudio.click(getContext());

        int w = getWidth(), h = getHeight();
        float px = p1X * w, py = p1Y * h;

        FloatingTarget hit = null;
        for (FloatingTarget t : targets) {
            float tx = t.x * w, ty = t.y * h;
            float dist = (float) Math.hypot(px - tx, py - ty);
            if (dist <= t.radius) {
                hit = t;
                break;
            }
        }

        if (hit != null) {
            hit.hit = true;
            if (hit.isCorrect) {
                p1Score += 100;
                SystemAudio.open(getContext());
                resolveRound(true, "Doğru Vuruş! +100 Puan");
            } else {
                p1Score = Math.max(0, p1Score - 30);
                EmaLightningTts.speakGame(getContext(), "Yanlış hedef! Eksi otuz puan.");
            }
        } else {
            // Miss
            SystemAudio.tick(getContext());
        }
        invalidate();
    }

    private void shootAi() {
        if (!roundActive || gameOver) return;
        int w = getWidth(), h = getHeight();
        float ax = aiX * w, ay = aiY * h;
        for (FloatingTarget t : targets) {
            float tx = t.x * w, ty = t.y * h;
            if (Math.hypot(ax - tx, ay - ty) <= t.radius && t.isCorrect) {
                aiScore += 100;
                resolveRound(true, "Gamze AI Doğru Vurdu! +100 Puan");
                break;
            }
        }
    }

    private void resolveRound(boolean answered, String message) {
        roundActive = false;
        main.removeCallbacks(timerTick);

        DuelChallenge c = CHALLENGES[currentRound];
        EmaLightningTts.speakGame(getContext(), message + " Doğru film: " + c.correctMovie + ". " + c.lore);

        main.postDelayed(() -> {
            loadRound(currentRound + 1);
        }, 3200);
    }

    private String crownMovie;

    private void endGame() {
        gameOver = true;
        roundActive = false;
        main.removeCallbacks(timerTick);

        String champion = p1Score >= aiScore ? "Tebrikler, sen kazandın!" : "Bu sefer Gamze kazandı.";
        crownMovie = CHALLENGES[(int) (Math.random() * CHALLENGES.length)].correctMovie;
        // Nothing starts by itself: the result stays on screen until the viewer presses OK (or Back to leave).
        EmaLightningTts.speakGame(getContext(), "Oyun bitti. " + champion + " Önerim " + crownMovie + ". İzlemek istersen OK'e bas.");
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (listener != null) listener.onGameExit();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (listener != null && crownMovie != null) listener.onMovieChosen(crownMovie);
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        float step = 0.05f;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                targetP1X = Math.max(0.1f, targetP1X - step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                targetP1X = Math.min(0.9f, targetP1X + step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                targetP1Y = Math.max(0.25f, targetP1Y - step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                targetP1Y = Math.min(0.85f, targetP1Y + step);
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                shootPlayer();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (listener != null) listener.onGameExit();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Deep Cinema Arena Pitch Dark Background
        canvas.drawRect(0, 0, w, h, bgPaint);

        boolean tr = "tr".equals(Locale.getDefault().getLanguage());

        if (gameOver) {
            // Game Over Trophy Screen
            titlePaint.setTextSize(UiKit.pxf(44));
            titlePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(p1Score >= aiScore ? "ŞAMPİYON SİNEFİL!" : "GAMZE AI KAZANDI!", w / 2f, h * 0.35f, titlePaint);

            textPaint.setTextSize(UiKit.pxf(24));
            textPaint.setColor(Color.parseColor("#38BDF8"));
            canvas.drawText("Sen: " + p1Score + " Puan  ·  Gamze AI: " + aiScore + " Puan", w / 2f, h * 0.44f, textPaint);

            textPaint.setTextSize(UiKit.pxf(28));
            textPaint.setColor(Color.parseColor("#F59E0B"));
            DuelChallenge top = CHALLENGES[0];
            canvas.drawText("Bu Akşamın Seçilen Filmi: " + top.correctMovie, w / 2f, h * 0.54f, textPaint);

            textPaint.setTextSize(UiKit.pxf(16));
            textPaint.setColor(Color.parseColor("#94A3B8"));
            canvas.drawText("[OK] Şimdi TV'de Başlat  ·  [GERİ] Çıkış", w / 2f, h * 0.65f, textPaint);
            return;
        }

        // 2. Top Header HUD: Scoreboard, Timer, Round Pill
        titlePaint.setTextSize(UiKit.pxf(26));
        titlePaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("CINEDUEL · SİNEMA NİŞANCISI", UiKit.pxf(48), UiKit.pxf(56), titlePaint);

        // Player Scores
        textPaint.setTextSize(UiKit.pxf(20));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.parseColor("#38BDF8"));
        canvas.drawText("Sen: " + p1Score + " P", UiKit.pxf(48), UiKit.pxf(94), textPaint);

        textPaint.setColor(Color.parseColor("#F59E0B"));
        canvas.drawText("Gamze AI: " + aiScore + " P", UiKit.pxf(180), UiKit.pxf(94), textPaint);

        // Timer Badge
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setColor(secondsRemaining <= 4 ? Color.parseColor("#EF4444") : Color.WHITE);
        textPaint.setTextSize(UiKit.pxf(32));
        canvas.drawText(secondsRemaining + "s", w - UiKit.pxf(48), UiKit.pxf(64), textPaint);

        // 3. Question Banner
        if (currentRound < CHALLENGES.length) {
            DuelChallenge c = CHALLENGES[currentRound];

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.parseColor("#A78BFA"));
            textPaint.setTextSize(UiKit.pxf(16));
            canvas.drawText("ROUND " + (currentRound + 1) + " / " + TOTAL_ROUNDS + " · " + c.promptType, w / 2f, UiKit.pxf(110), textPaint);

            titlePaint.setTextSize(UiKit.pxf(26));
            titlePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(c.question, w / 2f, UiKit.pxf(150), titlePaint);
        }

        // 4. Floating Cinema Targets
        for (FloatingTarget t : targets) {
            float tx = t.x * w;
            float ty = t.y * h;

            // Halo glow
            glowPaint.setColor(t.color);
            glowPaint.setStrokeWidth(UiKit.pxf(4));
            canvas.drawCircle(tx, ty, t.radius, glowPaint);

            // Glass orb body
            targetBgPaint.setColor(Color.argb(200, 15, 23, 42));
            canvas.drawCircle(tx, ty, t.radius - UiKit.pxf(2), targetBgPaint);

            // Label
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(UiKit.pxf(18));
            canvas.drawText(t.label, tx, ty + UiKit.pxf(6), textPaint);
        }

        // 5. Gamze AI Reticle
        float ax = aiX * w;
        float ay = aiY * h;
        canvas.drawCircle(ax, ay, UiKit.pxf(28), aiReticlePaint);
        canvas.drawLine(ax - UiKit.pxf(36), ay, ax + UiKit.pxf(36), ay, aiReticlePaint);
        canvas.drawLine(ax, ay - UiKit.pxf(36), ax, ay + UiKit.pxf(36), aiReticlePaint);
        textPaint.setTextSize(UiKit.pxf(12));
        textPaint.setColor(Color.parseColor("#F59E0B"));
        canvas.drawText("Gamze AI", ax, ay - UiKit.pxf(34), textPaint);

        // 6. Player Reticle (Controlled by Phone Gyro or TV D-Pad)
        float px = p1X * w;
        float py = p1Y * h;
        canvas.drawCircle(px, py, UiKit.pxf(32), reticlePaint);
        canvas.drawCircle(px, py, UiKit.pxf(6), reticlePaint);
        canvas.drawLine(px - UiKit.pxf(44), py, px - UiKit.pxf(12), py, reticlePaint);
        canvas.drawLine(px + UiKit.pxf(12), py, px + UiKit.pxf(44), py, reticlePaint);
        canvas.drawLine(px, py - UiKit.pxf(44), px, py - UiKit.pxf(12), reticlePaint);
        canvas.drawLine(px, py + UiKit.pxf(12), px, py + UiKit.pxf(44), reticlePaint);
        textPaint.setTextSize(UiKit.pxf(13));
        textPaint.setColor(Color.parseColor("#38BDF8"));
        canvas.drawText("Sen", px, py - UiKit.pxf(38), textPaint);
    }
}
