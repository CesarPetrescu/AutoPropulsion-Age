package com.photonspark.sparkmotors.sim;

import com.photonspark.sparkmotors.sim.electric.Powertrain;

/** Four tire forces drive a planar rigid body. SI units; forward and signed lateral/yaw axes follow the existing rig. Positive steer turns right in Minecraft. */
public final class VehicleDynamics {
    public static final double MASS = 1280, WHEEL_RADIUS = .34, WHEELBASE = 2.65, TRACK=1.66, YAW_INERTIA=2350, CG_HEIGHT=.54;
    private static final double[] GEARS = {3.6, 2.1, 1.4, 1.05, .82};
    public record Input(double throttle, double steer, boolean brake, boolean reverse, boolean clutch, boolean handbrake) {
        public Input(double throttle,double steer,boolean brake,boolean reverse,boolean clutch){this(throttle,steer,brake,reverse,clutch,false);}
        public Input(double throttle,double steer,boolean brake,boolean reverse){this(throttle,steer,brake,reverse,false);}
        public Input { throttle = clamp(throttle, 0, 1); steer = clamp(steer, -1, 1); }
    }
    public record Setup(int config, int limiter, double finalDrive, EngineFamily family, int engineParts, double temperature, double boostTarget, MechanicalState mechanics, DriveConfig drive, double mass,Powertrain powertrain) {
        public Setup(int c,int l,double f,EngineFamily family,int parts,double t,double boost,MechanicalState m,DriveConfig drive,double mass){this(c,l,f,family,parts,t,boost,m,drive,mass,Powertrain.COMBUSTION);}
        public Setup(int c,int l,double f,EngineFamily family,int parts,double t,double boost,MechanicalState m,DriveConfig drive){this(c,l,f,family,parts,t,boost,m,drive,MASS);}
        public Setup withMass(double mass){return new Setup(config,limiter,finalDrive,family,engineParts,temperature,boostTarget,mechanics,drive,mass,powertrain);}
        public Setup withPowertrain(Powertrain type){return new Setup(config,limiter,finalDrive,family,engineParts,temperature,boostTarget,mechanics,drive,type.massKg,type);}
        public Setup withMechanics(MechanicalState m){return new Setup(config,limiter,finalDrive,family,engineParts,m==null?temperature:m.coolantTemperature(),boostTarget,m,drive,mass,powertrain);}
        public Setup(int c,int l,double f,EngineFamily family,int parts,double t,double boost,MechanicalState m){this(c,l,f,family,parts,t,boost,m,DriveConfig.stock());}
        public Setup(int config,int limiter,double finalDrive,EngineFamily family,int engineParts,double temperature,double boostTarget){this(config,limiter,finalDrive,family,engineParts,temperature,boostTarget,null);}
        public Setup(int config,int limiter,double finalDrive){this(config,limiter,finalDrive,EngineFamily.I4,EnginePart.stock(),90);}
        public Setup(int config,int limiter,double finalDrive,EngineFamily family,int engineParts,double temperature){this(config,limiter,finalDrive,family,engineParts,temperature,1.4);}
        public Setup { mass=clamp(mass,500,5000);config = Assembly.sanitize(config); limiter = Math.clamp(limiter, 4000, 7000); finalDrive = clamp(finalDrive, 2.8, 4.8); family=family==null?EngineFamily.I4:family;engineParts=EnginePart.sanitize(engineParts);temperature=clamp(temperature,20,150);boostTarget=clamp(boostTarget,.2,1.4);drive=drive==null?DriveConfig.stock():drive; }
    }
    public record State(double speed, double rpm, int gear, double fuel, double yawDelta, double fuelUsed, EnginePhysics.State engine, WheelDynamics.State wheels, TransmissionPhysics.State transmission, double load) {
        public double groundSpeed(){return Math.hypot(speed,transmission.lateralSpeed());}
        public State(double speed,double rpm,int gear,double fuel,double yawDelta,double fuelUsed,EnginePhysics.State engine,WheelDynamics.State wheels,TransmissionPhysics.State transmission){this(speed,rpm,gear,fuel,yawDelta,fuelUsed,engine,wheels,transmission,0);}
        public State(double speed,double rpm,int gear,double fuel,double yawDelta,double fuelUsed,EnginePhysics.State engine,WheelDynamics.State wheels){this(speed,rpm,gear,fuel,yawDelta,fuelUsed,engine,wheels,TransmissionPhysics.State.stopped());}
        public State(double speed,double rpm,int gear,double fuel,double yawDelta,double fuelUsed,EnginePhysics.State engine){this(speed,rpm,gear,fuel,yawDelta,fuelUsed,engine,WheelDynamics.State.stopped());}
        public State(double speed,double rpm,int gear,double fuel,double yawDelta,double fuelUsed){this(speed,rpm,gear,fuel,yawDelta,fuelUsed,EnginePhysics.State.stopped(90,100));}
    }

