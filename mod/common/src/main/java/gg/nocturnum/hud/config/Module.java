package gg.nocturnum.hud.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.util.Tween;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A feature of the mod. Modules appear as rows in ClickGUI, may draw a HUD element, and own their settings.
 * Rule of the mod: modules only show what the player already knows or sees, and never act for the player.
 */
public abstract class Module {
    public final String id;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    public final Setting.Key keybind = new Setting.Key("key");
    public final Map<String, Map<String, Object>> presets = new LinkedHashMap<>();

    private boolean enabled;
    private final boolean defaultEnabled;
    public boolean favorite;

    /** Visual module: affected by the "disable all visuals" button. */
    public boolean visual;

    // --- HUD element placement (percent of the window, so it survives resolution / GUI scale changes) ---
    public final boolean hud;
    public float hx, hy;
    public final float defHx, defHy;
    public final Setting.Num scale;

    /** Right / bottom anchored elements keep their right / bottom edge fixed when their size changes. */
    public boolean anchorRight, anchorBottom;

    /** Fade/slide of the module's own HUD element. */
    public final Tween shown = new Tween(0);

    protected Module(String id, Category category, boolean defaultEnabled, boolean hud, float hx, float hy) {
        this.id = id;
        this.category = category;
        this.enabled = defaultEnabled;
        this.defaultEnabled = defaultEnabled;
        this.hud = hud;
        this.hx = this.defHx = hx;
        this.hy = this.defHy = hy;
        keybind.owner = this;
        if (hud) {
            scale = add(new Setting.Num("scale", 1f, 0.5f, 2f, 0.05f, "x"));
        } else {
            scale = null;
        }
    }

    protected <S extends Setting<?>> S adv(S s) {
        s.advanced = true;
        return add(s);
    }

    protected <S extends Setting<?>> S add(S s) {
        s.owner = this;
        settings.add(s);
        return s;
    }

    public boolean enabled() { return enabled; }

    public void setEnabled(boolean v) {
        if (enabled == v) return;
        if (v && !canEnable()) return;
        enabled = v;
        Config.markDirty();
        if (v) onEnable(); else onDisable();
        gg.nocturnum.hud.ui.Notifications.moduleToggled(this, v);
    }

    /** Hook for gated modules (Anarchy category): return false to refuse enabling. */
    public boolean canEnable() { return true; }

    public void toggle() { setEnabled(!enabled); }
    public boolean enabledByDefault() { return defaultEnabled; }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}

    /** Full-screen overlay drawn every frame while enabled (crosshair, vignettes...). */
    public void overlay(Gfx g, float partial) {}

    /** Draw the HUD element at its current position; (0,0) is the element's own top-left. */
    public void render(Gfx g, float partial) {}

    /** True when there is nothing to show (plate is hidden in game, still visible in the layout editor). */
    public boolean empty() { return false; }

    /** Size in GUI pixels (before scale). Needed by the layout editor for hit boxes and snapping. */
    public int width(Gfx g) { return 60; }
    public int height(Gfx g) { return 12; }

    public String nameKey() { return "module." + id; }
    public String descKey() { return "module." + id + ".desc"; }

    public boolean isModified() {
        if (enabled != defaultEnabled || hx != defHx || hy != defHy) return true;
        for (Setting<?> s : settings) if (!s.isDefault()) return true;
        return false;
    }

    public void resetAll() {
        setEnabled(defaultEnabled);
        hx = defHx;
        hy = defHy;
        for (Setting<?> s : settings) s.reset();
        Config.markDirty();
    }

    public Setting<?> find(String sid) {
        for (Setting<?> s : settings) if (s.id.equals(sid)) return s;
        return null;
    }

    protected void preset(String name, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        presets.put(name, m);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void applyPreset(String name) {
        Map<String, Object> p = presets.get(name);
        if (p == null) return;
        for (var e : p.entrySet()) {
            Setting s = find(e.getKey());
            if (s == null) continue;
            Object v = e.getValue();
            if (s instanceof Setting.Num n && v instanceof Number num) n.setClamped(num.floatValue());
            else if (s instanceof Setting.Bool b && v instanceof Boolean bo) b.set(bo);
            else if (s instanceof Setting.Mode m && v instanceof Integer i) m.set(i);
            else if (s instanceof Setting.Color c && v instanceof Integer i) c.set(i);
        }
    }

    // ---------- persistence ----------

    public JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("favorite", favorite);
        if (hud) {
            o.addProperty("x", hx);
            o.addProperty("y", hy);
        }
        o.add("key", keybind.save());
        JsonObject s = new JsonObject();
        for (Setting<?> st : settings) s.add(st.id, st.save());
        o.add("settings", s);
        return o;
    }

    public void load(JsonObject o) {
        if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
        if (o.has("favorite")) favorite = o.get("favorite").getAsBoolean();
        if (hud && o.has("x")) hx = o.get("x").getAsFloat();
        if (hud && o.has("y")) hy = o.get("y").getAsFloat();
        try {
            if (o.has("key")) keybind.load(o.get("key"));
        } catch (RuntimeException ignored) {
        }
        if (o.has("settings")) {
            JsonObject s = o.getAsJsonObject("settings");
            for (Setting<?> st : settings) {
                JsonElement e = s.get(st.id);
                if (e != null) {
                    try {
                        st.load(e);
                    } catch (RuntimeException ignored) {
                        // keep default on malformed values
                    }
                }
            }
        }
    }
}
