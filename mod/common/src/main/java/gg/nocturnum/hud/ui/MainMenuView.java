package gg.nocturnum.hud.ui;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Own title screen: night sky with parallax, vertical button list, player card and quick connect. */
public final class MainMenuView extends View {
    private final Ui ui = new Ui();
    private final long born = System.currentTimeMillis();
    private final float[][] stars = new float[170][4]; // x, y, size, phase
    private final List<Tween> hovers = new ArrayList<>();
    private float mx, my;

    public MainMenuView() {
        Random r = new Random(7);
        for (float[] s : stars) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat();
            s[2] = 0.5f + r.nextFloat() * 1.3f;
            s[3] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < 12; i++) hovers.add(new Tween(0));
    }

    @Override public boolean escapeCloses() { return false; }
    @Override public boolean dimBackground() { return false; }

    private float enter() { return Easing.OUT_CUBIC.apply(Math.min(1f, (System.currentTimeMillis() - born) / 600f)); }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, float pt) {
        mx = mouseX;
        my = mouseY;
        float k = enter();
        // sky
        g.gradientV(0, 0, width, height, 0xFF070711, 0xFF151232);
        float px = (mouseX / (float) width - 0.5f), py = (mouseY / (float) height - 0.5f);
        long t = System.currentTimeMillis();
        for (float[] s : stars) {
            float depth = s[2];
            float x = s[0] * width + px * depth * 14, y = s[1] * height + py * depth * 9;
            float tw = 0.55f + 0.45f * (float) Math.sin(t / 900.0 + s[3]);
            int a = Math.round(255 * tw * Math.min(1f, depth / 1.2f) * 0.8f);
            int c = Colors.withAlpha(0xFFFFFF, a);
            g.fill(Math.round(x), Math.round(y), Math.round(x + (depth > 1.2f ? 2 : 1)), Math.round(y + (depth > 1.2f ? 2 : 1)), c);
        }
        // moon
        float moonX = width * 0.74f + px * -26, moonY = height * 0.3f + py * -16;
        float breathe = 0.7f + 0.3f * (float) Math.sin(t / 1400.0);
        for (int i = 8; i >= 1; i--) Draw.circle(g, moonX, moonY, 38 + i * 9, Colors.withAlpha(Theme.accent(), Math.round(10 * breathe * (9 - i) / 8f + 1)));
        Draw.crescent(g, moonX, moonY, 46, Theme.accent());
        Draw.circle(g, moonX + 36, moonY - 36, 3, 0xFFFFFFFF);
        Draw.circle(g, moonX + 60, moonY + 8, 2, 0xFFFFFFFF);
        // ground silhouette
        g.gradientV(0, Math.round(height * 0.82f), width, height, 0x00000000, 0xCC05050C);

        ui.begin(mouseX, mouseY);
        g.alpha = k;
        // logo + buttons (left column)
        float bx = 44 - (1 - k) * 30, by = height * 0.22f;
        Draw.crescent(g, bx + 10, by + 10, 10, Theme.accent());
        g.textBold("NOCTURNUM", Math.round(bx + 28), Math.round(by + 4), 0xFFFFFFFF, true);
        g.text(Lang.t("menu.tagline"), Math.round(bx), Math.round(by + 30), Theme.TEXT_DIM, false);
        String[] labels = {Lang.t("menu.singleplayer"), Lang.t("menu.multiplayer"), "Nocturnum", Lang.t("menu.options"), Lang.t("menu.quit")};
        Runnable[] actions = {Platform::openSingleplayer, Platform::openMultiplayer, () -> Platform.openView(new ClickGuiView()), Platform::openOptions, Platform::quit};
        float y = by + 56;
        for (int i = 0; i < labels.length; i++) {
            float w = 150, h = 22;
            boolean hv = ui.hover(bx, y, w, h);
            Tween th = hovers.get(i);
            th.to(hv ? 1 : 0, 120, Easing.OUT_QUAD);
            float f = th.get();
            float xo = f * 6;
            int bg = Colors.lerp(0xAA14142A, Theme.accent(200), f);
            Draw.rect(g, bx + xo, y, w, h, 6, bg);
            Draw.ring(g, bx + xo, y, w, h, 6, 1, Theme.accent(Math.round(60 + 160 * f)));
            g.text(labels[i], Math.round(bx + xo + 12), Math.round(y + (h - g.fontH()) / 2f) + 1, Colors.lerp(Theme.TEXT, 0xFF0D0D18, f), false);
            Runnable a = actions[i];
            ui.region(bx, y, w, h).press = (b, px2, py2) -> {
                if (b == 0) a.run();
            };
            y += h + 6;
        }
        // player card
        float cw = 150, ch = 58, cx = width - cw - 24, cy = 20;
        Theme.plate(g, cx, cy, cw, ch, true);
        Draw.circle(g, cx + 22, cy + 29, 14, Theme.accent(70));
        g.text(Game.mc().getUser().getName().substring(0, 1).toUpperCase(), Math.round(cx + 18), Math.round(cy + 24), Theme.TEXT, true);
        g.textBold(Game.playerName(), Math.round(cx + 44), Math.round(cy + 14), Theme.TEXT, true);
        g.text(Lang.t("menu.account"), Math.round(cx + 44), Math.round(cy + 30), Theme.TEXT_DIM, false);
        // quick connect
        List<String[]> servers = Platform.recentServers(3);
        if (!servers.isEmpty()) {
            float qy = height - 70;
            g.text(Lang.t("menu.quick"), 44, Math.round(qy - 12), Theme.TEXT_DIM, false);
            float qx = 44;
            for (String[] s : servers) {
                float w = 140, h = 28;
                boolean hv = ui.hover(qx, qy, w, h);
                Draw.rect(g, qx, qy, w, h, 6, hv ? 0xCC20203C : 0xAA14142A);
                Draw.ring(g, qx, qy, w, h, 6, 1, Theme.accent(hv ? 200 : 70));
                g.text(s[0], Math.round(qx + 8), Math.round(qy + 5), Theme.TEXT, true);
                g.text(s[1], Math.round(qx + 8), Math.round(qy + 16), Theme.TEXT_FAINT, false);
                String ip = s[1], name = s[0];
                ui.region(qx, qy, w, h).press = (b, px2, py2) -> Platform.joinServer(name, ip);
                qx += w + 8;
            }
        }
        String ver = "Nocturnum HUD · Minecraft " + Platform.gameVersion();
        g.text(ver, width - g.textW(ver) - 8, height - 12, Theme.TEXT_FAINT, false);
        g.alpha = 1f;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) { return ui.click(x, y, button); }

    @Override
    public boolean mouseReleased(double x, double y, int button) { return ui.release(); }
}
