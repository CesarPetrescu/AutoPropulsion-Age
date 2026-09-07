package com.photonspark.sparkmotors.sim;

/** Resolved hardware curves, shared by the dynamic engine and steady-state garage/dyno estimate. */
public final class EngineBuild {
    private EngineBuild(){}
    private static double pick(EnginePart slot,int parts,double... values){return values[Math.max(0,Math.min(values.length-1,slot.variant(parts)-1))];}
    public static boolean turbo(int parts){int m=EnginePart.INDUCTION.variant(parts);return m==1||m==3||m==4;}
    public static double boostLimit(int parts){return switch(EnginePart.INDUCTION.variant(parts)){case 1,2->.65;case 3->1.40;case 4->1.15;case 5->.80;case 6->1.10;default->0;};}
    public static double spoolTime(int parts){return switch(EnginePart.INDUCTION.variant(parts)){case 1->.48;case 3->1.20;case 4->.65;default->.06;};}
    public static double boost(double rpm,int parts){
        double r=VehicleDynamics.clamp(rpm,0,10000),max=boostLimit(parts);
        return max*switch(EnginePart.INDUCTION.variant(parts)){
            case 1->VehicleDynamics.clamp((r-1600)/2100,0,1);
            case 2->Math.pow(VehicleDynamics.clamp(r/6800,0,1),2);
            case 3->VehicleDynamics.clamp((r-2800)/2600,0,1);
            case 4->VehicleDynamics.clamp((r-2100)/2200,0,1);
            case 5->VehicleDynamics.clamp(r/1300,0,1);
            case 6->VehicleDynamics.clamp(r/1500,0,1);
            default->0;
        };
    }
    public static double inertia(EngineFamily family,int parts){return .16*Math.max(.7,family.torqueScale)+pick(EnginePart.FLYWHEEL,parts,.22,.13,.065,.09);}
    public static double throttleTime(int parts){return pick(EnginePart.INTAKE,parts,.16,.12,.045,.20);}
    public static double fuelCapacity(EngineFamily family,int parts){return family.torqueScale*pick(EnginePart.FUEL,parts,220,360,520,760);}
    public static double blowerTorque(double rpm,double boost,int parts){
        int m=EnginePart.INDUCTION.variant(parts);
        return boost*(m==2?15:m==5?35:m==6?23:0)*(0.7+VehicleDynamics.clamp(rpm/6800,0,1)*.3);
    }
    public static double naturalTorque(double rpm,EngineFamily family,int grade,int parts,int limiter){
        if(grade==0||!EnginePart.ready(parts)||!Double.isFinite(rpm)||rpm>=limiter||rpm<0)return 0;
        double high=VehicleDynamics.clamp((rpm-2500)/3000,0,1);
        double base=PistonEngine.torque(rpm*family.rpmScale,grade==2,7200)*family.torqueScale;
        double intake=pick(EnginePart.INTAKE,parts,1,1.08,.97+.17*high,1.11-.07*high);
        double ignition=pick(EnginePart.IGNITION,parts,1,1.03,1.02+.025*high,1.035);
        double exhaust=pick(EnginePart.EXHAUST,parts,1,1.09-.04*high,1+.12*high,.94+.22*high);
        double head=pick(EnginePart.HEADWORK,parts,1,1.03+.03*high,.88+.28*high,.78+.45*high);
        double core=pick(EnginePart.INTERNALS,parts,1,1,1.13,1.02);
        return base*intake*ignition*exhaust*head*core;
    }
    public static double torqueAtBoost(double rpm,EngineFamily family,int grade,int parts,int limiter,double temperature,double boost){
        double heat=1-VehicleDynamics.clamp((temperature-110)/35,0,.6);
        double production=naturalTorque(rpm,family,grade,parts,limiter)*(1+boost*.85);
        return Math.max(0,Math.min(production,fuelCapacity(family,parts))*heat-blowerTorque(rpm,boost,parts));
    }
    public static double torque(double rpm,EngineFamily family,int grade,int parts,int limiter,double temperature){
        if(rpm>=limiter||!EnginePart.ready(parts)||grade==0)return 0;
        return torqueAtBoost(rpm,family,grade,parts,limiter,temperature,boost(rpm,parts));
    }
    public static double powerKw(double rpm,EngineFamily family,int grade,int parts,int limiter){return torque(rpm,family,grade,parts,limiter,90)*rpm*Math.PI/30000;}
    public static double temperature(double current,double rpm,double throttle,double boost,boolean running,int parts,double dt){return temperature(current,rpm,throttle,boost,running,parts,0,dt);}
    public static double temperature(double current,double rpm,double throttle,double boost,boolean running,int parts,double speed,double dt){
        current=VehicleDynamics.clamp(current,20,150);
        double cooling=pick(EnginePart.COOLING,parts,0,17,23,30);
        double target=running?82+throttle*24+boost*42-cooling-Math.min(10,Math.abs(speed)*.25):20;
        double rate=running?pick(EnginePart.COOLING,parts,.025,.045,.05,.06):.012;
        return current+(target-current)*(1-Math.exp(-rate*dt));
    }
}
