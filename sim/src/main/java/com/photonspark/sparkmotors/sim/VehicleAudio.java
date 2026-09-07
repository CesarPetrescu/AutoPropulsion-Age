package com.photonspark.sparkmotors.sim;

import java.util.*;
import static com.photonspark.sparkmotors.sim.VehicleCondition.Part;

/** Pure mixing policy, shared with matrix tests. All gains and pitches are bounded. */
public final class VehicleAudio {
    private VehicleAudio() {}
    public record Voice(String event,float volume,float pitch) {
        public Voice { volume=(float)VehicleCondition.bounded(volume,0,1);pitch=(float)VehicleCondition.bounded(pitch,.5,2); }
    }
    public static final String[] AUXILIARY={"turbo","supercharger","tire_roll","tire_skid","brake_squeal","brake_grind","damage_rattle","intake",
        "starter","engine_stop","shift","bov","impact","tire_burst","engine_failure","part_break"};
    public static List<String> events(){
        var names=new ArrayList<String>();
        for(var family:EngineFamily.values())for(String exhaust:new String[]{"stock","sport","open"})for(String band:new String[]{"low","high"})
            names.add("engine_"+family.id+"_"+exhaust+"_"+band);
        names.addAll(List.of(AUXILIARY));return List.copyOf(names);
    }
    public static Map<String,Voice> mix(EngineFamily family,int config,int parts,VehicleCondition condition,
                                       boolean running,boolean grounded,double rpm,double throttle,double speed,double boost,double slip,boolean braking) {
        Objects.requireNonNull(family);Objects.requireNonNull(condition);
        rpm=VehicleCondition.bounded(rpm,0,10000);throttle=VehicleCondition.bounded(throttle,0,1);
        speed=VehicleCondition.bounded(Math.abs(speed),0,100);boost=VehicleCondition.bounded(boost,0,2);slip=VehicleCondition.bounded(slip,0,1);
        var voices=new LinkedHashMap<String,Voice>();
        boolean engine=running && Assembly.ENGINE.variant(config)>0 && EnginePart.ready(parts) && condition.effects(config,parts).canRun();
        int exhaust=Assembly.EXHAUST.variant(config);
        String tone=exhaust==0||condition.health(Part.MUFFLER)<15?"open":exhaust==2?"sport":"stock";
        double gain=tone.equals("stock")?.38:tone.equals("sport")?.62:.82;
        double high=VehicleCondition.bounded((rpm-2400)/1800,0,1);
        if(engine){
            double load=.45+.55*throttle;
            put(voices,"low","engine_"+family.id+"_"+tone+"_low",gain*load*Math.sqrt(1-high),rpm/1800);
            put(voices,"high","engine_"+family.id+"_"+tone+"_high",gain*load*Math.sqrt(high),rpm/4200);
            int induction=EnginePart.INDUCTION.variant(parts);
            double efficiency=condition.effects(config,parts).boost();
            if(induction==1)put(voices,"induction","turbo",.48*boost*efficiency,.65+boost*1.5);
            if(induction==2)put(voices,"induction","supercharger",(.055+.18*throttle)*Math.min(1,rpm/4000)*efficiency,.5+rpm/4800);
            double intake=EnginePart.INTAKE.variant(parts)==2?.16:.045;
            if(Assembly.ENGINE.variant(config)==2)intake*=1.35;
            put(voices,"intake","intake",throttle*intake, .7+rpm/6000);
        }
        if(grounded&&speed>.15&&Assembly.WHEELS.variant(config)>0){
            put(voices,"roll","tire_roll",Math.min(.24,speed*.005),.5+speed/45);
            put(voices,"skid","tire_skid",slip*Math.min(.5,speed/35),.8+speed/100);
        }
        double brakeHealth=condition.effects(config,parts).braking();
        if(grounded&&braking&&speed>.5&&Assembly.BRAKES.variant(config)>0){
            boolean grind=brakeHealth<.25;
            double gainBrake=grind?.42:(Assembly.BRAKES.variant(config)==2?.10:.06)+(1-brakeHealth)*.16;
            put(voices,"brake",grind?"brake_grind":"brake_squeal",gainBrake*Math.min(1,speed/8),.8+speed/90);
        }
        double damage=1-condition.average(config,parts)/100;
        if((grounded&&speed>.5)||engine)put(voices,"damage","damage_rattle",damage*Math.min(.38,.1+speed/80),.7+Math.min(speed/45,.8));
        return Collections.unmodifiableMap(voices);
    }
    private static void put(Map<String,Voice> out,String layer,String sound,double volume,double pitch){out.put(layer,new Voice(sound,(float)volume,(float)pitch));}
}
