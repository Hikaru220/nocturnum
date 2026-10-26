package gg.nocturnum.hud.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /** Scales the damage camera shake (all three rotations of the vanilla bob). */
    @ModifyArg(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"), index = 0)
    private float nocturnum$hurt(float degrees) {
        return degrees * gg.nocturnum.hud.module.ViewTweaks.NoHurtShake.scale();
    }
}
