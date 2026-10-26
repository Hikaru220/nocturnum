package gg.nocturnum.hud.mixin;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.module.ExtraModules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void nocturnum$zoom(float partial, CallbackInfoReturnable<Float> cir) {
        ExtraModules.Zoom z = Config.get(ExtraModules.Zoom.class);
        if (z.enabled()) {
            float d = z.divisor();
            if (d > 1.001f) cir.setReturnValue(cir.getReturnValue() / d);
        }
        gg.nocturnum.hud.gfx.Projection.lastFov = cir.getReturnValue();
    }
}
