package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Lang;

/** Own start-up screen instead of the red Mojang one: logo with glow, thin progress bar, tip of the day. */
public final class LoadingScreenModule extends Module {
    public final Setting.Bool tips = add(new Setting.Bool("tips", true));
    public final Setting.Bool glow = add(new Setting.Bool("glow", true));

    private static final int TIP_COUNT = 8;
    private static final int TIP = (int) (Math.abs(System.nanoTime() / 1_000_003L) % TIP_COUNT) + 1;

    public LoadingScreenModule() {
        super("loading", Category.OTHER, true, false, 0, 0);
    }

    public static boolean on() {
        try {
            return Config.get(LoadingScreenModule.class).enabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static final int BACKGROUND = 0xFF090912;

    /** Background-independent part: logo + title. alpha follows vanilla's fade so the transition stays smooth. */
    public static void drawLogo(Gfx g, float alpha) {
        LoadingScreenModule m = Config.get(LoadingScreenModule.class);
        g.alpha = Math.max(0f, Math.min(1f, alpha));
        float cx = g.w() / 2f, cy = g.h() / 2f - 14;
        float breathe = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 600.0);
        int acc = Theme.accent();
        if (m.glow.get()) {
            // soft halo: several big translucent discs
            for (int i = 6; i >= 1; i--) {
                Draw.circle(g, cx, cy, 14 + i * 5, Colors.withAlpha(acc, Math.round(9 * breathe * (7 - i) / 6f + 2)));
            }
        }
        Draw.crescent(g, cx, cy, 20, acc);
        Draw.circle(g, cx + 15, cy - 15, 2.4f, 0xFFFFFFFF);
        Draw.circle(g, cx + 25, cy + 3, 1.5f, 0xFFFFFFFF);
        // Title is drawn with vector pixels: fonts are not loaded yet while the very first reload runs.
        drawPixelTitle(g, cx, cy + 38, 2);
        g.alpha = 1f;
    }

    /** Progress bar + caption + tip. */
    public static void drawProgress(Gfx g, float progress, float alpha) {
        LoadingScreenModule m = Config.get(LoadingScreenModule.class);
        g.alpha = Math.max(0f, Math.min(1f, alpha));
        float cx = g.w() / 2f, cy = g.h() / 2f + 66;
        float w = Math.min(220f, g.w() * 0.5f);
        float p = Math.max(0f, Math.min(1f, progress));
        Draw.rect(g, cx - w / 2f, cy, w, 3, 1.5f, Colors.withAlpha(0xFFFFFF, 26));
        Draw.rectGradientH(g, cx - w / 2f, cy, Math.max(3f, w * p), 3, 1.5f, Theme.accent(), Theme.accent2());
        String caption = Lang.t("loading.caption") + "  " + Math.round(p * 100) + "%";
        boolean fontsReady = g.textW(caption) > 0;
        if (fontsReady) Draw.centeredText(g, caption, cx, Math.round(cy + 9), Theme.TEXT_DIM, false);
        if (m.tips.get() && fontsReady) {
            String tip = Lang.t("loading.tip." + TIP);
            Draw.centeredText(g, tip, cx, g.h() - 28, Theme.TEXT_FAINT, false);
        }
        g.alpha = 1f;
    }

    private static final String[][] GLYPHS = {
        {"X...X", "XX..X", "X.X.X", "X..XX", "X...X", "X...X", "X...X"}, // N
        {".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."}, // O
        {".XXX.", "X...X", "X....", "X....", "X....", "X...X", ".XXX."}, // C
        {"XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."}, // T
        {"X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."}, // U
        {"XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"}, // R
        {"X...X", "XX.XX", "X.X.X", "X...X", "X...X", "X...X", "X...X"}, // M
    };
    private static final int[] TITLE = {0, 1, 2, 3, 4, 5, 0, 4, 6}; // N O C T U R N U M

    /** "NOCTURNUM" built from small rounded blocks, independent of any font. */
    private static void drawPixelTitle(Gfx g, float cx, float y, int px) {
        int gap = 3;
        int letterW = 5 * px;
        int total = TITLE.length * letterW + (TITLE.length - 1) * (gap * px);
        float x = cx - total / 2f;
        int i = 0;
        for (int gi : TITLE) {
            String[] rows = GLYPHS[gi];
            for (int r = 0; r < rows.length; r++)
                for (int c = 0; c < 5; c++)
                    if (rows[r].charAt(c) == 'X') Draw.rect(g, x + c * px, y + r * px, px, px, 0.6f, 0xFFFFFFFF);
            x += letterW + gap * px;
            i++;
        }
    }
}
