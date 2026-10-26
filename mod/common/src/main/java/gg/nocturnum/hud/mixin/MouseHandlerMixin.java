package gg.nocturnum.hud.mixin;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.module.Clicks;
import gg.nocturnum.hud.module.ExtraModules;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    /** Counts clicks for the CPS module (read only; the click itself is untouched). */
    @Inject(method = "onButton", at = @At("HEAD"))
    private void nocturnum$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1) Clicks.press(info.button());
    }

    /** Wheel changes the zoom factor while the zoom key is held. */
    @Inject(method = "onScroll", at = @At("HEAD"))
    private void nocturnum$onScroll(long window, double dx, double dy, CallbackInfo ci) {
        ExtraModules.Zoom z = Config.get(ExtraModules.Zoom.class);
        if (z.enabled()) z.wheel(dy);
    }
}
