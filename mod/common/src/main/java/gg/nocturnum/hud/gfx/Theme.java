package gg.nocturnum.hud.gfx;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.module.ClickGuiModule;
import gg.nocturnum.hud.util.Colors;

/** Colour theme + the shared look of all plates (spec section 2). */
public final class Theme {
    private Theme() {}

    public record Preset(String id, int accent, int accent2) {}

    public static final Preset[] PRESETS = {
        new Preset("nocturnum", 0xFF8B7CFF, 0xFFC084FC),
        new Preset("ice", 0xFF4FD6E8, 0xFF7AA2FF),
        new Preset("toxic", 0xFF7CFF6B, 0xFF2ECC71),
        new Preset("sakura", 0xFFFF8CC6, 0xFFFFB3D9),
        new Preset("mono", 0xFFFFFFFF, 0xFFA0A0B8),
        new Preset("red", 0xFFFF5A6A, 0xFFFF9A5A),
        new Preset("blue", 0xFF4A9BFF, 0xFF4AE0FF),
    };

    private static ClickGuiModule cg;

    private static ClickGuiModule cg() {
        if (cg == null) cg = Config.get(ClickGuiModule.class);
        return cg;
    }

    private static boolean custom() { return cg().theme.get() >= PRESETS.length; }

    public static int accent() {
        ClickGuiModule m = cg();
        if (custom()) return m.customAccent.argb();
        return PRESETS[m.theme.get()].accent;
    }

    public static int accent2() {
        ClickGuiModule m = cg();
        if (custom()) return Colors.lerp(m.customAccent.argb(), 0xFFFFFFFF, 0.25f);
        return PRESETS[m.theme.get()].accent2;
    }

    public static int accent(int alpha) { return Colors.withAlpha(accent(), alpha); }

    /** Gradient accent at 0..1 along a list or bar. */
    public static int gradient(float t) { return Colors.lerp(accent(), accent2(), t); }

    public static final int TEXT = 0xFFF2F1FF;
    public static final int TEXT_DIM = 0xFFA9A7CC;
    public static final int TEXT_FAINT = 0xFF77759F;
    public static final int OK = 0xFF4ADE80;
    public static final int BAD = 0xFFFF6B81;
    public static final int WARN = 0xFFFBBF24;

    public static int plateBg() { return Colors.withAlpha(0x0E0E18, Math.round(255 * cg().hudOpacity.get() / 100f)); }
    public static float radius() { return cg().hudRadius.get(); }
    public static float border() { return cg().hudBorder.get(); }
    public static float glowStrength() { return cg().hudGlow.get() / 100f; }
    public static boolean lowEnd() { return cg().lowEnd.get(); }

    /** Standard glass plate used by every HUD element. */
    public static void plate(Gfx g, float x, float y, float w, float h) { plate(g, x, y, w, h, false); }

    public static void plate(Gfx g, float x, float y, float w, float h, boolean active) {
        float r = radius();
        if (!lowEnd() && active) Draw.glow(g, x, y, w, h, r, accent(255), glowStrength());
        Draw.rect(g, x, y, w, h, r, plateBg());
        if (border() > 0) Draw.ring(g, x, y, w, h, r, border(), accent(Math.round(255 * 0.4f)));
    }
}
