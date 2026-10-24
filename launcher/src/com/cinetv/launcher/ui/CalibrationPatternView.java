package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * Full-screen reference patterns shown while the phone adjusts the picture:
 * gray ramp (gamma / tint), colour bars (saturation / hue), black and white level (brightness /
 * contrast, PLUGE-style), skin tones. Selected from the phone; "off" hides it.
 */
public class CalibrationPatternView extends View {

    private final Paint p = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String pattern = "off";

    public CalibrationPatternView(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        label.setColor(Color.argb(150, 255, 255, 255));
        label.setTextAlign(Paint.Align.CENTER);
    }

    public void show(String name) {
        pattern = name == null ? "off" : name;
        setVisibility("off".equals(pattern) ? GONE : VISIBLE);
        invalidate();
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setShader(null);
        switch (pattern) {
            case "gray": {
                int steps = 11;
                for (int i = 0; i < steps; i++) {
                    int v = Math.round(255f * i / (steps - 1));
                    p.setColor(Color.rgb(v, v, v));
                    c.drawRect(w * i / (float) steps, 0, w * (i + 1) / (float) steps, h * 0.6f, p);
                }
                p.setShader(new LinearGradient(0, 0, w, 0, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP));
                c.drawRect(0, h * 0.6f, w, h, p);
                break;
            }
            case "bars": {
                int[] bars = {0xFFBFBFBF, 0xFFBFBF00, 0xFF00BFBF, 0xFF00BF00, 0xFFBF00BF, 0xFFBF0000, 0xFF0000BF};
                for (int i = 0; i < bars.length; i++) {
                    p.setColor(bars[i]);
                    c.drawRect(w * i / 7f, 0, w * (i + 1) / 7f, h * 0.7f, p);
                }
                int[] low = {0xFF0000BF, 0xFF131313, 0xFFBF00BF, 0xFF131313, 0xFF00BFBF, 0xFF131313, 0xFFBFBFBF};
                for (int i = 0; i < low.length; i++) {
                    p.setColor(low[i]);
                    c.drawRect(w * i / 7f, h * 0.7f, w * (i + 1) / 7f, h * 0.78f, p);
                }
                p.setColor(0xFF101010);
                c.drawRect(0, h * 0.78f, w, h, p);
                break;
            }
            case "black": {
                c.drawColor(Color.BLACK);
                int[] levels = {0, 4, 8, 12, 16, 20};
                for (int i = 0; i < levels.length; i++) {
                    p.setColor(Color.rgb(levels[i], levels[i], levels[i]));
                    c.drawRect(w * (0.2f + i * 0.1f), h * 0.3f, w * (0.28f + i * 0.1f), h * 0.7f, p);
                }
                break;
            }
            case "white": {
                c.drawColor(Color.WHITE);
                int[] levels = {255, 251, 247, 243, 239, 235};
                for (int i = 0; i < levels.length; i++) {
                    p.setColor(Color.rgb(levels[i], levels[i], levels[i]));
                    c.drawRect(w * (0.2f + i * 0.1f), h * 0.3f, w * (0.28f + i * 0.1f), h * 0.7f, p);
                }
                break;
            }
            case "skin": {
                c.drawColor(Color.rgb(118, 118, 118));
                int[] skins = {0xFFF6D7C3, 0xFFE8B896, 0xFFD39C74, 0xFFB57B55, 0xFF8D5A3B, 0xFF5E3A26};
                for (int i = 0; i < skins.length; i++) {
                    p.setColor(skins[i]);
                    c.drawRoundRect(w * (0.1f + i * 0.135f), h * 0.32f, w * (0.22f + i * 0.135f), h * 0.68f, 24, 24, p);
                }
                break;
            }
            default:
                return;
        }
        label.setTextSize(h * 0.022f);
        label.setColor("white".equals(pattern) ? Color.argb(120, 0, 0, 0) : Color.argb(140, 255, 255, 255));
        c.drawText("CineTV · " + pattern, w / 2f, h * 0.95f, label);
    }
}
