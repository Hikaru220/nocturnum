package gg.nocturnum.hud.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.List<net.minecraft.resources.Identifier> requestedPostEffects;

    /** update() rebuilds the requested post effect list every tick; append ours at the end. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "update", at = @At("TAIL"))
    private void nocturnum$post(net.minecraft.client.DeltaTracker tracker, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        for (String id : gg.nocturnum.hud.module.PostModules.active()) requestedPostEffects.add(net.minecraft.resources.Identifier.parse(id));
    }

    /** Scales the damage camera shake (all three rotations of the vanilla bob). */
    @ModifyArg(method = "bobHurt", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"), index = 1)
    private float nocturnum$hurt(float degrees) {
        return degrees * gg.nocturnum.hud.module.ViewTweaks.NoHurtShake.scale();
    }
}
