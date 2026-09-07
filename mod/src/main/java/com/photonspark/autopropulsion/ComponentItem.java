package com.photonspark.autopropulsion;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;

public final class ComponentItem extends Item {
    public ComponentItem(Properties props){super(props);}
    public static String definition(ItemStack stack) {
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return "";
        String id=data.copyTag().getString("Part");return id.length()<=128?id:"";
    }
    public static ItemStack stack(String path) {
        String id=path.contains(":")?path:"autopropulsion:"+path;
        ItemStack stack=new ItemStack(Content.PART.get());CompoundTag tag=new CompoundTag();tag.putString("Part",id);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        int index=java.util.Arrays.asList(BuiltinCatalog.IDS).indexOf(id.substring(id.indexOf(':')+1));
        if(index>=0)stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(index+1));
        return stack;
    }
    @Override public Component getName(ItemStack stack) {
        String id=definition(stack);return id.isEmpty()?super.getName(stack):Component.translatable("part."+id.replace(':','.'));
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        String id=definition(stack);
        lines.add(Component.literal(id).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        String path=id.substring(id.indexOf(':')+1);
        boolean functional=java.util.Arrays.asList(BuiltinCatalog.FUNCTIONAL_IDS).contains(path);
        lines.add(Component.translatable(functional?"tooltip.autopropulsion.install":"tooltip.autopropulsion.catalog").withStyle(functional?net.minecraft.ChatFormatting.AQUA:net.minecraft.ChatFormatting.GOLD));
    }
}
