package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.platform.Platform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Short interface and combat sounds, each switchable, with its own volume. */
public final class SoundModules {
    private SoundModules() {}

    public static void registerAll() {
        Config.register(new UiSounds());
        Config.register(new HitSound());
        Config.register(new KillSound());
    }

    public static void onAttack(Entity e) {
        Config.get(HitSound.class).onAttack(e);
        Config.get(KillSound.class).onAttack(e);
    }

    /** Menu open/close, module switch and notification sounds. */
    public static final class UiSounds extends Module {
        public final Setting.Num volume = add(new Setting.Num("volume", 60f, 0f, 100f, 5f, "%"));
        public final Setting.Bool menu = add(new Setting.Bool("menu", true));
        public final Setting.Bool toggle = add(new Setting.Bool("toggle", true));
        public final Setting.Bool notify = add(new Setting.Bool("notify", true));
        public final Setting.Bool hover = add(new Setting.Bool("hover", false));

        public UiSounds() {
            super("uisounds", Category.SOUNDS, true, false, 0, 0);
        }

        private void play(String id, float pitch) { Platform.playUi(id, pitch, volume.get() / 100f); }

        private static UiSounds me() { return Config.get(UiSounds.class); }

        public static void menuOpen() {
            UiSounds u = me();
            if (u.enabled() && u.menu.get()) u.play("minecraft:block.beacon.activate", 1.6f);
        }

        public static void menuClose() {
            UiSounds u = me();
            if (u.enabled() && u.menu.get()) u.play("minecraft:block.beacon.deactivate", 1.8f);
        }

        public static void toggled(boolean state) {
            UiSounds u = me();
            if (u.enabled() && u.toggle.get()) u.play("minecraft:ui.button.click", state ? 1.5f : 0.9f);
        }

        public static void notification() {
            UiSounds u = me();
            if (u.enabled() && u.notify.get()) u.play("minecraft:block.note_block.bell", 1.7f);
        }
    }

    private static String pick(Setting.Mode set) {
        return switch (set.current()) {
            case "click" -> "minecraft:ui.button.click";
            case "bell" -> "minecraft:block.note_block.bell";
            case "bass" -> "minecraft:block.note_block.bass";
            case "silence" -> "";
            default -> "minecraft:entity.player.attack.crit";
        };
    }

    /** Sound when you hit a player or mob. */
    public static final class HitSound extends Module {
        public final Setting.Mode set = add(new Setting.Mode("set", 0, "classic", "click", "bell", "bass"));
        public final Setting.Num volume = add(new Setting.Num("volume", 70f, 0f, 100f, 5f, "%"));

        public HitSound() {
            super("hitsound", Category.SOUNDS, false, false, 0, 0);
        }

        void onAttack(Entity e) {
            if (enabled() && e instanceof LivingEntity) Platform.playUi(pick(set), 1.2f, volume.get() / 100f);
        }
    }

    /** Sound when the enemy dies from your hit (checked for a few seconds after the attack). */
    public static final class KillSound extends Module {
        public final Setting.Mode set = add(new Setting.Mode("set", 2, "classic", "click", "bell", "bass", "silence"));
        public final Setting.Num volume = add(new Setting.Num("volume", 80f, 0f, 100f, 5f, "%"));
        private Entity target;
        private long at;

        public KillSound() {
            super("killsound", Category.SOUNDS, false, false, 0, 0);
        }

        void onAttack(Entity e) {
            if (enabled() && e instanceof LivingEntity) {
                target = e;
                at = System.currentTimeMillis();
            }
        }

        @Override
        public void onTick() {
            if (target == null) return;
            if (!target.isAlive() || ((LivingEntity) target).getHealth() <= 0) {
                String s = pick(set);
                if (!s.isEmpty() && System.currentTimeMillis() - at < 3000) Platform.playUi(s, 0.8f, volume.get() / 100f);
                target = null;
            } else if (System.currentTimeMillis() - at > 3000) {
                target = null;
            }
        }
    }
}
