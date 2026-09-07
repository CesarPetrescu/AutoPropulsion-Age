package com.photonspark.sparkmotors.sim;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnginePhysicsTest {
    private VehicleDynamics.Setup setup(int parts){return new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,parts,90);}
    private EnginePhysics.State atRpm(double rpm){return new EnginePhysics.State(rpm*Math.PI/30,1,0,0,90,3,100,14.7,0,0,false);}
    private EnginePhysics.State holdRpm(int parts,double rpm,double seconds,double pedal){
        var s=atRpm(rpm);
        for(int i=0;i<seconds/.0125;i++){
            s=EnginePhysics.step(s,setup(parts),true,pedal,s.shaftTorque(),.0125);
            s=new EnginePhysics.State(rpm*Math.PI/30,s.throttle(),s.spool(),s.boost(),s.oilTemperature(),s.oilPressure(),s.health(),s.afr(),s.shaftTorque(),s.blowerKw(),s.blowOff());
        }return s;
    }
    @Test void turboNeedsTimeToSpoolAndLargeTurboLagsStreetTurbo(){
        var street=holdRpm(EnginePart.boosted(1),5000,.25,1);
        var big=holdRpm(EnginePart.boosted(3),5000,.25,1);
        assertTrue(street.boost()<.25);assertTrue(big.boost()<street.boost());
        assertTrue(holdRpm(EnginePart.boosted(1),5000,4,1).boost()>.6);
        assertTrue(holdRpm(EnginePart.boosted(3),6000,6,1).boost()>1.3);
    }
    @Test void blowoffDumpsPressureWhileTheTurboRetainsShaftSpeed(){
        int p=EnginePart.boosted(1);var s=holdRpm(p,5000,4,1);double spool=s.spool();boolean vent=false;
        for(int i=0;i<24;i++){s=EnginePhysics.step(s,setup(p),true,0,0,.0125);vent|=s.blowOff();}
        assertTrue(vent);assertTrue(s.boost()<.08);assertTrue(s.spool()>spool*.5);
    }
    @Test void rootsRespondsAtLowRpmAndBeltLoadIsReal(){
        var roots=holdRpm(EnginePart.boosted(5),2200,.3,1);var turbo=holdRpm(EnginePart.boosted(1),2200,.3,1);
        assertTrue(roots.boost()>.7&&turbo.boost()<.1);assertTrue(roots.blowerKw()>4);assertEquals(0,turbo.blowerKw());
        assertTrue(EngineBuild.blowerTorque(4000,.6,EnginePart.boosted(6))<EngineBuild.blowerTorque(4000,.6,EnginePart.boosted(5)));
    }
    @Test void flywheelInertiaChangesFreeRevAccelerationAndClutchPreventsVehicleMotion(){
        var input=new VehicleDynamics.Input(1,0,false,false,true);
        var heavy=EngineConfigurationTest.drive(setup(EnginePart.stock()),.5,input);
        var light=EngineConfigurationTest.drive(setup(EnginePart.FLYWHEEL.with(EnginePart.stock(),3)),.5,input);
        assertTrue(light.rpm()>heavy.rpm()+500,"Heavy="+heavy.rpm()+" light="+light.rpm());assertEquals(0,light.speed());
        var s=light;for(int i=0;i<80;i++)s=VehicleDynamics.step(s,false,input,setup(EnginePart.stock()),1,true,.0125);
        assertTrue(s.rpm()<light.rpm());
    }
    @Test void wastegateCapsBoostAndThrottleReleaseIsFiltered(){
        var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,EnginePart.boosted(3),90,.4);
        var s=atRpm(6000);for(int i=0;i<800;i++){s=EnginePhysics.step(s,setup,true,1,s.shaftTorque(),.0125);s=new EnginePhysics.State(6000*Math.PI/30,s.throttle(),s.spool(),s.boost(),90,3,100,s.afr(),s.shaftTorque(),s.blowerKw(),false);}
        assertTrue(s.boost()<=.401&&s.boost()>.35);
        s=EnginePhysics.step(s,setup,true,0,0,.0125);assertTrue(s.throttle()>0&&s.throttle()<1);
    }
    @Test void fuelCapacityLimitsPowerAndLeanBoostWearsTheEngine(){
        int p=EnginePart.boosted(6);p=EnginePart.INTAKE.with(p,3);p=EnginePart.EXHAUST.with(p,4);p=EnginePart.HEADWORK.with(p,4);
        var weak=holdRpm(p,5500,20,1);var strong=holdRpm(EnginePart.FUEL.with(p,4),5500,20,1);
        assertTrue(weak.afr()>13.5,"AFR="+weak.afr());assertTrue(weak.health()<strong.health());assertTrue(strong.shaftTorque()>weak.shaftTorque());
    }
    @Test void coolingOilSystemsAndOverheatAffectCondition(){
        int p=EnginePart.boosted(3);double stock=100,race=100;
        for(int i=0;i<1200;i++){stock=EngineBuild.temperature(stock,5500,1,1.4,true,EnginePart.COOLING.with(p,1),.05);race=EngineBuild.temperature(race,5500,1,1.4,true,EnginePart.COOLING.with(p,4),.05);}
        assertTrue(race<stock-15);
        var wet=holdRpm(p,5000,60,1);var dry=holdRpm(EnginePart.OIL.with(p,4),5000,60,1);assertTrue(dry.oilTemperature()<wet.oilTemperature()-15);assertTrue(dry.oilPressure()>=wet.oilPressure());
        var hot=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,p,135);var s=atRpm(5000);
        for(int i=0;i<800;i++)s=EnginePhysics.step(s,hot,true,1,s.shaftTorque(),.0125);assertTrue(s.health()<100);
    }
    @Test void raceCamsTradeLowEndTorqueForHighEndAndLimiterCutsFuel(){
        int stock=EnginePart.stock(),race=EnginePart.HEADWORK.with(stock,4);
        assertTrue(EngineBuild.torque(2000,EngineFamily.I4,1,race,6800,90)<EngineBuild.torque(2000,EngineFamily.I4,1,stock,6800,90));
        assertTrue(EngineBuild.torque(6000,EngineFamily.I4,1,race,6800,90)>EngineBuild.torque(6000,EngineFamily.I4,1,stock,6800,90));
        var s=EnginePhysics.step(atRpm(6900),setup(stock),true,1,0,.0125);assertTrue(s.shaftTorque()<0);
        assertEquals(0,EngineBuild.powerKw(7000,EngineFamily.I4,1,stock,6800));
    }
    @Test void stationaryBrakesHoldWhenARevTestReconnectsTheClutch(){
        var setup=setup(EnginePart.boosted(3));
        var s=EngineConfigurationTest.drive(setup,2,new VehicleDynamics.Input(1,0,true,false,true));
        assertTrue(s.rpm()>5000);assertEquals(0,s.speed());
        for(int i=0;i<320;i++){s=VehicleDynamics.step(s,true,new VehicleDynamics.Input(0,0,true,false),setup,1,true,.0125);assertEquals(0,s.speed(),.00001);}
        assertTrue(s.rpm()<1500);
    }
}
