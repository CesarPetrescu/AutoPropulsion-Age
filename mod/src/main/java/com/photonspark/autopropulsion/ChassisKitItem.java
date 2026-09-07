package com.photonspark.autopropulsion;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class ChassisKitItem extends Item {
    public ChassisKitItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        VehicleEntity car = new VehicleEntity(AutoPropulsion.HATCH.get(), context.getLevel());
        var pos = context.getClickedPos().relative(context.getClickedFace());
        car.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, context.getPlayer().getYRot(), 0);
        car.setOwner(context.getPlayer().getUUID());
        car.installDefaults();
        if (!context.getLevel().noCollision(car, car.getBoundingBox())) {
            context.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("Leave a clear 5 x 3 block area for the car."), true);
            return InteractionResult.FAIL;
        }
        if (context.getLevel().addFreshEntity(car)) {
            if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }
}
