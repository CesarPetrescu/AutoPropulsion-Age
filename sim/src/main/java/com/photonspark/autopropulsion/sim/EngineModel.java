package com.photonspark.autopropulsion.sim;

/** Calibrated lumped model, not a combustion chemistry or manufacturer dyno model. */
public final class EngineModel {
    private static final Curve PISTON_VE = new Curve(
        new double[]{0, 850, 1500, 2500, 4200, 5800, 6500, 7000, 9000, 14000},
        new double[]{0, .59, .70, .82, .90, .88, .83, .68, .20, 0});
    private static final Curve ROTARY_VE = new Curve(
        new double[]{0, 1000, 2500, 4500, 6500, 8000, 9000, 14000},
        new double[]{0, .48, .66, .79, .88, .90, .73, 0});
    private EngineModel() {}
    public static double torqueNm(EngineSpec spec, double rpm, double boostBar) {
        Numbers.finite(rpm, "rpm"); Numbers.finite(boostBar, "boost");
        if (rpm <= 0) return 0;
        double effectiveRpm = spec.raceCam() ? Math.max(0, rpm - 1000) : rpm;
        double ve = (spec.family() == EngineSpec.Family.PISTON ? PISTON_VE : ROTARY_VE).at(effectiveRpm);
        if (spec.raceCam() && rpm < 2500) ve *= .84;
        double chargeTempK = 293.15 + Math.max(0, boostBar) * 32;
        double densityRatio = (1 + Math.max(0, boostBar)) * 293.15 / chargeTempK;
        double indicated = 198 * spec.displacementLitres() * ve * spec.efficiency() * densityRatio * spec.powerMultiplier();
        return Math.max(0, indicated - 10 - .0018 * rpm);
    }
    public static double powerKw(double torqueNm, double rpm) { return torqueNm * rpm * (2 * StrictMath.PI / 60000); }
    public static double targetBoost(EngineSpec spec, double rpm, double throttle) {
        return spec.maxBoostBar() * Numbers.clamp((rpm - 1800) / 1800, 0, 1) * Numbers.clamp(throttle, 0, 1);
    }
    public static double fuelLitresPerSecond(double powerKw, double efficiency) {
        Numbers.positive(efficiency, "efficiency");
        // kW -> W, J/kg -> kg/s, kg/L -> L/s. An idle term accounts for idle losses.
        return .00018 + Math.max(0, powerKw) * 1000 / (efficiency * 43_000_000 * .745);
    }
}
