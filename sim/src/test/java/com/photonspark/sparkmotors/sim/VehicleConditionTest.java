package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.photonspark.sparkmotors.sim.VehicleCondition.Part.*;

class VehicleConditionTest {
    final int config=Assembly.stock(), parts=EnginePart.stock();
    @Test void localizedImpactDoesNotDamageOppositeCorners(){
        var c=new VehicleCondition();c.impact(VehicleCondition.Zone.LEFT,14,config,parts);
        assertTrue(c.health(TIRE_FL)<100);assertTrue(c.health(SUSPENSION_RL)<100);
        assertEquals(100,c.health(TIRE_FR));assertEquals(100,c.health(SUSPENSION_RR));assertEquals(100,c.health(COOLING));
    }
    @Test void frontAndRearDamageDifferentSystems(){
        var a=new VehicleCondition();var b=new VehicleCondition();a.impact(VehicleCondition.Zone.FRONT,14,config,parts);b.impact(VehicleCondition.Zone.REAR,14,config,parts);
        assertTrue(a.health(COOLING)<100);assertEquals(100,b.health(COOLING));assertTrue(b.health(MUFFLER)<100);assertEquals(100,a.health(MUFFLER));
    }
    @Test void impactsUseEnergyAndIgnoreInvalidOrGentleInputs(){
        var a=new VehicleCondition();var b=new VehicleCondition();a.impact(VehicleCondition.Zone.FRONT,8,config,parts);b.impact(VehicleCondition.Zone.FRONT,16,config,parts);
        assertTrue(b.state(FRONT_BODY).damage()>a.state(FRONT_BODY).damage()*3);
        var c=new VehicleCondition();for(double v:new double[]{0,-20,3,Double.NaN,Double.POSITIVE_INFINITY})c.impact(VehicleCondition.Zone.FRONT,v,config,parts);
        assertEquals(100,c.average(config,parts));
    }
    @Test void failuresAreEdgeTriggered(){
        var c=new VehicleCondition();assertTrue(c.impact(VehicleCondition.Zone.FRONT,40,config,parts).contains(FRONT_BODY));
        assertFalse(c.impact(VehicleCondition.Zone.FRONT,40,config,parts).contains(FRONT_BODY));
        c.repair(FRONT_BODY);assertTrue(c.impact(VehicleCondition.Zone.FRONT,40,config,parts).contains(FRONT_BODY));
    }
    @Test void repairsAndCopiesAffectOnlySelectedComponents(){
        var c=new VehicleCondition();c.restore(TIRE_FL,30,20);c.restore(ENGINE_BLOCK,15,50);c.repair(TIRE_FL);
        assertEquals(100,c.health(TIRE_FL));assertEquals(35,c.health(ENGINE_BLOCK));
        var donor=new VehicleCondition();donor.restore(TIRE_FR,22,33);c.copyFrom(donor,p->p.assembly==Assembly.WHEELS);
        assertEquals(45,c.health(TIRE_FR));assertEquals(35,c.health(ENGINE_BLOCK));
    }
    @Test void legacyWreckDoesNotResurrect(){
        var c=new VehicleCondition();c.migrateLegacy(0,config,parts);assertFalse(c.effects(config,parts).canRun());assertEquals(0,c.average(config,parts));
        c.migrateLegacy(64,config,parts);assertEquals(64,c.average(config,parts));
    }
    @Test void parkedOffDoesNotWearButHeatAndSlipDo(){
        var parked=new VehicleCondition();var normal=new VehicleCondition();var abused=new VehicleCondition();
        for(int i=0;i<1000;i++){
            parked.step(config,parts,false,0,0,0,false,20,0,.05);
            normal.step(config,parts,true,5000,1,20,true,90,0,.05);
            abused.step(config,parts,true,5000,1,20,true,128,1,.05);
        }
        assertEquals(100,parked.average(config,parts));assertTrue(normal.health(BRAKE_FL)<100);
        assertTrue(abused.health(INTERNALS)<normal.health(INTERNALS));assertTrue(abused.health(TIRE_FL)<normal.health(TIRE_FL));
    }
    @Test void dtPartitionIsStableAndAbsentPartsDoNotWear(){
        var a=new VehicleCondition();var b=new VehicleCondition();for(int i=0;i<20;i++)a.step(config,parts,true,3000,.6,12,true,90,.2,.05);
        for(int i=0;i<80;i++)b.step(config,parts,true,3000,.6,12,true,90,.2,.0125);
        for(var p:VehicleCondition.Part.values())assertEquals(a.health(p),b.health(p),1e-8);
        assertEquals(100,a.health(INDUCTION));
    }
    @Test void finiteStateClampsAndNegativeDamageDoesNotRepair(){
        var c=new VehicleCondition();c.restore(TIRE_FL,Double.NaN,Double.POSITIVE_INFINITY);assertTrue(Double.isFinite(c.health(TIRE_FL)));
        c.damage(TIRE_FL,20);c.damage(TIRE_FL,-50);assertEquals(80,c.health(TIRE_FL));c.restore(TIRE_FR,300,500);assertEquals(0,c.health(TIRE_FR));
    }
    private VehicleDynamics.Setup setup(VehicleCondition c){return new VehicleDynamics.Setup(config,6800,3.7,EngineFamily.I4,parts,90,c.effects(config,parts));}
    @Test void wornBrakesExtendStoppingAndFailedEngineStopsPower(){
        var good=new VehicleCondition();var bad=new VehicleCondition();for(var p:new VehicleCondition.Part[]{BRAKE_FL,BRAKE_FR,BRAKE_RL,BRAKE_RR})bad.damage(p,80);
        var input=new VehicleDynamics.Input(0,0,true,false);
        assertTrue(VehicleDynamics.step(20,40,true,input,setup(bad),1,true,.05).speed()>VehicleDynamics.step(20,40,true,input,setup(good),1,true,.05).speed());
        bad.damage(ENGINE_BLOCK,100);assertEquals(0,VehicleDynamics.step(0,40,true,new VehicleDynamics.Input(1,0,false,false),setup(bad),1,true,.05).speed());
    }
    @Test void damagedSuspensionPullsTowardDamagedSide(){
        var c=new VehicleCondition();c.damage(SUSPENSION_FR,90);assertTrue(c.effects(config,parts).pull()>0);
        assertTrue(VehicleDynamics.step(10,40,false,new VehicleDynamics.Input(0,0,false,false),setup(c),1,true,.05).yawDelta()>0);
    }
    @Test void coolingAndInductionFailuresHaveMechanicalConsequences(){
        int boosted=EnginePart.boosted(1);var c=new VehicleCondition();c.damage(COOLING,100);c.damage(INDUCTION,100);
        assertEquals(0,c.effects(config,boosted).boost());
        assertTrue(EngineBuild.torque(4500,EngineFamily.I4,1,boosted,6800,90,1)>EngineBuild.torque(4500,EngineFamily.I4,1,boosted,6800,90,0));
        assertTrue(EngineBuild.temperature(100,5000,1,.5,true,parts,.05,0)>EngineBuild.temperature(100,5000,1,.5,true,parts,.05,1));
    }
    @Test void airborneBrakingDoesNotInventTireContact(){
        var s=setup(new VehicleCondition());var off=new VehicleDynamics.Input(0,0,false,false);var brake=new VehicleDynamics.Input(0,0,true,false,true);
        assertEquals(VehicleDynamics.step(20,40,false,off,s,1,false,.05).speed(),VehicleDynamics.step(20,40,false,brake,s,1,false,.05).speed());
        assertEquals(0,VehicleDynamics.step(20,40,true,brake,s,1,false,.05).slip());
    }
}
