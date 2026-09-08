package com.photonspark.sparkmotors.sim;

import com.photonspark.sparkmotors.sim.electric.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PowertrainComponentsTest {
    private static MechanicalState fresh(Powertrain t,DriveConfig.Layout d,EngineFamily f){return PowertrainTopology.fresh(t,DriveConfig.preset(d),f,Assembly.stock(),EnginePart.stock());}
    private static VehicleDynamics.Setup setup(MechanicalState m,Powertrain t,DriveConfig.Layout d,EngineFamily f){return new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,f,EnginePart.stock(),m.coolantTemperature(),1.4,m,DriveConfig.preset(d)).withPowertrain(t);}
    @Test void everyVehicleAndLayoutHasOnlyItsInstalledTopology(){
        for(var t:Powertrain.values())for(var d:DriveConfig.Layout.values())for(var f:EngineFamily.values()){
            var m=fresh(t,d,f);assertEquals(2,m.version());assertTrue(m.validFor(s->PowertrainTopology.applicable(s,t,DriveConfig.preset(d),f)));
            assertEquals(d!=DriveConfig.Layout.RWD,m.get("driveline.cv_fl")!=null);
            assertEquals(d!=DriveConfig.Layout.FWD,m.get("driveline.cv_rr")!=null);
            assertEquals(!t.electric(),m.get("driveline.clutch")!=null);
            assertEquals(t.hybrid(),m.get("traction.generator")!=null);
            assertEquals(!t.electric()||t.hybrid(),m.get(f.rotary()?"internal.eccentric":"internal.crank")!=null);
            assertEquals(m.parts().size(),m.parts().values().stream().map(PartInstance::id).distinct().count());
        }
    }
    @Test void migrationIsIdempotentRetainsOldIdentitiesAndNeverFillsRemovedParts(){
        var old=MechanicalState.legacy(Assembly.stock(),EnginePart.stock(),100,115,70);
        old=old.with("driveline.shaft",old.get("driveline.shaft").condition(.65,.4,PartInstance.BENT));
        var m=PowertrainTopology.migrate(old,Powertrain.COMBUSTION,DriveConfig.preset(DriveConfig.Layout.AWD),EngineFamily.I4,Assembly.stock(),EnginePart.stock());
        for(var e:old.parts().entrySet())assertEquals(e.getValue(),m.get(e.getKey()));
        assertEquals(.65,m.get("driveline.cv_fl").wear());assertEquals(.4,m.get("driveline.cv_fl").damage());
        assertEquals(m,PowertrainTopology.migrate(old,Powertrain.COMBUSTION,DriveConfig.preset(DriveConfig.Layout.AWD),EngineFamily.I4,Assembly.stock(),EnginePart.stock()));
        var removed=m.with("driveline.cv_fl",null).with("cylinder.2.rings",null);
        assertEquals(removed,PowertrainTopology.migrate(removed,Powertrain.COMBUSTION,DriveConfig.preset(DriveConfig.Layout.AWD),EngineFamily.I4,Assembly.stock(),EnginePart.stock()));
    }
    @Test void axleFailuresDoNotDisableUnrelatedElectricAxle(){
        for(var t:Powertrain.values())if(t.electric()){
            var d=DriveConfig.preset(DriveConfig.Layout.AWD);var m=fresh(t,d.layout(),EngineFamily.I4);
            for(String unit:List.of("motor","inverter","reduction")){
                var broken=m.with(PowertrainTopology.unit(unit,0),null);
                assertEquals(0,PowertrainTopology.axleCapability(broken,t,d,0));assertEquals(1,PowertrainTopology.axleCapability(broken,t,d,1));
            }
            assertEquals(0,PowertrainTopology.availability(m.with("traction.contactor",null),t,d));
        }
    }
    @Test void brokenCvRespectsOpenLimitedSlipAndLockedDifferential(){
        var t=Powertrain.COMBUSTION;var m=fresh(t,DriveConfig.Layout.RWD,EngineFamily.I4).with("driveline.cv_rl",null);
        for(var diff:DriveConfig.Differential.values()){
            var d=new DriveConfig(DriveConfig.Layout.RWD,diff,0);double capability=PowertrainTopology.axleCapability(m,t,d,1);
            assertEquals(diff==DriveConfig.Differential.OPEN?0:diff==DriveConfig.Differential.LIMITED_SLIP?.35:1,capability,1e-9);
            var torques=PowertrainTopology.torques(m,t,d,WheelDynamics.State.stopped(),0,400*capability,.0125);
            assertEquals(0,torques[2]);assertEquals(400*capability,torques[3],1e-9);
        }
    }
    @Test void eachCylinderOrRotorHasOwnCompressionAndTargetedRepair(){
        for(var f:EngineFamily.values()){
            var m=fresh(Powertrain.COMBUSTION,DriveConfig.Layout.RWD,f);String key=(f.rotary()?"rotor.1.seals":"cylinder.1.rings");
            double healthy=InternalMechanics.compression(m,f,1);var worn=m.get(key).condition(.7,.6,PartInstance.LEAK);m=m.with(key,worn);
            assertTrue(InternalMechanics.compression(m,f,1)<healthy*.5);
            if(InternalMechanics.count(f)>1)assertEquals(healthy,InternalMechanics.compression(m,f,2),1e-9);
            var stored=m.select(s->s.assembly()==Assembly.ENGINE,true);
            var reinstalled=fresh(Powertrain.COMBUSTION,DriveConfig.Layout.RWD,f).replace(s->s.assembly()==Assembly.ENGINE,stored,true);
            assertEquals(worn,reinstalled.get(key));
            double failed=InternalMechanics.output(m,f,3000);var repaired=m.with(key,PartInstance.fresh(ComponentSlot.byKey(key).item(),0));assertTrue(InternalMechanics.output(repaired,f,3000)>failed);
            assertEquals(m.oil(),repaired.oil());
        }
    }
    @Test void seizedIndividualBearingPreventsCrankingAndOilCauseSurvivesReplacement(){
        var m=fresh(Powertrain.COMBUSTION,DriveConfig.Layout.RWD,EngineFamily.V6);
        var bearing=m.get("cylinder.3.bearing").condition(.99,.1,PartInstance.SEIZED);m=m.with("cylinder.3.bearing",bearing).with("oil.feed",null);
        assertFalse(MechanicalCapabilities.canCrank(m));
        m=m.with("cylinder.3.bearing",PartInstance.fresh("cylinder_bearing",0));assertTrue(MechanicalCapabilities.canCrank(m));
        for(int i=0;i<80;i++)m=InternalMechanics.step(m,EngineFamily.V6,5000,.8,true,.05);
        assertTrue(m.get("cylinder.3.bearing").wear()>.005);assertNull(m.get("oil.feed"));
    }
    @Test void electricRegenerationIsIndependentOfFrictionPadAndHydraulicState(){
        for(var t:Powertrain.values())if(t.electric())for(var d:DriveConfig.Layout.values()){
            var m=fresh(t,d,EngineFamily.I4);for(String c:ComponentSlot.CORNERS)m=m.with("wheel."+c+".pad",null).with("wheel."+c+".brake_hose",null);
            m=m.fluids(m.coolant(),m.oil(),0);var s=setup(m,t,d,EngineFamily.I4);
            var w=new WheelDynamics.State(Collections.nCopies(4,new WheelDynamics.Corner(10/.34,0,0,true,0,0,20)));
            var r=ElectricDynamics.step(t,ElectricDynamics.State.initial(t,.5),ElectricDynamics.Mode.ELECTRIC_ONLY,10,40,EnginePhysics.State.stopped(20,100),w,TransmissionPhysics.State.stopped(),true,new VehicleDynamics.Input(0,0,true,false),s,new double[]{1,1,1,1},new boolean[]{true,true,true,true},new double[4],false,.0125,20);
            assertTrue(r.electric().regenW()>0,t+" "+d);assertTrue(r.road().wheels().corners().stream().allMatch(c->c.brakeForce()==0));
            assertTrue(r.road().groundSpeed()<10);assertTrue(r.electric().battery().energyJ()>t.battery.capacityJ()*.5);
        }
    }
    @Test void motorHeatAndWearBelongToTheRemovableMotor(){
        var t=Powertrain.ELECTRIC_400;var m=fresh(t,DriveConfig.Layout.AWD,EngineFamily.I4);
        var hot=m.get("traction.motor_front").condition(.2,.1,0).operating(0,170);m=m.with("traction.motor_front",hot);
        var r=ElectricDynamics.step(t,ElectricDynamics.State.initial(t,.5),ElectricDynamics.Mode.AUTO,0,0,EnginePhysics.State.stopped(20,100),true,new VehicleDynamics.Input(1,0,false,false),setup(m,t,DriveConfig.Layout.AWD,EngineFamily.I4),1,true,false,.0125,20);
        assertEquals(hot.id(),r.mechanics().get("traction.motor_front").id());assertTrue(r.mechanics().get("traction.motor_front").temperature()>160);
        assertEquals(0,r.road().wheels().corners().get(0).omega(),1e-9);assertTrue(r.road().wheels().corners().get(2).omega()>0);
    }
    @Test void invalidBatteryPowerCannotBecomeCharging(){for(double w:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->BatteryModel.exchange(Powertrain.ELECTRIC_400.battery,BatteryModel.State.initial(Powertrain.ELECTRIC_400.battery,.5),w,.0125,20,0));}

    @Test void unusedFamilySparesDoNotSeizeTheInstalledEngine(){
        var m=fresh(Powertrain.COMBUSTION,DriveConfig.Layout.RWD,EngineFamily.I4);
        m=m.with("rotor.1.bearing",PartInstance.fresh("rotor_bearing",0).condition(1,0,PartInstance.SEIZED));
        assertTrue(MechanicalCapabilities.canCrank(m,EngineFamily.I4));assertEquals(1,InternalMechanics.output(m,EngineFamily.I4,2000),1e-9);
        assertFalse(MechanicalCapabilities.canCrank(m.with("internal.crank",null),EngineFamily.I4));
    }
    @Test void electricCoolingPumpAndFluidQuantityControlDriveUnitHeatRemoval(){
        var t=Powertrain.ELECTRIC_400;var healthy=fresh(t,DriveConfig.Layout.RWD,EngineFamily.I4);
        healthy=healthy.with("traction.motor_rear",healthy.get("traction.motor_rear").operating(0,150));
        double cooled=0,failed=0;
        for(int i=0;i<2;i++){
            var m=i==0?healthy:healthy.with("cooling.pump",null).fluids(0,0,1);
            var r=ElectricDynamics.step(t,ElectricDynamics.State.initial(t,.5),ElectricDynamics.Mode.AUTO,0,0,EnginePhysics.State.stopped(20,100),true,new VehicleDynamics.Input(0,0,false,false),setup(m,t,DriveConfig.Layout.RWD,EngineFamily.I4),1,true,false,.05,20);
            if(i==0){cooled=r.mechanics().get("traction.motor_rear").temperature();assertTrue(r.mechanics().coolantTemperature()>20);}else failed=r.mechanics().get("traction.motor_rear").temperature();
        }
        assertTrue(cooled<failed);
    }
    @Test void emptyHvBatteryCannotSupplyFreeAccessoryChargingOrCombustionOilFaults(){
        var t=Powertrain.ELECTRIC_400;var m=fresh(t,DriveConfig.Layout.RWD,EngineFamily.I4);
        var r=ElectricDynamics.step(t,ElectricDynamics.State.initial(t,0),ElectricDynamics.Mode.AUTO,0,0,EnginePhysics.State.stopped(20,100),true,new VehicleDynamics.Input(0,0,false,false),setup(m,t,DriveConfig.Layout.RWD,EngineFamily.I4),1,true,false,.05,20);
        assertEquals(0,r.auxiliaryW(),1e-9);assertEquals(0,r.electric().battery().energyJ(),1e-9);
        m=CircuitPhysics.step(m,0,1,0,true,false,false,0,false,.05,true);
        assertEquals(0,m.oil());assertFalse(m.faultHistory().contains("OIL_LEVEL_LOW"));assertEquals(20,m.coolantTemperature(),1e-9);
    }
    @Test void electricImpactDamagesOnlyTheContactedAxleWithinOneBudget(){
        var m=fresh(Powertrain.PLUG_IN_HYBRID,DriveConfig.Layout.AWD,EngineFamily.I4);
        var hit=CircuitPhysics.impact(m,"rear",25);
        assertTrue(hit.get("traction.motor_rear").damage()>0);assertEquals(m.get("traction.motor_front"),hit.get("traction.motor_front"));
        double total=hit.parts().entrySet().stream().mapToDouble(e->e.getValue().damage()-m.get(e.getKey()).damage()).sum();assertTrue(total<=.9000001);
    }
}