    public static State step(State previous,boolean ignition,Input input,Setup setup,double grip,boolean grounded,double dt){
        return step(previous.speed,previous.fuel,previous.engine,previous.wheels,previous.transmission,ignition,input,setup,grip,new boolean[]{grounded,grounded,grounded,grounded},new double[4],dt);
    }

    public static State step(double speed, double fuel, boolean ignition, Input in, Setup setup, double grip, boolean grounded, double dt) {
        return step(speed,fuel,EnginePhysics.State.stopped(setup.temperature,100),ignition,in,setup,grip,grounded,dt);
    }
    public static State step(double speed,double fuel,EnginePhysics.State engine,boolean ignition,Input in,Setup setup,double grip,boolean grounded,double dt){
        return step(speed,fuel,engine,WheelDynamics.State.stopped(),ignition,in,setup,grip,new boolean[]{grounded,grounded,grounded,grounded},new double[4],dt);
    }
    public static State step(double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheelState,boolean ignition,Input in,Setup setup,double grip,boolean[] contacts,double[] travel,double dt){
        return step(speed,fuel,engine,wheelState,TransmissionPhysics.State.stopped(),ignition,in,setup,grip,contacts,travel,dt);
    }
    public static State step(double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheelState,TransmissionPhysics.State transmission,boolean ignition,Input in,Setup setup,double grip,boolean[] contacts,double[] travel,double dt){
        return step(speed,fuel,engine,wheelState,transmission,ignition,in,setup,new double[]{grip,grip,grip,grip},contacts,travel,dt);
    }
    public static State step(double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheels,TransmissionPhysics.State trans,boolean ignition,Input in,Setup setup,double[] grip,boolean[] contacts,double[] travel,double dt){
        if(contacts.length!=4||travel.length!=4||grip.length!=4)throw new IllegalArgumentException("Four wheel contacts required");
        dt=clamp(dt,.0001,.05);int steps=(int)Math.ceil(dt/.0125);double yaw=0,used=0;State result=null;
        for(int i=0;i<steps;i++){
            result=integrate(clamp(speed,-65,65),clamp(fuel,0,50),engine,wheels,trans,ignition,in,setup,grip,contacts,travel,dt/steps);
            speed=result.speed;fuel=result.fuel;engine=result.engine;wheels=result.wheels;trans=result.transmission;yaw+=result.yawDelta;used+=result.fuelUsed;
        }
        return new State(result.speed,result.rpm,result.gear,result.fuel,yaw,used,result.engine,result.wheels,result.transmission,result.load);
    }
    private static State integrate(double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheels,TransmissionPhysics.State trans,boolean ignition,Input in,Setup setup,double[] grip,boolean[] contacts,double[] travel,double dt){
        double lateral=clamp(trans.lateralSpeed(),-65,65),yawRate=clamp(trans.yawRate(),-5,5);
        int gear=in.reverse?-1:1;double finalRatio=setup.finalDrive*(Assembly.TRANSMISSION.variant(setup.config)==2?1.10:1);
        if(!in.reverse)while(gear<5&&Math.abs(speed)/WHEEL_RADIUS*GEARS[gear-1]*finalRatio*60/(2*Math.PI)>Math.min(5800,setup.limiter-250))gear++;
        // Hold the selected ratio through sideways/backsiding drift, including the
        // zero-forward-speed crossing where road-speed shifting would otherwise kick down.
        boolean drifting=speed*(in.reverse?-1:1)<-.5||Math.abs(lateral)>Math.max(1,Math.abs(speed)*.4);
        if(drifting&&(trans.target()<0)==in.reverse)gear=trans.target();
        trans=TransmissionPhysics.shift(trans,gear,dt);gear=trans.gear();
        double ratio=(gear<0?3.4:GEARS[gear-1])*finalRatio,direction=gear<0?-1:1;
        double wheelOmega=(wheels.initialized()?setup.drive.drivenOmega(wheels):speed/WHEEL_RADIUS)*direction;
        boolean running=ignition&&fuel>0&&engine.health()>5&&Assembly.ENGINE.variant(setup.config)>0&&(setup.mechanics==null?EnginePart.ready(setup.engineParts):MechanicalCapabilities.buildProblem(setup.engineParts).isEmpty());
        boolean drive=running&&(setup.mechanics==null||engine.mode()==EnginePhysics.Mode.RUNNING)&&Assembly.TRANSMISSION.variant(setup.config)>0&&Assembly.WHEELS.variant(setup.config)>0;
        double clutchTorque=0,engagement=0;
        if(drive&&!in.clutch&&trans.remaining()==0){
            engagement=clamp((engine.rpm()-950)/850,0,1);
            double capacity=(Assembly.TRANSMISSION.variant(setup.config)==2?680:460)*engagement*MechanicalCapabilities.clutch(setup.mechanics)*PowertrainTopology.availability(setup.mechanics,setup.powertrain,setup.drive);
            // Couple the crank to the driven wheels, including airborne spin. Tire forces,
            // rather than an engine-side grip clamp, determine how much reaches the road.
            double roadCoupling=0;
            for(int c=0;c<4;c++)if(contacts[c])roadCoupling+=(c<2?setup.drive.frontFraction()*.5:(1-setup.drive.frontFraction())*.5)*clamp(1-wheels.corners().get(c).slip()/.3,0,1);
            double wheelInertia=WheelDynamics.INERTIA*(setup.drive.layout()==DriveConfig.Layout.AWD?4:2)+roadCoupling*MASS*WHEEL_RADIUS*WHEEL_RADIUS;
            clutchTorque=clamp((engine.omega()-wheelOmega*ratio)/(.06+dt*(1/EngineBuild.inertia(setup.family,setup.engineParts)+ratio*ratio/wheelInertia)),-capacity,capacity);
        }
        engine=EnginePhysics.step(engine,setup,running,in.throttle,clutchTorque,dt);
        double used=running&&engine.mode()==EnginePhysics.Mode.RUNNING?(.00022+Math.max(0,engine.shaftTorque())*engine.rpm()*1e-8)*setup.family.fuelScale*dt:0;
        var road=chassis(speed,fuel,engine,wheels,trans,in,setup,grip,contacts,travel,clutchTorque*ratio*setup.drive.efficiency()*direction,0,dt);
        wheels=road.wheels;trans=road.transmission;
        double slipPower=Math.abs(clutchTorque*(engine.omega()-wheelOmega*ratio))/1000;
        double heat=clamp(trans.clutchHeat()+(slipPower/3-(trans.clutchHeat()-20)*.025)*dt,20,1000);
        trans=trans.heat(heat).clutch(Math.abs(engine.omega()-wheelOmega*ratio)*60/(2*Math.PI),clutchTorque,engagement);
        double load=clamp(Math.max(0,clutchTorque)/Math.max(1,EngineBuild.naturalTorque(engine.rpm(),setup.family,Assembly.ENGINE.variant(setup.config),setup.engineParts,setup.limiter)),0,1);
        return new State(road.speed,engine.rpm(),gear,Math.max(0,fuel-used),road.yawDelta,used,engine,wheels,trans,load);
    }
    /** Shared rigid body and tire integration for combustion, battery and series-hybrid drives. */
    public static State chassis(double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheels,TransmissionPhysics.State trans,Input in,Setup setup,double[] grip,boolean[] contacts,double[] travel,double axleTorque,double regenBrakeTorque,double dt){
        double front=setup.drive.frontFraction(),rear=1-front;
        double fa=front*PowertrainTopology.axleCapability(setup.mechanics,setup.powertrain,setup.drive,0),ra=rear*PowertrainTopology.axleCapability(setup.mechanics,setup.powertrain,setup.drive,1),sum=fa+ra;
        double[] torques=PowertrainTopology.torques(setup.mechanics,setup.powertrain,setup.drive,wheels,sum>0?axleTorque*fa/sum:0,sum>0?axleTorque*ra/sum:0,dt);
        double[] regen={regenBrakeTorque*front*.5,regenBrakeTorque*front*.5,regenBrakeTorque*rear*.5,regenBrakeTorque*rear*.5};
        return chassisTorques(speed,fuel,engine,wheels,trans,in,setup,grip,contacts,travel,torques,regen,dt);
    }
    /** Explicit wheel torque inputs let independent e-axles account for their own work and failures. */
    public static State chassisTorques(double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheels,TransmissionPhysics.State trans,Input in,Setup setup,double[] grip,boolean[] contacts,double[] travel,double[] torques,double[] regen,double dt){
        double lateral=clamp(trans.lateralSpeed(),-65,65),yawRate=clamp(trans.yawRate(),-5,5);
        double response=Assembly.SUSPENSION.variant(setup.config)==2?1.08:1;
        double target=in.steer*.55*response/(1+Math.abs(speed)*.025);
        double steering=trans.steering()+clamp(target-trans.steering(),-1.8*response*dt,1.8*response*dt);
        var forces=WheelDynamics.stepTorques(wheels,setup,in,speed,lateral,yawRate,steering,torques,grip,contacts,travel,trans.longitudinalAcceleration(),trans.lateralAcceleration(),dt,regen);
        double magnitude=Math.hypot(speed,lateral),drag=Assembly.BODY.variant(setup.config)==2?.40:.43;
        double fx=forces.forward()-drag*speed*magnitude,fy=forces.lateral()-drag*lateral*magnitude;
        double ax=fx/setup.mass,ay=fy/setup.mass,newYaw=clamp(yawRate+forces.yawMoment()/(YAW_INERTIA*setup.mass/MASS)*dt,-5,5);
        double deltaYaw=(yawRate+newYaw)*.5*dt,u=speed+ax*dt,v=lateral+ay*dt;
        // Rotate velocity into the new body frame without redirecting world momentum.
        double cosine=Math.cos(deltaYaw),sine=Math.sin(deltaYaw);
        double next=u*cosine+v*sine;lateral=-u*sine+v*cosine;
        if(magnitude<.1&&Math.abs(yawRate)<.03&&Math.hypot(fx,fy)<=forces.holdingGrip()){next=0;lateral=0;newYaw=0;deltaYaw=0;ax=0;ay=0;}
        boolean supported=false;for(boolean c:contacts)supported|=c;
        if(supported&&in.throttle==0&&Math.hypot(next,lateral)<.035&&Math.abs(newYaw)<.02){next=0;lateral=0;newYaw=0;}
        trans=trans.motion(lateral,newYaw,steering,ax,ay);
        return new State(clamp(next,-65,65),engine.rpm(),trans.gear(),fuel,deltaYaw,0,engine,forces.state(),trans,0);
    }
    public static double clamp(double v, double min, double max) { return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : min; }
}
