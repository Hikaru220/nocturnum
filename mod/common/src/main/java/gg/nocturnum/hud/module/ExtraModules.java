package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Lang;

import java.nio.file.Files;
import java.nio.file.Path;

/** Camera, comfort and misc modules that do not need world rendering. */
public final class ExtraModules {
    private ExtraModules() {}

    public static void registerAll() {
        Config.register(new Crosshair());
        Config.register(new Zoom());
        Config.register(new ToggleSprint());
        Config.register(new DeathCoords());
        Config.register(new DiscordRpc());
        Config.register(new LoadingScreenModule());
        Config.register(new MainMenuModule());
        PackModules.registerAll();
        VisualModules.registerAll();
    }

    /** Custom crosshair: shape, colour, size, centre dot, expands when you attack. */
    public static final class Crosshair extends Module {
        public final Setting.Mode shape = add(new Setting.Mode("shape", 0, "cross", "dot", "circle", "square"));
        public final Setting.Color color = add(new Setting.Color("color", 0xFFFFFFFF));
        public final Setting.Num size = add(new Setting.Num("size", 5f, 2f, 14f, 1f, "px"));
        public final Setting.Num thickness = add(new Setting.Num("thickness", 1f, 1f, 3f, 1f, "px"));
        public final Setting.Num gap = add(new Setting.Num("gap", 2f, 0f, 8f, 1f, "px"));
        public final Setting.Bool centerDot = add(new Setting.Bool("centerDot", false));
        public final Setting.Bool outline = add(new Setting.Bool("outline", true));
        public final Setting.Bool expand = adv(new Setting.Bool("expand", true));

        private long attackAt;

        public Crosshair() {
            super("crosshair", Category.VISUALS, false, false, 0, 0);
            visual = true;
            preset("minimal", "size", 4f, "gap", 2f, "centerDot", false);
            preset("bright", "size", 7f, "thickness", 2f, "gap", 3f, "centerDot", true);
            preset("pvp", "size", 5f, "thickness", 1f, "gap", 2f, "centerDot", true);
        }

        @Override
        public void onTick() {
            if (Game.mc().options.keyAttack.isDown()) attackAt = System.currentTimeMillis();
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (Platform.screenOpen() || Game.mc().options.getCameraType().isFirstPerson() == false) return;
            float cx = g.w() / 2f, cy = g.h() / 2f;
            float grow = expand.get() ? Math.max(0, 1f - (System.currentTimeMillis() - attackAt) / 250f) * 3f : 0;
            int c = color.argb();
            float s = size.get(), t = thickness.get(), gp = gap.get() + grow;
            int o = 0xCC000000;
            switch (shape.current()) {
                case "dot" -> dot(g, cx, cy, t + 1, c);
                case "circle" -> {
                    if (outline.get()) Draw.ring(g, cx - s - 1, cy - s - 1, (s + 1) * 2, (s + 1) * 2, s + 1, t + 2, o);
                    Draw.ring(g, cx - s, cy - s, s * 2, s * 2, s, t, c);
                }
                case "square" -> {
                    if (outline.get()) Draw.ring(g, cx - s - 1, cy - s - 1, (s + 1) * 2, (s + 1) * 2, 0, t + 2, o);
                    Draw.ring(g, cx - s, cy - s, s * 2, s * 2, 0, t, c);
                }
                default -> {
                    if (outline.get()) {
                        arms(g, cx, cy, s + 1, t + 2, gp - 1, o);
                    }
                    arms(g, cx, cy, s, t, gp, c);
                }
            }
            if (centerDot.get() && !shape.is("dot")) dot(g, cx, cy, Math.max(1, t), c);
        }

        private static void dot(Gfx g, float cx, float cy, float r, int c) {
            g.fill(Math.round(cx - r / 2f), Math.round(cy - r / 2f), Math.round(cx + r / 2f + 0.5f), Math.round(cy + r / 2f + 0.5f), c);
        }

        private static void arms(Gfx g, float cx, float cy, float len, float t, float gap, int c) {
            int th = Math.round(t);
            int x0 = Math.round(cx - t / 2f), y0 = Math.round(cy - t / 2f);
            g.fill(x0, Math.round(cy - gap - len), x0 + th, Math.round(cy - gap), c);
            g.fill(x0, Math.round(cy + gap), x0 + th, Math.round(cy + gap + len), c);
            g.fill(Math.round(cx - gap - len), y0, Math.round(cx - gap), y0 + th, c);
            g.fill(Math.round(cx + gap), y0, Math.round(cx + gap + len), y0 + th, c);
        }
    }

