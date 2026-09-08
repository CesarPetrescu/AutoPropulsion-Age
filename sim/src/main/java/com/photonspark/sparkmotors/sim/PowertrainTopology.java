package com.photonspark.sparkmotors.sim;

import com.photonspark.sparkmotors.sim.electric.Powertrain;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Installed topology shared by service, rendering and torque routing. Layout changes never create parts. */
public final class PowertrainTopology {
    private PowertrainTopology(){}
    public static boolean front(DriveConfig d){return d.frontPercent()>0;}
    public static boolean rear(DriveConfig d){return d.frontPercent()<100;}
    public static boolean applicable(ComponentSlot s,Powertrain type,DriveConfig d,EngineFamily family){
        String k=s.key();
        if(InternalMechanics.internal(k))return (!type.electric()||type.hybrid())&&InternalMechanics.keys(family).contains(k);
        if(k.startsWith("traction.")){
            if(!type.electric())return false;
            if(k.equals("traction.generator"))return type.hybrid();
            if(k.endsWith("_front"))return front(d);
            if(k.endsWith("_rear"))return rear(d);
            return true;
        }
        if(k.equals("driveline.clutch")||k.equals("driveline.gearbox"))return !type.electric()||type.hybrid();
        if(k.equals("driveline.shaft"))return (!type.electric()||type.hybrid())&&rear(d);
        if(k.equals("driveline.transfer"))return (!type.electric()||type.hybrid())&&d.layout()==DriveConfig.Layout.AWD;
        if(k.equals("driveline.differential"))return rear(d);
        if(k.equals("driveline.front_differential"))return front(d);
        if(k.startsWith("driveline.cv_"))return k.charAt(13)=='f'?front(d):rear(d);
        if(type.electric()&&!type.hybrid())return k.startsWith("cooling.")||k.equals("engine.cooling")||s.assembly()!=Assembly.ENGINE&&!k.startsWith("exhaust.")&&!Set.of("electrical.starter","electrical.alternator","electrical.belt").contains(k);
        return true;
    }
    public static List<ComponentSlot> slots(Powertrain type,DriveConfig d,EngineFamily family){return ComponentSlot.ALL.stream().filter(s->applicable(s,type,d,family)).toList();}
    public static MechanicalState current(MechanicalState m){return new MechanicalState(MechanicalState.VERSION,m.parts(),m.coolant(),m.oil(),m.brakeFluid(),m.coolantTemperature(),m.oilTemperature(),m.distance(),m.faultHistory());}
    /** V1 identities and condition stay intact. New child IDs derive from their old parent, once only.
     * Missing V2 slots are never filled by migration, reload or a configuration toggle. */
    public static MechanicalState migrate(MechanicalState m,Powertrain type,DriveConfig d,EngineFamily family,int config,int hardware){
        if(m.version()>=MechanicalState.VERSION)return m;
        if(m.version()==2){
            var upgraded=new LinkedHashMap<>(m.parts());
            // Series hybrids had no mechanical output path. Derive its new mounts once from
            // the installed generator, preserving its condition; a missing donor stays missing.
            if(type.hybrid()&&m.get("traction.generator")!=null)for(String key:List.of("driveline.clutch","driveline.gearbox","driveline.shaft","driveline.transfer")){
                var slot=ComponentSlot.byKey(key);
                if(applicable(slot,type,d,family)&&!upgraded.containsKey(key))upgraded.put(key,derived(m.get("traction.generator"),slot));
            }
            return current(m.update(upgraded,m.coolant(),m.oil(),m.brakeFluid(),m.coolantTemperature(),m.oilTemperature(),m.distance(),m.faultHistory()));
        }
        var map=new LinkedHashMap<>(m.parts());
        for(var s:slots(type,d,family))if(s.detailed()&&s.installedBy(config,hardware)&&!map.containsKey(s.key())){
            PartInstance donor=m.get(InternalMechanics.internal(s.key())?"engine.internals":s.key().startsWith("driveline.cv_")?"driveline.shaft":s.key().contains("differential")?"driveline.differential":"driveline.gearbox");
            if(donor!=null)map.put(s.key(),derived(donor,s));
        }
        // Inactive legacy parts stay as recoverable spares; migration never deletes an old identity.
        return current(m.update(map,m.coolant(),m.oil(),m.brakeFluid(),m.coolantTemperature(),m.oilTemperature(),m.distance(),m.faultHistory()));
    }
    public static PartInstance derived(PartInstance donor,ComponentSlot s){
        var id=UUID.nameUUIDFromBytes((donor.id()+"/v2/"+s.key()).getBytes(StandardCharsets.UTF_8));
        return new PartInstance(id,s.item(),donor.wear(),donor.damage(),donor.faults(),0,donor.temperature());
    }
    public static MechanicalState fresh(Powertrain type,DriveConfig d,EngineFamily family,int config,int hardware){return migrate(MechanicalState.legacy(config,hardware,20,20,100),type,d,family,config,hardware).select(s->applicable(s,type,d,family),!type.electric()||type.hybrid()).fluids(8,type.electric()&&!type.hybrid()?0:5,1);}
    public static String diff(int axle){return axle==0?"driveline.front_differential":"driveline.differential";}
    public static String unit(String part,int axle){return "traction."+part+(axle==0?"_front":"_rear");}
    public static String cv(int c){return "driveline.cv_"+ComponentSlot.CORNERS[c];}
    public static boolean liveHv(MechanicalState m){return m==null||m.version()<2||m.capability("traction.hv_cable")>.05&&m.capability("traction.contactor")>.05&&m.capability("electrical.fuse")>.1&&m.capability("electrical.wiring")>.1&&CircuitPhysics.batteryCharge(m)>.1;}
    public static double axleCapability(MechanicalState m,Powertrain type,DriveConfig d,int axle){
        if((axle==0&&!front(d))||(axle==1&&!rear(d)))return 0;
        if(m==null||m.version()<2)return MechanicalCapabilities.transmission(m);
        double result=m.capability(diff(axle));
        if(type.electric())result*=liveHv(m)?m.capability(unit("motor",axle))*m.capability(unit("inverter",axle))*m.capability(unit("reduction",axle)):0;
        else {result*=m.capability("driveline.gearbox");if(axle==1)result*=m.capability("driveline.shaft");if(d.layout()==DriveConfig.Layout.AWD)result*=m.capability("driveline.transfer");}
        double left=m.capability(cv(axle*2)),right=m.capability(cv(axle*2+1));
        // A broken output on an open diff interrupts that axle; a limited-slip clutch has finite bias.
        double outputs=switch(d.differential()){case OPEN->Math.min(left,right);case LIMITED_SLIP->Math.min(left,right)+.35*Math.abs(left-right);case LOCKED->Math.max(left,right);};
        return result*outputs;
    }
    public static double availability(MechanicalState m,Powertrain type,DriveConfig d){return d.frontFraction()*axleCapability(m,type,d,0)+(1-d.frontFraction())*axleCapability(m,type,d,1);}
    public static double[] torques(MechanicalState m,Powertrain type,DriveConfig d,WheelDynamics.State wheels,double frontTorque,double rearTorque,double dt){
        double[] values=new double[4];
        for(int axle=0;axle<2;axle++){
            if((axle==0&&!front(d))||(axle==1&&!rear(d))||axleCapability(m,type,d,axle)<=0)continue;
            double torque=axle==0?frontTorque:rearTorque;
            var axleDrive=new DriveConfig(axle==0?DriveConfig.Layout.FWD:DriveConfig.Layout.RWD,d.differential(),axle==0?100:0);
            var pair=axleDrive.wheelTorques(torque,wheels,dt);
            double a=m==null||m.version()<2?1:m.capability(cv(axle*2)),b=m==null||m.version()<2?1:m.capability(cv(axle*2+1));
            if(a+b==0)continue;
            if(a==0||b==0){values[axle*2]=a==0?0:torque;values[axle*2+1]=b==0?0:torque;}
            else {values[axle*2]=pair[axle*2];values[axle*2+1]=pair[axle*2+1];}
        }
        return values;
    }
}
