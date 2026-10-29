package gg.nocturnum.hud.platform;

import com.mojang.blaze3d.platform.InputConstants;
import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.Hud;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.module.TargetHudModule;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.ui.View;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Everything that touches Minecraft / Fabric APIs which differ between game versions. */
public final class Platform {
    private Platform() {}

    private static final Logger LOG = LoggerFactory.getLogger("Nocturnum");

    public static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    public static void log(String s) { LOG.info(s); }

    public static String languageCode() { return Minecraft.getInstance().options.languageCode; }

    public static boolean isKeyDown(int key) {
        return key >= 0 && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public static String keyName(int key) { return gg.nocturnum.hud.util.KeyNames.of(key); }

    public static void openView(View v) { Minecraft.getInstance().setScreen(new ViewScreen(v)); }

    public static void chat(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal(s), false);
    }

    public static boolean screenOpen() { return Minecraft.getInstance().screen != null; }

    public static void closeScreen() { Minecraft.getInstance().setScreen(null); }

    public static void setClipboard(String s) { Minecraft.getInstance().keyboardHandler.setClipboard(s); }

    public static String getClipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }

    public static boolean viewOpen() { return Minecraft.getInstance().screen instanceof ViewScreen; }

    /** Developer helper: saves a screenshot of the current frame into the game folder. */
    public static void debugShot() {
        var mc = Minecraft.getInstance();
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), c -> {});
    }

    /** Developer helper: creates and opens a fresh creative test world (used by automated screenshots). */
    public static void debugCreateWorld() {
        var mc = Minecraft.getInstance();
        var rules = new net.minecraft.world.level.gamerules.GameRules(net.minecraft.world.level.WorldDataConfiguration.DEFAULT.enabledFeatures());
        var settings = new net.minecraft.world.level.LevelSettings("nct_test", net.minecraft.world.level.GameType.CREATIVE, false,
            net.minecraft.world.Difficulty.PEACEFUL, true, rules, net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("nct_test", settings, net.minecraft.world.level.levelgen.WorldOptions.defaultWithRandomSeed(),
            net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.screen);
    }

    public static boolean windowActive() { return Minecraft.getInstance().isWindowActive(); }

    public static int fpsLimit() { return Minecraft.getInstance().options.framerateLimit().get(); }

    public static void setFpsLimit(int v) { Minecraft.getInstance().options.framerateLimit().set(v); }

    public static double gamma() { return Minecraft.getInstance().options.gamma().get(); }

    public static void setGamma(double v) { Minecraft.getInstance().options.gamma().set(Math.max(0, Math.min(1, v))); }

    /** Sends text as the player: commands start with a slash, everything else is a chat message. */
    public static void sendChat(String text) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        if (text.startsWith("/")) conn.sendCommand(text.substring(1)); else conn.sendChat(text);
    }

    /** Local night-vision effect without an icon (client side only). */
    public static void nightVision(boolean on) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        var nv = net.minecraft.world.effect.MobEffects.NIGHT_VISION;
        if (on) {
            var cur = p.getEffect(nv);
            if (cur == null || cur.getDuration() < 220) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(nv, 400, 0, false, false, false));
        } else if (p.hasEffect(nv)) {
            p.removeEffect(nv);
        }
    }

    private static int[] boostSaved;

    /** Boost mode: cut particles, shadows, clouds and distance; restore the previous values on exit. */
    public static void boost(boolean on) {
        var o = Minecraft.getInstance().options;
        if (on) {
            boostSaved = new int[]{o.particles().get().ordinal(), o.cloudStatus().get().ordinal(), o.entityShadows().get() ? 1 : 0, o.renderDistance().get()};
            o.particles().set(net.minecraft.server.level.ParticleStatus.MINIMAL);
            o.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            o.entityShadows().set(false);
            o.renderDistance().set(Math.min(o.renderDistance().get(), 8));
        } else if (boostSaved != null) {
            o.particles().set(net.minecraft.server.level.ParticleStatus.values()[boostSaved[0]]);
            o.cloudStatus().set(net.minecraft.client.CloudStatus.values()[boostSaved[1]]);
            o.entityShadows().set(boostSaved[2] == 1);
            o.renderDistance().set(boostSaved[3]);
            boostSaved = null;
        }
    }

    public static void openSingleplayer() {
        var mc = Minecraft.getInstance();
        mc.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(mc.screen));
    }

    public static void openMultiplayer() {
        var mc = Minecraft.getInstance();
        mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(mc.screen));
    }

    public static void openOptions() {
        var mc = Minecraft.getInstance();
        mc.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(mc.screen, mc.options));
    }

    public static void quit() { Minecraft.getInstance().stop(); }

    public static String gameVersion() { return net.minecraft.SharedConstants.getCurrentVersion().name(); }

    /** Last servers from the multiplayer list as {name, address}. */
    public static java.util.List<String[]> recentServers(int n) {
        var out = new java.util.ArrayList<String[]>();
        try {
            var list = new net.minecraft.client.multiplayer.ServerList(Minecraft.getInstance());
            list.load();
            for (int i = 0; i < Math.min(n, list.size()); i++) out.add(new String[]{list.get(i).name, list.get(i).ip});
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    public static void joinServer(String name, String ip) {
        var mc = Minecraft.getInstance();
        var data = new net.minecraft.client.multiplayer.ServerData(name, ip, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(ip), data, false, null);
    }

    public static boolean titleScreenOpen() { return Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.TitleScreen; }

    /** {x, y, z, qx, qy, qz, qw, fovDegrees, thirdPerson} of the render camera, or null. */
    public static double[] cameraInfo() {
        var mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera();
        if (cam == null || mc.level == null) return null;
        var p = cam.position();
        var q = cam.rotation();
        return new double[]{p.x, p.y, p.z, q.x, q.y, q.z, q.w, mc.options.fov().get(), cam.isDetached() ? 1 : 0};
    }

    public static void spawnHitParticles(double x, double y, double z, String shape, int count, int argb) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        net.minecraft.core.particles.ParticleOptions type = switch (shape) {
            case "hearts" -> net.minecraft.core.particles.ParticleTypes.HEART;
            case "stars" -> net.minecraft.core.particles.ParticleTypes.END_ROD;
            case "dust" -> new net.minecraft.core.particles.DustParticleOptions(argb & 0xFFFFFF, 1.2f);
            default -> net.minecraft.core.particles.ParticleTypes.CRIT;
        };
        for (int i = 0; i < count; i++) {
            level.addParticle(type, x, y, z, (rnd.nextDouble() - 0.5) * 0.6, rnd.nextDouble() * 0.4, (rnd.nextDouble() - 0.5) * 0.6);
        }
    }

    /** Plays a UI sound at the given pitch and volume. */
    public static void playUi(String soundId, float pitch, float volume) {
        try {
            var ev = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.parse(soundId));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, pitch, volume));
        } catch (RuntimeException ignored) {
        }
    }

    public static float optionFov() { return Minecraft.getInstance().options.fov().get(); }

    /** Switches to third person (when asked) and returns the previous camera type ordinal. */
    public static int cameraType(boolean thirdBack) {
        var o = Minecraft.getInstance().options;
        int prev = o.getCameraType().ordinal();
        if (thirdBack) o.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        return prev;
    }

    public static void restoreCameraType(int ordinal) {
        Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.values()[ordinal]);
    }

    /** True while an Iris shader pack is rendering (our post effects would fight with it). */
    public static boolean shaderPackActive() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(inst);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Applies our post-processing chain(s). This version has one slot: motion blur wins over the colour grade. */
    public static void setPostEffects(java.util.List<String> ids) {
        var gr = Minecraft.getInstance().gameRenderer;
        var acc = (gg.nocturnum.hud.mixin.GameRendererPostAccess) gr;
        var current = acc.nocturnum$postEffectId();
        if (current != null && !current.getNamespace().equals("nocturnum")) return; // vanilla spectator effect owns the slot
        if (ids.isEmpty()) {
            if (current != null) gr.clearPostEffect();
            return;
        }
        var want = net.minecraft.resources.Identifier.parse(ids.get(ids.size() - 1));
        if (!want.equals(current)) acc.nocturnum$setPostEffect(want);
    }

    /** The view shown by the current screen, or null (developer tests). */
    public static gg.nocturnum.hud.ui.View currentView() {
        return Minecraft.getInstance().screen instanceof ViewScreen vs ? vs.view() : null;
    }

    /** Developer helper: clicks at GUI coordinates through the real MouseHandler path. */
    public static void simulateClick(double gx, double gy) {
        var mc = Minecraft.getInstance();
        var w = mc.getWindow();
        var mh = (gg.nocturnum.hud.mixin.MouseAccess) mc.mouseHandler;
        mh.nocturnum$xpos(gx * w.getScreenWidth() / (double) w.getGuiScaledWidth());
        mh.nocturnum$ypos(gy * w.getScreenHeight() / (double) w.getGuiScaledHeight());
        mh.nocturnum$button(w.handle(), new net.minecraft.client.input.MouseButtonInfo(0, 0), 1);
        mh.nocturnum$button(w.handle(), new net.minecraft.client.input.MouseButtonInfo(0, 0), 0);
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> Hud.tick());
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, Identifier.fromNamespaceAndPath("nocturnum", "hud"),
            (graphics, tracker) -> Hud.render(new Gfx(graphics), tracker.getGameTimeDeltaPartialTick(false)));
        // hide the vanilla crosshair while the custom one is enabled
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, tracker) -> {
            if (!Config.get(gg.nocturnum.hud.module.ExtraModules.Crosshair.class).enabled()) original.render(graphics, tracker);
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                var b = net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Nocturnum"),
                    x -> openView(new gg.nocturnum.hud.ui.ClickGuiView())).bounds(6, 6, 90, 20).build();
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(b);
            }
        });
        // leave protection: wrap the "disconnect" button of the pause menu
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof net.minecraft.client.gui.screens.PauseScreen ps) {
                var d = ((gg.nocturnum.hud.mixin.PauseScreenAccess) ps).nocturnum$disconnectButton();
                if (d == null) return;
                var acc = (gg.nocturnum.hud.mixin.ButtonAccess) d;
                var orig = acc.nocturnum$getOnPress();
                acc.nocturnum$setOnPress(b -> {
                    if (gg.nocturnum.hud.module.ComfortModules.LeaveProtection.shouldAsk()) {
                        openView(new gg.nocturnum.hud.ui.ConfirmView(gg.nocturnum.hud.util.Lang.t("leave.title"), gg.nocturnum.hud.util.Lang.t("leave.text"),
                            gg.nocturnum.hud.util.Lang.t("leave.yes"), gg.nocturnum.hud.util.Lang.t("leave.stay"), () -> orig.onPress(b), null));
                    } else {
                        orig.onPress(b);
                    }
                });
            }
        });
        // scoreboard and tab list editors
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, tracker) ->
            gg.nocturnum.hud.module.ComfortModules.ScoreboardEditor.wrap(new Gfx(graphics), () -> original.render(graphics, tracker)));
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, original -> (graphics, tracker) ->
            gg.nocturnum.hud.module.ComfortModules.TabEditor.wrap(new Gfx(graphics), () -> original.render(graphics, tracker)));
        // timestamps and click-to-copy for system / server messages
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) ->
            overlay ? msg : gg.nocturnum.hud.module.ComfortModules.EnhancedChat.decorate(msg));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) gg.nocturnum.hud.module.PackModules.onAttack(entity);
            return InteractionResult.PASS;
        });
        Game.mc();
    }
}
