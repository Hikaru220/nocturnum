package gg.nocturnum.hud.ui;

import gg.nocturnum.hud.gfx.Gfx;

/**
 * Version-independent screen content. A per-version ViewScreen adapter forwards Minecraft input to it,
 * so ClickGUI, the layout editor and the menus are written once for every supported game version.
 */
public abstract class View {
    public int width, height;

    public void init(int w, int h) {
        width = w;
        height = h;
    }

    public abstract void render(Gfx g, int mx, int my, float pt);

    public boolean mouseClicked(double x, double y, int button) { return false; }
    public boolean mouseReleased(double x, double y, int button) { return false; }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return false; }
    public boolean keyPressed(int key, int scancode, int mods) { return false; }
    public boolean charTyped(int codepoint) { return false; }
    public void tick() {}
    public void onClose() {}

    /** Whether Esc closes the view. Text fields override this while editing. */
    public boolean escapeCloses() { return true; }
    public boolean pausesGame() { return false; }
    /** Blur the world behind the view (vanilla blur). */
    public boolean blurBackground() { return false; }
    /** Darken the world behind the view. */
    public boolean dimBackground() { return true; }
}
