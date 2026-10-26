package gg.nocturnum.hud.mixin;

import gg.nocturnum.hud.module.CameraModules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
    /** Local weather: only the client world is touched, the server does not know. */
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void nocturnum$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) cir.setReturnValue(CameraModules.Weather.rain(cir.getReturnValue()));
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void nocturnum$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) cir.setReturnValue(CameraModules.Weather.thunder(cir.getReturnValue()));
    }
}
