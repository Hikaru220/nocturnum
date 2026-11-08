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

    private static void span(Gfx g, int y, float left, float right, int color) { spanH(g, y, y + 1, left, right, color); }

    /** Same span for rows y0..y1 in one go (identical rows are merged into a single quad). */
    private static void spanH(Gfx g, int y0, int y1, float left, float right, int color) {
        if (right <= left || y1 <= y0) return;
        int l = (int) Math.ceil(left);
        int rr = (int) Math.floor(right);
        if (rr > l) g.fill(l, y0, rr, y1, color);
        float lf = l - left; // partial pixel at the left edge
        if (lf > 0.02f) g.fill(l - 1, y0, l, y1, Colors.mulAlpha(color, lf));
        float rf = right - rr;
        if (rf > 0.02f) g.fill(rr, y0, rr + 1, y1, Colors.mulAlpha(color, rf));
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
        int i = 0;
        while (i < rows) {
            float in = inset(i + 0.5f, rows, r);
            int j = i + 1;
            while (j < rows && inset(j + 0.5f, rows, r) == in) j++;
            spanH(g, y0 + i, y0 + j, x + in, x + w - in, color);
            i = j;
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
        int i = 0;
        while (i < rows) {
            float o = inset(i + 0.5f, rows, r);
            boolean cap = i < t || i >= rows - t;
            float edge = o;
            if (!cap) edge = Math.max(t + inset(i - t + 0.5f, rows - 2 * t, ir), o);
            int j = i + 1;
            while (j < rows) {
                float o2 = inset(j + 0.5f, rows, r);
                boolean cap2 = j < t || j >= rows - t;
                float e2 = o2;
                if (!cap2) e2 = Math.max(t + inset(j - t + 0.5f, rows - 2 * t, ir), o2);
                if (cap2 != cap || o2 != o || e2 != edge) break;
                j++;
            }
            if (cap) {
                spanH(g, y0 + i, y0 + j, x + o, x + w - o, color);
            } else {
                spanH(g, y0 + i, y0 + j, x + o, x + edge, color);
                spanH(g, y0 + i, y0 + j, x + w - edge, x + w - o, color);
            }
            i = j;
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

    public static void centeredBold(Gfx g, String s, float cx, int y, int color, boolean shadow) {
        g.textBold(s, Math.round(cx - g.textBoldW(s) / 2f), y, color, shadow);
    }

    public static void centeredText(Gfx g, String s, float cx, int y, int color, boolean shadow) {
        g.text(s, Math.round(cx - g.textW(s) / 2f), y, color, shadow);
    }

    /** Straight line of the given thickness built from small squares (no GL line state needed). */
    public static void line(Gfx g, float x1, float y1, float x2, float y2, float thick, int color) {
        if (Colors.a(color) == 0) return;
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 4000 || Float.isNaN(len)) return;
        if (Math.max(Math.max(x1, x2), Math.max(y1, y2)) < -50 || Math.min(Math.min(x1, x2), Math.min(y1, y2)) > 6000) return;
        int h = Math.max(1, Math.round(Math.max(1f, thick)));
        float half = h / 2f;
        // one quad per row (or column) instead of one per pixel
        if (Math.abs(dx) >= Math.abs(dy)) {
            int ya = Math.round(Math.min(y1, y2) - half), yb = Math.round(Math.max(y1, y2) - half);
            if (yb == ya) {
                g.fill(Math.round(Math.min(x1, x2) - half), ya, Math.round(Math.max(x1, x2) - half) + h, ya + h, color);
                return;
            }
            float slope = dx / dy;
            for (int y = ya; y <= yb; y++) {
                float c0 = x1 + (y + half - y1) * slope, c1 = x1 + (y + 1 + half - y1) * slope;
                float lo = Math.max(Math.min(x1, x2), Math.min(c0, c1)), hi = Math.min(Math.max(x1, x2), Math.max(c0, c1));
                g.fill(Math.round(lo - half), y, Math.round(hi - half) + h, y + h, color);
            }
        } else {
            int xa = Math.round(Math.min(x1, x2) - half), xb = Math.round(Math.max(x1, x2) - half);
            if (xb == xa) {
                g.fill(xa, Math.round(Math.min(y1, y2) - half), xa + h, Math.round(Math.max(y1, y2) - half) + h, color);
                return;
            }
            float slope = dy / dx;
            for (int x = xa; x <= xb; x++) {
                float c0 = y1 + (x + half - x1) * slope, c1 = y1 + (x + 1 + half - x1) * slope;
                float lo = Math.max(Math.min(y1, y2), Math.min(c0, c1)), hi = Math.min(Math.max(y1, y2), Math.max(c0, c1));
                g.fill(x, Math.round(lo - half), x + h, Math.round(hi - half) + h, color);
            }
        }
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
