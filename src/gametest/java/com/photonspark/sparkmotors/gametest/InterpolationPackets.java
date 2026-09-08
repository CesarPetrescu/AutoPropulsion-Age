package com.photonspark.sparkmotors.gametest;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;

/** Deterministic partial-packet ordering through vanilla's actual client packet listener. */
final class InterpolationPackets {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static void verify(Minecraft mc){
        var probe=AutoPropulsionAge.CAR.get().create(mc.level);
        var source=AutoPropulsionAge.CAR.get().create(mc.level);
        require(probe!=null&&source!=null,"Could not create interpolation test entities");
        source.setId(probe.getId());probe.moveTo(mc.player.position().add(0,8,0),0,0);
        var base=probe.position();mc.level.addEntity(probe);
        try{
            var listener=mc.getConnection();
            source.moveTo(base.add(6,1,2),90,45);
            listener.handleTeleportEntity(new ClientboundTeleportEntityPacket(source));probe.tick();
            listener.handleMoveEntity(new ClientboundMoveEntityPacket.Pos(probe.getId(),(short)4096,(short)0,(short)0,false));
            for(int i=0;i<3;i++)probe.tick();
            require(probe.position().distanceToSqr(base.add(7,1,2))<1e-8,"Position-only packet lost its destination");
            require(Math.abs(probe.getYRot()-90)<.001&&Math.abs(probe.getXRot()-45)<.001,"Position-only packet discarded pending yaw/pitch");
            System.out.println("INTERPOLATION_PACKET_CLIENT_PASS position_preserves_heading");

            source.moveTo(base.add(-6,2,-4),179,0);
            listener.handleTeleportEntity(new ClientboundTeleportEntityPacket(source));probe.tick();
            listener.handleMoveEntity(new ClientboundMoveEntityPacket.Rot(probe.getId(),(byte)-32,(byte)16,false));
            for(int i=0;i<3;i++)probe.tick();
            require(probe.position().distanceToSqr(source.position())<1e-8,"Rotation-only packet discarded pending position");
            require(Math.abs(probe.getYRot()+45)<.001&&Math.abs(probe.getXRot()-22.5)<.001,"Rotation-only packet lost its yaw/pitch");
            System.out.println("INTERPOLATION_PACKET_CLIENT_PASS rotation_preserves_position");

            probe.moveTo(base,45,0);
            require(probe.lerpTargetX()==probe.getX()&&probe.lerpTargetY()==probe.getY()&&probe.lerpTargetZ()==probe.getZ()
                &&probe.lerpTargetYRot()==45&&probe.lerpTargetXRot()==0,"Settled getters retained an obsolete interpolation target");
            System.out.println("INTERPOLATION_PACKET_CLIENT_PASS settled_targets");
        }finally{mc.level.removeEntity(probe.getId(),Entity.RemovalReason.DISCARDED);}
    }
}
