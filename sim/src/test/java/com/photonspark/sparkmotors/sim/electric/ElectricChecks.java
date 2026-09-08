package com.photonspark.sparkmotors.sim.electric;

import com.photonspark.sparkmotors.sim.*;

/** Dependency-free invariant harness, also called by JUnit. Does not mock the electric algorithms. */
public final class ElectricChecks {
    private static int checks;
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void close(double a,double b,double tolerance,String message){require(Math.abs(a-b)<=tolerance,message+": "+a+" vs "+b);}
    public static void batteryConservation(){
        for(var type:Powertrain.values())if(type.electric())for(double temp:new double[]{-20,0,5,20,50,60})for(double soc:new double[]{0,.01,.2,.8,.99,1})for(double watts:new double[]{-500000,-10000,0,10000,500000}){
            var spec=type.battery;var original=BatteryModel.State.initial(spec,soc);
            var state=new BatteryModel.State(original.energyJ(),temp,.8,0).normalized(spec);
            var exchange=BatteryModel.exchange(spec,state,watts,.0125,20,10);
            close(state.energyJ()-exchange.state().energyJ(),exchange.terminalJ()+exchange.ohmicJ()+exchange.fadeJ(),1e-6,"pack energy conservation");
            require(exchange.state().energyJ()>=0&&exchange.state().soc(spec)<=1,"SOC bounds");
            require(Double.isFinite(exchange.voltageV())&&exchange.ohmicJ()>=0,"finite pack voltage and nonnegative heating");
            if(temp<=0&&watts<0)close(exchange.terminalJ(),0,1e-9,"no cold-cell charging");
        }
    }
    public static void chargers(){
        for(var type:Powertrain.values())if(type.electric())for(var tier:ChargingModel.Tier.values())for(double soc:new double[]{0,.5,.79,.8001,.99,1})for(double temp:new double[]{-10,20,60}){
            var pack=BatteryModel.State.initial(type.battery,soc);pack=new BatteryModel.State(pack.energyJ(),temp,1,0);
            double available=tier.inputLimitW()*.05;
            var result=ChargingModel.step(type,pack,tier,tier.inputV,available,.8,true,false,0,.05,20);
            close(result.inputJ(),result.storedJ()+result.lossJ()+result.heaterJ(),1e-6,"charger energy conservation");
            require(result.inputJ()<=available+1e-8,"source debit cannot exceed available energy");
            if(!type.plugIn()||soc>.8||temp>=55)close(result.inputJ(),0,1e-8,"incompatible/full/hot rejection");
            for(double voltage:new double[]{tier.inputV*.5,tier.inputV*1.11})close(ChargingModel.step(type,pack,tier,voltage,available,.8,true,false,0,.05,20).inputJ(),0,1e-9,"wrong voltage rejection");
            close(ChargingModel.step(type,pack,tier,tier.inputV,available,.8,true,true,0,.05,20).inputJ(),0,1e-9,"READY charge interlock");
        }
    }
    private static VehicleDynamics.Setup setup(){return new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,EnginePart.stock(),90,1.4);}
    public static void driving(){
        for(var type:Powertrain.values())if(type.electric()){
            var state=ElectricDynamics.State.initial(type,.65);var engine=EnginePhysics.State.stopped(90,100);double fuel=40,speed=0,start=state.battery().energyJ();
            for(int i=0;i<2400;i++){
                var r=ElectricDynamics.step(type,state,ElectricDynamics.Mode.ELECTRIC_ONLY,speed,fuel,engine,true,new VehicleDynamics.Input(1,0,false,false,false),setup(),1,true,false,.0125,20);
                speed=r.road().speed();fuel=r.road().fuel();engine=r.road().engine();state=r.electric();
            }
            require(speed>8,"electric acceleration for "+type);require(state.battery().energyJ()<start,"traction consumes battery");
            require(start-state.battery().energyJ()>=.5*type.massKg*speed*speed-1,"launch kinetic energy is paid for");
            close(fuel,40,1e-9,"electric-only mode consumes no fuel");
            double before=state.battery().energyJ(),ke=.5*type.massKg*speed*speed;
            for(int i=0;i<1600;i++){
                var r=ElectricDynamics.step(type,state,ElectricDynamics.Mode.ELECTRIC_ONLY,speed,fuel,engine,true,new VehicleDynamics.Input(0,0,true,false,false),setup(),1,true,false,.0125,20);
                speed=r.road().speed();engine=r.road().engine();state=r.electric();
            }
            close(speed,0,1e-6,"friction brakes stop vehicle");require(state.battery().energyJ()-before<ke,"regen cannot exceed kinetic energy");
        }
        var type=Powertrain.ELECTRIC_400;var empty=ElectricDynamics.State.initial(type,0);
        var r=ElectricDynamics.step(type,empty,ElectricDynamics.Mode.AUTO,0,40,EnginePhysics.State.stopped(20,100),true,new VehicleDynamics.Input(1,0,false,false,false),setup(),1,true,false,.0125,20);
        close(r.road().speed(),0,1e-8,"empty pack cannot launch");
        var unplug=ElectricDynamics.step(type,ElectricDynamics.State.initial(type,.5),ElectricDynamics.Mode.AUTO,0,40,EnginePhysics.State.stopped(20,100),true,new VehicleDynamics.Input(1,0,false,false,false),setup(),1,true,true,.0125,20);
        close(unplug.road().speed(),0,1e-8,"plugged vehicle cannot drive");
    }
    public static void generator(){
        for(var type:new Powertrain[]{Powertrain.HYBRID,Powertrain.PLUG_IN_HYBRID}){
            var state=ElectricDynamics.State.initial(type,.1);var engine=EnginePhysics.State.stopped(90,100);double fuel=10,generated=0,used=0;
            for(int i=0;i<4800;i++){
                var r=ElectricDynamics.step(type,state,ElectricDynamics.Mode.AUTO,0,fuel,engine,true,new VehicleDynamics.Input(0,0,true,false,false),setup(),1,true,false,.0125,20);
                state=r.electric();engine=r.road().engine();fuel=r.road().fuel();generated+=r.generatorOutputJ();used+=r.fuelEnergyJ();
                require(r.generatorOutputJ()<=r.fuelEnergyJ()*.31+1e-8,"generator energy backed by fuel");
            }
            require(generated>10000&&fuel<10,"generator runs and consumes fuel");require(generated<used,"conversion losses");
        }
    }
    public static void main(String[] args){batteryConservation();chargers();driving();generator();System.out.println("PASS: "+checks+" electrification invariant assertions");}
}
