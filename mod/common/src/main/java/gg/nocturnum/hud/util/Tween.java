package gg.nocturnum.hud.util;

/** A value animated in real time (milliseconds), independent of the 20 TPS game clock. */
public final class Tween {
    private float from, to;
    private long start;
    private float durationMs;
    private Easing easing = Easing.OUT_CUBIC;

    /** Global speed multiplier (0.5–2). 0 disables animations. */
    public static float speed = 1f;

    public Tween(float initial) {
        from = to = initial;
    }

    public float get() {
        if (speed <= 0 || durationMs <= 0) return to;
        float t = (System.nanoTime() / 1_000_000L - start) * speed / durationMs;
        if (t >= 1f) return to;
        if (t <= 0f) return from;
        return from + (to - from) * easing.apply(t);
    }

    public float target() { return to; }

    public boolean running() {
        return speed > 0 && durationMs > 0 && (System.nanoTime() / 1_000_000L - start) * speed < durationMs;
    }

    public void to(float target, float ms, Easing e) {
        if (target == to) return;
        from = get();
        to = target;
        durationMs = ms;
        easing = e;
        start = System.nanoTime() / 1_000_000L;
    }

    public void set(float v) {
        from = to = v;
        durationMs = 0;
    }
}
