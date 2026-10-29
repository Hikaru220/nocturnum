package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;
import net.minecraft.client.KeyMapping;

/** WASD, space and mouse buttons. A key fills with the accent colour while pressed. */
public final class KeystrokesModule extends Module {
    public final Setting.Bool showCps = add(new Setting.Bool("showCps", true));
    public final Setting.Bool showSpace = add(new Setting.Bool("showSpace", true));
    public final Setting.Bool showMouse = add(new Setting.Bool("showMouse", true));

    private final Tween[] t = {new Tween(0), new Tween(0), new Tween(0), new Tween(0), new Tween(0), new Tween(0), new Tween(0)};
    private static final int K = 22, GAP = 2;

    public KeystrokesModule() {
        super("keystrokes", Category.HUD, true, true, 0.84f, 0.64f);
    }

    @Override public int width(Gfx g) { return K * 3 + GAP * 2; }

    @Override
    public int height(Gfx g) {
        int h = K * 2 + GAP;
        if (showMouse.get()) h += K + GAP;
        if (showSpace.get()) h += 14 + GAP;
        return h;
    }

    private void key(Gfx g, int i, KeyMapping km, String label, int x, int y, int w, int h, String sub) {
        boolean down = km != null && km.isDown();
        t[i].to(down ? 1 : 0, down ? 70 : 140, Easing.OUT_CUBIC);
        float f = t[i].get();
        Draw.rect(g, x, y, w, h, Math.min(Theme.radius(), 5), Colors.lerp(Theme.plateBg(), Theme.accent(220), f));
        if (Theme.border() > 0) Draw.ring(g, x, y, w, h, Math.min(Theme.radius(), 5), 1, Theme.accent(Math.round(60 + 120 * f)));
        int tc = Colors.lerp(Theme.TEXT, 0xFF0D0D18, f);
        if (sub == null) {
            g.text(label, x + (w - g.textW(label)) / 2, y + (h - g.fontH()) / 2 + 1, tc, false);
        } else {
            g.text(label, x + (w - g.textW(label)) / 2, y + 3, tc, false);
            g.text(sub, x + (w - g.textW(sub)) / 2, y + h - g.fontH() - 1, Colors.lerp(Theme.TEXT_DIM, 0xFF0D0D18, f), false);
        }
    }

    @Override
    public void render(Gfx g, float pt) {
        var o = Game.mc().options;
        int x0 = K + GAP;
        key(g, 0, o.keyUp, "W", x0, 0, K, K, null);
        key(g, 1, o.keyLeft, "A", 0, K + GAP, K, K, null);
        key(g, 2, o.keyDown, "S", x0, K + GAP, K, K, null);
        key(g, 3, o.keyRight, "D", 2 * (K + GAP), K + GAP, K, K, null);
        int y = 2 * (K + GAP);
        if (showMouse.get()) {
            int mw = (width(g) - GAP) / 2;
            key(g, 4, o.keyAttack, Lang.t("hud.lmb"), 0, y, mw, K, showCps.get() ? Clicks.left() + " CPS" : null);
            key(g, 5, o.keyUse, Lang.t("hud.rmb"), mw + GAP, y, mw, K, showCps.get() ? Clicks.right() + " CPS" : null);
            y += K + GAP;
        }
        if (showSpace.get()) {
            key(g, 6, o.keyJump, "▁▁▁", 0, y, width(g), 14, null);
        }
    }
}
