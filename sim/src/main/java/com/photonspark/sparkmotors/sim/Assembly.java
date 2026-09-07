package com.photonspark.sparkmotors.sim;

/** Two bits per slot: 0 absent, 1 stock, 2 sport. Stable save/network encoding. */
public enum Assembly {
    ENGINE("Engine"), TRANSMISSION("Transmission"), WHEELS("Wheel set"),
    BRAKES("Brake set"), SUSPENSION("Suspension"), BODY("Body kit");
    public final String title;
    Assembly(String title) { this.title = title; }
    public int variant(int config) { return (config >>> (ordinal() * 2)) & 3; }
    public int with(int config, int value) {
        if (value < 0 || value > 2) throw new IllegalArgumentException("Unknown assembly variant");
        return (config & ~(3 << (ordinal() * 2))) | (value << (ordinal() * 2));
    }
    public static int stock() {
        int c = 0;
        for (Assembly a : values()) c = a.with(c, 1);
        return c;
    }
    public static int sanitize(int config) {
        int c = 0;
        for (Assembly a : values()) c = a.with(c, Math.min(2, a.variant(config)));
        return c;
    }
    public static boolean canDrive(int config) {
        return ENGINE.variant(config) > 0 && TRANSMISSION.variant(config) > 0
            && WHEELS.variant(config) > 0 && BRAKES.variant(config) > 0 && SUSPENSION.variant(config) > 0;
    }
    public String itemName(int variant) { return (variant == 2 ? "sport_" : "stock_") + name().toLowerCase(java.util.Locale.ROOT); }
}
