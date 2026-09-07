package com.photonspark.autopropulsion.sim;

import java.util.Arrays;

/** Immutable, endpoint-clamped, piecewise-linear curve. */
public final class Curve {
    private final double[] x;
    private final double[] y;
    public Curve(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) throw new IllegalArgumentException("two or more matching points required");
        this.x = x.clone(); this.y = y.clone();
        for (int i = 0; i < x.length; i++) {
            Numbers.finite(x[i], "x"); Numbers.finite(y[i], "y");
            if (i > 0 && x[i] <= x[i - 1]) throw new IllegalArgumentException("x must be strictly increasing");
        }
    }
    public double at(double t) {
        Numbers.finite(t, "curve input");
        if (t <= x[0]) return y[0];
        if (t >= x[x.length - 1]) return y[y.length - 1];
        int i = Arrays.binarySearch(x, t);
        if (i >= 0) return y[i];
        int right = -i - 1, left = right - 1;
        return y[left] + (y[right] - y[left]) * ((t - x[left]) / (x[right] - x[left]));
    }
}
