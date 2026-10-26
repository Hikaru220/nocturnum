package gg.nocturnum.hud.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.platform.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-server profiles for the Anarchy category: every feature is off on a new server, the player turns on what
 * that server allows and the mod remembers it. Servers on the blacklist (downloaded by the launcher) lock the category.
 */
public final class AnarchyProfiles {
    private AnarchyProfiles() {}

    private static String current = "";
    private static boolean loading;
    private static List<String> blacklist = List.of();
    private static boolean blacklistLoaded;

    public static String serverKey() {
        if (!Game.inWorld()) return "";
        if (Game.singleplayer()) return "local";
        String a = Game.serverAddress().toLowerCase().trim();
        int c = a.indexOf(':');
        return c > 0 ? a.substring(0, c) : a;
    }

    private static Path profilesFile() { return Config.dir().resolve("servers.json"); }

    private static void loadBlacklist() {
        if (blacklistLoaded) return;
        blacklistLoaded = true;
        List<String> l = new ArrayList<>();
        try {
            String prop = System.getProperty("nocturnum.blacklist");
            Path f = prop != null ? Path.of(prop) : Config.dir().resolve("blacklist.json");
            if (Files.exists(f)) {
                JsonArray a = JsonParser.parseString(Files.readString(f)).getAsJsonObject().getAsJsonArray("servers");
                for (var e : a) l.add(e.getAsString().toLowerCase());
            }
        } catch (Exception e) {
            Platform.log("blacklist read failed: " + e);
        }
        blacklist = l;
    }

    /** True on servers where the whole category is forbidden. Supports "*.example.com" wildcards. */
    public static boolean blocked() {
        loadBlacklist();
        String key = serverKey();
        if (key.isEmpty() || key.equals("local")) return false;
        for (String p : blacklist) {
            if (p.startsWith("*.") ? (key.endsWith(p.substring(1)) || key.equals(p.substring(2))) : key.equals(p)) return true;
        }
        return false;
    }

    private static JsonObject read() {
        try {
            if (Files.exists(profilesFile())) return JsonParser.parseString(Files.readString(profilesFile())).getAsJsonObject();
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static void write(JsonObject o) {
        try {
            Files.writeString(profilesFile(), o.toString());
        } catch (Exception ignored) {
        }
    }

    public static boolean warned() { return read().has("_warned"); }

    public static void markWarned() {
        JsonObject o = read();
        o.addProperty("_warned", true);
        write(o);
    }

    public static void record(Module m, boolean on) {
        if (loading || current.isEmpty()) return;
        JsonObject o = read();
        JsonArray arr = o.has(current) ? o.getAsJsonArray(current) : new JsonArray();
        Set<String> ids = new HashSet<>();
        for (var e : arr) ids.add(e.getAsString());
        if (on) ids.add(m.id); else ids.remove(m.id);
        JsonArray out = new JsonArray();
        ids.forEach(out::add);
        o.add(current, out);
        write(o);
    }

    /** Called every tick: when the server changes, switch to its saved set (new servers start with everything off). */
    public static void tick() {
        String key = serverKey();
        if (key.equals(current)) return;
        current = key;
        loading = true;
        try {
            Set<String> saved = new HashSet<>();
            JsonObject o = read();
            if (!key.isEmpty() && o.has(key)) for (var e : o.getAsJsonArray(key)) saved.add(e.getAsString());
            boolean block = blocked();
            for (Module m : Config.modules()) {
                if (m.category != Category.ANARCHY) continue;
                m.setEnabled(!block && saved.contains(m.id));
            }
        } finally {
            loading = false;
        }
    }
}
