package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MechanicalPowertrainTest {
    private MechanicalState fresh(int parts){return MechanicalState.legacy(Assembly.stock(),parts,90,90,100);}
    private VehicleDynamics.Setup setup(MechanicalState m,int parts){return new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,parts,m.coolantTemperature(),1.4,m);}
    private EnginePhysics.State start(MechanicalState m,int parts){var s=EnginePhysics.State.stopped(90,100);for(int i=0;i<80;i++)s=EnginePhysics.step(s,setup(m,parts),true,0,0,.05);return s;}
    @Test void starterHasCrankingStateAndCannotStartWithFlatBatteryOrOpenIgnition(){
        var m=fresh(EnginePart.stock());var s=EnginePhysics.step(EnginePhysics.State.stopped(90,100),setup(m,EnginePart.stock()),true,0,0,.05);
        assertEquals(EnginePhysics.Mode.CRANKING,s.mode());assertTrue(s.rpm()<400);assertEquals(EnginePhysics.Mode.RUNNING,start(m,EnginePart.stock()).mode());
        var battery=m.get("electrical.battery");assertFalse(MechanicalCapabilities.canCrank(m.with("electrical.battery",battery.operating(0,20))));
        var ignition=m.get("engine.ignition");var fault=m.with("engine.ignition",ignition.condition(0,0,PartInstance.OPEN_CIRCUIT));assertTrue(MechanicalCapabilities.canCrank(fault));assertEquals(EnginePhysics.Mode.STALLED,start(fault,EnginePart.stock()).mode());
    }
    @Test void oilSupplyFaultStarvesOriginalAndReplacementTurbo(){
        int parts=EnginePart.boosted(1);var m=fresh(parts);var feed=m.get("oil.feed").damage(.95,PartInstance.LEAK);m=m.with("oil.feed",feed);
        assertTrue(CircuitPhysics.measure(m,4500,true,15).oilPressure()<1.1);
        for(int i=0;i<1600;i++)m=CircuitPhysics.step(m,4500,.8,.6,true,15,false,.05);
        var old=m.get("engine.induction");assertTrue(m.oil()<5&&old.wear()>0);
        m=m.with("engine.induction",PartInstance.fresh("turbo_kit",0));assertEquals(feed,m.get("oil.feed"));assertTrue(CircuitPhysics.measure(m,4500,true,15).oilPressure()<.65);
        for(int i=0;i<200;i++)m=CircuitPhysics.step(m,4500,.8,.6,true,15,false,.05);
        assertTrue(m.get("engine.induction").wear()>0,"Replacement compressor still suffers from the unrepaired oil supply");
    }
    @Test void alternatorAndBeltControlChargeIndependentlyOfEngineHealth(){
        var m=fresh(EnginePart.stock());var battery=m.get("electrical.battery").operating(20,20);m=m.with("electrical.battery",battery);
        var charged=m;var broken=m.with("electrical.belt",null);
        for(int i=0;i<1200;i++){charged=CircuitPhysics.step(charged,2000,.2,0,true,false,true,0,false,.05);broken=CircuitPhysics.step(broken,2000,.2,0,true,false,true,0,false,.05);}
        assertTrue(charged.get("electrical.battery").reserve()>20);assertTrue(broken.get("electrical.battery").reserve()<20);assertTrue(broken.faultHistory().contains("CHARGING_LOW"));assertEquals(0,broken.get("engine.internals").damage());
    }
    @Test void brokenGearboxAllowsEngineToRevAndVehicleToCoast(){
        int parts=EnginePart.stock();var m=fresh(parts).with("driveline.gearbox",null);var engine=start(m,parts);var s=new VehicleDynamics.State(10,engine.rpm(),1,30,0,0,engine);
        for(int i=0;i<60;i++)s=VehicleDynamics.step(s,true,new VehicleDynamics.Input(1,0,false,false),setup(m,parts),1,true,.05);
        assertTrue(s.rpm()>4000);assertTrue(s.speed()>8&&s.speed()<10,"Failed torque path coasts without artificial stop");assertEquals(EnginePhysics.Mode.RUNNING,s.engine().mode());
    }
    @Test void wornClutchSlipsAndShiftHasFiniteDisengagement(){
        int parts=EnginePart.stock();var healthy=fresh(parts);var worn=healthy.with("driveline.clutch",healthy.get("driveline.clutch").condition(.96,.5,0));
        assertTrue(MechanicalCapabilities.clutch(worn)<.1);var shift=TransmissionPhysics.shift(TransmissionPhysics.State.stopped(),2,.05);assertEquals(1,shift.gear());assertTrue(shift.remaining()>0);for(int i=0;i<6;i++)shift=TransmissionPhysics.shift(shift,2,.05);assertEquals(2,shift.gear());assertEquals(0,shift.remaining());
        var a=new VehicleDynamics.State(0,850,1,30,0,0,start(healthy,parts));var b=new VehicleDynamics.State(0,850,1,30,0,0,start(worn,parts));
        for(int i=0;i<60;i++){a=VehicleDynamics.step(a,true,new VehicleDynamics.Input(1,0,false,false),setup(healthy,parts),1,true,.05);b=VehicleDynamics.step(b,true,new VehicleDynamics.Input(1,0,false,false),setup(worn,parts),1,true,.05);}
        assertTrue(a.speed()>b.speed()+2);assertTrue(b.rpm()>a.rpm());assertTrue(b.transmission().clutchHeat()>20);
    }
    @Test void pressureReleaseRequiresValvePressureAndTurboHardware(){
        int parts=EnginePart.boosted(1);var m=fresh(parts);var s=start(m,parts);for(int i=0;i<80;i++)s=EnginePhysics.step(s,setup(m,parts),true,1,i<20?0:80,.05);
        assertTrue(s.boost()>.1);var release=EnginePhysics.step(s,setup(m,parts),true,0,0,.05);assertTrue(release.blowOff());
        assertFalse(EnginePhysics.step(s,setup(m.with("induction.bov",null),parts),true,0,0,.05).blowOff());
        int blower=EnginePart.boosted(2);assertFalse(EnginePhysics.step(s,setup(fresh(blower),blower),true,0,0,.05).blowOff());
    }
    @Test void mufflerChangesNoCombustionOrOilAndFamiliesHaveDifferentCurveShapes(){
        int parts=EnginePart.stock();var m=fresh(parts);var sport=m.with("exhaust.muffler",PartInstance.fresh("sport_muffler",0));
        assertEquals(MechanicalCapabilities.combustion(m,4000),MechanicalCapabilities.combustion(sport,4000));
        double v6=EngineBuild.naturalTorque(2200,EngineFamily.V6,1,parts,6800)/EngineBuild.naturalTorque(5500,EngineFamily.V6,1,parts,6800);
        double rotary=EngineBuild.naturalTorque(2200,EngineFamily.ROTOR4,1,parts,6800)/EngineBuild.naturalTorque(5500,EngineFamily.ROTOR4,1,parts,6800);assertTrue(v6>rotary*1.1);
    }
}
