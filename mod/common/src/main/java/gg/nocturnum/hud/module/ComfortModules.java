package gg.nocturnum.hud.module;

import gg.nocturnum.hud.Game;
import gg.nocturnum.hud.config.Category;
import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.config.Module;
import gg.nocturnum.hud.config.Setting;
import gg.nocturnum.hud.gfx.Gfx;
import gg.nocturnum.hud.gfx.Theme;
import gg.nocturnum.hud.platform.Platform;
import gg.nocturnum.hud.ui.Notifications;
import gg.nocturnum.hud.util.Lang;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

/** Leave protection, enhanced chat helpers, scoreboard / tab editors and the screenshot notification. */
public final class ComfortModules {
    private ComfortModules() {}

    public static void registerAll() {
        Config.register(new LeaveProtection());
        Config.register(new EnhancedChat());
        Config.register(new ScoreboardEditor());
        Config.register(new TabEditor());
        Config.register(new ScreenshotNotify());
    }

    // ------------------------------------------------------------------ leave protection

    /** Asks for confirmation before leaving a server so a mis-click does not drop you out of a fight. */
    public static final class LeaveProtection extends Module {
        public final Setting.Bool onlyCombat = add(new Setting.Bool("onlyCombat", false));
        private static long lastCombat;

        public LeaveProtection() {
            super("leaveprotection", Category.COMFORT, true, false, 0, 0);
        }

        public static void markCombat() { lastCombat = System.currentTimeMillis(); }

        @Override
        public void onTick() {
            if (Game.inWorld() && Game.player().hurtTime > 0) markCombat();
        }

        /** Should the disconnect button ask first? Singleplayer never asks. */
        public static boolean shouldAsk() {
            LeaveProtection m = Config.get(LeaveProtection.class);
            if (!m.enabled() || Game.singleplayer()) return false;
            return !m.onlyCombat.get() || System.currentTimeMillis() - lastCombat < 10_000;
        }
    }

    // ------------------------------------------------------------------ chat

    /** Time before every message and a click on it copies its text. */
    public static final class EnhancedChat extends Module {
        public final Setting.Bool timestamps = add(new Setting.Bool("timestamps", true));
        public final Setting.Mode format = add(new Setting.Mode("format", 0, "short", "seconds"));
        public final Setting.Bool clickCopy = add(new Setting.Bool("clickCopy", true));

        public EnhancedChat() {
            super("enhancedchat", Category.COMFORT, false, false, 0, 0);
        }

