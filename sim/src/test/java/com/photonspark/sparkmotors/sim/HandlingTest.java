package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;

class HandlingTest {
    private static final boolean[] ROAD={true,true,true,true};
    private VehicleDynamics.Setup setup(DriveConfig.Layout layout,int grade,int parts){
        return new VehicleDynamics.Setup(Assembly.ENGINE.with(Assembly.stock(),grade),6800,3.7,EngineFamily.I4,parts,90,1.4,null,DriveConfig.preset(layout));
    }
    private VehicleDynamics.State rolling(double speed){return new VehicleDynamics.State(speed,3000,1,40,0,0,new EnginePhysics.State(3000*Math.PI/30,0,0,0,90,3,100,14.7,0,0,false));}
    private VehicleDynamics.State run(VehicleDynamics.State s,VehicleDynamics.Setup setup,VehicleDynamics.Input input,double grip,double seconds){
        for(int i=0;i<Math.round(seconds/.0125);i++)s=VehicleDynamics.step(s,true,input,setup,grip,true,.0125);return s;
    }
    @Test void routingAndDifferentialsConserveTorqueAndSelectTheActualPoweredAxles(){
        var corners=new ArrayList<WheelDynamics.Corner>();for(int c=0;c<4;c++)corners.add(new WheelDynamics.Corner(c%2==0?40:10,0,0,true,0,0,20));
        var wheels=new WheelDynamics.State(corners);
        for(var layout:DriveConfig.Layout.values())for(var diff:DriveConfig.Differential.values()){
            var setup=new DriveConfig(layout,diff,40);var torques=setup.wheelTorques(1000,wheels);
            assertEquals(1000,Arrays.stream(torques).sum(),1e-9);
            assertEquals(setup.frontFraction()*1000,torques[0]+torques[1],1e-9);
            if(layout==DriveConfig.Layout.RWD){assertEquals(0,torques[0]);assertEquals(0,torques[1]);}
            if(layout==DriveConfig.Layout.FWD){assertEquals(0,torques[2]);assertEquals(0,torques[3]);}
            int driven=layout==DriveConfig.Layout.RWD?2:0;
            if(diff==DriveConfig.Differential.OPEN)assertEquals(torques[driven],torques[driven+1]);
            else assertTrue(torques[driven]<torques[driven+1],"Differential transfers torque away from the faster wheel");
        }
    }
    @Test void tireForcesShareOneFrictionBudgetAndDisappearWithoutContact(){
        var setup=setup(DriveConfig.Layout.AWD,2,EnginePart.boosted(3));
        var input=new VehicleDynamics.Input(1,.7,true,false,false,true);
        var force=WheelDynamics.step(WheelDynamics.State.stopped(),setup,input,20,5,.4,.3,4500,new double[]{1,1,1,1},ROAD,new double[4],0,0,.0125);
        assertTrue(Math.hypot(force.forward(),force.lateral())<=VehicleDynamics.MASS*9.81*1.001);
        for(double ax:new double[]{-15,0,15})for(double ay:new double[]{-15,0,15}){
            var transferred=WheelDynamics.step(WheelDynamics.State.stopped(),setup,input,20,5,.4,.3,4500,new double[]{1,1,1,1},ROAD,new double[4],ax,ay,.0125);
            assertTrue(Math.hypot(transferred.forward(),transferred.lateral())<=VehicleDynamics.MASS*9.81*1.001,"Weight transfer cannot create extra total grip");
        }
        var air=WheelDynamics.step(WheelDynamics.State.stopped(),setup,input,20,5,.4,.3,4500,new double[]{1,1,1,1},new boolean[4],new double[4],0,0,.0125);
        assertEquals(0,air.forward());assertEquals(0,air.lateral());assertEquals(0,air.yawMoment());assertEquals(0,air.braking());
    }
    @Test void yawDoesNotRedirectWorldMomentumInAirAndSteeringCannotCreateAirborneYaw(){
        var setup=setup(DriveConfig.Layout.RWD,1,EnginePart.stock());var start=rolling(15);
        var trans=new TransmissionPhysics.State(1,1,0,20,3,.6);
        var a=VehicleDynamics.step(start.speed(),40,start.engine(),WheelDynamics.State.stopped(),trans,false,new VehicleDynamics.Input(0,1,true,false),setup,1,new boolean[4],new double[4],.05);
        double yaw=a.yawDelta(),worldU=a.speed()*Math.cos(yaw)-a.transmission().lateralSpeed()*Math.sin(yaw),worldV=a.speed()*Math.sin(yaw)+a.transmission().lateralSpeed()*Math.cos(yaw);
        assertEquals(3.0/15,worldV/worldU,1e-9,"Free-flight momentum direction must survive body yaw");
        assertEquals(.6,a.transmission().yawRate(),1e-12);
        assertTrue(a.groundSpeed()<Math.hypot(15,3),"Only aerodynamic drag changes airborne chassis speed");
    }
    @Test void suspensionReleasesTheRoadAndSettlesWithoutDownwardAdhesion(){
        double[] none={Double.NaN,Double.NaN,Double.NaN,Double.NaN};
        assertEquals(-9.81,SuspensionPhysics.acceleration(none,-5,null,DriveConfig.stock()),1e-12);
        assertEquals(-9.81,SuspensionPhysics.acceleration(new double[]{.27,.27,.27,.27},10,null,DriveConfig.stock()),1e-12);
        double height=.20,velocity=-1;
        for(int i=0;i<800;i++){double[] gaps={height,height,height,height};velocity+=SuspensionPhysics.acceleration(gaps,velocity,null,DriveConfig.stock())*.0125;height+=velocity*.0125;}
        assertEquals(SuspensionPhysics.REST_GAP,height,.002);assertEquals(0,velocity,.002);
    }
    @Test void powerfulBuildSpinsItsDrivenAxleAndAwdUsesBothAxles(){
        var states=new EnumMap<DriveConfig.Layout,VehicleDynamics.State>(DriveConfig.Layout.class);
        for(var layout:DriveConfig.Layout.values())states.put(layout,run(rolling(0),setup(layout,2,EnginePart.boosted(3)),new VehicleDynamics.Input(1,0,false,false),.4,3));
        var rear=states.get(DriveConfig.Layout.RWD).wheels().corners();var front=states.get(DriveConfig.Layout.FWD).wheels().corners();
        assertTrue(rear.get(2).slip()>rear.get(0).slip()+.2);
        assertTrue(front.get(0).slip()>front.get(2).slip()+.2);
        assertTrue(states.get(DriveConfig.Layout.AWD).speed()>states.get(DriveConfig.Layout.RWD).speed()+1);
        assertTrue(states.get(DriveConfig.Layout.AWD).speed()>states.get(DriveConfig.Layout.FWD).speed()+1);
    }
    @Test void handbrakeCreatesRearSlipAndRotationAndCountersteerReducesYaw(){
        var setup=setup(DriveConfig.Layout.RWD,1,EnginePart.stock());
        var entry=run(rolling(15),setup,new VehicleDynamics.Input(0,.65,false,false,true),1,.6);
        // A brief initiation followed by timely correction. Holding the handbrake
        // into a completed spin is not assumed to be recoverable.
        var drift=run(entry,setup,new VehicleDynamics.Input(0,.65,false,false,true,true),1,.2);
        var coast=run(entry,setup,new VehicleDynamics.Input(0,.65,false,false,true),1,.2);
        assertTrue(Math.abs(drift.transmission().lateralSpeed())>Math.abs(coast.transmission().lateralSpeed())+.3);
        assertTrue(drift.wheels().corners().get(2).slip()>.5);
        var counter=run(drift,setup,new VehicleDynamics.Input(0,-.8,false,false,true),1,.4);
        var held=run(drift,setup,new VehicleDynamics.Input(0,.65,false,false,true),1,.4);
        assertTrue(Math.abs(counter.transmission().yawRate())<Math.abs(held.transmission().yawRate()),"Countersteer yaw="+counter.transmission().yawRate()+" held="+held.transmission().yawRate()+" entry="+drift.transmission().yawRate()+" lateral="+drift.transmission().lateralSpeed());
    }
    @Test void steeringIsProgressiveAndMirrorSymmetric(){
        var setup=setup(DriveConfig.Layout.FWD,1,EnginePart.stock());
        var left=run(rolling(12),setup,new VehicleDynamics.Input(0,.5,false,false,true),1,2);
        var right=run(rolling(12),setup,new VehicleDynamics.Input(0,-.5,false,false,true),1,2);
        assertEquals(left.speed(),right.speed(),1e-8);assertEquals(left.transmission().lateralSpeed(),-right.transmission().lateralSpeed(),1e-8);
        assertEquals(left.transmission().yawRate(),-right.transmission().yawRate(),1e-8);
        assertTrue(left.transmission().yawRate()>0);assertTrue(left.groundSpeed()<12);
    }
    @Test void allLayoutsFamiliesAndInductionModesAccelerateBrakeAndStayFinite() throws Exception {
        int count=0;
        for(var layout:DriveConfig.Layout.values())for(var family:EngineFamily.values())for(int grade=1;grade<=2;grade++)for(int induction=0;induction<7;induction++){
            var setup=new VehicleDynamics.Setup(Assembly.ENGINE.with(Assembly.stock(),grade),6800,3.7,family,EnginePart.boosted(induction),90,1.4,null,DriveConfig.preset(layout));
            var s=run(rolling(0),setup,new VehicleDynamics.Input(1,0,false,false),1,5);
            assertTrue(s.speed()>3,layout+" "+family+" "+grade+" "+induction);assertEquals(0,s.transmission().lateralSpeed(),1e-8);
            s=run(s,setup,new VehicleDynamics.Input(0,0,true,false),1,8);
            assertEquals(0,s.groundSpeed(),.001);assertTrue(Double.isFinite(s.rpm()));count++;
        }
        var path=Path.of("build/reports/handling-matrix.json");Files.createDirectories(path.getParent());
        Files.writeString(path,"{\"drive_layouts\":3,\"families\":7,\"grades\":2,\"induction_modes\":7,\"drive_and_brake_cases_passed\":"+count+"}\n");
        assertEquals(294,count);
    }
}
