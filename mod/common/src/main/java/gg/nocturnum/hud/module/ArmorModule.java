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
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Worn armour with a smoothly shrinking durability bar; blinks under 15%. */
public final class ArmorModule extends Module {
    public final Setting.Mode layout = add(new Setting.Mode("layout", 0, "vertical", "horizontal"));
    public final Setting.Mode format = add(new Setting.Mode("format", 0, "percent", "numbers"));
    public final Setting.Bool warn = add(new Setting.Bool("warn", true));

    private final Tween[] bars = {new Tween(1), new Tween(1), new Tween(1), new Tween(1)};

    public ArmorModule() {
        super("armor", Category.HUD, true, true, 0.01f, 0.42f);
    }

    @Override public boolean empty() { return Game.armor().stream().allMatch(ItemStack::isEmpty); }

    private boolean vertical() { return layout.is("vertical"); }

    @Override public int width(Gfx g) { return vertical() ? 70 : 4 * 36 + 8; }
    @Override public int height(Gfx g) { return vertical() ? 4 * 24 + 6 : 44; }

    @Override
    public void render(Gfx g, float pt) {
        int w = width(g), h = height(g);
        Theme.plate(g, 0, 0, w, h);
        List<ItemStack> armor = Game.armor();
        for (int i = 0; i < 4; i++) {
            ItemStack st = i < armor.size() ? armor.get(i) : ItemStack.EMPTY;
            int x = vertical() ? 6 : 6 + i * 36;
            int y = vertical() ? 4 + i * 24 : 4;
            if (st.isEmpty()) {
                Draw.rect(g, x, y, 16, 16, 3, Colors.withAlpha(0xFFFFFF, 18));
                continue;
            }
            g.item(st, x, y);
            float frac = st.isDamageableItem() ? 1f - st.getDamageValue() / (float) st.getMaxDamage() : 1f;
            bars[i].to(frac, 400, Easing.OUT_EXPO);
            float f = bars[i].get();
            boolean low = warn.get() && st.isDamageableItem() && f < 0.15f;
            boolean blink = low && (System.currentTimeMillis() / 300) % 2 == 0;
            int col = f > 0.5f ? Theme.OK : f > 0.25f ? Theme.WARN : Theme.BAD;
            int bx = vertical() ? x + 20 : x;
            int by = vertical() ? y + 11 : y + 20;
            int bw = vertical() ? 40 : 28;
            Draw.rect(g, bx, by, bw, 3, 1.5f, Colors.withAlpha(0xFFFFFF, 30));
            Draw.rect(g, bx, by, Math.max(2, bw * f), 3, 1.5f, blink ? Theme.BAD : col);
            if (st.isDamageableItem()) {
                String t = format.is("percent") ? Math.round(f * 100) + "%" : (st.getMaxDamage() - st.getDamageValue()) + "";
                if (vertical()) g.text(t, bx, y + 1, blink ? Theme.BAD : Theme.TEXT, true);
                else g.text(t, bx, by + 5, blink ? Theme.BAD : Theme.TEXT_DIM, false);
            }
        }
    }
}
