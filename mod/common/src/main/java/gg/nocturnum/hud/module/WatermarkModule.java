package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Tween;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Corner plate: logo, nickname, FPS, ping, time. The logo "breathes" with a soft glow. */
public final class WatermarkModule extends Module {
    public final Setting.Bool showName = add(new Setting.Bool("showName", true));
    public final Setting.Bool showFps = add(new Setting.Bool("showFps", true));
    public final Setting.Bool showPing = add(new Setting.Bool("showPing", true));
    public final Setting.Bool showTime = add(new Setting.Bool("showTime", true));
    public final Setting.Mode style = add(new Setting.Mode("style", 0, "plate", "text"));

    private final Tween fps = new Tween(0), ping = new Tween(0);

    public WatermarkModule() {
        super("watermark", Category.HUD, true, true, 0.01f, 0.015f);
    }

    private List<String> parts() {
        List<String> p = new ArrayList<>();
        p.add("NOCTURNUM");
        if (showName.get()) p.add(Game.playerName());
        if (showFps.get()) {
            fps.to(Game.fps(), 150, gg.nocturnum.hud.util.Easing.LINEAR);
            p.add(Math.round(fps.get()) + " FPS");
        }
        if (showPing.get() && Game.ping() >= 0) {
            ping.to(Game.ping(), 150, gg.nocturnum.hud.util.Easing.LINEAR);
            p.add(Math.round(ping.get()) + " ms");
        }
        if (showTime.get()) p.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        return p;
    }

    private static final int PAD = 6, LOGO = 14, SEP = 9;

    @Override
    public int width(Gfx g) {
        int w = PAD + LOGO + 6;
        List<String> p = parts();
        for (int i = 0; i < p.size(); i++) w += g.textW(p.get(i)) + (i > 0 ? SEP : 0);
        return w + PAD;
    }

    @Override public int height(Gfx g) { return 20; }

    @Override
    public void render(Gfx g, float pt) {
        int w = width(g), h = height(g);
        boolean plate = style.is("plate");
        if (plate) Theme.plate(g, 0, 0, w, h, true);
        float breathe = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 700.0);
        int acc = Theme.accent();
        if (!Theme.lowEnd()) Draw.glow(g, PAD + 1, 3, LOGO - 2, LOGO - 2, 6, acc, 0.5f * breathe);
        Draw.crescent(g, PAD + LOGO / 2f, h / 2f, 6f, acc);
        Draw.circle(g, PAD + LOGO - 1.5f, 5.5f, 1f, 0xFFFFFFFF);
        int x = PAD + LOGO + 6;
        int ty = (h - g.fontH()) / 2 + 1;
        List<String> p = parts();
        for (int i = 0; i < p.size(); i++) {
            if (i > 0) {
                g.fill(x - SEP / 2, 5, x - SEP / 2 + 1, h - 5, Colors.withAlpha(0xFFFFFF, 40));
            }
            int col = i == 0 ? Theme.gradient(0f) : Theme.TEXT;
            if (i == 0) g.textBold(p.get(i), x, ty, col, true); else g.text(p.get(i), x, ty, col, true);
            x += (i == 0 ? g.textBoldW(p.get(i)) : g.textW(p.get(i))) + SEP;
        }
    }
}
