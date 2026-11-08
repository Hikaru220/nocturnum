package gg.nocturnum.hud.module;

import gg.nocturnum.hud.platform.Platform;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Reconnects to the last server after a restart. Active only on the "disconnected" screen:
 * checks the port every 10 s, at most 5 times, with a cancel button.
 */
public final class AutoReconnect {
    private AutoReconnect() {}

    private static final int MAX_TRIES = 5;
    private static final long INTERVAL_MS = 10_000;

    private static String lastName, lastIp;
    private static Screen screen;
    private static Button button;
    private static boolean cancelled, joining;
    private static int tries;
    private static long nextAt, lastJoinAt;
    private static volatile boolean pinging, up;

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var s = client.getCurrentServer();
            if (s != null) {
                lastName = s.name;
                lastIp = s.ip;
            } else {
                lastIp = null; // singleplayer
            }
        });
        ScreenEvents.AFTER_INIT.register((client, s, w, h) -> {
            if (!(s instanceof DisconnectedScreen) || lastIp == null) return;
            if (screen != s) {
                screen = s;
                reset();
                // the server answers but kicks us right away (whitelist, ban): do not loop
                if (System.currentTimeMillis() - lastJoinAt < 60_000) cancelled = true;
                ScreenEvents.remove(s).register(x -> {
                    if (screen == x) {
                        screen = null;
                        button = null;
                    }
                });
            }
            button = Button.builder(Component.literal(label()), b -> {
                cancelled = true;
                b.setMessage(Component.literal(label()));
            }).bounds(w / 2 - 110, 6, 220, 20).build();
            Platform.addWidget(s, button);
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> tick());
    }

    private static void reset() {
        cancelled = false;
        joining = false;
        up = false;
        pinging = false;
        tries = 0;
        nextAt = System.currentTimeMillis() + 5_000;
    }

    private static String label() {
        if (joining) return "Сервер доступен, подключаюсь…";
        if (cancelled) return "Автопереподключение отменено";
        if (tries >= MAX_TRIES && !pinging) return "Сервер не ответил — автопереподключение остановлено";
        int sec = (int) Math.max(0, (nextAt - System.currentTimeMillis() + 999) / 1000);
        return "Отмена автопереподключения (попытка " + Math.min(tries + 1, MAX_TRIES) + "/" + MAX_TRIES + ", " + sec + " с)";
    }

    private static void tick() {
        if (screen == null || lastIp == null || joining) {
            if (button != null && screen != null) button.setMessage(Component.literal(label()));
            return;
        }
        if (button != null) button.setMessage(Component.literal(label()));
        if (cancelled || tries >= MAX_TRIES && !pinging) return;
        if (up) {
            joining = true;
            lastJoinAt = System.currentTimeMillis();
            String name = lastName == null ? "Server" : lastName, ip = lastIp;
            Minecraft.getInstance().execute(() -> {
                if (screen != null) Platform.joinServer(name, ip);
            });
            return;
        }
        if (!pinging && System.currentTimeMillis() >= nextAt) {
            pinging = true;
            tries++;
            nextAt = System.currentTimeMillis() + INTERVAL_MS;
            final String ip = lastIp;
            Thread t = new Thread(() -> {
                try {
                    up = reachable(ip);
                } finally {
                    pinging = false;
                }
            }, "nocturnum-reconnect-ping");
            t.setDaemon(true);
            t.start();
        }
    }

    private static boolean reachable(String ip) {
        try {
            String host = ip;
            int port = 25565;
            int i = ip.lastIndexOf(':');
            if (i > 0 && ip.indexOf(':') == i) {
                host = ip.substring(0, i);
                port = Integer.parseInt(ip.substring(i + 1));
            }
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 3000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
