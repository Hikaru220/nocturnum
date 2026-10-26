package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;

/** Hides nickname, server address and coordinates in the mod's HUD (for streams and videos). */
public final class StreamerModule extends Module {
    public final Setting.Bool hideName = add(new Setting.Bool("hideName", true));
    public final Setting.Bool hideServer = add(new Setting.Bool("hideServer", true));
    public final Setting.Bool hideCoords = add(new Setting.Bool("hideCoords", true));

    public StreamerModule() {
        super("streamer", Category.OTHER, false, false, 0, 0);
    }
}
