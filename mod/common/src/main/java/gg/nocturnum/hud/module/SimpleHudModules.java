package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Small one-plate HUD elements. */
public final class SimpleHudModules {
    private SimpleHudModules() {}

    /** Base for plates that show a few lines of "label value" text. */
    abstract static class TextPlate extends Module {
        TextPlate(String id, boolean on, float x, float y) {
            super(id, Category.HUD, on, true, x, y);
        }

        abstract String[] lines();

        @Override
        public int width(Gfx g) {
            int w = 40;
            for (String s : lines()) w = Math.max(w, g.textW(s) + 14);
            return w;
        }

        @Override public int height(Gfx g) { return lines().length * (g.fontH() + 3) + 9; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            int y = 5;
            for (String s : lines()) {
                g.text(s, 7, y, Theme.TEXT, true);
                y += g.fontH() + 3;
            }
        }
    }

    public static final class Cps extends Module {
        public final Setting.Mode show = add(new Setting.Mode("show", 0, "left", "right", "both"));
        private final Tween l = new Tween(0), r = new Tween(0);

        public Cps() { super("cps", Category.HUD, true, true, 0.01f, 0.14f); }

        private String text() {
            l.to(Clicks.left(), 150, Easing.LINEAR);
            r.to(Clicks.right(), 150, Easing.LINEAR);
            int a = Math.round(l.get()), b = Math.round(r.get());
            return switch (show.current()) {
                case "left" -> a + " CPS";
                case "right" -> b + " CPS";
                default -> a + " | " + b + " CPS";
            };
        }

        @Override public int width(Gfx g) { return g.textW(text()) + 14; }
        @Override public int height(Gfx g) { return 18; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            g.text(text(), 7, 5, Theme.TEXT, true);
        }
    }

    public static final class FpsPing extends Module {
        public final Setting.Bool fps = add(new Setting.Bool("fps", true));
        public final Setting.Bool ping = add(new Setting.Bool("ping", true));
        private final Tween f = new Tween(0), p = new Tween(0);

        public FpsPing() { super("fpsping", Category.HUD, true, true, 0.01f, 0.085f); }

        private String text() {
            f.to(Game.fps(), 150, Easing.LINEAR);
            p.to(Math.max(0, Game.ping()), 150, Easing.LINEAR);
            StringBuilder sb = new StringBuilder();
            if (fps.get()) sb.append(Math.round(f.get())).append(" FPS");
            if (ping.get() && Game.ping() >= 0) sb.append(sb.length() > 0 ? "  |  " : "").append(Math.round(p.get())).append(" ms");
            return sb.length() == 0 ? "—" : sb.toString();
        }

        @Override public int width(Gfx g) { return g.textW(text()) + 14; }
        @Override public int height(Gfx g) { return 18; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            g.text(text(), 7, 5, Theme.TEXT, true);
        }
    }

    public static final class Coords extends TextPlate {
        public final Setting.Bool direction = add(new Setting.Bool("direction", true));
        public final Setting.Bool biome = add(new Setting.Bool("biome", true));
        public final Setting.Bool netherPortal = add(new Setting.Bool("netherPortal", true));

        public Coords() { super("coords", true, 0.01f, 0.2f); }

        @Override
        String[] lines() {
            if (!Game.inWorld()) return new String[]{"XYZ: —"};
            if (Game.streamer() && gg.nocturnum.hud.config.Config.get(StreamerModule.class).hideCoords.get()) return new String[]{"XYZ: ???"};
            var p = Game.player();
            java.util.List<String> l = new java.util.ArrayList<>();
            l.add(String.format("XYZ: %.1f / %.1f / %.1f", p.getX(), p.getY(), p.getZ()));
            if (netherPortal.get()) {
                String dim = Game.level().dimension().identifier().getPath();
                if (dim.equals("the_nether")) l.add(String.format("%s: %.0f / %.0f", Lang.t("hud.overworld"), p.getX() * 8, p.getZ() * 8));
                else if (dim.equals("overworld")) l.add(String.format("%s: %.0f / %.0f", Lang.t("hud.nether"), p.getX() / 8, p.getZ() / 8));
            }
            if (direction.get()) l.add(Lang.t("hud.facing") + ": " + Game.direction(p.getYRot()));
            if (biome.get()) l.add(Lang.t("hud.biome") + ": " + Game.biome());
            return l.toArray(new String[0]);
        }
    }

