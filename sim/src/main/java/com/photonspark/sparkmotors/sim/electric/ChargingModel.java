package com.photonspark.sparkmotors.sim.electric;

import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Grid-side voltage is separate from the regulated vehicle-side DC voltage. */
public final class ChargingModel {
    private ChargingModel(){}
    public enum Tier {
        LV("48 V workshop",48,20,.90,false),
        HOME("240 V wallbox",240,30,.93,false),
        RAPID("480 V rapid",480,125,.95,true),
        ULTRA("3200 V ultra",3200,100,.96,true);
        public final String title;
        public final double inputV,maxA,efficiency;
        public final boolean dc;
        Tier(String title,double inputV,double maxA,double efficiency,boolean dc){
            this.title=title;this.inputV=inputV;this.maxA=maxA;this.efficiency=efficiency;this.dc=dc;
        }
        public double inputLimitW(){return inputV*maxA;}
        public String id(){return name().toLowerCase(java.util.Locale.ROOT)+"_charger";}
    }
    public enum Status { UNPLUGGED, INCOMPATIBLE, INTERLOCK, NO_POWER, UNDERVOLTAGE, OVERVOLTAGE, COLD, HOT, COMPLETE, CHARGING, PREHEATING }
    public record Result(BatteryModel.State battery,double inputJ,double storedJ,double lossJ,double heaterJ,Status status){}
    public static Result step(Powertrain type,BatteryModel.State battery,Tier tier,double suppliedV,double availableJ,
                              double targetSoc,boolean connected,boolean ready,double speed,double dt,double ambientC){
        if(!Double.isFinite(dt)||dt<=0||dt>.25)throw new IllegalArgumentException("Invalid charge timestep");
        if(!connected)return idle(battery,Status.UNPLUGGED);
        if(!type.plugIn()||tier.dc&&type.dcChargeKw<=0)return idle(battery,Status.INCOMPATIBLE);
        if(ready||Math.abs(speed)>.1)return idle(battery,Status.INTERLOCK);
        if(!Double.isFinite(suppliedV)||suppliedV<=0||!Double.isFinite(availableJ)||availableJ<=0)return idle(battery,Status.NO_POWER);
        if(suppliedV>tier.inputV*1.10)return idle(battery,Status.OVERVOLTAGE);
        if(suppliedV<tier.inputV*.80)return idle(battery,Status.UNDERVOLTAGE);
        var spec=type.battery;var b=battery.normalized(spec);
        if(b.temperatureC()>=55||b.health()<.1)return idle(b,Status.HOT);
        double target=clamp(targetSoc,.5,1);
        if(b.soc(spec)>=target-.00001)return idle(b,Status.COMPLETE);
        double inputW=Math.min(availableJ/dt,Math.min(tier.inputLimitW(),suppliedV*tier.maxA));
        // Cold packs use real supplied energy for preconditioning; no cold-cell charging.
        if(b.temperatureC()<5){
            double heaterW=Math.min(2000,inputW*.95);
            double input=heaterW/.95*dt;
            return new Result(BatteryModel.addHeat(spec,b,heaterW*dt),input,0,input-heaterW*dt,heaterW*dt,Status.PREHEATING);
        }
        double vehicleLimit=(tier.dc?type.dcChargeKw:type.onboardChargerKw)*1000;
        double packW=Math.min(inputW*tier.efficiency,Math.min(vehicleLimit,BatteryModel.chargeLimitW(spec,b,dt)));
        // Respect the chosen charge ceiling without discarding accepted energy afterwards.
        double room=Math.max(0,target*b.capacityJ(spec)-b.energyJ());
        double v=BatteryModel.ocv(spec,b),r=BatteryModel.resistance(spec,b),i=room/(v*dt);
        packW=Math.min(packW,v*i+r*i*i);
        var exchange=BatteryModel.exchange(spec,b,-packW,dt,ambientC,0);
        double input=-exchange.terminalJ()/tier.efficiency,stored=exchange.state().energyJ()-b.energyJ();
        return new Result(exchange.state(),input,stored,input-stored,0,input>0?Status.CHARGING:Status.COLD);
    }
    private static Result idle(BatteryModel.State battery,Status status){return new Result(battery,0,0,0,0,status);}
}
