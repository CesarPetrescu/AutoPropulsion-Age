package com.photonspark.autopropulsion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class WorkshopBlockEntity extends BlockEntity {
    public WorkshopBlockEntity(BlockPos position, BlockState state) { super(AutoPropulsion.WORKSHOP_ENTITY.get(), position, state); }
}
