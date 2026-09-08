package com.photonspark.sparkmotors.sim.electric;

import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Lumped Thevenin pack: chemical energy, SOC-dependent OCV, I^2 R and a thermal mass.
 * Positive terminal power DISCHARGES the pack. Charging is negative. All exchanges use seconds/joules.
 * Not an electrochemical cell solver; pack constants and cycle-age law are gameplay calibrations.
 */
public final class BatteryModel {
    public static final double JOULES_PER_KWH=3_600_000;
    private BatteryModel(){}
    public record Spec(double kWh,double nominalV,double resistanceOhm,double dischargeA,double chargeA,double massKg){
        public Spec {
            for(double v:new double[]{kWh,nominalV,resistanceOhm,dischargeA,chargeA,massKg})
                if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException("Positive finite battery specification required");
        }
        public double capacityJ(){return kWh*JOULES_PER_KWH;}
    }
    public record State(double energyJ,double temperatureC,double health,double throughputJ){
        public State {
            energyJ=clamp(energyJ,0,1e10);temperatureC=clamp(temperatureC,-60,200);
            health=clamp(health,0,1);throughputJ=clamp(throughputJ,0,1e18);
        }
        public static State initial(Spec spec,double soc){return new State(spec.capacityJ()*clamp(soc,0,1),20,1,0);}
        public double capacityJ(Spec spec){return spec.capacityJ()*health;}
        public double soc(Spec spec){return clamp(energyJ/Math.max(1,capacityJ(spec)),0,1);}
        public State normalized(Spec spec){return new State(Math.min(energyJ,capacityJ(spec)),temperatureC,health,throughputJ);}
    }
    /** terminalJ + ohmicJ + fadeJ = old chemical energy - new chemical energy. */
    public record Exchange(State state,double terminalJ,double currentA,double voltageV,double ohmicJ,double fadeJ){}
    public static double ocv(Spec spec,State s){return spec.nominalV*(.84+.24*s.soc(spec));}
    public static double resistance(Spec spec,State s){
        return spec.resistanceOhm*(1+Math.max(0,20-s.temperatureC)*.035+3*(1-s.health));
    }
    public static double dischargeLimitW(Spec spec,State s,double dt){
        validDt(dt);double v=ocv(spec,s),r=resistance(spec,s);
        double thermal=clamp((65-s.temperatureC)/15,0,1)*clamp((s.temperatureC+30)/30,.1,1);
        double current=Math.min(spec.dischargeA*thermal*s.health*clamp(s.soc(spec)/.04,0,1),v/(2*r));
        current=Math.min(current,s.energyJ/(v*dt));
        return Math.max(0,v*current-r*current*current);
    }
    public static double chargeLimitW(Spec spec,State s,double dt){
        validDt(dt);if(s.temperatureC<=0||s.temperatureC>=55||s.health<.1)return 0;
        double v=ocv(spec,s),r=resistance(spec,s);
        double thermal=clamp(s.temperatureC/15,0,1)*clamp((55-s.temperatureC)/10,0,1);
        double current=spec.chargeA*thermal*s.health*clamp((1-s.soc(spec))/.2,0,1);
        current=Math.min(current,Math.max(0,s.capacityJ(spec)-s.energyJ)/(v*dt));
        return v*current+r*current*current;
    }
    public static Exchange exchange(Spec spec,State original,double terminalW,double dt,double ambientC,double speed){
        return exchange(spec,original,terminalW,dt,ambientC,speed,true);
    }
    /** External charging shares the vehicle's thermal clock: ohmic heat only when passiveCooling=false. */
    public static Exchange exchange(Spec spec,State original,double terminalW,double dt,double ambientC,double speed,boolean passiveCooling){
        validDt(dt);State s=original.normalized(spec);
        double v=ocv(spec,s),r=resistance(spec,s),p=clamp(terminalW,-1e7,1e7);
        p=clamp(p,-chargeLimitW(spec,s,dt),dischargeLimitW(spec,s,dt));
        // Stable quadratic roots, including tiny power near the end of a charge.
        double current=p>=0?2*p/(v+Math.sqrt(Math.max(0,v*v-4*r*p))):
            -2*(-p)/(v+Math.sqrt(v*v+4*r*(-p)));
        double ohmic=current*current*r*dt,chemical=v*current*dt;
        double throughput=s.throughputJ+Math.abs(chemical);
        // 20% capacity loss per 1500 full equivalent cycles, accelerated above 40 C.
        double wear=Math.abs(chemical)/(spec.capacityJ()*2*1500)*.2*(1+Math.max(0,s.temperatureC-40)*.08);
        double health=Math.max(0,s.health-wear);
        double energy=clamp(s.energyJ-chemical,0,s.capacityJ(spec));
        double fade=Math.max(0,energy-spec.capacityJ()*health);
        energy-=fade;
        double thermalMass=spec.massKg*900;
        double conductance=8+2*clamp(Math.abs(speed),0,65);
        double ambient=clamp(ambientC,-50,60),equilibrium=ambient+ohmic/dt/conductance;
        double temperature=passiveCooling?equilibrium+(s.temperatureC-equilibrium)*Math.exp(-conductance*dt/thermalMass):s.temperatureC+ohmic/thermalMass;
        return new Exchange(new State(energy,temperature,health,throughput),p*dt,current,v-current*r,ohmic,fade);
    }
    /** Heater energy is supplied separately by the charger, never created in the pack. */
    public static State addHeat(Spec spec,State s,double joules){
        return new State(s.energyJ,s.temperatureC+clamp(joules,0,1e8)/(spec.massKg*900),s.health,s.throughputJ);
    }
    private static void validDt(double dt){if(!Double.isFinite(dt)||dt<=0||dt>.25)throw new IllegalArgumentException("dt must be in (0, 0.25] seconds");}
}
