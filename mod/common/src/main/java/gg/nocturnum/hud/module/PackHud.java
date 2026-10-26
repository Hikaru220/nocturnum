package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** HUD elements and utilities of package 2. All of them only display what the player already knows. */
public final class PackHud {
    private PackHud() {}

    /** Items to count in the inventory; each can be switched on or off. */
    public static final class ItemCounter extends Module {
        private record Entry(String id, Item item, Setting.Bool on) {}

        private final List<Entry> entries = new ArrayList<>();
        public final Setting.Num warnBelow = add(new Setting.Num("warnBelow", 2f, 0f, 16f, 1f));

        public ItemCounter() {
            super("itemcounter", Category.HUD, false, true, 0.9f, 0.4f);
            add("pearl", Items.ENDER_PEARL, true);
            add("gapple", Items.GOLDEN_APPLE, true);
            add("totem", Items.TOTEM_OF_UNDYING, true);
            add("xp", Items.EXPERIENCE_BOTTLE, false);
            add("arrow", Items.ARROW, false);
            add("firework", Items.FIREWORK_ROCKET, false);
            add("splash", Items.SPLASH_POTION, false);
            add("potion", Items.POTION, false);
        }

        private void add(String id, Item item, boolean def) {
            entries.add(new Entry(id, item, add(new Setting.Bool(id, def))));
        }

        private List<Entry> active() {
            List<Entry> l = new ArrayList<>();
            for (Entry e : entries) if (e.on.get()) l.add(e);
            return l;
        }

        @Override public int width(Gfx g) { return 52; }
        @Override public int height(Gfx g) { return Math.max(1, active().size()) * 20 + 4; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            int y = 3;
            for (Entry e : active()) {
                int n = Game.count(e.item);
                g.item(e.item.getDefaultInstance(), 4, y);
                boolean low = n <= warnBelow.get() && warnBelow.get() > 0;
                g.text(String.valueOf(n), 26, y + 5, low ? Theme.BAD : Theme.TEXT, true);
                y += 20;
            }
        }
    }

    /** Hunger and saturation, plus what the food in your hand would restore. */
    public static final class Saturation extends Module {
        public Saturation() {
            super("saturation", Category.HUD, false, true, 0.4f, 0.9f);
        }

        private String[] lines() {
            if (!Game.inWorld()) return new String[]{"—"};
            var fd = Game.player().getFoodData();
            String a = Lang.t("hud.food") + ": " + fd.getFoodLevel() + "/20";
            String b = Lang.t("hud.saturation") + ": " + String.format("%.1f", fd.getSaturationLevel());
            ItemStack hand = Game.player().getMainHandItem();
            var food = hand.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null) b += String.format("   (+%d / +%.1f)", food.nutrition(), food.nutrition() * food.saturation() * 2f);
            return new String[]{a, b};
        }

        @Override
        public int width(Gfx g) {
            int w = 60;
            for (String s : lines()) w = Math.max(w, g.textW(s) + 14);
            return w;
        }

        @Override public int height(Gfx g) { return 2 * (g.fontH() + 3) + 8; }

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

    /** Hits in a row without being hit back. Resets after N seconds; flashes at 5+. */
    public static final class Combo extends Module {
        public final Setting.Num resetAfter = add(new Setting.Num("resetAfter", 3f, 1f, 10f, 0.5f, "s"));
        private int combo;
        private long lastHit;
        private int lastHurtTime;
        private final Tween pop = new Tween(0);

        public Combo() {
            super("combo", Category.HUD, false, true, 0.5f, 0.35f);
        }

        public void onAttack(Entity e) {
            combo++;
            lastHit = System.currentTimeMillis();
            pop.set(1);
            pop.to(0, 300, Easing.OUT_CUBIC);
        }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            int ht = Game.player().hurtTime;
            if (ht > 0 && lastHurtTime == 0) combo = 0; // you were hit: combo broken
            lastHurtTime = ht;
            if (combo > 0 && System.currentTimeMillis() - lastHit > resetAfter.get() * 1000) combo = 0;
        }

        @Override public boolean empty() { return combo == 0; }
        @Override public int width(Gfx g) { return 64; }
        @Override public int height(Gfx g) { return 26; }

