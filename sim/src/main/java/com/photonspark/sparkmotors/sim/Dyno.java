package com.photonspark.sparkmotors.sim;
public final class Dyno {
    public static void main(String[] args) {
        EngineFamily family=EngineFamily.I4;int induction=0,grade=1,limiter=6800;
        for(int i=0;i<args.length;i++)switch(args[i]){
            case "--family" -> {String id=args[++i];family=java.util.Arrays.stream(EngineFamily.values()).filter(f->f.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown engine family: "+id));}
            case "--induction" -> induction=switch(args[++i]){case "natural"->0;case "turbo"->1;case "supercharger"->2;default->throw new IllegalArgumentException("Unknown induction mode");};
            case "--sport" -> grade=2;
            case "--limiter" -> {limiter=Integer.parseInt(args[++i]);if(limiter<4000||limiter>7000)throw new IllegalArgumentException("Limiter must be 4000–7000 RPM");}
            default -> throw new IllegalArgumentException("Use --family i4|v6|flat4|rotor1|rotor2|rotor3|rotor4 --induction natural|turbo|supercharger --sport --limiter 6800");
        }
        int parts=induction==0?EnginePart.stock():EnginePart.boosted(induction);
        System.out.println("rpm,torque_nm,power_kw");
        for (int rpm = 800; rpm <= limiter; rpm += 200)
            System.out.printf(java.util.Locale.ROOT, "%d,%.2f,%.2f%n", rpm, EngineBuild.torque(rpm,family,grade,parts,limiter,90),EngineBuild.powerKw(rpm,family,grade,parts,limiter));
    }
}
