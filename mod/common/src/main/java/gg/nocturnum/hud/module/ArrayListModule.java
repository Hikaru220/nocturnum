package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** List of enabled modules; rows slide in from the side and the list shrinks smoothly when one is turned off. */
public final class ArrayListModule extends Module {
    public final Setting.Mode sort = add(new Setting.Mode("sort", 0, "length", "alphabet"));
    public final Setting.Bool gradient = add(new Setting.Bool("gradient", true));
    public final Setting.Bool includeHud = add(new Setting.Bool("includeHud", false));
    public final Setting.Bool background = add(new Setting.Bool("background", true));

    private static final class Row {
        final Tween show = new Tween(0), y = new Tween(0);
        boolean placed;
    }

    private final Map<Module, Row> rows = new HashMap<>();
    private static final int ROW_H = 12;

    public ArrayListModule() {
        super("arraylist", Category.HUD, true, true, 0.99f, 0.015f);
        anchorRight = true;
    }

    private boolean listed(Module m) {
        if (m == this || m.id.equals("clickgui") || m.id.equals("notifications")) return false;
        if (m.category == gg.nocturnum.hud.config.Category.OTHER) return false;
        if (m.hud && !includeHud.get()) return false;
        return true;
    }

    private List<Module> sorted(Gfx g) {
        List<Module> list = new ArrayList<>();
        for (Module m : Config.modules()) {
            if (!listed(m)) continue;
            Row r = rows.computeIfAbsent(m, k -> new Row());
            r.show.to(m.enabled() ? 1 : 0, 200, m.enabled() ? Easing.OUT_CUBIC : Easing.IN_QUAD);
            if (m.enabled() || r.show.get() > 0.01f) list.add(m);
        }
        if (sort.is("alphabet")) list.sort((a, b) -> Lang.t(a.nameKey()).compareToIgnoreCase(Lang.t(b.nameKey())));
        else list.sort((a, b) -> Integer.compare(g.textW(Lang.t(b.nameKey())), g.textW(Lang.t(a.nameKey()))));
        return list;
    }

    @Override
    public int width(Gfx g) {
        int w = 40;
        for (Module m : sorted(g)) w = Math.max(w, g.textW(Lang.t(m.nameKey())) + 12);
        return w;
    }

    @Override
    public int height(Gfx g) {
        int n = 0;
        for (Module ignored : sorted(g)) n++;
        return Math.max(ROW_H, n * ROW_H);
    }

    @Override
    public void render(Gfx g, float pt) {
        List<Module> list = sorted(g);
        int w = width(g);
        int i = 0;
        for (Module m : list) {
            Row r = rows.get(m);
            float target = i * ROW_H;
            if (!r.placed) {
                r.y.set(target);
                r.placed = true;
            } else {
                r.y.to(target, 200, Easing.OUT_CUBIC);
            }
            float t = r.show.get();
            String name = Lang.t(m.nameKey());
            int tw = g.textW(name);
            float rw = tw + 10;
            float x = w - rw + (1 - t) * (rw + 6);
            float y = r.y.get();
            int col = gradient.get() ? Theme.gradient(list.size() <= 1 ? 0 : i / (float) (list.size() - 1)) : Theme.accent();
            g.alpha = Math.max(0, Math.min(1, t));
            if (background.get()) Draw.rect(g, x, y, rw, ROW_H - 1, 3, Theme.plateBg());
            Draw.rect(g, x + rw - 2, y + 2, 1.5f, ROW_H - 5, 0.7f, col);
            g.text(name, Math.round(x + 4), Math.round(y + (ROW_H - g.fontH()) / 2f), Colors.withAlpha(col, 255), true);
            g.alpha = 1f;
            i++;
        }
    }
}
