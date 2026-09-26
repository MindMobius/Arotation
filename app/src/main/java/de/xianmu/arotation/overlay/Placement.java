package de.xianmu.arotation.overlay;

/** Pure, normalized geometry so position survives rotation, density and display size changes. */
public final class Placement {
    private Placement() {}
    public static float clamp(float n, float min, float max) {
        if (!Float.isFinite(n)) return min;
        return Math.max(min, Math.min(Math.max(min, max), n));
    }
    public static float normalize(float value, float min, float max) {
        return max <= min ? .5f : clamp((value-min)/(max-min), 0f, 1f);
    }
    public static int resolve(float fraction, int min, int max) {
        return Math.round(min + clamp(fraction, 0f, 1f) * Math.max(0, max-min));
    }
    public static int nearestEdge(int x, int min, int max) {
        return x-min < max-x ? min : max;
    }
}
