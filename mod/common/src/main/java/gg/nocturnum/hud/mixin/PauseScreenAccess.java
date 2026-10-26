package gg.nocturnum.hud.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PauseScreen.class)
public interface PauseScreenAccess {
    @Accessor("disconnectButton")
    Button nocturnum$disconnectButton();
}
