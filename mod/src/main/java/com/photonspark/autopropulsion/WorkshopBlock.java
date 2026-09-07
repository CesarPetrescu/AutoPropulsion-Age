package com.photonspark.autopropulsion;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.Comparator;

public final class WorkshopBlock extends BaseEntityBlock {
    private static final MapCodec<WorkshopBlock> CODEC = simpleCodec(properties -> new WorkshopBlock(properties, false));
    public final boolean dyno;
    public WorkshopBlock(Properties properties, boolean dyno) { super(properties); this.dyno = dyno; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WorkshopBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(0, 0, 0, 16, 5, 16); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var candidates = level.getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(6), car -> car.canModify(player));
        candidates.stream().min(Comparator.comparingDouble(player::distanceToSqr)).ifPresentOrElse(car -> car.openGarage(player, dyno),
            () -> player.displayClientMessage(net.minecraft.network.chat.Component.literal("Park your car within six blocks of the workshop."), true));
        return InteractionResult.CONSUME;
    }
}
