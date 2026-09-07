package com.photonspark.autopropulsion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;

public final class VehicleBlueprint extends Item {
    public VehicleBlueprint(Properties props){super(props);}
    @Override public InteractionResult useOn(UseOnContext ctx) {
        if(ctx.getPlayer()==null||ctx.getClickedFace()!=Direction.UP)return InteractionResult.FAIL;
        if(ctx.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var car=Content.VEHICLE.get().create(ctx.getLevel());if(car==null)return InteractionResult.FAIL;
        var pos=ctx.getClickedPos().above();car.moveTo(pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5,ctx.getPlayer().getYRot(),0);
        car.refreshBounds();
        if(!ctx.getLevel().noCollision(car,car.getBoundingBox())){ctx.getPlayer().displayClientMessage(Component.translatable("message.autopropulsion.clear_space"),true);return InteractionResult.FAIL;}
        car.setOwner(ctx.getPlayer().getUUID());ctx.getLevel().addFreshEntity(car);
        if(!ctx.getPlayer().getAbilities().instabuild)ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
