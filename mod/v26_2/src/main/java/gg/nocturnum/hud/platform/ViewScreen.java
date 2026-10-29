package gg.nocturnum.hud.platform;

import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.ui.View;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Adapts a version-independent {@link View} to a Minecraft screen. */
public final class ViewScreen extends Screen {
    private final View view;

    public ViewScreen(View view) {
        super(Component.literal("Nocturnum"));
        this.view = view;
    }

    public View view() { return view; }

    @Override
    protected void init() { view.init(width, height); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        Gfx g = new Gfx(graphics);
        if (view.blurBackground()) g.blur();
        view.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        // the view draws its own background
    }

    @Override public void tick() { view.tick(); }
    @Override public boolean isPauseScreen() { return view.pausesGame(); }
    @Override public boolean shouldCloseOnEsc() { return view.escapeCloses(); }
    @Override public void removed() { view.onClose(); }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) { return view.mouseClicked(e.x(), e.y(), e.button()) || super.mouseClicked(e, doubleClick); }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) { return view.mouseReleased(e.x(), e.y(), e.button()) || super.mouseReleased(e); }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return view.mouseDragged(e.x(), e.y(), e.button(), dx, dy) || super.mouseDragged(e, dx, dy); }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return view.mouseScrolled(x, y, dx, dy) || super.mouseScrolled(x, y, dx, dy); }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (view.keyPressed(e.key(), e.scancode(), e.modifiers())) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) { return view.charTyped(e.codepoint()) || super.charTyped(e); }
}
