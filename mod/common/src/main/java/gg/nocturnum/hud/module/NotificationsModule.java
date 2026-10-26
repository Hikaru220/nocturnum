package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.ui.Notifications;

public final class NotificationsModule extends Module {
    public final Setting.Num duration = add(new Setting.Num("duration", 3f, 1f, 5f, 0.5f, "s"));
    public final Setting.Bool onToggle = add(new Setting.Bool("onToggle", true));
    public final Setting.Bool sound = add(new Setting.Bool("sound", true));

    public NotificationsModule() {
        super("notifications", Category.HUD, true, true, 1f, 1f);
        anchorRight = true;
        anchorBottom = true;
    }

    @Override public void render(Gfx g, float pt) { Notifications.render(g, false); }
    @Override public int width(Gfx g) { return Notifications.width(); }
    @Override public int height(Gfx g) { return Notifications.height(); }
}
