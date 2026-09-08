package com.photonspark.sparkmotors.sim;

/** Separate questions for diagnosis; a failed brake must not switch off a runnable engine. */
public final class MechanicalCapabilities {
    public static boolean canCrank(MechanicalState m){return canCrank(m,EngineFamily.I4);}
    public static boolean canCrank(MechanicalState m,EngineFamily f){return (m.version()<2||m.capability(f.rotary()?"internal.eccentric":"internal.crank")>.02)&&!InternalMechanics.seized(m,f)&&CircuitPhysics.batteryCharge(m)>1&&m.capability("electrical.starter")>.15&&m.capability("electrical.wiring")>.2&&m.capability("electrical.fuse")>.2&&m.capability("engine.internals")>.02;}
    public static String buildProblem(int hardware){
        for(var p:new EnginePart[]{EnginePart.INTAKE,EnginePart.FUEL,EnginePart.IGNITION,EnginePart.INTERNALS,EnginePart.FLYWHEEL,EnginePart.HEADWORK})if(p.variant(hardware)==0)return "Missing "+p.title.toLowerCase(java.util.Locale.ROOT)+".";
        return EnginePart.compatibilityProblem(hardware);
    }
    public static boolean canRun(MechanicalState m,int hardware){return buildProblem(hardware).isEmpty()&&m.capability("engine.fuel")>.08&&m.capability("engine.ignition")>.08&&m.capability("engine.internals")>.04&&m.capability("electrical.wiring")>.15&&m.capability("electrical.fuse")>.15&&CircuitPhysics.batteryCharge(m)>.1;}
    public static double transmission(MechanicalState m){return m==null?1:m.capability("driveline.gearbox")*m.capability("driveline.differential")*m.capability("driveline.shaft");}
    public static double clutch(MechanicalState m){if(m==null)return 1;var p=m.get("driveline.clutch");return p==null?0:p.capability()*(1-p.wear()*.8)*VehicleDynamics.clamp(1-(p.temperature()-220)/300,.05,1);}
    public static double combustion(MechanicalState m,double rpm){
        if(m==null)return 1;var internals=m.get("engine.internals");if(internals==null)return 0;
        double regularity=(internals.faults()&PartInstance.MISFIRE)!=0?.7+.2*Math.sin(rpm*.07):1;
        return m.capability("engine.intake")*m.capability("engine.fuel")*m.capability("engine.ignition")*(m.version()>=2?1:1-internals.wear()*.5)*regularity;
    }
}