    /** Spyglass-like zoom while the key is held. Only changes your own field of view. */
    public static final class Zoom extends Module {
        public final Setting.Num factor = add(new Setting.Num("factor", 3f, 1.5f, 10f, 0.5f, "x"));
        public final Setting.Bool smooth = add(new Setting.Bool("smooth", true));
        public final Setting.Bool scroll = add(new Setting.Bool("scroll", true));
        private float current = 1f;

        public Zoom() {
            super("zoom", Category.CAMERA, true, false, 0, 0);
            keybind.set(67);
            keybind.hold = true;
        }

        /** Divisor applied to the field of view, 1 = no zoom. Called from the FOV mixin. */
        public float divisor() {
            boolean active = Platform.isKeyDown(keybind.get()) && !Platform.screenOpen();
            float target = active ? factor.get() : 1f;
            current = smooth.get() ? current + (target - current) * 0.2f : target;
            if (Math.abs(current - target) < 0.01f) current = target;
            return current;
        }

        public void wheel(double dy) {
            if (scroll.get() && Platform.isKeyDown(keybind.get())) factor.setClamped(factor.get() + (dy > 0 ? 0.5f : -0.5f));
        }

        @Override
        public void setEnabled(boolean v) {
            // the zoom key is a hold key handled by the FOV hook, the module switch means "feature available"
            super.setEnabled(v);
        }
    }

    /** Sprint without holding the key. Same speed as normal sprinting; just saves your finger. */
    public static final class ToggleSprint extends Module {
        public final Setting.Bool sneakToggle = add(new Setting.Bool("sneakToggle", false));
        private boolean sneakLatched;
        private boolean lastSneakDown;

        public ToggleSprint() {
            super("togglesprint", Category.COMFORT, false, false, 0, 0);
        }

        @Override
        public void onTick() {
            var mc = Game.mc();
            if (!Game.inWorld() || Platform.screenOpen()) return;
            var o = mc.options;
            if (o.keyUp.isDown()) o.keySprint.setDown(true);
            if (sneakToggle.get()) {
                boolean down = o.keyShift.isDown();
                if (down && !lastSneakDown) sneakLatched = !sneakLatched;
                lastSneakDown = down;
                if (sneakLatched) o.keyShift.setDown(true);
            }
        }

        @Override
        public void onDisable() { sneakLatched = false; }
    }

    /** After you die, prints where it happened in your own chat. */
    public static final class DeathCoords extends Module {
        private boolean reported;

        public DeathCoords() {
            super("deathcoords", Category.COMFORT, true, false, 0, 0);
        }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            var p = Game.player();
            if (p.isDeadOrDying()) {
                if (!reported) {
                    reported = true;
                    String where = Game.streamer() ? "???" : String.format("%d / %d / %d", p.getBlockX(), p.getBlockY(), p.getBlockZ());
                    Platform.chat("[Nocturnum] " + Lang.t("comfort.died") + ": " + where);
                }
            } else {
                reported = false;
            }
        }
    }

    /** Writes the game state to a small file so the launcher can show it in Discord (the launcher owns the connection). */
    public static final class DiscordRpc extends Module {
        public final Setting.Bool showServer = add(new Setting.Bool("showServer", true));
        private String last = "";

        public DiscordRpc() {
            super("discordrpc", Category.OTHER, true, false, 0, 0);
        }

        @Override
        public void onTick() {
            String path = System.getProperty("nocturnum.state");
            if (path == null) return;
            String screen = !Game.inWorld() ? "menu" : Game.singleplayer() ? "singleplayer" : "multiplayer";
            String server = screen.equals("multiplayer") && showServer.get() && !Game.streamer() ? Game.serverAddress() : "";
            String json = "{\"screen\":\"" + screen + "\",\"server\":\"" + server.replace("\\", "").replace("\"", "") + "\"}";
            if (json.equals(last)) return;
            last = json;
            try {
                Path p = Path.of(path);
                Files.createDirectories(p.getParent());
                Files.writeString(p, json);
            } catch (Exception ignored) {
            }
        }
    }

    static int unused() {
        return Colors.a(0);
    }
}
