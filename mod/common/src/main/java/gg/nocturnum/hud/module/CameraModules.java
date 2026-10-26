package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.ui.Notifications;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.gfx.Theme;

/** Camera and sky modules: Freelook and local weather. They change only what your own client draws. */
public final class CameraModules {
    private CameraModules() {}

    public static void registerAll() {
        Config.register(new Freelook());
        Config.register(new Weather());
    }

    /**
     * Look around without turning the character. Forbidden on some servers, so it switches itself off on
     * servers from the blacklist and can be disabled per server like the Anarchy features.
     */
    public static final class Freelook extends Module {
        public final Setting.Key hold = add(new Setting.Key("hold"));
        public final Setting.Bool thirdPerson = add(new Setting.Bool("thirdPerson", true));
        public final Setting.Num smooth = add(new Setting.Num("smooth", 0.25f, 0.05f, 1f, 0.05f));

        private boolean active, wasDown;
        public float yawOff, pitchOff;
        private int savedCamera = -1;

        public Freelook() {
            super("freelook", Category.CAMERA, false, false, 0, 0);
            hold.set(342); // left Alt
        }

        public static Freelook get() { return Config.get(Freelook.class); }

        /** True while the camera is detached from the player's rotation. */
        public boolean active() { return enabled() && active; }

        @Override
        public void onTick() {
            boolean allowed = Game.inWorld() && !Platform.screenOpen() && !AnarchyProfiles.blocked();
            boolean down = allowed && hold.get() >= 0 && Platform.isKeyDown(hold.get());
            if (down && !wasDown) {
                active = true;
                if (thirdPerson.get()) savedCamera = Platform.cameraType(true);
            } else if (!down && wasDown) {
                active = false;
                if (savedCamera >= 0) Platform.restoreCameraType(savedCamera);
                savedCamera = -1;
            }
            wasDown = down;
            if (!active) {
                // ease the camera back to where the player looks
                yawOff *= 1f - smooth.get();
                pitchOff *= 1f - smooth.get();
                if (Math.abs(yawOff) < 0.05f) yawOff = 0;
                if (Math.abs(pitchOff) < 0.05f) pitchOff = 0;
            }
        }

        @Override
        public void onDisable() {
            active = false;
            wasDown = false;
            yawOff = pitchOff = 0;
            if (savedCamera >= 0) Platform.restoreCameraType(savedCamera);
            savedCamera = -1;
        }

        /** Mouse movement while active: rotates the camera instead of the player. */
        public void turn(double dyaw, double dpitch) {
            yawOff += (float) (dyaw * 0.15);
            pitchOff += (float) (dpitch * 0.15);
        }
    }

    /** Clear sky, rain or storm only on your own screen; the server is not told. */
    public static final class Weather extends Module {
        public final Setting.Mode mode = add(new Setting.Mode("mode", 1, "vanilla", "clear", "rain", "thunder"));
        public final Setting.Num density = add(new Setting.Num("density", 100f, 10f, 100f, 5f, "%"));

        public Weather() {
            super("weather", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        /** Replacement for the rain level, or the vanilla value when this module is off. */
        public static float rain(float vanilla) {
            Weather w = Config.get(Weather.class);
            if (!w.enabled() || w.mode.is("vanilla")) return vanilla;
            return switch (w.mode.current()) {
                case "clear" -> 0f;
                default -> w.density.get() / 100f;
            };
        }

        public static float thunder(float vanilla) {
            Weather w = Config.get(Weather.class);
            if (!w.enabled() || w.mode.is("vanilla")) return vanilla;
            return w.mode.is("thunder") ? w.density.get() / 100f : 0f;
        }
    }

    static void info(String key) {
        Notifications.push(Lang.t("category.camera"), Lang.t(key), Theme.accent());
    }
}
