package com.photonspark.autopropulsion.sim;

public final class Numbers {
    private Numbers() {}
    public static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, finite(value, "value")));
    }
    public static double positive(double value, String name) {
        if (finite(value, name) <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
