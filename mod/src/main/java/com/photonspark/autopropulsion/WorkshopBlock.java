package com.photonspark.autopropulsion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public final class WorkshopBlock extends Block {
    public WorkshopBlock(Properties props){super(props);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide&&player instanceof ServerPlayer sp) {
            var cars=level.getEntitiesOfClass(VehicleEntity.class,new AABB(pos).inflate(6),v->v.canManage(player));
            cars.stream().min(java.util.Comparator.comparingDouble(v->v.distanceToSqr(player))).ifPresentOrElse(v->v.openGarage(sp),
                ()->player.displayClientMessage(Component.translatable("message.autopropulsion.no_car"),true));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
