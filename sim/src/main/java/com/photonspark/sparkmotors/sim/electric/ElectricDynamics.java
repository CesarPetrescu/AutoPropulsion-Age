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
    private static final double REDUCTION=9.1,RADIUS=.34,MOTOR_EFF=.91,REGEN_EFF=.82,FUEL_J_PER_L=34_200_000;
    private ElectricDynamics(){}
    public static Result step(Powertrain type,State state,Mode mode,double speed,double fuel,
                              EnginePhysics.State engine,boolean ready,VehicleDynamics.Input input,
                              VehicleDynamics.Setup setup,double grip,boolean grounded,boolean connected,double dt,double ambientC) {
        if(!type.electric()||!Double.isFinite(dt)||dt<=0||dt>.05)throw new IllegalArgumentException("Electric type and dt in (0,.05] required");
        double v=clamp(speed,-14,65),mass=type.massKg,oldFuel=clamp(fuel,0,60);
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
        double road=grounded?140+.43*v*v:.43*v*v;
        double throttle=ready&&grounded&&!input.brake()?clamp(input.throttle(),0,1):0;
        double motorDerate=clamp((160-state.motorC)/40,0,1)*clamp((110-state.inverterC)/25,0,1);
        double maxForce=Math.min(type.torqueNm*REDUCTION/RADIUS,type.motorKw*1000/Math.max(1,Math.abs(v)))*motorDerate;
        maxForce=Math.min(maxForce,mass*9.81*clamp(grip,0,1.5)*.65)*throttle;
        double aux=ready?450:0,driveWork=0,recovered=0,nextV=v,motorLoss=0,inverterLoss=0;
        double packLimit=BatteryModel.dischargeLimitW(type.battery,battery,dt);
        double budget=Math.max(0,packLimit+generator-aux);
        double tractionW=0;
        if(input.brake()&&grounded&&Math.abs(v)>0) {
            double brake=mass*(Assembly.BRAKES.variant(setup.config())==2?10.8:8)*clamp(grip,.05,1.5);
            nextV=Math.copySign(Math.max(0,Math.abs(v)-(brake+road)/mass*dt),v);
            double lostKinetic=.5*mass*(v*v-nextV*nextV);
            double brakeWork=lostKinetic*brake/Math.max(1,brake+road);
            double regenFraction=ready?Math.min(1,type.torqueNm*REDUCTION/RADIUS*.65/brake)*clamp(Math.abs(v)/2,0,1):0;
            double accept=BatteryModel.chargeLimitW(type.battery,battery,dt);
            recovered=Math.min(brakeWork*regenFraction,Math.min(type.motorKw*600*dt,(accept+aux)/REGEN_EFF*dt));
            tractionW=-recovered/dt*REGEN_EFF;motorLoss=recovered/dt*(1-REGEN_EFF);
        } else if(maxForce>0) {
            double sign=input.reverse()?-1:1;
            double lo=0,hi=maxForce;
            // Bisection includes the kinetic energy gained FROM REST and copper heating at low speed.
            for(int n=0;n<32;n++) {
                double force=(lo+hi)*.5;
                double nv=nextSpeed(v,sign*force,road,mass,dt,input.reverse());
                double work=Math.max(0,sign*force*(v+nv)*.5*dt);
                double copper=1100*Math.pow(force/Math.max(1,type.torqueNm*REDUCTION/RADIUS),2);
                double requested=work/dt/MOTOR_EFF+copper;
                if(requested<=budget)lo=force;else hi=force;
            }
            nextV=nextSpeed(v,sign*lo,road,mass,dt,input.reverse());
            driveWork=Math.max(0,sign*lo*(v+nextV)*.5*dt);
            double copper=1100*Math.pow(lo/Math.max(1,type.torqueNm*REDUCTION/RADIUS),2);
            tractionW=driveWork/dt/MOTOR_EFF+copper;motorLoss=tractionW-driveWork/dt;inverterLoss=motorLoss*.25;motorLoss*=.75;
        } else if(grounded) nextV=Math.copySign(Math.max(0,Math.abs(v)-road/mass*dt),v);
        double request=tractionW+aux-generator;
        var exchange=BatteryModel.exchange(type.battery,battery,request,dt,ambientC,v);
        double actual=exchange.terminalJ()/dt;
        double dumped=Math.max(0,actual-request)*dt;
        // Cold/full pack rejection cannot amplify regenerative braking; the rejected part is friction/dump heat.
        double regenStored=Math.max(0,Math.min(recovered/dt*REGEN_EFF,-actual));
        double motorC=cool(state.motorC,motorLoss,18_000,25+Math.abs(v)*2,dt,ambientC);
        double inverterC=cool(state.inverterC,inverterLoss,9_000,18+Math.abs(v),dt,ambientC);
        double yaw=grounded?nextV/2.65*Math.tan(clamp(input.steer(),-1,1)*.5/(1+Math.abs(nextV)*.035))*dt:0;
        var result=new State(exchange.state(),motorC,inverterC,generate,actual,exchange.currentA(),exchange.voltageV(),regenStored,generator);
        var roadState=new VehicleDynamics.State(nextV,engine.rpm(),input.reverse()?-1:1,Math.max(0,oldFuel-fuelJ/FUEL_J_PER_L),yaw,fuelJ/FUEL_J_PER_L,engine);
        return new Result(roadState,result,driveWork,recovered,fuelJ,generator*dt,dumped);
    }
    private static double nextSpeed(double v,double force,double road,double mass,double dt,boolean reverse) {
        double drag=Math.abs(v)>.001?Math.copySign(road,v):Math.copySign(Math.min(road,Math.abs(force)),force);
        double nv=v+(force-drag)/mass*dt;
        return reverse?clamp(nv,-14,Math.max(0,v)):clamp(nv,Math.min(0,v),65);
    }
    private static double cool(double old,double heat,double capacity,double conductance,double dt,double ambient){
        double equilibrium=clamp(ambient,-50,60)+heat/conductance;
        return clamp(equilibrium+(old-equilibrium)*Math.exp(-conductance*dt/capacity),-60,250);
    }
}
