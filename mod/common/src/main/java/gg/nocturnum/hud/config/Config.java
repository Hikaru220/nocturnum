package gg.nocturnum.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.nocturnum.hud.platform.Platform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Registry of modules + persistence. One JSON file per profile in config/nocturnum/ — easy to copy to another PC. */
public final class Config {
    private Config() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Module> MODULES = new ArrayList<>();
    private static boolean dirty;
    private static long lastSave;
    public static String profile = "default";

    public static List<Module> modules() { return MODULES; }

    public static void register(Module m) { MODULES.add(m); }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> c) {
        for (Module m : MODULES) if (c.isInstance(m)) return (T) m;
        throw new IllegalStateException("module not registered: " + c);
    }

    public static Module byId(String id) {
        for (Module m : MODULES) if (m.id.equals(id)) return m;
        return null;
    }

    public static void markDirty() { dirty = true; }

    public static Path dir() {
        Path d = Platform.configDir().resolve("nocturnum");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {
        }
        return d;
    }

    private static Path file(String name) { return dir().resolve(safe(name) + ".json"); }

    private static String safe(String n) { return n.replaceAll("[^\\p{L}\\p{N}_\\- ]", "_"); }

    public static List<String> profiles() {
        List<String> out = new ArrayList<>();
        try (var s = Files.list(dir())) {
            s.filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(p -> out.add(p.getFileName().toString().replaceAll("\\.json$", "")));
        } catch (IOException ignored) {
        }
        if (!out.contains(profile)) out.add(profile);
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public static JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject mods = new JsonObject();
        for (Module m : MODULES) mods.add(m.id, m.save());
        root.add("modules", mods);
        return root;
    }

    public static void fromJson(JsonObject root) {
        if (!root.has("modules")) return;
        JsonObject mods = root.getAsJsonObject("modules");
        for (Module m : MODULES) {
            if (mods.has(m.id) && mods.get(m.id).isJsonObject()) m.load(mods.getAsJsonObject(m.id));
        }
    }

    public static void load() {
        try {
            Path a = dir().resolve("active.txt");
            if (Files.exists(a)) profile = Files.readString(a).trim();
            if (profile.isEmpty()) profile = "default";
            Path f = file(profile);
            if (Files.exists(f)) fromJson(JsonParser.parseString(Files.readString(f)).getAsJsonObject());
        } catch (Exception e) {
            Platform.log("Config load failed, using defaults: " + e);
        }
        dirty = false;
    }

    public static void save() {
        try {
            Files.writeString(file(profile), GSON.toJson(toJson()));
            Files.writeString(dir().resolve("active.txt"), profile);
        } catch (IOException e) {
            Platform.log("Config save failed: " + e);
        }
        dirty = false;
        lastSave = System.currentTimeMillis();
    }

    /** Called every client tick: writes at most once per two seconds. */
    public static void tick() {
        if (dirty && System.currentTimeMillis() - lastSave > 2000) save();
    }

    // ---------- profiles ----------

    public static void saveProfileAs(String name) {
        profile = safe(name);
        save();
    }

    public static boolean switchProfile(String name) {
        Path f = file(name);
        if (!Files.exists(f)) return false;
        try {
            resetAll(false);
            fromJson(JsonParser.parseString(Files.readString(f)).getAsJsonObject());
            profile = safe(name);
            save();
            return true;
        } catch (Exception e) {
            Platform.log("Profile load failed: " + e);
            return false;
        }
    }

    public static void deleteProfile(String name) {
        try {
            Files.deleteIfExists(file(name));
        } catch (IOException ignored) {
        }
    }

    public static void resetAll(boolean keepFavorites) {
        for (Module m : MODULES) {
            boolean fav = m.favorite;
            m.resetAll();
            if (keepFavorites) m.favorite = fav;
        }
    }

    /** Share code: gzip + base64 of the profile JSON. */
    public static String exportCode() {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
                gz.write(toJson().toString().getBytes(StandardCharsets.UTF_8));
            }
            return "NCT1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bo.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    public static boolean importCode(String code) {
        try {
            String c = code.trim();
            if (!c.startsWith("NCT1:")) return false;
            byte[] raw = Base64.getUrlDecoder().decode(c.substring(5));
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                String json = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
                if (json.length() > 1_000_000) return false;
                resetAll(false);
                fromJson(JsonParser.parseString(json).getAsJsonObject());
            }
            markDirty();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
