package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import net.minecraft.world.effect.MobEffects;

/** Small first-person view tweaks that only change how YOUR screen looks. */
public final class ViewTweaks {
    private ViewTweaks() {}

    public static void registerAll() {
        Config.register(new FovStates());
        Config.register(new NoHurtShake());
        Config.register(new LowFire());
    }

    /** Own field of view for sprinting, slowness and flying instead of the vanilla "dash". */
    public static final class FovStates extends Module {
        public final Setting.Mode mode = add(new Setting.Mode("mode", 1, "vanilla", "none", "custom"));
        public final Setting.Num sprint = add(new Setting.Num("sprint", 6f, -30f, 30f, 1f, "°"));
        public final Setting.Num slow = add(new Setting.Num("slow", -4f, -30f, 30f, 1f, "°"));
        public final Setting.Num fly = add(new Setting.Num("fly", 0f, -30f, 30f, 1f, "°"));
        public final Setting.Num smooth = add(new Setting.Num("smooth", 0.15f, 0.02f, 1f, 0.01f));
        private float current;

        public FovStates() {
            super("fovstates", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        /** vanilla = result of the game's own calculation (with the dash); returns the FOV to use. */
        public float adjust(float vanilla, float base) {
            if (!enabled() || mode.is("vanilla") || !Game.inWorld()) return vanilla;
            float target = 0f;
            if (mode.is("custom")) {
                var p = Game.player();
                if (p.getAbilities().flying) target = fly.get();
                else if (p.isSprinting()) target = sprint.get();
                else if (p.hasEffect(MobEffects.SLOWNESS)) target = slow.get();
            }
            current += (target - current) * smooth.get();
            return base + current;
        }
    }

    /** Removes or softens the screen shake when you take damage. */
    public static final class NoHurtShake extends Module {
        public final Setting.Num strength = add(new Setting.Num("strength", 0f, 0f, 100f, 5f, "%"));

        public NoHurtShake() {
            super("nohurtshake", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        public static float scale() {
            NoHurtShake m = Config.get(NoHurtShake.class);
            return m.enabled() ? m.strength.get() / 100f : 1f;
        }
    }

    /** Lowers the fire overlay so burning does not block your view. */
    public static final class LowFire extends Module {
        public final Setting.Num height = add(new Setting.Num("height", 40f, 0f, 100f, 5f, "%"));

        public LowFire() {
            super("lowfire", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        /** Vertical shift (blocks) applied to the first-person fire quad. */
        public static double shift() {
            LowFire m = Config.get(LowFire.class);
            return m.enabled() ? -(1.0 - m.height.get() / 100.0) * 0.45 : 0.0;
        }
    }
}
