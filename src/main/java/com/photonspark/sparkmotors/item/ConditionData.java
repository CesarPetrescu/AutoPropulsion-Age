package com.photonspark.sparkmotors.item;

import com.photonspark.sparkmotors.sim.VehicleCondition;
import com.photonspark.sparkmotors.sim.VehicleCondition.Part;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.function.Predicate;

/** Named, versioned state. Never mutate an ItemStack's shared custom-data tag. */
public final class ConditionData {
    private ConditionData() {}
    public static CompoundTag save(VehicleCondition state,Predicate<Part> filter) {
        CompoundTag tag=new CompoundTag(); tag.putInt("Version",1);
        for(Part p:Part.values()) if(filter.test(p)) {
            var s=state.state(p); CompoundTag value=new CompoundTag();
            value.putFloat("Wear",(float)s.wear()); value.putFloat("Damage",(float)s.damage()); tag.put(p.id,value);
        }
        return tag;
    }
    public static VehicleCondition load(CompoundTag tag) {
        VehicleCondition state=new VehicleCondition();
        for(Part p:Part.values()) if(tag.contains(p.id,10)) {
            var value=tag.getCompound(p.id); state.restore(p,value.getFloat("Wear"),value.getFloat("Damage"));
        }
        return state;
    }
    public static VehicleCondition fromItem(ItemStack stack) {
        return load(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("PartCondition"));
    }
    public static ItemStack toItem(ItemStack stack,VehicleCondition state,Predicate<Part> filter) {
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        data.put("PartCondition",save(state,filter)); stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data)); return stack;
    }
}
