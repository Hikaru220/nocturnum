package gg.nocturnum.hud.module;

import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.ui.ConfirmView;
import net.minecraft.network.chat.Component;

/** Notices the SunShine "not on the whitelist" chat line and offers to open the application channel. */
public final class Whitelist {
    private Whitelist() {}

    private static final String MARK = "Тебя нет в белом списке сервера";
    private static final String APPLY_URL = "https://discord.gg/xmn3AdGeZB";
    private static long shownAt;

    private static void open(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else new ProcessBuilder(os.contains("mac") ? "open" : "xdg-open", url).start();
        } catch (java.io.IOException ignored) {
            // the link is also shown in the Discord channel list
        }
    }

    public static Component pass(Component msg) {
        try {
            if (msg.getString().contains(MARK) && System.currentTimeMillis() - shownAt > 60_000) {
                shownAt = System.currentTimeMillis();
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (Platform.screenOpen()) return;
                    Platform.openView(new ConfirmView("Нет в вайтлисте", "Подай заявку в Discord сервера.", "Подать заявку", "Меня приняли",
                        () -> open(APPLY_URL), null));
                });
            }
        } catch (RuntimeException ignored) {
            // never break chat
        }
        return ComfortModules.EnhancedChat.decorate(msg);
    }
}
