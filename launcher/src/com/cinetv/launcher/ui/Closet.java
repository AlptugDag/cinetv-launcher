package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.cinetv.launcher.service.PosterCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * The "Film Closet" room: a narrow walk-in closet lined floor to ceiling with white shelves packed spine
 * out with films, as a film collector's closet is. Seen from its door: the back wall straight ahead (Home's
 * DVD row is one of its shelves), the side walls running towards it, a light tube on the ceiling.
 *
 * Built to read as a photograph, not a drawing: every spine has its own width, colour, title and number
 * (real film titles), a few covers stand face out (the bundled posters), each shelf shades the row under
 * it, the light falls off from the ceiling, the side walls are a little out of focus, and there is grain.
 * It is drawn once, 1600x900, then kept as a JPEG in the app's cache.
 */
final class Closet {
    private static final int W = 1600, H = 900;
    private static final int VERSION = 1;

    private static final String[] TITLES = {
            "Seven Samurai", "8½", "The Seventh Seal", "Persona", "Rashomon", "Tokyo Story", "Wild Strawberries",
            "La Dolce Vita", "Breathless", "The 400 Blows", "Ugetsu", "Ikiru", "Playtime", "Stalker", "Andrei Rublev",
            "Chungking Express", "In the Mood for Love", "Paris, Texas", "Eraserhead", "Harakiri", "Yojimbo",
            "Fanny and Alexander", "L'Avventura", "Pierrot le Fou", "Contempt", "The Rules of the Game",
            "Grand Illusion", "M", "Metropolis", "Solaris", "Uzak", "Kış Uykusu", "Close-Up", "Taste of Cherry",
            "Three Colors: Blue", "The Double Life of Véronique", "Cléo from 5 to 7", "Late Spring", "High and Low",
            "Ran", "Days of Heaven", "Badlands", "Le Samouraï", "Army of Shadows", "Amarcord", "Nights of Cabiria",
            "Bicycle Thieves", "Umberto D.", "The Leopard", "Black Orpheus", "Kes", "Ivan's Childhood", "Mirror",
            "Floating Weeds", "Sansho the Bailiff", "Woman in the Dunes", "Kwaidan", "Onibaba", "Tampopo",
            "Jules and Jim", "Last Year at Marienbad", "Hiroshima mon amour", "Au hasard Balthazar", "Pickpocket",
            "Diary of a Country Priest", "The Battle of Algiers", "Z", "The Conformist", "Brief Encounter",
            "The Third Man", "Black Narcissus", "Kind Hearts and Coronets", "Ordet", "Vampyr", "Autumn Sonata",
            "Cries and Whispers", "Through a Glass Darkly", "Winter Light", "Shoah", "Nashville", "Mikey and Nicky",
            "Night of the Living Dead", "Eyes Without a Face", "Les Diaboliques", "Wages of Fear", "Mon oncle",
            "Monsieur Hulot's Holiday", "Thieves' Highway", "Rififi", "Bob le flambeur", "Masculin féminin",
            "Two or Three Things I Know About Her", "Vivre sa vie", "A Brighter Summer Day", "Yi Yi", "Taipei Story",
            "Fallen Angels", "Happy Together", "Days of Being Wild", "Tokyo Drifter", "Branded to Kill"};
    /** Spines are mostly white or black, with a run of colours, as on a real collector's wall. */
    private static final int[] SPINES = {0xFFEDE9E1, 0xFFF4F1EA, 0xFF16161A, 0xFF1F1E23, 0xFFE8E2D4, 0xFF9E2A2B, 0xFF2B4C7E,
            0xFFD9A441, 0xFF3F6B4E, 0xFF6B3E75, 0xFFC65D3B, 0xFF8FA3AD, 0xFFE7C9A9, 0xFF2E2A27, 0xFFB8B2A6, 0xFF4A5A6A,
            0xFFE3D9C6, 0xFF101014, 0xFFF0EEE8, 0xFF7A1F2B};

    private Closet() {}

