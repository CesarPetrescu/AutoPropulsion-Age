package com.photonspark.sparkmotors.sim;

import java.util.*;
import java.util.function.Predicate;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Common persistent authority. A bundle uses the same representation with only its own slots. */
public record MechanicalState(int version,Map<String,PartInstance> parts,double coolant,double oil,double brakeFluid,
                              double coolantTemperature,double oilTemperature,double distance,Set<String> faultHistory) {
    public static final int VERSION=1;
    public MechanicalState {
        if(version!=VERSION)throw new IllegalArgumentException("Unsupported mechanical data version "+version);
        if(parts.size()>128||faultHistory.size()>128)throw new IllegalArgumentException("Mechanical data exceeds bounds");
        parts=Map.copyOf(parts);faultHistory=Set.copyOf(faultHistory);
        coolant=clamp(coolant,0,8);oil=clamp(oil,0,5);brakeFluid=clamp(brakeFluid,0,1);
        coolantTemperature=clamp(coolantTemperature,20,180);oilTemperature=clamp(oilTemperature,20,180);distance=clamp(distance,0,1e12);
    }
    public static MechanicalState empty(){return new MechanicalState(VERSION,Map.of(),0,0,0,20,20,0,Set.of());}
    public static MechanicalState legacy(int config,int hardware,double temperature,double oilTemperature,double engineHealth){
        var parts=new LinkedHashMap<String,PartInstance>();
        for(var s:ComponentSlot.ALL)if(s.installedBy(config,hardware))parts.put(s.key(),s.fresh(config,hardware));
        var internal=parts.get("engine.internals");
        if(internal!=null)parts.put("engine.internals",internal.condition(0,1-clamp(engineHealth,0,100)/100,0));
        return new MechanicalState(VERSION,parts,8,5,1,temperature,oilTemperature,0,Set.of());
    }
    public PartInstance get(String key){return parts.get(key);}
    public double capability(String key){var p=get(key);return p==null?0:p.capability();}
    public MechanicalState with(String key,PartInstance part){var map=new HashMap<>(parts);if(part==null)map.remove(key);else map.put(key,part);return update(map,coolant,oil,brakeFluid,coolantTemperature,oilTemperature,distance,faultHistory);}
    public MechanicalState update(Map<String,PartInstance> map,double coolant,double oil,double brakes,double temperature,double oilTemp,double distance,Set<String> faults){return new MechanicalState(VERSION,map,coolant,oil,brakes,temperature,oilTemp,distance,faults);}
    public MechanicalState fluids(double coolant,double oil,double brakes){return update(parts,coolant,oil,brakes,coolantTemperature,oilTemperature,distance,faultHistory);}
    public MechanicalState select(Predicate<ComponentSlot> selection,boolean engine){
        var map=new HashMap<String,PartInstance>();for(var s:ComponentSlot.ALL)if(selection.test(s)&&get(s.key())!=null)map.put(s.key(),get(s.key()));
        return update(map,engine?coolant:0,engine?oil:0,0,coolantTemperature,oilTemperature,0,Set.of());
    }
    public MechanicalState replace(Predicate<ComponentSlot> selection,MechanicalState incoming,boolean engine){
        var map=new HashMap<>(parts);for(var s:ComponentSlot.ALL)if(selection.test(s)){map.remove(s.key());if(incoming.get(s.key())!=null)map.put(s.key(),incoming.get(s.key()));}
        return update(map,engine?incoming.coolant:coolant,engine?incoming.oil:oil,brakeFluid,engine?incoming.coolantTemperature:coolantTemperature,engine?incoming.oilTemperature:oilTemperature,distance,faultHistory);
    }
    public boolean validFor(Predicate<ComponentSlot> selection){
        var ids=new HashSet<UUID>();for(var e:parts.entrySet()){
            ComponentSlot s;try{s=ComponentSlot.byKey(e.getKey());}catch(IllegalArgumentException ex){return false;}
            if(!selection.test(s)||!s.accepts(e.getValue())||!ids.add(e.getValue().id()))return false;
        }return true;
    }
}
