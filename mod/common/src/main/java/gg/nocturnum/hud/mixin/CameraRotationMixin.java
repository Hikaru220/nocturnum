package gg.nocturnum.hud.mixin;

import gg.nocturnum.hud.module.CameraModules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public class CameraRotationMixin {
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float nocturnum$yaw(float yaw) {
        CameraModules.Freelook f = CameraModules.Freelook.get();
        return f.enabled() ? yaw + f.yawOff : yaw;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float nocturnum$pitch(float pitch) {
        CameraModules.Freelook f = CameraModules.Freelook.get();
        return f.enabled() ? Math.max(-90f, Math.min(90f, pitch + f.pitchOff)) : pitch;
    }
}