    public static final class ItemHand extends Module {
        private net.minecraft.world.item.ItemStack last = net.minecraft.world.item.ItemStack.EMPTY;
        private long changed;
        private final Tween vis = new Tween(0);

        public ItemHand() { super("itemhand", Category.HUD, false, true, 0.5f, 0.82f); }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            var cur = Game.player().getMainHandItem();
            if (!net.minecraft.world.item.ItemStack.matches(cur, last)) {
                last = cur.copy();
                changed = System.currentTimeMillis();
            }
        }

        private String name() { return last.isEmpty() ? "" : last.getHoverName().getString(); }

        @Override public int width(Gfx g) { return Math.max(60, g.textW(name()) + 34); }
        @Override public int height(Gfx g) { return 24; }

        @Override
        public void render(Gfx g, float pt) {
            boolean show = !last.isEmpty() && System.currentTimeMillis() - changed < 2000;
            vis.to(show ? 1 : 0, show ? 150 : 400, Easing.OUT_CUBIC);
            float v = vis.get();
            if (v < 0.01f) return;
            g.alpha = v;
            Theme.plate(g, 0, 0, width(g), height(g));
            g.item(last, 4, 4);
            g.text(name(), 24, 4, Theme.TEXT, true);
            if (last.isDamageableItem()) g.text((last.getMaxDamage() - last.getDamageValue()) + "/" + last.getMaxDamage(), 24, 13, Theme.TEXT_DIM, false);
            else if (last.getCount() > 1) g.text("x" + last.getCount(), 24, 13, Theme.TEXT_DIM, false);
            g.alpha = 1f;
        }
    }

    public static final class Totems extends Module {
        public final Setting.Bool warnZero = add(new Setting.Bool("warnZero", true));
        private int prev = -1;
        private long shake;

        public Totems() { super("totems", Category.HUD, false, true, 0.5f, 0.7f); }

        @Override
        public void onTick() {
            int c = Game.count(Items.TOTEM_OF_UNDYING);
            if (prev >= 0 && c < prev) shake = System.currentTimeMillis();
            prev = c;
        }

        @Override public int width(Gfx g) { return 44; }
        @Override public int height(Gfx g) { return 24; }

        @Override
        public void render(Gfx g, float pt) {
            int c = Math.max(0, prev);
            float age = (System.currentTimeMillis() - shake) / 400f;
            int dx = age < 1 ? Math.round((float) Math.sin(age * 30) * (1 - age) * 3) : 0;
            Theme.plate(g, 0, 0, width(g), height(g));
            g.item(Items.TOTEM_OF_UNDYING.getDefaultInstance(), 4 + dx, 4);
            boolean warn = warnZero.get() && c == 0;
            g.text(String.valueOf(c), 26, 8, warn ? Theme.BAD : Theme.TEXT, true);
        }
    }

    public static final class Clock extends Module {
        public final Setting.Bool hour12 = add(new Setting.Bool("hour12", false));
        public final Setting.Bool session = add(new Setting.Bool("session", true));
        private final long start = System.currentTimeMillis();

        public Clock() { super("clock", Category.HUD, false, true, 0.9f, 0.9f); }

        private String text() {
            String t = LocalTime.now().format(DateTimeFormatter.ofPattern(hour12.get() ? "hh:mm a" : "HH:mm"));
            return session.get() ? t + "  |  " + Game.formatTime((System.currentTimeMillis() - start) / 1000) : t;
        }

        @Override public int width(Gfx g) { return g.textW(text()) + 14; }
        @Override public int height(Gfx g) { return 18; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            g.text(text(), 7, 5, Theme.TEXT, true);
        }
    }

    public static final class Speed extends Module {
        public final Setting.Mode unit = add(new Setting.Mode("unit", 0, "bps", "kmh"));
        private double px, pz;
        private float speed;
        private final Tween v = new Tween(0);

        public Speed() { super("speed", Category.HUD, false, true, 0.01f, 0.95f); }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            var p = Game.player();
            double dx = p.getX() - px, dz = p.getZ() - pz;
            px = p.getX();
            pz = p.getZ();
            speed = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
        }

        private String text() {
            v.to(speed, 150, Easing.LINEAR);
            float val = v.get();
            return unit.is("kmh") ? String.format("%.1f km/h", val * 3.6f) : String.format("%.1f b/s", val);
        }

        @Override public int width(Gfx g) { return g.textW(text()) + 14; }
        @Override public int height(Gfx g) { return 18; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            g.text(text(), 7, 5, Theme.TEXT, true);
        }
    }
}
