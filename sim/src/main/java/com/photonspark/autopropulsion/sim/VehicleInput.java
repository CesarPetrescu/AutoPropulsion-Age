package com.photonspark.autopropulsion.sim;

public record VehicleInput(double throttle, double brake, double steer, boolean clutch, boolean handbrake) {
    public static final VehicleInput PARKED = new VehicleInput(0,1,0,true,true);
    public static final VehicleInput IDLE = new VehicleInput(0,0,0,false,false);
    public VehicleInput {
        throttle=Numbers.clamp(throttle,0,1); brake=Numbers.clamp(brake,0,1); steer=Numbers.clamp(steer,-1,1);
    }
}
