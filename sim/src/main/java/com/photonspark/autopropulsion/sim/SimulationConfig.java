package com.photonspark.autopropulsion.sim;
public record SimulationConfig(int substeps, double fuelMultiplier, double damageMultiplier) {
    public SimulationConfig {
        if(substeps<1||substeps>16) throw new IllegalArgumentException("substeps must be 1..16");
        if(Numbers.finite(fuelMultiplier,"fuelMultiplier")<0||fuelMultiplier>10) throw new IllegalArgumentException("fuel multiplier 0..10");
        if(Numbers.finite(damageMultiplier,"damageMultiplier")<0||damageMultiplier>10) throw new IllegalArgumentException("damage multiplier 0..10");
    }
    public static SimulationConfig defaults() { return new SimulationConfig(4,1,1); }
}
