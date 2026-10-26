package gg.nocturnum.hud.mixin;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.module.ExtraModules;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nocturnum$zoom(Camera camera, float partial, boolean useConfigured, CallbackInfoReturnable<Float> cir) {
        if (useConfigured) cir.setReturnValue(Config.get(gg.nocturnum.hud.module.ViewTweaks.FovStates.class).adjust(cir.getReturnValue(), gg.nocturnum.hud.platform.Platform.optionFov()));
        ExtraModules.Zoom z = Config.get(ExtraModules.Zoom.class);
        if (z.enabled()) {
            float d = z.divisor();
            if (d > 1.001f) cir.setReturnValue(cir.getReturnValue() / d);
        }
        if (useConfigured) gg.nocturnum.hud.gfx.Projection.lastFov = cir.getReturnValue();
    }

    /** Scales the damage camera shake (all three rotations of the vanilla bob). */
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"), index = 0)
    private float nocturnum$hurt(float degrees) {
        return degrees * gg.nocturnum.hud.module.ViewTweaks.NoHurtShake.scale();
    }
}
