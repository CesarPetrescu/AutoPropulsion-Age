package com.photonspark.autopropulsion.sim;

public final class Numbers {
    private Numbers() {}
    public static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
    public static double positive(double value, String name) {
        if (finite(value, name) <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
    public static double clamp(double value, double min, double max) {
        finite(value, "value");
        if (min > max) throw new IllegalArgumentException("inverted bounds");
        return Math.max(min, Math.min(max, value));
    }
    public static double approach(double current, double target, double rate, double dt) {
        return current + (target - current) * (1 - StrictMath.exp(-rate * dt));
    }
}
