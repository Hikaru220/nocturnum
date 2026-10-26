package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Projection;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Colors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

/** Small text labels attached to things in the world (drawn on screen, only for what the game already renders). */
public final class LabelModules {
    private LabelModules() {}

    public static void registerAll() {
        Config.register(new TntTimer());
        Config.register(new SelfNameTag());
    }

    /** Seconds until a lit TNT explodes. */
    public static final class TntTimer extends Module {
        public final Setting.Bool warnColor = add(new Setting.Bool("warnColor", true));

        public TntTimer() {
            super("tnttimer", Category.COMFORT, false, false, 0, 0);
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (!Game.inWorld()) return;
            var me = Game.player();
            for (Entity e : Game.level().entitiesForRendering()) {
                if (!(e instanceof PrimedTnt tnt) || me.distanceToSqr(e) > 48 * 48 || !me.hasLineOfSight(e)) continue;
                float[] s = Projection.project(e.getX(), e.getY() + e.getBbHeight() + 0.6, e.getZ());
                if (s == null) continue;
                float sec = tnt.getFuse() / 20f;
                String t = String.format("%.1f", sec);
                int w = g.textW(t) + 10;
                int col = warnColor.get() && sec < 1.5f ? Theme.BAD : Theme.TEXT;
                Draw.rect(g, s[0] - w / 2f, s[1] - 6, w, 12, 5, Theme.plateBg());
                Draw.ring(g, s[0] - w / 2f, s[1] - 6, w, 12, 5, 1, Colors.withAlpha(col, 160));
                g.text(t, Math.round(s[0] - g.textW(t) / 2f), Math.round(s[1] - 4), col, true);
            }
        }
    }

    /** Your own name above your head in third-person view. */
    public static final class SelfNameTag extends Module {
        public final Setting.Bool background = add(new Setting.Bool("background", true));

        public SelfNameTag() {
            super("selfnametag", Category.CAMERA, false, false, 0, 0);
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (!Game.inWorld() || !Projection.thirdPerson) return;
            var p = Game.player();
            double x = p.xo + (p.getX() - p.xo) * pt, y = p.yo + (p.getY() - p.yo) * pt, z = p.zo + (p.getZ() - p.zo) * pt;
            float[] s = Projection.project(x, y + p.getBbHeight() + 0.55, z);
            if (s == null) return;
            String n = Game.playerName();
            int w = g.textW(n) + 8;
            if (background.get()) Draw.rect(g, s[0] - w / 2f, s[1] - 6, w, 12, 3, 0x66000000);
            g.text(n, Math.round(s[0] - g.textW(n) / 2f), Math.round(s[1] - 4), 0xFFFFFFFF, true);
        }
    }
}
