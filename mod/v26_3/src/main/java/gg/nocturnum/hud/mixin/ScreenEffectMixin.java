package gg.nocturnum.hud.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.nocturnum.hud.module.ViewTweaks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectMixin {
    /** Moves the first-person fire down before it is drawn. */
    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void nocturnum$fire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        double s = ViewTweaks.LowFire.shift();
        if (s != 0) pose.translate(0.0, s, 0.0);
    }
}
