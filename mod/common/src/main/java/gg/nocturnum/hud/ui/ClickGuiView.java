package gg.nocturnum.hud.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.module.ClickGuiModule;
import gg.nocturnum.hud.module.StreamerModule;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The settings menu: one column per category, rows with switches, right click expands settings,
 * search at the bottom. Every change applies instantly (no Save button); Ctrl+Z undoes.
 */
public final class ClickGuiView extends View {
    private static final int COL_W = 124, HEAD_H = 18, ROW_H = 16, GAP = 8;
    private static final int[] PALETTE = {
        0xFFFFFFFF, 0xFFB0B0C8, 0xFF1A1A2A, 0xFFFF5A6A, 0xFFFF9A3C, 0xFFFFD23F,
        0xFF7CFF6B, 0xFF2ECC9A, 0xFF4FD6E8, 0xFF4A9BFF, 0xFF8B7CFF, 0xFFFF8CC6
    };

    private static final class Col {
        float x, y;
        boolean collapsed;
        float scroll;
        final Tween enter = new Tween(0);
    }

    private final Ui ui = new Ui();
    private final Map<String, Col> cols = new HashMap<>();
    private final Set<Module> expanded = new HashSet<>();
    private final Set<Module> advancedOpen = new HashSet<>();
    private final Map<Module, Tween> expandT = new IdentityHashMap<>();
    private final Map<Object, Tween> toggleT = new IdentityHashMap<>();
    private final Deque<Runnable> undo = new ArrayDeque<>();

    private final Tween open = new Tween(0);
    private boolean closing;
    private String search = "";
    private boolean searchFocus;

    private Setting<?> listening;
    private Module listeningModule;
    private Setting.Num editNum;
    private Setting.Color editHex;
    private final StringBuilder editBuf = new StringBuilder();
    private Setting.Color pickerOpen;
    private boolean profilesOpen;
    private String profileName = "";
    private boolean profileNameFocus;
    private String flashSetting;
    private Module flashModule;
    private long flashAt;

    // column dragging
    private Col dragging;
    private double dragStartX, dragStartY;
    private boolean dragMoved;
    private float uiScale = 1f;

    public ClickGuiView() {
        open.to(1, 220, Easing.OUT_CUBIC);
        loadLayout();
    }

    @Override public boolean escapeCloses() { return false; }
    @Override public boolean blurBackground() { return Config.get(ClickGuiModule.class).blur.get(); }

    // ------------------------------------------------------------------ layout persistence

    private Col col(String id) { return cols.computeIfAbsent(id, k -> new Col()); }

