package gg.nocturnum.hud.mixin;

import gg.nocturnum.hud.module.CameraModules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    /** While Freelook is held, mouse movement turns the camera instead of the character. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void nocturnum$freelook(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            CameraModules.Freelook f = CameraModules.Freelook.get();
            if (f.active()) {
                f.turn(yaw, pitch);
                ci.cancel();
            }
        }
    }
}
