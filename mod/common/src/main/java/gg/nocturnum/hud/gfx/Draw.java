package gg.nocturnum.hud.gfx;

import gg.nocturnum.hud.util.Colors;

/**
 * Anti-aliased rounded shapes built from row fills. Cheap enough for dozens of plates per frame
 * and sharp at any GUI scale (no textures involved).
 */
public final class Draw {
    private Draw() {}

    /** Horizontal inset (in px) of a rounded corner at row centre dy (0 = top edge). */
    private static float inset(float dy, float h, float r) {
        float d = dy < r ? r - dy : (dy > h - r ? dy - (h - r) : 0);
        if (d <= 0) return 0;
        float s = r * r - d * d;
        return r - (float) Math.sqrt(Math.max(0, s));
    }

    private static void span(Gfx g, int y, float left, float right, int color) {
        if (right <= left) return;
        int l = (int) Math.ceil(left);
        int rr = (int) Math.floor(right);
        if (rr > l) g.fill(l, y, rr, y + 1, color);
        float lf = l - left; // partial pixel at the left edge
        if (lf > 0.02f) g.fill(l - 1, y, l, y + 1, Colors.mulAlpha(color, lf));
        float rf = right - rr;
        if (rf > 0.02f) g.fill(rr, y, rr + 1, y + 1, Colors.mulAlpha(color, rf));
    }

    public static void rect(Gfx g, float x, float y, float w, float h, float r, int color) {
        if (Colors.a(color) == 0 || w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2f);
        if (r < 0.5f) {
            g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
            return;
        }
        int rows = Math.round(h);
        int y0 = Math.round(y);
        for (int i = 0; i < rows; i++) {
            float in = inset(i + 0.5f, rows, r);
            span(g, y0 + i, x + in, x + w - in, color);
        }
    }

    /** Vertical gradient inside a rounded rectangle. */
    public static void rectGradient(Gfx g, float x, float y, float w, float h, float r, int top, int bottom) {
        r = Math.min(r, Math.min(w, h) / 2f);
        int rows = Math.round(h);
        int y0 = Math.round(y);
        for (int i = 0; i < rows; i++) {
            float in = inset(i + 0.5f, rows, r);
            span(g, y0 + i, x + in, x + w - in, Colors.lerp(top, bottom, rows <= 1 ? 0 : i / (float) (rows - 1)));
        }
    }

    /** Horizontal gradient inside a rounded rectangle (gradient accent bars). */
    public static void rectGradientH(Gfx g, float x, float y, float w, float h, float r, int left, int right) {
        r = Math.min(r, Math.min(w, h) / 2f);
        int rows = Math.round(h);
        int y0 = Math.round(y);
        int cols = Math.max(1, Math.round(w));
        int x0 = Math.round(x);
        for (int c = 0; c < cols; c++) {
            int col = Colors.lerp(left, right, cols <= 1 ? 0 : c / (float) (cols - 1));
            int top = 0, bot = rows;
            for (int i = 0; i < rows / 2; i++) {
                float in = inset(i + 0.5f, rows, r);
                if (c + 0.5f < in || c + 0.5f > cols - in) top = i + 1;
            }
            for (int i = rows - 1; i >= rows / 2; i--) {
                float in = inset(i + 0.5f, rows, r);
                if (c + 0.5f < in || c + 0.5f > cols - in) bot = i; else break;
            }
            if (bot > top) g.fill(x0 + c, y0 + top, x0 + c + 1, y0 + bot, col);
        }
    }

    /** Ring (border) of thickness t. */
    public static void ring(Gfx g, float x, float y, float w, float h, float r, float t, int color) {
        if (t <= 0 || Colors.a(color) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2f);
        int rows = Math.round(h);
        int y0 = Math.round(y);
        float ir = Math.max(0, r - t);
        for (int i = 0; i < rows; i++) {
            float o = inset(i + 0.5f, rows, r);
            if (i < t || i >= rows - t) {
                span(g, y0 + i, x + o, x + w - o, color);
            } else {
                float in = t + inset(i - t + 0.5f, rows - 2 * t, ir);
                float edge = Math.max(in, o);
                span(g, y0 + i, x + o, x + edge, color);
                span(g, y0 + i, x + w - edge, x + w - o, color);
            }
        }
    }

    /** Soft halo around a rounded rect. strength 0..1. */
    public static void glow(Gfx g, float x, float y, float w, float h, float r, int color, float strength) {
        if (strength <= 0.01f) return;
        int layers = 5;
        int base = Colors.a(color);
        for (int i = layers; i >= 1; i--) {
            float k = (float) Math.pow(1f - (i - 1) / (float) layers, 2.2) * strength * 0.16f;
            rect(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, Colors.withAlpha(color, Math.round(base * k)));
        }
    }

    public static void centeredText(Gfx g, String s, float cx, int y, int color, boolean shadow) {
        g.text(s, Math.round(cx - g.textW(s) / 2f), y, color, shadow);
    }

    public static void circle(Gfx g, float cx, float cy, float r, int color) {
        rect(g, cx - r, cy - r, r * 2, r * 2, r, color);
    }

    /** Nocturnum crescent mark (opens to the upper right), drawn row by row. */
    public static void crescent(Gfx g, float cx, float cy, float r, int color) {
        float ri = r * 0.82f, ox = r * 0.38f, oy = -r * 0.30f;
        int rows = Math.round(r * 2);
        int top = Math.round(cy - r);
        for (int i = 0; i < rows; i++) {
            float dy = i + 0.5f - r;
            float w1 = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            float l = cx - w1, rr = cx + w1;
            float dyi = dy - oy;
            if (Math.abs(dyi) < ri) {
                float w2 = (float) Math.sqrt(ri * ri - dyi * dyi);
                float il = cx + ox - w2, ir = cx + ox + w2;
                span(g, top + i, l, Math.min(rr, il), color);
                span(g, top + i, Math.max(l, ir), rr, color);
            } else {
                span(g, top + i, l, rr, color);
            }
        }
    }
}
