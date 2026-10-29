package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Projection;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Features of the Anarchy category that show more than vanilla does (through walls, other players on a map...).
 * Off by default, gated per server, locked on blacklisted servers. They are not hidden from moderators.
 */
public final class AnarchyModules {
    private AnarchyModules() {}

    public static void registerAll() {
        Config.register(new TargetEsp());
        Config.register(new Hitboxes());
        Config.register(new Minimap());
    }

    private static Iterable<Entity> entities() {
        return Game.level().entitiesForRendering();
    }

    /** Projected screen rectangle of an entity box plus all 8 projected corners (null entries = behind camera). */
    private static float[][] corners(AABB b) {
        float[][] s = new float[8][];
        for (int i = 0; i < 8; i++) {
            s[i] = Projection.project((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
        }
        return s;
    }

    private static void box3d(Gfx g, float[][] s, float thick, int col) {
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) if (s[e[0]] != null && s[e[1]] != null) Draw.line(g, s[e[0]][0], s[e[0]][1], s[e[1]][0], s[e[1]][1], thick, col);
    }

    private static float[] bounds(float[][] s) {
        float x1 = 1e9f, y1 = 1e9f, x2 = -1e9f, y2 = -1e9f;
        int n = 0;
        for (float[] p : s) {
            if (p == null) continue;
            n++;
            x1 = Math.min(x1, p[0]);
            y1 = Math.min(y1, p[1]);
            x2 = Math.max(x2, p[0]);
            y2 = Math.max(y2, p[1]);
        }
        return n < 4 ? null : new float[]{x1, y1, x2, y2};
    }

    private static void frame(Gfx g, float[] b, float thick, int col) {
        int t = Math.max(1, Math.round(thick));
        int x1 = Math.round(b[0]), y1 = Math.round(b[1]), x2 = Math.round(b[2]), y2 = Math.round(b[3]);
        g.fill(x1, y1, x2, y1 + t, col);
        g.fill(x1, y2 - t, x2, y2, col);
        g.fill(x1, y1, x1 + t, y2, col);
        g.fill(x2 - t, y1, x2, y2, col);
    }

    /** A rotating ring around whoever you just hit. */
    static final class TargetEsp extends AnarchyModule {
        public final Setting.Mode marker = add(new Setting.Mode("marker", 0, "ring", "diamond", "glow"));
        public final Setting.Color color = add(new Setting.Color("color", 0xFF8B7CFF, true));
        public final Setting.Num speed = add(new Setting.Num("speed", 1f, 0.2f, 4f, 0.2f, "x"));
        public final Setting.Num hold = add(new Setting.Num("hold", 4f, 1f, 10f, 0.5f, "s"));
        private Entity target;
        private long hitAt;

        TargetEsp() {
            super("targetesp", false, 0, 0);
            visual = true;
        }

        void onAttack(Entity e) {
            if (enabled()) {
                target = e;
                hitAt = System.currentTimeMillis();
            }
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (target == null || !target.isAlive() || System.currentTimeMillis() - hitAt > hold.get() * 1000 || !Game.inWorld()) return;
            double x = target.xo + (target.getX() - target.xo) * pt, y = target.yo + (target.getY() - target.yo) * pt, z = target.zo + (target.getZ() - target.zo) * pt;
            double h = target.getBbHeight();
            double a0 = System.currentTimeMillis() / 400.0 * speed.get();
            double yy = y + h * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 600.0 * speed.get()));
            float rad = (float) Math.max(0.4, target.getBbWidth() * 0.9);
            switch (marker.current()) {
                case "diamond" -> {
                    float[] top = Projection.project(x, y + h + 0.5 + 0.1 * Math.sin(a0), z);
                    float[] bot = Projection.project(x, y + h + 0.2 + 0.1 * Math.sin(a0), z);
                    if (top != null && bot != null) {
                        int c = color.argb();
                        Draw.line(g, top[0], top[1], bot[0] - 5, (top[1] + bot[1]) / 2, 2, c);
                        Draw.line(g, top[0], top[1], bot[0] + 5, (top[1] + bot[1]) / 2, 2, c);
                        Draw.line(g, bot[0], bot[1], bot[0] - 5, (top[1] + bot[1]) / 2, 2, c);
                        Draw.line(g, bot[0], bot[1], bot[0] + 5, (top[1] + bot[1]) / 2, 2, c);
                    }
                }
                case "glow" -> {
                    float[] p = Projection.project(x, y + h * 0.5, z);
                    if (p != null) for (int i = 5; i >= 1; i--) Draw.circle(g, p[0], p[1], 6 + i * 6, Colors.withAlpha(color.argb(), 14 + (6 - i) * 4));
                }
                default -> {
                    float[] prev = null;
                    for (int i = 0; i <= 36; i++) {
                        double a = a0 + i * Math.PI * 2 / 36;
                        float[] p = Projection.project(x + Math.cos(a) * rad, yy, z + Math.sin(a) * rad);
                        if (p != null && prev != null) Draw.line(g, prev[0], prev[1], p[0], p[1], 2, color.argb(i / 36f));
                        prev = p;
                    }
                }
            }
        }
    }

    /** Entity hitboxes; colour runs from green to red with health. */
    static final class Hitboxes extends AnarchyModule {
        public final Setting.Bool onlyPlayers = add(new Setting.Bool("onlyPlayers", true));
        public final Setting.Num thickness = add(new Setting.Num("thickness", 1f, 1f, 3f, 1f, "px"));
        public final Setting.Bool lookLine = add(new Setting.Bool("lookLine", false));
        public final Setting.Num range = add(new Setting.Num("range", 48f, 8f, 128f, 8f, "m"));

        Hitboxes() {
            super("hitboxes", false, 0, 0);
            visual = true;
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (!Game.inWorld() || Platform.screenOpen()) return;
            var me = Game.player();
            double r2 = range.get() * range.get();
            for (Entity e : entities()) {
                if (e == me || !(e instanceof LivingEntity le) || e.distanceToSqr(me) > r2) continue;
                if (onlyPlayers.get() && !(e instanceof Player)) continue;
                float f = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
                int col = Colors.lerp(0xFFFF4D4D, 0xFF4DFF6A, f);
                box3d(g, corners(le.getBoundingBox()), thickness.get(), col);
                if (lookLine.get()) {
                    var eye = le.getEyePosition();
                    var look = le.getLookAngle();
                    float[] a = Projection.project(eye.x, eye.y, eye.z), b = Projection.project(eye.x + look.x * 2, eye.y + look.y * 2, eye.z + look.z * 2);
                    if (a != null && b != null) Draw.line(g, a[0], a[1], b[0], b[1], 1, 0xFF5AB0FF);
                }
            }
        }
    }

    /** Top-down map of the terrain around you with dots for players and mobs. */
    static final class Minimap extends AnarchyModule {
        public final Setting.Mode shape = add(new Setting.Mode("shape", 1, "square", "round"));
        public final Setting.Num radius = add(new Setting.Num("radius", 32f, 16f, 64f, 8f, "m"));
        public final Setting.Num size = add(new Setting.Num("size", 90f, 60f, 160f, 10f, "px"));
        public final Setting.Bool showPlayers = add(new Setting.Bool("showPlayers", true));
        public final Setting.Bool showMobs = add(new Setting.Bool("showMobs", true));
        public final Setting.Bool showAnimals = add(new Setting.Bool("showAnimals", false));
        public final Setting.Bool names = add(new Setting.Bool("names", true));
        public final Setting.Bool followLook = add(new Setting.Bool("followLook", false));

        private static final int N = 48;
        private final int[] colors = new int[N * N];
        private int lastX = Integer.MIN_VALUE, lastZ, tick;

        Minimap() {
            super("minimap", true, 0.88f, 0.12f);
            anchorRight = true;
        }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            tick++;
            BlockPos p = Game.pos();
            // refresh terrain only every few ticks or when we moved a few blocks (not every frame)
            if (tick % 10 != 0 && Math.abs(p.getX() - lastX) < 3 && Math.abs(p.getZ() - lastZ) < 3) return;
            lastX = p.getX();
            lastZ = p.getZ();
            var level = Game.level();
            float step = radius.get() * 2 / N;
            var pos = new BlockPos.MutableBlockPos();
            for (int j = 0; j < N; j++) {
                for (int i = 0; i < N; i++) {
                    int wx = Math.round(p.getX() + (i - N / 2f) * step), wz = Math.round(p.getZ() + (j - N / 2f) * step);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
                    pos.set(wx, y, wz);
                    int col = level.getBlockState(pos).getMapColor(level, pos).col;
                    // slight shading by height difference for relief
                    int shade = Math.max(-30, Math.min(30, (y - p.getY()) * 3));
                    colors[j * N + i] = 0xFF000000 | Colors.argb(255, Colors.clamp(((col >> 16) & 255) + shade), Colors.clamp(((col >> 8) & 255) + shade), Colors.clamp((col & 255) + shade));
                }
            }
        }

        @Override public int width(Gfx g) { return size.asInt(); }
        @Override public int height(Gfx g) { return size.asInt(); }

        @Override
        public void render(Gfx g, float pt) {
            int sz = size.asInt();
            boolean round = shape.is("round");
            Theme.plate(g, 0, 0, sz, sz, true);
            float cell = (sz - 4) / (float) N;
            float yaw = followLook.get() && Game.inWorld() ? (float) Math.toRadians(Game.player().getYRot() + 180) : 0f;
            float c = sz / 2f;
            g.scissor(2, 2, sz - 2, sz - 2);
            for (int j = 0; j < N; j++) {
                for (int i = 0; i < N; i++) {
                    float px = 2 + i * cell, py = 2 + j * cell;
                    if (round) {
                        float dx = px + cell / 2 - c, dy = py + cell / 2 - c;
                        if (dx * dx + dy * dy > (c - 2) * (c - 2)) continue;
                    }
                    g.fill(Math.round(px), Math.round(py), Math.round(px + cell + 0.5f), Math.round(py + cell + 0.5f), colors[j * N + i]);
                }
            }
            if (Game.inWorld()) {
                var me = Game.player();
                float scale = (sz - 4) / (radius.get() * 2);
                for (Entity e : entities()) {
                    if (e == me || !(e instanceof LivingEntity)) continue;
                    int col;
                    if (e instanceof Player) {
                        if (!showPlayers.get()) continue;
                        col = 0xFFFF5A6A;
                    } else if (e instanceof Animal) {
                        if (!showAnimals.get()) continue;
                        col = 0xFF7CFF6B;
                    } else {
                        if (!showMobs.get()) continue;
                        col = 0xFFFFD23F;
                    }
                    float dx = (float) (e.getX() - me.getX()) * scale, dz = (float) (e.getZ() - me.getZ()) * scale;
                    float rx = dx * (float) Math.cos(yaw) - dz * (float) Math.sin(yaw), rz = dx * (float) Math.sin(yaw) + dz * (float) Math.cos(yaw);
                    if (Math.abs(rx) > c - 4 || Math.abs(rz) > c - 4) continue;
                    Draw.circle(g, c + rx, c + rz, 2f, col);
                    if (names.get() && e instanceof Player) g.text(Game.streamer() ? "?" : e.getName().getString(), Math.round(c + rx + 4), Math.round(c + rz - 4), Theme.TEXT, true);
                }
                Draw.circle(g, c, c, 2.5f, 0xFFFFFFFF);
            }
            g.unscissor();
        }
    }
}
