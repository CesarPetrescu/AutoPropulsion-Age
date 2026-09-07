package com.photonspark.sparkmotors.item;

import com.photonspark.sparkmotors.sim.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.util.List;

/** A removed engine carries its installed service assemblies when traded or refitted. */
public final class EngineItem extends Item {
    public EngineItem(Properties properties){super(properties);}
    public static int parts(ItemStack stack){
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return data.contains("EngineParts")?(data.getInt("EngineDataVersion")<2?EnginePart.fromLegacy(data.getInt("EngineParts")):EnginePart.sanitize(data.getInt("EngineParts"))):EnginePart.stock();
    }
    public static ItemStack withParts(ItemStack stack,int parts){
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        data.putInt("EngineParts",EnginePart.sanitize(parts));data.putInt("EngineDataVersion",2);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return stack;
    }
    public static float temperature(ItemStack stack){
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return data.contains("EngineTemperature")?(float)VehicleDynamics.clamp(data.getFloat("EngineTemperature"),20,150):20;
    }
    public static ItemStack withTemperature(ItemStack stack,float temperature){
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.putFloat("EngineTemperature",temperature);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return stack;
    }
    public static float oilTemperature(ItemStack stack){return value(stack,"OilTemperature",20,20,180);}
    public static float health(ItemStack stack){return value(stack,"EngineHealth",100,0,100);}
    private static float value(ItemStack stack,String key,float fallback,float min,float max){
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return tag.contains(key)?(float)VehicleDynamics.clamp(tag.getFloat(key),min,max):fallback;
    }
    public static ItemStack withCondition(ItemStack stack,float oil,float health){
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putFloat("OilTemperature",oil);tag.putFloat("EngineHealth",health);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return stack;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        int parts=parts(stack);
        lines.add(Component.literal("Engine condition: "+Math.round(health(stack))+"%"));
        for(var slot:EnginePart.values())lines.add(Component.literal(slot.title+": "+slot.label(slot.variant(parts))));
        String problem=EnginePart.problem(parts);if(!problem.isEmpty())lines.add(Component.literal(problem));
    }
}
