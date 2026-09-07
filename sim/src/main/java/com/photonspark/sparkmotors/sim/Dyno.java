package com.photonspark.sparkmotors.sim;

/** Reproducible steady-state curve or ten-second acceleration / throttle-lift telemetry. */
public final class Dyno {
    public static void main(String[] args){
        EngineFamily family=EngineFamily.I4;int induction=0,grade=1,limiter=6800;double boost=1.4;boolean transientRun=false;
        var partsToFit=new java.util.LinkedHashMap<EnginePart,Integer>();
        for(int i=0;i<args.length;i++){
            String arg=args[i];
            if(!arg.equals("--sport")&&!arg.equals("--transient")&&i+1>=args.length)throw new IllegalArgumentException("Missing value for "+arg);
            switch(arg){
                case "--family" -> {String id=args[++i];family=java.util.Arrays.stream(EngineFamily.values()).filter(f->f.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown engine family: "+id));}
                case "--induction" -> induction=switch(args[++i]){case "natural"->0;case "turbo"->1;case "supercharger"->2;case "large-turbo"->3;case "twin-turbo"->4;case "roots"->5;case "twin-screw"->6;default->throw new IllegalArgumentException("Unknown induction mode");};
                case "--part" -> {String[] pair=args[++i].split("=",-1);if(pair.length!=2)throw new IllegalArgumentException("Use --part intake=3 (stable slot ID and option 0..max)");var slot=java.util.Arrays.stream(EnginePart.values()).filter(p->p.id.equals(pair[0])).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown slot: "+pair[0]));int value=Integer.parseInt(pair[1]);slot.with(0,value);partsToFit.put(slot,value);}
                case "--boost" -> {boost=Double.parseDouble(args[++i]);if(!Double.isFinite(boost)||boost<.2||boost>1.4)throw new IllegalArgumentException("Boost target must be 0.2..1.4 bar");}
                case "--sport" -> grade=2;
                case "--transient" -> transientRun=true;
                case "--limiter" -> {limiter=Integer.parseInt(args[++i]);if(limiter<4000||limiter>7000)throw new IllegalArgumentException("Limiter must be 4000..7000 RPM");}
                default -> throw new IllegalArgumentException("Options: --family ID --induction natural|turbo|supercharger|large-turbo|twin-turbo|roots|twin-screw --part SLOT=VALUE --boost BAR --sport --limiter RPM --transient");
            }
        }
        int parts=induction==0?EnginePart.stock():EnginePart.boosted(induction);
        for(var entry:partsToFit.entrySet())parts=entry.getKey().with(parts,entry.getValue());
        if(!EnginePart.ready(parts))throw new IllegalArgumentException(EnginePart.problem(parts));
        if(transientRun){
            var setup=new VehicleDynamics.Setup(Assembly.ENGINE.with(Assembly.stock(),grade),limiter,3.7,family,parts,90,boost);
            var s=new VehicleDynamics.State(0,0,1,40,0,0);System.out.println("seconds,rpm,speed_mps,boost_bar,turbo_speed,throttle,shaft_nm,afr,oil_c,oil_bar,engine_health,blower_kw");
            for(int tick=0;tick<800;tick++){
                s=VehicleDynamics.step(s,true,new VehicleDynamics.Input(tick<560?1:0,0,tick>=560,false),setup,1,true,.0125);
                if(tick%4==3){var e=s.engine();System.out.printf(java.util.Locale.ROOT,"%.2f,%.1f,%.3f,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.2f,%.3f,%.3f%n",(tick+1)*.0125,s.rpm(),s.speed(),e.boost(),e.spool(),e.throttle(),e.shaftTorque(),e.afr(),e.oilTemperature(),e.oilPressure(),e.health(),e.blowerKw());}
            }
        }else{
            System.out.println("rpm,torque_nm,power_kw");
            for(int rpm=800;rpm<=limiter;rpm+=200){double torque=EngineBuild.torqueAtBoost(rpm,family,grade,parts,limiter,90,Math.min(boost,EngineBuild.boost(rpm,parts)));System.out.printf(java.util.Locale.ROOT,"%d,%.2f,%.2f%n",rpm,torque,torque*rpm*Math.PI/30000);}
        }
    }
}
