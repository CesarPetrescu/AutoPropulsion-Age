package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Actual entity/voxel integration, in addition to the pure continuous-collision tests. */
@GameTestHolder("sparkmotors") @PrefixGameTestTemplate(false)
public final class CollisionGameTests {
    private CarEntity car(GameTestHelper h,float yaw){
        var c=AutoPropulsionAge.CAR.get().create(h.getLevel());var p=h.absoluteVec(new Vec3(8.5,2.02,8.5));
        c.moveTo(p.x,p.y,p.z,yaw,0);h.getLevel().addFreshEntity(c);return c;
    }
    /** Initial-condition fixture only; subsequent motion runs through the real server tick. */
    private static void motion(CarEntity car,double forward,double lateral,double yawRate){
        try{
            var speed=CarEntity.class.getDeclaredField("speed");speed.setAccessible(true);speed.setDouble(car,forward);
            var trans=CarEntity.class.getDeclaredField("transmissionState");trans.setAccessible(true);
            trans.set(car,new TransmissionPhysics.State(1,1,0,20,lateral,yawRate));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @GameTest(template="test_track",timeoutTicks=45) public void emptyEnvelopeCornerDoesNotBlockTheRotatedCar(GameTestHelper h){
        var c=car(h,45);h.setBlock(new BlockPos(10,2,10),Blocks.STONE);h.setBlock(new BlockPos(10,3,10),Blocks.STONE);
        h.assertTrue(!h.getLevel().noCollision(c,c.getBoundingBox()),"Fixture must obstruct the old enclosing AABB");
        h.assertTrue(c.hasBodyClearance(),"Post outside the actual car must not obstruct it");
        var p=h.makeMockServerPlayerInLevel();p.moveTo(c.position());p.startRiding(c,true);motion(c,6,0,.3);
        Vec3 origin=c.position();h.onEachTick(()->c.receiveInput(16,.5f));
        h.runAfterDelay(16,()->{
            h.assertTrue(c.position().distanceTo(origin)>2,"Phantom bounding-box corner stopped the car");
            h.assertTrue(c.getYRot()>48,"Phantom bounding-box corner blocked steering: "+c.getYRot());
            h.assertTrue(c.health()==100,"Empty corner caused collision damage");p.stopRiding();
            System.out.println("ORIENTED_COLLISION_SERVER_PASS empty_corner");h.succeed();
        });
    }
    @GameTest(template="test_track",timeoutTicks=250) public void crashStopAndRelaunchUsesTheVisibleHeading(GameTestHelper h){
        var c=car(h,0);var p=h.makeMockServerPlayerInLevel();p.moveTo(c.position());c.setOwner(p.getUUID());p.startRiding(c,true);
        for(int x=3;x<14;x++)for(int y=2;y<5;y++)h.setBlock(new BlockPos(x,y,14),Blocks.STONE);
        c.action(p,CarPackets.IGNITION,0,0);motion(c,14,0,0);int[] tick={0};
        h.onEachTick(()->{tick[0]++;c.receiveInput(tick[0]<100?2:1,0);});
        h.runAfterDelay(20,()->{
            double wall=h.absoluteVec(new Vec3(8,2,14)).z;
            h.assertTrue(c.getZ()<=wall-2.274,"Nose penetrated the voxel wall");
            h.assertTrue(c.health()<100,"Real impact must damage the front");
            h.assertTrue(c.mechanics().get("wheel.fl.link").damage()==0,"Head-on hit incorrectly bent a steering corner");
        });
        h.runAfterDelay(85,()->{
            h.assertTrue(c.horizontalSpeed()<.05,"Car retains motion after stopping");
            for(int x=3;x<14;x++)for(int y=2;y<5;y++)h.setBlock(new BlockPos(x,y,14),Blocks.AIR);
        });
        h.runAfterDelay(148,()->{
            var delta=c.getDeltaMovement();double angle=Math.toRadians(c.getYRot());
            h.assertTrue(c.speed()>3,"Car cannot relaunch after a moderate impact: "+c.speed());
            h.assertTrue(CarGeometry.forward(delta.x,delta.z,angle)>0,"Forward input drives opposite the visible nose");
            h.assertTrue(Math.abs(CarGeometry.lateral(delta.x,delta.z,angle))<.01&&Math.abs(c.lateralSpeed())<.1,"Permanent heading/velocity mismatch after impact");
            p.stopRiding();System.out.println("HEADING_RECOVERY_SERVER_PASS crash");h.succeed();
        });
    }
    @GameTest(template="test_track",timeoutTicks=190) public void spinningCarStopsAndRelaunchesWithoutKeepingASidewaysFrame(GameTestHelper h){
        var c=car(h,3600);var p=h.makeMockServerPlayerInLevel();p.moveTo(c.position());c.setOwner(p.getUUID());p.startRiding(c,true);
        c.action(p,CarPackets.IGNITION,0,0);motion(c,5,4,.6);int[] tick={0};h.onEachTick(()->{tick[0]++;c.receiveInput(tick[0]<100?2:1,0);});
        h.runAfterDelay(90,()->{h.assertTrue(c.horizontalSpeed()<.05,"Drift did not settle under service braking");h.assertTrue(Math.abs(c.getYRot())<=180,"Spin accumulated an unbounded heading");});
        h.runAfterDelay(146,()->{
            var v=c.getDeltaMovement();double a=Math.toRadians(c.getYRot());
            h.assertTrue(c.speed()>3&&CarGeometry.forward(v.x,v.z,a)>0,"Relaunch failed");
            h.assertTrue(Math.abs(CarGeometry.lateral(v.x,v.z,a))<.01,"Drift kept a stale body frame");
            h.assertTrue(!c.reverseSelected()&&c.gear()>0,"Drift changed the driver's selected direction");
            p.stopRiding();System.out.println("HEADING_RECOVERY_SERVER_PASS spin");h.succeed();
        });
    }
}
