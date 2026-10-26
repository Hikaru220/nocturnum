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

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space post effects (colour grade, motion blur). They run as vanilla post-processing chains, so they
 * stack on top of the normal picture. With an Iris shader pack active they switch themselves off.
 */
public final class PostModules {
    private PostModules() {}

    private static final float[] SAT = {0f, 0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    private static final float[] CON = {0.7f, 0.85f, 1f, 1.15f, 1.3f};
    private static boolean warnedShader;
    private static String applied = "";
    private static volatile List<String> current = List.of();

    /** Chains that should be on right now (read by the 26.3 renderer hook). */
    public static List<String> active() { return current; }
    private static float lastYaw, lastPitch;
    private static int calm;

    public static void registerAll() {
        Config.register(new ColorGrade());
        Config.register(new MotionBlur());
    }

    private static int nearest(float[] a, float v) {
        int best = 0;
        for (int i = 1; i < a.length; i++) if (Math.abs(a[i] - v) < Math.abs(a[best] - v)) best = i;
        return best;
    }

    /** Saturation, contrast and warmth of the whole picture. */
    public static final class ColorGrade extends Module {
        public final Setting.Num saturation = add(new Setting.Num("saturation", 120f, 0f, 200f, 5f, "%"));
        public final Setting.Num contrast = add(new Setting.Num("contrast", 100f, 70f, 130f, 5f, "%"));
        public final Setting.Num warmth = add(new Setting.Num("warmth", 0f, -2f, 2f, 1f));

        public ColorGrade() {
            super("colorgrade", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        String id() {
            return "nocturnum:grade_" + nearest(SAT, saturation.get() / 100f) + "_" + nearest(CON, contrast.get() / 100f) + "_" + (Math.round(warmth.get()) + 2);
        }
    }

    /** Smooth cinematic blur; with "only when turning" it appears while the camera rotates quickly. */
    public static final class MotionBlur extends Module {
        public final Setting.Num strength = add(new Setting.Num("strength", 40f, 10f, 90f, 10f, "%"));
        public final Setting.Bool onlyTurning = add(new Setting.Bool("onlyTurning", true));

        public MotionBlur() {
            super("motionblur", Category.VISUALS, false, false, 0, 0);
            visual = true;
        }

        String id() { return "nocturnum:blur_" + Math.max(1, Math.min(9, Math.round(strength.get() / 10f))); }
    }

    /** Called every tick: decides which chains should be active and hands them to the platform layer. */
    public static void tick() {
        ColorGrade g = Config.get(ColorGrade.class);
        MotionBlur b = Config.get(MotionBlur.class);
        boolean wantAny = (g.enabled() || b.enabled()) && Game.inWorld();
        if (wantAny && Platform.shaderPackActive()) {
            if (!warnedShader) {
                warnedShader = true;
                Notifications.push(Lang.t("module.colorgrade"), Lang.t("post.shaderConflict"), Theme.WARN);
            }
            wantAny = false;
        }
        List<String> ids = new ArrayList<>();
        if (wantAny) {
            if (g.enabled()) ids.add(g.id());
            if (b.enabled()) {
                boolean show = true;
                if (b.onlyTurning.get()) {
                    var p = Game.player();
                    float dy = Math.abs(p.getYRot() - lastYaw), dp = Math.abs(p.getXRot() - lastPitch);
                    lastYaw = p.getYRot();
                    lastPitch = p.getXRot();
                    if (Math.max(dy, dp) > 2.0f) calm = 0; else calm++;
                    show = calm < 8;
                }
                if (show) ids.add(b.id());
            }
        }
        String key = String.join(",", ids);
        if (!key.equals(applied)) {
            applied = key;
            current = List.copyOf(ids);
            Platform.setPostEffects(ids);
        }
    }
}
