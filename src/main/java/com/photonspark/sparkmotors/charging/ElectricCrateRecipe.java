package com.photonspark.sparkmotors.charging;

import com.photonspark.sparkmotors.item.CarCrateItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A vehicle conversion transfers both donor engine data and the exact battery state. */
public record ElectricCrateRecipe(ShapedRecipe delegate) implements CraftingRecipe {
    @Override public boolean matches(CraftingInput input,Level level){return delegate.matches(input,level);}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){
        var result=delegate.assemble(input,registries);var data=new net.minecraft.nbt.CompoundTag();
        for(int i=0;i<input.size();i++)if(input.getItem(i).getItem() instanceof CarCrateItem)
            {
            data=input.getItem(i).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            var mechanical=com.photonspark.sparkmotors.item.MechanicalData.get(input.getItem(i));
            if(mechanical!=null)com.photonspark.sparkmotors.item.MechanicalData.set(result,mechanical);
        }
        for(int i=0;i<input.size();i++)if(input.getItem(i).getItem() instanceof TractionBatteryItem item){
            var pack=item.write(new ItemStack(item),item.read(input.getItem(i)));
            data.put("TractionBattery",pack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag());
        }
        result.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height){return delegate.canCraftInDimensions(width,height);}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return delegate.getResultItem(registries);}
    @Override public NonNullList<Ingredient> getIngredients(){return delegate.getIngredients();}
    @Override public CraftingBookCategory category(){return delegate.category();}
    @Override public String getGroup(){return delegate.getGroup();}
    @Override public RecipeSerializer<?> getSerializer(){return Electrification.CRATE_RECIPE.get();}
    public static final class Serializer implements RecipeSerializer<ElectricCrateRecipe> {
        @Override public MapCodec<ElectricCrateRecipe> codec(){return ShapedRecipe.Serializer.CODEC.xmap(ElectricCrateRecipe::new,ElectricCrateRecipe::delegate);}
        @Override public StreamCodec<RegistryFriendlyByteBuf,ElectricCrateRecipe> streamCodec(){return ShapedRecipe.Serializer.STREAM_CODEC.map(ElectricCrateRecipe::new,ElectricCrateRecipe::delegate);}
    }
}
