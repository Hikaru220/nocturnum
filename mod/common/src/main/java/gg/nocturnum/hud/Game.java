package gg.nocturnum.hud;

import gg.nocturnum.hud.config.Config;
import gg.nocturnum.hud.module.StreamerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Read-only accessors for things the player already sees in vanilla (F3, inventory, tab list). */
public final class Game {
    private Game() {}

    public static Minecraft mc() { return Minecraft.getInstance(); }

    public static LocalPlayer player() { return mc().player; }

    public static ClientLevel level() { return mc().level; }

    public static boolean inWorld() { return mc().player != null && mc().level != null; }

    public static int fps() { return mc().getFps(); }

    public static int ping() {
        var conn = mc().getConnection();
        if (conn == null || mc().player == null) return -1;
        PlayerInfo info = conn.getPlayerInfo(mc().player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    public static boolean singleplayer() { return mc().hasSingleplayerServer(); }

    public static String serverAddress() {
        var data = mc().getCurrentServer();
        return data == null ? "" : data.ip;
    }

    public static boolean streamer() { return Config.get(StreamerModule.class).enabled(); }

    public static String playerName() {
        return streamer() ? "Player" : mc().getUser().getName();
    }

    public static String direction(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        String[] n = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        return n[Math.round(y / 45f) % 8];
    }

    public static String biome() {
        if (!inWorld()) return "";
        var holder = level().getBiome(player().blockPosition());
        return holder.unwrapKey().map(k -> Component.translatable("biome." + k.identifier().getNamespace() + "." + k.identifier().getPath()).getString()).orElse("?");
    }

    public static List<ItemStack> armor() {
        List<ItemStack> out = new ArrayList<>();
        if (!inWorld()) return out;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            out.add(player().getItemBySlot(s));
        }
        return out;
    }

    public static int count(net.minecraft.world.item.Item item) {
        if (!inWorld()) return 0;
        int n = 0;
        var inv = player().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    public static String formatTime(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    public static BlockPos pos() { return player().blockPosition(); }
}
