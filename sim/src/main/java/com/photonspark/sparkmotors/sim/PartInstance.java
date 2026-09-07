package com.photonspark.sparkmotors.sim;

import java.util.UUID;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Immutable installed object. Identity survives inventory, trading and assembly conversions. */
public record PartInstance(UUID id, String item, double wear, double damage, int faults, double reserve, double temperature) {
    public static final int LEAK=1, SEIZED=2, OPEN_CIRCUIT=4, BENT=8, MISFIRE=16;
    public PartInstance {
        if(id==null||item==null||item.length()>100)throw new IllegalArgumentException("Invalid part identity");
        wear=clamp(wear,0,1);damage=clamp(damage,0,1);faults&=31;reserve=clamp(reserve,0,100);temperature=clamp(temperature,-40,1000);
    }
    public static PartInstance fresh(String item,double reserve){return new PartInstance(UUID.randomUUID(),item,0,0,0,reserve,20);}
    public double capability(){return (faults&(SEIZED|OPEN_CIRCUIT))!=0?0:(1-damage)*(1-wear*.7);}
    public PartInstance condition(double wear,double damage,int faults){return new PartInstance(id,item,wear,damage,faults,reserve,temperature);}
    public PartInstance operating(double reserve,double temperature){return new PartInstance(id,item,wear,damage,faults,reserve,temperature);}
    public PartInstance damage(double amount,int fault){return condition(wear,damage+amount,faults|fault);}
}
