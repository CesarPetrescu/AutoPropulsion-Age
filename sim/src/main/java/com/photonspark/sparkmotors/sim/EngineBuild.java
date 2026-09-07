package com.photonspark.sparkmotors.sim;

/** The same resolved build drives the car, garage graph and matrix tests. */
public final class EngineBuild {
    private EngineBuild(){}
    public static double boost(double rpm,int parts){
        if(!Double.isFinite(rpm))return 0;
        return switch(EnginePart.INDUCTION.variant(parts)){
            case 1 -> .65*VehicleDynamics.clamp((rpm-1800)/2000,0,1);
            case 2 -> .50*Math.sqrt(VehicleDynamics.clamp(rpm/6800,0,1));
            default -> 0;
        };
    }
    public static double torque(double rpm,EngineFamily family,int grade,int parts,int limiter,double temperature){
        if(grade==0||!EnginePart.ready(parts)||!Double.isFinite(rpm)||rpm>=limiter||rpm<0)return 0;
        double base=PistonEngine.torque(rpm*family.rpmScale,grade==2,7200)*family.torqueScale;
        double intake=EnginePart.INTAKE.variant(parts)==2?1.08:1;
        double ignition=EnginePart.IGNITION.variant(parts)==2?1.03:1;
        double charge=1+boost(rpm,parts)*.85-(EnginePart.INDUCTION.variant(parts)==2?.04:0);
        double heat=1-VehicleDynamics.clamp((temperature-110)/35,0,.6);
        return base*intake*ignition*charge*heat;
    }
    public static double powerKw(double rpm,EngineFamily family,int grade,int parts,int limiter){
        return torque(rpm,family,grade,parts,limiter,90)*rpm*Math.PI/30000;
    }
    public static double temperature(double current,double rpm,double throttle,double boost,boolean running,int parts,double dt){
        current=VehicleDynamics.clamp(current,20,150);
        double target=running?82+throttle*24+boost*42-(EnginePart.COOLING.variant(parts)==2?22:0):20;
        double rate=running?(EnginePart.COOLING.variant(parts)==2?.045:.025):.012;
        return current+(target-current)*(1-Math.exp(-rate*dt));
    }
}
