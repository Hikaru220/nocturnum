package gg.nocturnum.hud.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minimal Discord Rich Presence client (local named pipe), so the status works even when the launcher is closed. */
public final class DiscordIpc {
    private DiscordIpc() {}

    private static final String ID = System.getProperty("nocturnum.rpc.appid", "1565258293826297997");
    private static volatile String details = "", state = "";
    private static volatile long startSec;
    private static volatile boolean dirty;
    private static Thread worker;

    public static synchronized void update(String d, String s, long start) {
        if (d.equals(details) && s.equals(state)) return;
        details = d;
        state = s;
        startSec = start;
        dirty = true;
        if (worker == null && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            worker = new Thread(DiscordIpc::run, "nocturnum-rpc");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static void run() {
        RandomAccessFile pipe = null;
        long lastSend = 0;
        while (true) {
            try {
                Thread.sleep(2000);
                if (pipe == null) {
                    pipe = connect();
                    if (pipe == null) {
                        Thread.sleep(28000);
                        continue;
                    }
                    dirty = true;
                }
                if (dirty && System.currentTimeMillis() - lastSend >= 15000) {
                    dirty = false;
                    lastSend = System.currentTimeMillis();
                    gg.nocturnum.hud.platform.Platform.log("Discord RPC: " + details);
                    send(pipe, 1, activity());
                    readFrame(pipe);
                }
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                try {
                    if (pipe != null) pipe.close();
                } catch (IOException ignored) {
                    // already gone
                }
                pipe = null;
            }
        }
    }

    private static RandomAccessFile connect() {
        for (int i = 0; i < 10; i++) {
            try {
                RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                send(f, 0, "{\"v\":1,\"client_id\":\"" + ID + "\"}");
                readFrame(f);
                return f;
            } catch (IOException ignored) {
                // try the next pipe
            }
        }
        return null;
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static String activity() {
        String d = details, s = state;
        StringBuilder sb = new StringBuilder("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":").append(ProcessHandle.current().pid())
            .append(",\"activity\":{\"details\":\"").append(esc(d)).append('"');
        if (!s.isEmpty()) sb.append(",\"state\":\"").append(esc(s)).append('"');
        sb.append(",\"timestamps\":{\"start\":").append(startSec).append("},\"assets\":{\"large_image\":\"nocturnum\",\"large_text\":\"Nocturnum\"}")
            .append(",\"buttons\":[{\"label\":\"Скачать Nocturnum\",\"url\":\"https://t.me/Nocturnum_Launcher\"}]}},\"nonce\":\"")
            .append(UUID.randomUUID()).append("\"}");
        return sb.toString();
    }

    private static void send(RandomAccessFile f, int op, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[8 + b.length];
        out[0] = (byte) op;
        out[4] = (byte) b.length;
        out[5] = (byte) (b.length >> 8);
        out[6] = (byte) (b.length >> 16);
        out[7] = (byte) (b.length >> 24);
        System.arraycopy(b, 0, out, 8, b.length);
        f.write(out);
    }

    private static void readFrame(RandomAccessFile f) throws IOException {
        byte[] h = new byte[8];
        f.readFully(h);
        int len = (h[4] & 255) | (h[5] & 255) << 8 | (h[6] & 255) << 16 | (h[7] & 255) << 24;
        if (len < 0 || len > 1 << 20) throw new IOException("bad frame");
        f.readFully(new byte[len]);
    }
}
