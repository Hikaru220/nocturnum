package gg.nocturnum.hud.ui;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.module.NotificationsModule;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Lang;
import gg.nocturnum.hud.util.Tween;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Pop-up plates: slide in with overshoot, show a timer bar, leave sideways. */
public final class Notifications {
    private Notifications() {}

    private static final class Note {
        final String title, text;
        final int color;
        final long born = System.currentTimeMillis();
        final long life;
        final Tween in = new Tween(0);
        final Tween y = new Tween(0);
        boolean leaving;
        long leaveAt;

        Note(String title, String text, int color, long life) {
            this.title = title;
            this.text = text;
            this.color = color;
            this.life = life;
            in.to(1, 250, Easing.OUT_BACK);
        }
    }

    private static final List<Note> NOTES = new ArrayList<>();

    public static void push(String title, String text, int color) {
        NotificationsModule m = Config.get(NotificationsModule.class);
        if (!m.enabled()) return;
        synchronized (NOTES) {
            if (NOTES.size() > 6) NOTES.remove(0);
            NOTES.add(new Note(title, text, color, (long) (m.duration.get() * 1000)));
        }
        if (m.sound.get()) gg.nocturnum.hud.module.SoundModules.UiSounds.notification();
    }

    public static void moduleToggled(Module mod, boolean on) {
        if (mod instanceof NotificationsModule || mod.id.equals("clickgui")) return;
        NotificationsModule m = Config.get(NotificationsModule.class);
        if (!m.enabled() || !m.onToggle.get()) return;
        push(Lang.t(mod.nameKey()), Lang.t(on ? "notify.enabled" : "notify.disabled"), on ? Theme.OK : Theme.BAD);
    }

    public static void info(String text) { push("Nocturnum", text, Theme.accent()); }

    private static final int W = 150, H = 30, GAP = 4;

    /** Draws at origin (0,0) = the anchor corner of the notification stack. */
    public static void render(Gfx g, boolean preview) {
        long now = System.currentTimeMillis();
        List<Note> list;
        synchronized (NOTES) {
            if (preview && NOTES.isEmpty()) {
                // layout editor shows one sample so the element is visible
                Note n = new Note("Nocturnum", Lang.t("notify.sample"), Theme.accent(), 100000);
                n.in.set(1);
                list = List.of(n);
            } else {
                list = new ArrayList<>(NOTES);
            }
        }
        int i = 0;
        for (Note n : list) {
            if (!n.leaving && now - n.born > n.life) {
                n.leaving = true;
                n.leaveAt = now;
                n.in.to(0, 200, Easing.IN_QUAD);
            }
            float t = n.in.get();
            if (n.leaving && t <= 0.001f) continue;
            float target = i * (H + GAP);
            n.y.to(target, 200, Easing.OUT_CUBIC);
            float yy = n.y.get();
            float slide = (1 - t) * (W + 12);
            Gfx gg = g;
            float alpha = Math.max(0, Math.min(1, t * 1.2f));
            gg.alpha = alpha;
            float x = slide;
            Theme.plate(gg, x, yy, W, H, true);
            Draw.rect(gg, x + 4, yy + 5, 2, H - 10, 1, n.color);
            gg.text(n.title, Math.round(x + 12), Math.round(yy + 5), Theme.TEXT, true);
            gg.text(n.text, Math.round(x + 12), Math.round(yy + 5 + gg.fontH() + 2), Theme.TEXT_DIM, false);
            float k = Math.max(0, Math.min(1, 1f - (now - n.born) / (float) n.life));
            Draw.rect(gg, x + 6, yy + H - 4, (W - 12) * k, 1.5f, 0.7f, Colors.withAlpha(n.color, 200));
            gg.alpha = 1f;
            i++;
        }
        synchronized (NOTES) {
            Iterator<Note> it = NOTES.iterator();
            while (it.hasNext()) {
                Note n = it.next();
                if (n.leaving && now - n.leaveAt > 400) it.remove();
            }
        }
    }

    public static int width() { return W; }
    public static int height() { return H; }
}
