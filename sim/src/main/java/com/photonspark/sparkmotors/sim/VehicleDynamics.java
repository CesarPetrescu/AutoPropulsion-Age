package com.photonspark.sparkmotors.sim;

/** Longitudinal driveline and bicycle steering model. World contact is supplied by the game. */
public final class VehicleDynamics {
    public static final double MASS = 1280, WHEEL_RADIUS = .34, WHEELBASE = 2.65;
    private static final double[] GEARS = {3.6, 2.1, 1.4, 1.05, .82};
    public record Input(double throttle, double steer, boolean brake, boolean reverse) {
        public Input { throttle = clamp(throttle, 0, 1); steer = clamp(steer, -1, 1); }
    }
    public record Setup(int config, int limiter, double finalDrive, EngineFamily family, int engineParts, double temperature) {
        public Setup(int config,int limiter,double finalDrive){this(config,limiter,finalDrive,EngineFamily.I4,EnginePart.stock(),90);}
        public Setup { config = Assembly.sanitize(config); limiter = Math.clamp(limiter, 4000, 7000); finalDrive = clamp(finalDrive, 2.8, 4.8); family=family==null?EngineFamily.I4:family;engineParts=EnginePart.sanitize(engineParts);temperature=clamp(temperature,20,150); }
    }
    public record State(double speed, double rpm, int gear, double fuel, double yawDelta, double fuelUsed) {}

    public static State step(double speed, double fuel, boolean ignition, Input in, Setup setup, double grip, boolean grounded, double dt) {
        if (!Double.isFinite(speed)) speed = 0;
        speed = clamp(speed, -14, 65);
        fuel = clamp(fuel, 0, 50);
        double rpm = 0, yaw = 0, used = 0;
        int gear = in.reverse ? -1 : 1;
        double shiftRpm = Math.min(5800, setup.limiter - 250);
        double finalRatio = setup.finalDrive * (Assembly.TRANSMISSION.variant(setup.config) == 2 ? 1.10 : 1);
        if (!in.reverse) {
            while (gear < 5 && Math.abs(speed) / WHEEL_RADIUS * GEARS[gear - 1] * finalRatio * 60 / (2 * Math.PI) > shiftRpm) gear++;
        }
        double ratio = (in.reverse ? 3.4 : GEARS[gear - 1]) * finalRatio;
        boolean running = ignition && fuel > 0 && Assembly.ENGINE.variant(setup.config) > 0 && EnginePart.ready(setup.engineParts);
        boolean drive = running && Assembly.canDrive(setup.config);
        double force = 0;
        if (running) {
            rpm = Math.max(850, Math.abs(speed) / WHEEL_RADIUS * ratio * 60 / (2 * Math.PI));
            if (Math.abs(speed) < 2) rpm = Math.max(rpm, 850 + in.throttle * 1300);
            rpm = Math.min(rpm, setup.limiter);
            used = (.00022 + in.throttle * rpm / 6000 * .0055) * setup.family.fuelScale * (1+EngineBuild.boost(rpm,setup.engineParts)*in.throttle*.8) * dt;
        }
        if (drive && grounded && (!in.reverse || Math.abs(speed) < 13)) {
            force = EngineBuild.torque(rpm,setup.family,Assembly.ENGINE.variant(setup.config),setup.engineParts,setup.limiter,setup.temperature) * ratio * .86 / WHEEL_RADIUS * in.throttle * (in.reverse ? -1 : 1);
            double tireGrip = grip * (Assembly.WHEELS.variant(setup.config) == 2 ? 1.17 : 1);
            force = clamp(force, -MASS * 9.81 * tireGrip * .56, MASS * 9.81 * tireGrip * .56);
        }
        double drag = Assembly.BODY.variant(setup.config) == 2 ? .40 : .43;
        double resistance = drag * speed * Math.abs(speed) + Math.signum(speed) * (grounded ? 140 : 0);
        if (in.brake) resistance += Math.signum(speed) * MASS * (Assembly.BRAKES.variant(setup.config) == 2 ? 10.8 : 8.0) * grip;
        if (!drive) resistance += Math.signum(speed) * 160;
        double next = speed + (force - resistance) / MASS * dt;
        if (Math.signum(next) != Math.signum(speed) && Math.abs(force) < Math.abs(resistance)) next = 0;
        if (!Assembly.canDrive(setup.config)) next *= Math.exp(-5 * dt);
        if (grounded) {
            double response = Assembly.SUSPENSION.variant(setup.config) == 2 ? 1.08 : 1;
            double steering = in.steer * .49 * response / (1 + Math.abs(next) * .045);
            yaw = next / WHEELBASE * Math.tan(steering) * dt * Math.min(1, grip);
        }
        return new State(clamp(next, -14, 65), rpm, gear, Math.max(0, fuel - used), yaw, used);
    }
    public static double clamp(double v, double min, double max) { return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : min; }
}
