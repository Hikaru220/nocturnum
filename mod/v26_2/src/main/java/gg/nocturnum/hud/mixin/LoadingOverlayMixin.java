package gg.nocturnum.hud.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.module.LoadingScreenModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/**
 * Replaces the visuals of the vanilla start-up overlay but keeps its logic untouched, so loading,
 * fade-out and error handling behave exactly like vanilla.
 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    private boolean nocturnum$logoDrawn;

    @Shadow private float currentProgress;

    @Shadow private void extractProgressBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float alpha) {}

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void nocturnum$reset(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        nocturnum$logoDrawn = false;
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int nocturnum$background(IntSupplier supplier) {
        return LoadingScreenModule.on() ? LoadingScreenModule.BACKGROUND : supplier.getAsInt();
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void nocturnum$logo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier id, int x, int y, float u, float v, int w, int h, int uw, int vh, int tw, int th, int color) {
        if (!LoadingScreenModule.on()) {
            g.blit(pipeline, id, x, y, u, v, w, h, uw, vh, tw, th, color);
            return;
        }
        if (!nocturnum$logoDrawn) {
            nocturnum$logoDrawn = true;
            LoadingScreenModule.drawLogo(new Gfx(g), ((color >>> 24) & 255) / 255f);
        }
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
    private void nocturnum$bar(LoadingOverlay self, GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float alpha) {
        if (!LoadingScreenModule.on()) {
            extractProgressBar(g, x0, y0, x1, y1, alpha);
            return;
        }
        LoadingScreenModule.drawProgress(new Gfx(g), currentProgress, alpha);
    }
}
