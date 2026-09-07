package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineConfigurationTest {
    private static int ternary(int n){int p=0;for(var slot:EnginePart.values()){p=slot.with(p,n%3);n/=3;}return p;}
    @Test void everyEngineStateHasConsistentStartupAndFiniteOutput() throws Exception {
        int accepted=0,rejected=0;
        for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int n=0;n<729;n++){
            int p=ternary(n);boolean complete=true;
            for(var slot:EnginePart.values())if(slot!=EnginePart.INDUCTION&&slot.variant(p)==0)complete=false;
            boolean supported=EnginePart.INDUCTION.variant(p)==0||(EnginePart.FUEL.variant(p)==2&&EnginePart.INTERNALS.variant(p)==2);
            boolean expected=complete&&supported;
            assertEquals(expected,EnginePart.ready(p),family+" parts="+p);
            var setup=new VehicleDynamics.Setup(Assembly.ENGINE.with(Assembly.stock(),grade),6800,3.7,family,p,90);
            var state=VehicleDynamics.step(0,40,true,new VehicleDynamics.Input(1,0,false,false),setup,1,true,.05);
            assertTrue(Double.isFinite(state.speed())&&Double.isFinite(state.rpm())&&Double.isFinite(state.fuel()));
            assertEquals(expected,state.speed()>0);assertEquals(expected,state.rpm()>0);
            if(expected)accepted++;else{rejected++;assertEquals(40,state.fuel());}
            for(int rpm:new int[]{0,850,2500,4500,6799,6800,9000}){
                double torque=EngineBuild.torque(rpm,family,grade,p,6800,90);
                assertTrue(Double.isFinite(torque)&&torque>=0);
                if(!expected||rpm>=6800)assertEquals(0,torque);
            }
        }
        assertEquals(672,accepted);assertEquals(9534,rejected);
        Path report=Path.of("build/reports/engine-states.json");Files.createDirectories(report.getParent());
        Files.writeString(report,"{\"families\":7,\"grades\":2,\"states_checked\":10206,\"ready\":"+accepted+",\"correctly_blocked\":"+rejected+"}\n");
    }
    @Test void everyReadyBuildDrivesAndBrakesAcrossCarVariantsAndTuneBounds() throws Exception {
        int checked=0;
        for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int n=0;n<729;n++){
            int parts=ternary(n);if(!EnginePart.ready(parts))continue;
            for(int carBits=0;carBits<32;carBits++)for(int tune=0;tune<3;tune++){
                int config=Assembly.ENGINE.with(Assembly.stock(),grade);
                for(int i=1;i<6;i++)config=Assembly.values()[i].with(config,1+((carBits>>>(i-1))&1));
                int limiter=new int[]{4000,6800,7000}[tune];double finalDrive=new double[]{2.8,3.7,4.8}[tune];
                var setup=new VehicleDynamics.Setup(config,limiter,finalDrive,family,parts,90);
                double speed=0,fuel=40;
                for(int t=0;t<100;t++){var s=VehicleDynamics.step(speed,fuel,true,new VehicleDynamics.Input(1,.2,false,false),setup,1,true,.05);speed=s.speed();fuel=s.fuel();assertTrue(Double.isFinite(speed)&&Double.isFinite(s.yawDelta())&&s.rpm()<=limiter);}
                assertTrue(speed>3&&fuel<40&&fuel>0,family+" could not drive parts="+parts);
                for(int t=0;t<160;t++)speed=VehicleDynamics.step(speed,fuel,true,new VehicleDynamics.Input(0,0,true,false),setup,1,true,.05).speed();
                assertEquals(0,speed,.001);checked++;
            }
        }
        assertEquals(64512,checked);
        Path report=Path.of("build/reports/engine-driving-matrix.json");Files.createDirectories(report.getParent());
        Files.writeString(report,"{\"ready_engine_builds\":672,\"car_variants\":32,\"tune_cases\":3,\"drive_and_brake_cases_passed\":"+checked+"}\n");
    }
    @Test void inductionModesAndUpgradesHaveDistinctEffects(){
        for(var f:EngineFamily.values()){
            int stock=EnginePart.stock(),turbo=EnginePart.boosted(1),blower=EnginePart.boosted(2);
            assertEquals(0,EngineBuild.boost(1000,turbo));assertTrue(EngineBuild.boost(2000,blower)>EngineBuild.boost(2000,turbo));
            double na=EngineBuild.torque(4500,f,1,stock,6800,90);
            assertTrue(EngineBuild.torque(4500,f,1,turbo,6800,90)>na*1.4);
            assertTrue(EngineBuild.torque(4500,f,1,blower,6800,90)>na*1.2);
            assertTrue(EngineBuild.torque(4500,f,1,EnginePart.INTAKE.with(stock,2),6800,90)>na);
            assertTrue(EngineBuild.torque(4500,f,1,EnginePart.IGNITION.with(stock,2),6800,90)>na);
            assertEquals(0,EngineBuild.powerKw(6800,f,2,turbo,6800));
            assertTrue(EngineBuild.torque(4500,f,1,turbo,6800,135)<EngineBuild.torque(4500,f,1,turbo,6800,90));
        }
    }
    @Test void reverseLowGripAndAirborneConfigurationsRemainControlled(){
        for(var f:EngineFamily.values())for(int induction=0;induction<3;induction++){
            var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,f,EnginePart.boosted(induction),90);
            for(double grip:new double[]{.22,.70,1}){
                double speed=0;
                for(int i=0;i<200;i++)speed=VehicleDynamics.step(speed,40,true,new VehicleDynamics.Input(1,1,false,true),setup,grip,true,.05).speed();
                assertTrue(speed< -2&&speed>=-14);
            }
            assertEquals(0,VehicleDynamics.step(0,40,true,new VehicleDynamics.Input(1,1,false,false),setup,1,false,.05).speed());
        }
    }
    @Test void coolingUpgradeAndEngineShutdownActuallyCoolTheEngine(){
        double stock=100,heavy=100,off=100;
        for(int i=0;i<1200;i++){
            stock=EngineBuild.temperature(stock,5000,1,.65,true,EnginePart.stock(),.05);
            heavy=EngineBuild.temperature(heavy,5000,1,.65,true,EnginePart.COOLING.with(EnginePart.stock(),2),.05);
            off=EngineBuild.temperature(off,0,0,0,false,EnginePart.stock(),.05);
        }
        assertTrue(heavy<stock-10);assertTrue(off<60);assertTrue(stock<140);
    }
}
