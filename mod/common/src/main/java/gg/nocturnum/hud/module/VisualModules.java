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
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/** World effects drawn as screen-space overlays: they only decorate your own view, nothing is sent to anyone. */
final class VisualModules {
    private VisualModules() {}

    static void attack(Entity e) {
        Config.get(HitEffects.class).onAttack(e);
        Config.get(HitColor.class).onAttack(e);
    }

    static void registerAll() {
        Config.register(new JumpCircles());
        Config.register(new Trails());
        Config.register(new ChinaHat());
        Config.register(new BlockOverlay());
        Config.register(new HitEffects());
        Config.register(new HitColor());
    }

    private static void ring(Gfx g, double x, double y, double z, double radius, int points, float thick, int color) {
        float[] prev = null;
        for (int i = 0; i <= points; i++) {
            double a = i * Math.PI * 2 / points;
            float[] p = Projection.project(x + Math.cos(a) * radius, y, z + Math.sin(a) * radius);
            if (p != null && prev != null) Draw.line(g, prev[0], prev[1], p[0], p[1], thick, color);
            prev = p;
        }
    }

    /** Expanding ring under your feet when you jump. */
    static final class JumpCircles extends Module {
        public final Setting.Mode type = add(new Setting.Mode("type", 0, "ring", "wave", "double"));
        public final Setting.Num radius = add(new Setting.Num("radius", 1.6f, 0.5f, 4f, 0.1f, "m"));
        public final Setting.Num thickness = add(new Setting.Num("thickness", 1.5f, 0.5f, 4f, 0.5f, "px"));
        public final Setting.Num life = add(new Setting.Num("life", 0.8f, 0.3f, 2f, 0.1f, "s"));
        public final Setting.Color color = add(new Setting.Color("color", 0xFF8B7CFF, false));
        public final Setting.Num maxCount = adv(new Setting.Num("maxCount", 20f, 1f, 40f, 1f));

        private record Circle(double x, double y, double z, long born) {}

        private final List<Circle> circles = new ArrayList<>();
        private boolean wasGround = true;

        JumpCircles() {
            super("jumpcircles", Category.VISUALS, false, false, 0, 0);
            visual = true;
            preset("minimal", "radius", 1.0f, "thickness", 1f);
            preset("bright", "radius", 2.4f, "thickness", 2.5f);
            preset("pvp", "radius", 1.4f, "thickness", 1.5f);
        }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            var p = Game.player();
            if (wasGround && !p.onGround() && p.getDeltaMovement().y > 0.2) {
                circles.add(new Circle(p.getX(), p.getY() + 0.02, p.getZ(), System.currentTimeMillis()));
                while (circles.size() > maxCount.asInt()) circles.remove(0);
            }
            wasGround = p.onGround();
            long now = System.currentTimeMillis();
            circles.removeIf(c -> now - c.born > life.get() * 1000);
        }

