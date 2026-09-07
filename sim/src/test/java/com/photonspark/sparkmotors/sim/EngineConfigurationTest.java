package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineConfigurationTest {
    static VehicleDynamics.State drive(VehicleDynamics.Setup setup,double seconds,VehicleDynamics.Input input){
        var s=new VehicleDynamics.State(0,0,1,40,0,0);
        for(int t=0;t<(int)(seconds/.0125);t++)s=VehicleDynamics.step(s,true,input,setup,1,true,.0125);
        return s;
    }
    @Test void allPopulatedCombinationsHaveCorrectCompatibilityAndFiniteSteadyTorque() throws Exception {
        int checked=0,ready=0,blocked=0;
        // Exhaust all 4^9 supporting-part choices x 7 induction choices; no sampled startup claims.
        for(int n=0;n<262144;n++)for(int mode=0;mode<=6;mode++){
            int p=0,bits=n;for(var slot:EnginePart.values())p=slot.with(p,slot==EnginePart.INDUCTION?mode:1+((bits>>>(slot.ordinal()-(slot.ordinal()>5?1:0))*2)&3));
            boolean ok=mode==0||(EnginePart.FUEL.variant(p)>=2&&(EnginePart.INTERNALS.variant(p)==2||EnginePart.INTERNALS.variant(p)==4)&&
                ((mode!=3&&mode!=4)||(EnginePart.FUEL.variant(p)>=3&&EnginePart.INTERNALS.variant(p)==4&&EnginePart.COOLING.variant(p)>=2&&EnginePart.IGNITION.variant(p)>=2)));
            assertEquals(ok,EnginePart.ready(p));
            double torque=EngineBuild.torque(4500,EngineFamily.I4,1,p,6800,90);
            assertTrue(Double.isFinite(torque));assertEquals(ok,torque>0);
            if(ok)ready++;else blocked++;checked++;
        }
        assertEquals(1835008,checked);
        Path path=Path.of("build/reports/engine-states.json");Files.createDirectories(path.getParent());
        Files.writeString(path,"{\"scope\":\"Exhaustive populated hardware combinations; compatibility and reference steady torque. Missing parts checked separately.\",\"combinations\":"+checked+",\"ready\":"+ready+",\"blocked\":"+blocked+"}\n");
    }
    @Test void allHardwareOptionsDriveAcrossFamiliesGradesInductionAndTuneBounds() throws Exception {
        int checked=0;Set<Integer> builds=new HashSet<>();
        for(int mode=0;mode<=6;mode++)for(var slot:EnginePart.values())for(int v=1;v<=slot.maxVariant();v++){
            int parts=slot.with(EnginePart.boosted(mode),v);if(EnginePart.ready(parts))builds.add(parts);
        }
        for(int parts:builds)for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int tune=0;tune<3;tune++){
            var setup=new VehicleDynamics.Setup(Assembly.ENGINE.with(Assembly.stock(),grade),new int[]{4000,6800,7000}[tune],new double[]{2.8,3.7,4.8}[tune],family,parts,90);
            var s=drive(setup,5,new VehicleDynamics.Input(1,.2,false,false));
            assertTrue(s.speed()>3&&s.fuel()<40,family+" did not drive parts="+parts+" speed="+s.speed());
            assertTrue(Double.isFinite(s.engine().oilPressure())&&s.rpm()<=setup.limiter()*1.041);
            for(int t=0;t<160;t++)s=VehicleDynamics.step(s,true,new VehicleDynamics.Input(0,0,true,false),setup,1,true,.05);
            assertEquals(0,s.speed(),.001);checked++;
        }
        Path path=Path.of("build/reports/engine-driving-matrix.json");Files.createDirectories(path.getParent());
        Files.writeString(path,"{\"scope\":\"Every option in each compatible induction baseline, all 7 families, 2 grades, 3 tune bounds; continuous stateful drive and brake\",\"distinct_builds\":"+builds.size()+",\"drive_and_brake_cases_passed\":"+checked+"}\n");
    }
    @Test void missingSlotsAndAllPairsRejectUnsupportedInstallations(){
        for(var slot:EnginePart.values())if(slot!=EnginePart.INDUCTION){assertFalse(EnginePart.ready(slot.with(EnginePart.stock(),0)));}
        for(var a:EnginePart.values())for(var b:EnginePart.values())if(a!=b)for(int va=0;va<=a.maxVariant();va++)for(int vb=0;vb<=b.maxVariant();vb++){
            int p=b.with(a.with(EnginePart.stock(),va),vb);assertEquals(p,EnginePart.sanitize(p));
            for(var slot:EnginePart.values())assertEquals(slot==a?va:slot==b?vb:slot==EnginePart.INDUCTION?0:1,slot.variant(p));
        }
        assertFalse(EnginePart.INDUCTION.installationProblem(EnginePart.stock(),3).isEmpty());
        assertFalse(EnginePart.INTERNALS.installationProblem(EnginePart.boosted(1),3).isEmpty());
        assertEquals("",EnginePart.INTERNALS.installationProblem(EnginePart.boosted(1),0));
    }
    @Test void allLegacyEncodingsMigrateWithoutChangingTheOriginalSixSlots(){
        for(int old=0;old<4096;old++){
            int p=EnginePart.fromLegacy(old);
            for(var slot:EnginePart.values())assertEquals(slot.ordinal()<6?Math.min(2,(old>>>(slot.ordinal()*2))&3):1,slot.variant(p));
        }
        assertThrows(IllegalArgumentException.class,()->EnginePart.INDUCTION.with(0,7));
    }
    @Test void continuousDynamicsAreDeterministicAndSafeOnLowGripReverseAndInTheAir(){
        for(var f:EngineFamily.values())for(int mode=0;mode<=6;mode++){
            var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,f,EnginePart.boosted(mode),90);
            var input=new VehicleDynamics.Input(1,1,false,true);
            assertEquals(drive(setup,3,input),drive(setup,3,input));
            for(double grip:new double[]{.22,.7,1}){
                var s=new VehicleDynamics.State(0,0,1,40,0,0);
                for(int t=0;t<240;t++)s=VehicleDynamics.step(s,true,input,setup,grip,true,.025);
                assertTrue(s.speed()< -2&&s.speed()>=-14);
            }
            var s=VehicleDynamics.step(new VehicleDynamics.State(0,0,1,40,0,0),true,input,setup,1,false,.05);assertEquals(0,s.speed());
        }
    }
}
