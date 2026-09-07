package com.photonspark.sparkmotors.sim;

/** Deterministic reference 2.0 L engine. SI units; no Minecraft dependencies. */
public final class PistonEngine {
    private static final double[] RPM = {0, 800, 1500, 2500, 3500, 4500, 5500, 6000, 6500, 7200};
    private static final double[] TORQUE = {0, 85, 125, 153, 173, 180, 178, 174, 153, 0};
    private PistonEngine() {}
    public static double torque(double rpm, boolean sport, int limiter) {
        if (!Double.isFinite(rpm) || rpm < 0 || rpm >= limiter) return 0;
        for (int i = 1; i < RPM.length; i++) {
            if (rpm <= RPM[i]) {
                double t = (rpm - RPM[i - 1]) / (RPM[i] - RPM[i - 1]);
                return (TORQUE[i - 1] + t * (TORQUE[i] - TORQUE[i - 1])) * (sport ? 1.38 : 1);
            }
        }
        return 0;
    }
    public static double powerKw(double rpm, boolean sport, int limiter) {
        return torque(rpm, sport, limiter) * rpm * Math.PI / 30000;
    }
}
