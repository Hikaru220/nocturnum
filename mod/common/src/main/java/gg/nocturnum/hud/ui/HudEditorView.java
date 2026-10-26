package gg.nocturnum.hud.ui;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Lang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Layout editor: drag HUD elements with the mouse, they snap to screen edges, centre and each other.
 * Wheel = scale, right click = open the element's settings, Ctrl+Z = undo. Positions are stored in percent.
 */
public final class HudEditorView extends View {
    private record State(Module m, float x, float y, float scale) {}

    private final Deque<State> undo = new ArrayDeque<>();
    private final Module focus;
    private final Ui ui = new Ui();
    private Module dragging;
    private float grabDx, grabDy;
    private float guideX = -1, guideY = -1;
    private long openedAt = System.currentTimeMillis();
    private Gfx lastGfx;

    public HudEditorView(Module focus) { this.focus = focus; }

    @Override public boolean dimBackground() { return false; }

    private List<Module> elements() {
        List<Module> l = new ArrayList<>();
        for (Module m : Config.modules()) if (m.hud) l.add(m);
        return l;
    }

    private float[] box(Module m, Gfx g) {
        float sc = m.scale.get();
        float w = m.width(g) * sc, h = m.height(g) * sc;
        float x = m.hx * width - (m.anchorRight ? w : 0);
        float y = m.hy * height - (m.anchorBottom ? h : 0);
        x = Math.max(0, Math.min(width - w, x));
        y = Math.max(0, Math.min(height - h, y));
        return new float[]{x, y, w, h};
    }

    @Override
    public void render(Gfx g, int mx, int my, float pt) {
        lastGfx = g;
        g.fill(0, 0, width, height, 0x66000010);
        // grid
        int step = 20;
        for (int x = step; x < width; x += step) g.fill(x, 0, x + 1, height, 0x0CFFFFFF);
        for (int y = step; y < height; y += step) g.fill(0, y, width, y + 1, 0x0CFFFFFF);
        // centre guides
        g.fill(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);

        ui.begin(mx, my);
        for (Module m : elements()) {
            float[] b = box(m, g);
            boolean hover = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
            boolean sel = m == dragging || m == focus;
            g.push(b[0], b[1], m.scale.get());
            if (m instanceof gg.nocturnum.hud.module.TargetHudModule t) t.previewMode = true;
            float prevAlpha = g.alpha;
            if (!m.enabled()) g.alpha = 0.45f;
            m.render(g, pt);
            g.alpha = prevAlpha;
            if (m instanceof gg.nocturnum.hud.module.TargetHudModule t) t.previewMode = false;
            g.pop();
            int outline = sel ? Theme.accent(255) : hover ? Theme.accent(200) : Theme.accent(70);
            Draw.ring(g, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, 3, 1, outline);
            if (hover || sel) {
                String label = Lang.t(m.nameKey()) + (m.enabled() ? "" : " (" + Lang.t("gui.off") + ")");
                g.text(label, Math.round(b[0]), Math.round(b[1]) - g.fontH() - 2 < 0 ? Math.round(b[1] + b[3] + 2) : Math.round(b[1]) - g.fontH() - 2, Theme.TEXT, true);
            }
        }
        if (dragging != null) {
            if (guideX >= 0) g.fill(Math.round(guideX), 0, Math.round(guideX) + 1, height, Theme.accent(200));
            if (guideY >= 0) g.fill(0, Math.round(guideY), width, Math.round(guideY) + 1, Theme.accent(200));
        }

        // toolbar
        String[] presets = {"minimal", "pvp", "streamer"};
        float bw = 70, bh = 16, total = presets.length * (bw + 4) + 90;
        float x = (width - total) / 2f, y = height - 26;
        Draw.rect(g, x - 6, y - 4, total + 12, bh + 8, 6, 0xB0101020);
        Draw.ring(g, x - 6, y - 4, total + 12, bh + 8, 6, 1, Theme.accent(80));
        for (String p : presets) {
            Ui.chip(g, Lang.t("layout." + p), x, y, bw, bh, false, ui.hover(x, y, bw, bh));
            ui.region(x, y, bw, bh).press = (b, px, py) -> {
                snapshotAll();
                applyLayout(p);
            };
            x += bw + 4;
        }
        Ui.chip(g, Lang.t("common.done"), x + 6, y, 78, bh, true, ui.hover(x + 6, y, 78, bh));
        ui.region(x + 6, y, 78, bh).press = (b, px, py) -> Platform.closeScreen();
        g.text(Lang.t("gui.editorHint"), Math.round(width / 2f - g.textW(Lang.t("gui.editorHint")) / 2f), height - 46, Theme.TEXT_DIM, true);
    }

