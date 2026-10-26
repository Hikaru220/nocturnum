package gg.nocturnum.hud.gfx;

import gg.nocturnum.hud.platform.Platform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Projects world points onto the GUI so world-anchored effects (rings, trails, hat, block outline) can be drawn
 * with the same code on every game version. Effects are screen-space overlays, so they do not hide behind blocks.
 */
public final class Projection {
    private Projection() {}

    private static double cx, cy, cz;
    private static final Quaternionf INV = new Quaternionf();
    private static float tanHalfFov = 0.5f, aspect = 1.78f;
    private static float guiW = 1, guiH = 1;
    private static boolean valid;
    /** Vertical FOV in degrees as last used by the renderer (captured by a mixin); <=0 = unknown. */
    public static float lastFov = -1;
    public static boolean thirdPerson;
    private static final Vector3f TMP = new Vector3f();

    /** Call once per frame before projecting. */
    public static void begin(Gfx g) {
        double[] info = Platform.cameraInfo();
        if (info == null) {
            valid = false;
            return;
        }
        cx = info[0];
        cy = info[1];
        cz = info[2];
        INV.set((float) info[3], (float) info[4], (float) info[5], (float) info[6]).conjugate();
        float fov = lastFov > 1 ? lastFov : (float) info[7];
        tanHalfFov = (float) Math.tan(Math.toRadians(fov) / 2.0);
        guiW = g.w();
        guiH = g.h();
        aspect = guiW / guiH;
        thirdPerson = info[8] > 0.5f;
        valid = true;
    }

    /** Returns {x, y} in GUI pixels, or null if the point is behind the camera. */
    public static float[] project(double x, double y, double z) {
        if (!valid) return null;
        TMP.set((float) (x - cx), (float) (y - cy), (float) (z - cz));
        TMP.rotate(INV);
        if (TMP.z > -0.05f) return null;
        float nx = (TMP.x / -TMP.z) / (tanHalfFov * aspect);
        float ny = (TMP.y / -TMP.z) / tanHalfFov;
        return new float[]{(nx + 1f) * 0.5f * guiW, (1f - ny) * 0.5f * guiH};
    }
}
