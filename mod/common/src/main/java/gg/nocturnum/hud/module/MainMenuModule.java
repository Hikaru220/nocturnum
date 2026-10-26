package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import net.fabricmc.loader.api.FabricLoader;

/** Replaces the title screen with the Nocturnum one. Steps aside when another mod (e.g. FancyMenu) owns the menu. */
public final class MainMenuModule extends Module {
    public MainMenuModule() {
        super("mainmenu", Category.OTHER, true, false, 0, 0);
    }

    public boolean active() {
        return enabled() && !FabricLoader.getInstance().isModLoaded("fancymenu") && !FabricLoader.getInstance().isModLoaded("custom_menu");
    }
}
