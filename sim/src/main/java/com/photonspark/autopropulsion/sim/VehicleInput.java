package com.photonspark.autopropulsion.sim;

/** Clutch: 0 engaged, 1 disengaged. Gear: -1 reverse, 0 neutral, 1..5 forward. */
public record VehicleInput(double throttle, double brake, double steer, double clutch, int gear, boolean handbrake) {
    public static final VehicleInput PARKED = new VehicleInput(0, 0, 0, 0, 0, true);
    public VehicleInput {
        throttle = Numbers.clamp(throttle, 0, 1); brake = Numbers.clamp(brake, 0, 1);
        steer = Numbers.clamp(steer, -1, 1); clutch = Numbers.clamp(clutch, 0, 1);
        if (gear < -1 || gear > 5) throw new IllegalArgumentException("gear outside -1..5");
    }
    public static boolean validPacket(float throttle, float brake, float steer, float clutch, int gear) {
        return Float.isFinite(throttle) && throttle >= 0 && throttle <= 1
            && Float.isFinite(brake) && brake >= 0 && brake <= 1
            && Float.isFinite(steer) && steer >= -1 && steer <= 1
            && Float.isFinite(clutch) && clutch >= 0 && clutch <= 1 && gear >= -1 && gear <= 5;
    }
}
