package gg.nocturnum.hud.module;

import java.util.ArrayDeque;

/** Click counter for the CPS module. Fed by a mixin on the mouse handler. */
public final class Clicks {
    private Clicks() {}

    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

    public static void press(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) synchronized (LEFT) { LEFT.addLast(now); }
        else if (button == 1) synchronized (RIGHT) { RIGHT.addLast(now); }
    }

    private static int count(ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
            return q.size();
        }
    }

    public static int left() { return count(LEFT); }
    public static int right() { return count(RIGHT); }
}
