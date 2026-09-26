package de.xianmu.arotation.rotation;

/** Pure rotation math. Rotation 0 is a device's natural orientation, NOT always portrait. */
public final class RotationPolicy {
    private RotationPolicy() {}
    public static boolean naturalLandscape(int width, int height, int rotation) {
        boolean wide = width > height;
        return (rotation & 1) == 0 ? wide : !wide;
    }
    public static boolean isLandscape(int rotation, boolean naturalLandscape) {
        return ((rotation & 1) == 0) == naturalLandscape;
    }
    public static int toggleTarget(int current, boolean naturalLandscape, boolean reverseLandscape) {
        if (isLandscape(current, naturalLandscape)) return naturalLandscape ? 1 : 0;
        int landscape = naturalLandscape ? 0 : 1;
        return (landscape + (reverseLandscape ? 2 : 0)) % 4;
    }
    public static boolean ownsSettings(int currentAuto, int currentRotation, int lastWrittenRotation) {
        return currentAuto == 0 && currentRotation == lastWrittenRotation;
    }
}
