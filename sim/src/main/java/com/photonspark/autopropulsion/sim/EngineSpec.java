package com.photonspark.autopropulsion.sim;

/** SI-oriented engine calibration. Rotary displacement is explicitly torque-equivalent. */
public record EngineSpec(Family family, double displacementLitres, double compressionRatio,
                         double idleRpm, double redlineRpm, double maxBoostBar,
                         double structuralTorqueNm, double radiatorWattsPerKelvin,
                         double powerMultiplier, boolean raceCam, String bottleneck) {
    public enum Family { PISTON, ROTARY }
    public EngineSpec {
        if (family == null || bottleneck == null) throw new IllegalArgumentException("missing family/bottleneck");
        Numbers.positive(displacementLitres, "displacement");
        if (Numbers.finite(compressionRatio, "compression") <= 1 || compressionRatio > 25) throw new IllegalArgumentException("compression outside (1,25]");
        Numbers.positive(idleRpm, "idle");
        if (Numbers.finite(redlineRpm, "redline") <= idleRpm || redlineRpm > 14000) throw new IllegalArgumentException("invalid redline");
        if (Numbers.finite(maxBoostBar, "boost") < 0 || maxBoostBar > 3) throw new IllegalArgumentException("boost outside [0,3]");
        Numbers.positive(structuralTorqueNm, "structural torque");
        Numbers.positive(radiatorWattsPerKelvin, "radiator");
        Numbers.positive(powerMultiplier, "power multiplier");
    }
    public static EngineSpec reference() {
        return new EngineSpec(Family.PISTON, 2, 10, 850, 6800, 0, 260, 240, 1, false, "engine/rods");
    }
    public static EngineSpec rotary() {
        return new EngineSpec(Family.ROTARY, 2.616, 10, 1000, 9000, 0, 290, 260, 1, false, "engine/apex_seals");
    }
    public double efficiency() { return (1 - StrictMath.pow(compressionRatio, -0.35)) * (family == Family.ROTARY ? 0.85 : 1); }
    public EngineSpec withBoost(double boost, double torqueLimit) {
        return new EngineSpec(family, displacementLitres, compressionRatio, idleRpm, redlineRpm,
            boost, torqueLimit, radiatorWattsPerKelvin, powerMultiplier, raceCam, bottleneck);
    }
    public EngineSpec withRaceCam() {
        return new EngineSpec(family, displacementLitres, compressionRatio, idleRpm, Math.min(9000, redlineRpm + 800),
            maxBoostBar, structuralTorqueNm, radiatorWattsPerKelvin, powerMultiplier, true, bottleneck);
    }
}
