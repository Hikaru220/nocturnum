package gg.nocturnum.hud.module;

import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;
import org.lwjgl.glfw.GLFW;

/** Settings of the menu itself and the shared look of all HUD plates. */
public final class ClickGuiModule extends Module {
    public final Setting.Mode theme = add(new Setting.Mode("theme", 0, "nocturnum", "ice", "toxic", "sakura", "mono", "red", "blue", "custom"));
    public final Setting.Color customAccent = add(new Setting.Color("customAccent", 0xFF8B7CFF));
    public final Setting.Bool blur = add(new Setting.Bool("blur", true));
    public final Setting.Num animSpeed = add(new Setting.Num("animSpeed", 1f, 0.5f, 2f, 0.1f, "x"));
    public final Setting.Bool noAnim = add(new Setting.Bool("noAnim", false));
    public final Setting.Bool lowEnd = add(new Setting.Bool("lowEnd", false));
    public final Setting.Mode language = add(new Setting.Mode("language", 0, "auto", "ru", "en"));
    public final Setting.Bool customFont = adv(new Setting.Bool("customFont", true));
    public final Setting.Num hudOpacity = adv(new Setting.Num("hudOpacity", 70f, 0f, 100f, 1f, "%"));
    public final Setting.Num hudBorder = adv(new Setting.Num("hudBorder", 1f, 0f, 2f, 1f, "px"));
    public final Setting.Num hudRadius = adv(new Setting.Num("hudRadius", 8f, 0f, 16f, 1f, "px"));
    public final Setting.Num hudGlow = adv(new Setting.Num("hudGlow", 35f, 0f, 100f, 1f, "%"));

    public ClickGuiModule() {
        super("clickgui", Category.OTHER, true, false, 0, 0);
        keybind.set(GLFW.GLFW_KEY_RIGHT_SHIFT);
        keybind.owner = this;
    }

    @Override
    public void setEnabled(boolean v) {
        // The menu itself cannot be switched off, otherwise the player would lock themselves out.
        super.setEnabled(true);
    }

    /** Re-applies global look-and-feel settings; called every tick (cheap). */
    public void apply() {
        Tween.speed = noAnim.get() ? 0f : animSpeed.get();
        Gfx.customFont = customFont.get();
        Lang.forced = language.current();
    }
}
