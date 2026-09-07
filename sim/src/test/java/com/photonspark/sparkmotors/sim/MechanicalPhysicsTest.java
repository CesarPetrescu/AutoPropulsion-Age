package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class MechanicalPhysicsTest {
    private MechanicalState fresh(){return MechanicalState.legacy(Assembly.stock(),EnginePart.stock(),90,90,100);}
    private MechanicalState run(MechanicalState m,int seconds){for(int i=0;i<seconds*20;i++)m=CircuitPhysics.step(m,4200,.8,0,true,15,false,.05);return m;}
    @Test void frontImpactLeakOverheatsAndCorrectRepairStillNeedsFluidAndRetainsDamage(){
        var healthy=run(fresh(),180);var hit=CircuitPhysics.impact(fresh(),"front",18);
        assertTrue(CircuitPhysics.coolantLeak(hit)>.04);assertEquals(fresh().get("wheel.fr.pad").damage(),hit.get("wheel.fr.pad").damage());
        var hot=run(hit,180);assertEquals(0,hot.coolant(),.001);assertTrue(hot.coolantTemperature()>healthy.coolantTemperature()+30);assertTrue(hot.get("engine.internals").damage()>.02);
        var repaired=hot.with("cooling.upper_hose",PartInstance.fresh("cooling_upper_hose",0));
        assertEquals(hot.coolant(),repaired.coolant());assertEquals(hot.get("engine.internals"),repaired.get("engine.internals"));
        assertTrue(CircuitPhysics.pressureHold(repaired,10)>.90);assertTrue(CircuitPhysics.pressureHold(hot,10)<.8);
        repaired=repaired.fluids(8,repaired.oil(),repaired.brakeFluid());var cooled=run(repaired,180);assertTrue(cooled.coolantTemperature()<hot.coolantTemperature());assertTrue(cooled.get("engine.internals").damage()>=hot.get("engine.internals").damage());
    }
    @Test void missingPumpAndFanHaveDifferentMeasuredEvidenceThanLeakingHose(){
        var pump=fresh().with("cooling.pump",null);assertEquals(1,CircuitPhysics.pressureHold(pump,10));assertEquals(0,CircuitPhysics.measure(pump,3000,true,0).circulation());
        var hot=fresh().with("cooling.fan",null);for(int i=0;i<3600;i++)hot=CircuitPhysics.step(hot,4200,.8,0,true,0,false,.05);assertEquals(8,hot.coolant());assertFalse(CircuitPhysics.measure(hot,3000,true,0).fan());assertTrue(hot.coolantTemperature()>90);
    }
    @Test void serviceBrakeAndRearHandbrakeAreDistinctAndMissingBrakeDoesNotStopRolling(){
        var m=fresh();var contacts=new boolean[]{true,true,true,true};var travel=new double[4];
        var service=WheelDynamics.step(WheelDynamics.State.stopped(),m,Assembly.stock(),new VehicleDynamics.Input(0,0,true,false,false,false),15,0,1,contacts,travel,.05);
        var hand=WheelDynamics.step(WheelDynamics.State.stopped(),m,Assembly.stock(),new VehicleDynamics.Input(0,0,false,false,false,true),15,0,1,contacts,travel,.05);
        assertTrue(service.braking()>hand.braking());assertEquals(0,hand.state().corners().get(0).brakeForce());assertTrue(hand.state().corners().get(2).brakeForce()>0);
        int missing=Assembly.BRAKES.with(Assembly.stock(),0);var state=VehicleDynamics.step(15,30,false,new VehicleDynamics.Input(0,0,true,false),new VehicleDynamics.Setup(missing,6800,3.7),1,true,.05);
        assertTrue(state.speed()>14.9,"Missing brakes must coast rather than use the stock branch or missing-assembly drag");
    }
    @Test void airborneBrakingSlowsOnlyWheelRotationNotChassis(){
        var engine=EnginePhysics.State.stopped(90,100);var spinning=new WheelDynamics.State(Collections.nCopies(4,new WheelDynamics.Corner(40,0,0,false,0,0,20)));
        var a=VehicleDynamics.step(15,30,engine,spinning,false,new VehicleDynamics.Input(0,0,true,false),new VehicleDynamics.Setup(Assembly.stock(),6800,3.7),1,new boolean[4],new double[4],.05);
        var b=VehicleDynamics.step(15,30,engine,spinning,false,new VehicleDynamics.Input(0,0,false,false),new VehicleDynamics.Setup(Assembly.stock(),6800,3.7),1,new boolean[4],new double[4],.05);
        assertEquals(b.speed(),a.speed(),1e-12);assertTrue(a.wheels().corners().get(0).omega()<b.wheels().corners().get(0).omega());assertEquals(0,a.wheels().corners().get(0).brakeForce());
    }
    @Test void failedFrontLeftHydraulicsPullButHandbrakeStillWorks(){
        var m=fresh().with("wheel.fl.brake_hose",null);var input=new VehicleDynamics.Input(0,0,true,false);
        var f=WheelDynamics.step(WheelDynamics.State.stopped(),m,Assembly.stock(),input,15,0,1,new boolean[]{true,true,true,true},new double[4],.05);
        assertEquals(0,f.state().corners().get(0).brakeForce());assertTrue(f.state().corners().get(1).brakeForce()>0);assertNotEquals(0,f.yawMoment());
        assertTrue(WheelDynamics.brakeCapability(m.fluids(8,5,0),2,false)>0);assertEquals(0,WheelDynamics.brakeCapability(m.fluids(8,5,0),2,true));
    }
    @Test void usedTireMovesAcrossCornersWithoutResetAndFluidStateIsImmutable(){
        var m=fresh();var used=m.get("wheel.fl.tire").condition(.8,.3,PartInstance.LEAK).operating(.8,60);var changed=m.with("wheel.fl.tire",null).with("wheel.rr.tire",used);
        assertNotNull(m.get("wheel.fl.tire"));assertEquals(used,changed.get("wheel.rr.tire"));assertTrue(WheelDynamics.tireGrip(changed,3)<WheelDynamics.tireGrip(m,3)*.5);
        assertThrows(UnsupportedOperationException.class,()->m.parts().clear());
        var later=run(changed,30);assertTrue(later.get("wheel.rr.tire").reserve()<.8);assertEquals(used.id(),later.get("wheel.rr.tire").id());
    }
}
