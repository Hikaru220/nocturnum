package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.ui.Notifications;
import gg.nocturnum.hud.util.Lang;

/** Base of features that show more than vanilla does. Off by default, gated per server, never hidden from moderators. */
public abstract class AnarchyModule extends Module {
    protected AnarchyModule(String id, boolean hud, float x, float y) {
        super(id, Category.ANARCHY, false, hud, x, y);
    }

    @Override
    public boolean canEnable() {
        if (AnarchyProfiles.blocked()) {
            Notifications.push(Lang.t(nameKey()), Lang.t("anarchy.blocked"), Theme.BAD);
            return false;
        }
        if (!AnarchyProfiles.warned()) {
            AnarchyProfiles.markWarned();
            Notifications.push(Lang.t("category.anarchy"), Lang.t("anarchy.warning"), Theme.WARN);
        }
        return true;
    }

    @Override
    public void setEnabled(boolean v) {
        boolean before = enabled();
        super.setEnabled(v);
        if (enabled() != before) AnarchyProfiles.record(this, enabled());
    }
}
