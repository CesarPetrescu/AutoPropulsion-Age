package com.photonspark.sparkmotors.sim.electric;

import com.photonspark.sparkmotors.sim.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Calibrated parallel hybrid supervisor, not a manufacturer ECU map. */
public final class HybridControl {
    public record Demand(boolean engineOn,double pedal,double assist,double chargeW,double reserve,boolean disconnect) {}
    private HybridControl() {}
    public static double reserve(Powertrain type,ElectricDynamics.Mode mode){
        return mode==ElectricDynamics.Mode.ELECTRIC_ONLY?.04:mode==ElectricDynamics.Mode.CHARGE_SUSTAIN?.45:type==Powertrain.HYBRID?.35:.12;
    }
    public static Demand demand(Powertrain type,ElectricDynamics.Mode mode,boolean wasOn,double soc,double speed,
                                EnginePhysics.State engine,VehicleDynamics.Input input,boolean available){
        double reserve=reserve(type,mode),low=reserve+.10,high=reserve+.20;
        double velocity=Math.abs(speed);
        boolean permitted=mode!=ElectricDynamics.Mode.ELECTRIC_ONLY&&available;
        // Finish within half a percentage point of target: a taper toward exact equality
        // would otherwise settle at auxiliary demand and keep an unloaded engine running.
        boolean on=permitted&&(soc<(wasOn?high-.005:low)||velocity>(wasOn?12:16)||input.throttle()>(wasOn?.30:.65));
        boolean charging=on&&soc<high-.005;
        // High-speed wheel drive belongs to the engine. Smoothly taper assistance between
        // 50 and 80 km/h, retaining low-speed launch and shift-fill assistance above reserve.
        double assist=on&&engine.mode()==EnginePhysics.Mode.RUNNING?clamp((22-velocity)/8,0,1):1;
        assist*=clamp((soc-reserve)/.08,0,1);
        boolean disconnect=!on||(velocity<3&&soc>reserve+.02)||input.brake()||input.clutch();
        double charge=charging?(type==Powertrain.HYBRID?8000:12000)*clamp((high-soc)/.06,0,1):0;
        double pedal=input.throttle();
        if(!on)pedal=0;
        return new Demand(on,pedal,assist,charge,reserve,disconnect);
    }
}
