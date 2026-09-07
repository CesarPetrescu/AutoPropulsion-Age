package com.photonspark.sparkmotors.sim;

import java.util.*;
import java.util.function.Predicate;

/** Deterministic, server-owned component state. Values are percent, time is seconds.
 * Gameplay calibration, not a finite-element crash or tire simulation.
 * Stable IDs, not enum ordinals, are used in saves and item data.
 */
public final class VehicleCondition {
    public enum Part {
        FRAME("frame", "Chassis", null, null, 8),
        FRONT_BODY("front_body", "Front body", Assembly.BODY, null, 2),
        REAR_BODY("rear_body", "Rear body", Assembly.BODY, null, 2),
        LEFT_BODY("left_body", "Left body", Assembly.BODY, null, 2),
        RIGHT_BODY("right_body", "Right body", Assembly.BODY, null, 2),
        ENGINE_BLOCK("engine_block", "Engine block", Assembly.ENGINE, null, 8),
        INTAKE("intake", "Intake", Assembly.ENGINE, EnginePart.INTAKE, 2),
        FUEL_SYSTEM("fuel_system", "Fuel system", Assembly.ENGINE, EnginePart.FUEL, 3),
        IGNITION("ignition", "Ignition", Assembly.ENGINE, EnginePart.IGNITION, 2),
        COOLING("cooling", "Cooling system", Assembly.ENGINE, EnginePart.COOLING, 3),
        INTERNALS("internals", "Engine internals", Assembly.ENGINE, EnginePart.INTERNALS, 6),
        INDUCTION("induction", "Turbo / supercharger", Assembly.ENGINE, EnginePart.INDUCTION, 4),
        TRANSMISSION("transmission", "Transmission", Assembly.TRANSMISSION, null, 5),
        TIRE_FL("tire_fl", "Front left tire", Assembly.WHEELS, null, 1),
        TIRE_FR("tire_fr", "Front right tire", Assembly.WHEELS, null, 1),
        TIRE_RL("tire_rl", "Rear left tire", Assembly.WHEELS, null, 1),
        TIRE_RR("tire_rr", "Rear right tire", Assembly.WHEELS, null, 1),
        BRAKE_FL("brake_fl", "Front left brake", Assembly.BRAKES, null, 2),
        BRAKE_FR("brake_fr", "Front right brake", Assembly.BRAKES, null, 2),
        BRAKE_RL("brake_rl", "Rear left brake", Assembly.BRAKES, null, 2),
        BRAKE_RR("brake_rr", "Rear right brake", Assembly.BRAKES, null, 2),
        SUSPENSION_FL("suspension_fl", "Front left suspension", Assembly.SUSPENSION, null, 3),
        SUSPENSION_FR("suspension_fr", "Front right suspension", Assembly.SUSPENSION, null, 3),
        SUSPENSION_RL("suspension_rl", "Rear left suspension", Assembly.SUSPENSION, null, 3),
        SUSPENSION_RR("suspension_rr", "Rear right suspension", Assembly.SUSPENSION, null, 3),
        MUFFLER("muffler", "Exhaust / muffler", Assembly.EXHAUST, null, 2);

