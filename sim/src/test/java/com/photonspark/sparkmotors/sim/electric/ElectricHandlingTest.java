package com.photonspark.sparkmotors.sim.electric;

import com.photonspark.sparkmotors.sim.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ElectricHandlingTest {
    private static final boolean[] ROAD={true,true,true,true},AIR={false,false,false,false};
    private static final double[] GRIP={1,1,1,1};
    private static VehicleDynamics.Setup setup(DriveConfig.Layout layout){return new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,EnginePart.stock(),90,1.4,MechanicalState.legacy(Assembly.stock(),EnginePart.stock(),90,90,100),DriveConfig.preset(layout));}
    private static final class Car {
        final Powertrain type;ElectricDynamics.State e;VehicleDynamics.State road=new VehicleDynamics.State(0,0,1,40,0,0);VehicleDynamics.Setup setup;
        Car(Powertrain type,DriveConfig.Layout layout,double soc){this.type=type;setup=setup(layout);e=ElectricDynamics.State.initial(type,soc);}
        ElectricDynamics.Result step(VehicleDynamics.Input input,boolean ready,boolean[] contacts){
            var r=ElectricDynamics.step(type,e,ElectricDynamics.Mode.ELECTRIC_ONLY,road.speed(),road.fuel(),road.engine(),road.wheels(),road.transmission(),ready,input,setup,GRIP,contacts,new double[4],false,.0125,20);e=r.electric();road=r.road();return r;
        }
        void run(int n,VehicleDynamics.Input input){for(int i=0;i<n;i++)step(input,true,ROAD);}
    }
    @Test void allFourPowertrainsUseAllThreeLayoutsWithLateralMotionAndEnergyAccounting(){
        for(var type:Powertrain.values())if(type.electric())for(var layout:DriveConfig.Layout.values()){
            var c=new Car(type,layout,.7);double initial=c.e.battery().energyJ();c.run(600,new VehicleDynamics.Input(1,0,false,false));
            assertTrue(c.road.speed()>5,type+" "+layout);assertTrue(c.e.battery().energyJ()<initial);
            assertTrue(initial-c.e.battery().energyJ()>=.5*type.massKg*c.road.groundSpeed()*c.road.groundSpeed()-1,"launch is paid for");
            c.run(100,new VehicleDynamics.Input(.7,.6,false,false,false,true));
            assertTrue(Math.abs(c.road.transmission().lateralSpeed())>.15,"lateral slip survives electric integration "+type+layout);
            assertTrue(c.road.transmission().yawRate()>0,"positive rig steering remains right");
            assertEquals(1,c.road.gear());assertEquals(40,c.road.fuel(),1e-9);
        }
    }
    @Test void missingBrakesAirborneBrakingAndRearHandbrakeUseActualCorners(){
        var c=new Car(Powertrain.ELECTRIC_400,DriveConfig.Layout.RWD,.6);c.run(400,new VehicleDynamics.Input(1,0,false,false));
        var before=c.road;var electric=c.e;
        var m=c.setup.mechanics();for(String corner:ComponentSlot.CORNERS)m=m.with("wheel."+corner+".pad",null);
        c.setup=new VehicleDynamics.Setup(c.setup.config(),6800,3.7,EngineFamily.I4,EnginePart.stock(),90,1.4,m,c.setup.drive());
        c.step(new VehicleDynamics.Input(0,0,true,false),true,ROAD);
        assertEquals(0,c.e.regenW(),1e-9,"missing service brakes do not invent regen command");
        assertTrue(before.speed()-c.road.speed()<.1,"no stock brake fallback");
        c.setup=setup(DriveConfig.Layout.RWD);c.road=before;c.e=electric;
        c.step(new VehicleDynamics.Input(0,0,true,false),true,AIR);
        assertEquals(0,c.e.regenW(),1e-9);assertTrue(before.speed()-c.road.speed()<.01,"airborne service brake does not stop body");
        c.road=before;c.e=electric;c.step(new VehicleDynamics.Input(0,0,false,false,false,true),true,ROAD);
        assertEquals(0,c.e.regenW(),1e-9,"handbrake is mechanical");
        assertEquals(0,c.road.wheels().corners().get(0).brakeForce());assertTrue(c.road.wheels().corners().get(2).brakeForce()>0);
    }
    @Test void fullPackUsesFrictionBrakesAndRollingOffStillWorks(){
        var c=new Car(Powertrain.ELECTRIC_800,DriveConfig.Layout.AWD,.6);c.run(500,new VehicleDynamics.Input(1,0,false,false));
        c.e=c.e.withBattery(BatteryModel.State.initial(c.type.battery,1));double peak=c.road.speed();
        double regen=0;for(int i=0;i<1200;i++){c.step(new VehicleDynamics.Input(0,0,true,false),false,ROAD);regen+=c.e.regenW();}
        assertTrue(peak>5);assertTrue(c.road.groundSpeed()<.04);assertEquals(0,regen);assertEquals(1,c.e.battery().soc(c.type.battery),1e-6);
    }
    @Test void backwardsSlideDoesNotSelectReverseAndBrokenShaftCannotDrive(){
        var c=new Car(Powertrain.ELECTRIC_400,DriveConfig.Layout.RWD,.5);
        c.road=new VehicleDynamics.State(-5,0,1,40,0,0,EnginePhysics.State.stopped(20,100),WheelDynamics.State.stopped(),TransmissionPhysics.State.stopped().motion(7,.1,0,0,0));
        c.step(new VehicleDynamics.Input(0,0,false,false),true,ROAD);assertEquals(1,c.road.gear());assertTrue(c.road.transmission().lateralSpeed()>5);
        c=new Car(Powertrain.ELECTRIC_400,DriveConfig.Layout.RWD,.5);var m=c.setup.mechanics().with("driveline.shaft",null);
        c.setup=new VehicleDynamics.Setup(c.setup.config(),6800,3.7,EngineFamily.I4,EnginePart.stock(),90,1.4,m,c.setup.drive());
        c.run(200,new VehicleDynamics.Input(1,0,false,false));assertEquals(0,c.road.groundSpeed(),1e-9);
    }
    @Test void motorCurvesRespectEachRatingConstantPowerAndOverspeed(){
        for(var type:Powertrain.values())if(type.electric()){
            assertEquals(type.torqueNm,ElectricDynamics.motorTorque(type,0),1e-9);
            double base=type.motorKw*30000/(type.torqueNm*Math.PI);
            assertEquals(type.motorKw,ElectricDynamics.motorTorque(type,base*1.3)*base*1.3*Math.PI/30000,1e-8);
            for(int rpm=0;rpm<=20000;rpm+=25){double t=ElectricDynamics.motorTorque(type,rpm);assertTrue(t>=0&&t<=type.torqueNm);assertTrue(t*rpm*Math.PI/30000<=type.motorKw+1e-8);}
            assertEquals(0,ElectricDynamics.motorTorque(type,18000));assertEquals(0,ElectricDynamics.motorTorque(type,Double.NaN));
        }
    }
}
