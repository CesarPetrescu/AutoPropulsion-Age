package com.photonspark.sparkmotors.sim;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VehicleCollisionTest {
    private final List<VehicleCollision.Box> hull=CarGeometry.hull(false,true,new double[4],0);
    @Test void rotatedEnvelopeCornersAreEmptyButTheBodyStillHitsWalls(){
        var emptyCorner=VehicleCollision.Box.bounds(2.05,.4,2.05,2.25,1,2.25);
        assertTrue(VehicleCollision.clear(hull,List.of(emptyCorner),0,0,0,Math.PI/4));
        var actualNose=VehicleCollision.Box.bounds(-1.7,.4,1.4,-1.3,1,1.8);
        assertFalse(VehicleCollision.clear(hull,List.of(actualNose),0,0,0,Math.PI/4));
    }
    @Test void continuousSweepStopsAtThinWallAtMaximumSpeedAndReversesAway(){
        var wall=VehicleCollision.Box.bounds(-10,0,3,10,3,3.0625);
        var hit=VehicleCollision.move(hull,List.of(wall),0,0,0,0,0,0,0,65,0,1280,.05);
        assertEquals(3-2.275,hit.z(),.0001);assertEquals(0,hit.vz(),1e-9);assertEquals(0,hit.yawRate(),1e-9);
        assertTrue(hit.horizontal());assertEquals(65,hit.impact(),1e-9);
        var away=VehicleCollision.move(hull,List.of(wall),hit.x(),hit.y(),hit.z(),hit.yaw(),0,0,0,-5,0,1280,.05);
        assertEquals(hit.z()-.25,away.z(),1e-9);assertFalse(away.horizontal());
    }
    @Test void glancingContactKeepsTangentialMotionAndDoesNotAddEnergy(){
        var wall=VehicleCollision.Box.bounds(-10,0,3,10,3,3.2);
        double vx=12,vz=20,omega=.3,mass=1280;
        var r=VehicleCollision.move(hull,List.of(wall),0,0,.2,.3,0,vx,0,vz,omega,mass,.05);
        assertTrue(r.horizontal());assertTrue(r.vx()>2,"Sliding along a wall must not clear both velocity axes");
        double before=.5*mass*(vx*vx+vz*vz)+.5*VehicleDynamics.YAW_INERTIA*omega*omega;
        double after=.5*mass*(r.vx()*r.vx()+r.vz()*r.vz())+.5*VehicleDynamics.YAW_INERTIA*r.yawRate()*r.yawRate();
        assertTrue(after<=before+1e-5);assertTrue(VehicleCollision.clear(hull,List.of(wall),r.x(),r.y(),r.z(),r.yaw()));
    }
    @Test void diagonalCarCanRotateBesideAnEmptyEnvelopeCorner(){
        var obstacle=VehicleCollision.Box.bounds(1.9,0,1.9,2.2,2,2.2);
        var r=VehicleCollision.move(hull,List.of(obstacle),0,0,0,Math.PI/4,.04,0,0,0,.8,1280,.05);
        assertEquals(Math.PI/4+.04,r.yaw(),1e-12);assertEquals(.8,r.yawRate(),1e-12);assertFalse(r.horizontal());
    }
    @Test void rotationSweepCannotPutANoseThroughAWall(){
        var wall=VehicleCollision.Box.bounds(-10,0,2.3,10,3,2.5);
        var r=VehicleCollision.move(hull,List.of(wall),0,0,0,0,.1,0,0,0,2,1280,.05);
        assertTrue(r.horizontal());assertTrue(r.yaw()>0&&r.yaw()<.1);assertEquals(0,r.yawRate());
        assertTrue(VehicleCollision.clear(hull,List.of(wall),r.x(),r.y(),r.z(),r.yaw()));
    }
    @Test void groundClearanceAndBonnetHeightDifferFromTheCabin(){
        assertTrue(VehicleCollision.clear(hull,List.of(VehicleCollision.Box.bounds(-.4,0,-.4,.4,.15,.4)),0,0,0,0));
        assertTrue(VehicleCollision.clear(hull,List.of(VehicleCollision.Box.bounds(-.5,1.15,1.5,.5,1.3,1.7)),0,0,0,0));
        assertFalse(VehicleCollision.clear(hull,List.of(VehicleCollision.Box.bounds(-.5,1.15,-.5,.5,1.3,0)),0,0,0,0));
        var ground=VehicleCollision.Box.bounds(-10,-1,-10,10,0,10);
        var fall=VehicleCollision.move(hull,List.of(ground),0,1,0,0,0,0,-30,0,0,1280,.05);
        assertTrue(fall.vertical());assertEquals(0,fall.vy());assertFalse(fall.horizontal());
        assertTrue(VehicleCollision.clear(hull,List.of(ground),fall.x(),fall.y(),fall.z(),fall.yaw()));
    }
    @Test void allHeadingsRoundTripVelocityAndTireFixturesUseTheSameCorners(){
        for(double degrees:new double[]{-1080,-361,-180,-90,0,45,179,180,360,721,10000})for(double forward:new double[]{-20,0,30})for(double lateral:new double[]{-15,0,15}){
            double a=Math.toRadians(degrees),x=CarGeometry.worldX(forward,lateral,a),z=CarGeometry.worldZ(forward,lateral,a);
            assertEquals(forward,CarGeometry.forward(x,z,a),1e-9);assertEquals(lateral,CarGeometry.lateral(x,z,a),1e-9);
        }
        for(int c=0;c<4;c++)for(String prefix:List.of("rim_","brake_pad_","brake_caliper_","knuckle_")){
            String name=prefix+ComponentSlot.CORNERS[c]+(prefix.equals("brake_pad_")?"_-1":"");
            assertEquals(c,CarGeometry.corner(name));assertTrue(CarGeometry.steers(name));assertEquals(prefix.equals("rim_"),CarGeometry.spins(name));
        }
    }
    @Test void repeatedSpinBrakeAndRelaunchRecoversHeadingForEveryLayout(){
        for(var layout:DriveConfig.Layout.values())for(double lateral:new double[]{-20,8,20}){
            var setup=new VehicleDynamics.Setup(Assembly.stock(),6800,3.7,EngineFamily.I4,EnginePart.stock(),90,1.4,null,DriveConfig.preset(layout));
            var s=new VehicleDynamics.State(10,3000,1,40,0,0,new EnginePhysics.State(3000*Math.PI/30,0,0,0,90,3,100,14.7,0,0,false),WheelDynamics.State.stopped(),new TransmissionPhysics.State(1,1,0,20,lateral,.7));
            for(int i=0;i<1200;i++)s=VehicleDynamics.step(s,true,new VehicleDynamics.Input(i>=800?1:0,0,i<800,false),setup,1,true,.0125);
            assertTrue(s.speed()>3,layout.toString());assertEquals(0,s.transmission().lateralSpeed(),1e-6);assertEquals(0,s.transmission().yawRate(),1e-6);
        }
    }
    @Test void shellRollAndPitchCannotUseMoreCompressionThanTheWheelArchesAllow(){
        for(double pitch:new double[]{-18,0,18})for(double roll:new double[]{-18,0,18}){
            double[] travel={0,.05,-.08,0};double f=CarGeometry.tiltFraction(pitch,roll,travel),p=Math.toRadians(pitch*f),r=Math.toRadians(roll*f);
            for(int c=0;c<4;c++){
                double y=-Math.sin(r)*CarGeometry.wheelX(c)+Math.cos(r)*(Math.cos(p)*(.34+travel[c])-Math.sin(p)*CarGeometry.wheelZ(c));
                assertTrue(y-.34<=SuspensionPhysics.MAX_BUMP+1e-9,"Body animation intersects a tire");
            }
        }
    }
}
