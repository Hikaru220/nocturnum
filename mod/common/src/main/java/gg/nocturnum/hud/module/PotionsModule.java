package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Easing;
import gg.nocturnum.hud.util.Tween;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Active effects with level and time left; a new effect "flashes", the last 5 seconds blink. */
public final class PotionsModule extends Module {
    public final Setting.Bool icons = add(new Setting.Bool("icons", true));
    public final Setting.Bool colorByType = add(new Setting.Bool("colorByType", true));

    private final Map<Holder<MobEffect>, Tween> flash = new HashMap<>();

    public PotionsModule() {
        super("potions", Category.HUD, true, true, 0.01f, 0.62f);
    }

    private List<MobEffectInstance> effects() {
        if (!Game.inWorld()) return List.of();
        return new ArrayList<>(Game.player().getActiveEffects());
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static String time(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "∞";
        int s = e.getDuration() / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private String label(MobEffectInstance e) {
        return e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
    }

    @Override public boolean empty() { return effects().isEmpty(); }

    @Override
    public int width(Gfx g) {
        int w = 100;
        for (MobEffectInstance e : effects()) w = Math.max(w, g.textW(label(e)) + g.textW(time(e)) + (icons.get() ? 40 : 22));
        return w;
    }

    @Override public int height(Gfx g) { return Math.max(1, effects().size()) * 20 + 6; }

    @Override
    public void render(Gfx g, float pt) {
        List<MobEffectInstance> list = effects();
        int w = width(g), h = height(g);
        Theme.plate(g, 0, 0, w, h);
        int y = 3;
        if (list.isEmpty()) g.text("—", 8, 8, Theme.TEXT_FAINT, false);
        for (MobEffectInstance e : list) {
            Tween f = flash.computeIfAbsent(e.getEffect(), k -> {
                Tween t = new Tween(1);
                t.to(0, 500, Easing.OUT_CUBIC);
                return t;
            });
            float fl = f.get();
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < 100 && (System.currentTimeMillis() / 250) % 2 == 0;
            int tint = colorByType.get() ? (0xFF000000 | e.getEffect().value().getColor()) : Theme.accent();
            if (fl > 0.01f) Draw.rect(g, 2, y, w - 4, 18, 4, Colors.withAlpha(tint, Math.round(110 * fl)));
            int x = 8;
            if (icons.get()) {
                Identifier sprite = e.getEffect().unwrapKey().map(k -> Identifier.fromNamespaceAndPath(k.identifier().getNamespace(), "mob_effect/" + k.identifier().getPath())).orElse(null);
                if (sprite != null) g.sprite(sprite, x, y, 16, 16);
                x += 20;
            } else {
                Draw.rect(g, x, y + 3, 3, 12, 1.5f, tint);
                x += 8;
            }
            int col = ending ? Theme.BAD : Theme.TEXT;
            g.text(label(e), x, y + 5, col, true);
            String t = time(e);
            g.text(t, w - 6 - g.textW(t), y + 5, ending ? Theme.BAD : Theme.TEXT_DIM, false);
            y += 20;
        }
        flash.keySet().removeIf(k -> list.stream().noneMatch(e -> e.getEffect().equals(k)));
    }
}