        @Override
        public void render(Gfx g, float pt) {
            int shown = combo == 0 ? 5 : combo;
            boolean hot = shown >= 5;
            Theme.plate(g, 0, 0, width(g), height(g), hot);
            String t = shown + " " + Lang.t("hud.combo");
            int col = hot ? Colors.lerp(Theme.accent(), 0xFFFFFFFF, pop.get()) : Theme.TEXT;
            Draw.centeredBold(g, t, width(g) / 2f, 8, col, true);
        }
    }

    /** Time left until pearls, shields, chorus fruit and similar can be used again (vanilla cooldowns). */
    public static final class Cooldowns extends Module {
        private final List<Item> tracked = List.of(Items.ENDER_PEARL, Items.SHIELD, Items.CHORUS_FRUIT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.TRIDENT, Items.WIND_CHARGE);

        public Cooldowns() {
            super("cooldowns", Category.COMFORT, true, true, 0.5f, 0.6f);
        }

        private List<Item> active() {
            List<Item> l = new ArrayList<>();
            if (!Game.inWorld()) return l;
            for (Item i : tracked) {
                if (Game.player().getCooldowns().isOnCooldown(i.getDefaultInstance())) l.add(i);
            }
            return l;
        }

        @Override public boolean empty() { return active().isEmpty(); }
        @Override public int width(Gfx g) { return Math.max(1, active().size()) * 24 + 4; }
        @Override public int height(Gfx g) { return 26; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            int x = 4;
            List<Item> l = active();
            if (l.isEmpty()) {
                g.item(Items.ENDER_PEARL.getDefaultInstance(), 4, 5);
                return;
            }
            for (Item i : l) {
                ItemStack st = i.getDefaultInstance();
                float f = Game.player().getCooldowns().getCooldownPercent(st, pt);
                g.item(st, x, 5);
                Draw.rect(g, x, 22, 16, 2, 1, Colors.withAlpha(0xFFFFFF, 40));
                Draw.rect(g, x, 22, Math.max(1, 16 * (1 - f)), 2, 1, Theme.accent());
                x += 24;
            }
        }
    }

    /** How far a bow or crossbow is drawn. */
    public static final class BowPower extends Module {
        public BowPower() {
            super("bowpower", Category.COMFORT, false, true, 0.5f, 0.5f);
        }

        private float power() {
            if (!Game.inWorld()) return 0;
            var p = Game.player();
            if (!p.isUsingItem()) return 0;
            ItemStack use = p.getUseItem();
            if (use.is(Items.BOW)) return net.minecraft.world.item.BowItem.getPowerForTime(p.getTicksUsingItem());
            if (use.is(Items.CROSSBOW)) return Math.min(1f, p.getTicksUsingItem() / 25f);
            return 0;
        }

        @Override public boolean empty() { return power() <= 0; }
        @Override public int width(Gfx g) { return 60; }
        @Override public int height(Gfx g) { return 12; }

        @Override
        public void render(Gfx g, float pt) {
            float f = power();
            if (f <= 0) f = 0.6f; // editor preview
            Theme.plate(g, 0, 0, width(g), height(g), f >= 1f);
            Draw.rect(g, 4, 4, width(g) - 8, 4, 2, Colors.withAlpha(0xFFFFFF, 30));
            Draw.rectGradientH(g, 4, 4, Math.max(3, (width(g) - 8) * f), 4, 2, Theme.accent(), f >= 1 ? 0xFFFFFFFF : Theme.accent2());
        }
    }

    /** Memory used by the game, as a number or a bar; turns red above 90%. */
    public static final class Memory extends Module {
        public final Setting.Mode view = add(new Setting.Mode("view", 0, "number", "bar"));

        public Memory() {
            super("memory", Category.COMFORT, false, true, 0.9f, 0.8f);
        }

        private static float used() {
            Runtime r = Runtime.getRuntime();
            return (r.totalMemory() - r.freeMemory()) / (float) r.maxMemory();
        }

        private String text() {
            Runtime r = Runtime.getRuntime();
            return String.format("RAM %d / %d MB", (r.totalMemory() - r.freeMemory()) >> 20, r.maxMemory() >> 20);
        }

        @Override public int width(Gfx g) { return view.is("bar") ? 80 : g.textW(text()) + 14; }
        @Override public int height(Gfx g) { return view.is("bar") ? 22 : 18; }

        @Override
        public void render(Gfx g, float pt) {
            float u = used();
            int col = u > 0.9f ? Theme.BAD : u > 0.75f ? Theme.WARN : Theme.OK;
            Theme.plate(g, 0, 0, width(g), height(g));
            if (view.is("bar")) {
                g.text("RAM " + Math.round(u * 100) + "%", 6, 3, Theme.TEXT, true);
                Draw.rect(g, 6, 14, width(g) - 12, 3, 1.5f, Colors.withAlpha(0xFFFFFF, 30));
                Draw.rect(g, 6, 14, Math.max(2, (width(g) - 12) * u), 3, 1.5f, col);
            } else {
                g.text(text(), 7, 5, col, true);
            }
        }
    }

    /** Distance of the hit you just landed. Off by default: banned on some servers. */
    public static final class Reach extends Module {
        private double dist = -1;
        private long at;

        public Reach() {
            super("reach", Category.COMFORT, false, true, 0.5f, 0.45f);
        }

        public void onAttack(Entity e) {
            if (!Game.inWorld()) return;
            var p = Game.player();
            var eye = p.getEyePosition();
            var box = e.getBoundingBox();
            double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
            double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
            double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
            dist = eye.distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
            at = System.currentTimeMillis();
        }

        @Override
        public void onEnable() {
            gg.nocturnum.hud.ui.Notifications.push(Lang.t("module.reach"), Lang.t("warn.serverRules"), Theme.WARN);
        }

        @Override public boolean empty() { return dist < 0 || System.currentTimeMillis() - at > 3000; }
        @Override public int width(Gfx g) { return 56; }
        @Override public int height(Gfx g) { return 18; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            g.text(String.format("%.2f %s", dist < 0 ? 3.0 : dist, Lang.t("hud.blocks")), 7, 5, Theme.TEXT, true);
        }
    }

    /** Tape or circle showing where you look: N / E / S / W and degrees. */
    public static final class Compass extends Module {
        public Compass() {
            super("compass", Category.COMFORT, false, true, 0.38f, 0.01f);
        }

        @Override public int width(Gfx g) { return 150; }
        @Override public int height(Gfx g) { return 20; }

        @Override
        public void render(Gfx g, float pt) {
            float yaw = Game.inWorld() ? Game.player().getYRot() : 0;
            Theme.plate(g, 0, 0, width(g), height(g));
            g.scissor(3, 0, width(g) - 3, height(g));
            String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            float pxPerDeg = 1.1f;
            float cx = width(g) / 2f;
            for (int i = -2; i <= 10; i++) {
                float deg = i * 45f;
                float rel = ((deg - ((yaw % 360) + 360) % 360) + 540) % 360 - 180;
                float x = cx + rel * pxPerDeg;
                String n = names[((i % 8) + 8) % 8];
                boolean cardinal = n.length() == 1;
                g.text(n, Math.round(x - g.textW(n) / 2f), 5, cardinal ? (n.equals("N") ? Theme.BAD : Theme.TEXT) : Theme.TEXT_DIM, true);
            }
            g.unscissor();
            g.fill(Math.round(cx), 1, Math.round(cx) + 1, 4, Theme.accent());
            Draw.centeredText(g, Math.round(((yaw % 360) + 540) % 360) + "°", cx, height(g) - 1, Theme.TEXT_FAINT, false);
        }
    }

    /** Session / today / total play time (total is kept in config/nocturnum/playtime.txt). */
    public static final class PlayTime extends Module {
        private final long start = System.currentTimeMillis();
        private long total;
        private long lastSave;

        public PlayTime() {
            super("playtime", Category.COMFORT, false, true, 0.9f, 0.85f);
            try {
                var f = gg.nocturnum.hud.config.Config.dir().resolve("playtime.txt");
                if (java.nio.file.Files.exists(f)) total = Long.parseLong(java.nio.file.Files.readString(f).trim());
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onTick() {
            long now = System.currentTimeMillis();
            if (now - lastSave > 30000) {
                lastSave = now;
                total += 30;
                try {
                    java.nio.file.Files.writeString(gg.nocturnum.hud.config.Config.dir().resolve("playtime.txt"), String.valueOf(total));
                } catch (Exception ignored) {
                }
            }
        }

        private String text() {
            return Lang.t("hud.session") + " " + Game.formatTime((System.currentTimeMillis() - start) / 1000) + "  |  " + Lang.t("hud.total") + " " + total / 3600 + "h";
        }

        @Override public int width(Gfx g) { return g.textW(text()) + 14; }
        @Override public int height(Gfx g) { return 18; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            g.text(text(), 7, 5, Theme.TEXT, true);
        }
    }

    /** Stopwatch and countdown timer controlled by keys. */
    public static final class Stopwatch extends Module {
        public final Setting.Key startStop = add(new Setting.Key("startStop"));
        public final Setting.Key reset = add(new Setting.Key("reset"));
        public final Setting.Num timer = add(new Setting.Num("timer", 0f, 0f, 3600f, 5f, "s"));
        private long accumulated, startedAt;
        private boolean running, lastA, lastB, alarmed;

        public Stopwatch() {
            super("stopwatch", Category.COMFORT, false, true, 0.9f, 0.75f);
        }

        private long elapsed() { return accumulated + (running ? System.currentTimeMillis() - startedAt : 0); }

        @Override
        public void onTick() {
            if (Platform.screenOpen()) return;
            boolean a = startStop.get() >= 0 && Platform.isKeyDown(startStop.get());
            boolean b = reset.get() >= 0 && Platform.isKeyDown(reset.get());
            if (a && !lastA) {
                if (running) accumulated = elapsed();
                else startedAt = System.currentTimeMillis();
                running = !running;
            }
            if (b && !lastB) {
                running = false;
                accumulated = 0;
                alarmed = false;
            }
            lastA = a;
            lastB = b;
            if (timer.get() > 0 && running && !alarmed && elapsed() >= timer.get() * 1000) {
                alarmed = true;
                gg.nocturnum.hud.ui.Notifications.push(Lang.t("module.stopwatch"), Lang.t("hud.timerDone"), Theme.OK);
            }
        }

        private String text() {
            long ms = timer.get() > 0 ? Math.max(0, (long) (timer.get() * 1000) - elapsed()) : elapsed();
            return String.format("%02d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10);
        }

        @Override public int width(Gfx g) { return 62; }
        @Override public int height(Gfx g) { return 18; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g), running);
            g.text(text(), 7, 5, running ? Theme.accent() : Theme.TEXT, true);
        }
    }

    /** FPS, frame time, memory and a plain-words hint about what is slowing the game. */
    public static final class PerfMonitor extends Module {
        public final Setting.Mode view = add(new Setting.Mode("view", 0, "compact", "detailed"));
        private final Tween fps = new Tween(0);

        public PerfMonitor() {
            super("perfmonitor", Category.HUD, false, true, 0.01f, 0.75f);
        }

        private String hint() {
            int f = Game.fps();
            Runtime r = Runtime.getRuntime();
            float mem = (r.totalMemory() - r.freeMemory()) / (float) r.maxMemory();
            if (mem > 0.9f) return Lang.t("hint.memory");
            if (f > 0 && f < 30) return Lang.t("hint.lowFps");
            return Lang.t("hint.ok");
        }

        private String[] lines() {
            fps.to(Game.fps(), 150, Easing.LINEAR);
            int f = Math.round(fps.get());
            String a = f + " FPS  |  " + (f > 0 ? String.format("%.1f", 1000f / f) : "—") + " ms";
            if (view.is("compact")) return new String[]{a};
            Runtime r = Runtime.getRuntime();
            return new String[]{a, String.format("RAM %d / %d MB", (r.totalMemory() - r.freeMemory()) >> 20, r.maxMemory() >> 20), hint()};
        }

        @Override
        public int width(Gfx g) {
            int w = 60;
            for (String s : lines()) w = Math.max(w, g.textW(s) + 14);
            return w;
        }

        @Override public int height(Gfx g) { return lines().length * (g.fontH() + 3) + 9; }

        @Override
        public void render(Gfx g, float pt) {
            Theme.plate(g, 0, 0, width(g), height(g));
            int y = 5;
            String[] l = lines();
            for (int i = 0; i < l.length; i++) {
                g.text(l[i], 7, y, i == 2 ? Theme.WARN : Theme.TEXT, true);
                y += g.fontH() + 3;
            }
        }
    }
}
