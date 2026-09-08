package com.photonspark.sparkmotors.item;
import com.photonspark.sparkmotors.AutoPropulsionAge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
public final class CarCrateItem extends Item {
    private final com.photonspark.sparkmotors.sim.electric.Powertrain powertrain;
    public CarCrateItem(Properties properties) { this(properties,com.photonspark.sparkmotors.sim.electric.Powertrain.COMBUSTION); }
    public CarCrateItem(Properties properties,com.photonspark.sparkmotors.sim.electric.Powertrain type) { super(properties);powertrain=type; }
    @Override public InteractionResult useOn(UseOnContext ctx) {
        if(ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var player=ctx.getPlayer();if(player==null)return InteractionResult.FAIL;
        var pos=ctx.getClickedPos().relative(ctx.getClickedFace());
        var car=AutoPropulsionAge.CAR.get().create(ctx.getLevel());
        if(car==null)return InteractionResult.FAIL;
        car.moveTo(pos.getX()+.5,pos.getY()+.05,pos.getZ()+.5,player.getYRot(),0);
        car.setOwner(player.getUUID());
        var state=new net.minecraft.nbt.CompoundTag();car.saveWithoutId(state);
        state.putInt("EngineParts",EngineItem.parts(ctx.getItemInHand()));state.putFloat("EngineTemperature",EngineItem.temperature(ctx.getItemInHand()));
        state.putFloat("OilTemperature",EngineItem.oilTemperature(ctx.getItemInHand()));state.putFloat("EngineHealth",EngineItem.health(ctx.getItemInHand()));state.remove("Mechanics");car.load(state);
        var donor=MechanicalData.get(ctx.getItemInHand());if(donor!=null)car.setMechanics(donor);
        if(!car.hasBodyClearance()) {
            player.displayClientMessage(Component.literal("Clear a space about 5 x 5 blocks for the sedan."),true);return InteractionResult.FAIL;
        }
        car.initializePowertrain(powertrain,player.isCreative()?.65:0);
        var stored=ctx.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if(powertrain.electric()&&stored.contains("TractionBattery")){
            // Crafting a vehicle with a used battery must not refill or rejuvenate it.
            var battery=stored.getCompound("TractionBattery");var saved=new net.minecraft.nbt.CompoundTag();car.saveWithoutId(saved);
            saved.putDouble("BatteryJ",battery.getDouble("EnergyJ"));saved.putDouble("BatteryC",battery.getDouble("TemperatureC"));
            saved.putDouble("BatteryHealth",battery.getDouble("Health"));saved.putDouble("BatteryThroughputJ",battery.getDouble("ThroughputJ"));car.load(saved);
        }
        ctx.getLevel().addFreshEntity(car);
        if(!player.isCreative())ctx.getItemInHand().shrink(1);
        player.displayClientMessage(Component.literal("Right-click to drive. G: garage · R: ignition · H: lights · Shift: exit"),false);
        return InteractionResult.CONSUME;
    }
}
