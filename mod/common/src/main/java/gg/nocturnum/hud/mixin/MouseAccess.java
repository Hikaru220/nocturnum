package gg.nocturnum.hud.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Developer-only access used by automated click tests. */
@Mixin(MouseHandler.class)
public interface MouseAccess {
    @Accessor("xpos")
    void nocturnum$xpos(double v);

    @Accessor("ypos")
    void nocturnum$ypos(double v);

    @Invoker("onButton")
    void nocturnum$button(long window, MouseButtonInfo info, int action);
}
