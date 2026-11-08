package gg.nocturnum.hud.util;

import gg.nocturnum.hud.platform.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Per-module cost meter: average and peak milliseconds per frame/tick over the last ~5 s. Off unless started. */
public final class Profiler {
    private Profiler() {}

    private static final class Stat {
        double sumMs, peakMs;
        int n;
    }

    private static final Map<String, Stat> STATS = new HashMap<>();
    private static boolean on;
    private static long startedAt;

    public static boolean active() { return on; }

    public static void start() {
        STATS.clear();
        startedAt = System.currentTimeMillis();
        on = true;
    }

    public static long now() { return on ? System.nanoTime() : 0; }

    public static void add(String id, long t0) {
        if (!on || t0 == 0) return;
        double ms = (System.nanoTime() - t0) / 1e6;
        Stat s = STATS.computeIfAbsent(id, k -> new Stat());
        s.sumMs += ms;
        s.n++;
        if (ms > s.peakMs) s.peakMs = ms;
    }

    /** Stops measuring and returns the report lines, heaviest first. */
    public static java.util.List<String> report() {
        on = false;
        double secs = Math.max(1, (System.currentTimeMillis() - startedAt) / 1000.0);
        var rows = new ArrayList<Map.Entry<String, Stat>>(STATS.entrySet());
        rows.sort((a, b) -> Double.compare(b.getValue().sumMs, a.getValue().sumMs));
        var out = new ArrayList<String>();
        out.add("Nocturnum perf: " + (int) secs + " c, мс/секунду (среднее за вызов / пик)");
        for (var e : rows) {
            Stat s = e.getValue();
            out.add(String.format("%-14s %6.2f мс/с   %.3f / %.3f", e.getKey(), s.sumMs / secs, s.sumMs / Math.max(1, s.n), s.peakMs));
        }
        STATS.clear();
        return out;
    }

    /** Called by the /nocturnum perf command: first call starts, second call (or after 5 s) prints. */
    public static void toggle() {
        if (!on) {
            start();
            Platform.chat("Nocturnum: замер запущен. Повторите /nocturnum perf через 5–10 секунд.");
            return;
        }
        for (String l : report()) {
            Platform.chat(l);
            Platform.log(l);
        }
    }
}
