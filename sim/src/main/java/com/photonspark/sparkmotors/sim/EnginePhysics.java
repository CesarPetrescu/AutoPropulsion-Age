package com.photonspark.sparkmotors.sim;

import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Fixed-step crank angular momentum, throttle plate, compressor, mixture and oil model. SI internally. */
public final class EnginePhysics {
    public record State(double omega,double throttle,double spool,double boost,double oilTemperature,double oilPressure,
                        double health,double afr,double shaftTorque,double blowerKw,boolean blowOff) {
        public State {omega=clamp(omega,0,1000);throttle=clamp(throttle,0,1);spool=clamp(spool,0,1);boost=clamp(boost,0,2);
            oilTemperature=clamp(oilTemperature,20,180);oilPressure=clamp(oilPressure,0,9);health=clamp(health,0,100);
            afr=clamp(afr,8,25);shaftTorque=clamp(shaftTorque,-2000,4000);blowerKw=clamp(blowerKw,0,250);}
        public double rpm(){return omega*30/Math.PI;}
        public static State stopped(double oilTemperature,double health){return new State(0,0,0,0,oilTemperature,0,health,14.7,0,0,false);}
    }
    public static State step(State s,VehicleDynamics.Setup setup,boolean running,double pedal,double loadTorque,double dt){
        dt=clamp(dt,.0001,.05);int parts=setup.engineParts();
        running=running&&EnginePart.ready(parts)&&Assembly.ENGINE.variant(setup.config())>0&&s.health()>5;
        double omega=s.omega(),rpm=s.rpm();
        // Starter catches at idle. Subsequent speed is integrated, including during clutch slip and free revs.
        if(running&&omega<40){omega=850*Math.PI/30;rpm=850;}
        double throttle=s.throttle()+((running?clamp(pedal,0,1):0)-s.throttle())*(1-Math.exp(-dt/EngineBuild.throttleTime(parts)));
        double target=running?Math.min(setup.boostTarget(),EngineBuild.boost(rpm,parts))*throttle:0;
        double max=EngineBuild.boostLimit(parts),spool=s.spool();boolean turbo=EngineBuild.turbo(parts);
        if(turbo){
            double exhaust=running?Math.sqrt(clamp(target/Math.max(.01,max),0,1)):0;
            spool+=(exhaust-spool)*(1-Math.exp(-dt/(exhaust>spool?EngineBuild.spoolTime(parts):1.2)));
            target=Math.min(setup.boostTarget(),max*spool*spool)*throttle;
        }else spool=0;
        boolean vent=turbo&&pedal<.1&&s.boost()>.10;
        if(turbo&&pedal<.1)target=0;
        double boost=s.boost()+(target-s.boost())*(1-Math.exp(-dt/(turbo&&pedal<.1?.055:turbo?.13:.035)));
        if(!running)boost*=Math.exp(-dt/.08);
        double potential=EngineBuild.naturalTorque(rpm,setup.family(),Assembly.ENGINE.variant(setup.config()),parts,setup.limiter())*(1+boost*.85);
        double requested=potential*throttle;
        double fuelFraction=Math.min(1,EngineBuild.fuelCapacity(setup.family(),parts)/Math.max(1,requested));
        double desiredAfr=boost>.08?12.2:14.7,afr=running?Math.min(25,desiredAfr/Math.max(.3,fuelFraction)):14.7;
        double blower=EngineBuild.blowerTorque(rpm,boost,parts)*Math.max(.15,throttle);
        double friction=12+omega*.045,idle=running?clamp((850*Math.PI/30-omega)*1.4+friction,0,70):0;
        double heatDerate=1-clamp((setup.temperature()-110)/35,0,.6);
        double shaft=running?potential*throttle*fuelFraction*heatDerate*(.5+.5*s.health()/100)+(idle-friction)*(1-throttle)-blower:-friction;
        // Fuel cut still leaves friction and driveline load above the limiter.
        if(rpm>=setup.limiter()&&running)shaft=-friction-blower;
        double nextOmega=clamp(omega+(shaft-loadTorque)/EngineBuild.inertia(setup.family(),parts)*dt,0,setup.limiter()*Math.PI/30*1.04);
        double oilCooling=switch(EnginePart.OIL.variant(parts)){case 3->17;case 4->24;default->0;};
        double oilTarget=running?setup.temperature()+8+throttle*18+boost*15-oilCooling:20;
        double oil=s.oilTemperature()+(oilTarget-s.oilTemperature())*(1-Math.exp(-dt*.035));
        double pump=switch(EnginePart.OIL.variant(parts)){case 2->1.15;case 3->1.10;case 4->1.35;default->1;};
        double pressure=running?clamp((.8+rpm*.0008)*pump*(1-clamp((oil-110)/100,0,.65)),0,6.5):0;
        double lean=running&&throttle>.7&&boost>.1?Math.max(0,afr-13.5):0;
        double hot=running?Math.max(0,oil-145)/8+Math.max(0,setup.temperature()-120)/5:0;
        double health=s.health()-(lean*lean*.45+hot*hot*.10)*dt;
        return new State(nextOmega,throttle,spool,boost,oil,pressure,health,afr,shaft,blower*omega/1000,vent);
    }
}
