package com.photonspark.sparkmotors.sim;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleAudioTest {
    private MechanicalState fresh(){return MechanicalState.legacy(Assembly.stock(),EnginePart.boosted(1),90,90,100);}
    private WheelDynamics.State wheels(boolean contact){return new WheelDynamics.State(Collections.nCopies(4,new WheelDynamics.Corner(40,0,0,contact,0,0,100)));}
    private VehicleAudio.Input input(MechanicalState m,EnginePhysics.Mode mode,double load,int induction,boolean contact,boolean brakes){return new VehicleAudio.Input(EngineFamily.I4,mode,4000,.8,load,.8,.5,13.6,induction,brakes,false,false,wheels(contact),m);}
    @Test void mufflerOnlyChangesExhaustVoice(){
        var m=fresh();var stock=new HashMap<>(VehicleAudio.mix(input(m,EnginePhysics.Mode.RUNNING,.7,1,true,false)));var sport=new HashMap<>(VehicleAudio.mix(input(m.with("exhaust.muffler",PartInstance.fresh("sport_muffler",0)),EnginePhysics.Mode.RUNNING,.7,1,true,false)));
        assertNotEquals(stock.remove("exhaust"),sport.remove("exhaust"));assertEquals(stock,sport);
    }
    @Test void roadAudioSurvivesEngineOffButRequiresEachContact(){
        var off=VehicleAudio.mix(input(fresh(),EnginePhysics.Mode.OFF,0,0,true,false));assertEquals(4,off.size());assertTrue(off.containsKey("road0"));assertTrue(VehicleAudio.mix(input(fresh(),EnginePhysics.Mode.OFF,0,0,false,false)).isEmpty());
        var m=fresh().with("wheel.fl.tire",null);var missing=VehicleAudio.mix(input(m,EnginePhysics.Mode.OFF,0,0,true,false));assertFalse(missing.containsKey("road0"));assertTrue(missing.containsKey("road1"));
    }
    @Test void equalRpmDifferentLoadChangesTheMix(){
        var coast=VehicleAudio.mix(input(fresh(),EnginePhysics.Mode.RUNNING,0,1,true,false));var load=VehicleAudio.mix(input(fresh(),EnginePhysics.Mode.RUNNING,1,1,true,false));assertTrue(load.get("low").gain()>coast.get("low").gain());assertFalse(load.containsKey("coast"));assertEquals(coast.get("low").pitch(),load.get("low").pitch());
    }
    @Test void grindingNeedsWornPadBrakingAndContact(){
        var m=fresh();assertFalse(VehicleAudio.mix(input(m,EnginePhysics.Mode.OFF,0,0,true,true)).containsKey("brake0"));m=m.with("wheel.fl.pad",m.get("wheel.fl.pad").condition(.98,0,0));
        assertTrue(VehicleAudio.mix(input(m,EnginePhysics.Mode.OFF,0,0,true,true)).containsKey("brake0"));assertFalse(VehicleAudio.mix(input(m,EnginePhysics.Mode.OFF,0,0,true,false)).containsKey("brake0"));assertFalse(VehicleAudio.mix(input(m,EnginePhysics.Mode.OFF,0,0,false,true)).containsKey("brake0"));
    }
    @Test void compressorTypesAreExclusiveAndAllVoicesResolveToAuthoredAssets(){
        Set<String> types=new HashSet<>();for(int mode=1;mode<=6;mode++){var mix=VehicleAudio.mix(input(fresh(),EnginePhysics.Mode.RUNNING,.8,mode,true,true));types.add(mix.get("induction").sound());for(var v:mix.values()){assertTrue(VehicleAudio.assets().contains(v.sound()));assertTrue(Double.isFinite(v.gain())&&v.gain()<=.55);}}
        assertEquals(Set.of("turbo","centrifugal","roots","twin_screw"),types);assertEquals(48,VehicleAudio.assets().size());
    }
}
