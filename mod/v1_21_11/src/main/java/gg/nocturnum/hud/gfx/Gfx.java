package gg.nocturnum.hud.gfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin per-Minecraft-version facade over the GUI graphics object. All mod drawing goes through here,
 * so the rest of the code is identical for every supported game version.
 */
public final class Gfx {
    private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("nocturnum", "ui"));
    private static final Style UI_STYLE = Style.EMPTY.withFont(UI_FONT);
    private static final Map<String, Component> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Component> e) { return size() > 600; }
    };

    /** true: Manrope (smooth, Cyrillic). false: vanilla pixel font. */
    public static boolean customFont = true;

    public final GuiGraphics g;

    /** Global opacity multiplier for fades (applies to fills and text). */
    public float alpha = 1f;

    public Gfx(GuiGraphics g) { this.g = g; }

    private static Font font() { return Minecraft.getInstance().font; }

    private static Component comp(String s) {
        if (!customFont) return Component.literal(s);
        Component c = CACHE.get(s);
        if (c == null) {
            c = Component.literal(s).withStyle(UI_STYLE);
            CACHE.put(s, c);
        }
        return c;
    }

    public int w() { return g.guiWidth(); }
    public int h() { return g.guiHeight(); }

    private int a(int argb) {
        if (alpha >= 0.999f) return argb;
        int al = Math.round(((argb >>> 24) & 255) * alpha);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, a(argb)); }

    public void gradientV(int x1, int y1, int x2, int y2, int top, int bottom) { g.fillGradient(x1, y1, x2, y2, a(top), a(bottom)); }

    public void text(String s, int x, int y, int argb, boolean shadow) { g.drawString(font(), comp(s), x, y, a(argb), shadow); }

    public int textW(String s) { return font().width(comp(s)); }

    public int fontH() { return font().lineHeight; }

    public void push(float tx, float ty, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(tx, ty);
        g.pose().scale(scale, scale);
    }

    public void pop() { g.pose().popMatrix(); }

    public void scissor(int x1, int y1, int x2, int y2) { g.enableScissor(x1, y1, x2, y2); }
    public void unscissor() { g.disableScissor(); }

    public void item(ItemStack s, int x, int y) { g.renderItem(s, x, y); }
    public void itemDecor(ItemStack s, int x, int y) { g.renderItemDecorations(font(), s, x, y); }

    public void sprite(Identifier id, int x, int y, int w, int h) { g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h); }

    public void blur() { g.blurBeforeThisStratum(); }
}
