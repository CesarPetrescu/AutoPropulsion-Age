package com.photonspark.sparkmotors.sim;

/** Longitudinal driveline and bicycle steering model. World contact is supplied by the game. */
public final class VehicleDynamics {
    public static final double MASS = 1280, WHEEL_RADIUS = .34, WHEELBASE = 2.65;
    private static final double[] GEARS = {3.6, 2.1, 1.4, 1.05, .82};
    public record Input(double throttle, double steer, boolean brake, boolean reverse, boolean clutch, boolean handbrake) {
        public Input(double throttle,double steer,boolean brake,boolean reverse,boolean clutch){this(throttle,steer,brake,reverse,clutch,false);}
        public Input(double throttle,double steer,boolean brake,boolean reverse){this(throttle,steer,brake,reverse,false);}
        public Input { throttle = clamp(throttle, 0, 1); steer = clamp(steer, -1, 1); }
    }
    public record Setup(int config, int limiter, double finalDrive, EngineFamily family, int engineParts, double temperature, double boostTarget, MechanicalState mechanics) {
        public Setup(int config,int limiter,double finalDrive,EngineFamily family,int engineParts,double temperature,double boostTarget){this(config,limiter,finalDrive,family,engineParts,temperature,boostTarget,null);}
        public Setup(int config,int limiter,double finalDrive){this(config,limiter,finalDrive,EngineFamily.I4,EnginePart.stock(),90);}
        public Setup(int config,int limiter,double finalDrive,EngineFamily family,int engineParts,double temperature){this(config,limiter,finalDrive,family,engineParts,temperature,1.4);}
        public Setup { config = Assembly.sanitize(config); limiter = Math.clamp(limiter, 4000, 7000); finalDrive = clamp(finalDrive, 2.8, 4.8); family=family==null?EngineFamily.I4:family;engineParts=EnginePart.sanitize(engineParts);temperature=clamp(temperature,20,150);boostTarget=clamp(boostTarget,.2,1.4); }
    }
    public record State(double speed, double rpm, int gear, double fuel, double yawDelta, double fuelUsed, EnginePhysics.State engine, WheelDynamics.State wheels) {
        public State(double speed,double rpm,int gear,double fuel,double yawDelta,double fuelUsed,EnginePhysics.State engine){this(speed,rpm,gear,fuel,yawDelta,fuelUsed,engine,WheelDynamics.State.stopped());}
        public State(double speed,double rpm,int gear,double fuel,double yawDelta,double fuelUsed){this(speed,rpm,gear,fuel,yawDelta,fuelUsed,EnginePhysics.State.stopped(90,100));}
    }

    public static State step(State previous,boolean ignition,Input input,Setup setup,double grip,boolean grounded,double dt){
        return step(previous.speed,previous.fuel,previous.engine,previous.wheels,ignition,input,setup,grip,new boolean[]{grounded,grounded,grounded,grounded},new double[4],dt);
    }