        @Override
        public void overlay(Gfx g, float pt) {
            long now = System.currentTimeMillis();
            int n = 0;
            for (Circle c : circles) {
                float t = Math.min(1f, (now - c.born) / (life.get() * 1000f));
                float e = Easing.OUT_CUBIC.apply(t);
                int col = Colors.mulAlpha(color.argb(n * 0.07f), 1f - t);
                ring(g, c.x, c.y, c.z, radius.get() * e, 40, thickness.get(), col);
                if (!type.is("ring")) ring(g, c.x, c.y, c.z, radius.get() * e * 0.6f, 32, thickness.get(), Colors.mulAlpha(col, 0.7f));
                n++;
            }
        }
    }

    /** Coloured ribbon behind your character. */
    static final class Trails extends Module {
        public final Setting.Num length = add(new Setting.Num("length", 30f, 5f, 80f, 1f));
        public final Setting.Num width = add(new Setting.Num("width", 2f, 1f, 6f, 0.5f, "px"));
        public final Setting.Color color = add(new Setting.Color("color", 0xFF8B7CFF, true));
        public final Setting.Bool onlySprint = add(new Setting.Bool("onlySprint", false));
        public final Setting.Bool gradient = add(new Setting.Bool("gradient", true));

        private final List<double[]> pts = new ArrayList<>();

        Trails() {
            super("trails", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            var p = Game.player();
            boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.0004;
            if (moving && (!onlySprint.get() || p.isSprinting())) pts.add(new double[]{p.getX(), p.getY() + 0.1, p.getZ()});
            else if (!pts.isEmpty()) pts.remove(0);
            while (pts.size() > length.asInt()) pts.remove(0);
        }

        @Override
        public void overlay(Gfx g, float pt) {
            float[] prev = null;
            int n = pts.size();
            for (int i = 0; i < n; i++) {
                double[] q = pts.get(i);
                float[] p = Projection.project(q[0], q[1], q[2]);
                float k = (i + 1) / (float) n;
                if (p != null && prev != null) {
                    int c = color.argb(gradient.get() ? 1f - k : 0f);
                    Draw.line(g, prev[0], prev[1], p[0], p[1], Math.max(0.6f, width.get() * k), Colors.mulAlpha(c, k));
                }
                prev = p;
            }
        }
    }

    /** Cone, halo or pyramid above your head. */
    static final class ChinaHat extends Module {
        public final Setting.Mode shape = add(new Setting.Mode("shape", 0, "cone", "halo", "pyramid"));
        public final Setting.Color color = add(new Setting.Color("color", 0xFFFF8CC6, false));
        public final Setting.Bool rotate = add(new Setting.Bool("rotate", true));
        public final Setting.Bool firstPerson = add(new Setting.Bool("firstPerson", false));

        ChinaHat() {
            super("chinahat", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (!Game.inWorld()) return;
            if (!Projection.thirdPerson && !firstPerson.get()) return;
            var p = Game.player();
            double x = p.xo + (p.getX() - p.xo) * pt, y = p.yo + (p.getY() - p.yo) * pt, z = p.zo + (p.getZ() - p.zo) * pt;
            double top = y + p.getBbHeight() + 0.05;
            double spin = rotate.get() ? System.currentTimeMillis() / 1500.0 : 0;
            int c = color.argb();
            switch (shape.current()) {
                case "halo" -> ring(g, x, top + 0.15, z, 0.3, 28, 1.5f, c);
                case "pyramid" -> {
                    for (int i = 0; i < 4; i++) {
                        double a = spin + i * Math.PI / 2, b = a + Math.PI / 2;
                        float[] p1 = Projection.project(x + Math.cos(a) * 0.5, top, z + Math.sin(a) * 0.5);
                        float[] p2 = Projection.project(x + Math.cos(b) * 0.5, top, z + Math.sin(b) * 0.5);
                        float[] apex = Projection.project(x, top + 0.4, z);
                        if (p1 != null && p2 != null && apex != null) {
                            Draw.line(g, p1[0], p1[1], p2[0], p2[1], 1.5f, c);
                            Draw.line(g, p1[0], p1[1], apex[0], apex[1], 1.5f, c);
                        }
                    }
                }
                default -> {
                    ring(g, x, top, z, 0.55, 36, 1.5f, c);
                    float[] apex = Projection.project(x, top + 0.38, z);
                    for (int i = 0; i < 8 && apex != null; i++) {
                        double a = spin + i * Math.PI / 4;
                        float[] edge = Projection.project(x + Math.cos(a) * 0.55, top, z + Math.sin(a) * 0.55);
                        if (edge != null) Draw.line(g, edge[0], edge[1], apex[0], apex[1], 1f, Colors.mulAlpha(c, 0.8f));
                    }
                }
            }
        }
    }

    /** Coloured outline (optionally pulsing) around the block you look at. */
    static final class BlockOverlay extends Module {
        public final Setting.Color color = add(new Setting.Color("color", 0xFF8B7CFF, false));
        public final Setting.Num thickness = add(new Setting.Num("thickness", 1.5f, 0.5f, 4f, 0.5f, "px"));
        public final Setting.Bool pulse = add(new Setting.Bool("pulse", true));

        BlockOverlay() {
            super("blockoverlay", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (!Game.inWorld() || Platform.screenOpen()) return;
            HitResult hr = Game.mc().hitResult;
            if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) return;
            BlockPos bp = bhr.getBlockPos();
            double e = 0.002;
            double[][] c = new double[8][];
            for (int i = 0; i < 8; i++) c[i] = new double[]{bp.getX() + ((i & 1) == 0 ? -e : 1 + e), bp.getY() + ((i & 2) == 0 ? -e : 1 + e), bp.getZ() + ((i & 4) == 0 ? -e : 1 + e)};
            float[][] s = new float[8][];
            for (int i = 0; i < 8; i++) s[i] = Projection.project(c[i][0], c[i][1], c[i][2]);
            float a = pulse.get() ? 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 250.0) : 1f;
            int col = Colors.mulAlpha(color.argb(), a);
            int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] ed : edges) {
                if (s[ed[0]] != null && s[ed[1]] != null) Draw.line(g, s[ed[0]][0], s[ed[0]][1], s[ed[1]][0], s[ed[1]][1], thickness.get(), col);
            }
        }
    }

    /** Extra particles at the hit point and a ring around the target. Particles are client-side only. */
    static final class HitEffects extends Module {
        public final Setting.Mode shape = add(new Setting.Mode("shape", 0, "sparks", "hearts", "stars", "dust"));
        public final Setting.Num count = add(new Setting.Num("count", 8f, 1f, 30f, 1f));
        public final Setting.Color color = add(new Setting.Color("color", 0xFF8B7CFF, false));
        public final Setting.Bool wave = add(new Setting.Bool("wave", true));

        private final List<double[]> waves = new ArrayList<>();

        HitEffects() {
            super("hiteffects", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        void onAttack(Entity e) {
            if (!Game.inWorld() || !enabled()) return;
            Platform.spawnHitParticles(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), shape.current(), count.asInt(), color.argb());
            if (wave.get()) waves.add(new double[]{e.getX(), e.getY() + 0.05, e.getZ(), System.currentTimeMillis()});
        }

        @Override
        public void overlay(Gfx g, float pt) {
            long now = System.currentTimeMillis();
            waves.removeIf(w -> now - w[3] > 600);
            for (double[] w : waves) {
                float t = (now - (long) w[3]) / 600f;
                ring(g, w[0], w[1], w[2], 0.4 + 1.6 * Easing.OUT_CUBIC.apply(t), 32, 1.5f, Colors.mulAlpha(color.argb(), 1f - t));
            }
        }
    }

    /** Brief colour flash of the screen when you land a hit. */
    static final class HitColor extends Module {
        public final Setting.Color color = add(new Setting.Color("color", 0x66FF4466, false));
        private long at;

        HitColor() {
            super("hitcolor", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        void onAttack(Entity e) { at = System.currentTimeMillis(); }

        @Override
        public void overlay(Gfx g, float pt) {
            float t = (System.currentTimeMillis() - at) / 220f;
            if (t >= 1f) return;
            g.alpha = 1f - t;
            g.fill(0, 0, g.w(), g.h(), color.argb());
            g.alpha = 1f;
            Theme.accent();
        }
    }
}
