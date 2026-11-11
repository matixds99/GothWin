package com.winlator.inputcontrols;

/**
 * Pure geometry of a RADIAL_MENU element (no Android dependencies, so it can be unit tested on a plain JVM).
 *
 * Menus with few slices keep the classic Winlator layout (ring right around the button).
 * Menus with many slices (e.g. an A-Z wheel) get a large ring that fits the screen and, while open,
 * is shifted so it never leaves the screen.
 */
final class RadialMenuGeometry {
    static final int DENSE_MIN_SLICES = 9;
    private static final double TWO_PI = Math.PI * 2;

    final float cx;
    final float cy;
    final float buttonRadius;
    final float innerRadius;
    final float outerRadius;
    final int sliceCount;
    final boolean dense;

    private RadialMenuGeometry(float cx, float cy, float buttonRadius, float innerRadius, float outerRadius, int sliceCount, boolean dense) {
        this.cx = cx;
        this.cy = cy;
        this.buttonRadius = buttonRadius;
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.sliceCount = sliceCount;
        this.dense = dense;
    }

    static boolean isDense(int sliceCount) {
        return sliceCount >= DENSE_MIN_SLICES;
    }

    static RadialMenuGeometry compute(float buttonCx, float buttonCy, float buttonRadius, float snappingSize, float scale,
                                      int sliceCount, float viewWidth, float viewHeight, boolean open) {
        if (!isDense(sliceCount)) {
            float inner = buttonRadius + snappingSize * 0.5f;
            float outer = buttonRadius * 2 + snappingSize * scale;
            return new RadialMenuGeometry(buttonCx, buttonCy, buttonRadius, inner, outer, sliceCount, false);
        }

        float outer = Math.min(viewWidth, viewHeight) * 0.47f;
        float inner = Math.max(outer * 0.58f, buttonRadius + snappingSize);
        float cx = buttonCx;
        float cy = buttonCy;
        if (open) {
            cx = clampCenter(buttonCx, outer, viewWidth);
            cy = clampCenter(buttonCy, outer, viewHeight);
        }
        return new RadialMenuGeometry(cx, cy, buttonRadius, inner, outer, sliceCount, true);
    }

    private static float clampCenter(float value, float radius, float size) {
        float min = radius;
        float max = size - radius;
        if (max < min) return size * 0.5f;
        return Math.max(min, Math.min(value, max));
    }

    boolean isShifted(float buttonCx, float buttonCy) {
        return Math.abs(cx - buttonCx) > 0.5f || Math.abs(cy - buttonCy) > 0.5f;
    }

    float touchAreaCenterRadius() {
        return (innerRadius + outerRadius) * 0.5f;
    }

    /** Arc length of one slice measured at the middle of the ring. */
    float sliceArcLength() {
        return (float)(TWO_PI * touchAreaCenterRadius() / sliceCount);
    }

    /** Largest label size (px) that still fits a slice, used for dense menus. */
    float denseTextSize() {
        return Math.min(sliceArcLength() * 0.6f, (outerRadius - innerRadius) * 0.55f);
    }

    /** True when the point is inside the open ring (slices) or the center button. */
    boolean containsOpen(float x, float y) {
        return distance(x, y) < outerRadius;
    }

    boolean isInCenterButton(float x, float y) {
        return distance(x, y) < buttonRadius;
    }

    /**
     * Index of the slice under the point, or -1. Slice 0 starts at 12 o'clock and slices go clockwise,
     * exactly like they are drawn in ControlElement.
     */
    int sliceAt(float x, float y) {
        float dist = distance(x, y);
        if (dist <= innerRadius || dist > outerRadius) return -1;

        double angle = Math.atan2(y - cy, x - cx) + Math.PI * 0.5;
        if (angle < 0) angle += TWO_PI;
        int index = (int)(angle / (TWO_PI / sliceCount));
        return Math.min(index, sliceCount - 1);
    }

    private float distance(float x, float y) {
        float dx = x - cx;
        float dy = y - cy;
        return (float)Math.sqrt(dx * dx + dy * dy);
    }
}
