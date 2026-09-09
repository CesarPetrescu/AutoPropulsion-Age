package com.photonspark.sparkmotors.item;

import com.photonspark.sparkmotors.entity.CarEntity;
import com.photonspark.sparkmotors.sim.BodyStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** Applied by the ordinary CarEntity interaction, never by an unvalidated client packet. */
public final class CarBodyKitItem extends Item {
    private final BodyStyle style;
    public CarBodyKitItem(Properties properties,BodyStyle style){super(properties);this.style=style;}
    public BodyStyle style(){return style;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.sparkmotors.body_kit"));
        lines.add(Component.translatable("tooltip.sparkmotors.body_kit_preserves"));
    }
    public InteractionResult install(ServerPlayer player,CarEntity car,InteractionHand hand){
        var input=player.getItemInHand(hand);
        if(input.getItem()!=this||!car.mayModify(player)||car.distanceToSqr(player)>36)return InteractionResult.FAIL;
        if(car.bodyStyle()==style){message(player,"message.sparkmotors.body_same");return InteractionResult.CONSUME;}
        if(car.ignition()||car.horizontalSpeed()>.05||car.isVehicle()||car.plugged()||!car.raised()){
            message(player,"message.sparkmotors.body_service");return InteractionResult.CONSUME;
        }
        var previous=car.bodyStyle();
        if(!car.trySetBodyStyle(style)){message(player,"message.sparkmotors.body_obstructed");return InteractionResult.CONSUME;}
        // Return the displaced shell. Neither a missing inventory slot nor a repeated swap may
        // duplicate a kit or mutate any drivetrain, fluids, battery charge, wear or serial IDs.
        if(!player.getAbilities().instabuild){
            input.shrink(1);
            var returned=BodyStyles.kit(previous);
            if(!player.getInventory().add(returned))player.drop(returned,false);
        }
        player.displayClientMessage(Component.translatable("message.sparkmotors.body_installed",style.title()),true);
        return InteractionResult.CONSUME;
    }
    private static void message(ServerPlayer player,String key){player.displayClientMessage(Component.translatable(key),true);}
}
