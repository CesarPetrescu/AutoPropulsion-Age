package com.photonspark.autopropulsion;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.photonspark.autopropulsion.sim.SimulationConfig;

public final class Config {
    public static final ModConfigSpec SERVER_SPEC,CLIENT_SPEC;
    public static final ModConfigSpec.IntValue SUBSTEPS;
    public static final ModConfigSpec.DoubleValue FUEL,DAMAGE;
    public static final ModConfigSpec.BooleanValue GUEST_DRIVING,HUD,DETAIL;
    static {
        var s=new ModConfigSpec.Builder();
        SUBSTEPS=s.comment("Substeps per 20 TPS tick. 4 = 80 Hz. Range 1..16.").defineInRange("simulationSubsteps",4,1,16);
        FUEL=s.comment("Zero disables fuel consumption.").defineInRange("fuelConsumptionMultiplier",1.,0.,10.);
        DAMAGE=s.comment("Zero disables engine and collision damage.").defineInRange("damageMultiplier",1.,0.,10.);
        GUEST_DRIVING=s.comment("Allow a non-owner to drive. Does not grant tuning/part management.").define("allowGuestDriving",false);
        SERVER_SPEC=s.build();
        var c=new ModConfigSpec.Builder();
        HUD=c.define("showHud",true);DETAIL=c.comment("Show engine-bay detail geometry.").define("detailedModels",true);CLIENT_SPEC=c.build();
    }
    public static SimulationConfig simulation() {return new SimulationConfig(SUBSTEPS.get(),FUEL.get(),DAMAGE.get());}
    private Config() {}
}
