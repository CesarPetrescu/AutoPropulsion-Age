package com.photonspark.sparkmotors.charging;

import com.photonspark.sparkmotors.sim.electric.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Removed packs retain energy, temperature, health and throughput. New packs are EMPTY. */
public final class TractionBatteryItem extends Item {
    public final Powertrain type;
    public TractionBatteryItem(Properties properties,Powertrain type){super(properties);this.type=type;}
    public BatteryModel.State read(ItemStack stack){
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return new BatteryModel.State(tag.getDouble("EnergyJ"),tag.contains("TemperatureC")?tag.getDouble("TemperatureC"):20,tag.contains("Health")?tag.getDouble("Health"):1,tag.getDouble("ThroughputJ")).normalized(type.battery);
    }
    public ItemStack write(ItemStack stack,BatteryModel.State state){
        var tag=new CompoundTag();tag.putDouble("EnergyJ",state.energyJ());tag.putDouble("TemperatureC",state.temperatureC());tag.putDouble("Health",state.health());tag.putDouble("ThroughputJ",state.throughputJ());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return stack;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        var state=read(stack);tooltip.add(Component.literal(String.format(Locale.ROOT,"%.0f V / %.1f kWh nominal",type.battery.nominalV(),type.battery.kWh())));
        tooltip.add(Component.literal(String.format(Locale.ROOT,"SOC %.1f%% / health %.1f%% / %.1f C",state.soc(type.battery)*100,state.health()*100,state.temperatureC())));
        tooltip.add(Component.literal("Workshop replacement: READY off, charger disconnected, hood open."));
    }
}