    private void loadLayout() {
        try {
            var f = Config.dir().resolve("gui_layout.json");
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                for (var e : o.entrySet()) {
                    JsonObject c = e.getValue().getAsJsonObject();
                    Col col = col(e.getKey());
                    col.x = c.get("x").getAsFloat();
                    col.y = c.get("y").getAsFloat();
                    col.collapsed = c.has("collapsed") && c.get("collapsed").getAsBoolean();
                    col.scroll = 0;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveLayout() {
        try {
            JsonObject o = new JsonObject();
            for (var e : cols.entrySet()) {
                JsonObject c = new JsonObject();
                c.addProperty("x", e.getValue().x);
                c.addProperty("y", e.getValue().y);
                c.addProperty("collapsed", e.getValue().collapsed);
                o.add(e.getKey(), c);
            }
            Files.writeString(Config.dir().resolve("gui_layout.json"), o.toString());
        } catch (Exception ignored) {
        }
    }

    private void defaultPositions(float vw) {
        List<String> order = columnOrder();
        float total = order.size() * COL_W + (order.size() - 1) * GAP;
        float x0 = Math.max(6, (vw - total) / 2f);
        int i = 0;
        for (String id : order) {
            Col c = col(id);
            if (c.x == 0 && c.y == 0) {
                c.x = x0 + i * (COL_W + GAP);
                c.y = 16;
            }
            i++;
        }
    }

    private List<String> columnOrder() {
        List<String> l = new ArrayList<>();
        if (Config.modules().stream().anyMatch(m -> m.favorite)) l.add("favorites");
        for (Category c : Category.values()) {
            if (Config.modules().stream().anyMatch(m -> m.category == c)) l.add(c.id);
        }
        return l;
    }

    // ------------------------------------------------------------------ helpers

    private Tween tt(Object key) { return toggleT.computeIfAbsent(key, k -> new Tween(0)); }

    private boolean matches(Module m) {
        if (search.isEmpty()) return true;
        String q = search.toLowerCase();
        return name(m).toLowerCase().contains(q) || Lang.english(m.nameKey()).toLowerCase().contains(q) || m.id.contains(q) || settingMatch(m) != null;
    }

    private Setting<?> settingMatch(Module m) {
        if (search.isEmpty()) return null;
        String q = search.toLowerCase();
        for (Setting<?> s : m.settings) {
            if (Lang.t(s.nameKey()).toLowerCase().contains(q) || Lang.english(s.nameKey()).toLowerCase().contains(q)) return s;
        }
        return null;
    }

    private static List<String> wrap(Gfx g, String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (g.textW(t) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(t);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** Shortens text with an ellipsis so it never runs under the switch. */
    private static String fit(Gfx g, String s, int maxW) {
        if (g.textW(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && g.textW(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static String name(Module m) { return Lang.t(m.nameKey()); }

    private void pushUndo(Setting<?> s) {
        JsonElement snap = s.save();
        undo.push(() -> {
            s.load(snap);
            Config.markDirty();
        });
        while (undo.size() > 80) undo.removeLast();
    }

    private void doUndo() {
        Runnable r = undo.poll();
        if (r != null) r.run();
    }

    private void commitEdit() {
        if (editNum != null) {
            try {
                String t = editBuf.toString().replace(',', '.');
                if (!t.isBlank()) {
                    pushUndo(editNum);
                    editNum.setClamped(Float.parseFloat(t));
                }
            } catch (NumberFormatException ignored) {
            }
            editNum = null;
        }
        if (editHex != null) {
            int c = Colors.parseHex(editBuf.toString(), 0);
            if (c != 0) {
                pushUndo(editHex);
                editHex.set(Colors.withAlpha(c, Colors.a(editHex.get())));
            }
            editHex = null;
        }
    }

    private boolean editing() { return editNum != null || editHex != null || searchFocus || profileNameFocus; }

    private void startClose() {
        if (closing) return;
        closing = true;
        commitEdit();
        open.to(0, 150, Easing.IN_CUBIC);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Gfx g, int mx, int my, float pt) {
        if (closing && open.get() <= 0.01f) {
            saveLayout();
            Platform.closeScreen();
            return;
        }
        float k = open.get();
        uiScale = Math.max(0.55f, Math.min(1.25f, width / 640f));
        float vw = width / uiScale, vh = height / uiScale;
        float vmx = mx / uiScale, vmy = my / uiScale;
        defaultPositions(vw);

        g.fill(0, 0, width, height, Colors.withAlpha(0x05050C, Math.round(150 * k)));
        ui.begin(vmx, vmy);
        ui.scale = uiScale;
        g.push(0, 0, uiScale);

        List<String> order = columnOrder();
        int idx = 0;
        for (String id : order) {
            Col c = col(id);
            float delay = idx * 30f;
            float local = Math.max(0, Math.min(1, (k * (220f + order.size() * 30f) - delay) / 220f));
            c.enter.set(local);
            float a = Easing.OUT_CUBIC.apply(local);
            float scale = closing ? 0.95f + 0.05f * k : 1f;
            g.alpha = Math.min(1f, a);
            g.push(c.x + COL_W / 2f * (1 - scale), c.y + (1 - a) * -28 + (1 - scale) * 60, scale);
            ui.origin(c.x + COL_W / 2f * (1 - scale), c.y + (1 - a) * -28 + (1 - scale) * 60);
            drawColumn(g, id, c, vh - 70);
            ui.origin(0, 0);
            g.pop();
            g.alpha = 1f;
            idx++;
        }
        g.alpha = Math.min(1f, k);
        drawBottomBar(g, vw, vh);
        if (profilesOpen) drawProfiles(g, vw, vh);
        g.alpha = 1f;
        // tooltip
        if (ui.tooltip != null && !ui.tooltip.isEmpty()) drawTooltip(g, ui.tooltip, vmx, vmy, vw, vh);
        g.pop();
    }

    private void drawTooltip(Gfx g, String text, float mx, float my, float vw, float vh) {
        String[] lines = text.split("\n");
        int w = 0;
        for (String l : lines) w = Math.max(w, g.textW(l));
        w += 12;
        int h = lines.length * (g.fontH() + 1) + 8;
        float x = Math.min(mx + 10, vw - w - 4), y = Math.min(my + 12, vh - h - 4);
        Draw.rect(g, x, y, w, h, 4, 0xF2121224);
        Draw.ring(g, x, y, w, h, 4, 1, Theme.accent(110));
        int ty = Math.round(y) + 4;
        for (int i = 0; i < lines.length; i++) {
            g.text(lines[i], Math.round(x) + 6, ty, i == 0 ? Theme.TEXT : Theme.TEXT_DIM, false);
            ty += g.fontH() + 1;
        }
    }

    private List<Module> modulesOf(String colId) {
        List<Module> l = new ArrayList<>();
        for (Module m : Config.modules()) {
            if (colId.equals("favorites") ? m.favorite : (m.category.id.equals(colId))) {
                if (matches(m)) l.add(m);
            }
        }
        return l;
    }

    private void drawColumn(Gfx g, String id, Col c, float maxH) {
        List<Module> mods = modulesOf(id);
        if (mods.isEmpty() && !search.isEmpty()) return;
        String title = id.equals("favorites") ? Lang.t("category.favorites") : Lang.t("category." + id);
        // header
        Draw.rect(g, 0, 0, COL_W, HEAD_H, 5, Theme.accent(235));
        Draw.centeredText(g, title.toUpperCase(), COL_W / 2f, (HEAD_H - g.fontH()) / 2 + 1, 0xFF0D0D18, false);
        Ui.Hit head = ui.region(0, 0, COL_W, HEAD_H);
        head.press = (b, x, y) -> {
            dragging = c;
            dragStartX = ui.mx;
            dragStartY = ui.my;
            dragMoved = false;
        };
        head.drag = (x, y, dx, dy) -> {
            if (Math.abs(ui.mx - dragStartX) + Math.abs(ui.my - dragStartY) > 3) dragMoved = true;
            if (dragMoved) {
                c.x += dx;
                c.y += dy;
            }
        };
        if (ui.hover(0, 0, COL_W, HEAD_H)) ui.tooltip = Lang.t("gui.dragHint");
        if (c.collapsed) return;

        // body: measure
        float bodyTop = HEAD_H + 2;
        float total = 0;
        for (Module m : mods) total += ROW_H + panelHeight(g, m);
        float viewH = Math.min(total + 4, maxH - c.y - bodyTop);
        viewH = Math.max(viewH, ROW_H);
        Draw.rect(g, 0, bodyTop, COL_W, viewH + 4, 5, 0xE0121220);
        Draw.ring(g, 0, bodyTop, COL_W, viewH + 4, 5, 1, Theme.accent(70));

        float maxScroll = Math.max(0, total - viewH);
        c.scroll = Math.max(0, Math.min(maxScroll, c.scroll));
        float[] clipSave = ui.saveClip();
        ui.clip(0, bodyTop, COL_W, bodyTop + viewH + 4);
        Ui.Hit area = ui.region(0, bodyTop, COL_W, viewH + 4);
        area.scroll = amt -> {
            if (expandedUnderMouse()) return;
            c.scroll = Math.max(0, Math.min(maxScroll, c.scroll - amt.floatValue() * 14));
        };
        g.scissor(0, Math.round(bodyTop), COL_W, Math.round(bodyTop + viewH + 4));
        float y = bodyTop + 2 - c.scroll;
        for (Module m : mods) {
            y += drawModule(g, m, c, 0, y);
        }
        g.unscissor();
        ui.restoreClip(clipSave);
        if (maxScroll > 0) {
            float th = Math.max(12, viewH * viewH / total);
            float ty = bodyTop + 2 + (viewH - th) * (c.scroll / maxScroll);
            Draw.rect(g, COL_W - 3, ty, 1.5f, th, 0.7f, Theme.accent(150));
        }
    }

    private boolean expandedUnderMouse() { return false; }

    private float panelHeight(Gfx g, Module m) {
        Tween t = expandT.computeIfAbsent(m, k -> new Tween(0));
        boolean exp = isExpanded(m);
        t.to(exp ? 1 : 0, 200, Easing.OUT_QUART);
        float f = t.get();
        if (f < 0.002f) return 0;
        return layoutRows(g, m).stream().mapToInt(r -> r.h).sum() * f + 2;
    }

    private boolean isExpanded(Module m) {
        if (expanded.contains(m)) return true;
        return !search.isEmpty() && settingMatch(m) != null && !name(m).toLowerCase().contains(search.toLowerCase());
    }

    private float drawModule(Gfx g, Module m, Col c, float x, float y) {
        boolean visible = y + ROW_H > -2 && y < 4000;
        float h = ROW_H;
        boolean hasSettings = !m.settings.isEmpty() || m.hud || !m.presets.isEmpty();
        Tween en = tt(m);
        en.to(m.enabled() ? 1 : 0, 180, Easing.OUT_BACK);
        float e = en.get();
        boolean hov = ui.hover(x, y, COL_W, ROW_H);
        Ui.Hit row = ui.region(x, y, COL_W, ROW_H);
        row.press = (b, mx, my) -> {
            if (b == 0) {
                if (mx > COL_W - 30 || !hasSettings) {
                    if (m.id.equals("clickgui")) return;
                    m.toggle();
                } else if (mx < 14) {
                    m.favorite = !m.favorite;
                    Config.markDirty();
                } else {
                    m.toggle();
                }
            } else if (b == 1) {
                if (expanded.contains(m)) expanded.remove(m); else expanded.add(m);
            } else if (b == 2) {
                listening = m.keybind;
                listeningModule = m;
                expanded.add(m);
            }
        };
        if (hov) ui.tooltip = Lang.t(m.descKey()) + "\n" + Lang.english(m.nameKey());
        if (visible) {
            int base = Colors.lerp(0x00000000, Theme.accent(70), Math.max(0, Math.min(1, e)));
            float hv = hov ? 1.02f : 1f;
            if (m.enabled() || e > 0.02f) Draw.rect(g, x + 2, y + 1, COL_W - 4, ROW_H - 2, 3, base);
            else if (hov) Draw.rect(g, x + 2, y + 1, COL_W - 4, ROW_H - 2, 3, 0x22FFFFFF);
            int tc = m.enabled() ? Theme.TEXT : Theme.TEXT_DIM;
            if (hov && m.favorite == false) g.text("☆", Math.round(x + 5), Math.round(y + 4), Theme.TEXT_FAINT, false);
            else if (m.favorite) g.text("★", Math.round(x + 5), Math.round(y + 4), Theme.WARN, false);
            g.text(fit(g, name(m), COL_W - 46 - (hov || m.favorite ? 15 : 7)), Math.round(x + (hov || m.favorite ? 15 : 7)), Math.round(y + (ROW_H - g.fontH()) / 2f + 1), tc, false);
            if (hasSettings) {
                Tween ex = expandT.computeIfAbsent(m, k -> new Tween(0));
                String arrow = ex.get() > 0.5f ? "v" : ">";
                g.text(arrow, Math.round(x + COL_W - 38), Math.round(y + 4), Theme.TEXT_FAINT, false);
            }
            Ui.toggle(g, tt(m.keybind), x + COL_W - 26, y + 3, m.enabled());
            if (hv < 1f) hv = 1f;
        }
        Tween ex = expandT.computeIfAbsent(m, k -> new Tween(0));
        float panel = panelHeight(g, m);
        if (panel > 0.5f) {
            float[] cs = ui.saveClip();
            ui.clip(0, y + ROW_H, COL_W, y + ROW_H + panel);
            g.scissor(0, Math.round(y + ROW_H), COL_W, Math.round(y + ROW_H + panel));
            drawPanel(g, m, c, x, y + ROW_H, panel);
            g.unscissor();
            ui.restoreClip(cs);
        }
        return h + panel;
    }

    // ------------------------------------------------------------------ settings panel

    private interface Painter { void draw(Gfx g, float x, float y, float w); }

    private record Row(int h, Painter paint) {}

    private List<Row> layoutRows(Gfx g, Module m) {
        List<Row> rows = new ArrayList<>();
        // description (first line of the panel, ТЗ 4.4.2)
        String desc = Lang.t(m.descKey());
        List<String> lines = wrap(g, desc, COL_W - 14);
        rows.add(new Row(lines.size() * (g.fontH() + 1) + 6, (gg, x, y, w) -> {
            int ty = Math.round(y) + 3;
            for (String l : lines) {
                gg.text(l, Math.round(x) + 7, ty, Theme.TEXT_DIM, false);
                ty += gg.fontH() + 1;
            }
        }));
        if (m.hud) rows.add(previewRow(m));
        if (m.hud) {
            rows.add(new Row(18, (gg, x, y, w) -> {
                boolean hv = ui.hover(x + 6, y + 1, w - 12, 15);
                Ui.chip(gg, Lang.t("gui.moveOnScreen"), x + 6, y + 1, w - 12, 15, false, hv);
                Ui.Hit h = ui.region(x + 6, y + 1, w - 12, 15);
                h.press = (b, mx, my) -> {
                    commitEdit();
                    saveLayout();
                    Platform.openView(new HudEditorView(m));
                };
            }));
        }
        if (!m.presets.isEmpty()) {
            rows.add(new Row(18, (gg, x, y, w) -> {
                float cx = x + 6;
                float cw = (w - 12 - 4 * (m.presets.size() - 1)) / m.presets.size();
                for (String p : m.presets.keySet()) {
                    boolean hv = ui.hover(cx, y + 1, cw, 15);
                    Ui.chip(gg, Lang.t("preset." + p), cx, y + 1, cw, 15, false, hv);
                    Ui.Hit h = ui.region(cx, y + 1, cw, 15);
                    String name = p;
                    h.press = (b, mx, my) -> m.applyPreset(name);
                    cx += cw + 4;
                }
            }));
        }
        List<Setting<?>> basic = new ArrayList<>(), adv = new ArrayList<>();
        for (Setting<?> s : m.settings) (s.advanced ? adv : basic).add(s);
        for (Setting<?> s : basic) rows.addAll(settingRows(g, m, s));
        if (!adv.isEmpty()) {
            boolean open = advancedOpen.contains(m);
            rows.add(new Row(14, (gg, x, y, w) -> {
                gg.text((open ? "v " : "> ") + Lang.t("gui.advanced"), Math.round(x) + 7, Math.round(y) + 3, Theme.accent(), false);
                Ui.Hit h = ui.region(x, y, w, 14);
                h.press = (b, mx, my) -> {
                    if (open) advancedOpen.remove(m); else advancedOpen.add(m);
                };
            }));
            if (open) for (Setting<?> s : adv) rows.addAll(settingRows(g, m, s));
        }
        // key binding row
        rows.addAll(settingRows(g, m, m.keybind));
        // reset module
        if (m.isModified()) {
            rows.add(new Row(18, (gg, x, y, w) -> {
                boolean hv = ui.hover(x + 6, y + 1, w - 12, 15);
                Ui.chip(gg, "↺ " + Lang.t("gui.resetModule"), x + 6, y + 1, w - 12, 15, false, hv);
                ui.region(x + 6, y + 1, w - 12, 15).press = (b, mx, my) -> {
                    for (Setting<?> s : m.settings) pushUndo(s);
                    m.resetAll();
                };
            }));
        }
        return rows;
    }

    private Row previewRow(Module m) {
        int ph = 46;
        return new Row(ph + 4, (gg, x, y, w) -> {
            Draw.rect(gg, x + 5, y + 2, w - 10, ph, 4, 0x66000000);
            // live preview of the element itself, scaled to fit the box
            int ew = Math.max(1, m.width(gg)), eh = Math.max(1, m.height(gg));
            float sc = Math.min(1f, Math.min((w - 18) / ew, (ph - 8) / (float) eh));
            gg.scissor(Math.round(x + 5), Math.round(y + 2), Math.round(x + w - 5), Math.round(y + 2 + ph));
            gg.push(x + 8, y + 5, sc);
            boolean wasShown = m.enabled();
            if (m instanceof gg.nocturnum.hud.module.TargetHudModule t) t.previewMode = true;
            m.render(gg, 0);
            if (m instanceof gg.nocturnum.hud.module.TargetHudModule t) t.previewMode = false;
            gg.pop();
            gg.unscissor();
            if (!wasShown) gg.fill(Math.round(x) + 5, Math.round(y) + 2, Math.round(x + w) - 5, Math.round(y) + 2 + ph, 0x66000000);
        });
    }

    private List<Row> settingRows(Gfx g, Module m, Setting<?> s) {
        List<Row> rows = new ArrayList<>();
        String label = s == m.keybind ? Lang.t("gui.key") : Lang.t(s.nameKey());
        String tip = s == m.keybind ? Lang.t("gui.key.desc") : Lang.t(s.descKey());
        boolean flash = s == flashSettingObj(m) && System.currentTimeMillis() - flashAt < 1200;
        if (s instanceof Setting.Bool b) {
            rows.add(new Row(14, (gg, x, y, w) -> {
                if (flash) Draw.rect(gg, x + 2, y, w - 4, 14, 3, Theme.accent(60));
                gg.text(label, Math.round(x) + 7, Math.round(y) + 3, Theme.TEXT, false);
                Ui.toggle(gg, tt(b), x + w - 26, y + 2, b.get());
                resetIcon(gg, s, x + w - 38, y + 3);
                Ui.Hit h = ui.region(x, y, w - 40, 14);
                h.tip = tip;
                if (ui.hover(x, y, w - 40, 14)) ui.tooltip = tip;
                h.press = (bt, mx, my) -> {
                    pushUndo(b);
                    b.toggle();
                };
            }));
        } else if (s instanceof Setting.Num n) {
            rows.add(new Row(24, (gg, x, y, w) -> {
                if (flash) Draw.rect(gg, x + 2, y, w - 4, 24, 3, Theme.accent(60));
                gg.text(label, Math.round(x) + 7, Math.round(y) + 2, Theme.TEXT, false);
                String val = editNum == n ? editBuf + "|" : fmt(n);
                int vw = gg.textW(val);
                gg.text(val, Math.round(x + w - 8 - vw), Math.round(y) + 2, editNum == n ? Theme.accent() : Theme.TEXT_DIM, false);
                Ui.Hit vh = ui.region(x + w - 12 - vw, y, vw + 8, 11);
                vh.press = (bt, mx, my) -> {
                    commitEdit();
                    editNum = n;
                    editBuf.setLength(0);
                };
                resetIcon(gg, s, x + w - 22 - vw, y + 2);
                float tx = x + 7, tw = w - 14, ty = y + 15;
                Draw.rect(gg, tx, ty, tw, 4, 2, 0xFF2A2A44);
                float f = Math.max(0, Math.min(1, n.get01()));
                Draw.rectGradientH(gg, tx, ty, Math.max(3, tw * f), 4, 2, Theme.accent(), Theme.accent2());
                Draw.circle(gg, tx + tw * f, ty + 2, 3.5f, 0xFFFFFFFF);
                Ui.Hit h = ui.region(tx - 3, y + 11, tw + 6, 12);
                h.tip = tip;
                if (ui.hover(x, y, w, 24)) ui.tooltip = tip;
                h.press = (bt, mx, my) -> {
                    commitEdit();
                    pushUndo(n);
                    n.setClamped(n.min + (float) ((mx - tx) / tw) * (n.max - n.min));
                };
                h.drag = (mx, my, dx, dy) -> n.setClamped(n.min + (float) ((mx - tx) / tw) * (n.max - n.min));
                h.scroll = amt -> {
                    pushUndo(n);
                    n.setClamped(n.get() + (float) Math.signum(amt) * Math.max(n.step, (n.max - n.min) / 100f));
                };
            }));
        } else if (s instanceof Setting.Mode md) {
            rows.add(new Row(15, (gg, x, y, w) -> {
                if (flash) Draw.rect(gg, x + 2, y, w - 4, 15, 3, Theme.accent(60));
                gg.text(label, Math.round(x) + 7, Math.round(y) + 3, Theme.TEXT, false);
                String cur = Lang.t(md.optionKey(md.get()));
                int cw = gg.textW(cur) + 10;
                boolean hv = ui.hover(x + w - cw - 6, y + 1, cw, 13);
                Ui.chip(gg, cur, x + w - cw - 6, y + 1, cw, 13, false, hv);
                resetIcon(gg, s, x + w - cw - 18, y + 3);
                Ui.Hit h = ui.region(x, y, w, 15);
                h.tip = tip;
                if (ui.hover(x, y, w, 15)) ui.tooltip = tip;
                h.press = (bt, mx, my) -> {
                    pushUndo(md);
                    if (bt == 1) md.set((md.get() + md.options.size() - 1) % md.options.size()); else md.next();
                };
            }));
        } else if (s instanceof Setting.Color cl) {
            rows.add(new Row(15, (gg, x, y, w) -> {
                if (flash) Draw.rect(gg, x + 2, y, w - 4, 15, 3, Theme.accent(60));
                gg.text(label, Math.round(x) + 7, Math.round(y) + 3, Theme.TEXT, false);
                Draw.rect(gg, x + w - 26, y + 2, 20, 11, 3, 0xFF000000);
                Draw.rect(gg, x + w - 25, y + 3, 18, 9, 2, Colors.withAlpha(cl.argb(), 255));
                resetIcon(gg, s, x + w - 38, y + 3);
                Ui.Hit h = ui.region(x, y, w - 40, 15);
                h.tip = tip;
                if (ui.hover(x, y, w - 40, 15)) ui.tooltip = tip;
                h.press = (bt, mx, my) -> {
                    commitEdit();
                    pickerOpen = pickerOpen == cl ? null : cl;
                };
            }));
            if (pickerOpen == cl) rows.add(colorPicker(cl));
        } else if (s instanceof Setting.Key kb) {
            rows.add(new Row(15, (gg, x, y, w) -> {
                gg.text(label, Math.round(x) + 7, Math.round(y) + 3, Theme.TEXT, false);
                boolean ls = listening == kb;
                String cur = ls ? "..." : kb.get() < 0 ? Lang.t("gui.none") : Platform.keyName(kb.get());
                int cw = gg.textW(cur) + 10;
                boolean hv = ui.hover(x + w - cw - 6, y + 1, cw, 13);
                Ui.chip(gg, cur, x + w - cw - 6, y + 1, cw, 13, ls, hv);
                String mode = Lang.t(kb.hold ? "gui.hold" : "gui.toggle");
                int mw = gg.textW(mode) + 8;
                if (kb.get() >= 0) {
                    boolean hv2 = ui.hover(x + w - cw - mw - 12, y + 1, mw, 13);
                    Ui.chip(gg, mode, x + w - cw - mw - 12, y + 1, mw, 13, false, hv2);
                    ui.region(x + w - cw - mw - 12, y + 1, mw, 13).press = (bt, mx, my) -> {
                        pushUndo(kb);
                        kb.hold = !kb.hold;
                        Config.markDirty();
                    };
                }
                Ui.Hit h = ui.region(x + w - cw - 6, y + 1, cw, 13);
                h.tip = tip;
                if (ui.hover(x, y, w, 15)) ui.tooltip = tip + "\n" + Lang.t("gui.key.hint");
                h.press = (bt, mx, my) -> {
                    commitEdit();
                    listening = kb;
                    listeningModule = m;
                };
            }));
        }
        return rows;
    }

    private Setting<?> flashSettingObj(Module m) {
        if (flashModule != m || flashSetting == null) return null;
        return m.find(flashSetting);
    }

    private static String fmt(Setting.Num n) {
        float v = n.get();
        String s = n.step >= 1 ? String.valueOf(Math.round(v)) : String.format("%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + n.suffix;
    }

    private void resetIcon(Gfx g, Setting<?> s, float x, float y) {
        if (s.isDefault()) return;
        boolean hv = ui.hover(x - 1, y - 1, 10, 10);
        g.text("↺", Math.round(x), Math.round(y), hv ? Theme.accent() : Theme.TEXT_FAINT, false);
        Ui.Hit h = ui.region(x - 2, y - 2, 12, 12);
        h.press = (b, mx, my) -> {
            pushUndo(s);
            s.reset();
        };
        if (hv) ui.tooltip = Lang.t("gui.reset");
    }

    private Row colorPicker(Setting.Color cl) {
        int rows = 2;
        int h = rows * 13 + 14 + 12 + 14 + 6;
        return new Row(h, (gg, x, y, w) -> {
            Draw.rect(gg, x + 4, y, w - 8, h - 2, 4, 0x66000000);
            float sw = (w - 16 - 5 * 3) / 6f;
            for (int i = 0; i < PALETTE.length; i++) {
                float cx = x + 8 + (i % 6) * (sw + 3), cy = y + 4 + (i / 6) * 13;
                Draw.rect(gg, cx, cy, sw, 10, 3, PALETTE[i]);
                if ((cl.get() & 0xFFFFFF) == (PALETTE[i] & 0xFFFFFF) && !cl.rainbow) Draw.ring(gg, cx - 1, cy - 1, sw + 2, 12, 3, 1, 0xFFFFFFFF);
                int col = PALETTE[i];
                ui.region(cx, cy, sw, 10).press = (b, mx, my) -> {
                    pushUndo(cl);
                    cl.setRainbow(false);
                    cl.set(Colors.withAlpha(col, Colors.a(cl.get())));
                };
            }
            float y2 = y + 4 + rows * 13;
            // hex
            String hex = editHex == cl ? editBuf + "|" : Colors.toHex(cl.get());
            Draw.rect(gg, x + 8, y2, 52, 11, 3, 0xFF1A1A2C);
            gg.text(hex, Math.round(x) + 11, Math.round(y2) + 2, editHex == cl ? Theme.accent() : Theme.TEXT, false);
            ui.region(x + 8, y2, 52, 11).press = (b, mx, my) -> {
                commitEdit();
                editHex = cl;
                editBuf.setLength(0);
            };
            // accent button
            boolean hv = ui.hover(x + 64, y2, w - 72, 11);
            Ui.chip(gg, Lang.t("gui.accent"), x + 64, y2, w - 72, 11, false, hv);
            ui.region(x + 64, y2, w - 72, 11).press = (b, mx, my) -> {
                pushUndo(cl);
                cl.setRainbow(false);
                cl.set(Colors.withAlpha(Theme.accent(), Colors.a(cl.get())));
            };
            // alpha
            float y3 = y2 + 14;
            gg.text(Lang.t("gui.alpha"), Math.round(x) + 9, Math.round(y3) - 1, Theme.TEXT_DIM, false);
            float ax = x + 8 + 30, aw = w - 16 - 30;
            Draw.rect(gg, ax, y3 + 2, aw, 4, 2, 0xFF2A2A44);
            float af = Colors.a(cl.get()) / 255f;
            Draw.rect(gg, ax, y3 + 2, Math.max(3, aw * af), 4, 2, Theme.accent());
            Draw.circle(gg, ax + aw * af, y3 + 4, 3.5f, 0xFFFFFFFF);
            Ui.Hit ah = ui.region(ax - 3, y3, aw + 6, 10);
            ah.press = (b, mx, my) -> {
                pushUndo(cl);
                cl.set(Colors.withAlpha(cl.get(), (int) Math.round(Math.max(0, Math.min(1, (mx - ax) / aw)) * 255)));
            };
            ah.drag = (mx, my, dx, dy) -> cl.set(Colors.withAlpha(cl.get(), (int) Math.round(Math.max(0, Math.min(1, (mx - ax) / aw)) * 255)));
            // rainbow
            float y4 = y3 + 11;
            Draw.rect(gg, x + 8, y4, 9, 9, 2, cl.rainbow ? Theme.accent() : 0xFF2A2A44);
            if (cl.rainbow) gg.text("✓", Math.round(x) + 9, Math.round(y4), 0xFF0D0D18, false);
            gg.text(Lang.t("gui.rainbow"), Math.round(x) + 21, Math.round(y4) + 1, Theme.TEXT, false);
            ui.region(x + 8, y4, w - 16, 10).press = (b, mx, my) -> {
                pushUndo(cl);
                cl.setRainbow(!cl.rainbow);
            };
        });
    }

    private void drawPanel(Gfx g, Module m, Col c, float x, float y, float visibleH) {
        List<Row> rows = layoutRows(g, m);
        float full = rows.stream().mapToInt(r -> r.h).sum();
        Draw.rect(g, x + 3, y, COL_W - 6, visibleH, 4, 0x44000000);
        float yy = y + 1;
        for (Row r : rows) {
            r.paint.draw(g, x, yy, COL_W);
            yy += r.h;
        }
        // the paint callbacks use absolute coordinates for hit regions; draw callbacks use the same
        // origin because the column is pushed with a translate, so convert back for drawing.
        if (full < 0) return;
    }

    // ------------------------------------------------------------------ bottom bar

    private void drawBottomBar(Gfx g, float vw, float vh) {
        float sw = 150, bh = 16, by = vh - 26;
        float totalW = sw + 4 * 80;
        float x = (vw - totalW) / 2f;
        Draw.rect(g, x - 6, by - 4, totalW + 12, bh + 8, 6, 0xB0101020);
        Draw.ring(g, x - 6, by - 4, totalW + 12, bh + 8, 6, 1, Theme.accent(80));
        // search
        Draw.rect(g, x, by, sw, bh, 4, 0xFF181830);
        if (searchFocus) Draw.ring(g, x, by, sw, bh, 4, 1, Theme.accent());
        String text = search.isEmpty() && !searchFocus ? Lang.t("gui.search") : search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
        g.text(text, Math.round(x) + 6, Math.round(by) + 4, search.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT, false);
        ui.region(x, by, sw, bh).press = (b, mx, my) -> {
            commitEdit();
            searchFocus = true;
            profileNameFocus = false;
        };
        float bx = x + sw + 4;
        float bw = 76;
        bottomButton(g, bx, by, bw, bh, Lang.t("gui.disableVisuals"), false, () -> {
            for (Module m : Config.modules()) if (m.visual && m.enabled()) m.setEnabled(false);
        });
        bx += bw + 4;
        StreamerModule st = Config.get(StreamerModule.class);
        bottomButton(g, bx, by, bw, bh, Lang.t("module.streamer"), st.enabled(), () -> st.toggle());
        bx += bw + 4;
        bottomButton(g, bx, by, bw, bh, Lang.t("gui.profiles"), profilesOpen, () -> profilesOpen = !profilesOpen);
        bx += bw + 4;
        bottomButton(g, bx, by, bw, bh, Lang.t("gui.hudEditor"), false, () -> {
            saveLayout();
            Platform.openView(new HudEditorView(null));
        });
    }

    private void bottomButton(Gfx g, float x, float y, float w, float h, String label, boolean active, Runnable r) {
        boolean hv = ui.hover(x, y, w, h);
        Ui.chip(g, label, x, y, w, h, active, hv);
        ui.region(x, y, w, h).press = (b, mx, my) -> {
            commitEdit();
            r.run();
        };
    }

    private void drawProfiles(Gfx g, float vw, float vh) {
        float w = 170, h = 150, x = (vw - w) / 2f + 90, y = vh - 26 - h - 10;
        Draw.rect(g, x, y, w, h, 6, 0xF0101020);
        Draw.ring(g, x, y, w, h, 6, 1, Theme.accent(110));
        g.text(Lang.t("gui.profiles"), Math.round(x) + 8, Math.round(y) + 6, Theme.accent(), false);
        ui.region(x, y, w, h); // swallow clicks
        float yy = y + 20;
        for (String p : Config.profiles()) {
            if (yy > y + h - 50) break;
            boolean cur = p.equals(Config.profile);
            boolean hv = ui.hover(x + 6, yy, w - 36, 12);
            Draw.rect(g, x + 6, yy, w - 36, 12, 3, cur ? Theme.accent(110) : hv ? 0x33FFFFFF : 0x22FFFFFF);
            g.text(p, Math.round(x) + 10, Math.round(yy) + 2, Theme.TEXT, false);
            ui.region(x + 6, yy, w - 36, 12).press = (b, mx, my) -> {
                Config.switchProfile(p);
                Notifications.info(Lang.t("gui.profileLoaded"));
            };
            boolean dv = ui.hover(x + w - 26, yy, 20, 12);
            g.text("✕", Math.round(x + w - 20), Math.round(yy) + 2, dv ? Theme.BAD : Theme.TEXT_FAINT, false);
            if (!p.equals("default")) ui.region(x + w - 26, yy, 20, 12).press = (b, mx, my) -> Config.deleteProfile(p);
            yy += 14;
        }
        float fy = y + h - 42;
        Draw.rect(g, x + 6, fy, w - 52, 12, 3, 0xFF181830);
        if (profileNameFocus) Draw.ring(g, x + 6, fy, w - 52, 12, 3, 1, Theme.accent());
        g.text(profileName.isEmpty() && !profileNameFocus ? Lang.t("gui.profileName") : profileName + (profileNameFocus ? "|" : ""), Math.round(x) + 10, Math.round(fy) + 2, profileName.isEmpty() ? Theme.TEXT_FAINT : Theme.TEXT, false);
        ui.region(x + 6, fy, w - 52, 12).press = (b, mx, my) -> {
            commitEdit();
            profileNameFocus = true;
            searchFocus = false;
        };
        bottomButton(g, x + w - 44, fy, 38, 12, Lang.t("common.save"), false, () -> {
            if (!profileName.isBlank()) {
                Config.saveProfileAs(profileName.trim());
                profileName = "";
                profileNameFocus = false;
                Notifications.info(Lang.t("gui.profileSaved"));
            }
        });
        bottomButton(g, x + 6, fy + 16, (w - 16) / 2f, 12, Lang.t("gui.copyCode"), false, () -> {
            Platform.setClipboard(Config.exportCode());
            Notifications.info(Lang.t("gui.codeCopied"));
        });
        bottomButton(g, x + 10 + (w - 16) / 2f, fy + 16, (w - 16) / 2f, 12, Lang.t("gui.pasteCode"), false, () -> {
            Notifications.info(Lang.t(Config.importCode(Platform.getClipboard()) ? "gui.codeLoaded" : "gui.codeBad"));
        });
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        double vx = x / uiScale, vy = y / uiScale;
        if (closing) return true;
        boolean wasEditing = editing();
        if (editNum != null || editHex != null) {
            // click elsewhere commits; a click on the same field restarts it via the region handler
            commitEdit();
        }
        searchFocus = false;
        profileNameFocus = false;
        if (listening != null) {
            // bind mouse button as key? only keyboard keys are supported; clicking cancels
            listening = null;
            listeningModule = null;
        }
        boolean handled = ui.click(vx, vy, button);
        if (!handled && profilesOpen) profilesOpen = false;
        if (!handled && !wasEditing && button == 0) {
            // click on empty space closes the menu like most client menus
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        Ui.Hit p = ui.pressedHit();
        boolean had = ui.release();
        if (dragging != null) {
            if (!dragMoved) {
                dragging.collapsed = !dragging.collapsed;
            }
            dragging = null;
            saveLayout();
        }
        return had || p != null;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return ui.dragged(x / uiScale, y / uiScale, dx / uiScale, dy / uiScale);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return ui.scrolled(x / uiScale, y / uiScale, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & 2) != 0;
        if (listening != null) {
            if (key == 256) {
                ((Setting.Key) listening).set(-1);
            } else {
                pushUndo(listening);
                ((Setting.Key) listening).set(key);
            }
            listening = null;
            listeningModule = null;
            return true;
        }
        if (key == 256) { // Esc
            if (editNum != null || editHex != null) {
                editNum = null;
                editHex = null;
            } else if (profilesOpen) {
                profilesOpen = false;
            } else if (!search.isEmpty() || searchFocus) {
                search = "";
                searchFocus = false;
            } else {
                startClose();
            }
            return true;
        }
        if (key == 257 || key == 335) { // Enter
            commitEdit();
            searchFocus = false;
            profileNameFocus = false;
            return true;
        }
        if (key == 259) { // Backspace
            if (editNum != null || editHex != null) {
                if (editBuf.length() > 0) editBuf.setLength(editBuf.length() - 1);
            } else if (profileNameFocus) {
                if (!profileName.isEmpty()) profileName = profileName.substring(0, profileName.length() - 1);
            } else if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
            return true;
        }
        if (ctrl && key == 90) { // Ctrl+Z
            doUndo();
            return true;
        }
        if (ctrl && key == 86 && searchFocus) { // paste into search
            search += Platform.getClipboard();
            return true;
        }
        // open the same key again closes the menu
        ClickGuiModule cg = Config.get(ClickGuiModule.class);
        if (key == cg.keybind.get() && !editing()) {
            startClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(int cp) {
        if (cp < 32 || cp == 127) return false;
        String s = new String(Character.toChars(cp));
        if (editNum != null) {
            if ("0123456789.,-".contains(s)) editBuf.append(s);
        } else if (editHex != null) {
            if ("0123456789abcdefABCDEF#".contains(s) && editBuf.length() < 9) editBuf.append(s);
        } else if (profileNameFocus) {
            if (profileName.length() < 24) profileName += s;
        } else {
            // typing anywhere starts a search, like in the reference menu
            searchFocus = true;
            if (search.length() < 32) search += s;
        }
        return true;
    }

    /** Called when another view (e.g. HUD editor) wants this menu to highlight a specific setting. */
    public void flash(Module m, String settingId) {
        flashModule = m;
        flashSetting = settingId;
        flashAt = System.currentTimeMillis();
        expanded.add(m);
    }
}