    static Bitmap render(Context ctx) {
        File cached = new File(ctx.getCacheDir(), "closet-v" + VERSION + ".jpg");
        if (cached.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(cached.getPath());
            if (b != null) return b;
        }
        try {
            Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            draw(ctx, out);
            try (FileOutputStream f = new FileOutputStream(cached)) {
                out.compress(Bitmap.CompressFormat.JPEG, 88, f);
            } catch (Throwable ignored) {
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Paint p() {
        return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    // the back wall, straight ahead, and the point the side walls run to
    private static final float BL = W * 0.25f, BR = W * 0.75f, BT = H * 0.05f, BB = H * 0.92f;
    private static final float VX = W / 2f, VY = H * 0.47f;
    private static final int LEVELS = 7;

    private static void draw(Context ctx, Bitmap out) {
        Canvas c = new Canvas(out);
        Random r = new Random(1937);
        Bitmap[] covers = new Bitmap[8];
        for (int i = 0; i < covers.length; i++) covers[i] = PosterCache.decodeAsset(ctx, "posters/poster_" + (i + 1) + ".png", 200, 300);

        c.drawColor(Color.parseColor("#2A2B2E"));
        // ceiling with its light tube, floor carpet
        Paint ceil = p();
        ceil.setShader(new LinearGradient(0, 0, 0, BT, Color.parseColor("#C9C8C4"), Color.parseColor("#E9E8E4"), Shader.TileMode.CLAMP));
        Path cp = new Path();
        cp.moveTo(0, 0); cp.lineTo(W, 0); cp.lineTo(BR, BT); cp.lineTo(BL, BT); cp.close();
        c.drawPath(cp, ceil);
        Paint floor = p();
        floor.setShader(new LinearGradient(0, BB, 0, H, Color.parseColor("#3A3936"), Color.parseColor("#22211F"), Shader.TileMode.CLAMP));
        Path fp = new Path();
        fp.moveTo(BL, BB); fp.lineTo(BR, BB); fp.lineTo(W, H); fp.lineTo(0, H); fp.close();
        c.drawPath(fp, floor);

        // back wall: white shelves, packed
        float gap = (BB - BT) / LEVELS;
        Paint back = p();
        back.setShader(new LinearGradient(0, BT, 0, BB, Color.parseColor("#D8D8D6"), Color.parseColor("#B9BAB9"), Shader.TileMode.CLAMP));
        c.drawRect(BL, BT, BR, BB, back);
        for (int lv = 0; lv < LEVELS; lv++) {
            float top = BT + lv * gap + 10, bottom = BT + (lv + 1) * gap - 6;
            row(c, r, covers, BL + 8, BR - 8, top, bottom);
            board(c, BL, BR, bottom, 12);
        }

        // side walls: the same shelves running towards the door, seen at an angle
        for (int side = 0; side < 2; side++) wall(c, r, side == 0);

        // light: the tube on the ceiling, falling off towards the floor and the door
        Paint light = p();
        light.setShader(new RadialGradient(VX, BT * 0.4f, W * 0.7f, new int[]{Color.argb(70, 255, 255, 250), Color.argb(0, 255, 255, 250)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, light);
        Paint tube = p();
        tube.setColor(Color.parseColor("#FBFBF7"));
        c.drawRoundRect(new RectF(VX - W * 0.12f, BT * 0.25f, VX + W * 0.12f, BT * 0.55f), 6, 6, tube);
        Paint fall = p();
        fall.setShader(new LinearGradient(0, 0, 0, H, Color.argb(0, 0, 0, 0), Color.argb(120, 0, 0, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fall);

        depthOfField(out, c);
        // grain and the lens's vignette
        Paint grain = new Paint();
        for (int i = 0; i < 90000; i++) {
            grain.setColor(r.nextBoolean() ? Color.argb(10, 255, 255, 255) : Color.argb(12, 0, 0, 0));
            c.drawPoint(r.nextInt(W), r.nextInt(H), grain);
        }
        Paint vig = p();
        vig.setShader(new RadialGradient(VX, VY, W * 0.72f, new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 0, 0, 0)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, vig);
    }

    /** One shelf of the back wall: spines side by side, now and then a cover face out or a small stack. */
    private static void row(Canvas c, Random r, Bitmap[] covers, float x0, float x1, float top, float bottom) {
        Paint spine = p(), txt = p(), edge = p();
        txt.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        float x = x0;
        while (x < x1) {
            if (r.nextInt(23) == 0 && x + 90 < x1) {
                Bitmap cover = covers[r.nextInt(covers.length)];
                if (cover != null) {
                    float ch = (bottom - top) * 0.9f, cw = ch * 0.7f;
                    RectF d = new RectF(x + 2, bottom - ch, x + 2 + cw, bottom);
                    Paint sh = p();
                    sh.setColor(Color.argb(90, 0, 0, 0));
                    c.drawRect(d.left + 4, d.top + 4, d.right + 4, d.bottom, sh);
                    c.drawBitmap(cover, null, d, p());
                    Paint gloss = p();
                    gloss.setShader(new LinearGradient(d.left, d.top, d.right, d.bottom, Color.argb(60, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    c.drawRect(d, gloss);
                    x += cw + 6;
                    continue;
                }
            }
            float w = 9 + r.nextInt(8) + (r.nextInt(9) == 0 ? 10 : 0);
            float h = (bottom - top) * (0.84f + r.nextFloat() * 0.1f);
            int col = SPINES[r.nextInt(SPINES.length)];
            RectF s = new RectF(x, bottom - h, Math.min(x1, x + w), bottom);
            spine.setColor(col);
            c.drawRect(s, spine);
            // the rounded spine catches the light on one side
            edge.setShader(new LinearGradient(s.left, 0, s.right, 0, new int[]{Color.argb(70, 0, 0, 0), Color.argb(30, 255, 255, 255), Color.argb(80, 0, 0, 0)},
                    new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(s, edge);
            boolean dark = Color.red(col) + Color.green(col) + Color.blue(col) < 360;
            txt.setColor(dark ? Color.argb(220, 240, 236, 228) : Color.argb(220, 24, 22, 20));
            // the title, running up the spine, and the collection number at its top
            txt.setTextSize(Math.min(w * 0.62f, 9.5f));
            c.save();
            c.translate(s.centerX() + txt.getTextSize() * 0.36f, s.bottom - 16);
            c.rotate(-90);
            String t = TITLES[r.nextInt(TITLES.length)].toUpperCase(java.util.Locale.ROOT);
            float maxLen = h - 34;
            while (txt.measureText(t) > maxLen && t.length() > 3) t = t.substring(0, t.length() - 1);
            c.drawText(t, 0, 0, txt);
            c.restore();
            txt.setTextSize(Math.min(w * 0.5f, 7.5f));
            String num = String.valueOf(100 + r.nextInt(1150));
            c.drawText(num, s.centerX() - txt.measureText(num) / 2f, s.top + 10, txt);
            x += w + (r.nextInt(5) == 0 ? 1 : 0);
        }
        // the shelf above shades the top of this row
        Paint shade = p();
        shade.setShader(new LinearGradient(0, top - 10, 0, top + (bottom - top) * 0.45f, Color.argb(150, 10, 10, 12), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(x0 - 8, top - 10, x1 + 8, top + (bottom - top) * 0.45f, shade);
    }

    /** A white laminate board seen from the front: bright edge, shadow under it. */
    private static void board(Canvas c, float x0, float x1, float y, float thick) {
        Paint b = p();
        b.setShader(new LinearGradient(0, y, 0, y + thick, new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#E9E9E7"), Color.parseColor("#C2C3C4")},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(x0, y, x1, y + thick, b);
        Paint sh = p();
        sh.setShader(new LinearGradient(0, y + thick, 0, y + thick + 16, Color.argb(110, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(x0, y + thick, x1, y + thick + 16, sh);
    }

    /** A side wall: each shelf as a strip running from the door (screen edge) to the back wall. */
    private static void wall(Canvas c, Random r, boolean left) {
        float bx = left ? BL : BR, ex = left ? 0 : W;
        float gap = (BB - BT) / LEVELS;
        Paint wallP = p();
        wallP.setShader(new LinearGradient(ex, 0, bx, 0, Color.parseColor("#9C9D9E"), Color.parseColor("#C6C7C6"), Shader.TileMode.CLAMP));
        Path wp = new Path();
        wp.moveTo(ex, 0); wp.lineTo(bx, BT); wp.lineTo(bx, BB); wp.lineTo(ex, H); wp.close();
        c.drawPath(wp, wallP);
        Paint spine = p(), shade = p();
        for (int lv = 0; lv < LEVELS; lv++) {
            float yTopB = BT + lv * gap + 10, yBotB = BT + (lv + 1) * gap - 6;
            // the same heights at the door, spread away from the eye level
            float k = 2.25f;
            float yTopE = VY + (yTopB - VY) * k, yBotE = VY + (yBotB - VY) * k;
            float t = 0f;
            while (t < 1f) {
                // spines get narrower towards the back wall (perspective)
                float dt = (0.010f + r.nextFloat() * 0.008f) * (1.25f - t * 0.7f);
                float t1 = Math.min(1f, t + dt);
                float hFrac = 0.84f + r.nextFloat() * 0.1f;
                float xa = ex + (bx - ex) * t, xb = ex + (bx - ex) * t1;
                float botA = yBotE + (yBotB - yBotE) * t, botB = yBotE + (yBotB - yBotE) * t1;
                float topA = botA - (botA - (yTopE + (yTopB - yTopE) * t)) * hFrac;
                float topB = botB - (botB - (yTopE + (yTopB - yTopE) * t1)) * hFrac;
                Path s = new Path();
                s.moveTo(xa, topA); s.lineTo(xb, topB); s.lineTo(xb, botB); s.lineTo(xa, botA); s.close();
                int col = SPINES[r.nextInt(SPINES.length)];
                spine.setColor(col);
                c.drawPath(s, spine);
                // the spines face the aisle: lit where they turn to the ceiling light, darker near the door
                shade.setColor(Color.argb((int) (90 * (1f - t)) + 20, 0, 0, 0));
                c.drawPath(s, shade);
                shade.setColor(Color.argb(40, 0, 0, 0));
                c.drawLine(xb, topB, xb, botB, shade);
                t = t1 + 0.0015f;
            }
            // the board under the row, as a strip in perspective
            float yb0 = yBotE, yb1 = yBotB;
            Paint board = p();
            board.setShader(new LinearGradient(0, Math.min(yb0, yb1), 0, Math.max(yb0, yb1) + 30, Color.parseColor("#F2F2F0"), Color.parseColor("#BDBEBF"), Shader.TileMode.CLAMP));
            Path bp = new Path();
            bp.moveTo(ex, yb0); bp.lineTo(bx, yb1); bp.lineTo(bx, yb1 + 12); bp.lineTo(ex, yb0 + 12 * 2.25f); bp.close();
            c.drawPath(bp, board);
            // shade from the board above
            float ya0 = VY + (BT + lv * gap - VY) * k, ya1 = BT + lv * gap;
            Paint top = p();
            top.setColor(Color.argb(70, 0, 0, 0));
            Path tp = new Path();
            tp.moveTo(ex, ya0); tp.lineTo(bx, ya1); tp.lineTo(bx, ya1 + gap * 0.3f); tp.lineTo(ex, ya0 + gap * 0.3f * k); tp.close();
            c.drawPath(tp, top);
        }
    }

    /** The lens is focused on the back wall: the side walls soften towards the door. */
    private static void depthOfField(Bitmap out, Canvas c) {
        Bitmap small = Bitmap.createScaledBitmap(out, W / 4, H / 4, true);
        Bitmap soft = Blur.stack(small, 5);
        Bitmap layer = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        lc.drawBitmap(soft, null, new RectF(0, 0, W, H), p());
        // keep the blur only where the walls come closest to the lens: fully at the door, none at the back wall
        Paint mask = new Paint();
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mask.setShader(new LinearGradient(0, 0, W, 0,
                new int[]{Color.argb(255, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(255, 0, 0, 0)},
                new float[]{0f, BL / W, BR / W, 1f}, Shader.TileMode.CLAMP));
        lc.drawRect(0, 0, W, H, mask);
        c.drawBitmap(layer, 0, 0, null);
        small.recycle();
        layer.recycle();
    }
}
