package gg.nocturnum.hud.mixin;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.module.ExtraModules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    /** Hides the vanilla crosshair while the custom one is on. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nocturnum$crosshair(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (Config.get(ExtraModules.Crosshair.class).enabled()) ci.cancel();
    }
}
