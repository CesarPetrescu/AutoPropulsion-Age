package com.photonspark.autopropulsion.sim;

public final class EngineModel {
    private EngineModel() {}
    public record Sample(double rpm, double torqueNm, double powerKw, double fuelKgS) {}
    public static double efficiency(EngineSpec s) { return 1-Math.pow(s.compression(),-.35); }
    public static Sample sample(EngineSpec s, double rpm, double throttle, double boost) {
        rpm=Numbers.clamp(rpm,0,30000); throttle=Numbers.clamp(throttle,0,1); boost=Numbers.clamp(boost,0,3);
        double chargeTempK=298.15+boost*32;
        double indicated=206*s.litres()*s.ve().at(rpm)*efficiency(s)*(1+boost)*298.15/chargeTempK*s.powerMultiplier()*throttle;
        double friction=10+.002*rpm;
        double torque=Math.max(0,indicated-friction);
        if(rpm>=s.rpmLimit()) torque=0;
        double power=torque*rpm*2*Math.PI/60000;
        // LHV is J/kg; convert kW to W before dividing by efficiency and LHV.
        double etaBrake=efficiency(s)*.58;
        double fuel=Math.max(.00016, power*1000/(etaBrake*43_000_000));
        return new Sample(rpm,torque,power,fuel);
    }
}
