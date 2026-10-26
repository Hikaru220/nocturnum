package gg.nocturnum.hud.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererPostAccess {
    @Accessor("postEffectId")
    Identifier nocturnum$postEffectId();

    @Invoker("setPostEffect")
    void nocturnum$setPostEffect(Identifier id);
}
