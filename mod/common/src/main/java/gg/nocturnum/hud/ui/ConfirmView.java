package gg.nocturnum.hud.ui;

import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Lang;

/** Small yes/no dialog used by leave protection. Esc = stay. */
public final class ConfirmView extends View {
    private final Ui ui = new Ui();
    private final String title, text, yes, no;
    private final Runnable onYes;
    private final Runnable onNo;

    public ConfirmView(String title, String text, String yes, String no, Runnable onYes, Runnable onNo) {
        this.title = title;
        this.text = text;
        this.yes = yes;
        this.no = no;
        this.onYes = onYes;
        this.onNo = onNo;
    }

    @Override public boolean escapeCloses() { return false; }
    @Override public boolean blurBackground() { return true; }

    @Override
    public void render(Gfx g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88050510);
        ui.begin(mx, my);
        float w = 220, h = 84, x = (width - w) / 2f, y = (height - h) / 2f;
        Theme.plate(g, x, y, w, h, true);
        g.textBold(title, Math.round(x + 12), Math.round(y + 10), Theme.TEXT, true);
        g.text(text, Math.round(x + 12), Math.round(y + 28), Theme.TEXT_DIM, false);
        float bw = (w - 36) / 2f, by = y + h - 28;
        Ui.chip(g, no, x + 12, by, bw, 18, true, ui.hover(x + 12, by, bw, 18));
        ui.region(x + 12, by, bw, 18).press = (b, px, py) -> {
            Platform.closeScreen();
            if (onNo != null) onNo.run();
        };
        Ui.chip(g, yes, x + 24 + bw, by, bw, 18, false, ui.hover(x + 24 + bw, by, bw, 18));
        ui.region(x + 24 + bw, by, bw, 18).press = (b, px, py) -> onYes.run();
        Draw.line(g, x, y, x, y, 1, 0); // keep Draw referenced for future styling
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) { return ui.click(x, y, button); }

    @Override
    public boolean mouseReleased(double x, double y, int button) { return ui.release(); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            Platform.closeScreen();
            if (onNo != null) onNo.run();
            return true;
        }
        return false;
    }

    public static String stay() { return Lang.t("leave.stay"); }
}
