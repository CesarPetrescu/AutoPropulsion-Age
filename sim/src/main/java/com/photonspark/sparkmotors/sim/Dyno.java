package com.photonspark.sparkmotors.sim;
public final class Dyno {
    public static void main(String[] args) {
        System.out.println("rpm,torque_nm,power_kw");
        for (int rpm = 800; rpm <= 6800; rpm += 200)
            System.out.printf(java.util.Locale.ROOT, "%d,%.2f,%.2f%n", rpm, PistonEngine.torque(rpm, false, 6800), PistonEngine.powerKw(rpm, false, 6800));
    }
}
