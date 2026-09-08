package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.client.*;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import com.photonspark.sparkmotors.sim.*;
import net.minecraft.client.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.Locale;

/** Native key mappings, garage buttons and packets; no operating-system input. */
final class HandlingClient {
    private static int phase=5,ticks,job,resetCase,resetRevision,lastResetSample=-1;
    private static boolean done,sawAir;
    private record ResetSample(int revision,int serverTick,Vec3 position,float yaw,double speed) {}
    private static volatile ResetSample resetSample;
    private static int resetStable;
    private static final float[] RESET_YAWS={90,-90,179,-179,540,-540,37,0};
    private static Vec3 resetPosition;
    private static float resetYaw;
    private static Vec3 origin;
    private static double peak,lateral,rearSlip,leftYaw,airY,entryYaw,worldLeft;
    static void tick(Minecraft mc,CarEntity car){
        if(done)return;
        try{step(mc,car);}catch(Exception e){
            done=true;keys(mc,false,false,false,false,false,false);
            ClientSmoke.write(mc,"FAILED: handling phase "+phase+" layout "+job+" tick "+ticks+": "+e.getMessage());mc.stop();
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void press(Minecraft mc,String label){ClientSmoke.press(mc,label,0);}
    private static void keys(Minecraft mc,boolean throttle,boolean brake,boolean left,boolean right,boolean hand,boolean clutch){
        mc.options.keyUp.setDown(throttle);mc.options.keyDown.setDown(brake);
        mc.options.keyLeft.setDown(left);mc.options.keyRight.setDown(right);mc.options.keyJump.setDown(hand);CarClient.CLUTCH.setDown(clutch);
    }
    private static void server(Minecraft mc,CarEntity car,java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer,CarEntity> action){
        var id=mc.player.getUUID();int carId=car.getId();mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            action.accept(p,(CarEntity)p.serverLevel().getEntity(carId));
        });
    }
    private static void reset(Minecraft mc,CarEntity car,Vec3 position,float yaw){
        resetSample=null;resetStable=0;lastResetSample=-1;resetPosition=position;resetYaw=yaw;
        int revision=++resetRevision;
        server(mc,car,(p,c)->{
            p.stopRiding();var tag=new CompoundTag();c.saveWithoutId(tag);c.load(tag);
            c.moveTo(position.x,position.y,position.z,yaw,0);
            p.teleportTo(position.x+4,position.y+1,position.z-3);CarPackets.open(p,c);
            resetSample=new ResetSample(revision,c.tickCount,c.position(),c.getYRot(),c.horizontalSpeed());
        });
    }
    private static boolean resetConverged(Minecraft mc,CarEntity car){
        // Observe immutable samples from the server thread. A server task completing is not
        // evidence that its rotation packet has arrived and finished client interpolation.
        var sample=resetSample;int revision=resetRevision;
        server(mc,car,(p,c)->resetSample=new ResetSample(revision,c.tickCount,c.position(),c.getYRot(),c.horizontalSpeed()));
        if(sample!=null&&sample.revision()==revision&&sample.serverTick()!=lastResetSample){
            lastResetSample=sample.serverTick();
            // Network entity angles have 360/256 degree resolution; the steering threshold below stays unchanged.
            boolean synced=Math.abs(net.minecraft.util.Mth.wrapDegrees(car.getYRot()-sample.yaw()))<=360f/256+.05
                &&Math.abs(net.minecraft.util.Mth.wrapDegrees(sample.yaw()-resetYaw))<.05
                &&car.position().distanceToSqr(sample.position())<.01
                &&sample.position().distanceToSqr(resetPosition)<.03
                &&sample.speed()<.03&&car.horizontalSpeed()<.03;
            resetStable=synced?resetStable+1:0;
        }
        require(ticks<200,"Reset synchronization failed independently of steering: revision="+revision+" client="+car.getYRot()+" server="+sample);
        return resetStable>=5;
    }
    private static void step(Minecraft mc,CarEntity car){
        ticks++;if(origin==null)origin=car.position();
        var layout=DriveConfig.Layout.values()[Math.min(job,2)];
        if(phase==5){
            keys(mc,false,false,false,false,false,false);
            if(resetCase==0)InterpolationPackets.verify(mc);
            reset(mc,car,resetCase==RESET_YAWS.length-1?origin:origin.add((resetCase%2)*6,0,3),RESET_YAWS[resetCase]);
            phase=6;ticks=0;
        }else if(phase==6){
            if(resetConverged(mc,car)){
                System.out.printf(Locale.ROOT,"HANDLING_RESET_CASE_PASS %d target=%.2f client=%.2f server=%.2f%n",resetCase,resetYaw,car.getYRot(),resetSample.yaw());
                if(++resetCase==RESET_YAWS.length){System.out.println("HANDLING_RESET_MATRIX_PASS 8");phase=0;}else phase=5;
                ticks=0;
            }
        }else if(phase==0){
            if(ticks==15&&job>0)CarClient.send(car,CarPackets.OPEN,0,0);
            if(ticks==35)press(mc,"Drive");
            if(ticks==50)press(mc,"Raise / lower jack");
            if(ticks==70)press(mc,job==0?"Open diff":layout.name());
            if(ticks==90&&job==0)press(mc,"RWD");
            if(ticks==110){require(car.driveConfig().equals(DriveConfig.preset(layout)),"Preset did not synchronize");press(mc,"Locked diff");}
            if(ticks==130){require(car.driveConfig().differential()==DriveConfig.Differential.LOCKED,"Locked differential did not synchronize");press(mc,layout==DriveConfig.Layout.FWD?"Open diff":"Limited slip");}
            if(ticks==150&&layout==DriveConfig.Layout.AWD)press(mc,"Front +10%");
            if(ticks==170&&layout==DriveConfig.Layout.AWD){require(car.driveConfig().frontPercent()==50,"AWD split did not synchronize");press(mc,"Front -10%");}
            if(ticks==190){
                require(car.driveConfig().equals(DriveConfig.preset(layout)),"Final road preset mismatch");
                ClientSmoke.screenshot("handling-"+layout.name().toLowerCase(Locale.ROOT)+"-setup.png");
            }
            if(ticks==200)press(mc,"Raise / lower jack");
            if(ticks==220){
                require(!car.raised(),"Jack did not lower");mc.setScreen(null);
                server(mc,car,(p,c)->p.startRiding(c,true));mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            if(ticks==240)CarClient.send(car,CarPackets.IGNITION,0,0);
            if(ticks==270){require(car.engineRunning(),"Engine did not start");phase=1;ticks=0;peak=0;lateral=0;rearSlip=0;leftYaw=0;}
        }else if(phase==1){
            keys(mc,ticks<100||ticks>=280&&ticks<330,ticks>=145&&ticks<280||ticks>=330,ticks>=100&&ticks<112,ticks>=112&&ticks<120,ticks>=108&&ticks<112,ticks>=100&&ticks<280||ticks>=330);
            peak=Math.max(peak,car.horizontalSpeed());
            if(ticks>=100&&ticks<145){lateral=Math.max(lateral,Math.abs(car.lateralSpeed()));rearSlip=Math.max(rearSlip,Math.max(car.wheelSlip(2),car.wheelSlip(3)));}
            if(ticks==99){entryYaw=car.getYRot();worldLeft=0;}
            if(ticks>=103&&ticks<110)worldLeft=Math.min(worldLeft,net.minecraft.util.Mth.wrapDegrees(car.getYRot()-(float)entryYaw));
            if(ticks>=99&&ticks<=120){
                int sample=ticks;double clientYaw=car.getYRot(),clientRate=car.yawRate();
                System.out.printf(Locale.ROOT,"QA_HANDLING_CLIENT layout=%s tick=%d heading=%.5f reference=%.5f yawRate=%.5f speed=%.5f%n",layout,sample,clientYaw,entryYaw,clientRate,car.horizontalSpeed());
                server(mc,car,(p,c)->System.out.printf(Locale.ROOT,"QA_HANDLING_SERVER layout=%s sample=%d serverTick=%d heading=%.5f yawRate=%.5f speed=%.5f raised=%s%n",layout,sample,c.tickCount,c.getYRot(),c.yawRate(),c.horizontalSpeed(),c.raised()));
            }
            if(ticks>=103&&ticks<110)leftYaw=Math.max(leftYaw,-car.yawRate());
            if(ticks==113)ClientSmoke.screenshot("handling-"+layout.name().toLowerCase(Locale.ROOT)+"-drift.png");
            if(ticks==265){
                require(peak>5&&lateral>.3&&rearSlip>.5&&leftYaw>.05,"No physical drift/left steering: speed="+peak+" lateral="+lateral+" rearSlip="+rearSlip+" yaw="+leftYaw);
                require(worldLeft<-.5,"A key must turn toward driver left in Minecraft world coordinates: "+worldLeft);
                require(car.horizontalSpeed()<.3&&car.fuel()<40,"Service brakes/fuel failed: speed="+car.horizontalSpeed());
                System.out.printf(Locale.ROOT,"DRIVE_LAYOUT_PASS %s peak=%.3f lateral=%.3f rearSlip=%.3f leftYaw=%.3f%n",layout,peak,lateral,rearSlip,leftYaw);
            }
            if(ticks==325){
                var velocity=car.getDeltaMovement();double yaw=Math.toRadians(car.getYRot());
                require(car.speed()>3&&CarGeometry.forward(velocity.x,velocity.z,yaw)>0,"Relaunch does not follow the rendered nose");
                require(Math.abs(car.lateralSpeed())<.1&&Math.abs(CarGeometry.lateral(velocity.x,velocity.z,yaw))<.02,"Persistent sideways frame after drift");
                require(!car.reverseSelected()&&car.gear()>0,"Drift changed the selected forward direction");
                System.out.println("HEADING_RECOVERY_CLIENT_PASS "+layout);ClientSmoke.screenshot("handling-"+layout.name().toLowerCase(Locale.ROOT)+"-recovered.png");
            }
            if(ticks==395){
                require(car.horizontalSpeed()<.3,"Relaunch braking failed");keys(mc,false,false,false,false,false,true);CarClient.send(car,CarPackets.IGNITION,0,0);phase=2;ticks=0;
            }
        }else if(phase==2&&ticks==20){
            if(++job<3){
                reset(mc,car,origin,0);phase=4;ticks=0;
            }else{
                airY=car.getY();server(mc,car,(p,c)->c.setPos(c.getX(),c.getY()+3,c.getZ()));phase=3;ticks=0;
            }
        }else if(phase==4){
            keys(mc,false,false,false,false,false,false);
            if(resetConverged(mc,car)){System.out.println("HANDLING_RESET_CONVERGED "+layout);phase=0;ticks=0;}
        }else if(phase==3){
            keys(mc,false,false,true,false,true,true);
            boolean contact=false;for(int c=0;c<4;c++)contact|=car.wheelContact(c);
            if(ticks>=4&&ticks<=12&&!contact&&car.getY()>airY+1){sawAir=true;require(Math.abs(car.yawRate())<.05,"Airborne steering created chassis yaw");}
            if(ticks==8)ClientSmoke.screenshot("handling-airborne.png");
            if(ticks==85){
                require(sawAir&&contact&&Math.abs(car.getY()-airY)<.08,"Gravity, loss of contact or suspension landing failed");
                keys(mc,false,false,false,false,false,false);CarClient.stopSounds();done=true;
                System.out.println("HANDLING_CLIENT_PASS AIRBORNE_LANDING_PASS");
                ClientSmoke.write(mc,"PASS: RWD/FWD/AWD presets, differential/split GUI packets, native steering keys, rear handbrake drift, service braking and suspension flight/landing.");mc.stop();
            }
        }
    }
}
