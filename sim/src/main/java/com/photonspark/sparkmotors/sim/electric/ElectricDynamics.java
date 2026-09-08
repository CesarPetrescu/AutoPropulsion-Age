package com.photonspark.sparkmotors.sim.electric;

import com.photonspark.sparkmotors.sim.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Server fixed-step series drivetrain. Road work uses midpoint velocity (including launch), not F*v_old.
 * Generator output is paid for by crank load and fuel; rejected generation is dumped, never stored twice.
 * Simplified calibrated motor/generator maps, not an OEM efficiency map or parallel/power-split hybrid.
 */
public final class ElectricDynamics {
    public enum Mode { AUTO, ELECTRIC_ONLY, CHARGE_SUSTAIN }
    public record State(BatteryModel.State battery,double motorC,double inverterC,boolean generating,
                        double packW,double currentA,double voltageV,double regenW,double generatorW) {
        public static State initial(Powertrain type,double soc) {
            return new State(BatteryModel.State.initial(type.battery,soc),20,20,false,0,0,type.battery.nominalV(),0,0);
        }
        public State withBattery(BatteryModel.State s){return new State(s,motorC,inverterC,generating,packW,currentA,voltageV,regenW,generatorW);}
    }
    public record Result(VehicleDynamics.State road,State electric,double wheelWorkJ,double recoveredWheelJ,
                         double fuelEnergyJ,double generatorOutputJ,double dumpedJ) {}
    public static final double REDUCTION=9.1;
    private static final double MOTOR_EFF=.91,REGEN_EFF=.82,FUEL_J_PER_L=34_200_000;
    public static double motorRpm(double drivenWheelOmega){return Math.abs(drivenWheelOmega)*REDUCTION*30/Math.PI;}
    /** Constant torque below base speed, constant power above, then motor overspeed taper. */
    public static double motorTorque(Powertrain type,double rpm){
        if(!Double.isFinite(rpm)||rpm<0)return 0;
        return Math.min(type.torqueNm,type.motorKw*1000/Math.max(1,rpm*Math.PI/30))*clamp((18000-rpm)/1500,0,1);
    }
    private ElectricDynamics(){}
    public static Result step(Powertrain type,State state,Mode mode,double speed,double fuel,
                              EnginePhysics.State engine,boolean ready,VehicleDynamics.Input input,
                              VehicleDynamics.Setup setup,double grip,boolean grounded,boolean connected,double dt,double ambientC) {
        return step(type,state,mode,speed,fuel,engine,WheelDynamics.State.stopped(),TransmissionPhysics.State.stopped(),ready,input,setup,new double[]{grip,grip,grip,grip},new boolean[]{grounded,grounded,grounded,grounded},new double[4],connected,dt,ambientC);
    }
    public static Result step(Powertrain type,State state,Mode mode,double speed,double fuel,EnginePhysics.State engine,WheelDynamics.State wheels,TransmissionPhysics.State trans,boolean ready,VehicleDynamics.Input input,VehicleDynamics.Setup setup,double[] grip,boolean[] contacts,double[] travel,boolean connected,double dt,double ambientC){
        if(!type.electric()||!Double.isFinite(dt)||dt<=0||dt>.05)throw new IllegalArgumentException("Electric type and dt in (0,.05] required");
        double v=clamp(speed,-65,65),oldFuel=clamp(fuel,0,50);
        setup=setup.withMass(type.massKg);
        var battery=state.battery.normalized(type.battery);
        ready=ready&&!connected&&battery.health()>=.1;
        double soc=battery.soc(type.battery);
        double lower=type==Powertrain.HYBRID?.45:.18,upper=type==Powertrain.HYBRID?.65:.30;
        if(mode==Mode.CHARGE_SUSTAIN){lower=.55;upper=.65;}
        boolean generate=state.generating?soc<upper:soc<lower;
        generate=generate&&type.hybrid()&&mode!=Mode.ELECTRIC_ONLY&&ready&&oldFuel>0&&engine.health()>5
            &&setup.temperature()<125&&Assembly.ENGINE.variant(setup.config())>0&&EnginePart.ready(setup.engineParts());
        double generator=0,fuelJ=0;
        if(type.hybrid()) {
            // Govern the existing installed engine at 3000 rpm. Load is limited by instantaneous shaft output.
            double pedal=generate?clamp(.55+(3000-engine.rpm())/2200,.15,1):0;
            double load=generate&&engine.rpm()>900?Math.min(type.generatorKw*1000/(Math.max(100,engine.omega())*.91),Math.max(0,engine.shaftTorque())*.96):0;
            var next=EnginePhysics.step(engine,setup,generate,pedal,load,dt);
            double mechanical=Math.max(0,load*(engine.omega()+next.omega())*.5);
            generator=mechanical*.91;
            // 30% engine conversion plus idling overhead; engine rotation is not a free generator source.
            fuelJ=generate?(mechanical/.30+4500)*dt:0;
            if(fuelJ>oldFuel*FUEL_J_PER_L){double f=oldFuel*FUEL_J_PER_L/Math.max(1,fuelJ);generator*=f;fuelJ=oldFuel*FUEL_J_PER_L;}
            engine=next;
        } else engine=EnginePhysics.State.stopped(engine.oilTemperature(),engine.health());
        // An EV uses the same contact patches, differentials, brakes and yaw integration as an ICE car.
        // No mechanical clutch in this fixed-reduction series drivetrain.
        int gear=input.reverse()?-1:1;
        trans=new TransmissionPhysics.State(gear,gear,0,20,trans.lateralSpeed(),trans.yawRate(),trans.steering(),trans.longitudinalAcceleration(),trans.lateralAcceleration());
        double omega=wheels.initialized()?setup.drive().drivenOmega(wheels):speed/VehicleDynamics.WHEEL_RADIUS;
        double motorDerate=clamp((160-state.motorC)/40,0,1)*clamp((110-state.inverterC)/25,0,1);
        double capability=Assembly.TRANSMISSION.variant(setup.config())>0?MechanicalCapabilities.transmission(setup.mechanics()):0;
        double maxTorque=motorTorque(type,motorRpm(omega))*REDUCTION*setup.drive().efficiency()*motorDerate*capability;
        double aux=ready?450:0,packLimit=BatteryModel.dischargeLimitW(type.battery,battery,dt);
        double budget=Math.max(0,packLimit+generator-aux),accept=BatteryModel.chargeLimitW(type.battery,battery,dt);
        double direction=input.reverse()?-1:1;
        boolean drivenContact=false;
        for(int c=0;c<4;c++)if(contacts[c]&&(c<2?setup.drive().frontFraction()>0:setup.drive().frontFraction()<1))drivenContact=true;
        boolean regen=ready&&input.brake()&&drivenContact&&Math.abs(omega)>2&&Math.abs(speed)>.5;
        double desired=regen?-Math.signum(omega)*maxTorque*.65:ready&&!input.brake()?direction*maxTorque*input.throttle():0;
        if(!regen&&budget<=0)desired=0;
        if(regen&&accept+aux<=0)desired=0;
        double serviceTorque=0;
        for(int c=0;c<4;c++)if(contacts[c]&&Assembly.BRAKES.variant(setup.config())>0)
            serviceTorque+=type.massKg*(Assembly.BRAKES.variant(setup.config())==2?10.8:8)*(c<2?.30:.20)*WheelDynamics.brakeCapability(setup.mechanics(),c,true)*VehicleDynamics.WHEEL_RADIUS;
        if(regen)desired=Math.copySign(Math.min(Math.abs(desired),serviceTorque*.65),desired);
        // Evaluate shaft work with the new wheel speed. This pays for launch and airborne wheel inertia,
        // and bounds regen by actual shaft work and pack acceptance, including full/cold batteries.
        double lo=0,hi=1;
        for(int n=0;n<28;n++){
            double fraction=(lo+hi)*.5,torque=desired*fraction;
            double friction=regen?1-clamp(Math.abs(torque)/Math.max(1,serviceTorque),0,.65):1;
            var trial=VehicleDynamics.chassis(v,oldFuel,engine,wheels,trans,input,setup,grip,contacts,travel,torque,friction,dt);
            double work=torque*(omega+setup.drive().drivenOmega(trial.wheels()))*.5;
            double copper=1100*Math.pow(torque/Math.max(1,type.torqueNm*REDUCTION),2);
            double watts=work>=0?work/MOTOR_EFF+copper:work*REGEN_EFF;
            if(watts<=budget+1e-8&&watts>=-(accept+aux)+1e-8)lo=fraction;else hi=fraction;
        }
        double torque=desired*lo,friction=regen?1-clamp(Math.abs(torque)/Math.max(1,serviceTorque),0,.65):1;
        var roadState=VehicleDynamics.chassis(v,oldFuel,engine,wheels,trans,input,setup,grip,contacts,travel,torque,friction,dt);
        double shaftW=torque*(omega+setup.drive().drivenOmega(roadState.wheels()))*.5;
        double copper=1100*Math.pow(torque/Math.max(1,type.torqueNm*REDUCTION),2);
        double tractionW=shaftW>=0?shaftW/MOTOR_EFF+copper:shaftW*REGEN_EFF;
        double loss=Math.max(0,tractionW-shaftW),request=tractionW+aux-generator;
        var exchange=BatteryModel.exchange(type.battery,battery,request,dt,ambientC,roadState.groundSpeed());
        double actual=exchange.terminalJ()/dt,dumped=Math.max(0,actual-request)*dt;
        double regenStored=Math.max(0,Math.min(-tractionW,-actual));
        double motorC=cool(state.motorC,loss*.75,18_000,25+Math.abs(v)*2,dt,ambientC);
        double inverterC=cool(state.inverterC,loss*.25,9_000,18+Math.abs(v),dt,ambientC);
        var result=new State(exchange.state(),motorC,inverterC,generate,actual,exchange.currentA(),exchange.voltageV(),regenStored,generator);
        double used=fuelJ/FUEL_J_PER_L;
        roadState=new VehicleDynamics.State(roadState.speed(),engine.rpm(),gear,Math.max(0,oldFuel-used),roadState.yawDelta(),used,engine,roadState.wheels(),roadState.transmission(),clamp(Math.abs(torque)/Math.max(1,maxTorque),0,1));
        return new Result(roadState,result,Math.max(0,shaftW)*dt,Math.max(0,-shaftW)*dt,fuelJ,generator*dt,dumped);
    }
    private static double cool(double old,double heat,double capacity,double conductance,double dt,double ambient){
        double equilibrium=clamp(ambient,-50,60)+heat/conductance;
        return clamp(equilibrium+(old-equilibrium)*Math.exp(-conductance*dt/capacity),-60,250);
    }
}
