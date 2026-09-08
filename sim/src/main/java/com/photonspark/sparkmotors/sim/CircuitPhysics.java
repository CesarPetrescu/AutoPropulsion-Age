package com.photonspark.sparkmotors.sim;

import java.util.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Deliberately bounded workshop-scale heat and mass balance, in litres, seconds and degrees C. */
public final class CircuitPhysics {
    public record Measurements(double coolantLeak,double coolantPressure,double circulation,boolean fan,double oilPressure,double voltage) {}
    public static double leak(PartInstance p,double scale){
        if(p==null)return scale;
        return scale*clamp(Math.max(0,p.damage()-.2)+((p.faults()&PartInstance.LEAK)!=0?.25:0),0,1);
    }
    public static double coolantLeak(MechanicalState m){return leak(m.get("cooling.upper_hose"),.22)+leak(m.get("cooling.lower_hose"),.22)+leak(m.get("engine.cooling"),.12);}
    public static double pressureHold(MechanicalState m,double seconds){return Math.exp(-coolantLeak(m)*seconds*3);}
    public static Measurements measure(MechanicalState m,double rpm,boolean running,double speed){
        return measure(m,rpm,running,speed,m.get("traction.contactor")!=null&&m.get("engine.internals")==null);
    }
    public static Measurements measure(MechanicalState m,double rpm,boolean running,double speed,boolean electricOnly){
        double fill=clamp(m.coolant()/6,0,1),leak=coolantLeak(m);
        boolean fan=m.coolantTemperature()>(electricOnly?45:92)&&m.capability("cooling.fan")>.2&&m.capability("electrical.wiring")>.2&&m.capability("electrical.fuse")>.2&&batteryCharge(m)>1;
        double thermostat=clamp((m.coolantTemperature()-(electricOnly?32:78))/12,0,1)*m.capability("cooling.thermostat");
        double flow=running?(electricOnly?1:clamp(rpm/2200,.18,1.5))*m.capability("cooling.pump")*fill*thermostat:0;
        double pressure=clamp((m.coolantTemperature()-65)/40,0,1.3)*fill*pressureHold(m,1);
        double oil=running&&!electricOnly?clamp((.8+rpm*.0008)*m.capability("oil.pump")*m.capability("oil.filter")*m.capability("oil.feed")*clamp(m.oil()/2.5,0,1)*(1-clamp((m.oilTemperature()-110)/100,0,.65)),0,6.5):0;
        double charging=running?(electricOnly?m.capability("traction.dc_dc")*.5:m.capability("electrical.alternator")*m.capability("electrical.belt")*clamp(rpm/1600,0,1)):0;
        return new Measurements(leak,pressure,flow,fan,oil,charging>.3?14.2:10.5+Math.min(1,batteryCharge(m)/48)*2.2);
    }
    public static double batteryCharge(MechanicalState m){var p=m.get("electrical.battery");return p==null?0:p.reserve()*p.capability();}
    public static MechanicalState step(MechanicalState m,double rpm,double throttle,double boost,boolean running,double speed,boolean brake,double dt){
        return step(m,rpm,throttle,boost,running,false,false,speed,brake,dt);
    }
    public static MechanicalState step(MechanicalState m,double rpm,double throttle,double boost,boolean running,boolean cranking,boolean lights,double speed,boolean brake,double dt){
        return step(m,rpm,throttle,boost,running,cranking,lights,speed,brake,dt,false);
    }
    public static MechanicalState step(MechanicalState m,double rpm,double throttle,double boost,boolean running,boolean cranking,boolean lights,double speed,boolean brake,double dt,boolean electricOnly){
        dt=clamp(dt,.0001,.05);var read=measure(m,rpm,running,speed,electricOnly);var parts=new HashMap<>(m.parts());var faults=new HashSet<>(m.faultHistory());
        boolean combustion=running&&!electricOnly;
        double coolant=Math.max(0,m.coolant()-read.coolantLeak*(.35+read.coolantPressure*.65)*dt);
        double heat=combustion?7+rpm*.0025+throttle*42+boost*20:0;
        var radiatorPart=m.get("engine.cooling");String radiatorItem=radiatorPart==null?"":radiatorPart.item();
        double radiator=m.capability("engine.cooling")*(radiatorItem.equals("performance_cooling")?1.3:radiatorItem.equals("cooling_3")?1.25:radiatorItem.equals("cooling_4")?1.65:1);
        double cooling=(m.coolantTemperature()-20)*(.07+read.circulation*radiator*(.12+Math.abs(speed)*.024+(read.fan?.32:0)));
        double capacity=26+coolant*4.18;
        double temperature=m.coolantTemperature()+(heat-cooling)/capacity*dt;
        if(combustion&&temperature>118){var p=parts.get("engine.internals");if(p!=null)parts.put("engine.internals",p.damage(Math.pow((temperature-118)/20,2)*.0015*dt,temperature>145?PartInstance.MISFIRE:0));}
        double brakeFluid=m.brakeFluid();
        for(int corner=0;corner<4;corner++){
            String prefix="wheel."+ComponentSlot.CORNERS[corner]+".";
            brakeFluid=Math.max(0,brakeFluid-leak(m.get(prefix+"brake_hose"),.025)*(brake?1:.1)*dt);
            var tire=parts.get(prefix+"tire");
            if(tire!=null){double pressure=Math.max(0,tire.reserve()-leak(tire,.10)*dt);double wear=tire.wear()+Math.abs(speed)*dt*(pressure<1?.000005:.00000015);parts.put(prefix+"tire",tire.condition(wear,tire.damage(),tire.faults()).operating(pressure,tire.temperature()));}
        }
        double oilLeak=electricOnly?0:leak(m.get("engine.oil"),.06)+leak(m.get("oil.feed"),.045);
        double oil=Math.max(0,m.oil()-oilLeak*(running?.5+read.oilPressure*.1:.15)*dt);
        double oilCooler=m.get("engine.oil")==null?0:m.get("engine.oil").item().equals("oil_system_3")?.055:m.get("engine.oil").item().equals("oil_system_4")?.08:0;
        double oilHeat=(combustion?1+rpm*.001+throttle*4+boost*4:0)+(temperature-m.oilTemperature())*.06;
        double oilTemp=m.oilTemperature()+(oilHeat-(m.oilTemperature()-20)*(.025+oilCooler+Math.abs(speed)*.0015))/(6+oil*2)*dt;
        if(combustion&&rpm>1200&&read.oilPressure<.65){
            faults.add("OIL_PRESSURE_LOW");
            for(String key:m.version()>=2?List.of("engine.induction"):List.of("engine.internals","engine.induction")){var p=parts.get(key);if(p!=null)parts.put(key,p.condition(p.wear()+(.65-read.oilPressure)*rpm/3500*.004*dt,p.damage(),p.faults()));}
        }
        var battery=parts.get("electrical.battery");
        if(battery!=null){double demand=(cranking?180:running?8:0)+(read.fan?18:0)+(lights?12:0);
            double charging=combustion?60*m.capability("electrical.alternator")*m.capability("electrical.belt")*clamp(rpm/1600,0,1):0;
            parts.put("electrical.battery",battery.operating(clamp(battery.reserve()+(charging-demand)*dt/3600,0,48),battery.temperature()));
            if(combustion&&charging<demand)faults.add("CHARGING_LOW");}
        if(!electricOnly&&oil<1)faults.add("OIL_LEVEL_LOW");
        if(coolant<2)faults.add("COOLANT_LOW");if(temperature>110)faults.add("COOLANT_HOT");if(brakeFluid<.2)faults.add("BRAKE_PRESSURE_LOW");
        return m.update(parts,coolant,oil,brakeFluid,temperature,oilTemp,m.distance()+Math.abs(speed)*dt,faults);
    }
    /** One bounded allocation per contact event; unaffected parts retain exactly their prior state. */
    public static MechanicalState impact(MechanicalState m,String region,double speed){
        double budget=clamp((speed*speed-25)/450,0,.9);if(budget==0)return m;
        String[] keys;double[] weights;
        if(region.equals("front")&&m.get("traction.motor_front")!=null){keys=new String[]{"body.front","cooling.upper_hose","engine.cooling","traction.motor_front","traction.inverter_front"};weights=new double[]{.20,.55,.05,.10,.10};}
        else if(region.equals("rear")&&m.get("traction.motor_rear")!=null){keys=new String[]{"body.rear","traction.motor_rear","traction.inverter_rear","driveline.differential"};weights=new double[]{.4,.25,.2,.15};}
        else if(region.equals("front")){keys=new String[]{"body.front","cooling.upper_hose","engine.cooling"};weights=new double[]{.25,.55,.20};}
        else if(region.equals("rear")){keys=new String[]{"body.rear","exhaust.pipe","exhaust.muffler"};weights=new double[]{.45,.30,.25};}
        else {String c=Arrays.asList(ComponentSlot.CORNERS).contains(region)?region:"fl";keys=new String[]{"wheel."+c+".rim","wheel."+c+".link","wheel."+c+".tire"};weights=new double[]{.4,.35,.25};
            if(m.get("driveline.cv_"+c)!=null){keys=new String[]{"wheel."+c+".rim","wheel."+c+".link","wheel."+c+".tire","driveline.cv_"+c};weights=new double[]{.35,.3,.25,.1};}
        }
        for(int i=0;i<keys.length;i++){var p=m.get(keys[i]);if(p!=null){int fault=keys[i].contains("hose")?PartInstance.LEAK:keys[i].endsWith("rim")||keys[i].endsWith("link")?PartInstance.BENT:0;if(keys[i].startsWith("traction.inverter")&&p.damage()+budget*weights[i]>.7)fault|=PartInstance.OPEN_CIRCUIT;m=m.with(keys[i],p.damage(budget*weights[i],fault));}}
        return m;
    }
}
