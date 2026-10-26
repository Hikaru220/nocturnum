package gg.nocturnum.hud;

import gg.nocturnum.hud.platform.Platform;
import net.fabricmc.api.ClientModInitializer;

public final class NocturnumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Hud.registerModules();
        Platform.init();
        Platform.log("Nocturnum HUD loaded");
    }
}
