package gg.nocturnum.hud.util;

/** Easing curves shared by all animations so the whole UI moves with one "character". */
public enum Easing {
    LINEAR { public float apply(float t) { return t; } },
    OUT_QUAD { public float apply(float t) { return 1 - (1 - t) * (1 - t); } },
    OUT_CUBIC { public float apply(float t) { float u = 1 - t; return 1 - u * u * u; } },
    IN_CUBIC { public float apply(float t) { return t * t * t; } },
    IN_OUT_CUBIC { public float apply(float t) { return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; } },
    OUT_QUART { public float apply(float t) { float u = 1 - t; return 1 - u * u * u * u; } },
    OUT_EXPO { public float apply(float t) { return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t); } },
    OUT_BACK {
        public float apply(float t) {
            float c1 = 1.70158f, c3 = c1 + 1;
            float u = t - 1;
            return 1 + c3 * u * u * u + c1 * u * u;
        }
    },
    IN_QUAD { public float apply(float t) { return t * t; } };

    public abstract float apply(float t);
}
