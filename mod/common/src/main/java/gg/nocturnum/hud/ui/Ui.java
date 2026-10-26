package gg.nocturnum.hud.ui;

import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Tween;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tiny immediate-mode toolkit: widgets are drawn every frame and register a hit region. On a click the
 * topmost region from the previous frame receives the event. No retained widget tree, so layout code stays short.
 */
public final class Ui {
    public interface Press { void on(int button, double x, double y); }
    public interface Drag { void on(double x, double y, double dx, double dy); }

    public static final class Hit {
        public float x, y, w, h;
        public Press press;
        public Drag drag;
        public Consumer<Double> scroll;
        public String tip;
        public float clipX1, clipY1, clipX2, clipY2;
        public float ox, oy;

        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h && mx >= clipX1 && mx < clipX2 && my >= clipY1 && my < clipY2;
        }
    }

    private List<Hit> frame = new ArrayList<>();
    private List<Hit> last = new ArrayList<>();
    private Hit pressed;
    public float mx, my;
    private float ox, oy;

    /** Origin of the coordinate system used by region()/hover()/clip() (set per column etc). */
    public void origin(float x, float y) {
        ox = x;
        oy = y;
    }
    public String tooltip;
    public float tipX, tipY;
    public float scale = 1f;
    private float clipX1 = -1e9f, clipY1 = -1e9f, clipX2 = 1e9f, clipY2 = 1e9f;
    private final Map<Object, Tween> tweens = new IdentityHashMap<>();

    public void begin(float mouseX, float mouseY) {
        last = frame;
        frame = new ArrayList<>();
        mx = mouseX;
        my = mouseY;
        tooltip = null;
        clipX1 = clipY1 = -1e9f;
        clipX2 = clipY2 = 1e9f;
        ox = oy = 0;
    }

    /** Limit hit-testing to a rectangle (for scrolled / animated areas). */
    public void clip(float x1, float y1, float x2, float y2) {
        clipX1 = Math.max(clipX1, x1 + ox);
        clipY1 = Math.max(clipY1, y1 + oy);
        clipX2 = Math.min(clipX2, x2 + ox);
        clipY2 = Math.min(clipY2, y2 + oy);
    }

    public float[] saveClip() { return new float[]{clipX1, clipY1, clipX2, clipY2}; }

    public void restoreClip(float[] c) {
        clipX1 = c[0];
        clipY1 = c[1];
        clipX2 = c[2];
        clipY2 = c[3];
    }

    public Hit region(float x, float y, float w, float h) {
        Hit hit = new Hit();
        hit.ox = ox;
        hit.oy = oy;
        hit.x = x + ox;
        hit.y = y + oy;
        hit.w = w;
        hit.h = h;
        hit.clipX1 = clipX1;
        hit.clipY1 = clipY1;
        hit.clipX2 = clipX2;
        hit.clipY2 = clipY2;
        frame.add(hit);
        return hit;
    }

    public boolean hover(float x, float y, float w, float h) {
        x += ox;
        y += oy;
        return mx >= x && my >= y && mx < x + w && my < y + h && mx >= clipX1 && mx < clipX2 && my >= clipY1 && my < clipY2;
    }

    private Hit hitAt(double x, double y) {
        for (int i = last.size() - 1; i >= 0; i--) {
            Hit h = last.get(i);
            if (h.contains(x, y)) return h;
        }
        return null;
    }

    public boolean click(double x, double y, int button) {
        Hit h = hitAt(x, y);
        if (h == null) return false;
        if (h.press != null) h.press.on(button, x - h.ox, y - h.oy);
        pressed = h;
        return h.press != null || h.drag != null;
    }

    public boolean release() {
        boolean had = pressed != null;
        pressed = null;
        return had;
    }

    public boolean dragged(double x, double y, double dx, double dy) {
        if (pressed != null && pressed.drag != null) {
            pressed.drag.on(x - pressed.ox, y - pressed.oy, dx, dy);
            return true;
        }
        return false;
    }

    public boolean scrolled(double x, double y, double amount) {
        Hit h = hitAt(x, y);
        // bubble to the nearest region with a scroll handler
        for (int i = last.size() - 1; i >= 0; i--) {
            Hit c = last.get(i);
            if (c.scroll != null && c.contains(x, y)) {
                c.scroll.accept(amount);
                return true;
            }
        }
        return h != null && h.scroll != null;
    }

    public Hit pressedHit() { return pressed; }

    public Tween tween(Object key, float init) { return tweens.computeIfAbsent(key, k -> new Tween(init)); }

    // ---------------------------------------------------------------- widgets

    /** Pill switch with a "jumping" knob (ease-out-back). */
    public static void toggle(Gfx g, Tween t, float x, float y, boolean on) {
        t.to(on ? 1 : 0, 180, Easing.OUT_BACK);
        float k = t.get();
        int off = 0xFF3A3A5A;
        int bg = Colors.lerp(off, Theme.accent(), Math.max(0, Math.min(1, k)));
        Draw.rect(g, x, y, 20, 10, 5, bg);
        Draw.circle(g, x + 5 + 10 * k, y + 5, 3.6f, 0xFFFFFFFF);
    }

    public static void chip(Gfx g, String text, float x, float y, float w, float h, boolean active, boolean hover) {
        int bg = active ? Theme.accent(200) : hover ? 0xFF343456 : 0xFF26263E;
        Draw.rect(g, x, y, w, h, 4, bg);
        int tc = active ? 0xFF0D0D18 : Theme.TEXT;
        g.text(text, Math.round(x + (w - g.textW(text)) / 2f), Math.round(y + (h - g.fontH()) / 2f) + 1, tc, false);
    }
}
