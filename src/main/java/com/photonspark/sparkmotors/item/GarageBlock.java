package com.photonspark.sparkmotors.item;
import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.net.CarPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.network.chat.Component;
public final class GarageBlock extends Block {
    public GarageBlock(Properties p){super(p);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide && player instanceof ServerPlayer sp) {
            var cars=level.getEntitiesOfClass(CarEntity.class,new AABB(pos).inflate(8),c->c.mayModify(player));
            cars.sort(java.util.Comparator.comparingDouble(c->c.distanceToSqr(player)));
            if(cars.isEmpty())player.displayClientMessage(Component.literal("Park your car within 8 blocks of this controller."),true);
            else CarPackets.open(sp,cars.getFirst());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
