package com.photonspark.sparkmotors.sim;

import java.util.*;

/** Stable service paths; repeated parts are distinct slots, never a shared axle percentage. */
public record ComponentSlot(String key,String title,Assembly assembly,EnginePart hardware,String item,int corner,Access access) {
    public enum Access { HOOD, LIFT, CABIN }
    public static final List<ComponentSlot> ALL;
    public static final String[] CORNERS={"fl","fr","rl","rr"};
    static {
        var all=new ArrayList<ComponentSlot>();
        for(var p:EnginePart.values())all.add(new ComponentSlot("engine."+p.name().toLowerCase(Locale.ROOT),p.title,Assembly.ENGINE,p,"",-1,Access.HOOD));
        add(all,"cooling",Assembly.ENGINE,Access.HOOD,"upper_hose","Upper coolant hose","lower_hose","Lower coolant hose","pump","Water pump","fan","Electric radiator fan","thermostat","Thermostat","sender","Coolant temperature sender");
        add(all,"oil",Assembly.ENGINE,Access.HOOD,"pump","Oil pump","filter","Oil filter","feed","Oil supply line","sender","Oil pressure sender");
        add(all,"electrical",Assembly.BODY,Access.HOOD,"battery","Battery","starter","Starter motor","alternator","Alternator","belt","Accessory belt","wiring","Essential wiring","fuse","Main fuse");
        add(all,"driveline",Assembly.TRANSMISSION,Access.LIFT,"clutch","Clutch","gearbox","Gearbox","differential","Differential","shaft","Drive shaft");
        add(all,"induction",Assembly.ENGINE,Access.HOOD,"pipe","Charge pipe","intercooler","Intercooler","wastegate","Wastegate / bypass","bov","Pressure release valve","belt","Supercharger drive belt");
        add(all,"exhaust",Assembly.BODY,Access.LIFT,"pipe","Exhaust section","muffler","Muffler");
        add(all,"body",Assembly.BODY,Access.CABIN,"front","Front body panel","rear","Rear body panel","lamps","Road lamps","instruments","Instrument cluster");
        String[] names={"Front left","Front right","Rear left","Rear right"};
        for(int c=0;c<4;c++){
            corner(all,c,names[c],Assembly.WHEELS,"tire","Tire","rim","Rim","bearing","Wheel bearing");
            corner(all,c,names[c],Assembly.BRAKES,"pad","Brake pads","disc","Brake disc","caliper","Caliper","brake_hose","Brake hose");
            corner(all,c,names[c],Assembly.SUSPENSION,"spring","Spring","damper","Damper","link","Steering / alignment link");
        }
        // Append only: packet slot indices and old item IDs remain stable.
        add(all,"driveline",Assembly.TRANSMISSION,Access.LIFT,"front_differential","Front differential","transfer","Transfer case","cv_fl","Front left CV shaft","cv_fr","Front right CV shaft","cv_rl","Rear left CV shaft","cv_rr","Rear right CV shaft");
        add(all,"traction",Assembly.TRANSMISSION,Access.LIFT,"motor_front","Front traction motor","motor_rear","Rear traction motor","inverter_front","Front inverter","inverter_rear","Rear inverter","reduction_front","Front reduction gear","reduction_rear","Rear reduction gear","hv_cable","HV supply harness","contactor","Traction contactor","dc_dc","12 V converter","generator","Engine generator");
        add(all,"internal",Assembly.ENGINE,Access.HOOD,"crank","Crankshaft","timing","Timing drive","eccentric","Eccentric shaft");
        repeated(all,"cylinder",6,"piston","Piston","rings","Piston rings","bearing","Connecting rod bearing","valves","Valve assembly");
        repeated(all,"rotor",4,"housing","Rotor housing","seals","Apex and side seals","bearing","Rotor bearing");
        ALL=List.copyOf(all);
    }
    private static void repeated(List<ComponentSlot> all,String system,int count,String... pairs){
        for(int n=1;n<=count;n++)for(int i=0;i<pairs.length;i+=2)all.add(new ComponentSlot(system+"."+n+"."+pairs[i],system+" "+n+" "+pairs[i+1],Assembly.ENGINE,null,system+"_"+pairs[i],-1,Access.HOOD));
    }
    public boolean detailed(){return key.startsWith("traction.")||key.startsWith("internal.")||key.startsWith("cylinder.")||key.startsWith("rotor.")||key.startsWith("driveline.cv_")||key.equals("driveline.front_differential")||key.equals("driveline.transfer");}
    private static void add(List<ComponentSlot> all,String system,Assembly assembly,Access access,String... pairs){
        for(int i=0;i<pairs.length;i+=2)all.add(new ComponentSlot(system+"."+pairs[i],pairs[i+1],assembly,null,system+"_"+pairs[i],-1,access));
    }
    private static void corner(List<ComponentSlot> all,int c,String name,Assembly assembly,String... pairs){
        for(int i=0;i<pairs.length;i+=2)all.add(new ComponentSlot("wheel."+CORNERS[c]+"."+pairs[i],name+" "+pairs[i+1].toLowerCase(Locale.ROOT),assembly,null,"service_"+pairs[i],c,Access.LIFT));
    }
    public static ComponentSlot byKey(String key){return ALL.stream().filter(s->s.key.equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown component "+key));}
    public static ComponentSlot engine(EnginePart part){return byKey("engine."+part.name().toLowerCase(Locale.ROOT));}
    public String item(int config,int parts){return hardware==null?item:hardware.variant(parts)==0?"":hardware.itemName(hardware.variant(parts));}
    public boolean installedBy(int config,int parts){return assembly.variant(config)>0&&(hardware==null||hardware.variant(parts)>0);}
    public PartInstance fresh(int config,int parts){return PartInstance.fresh(item(config,parts),key.endsWith(".tire")?2.3:key.equals("electrical.battery")?48:0);}
    public boolean accepts(PartInstance part){return hardware==null?part.item().equals(item)||(key.equals("exhaust.muffler")&&part.item().equals("sport_muffler")):java.util.stream.IntStream.rangeClosed(1,hardware.maxVariant()).anyMatch(v->part.item().equals(hardware.itemName(v)));}
}
