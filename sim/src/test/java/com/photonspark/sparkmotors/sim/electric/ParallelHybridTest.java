package com.photonspark.sparkmotors.sim.electric;

import com.photonspark.sparkmotors.sim.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParallelHybridTest {
    private static final double DT=.0125;
    private static final VehicleDynamics.Input GO=new VehicleDynamics.Input(1,0,false,false),PARK=new VehicleDynamics.Input(0,0,true,false);
    private static final class Car {
        final Powertrain type;ElectricDynamics.Mode mode=ElectricDynamics.Mode.AUTO;
        ElectricDynamics.State pack;VehicleDynamics.State road=new VehicleDynamics.State(0,0,1,40,0,0);VehicleDynamics.Setup setup;
        double wheelJ,generatedJ,fuelJ;
        Car(Powertrain t,DriveConfig.Layout d,EngineFamily f,double soc){
            type=t;var drive=DriveConfig.preset(d);var m=PowertrainTopology.fresh(t,drive,f,Assembly.stock(),EnginePart.stock());
            setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,f,EnginePart.stock(),20,1,m,drive).withPowertrain(t);
            pack=ElectricDynamics.State.initial(t,soc);
        }
        ElectricDynamics.Result tick(VehicleDynamics.Input in){
            var r=ElectricDynamics.step(type,pack,mode,road.speed(),road.fuel(),road.engine(),road.wheels(),road.transmission(),true,in,setup,new double[]{1,1,1,1},new boolean[]{true,true,true,true},new double[4],false,DT,20);
            pack=r.electric();road=r.road();setup=setup.withMechanics(r.mechanics());wheelJ+=r.wheelWorkJ();generatedJ+=r.generatorOutputJ();fuelJ+=r.fuelEnergyJ();
            return r;
        }
        void run(int n,VehicleDynamics.Input in){for(int i=0;i<n;i++)tick(in);}
        double soc(){return pack.battery().soc(type.battery);}
        void moving(double speed){
            var engine=new EnginePhysics.State(3500*Math.PI/30,.5,0,0,90,4,100,14.7,100,0,false);
            var wheels=new WheelDynamics.State(Collections.nCopies(4,new WheelDynamics.Corner(speed/.34,0,0,true,0,0,20)));
            road=new VehicleDynamics.State(speed,3500,3,40,0,0,engine,wheels,new TransmissionPhysics.State(3,3,0,20,0,0));
        }
    }
    @Test void allFamiliesAndLayoutsCanLaunchOnEngineAtBatteryReserve(){
        for(var t:List.of(Powertrain.HYBRID,Powertrain.PLUG_IN_HYBRID))for(var d:DriveConfig.Layout.values())for(var f:EngineFamily.values()){
            var c=new Car(t,d,f,HybridControl.reserve(t,ElectricDynamics.Mode.AUTO));
            c.run(1200,GO);
            assertTrue(c.road.speed()>4,t+" "+d+" "+f+" speed="+c.road.speed()+" rpm="+c.road.rpm());
            assertTrue(c.road.fuel()<40);assertTrue(c.soc()>HybridControl.reserve(t,c.mode)-.01);
        }
    }
    @Test void highwayDriveUsesFuelAndMechanicalClutchWithZeroMotorTraction(){
        for(var t:List.of(Powertrain.HYBRID,Powertrain.PLUG_IN_HYBRID))for(var d:DriveConfig.Layout.values()){
            var c=new Car(t,d,EngineFamily.I4,.60);c.moving(25);
            c.run(400,GO);
            assertTrue(c.road.speed()>25,t+" "+d+" "+c.road.speed());
            assertEquals(0,c.wheelJ,1e-8,"No motor propulsion above 80 km/h");
            assertTrue(c.fuelJ>1000);assertTrue(c.road.transmission().clutchTorque()>0);
            assertTrue(c.pack.packW()<1000,"Only auxiliaries draw from battery");
        }
    }
    @Test void brokenClutchStopsEnginePathButNotIndependentMotorPath(){
        var c=new Car(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.RWD,EngineFamily.I4,.65);
        c.setup=c.setup.withMechanics(c.setup.mechanics().with("driveline.clutch",null));c.moving(25);c.run(400,GO);
        assertTrue(c.road.speed()<25);assertEquals(0,c.road.transmission().clutchTorque());
        c=new Car(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.RWD,EngineFamily.I4,.65);
        c.setup=c.setup.withMechanics(c.setup.mechanics().with("driveline.clutch",null));c.mode=ElectricDynamics.Mode.ELECTRIC_ONLY;c.run(400,GO);
        assertTrue(c.road.speed()>5);assertEquals(0,c.fuelJ);
    }
    @Test void failedElectricMotorDoesNotDisableHealthyMechanicalOutput(){
        var c=new Car(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.RWD,EngineFamily.I4,.12);
        c.setup=c.setup.withMechanics(c.setup.mechanics().with("traction.motor_rear",null));c.run(1000,GO);
        assertTrue(c.road.speed()>5);assertEquals(0,c.wheelJ,1e-8);
    }
    @Test void prolongedDemandDoesNotSpendTheHybridReserve(){
        for(var t:List.of(Powertrain.HYBRID,Powertrain.PLUG_IN_HYBRID)){
            double reserve=HybridControl.reserve(t,ElectricDynamics.Mode.AUTO);var c=new Car(t,DriveConfig.Layout.AWD,EngineFamily.I4,reserve+.015);
            c.run(9600,GO);
            assertTrue(c.soc()>reserve-.01,t+" depleted reserve: "+c.soc());assertTrue(c.road.speed()>10);
        }
    }
    @Test void chargingIsPaidAndBacksOffWhenEngineTorqueIsNeeded(){
        var parked=new Car(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.AWD,EngineFamily.I4,.13);parked.run(2400,PARK);
        assertTrue(parked.soc()>.13);assertTrue(parked.generatedJ>10000);assertTrue(parked.generatedJ<parked.fuelJ*.31);
        var c=new Car(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.RWD,EngineFamily.I4,.13);c.moving(25);
        c.run(800,new VehicleDynamics.Input(.30,0,false,false));double cruising=c.generatedJ/10;
        assertTrue(cruising>100,"Spare engine output charges while moving");
        double generated=c.generatedJ;c.run(160,GO);double accelerating=(c.generatedJ-generated)/2;
        assertTrue(accelerating<cruising,"Charge backs off: cruise="+cruising+" W, acceleration="+accelerating+" W");
    }
    @Test void fullColdAndFailedGeneratorDoNotReceiveUnacceptedCharge(){
        for(int fault=0;fault<3;fault++){
            var c=new Car(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.RWD,EngineFamily.I4,fault==0?1:.13);
            if(fault==1)c.pack=c.pack.withBattery(new BatteryModel.State(c.pack.battery().energyJ(),-20,1,0));
            if(fault==2)c.setup=c.setup.withMechanics(c.setup.mechanics().with("traction.generator",null));
            c.run(400,PARK);assertEquals(0,c.generatedJ,1e-8);
        }
    }
    @Test void v2HybridMigrationPreservesConditionAndNeverHealsAgain(){
        var c=new Car(Powertrain.HYBRID,DriveConfig.Layout.AWD,EngineFamily.V6,.5);
        var m=c.setup.mechanics();var map=new HashMap<>(m.parts());
        for(String key:List.of("driveline.clutch","driveline.gearbox","driveline.shaft","driveline.transfer"))map.remove(key);
        var donor=map.get("traction.generator").condition(.6,.3,PartInstance.BENT);map.put("traction.generator",donor);
        var old=new MechanicalState(2,map,3,2,.5,95,100,123,Set.of("COOLANT_LOW"));
        var migrated=PowertrainTopology.migrate(old,c.type,c.setup.drive(),EngineFamily.V6,Assembly.stock(),EnginePart.stock());
        for(var e:old.parts().entrySet())assertEquals(e.getValue(),migrated.get(e.getKey()));
        assertEquals(.6,migrated.get("driveline.clutch").wear());assertEquals(.3,migrated.get("driveline.gearbox").damage());
        assertEquals(3,migrated.coolant());assertEquals(123,migrated.distance());
        var removed=migrated.with("driveline.clutch",null);
        assertEquals(removed,PowertrainTopology.migrate(removed,c.type,c.setup.drive(),EngineFamily.V6,Assembly.stock(),EnginePart.stock()));
        assertEquals(migrated,PowertrainTopology.migrate(old,c.type,c.setup.drive(),EngineFamily.V6,Assembly.stock(),EnginePart.stock()));
    }
    @Test void completeHybridLaunchAndBrakeCannotCreateEnergy(){
        for(var t:List.of(Powertrain.HYBRID,Powertrain.PLUG_IN_HYBRID))for(var d:DriveConfig.Layout.values()){
            var c=new Car(t,d,EngineFamily.V6,.50);double before=c.pack.battery().energyJ();
            c.run(1600,GO);
            double kinetic=.5*t.massKg*Math.pow(c.road.groundSpeed(),2);
            assertTrue(kinetic<before-c.pack.battery().energyJ()+c.fuelJ*.33,"Combined drive is paid for");
            c.run(1600,PARK);
            assertTrue(c.road.groundSpeed()<.05);
            assertTrue(c.pack.battery().energyJ()-before<c.fuelJ*.31,"Braking/charging round trip has no free energy");
        }
    }
    @Test void failedIgnitionFallsBackToElectricEvenAboveTheNormalAssistCutoff(){
        var c=new Car(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.RWD,EngineFamily.I4,.60);c.moving(25);
        c.setup=c.setup.withMechanics(c.setup.mechanics().with("engine.ignition",null));c.run(400,GO);
        assertTrue(c.wheelJ>10000,"Working electric path supplies propulsion despite engine fault");
        assertTrue(c.road.speed()>25);assertEquals(40,c.road.fuel());assertFalse(c.pack.generating());
    }
    @Test void chargeSustainStopsAtItsBandAndElectricOnlyNeverStartsEngine(){
        var c=new Car(Powertrain.HYBRID,DriveConfig.Layout.FWD,EngineFamily.I4,.54);c.mode=ElectricDynamics.Mode.CHARGE_SUSTAIN;
        boolean stopped=false;
        for(int i=0;i<24000;i++){c.tick(PARK);if(c.soc()>.64&&!c.pack.generating()){stopped=true;break;}}
        assertTrue(stopped,"Reached the upper band and stopped generation");
        assertTrue(c.soc()>.64&&c.soc()<.651,"Sustain charge band: "+c.soc());
        assertEquals(0,c.pack.generatorW(),1e-6);
        c.mode=ElectricDynamics.Mode.ELECTRIC_ONLY;double before=c.road.fuel();c.run(500,GO);
        assertEquals(before,c.road.fuel());assertEquals(0,c.pack.generatorW());assertFalse(c.pack.generating());
    }
}
