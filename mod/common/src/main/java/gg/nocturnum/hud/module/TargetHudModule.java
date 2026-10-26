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
import gg.nocturnum.hud.util.Tween;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Name, health and armour of whoever you just hit. Only vanilla-synced data of an entity you already
 * touched is shown (nothing through walls). Off by default: forbidden on some servers.
 */
public final class TargetHudModule extends Module {
    public final Setting.Mode style = add(new Setting.Mode("style", 0, "compact", "large"));
    public final Setting.Num hold = add(new Setting.Num("hold", 3f, 1f, 8f, 0.5f, "s"));

    private LivingEntity target;
    private long hitAt;
    private final Tween hp = new Tween(1), ghost = new Tween(1), vis = new Tween(0);

    public TargetHudModule() {
        super("targethud", Category.HUD, false, true, 0.5f, 0.55f);
    }

    public void onAttack(Entity e) {
        if (e instanceof LivingEntity le && enabled()) {
            if (target != le) {
                float f = frac(le);
                hp.set(f);
                ghost.set(f);
            }
            target = le;
            hitAt = System.currentTimeMillis();
        }
    }

    private static float frac(LivingEntity e) {
        return Math.max(0, Math.min(1, e.getHealth() / Math.max(1f, e.getMaxHealth())));
    }

    private boolean active(boolean preview) {
        return preview || (target != null && target.isAlive() && System.currentTimeMillis() - hitAt < hold.get() * 1000);
    }

    @Override public int width(Gfx g) { return style.is("large") ? 150 : 120; }
    @Override public int height(Gfx g) { return style.is("large") ? 44 : 30; }

    public boolean previewMode;

    @Override
    public void render(Gfx g, float pt) {
        boolean act = active(previewMode);
        vis.to(act ? 1 : 0, act ? 200 : 300, Easing.OUT_CUBIC);
        float v = vis.get();
        if (v < 0.01f) return;
        g.alpha = v;
        int w = width(g), h = height(g);
        Theme.plate(g, 0, 0, w, h, true);
        float f;
        String name;
        int armor;
        if (target != null && !previewMode) {
            f = frac(target);
            name = Game.streamer() ? "Player" : target.getName().getString();
            armor = target.getArmorValue();
        } else {
            f = 0.7f;
            name = "Target";
            armor = 12;
        }
        // red bar follows damage quickly, the white "ghost" catches up slowly
        hp.to(f, 120, Easing.OUT_CUBIC);
        ghost.to(f, 400, Easing.OUT_EXPO);
        int bx = 30, bw = w - bx - 8, by = h - 11;
        Draw.rect(g, 6, 6, 18, 18, 3, Colors.withAlpha(0xFFFFFF, 25));
        if (target != null && !previewMode) Draw.rect(g, 8, 8, 14, 14, 2, Theme.accent(180));
        g.text(name, bx, 6, Theme.TEXT, true);
        Draw.rect(g, bx, by, bw, 4, 2, Colors.withAlpha(0xFFFFFF, 30));
        Draw.rect(g, bx, by, Math.max(2, bw * ghost.get()), 4, 2, 0xFFFFFFFF);
        int col = f > 0.5f ? Theme.OK : f > 0.25f ? Theme.WARN : Theme.BAD;
        Draw.rect(g, bx, by, Math.max(2, bw * hp.get()), 4, 2, col);
        String hpText = target != null && !previewMode ? String.format("%.1f", target.getHealth()) : "14.0";
        g.text(hpText + "  ◆" + armor, bx, by - g.fontH() - 1 + (style.is("large") ? 0 : 99999), Theme.TEXT_DIM, false);
        g.alpha = 1f;
    }
}
