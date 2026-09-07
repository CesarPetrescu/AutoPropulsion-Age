package com.photonspark.sparkmotors.item;

import com.photonspark.sparkmotors.AutoPropulsionAge;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Shaped engine conversions preserve the donor's installed kits instead of deleting them. */
public record EngineCraftingRecipe(ShapedRecipe delegate) implements CraftingRecipe {
    @Override public boolean matches(CraftingInput input,Level level){return delegate.matches(input,level);}
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){
        var result=delegate.assemble(input,registries);
        for(int i=0;i<input.size();i++)if(input.getItem(i).getItem() instanceof EngineItem){
            var data=input.getItem(i).get(DataComponents.CUSTOM_DATA);if(data!=null)result.set(DataComponents.CUSTOM_DATA,data);break;
        }
        return result;
    }
    @Override public boolean canCraftInDimensions(int width,int height){return delegate.canCraftInDimensions(width,height);}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return delegate.getResultItem(registries);}
    @Override public NonNullList<Ingredient> getIngredients(){return delegate.getIngredients();}
    @Override public CraftingBookCategory category(){return delegate.category();}
    @Override public String getGroup(){return delegate.getGroup();}
    @Override public RecipeSerializer<?> getSerializer(){return AutoPropulsionAge.ENGINE_RECIPE.get();}
    public static final class Serializer implements RecipeSerializer<EngineCraftingRecipe> {
        @Override public MapCodec<EngineCraftingRecipe> codec(){return ShapedRecipe.Serializer.CODEC.xmap(EngineCraftingRecipe::new,EngineCraftingRecipe::delegate);}
        @Override public StreamCodec<RegistryFriendlyByteBuf,EngineCraftingRecipe> streamCodec(){return ShapedRecipe.Serializer.STREAM_CODEC.map(EngineCraftingRecipe::new,EngineCraftingRecipe::delegate);}
    }
}
