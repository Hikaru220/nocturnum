package gg.nocturnum.hud.util;

public final class Colors {
    private Colors() {}

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    public static int a(int c) { return c >>> 24; }
    public static int r(int c) { return (c >> 16) & 255; }
    public static int g(int c) { return (c >> 8) & 255; }
    public static int b(int c) { return c & 255; }

    public static int withAlpha(int c, int a) { return (clamp(a) << 24) | (c & 0xFFFFFF); }

    public static int mulAlpha(int c, float k) { return withAlpha(c, Math.round(a(c) * Math.max(0, Math.min(1, k)))); }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        return argb(
            Math.round(a(c1) + (a(c2) - a(c1)) * t),
            Math.round(r(c1) + (r(c2) - r(c1)) * t),
            Math.round(g(c1) + (g(c2) - g(c1)) * t),
            Math.round(b(c1) + (b(c2) - b(c1)) * t));
    }

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float r, g, b;
        int sector = (int) (h * 6);
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return argb(255, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    /** Smoothly cycling colour for the "rainbow" mode. */
    public static int rainbow(float offset) {
        float t = (System.currentTimeMillis() % 6000L) / 6000f;
        return hsv(t + offset, 0.65f, 1f);
    }

    public static String toHex(int c) { return String.format("#%06X", c & 0xFFFFFF); }

    public static int parseHex(String s, int fallback) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.length() == 6) return 0xFF000000 | Integer.parseInt(t, 16);
            if (t.length() == 8) return (int) Long.parseLong(t, 16);
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }
}
