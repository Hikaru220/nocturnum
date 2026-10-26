package gg.nocturnum.hud.config;

public enum Category {
    HUD("hud"),
    VISUALS("visuals"),
    CAMERA("camera"),
    SOUNDS("sounds"),
    COMFORT("comfort"),
    OTHER("other");

    public final String id;

    Category(String id) { this.id = id; }

    public String key() { return "category." + id; }
}
