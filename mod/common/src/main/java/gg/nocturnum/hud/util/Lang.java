package gg.nocturnum.hud.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.nocturnum.hud.platform.Platform;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Mod UI text. Russian by default (ТЗ), English as the fallback; follows the game language. */
public final class Lang {
    private Lang() {}

    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();
    private static boolean loaded;

    /** "auto" follows the game language, otherwise "ru" / "en". */
    public static String forced = "auto";

    private static void load() {
        if (loaded) return;
        loaded = true;
        read("ru", RU);
        read("en", EN);
    }

    private static void read(String lang, Map<String, String> into) {
        try (InputStream in = Lang.class.getResourceAsStream("/assets/nocturnum/lang/" + lang + ".json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            o.entrySet().forEach(e -> into.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            Platform.log("Lang load failed (" + lang + "): " + e);
        }
    }

    public static boolean russian() {
        if (forced.equals("ru")) return true;
        if (forced.equals("en")) return false;
        String l = Platform.languageCode();
        return l == null || l.toLowerCase().startsWith("ru") || l.toLowerCase().startsWith("uk") || l.toLowerCase().startsWith("be") || l.toLowerCase().startsWith("kk");
    }

    public static String t(String key) {
        load();
        Map<String, String> primary = russian() ? RU : EN;
        String s = primary.get(key);
        if (s == null) s = EN.get(key);
        if (s == null) s = RU.get(key);
        return s != null ? s : key;
    }

    public static String t(String key, Object... args) {
        String s = t(key);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    /** English name of a module (for the small subtitle in tooltips). */
    public static String english(String key) {
        load();
        String s = EN.get(key);
        return s != null ? s : key;
    }

    public static boolean has(String key) {
        load();
        return RU.containsKey(key) || EN.containsKey(key);
    }
}
