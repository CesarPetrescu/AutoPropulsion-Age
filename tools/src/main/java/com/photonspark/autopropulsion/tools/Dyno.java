package com.photonspark.autopropulsion.tools;
import com.photonspark.autopropulsion.sim.*;
import java.util.Locale;

/** CSV on stdout, summary on stderr, so shell redirection stays machine-readable. */
public final class Dyno {
    public static void main(String[] args) {
        boolean race=false; double boost=0;
        for(int i=0;i<args.length;i++) switch(args[i]) {
            case "--race-cam" -> race=true;
            case "--boost" -> {if(++i>=args.length) throw new IllegalArgumentException("--boost needs gauge bar");boost=Double.parseDouble(args[i]);}
            case "--engine" -> {if(++i>=args.length||!args[i].equals("ref_i4_2.0")) throw new IllegalArgumentException("Only ref_i4_2.0 is implemented");}
            default -> throw new IllegalArgumentException("Unknown argument: "+args[i]);
        }
        var spec=EngineSpec.reference().withUpgrades(race,boost,false,false);
        System.out.println("rpm,torque_nm,power_kw,fuel_kg_s"); double pt=0,pp=0;
        for(int rpm=800;rpm<spec.rpmLimit();rpm+=100) {
            var s=EngineModel.sample(spec,rpm,1,boost);pt=Math.max(pt,s.torqueNm());pp=Math.max(pp,s.powerKw());
            System.out.printf(Locale.ROOT,"%d,%.6f,%.6f,%.9f%n",rpm,s.torqueNm(),s.powerKw(),s.fuelKgS());
        }
        System.err.printf(Locale.ROOT,"Peak torque %.2f Nm; peak power %.2f kW. Analytical engine dyno, not wheel measurement.%n",pt,pp);
    }
}
