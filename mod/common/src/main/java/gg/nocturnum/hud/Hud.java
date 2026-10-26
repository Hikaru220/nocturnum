package gg.nocturnum.hud;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.module.*;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.ui.ClickGuiView;
import gg.nocturnum.hud.util.Easing;

import java.util.HashSet;
import java.util.Set;

/** Module registry, per-tick logic and HUD rendering shared by every game version. */
public final class Hud {
    private Hud() {}

    private static final Set<Module> keyDown = new HashSet<>();
    private static boolean menuKeyDown;
    private static boolean started;

    public static boolean ready() { return started; }

    public static void registerModules() {
        Config.register(new ClickGuiModule());
        // HUD
        Config.register(new WatermarkModule());
        Config.register(new ArrayListModule());
        Config.register(new KeybindsModule());
        Config.register(new ArmorModule());
        Config.register(new PotionsModule());
        Config.register(new KeystrokesModule());
        Config.register(new SimpleHudModules.Cps());
        Config.register(new SimpleHudModules.FpsPing());
        Config.register(new SimpleHudModules.Coords());
        Config.register(new NotificationsModule());
        Config.register(new TargetHudModule());
        Config.register(new SimpleHudModules.ItemHand());
        Config.register(new SimpleHudModules.Totems());
        Config.register(new SimpleHudModules.Clock());
        Config.register(new SimpleHudModules.Speed());
        // everything else (visuals, camera, comfort, other)
        ExtraModules.registerAll();
        Config.register(new StreamerModule());
        Config.load();
        started = true;
    }

    private static int debugTicks;

    /** Developer hook: -Dnocturnum.debug=world,clickgui|editor,shot automates screenshots and tests. */
    private static void debug() {
        String d = System.getProperty("nocturnum.debug");
        if (d == null || debugTicks > 1400) return;
        debugTicks++;
        boolean world = d.contains("world");
        int open = world ? 560 : 200;
        if (world && debugTicks == 60 && !Game.inWorld()) Platform.debugCreateWorld();
        if (debugTicks == open && (!world || Game.inWorld())) {
            if (d.contains("clickgui")) Platform.openView(new ClickGuiView());
            else if (d.contains("editor")) Platform.openView(new gg.nocturnum.hud.ui.HudEditorView(null));
        }
        if (d.contains("fx") && world && Game.inWorld()) {
            if (debugTicks == open - 120) {
                for (String id : new String[]{"jumpcircles", "chinahat", "trails", "blockoverlay", "hiteffects", "crosshair", "hitcolor", "esp", "hitboxes", "minimap"}) {
                    var m = Config.byId(id);
                    if (m != null) m.setEnabled(true);
                }
                Game.mc().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                Game.player().setXRot(8);
            }
            if (debugTicks > open - 100 && debugTicks < open - 30) Game.player().setDeltaMovement(0.12, Game.player().getDeltaMovement().y, 0.04);
            if (debugTicks == open - 70 || debugTicks == open - 40) Game.player().jumpFromGround();
        }
        if (debugTicks == open + 60 && d.contains("shot")) Platform.debugShot();
        if (d.contains("loadshot") && (debugTicks == 2 || debugTicks == 12 || debugTicks == 25)) Platform.debugShot();
    }

    public static void tick() {
        if (!started) return;
        debug();
        Config.get(ClickGuiModule.class).apply();
        boolean inGame = Game.inWorld();
        for (Module m : Config.modules()) {
            if (m.enabled()) {
                try {
                    m.onTick();
                } catch (RuntimeException e) {
                    Platform.log("Module " + m.id + " tick failed: " + e);
                }
            }
        }
        if (inGame && !Platform.screenOpen()) {
            for (Module m : Config.modules()) {
                int k = m.keybind.get();
                if (k < 0 || m instanceof ClickGuiModule || m instanceof ExtraModules.Zoom) continue;
                boolean down = Platform.isKeyDown(k);
                if (m.keybind.hold) {
                    m.setEnabled(down);
                } else if (down && !keyDown.contains(m)) {
                    m.toggle();
                }
                if (down) keyDown.add(m); else keyDown.remove(m);
            }
        }
        // menu key (Right Shift by default)
        ClickGuiModule cg = Config.get(ClickGuiModule.class);
        int mk = cg.keybind.get();
        boolean down = mk >= 0 && Platform.isKeyDown(mk);
        if (down && !menuKeyDown && !Platform.screenOpen() && inGame) Platform.openView(new ClickGuiView());
        menuKeyDown = down;
        if (Platform.titleScreenOpen() && Config.get(gg.nocturnum.hud.module.MainMenuModule.class).active()) Platform.openView(new gg.nocturnum.hud.ui.MainMenuView());
        Config.tick();
        AnarchyProfiles.tick();
        Config.get(PackModules.Boost.class).watch();
    }

    /** Draws all HUD elements. Called from the platform's HUD callback. */
    public static void render(Gfx g, float partial) {
        if (!started) return;
        gg.nocturnum.hud.gfx.Projection.begin(g);
        for (Module m : Config.modules()) {
            if (!m.hud) {
                if (m.enabled()) {
                    try {
                        m.overlay(g, partial);
                    } catch (RuntimeException e) {
                        Platform.log("Module " + m.id + " overlay failed: " + e);
                    }
                }
                continue;
            }
            m.shown.to(m.enabled() ? 1 : 0, 200, Easing.OUT_CUBIC);
            float s = m.shown.get();
            if (s < 0.01f || m.empty()) continue;
            boolean pushed = false;
            try {
                float sc = m.scale.get();
                float w = m.width(g) * sc, h = m.height(g) * sc;
                float x = m.hx * g.w() - (m.anchorRight ? w : 0);
                float y = m.hy * g.h() - (m.anchorBottom ? h : 0);
                x = Math.max(0, Math.min(g.w() - w, x));
                y = Math.max(0, Math.min(g.h() - h, y));
                g.alpha = s;
                g.push(x + (1 - s) * (m.anchorRight ? 10 : -10), y, sc);
                pushed = true;
                m.render(g, partial);
            } catch (RuntimeException e) {
                Platform.log("Module " + m.id + " render failed: " + e);
            } finally {
                if (pushed) g.pop();
                g.alpha = 1f;
            }
        }
    }
}
