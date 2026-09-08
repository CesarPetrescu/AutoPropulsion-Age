package com.photonspark.sparkmotors.sim;

import java.util.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Explicit layer mixer. Each voice is concurrent; sounds.json alternatives are never used as RPM layers. */
public final class VehicleAudio {
    public record Voice(String sound,double gain,double pitch,int corner) {}
    public record Input(EngineFamily family,EnginePhysics.Mode mode,double rpm,double throttle,double load,double spool,double boost,double speed,
                        int induction,boolean brake,boolean handbrake,boolean rough,WheelDynamics.State wheels,MechanicalState mechanics) {}
    public static Set<String> assets(){
        var names=new LinkedHashSet<String>();for(var family:EngineFamily.values())for(String band:List.of("idle","low","high","coast"))names.add(family.id+"_"+band);
        names.addAll(List.of("intake","exhaust_stock","exhaust_sport","exhaust_open","turbo","centrifugal","roots","twin_screw","boost_leak","road","gravel","tire_slip","brake_grind","bearing","knock","starter","release","latch","impact","horn"));return Set.copyOf(names);
    }
    private static void add(Map<String,Voice> mix,String key,String sound,double gain,double pitch,int corner){if(gain>.002)mix.put(key,new Voice(sound,clamp(gain,0,.55),clamp(pitch,.5,2),corner));}
    public static Map<String,Voice> mix(Input s){
        var result=new LinkedHashMap<String,Voice>();var m=s.mechanics;boolean running=s.mode==EnginePhysics.Mode.RUNNING;
        double load=clamp(s.load,0,1),rpm=s.rpm;
        if(s.mode==EnginePhysics.Mode.CRANKING)add(result,"starter","starter",.18,rpm/220,-1);
        if(running){
            double idle=clamp((2200-rpm)/1300,0,1),high=clamp((rpm-3400)/2200,0,1),low=Math.max(0,1-idle-high);
            add(result,"idle",s.family.id+"_idle",idle*.19,rpm/900,-1);
            add(result,"low",s.family.id+"_low",low*(.06+.18*load),rpm/3000,-1);
            add(result,"high",s.family.id+"_high",high*(.04+.20*load),rpm/5800,-1);
            add(result,"coast",s.family.id+"_coast",(1-idle)*(1-load)*.09,rpm/3000,-1);
            var muffler=m.get("exhaust.muffler");var pipe=m.get("exhaust.pipe");
            String exhaust=muffler==null||pipe==null||CircuitPhysics.leak(pipe,1)>.3?"exhaust_open":muffler.item().equals("sport_muffler")?"exhaust_sport":"exhaust_stock";
            add(result,"exhaust",exhaust,(.025+load*.11)*(exhaust.equals("exhaust_stock")?.6:1),rpm/3000,-1);
            add(result,"intake","intake",s.throttle*(.018+.05*load)*m.capability("engine.intake"),rpm/3500,-1);
            if(s.induction==1||s.induction==3||s.induction==4)add(result,"induction","turbo",s.spool*s.spool*.13*m.capability("engine.induction"),.6+s.spool*1.35,-1);
            else if(s.induction>0){String sound=s.induction==2?"centrifugal":s.induction==5?"roots":"twin_screw";add(result,"induction",sound,(.02+s.boost*.10)*m.capability("induction.belt")*m.capability("engine.induction"),rpm/3500,-1);}
            add(result,"leak","boost_leak",s.boost*CircuitPhysics.leak(m.get("induction.pipe"),1)*.2,1,-1);
            var internal=m.get("engine.internals");if(internal!=null)add(result,"knock","knock",Math.max(0,internal.wear()-.65)*.18+((internal.faults()&PartInstance.MISFIRE)!=0?.08:0),rpm/3000,-1);
        }
        for(int c=0;c<4;c++){
            var wheel=s.wheels.corners().get(c);String prefix="wheel."+ComponentSlot.CORNERS[c]+".";double speed=Math.abs(wheel.omega()*.34);
            if(!wheel.contact()||Math.abs(s.speed)<.25||m.get(prefix+"tire")==null)continue;
            add(result,"road"+c,s.rough?"gravel":"road",Math.min(.055,speed*.002),.65+speed/40,c);
            add(result,"slip"+c,"tire_slip",wheel.slip()*Math.min(1,speed/6)*.09,1+wheel.slip()*.2,c);
            var pad=m.get(prefix+"pad");boolean braking=(s.brake&&WheelDynamics.brakeCapability(m,c,true)>0)||(s.handbrake&&c>=2&&WheelDynamics.brakeCapability(m,c,false)>0);
            if(pad!=null&&braking)add(result,"brake"+c,"brake_grind",Math.max(0,pad.wear()-.9)*1.5,.8+speed/45,c);
            var bearing=m.get(prefix+"bearing");if(bearing!=null)add(result,"bearing"+c,"bearing",(bearing.damage()+bearing.wear())*Math.min(.06,speed*.003),.7+speed/40,c);
        }
        return Map.copyOf(result);
    }
}
