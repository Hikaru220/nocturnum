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

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(c -> Hud.tick());
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, Identifier.fromNamespaceAndPath("nocturnum", "hud"),
            (graphics, tracker) -> Hud.render(new Gfx(graphics), tracker.getGameTimeDeltaPartialTick(false)));
        // hide the vanilla crosshair while the custom one is enabled
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, tracker) -> {
            if (!Config.get(gg.nocturnum.hud.module.ExtraModules.Crosshair.class).enabled()) original.render(graphics, tracker);
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) Config.get(TargetHudModule.class).onAttack(entity);
            return InteractionResult.PASS;
        });
        Game.mc();
    }
}
