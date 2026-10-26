package gg.nocturnum.hud.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Projection;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.ui.Notifications;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Lang;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Waypoints and friends. Both are only lists of things YOU saved; nothing is tracked or sent anywhere. */
public final class SocialModules {
    private SocialModules() {}

    public static void registerAll() {
        Config.register(new Waypoints());
        Config.register(new Friends());
    }

    private static String sanitize(String s) { return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    /** Saved coordinates shown on screen with the distance. Stored per server and world. */
    public static final class Waypoints extends Module {
        public final Setting.Key addKey = add(new Setting.Key("addKey"));
        public final Setting.Key removeKey = add(new Setting.Key("removeKey"));
        public final Setting.Key hideKey = add(new Setting.Key("hideKey"));
        public final Setting.Bool deathPoint = add(new Setting.Bool("deathPoint", true));
        public final Setting.Color color = add(new Setting.Color("color", 0xFF4FD6E8));
        public final Setting.Num maxDistance = add(new Setting.Num("maxDistance", 0f, 0f, 5000f, 50f, "m"));

        private record Point(String name, double x, double y, double z) {}

        private final List<Point> points = new ArrayList<>();
        private String loadedKey = "";
        private boolean hidden, dA, dR, dH, wasDead;

        public Waypoints() {
            super("waypoints", Category.COMFORT, false, false, 0, 0);
        }

        private String key() {
            String srv = Game.singleplayer() ? "local" : sanitize(Game.serverAddress());
            String world = Game.level() == null ? "world" : sanitize(Game.level().dimension().identifier().toString());
            return srv + "/" + world;
        }

        private Path file(String key) { return Config.dir().resolve("waypoints").resolve(key + ".json"); }

        private void load() {
            points.clear();
            loadedKey = key();
            try {
                Path f = file(loadedKey);
                if (Files.exists(f)) {
                    for (var e : JsonParser.parseString(Files.readString(f)).getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        points.add(new Point(o.get("name").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void persist() {
            try {
                JsonArray a = new JsonArray();
                for (Point p : points) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", p.name);
                    o.addProperty("x", p.x);
                    o.addProperty("y", p.y);
                    o.addProperty("z", p.z);
                    a.add(o);
                }
                Path f = file(loadedKey);
                Files.createDirectories(f.getParent());
                Files.writeString(f, a.toString());
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onTick() {
            if (!Game.inWorld()) return;
            if (!key().equals(loadedKey)) load();
            var p = Game.player();
            if (deathPoint.get()) {
                if (p.isDeadOrDying() && !wasDead) {
                    wasDead = true;
                    points.add(new Point(Lang.t("waypoint.death"), p.getX(), p.getY(), p.getZ()));
                    persist();
                } else if (!p.isDeadOrDying()) {
                    wasDead = false;
                }
            }
            if (Platform.screenOpen()) return;
            boolean a = addKey.get() >= 0 && Platform.isKeyDown(addKey.get());
            boolean r = removeKey.get() >= 0 && Platform.isKeyDown(removeKey.get());
            boolean h = hideKey.get() >= 0 && Platform.isKeyDown(hideKey.get());
            if (a && !dA) {
                points.add(new Point(Lang.t("waypoint.name", points.size() + 1), p.getX(), p.getY(), p.getZ()));
                persist();
                Notifications.push(Lang.t("module.waypoints"), Lang.t("waypoint.added"), Theme.OK);
            }
            if (r && !dR && !points.isEmpty()) {
                Point near = null;
                double best = 25;
                for (Point q : points) {
                    double d = (q.x - p.getX()) * (q.x - p.getX()) + (q.y - p.getY()) * (q.y - p.getY()) + (q.z - p.getZ()) * (q.z - p.getZ());
                    if (d < best) {
                        best = d;
                        near = q;
                    }
                }
                if (near != null) {
                    points.remove(near);
                    persist();
                    Notifications.push(Lang.t("module.waypoints"), Lang.t("waypoint.removed"), Theme.WARN);
                }
            }
            if (h && !dH) hidden = !hidden;
            dA = a;
            dR = r;
            dH = h;
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (hidden || !Game.inWorld() || points.isEmpty()) return;
            var me = Game.player();
            for (Point q : points) {
                double dx = q.x - me.getX(), dy = q.y - me.getY(), dz = q.z - me.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (maxDistance.get() > 0 && dist > maxDistance.get()) continue;
                float[] s = Projection.project(q.x, q.y + 1.0, q.z);
                if (s == null) continue;
                int col = color.argb();
                String label = q.name + "  " + Math.round(dist) + "m";
                int w = g.textW(label) + 10;
                float x = Math.max(w / 2f + 2, Math.min(g.w() - w / 2f - 2, s[0]));
                float y = Math.max(14, Math.min(g.h() - 24, s[1]));
                Draw.circle(g, x, y, 3, col);
                Draw.rect(g, x - w / 2f, y - 18, w, 12, 5, Theme.plateBg());
                Draw.ring(g, x - w / 2f, y - 18, w, 12, 5, 1, Colors.withAlpha(col, 140));
                g.text(label, Math.round(x - w / 2f + 5), Math.round(y - 16), Theme.TEXT, true);
            }
        }
    }

    /** Marks players you added to your list with a coloured diamond over their head. Only players the game already draws. */
    public static final class Friends extends Module {
        public final Setting.Key addKey = add(new Setting.Key("addKey"));
        public final Setting.Color color = add(new Setting.Color("color", 0xFF7CFF6B));
        public final Setting.Bool sound = add(new Setting.Bool("sound", false));
        private final List<String> names = new ArrayList<>();
        private boolean dA, loaded;

        public Friends() {
            super("friends", Category.COMFORT, false, false, 0, 0);
        }

        private Path file() { return Config.dir().resolve("friends.json"); }

        private void load() {
            loaded = true;
            try {
                if (Files.exists(file())) for (var e : JsonParser.parseString(Files.readString(file())).getAsJsonArray()) names.add(e.getAsString());
            } catch (Exception ignored) {
            }
        }

        private void persist() {
            try {
                JsonArray a = new JsonArray();
                names.forEach(a::add);
                Files.writeString(file(), a.toString());
            } catch (Exception ignored) {
            }
        }

        public boolean isFriend(String name) { return names.contains(name.toLowerCase(Locale.ROOT)); }

        @Override
        public void onTick() {
            if (!loaded) load();
            if (!Game.inWorld() || Platform.screenOpen()) return;
            boolean a = addKey.get() >= 0 && Platform.isKeyDown(addKey.get());
            if (a && !dA) {
                Entity e = Game.mc().crosshairPickEntity;
                if (e instanceof Player p) {
                    String n = p.getName().getString().toLowerCase(Locale.ROOT);
                    if (names.remove(n)) Notifications.push(Lang.t("module.friends"), Lang.t("friends.removed"), Theme.WARN);
                    else {
                        names.add(n);
                        Notifications.push(Lang.t("module.friends"), Lang.t("friends.added", p.getName().getString()), Theme.OK);
                    }
                    persist();
                }
            }
            dA = a;
        }

        @Override
        public void overlay(Gfx g, float pt) {
            if (!Game.inWorld() || names.isEmpty()) return;
            for (Entity e : Game.level().entitiesForRendering()) {
                if (!(e instanceof Player p) || p == Game.player() || !isFriend(p.getName().getString())) continue;
                if (Game.player().distanceToSqr(p) > 96 * 96 || !Game.player().hasLineOfSight(p)) continue; // only players you actually see
                float[] s = Projection.project(p.getX(), p.getY() + p.getBbHeight() + 0.75, p.getZ());
                if (s == null) continue;
                int c = color.argb();
                Draw.line(g, s[0] - 4, s[1], s[0], s[1] - 5, 2, c);
                Draw.line(g, s[0], s[1] - 5, s[0] + 4, s[1], 2, c);
                Draw.line(g, s[0] + 4, s[1], s[0], s[1] + 5, 2, c);
                Draw.line(g, s[0], s[1] + 5, s[0] - 4, s[1], 2, c);
            }
        }
    }
}
