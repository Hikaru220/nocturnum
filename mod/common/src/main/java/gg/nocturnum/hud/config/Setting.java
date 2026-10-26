package gg.nocturnum.hud.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import gg.nocturnum.hud.util.Colors;

import java.util.List;

/** One configurable value of a module. Every setting has a tooltip (lang key) and can be reset. */
public abstract class Setting<T> {
    public final String id;
    public boolean advanced;
    protected T value;
    protected final T def;
    public Module owner;

    protected Setting(String id, T def) {
        this.id = id;
        this.def = def;
        this.value = def;
    }

    public T get() { return value; }
    public T defaultValue() { return def; }
    public boolean isDefault() { return value.equals(def); }

    public void set(T v) {
        T old = value;
        value = v;
        if (owner != null && !old.equals(v)) Config.markDirty();
    }

    public void reset() { set(def); }

    public Setting<T> advanced() { this.advanced = true; return this; }

    public String nameKey() { return "module." + owner.id + "." + id; }
    public String descKey() { return nameKey() + ".desc"; }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);

    // ---------- kinds ----------

    public static final class Bool extends Setting<Boolean> {
        public Bool(String id, boolean def) { super(id, def); }
        public JsonElement save() { return new JsonPrimitive(value); }
        public void load(JsonElement e) { value = e.getAsBoolean(); }
        public void toggle() { set(!value); }
    }

    public static final class Num extends Setting<Float> {
        public final float min, max, step;
        public final String suffix;

        public Num(String id, float def, float min, float max, float step, String suffix) {
            super(id, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix;
        }

        public Num(String id, float def, float min, float max, float step) { this(id, def, min, max, step, ""); }

        public void setClamped(float v) {
            if (step > 0) v = Math.round(v / step) * step;
            set(Math.max(min, Math.min(max, v)));
        }

        public int asInt() { return Math.round(value); }
        public float get01() { return (value - min) / (max - min); }
        public JsonElement save() { return new JsonPrimitive(value); }
        public void load(JsonElement e) { value = Math.max(min, Math.min(max, e.getAsFloat())); }
    }

    public static final class Mode extends Setting<Integer> {
        public final List<String> options;

        public Mode(String id, int def, String... options) {
            super(id, def);
            this.options = List.of(options);
        }

        public String current() { return options.get(value); }
        public boolean is(String o) { return options.get(value).equals(o); }
        public void next() { set((value + 1) % options.size()); }
        public String optionKey(int i) { return nameKey() + "." + options.get(i); }
        public JsonElement save() { return new JsonPrimitive(options.get(value)); }

        public void load(JsonElement e) {
            int i = options.indexOf(e.getAsString());
            if (i >= 0) value = i;
        }
    }

    /** ARGB colour, optionally animated rainbow. */
    public static final class Color extends Setting<Integer> {
        public boolean rainbow;
        private final boolean defRainbow;

        public Color(String id, int def) { this(id, def, false); }

        public Color(String id, int def, boolean rainbow) {
            super(id, def);
            this.rainbow = rainbow;
            this.defRainbow = rainbow;
        }

        /** Current colour (rainbow resolved). */
        public int argb() { return rainbow ? Colors.withAlpha(Colors.rainbow(0), Colors.a(value)) : value; }

        public int argb(float offset) { return rainbow ? Colors.withAlpha(Colors.rainbow(offset), Colors.a(value)) : value; }

        public void setRainbow(boolean r) { if (rainbow != r) { rainbow = r; Config.markDirty(); } }

        @Override public boolean isDefault() { return value.equals(def) && rainbow == defRainbow; }
        @Override public void reset() { set(def); setRainbow(defRainbow); }

        public JsonElement save() {
            var o = new com.google.gson.JsonObject();
            o.addProperty("color", Colors.toHex(value) + String.format("%02X", Colors.a(value)));
            o.addProperty("rainbow", rainbow);
            return o;
        }

        public void load(JsonElement e) {
            var o = e.getAsJsonObject();
            String c = o.get("color").getAsString();
            // stored as #RRGGBBAA
            int rgb = Colors.parseHex(c.substring(0, 7), 0xFFFFFFFF);
            int a = c.length() >= 9 ? Integer.parseInt(c.substring(7, 9), 16) : 255;
            value = Colors.withAlpha(rgb, a);
            rainbow = o.has("rainbow") && o.get("rainbow").getAsBoolean();
        }
    }

    /** Key binding; -1 = unbound. */
    public static final class Key extends Setting<Integer> {
        public boolean hold;

        public Key(String id) { super(id, -1); }

        public JsonElement save() {
            var o = new com.google.gson.JsonObject();
            o.addProperty("key", value);
            o.addProperty("hold", hold);
            return o;
        }

        public void load(JsonElement e) {
            var o = e.getAsJsonObject();
            value = o.get("key").getAsInt();
            hold = o.has("hold") && o.get("hold").getAsBoolean();
        }
    }
}
