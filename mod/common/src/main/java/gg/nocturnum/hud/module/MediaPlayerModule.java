package gg.nocturnum.hud.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Draw;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.util.Colors;
import gg.nocturnum.hud.util.Lang;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shows the track playing on the PC (Yandex Music, Spotify, a browser tab...). The launcher reads it from Windows
 * media controls and serves it on 127.0.0.1 only; without the launcher the plate stays hidden.
 */
public final class MediaPlayerModule extends Module {
    public final Setting.Mode view = add(new Setting.Mode("view", 1, "compact", "large"));
    public final Setting.Bool hidePaused = add(new Setting.Bool("hidePaused", false));
    public final Setting.Key prevKey = add(new Setting.Key("prevKey"));
    public final Setting.Key toggleKey = add(new Setting.Key("toggleKey"));
    public final Setting.Key nextKey = add(new Setting.Key("nextKey"));

    private static final int COVER = 20;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build();
    private final String endpoint = System.getProperty("nocturnum.media");
    private final String token = System.getProperty("nocturnum.media.token", "");

    private volatile boolean available, playing;
    private volatile String title = "", artist = "";
    private volatile long positionMs, durationMs, fetchedAt, trackId = -1;
    private volatile int[] cover;
    private volatile long lastCoverTrack = -2;
    private Thread poller;
    private volatile boolean running;
    private boolean prevDown, toggleDown, nextDown;

    public MediaPlayerModule() {
        super("mediaplayer", Category.HUD, true, true, 0.16f, 0.55f);
    }

    @Override public void onEnable() { startPolling(); }
    @Override public void onDisable() { running = false; }

    private void startPolling() {
        if (endpoint == null || running) return;
        running = true;
        poller = new Thread(() -> {
            while (running) {
                try {
                    HttpResponse<String> r = http.send(req("/state").GET().build(), HttpResponse.BodyHandlers.ofString());
                    if (r.statusCode() == 200) {
                        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                        available = o.get("available").getAsBoolean();
                        title = o.get("title").getAsString();
                        artist = o.get("artist").getAsString();
                        positionMs = o.get("positionMs").getAsLong();
                        durationMs = o.get("durationMs").getAsLong();
                        playing = o.get("playing").getAsBoolean();
                        fetchedAt = System.currentTimeMillis();
                        long id = o.get("trackId").getAsLong();
                        if (id != lastCoverTrack && available) {
                            lastCoverTrack = id;
                            trackId = id;
                            loadCover();
                        }
                    } else {
                        available = false;
                    }
                } catch (Exception e) {
                    available = false;
                }
                try {
                    Thread.sleep(900);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "nocturnum-media");
        poller.setDaemon(true);
        poller.start();
    }

    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder(URI.create("http://" + endpoint + path)).timeout(Duration.ofSeconds(2)).header("X-Nocturnum-Token", token);
    }

    /** Downscales the cover to a tiny pixel grid drawn with plain fills (no texture upload needed). */
    private void loadCover() {
        try {
            HttpResponse<byte[]> r = http.send(req("/cover").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) {
                cover = null;
                return;
            }
            var img = javax.imageio.ImageIO.read(new ByteArrayInputStream(r.body()));
            if (img == null) {
                cover = null;
                return;
            }
            int[] px = new int[COVER * COVER];
            for (int y = 0; y < COVER; y++) {
                for (int x = 0; x < COVER; x++) {
                    px[y * COVER + x] = 0xFF000000 | img.getRGB(x * img.getWidth() / COVER, y * img.getHeight() / COVER);
                }
            }
            cover = px;
        } catch (Throwable t) {
            cover = null;
        }
    }

    private void command(String c) {
        if (endpoint == null) return;
        Thread t = new Thread(() -> {
            try {
                http.send(req("/cmd/" + c).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        }, "nocturnum-media-cmd");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onTick() {
        if (endpoint != null && !running) startPolling();
        if (Platform.screenOpen()) return;
        boolean p = prevKey.get() >= 0 && Platform.isKeyDown(prevKey.get());
        boolean t = toggleKey.get() >= 0 && Platform.isKeyDown(toggleKey.get());
        boolean n = nextKey.get() >= 0 && Platform.isKeyDown(nextKey.get());
        if (p && !prevDown) command("prev");
        if (t && !toggleDown) command("toggle");
        if (n && !nextDown) command("next");
        prevDown = p;
        toggleDown = t;
        nextDown = n;
    }

    @Override
    public boolean empty() {
        if (endpoint == null || !available) return true;
        return hidePaused.get() && !playing;
    }

    private boolean large() { return view.is("large"); }

    @Override public int width(Gfx g) { return large() ? 150 : 128; }
    @Override public int height(Gfx g) { return large() ? 44 : 28; }

    private String fit(Gfx g, String s, int maxW, boolean bold) {
        if ((bold ? g.textBoldW(s) : g.textW(s)) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && (bold ? g.textBoldW(t + "…") : g.textW(t + "…")) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    @Override
    public void render(Gfx g, float pt) {
        boolean preview = endpoint == null || !available;
        int w = width(g), h = height(g);
        Theme.plate(g, 0, 0, w, h, playing && !preview);
        int size = large() ? 32 : 20, cx = 6, cy = (h - size) / 2;
        int[] c = cover;
        if (c != null && !preview) {
            float k = size / (float) COVER;
            for (int y = 0; y < COVER; y++) {
                int x = 0;
                while (x < COVER) {
                    int col = c[y * COVER + x], run = 1;
                    while (x + run < COVER && c[y * COVER + x + run] == col) run++;
                    g.fill(cx + Math.round(x * k), cy + Math.round(y * k), cx + Math.round((x + run) * k), cy + Math.round((y + 1) * k), col);
                    x += run;
                }
            }
        } else {
            Draw.rect(g, cx, cy, size, size, 4, Theme.accent(90));
            Draw.centeredText(g, "♪", cx + size / 2f, cy + (size - g.fontH()) / 2 + 1, Theme.TEXT, true);
        }
        int tx = cx + size + 7, maxW = w - tx - 6;
        String t = preview ? Lang.t("media.sample") : title;
        String a = preview ? "Nocturnum" : artist;
        g.textBold(fit(g, t, maxW, true), tx, large() ? 6 : 4, Theme.TEXT, true);
        g.text(fit(g, a, maxW, false), tx, (large() ? 6 : 4) + g.fontH() + 2, Theme.TEXT_DIM, false);
        if (large()) {
            long pos = positionMs + (playing ? System.currentTimeMillis() - fetchedAt : 0);
            float f = durationMs > 0 ? Math.max(0f, Math.min(1f, pos / (float) durationMs)) : (preview ? 0.4f : 0f);
            Draw.rect(g, tx, h - 9, maxW, 3, 1.5f, Colors.withAlpha(0xFFFFFF, 30));
            Draw.rectGradientH(g, tx, h - 9, Math.max(2, maxW * f), 3, 1.5f, Theme.accent(), Theme.accent2());
        }
    }
}
