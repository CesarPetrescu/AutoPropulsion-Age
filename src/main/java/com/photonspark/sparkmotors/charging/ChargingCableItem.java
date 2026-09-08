package com.photonspark.sparkmotors.charging;

import com.photonspark.sparkmotors.entity.CarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** Pair a charger, then right-click an owned stationary vehicle. The server validates both endpoints. */
public final class ChargingCableItem extends Item {
    public ChargingCableItem(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var player=context.getPlayer();if(player==null)return InteractionResult.FAIL;
        if(!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ChargerBlockEntity charger)||!charger.mayUse(player))return InteractionResult.FAIL;
        if(player.isShiftKeyDown()&&(charger.connected()||charger.hasCable())){charger.unplug(player);return InteractionResult.CONSUME;}
        var tag=context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putLong("Charger",context.getClickedPos().asLong());tag.putString("Dimension",context.getLevel().dimension().location().toString());
        context.getItemInHand().set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        player.displayClientMessage(Component.literal("Charger selected. Right-click your EV or plug-in hybrid within 6 blocks. READY must be off."),false);
        return InteractionResult.CONSUME;
    }
    public static void connect(ServerPlayer player,CarEntity car,ItemStack cable){
        var data=cable.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!data.contains("Charger")||!data.getString("Dimension").equals(player.level().dimension().location().toString())){message(player,"Select a charger with this cable first.");return;}
        var pos=BlockPos.of(data.getLong("Charger"));
        if(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>100||!player.level().hasChunkAt(pos)){message(player,"Charger is too far away or unloaded.");return;}
        if(player.level().getBlockEntity(pos) instanceof ChargerBlockEntity charger&&charger.connectCable(player,car,cable))message(player,"Cable installed. Sneak-click either end with an empty hand to unplug and recover it.");
        else message(player,"Cannot connect: check ownership, cable occupancy, six-block reach, vehicle type and READY state.");
    }
    public static void clearPairing(ItemStack cable){
        var tag=cable.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.remove("Charger");tag.remove("Dimension");
        if(tag.isEmpty())cable.remove(DataComponents.CUSTOM_DATA);else cable.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static void message(ServerPlayer player,String text){player.displayClientMessage(Component.literal(text),false);}
}