        public final String id, title;
        public final Assembly assembly;
        public final EnginePart enginePart;
        public final int repairCost;
        Part(String id, String title, Assembly assembly, EnginePart enginePart, int cost) {
            this.id=id; this.title=title; this.assembly=assembly; this.enginePart=enginePart; this.repairCost=cost;
        }
        public boolean installed(int config, int parts) {
            if (assembly != null && assembly.variant(config)==0) return false;
            return enginePart==null || enginePart.variant(parts)>0;
        }
        public static Optional<Part> byId(String id) {
            return Arrays.stream(values()).filter(p->p.id.equals(id)).findFirst();
        }
    }
    public enum Zone { FRONT, REAR, LEFT, RIGHT, UNDERBODY }
    public record State(double wear, double damage) {
        public State { wear=bounded(wear,0,100); damage=bounded(damage,0,100); }
        public double health() { return Math.max(0,100-wear-damage); }
    }
    public record Effects(double power, double braking, double grip, double steering, double pull,
                          double cooling, double boost, boolean canRun) {}
    private final EnumMap<Part,State> states = new EnumMap<>(Part.class);
    public VehicleCondition() { for (Part p:Part.values()) states.put(p,new State(0,0)); }
    public State state(Part p) { return states.get(Objects.requireNonNull(p)); }
    public double health(Part p) { return state(p).health(); }
    public void restore(Part p,double wear,double damage) { states.put(p,new State(wear,damage)); }
    public void repair(Part p) { restore(p,0,0); }
    public void repair(Predicate<Part> filter) { for(Part p:Part.values()) if(filter.test(p)) repair(p); }
    public void copyFrom(VehicleCondition other,Predicate<Part> filter) {
        for(Part p:Part.values()) if(filter.test(p)) states.put(p,other.state(p));
    }
    public void damage(Part p,double amount) {
        if (!Double.isFinite(amount) || amount<=0) return;
        State s=state(p); restore(p,s.wear,s.damage+amount);
    }
    private void wear(Part p,double amount) {
        if (!Double.isFinite(amount) || amount<=0) return;
        State s=state(p); restore(p,s.wear+amount,s.damage);
    }
    public double average(int config,int parts) {
        return Arrays.stream(Part.values()).filter(p->p.installed(config,parts)).mapToDouble(this::health).average().orElse(100);
    }
    public String problem(int config,int parts) {
        for(Part p:new Part[]{Part.FRAME,Part.ENGINE_BLOCK,Part.INTERNALS,Part.FUEL_SYSTEM,Part.IGNITION})
            if(p.installed(config,parts) && health(p)<=0) return p.title+" has failed. Repair or replace it.";
        return "";
    }
    public Effects effects(int config,int parts) {
        double engine=Math.min(factor(Part.ENGINE_BLOCK),Math.min(factor(Part.INTERNALS),Math.min(factor(Part.FUEL_SYSTEM),factor(Part.IGNITION))));
        double brakes=mean(Part.BRAKE_FL,Part.BRAKE_FR,Part.BRAKE_RL,Part.BRAKE_RR);
        double tires=mean(Part.TIRE_FL,Part.TIRE_FR,Part.TIRE_RL,Part.TIRE_RR);
        double suspension=mean(Part.SUSPENSION_FL,Part.SUSPENSION_FR,Part.SUSPENSION_RL,Part.SUSPENSION_RR);
        double left=(factor(Part.TIRE_FL)+factor(Part.TIRE_RL)+factor(Part.SUSPENSION_FL)+factor(Part.SUSPENSION_RL))/4;
        double right=(factor(Part.TIRE_FR)+factor(Part.TIRE_RR)+factor(Part.SUSPENSION_FR)+factor(Part.SUSPENSION_RR))/4;
        return new Effects(engine*factor(Part.TRANSMISSION)*(.65+.35*factor(Part.INTAKE)), brakes,
            .12+.88*tires, .2+.8*suspension, (left-right)*.16,
            factor(Part.COOLING), EnginePart.INDUCTION.variant(parts)==0?0:factor(Part.INDUCTION),problem(config,parts).isEmpty());
    }
    private double factor(Part p) { return health(p)/100; }
    private double mean(Part... parts) { double sum=0; for(Part p:parts) sum+=factor(p); return sum/parts.length; }
    /** Legacy global health is distributed conservatively, without resurrecting a wreck. */
    public void migrateLegacy(double health,int config,int parts) {
        for(Part p:Part.values()) if(p.installed(config,parts)) restore(p,0,100-bounded(health,0,100));
    }
    /** Returns only NEW failures, so a failed part cannot emit a break sound every tick. */
    public EnumSet<Part> impact(Zone zone,double deltaV,int config,int parts) {
        EnumSet<Part> was=failed();
        double severity=bounded((deltaV*deltaV-9)*.15,0,130);
        if(!Double.isFinite(deltaV)||deltaV<=3) return EnumSet.noneOf(Part.class);
        hit(Part.FRAME,severity*.26,config,parts);
        switch(zone) {
            case FRONT -> { hit(Part.FRONT_BODY,severity,config,parts); hit(Part.COOLING,severity*.75,config,parts);
                hit(Part.ENGINE_BLOCK,severity*.35,config,parts); hit(Part.INTAKE,severity*.35,config,parts);
                corner(0,severity*.30,config,parts); corner(1,severity*.30,config,parts); }
            case REAR -> { hit(Part.REAR_BODY,severity,config,parts); hit(Part.MUFFLER,severity*.8,config,parts);
                corner(2,severity*.35,config,parts); corner(3,severity*.35,config,parts); }
            case LEFT -> { hit(Part.LEFT_BODY,severity,config,parts); corner(0,severity*.8,config,parts); corner(2,severity*.8,config,parts); }
            case RIGHT -> { hit(Part.RIGHT_BODY,severity,config,parts); corner(1,severity*.8,config,parts); corner(3,severity*.8,config,parts); }
            case UNDERBODY -> { for(int i=0;i<4;i++) corner(i,severity*.7,config,parts); hit(Part.TRANSMISSION,severity*.3,config,parts); }
        }
        EnumSet<Part> now=failed(); now.removeAll(was); return now;
    }
    private void hit(Part p,double amount,int config,int parts) { if(p.installed(config,parts)) damage(p,amount); }
    private void corner(int corner,double amount,int config,int parts) {
        hit(Part.values()[Part.TIRE_FL.ordinal()+corner],amount*.55,config,parts);
        hit(Part.values()[Part.BRAKE_FL.ordinal()+corner],amount*.25,config,parts);
        hit(Part.values()[Part.SUSPENSION_FL.ordinal()+corner],amount,config,parts);
    }
    public EnumSet<Part> failed() {
        EnumSet<Part> out=EnumSet.noneOf(Part.class); for(Part p:Part.values()) if(health(p)<=0) out.add(p); return out;
    }
    /** Slow normal wear; heat, load and tire slip increase it. Parked/off cars never age. */
    public void step(int config,int parts,boolean running,double rpm,double throttle,double speed,
                     boolean braking,double temperature,double slip,double dt) {
        dt=bounded(dt,0,.25); throttle=bounded(throttle,0,1); rpm=bounded(rpm,0,20000);
        speed=bounded(Math.abs(speed),0,100); temperature=bounded(temperature,20,200); slip=bounded(slip,0,1);
        if(dt==0) return;
        for(Part p:Part.values()) {
            if(!p.installed(config,parts)||health(p)<=0) continue;
            double rate=0;
            if(p.assembly==Assembly.WHEELS) rate=speed*.00004+slip*speed*.0012;
            if(p.assembly==Assembly.BRAKES && braking) rate=speed*.0005;
            if(p.assembly==Assembly.SUSPENSION) rate=speed*.000025;
            if(p==Part.TRANSMISSION && running) rate=throttle*rpm*.0000006;
            if(p.assembly==Assembly.ENGINE && running) {
                rate=.00012 + throttle*rpm*.00000015;
                if(p==Part.INTERNALS || p==Part.ENGINE_BLOCK) {
                    rate+=Math.max(0,temperature-110)*.025;
                    if(temperature<50) rate+=throttle*Math.max(0,rpm-3500)*.000004;
                }
                if(p==Part.INDUCTION) rate*=1+throttle*2;
                if(p==Part.COOLING) rate=.00012;
            }
            if(p==Part.MUFFLER && running) rate=.00008+throttle*.00008;
            int grade=p.enginePart!=null?p.enginePart.variant(parts):p.assembly==null?1:p.assembly.variant(config);
            if(grade==2 && p.assembly!=Assembly.WHEELS && p.assembly!=Assembly.BODY) rate*=.8;
            wear(p,rate*dt);
        }
    }
    public static double bounded(double value,double min,double max) {
        return Double.isFinite(value)?Math.max(min,Math.min(max,value)):min;
    }
}