        /** Prefixes a message with a clickable time stamp that copies the message text. */
        public static net.minecraft.network.chat.Component decorate(net.minecraft.network.chat.Component msg) {
            EnhancedChat m = Config.get(EnhancedChat.class);
            if (!m.enabled() || !m.timestamps.get()) return msg;
            var stamp = net.minecraft.network.chat.Component.literal("[" + m.stamp() + "] ").withStyle(s -> {
                var st = s.withColor(net.minecraft.ChatFormatting.GRAY);
                if (m.clickCopy.get()) {
                    st = st.withClickEvent(new net.minecraft.network.chat.ClickEvent.CopyToClipboard(msg.getString()))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal(Lang.t("chat.copyHint"))));
                }
                return st;
            });
            return net.minecraft.network.chat.Component.empty().append(stamp).append(msg);
        }

        public String stamp() {
            return LocalTime.now().format(DateTimeFormatter.ofPattern(format.is("seconds") ? "HH:mm:ss" : "HH:mm"));
        }
    }

    // ------------------------------------------------------------------ vanilla HUD editors

    /** Moves, scales or hides the scoreboard on the right. The wrapper is installed by the platform layer. */
    public static final class ScoreboardEditor extends Module {
        public final Setting.Num offsetX = add(new Setting.Num("offsetX", 0f, -50f, 50f, 1f, "%"));
        public final Setting.Num offsetY = add(new Setting.Num("offsetY", 0f, -50f, 50f, 1f, "%"));
        public final Setting.Num scale = add(new Setting.Num("scaleSb", 1f, 0.5f, 1.5f, 0.05f, "x"));
        public final Setting.Bool hide = add(new Setting.Bool("hide", false));

        public ScoreboardEditor() {
            super("scoreboard", Category.HUD, false, false, 0, 0);
        }

        /** Renders {@code vanilla} with the editor's transform (anchored at the right edge, vertical centre). */
        public static void wrap(Gfx g, Runnable vanilla) {
            ScoreboardEditor m = Config.get(ScoreboardEditor.class);
            if (!m.enabled()) {
                vanilla.run();
                return;
            }
            if (m.hide.get()) return;
            float s = m.scale.get();
            g.push(g.w() - g.w() * s + g.w() * m.offsetX.get() / 100f, g.h() / 2f - g.h() / 2f * s + g.h() * m.offsetY.get() / 100f, s);
            try {
                vanilla.run();
            } finally {
                g.pop();
            }
        }
    }

    /** Scales and moves the player list shown with Tab. */
    public static final class TabEditor extends Module {
        public final Setting.Num offsetY = add(new Setting.Num("offsetY", 0f, -20f, 40f, 1f, "%"));
        public final Setting.Num scale = add(new Setting.Num("scaleTab", 1f, 0.5f, 1.5f, 0.05f, "x"));

        public TabEditor() {
            super("tabeditor", Category.HUD, false, false, 0, 0);
        }

        public static void wrap(Gfx g, Runnable vanilla) {
            TabEditor m = Config.get(TabEditor.class);
            if (!m.enabled()) {
                vanilla.run();
                return;
            }
            float s = m.scale.get();
            g.push(g.w() / 2f - g.w() / 2f * s, g.h() * m.offsetY.get() / 100f, s);
            try {
                vanilla.run();
            } finally {
                g.pop();
            }
        }
    }

    // ------------------------------------------------------------------ screenshots

    /** After a screenshot: a plate with the file name; keys copy the picture or open the folder. */
    public static final class ScreenshotNotify extends Module {
        public final Setting.Key copyKey = add(new Setting.Key("copyKey"));
        public final Setting.Key folderKey = add(new Setting.Key("folderKey"));
        public final Setting.Bool autoCopy = add(new Setting.Bool("autoCopy", false));
        private long seen;
        private File last;
        private boolean dA, dB;
        private int tick;

        public ScreenshotNotify() {
            super("screenshots", Category.COMFORT, false, false, 0, 0);
            copyKey.set(-1);
        }

        private File dir() { return new File(Game.mc().gameDirectory, "screenshots"); }

        @Override
        public void onEnable() { seen = System.currentTimeMillis(); }

        @Override
        public void onTick() {
            if (++tick % 20 == 0) {
                File[] fs = dir().listFiles((d, n) -> n.endsWith(".png"));
                if (fs != null && fs.length > 0) {
                    File newest = Arrays.stream(fs).max(Comparator.comparingLong(File::lastModified)).get();
                    if (newest.lastModified() > seen) {
                        seen = newest.lastModified();
                        last = newest;
                        Notifications.push(Lang.t("module.screenshots"), newest.getName(), Theme.OK);
                        if (autoCopy.get()) copy();
                    }
                }
            }
            if (Platform.screenOpen()) return;
            boolean a = copyKey.get() >= 0 && Platform.isKeyDown(copyKey.get());
            boolean b = folderKey.get() >= 0 && Platform.isKeyDown(folderKey.get());
            if (a && !dA) copy();
            if (b && !dB) openFolder();
            dA = a;
            dB = b;
        }

        private void copy() {
            if (last == null) return;
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(last);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.Transferable() {
                    public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() { return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor}; }
                    public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f) { return f.equals(java.awt.datatransfer.DataFlavor.imageFlavor); }
                    public Object getTransferData(java.awt.datatransfer.DataFlavor f) { return img; }
                }, null);
                Notifications.push(Lang.t("module.screenshots"), Lang.t("shot.copied"), Theme.OK);
            } catch (Throwable t) {
                Notifications.push(Lang.t("module.screenshots"), Lang.t("shot.copyFailed"), Theme.BAD);
            }
        }

        private void openFolder() {
            try {
                java.awt.Desktop.getDesktop().open(dir());
            } catch (Throwable t) {
                Platform.log("open screenshots folder failed: " + t);
            }
        }
    }
}
