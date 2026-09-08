package com.photonspark.sparkmotors.sim.electric;

import com.photonspark.sparkmotors.sim.*;
import static com.photonspark.sparkmotors.sim.VehicleDynamics.clamp;

/** Server fixed-step electric / parallel hybrid drivetrain. Shaft work uses midpoint wheel velocity, including launch.
 * Generator output is paid for by crank load and fuel; rejected generation is dumped, never stored twice.
 * Simplified calibrated motor/generator maps, not an OEM efficiency map.
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
                         double fuelEnergyJ,double generatorOutputJ,double dumpedJ,MechanicalState mechanics,double auxiliaryW) {}
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
        setup=setup.withPowertrain(type);
        var mechanics=setup.mechanics();boolean detailed=mechanics!=null&&mechanics.version()>=2;
        var battery=state.battery.normalized(type.battery);
        ready=ready&&!connected&&battery.health()>=.1&&PowertrainTopology.liveHv(mechanics);
        double soc=battery.soc(type.battery);
        boolean engineAvailable=ready&&oldFuel>0&&engine.health()>5
            &&setup.temperature()<125&&Assembly.ENGINE.variant(setup.config())>0&&EnginePart.ready(setup.engineParts());
        if(type.hybrid()&&mechanics!=null)engineAvailable&=MechanicalCapabilities.canRun(mechanics,setup.engineParts())
            &&engine.mode()!=EnginePhysics.Mode.STALLED&&(engine.mode()==EnginePhysics.Mode.RUNNING||MechanicalCapabilities.canCrank(mechanics,setup.family()));
        double generator=0,fuelJ=0,engineLoad=0,assist=1,generatorCapability=detailed?mechanics.capability("traction.generator"):1;
        double[] mechanicalTorque=new double[4];
        boolean generate=false;
        double accept=BatteryModel.chargeLimitW(type.battery,battery,dt);
        if(type.hybrid()) {
            var control=HybridControl.demand(type,mode,state.generating,soc,v,engine,input,engineAvailable);
            generate=control.engineOn();assist=control.assist();
            double charge=Math.min(control.chargeW(),Math.max(0,accept-450))*generatorCapability;
            // Keep crank acceleration available for launch / maximum pedal demand.
            // A weak engine must not be pinned at clutch bite RPM by its generator.
            if(input.throttle()>.75||!control.disconnect()&&input.throttle()>0&&engine.rpm()<1800)charge=0;
            double pedal=control.pedal();
            if(!control.disconnect()&&charge>0)pedal=Math.min(1,pedal+.16);
            if(control.disconnect()&&charge>0)pedal=clamp(.40+(generatorTargetRpm(setup.family())-engine.rpm())/2200,.12,.85);
            // A full/cold battery must not make the parked engine rev against an unavailable charger.
            if(control.disconnect()&&input.throttle()==0&&charge==0)generate=false;
            var command=new VehicleDynamics.Input(pedal,input.steer(),input.brake(),input.reverse(),control.disconnect(),input.handbrake());
            var power=VehicleDynamics.powerStage(v,oldFuel,engine,wheels,trans,generate,command,setup,contacts,charge,dt);
            engine=power.engine();trans=power.transmission();mechanicalTorque=power.torques();
            generator=power.generatorW();fuelJ=power.fuelUsed()*FUEL_J_PER_L;engineLoad=power.load();
        } else engine=EnginePhysics.State.stopped(engine.oilTemperature(),engine.health());
        // An EV uses the same contact patches, differentials, brakes and yaw integration as an ICE car.
        int gear=type.hybrid()&&mode!=Mode.ELECTRIC_ONLY?trans.gear():input.reverse()?-1:1;
        if(!type.hybrid()||mode==Mode.ELECTRIC_ONLY)trans=new TransmissionPhysics.State(gear,gear,0,trans.clutchHeat(),trans.lateralSpeed(),trans.yawRate(),trans.steering(),trans.longitudinalAcceleration(),trans.lateralAcceleration());
        double[] desired=new double[2],maximum=new double[2],oldOmega=new double[4];
        boolean regen=ready&&input.brake()&&Math.abs(speed)>.5;
        double aux=ready?450:0,packLimit=BatteryModel.dischargeLimitW(type.battery,battery,dt);
        if(type.hybrid()){
            double aboveReserve=Math.max(0,battery.energyJ()-type.battery.capacityJ()*battery.health()*HybridControl.reserve(type,mode));
            packLimit=Math.min(packLimit,aboveReserve/dt*.85);
        }
        double budget=Math.max(0,packLimit+generator-aux);
        for(int c=0;c<4;c++)oldOmega[c]=wheels.initialized()?wheels.corners().get(c).omega():speed/VehicleDynamics.WHEEL_RADIUS;
        for(int axle=0;axle<2;axle++){
            double share=axle==0?setup.drive().frontFraction():1-setup.drive().frontFraction();
            double omega=(oldOmega[axle*2]+oldOmega[axle*2+1])*.5;
            double motor=temperature(mechanics,"motor",axle,state.motorC),inverter=temperature(mechanics,"inverter",axle,state.inverterC);
            double derate=clamp((160-motor)/40,0,1)*clamp((110-inverter)/25,0,1);
            double capability=Assembly.TRANSMISSION.variant(setup.config())>0?PowertrainTopology.axleCapability(mechanics,type,setup.drive(),axle):0;
            maximum[axle]=motorTorque(type,motorRpm(omega))*share*REDUCTION*setup.drive().efficiency()*derate*capability;
            if(regen){
                // Pedal intent and the electrical torque path command regen, independently of hydraulic pads/hoses.
                // No airborne regeneration, nor rear motor braking against the applied handbrake.
                if((contacts[axle*2]||contacts[axle*2+1])&&Math.abs(omega)>2&&!(axle==1&&input.handbrake())&&accept+aux>0)
                    desired[axle]=-Math.signum(omega)*Math.min(maximum[axle]*.65,type.massKg*3*VehicleDynamics.WHEEL_RADIUS*share);
            }else if(ready&&!input.brake()&&budget>0)desired[axle]=(input.reverse()?-1:1)*maximum[axle]*input.throttle()*assist;
        }
        double[] full=PowertrainTopology.torques(mechanics,type,setup.drive(),wheels,desired[0],desired[1],dt);
        // A wheel with no road contact cannot collect regeneration from its wheel inertia.
        if(regen)for(int c=0;c<4;c++)if(!contacts[c])full[c]=0;
        double lo=0,hi=1;
        // Most requests already fit the electrical limits. Solve that case once. Only
        // constrained requests need a search, keeping its last feasible chassis result.
        // Fourteen bisections bound torque error below 0.007%; always choose the paid side.
        VehicleDynamics.State roadState=null;
        for(int n=0;n<15;n++){
            double fraction=n==0?1:(lo+hi)*.5;double[] torque=scaled(full,fraction),replaced=regen?absolute(torque):new double[4];
            var trial=VehicleDynamics.chassisTorques(v,oldFuel,engine,wheels,trans,input,setup,grip,contacts,travel,added(torque,mechanicalTorque),replaced,dt);
            double watts=0;
            for(int axle=0;axle<2;axle++)watts+=electricalW(type,setup,torque,oldOmega,trial.wheels(),axle);
            if(watts<=budget+1e-8&&watts>=-(accept+aux)+1e-8){lo=fraction;roadState=trial;if(n==0)break;}else hi=fraction;
            if(desired[0]==0&&desired[1]==0)break;
        }
        double[] torque=scaled(full,lo),replaced=regen?absolute(torque):new double[4];
        if(roadState==null)roadState=VehicleDynamics.chassisTorques(v,oldFuel,engine,wheels,trans,input,setup,grip,contacts,travel,added(torque,mechanicalTorque),replaced,dt);
        double tractionW=0,shaftW=0,motorC=-60,inverterC=-60;
        var thermal=detailed?new java.util.HashMap<>(mechanics.parts()):null;
        double coolantHeat=0;
        for(int axle=0;axle<2;axle++){
            double work=workW(torque,oldOmega,roadState.wheels(),axle),watts=electricalW(type,setup,torque,oldOmega,roadState.wheels(),axle);
            shaftW+=work;tractionW+=watts;
            double loss=Math.max(0,watts-work);
            double oldMotor=temperature(mechanics,"motor",axle,state.motorC),oldInverter=temperature(mechanics,"inverter",axle,state.inverterC);
            double circulation=detailed?mechanics.capability("cooling.pump")*clamp(mechanics.coolant()/6,0,1):1;
            double sink=detailed?mechanics.coolantTemperature():ambientC;
            double mt=cool(oldMotor,loss*.75,18_000,5+circulation*(20+Math.abs(v)*2),dt,sink);
            double it=cool(oldInverter,loss*.25,9_000,4+circulation*(14+Math.abs(v)),dt,sink);
            if((axle==0&&PowertrainTopology.front(setup.drive()))||(axle==1&&PowertrainTopology.rear(setup.drive()))){motorC=Math.max(motorC,mt);inverterC=Math.max(inverterC,it);}
            if(detailed){
                double transferred=0;if(mechanics.get(PowertrainTopology.unit("motor",axle))!=null)transferred+=loss*.75*dt-(mt-oldMotor)*18000;
                if(mechanics.get(PowertrainTopology.unit("inverter",axle))!=null)transferred+=loss*.25*dt-(it-oldInverter)*9000;
                heatPart(thermal,PowertrainTopology.unit("motor",axle),mt,Math.max(0,mt-145)*dt*.000001);heatPart(thermal,PowertrainTopology.unit("inverter",axle),it,Math.max(0,it-100)*dt*.000002);
                coolantHeat+=transferred;
            }
        }
        if(detailed)mechanics=mechanics.update(thermal,mechanics.coolant(),mechanics.oil(),mechanics.brakeFluid(),mechanics.coolantTemperature()+coolantHeat/(26000+4180*mechanics.coolant()),mechanics.oilTemperature(),mechanics.distance(),mechanics.faultHistory());
        double request=tractionW+aux-generator;
        var exchange=BatteryModel.exchange(type.battery,battery,request,dt,ambientC,roadState.groundSpeed());
        double actual=exchange.terminalJ()/dt,dumped=Math.max(0,actual-request)*dt;
        double regenStored=Math.max(0,Math.min(-tractionW,-actual));
        var result=new State(exchange.state(),motorC,inverterC,generate,actual,exchange.currentA(),exchange.voltageV(),regenStored,generator);
        double used=fuelJ/FUEL_J_PER_L;
        roadState=new VehicleDynamics.State(roadState.speed(),engine.rpm(),gear,Math.max(0,oldFuel-used),roadState.yawDelta(),used,engine,roadState.wheels(),roadState.transmission(),Math.max(engineLoad,clamp((Math.abs(desired[0])+Math.abs(desired[1]))*lo/Math.max(1,maximum[0]+maximum[1]),0,1)));
        return new Result(roadState,result,Math.max(0,shaftW)*dt,Math.max(0,-shaftW)*dt,fuelJ,generator*dt,dumped,mechanics,clamp(actual+generator-tractionW,0,aux));
    }
    public static double generatorTargetRpm(EngineFamily f){return f.rotary()?4000+(f.ordinal()-3)*150:f==EngineFamily.V6?2600:f==EngineFamily.FLAT4?3000:3200;}
    public static double generatorEfficiency(EngineFamily f,double rpm,double load){return clamp((f.rotary()?.28:.32)-.06*Math.pow(clamp(load,0,1)-.7,2)-.025*Math.pow((rpm-generatorTargetRpm(f))/3000,2),.18,.32);}
    private static double[] scaled(double[] values,double scale){return new double[]{values[0]*scale,values[1]*scale,values[2]*scale,values[3]*scale};}
    private static double[] added(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2],a[3]+b[3]};}
    private static double[] absolute(double[] values){return new double[]{Math.abs(values[0]),Math.abs(values[1]),Math.abs(values[2]),Math.abs(values[3])};}
    private static double workW(double[] torque,double[] old,WheelDynamics.State wheels,int axle){double result=0;for(int c=axle*2;c<axle*2+2;c++)result+=torque[c]*(old[c]+wheels.corners().get(c).omega())*.5;return result;}
    private static double electricalW(Powertrain type,VehicleDynamics.Setup setup,double[] torque,double[] old,WheelDynamics.State wheels,int axle){
        double work=workW(torque,old,wheels,axle),total=torque[axle*2]+torque[axle*2+1];
        double copper=1100*Math.pow(total/Math.max(1,type.torqueNm*REDUCTION),2);
        return work>=0?work/(MOTOR_EFF*setup.drive().efficiency())+copper:work*REGEN_EFF*setup.drive().efficiency()+copper;
    }
    private static double temperature(MechanicalState m,String unit,int axle,double fallback){var p=m==null?null:m.get(PowertrainTopology.unit(unit,axle));return p==null?(m!=null&&m.version()>=2?20:fallback):p.temperature();}
    private static void heatPart(java.util.Map<String,PartInstance> m,String key,double temperature,double wear){var p=m.get(key);if(p!=null)m.put(key,p.condition(p.wear()+wear,p.damage(),p.faults()).operating(p.reserve(),temperature));}
    private static double cool(double old,double heat,double capacity,double conductance,double dt,double ambient){
        double equilibrium=clamp(ambient,-50,180)+heat/conductance;
        return clamp(equilibrium+(old-equilibrium)*Math.exp(-conductance*dt/capacity),-60,250);
    }
}