    private void snapshotAll() {
        for (Module m : elements()) undo.push(new State(m, m.hx, m.hy, m.scale.get()));
    }

    public static void applyLayout(String name) {
        for (Module m : Config.modules()) {
            if (!m.hud) continue;
            m.hx = m.defHx;
            m.hy = m.defHy;
            m.scale.set(1f);
        }
        java.util.Set<String> on = switch (name) {
            case "minimal" -> java.util.Set.of("watermark", "fpsping");
            case "pvp" -> java.util.Set.of("watermark", "arraylist", "keystrokes", "cps", "armor", "potions", "fpsping", "notifications");
            default -> java.util.Set.of("notifications", "keystrokes", "cps"); // streamer
        };
        for (Module m : Config.modules()) if (m.hud) m.setEnabled(on.contains(m.id));
        switch (name) {
            case "pvp" -> {
                set("fpsping", 0.01f, 0.1f);
                set("cps", 0.01f, 0.2f);
                set("keystrokes", 0.01f, 0.8f);
                set("armor", 0.92f, 0.45f);
                set("potions", 0.01f, 0.35f);
            }
            case "streamer" -> {
                set("keystrokes", 0.01f, 0.7f);
                set("cps", 0.01f, 0.6f);
            }
            default -> {
            }
        }
        Config.markDirty();
    }

    private static void set(String id, float x, float y) {
        Module m = Config.byId(id);
        if (m != null) {
            m.hx = x;
            m.hy = y;
        }
    }

    private Module at(double mx, double my) {
        List<Module> l = elements();
        for (int i = l.size() - 1; i >= 0; i--) {
            float[] b = box(l.get(i), lastGfx);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return l.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (ui.click(x, y, button)) return true;
        Module m = at(x, y);
        if (m == null || lastGfx == null) return true;
        if (button == 0) {
            undo.push(new State(m, m.hx, m.hy, m.scale.get()));
            dragging = m;
            float[] b = box(m, lastGfx);
            grabDx = (float) x - b[0];
            grabDy = (float) y - b[1];
        } else if (button == 1) {
            ClickGuiView v = new ClickGuiView();
            v.flash(m, null);
            Platform.openView(v);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        ui.release();
        dragging = null;
        guideX = guideY = -1;
        Config.markDirty();
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging == null || lastGfx == null) return false;
        Module m = dragging;
        float sc = m.scale.get();
        float w = m.width(lastGfx) * sc, h = m.height(lastGfx) * sc;
        float nx = (float) x - grabDx, ny = (float) y - grabDy;
        guideX = guideY = -1;
        // snapping: edges, centre and other elements (6px threshold)
        List<Float> xs = new ArrayList<>(List.of(0f, width / 2f, (float) width));
        List<Float> ys = new ArrayList<>(List.of(0f, height / 2f, (float) height));
        for (Module o : elements()) {
            if (o == m || !o.enabled()) continue;
            float[] b = box(o, lastGfx);
            xs.add(b[0]);
            xs.add(b[0] + b[2]);
            ys.add(b[1]);
            ys.add(b[1] + b[3]);
        }
        float bestX = 6, bestY = 6;
        for (float t : xs) {
            for (float edge : new float[]{nx, nx + w / 2f, nx + w}) {
                float d = Math.abs(edge - t);
                if (d < bestX) {
                    bestX = d;
                    nx += t - edge;
                    guideX = t;
                }
            }
        }
        for (float t : ys) {
            for (float edge : new float[]{ny, ny + h / 2f, ny + h}) {
                float d = Math.abs(edge - t);
                if (d < bestY) {
                    bestY = d;
                    ny += t - edge;
                    guideY = t;
                }
            }
        }
        nx = Math.max(0, Math.min(width - w, nx));
        ny = Math.max(0, Math.min(height - h, ny));
        m.hx = (nx + (m.anchorRight ? w : 0)) / width;
        m.hy = (ny + (m.anchorBottom ? h : 0)) / height;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        Module m = at(x, y);
        if (m == null) return false;
        undo.push(new State(m, m.hx, m.hy, m.scale.get()));
        m.scale.setClamped(m.scale.get() + (dy > 0 ? 0.05f : -0.05f));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 90 && (mods & 2) != 0) { // Ctrl+Z
            State s = undo.poll();
            if (s != null) {
                s.m.hx = s.x;
                s.m.hy = s.y;
                s.m.scale.set(s.scale);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        Colors.a(0);
    }
}
