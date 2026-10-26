package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Lang;

import java.util.ArrayList;
import java.util.List;

/** Window listing modules that have a key bound, highlighting a row while its key is held. */
public final class KeybindsModule extends Module {
    public final Setting.Bool onlyEnabled = add(new Setting.Bool("onlyEnabled", false));

    public KeybindsModule() {
        super("keybinds", Category.HUD, false, true, 0.16f, 0.2f);
    }

    private List<Module> bound() {
        List<Module> l = new ArrayList<>();
        for (Module m : Config.modules()) {
            if (m.keybind.get() < 0 || m == this) continue;
            if (onlyEnabled.get() && !m.enabled()) continue;
            l.add(m);
        }
        return l;
    }

    @Override
    public int width(Gfx g) {
        int w = g.textW(Lang.t("module.keybinds")) + 16;
        for (Module m : bound()) w = Math.max(w, g.textW(Lang.t(m.nameKey())) + g.textW(Platform.keyName(m.keybind.get())) + 28);
        return Math.max(w, 90);
    }

    @Override public int height(Gfx g) { return 18 + Math.max(1, bound().size()) * 11 + 4; }

    @Override
    public void render(Gfx g, float pt) {
        int w = width(g), h = height(g);
        Theme.plate(g, 0, 0, w, h);
        g.text(Lang.t("module.keybinds"), 8, 5, Theme.gradient(0), true);
        g.fill(6, 16, w - 6, 17, Theme.accent(70));
        int y = 20;
        List<Module> l = bound();
        if (l.isEmpty()) g.text("—", 8, y, Theme.TEXT_FAINT, false);
        for (Module m : l) {
            boolean down = Platform.isKeyDown(m.keybind.get());
            if (down) Draw.rect(g, 4, y - 1, w - 8, 11, 3, Theme.accent(70));
            g.text(Lang.t(m.nameKey()), 8, y, m.enabled() ? Theme.TEXT : Theme.TEXT_DIM, false);
            String k = Platform.keyName(m.keybind.get());
            g.text(k, w - 8 - g.textW(k), y, m.enabled() ? Theme.accent() : Theme.TEXT_FAINT, false);
            y += 11;
        }
    }
}
