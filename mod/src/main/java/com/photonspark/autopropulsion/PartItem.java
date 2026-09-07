package com.photonspark.autopropulsion;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import java.util.List;

public final class PartItem extends Item {
    public PartItem(Properties properties) { super(properties); }
    public static String partId(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("part");
    }
    public static ItemStack stack(String id) {
        ItemStack stack = new ItemStack(AutoPropulsion.PART.get());
        CompoundTag tag = new CompoundTag(); tag.putString("part", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(PartCatalog.icon(id)));
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        String id = partId(stack);
        return id.startsWith(AutoPropulsion.ID + ":") ? Component.translatable("part.autopropulsion." + id.substring(id.indexOf(':') + 1)) : Component.literal(id.isBlank() ? "Unassigned vehicle part" : id);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String id = partId(stack);
        lines.add(Component.literal(id));
        lines.add(Component.literal(id.contains(":model_") ? "Model study only. Gameplay is not implemented." : "Engine off: right-click your car to install."));
    }
}
