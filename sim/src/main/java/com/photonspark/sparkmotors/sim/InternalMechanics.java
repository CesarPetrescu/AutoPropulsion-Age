package com.photonspark.sparkmotors.sim;

import java.util.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Family-specific service units. Compression is a calibrated workshop measurement, in bar. */
public final class InternalMechanics {
    private static final Map<EngineFamily,Set<String>> KEYS=new EnumMap<>(EngineFamily.class);
    static {for(var f:EngineFamily.values()){
        var keys=new LinkedHashSet<String>();String system=f.rotary()?"rotor":"cylinder";
        keys.add(f.rotary()?"internal.eccentric":"internal.crank");if(!f.rotary())keys.add("internal.timing");
        for(int n=1;n<=count(f);n++)for(String suffix:f.rotary()?new String[]{"housing","seals","bearing"}:new String[]{"piston","rings","bearing","valves"})keys.add(system+"."+n+"."+suffix);
        KEYS.put(f,Collections.unmodifiableSet(keys));
    }}
    private InternalMechanics(){}
    public static boolean internal(String key){return key.startsWith("internal.")||key.startsWith("cylinder.")||key.startsWith("rotor.");}
    public static int count(EngineFamily f){return f.rotary()?f.ordinal()-2:f==EngineFamily.V6?6:4;}
    public static Set<String> keys(EngineFamily f){return KEYS.get(f);}
    public static boolean seized(MechanicalState m){return m.version()>=2&&m.parts().entrySet().stream().anyMatch(e->internal(e.getKey())&&(e.getValue().faults()&PartInstance.SEIZED)!=0);}
    public static boolean seized(MechanicalState m,EngineFamily f){return m.version()>=2&&keys(f).stream().map(m::get).filter(Objects::nonNull).anyMatch(p->(p.faults()&PartInstance.SEIZED)!=0);}
    public static double compression(MechanicalState m,EngineFamily f,int unit){
        if(unit<1||unit>count(f))throw new IllegalArgumentException("Invalid cylinder / rotor");
        double block=m.capability("engine.internals");
        if(m.version()<2)return block*(f.rotary()?8.5:12);
        String prefix=(f.rotary()?"rotor":"cylinder")+"."+unit+".";
        double value=f.rotary()?Math.min(m.capability(prefix+"housing"),m.capability(prefix+"seals")):Math.min(m.capability(prefix+"piston"),Math.min(m.capability(prefix+"rings"),m.capability(prefix+"valves")));
        for(String suffix:f.rotary()?new String[]{"housing","seals"}:new String[]{"piston","rings","valves"}){
            var p=m.get(prefix+suffix);if(p!=null&&(p.faults()&PartInstance.LEAK)!=0)value*=.55;
        }
        return Math.min(block,value)*(f.rotary()?8.5:12);
    }
    public static double output(MechanicalState m,EngineFamily f,double rpm){
        if(m==null||m.version()<2)return 1;
        if(seized(m,f))return 0;
        double shaft=m.capability(f.rotary()?"internal.eccentric":"internal.crank");
        if(!f.rotary())shaft=Math.min(shaft,m.capability("internal.timing"));
        double sum=0;
        for(int n=1;n<=count(f);n++){
            double ratio=compression(m,f,n)/(f.rotary()?8.5:12);
            double bearing=m.capability((f.rotary()?"rotor":"cylinder")+"."+n+".bearing");
            // A weak cylinder/rotor loses its own contribution and creates a bounded irregular pulse.
            sum+=Math.min(ratio,bearing)*(ratio<.55?.75+.20*Math.sin(rpm*.011+n*2.399):1);
        }
        return Math.min(shaft,sum/count(f));
    }
    public static MechanicalState migrateBundle(MechanicalState m,EngineFamily f){
        if(m.version()>=2)return m;
        var parent=m.get("engine.internals");var map=new LinkedHashMap<>(m.parts());
        if(parent!=null)for(String key:keys(f))map.putIfAbsent(key,PowertrainTopology.derived(parent,ComponentSlot.byKey(key)));
        return PowertrainTopology.current(m.update(map,m.coolant(),m.oil(),m.brakeFluid(),m.coolantTemperature(),m.oilTemperature(),m.distance(),m.faultHistory()));
    }
    public static boolean hasFailure(MechanicalState m,EngineFamily f){return m.version()>=2&&(seized(m,f)||output(m,f,2000)<.7);}
    public static MechanicalState step(MechanicalState m,EngineFamily f,double rpm,double throttle,boolean running,double dt){
        if(m.version()<2||!running||rpm<700)return m;
        double pressure=CircuitPhysics.measure(m,rpm,true,0).oilPressure();
        double starvation=Math.max(0,.65-pressure)*rpm/3500,hot=Math.max(0,m.coolantTemperature()-118)/25;
        var map=new HashMap<>(m.parts());var faults=new HashSet<>(m.faultHistory());
        for(String key:keys(f)){
            var p=map.get(key);if(p==null)continue;
            boolean bearing=key.endsWith("bearing")||key.equals("internal.crank")||key.equals("internal.eccentric");
            boolean seal=key.endsWith("rings")||key.endsWith("seals");
            double wear=p.wear()+dt*(rpm/4000*.0000003+starvation*(bearing?.004:seal?.001:.0002));
            double damage=p.damage()+dt*hot*hot*(seal?.0018:bearing?.0001:.0005);
            int fault=p.faults();if(seal&&damage>.65)fault|=PartInstance.LEAK;
            if(bearing&&wear>.98&&starvation>.15)fault|=PartInstance.SEIZED;
            map.put(key,p.condition(wear,damage,fault).operating(p.reserve(),m.oilTemperature()));
            if(fault!=0)faults.add((f.rotary()?"ROTARY_":"CYLINDER_")+key.replace('.','_').toUpperCase(Locale.ROOT));
        }
        return m.update(map,m.coolant(),m.oil(),m.brakeFluid(),m.coolantTemperature(),m.oilTemperature(),m.distance(),faults);
    }
    public static String report(MechanicalState m,EngineFamily f){
        var result=new StringBuilder(f.rotary()?"Rotor compression (bar equivalent): ":"Cylinder compression (bar): ");
        for(int n=1;n<=count(f);n++){if(n>1)result.append(" / ");result.append(n).append(": ").append(String.format(Locale.ROOT,"%.2f",compression(m,f,n)));}
        return result.toString();
    }
}
