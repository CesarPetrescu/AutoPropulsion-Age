package com.photonspark.sparkmotors.charging;

import com.photonspark.sparkmotors.sim.electric.ChargingModel;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;

public final class ChargerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public final ChargingModel.Tier tier;
    public ChargerBlock(Properties properties,ChargingModel.Tier tier){super(properties);this.tier=tier;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return switch(tier){case LV->Block.box(2,0,2,14,12,14);case HOME->Block.box(3,0,3,13,16,13);case RAPID->Block.box(1,0,1,15,16,15);case ULTRA->Block.box(0,0,0,16,16,16);};
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ChargerBlockEntity(pos,state);}
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide||type!=Electrification.CHARGER_ENTITY.get()?null:(world,pos,block,entity)->((ChargerBlockEntity)entity).serverTick();
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ChargerBlockEntity charger)charger.interact(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof ChargerBlockEntity charger)charger.disconnect();
        super.onRemove(state,level,pos,next,moving);
    }
}
