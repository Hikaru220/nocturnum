package gg.nocturnum.hud.mixin;

import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Button.class)
public interface ButtonAccess {
    @Accessor("onPress")
    Button.OnPress nocturnum$getOnPress();

    @Mutable
    @Accessor("onPress")
    void nocturnum$setOnPress(Button.OnPress onPress);
}