    public static State step(double speed, double fuel, boolean ignition, Input in, Setup setup, double grip, boolean grounded, double dt) {
        return step(speed,fuel,EnginePhysics.State.stopped(setup.temperature,100),ignition,in,setup,grip,grounded,dt);
    }
    public static State step(double speed,double fuel,EnginePhysics.State engine,boolean ignition,Input in,Setup setup,double grip,boolean grounded,double dt){
        return step(speed,fuel,engine,WheelDynamics.State.stopped(),ignition,in,setup,grip,new boolean[]{grounded,grounded,grounded,grounded},new double[4],dt);
    }
    public static State step(double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheelState,boolean ignition,Input in,Setup setup,double grip,boolean[] contacts,double[] travel,double dt){
        boolean grounded=contacts[0]||contacts[1]||contacts[2]||contacts[3];
        dt=clamp(dt,.0001,.05);grip=clamp(grip,0,2);
        if (!Double.isFinite(speed)) speed = 0;
        speed = clamp(speed, -14, 65);
        fuel = clamp(fuel, 0, 50);
        double yaw = 0, used = 0;
        int gear = in.reverse ? -1 : 1;
        double shiftRpm = Math.min(5800, setup.limiter - 250);
        double finalRatio = setup.finalDrive * (Assembly.TRANSMISSION.variant(setup.config) == 2 ? 1.10 : 1);
        if (!in.reverse) {
            while (gear < 5 && Math.abs(speed) / WHEEL_RADIUS * GEARS[gear - 1] * finalRatio * 60 / (2 * Math.PI) > shiftRpm) gear++;
        }
        double ratio = (in.reverse ? 3.4 : GEARS[gear - 1]) * finalRatio;
        boolean running = ignition && fuel > 0 && engine.health()>5 && Assembly.ENGINE.variant(setup.config) > 0 && EnginePart.ready(setup.engineParts);
        boolean drive = running && Assembly.TRANSMISSION.variant(setup.config)>0 && Assembly.WHEELS.variant(setup.config)>0;
        double force = 0;
        double clutchTorque=0;
        if(drive&&!in.clutch&&grounded&&(!in.reverse||Math.abs(speed)<13)){
            double wheelOmega=Math.abs(speed)/WHEEL_RADIUS*ratio;
            double engagement=clamp((engine.rpm()-950)/850,0,1);
            double capacity=(Assembly.TRANSMISSION.variant(setup.config)==2?680:460)*engagement;
            // Implicit two-inertia clutch coupling avoids stiff oscillation at the 80 Hz simulation step.
            double reflected=ratio*ratio/(MASS*WHEEL_RADIUS*WHEEL_RADIUS);
            clutchTorque=clamp((engine.omega()-wheelOmega)/(.025+dt*(1/EngineBuild.inertia(setup.family,setup.engineParts)+reflected)),-capacity,capacity);
            force=clutchTorque*ratio*.86/WHEEL_RADIUS*(in.reverse?-1:1);
            double tireGrip = grip * (Assembly.WHEELS.variant(setup.config) == 2 ? 1.17 : 1) * ((contacts[2]?WheelDynamics.tireGrip(setup.mechanics,2):0)+(contacts[3]?WheelDynamics.tireGrip(setup.mechanics,3):0))*.5;
            force = clamp(force, -MASS * 9.81 * tireGrip * .56, MASS * 9.81 * tireGrip * .56);
            clutchTorque=force*WHEEL_RADIUS/(ratio*.86)*(in.reverse?-1:1);
        }
        engine=EnginePhysics.step(engine,setup,running,in.throttle,clutchTorque,dt);
        double rpm=engine.rpm();
        if(running)used=(.00022+Math.max(0,engine.shaftTorque())*rpm*1.0e-8)*setup.family.fuelScale*dt;
        double drag = Assembly.BODY.variant(setup.config) == 2 ? .40 : .43;
        var wheelForces=WheelDynamics.step(wheelState,setup.mechanics,setup.config,in,speed,force,grip,contacts,travel,dt);
        double resistance = drag * speed * Math.abs(speed) + Math.signum(speed) * wheelForces.rolling();
        double brakingForce=wheelForces.braking();
        resistance+=Math.abs(speed)<.1?clamp(force,-brakingForce,brakingForce):Math.signum(speed)*brakingForce;
        double next = speed + (force - resistance) / MASS * dt;
        if (Math.signum(next) != Math.signum(speed) && Math.abs(force) < Math.abs(resistance)) next = 0;
        if (grounded) {
            double response = Assembly.SUSPENSION.variant(setup.config) == 2 ? 1.08 : 1;
            double steering = in.steer * .49 * response / (1 + Math.abs(next) * .045);
            double frontGrip=((contacts[0]?WheelDynamics.tireGrip(setup.mechanics,0):0)+(contacts[1]?WheelDynamics.tireGrip(setup.mechanics,1):0))*.5;
            yaw = next / WHEELBASE * Math.tan(steering) * dt * Math.min(1, grip*frontGrip);
            yaw+=Math.signum(next)*wheelForces.yawMoment()/MASS*.002*dt;
        }
        return new State(clamp(next, -14, 65), rpm, gear, Math.max(0, fuel - used), yaw, used,engine,wheelForces.state());
    }
    public static double clamp(double v, double min, double max) { return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : min; }
}
