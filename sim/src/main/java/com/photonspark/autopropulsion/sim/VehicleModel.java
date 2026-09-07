package com.photonspark.autopropulsion.sim;

/** Server-friendly fixed-step longitudinal/bicycle prototype with fuel, heat and structural wear. */
public final class VehicleModel {
    public static final double MASS_KG = 1080, WHEEL_RADIUS_M = .32, WHEELBASE_M = 2.52;
    private static final double[] RATIOS = {0, 3.25, 1.95, 1.34, 1.03, .82};
    private EngineSpec spec;
    private double speed, yaw, rpm, boost, throttle, fuel = 30, coolantK = 293.15, oilK = 293.15;
    private double health = 100, odometer, wheelAngle;
    private boolean running;
    private int gear;
    private String dtc = "";
    public VehicleModel(EngineSpec spec) { this.spec = java.util.Objects.requireNonNull(spec); }
    public EngineSpec spec() { return spec; }
    public void setSpec(EngineSpec spec) { this.spec = java.util.Objects.requireNonNull(spec); }
    public void setRunning(boolean value) { running = value && fuel > 0 && health > 10; if (running) rpm = Math.max(spec.idleRpm(), rpm); }
    public void setYaw(double radians) { yaw = Numbers.finite(radians, "yaw"); }
    public void constrainSpeed(double metresPerSecond) { speed = Numbers.clamp(metresPerSecond, -60, 60); }
    public void refuel(double litres) { fuel = Numbers.clamp(fuel + Numbers.finite(litres, "fuel"), 0, 45); }
    public void repair() { health = 100; dtc = ""; }
    public void tick(VehicleInput input, int substeps, double grip, double damageMultiplier, double fuelMultiplier) {
        if (substeps < 1 || substeps > 8) throw new IllegalArgumentException("substeps outside 1..8");
        Numbers.finite(grip, "grip"); Numbers.finite(damageMultiplier, "damage multiplier"); Numbers.finite(fuelMultiplier, "fuel multiplier");
        if (grip < 0 || grip > 2 || damageMultiplier < 0 || damageMultiplier > 10 || fuelMultiplier < 0 || fuelMultiplier > 10) throw new IllegalArgumentException("invalid simulation configuration");
        double dt = .05 / substeps;
        for (int i = 0; i < substeps; i++) step(input, dt, grip, damageMultiplier, fuelMultiplier);
    }
    private void step(VehicleInput input, double dt, double grip, double damageMult, double fuelMult) {
        gear = input.gear();
        double ratio = gear < 0 ? -3.1 : RATIOS[gear];
        double coupling = 1 - input.clutch();
        throttle = Numbers.approach(throttle, running ? input.throttle() : 0, 9, dt);
        boost = Numbers.approach(boost, EngineModel.targetBoost(spec, rpm, throttle), throttle > .1 ? 2.5 : 10, dt);
        double wheelDrivenRpm = Math.abs(speed * ratio * 3.9 / WHEEL_RADIUS_M) * 60 / (2 * StrictMath.PI);
        if (running) {
            if (gear != 0 && coupling > .15) {
                // Assisted takeoff intentionally replaces a stiff clutch/shaft constraint in this milestone.
                double targetRpm = Math.max(spec.idleRpm() + throttle * 900, wheelDrivenRpm);
                rpm = Numbers.approach(rpm, targetRpm, 14 * coupling, dt);
            } else {
                double freeTorque = EngineModel.torqueNm(spec, rpm, boost) * throttle - .007 * Math.max(0, rpm - spec.idleRpm());
                rpm = Numbers.clamp(rpm + freeTorque / .28 * dt * 60 / (2 * StrictMath.PI), spec.idleRpm(), spec.redlineRpm() + 300);
            }
        } else { rpm = Numbers.approach(rpm, 0, 8, dt); }
        double torque = running && rpm < spec.redlineRpm() ? EngineModel.torqueNm(spec, rpm, boost) * throttle * Math.min(1, health / 50) : 0;
        double drive = gear == 0 ? 0 : torque * ratio * 3.9 * .89 * coupling / WHEEL_RADIUS_M;
        double traction = MASS_KG * 9.81 * grip * .63;
        drive = Numbers.clamp(drive, -traction, traction);
        double drag = .5 * 1.225 * .62 * speed * Math.abs(speed) + (Math.abs(speed) > .03 ? Math.copySign(130, speed) : 0);
        double next = speed + (drive - drag) / MASS_KG * dt;
        double braking = (input.brake() * 9.5 + (input.handbrake() ? 5.5 : 0)) * Math.min(1, grip) * dt;
        speed = Math.copySign(Math.max(0, Math.abs(next) - braking), next);
        speed = Numbers.clamp(speed, -18, 60);
        if (Math.abs(speed) < .002 && Math.abs(drive) < 130) speed = 0;
        yaw += speed / WHEELBASE_M * StrictMath.tan(input.steer() * .49) / (1 + speed * speed / 625) * dt;
        yaw = StrictMath.IEEEremainder(yaw, 2 * StrictMath.PI);
        wheelAngle = StrictMath.IEEEremainder(wheelAngle + speed / WHEEL_RADIUS_M * dt, 2 * StrictMath.PI);
        odometer += Math.abs(speed) * dt;
        double powerKw = EngineModel.powerKw(torque, rpm);
        if (running) {
            fuel = Math.max(0, fuel - EngineModel.fuelLitresPerSecond(powerKw, spec.efficiency()) * dt * fuelMult);
            double heatWatts = powerKw * 1000 * (1 / spec.efficiency() - 1) * .30 + 1700;
            double coolingWatts = spec.radiatorWattsPerKelvin() * (coolantK - 293.15) * (.45 + Math.abs(speed) * .05);
            coolantK = Numbers.clamp(coolantK + (heatWatts - coolingWatts) / (7 * 4180) * dt, 250, 500);
        } else { coolantK = Numbers.approach(coolantK, 293.15, .004, dt); }
        oilK = Numbers.approach(oilK, coolantK + (running ? 8 : 0), .015, dt);
        double overshoot = Math.max(0, torque / spec.structuralTorqueNm() - 1);
        double overrev = Math.max(0, rpm / spec.redlineRpm() - 1);
        double heatStress = Math.max(0, (coolantK - 393.15) / 30);
        health = Math.max(0, health - (overshoot * overshoot * 35 + overrev * overrev * 60 + heatStress * heatStress * 4) * dt * damageMult);
        if (overshoot > 0) dtc = "APA0101:" + spec.bottleneck();
        else if (coolantK > 393.15) dtc = "P0217";
        else if (overrev > .08) dtc = "APA0102:overrev";
        if (health <= 10) { running = false; dtc = "APA0100:engine_failed"; }
        if (fuel <= 0) { running = false; dtc = "APA0200:empty_tank"; }
    }
    public Snapshot snapshot() { return new Snapshot(speed, yaw, rpm, boost, fuel, coolantK, oilK, health, odometer, wheelAngle, running, gear, dtc); }
    public void restore(Snapshot s) {
        speed = Numbers.clamp(s.speed(), -60, 60); yaw = Numbers.finite(s.yaw(), "yaw");
        rpm = Numbers.clamp(s.rpm(), 0, 15000); boost = Numbers.clamp(s.boost(), 0, 3); fuel = Numbers.clamp(s.fuel(), 0, 45);
        coolantK = Numbers.clamp(s.coolantK(), 250, 500); oilK = Numbers.clamp(s.oilK(), 250, 500);
        health = Numbers.clamp(s.health(), 0, 100); odometer = Math.max(0, Numbers.finite(s.odometer(), "odometer"));
        wheelAngle = Numbers.finite(s.wheelAngle(), "wheel angle"); gear = Math.max(-1, Math.min(5, s.gear()));
        dtc = java.util.Objects.requireNonNull(s.dtc()); running = s.running() && fuel > 0 && health > 10;
        throttle = 0;
    }
    public record Snapshot(double speed, double yaw, double rpm, double boost, double fuel, double coolantK,
                           double oilK, double health, double odometer, double wheelAngle, boolean running, int gear, String dtc) {}
}
