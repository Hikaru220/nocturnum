package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.ui.Notifications;
import gg.nocturnum.hud.util.Lang;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registration plus the comfort, performance and anarchy modules of package 2 that need no custom drawing. */
public final class PackModules {
    private PackModules() {}

    public static void registerAll() {
        Config.register(new PackHud.ItemCounter());
        Config.register(new PackHud.Saturation());
        Config.register(new PackHud.Combo());
        Config.register(new PackHud.Cooldowns());
        Config.register(new PackHud.BowPower());
        Config.register(new PackHud.Memory());
        Config.register(new PackHud.Reach());
        Config.register(new PackHud.Compass());
        Config.register(new PackHud.PlayTime());
        Config.register(new PackHud.Stopwatch());
        Config.register(new PackHud.PerfMonitor());
        Config.register(new MediaPlayerModule());
        Config.register(new CommandBinds());
        Config.register(new BackgroundFps());
        Config.register(new Boost());
        Config.register(new Fullbright());
        AnarchyModules.registerAll();
        SoundModules.registerAll();
        ViewTweaks.registerAll();
        SocialModules.registerAll();
        LabelModules.registerAll();
        CameraModules.registerAll();
        ComfortModules.registerAll();
        PostModules.registerAll();
    }

    /** Called on every client-side attack (from the platform event). */
    public static void onAttack(Entity e) {
        Config.get(TargetHudModule.class).onAttack(e);
        Config.get(PackHud.Combo.class).onAttack(e);
        Config.get(PackHud.Reach.class).onAttack(e);
        VisualModules.attack(e);
        SoundModules.onAttack(e);
        Config.get(AnarchyModules.TargetEsp.class).onAttack(e);
    }

    /** Sends a command or message on a key press, as if the player typed it. At most once per second per bind. */
    public static final class CommandBinds extends Module {
        public static final int COUNT = 20;
        private static final Set<String> DANGEROUS = Set.of("tpaccept", "tpyes", "pay", "give", "clear", "kill", "tpa", "sell", "accept");
        private final Setting.Key[] keys = new Setting.Key[COUNT];
        private final Setting.Text[] texts = new Setting.Text[COUNT];
        private final boolean[] down = new boolean[COUNT];
        private final long[] last = new long[COUNT];
        private final Map<Integer, Long> armed = new HashMap<>();

        public CommandBinds() {
            super("commandbinds", Category.COMFORT, false, false, 0, 0);
            for (int i = 0; i < COUNT; i++) {
                Setting.Key k = new Setting.Key("key" + (i + 1));
                Setting.Text t = new Setting.Text("text" + (i + 1), i == 0 ? "/home" : i == 1 ? "/spawn" : "", 100);
                if (i >= 4) {
                    k.advanced = true;
                    t.advanced = true;
                }
                add(k);
                add(t);
                keys[i] = k;
                texts[i] = t;
            }
        }

        @Override
        public void onTick() {
            if (!Game.inWorld() || Platform.screenOpen()) return;
            long now = System.currentTimeMillis();
            for (int i = 0; i < COUNT; i++) {
                int k = keys[i].get();
                boolean d = k >= 0 && Platform.isKeyDown(k);
                if (d && !down[i] && !texts[i].get().isBlank() && now - last[i] >= 1000) fire(i, now);
                down[i] = d;
            }
        }

        private void fire(int i, long now) {
            String text = texts[i].get().trim();
            String head = text.startsWith("/") ? text.substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
            if (DANGEROUS.contains(head)) {
                Long t = armed.get(i);
                if (t == null || now - t > 3000) {
                    armed.put(i, now);
                    Notifications.push(Lang.t("module.commandbinds"), Lang.t("hud.confirmPress"), Theme.WARN);
                    return;
                }
                armed.remove(i);
            }
            last[i] = now;
            Platform.sendChat(text);
        }
    }

    /** Lowers the frame rate while the window is in the background. */
    public static final class BackgroundFps extends Module {
        public final Setting.Num limit = add(new Setting.Num("limit", 20f, 5f, 60f, 5f, " fps"));
        private int saved = -1;

        public BackgroundFps() {
            super("bgfps", Category.OTHER, false, false, 0, 0);
        }

        @Override
        public void onTick() {
            boolean bg = !Platform.windowActive();
            if (bg && saved < 0) {
                saved = Platform.fpsLimit();
                Platform.setFpsLimit(limit.asInt());
            } else if (!bg && saved >= 0) {
                Platform.setFpsLimit(saved);
                saved = -1;
            }
        }

        @Override
        public void onDisable() {
            if (saved >= 0) Platform.setFpsLimit(saved);
            saved = -1;
        }
    }

    /** One switch that cuts particles, shadows, clouds and distance for big fights; can switch on by itself when FPS drops. */
    public static final class Boost extends Module {
        public final Setting.Num autoBelow = add(new Setting.Num("autoBelow", 0f, 0f, 120f, 5f, " fps"));
        private boolean applied;
        private long lowSince;

        public Boost() {
            super("boost", Category.OTHER, false, false, 0, 0);
        }

        @Override public void onEnable() { apply(true); }
        @Override public void onDisable() { apply(false); }

        private void apply(boolean on) {
            if (on == applied) return;
            applied = on;
            Platform.boost(on);
        }

        /** Called every tick even when the module is off: switch on when FPS stays under the limit for 5 seconds. */
        public void watch() {
            float n = autoBelow.get();
            if (n <= 0 || enabled() || !Game.inWorld()) {
                lowSince = 0;
                return;
            }
            if (Game.fps() < n) {
                if (lowSince == 0) lowSince = System.currentTimeMillis();
                else if (System.currentTimeMillis() - lowSince > 5000) {
                    lowSince = 0;
                    setEnabled(true);
                }
            } else {
                lowSince = 0;
            }
        }
    }

    /** Makes everything bright, even in dark caves. */
    public static final class Fullbright extends AnarchyModule {
        public final Setting.Mode method = add(new Setting.Mode("method", 0, "effect", "gamma"));
        public final Setting.Num strength = add(new Setting.Num("strength", 100f, 0f, 100f, 5f, "%"));
        private double savedGamma = -1;
        private float current;

        public Fullbright() {
            super("fullbright", false, 0, 0);
            visual = true;
        }

        @Override
        public void onTick() {
            float target = strength.get() / 100f;
            current += (target - current) * 0.1f; // smooth fade-in
            if (method.is("effect")) {
                Platform.nightVision(current > 0.4f);
            } else {
                if (savedGamma < 0) savedGamma = Platform.gamma();
                Platform.setGamma(savedGamma + (1.0 - savedGamma) * current);
            }
        }

        @Override
        public void onDisable() {
            Platform.nightVision(false);
            if (savedGamma >= 0) Platform.setGamma(savedGamma);
            savedGamma = -1;
            current = 0;
        }
    }
}
