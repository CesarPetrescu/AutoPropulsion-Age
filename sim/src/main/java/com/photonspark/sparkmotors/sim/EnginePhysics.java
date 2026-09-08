package com.photonspark.sparkmotors.sim;

import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Fixed-step crank angular momentum, throttle plate, compressor, mixture and oil model. SI internally. */
public final class EnginePhysics {
    public enum Mode { OFF, CRANKING, RUNNING, STALLED }
    public record State(double omega,double throttle,double spool,double boost,double oilTemperature,double oilPressure,
                        double health,double afr,double shaftTorque,double blowerKw,boolean blowOff,Mode mode,double startTime) {
        public State(double omega,double throttle,double spool,double boost,double oilTemperature,double oilPressure,double health,double afr,double shaftTorque,double blowerKw,boolean blowOff){this(omega,throttle,spool,boost,oilTemperature,oilPressure,health,afr,shaftTorque,blowerKw,blowOff,omega>40?Mode.RUNNING:Mode.OFF,0);}
        public State {omega=clamp(omega,0,1000);throttle=clamp(throttle,0,1);spool=clamp(spool,0,1);boost=clamp(boost,0,2);
            oilTemperature=clamp(oilTemperature,20,180);oilPressure=clamp(oilPressure,0,9);health=clamp(health,0,100);
            afr=clamp(afr,8,25);shaftTorque=clamp(shaftTorque,-2000,4000);blowerKw=clamp(blowerKw,0,250);}
        public double rpm(){return omega*30/Math.PI;}
        public static State stopped(double oilTemperature,double health){return new State(0,0,0,0,oilTemperature,0,health,14.7,0,0,false);}
    }
    public static State step(State s,VehicleDynamics.Setup setup,boolean running,double pedal,double loadTorque,double dt){
        dt=clamp(dt,.0001,.05);int parts=setup.engineParts();var mechanical=setup.mechanics();
        Mode mode=s.mode;double startTime=s.startTime;
        if(mechanical!=null){
            if(!running){mode=Mode.OFF;startTime=0;}
            else if(mode==Mode.OFF){mode=Mode.CRANKING;startTime=0;}
            if(mode==Mode.CRANKING){
                startTime+=dt;
                double starter=MechanicalCapabilities.canCrank(mechanical,setup.family())?65*mechanical.capability("electrical.starter")*clamp(CircuitPhysics.batteryCharge(mechanical)/8,0,1):0;
                double omega=clamp(s.omega+(starter-15-s.omega*.8)/EngineBuild.inertia(setup.family(),parts)*dt,0,40);
                if(startTime>.45&&omega>18&&MechanicalCapabilities.canRun(mechanical,parts)&&InternalMechanics.output(mechanical,setup.family(),rpmForStart(omega))>.08)mode=Mode.RUNNING;
                else if(startTime>3)mode=Mode.STALLED;
                if(mode!=Mode.RUNNING)return new State(omega,0,0,0,mechanical.oilTemperature(),0,s.health,14.7,starter,0,false,mode,startTime);
            }
            running=running&&mode==Mode.RUNNING&&MechanicalCapabilities.canRun(mechanical,parts)&&InternalMechanics.output(mechanical,setup.family(),s.rpm())>.04;
            if(mode==Mode.RUNNING&&!running)mode=Mode.STALLED;
        }
        running=running&&(mechanical==null?EnginePart.ready(parts):MechanicalCapabilities.buildProblem(parts).isEmpty())&&Assembly.ENGINE.variant(setup.config())>0&&s.health()>5;
        double omega=s.omega(),rpm=s.rpm();
        // Starter catches at idle. Subsequent speed is integrated, including during clutch slip and free revs.
        if(running&&omega<40&&(mechanical==null||s.mode==Mode.CRANKING)){omega=850*Math.PI/30;rpm=850;}
        double throttle=s.throttle()+((running?clamp(pedal,0,1):0)-s.throttle())*(1-Math.exp(-dt/EngineBuild.throttleTime(parts)));
        double target=running?Math.min(setup.boostTarget(),EngineBuild.boost(rpm,parts))*throttle:0;
        double max=EngineBuild.boostLimit(parts),spool=s.spool();boolean turbo=EngineBuild.turbo(parts);
        if(turbo){
            double exhaust=running?Math.sqrt(clamp(target/Math.max(.01,max),0,1)):0;
            if(mechanical!=null)exhaust*=Math.sqrt(.18+.82*clamp((Math.max(0,loadTorque)+Math.max(0,s.shaftTorque())*.2)/120,0,1));
            spool+=(exhaust-spool)*(1-Math.exp(-dt/(exhaust>spool?EngineBuild.spoolTime(parts):1.2)));
            target=Math.min(setup.boostTarget(),max*spool*spool)*throttle;
        }else spool=0;
        if(mechanical!=null){
            double compressor=mechanical.capability("engine.induction");double pipe=mechanical.capability("induction.pipe")*(1-CircuitPhysics.leak(mechanical.get("induction.pipe"),1));
            target*=compressor*pipe*(turbo?1:mechanical.capability("induction.belt"));
            // Failed control leaks away pressure; replacing the compressor alone does not repair it.
            target*=mechanical.capability("induction.wastegate");
        }
        boolean vent=turbo&&pedal<.1&&s.throttle>.12&&s.boost()>.10&&(mechanical==null||mechanical.capability("induction.bov")>.2);
        if(turbo&&pedal<.1)target=0;
        double boost=s.boost()+(target-s.boost())*(1-Math.exp(-dt/(turbo&&pedal<.1?.055:turbo?.13:.035)));
        if(!running)boost*=Math.exp(-dt/.08);
        double potential=EngineBuild.naturalTorque(rpm,setup.family(),Assembly.ENGINE.variant(setup.config()),parts,setup.limiter())*(1+boost*.85);
        double requested=potential*throttle;
        double fuelFraction=Math.min(1,EngineBuild.fuelCapacity(setup.family(),parts)*(mechanical==null?1:mechanical.capability("engine.fuel"))/Math.max(1,requested));
        double desiredAfr=boost>.08?12.2:14.7,afr=running?Math.min(25,desiredAfr/Math.max(.3,fuelFraction)):14.7;
        double blower=EngineBuild.blowerTorque(rpm,boost,parts)*Math.max(.15,throttle);
        double friction=12+omega*.045,idle=running?clamp((850*Math.PI/30-omega)*1.4+friction,0,70):0;
        double heatDerate=1-clamp((setup.temperature()-110)/35,0,.6);
        double internals=InternalMechanics.output(mechanical,setup.family(),rpm);
        double shaft=running?potential*throttle*fuelFraction*heatDerate*MechanicalCapabilities.combustion(mechanical,rpm)*(mechanical!=null&&mechanical.version()>=2?internals:(.5+.5*s.health()/100))+(idle-friction)*(1-throttle)-blower:-friction;
        // Fuel cut still leaves friction and driveline load above the limiter.
        if(rpm>=setup.limiter()&&running)shaft=-friction-blower;
        double nextOmega=clamp(omega+(shaft-loadTorque)/EngineBuild.inertia(setup.family(),parts)*dt,0,setup.limiter()*Math.PI/30*1.04);
        double oilCooling=switch(EnginePart.OIL.variant(parts)){case 3->17;case 4->24;default->0;};
        double oilTarget=running?setup.temperature()+8+throttle*18+boost*15-oilCooling:20;
        double oil=s.oilTemperature()+(oilTarget-s.oilTemperature())*(1-Math.exp(-dt*.035));
        double pump=switch(EnginePart.OIL.variant(parts)){case 2->1.15;case 3->1.10;case 4->1.35;default->1;};
        double pressure=running?clamp((.8+rpm*.0008)*pump*(1-clamp((oil-110)/100,0,.65)),0,6.5):0;
        if(mechanical!=null){var read=CircuitPhysics.measure(mechanical,rpm,running,0);oil=mechanical.oilTemperature();pressure=read.oilPressure();}
        if(mechanical!=null&&running&&nextOmega<25&&s.mode==Mode.RUNNING){mode=Mode.STALLED;}
        if(mechanical==null)mode=running?Mode.RUNNING:Mode.OFF;
        double lean=running&&throttle>.7&&boost>.1?Math.max(0,afr-13.5):0;
        double hot=running?Math.max(0,oil-145)/8+Math.max(0,setup.temperature()-120)/5:0;
        double health=s.health()-(lean*lean*.45+hot*hot*.10)*dt;
        return new State(nextOmega,throttle,spool,boost,oil,pressure,health,afr,shaft,blower*omega/1000,vent,mode,startTime);
    }
    private static double rpmForStart(double omega){return omega*30/Math.PI;}
}
